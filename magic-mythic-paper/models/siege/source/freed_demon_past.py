"""해방된 악마 · 봉인 전 (freed_demon_past) — 100년 전 전성기의 마왕. 공성전 레이드 최종 보스.
지금 모습(족쇄·부러진 뿔·봉인 흉터)과 달리: 온전한 숫양뿔 한 쌍 + 다섯 갈래 뿔 왕관, 박쥐 날개 네 장,
검은 쇠·금 갑옷, 가슴에 온전한 지옥문 문양, 오른손에 자기 키만 한 마검, 꼿꼿이 선 12블록 거구."""
from common import *
from weapon_ik import solve_hand
import math, os, json

ID = 'freed_demon_past'
PAL = dict(
    SKIN=(152, 28, 30), DSKIN=(76, 12, 18), HL=(200, 64, 48), BELLY=(110, 22, 26),
    FUR=(54, 30, 30), DFUR=(22, 12, 14), HORN=(42, 32, 36), DHORN=(18, 12, 16), HORNTIP=(255, 130, 40),
    BONE=(214, 198, 168), DBONE=(124, 108, 88), ARMOR=(40, 34, 44), DARMOR=(18, 14, 20),
    GOLD=(230, 174, 62), DGOLD=(142, 98, 30), EMBER=(255, 140, 40), EMBER2=(255, 222, 124),
    EYE=(255, 96, 30, 255), EYE_LIE=(200, 96, 255, 255), MEMB=(118, 22, 30), DMEMB=(50, 6, 14),
    CLOTH=(104, 12, 22), DCLOTH=(44, 4, 10), BLADE=(52, 46, 60), BLADE_HI=(150, 140, 160), GUM=(110, 20, 28))
SHINY = dict(
    SKIN=(50, 38, 86), DSKIN=(22, 16, 40), HL=(92, 74, 136), BELLY=(38, 28, 62),
    FUR=(226, 218, 232), DFUR=(150, 140, 166), HORN=(220, 184, 98), DHORN=(140, 104, 44), HORNTIP=(120, 240, 255),
    BONE=(242, 238, 252), DBONE=(152, 148, 172), ARMOR=(214, 208, 224), DARMOR=(130, 124, 150),
    GOLD=(96, 210, 232), DGOLD=(40, 120, 150), EMBER=(90, 220, 255), EMBER2=(214, 250, 255),
    EYE=(255, 226, 90, 255), EYE_LIE=(255, 90, 170, 255), MEMB=(52, 38, 96), DMEMB=(20, 14, 44),
    CLOTH=(30, 40, 110), DCLOTH=(10, 14, 48), BLADE=(226, 230, 240), BLADE_HI=(255, 255, 255), GUM=(60, 30, 90))
HITBOX = (3.8, 12.0, 10.0)
def apply_palette(p): PAL.update(p)

import gen as _gen
def _pack(cubes):   # 거구라 512x512에 안 들어가면 1024까지 허용
    for W, H in [(512, 512), (1024, 512), (1024, 1024)]:
        x = y = rowh = 0; ok = True
        for c in sorted(cubes, key=lambda c: -(c.size[1] + c.size[2])):
            w, h, d = c.size; bw, bh = 2 * (w + d), d + h
            if x + bw > W: x = 0; y += rowh; rowh = 0
            if y + bh > H: ok = False; break
            c.uv = [x, y]; x += bw; rowh = max(rowh, bh)
        if ok: return W, H
    raise Exception('too big')
_gen.pack = _pack

# ---------------- 페인터 ----------------
def flesh(top='HL', bot='DSKIN', crack=0.006, muscle=False):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            g = ly / max(1, h - 1) if face not in ('up', 'down') else .35
            base = lerp(PAL[top], PAL['SKIN'], min(1, g * 2)) if g < .5 else lerp(PAL['SKIN'], PAL[bot], (g - .5) * 2)
            for lx in range(w):
                f = SHADE[face] * (1 + rng.uniform(-.06, .06))
                if min(lx, w - 1 - lx, ly, h - 1 - ly) == 0 and w > 2 and h > 2: f *= .78
                if muscle and face in ('north', 'south') and w >= 6 and (lx == w // 2 or (ly % 7 == 6 and 1 < lx < w - 2)): f *= .84
                img.putpixel((x1 + lx, y1 + ly), col(base, f))
        for _ in range(int(w * h * crack)):      # 불씨 균열
            x = rng.randrange(w); y = rng.randrange(h)
            for s in range(rng.randint(3, 8)):
                if 0 <= x < w and 0 <= y < h: img.putpixel((x1 + x, y1 + y), col(PAL['EMBER2'] if s == 0 else PAL['EMBER'], rng.uniform(.85, 1)))
                x += rng.choice([-1, 0, 1]); y += rng.choice([0, 1, 1])
    return p

def fur(rag=3):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        cut = [rng.randint(0, rag) for _ in range(w)] if face not in ('up', 'down') else [0] * w
        st = [rng.random() for _ in range(w)]
        for lx in range(w):
            for ly in range(h):
                if ly >= h - cut[lx]: img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                c = lerp(PAL['FUR'], PAL['DFUR'], .6 if st[lx] < .3 else (.15 if st[lx] > .8 else .35))
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.12, .12)) * (1.05 - .25 * ly / max(1, h))))
    return p

def hornp(tip=0.0):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                ring = (ly % 4 == 3) if face not in ('up', 'down') else (lx % 4 == 3)
                c = PAL['DHORN'] if ring else PAL['HORN']
                if tip > 0: c = lerp(c, PAL['HORNTIP'], tip * ((1 - ly / max(1, h - 1)) if face not in ('up', 'down') else 1))
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.08, .08))))
    return p

def bone(dark=False):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            g = ly / max(1, h - 1)
            for lx in range(w):
                c = lerp(PAL['DBONE'], PAL['BONE'], .35) if dark else lerp(PAL['BONE'], PAL['DBONE'], g * .6)
                f = SHADE[face] * (1 + rng.uniform(-.08, .08))
                if min(lx, w - 1 - lx, ly, h - 1 - ly) == 0 and w > 2 and h > 2: f *= .82
                img.putpixel((x1 + lx, y1 + ly), col(c, f))
    return p

def armor(runes=0.0, tr=1):
    """검은 쇠 판금 + 금 테두리 + (runes) 불씨 룬 줄."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                e = min(lx, w - 1 - lx, ly, h - 1 - ly)
                c = PAL['GOLD'] if (e < tr and w > 2 and h > 2) else (PAL['DGOLD'] if e == tr and w > 4 and h > 4 else lerp(PAL['ARMOR'], PAL['DARMOR'], ly / max(1, h - 1)))
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.07, .07))))
        if runes and face not in ('up', 'down') and h >= 5:
            ly = h // 2
            for lx in range(2, w - 2):
                if rng.random() < runes:
                    px(img, r, lx, ly, col(PAL['EMBER'])); px(img, r, lx, ly - 1 + 2 * rng.randint(0, 1), col(PAL['EMBER2']))
    return p

def glow(k):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r
        for y in range(y1, y2):
            for x in range(x1, x2): img.putpixel((x, y), col(PAL[k], 1 + rng.uniform(-.04, .04)))
    return p

def banner(sigil=True, depth=6):
    """붉은 깃발 천: 금 테두리 + 지옥문 문양 + 찢긴 밑단."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        cut = [rng.randint(1, depth) for _ in range(w)]
        for ly in range(h):
            for lx in range(w):
                if face not in ('up', 'down') and ly >= h - cut[lx]: img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                c = lerp(PAL['CLOTH'], PAL['DCLOTH'], ly / max(1, h - 1))
                if lx in (0, w - 1) and w > 3: c = PAL['GOLD']
                if lx % 5 == 2: c = lerp(c, PAL['DCLOTH'], .5)
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.06, .06))))
        if sigil and face == 'north' and w >= 10:
            cx, cy, R = (w - 1) / 2, h * .32, w * .32
            for ly in range(h):
                for lx in range(w):
                    d = math.hypot(lx - cx, ly - cy); a = math.atan2(ly - cy, lx - cx)
                    if abs(d - R) < .8 or (d < R and abs(math.sin(a * 2.5 + .6)) < .12 and d > 1.2): px(img, r, lx, ly, col(PAL['GOLD']))
    return p

def gate_sigil(img, r, rng):
    """가슴: 깨지지 않은 지옥문 문양 (지금의 '깨진 봉인 흉터'의 온전한 모습)."""
    w, h = r[2] - r[0], r[3] - r[1]; cx, cy = (w - 1) / 2, (h - 1) / 2; R = min(w, h) / 2 - 1.5
    for ly in range(h):
        for lx in range(w):
            d = math.hypot(lx - cx, ly - cy); a = math.atan2(ly - cy, lx - cx)
            on = abs(d - R) < .9 or abs(d - R * .42) < .7 or (R * .42 < d < R and abs(math.sin(a * 3)) < .1)
            if d < R * .25: px(img, r, lx, ly, col(PAL['EMBER2']))
            elif on: px(img, r, lx, ly, col(PAL['EMBER'] if rng.random() < .8 else PAL['EMBER2']))
            elif d < R: px(img, r, lx, ly, col(PAL['DSKIN'], .8 + .2 * rng.random()))

