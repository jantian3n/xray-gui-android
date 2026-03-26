package com.example.xray_gui_kotlin.runtime

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest

class GeoDataUpdater(private val context: Context) {
    companion object {
        private val BASE_URLS = listOf(
            "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/",
            "https://mirror.ghproxy.com/https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/",
            "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/",
        )
        private const val BOOTSTRAP_ASSET_DIR = "bootstrap-geodata"
        private const val LAST_UPDATE_FILE = "LAST_UPDATE.txt"
        private const val DEFAULT_PROXY_HOST = "127.0.0.1"
        private const val DEFAULT_PROXY_PORT = 10809
        private const val STALE_AFTER_MS = 24L * 60L * 60L * 1000L

        private val FILES = listOf("geoip.dat", "geosite.dat")

        fun geodataDir(context: Context): File = File(context.filesDir, "xray/geodata").apply { mkdirs() }

        fun defaultProxyPort(): Int = DEFAULT_PROXY_PORT

        fun missingFiles(context: Context): List<String> {
            val dir = geodataDir(context)
            return FILES.filter { fileName ->
                val file = File(dir, fileName)
                !file.isFile || file.length() <= 0L
            }
        }

        fun needsRefresh(context: Context, staleAfterMs: Long = STALE_AFTER_MS): Boolean {
            val stampFile = File(geodataDir(context), LAST_UPDATE_FILE)
            val timestamp = stampFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: return true
            return System.currentTimeMillis() - timestamp >= staleAfterMs
        }

        fun lastUpdateMillis(context: Context): Long? {
            val dir = geodataDir(context)
            val stampFile = File(dir, LAST_UPDATE_FILE)
            val stampValue = stampFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
            if (stampValue != null) {
                return stampValue
            }

            val geoIp = File(dir, "geoip.dat")
            if (geoIp.isFile && geoIp.lastModified() > 0L) {
                return geoIp.lastModified()
            }
            return null
        }
    }

    fun installBundledIfMissing(): List<String> {
        val geodataDir = geodataDir(context)
        val installed = mutableListOf<String>()

        for (fileName in FILES) {
            val target = File(geodataDir, fileName)
            if (target.isFile && target.length() > 0L) {
                continue
            }

            val assetPath = "$BOOTSTRAP_ASSET_DIR/$fileName"
            runCatching {
                context.assets.open(assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.onSuccess {
                installed += fileName
                RuntimeLogBus.emit("Installed bundled $fileName from app assets")
            }.onFailure {
                RuntimeLogBus.emit("Bundled $fileName not found: ${it.message}")
            }
        }

        if (installed.isNotEmpty()) {
            File(geodataDir, LAST_UPDATE_FILE).writeText("0")
        }

        return installed
    }

    fun update(proxyPort: Int? = null) {
        val geodataDir = geodataDir(context)
        val routeLabel = if (proxyPort == null) "direct network" else "local HTTP proxy $DEFAULT_PROXY_HOST:$proxyPort"
        RuntimeLogBus.emit("Updating geodata into ${geodataDir.absolutePath} via $routeLabel")

        FILES.forEach { fileName -> downloadAndVerify(fileName, geodataDir, proxyPort) }
        File(geodataDir, LAST_UPDATE_FILE).writeText(System.currentTimeMillis().toString())
        RuntimeLogBus.emit("Geodata update finished")
    }

    private fun downloadAndVerify(fileName: String, outputDir: File, proxyPort: Int?) {
        val tempFile = File(outputDir, "$fileName.download")
        val targetFile = File(outputDir, fileName)

        val routeCandidates = buildList {
            if (proxyPort != null) add(proxyPort)
            add(null)
            if (proxyPort == null) {
                add(DEFAULT_PROXY_PORT)
            }
        }.distinct()

        var lastError: Throwable? = null
        for (candidateProxyPort in routeCandidates) {
            val route = if (candidateProxyPort == null) "direct" else "proxy:$DEFAULT_PROXY_HOST:$candidateProxyPort"
            for (baseUrl in BASE_URLS) {
                runCatching {
                    RuntimeLogBus.emit("Downloading $fileName via $route from $baseUrl")
                    downloadToFile("$baseUrl$fileName", tempFile, candidateProxyPort)

                    val checksumText = downloadText("$baseUrl$fileName.sha256sum", candidateProxyPort)
                    val expectedHash = checksumText.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() }
                        ?.substringBefore(' ')
                        ?.lowercase()
                        ?: error("Checksum file for $fileName is empty")

                    val actualHash = sha256(tempFile)
                    if (actualHash != expectedHash) {
                        error("Checksum mismatch for $fileName. expected=$expectedHash actual=$actualHash")
                    }

                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    RuntimeLogBus.emit("Verified and installed $fileName via $route")
                }.onSuccess {
                    return
                }.onFailure {
                    tempFile.delete()
                    lastError = it
                    RuntimeLogBus.emit("Geodata source failed: ${it.message}")
                }
            }
        }

        throw IllegalStateException("Failed to download $fileName after all routes and mirrors", lastError)
    }

    private fun downloadText(url: String, proxyPort: Int?): String {
        val connection = openConnection(url, proxyPort)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadToFile(url: String, output: File, proxyPort: Int?) {
        val connection = openConnection(url, proxyPort)
        try {
            connection.inputStream.use { input ->
                output.outputStream().use { outputStream -> input.copyTo(outputStream) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, proxyPort: Int?): HttpURLConnection {
        val proxy = if (proxyPort == null) {
            Proxy.NO_PROXY
        } else {
            Proxy(Proxy.Type.HTTP, InetSocketAddress(DEFAULT_PROXY_HOST, proxyPort))
        }
        val connection = URL(url).openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        val code = connection.responseCode
        if (code !in 200..299) {
            error("Unexpected HTTP $code for $url")
        }
        return connection
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> updateDigest(digest, input) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateDigest(digest: MessageDigest, input: InputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) {
                break
            }
            digest.update(buffer, 0, count)
        }
    }
}
