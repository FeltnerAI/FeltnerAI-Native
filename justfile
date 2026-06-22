set windows-shell := ["powershell.exe", "-NoLogo", "-Command"]

gradlew := if os_family() == "windows" { "./gradlew.bat" } else { "./gradlew" }

default:
    @just --list

# --- Dev: run the app on a target -----------------------------------------

[group('dev')]
desktop:
    {{gradlew}} :composeApp:run

# Installs and launches the debug build on a connected device/emulator.
[group('dev')]
android:
    {{gradlew}} :composeApp:installDebug

# Builds, installs, and launches the app on an iPhone Simulator.
[group('dev')]
dev-ios:
    ./scripts/dev-ios-simulator.sh

# --- Build: produce real artifacts ----------------------------------------

# Native desktop installer (.msi / .dmg / .deb) for the current OS.
[group('build')]
build-desktop:
    {{gradlew}} :composeApp:packageDistributionForCurrentOS

[group('build')]
build-android:
    {{gradlew}} :composeApp:assembleRelease

# Builds the Kotlin framework for iOS (device). Linking the .app and signing
# happen in Xcode (iosApp/) on macOS.
[group('build')]
build-ios:
    {{gradlew}} :composeApp:linkReleaseFrameworkIosArm64

[group('build')]
clean:
    {{gradlew}} clean
