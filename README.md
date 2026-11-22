# InkBridge

**Turn an Android tablet into a Bluetooth writing pad for a Windows laptop.**

InkBridge is an early-stage, open-source teaching tool. The Android app captures stylus strokes; the Windows companion receives them over Bluetooth Classic RFCOMM/SPP and draws them in a transparent, click-through overlay above the desktop.

> **Alpha status:** source code and automated build pipelines are prepared, but the first end-to-end test on a physical Android tablet and Windows Bluetooth adapter has not yet been performed. Bluetooth SPP support and incoming COM-port behavior vary by Windows driver and tablet. Treat this as an MVP for testing, not a classroom-certified release.

## What is included

- `android-app/` — native Kotlin Android application; paired-device picker, pen/marker/eraser, pressure-aware strokes, stylus-only mode, undo/redo, clear and PNG export.
- `windows-companion/` — .NET 8 WPF desktop companion; Bluetooth COM-port receiver, virtual-desktop overlay, click-through window, undo/redo/clear and basic activity log.
- `.github/workflows/build.yml` — builds a debug APK and self-contained Windows x64 EXE on GitHub Actions; a `v*` tag creates a GitHub Release containing both files.
- `docs/` — Bluetooth setup, architecture, protocol, test plan and an honest validation-status report.

## Connection design

```text
Android tablet (APK)
  Active stylus / MotionEvent
          |
          v
  Normalized JSON stroke frames
          |
          v
  Bluetooth Classic RFCOMM / SPP
          |
          v
Windows Bluetooth incoming COM port
          |
          v
InkBridge Companion (EXE)
          |
          v
Transparent click-through desktop overlay
```

The transport is Bluetooth Classic SPP, not BLE and not Wi-Fi. The Android app connects to an already-paired laptop using the standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB`. The Windows application reads the incoming serial port using `System.IO.Ports`.

## Quick start

### 1. Build the apps

The easiest route is the GitHub Actions workflow:

1. Push this repository to GitHub.
2. Open **Actions → Build InkBridge → Run workflow**.
3. Download `InkBridge-Android-APK` and `InkBridge-Windows-x64` from the completed run.

For local build commands, see [Build and release](docs/BUILD_RELEASE.md). Current static checks and the hardware/build checks still outstanding are listed in [Validation status](docs/VALIDATION_STATUS.md).

### 2. Configure Bluetooth on Windows

1. Turn on Bluetooth on the Windows laptop.
2. Open **Settings → Bluetooth & devices** and pair the Android tablet with the laptop.
3. Open the classic Bluetooth control panel (`control.exe bthprops.cpl`).
4. In **COM Ports**, add an **Incoming** port (the tablet initiates the connection). Note its COM number, for example `COM7`.
5. Open InkBridge Companion, select that incoming COM port, and click **Open port**.

Windows 11 includes RFCOMM and Serial Port Profile support, but the exact COM-port UI and availability depend on the Bluetooth driver. If Windows does not offer an incoming SPP port, this alpha build cannot connect through that adapter/driver yet.

### 3. Connect from Android

1. Install the APK and open InkBridge.
2. Grant the **Nearby devices** permission when Android asks.
3. Ensure the tablet and laptop are paired in Android Bluetooth settings.
4. Tap **Connect laptop**, then choose the paired laptop.
5. On the Windows app, click **Show overlay** (or start writing; the first received frame will show it automatically).
6. Write with an active stylus. Ink should appear over the Windows desktop.

### 4. Classroom workflow

Open PowerPoint, a PDF, a browser, or another teaching resource on the laptop. Keep the InkBridge overlay visible. Write on the tablet; the overlay is click-through so normal mouse and keyboard interaction can continue underneath. Hide the overlay from InkBridge Companion when finished.

## Requirements

- Android 8.0+ (API 26+) device with Bluetooth Classic and an active stylus for best results.
- Windows 10/11 x64 laptop with Bluetooth Classic RFCOMM/SPP support and an incoming Bluetooth COM port.
- .NET 8 Windows runtime is bundled in the self-contained EXE; no separate .NET installation should be needed for that published build.
- Both devices must be paired in system Bluetooth settings before connecting.

A generic capacitive stylus is treated as touch and does not provide reliable pressure sensitivity or palm rejection. Use a tablet-compatible active pen (for example, the device manufacturer's supported pen).

## Current limitations

- The first alpha uses a Windows incoming virtual COM port; it does not create a Bluetooth SPP server/COM port itself.
- No BLE, Wi-Fi or USB fallback is implemented.
- The writing surface maps a 16:9 tablet canvas to the Windows virtual desktop. Very different monitor aspect ratios may stretch the handwriting.
- It is a single-user, single-tablet stream. No session discovery, multi-tablet support, installer, code signing or automatic updater yet.
- PNG export is currently from the tablet canvas. The Windows overlay itself is not exported as a screenshot by the companion.
- Device-specific stylus pressure, eraser inversion and palm rejection must be verified on real hardware.

## Security and privacy

InkBridge does not use a cloud service or send data to a remote server. Bluetooth Classic secure RFCOMM is used with a paired device. The app enumerates already-paired devices only; it does not scan for nearby devices. Do not pair with untrusted devices. See [Security](SECURITY.md).

## Contributing

Issues and pull requests are welcome. Please include Windows version, Bluetooth adapter/driver, tablet model, Android version, stylus model, and the COM-port direction when reporting connection problems. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT. See [LICENSE](LICENSE).
