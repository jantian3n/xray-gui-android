# Pure Kotlin Migration - Phase 1

This phase introduces an independent Android client that does not depend on Flutter runtime entrypoints.

## New Module

- `android-kotlin-client/`

This module contains:

- Kotlin + Jetpack Compose UI
- Importer for `vless://`, script-style `client_outbound.json`, and `client_split_patch.json`
- Profile model and DataStore persistence
- Xray JSON compiler
- VPN permission entry + `VpnService`
- Runtime start/stop and log stream
- gomobile reflection bridge for `xraymobile.aar`

## Build

```bash
cd android-kotlin-client
./gradlew :app:assembleDebug
```

## AAR Installation

```bash
bash ./android-kotlin-client/scripts/install_xraymobile_aar.sh
```

If `xraymobile.aar` is not installed, app runtime falls back to dry-run tunnel mode and logs an availability hint.

## Notes

- Flutter project remains untouched and can still be built.
- Go runtime package (`mobile/xraymobile`) is not changed in this phase.
- This is MVP migration groundwork, not full feature parity.
