# 엘리시아 레이드 공용 텍스처 — 성(聖) 금빛 / 지옥 진홍 두 계열. usage: python gen_elysia.py <outdir>
import sys, math, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = (sys.argv[1] if len(sys.argv) > 1 else ".") + "/"

def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def save(rgb, a, name):
    if np.ndim(rgb) == 1: rgb = np.ones(a.shape + (3,)) * np.array(rgb)
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]]) * 255).astype(np.uint8), "RGBA").save(OUT + name + ".png")
HOLY = [(1.00, (1.00, 1.00, 0.97)), (0.75, (1.00, 0.95, 0.72)), (0.50, (1.00, 0.80, 0.38)),
        (0.25, (0.92, 0.56, 0.16)), (0.00, (0.55, 0.28, 0.06))]
HELL = [(1.00, (1.00, 0.96, 0.82)), (0.78, (1.00, 0.72, 0.30)), (0.55, (1.00, 0.36, 0.10)),
        (0.30, (0.78, 0.08, 0.06)), (0.00, (0.26, 0.01, 0.04))]
def ramp(h, stops):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
def grid(n): return np.mgrid[-1:1:n*1j, -1:1:n*1j]

# ---------- 선 프로파일 (메시 선 작업용: V 방향 단면) ----------
v = np.linspace(-1, 1, 32)[:, None] * np.ones((1, 64))
save([1, 1, 1], np.exp(-(v / 0.22) ** 2) + np.exp(-(v / 0.6) ** 2) * 0.35, "T_Line")

# ---------- 룬 띠 512x128 ----------
def rune_strip(seed):
    S = 3; W, H = 512 * S, 128 * S
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im); rng = np.random.default_rng(seed)
    d.line([(0, 18 * S), (W, 18 * S)], fill=200, width=2 * S); d.line([(0, H - 18 * S), (W, H - 18 * S)], fill=200, width=2 * S)
    n = 8; cw = W / n
    for i in range(n):
        cx = cw * (i + 0.5); cy = H / 2; s = 26 * S
        k = [0, 3, 1, 4, 2, 5, 3, 0][i]
        pts = []
        if k == 0: d.ellipse([cx - s * .6, cy - s * .6, cx + s * .6, cy + s * .6], outline=255, width=3 * S); d.line([(cx, cy - s), (cx, cy + s)], fill=255, width=3 * S)
        elif k == 1: d.polygon([(cx, cy - s), (cx + s * .8, cy + s * .6), (cx - s * .8, cy + s * .6)], outline=255, width=3 * S); d.line([(cx - s, cy), (cx + s, cy)], fill=255, width=2 * S)
        elif k == 2: d.line([(cx - s * .7, cy - s), (cx + s * .7, cy + s)], fill=255, width=3 * S); d.line([(cx + s * .7, cy - s), (cx - s * .7, cy + s)], fill=255, width=3 * S); d.ellipse([cx - 5 * S, cy - 5 * S, cx + 5 * S, cy + 5 * S], fill=255)
        elif k == 3: d.arc([cx - s, cy - s, cx + s, cy + s], 200, 520, fill=255, width=3 * S); d.line([(cx, cy - s * .3), (cx, cy + s)], fill=255, width=3 * S)
        elif k == 4: d.rectangle([cx - s * .5, cy - s * .8, cx + s * .5, cy + s * .8], outline=255, width=3 * S); d.line([(cx - s * .5, cy), (cx + s * .5, cy)], fill=255, width=3 * S)
        else: d.line([(cx, cy - s), (cx, cy + s)], fill=255, width=3 * S); d.line([(cx - s * .6, cy - s * .4), (cx, cy - s), (cx + s * .6, cy - s * .4)], fill=255, width=3 * S)
        d.ellipse([cx + cw * .42 - 3 * S, cy - 3 * S, cx + cw * .42 + 3 * S, cy + 3 * S], fill=180)
    a = np.asarray(im, float) / 255
    g = np.asarray(im.filter(ImageFilter.GaussianBlur(6 * S)), float) / 255
    A = Image.fromarray((np.clip(a + g * 1.4, 0, 1) * 255).astype(np.uint8)).resize((512, 128), Image.LANCZOS)
    save([1, 1, 1], np.asarray(A, float) / 255, "T_Rune")
