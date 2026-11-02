# 재의 그림자 보스 VFX (그룹 A) 공용 텍스처: 잿불 불꽃·재 안개·손톱 자국·그림자 손·먹물 웅덩이·돌진 띠 등.
# usage: python gen_ash_a.py <outdir>   (예산: 큰 시트는 생성 단계에서 256~384로 바로 저장)
import sys, os, math, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = (sys.argv[1] if len(sys.argv) > 1 else "tex_a") + "/"; os.makedirs(OUT, exist_ok=True)

def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def save(rgb, a, name, size=None):
    im = Image.fromarray((np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]]) * 255).astype(np.uint8), "RGBA")
    if size: im = im.resize(size, Image.LANCZOS)
    im.save(OUT + name)
def ramp(h, stops):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
EMBER = [(1.0,(1,0.97,0.88)),(0.8,(1,0.82,0.48)),(0.6,(1,0.55,0.16)),(0.4,(0.92,0.30,0.06)),(0.2,(0.62,0.10,0.05)),(0.0,(0.18,0.04,0.04))]
GREY = [(1.0,(1,1,1)),(0.0,(0.6,0.6,0.6))]

S = 256
yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; rr = np.hypot(xx, yy) + 1e-6; ang = np.arctan2(yy, xx)

# ---------------- line work + ash runes + candle-halo sigil ----------------
vv = np.linspace(-1, 1, 16)[:, None] * np.ones((1, 64))
save(np.ones((16, 64, 3)), np.clip(np.exp(-(vv / 0.35) ** 2) + 0.35 * np.exp(-(vv / 0.8) ** 2), 0, 1), "T_Line.png")
def glyph_strip(n, w=1024, h=256):
    im = Image.new("L", (w, h), 0); d = ImageDraw.Draw(im); cw = w / n
    d.line([(0, 30), (w, 30)], fill=200, width=6); d.line([(0, h - 30), (w, h - 30)], fill=200, width=6)
    for i in range(n):
        cx, cy = cw * (i + 0.5), h / 2; k = i % 6; s = h * 0.26
        if k == 0:   # candle with flame
            d.rectangle([cx - s * 0.25, cy - s * 0.1, cx + s * 0.25, cy + s], fill=255)
            d.polygon([(cx, cy - s * 0.95), (cx - s * 0.22, cy - s * 0.35), (cx + s * 0.22, cy - s * 0.35)], fill=255)
        elif k == 1:  # eye (single)
            d.ellipse([cx - s, cy - s * 0.45, cx + s, cy + s * 0.45], outline=255, width=9); d.ellipse([cx - s * 0.2, cy - s * 0.42, cx + s * 0.2, cy + s * 0.42], fill=255)
        elif k == 2:  # ash flake trio
            for dx, dy in ((-0.5, 0.3), (0.4, -0.2), (0.0, 0.6)):
                d.polygon([(cx + dx * s, cy + dy * s - 18), (cx + dx * s + 14, cy + dy * s), (cx + dx * s, cy + dy * s + 18), (cx + dx * s - 14, cy + dy * s)], fill=255)
        elif k == 3:  # mask
            d.ellipse([cx - s * 0.7, cy - s, cx + s * 0.7, cy + s], outline=255, width=9)
            d.polygon([(cx - s * 0.5, cy - s * 0.25), (cx - s * 0.1, cy - s * 0.1), (cx - s * 0.45, cy + s * 0.05)], fill=255)
            d.polygon([(cx + s * 0.5, cy - s * 0.25), (cx + s * 0.1, cy - s * 0.1), (cx + s * 0.45, cy + s * 0.05)], fill=255)
            d.line([(cx - s * 0.3, cy + s * 0.5), (cx + s * 0.3, cy + s * 0.5)], fill=255, width=7)
        elif k == 4:  # claw marks
            for o in (-0.45, 0, 0.45): d.line([(cx + (o - 0.25) * s, cy - s), (cx + (o + 0.25) * s, cy + s)], fill=255, width=9)
        else:         # broken halo
            d.arc([cx - s, cy - s, cx + s, cy + s], 200, 520, fill=255, width=9)
            for a0 in (240, 300, 360, 420): ar = math.radians(a0); d.line([(cx + math.cos(ar) * s, cy + math.sin(ar) * s), (cx + math.cos(ar) * s * 1.3, cy + math.sin(ar) * s * 1.3)], fill=255, width=7)
        d.ellipse([cx + cw / 2 - 7, cy - 7, cx + cw / 2 + 7, cy + 7], fill=200)
    return im
