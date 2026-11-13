# 공성전 레이드 보스별 전용 텍스처 (흰색 알파, 색은 머티리얼 tint). usage: python gen_siege.py <outdir>
import sys, math, os, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = sys.argv[1] + '/'; os.makedirs(OUT, exist_ok=True)
def lineart(size, draw, name, S=3, blur=5, glow=1.2):
    W, H = size if isinstance(size, tuple) else (size, size)
    im = Image.new('L', (W * S, H * S), 0); d = ImageDraw.Draw(im); draw(d, W * S, H * S, S)
    a = np.asarray(im, float) / 255; g = np.asarray(im.filter(ImageFilter.GaussianBlur(blur * S)), float) / 255
    A = np.clip(a + g * glow, 0, 1); A = Image.fromarray((A * 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)
    A = np.asarray(A, float) / 255
    Image.fromarray((np.dstack([np.ones((H, W, 3)), A[..., None]]) * 255).astype(np.uint8), 'RGBA').save(OUT + name + '.png')
def circ(d, cx, cy, r, w, fill=None): d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=255, width=max(1, int(w)), fill=fill)
def pol(cx, cy, r, a): return (cx + r * math.cos(a), cy + r * math.sin(a))
TAU = math.tau

# ---------- 솜니아: 뜬 눈 / 감은 눈 ----------
def eye(open_):
    def f(d, W, H, S):
        cx, cy = W / 2, H / 2; R = W * 0.46; w = 3 * S
        circ(d, cx, cy, R, w); circ(d, cx, cy, R * 0.93, S)
        for i in range(28):                                   # 바깥 별 눈금
            a = i * TAU / 28; d.line([pol(cx, cy, R * 0.86, a), pol(cx, cy, R * (0.92 if i % 2 else 0.9), a)], fill=255, width=S)
        ew, eh = R * 0.74, R * 0.36
        top = [(cx - ew + 2 * ew * t, cy - eh * math.sin(math.pi * t) ** 0.8) for t in np.linspace(0, 1, 60)]
        bot = [(cx - ew + 2 * ew * t, cy + eh * math.sin(math.pi * t) ** 0.8) for t in np.linspace(0, 1, 60)]
        if open_:
            d.line(top, fill=255, width=w); d.line(bot, fill=255, width=w)
            circ(d, cx, cy, eh * 0.86, w, fill=70); circ(d, cx, cy, eh * 0.6, S)
            for i in range(24): a = i * TAU / 24; d.line([pol(cx, cy, eh * 0.3, a), pol(cx, cy, eh * 0.8, a)], fill=200, width=S)
            d.ellipse([cx - eh * 0.13, cy - eh * 0.7, cx + eh * 0.13, cy + eh * 0.7], fill=255)          # 세로 동공
            for i, t in enumerate(np.linspace(0.12, 0.88, 9)):                                           # 위·아래 속눈썹
                x = cx - ew + 2 * ew * t; y = eh * math.sin(math.pi * t) ** 0.8; L = R * (0.16 + 0.08 * math.sin(math.pi * t))
                d.line([(x, cy - y - 4 * S), (x + (t - .5) * L * 0.7, cy - y - L)], fill=255, width=2 * S)
                d.line([(x, cy + y + 4 * S), (x + (t - .5) * L * 0.5, cy + y + L * 0.7)], fill=220, width=2 * S)
        else:
            d.line(bot, fill=255, width=w)
            for t in np.linspace(0.12, 0.88, 9):
                x = cx - ew + 2 * ew * t; y = eh * math.sin(math.pi * t) ** 0.8; L = R * (0.14 + 0.07 * math.sin(math.pi * t))
                d.line([(x, cy + y + 3 * S), (x + (t - .5) * L * 0.6, cy + y + L)], fill=255, width=2 * S)
            for k, (zx, zy, zs) in enumerate([(0.1, -0.3, 0.1), (0.3, -0.5, 0.075), (0.45, -0.66, 0.055)]):   # z z z
                x, y, s = cx + R * zx, cy + R * zy, R * zs
                d.line([(x - s, y - s), (x + s, y - s), (x - s, y + s), (x + s, y + s)], fill=255, width=2 * S)
        for a in (0.9, 2.2, 3.7, 5.4):                         # 초승달 장식
            x, y = pol(cx, cy, R * 0.8, a); r = R * 0.06
            d.ellipse([x - r, y - r, x + r, y + r], fill=255); d.ellipse([x - r * 0.3, y - r * 1.1, x + r * 1.5, y + r * 0.7], fill=0)
    return f
