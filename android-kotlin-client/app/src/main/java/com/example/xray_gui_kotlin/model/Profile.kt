package com.example.xray_gui_kotlin.model

data class Profile(
    val id: String,
    val name: String,
    val node: VlessNode,
    val routingPreset: RoutingPreset = RoutingPreset.CN_DIRECT,
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val tunMtu: Int = 1500,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "routingPreset" to routingPreset.name,
        "socksPort" to socksPort,
        "httpPort" to httpPort,
        "tunMtu" to tunMtu,
        "node" to node.toMap(),
    )

    companion object {
        fun fromNode(
            node: VlessNode,
            routingPreset: RoutingPreset = RoutingPreset.CN_DIRECT,
        ): Profile {
            val safeName = node.name.trim().ifBlank { "${node.address}:${node.port}" }
            return Profile(
                id = slugify(safeName),
                name = safeName,
                node = node,
                routingPreset = routingPreset,
            )
        }

        fun fromMap(source: Map<String, Any?>): Profile {
            val nodeMap = source["node"] as? Map<*, *>
                ?: error("Profile.node is required")
            return Profile(
                id = source["id"].toStringOrEmpty(),
                name = source["name"].toStringOrEmpty(),
                node = VlessNode.fromMap(nodeMap as Map<String, Any?>),
                routingPreset = source["routingPreset"].toRoutingPreset(),
                socksPort = source["socksPort"].toIntOr(10808),
                httpPort = source["httpPort"].toIntOr(10809),
                tunMtu = source["tunMtu"].toIntOr(1500),
            )
        }

        private fun slugify(value: String): String {
            return value.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .replace(Regex("^-+|-+$"), "")
        }
    }
}

private fun Any?.toStringOrEmpty(): String = this?.toString()?.trim().orEmpty()

private fun Any?.toIntOr(default: Int): Int {
    return when (this) {
        is Number -> toInt()
        is String -> trim().toIntOrNull() ?: default
        else -> default
    }
}

private fun Any?.toRoutingPreset(): RoutingPreset {
    return RoutingPreset.entries.firstOrNull { it.name == this?.toString() } ?: RoutingPreset.CN_DIRECT
}
