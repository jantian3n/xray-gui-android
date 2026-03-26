package com.example.xray_gui_kotlin.model

enum class RoutingPreset(
    val label: String,
    val description: String,
) {
    CN_DIRECT(
        label = "国内直连",
        description = "中国大陆和私有地址直连，海外流量走代理。",
    ),
    GLOBAL_PROXY(
        label = "全局代理",
        description = "除私有地址外，所有流量都走代理。",
    ),
    GFW_LIKE(
        label = "常见被墙域名代理",
        description = "常见受限域名走代理，其余多数流量保持直连。",
    ),
}
