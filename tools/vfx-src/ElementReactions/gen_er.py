# ElementReactions shared textures: mostly white/greyscale, coloured per reaction by material tint + colorOverLifetime.
import sys, numpy as np
from PIL import Image
OUT = sys.argv[1] + "/"
def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def save(rgb, a, name):
    rgb = np.broadcast_to(rgb, a.shape + (3,)) if np.ndim(rgb) == 1 else rgb
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]]) * 255).astype(np.uint8), "RGBA").save(OUT + name)
def grid(S):
    yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; return xx, yy, np.hypot(xx, yy) + 1e-6
def hot(core):  # white core → slightly dimmer edge so the tint shows the hue
    return np.dstack([0.72 + 0.28 * core] * 3)

# glow / dot
xx, yy, r = grid(128)
g = (np.exp(-(r / 0.3) ** 2) * 0.9 + np.exp(-(r / 0.7) ** 2) * 0.3) * (1 - ss(0.9, 1, r))
save(hot(np.exp(-(r / 0.25) ** 2)), g, "T_Glow.png")
xx, yy, r = grid(64)
save(np.ones(3), np.clip(np.exp(-(r / 0.3) ** 2) + (1 - ss(0.1, 0.18, r)), 0, 1) * (1 - ss(0.85, 1, r)), "T_Dot.png")

