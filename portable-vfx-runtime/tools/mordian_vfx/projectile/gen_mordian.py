# Mordian (모르디안의 원령) projectile textures — ink body + violet glowing rims. usage: python gen_mordian.py <outdir> [curse|rain|all]
import sys, os, math, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = (sys.argv[1] if len(sys.argv) > 1 else ".") + "/"; os.makedirs(OUT, exist_ok=True)
WHICH = sys.argv[2] if len(sys.argv) > 2 else "all"

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
def hx(h): return tuple(int(h[i:i+2], 16) / 255 for i in (1, 3, 5))
# violet ramp: 1 = white lilac, .8 glow lilac, .55 main violet, .3 deep violet, .12 shadow violet, 0 abyss ink
RAMP = [(1.00, hx("#f6eeff")), (0.80, hx("#d696ff")), (0.55, hx("#8a3cff")), (0.30, hx("#4f1f9a")),
        (0.12, hx("#2a1640")), (0.00, hx("#09060e"))]
def ramp(h, stops=RAMP):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
INK = np.array(hx("#09060e")); SHADOW = np.array(hx("#2a1640"))

def mask_from_draw(W, H, k, fn, blur=0):
    im = Image.new("L", (W * k, H * k), 0); d = ImageDraw.Draw(im); fn(d, k)
    if blur: im = im.filter(ImageFilter.GaussianBlur(blur * k))
    return np.asarray(im.resize((W, H), Image.LANCZOS), float) / 255

