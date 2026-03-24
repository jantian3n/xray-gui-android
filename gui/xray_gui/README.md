# Xray GUI

Flutter application for the Android Xray client in this repository.

## Current Status

The app currently includes:

- `vless://` parsing and field-based node editing
- local node persistence and current-node selection
- Xray JSON compilation for Android VPN mode
- Android runtime bridge over method and event channels
- `VpnService`-driven full-tunnel startup
- bootstrap geodata installation and background refresh
- MD3-style bottom navigation for connection, nodes, routing, and logs

## Directory Layout

```text
lib/
  main.dart
  src/
    app.dart
    core/
      models/
      services/
    features/
      home/

android/
scripts/
test/
```

## Build Flow

1. Fetch Flutter dependencies:

   ```bash
   flutter pub get
   ```

2. Build and install the gomobile AAR from the repository root:

   ```bash
   bash ./gui/xray_gui/scripts/build_android_aar.sh
   bash ./gui/xray_gui/scripts/install_android_aar.sh
   ```

3. Build the Android APK:

   ```bash
   bash ./gui/xray_gui/scripts/build_android_apk.sh --debug
   ```

## Debugging

See [`../../docs/android-debugging.md`](../../docs/android-debugging.md).

## Build On macOS

See [`../../docs/android-build-macos.md`](../../docs/android-build-macos.md).