# 4-spike star
xx, yy, r = grid(256); ax, ay = np.abs(xx), np.abs(yy)
spike = np.exp(-ay / (0.018 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (0.018 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
glow = np.exp(-(r / 0.14) ** 2) * 0.9 + np.exp(-(r / 0.45) ** 2) * 0.25
save(hot(np.clip(spike + np.exp(-(r / 0.1) ** 2), 0, 1)), np.clip(spike + glow, 0, 1) * (1 - ss(0.95, 1, r)), "T_Star.png")

# thin ring with soft inner wash
n = smooth_noise(256, 256, 20, 20, 3, 5)
ring = np.exp(-((r - 0.82) / 0.03) ** 2)
inner = np.exp(-((r - 0.72) / 0.12) ** 2) * (r < 0.82) * (0.5 + 0.6 * n)
save(hot(ring), np.clip(ring + inner * 0.4, 0, 1) * (1 - ss(0.95, 1, r)), "T_Ring.png")

# spiral swirl arcs (top view decal; additive)
ang = np.arctan2(yy, xx); nS = smooth_noise(256, 256, 30, 30, 3, 7)
arms = 3; phase = ang * arms / (2*np.pi) + r * 1.6 + (nS - 0.5) * 0.25
band = np.abs(((phase % 1) + 1) % 1 - 0.5) * 2         # 0 at arm centre
line = np.exp(-(band / (0.10 + 0.12 * r)) ** 2) * ss(0.15, 0.4, r) * (1 - ss(0.82, 0.98, r))
streak = 0.6 + 0.4 * smooth_noise(256, 256, 8, 40, 3, 8)
save(hot(line), np.clip(line * streak, 0, 1), "T_Swirl.png")

# ground crack decal (glowing seams, dark rims baked into rgb, alpha only on cracks)
nC = smooth_noise(256, 256, 18, 18, 4, 9)
cr = np.zeros_like(r)
rng = np.random.default_rng(3)
for k in range(7):
    a0 = k * 2*np.pi/7 + rng.uniform(-0.3, 0.3)
    wob = a0 + (nC - 0.5) * 0.9 * r
    d = np.abs(np.angle(np.exp(1j * (ang - wob)))) * r      # distance from the ray
    lenk = rng.uniform(0.6, 0.92)
    cr = np.maximum(cr, np.exp(-(d / (0.035 * (1 - r / lenk).clip(0.15, 1))) ** 2) * (r < lenk) * ss(lenk, lenk - 0.2, r))
cr = np.maximum(cr, np.exp(-(r / 0.16) ** 2))
save(hot(cr ** 0.6), np.clip(cr * 1.2, 0, 1), "T_Crack.png")

# soft puff flipbook 4x4 (64 px cells): round mist that loosens and fades
C, G = 64, 4; xx, yy, r = grid(C)
nA = smooth_noise(C, C, 18, 18, 3, 11); nB = smooth_noise(C, C, 9, 9, 3, 12)
rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
for i in range(G*G):
    t = i / (G*G - 1); R = 0.55 + 0.35 * t ** 0.6
    body = 1 - np.hypot(xx + (nA - .5) * .5, yy + (nB - .5) * .5) / R - t ** 1.4 * 0.7 * (0.4 + nA)
    a = ss(0.0, 0.5, body) * (1 - 0.4 * t) * (1 - ss(0.85, 1, r))
    rr, cc = divmod(i, G)
    rgbS[rr*C:(rr+1)*C, cc*C:(cc+1)*C] = np.dstack([0.85 + 0.15 * ss(0, .6, body)] * 3)
    aS[rr*C:(rr+1)*C, cc*C:(cc+1)*C] = a
save(rgbS, aS, "T_Puff_Sheet.png")

# streak (stretched sparks, head right)
W, H = 256, 32
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
taper = x ** 1.5 * (1 - ss(0.9, 1, x))
core = np.exp(-(dy / (0.12 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.5 * taper + 1e-3)) ** 2) * 0.4 * taper
save(hot(core), np.clip(core + halo, 0, 1), "T_Streak.png")

# lightning bolt flipbook 1x4 (512x64 cells; u = along the bolt)
W, H = 512, 64; rows = []
for k in range(4):
    rng = np.random.default_rng(40 + k)
    pts = np.cumsum(rng.normal(0, 0.16, 15)); pts -= np.linspace(pts[0], pts[-1], 15); pts = np.clip(pts, -0.55, 0.55)
    xs = np.linspace(0, 1, W); yc = np.interp(xs, np.linspace(0, 1, 15), pts)
    yc += (smooth_noise(W, 1, 6, 1, 2, 60 + k)[0] - 0.5) * 0.12
    yv = np.linspace(-1, 1, H)[:, None]; d = np.abs(yv - yc[None, :])
    env = ss(0.0, 0.05, xs) * ss(1.0, 0.95, xs)
    core = np.exp(-(d / 0.05) ** 2) * env; halo = np.exp(-(d / 0.3) ** 2) * 0.45 * env
    rows.append((hot(core), np.clip(core + halo, 0, 1)))
save(np.vstack([r_[0] for r_ in rows]), np.vstack([r_[1] for r_ in rows]), "T_Bolt_Sheet.png")

# crystal shard (64x128, faceted diamond with bright edge)
W, H = 64, 128
x = np.linspace(-1, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
dist = 1 - (np.abs(x) / 0.55 + np.abs(y)); a = ss(0.0, 0.06, dist)
facet = np.where(x < 0, 0.75, 1.0) * (0.8 + 0.2 * (-y))
edge = np.exp(-(np.clip(dist, 0, None) / 0.08) ** 2)
save(np.dstack([np.clip(facet * 0.85 + edge * 0.4, 0, 1)] * 3), np.clip(a * (0.55 + 0.45 * edge) + np.exp(-(np.clip(-dist, 0, None) / 0.12) ** 2) * 0.3, 0, 1), "T_Shard.png")

# earth / mud chunks 2x2 (64 px cells), alpha-blended, shaded rgb (tinted brown per use)
C = 64; xx, yy, r = grid(C); rgbS = np.zeros((128, 128, 3)); aS = np.zeros((128, 128))
for i in range(4):
    nk = smooth_noise(C, C, 10, 10, 3, 70 + i)
    rr = 0.55 + (nk - 0.5) * 0.45; body = 1 - r / rr
    a = ss(0.0, 0.08, body)
    lit = np.clip(0.55 + 0.45 * (-xx * 0.5 - yy * 0.8) * 0.8 + (smooth_noise(C, C, 5, 5, 2, 80 + i) - .5) * .3, 0.25, 1)
    ro, co = divmod(i, 2)
    rgbS[ro*C:(ro+1)*C, co*C:(co+1)*C] = np.dstack([lit] * 3); aS[ro*C:(ro+1)*C, co*C:(co+1)*C] = a
save(rgbS, aS, "T_Chunk_Sheet.png")

# rift slit (64x256): jagged vertical tear, bright edge, dark-violet core baked in alpha-additive (white edge, tint gives violet)
W, H = 64, 256
x = np.linspace(-1, 1, W)[None, :]; yv = np.linspace(-1, 1, H)[:, None]
jag = (smooth_noise(1, H, 1, 10, 3, 90)[:, :1] - 0.5) * 0.35
wdt = 0.28 * np.cos(np.pi / 2 * np.abs(yv)) ** 1.3
d = np.abs(x - jag) / (wdt + 1e-3)
edge = np.exp(-((d - 1) / 0.25) ** 2); inner = (d < 1) * 0.35
halo = np.exp(-(np.clip(d - 1, 0, None) / 1.2) ** 2) * 0.35 * (wdt / 0.28)
save(hot(edge), np.clip(edge + inner + halo, 0, 1) * ss(1, 0.92, np.abs(yv)), "T_Rift.png")

# sigil (brand circle): ring + inner triangle strokes + tick marks
xx, yy, r = grid(256); ang = np.arctan2(yy, xx)
sig = np.exp(-((r - 0.8) / 0.025) ** 2) + np.exp(-((r - 0.66) / 0.015) ** 2) * 0.8
for k in range(3):
    th = np.pi / 2 + k * 2 * np.pi / 3; nx, ny = np.cos(th), np.sin(th)
    d = np.abs(xx * nx + yy * ny + 0.33)             # triangle sides
    sig += np.exp(-(d / 0.02) ** 2) * (r < 0.66)
ticks = (np.abs(np.angle(np.exp(1j * ang * 12))) < 0.12) * ((r > 0.84) & (r < 0.93))
sig = np.clip(sig + ticks * 0.9, 0, 1)
save(hot(sig), np.clip(sig + np.exp(-((r - 0.75) / 0.2) ** 2) * 0.2, 0, 1) * (1 - ss(0.95, 1, r)), "T_Sigil.png")

# dark disc (eclipse body), alpha-blended
xx, yy, r = grid(128)
save(np.array([0.02, 0.01, 0.04]) + 0 * r[..., None], (1 - ss(0.80, 0.86, r)), "T_Disc.png")
print("ok")
# billboards are square: pad the tall textures onto square canvases (slit / shard stay centred)
for name, S in (("T_Rift.png", 256), ("T_Shard.png", 128)):
    im = Image.open(OUT + name); c = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    c.paste(im, ((S - im.size[0]) // 2, (S - im.size[1]) // 2)); c.save(OUT + name)

# ================================================================ 2026-10-20 additions (CC reaction rework)
from PIL import ImageDraw, ImageFilter
def glyph(draw_fn, S=128, k=3, blur=6, core_w=1.0):
    """Line art drawn at k x with PIL, white on clear, + gaussian glow halo; downsampled to S."""
    big = Image.new("L", (S * k, S * k), 0); d = ImageDraw.Draw(big); draw_fn(d, S * k)
    line = np.asarray(big.resize((S, S), Image.LANCZOS), float) / 255
    halo = np.asarray(big.filter(ImageFilter.GaussianBlur(blur * k)).resize((S, S), Image.LANCZOS), float) / 255
    a = np.clip(line * core_w + halo * 1.6, 0, 1)
    return hot(np.clip(line, 0, 1)), a

# thin line profile for mesh rings (u along the ring, v across)
W, H = 64, 32; v = np.linspace(-1, 1, H)[:, None] + 0 * np.zeros((1, W))
core = np.exp(-(v / 0.16) ** 2); halo = np.exp(-(v / 0.6) ** 2) * 0.45
save(hot(core), np.clip(core + halo, 0, 1) * (1 - ss(0.9, 1, np.abs(v))), "T_Line.png")

# flame tongue flipbook 4x4 (64 px), grey-hot (tinted per use)
C = 64; yy, xx = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 16, 16, 3, 31); nB = smooth_noise(C, C, 8, 8, 3, 32); stk = smooth_noise(C, C, 4, 22, 3, 33)
rgbS = np.zeros((C*4, C*4, 3)); aS = np.zeros((C*4, C*4))
for i in range(16):
    t = i / 15
    wx = xx + (nA - .5) * .5 * (1 - yy) * .6; wy = yy + 0.25
    width = 0.7 * np.clip((wy + 1) / 1.6, 0, 1) ** 0.8 * (1 - 0.25 * t)
    tip = ss(-1.0 + 0.5 * t, -0.2, wy)                        # tongue rises, tip burns away over time
    body = 1 - np.abs(wx) / (width + 1e-3)
    heat = np.clip(body, 0, 1) * tip * (0.7 + 0.4 * stk) - t ** 1.4 * 0.6 * (0.4 + nB)
    a = ss(0.0, 0.12, heat) + np.exp(-(np.clip(-body, 0, None) / 0.5) ** 2) * 0.25 * tip * (1 - t)
    a *= ss(1.0, 0.75, wy)                                     # soft base
    r_, c_ = divmod(i, 4)
    rgbS[r_*C:(r_+1)*C, c_*C:(c_+1)*C] = hot(np.clip(heat * 1.4, 0, 1)); aS[r_*C:(r_+1)*C, c_*C:(c_+1)*C] = np.clip(a, 0, 1)
save(rgbS, aS, "T_FlameTongue_Sheet.png")

# small lightning arcs 2x2 (128 px): jagged arc segments for the disarm crackle
C = 128; rgbS = np.zeros((256, 256, 3)); aS = np.zeros((256, 256))
for i in range(4):
    rng = np.random.default_rng(100 + i); n = 9
    pts = [(0.12 + 0.76 * k / (n - 1), 0.5 + rng.normal(0, 0.13)) for k in range(n)]
    def dr(d, S, pts=pts, rng=rng):
        P = [(x * S, y * S) for x, y in pts]
        d.line(P, fill=255, width=int(S * 0.025), joint="curve")
        for k in (2, 5):                                         # two small forks
            x, y = P[k]; d.line([(x, y), (x + S * 0.12, y + S * rng.choice([-0.18, 0.18]))], fill=200, width=int(S * 0.015))
    rgb, a = glyph(dr, S=C, blur=4)
    r_, c_ = divmod(i, 2); rgbS[r_*C:(r_+1)*C, c_*C:(c_+1)*C] = rgb; aS[r_*C:(r_+1)*C, c_*C:(c_+1)*C] = a
save(rgbS, aS, "T_Arc_Sheet.png")

# bubble (ring with highlight)
xx, yy, r = grid(64)
bub = np.exp(-((r - 0.7) / 0.08) ** 2) + np.exp(-(np.hypot(xx + 0.3, yy + 0.3) / 0.15) ** 2) * 0.9 + (r < 0.7) * 0.15
save(hot(np.clip(bub, 0, 1)), np.clip(bub, 0, 1) * (1 - ss(0.9, 1, r)), "T_Bubble.png")

# glyphs (192 px): heal cross, broken heal seal, crescent moon, Z, mana diamond, weak arrow
def cross(d, S, w=0.13):
    c = S / 2; a = S * 0.32; b = S * w
    d.polygon([(c - b, c - a), (c + b, c - a), (c + b, c - b), (c + a, c - b), (c + a, c + b), (c + b, c + b), (c + b, c + a),
               (c - b, c + a), (c - b, c + b), (c - a, c + b), (c - a, c - b), (c - b, c - b)], outline=255, width=int(S * 0.03))
rgb, a = glyph(lambda d, S: cross(d, S)); save(rgb, a, "T_Cross.png")
def seal(d, S):
    cross(d, S, 0.11); c = S / 2
    d.ellipse([S * 0.1, S * 0.1, S * 0.9, S * 0.9], outline=255, width=int(S * 0.035))
    d.line([(S * 0.2, S * 0.8), (S * 0.8, S * 0.2)], fill=255, width=int(S * 0.06))
rgb, a = glyph(seal); save(rgb, a, "T_Seal.png")
def moon(d, S):
    d.ellipse([S * 0.18, S * 0.18, S * 0.82, S * 0.82], fill=255)
    d.ellipse([S * 0.34, S * 0.1, S * 0.94, S * 0.7], fill=0)
rgb, a = glyph(moon, blur=5, core_w=0.9); save(rgb, a, "T_Moon.png")
def zz(d, S):
    w = int(S * 0.07)
    d.line([(S * 0.28, S * 0.28), (S * 0.72, S * 0.28), (S * 0.28, S * 0.72), (S * 0.72, S * 0.72)], fill=255, width=w, joint="curve")
rgb, a = glyph(zz, blur=4); save(rgb, a, "T_Z.png")
def diamond(d, S):
    c = S / 2
    d.polygon([(c, S * 0.1), (S * 0.78, c), (c, S * 0.9), (S * 0.22, c)], outline=255, width=int(S * 0.04))
    d.polygon([(c, S * 0.3), (S * 0.62, c), (c, S * 0.7), (S * 0.38, c)], fill=170)
    d.line([(S * 0.42, S * 0.18), (S * 0.56, S * 0.45), (S * 0.46, S * 0.6), (S * 0.6, S * 0.85)], fill=0, width=int(S * 0.035))
rgb, a = glyph(diamond); save(rgb, a, "T_ManaCrack.png")
def arrow(d, S):
    c = S / 2; w = int(S * 0.06)
    for y0 in (0.18, 0.44):
        d.line([(S * 0.26, S * y0), (c, S * (y0 + 0.24)), (S * 0.74, S * y0)], fill=255, width=w, joint="curve")
rgb, a = glyph(arrow); save(rgb, a, "T_Down.png")

# chain strip (256x64, u along, tiles horizontally): oval links
def chain(d, S):
    pass
W, H = 256, 64; big = Image.new("L", (W * 3, H * 3), 0); dd = ImageDraw.Draw(big)
for k in range(5):
    x0 = k * W * 3 / 4 - W * 3 / 8 * 0
    if k % 2 == 0: dd.ellipse([x0 - 70, 36, x0 + 110, 156], outline=255, width=14)
    else: dd.line([(x0 - 40, 96), (x0 + 80, 96)], fill=255, width=16)
line = np.asarray(big.resize((W, H), Image.LANCZOS), float) / 255
halo = np.asarray(big.filter(ImageFilter.GaussianBlur(12)).resize((W, H), Image.LANCZOS), float) / 255
save(hot(line), np.clip(line + halo * 1.4, 0, 1), "T_Chain.png")

# dark wisp stroke (256x64, head right): smoky tapered tail for fear
W, H = 256, 64
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
nW = smooth_noise(W, H, 40, 6, 3, 120)
wy = y + (nW - 0.5) * 0.5 * (1 - x)
taper = np.sin(np.pi * np.clip(x, 0, 1)) ** 0.7 * x ** 0.4
d = np.abs(wy) / (0.55 * taper + 1e-3)
body = np.clip(1 - d, 0, 1) ** 0.8 * (0.8 + 0.4 * smooth_noise(W, H, 60, 4, 3, 121))
save(hot(np.exp(-(d / 0.3) ** 2) * taper), np.clip(body, 0, 1), "T_Wisp.png")
print("ok2")