def skull_face(img, r, rng):
    w, h = r[2] - r[0], r[3] - r[1]
    for sx in (2, w - 11):
        for dx in range(9):
            for dy in range(5, 11):
                if not (dx in (0, 8) and dy in (5, 10)): px(img, r, sx + dx, dy, (22, 4, 8, 255))
    for lx in range(5, w - 5): px(img, r, lx, 2, col(PAL['DSKIN'], .75))
    for ly in range(h): px(img, r, 0, ly, col(PAL['DSKIN'], .7)); px(img, r, w - 1, ly, col(PAL['DSKIN'], .7))

def snout_face(img, r, rng):
    w, h = r[2] - r[0], r[3] - r[1]
    for lx in (2, 3, 4, w - 5, w - 4, w - 3):
        for ly in (2, 3): px(img, r, lx, ly, (20, 4, 8, 255))
    for lx in range(w): px(img, r, lx, h - 1, (30, 6, 10, 255))

def belt_skull(img, r, rng):
    w, h = r[2] - r[0], r[3] - r[1]
    for sx in (1, w - 4):
        for dx in range(3):
            for dy in (3, 4, 5): px(img, r, sx + dx, dy, col(PAL['EMBER'] if (dx, dy) == (1, 4) else PAL['DARMOR']))
    for lx in range(2, w - 2):
        if lx % 2 == 0: px(img, r, lx, h - 3, col(PAL['DARMOR'])); px(img, r, lx, h - 2, col(PAL['DARMOR']))
    px(img, r, w // 2 - 1, 7, col(PAL['DARMOR'])); px(img, r, w // 2, 7, col(PAL['DARMOR']))

WING_POLY = [(0, .66), (.44, .03), (1.0, .22), (.90, .42), (.97, .63), (.80, .70), (.82, .92), (.62, .86), (.56, 1.0), (.30, .88), (0, .98)]
WRIST = (.44, .05); TIPS = [(1.0, .22), (.97, .63), (.82, .92), (.56, 1.0)]
def _inpoly(t, v):
    c = False; n = len(WING_POLY)
    for i in range(n):
        (x1, y1), (x2, y2) = WING_POLY[i], WING_POLY[(i + 1) % n]
        if (y1 > v) != (y2 > v) and t < (x2 - x1) * (v - y1) / (y2 - y1) + x1: c = not c
    return c
def _dseg(t, v, a, b):
    ax, ay = a; bx, by = b; dx, dy = bx - ax, by - ay
    u = max(0, min(1, ((t - ax) * dx + (v - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(t - ax - u * dx, v - ay - u * dy)
def membrane(box):
    """박쥐 날개 판(두께 1): 팔뼈·손가락뼈가 그려진 막. 실루엣 밖은 투명."""
    (X0, Y0, Z0), (X1, Y1, Z1) = box
    ax0, ax1 = min(abs(X0), abs(X1)), max(abs(X0), abs(X1))
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                if face not in ('north', 'south'): img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                x = (X1 - lx - .5) if face == 'north' else (X0 + lx + .5); y = Y1 - ly - .5
                t = (abs(x) - ax0) / (ax1 - ax0); v = (Y1 - y) / (Y1 - Y0)
                if not _inpoly(t, v): img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                c = lerp(PAL['MEMB'], PAL['DMEMB'], min(1, v * .75 + .1))
                db = min([_dseg(t, v, (0, .68), WRIST)] + [_dseg(t, v, WRIST, tp) for tp in TIPS])
                if db < .022: c = PAL['DSKIN']
                elif db < .034: c = lerp(c, PAL['DSKIN'], .6)
                elif not (_inpoly(t + .02, v) and _inpoly(t, v + .025) and _inpoly(t - .02, v) and _inpoly(t, v - .025)): c = lerp(c, PAL['DSKIN'], .7)
                elif rng.random() < .012: c = PAL['EMBER']
                elif (lx + ly * 3) % 11 == 0: c = lerp(c, PAL['MEMB'], .5)
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.05, .05))))
    return p

def blade_p():
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                e = lx in (0, w - 1) and face in ('north', 'south')
                c = PAL['BLADE_HI'] if e else PAL['BLADE']
                if face in ('north', 'south') and (ly + lx * 2) % 17 == 0 and not e: c = lerp(c, PAL['BLADE_HI'], .35)
                if face in ('north', 'south') and ly % 12 == 5 and 2 < lx < w - 3: c = PAL['EMBER'] if (lx + ly) % 3 else PAL['EMBER2']   # 룬
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.05, .05))))
    return p

