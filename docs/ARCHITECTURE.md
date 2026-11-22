# Architecture

## Components

### Android APK

- Kotlin Android Activity with a page-aware custom DrawingView.
- MotionEvent provides normalized page coordinates, pointer type and pressure where the digitizer reports it.
- Wi-Fi TCP is the primary transport. Android connects to the laptop's IPv4 address on port 45832 and performs a 6-digit token handshake.
- The tablet sends a compressed JPEG page frame when the paper/PDF page changes, followed by newline-delimited JSON frames for pen points and editing commands.
- After a successful connection, Android re-sends the current page and visible ink so the Windows preview is restored after reconnecting.
- AndroidX WindowInsets APIs provide an immersive mode that hides status and navigation bars while retaining the writing toolbar.
- Finger/touch input is enabled by default; the user can switch to stylus-only mode.

### Windows EXE

- .NET 8 WPF desktop application.
- A TcpListener binds to all IPv4 interfaces on TCP port 45832. It accepts one tablet stream at a time.
- A random six-digit connection code is stored in the current Windows user's LocalAppData folder and displayed in the app. The Android client must provide it in the first JSON handshake frame.
- The receiver validates each frame and updates InkCanvasView, a page preview embedded inside the main application window.
- The optional OverlayWindow remains a transparent topmost click-through surface for presenting over PowerPoint, PDF readers and browsers. It is disabled by default and can be enabled from the Windows UI.
- UpdateService checks public GitHub Releases and offers a verified self-update when a newer Windows build is published.

## Wi-Fi transport

- Windows listens on 0.0.0.0:45832, making the service reachable through active Wi-Fi/Ethernet interfaces.
- Android and Windows must be on the same reachable LAN or hotspot. Guest networks with client isolation can block peer-to-peer traffic.
- The initial client frame is {"type":"hello","token":"123456","protocol":2}. Windows replies with {"type":"welcome","protocol":2} or {"type":"denied"}.
- Each following frame is a compact UTF-8 JSON object followed by a newline. A receiver reads complete lines rather than assuming one socket read equals one frame.
- The paper frame contains the page aspect ratio and a Base64 JPEG image. Drawing frames contain normalized page coordinates, pressure, color and width.
- The 6-digit token is a local pairing gate, not end-to-end encryption. Use a trusted private network; Windows Firewall should allow InkBridge on Private networks.

## Data flow

~~~text
Android stylus/touch MotionEvent
   -> normalize to page coordinates
   -> JPEG page frame + newline-delimited JSON ink frames
   -> authenticated TCP over local Wi-Fi
   -> Windows TcpListener / handshake validation
   -> embedded WPF page preview
   -> optional transparent click-through overlay
~~~

## PDF workspace

- Android's PdfRenderer displays imported PDF pages as the page background.
- New notebook pages can use blank, ruled, grid, dot-grid or Cornell-style backgrounds.
- When a page changes, its rendered background is mirrored to Windows as a compressed JPEG.
- Strokes are stored in normalized page coordinates and flattened together with each page background when exporting with PdfDocument.
- Exported PDFs are rasterized page images; searchable/selectable text is not preserved in the exported copy.
- The latest saved PDF can be shared through Android's system share sheet.

## GitHub update flow

- A successful push to main triggers separate Android and Windows builds.
- With a private Android signing keystore configured as GitHub Actions Secrets, the workflow publishes both assets as a versioned GitHub Release.
- Android checks the latest release, downloads the APK, verifies the checksum when available, and hands off installation to Android's user-confirmed installer.
- Windows checks the latest release, verifies the downloaded EXE digest when available, and replaces its executable after closing the running process.

## Scope and privacy

- No account, cloud relay or external drawing server.
- The drawing session stays on the local network; GitHub is used for release metadata and update downloads.
- One Android device and one Windows receiver at a time.
- The local 6-digit connection code is saved in Windows LocalAppData and in Android's private app preferences after entry. It should not be treated as cryptographic encryption.

## Overlay visibility

The desktop app always displays the page and pen strokes in its own Live Tablet Canvas panel. The transparent presentation overlay is optional and is not shown by default. After the presenter explicitly hides it, incoming strokes continue updating the hidden drawing model; the in-app preview remains live.