lineart(384, eye(True), 'T_EyeOpen'); lineart(384, eye(False), 'T_EyeClosed')

# ---------- 룰레타: 룰렛 휠 ----------
def wheel(d, W, H, S):
    cx, cy = W / 2, H / 2; R = W * 0.46
    for i in range(12):
        a0 = math.degrees(i * TAU / 12); 
        if i % 2 == 0: d.pieslice([cx - R * 0.9, cy - R * 0.9, cx + R * 0.9, cy + R * 0.9], a0, a0 + 30, fill=95)
    d.ellipse([cx - R * 0.42, cy - R * 0.42, cx + R * 0.42, cy + R * 0.42], fill=0)
    circ(d, cx, cy, R, 4 * S); circ(d, cx, cy, R * 0.9, 2 * S); circ(d, cx, cy, R * 0.42, 3 * S); circ(d, cx, cy, R * 0.34, S); circ(d, cx, cy, R * 0.1, 3 * S, fill=255)
    for i in range(12):
        a = i * TAU / 12; d.line([pol(cx, cy, R * 0.42, a), pol(cx, cy, R * 0.9, a)], fill=255, width=2 * S)
        b = a + TAU / 24; x, y = pol(cx, cy, R * 0.95, b); r = R * 0.028; d.polygon([(x, y - r * 1.5), (x + r, y), (x, y + r * 1.5), (x - r, y)], fill=255)
        x, y = pol(cx, cy, R * 0.66, b); r = R * 0.05
        d.ellipse([x - r, y - r, x + r, y + r], outline=255, width=2 * S)
    for i in range(4): a = i * TAU / 4 + TAU / 8; d.line([pol(cx, cy, R * 0.1, a), pol(cx, cy, R * 0.34, a)], fill=255, width=3 * S)
lineart(384, wheel, 'T_Wheel')

# ---------- 벨자르: 후광 문양 / 가시 문양 / 빛의 십자창 / 사슬 ----------
def halo_sig(d, W, H, S):
    cx, cy = W / 2, H / 2; R = W * 0.46
    for r, w in ((1, 4), (0.93, 1), (0.7, 2), (0.66, 1), (0.36, 2)): circ(d, cx, cy, R * r, w * S)
    for i in range(36): a = i * TAU / 36; d.line([pol(cx, cy, R * 0.72, a), pol(cx, cy, R * (0.91 if i % 3 == 0 else 0.82), a)], fill=255, width=S * (2 if i % 3 == 0 else 1))
    for a in (0, TAU / 4, TAU / 2, 3 * TAU / 4):                 # 끝이 벌어진 십자
        x, y = pol(cx, cy, R * 0.62, a); n = (-math.sin(a), math.cos(a)); t = R * 0.11
        d.polygon([(cx + n[0] * t * 0.3, cy + n[1] * t * 0.3), (x + n[0] * t, y + n[1] * t), (x - n[0] * t, y - n[1] * t), (cx - n[0] * t * 0.3, cy - n[1] * t * 0.3)], fill=255)
    for i in range(8):                                          # 날개 호
        a = i * TAU / 8 + TAU / 16; x, y = pol(cx, cy, R * 0.52, a); r = R * 0.16
        d.arc([x - r, y - r, x + r, y + r], math.degrees(a) - 100, math.degrees(a) + 100, fill=255, width=2 * S)
