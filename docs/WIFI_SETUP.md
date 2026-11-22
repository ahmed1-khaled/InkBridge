# Wi-Fi connection setup

InkBridge 0.3 uses authenticated TCP over the local network. Bluetooth pairing and incoming COM ports are not used.

## Before connecting

1. Connect the Windows laptop and Android tablet to the **same Wi-Fi network**.
2. If there is no router, enable Windows Mobile Hotspot and connect the tablet to that hotspot, or use another trusted local hotspot.
3. Avoid guest Wi-Fi networks. Some guest networks enable client isolation and prevent devices from talking to each other.

## Windows

1. Open `InkBridge.Companion.exe`.
2. The Wi-Fi card lists local IPv4 addresses, prioritizing Wi-Fi.
3. Read the 6-digit connection code in the same card.
4. Leave InkBridge open. It listens on TCP port `45832`.
5. If Windows Defender Firewall asks, allow InkBridge on **Private networks**. Do not enable Public network access unless you understand the risk.

The pairing code is saved in the current Windows user's local app data, so it stays the same after restarting InkBridge.

## Android

1. Open InkBridge on the tablet.
2. Tap **Connect Wi-Fi**.
3. Enter one of the laptop IPv4 addresses exactly as shown on Windows.
4. Enter the 6-digit code shown on Windows.
5. Tap **Connect**. InkBridge remembers the address and code for later reconnects.

The first authenticated connection receives the current paper/PDF page and visible strokes, then all new pen points stream live.

## What is mirrored

The Windows **Live Tablet Canvas** panel displays the paper template or imported PDF page plus pen/marker strokes. It mirrors the writing page, not the tablet's entire Android screen or status bar.

The transparent Windows desktop overlay is optional. Use **Show desktop overlay** when you want ink over PowerPoint, a PDF reader or another PC application. The in-app preview keeps receiving ink even when the overlay is hidden.

## Troubleshooting

- **No IP shown on Windows:** connect the laptop to Wi-Fi or Ethernet, then click **Restart Wi-Fi service**.
- **Connection times out:** verify both devices are on the same Wi-Fi/hotspot, choose the laptop's Wi-Fi IPv4 address, keep the Windows app open and check Windows Firewall.
- **Connection refused:** click **Restart Wi-Fi service** in the Windows app and try again.
- **Invalid code:** type the current 6-digit code displayed on Windows. The code is local to that Windows user profile.
- **It works on home Wi-Fi but not campus/guest Wi-Fi:** the network may isolate wireless clients. Use a trusted private hotspot instead.
- **Tablet connected but no strokes:** leave the Android app in the foreground, reconnect once, and make sure the Windows Live Tablet Canvas panel is visible.
- **The laptop changed networks:** its IP address may have changed. Re-enter the new address in Android; the saved code does not normally change.
- **Windows Firewall blocked it:** in Windows Security → Firewall & network protection → Allow an app through firewall, allow InkBridge on Private networks.

## Privacy and security

Ink data is sent directly to the laptop over the local network. No cloud relay is used. A 6-digit pairing code is checked during the TCP handshake before ink frames are accepted. Use a trusted private network; the stream is not encrypted end-to-end, so do not use an untrusted public Wi-Fi network for sensitive material.
