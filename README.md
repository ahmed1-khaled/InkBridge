# InkBridge

**An Android phone or tablet becomes a large, flexible writing pad for a Windows laptop.**

InkBridge is an open-source teaching notebook. Draw with an active stylus or finger on Android, stream strokes over Bluetooth Classic to a transparent Windows desktop overlay, and keep your study notes as PDF.

> **Alpha / device test required:** APK and Windows EXE builds are checked by GitHub Actions. End-to-end Bluetooth behavior still needs a test on the actual phone/tablet and Windows Bluetooth adapter.

## What you get

- **Android app:** phone- and tablet-friendly UI, large responsive canvas, remembered laptop pairing, touch/stylus input, pen, marker, eraser, undo/redo, colors and brush size.
- **Notebook pages:** create Blank, Ruled, Grid, Dots and Cornell templates with 1–100 pages.
- **PDF workflow:** open an existing PDF, annotate each page, navigate pages, export an annotated PDF, and share it with other apps.
- **Windows companion:** self-contained Windows x64 EXE. It listens for the tablet directly over Bluetooth RFCOMM. No manual COM-port selection.
- **Desktop overlay:** transparent, click-through ink over PowerPoint, PDF readers, browsers and other Windows applications.
- **GitHub updates:** each push to main builds both apps. With the private Android signing key configured, the workflow publishes a versioned GitHub Release containing the APK and EXE. Both apps check GitHub for updates while running and at startup.

## How connection works

1. Pair the Android device and Windows laptop once using the operating systems' Bluetooth settings.
2. Start InkBridge Companion on Windows; it starts its Bluetooth RFCOMM listener automatically.
3. Start InkBridge on Android and tap **Connect**. The app remembers the selected laptop for next time.
4. Write on the Android canvas. The Windows overlay shows the ink in real time.

The transport is Bluetooth Classic RFCOMM/SPP, not BLE and not Wi-Fi. Internet is only needed for update checks/downloads. Actual pen strokes stay on the local Bluetooth connection.

## PDF notebooks

- **Open PDF** uses Android's document picker. Existing pages are rendered as the background.
- **New notebook** offers Blank, Ruled, Grid, Dots and Cornell paper.
- **Save PDF** exports all pages, including the rendered PDF background and handwriting.
- **Share** opens Android's share sheet for the most recently saved PDF.

Exported PDFs are flattened page images. Their appearance is preserved, but selectable/searchable text from an imported PDF is not retained in the exported file. Keep the original PDF separately when you need searchable text or accessibility layers.

## Requirements

- Android 8.0+ (API 26+) with Bluetooth Classic.
- Windows 10/11 x64 with Bluetooth Classic RFCOMM support and a compatible driver.
- Both devices must be paired in operating-system settings before connecting.
- Active stylus is recommended for palm rejection and pressure sensitivity. Finger input is enabled by default and can be disabled using the toolbar.

## First setup

### Windows

1. Install and open InkBridge.Companion.exe.
2. Keep Bluetooth turned on.
3. Its status should change to **WAITING FOR TABLET**.
4. Pair your phone/tablet with the laptop in Windows Bluetooth Settings once.

### Android

1. Install and open InkBridge.
2. Allow Nearby devices permission.
3. Pair with the laptop if you have not already.
4. Tap **Connect**. If there is more than one paired device, select your laptop. Long-press Connect to choose another laptop later.
5. Start writing. The Windows overlay appears when the first stroke arrives.

The previous alpha used a manual Windows Incoming COM port. This version replaces that flow with an automatic RFCOMM listener; do not follow the old COM-port instructions.

## Automatic updates

See docs/AUTO_UPDATES.md. The first Android release needs a one-time signing key setup. The key must stay private and be backed up. Android will always ask the user to approve installing an APK update. Windows asks before updating, downloads the new executable, replaces it after closing, and relaunches.

### One-time Android signing setup

From the repository root in PowerShell, run:

~~~powershell
gh auth status
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\setup-android-signing.ps1
~~~

Save the generated keystore and passwords in a secure backup. Never commit them. If this key is lost, existing Android installations cannot be updated in-place with future releases signed using a different key.

After the secrets are configured, open **Actions → Build and publish InkBridge → Run workflow** on main once. Future pushes to main automatically create newer releases.

## Security and privacy

Ink strokes are sent locally over paired Bluetooth. GitHub is contacted only for public release metadata and download assets. There is no cloud relay for the drawing session. Android APK installation remains user-confirmed, and the update APK is checked against the SHA-256 release digest where GitHub provides one.

## Development

- Android: Kotlin, Android SDK 35, Gradle 8.9, AndroidX Core.
- Windows: .NET 8 WPF, 32feet.NET for Bluetooth Classic RFCOMM.
- Protocol: newline-delimited JSON frames. See docs/PROTOCOL.md.
- CI: GitHub Actions workflow in .github/workflows/build.yml.

See docs/ARCHITECTURE.md, docs/BLUETOOTH_SETUP.md, docs/BUILD_RELEASE.md, docs/TEST_PLAN.md, and docs/VALIDATION_STATUS.md.

## Known limitations

- Bluetooth implementation must be tested with the specific adapter/driver and Android device.
- Pairing is performed in system Bluetooth settings once; the app remembers the preferred paired device but cannot silently bypass Android pairing/permissions.
- Very large PDFs can consume substantial memory because page snapshots are cached for editing.
- PDF export is flattened/rasterized, not a vector PDF or editable source PDF.
- Automatic Android in-place updates require the original signing key to remain available in GitHub Secrets and safely backed up.
- Windows auto-update is not code-signed; SmartScreen may show a warning for a new open-source EXE.
- No iOS app is included.

## License

MIT. See LICENSE.
