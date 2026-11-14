$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $repoRoot "backend"
$credentialsFile = Join-Path $backendDir "service_account.json"
$cacheDir = Join-Path $backendDir ".demo-cache"
$venvPython = Join-Path $backendDir ".venv\Scripts\python.exe"

if (-not (Test-Path $credentialsFile)) {
    throw "Missing $credentialsFile. Keep the Earth Engine service-account file local and do not commit it."
}

if (-not (Test-Path $venvPython)) {
    $pythonLauncher = Get-Command py -ErrorAction SilentlyContinue
    if ($pythonLauncher) {
        $pythonArgs = $null
        foreach ($version in @("3.11", "3.13")) {
            & $pythonLauncher.Source "-$version" --version 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $pythonArgs = @("-$version")
                break
            }
        }
        if ($null -eq $pythonArgs) {
            throw "Install Python 3.11 or 3.13, then retry. Check installed versions with 'py --list'."
        }
        & $pythonLauncher.Source @pythonArgs -m venv (Join-Path $backendDir ".venv")
    } else {
        $python = Get-Command python -ErrorAction SilentlyContinue
        if (-not $python) {
            throw "Install Python 3.11 or 3.13 and ensure the 'py' launcher or 'python' is on PATH."
        }
        $installedVersion = & $python.Source -c "import sys; print('%d.%d' % sys.version_info[:2])"
        if ($LASTEXITCODE -ne 0 -or $installedVersion.Trim() -notin @("3.11", "3.13")) {
            throw "The local backend requires Python 3.11 or 3.13; found $installedVersion."
        }
        & $python.Source -m venv (Join-Path $backendDir ".venv")
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create the backend virtual environment. Install Python 3.11 or 3.13 and retry."
    }
}

$pythonVersion = & $venvPython -c "import sys; print('%d.%d' % sys.version_info[:2])"
if ($LASTEXITCODE -ne 0 -or $pythonVersion.Trim() -notin @("3.11", "3.13")) {
    throw "The backend virtual environment must use Python 3.11 or 3.13; found $pythonVersion."
}

& $venvPython -m pip install -r (Join-Path $backendDir "requirements.txt")
if ($LASTEXITCODE -ne 0) {
    throw "Backend dependency installation failed."
}

$secureKey = Read-Host "Enter the demo CROP_ADVISOR_API_KEY (use the same key when building the demo APKs)" -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    if ([string]::IsNullOrWhiteSpace($plainKey)) {
        throw "CROP_ADVISOR_API_KEY cannot be empty."
    }
    $env:EE_PROJECT = "umaoyaproject-513615"
    $env:EE_SERVICE_ACCOUNT_FILE = $credentialsFile
    $env:ADVISOR_CACHE_DIR = $cacheDir
    $env:CROP_ADVISOR_API_KEY = $plainKey
    New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null

    Write-Host "Starting the local API on port 8000. Keep this window open during the demo."
    Write-Host "Check readiness at http://127.0.0.1:8000/health"
    Write-Host "For Wi-Fi phones, use this laptop's private IPv4 address from ipconfig."
    Write-Host "Press Ctrl+C to stop the server."
    Push-Location $backendDir
    try {
        & $venvPython -m uvicorn main:app --host 0.0.0.0 --port 8000
        if ($LASTEXITCODE -ne 0) {
            throw "The local API stopped with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
} finally {
    Remove-Item Env:CROP_ADVISOR_API_KEY -ErrorAction SilentlyContinue
    Remove-Item Env:EE_SERVICE_ACCOUNT_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:EE_PROJECT -ErrorAction SilentlyContinue
    Remove-Item Env:ADVISOR_CACHE_DIR -ErrorAction SilentlyContinue
    $plainKey = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
}
