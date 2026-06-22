# FeltnerAI-Native

A native-first Kotlin Multiplatform client for [FeltnerAI](../FeltnerAI), the
self-hosted OpenAI-compatible chat server.

The project is organized as a shared Kotlin core plus platform-owned UI:

- `shared/` contains API access, auth/session flow, chat state, SSE streaming,
  saved server profiles, model selection, settings, and admin API wrappers.
- `androidApp/` contains the Android app and Android-only Jetpack Compose UI.
- `appleApp/` contains SwiftUI targets for iOS and macOS.
- Windows and Linux UI targets are future work.

The client still uses FeltnerAI's versioned `/api/v1` contract and portal bearer
sessions (`POST /auth/login` with `portal: true`). That is a backend protocol
detail and intentionally did not change with the app rename.

## Features

- Multiple saved server profiles validated through `/server`
- Login with persisted bearer token and valid-session resume
- List, create, rename, and delete chats
- Live SSE token streaming with stop and regenerate
- Markdown rendering of assistant replies
- Copy message, model picker, and per-chat model persistence
- Theme preference and password change
- Admin screens for users, providers, models, LM Studio, server settings, and branding

ZIP backup import/export and logo/favicon uploads still require native file
dialog work and remain available through the web admin.

## Architecture

```text
shared/
  src/commonMain/kotlin/ai/feltner/nativeapp/
    api/                  # Serializable DTOs, Ktor REST client, SSE parsing
    data/                 # Profile persistence abstraction + Settings store
    FeltnerNativeController.kt
    Platform.kt
  src/androidMain/        # Android actuals, OkHttp engine
  src/appleMain/          # iOS/macOS actuals, Apple Swift bridge, Darwin engine
  src/jvmMain/            # JVM actuals, OkHttp engine for tests/future desktop

androidApp/
  src/androidMain/        # MainActivity, manifest, Android Compose screens/theme

appleApp/
  FeltnerAINative.xcodeproj
  FeltnerAINative/        # SwiftUI app shared by iOS and macOS targets
```

`shared` does not depend on Compose. Platform UI layers render
`NativeAppState` from `FeltnerNativeController` and call controller actions.

## Prerequisites

- JDK 17 or 21. JDK 26 currently fails Kotlin/Gradle configuration.
- Android SDK for Android builds.
- macOS with Xcode for Apple framework and app builds.

## Build & Run

List available tasks:

```bash
just
```

Run shared Kotlin tests:

```bash
just test-shared
```

Build Android:

```bash
just build-android-debug
just build-android
```

Build iOS/macOS apps:

```bash
just build-ios
just build-macos
```

Build Apple release frameworks:

```bash
just build-apple-frameworks
```

Run supported native targets:

```bash
just dev-android
just dev-ios
just dev-macos
```

Open `appleApp/FeltnerAINative.xcodeproj` in Xcode for iOS or macOS app
development. The "Compile Kotlin Framework" build phase invokes
`./gradlew :shared:embedAndSignAppleFrameworkForXcode`.

## Connecting

1. Launch the app and enter your FeltnerAI server URL.
2. Complete first-run server setup in a browser if the server has not been set up.
3. Sign in with a server account, pick a model, and start chatting.

For plaintext `http://` development servers on Android, cleartext traffic is
enabled in the debug app manifest.

## Security Note

Bearer tokens are currently stored through `multiplatform-settings`
(NSUserDefaults on Apple platforms, SharedPreferences on Android, JVM
Preferences). Production builds should move tokens to platform secure storage
such as Keychain, Android Keystore or EncryptedSharedPreferences.
