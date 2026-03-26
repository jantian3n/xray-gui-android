package com.example.xray_gui_kotlin.parser

import com.example.xray_gui_kotlin.model.VlessNode
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class VlessUriParser {
    private val handledKeys = setOf(
        "encryption",
        "flow",
        "type",
        "security",
        "sni",
        "servername",
        "fp",
        "pbk",
        "publickey",
        "sid",
        "shortid",
        "spx",
        "spiderx",
        "host",
        "path",
        "mode",
        "alpn",
    )

    fun parse(raw: String): VlessNode {
        val link = raw.trim()
        require(link.isNotEmpty()) { "Empty link." }

        val uri = URI(link)
        require(uri.scheme.equals("vless", ignoreCase = true)) { "Unsupported scheme: ${uri.scheme}" }

        val userInfo = uri.userInfo.orEmpty()
        require(userInfo.isNotEmpty()) { "Missing VLESS user id." }
        require(!uri.host.isNullOrBlank()) { "Missing server host." }
        require(uri.port > 0) { "Missing server port." }

        val query = parseQuery(uri.rawQuery)

        val network = normalizeNetwork(query["type"].orEmpty().ifBlank { "tcp" })
        val security = query["security"].orEmpty().ifBlank { "none" }
        val serverName = query["sni"].orEmpty().ifBlank { query["servername"].orEmpty() }
        val name = uri.rawFragment?.let { decode(it).trim() }
            ?.ifBlank { null }
            ?: "${uri.host}:${uri.port}"

        val extras = query.filterKeys { it !in handledKeys }

        return VlessNode(
            name = name,
            address = uri.host.orEmpty().trim(),
            port = uri.port,
            id = decode(userInfo).trim(),
            network = network,
            security = security.trim(),
            encryption = query["encryption"].orEmpty().ifBlank { "none" }.trim(),
            flow = query["flow"].orEmpty().trim(),
            serverName = serverName.trim(),
            fingerprint = query["fp"].orEmpty().trim(),
            publicKey = query["pbk"].orEmpty().ifBlank { query["publickey"].orEmpty() }.trim(),
            shortId = query["sid"].orEmpty().ifBlank { query["shortid"].orEmpty() }.trim(),
            spiderX = query["spx"].orEmpty().ifBlank { query["spiderx"].orEmpty() }.trim(),
            host = query["host"].orEmpty().trim(),
            path = query["path"].orEmpty().trim(),
            mode = query["mode"].orEmpty().trim(),
            alpn = parseAlpn(query["alpn"].orEmpty()),
            extras = extras,
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }
        return rawQuery.split('&')
            .mapNotNull { entry ->
                if (entry.isBlank()) return@mapNotNull null
                val parts = entry.split('=', limit = 2)
                val key = decode(parts[0]).lowercase().trim()
                if (key.isEmpty()) return@mapNotNull null
                val value = if (parts.size > 1) decode(parts[1]).trim() else ""
                key to value
            }
            .toMap()
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8)
    }

    private fun normalizeNetwork(value: String): String {
        val lower = value.trim().lowercase()
        return if (lower == "splithttp") "xhttp" else lower.ifBlank { "tcp" }
    }

    private fun parseAlpn(value: String): List<String> {
        if (value.isBlank()) {
            return emptyList()
        }
        return value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
