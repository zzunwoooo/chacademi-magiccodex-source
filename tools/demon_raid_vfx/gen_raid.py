# 룰레타(roulette) / 솜니아(somnia) 레이드 텍스처. 흰~회색 중립 램프, 색은 머티리얼 tint.
# usage: python gen_raid.py <roulette|somnia> <outdir>
import sys, math, os, numpy as np
from PIL import Image, ImageDraw, ImageFilter
THEME = sys.argv[1]; OUT = sys.argv[2] + "/"; os.makedirs(OUT, exist_ok=True)

def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def save(rgb, a, name):
    a = np.clip(a, 0, 1)
    if np.ndim(rgb) == 1 or (isinstance(rgb, list)): rgb = np.ones(a.shape + (3,)) * np.array(rgb, float)
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), a[..., None]]) * 255).astype(np.uint8), "RGBA").save(OUT + name + ".png")
WHITE = [(1.0, (1, 1, 1)), (0.6, (0.92, 0.92, 0.92)), (0.3, (0.7, 0.7, 0.7)), (0.0, (0.4, 0.4, 0.4))]
def ramp(h, stops):
    h = np.clip(h, 0, 1); out = np.zeros(h.shape + (3,))
    for (h1, c1), (h0, c0) in zip(stops[:-1], stops[1:]):
        m = (h <= h1) & (h >= h0); u = ((h - h0) / (h1 - h0))[..., None]
        out[m] = (np.array(c0) * (1 - u) + np.array(c1) * u)[m]
    return out
