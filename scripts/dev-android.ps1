$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$PackageId = if ($env:ANDROID_PACKAGE_ID) { $env:ANDROID_PACKAGE_ID } else { "ai.feltner.nativeapp" }

function Find-AndroidSdk {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk")
    )

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }

        $adb = Join-Path $candidate "platform-tools\adb.exe"
        if (Test-Path $adb) {
            return $candidate
        }
    }

    throw "Could not find Android SDK. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

function Get-DeviceCount {
    param([string]$Adb)

    $devices = & $Adb devices
    return @($devices | Where-Object { $_ -match "\tdevice$" }).Count
}

function Wait-For-Boot {
    param([string]$Adb)

    & $Adb wait-for-device | Out-Null
    for ($i = 0; $i -lt 90; $i++) {
        $booted = (& $Adb shell getprop sys.boot_completed 2>$null).Trim()
        if ($booted -eq "1") {
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for Android device to boot."
}

$AndroidSdk = Find-AndroidSdk
$Adb = Join-Path $AndroidSdk "platform-tools\adb.exe"
$Emulator = Join-Path $AndroidSdk "emulator\emulator.exe"

if ((Get-DeviceCount $Adb) -eq 0) {
    if (-not (Test-Path $Emulator)) {
        throw "No connected Android device and emulator tool was not found."
    }

    $AvdName = if ($env:ANDROID_AVD) { $env:ANDROID_AVD } else { (& $Emulator -list-avds | Select-Object -First 1) }
    if ([string]::IsNullOrWhiteSpace($AvdName)) {
        throw "No connected Android device and no Android Virtual Devices exist."
    }

    Write-Host "Starting Android emulator: $AvdName"
    Start-Process -FilePath $Emulator -ArgumentList @("-avd", $AvdName)
    Wait-For-Boot $Adb
}

& (Join-Path $PSScriptRoot "gradle-with-jdk.ps1") ":androidApp:installDebug"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& $Adb shell monkey -p $PackageId -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Launched $PackageId"
