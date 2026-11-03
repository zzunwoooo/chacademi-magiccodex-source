# Mordian Wraith boss VFX textures (painterly, ink body + violet rim). usage: python gen_mordian.py <outdir>
import sys, os, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = (sys.argv[1] if len(sys.argv) > 1 else ".") + "/"; os.makedirs(OUT, exist_ok=True)

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

INK = np.array([0.035, 0.024, 0.055]); SHADOW = np.array([0.165, 0.086, 0.25])
VIOLET = np.array([0.54, 0.235, 1.0]); LILAC = np.array([0.84, 0.59, 1.0]); WL = np.array([0.95, 0.9, 1.0])
GOLD = np.array([0.55, 0.44, 0.23]); GOLDH = np.array([0.95, 0.80, 0.48])
RAMP = [(1.00, tuple(WL)), (0.78, tuple(LILAC)), (0.50, tuple(VIOLET)), (0.25, (0.30, 0.13, 0.55)), (0.0, tuple(SHADOW))]
def ramp(h, stops=RAMP):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
def mix(a, b, k): return a * (1 - k[..., None]) + b * k[..., None]

# ---------------- claw crescent wipe: 1 col x 16 rows, cell 512x48. U = along stroke (head at U=1), V across
CW, CH, NF = 512, 48, 16
u = np.linspace(0, 1, CW)[None, :]; v = np.linspace(-1, 1, CH)[:, None]
nA = smooth_noise(CW, CH, 220, 10, 3, 1); nB = smooth_noise(CW, CH, 30, 30, 3, 2); nE = smooth_noise(CW, CH, 18, 18, 3, 3)
prof = np.sin(np.pi * np.clip(u, 0, 1) ** 0.85) ** 0.75 * (0.30 + 0.70 * u ** 0.8)
cv = 0.12 * np.sin(np.pi * u)
d = np.abs(v - cv - (nB - 0.5) * 0.08) / (0.92 * prof + 1e-3)
lead = (v < cv).astype(float)                         # upper edge = leading (outer) edge, brighter
def claw_cell(t):
    head = min(1.0, t / 0.36) ** 0.7; tail = max(0.0, (t - 0.16) / 0.84) ** 1.25
    dn = np.clip(d, 0, 1)
    vis = ss(tail - 0.02 + 0.08 * dn, tail + 0.10 + 0.08 * dn, u) * (1 - ss(head - 0.01 - 0.07 * dn, head + 0.012 - 0.07 * dn, u))
    rel = np.clip((u - tail) / max(head - tail, 1e-3), 0, 1)
    inside = 1 - ss(0.86, 1.0, d)
    rimk = np.exp(-(np.clip(1 - d, 0, None) / (0.07 + 0.09 * lead)) ** 2) * inside
    rimk *= (0.35 + 0.65 * lead) * (0.6 + 0.4 * nA)
    headk = np.exp(-((u - head) / 0.045) ** 2) * inside * (1 - t) ** 0.5
    heat = np.clip(rimk * (0.55 + 0.45 * rel) + headk * 0.9, 0, 1)
    body = INK * (0.8 + 0.6 * nA[..., None]) + SHADOW * 0.25 * (1 - d[..., None]) ** 2
    rgb = mix(body, ramp(0.25 + 0.75 * heat), np.clip(heat * 1.6, 0, 1))
    halo = np.exp(-(np.clip(d - 1, 0, None) / 0.3) ** 2) * (d > 0.86) * 0.25 * (0.4 + 0.6 * rel)
    rgb = mix(rgb, SHADOW * 0.9, np.where(d > 0.86, 1.0, 0.0) * 1.0)
    a = np.clip(inside * 0.94 + halo, 0, 1) * vis
    if t > 0.4:                                     # break up into ink fragments
        k = (t - 0.4) / 0.6
        a *= 1 - ss(0.0, 0.35, k * 1.3 - nE * 0.8 + (1 - u) * 0.25)
    return rgb, a
rgbS = np.zeros((CH * NF, CW, 3)); aS = np.zeros((CH * NF, CW))
for i in range(NF):
    rgb, a = claw_cell(i / (NF - 1)); rgbS[i*CH:(i+1)*CH] = rgb; aS[i*CH:(i+1)*CH] = a
