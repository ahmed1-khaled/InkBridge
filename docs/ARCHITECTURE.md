# Architecture

## Components

### Android APK

- Kotlin Android Activity with a responsive page-aware custom View.
- MotionEvent provides coordinates, pointer type and pressure when the digitizer reports them.
- The page surface adapts to the selected PDF page or notebook template aspect ratio and sends normalized coordinates in the range [0, 1].
- BluetoothSppClient opens a secure RFCOMM socket to a paired Windows laptop and serializes newline-delimited UTF-8 JSON frames.
- Finger/touch input is enabled by default for phones and tablets; the user can switch to stylus-only mode.

### Windows EXE

- .NET 8 WPF desktop application.
- 32feet.NET BluetoothListener advertises the InkBridge RFCOMM service and waits for a paired Android device to connect. There is no manual virtual COM-port setup.
- The accepted BluetoothClient exposes a bidirectional stream. The desktop reads JSON frames and writes undo/redo/clear commands back to Android.
- OverlayWindow is a transparent topmost WPF window positioned over the Windows virtual desktop. Extended window styles make it click-through and prevent activation.
- Strokes are rendered as rounded line segments. The overlay supports draw, eraser, undo, redo and clear frames.
- UpdateService checks the public GitHub Releases API and offers a verified self-update when a newer Windows build is published.

## Bluetooth service

The Android client connects to the standard Serial Port Profile UUID 00001101-0000-1000-8000-00805F9B34FB. Windows advertises the same RFCOMM service using 32feet.NET. The devices must be paired once in operating-system settings. The transport is a local byte stream; the app does not require a cloud relay or Internet access to draw.

## Data flow

~~~text
Android stylus/touch MotionEvent
   -> page normalization
   -> newline-delimited JSON frames
   -> secure Bluetooth Classic RFCOMM
   -> Windows BluetoothListener / accepted stream
   -> JSON frame validation
   -> transparent click-through desktop overlay
~~~

## PDF workspace

- Android's PdfRenderer displays imported PDF pages as the page background.
- New notebook pages can use blank, ruled, grid, dot-grid or Cornell-style backgrounds.
- Strokes are stored in normalized page coordinates and flattened together with each page background when exporting with PdfDocument.
- Exported PDFs are rasterized page images; searchable/selectable text is not preserved in the exported copy.
- The latest saved PDF can be shared through Android's system share sheet.

## GitHub update flow

- A successful push to main triggers separate Android and Windows builds.
- With a private Android signing keystore configured as GitHub Actions Secrets, the workflow publishes both assets as a versioned GitHub Release.
- Android checks the latest release, downloads the APK, verifies the checksum when available, and hands off installation to Android's user-confirmed installer.
- Windows checks the latest release, verifies the downloaded EXE digest when available, and replaces its executable after closing the running process.

## Scope and privacy

- No account, cloud relay, drawing telemetry or external drawing server.
- Only already-bonded Bluetooth devices are listed; the app does not scan continuously.
- One Android device and one Windows receiver at a time.
- Internet is used for release metadata and update downloads only. Ink transfer remains local Bluetooth traffic.

## Overlay visibility

The desktop overlay auto-shows on the first received pen frame. After the presenter explicitly hides it, incoming strokes continue updating the hidden drawing model without forcing it back onto the screen. The Show overlay button clears that suppression.
