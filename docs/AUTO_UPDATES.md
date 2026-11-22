# Automatic updates

InkBridge uses the public GitHub Releases API for version checks. The drawing stream remains local Bluetooth traffic and does not depend on the Internet.

## What happens on each push

1. A push to main starts the Android and Windows build jobs.
2. Windows publishes a self-contained x64 executable.
3. Android builds a signed release APK only when the signing key secrets are configured. Without them, CI produces a debug-only APK and deliberately skips public release publishing.
4. When both builds succeed and Android signing is configured, GitHub Actions publishes a versioned release containing InkBridge-Android.apk, InkBridge.Companion.exe and SHA256SUMS.txt.
5. Android and Windows check releases/latest on startup and periodically while the app is open.

## One-time Android signing configuration

Android only permits an in-place update when a new APK is signed with the same certificate as the installed app. The signing key is private and must never be committed to this repository.

From Windows PowerShell at the repository root:

~~~powershell
gh auth status
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\setup-android-signing.ps1
~~~

The script:
- generates a JKS file under %LOCALAPPDATA%\InkBridge\, outside the repository;
- asks you to set keystore/key passwords;
- stores the Base64 JKS and its credentials in GitHub Actions Secrets:
  INKBRIDGE_KEYSTORE_BASE64, INKBRIDGE_STORE_PASSWORD, INKBRIDGE_KEY_PASSWORD, and INKBRIDGE_KEY_ALIAS.

Back up the JKS file and passwords in a secure location you control. Do not commit them, send them in chat, or lose them. GitHub Secrets keep the build-time copy private, but are not a replacement for your own backup.

Once secrets are set, go to Actions → Build and publish InkBridge → Run workflow on main. This creates the first signed release. Future successful pushes to main publish releases automatically.

### Important: existing 0.1.0-alpha build

The earlier alpha APK was an unsigned/debug CI build. Android may refuse an in-place update from that build to the newly signed release because the signing certificates differ. Uninstall the old alpha once before installing the first signed stable APK. This removes app-local data, so export anything important first. Future releases signed with the same JKS can update in place.

## Android update experience

- Checks GitHub on app startup and every 30 minutes while the app is active.
- Shows an update prompt and downloads the release APK.
- Verifies the SHA-256 digest where GitHub publishes it.
- Android may require enabling Install unknown apps for InkBridge, then the user must approve the system installer prompt.
- Android does not allow this ordinary app to silently replace itself.

## Windows update experience

- Checks GitHub on startup and every 45 minutes while the companion remains open.
- Offers the update before downloading it.
- Downloads the release EXE and verifies GitHub's SHA-256 digest where available.
- Starts a small PowerShell helper that waits for the running process to exit, replaces the executable, and launches the new copy.
- The app should be installed in a user-writable folder. If the existing location cannot be replaced, the updater uses a per-user fallback folder under LocalAppData.

## Failure modes

- No Internet: local Bluetooth writing continues; update checks fail harmlessly.
- No latest release: no update is shown.
- Missing Android signing secrets: CI can still produce a debug APK artifact and a Windows artifact, but no auto-update release is published.
- Lost Android keystore: previously installed signed copies cannot receive in-place updates signed with a different key. Preserve the key securely.
- Windows SmartScreen: this open-source EXE is not code-signed. Windows may show a reputation warning until the executable gains reputation or is signed by a publisher.