save(rgbS, aS, "T_ClawSheet.png")

# ---------------- three parallel gouges (ground scratch), 512x128, along U
SW, SH = 512, 128
u2 = np.linspace(0, 1, SW)[None, :]; v2 = np.linspace(0, 1, SH)[:, None]
nS = smooth_noise(SW, SH, 26, 26, 3, 7); nS2 = smooth_noise(SW, SH, 160, 6, 3, 8)
inkA = np.zeros((SH, SW)); core = np.zeros((SH, SW))
for vc, a0, a1 in [(0.22, 0.06, 0.86), (0.5, 0.0, 1.0), (0.78, 0.12, 0.94)]:
    rel = np.clip((u2 - a0) / (a1 - a0), 0, 1); on = (u2 >= a0) & (u2 <= a1)
    w = np.sin(np.pi * rel) ** 0.55 * (0.4 + 0.6 * rel ** 0.5) * 0.085
    dd = np.abs(v2 - vc - (nS - 0.5) * 0.03) / (w + 1e-4)
    inkA = np.maximum(inkA, on * (1 - ss(0.9, 1.6 + 0.6 * nS, dd)))
    core = np.maximum(core, on * np.exp(-(dd / 0.42) ** 2) * (0.6 + 0.4 * nS2))
rgb = mix(INK * (0.8 + 0.5 * nS[..., None]), ramp(0.3 + 0.7 * core), np.clip(core * 1.5, 0, 1))
save(rgb, np.clip(inkA * 0.9 + core * 0.2, 0, 1), "T_Scratch3.png")

