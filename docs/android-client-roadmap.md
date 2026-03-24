# Android-First GUI Roadmap

This document defines the first implementation target for a GUI client built on top of `xray-core`, with Android as the priority platform and Flutter as the shared UI layer for later desktop reuse.

## Goal

Ship an Android client that can:

- import a single `vless://` node;
- support `xhttp` and `reality`;
- enable a VPN-based runtime through Android `VpnService`;
- provide built-in China / non-China routing presets;
- update `geoip.dat` and `geosite.dat`;
- leave a clean path for future Windows and macOS support.

## Architecture

The Android-first app is split into three layers:

1. Flutter UI layer
   - node import;
   - profile editing;
   - routing and DNS preset selection;
   - logs and runtime state;
   - future desktop reuse.
2. Android runtime layer
   - `VpnService`;
   - foreground service lifecycle;
   - file storage for config and geodata;
   - method channel bridge to Flutter.
3. Xray runtime layer
   - Xray config generation;
   - start and stop runtime;
   - TUN fd injection on Android;
   - geodata refresh and validation.

## Suggested Repo Layout

```text
docs/
  android-client-roadmap.md
gui/
  xray_gui/
    README.md
    pubspec.yaml
    lib/
      main.dart
      src/
        app.dart
        core/
          models/
          services/
        features/
          home/
```

## Android Runtime Path

Expected runtime chain:

1. Flutter imports a `vless://` link.
2. Dart parses the link into a typed profile.
3. Dart compiles the profile into Xray JSON.
4. Flutter calls Android through a method channel.
5. Android requests VPN permission if needed.
6. Android obtains the TUN fd from `VpnService`.
7. Android passes the fd and config to the embedded Xray runtime.
8. Runtime starts, logs are streamed back to Flutter.

## MVP Scope

### Must Have

- `vless://` import for one node at a time;
- `reality` outbound support;
- `xhttp` outbound support;
- `cnDirect`, `globalProxy`, `gfwLike` routing presets;
- geodata updater;
- runtime start, stop, and log output;
- profile persistence.

### Deliberately Deferred

- subscription parsing;
- multi-core support;
- fully visualized advanced Xray options;
- traffic graphs and observatory dashboards;
- desktop-specific system proxy management;
- per-app Android routing UI.

## Routing Strategy

Use built-in presets first, then allow advanced overrides later.

### `cnDirect`

- ads blocked;
- private and China traffic direct;
- non-China traffic proxied;
- final TCP/UDP fallback goes to proxy.

### `globalProxy`

- ads blocked;
- private traffic direct;
- everything else proxied.

### `gfwLike`

- ads blocked;
- `geosite:gfw` and Telegram-related IPs proxied;
- the rest direct.

## Geodata Strategy

Preferred default source order:

1. official `v2fly/domain-list-community` and `v2fly/geoip`;
2. optional enhanced source `Loyalsoldier/v2ray-rules-dat`.

Updater requirements:

- download to temp files first;
- verify checksum when available;
- swap atomically on success;
- keep last known good files;
- expose last update time in UI.

## Method Channel Contract

The Flutter side should target a stable interface like this:

- `requestVpnPermission`
- `start`
- `stop`
- `runtimeState`
- `updateGeoData`
- `listProfiles`
- `saveProfile`
- `deleteProfile`

Log streaming should use an event channel:

- channel: `xray_gui/runtime_logs`
- payload: line-based text or structured log events later

## Native Android Tasks

The next native milestones should be implemented in this order:

1. create Android host app and Flutter embedding;
2. add `VpnService` and foreground notification flow;
3. add method channel handlers;
4. integrate gomobile or `libXray`-style binding;
5. wire config file storage and geodata directories;
6. stream runtime logs back to Flutter.

## Recommended First Milestone

The first milestone should prove one complete path:

1. paste a `vless://` link in Flutter;
2. compile valid Xray JSON;
3. request VPN permission;
4. start Android runtime;
5. connect with `xhttp + reality`;
6. show logs in the app.

## Risks

- Android runtime integration depends on Go and `gomobile`, which are not currently available in this environment.
- `xray-core` configuration surface is much larger than any first-pass form UI, so advanced JSON escape hatches are necessary.
- geodata updates need rollback behavior, otherwise a broken update can make routing fail.
- VPN lifecycle bugs are the highest-risk Android area and should be tested before polishing UI.

## Immediate Next Steps

1. keep the Flutter project focused on typed models, parsing, and config generation;
2. define the Android method channel API before writing native runtime glue;
3. implement one good `vless:// reality + xhttp` happy path end to end;
4. add routing presets and geodata updates only after runtime startup is stable.