# ---------------------------------------------------------------- shared
def gen_common():
    S = 128; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy) + 1e-6
    g = np.exp(-(r / 0.35) ** 2) * 0.85 + np.exp(-(r / 0.75) ** 2) * 0.25; g *= 1 - ss(0.9, 1.0, r)
    save(np.ones((S, S, 3)), g, "T_Glow.png")
    S = 64; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy) + 1e-6
    dd = np.exp(-(r / 0.3) ** 2) + (1 - ss(0.08, 0.16, r))
    save(np.ones((S, S, 3)), np.clip(dd, 0, 1) * (1 - ss(0.85, 1, r)), "T_Dot.png")
    # 4-point star, violet → white core
    S = 128; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy) + 1e-6; ax, ay = np.abs(xx), np.abs(yy)
    spike = np.exp(-ay / (0.025 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (0.025 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
    glow = np.exp(-(r / 0.15) ** 2) * 0.9 + np.exp(-(r / 0.45) ** 2) * 0.3
    heat = np.clip(spike * 1.1 + np.exp(-(r / 0.12) ** 2), 0, 1)
    save(ramp(0.45 + 0.55 * heat), np.clip(spike + glow, 0, 1) * (1 - ss(0.9, 1, r)), "T_Star.png")
    # streak (head at right)
    W, H = 256, 32; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
    taper = x ** 1.5 * (1 - ss(0.9, 1.0, x))
    core = np.exp(-(dy / (0.12 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.55 * taper + 1e-3)) ** 2) * 0.45 * taper
    save(ramp(0.4 + 0.6 * np.clip(core + halo * 0.4, 0, 1)), np.clip(core + halo, 0, 1), "T_Streak.png")
    # sharp ring with a faint inner wash
    S = 256; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy)
    nR = smooth_noise(S, S, 18, 18, 3, 51)
    ring = np.exp(-((r - 0.86) / 0.018) ** 2); ring2 = np.exp(-((r - 0.80) / 0.008) ** 2) * 0.6
    inner = np.exp(-((r - 0.74) / 0.12) ** 2) * (r < 0.86) * (0.4 + 0.6 * nR)
    a = np.clip(ring + ring2 + inner * 0.45, 0, 1) * (1 - ss(0.95, 1, r))
    save(ramp(np.clip(ring * 1.0 + ring2 + inner * 0.35, 0, 1) * 0.75 + 0.25), a, "T_Ring.png")
    # ink puff flipbook 4x4 @128: jagged dark ink bloom, violet under-rim, dissolving
    C, G = 128, 4; yy, xx = np.mgrid[-1:1:C*1j, -1:1:C*1j]
    nA = smooth_noise(C, C, 34, 34, 4, 11); nB = smooth_noise(C, C, 14, 14, 3, 12); nC = smooth_noise(C, C, 7, 40, 3, 13)
    ang = np.arctan2(yy, xx)
    def cell(t):
        R = 0.52 + 0.34 * t ** 0.55
        spikes = 0.10 * np.maximum(0, np.sin(7 * ang + 2.0 * nA * 6)) ** 3 * (1 - t * 0.5)
        wx = xx + (nA - 0.5) * 0.5; wy = yy + (nB - 0.5) * 0.45
        body = 1 - np.hypot(wx, wy) / (R + spikes)
        body = body + (nC - 0.5) * 0.18 - t ** 1.4 * 0.9 * (0.4 + nB)
        a = ss(0.0, 0.10 + 0.25 * t, body) * (0.95 - 0.35 * t)
        rim = np.exp(-(np.clip(body, 0, None) / (0.09 + 0.05 * t)) ** 2) * ss(-0.02, 0.02, body)   # edge band
        lit = np.clip(rim * (1 - t) ** 1.0 * (0.85 + 0.6 * ss(-0.3, 0.6, -wy + 0.2)), 0, 1)       # violet rim, stronger below
        rgb = INK * (1 - 0.6 * ss(0, 0.5, body))[..., None] + SHADOW * 0.6 * nA[..., None]
        rgb = rgb * (1 - lit[..., None]) + np.array(hx("#a65cff")) * lit[..., None]
        return rgb, a * (1 - ss(0.88, 1.0, np.hypot(xx, yy)))
    rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
    for i in range(G*G):
        rr, cc = divmod(i, G); rgb, a = cell(i / (G*G - 1))
        rgbS[rr*C:(rr+1)*C, cc*C:(cc+1)*C] = rgb; aS[rr*C:(rr+1)*C, cc*C:(cc+1)*C] = a
    save(rgbS, aS, "T_InkPuff_Sheet.png")
    # ink tendril / splash spike (head at right): dark body, violet edge
    W, H = 256, 64; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
    nT = smooth_noise(W, H, 50, 10, 3, 31)
    prof = (np.sin(np.pi * np.clip(x, 0, 1) * 0.5 + 0.0) ** 0.5) * (1 - ss(0.82, 1.0, x)) * 0.75 + 0.18 * np.exp(-((x - 0.93) / 0.05) ** 2)
    prof = prof * (0.35 + 0.65 * x)
    dist = prof - (dy + (nT - 0.5) * 0.22)
    a = ss(-0.02, 0.05, dist) * ss(0.0, 0.25, x)
    edge = np.exp(-(np.clip(dist, 0, None) / 0.07) ** 2)
    rgb = INK * (1 - edge[..., None]) + np.array(hx("#a65cff")) * (edge * (0.4 + 0.6 * x))[..., None]
    save(rgb, a, "T_Tendril.png")
    # ink shard (head at right)
    W, H = 128, 32; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
    nS = smooth_noise(W, H, 20, 20, 3, 41)
    profS = np.sin(np.pi * x) ** 0.7 * (0.4 + 0.6 * x)
    dist = 0.8 * profS - (dy + (nS - 0.5) * 0.25)
    a = ss(-0.04, 0.03, dist); edge = np.exp(-(np.clip(dist, 0, None) / 0.08) ** 2)
    save(INK * (1 - edge[..., None]) + np.array(hx("#c07cff")) * edge[..., None], a, "T_Shard.png")
    # ink orb: spiky black core, violet glowing rim + soft violet halo (alpha material; HDR tint blooms the rim)
    S = 128; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy) + 1e-6; ang = np.arctan2(yy, xx)
    nO = smooth_noise(S, S, 20, 20, 3, 61)
    R = 0.42 + 0.16 * np.maximum(0, np.sin(9 * ang + 3 * nO)) ** 4 + 0.05 * (nO - 0.5)
    body = 1 - r / R
    rim = np.exp(-(np.abs(body) / 0.09) ** 2)
    halo = np.exp(-(np.clip(-body, 0, None) / 0.35) ** 2) * 0.55
    a = np.clip(ss(-0.03, 0.03, body) + halo, 0, 1) * (1 - ss(0.9, 1, r))
    heat = np.clip(rim * 0.9 + halo * 0.6, 0, 1) * (body < 0.15) + rim * 0.0
    rgb = ramp(0.25 + 0.7 * heat)
    inside = ss(0.02, 0.2, body)[..., None]
    rgb = rgb * (1 - inside) + (INK + SHADOW * 0.5 * nO[..., None]) * inside
    save(rgb, a, "T_InkOrb.png")
    # dust puff (white, soft, noisy) — violet comes from the material tint
    S = 128; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy)
    nD = smooth_noise(S, S, 18, 18, 4, 71)
    a = ss(0.0, 0.7, 1 - r / (0.75 + 0.25 * nD)) * (0.45 + 0.55 * nD)
    save(np.ones((S, S, 3)) * (0.75 + 0.25 * nD[..., None]), a * (1 - ss(0.9, 1, r)), "T_Dust.png")
    # ink trail band (u along trail, v across): dark core, violet edges
    W, H = 64, 64; y = np.linspace(-1, 1, H)[:, None] * np.ones((1, W)); nI = smooth_noise(W, H, 30, 6, 3, 81)
    dy = np.abs(y + (nI - 0.5) * 0.25)
    a = ss(1.0, 0.55, dy)
    edge = np.exp(-((dy - 0.62) / 0.14) ** 2)
    save(INK * (1 - edge[..., None]) + np.array(hx("#9a50ff")) * edge[..., None], a, "T_InkTrail.png")
    # wipe stroke 2x8 (256x32 cells) for spin arcs
    CW, CH = 256, 32
    u = np.linspace(0, 1, CW)[None, :]; v = np.linspace(0, 1, CH)[:, None]
    N1 = smooth_noise(CW, CH, 40, 40, 3, 21); N2 = smooth_noise(CW, CH, 12, 12, 3, 22); strands = smooth_noise(CW, CH, 80, 3, 3, 23)
    def scell(t):
        head = min(1.0, t / 0.55) ** 0.75; tail = max(0.0, (t - 0.18) / 0.82) ** 1.35
        rel = np.clip((u - tail) / max(head - tail, 1e-3), 0, 1)
        inside = ss(tail, tail + 0.05, u) * (1 - ss(head - 0.02, head + 0.005, u))
        width = np.sin(np.pi * rel) ** 0.55
        d = np.abs(v - 0.5 - (N1 - 0.5) * 0.1) / (0.36 * width + 1e-3)
        core = np.exp(-(d / 0.18) ** 2); body = np.clip(1 - d, 0, 1) ** 1.5 * (0.55 + 0.45 * strands)
        heat = np.clip(core + body * 0.7, 0, 1) * (0.45 + 0.55 * rel ** 0.6)
        if t > 0.45: heat = heat * (1 - ss(0.0, 1.0, (t - 0.45) * 2.2 * (0.6 + N2)))
        a = np.clip(ss(0.02, 0.25, heat) + np.exp(-(d / 0.9) ** 2) * 0.25 * width, 0, 1) * inside
        return ramp(heat * 0.9 + 0.1), a
    rgbS = np.zeros((CH*8, CW*2, 3)); aS = np.zeros((CH*8, CW*2))
    for i in range(16):
        rr, cc = divmod(i, 2); rgb, a = scell(i / 15)
        rgbS[rr*CH:(rr+1)*CH, cc*CW:(cc+1)*CW] = rgb; aS[rr*CH:(rr+1)*CH, cc*CW:(cc+1)*CW] = a
    save(rgbS, aS, "T_Stroke_Sheet.png")

