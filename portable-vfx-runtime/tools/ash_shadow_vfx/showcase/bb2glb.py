"""Blockbench bbmodel(들) → glb 하나 (미리보기 캐릭터용). 각 모델 = 루트 노드 하나, 본 = 노드, 큐브 = 그 본의 메시.
클립 = 여러 모델의 애니를 구간별로 이어 붙인 것 (다른 모델은 scale 0 으로 숨김). 30fps 로 구워 LINEAR.
좌표: bb(px, 앞 = -z) → glTF(m, 앞 = +z): C = diag(1,1,-1)/16. 변환 X → D X D (D = diag(1,1,-1))."""
import json, base64, struct, math, sys
import numpy as np

def rotm(r):
    x, y, z = [math.radians(a) for a in r]
    Rx = np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]])
    Ry = np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]])
    Rz = np.array([[math.cos(z),-math.sin(z),0],[math.sin(z),math.cos(z),0],[0,0,1]])
    return Rz @ Ry @ Rx

def kv(k): d = k['data_points'][0]; return [float(d['x']), float(d['y']), float(d['z'])]
def sample(keys, t, dflt):
    if not keys: return dflt
    n = len(keys)
    if t <= keys[0]['time']: return kv(keys[0])
    if t >= keys[-1]['time']: return kv(keys[-1])
    for i in range(n - 1):
        a, b = keys[i], keys[i + 1]
        if t <= b['time']:
            f = (t - a['time']) / max(1e-6, b['time'] - a['time']); A, B = kv(a), kv(b)
            if a.get('interpolation') == 'catmullrom' or b.get('interpolation') == 'catmullrom':
                P = kv(keys[max(0, i - 1)]); N = kv(keys[min(n - 1, i + 2)]); f2 = f * f; f3 = f2 * f
                return [0.5 * ((2 * A[j]) + (-P[j] + B[j]) * f + (2 * P[j] - 5 * A[j] + 4 * B[j] - N[j]) * f2 + (-P[j] + 3 * A[j] - 3 * B[j] + N[j]) * f3) for j in range(3)]
            return [A[j] + (B[j] - A[j]) * f for j in range(3)]
    return kv(keys[-1])

D = np.diag([1., 1., -1.]); CS = 1 / 16.
def quat(R):
    m = R; tr = m[0,0] + m[1,1] + m[2,2]
    if tr > 0:
        s = math.sqrt(tr + 1) * 2; w = 0.25 * s; x = (m[2,1] - m[1,2]) / s; y = (m[0,2] - m[2,0]) / s; z = (m[1,0] - m[0,1]) / s
    elif m[0,0] > m[1,1] and m[0,0] > m[2,2]:
        s = math.sqrt(1 + m[0,0] - m[1,1] - m[2,2]) * 2; w = (m[2,1] - m[1,2]) / s; x = 0.25 * s; y = (m[0,1] + m[1,0]) / s; z = (m[0,2] + m[2,0]) / s
    elif m[1,1] > m[2,2]:
        s = math.sqrt(1 + m[1,1] - m[0,0] - m[2,2]) * 2; w = (m[0,2] - m[2,0]) / s; x = (m[0,1] + m[1,0]) / s; y = 0.25 * s; z = (m[1,2] + m[2,1]) / s
    else:
        s = math.sqrt(1 + m[2,2] - m[0,0] - m[1,1]) * 2; w = (m[1,0] - m[0,1]) / s; x = (m[0,2] + m[2,0]) / s; y = (m[1,2] + m[2,1]) / s; z = 0.25 * s
    q = np.array([x, y, z, w]); return q / np.linalg.norm(q)

def local_trs(g, A, t):
    """그룹 g 의 로컬 TRS (glTF 공간). A = 그 애니의 {본 이름: {채널: 키들}} 또는 None."""
    k = (A or {}).get(g['name'], {})
    p = sample(k.get('position'), t, [0, 0, 0]); r = sample(k.get('rotation'), t, [0, 0, 0]); s = sample(k.get('scale'), t, [1, 1, 1])
    r = [-r[0], -r[1], r[2]]; p = [-p[0], p[1], p[2]]
    gr = g.get('rotation', [0, 0, 0]); R = rotm([gr[i] + r[i] for i in range(3)])
    o = np.array(g['origin'], float); a = o + np.array(p, float)
    Rp = D @ R @ D; S = np.array(s, float)
    tvec = CS * (D @ a) - Rp @ (S * (CS * (D @ o)))
    return tvec, quat(Rp), S

