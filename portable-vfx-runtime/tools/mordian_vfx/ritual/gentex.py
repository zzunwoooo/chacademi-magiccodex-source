# Mordian VFX textures. usage: python gentex.py <outdir> <pack>   pack = rune | offering | reseal
import sys, os, math, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = sys.argv[1].rstrip('/') + '/'; PACK = sys.argv[2]; os.makedirs(OUT, exist_ok=True)

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
def hexc(h): return np.array([int(h[i:i+2], 16) / 255 for i in (1, 3, 5)])

# palettes: core → mid → edge (white-hot core → saturated hue → deep hue)
if PACK == 'reseal':
    CORE, MID, EDGE = hexc('#ffffff'), hexc('#f4f0ff'), hexc('#e8d9a8')
else:
    CORE, MID, EDGE = hexc('#f2e6ff'), hexc('#d696ff'), hexc('#8a3cff')
INK, SHADOW, VIO, LIL = hexc('#09060e'), hexc('#2a1640'), hexc('#8a3cff'), hexc('#d696ff')
def ramp(h):  # h 1 = core, 0.5 = mid, 0 = edge
    h = np.clip(h, 0, 1)[..., None]
    return np.where(h > 0.5, MID + (CORE - MID) * (h - 0.5) * 2, EDGE + (MID - EDGE) * h * 2)

# ---------- line profile (U along the line, V across); used by every mesh ring / polygram / glyph stroke ----------
W, H = 64, 32
v = np.linspace(-1, 1, H)[:, None] * np.ones((1, W))
core = np.exp(-(v / 0.16) ** 2); halo = np.exp(-(v / 0.55) ** 2) * 0.5
save(ramp(core * 0.95 + halo * 0.4), np.clip(core + halo, 0, 1) * (1 - ss(0.9, 1.0, np.abs(v))), "T_Line.png")

# ---------- line reveal wipe 2x8 (cells 256x32): bright pen head runs U 0 → 1 (frame 13), last frames = full line ----------
CW, CH = 256, 32
u = np.linspace(0, 1, CW)[None, :]; vv = np.linspace(-1, 1, CH)[:, None]
grain = smooth_noise(CW, CH, 60, 6, 3, 5)
def wipe(i):
    h = min(1.0, (i + 1) / 13)
    shown = 1 - ss(h - 0.005, h + 0.01, u)
    c = np.exp(-(vv / 0.16) ** 2); hal = np.exp(-(vv / 0.55) ** 2) * 0.5
    head = np.exp(-((u - h) / 0.05) ** 2) * (1 if i < 13 else max(0, 1 - (i - 12) / 3))
    trail = np.exp(-np.clip(h - u, 0, None) / 0.12) * shown
    heat = np.clip(c * (0.7 + 0.3 * grain) + trail * c * 0.4 + head * np.exp(-(vv / 0.5) ** 2), 0, 1)
    a = np.clip((c + hal) * shown + head * np.exp(-(vv / 0.7) ** 2) * 0.9, 0, 1) * (1 - ss(0.9, 1.0, np.abs(vv)))
    return ramp(heat * 0.95 + hal * 0.3), a
rgbS = np.zeros((CH * 8, CW * 2, 3)); aS = np.zeros((CH * 8, CW * 2))
for i in range(16):
    r, c = divmod(i, 2); rgb, a = wipe(i)
    rgbS[r*CH:(r+1)*CH, c*CW:(c+1)*CW] = rgb; aS[r*CH:(r+1)*CH, c*CW:(c+1)*CW] = a
save(rgbS, aS, "T_LineWipe.png")

# ---------- helpers for PIL line art: draw at 3x on a mask, blur for glow, downsample ----------
def lineart(size, draw_fn, ss_=3, glow=6, core_w=1.0):
    S = size * ss_; m = Image.new("L", (S, S), 0); d = ImageDraw.Draw(m); draw_fn(d, S)
    core = np.asarray(m.resize((size, size), Image.LANCZOS), float) / 255
    g = np.asarray(m.filter(ImageFilter.GaussianBlur(glow * ss_)).resize((size, size), Image.LANCZOS), float) / 255
    g = g / max(g.max(), 1e-3)
    heat = np.clip(core * core_w + g * 0.35, 0, 1)
    a = np.clip(core + g * 0.75, 0, 1)
    return ramp(heat), a

