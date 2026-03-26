package com.example.xray_gui_kotlin.runtime

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object RuntimeLogBus {
    private const val TAG = "XrayKotlin"

    private val mutableLogs = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 128)
    private val mutableState = MutableStateFlow("idle")

    val logs: SharedFlow<String> = mutableLogs.asSharedFlow()
    val state: StateFlow<String> = mutableState.asStateFlow()

    fun updateState(newState: String) {
        mutableState.value = newState
        emit("state=$newState")
    }

    fun emit(message: String) {
        Log.d(TAG, message)
        mutableLogs.tryEmit(message)
    }
}
