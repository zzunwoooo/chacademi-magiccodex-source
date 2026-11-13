from gen import *
import math, random
import numpy as np

cm = 'catmullrom'

def mirror_x(v): return [-v[0], v[1], v[2]]
def MR(r, m): return [r[0], -r[1], -r[2]] if m else list(r)
def MP(v, m): return mirror_x(v) if m else list(v)

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
        else: shift(c, dy)

def seg(name, a, b, wd, paint, mirror=False):
    """a->b 방향 막대(정사각 단면 wd). 그룹 회전으로 방향을 맞춤"""
    if mirror: a = mirror_x(a); b = mirror_x(b)
    a = np.array(a, float); b = np.array(b, float); d = b - a; L = np.linalg.norm(d); d /= L
    ax = math.degrees(math.asin(d[2])); cz = math.degrees(math.atan2(-d[0], d[1]))
    L = max(1, int(round(L))); h = wd / 2
    cube = Cube(name, [a[0] - h, a[1], a[2] - h], [a[0] + h, a[1] + L, a[2] + h], paint)
    return Group(name, [float(v) for v in a], [cube], [ax, 0, cz])

# ---------------- painters ----------------
def mix(a, b, t): return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))

def shaded(base, noise=.06, grad=.14, edge=.9, alpha=None):
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect; h = y2 - y1; w = x2 - x1
        a = alpha if alpha is not None else (base[3] if len(base) > 3 else 255)
        for y in range(y1, y2):
            for x in range(x1, x2):
                lx, ly = x - x1, y - y1
                f = SHADE[face] * (1 + rng.uniform(-noise, noise))
                if face not in ('up', 'down') and h > 1: f *= 1 + grad / 2 - grad * ly / (h - 1)
                if (lx in (0, w - 1) or ly in (0, h - 1)) and w > 2 and h > 2: f *= edge
                img.putpixel((x, y), col(base, f, a))
    return p

def robe(base, trim=None, trim_rows=0, ph=0., torn=0, dark=None):
    """세로 주름 + 아래 금색 단 + (torn) 찢어진 밑단"""
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect; w, h = x2 - x1, y2 - y1
        for y in range(y1, y2):
            for x in range(x1, x2):
                lx, ly = x - x1, y - y1
                f = SHADE[face] * (1 + rng.uniform(-.05, .05))
                c = base; a = 255
                if face not in ('up', 'down'):
                    f *= 1 - .11 * (.5 + .5 * math.sin(lx * 1.7 + ph))
                    f *= 1.06 - .12 * ly / max(1, h - 1)
                    if trim and ly >= h - trim_rows: c = trim; f *= 1.1 if ly == h - trim_rows else .95
                    if torn and ly >= h - torn:
                        k = (lx * 7 + int(ph * 5)) % 5
                        if ly - (h - torn) >= k % (torn + 1): a = 0
                    if dark and ly >= h - 2 and not trim: c = mix(c, dark, .5)
                img.putpixel((x, y), col(c, f, a))
    return p

def gold(base, hi=None):
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect; w, h = x2 - x1, y2 - y1
        for y in range(y1, y2):
            for x in range(x1, x2):
                lx, ly = x - x1, y - y1
                f = SHADE[face] * (1 + rng.uniform(-.05, .05))
                c = base
                if face == 'up' or (ly == 0 and h > 1): f *= 1.18
                elif ly == h - 1 and h > 1: f *= .82
                if hi and rng.random() < .05 and face in ('up', 'north'): c = hi
                img.putpixel((x, y), col(c, f, 255))
    return p