g = glyph_strip(8); gl = np.asarray(g.filter(ImageFilter.GaussianBlur(8)), float) / 255; gs = np.asarray(g.filter(ImageFilter.GaussianBlur(1)), float) / 255
save(np.ones((256, 1024, 3)) * np.array([1, 0.95, 0.9]), np.clip(gs + gl * 0.7, 0, 1), "T_RuneStrip.png", (512, 128))
def sigil(size=1152):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2
    for r, w in ((0.48, 10), (0.44, 5), (0.30, 8), (0.26, 4)): d.ellipse([c - size * r, c - size * r, c + size * r, c + size * r], outline=255, width=w)
    for k in range(12):   # candles around the halo
        a0 = math.radians(k * 30); px, py = c + math.cos(a0) * size * 0.37, c + math.sin(a0) * size * 0.37
        d.ellipse([px - 14, py - 14, px + 14, py + 14], fill=255)
        d.line([(c + math.cos(a0) * size * 0.30, c + math.sin(a0) * size * 0.30), (px, py)], fill=255, width=5)
    for k in range(48):
        a0 = math.radians(k * 7.5); L1, L2 = size * 0.44, size * (0.48 if k % 4 == 0 else 0.46)
        d.line([(c + math.cos(a0) * L1, c + math.sin(a0) * L1), (c + math.cos(a0) * L2, c + math.sin(a0) * L2)], fill=255, width=5)
    s = size * 0.2   # mask with one eye
    d.ellipse([c - s * 0.75, c - s, c + s * 0.75, c + s], outline=255, width=12)
    d.polygon([(c - s * 0.15, c - s * 0.2), (c + s * 0.15, c - s * 0.2), (c, c - s * 0.55)], fill=255)
    d.ellipse([c - s * 0.12, c - s * 0.3, c + s * 0.12, c - s * 0.05], fill=0)
    for o in (-0.3, 0, 0.3): d.line([(c + o * s, c + s * 0.35), (c + o * s, c + s * 0.6)], fill=255, width=8)
    d.line([(c - s * 0.4, c + s * 0.48), (c + s * 0.4, c + s * 0.48)], fill=255, width=8)
    return im
g = sigil(); gl = np.asarray(g.filter(ImageFilter.GaussianBlur(14)), float) / 255; gs = np.asarray(g.filter(ImageFilter.GaussianBlur(1.5)), float) / 255
save(np.ones((1152, 1152, 3)), np.clip(gs + gl * 0.8, 0, 1), "T_Sigil.png", (320, 320))

# ---------------- flipbooks 4x4 (cells 128 → saved 256) ----------------
C, G = 128, 4
cy_, cx_ = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 36, 36, 4, 11); nB = smooth_noise(C, C, 16, 16, 3, 12); stk = smooth_noise(C, C, 7, 45, 3, 13)
def flame_cell(t):
    R = 0.55 + 0.33 * t ** 0.6
    wx = cx_ + (nA - 0.5) * 0.5; wy = cy_ + (nB - 0.5) * 0.4 + 0.12
    up = np.clip(-wy, 0, None); rw = np.hypot(wx * (1 + 0.9 * up), wy * (1 - 0.4 * up)); body = 1 - rw / R
    heat = body * (0.75 + 0.45 * stk) - t ** 1.3 * 0.75 * (0.5 + nB)
    ac = ss(0.0, 0.10, heat); halo = np.exp(-(np.clip(-body, 0, None) / 0.18) ** 2) * 0.35 * (1 - t) ** 1.5
    rgb = ramp(np.clip(heat * 1.25, 0, 1) * (1 - 0.35 * t), EMBER)
    a = np.clip(ac + halo * (1 - ac), 0, 1); rgb = rgb * ac[..., None] + np.array((1, 0.4, 0.1)) * (1 - ac[..., None])
    return rgb, a * (1 - ss(0.88, 1.0, np.hypot(cx_, cy_)))
