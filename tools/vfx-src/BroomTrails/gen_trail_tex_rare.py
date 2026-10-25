# 희귀 등급 궤적 텍스처: 나뭇잎/꽃잎 4프레임 뒤집힘 플립북(색 포함, alpha 블렌드용), 성운 구름, 물결 줄기.
import sys, os, math, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
SS = 4; F = 64
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot

def leaf_outline(halfw, n=120, tip=1.0):
    """midrib along +y (stem at bottom). halfw(t) for t in 0..1 -> polygon in unit coords (-1..1)."""
    L, R = [], []
    for i in range(n + 1):
        t = i / n; w = halfw(t); y = -0.86 + 1.72 * t * tip
        L.append((-w, y)); R.append((w, y))
    return R + L[::-1]
def draw_leaf(poly, base, vein, edge, scale_x, shade, stem=True, veins=4, seed=0):
    S = F * SS; im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    P = lambda x, y: ((0.5 + 0.47 * x * scale_x) * S, (0.5 - 0.47 * y) * S)
    col = tuple(int(c * shade) for c in base)
    d.polygon([P(x, y) for x, y in poly], fill=col + (255,), outline=tuple(int(c * shade) for c in edge) + (255,))
    mask = im.split()[3]
    if scale_x > 0.25:
        vl = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d2 = ImageDraw.Draw(vl)
        vc = tuple(int(c * shade) for c in vein) + (255,)
        d2.line([P(0, -0.86), P(0, 0.8)], fill=vc, width=max(1, int(SS * 1.2)))
        for k in range(1, veins + 1):
            y0 = -0.75 + 1.4 * k / (veins + 1)
            for sgn in (-1, 1): d2.line([P(0, y0), P(sgn * 0.42, y0 + 0.28)], fill=vc, width=max(1, int(SS * 0.8)))
        va = Image.fromarray((np.asarray(vl.split()[3], float) * np.asarray(mask, float) / 255 * 0.9).astype(np.uint8))
        vl.putalpha(va); im.alpha_composite(vl); d = ImageDraw.Draw(im)
    if stem: d.line([P(0, -0.86), P(0.02, -1.0)], fill=tuple(int(c * shade) for c in vein) + (255,), width=int(SS * 1.6))
    im = im.resize((F, F), Image.LANCZOS)
    a = np.asarray(im, float) / 255
    n = smooth_noise(F, F, 12, 12, 3, seed)[..., None]
    a[..., :3] = np.clip(a[..., :3] * (0.85 + 0.3 * n), 0, 1)
    return a
def sheet(name, poly, base, vein, edge, back, **kw):
    frames = []
    for k, (sx, sh) in enumerate([(1.0, 1.0), (0.55, 0.8), (0.14, 0.6), (0.55, 0.72)]):
        b = base if k < 2 else back
        frames.append(draw_leaf(poly, b, vein, edge, sx, sh, seed=k, **kw))
    img = np.concatenate(frames, axis=1)
    Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))

# oak: lobed leaf (5 rounded lobes)
oak = leaf_outline(lambda t: (math.sin(math.pi * min(1, t * 1.05)) ** 0.7) * (0.38 + 0.2 * abs(math.sin(t * math.pi * 4.5))) + 0.03)
sheet("T_Leaf_Oak.png", oak, (118, 150, 52), (70, 92, 30), (60, 80, 26), (150, 140, 60))
# cherry blossom petal: rounded with a notch at the tip
cher = leaf_outline(lambda t: (math.sin(math.pi * t * 0.92) ** 0.55) * 0.62 * (1 - 0.55 * ss(0.86, 1.0, np.array(t)).item() * (1 - abs(math.sin(t * 40)) * 0)) + 0.02)
def petal_poly():
    pts = []
    for i in range(240):
        a = 2 * math.pi * i / 240; sn = math.sin(a)
        r = 0.78 * (1 - 0.32 * math.exp(-((a - math.pi / 2) / 0.13) ** 2))
        x = r * math.cos(a) * (1 + 0.75 * min(0, sn)); y = r * sn * (1.12 if sn < 0 else 1.0) + 0.05
        pts.append((x, y))
    return pts
sheet("T_Leaf_Cherry.png", petal_poly(), (255, 190, 210), (250, 225, 235), (235, 150, 180), (245, 170, 195), stem=False, veins=0)
# birch: ovate, serrated, golden green
birch = leaf_outline(lambda t: (math.sin(math.pi * t) ** 0.6) * (0.55 - 0.25 * t) * (1 + 0.08 * math.sin(t * 60)) + 0.02)
sheet("T_Leaf_Birch.png", birch, (196, 196, 70), (140, 140, 40), (120, 120, 30), (220, 180, 70))
# mahogany: elongated ellipse with drip tip, bronze red
maho = leaf_outline(lambda t: (math.sin(math.pi * t) ** 0.9) * 0.4 * (1 - 0.6 * ss(0.8, 1.0, np.array(t)).item()) + 0.015)
sheet("T_Leaf_Mahogany.png", maho, (150, 64, 40), (96, 36, 22), (90, 34, 20), (120, 80, 40))
# bamboo: long narrow lance
bamb = leaf_outline(lambda t: (math.sin(math.pi * t) ** 0.5) * 0.17 * (1 - 0.7 * t) + 0.012)
sheet("T_Leaf_Bamboo.png", bamb, (130, 186, 70), (90, 140, 44), (80, 128, 40), (160, 196, 96), veins=0)

# nebula cloud 128: soft cloudy blob (white; colour from tint/colorOverLifetime), wispy edges
S = 128; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy)
n = smooth_noise(S, S, 30, 30, 4, 5); n2 = smooth_noise(S, S, 14, 14, 3, 6)
a = np.clip((1 - ss(0.25, 1.0, r + (n - 0.5) * 0.7)) * (0.35 + 0.8 * n2), 0, 1) ** 1.3
Image.fromarray((np.dstack([np.ones((S, S, 3)), a]) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "T_Nebula.png"))
# wave droplet streak 128x32: bright crest line with soft spray below (white)
W, H = 128, 32; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
crest = np.exp(-((y + 0.1) / (0.18 * (x ** 0.6) + 0.03)) ** 2) * ss(0.0, 0.3, x) * (1 - ss(0.92, 1, x))
foam = smooth_noise(W, H, 16, 6, 3, 9) * np.exp(-((y - 0.3) / 0.45) ** 2) * 0.5 * x
a = np.clip(crest + foam, 0, 1)
Image.fromarray((np.dstack([np.ones((H, W, 3)), a]) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "T_WaveStreak.png"))
print("ok")
# ---- T_Ripple 256x64: two crossing sine crest lines (braided water), soft glow, foam specks, ends fade
W, H = 256, 64; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
env = ss(0.0, 0.18, x) * (1 - ss(0.75, 1.0, x)); amp = 0.55 * (0.35 + 0.65 * x)
a = np.zeros((H, W))
for ph, wgt in ((0, 1.0), (np.pi, 0.75)):
    yc = amp * np.sin(2 * np.pi * 2.0 * x + ph)
    d = np.abs(y - yc)
    a += wgt * (np.exp(-(d / 0.07) ** 2) + 0.35 * np.exp(-(d / 0.25) ** 2))
foam = (smooth_noise(W, H, 6, 6, 2, 31) > 0.72) * 0.6 * np.exp(-(y / 0.8) ** 2)
a = np.clip((a + foam) * env, 0, 1)
hot = np.clip(a ** 2, 0, 1)[..., None]
Image.fromarray((np.dstack([np.array([0.85, 0.95, 1.0]) * (1 - hot) + hot, a]) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "T_Ripple.png"))