# ---------------------------------------------------------------- curse bolt
def gen_curse():
    # barbed arrowhead pointing right (+U): black body, violet rim (alpha material, HDR tint blooms the rim)
    W, H, k = 256, 128, 4
    def draw(d, k):
        cy = H * k / 2; L = W * k
        pts = [(L * 0.98, cy),                                  # tip
               (L * 0.62, cy - H * k * 0.30), (L * 0.66, cy - H * k * 0.14),   # upper barb
               (L * 0.44, cy - H * k * 0.40), (L * 0.48, cy - H * k * 0.12),   # second barb
               (L * 0.22, cy - H * k * 0.30), (L * 0.28, cy - H * k * 0.07),
               (L * 0.02, cy - H * k * 0.03),
               (L * 0.02, cy + H * k * 0.03),
               (L * 0.28, cy + H * k * 0.07), (L * 0.22, cy + H * k * 0.30),
               (L * 0.48, cy + H * k * 0.12), (L * 0.44, cy + H * k * 0.40),
               (L * 0.66, cy + H * k * 0.14), (L * 0.62, cy + H * k * 0.30)]
        d.polygon(pts, fill=255)
    m = mask_from_draw(W, H, k, draw)
    inner = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3.2)), float) / 255
    rim = np.clip(m - ss(0.55, 0.95, inner), 0, 1)               # bright band along the silhouette
    halo = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(9)), float) / 255
    x = np.linspace(0, 1, W)[None, :] * np.ones((H, 1))
    y = np.linspace(-1, 1, H)[:, None] * np.ones((1, W))
    spine = np.exp(-(np.abs(y) / 0.035) ** 2) * ss(0.1, 0.9, x) * m * 0.7                     # glowing centre vein
    heat = np.clip(rim * (0.55 + 0.45 * x) + spine, 0, 1)
    a = np.clip(m + halo * 0.55 * (1 - m), 0, 1)
    rgb = ramp(0.3 + 0.7 * heat) * np.clip(heat * 1.4 + (1 - m) * 0.9, 0, 1)[..., None] + INK * (1 - np.clip(heat * 1.4, 0, 1))[..., None] * m[..., None]
    rgb = np.where((m < 0.5)[..., None], np.array(hx("#8a3cff")) * np.ones_like(rgb), rgb)   # halo colour
    save(rgb, a, "T_ArrowHead.png")
    # three parallel diagonal claw scratches — dark ink burn + glowing violet cut lines (same texture: alpha decal + additive glow)
    S, k = 256, 4
    burn = np.zeros((S, S)); line = np.zeros((S, S))
    rng = np.random.default_rng(5)
    for i, off in enumerate((-0.27, 0.0, 0.27)):
        def drawL(d, k, off=off, w=1.0, i=i):
            n = 40; pts_l = []; pts_r = []
            for j in range(n + 1):
                s = j / n
                # diagonal line (bottom-left → top-right), the three lines offset perpendicular to it
                px, py = 0.5 + (s - 0.5) * 0.551 + off * 0.707 * 0.9, 0.5 - (s - 0.5) * 0.551 + off * 0.707 * 0.9
                px += 0.012 * math.sin(s * 9 + i * 2)
                hw = (0.004 + 0.030 * math.sin(math.pi * s) ** 0.8 * (0.85 + 0.15 * math.sin(s * 23 + i))) * w
                nx, ny = 0.707, 0.707
                pts_l.append(((px + nx * hw) * S * k, (py + ny * hw) * S * k)); pts_r.append(((px - nx * hw) * S * k, (py - ny * hw) * S * k))
            d.polygon(pts_l + pts_r[::-1], fill=255)
        line = np.maximum(line, mask_from_draw(S, S, k, lambda d, k: drawL(d, k, w=0.55)))
        burn = np.maximum(burn, mask_from_draw(S, S, k, lambda d, k: drawL(d, k, w=2.6), blur=2.5))
    nB = smooth_noise(S, S, 10, 10, 3, 91)
    yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy)
    burn = np.clip(burn * (0.65 + 0.5 * nB), 0, 1)
    glowL = np.asarray(Image.fromarray((line * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)), float) / 255
    heat = np.clip(line * 1.0 + glowL * 0.6, 0, 1)
    a = np.clip(np.maximum(burn * 0.85, heat), 0, 1) * (1 - ss(0.9, 1, r))
    rgb = ramp(0.2 + 0.8 * heat) * np.clip(heat * 1.5, 0, 1)[..., None] + INK * (1 - np.clip(heat * 1.5, 0, 1))[..., None]
    save(rgb, a, "T_Claw.png")
    # rune ring: thin double ring + 12 small triple-slash glyphs between
    S, k = 256, 3
    def drawR(d, k):
        c = S * k / 2
        for rr, w in ((0.92, 1.6), (0.70, 1.2)):
            R = rr * c; d.ellipse([c - R, c - R, c + R, c + R], outline=255, width=int(w * k))
        for g in range(12):
            a0 = g / 12 * 2 * math.pi
            for j in (-1, 0, 1):
                a1 = a0 + j * 0.06
                r0, r1 = 0.75 * c, 0.87 * c
                tilt = 0.10
                d.line([(c + math.cos(a1 - tilt) * r0, c + math.sin(a1 - tilt) * r0), (c + math.cos(a1 + tilt) * r1, c + math.sin(a1 + tilt) * r1)], fill=255, width=int(1.4 * k))
            # dot between glyphs
            a2 = a0 + math.pi / 12; rd = 0.81 * c; s = 2.2 * k
            d.ellipse([c + math.cos(a2) * rd - s, c + math.sin(a2) * rd - s, c + math.cos(a2) * rd + s, c + math.sin(a2) * rd + s], fill=255)
    m = mask_from_draw(S, S, k, drawR)
    gl = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)), float) / 255
    save(ramp(0.5 + 0.5 * np.clip(m + gl * 0.3, 0, 1)), np.clip(m + gl * 0.7, 0, 1), "T_RuneRing.png")

