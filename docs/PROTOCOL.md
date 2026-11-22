# InkBridge wire protocol v1

Transport: Bluetooth Classic RFCOMM/SPP, UTF-8 text stream. Each frame is one compact JSON object followed by `\n`. A receiver must not assume one serial read equals one frame; chunks may contain partial or multiple frames.

Every frame has `type`.

| Type | Required fields | Meaning |
|---|---|---|
| `begin` | `mode`, `x`, `y`, `p`, `color`, `width` | Start a stroke. `mode` is `draw` or `erase`. |
| `point` | `x`, `y`, `p` | Append a point to the active stroke. |
| `end` | — | Finish the current stroke. |
| `undo` | — | Undo the last drawing/erase action. |
| `redo` | — | Redo the latest undone action. |
| `clear` | — | Remove all visible ink. |

Fields:

- `x`, `y`: normalized floating-point coordinates from `0.0` to `1.0`.
- `p`: normalized pressure, clamped to `0.15`–`1.0`.
- `color`: Android ARGB string, e.g. `#FF236FEB` or semi-transparent marker `#66236FEB`.
- `width`: brush width divided by the smaller dimension of the tablet writing area. The receiver scales it to its virtual desktop.
- `mode`: `draw` or `erase`.

A single active stroke is allowed. `begin` implicitly finishes an unfinished stroke on the receiver. Unknown frame types are ignored. The receiver clamps coordinates, width and pressure before rendering.

## Sample session

```jsonl
{"type":"begin","mode":"draw","x":0.12,"y":0.28,"p":0.65,"color":"#FF236FEB","width":0.008}
{"type":"point","x":0.16,"y":0.31,"p":0.72}
{"type":"point","x":0.21,"y":0.35,"p":0.81}
{"type":"end"}
{"type":"undo"}
{"type":"redo"}
```

## Transport parameters

- Standard SPP UUID: `00001101-0000-1000-8000-00805F9B34FB`.
- Windows serial settings: 115200 baud, 8 data bits, no parity, one stop bit, no handshake. RFCOMM is a byte stream; the baud value is only a virtual COM configuration parameter and does not set the over-the-air Bluetooth radio rate.
- Frames are small and intended for interactive pen movement. Compression is not required for the MVP.
