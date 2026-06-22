# FeltnerAI Portal (Kotlin Multiplatform)

A native Portal client for [FeltnerAI](../FeltnerAI) — the self-hosted,
OpenAI-compatible chat server — built with **Kotlin Multiplatform** and
**Compose Multiplatform**. One shared codebase compiles natively to **Android**,
**iOS**, and **desktop** (Windows / macOS / Linux).

It speaks FeltnerAI's versioned `/api/v1` contract using **Portal bearer
sessions** (`POST /auth/login` with `portal: true`), the same authentication
path the Tauri desktop Portal uses — not the browser's cookie + CSRF flow.

## Features

Feature parity with the FeltnerAI web portal:

**Chat**
- Multiple saved server profiles, validated via the `/server` handshake
- Portal login with a persisted bearer token (auto-resumes valid sessions)
- List / create / rename / delete chats and browse history
- Live token streaming over SSE with stop + **regenerate**
- **Markdown rendering** of assistant replies (GitHub-flavored)
- **Copy** message, model picker (persisted per-chat)

**Account / Settings**
- Theme preference (light / dark / system) synced via `/auth/preferences`
- Change password (`/auth/password`)

**Administration** (shown only to admin accounts)
- **Users** — create, edit (role/disable/reset password), delete
- **Providers** — create, edit, delete, and test connections
- **Models** — configure, enable/disable, set default, delete
- **LM Studio** — status, start/stop local server, load/unload models
- **Server** — public URL, trusted proxies, start-at-login, LM Studio CLI path
- **Branding** — server name, accent color, custom CSS

Adaptive navigation (permanent sidebar on desktop/tablet, drawer on phones).

> Not yet ported: ZIP backup export/import and logo/favicon **uploads** — these
> need native file dialogs and remain in the web admin. Everything else is here.

## Architecture

```
composeApp/
  src/commonMain/kotlin/ai/feltner/portal/
    api/Models.kt          # @Serializable DTOs mirroring /api/v1
    api/FeltnerClient.kt   # Ktor client: REST + manual SSE parsing
    data/ProfileStore.kt   # multiplatform-settings persistence
    AppViewModel.kt        # StateFlow-driven app state machine
    App.kt                 # root composable + navigation
    ui/                    # Compose Material 3 screens + theme
    Platform.kt            # expect: platformName(), nowIso()
  src/androidMain/         # MainActivity, manifest, Android actuals, OkHttp engine
  src/jvmMain/             # desktop main(), JVM actuals, OkHttp engine
  src/iosMain/             # MainViewController, iOS actuals, Darwin engine
iosApp/                    # SwiftUI host app + Xcode project
```

The HTTP engine is selected per target automatically (OkHttp on Android/JVM,
Darwin on iOS) — `HttpClient { }` in `commonMain` picks up whichever engine the
target provides.

## Prerequisites

- JDK 17+ (JDK 17 or 21 recommended for the current Gradle/Kotlin tooling)
- Android: Android SDK (set via `local.properties` or `ANDROID_HOME`)
- iOS: macOS with Xcode 15+ (the Kotlin framework only builds on macOS)

## Build & run

### Desktop
```bash
./gradlew :composeApp:run
```
Package native installers (`.msi` / `.dmg` / `.deb`):
```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

### Android
Create `local.properties` with `sdk.dir=/path/to/Android/sdk`, then:
```bash
./gradlew :composeApp:installDebug      # to a connected device/emulator
./gradlew :composeApp:assembleDebug     # APK in composeApp/build/outputs/apk
```

### iOS (macOS only)
Run the debug app on an iPhone Simulator:
```bash
just dev-ios
```
To target a specific simulator:
```bash
IOS_SIMULATOR="iPhone 15" just dev-ios
```

Open `iosApp/iosApp.xcodeproj` in Xcode, set your signing **Team**, and Run.
The "Compile Kotlin Framework" build phase invokes
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` automatically.

## Connecting

1. Launch the app and enter your FeltnerAI server URL (e.g. `https://chat.example.com`).
   The server must have completed first-run setup (do that once in a browser).
2. Sign in with an account created by the server administrator.
3. Pick a model and start chatting.

> The server must expose the `portal_sessions` capability (it does by default).
> For plaintext `http://` servers on Android, cleartext traffic is enabled in
> the manifest for development convenience.

## Security note

Bearer tokens are stored via `multiplatform-settings` (NSUserDefaults /
SharedPreferences / JVM Preferences). For production, move them to the platform
secure store (iOS Keychain, Android Keystore/EncryptedSharedPreferences),
mirroring the Rust Portal's `keyring` usage. See `data/ProfileStore.kt`.