# ---------------- 모델 ----------------
def build():
    sk = flesh(); skm = flesh(muscle=True); dk = flesh('SKIN', 'DSKIN', .003)
    ar = armor(); arr = armor(.35); gold = sol(PAL, 'GOLD', .08); dgold = sol(PAL, 'DGOLD', .08)
    claw = tipped(PAL, 'DHORN', 'BONE', 4)

    # ===== 머리 =====
    def ram_horn(s):
        m, P, Rt = side(s)
        segs = [([10, 164, -20], [20, 176, -8], 0), ([16, 172, -16], [27, 183, -4], 0), ([22, 177, -8], [33, 187, 3], 0),
                ([27, 173, 1], [37, 183, 12], .15), ([31, 162, 5], [39, 175, 13], .3), ([31, 153, -1], [38, 164, 7], .5),
                ([31, 148, -8], [36, 156, -1], .8), ([31.5, 148, -13], [35.5, 152, -8], 1.0)]
        k = [C(f'{s}_ram_horn{i+1}', a, b, hornp(t), m) for i, (a, b, t) in enumerate(segs)]
        k += [C(f'{s}_horn_ring1', [15.5, 171.5, -16.5], [27.5, 174.5, -3.5], gold, m),
              C(f'{s}_horn_ring2', [26.5, 176, 0.5], [37.5, 179, 12.5], gold, m)]
        return Group(f'{s}_horn', P([12, 168, -14]), k)
    def crown_horn(name, x, base, hts, lean, m=False):
        k = []; y = base
        for i, (wd, ht) in enumerate(hts):
            k.append(C(f'{name}{i+1}', [x - wd / 2, y, -19 - wd / 2 + 2], [x + wd / 2, y + ht, -19 + wd / 2 + 2], hornp(.25 * i + (.5 if i == len(hts) - 1 else 0)), m))
            y += ht
        return Group(name, [-x if m else x, base, -17], k, [-8, 0, lean if m else -lean])
    teeth = []
    for s in ('right', 'left'):
        m = s == 'left'
        teeth += [C(f'{s}_fang', [5, 142, -38], [7, 151, -36], bone(), m), C(f'{s}_tooth1', [7, 146, -36], [9, 151, -34], bone(), m),
                  C(f'{s}_tooth2', [7, 147, -32], [9, 151, -30], bone(), m),
                  C(f'{s}_cheek_spike1', [12, 154, -20], [20, 157, -17], bone(True), m, rot=[0, -25, 10], origin=[12, 155, -18]),
                  C(f'{s}_cheek_spike2', [12, 149, -16], [18, 152, -13], bone(True), m, rot=[0, -20, -10], origin=[12, 150, -14]),
                  C(f'{s}_ear', [12, 158, -12], [15, 166, 0], dk, m, rot=[15, -15, 0], origin=[12, 162, -10]),
                  C(f'{s}_ear_tip', [13, 162, 0], [14, 166, 6], dk, m, rot=[15, -15, 0], origin=[12, 162, -10])]
    jk = [C('jaw', [-9, 142, -36], [9, 149, -10], faces(dk, up=lambda img, r, rng: [img.putpixel((x, y), col(PAL['GUM'], rng.uniform(.85, 1))) for x in range(r[0], r[2]) for y in range(r[1], r[3])])),
          C('jaw_tip', [-6, 140, -38], [6, 144, -32], dk),
          C('beard', [-5, 124, -36], [5, 141, -31], fur(5)), C('beard_ring', [-4, 132, -36.5], [4, 135, -30.5], gold),
          C('chin_spike_r', [6, 134, -30], [9, 142, -27], bone(True), rot=[0, 0, -12]), C('chin_spike_l', [-9, 134, -30], [-6, 142, -27], bone(True), rot=[0, 0, 12])]
    for s in ('right', 'left'):
        m = s == 'left'
        jk += [C(f'{s}_low_fang', [5.5, 149, -37], [7.5, 155, -35], bone(), m), C(f'{s}_low_tooth', [7.5, 149, -32], [9, 153, -30], bone(), m)]
    jaw = Group('jaw', [0, 149, -12], jk)
    def eye_set(name, k, z):
        return Group(name, [0, 161, -28], [
            C(f'right_{name}', [3, 160, z], [10, 163, z], glow(k)), C(f'left_{name}', [-10, 160, z], [-3, 163, z], glow(k)),
            C(f'right_{name}_tail', [10, 162, z], [12, 164, z], glow(k)), C(f'left_{name}_tail', [-12, 162, z], [-10, 164, z], glow(k)),
            C(f'third_{name}', [-1, 164, z], [1, 169, z], glow(k))])
    head = Group('head', [0, 152, -6], [
        C('skull', [-12, 148, -28], [12, 168, -4], faces(sk, north=skull_face)),
        C('skull_back', [-10, 150, -4], [10, 166, 2], dk),
        C('brow', [-13, 163, -30], [13, 168, -24], dk),
        C('brow_spike_r', [6, 167, -30], [9, 173, -27], bone(True), rot=[-25, 0, -15]), C('brow_spike_l', [-9, 167, -30], [-6, 173, -27], bone(True), rot=[-25, 0, 15]),
        C('snout', [-8, 150, -40], [8, 160, -28], faces(sk, north=snout_face)),
        C('nose_bridge', [-4, 159, -39], [4, 163, -28], dk), C('nose_spike', [-1, 163, -35], [1, 167, -32], bone(True)),
        C('crown_band', [-13, 167, -29], [13, 171, -5], armor(.5)),
        C('crown_gem', [-2, 167, -30.5], [2, 172, -29], glow('EMBER2')),
        crown_horn('crown_spire_c', 0, 171, [(5, 12), (4, 12), (3, 10), (2, 8)], 0),
        crown_horn('right_crown_spire1', 7, 171, [(4, 11), (3, 10), (2, 9)], 14), crown_horn('left_crown_spire1', 7, 171, [(4, 11), (3, 10), (2, 9)], 14, True),
        crown_horn('right_crown_spire2', 12, 170, [(3, 9), (2, 9)], 30), crown_horn('left_crown_spire2', 12, 170, [(3, 9), (2, 9)], 30, True),
        jaw, eye_set('eye', 'EYE', -28.15), eye_set('eye_lie', 'EYE_LIE', -28.3), ram_horn('right'), ram_horn('left')] + teeth)
    head.children[-len(teeth) - 4].name = 'eyes'; head.children[-len(teeth) - 3].name = 'eyes_lie'

    # ===== 팔 =====
    def sword():
        return Group('sword', [34, 64, -1], [
            C('sword_grip', [32, 52, -3], [36, 78, 1], stripes(PAL, 'DARMOR', 'DGOLD', 4, 1, vertical=False)),
            C('sword_pommel', [30, 45, -5], [38, 52, 3], armor()), C('sword_pommel_gem', [32, 41, -3], [36, 45, 1], glow('EMBER')),
            C('sword_guard', [18, 78, -5], [50, 85, 3], armor(.4)),
            C('sword_guard_horn_r', [46, 85, -3], [50, 96, 1], hornp(.8)), C('sword_guard_horn_l', [18, 85, -3], [22, 96, 1], hornp(.8)),
            C('sword_guard_skull', [29, 76, -7], [39, 88, -5], faces(bone(), north=belt_skull)),
            C('sword_blade', [26, 85, -2], [42, 178, 0], blade_p()),
            C('sword_blade_mid', [28, 178, -2], [40, 190, 0], blade_p()),
            C('sword_blade_tip', [31, 190, -2], [37, 200, 0], blade_p()), C('sword_blade_point', [33, 200, -2], [35, 206, 0], blade_p()),
            C('sword_fuller', [33, 88, -2.4], [35, 176, 0.4], glow('EMBER')),
            C('sword_barb_r', [42, 150, -2], [46, 160, 0], blade_p(), rot=[0, 0, 0]), C('sword_barb_l', [22, 150, -2], [26, 160, 0], blade_p()),
            C('sword_barb_r2', [42, 120, -2], [45, 128, 0], blade_p()), C('sword_barb_l2', [23, 120, -2], [26, 128, 0], blade_p())], [-84, 0, 8])
    def arm(s):
        m, P, Rt = side(s)
        fingers = []
        for i, z in enumerate((-9, -4.5, 0, 4.5)):
            fingers += [C(f'{s}_finger{i+1}', [36, 47, z], [40, 59, z + 4], dk, m), C(f'{s}_claw{i+1}', [36.5, 35, z + .5], [39.5, 47, z + 3.5], claw, m)]
        hk = [C(f'{s}_palm', [27, 58, -10], [41, 72, 9], sk, m), C(f'{s}_knuckles', [31, 56, -11], [40, 62, 10], dk, m),
              C(f'{s}_thumb', [27, 50, -13], [31, 62, -9], dk, m), C(f'{s}_thumb_claw', [27.5, 41, -12.5], [30.5, 50, -9.5], claw, m)] + fingers
        if s == 'right':   # 마검을 쥔 주먹: 손가락 네 개가 손잡이를 감고, 엄지가 위에서 누른다
            fist = []
            for i, z in enumerate((-9, -4.5, 0, 4.5)):
                fist += [C(f'right_finger{i+1}', [37, 55, z], [42, 67, z + 4], dk), C(f'right_finger{i+1}_tip', [29, 53, z], [38, 58, z + 4], dk),
                         C(f'right_claw{i+1}', [26, 54, z + .5], [29, 57, z + 3.5], claw)]
            hk = [C('right_palm', [27, 58, -10], [38, 72, 9], sk), C('right_knuckles', [36, 60, -11], [43, 70, 10], dk),
                  C('right_thumb', [25, 62, -12], [29, 68, -2], dk), C('right_thumb_claw', [25.5, 62.5, -1.5], [28.5, 65.5, 2.5], claw)] + fist
            hk.append(sword())
        hand = Group(f'{s}_hand', P([34, 72, 0]), hk)
        fore = Group(f'{s}_forearm', P([34, 106, 0]), [
            C(f'{s}_forearm', [27, 70, -8], [41, 106, 7], skm, m),
            C(f'{s}_vambrace', [25.5, 76, -9.5], [42.5, 98, 8.5], arr, m),
            C(f'{s}_vambrace_gem', [32, 84, -10.5], [36, 90, -9.5], glow('EMBER'), m),
            C(f'{s}_arm_blade1', [42, 92, -1.5], [52, 96, 1.5], bone(True), m, rot=[0, 0, 28], origin=[42, 94, 0]),
            C(f'{s}_arm_blade2', [42, 84, -1.5], [50, 88, 1.5], bone(True), m, rot=[0, 0, 28], origin=[42, 86, 0]),
            C(f'{s}_arm_blade3', [42, 77, -1.5], [48, 80, 1.5], bone(True), m, rot=[0, 0, 28], origin=[42, 78, 0]),
            C(f'{s}_elbow_spike', [32, 102, 7], [36, 108, 17], bone(True), m, rot=[-30, 0, 0], origin=[34, 105, 8]), hand],
            [78 if s == 'right' else 34, 0, 0])
        return Group(f'{s}_arm', P([32, 140, 0]), [
            C(f'{s}_pauldron', [24, 134, -15], [46, 152, 13], arr, m),
            C(f'{s}_pauldron_rim', [22, 130, -16], [48, 135, 14], ar, m),
            C(f'{s}_pauldron_tier', [40, 140, -13], [52, 148, 11], ar, m),
            C(f'{s}_pauldron_skull', [29, 136, -18], [41, 148, -15], faces(bone(), north=belt_skull), m),
            C(f'{s}_pauldron_spike1', [28, 152, -9], [32, 168, -5], hornp(.6), m, rot=[-10, 0, -18], origin=[30, 152, -7]),
            C(f'{s}_pauldron_spike2', [36, 151, -2], [40, 166, 2], hornp(.6), m, rot=[0, 0, -30], origin=[38, 151, 0]),
            C(f'{s}_pauldron_spike3', [44, 147, -2], [47, 159, 1], hornp(.6), m, rot=[0, 0, -48], origin=[45, 147, 0]),
            C(f'{s}_pauldron_spike4', [28, 152, 5], [32, 164, 9], hornp(.6), m, rot=[20, 0, -15], origin=[30, 152, 7]),
            C(f'{s}_upper_arm', [26, 104, -9], [42, 138, 8], skm, m),
            C(f'{s}_bicep', [28, 112, -11], [40, 130, -9], sk, m),
            C(f'{s}_arm_band', [25, 108, -10], [43, 112, 9], gold, m), fore], Rt([0, 0, 9] if s == 'left' else [10, 0, 6]))

    # ===== 날개 =====
    def wings(s):
        m, P, Rt = side(s)
        def plate(name, lo, hi, origin, rot, extra):
            b = (lo, hi)
            if m: b = ([-hi[0], lo[1], lo[2]], [-lo[0], hi[1], hi[2]])
            return Group(f'{s}_{name}', P(origin), [Cube(f'{s}_{name}_membrane', b[0], b[1], membrane(b)), *extra], Rt(rot))
        up = plate('wing', [14, 112, 15], [126, 216, 16], [14, 146, 15], [0, -26, 10], [
            C(f'{s}_wing_root', [10, 138, 11], [22, 152, 19], sk, m),
            C(f'{s}_wing_arm', [16, 144, 13.5], [66, 149, 17.5], dk, m, rot=[0, 0, 52], origin=[16, 146.5, 15.5]),
            C(f'{s}_wing_wrist', [59, 205, 13], [67, 213, 18], bone(True), m),
            C(f'{s}_wing_hook', [61, 213, 14], [65, 224, 17], claw, m, rot=[0, 0, 12], origin=[63, 213, 15.5])])
        low = plate('wing2', [14, 44, 17], [100, 124, 18], [14, 118, 17], [0, -32, -14], [
            C(f'{s}_wing2_root', [10, 110, 13], [20, 122, 20], sk, m),
            C(f'{s}_wing2_arm', [16, 96, 15.5], [54, 100, 19.5], dk, m, rot=[0, 0, 52], origin=[16, 98, 17.5]),
            C(f'{s}_wing2_wrist', [49, 116, 15], [55, 122, 20], bone(True), m)])
        return [up, low]

    # ===== 꼬리 =====
    def tail_seg(i, z0, ln, wd, child, rot):
        k = [C(f'tail{i}', [-wd / 2, 88 - wd / 2, z0], [wd / 2, 88 + wd / 2, z0 + ln], sk if i < 3 else dk),
             C(f'tail{i}_spike', [-1.5, 88 + wd / 2, z0 + ln * .3], [1.5, 88 + wd / 2 + 7, z0 + ln * .3 + 5], bone(True), rot=[-30, 0, 0])]
        if i == 5:
            k += [C('tail_blade', [-1, 80, z0 + ln], [1, 96, z0 + ln + 14], blade_p()), C('tail_blade_tip', [-1, 84, z0 + ln + 14], [1, 92, z0 + ln + 22], blade_p()),
                  C('tail_blade_ring', [-3, 85, z0 + ln - 3], [3, 91, z0 + ln], gold)]
        if child: k.append(child)
        return Group(f'tail{i}' if i > 1 else 'tail', [0, 88, z0], k, rot)
    t = None
    for i, (z0, ln, wd, rot) in reversed(list(enumerate([(10, 18, 12, [30, 0, 0]), (28, 18, 10, [-16, 0, 0]), (46, 18, 8, [-16, 0, 0]), (64, 16, 6, [-14, 0, 0]), (80, 14, 4, [-12, 0, 0])], 1))):
        t = tail_seg(i, z0, ln, wd, t, rot)
    tail = t

    # ===== 몸통 =====
    spines = [C(f'spine{i+1}', [-2, y, 12], [2, y + 9, 17], bone(), rot=[-35, 0, 0], origin=[0, y, 14]) for i, y in enumerate([106, 115, 124, 133, 142])]
    abs_ = []
    for row in range(3):
        for s in ('right', 'left'):
            abs_.append(C(f'{s}_ab{row+1}', [1, 97 + row * 5.5, -11], [9, 101.5 + row * 5.5, -9], flesh('SKIN', 'BELLY', 0), s == 'left'))
    chest = Group('chest', [0, 98, 0], [
        C('abdomen', [-15, 96, -9], [15, 114, 9], flesh('SKIN', 'BELLY', .003)),
        C('ribcage', [-26, 112, -14], [26, 142, 13], skm),
        C('core', [-19, 118, -16], [19, 140, -14], faces(flesh('HL', 'SKIN', 0), north=gate_sigil)),
        C('right_ribs', [26, 114, -10], [28, 128, 6], stripes(PAL, 'SKIN', 'DSKIN', 3, 1, vertical=False)),
        C('left_ribs', [-28, 114, -10], [-26, 128, 6], stripes(PAL, 'SKIN', 'DSKIN', 3, 1, vertical=False)),
        C('traps', [-22, 138, -8], [22, 152, 13], sk),
        C('gorget', [-14, 140, -14], [14, 149, 8], arr),
        C('gorget_fang_r', [8, 132, -15.5], [11, 141, -14.5], bone(), rot=[0, 0, 12]), C('gorget_fang_l', [-11, 132, -15.5], [-8, 141, -14.5], bone(), rot=[0, 0, -12]),
        C('neck', [-8, 146, -13], [8, 158, 3], dk),
        C('mane', [-13, 136, 8], [13, 160, 15], fur(5)),
        C('back_plate', [-16, 116, 13], [16, 138, 15], ar)] + abs_ + spines + [head, arm('right'), arm('left'), *wings('right'), *wings('left')])
    tassets = []
    for s in ('right', 'left'):
        m = s == 'left'
        tassets += [C(f'{s}_tasset', [15, 64, -9], [21, 92, 9], arr, m), C(f'{s}_tasset_low', [16, 54, -7], [20, 66, 7], ar, m),
                    C(f'{s}_hip_skull', [9, 82, -14], [17, 91, -12], faces(bone(), north=belt_skull), m)]
    body = Group('body', [0, 86, 0], [
        C('pelvis', [-18, 80, -11], [18, 98, 11], dk),
        C('belt', [-19, 88, -12], [19, 95, 12], armor(.3)),
        C('belt_buckle', [-6, 84, -14], [6, 98, -12], faces(bone(), north=belt_skull)),
        Group('loin_front', [0, 86, -13], [C('loin_front', [-9, 30, -13.5], [9, 86, -12.5], banner(True, 7))]),
        Group('loin_back', [0, 88, 12], [C('loin_back', [-15, 26, 12], [15, 88, 13], banner(False, 9))]),
        chest, tail] + tassets)

    # ===== 다리 =====
    def leg(s):
        m, P, Rt = side(s)
        foot = Group(f'{s}_foot', P([14, 34, 5]), [
            C(f'{s}_hock', [9, 16, 0], [19, 34, 10], dk, m),
            C(f'{s}_heel_spike', [12, 22, 10], [16, 28, 18], bone(True), m),
            C(f'{s}_meta', [9.5, 7, -5], [18.5, 18, 6], dk, m),
            C(f'{s}_ankle_fur', [7, 7, -7], [21, 14, 9], fur(3), m),
            C(f'{s}_ankle_ring', [8, 14, -6], [20, 17, 8], gold, m),
            C(f'{s}_hoof_in', [7, 0, -12], [13, 9, 5], hornp(), m), C(f'{s}_hoof_out', [15, 0, -12], [21, 9, 5], hornp(), m),
            C(f'{s}_dewclaw', [12, 3, 5], [16, 9, 9], hornp(), m)])
        shin = Group(f'{s}_shin', P([14, 60, -4]), [
            C(f'{s}_shin', [8, 32, -4], [20, 60, 7], fur(), m),
            C(f'{s}_greave', [7, 36, -6], [21, 58, -3], arr, m),
            C(f'{s}_knee_plate', [8, 54, -11], [20, 66, -5], ar, m),
            C(f'{s}_knee_spike', [12, 62, -14], [16, 72, -10], hornp(.6), m, rot=[-20, 0, 0], origin=[14, 64, -12]), foot])
        return Group(f'{s}_leg', P([13, 84, 0]), [
            C(f'{s}_thigh', [5, 58, -11], [22, 88, 8], fur(), m),
            C(f'{s}_thigh_fringe', [4, 52, -12], [23, 60, 9], fur(6), m), shin])
    return Group('root', [0, 0, 0], [body, leg('right'), leg('left')])

