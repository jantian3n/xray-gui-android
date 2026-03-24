# Xray GUI Android

Android-first Xray GUI client built with Flutter, Kotlin, `VpnService`, and a gomobile-wrapped Xray runtime.

## Recommended Companion Script

This Android GUI is recommended to be used together with
[`jantian3n/xray-vless-xhttp-multimode-installer`](https://github.com/jantian3n/xray-vless-xhttp-multimode-installer).

The app can import the script outputs for:

- single `vless://` XHTTP + REALITY links
- script-style `client_outbound.json`
- `client_split_patch.json` for split XHTTP modes

Use this command on your server to fetch and run the companion XHTTP deployment script:

```bash
wget -O deploy_vless_xhttp.sh https://raw.githubusercontent.com/jantian3n/xray-vless-xhttp-multimode-installer/main/deploy_vless_xhttp.sh && chmod +x deploy_vless_xhttp.sh && sudo bash deploy_vless_xhttp.sh
```

## What Is In This Repo

- Flutter UI for node import, node list management, routing presets, and logs
- Android host with `VpnService` integration and full-tunnel routing
- bundled bootstrap `geoip.dat` and `geosite.dat` assets for first-run startup
- Go `xraymobile` binding package that embeds `github.com/xtls/xray-core`

## Repository Layout

```text
docs/
gui/xray_gui/
mobile/xraymobile/
```

## Quick Start

1. Install Flutter, Android Studio, Android SDK, JDK 17, Go, `gomobile`, and `gobind`.
2. Build the Android AAR:

   ```bash
   bash ./gui/xray_gui/scripts/build_android_aar.sh
   bash ./gui/xray_gui/scripts/install_android_aar.sh
   ```

3. Build the APK:

   ```bash
   bash ./gui/xray_gui/scripts/build_android_apk.sh --debug
   ```

The default Android build target is `arm64-v8a`.

## Docs

- Build on macOS: [docs/android-build-macos.md](docs/android-build-macos.md)
- Debugging: [docs/android-debugging.md](docs/android-debugging.md)
- Android roadmap: [docs/android-client-roadmap.md](docs/android-client-roadmap.md)

## Notes

- The app currently targets Android first.
- `mobile/xraymobile` depends on the published `github.com/xtls/xray-core` module.
- The generated `xraymobile.aar` is intentionally not committed. Build it locally before packaging the app.
