"""악마 1인 레이드 모델 공통 도우미 — 대칭 큐브, 팔레트 참조 페인터, 애니메이션 키 유틸."""
from gen import *
import math

CM = 'catmullrom'

def mirror_x(v): return [-v[0], v[1], v[2]]

def C(name, frm, to, paint, mirror=False, **kw):
    if mirror:
        frm, to = [-to[0], frm[1], frm[2]], [-frm[0], to[1], to[2]]
        if 'origin' in kw: kw['origin'] = mirror_x(kw['origin'])
        if 'rot' in kw: kw['rot'] = [kw['rot'][0], -kw['rot'][1], -kw['rot'][2]]
    return Cube(name, frm, to, paint, **kw)

def shift(node, dy):
    node.origin = [node.origin[0], node.origin[1] + dy, node.origin[2]]
    for c in node.children:
        if isinstance(c, Cube):
            c.frm = [c.frm[0], c.frm[1] + dy, c.frm[2]]; c.to = [c.to[0], c.to[1] + dy, c.to[2]]
            c.origin = [c.origin[0], c.origin[1] + dy, c.origin[2]]
        else:
            shift(c, dy)

def side(s):
    """좌우 대칭 도우미: (mirror 여부, 위치 변환, 회전 변환)."""
    m = s == 'left'
    P = (lambda v: mirror_x(v)) if m else (lambda v: v)
    R = (lambda r: [r[0], -r[1], -r[2]]) if m else (lambda r: r)
    return m, P, R

def lerp(a, b, f): return tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3))

# ---------------- 팔레트 참조 페인터 (팔레트 dict를 그릴 때 읽음 → 이로치는 dict만 바꾸면 됨) ----------------
def sol(pal, k, noise=0.08, dark_bottom=0.0):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; h = y2 - y1
        for y in range(y1, y2):
            g = (y - y1) / max(1, h - 1) if face not in ('up', 'down') else 0
            for x in range(x1, x2):
                f = SHADE[face] * (1 + rng.uniform(-noise, noise)) * (1 - dark_bottom * g)
                img.putpixel((x, y), col(pal[k], f))
    return p

def grad(pal, k1, k2, noise=0.07):
    """옆면 위→아래 k1→k2 그라데이션."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; h = y2 - y1
        for y in range(y1, y2):
            g = (y - y1) / max(1, h - 1) if face not in ('up', 'down') else 0.3
            c = lerp(pal[k1], pal[k2], g)
            for x in range(x1, x2):
                img.putpixel((x, y), col(c, SHADE[face] * (1 + rng.uniform(-noise, noise))))
    return p

def trim(pal, k, ek, w=1, noise=0.07):
    """본체 + 옆면 테두리(ek)."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r
        for y in range(y1, y2):
            for x in range(x1, x2):
                edge = face not in ('up', 'down') and (x - x1 < w or x2 - 1 - x < w or y - y1 < w or y2 - 1 - y < w)
                img.putpixel((x, y), col(pal[ek] if edge else pal[k], SHADE[face] * (1 + rng.uniform(-noise, noise))))
    return p

def stripes(pal, k1, k2, period=4, width=1, vertical=True, noise=0.06):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r
        for y in range(y1, y2):
            for x in range(x1, x2):
                t = (x - x1) if vertical else (y - y1)
                c = pal[k2] if (t % period) < width and face not in ('up', 'down') else pal[k1]
                img.putpixel((x, y), col(c, SHADE[face] * (1 + rng.uniform(-noise, noise))))
    return p

def tipped(pal, k, tk, n=2, noise=0.07):
    """손톱·뿔 끝: 옆면 아래 n줄을 tk로."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r
        for y in range(y1, y2):
            for x in range(x1, x2):
                c = pal[tk] if (face == 'down' or (face not in ('up', 'down') and y2 - 1 - y < n)) else pal[k]
                img.putpixel((x, y), col(c, SHADE[face] * (1 + rng.uniform(-noise, noise))))
    return p

def tipped_up(pal, k, tk, n=2, noise=0.07):
    """위로 솟은 뿔 끝: 옆면 위 n줄을 tk로."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r
        for y in range(y1, y2):
            for x in range(x1, x2):
                c = pal[tk] if (face == 'up' or (face not in ('up', 'down') and y - y1 < n)) else pal[k]
                img.putpixel((x, y), col(c, SHADE[face] * (1 + rng.uniform(-noise, noise))))
    return p

def faces(base, **fs):
    """base 칠한 뒤 특정 면에 덧그리기: fs[face](img, r, rng)."""
    def p(img, face, r, rng):
        base(img, face, r, rng)
        if face in fs: fs[face](img, r, rng)
    return p

def ragged(base, chance=0.35, rows=2):
    """옆면 아래 가장자리를 투명 픽셀로 들쭉날쭉하게 (천·털 끝)."""
    def p(img, face, r, rng):
        base(img, face, r, rng)
        if face in ('up', 'down'): return
        x1, y1, x2, y2 = r
        for x in range(x1, x2):
            for k in range(rows):
                if rng.random() < chance * (1 - k * 0.45): img.putpixel((x, y2 - 1 - k), (0, 0, 0, 0))
    return p

