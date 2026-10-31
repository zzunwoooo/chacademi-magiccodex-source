# Pumpkin Reaper (Halloween boss) shared painterly textures. usage: python gen_pumpkin.py <outdir>
import sys, os, math, numpy as np
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
def ramp(h, stops):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
FIRE = [(1.0,(1,1,0.94)),(0.8,(1,0.9,0.55)),(0.6,(1,0.62,0.18)),(0.4,(0.96,0.36,0.06)),(0.2,(0.70,0.12,0.06)),(0.0,(0.32,0.04,0.10))]
SOUL = [(1.0,(0.95,1,1)),(0.8,(0.62,1,0.90)),(0.6,(0.25,0.92,0.78)),(0.4,(0.10,0.66,0.66)),(0.2,(0.10,0.30,0.48)),(0.0,(0.10,0.06,0.26))]
VIOL = [(1.0,(1,0.95,1)),(0.8,(0.95,0.68,1)),(0.6,(0.74,0.40,1)),(0.4,(0.52,0.20,0.86)),(0.2,(0.30,0.08,0.52)),(0.0,(0.12,0.03,0.22))]
REDW = [(1.0,(1,0.85,0.75)),(0.7,(1,0.32,0.20)),(0.4,(0.95,0.10,0.06)),(0.0,(0.45,0.02,0.02))]

S = 256
yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; rr = np.hypot(xx, yy) + 1e-6; ang = np.arctan2(yy, xx)
# ---------------- warning decals (red) ----------------
hatch = 0.5 + 0.5 * np.sin((xx + yy) * 38)
fill = (0.10 + 0.40 * rr ** 2.2 + 0.08 * ss(0.6, 1, hatch) * (rr < 0.97)) * (1 - ss(0.97, 1.0, rr))
save(ramp(0.45 + 0.4 * rr, REDW), fill, "T_WarnFill.png")
rim = np.exp(-((rr - 0.955) / 0.022) ** 2) + 0.45 * np.exp(-((rr - 0.88) / 0.008) ** 2)
ticks = (np.cos(ang * 24) > 0.93) * np.exp(-((rr - 0.915) / 0.02) ** 2) * 0.9
glowr = np.exp(-((rr - 0.95) / 0.07) ** 2) * 0.35
a = np.clip(rim + ticks + glowr, 0, 1) * (1 - ss(0.99, 1.0, rr))
save(ramp(0.55 + 0.45 * np.clip(rim + ticks, 0, 1), REDW), a, "T_WarnRim.png")
# band for sector / line meshes: V = 0 inner → 1 outer edge, U across (edges bright)
B = 128; v = np.linspace(0, 1, B)[:, None]; u = np.linspace(0, 1, B)[None, :]
edgeV = np.exp(-((v - 0.975) / 0.02) ** 2); edgeU = np.exp(-(np.minimum(u, 1 - u) / 0.018) ** 2)
hb = 0.5 + 0.5 * np.sin((u * 3 + v * 3) * 24)
a = np.clip(0.12 + 0.36 * v ** 1.8 + 0.07 * ss(0.6, 1, hb) + edgeV + 0.9 * edgeU, 0, 1)
save(ramp(0.5 + 0.5 * np.clip(edgeV + edgeU, 0, 1), REDW), a, "T_WarnBand.png")
# growing fill front (thin bright line at the leading edge, used as a sweep ring inside the zone)
front = np.exp(-((rr - 0.97) / 0.03) ** 2) + np.clip(0.25 * (rr / 0.97) ** 6, 0, 0.25) * (rr < 0.97)
save(ramp(0.6 + 0.4 * front, REDW), np.clip(front, 0, 1) * (1 - ss(0.99, 1, rr)), "T_WarnGrow.png")
# share (stack) marker: rim + 3 figures + inward chevrons, drawn at 2x
def draw_share(size=512):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2
    for k in range(3):
        a0 = math.radians(90 + k * 120); px, py = c + math.cos(a0) * size * 0.17, c - math.sin(a0) * size * 0.17
        d.ellipse([px - 22, py - 46, px + 22, py - 2], fill=255)
        d.rounded_rectangle([px - 34, py + 6, px + 34, py + 74], 22, fill=255)
    for k in range(6):
        a0 = math.radians(k * 60 + 30); r1, r2 = size * 0.40, size * 0.33
        tip = (c + math.cos(a0) * r2, c + math.sin(a0) * r2)
        p1 = (c + math.cos(a0 + 0.12) * r1, c + math.sin(a0 + 0.12) * r1); p2 = (c + math.cos(a0 - 0.12) * r1, c + math.sin(a0 - 0.12) * r1)
        d.line([p1, tip, p2], fill=255, width=14)
    return np.asarray(im.filter(ImageFilter.GaussianBlur(1.2)).resize((S, S), Image.LANCZOS), float) / 255
