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


## New workspace and transfer checks

- [ ] Restart Windows Companion and confirm the six-digit session code changes.
- [ ] Use the refresh-code control while disconnected and confirm the previous code is rejected.
- [ ] Connect Android using the current code; reconnect after a drop without restarting Windows.
- [ ] Capture the full tablet page to PNG and verify the paper background and strokes are both present.
- [ ] Select a custom crop from the Windows preview and verify the exported image dimensions.
- [ ] Copy the full page from the Windows preview and paste it into Paint or another image editor.
- [ ] Move the Android tool dock left and right; hide and restore the tool list without losing the canvas.
- [ ] Send a small image Android → Windows and Windows → Android; verify the files open correctly.
- [ ] Test a larger file, a zero-byte file, and a transfer interrupted by disconnect; confirm partial files are not mistaken for completed files.
- [ ] Verify Windows receives files in Documents/InkBridge Transfers and Android 10+ receives files in Downloads/InkBridge.


## Detached preview and network diagnostics

- [ ] Open the live preview in its own Windows window; confirm the main app remains open and the page keeps updating.
- [ ] Minimize and maximize/restore the detached preview using the native title-bar buttons.
- [ ] Minimize the main app while the detached preview remains visible and continues updating.
- [ ] Close the detached preview with X; confirm the same live surface returns to the main app without losing strokes or page background.
- [ ] Close the main app while the preview is detached; confirm both windows and the listener shut down cleanly.
- [ ] Use Copy preferred IP and verify it copies the Wi-Fi adapter IPv4 address rather than a VPN/loopback address.
- [ ] Run Allow through Firewall and approve the UAC prompt; verify the rule is scoped to InkBridge, TCP 45832 and Private profile.
- [ ] Pair using ASCII digits, Arabic-Indic digits (٠١٢٣٤٥٦), and Persian digits (۰۱۲۳۴۵۶); all valid forms should normalize and connect.
- [ ] Enter an incorrect code; confirm Android receives a pairing rejection and Windows logs a code/protocol mismatch.
- [ ] Attempt a connection without sending a handshake; confirm Windows times out after eight seconds and accepts the next device.
- [ ] Test with both devices on a private Wi-Fi network and a personal hotspot; confirm guest/client-isolated Wi-Fi is reported as a network limitation.
- [ ] Confirm the tablet and Windows UI use the shared high-contrast blue/white palette and that drawing paper remains white.