def feather(base, tip=None, shaft=None, tattered=0., ember=None):
    """두께 1의 깃털: 북/남면 아래쪽이 뾰족하게 깎임"""
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect; w, h = x2 - x1, y2 - y1
        for y in range(y1, y2):
            for x in range(x1, x2):
                lx, ly = x - x1, y - y1
                f = SHADE[face] * (1 + rng.uniform(-.06, .06)); c = base; a = 255
                v = ly / max(1, h - 1)
                if tip: c = mix(base, tip, max(0, v - .45) / .55)
                if face in ('north', 'south'):
                    if w >= 2 and lx == w // 2 - (1 if w % 2 == 0 else 0) and v < .9 and shaft: c = shaft
                    rem = h - 1 - ly
                    if w >= 2 and rem < w and abs(lx - (w - 1) / 2) > rem * .7 + .3: a = 0
                    if ly % 3 == 2: f *= .92
                    if tattered and rng.random() < tattered * (.3 + v) and (lx in (0, w - 1)): a = 0
                    if ember and (lx in (0, w - 1)) and rng.random() < .1: c = ember
                img.putpixel((x, y), col(c, f, a))
    return p

def hair(base, dark):
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect; w, h = x2 - x1, y2 - y1
        for y in range(y1, y2):
            for x in range(x1, x2):
                lx, ly = x - x1, y - y1
                f = SHADE[face] * (1 + rng.uniform(-.05, .05)); c = base
                s = math.sin(lx * 1.9 + math.sin(ly * .7))
                if s > .55: c = mix(base, dark, .55)
                if face not in ('up', 'down'): f *= 1.08 - .16 * ly / max(1, h - 1)
                img.putpixel((x, y), col(c, f, 255))
    return p

def stone(base, vein=None, crack=None, moss=None):
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect; w, h = x2 - x1, y2 - y1
        for y in range(y1, y2):
            for x in range(x1, x2):
                lx, ly = x - x1, y - y1
                f = SHADE[face] * (1 + rng.uniform(-.07, .07)); c = base
                if vein and (math.sin(x * .6 + y * .9) + math.sin(x * .23 - y * .41) * 1.3) > 1.75: c = mix(base, vein, .55)
                if crack and rng.random() < .025: c = crack
                if (lx in (0, w - 1) or ly in (0, h - 1)) and w > 2 and h > 2: f *= .9
                if moss and face == 'up' and rng.random() < .1: c = moss
                img.putpixel((x, y), col(c, f, 255))
    return p

def glow(c):
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect
        for y in range(y1, y2):
            for x in range(x1, x2):
                img.putpixel((x, y), col(c, 1 + rng.uniform(-.04, .04), c[3] if len(c) > 3 else 255))
    return p

def decal(fn):
    """fn(u,v)->RGBA or None, u,v in 0..1 (윗면/아랫면 전용 평면 무늬)"""
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect; w, h = x2 - x1, y2 - y1
        for y in range(y1, y2):
            for x in range(x1, x2):
                u = (x - x1 + .5) / w; v = (y - y1 + .5) / h
                if face == 'down': v = 1 - v
                r = fn(u, v, rng)
                img.putpixel((x, y), r if r else (0, 0, 0, 0))
    return p

def with_faces(default, **faces):
    def p(img, face, rect, rng):
        default(img, face, rect, rng)
        if face in faces: faces[face](img, rect, rng)
    return p

def dot(img, rect, lx, ly, c):
    x1, y1, x2, y2 = rect
    if 0 <= lx < x2 - x1 and 0 <= ly < y2 - y1: img.putpixel((x1 + lx, y1 + ly), c if len(c) == 4 else c + (255,))

def P(*args): return args

# ---------------- reusable parts ----------------
def halo(name, cy, r, paint, broken=False, cz=0.):
    """수평 고리(8조각). broken=True면 일부 조각이 빠지고 기울어짐"""
    t = 1
    segs = {
        'front': ([-r + 2, cy, cz - r], [r - 2, cy + t, cz - r + 1]),
        'back': ([-r + 2, cy, cz + r - 1], [r - 2, cy + t, cz + r]),
        'right': ([r - 1, cy, cz - r + 2], [r, cy + t, cz + r - 2]),
        'left': ([-r, cy, cz - r + 2], [-r + 1, cy + t, cz + r - 2]),
        'fr': ([r - 2, cy, cz - r + 1], [r - 1, cy + t, cz - r + 2]),
        'fl': ([-r + 1, cy, cz - r + 1], [-r + 2, cy + t, cz - r + 2]),
        'br': ([r - 2, cy, cz + r - 2], [r - 1, cy + t, cz + r - 1]),
        'bl': ([-r + 1, cy, cz + r - 2], [-r + 2, cy + t, cz + r - 1]),
    }
    kids = []
    skip = {'fl', 'left'} if broken else set()
    for k, (f, to) in segs.items():
        if k in skip: continue
        kids.append(C(f'{name}_{k}', f, to, paint))
    if broken:  # 떨어져 나간 조각이 비스듬히 매달림
        kids.append(Group(f'{name}_shard', [-r + 1, cy, cz - 1], [
            C(f'{name}_shard', [-r, cy - 3, cz - 2], [-r + 1, cy, cz + 2], paint)], [0, 0, -28]))
    return Group(name, [0, cy, cz], kids, [14, 0, -10] if broken else [0, 0, 0])

