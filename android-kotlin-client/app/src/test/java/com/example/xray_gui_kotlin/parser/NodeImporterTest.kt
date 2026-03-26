package com.example.xray_gui_kotlin.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeImporterTest {
    private val importer = NodeImporter()

    @Test
    fun parseOutboundJson_supportsSplitReality() {
        val outboundJson = """
            {
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": "2400:8d60:3::4034:271c",
                    "port": 443,
                    "users": [
                      { "id": "a7050a6f-96df-4e6a-8a5c-fa98664275dc", "encryption": "none" }
                    ]
                  }
                ]
              },
              "streamSettings": {
                "network": "xhttp",
                "security": "reality",
                "realitySettings": {
                  "serverName": "download-installer.cdn.mozilla.net",
                  "fingerprint": "chrome",
                  "publicKey": "upload-public-key"
                },
                "xhttpSettings": {
                  "path": "/c1a13b04daf2",
                  "downloadSettings": {
                    "address": "203.0.113.10",
                    "port": 443,
                    "network": "xhttp",
                    "security": "reality",
                    "realitySettings": {
                      "serverName": "download-installer.cdn.mozilla.net",
                      "fingerprint": "chrome",
                      "publicKey": "download-public-key"
                    },
                    "xhttpSettings": {
                      "path": "/c1a13b04daf2"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val node = importer.parseNode(outboundJson)

        assertEquals("2400:8d60:3::4034:271c", node.address)
        assertEquals("reality", node.security)
        assertEquals("/c1a13b04daf2", node.path)
        assertNotNull(node.downloadSettings)
        assertEquals("203.0.113.10", node.downloadSettings?.address)
        assertEquals("download-public-key", node.downloadSettings?.publicKey)
    }

    @Test
    fun applyPatch_updatesDownloadSettings() {
        val rawLink = "vless://a7050a6f-96df-4e6a-8a5c-fa98664275dc@[2400:8d60:3::4034:271c]:443?encryption=none&type=xhttp&path=%2Fc1a13b04daf2&mode=auto&security=reality&sni=download-installer.cdn.mozilla.net&fp=chrome&pbk=upload-public-key&sid=0c8cf1635139e0b3&spx=%2F#node"
        val patchJson = """
            {
              "downloadSettings": {
                "address": "203.0.113.10",
                "port": 443,
                "network": "xhttp",
                "security": "reality",
                "realitySettings": {
                  "serverName": "download-installer.cdn.mozilla.net",
                  "fingerprint": "chrome",
                  "publicKey": "download-public-key"
                },
                "xhttpSettings": {
                  "path": "/c1a13b04daf2"
                }
              }
            }
        """.trimIndent()

        val node = VlessUriParser().parse(rawLink)
        val patched = importer.applyPatch(node, patchJson)

        assertNotNull(patched.downloadSettings)
        assertEquals("203.0.113.10", patched.downloadSettings?.address)
        assertEquals("download-public-key", patched.downloadSettings?.publicKey)
        assertTrue(importer.looksLikePatch(patchJson))
    }
}
