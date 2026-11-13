"""벨자르 · 봉인 전 (belzar_past) — 100년 전 전성기. 반은 천사, 반은 악마인 여섯 날개 세라핌.
오른쪽(+x)은 흰 날개·금 저울, 왼쪽(-x)은 불씨 박힌 검은 날개·장부. 팔 넷(아래 한 쌍은 기도), 이중 후광, 꼬리."""
from common import *
import boss as B
import os
from PIL import Image

ID = 'belzar_past'
PAL = dict(
    A_ROBE=(240, 236, 226), A_ROBE_D=(198, 192, 180), GOLD=(232, 188, 80), GOLD_HI=(255, 240, 170),
    A_SKIN=(246, 224, 206), A_HAIR=(244, 220, 156), A_HAIR_D=(208, 172, 100),
    A_FEA=(250, 248, 242), A_FEA_TIP=(216, 206, 186), A_SHAFT=(216, 202, 170), A_COV=(242, 240, 232),
    D_ROBE=(54, 24, 38), D_ROBE_D=(24, 10, 18), D_GOLD=(156, 62, 40), D_GOLD_HI=(240, 120, 60),
    D_SKIN=(112, 88, 104), D_HAIR=(34, 26, 34), D_HAIR_D=(14, 10, 14),
    D_FEA=(40, 22, 30), D_FEA_TIP=(176, 50, 26), D_SHAFT=(96, 40, 36), D_COV=(54, 30, 40), EMBER=(244, 100, 36),
    HORN=(62, 42, 44), HORN_TIP=(22, 14, 16), A_HORN=(226, 180, 76), A_HORN_TIP=(255, 240, 170),
    EYE=(255, 84, 40, 255), CLAW=(22, 16, 18), LEDGER=(122, 28, 30), PAGE=(224, 208, 174),
    LINE=(30, 10, 14), LIP=(232, 150, 150), D_LIP=(40, 10, 14), GEM=(255, 246, 200, 255), D_GEM=(255, 70, 40, 255),
    CHAIN=(88, 84, 90), LASH=(96, 62, 48), IRIS=(206, 150, 52), BLUSH=(246, 186, 180), IRON=(44, 34, 40))
SHINY = dict(PAL,
    A_ROBE=(220, 226, 244), A_ROBE_D=(164, 172, 206), GOLD=(196, 206, 226), GOLD_HI=(244, 250, 255),
    A_HAIR=(226, 234, 250), A_HAIR_D=(156, 170, 210), A_FEA=(236, 246, 255), A_FEA_TIP=(140, 184, 240),
    A_SHAFT=(170, 198, 234), A_COV=(224, 236, 252),
    D_ROBE=(20, 30, 62), D_ROBE_D=(8, 12, 30), D_GOLD=(60, 120, 170), D_GOLD_HI=(120, 220, 255),
    D_SKIN=(84, 98, 132), D_FEA=(18, 26, 50), D_FEA_TIP=(40, 150, 220), D_SHAFT=(40, 70, 110), D_COV=(26, 36, 64),
    EMBER=(80, 210, 255), HORN=(210, 204, 190), HORN_TIP=(146, 138, 122), A_HORN=(190, 200, 222), A_HORN_TIP=(244, 250, 255),
    EYE=(190, 110, 255, 255), LEDGER=(30, 42, 96), GEM=(200, 236, 255, 255), D_GEM=(150, 90, 255, 255),
    IRIS=(96, 150, 226), LASH=(70, 74, 110), IRON=(30, 36, 56))
HITBOX = (1.8, 4.6, 3.45)
LIFT = 12
def apply_palette(p): globals().update(p)
apply_palette(PAL)

# ---------------- 도우미 ----------------
def mirror_node(n):
    """그룹 트리를 x 대칭으로 뒤집음 (저울을 오른손으로 옮길 때)."""
    n.origin = mirror_x(n.origin); n.rot = [n.rot[0], -n.rot[1], -n.rot[2]]
    if isinstance(n, Cube):
        n.frm, n.to = [-n.to[0], n.frm[1], n.frm[2]], [-n.frm[0], n.to[1], n.to[2]]
    else:
        for c in n.children: mirror_node(c)
    return n

def move(n, d):
    n.origin = [a + b for a, b in zip(n.origin, d)]
    if isinstance(n, Cube):
        n.frm = [a + b for a, b in zip(n.frm, d)]; n.to = [a + b for a, b in zip(n.to, d)]
    else:
        for c in n.children: move(c, d)
    return n

JAG = [0, 1, 1, 0, -1, 0, 1, 0, -1, -1, 0, 1]
def SP(name, frm, to, pa, pb, seam=True):
    """가운데 걸친 큐브: +x 쪽은 pa(천사), -x 쪽은 pb(악마). 경계는 들쭉날쭉 + 불씨 금."""
    X0, X1 = frm[0], to[0]
    def p(img, face, rect, rng):
        pa(img, face, rect, rng)
        if face == 'east': return
        tmp = Image.new('RGBA', img.size, (0, 0, 0, 0)); pb(tmp, face, rect, rng)
        x1, y1, x2, y2 = rect
        for y in range(y1, y2):
            for x in range(x1, x2):
                lx, ly = x - x1, y - y1
                if face == 'west': wx = -99
                elif face == 'south': wx = X0 + lx + .5
                else: wx = X1 - lx - .5
                j = JAG[ly % len(JAG)] if face in ('north', 'south') else JAG[(ly + 3) % len(JAG)] * .5
                if wx < j:
                    c = tmp.getpixel((x, y))
                    if seam and wx > j - 1 and c[3] and rng.random() < .45: c = col(EMBER, 1.05)
                    img.putpixel((x, y), c)
    return Cube(name, frm, to, p)

def split_face(img, r, rng):
    # lx 0..3 = 천사(모델 오른쪽), 4..7 = 악마
    for lx in (0, 1, 2): dot(img, r, lx, 3, LASH)
    dot(img, r, 1, 4, (252, 250, 246)); dot(img, r, 2, 4, IRIS); dot(img, r, 1, 5, BLUSH); dot(img, r, 3, 6, LIP)
    for lx in (5, 6, 7): dot(img, r, lx, 3, LINE)
    dot(img, r, 5, 4, EYE[:3]); dot(img, r, 6, 4, EYE[:3])
    for lx in (4, 5, 6): dot(img, r, lx, 6, D_LIP)
    dot(img, r, 5, 6, (236, 226, 210)); dot(img, r, 7, 5, EMBER); dot(img, r, 6, 1, EMBER)

