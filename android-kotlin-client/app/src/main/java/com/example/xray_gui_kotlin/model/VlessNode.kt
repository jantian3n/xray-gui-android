package com.example.xray_gui_kotlin.model

data class VlessNode(
    val name: String,
    val address: String,
    val port: Int,
    val id: String,
    val network: String,
    val security: String,
    val encryption: String = "none",
    val flow: String = "",
    val serverName: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val host: String = "",
    val path: String = "",
    val mode: String = "",
    val alpn: List<String> = emptyList(),
    val downloadSettings: XhttpDownloadSettings? = null,
    val extras: Map<String, String> = emptyMap(),
) {
    val isReality: Boolean get() = security.equals("reality", ignoreCase = true)
    val isTls: Boolean get() = security.equals("tls", ignoreCase = true)
    val isXhttp: Boolean get() = network.equals("xhttp", true) || network.equals("splithttp", true)

    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "address" to address,
        "port" to port,
        "id" to id,
        "network" to network,
        "security" to security,
        "encryption" to encryption,
        "flow" to flow,
        "serverName" to serverName,
        "fingerprint" to fingerprint,
        "publicKey" to publicKey,
        "shortId" to shortId,
        "spiderX" to spiderX,
        "host" to host,
        "path" to path,
        "mode" to mode,
        "alpn" to alpn,
        "downloadSettings" to downloadSettings?.toMap(),
        "extras" to extras,
    )

    companion object {
        fun fromMap(source: Map<String, Any?>): VlessNode {
            val download = source["downloadSettings"] as? Map<*, *>
            val extraMap = (source["extras"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k?.toString()?.trim().orEmpty()
                if (key.isEmpty()) null else key to v?.toString().orEmpty()
            }?.toMap().orEmpty()

            return VlessNode(
                name = source["name"].toStringOrEmpty(),
                address = source["address"].toStringOrEmpty(),
                port = source["port"].toIntOr(443),
                id = source["id"].toStringOrEmpty(),
                network = source["network"].toStringOr("tcp"),
                security = source["security"].toStringOr("none"),
                encryption = source["encryption"].toStringOr("none"),
                flow = source["flow"].toStringOrEmpty(),
                serverName = source["serverName"].toStringOrEmpty(),
                fingerprint = source["fingerprint"].toStringOrEmpty(),
                publicKey = source["publicKey"].toStringOrEmpty(),
                shortId = source["shortId"].toStringOrEmpty(),
                spiderX = source["spiderX"].toStringOrEmpty(),
                host = source["host"].toStringOrEmpty(),
                path = source["path"].toStringOrEmpty(),
                mode = source["mode"].toStringOrEmpty(),
                alpn = source["alpn"].toStringList(),
                downloadSettings = download?.let { XhttpDownloadSettings.fromMap(it as Map<String, Any?>) },
                extras = extraMap,
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
