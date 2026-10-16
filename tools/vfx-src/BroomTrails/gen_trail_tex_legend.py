# 전설 등급 궤적 텍스처: 하트(빛), 검은 하트(알파), 컨페티, 홀로 나비, 불꽃, 깃털
import sys, os, math, numpy as np
from PIL import Image, ImageDraw
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
def save(arr, name): Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))
S = 128; yy, xx = np.mgrid[1:-1:S*1j, -1:1:S*1j]
# heart SDF-ish: (x^2 + y^2 - 1)^3 - x^2 y^3 <= 0  (scaled)
def heart_field(x, y): x = x * 1.25; y = y * 1.25 + 0.15; return (x * x + y * y - 1) ** 3 - x * x * y ** 3
h = heart_field(xx, yy); inside = (h <= 0).astype(float)
from scipy.ndimage import distance_transform_edt, gaussian_filter
din = distance_transform_edt(inside) / S * 2; dout = distance_transform_edt(1 - inside) / S * 2
# glowing heart (white): bright rim + soft fill + outer glow
a = np.clip(np.exp(-(din / 0.06) ** 2) * inside + 0.45 * inside + np.exp(-(dout / 0.12) ** 2) * 0.6 * (1 - inside), 0, 1)
hot = np.clip(np.exp(-(din / 0.04) ** 2) * inside + 0.3 * inside, 0, 1)[..., None]
save(np.dstack([np.array([1, 0.9, 0.95]) * (1 - hot) + hot, a]), "T_HeartGlow.png")
# dark heart (alpha): near-black glossy body, purple rim
body = inside; rim = np.exp(-(din / 0.07) ** 2) * inside
col = np.dstack([0.07 + 0.5 * rim, 0.03 + 0.15 * rim, 0.1 + 0.7 * rim])
gl = np.exp(-(((xx + 0.35) ** 2 + (yy - 0.35) ** 2) / 0.02)) * inside * 0.6
col = col + gl[..., None]
a = np.clip(body + np.exp(-(dout / 0.06) ** 2) * 0.5 * (1 - inside), 0, 1)
save(np.dstack([col, a]), "T_HeartDark.png")
# confetti 4-frame tumble (white rectangles at various foreshortening), 32 each
F = 32; frames = []
for k, w in enumerate([1.0, 0.6, 0.18, 0.6]):
    im = Image.new("RGBA", (F * 4, F * 4), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    cx = F * 2; hw = F * 1.5 * w; hh = F * 0.75
    d.polygon([(cx - hw, cx - hh), (cx + hw, cx - hh - F * 0.2), (cx + hw, cx + hh - F * 0.2), (cx - hw, cx + hh)], fill=(255, 255, 255, 255))
    a = np.asarray(im.resize((F, F), Image.LANCZOS), float) / 255
    a[..., :3] *= (1.0 if k < 2 else 0.75); frames.append(a)
save(np.concatenate(frames, 1), "T_Confetti.png")
# holo butterfly 4-frame wing flap, 128 each, white with scanlines + bright edges
B = 128; frames = []
by, bx = np.mgrid[1:-1:B*1j, -1:1:B*1j]
for k, open_ in enumerate([1.0, 0.65, 0.25, 0.65]):
    x = np.abs(bx) / max(open_, 0.12); y = by
    up = ((x - 0.48) / 0.46) ** 2 + ((y - 0.28) / 0.5) ** 2 <= 1
    lo = ((x - 0.36) / 0.34) ** 2 + ((y + 0.38) / 0.36) ** 2 <= 1
    wing = (up | lo) & (np.abs(bx) > 0.03)
    m = wing.astype(float); dd = distance_transform_edt(m) / B * 2
    edge = np.exp(-(dd / 0.035) ** 2) * m
    scan = 0.55 + 0.45 * ((np.arange(B)[:, None] // 3) % 2)
    veins = np.exp(-((np.abs(np.arctan2(y - 0.0, x + 0.01)) % 0.45 - 0.22) / 0.03) ** 2) * m * 0.3
    body = np.exp(-(bx / 0.035) ** 2) * (np.abs(by) < 0.55)
    a = np.clip(m * 0.35 * scan + edge + veins + body, 0, 1)
    hot = np.clip(edge + body, 0, 1)[..., None]
    frames.append(np.dstack([np.array([1, 0.85, 0.85]) * (1 - hot) + hot, a]))
save(np.concatenate(frames, 1), "T_Butterfly.png")
# flame 4-frame flicker, 128 each: tongue shape rising up (+V), white
frames = []
fy, fx = np.mgrid[1:-1:S*1j, -1:1:S*1j]
for k in range(4):
    n = smooth_noise(S, S, 22, 30, 4, 40 + k)
    t = (fy + 1) / 2                                     # 0 bottom .. 1 top
    width = 0.55 * (1 - t) ** 0.8 * (0.8 + 0.4 * n) + 0.02
    sway = 0.18 * np.sin(t * 5 + k * 1.6) * t
    d = np.abs(fx - sway) / width
    core = np.clip(1 - d, 0, 1) ** 1.2 * (1 - ss(0.75, 1.0, t + (n - 0.5) * 0.4)) * ss(-0.05, 0.12, t)
    a = np.clip(core * 1.4, 0, 1); hot = np.clip(core ** 2 * 1.6, 0, 1)[..., None]
    frames.append(np.dstack([np.array([0.95, 0.9, 0.85]) * (1 - hot) + hot, a]))
save(np.concatenate(frames, 1), "T_Flame.png")
# feather 4-frame tumble, 64 each, white feather with grey shading, colored (alpha blend)
frames = []
for k, (sx, sh) in enumerate([(1.0, 1.0), (0.55, 0.85), (0.16, 0.7), (0.55, 0.8)]):
    M = 256; im = Image.new("RGBA", (M, M), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    P = lambda x, y: ((0.5 + 0.45 * x * sx) * M, (0.5 - 0.45 * y) * M)
    pts = []
    for i in range(41):
        t = i / 40; w = 0.42 * math.sin(math.pi * min(1, t * 1.08)) ** 0.7 * (1 - 0.25 * t) + 0.02; pts.append((w, -0.8 + 1.75 * t))
    poly = [P(x, y) for x, y in pts] + [P(-x * 0.85, y) for x, y in reversed(pts)]
    c = int(250 * sh); d.polygon(poly, fill=(c, c, int(c * 1.0), 255))
    mask = im.split()[3]; bl = Image.new("RGBA", (M, M), (0, 0, 0, 0)); db = ImageDraw.Draw(bl)
    for i in range(1, 12):                               # barb lines, clipped to the vane
        y0 = -0.6 + 1.4 * i / 12
        for sg in (-1, 1): db.line([P(0, y0), P(sg * 0.38, y0 + 0.2)], fill=(int(c * 0.82),) * 3 + (255,), width=3)
    bl.putalpha(Image.fromarray((np.asarray(bl.split()[3], float) * np.asarray(mask, float) / 255).astype(np.uint8))); im.alpha_composite(bl); d = ImageDraw.Draw(im)
    d.line([P(0, -1.0), P(0, 0.9)], fill=(int(c * 0.75), int(c * 0.72), int(c * 0.6), 255), width=6)
    a = np.asarray(im.resize((64, 64), Image.LANCZOS), float) / 255
    frames.append(a)
save(np.concatenate(frames, 1), "T_Feather.png")
print("ok")
