import sys, glob, os
from PIL import Image, ImageDraw
fs = sorted(glob.glob(sys.argv[1] + "/*.png")); tiles = []
for f in fs:
    im = Image.open(f).convert("RGBA"); im.thumbnail((360, 360))
    bg = Image.new("RGBA", (380, 400), (95, 90, 75, 255)); 
    for y in range(0, 400, 20):
        for x in range(0, 380, 20):
            if (x//20 + y//20) % 2: bg.paste((120, 115, 100, 255), (x, y, x+20, y+20))
    bg.alpha_composite(im, (10, 30)); ImageDraw.Draw(bg).text((5, 5), os.path.basename(f) + " %dx%d" % Image.open(f).size, fill=(255,255,255,255)); tiles.append(bg)
n = 5; rows = (len(tiles) + n - 1) // n; out = Image.new("RGB", (380*n, 400*rows))
for i, t in enumerate(tiles): out.paste(t.convert("RGB"), ((i % n) * 380, (i // n) * 400))
out.save(sys.argv[2])