def grid(n): return np.mgrid[-1:1:n * 1j, -1:1:n * 1j]
def lineart(N, S, draw, blur=6, glow=1.3, name=None, size=None):
    """PIL 선 그림을 S배로 그려 글로우 후 축소 → 흰색 알파 텍스처."""
    W, H = (N if isinstance(N, tuple) else (N, N))
    im = Image.new("L", (W * S, H * S), 0); d = ImageDraw.Draw(im); draw(d, W * S, H * S, S)
    a = np.asarray(im, float) / 255; g = np.asarray(im.filter(ImageFilter.GaussianBlur(blur * S)), float) / 255
    A = Image.fromarray((np.clip(a + g * glow, 0, 1) * 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)
    save([1, 1, 1], np.asarray(A, float) / 255, name)

# ================= 공통 =================
v = np.linspace(-1, 1, 32)[:, None] * np.ones((1, 64))
save([1, 1, 1], np.exp(-(v / 0.22) ** 2) + np.exp(-(v / 0.6) ** 2) * 0.35, "T_Line")
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
ang = np.arctan2(y1 * 2, x1 * 2); nf = smooth_noise(128, 128, 8, 8, 2, 3)
rays = (0.5 + 0.5 * np.cos(ang * 10)) ** 6 * 0.8 + (0.5 + 0.5 * np.cos(ang * 17 + 1)) ** 10 * 0.6
save([1, 1, 1], np.clip(np.exp(-(r1 / 0.15) ** 2) + rays * np.exp(-(r1 / 0.7) ** 1.5) * (0.6 + 0.6 * nf), 0, 1) * (1 - ss(0.9, 1, r1)), "T_Flare")
W, H = 256, 32
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
tp = np.sin(np.pi * x) ** 0.8 * (0.35 + 0.65 * x)
save([1, 1, 1], np.clip(np.exp(-(dy / (0.16 * tp + 0.01)) ** 2) + np.exp(-(dy / (0.55 * tp + 1e-3)) ** 2) * 0.35 * tp, 0, 1), "T_Streak")
PW, PH = 64, 256
px_ = np.linspace(-1, 1, PW)[None, :]; py_ = np.linspace(0, 1, PH)[:, None]
st = smooth_noise(PW, PH, 6, 140, 3, 7); env = ss(0, 0.12, py_) * (1 - ss(0.7, 1.0, py_))
core = np.exp(-(px_ / 0.12) ** 2); body = np.exp(-(px_ / 0.42) ** 2) * (0.55 + 0.6 * st)
save(ramp(np.clip(core + body * 0.6, 0, 1), WHITE), np.clip(core + body * 0.7, 0, 1) * env, "T_Pillar")
# 부채꼴/띠 채움: V = 안→밖(부채꼴) / 폭 방향(띠), U 방향 결
FW, FH = 64, 64
fv = np.linspace(0, 1, FH)[:, None] * np.ones((1, FW)); fs = smooth_noise(FW, FH, 4, 40, 3, 31)
save([1, 1, 1], np.clip((0.25 + 0.75 * fv ** 1.6) * (0.6 + 0.5 * fs) + np.exp(-((fv - 0.97) / 0.03) ** 2), 0, 1) * ss(0, 0.08, fv), "T_FanFill")
bv = np.linspace(-1, 1, 32)[:, None] * np.ones((1, 128)); bs = smooth_noise(128, 32, 40, 4, 3, 33)
save([1, 1, 1], np.clip(np.exp(-(bv / 0.75) ** 6) * (0.45 + 0.55 * bs) + np.exp(-((np.abs(bv) - 0.85) / 0.06) ** 2) * 0.9, 0, 1), "T_Band")
# 파편
yy, xx = grid(64)
dshape = 0.85 - (np.abs(xx) * 1.9 + np.abs(yy) * 1.05)
a = ss(-0.02, 0.04, dshape); rim = np.exp(-(np.clip(dshape, 0, None) / 0.1) ** 2)
save(np.array([0.06, 0.05, 0.07]) * (1 - rim[..., None]) + np.array([1, 1, 1]) * rim[..., None], a, "T_ShardDark")
save([1, 1, 1], np.clip(a * (0.35 + 0.65 * rim) + np.exp(-(np.clip(-dshape, 0, None) / 0.1) ** 2) * 0.4, 0, 1), "T_ShardLight")
# 금 간 바닥
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
save([1, 1, 1], np.clip(cr + gl * 1.1, 0, 1) * (1 - ss(0.7, 1.0, r)), "T_Crack")
# 4x4 시트: 어두운 안개(알파) / 빛 먼지(가산)
C, G = 64, 4
yy, xx = grid(C)
nA = smooth_noise(C, C, 18, 18, 4, 11); nB = smooth_noise(C, C, 8, 8, 3, 12); stq = smooth_noise(C, C, 4, 22, 3, 13)
def mist_cell(t):
    R = 0.6 + 0.32 * t ** 0.5; wx = xx + (nB - 0.5) * 0.6; wy = yy + (nA - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R + (stq - 0.5) * 0.25 - t ** 1.5 * 0.8 * (0.5 + nA)
    return np.ones((C, C, 3)) * np.array([0.06, 0.04, 0.07]), ss(0.0, 0.45, body) * 0.7 * (1 - 0.3 * t) * (1 - ss(0.88, 1.0, np.hypot(xx, yy)))
def dust_cell(t):
    R = 0.55 + 0.35 * t ** 0.5; wx = xx + (nB - 0.5) * 0.5; wy = yy + (nA - 0.5) * 0.5
    body = 1 - np.hypot(wx, wy) / R + (stq - 0.5) * 0.3 - t ** 1.4 * 0.7 * (0.5 + nA)
    return ramp(np.clip(body, 0, 1) * 0.8 + 0.2, WHITE), ss(0.0, 0.6, body) * 0.55 * (1 - 0.4 * t) * (1 - ss(0.88, 1.0, np.hypot(xx, yy)))
def sheet(fn, name):
    rgbS = np.zeros((C * G, C * G, 3)); aS = np.zeros((C * G, C * G))
    for i in range(G * G):
        rr, cc = divmod(i, G); rgb, a = fn(i / (G * G - 1))
        rgbS[rr * C:(rr + 1) * C, cc * C:(cc + 1) * C] = rgb; aS[rr * C:(rr + 1) * C, cc * C:(cc + 1) * C] = a
    save(rgbS, aS, name)
sheet(mist_cell, "T_DarkMist_Sheet"); sheet(dust_cell, "T_Dust_Sheet")
# 베기 스트로크 2x8 (256x32 칸, 머리 = 오른쪽), 중립
CW, CH = 256, 32
u = np.linspace(0, 1, CW)[None, :]; vv = np.linspace(0, 1, CH)[:, None]
N1 = smooth_noise(CW, CH, 50, 50, 3, 21); strands = smooth_noise(CW, CH, 120, 4, 3, 23)
def stroke_cell(t):
    head = min(1.0, t / 0.45) ** 0.7; tail = max(0.0, (t - 0.15) / 0.85) ** 1.3
    rel = np.clip((u - tail) / max(head - tail, 1e-3), 0, 1)
    inside = ss(tail, tail + 0.05, u) * (1 - ss(head - 0.012, head + 0.004, u))
    width = np.sin(np.pi * rel) ** 0.55
    dd = np.abs(vv - 0.5 - (N1 - 0.5) * 0.08) / (0.38 * width + 1e-3)
    core = np.exp(-(dd / 0.16) ** 2); body = np.clip(1 - dd, 0, 1) ** 1.5 * (0.55 + 0.45 * strands)
    heat = np.clip(core + body * 0.75, 0, 1) * (0.45 + 0.55 * rel ** 0.6)
    return ramp(heat, WHITE), np.clip(ss(0.02, 0.25, heat) + np.exp(-(dd / 0.9) ** 2) * 0.25 * width, 0, 1) * inside
rgbS = np.zeros((CH * 8, CW * 2, 3)); aS = np.zeros((CH * 8, CW * 2))
for i in range(16):
    rr, cc = divmod(i, 2); rgb, a = stroke_cell(i / 15)
    rgbS[rr * CH:(rr + 1) * CH, cc * CW:(cc + 1) * CW] = rgb; aS[rr * CH:(rr + 1) * CH, cc * CW:(cc + 1) * CW] = a
save(rgbS, aS, "T_Slash_Sheet")

def strip_glyphs(name, glyph, n=8):
    """512x128 룬 띠: 위아래 선 + n칸 문양."""
    def dr(d, W, H, S):
        d.line([(0, 18 * S), (W, 18 * S)], fill=200, width=2 * S); d.line([(0, H - 18 * S), (W, H - 18 * S)], fill=200, width=2 * S)
        cw = W / n
        for i in range(n):
            glyph(d, cw * (i + 0.5), H / 2, 26 * S, i, S)
            d.ellipse([cw * (i + 0.92) - 3 * S, H / 2 - 3 * S, cw * (i + 0.92) + 3 * S, H / 2 + 3 * S], fill=170)
    lineart((512, 128), 3, dr, 6, 1.4, name)

# ================= 룰레타 =================
def spade(d, cx, cy, s, fill=None, w=3):
    pts = [(cx, cy - s)] + [(cx + s * 0.95 * math.sin(t) * (0.6 + 0.4 * math.cos(t)), cy - s * 0.05 + s * 0.55 * (1 - math.cos(t)) * 0.9) for t in np.linspace(0.2, math.pi * 0.95, 10)]
    pts += [(cx, cy + s * 0.45)] + [(cx - p[0] + cx, p[1]) for p in reversed(pts[1:])]
    pts = [(cx, cy - s)] + [(cx + s * 0.9 * math.sin(a), cy + s * (0.15 - 0.55 * math.cos(a))) for a in np.linspace(0.6, 2.4, 8)] + [(cx + s * 0.18, cy + s * 0.45), (cx - s * 0.18, cy + s * 0.45)] + [(cx - s * 0.9 * math.sin(a), cy + s * (0.15 - 0.55 * math.cos(a))) for a in np.linspace(2.4, 0.6, 8)]
    d.polygon(pts, fill=fill, outline=255, width=w)
    d.polygon([(cx, cy + s * 0.2), (cx - s * 0.35, cy + s), (cx + s * 0.35, cy + s)], fill=fill, outline=255, width=w)
def heart(d, cx, cy, s, fill=None, w=3):
    pts = []
    for t in np.linspace(0, 2 * math.pi, 48):
        X = 16 * math.sin(t) ** 3; Y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + X / 17 * s, cy - Y / 17 * s))
    d.polygon(pts, fill=fill, outline=255, width=w)
