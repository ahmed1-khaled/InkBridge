#!/usr/bin/env python3
"""Small protocol fixture check; this is not a Bluetooth hardware integration test."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "protocol" / "sample-frames.jsonl"
frames = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
allowed = {"begin", "point", "end", "undo", "redo", "clear"}
active = False
for index, frame in enumerate(frames, 1):
    kind = frame.get("type")
    assert kind in allowed, f"frame {index}: unknown type {kind!r}"
    if kind == "begin":
        assert not active, f"frame {index}: nested begin"
        assert frame.get("mode") in {"draw", "erase"}
        assert 0 <= frame["x"] <= 1 and 0 <= frame["y"] <= 1
        assert 0.15 <= frame["p"] <= 1
        assert 0.0005 <= frame["width"] <= 0.2
        active = True
    elif kind == "point":
        assert active, f"frame {index}: point without begin"
        assert 0 <= frame["x"] <= 1 and 0 <= frame["y"] <= 1
        assert 0.15 <= frame["p"] <= 1
    elif kind == "end":
        assert active, f"frame {index}: end without begin"
        active = False
    else:
        assert not active, f"frame {index}: command inside active stroke"
assert not active, "sample ended with an unfinished stroke"
print(f"OK: validated {len(frames)} protocol-v1 frames")
