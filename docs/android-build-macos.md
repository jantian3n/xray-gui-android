# Build Android APK On macOS

This is the intended build path for this repository on macOS once Flutter, Go, and Android tooling are installed.

## Required Tools

- Flutter SDK
- Android Studio
- Android SDK
- JDK 17
- Go
- `gomobile`
- `gobind`

Recommended checks:

```bash
flutter --version
java -version
go version
gomobile version
adb version
```

## 1. Prepare Flutter Dependencies

```bash
cd gui/xray_gui
flutter pub get
```

## 2. Build The Go Android Library

From the repository root:

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
bash ./gui/xray_gui/scripts/build_android_aar.sh
```

This should produce:

```text
build/xraymobile.aar
```

The Go binding package is:

```text
mobile/xraymobile
```

## 3. Import The AAR Into The Android App

Create a libs directory if needed:

```bash
mkdir -p gui/xray_gui/android/app/libs
cp build/xraymobile.aar gui/xray_gui/android/app/libs/
```

Or use the helper script:

```bash
bash ./gui/xray_gui/scripts/install_android_aar.sh
```

Then add the dependency in `android/app/build.gradle.kts` or `android/app/build.gradle`.

For Kotlin DSL:

```kotlin
dependencies {
    implementation(files("libs/xraymobile.aar"))
}
```

For Groovy DSL:

```groovy
dependencies {
    implementation files("libs/xraymobile.aar")
}
```

The Android runtime bridge uses reflection to detect the generated gomobile classes, so after the AAR is added you usually do not need to change Kotlin imports manually.

## 4. Build A Debug APK

If you use a local HTTP/SOCKS proxy in your shell environment, prefer the helper script because it clears proxy variables before invoking Flutter and Gradle:

```bash
bash ./gui/xray_gui/scripts/build_android_apk.sh --debug
```

Direct Flutter invocation also works when your network environment is clean:

```bash
cd gui/xray_gui
flutter build apk --debug
```

Expected output location:

```text
build/app/outputs/flutter-apk/app-debug.apk
```

## 5. Build A Release APK

For the default arm64-only release build:

```bash
bash ./gui/xray_gui/scripts/build_android_apk.sh --release
```

Expected output location:

```text
build/app/outputs/flutter-apk/app-release.apk
```

If you want multiple ABI-specific release APKs instead:

```bash
bash ./gui/xray_gui/scripts/build_android_apk.sh --release --split-per-abi
```

## 6. Install On A Device

```bash
adb install -r gui/xray_gui/build/app/outputs/flutter-apk/app-debug.apk
```

## 7. Alternative Gradle Build

After the Android host exists, you can also build directly with Gradle:

```bash
cd gui/xray_gui/android
./gradlew assembleDebug
```

Android documentation says debug APKs can also be built with `assembleDebug`, and installed with `installDebug`.

## Current Status

In the current repository snapshot:

- the Flutter shell is present;
- the Android host is checked in;
- the Go mobile runtime wrapper is present;
- the Kotlin runtime already attempts to load and call the generated `xraymobile.aar` API through reflection.
- full-tunnel VPN startup and geodata bootstrap are implemented.
