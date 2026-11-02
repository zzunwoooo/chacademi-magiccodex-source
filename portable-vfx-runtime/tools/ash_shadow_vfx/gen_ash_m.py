# 재의 그림자 잡몹 VFX (AM_*) 공용 텍스처 — 모두 중립(흰~회색) 램프. 색은 머티리얼 tint 로 (기본 = 잿불 주황, 이로치 = 하늘색).
# usage: python3 gen_ash_m.py tex_m
import sys, os, math, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = (sys.argv[1] if len(sys.argv) > 1 else "tex_m") + "/"; os.makedirs(OUT, exist_ok=True)

def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def save(rgb, a, name):
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]]) * 255).astype(np.uint8), "RGBA").save(OUT + name)
def gray(v): v = np.clip(v, 0, 1); return np.dstack([v, v, v])
# neutral heat ramp: hot core near white, edges mid grey (tint gives the hue)
def heat(h): return gray(0.32 + 0.68 * np.clip(h, 0, 1) ** 0.8)

# ---------------- flame flipbook 4x4 of 64 (256²) ----------------
C, G = 64, 4
cy_, cx_ = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 18, 18, 4, 11); nB = smooth_noise(C, C, 8, 8, 3, 12); stk = smooth_noise(C, C, 4, 22, 3, 13)
def flame_cell(t):
    R = 0.55 + 0.33 * t ** 0.6
    wx = cx_ + (nA - 0.5) * 0.45; wy = cy_ + (nB - 0.5) * 0.35 + 0.12
    up = np.clip(-wy, 0, None); rw = np.hypot(wx * (1 + 0.7 * up), wy * (1 - 0.4 * up)); body = 1 - rw / R
    h = body * (0.75 + 0.45 * stk) - t ** 1.3 * 0.75 * (0.5 + nB)
    ac = ss(0.0, 0.10, h); halo = np.exp(-(np.clip(-body, 0, None) / 0.18) ** 2) * 0.3 * (1 - t) ** 1.5
    a = np.clip(ac + halo * (1 - ac), 0, 1) * (1 - ss(0.88, 1.0, np.hypot(cx_, cy_)))
    return heat(np.clip(h * 1.25, 0, 1) * (1 - 0.35 * t)), a
