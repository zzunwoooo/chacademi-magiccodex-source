# 재의 그림자 VFX 그룹 B 텍스처. usage: python gen_ash_b.py tex_b
# 1) 하베스트 공용 텍스처(gen_pumpkin.py)를 같은 이름으로 만든 뒤  2) 재 테마로 바꾸거나 새로 추가한다.
import sys, os, subprocess, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = (sys.argv[1] if len(sys.argv) > 1 else "tex_b") + "/"; os.makedirs(OUT, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
subprocess.run([sys.executable, os.path.join(HERE, "../pumpkin_reaper_vfx/gen_pumpkin.py"), OUT], check=True)

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
EMBER = [(1.0,(1,0.97,0.88)),(0.8,(1,0.82,0.5)),(0.6,(1,0.52,0.16)),(0.4,(0.92,0.3,0.08)),(0.2,(0.6,0.12,0.06)),(0.0,(0.2,0.04,0.04))]
GOLD = [(1.0,(1,1,0.96)),(0.75,(1,0.94,0.7)),(0.5,(1,0.84,0.45)),(0.25,(0.85,0.62,0.25)),(0.0,(0.4,0.26,0.1))]

# ---- 재 안개 플립북 4x4 (128): 숯검정, 아래쪽이 잿불로 은은히 밝음 (하베스트 보라 안개 대체)
C, G = 128, 4
cy_, cx_ = np.mgrid[-1:1:C*1j, -1:1:C*1j]
nA = smooth_noise(C, C, 36, 36, 4, 71); nB = smooth_noise(C, C, 16, 16, 3, 72); stk = smooth_noise(C, C, 7, 45, 3, 73)
def ash_mist(t):
    R = 0.6 + 0.32 * t ** 0.5; wx = cx_ + (nB - 0.5) * 0.6; wy = cy_ + (nA - 0.5) * 0.6
    body = 1 - np.hypot(wx, wy) / R + (stk - 0.5) * 0.25 - t ** 1.5 * 0.8 * (0.5 + nA)
    a = ss(0.0, 0.5, body) * 0.65 * (1 - 0.3 * t); lit = ss(0.1, 0.9, wy * 0.8 + 0.1)[..., None] * (1 - t) * 0.5
    rgb = np.array([0.07, 0.06, 0.06]) * (1 - lit) + np.array([0.42, 0.17, 0.07]) * lit
    return rgb, a * (1 - ss(0.88, 1.0, np.hypot(cx_, cy_)))
rgbS = np.zeros((C*G, C*G, 3)); aS = np.zeros((C*G, C*G))
for i in range(G*G):
    r, c = divmod(i, G); rgb, a = ash_mist(i / (G*G - 1)); rgbS[r*C:(r+1)*C, c*C:(c+1)*C] = rgb; aS[r*C:(r+1)*C, c*C:(c+1)*C] = a
save(rgbS, aS, "T_Mist_Sheet.png")

# ---- 재 조각(flake) 2x2 아틀라스 128: 회백색 불규칙 조각, 가장자리 잿불
F = 64; atl_rgb = np.zeros((F*2, F*2, 3)); atl_a = np.zeros((F*2, F*2))
for k in range(4):
    rng = np.random.default_rng(80 + k); n = rng.integers(5, 8); ang = np.sort(rng.random(n) * 2 * np.pi); rad = 0.45 + rng.random(n) * 0.5
    im = Image.new("L", (F*4, F*4), 0); d = ImageDraw.Draw(im)
    d.polygon([(F*2 + np.cos(a) * r * F*1.8, F*2 + np.sin(a) * r * F*1.2) for a, r in zip(ang, rad)], fill=255)
    m = np.asarray(im.filter(ImageFilter.GaussianBlur(3)).resize((F, F), Image.LANCZOS), float) / 255
    edge = np.clip(m * (1 - m) * 4, 0, 1)
    grain = smooth_noise(F, F, 6, 6, 3, 90 + k)
    body = np.array([0.62, 0.6, 0.58]) * (0.6 + 0.5 * grain[..., None])
    rgb = body * (1 - edge[..., None]) + np.array([1.0, 0.45, 0.12]) * edge[..., None]
    r, c = divmod(k, 2); atl_rgb[r*F:(r+1)*F, c*F:(c+1)*F] = rgb; atl_a[r*F:(r+1)*F, c*F:(c+1)*F] = ss(0.15, 0.6, m)
save(atl_rgb, atl_a, "T_AshFlake.png")

# ---- 금빛 육각 막 (빛의 수호) 256
S = 256; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; rr = np.hypot(xx, yy) + 1e-6
def hexdist(X, Y):
    X, Y = np.abs(X), np.abs(Y); return np.maximum(X * 0.5 + Y * np.sqrt(3) / 2, X)
sc = 8; gx = xx * sc; gy = yy * sc
col = np.round(gx / 1.5); cx2 = col * 1.5; cy2 = np.round((gy - (col % 2) * np.sqrt(3) / 2) / np.sqrt(3)) * np.sqrt(3) + (col % 2) * np.sqrt(3) / 2
hd = hexdist(gx - cx2, gy - cy2); edge = np.exp(-((hd - 0.92) / 0.06) ** 2); fres = ss(0.4, 1.0, rr) ** 1.5
a = np.clip(edge * (0.35 + 0.65 * fres) + 0.16 * fres + 0.05, 0, 1) * (1 - ss(0.96, 1, rr))
save(ramp(0.5 + 0.5 * np.clip(edge + fres * 0.5, 0, 1), GOLD), a, "T_HexGold.png")

# ---- 잿불 균열선 (세로 64x512: V 아래 0 → 위 1 = 길이 방향): 하얀 핵 + 주황 빛 + 지그재그
W, H = 64, 512; fy, fx = np.mgrid[0:1:H*1j, -1:1:W*1j]
wig = (smooth_noise(1, H, 1, 18, 4, 101)[:, :1] - 0.5) * 0.9 + (smooth_noise(1, H, 1, 4, 2, 102)[:, :1] - 0.5) * 0.35
taper = np.clip(1 - fy ** 2.2, 0, 1) * ss(0.0, 0.04, fy)
dxx = np.abs(fx - wig) / (0.09 * taper + 0.02)
core = np.exp(-dxx ** 2); glow = np.exp(-(dxx / 4.5) ** 2) * 0.55 * taper
br = smooth_noise(W, H, 8, 30, 3, 103)
branch = np.exp(-(np.abs(fx - wig - (br - 0.5) * 1.4) / 0.05) ** 2) * ss(0.62, 0.8, br) * taper * 0.7
hot = np.clip(core + branch, 0, 1)
save(ramp(0.3 + 0.7 * np.clip(hot + glow * 0.5, 0, 1), EMBER), np.clip(hot + glow, 0, 1), "T_Fissure.png")
# ---- 그을음 띠 (64x512, 알파 블렌드용 어두운 바닥 자국)
scorch = np.exp(-(np.abs(fx - wig) / (0.55 * taper + 0.05)) ** 2) * (0.6 + 0.4 * smooth_noise(W, H, 10, 20, 3, 104))
save(np.zeros((H, W, 3)) + np.array([0.06, 0.04, 0.04]), np.clip(scorch * 0.85, 0, 1) * ss(0.0, 0.05, fy), "T_Scorch.png")

# ---- 촛불 고리 조각용 작은 촛불꽃 (64x128 세로, 바닥 = 심지)
CW_, CH_ = 64, 128; qy, qx = np.mgrid[0:1:CH_*1j, -1:1:CW_*1j]
prof = 0.55 * np.sin(np.pi * np.clip(1 - qy, 0, 1) ** 0.8) ** 0.8 * (1 - qy * 0.3)
d2 = np.abs(qx) / (prof + 1e-3) + (1 - np.clip((1 - qy) / 0.15, 0, 1)) * 0.0
heat = np.clip(1 - d2, 0, 1) * ss(0.0, 0.1, 1 - qy)
halo = np.exp(-(np.hypot(qx / 0.9, (qy - 0.6) / 0.55)) ** 2) * 0.35
save(ramp(0.25 + 0.75 * heat, EMBER), np.clip(ss(0.0, 0.3, heat) + halo, 0, 1), "T_CandleFlame.png")
print("ash-b ok")
# ---- 금빛 줄기 (빛 장치용 스파크·기둥·빛줄기; FireStreak 의 주황 대신 흰~금)
W2, H2 = 256, 32; x = np.linspace(0, 1, W2)[None, :]; y = np.linspace(-1, 1, H2)[:, None]; dy = np.abs(y)
taper = x ** 1.6 * (1 - ss(0.92, 1.0, x))
core = np.exp(-(dy / (0.09 * taper + 0.01)) ** 2); halo = np.exp(-(dy / (0.5 * taper + 1e-3)) ** 2) * 0.45 * taper
save(ramp(0.45 + 0.55 * np.clip(core + halo * 0.5, 0, 1), GOLD), np.clip(core + halo, 0, 1), "T_GoldStreak.png")
print("gold ok")