ic = draw_share()
a = np.clip(rim + ic * 0.95 + glowr + 0.12 * (rr < 0.95), 0, 1) * (1 - ss(0.99, 1.0, rr))
rgb = ramp(0.5 + 0.5 * np.clip(rim + ic, 0, 1), REDW); rgb = rgb * (1 - ic[..., None] * 0.4) + np.array([1, 0.85, 0.6]) * ic[..., None] * 0.4
save(rgb, a, "T_WarnShare.png")

# ---------------- line work + runes + sigil ----------------
vv = np.linspace(-1, 1, 16)[:, None] * np.ones((1, 64))
lp = np.exp(-(vv / 0.35) ** 2) + 0.35 * np.exp(-(vv / 0.8) ** 2)
save(np.ones((16, 64, 3)), np.clip(lp, 0, 1), "T_Line.png")
def glyph_strip(n, w=1024, h=256, seed=3):
    im = Image.new("L", (w, h), 0); d = ImageDraw.Draw(im); cw = w / n; rng = np.random.default_rng(seed)
    d.line([(0, 30), (w, 30)], fill=200, width=6); d.line([(0, h - 30), (w, h - 30)], fill=200, width=6)
    for i in range(n):
        cx, cy = cw * (i + 0.5), h / 2; k = i % 6; s = h * 0.26
        if k == 0:   # crescent moon
            d.ellipse([cx - s, cy - s, cx + s, cy + s], fill=255); d.ellipse([cx - s * 0.45, cy - s * 1.05, cx + s * 1.4, cy + s * 0.9], fill=0)
        elif k == 1:  # pumpkin glyph
            d.ellipse([cx - s, cy - s * 0.75, cx + s, cy + s * 0.85], outline=255, width=9); d.line([(cx, cy - s * 0.75), (cx + 8, cy - s * 1.15)], fill=255, width=9)
            d.polygon([(cx - s * 0.5, cy - 8), (cx - s * 0.2, cy - 8), (cx - s * 0.35, cy - s * 0.38)], fill=255); d.polygon([(cx + s * 0.5, cy - 8), (cx + s * 0.2, cy - 8), (cx + s * 0.35, cy - s * 0.38)], fill=255)
            d.arc([cx - s * 0.55, cy - s * 0.3, cx + s * 0.55, cy + s * 0.55], 20, 160, fill=255, width=8)
        elif k == 2:  # bat
            pts = [(cx - s * 1.2, cy - s * 0.2), (cx - s * 0.7, cy - s * 0.5), (cx - s * 0.5, cy - s * 0.1), (cx - s * 0.2, cy - s * 0.35), (cx, cy - s * 0.1),
                   (cx + s * 0.2, cy - s * 0.35), (cx + s * 0.5, cy - s * 0.1), (cx + s * 0.7, cy - s * 0.5), (cx + s * 1.2, cy - s * 0.2), (cx + s * 0.6, cy + s * 0.25),
                   (cx + s * 0.3, cy + s * 0.05), (cx, cy + s * 0.45), (cx - s * 0.3, cy + s * 0.05), (cx - s * 0.6, cy + s * 0.25)]
            d.polygon(pts, fill=255)
        elif k == 3:  # scythe rune
            d.line([(cx - s * 0.5, cy + s), (cx + s * 0.3, cy - s)], fill=255, width=10); d.arc([cx - s * 1.2, cy - s * 1.25, cx + s * 0.6, cy + s * 0.2], 200, 330, fill=255, width=10)
        elif k == 4:  # eye
            d.ellipse([cx - s, cy - s * 0.5, cx + s, cy + s * 0.5], outline=255, width=9); d.ellipse([cx - s * 0.25, cy - s * 0.25, cx + s * 0.25, cy + s * 0.25], fill=255)
        else:         # cross-star
            for a0 in (0, 90, 45, 135):
                L = s if a0 % 90 == 0 else s * 0.55; ar = math.radians(a0)
                d.line([(cx - math.cos(ar) * L, cy - math.sin(ar) * L), (cx + math.cos(ar) * L, cy + math.sin(ar) * L)], fill=255, width=8)
        d.ellipse([cx + cw / 2 - 7, cy - 7, cx + cw / 2 + 7, cy + 7], fill=200)
    return im
