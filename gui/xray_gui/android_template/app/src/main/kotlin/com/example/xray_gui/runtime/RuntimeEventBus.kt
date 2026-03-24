package com.example.xray_gui.runtime

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.EventChannel

object RuntimeEventBus {
    private const val TAG = "XrayGui"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var eventSink: EventChannel.EventSink? = null

    @Volatile
    private var state: String = "idle"

    fun attachSink(sink: EventChannel.EventSink?) {
        eventSink = sink
    }

    fun clearSink() {
        eventSink = null
    }

    fun updateState(newState: String) {
        state = newState
        emit("state=$newState")
    }

    fun currentState(): String = state

    fun emit(message: String) {
        Log.d(TAG, message)
        val sink = eventSink ?: return
        mainHandler.post {
            sink.success(message)
        }
    }
}
