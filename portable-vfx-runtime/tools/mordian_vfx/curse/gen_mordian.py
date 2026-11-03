# Mordian (모르디안의 원령) shared texture library: abyss ink + violet glow, three-line scratch motif.
# usage: python gen_mordian.py <outdir>
import sys, os, numpy as np
from PIL import Image, ImageFilter
OUT = (sys.argv[1] if len(sys.argv) > 1 else ".") + "/"
os.makedirs(OUT, exist_ok=True)

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
def blur(x, r):
    return np.asarray(Image.fromarray((np.clip(x, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)), float) / 255

# violet ramp: heat 1 → white lilac, → lilac → main violet → deep violet → shadow violet
VIO = [(1.00, (0.96, 0.92, 1.00)), (0.78, (0.85, 0.62, 1.00)), (0.55, (0.60, 0.30, 1.00)),
       (0.30, (0.36, 0.13, 0.75)), (0.00, (0.16, 0.08, 0.26))]
def ramp(h, stops=VIO):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
INK = np.array([0.035, 0.024, 0.055]); SHD = np.array([0.165, 0.086, 0.25]); LIL = np.array([0.84, 0.59, 1.0]); VIOc = np.array([0.54, 0.235, 1.0])

def grid(N, lim=1.0):
    Y, X = np.mgrid[-lim:lim:N*1j, -lim:lim:N*1j]; R = np.hypot(X, Y) + 1e-6; return X, Y, R, np.arctan2(Y, X)
def ringl(R, r0, w): return np.exp(-((R - r0) / w) ** 2)
def segs(X, Y, S, w, taper=False):
    out = np.zeros_like(X)
    for sg in S:
        ax, ay, bx, by = sg[:4]
        dx, dy = bx - ax, by - ay; L2 = dx*dx + dy*dy + 1e-9
        t = np.clip(((X - ax) * dx + (Y - ay) * dy) / L2, 0, 1)
        d = np.hypot(X - ax - t*dx, Y - ay - t*dy)
        ww = w * (np.sin(np.pi * t) ** 0.6 + 0.08) if taper else w
        out = np.maximum(out, np.exp(-(d / ww) ** 2))
    return out
GLYPHS = [
    [(-0.5,-0.9,-0.5,0.9),(-0.5,0.9,0.5,0.3),(-0.5,0.0,0.5,0.3),(0.5,0.3,0.5,-0.9)],
    [(0,-0.9,0,0.9),(-0.6,0.5,0,0.9),(0.6,0.5,0,0.9),(-0.5,-0.2,0.5,-0.2)],
    [(-0.6,-0.9,0.6,0.9),(0.6,-0.9,-0.6,0.9),(-0.6,0.9,0.6,0.9)],
    [(-0.5,-0.9,-0.5,0.9),(0.5,-0.9,0.5,0.9),(-0.5,0.4,0.5,-0.4)],
    [(0,-0.9,0,0.9),(0,0.2,0.6,0.8),(0,-0.3,-0.6,0.3),(-0.6,-0.9,0.6,-0.9)],
    [(-0.6,0.9,0,-0.9),(0,-0.9,0.6,0.9),(-0.3,0.0,0.3,0.0)],
    [(-0.55,-0.9,0.55,0.9),(-0.15,-0.9,0.95,0.9),(-0.95,-0.9,0.15,0.9)],   # three-line scratch rune (signature)
    [(0,-0.9,0,0.9),(-0.6,-0.4,0.6,-0.4),(-0.6,0.4,0.6,0.4),(-0.6,-0.4,-0.6,0.4)],
]
def glyph_at(X, Y, cx, cy, ang, sc, g, w):
    c, s = np.cos(ang), np.sin(ang); u = ((X - cx) * c + (Y - cy) * s) / sc; v = (-(X - cx) * s + (Y - cy) * c) / sc
    m = (np.abs(u) < 1.3) & (np.abs(v) < 1.3); out = np.zeros_like(X)
    if m.any(): out[m] = segs(u[m], v[m], GLYPHS[g], w / sc)
    return out
def folded(N, n):
    X, Y, R, T = grid(N); sec = 2 * np.pi / n; Tf = (T + sec / 2) % sec - sec / 2
    return R * np.cos(Tf), R * np.sin(Tf), R, T
def finish(lines, R, name, rmax=0.985, glow=0.45, out=None, br=None):
    n = smooth_noise(lines.shape[1], lines.shape[0], 25, 25, 3, 7)
    g = blur(lines, br or lines.shape[0] / 90)
    heat = np.clip(lines * (0.85 + 0.3 * n), 0, 1)
    a = np.clip(lines + g * glow, 0, 1) * (1 - ss(rmax - 0.02, rmax, R))
    save(ramp(0.25 + 0.75 * np.maximum(heat, g * 0.55)), a, name, out)

# ---------------- T_Line: mesh line profile (U along, V across) ----------------
v = np.linspace(-1, 1, 32)[:, None] * np.ones((1, 16))
core = np.exp(-(v / 0.22) ** 2); halo = np.exp(-(v / 0.6) ** 2) * 0.55
save(ramp(0.35 + 0.65 * np.clip(core + halo * 0.3, 0, 1)), np.clip(core + halo, 0, 1) * (1 - ss(0.9, 1, np.abs(v))), "T_Line.png")

# ---------------- T_RuneStrip 512x128: glyph band between two border lines (U along the band) ----------------
W, H = 1536, 384
Y, X = np.mgrid[0:H, 0:W].astype(float); X /= H; Y = Y / H * 2 - 1      # X in cell units (height = 1), Y -1..1
L = np.exp(-((Y - 0.86) / 0.022) ** 2) * 0.9 + np.exp(-((Y + 0.86) / 0.022) ** 2) * 0.9
order = [6, 0, 3, 7, 1, 6, 4, 2, 5, 6, 3, 1]
nG = len(order); cw = (W / H) / nG
for i, g in enumerate(order):
    cx = (i + 0.5) * cw
    u = (X - cx) / 0.42 * 2; m = np.abs(u) < 1.4
    gl = np.zeros_like(X); gl[m] = segs(u[m], Y[m] / 0.6 * 1.0, GLYPHS[g], 0.075)
    L = np.maximum(L, gl * (1.0 if g == 6 else 0.85))
    # dots between glyphs
    L = np.maximum(L, np.exp(-(((X - (i + 1.0) * cw) / 0.03) ** 2 + (Y / 0.06) ** 2)) * 0.8)
g = blur(L, 6)
a = np.clip(L + g * 0.55, 0, 1)
save(ramp(0.3 + 0.7 * np.maximum(L, g * 0.5)), a, "T_RuneStrip.png", (512, 128))

# ---------------- T_SealRing 256 (12-fold symmetric): brand seal outer ring ----------------
N = 768
Xf, Yf, R, T = folded(N, 12)
L = ringl(R, 0.95, 0.009) * 1.0 + ringl(R, 0.89, 0.005) * 0.7 + ringl(R, 0.66, 0.007) * 0.9
L = np.maximum(L, glyph_at(Xf, Yf, 0.775, 0, np.pi / 2, 0.075, 6, 0.012))
L = np.maximum(L, ringl(np.hypot(Xf - 0.92 * np.cos(np.pi/12 - 1e-4), Yf - 0.92 * np.sin(np.pi/12 - 1e-4)), 0.02, 0.008))
# outward thorns on the outer ring
L = np.maximum(L, segs(Xf, Yf, [(0.95, -0.03, 1.0, 0.0), (0.95, 0.03, 1.0, 0.0)], 0.006) * 0.9)
finish(np.clip(L, 0, 1.2), R, "T_SealRing.png", out=(256, 256))

# ---------------- T_SealCore 256: inner emblem — circle, inverted triangle, three-scratch slash ----------------
X, Y, R, T = grid(N)
tri = [(0.52 * np.cos(np.pi/2 + k * 2*np.pi/3), -0.52 * np.sin(np.pi/2 + k * 2*np.pi/3)) for k in range(3)]
L = ringl(R, 0.58, 0.008) * 0.85 + ringl(R, 0.12, 0.012) * 0.6
L = np.maximum(L, segs(X, Y, [(*tri[k], *tri[(k + 1) % 3]) for k in range(3)], 0.008) * 0.75)
sl = [(-0.42 + o, 0.55, 0.12 + o, -0.6) for o in (-0.2, 0.0, 0.2)]   # three parallel diagonal scratches
S3 = segs(X, Y, sl, 0.04, taper=True)
L = np.maximum(L, S3 * 1.15)
finish(np.clip(L, 0, 1.25), R, "T_SealCore.png", out=(256, 256), glow=0.6)

# ---------------- T_Scratch 256: three parallel diagonal claw scratches (additive, signature) ----------------
X, Y, R, T = grid(N)
nn = smooth_noise(N, N, 12, 12, 2, 31)
sl = [(-0.55 + o, 0.78, 0.35 + o, -0.8) for o in (-0.3, 0.0, 0.3)]
S3 = segs(X + (nn - 0.5) * 0.02, Y, sl, 0.065, taper=True)
core = np.clip(S3 * 1.3 - 0.15, 0, 1)
g = blur(core, 14)
save(ramp(0.3 + 0.7 * np.maximum(core, g * 0.5)), np.clip(core + g * 0.7, 0, 1) * (1 - ss(0.95, 1.0, np.maximum(np.abs(X), np.abs(Y)))), "T_Scratch.png", (256, 256))

# ---------------- T_SlashShard 256x64: one torn slash fragment, ink body + lilac rim (alpha) ----------------
W, H = 768, 192
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
nS = smooth_noise(W, H, 40, 30, 3, 41)
prof = np.sin(np.pi * x) ** 0.8 * (0.35 + 0.65 * x)
dist = 0.62 * prof - (np.abs(y + 0.15 * np.sin(x * 3)) + (nS - 0.5) * 0.25)
a = ss(-0.03, 0.02, dist)
edge = np.exp(-(np.clip(dist, 0, None) / 0.06) ** 2)
rgb = INK[None, None, :] * (1 - edge[..., None]) + LIL[None, None, :] * edge[..., None]
save(rgb, a, "T_SlashShard.png", (256, 64))

# ---------------- glow / dot / star ----------------
S = 128; X, Y, R, T = grid(S)
gl = np.exp(-(R / 0.35) ** 2) * 0.85 + np.exp(-(R / 0.75) ** 2) * 0.25; gl *= 1 - ss(0.9, 1.0, R)
save(np.ones((S, S, 3)), gl, "T_Glow.png")
S = 64; X, Y, R, T = grid(S)
dd = np.exp(-(R / 0.3) ** 2) + (1 - ss(0.08, 0.16, R))
save(ramp(0.6 + 0.4 * np.exp(-(R / 0.25) ** 2)), np.clip(dd, 0, 1) * (1 - ss(0.85, 1, R)), "T_Dot.png")
S = 256; X, Y, R, T = grid(S); ax, ay = np.abs(X), np.abs(Y)
spike = np.exp(-ay / (0.02 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (0.02 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
d45 = np.abs(X + Y) / 1.414, np.abs(X - Y) / 1.414
spike2 = (np.exp(-d45[0] / 0.012) + np.exp(-d45[1] / 0.012)) * np.clip(1 - R / 0.55, 0, 1) ** 2 * 0.6
glw = np.exp(-(R / 0.14) ** 2) * 0.9 + np.exp(-(R / 0.45) ** 2) * 0.3
heat = np.clip(spike * 1.2 + spike2 + np.exp(-(R / 0.1) ** 2), 0, 1)
save(ramp(0.4 + 0.6 * heat), np.clip(spike + spike2 + glw, 0, 1), "T_Star.png", (128, 128))

# ---------------- T_Streak 256x32 (head at right): rays, sparks, arc strokes ----------------
W, H = 512, 64
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
taper = x ** 1.4 * (1 - ss(0.93, 1.0, x))
core = np.exp(-(dy / (0.1 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.5 * taper + 1e-3)) ** 2) * 0.5 * taper
save(ramp(0.3 + 0.7 * np.clip(core + halo * 0.4, 0, 1)), np.clip(core + halo, 0, 1), "T_Streak.png", (256, 32))

# ---------------- T_InkMist 128: translucent dark mist (alpha) ----------------
S = 256; X, Y, R, T = grid(S)
n1 = smooth_noise(S, S, 50, 50, 4, 61); n2 = smooth_noise(S, S, 18, 18, 3, 62)
body = 1 - R / 0.85 + (n1 - 0.5) * 0.6 + (n2 - 0.5) * 0.2
a = ss(0.0, 0.6, body) * 0.75
rim = np.exp(-((body - 0.08) / 0.1) ** 2) * 0.35
save(INK[None, None, :] * (1 - rim[..., None]) + SHD[None, None, :] * 1.6 * rim[..., None], a, "T_InkMist.png", (128, 128))

# ---------------- T_InkSplash_Sheet 512 (4x4 of 128): spiky ink crown burst, ink body + violet rim (alpha) ----------------
C = 256; X, Y, R, T = grid(C)
nA = smooth_noise(C, C, 40, 40, 4, 71); nB = smooth_noise(C, C, 14, 14, 3, 72)
rng = np.random.default_rng(7)
spk_ang = rng.uniform(0, 2*np.pi, 14); spk_len = rng.uniform(0.55, 1.0, 14); spk_w = rng.uniform(0.12, 0.22, 14)
def splash_cell(t):
    grow = 0.25 + 0.75 * (1 - (1 - min(1, t / 0.45)) ** 2)
    rad = 0.42 * grow * (1 + (nA - 0.5) * 0.5)
    field = rad - R
    for a0, l0, w0 in zip(spk_ang, spk_len, spk_w):
        da = np.angle(np.exp(1j * (T - a0)))
        L_ = l0 * grow * 0.92
        w = w0 * (1 - np.clip(R / max(L_, 1e-3), 0, 1)) ** 1.2
        field = np.maximum(field, (w - np.abs(da) * R) * (R < L_) * 1.0 - (R >= L_) * 1.0)
    # droplets beyond spikes
    for a0, l0 in zip(spk_ang[::2], spk_len[::2]):
        px, py = np.cos(a0 + 0.1) * (l0 * grow + 0.05), np.sin(a0 + 0.1) * (l0 * grow + 0.05)
        field = np.maximum(field, 0.035 * (1 - t * 0.6) - np.hypot(X - px, Y - py))
    hole = ss(0.35, 1.0, t) * (0.45 + 0.6 * nB)       # breaks apart from the inside
    field = field - np.clip(hole * (0.6 - R) * 0.8, 0, None) - ss(0.6, 1.0, t) * 0.12 * (0.5 + nB)
    a = ss(-0.01, 0.015, field)
    edge = np.exp(-(np.clip(field, 0, None) / 0.03) ** 2)
    lit = edge * (1 - 0.6 * t)
    rgb = INK[None, None, :] * (1 - lit[..., None]) + (LIL * 0.9)[None, None, :] * lit[..., None]
    return rgb, a * (1 - ss(0.94, 1.0, R))
rgbS = np.zeros((C*4, C*4, 3)); aS = np.zeros((C*4, C*4))
for i in range(16):
    r, c = divmod(i, 4); rgb, a = splash_cell(i / 15)
    rgbS[r*C:(r+1)*C, c*C:(c+1)*C] = rgb; aS[r*C:(r+1)*C, c*C:(c+1)*C] = a
save(rgbS, aS, "T_InkSplash_Sheet.png", (512, 512))

# ---------------- T_InkSpike 64x256: tall thorn, ink body + lilac rim, base fades (alpha; V up) ----------------
W, H = 192, 768
x = np.linspace(-1, 1, W)[None, :]; yv = np.linspace(1, 0, H)[:, None]   # row 0 = top (tip)
nK = smooth_noise(W, H, 20, 120, 3, 81)
half = 0.75 * (1 - yv) ** 0.9 + 0.02
dist = half - np.abs(x + (nK - 0.5) * 0.25 * (1 - yv))
a = ss(-0.03, 0.03, dist) * ss(0.0, 0.12, yv)
edge = np.exp(-(np.clip(dist, 0, None) / 0.05) ** 2)
streak = smooth_noise(W, H, 8, 150, 2, 82)
lit = np.clip(edge * 0.85 + np.clip(streak - 0.62, 0, 1) * 0.6 * (1 - edge), 0, 1) * (0.35 + 0.65 * (1 - yv))
rgb = INK[None, None, :] * (1 - lit[..., None]) + LIL[None, None, :] * lit[..., None]
save(rgb, a, "T_InkSpike.png", (64, 256))

# ---------------- T_Pillar 128x512: torn black ink column with violet rim (alpha; V up) ----------------
W, H = 256, 1024
x = np.linspace(-1, 1, W)[None, :]; yv = np.linspace(1, 0, H)[:, None]
nP = smooth_noise(W, H, 30, 140, 4, 91); nQ = smooth_noise(W, H, 12, 60, 3, 92)
half = 0.55 + 0.25 * (1 - yv) ** 2 - 0.35 * ss(0.7, 1.0, yv) + (nP - 0.5) * 0.35
top = ss(0.0, 0.08, 0.93 - yv + (nQ - 0.5) * 0.25)      # torn top edge
dist = np.minimum(half - np.abs(x), top * 0.3)
a = ss(-0.02, 0.03, dist) * ss(0.0, 0.05, yv)
edge = np.exp(-(np.clip(dist, 0, None) / 0.09) ** 2)
streak = smooth_noise(W, H, 6, 200, 2, 93)
edge = np.exp(-(np.clip(dist, 0, None) / 0.05) ** 2)
lit = np.clip(edge * 0.9 + np.clip(streak - 0.66, 0, 1) * 0.7 * (1 - edge), 0, 1)
base = ss(0.18, 0.0, yv) * 0.3
lit = np.clip(lit + base, 0, 1)
rgb = INK[None, None, :] * (1 - lit[..., None]) + LIL[None, None, :] * lit[..., None]
save(rgb, a, "T_Pillar.png", (128, 512))

# ---------------- T_Shock 256: ground shock ring (sharp outer edge, soft inner wash, radial streaks) ----------------
N = 512; X, Y, R, T = grid(N)
nr = smooth_noise(N, N, 6, 6, 2, 101)
ang = (T + np.pi) / (2 * np.pi)
radial = np.asarray(Image.fromarray((smooth_noise(512, 8, 6, 8, 2, 102) * 255).astype(np.uint8)).resize((512, 8)), float)[4] / 255
rs = radial[(ang * 511).astype(int)]
edge = np.exp(-((R - 0.9) / 0.018) ** 2)
wash = ss(0.35, 0.9, R) * (R < 0.9) * (0.45 + 0.55 * rs) * 0.55
L = np.clip(edge + wash, 0, 1)
save(ramp(0.25 + 0.75 * np.clip(edge + wash * 0.4, 0, 1)), np.clip(L + blur(edge, 6) * 0.4, 0, 1) * (1 - ss(0.95, 1.0, R)), "T_Shock.png", (256, 256))

# ---------------- T_InkSplat 256: ground ink splat decal with scratch scars and violet rim (alpha) ----------------
N = 512; X, Y, R, T = grid(N)
n1 = smooth_noise(N, N, 60, 60, 4, 111); n2 = smooth_noise(N, N, 16, 16, 3, 112)
field = 0.55 + (n1 - 0.5) * 0.45 - R
for k in range(16):
    a0 = k * 2 * np.pi / 16 + (n1[k*7 % N, k*13 % N] - 0.5)
    da = np.angle(np.exp(1j * (T - a0)))
    L_ = 0.7 + 0.25 * np.sin(k * 2.3)
    field = np.maximum(field, (0.07 * (1 - R / L_) - np.abs(da) * R * 0.5) * (R < L_) - (R >= L_))
a = ss(-0.01, 0.02, field) * (0.85 + 0.15 * n2)
edge = np.exp(-(np.clip(field, 0, None) / 0.03) ** 2) * 0.55
sl = [(-0.42 + o, 0.42, 0.22 + o, -0.45) for o in (-0.18, 0.0, 0.18)]
scar = segs(X, Y, sl, 0.022, taper=True)
lit = np.clip(edge + scar * 1.0, 0, 1)
rgb = INK[None, None, :] * (1 - lit[..., None]) + LIL[None, None, :] * lit[..., None]
save(rgb, np.clip(a + scar * 0.5, 0, 1) * (1 - ss(0.95, 1, R)), "T_InkSplat.png", (256, 256))

# ---------------- T_ForbidSigil 256 (centre seal of the forbidden ring) ----------------
N = 768; X, Y, R, T = grid(N)
p8 = [(0.78 * np.cos(k * np.pi / 4), 0.78 * np.sin(k * np.pi / 4)) for k in range(8)]
L = ringl(R, 0.95, 0.008) + ringl(R, 0.88, 0.005) * 0.7 + ringl(R, 0.78, 0.006) * 0.85 + ringl(R, 0.3, 0.006) * 0.8
L = np.maximum(L, segs(X, Y, [(*p8[k], *p8[(k + 3) % 8]) for k in range(8)], 0.006) * 0.7 * (R < 0.79))
sl = [(-0.32 + o, 0.42, 0.1 + o, -0.42) for o in (-0.15, 0.0, 0.15)]
L = np.maximum(L, segs(X, Y, sl, 0.03, taper=True) * 1.2)
Xf, Yf, _, _ = folded(N, 16)
L = np.maximum(L, glyph_at(Xf, Yf, 0.835, 0, np.pi / 2, 0.035, 6, 0.007) * 0.9)
finish(np.clip(L, 0, 1.25), R, "T_ForbidSigil.png", out=(256, 256))

# ---------------- T_Wisp 64x256: thin rising ink tendril (alpha, V up) ----------------
W, H = 128, 512
x = np.linspace(-1, 1, W)[None, :]; yv = np.linspace(1, 0, H)[:, None]
nw = smooth_noise(W, H, 30, 100, 3, 121)
cx = np.sin(yv * 7) * 0.25 + (nw - 0.5) * 0.3
half = 0.32 * np.sin(np.pi * yv) ** 0.7 + 0.02
dist = half - np.abs(x - cx)
a = ss(-0.03, 0.04, dist) * 0.85
edge = np.exp(-(np.clip(dist, 0, None) / 0.06) ** 2) * 0.7
rgb = INK[None, None, :] * (1 - edge[..., None]) + VIOc[None, None, :] * edge[..., None]
save(rgb, a, "T_Wisp.png", (64, 256))
print("ok")

# ---------------- T_Fade 16x64: bright bottom (V=0) fading upward (rim walls), T_*H rotated copies for stretched billboards ----------------
yv = np.linspace(1, 0, 64)[:, None] * np.ones((1, 16))
a = (1 - yv) ** 1.8 * 0.9 + np.exp(-(yv / 0.06) ** 2) * 0.4
save(ramp(0.35 + 0.65 * (1 - yv) ** 2), np.clip(a, 0, 1), "T_Fade.png")
Image.open(OUT + "T_InkSpike.png").rotate(-90, expand=True).save(OUT + "T_InkSpikeH.png")
Image.open(OUT + "T_Wisp.png").rotate(-90, expand=True).save(OUT + "T_WispH.png")
print("ok2")
