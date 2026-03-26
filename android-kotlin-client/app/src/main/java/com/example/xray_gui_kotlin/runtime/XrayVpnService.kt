package com.example.xray_gui_kotlin.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.xray_gui_kotlin.MainActivity
import com.example.xray_gui_kotlin.R
import java.io.File
import java.net.InetAddress
import java.util.concurrent.Executors

class XrayVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.example.xray_gui_kotlin.action.START"
        const val ACTION_STOP = "com.example.xray_gui_kotlin.action.STOP"

        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_CONFIG_PATH = "config_path"

        private const val NOTIFICATION_CHANNEL_ID = "xray_kotlin_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val OPEN_APP_REQUEST_CODE = 100
        private const val STOP_SERVICE_REQUEST_CODE = 101
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var gomobileBridge: GomobileXrayBridge? = null
    private var currentProfileName: String = "Xray Android"
    private var currentConfigPath: String = ""
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
                currentProfileName = profileName
                currentConfigPath = configPath
                startRuntime(profileName, configPath)
                START_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (RuntimeLogBus.state.value !in setOf("starting", "running", "running-dry")) {
            return
        }

        RuntimeLogBus.emit("task removed, keep vpn service alive")
        val restartIntent = Intent(applicationContext, XrayVpnService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_PROFILE_NAME, currentProfileName)
            putExtra(EXTRA_CONFIG_PATH, currentConfigPath)
        }
        ContextCompat.startForegroundService(applicationContext, restartIntent)
    }

    override fun onDestroy() {
        stopXrayRuntime()
        closeTunnel()
        leaveForeground()
        if (RuntimeLogBus.state.value in setOf("starting", "running", "running-dry", "stopping")) {
            RuntimeLogBus.updateState("stopped")
            RuntimeLogBus.emit("vpn service destroyed")
        }
        runtimeExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRevoke() {
        RuntimeLogBus.emit("vpn revoked by system")
        stopRuntime()
        super.onRevoke()
    }

    private fun startRuntime(profileName: String, configPath: String) {
        ensureNotificationChannel()
        enterForeground(profileName)
        RuntimeLogBus.emit("vpn service starting for profile=$profileName")

        runtimeExecutor.execute {
            startXrayRuntime(profileName, configPath)
        }
    }

    private fun stopRuntime() {
        stopXrayRuntime()
        closeTunnel()
        leaveForeground()
        stopSelf()
        currentConfigPath = ""
        RuntimeLogBus.updateState("stopped")
        RuntimeLogBus.emit("vpn service stopped")
    }

    private fun establishDryRunTunnel(profileName: String): Int = establishTunnel(profileName, false)

    private fun establishFullTunnel(profileName: String): Int = establishTunnel(profileName, true)

    private fun establishTunnel(profileName: String, fullTunnel: Boolean): Int {
        tunInterface?.let { existing ->
            val fd = existing.fd
            RuntimeLogBus.emit("tunnel already established fd=$fd")
            return fd
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
            RuntimeLogBus.emit("configuring full-tunnel routes")
        } else {
            builder.addRouteLiteral("198.18.0.0", 15)
            builder.addDnsServerLiteral("1.1.1.1")
            RuntimeLogBus.emit("configuring dry-run routes")
        }

        tunInterface = builder.establish() ?: error("VpnService.Builder.establish() returned null")
        val fd = tunInterface?.fd ?: error("VPN tunnel fd is invalid")
        RuntimeLogBus.emit("tunnel established fd=$fd")
        return fd
    }

    private fun startXrayRuntime(profileName: String, configPath: String) {
        try {
            stopXrayRuntime()
            closeTunnel()

            if (configPath.isBlank()) {
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
                RuntimeLogBus.emit("gomobile binding not found. running dry mode")
                RuntimeLogBus.emit(GomobileXrayBridge.availabilityHint())
                RuntimeLogBus.updateState("running-dry")
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

            RuntimeLogBus.emit("xray runtime started via gomobile, version=${bridge.version()}")
            RuntimeLogBus.updateState("running")
            scheduleBackgroundGeoDataRefresh()
        } catch (t: Throwable) {
            handleStartFailure("unexpected xray runtime error: ${t.message}")
        }
    }

    private fun ensureGeoDataReady() {
        val updater = GeoDataUpdater(this)
        val installed = updater.installBundledIfMissing()
        if (installed.isNotEmpty()) {
            RuntimeLogBus.emit("bootstrap geodata installed: ${installed.joinToString()}")
        }

        val missing = GeoDataUpdater.missingFiles(this)
        if (missing.isNotEmpty()) {
            RuntimeLogBus.emit("geodata still missing: ${missing.joinToString()}. runtime will start with bootstrap rules, background refresh later")
        }
    }

    private fun scheduleBackgroundGeoDataRefresh() {
        runtimeExecutor.execute {
            if (RuntimeLogBus.state.value != "running") {
                return@execute
            }
            if (!GeoDataUpdater.needsRefresh(this)) {
                RuntimeLogBus.emit("geodata refresh skipped: still fresh")
                return@execute
            }
            runCatching {
                RuntimeLogBus.emit("starting background geodata refresh through local proxy")
                GeoDataUpdater(this).update(proxyPort = GeoDataUpdater.defaultProxyPort())
            }.onFailure {
                RuntimeLogBus.emit("background geodata refresh failed: ${it.message}")
            }
        }
    }

    private fun stopXrayRuntime() {
        val bridge = gomobileBridge ?: return
        runCatching {
            val stopError = bridge.stop()
            if (stopError.isNotEmpty()) {
                RuntimeLogBus.emit("xray runtime stop failed: $stopError")
            } else {
                RuntimeLogBus.emit("xray runtime stopped")
            }
        }.onFailure {
            RuntimeLogBus.emit("unexpected xray runtime stop error: ${it.message}")
        }
        gomobileBridge = null
    }

    private fun handleStartFailure(message: String) {
        stopXrayRuntime()
        closeTunnel()
        RuntimeLogBus.updateState("error")
        RuntimeLogBus.emit(message)
        leaveForeground()
        stopSelf()
    }

    private fun closeTunnel() {
        val tunnel = tunInterface ?: return
        val fd = tunnel.fd
        tunnel.close()
        tunInterface = null
        RuntimeLogBus.emit("vpn tunnel closed fd=$fd")
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
        runCatching {
            builder.addDisallowedApplication(packageName)
            RuntimeLogBus.emit("excluded app package from VPN: $packageName")
        }.getOrElse {
            throw IllegalStateException("failed to exclude app package from VPN", it)
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
        channel.description = getString(R.string.notification_channel_description)
        channel.setShowBadge(false)
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
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, XrayVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_SERVICE_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text_running, profileName))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_disconnect),
                stopPendingIntent,
            )
            .build()
    }
}