g = glyph_strip(8); gl = np.asarray(g.filter(ImageFilter.GaussianBlur(8)), float) / 255; gs = np.asarray(g.filter(ImageFilter.GaussianBlur(1)), float) / 255
a = np.clip(gs + gl * 0.7, 0, 1); A = Image.fromarray((a * 255).astype(np.uint8)).resize((512, 128), Image.LANCZOS)
a = np.asarray(A, float) / 255; save(np.ones((128, 512, 3)) * np.array([1, 0.95, 0.9]), a, "T_RuneStrip.png")
def sigil(size=1152):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2
    for r, w in ((0.48, 10), (0.44, 5), (0.30, 8), (0.27, 4)): d.ellipse([c - size * r, c - size * r, c + size * r, c + size * r], outline=255, width=w)
    for k in range(36):
        a0 = math.radians(k * 10); L1, L2 = size * 0.30, size * (0.36 if k % 3 == 0 else 0.33)
        d.line([(c + math.cos(a0) * L1, c + math.sin(a0) * L1), (c + math.cos(a0) * L2, c + math.sin(a0) * L2)], fill=255, width=6)
    for k in range(8):   # moon phases between the rings
        a0 = math.radians(k * 45 + 22.5); px, py = c + math.cos(a0) * size * 0.395, c + math.sin(a0) * size * 0.395; s = size * 0.032
        d.ellipse([px - s, py - s, px + s, py + s], outline=255, width=5)
        if k % 2: d.ellipse([px - s * 0.5, py - s, px + s * 1.5, py + s], fill=0)
        else: d.ellipse([px - s * 0.7, py - s * 0.7, px + s * 0.7, py + s * 0.7], fill=255)
    s = size * 0.2   # carved jack-o-lantern face
    d.polygon([(c - s * 0.75, c - s * 0.05), (c - s * 0.2, c - s * 0.05), (c - s * 0.5, c - s * 0.55)], fill=255)
    d.polygon([(c + s * 0.75, c - s * 0.05), (c + s * 0.2, c - s * 0.05), (c + s * 0.5, c - s * 0.55)], fill=255)
    d.polygon([(c - s * 0.12, c + s * 0.12), (c + s * 0.12, c + s * 0.12), (c, c - s * 0.1)], fill=255)
    m = [(c - s * 0.85, c + s * 0.25), (c - s * 0.55, c + s * 0.42), (c - s * 0.4, c + s * 0.3), (c - s * 0.2, c + s * 0.5), (c, c + s * 0.36), (c + s * 0.2, c + s * 0.5),
         (c + s * 0.4, c + s * 0.3), (c + s * 0.55, c + s * 0.42), (c + s * 0.85, c + s * 0.25), (c + s * 0.6, c + s * 0.75), (c, c + s * 0.9), (c - s * 0.6, c + s * 0.75)]
    d.polygon(m, fill=255)
    return im
g = sigil(); gl = np.asarray(g.filter(ImageFilter.GaussianBlur(14)), float) / 255; gs = np.asarray(g.filter(ImageFilter.GaussianBlur(1.5)), float) / 255
a = np.clip(gs + gl * 0.8, 0, 1); a = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).resize((384, 384), Image.LANCZOS), float) / 255
save(np.ones((384, 384, 3)), a, "T_Sigil.png")

