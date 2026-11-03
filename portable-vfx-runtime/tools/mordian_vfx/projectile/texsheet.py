import sys, glob, os
from PIL import Image, ImageDraw
d = sys.argv[1]; fs = sorted(glob.glob(d + "/*.png")); T = 256
im = Image.new("RGB", (T * 4, T * ((len(fs) + 3) // 4)), (40, 40, 48)); dr = ImageDraw.Draw(im)
for i, f in enumerate(fs):
    a = Image.open(f).convert("RGBA"); a.thumbnail((T - 8, T - 20))
    bg = Image.new("RGBA", a.size, (90, 90, 100, 255)); 
    for yy in range(0, a.size[1], 16):
        for xx in range(0, a.size[0], 16):
            if (xx // 16 + yy // 16) % 2: bg.paste((120, 120, 130, 255), (xx, yy, xx + 16, yy + 16))
    bg.alpha_composite(a); x, y = (i % 4) * T, (i // 4) * T
    im.paste(bg.convert("RGB"), (x + 4, y + 16)); dr.text((x + 4, y + 2), os.path.basename(f), fill=(255, 255, 255))
im.save(sys.argv[2])
