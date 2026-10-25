# Demo-only caster model per broom: blocky rider + the REAL broom bbmodel (same parsing as chacademy-motion BroomModel.java),
# placed like BroomRenderer (seat p_pilot at feet + 0.66, model front -Z = flight direction). Prints the bristle-tip (Unity local, feet origin).
# usage: python gen_broom_glb.py <broom.bbmodel> <out.glb>
import sys, json, struct, math, base64, numpy as np

def rx(d): a = math.radians(d); c, s = math.cos(a), math.sin(a); return np.array([[1,0,0,0],[0,c,-s,0],[0,s,c,0],[0,0,0,1]])
def ry(d): a = math.radians(d); c, s = math.cos(a), math.sin(a); return np.array([[c,0,s,0],[0,1,0,0],[-s,0,c,0],[0,0,0,1]])
def rz(d): a = math.radians(d); c, s = math.cos(a), math.sin(a); return np.array([[c,-s,0,0],[s,c,0,0],[0,0,1,0],[0,0,0,1]])
def tr(x, y, z): m = np.eye(4); m[:3, 3] = (x, y, z); return m
def rot_around(o, r):
    if not any(r): return np.eye(4)
    return tr(*o) @ (rz(r[2]) @ ry(r[1]) @ rx(r[0])) @ tr(-o[0], -o[1], -o[2])
def ap(m, p): return (m @ np.array([p[0], p[1], p[2], 1.0]))[:3]
def corners(f, a, b):
    return {"north": [(b[0],b[1],a[2]),(a[0],b[1],a[2]),(a[0],a[1],a[2]),(b[0],a[1],a[2])],
            "south": [(a[0],b[1],b[2]),(b[0],b[1],b[2]),(b[0],a[1],b[2]),(a[0],a[1],b[2])],
            "east":  [(b[0],b[1],b[2]),(b[0],b[1],a[2]),(b[0],a[1],a[2]),(b[0],a[1],b[2])],
            "west":  [(a[0],b[1],a[2]),(a[0],b[1],b[2]),(a[0],a[1],b[2]),(a[0],a[1],a[2])],
            "up":    [(a[0],b[1],a[2]),(b[0],b[1],a[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])],
            "down":  [(a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],a[1],a[2]),(a[0],a[1],a[2])]}.get(f)

