# Build and release

## Local requirements

- Android Studio or JDK 17 plus Android SDK Platform 35 and Build Tools 35.
- Gradle 8.9 (the Actions workflow configures it automatically).
- .NET 8 SDK for the Windows WPF companion.

## GitHub Actions

The workflow .github/workflows/build.yml runs on every push to main:
- Android job builds a signed Release APK only when Android signing secrets exist; otherwise it produces a debug-only APK artifact.
- Windows job publishes a self-contained win-x64 executable.
- Release job uploads both apps and SHA256 digests to a versioned GitHub Release only after signed Android and Windows builds succeed.

Open Actions → Build and publish InkBridge to inspect logs and download artifacts.

## First Android signing key

For the in-place Android update flow, run scripts/setup-android-signing.ps1 once from Windows PowerShell. Follow AUTO_UPDATES.md and protect the generated keystore. The same key is required for every future APK release.

## Local Android build

~~~powershell
gradle --no-daemon :android-app:assembleDebug
~~~

The APK is written under android-app/build/outputs/apk/debug/.

For a signed local Release build, set the same signing environment variables described in android-app/build.gradle.kts and run:

~~~powershell
gradle --no-daemon :android-app:assembleRelease
~~~

## Local Windows build

~~~powershell
dotnet publish windows-companion/InkBridge.Companion.csproj --configuration Release --runtime win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true --output publish
~~~

Output: publish/InkBridge.Companion.exe.

## Versioning

Actions injects an increasing GitHub Actions run number into both app versions. The Android app reads the latest Release tag v0.2.N; the Windows companion reads its own assembly version and compares it to the same release. The release asset names must remain:
- InkBridge-Android.apk
- InkBridge.Companion.exe
