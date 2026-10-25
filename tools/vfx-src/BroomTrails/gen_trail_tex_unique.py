# 유일 등급 궤적 텍스처: 에너지 고리, 소닉붐 고리, 홀로그램 물결, 홀로 문양 플립북
import sys, os, numpy as np
from PIL import Image
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def save(rgb, a, name): Image.fromarray((np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]]) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))
S = 256; yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy); th = np.arctan2(yy, xx)
# energy ring: crisp bright band at r=0.8, soft glow both sides, faint swirl variation
band = np.exp(-((r - 0.8) / 0.035) ** 2) + 0.4 * np.exp(-((r - 0.8) / 0.12) ** 2)
band *= 0.8 + 0.2 * np.sin(th * 6) ** 2
a = np.clip(band, 0, 1) * (1 - ss(0.96, 1.0, r)); hot = np.clip(np.exp(-((r - 0.8) / 0.03) ** 2), 0, 1)[..., None]
save(np.array([0.85, 0.92, 1.0]) * (1 - hot) + hot, a, "T_Ring.png")
# shock ring: thick bright leading edge (outer), fading inward trail, like a pressure wave
edge = np.exp(-((r - 0.84) / 0.04) ** 2) + np.where(r < 0.84, np.exp(-((0.84 - r) / 0.3) ** 2) * 0.45, 0)
a = np.clip(edge, 0, 1) * (1 - ss(0.95, 1.0, r)); hot = np.clip(np.exp(-((r - 0.84) / 0.035) ** 2), 0, 1)[..., None]
save(np.array([0.9, 0.95, 1.0]) * (1 - hot) + hot, a, "T_ShockRing.png")
# holo wave 256x64: sine crest made of scanlines (horizontal dashes), grid ticks, ends fade
W, H = 256, 64; x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
env = ss(0.0, 0.15, x) * (1 - ss(0.8, 1.0, x))
yc = 0.5 * np.sin(2 * np.pi * 1.5 * x); d = np.abs(y - yc)
crest = np.exp(-(d / 0.09) ** 2) + 0.3 * np.exp(-(d / 0.35) ** 2)
scan = 0.55 + 0.45 * (np.arange(H)[:, None] % 4 < 2)
ticks = ((np.arange(W)[None, :] % 32) < 2) * 0.35 * np.exp(-(d / 0.5) ** 2)
a = np.clip((crest * scan + ticks) * env, 0, 1); hot = np.clip(np.exp(-(d / 0.06) ** 2), 0, 1)[..., None]
save(np.array([0.8, 0.95, 1.0]) * (1 - hot) + hot, a, "T_HoloWave.png")
# holo glyphs 4-frame flipbook (64 each): square bracket, hex, plus, triangle outlines
F = 64; frames = []
gy, gx = np.mgrid[-1:1:F*1j, -1:1:F*1j]
def line_mask(dist, w=0.07): return np.exp(-(dist / w) ** 2)
sq = np.maximum(np.abs(gx), np.abs(gy)); f0 = line_mask(np.abs(sq - 0.6)) * ((np.abs(gx) > 0.25) | (np.abs(gy) > 0.25) | True) * (1 - ((np.abs(gx) < 0.3) & (np.abs(gy) > 0.4)))
ang = np.arctan2(gy, gx); hexr = np.cos(np.pi / 6) / np.cos((ang % (np.pi / 3)) - np.pi / 6) * 0.62; f1 = line_mask(np.hypot(gx, gy) - hexr)
f2 = np.maximum(line_mask(np.abs(gx)) * (np.abs(gy) < 0.6), line_mask(np.abs(gy)) * (np.abs(gx) < 0.6))
tri = np.maximum.reduce([gy * 1.0 - 0.45, -0.87 * gx - 0.5 * gy - 0.45, 0.87 * gx - 0.5 * gy - 0.45]); f3 = line_mask(tri, 0.06)
for f in (f0, f1, f2, f3):
    a = np.clip(f + 0.25 * np.exp(-(np.hypot(gx, gy) / 0.5) ** 2), 0, 1) * (1 - ss(0.9, 1, np.maximum(abs(gx), abs(gy))))
    frames.append(np.dstack([np.ones((F, F, 3)), a]))
Image.fromarray((np.concatenate(frames, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "T_HoloGlyph.png"))
print("ok")
