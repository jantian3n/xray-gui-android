package com.example.xray_gui_kotlin.runtime

class GomobileXrayBridge private constructor(
    private val bindingClass: Class<*>,
    private val runtimeInstance: Any,
) {
    companion object {
        private val candidateBindingClassNames = listOf(
            "xraymobile.Xraymobile",
            "go.xraymobile.Xraymobile",
        )

        fun createOrNull(): GomobileXrayBridge? {
            return runCatching {
                val bindingClass = loadBindingClass() ?: return null
                val runtimeInstance = bindingClass.getMethod("newRuntime").invoke(null) ?: return null
                GomobileXrayBridge(bindingClass, runtimeInstance)
            }.getOrNull()
        }

        fun availabilityHint(): String {
            return "Expected gomobile binding class one of: ${candidateBindingClassNames.joinToString()}"
        }

        private fun loadBindingClass(): Class<*>? {
            for (className in candidateBindingClassNames) {
                try {
                    return Class.forName(className)
                } catch (_: ClassNotFoundException) {
                    // continue
                }
            }
            return null
        }
    }

    fun version(): String = runCatching {
        bindingClass.getMethod("version").invoke(null)?.toString().orEmpty()
    }.getOrDefault("")

    fun validateAndroid(configJson: String, filesDir: String): String {
        return invokeString("validateAndroid", configJson, filesDir)
    }

    fun startAndroid(configJson: String, filesDir: String, tunFd: Int): String {
        return invokeString("startAndroid", configJson, filesDir, tunFd)
    }

    fun stop(): String = invokeString("stop")

    private fun invokeString(methodName: String, vararg args: Any): String {
        val method = runtimeInstance.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.size == args.size }
            ?: error("Method not found: $methodName")

        val converted = args.mapIndexed { index, value -> convertArg(value, method.parameterTypes[index]) }.toTypedArray()
        return method.invoke(runtimeInstance, *converted)?.toString().orEmpty()
    }

    private fun convertArg(value: Any, parameterType: Class<*>): Any {
        return when {
            parameterType == java.lang.Long.TYPE || parameterType == java.lang.Long::class.java -> {
                if (value is Int) value.toLong() else value
            }
            parameterType == java.lang.Integer.TYPE || parameterType == java.lang.Integer::class.java -> {
                if (value is Long) value.toInt() else value
            }
            else -> value
        }
    }
}