# ---------------- flipbooks (4x4 of 128) ----------------
C, G = 128, 4
cy_, cx_ = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 36, 36, 4, 11); nB = smooth_noise(C, C, 16, 16, 3, 12); stk = smooth_noise(C, C, 7, 45, 3, 13)
def flame_cell(t, stops, tint):
    R = 0.55 + 0.33 * t ** 0.6
    wx = cx_ + (nA - 0.5) * 0.45; wy = cy_ + (nB - 0.5) * 0.35 + 0.12
    up = np.clip(-wy, 0, None); rw = np.hypot(wx * (1 + 0.7 * up), wy * (1 - 0.4 * up)); body = 1 - rw / R
    heat = body * (0.75 + 0.45 * stk) - t ** 1.3 * 0.75 * (0.5 + nB)
    ac = ss(0.0, 0.10, heat); halo = np.exp(-(np.clip(-body, 0, None) / 0.18) ** 2) * 0.35 * (1 - t) ** 1.5
    rgb = ramp(np.clip(heat * 1.25, 0, 1) * (1 - 0.35 * t), stops)
    a = np.clip(ac + halo * (1 - ac), 0, 1); rgb = rgb * ac[..., None] + np.array(tint) * (1 - ac[..., None])
    return rgb, a * (1 - ss(0.88, 1.0, np.hypot(cx_, cy_)))
