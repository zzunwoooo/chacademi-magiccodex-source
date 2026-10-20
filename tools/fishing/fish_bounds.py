#!/usr/bin/env python3
"""Regenerates magic-codex-fabric/.../client/FishArtBounds.java from the 256px fish pictures (visible alpha bounds)."""
import os
from PIL import Image
HERE = os.path.dirname(__file__)
PICS = os.path.join(HERE, "..", "..", "magic-codex-fabric/src/main/resources/assets/magiccodex/textures/gui/fishing/fish")
OUT = os.path.join(HERE, "..", "..", "magic-codex-fabric/src/main/java/school/magiccodex/client/FishArtBounds.java")
rows = []
for i in range(1, 201):
    a = Image.open(os.path.join(PICS, f"fish_{i:03d}.png")).getchannel("A").point(lambda v: 255 if v > 24 else 0)
    rows.append(a.getbbox() or (0, 0, 256, 256))
text = open(OUT, encoding="utf-8").read()
head, rest = text.split("static final int[][] BOUNDS = {", 1)
tail = rest.split("};", 1)[1]
body = ",\n            ".join("{%d, %d, %d, %d}" % b for b in rows)
open(OUT, "w", encoding="utf-8").write(head + "static final int[][] BOUNDS = {\n            " + body + "\n    };" + tail)
print("ok", len(rows))