# ---------------- 애니메이션 ----------------
# 규약: +x = 늘어진 팔다리는 앞으로 / 몸통·머리는 뒤로 젖힘. 앞으로 숙이기 = -x
def anims(G):
    def wings(keys, low=.8):
        return {'right_wing': [R(*k) for k in keys], 'right_wing2': [R(k[0], *[v * low for v in k[1:]]) for k in keys]}
    KN = -30
    kneel = lambda t0, t1: {'right_leg': [R(t0, 70), R(t1, 70)], 'right_shin': [R(t0, -105), R(t1, -105)], 'right_foot': [R(t0, 35), R(t1, 35)]}
    # 등장: 마검에 기대 한 무릎 꿇고 날개로 몸을 덮고 있다가 → 일어서며 네 날개를 펼치고 포효
    spawn = mirror_tracks({
        'body': [T(0, 0, KN), T(1.8, 0, KN), T(2.8, 0, 0), T(4.5)],
        'right_leg': [R(0, 70), R(1.8, 70), R(2.8)], 'right_shin': [R(0, -105), R(1.8, -105), R(2.8)], 'right_foot': [R(0, 35), R(1.8, 35), R(2.8)],
        'chest': [R(0, -34), R(1.8, -34), R(2.6, 20), R(3.8, 16), R(4.5)],
        'head': [R(0, -30), R(1.8, -30), R(2.7, 30), R(3.8, 26), R(4.5)],
        'jaw': [Z(0), R(2.5), R(2.8, -32), R(3.8, -28), R(4.4)],
        'right_wing': [R(0, 0, 80, -24), R(1.8, 0, 80, -24), R(2.7, 0, -14, 30), R(3.8, 0, -8, 26), R(4.5)],
        'right_wing2': [R(0, 0, 84, 6), R(2.0, 0, 84, 6), R(2.9, 0, -12, -14), R(3.8, 0, -8, -10), R(4.5)],
        'left_arm': [R(0, 40, 0, -20), R(1.8, 40, 0, -20), R(2.7, 10, 0, -70), R(3.8, 6, 0, -64), R(4.5)],
        'tail': [R(0, 34), R(1.8, 34), R(2.8, -14), R(4.5)]})
    spawn['right_arm'] = [R(0, 20, 0, 0), R(1.8, 20, 0, 0), R(2.7, 100, 0, 20), R(3.8, 96, 0, 18), R(4.5)]
    spawn['sword'] = [R(0, 0, 0, 0), R(1.8, 0, 0, 0), R(2.7, -30, 0, 0), R(3.8, -26, 0, 0), R(4.5)]
    L = 4.0
    idle = mirror_tracks({
        'chest': [Z(0), R(L / 2, 2.5), Z(L), T(0), T(L / 2, 0, 1), T(L)],
        'head': [R(0, -2, -4), R(L / 2, 2, 4), R(L, -2, -4)], 'jaw': [Z(0), R(L / 2, -5), Z(L)],
        'right_wing': [Z(0), R(L / 2, 0, -5, 7), Z(L)], 'right_wing2': [Z(0), R(L / 2 + .3, 0, -4, -5), Z(L)],
        'right_arm': [Z(0), R(L / 2, -2, 0, 2), Z(L)],
        'tail': osc('rotation', L, -10, 10, 0, 4, 1), 'tail2': osc('rotation', L, -8, 8, 1, 4, 1), 'tail3': osc('rotation', L, -10, 10, 2, 4, 1),
        'tail4': osc('rotation', L, -12, 12, 3, 4, 1),
        'loin_front': [Z(0), R(L / 2, 3), Z(L)], 'loin_back': [Z(0), R(L / 2, -3), Z(L)]})
    idle['sword'] = [Z(0), R(L / 2, 0, 0, 3), Z(L)]; idle['left_hand'] = [Z(0), R(L / 2, 12), Z(L)]
    L = 2.0
    walk = {
        'right_leg': [R(0, 22), R(L / 2, -22), R(L, 22)], 'left_leg': [R(0, -22), R(L / 2, 22), R(L, -22)],
        'right_shin': [Z(0), R(L / 2), R(L * .75, -32), Z(L)], 'left_shin': [Z(0), R(L * .25, -32), R(L / 2), Z(L)],
        'right_foot': [Z(0), R(L / 2), R(L * .75, 20), Z(L)], 'left_foot': [Z(0), R(L * .25, 20), R(L / 2), Z(L)],
        'body': [T(0), T(L * .25, 0, -2), T(L / 2), T(L * .75, 0, -2), T(L), R(0, 0, 4), R(L / 2, 0, -4), R(L, 0, 4)],
        'chest': [R(0, -4), R(L, -4)], 'head': [R(0, 3), R(L * .25, 5), R(L / 2, 3), R(L * .75, 5), R(L, 3)],
        'left_arm': [R(0, 12), R(L / 2, -12), R(L, 12)], 'right_arm': [R(0, -4), R(L / 2, 4), R(L, -4)],
        'right_wing': [R(0, 0, -6, 4), R(L / 2, 0, -6, -2), R(L, 0, -6, 4)], 'left_wing': [R(0, 0, 6, -4), R(L / 2, 0, 6, 2), R(L, 0, 6, -4)],
        'right_wing2': [R(0, 0, -5, -3), R(L / 2, 0, -5, 2), R(L, 0, -5, -3)], 'left_wing2': [R(0, 0, 5, 3), R(L / 2, 0, 5, -2), R(L, 0, 5, 3)],
        'tail': osc('rotation', L, -12, 12, 0, 4, 1), 'tail2': osc('rotation', L, -10, 10, 1, 4, 1), 'tail3': osc('rotation', L, -10, 10, 2, 4, 1),
        'loin_front': [R(0, 6), R(L / 4, -6), R(L / 2, 6), R(L * .75, -6), R(L, 6)]}
    # 기본 공격: 마검 대각 내려베기
    attack = {
        'right_arm': [Z(0), R(.5, 150, 0, 30), R(.68, 10, 0, -30, 'linear'), R(1.0, 6, 0, -32), Z(1.5)],
        'right_forearm': [Z(0), R(.5, -40), R(.68, -30, 0, 0, 'linear'), Z(1.5)],
        'sword': [Z(0), R(.5, -20), R(.68, 70, 0, 0, 'linear'), R(1.0, 74), Z(1.5)],
        'chest': [Z(0), R(.5, 10, -24), R(.68, -20, 28, 0, 'linear'), R(1.0, -18, 26), Z(1.5)],
        'head': [Z(0), R(.5, 8), R(.68, 6, -10, 0, 'linear'), Z(1.5)], 'jaw': [Z(0), R(.6, -24), Z(1.5)],
        'body': [T(0), T(.68, 0, -3, -6, 'linear'), T(1.0, 0, -3, -6), T(1.5)],
        'right_wing': [Z(0), R(.5, 0, -10, 20), R(.68, 0, 16, -6, 'linear'), Z(1.5)], 'left_wing': [Z(0), R(.5, 0, 10, -20), R(.68, 0, -16, 6, 'linear'), Z(1.5)]}
    # 가로 쓸기: 몸을 틀어 마검으로 전방 반원
    sword_sweep = {
        'chest': [Z(0), R(.6, 0, -50), R(.85, -8, 60, 0, 'linear'), R(1.2, -6, 54), Z(1.8)],
        'right_arm': [Z(0), R(.6, 20, 0, 60), R(.85, 30, 0, 40, 'linear'), R(1.2, 30, 0, 36), Z(1.8)],
        'right_forearm': [Z(0), R(.6, -50), R(.85, -60, 0, 0, 'linear'), Z(1.8)],
        'sword': [Z(0), R(.6, 84, 0, 0), R(.85, 84, 0, 0, 'linear'), R(1.2, 84), Z(1.8)],
        'left_arm': [Z(0), R(.6, -20, 0, -30), R(.85, 30, 0, -50, 'linear'), Z(1.8)],
        'body': [R(0), R(.6, 0, -14), R(.85, 0, 16, 0, 'linear'), R(1.8), T(0), T(.85, 0, -2, -4, 'linear'), T(1.8)],
        'tail': [Z(0), R(.6, 0, 30), R(.85, 0, -40, 0, 'linear'), Z(1.8)], 'jaw': [Z(0), R(.8, -22), Z(1.8)]}
    # 내려찍기: 마검을 두 손으로 거꾸로 쥐고 바닥에 꽂음 (지연 후 타격)
    slam = mirror_tracks({
        'chest': [Z(0), R(.7, 18), R(1.3, 20), R(1.45, -34, 0, 0, 'linear'), R(1.9, -32), Z(2.4)],
        'head': [Z(0), R(.7, 16), R(1.45, -6, 0, 0, 'linear'), Z(2.4)], 'jaw': [Z(0), R(.7, -26), R(1.45, -8, 0, 0, 'linear'), Z(2.4)],
        'body': [T(0), T(.7, 0, 3), T(1.45, 0, -10, 0, 'linear'), T(1.9, 0, -10), T(2.4)],
        'right_leg': [Z(0), R(1.45, 24, 0, 0, 'linear'), R(1.9, 24), Z(2.4)], 'right_shin': [Z(0), R(1.45, -36, 0, 0, 'linear'), R(1.9, -36), Z(2.4)],
        'right_wing': [Z(0), R(.7, 0, 0, 28), R(1.45, 0, -22, -12, 'linear'), Z(2.4)], 'right_wing2': [Z(0), R(.7, 0, 0, -18), R(1.45, 0, -18, 8, 'linear'), Z(2.4)]})
    slam['right_arm'] = [Z(0), R(.7, 120, 0, -10), R(1.3, 126, 0, -12), R(1.45, 30, 0, -14, 'linear'), R(1.9, 28, 0, -14), Z(2.4)]
    slam['right_forearm'] = [Z(0), R(.7, -20), R(1.45, -10, 0, 0, 'linear'), Z(2.4)]
    slam['sword'] = [Z(0), R(.7, 170), R(1.3, 172), R(1.45, 176, 0, 0, 'linear'), R(1.9, 176), Z(2.4)]
    slam['left_arm'] = [Z(0), R(.7, 130, 0, 34), R(1.3, 136, 0, 36), R(1.45, 50, 0, 30, 'linear'), R(1.9, 48, 0, 30), Z(2.4)]
    # 왼손 할퀴기
    claw_ = {
        'left_arm': [Z(0), R(.35, -40, 0, -50), R(.5, 84, 0, 20, 'linear'), R(.8, 76, 0, 24), Z(1.2)],
        'left_forearm': [Z(0), R(.35, 30), R(.5, -20, 0, 0, 'linear'), Z(1.2)], 'left_hand': [Z(0), R(.35, -30), R(.5, 25, 0, 0, 'linear'), Z(1.2)],
        'chest': [Z(0), R(.35, 4, 22), R(.5, -14, -26, 0, 'linear'), Z(1.2)], 'jaw': [Z(0), R(.45, -22), Z(1.2)],
        'body': [T(0), T(.5, 0, 0, -4, 'linear'), T(1.2)]}
    dash = mirror_tracks({
        'chest': [Z(0), R(.25, -34), R(.9, -34), Z(1.2)], 'head': [Z(0), R(.25, 24), R(.9, 24), Z(1.2)],
        'right_wing': [Z(0), R(.25, 0, -40, -6), R(.9, 0, -40, -6), Z(1.2)], 'right_wing2': [Z(0), R(.25, 0, -36, 8), R(.9, 0, -36, 8), Z(1.2)],
        'tail': [Z(0), R(.25, -25), R(.9, -25), Z(1.2)]})
    dash.update({'right_leg': [Z(0), R(.25, 25), R(.9, 25), Z(1.2)], 'left_leg': [Z(0), R(.25, -25), R(.9, -25), Z(1.2)],
                 'right_arm': [Z(0), R(.25, -30, 0, 10), R(.9, -30, 0, 10), Z(1.2)], 'sword': [Z(0), R(.25, 80), R(.9, 80), Z(1.2)],
                 'left_arm': [Z(0), R(.25, -50, 0, -15), R(.9, -50, 0, -15), Z(1.2)]})
    # 시전: 왼손을 치켜들고 마검을 앞에 세움, 네 날개 활짝
    cast = mirror_tracks({
        'chest': [Z(0), R(.6, 14), R(2.0, 14), Z(2.6)], 'head': [Z(0), R(.6, 26), R(2.0, 26), Z(2.6)], 'jaw': [Z(0), R(.6, -28), R(2.0, -28), Z(2.6)],
        'right_wing': [Z(0), R(.6, 0, -12, 30), R(2.0, 0, -12, 30), Z(2.6)], 'right_wing2': [Z(0), R(.6, 0, -10, -22), R(2.0, 0, -10, -22), Z(2.6)],
        'body': [T(0), T(.6, 0, 2), T(2.0, 0, 2), T(2.6)]})
    cast['left_arm'] = [Z(0), R(.6, 150, 0, -24), R(1.3, 156, 0, -20), R(2.0, 150, 0, -24), Z(2.6)]
    cast['left_forearm'] = [Z(0), R(.6, -30), R(2.0, -30), Z(2.6)]; cast['left_hand'] = [Z(0), R(.6, -40), R(2.0, -40), Z(2.6)]
    L = 2.4
    taunt = mirror_tracks({
        'chest': [R(0, 10), R(L / 2, 12), R(L, 10)], 'head': [R(0, 8, 0, 10), R(L / 2, 6, 0, -10), R(L, 8, 0, 10)],
        'jaw': [R(0, -8), R(L / 2, -14), R(L, -8)], 'tail': [R(0, -15, -15), R(L / 2, -15, 15), R(L, -15, -15)]})
    taunt['left_arm'] = [R(0, 60, 0, -30), R(L / 2, 66, 0, -24), R(L, 60, 0, -30)]
    taunt['left_hand'] = [R(0, -50), R(L / 4, -20), R(L / 2, -50), R(L * .75, -20), R(L, -50)]
    wing_gust = mirror_tracks({
        'right_wing': [Z(0), R(.6, 0, -55, 10), R(.8, 0, 50, 24, 'linear'), R(1.15, 0, 42, 20), Z(1.7)],
        'right_wing2': [Z(0), R(.65, 0, -50, -6), R(.85, 0, 46, -16, 'linear'), R(1.2, 0, 40, -12), Z(1.7)],
        'chest': [Z(0), R(.6, 16), R(.8, -14, 0, 0, 'linear'), Z(1.7)], 'head': [Z(0), R(.6, 10), R(.8, -4, 0, 0, 'linear'), Z(1.7)]})
    # 포효(광폭화): 웅크렸다가 가슴을 열고 하늘로 포효, 마검을 치켜듦
    roar = mirror_tracks({
        'chest': [Z(0), R(.6, -26), R(1.1, 24), R(1.4, 20), R(1.7, 24), R(2.6, 22), Z(3.2)],
        'head': [Z(0), R(.6, -20), R(1.1, 38), R(1.4, 32), R(1.7, 38), R(2.6, 34), Z(3.2)],
        'jaw': [Z(0), R(.9), R(1.1, -36), R(2.6, -34), Z(3.1)],
        'body': [T(0), T(.6, 0, -6), T(1.1, 0, 2), T(2.6, 0, 2), T(3.2)],
        'right_wing': [Z(0), R(.6, 0, 50, -10), R(1.1, 0, -20, 34), R(1.4, 0, -14, 30), R(1.7, 0, -20, 34), R(2.6, 0, -18, 32), Z(3.2)],
        'right_wing2': [Z(0), R(.6, 0, 50, 6), R(1.1, 0, -18, -24), R(2.6, 0, -16, -22), Z(3.2)],
        'tail': [Z(0), R(.6, 20), R(1.1, -24), R(2.6, -22), Z(3.2)]})
    roar['right_arm'] = [Z(0), R(.6, -10, 0, -10), R(1.1, 110, 0, 30), R(2.6, 106, 0, 30), Z(3.2)]
    roar['sword'] = [Z(0), R(1.1, -24), R(2.6, -24), Z(3.2)]
    roar['left_arm'] = [Z(0), R(.6, 30, 0, 20), R(1.1, 10, 0, -84), R(2.6, 8, 0, -80), Z(3.2)]
    L = 3.0
    groggy = mirror_tracks({
        'body': [T(0, 0, KN), T(L / 2, 0, KN - 1), T(L, 0, KN)],
        'right_leg': [R(0, 70), R(L, 70)], 'right_shin': [R(0, -105), R(L, -105)], 'right_foot': [R(0, 35), R(L, 35)],
        'chest': [R(0, -30), R(L / 2, -34), R(L, -30)], 'head': [R(0, -24, 0, 8), R(L / 2, -28, 0, 10), R(L, -24, 0, 8)],
        'jaw': [R(0, -10), R(L / 2, -16), R(L, -10)],
        'right_wing': [R(0, 0, 30, -40), R(L / 2, 0, 32, -44), R(L, 0, 30, -40)], 'right_wing2': [R(0, 0, 30, 14), R(L, 0, 30, 14)],
        'tail': [R(0, 30), R(L, 30)]})
    groggy['right_arm'] = [R(0, 24, 0, 0), R(L, 24, 0, 0)]; groggy['left_arm'] = [R(0, 30, 0, 10), R(L, 30, 0, 10)]
    groggy['left_leg'] = [R(0, -20), R(L, -20)]; groggy['left_shin'] = [R(0, -20), R(L, -20)]; groggy['left_foot'] = [R(0, 0), R(L, 0)]
    # 사망(봉인): 마검을 떨구고 무릎 꿇음 → 날개가 접히며 앞으로 고꾸라져 가라앉음
    death = mirror_tracks({
        'body': [T(0), T(1.2, 0, KN), T(3.0, 0, KN)],
        'right_leg': [Z(0), R(1.2, 70)], 'right_shin': [Z(0), R(1.2, -105)], 'right_foot': [Z(0), R(1.2, 35)],
        'chest': [Z(0), R(.6, 24), R(1.6, -18), R(3.0, -58)], 'head': [Z(0), R(.6, 34), R(1.6, -18), R(3.0, -34)],
        'jaw': [Z(0), R(.6, -34), R(2.4, -12)],
        'right_wing': [Z(0), R(.6, 0, -16, 30), R(1.8, 0, 30, -30), R(3.0, 0, 50, -52)], 'right_wing2': [Z(0), R(1.8, 0, 30, 10), R(3.0, 0, 50, 24)],
        'left_arm': [Z(0), R(.6, -30, 0, -60), R(1.6, 30, 0, -10), R(3.0, 60, 0, -5)],
        'root': [T(0), T(3.0), T(5.0, 0, -70)], 'tail': [Z(0), R(1.6, 30), R(3.0, 36)]})
    death['right_arm'] = [Z(0), R(.6, 40, 0, 40), R(1.6, -10, 0, 10), R(3.0, 20, 0, 6)]
    death['sword'] = [Z(0), R(.6, -20), R(1.4, 60, 0, 40), R(2.2, 100, 0, 70), R(5.0, 100, 0, 70), T(0), T(1.4, 0, -10, 0), T(2.2, 6, -40, -6), T(5.0, 6, -40, -6)]
    spec = [('spawn', 4.5, 'once', spawn), ('idle', 4.0, 'loop', idle), ('walk', 2.0, 'loop', walk),
            ('attack', 1.5, 'once', attack), ('death', 5.0, 'hold', death),
            ('sword_sweep', 1.8, 'once', sword_sweep), ('slam', 2.4, 'once', slam), ('claw', 1.2, 'once', claw_),
            ('dash', 1.2, 'once', dash), ('cast', 2.6, 'once', cast), ('taunt', 2.4, 'loop', taunt),
            ('wing_gust', 1.7, 'once', wing_gust), ('roar', 3.2, 'once', roar), ('groggy', 3.0, 'loop', groggy)]
    # ---- 공성전 짤패턴 모션 (VFX 팩 Siege_Demon 과 짝). 규약: 몸통·머리 -x = 앞으로 숙임 ----
    def swing(sg):   # 1 대검 3연격의 가로 휘두르기: 0.45초에 벤다. sg = +1 오른쪽에서 왼쪽으로, -1 반대
        return {'chest': [Z(0), R(.3, 0, -48 * sg), R(.45, -8, 58 * sg, 0, 'linear'), R(.7, -6, 52 * sg), Z(1.0)],
                'right_arm': [Z(0), R(.3, 20, 0, 60 if sg > 0 else -20), R(.45, 30, 0, 40 if sg > 0 else 60, 'linear'), R(.7, 30, 0, 36 if sg > 0 else 56), Z(1.0)],
                'right_forearm': [Z(0), R(.3, -50), R(.45, -60, 0, 0, 'linear'), Z(1.0)], 'sword': [Z(0), R(.3, 84), R(.7, 84), Z(1.0)],
                'left_arm': [Z(0), R(.3, -20, 0, -30), R(.45, 30, 0, -50, 'linear'), Z(1.0)],
                'body': [R(0), R(.3, 0, -14 * sg), R(.45, 0, 16 * sg, 0, 'linear'), R(1.0), T(0), T(.45, 0, -2, -4, 'linear'), T(1.0)],
                'tail': [Z(0), R(.3, 0, 30 * sg), R(.45, 0, -40 * sg, 0, 'linear'), Z(1.0)], 'jaw': [Z(0), R(.4, -22), Z(1.0)],
                'right_wing': [Z(0), R(.3, 0, -8, 14), R(.45, 0, 12, -4, 'linear'), Z(1.0)], 'left_wing': [Z(0), R(.3, 0, 8, -14), R(.45, 0, -12, 4, 'linear'), Z(1.0)]}
    swing_r, swing_l = swing(1), swing(-1)
    cleave = {   # 3연격의 마무리: 0.5초에 정면 내려베기
        'right_arm': [Z(0), R(.36, 156, 0, 20), R(.5, 10, 0, -10, 'linear'), R(.85, 6, 0, -12), Z(1.3)], 'right_forearm': [Z(0), R(.36, -40), R(.5, -30, 0, 0, 'linear'), Z(1.3)],
        'sword': [Z(0), R(.36, -20), R(.5, 72, 0, 0, 'linear'), R(.85, 76), Z(1.3)], 'left_arm': [Z(0), R(.36, 140, 0, 30), R(.5, 30, 0, 20, 'linear'), Z(1.3)],
        'chest': [Z(0), R(.36, 14), R(.5, -26, 0, 0, 'linear'), R(.85, -24), Z(1.3)], 'head': [Z(0), R(.36, 10), R(.5, 4, 0, 0, 'linear'), Z(1.3)], 'jaw': [Z(0), R(.45, -26), Z(1.3)],
        'body': [T(0), T(.5, 0, -4, -6, 'linear'), T(.85, 0, -4, -6), T(1.3)],
        'right_wing': [Z(0), R(.36, 0, -10, 22), R(.5, 0, 16, -6, 'linear'), Z(1.3)], 'left_wing': [Z(0), R(.36, 0, 10, -22), R(.5, 0, -16, 6, 'linear'), Z(1.3)]}
    leap = mirror_tracks({   # 2 도약 강타: 웅크림(0.25) → 솟구침 → 0.7초에 내려찍으며 착지
        'root': [T(0), T(.25, 0, -8), T(.45, 0, 70), T(.58, 0, 74), T(.7, 0, -6, 0, 'linear'), T(1.2, 0, -6), T(1.8)],
        'right_leg': [Z(0), R(.25, 40), R(.45, -10), R(.7, 34, 0, 0, 'linear'), R(1.2, 34), R(1.8)], 'right_shin': [Z(0), R(.25, -60), R(.45, -20), R(.7, -50, 0, 0, 'linear'), R(1.2, -50), R(1.8)],
        'right_foot': [Z(0), R(.25, 20), R(.45, 30), R(.7, 16, 0, 0, 'linear'), R(1.8)],
        'chest': [Z(0), R(.25, -24), R(.45, 16), R(.58, 20), R(.7, -36, 0, 0, 'linear'), R(1.2, -34), R(1.8)], 'head': [Z(0), R(.25, 14), R(.58, 14), R(.7, 10, 0, 0, 'linear'), R(1.8)],
        'jaw': [Z(0), R(.5, -30), R(1.2, -12), R(1.8)],
        'right_wing': [Z(0), R(.25, 0, 30, -20), R(.45, 0, -24, 36), R(.58, 0, -10, 40), R(.7, 0, 24, -16, 'linear'), R(1.8)],
        'right_wing2': [Z(0), R(.25, 0, 28, 10), R(.45, 0, -20, -22), R(.7, 0, 20, 10, 'linear'), R(1.8)], 'tail': [Z(0), R(.25, 30), R(.45, -30), R(.7, 24, 0, 0, 'linear'), R(1.8)]})
    leap['right_arm'] = [Z(0), R(.25, -20), R(.45, 130, 0, -10), R(.58, 136, 0, -12), R(.7, 30, 0, -14, 'linear'), R(1.2, 28, 0, -14), Z(1.8)]
    leap['sword'] = [Z(0), R(.45, 170), R(.58, 172), R(.7, 176, 0, 0, 'linear'), R(1.2, 176), Z(1.8)]
    leap['left_arm'] = [Z(0), R(.25, -20), R(.45, 136, 0, 34), R(.58, 140, 0, 36), R(.7, 50, 0, 30, 'linear'), R(1.2, 48, 0, 30), Z(1.8)]
    sword_throw = {   # 4 마검 투척: 0.4초에 손을 떠난다(마검 본이 사라짐). 끝 자세 유지 → sword_recall 로 이어 받는다
        'chest': [Z(0), R(.25, 12, -30), R(.4, -22, 34, 0, 'linear'), R(.8, -10, 14), R(1.2, -4, 6)],
        'right_arm': [Z(0), R(.25, -50, 0, 30), R(.4, 110, 0, 10, 'linear'), R(.8, 60, 0, 10), R(1.2, 30, 0, 14)], 'right_forearm': [Z(0), R(.25, 20), R(.4, -60, 0, 0, 'linear'), R(1.2, -40)],
        'sword': [Z(0), R(.25, -60), R(.4, 40, 0, 0, 'linear'), S(0), S(.38, 1, 1, 1), S(.42, 0, 0, 0, 'linear'), S(1.2, 0, 0, 0)],
        'left_arm': [Z(0), R(.25, 40, 0, -40), R(.4, -20, 0, -30, 'linear'), R(1.2, 0, 0, -10)], 'jaw': [Z(0), R(.4, -28), R(1.2, -8)],
        'body': [T(0), T(.25, 0, 0, 4), T(.4, 0, -3, -6, 'linear'), T(1.2, 0, 0, 0)]}
    sword_recall = {   # 마검 회수: 손을 뻗고(0.2), 0.6초에 마검이 돌아와 잡힌다
        'chest': [R(0, -4, 6), R(.2, -10, 0), R(.6, 12, -10, 0, 'linear'), R(.9, 6, -6), Z(1.4)],
        'right_arm': [R(0, 30, 0, 14), R(.2, 96, 0, 6), R(.55, 96, 0, 6), R(.66, 40, 0, 20, 'linear'), R(.9, 30, 0, 14), Z(1.4)], 'right_forearm': [R(0, -40), R(.2, -78), R(.55, -78), R(.66, -30, 0, 0, 'linear'), Z(1.4)],
        'sword': [S(0, 0, 0, 0), S(.55, 0, 0, 0), S(.6, 1.15, 1.15, 1.15, 'linear'), S(.75, 1, 1, 1), R(0), R(.6, 30), R(.9, -10), Z(1.4)],
        'left_arm': [R(0, 0, 0, -10), Z(1.4)], 'jaw': [R(0, -8), R(.6, -20), Z(1.4)], 'body': [T(0), T(.62, 0, 0, 3, 'linear'), T(1.4)]}
    fissure = mirror_tracks({   # 5 지면 가르기: 0.5초에 마검을 거꾸로 꽂고 1.4초까지 버틴다(균열이 달려감)
        'chest': [Z(0), R(.3, 14), R(.5, -32, 0, 0, 'linear'), R(1.4, -30), Z(2.0)], 'head': [Z(0), R(.3, 10), R(.5, 14, 0, 0, 'linear'), R(1.4, 16), Z(2.0)], 'jaw': [Z(0), R(.5, -24), R(1.4, -30), Z(2.0)],
        'body': [T(0), T(.3, 0, 2), T(.5, 0, -9, 0, 'linear'), T(1.4, 0, -9), T(2.0)],
        'right_leg': [Z(0), R(.5, 22, 0, 0, 'linear'), R(1.4, 22), Z(2.0)], 'right_shin': [Z(0), R(.5, -34, 0, 0, 'linear'), R(1.4, -34), Z(2.0)],
        'right_wing': [Z(0), R(.3, 0, 0, 24), R(.5, 0, -20, -10, 'linear'), R(1.4, 0, -16, -6), Z(2.0)]})
    fissure['right_arm'] = [Z(0), R(.3, 120, 0, -10), R(.5, 30, 0, -14, 'linear'), R(1.4, 28, 0, -14), Z(2.0)]
    fissure['sword'] = [Z(0), R(.3, 168), R(.5, 176, 0, 0, 'linear'), R(1.4, 176), Z(2.0)]
    fissure['left_arm'] = [Z(0), R(.3, 126, 0, 34), R(.5, 50, 0, 30, 'linear'), R(1.4, 48, 0, 30), Z(2.0)]
    breath = mirror_tracks({   # 6 지옥불 숨결: 들이마시고(0.3) 0.45~1.9초 동안 고개를 한쪽에서 반대쪽으로 훑는다
        'right_wing': [Z(0), R(.3, 0, -20, 20), R(1.9, 0, -24, 16), Z(2.4)], 'right_wing2': [Z(0), R(.3, 0, -18, -12), R(1.9, 0, -20, -10), Z(2.4)],
        'right_arm': [Z(0), R(.3, -10, 0, 20), R(1.9, -14, 0, 26), Z(2.4)], 'tail': [Z(0), R(.3, 20), R(1.9, 24), Z(2.4)]})
    breath['chest'] = [Z(0), R(.3, 16, -30), R(.45, -12, -40, 0, 'linear'), R(1.9, -12, 40, 0, 'linear'), Z(2.4)]
    breath['head'] = [Z(0), R(.3, 22, -30), R(.45, -6, -44, 0, 'linear'), R(1.9, -6, 44, 0, 'linear'), Z(2.4)]
    breath['jaw'] = [Z(0), R(.3, -6), R(.45, -38, 0, 0, 'linear'), R(1.9, -36), Z(2.3)]
    pillar_cast = {   # 7 추적 불기둥: 왼손을 내밀고 0.4초마다 손목을 치켜올린다 (6번)
        'left_arm': [Z(0), R(.25, 70, 0, -20), R(2.6, 70, 0, -20), Z(3.0)], 'left_forearm': [Z(0), R(.25, -30), R(2.6, -30), Z(3.0)],
        'left_hand': [Z(0)] + [k for i in range(6) for k in (R(round(.5 + i * .4, 2), 30), R(round(.7 + i * .4, 2), -50, 0, 0, 'linear'))] + [Z(3.0)],
        'chest': [Z(0), R(.25, -6, 10), R(2.6, -6, 10), Z(3.0)], 'head': [Z(0), R(.25, 4, 8), R(2.6, 4, 8), Z(3.0)], 'jaw': [Z(0), R(.25, -10), R(2.6, -10), Z(3.0)]}
    doom = mirror_tracks({   # 10 종말의 일격: 1.3초 동안 마검을 치켜들었다가 1.5초에 전장 절반을 내려친다
        'chest': [Z(0), R(.7, 20), R(1.35, 24), R(1.5, -40, 0, 0, 'linear'), R(2.0, -38), Z(2.6)], 'head': [Z(0), R(.7, 18), R(1.5, -6, 0, 0, 'linear'), Z(2.6)], 'jaw': [Z(0), R(.7, -30), R(1.5, -10, 0, 0, 'linear'), Z(2.6)],
        'body': [T(0), T(.7, 0, 4), T(1.5, 0, -12, -4, 'linear'), T(2.0, 0, -12, -4), T(2.6)],
        'right_leg': [Z(0), R(1.5, 26, 0, 0, 'linear'), R(2.0, 26), Z(2.6)], 'right_shin': [Z(0), R(1.5, -40, 0, 0, 'linear'), R(2.0, -40), Z(2.6)],
        'right_wing': [Z(0), R(.7, 0, -6, 34), R(1.35, 0, -8, 38), R(1.5, 0, -24, -16, 'linear'), Z(2.6)], 'right_wing2': [Z(0), R(.7, 0, -4, -22), R(1.5, 0, -18, 10, 'linear'), Z(2.6)]})
    doom['right_arm'] = [Z(0), R(.7, 150, 0, -10), R(1.35, 156, 0, -12), R(1.5, 20, 0, -14, 'linear'), R(2.0, 18, 0, -14), Z(2.6)]
    doom['sword'] = [Z(0), R(.7, -30), R(1.35, -34), R(1.5, 70, 0, 0, 'linear'), R(2.0, 72), Z(2.6)]
    doom['left_arm'] = [Z(0), R(.7, 150, 0, 34), R(1.35, 156, 0, 36), R(1.5, 30, 0, 26, 'linear'), R(2.0, 28, 0, 26), Z(2.6)]
    spec += [('swing_r', 1.0, 'once', swing_r), ('swing_l', 1.0, 'once', swing_l), ('cleave', 1.3, 'once', cleave), ('leap', 1.8, 'once', leap),
             ('sword_throw', 1.2, 'hold', sword_throw), ('sword_recall', 1.4, 'once', sword_recall), ('fissure', 2.0, 'once', fissure), ('breath', 2.4, 'once', breath),
             ('pillar_cast', 3.0, 'once', pillar_cast), ('doom', 2.6, 'once', doom), ('gate_strike', 2.4, 'once', {k: list(v) for k, v in slam.items()})]
    # ---- 마검: 손에 고정, 손목이 마검 방향을 맞춘다 (weapon_ik). (t, phi, pitch[, 날 넓은 면 법선]) / phi 0=정면 +=오른쪽, pitch +=위 ----
    X, Yn = (1, 0, 0), (0, 1, 0); U = 'rest'
    WT = {
        'spawn': [(0, 0, -72, Yn), (1.8, 0, -72), (2.7, 8, 82, (0, 0, -1)), (3.8, 8, 82), (4.5, U)],
        'attack': [(0, U), (.5, 0, 150, X), (.68, -10, -20), (1.0, -10, -26), (1.5, U)],
        'sword_sweep': [(0, U), (.6, 118, -4, Yn), (.85, -70, -8), (1.2, -80, -8), (1.8, U)],
        'slam': [(0, U), (.7, 0, -76, (0, 0, -1)), (1.3, 0, -78), (1.45, 0, -86), (1.9, 0, -86), (2.4, U)],
        'dash': [(0, U), (.25, 172, -8, Yn), (.9, 172, -8), (1.2, U)],
        'groggy': [(0, 0, -78, X)],
        'death': [(0, U), (.6, U), (1.6, -50, 0, Yn), (3.0, -70, -6), (5.0, -70, -6)],
        'swing_r': [(0, U), (.3, 118, -4, Yn), (.45, -66, -8), (.7, -78, -8), (1.0, U)],
        'swing_l': [(0, U), (.3, -104, -4, Yn), (.45, 70, -8), (.7, 82, -8), (1.0, U)],
        'cleave': [(0, U), (.36, 0, 152, X), (.5, 0, -22), (.85, 0, -28), (1.3, U)],
        'leap': [(0, U), (.25, U), (.45, 0, -74, (0, 0, -1)), (.58, 0, -76), (.7, 0, -86), (1.2, 0, -86), (1.8, U)],
        'sword_throw': [(0, U), (.25, 168, 24, Yn), (.4, 0, -4), (1.2, 0, -4)],
        'sword_recall': [(0, 0, -4, Yn), (.6, 0, 16), (.9, 0, 66), (1.4, U)],
        'fissure': [(0, U), (.3, 0, -76, (0, 0, -1)), (.5, 0, -86), (1.4, 0, -86), (2.0, U)],
        'doom': [(0, U), (.7, 0, 146, X), (1.35, 0, 154), (1.5, 0, -24), (2.0, 0, -30), (2.6, U)]}
    WT['gate_strike'] = WT['slam']
    root = [g for g in G if g.name == 'root'][0]
    for name, ln, lp, tr in spec:
        w = solve_hand(root, tr, ln, ['root', 'body', 'chest', 'right_arm', 'right_forearm'], 'right_hand', 'sword', WT.get(name))
        WRIST_DEV[name] = round(w)
    return build_anims(f'animation.{ID}.', spec, G)
WRIST_DEV = {}

def hide_groups(path, names):
    """거짓 눈(eyes_lie)은 Blockbench에서 숨김 상태로 저장 (서버에서 partvisibility로 켬)."""
    m = json.load(open(path)); el = {e['uuid']: e for e in m['elements']}
    def walk(g):
        for c in g['children']:
            if isinstance(c, dict):
                if c['name'] in names:
                    c['visibility'] = False
                    for u in c['children']:
                        if isinstance(u, str): el[u]['visibility'] = False
                walk(c)
    for g in m['outliner']: walk(g)
    json.dump(m, open(path, 'w'), indent=1, ensure_ascii=False)

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    export(ID, build(), anims, 51, O + ID + '.bbmodel', HITBOX); hide_groups(O + ID + '.bbmodel', ['eyes_lie'])
    apply_palette(SHINY)
    export(ID + '_shiny', build(), anims, 51, O + ID + '_shiny.bbmodel', HITBOX); hide_groups(O + ID + '_shiny.bbmodel', ['eyes_lie'])
