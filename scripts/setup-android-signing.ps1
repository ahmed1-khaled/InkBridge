$ErrorActionPreference = "Stop"
$repo = "ahmed1-khaled/InkBridge"
$secretFolder = Join-Path $env:LOCALAPPDATA "InkBridge"
New-Item -ItemType Directory -Path $secretFolder -Force | Out-Null
$keystore = Join-Path $secretFolder "inkbridge-release.jks"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool was not found. Install Android Studio or a JDK 17+ and ensure keytool is on PATH."
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is required. Install it and run gh auth login first."
}

Write-Host "Creating a private Android signing key. Keep the passwords safe; losing this key prevents future APK updates." -ForegroundColor Cyan
Write-Host "The keystore will be stored outside the repository at: $keystore"
if (Test-Path $keystore) {
    throw "A keystore already exists at $keystore. Do not overwrite it. Use the original key for every later release."
}

& keytool -genkeypair -v -storetype JKS -keystore $keystore -alias inkbridge -keyalg RSA -keysize 3072 -validity 10000
if ($LASTEXITCODE -ne 0) { throw "keytool failed to create the signing key." }

$storePassword = Read-Host "Enter the keystore password you just created"
$keyPassword = Read-Host "Enter the key password (usually the same password)"
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
Write-Host "Android signing secrets were saved to GitHub. Keep this keystore and its passwords backed up securely." -ForegroundColor Green
Write-Host "The key file is outside the repository and will not be committed."