def mist_cell(t):   # charcoal ash smoke, faint ember-lit underside
    R = 0.6 + 0.32 * t ** 0.5; wx = cx_ + (nB - 0.5) * 0.6; wy = cy_ + (nA - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R + (stk - 0.5) * 0.25 - t ** 1.5 * 0.8 * (0.5 + nA)
    a = ss(0.0, 0.5, body) * 0.62 * (1 - 0.3 * t); lit = ss(0.1, 0.9, wy * 0.8 + 0.2)[..., None] * (1 - t) * 0.5
    rgb = np.array([0.09, 0.08, 0.08]) * (1 - lit) + np.array([0.45, 0.18, 0.07]) * lit
    return rgb, a * (1 - ss(0.88, 1.0, np.hypot(cx_, cy_)))
def sheet(fn, name, out=256):
    rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
    for i in range(G*G):
        r, c = divmod(i, G); rgb, a = fn(i / (G*G - 1)); rgbS[r*C:(r+1)*C, c*C:(c+1)*C] = rgb; aS[r*C:(r+1)*C, c*C:(c+1)*C] = a
    save(rgbS, aS, name, (out, out))
sheet(flame_cell, "T_EmberFlame_Sheet.png")
sheet(mist_cell, "T_AshMist_Sheet.png")

# ---------------- claw slash wipe 2x8 (256x64 cells → saved 384x256): three parallel claw strokes ----------------
CW, CH = 256, 64
u = np.linspace(0, 1, CW)[None, :]; v = np.linspace(0, 1, CH)[:, None]
N2 = smooth_noise(CW, CH, 12, 12, 3, 22); strands = smooth_noise(CW, CH, 120, 4, 3, 23)
CLAW = [(1.0,(1,0.98,0.9)),(0.8,(1,0.78,0.38)),(0.6,(1,0.48,0.12)),(0.42,(0.78,0.2,0.06)),(0.25,(0.35,0.07,0.05)),(0.0,(0.06,0.03,0.04))]
def claw_cell(t):
    head = min(1.0, t / 0.45) ** 0.7; tail = max(0.0, (t - 0.2) / 0.8) ** 1.3
    inside = ss(tail, tail + 0.05, u) * (1 - ss(head - 0.012, head + 0.004, u))
    rel = np.clip((u - tail) / max(head - tail, 1e-3), 0, 1); width = np.sin(np.pi * rel) ** 0.5
    heat = np.zeros((CH, CW)); halo = np.zeros((CH, CW))
    for lane in (0.2, 0.5, 0.8):
        d = np.abs(v - lane) / (0.105 * width + 1e-3)
        heat = np.maximum(heat, np.exp(-(d / 0.5) ** 2) * (0.6 + 0.4 * strands)); halo = np.maximum(halo, np.exp(-(d / 1.8) ** 2))
    heat = heat * (0.45 + 0.55 * rel ** 0.6)
    if t > 0.45: heat = heat * (1 - ss(0.0, 1.0, (t - 0.45) * 2.2 * (0.6 + N2)))
    a = np.clip(ss(0.02, 0.25, heat) + halo * 0.18 * width, 0, 1) * inside
    return ramp(heat, CLAW), a
rgbS = np.zeros((CH*8, CW*2, 3)); aS = np.zeros((CH*8, CW*2))
for i in range(16):
    r, c = divmod(i, 2); rgb, a = claw_cell(i / 15); rgbS[r*CH:(r+1)*CH, c*CW:(c+1)*CW] = rgb; aS[r*CH:(r+1)*CH, c*CW:(c+1)*CW] = a
save(rgbS, aS, "T_ClawSlash_Sheet.png", (384, 256))

# ---------------- small sprites ----------------
W, H = 256, 32; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
taper = x ** 1.6 * (1 - ss(0.92, 1.0, x))
core = np.exp(-(dy / (0.09 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.5 * taper + 1e-3)) ** 2) * 0.45 * taper
save(ramp(0.35 + 0.65 * np.clip(core + halo * 0.5, 0, 1), EMBER), np.clip(core + halo, 0, 1), "T_FireStreak.png")
nS = smooth_noise(W, H, 20, 20, 3, 41); profS = np.sin(np.pi * x) ** 0.7 * (0.4 + 0.6 * x)
dist = 0.5 * profS - (dy + (nS - 0.5) * 0.25); a = ss(-0.03, 0.02, dist); edge = np.exp(-(np.clip(dist, 0, None) / 0.05) ** 2)[..., None]
save(np.array([0.06, 0.05, 0.05]) * (1 - edge) + np.array([1.0, 0.45, 0.12]) * edge, a, "T_InkShard.png")
M = 128; my, mx = np.mgrid[-1:1:M*1j, -1:1:M*1j]; mr = np.hypot(mx, my) + 1e-6; ax_, ay_ = np.abs(mx), np.abs(my)
spike = np.exp(-ay_ / (0.02 * (1 - np.clip(ax_, 0, 1)) + 1e-3)) * (1 - ax_) ** 2 + np.exp(-ax_ / (0.02 * (1 - np.clip(ay_, 0, 1)) + 1e-3)) * (1 - ay_) ** 2
save(np.ones((M, M, 3)), np.clip(spike + np.exp(-(mr / 0.16) ** 2) * 0.9 + np.exp(-(mr / 0.5) ** 2) * 0.3, 0, 1), "T_Star.png")
gg = np.exp(-(mr / 0.35) ** 2) * 0.85 + np.exp(-(mr / 0.75) ** 2) * 0.25; gg *= 1 - ss(0.9, 1.0, mr); save(np.ones((M, M, 3)), gg, "T_Glow.png")
D = 64; dy2, dx2 = np.mgrid[-1:1:D*1j, -1:1:D*1j]; dr = np.hypot(dx2, dy2)
save(np.ones((D, D, 3)), np.clip(np.exp(-(dr / 0.3) ** 2) + (1 - ss(0.08, 0.14, dr)), 0, 1) * (1 - ss(0.85, 1, dr)), "T_Dot.png")
# ash flake: irregular pale flake with darker burnt edge (alpha-blended)
fl = Image.new("L", (256, 256), 0); d = ImageDraw.Draw(fl); rng = np.random.default_rng(71)
pts = [(128 + math.cos(a) * r, 128 + math.sin(a) * r * 0.7) for a, r in zip(np.linspace(0, 2 * math.pi, 9)[:-1], rng.uniform(60, 110, 8))]
d.polygon(pts, fill=255)
fa = np.asarray(fl.filter(ImageFilter.GaussianBlur(2)).resize((D, D), Image.LANCZOS), float) / 255
fe = np.asarray(fl.filter(ImageFilter.MinFilter(25)).filter(ImageFilter.GaussianBlur(3)).resize((D, D), Image.LANCZOS), float) / 255
rgb = np.array([0.30, 0.27, 0.25])[None, None, :] * (1 - fe[..., None]) + np.array([0.86, 0.83, 0.79])[None, None, :] * fe[..., None]
save(rgb, fa, "T_AshFlake.png")
nR = smooth_noise(S, S, 10, 10, 3, 51); ring = np.exp(-((rr - 0.8) / 0.025) ** 2); inner = np.exp(-((rr - 0.7) / 0.13) ** 2) * (rr < 0.8) * (0.5 + 0.6 * nR)
save(np.ones((S, S, 3)), np.clip(ring + inner * 0.55, 0, 1) * (1 - ss(0.95, 1.0, rr)), "T_Ring.png")
crest = np.exp(-((rr - 0.85 - (nR - 0.5) * 0.06) / 0.04) ** 2); trail = np.exp(-((rr - 0.7) / 0.15) ** 2) * (rr < 0.85) * (0.4 + 0.6 * nR)
save(np.ones((S, S, 3)), np.clip(crest + trail * 0.5, 0, 1) * (1 - ss(0.95, 1, rr)), "T_Shock.png")
def cracks(size=768, seed=5, n=11):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2; rng = np.random.default_rng(seed)
    for k in range(n):
        a0 = k / n * 2 * math.pi + rng.uniform(-0.2, 0.2); px, py = c, c; L = size * rng.uniform(0.32, 0.47); m = 9
        for j in range(m):
            a0 += rng.uniform(-0.45, 0.45); nx, ny = px + math.cos(a0) * L / m, py + math.sin(a0) * L / m
            d.line([(px, py), (nx, ny)], fill=255, width=max(2, int(12 * (1 - j / m)))); px, py = nx, ny
            if rng.random() < 0.3:
                b = a0 + rng.choice([-1, 1]) * 0.9; d.line([(px, py), (px + math.cos(b) * L * 0.18, py + math.sin(b) * L * 0.18)], fill=255, width=3)
    return im
cr = cracks(); cg = np.asarray(cr.filter(ImageFilter.GaussianBlur(6)).resize((S, S), Image.LANCZOS), float) / 255
cs = np.asarray(cr.filter(ImageFilter.GaussianBlur(1)).resize((S, S), Image.LANCZOS), float) / 255
dark = np.clip(cg * 1.6, 0, 1) * 0.85 * (1 - ss(0.8, 1, rr)); hot = cs
rgb = np.array([0.05, 0.04, 0.04]) * (1 - hot[..., None]) + ramp(0.55 + 0.45 * hot, EMBER) * hot[..., None]
save(rgb, np.clip(dark + hot, 0, 1) * (1 - ss(0.85, 1, rr)), "T_Crack.png")
# scorch: soft dark burn disc with ember speckles (alpha-blended)
nS2 = smooth_noise(S, S, 18, 18, 4, 81)
sc_a = (1 - ss(0.45, 1.0, rr + (nS2 - 0.5) * 0.35)) * 0.85
speck = (smooth_noise(S, S, 3, 3, 2, 82) > 0.8) * (rr < 0.75)
rgb = np.array([0.06, 0.05, 0.05]) * np.ones((S, S, 3)); rgb = np.where(speck[..., None], np.array([1.0, 0.45, 0.1]), rgb)
save(rgb, sc_a, "T_Scorch.png")

# ---------------- shadow hand print (top-down decal: palm toward origin, fingers toward +Z = texture top) ----------------
def hand_mask(size=768):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2
    d.ellipse([c - 150, c - 20, c + 150, c + 260], fill=255)                      # palm (lower half)
    for k, (ox, L, w) in enumerate([(-130, 300, 54), (-45, 360, 58), (45, 360, 58), (130, 300, 54)]):
        tilt = (ox / 130) * 0.18; x0, y0 = c + ox * 0.95, c + 30
        x1, y1 = x0 + math.sin(tilt) * L, y0 - math.cos(tilt) * L
        d.line([(x0, y0), (x1, y1)], fill=255, width=w)
        d.polygon([(x1 - w / 2, y1 + 10), (x1 + w / 2, y1 + 10), (x1 + math.sin(tilt) * 70, y1 - 70)], fill=255)   # claw tip
    d.line([(c - 150, c + 120), (c - 280, c - 40)], fill=255, width=56)            # thumb
    d.polygon([(c - 300, c - 20), (c - 255, c - 60), (c - 320, c - 110)], fill=255)
    return im
hm = hand_mask(); hb = np.asarray(hm.filter(ImageFilter.GaussianBlur(3)).resize((S, S), Image.LANCZOS), float) / 255
he = np.asarray(hm.filter(ImageFilter.MinFilter(21)).filter(ImageFilter.GaussianBlur(4)).resize((S, S), Image.LANCZOS), float) / 255
hrim = np.clip(hb - he, 0, 1) * 1.8
hcr = np.asarray(cracks(768, 9, 7).filter(ImageFilter.GaussianBlur(1)).resize((S, S), Image.LANCZOS), float) / 255 * he
hot = np.clip(hrim + hcr * 0.9, 0, 1)
rgb = np.array([0.04, 0.03, 0.04]) * (1 - hot[..., None]) + ramp(0.3 + 0.42 * hot, EMBER) * hot[..., None]
save(rgb, np.clip(hb * 0.95 + hot * 0.2, 0, 1), "T_HandPrint.png")

# ---------------- rising shadow hand (side view, fingers up, wrist at bottom) 128x256 ----------------
def side_hand(w=512, h=1024):
    im = Image.new("L", (w, h), 0); d = ImageDraw.Draw(im); c = w / 2
    d.polygon([(c - 70, h), (c + 70, h), (c + 110, h * 0.55), (c - 110, h * 0.55)], fill=255)            # forearm
    d.ellipse([c - 150, h * 0.36, c + 150, h * 0.62], fill=255)                                              # palm
    for ox, top, bend in ((-120, 0.12, -40), (-40, 0.04, -12), (40, 0.05, 12), (120, 0.14, 40)):
        d.line([(c + ox * 0.9, h * 0.45), (c + ox + bend * 0.4, h * (top + 0.16)), (c + ox + bend, h * top)], fill=255, width=46, joint="curve")
        d.polygon([(c + ox + bend - 22, h * top + 10), (c + ox + bend + 22, h * top + 10), (c + ox + bend * 1.5, h * top - 70)], fill=255)
    d.line([(c - 140, h * 0.55), (c - 230, h * 0.38)], fill=255, width=44)
    return im
sh = side_hand(); sb = np.asarray(sh.filter(ImageFilter.GaussianBlur(3)).resize((128, 256), Image.LANCZOS), float) / 255
se = np.asarray(sh.filter(ImageFilter.MinFilter(25)).filter(ImageFilter.GaussianBlur(4)).resize((128, 256), Image.LANCZOS), float) / 255
srim = np.clip(sb - se, 0, 1) * 1.8; nH = smooth_noise(128, 256, 10, 30, 3, 91)
fade = ss(1.0, 0.7, np.linspace(0, 1, 256))[:, None]   # wrist dissolves into smoke at the bottom
rgb = np.array([0.04, 0.03, 0.04]) * (1 - srim[..., None]) + ramp(0.3 + 0.42 * np.clip(srim, 0, 1), EMBER) * np.clip(srim, 0, 1)[..., None]
save(rgb, np.clip(sb * (0.75 + 0.25 * nH) * fade, 0, 1), "T_ShadowHand.png")

# ---------------- ink pool (top-down, rippled rim + glints) ----------------
nP = smooth_noise(S, S, 14, 14, 4, 101); prr = rr + (nP - 0.5) * 0.18
pool = 1 - ss(0.7, 0.86, prr); rip = np.exp(-((prr - 0.5) / 0.03) ** 2) * 0.5 + np.exp(-((prr - 0.72) / 0.025) ** 2) * 0.8
rgb = np.array([0.03, 0.02, 0.03]) * np.ones((S, S, 3)) + np.array([0.9, 0.35, 0.08]) * rip[..., None] * 0.8
save(rgb, np.clip(pool * 0.95 + rip * 0.2, 0, 1), "T_InkPool.png")

# ---------------- dash band (U across, V along): ember streak lines in ash, edges ragged ----------------
BW2, BH2 = 128, 256; bu = np.linspace(-1, 1, BW2)[None, :]; bv = np.linspace(0, 1, BH2)[:, None]
lines = smooth_noise(BW2, BH2, 3, 120, 3, 111); edgeN = smooth_noise(BW2, BH2, 20, 20, 3, 112)
body = 1 - ss(0.6, 1.0, np.abs(bu) + (edgeN - 0.5) * 0.4)
streak = ss(0.55, 0.85, lines) * body; endfade = ss(0.0, 0.08, bv) * (1 - ss(0.9, 1.0, bv))
rgb = ramp(0.3 + 0.7 * streak, EMBER) * streak[..., None] + np.array([0.06, 0.05, 0.05]) * (1 - streak[..., None])
save(rgb, np.clip(body * 0.55 + streak * 0.6, 0, 1) * endfade, "T_DashBand.png")
print("ok")
