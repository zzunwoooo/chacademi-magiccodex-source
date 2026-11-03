# Mordian chain / beam textures (shared look for MordianChainPull, MordianChainBind, MordianSealBeam).
# usage: python gen_mordian_chain.py <outdir>
import sys, os, numpy as np
from PIL import Image, ImageDraw, ImageFilter
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
def save(rgb, a, name):
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]]) * 255).astype(np.uint8), "RGBA").save(OUT + name)
# violet ramp: 1 = white-lilac core → lilac → main violet → shadow violet → abyss ink
RAMP = [(1.00, (0.98, 0.94, 1.00)), (0.78, (0.86, 0.62, 1.00)), (0.55, (0.62, 0.30, 1.00)),
        (0.32, (0.40, 0.14, 0.78)), (0.14, (0.18, 0.07, 0.30)), (0.00, (0.05, 0.03, 0.08))]
def ramp(h, stops=RAMP):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
INK = np.array([0.035, 0.024, 0.055]); SHAD = np.array([0.16, 0.09, 0.25]); VIO = np.array([0.54, 0.24, 1.0])
GOLD_D = np.array([0.30, 0.22, 0.10]); GOLD = np.array([0.55, 0.44, 0.23]); GOLD_H = np.array([0.86, 0.74, 0.46])

# ---------- chain link (128x64, u = along the chain): ink body, faded-gold rim, violet inner sheen. alpha material, tint ~1.5
W, H = 128, 64
yy, xx = np.mgrid[-1:1:H*1j, -1:1:W*1j]
# superellipse oval ring: outer radii (0.96, 0.92), hole (0.56, 0.38)
def sell(x, y, a, b, p=2.6): return (np.abs(x / a) ** p + np.abs(y / b) ** p) ** (1 / p)
ro = sell(xx, yy, 0.97, 0.90); ri = sell(xx, yy, 0.62, 0.36)
body = ss(1.0, 0.94, ro) * ss(1.0, 1.08, ri)
nL = smooth_noise(W, H, 10, 10, 3, 5)
rim_o = np.exp(-((ro - 0.93) / 0.05) ** 2); rim_i = np.exp(-((ri - 1.04) / 0.07) ** 2)
top = ss(0.3, -0.8, yy)                      # light from above: upper half of the gold brighter
gold = np.clip(rim_o * (0.55 + 0.6 * top) + rim_i * 0.55, 0, 1) * (0.7 + 0.5 * nL)
midband = np.exp(-((ro + ri) * 0.5 - 0.95) ** 2 / 0.02)   # dull centre of the metal = ink
rgb = INK * (1 - gold[..., None]) + (GOLD_D * (1 - top[..., None]) + GOLD_H * top[..., None]) * gold[..., None]
rgb = rgb + VIO * 0.25 * np.exp(-((ri - 1.12) / 0.05) ** 2)[..., None]       # violet sheen on the inner lip
save(rgb, body * (0.92 + 0.08 * nL), "T_ChainLink.png")
# edge-on link (128x32): a dark bar with gold top edge
W2, H2 = 128, 32
yb, xb = np.mgrid[-1:1:H2*1j, -1:1:W2*1j]
rb = sell(xb, yb, 0.97, 0.62, 3.0)
bb = ss(1.0, 0.9, rb)
gb = np.exp(-((rb - 0.88) / 0.07) ** 2) * (0.5 + 0.5 * ss(0.4, -0.7, yb)) * (0.7 + 0.5 * smooth_noise(W2, H2, 10, 10, 2, 6))
save(INK * (1 - gb[..., None]) + GOLD_H * gb[..., None], bb, "T_ChainBar.png")

# ---------- shadow band (256x64, u along length): translucent ink wrap with brush strands, violet rim; alpha, tint ~2
W, H = 256, 64
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
strands = smooth_noise(W, H, 90, 5, 3, 11); nB = smooth_noise(W, H, 40, 30, 3, 12)
taper = np.sin(np.pi * x) ** 0.5
edge = 0.62 * taper * (0.85 + 0.3 * (nB - 0.5))
inside = ss(0.0, 0.12, edge - dy)
rimv = np.exp(-((dy - edge) / 0.09) ** 2) * taper
a = np.clip(inside * (0.55 + 0.4 * strands) + rimv * 0.7, 0, 1)
rgb = INK * (1 - rimv[..., None]) + VIO * 0.42 * rimv[..., None] + SHAD * 0.4 * (strands * inside)[..., None]
save(rgb, a, "T_ShadowBand.png")