def thorn_sig(d, W, H, S):
    cx, cy = W / 2, H / 2; R = W * 0.46; pts = []
    for i in range(48): a = i * TAU / 48; pts.append(pol(cx, cy, R * (1.0 if i % 2 == 0 else 0.8) * (0.96 if i % 4 else 1), a))
    d.line(pts + [pts[0]], fill=255, width=3 * S)
    circ(d, cx, cy, R * 0.74, 2 * S); circ(d, cx, cy, R * 0.44, 2 * S)
    tri = [pol(cx, cy, R * 0.7, math.pi / 2 + i * TAU / 3) for i in range(3)]; d.line(tri + [tri[0]], fill=255, width=3 * S)
    tri = [pol(cx, cy, R * 0.44, -math.pi / 2 + i * TAU / 3) for i in range(3)]; d.line(tri + [tri[0]], fill=255, width=2 * S)
    d.ellipse([cx - R * 0.05, cy - R * 0.2, cx + R * 0.05, cy + R * 0.2], fill=255)
    rng = np.random.default_rng(3)
    for i in range(9):                                          # 갈라진 금
        a = rng.uniform(0, TAU); p = pol(cx, cy, R * 0.46, a); seg = [p]
        for k in range(5): a += rng.uniform(-0.5, 0.5); p = (p[0] + math.cos(a) * R * 0.07, p[1] + math.sin(a) * R * 0.07); seg.append(p)
        d.line(seg, fill=230, width=2 * S)
def cross(d, W, H, S):
    cx = W / 2; d.polygon([(cx, H * 0.02), (cx + W * 0.07, H * 0.3), (cx + W * 0.035, H * 0.98), (cx - W * 0.035, H * 0.98), (cx - W * 0.07, H * 0.3)], fill=255)
    d.polygon([(W * 0.08, H * 0.26), (cx, H * 0.235), (W * 0.92, H * 0.26), (cx, H * 0.285)], fill=255)
    d.polygon([(cx, H * 0.2), (cx + W * 0.16, H * 0.26), (cx, H * 0.32), (cx - W * 0.16, H * 0.26)], outline=255, width=2 * S)
def chain(d, W, H, S):
    cx = W / 2; n = 8; h = H / n
    for i in range(n):
        y = (i + .5) * h
        if i % 2 == 0: d.ellipse([cx - W * 0.26, y - h * 0.62, cx + W * 0.26, y + h * 0.62], outline=255, width=5 * S)
        else: d.rectangle([cx - 3 * S, y - h * 0.62, cx + 3 * S, y + h * 0.62], fill=255)
lineart(384, halo_sig, 'T_HaloSigil'); lineart(384, thorn_sig, 'T_ThornSigil'); lineart((96, 384), cross, 'T_Cross', blur=4, glow=1.5); lineart((64, 256), chain, 'T_Chain', blur=2, glow=0.7)

# ---------- 해방된 악마: 지옥문 문양 / 마검 / 발톱 자국 / 불꽃 시트 ----------
def gate_sig(d, W, H, S):
    cx, cy = W / 2, H / 2; R = W * 0.44; rng = np.random.default_rng(7)
    pts = [pol(cx, cy, R * (1 + rng.uniform(-0.035, 0.035)), i * TAU / 90) for i in range(90)]; d.line(pts + [pts[0]], fill=255, width=6 * S)
    circ(d, cx, cy, R * 0.42, 4 * S); d.ellipse([cx - R * 0.2, cy - R * 0.2, cx + R * 0.2, cy + R * 0.2], fill=255)
    for i in range(6):                                          # 세 갈래 별 (문양은 모델 가슴과 같은 모양)
        a = i * TAU / 6; n = (-math.sin(a), math.cos(a)); p0 = pol(cx, cy, R * 0.42, a); p1 = pol(cx, cy, R * 0.98, a); t = R * 0.05
        d.polygon([(p0[0] + n[0] * t, p0[1] + n[1] * t), p1, (p0[0] - n[0] * t, p0[1] - n[1] * t)], fill=255)
    for i in range(14):                                         # 바깥으로 뻗는 균열
        a = rng.uniform(0, TAU); p = pol(cx, cy, R * 1.0, a); seg = [p]
        for k in range(4): a += rng.uniform(-0.6, 0.6); p = (p[0] + math.cos(a) * R * 0.05, p[1] + math.sin(a) * R * 0.05); seg.append(p)
        d.line(seg, fill=230, width=2 * S)
