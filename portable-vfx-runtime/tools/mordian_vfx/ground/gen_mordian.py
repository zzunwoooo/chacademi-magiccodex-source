# Mordian (모르디안의 원령) shared texture set: ink bodies with violet rims, lilac glows, cracks, pool, claws, runes, scratches.
# usage: python gen_mordian.py <outdir>
import sys, math, numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import gaussian_filter
OUT = (sys.argv[1] if len(sys.argv) > 1 else ".") + "/"
rng = np.random.default_rng(7)

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

INK = np.array([0.035, 0.024, 0.055]); SH = np.array([0.165, 0.086, 0.25]); VI = np.array([0.54, 0.235, 1.0])
LI = np.array([0.84, 0.59, 1.0]); WL = np.array([0.95, 0.9, 1.0])
RAMP = [(1.0, WL), (0.75, LI), (0.45, VI), (0.2, np.array([0.30, 0.12, 0.6])), (0.0, SH)]
def ramp(h, stops=RAMP):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
def mask_from_draw(S, fn, blur=0):
    im = Image.new("L", (S, S), 0); fn(ImageDraw.Draw(im)); a = np.asarray(im, float) / 255
    return gaussian_filter(a, blur) if blur else a
def down(a, k):  # area downsample
    h, w = a.shape[:2]; return a.reshape(h // k, k, w // k, k, *a.shape[2:]).mean(axis=(1, 3))

# ---------------------------------------------------------------- T_Line (ring/polygram profile, U along, V across)
W, H = 128, 32
v = np.linspace(-1, 1, H)[:, None] * np.ones((1, W)); n = smooth_noise(W, H, 16, 40, 2, 3)
core = np.exp(-(v / 0.16) ** 2); halo = np.exp(-(v / 0.55) ** 2) * 0.5
a = np.clip(core + halo, 0, 1) * (0.85 + 0.15 * n)
save(ramp(0.35 + 0.65 * core), a, "T_Line.png")

# ---------------------------------------------------------------- T_ShockBand (ring band: V=1 leading sharp edge, trailing brushy smear)
W, H = 256, 64
vv = np.linspace(0, 1, H)[:, None] * np.ones((1, W)); st = smooth_noise(W, H, 6, 60, 3, 5); n2 = smooth_noise(W, H, 40, 30, 3, 6)
lead = np.exp(-((vv - 0.82) / 0.05) ** 2)
trail = ss(0.0, 0.8, vv) * (1 - ss(0.8, 0.86, vv)) * (0.35 + 0.65 * st)
heat = np.clip(lead * 1.0 + trail * 0.55, 0, 1)
a = np.clip(lead + trail * 0.75, 0, 1) * (1 - ss(0.88, 1.0, vv)) * (0.7 + 0.3 * n2)
save(ramp(heat), a, "T_ShockBand.png")

# ---------------------------------------------------------------- T_Glow / T_Dot / T_Star / T_Streak
S = 128; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy)
g = (np.exp(-(r / 0.35) ** 2) * 0.85 + np.exp(-(r / 0.75) ** 2) * 0.25) * (1 - ss(0.9, 1, r))
save(np.ones((S, S, 3)), g, "T_Glow.png")
S = 64; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy)
save(np.ones((S, S, 3)), np.clip(np.exp(-(r / 0.3) ** 2) + (1 - ss(0.08, 0.16, r)), 0, 1) * (1 - ss(0.85, 1, r)), "T_Dot.png")
S = 256; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy) + 1e-6; ax, ay = np.abs(xx), np.abs(yy)
def spikes(ax, ay, w):
    return np.exp(-ay / (w * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (w * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
d1 = (xx + yy) / math.sqrt(2); d2 = (xx - yy) / math.sqrt(2)
sp = spikes(ax, ay, 0.022) + 0.45 * spikes(np.abs(d1) * 1.6, np.abs(d2) * 1.6, 0.02)
gl = np.exp(-(r / 0.14) ** 2) * 0.9 + np.exp(-(r / 0.45) ** 2) * 0.28
heat = np.clip(sp * 1.2 + np.exp(-(r / 0.1) ** 2), 0, 1)
save(ramp(0.4 + 0.6 * heat), np.clip(sp + gl, 0, 1) * (1 - ss(0.92, 1, r)), "T_Star.png")
W, H = 256, 32; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
taper = x ** 1.4 * (1 - ss(0.9, 1.0, x))
core = np.exp(-(dy / (0.12 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.55 * taper + 1e-3)) ** 2) * 0.45 * taper
save(ramp(0.35 + 0.65 * np.clip(core + halo * 0.4, 0, 1)), np.clip(core + halo, 0, 1), "T_Streak.png")

# ---------------------------------------------------------------- T_ClawStrip (ink spike/claw/tendril: U = base(0) → tip(1), V across). Alpha-blend + HDR tint.
W, H = 256, 64
u = np.linspace(0, 1, W)[None, :] * np.ones((H, 1)); vv = np.linspace(-1, 1, H)[:, None] * np.ones((1, W))
nE = smooth_noise(W, H, 20, 30, 3, 9); grain = smooth_noise(W, H, 50, 4, 3, 10)
wid = (1 - u) ** 0.75 * 0.92 * (0.88 + 0.12 * nE) + 0.02
d = np.abs(vv) / wid; inside = 1 - ss(0.9, 1.0, d)
rim = ss(0.7, 0.93, d) * inside
body = INK * (1 - grain[..., None] * 0.6) + SH * grain[..., None] * 0.6
tipglow = ss(0.86, 1.0, u)[..., None]
rgb = body * (1 - rim[..., None]) + (VI * 0.6 + LI * 0.4) * rim[..., None]
rgb = rgb * (1 - tipglow * 0.8) + LI * tipglow * 0.8 * (0.5 + 0.5 * (1 - d[..., None]))
a = inside * ss(0.0, 0.04, u)
save(rgb, a, "T_ClawStrip.png")

# ---------------------------------------------------------------- T_Shard (dark shard, violet rim, head at right)
W, H = 128, 64; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
nS = smooth_noise(W, H, 20, 20, 3, 41)
prof = np.sin(np.pi * x) ** 0.7 * (0.35 + 0.65 * x)
dist = 0.85 * prof - (dy + (nS - 0.5) * 0.3)
a = ss(-0.03, 0.02, dist)
edge = np.exp(-(np.clip(dist, 0, None) / 0.09) ** 2)[..., None]
save(INK * (1 - edge) + (LI * 0.7 + VI * 0.3) * edge, a, "T_Shard.png")

# ---------------------------------------------------------------- T_Puff_Sheet 4x4 (white, soft noisy puff; colour from layer / material)
C, G = 96, 4; yy, xx = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 30, 30, 4, 11); nB = smooth_noise(C, C, 14, 14, 3, 12); stk = smooth_noise(C, C, 8, 40, 3, 13)
rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
for i in range(G*G):
    t = i / (G*G - 1); R = 0.55 + 0.35 * t ** 0.5
    wx = xx + (nB - 0.5) * 0.55; wy = yy + (nA - 0.5) * 0.55
    body = 1 - np.hypot(wx, wy) / R + (stk - 0.5) * 0.3 - t ** 1.4 * 0.85 * (0.4 + nA)
    al = ss(0.0, 0.5, body) * 0.85 * (1 - ss(0.85, 1, np.hypot(xx, yy)))
    shade = 0.75 + 0.25 * ss(-0.2, 0.8, -wy)
    rr, cc = divmod(i, G); rgbS[rr*C:(rr+1)*C, cc*C:(cc+1)*C] = shade[..., None]; aS[rr*C:(rr+1)*C, cc*C:(cc+1)*C] = al
save(rgbS, aS, "T_Puff_Sheet.png")

# ---------------------------------------------------------------- T_Stroke_Sheet 2x8 (violet brush stroke wipe, 256x32 cells)
CW, CH = 256, 32
u1 = np.linspace(0, 1, CW)[None, :]; v1 = np.linspace(0, 1, CH)[:, None]
N1 = smooth_noise(CW, CH, 40, 40, 3, 21); N2 = smooth_noise(CW, CH, 12, 12, 3, 22); strands = smooth_noise(CW, CH, 90, 3, 3, 23)
def stroke_cell(t):
    head = min(1.0, t / 0.5) ** 0.75; tail = max(0.0, (t - 0.18) / 0.82) ** 1.3
    rel = np.clip((u1 - tail) / max(head - tail, 1e-3), 0, 1)
    inside = ss(tail, tail + 0.05, u1) * (1 - ss(head - 0.015, head + 0.005, u1))
    width = np.sin(np.pi * rel) ** 0.55
    d = np.abs(v1 - 0.5 - (N1 - 0.5) * 0.12) / (0.38 * width + 1e-3)
    core = np.exp(-(d / 0.18) ** 2); body = np.clip(1 - d, 0, 1) ** 1.5 * (0.5 + 0.5 * strands)
    heat = np.clip(core + body * 0.7, 0, 1) * (0.45 + 0.55 * rel ** 0.6)
    if t > 0.45: heat = heat * (1 - ss(0.0, 1.0, (t - 0.45) * 2.2 * (0.6 + N2)))
    al = np.clip(ss(0.02, 0.25, heat) + np.exp(-(d / 0.9) ** 2) * 0.25 * width, 0, 1) * inside
    return ramp(heat * 0.95), al
rgbS = np.zeros((CH*8, CW*2, 3)); aS = np.zeros((CH*8, CW*2))
for i in range(16):
    rr, cc = divmod(i, 2); c_, a_ = stroke_cell(i / 15)
    rgbS[rr*CH:(rr+1)*CH, cc*CW:(cc+1)*CW] = c_; aS[rr*CH:(rr+1)*CH, cc*CW:(cc+1)*CW] = a_
save(rgbS, aS, "T_Stroke_Sheet.png")

# ---------------------------------------------------------------- T_Crack (512, alpha-blend + HDR tint): black branching cracks, violet rims, crater centre
S2, K = 1024, 2
def crack_mask():
    im = Image.new("L", (S2, S2), 0); dr = ImageDraw.Draw(im); c = S2 / 2
    def walk(x, y, ang, length, w, depth):
        steps = int(length / 14)
        for i in range(steps):
            ang += rng.normal(0, 0.28); nx, ny = x + math.cos(ang) * 14, y + math.sin(ang) * 14
            ww = max(1.5, w * (1 - i / steps) ** 0.8)
            dr.line([(x, y), (nx, ny)], fill=255, width=int(round(ww))); dr.ellipse([nx - ww / 2, ny - ww / 2, nx + ww / 2, ny + ww / 2], fill=255)
            x, y = nx, ny
            if depth < 2 and rng.random() < 0.09:
                walk(x, y, ang + rng.choice([-1, 1]) * rng.uniform(0.5, 1.1), length * (1 - i / steps) * rng.uniform(0.35, 0.6), ww * 0.7, depth + 1)
            if math.hypot(x - c, y - c) > S2 * 0.47: break
    n = 11
    for k in range(n):
        ang = k / n * 2 * math.pi + rng.uniform(-0.2, 0.2)
        r0 = rng.uniform(60, 90)
        walk(c + math.cos(ang) * r0, c + math.sin(ang) * r0, ang, rng.uniform(300, 440), rng.uniform(26, 38), 0)
    # concentric broken arcs (ring fractures)
    for rad, wdt in [(150, 11), (270, 8)]:
        a0 = 0
        while a0 < 360:
            span = rng.uniform(20, 55)
            jit = rng.uniform(-12, 12)
            dr.arc([c - rad - jit, c - rad - jit, c + rad + jit, c + rad + jit], a0, a0 + span, fill=255, width=wdt)
            a0 += span + rng.uniform(15, 40)
    return np.asarray(im, float) / 255
M = crack_mask()
yy, xx = np.mgrid[-1:1:S2*1j, -1:1:S2*1j]; rr = np.hypot(xx, yy)
nC = smooth_noise(S2, S2, 40, 40, 4, 31)
crater = 1 - ss(0.13, 0.17, rr + (nC - 0.5) * 0.12)
M = np.clip(M + crater, 0, 1)
Mb = gaussian_filter(M, 7); rim = np.clip(Mb * 2.2, 0, 1) * (1 - M)
inner = np.zeros_like(M)          # thin hot line in the middle of wide cracks
scorch = (1 - ss(0.2, 0.75, rr + (nC - 0.5) * 0.25)) * 0.45
body = INK * (1 - inner[..., None]) + VI * 0.5 * inner[..., None]
rgb = body * M[..., None] + (VI * 0.55 + LI * 0.45) * rim[..., None] * (1 - M[..., None]) + INK * (scorch[..., None]) * (1 - np.clip(M + rim, 0, 1)[..., None])
a = np.clip(np.maximum(np.maximum(M, rim * 0.95), scorch), 0, 1) * (1 - ss(0.93, 1.0, rr))
save(down(rgb, K), down(a, K), "T_Crack.png")

# ---------------------------------------------------------------- T_InkPool (384, alpha-blend + HDR tint): ink puddle, splatter tendrils, inner violet rim, swirl grain
S2, K = 768, 2
yy, xx = np.mgrid[-1:1:S2*1j, -1:1:S2*1j]; rr = np.hypot(xx, yy); th = np.arctan2(yy, xx)
nP = smooth_noise(S2, S2, 60, 60, 4, 51)
edge = 0.6 + 0.06 * np.sin(5 * th + 1.0) + 0.04 * np.sin(9 * th - 0.4) + (nP - 0.5) * 0.12
for k in range(12):  # tendril splatters
    ang = k / 12 * 2 * math.pi + rng.uniform(-0.2, 0.2); L = rng.uniform(0.06, 0.2); wdt = rng.uniform(0.09, 0.15)
    dth = np.angle(np.exp(1j * (th - ang)))
    edge = np.maximum(edge, (0.6 + L) - np.abs(dth) / wdt * L * 1.0 - 0)
body = ss(0.0, 0.015, edge - rr)
drops = np.zeros_like(rr)
for k in range(26):
    ang = rng.uniform(0, 2 * math.pi); dist = rng.uniform(0.7, 0.95); s_ = rng.uniform(0.012, 0.035)
    drops = np.maximum(drops, 1 - ss(s_ * 0.8, s_, np.hypot(xx - math.cos(ang) * dist, yy - math.sin(ang) * dist)))
Mp = np.clip(body + drops, 0, 1)
dist_in = np.clip(edge - rr, 0, None)
rimP = np.exp(-(dist_in / 0.02) ** 2) * body + np.clip(gaussian_filter(Mp, 5) * 2.2, 0, 1) * (1 - Mp) * 0.85
swirl = smooth_noise(S2, S2, 8, 80, 3, 52)  # streak grain, then twisted
tw = (th + rr * 4.0) % (2 * math.pi)
sw = 0.5 + 0.5 * np.sin(tw * 6 + swirl * 4)
deep = SH * 0.5 * (sw * (1 - ss(0.0, 0.55, rr)))[..., None]
rgb = (INK + deep) * (1 - rimP[..., None]) + (VI * 0.6 + LI * 0.4) * rimP[..., None]
a = np.clip(np.maximum(Mp, rimP * 0.9), 0, 1) * (1 - ss(0.96, 1.0, rr))
save(down(rgb, K), down(a, K), "T_InkPool.png")

# ---------------------------------------------------------------- T_Scratch (256, additive): three parallel diagonal claw scratches (the signature)
S2, K = 768, 3
hot = np.zeros((S2, S2))
for i, off in enumerate([-0.24, 0.0, 0.24]):
    im = Image.new("L", (S2, S2), 0); dr = ImageDraw.Draw(im)
    # line from lower-left to upper-right, offset perpendicular
    p0 = np.array([-0.72, 0.62]) + np.array([0.707, 0.707]) * off; p1 = np.array([0.72, -0.62]) + np.array([0.707, 0.707]) * off
    p0 = p0 + rng.uniform(-0.06, 0.06, 2); p1 = p1 + rng.uniform(-0.06, 0.06, 2)
    N = 40
    for j in range(N):
        s0, s1 = j / N, (j + 1) / N
        a0 = p0 + (p1 - p0) * s0; a1 = p0 + (p1 - p0) * s1
        jit = rng.normal(0, 0.006, 2)
        w = 26 * math.sin(math.pi * (s0 + 0.5 / N)) ** 0.7 + 2
        dr.line([tuple((a0 + 1) * S2 / 2), tuple((a1 + jit + 1) * S2 / 2)], fill=255, width=int(w))
    hot = np.maximum(hot, np.asarray(im, float) / 255)
core = gaussian_filter(hot, 3)
glow = gaussian_filter(hot, 22)
heat = np.clip(core * 1.0 + glow * 0.5, 0, 1)
a = np.clip(core + glow * 1.4, 0, 1)
save(down(ramp(0.25 + 0.75 * heat), K), down(a, K), "T_Scratch.png")

# ---------------------------------------------------------------- T_RuneStrip (512x128, additive): angular runes + border lines + triple-slash separators
W2, H2, K = 1024, 256, 2
im = Image.new("L", (W2, H2), 0); dr = ImageDraw.Draw(im)
dr.line([(0, 28), (W2, 28)], fill=255, width=5); dr.line([(0, H2 - 28), (W2, H2 - 28)], fill=255, width=5)
dr.line([(0, 44), (W2, 44)], fill=150, width=2); dr.line([(0, H2 - 44), (W2, H2 - 44)], fill=150, width=2)
x0 = 20; gi = 0
while x0 < W2 - 90:
    if gi % 4 == 3:  # triple slash separator
        for k in range(3):
            xs = x0 + k * 16; dr.line([(xs, 170), (xs + 26, 86)], fill=255, width=6)
        x0 += 76
    else:
        pts = [(x0 + px * 60, 72 + py * 112 / 2) for px in (0, 0.5, 1) for py in (0, 1, 2)]
        idx = rng.choice(9, size=5, replace=True)
        for a_, b_ in zip(idx[:-1], idx[1:]):
            if a_ != b_: dr.line([pts[a_], pts[b_]], fill=255, width=7)
        if rng.random() < 0.5: c_ = pts[rng.integers(9)]; dr.ellipse([c_[0] - 8, c_[1] - 8, c_[0] + 8, c_[1] + 8], outline=255, width=5)
        x0 += 88
    gi += 1
L = np.asarray(im, float) / 255
core = gaussian_filter(L, 1.2); glow = gaussian_filter(L, 9)
heat = np.clip(core + glow * 0.4, 0, 1)
save(down(ramp(0.3 + 0.7 * heat), K), down(np.clip(core + glow * 1.2, 0, 1), K), "T_RuneStrip.png")
print("ok")
