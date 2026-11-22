# Architecture

## Components

### Android APK

- Kotlin Android Activity and a custom `View` for low-latency Canvas drawing.
- `MotionEvent` supplies coordinates, tool type and pressure when the digitizer reports them.
- The 16:9 writing area converts points to normalized coordinates in the range `[0, 1]`.
- `BluetoothSppClient` opens a secure RFCOMM socket to a paired laptop and serializes newline-delimited UTF-8 JSON frames on a single background executor.
- Finger input is disabled by default to reduce accidental palm/finger marks. The user can enable it.

### Windows EXE

- .NET 8 WPF desktop application.
- `System.IO.Ports.SerialPort` opens the configured incoming virtual COM port.
- A line buffer reconstructs complete JSON frames from arbitrary serial chunks.
- `OverlayWindow` is a transparent topmost WPF window positioned over the Windows virtual desktop. Extended window styles make it click-through and prevent activation.
- Strokes are rendered as individual rounded line segments. The overlay supports draw, eraser, undo, redo and clear frames.

## Why SPP for the first version?

The requested link is Bluetooth-only. Android exposes RFCOMM/SPP as a stream socket and Windows 11 lists RFCOMM and SPP among its supported Bluetooth profiles. Using the Windows virtual incoming COM endpoint keeps the Windows receiver small and makes it possible to implement in a portable .NET desktop app. The trade-off is that incoming COM-port creation is dependent on the Windows Bluetooth driver and control-panel support.

## Data flow

```text
Stylus MotionEvent
   -> Android content-area normalization
   -> newline-delimited JSON v1
   -> secure RFCOMM/SPP socket
   -> Windows incoming COM port
   -> line framing + JSON parse
   -> WPF overlay stroke model
   -> transparent click-through desktop rendering
```

## Scope decisions

- No account, cloud relay, analytics, telemetry or external server.
- Only already-bonded devices are listed; the app does not perform discovery scans.
- One tablet and one Windows receiver at a time.
- The first implementation deliberately uses a virtual COM port rather than WinRT's lower-level Bluetooth APIs. If a target Windows driver cannot expose an incoming SPP port, a future version can replace the transport with a native RFCOMM server implementation or offer BLE/USB as alternatives.

## Overlay visibility

The desktop overlay auto-shows on the first received pen frame. After the presenter explicitly hides it, incoming strokes continue updating the hidden drawing model without forcing the overlay back onto the screen. The Show overlay button clears that suppression.