class Model:
    def __init__(self, path):
        self.m = json.load(open(path)); self.el = {e['uuid']: e for e in self.m['elements']}
        self.name = self.m['name']; self.W = self.m['resolution']['width']; self.H = self.m['resolution']['height']
        self.tex = base64.b64decode(self.m['textures'][0]['source'].split(',')[1])
        self.groups = []
        def walk(g, parent):
            idx = len(self.groups); self.groups.append((g, parent))
            for c in g['children']:
                if isinstance(c, dict): walk(c, idx)
        for g in self.m['outliner']:
            if isinstance(g, dict) and g.get('export', True): walk(g, -1)
        self.anims = {}
        for a in self.m['animations']:
            short = a['name'].split('.')[-1]; bones = {}
            for v in a['animators'].values():
                ch = {}
                for k in v['keyframes']: ch.setdefault(k['channel'], []).append(k)
                for c in ch.values(): c.sort(key=lambda k: k['time'])
                bones[v['name']] = ch
            self.anims[short] = (float(a['length']), bones)

    def mesh_of(self, g):
        """그룹 바로 아래 큐브들 → (pos, nrm, uv, idx). 정점은 절대 좌표(glTF 공간)."""
        P, N, UV, I = [], [], [], []
        for c in g['children']:
            if not isinstance(c, str): continue
            e = self.el[c]; o = np.array(e['origin'], float); Rc = rotm(e.get('rotation', [0, 0, 0]))
            f = np.array(e['from'], float) - e.get('inflate', 0); t = np.array(e['to'], float) + e.get('inflate', 0)
            X0, Y0, Z0 = f; X1, Y1, Z1 = t
            Pf = {'north': ((X1,Y1,Z0),(X0,Y1,Z0),(X1,Y0,Z0)), 'south': ((X0,Y1,Z1),(X1,Y1,Z1),(X0,Y0,Z1)),
                  'east': ((X1,Y1,Z1),(X1,Y1,Z0),(X1,Y0,Z1)), 'west': ((X0,Y1,Z0),(X0,Y1,Z1),(X0,Y0,Z0)),
                  'up': ((X1,Y1,Z1),(X0,Y1,Z1),(X1,Y1,Z0)), 'down': ((X1,Y0,Z0),(X0,Y0,Z0),(X1,Y0,Z1))}
            for face, fd in e['faces'].items():
                u = fd['uv']; x1, x2 = sorted([u[0], u[2]]); y1, y2 = sorted([u[1], u[3]])
                if x2 <= x1 or y2 <= y1: continue
                c0, cu, cv = [np.array(q, float) for q in Pf[face]]; c3 = cu + cv - c0
                pts = [CS * (D @ (o + Rc @ (q - o))) for q in (c0, cu, c3, cv)]
                uvs = [(x1 / self.W, y1 / self.H), (x2 / self.W, y1 / self.H), (x2 / self.W, y2 / self.H), (x1 / self.W, y2 / self.H)]
                n = np.cross(pts[1] - pts[0], pts[3] - pts[0]); n = n / (np.linalg.norm(n) or 1)
                b = len(P)
                for q, uv in zip(pts, uvs): P.append(q); N.append(n); UV.append(uv)
                I += [b, b + 1, b + 2, b, b + 2, b + 3]
        return P, N, UV, I