rng = np.random.default_rng(7)
def rune_glyph(d, x, y, s, w, seed):
    r = np.random.default_rng(seed)
    pts = [(x + s * px, y + s * py) for px, py in [(0, -1), (0, 1)]]
    d.line(pts, fill=255, width=w)                              # stem
    for k in range(r.integers(2, 4)):
        y0 = r.uniform(-0.9, 0.6); side = r.choice([-1, 1]); L = r.uniform(0.4, 0.75)
        kind = r.integers(0, 3)
        if kind == 0: d.line([(x, y + s * y0), (x + side * s * L, y + s * (y0 + L * 0.7))], fill=255, width=w)
        elif kind == 1: d.line([(x, y + s * y0), (x + side * s * L, y + s * (y0 - L * 0.6)), (x + side * s * L, y + s * (y0 + 0.3))], fill=255, width=w)
        else: d.ellipse([x + side * s * 0.45 - s * 0.14, y + s * y0 - s * 0.14, x + side * s * 0.45 + s * 0.14, y + s * y0 + s * 0.14], outline=255, width=w)

# ---------- rune strip 512x128 (maps U 0..1 of one arc segment) ----------
def runestrip(n=5, seed=11):
    def fn(d, S):
        Wd, Hd = 512 * 3, 128 * 3; im = Image.new("L", (Wd, Hd), 0); dd = ImageDraw.Draw(im)
        dd.line([(0, Hd * 0.1), (Wd, Hd * 0.1)], fill=255, width=6); dd.line([(0, Hd * 0.9), (Wd, Hd * 0.9)], fill=255, width=6)
        for k in range(n):
            rune_glyph(dd, (k + 0.5) * Wd / n, Hd * 0.5, Hd * 0.27, 9, seed + k)
            dd.ellipse([(k + 1) * Wd / n - 9, Hd * 0.5 - 9, (k + 1) * Wd / n + 9, Hd * 0.5 + 9], fill=255)
        return im
    im = fn(None, None)
    core = np.asarray(im.resize((512, 128), Image.LANCZOS), float) / 255
    g = np.asarray(im.filter(ImageFilter.GaussianBlur(14)).resize((512, 128), Image.LANCZOS), float) / 255; g /= max(g.max(), 1e-3)
    save(ramp(np.clip(core + g * 0.3, 0, 1)), np.clip(core + g * 0.7, 0, 1), "T_RuneStrip.png")
runestrip()

