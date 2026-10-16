# Demo-only caster model: a blocky Minecraft-style rider sitting on a broom (preview only, the mod ignores it).
# Unity frame authored here (+Z forward, +Y up, origin = rider feet as the mod reports them), converted to glTF on export (x negated).
# One clip "fly" (12 s, gentle bob + sway) so the preview never runs out of animation.  usage: python gen_rider.py <out.glb>
import sys, json, struct, math, numpy as np

def Rx(a): c, s = math.cos(a), math.sin(a); return np.array([[1,0,0],[0,c,-s],[0,s,c]])
def Rz(a): c, s = math.cos(a), math.sin(a); return np.array([[c,-s,0],[s,c,0],[0,0,1]])
V, Nn, C, I = [], [], [], []
def box(center, size, col, R=np.eye(3), pivot=None):
    """axis box of `size` centred at `center`, rotated by R about `pivot` (default its centre)"""
    cx = np.array(center, float); hs = np.array(size, float) / 2; pv = np.array(pivot if pivot is not None else center, float)
    faces = [((1,0,0),(0,1,0),(0,0,1)), ((-1,0,0),(0,0,1),(0,1,0)), ((0,1,0),(0,0,1),(1,0,0)),
             ((0,-1,0),(1,0,0),(0,0,1)), ((0,0,1),(1,0,0),(0,1,0)), ((0,0,-1),(0,1,0),(1,0,0))]
    shade = [0.92, 0.78, 1.0, 0.6, 0.86, 0.72]
    for (n, a, b), sh in zip(faces, shade):
        n, a, b = map(np.array, (n, a, b)); base = len(V)
        for sa, sb in ((-1,-1),(1,-1),(1,1),(-1,1)):
            p = cx + hs * n + hs * a * sa + hs * b * sb
            V.append(R @ (p - pv) + pv); Nn.append(R @ n); C.append([*(np.array(col) * sh), 1])
        I.extend([base, base+1, base+2, base, base+2, base+3])

SKIN = (0.93, 0.76, 0.62); HAIR = (0.30, 0.20, 0.13); ROBE = (0.16, 0.20, 0.42); ROBE_L = (0.24, 0.30, 0.58); TRIM = (0.85, 0.70, 0.30)
PANTS = (0.14, 0.13, 0.17); BOOT = (0.25, 0.17, 0.12); WOOD = (0.45, 0.29, 0.16); WOOD_D = (0.33, 0.21, 0.12); STRAW = (0.86, 0.72, 0.40); STRAW_D = (0.70, 0.55, 0.28)
BY = 0.78                                                     # broom height above the feet origin
# broom: handle, binding, bristle bundle (wider toward the tail end)
box((0, BY, 0.18), (0.07, 0.07, 1.55), WOOD)
box((0, BY + 0.005, 0.98), (0.09, 0.09, 0.08), WOOD_D)
box((0, BY, -0.62), (0.13, 0.13, 0.07), TRIM)
for k in range(4):
    z0 = -0.66 - k * 0.1; w = 0.15 + k * 0.05
    box((0, BY - 0.01 * k, z0 - 0.05), (w, w * 0.85, 0.11), STRAW if k % 2 == 0 else STRAW_D)
box((0, BY - 0.04, -1.08), (0.3, 0.22, 0.06), STRAW_D)
# rider (Minecraft proportions), sitting on the handle, leaning into the flight
hip = np.array([0, BY + 0.06, 0.02]); lean = Rx(0.32)        # +X rotation tips +Y toward +Z (leaning forward)
box(hip + [0, 0.375, 0], (0.5, 0.75, 0.25), ROBE, lean, pivot=hip)
box(hip + [0, 0.05, 0], (0.52, 0.1, 0.27), TRIM, lean, pivot=hip)
neck = hip + lean @ np.array([0, 0.75, 0])
box(neck + [0, 0.25, 0.02], (0.5, 0.5, 0.5), SKIN)
box(neck + [0, 0.44, -0.02], (0.53, 0.14, 0.54), HAIR)
box(neck + [0, 0.27, -0.235], (0.53, 0.42, 0.08), HAIR)
for s in (-1, 1):
    sh = neck + [s * 0.375, -0.05, 0]
    arm = Rx(-1.15) @ Rz(-s * 0.2)                             # arms reach forward-down to the handle
    box(sh + [0, -0.3, 0], (0.25, 0.75, 0.25), ROBE_L, arm, pivot=sh)
    box(sh + [0, -0.62, 0], (0.26, 0.12, 0.26), SKIN, arm, pivot=sh)
    th = hip + [s * 0.13, -0.02, 0]
    leg = Rx(-0.45) @ Rz(s * 0.3)                            # legs hang astride, knees slightly forward
    box(th + [0, -0.33, 0], (0.25, 0.66, 0.25), PANTS, leg, pivot=th)
    box(th + [0, -0.62, 0.03], (0.27, 0.16, 0.3), BOOT, leg, pivot=th)

