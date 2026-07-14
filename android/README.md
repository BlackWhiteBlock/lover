# Lover Android MVP

Native Android client built with Kotlin, Jetpack Compose and Material 3.

## Build

Requirements: JDK 21 and Android SDK 36.

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
- Images and videos use the Android system Photo Picker. Selected content is streamed from its URI through an OkHttp `RequestBody`; images are limited to 30 MB and videos to 200 MB without loading the source file into memory.
- Video upload extracts and compresses a representative JPEG frame, uploads and completes both assets, then creates the media record with `thumbnailAssetId`. Video details play the signed URL with Media3 ExoPlayer.
- Cold start waits for DataStore to load persisted credentials, then refreshes `/api/me`, bootstrap and lists. A temporary network failure keeps the cache visible; an invalid refresh session returns to login.
- The backend contract is centralized in `core/network/ApiService.kt`.

The client aligns with `backend/src/modules/*.ts` and `backend/src/openapi.ts`. Locked capsule content is never inferred locally: the UI uses the server's `isUnlocked` field and handles absent protected content.
