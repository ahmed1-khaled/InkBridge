# Validation status

Updated 2026-11-22 for commit 02b0c433.

## GitHub Actions build 33

- [x] Android debug APK assembled successfully on Ubuntu using JDK 17, Android SDK 35 and Gradle 8.9.
- [x] Windows self-contained x64 EXE published successfully on windows-latest using .NET 8.
- [x] Both CI artifacts uploaded:
  - InkBridge-Android-APK-debug-only
  - InkBridge-Windows-x64
- [x] Workflow concluded successfully. The Publish update release job was intentionally skipped because Android signing secrets are not configured yet.
- [x] AndroidX Core is pinned to 1.15.0 to remain compatible with the current Android Gradle Plugin 8.7.3 and compileSdk 35.
- [x] Windows updater and the direct Bluetooth RFCOMM listener compile successfully.
- [x] GitHub Actions workflow YAML, Android manifest/resource XML and WPF XAML/project XML were checked by the successful build.

## Not yet hardware-validated

- [ ] Direct Bluetooth RFCOMM connection between the user's Windows Bluetooth adapter and the actual Android phone/tablet.
- [ ] Pairing permission behavior, reconnect after disconnect, latency and stylus pressure.
- [ ] Overlay click-through behavior on the user's Windows setup, including multiple monitors and projector.
- [ ] PDF import, page navigation, rasterized export and share flow on physical Android hardware.
- [ ] Windows update replacement and Android user-confirmed APK installation after a signed GitHub Release exists.

## Release readiness

The two binaries are build-verified, but this is still an alpha device-test build. Do not treat a successful CI compile as proof that Bluetooth or the document workflows have been tested on physical devices.

To enable future automatic signed Android releases, run scripts/setup-android-signing.ps1 from the updated local clone. Preserve the generated keystore and passwords in a secure backup. Then run the Build and publish InkBridge workflow once on main; subsequent successful pushes to main publish versioned Releases automatically.

The first signed APK may require one uninstall/reinstall because the earlier alpha APK was signed with the CI debug key. Export any important local notes before uninstalling it.
