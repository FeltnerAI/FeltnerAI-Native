set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

gradle := if os_family() == "windows" { "powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File ./scripts/gradle-with-jdk.ps1" } else { "./scripts/gradle-with-jdk.sh" }
android_dev := if os_family() == "windows" { "powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File ./scripts/dev-android.ps1" } else { "./scripts/dev-android.sh" }
host_clean := if os_family() == "windows" { "dotnet clean windowsApp/FeltnerAINative.Windows.csproj -p:Platform=x64" } else { ":" }
windows_project := "windowsApp/FeltnerAINative.Windows.csproj"
windows_debug_exe := ".\\windowsApp\\bin\\x64\\Debug\\net8.0-windows10.0.19041.0\\win-x64\\FeltnerAINative.Windows.exe"

[group('tools')]
default: list

# List available recipes.
[group('tools')]
list:
    @just --list

# Format this justfile.
[group('tools')]
fmt:
    just --fmt

# Check justfile formatting.
[group('tools')]
fmt-check:
    just --fmt --check

# Clean build outputs for this host.
[group('tools')]
clean:
    {{ gradle }} clean
    {{ host_clean }}

# --- Dev: build, install, and run a native target -------------------------

[group('dev')]
dev-android:
    {{ android_dev }}

[group('dev')]
[macos]
dev-ios:
    ./scripts/dev-ios-simulator.sh

[group('dev')]
[macos]
dev-macos:
    ./scripts/dev-macos.sh

[group('dev')]
[windows]
dev-windows: build-windows-debug
    & {{ windows_debug_exe }}

# --- Build: produce artifacts and run validation --------------------------

# Run the shared Kotlin test suite.
[group('build')]
test-shared:
    {{ gradle }} :shared:allTests

[group('build')]
build-android-debug:
    {{ gradle }} :androidApp:assembleDebug

[group('build')]
build-android:
    {{ gradle }} :androidApp:assembleRelease

[group('build')]
[macos]
build-ios-frameworks:
    {{ gradle }} :shared:linkReleaseFrameworkIosArm64 :shared:linkReleaseFrameworkIosSimulatorArm64

[group('build')]
[macos]
build-macos-frameworks:
    {{ gradle }} :shared:linkReleaseFrameworkMacosArm64 :shared:linkReleaseFrameworkMacosX64

[group('build')]
[windows]
build-windows-native-shared:
    {{ gradle }} :shared:linkReleaseSharedMingwX64

[group('build')]
[windows]
build-windows-debug:
    dotnet build {{ windows_project }} --configuration Debug -p:Platform=x64

[group('build')]
[windows]
build-windows:
    dotnet build {{ windows_project }} --configuration Release -p:Platform=x64

[group('build')]
[macos]
build-apple-frameworks:
    {{ gradle }} :shared:linkReleaseFrameworkIosArm64 :shared:linkReleaseFrameworkIosSimulatorArm64 :shared:linkReleaseFrameworkMacosArm64 :shared:linkReleaseFrameworkMacosX64

[group('build')]
[macos]
build-ios:
    xcodebuild -project appleApp/FeltnerAINative.xcodeproj -scheme FeltnerAI-Native -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath appleApp/build/DerivedData build

[group('build')]
[macos]
build-macos:
    xcodebuild -project appleApp/FeltnerAINative.xcodeproj -scheme FeltnerAI-Native-macOS -configuration Debug -destination 'platform=macOS' -derivedDataPath appleApp/build/DerivedData build CODE_SIGNING_ALLOWED=NO

[group('build')]
[windows]
build-all: test-shared build-android-debug build-windows-debug

[group('build')]
[macos]
build-all: test-shared build-android-debug build-ios build-macos

[group('build')]
[windows]
build-release-all: test-shared build-android build-windows

[group('build')]
[macos]
build-release-all: test-shared build-android build-apple-frameworks
