# Contributing

Thanks for helping improve InkBridge.

## Before opening an issue

For Bluetooth or stylus bugs, include:

- tablet model and Android version
- active stylus model (and whether it is the tablet manufacturer's supported pen)
- Windows version
- Bluetooth adapter name and driver version
- whether Windows exposes an **Incoming** SPP COM port
- InkBridge Companion log lines around the failure

Do not include personal Bluetooth pairing keys, device identifiers that you consider private, or other secrets.

## Development notes

- Keep Bluetooth I/O off the Android UI thread.
- Preserve newline-delimited JSON framing in protocol v1.
- Clamp untrusted coordinates, pressure and width values in the receiver.
- Avoid adding cloud services, analytics or account requirements to the MVP.
- Add or update protocol documentation when changing frame structure.
- Do not commit signing keys, keystores, local machine paths, `local.properties`, build outputs or credentials.

## Pull requests

Keep changes focused, describe how they were tested, and clearly state when a change still needs physical Android/Windows Bluetooth hardware testing.
