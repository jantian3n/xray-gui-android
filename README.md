# Xray GUI Android（纯 Kotlin 版）

本仓库当前聚焦于纯 Kotlin 的 Android 客户端，主要能力包括：

- Jetpack Compose 界面
- 基于 Android `VpnService` 的运行控制
- VLESS / XHTTP 节点导入与编辑
- 内置分流策略与日志查看

## 仓库结构

```text
android-kotlin-client/
apk/
docs/
```

## 构建 APK

```bash
cd android-kotlin-client
./gradlew :app:assembleDebug
```

Debug APK 产物路径：

```text
android-kotlin-client/app/build/outputs/apk/debug/app-debug.apk
```

## 文档

- 纯 Kotlin 迁移说明：[docs/pure-kotlin-migration-phase1.md](docs/pure-kotlin-migration-phase1.md)

## 测试 APK

仓库当前提供的 Debug 包：

```text
apk/xray-kotlin-client-debug-20260326.apk
```