def blade(d, W, H, S):
    cx = W / 2; d.polygon([(cx - W * 0.2, H * 0.02), (cx + W * 0.2, H * 0.02), (cx + W * 0.17, H * 0.84), (cx, H * 0.99), (cx - W * 0.17, H * 0.84)], fill=120, outline=255, width=3 * S)
    d.rectangle([cx - 2 * S, H * 0.05, cx + 2 * S, H * 0.8], fill=255)
    for y in (0.3, 0.5): d.polygon([(cx + W * 0.19, H * y), (cx + W * 0.42, H * (y - 0.04)), (cx + W * 0.19, H * (y + 0.07))], fill=255); d.polygon([(cx - W * 0.19, H * y), (cx - W * 0.42, H * (y - 0.04)), (cx - W * 0.19, H * (y + 0.07))], fill=255)
def claw(d, W, H, S):
    for k, x0 in enumerate((0.26, 0.5, 0.74)):
        pts = [(W * (x0 + 0.1 * math.sin(t * 2.2 - 0.8)), H * (0.05 + 0.9 * t)) for t in np.linspace(0, 1, 40)]
        for i in range(len(pts) - 1): d.line([pts[i], pts[i + 1]], fill=255, width=max(1, int(S * 11 * math.sin(math.pi * (i + .5) / len(pts)) ** 0.7)))
lineart(384, gate_sig, 'T_GateSigil', blur=7, glow=1.4); lineart((96, 384), blade, 'T_Blade', blur=3, glow=0.9); lineart(256, claw, 'T_ClawMark', blur=5, glow=1.3)
def noise(n, sc, seed):
    r = np.random.default_rng(seed); acc = np.zeros((n, n)); amp = 1; tot = 0
    for o in range(4):
        g = max(2, int(n / sc * 2 ** o)); im = Image.fromarray((r.random((g + 1, g + 1)) * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
        acc += np.asarray(im, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
C = 128; sheet = np.zeros((C * 4, C * 4)); yy, xx = np.mgrid[0:1:C * 1j, -1:1:C * 1j]
for f in range(16):
    t = f / 15; n1 = noise(C, 40, 100 + f // 2); n2 = noise(C, 16, 200 + f)
    v = 1 - yy                                                   # 0 아래 → 1 위
    width = (0.62 - 0.5 * v ** 0.8) * (1 - 0.25 * t) + 0.02
    sway = 0.22 * np.sin(v * 5 + t * 6 + f) * v
    body = np.clip(1 - np.abs(xx - sway) / np.clip(width, 0.02, None), 0, 1)
    a = np.clip(body * 1.5 - (n1 * 0.9 + n2 * 0.5) * (0.35 + 0.9 * v + 0.6 * t), 0, 1) ** 0.8
    a *= np.clip(v * 14, 0, 1) * np.clip((1 - v) * 6, 0, 1) * (1 - t ** 3)
    sheet[(f // 4) * C:(f // 4 + 1) * C, (f % 4) * C:(f % 4 + 1) * C] = a
core = np.clip(sheet * 1.6 - 0.5, 0, 1)[..., None]
rgb = np.ones(sheet.shape + (3,)) * (0.82 + 0.18 * core)
Image.fromarray((np.dstack([rgb, np.clip(sheet * 1.25, 0, 1)[..., None]]) * 255).astype(np.uint8), 'RGBA').save(OUT + 'T_Flame_Sheet.png')
print('ok', sorted(os.listdir(OUT)))
