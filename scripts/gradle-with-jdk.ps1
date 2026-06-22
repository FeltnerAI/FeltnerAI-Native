$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")

function Get-JavaMajor {
    param([Parameter(Mandatory = $true)][string]$JavaHome)

    $java = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path $java)) {
        return 0
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $versionOutput = & $java -version 2>&1 | ForEach-Object { "$_" }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $versionLine = ($versionOutput | Select-String -Pattern 'version "([^"]+)"' | Select-Object -First 1).Matches.Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($versionLine)) {
        return 0
    }

    if ($versionLine.StartsWith("1.")) {
        return [int](($versionLine.Substring(2)).Split(".")[0])
    }

    return [int](($versionLine.Split(".")[0]))
}

function Use-JavaHome {
    param([string]$JavaHome)

    if ([string]::IsNullOrWhiteSpace($JavaHome)) {
        return $false
    }

    $java = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path $java)) {
        return $false
    }

    $major = Get-JavaMajor $JavaHome
    if ($major -ge 17 -and $major -lt 26) {
        $env:JAVA_HOME = $JavaHome
        $env:PATH = "$JavaHome\bin;$env:PATH"
        return $true
    }

    return $false
}

function Configure-JavaHome {
    if (Use-JavaHome $env:JAVA_HOME) {
        return
    }

    $candidates = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.3\jbr",
        "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3\jbr"
    )

    $adoptium = "C:\Program Files\Eclipse Adoptium"
    if (Test-Path $adoptium) {
        $candidates += Get-ChildItem $adoptium -Directory |
            Where-Object { $_.Name -match "jdk-(17|21)\." } |
            Sort-Object Name -Descending |
            ForEach-Object { $_.FullName }
    }

    $javaHomes = @("C:\Program Files\Java", "C:\Program Files\Microsoft")
    foreach ($javaHomeRoot in $javaHomes) {
        if (Test-Path $javaHomeRoot) {
            $candidates += Get-ChildItem $javaHomeRoot -Directory -Recurse -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match "jdk-(17|21)" -or $_.Name -match "^(17|21)" } |
                ForEach-Object { $_.FullName }
        }
    }

    foreach ($candidate in $candidates) {
        if (Use-JavaHome $candidate) {
            return
        }
    }

    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $current = & java -version 2>&1 | ForEach-Object { "$_" }
        }
        finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        $versionLine = ($current | Select-String -Pattern 'version "([^"]+)"' | Select-Object -First 1).Matches.Groups[1].Value
        if (-not [string]::IsNullOrWhiteSpace($versionLine)) {
            $major = [int](($versionLine.Split(".")[0]))
            if ($major -ge 26) {
                throw "Gradle needs JDK 17, 21, or 25; current java is $major. Set JAVA_HOME to a compatible JDK."
            }
        }
    }
}

function Configure-AndroidHome {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME) -and (Test-Path $env:ANDROID_HOME)) {
        return
    }

    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT) -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
        return
    }

    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk")
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            $env:ANDROID_HOME = $candidate
            $env:ANDROID_SDK_ROOT = $candidate
            return
        }
    }
}

Configure-JavaHome
Configure-AndroidHome

Push-Location $RootDir
try {
    & (Join-Path $RootDir "gradlew.bat") @args
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