def build(models, clips, out):
    """models: [Model]; clips: {name: [(model_index, anim_name, start_s, length_or_None), ...]}"""
    nodes, meshes, accessors, views, bufs = [], [], [], [], bytearray()
    def add_buf(data, target=None):
        while len(bufs) % 4: bufs.append(0)
        off = len(bufs); bufs.extend(data)
        v = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
        if target: v["target"] = target
        views.append(v); return len(views) - 1
    def acc(arr, typ, ctype=5126, target=None, minmax=False):
        a = np.asarray(arr, dtype=np.float32 if ctype == 5126 else np.uint32)
        vi = add_buf(a.tobytes(), target)
        d = {"bufferView": vi, "componentType": ctype, "count": len(a), "type": typ}
        if minmax: d["min"] = a.min(axis=0).tolist(); d["max"] = a.max(axis=0).tolist()
        accessors.append(d); return len(accessors) - 1
    images, textures, materials = [], [], []
    roots, node_of = [], {}
    for mi, M in enumerate(models):
        vi = add_buf(M.tex); images.append({"bufferView": vi, "mimeType": "image/png"})
        textures.append({"source": len(images) - 1}); materials.append({"pbrMetallicRoughness": {"baseColorTexture": {"index": len(textures) - 1}}, "alphaMode": "MASK"})
        root = len(nodes); nodes.append({"name": "M_" + M.name, "children": []}); roots.append(root)
        for gi, (g, parent) in enumerate(M.groups):
            ni = len(nodes); node_of[(mi, gi)] = ni
            t, q, s = local_trs(g, None, 0)
            nd = {"name": f"{M.name}:{g['name']}", "translation": t.tolist(), "rotation": q.tolist(), "scale": s.tolist(), "children": []}
            P, N, UV, I = M.mesh_of(g)
            if P:
                prim = {"attributes": {"POSITION": acc(P, "VEC3", target=34962, minmax=True), "NORMAL": acc(N, "VEC3", target=34962), "TEXCOORD_0": acc(UV, "VEC2", target=34962)},
                        "indices": acc(I, "SCALAR", 5125, 34963), "material": mi}
                meshes.append({"primitives": [prim]}); nd["mesh"] = len(meshes) - 1
            nodes.append(nd)
            (nodes[root] if parent < 0 else nodes[node_of[(mi, parent)]])["children"].append(ni)
    animations = []
    FPS = 30
    for cname, segs in clips.items():
        total = max(s[2] + (s[3] if s[3] is not None else models[s[0]].anims[s[1]][0]) for s in segs)
        times = [i / FPS for i in range(int(math.ceil(total * FPS)) + 1)]
        def seg_at(t):
            cur = segs[0]
            for s in segs:
                if t >= s[2] - 1e-9: cur = s
            return cur
        samplers, channels = [], []
        tin = acc([[x] for x in times], "SCALAR"); accessors[tin]["min"] = [0.0]; accessors[tin]["max"] = [times[-1]]
        # 모델 보이기/숨기기
        for mi, M in enumerate(models):
            sc = [[1, 1, 1] if seg_at(t)[0] == mi else [0, 0, 0] for t in times]
            samplers.append({"input": tin, "output": acc(sc, "VEC3"), "interpolation": "STEP"})
            channels.append({"sampler": len(samplers) - 1, "target": {"node": roots[mi], "path": "scale"}})
        for mi, M in enumerate(models):
            used = any(s[0] == mi for s in segs)
            if not used: continue
            for gi, (g, parent) in enumerate(M.groups):
                T, Q, S = [], [], []
                for t in times:
                    s = seg_at(t)
                    if s[0] == mi:
                        L, bones = M.anims[s[1]]; tt = min(t - s[2], L)
                        tv, q, sv = local_trs(g, bones, tt)
                    else:
                        tv, q, sv = local_trs(g, None, 0)
                    if Q and np.dot(Q[-1], q) < 0: q = -q
                    T.append(tv); Q.append(q); S.append(sv)
                ni = node_of[(mi, gi)]
                for path, data, typ in (("translation", T, "VEC3"), ("rotation", Q, "VEC4"), ("scale", S, "VEC3")):
                    samplers.append({"input": tin, "output": acc(data, typ), "interpolation": "LINEAR"})
                    channels.append({"sampler": len(samplers) - 1, "target": {"node": ni, "path": path}})
        animations.append({"name": cname, "samplers": samplers, "channels": channels})
    gltf = {"asset": {"version": "2.0", "generator": "bb2glb"}, "scene": 0, "scenes": [{"nodes": roots}], "nodes": nodes, "meshes": meshes,
            "accessors": accessors, "bufferViews": views, "buffers": [{"byteLength": len(bufs)}], "images": images, "textures": textures,
            "materials": materials, "samplers": [{"magFilter": 9728, "minFilter": 9728}], "animations": animations}
    for t in gltf["textures"]: t["sampler"] = 0
    js = json.dumps(gltf, separators=(',', ':')).encode()
    while len(js) % 4: js += b' '
    while len(bufs) % 4: bufs.append(0)
    glb = struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(bufs)) + struct.pack('<II', len(js), 0x4E4F534A) + js + struct.pack('<II', len(bufs), 0x004E4942) + bytes(bufs)
    open(out, 'wb').write(glb); print(out, len(glb) // 1024, 'KB', len(nodes), 'nodes', len(animations), 'clips')
