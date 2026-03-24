package com.example.xray_gui.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors

class XrayRuntimeController(private val context: Context) {
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun start(arguments: Map<*, *>) {
        val profile = arguments["profile"] as? Map<*, *>
        val config = arguments["config"]

        val profileName = (profile?.get("name") as? String)?.ifBlank { null } ?: "Xray Android"
        val runtimeDir = File(context.filesDir, "xray").apply { mkdirs() }
        val configFile = File(runtimeDir, "config.json")
        val profileFile = File(runtimeDir, "profile.json")

        configFile.writeText(JsonValueEncoder.encode(config))
        profileFile.writeText(JsonValueEncoder.encode(profile))

        RuntimeEventBus.emit("wrote config to ${configFile.absolutePath}")
        RuntimeEventBus.updateState("starting")

        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_START
            putExtra(XrayVpnService.EXTRA_PROFILE_NAME, profileName)
            putExtra(XrayVpnService.EXTRA_CONFIG_PATH, configFile.absolutePath)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        RuntimeEventBus.updateState("stopping")
        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun runtimeState(): String = RuntimeEventBus.currentState()

    fun updateGeoData() {
        ioExecutor.execute {
            try {
                RuntimeEventBus.emit("starting geodata update")
                val proxyPort = if (RuntimeEventBus.currentState() == "running") {
                    GeoDataUpdater.defaultProxyPort()
                } else {
                    null
                }
                GeoDataUpdater(context).update(proxyPort = proxyPort)
            } catch (t: Throwable) {
                RuntimeEventBus.emit("geodata-update-error=${t.message}")
            }
        }
    }
}
