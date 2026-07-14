# Lover Android MVP

Native Android client built with Kotlin, Jetpack Compose and Material 3.

## Build

Requirements: JDK 21 and Android SDK 35.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Development behavior

- Enter any phone-like number (at least 6 digits) and a code of at least 4 digits.
- Account and couple data are persisted locally with DataStore so the complete MVP can run without a backend.
- Photo/video selection uses the Android system Photo Picker.
- The backend contract is centralized in `core/network/ApiService.kt`.
- The emulator API base URL defaults to `http://10.0.2.2:8080/` and can be changed in `app/build.gradle.kts`.

The local-first repository is the current data source. `ApiService` is wired through Hilt/Retrofit and ready to replace or synchronize the local operations when the server is available.
