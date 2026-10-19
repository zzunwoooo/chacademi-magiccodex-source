#!/usr/bin/env python3
"""낚시 물고기 그림 넣기 (2026-10-20).

    python tools/fishing/install_fish_art.py <그림 폴더>

<그림 폴더> 안의 fish_001.png ~ fish_200.png (투명 PNG, 크기 상관없음) 를
  - 낚시 패널·도감용: magic-codex-fabric/.../textures/gui/fishing/fish/<id>.png (512x512)
  - 아이템 아이콘용:   magic-codex-fabric/.../textures/item/fishing/fish/<id>.png (128x128) + models / items json
으로 넣는다. 그림이 있는 물고기만 바뀌고, 없는 물고기는 계속 실루엣으로 보인다.
아이템 아이콘을 쓰려면 서버 fishing.yml 의 settings.fish-item-models 를 true 로 바꾸고 /낚시관리 리로드.
"""
import json, os, re, sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "magic-codex-fabric", "src", "main", "resources", "assets", "magiccodex")

def fit(img, size):
    img = img.convert("RGBA")
    box = img.getbbox() or (0, 0, img.width, img.height)
    img = img.crop(box)
    scale = size * .92 / max(img.width, img.height)
    img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, ((size - img.width) // 2, (size - img.height) // 2), img)
    return out

def main(folder):
    n = 0
    for name in sorted(os.listdir(folder)):
        m = re.fullmatch(r"(fish_\d{3})\.png", name)
        if not m: continue
        fid = m.group(1)
        img = Image.open(os.path.join(folder, name))
        for sub, size in (("textures/gui/fishing/fish", 512), ("textures/item/fishing/fish", 128)):
            os.makedirs(os.path.join(ROOT, sub), exist_ok=True)
            fit(img, size).save(os.path.join(ROOT, sub, fid + ".png"), optimize=True)
        os.makedirs(os.path.join(ROOT, "models/item/fishing/fish"), exist_ok=True)
        os.makedirs(os.path.join(ROOT, "items/fishing/fish"), exist_ok=True)
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "magiccodex:item/fishing/fish/" + fid}},
                  open(os.path.join(ROOT, "models/item/fishing/fish", fid + ".json"), "w"))
        json.dump({"model": {"type": "minecraft:model", "model": "magiccodex:item/fishing/fish/" + fid}},
                  open(os.path.join(ROOT, "items/fishing/fish", fid + ".json"), "w"))
        n += 1
    print(f"{n}종 넣음. 서버 fishing.yml settings.fish-item-models: true 로 바꾼 뒤 /낚시관리 리로드")

if __name__ == "__main__":
    if len(sys.argv) != 2: sys.exit(__doc__)
    main(sys.argv[1])
