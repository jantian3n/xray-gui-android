package com.example.xray_gui_kotlin.config

import com.example.xray_gui_kotlin.model.Profile
import com.example.xray_gui_kotlin.model.VlessNode
import com.example.xray_gui_kotlin.model.XhttpDownloadSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class XrayConfigCompilerTest {
    @Test
    fun compile_supportsSplitTlsDownloadSettings() {
        val node = VlessNode(
            name = "cdn-tls-split",
            address = "cdn.example.com",
            port = 443,
            id = "11111111-2222-3333-4444-555555555555",
            network = "xhttp",
            security = "tls",
            serverName = "cdn.example.com",
            fingerprint = "chrome",
            path = "/edge-path",
            mode = "auto",
            alpn = listOf("h2"),
            downloadSettings = XhttpDownloadSettings(
                address = "origin.example.com",
                port = 443,
                network = "xhttp",
                security = "tls",
                serverName = "origin.example.com",
                fingerprint = "chrome",
                path = "/edge-path",
                mode = "auto",
                alpn = listOf("h2"),
            ),
        )

        val config = XrayConfigCompiler().compile(Profile.fromNode(node))
        val outbounds = config["outbounds"] as List<*>
        val outbound = outbounds.first() as Map<*, *>
        val streamSettings = outbound["streamSettings"] as Map<*, *>
        val tlsSettings = streamSettings["tlsSettings"] as Map<*, *>
        val xhttpSettings = streamSettings["xhttpSettings"] as Map<*, *>
        val downloadSettings = xhttpSettings["downloadSettings"] as Map<*, *>
        val downloadTls = downloadSettings["tlsSettings"] as Map<*, *>

        assertEquals("tls", streamSettings["security"])
        assertEquals("cdn.example.com", tlsSettings["serverName"])
        assertEquals("origin.example.com", downloadSettings["address"])
        assertEquals("origin.example.com", downloadTls["serverName"])
        assertNotNull(downloadSettings["xhttpSettings"])
    }
}
