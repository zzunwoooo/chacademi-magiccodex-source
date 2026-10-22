#!/usr/bin/env python3
"""차카데미 NPC 모델 정리 도구 (FBX/GLB → 배포용 GLB).

원본(FBX 권장, Tripo/Mixamo 리깅)을 클라이언트 모드(chacademi-npc-model)가 읽는 규격으로 바꾼다.

출력 규격 (docs/NPC_MODEL_FORMAT.md 와 같음)
  - glTF 2.0 GLB 한 파일, 외부 URI 없음, 텍스처 JPEG/PNG 내장
  - 좌표: +Y 위, 모델 정면 +Z, 1 = 1블록, 원점 = 두 발 사이 바닥
  - 최상위 노드 "chaca_root" 하나가 단위·방향·원점 보정을 담당 (원래 계층은 그 아래 그대로)
  - 애니메이션: 제자리(in-place). 원래 클립의 수평 이동은 지우고 그 속도를 extras 에 남김
  - 텍스처: 최대 변 --max-texture(기본 2048), UV 섬 가장자리 패딩(번짐 방지)
  - asset.extras.chaca = {formatVersion, heightBlocks, clips{이름:{seconds, rootSpeed}}, source}

필요: Python 3.10+, numpy, Pillow. FBX 입력은 FBX2glTF(v0.9.7) 실행 파일이 필요하다
  https://github.com/facebookincubator/FBX2glTF/releases (Windows: FBX2glTF-windows-x64.exe)

예)
  python normalize_npc_model.py --input 루카.fbx --textures 루카.fbm --fbx2gltf FBX2glTF.exe \
      --out luka.glb --height 1.8
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import os
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

FORMAT_VERSION = 1
COMP = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


# ---------------------------------------------------------------- GLB 입출력

def read_glb(path: Path):
    data = path.read_bytes()
    magic, version, length = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF" or version != 2:
        raise SystemExit(f"GLB 아님: {path}")
    off, js, bin_ = 12, None, b""
    while off < length:
        clen, ctype = struct.unpack_from("<II", data, off)
        chunk = data[off + 8: off + 8 + clen]
        if ctype == 0x4E4F534A:
            js = json.loads(chunk.decode("utf-8"))
        elif ctype == 0x004E4942:
            bin_ = bytes(chunk)
        off += 8 + clen
    return js, bytearray(bin_)


def write_glb(path: Path, js: dict, bin_: bytes):
    js_bytes = json.dumps(js, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    js_bytes += b" " * ((4 - len(js_bytes) % 4) % 4)
    bin_ = bytes(bin_) + b"\0" * ((4 - len(bin_) % 4) % 4)
    total = 12 + 8 + len(js_bytes) + 8 + len(bin_)
    with open(path, "wb") as f:
        f.write(struct.pack("<4sII", b"glTF", 2, total))
        f.write(struct.pack("<II", len(js_bytes), 0x4E4F534A) + js_bytes)
        f.write(struct.pack("<II", len(bin_), 0x004E4942) + bin_)


class Gltf:
    def __init__(self, js, bin_):
        self.js, self.bin = js, bin_

    def accessor(self, i):
        a = self.js["accessors"][i]
        bv = self.js["bufferViews"][a["bufferView"]]
        dt = np.dtype(COMP[a["componentType"]])
        n = NCOMP[a["type"]]
        start = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
        stride = bv.get("byteStride") or n * dt.itemsize
        raw = np.frombuffer(self.bin, np.uint8, count=stride * (a["count"] - 1) + n * dt.itemsize, offset=start)
        out = np.empty((a["count"], n), dt)
        for r in range(a["count"]):
            out[r] = np.frombuffer(raw[r * stride: r * stride + n * dt.itemsize].tobytes(), dt)
        if a.get("normalized"):
            out = out.astype(np.float64) / float(np.iinfo(dt).max)
        return out

    def add_view(self, payload: bytes, target=None) -> int:
        while len(self.bin) % 4:
            self.bin.append(0)
        view = {"buffer": 0, "byteOffset": len(self.bin), "byteLength": len(payload)}
        if target:
            view["target"] = target
        self.bin += payload
        self.js["bufferViews"].append(view)
        return len(self.js["bufferViews"]) - 1

    def add_accessor(self, arr: np.ndarray, typ: str) -> int:
        arr = np.ascontiguousarray(arr, np.float32)
        view = self.add_view(arr.tobytes())
        acc = {"bufferView": view, "componentType": 5126, "count": int(arr.shape[0]), "type": typ}
        if typ != "MAT4":
            acc["min"] = [float(x) for x in arr.reshape(arr.shape[0], -1).min(0)]
            acc["max"] = [float(x) for x in arr.reshape(arr.shape[0], -1).max(0)]
        self.js["accessors"].append(acc)
        return len(self.js["accessors"]) - 1


# ---------------------------------------------------------------- 수학

def quat_to_mat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def local_matrix(node):
    if "matrix" in node:
        return np.array(node["matrix"], np.float64).reshape(4, 4).T
    m = np.eye(4)
    m[:3, :3] = quat_to_mat(node.get("rotation", [0, 0, 0, 1])) @ np.diag(node.get("scale", [1, 1, 1]))
    m[:3, 3] = node.get("translation", [0, 0, 0])
    return m


def world_matrices(js):
    parents = {c: i for i, n in enumerate(js["nodes"]) for c in n.get("children", [])}
    cache = {}

    def world(i):
        if i not in cache:
            m = local_matrix(js["nodes"][i])
            cache[i] = world(parents[i]) @ m if i in parents else m
        return cache[i]

    return [world(i) for i in range(len(js["nodes"]))], parents


# ---------------------------------------------------------------- 텍스처

def uv_mask(g: Gltf, material: int, size: int) -> Image.Image:
    """해당 재질이 실제로 쓰는 UV 영역(삼각형)을 칠한 마스크."""
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    for mesh in g.js["meshes"]:
        for p in mesh["primitives"]:
            if p.get("material") != material or "TEXCOORD_0" not in p["attributes"]:
                continue
            uv = g.accessor(p["attributes"]["TEXCOORD_0"]).astype(np.float64) * size
            idx = g.accessor(p["indices"]).reshape(-1, 3)
            for tri in idx:
                pts = [tuple(uv[k]) for k in tri]
                draw.polygon(pts, fill=255)
    return mask


def dilate(img: Image.Image, mask: Image.Image, pixels: int) -> Image.Image:
    """UV 섬 바깥을 가장 가까운 섬 색으로 채운다(밉맵·축소 때 이웃 섬 색 번짐 방지)."""
    a = np.asarray(img.convert("RGB"), np.float32)
    m = np.asarray(mask, np.uint8) > 0
    filled = m.copy()
    acc = a * m[..., None]
    for _ in range(pixels):
        if filled.all():
            break
        s = np.zeros_like(a)
        c = np.zeros(m.shape, np.float32)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            sf = np.roll(filled, (dy, dx), (0, 1))
            s += np.roll(acc, (dy, dx), (0, 1)) * sf[..., None]
            c += sf
        grow = (~filled) & (c > 0)
        acc[grow] = s[grow] / c[grow][:, None]
        filled |= grow
    out = np.where(filled[..., None], acc, a)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB")


def find_texture(tex_dir: Path | None, node_name: str):
    if not tex_dir:
        return None
    import re
    key = node_name.replace("tripo_node_", "").lower()
    if not key:
        return None
    imgs = [f for f in sorted(tex_dir.iterdir()) if f.suffix.lower() in (".jpg", ".jpeg", ".png")]
    # 1순위: "…_<노드이름>_basecolor" 처럼 이름이 통째로 맞는 것 (Mesh 가 Mesh_001 그림에 붙지 않게)
    exact = [f for f in imgs if re.search(r"(^|[_.\-])" + re.escape(key) + r"_basecolor", f.stem.lower())]
    if len(exact) == 1:
        return exact[0]
    loose = [f for f in imgs if key in f.name.lower()]
    return loose[0] if len(loose) == 1 else (exact[0] if exact else None)


# ---------------------------------------------------------------- 본 작업

def normalize(src: Path, out: Path, tex_dir: Path | None, height: float | None, scale: float | None,
              max_tex: int, root_bone: str, jpeg_q: int, pad: int, forced_yaw: float | None = None) -> dict:
    js, bin_ = read_glb(src)
    g = Gltf(js, bin_)
    report = {"warnings": []}
    if js.get("extensionsRequired"):
        raise SystemExit(f"필수 확장 미지원: {js['extensionsRequired']}")
    if not js.get("skins"):
        report["warnings"].append("스킨 없음: 정적 모델로만 표시됩니다")

    worlds, parents = world_matrices(js)

    # 1) 기본 자세 정점 위치 (스키닝 적용) → 크기·발바닥
    pts = []
    for ni, node in enumerate(js["nodes"]):
        if "mesh" not in node:
            continue
        skin = js["skins"][node["skin"]] if "skin" in node else None
        jm = None
        if skin:
            ibm = g.accessor(skin["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1)
            jm = np.stack([worlds[j] @ ibm[k] for k, j in enumerate(skin["joints"])])
        for p in js["meshes"][node["mesh"]]["primitives"]:
            P = g.accessor(p["attributes"]["POSITION"]).astype(np.float64)
            Ph = np.c_[P, np.ones(len(P))]
            if jm is not None and "JOINTS_0" in p["attributes"]:
                J = g.accessor(p["attributes"]["JOINTS_0"]).astype(int)
                W = g.accessor(p["attributes"]["WEIGHTS_0"]).astype(np.float64)
                W /= np.maximum(W.sum(1, keepdims=True), 1e-9)
                acc = np.zeros((len(P), 4))
                for k in range(4):
                    acc += W[:, k:k + 1] * np.einsum("nij,nj->ni", jm[J[:, k]], Ph)
                pts.append(acc[:, :3])
            else:
                pts.append((Ph @ worlds[ni].T)[:, :3])
    pts = np.concatenate(pts)

    # 2) 정면 방향: 발끝 - 발목 (없으면 +Z 유지)
    name_to_node = {n.get("name", ""): i for i, n in enumerate(js["nodes"])}

    def bone_pos(suffix):
        for nm, i in name_to_node.items():
            if nm.split(":")[-1] == suffix:
                return worlds[i][:3, 3]
        return None

    yaw = 0.0
    fwd = None
    # 정면 = 위 × (오른쪽 허벅지 - 왼쪽 허벅지). 발끝 방향은 팔자걸음 자세에 흔들려 보조로만 쓴다.
    lh, rh = bone_pos("LeftUpLeg"), bone_pos("RightUpLeg")
    if lh is not None and rh is not None:
        fwd = np.cross([0.0, 1.0, 0.0], rh - lh)
        toes = [bone_pos(s + "ToeBase") - bone_pos(s + "Foot") for s in ("Left", "Right")
                if bone_pos(s + "ToeBase") is not None and bone_pos(s + "Foot") is not None]
        if toes and np.dot(sum(toes), fwd) < 0:
            report["warnings"].append("골반 기준 정면과 발끝 방향이 반대 — 뼈 이름(좌우)을 확인하세요")
    if forced_yaw is not None:
        yaw = math.radians(forced_yaw)
    elif fwd is not None and math.hypot(fwd[0], fwd[2]) > 1e-9:
        yaw = math.atan2(fwd[0], fwd[2])  # 정면을 +Z 로 돌리는 각
    else:
        report["warnings"].append("허벅지 뼈를 못 찾아 정면 방향 보정 안 함(+Z 가정, --yaw 로 지정 가능)")
    c, s = math.cos(-yaw), math.sin(-yaw)
    rot = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    rp = pts @ rot.T
    lo, hi = rp.min(0), rp.max(0)
    model_h = hi[1] - lo[1]
    if model_h <= 1e-9:
        raise SystemExit("모델 높이가 0")
    k = scale if scale else (height / model_h if height else 1.0)
    center = np.array([(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2])
    root = np.eye(4)
    root[:3, :3] = rot * k
    root[:3, 3] = -center * k

    # 3) chaca_root 삽입
    old_roots = list(js["scenes"][js.get("scene", 0)]["nodes"])
    q = rot_to_quat(rot)
    js["nodes"].append({"name": "chaca_root", "translation": [float(x) for x in root[:3, 3]],
                        "rotation": q, "scale": [k, k, k], "children": old_roots})
    root_index = len(js["nodes"]) - 1
    js["scenes"] = [{"name": "Scene", "nodes": [root_index]}]
    js["scene"] = 0

    # 4) 애니메이션 제자리화
    clips = {}
    hips = next((i for nm, i in name_to_node.items() if nm.split(":")[-1] == root_bone), None)
    for anim in js.get("animations", []):
        if anim.get("name"):
            anim["name"] = anim["name"].split("|")[-1]  # Blender FBX 접두어 "Armature|Armature|" 제거
        seconds = 0.0
        for smp in anim["samplers"]:
            seconds = max(seconds, float(js["accessors"][smp["input"]]["max"][0]))
        root_speed = 0.0
        for ch in anim["channels"]:
            if ch["target"].get("node") != hips or ch["target"]["path"] != "translation" or hips is None:
                continue
            smp = anim["samplers"][ch["sampler"]]
            t = g.accessor(smp["input"]).astype(np.float64)[:, 0]
            v = g.accessor(smp["output"]).astype(np.float64)
            if smp.get("interpolation") == "CUBICSPLINE":
                report["warnings"].append(f"{anim.get('name')}: CUBICSPLINE 루트 이동은 제자리화 생략")
                continue
            pw = root @ worlds[parents[hips]] if hips in parents else root
            wv = (np.c_[v, np.ones(len(v))] @ pw.T)[:, :3]
            span = max(t[-1] - t[0], 1e-6)
            drift = wv[-1] - wv[0]
            drift[1] = 0.0
            if np.linalg.norm(drift) > 0.05:  # 5cm 이상 움직이면 루트 이동으로 본다
                root_speed = float(np.linalg.norm(drift) / span)
                wv -= np.outer((t - t[0]) / span, drift)
                inv = np.linalg.inv(pw)
                nv = (np.c_[wv, np.ones(len(wv))] @ inv.T)[:, :3]
                smp["output"] = g.add_accessor(nv, "VEC3")
        clips[anim.get("name") or f"clip{len(clips)}"] = {"seconds": round(seconds, 4), "rootSpeed": round(root_speed, 4)}

    # 5) 텍스처: 메쉬 노드 이름으로 원본 그림 연결, 패딩, 크기 제한, 내장
    new_images, new_textures = [], []
    js["samplers"] = [{"magFilter": 9729, "minFilter": 9987, "wrapS": 33071, "wrapT": 33071}]
    mat_source = {}
    for node in js["nodes"]:
        if "mesh" not in node:
            continue
        for p in js["meshes"][node["mesh"]]["primitives"]:
            mi = p.get("material")
            if mi is None or mi in mat_source:
                continue
            f = find_texture(tex_dir, node.get("name", ""))
            img = None
            if f:
                img = Image.open(f).convert("RGB")
            else:
                img = embedded_image(g, mi)
            if img is None:
                report["warnings"].append(f"재질 {mi}: 텍스처 없음 → 흰색")
                img = Image.new("RGB", (16, 16), (255, 255, 255))
            if max(img.size) > max_tex:
                r = max_tex / max(img.size)
                img = img.resize((max(1, round(img.width * r)), max(1, round(img.height * r))), Image.LANCZOS)
            if pad > 0 and min(img.size) >= 64:
                img = dilate(img, uv_mask(g, mi, img.width), pad)
            buf = io.BytesIO()
            img.save(buf, "JPEG", quality=jpeg_q, optimize=True)
            mat_source[mi] = len(new_images)
            new_images.append({"name": f"tex{mi}", "mimeType": "image/jpeg", "bufferView": g.add_view(buf.getvalue())})
            new_textures.append({"sampler": 0, "source": len(new_images) - 1})
    js["images"], js["textures"] = new_images, new_textures
    for mi, mat in enumerate(js.get("materials", [])):
        mat.pop("extras", None)
        mat.pop("extensions", None)
        pbr = mat.setdefault("pbrMetallicRoughness", {})
        pbr["metallicFactor"] = 0.0
        pbr["roughnessFactor"] = 1.0
        if mi in mat_source:
            pbr["baseColorTexture"] = {"index": mat_source[mi]}
        mat["doubleSided"] = True
        mat.setdefault("alphaMode", "OPAQUE")
    js.pop("extensionsUsed", None)

    # 6) 안 쓰는 bufferView 제거 + 버퍼 재구성
    compact(g)
    js["buffers"] = [{"byteLength": len(g.bin)}]
    js["asset"] = {"version": "2.0", "generator": "chacademi normalize_npc_model",
                   "extras": {"chaca": {"formatVersion": FORMAT_VERSION, "heightBlocks": round(float(model_h * k), 4),
                                        "clips": clips, "source": src.name}}}
    write_glb(out, js, g.bin)
    data = out.read_bytes()
    report.update({"out": str(out), "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                   "heightBlocks": round(float(model_h * k), 4), "scale": k, "yawDegrees": round(math.degrees(yaw), 2),
                   "clips": clips, "triangles": count_tris(js), "joints": len(js["skins"][0]["joints"]) if js.get("skins") else 0,
                   "textures": [Image.open(io.BytesIO(bytes(g.bin[js['bufferViews'][i['bufferView']]['byteOffset']:][:js['bufferViews'][i['bufferView']]['byteLength']]))).size for i in js["images"]]})
    return report


def embedded_image(g: Gltf, material: int):
    try:
        ti = g.js["materials"][material]["pbrMetallicRoughness"]["baseColorTexture"]["index"]
        im = g.js["images"][g.js["textures"][ti]["source"]]
        if "bufferView" not in im:
            return None
        bv = g.js["bufferViews"][im["bufferView"]]
        raw = bytes(g.bin[bv.get("byteOffset", 0): bv.get("byteOffset", 0) + bv["byteLength"]])
        img = Image.open(io.BytesIO(raw)).convert("RGB")
        return None if max(img.size) <= 2 else img  # FBX2glTF 의 1px 자리표시 그림은 무시
    except (KeyError, IndexError, OSError):
        return None


def rot_to_quat(m):
    t = m[0, 0] + m[1, 1] + m[2, 2]
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        q = [(m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s, 0.25 * s]
    else:
        i = int(np.argmax([m[0, 0], m[1, 1], m[2, 2]]))
        j, k = (i + 1) % 3, (i + 2) % 3
        s = math.sqrt(1.0 + m[i, i] - m[j, j] - m[k, k]) * 2
        q = [0, 0, 0, 0]
        q[i] = 0.25 * s
        q[j] = (m[j, i] + m[i, j]) / s
        q[k] = (m[k, i] + m[i, k]) / s
        q[3] = (m[k, j] - m[j, k]) / s
    return [float(x) for x in q]


def count_tris(js):
    return sum(js["accessors"][p["indices"]]["count"] // 3 for m in js["meshes"] for p in m["primitives"])


def compact(g: Gltf):
    js = g.js
    used = set()
    for a in js["accessors"]:
        if "bufferView" in a:
            used.add(a["bufferView"])
    for im in js.get("images", []):
        if "bufferView" in im:
            used.add(im["bufferView"])
    # 쓰이지 않는 accessor 도 정리
    used_acc = set()
    for m in js["meshes"]:
        for p in m["primitives"]:
            used_acc.update(p["attributes"].values())
            if "indices" in p:
                used_acc.add(p["indices"])
            for t in p.get("targets", []):
                used_acc.update(t.values())
    for s in js.get("skins", []):
        if "inverseBindMatrices" in s:
            used_acc.add(s["inverseBindMatrices"])
    for a in js.get("animations", []):
        for smp in a["samplers"]:
            used_acc.update((smp["input"], smp["output"]))
    acc_map, new_acc = {}, []
    for i, a in enumerate(js["accessors"]):
        if i in used_acc:
            acc_map[i] = len(new_acc)
            new_acc.append(a)
    for m in js["meshes"]:
        for p in m["primitives"]:
            p["attributes"] = {k: acc_map[v] for k, v in p["attributes"].items()}
            if "indices" in p:
                p["indices"] = acc_map[p["indices"]]
            if p.get("targets"):
                p["targets"] = [{k: acc_map[v] for k, v in t.items()} for t in p["targets"]]
            else:
                p.pop("targets", None)
    for s in js.get("skins", []):
        if "inverseBindMatrices" in s:
            s["inverseBindMatrices"] = acc_map[s["inverseBindMatrices"]]
    for a in js.get("animations", []):
        for smp in a["samplers"]:
            smp["input"], smp["output"] = acc_map[smp["input"]], acc_map[smp["output"]]
    js["accessors"] = new_acc
    used = {a["bufferView"] for a in new_acc if "bufferView" in a} | {im["bufferView"] for im in js.get("images", []) if "bufferView" in im}
    new_bin, view_map, views = bytearray(), {}, []
    for i, v in enumerate(js["bufferViews"]):
        if i not in used:
            continue
        while len(new_bin) % 4:
            new_bin.append(0)
        chunk = g.bin[v.get("byteOffset", 0): v.get("byteOffset", 0) + v["byteLength"]]
        nv = dict(v)
        nv["byteOffset"] = len(new_bin)
        new_bin += chunk
        view_map[i] = len(views)
        views.append(nv)
    for a in js["accessors"]:
        if "bufferView" in a:
            a["bufferView"] = view_map[a["bufferView"]]
    for im in js.get("images", []):
        if "bufferView" in im:
            im["bufferView"] = view_map[im["bufferView"]]
    js["bufferViews"] = views
    g.bin = new_bin


# ---------------------------------------------------------------- 비율 조정 (다리·몸통·팔 길이)

PROPORTION_SEGMENTS = {"legs": ("UpLeg", "Leg"), "torso": ("Spine", "Spine1", "Spine2"), "arms": ("Arm", "ForeArm")}


def reproportion(g: Gltf, factors: dict, report: dict):
    """뼈 길이를 바꿔 비율을 고친다 (예: 다리 0.85). 애니메이션은 회전만 쓰므로 그대로 맞는다.

    바인드 자세 기준으로 관절 위치를 새로 잡고, 정점은 붙은 뼈의 '길이 방향으로만' 줄인다(두께 유지).
    노드 로컬 위치·inverse bind 를 같이 고쳐서 기본 자세·애니메이션·스킨이 모두 새 길이를 쓴다.
    """
    js = g.js
    if not js.get("skins") or not any(abs(v - 1) > 1e-6 for v in factors.values()):
        return
    seg_k = {}
    for group, k in factors.items():
        for bone in PROPORTION_SEGMENTS[group]:
            seg_k[bone] = k
    nodes = js["nodes"]
    bare = {i: n.get("name", "").split(":")[-1] for i, n in enumerate(nodes)}
    joints = sorted({j for s in js["skins"] for j in s["joints"]})
    jset = set(joints)
    parent = {c: i for i, n in enumerate(nodes) for c in n.get("children", [])}
    skin0 = js["skins"][0]
    ibm0 = g.accessor(skin0["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1).astype(np.float64)
    B = {j: np.linalg.inv(ibm0[k]) for k, j in enumerate(skin0["joints"])}
    # 스킨마다 메쉬 좌표계가 다를 수 있음: T_s = B0_j @ IBM_s,j (메쉬_s → 메쉬0) 가 모든 관절에서 같아야 함
    skinT = []
    for s in js["skins"]:
        ib = g.accessor(s["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1).astype(np.float64)
        Ts = [B[j] @ ib[k] for k, j in enumerate(s["joints"]) if j in B]
        T = Ts[0]
        if max(np.abs(t - T).max() for t in Ts) > 1e-3 * max(1.0, np.abs(T).max()):
            raise SystemExit("스킨마다 바인드 자세가 달라 비율 조정을 할 수 없음")
        skinT.append(T)
    pos = {j: B[j][:3, 3].copy() for j in B}
    # 뼈 방향: 같은 이름 계열의 대표 자식 쪽
    def main_child(j):
        kids = [c for c in nodes[j].get("children", []) if c in pos]
        if not kids:
            return None
        nm_ = bare[j]
        prefer = {"UpLeg": "Leg", "Leg": "Foot", "Spine": "Spine1", "Spine1": "Spine2", "Spine2": "Neck", "Arm": "ForeArm", "ForeArm": "Hand"}.get(
            nm_.replace("Left", "").replace("Right", ""))
        for c in kids:
            if prefer and bare[c].replace("Left", "").replace("Right", "") == prefer:
                return c
        return max(kids, key=lambda c: np.linalg.norm(pos[c] - pos[j]))
    A = {}
    for j in pos:
        k = seg_k.get(bare[j].replace("Left", "").replace("Right", ""), 1.0)
        c = main_child(j)
        if abs(k - 1) < 1e-9 or c is None:
            A[j] = np.eye(3)
            continue
        d = pos[c] - pos[j]
        d /= np.linalg.norm(d)
        A[j] = np.eye(3) + (k - 1) * np.outer(d, d)
    # 새 관절 위치 (부모부터)
    newpos = {}
    order = []
    def visit(j):
        order.append(j)
        for c in nodes[j].get("children", []):
            if c in pos:
                visit(c)
    for r in [j for j in pos if parent.get(j) not in pos]:
        visit(r)
    for j in order:
        p = parent.get(j)
        newpos[j] = pos[j].copy() if p not in pos else newpos[p] + A[p] @ (pos[j] - pos[p])
    # 노드 로컬 위치 = 부모 바인드 회전 기준 새 오프셋
    for j in order:
        p = parent.get(j)
        if p in pos:
            t = np.linalg.solve(B[p][:3, :3], newpos[j] - newpos[p])
            nodes[j]["translation"] = [float(x) for x in t]
            nodes[j].pop("matrix", None)
    # 정점·법선
    for ni, node in enumerate(nodes):
        if "mesh" not in node or "skin" not in node:
            continue
        sk = js["skins"][node["skin"]]
        jl = sk["joints"]
        T = skinT[node["skin"]]
        Ti = np.linalg.inv(T)
        for prim in js["meshes"][node["mesh"]]["primitives"]:
            at = prim["attributes"]
            P = g.accessor(at["POSITION"]).astype(np.float64) @ T[:3, :3].T + T[:3, 3]  # 메쉬0 좌표로
            N = (g.accessor(at["NORMAL"]).astype(np.float64) @ np.linalg.inv(T[:3, :3])) if "NORMAL" in at else None
            J = g.accessor(at["JOINTS_0"]).astype(int)
            W = g.accessor(at["WEIGHTS_0"]).astype(np.float64)
            W /= np.maximum(W.sum(1, keepdims=True), 1e-12)
            P2 = np.zeros_like(P)
            N2 = np.zeros_like(P) if N is not None else None
            for kk in range(4):
                nodes_k = np.array([jl[x] for x in J[:, kk]])
                for j in np.unique(nodes_k):
                    m = nodes_k == j
                    w = W[m, kk:kk + 1]
                    P2[m] += w * (newpos[j] + (P[m] - pos[j]) @ A[j].T)
                    if N is not None:
                        N2[m] += w * (N[m] @ np.linalg.inv(A[j]))
            at["POSITION"] = g.add_accessor(P2 @ Ti[:3, :3].T + Ti[:3, 3], "VEC3")  # 원래 메쉬 좌표로
            if N is not None:
                N2 = N2 @ T[:3, :3]
                N2 /= np.maximum(np.linalg.norm(N2, axis=1, keepdims=True), 1e-12)
                at["NORMAL"] = g.add_accessor(N2, "VEC3")
    # inverse bind
    for si, s in enumerate(js["skins"]):
        mats = []
        for j in s["joints"]:
            Bn = B[j].copy()
            Bn[:3, 3] = newpos[j]
            mats.append((np.linalg.inv(Bn) @ skinT[si]).T.reshape(16))
        s["inverseBindMatrices"] = g.add_accessor(np.array(mats, np.float32), "MAT4")
    report["proportion"] = factors


def main(argv=None):
    ap = argparse.ArgumentParser(description="차카데미 NPC 모델 정리 (FBX/GLB → 배포용 GLB)")
    ap.add_argument("--input", required=True, type=Path, help=".fbx 또는 .glb")
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--textures", type=Path, help="FBX 텍스처 폴더(.fbm). 메쉬 노드 이름으로 그림을 찾음")
    ap.add_argument("--fbx2gltf", type=Path, help="FBX2glTF 실행 파일 (FBX 입력일 때)")
    ap.add_argument("--height", type=float, default=1.8, help="결과 키(블록). --scale 이 있으면 무시")
    ap.add_argument("--scale", type=float)
    ap.add_argument("--max-texture", type=int, default=2048)
    ap.add_argument("--jpeg-quality", type=int, default=90)
    ap.add_argument("--pad", type=int, default=8, help="UV 섬 가장자리 패딩(픽셀)")
    ap.add_argument("--yaw", type=float, help="정면 보정 각(도)을 직접 지정. 생략하면 골반 축으로 자동 계산")
    ap.add_argument("--root-bone", default="Hips", help="루트 이동을 지울 뼈 이름(접두어 제외)")
    ap.add_argument("--report", type=Path, help="결과 JSON 저장 위치")
    ap.add_argument("--legs", type=float, default=1.0, help="다리 길이 배율 (예: 0.85 = 15%% 짧게)")
    ap.add_argument("--torso", type=float, default=1.0, help="몸통(허리~목) 길이 배율")
    ap.add_argument("--arms", type=float, default=1.0, help="팔 길이 배율")
    a = ap.parse_args(argv)

    src = a.input
    with tempfile.TemporaryDirectory() as tmp:
        if src.suffix.lower() == ".fbx":
            if not a.fbx2gltf:
                raise SystemExit("FBX 입력에는 --fbx2gltf 가 필요합니다")
            base = Path(tmp) / "converted"
            r = subprocess.run([str(a.fbx2gltf), "-b", "-i", str(src), "-o", str(base), "--pbr-metallic-roughness"],
                               capture_output=True, text=True)
            if r.returncode != 0 or not base.with_suffix(".glb").exists():
                sys.stderr.write(r.stdout + r.stderr)
                raise SystemExit("FBX2glTF 변환 실패")
            src = base.with_suffix(".glb")
        factors = {"legs": a.legs, "torso": a.torso, "arms": a.arms}
        if any(abs(v - 1) > 1e-6 for v in factors.values()):
            # 비율 먼저 바꾼 GLB 를 만든 뒤 그걸 정리 (키·발바닥·정면은 바뀐 비율 기준으로 다시 계산)
            js0, bin0 = read_glb(src)
            g0 = Gltf(js0, bin0)
            rep0 = {}
            reproportion(g0, factors, rep0)
            compact(g0)
            js0["buffers"] = [{"byteLength": len(g0.bin)}]
            src = Path(tmp) / "reproportioned.glb"
            write_glb(src, js0, g0.bin)
        report = normalize(src, a.out, a.textures, None if a.scale else a.height, a.scale,
                           a.max_texture, a.root_bone, a.jpeg_quality, a.pad, a.yaw)
        report["proportion"] = factors
    report["input"] = str(a.input)
    text = json.dumps(report, ensure_ascii=False, indent=2)
    if a.report:
        a.report.write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
