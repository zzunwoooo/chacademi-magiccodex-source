#!/usr/bin/env python3
"""낚시 물고기 그림 넣기 (2026-10-20).

    python tools/fishing/install_fish_art.py <그림 폴더>

<그림 폴더> 안의 fish_001.png ~ fish_200.png (투명 PNG, 크기 상관없음) 를
  - 낚시 패널·도감용: magic-codex-fabric/.../textures/gui/fishing/fish/<id>.png (256x256)
으로 넣는다. 그림이 있는 물고기만 바뀌고, 없는 물고기는 계속 실루엣으로 보인다.
아이템 아이콘은 ItemsAdder 아이템(item:fish_1~200)이 맡는다 (2026-10-20 owner). 넣은 뒤 tools/fishing/fish_bounds.py 도 다시 실행.
"""
import os, re, sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "magic-codex-fabric", "src", "main", "resources", "assets", "magiccodex")

def fit(img, size):
    """Square, transparent, at most `size`; GPT's 256x256 game assets already carry their margins, so no cropping."""
    img = img.convert("RGBA")
    if img.width != img.height:
        side = max(img.width, img.height)
        sq = Image.new("RGBA", (side, side), (0, 0, 0, 0)); sq.paste(img, ((side - img.width) // 2, (side - img.height) // 2), img); img = sq
    return img if img.width == size else img.resize((size, size), Image.LANCZOS)

def main(folder):
    n = 0
    for name in sorted(os.listdir(folder)):
        m = re.fullmatch(r"(fish_\d{3})\.png", name)
        if not m: continue
        fid = m.group(1)
        img = Image.open(os.path.join(folder, name))
        sub = "textures/gui/fishing/fish"
        os.makedirs(os.path.join(ROOT, sub), exist_ok=True)
        fit(img, 256).save(os.path.join(ROOT, sub, fid + ".png"), optimize=True)
        n += 1
    print(f"{n}종 넣음. tools/fishing/fish_bounds.py 로 실루엣 범위를 다시 만드세요.")

if __name__ == "__main__":
    if len(sys.argv) != 2: sys.exit(__doc__)
    main(sys.argv[1])