def iron_p():
    def p(img, face, rect, rng):
        x1, y1, x2, y2 = rect
        for y in range(y1, y2):
            for x in range(x1, x2):
                c = EMBER if rng.random() < .16 else IRON
                img.putpixel((x, y), col(c, SHADE[face] * (1 + rng.uniform(-.06, .06))))
    return p

# ---------------- 파츠 ----------------
def side_cols(s):
    if s == 'right': return dict(ROBE=A_ROBE, ROBE_D=A_ROBE_D, G=GOLD, GH=GOLD_HI, SKIN=A_SKIN, demon=False)
    return dict(ROBE=D_ROBE, ROBE_D=D_ROBE_D, G=D_GOLD, GH=D_GOLD_HI, SKIN=D_SKIN, demon=True)

def arm(s, tier):
    m = s == 'left'; K = side_cols(s); n = '' if tier == 1 else '2'; demon = K['demon']
    B.apply_palette(dict(SKIN=K['SKIN'], CLAW=CLAW, GOLD=GOLD, GOLD_HI=GOLD_HI, GEM=GEM if not demon else D_GEM,
                         LEDGER=LEDGER, PAGE=PAGE, CHAIN=CHAIN))
    h = B.hand(s, demon)
    for c in all_cubes(h): c.name = c.name.replace(f'{s}_', f'{s}_h{tier}_')
    h.name = f'{s}_hand{n}'
    if tier == 1:
        if s == 'right':
            B.apply_palette(dict(GEM=GEM)); h.children.append(mirror_node(B.scales()))
        else:
            B.apply_palette(dict(GEM=D_GEM)); h.children.append(B.ledger_book())
    sl = robe(K['ROBE'], K['G'], 1, ph=1 + m + tier, torn=0)
    g = gold(K['G'], K['GH'])
    fr = ((62 if s == 'right' else 24) if tier == 1 else 78)
    fore = Group(f'{s}_forearm{n}', MP([6.5, 29, 0], m), [
        C(f'{s}_forearm{n}', [5, 22, -1.5], [8, 29, 1.5], shaded(K['SKIN']), m),
        C(f'{s}_sleeve_bell{n}', [4, 23, -2.5], [9, 29, 2.5], sl, m),
        C(f'{s}_cuff{n}', [4.5, 22, -2], [8.5, 23, 2], g, m), h], [fr, 0, 0])
    k = [C(f'{s}_upper_arm{n}', [5, 28, -1.5], [8, 34, 1.5], robe(K['ROBE_D'], ph=3), m), fore]
    if tier == 1:
        k += [C(f'{s}_shoulder', [4.5, 33, -2.5], [8.5, 38, 2.5], robe(K['ROBE'], ph=2), m),
              C(f'{s}_pauldron', [4.5, 37, -3], [10, 39, 3], g, m),
              C(f'{s}_pauldron_rim', [8, 35, -3.5], [11, 37, 3.5], g, m),
              C(f'{s}_pauldron_crest', [7, 39, -.5], [8, 43, .5], shaded(HORN) if demon else g, m, rot=[0, 0, -22], origin=[7.5, 39, 0])]
        rot = [22, 0, 7] if s == 'right' else [6, 0, 9]
    else:
        k += [C(f'{s}_shoulder2', [4.5, 32, -2], [8, 35, 2], robe(K['ROBE'], ph=4), m)]
        rot = [30, 0, -16]
    G_ = Group(f'{s}_arm{n}', MP([6.5, 37, 0], m), k, MR(rot, m))
    if tier == 2: move(G_, [-1.5 if s == 'right' else 1.5, -6, -1])
    return G_

def horn(s):
    if s == 'right': B.apply_palette(dict(HORN=A_HORN, HORN_TIP=A_HORN_TIP))
    else: B.apply_palette(dict(HORN=HORN, HORN_TIP=HORN_TIP))
    return B.horn(s)

def outer_halo(cy, r, cz):
    """가시 돋친 바깥 고리 — 금 후광을 감싼 검은 쇠 고리."""
    p = iron_p(); k = []
    k.append(C('halo_outer_front', [-r + 2, cy, cz - r], [r - 2, cy + 1, cz - r + 1], p))
    k.append(C('halo_outer_back', [-r + 2, cy, cz + r - 1], [r - 2, cy + 1, cz + r], p))
    k.append(C('halo_outer_right', [r - 1, cy, cz - r + 2], [r, cy + 1, cz + r - 2], p))
    k.append(C('halo_outer_left', [-r, cy, cz - r + 2], [-r + 1, cy + 1, cz + r - 2], p))
    for i, (sx, sz) in enumerate([(1, 1), (-1, 1), (1, -1), (-1, -1)]):
        x0 = (r - 2) if sx > 0 else (-r + 1); z0 = cz + ((r - 2) if sz > 0 else (-r + 1))
        k.append(C(f'halo_outer_corner{i}', [x0, cy, z0], [x0 + 1, cy + 1, z0 + 1], p))
    sp = shaded(HORN_TIP)
    for i, (x, z) in enumerate([(0, -r), (r - .5, 0), (0, r - 1), (-r + .5, 0), (r - 2.5, -r + 2), (-r + 2.5, -r + 2), (r - 2.5, r - 2.5), (-r + 2.5, r - 2.5)]):
        hh = 4 if i < 4 else 2
        k.append(C(f'halo_spike{i}', [x - .5, cy + 1, cz + z], [x + .5, cy + 1 + hh, cz + z + 1], sp))
    return Group('halo_outer', [0, cy, cz], k, [8, 0, 0])

