# Bluetooth Classic connection

InkBridge uses **Bluetooth Classic RFCOMM/SPP**. Windows starts a service listener inside the desktop app; Android connects to it directly. There is no serial COM port dropdown or manual incoming-port configuration in this version.

## One-time pairing

### Windows 10/11

1. Turn Bluetooth on in Windows Settings.
2. Open **Settings → Bluetooth & devices → Add device → Bluetooth**.
3. Pair the Android phone/tablet. Accept the pairing confirmation on both screens.
4. Start InkBridge Companion. It automatically starts listening for the InkBridge Bluetooth service.
5. The status should show **WAITING FOR TABLET**.

### Android

1. Turn Bluetooth on and pair with the Windows laptop in Android Bluetooth Settings.
2. Open InkBridge and allow **Nearby devices** permission.
3. Tap **Connect**. If only one known paired device is available, the app connects to it automatically. If several devices are paired, choose the laptop.
4. The selected laptop is remembered. Long-press **Connect** to pick a different laptop later.
5. When the Windows app shows **TABLET CONNECTED**, write on the Android canvas.

## Connection model

- Windows: 32feet.NET BluetoothListener, standard SPP UUID.
- Android: secure RFCOMM client socket using UUID 00001101-0000-1000-8000-00805F9B34FB.
- The drawing stream stays on the local paired Bluetooth connection.
- Internet is not needed for writing; it is used only for checking/downloading app updates.

## Troubleshooting

- **Windows says Bluetooth not ready:** make sure Bluetooth is on, restart InkBridge Companion, and verify the adapter/driver supports Bluetooth Classic RFCOMM.
- **Android does not show the laptop:** pair the devices in the system Bluetooth settings first, grant Nearby devices permission, and tap Connect again.
- **Connection is refused or times out:** close and reopen InkBridge Companion, confirm it says **WAITING FOR TABLET**, then reconnect from Android. Remove and re-pair the device in system settings if necessary.
- **No ink arrives:** ensure the Windows badge says **TABLET CONNECTED** and the overlay is not hidden. Try a short test stroke.
- **Finger does not draw:** make sure the toolbar says **Touch: On**. For an active stylus, choose Pen only if you want palm rejection.
- **Pressure stays constant:** the digitizer/stylus may not report pressure; the app uses a safe fallback.
- **Windows lacks Classic Bluetooth support:** this version requires RFCOMM. A machine/adapter limited to BLE will need a different transport implementation.

## Privacy

Ink is transferred directly between already-paired devices. There is no cloud relay and no account login is needed for local Bluetooth connection.
