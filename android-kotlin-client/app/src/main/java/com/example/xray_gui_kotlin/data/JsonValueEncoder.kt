package com.example.xray_gui_kotlin.data

import org.json.JSONArray
import org.json.JSONObject

object JsonValueEncoder {
    fun encode(value: Any?): String {
        val jsonValue = toJsonValue(value)
        return when (jsonValue) {
            is JSONObject -> jsonValue.toString(2)
            is JSONArray -> jsonValue.toString(2)
            JSONObject.NULL, null -> "null"
            else -> JSONObject.wrap(jsonValue)?.toString() ?: "\"$jsonValue\""
        }
    }

    private fun toJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, nested) ->
                    if (key is String) {
                        put(key, toJsonValue(nested))
                    }
                }
            }
            is List<*> -> JSONArray().apply {
                value.forEach { put(toJsonValue(it)) }
            }
            is Boolean, is Number, is String -> value
            else -> value.toString()
        }
    }
}
