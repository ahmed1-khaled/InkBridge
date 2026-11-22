# InkBridge

**Turn an Android phone or tablet into a live writing pad for a Windows laptop.**

InkBridge is a focused digital whiteboard for teaching, explaining, annotating PDFs and presenting. Write on the Android canvas with a stylus or finger; the same page and strokes appear live inside the Windows companion app.

> **Version 0.3 Wi-Fi transport update:** Bluetooth pairing is no longer part of the connection setup. Ink is sent over an authenticated TCP connection on your local Wi-Fi network or hotspot. Physical-device testing is still required for final validation.

## What you get

- **Android writing canvas:** large responsive page, pen, marker, eraser, undo/redo, colors, brush size and touch/stylus-only input.
- **Immersive full screen:** hide the Android status and navigation bars so the time, battery and system UI do not take space while writing. Swipe from the screen edge to temporarily reveal system bars.
- **Live page mirroring:** the page background, imported PDF page or notebook template and pen strokes are mirrored into the Windows app's own preview panel.
- **Notebook pages:** Blank, Ruled, Grid, Dots and Cornell templates with 1–100 pages.
- **PDF workflow:** open PDFs, annotate pages, navigate, export an annotated copy and share it.
- **Windows companion:** modern dark interface, live in-app page preview, status and connection details. An optional transparent click-through overlay is available for PowerPoint, PDF readers and browsers.
- **Local Wi-Fi connection:** both devices join the same Wi-Fi network or hotspot. Windows shows its local IP addresses and a 6-digit pairing code; Android connects directly to TCP port 45832.
- **GitHub updates:** successful builds publish versioned Android APK and Windows EXE assets when the Android signing key is configured.

## Quick start

### 1. Windows laptop

1. Download and run `InkBridge.Companion.exe` from the latest GitHub Release.
2. Connect the laptop to the Wi-Fi network you want to use.
3. Keep InkBridge open. The top connection card shows the laptop IP address and a 6-digit code.
4. If Windows Firewall asks, allow InkBridge on **Private networks**.

### 2. Android tablet or phone

1. Connect the Android device to the **same Wi-Fi network or hotspot** as the laptop.
2. Open InkBridge and tap **Connect Wi-Fi**.
3. Enter one of the laptop IP addresses and the 6-digit code displayed in the Windows app.
4. The app remembers the connection details. Tap Connect again to reconnect after a drop.

Bluetooth pairing is not required. An Internet connection is not required for the live writing session; the devices only need local network connectivity. See [Wi-Fi setup](docs/WIFI_SETUP.md) for troubleshooting.

## Live page preview and presentation overlay

The Windows app always mirrors the paper/PDF background and all ink in the **Live Tablet Canvas** panel. You do not need to mirror the entire Android screen.

The **Show desktop overlay** button is optional. It places only the ink over other Windows applications, click-through, for presentations. Hide the overlay at any time; the in-app preview keeps working.

## Full-screen writing on Android

Tap **Full screen** in the Android header to hide the Android status and navigation bars. The writing tools remain available. Swipe from a screen edge to temporarily reveal system bars; tap **Exit full screen** to restore the normal view.

## PDF notebooks

- **Open PDF** uses Android's system document picker.
- **New notebook** offers Blank, Ruled, Grid, Dots and Cornell templates.
- **Save PDF** exports all pages with backgrounds and handwriting.
- **Share** opens Android's share sheet for the last exported PDF.

Exported PDFs are flattened page images. Their visual appearance is retained, but imported PDF text is not selectable/searchable in the exported copy. Keep the original PDF when text accessibility is important.

## Requirements

- Android 8.0+ (API 26+) and a Wi-Fi connection.
- Windows 10/11 x64.
- Both devices must be on the same local network. Guest Wi-Fi networks with client isolation may block device-to-device traffic.
- An active stylus is recommended for pressure sensitivity and palm rejection; finger touch is enabled by default.

## Connection details

- Transport: TCP over local Wi-Fi/LAN.
- Windows listening port: `45832`.
- Initial handshake: 6-digit local pairing code displayed by Windows.
- Payload: newline-delimited UTF-8 JSON frames, including a compressed JPEG page image and normalized pen points.
- Ink stays on the local network. GitHub is contacted only for update metadata and release downloads.
- No Bluetooth pairing, COM port, cloud relay or account login is required.

## Automatic updates

See [docs/AUTO_UPDATES.md](docs/AUTO_UPDATES.md). Android releases must use the original private signing key. The key is stored locally outside the repository and configured in GitHub Actions Secrets. Back it up securely; never commit or share it. Android installation always requires user approval.

## Development

- Android: Kotlin, Android SDK 35, Gradle 8.9, AndroidX Core.
- Windows: .NET 8 WPF.
- Protocol: TCP with newline-delimited JSON frames; see [docs/PROTOCOL.md](docs/PROTOCOL.md).
- CI: GitHub Actions in `.github/workflows/build.yml`.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/WIFI_SETUP.md](docs/WIFI_SETUP.md), [docs/BUILD_RELEASE.md](docs/BUILD_RELEASE.md), [docs/TEST_PLAN.md](docs/TEST_PLAN.md) and [docs/VALIDATION_STATUS.md](docs/VALIDATION_STATUS.md).

## Known limitations

- Actual Wi-Fi connection, Windows Firewall behavior, stylus latency and PDF workflow must be validated on the target laptop/tablet.
- Both devices must be on the same reachable LAN; guest network isolation or some institutional Wi-Fi policies may block local traffic.
- The laptop's IP address can change when it joins another Wi-Fi network; enter the new address in Android.
- Large imported PDFs can use substantial memory because pages are rasterized and cached.
- PDF export is rasterized, not a vector or searchable PDF.
- Windows EXE is not code-signed; SmartScreen may show a warning.
- No iOS app is included.

## License

MIT. See LICENSE.