S = 128
yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; rr = np.hypot(xx, yy) + 1e-6
g = np.exp(-(rr / 0.35) ** 2) * 0.85 + np.exp(-(rr / 0.75) ** 2) * 0.25; g *= 1 - ss(0.9, 1.0, rr)
save(np.ones((S, S, 3)), g, "T_Glow.png")
S2 = 64; y2, x2 = np.mgrid[-1:1:S2*1j, -1:1:S2*1j]; r2 = np.hypot(x2, y2)
dd_ = np.exp(-(r2 / 0.3) ** 2) + (1 - ss(0.08, 0.16, r2))
save(ramp(np.clip(1 - r2 * 1.5, 0, 1) * 0.5 + 0.5), np.clip(dd_, 0, 1) * (1 - ss(0.85, 1, r2)), "T_Dot.png")
ax, ay = np.abs(xx), np.abs(yy)
spike = np.exp(-ay / (0.025 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (0.025 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
gl = np.exp(-(rr / 0.14) ** 2) * 0.9 + np.exp(-(rr / 0.45) ** 2) * 0.3
save(ramp(np.clip(spike + np.exp(-(rr / 0.1) ** 2), 0, 1) * 0.6 + 0.35), np.clip(spike + gl, 0, 1) * (1 - ss(0.95, 1, rr)), "T_Star.png")
# streak (head at the right, used stretched)
W2, H2 = 256, 32
x = np.linspace(0, 1, W2)[None, :]; y = np.linspace(-1, 1, H2)[:, None]; dy = np.abs(y)
taper = x ** 1.4 * (1 - ss(0.9, 1.0, x))
c = np.exp(-(dy / (0.12 * taper + 0.01)) ** 2); hl = np.exp(-(dy / (0.5 * taper + 1e-3)) ** 2) * 0.45 * taper
save(ramp(np.clip(c + hl * 0.4, 0, 1)), np.clip(c + hl, 0, 1), "T_Streak.png")
# ring (thin sharp ring + soft inner wash), radius 0.8 of the half size
S3 = 256; y3, x3 = np.mgrid[-1:1:S3*1j, -1:1:S3*1j]; r3 = np.hypot(x3, y3)
nR = smooth_noise(S3, S3, 20, 20, 3, 51)
ring = np.exp(-((r3 - 0.8) / 0.022) ** 2); inner = np.exp(-((r3 - 0.7) / 0.13) ** 2) * (r3 < 0.8) * (0.5 + 0.6 * nR)
save(ramp(np.clip(ring + inner * 0.35, 0, 1)), np.clip(ring + inner * 0.5, 0, 1) * (1 - ss(0.95, 1.0, r3)), "T_Ring.png")
# ink shard: dark body + violet rim (alpha-blended, HDR tint blooms the rim)
W4, H4 = 128, 64
x4 = np.linspace(0, 1, W4)[None, :]; y4 = np.linspace(-1, 1, H4)[:, None]
nS = smooth_noise(W4, H4, 20, 20, 3, 41)
prof = np.sin(np.pi * x4) ** 0.8 * (0.35 + 0.65 * x4)
dist = 0.6 * prof - (np.abs(y4) + (nS - 0.5) * 0.3)
a = ss(-0.03, 0.03, dist); edge = np.exp(-(np.clip(dist, 0, None) / 0.07) ** 2)[..., None]
rimc = LIL if PACK != 'reseal' else LIL
save(INK * (1 - edge) + rimc * edge * 0.55, a, "T_InkShard.png")
# ink mist flipbook 4x4 (96 px cells): translucent dark smoke with a faint violet underlight
C, G = 96, 4
yy5, xx5 = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 30, 30, 4, 11); nB = smooth_noise(C, C, 14, 14, 3, 12)
rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
for i in range(16):
    t = i / 15; R = 0.55 + 0.35 * t ** 0.5
    wx = xx5 + (nB - 0.5) * 0.6; wy = yy5 + (nA - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R - t ** 1.5 * 0.7 * (0.5 + nA)
    a = ss(0.0, 0.5, body) * 0.7 * (1 - 0.3 * t)
    lit = (ss(0.0, 0.6, yy5 * 0.6 + 0.1) * (1 - t) * 0.6)[..., None]
    rgb = INK * (1 - lit) + SHADOW * 1.6 * lit
    r_, c_ = divmod(i, G)
    rgbS[r_*C:(r_+1)*C, c_*C:(c_+1)*C] = rgb; aS[r_*C:(r_+1)*C, c_*C:(c_+1)*C] = a * (1 - ss(0.88, 1, np.hypot(xx5, yy5)))
save(rgbS, aS, "T_InkMist_Sheet.png")

# ---------- three parallel diagonal scratches (Mordian signature), violet, 256 ----------
def scratches(d, S):
    for k in range(3):
        o = (k - 1) * 0.2 * S
        pts = [(S * 0.22 + o, S * 0.18), (S * 0.78 + o, S * 0.82)]
        n = 24
        for j in range(n):
            t0, t1 = j / n, (j + 1) / n
            w = max(2, int(S * 0.045 * math.sin(math.pi * (t0 + t1) / 2) ** 0.7))
            p0 = (pts[0][0] + (pts[1][0] - pts[0][0]) * t0, pts[0][1] + (pts[1][1] - pts[0][1]) * t0)
            p1 = (pts[0][0] + (pts[1][0] - pts[0][0]) * t1, pts[0][1] + (pts[1][1] - pts[0][1]) * t1)
            d.line([p0, p1], fill=255, width=w)
rgb, a = lineart(256, scratches, glow=7)
save(rgb, a, "T_Scratch.png")

# ---------- dark crack decal (wrong / fail): violet glowing cracks, radial ----------
def cracks(d, S, seed=3, n=9):
    r = np.random.default_rng(seed); c = S / 2
    for k in range(n):
        ang = k / n * 2 * math.pi + r.uniform(-0.25, 0.25); p = (c, c); L = 0
        while L < S * 0.46:
            step = r.uniform(S * 0.03, S * 0.06); ang += r.uniform(-0.45, 0.45); L += step
            q = (p[0] + math.cos(ang) * step, p[1] + math.sin(ang) * step)
            d.line([p, q], fill=int(255 * max(0.05, 1 - L / (S * 0.5)) ** 0.5), width=max(2, int(S * 0.012 * max(0, 1 - L / (S * 0.5)) + 2)))
            if r.random() < 0.2:
                b = ang + r.choice([-1, 1]) * 0.8; q2 = (q[0] + math.cos(b) * step * 1.5, q[1] + math.sin(b) * step * 1.5)
                d.line([q, q2], fill=160, width=3)
            p = q
rgb, a = lineart(256, cracks, glow=5)
save(rgb, a, "T_Crack.png")

if PACK in ('offering', 'rune', 'reseal'):
    # ink splat decal (alpha-blended): ragged dark ink stain with violet-lit rim
    S6 = 256; y6, x6 = np.mgrid[-1:1:S6*1j, -1:1:S6*1j]; r6 = np.hypot(x6, y6); th = np.arctan2(y6, x6)
    nn = smooth_noise(S6, S6, 18, 18, 4, 61)
    edge_r = 0.62 + 0.18 * np.sin(7 * th + 1) * 0.5 + 0.22 * (nn - 0.5) + 0.12 * np.maximum(0, np.sin(13 * th)) ** 6
    dist = edge_r - r6
    a = ss(-0.02, 0.04, dist) * (0.75 + 0.25 * nn)
    blobs = smooth_noise(S6, S6, 8, 8, 2, 62); a = np.maximum(a, ss(0.72, 0.8, blobs) * (r6 < 0.95) * 0.9)
    rim = np.exp(-(np.clip(dist, 0, None) / 0.05) ** 2)[..., None]
    save(INK * (1 - rim) + VIO * 0.5 * rim, a * (1 - ss(0.92, 1, r6)), "T_InkSplat.png")

if PACK == 'offering':
    # shadow spike (vertical, base at the bottom = v 0): dark ink body with a violet rim
    W7, H7 = 96, 256
    x7 = np.linspace(-1, 1, W7)[None, :]; y7 = np.linspace(1, 0, H7)[:, None]   # y7 = 0 at the bottom row
    n7 = smooth_noise(W7, H7, 10, 60, 3, 71)
    half = 0.85 * (1 - y7) ** 1.3 + 0.04
    dist = half - np.abs(x7 + (n7 - 0.5) * 0.25 * (1 - y7))
    a = ss(-0.03, 0.03, dist) * (1 - ss(0.0, 0.08, -y7 + 0.04))
    rim = np.exp(-(np.clip(dist, 0, None) / 0.08) ** 2)[..., None]
    streak = (0.6 + 0.4 * n7)[..., None]
    save(INK * (1 - rim) + (VIO * 0.6 + LIL * 0.4) * rim * streak, a * ss(0.0, 0.06, y7), "T_Spike.png")

if PACK == 'reseal':
    # chain of light (U along the chain): oval links alternating face-on / edge-on
    W8, H8 = 512, 64
    def chain(d, S):
        pass
    im = Image.new("L", (W8 * 3, H8 * 3), 0); dd = ImageDraw.Draw(im); n = 8; Lw = W8 * 3 / n
    for k in range(n):
        x0 = k * Lw
        if k % 2 == 0: dd.ellipse([x0 - Lw * 0.12, H8 * 3 * 0.22, x0 + Lw * 1.12, H8 * 3 * 0.78], outline=255, width=14)
        else: dd.rounded_rectangle([x0 - Lw * 0.15, H8 * 3 * 0.44, x0 + Lw * 1.15, H8 * 3 * 0.56], radius=12, fill=255)
    core = np.asarray(im.resize((W8, H8), Image.LANCZOS), float) / 255
    gg = np.asarray(im.filter(ImageFilter.GaussianBlur(16)).resize((W8, H8), Image.LANCZOS), float) / 255; gg /= gg.max()
    vv8 = np.linspace(-1, 1, H8)[:, None] * np.ones((1, W8))
    beam = np.exp(-(vv8 / 0.12) ** 2) * 0.12
    save(ramp(np.clip(core * 0.9 + gg * 0.3 + beam, 0, 1)), np.clip(core + gg * 0.7 + beam, 0, 1), "T_Chain.png")
    # light pillar (vertical soft column, V up)
    W9, H9 = 64, 256
    x9 = np.linspace(-1, 1, W9)[None, :]; y9 = np.linspace(1, 0, H9)[:, None]
    n9 = smooth_noise(W9, H9, 8, 80, 3, 91)
    col = np.exp(-(x9 / 0.18) ** 2) + np.exp(-(x9 / 0.5) ** 2) * 0.35
    fade = ss(0.0, 0.08, y9) * (1 - ss(0.45, 1.0, y9))
    save(ramp(np.clip(np.exp(-(x9 / 0.15) ** 2) * (0.8 + 0.2 * n9), 0, 1)), np.clip(col * fade * (0.75 + 0.35 * n9), 0, 1), "T_Pillar.png")
    # dark ink brush stroke (U along, used on spiral ribbons, alpha-blended): ink body, faint violet edge
    W10, H10 = 256, 48
    x10 = np.linspace(0, 1, W10)[None, :]; y10 = np.linspace(-1, 1, H10)[:, None]
    n10 = smooth_noise(W10, H10, 70, 5, 3, 101)
    prof = np.sin(np.pi * x10) ** 0.6 * (0.3 + 0.7 * x10)
    dist = 0.75 * prof - np.abs(y10) + (n10 - 0.5) * 0.3
    a = ss(-0.02, 0.1, dist) * (0.55 + 0.45 * n10)
    rim = np.exp(-(np.clip(dist, 0, None) / 0.08) ** 2)[..., None]
    save(INK * (1 - rim) + VIO * 0.45 * rim, a, "T_InkStroke.png")

# ---------- central sigils ----------
def sigil_offering(d, S):
    c = S / 2; R = S * 0.46
    def circ(r, w): d.ellipse([c - r, c - r, c + r, c + r], outline=255, width=w)
    circ(R, 7); circ(R * 0.9, 3); circ(R * 0.42, 5)
    for k in range(12):  # ticks
        a = k / 12 * 2 * math.pi; r0, r1 = R * 0.9, R * (0.78 if k % 3 == 0 else 0.84)
        d.line([(c + math.cos(a) * r0, c + math.sin(a) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1)], fill=255, width=6)
    for k in range(3):  # three crescents (phases) around
        a = k / 3 * 2 * math.pi - math.pi / 2; px, py = c + math.cos(a) * R * 0.62, c + math.sin(a) * R * 0.62; rr_ = R * 0.13
        d.ellipse([px - rr_, py - rr_, px + rr_, py + rr_], outline=255, width=6)
        d.ellipse([px - rr_ * 0.55, py - rr_ * 0.55, px + rr_ * 0.55, py + rr_ * 0.55], fill=255)
    # central mask-like eye: almond + three scratches
    w_, h_ = R * 0.34, R * 0.17
    d.polygon([(c - w_, c), (c, c - h_), (c + w_, c), (c, c + h_)], outline=255, width=6)
    for k in range(3):
        o = (k - 1) * R * 0.09
        d.line([(c - R * 0.2 + o, c - R * 0.3), (c + R * 0.2 + o, c + R * 0.3)], fill=255, width=7)
def sigil_reseal(d, S):
    c = S / 2; R = S * 0.46
    def circ(r, w): d.ellipse([c - r, c - r, c + r, c + r], outline=255, width=w)
    circ(R, 8); circ(R * 0.93, 3); circ(R * 0.55, 6); circ(R * 0.2, 5)
    for k in range(24):
        a = k / 24 * 2 * math.pi; r0, r1 = R * 0.93, R * (0.8 if k % 6 == 0 else 0.87)
        d.line([(c + math.cos(a) * r0, c + math.sin(a) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1)], fill=255, width=6)
    for k in range(4):  # key / cross arms (seal)
        a = k / 4 * 2 * math.pi
        d.line([(c + math.cos(a) * R * 0.2, c + math.sin(a) * R * 0.2), (c + math.cos(a) * R * 0.78, c + math.sin(a) * R * 0.78)], fill=255, width=8)
        px, py = c + math.cos(a) * R * 0.66, c + math.sin(a) * R * 0.66; b = a + math.pi / 2
        d.line([(px + math.cos(b) * R * 0.09, py + math.sin(b) * R * 0.09), (px - math.cos(b) * R * 0.09, py - math.sin(b) * R * 0.09)], fill=255, width=7)
    for k in range(4):
        a = (k + 0.5) / 4 * 2 * math.pi; px, py = c + math.cos(a) * R * 0.4, c + math.sin(a) * R * 0.4; rr_ = R * 0.06
        d.ellipse([px - rr_, py - rr_, px + rr_, py + rr_], fill=255)
if PACK == 'offering':
    rgb, a = lineart(384, sigil_offering, glow=5); save(rgb, a, "T_Sigil.png")
if PACK == 'reseal':
    rgb, a = lineart(384, sigil_reseal, glow=5); save(rgb, a, "T_Sigil.png")
print("ok", PACK)
# dark ink line profile (alpha-blended over light): wide, ragged
W11, H11 = 128, 32
v11 = np.linspace(-1, 1, H11)[:, None] * np.ones((1, W11)); n11 = smooth_noise(W11, H11, 12, 8, 3, 111)
a11 = ss(0.0, 0.25, 0.85 - np.abs(v11) - (n11 - 0.5) * 0.35)
rim11 = np.exp(-((np.abs(v11) - 0.75) / 0.12) ** 2)[..., None]
save(INK * (1 - rim11) + VIO * 0.35 * rim11, a11, "T_InkLine.png")
