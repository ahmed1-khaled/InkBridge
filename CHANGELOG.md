# Changelog

All notable changes to InkBridge are recorded here.

## 0.1.0-alpha — 2026-11-22

### Added

- Native Android tablet application with active-stylus input, pressure-aware strokes, pen, marker, eraser, color palette and brush sizing.
- Bluetooth Classic RFCOMM/SPP transport with secure paired-device connection and bidirectional control frames.
- Windows .NET 8 WPF companion with incoming Bluetooth COM-port selection and a transparent click-through overlay spanning the virtual desktop.
- Synchronized Undo, Redo and Clear controls on both devices.
- PNG export from the Android writing surface.
- GitHub Actions workflows to build a debug APK and self-contained Windows x64 EXE, with release assets on version tags.
- Setup, architecture, protocol, test-plan and security documentation.

### Known limitations

- Bluetooth SPP/Incoming COM support and stylus behavior require testing with the actual laptop, Bluetooth adapter, tablet and active pen.
- No BLE, Wi-Fi or USB transport fallback, Windows installer, code signing, automatic reconnect or Play Store release signing yet.