V = np.array(V, np.float32); Nn = np.array(Nn, np.float32); C = np.array(C, np.float32); I = np.array(I, np.uint16)
FX = np.array([-1, 1, 1], np.float32); V *= FX; Nn *= FX                 # Unity → glTF
# clip: bob (translation) + slight roll, sampled
T_END = 12.0; ts = np.arange(0, T_END + 1e-6, 0.1, dtype=np.float32)
tr = np.array([[0, 0.04 * math.sin(t * 2 * math.pi / 1.6), 0] for t in ts], np.float32)
def q_axis(ax, a): s = math.sin(a / 2); return [ax[0]*s, ax[1]*s, ax[2]*s, math.cos(a / 2)]
rot = np.array([q_axis((0, 0, 1), 0.04 * math.sin(t * 2 * math.pi / 2.3)) for t in ts], np.float32)

bin_ = bytearray(); views = []; accs = []
def push(arr, ctype, typ, target=None, mm=False):
    off = len(bin_); b = arr.tobytes(); bin_.extend(b)
    while len(bin_) % 4: bin_.append(0)
    v = {"buffer": 0, "byteOffset": off, "byteLength": len(b)}
    if target: v["target"] = target
    views.append(v); a = {"bufferView": len(views) - 1, "componentType": ctype, "count": int(arr.shape[0]), "type": typ}
    if mm: a["min"] = arr.min(axis=0).tolist() if arr.ndim > 1 else [float(arr.min())]; a["max"] = arr.max(axis=0).tolist() if arr.ndim > 1 else [float(arr.max())]
    accs.append(a); return len(accs) - 1
pa = push(V, 5126, "VEC3", 34962, True); na = push(Nn, 5126, "VEC3", 34962); ca = push(C, 5126, "VEC4", 34962); ia = push(I, 5123, "SCALAR", 34963)
ta = push(ts, 5126, "SCALAR", mm=True); tra = push(tr, 5126, "VEC3"); ra = push(rot, 5126, "VEC4")
J = {"asset": {"version": "2.0", "generator": "gen_rider.py"}, "scene": 0, "scenes": [{"nodes": [0]}],
     "nodes": [{"name": "rider", "mesh": 0}],
     "meshes": [{"name": "rider", "primitives": [{"attributes": {"POSITION": pa, "NORMAL": na, "COLOR_0": ca}, "indices": ia, "material": 0}]}],
     "materials": [{"name": "body", "pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 1], "metallicFactor": 0, "roughnessFactor": 0.8}, "doubleSided": True}],
     "animations": [{"name": "fly", "samplers": [{"input": ta, "output": tra, "interpolation": "LINEAR"}, {"input": ta, "output": ra, "interpolation": "LINEAR"}],
                     "channels": [{"sampler": 0, "target": {"node": 0, "path": "translation"}}, {"sampler": 1, "target": {"node": 0, "path": "rotation"}}]}],
     "buffers": [{"byteLength": len(bin_)}], "bufferViews": views, "accessors": accs}
js = json.dumps(J).encode(); js += b" " * ((4 - len(js) % 4) % 4)
out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(bin_)) + struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(bin_), 0x004E4942) + bytes(bin_)
open(sys.argv[1], "wb").write(out); print("glb", len(out), "bytes, tris", len(I) // 3)
