# Start Here — InkBridge

## 1. Create the GitHub repository

1. Extract `InkBridge-source.zip` to a normal folder on your Windows PC.
2. Install Git for Windows and GitHub CLI if they are not already installed.
3. Open PowerShell in the extracted `InkBridge` folder.
4. Sign in to GitHub once:

   ```powershell
   gh auth login
   ```

5. Create the repository and push the source:

   ```powershell
   Set-ExecutionPolicy -Scope Process Bypass
   .\scripts\create-github-repo.ps1 -Visibility public
   ```

   This creates `ahmed1-khaled/InkBridge`. Use `-Visibility private` instead if you prefer a private repository.

## 2. Download the APK and EXE

The first push to `main` starts the **Build InkBridge** GitHub Actions workflow. When both jobs finish successfully:

1. Open the new repository on GitHub.
2. Select **Actions**.
3. Open the latest **Build InkBridge** run.
4. Download the `InkBridge-Android-APK` and `InkBridge-Windows-x64` artifacts.

The Android file is a debug-signed APK for sideload testing. The Windows file is a self-contained x64 EXE. The EXE is not code-signed, and the APK is not a Play Store release.

## 3. Pair and connect the devices

1. Pair the tablet and laptop in their normal Bluetooth settings.
2. On Windows, run `control.exe bthprops.cpl` and add an **Incoming** Bluetooth COM port if the adapter/driver provides that option.
3. Open InkBridge Companion, select the incoming COM port, then click **Open port**.
4. Install and open InkBridge on Android, grant Nearby devices permission, tap **Connect laptop**, and select the paired Windows PC.
5. Start writing with the tablet's supported active stylus.

See [docs/BLUETOOTH_SETUP.md](docs/BLUETOOTH_SETUP.md) for the full setup and troubleshooting steps. The first release still requires real-device validation; not every Windows Bluetooth driver exposes an incoming SPP COM port.