# ---------- ink ribbon (256x32): thin dark ribbon, violet rim; alpha tint ~2.2 (dark body, glowing rim)
W, H = 256, 32
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
nI = smooth_noise(W, H, 50, 12, 3, 21)
prof = np.sin(np.pi * x) ** 0.8 * (0.35 + 0.65 * x)          # head at the right
edge = 0.75 * prof + (nI - 0.5) * 0.25
inside = ss(0.0, 0.1, edge - dy)
rim = np.exp(-((dy - edge) / 0.12) ** 2) * prof
save(INK * (1 - rim[..., None]) + VIO * 0.45 * rim[..., None], np.clip(inside + rim * 0.6, 0, 1), "T_InkRibbon.png")

# ---------- violet streak (256x32 additive, head right)
x = np.linspace(0, 1, W)[None, :]
taper = x ** 1.4 * (1 - ss(0.9, 1.0, x))
core = np.exp(-(dy / (0.12 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.55 * taper + 1e-3)) ** 2) * 0.5 * taper
heat = np.clip(core + halo * 0.4, 0, 1)
save(ramp(0.3 + 0.7 * heat), np.clip(core + halo, 0, 1), "T_VioletStreak.png")

# ---------- lightning arcs: 1x4 flipbook (each 256x64 row = one jagged bolt) additive
W, H = 256, 64; rows = []
rgS = np.zeros((H * 4, W, 3)); aS = np.zeros((H * 4, W))
for k in range(4):
    S = 3; im = Image.new("L", (W * S, H * S), 0); d = ImageDraw.Draw(im)
    rng = np.random.default_rng(100 + k)
    pts = [(0, H * S / 2)]; n = 14
    for i in range(1, n + 1):
        px = W * S * i / n; py = H * S / 2 + rng.uniform(-0.32, 0.32) * H * S * np.sin(np.pi * i / n) ** 0.5
        pts.append((px, py))
    d.line(pts, fill=255, width=5 * S // 2)
    for j in range(3):                       # forks
        i0 = rng.integers(2, n - 3); p0 = pts[i0]
        q = [p0]
        for m in range(3):
            q.append((q[-1][0] + rng.uniform(12, 22) * S, q[-1][1] + rng.uniform(-14, 14) * S))
        d.line(q, fill=170, width=3 * S // 2)
    core = np.asarray(im.filter(ImageFilter.GaussianBlur(1.2 * S)).resize((W, H), Image.LANCZOS), float) / 255
    glow = np.asarray(im.filter(ImageFilter.GaussianBlur(7 * S)).resize((W, H), Image.LANCZOS), float) / 255
    xr = np.linspace(0, 1, W)[None, :]; fade = ss(0, 0.08, xr) * ss(1, 0.92, xr)
    heat = np.clip(core * 1.4 + glow * 0.8, 0, 1)
    rgS[k*H:(k+1)*H] = ramp(0.35 + 0.65 * heat); aS[k*H:(k+1)*H] = np.clip(core * 1.3 + glow * 1.6, 0, 1) * fade
save(rgS, aS, "T_Lightning.png")

# ---------- shards: ink shard with violet rim, link shard with gold rim (64x128? → 128x64, head right)
W, H = 128, 64
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
nS = smooth_noise(W, H, 20, 20, 3, 41)
profS = np.sin(np.pi * x) ** 0.7 * (0.45 + 0.55 * x)
dist = 0.6 * profS - (dy + (nS - 0.5) * 0.3)
a = ss(-0.03, 0.02, dist); edge = np.exp(-(np.clip(dist, 0, None) / 0.07) ** 2)[..., None]
save(INK * (1 - edge) + VIO * 0.5 * edge, a, "T_InkShard.png")
save(INK * (1 - edge) + GOLD_H * 0.7 * edge, a, "T_LinkShard.png")

# ---------- star / glow / dot / ring (additive, white-ish; colour comes from tint/colour curves)
S = 128
yy2, xx2 = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r2 = np.hypot(xx2, yy2) + 1e-6
ax, ay = np.abs(xx2), np.abs(yy2)
spike = np.exp(-ay / (0.03 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (0.03 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
glow = np.exp(-(r2 / 0.16) ** 2) * 0.9 + np.exp(-(r2 / 0.5) ** 2) * 0.3
heat = np.clip(spike * 1.2 + np.exp(-(r2 / 0.12) ** 2), 0, 1)
save(ramp(0.4 + 0.6 * heat), np.clip(spike + glow, 0, 1), "T_Star.png")
g = np.exp(-(r2 / 0.35) ** 2) * 0.85 + np.exp(-(r2 / 0.75) ** 2) * 0.25; g *= 1 - ss(0.9, 1.0, r2)
save(np.ones((S, S, 3)), g, "T_Glow.png")
S3 = 64; yy3, xx3 = np.mgrid[-1:1:S3*1j, -1:1:S3*1j]; r3 = np.hypot(xx3, yy3)
dd = np.exp(-(r3 / 0.3) ** 2) + (1 - ss(0.08, 0.16, r3))
save(np.ones((S3, S3, 3)), np.clip(dd, 0, 1) * (1 - ss(0.85, 1, r3)), "T_Dot.png")
S = 256
yy2, xx2 = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r2 = np.hypot(xx2, yy2) + 1e-6; ang = np.arctan2(yy2, xx2)
ring = np.exp(-((r2 - 0.86) / 0.022) ** 2) + 0.35 * np.exp(-((r2 - 0.8) / 0.08) ** 2)
save(ramp(0.45 + 0.55 * np.clip(ring, 0, 1)), np.clip(ring, 0, 1) * (1 - ss(0.96, 1.0, r2)), "T_ThinRing.png")
# shackle ring: thick band with 8 rune notches + inner thin ring (additive)
band = np.exp(-((r2 - 0.78) / 0.06) ** 4)
notch = (np.cos(ang * 8) > 0.86) * np.exp(-((r2 - 0.78) / 0.12) ** 2)
inner = np.exp(-((r2 - 0.6) / 0.018) ** 2) * (0.6 + 0.4 * (np.cos(ang * 24) > 0))
heat = np.clip(band * 0.7 + notch * 0.6 + inner, 0, 1)
save(ramp(0.3 + 0.7 * heat), np.clip(band * 0.8 + notch + inner + 0.35 * np.exp(-((r2 - 0.78) / 0.2) ** 2), 0, 1) * (1 - ss(0.96, 1, r2)), "T_ShackleRing.png")

# ---------- signature: three parallel diagonal scratches (256, additive)
S = 256; Sc = 3
im = Image.new("L", (S * Sc, S * Sc), 0); d = ImageDraw.Draw(im)
for k, off in enumerate([-0.24, 0.0, 0.24]):
    L = 0.72 - 0.08 * abs(k - 1)
    cx, cy = 0.5 + off * 0.7, 0.5 + off * 0.7
    a0 = (cx - L / 2 * 0.72, cy - L / 2 * -0.72 * -1)
    p0 = np.array([cx - 0.36 * L, cy + 0.36 * L]); p1 = np.array([cx + 0.36 * L, cy - 0.36 * L])
    # tapered stroke = polygon
    dvec = p1 - p0; nvec = np.array([-dvec[1], dvec[0]]) / np.linalg.norm(dvec)
    poly = []
    for t in np.linspace(0, 1, 24): w = 0.03 * np.sin(np.pi * t) ** 0.7 * (0.5 + 0.5 * t); poly.append(tuple(((p0 + dvec * t + nvec * w) * S * Sc)))
    for t in np.linspace(1, 0, 24): w = 0.03 * np.sin(np.pi * t) ** 0.7 * (0.5 + 0.5 * t); poly.append(tuple(((p0 + dvec * t - nvec * w) * S * Sc)))
    d.polygon(poly, fill=255)
core = np.asarray(im.filter(ImageFilter.GaussianBlur(1.0 * Sc)).resize((S, S), Image.LANCZOS), float) / 255
glow = np.asarray(im.filter(ImageFilter.GaussianBlur(9 * Sc)).resize((S, S), Image.LANCZOS), float) / 255
heat = np.clip(core * 1.3 + glow * 0.5, 0, 1)
save(ramp(0.3 + 0.7 * heat), np.clip(core * 1.2 + glow * 1.4, 0, 1), "T_Scratch3.png")

# ---------- crack decal (256, additive): branching cracks from the centre + the 3-scratch mark in the middle
S = 256; Sc = 2
im = Image.new("L", (S * Sc, S * Sc), 0); d = ImageDraw.Draw(im)
rng = np.random.default_rng(77)
def crack(p, ang, length, width, depth):
    pts = [p]
    for i in range(6):
        ang += rng.uniform(-0.3, 0.3); step = length / 6
        p = (p[0] + np.cos(ang) * step, p[1] + np.sin(ang) * step); pts.append(p)
        if depth > 0 and rng.random() < 0.35: crack(p, ang + rng.choice([-1, 1]) * rng.uniform(0.5, 1.0), length * 0.45, max(1, width - 2), depth - 1)
    d.line([(q[0] * Sc, q[1] * Sc) for q in pts], fill=255, width=int(width * Sc))
for k in range(10):
    a0 = k / 10 * 2 * np.pi + rng.uniform(-0.25, 0.25)
    crack((S / 2 + np.cos(a0) * 14, S / 2 + np.sin(a0) * 14), a0, rng.uniform(55, 85), 4, 2)
core = np.asarray(im.filter(ImageFilter.GaussianBlur(0.8 * Sc)).resize((S, S), Image.LANCZOS), float) / 255
glow = np.asarray(im.filter(ImageFilter.GaussianBlur(6 * Sc)).resize((S, S), Image.LANCZOS), float) / 255
yy2, xx2 = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r2 = np.hypot(xx2, yy2)
fall = 1 - ss(0.75, 1.0, r2)
heat = np.clip(core * 1.2 + glow * 0.4, 0, 1) * (1 - 0.4 * r2)
save(ramp(0.25 + 0.75 * heat), np.clip(core + glow * 1.2, 0, 1) * fall, "T_Crack.png")

# ---------- dark mist 4x4 sheet (64 px cells, 256 total) alpha translucent ink with violet underlight
C, G = 64, 4
yy, xx = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 18, 18, 3, 61); nB = smooth_noise(C, C, 9, 9, 3, 62)
rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
for i in range(G * G):
    t = i / (G * G - 1); r, c = divmod(i, G)
    R = 0.55 + 0.35 * t ** 0.5
    body = 1 - np.hypot(xx + (nB - 0.5) * 0.6, yy + (nA - 0.5) * 0.6) / R - t ** 1.5 * 0.8 * (0.5 + nA)
    a = ss(0.0, 0.5, body) * 0.6 * (1 - 0.3 * t)
    lit = ss(0.2, 0.9, yy * 0.8 + 0.2)[..., None] * (1 - t) * 0.6
    rgbS[r*C:(r+1)*C, c*C:(c+1)*C] = INK * (1 - lit) + VIO * 0.35 * lit
    aS[r*C:(r+1)*C, c*C:(c+1)*C] = a * (1 - ss(0.85, 1.0, np.hypot(xx, yy)))
save(rgbS, aS, "T_Mist.png")

# ---------- beam body (256x128, u along the beam): dark core with violet glowing edges, streaked; alpha, tint ~2.2
W, H = 256, 128
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
st = smooth_noise(W, H, 120, 6, 3, 81); nb = smooth_noise(W, H, 60, 20, 3, 82)
taper = ss(0, 0.12, x) * ss(1, 0.88, x)
edge = 0.78 + (nb - 0.5) * 0.12
inside = ss(0, 0.08, edge - dy)
rim = np.exp(-((dy - edge * 0.93) / 0.07) ** 2)
a = np.clip(inside * (0.82 + 0.18 * st) + rim * 0.5, 0, 1) * taper
rgb = INK * (1 - rim[..., None]) + VIO * 0.5 * rim[..., None] + SHAD * 0.25 * (st * inside)[..., None]
save(rgb, a, "T_BeamBody.png")
# beam edge glow (256x64 additive): two bright edge lines + soft fill, white-violet streaks
W, H = 256, 64
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
st = smooth_noise(W, H, 100, 4, 3, 91)
taper = ss(0, 0.15, x) * ss(1, 0.85, x)
lines = np.exp(-((dy - 0.72) / 0.06) ** 2) * (0.6 + 0.6 * st)
fill = np.exp(-((dy - 0.8) / 0.18) ** 2) * 0.25 * st
heat = np.clip(lines + fill, 0, 1)
save(ramp(0.3 + 0.7 * heat), np.clip(lines + fill, 0, 1) * taper, "T_BeamEdge.png")
print("ok")
