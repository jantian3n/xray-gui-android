package com.example.xray_gui_kotlin.config

import com.example.xray_gui_kotlin.model.Profile
import com.example.xray_gui_kotlin.model.RoutingPreset
import com.example.xray_gui_kotlin.model.RuntimeMode
import com.example.xray_gui_kotlin.model.VlessNode
import com.example.xray_gui_kotlin.model.XhttpDownloadSettings

class XrayConfigCompiler {
    fun compile(profile: Profile, hasGeoData: Boolean = true): Map<String, Any?> {
        validateNode(profile.node)

        return mapOf(
            "log" to mapOf("loglevel" to "info"),
            "dns" to if (hasGeoData) buildDns(profile.routingPreset) else buildBootstrapDns(),
            "inbounds" to buildInbounds(profile),
            "outbounds" to buildOutbounds(profile.node),
            "routing" to mapOf(
                "domainStrategy" to "IPIfNonMatch",
                "domainMatcher" to "mph",
                "rules" to if (hasGeoData) buildRoutingRules(profile.routingPreset) else buildBootstrapRoutingRules(),
            ),
        )
    }

    private fun buildBootstrapDns(): Map<String, Any?> {
        return mapOf(
            "queryStrategy" to "UseIP",
            "servers" to listOf(
                "223.5.5.5",
                "https://1.1.1.1/dns-query",
                "8.8.8.8",
            ),
        )
    }

    private fun buildBootstrapRoutingRules(): List<Map<String, Any?>> {
        return listOf(
            mapOf(
                "type" to "field",
                "outboundTag" to "direct",
                "ip" to listOf(
                    "10.0.0.0/8",
                    "172.16.0.0/12",
                    "192.168.0.0/16",
                    "127.0.0.0/8",
                    "169.254.0.0/16",
                    "::1/128",
                    "fc00::/7",
                    "fe80::/10",
                ),
            ),
            mapOf(
                "type" to "field",
                "outboundTag" to "proxy",
                "network" to "tcp,udp",
            ),
        )
    }

    private fun validateNode(node: VlessNode) {
        validateSecurity(
            label = "Upload",
            security = node.security,
            serverName = node.serverName,
            fingerprint = node.fingerprint,
            publicKey = node.publicKey,
        )

        if (node.isXhttp && node.path.isBlank()) {
            throw IllegalArgumentException("XHTTP requires a path.")
        }

        node.downloadSettings?.let { download ->
            if (!node.isXhttp) {
                throw IllegalArgumentException("Split download settings require XHTTP on upload.")
            }
            validateDownloadSettings(download)
        }
    }

    private fun validateDownloadSettings(download: XhttpDownloadSettings) {
        require(download.address.isNotBlank()) { "Split download requires an address." }
        require(download.port in 1..65535) { "Split download requires a valid port." }
        require(download.isXhttp) { "Split download currently expects network=xhttp." }
        require(download.path.isNotBlank()) { "Split download XHTTP requires a path." }

        validateSecurity(
            label = "Download",
            security = download.security,
            serverName = download.serverName,
            fingerprint = download.fingerprint,
            publicKey = download.publicKey,
        )
    }

    private fun validateSecurity(
        label: String,
        security: String,
        serverName: String,
        fingerprint: String,
        publicKey: String,
    ) {
        when (security.lowercase()) {
            "reality" -> {
                require(publicKey.isNotBlank()) { "$label REALITY requires public key." }
                require(serverName.isNotBlank()) { "$label REALITY requires serverName or sni." }
                require(fingerprint.isNotBlank()) { "$label REALITY requires fingerprint." }
            }
            "tls" -> {
                require(serverName.isNotBlank()) { "$label TLS requires serverName or sni." }
                require(fingerprint.isNotBlank()) { "$label TLS requires fingerprint." }
            }
        }
    }

    private fun buildDns(preset: RoutingPreset): Map<String, Any?> {
        return when (preset) {
            RoutingPreset.CN_DIRECT -> mapOf(
                "hosts" to mapOf("geosite:category-ads-all" to "127.0.0.1"),
                "queryStrategy" to "UseIP",
                "servers" to listOf(
                    mapOf(
                        "address" to "223.5.5.5",
                        "port" to 53,
                        "domains" to listOf("geosite:cn", "geosite:private"),
                        "expectIPs" to listOf("geoip:cn", "geoip:private"),
                    ),
                    mapOf(
                        "address" to "https://1.1.1.1/dns-query",
                        "domains" to listOf("geosite:geolocation-!cn"),
                        "expectIPs" to listOf("geoip:!cn", "geoip:private"),
                    ),
                    "8.8.8.8",
                ),
            )
            RoutingPreset.GLOBAL_PROXY -> mapOf(
                "hosts" to mapOf("geosite:category-ads-all" to "127.0.0.1"),
                "queryStrategy" to "UseIP",
                "servers" to listOf(
                    "https://1.1.1.1/dns-query",
                    "223.5.5.5",
                    "8.8.8.8",
                ),
            )
            RoutingPreset.GFW_LIKE -> mapOf(
                "hosts" to mapOf("geosite:category-ads-all" to "127.0.0.1"),
                "queryStrategy" to "UseIP",
                "servers" to listOf(
                    "https://1.1.1.1/dns-query",
                    "223.5.5.5",
                    "8.8.8.8",
                ),
            )
        }
    }

