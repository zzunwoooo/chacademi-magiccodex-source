# 시간의 망령 VFX 텍스처. usage: python gen_time.py tex_t
# 1) 하베스트 공용 텍스처(gen_pumpkin.py)를 같은 이름으로 만든 뒤  2) 시간 테마(문자판·로마숫자·모래·톱니·시곗바늘)로 바꾸거나 새로 추가한다.
# 색은 대부분 흰~회색으로 두고 머티리얼 tint 로 칠한다 (청록 = 시간, 금빛 = 모래·황동, 남색 = 그림자).
import sys, os, math, subprocess, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = (sys.argv[1] if len(sys.argv) > 1 else "tex_t") + "/"; os.makedirs(OUT, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
subprocess.run([sys.executable, os.path.join(HERE, "../pumpkin_reaper_vfx/gen_pumpkin.py"), OUT], check=True, stdout=subprocess.DEVNULL)
for junk in ("T_Bat", "T_BoneClaw", "T_Candy", "T_Chain", "T_Ghost", "T_Hex", "T_PumpkinFlame_Sheet", "T_VioletFlame_Sheet", "T_WarnShare"):
    p = OUT + junk + ".png"
    if os.path.exists(p): os.remove(p)

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
def glowify(im, blur, k, size):
    gl = np.asarray(im.filter(ImageFilter.GaussianBlur(blur)).resize(size, Image.LANCZOS), float) / 255
    sh = np.asarray(im.filter(ImageFilter.GaussianBlur(1.2)).resize(size, Image.LANCZOS), float) / 255
    return np.clip(sh + gl * k, 0, 1)

ROMAN = ["XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"]
def roman(d, cx, cy, txt, h, w=None, width=7):
    """로마 숫자를 선으로 그림 (I·V·X 만)."""
    w = w or h * 0.42; gap = w * 0.25; total = sum(w * (0.35 if ch == "I" else 1) for ch in txt) + gap * (len(txt) - 1); x = cx - total / 2
    for ch in txt:
        cw = w * (0.35 if ch == "I" else 1)
        if ch == "I": d.line([(x + cw / 2, cy - h / 2), (x + cw / 2, cy + h / 2)], fill=255, width=width)
        elif ch == "V": d.line([(x, cy - h / 2), (x + cw / 2, cy + h / 2), (x + cw, cy - h / 2)], fill=255, width=width)
        else: d.line([(x, cy - h / 2), (x + cw, cy + h / 2)], fill=255, width=width); d.line([(x + cw, cy - h / 2), (x, cy + h / 2)], fill=255, width=width)
        x += cw + gap

# ---------------- 룬띠: 로마 숫자 + 눈금 (512x128) ----------------
def glyph_strip(w=1536, h=384):
    im = Image.new("L", (w, h), 0); d = ImageDraw.Draw(im); n = 12; cw = w / n
    d.line([(0, 40), (w, 40)], fill=200, width=8); d.line([(0, h - 40), (w, h - 40)], fill=200, width=8)
    for k in range(60): x = w * k / 60; d.line([(x, 40), (x, 40 + (40 if k % 5 == 0 else 20))], fill=230, width=6)
    for i in range(n): roman(d, cw * (i + 0.5), h / 2 + 18, ROMAN[i], h * 0.36, width=14)
    return im
save(np.ones((128, 512, 3)), glowify(glyph_strip(), 10, 0.6, (512, 128)), "T_RuneStrip.png")

# ---------------- 가운데 문양: 문자판 (바늘 둘 + 모래시계) 320 ----------------
def dial(size=1152, hands=True):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2
    for r, w in ((0.48, 12), (0.455, 4), (0.33, 8), (0.30, 3), (0.12, 6)): d.ellipse([c - size * r, c - size * r, c + size * r, c + size * r], outline=255, width=w)
    for k in range(60):
        a = math.radians(k * 6 - 90); L1, L2 = size * (0.41 if k % 5 else 0.385), size * 0.455
        d.line([(c + math.cos(a) * L1, c + math.sin(a) * L1), (c + math.cos(a) * L2, c + math.sin(a) * L2)], fill=255, width=10 if k % 5 == 0 else 4)
    for i in range(12):
        a = math.radians(i * 30 - 90); rx, ry = c + math.cos(a) * size * 0.355, c + math.sin(a) * size * 0.355
        roman(d, rx, ry, ROMAN[i], size * 0.045, width=6)
    if hands:
        for ang, L, w in ((-60, 0.22, 16), (100, 0.31, 10)):
            a = math.radians(ang - 90); x1, y1 = c + math.cos(a) * size * L, c + math.sin(a) * size * L
            d.line([(c, c), (x1, y1)], fill=255, width=w)
            d.polygon([(x1 + math.cos(a) * 40, y1 + math.sin(a) * 40), (x1 + math.cos(a + 1.9) * 26, y1 + math.sin(a + 1.9) * 26), (x1 + math.cos(a - 1.9) * 26, y1 + math.sin(a - 1.9) * 26)], fill=255)
    s = size * 0.075   # 가운데 모래시계
    d.polygon([(c - s, c - s * 1.3), (c + s, c - s * 1.3), (c, c), (c + s, c + s * 1.3), (c - s, c + s * 1.3), (c, c)], outline=255, width=7)
    return im
save(np.ones((320, 320, 3)), glowify(dial(), 14, 0.8, (320, 320)), "T_Sigil.png")
save(np.ones((256, 256, 3)), glowify(dial(768, False), 9, 0.7, (256, 256)), "T_Dial.png")

# ---------------- 남색 그림자 안개 4x4 (회색조, 아래쪽 은은히 밝음) ----------------
C, G = 128, 4
cy_, cx_ = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 36, 36, 4, 11); nB = smooth_noise(C, C, 16, 16, 3, 12); stk = smooth_noise(C, C, 7, 45, 3, 13)
def mist_cell(t):
    R = 0.6 + 0.32 * t ** 0.5; wx = cx_ + (nB - 0.5) * 0.6; wy = cy_ + (nA - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R + (stk - 0.5) * 0.25 - t ** 1.5 * 0.8 * (0.5 + nA)
    a = ss(0.0, 0.5, body) * 0.6 * (1 - 0.3 * t); lit = ss(0.1, 0.9, wy * 0.8 + 0.2)[..., None] * (1 - t) * 0.5
    rgb = np.array([0.07, 0.07, 0.13]) * (1 - lit) + np.array([0.18, 0.42, 0.5]) * lit
    return rgb, a * (1 - ss(0.88, 1.0, np.hypot(cx_, cy_)))
rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
for i in range(G*G):
    r, c = divmod(i, G); rgb, a = mist_cell(i / (G*G - 1)); rgbS[r*C:(r+1)*C, c*C:(c+1)*C] = rgb; aS[r*C:(r+1)*C, c*C:(c+1)*C] = a
save(rgbS, aS, "T_Mist_Sheet.png", (256, 256))

# ---------------- 모래 알갱이 (작은 마름모, 흰색) ----------------
D = 64; im = Image.new("L", (256, 256), 0); d = ImageDraw.Draw(im)
d.polygon([(128, 30), (210, 128), (128, 226), (46, 128)], fill=255)
sa = np.asarray(im.filter(ImageFilter.GaussianBlur(10)).resize((D, D), Image.LANCZOS), float) / 255
save(np.ones((D, D, 3)), np.clip(sa * 1.3, 0, 1), "T_Sand.png")

# ---------------- 시곗바늘 실루엣 (옆에서 본 화살 모양, 위 = 끝) 64x256: 테두리 밝음 ----------------
def hand_sil(w=256, h=1024):
    im = Image.new("L", (w, h), 0); d = ImageDraw.Draw(im); c = w / 2
    d.rectangle([c - 14, h * 0.18, c + 14, h * 0.86], fill=255)                                  # 몸
    d.polygon([(c, 0), (c + 70, h * 0.2), (c + 18, h * 0.18), (c - 18, h * 0.18), (c - 70, h * 0.2)], fill=255)   # 화살 머리
    d.ellipse([c - 56, h * 0.78, c + 56, h * 0.9], fill=255)                                      # 평형추
    d.ellipse([c - 30, h * 0.81, c + 30, h * 0.87], fill=0)
    d.polygon([(c - 30, h), (c + 30, h), (c + 14, h * 0.9), (c - 14, h * 0.9)], fill=255)          # 꼬리
    return im
hs = hand_sil(); hb = np.asarray(hs.filter(ImageFilter.GaussianBlur(2)).resize((64, 256), Image.LANCZOS), float) / 255
he = np.asarray(hs.filter(ImageFilter.MinFilter(13)).filter(ImageFilter.GaussianBlur(2)).resize((64, 256), Image.LANCZOS), float) / 255
rim = np.clip(hb - he, 0, 1) * 1.6
save(np.ones((256, 64, 3)) * (0.35 + 0.65 * np.clip(rim, 0, 1))[..., None], np.clip(hb, 0, 1), "T_ClockHand.png")

# ---------------- 톱니바퀴 (위에서 본, 바닥 데칼/공중 회전) 256 ----------------
def gear(size=768, teeth=16):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2; R0, R1 = size * 0.40, size * 0.47
    pts = []
    for k in range(teeth * 4):
        a = k / (teeth * 4) * 2 * math.pi; r = R1 if (k % 4) in (1, 2) else R0
        pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
    d.polygon(pts, fill=255); d.ellipse([c - size * 0.30, c - size * 0.30, c + size * 0.30, c + size * 0.30], fill=0)
    d.ellipse([c - size * 0.08, c - size * 0.08, c + size * 0.08, c + size * 0.08], fill=255)
    for k in range(6):
        a = k / 6 * 2 * math.pi; d.line([(c, c), (c + math.cos(a) * size * 0.31, c + math.sin(a) * size * 0.31)], fill=255, width=26)
    return im
save(np.ones((256, 256, 3)), glowify(gear(), 8, 0.5, (256, 256)), "T_Gear.png")

# ---------------- 모래시계 문장 (정면) 128x192 ----------------
def hourglass(w=512, h=768):
    im = Image.new("L", (w, h), 0); d = ImageDraw.Draw(im); c = w / 2
    d.rectangle([60, 30, w - 60, 70], fill=255); d.rectangle([60, h - 70, w - 60, h - 30], fill=255)
    d.line([(110, 70), (c - 14, h / 2), (110, h - 70)], fill=255, width=16); d.line([(w - 110, 70), (c + 14, h / 2), (w - 110, h - 70)], fill=255, width=16)
    d.polygon([(150, 140), (w - 150, 140), (c, h / 2 - 20)], fill=180); d.polygon([(c, h - 180), (w - 140, h - 80), (140, h - 80)], fill=210)
    d.line([(c, h / 2 - 20), (c, h - 180)], fill=255, width=6)
    return im
save(np.ones((192, 128, 3)), glowify(hourglass(), 10, 0.7, (128, 192)), "T_Hourglass.png")

# ---------------- 금 간 시간 균열 (바닥 데칼, 검은 몸 + 흰 선) ----------------
S = 256; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; rr = np.hypot(xx, yy) + 1e-6
def cracks(size=768, seed=5, n=11):
    im = Image.new("L", (size, size), 0); d = ImageDraw.Draw(im); c = size / 2; rng = np.random.default_rng(seed)
    for k in range(n):
        a0 = k / n * 2 * math.pi + rng.uniform(-0.2, 0.2); px, py = c, c; L = size * rng.uniform(0.32, 0.47); m = 9
        for j in range(m):
            a0 += rng.uniform(-0.45, 0.45); nx, ny = px + math.cos(a0) * L / m, py + math.sin(a0) * L / m
            d.line([(px, py), (nx, ny)], fill=255, width=max(2, int(12 * (1 - j / m)))); px, py = nx, ny
    return im
cr = cracks(); cg = np.asarray(cr.filter(ImageFilter.GaussianBlur(6)).resize((S, S), Image.LANCZOS), float) / 255
cs = np.asarray(cr.filter(ImageFilter.GaussianBlur(1)).resize((S, S), Image.LANCZOS), float) / 255
dark = np.clip(cg * 1.6, 0, 1) * 0.85 * (1 - ss(0.8, 1, rr))
rgb = np.array([0.04, 0.04, 0.08]) * (1 - cs[..., None]) + np.array([0.55, 1.0, 1.0]) * cs[..., None]
save(rgb, np.clip(dark + cs, 0, 1) * (1 - ss(0.85, 1, rr)), "T_Crack.png")

# ---------------- 시간 물결 (동심원 여러 겹, 정지 파동) ----------------
nW = smooth_noise(S, S, 10, 10, 3, 61)
waves = sum(np.exp(-((rr - r0) / 0.018) ** 2) * a for r0, a in ((0.92, 1), (0.78, 0.7), (0.62, 0.5), (0.46, 0.35)))
save(np.ones((S, S, 3)), np.clip(waves * (0.75 + 0.35 * nW), 0, 1) * (1 - ss(0.96, 1, rr)), "T_Ripple.png")
# ---------------- 초침 휩쓸기 와이프 시트: 낫 휘두르기 시트를 흰~회색으로 (색은 tint) ----------------
sw = np.asarray(Image.open(OUT + "T_ScytheSlash_Sheet.png").convert("RGBA"), float) / 255
lum = np.clip(sw[..., :3].max(axis=2) ** 0.8, 0, 1)
save(np.dstack([lum] * 3) * 0.75 + 0.25, sw[..., 3], "T_Sweep_Sheet.png")
# ---------------- 먹 조각 (남색 몸 + 흰 테두리) ----------------
W2, H2 = 256, 32; x2 = np.linspace(0, 1, W2)[None, :]; y2 = np.linspace(-1, 1, H2)[:, None]; dy2 = np.abs(y2)
nS = smooth_noise(W2, H2, 20, 20, 3, 41); profS = np.sin(np.pi * x2) ** 0.7 * (0.4 + 0.6 * x2)
dist = 0.5 * profS - (dy2 + (nS - 0.5) * 0.25); a = ss(-0.03, 0.02, dist); edge = np.exp(-(np.clip(dist, 0, None) / 0.05) ** 2)[..., None]
save(np.array([0.05, 0.05, 0.10]) * (1 - edge) + np.array([0.75, 1.0, 1.0]) * edge, a, "T_InkShard.png")
# ---------------- 흰 줄기 (금빛 기둥·불티용, 색은 tint) ----------------
W3, H3 = 256, 32; x3 = np.linspace(0, 1, W3)[None, :]; y3 = np.linspace(-1, 1, H3)[:, None]; dy3 = np.abs(y3)
taper = x3 ** 1.6 * (1 - ss(0.92, 1.0, x3)); core = np.exp(-(dy3 / (0.09 * taper + 0.01)) ** 2); halo = np.exp(-(dy3 / (0.5 * taper + 1e-3)) ** 2) * 0.45 * taper
save(np.ones((H3, W3, 3)) * (0.55 + 0.45 * np.clip(core, 0, 1))[..., None], np.clip(core + halo, 0, 1), "T_WhiteStreak.png")
for junk in ("T_ScytheSlash_Sheet", "T_WarnBand", "T_WarnFill", "T_WarnGrow", "T_WarnRim"):
    if os.path.exists(OUT + junk + ".png"): os.remove(OUT + junk + ".png")
print("ok")
