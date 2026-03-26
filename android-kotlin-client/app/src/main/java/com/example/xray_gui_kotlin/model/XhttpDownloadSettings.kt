package com.example.xray_gui_kotlin.model

data class XhttpDownloadSettings(
    val address: String,
    val port: Int,
    val network: String = "xhttp",
    val security: String = "reality",
    val serverName: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val host: String = "",
    val path: String = "",
    val mode: String = "",
    val alpn: List<String> = emptyList(),
) {
    val isReality: Boolean get() = security.equals("reality", ignoreCase = true)
    val isTls: Boolean get() = security.equals("tls", ignoreCase = true)
    val isXhttp: Boolean get() = network.equals("xhttp", true) || network.equals("splithttp", true)

    fun toMap(): Map<String, Any?> = mapOf(
        "address" to address,
        "port" to port,
        "network" to network,
        "security" to security,
        "serverName" to serverName,
        "fingerprint" to fingerprint,
        "publicKey" to publicKey,
        "shortId" to shortId,
        "spiderX" to spiderX,
        "host" to host,
        "path" to path,
        "mode" to mode,
        "alpn" to alpn,
    )

    companion object {
        fun fromMap(source: Map<String, Any?>): XhttpDownloadSettings {
            return XhttpDownloadSettings(
                address = source["address"].toStringOrEmpty(),
                port = source["port"].toIntOr(443),
                network = source["network"].toStringOr("xhttp"),
                security = source["security"].toStringOr("reality"),
                serverName = source["serverName"].toStringOrEmpty(),
                fingerprint = source["fingerprint"].toStringOrEmpty(),
                publicKey = source["publicKey"].toStringOrEmpty(),
                shortId = source["shortId"].toStringOrEmpty(),
                spiderX = source["spiderX"].toStringOrEmpty(),
                host = source["host"].toStringOrEmpty(),
                path = source["path"].toStringOrEmpty(),
                mode = source["mode"].toStringOrEmpty(),
                alpn = source["alpn"].toStringList(),
            )
        }
    }
}

private fun Any?.toStringOrEmpty(): String = this?.toString()?.trim().orEmpty()

private fun Any?.toStringOr(default: String): String {
    val value = this?.toString()?.trim().orEmpty()
    return if (value.isEmpty()) default else value
}

private fun Any?.toIntOr(default: Int): Int {
    return when (this) {
        is Number -> toInt()
        is String -> trim().toIntOrNull() ?: default
        else -> default
    }
}

private fun Any?.toStringList(): List<String> {
    return when (this) {
        is List<*> -> mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        is String -> split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
}