def mist_cell(t):   # ash smoke: grey body, lighter top
    R = 0.6 + 0.32 * t ** 0.5; wx = cx_ + (nB - 0.5) * 0.6; wy = cy_ + (nA - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R + (stk - 0.5) * 0.25 - t ** 1.5 * 0.8 * (0.5 + nA)
    a = ss(0.0, 0.5, body) * 0.65 * (1 - 0.3 * t); lit = ss(0.1, 0.9, -wy * 0.8 + 0.2) * (1 - t) * 0.5
    return gray(0.35 + 0.5 * lit), a * (1 - ss(0.88, 1.0, np.hypot(cx_, cy_)))
def sheet(fn, name):
    rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
    for i in range(G*G):
        r, c = divmod(i, G); rgb, a = fn(i / (G*G - 1)); rgbS[r*C:(r+1)*C, c*C:(c+1)*C] = rgb; aS[r*C:(r+1)*C, c*C:(c+1)*C] = a
    save(rgbS, aS, name)
sheet(flame_cell, "T_AFlame_Sheet.png")
sheet(mist_cell, "T_AMist_Sheet.png")

# ---------------- streak / star / glow / dot ----------------
W, H = 128, 16; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
taper = x ** 1.6 * (1 - ss(0.92, 1.0, x))
core = np.exp(-(dy / (0.09 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.5 * taper + 1e-3)) ** 2) * 0.45 * taper
save(heat(np.clip(core + halo * 0.5, 0, 1)), np.clip(core + halo, 0, 1), "T_AStreak.png")
M = 128; my, mx = np.mgrid[-1:1:M*1j, -1:1:M*1j]; mr = np.hypot(mx, my) + 1e-6; ax_, ay_ = np.abs(mx), np.abs(my)
spike = np.exp(-ay_ / (0.02 * (1 - np.clip(ax_, 0, 1)) + 1e-3)) * (1 - ax_) ** 2 + np.exp(-ax_ / (0.02 * (1 - np.clip(ay_, 0, 1)) + 1e-3)) * (1 - ay_) ** 2
glw = np.exp(-(mr / 0.16) ** 2) * 0.9 + np.exp(-(mr / 0.5) ** 2) * 0.3
save(np.ones((M, M, 3)), np.clip(spike + glw, 0, 1), "T_AStar.png")
gg = np.exp(-(mr / 0.35) ** 2) * 0.85 + np.exp(-(mr / 0.75) ** 2) * 0.25; gg *= 1 - ss(0.9, 1.0, mr); save(np.ones((M, M, 3)), gg, "T_AGlow.png")
D = 32; dy2, dx2 = np.mgrid[-1:1:D*1j, -1:1:D*1j]; dr = np.hypot(dx2, dy2)
save(np.ones((D, D, 3)), np.clip(np.exp(-(dr / 0.3) ** 2) + (1 - ss(0.08, 0.14, dr)), 0, 1) * (1 - ss(0.85, 1, dr)), "T_ADot.png")

# ---------------- ring / shock / crack ----------------
S = 256; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; rr = np.hypot(xx, yy) + 1e-6; ang = np.arctan2(yy, xx)
nR = smooth_noise(S, S, 10, 10, 3, 51)
crest = np.exp(-((rr - 0.85 - (nR - 0.5) * 0.06) / 0.04) ** 2); trail = np.exp(-((rr - 0.7) / 0.15) ** 2) * (rr < 0.85) * (0.4 + 0.6 * nR)
save(heat(crest), np.clip(crest + trail * 0.5, 0, 1) * (1 - ss(0.95, 1, rr)), "T_AShock.png")
# ash ring: dusty band (for alpha-blended dark dust waves)
band = np.exp(-((rr - 0.78 - (nR - 0.5) * 0.1) / 0.12) ** 2) * (0.5 + 0.6 * nR)
save(gray(0.5 + 0.3 * nR), np.clip(band, 0, 1) * (1 - ss(0.95, 1, rr)), "T_ADust.png")
def cracks(size=384, seed=5):
    rng = np.random.default_rng(seed); im = Image.new("L", (size * 2, size * 2), 0); d = ImageDraw.Draw(im); c = size
    for k in range(9):
        a = k / 9 * math.tau + rng.uniform(-0.2, 0.2); x0, y0 = c, c; L = rng.uniform(0.55, 0.95) * size
        for s in range(14):
            a += rng.uniform(-0.45, 0.45); st = L / 14; x1, y1 = x0 + math.cos(a) * st, y0 + math.sin(a) * st
            d.line([x0, y0, x1, y1], fill=255, width=max(2, int(9 * (1 - s / 14))));
            if rng.random() < 0.25: b = a + rng.choice([-1, 1]) * rng.uniform(0.6, 1.1); d.line([x1, y1, x1 + math.cos(b) * st * 2, y1 + math.sin(b) * st * 2], fill=200, width=3)
            x0, y0 = x1, y1
    im = im.resize((size, size), Image.LANCZOS); a = np.asarray(im, float) / 255
    glow = np.asarray(im.filter(ImageFilter.GaussianBlur(5)), float) / 255
    sy_, sx_ = np.mgrid[-1:1:size*1j, -1:1:size*1j]; r2 = np.hypot(sx_, sy_)
    scorch = np.exp(-(r2 / 0.45) ** 2) * 0.55
    rgb = gray(np.clip(a * 1.0 + glow * 0.4, 0, 1) * 0.95 + 0.05)
    alpha = np.clip(a + glow * 0.6 + scorch, 0, 1) * (1 - ss(0.82, 1, r2))
    # scorch body dark: encode luminance low where only scorch
    rgb = rgb * np.clip(a + glow, 0.12, 1)[..., None]
    return rgb, alpha
rgb, a = cracks(); save(rgb, a, "T_ACrack.png")

# ---------------- crisp line profile (mesh line work) ----------------
lp = np.exp(-(np.linspace(-1, 1, 16)[:, None] / 0.28) ** 2) * np.ones((1, 64))
save(np.ones((16, 64, 3)), np.clip(lp, 0, 1), "T_ALine.png")

# ---------------- ash shard: dark body, bright rim (alpha-blend, HDR tint lights the rim) ----------------
W2, H2 = 128, 32; x2 = np.linspace(0, 1, W2)[None, :]; y2 = np.linspace(-1, 1, H2)[:, None]; dy3 = np.abs(y2)
nS = smooth_noise(W2, H2, 10, 10, 3, 41); prof = np.sin(np.pi * x2) ** 0.7 * (0.4 + 0.6 * x2)
dist = 0.5 * prof - (dy3 + (nS - 0.5) * 0.25); a = ss(-0.03, 0.02, dist); edge = np.exp(-(np.clip(dist, 0, None) / 0.05) ** 2)
save(gray(0.06 + 0.94 * edge), a, "T_AShard.png")

# ---------------- fang crescent (bite) : thick crescent with 3 tooth spikes on the inner edge ----------------
F = 256; fy, fx = np.mgrid[-1:1:F*1j, -1:1:F*1j]
r1 = np.hypot(fx, fy - 0.35); r2 = np.hypot(fx, fy - 0.6)
cres = ss(0.0, 0.03, 0.95 - r1) * ss(0.0, 0.03, r2 - 0.85)
teeth = np.zeros_like(fx)
for tx, L in ((-0.5, 0.26), (-0.18, 0.34), (0.18, 0.34), (0.5, 0.26)):
    base_y = 0.6 - math.sqrt(0.85 ** 2 - tx ** 2)
    w = (base_y + L - fy) / L * 0.11
    teeth = np.maximum(teeth, ((fy > base_y - 0.04) & (fy < base_y + L) & (np.abs(fx - tx) < np.clip(w, 0, 1))).astype(float))
shape = np.clip(cres + teeth, 0, 1)
sh = Image.fromarray((shape * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)); shape = np.asarray(sh, float) / 255
glo = np.asarray(sh.filter(ImageFilter.GaussianBlur(9)), float) / 255
inner = np.clip(1 - np.abs((r2 - 0.9) / 0.12), 0, 1)
save(heat(np.clip(shape * (0.55 + 0.45 * inner) + glo * 0.3, 0, 1)), np.clip(shape + glo * 0.45, 0, 1), "T_AFang.png")

# ---------------- bite arc (sector UV: u across angle, v radial) ----------------
AW, AH = 128, 64; au = np.linspace(0, 1, AW)[None, :]; av = np.linspace(0, 1, AH)[:, None]
nar = smooth_noise(AW, AH, 8, 20, 3, 61)
arcA = (np.exp(-((av - 0.08) / 0.06) ** 2) + ss(0.7, 0.05, av) * 0.35 * (0.5 + 0.6 * nar)) * np.sin(np.pi * au) ** 0.6
save(heat(np.clip(arcA, 0, 1)), np.clip(arcA, 0, 1), "T_AArc.png")

# ---------------- wax droplet (alpha-blend, glossy) ----------------
Dw = 64; wy_, wx_ = np.mgrid[-1:1:Dw*1j, -1:1:Dw*1j]
dropr = np.hypot(wx_ * (1 + 0.6 * np.clip(wy_, 0, 1)), wy_ * 0.9 + 0.1)
body = ss(0.0, 0.06, 0.75 - dropr); hl = np.exp(-((wx_ + 0.25) ** 2 + (wy_ + 0.25) ** 2) / 0.02)
save(gray(np.clip(0.75 + 0.25 * hl - 0.25 * np.clip(wy_, 0, 1), 0, 1)), body, "T_AWax.png")

# ---------------- ember sigil (small ash ring glyph for ground marks) ----------------
G2 = 384; im = Image.new("L", (G2 * 2, G2 * 2), 0); d = ImageDraw.Draw(im); c = G2
for R0, w in ((0.92, 6), (0.80, 3), (0.42, 4)):
    r = R0 * G2; d.ellipse([c - r, c - r, c + r, c + r], outline=255, width=w)
for k in range(12):
    a = k / 12 * math.tau; r0, r1 = 0.82 * G2, (0.88 if k % 2 else 0.95) * G2
    d.line([c + math.cos(a) * r0, c + math.sin(a) * r0, c + math.cos(a) * r1, c + math.sin(a) * r1], fill=255, width=5)
pts = [(c + math.cos(k / 3 * math.tau - math.pi / 2) * 0.42 * G2 * 1.0, c + math.sin(k / 3 * math.tau - math.pi / 2) * 0.42 * G2) for k in range(3)]
for k in range(3):
    a = k / 3 * math.tau - math.pi / 2; d.line([c, c, c + math.cos(a) * 0.80 * G2, c + math.sin(a) * 0.80 * G2], fill=230, width=4)
    d.ellipse([c + math.cos(a) * 0.80 * G2 - 14, c + math.sin(a) * 0.80 * G2 - 14, c + math.cos(a) * 0.80 * G2 + 14, c + math.sin(a) * 0.80 * G2 + 14], outline=255, width=4)
im2 = im.resize((G2 // 1, G2 // 1), Image.LANCZOS) if False else im.resize((256, 256), Image.LANCZOS)
a = np.asarray(im2, float) / 255; gl = np.asarray(im2.filter(ImageFilter.GaussianBlur(4)), float) / 255
save(np.ones((256, 256, 3)), np.clip(a + gl * 0.5, 0, 1), "T_ASigil.png")
print("ok", sorted(os.listdir(OUT)))