PIPS = {1: [(.5, .5)], 2: [(.25, .25), (.75, .75)], 3: [(.25, .25), (.5, .5), (.75, .75)],
        4: [(.25, .25), (.75, .25), (.25, .75), (.75, .75)],
        5: [(.25, .25), (.75, .25), (.5, .5), (.25, .75), (.75, .75)],
        6: [(.25, .2), (.75, .2), (.25, .5), (.75, .5), (.25, .8), (.75, .8)]}
DICE_FACES = {'north': 1, 'south': 6, 'east': 3, 'west': 4, 'up': 2, 'down': 5}

def dice(pal, k, pk, ek=None, pip_frac=0.12):
    """주사위: 면마다 눈 (마주보는 면 합 7)."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for y in range(y1, y2):
            for x in range(x1, x2):
                edge = ek and (x - x1 < 1 or x2 - 1 - x < 1 or y - y1 < 1 or y2 - 1 - y < 1)
                img.putpixel((x, y), col(pal[ek] if edge else pal[k], SHADE[face] * (1 + rng.uniform(-.04, .04))))
        rad = max(0.6, min(w, h) * pip_frac)
        for (u, v) in PIPS[DICE_FACES[face]]:
            cx, cy = u * w, v * h
            for ly in range(h):
                for lx in range(w):
                    if (lx + .5 - cx) ** 2 + (ly + .5 - cy) ** 2 <= rad * rad:
                        px(img, r, lx, ly, col(pal[pk]))
    return p

# ---------------- 애니메이션 유틸 ----------------
def Z(t, ch='rotation'): return (ch, t, 0, 0, 0, CM)
def R(t, x=0, y=0, z=0, i=CM): return ('rotation', t, x, y, z, i)
def T(t, x=0, y=0, z=0, i=CM): return ('position', t, x, y, z, i)
def S(t, x=1, y=1, z=1, i=CM): return ('scale', t, x, y, z, i)

def osc(ch, L, a, b, phase=0.0, steps=4, axis=0):
    out = []
    for i in range(steps + 1):
        t = L * i / steps; v = (a + b) / 2 + (b - a) / 2 * math.sin(2 * math.pi * i / steps + phase)
        xyz = [0, 0, 0]; xyz[axis] = round(v, 3); out.append((ch, round(t, 4), *xyz, CM))
    return out

def mirror_tracks(tr):
    """right_* 트랙을 left_*로 대칭 복제 (left_가 이미 있으면 그대로)."""
    out = dict(tr)
    for k, v in tr.items():
        if k.startswith('right_') and 'left_' + k[6:] not in tr:
            nv = []
            for (c, t, x, y, z, *rest) in v:
                if c == 'rotation': nv.append((c, t, x, -y, -z, *rest))
                elif c == 'position': nv.append((c, t, -x, y, z, *rest))
                else: nv.append((c, t, x, y, z, *rest))
            out['left_' + k[6:]] = nv
    return out

def swap_sides(tr):
    """좌우를 통째로 바꾼 트랙 (반대 손 버전)."""
    out = {}
    for k, v in tr.items():
        nk = k.replace('right_', '@').replace('left_', 'right_').replace('@', 'left_')
        nv = []
        for (c, t, x, y, z, *rest) in v:
            if c == 'rotation': nv.append((c, t, x, -y, -z, *rest))
            elif c == 'position': nv.append((c, t, -x, y, z, *rest))
            else: nv.append((c, t, x, y, z, *rest))
        out[nk] = nv
    return out

def biped_walk(L, leg=26, arm=16, lift=32, bob=0.6, arm_names=('right_arm', 'left_arm'), extra=None):
    """2족 보행: 다리 좌우 반대, 팔은 같은 쪽 다리와 반대, 내딛는 쪽 정강이 접기."""
    h, q = L / 2, L / 4
    sw = lambda a: [R(0, a), R(h, -a), R(L, a)]
    tr = {'right_leg': sw(leg), 'left_leg': sw(-leg),
          arm_names[0]: sw(-arm), arm_names[1]: sw(arm),
          'right_shin': [Z(0), R(q), R(3 * q, -lift), R(L)],
          'left_shin': [Z(0), R(q, -lift), R(h), R(L)],
          'body': [T(0), T(q, 0, -bob), T(h), T(3 * q, 0, -bob), T(L)]}
    tr.update(extra or {})
    return tr

def build_anims(prefix, spec, G):
    """spec: [(name, length, loop, tracks)] → anim 목록 (같은 채널·시간 중복 제거)."""
    out = []
    for name, ln, lp, tr in spec:
        for k, v in tr.items():
            seen = set(); tr[k] = [x for x in v if not ((x[0], x[1]) in seen or seen.add((x[0], x[1])))]
        out.append(anim(prefix + name, ln, lp, tr, G))
    return out