def diamond(d, cx, cy, s, fill=None, w=3): d.polygon([(cx, cy - s), (cx + s * 0.7, cy), (cx, cy + s), (cx - s * 0.7, cy)], fill=fill, outline=255, width=w)
def club(d, cx, cy, s, fill=None, w=3):
    for ox, oy in ((0, -0.45), (-0.45, 0.05), (0.45, 0.05)):
        d.ellipse([cx + (ox - 0.38) * s, cy + (oy - 0.38) * s, cx + (ox + 0.38) * s, cy + (oy + 0.38) * s], fill=fill, outline=255, width=w)
    d.polygon([(cx, cy), (cx - s * 0.32, cy + s), (cx + s * 0.32, cy + s)], fill=fill, outline=255, width=w)
SUITS = [spade, heart, diamond, club]

if THEME == "roulette":
    def g_suit(d, cx, cy, s, i, S):
        SUITS[i % 4](d, cx, cy, s * 0.8, None, 3 * S)
    strip_glyphs("T_Rune", g_suit)
    def sigil(d, W, H, S):
        c = W / 2
        def circ(r, w, f=255): d.ellipse([c - r, c - r, c + r, c + r], outline=f, width=int(w))
        circ(0.96 * c, 3 * S); circ(0.88 * c, 2 * S, 200); circ(0.55 * c, 3 * S); circ(0.22 * c, 3 * S)
        for i in range(36):
            a = i / 36 * 2 * math.pi; r0 = 0.88 * c; r1 = r0 + (0.07 if i % 6 == 0 else 0.035) * c
            d.line([(c + r0 * math.cos(a), c + r0 * math.sin(a)), (c + r1 * math.cos(a), c + r1 * math.sin(a))], fill=255, width=2 * S)
        for k in range(6):  # 6구역 바퀴살 + 구역마다 문양
            a = k / 6 * 2 * math.pi - math.pi / 2 + math.pi / 6
            d.line([(c + 0.22 * c * math.cos(a), c + 0.22 * c * math.sin(a)), (c + 0.88 * c * math.cos(a), c + 0.88 * c * math.sin(a))], fill=255, width=3 * S)
            am = a + math.pi / 6; SUITS[k % 4](d, c + 0.71 * c * math.cos(am), c + 0.71 * c * math.sin(am), 0.09 * c, None, 2 * S)
        d.polygon([(c, c - 0.5 * c), (c + 0.05 * c, c), (c, c + 0.12 * c), (c - 0.05 * c, c)], fill=255)  # 바늘
    lineart(256, 3, sigil, 6, 1.3, "T_Sigil")
    # 카드 (흰 앞면 + 테두리 + 가운데 스페이드), 64x96
    def card(d, W, H, S):
        d.rounded_rectangle([3 * S, 3 * S, W - 3 * S, H - 3 * S], radius=7 * S, fill=230, outline=255, width=3 * S)
        d.rounded_rectangle([8 * S, 8 * S, W - 8 * S, H - 8 * S], radius=4 * S, outline=120, width=2 * S)
        spade(d, W / 2, H / 2, 14 * S, 40, 2 * S)
    lineart((64, 96), 3, card, 2, 0.5, "T_Card")
    # 칩 위에서 본 모습 64
    yy, xx = grid(64); rr = np.hypot(xx, yy); th = np.arctan2(yy, xx)
    seg = (np.floor((th + math.pi) / (2 * math.pi) * 8) % 2)
    lum = np.where(rr > 0.72, np.where(seg > 0, 1.0, 0.45), np.where(rr > 0.62, 0.95, np.where(rr > 0.55, 0.5, 0.75)))
    lum = lum + np.exp(-((rr - 0.3) / 0.05) ** 2) * 0.3
    save(np.dstack([lum] * 3), ss(0.98, 0.92, rr), "T_Chip")
    # 동전 64 (테두리 + 뿔 문양)
    def coin(d, W, H, S):
        c = W / 2; d.ellipse([2 * S, 2 * S, W - 2 * S, H - 2 * S], fill=200, outline=255, width=3 * S)
        d.ellipse([9 * S, 9 * S, W - 9 * S, H - 9 * S], outline=140, width=2 * S)
        d.ellipse([c - 9 * S, c - 6 * S, c + 9 * S, c + 12 * S], fill=255)
        for sx in (-1, 1): d.polygon([(c + sx * 6 * S, c - 2 * S), (c + sx * 11 * S, c - 15 * S), (c + sx * 2 * S, c - 4 * S)], fill=255)
    lineart(64, 3, coin, 1.5, 0.4, "T_Coin")
    # 무늬 4종 (채운 문양, 64)
    for nm, fn in zip(["T_Spade", "T_Hearts", "T_Diamond", "T_Club"], SUITS):
        lineart(64, 3, lambda d, W, H, S, fn=fn: fn(d, W / 2, H / 2 - 2 * S, W * 0.36, 255, 2 * S), 3, 0.8, nm)
    # 주사위 64
    def die(d, W, H, S):
        d.rounded_rectangle([4 * S, 4 * S, W - 4 * S, H - 4 * S], radius=10 * S, fill=230, outline=255, width=3 * S)
        for ox, oy in ((-1, -1), (1, -1), (0, 0), (-1, 1), (1, 1)):
            cx, cy = W / 2 + ox * 15 * S, H / 2 + oy * 15 * S; d.ellipse([cx - 5 * S, cy - 5 * S, cx + 5 * S, cy + 5 * S], fill=60)
    lineart(64, 3, die, 2, 0.5, "T_Die")

