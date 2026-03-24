# xraymobile

`xraymobile` is a small Go binding package for Android integration through `gomobile bind`.

## Purpose

This package gives the Android host a minimal runtime API:

- validate Xray JSON;
- start Xray from JSON;
- start Xray on Android with a TUN fd;
- stop Xray;
- expose runtime state and last error;
- configure geodata lookup path through `xray.location.asset`.

## Package Path

```text
./mobile/xraymobile
```

## Current API

- `NewRuntime()`
- `Version()`
- `(*Runtime).Version()`
- `(*Runtime).ValidateJSON(...)`
- `(*Runtime).StartJSON(...)`
- `(*Runtime).StartAndroid(...)`
- `(*Runtime).StartAndroidWithAssetDir(...)`
- `(*Runtime).Stop()`
- `(*Runtime).IsRunning()`
- `(*Runtime).LastError()`

Methods return an empty string on success, or an error message on failure. This keeps the API simple for gomobile-generated bindings.

## Build On macOS

From the repository root:

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
gomobile bind -target=android -androidapi 24 -o build/xraymobile.aar ./mobile/xraymobile
```

You can also use the helper script:

```bash
bash ./gui/xray_gui/scripts/build_android_aar.sh
```

## Android Integration Direction

The intended Android flow is:

1. Kotlin obtains a TUN fd from `VpnService`.
2. Kotlin passes the generated Xray JSON and TUN fd into `xraymobile.Runtime`.
3. `xraymobile` sets:
   - `xray.tun.fd`
   - `xray.location.asset`
4. `xray-core` starts from JSON and reads geodata from app-private storage.

## Notes

- This package imports `github.com/xtls/xray-core/main/distro/all` so JSON loaders and optional features are registered before startup.
- It assumes geodata files are stored in:

```text
<filesDir>/xray/geodata
```
