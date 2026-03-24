package com.example.xray_gui.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.xray_gui.MainActivity
import com.example.xray_gui.R
import java.io.File
import java.net.InetAddress
import java.util.concurrent.Executors

class XrayVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.example.xray_gui.action.START"
        const val ACTION_STOP = "com.example.xray_gui.action.STOP"

        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_CONFIG_PATH = "config_path"

        private const val NOTIFICATION_CHANNEL_ID = "xray_gui_vpn"
        private const val NOTIFICATION_ID = 1001
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var gomobileBridge: GomobileXrayBridge? = null
    private val runtimeExecutor = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopRuntime()
                START_NOT_STICKY
            }
            else -> {
                val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME) ?: "Xray Android"
                val configPath = intent?.getStringExtra(EXTRA_CONFIG_PATH).orEmpty()
                startRuntime(profileName, configPath)
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopXrayRuntime()
        closeTunnel()
        leaveForeground()
        if (RuntimeEventBus.currentState() in setOf("starting", "running", "running-dry", "stopping")) {
            RuntimeEventBus.updateState("stopped")
            RuntimeEventBus.emit("vpn service destroyed")
        }
        runtimeExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRevoke() {
        RuntimeEventBus.emit("vpn revoked by system")
        stopRuntime()
        super.onRevoke()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent)

    private fun startRuntime(profileName: String, configPath: String) {
        ensureNotificationChannel()
        enterForeground(profileName)
        RuntimeEventBus.emit("vpn service starting for profile=$profileName")

        if (configPath.isNotEmpty()) {
            val exists = File(configPath).exists()
            RuntimeEventBus.emit("config-path=$configPath exists=$exists")
        }

        runtimeExecutor.execute {
            startXrayRuntime(profileName, configPath)
        }
    }

    private fun stopRuntime() {
        stopXrayRuntime()
        closeTunnel()
        leaveForeground()
        stopSelf()
        RuntimeEventBus.updateState("stopped")
        RuntimeEventBus.emit("vpn service stopped")
    }

    private fun establishDryRunTunnel(profileName: String): Int {
        return establishTunnel(profileName = profileName, fullTunnel = false)
    }

    private fun establishFullTunnel(profileName: String): Int {
        return establishTunnel(profileName = profileName, fullTunnel = true)
    }

    private fun establishTunnel(profileName: String, fullTunnel: Boolean): Int {
        if (tunInterface != null) {
            val existingFd = tunInterface?.fd ?: -1
            val modeLabel = if (fullTunnel) "vpn" else "dry-run"
            RuntimeEventBus.emit("$modeLabel tunnel already established fd=$existingFd")
            return existingFd
        }

        val builder = Builder()
            .setSession(profileName)
            .setMtu(1500)

        builder.addAddressLiteral("172.19.0.1", 30)
        builder.addAddressLiteral("fdfe:dcba:9876::1", 126)

        if (fullTunnel) {
            builder.addRouteLiteral("0.0.0.0", 0)
            builder.addRouteLiteral("::", 0)
            builder.addDnsServerLiteral("1.1.1.1")
            builder.addDnsServerLiteral("8.8.8.8")
            builder.addDnsServerLiteral("2606:4700:4700::1111")
            builder.addDnsServerLiteral("2001:4860:4860::8888")
            excludeOwnPackageFromVpn(builder)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            RuntimeEventBus.emit("configuring full-tunnel routes for Android VpnService")
        } else {
            builder.addRouteLiteral("198.18.0.0", 15)
            builder.addDnsServerLiteral("1.1.1.1")
            builder.addDnsServerLiteral("223.5.5.5")
            RuntimeEventBus.emit("configuring dry-run routes for Android VpnService")
        }

        tunInterface = builder.establish() ?: error("VpnService.Builder.establish() returned null.")
        val fd = tunInterface?.fd ?: error("VPN tunnel fd is invalid.")
        val modeLabel = if (fullTunnel) "vpn" else "dry-run"
        RuntimeEventBus.emit("$modeLabel tunnel established fd=$fd")
        return fd
    }

    private fun startXrayRuntime(profileName: String, configPath: String) {
        try {
            stopXrayRuntime()
            closeTunnel()

            if (configPath.isEmpty()) {
                handleStartFailure("missing config path")
                return
            }

            val configFile = File(configPath)
            if (!configFile.exists()) {
                handleStartFailure("config file does not exist: $configPath")
                return
            }

            ensureGeoDataReady()

            val bridge = GomobileXrayBridge.createOrNull()
            if (bridge == null) {
                establishDryRunTunnel(profileName)
                RuntimeEventBus.emit("gomobile binding not found. falling back to dry-run tunnel mode.")
                RuntimeEventBus.emit(GomobileXrayBridge.availabilityHint())
                RuntimeEventBus.updateState("running-dry")
                return
            }

            val configJson = configFile.readText()
            val validationError = bridge.validateAndroid(configJson, filesDir.absolutePath)
            if (validationError.isNotEmpty()) {
                handleStartFailure("xray config validation failed: $validationError")
                return
            }

            val tunFd = establishFullTunnel(profileName)
            gomobileBridge = bridge
            val startError = bridge.startAndroid(configJson, filesDir.absolutePath, tunFd)
            if (startError.isNotEmpty()) {
                handleStartFailure("xray runtime start failed: $startError")
                return
            }

            RuntimeEventBus.emit("xray runtime started via gomobile, version=${bridge.version()}")
            RuntimeEventBus.updateState("running")
            scheduleBackgroundGeoDataRefresh()
        } catch (t: Throwable) {
            handleStartFailure("unexpected xray runtime error: ${t.message}")
        }
    }

    private fun ensureGeoDataReady() {
        val updater = GeoDataUpdater(this)
        val installedFiles = updater.installBundledIfMissing()
        if (installedFiles.isNotEmpty()) {
            RuntimeEventBus.emit("bootstrap geodata installed: ${installedFiles.joinToString()}")
        }

        val missingFiles = GeoDataUpdater.missingFiles(this)
        if (missingFiles.isEmpty()) {
            RuntimeEventBus.emit("geodata ready at ${GeoDataUpdater.geodataDir(this).absolutePath}")
            return
        }

        RuntimeEventBus.emit(
            "geodata missing: ${missingFiles.joinToString()}. starting bootstrap download.",
        )
        updater.update()
    }

    private fun scheduleBackgroundGeoDataRefresh() {
        runtimeExecutor.execute {
            if (RuntimeEventBus.currentState() != "running") {
                return@execute
            }
            if (!GeoDataUpdater.needsRefresh(this)) {
                RuntimeEventBus.emit("geodata refresh skipped: bootstrap data is still fresh enough")
                return@execute
            }
            try {
                RuntimeEventBus.emit("starting background geodata refresh through local proxy")
                GeoDataUpdater(this).update(proxyPort = GeoDataUpdater.defaultProxyPort())
            } catch (t: Throwable) {
                RuntimeEventBus.emit("background geodata refresh failed: ${t.message}")
            }
        }
    }

    private fun stopXrayRuntime() {
        val bridge = gomobileBridge ?: return
        try {
            val stopError = bridge.stop()
            if (stopError.isNotEmpty()) {
                RuntimeEventBus.emit("xray runtime stop failed: $stopError")
            } else {
                RuntimeEventBus.emit("xray runtime stopped")
            }
        } catch (t: Throwable) {
            RuntimeEventBus.emit("unexpected xray runtime stop error: ${t.message}")
        }
        gomobileBridge = null
    }

    private fun handleStartFailure(message: String) {
        stopXrayRuntime()
        closeTunnel()
        RuntimeEventBus.updateState("error")
        RuntimeEventBus.emit(message)
        leaveForeground()
        stopSelf()
    }

    private fun closeTunnel() {
        val currentTunnel = tunInterface ?: return
        val fd = currentTunnel.fd
        currentTunnel.close()
        tunInterface = null
        RuntimeEventBus.emit("vpn tunnel closed fd=$fd")
    }

    private fun leaveForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun excludeOwnPackageFromVpn(builder: Builder) {
        try {
            builder.addDisallowedApplication(packageName)
            RuntimeEventBus.emit("excluded app package from VPN to avoid routing loops: $packageName")
        } catch (t: Throwable) {
            throw IllegalStateException("failed to exclude app package from VPN: $packageName", t)
        }
    }

    private fun Builder.addAddressLiteral(address: String, prefixLength: Int): Builder = apply {
        addAddress(InetAddress.getByName(address), prefixLength)
    }

    private fun Builder.addRouteLiteral(address: String, prefixLength: Int): Builder = apply {
        addRoute(InetAddress.getByName(address), prefixLength)
    }

    private fun Builder.addDnsServerLiteral(address: String): Builder = apply {
        addDnsServer(InetAddress.getByName(address))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun enterForeground(profileName: String) {
        val notification = buildNotification(profileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(profileName: String): Notification {
        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, profileName))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
