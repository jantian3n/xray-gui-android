# Android Debugging Guide

This guide explains how to run and debug the Android-first client after the current source skeleton is copied into a generated Flutter Android host project.

## Tooling

Install and verify these tools first:

- Flutter SDK
- Android Studio and Android SDK
- JDK 17
- Go
- `gomobile`
- `adb`

Recommended checks:

```bash
flutter --version
dart --version
java -version
go version
gomobile version
adb version
```

## Prepare The Android Host

The current repository contains Android native source files under:

```text
gui/xray_gui/android_template/
```

Generate the actual Flutter Android host first:

```bash
cd /Users/yan/Desktop/xray/Xray-core/gui/xray_gui
flutter create --platforms android .
```

Then copy or merge the template sources into the generated Android host:

```text
android_template/app/src/main/...
-> android/app/src/main/...
```

If your package name is not `com.example.xray_gui`, update the Kotlin package declarations and manifest names accordingly.

## Run The App

Fetch dependencies:

```bash
cd /Users/yan/Desktop/xray/Xray-core/gui/xray_gui
flutter pub get
```

Start an emulator or connect a device:

```bash
flutter devices
```

Run in debug mode:

```bash
flutter run -d <device-id>
```

## What To Test First

Use this order so failures are easier to isolate:

1. app launches;
2. `Import` parses a `vless://` link and shows compiled JSON;
3. `Start` triggers VPN permission;
4. permission is granted and the foreground service starts;
5. log channel receives native log lines;
6. `Update Geodata` downloads `geoip.dat` and `geosite.dat`.

## Where To Put Breakpoints

Open the Android module in Android Studio and add breakpoints in:

- `MainActivity.kt`
- `RuntimeMethodHandler.kt`
- `XrayRuntimeController.kt`
- `XrayVpnService.kt`
- `GeoDataUpdater.kt`

Most useful initial breakpoints:

- method channel entry in `onMethodCall`;
- VPN permission callback;
- foreground service start path;
- geodata checksum verification;
- dry-run TUN establish path.

## Logcat Filters

Filter by these tags:

- `XrayGui`
- `XrayVpnService`
- `GeoDataUpdater`
- `Flutter`

CLI examples:

```bash
adb logcat | rg "XrayGui|XrayVpnService|GeoDataUpdater|Flutter"
```

```bash
adb logcat -s XrayGui XrayVpnService GeoDataUpdater
```

## Inspect App Files

The native controller writes files under app-private storage:

```text
files/xray/config.json
files/xray/profile.json
files/xray/geodata/
```

On a debug build, inspect them with:

```bash
adb shell run-as com.example.xray_gui ls files/xray
adb shell run-as com.example.xray_gui cat files/xray/config.json
adb shell run-as com.example.xray_gui cat files/xray/profile.json
adb shell run-as com.example.xray_gui ls files/xray/geodata
```

## Check VPN State

Confirm the service is alive:

```bash
adb shell dumpsys activity services com.example.xray_gui
```

Check whether Android sees the VPN:

```bash
adb shell dumpsys connectivity | rg -i vpn
```

## Common Failure Cases

### `MissingPluginException`

Cause:

- Flutter side method channel exists, but Android host does not register the native handler.

Check:

- `MainActivity.kt` is using the template code;
- channel names match exactly:
  - `xray_gui/runtime`
  - `xray_gui/runtime_logs`

### VPN Permission Not Showing

Cause:

- `requestVpnPermission` path is not invoked;
- `VpnService.prepare()` result handling is broken.

Check:

- breakpoint in `requestVpnPermission`;
- activity result callback returns to the pending Flutter result.

### Foreground Service Crash On Android 14+

Cause:

- missing foreground service type or permission.

Check:

- service manifest contains `android:foregroundServiceType="systemExempted"`;
- manifest includes `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`.

Android documentation currently states that VPN apps configured through system VPN settings qualify for `systemExempted`.

### Geodata Update Fails

Cause:

- network blocked;
- GitHub inaccessible;
- checksum mismatch;
- app-private storage write failure.

Check:

- logcat lines from `GeoDataUpdater`;
- temp files under `files/xray/geodata`;
- whether `.sha256sum` content matches the downloaded file.

### Node Imports But Start Fails

Cause:

- `vless://` fields missing for `reality` or `xhttp`.

Check:

- config preview in Flutter;
- `REALITY` fields:
  - `publicKey`
  - `serverName` or `sni`
  - `fingerprint`
- `XHTTP` field:
  - `path`

## Recommended Debugging Sequence

1. verify Flutter UI and parser without touching Android runtime;
2. verify method channel calls with stub responses;
3. verify VPN permission flow;
4. verify foreground service start and log streaming;
5. verify geodata updater;
6. only then integrate gomobile or `libXray` runtime.

## After Gomobile Integration

Once the Go binding is added, add these extra checks:

```bash
adb logcat | rg "xray|gomobile|XrayGui"
```

```bash
adb shell run-as com.example.xray_gui ls files/xray/geodata
```

Then confirm:

- TUN fd is passed into the Go runtime;
- Xray starts with the generated `config.json`;
- traffic begins to flow after routes are expanded beyond the current dry-run setup.
