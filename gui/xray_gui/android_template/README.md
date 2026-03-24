# Android Template

This folder contains Android native source files for the Flutter app, but it is intentionally not placed under `android/` yet.

## Why

The current environment does not have `flutter` installed, so the official Flutter Android host project could not be generated here.

Keeping these files in `android_template/` avoids blocking the later command:

```bash
flutter create --platforms android .
```

## How To Use

1. Generate the Android host:

```bash
cd gui/xray_gui
flutter create --platforms android .
```

2. Copy or merge:

```text
android_template/app/src/main/... -> android/app/src/main/...
```

3. If your generated package is not `com.example.xray_gui`, rename the Kotlin package and manifest references.

## What This Template Includes

- `MethodChannel` and `EventChannel` wiring;
- VPN permission request flow;
- foreground `VpnService` skeleton;
- dry-run TUN establish path;
- runtime config persistence;
- geodata updater using `Loyalsoldier/v2ray-rules-dat`.
- a clean handoff point for the gomobile package at `mobile/xraymobile`.
- reflective gomobile bridge detection so Kotlin imports usually do not need manual edits after adding the AAR.

## What Is Still Missing

- an actually built `xraymobile.aar` added to the Android host;
- real packet pumping between Xray and the TUN fd;
- production route strategy for full-device VPN mode.

## Next Integration Step

Build the Go binding:

```bash
bash ./gui/xray_gui/scripts/build_android_aar.sh
```

Then import the resulting `build/xraymobile.aar` into the generated Flutter Android host and call it from `XrayRuntimeController`.
