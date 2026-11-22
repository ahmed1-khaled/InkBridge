# InkBridge wire protocol v2

Transport: authenticated TCP over the same local Wi-Fi/LAN. Windows listens on TCP port **45832**. The stream is UTF-8 text; each frame is one compact JSON object followed by a newline. A receiver must not assume one socket read equals one frame; a line may arrive in partial or multiple reads.

## Handshake

The first client frame must contain the 6-digit code shown in the Windows companion:

~~~json
{"type":"hello","token":"123456","protocol":2}
~~~

Windows responds with exactly one of:

~~~json
{"type":"welcome","protocol":2}
{"type":"denied"}
~~~

Only after a successful welcome does the client send page and ink frames. The code is a local pairing gate; it does not encrypt the stream.

## Frames

| Type | Required fields | Meaning |
|---|---|---|
| paper | aspect, image? | Replace current page background; image is a Base64 JPEG of the displayed template/PDF page. |
| begin | mode, x, y, p, color, width | Start a stroke. mode is draw or erase. |
| point | x, y, p | Append a point to the active stroke. |
| end | — | Finish the current stroke. |
| undo | — | Undo the last drawing/erase action. |
| redo | — | Redo the latest undone action. |
| clear | — | Remove all visible ink. |

Fields:

- aspect: page width divided by page height.
- image: Base64-encoded JPEG page background. Omitted when a plain white page is used.
- x, y: normalized floating-point coordinates from 0.0 to 1.0 relative to the page, not the whole window.
- p: normalized pressure, clamped to 0.15–1.0.
- color: Android ARGB string, e.g. #FF236FEB or semi-transparent marker #66236FEB.
- width: brush width divided by the smaller dimension of the page writing area.
- mode: draw or erase.

A single active stroke is allowed. begin implicitly finishes an unfinished stroke on the receiver. Unknown frame types are ignored. Receivers clamp coordinates, width and pressure before rendering.

## Sample session

~~~json
{"type":"paper","aspect":0.75}
{"type":"begin","mode":"draw","x":0.12,"y":0.28,"p":0.65,"color":"#FF236FEB","width":0.008}
{"type":"point","x":0.16,"y":0.31,"p":0.72}
{"type":"point","x":0.21,"y":0.35,"p":0.81}
{"type":"end"}
{"type":"undo"}
{"type":"redo"}
~~~

## Connection behavior

- Windows uses TcpListener(IPAddress.Any, 45832) and accepts one Android client at a time.
- Android connects using the laptop IPv4 address displayed by the Windows app.
- Both devices must share a reachable local Wi-Fi network or hotspot. Guest network isolation and some managed campus networks may block local traffic.
- After reconnecting, Android re-sends the current page and visible strokes so the Windows preview is synchronized.
- Internet is not required for drawing; it is used only for app update checks/downloads.
