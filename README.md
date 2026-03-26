# Xray GUI Android (Pure Kotlin)

This repository now focuses on the pure Kotlin Android client:

- Jetpack Compose UI
- Android `VpnService` runtime control
- VLESS/XHTTP import and editing
- built-in routing presets and logs

## Repository Layout

```text
android-kotlin-client/
apk/
docs/
```

## Build APK

```bash
cd android-kotlin-client
./gradlew :app:assembleDebug
```

Debug APK output:

```text
android-kotlin-client/app/build/outputs/apk/debug/app-debug.apk
```

## Docs

- Pure Kotlin migration notes: [docs/pure-kotlin-migration-phase1.md](docs/pure-kotlin-migration-phase1.md)

## Test APK

Current debug package in repo:

```text
apk/xray-kotlin-client-debug-20260326.apk
```