def mist_cell(t):
    R = 0.6 + 0.32 * t ** 0.5; wx = cx_ + (nB - 0.5) * 0.6; wy = cy_ + (nA - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R + (stk - 0.5) * 0.25 - t ** 1.5 * 0.8 * (0.5 + nA)
    a = ss(0.0, 0.5, body) * 0.6 * (1 - 0.3 * t); lit = ss(0.1, 0.9, -wy * 0.8 + 0.2)[..., None] * (1 - t) * 0.6
    rgb = np.array([0.10, 0.05, 0.14]) * (1 - lit) + np.array([0.42, 0.16, 0.55]) * lit
    return rgb, a * (1 - ss(0.88, 1.0, np.hypot(cx_, cy_)))
def sheet(fn, name):
    rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
    for i in range(G*G):
        r, c = divmod(i, G); rgb, a = fn(i / (G*G - 1)); rgbS[r*C:(r+1)*C, c*C:(c+1)*C] = rgb; aS[r*C:(r+1)*C, c*C:(c+1)*C] = a
    save(rgbS, aS, name)
sheet(lambda t: flame_cell(t, FIRE, (1, 0.45, 0.12)), "T_PumpkinFlame_Sheet.png")
sheet(lambda t: flame_cell(t, SOUL, (0.2, 0.9, 0.75)), "T_SoulFlame_Sheet.png")
sheet(mist_cell, "T_Mist_Sheet.png")
sheet(lambda t: flame_cell(t, VIOL, (0.7, 0.35, 1.0)), "T_VioletFlame_Sheet.png")

# ---------------- scythe slash wipe 2x8 (256x48 cells): orange edge → violet body ----------------
CW, CH = 256, 48
u = np.linspace(0, 1, CW)[None, :]; v = np.linspace(0, 1, CH)[:, None]
N1 = smooth_noise(CW, CH, 40, 40, 4, 21); N2 = smooth_noise(CW, CH, 12, 12, 3, 22); strands = smooth_noise(CW, CH, 120, 4, 3, 23)
SLASH = [(1.0,(1,1,0.92)),(0.82,(1,0.80,0.40)),(0.62,(1,0.48,0.14)),(0.45,(0.85,0.26,0.40)),(0.28,(0.55,0.20,0.80)),(0.0,(0.18,0.05,0.35))]
def slash_cell(t):
    head = min(1.0, t / 0.5) ** 0.7; tail = max(0.0, (t - 0.15) / 0.85) ** 1.3
    rel = np.clip((u - tail) / max(head - tail, 1e-3), 0, 1)
    inside = ss(tail, tail + 0.05, u) * (1 - ss(head - 0.012, head + 0.004, u)); width = np.sin(np.pi * rel) ** 0.5
    d = np.clip(1 - v, 0, 1) / (0.95 * width + 1e-3)   # outer edge at V=1 (sharp), body toward V=0
    core = np.exp(-(d / 0.12) ** 2); body = np.clip(1 - d, 0, 1) ** 1.2 * (0.55 + 0.45 * strands)
    heat = np.clip(core + body * 0.7, 0, 1) * (0.4 + 0.6 * rel ** 0.6)
    if t > 0.45: heat = heat * (1 - ss(0.0, 1.0, (t - 0.45) * 2.2 * (0.6 + N2)))
    a = np.clip(ss(0.02, 0.22, heat) + np.exp(-(d / 0.9) ** 2) * 0.22 * width, 0, 1) * inside
    return ramp(heat, SLASH), a
rgbS = np.zeros((CH*8, CW*2, 3)); aS = np.zeros((CH*8, CW*2))
for i in range(16):
    r, c = divmod(i, 2); rgb, a = slash_cell(i / 15); rgbS[r*CH:(r+1)*CH, c*CW:(c+1)*CW] = rgb; aS[r*CH:(r+1)*CH, c*CW:(c+1)*CW] = a
save(rgbS, aS, "T_ScytheSlash_Sheet.png")

# ---------------- small sprites ----------------
W, H = 256, 32; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
taper = x ** 1.6 * (1 - ss(0.92, 1.0, x))
core = np.exp(-(dy / (0.09 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.5 * taper + 1e-3)) ** 2) * 0.45 * taper
save(ramp(0.35 + 0.65 * np.clip(core + halo * 0.5, 0, 1), FIRE), np.clip(core + halo, 0, 1), "T_FireStreak.png")
save(ramp(0.35 + 0.65 * np.clip(core + halo * 0.5, 0, 1), SOUL), np.clip(core + halo, 0, 1), "T_SoulStreak.png")
nS = smooth_noise(W, H, 20, 20, 3, 41); profS = np.sin(np.pi * x) ** 0.7 * (0.4 + 0.6 * x)
dist = 0.5 * profS - (dy + (nS - 0.5) * 0.25); a = ss(-0.03, 0.02, dist); edge = np.exp(-(np.clip(dist, 0, None) / 0.05) ** 2)[..., None]
save(np.array([0.07, 0.03, 0.09]) * (1 - edge) + np.array([1.0, 0.5, 0.15]) * edge, a, "T_InkShard.png")
M = 128; my, mx = np.mgrid[-1:1:M*1j, -1:1:M*1j]; mr = np.hypot(mx, my) + 1e-6; ax_, ay_ = np.abs(mx), np.abs(my)
spike = np.exp(-ay_ / (0.02 * (1 - np.clip(ax_, 0, 1)) + 1e-3)) * (1 - ax_) ** 2 + np.exp(-ax_ / (0.02 * (1 - np.clip(ay_, 0, 1)) + 1e-3)) * (1 - ay_) ** 2
glow = np.exp(-(mr / 0.16) ** 2) * 0.9 + np.exp(-(mr / 0.5) ** 2) * 0.3
save(np.ones((M, M, 3)), np.clip(spike + glow, 0, 1), "T_Star.png")
gg = np.exp(-(mr / 0.35) ** 2) * 0.85 + np.exp(-(mr / 0.75) ** 2) * 0.25; gg *= 1 - ss(0.9, 1.0, mr); save(np.ones((M, M, 3)), gg, "T_Glow.png")
D = 64; dy2, dx2 = np.mgrid[-1:1:D*1j, -1:1:D*1j]; dr = np.hypot(dx2, dy2)
save(np.ones((D, D, 3)), np.clip(np.exp(-(dr / 0.3) ** 2) + (1 - ss(0.08, 0.14, dr)), 0, 1) * (1 - ss(0.85, 1, dr)), "T_Dot.png")
nR = smooth_noise(S, S, 10, 10, 3, 51); ring = np.exp(-((rr - 0.8) / 0.025) ** 2); inner = np.exp(-((rr - 0.7) / 0.13) ** 2) * (rr < 0.8) * (0.5 + 0.6 * nR)
save(np.ones((S, S, 3)), np.clip(ring + inner * 0.55, 0, 1) * (1 - ss(0.95, 1.0, rr)), "T_Ring.png")
# shockwave ring with jagged crest (grey-white so tint colours it)
crest = np.exp(-((rr - 0.85 - (nR - 0.5) * 0.06) / 0.04) ** 2); trail = np.exp(-((rr - 0.7) / 0.15) ** 2) * (rr < 0.85) * (0.4 + 0.6 * nR)
save(np.ones((S, S, 3)), np.clip(crest + trail * 0.5, 0, 1) * (1 - ss(0.95, 1, rr)), "T_Shock.png")
# ground crack decal: dark cracks with glowing orange cores
def cracks(size=768, seed=5):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2; rng = np.random.default_rng(seed)
    for k in range(11):
        a0 = k / 11 * 2 * math.pi + rng.uniform(-0.2, 0.2); px, py = c, c; L = size * rng.uniform(0.32, 0.47); n = 9
        for j in range(n):
            a0 += rng.uniform(-0.45, 0.45); nx, ny = px + math.cos(a0) * L / n, py + math.sin(a0) * L / n
            d.line([(px, py), (nx, ny)], fill=255, width=max(2, int(12 * (1 - j / n)))); px, py = nx, ny
            if rng.random() < 0.3:
                b = a0 + rng.choice([-1, 1]) * 0.9; d.line([(px, py), (px + math.cos(b) * L * 0.18, py + math.sin(b) * L * 0.18)], fill=255, width=3)
    return im
cr = cracks(); cg = np.asarray(cr.filter(ImageFilter.GaussianBlur(6)).resize((S, S), Image.LANCZOS), float) / 255
cs = np.asarray(cr.filter(ImageFilter.GaussianBlur(1)).resize((S, S), Image.LANCZOS), float) / 255
dark = np.clip(cg * 1.6, 0, 1) * 0.85 * (1 - ss(0.8, 1, rr)); hot = cs
rgb = np.array([0.06, 0.03, 0.06]) * (1 - hot[..., None]) + ramp(0.55 + 0.45 * hot, FIRE) * hot[..., None]
save(rgb, np.clip(dark + hot, 0, 1) * (1 - ss(0.85, 1, rr)), "T_Crack.png")
# bat silhouette (dark body, violet rim) 128
def bat(size=512):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2; s = size * 0.42
    pts = [(c - s, c - s * 0.15), (c - s * 0.6, c - s * 0.45), (c - s * 0.45, c - s * 0.12), (c - s * 0.2, c - s * 0.3), (c - s * 0.08, c - s * 0.42), (c, c - s * 0.25),
           (c + s * 0.08, c - s * 0.42), (c + s * 0.2, c - s * 0.3), (c + s * 0.45, c - s * 0.12), (c + s * 0.6, c - s * 0.45), (c + s, c - s * 0.15), (c + s * 0.55, c + s * 0.2),
           (c + s * 0.3, c + s * 0.05), (c + s * 0.12, c + s * 0.3), (c, c + s * 0.42), (c - s * 0.12, c + s * 0.3), (c - s * 0.3, c + s * 0.05), (c - s * 0.55, c + s * 0.2)]
    d.polygon(pts, fill=255); return im
bm = bat(); body = np.asarray(bm.filter(ImageFilter.GaussianBlur(1.5)).resize((M, M), Image.LANCZOS), float) / 255
er = np.asarray(bm.filter(ImageFilter.MinFilter(9)).filter(ImageFilter.GaussianBlur(2)).resize((M, M), Image.LANCZOS), float) / 255
rimb = np.clip(body - er, 0, 1); rgb = np.array([0.05, 0.02, 0.07]) * (1 - rimb[..., None] * 2) + np.array([0.75, 0.4, 1.0]) * np.clip(rimb * 2, 0, 1)[..., None]
save(np.clip(rgb, 0, 1), body, "T_Bat.png")
# wrapped candy sprite (bright, alpha) 128
def candy(size=512):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0)); d = ImageDraw.Draw(im); c = size / 2
    for sgn in (-1, 1):
        d.polygon([(c + sgn * 70, c), (c + sgn * 200, c - 90), (c + sgn * 170, c), (c + sgn * 200, c + 90)], fill=(255, 150, 220, 255))
    d.ellipse([c - 95, c - 80, c + 95, c + 80], fill=(255, 110, 40, 255))
    for k in range(5): d.arc([c - 95, c - 80, c + 95, c + 80], k * 72, k * 72 + 30, fill=(255, 245, 210, 255), width=26)
    d.ellipse([c - 55, c - 50, c - 15, c - 20], fill=(255, 255, 255, 220))
    return im.filter(ImageFilter.GaussianBlur(1.5)).resize((M, M), Image.LANCZOS)