# ---------------------------------------------------------------- dark rain
def gen_rain():
    # comet: ink teardrop, head at right (u=1), violet rim brightest at the head, ragged tail
    W, H = 256, 64; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
    nT = smooth_noise(W, H, 40, 8, 4, 101); nT2 = smooth_noise(W, H, 90, 5, 3, 102)
    headc = 0.86
    prof = np.where(x < headc, 0.62 * (x / headc) ** 0.9, 0.62 * np.sqrt(np.clip(1 - ((x - headc) / (1 - headc)) ** 2, 0, 1)))
    prof = prof + (nT2 - 0.5) * 0.18 * (1 - x)
    dist = prof - (dy + (nT - 0.5) * 0.2 * (1 - x))
    a = ss(-0.03, 0.05, dist) * ss(0.0, 0.3, x)
    edge = np.exp(-(np.clip(dist, 0, None) / 0.07) ** 2)
    headglow = np.exp(-(((x - headc) / 0.14) ** 2 + (y / 0.5) ** 2))
    strands = np.clip(smooth_noise(W, H, 70, 3, 3, 103) - 0.5, 0, 1) * 1.5 * (1 - x) * (dist > 0)
    heat = np.clip(edge * (0.35 + 0.65 * x) + strands * 0.6, 0, 1)
    rgb = ramp(0.3 + 0.65 * heat) * heat[..., None] + INK * (1 - heat[..., None])
    halo = np.exp(-(np.clip(-dist, 0, None) / 0.12) ** 2) * 0.4 * x * (dist < 0)
    rgb = np.where((dist < 0)[..., None], np.array(hx("#8a3cff")) * np.ones_like(rgb), rgb)
    save(rgb, np.clip(a + halo, 0, 1), "T_Comet.png")
    # crack decal: central ink splat + branching cracks glowing violet
    S, k = 256, 4
    rng = np.random.default_rng(7)
    def drawC(d, k, w=1.0):
        c = S * k / 2
        def branch(x0, y0, ang, length, width, depth):
            x, y = x0, y0; steps = 7
            for i in range(steps):
                ang += rng.uniform(-0.35, 0.35)
                nx_, ny_ = x + math.cos(ang) * length / steps, y + math.sin(ang) * length / steps
                d.line([(x, y), (nx_, ny_)], fill=255, width=max(1, int(width * w * (1 - i / steps * 0.6))))
                if depth > 0 and rng.random() < 0.28:
                    branch(nx_, ny_, ang + rng.choice([-1, 1]) * rng.uniform(0.5, 0.9), length * 0.45, width * 0.6, depth - 1)
                x, y = nx_, ny_
        n = 11
        for i in range(n):
            a0 = i / n * 2 * math.pi + rng.uniform(-0.2, 0.2)
            r0 = 0.22 * c
            branch(c + math.cos(a0) * r0, c + math.sin(a0) * r0, a0, rng.uniform(0.55, 0.74) * c, 3.4 * k, 2)
    st = rng.bit_generator.state
    line = mask_from_draw(S, S, k, lambda d, k: drawC(d, k, 1.0))
    rng.bit_generator.state = st
    burn = mask_from_draw(S, S, k, lambda d, k: drawC(d, k, 3.2), blur=3)
    yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy); ang = np.arctan2(yy, xx)
    nS = smooth_noise(S, S, 14, 14, 4, 111)
    splatR = 0.30 + 0.10 * np.maximum(0, np.sin(9 * ang + 4 * nS)) ** 3 + 0.06 * (nS - 0.5)
    splat = ss(0.0, 0.06, splatR - r)
    burn = np.clip(np.maximum(burn * (0.6 + 0.5 * nS), splat * 0.92), 0, 1)
    glowL = np.asarray(Image.fromarray((line * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)), float) / 255
    centre = np.exp(-(r / 0.16) ** 2) * 0.0
    ringglow = np.exp(-((r - splatR) / 0.03) ** 2) * 0.55
    heat = np.clip(line * (1 - 0.5 * ss(0.3, 0.95, r)) + glowL * 0.5 + ringglow, 0, 1) * (1 - ss(0.85, 0.98, r))
    a = np.clip(np.maximum(burn * 0.88, heat), 0, 1) * (1 - ss(0.9, 1, r))
    rgb = ramp(0.2 + 0.8 * heat) * np.clip(heat * 1.5, 0, 1)[..., None] + (INK + SHADOW * 0.4 * nS[..., None]) * (1 - np.clip(heat * 1.5, 0, 1))[..., None]
    save(rgb, a, "T_Crack.png")

gen_common()
if WHICH in ("curse", "all"): gen_curse()
if WHICH in ("rain", "all"): gen_rain()
print("ok", WHICH)