def build():
    apply_palette({k: globals()[k] for k in PAL})
    rA = lambda ph=0, tr=0: robe(A_ROBE, GOLD, tr, ph)
    rD = lambda ph=0, tr=0: robe(D_ROBE, D_GOLD, tr, ph, dark=D_ROBE_D)
    rAd = lambda ph=0, tr=0: robe(A_ROBE_D, GOLD, tr, ph)
    rDd = lambda ph=0, tr=0: robe(D_ROBE_D, D_GOLD, tr, ph)
    gA = gold(GOLD, GOLD_HI); gD = gold(D_GOLD, D_GOLD_HI)
    skirt = [
        SP('hem', [-8, 4, -6], [8, 9, 6], rA(0), rD(0)),
        SP('skirt_lower', [-7, 9, -5], [7, 17, 5], rA(1), rD(1)),
        SP('skirt_mid', [-6, 17, -4.5], [6, 24, 4.5], rA(2), rD(2)),
        SP('waist', [-5, 24, -4], [5, 29, 4], rA(3), rD(3)),
        SP('sash', [-5.5, 28, -4.5], [5.5, 30, 4.5], gA, gD, False),
        SP('sash_tail', [-1, 16, -5.5], [1, 28, -4.5], gA, gD, False),
        C('right_fold', [7, 9, -1], [8, 17, 1], rAd(5)), C('left_fold', [-8, 9, -1], [-7, 17, 1], rDd(6)),
        C('right_hip_plate', [5, 20, -3], [7, 27, 3], gA), C('left_hip_plate', [-7, 20, -3], [-5, 27, 3], gD),
        SP('train', [-6, 3, 6], [6, 8, 9], rA(9), rD(9)),
    ]
    torso = [
        SP('chest', [-4.5, 29, -3], [4.5, 38, 3], rA(1), rD(1)),
        SP('breastplate', [-3, 31, -4], [3, 37, -3], gA, gD, False),
        C('heart_gem', [-1, 33, -5], [1, 35, -4], glow(GEM)),
        SP('mantle', [-6, 35, -3.5], [6, 38, 3.5], rA(2, 1), rD(2, 1)),
        C('mantle_front_r', [3, 32, -4.5], [5, 36, -3.5], rA(4, 1)), C('mantle_front_l', [-5, 32, -4.5], [-3, 36, -3.5], rD(3, 1)),
        SP('collar', [-3, 38, -3.5], [3, 39, 2.5], gA, gD, False),
        SP('high_collar', [-3.5, 39, 1.5], [3.5, 43, 3.5], rAd(1), rDd(1)),
        SP('neck', [-1.5, 38, -1.5], [1.5, 41, 1.5], shaded(A_SKIN), shaded(D_SKIN)),
        SP('back', [-4, 30, 3], [4, 37, 4], rAd(5), rDd(5)),
        C('waist_chain', [-5.5, 26, -4.5], [0, 27, 4.5], shaded(CHAIN)),
        C('waist_chain_hang', [-4, 20, -5], [-3, 26, -4], shaded(CHAIN)),
        C('censer_chain', [3, 21, -5], [4, 26, -4], gA), C('censer', [2.5, 18, -5.5], [4.5, 21, -3.5], gA),
    ]
    hk = [SP('skull', [-4, 40, -4], [4, 48, 4], shaded(A_SKIN, noise=.025, grad=0, edge=1), shaded(D_SKIN, grad=.06)),
          SP('hair_cap', [-4.5, 46, -4.5], [4.5, 49, 4.5], hair(A_HAIR, A_HAIR_D), hair(D_HAIR, D_HAIR_D), False),
          C('right_fringe', [1.5, 45, -5], [4.5, 47, -4], hair(A_HAIR, A_HAIR_D)),
          C('left_fringe', [-4.5, 45, -5], [-1.5, 47, -4], hair(D_HAIR, D_HAIR_D)),
          C('right_lock', [4, 34, -3.5], [5, 46, .5], hair(A_HAIR, A_HAIR_D)),
          C('left_lock', [-5, 34, -3.5], [-4, 46, .5], hair(D_HAIR, D_HAIR_D)),
          SP('hair_back', [-4.5, 29, 3], [4.5, 47, 5], hair(A_HAIR, A_HAIR_D), hair(D_HAIR, D_HAIR_D), False),
          SP('hair_tail', [-2.5, 20, 4], [2.5, 29, 6], hair(A_HAIR, A_HAIR_D), hair(D_HAIR, D_HAIR_D), False),
          SP('circlet', [-4.5, 47, -5], [4.5, 48, -4], gA, gD, False),
          C('circlet_gem', [-.5, 47, -5.5], [.5, 49, -4.5], glow(D_GEM)),
          C('crown_spire', [-.5, 49, -4.5], [.5, 52, -3.5], gA),
          C('right_crown_spire', [2, 49, -4.5], [3, 51, -3.5], gA), C('left_crown_spire', [-3, 49, -4.5], [-2, 51, -3.5], gD),
          C('left_eye_glow', [-3, 43.5, -4.2], [-1, 44.5, -4.2], glow(EYE)),
          C('jaw_spike', [-2.5, 39, -4], [-1.5, 40, -3], shaded(HORN)),
          halo('halo', 55, 5, gA, cz=1), outer_halo(56, 8, 1)]
    hk[0].paint = with_faces(hk[0].paint, north=split_face)
    hk += horn('right') + horn('left')
    head = Group('head', [0, 40, 0], hk)
    body_k = skirt + torso + [head, arm('right', 1), arm('left', 1), arm('right', 2), arm('left', 2)]
    fA = feather(A_FEA, A_FEA_TIP, A_SHAFT); cA = feather(A_COV, A_FEA, None)
    fD = feather(D_FEA, D_FEA_TIP, D_SHAFT, tattered=.05, ember=EMBER); cD = feather(D_COV, D_FEA, None)
    for s, fp, cv in (('right', fA, cA), ('left', fD, cD)):
        body_k.append(wing(s, 'upper_', (2, 38, 3), 9, 2, 8, 3, cv, fp, [-10, -28, 46], z0=1))
        body_k.append(wing(s, 'mid_', (2, 34, 3), 9, 2, 9, 3, cv, fp, [0, -34, 10], z0=2.5))
        body_k.append(wing(s, 'low_', (2, 29, 3), 7, 2, 7, 3, cv, fp, [10, -40, -28], z0=4))
    B.apply_palette(dict(SKIN=D_SKIN, HORN=HORN, HORN_TIP=HORN_TIP))
    body_k.append(B.tail())
    body = Group('body', [0, 24, 0], body_k)
    shift(body, LIFT)
    body.children += [     # 길게 끌리는 아랫단 (원래 모델보다 키가 커짐)
        SP('skirt_flare', [-9, 10, -7], [9, 16, 7], rA(4), rD(4)),
        SP('skirt_base', [-10, 3, -8], [10, 10, 8], rA(5, 1), rD(5, 1)),
        SP('front_tabard', [-3, 4, -9], [3, 20, -8], rAd(6, 1), rDd(6, 1)),
        C('tabard_gem', [-1, 14, -9.5], [1, 16, -9], glow(D_GEM)),
        C('right_base_fold', [10, 3, -4], [11, 10, 1], rAd(7, 1)), C('left_base_fold', [-11, 3, -1], [-10, 10, 4], rDd(8, 1)),
        SP('train_low', [-8, 2, 8], [8, 8, 12], rA(9, 1), rD(9, 1)),
        SP('train_end', [-5, 1, 12], [5, 5, 16], rA(10, 1), rD(10, 1)),
    ]
    return Group('root', [0, 0, 0], [body])

