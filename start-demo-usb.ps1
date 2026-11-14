$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkPath = Join-Path $repoRoot "frontend\app\build\outputs\apk\demoUsb\debug\app-demoUsb-debug.apk"

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
$adbPath = if ($adbCommand) { $adbCommand.Source } else { $null }

if (-not $adbPath) {
    $sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) | Where-Object { $_ }
    if ($env:LOCALAPPDATA) {
        $sdkRoots += Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }

    foreach ($sdkRoot in $sdkRoots) {
        $candidate = Join-Path $sdkRoot "platform-tools\adb.exe"
        if (Test-Path $candidate) {
            $adbPath = $candidate
            break
        }
    }
}

if (-not $adbPath) {
    throw "Android Debug Bridge (adb) was not found. Install Android SDK Platform-Tools or add platform-tools to PATH."
}

$deviceOutput = & $adbPath devices
if ($LASTEXITCODE -ne 0) {
    throw "Could not query Android devices with adb."
}

$deviceSerials = @(
    $deviceOutput |
        ForEach-Object {
            if ($_ -match "^\s*(\S+)\s+device\s*$") { $Matches[1] }
        }
)

if ($deviceSerials.Count -eq 0) {
    $deviceOutput | ForEach-Object { Write-Host $_ }
    throw "No authorized phone found. Connect it by USB, enable USB debugging, accept its computer authorization prompt, and run this script again."
}

$health = $null
try {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:8000/health" -TimeoutSec 5
} catch {
    throw "The laptop backend is not responding at http://127.0.0.1:8000/health. Start it with .\start-demo-backend.ps1 first."
}

if ($health.status -ne "ok") {
    throw "The laptop backend health check did not return status 'ok'."
}

$secureKey = Read-Host "Enter the demo CROP_ADVISOR_API_KEY used by the running backend" -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $env:CROP_ADVISOR_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    if ([string]::IsNullOrWhiteSpace($env:CROP_ADVISOR_API_KEY)) {
        throw "CROP_ADVISOR_API_KEY cannot be empty."
    }

    $javaHome = $env:JAVA_HOME
    if (-not $javaHome -and (Test-Path "C:\Program Files\Android\Android Studio\jbr\bin\java.exe")) {
        $javaHome = "C:\Program Files\Android\Android Studio\jbr"
        $env:JAVA_HOME = $javaHome
        $env:Path = "$javaHome\bin;$env:Path"
    }

    Push-Location $repoRoot
    try {
        & (Join-Path $repoRoot "gradlew.bat") :frontend:app:assembleDemoUsbDebug
        if ($LASTEXITCODE -ne 0) {
            throw "USB demo APK build failed."
        }
    } finally {
        Pop-Location
        Remove-Item Env:CROP_ADVISOR_API_KEY -ErrorAction SilentlyContinue
    }

    if (-not (Test-Path $apkPath)) {
        throw "The USB APK build completed but the expected APK was not found at $apkPath."
    }

    foreach ($serial in $deviceSerials) {
        Write-Host "Forwarding laptop API to phone $serial..."
        & $adbPath -s $serial reverse tcp:8000 tcp:8000
        if ($LASTEXITCODE -ne 0) {
            throw "Could not configure USB port forwarding for phone $serial."
        }

        Write-Host "Installing the USB demo app on phone $serial..."
        & $adbPath -s $serial install -r $apkPath
        if ($LASTEXITCODE -ne 0) {
            throw "Could not install the USB demo APK on phone $serial."
        }
    }

    Write-Host "USB demo app installed. Keep the backend running and each phone connected by USB."
} finally {
    Remove-Item Env:CROP_ADVISOR_API_KEY -ErrorAction SilentlyContinue
    if ($keyPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    }
}
