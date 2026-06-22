set windows-shell := ["powershell.exe", "-NoLogo", "-Command"]

gradle := "./scripts/gradle-with-jdk.sh"

default:
    @just --list

# --- Dev: run the app on a target -----------------------------------------

[group('dev')]
dev-android:
    ./scripts/dev-android.sh

# Builds, installs, and launches the app on an iPhone Simulator.
[group('dev')]
dev-ios:
    ./scripts/dev-ios-simulator.sh

# Builds and opens the macOS app.
[group('dev')]
dev-macos:
    ./scripts/dev-macos.sh

[group('dev')]
android: dev-android

[group('dev')]
ios: dev-ios

[group('dev')]
macos: dev-macos

# --- Build: produce real artifacts ----------------------------------------

# Run the shared Kotlin test suite.
[group('build')]
test-shared:
    {{gradle}} :shared:allTests

[group('build')]
build-shared:
    {{gradle}} :shared:allTests

[group('build')]
build-android-debug:
    {{gradle}} :androidApp:assembleDebug

[group('build')]
build-android:
    {{gradle}} :androidApp:assembleRelease

[group('build')]
build-ios-frameworks:
    {{gradle}} :shared:linkReleaseFrameworkIosArm64 :shared:linkReleaseFrameworkIosSimulatorArm64

[group('build')]
build-macos-frameworks:
    {{gradle}} :shared:linkReleaseFrameworkMacosArm64 :shared:linkReleaseFrameworkMacosX64

[group('build')]
build-apple-frameworks:
    {{gradle}} :shared:linkReleaseFrameworkIosArm64 :shared:linkReleaseFrameworkIosSimulatorArm64 :shared:linkReleaseFrameworkMacosArm64 :shared:linkReleaseFrameworkMacosX64

[group('build')]
build-ios:
    xcodebuild -project appleApp/FeltnerAINative.xcodeproj -scheme FeltnerAI-Native -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath appleApp/build/DerivedData build

[group('build')]
build-macos:
    xcodebuild -project appleApp/FeltnerAINative.xcodeproj -scheme FeltnerAI-Native-macOS -configuration Debug -destination 'platform=macOS' -derivedDataPath appleApp/build/DerivedData build CODE_SIGNING_ALLOWED=NO

[group('build')]
build-all: build-shared build-android-debug build-ios build-macos

[group('build')]
build-release-all: build-shared build-android build-apple-frameworks

[group('build')]
clean:
    {{gradle}} clean
