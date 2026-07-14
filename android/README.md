# Lover Android MVP

Native Android client built with Kotlin, Jetpack Compose and Material 3.

## Build

Requirements: JDK 21 and Android SDK 35.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Development behavior

- Start `/workspace/backend` with PostgreSQL, then enter a valid mainland China phone number and request an SMS code. The default development code is `123456`.
- The emulator API base URL is `http://10.0.2.2:4000/`. For a physical device, change `API_BASE_URL` in `app/build.gradle.kts` to the machine's reachable address.
- For local media uploads, configure the backend `PUBLIC_BASE_URL` to the same address reachable from Android (for an emulator, `http://10.0.2.2:4000`), because the server returns an absolute `uploadUrl`.
- Storage grants support both `local` and `qiniu`. Local upload sends only the short-lived storage bearer token. Qiniu upload uses a separate unauthenticated OkHttp client and posts `token`, `key`, server-provided `uploadFields`, and `file`; Lover access/refresh tokens are never sent to the storage host.
- Access/refresh tokens, current user and the latest server cache are persisted with DataStore. An OkHttp authenticator rotates refresh tokens once for concurrent 401 responses.
- Image selection uses the Android system Photo Picker. The client reads the selected URI, requests an asset token, uploads multipart bytes, completes the asset, creates the media record and obtains a signed display URL.
- Video creation is intentionally disabled in this MVP because the backend requires a separate ready thumbnail asset.
- The backend contract is centralized in `core/network/ApiService.kt`.

The client aligns with `backend/src/modules/*.ts` and `backend/src/openapi.ts`. Locked capsule content is never inferred locally: the UI uses the server's `isUnlocked` field and handles absent protected content.
