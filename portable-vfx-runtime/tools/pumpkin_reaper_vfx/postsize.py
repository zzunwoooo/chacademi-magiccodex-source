# gen_pumpkin.py 다음에 실행: 팩 텍스처 예산(팩당 RGBA ≤ 4 MiB)에 맞춰 큰 시트를 줄인다. usage: python postsize.py <texdir>
import sys
from PIL import Image
d = sys.argv[1] if len(sys.argv) > 1 else "tex"
for f, s in [("T_PumpkinFlame_Sheet.png", (256, 256)), ("T_SoulFlame_Sheet.png", (256, 256)), ("T_Mist_Sheet.png", (256, 256)),
             ("T_VioletFlame_Sheet.png", (256, 256)), ("T_Sigil.png", (256, 256)), ("T_ScytheSlash_Sheet.png", (384, 288))]:
    Image.open(f"{d}/{f}").resize(s, Image.LANCZOS).save(f"{d}/{f}")
print("ok")
