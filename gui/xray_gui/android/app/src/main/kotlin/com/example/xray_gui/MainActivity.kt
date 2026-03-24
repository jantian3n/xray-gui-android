package com.example.xray_gui

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.xray_gui.runtime.RuntimeMethodHandler
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterFragmentActivity() {
    private lateinit var runtimeMethodHandler: RuntimeMethodHandler

    private val vpnPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            runtimeMethodHandler.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
        }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        runtimeMethodHandler = RuntimeMethodHandler(
            activity = this,
            vpnPermissionLauncher = vpnPermissionLauncher,
        )
        runtimeMethodHandler.attach(flutterEngine.dartExecutor.binaryMessenger)
    }
}
