# Validation status

Updated 2026-11-22 for the Wi-Fi live-canvas development branch.

## Previous release build 42 (v0.2.42)

- [x] Android signed Release APK built successfully on GitHub Actions.
- [x] Windows self-contained x64 EXE published successfully using .NET 8.
- [x] GitHub Release v0.2.42 created with Android APK, Windows EXE and SHA256SUMS.txt.
- [x] The Android signing secrets were present and the release job completed successfully.

## Wi-Fi live-canvas implementation in progress

- [x] Added Android TCP client with a 6-digit pairing handshake and reconnect synchronization.
- [x] Added Windows TCP listener on port 45832 with local IPv4 display and a per-user pairing code.
- [x] Added an embedded WPF live page preview and page-image mirroring for notebook/PDF backgrounds.
- [x] Made the full-screen tablet mode hide Android status and navigation bars.
- [x] Made the transparent desktop presentation overlay optional; the in-app preview is always available.
- [x] Updated connection, protocol and test documentation for Wi-Fi.
- [ ] New branch still requires GitHub Actions build verification.
- [ ] Physical Wi-Fi connection, firewall, page mirroring, latency and PDF tests still require the user's actual laptop and tablet.

## Important validation boundary

The Wi-Fi implementation is not hardware-validated until the user connects the two devices on the same reachable Wi-Fi/hotspot and confirms that the Windows preview receives paper and strokes. Guest network client isolation and Windows Firewall can block the connection even when both devices show Wi-Fi connectivity.

The earlier v0.2.42 release used Bluetooth RFCOMM; its successful CI build did not prove the Bluetooth link worked on the user's adapter. The new 0.3 transport intentionally changes to local Wi-Fi TCP.
