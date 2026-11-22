# Security policy

InkBridge is an early-stage local teaching utility. It has no cloud backend and does not intentionally transmit content outside the paired Bluetooth link.

## Supported versions

Only the current `main` branch and the latest tagged release are expected to receive fixes while the project is in alpha.

## Reporting a vulnerability

Please do not publish Bluetooth pairing secrets, personal data or an exploit that affects other users in a public issue. Contact the repository owner through GitHub's private vulnerability reporting feature when available, or open a minimal issue asking for a private contact channel.

## Security notes

- The Android app lists bonded devices only and requests `BLUETOOTH_CONNECT` permission on Android 12+.
- The Android app uses the secure RFCOMM socket API rather than the insecure RFCOMM variant.
- Pair devices using the operating system's normal Bluetooth pairing flow.
- Do not pair with devices you do not trust.
- The Windows receiver parses data received from the serial stream and clamps drawing values; this alpha has not undergone a formal security audit.