rune_strip(4)

# ---------- 중앙 문장: 성 = 천칭 + 햇살 / 지옥 = 장부의 눈 ----------
def sigil(kind):
    S = 3; N = 320 * S; c = N / 2
    im = Image.new("L", (N, N), 0); d = ImageDraw.Draw(im)
    def circ(r, w, f=255): d.ellipse([c - r, c - r, c + r, c + r], outline=f, width=int(w))
    circ(0.96 * c, 3 * S); circ(0.86 * c, 2 * S, 200)
    for i in range(48):  # 눈금
        a = i / 48 * 2 * math.pi; r0 = 0.86 * c; r1 = r0 + (0.07 if i % 4 == 0 else 0.035) * c
        d.line([(c + r0 * math.cos(a), c + r0 * math.sin(a)), (c + r1 * math.cos(a), c + r1 * math.sin(a))], fill=255, width=2 * S)
    if kind == "holy":
        for i in range(12):  # 햇살
            a = i / 12 * 2 * math.pi; r0, r1 = 0.36 * c, 0.78 * c
            w = 0.05 if i % 2 == 0 else 0.03
            p = [(c + r0 * math.cos(a - w), c + r0 * math.sin(a - w)), (c + r1 * math.cos(a), c + r1 * math.sin(a)), (c + r0 * math.cos(a + w), c + r0 * math.sin(a + w))]
            d.polygon(p, outline=255, width=2 * S)
        circ(0.32 * c, 3 * S)
        # 천칭: 기둥, 들보, 접시 둘
        d.line([(c, c - 0.25 * c), (c, c + 0.22 * c)], fill=255, width=4 * S)
        d.line([(c - 0.22 * c, c - 0.12 * c), (c + 0.22 * c, c - 0.12 * c)], fill=255, width=4 * S)
        for sx in (-1, 1):
            x = c + sx * 0.2 * c
            d.line([(x, c - 0.12 * c), (x - 0.07 * c, c + 0.05 * c)], fill=230, width=2 * S); d.line([(x, c - 0.12 * c), (x + 0.07 * c, c + 0.05 * c)], fill=230, width=2 * S)
            d.arc([x - 0.08 * c, c - 0.02 * c, x + 0.08 * c, c + 0.12 * c], 0, 180, fill=255, width=3 * S)
        d.polygon([(c, c - 0.31 * c), (c + 0.03 * c, c - 0.26 * c), (c, c - 0.21 * c), (c - 0.03 * c, c - 0.26 * c)], fill=255)
    else:
        for i in range(6):  # 날개 6장 = 휘어진 갈퀴
            a = i / 6 * 2 * math.pi + math.pi / 6
            pts = [(c + (0.36 + 0.42 * t) * c * math.cos(a + 0.5 * t * t), c + (0.36 + 0.42 * t) * c * math.sin(a + 0.5 * t * t)) for t in np.linspace(0, 1, 16)]
            d.line(pts, fill=255, width=3 * S)
            d.line([pts[8], (pts[8][0] + 0.06 * c * math.cos(a + 1.6), pts[8][1] + 0.06 * c * math.sin(a + 1.6))], fill=230, width=2 * S)
        circ(0.32 * c, 3 * S)
        # 장부의 눈
        d.arc([c - 0.26 * c, c - 0.2 * c, c + 0.26 * c, c + 0.2 * c], 200, 340, fill=255, width=4 * S)
        d.arc([c - 0.26 * c, c - 0.2 * c, c + 0.26 * c, c + 0.2 * c], 20, 160, fill=255, width=4 * S)
        d.ellipse([c - 0.07 * c, c - 0.09 * c, c + 0.07 * c, c + 0.09 * c], fill=255)
        d.line([(c, c - 0.09 * c), (c, c + 0.09 * c)], fill=0, width=3 * S)
    a = np.asarray(im, float) / 255; g = np.asarray(im.filter(ImageFilter.GaussianBlur(7 * S)), float) / 255
    A = Image.fromarray((np.clip(a + g * 1.3, 0, 1) * 255).astype(np.uint8)).resize((320, 320), Image.LANCZOS)
    save([1, 1, 1], np.asarray(A, float) / 255, "T_Sigil" + ("Holy" if kind == "holy" else "Hell"))
sigil("holy"); sigil("hell")

