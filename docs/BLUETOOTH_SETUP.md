# Bluetooth setup (legacy)

Bluetooth Classic was used by earlier InkBridge alpha releases. The current InkBridge 0.3 Wi-Fi build does not use Bluetooth for ink transfer because the Windows RFCOMM service was not reliable across different Windows Bluetooth adapters and drivers.

Do not create an Incoming COM port or pair the tablet in Bluetooth settings for the current version.

Use [Wi-Fi connection setup](WIFI_SETUP.md) instead:
- Connect the Windows laptop and Android tablet to the same Wi-Fi network or hotspot.
- Read the laptop IPv4 address and six-digit code from the Windows InkBridge app.
- Enter both in Android using Connect Wi-Fi.

Bluetooth hardware may remain enabled on both devices, but it is not involved in the current data path.
