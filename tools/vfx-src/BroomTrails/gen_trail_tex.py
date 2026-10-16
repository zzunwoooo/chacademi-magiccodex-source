# Broom trail textures (painterly, neutral white so colour comes from colorOverLifetime + material tint).
# usage: python gen_trail_tex.py <outdir> [legend]
import sys, os, numpy as np
from PIL import Image
OUT = sys.argv[1]; LEGEND = len(sys.argv) > 2 and sys.argv[2] == "legend"
os.makedirs(OUT, exist_ok=True)

def smooth_noise(w, h, sx, sy, octaves=4, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(octaves):
        gx, gy = max(2, int(w / sx * 2**o)), max(2, int(h / sy * 2**o))
        g = Image.fromarray((r.random((gy + 1, gx + 1)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        acc += np.asarray(g, float) / 255 * amp; tot += amp; amp *= 0.5
    return acc / tot
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def save(rgb, a, name):
    Image.fromarray((np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]]) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))
WHITE = np.array([1.0, 1.0, 1.0]); TINTED = np.array([0.82, 0.9, 1.0])

# ---- T_TrailStreak 256x32: horizontal streak, head (u=1) bright and sharp, tail (u=0) thins out; brush grain along the flow
W, H = 256, 32
x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]; dy = np.abs(y)
grain = smooth_noise(W, H, 50, 6, 3, 7)
prof = (x ** 0.7) * (1 - ss(0.9, 1.0, x)) + 0.02
core = np.exp(-(dy / (0.16 * prof + 0.02)) ** 2)
halo = np.exp(-(dy / (0.6 * prof + 0.02)) ** 2) * 0.35 * prof
a = np.clip(core * (0.75 + 0.4 * grain) + halo, 0, 1) * ss(0.0, 0.12, x)
heat = np.clip(core, 0, 1)[..., None]
save(TINTED * (1 - heat) + WHITE * heat, a, "T_TrailStreak.png")

# ---- T_Dot 64
S = 64
yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy)
dd = np.exp(-(r / 0.28) ** 2) + (1 - ss(0.06, 0.14, r)) * 0.6
save(np.ones((S, S, 3)), np.clip(dd, 0, 1) * (1 - ss(0.85, 1, r)), "T_Dot.png")

if LEGEND:
    # ---- T_AuroraWisp 256x64: soft wavy band, frayed brush edges, both ends fade (aurora ribbon segment)
    W, H = 256, 64
    x = np.linspace(0, 1, W)[None, :]; y = np.linspace(-1, 1, H)[:, None]
    n1 = smooth_noise(W, H, 70, 20, 3, 21); n2 = smooth_noise(W, H, 40, 4, 3, 22)
    yc = y + 0.25 * np.sin(x * np.pi * 2.0) + (n1 - 0.5) * 0.3
    band = np.exp(-(np.abs(yc) / (0.42 * np.sin(np.pi * x) ** 0.6 + 0.04)) ** 2)
    a = np.clip(band * (0.55 + 0.6 * n2), 0, 1) * ss(0.0, 0.25, x) * (1 - ss(0.8, 1.0, x))
    hot = np.clip(band ** 3, 0, 1)[..., None]
    save(TINTED * (1 - hot) + WHITE * hot, a, "T_AuroraWisp.png")
    # ---- T_Star4 128: 4-point sparkle, thin crisp spikes + small soft core
    S = 128
    yy, xx = np.mgrid[-1:1:S*1j, -1:1:S*1j]; r = np.hypot(xx, yy) + 1e-6; ax, ay = np.abs(xx), np.abs(yy)
    spike = np.exp(-ay / (0.025 * (1 - np.clip(ax, 0, 1)) + 1e-3)) * (1 - ax) ** 2.2 + np.exp(-ax / (0.025 * (1 - np.clip(ay, 0, 1)) + 1e-3)) * (1 - ay) ** 2.2
    glow = np.exp(-(r / 0.13) ** 2) * 0.9 + np.exp(-(r / 0.4) ** 2) * 0.25
    save(np.ones((S, S, 3)), np.clip(spike + glow, 0, 1) * (1 - ss(0.9, 1, r)), "T_Star4.png")
    # ---- T_Glow 128
    g = np.exp(-(r / 0.35) ** 2) * 0.85 + np.exp(-(r / 0.75) ** 2) * 0.25; g *= 1 - ss(0.9, 1.0, r)
    save(np.ones((S, S, 3)), g, "T_Glow.png")
print("ok", OUT)
