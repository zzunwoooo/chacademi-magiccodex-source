# ElementReactions v3 textures (2026-10-20): painted per-reaction textures, colour baked in (white-hot core -> hue -> deep hue).
# Materials tint these only for HDR brightness. python gen_er2.py <outdir>
import sys, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = sys.argv[1] + "/"
R0 = np.random.default_rng(7)

def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def ramp(h, stops):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / max(h1 - h0, 1e-6))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
def save(rgb, a, name):
    rgb = np.broadcast_to(rgb, a.shape + (3,)) if np.ndim(rgb) == 1 else rgb
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]]) * 255).astype(np.uint8), "RGBA").save(OUT + name)
def polar(S):
    yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; return xx, yy, np.hypot(xx, yy) + 1e-6, np.arctan2(yy, xx)
def polar_noise(S, na, nr, seed, oct=3):
    """noise sampled in (angle, radius) so streaks run around / along the radius."""
    xx, yy, r, ang = polar(S); N = smooth_noise(512, 128, 512 / na, 128 / nr, oct, seed)
    ai = ((ang + np.pi) / (2 * np.pi) * 511).astype(int) % 512; ri = np.clip(r * 127, 0, 127).astype(int)
    return N[ri, ai]
def hot_ramp(c):  # generic ramp for a hue c: deep -> c -> white
    c = np.array(c); return [(1.0, (1, 1, 0.97)), (0.75, tuple(np.clip(c * 0.5 + 0.55, 0, 1))), (0.45, tuple(c)), (0.0, tuple(c * 0.35))]

FIRE = [(1.0, (1, 1, 0.92)), (0.8, (1, 0.9, 0.55)), (0.6, (1, 0.62, 0.2)), (0.4, (0.95, 0.33, 0.07)), (0.2, (0.7, 0.12, 0.04)), (0.0, (0.3, 0.04, 0.04))]