# ---------- 기본 형태 (흰색, 색은 머티리얼 tint) ----------
yy, xx = grid(256); r = np.hypot(xx, yy) + 1e-6
save([1, 1, 1], (np.exp(-((r - 0.82) / 0.03) ** 2) + np.exp(-((r - 0.8) / 0.12) ** 2) * 0.35) * (1 - ss(0.95, 1, r)), "T_Ring")
save([1, 1, 1], (np.exp(-((r - 0.75) / 0.16) ** 2) * (r < 0.78) + np.exp(-((r - 0.78) / 0.05) ** 2)) * 0.9 * (1 - ss(0.95, 1, r)), "T_Wave")
y1, x1 = grid(128); r1 = np.hypot(x1, y1) + 1e-6
save([1, 1, 1], (np.exp(-(r1 / 0.35) ** 2) * 0.85 + np.exp(-(r1 / 0.75) ** 2) * 0.25) * (1 - ss(0.9, 1, r1)), "T_Glow")
ax, ay = np.abs(x1), np.abs(y1)
spike = np.exp(-ay / (0.025 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2 + np.exp(-ax / (0.025 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2
save([1, 1, 1], np.clip(spike + np.exp(-(r1 / 0.18) ** 2) + np.exp(-(r1 / 0.5) ** 2) * 0.25, 0, 1), "T_Star")
y6, x6 = grid(64); r6 = np.hypot(x6, y6)
save([1, 1, 1], np.clip(np.exp(-(r6 / 0.3) ** 2) + (1 - ss(0.1, 0.18, r6)), 0, 1) * (1 - ss(0.85, 1, r6)), "T_Dot")
# 섬광: 여러 갈래 햇살
ang = np.arctan2(y1 * 2, x1 * 2); nf = smooth_noise(128, 128, 8, 8, 2, 3)
rays = (0.5 + 0.5 * np.cos(ang * 10)) ** 6 * 0.8 + (0.5 + 0.5 * np.cos(ang * 17 + 1)) ** 10 * 0.6
save([1, 1, 1], np.clip(np.exp(-(r1 / 0.15) ** 2) + rays * np.exp(-(r1 / 0.7) ** 1.5) * (0.6 + 0.6 * nf), 0, 1) * (1 - ss(0.9, 1, r1)), "T_Flare")

# ---------- 줄기 (가로, 머리 = 오른쪽) / 빛기둥 (세로) ----------
W, H = 256, 32
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
tp = np.sin(np.pi * x) ** 0.8 * (0.35 + 0.65 * x)
save([1, 1, 1], np.clip(np.exp(-(dy / (0.16 * tp + 0.01)) ** 2) + np.exp(-(dy / (0.55 * tp + 1e-3)) ** 2) * 0.35 * tp, 0, 1), "T_Streak")
PW, PH = 128, 512
px_ = np.linspace(-1, 1, PW)[None, :]; py_ = np.linspace(0, 1, PH)[:, None]
st = smooth_noise(PW, PH, 6, 140, 3, 7)
env = ss(0, 0.12, py_) * (1 - ss(0.7, 1.0, py_))
core = np.exp(-(px_ / 0.12) ** 2); body = np.exp(-(px_ / 0.42) ** 2) * (0.55 + 0.6 * st)
heat = np.clip(core + body * 0.6, 0, 1)
save(ramp(heat * 0.95 + 0.05, HOLY), np.clip(core + body * 0.7, 0, 1) * env, "T_PillarHoly")
save(ramp(heat * 0.95 + 0.05, HELL), np.clip(core + body * 0.75, 0, 1) * env, "T_PillarHell")

# ---------- 깃털 128x256 (뿌리 = 아래) ----------
FW, FH = 128, 256
fx = np.linspace(-1, 1, FW)[None, :]; fy = np.linspace(1, 0, FH)[:, None]   # fy 1 = 위(끝)
half = 0.62 * np.sin(np.pi * np.clip(fy * 0.92 + 0.04, 0, 1)) ** 0.7 * (0.5 + 0.5 * (1 - (fy - 0.55) ** 2))
nb = smooth_noise(FW, FH, 3, 40, 2, 8)
barb = 0.5 + 0.5 * np.sin((fy * 60 - np.abs(fx) * 9))
edge = half - np.abs(fx) - (nb - 0.5) * 0.08
a = ss(-0.02, 0.03, edge) * ss(0.0, 0.06, fy)
shaft = np.exp(-(fx / 0.035) ** 2) * (fy > 0.02)
lum = 0.9 + 0.1 * barb
save(np.clip(lum, 0, 1)[..., None] * np.ones((1, 1, 3)), np.clip(a * (0.8 + 0.2 * barb) + shaft * 0.5, 0, 1), "T_Feather")

# ---------- 파편: 어두운 몸 + 밝은 테두리 (알파 블렌드) / 밝은 결정 (가산) ----------
yy, xx = grid(128)
dshape = 0.85 - (np.abs(xx) * 1.9 + np.abs(yy) * 1.05)
a = ss(-0.02, 0.02, dshape); rim = np.exp(-(np.clip(dshape, 0, None) / 0.08) ** 2)
save(np.array([0.07, 0.04, 0.06]) * (1 - rim[..., None]) + np.array([1, 1, 1]) * rim[..., None], a, "T_ShardDark")
save([1, 1, 1], np.clip(a * (0.35 + 0.65 * rim) + np.exp(-(np.clip(-dshape, 0, None) / 0.1) ** 2) * 0.4, 0, 1), "T_ShardLight")

# ---------- 육각 패널 (방벽) 256 ----------
S = 256; yy, xx = np.mgrid[0:S, 0:S] / S * 3.0
def hexd(px, py):
    q = np.stack([px, py * 2 / math.sqrt(3)], -1)
    best = np.full(px.shape, 9.0)
    for cy in range(-1, 5):
        for cx in range(-1, 5):
            ox = cx + (0.5 if cy % 2 else 0); oy = cy * math.sqrt(3) / 2
            dx = np.abs(px - ox); dyy = np.abs(py - oy)
            dist = np.maximum(dx * 0.866 * 2 / math.sqrt(3) * 0 + np.maximum(dyy * 2 / math.sqrt(3) * 0.866, dx * 0.5 + dyy * 0.866 * 0 + dyy * 0.5 * 0) , dx)
            dd = np.maximum(dx, dx * 0.5 + dyy * 0.866)
            best = np.minimum(best, dd)
    return best
hd = hexd(xx, yy)
ln = np.exp(-((hd - 0.5) / 0.02) ** 2) + np.exp(-((hd - 0.5) / 0.08) ** 2) * 0.3
fill = ss(0.5, 0.0, hd) * 0.18
save([1, 1, 1], np.clip(ln + fill, 0, 1), "T_Hex")

# ---------- 지옥 불꽃 4x4 (128px 칸) / 검은 안개 4x4 ----------
C, G = 96, 4
yy, xx = grid(C)
nA = smooth_noise(C, C, 27, 27, 4, 11); nB = smooth_noise(C, C, 12, 12, 3, 12); stq = smooth_noise(C, C, 5, 34, 3, 13)
def flame_cell(t):
    R = 0.55 + 0.33 * t ** 0.6
    wx = xx + (nA - 0.5) * 0.45; wy = yy + (nB - 0.5) * 0.35 + 0.12
    up = np.clip(-wy, 0, None); rw = np.hypot(wx * (1 + 0.6 * up), wy * (1 - 0.35 * up)); body = 1 - rw / R
    heat = body * (0.75 + 0.45 * stq) - t ** 1.3 * 0.75 * (0.5 + nB)
    a_core = ss(0.0, 0.10, heat); halo = np.exp(-(np.clip(-body, 0, None) / 0.18) ** 2) * 0.35 * (1 - t) ** 1.5
    rgb = ramp(np.clip(heat * 1.25, 0, 1) * (1 - 0.35 * t), HELL)
    rgb = rgb * a_core[..., None] + np.array([0.9, 0.12, 0.05]) * (1 - a_core[..., None])
    return rgb, np.clip(a_core + halo * (1 - a_core), 0, 1) * (1 - ss(0.88, 1.0, np.hypot(xx, yy)))
def mist_cell(t):
    R = 0.6 + 0.32 * t ** 0.5; wx = xx + (nB - 0.5) * 0.6; wy = yy + (nA - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R + (stq - 0.5) * 0.25 - t ** 1.5 * 0.8 * (0.5 + nA)
    return np.ones((C, C, 3)) * np.array([0.06, 0.03, 0.05]), ss(0.0, 0.45, body) * 0.7 * (1 - 0.3 * t) * (1 - ss(0.88, 1.0, np.hypot(xx, yy)))
def dust_cell(t):  # 금빛 먼지 (가산)
    R = 0.55 + 0.35 * t ** 0.5; wx = xx + (nB - 0.5) * 0.5; wy = yy + (nA - 0.5) * 0.5
    body = 1 - np.hypot(wx, wy) / R + (stq - 0.5) * 0.3 - t ** 1.4 * 0.7 * (0.5 + nA)
    a = ss(0.0, 0.6, body) * 0.55 * (1 - 0.4 * t) * (1 - ss(0.88, 1.0, np.hypot(xx, yy)))
    return ramp(np.clip(body, 0, 1) * 0.8 + 0.2, HOLY), a
def sheet(fn, name):
    rgbS = np.zeros((C * G, C * G, 3)); aS = np.zeros((C * G, C * G))
    for i in range(G * G):
        rr, cc = divmod(i, G); rgb, a = fn(i / (G * G - 1))
        rgbS[rr * C:(rr + 1) * C, cc * C:(cc + 1) * C] = rgb; aS[rr * C:(rr + 1) * C, cc * C:(cc + 1) * C] = a
    save(rgbS, aS, name)
sheet(flame_cell, "T_HellFlame_Sheet"); sheet(mist_cell, "T_DarkMist_Sheet"); sheet(dust_cell, "T_HolyDust_Sheet")

# ---------- 베기 스트로크 4x4 (256x64 칸, 머리 = 오른쪽) ----------
CW, CH = 256, 32
u = np.linspace(0, 1, CW)[None, :]; vv = np.linspace(0, 1, CH)[:, None]
N1 = smooth_noise(CW, CH, 50, 50, 3, 21); strands = smooth_noise(CW, CH, 120, 4, 3, 23)
def stroke_cell(t, stops):
    head = min(1.0, t / 0.45) ** 0.7; tail = max(0.0, (t - 0.15) / 0.85) ** 1.3
    rel = np.clip((u - tail) / max(head - tail, 1e-3), 0, 1)
    inside = ss(tail, tail + 0.05, u) * (1 - ss(head - 0.012, head + 0.004, u))
    width = np.sin(np.pi * rel) ** 0.55
    d = np.abs(vv - 0.5 - (N1 - 0.5) * 0.08) / (0.38 * width + 1e-3)
    core = np.exp(-(d / 0.16) ** 2); body = np.clip(1 - d, 0, 1) ** 1.5 * (0.55 + 0.45 * strands)
    heat = np.clip(core + body * 0.75, 0, 1) * (0.45 + 0.55 * rel ** 0.6)
    a = np.clip(ss(0.02, 0.25, heat) + np.exp(-(d / 0.9) ** 2) * 0.25 * width, 0, 1) * inside
    return ramp(heat, stops), a
for nm, stops in (("T_SlashHell_Sheet", HELL), ("T_SlashHoly_Sheet", HOLY)):
    rgbS = np.zeros((CH * 8, CW * 2, 3)); aS = np.zeros((CH * 8, CW * 2))
    for i in range(16):
        rr, cc = divmod(i, 2); rgb, a = stroke_cell(i / 15, stops)
        rgbS[rr * CH:(rr + 1) * CH, cc * CW:(cc + 1) * CW] = rgb; aS[rr * CH:(rr + 1) * CH, cc * CW:(cc + 1) * CW] = a
    save(rgbS, aS, nm)

# ---------- 사슬 (가로 128x32, 고리 4개) ----------
S = 4; W, H = 128 * S, 32 * S
im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
for i in range(4):
    cx = (i + 0.5) * W / 4
    if i % 2 == 0: d.rounded_rectangle([cx - 20 * S, H / 2 - 9 * S, cx + 20 * S, H / 2 + 9 * S], radius=9 * S, outline=255, width=4 * S)
    else: d.line([(cx - 20 * S, H / 2), (cx + 20 * S, H / 2)], fill=255, width=5 * S)
a = np.asarray(im, float) / 255; g = np.asarray(im.filter(ImageFilter.GaussianBlur(4 * S)), float) / 255
A = np.asarray(Image.fromarray((np.clip(a + g, 0, 1) * 255).astype(np.uint8)).resize((128, 32), Image.LANCZOS), float) / 255
lum = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).resize((128, 32), Image.LANCZOS), float) / 255
save(np.dstack([0.4 + 0.6 * lum] * 3), A, "T_Chain")

# ---------- 장부 종잇조각 (알파) / 음표 / 하트 / 금 간 바닥 ----------
yy, xx = grid(64)
pg = (np.abs(xx) < 0.75) & (np.abs(yy) < 0.9)
ink = (np.abs(((yy + 1) * 6) % 1 - 0.5) < 0.12) & (np.abs(xx) < 0.6) & (smooth_noise(64, 64, 6, 64, 2, 5) > 0.42)
burn = ss(0.55, 0.95, smooth_noise(64, 64, 18, 18, 3, 6) + np.hypot(xx, yy) * 0.35)
rgb = np.ones((64, 64, 3)) * np.array([0.92, 0.85, 0.68]); rgb[ink] = [0.3, 0.12, 0.1]
rgb = rgb * (1 - burn[..., None]) + np.array([1, 0.45, 0.1]) * burn[..., None]
save(rgb, pg * (1 - ss(0.85, 1, burn)), "T_Page")
S = 4; N = 128 * S
im = Image.new("L", (N, N), 0); d = ImageDraw.Draw(im)
d.ellipse([N * .2, N * .62, N * .48, N * .84], fill=255); d.line([(N * .46, N * .72), (N * .46, N * .14)], fill=255, width=7 * S)
d.polygon([(N * .46, N * .14), (N * .74, N * .26), (N * .74, N * .38), (N * .46, N * .26)], fill=255)
a = np.asarray(im, float) / 255; g = np.asarray(im.filter(ImageFilter.GaussianBlur(6 * S)), float) / 255
save([1, 1, 1], np.asarray(Image.fromarray((np.clip(a + g * 1.2, 0, 1) * 255).astype(np.uint8)).resize((128, 128), Image.LANCZOS), float) / 255, "T_Note")
yy, xx = grid(512); X = xx * 1.45; Y = -yy * 1.45 + 0.25
m = (((X ** 2 + Y ** 2 - 1) ** 3 - X ** 2 * Y ** 3) < 0).astype(np.uint8) * 255
mi = Image.fromarray(m); er = np.asarray(mi.filter(ImageFilter.MinFilter(25)), float) / 255
mm = m / 255.0; gl = np.asarray(mi.filter(ImageFilter.GaussianBlur(28)), float) / 255
hA = np.clip((mm - er) * 1.0 + er * 0.45 + gl * 0.5, 0, 1)
save([1, 1, 1], np.asarray(Image.fromarray((hA * 255).astype(np.uint8)).resize((128, 128), Image.LANCZOS), float) / 255, "T_Heart")
S = 256; yy, xx = grid(S); r = np.hypot(xx, yy)
im = Image.new("L", (S * 2, S * 2), 0); d = ImageDraw.Draw(im); rng = np.random.default_rng(9)
for k in range(9):
    a0 = k / 9 * 2 * math.pi + rng.uniform(-.2, .2); pts = [(S, S)]; rr_ = 0.0; ang_ = a0
    while rr_ < 0.95:
        rr_ += rng.uniform(.06, .12); ang_ += rng.uniform(-.25, .25); pts.append((S + rr_ * S * math.cos(ang_), S + rr_ * S * math.sin(ang_)))
    d.line(pts, fill=255, width=6)
    for j in range(2, len(pts) - 1, 3):
        b = ang_ + rng.choice([-1, 1]) * 0.8; p0 = pts[j]; d.line([p0, (p0[0] + 40 * math.cos(b), p0[1] + 40 * math.sin(b))], fill=200, width=4)
cr = np.asarray(im.resize((S, S), Image.LANCZOS), float) / 255
gl = np.asarray(im.filter(ImageFilter.GaussianBlur(10)).resize((S, S), Image.LANCZOS), float) / 255
fade = 1 - ss(0.7, 1.0, r)
save([1, 1, 1], np.clip(cr + gl * 1.1, 0, 1) * fade, "T_Crack")
print("ok")
