# Bluetooth Classic setup

InkBridge alpha uses **Bluetooth Classic RFCOMM with the Serial Port Profile (SPP)**. It does not use Bluetooth Low Energy (BLE).

## Windows 10/11

1. Enable Bluetooth in Windows Settings.
2. Pair the Android tablet with the laptop using **Settings → Bluetooth & devices → Add device → Bluetooth**.
3. Open the legacy Bluetooth control-panel page by running:

   ```powershell
   control.exe bthprops.cpl
   ```

4. Open the **COM Ports** tab. Select **Add…**, then choose **Incoming (device initiates the connection)** if the option is available.
5. Record the assigned incoming port (for example, `COM7`). Do not select an outgoing port for this receiver.
6. Start InkBridge Companion, refresh the port list, select the incoming COM port and click **Open port**.

The Windows COM-port option is driver-dependent. If only outgoing ports are available, or Windows does not expose Serial Port Profile for the adapter, this build cannot receive the tablet stream over SPP on that machine.

## Android tablet

1. Pair with the Windows laptop in the Android system Bluetooth settings first.
2. Open InkBridge and allow **Nearby devices** access.
3. Tap **Connect laptop** and select the paired Windows laptop.
4. Keep the Windows incoming COM port open in InkBridge Companion before connecting from Android.

The app uses the standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB` and Android's secure RFCOMM socket API. Pairing is performed by Android/Windows system UI; InkBridge only connects to a bonded device.

## Troubleshooting

- **No laptop appears in Android:** pair both devices in system settings first, grant Nearby devices permission, then reopen the paired-device list.
- **Connection fails immediately:** verify the Windows app has opened the *incoming* COM port, not an outgoing port. Close other software that might have the same COM port open.
- **Connected but no ink:** show the overlay, check the Android status line, and test with a simple pen stroke. The Windows app changes to “TABLET CONNECTED” when the first valid frame arrives.
- **Windows has no Incoming option:** check the Bluetooth adapter's driver and SPP support. This is an environment limitation, not something the APK can enable by itself.
- **Pen appears as finger:** use an active stylus supported by the tablet. The app has a Finger toggle for devices where touch drawing is desired.
- **Pressure stays constant:** the tablet digitizer/stylus may not report pressure. InkBridge uses a safe fallback pressure so drawing still works.

## Bluetooth direction and port roles

The tablet is the RFCOMM client. Windows exposes an incoming serial endpoint; the desktop app opens that COM port and reads its byte stream. This design avoids requiring a Windows Bluetooth server implementation in the first MVP, but it also means the COM-port setup must be available on the Windows adapter/driver.
