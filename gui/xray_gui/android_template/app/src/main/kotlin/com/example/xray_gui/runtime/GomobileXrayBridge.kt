package com.example.xray_gui.runtime

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
                val runtimeInstance = bindingClass
                    .getMethod("newRuntime")
                    .invoke(null)
                    ?: return null

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
                    // try next candidate
                }
            }
            return null
        }
    }

    fun version(): String {
        return runCatching {
            bindingClass.getMethod("version").invoke(null)?.toString().orEmpty()
        }.getOrDefault("")
    }

    fun validateJSON(configJson: String): String {
        return invokeString("validateJSON", configJson)
    }

    fun validateAndroid(configJson: String, filesDir: String): String {
        return invokeString("validateAndroid", configJson, filesDir)
    }

    fun startAndroid(configJson: String, filesDir: String, tunFd: Int): String {
        return invokeString("startAndroid", configJson, filesDir, tunFd)
    }

    fun stop(): String {
        return invokeString("stop")
    }

    fun isRunning(): Boolean {
        return runtimeInstance.javaClass.getMethod("isRunning").invoke(runtimeInstance) as? Boolean ?: false
    }

    fun lastError(): String {
        return invokeString("lastError")
    }

    private fun invokeString(methodName: String, vararg args: Any): String {
        val methods = runtimeInstance.javaClass.methods.filter { method ->
            method.name == methodName && method.parameterTypes.size == args.size
        }

        val method = methods.firstOrNull()
            ?: error("Method not found: $methodName with ${args.size} args")

        val convertedArgs = args.mapIndexed { index, arg ->
            convertArg(arg, method.parameterTypes[index])
        }.toTypedArray()

        return method.invoke(runtimeInstance, *convertedArgs)?.toString().orEmpty()
    }

    private fun convertArg(arg: Any, parameterType: Class<*>): Any {
        return when {
            parameterType == java.lang.Long.TYPE || parameterType == java.lang.Long::class.java -> {
                when (arg) {
                    is Int -> arg.toLong()
                    is Long -> arg
                    else -> arg
                }
            }
            parameterType == java.lang.Integer.TYPE || parameterType == java.lang.Integer::class.java -> {
                when (arg) {
                    is Int -> arg
                    is Long -> arg.toInt()
                    else -> arg
                }
            }
            parameterType == java.lang.Boolean.TYPE || parameterType == java.lang.Boolean::class.java -> {
                when (arg) {
                    is Boolean -> arg
                    else -> arg
                }
            }
            else -> arg
        }
    }
}