# ================= 솜니아 =================
if THEME == "somnia":
    def g_moon(d, cx, cy, s, i, S):  # 달의 위상 8단계
        k = i % 8
        d.ellipse([cx - s * .7, cy - s * .7, cx + s * .7, cy + s * .7], outline=255, width=2 * S)
        f = math.cos(k / 8 * 2 * math.pi)
        if k == 4: d.ellipse([cx - s * .6, cy - s * .6, cx + s * .6, cy + s * .6], fill=255)
        elif k != 0:
            for yy_ in np.linspace(-0.6, 0.6, 25):
                hw = math.sqrt(max(0, 0.36 - yy_ ** 2)); x0 = -hw if k < 4 else hw * f; x1 = -hw * f if k < 4 else hw
                x0, x1 = (cx + min(x0, x1) * s, cx + max(x0, x1) * s) if k < 4 else (cx - hw * s, cx - hw * f * s)
                d.line([(x0, cy + yy_ * s), (x1, cy + yy_ * s)], fill=255, width=2 * S)
    strip_glyphs("T_Rune", g_moon)
    def sigil(d, W, H, S):  # 초승달 + 감은 눈 + 별
        c = W / 2
        def circ(r, w, f=255): d.ellipse([c - r, c - r, c + r, c + r], outline=f, width=int(w))
        circ(0.96 * c, 3 * S); circ(0.87 * c, 2 * S, 200); circ(0.5 * c, 2 * S, 220)
        for i in range(8):
            a = i / 8 * 2 * math.pi; R0 = 0.66 * c
            x0, y0 = c + R0 * math.cos(a), c + R0 * math.sin(a); s = 0.06 * c
            d.polygon([(x0, y0 - s * 1.6), (x0 + s * .35, y0 - s * .35), (x0 + s * 1.6, y0), (x0 + s * .35, y0 + s * .35), (x0, y0 + s * 1.6), (x0 - s * .35, y0 + s * .35), (x0 - s * 1.6, y0), (x0 - s * .35, y0 - s * .35)], fill=255)
        for i in range(24):
            a = i / 24 * 2 * math.pi; d.line([(c + 0.87 * c * math.cos(a), c + 0.87 * c * math.sin(a)), (c + 0.93 * c * math.cos(a), c + 0.93 * c * math.sin(a))], fill=255, width=2 * S)
        # 감은 눈 (아래로 볼록한 곡선 + 속눈썹)
        pts = [(c + t * 0.36 * c, c + 0.08 * c * (1 - t * t)) for t in np.linspace(-1, 1, 30)]
        d.line(pts, fill=255, width=4 * S)
        for t in np.linspace(-0.8, 0.8, 7):
            px0, py0 = c + t * 0.36 * c, c + 0.08 * c * (1 - t * t); d.line([(px0, py0), (px0 + t * 0.05 * c, py0 + 0.1 * c)], fill=255, width=3 * S)
        # 위 초승달
        d.chord([c - 0.18 * c, c - 0.42 * c, c + 0.18 * c, c - 0.06 * c], 0, 360, fill=255)
        d.ellipse([c - 0.1 * c, c - 0.46 * c, c + 0.24 * c, c - 0.12 * c], fill=0)
    lineart(256, 3, sigil, 6, 1.3, "T_Sigil")
    # 초승달 64
    yy, xx = grid(64); m1 = np.hypot(xx, yy) < 0.8; m2 = np.hypot(xx - 0.38, yy + 0.18) < 0.68
    m = (m1 & ~m2).astype(float); mi = Image.fromarray((m * 255).astype(np.uint8))
    gl = np.asarray(mi.filter(ImageFilter.GaussianBlur(4)), float) / 255
    save([1, 1, 1], np.clip(m + gl * 0.8, 0, 1), "T_Moon")
    # Z 글자 64
    lineart(64, 4, lambda d, W, H, S: d.line([(16 * S, 16 * S), (48 * S, 16 * S), (16 * S, 48 * S), (48 * S, 48 * S)], fill=255, width=6 * S, joint="curve"), 3, 1.0, "T_Zzz")
    # 음표 64
    def note(d, W, H, S):
        d.ellipse([12 * S, 38 * S, 28 * S, 50 * S], fill=255); d.line([(27 * S, 44 * S), (27 * S, 12 * S)], fill=255, width=4 * S)
        d.polygon([(27 * S, 12 * S), (44 * S, 18 * S), (44 * S, 26 * S), (27 * S, 20 * S)], fill=255)
    lineart(64, 4, note, 3, 1.0, "T_Note")
    # 깃털 64x128 (뿌리 = 아래)
    FW, FH = 64, 128
    fx = np.linspace(-1, 1, FW)[None, :]; fy = np.linspace(1, 0, FH)[:, None]
    half = 0.62 * np.sin(np.pi * np.clip(fy * 0.92 + 0.04, 0, 1)) ** 0.7 * (0.5 + 0.5 * (1 - (fy - 0.55) ** 2))
    nb = smooth_noise(FW, FH, 3, 40, 2, 8); barb = 0.5 + 0.5 * np.sin((fy * 50 - np.abs(fx) * 9))
    edge = half - np.abs(fx) - (nb - 0.5) * 0.08; a = ss(-0.02, 0.04, edge) * ss(0.0, 0.06, fy)
    shaft = np.exp(-(fx / 0.05) ** 2) * (fy > 0.02)
    save(np.clip(0.88 + 0.12 * barb, 0, 1)[..., None] * np.ones((1, 1, 3)), np.clip(a * (0.8 + 0.2 * barb) + shaft * 0.5, 0, 1), "T_Feather")
    # 꿈 방울 64 (얇은 테 + 하이라이트)
    yy, xx = grid(64); rr = np.hypot(xx, yy)
    save([1, 1, 1], np.clip(np.exp(-((rr - 0.82) / 0.07) ** 2) + np.exp(-((rr) / 0.85) ** 6) * 0.12 + np.exp(-(np.hypot(xx + 0.35, yy + 0.35) / 0.12) ** 2), 0, 1) * (rr < 0.97), "T_Bubble")
    # 오선 벽 (자장가 고리): 가로 5줄, 아래 밝고 위로 사라짐, 128x64
    sy = np.linspace(0, 1, 64)[:, None] * np.ones((1, 128))
    lines = sum(np.exp(-((sy - (0.3 + 0.1 * k)) / 0.012) ** 2) for k in range(5))
    save([1, 1, 1], np.clip(lines * 0.9 + (1 - sy) ** 3 * 0.35, 0, 1) * ss(1.0, 0.85, sy) * ss(0.0, 0.08, sy), "T_Staff")
    # 소용돌이 256 (잠의 웅덩이)
    yy, xx = grid(256); rr = np.hypot(xx, yy); th = np.arctan2(yy, xx)
    sp = 0.5 + 0.5 * np.cos(th * 3 + rr * 9)
    save([1, 1, 1], np.clip(sp ** 3 * np.clip(1 - rr, 0, 1) ** 0.7 * 1.1 + np.exp(-(rr / 0.2) ** 2) * 0.6, 0, 1) * (1 - ss(0.85, 1.0, rr)), "T_Spiral")
print("ok", THEME)
