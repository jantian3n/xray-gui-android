package com.example.xray_gui_kotlin.parser

import com.example.xray_gui_kotlin.model.VlessNode
import com.example.xray_gui_kotlin.model.XhttpDownloadSettings
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class NodeImporter(
    private val parser: VlessUriParser = VlessUriParser(),
) {
    fun parseNode(raw: String): VlessNode {
        val text = raw.trim()
        require(text.isNotEmpty()) { "没有可导入的内容。" }
        if (text.startsWith("vless://", ignoreCase = true)) {
            return parser.parse(text)
        }

        val json = parseJsonObject(text)
        if (looksLikeOutbound(json)) {
            return parseOutbound(json)
        }
        if (looksLikePatch(json)) {
            throw IllegalArgumentException("这是 split patch JSON，请先选中一个已有节点后应用补丁。")
        }
        throw IllegalArgumentException("暂不支持这类导入内容。请粘贴 vless:// 或 client_outbound.json。")
    }

    fun applyPatch(baseNode: VlessNode, raw: String): VlessNode {
        val text = raw.trim()
        require(text.isNotEmpty()) { "没有可应用的补丁内容。" }

        val json = parseJsonObject(text)
        require(looksLikePatch(json)) { "补丁内容缺少 downloadSettings。" }

        val downloadJson = json.getObject("downloadSettings")
            ?: error("downloadSettings 必须是对象。")

        return baseNode.copy(
            downloadSettings = parseDownloadSettings(downloadJson),
        )
    }

    fun looksLikePatch(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty() || text.startsWith("vless://", ignoreCase = true)) {
            return false
        }
        return runCatching {
            looksLikePatch(parseJsonObject(text))
        }.getOrDefault(false)
    }

    private fun parseOutbound(json: JsonObject): VlessNode {
        val settings = json.getObject("settings") ?: error("settings 必须是对象。")
        val vnext = settings.getArray("vnext") ?: error("settings.vnext 必须是数组。")
        require(vnext.size() > 0) { "VLESS outbound 缺少 vnext。" }

        val endpoint = vnext.getObject(0) ?: error("settings.vnext[0] 必须是对象。")
        val users = endpoint.getArray("users") ?: error("settings.vnext[0].users 必须是数组。")
        require(users.size() > 0) { "VLESS outbound 缺少 users。" }

        val user = users.getObject(0) ?: error("settings.vnext[0].users[0] 必须是对象。")
        val streamSettings = json.getObject("streamSettings") ?: JsonObject()
        val xhttpSettings = pickXhttpSettings(streamSettings)

        val security = streamSettings.getString("security").ifBlank { "none" }
        val realitySettings = streamSettings.getObject("realitySettings") ?: JsonObject()
        val tlsSettings = streamSettings.getObject("tlsSettings") ?: JsonObject()

        return VlessNode(
            name = json.getString("tag").ifBlank {
                "${endpoint.getString("address")}:${endpoint.getInt("port", 443)}"
            },
            address = endpoint.getString("address"),
            port = endpoint.getInt("port", 443),
            id = user.getString("id"),
            network = streamSettings.getString("network").ifBlank { "tcp" },
            security = security,
            encryption = user.getString("encryption").ifBlank { "none" },
            flow = user.getString("flow"),
            serverName = resolveServerName(security, realitySettings, tlsSettings),
            fingerprint = resolveFingerprint(security, realitySettings, tlsSettings),
            publicKey = realitySettings.getString("publicKey"),
            shortId = realitySettings.getString("shortId"),
            spiderX = realitySettings.getString("spiderX"),
            host = xhttpSettings.getString("host"),
            path = xhttpSettings.getString("path"),
            mode = xhttpSettings.getString("mode"),
            alpn = parseStringList(tlsSettings.getElement("alpn")),
            downloadSettings = xhttpSettings.getObject("downloadSettings")?.let { parseDownloadSettings(it) },
        )
    }

    private fun parseDownloadSettings(json: JsonObject): XhttpDownloadSettings {
        val security = json.getString("security").ifBlank { "none" }
        val realitySettings = json.getObject("realitySettings") ?: JsonObject()
        val tlsSettings = json.getObject("tlsSettings") ?: JsonObject()
        val xhttpSettings = pickXhttpSettings(json)

        return XhttpDownloadSettings(
            address = json.getString("address"),
            port = json.getInt("port", 443),
            network = json.getString("network").ifBlank { "xhttp" },
            security = security,
            serverName = resolveServerName(security, realitySettings, tlsSettings),
            fingerprint = resolveFingerprint(security, realitySettings, tlsSettings),
            publicKey = realitySettings.getString("publicKey"),
            shortId = realitySettings.getString("shortId"),
            spiderX = realitySettings.getString("spiderX"),
            host = xhttpSettings.getString("host"),
            path = xhttpSettings.getString("path"),
            mode = xhttpSettings.getString("mode"),
            alpn = parseStringList(tlsSettings.getElement("alpn")),
        )
    }

    private fun pickXhttpSettings(json: JsonObject): JsonObject {
        return json.getObject("xhttpSettings")
            ?: json.getObject("splithttpSettings")
            ?: JsonObject()
    }

    private fun looksLikeOutbound(json: JsonObject): Boolean {
        return json.getString("protocol").equals("vless", ignoreCase = true) && json.getObject("settings") != null
    }

    private fun looksLikePatch(json: JsonObject): Boolean {
        return json.getObject("downloadSettings") != null
    }

    private fun parseJsonObject(raw: String): JsonObject {
        val decoded = JsonParser.parseString(raw)
        require(decoded.isJsonObject) { "导入内容必须是 JSON 对象。" }
        return decoded.asJsonObject
    }

    private fun resolveServerName(
        security: String,
        realitySettings: JsonObject,
        tlsSettings: JsonObject,
    ): String {
        return when {
            security.equals("reality", ignoreCase = true) -> realitySettings.getString("serverName")
            security.equals("tls", ignoreCase = true) -> tlsSettings.getString("serverName")
            else -> ""
        }
    }

    private fun resolveFingerprint(
        security: String,
        realitySettings: JsonObject,
        tlsSettings: JsonObject,
    ): String {
        return when {
            security.equals("reality", ignoreCase = true) -> realitySettings.getString("fingerprint")
            security.equals("tls", ignoreCase = true) -> tlsSettings.getString("fingerprint")
            else -> ""
        }
    }

    private fun parseStringList(value: JsonElement?): List<String> {
        return when {
            value == null || value.isJsonNull -> emptyList()
            value.isJsonArray -> value.asJsonArray.mapNotNull {
                if (!it.isJsonNull) runCatching { it.asString.trim() }.getOrNull() else null
            }.filter { it.isNotEmpty() }
            value.isJsonPrimitive -> runCatching { value.asString }.getOrDefault("")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

}

private fun JsonObject.getElement(key: String): JsonElement? {
    return if (has(key)) get(key) else null
}

private fun JsonObject.getObject(key: String): JsonObject? {
    val value = getElement(key) ?: return null
    return if (value.isJsonObject) value.asJsonObject else null
}

private fun JsonObject.getArray(key: String): JsonArray? {
    val value = getElement(key) ?: return null
    return if (value.isJsonArray) value.asJsonArray else null
}

private fun JsonObject.getString(key: String): String {
    val value = getElement(key) ?: return ""
    if (value.isJsonNull) {
        return ""
    }
    return runCatching { value.asString }.getOrDefault("")
}

private fun JsonObject.getInt(key: String, defaultValue: Int): Int {
    val value = getElement(key) ?: return defaultValue
    if (value.isJsonNull) {
        return defaultValue
    }
    return runCatching { value.asInt }.getOrDefault(defaultValue)
}

private fun JsonArray.getObject(index: Int): JsonObject? {
    if (index < 0 || index >= size()) {
        return null
    }
    val value = get(index)
    return if (value.isJsonObject) value.asJsonObject else null
}