# ---------------- 애니메이션 ----------------
PAIRS = [('upper_', 1.0, 0.0), ('mid_', .8, .7), ('low_', .6, 1.4)]
def anims(G):
    n = f'animation.{ID}.'
    def wings(t, rx=0, ry=0, rz=0, it=cm, scale=None):
        d = {}
        for p, k, _ in PAIRS:
            d[f'right_{p}wing'] = [('rotation', t, rx, ry, rz * k, it)]
            d[f'left_{p}wing'] = [('rotation', t, rx, -ry, -rz * k, it)]
            if scale is not None:
                d[f'right_{p}wing'].append(('scale', t, scale, scale, scale, it))
                d[f'left_{p}wing'].append(('scale', t, scale, scale, scale, it))
        return d
    def arms(t, rx, rz=0, frx=0, it=cm, only=None, tier=''):
        d = {}
        for s, sg in (('right', 1), ('left', -1)):
            if only and s != only: continue
            d[f'{s}_arm{tier}'] = [('rotation', t, rx, 0, rz * sg, it)]
            d[f'{s}_forearm{tier}'] = [('rotation', t, frx, 0, 0, it)]
        return d
    def wing_wave(a, L, yb=0):
        d = {}
        for p, k, ph in PAIRS:
            d[f'right_{p}wing'] = [(r[0], r[1], 0, yb, r[4] * k, cm) for r in rot_wave(a, L, 2, ph)]
            d[f'left_{p}wing'] = [(r[0], r[1], 0, -yb, -r[4] * k, cm) for r in rot_wave(a, L, 2, ph)]
        return d
    R = lambda t, x=0, y=0, z=0, it=cm: ('rotation', t, x, y, z, it)
    T = lambda t, x=0, y=0, z=0, it=cm: ('position', t, x, y, z, it)
    halo_spin = lambda L, turns=1: {'halo': [R(0, 0, 0, 0, 'linear'), R(L, 0, 360 * turns, 0, 'linear')],
                                    'halo_outer': [R(0, 0, 0, 0, 'linear'), R(L, 0, -360 * turns, 0, 'linear')]}
    # spawn: 날개로 몸을 감싼 채 하늘에서 내려와 여섯 날개를 터뜨려 펼침
    spawn = merge(
        {'root': [T(0, 0, 40, 0, 'linear'), T(1.6, 0, 3), T(2.2, 0, 0), T(3.2, 0, 0)],
         'body': [R(0, 18, 0, 0, 'linear'), R(1.6, 18), R(2.0, -14), R(2.6, -6), R(3.2)],
         'head': [R(0, 28, 0, 0, 'linear'), R(1.6, 28), R(2.1, -26), R(2.7, -12), R(3.2)],
         'tail': [R(0, 30, 0, 0, 'linear'), R(1.6, 30), R(2.2, -20), R(3.2)]},
        halo_spin(3.2, 2),
        wings(0, 0, -72, -30, 'linear', .5), wings(1.6, 0, -72, -30, cm, .5), wings(2.0, 0, 20, 32, 'linear', 1.15), wings(2.6, 0, 8, 14, cm, 1), wings(3.2, 0, 0, 0, cm, 1),
        arms(0, 0, -5, 0, 'linear'), arms(1.6, 0, -5), arms(2.1, 30, 50, -20), arms(3.2, 0, 0, 0))
    L = 3.0
    idle = merge({'body': pos_wave(.9, L), 'head': rot_wave(2.5, L, 0, 1.2),
                  'tail': [(r[0], r[1], 0, r[2], 0, cm) for r in rot_wave(14, L, 0)], 'tail3': rot_wave(-10, L, 0, 1),
                  'right_arm': rot_wave(4, L, 0, .4), 'left_arm': rot_wave(-4, L, 0, .4),
                  'right_arm2': rot_wave(2, L, 0, 1), 'left_arm2': rot_wave(2, L, 0, 1),
                  'scales_beam': rot_wave(5, L, 2, 2)}, halo_spin(L), wing_wave(8, L))
    L = 1.5
    walk = merge({'body': [(k[0], k[1], 12 + k[2], 0, 0, cm) for k in rot_wave(3, L, 0)],
                  'head': [R(0, -8), R(L, -8)],
                  'tail': [(r[0], r[1], -10, r[2], 0, cm) for r in rot_wave(20, L, 0)],
                  'right_arm': [R(t, -14 + v, 0, 8) for _, t, v, *_ in rot_wave(6, L, 0)],
                  'left_arm': [R(t, -12 - v, 0, -8) for _, t, v, *_ in rot_wave(6, L, 0)]},
                 halo_spin(L), wing_wave(16, L, -8))
    walk['body'] = merge({'body': walk['body']}, {'body': pos_wave(.7, L)})['body']
    # attack: 악마 쪽(왼손) 장부 든 팔 대신 아래 왼손으로 할퀴고, 꼬리가 따라 침
    attack = merge(
        {'left_arm2': [R(0), R(.35, 150, 0, -45), R(.55, 40, 0, 35, 'linear'), R(1.0)],
         'left_forearm2': [R(0), R(.35, -70), R(.55, -50, 0, 0, 'linear'), R(1.0)],
         'right_arm2': [R(0), R(.35, -10, 0, 20), R(1.0)],
         'body': [R(0), R(.35, -6, -22), R(.55, 12, 24, 0, 'linear'), R(1.0)],
         'tail': [R(0), R(.35, 0, 20), R(.6, 0, -30, 0, 'linear'), R(1.0)]},
        wings(0, 0, 0, 0, 'linear'), wings(.35, 0, 15, 20), wings(.6, 0, -15, -10), wings(1.0, 0, 0, 0))
    # judgment(심판): 천사 쪽 저울을 높이 들어 올림 → 빛을 내리꽂음
    judgment = merge(
        {'right_arm': [R(0), R(.6, 150, 0, 16), R(1.2, 156, 0, 12), R(1.45, 50, 0, 0, 'linear'), R(2.0)],
         'right_forearm': [R(0), R(.6, -50), R(1.2, -50), R(1.45, -10, 0, 0, 'linear'), R(2.0)],
         'scales': [R(0), R(.6, -60), R(1.2, -60), R(1.45, 0, 0, 0, 'linear'), R(2.0)],
         'scales_beam': [R(0), R(.6, 0, 0, -18), R(.9, 0, 0, 18), R(1.2, 0, 0, -18), R(2.0)],
         'body': [R(0), R(.6, -8, 8), R(1.2, -10, 8), R(1.45, 12, -4, 0, 'linear'), R(2.0)],
         'head': [R(0), R(.6, -18), R(1.2, -20), R(1.45, 8, 0, 0, 'linear'), R(2.0)],
         'halo': [T(0), T(.6, 0, 3), T(1.2, 0, 3), T(2.0)]},
        wings(0, 0, 0, 0, 'linear'), wings(.6, 0, 20, 30), wings(1.2, 0, 22, 32), wings(1.45, 0, -10, -14, 'linear'), wings(2.0, 0, 0, 0))
    # collect(수금): 장부를 높이 들었다가 바닥에 내리찍음
    collect = merge(
        {'left_arm': [R(0), R(.7, 170, 0, -10), R(.95, 175, 0, -10), R(1.15, 55, 0, 0, 'linear'), R(1.8)],
         'left_forearm': [R(0), R(.7, -20), R(1.15, 10, 0, 0, 'linear'), R(1.8)],
         'ledger_book': [R(0), R(.7, 60), R(1.15, -20, 0, 0, 'linear'), R(1.8)],
         'body': [R(0), R(.7, -14), R(1.15, 22, 0, 0, 'linear'), R(1.8), T(0), T(.7, 0, 3), T(1.15, 0, -4, 0, 'linear'), T(1.8)],
         'head': [R(0), R(.7, -20), R(1.15, 14, 0, 0, 'linear'), R(1.8)]},
        wings(0, 0, 0, 0, 'linear'), wings(.7, 0, 20, 34), wings(1.15, 0, -10, -20, 'linear'), wings(1.8, 0, 0, 0))
    # wing_burst: 여섯 날개를 접었다가 한꺼번에 터뜨림 (전방위 패턴)
    wing_burst = merge(
        {'body': [T(0), T(.7, 0, -2), T(.95, 0, 5, 0, 'linear'), T(1.5, 0, 4), T(2.0), R(0), R(.7, 14), R(.95, -14, 0, 0, 'linear'), R(1.5, -10), R(2.0)],
         'head': [R(0), R(.7, 20), R(.95, -24, 0, 0, 'linear'), R(1.5, -18), R(2.0)]},
        wings(0, 0, 0, 0, 'linear', 1), wings(.7, 0, -68, -28, cm, .8), wings(.95, 0, 26, 36, 'linear', 1.2), wings(1.5, 0, 20, 30, cm, 1.1), wings(2.0, 0, 0, 0, cm, 1),
        arms(0, 0, 0, 0, 'linear', tier='2'), arms(.7, 10, -10, 10, tier='2'), arms(.95, 20, 80, -60, 'linear', tier='2'), arms(1.5, 20, 76, -60, tier='2'), arms(2.0, 0, 0, 0, tier='2'))
    tail_swing = merge(
        {'body': [R(0), R(.4, 0, -40), R(.75, 0, 95, 0, 'linear'), R(1.2)],
         'tail': [R(0), R(.4, -20, 50), R(.75, -30, -60, 0, 'linear'), R(1.2)],
         'tail2': [R(0), R(.4, 0, 25), R(.8, 0, -30, 0, 'linear'), R(1.2)]},
        wings(0, 0, 0, 0, 'linear'), wings(.4, 0, -25, -10), wings(.75, 0, 20, 15), wings(1.2, 0, 0, 0))
    # cast: 아래 두 손은 기도, 위 두 손(저울·장부)을 양옆으로 펼쳐 듦
    cast = merge(
        {'body': [T(0), T(.7, 0, 5), T(1.6, 0, 5), T(2, 0, 0), R(0), R(.7, -10), R(1.6, -10), R(2)],
         'head': [R(0), R(.7, -22), R(1.6, -22), R(2)],
         'tail': [R(0), R(.7, -25), R(1.6, -25), R(2)]},
        arms(0, 0, 0, 0, 'linear'), arms(.7, 60, 62, -30), arms(1.6, 60, 62, -30), arms(2, 0, 0, 0),
        wings(0, 0, 0, 0, 'linear'), wings(.7, 0, 22, 30), wings(1.6, 0, 22, 30), wings(2, 0, 0, 0), halo_spin(2, 2))
    # groggy(루프): 날개가 처지고 고개를 떨굼
    L = 3.0
    groggy = merge(
        {'body': [R(0, 24), R(L / 2, 27), R(L, 24), T(0, 0, -5), T(L / 2, 0, -6), T(L, 0, -5)],
         'head': [R(0, 26, 0, 8), R(L / 2, 30, 0, 10), R(L, 26, 0, 8)],
         'halo': [R(0, 20, 0, 14), R(L, 20, 0, 14)], 'halo_outer': [R(0, -16, 0, -20), R(L, -16, 0, -20)],
         'tail': [R(0, 36), R(L, 36)]},
        wings(0, 0, -50, -34), wings(L / 2, 0, -52, -37), wings(L, 0, -50, -34),
        arms(0, 20, -8, 10), arms(L, 20, -8, 10))
    # death(봉인): 경련 → 주저앉음 → 날개가 오그라들고 후광이 떨어짐
    death = merge(
        {'body': [R(0, 0, 0, 0, 'linear'), R(.3, -16, 8, 0, 'linear'), R(.6, -12, -10, 0, 'linear'), R(.9, -18, 6, 0, 'linear'), R(2.2, 34), R(4, 40),
                  T(0), T(1.0, 0, 3), T(2.2, 0, -8), T(4, 0, -10)],
         'head': [R(0), R(.9, -35), R(2.4, 35), R(4, 40)],
         'tail': [R(0), R(2.2, 40), R(4, 45)],
         'halo': [T(0), T(2.4, 0, -16), T(4, 0, -16), R(0), R(2.4, 70, 0, 30), R(4, 80, 0, 30)],
         'halo_outer': [T(0), T(1.2, 0, 4), T(2.8, 0, -20), T(4, 0, -20), R(0), R(2.8, -60, 0, -40), R(4, -70, 0, -40)]},
        arms(0, 0, 0, 0, 'linear'), arms(.9, 160, 40, -20), arms(2.4, 20, 10, 30), arms(4, 15, 10, 30),
        wings(0, 0, 0, 0, 'linear', 1), wings(.9, 0, 25, 40, cm, 1.1), wings(2.6, 0, -60, -40, cm, .6), wings(4, 0, -70, -45, cm, 0))
    # ---- 공성전 짤패턴 모션 (VFX 팩 Siege_Belzar 와 짝). 몸통·머리는 + = 앞으로 숙임(아래에서 부호 뒤집음) ----
    hold2 = lambda t0, t1, f: merge(f(t0), f(t1))
    debt_mark = merge(   # 1 청구서 표식: 장부를 펴 들고 아래 오른손으로 적은 뒤(0.3~0.9초) 상대를 가리킨다(1.0초에 표식)
        {'left_arm': [R(0), R(.3, 70, 0, -12), R(1.2, 70, 0, -12), R(1.6)], 'left_forearm': [R(0), R(.3, 30), R(1.2, 30), R(1.6)], 'ledger_book': [R(0), R(.3, 40), R(1.2, 40), R(1.6)],
         'right_arm2': [R(0), R(.3, 30, 0, -30), R(.45, 34, 0, -22), R(.6, 30, 0, -30), R(.75, 34, 0, -22), R(.9, 30, 0, -30), R(1.0, 60, 0, 20, 'linear'), R(1.3, 56, 0, 20), R(1.6)],
         'right_forearm2': [R(0), R(.3, 10), R(.9, 10), R(1.0, -60, 0, 0, 'linear'), R(1.3, -60), R(1.6)],
         'head': [R(0), R(.3, 14, 0, 8), R(.9, 14, 0, 8), R(1.0, -4), R(1.6)], 'body': [R(0), R(.3, 6), R(1.0, -4), R(1.6)]})
    def inv(first):   # 2 흑백 반전: first 쪽이 0.9초에, 반대쪽이 1.7초에 터진다. 'W' = 저울(오른팔) 먼저, 'K' = 장부(왼팔) 먼저
        tw, tk = (0.9, 1.7) if first == 'W' else (1.7, 0.9)
        return merge(
            {'right_arm': [R(0), R(tw - .5, 150, 0, 16), R(tw - .08, 156, 0, 12), R(tw, 50, 0, 0, 'linear'), R(tw + .4, 46), R(2.6)],
             'right_forearm': [R(0), R(tw - .5, -50), R(tw - .08, -50), R(tw, -10, 0, 0, 'linear'), R(2.6)], 'scales': [R(0), R(tw - .5, -60), R(tw - .08, -60), R(tw, 0, 0, 0, 'linear'), R(2.6)],
             'left_arm': [R(0), R(tk - .5, 170, 0, -10), R(tk - .08, 175, 0, -10), R(tk, 55, 0, 0, 'linear'), R(tk + .4, 50), R(2.6)],
             'left_forearm': [R(0), R(tk - .5, -20), R(tk, 10, 0, 0, 'linear'), R(2.6)], 'ledger_book': [R(0), R(tk - .5, 60), R(tk, -20, 0, 0, 'linear'), R(2.6)],
             'body': [R(0), R(.4, -10), R(.9, 14, 0, 0, 'linear'), R(1.3, -8), R(1.7, 16, 0, 0, 'linear'), R(2.6)], 'head': [R(0), R(.4, -18), R(.9, 10, 0, 0, 'linear'), R(1.3, -14), R(1.7, 12, 0, 0, 'linear'), R(2.6)]},
            wings(0, 0, 0, 0, 'linear'), wings(.4, 0, 20, 30), wings(.9, 0, -10, -14, 'linear'), wings(1.3, 0, 18, 26), wings(1.7, 0, -12, -18, 'linear'), wings(2.6, 0, 0, 0))
    invert_white_first, invert_black_first = inv('W'), inv('K')
    chain_pull = merge(   # 3 사슬 포박: 0.7초에 아래 왼손으로 사슬을 던지고 1.0초에 잡아당긴다 (이어서 attack)
        {'left_arm2': [R(0), R(.45, -30, 0, -20), R(.7, 96, 0, 10, 'linear'), R(.9, 92, 0, 10), R(1.05, 20, 0, -10, 'linear'), R(1.6)],
         'left_forearm2': [R(0), R(.45, 20), R(.7, -70, 0, 0, 'linear'), R(.9, -70), R(1.05, 20, 0, 0, 'linear'), R(1.6)],
         'body': [R(0), R(.45, -8, -18), R(.7, 14, 20, 0, 'linear'), R(.9, 12, 18), R(1.05, -10, -10, 0, 'linear'), R(1.6)],
         'tail': [R(0), R(.7, 0, 24), R(1.05, 0, -20), R(1.6)]},
        wings(0, 0, 0, 0, 'linear'), wings(.45, 0, 14, 16), wings(.7, 0, -14, -8, 'linear'), wings(1.05, 0, 16, 20, 'linear'), wings(1.6, 0, 0, 0))
    feather_scatter = merge(   # 4 깃털 지뢰: 날개를 크게 털어 깃털을 뿌린다 (0.6초)
        {'body': [R(0), R(.35, -10), R(.6, 12, 0, 0, 'linear'), R(1.4)], 'head': [R(0), R(.35, -14), R(.6, 8, 0, 0, 'linear'), R(1.4)]},
        wings(0, 0, 0, 0, 'linear'), wings(.35, 0, 26, 38), wings(.6, 0, -34, -24, 'linear'), wings(.8, 0, 14, 20), wings(1.0, 0, -10, -8), wings(1.4, 0, 0, 0),
        arms(0, 0, 0, 0, 'linear'), arms(.35, 40, 50, -10), arms(.6, 30, 20, 0, 'linear'), arms(1.4, 0, 0, 0))
    black_wave = merge(   # 지뢰를 터뜨리는 검은 파동: 왼쪽 날개와 꼬리를 한 번 후려친다 (0.5초)
        {'body': [R(0), R(.3, 0, 26), R(.5, 6, -30, 0, 'linear'), R(1.2)], 'tail': [R(0), R(.3, -10, -30), R(.5, -20, 50, 0, 'linear'), R(1.2)],
         'left_arm': [R(0), R(.3, 40, 0, -60), R(.5, 60, 0, 30, 'linear'), R(1.2)]},
        wings(0, 0, 0, 0, 'linear'), wings(.3, 0, 20, 24), wings(.5, 0, -24, -14, 'linear'), wings(1.2, 0, 0, 0))
    halo_close = merge(   # 5 좁혀드는 후광: 두 팔과 날개를 활짝 폈다가(0.8초) 2.4초까지 천천히 오므린다
        {'body': [R(0), R(.8, -10), R(2.4, 8), R(2.8), T(0), T(.8, 0, 4), T(2.4, 0, 0), T(2.8)], 'head': [R(0), R(.8, -20), R(2.4, 12), R(2.8)],
         'halo': [T(0), T(.8, 0, 4), T(2.4, 0, -1), T(2.8), ('scale', 0, 1, 1, 1, cm), ('scale', .8, 1.6, 1.6, 1.6, cm), ('scale', 2.4, .6, .6, .6, cm), ('scale', 2.8, 1, 1, 1, cm)],
         'halo_outer': [('scale', 0, 1, 1, 1, cm), ('scale', .8, 1.5, 1.5, 1.5, cm), ('scale', 2.4, .7, .7, .7, cm), ('scale', 2.8, 1, 1, 1, cm)]},
        arms(0, 0, 0, 0, 'linear'), arms(.8, 30, 84, -10), arms(2.4, 70, -8, 40), arms(2.8, 0, 0, 0),
        wings(0, 0, 0, 0, 'linear'), wings(.8, 0, 26, 36), wings(2.4, 0, -54, -22), wings(2.8, 0, 0, 0))
    mirror_strike = merge(   # 6 거울 일격: 오른손을 들어 0.8초에 내리긋는다 (분신도 같은 모션)
        {'right_arm': [R(0), R(.5, 160, 0, 20), R(.7, 166, 0, 16), R(.8, 30, 0, -14, 'linear'), R(1.1, 28, 0, -14), R(1.6)], 'right_forearm': [R(0), R(.5, -30), R(.8, 0, 0, 0, 'linear'), R(1.6)],
         'body': [R(0), R(.5, -10, 12), R(.8, 18, -14, 0, 'linear'), R(1.1, 16, -12), R(1.6)], 'head': [R(0), R(.5, -14), R(.8, 10, 0, 0, 'linear'), R(1.6)]},
        wings(0, 0, 0, 0, 'linear'), wings(.5, 0, 22, 32), wings(.8, 0, -14, -18, 'linear'), wings(1.6, 0, 0, 0))
    spear_march = merge(   # 7 행진하는 창: 오른손으로 가리킨 채 0.25초마다 손끝을 튕긴다 (8번)
        {'right_arm': [R(0), R(.3, 84, 0, 4), R(2.3, 84, 0, 4), R(2.6)],
         'right_forearm': [R(0), R(.3, -50)] + [R(round(.45 + k * .25, 2) - (.1 if j else 0), -50 + (0 if j else 14)) for k in range(8) for j in (1, 0)] + [R(2.6)],
         'body': [R(0), R(.3, 6), R(2.3, 6), R(2.6)], 'head': [R(0), R(.3, 8), R(2.3, 8), R(2.6)], 'halo': [R(0, 0, 0, 0, 'linear'), R(2.6, 0, 720, 0, 'linear')]},
        wings(0, 0, 0, 0, 'linear'), wings(.3, 0, 14, 22), wings(2.3, 0, 14, 22), wings(2.6, 0, 0, 0))
    spear_march['right_forearm'] = sorted({k[1]: k for k in spear_march['right_forearm']}.values(), key=lambda k: k[1])
    interest = merge(   # 8 복리: 장부를 들고 아래 오른손으로 세 번 내리친다 (0.8 / 1.8 / 2.8초)
        {'left_arm': [R(0), R(.4, 80, 0, -12), R(3.0, 80, 0, -12), R(3.4)], 'left_forearm': [R(0), R(.4, 30), R(3.0, 30), R(3.4)], 'ledger_book': [R(0), R(.4, 44), R(3.0, 44), R(3.4)],
         'right_arm2': [R(0)] + [k for i, t in enumerate((.8, 1.8, 2.8)) for k in (R(round(t - .3, 2), 90 + i * 20, 0, 10), R(t, 20, 0, -10, 'linear'), R(round(t + .2, 2), 24, 0, -10))] + [R(3.4)],
         'body': [R(0)] + [k for i, t in enumerate((.8, 1.8, 2.8)) for k in (R(round(t - .3, 2), -4 - i * 3), R(t, 8 + i * 4, 0, 0, 'linear'))] + [R(3.4)],
         'head': [R(0), R(.4, 12, 0, 8), R(3.0, 12, 0, 8), R(3.4)]},
        wings(0, 0, 0, 0, 'linear'), wings(.5, 0, 8, 10), wings(1.5, 0, 14, 20), wings(2.5, 0, 22, 32), wings(2.8, 0, -10, -14, 'linear'), wings(3.4, 0, 0, 0))
    final_claim = merge(   # 9 최후 청구: 두 팔을 들어 1.0초에 흰 칸, 2.1초에 검은 칸
        {'body': [T(0), T(.7, 0, 5), T(2.2, 0, 5), T(2.8), R(0), R(.7, -10), R(1.0, 6, 0, 0, 'linear'), R(1.6, -10), R(2.1, 10, 0, 0, 'linear'), R(2.8)],
         'head': [R(0), R(.7, -22), R(1.0, 6, 0, 0, 'linear'), R(1.6, -20), R(2.1, 10, 0, 0, 'linear'), R(2.8)],
         'right_arm': [R(0), R(.7, 165, 0, 20), R(1.0, 60, 0, 30, 'linear'), R(2.2, 56, 0, 30), R(2.8)], 'left_arm': [R(0), R(.7, 100, 0, -60), R(1.7, 170, 0, -16), R(2.1, 56, 0, -20, 'linear'), R(2.8)],
         'halo': [R(0, 0, 0, 0, 'linear'), R(2.8, 0, 720, 0, 'linear')], 'halo_outer': [R(0, 0, 0, 0, 'linear'), R(2.8, 0, -720, 0, 'linear')]},
        wings(0, 0, 0, 0, 'linear'), wings(.7, 0, 22, 30), wings(1.0, 0, -8, -10, 'linear'), wings(1.7, 0, 24, 34), wings(2.1, 0, -12, -16, 'linear'), wings(2.8, 0, 0, 0))
    gate_strike = merge(   # 성문 강타: 장부를 1.3초 동안 치켜들고 1.5초에 내리찍는다
        {'left_arm': [R(0), R(1.2, 170, 0, -10), R(1.38, 176, 0, -10), R(1.5, 55, 0, 0, 'linear'), R(1.9, 50), R(2.3)], 'left_forearm': [R(0), R(1.2, -20), R(1.5, 10, 0, 0, 'linear'), R(2.3)],
         'ledger_book': [R(0), R(1.2, 60), R(1.5, -20, 0, 0, 'linear'), R(2.3)],
         'body': [R(0), R(1.2, -14), R(1.5, 24, 0, 0, 'linear'), R(1.9, 20), R(2.3), T(0), T(1.2, 0, 4), T(1.5, 0, -4, 0, 'linear'), T(2.3)], 'head': [R(0), R(1.2, -20), R(1.5, 14, 0, 0, 'linear'), R(2.3)]},
        wings(0, 0, 0, 0, 'linear'), wings(1.2, 0, 22, 36), wings(1.5, 0, -12, -20, 'linear'), wings(2.3, 0, 0, 0))
    enrage = merge(   # 광폭화: 웅크렸다가 여섯 날개를 터뜨리며 흰 쪽·검은 쪽 팔을 벌린다
        {'body': [T(0), T(.6, 0, -3), T(.9, 0, 6, 0, 'linear'), T(2.0, 0, 5), T(2.4), R(0), R(.6, 18), R(.9, -16, 0, 0, 'linear'), R(2.0, -12), R(2.4)],
         'head': [R(0), R(.6, 24), R(.9, -28, 0, 0, 'linear'), R(1.2, -22), R(1.5, -28), R(2.0, -22), R(2.4)], 'tail': [R(0), R(.6, 26), R(.9, -24, 0, 0, 'linear'), R(2.0, -20), R(2.4)],
         'halo': [R(0, 0, 0, 0, 'linear'), R(2.4, 0, 1080, 0, 'linear')], 'halo_outer': [R(0, 0, 0, 0, 'linear'), R(2.4, 0, -1080, 0, 'linear')]},
        wings(0, 0, 0, 0, 'linear', 1), wings(.6, 0, -70, -30, cm, .8), wings(.9, 0, 28, 40, 'linear', 1.25), wings(2.0, 0, 22, 32, cm, 1.15), wings(2.4, 0, 0, 0, cm, 1),
        arms(0, 0, 0, 0, 'linear'), arms(.6, 20, -10, 20), arms(.9, 40, 84, -20, 'linear'), arms(2.0, 36, 80, -20), arms(2.4, 0, 0, 0),
        arms(0, 0, 0, 0, 'linear', tier='2'), arms(.6, 10, -10, 10, tier='2'), arms(.9, 20, 70, -60, 'linear', tier='2'), arms(2.0, 20, 66, -60, tier='2'), arms(2.4, 0, 0, 0, tier='2'))
    NEW = [('debt_mark', 1.6, debt_mark), ('invert_white_first', 2.6, invert_white_first), ('invert_black_first', 2.6, invert_black_first), ('chain_pull', 1.6, chain_pull),
           ('feather_scatter', 1.4, feather_scatter), ('black_wave', 1.2, black_wave), ('halo_close', 2.8, halo_close), ('mirror_strike', 1.6, mirror_strike),
           ('spear_march', 2.6, spear_march), ('interest', 3.4, interest), ('final_claim', 2.8, final_claim), ('gate_strike', 2.3, gate_strike), ('enrage', 2.4, enrage)]
    for _tr in (spawn, idle, walk, attack, judgment, collect, wing_burst, tail_swing, cast, groggy, death) + tuple(x[2] for x in NEW):   # 몸통·머리 +x = 뒤로 젖힘 → 의도대로 부호 뒤집음
        for _b in ('body', 'head'):
            if _b in _tr: _tr[_b] = [(k[0], k[1], -k[2], *k[3:]) if k[0] == 'rotation' else k for k in _tr[_b]]
    return [anim(n + 'spawn', 3.2, 'once', spawn, G), anim(n + 'idle', 3.0, 'loop', idle, G),
            anim(n + 'walk', 1.5, 'loop', walk, G), anim(n + 'attack', 1.0, 'once', attack, G),
            anim(n + 'judgment', 2.0, 'once', judgment, G), anim(n + 'collect', 1.8, 'once', collect, G),
            anim(n + 'wing_burst', 2.0, 'once', wing_burst, G), anim(n + 'tail_swing', 1.2, 'once', tail_swing, G),
            anim(n + 'cast', 2.0, 'once', cast, G), anim(n + 'groggy', 3.0, 'loop', groggy, G),
            anim(n + 'death', 4.0, 'hold', death, G)] + [anim(n + nm, ln, 'once', tr, G) for nm, ln, tr in NEW]

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'; os.makedirs(O, exist_ok=True)
    export(ID, build(), anims, 21, O + ID + '.bbmodel', HITBOX)
    apply_palette(SHINY)
    export(ID + '_shiny', build(), anims, 21, O + ID + '_shiny.bbmodel', HITBOX)