def parse_bb(path):
    d = json.load(open(path, encoding="utf-8"))
    texW, texH = d.get("resolution", {}).get("width", 16), d.get("resolution", {}).get("height", 16); png = None
    for t in d.get("textures", []):
        s = t.get("source", "")
        if s.startswith("data:image/png;base64,"):
            png = base64.b64decode(s.split(",", 1)[1]); texW = t.get("uv_width", texW); texH = t.get("uv_height", texH); break
    els = {e["uuid"]: e for e in d["elements"]}; quads = []; seat = [0, 12, 0]; seen = set()
    def cube(el, M):
        if el.get("type", "cube") != "cube" or el.get("visibility") is False or el.get("export") is False: return
        fr, to = list(map(float, el["from"])), list(map(float, el["to"])); inf = el.get("inflate", 0)
        if inf:
            for k in range(3): sg = 1 if to[k] >= fr[k] else -1; fr[k] -= inf * sg; to[k] += inf * sg
        m = M @ rot_around(el.get("origin", [0,0,0]), el.get("rotation", [0,0,0]))
        for fn, face in el.get("faces", {}).items():
            if face.get("texture") is None: continue
            c = corners(fn, fr, to); uv = face.get("uv")
            if c is None or not uv or len(uv) < 4: continue
            u1, v1, u2, v2 = uv[0] / texW, uv[1] / texH, uv[2] / texW, uv[3] / texH
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]; turns = (int(round(face.get("rotation", 0))) // 90) & 3
            pos, tuv = [], []
            for corner in (1, 0, 3, 2):
                pos.append(ap(m, c[corner])); tuv.append(uvs[(corner - turns + 4) & 3])
            quads.append((pos, tuv))
    def walk(node, M):
        nonlocal seat
        if isinstance(node, str):
            seen.add(node)
            if node in els: cube(els[node], M)
            return
        if node.get("export") is False: return
        o = node.get("origin", [0,0,0]); m = M @ rot_around(o, node.get("rotation", [0,0,0]))
        if node.get("name") == "p_pilot": seat = list(ap(M, o))
        for ch in node.get("children", []): walk(ch, m)
    for n in d.get("outliner", []): walk(n, np.eye(4))
    for u, e in els.items():
        if u not in seen: cube(e, np.eye(4))
    return quads, seat, png

SEAT_Y = 0.66
def to_unity(p, seat):  # BroomRenderer: feet + SEAT_Y + YP(180)·(p - seat)/16, MC → Unity (x mirrored)
    return np.array([(p[0] - seat[0]) / 16, (p[1] - seat[1]) / 16 + SEAT_Y, -(p[2] - seat[2]) / 16])

TIP_BAND = 4.0
def tip_of(quads, seat):  # same rule as BroomTrails.tipOf: x,y = centre of all vertices in the back 4 px band, z = back end - 1 px
    maxz = max(p[2] for q, _ in quads for p in q)
    pts = [p for q, _ in quads for p in q if p[2] >= maxz - TIP_BAND]
    t = np.mean(pts, axis=0); return to_unity((t[0], t[1], maxz - 1.0), seat)

# ---------------- rider (vertex colors), sitting on the handle at SEAT_Y ----------------
RV, RN, RC, RI = [], [], [], []
def Rx(a): c, s = math.cos(a), math.sin(a); return np.array([[1,0,0],[0,c,-s],[0,s,c]])
def Rz(a): c, s = math.cos(a), math.sin(a); return np.array([[c,-s,0],[s,c,0],[0,0,1]])
def box(center, size, col, R=np.eye(3), pivot=None):
    cx = np.array(center, float); hs = np.array(size, float) / 2; pv = np.array(pivot if pivot is not None else center, float)
    faces = [((1,0,0),(0,1,0),(0,0,1)), ((-1,0,0),(0,0,1),(0,1,0)), ((0,1,0),(0,0,1),(1,0,0)), ((0,-1,0),(1,0,0),(0,0,1)), ((0,0,1),(1,0,0),(0,1,0)), ((0,0,-1),(0,1,0),(1,0,0))]
    for (n, a, b), sh in zip(faces, [0.92, 0.78, 1.0, 0.6, 0.86, 0.72]):
        n, a, b = map(np.array, (n, a, b)); base = len(RV)
        for sa, sb in ((-1,-1),(1,-1),(1,1),(-1,1)):
            p = cx + hs * n + hs * a * sa + hs * b * sb; RV.append(R @ (p - pv) + pv); RN.append(R @ n); RC.append([*(np.array(col) * sh), 1])
        RI.extend([base, base+1, base+2, base, base+2, base+3])
SKIN = (0.93, 0.76, 0.62); HAIR = (0.30, 0.20, 0.13); ROBE = (0.16, 0.20, 0.42); ROBE_L = (0.24, 0.30, 0.58); TRIM = (0.85, 0.70, 0.30); PANTS = (0.14, 0.13, 0.17); BOOT = (0.25, 0.17, 0.12)
hip = np.array([0, SEAT_Y + 0.08, 0.0]); lean = Rx(0.32)
box(hip + [0, 0.375, 0], (0.5, 0.75, 0.25), ROBE, lean, pivot=hip); box(hip + [0, 0.05, 0], (0.52, 0.1, 0.27), TRIM, lean, pivot=hip)
neck = hip + lean @ np.array([0, 0.75, 0])
box(neck + [0, 0.25, 0.02], (0.5, 0.5, 0.5), SKIN); box(neck + [0, 0.44, -0.02], (0.53, 0.14, 0.54), HAIR); box(neck + [0, 0.27, -0.235], (0.53, 0.42, 0.08), HAIR)
for s in (-1, 1):
    sh = neck + [s * 0.375, -0.05, 0]; arm = Rx(-1.15) @ Rz(-s * 0.2)
    box(sh + [0, -0.3, 0], (0.25, 0.75, 0.25), ROBE_L, arm, pivot=sh); box(sh + [0, -0.62, 0], (0.26, 0.12, 0.26), SKIN, arm, pivot=sh)
    th = hip + [s * 0.13, -0.02, 0]; leg = Rx(-0.45) @ Rz(s * 0.3)
    box(th + [0, -0.33, 0], (0.25, 0.66, 0.25), PANTS, leg, pivot=th); box(th + [0, -0.62, 0.03], (0.27, 0.16, 0.3), BOOT, leg, pivot=th)

def export(quads, seat, png, out):
    FX = np.array([-1, 1, 1], np.float32)
    BV, BN, BU, BI = [], [], [], []
    for pos, uv in quads:
        P = [to_unity(p, seat) for p in pos]; n = np.cross(P[1] - P[0], P[2] - P[1]); L = np.linalg.norm(n)
        if L < 1e-9: continue
        base = len(BV)
        for p, t in zip(P, uv): BV.append(p); BN.append(n / L); BU.append(t)
        BI.extend([base, base+1, base+2, base, base+2, base+3])
    bin_ = bytearray(); views = []; accs = []
    def raw(b):
        off = len(bin_); bin_.extend(b)
        while len(bin_) % 4: bin_.append(0)
        views.append({"buffer": 0, "byteOffset": off, "byteLength": len(b)}); return len(views) - 1
    def acc(arr, ct, ty, mm=False):
        a = {"bufferView": raw(arr.tobytes()), "componentType": ct, "count": int(arr.shape[0]), "type": ty}
        if mm: a["min"] = arr.min(axis=0).tolist() if arr.ndim > 1 else [float(arr.min())]; a["max"] = arr.max(axis=0).tolist() if arr.ndim > 1 else [float(arr.max())]
        accs.append(a); return len(accs) - 1
    rp = acc(np.array(RV, np.float32) * FX, 5126, "VEC3", True); rn = acc(np.array(RN, np.float32) * FX, 5126, "VEC3"); rc = acc(np.array(RC, np.float32), 5126, "VEC4"); ri = acc(np.array(RI, np.uint16), 5123, "SCALAR")
    bp = acc(np.array(BV, np.float32) * FX, 5126, "VEC3", True); bn = acc(np.array(BN, np.float32) * FX, 5126, "VEC3"); bu = acc(np.array(BU, np.float32), 5126, "VEC2"); bi = acc(np.array(BI, np.uint32 if len(BV) > 65000 else np.uint16), 5125 if len(BV) > 65000 else 5123, "SCALAR")
    ts = np.arange(0, 20.01, 0.1, dtype=np.float32)
    trn = np.array([[0, 0.03 * math.sin(t * 2 * math.pi / 1.6), 0] for t in ts], np.float32)
    ta = acc(ts, 5126, "SCALAR", True); tra = acc(trn, 5126, "VEC3")
    J = {"asset": {"version": "2.0"}, "scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"name": "rider", "mesh": 0}],
         "meshes": [{"primitives": [{"attributes": {"POSITION": rp, "NORMAL": rn, "COLOR_0": rc}, "indices": ri, "material": 0},
                                    {"attributes": {"POSITION": bp, "NORMAL": bn, "TEXCOORD_0": bu}, "indices": bi, "material": 1}]}],
         "materials": [{"pbrMetallicRoughness": {"baseColorFactor": [1,1,1,1], "metallicFactor": 0}, "doubleSided": True},
                       {"pbrMetallicRoughness": {"baseColorFactor": [1,1,1,1], "metallicFactor": 0, "baseColorTexture": {"index": 0}}, "doubleSided": True, "alphaMode": "MASK"}],
         "images": [{"mimeType": "image/png", "bufferView": raw(png)}], "textures": [{"source": 0}],
         "animations": [{"name": "fly", "samplers": [{"input": ta, "output": tra, "interpolation": "LINEAR"}], "channels": [{"sampler": 0, "target": {"node": 0, "path": "translation"}}]}],
         "buffers": [{"byteLength": len(bin_)}], "bufferViews": views, "accessors": accs}
    js = json.dumps(J).encode(); js += b" " * ((4 - len(js) % 4) % 4)
    open(out, "wb").write(struct.pack("<III", 0x46546C67, 2, 28 + len(js) + len(bin_)) + struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(bin_), 0x004E4942) + bytes(bin_))

if __name__ == "__main__":
    q, seat, png = parse_bb(sys.argv[1]); export(q, seat, png, sys.argv[2])
    print(json.dumps({"tip": [round(float(v), 3) for v in tip_of(q, seat)], "quads": len(q), "seat": [float(v) for v in seat]}))