candy().save(OUT + "T_Candy.png")
# hex barrier panel (violet) 256
hx = np.zeros((S, S)); scale = 9
q = (xx * scale); r_ = (yy * scale) / np.sqrt(3) * 2
def hexdist(X, Y):
    X, Y = np.abs(X), np.abs(Y); return np.maximum(X * 0.5 + Y * np.sqrt(3) / 2, X)
gx = xx * scale; gy = yy * scale
cx2 = np.round(gx / 1.5) * 1.5; cy2 = np.round((gy - (np.round(gx / 1.5) % 2) * np.sqrt(3) / 2) / np.sqrt(3)) * np.sqrt(3) + (np.round(gx / 1.5) % 2) * np.sqrt(3) / 2
hd = hexdist(gx - cx2, gy - cy2)
edge = np.exp(-((hd - 0.92) / 0.06) ** 2)
fres = ss(0.4, 1.0, rr) ** 1.5
a = np.clip(edge * (0.35 + 0.65 * fres) + 0.18 * fres + 0.05, 0, 1) * (1 - ss(0.96, 1, rr))
save(ramp(0.5 + 0.5 * np.clip(edge + fres * 0.5, 0, 1), VIOL), a, "T_Hex.png")
# soul wisp (ghost streak with eye holes) 128x256 vertical: head at top
GW, GH = 128, 256; gy_, gx_ = np.mgrid[0:1:GH*1j, -1:1:GW*1j]; nW = smooth_noise(GW, GH, 20, 60, 3, 61)
wid = 0.62 * (1 - gy_) ** 0.6 * (0.6 + 0.8 * np.exp(-((gy_ - 0.18) / 0.2) ** 2)); wx = gx_ + (nW - 0.5) * 0.5 * gy_
body = 1 - np.abs(wx) / (wid + 1e-3); headb = np.clip(1 - np.hypot(gx_ / 0.62, (gy_ - 0.2) / 0.2), 0, 1)
dens = np.clip(body, 0, 1) * (1 - ss(0.6, 1.0, gy_)) + headb
eyes = np.exp(-(np.hypot((gx_ - 0.22) / 0.09, (gy_ - 0.18) / 0.05)) ** 2) + np.exp(-(np.hypot((gx_ + 0.22) / 0.09, (gy_ - 0.18) / 0.05)) ** 2)
a = np.clip(ss(0.0, 0.35, dens) - eyes * 0.9, 0, 1); save(ramp(np.clip(0.4 + dens * 0.6, 0, 1), SOUL), a, "T_Ghost.png")
# bone claw (dark bone finger, teal rim) 64x256, base at bottom, tip up
BW, BH = 64, 256; by_, bx_ = np.mgrid[0:1:BH*1j, -1:1:BW*1j]
bend = 0.35 * by_ ** 2.2; wdt = 0.55 * (1 - by_ ** 1.5) + 0.04; knuck = 0.12 * (np.exp(-((by_ - 0.35) / 0.04) ** 2) + np.exp(-((by_ - 0.65) / 0.035) ** 2))
dd = np.abs(bx_ - bend) / (wdt + knuck); a = ss(1.02, 0.9, dd) * (1 - ss(0.97, 1.0, by_)) * ss(0.0, 0.05, by_)
rimc = np.exp(-((dd - 0.85) / 0.1) ** 2)[..., None]
rgb = np.array([0.20, 0.17, 0.15]) * (1 - rimc) + np.array([0.35, 1.0, 0.85]) * rimc; rgb = rgb * (0.7 + 0.3 * (1 - by_))[..., None]
save(np.clip(rgb, 0, 1), a, "T_BoneClaw.png")
# chain link strip (U along, 4 links) 256x32
CW2, CH2 = 256, 32; cy3, cx3 = np.mgrid[-1:1:CH2*1j, 0:4:CW2*1j]; fx = (cx3 % 1) - 0.5
ell = np.hypot(fx / 0.42, cy3 / 0.62); lk = np.exp(-((ell - 1) / 0.16) ** 2); alt = (np.floor(cx3) % 2 == 1)
lk = np.where(alt, np.exp(-(np.abs(cy3) / 0.14) ** 2) * (np.abs(fx) < 0.45) * 0.9, lk)
save(ramp(0.4 + 0.6 * np.clip(lk, 0, 1), SOUL), np.clip(lk + 0.25 * np.exp(-(cy3 / 0.6) ** 2), 0, 1), "T_Chain.png")
print("ok")