# ---------------------------------------------------------------- shared small pieces (grey-hot, tinted per use)
xx, yy, r, ang = polar(128)
g = (np.exp(-(r / 0.3) ** 2) * 0.9 + np.exp(-(r / 0.7) ** 2) * 0.3) * (1 - ss(0.9, 1, r))
save(np.dstack([0.72 + 0.28 * np.exp(-(r / 0.25) ** 2)] * 3), g, "T_Glow.png")
xx, yy, r, ang = polar(64)
save(np.ones(3), np.clip(np.exp(-(r / 0.3) ** 2) + (1 - ss(0.1, 0.18, r)), 0, 1) * (1 - ss(0.85, 1, r)), "T_Dot.png")
xx, yy, r, ang = polar(192); ax, ay = np.abs(xx), np.abs(yy)
spike = np.exp(-ay / (0.02 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (0.02 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
save(np.dstack([0.75 + 0.25 * np.clip(spike + np.exp(-(r / 0.1) ** 2), 0, 1)] * 3), np.clip(spike + np.exp(-(r / 0.14) ** 2) * 0.9 + np.exp(-(r / 0.45) ** 2) * 0.25, 0, 1) * (1 - ss(0.95, 1, r)), "T_Star.png")
W, H = 128, 32
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
taper = x ** 1.5 * (1 - ss(0.9, 1, x))
core = np.exp(-(dy / (0.14 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.5 * taper + 1e-3)) ** 2) * 0.4 * taper
save(np.dstack([0.72 + 0.28 * core] * 3), np.clip(core + halo, 0, 1), "T_Streak.png")
# soft mist 4x4 (64 px): dark accents (alpha-blended, tinted dark)
C = 48; xx, yy, r, ang = polar(C)
nA = smooth_noise(C, C, 14, 14, 3, 11); nB = smooth_noise(C, C, 7, 7, 3, 12)
rgbS = np.zeros((192, 192, 3)); aS = np.zeros((192, 192))
for i in range(16):
    t = i / 15; Rr = 0.55 + 0.35 * t ** 0.6
    body = 1 - np.hypot(xx + (nA - .5) * .5, yy + (nB - .5) * .5) / Rr - t ** 1.4 * 0.7 * (0.4 + nA)
    rr_, cc_ = divmod(i, 4)
    rgbS[rr_*C:(rr_+1)*C, cc_*C:(cc_+1)*C] = np.dstack([0.85 + 0.15 * ss(0, .6, body)] * 3)
    aS[rr_*C:(rr_+1)*C, cc_*C:(cc_+1)*C] = ss(0.0, 0.5, body) * (1 - 0.4 * t) * (1 - ss(0.85, 1, r))
save(rgbS, aS, "T_Mist_Sheet.png")
# rock chunks 2x2 (alpha, shaded, tinted)
C = 64; xx, yy, r, ang = polar(C); rgbS = np.zeros((128, 128, 3)); aS = np.zeros((128, 128))
for i in range(4):
    nk = smooth_noise(C, C, 10, 10, 3, 70 + i); rr = 0.55 + (nk - 0.5) * 0.45
    lit = np.clip(0.55 + 0.36 * (-xx * 0.5 - yy * 0.8) + (smooth_noise(C, C, 5, 5, 2, 80 + i) - .5) * .3, 0.25, 1)
    ro, co = divmod(i, 2); rgbS[ro*C:(ro+1)*C, co*C:(co+1)*C] = np.dstack([lit] * 3); aS[ro*C:(ro+1)*C, co*C:(co+1)*C] = ss(0, 0.08, 1 - r / rr)
save(rgbS, aS, "T_Chunk_Sheet.png")

# ---------------------------------------------------------------- painted shockwave (grey-hot, tinted): thick soft front, streaky trail inward
S = 192; xx, yy, r, ang = polar(S); pn = polar_noise(S, 40, 6, 201)
front = np.exp(-((r - 0.84) / 0.045) ** 2)
trail = ss(0.35, 0.84, r) * (1 - ss(0.84, 0.88, r)) * (0.35 + 0.65 * pn) ** 1.5
v = np.clip(front + trail * 0.55, 0, 1)
save(ramp(v, [(1, (1, 1, 1)), (0.5, (0.85, 0.85, 0.85)), (0, (0.55, 0.55, 0.55))]), np.clip(front * 1.1 + trail * 0.6, 0, 1) * (1 - ss(0.93, 1, r)), "T_Wave.png")

# ---------------------------------------------------------------- 1 steam: billowing steam sheet 4x4 (64 px), white with warm underlight
C = 48; xx, yy, r, ang = polar(C)
nA = smooth_noise(C, C, 11, 11, 4, 301); nB = smooth_noise(C, C, 6, 6, 3, 302)
rgbS = np.zeros((192, 192, 3)); aS = np.zeros((192, 192))
for i in range(16):
    t = i / 15; Rr = 0.5 + 0.38 * t ** 0.5
    wx = xx + (nA - .5) * .55; wy = yy + (nB - .5) * .55
    body = 1 - np.hypot(wx, wy) / Rr - t ** 1.5 * 0.75 * (0.45 + nA)
    dens = ss(0.0, 0.45, body)
    lit = ss(-0.6, 0.8, -wy - wx * 0.3)              # light from top-left, warm from below
    rgb = ramp(lit * 0.8 + 0.2 * (1 - t), [(1, (1, 1, 1)), (0.6, (0.88, 0.93, 1.0)), (0.3, (0.7, 0.78, 0.9)), (0, (0.95, 0.6, 0.4))])
    rr_, cc_ = divmod(i, 4)
    rgbS[rr_*C:(rr_+1)*C, cc_*C:(cc_+1)*C] = rgb; aS[rr_*C:(rr_+1)*C, cc_*C:(cc_+1)*C] = dens * (0.85 - 0.45 * t) * (1 - ss(0.85, 1, r))
save(rgbS, aS, "T_Steam_Sheet.png")

# ---------------------------------------------------------------- fire: flame flipbook 4x4 (64 px) with the painterly fire ramp
C = 64; xx, yy, r, ang = polar(C)
nA = smooth_noise(C, C, 18, 18, 4, 311); nB = smooth_noise(C, C, 8, 8, 3, 312); stk = smooth_noise(C, C, 4, 24, 3, 313)
rgbS = np.zeros((256, 256, 3)); aS = np.zeros((256, 256))
for i in range(16):
    t = i / 15; Rr = 0.82 + 0.12 * t ** 0.6
    wx = xx + (nA - 0.5) * 0.45; wy = yy + (nB - 0.5) * 0.35 + 0.12
    up = np.clip(-wy, 0, None)
    rw = np.hypot(wx * (1 + 0.8 * up), wy * (1 - 0.4 * up))
    body = 1 - rw / Rr
    heat = body * (0.75 + 0.45 * stk) - t ** 1.3 * 0.75 * (0.5 + nB)
    a_core = ss(0.0, 0.1, heat); halo = np.exp(-(np.clip(-body, 0, None) / 0.18) ** 2) * 0.35 * (1 - t) ** 1.5
    rgb = ramp(np.clip(heat * 1.3, 0, 1) * (1 - 0.3 * t), FIRE)
    rgb = rgb * a_core[..., None] + np.array([1.0, 0.45, 0.12]) * (1 - a_core[..., None])
    rr_, cc_ = divmod(i, 4)
    rgbS[rr_*C:(rr_+1)*C, cc_*C:(cc_+1)*C] = rgb; aS[rr_*C:(rr_+1)*C, cc_*C:(cc_+1)*C] = np.clip(a_core + halo * (1 - a_core), 0, 1) * (1 - ss(0.88, 1, r))
save(rgbS, aS, "T_Flame_Sheet.png")

# ---------------------------------------------------------------- 2 wildfire ground: burning ring with outward flame licks + ember cracks
S = 192; xx, yy, r, ang = polar(S); pn = polar_noise(S, 24, 4, 321); pn2 = polar_noise(S, 60, 10, 322)
lick = 0.80 + 0.13 * pn ** 1.5                                   # flame front radius varies around the ring
ringv = ss(lick - 0.22, lick - 0.02, r) * (1 - ss(lick - 0.02, lick + 0.06, r))
heat = ringv * (0.55 + 0.6 * pn2)
inner = ss(0.15, 0.7, r) * (1 - ss(0.7, 0.8, r)) * (pn2 > 0.62) * 0.6   # scattered embers inside
v = np.clip(heat + inner, 0, 1)
save(ramp(v, FIRE), np.clip(ss(0.05, 0.4, v) + np.exp(-((r - lick) / 0.12) ** 2) * 0.3, 0, 1) * (1 - ss(0.95, 1, r)), "T_FireRing.png")

# ---------------------------------------------------------------- dark scorched ground (alpha-blended, tinted per use: char / mud shadow)
S = 192; xx, yy, r, ang = polar(S); n = smooth_noise(S, S, 30, 30, 4, 331)
edge = 0.72 + (polar_noise(S, 14, 3, 332) - 0.5) * 0.35
save(np.dstack([0.25 + 0.5 * n] * 3), ss(edge, edge - 0.35, r) * (0.55 + 0.45 * n) * 0.9, "T_Char.png")

# ---------------------------------------------------------------- 3 magma: molten crack network glowing from the centre
S = 192; xx, yy, r, ang = polar(S)
n1 = smooth_noise(S, S, 22, 22, 4, 341); n2 = smooth_noise(S, S, 9, 9, 3, 342)
cr = np.exp(-(np.abs(n1 - 0.5) / 0.025) ** 2) + 0.6 * np.exp(-(np.abs(n2 - 0.5) / 0.02) ** 2)
fall = ss(0.95, 0.15, r)
pool = np.exp(-(r / 0.28) ** 2) * (0.7 + 0.3 * n2)
v = np.clip(cr * fall * 0.9 + pool, 0, 1)
save(ramp(np.clip(v * (0.55 + 0.6 * fall), 0, 1), FIRE), np.clip(ss(0.04, 0.3, v) * fall + pool * 0.3, 0, 1), "T_Lava.png")

# ---------------------------------------------------------------- 4 flare: painted sun burst (rays of varied length + corona)
S = 192; xx, yy, r, ang = polar(S); rng = np.random.default_rng(351)
rays = np.zeros_like(r)
for k in range(22):
    a0 = rng.uniform(0, 2 * np.pi); L = rng.uniform(0.45, 0.95); wdt = rng.uniform(0.012, 0.03)
    d = np.abs(np.angle(np.exp(1j * (ang - a0))))
    rays = np.maximum(rays, np.exp(-(d / (wdt * (1.2 - r))) ** 2) * ss(L, 0.1, r) * rng.uniform(0.5, 1))
corona = np.exp(-((r - 0.32) / 0.05) ** 2) * (0.7 + 0.3 * polar_noise(S, 30, 4, 352))
core = np.exp(-(r / 0.22) ** 2)
core = np.exp(-(r / 0.16) ** 2)
v = np.clip(rays * 1.3 + corona * 0.8 + core, 0, 1)
SUN = [(1, (1, 1, 0.95)), (0.7, (1, 0.93, 0.6)), (0.4, (1, 0.72, 0.25)), (0, (0.85, 0.4, 0.1))]
save(ramp(v, SUN), np.clip(v + np.exp(-(r / 0.5) ** 2) * 0.25, 0, 1) * (1 - ss(0.95, 1, r)), "T_Sun.png")

# ---------------------------------------------------------------- 5 ember curse: brush-painted burning brand circle with runes
S = 192; big = Image.new("L", (S * 3, S * 3), 0); d = ImageDraw.Draw(big); c = S * 1.5; rng = np.random.default_rng(361)
for rad, w in ((0.86, 0.035), (0.68, 0.02)):
    R = rad * c; seg = 48
    for k in range(seg):
        if rng.random() < 0.12: continue                              # brush gaps
        a0 = k / seg * 360; d.arc([c - R, c - R, c + R, c + R], a0, a0 + 360 / seg + 1, fill=int(200 + 55 * rng.random()), width=int(S * 3 * w * rng.uniform(0.7, 1.2)))
for k in range(8):                                                    # rune ticks between the rings
    a = k / 8 * 2 * np.pi + 0.2; p = [(c + np.cos(a + t) * c * (0.77 + 0.04 * s_), c + np.sin(a + t) * c * (0.77 + 0.04 * s_)) for t, s_ in ((-0.08, -1), (0, 1), (0.08, -1))]
    d.line(p, fill=255, width=int(S * 0.05))
for k in range(3):                                                    # inner triangle (pointing down)
    a1 = np.pi / 2 + k * 2 * np.pi / 3; a2 = a1 + 2 * np.pi / 3
    d.line([(c + np.cos(a1) * c * 0.62, c + np.sin(a1) * c * 0.62), (c + np.cos(a2) * c * 0.62, c + np.sin(a2) * c * 0.62)], fill=230, width=int(S * 0.04))
line = np.asarray(big.resize((S, S), Image.LANCZOS), float) / 255
halo = np.asarray(big.filter(ImageFilter.GaussianBlur(10)).resize((S, S), Image.LANCZOS), float) / 255
v = np.clip(line * (0.75 + 0.35 * smooth_noise(S, S, 12, 12, 3, 362)) + halo * 0.4, 0, 1)
save(ramp(v, [(1, (1, 0.95, 0.8)), (0.7, (1, 0.6, 0.25)), (0.4, (0.85, 0.2, 0.1)), (0, (0.35, 0.05, 0.12))]), np.clip(line + halo * 1.3, 0, 1), "T_EmberSigil.png")

# ---------------------------------------------------------------- mana shard (painted blue crystal, square 96)
W, H = 48, 96; x = np.linspace(-1, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
dist = 1 - (np.abs(x) / 0.6 + np.abs(y)); a = ss(0, 0.05, dist)
facet = np.where(x < 0, 0.6, 1.0) * (0.75 + 0.25 * (-y)); edge = np.exp(-(np.clip(dist, 0, None) / 0.07) ** 2)
v = np.clip(facet * 0.7 + edge * 0.5, 0, 1)
im = np.dstack([ramp(v, [(1, (1, 1, 1)), (0.6, (0.55, 0.85, 1)), (0.3, (0.25, 0.45, 1)), (0, (0.2, 0.15, 0.6))]), (np.clip(a * (0.6 + 0.4 * edge), 0, 1))[..., None]])
canvas = np.zeros((96, 96, 4)); canvas[:, 24:72] = im; save(canvas[..., :3], canvas[..., 3], "T_ManaShard.png")

# ---------------------------------------------------------------- 6 lightning sheet 1x4 (384x48 cells): white core -> cyan -> violet halo, with forks
W, H = 384, 48; rows_rgb, rows_a = [], []
BOLT = [(1, (1, 1, 1)), (0.6, (0.75, 0.92, 1)), (0.3, (0.35, 0.6, 1)), (0, (0.35, 0.25, 0.85))]
for k in range(4):
    rng = np.random.default_rng(370 + k); xs = np.linspace(0, 1, W)
    pts = np.cumsum(rng.normal(0, 0.18, 14)); pts -= np.linspace(pts[0], pts[-1], 14); pts = np.clip(pts, -0.5, 0.5)
    yc = np.interp(xs, np.linspace(0, 1, 14), pts) + (smooth_noise(W, 1, 5, 1, 2, 380 + k)[0] - 0.5) * 0.12
    yv = np.linspace(-1, 1, H)[:, None]; dd = np.abs(yv - yc[None, :])
    env = ss(0, 0.04, xs) * ss(1, 0.96, xs)
    core = np.exp(-(dd / 0.06) ** 2) * env
    for f in range(2):                                                # forks
        x0 = rng.uniform(0.25, 0.7); L = rng.uniform(0.08, 0.16); sgn = rng.choice([-1, 1])
        m = (xs > x0) & (xs < x0 + L); fy = np.interp(x0, xs, yc) + sgn * (xs - x0) * 3.5
        core = np.maximum(core, np.exp(-(np.abs(yv - fy[None, :]) / 0.04) ** 2) * m * (1 - (xs - x0) / L) * 0.8)
    v = np.clip(core + np.exp(-(dd / 0.35) ** 2) * 0.35 * env, 0, 1)
    rows_rgb.append(ramp(v, BOLT)); rows_a.append(np.clip(core + np.exp(-(dd / 0.35) ** 2) * 0.45 * env, 0, 1))
save(np.vstack(rows_rgb), np.vstack(rows_a), "T_Bolt_Sheet.png")
# small arcs 2x2 (96 px) for the disarm crackle
C = 96; rgbS = np.zeros((192, 192, 3)); aS = np.zeros((192, 192))
for i in range(4):
    rng = np.random.default_rng(390 + i); big = Image.new("L", (C * 3, C * 3), 0); d = ImageDraw.Draw(big)
    P = [((0.1 + 0.8 * k / 7) * C * 3, (0.5 + rng.normal(0, 0.14)) * C * 3) for k in range(8)]
    d.line(P, fill=255, width=7, joint="curve"); x, y = P[3]; d.line([(x, y), (x + C * 0.35, y + C * rng.choice([-0.5, 0.5]))], fill=200, width=5)
    line = np.asarray(big.resize((C, C), Image.LANCZOS), float) / 255; halo = np.asarray(big.filter(ImageFilter.GaussianBlur(9)).resize((C, C), Image.LANCZOS), float) / 255
    v = np.clip(line + halo * 0.5, 0, 1); ro, co = divmod(i, 2)
    rgbS[ro*C:(ro+1)*C, co*C:(co+1)*C] = ramp(v, BOLT); aS[ro*C:(ro+1)*C, co*C:(co+1)*C] = np.clip(line + halo * 1.5, 0, 1)
save(rgbS, aS, "T_Arc_Sheet.png")

# ---------------------------------------------------------------- 7 mud splat (alpha, colour baked): wet brown body, spiky splash, glossy highlights
S = 192; xx, yy, r, ang = polar(S); pn = polar_noise(S, 18, 3, 401); n = smooth_noise(S, S, 20, 20, 4, 402)
edge = 0.55 + 0.38 * pn ** 2.2                                       # splash spikes
body = ss(edge, edge - 0.08, r)
gloss = np.exp(-((n - 0.62) / 0.05) ** 2) * body * 0.8
shade = 0.35 + 0.4 * n
rgb = np.dstack([shade * 0.55, shade * 0.38, shade * 0.24]) + gloss[..., None] * np.array([0.6, 0.5, 0.38])
blobs = np.zeros_like(r)
for k in range(9):
    a0 = R0.uniform(0, 2 * np.pi); rr = R0.uniform(0.72, 0.9); cx, cy = np.cos(a0) * rr, np.sin(a0) * rr
    blobs = np.maximum(blobs, ss(0.07, 0.04, np.hypot(xx - cx, yy - cy)))
save(rgb, np.clip(body * 0.95 + blobs * 0.9, 0, 1), "T_Mud.png")

# ---------------------------------------------------------------- 8 rainbow: painted prism ring (hue around the ring) + glitter
S = 192; xx, yy, r, ang = polar(S); pn = polar_noise(S, 30, 5, 411)
hue = (ang / (2 * np.pi) + 0.5)
def hsv(h, s, v):
    i = (h * 6).astype(int) % 6; f = h * 6 - np.floor(h * 6); p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    out = np.zeros(h.shape + (3,))
    for k, (a_, b_, c_) in enumerate(((v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q))):
        m = i == k; out[m] = np.dstack([a_, b_, c_])[m] if np.ndim(a_) else 0
    return out
band = np.exp(-((r - 0.8) / 0.07) ** 2) * (0.65 + 0.35 * pn)
inner = ss(0.3, 0.78, r) * (1 - ss(0.78, 0.84, r)) * 0.25
glit = (smooth_noise(S, S, 3, 3, 1, 412) > 0.86) * ss(0.4, 0.9, r) * 0.9
rgb = hsv(hue, np.full_like(r, 0.55), np.ones_like(r)) * (1 - glit[..., None] * 0.5) + glit[..., None] * 0.5
save(np.clip(rgb * 0.75 + band[..., None] * 0.3, 0, 1), np.clip(band + inner + glit, 0, 1) * (1 - ss(0.95, 1, r)), "T_Prism.png")
# heal cross (painted green-white)
big = Image.new("L", (288, 288), 0); d = ImageDraw.Draw(big); c, a_, b_ = 144, 96, 34
d.polygon([(c - b_, c - a_), (c + b_, c - a_), (c + b_, c - b_), (c + a_, c - b_), (c + a_, c + b_), (c + b_, c + b_), (c + b_, c + a_), (c - b_, c + a_), (c - b_, c + b_), (c - a_, c + b_), (c - a_, c - b_), (c - b_, c - b_)], fill=255)
fill = np.asarray(big.resize((96, 96), Image.LANCZOS), float) / 255; halo = np.asarray(big.filter(ImageFilter.GaussianBlur(14)).resize((96, 96), Image.LANCZOS), float) / 255
yy2 = np.linspace(0, 1, 96)[:, None] + np.zeros((1, 96))
save(ramp(np.clip(fill * (1 - yy2 * 0.5) + halo * 0.3, 0, 1), [(1, (1, 1, 0.95)), (0.6, (0.6, 1, 0.6)), (0.3, (0.2, 0.8, 0.35)), (0, (0.1, 0.5, 0.25))]), np.clip(fill + halo * 1.2, 0, 1), "T_Cross.png")

# ---------------------------------------------------------------- 9 poison pool: green toxic pool, violet rim, bubbles
S = 192; xx, yy, r, ang = polar(S); n = smooth_noise(S, S, 18, 18, 4, 421); pn = polar_noise(S, 12, 3, 422)
edge = 0.78 + (pn - 0.5) * 0.18
pool = ss(edge, edge - 0.1, r)
swirl = np.sin(ang * 3 + r * 7 + n * 4) * 0.5 + 0.5
v = np.clip(pool * (0.35 + 0.45 * swirl * n + 0.3 * (1 - r)), 0, 1)
rim = np.exp(-((r - edge + 0.03) / 0.04) ** 2)
bub = np.zeros_like(r)
for k in range(14):
    cx, cy = R0.uniform(-0.55, 0.55, 2); rb = R0.uniform(0.03, 0.07)
    bub = np.maximum(bub, np.exp(-((np.hypot(xx - cx, yy - cy) - rb) / 0.012) ** 2) * (np.hypot(cx, cy) < 0.6))
rgb = ramp(v, [(1, (0.9, 1, 0.6)), (0.6, (0.45, 0.95, 0.3)), (0.3, (0.2, 0.55, 0.2)), (0, (0.12, 0.2, 0.15))]) * (1 - rim[..., None]) + rim[..., None] * np.array([0.7, 0.4, 1])
rgb = rgb * (1 - bub[..., None]) + bub[..., None] * np.array([0.85, 1, 0.7])
save(rgb, np.clip(v * 0.8 + rim * 0.9 + bub, 0, 1) * (1 - ss(0.95, 1, r)), "T_Poison.png")
# bubble
xx, yy, r, ang = polar(48)
b = np.exp(-((r - 0.7) / 0.09) ** 2) + np.exp(-(np.hypot(xx + 0.3, yy + 0.3) / 0.16) ** 2) * 0.9 + (r < 0.7) * 0.2
save(ramp(np.clip(b, 0, 1), [(1, (0.95, 1, 0.8)), (0.5, (0.5, 0.95, 0.35)), (0, (0.25, 0.6, 0.2))]), np.clip(b, 0, 1) * (1 - ss(0.9, 1, r)), "T_Bubble.png")

# ---------------------------------------------------------------- 10 sand vortex: spiral arms of streaky sand + grain speckle
S = 192; xx, yy, r, ang = polar(S); pn = polar_noise(S, 70, 6, 431)
phase = ang * 3 / (2 * np.pi) + r * 1.4 + (pn - 0.5) * 0.25
band = np.abs(((phase % 1) + 1) % 1 - 0.5) * 2
arm = np.exp(-(band / (0.16 + 0.18 * r)) ** 2) * ss(0.08, 0.35, r) * (1 - ss(0.85, 0.98, r))
grain = (smooth_noise(S, S, 2, 2, 1, 432) > 0.7) * ss(0.2, 0.6, r) * (1 - ss(0.88, 0.98, r)) * 0.7
v = np.clip(arm * (0.55 + 0.5 * pn) + grain * 0.6, 0, 1)
SAND = [(1, (1, 0.97, 0.85)), (0.65, (1, 0.82, 0.5)), (0.35, (0.82, 0.58, 0.3)), (0, (0.45, 0.3, 0.16))]
save(ramp(v, SAND), np.clip(arm * (0.5 + 0.5 * pn) + grain, 0, 1), "T_SandVortex.png")
# sand shackle seal (painted)
S = 160; big = Image.new("L", (S * 3, S * 3), 0); d = ImageDraw.Draw(big); c = S * 1.5
for rad, w in ((0.85, 16), (0.6, 10)): d.ellipse([c - rad * c, c - rad * c, c + rad * c, c + rad * c], outline=255, width=w)
for k in range(6):
    a = k / 6 * 2 * np.pi; d.line([(c + np.cos(a) * c * 0.6, c + np.sin(a) * c * 0.6), (c + np.cos(a) * c * 0.85, c + np.sin(a) * c * 0.85)], fill=255, width=14)
    d.ellipse([c + np.cos(a + 0.52) * c * 0.725 - 10, c + np.sin(a + 0.52) * c * 0.725 - 10, c + np.cos(a + 0.52) * c * 0.725 + 10, c + np.sin(a + 0.52) * c * 0.725 + 10], fill=255)
line = np.asarray(big.resize((S, S), Image.LANCZOS), float) / 255; halo = np.asarray(big.filter(ImageFilter.GaussianBlur(9)).resize((S, S), Image.LANCZOS), float) / 255
v = np.clip(line * (0.7 + 0.4 * smooth_noise(S, S, 8, 8, 3, 433)) + halo * 0.3, 0, 1)
pass

# ---------------------------------------------------------------- 12 void: ink splash (alpha dark body) + its glowing rim (additive)
S = 192; xx, yy, r, ang = polar(S); pn = polar_noise(S, 16, 3, 441); n = smooth_noise(S, S, 14, 14, 3, 442)
edge = 0.45 + 0.45 * pn ** 2.5
body = ss(edge, edge - 0.06, r)
save(np.dstack([0.08 + 0.1 * n, 0.03 + 0.05 * n, 0.14 + 0.15 * n]), body * 0.92, "T_VoidInk.png")
rim = np.exp(-((r - edge + 0.02) / 0.035) ** 2) * ss(0.05, 0.3, r)
save(ramp(np.clip(rim * (0.7 + 0.4 * n), 0, 1), [(1, (1, 0.9, 1)), (0.6, (0.8, 0.5, 1)), (0.3, (0.5, 0.25, 0.95)), (0, (0.25, 0.1, 0.5))]), np.clip(rim * 1.2 + np.exp(-((r - edge) / 0.12) ** 2) * 0.25 * (r < edge + 0.1), 0, 1), "T_VoidRim.png")
# wisp stroke (violet, head right)
W, H = 192, 48; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
nW = smooth_noise(W, H, 30, 6, 3, 443); wy = y + (nW - 0.5) * 0.5 * (1 - x)
taper = np.sin(np.pi * x) ** 0.7 * x ** 0.4; d_ = np.abs(wy) / (0.6 * taper + 1e-3)
body = np.clip(1 - d_, 0, 1) ** 0.8 * (0.75 + 0.25 * smooth_noise(W, H, 50, 4, 3, 444))
core = np.exp(-(d_ / 0.25) ** 2) * taper
save(ramp(np.clip(core + body * 0.4, 0, 1), [(1, (1, 0.9, 1)), (0.5, (0.7, 0.4, 1)), (0, (0.25, 0.08, 0.45))]), np.clip(body, 0, 1), "T_Wisp.png")

# ---------------------------------------------------------------- 13 crystal cluster (painted light-gold/ice crystals, square 192)
S = 192; img = np.zeros((S, S, 3)); al = np.zeros((S, S)); Y, X = np.mgrid[0:S, 0:S] / S
shards = [(0.5, 0.06, 0.13, 0.0), (0.33, 0.25, 0.1, -0.35), (0.67, 0.22, 0.1, 0.32), (0.2, 0.5, 0.08, -0.6), (0.8, 0.5, 0.08, 0.6), (0.43, 0.38, 0.07, -0.12), (0.58, 0.4, 0.07, 0.15)]
for (tx, ty, w, lean) in sorted(shards, key=lambda s: -s[1]):
    bx = 0.5 + lean * 0.25; by = 0.98                                  # all grow from the bottom centre
    vx, vy = tx - bx, ty - by; L = np.hypot(vx, vy); ux, uy = vx / L, vy / L
    px, py = X - bx, Y - by; along = (px * ux + py * uy) / L; across = (px * -uy + py * ux) / w
    halfw = np.clip(1 - along, 0, 1) ** 0.9 * np.clip(along / 0.15, 0, 1) ** 0.4
    m = (along > 0) & (along < 1) & (np.abs(across) < halfw)
    facet = np.where(across < 0, 0.62, 1.0) * (0.55 + 0.45 * along)
    edge = np.exp(-((np.abs(across) - halfw) / 0.08) ** 2) + np.exp(-(across / 0.05) ** 2) * 0.5
    v = np.clip(facet * 0.75 + edge * 0.4, 0, 1)
    col = ramp(v, [(1, (1, 1, 1)), (0.7, (0.85, 0.95, 1)), (0.45, (0.55, 0.8, 1)), (0, (0.35, 0.45, 0.8))])
    img[m] = col[m]; al[m] = np.clip(0.75 + 0.25 * edge[m], 0, 1)
gl = np.asarray(Image.fromarray((al * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6)), float) / 255
save(img + (1 - al[..., None]) * np.array([0.7, 0.9, 1]), np.clip(al + gl * 0.35 * (1 - al), 0, 1), "T_Crystal.png")

# ---------------------------------------------------------------- 14 earth spikes (dark rock, violet glowing seams; alpha-blended) + glow-only copy
S = 192; img = np.zeros((S, S, 3)); al = np.zeros((S, S)); seam = np.zeros((S, S))
nR = smooth_noise(S, S, 10, 10, 3, 451)
for (tx, ty, w, lean) in sorted([(0.5, 0.08, 0.16, 0.0), (0.28, 0.35, 0.12, -0.4), (0.74, 0.3, 0.12, 0.38), (0.15, 0.62, 0.09, -0.7), (0.86, 0.6, 0.09, 0.7)], key=lambda s: -s[1]):
    bx = 0.5 + lean * 0.3; by = 1.0
    vx, vy = tx - bx, ty - by; L = np.hypot(vx, vy); ux, uy = vx / L, vy / L
    px, py = X - bx, Y - by; along = (px * ux + py * uy) / L; across = (px * -uy + py * ux) / w
    halfw = np.clip(1 - along, 0, 1) ** 0.75 * (0.85 + 0.3 * (nR - 0.5))
    m = (along > 0) & (along < 1) & (np.abs(across) < halfw)
    lit = np.where(across < 0, 0.45, 0.8) * (0.7 + 0.5 * nR)
    img[m] = (np.dstack([lit * 0.28, lit * 0.22, lit * 0.3]))[m]; al[m] = 1
    sm = np.exp(-(np.abs(np.sin(along * 9 + across * 2 + nR * 6)) / 0.12) ** 2) * (np.abs(across) < halfw * 0.8) * (along > 0.05) * (along < 0.9)
    seam = np.maximum(seam, sm * m)
img = img * (1 - seam[..., None]) + seam[..., None] * np.array([0.85, 0.5, 1.0])
save(img, al, "T_Spikes.png")
gl = np.asarray(Image.fromarray((seam * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)), float) / 255
edgeA = np.asarray(Image.fromarray((al * 255).astype(np.uint8)).filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(2)), float) / 255
v = np.clip(seam + gl * 0.6 + edgeA * 0.5, 0, 1)
save(ramp(v, [(1, (1, 0.9, 1)), (0.5, (0.75, 0.45, 1)), (0, (0.35, 0.15, 0.6))]), v, "T_SpikesGlow.png")

# ---------------------------------------------------------------- 15 eclipse: painted corona with flares (additive) + dark disc (alpha)
S = 192; xx, yy, r, ang = polar(S); pn = polar_noise(S, 20, 4, 461); rng = np.random.default_rng(462)
cor = np.exp(-((r - 0.42) / (0.035 + 0.05 * pn)) ** 2) + ss(0.42, 0.9, r) * (1 - ss(0.42, 0.44, r)) * 0
fl = np.zeros_like(r)
for k in range(10):
    a0 = rng.uniform(0, 2 * np.pi); L = rng.uniform(0.6, 0.92); dd = np.abs(np.angle(np.exp(1j * (ang - a0))))
    fl = np.maximum(fl, np.exp(-(dd / (0.05 * (1.1 - r))) ** 2) * ss(L, 0.42, r) * (r > 0.4))
v = np.clip(cor + fl * 0.7 + np.exp(-((r - 0.42) / 0.18) ** 2) * 0.3, 0, 1)
save(ramp(v, [(1, (1, 0.97, 0.9)), (0.7, (1, 0.8, 0.6)), (0.4, (0.85, 0.55, 1)), (0, (0.35, 0.2, 0.6))]), np.clip(v, 0, 1) * (1 - ss(0.95, 1, r)), "T_Corona.png")
xx, yy, r, ang = polar(96)
save(np.dstack([0.03 + 0 * r, 0.02 + 0 * r, 0.07 + 0.05 * (1 - r)]), 1 - ss(0.8, 0.86, r), "T_Disc.png")

# ---------------------------------------------------------------- glyphs (128 px, painted fill + glow, grey-hot: tinted)
def glyph(draw_fn, S=128, k=3, blur=6):
    big = Image.new("L", (S * k, S * k), 0); d = ImageDraw.Draw(big); draw_fn(d, S * k)
    line = np.asarray(big.resize((S, S), Image.LANCZOS), float) / 255
    halo = np.asarray(big.filter(ImageFilter.GaussianBlur(blur * k)).resize((S, S), Image.LANCZOS), float) / 255
    yy3 = np.linspace(0, 1, S)[:, None] + np.zeros((1, S))
    return np.dstack([np.clip(0.6 + 0.4 * line * (1.1 - 0.5 * yy3), 0, 1)] * 3), np.clip(line + halo * 1.5, 0, 1)
def cross(d, S, w=0.11):
    c = S / 2; a = S * 0.3; b = S * w
    d.polygon([(c - b, c - a), (c + b, c - a), (c + b, c - b), (c + a, c - b), (c + a, c + b), (c + b, c + b), (c + b, c + a), (c - b, c + a), (c - b, c + b), (c - a, c + b), (c - a, c - b), (c - b, c - b)], fill=255)
def seal(d, S):
    cross(d, S); d.ellipse([S * 0.08, S * 0.08, S * 0.92, S * 0.92], outline=255, width=int(S * 0.06))
    d.line([(S * 0.18, S * 0.82), (S * 0.82, S * 0.18)], fill=0, width=int(S * 0.16)); d.line([(S * 0.18, S * 0.82), (S * 0.82, S * 0.18)], fill=255, width=int(S * 0.07))
save(*glyph(seal), "T_Seal.png")
def moon(d, S): d.ellipse([S * 0.16, S * 0.16, S * 0.84, S * 0.84], fill=255); d.ellipse([S * 0.34, S * 0.06, S * 0.96, S * 0.68], fill=0)
save(*glyph(moon, blur=5), "T_Moon.png")
def zz(d, S): d.line([(S * 0.27, S * 0.27), (S * 0.73, S * 0.27), (S * 0.27, S * 0.73), (S * 0.73, S * 0.73)], fill=255, width=int(S * 0.1), joint="curve")
save(*glyph(zz, blur=4), "T_Z.png")
def mana(d, S):
    c = S / 2; d.polygon([(c, S * 0.08), (S * 0.8, c), (c, S * 0.92), (S * 0.2, c)], fill=255)
    d.line([(S * 0.42, S * 0.16), (S * 0.57, S * 0.44), (S * 0.45, S * 0.6), (S * 0.6, S * 0.86)], fill=0, width=int(S * 0.06))
save(*glyph(mana), "T_ManaCrack.png")
def arrow(d, S):
    for y0 in (0.16, 0.44): d.polygon([(S * 0.2, S * y0), (S * 0.5, S * (y0 + 0.24)), (S * 0.8, S * y0), (S * 0.8, S * (y0 + 0.12)), (S * 0.5, S * (y0 + 0.36)), (S * 0.2, S * (y0 + 0.12))], fill=255)
save(*glyph(arrow), "T_Down.png")
print("ok")
