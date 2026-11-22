$ErrorActionPreference = 'Stop'
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw 'Gradle is not installed. Use GitHub Actions, or install Gradle 8.9 and Android SDK 35.'
}
gradle --no-daemon :android-app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Android APK build failed.' }
Write-Host 'APK: android-app/build/outputs/apk/debug/android-app-debug.apk' -ForegroundColor Green
