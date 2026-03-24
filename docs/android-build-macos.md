# Build Android APK On macOS

This is the intended build path for the Android client on macOS once Flutter, Go, and Android tooling are installed.

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

## 1. Generate The Flutter Android Host

```bash
cd /Users/yan/Desktop/xray/Xray-core/gui/xray_gui
flutter create --platforms android .
flutter pub get
```

## 2. Merge The Native Android Template

Copy:

```text
gui/xray_gui/android_template/app/src/main/...
-> gui/xray_gui/android/app/src/main/...
```

Or use the helper script:

```bash
bash ./gui/xray_gui/scripts/merge_android_template.sh
```

If your generated package name is different from `com.example.xray_gui`, rename:

- Kotlin package declarations
- manifest references
- `run-as` package names used during debugging

## 3. Build The Go Android Library

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

## 4. Import The AAR Into The Flutter Android Host

Create a libs directory if needed:

```bash
mkdir -p /Users/yan/Desktop/xray/Xray-core/gui/xray_gui/android/app/libs
cp /Users/yan/Desktop/xray/Xray-core/build/xraymobile.aar /Users/yan/Desktop/xray/Xray-core/gui/xray_gui/android/app/libs/
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

The current Android template uses reflection to detect the generated gomobile classes, so after the AAR is added you usually do not need to change Kotlin imports manually.

## 5. Build A Debug APK

If you use a local HTTP/SOCKS proxy in your shell environment, prefer the helper script because it clears proxy variables before invoking Flutter and Gradle:

```bash
bash ./gui/xray_gui/scripts/build_android_apk.sh --debug
```

Direct Flutter invocation also works when your network environment is clean:

```bash
cd /Users/yan/Desktop/xray/Xray-core/gui/xray_gui
flutter build apk --debug
```

Expected output location:

```text
build/app/outputs/flutter-apk/app-debug.apk
```

## 6. Build A Release APK

Once signing is configured:

```bash
bash ./gui/xray_gui/scripts/build_android_apk.sh --release --split-per-abi
```

Expected output location:

```text
build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk
build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
build/app/outputs/flutter-apk/app-x86_64-release.apk
```

## 7. Install On A Device

```bash
adb install -r /Users/yan/Desktop/xray/Xray-core/gui/xray_gui/build/app/outputs/flutter-apk/app-debug.apk
```

## 8. Alternative Gradle Build

After the Android host exists, you can also build directly with Gradle:

```bash
cd /Users/yan/Desktop/xray/Xray-core/gui/xray_gui/android
./gradlew assembleDebug
```

Android documentation says debug APKs can also be built with `assembleDebug`, and installed with `installDebug`.

## Current Limitation

In the current repository snapshot:

- the Flutter shell is present;
- the Android native template is present;
- the Go mobile runtime wrapper is present;
- the Kotlin template already attempts to load and call the generated `xraymobile.aar` API through reflection.

What is still missing is end-to-end traffic testing with a real `xraymobile.aar` build wired into the Android runtime.