# ---------------- dark ink mist 4x4 (128 cells)
C, G = 128, 4
yy, xx = np.mgrid[-1:1:C*1j, -1:1:C*1j]; rr = np.hypot(xx, yy)
m1 = smooth_noise(C, C, 34, 34, 4, 11); m2 = smooth_noise(C, C, 16, 16, 3, 12); ms = smooth_noise(C, C, 10, 50, 3, 13)
def mist_cell(t):
    R = 0.55 + 0.35 * t ** 0.5
    wx = xx + (m1 - 0.5) * 0.6; wy = yy + (m2 - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R + (ms - 0.5) * 0.3 - t ** 1.4 * 0.75 * (0.4 + m1)
    a = ss(0.0, 0.5, body) * 0.6 * (1 - 0.25 * t)
    rim = np.exp(-(body / 0.12) ** 2) * (body > -0.05) * (1 - t) * 0.8
    rgb = mix(INK + SHADOW * 0.4 * ms[..., None], VIOLET * 0.7, np.clip(rim, 0, 1))
    return rgb, np.clip(a + rim * 0.15, 0, 1) * (1 - ss(0.9, 1.0, rr))
def splat_cell(t):  # ink splash crown: blot + radial spikes + droplets, dark body violet rim
    ang = np.arctan2(yy, xx); sp = smooth_noise(C, C, 6, 6, 2, 21)
    spikes = 0.5 + 0.5 * np.cos(ang * 9 + 2 * np.sin(ang * 3)) ** 7
    R = 0.25 + 0.55 * t ** 0.45
    rad = R * (0.55 + 0.6 * spikes * (0.6 + 0.4 * m1))
    body = 1 - rr / rad + (m2 - 0.5) * 0.25
    hole = ss(0.0, 0.25, rr / (R * 0.9) - 0.15 - t * 0.6) if t > 0.3 else np.ones_like(rr)
    drops = (np.hypot(xx * 1.0, yy) > R * 0.9) * ss(0.72, 0.8, sp) * (1 - t) * (rr < R * 1.35)
    a = np.clip(ss(0.0, 0.08, body) * (0.35 + 0.65 * hole) + drops, 0, 1) * (1 - ss(0.55, 1.0, t)) ** 0.8
    rim = np.exp(-(np.clip(body, 0, None) / 0.12) ** 2) * (body > -0.02)
    rgb = mix(INK, ramp(0.35 + 0.5 * rim), np.clip(rim * 0.9 * (1 - t * 0.6), 0, 1))
    return rgb, a * 0.95 * (1 - ss(0.92, 1.0, rr))
def sheet(fn, name):
    rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
    for i in range(G*G):
        r, c = divmod(i, G); rgb, a = fn(i / (G*G - 1))
        rgbS[r*C:(r+1)*C, c*C:(c+1)*C] = rgb; aS[r*C:(r+1)*C, c*C:(c+1)*C] = a
    save(rgbS, aS, name)
sheet(mist_cell, "T_InkMist_Sheet.png"); sheet(splat_cell, "T_InkSplat_Sheet.png")

# ---------------- small sprites
S = 128
yy2, xx2 = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r2 = np.hypot(xx2, yy2) + 1e-6; ax, ay = np.abs(xx2), np.abs(yy2)
g = np.exp(-(r2 / 0.35) ** 2) * 0.85 + np.exp(-(r2 / 0.75) ** 2) * 0.25; g *= 1 - ss(0.9, 1.0, r2)
save(np.ones((S, S, 3)), g, "T_Glow.png")
spike = np.exp(-ay / (0.03 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (0.03 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
heat = np.clip(spike * 1.2 + np.exp(-(r2 / 0.12) ** 2), 0, 1)
save(ramp(0.45 + 0.55 * heat), np.clip(spike + np.exp(-(r2 / 0.18) ** 2) * 0.9 + np.exp(-(r2 / 0.5) ** 2) * 0.25, 0, 1), "T_Star4.png")
D = 64; yy3, xx3 = np.mgrid[-1:1:D*1j, -1:1:D*1j]; r3 = np.hypot(xx3, yy3)
save(np.ones((D, D, 3)), np.clip(np.exp(-(r3 / 0.3) ** 2) + (1 - ss(0.08, 0.16, r3)), 0, 1) * (1 - ss(0.85, 1, r3)), "T_Dot.png")
# streak (head at right)
W_, H_ = 256, 32
x = np.linspace(0, 1, W_)[None, :]; y = np.linspace(-1, 1, H_)[:, None]; dy = np.abs(y)
taper = x ** 1.5 * (1 - ss(0.9, 1.0, x))
corel = np.exp(-(dy / (0.12 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.55 * taper + 1e-3)) ** 2) * 0.45 * taper
save(ramp(0.3 + 0.7 * np.clip(corel + halo * 0.4, 0, 1)), np.clip(corel + halo, 0, 1), "T_Streak.png")
# ink shard (dark body, violet rim), 128x64, head right
W_, H_ = 128, 64
x = np.linspace(0, 1, W_)[None, :]; y = np.linspace(-1, 1, H_)[:, None]; dy = np.abs(y)
nS3 = smooth_noise(W_, H_, 20, 20, 3, 41)
profS = np.sin(np.pi * x) ** 0.7 * (0.4 + 0.6 * x)
dist = 0.55 * profS - (dy + (nS3 - 0.5) * 0.3)
a = ss(-0.03, 0.02, dist); edge = np.exp(-(np.clip(dist, 0, None) / 0.035) ** 2) * (y < 0)
save(mix(INK + SHADOW * 0.2, VIOLET, edge * 0.7), a, "T_InkShard.png")
# ink tail streak 256x64 (dark smoke strand with violet edge, head right)
W_, H_ = 256, 64
x = np.linspace(0, 1, W_)[None, :]; y = np.linspace(-1, 1, H_)[:, None]
nT = smooth_noise(W_, H_, 80, 8, 3, 51); nT2 = smooth_noise(W_, H_, 20, 20, 3, 52)
pr = (0.25 + 0.75 * x ** 0.7) * (1 - ss(0.88, 1.0, x))
dd = np.abs(y + (nT2 - 0.5) * 0.4 * (1 - x)) / (pr + 1e-3)
a = (1 - ss(0.5, 1.0, dd + (nT - 0.5) * 0.5)) * ss(0.0, 0.5, x) * 0.85
rim = np.exp(-((dd - 0.75) / 0.15) ** 2) * x
save(mix(INK + SHADOW * 0.3 * nT[..., None], VIOLET, np.clip(rim, 0, 1) * 0.8), np.clip(a, 0, 1), "T_InkTail.png")
# line profile for mesh line work: 64x16, gaussian across V
L = np.exp(-((np.linspace(-1, 1, 16)) / 0.35) ** 2)[:, None] * np.ones((1, 64))
save(np.ones((16, 64, 3)), L + 0.0, "T_Line.png")
# shock ring 256
S = 256; yy4, xx4 = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r4 = np.hypot(xx4, yy4); nR = smooth_noise(S, S, 14, 14, 3, 61)
ring = np.exp(-((r4 - 0.86) / 0.022) ** 2); inner = np.exp(-((r4 - 0.74) / 0.12) ** 2) * (r4 < 0.86) * (0.45 + 0.7 * nR)
save(ramp(np.clip(ring + inner * 0.45, 0, 1) * 0.9 + 0.1), np.clip(ring + inner * 0.6, 0, 1) * (1 - ss(0.95, 1.0, r4)), "T_ShockRing.png")

# ---------------- chain band 512x64: dark smoky band + gold chain links, alpha fades toward the tail (U=0)
BW, BH, SC = 512, 64, 3
img = Image.new("RGBA", (BW * SC, BH * SC), (0, 0, 0, 0)); dr = ImageDraw.Draw(img)
gold = tuple(int(c * 255) for c in GOLD) + (255,); goldh = tuple(int(c * 255) for c in GOLDH) + (255,); ink = tuple(int(c * 255) for c in INK) + (255,)
step = 34 * SC; cy = BH * SC // 2
for k in range(-1, BW * SC // step + 2):
    cx = k * step + step // 2
    if k % 2 == 0:
        bb = [cx - 26 * SC, cy - 13 * SC, cx + 26 * SC, cy + 13 * SC]
        dr.ellipse(bb, fill=ink, outline=gold, width=5 * SC); dr.ellipse([bb[0] + 9*SC, bb[1] + 6*SC, bb[2] - 9*SC, bb[3] - 6*SC], fill=(0, 0, 0, 0), outline=goldh, width=SC)
    else:
        dr.rounded_rectangle([cx - 24 * SC, cy - 4 * SC, cx + 24 * SC, cy + 4 * SC], radius=4 * SC, fill=gold, outline=goldh, width=SC)
links = np.asarray(img.resize((BW, BH), Image.LANCZOS), float) / 255
u5 = np.linspace(0, 1, BW)[None, :]; v5 = np.linspace(-1, 1, BH)[:, None]; nb = smooth_noise(BW, BH, 60, 10, 3, 71)
band = (1 - ss(0.55, 1.0, np.abs(v5) + (nb - 0.5) * 0.4)) * 0.75
edge = np.exp(-((np.abs(v5) - 0.72) / 0.12) ** 2) * (0.5 + 0.5 * nb)
base_rgb = mix(INK + SHADOW * 0.4 * nb[..., None], VIOLET * 0.9, np.clip(edge, 0, 1))
la = links[..., 3]
rgb = mix(base_rgb, links[..., :3], la)
fade = ss(0.0, 0.75, u5) * (1 - ss(0.96, 1.0, u5))
save(rgb, np.clip(np.maximum(band, la) + edge * 0.4, 0, 1) * fade, "T_ChainBand.png")

# ---------------- spin smear 512x128 (additive violet afterglow). U: tail 0 → head 1; V: 0 inner → 1 outer
u6 = np.linspace(0, 1, 512)[None, :]; v6 = np.linspace(0, 1, 128)[:, None]
st = smooth_noise(512, 128, 300, 5, 3, 81); st2 = smooth_noise(512, 128, 60, 20, 3, 82)
dens = (0.25 + 0.75 * v6 ** 1.5) * (0.45 + 0.75 * st) * ss(0.0, 0.8, u6) ** 1.3 * (1 - ss(0.97, 1.0, u6)) * (1 - ss(0.9, 1.0, v6)) * ss(0.0, 0.25, v6)
outer = np.exp(-((v6 - 0.86) / 0.04) ** 2) * ss(0.4, 1.0, u6)
heat = np.clip(dens * 0.7 + outer * 0.9, 0, 1)
save(ramp(0.15 + 0.85 * heat), np.clip(dens * (0.7 + 0.3 * st2) + outer, 0, 1), "T_SpinSmear.png")
print("ok")
