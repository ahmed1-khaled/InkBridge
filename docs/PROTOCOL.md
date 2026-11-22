# InkBridge wire protocol v1

Transport: Bluetooth Classic RFCOMM/SPP, UTF-8 text stream. Each frame is one compact JSON object followed by a newline. A receiver must not assume one socket read equals one frame; chunks may contain partial or multiple frames.

Every frame has type.

| Type | Required fields | Meaning |
|---|---|---|
| begin | mode, x, y, p, color, width | Start a stroke. mode is draw or erase. |
| point | x, y, p | Append a point to the active stroke. |
| end | — | Finish the current stroke. |
| undo | — | Undo the last drawing/erase action. |
| redo | — | Redo the latest undone action. |
| clear | — | Remove all visible ink. |

Fields:

- x, y: normalized floating-point coordinates from 0.0 to 1.0.
- p: normalized pressure, clamped to 0.15–1.0.
- color: Android ARGB string, e.g. #FF236FEB or semi-transparent marker #66236FEB.
- width: brush width divided by the smaller dimension of the page writing area. The receiver scales it to the Windows virtual desktop.
- mode: draw or erase.

A single active stroke is allowed. begin implicitly finishes an unfinished stroke on the receiver. Unknown frame types are ignored. The receiver clamps coordinates, width and pressure before rendering.

## Sample session

~~~json
{"type":"begin","mode":"draw","x":0.12,"y":0.28,"p":0.65,"color":"#FF236FEB","width":0.008}
{"type":"point","x":0.16,"y":0.31,"p":0.72}
{"type":"point","x":0.21,"y":0.35,"p":0.81}
{"type":"end"}
{"type":"undo"}
{"type":"redo"}
~~~

## Transport parameters

- Standard SPP UUID: 00001101-0000-1000-8000-00805F9B34FB.
- Windows uses the 32feet.NET BluetoothListener to advertise the service and accepts one client stream at a time.
- Android connects to an already-paired laptop using Android's secure RFCOMM socket API.
- Both directions use newline-delimited UTF-8 JSON. Desktop undo/redo/clear commands use the same stream back to Android.
