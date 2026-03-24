package com.example.xray_gui.runtime

import org.json.JSONArray
import org.json.JSONObject

object JsonValueEncoder {
    fun encode(value: Any?): String {
        return when (val jsonValue = toJsonValue(value)) {
            is JSONObject -> jsonValue.toString(2)
            is JSONArray -> jsonValue.toString(2)
            JSONObject.NULL -> "null"
            null -> "null"
            else -> JSONObject.wrap(jsonValue)?.toString() ?: "\"$jsonValue\""
        }
    }

    private fun toJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, nestedValue) ->
                    if (key is String) {
                        put(key, toJsonValue(nestedValue))
                    }
                }
            }
            is List<*> -> JSONArray().apply {
                value.forEach { nestedValue ->
                    put(toJsonValue(nestedValue))
                }
            }
            is Boolean, is Number, is String -> value
            else -> value.toString()
        }
    }
}
