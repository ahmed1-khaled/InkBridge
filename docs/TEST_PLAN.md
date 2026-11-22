# MVP test plan

The following checks are required before relying on InkBridge in a lecture. They have **not** all been run on physical hardware yet.

## Build checks

- [ ] Android debug APK builds on GitHub Actions with JDK 17, Gradle 8.9 and Android SDK 35.
- [ ] Windows x64 self-contained EXE publishes on a Windows runner.
- [ ] The APK installs on Android 8.0+ and requests Nearby devices permission on Android 12+.

## Bluetooth checks

- [ ] Pair the tablet and Windows laptop in OS settings.
- [ ] Create an Incoming SPP COM port in Windows classic Bluetooth settings.
- [ ] Open the incoming COM port in InkBridge Companion.
- [ ] Connect from the APK using the secure RFCOMM SPP UUID.
- [ ] Leave the link active for at least 20 minutes and check for disconnects.
- [ ] Test reconnect after toggling Bluetooth off/on on each side.

## Ink checks

- [ ] Pen strokes appear on tablet and Windows overlay.
- [ ] Fast loops and diagonal lines do not show unacceptable gaps.
- [ ] Stylus pressure changes width where supported by the tablet.
- [ ] Active pen eraser inversion and app Eraser mode remove strokes.
- [ ] Stylus-only mode ignores resting fingers; Finger mode accepts touch.
- [ ] Undo, redo and clear stay synchronized between tablet and PC.
- [ ] Compare a 16:9 tablet writing area with the actual projector/monitor ratio.

## Desktop checks

- [ ] Overlay covers all monitors in the virtual desktop.
- [ ] Normal mouse clicks pass through to the app below.
- [ ] Keyboard focus stays with PowerPoint/PDF/browser while drawing from tablet.
- [ ] Overlay hide/show does not lose already drawn marks.
- [ ] Test with PowerPoint slideshow, a PDF viewer and a browser.

## Performance checks

Record the following for each tablet/laptop pair:

- tablet and stylus model
- Android version
- Windows version and Bluetooth adapter/driver
- COM port direction and number
- subjective latency, missed points and reconnect behavior
- CPU and memory while drawing a dense page

Do not describe this alpha as classroom-ready until the checks above have passed on the actual hardware that will be used in class.

## Overlay visibility regression

- Start streaming ink so the overlay appears automatically.
- Click **Hide overlay** and keep drawing from the tablet; the overlay must remain hidden.
- Click **Show overlay**; all strokes written while hidden should appear.

## Cross-device command synchronization

- Send Undo and Redo from the tablet and confirm the Windows overlay follows.
- Send Undo, Redo and Clear from the desktop and confirm the tablet follows.
- Repeat while a pen stroke is in progress; both endpoints should finish the active stroke before applying the command.
