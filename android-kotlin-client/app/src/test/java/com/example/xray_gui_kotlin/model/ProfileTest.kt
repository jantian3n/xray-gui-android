package com.example.xray_gui_kotlin.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTest {
    @Test
    fun fromMap_ignoresLegacyRuntimeModeField() {
        val source = mapOf(
            "id" to "legacy-id",
            "name" to "legacy-profile",
            "routingPreset" to "CN_DIRECT",
            "runtimeMode" to "LOCAL_PROXY",
            "socksPort" to 10808,
            "httpPort" to 10809,
            "tunMtu" to 1500,
            "node" to mapOf(
                "name" to "node-1",
                "address" to "example.com",
                "port" to 443,
                "id" to "11111111-2222-3333-4444-555555555555",
                "network" to "tcp",
                "security" to "none",
            ),
        )

        val profile = Profile.fromMap(source)

        assertEquals("legacy-id", profile.id)
        assertEquals("legacy-profile", profile.name)
        assertEquals("example.com", profile.node.address)
    }
}