def wing(side, pre, origin, n, fw, base_len, step, covert_p, feather_p, rot, rise=1.5, z0=0., splay=1.4, back_p=None, second=True):
    """깃털 날개: 안쪽은 낮고 바깥쪽은 높게, 깃털이 아래로 늘어짐. 바깥 깃털일수록 길다."""
    m = side == 'left'
    ox, oy, oz = origin
    kids = []
    for i in range(n):
        x = ox + i * fw; top = oy + i * rise
        kids.append(C(f'{side}_{pre}covert{i}', [x, round(top - 5), oz + z0], [x + fw, round(top + 1), oz + z0 + 1], covert_p, m))
        if i % 2 == 0 and i < n - 1:
            kids.append(C(f'{side}_{pre}covert_f{i}', [x + .5, round(top - 3), oz + z0 - 1], [x + .5 + fw, round(top + 1), oz + z0], back_p or covert_p, m))
        L = base_len + i * step; fy = round(top - 4)
        fe = C(f'{side}_{pre}feather{i}', [x, fy - L, oz + z0 + .5], [x + fw, fy, oz + z0 + 1.5], feather_p, m)
        kids.append(Group(f'{side}_{pre}feather{i}', MP([x + fw / 2, fy, oz + z0 + 1], m), [fe], MR([0, 0, splay * i], m)))
        if second and i < n - 1:
            L2 = max(3, int(L * .62)); x2 = x + fw / 2
            fe2 = C(f'{side}_{pre}second{i}', [x2, fy + 1 - L2, oz + z0 - .5], [x2 + fw, fy + 1, oz + z0 + .5], feather_p, m)
            kids.append(Group(f'{side}_{pre}second{i}', MP([x2 + fw / 2, fy + 1, oz + z0], m), [fe2], MR([0, 0, splay * i * .8], m)))
    # 바깥 끝 아치
    x = ox + n * fw; top = oy + (n - 1) * rise
    kids.append(C(f'{side}_{pre}tip', [x, round(top - 8), oz + z0], [x + 2, round(top + 3), oz + z0 + 1], covert_p, m))
    return Group(f'{side}_{pre}wing', MP([ox, oy, oz], m), kids, MR(rot, m))

def sw(a, L, ph=0):
    """sin 왕복 키프레임(5점)"""
    return [(L * k / 4, a * math.sin(2 * math.pi * k / 4 + ph)) for k in range(5)]

def rot_wave(a, L, axis=0, ph=0., n=8):
    out = []
    for k in range(n + 1):
        t = L * k / n; v = a * math.sin(2 * math.pi * k / n + ph)
        r = [0, 0, 0]; r[axis] = v
        out.append(('rotation', round(t, 4), *r, cm))
    return out

def pos_wave(a, L, axis=1, ph=0., n=8, base=0.):
    out = []
    for k in range(n + 1):
        t = L * k / n; v = base + a * math.sin(2 * math.pi * k / n + ph)
        r = [0, 0, 0]; r[axis] = v
        out.append(('position', round(t, 4), *r, cm))
    return out

def merge(*tracks):
    out = {}
    for tr in tracks:
        for k, v in tr.items(): out.setdefault(k, []).extend(v)
    for k, v in out.items():
        seen = set(); res = []
        for x in sorted(v, key=lambda x: (x[0], x[1])):
            if (x[0], x[1]) in seen: continue
            seen.add((x[0], x[1])); res.append(x)
        out[k] = res
    return out
