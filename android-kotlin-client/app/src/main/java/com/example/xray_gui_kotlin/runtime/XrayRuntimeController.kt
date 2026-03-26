package com.example.xray_gui_kotlin.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.xray_gui_kotlin.data.JsonValueEncoder
import com.example.xray_gui_kotlin.model.Profile
import java.io.File
import java.util.concurrent.Executors

class XrayRuntimeController(private val context: Context) {
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun start(profile: Profile, config: Map<String, Any?>) {
        val runtimeDir = File(context.filesDir, "xray").apply { mkdirs() }
        val configFile = File(runtimeDir, "config.json")
        val profileFile = File(runtimeDir, "profile.json")

        configFile.writeText(JsonValueEncoder.encode(config))
        profileFile.writeText(JsonValueEncoder.encode(profile.toMap()))

        RuntimeLogBus.emit("wrote config to ${configFile.absolutePath}")
        RuntimeLogBus.updateState("starting")

        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_START
            putExtra(XrayVpnService.EXTRA_PROFILE_NAME, profile.name)
            putExtra(XrayVpnService.EXTRA_CONFIG_PATH, configFile.absolutePath)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        RuntimeLogBus.updateState("stopping")
        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun updateGeoData() {
        ioExecutor.execute {
            try {
                RuntimeLogBus.emit("starting geodata update")
                val proxyPort = if (RuntimeLogBus.state.value == "running") {
                    GeoDataUpdater.defaultProxyPort()
                } else {
                    null
                }
                GeoDataUpdater(context).update(proxyPort)
            } catch (t: Throwable) {
                RuntimeLogBus.emit("geodata-update-error=${t.message}")
            }
        }
    }
}
