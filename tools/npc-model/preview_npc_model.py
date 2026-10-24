#!/usr/bin/env python3
"""정리된 NPC GLB를 소프트웨어로 렌더해 자세를 눈으로 확인하는 도구 (게임 없이 검수용).

모드 렌더러와 같은 규칙(노드 계층 → 관절 행렬 × inverse bind → 4개 웨이트 선형 스키닝,
LINEAR/STEP 키프레임, 회전은 slerp)으로 자세를 계산하고, 앞·옆 두 방향을 그린다.

예) python preview_npc_model.py luka.glb --clip walk --times 0,0.3,0.6,0.9 --out walk.png
"""
from __future__ import annotations

import argparse
import io
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import normalize_npc_model as nm


def slerp(a, b, t):
    d = float(np.dot(a, b))
    if d < 0:
        b, d = -b, -d
    if d > 0.9995:
        r = a + t * (b - a)
        return r / np.linalg.norm(r)
    th = math.acos(d)
    return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / math.sin(th)


def sample(g, anim, time):
    """애니메이션의 time 시점 노드별 TRS 덮어쓰기."""
    out = {}
    for ch in anim["channels"]:
        smp = anim["samplers"][ch["sampler"]]
        t = g.accessor(smp["input"])[:, 0].astype(np.float64)
        v = g.accessor(smp["output"]).astype(np.float64)
        tt = min(max(time, t[0]), t[-1])
        i = int(np.searchsorted(t, tt, side="right") - 1)
        i = max(0, min(i, len(t) - 2)) if len(t) > 1 else 0
        if len(t) == 1:
            val = v[0]
        elif smp.get("interpolation") == "STEP":
            val = v[i]
        else:
            f = 0.0 if t[i + 1] == t[i] else (tt - t[i]) / (t[i + 1] - t[i])
            val = slerp(v[i], v[i + 1], f) if ch["target"]["path"] == "rotation" else v[i] * (1 - f) + v[i + 1] * f
        out.setdefault(ch["target"]["node"], {})[ch["target"]["path"]] = [float(x) for x in val]
    return out


def posed_points(g, overrides):
    js = g.js
    nodes = [dict(n, **overrides.get(i, {})) for i, n in enumerate(js["nodes"])]
    for i, n in enumerate(nodes):
        if i in overrides:
            n.pop("matrix", None)
    worlds, _ = nm.world_matrices({"nodes": nodes})
    tris, cols = [], []
    for ni, node in enumerate(js["nodes"]):
        if "mesh" not in node:
            continue
        skin = js["skins"][node["skin"]] if "skin" in node else None
        for p in js["meshes"][node["mesh"]]["primitives"]:
            P = g.accessor(p["attributes"]["POSITION"]).astype(np.float64)
            Ph = np.c_[P, np.ones(len(P))]
            if skin:
                ibm = g.accessor(skin["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1)
                jm = np.stack([worlds[j] @ ibm[k] for k, j in enumerate(skin["joints"])])
                J = g.accessor(p["attributes"]["JOINTS_0"]).astype(int)
                W = g.accessor(p["attributes"]["WEIGHTS_0"]).astype(np.float64)
                W /= np.maximum(W.sum(1, keepdims=True), 1e-9)
                V = np.zeros((len(P), 4))
                for k in range(4):
                    V += W[:, k:k + 1] * np.einsum("nij,nj->ni", jm[J[:, k]], Ph)
            else:
                V = Ph @ worlds[ni].T
            idx = g.accessor(p["indices"]).reshape(-1, 3)
            uv = g.accessor(p["attributes"]["TEXCOORD_0"]).astype(np.float64)
            tex = texture_of(g, p.get("material"))
            c = uv[idx].mean(1)
            if tex is not None:
                tw, th = tex.shape[1], tex.shape[0]
                xs = np.clip((c[:, 0] * tw).astype(int), 0, tw - 1)
                ys = np.clip((c[:, 1] * th).astype(int), 0, th - 1)
                cols.append(tex[ys, xs])
            else:
                cols.append(np.full((len(idx), 3), 200))
            tris.append(V[:, :3][idx])
    return np.concatenate(tris), np.concatenate(cols)


_tex_cache = {}


def texture_of(g, mi):
    if mi is None:
        return None
    if mi not in _tex_cache:
        try:
            ti = g.js["materials"][mi]["pbrMetallicRoughness"]["baseColorTexture"]["index"]
            im = g.js["images"][g.js["textures"][ti]["source"]]
            bv = g.js["bufferViews"][im["bufferView"]]
            raw = bytes(g.bin[bv["byteOffset"]: bv["byteOffset"] + bv["byteLength"]])
            _tex_cache[mi] = np.asarray(Image.open(io.BytesIO(raw)).convert("RGB"))
        except (KeyError, IndexError):
            _tex_cache[mi] = None
    return _tex_cache[mi]


def draw(tris, cols, yaw_deg, size=420, height=2.0):
    a = math.radians(yaw_deg)
    R = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    T = tris @ R.T
    n = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
    n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-12)
    light = np.abs(n @ np.array([0.3, 0.5, 0.81])) * 0.55 + 0.45
    order = np.argsort(T[:, :, 2].mean(1))  # 카메라는 +Z 에서 -Z 를 봄: z 작은 것부터
    img = Image.new("RGB", (size, size), (235, 235, 240))
    d = ImageDraw.Draw(img)
    s = size / height
    for i in order:
        pts = [(size / 2 + v[0] * s, size - 10 - v[1] * s) for v in T[i]]
        c = tuple(int(min(255, x * light[i])) for x in cols[i])
        d.polygon(pts, fill=c)
    d.line([(0, size - 10), (size, size - 10)], fill=(120, 120, 120))
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("glb", type=Path)
    ap.add_argument("--clip")
    ap.add_argument("--times", default="0")
    ap.add_argument("--views", default="0,90")
    ap.add_argument("--out", type=Path, required=True)
    a = ap.parse_args()
    js, bin_ = nm.read_glb(a.glb)
    g = nm.Gltf(js, bin_)
    anim = next((x for x in js.get("animations", []) if x.get("name") == a.clip), None) if a.clip else None
    times = [float(x) for x in a.times.split(",")]
    views = [float(x) for x in a.views.split(",")]
    tiles = []
    for t in times:
        tris, cols = posed_points(g, sample(g, anim, t) if anim else {})
        h = max(2.0, float(tris[:, :, 1].max()) * 1.05)  # 큰 모델(모자 등)도 잘리지 않게
        tiles.append([draw(tris, cols, v, height=h) for v in views])
    W = 420
    sheet = Image.new("RGB", (W * len(times), W * len(views)), "white")
    dd = ImageDraw.Draw(sheet)
    for c, col in enumerate(tiles):
        for r, im in enumerate(col):
            sheet.paste(im, (c * W, r * W))
            dd.text((c * W + 6, r * W + 6), f"{a.clip or 'rest'} t={times[c]:.2f}s view={views[r]:.0f}", fill=(0, 0, 0))
    sheet.save(a.out)


if __name__ == "__main__":
    main()
