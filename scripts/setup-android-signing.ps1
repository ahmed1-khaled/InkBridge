$ErrorActionPreference = "Stop"
$repo = "ahmed1-khaled/InkBridge"
$secretFolder = Join-Path $env:LOCALAPPDATA "InkBridge"
New-Item -ItemType Directory -Path $secretFolder -Force | Out-Null
$keystore = Join-Path $secretFolder "inkbridge-release.jks"

function Find-InkBridgeKeytool {
    # 1) PATH and standard Java environment variables.
    $fromPath = Get-Command keytool.exe, keytool -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($fromPath) { return $fromPath.Source }

    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($variable in @("JAVA_HOME", "JDK_HOME", "STUDIO_JDK", "ANDROID_STUDIO_JDK")) {
        $value = [Environment]::GetEnvironmentVariable($variable)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            $candidates.Add((Join-Path $value "bin\keytool.exe"))
        }
    }

    # 2) Common Android Studio locations (Android Studio bundles its own JBR).
    foreach ($studioRoot in @(
        (Join-Path $env:ProgramFiles "Android\Android Studio"),
        (Join-Path $env:LOCALAPPDATA "Programs\Android Studio"),
        (Join-Path $env:LOCALAPPDATA "JetBrains\Toolbox\apps\AndroidStudio")
    )) {
        $candidates.Add((Join-Path $studioRoot "jbr\bin\keytool.exe"))
        $candidates.Add((Join-Path $studioRoot "jre\bin\keytool.exe"))
        $candidates.Add((Join-Path $studioRoot "bin\keytool.exe"))
        if (Test-Path $studioRoot) {
            Get-ChildItem -Path $studioRoot -Filter keytool.exe -File -Recurse -ErrorAction SilentlyContinue |
                ForEach-Object { $candidates.Add($_.FullName) }
        }
    }

    # 3) Common JDK distributions and Gradle-managed JDK installations.
    $searchPatterns = @(
        (Join-Path $env:ProgramFiles "Java\*\bin\keytool.exe"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium\*\bin\keytool.exe"),
        (Join-Path $env:ProgramFiles "Microsoft\*\bin\keytool.exe"),
        (Join-Path $env:ProgramFiles "Amazon Corretto\*\bin\keytool.exe"),
        (Join-Path $env:ProgramFiles "Zulu\*\bin\keytool.exe"),
        (Join-Path $env:USERPROFILE ".gradle\jdks\*\bin\keytool.exe")
    )
    foreach ($pattern in $searchPatterns) {
        Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

$keytool = Find-InkBridgeKeytool
if (-not $keytool) {
    Write-Host ""
    Write-Host "I could not find keytool.exe. It is normally bundled with Android Studio's JBR or a JDK." -ForegroundColor Yellow
    Write-Host "Check whether Android Studio is installed in one of these locations:"
    Write-Host "  $env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe"
    Write-Host "  $env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe"
    Write-Host ""
    Write-Host "You can locate it manually with this command:"
    Write-Host '  Get-ChildItem "$env:ProgramFiles\Android","$env:ProgramFiles\Java","$env:USERPROFILE\.gradle\jdks" -Filter keytool.exe -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 10 -ExpandProperty FullName'
    throw "Install Android Studio (recommended) or a JDK 17+, then rerun this script. No signing key has been created yet."
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) was not found. Install it, open a new PowerShell window, and run gh auth login."
}

Write-Host "Found keytool: $keytool" -ForegroundColor Green
gh auth status --hostname github.com
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run gh auth login, then rerun this script."
}

Write-Host ""
Write-Host "Creating your private Android signing key. This is a one-time operation." -ForegroundColor Cyan
Write-Host "Keystore location (outside the Git repository): $keystore"
Write-Host "Back up this file and its passwords securely. Losing it prevents in-place Android updates."
if (Test-Path $keystore) {
    throw "A keystore already exists at $keystore. Do not overwrite it; use the original key for all future releases."
}

& $keytool -genkeypair -v -storetype JKS -keystore $keystore -alias inkbridge -keyalg RSA -keysize 3072 -validity 10000
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $keystore)) {
    throw "keytool did not successfully create the signing key. No GitHub secrets have been changed."
}

$storePassword = Read-Host "Enter the keystore password you used during key generation"
$keyPassword = Read-Host "Enter the key password (usually the same password)"
if ([string]::IsNullOrWhiteSpace($storePassword) -or [string]::IsNullOrWhiteSpace($keyPassword)) {
    throw "Passwords cannot be empty. Keep the generated keystore safe and rerun the setup with its original passwords."
}

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))

$base64 | gh secret set INKBRIDGE_KEYSTORE_BASE64 --repo $repo
if ($LASTEXITCODE -ne 0) { throw "Could not upload INKBRIDGE_KEYSTORE_BASE64 to GitHub." }
gh secret set INKBRIDGE_STORE_PASSWORD --repo $repo --body $storePassword
if ($LASTEXITCODE -ne 0) { throw "Could not upload INKBRIDGE_STORE_PASSWORD to GitHub." }
gh secret set INKBRIDGE_KEY_PASSWORD --repo $repo --body $keyPassword
if ($LASTEXITCODE -ne 0) { throw "Could not upload INKBRIDGE_KEY_PASSWORD to GitHub." }
gh secret set INKBRIDGE_KEY_ALIAS --repo $repo --body "inkbridge"
if ($LASTEXITCODE -ne 0) { throw "Could not upload INKBRIDGE_KEY_ALIAS to GitHub." }

Write-Host ""
Write-Host "Android signing secrets were saved to GitHub." -ForegroundColor Green
Write-Host "IMPORTANT: Back up this file and its passwords somewhere safe: $keystore"
Write-Host "The keystore is outside the repository and has not been committed."
