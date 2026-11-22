# Build and release

## GitHub Actions (recommended)

The workflow at `.github/workflows/build.yml` builds both deliverables on GitHub-hosted runners. It uses the current major releases of the GitHub-hosted workflow actions and pins the Android Gradle Plugin/Gradle pair to a compatible, conservative toolchain:

- Android: debug APK from `:android-app:assembleDebug`.
- Windows: self-contained, single-file x64 `.exe` using .NET 8.

Run **Actions → Build InkBridge → Run workflow** and download the two artifacts from the workflow run. The first Android build downloads the required SDK/build tools.

To publish a versioned GitHub Release, push a tag such as:

```powershell
git tag v0.1.0-alpha
git push origin v0.1.0-alpha
```

The workflow attaches the APK and EXE to the release automatically.

## Create the GitHub repository from this folder

The prepared project folder includes `scripts/create-github-repo.ps1`. On Windows:

1. Install Git for Windows and GitHub CLI (`gh`).
2. Open PowerShell in the extracted `InkBridge` folder.
3. Run `gh auth login` and finish browser authentication.
4. Run:

   ```powershell
   Set-ExecutionPolicy -Scope Process Bypass
   .\scripts\create-github-repo.ps1 -Visibility public
   ```

The script initializes Git if needed, creates `ahmed1-khaled/InkBridge`, and pushes the current branch. Use `-Visibility private` to keep the repo private.

## Local Windows EXE build

Install .NET 8 SDK, then run:

```powershell
.\scripts\build-windows.ps1
```

Output: `publish/InkBridge.Companion.exe`.

## Local Android APK build

Install JDK 17, Android SDK Platform 35 and Build Tools, plus Gradle 8.9. Then run:

```powershell
.\scripts\build-android.ps1
```

Output: `android-app/build/outputs/apk/debug/android-app-debug.apk`.

This is a debug-signed APK for sideloading/testing. It is not a Play Store release. A production release needs a private signing key and a secure signing workflow; never commit a keystore or signing password.
