package com.example.xray_gui.runtime

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest

class GeoDataUpdater(private val context: Context) {
    companion object {
        private const val TAG = "GeoDataUpdater"
        private const val BASE_URL =
            "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/"
        private const val BOOTSTRAP_ASSET_DIR = "bootstrap-geodata"
        private const val LAST_UPDATE_FILE = "LAST_UPDATE.txt"
        private const val DEFAULT_PROXY_HOST = "127.0.0.1"
        private const val DEFAULT_PROXY_PORT = 10809
        private const val STALE_AFTER_MS = 24L * 60L * 60L * 1000L
        private val FILES = listOf(
            "geoip.dat",
            "geosite.dat",
        )

        fun geodataDir(context: Context): File =
            File(context.filesDir, "xray/geodata").apply { mkdirs() }

        fun missingFiles(context: Context): List<String> {
            val geodataDir = geodataDir(context)
            return FILES.filter { fileName ->
                val file = File(geodataDir, fileName)
                !file.isFile || file.length() <= 0L
            }
        }

        fun defaultProxyPort(): Int = DEFAULT_PROXY_PORT

        fun needsRefresh(context: Context, staleAfterMs: Long = STALE_AFTER_MS): Boolean {
            val stampFile = File(geodataDir(context), LAST_UPDATE_FILE)
            val timestamp = stampFile.takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.toLongOrNull()
                ?: return true
            return System.currentTimeMillis() - timestamp >= staleAfterMs
        }
    }

    fun installBundledIfMissing(): List<String> {
        val geodataDir = geodataDir(context)
        val installedFiles = mutableListOf<String>()

        for (fileName in FILES) {
            val targetFile = File(geodataDir, fileName)
            if (targetFile.isFile && targetFile.length() > 0L) {
                continue
            }

            val assetPath = "$BOOTSTRAP_ASSET_DIR/$fileName"
            runCatching {
                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onSuccess {
                installedFiles += fileName
                RuntimeEventBus.emit("Installed bundled $fileName from app assets")
            }.onFailure { throwable ->
                RuntimeEventBus.emit("Bundled $fileName not found: ${throwable.message}")
            }
        }

        if (installedFiles.isNotEmpty()) {
            File(geodataDir, LAST_UPDATE_FILE).writeText("0")
        }

        return installedFiles
    }

    fun update(proxyPort: Int? = null) {
        val geodataDir = geodataDir(context)
        val routeLabel = if (proxyPort == null) {
            "direct network"
        } else {
            "local HTTP proxy ${DEFAULT_PROXY_HOST}:$proxyPort"
        }
        RuntimeEventBus.emit("Updating geodata into ${geodataDir.absolutePath} via $routeLabel")

        for (fileName in FILES) {
            downloadAndVerify(fileName, geodataDir, proxyPort)
        }

        File(geodataDir, LAST_UPDATE_FILE).writeText(System.currentTimeMillis().toString())
        RuntimeEventBus.emit("Geodata update finished.")
    }

    private fun downloadAndVerify(fileName: String, outputDir: File, proxyPort: Int?) {
        val tempFile = File(outputDir, "$fileName.download")
        val targetFile = File(outputDir, fileName)

        RuntimeEventBus.emit("Downloading $fileName")
        downloadToFile("$BASE_URL$fileName", tempFile, proxyPort)

        val checksumText = downloadText("$BASE_URL$fileName.sha256sum", proxyPort)
        val expectedHash = checksumText
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.substringBefore(' ')
            ?.lowercase()
            ?: error("Checksum file for $fileName is empty.")

        val actualHash = sha256(tempFile)
        if (actualHash != expectedHash) {
            tempFile.delete()
            error("Checksum mismatch for $fileName. expected=$expectedHash actual=$actualHash")
        }

        tempFile.copyTo(targetFile, overwrite = true)
        tempFile.delete()
        RuntimeEventBus.emit("Verified and installed $fileName")
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
                output.outputStream().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, proxyPort: Int?): HttpURLConnection {
        val proxy = if (proxyPort == null) {
            Proxy.NO_PROXY
        } else {
            Proxy(
                Proxy.Type.HTTP,
                InetSocketAddress(DEFAULT_PROXY_HOST, proxyPort),
            )
        }
        val connection = URL(url).openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val message = "Unexpected HTTP $responseCode for $url"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }
        return connection
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            updateDigest(digest, input)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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