    private fun buildInbounds(profile: Profile): List<Map<String, Any?>> {
        val localInbounds = listOf(
            mapOf(
                "tag" to "socks-in",
                "listen" to "127.0.0.1",
                "port" to profile.socksPort,
                "protocol" to "socks",
                "settings" to mapOf(
                    "udp" to true,
                    "auth" to "noauth",
                ),
            ),
            mapOf(
                "tag" to "http-in",
                "listen" to "127.0.0.1",
                "port" to profile.httpPort,
                "protocol" to "http",
                "settings" to emptyMap<String, Any>(),
            ),
        )

        return if (profile.runtimeMode == RuntimeMode.VPN) {
            listOf(
                mapOf(
                    "tag" to "tun-in",
                    "port" to 0,
                    "protocol" to "tun",
                    "settings" to mapOf(
                        "name" to "xray0",
                        "MTU" to profile.tunMtu,
                    ),
                ),
            ) + localInbounds
        } else {
            localInbounds
        }
    }

    private fun buildOutbounds(node: VlessNode): List<Map<String, Any?>> {
        return listOf(
            mapOf(
                "tag" to "proxy",
                "protocol" to "vless",
                "settings" to mapOf(
                    "vnext" to listOf(
                        mapOf(
                            "address" to node.address,
                            "port" to node.port,
                            "users" to listOf(
                                removeEmpty(
                                    mutableMapOf(
                                        "id" to node.id,
                                        "encryption" to node.encryption,
                                        "flow" to node.flow,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                "streamSettings" to buildStreamSettings(node),
            ),
            mapOf(
                "tag" to "direct",
                "protocol" to "freedom",
                "settings" to emptyMap<String, Any>(),
            ),
            mapOf(
                "tag" to "block",
                "protocol" to "blackhole",
                "settings" to emptyMap<String, Any>(),
            ),
        )
    }

    private fun buildStreamSettings(node: VlessNode): Map<String, Any?> {
        return removeEmpty(
            mutableMapOf(
                "network" to node.network,
                "security" to node.security,
                "tlsSettings" to if (node.isTls) removeEmpty(
                    mutableMapOf(
                        "serverName" to node.serverName,
                        "fingerprint" to node.fingerprint,
                        "alpn" to node.alpn,
                    ),
                ) else null,
                "realitySettings" to if (node.isReality) removeEmpty(
                    mutableMapOf(
                        "serverName" to node.serverName,
                        "fingerprint" to node.fingerprint,
                        "publicKey" to node.publicKey,
                        "shortId" to node.shortId,
                        "spiderX" to node.spiderX,
                    ),
                ) else null,
                "xhttpSettings" to if (node.isXhttp) removeEmpty(
                    mutableMapOf(
                        "host" to node.host,
                        "path" to node.path,
                        "mode" to node.mode,
                        "downloadSettings" to node.downloadSettings?.let { buildDownloadSettings(it) },
                    ),
                ) else null,
            ),
        )
    }

    private fun buildDownloadSettings(download: XhttpDownloadSettings): Map<String, Any?> {
        return removeEmpty(
            mutableMapOf(
                "address" to download.address,
                "port" to download.port,
                "network" to download.network,
                "security" to download.security,
                "tlsSettings" to if (download.isTls) removeEmpty(
                    mutableMapOf(
                        "serverName" to download.serverName,
                        "fingerprint" to download.fingerprint,
                        "alpn" to download.alpn,
                    ),
                ) else null,
                "realitySettings" to if (download.isReality) removeEmpty(
                    mutableMapOf(
                        "serverName" to download.serverName,
                        "fingerprint" to download.fingerprint,
                        "publicKey" to download.publicKey,
                        "shortId" to download.shortId,
                        "spiderX" to download.spiderX,
                    ),
                ) else null,
                "xhttpSettings" to if (download.isXhttp) removeEmpty(
                    mutableMapOf(
                        "host" to download.host,
                        "path" to download.path,
                        "mode" to download.mode,
                    ),
                ) else null,
            ),
        )
    }

    private fun buildRoutingRules(preset: RoutingPreset): List<Map<String, Any?>> {
        return when (preset) {
            RoutingPreset.CN_DIRECT -> listOf(
                mapOf(
                    "type" to "field",
                    "outboundTag" to "block",
                    "domain" to listOf("geosite:category-ads-all"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "direct",
                    "domain" to listOf("geosite:private", "geosite:apple-cn", "geosite:google-cn", "geosite:tld-cn"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "proxy",
                    "domain" to listOf("geosite:geolocation-!cn"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "direct",
                    "domain" to listOf("geosite:cn"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "direct",
                    "ip" to listOf("geoip:cn", "geoip:private"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "proxy",
                    "network" to "tcp,udp",
                ),
            )
            RoutingPreset.GLOBAL_PROXY -> listOf(
                mapOf(
                    "type" to "field",
                    "outboundTag" to "block",
                    "domain" to listOf("geosite:category-ads-all"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "direct",
                    "domain" to listOf("geosite:private"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "direct",
                    "ip" to listOf("geoip:private"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "proxy",
                    "network" to "tcp,udp",
                ),
            )
            RoutingPreset.GFW_LIKE -> listOf(
                mapOf(
                    "type" to "field",
                    "outboundTag" to "block",
                    "domain" to listOf("geosite:category-ads-all"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "proxy",
                    "domain" to listOf("geosite:gfw"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "proxy",
                    "ip" to listOf("geoip:telegram"),
                ),
                mapOf(
                    "type" to "field",
                    "outboundTag" to "direct",
                    "network" to "tcp,udp",
                ),
            )
        }
    }

    private fun removeEmpty(source: MutableMap<String, Any?>): Map<String, Any?> {
        val iterator = source.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val value = entry.value
            val shouldRemove = when (value) {
                null -> true
                is String -> value.isBlank()
                is List<*> -> value.isEmpty()
                is Map<*, *> -> value.isEmpty()
                else -> false
            }
            if (shouldRemove) {
                iterator.remove()
            }
        }
        return source
    }
}
