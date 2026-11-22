# InkBridge 0.3 device test plan

GitHub Actions compile success is not proof of physical-device connectivity. Complete these checks on the target Windows laptop and Android tablet before relying on InkBridge for a lecture.

## Build checks

- [ ] Android signed Release APK builds on GitHub Actions with JDK 17, Android SDK 35 and Gradle 8.9.
- [ ] Windows self-contained x64 EXE publishes on a Windows runner.
- [ ] Windows WPF XAML and the embedded preview compile successfully.
- [ ] The Android APK installs as an in-place update over v0.2.42 using the same signing key.

## Wi-Fi connection checks

- [ ] Connect both devices to the same private Wi-Fi network.
- [ ] Open InkBridge on Windows and confirm it displays an IPv4 address and six-digit code.
- [ ] Allow the app through Windows Firewall on Private networks if prompted.
- [ ] Enter the laptop IP and code in Android and confirm the authenticated TCP handshake succeeds.
- [ ] Confirm the Windows badge changes to TABLET CONNECTED.
- [ ] Disconnect/reconnect and confirm the page and current ink are synchronized again.
- [ ] Test with a private mobile hotspot if the router blocks device-to-device traffic.
- [ ] Test timeout and invalid-code error messages.

## Live page mirroring

- [ ] Confirm Blank, Ruled, Grid, Dots and Cornell notebook backgrounds appear in the Windows Live Tablet Canvas panel.
- [ ] Confirm an imported PDF page appears in the desktop preview.
- [ ] Confirm pen, marker, color, width and pressure changes appear with acceptable latency.
- [ ] Confirm fast loops and diagonal lines do not show unacceptable gaps.
- [ ] Confirm eraser, undo, redo and clear remain synchronized.
- [ ] Change PDF/notebook pages and confirm the new page replaces the previous desktop preview.
- [ ] Reconnect after drawing and confirm the current page and visible strokes are replayed.

## Android full-screen and controls

- [ ] Tap Full screen and confirm Android status/navigation bars disappear.
- [ ] Swipe from a screen edge to reveal transient system bars, then confirm they hide again.
- [ ] Exit full screen and confirm normal system bars return.
- [ ] Test touch and stylus-only input, pen tools and horizontal toolbar scrolling.

## Windows presentation tools

- [ ] Confirm pen strokes appear inside the Windows app preview even when the desktop overlay is hidden.
- [ ] Enable Show desktop overlay and test over PowerPoint, a PDF reader and a browser.
- [ ] Confirm mouse clicks pass through the overlay.
- [ ] Hide the overlay and confirm the in-app preview keeps updating.
- [ ] Test on multiple monitors and a projector.
- [ ] Test Windows update replacement and Android user-confirmed APK installation.

## Performance

Record tablet/stylus model, Android version, Windows version, Wi-Fi adapter/router, subjective latency, missed points and CPU/memory while drawing a dense page. Do not describe the build as classroom-ready until these tests pass on the actual hardware.
