package com.example.xray_gui.runtime

import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class RuntimeMethodHandler(
    private val activity: FragmentActivity,
    private val vpnPermissionLauncher: ActivityResultLauncher<Intent>,
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    companion object {
        private const val METHOD_CHANNEL = "xray_gui/runtime"
        private const val LOG_CHANNEL = "xray_gui/runtime_logs"
    }

    private val controller = XrayRuntimeController(activity.applicationContext)
    private var pendingVpnPermissionResult: MethodChannel.Result? = null

    fun attach(binaryMessenger: BinaryMessenger) {
        MethodChannel(binaryMessenger, METHOD_CHANNEL).setMethodCallHandler(this)
        EventChannel(binaryMessenger, LOG_CHANNEL).setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "requestVpnPermission" -> requestVpnPermission(result)
                "start" -> {
                    val args = call.arguments as? Map<*, *>
                        ?: error("Missing start arguments.")
                    controller.start(args)
                    result.success(true)
                }
                "stop" -> {
                    controller.stop()
                    result.success(true)
                }
                "runtimeState" -> result.success(controller.runtimeState())
                "updateGeoData" -> {
                    controller.updateGeoData()
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        } catch (t: Throwable) {
            RuntimeEventBus.emit("method-error=${call.method}: ${t.message}")
            result.error("RUNTIME_ERROR", t.message, null)
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        RuntimeEventBus.attachSink(events)
        RuntimeEventBus.emit("runtime log stream attached")
    }

    override fun onCancel(arguments: Any?) {
        RuntimeEventBus.clearSink()
    }

    fun onVpnPermissionResult(granted: Boolean) {
        RuntimeEventBus.emit("vpn-permission=$granted")
        pendingVpnPermissionResult?.success(granted)
        pendingVpnPermissionResult = null
    }

    private fun requestVpnPermission(result: MethodChannel.Result) {
        val intent = VpnService.prepare(activity)
        if (intent == null) {
            RuntimeEventBus.emit("vpn permission already granted")
            result.success(true)
            return
        }

        if (pendingVpnPermissionResult != null) {
            result.error("BUSY", "VPN permission request already in progress.", null)
            return
        }

        pendingVpnPermissionResult = result
        vpnPermissionLauncher.launch(intent)
    }
}
