"""가면의 악마 보스: 1페이즈 엘리시아(천사 분장) / 2페이즈 벨자르(정체)"""
from common import *
import common, os, sys

# ---------- 팔레트 ----------
ELYSIA = dict(
    ROBE=(238, 234, 224), ROBE_D=(196, 190, 178), GOLD=(230, 186, 78), GOLD_HI=(255, 238, 168),
    SKIN=(246, 224, 206), HAIR=(242, 216, 150), HAIR_D=(206, 170, 98),
    FEA=(248, 246, 240), FEA_TIP=(212, 204, 188), SHAFT=(214, 200, 168), COVERT=(240, 238, 230),
    LINE=(140, 100, 72), LASH=(96, 62, 48), IRIS=(206, 150, 52), BLUSH=(246, 186, 180), LIP=(232, 150, 150), GEM=(255, 246, 200, 255),
    SHADOW=(26, 8, 30, 115))
ELYSIA_SHINY = dict(ELYSIA,
    ROBE=(222, 214, 236), ROBE_D=(170, 160, 196), GOLD=(214, 128, 140), GOLD_HI=(255, 206, 214),
    HAIR=(226, 232, 246), HAIR_D=(160, 170, 206), FEA=(236, 244, 255), FEA_TIP=(150, 186, 236),
    SHAFT=(170, 196, 230), COVERT=(226, 234, 250), GEM=(200, 236, 255, 255), SHADOW=(10, 20, 40, 115), IRIS=(96, 150, 226), LASH=(70, 74, 110))
BELZAR = dict(
    ROBE=(48, 30, 42), ROBE_D=(26, 14, 22), GOLD=(128, 88, 44), GOLD_HI=(196, 140, 70),
    SKIN=(116, 92, 104), HAIR=(38, 30, 36), HAIR_D=(16, 12, 16),
    FEA=(36, 26, 32), FEA_TIP=(14, 8, 12), SHAFT=(70, 40, 40), COVERT=(46, 32, 40), EMBER=(214, 74, 34),
    HORN=(58, 42, 42), HORN_TIP=(20, 14, 14), EYE=(255, 76, 40, 255), CLAW=(22, 16, 18),
    LEDGER=(112, 30, 30), PAGE=(222, 206, 172), LINE=(30, 10, 14), LIP=(40, 10, 14), GEM=(255, 60, 40, 255),
    CHAIN=(84, 80, 86), SHADOW=(0, 0, 0, 0))
BELZAR_SHINY = dict(BELZAR,
    ROBE=(22, 30, 58), ROBE_D=(10, 14, 30), GOLD=(150, 156, 170), GOLD_HI=(220, 226, 240),
    SKIN=(84, 96, 128), FEA=(20, 26, 46), FEA_TIP=(6, 8, 18), EMBER=(60, 190, 255), COVERT=(28, 36, 60),
    HORN=(214, 206, 188), HORN_TIP=(150, 140, 120), EYE=(200, 110, 255, 255), GEM=(150, 90, 255, 255),
    LEDGER=(30, 40, 90))
def apply_palette(p): globals().update(p)

# ---------- 얼굴 ----------
def angel_face(img, r, rng):
    for lx in (1, 2, 5, 6): dot(img, r, lx, 3, LASH)                      # 속눈썹
    dot(img, r, 0, 3, LASH); dot(img, r, 7, 3, LASH)                     # 바깥 꼬리 속눈썹
    dot(img, r, 1, 4, (252, 250, 246)); dot(img, r, 2, 4, IRIS)          # 왼쪽(화면 오른쪽) 눈: 흰자+눈동자
    dot(img, r, 5, 4, IRIS); dot(img, r, 6, 4, (252, 250, 246))
    for lx in (1, 6): dot(img, r, lx, 5, BLUSH)
    dot(img, r, 3, 6, LIP); dot(img, r, 4, 6, LIP)
def demon_face(img, r, rng):
    for lx, ly in [(1, 4), (2, 4), (5, 4), (6, 4)]: dot(img, r, lx, ly, EYE)
    for lx, ly in [(0, 3), (1, 3), (6, 3), (7, 3), (2, 3), (5, 3)]: dot(img, r, lx, ly, LINE)
    for lx in range(1, 7): dot(img, r, lx, 6, LIP)
    for lx in (2, 5): dot(img, r, lx, 6, (236, 226, 210))                 # 송곳니
    for lx, ly in [(0, 5), (7, 5), (3, 2), (4, 1)]: dot(img, r, lx, ly, EMBER)  # 갈라진 금

def shadow_fn(u, v, rng):
    """천사의 바닥 그림자 — 뿔 달린 여섯 날개 악마 실루엣"""
    x, y = u - .5, v - .5; ax = abs(x)
    a = SHADOW
    if (x / .085) ** 2 + ((y - .06) / .17) ** 2 < 1: return a          # 몸
    if x * x + (y + .17) ** 2 < .058 ** 2: return a                       # 머리
    for k in range(3):                                                    # 뿔
        t = k / 2
        hx = .045 + .03 * t; hy = -.22 - .07 * t
        if (ax - hx) ** 2 + (y - hy) ** 2 < (.022 - .006 * k) ** 2: return a
    r = math.hypot(ax, y); phi = math.atan2(-y, ax)
    for alpha, L in [(.62, .47), (.08, .45), (-.5, .38)]:                # 날개 3쌍
        if .07 < r < L:
            half = .2 * (1 - r / L) + .035 + .02 * math.sin(r * 70)
            if abs(phi - alpha) < half: return a
    tx = .05 * math.sin((y - .2) * 9)                                     # 꼬리
    if .2 < y < .43 and abs(x - tx) < .012: return a
    if (x - .03) ** 2 + (y - .44) ** 2 < .028 ** 2: return a
    return None

# ---------- 파츠 ----------
def hand(side, demon):
    m = side == 'left'
    k = [C(f'{side}_palm', [5.5, 17, -1.5], [7.5, 20, 1.5], shaded(SKIN), m),
         C(f'{side}_thumb', [4.5, 17, -1.5], [5.5, 19, -.5], shaded(SKIN), m)]
    for i, z in enumerate((-1.5, -.5, .5)):
        if demon:
            k.append(C(f'{side}_finger{i}', [5.5, 14, z], [7.5, 17, z + 1], shaded(SKIN), m))
            k.append(C(f'{side}_claw{i}', [6, 12, z], [7, 14, z + 1], shaded(CLAW), m))
        else:
            k.append(C(f'{side}_finger{i}', [5.5, 15, z], [7.5, 17, z + 1], shaded(SKIN), m))
    return Group(f'{side}_hand', MP([6.5, 20, 0], m), k)

def scales():
    """엘리시아의 금 저울(왼손) — 한쪽으로 기울어진 저울"""
    g = gold(GOLD, GOLD_HI)
    beam = Group('scales_beam', [-6.5, 8.5, 0], [
        C('scales_beam', [-12, 8, -.5], [-1, 9, .5], g),
        C('scales_chain_a', [-11.5, 3, 0], [-10.5, 8, 0], g),
        C('scales_chain_a2', [-11, 3, -.5], [-11, 8, .5], g),
        C('scales_pan_a', [-12.5, 2, -1.5], [-9.5, 3, 1.5], g),
        C('scales_pan_a_rim', [-12.5, 3, -1.5], [-9.5, 4, -.5], g),
        C('scales_chain_b', [-2.5, 3, 0], [-1.5, 8, 0], g),
        C('scales_chain_b2', [-2, 3, -.5], [-2, 8, .5], g),
        C('scales_pan_b', [-3.5, 2, -1.5], [-.5, 3, 1.5], g),
        C('scales_pan_b_rim', [-3.5, 3, -1.5], [-.5, 4, -.5], g),
    ], [0, 0, 9])
    return Group('scales', [-6.5, 15, 0], [
        C('scales_ring', [-7.5, 13, -.5], [-5.5, 15, .5], g),
        C('scales_stem', [-7, 9, -.5], [-6, 13, .5], g),
        C('scales_finial', [-7.5, 7, -1], [-5.5, 8, 1], g),
        C('scales_gem', [-7, 10, -1], [-6, 11, -.5 + .5], glow(GEM)),
        beam], [-84, 0, 0])

def ledger_book():
    """벨자르의 장부(왼손)"""
    cov = with_faces(shaded(LEDGER, edge=.8), west=lambda img, r, rng: [dot(img, r, x, y, GOLD) for x, y in [(3, 3), (3, 4), (2, 4), (4, 4), (3, 5)]])
    return Group('ledger_book', [-6.5, 16, 0], [
        C('book_back_cover', [-8, 7, -4], [-7, 17, 3], cov),
        C('book_pages', [-7, 7.5, -3.5], [-5, 16.5, 2.5], shaded(PAGE, noise=.03)),
        C('book_front_cover', [-5, 7, -4], [-4, 17, 3], cov),
        C('book_spine', [-8, 7, 3], [-4, 17, 4], shaded(LEDGER)),
        C('book_clasp', [-6.5, 10, -5], [-5.5, 13, -4], gold(GOLD, GOLD_HI)),
        C('book_chain', [-6.5, 2, 3.5], [-5.5, 7, 3.5], shaded(CHAIN)),
        C('book_bookmark', [-6.5, 4, -3], [-5.5, 7, -3], shaded(GEM[:3])),
    ], [-28, 0, 0])

def arm(side, demon):
    m = side == 'left'
    sl = robe(ROBE, GOLD, 1, ph=1 + m, torn=2 if demon else 0)
    h = hand(side, demon)
    if side == 'left': h.children.append(ledger_book() if demon else scales())
    fore = Group(f'{side}_forearm', MP([6.5, 29, 0], m), [
        C(f'{side}_forearm', [5, 22, -1.5], [8, 29, 1.5], shaded(SKIN), m),
        C(f'{side}_sleeve_bell', [4, 23, -2.5], [9, 29, 2.5], sl, m),
        C(f'{side}_cuff', [4.5, 22, -2], [8.5, 23, 2], gold(GOLD, GOLD_HI), m), h],
        [(20 if demon else 62) if m else 18, 0, 0])
    k = [C(f'{side}_shoulder', [4.5, 33, -2.5], [8.5, 38, 2.5], robe(ROBE, ph=2), m),
         C(f'{side}_upper_arm', [5, 28, -1.5], [8, 34, 1.5], robe(ROBE_D, ph=3), m),
         C(f'{side}_pauldron', [5, 37, -2], [9, 39, 2], gold(GOLD, GOLD_HI), m), fore]
    if demon: k.append(C(f'{side}_shoulder_spike', [7, 38, -.5], [8, 41, .5], shaded(HORN), m, rot=[0, 0, -25], origin=[7.5, 38, 0]))
    return Group(f'{side}_arm', MP([6.5, 37, 0], m), k, MR([(22 if (m and not demon) else 0), 0, 7], m))

def horn(side):
    m = side == 'left'
    pts = [(4, 46, -1.5), (6.5, 48.5, -.5), (8, 52, 1), (8.2, 55.5, 2.5), (7.2, 58.5, 4), (5.8, 60.5, 5)]
    ws = [3, 3, 2, 2, 1]
    gs = []
    for i in range(5):
        c = shaded(HORN if i < 3 else HORN_TIP, grad=.25)
        a, e = np.array(pts[i]), np.array(pts[i + 1]); e = a + (e - a) * 1.35
        gs.append(seg(f'{side}_horn{i}', list(a), list(e), ws[i], c, m))
    return gs

def tail():
    nodes = None
    n = 6
    for i in reversed(range(n)):
        z0 = 5 + 3 * i
        w = 3 if i < 2 else 2
        kids = [C(f'tail{i}', [-w / 2, 20 - w / 2 + 1, z0], [w / 2, 20 + w / 2 + 1, z0 + 3], shaded(SKIN if i < 4 else HORN))]
        if i == n - 1:
            kids += [C('tail_spade', [-2, 20.5, z0 + 3], [2, 21.5, z0 + 6], shaded(HORN_TIP)),
                     C('tail_spade_tip', [-1, 20.5, z0 + 6], [1, 21.5, z0 + 7], shaded(HORN_TIP))]
        if nodes: kids.append(nodes)
        nodes = Group(f'tail{i}', [0, 21, z0], kids, [32 if i == 0 else -13, 0, 0])
    return Group('tail', [0, 21, 5], [nodes])

def build(form):
    demon = form == 'belzar'
    R = lambda ph=0, **k: robe(ROBE, GOLD, k.pop('tr', 0), ph=ph, torn=3 if demon and k.pop('t', 0) else 0, dark=ROBE_D if demon else None)
    skirt = [
        C('hem', [-8, 4, -6], [8, 9, 6], robe(ROBE, GOLD, 1, 0, torn=3 if demon else 0)),
        C('skirt_lower', [-7, 9, -5], [7, 17, 5], robe(ROBE, ph=1)),
        C('skirt_mid', [-6, 17, -4.5], [6, 24, 4.5], robe(ROBE, ph=2)),
        C('waist', [-5, 24, -4], [5, 29, 4], robe(ROBE, ph=3)),
        C('sash', [-5.5, 28, -4.5], [5.5, 30, 4.5], gold(GOLD, GOLD_HI)),
        C('sash_tail', [-1, 19, -5.5], [1, 28, -4.5], gold(GOLD, GOLD_HI)),
        C('front_panel', [-2.5, 5, -6.5], [2.5, 19, -5.5], robe(ROBE_D, GOLD, 1, 4, torn=3 if demon else 0)),
        C('right_fold', [7, 9, -1], [8, 17, 1], robe(ROBE_D, ph=5)),
        C('left_fold', [-8, 9, -1], [-7, 17, 1], robe(ROBE_D, ph=6)),
        C('right_hem_fold', [8, 4, -3], [9, 9, 0], robe(ROBE_D, GOLD, 1, 7, torn=3 if demon else 0)),
        C('left_hem_fold', [-9, 4, 0], [-8, 9, 3], robe(ROBE_D, GOLD, 1, 8, torn=3 if demon else 0)),
        C('train', [-6, 3, 6], [6, 8, 9], robe(ROBE, GOLD, 1, 9, torn=4 if demon else 0)),
    ]
    torso = [
        C('chest', [-4.5, 29, -3], [4.5, 38, 3], robe(ROBE, ph=1)),
        C('breastplate', [-3, 31, -4], [3, 37, -3], gold(GOLD, GOLD_HI)),
        C('heart_gem', [-1, 33, -4.5], [1, 35, -4], glow(GEM)) if False else C('heart_gem', [-1, 33, -5], [1, 35, -4], glow(GEM)),
        C('mantle', [-6, 35, -3.5], [6, 38, 3.5], robe(ROBE_D if demon else ROBE, GOLD, 1, 2)),
        C('mantle_front', [-5, 32, -4.5], [-3, 36, -3.5], robe(ROBE, GOLD, 1, 3)),
        C('mantle_front_r', [3, 32, -4.5], [5, 36, -3.5], robe(ROBE, GOLD, 1, 4)),
        C('collar', [-3, 38, -3.5], [3, 39, 2.5], gold(GOLD, GOLD_HI)),
        C('neck', [-1.5, 38, -1.5], [1.5, 41, 1.5], shaded(SKIN)),
        C('back', [-4, 30, 3], [4, 37, 4], robe(ROBE_D, ph=5)),
    ]
    if demon:
        torso += [C('waist_chain', [-5.5, 26, -4.5], [5.5, 27, 4.5], shaded(CHAIN)),
                  C('waist_chain_hang', [3, 20, -5], [4, 26, -4], shaded(CHAIN))]
    hk = [C('skull', [-4, 40, -4], [4, 48, 4], with_faces(shaded(SKIN, grad=.06), north=demon_face) if demon else
             with_faces(shaded(SKIN, noise=.025, grad=0, edge=1), north=angel_face)),
          C('hair_cap', [-4.5, 46, -4.5], [4.5, 49, 4.5], hair(HAIR, HAIR_D)),
          C('right_fringe', [1.5, 45, -5], [4.5, 47, -4], hair(HAIR, HAIR_D)),
          C('left_fringe', [-4.5, 45, -5], [-1.5, 47, -4], hair(HAIR, HAIR_D)),
          C('right_lock', [4, 36, -3.5], [5, 46, .5], hair(HAIR, HAIR_D)),
          C('left_lock', [-5, 36, -3.5], [-4, 46, .5], hair(HAIR, HAIR_D)),
          C('hair_back', [-4.5, 31, 3], [4.5, 47, 5], hair(HAIR, HAIR_D)),
          C('braid', [-1.5, 24, 4], [1.5, 31, 6], hair(HAIR, HAIR_D)),
          C('circlet', [-4.5, 47, -5], [4.5, 48, -4.5 + .5], gold(GOLD, GOLD_HI)),
          C('circlet_gem', [-.5, 47, -5.5], [.5, 48, -4.5], glow(GEM)),
          halo('broken_halo' if demon else 'halo', 54, 5, gold(GOLD, GOLD_HI), broken=demon, cz=1)]
    if demon:
        hk += horn('right') + horn('left')
        hk += [C('nose', [-.5, 43, -5], [.5, 45, -4], shaded(SKIN)), C('right_eye_glow', [1, 43.5, -4.2], [3, 44.5, -4.2], glow(EYE)),
               C('left_eye_glow', [-3, 43.5, -4.2], [-1, 44.5, -4.2], glow(EYE)),
               C('jaw_spike', [-.5, 39, -4], [.5, 40, -3], shaded(HORN))]
    head = Group('head', [0, 40, 0], hk)
    body_k = skirt + torso + [head, arm('right', demon), arm('left', demon)]
    if demon:
        fp = feather(FEA, FEA_TIP, SHAFT, tattered=.18, ember=EMBER)
        cv = feather(COVERT, FEA, None)
        for side in ('right', 'left'):
            body_k.append(wing(side, 'upper_', (2, 38, 3), 6, 2, 6, 3, cv, fp, [-10, -28, 42], z0=1))
            body_k.append(wing(side, 'mid_', (2, 34, 3), 6, 2, 7, 3, cv, fp, [0, -34, 8], z0=2.5))
            body_k.append(wing(side, 'low_', (2, 29, 3), 5, 2, 5, 2, cv, fp, [10, -40, -26], z0=4))
        body_k.append(tail())
    else:
        fp = feather(FEA, FEA_TIP, SHAFT)
        cv = feather(COVERT, FEA, None)
        for side in ('right', 'left'):
            body_k.append(wing(side, '', (2, 36, 3), 7, 2, 9, 3, cv, fp, [-4, -24, 18], z0=1))
    body = Group('body', [0, 24, 0], body_k)
    root_k = [body]
    if not demon:
        root_k.append(Group('false_shadow', [0, 0, 0], [C('false_shadow', [-20, .1, -20], [20, .1, 20], decal(shadow_fn))]))
    return Group('root', [0, 0, 0], root_k)

# ---------- 애니메이션 ----------
def wings_of(G, demon):
    names = [g.name for g in G if g.name.endswith('wing')]
    return names

def anims_elysia(G):
    n = 'animation.elysia.'
    W = ['right_wing', 'left_wing']
    def wpair(rz, ry=0, t=0, it=cm):  # 오른쪽 기준 값 → 왼쪽은 y,z 반전
        return {'right_wing': [('rotation', t, 0, ry, rz, it)], 'left_wing': [('rotation', t, 0, -ry, -rz, it)]}
    def arms(t, rx, rz=0, frx=0, it=cm, only=None):
        d = {}
        for s, sg in (('right', 1), ('left', -1)):
            if only and s != only: continue
            d[f'{s}_arm'] = [('rotation', t, rx, 0, rz * sg, it)]
            d[f'{s}_forearm'] = [('rotation', t, frx, 0, 0, it)]
        return d
    # spawn: 하늘에서 내려오며 날개를 펼침
    spawn = merge(
        {'root': [('position', 0, 0, 34, 0), ('position', 1.8, 0, 2, 0, cm), ('position', 2.4, 0, 0, 0, cm), ('position', 3, 0, 0, 0)],
         'head': [('rotation', 0, 25, 0, 0), ('rotation', 1.8, 25, 0, 0, cm), ('rotation', 2.5, -6, 0, 0, cm), ('rotation', 3, 0, 0, 0, cm)],
         'halo': [('rotation', 0, 0, 0, 0), ('rotation', 3, 0, 360, 0)]},
        wpair(-25, -55, 0, 'linear'), wpair(-25, -55, 1.6), wpair(26, 12, 2.3), wpair(0, 0, 3),
        arms(0, 0, -5, 0, 'linear'), arms(1.8, 0, -5), arms(2.4, 0, 38), arms(3, 0, 0))
    # idle: 부유 + 날개 + 후광 회전
    L = 3.0
    idle = {'body': pos_wave(.7, L), 'halo': [('rotation', 0, 0, 0, 0), ('rotation', L, 0, 360, 0)],
            'head': rot_wave(2.5, L, 0, 1), 'right_arm': rot_wave(4, L, 0, .5), 'left_arm': rot_wave(-3, L, 0, .5),
            'right_wing': [(r[0], r[1], 0, 0, r[4], cm) for r in rot_wave(5, L, 2)],
            'left_wing': [(r[0], r[1], 0, 0, -r[4], cm) for r in rot_wave(5, L, 2)],
            'scales_beam': rot_wave(4, L, 2, 2)}
    L = 1.6
    walk = {'body': [(k[0], k[1], 10 + k[2], k[3], k[4], cm) for k in rot_wave(3, L, 0)] + [],
            'right_wing': [(r[0], r[1], 0, -6, r[4], cm) for r in rot_wave(12, L, 2)],
            'left_wing': [(r[0], r[1], 0, 6, -r[4], cm) for r in rot_wave(12, L, 2)],
            'right_arm': [('rotation', t, -14 + v, 0, 7, cm) for _, t, v, *_ in rot_wave(6, L, 0)],
            'left_arm': [('rotation', t, -10 - v, 0, -7, cm) for _, t, v, *_ in rot_wave(6, L, 0)],
            'halo': [('rotation', 0, 0, 0, 0), ('rotation', L, 0, 360, 0)]}
    walk['body'] = merge({'body': walk['body']}, {'body': pos_wave(.6, L, 1, 0, 8)})['body']
    # attack: 오른손을 높이 들어 빛의 창을 내리꽂음
    attack = merge(
        {'right_arm': [('rotation', 0, 0, 0, 0), ('rotation', .45, 165, 0, 14, cm), ('rotation', .62, 172, 0, 10, cm), ('rotation', .78, 35, 0, 0), ('rotation', 1.2, 0, 0, 0, cm)],
         'right_forearm': [('rotation', 0, 0, 0, 0), ('rotation', .45, -10, 0, 0, cm), ('rotation', .78, 20, 0, 0), ('rotation', 1.2, 0, 0, 0, cm)],
         'body': [('rotation', 0, 0, 0, 0), ('rotation', .45, -8, 10, 0, cm), ('rotation', .8, 14, -6, 0), ('rotation', 1.2, 0, 0, 0, cm)],
         'head': [('rotation', 0, 0, 0, 0), ('rotation', .45, -12, 0, 0, cm), ('rotation', .8, 8, 0, 0), ('rotation', 1.2, 0, 0, 0, cm)]},
        wpair(0, 0, 0, 'linear'), wpair(22, 10, .45), wpair(-8, -12, .8, 'linear'), wpair(0, 0, 1.2))
    # offer: 두 손을 내밀어 제안 (마지막 자세 유지)
    offer = merge(
        {'head': [('rotation', 0, 0, 0, 0), ('rotation', 1.0, 10, 0, 8, cm), ('rotation', 2, 10, 0, 8, cm)],
         'body': [('rotation', 0, 0, 0, 0), ('rotation', 1.0, 6, 0, 0, cm), ('rotation', 2, 6, 0, 0, cm)],
         'scales': [('rotation', 0, 0, 0, 0), ('rotation', 1.0, -15, 0, 0, cm), ('rotation', 2, -15, 0, 0, cm)]},
        arms(0, 0, 0, 0, 'linear'), arms(1.0, 62, 16, 10), arms(2, 62, 16, 10),
        wpair(0, 0, 0, 'linear'), wpair(10, 14, 1.0), wpair(10, 14, 2))
    # cast: 두 팔을 하늘로 (심판의 빛 등 기믹 시전)
    cast = merge(
        {'body': [('position', 0, 0, 0, 0), ('position', .7, 0, 4, 0, cm), ('position', 1.6, 0, 4, 0, cm), ('position', 2, 0, 0, 0, cm),
                  ('rotation', 0, 0, 0, 0), ('rotation', .7, -8, 0, 0, cm), ('rotation', 1.6, -8, 0, 0, cm), ('rotation', 2, 0, 0, 0, cm)],
         'head': [('rotation', 0, 0, 0, 0), ('rotation', .7, -22, 0, 0, cm), ('rotation', 1.6, -22, 0, 0, cm), ('rotation', 2, 0, 0, 0, cm)],
         'halo': [('position', 0, 0, 0, 0), ('position', .7, 0, 3, 0, cm), ('position', 1.6, 0, 3, 0, cm), ('position', 2, 0, 0, 0, cm),
                  ('rotation', 0, 0, 0, 0), ('rotation', 2, 0, 540, 0)]},
        arms(0, 0, 0, 0, 'linear'), arms(.7, 168, 22, -5), arms(1.6, 168, 22, -5), arms(2, 0, 0, 0),
        wpair(0, 0, 0, 'linear'), wpair(30, 18, .7), wpair(30, 18, 1.6), wpair(0, 0, 2))
    # reveal: 정체 폭로 직전 — 머리를 감싸 쥐고 경련, 후광 기울어짐, 날개 처짐 (끝 자세 유지 → 벨자르 spawn으로 교체)
    jerk = [('rotation', 0, 0, 0, 0)]
    for i, (rx, ry, rz) in enumerate([(14, 20, -12), (-10, -24, 16), (18, 12, 8), (-6, -18, -14), (22, 6, 10), (30, 0, 0)]):
        jerk.append(('rotation', .4 + i * .42, rx, ry, rz))
    jerk.append(('rotation', 4, 30, 0, 0, cm))
    reveal = merge(
        {'head': jerk,
         'body': [('rotation', 0, 0, 0, 0), ('rotation', 1.2, 12, 0, 0, cm), ('rotation', 3.0, 24, 0, 0, cm), ('rotation', 4, 26, 0, 0, cm),
                  ('position', 0, 0, 0, 0), ('position', 3.0, 0, -3, 0, cm), ('position', 4, 0, -3, 0)],
         'halo': [('rotation', 0, 0, 0, 0), ('rotation', 1.5, 8, 200, 16, cm), ('rotation', 2.6, 30, 260, 34, cm), ('rotation', 4, 34, 270, 38, cm),
                  ('position', 0, 0, 0, 0), ('position', 2.6, 1, -2, 0, cm), ('position', 4, 1.5, -3, 0, cm)],
         'scales': [('rotation', 0, 0, 0, 0), ('rotation', 2.2, 0, 0, 0), ('rotation', 2.6, 40, 0, 0), ('rotation', 4, 70, 0, 0, cm)],
         'scales_beam': [('rotation', 0, 0, 0, 0), ('rotation', 2.2, 0, 0, 30), ('rotation', 4, 0, 0, 50, cm)]},
        arms(0, 0, 0, 0, 'linear'), arms(.8, 150, -24, 65), arms(3.6, 150, -24, 65), arms(4, 150, -24, 65),
        wpair(0, 0, 0, 'linear'), wpair(-10, -20, 1.5), wpair(-34, -48, 3.2), wpair(-36, -50, 4))
    death = merge(
        {'body': [('rotation', 0, 0, 0, 0), ('rotation', 1.5, 35, 0, 0, cm), ('rotation', 3, 40, 0, 0),
                  ('position', 0, 0, 0, 0), ('position', 1.5, 0, -6, 0, cm), ('position', 3, 0, -8, 0)],
         'head': [('rotation', 0, 0, 0, 0), ('rotation', 1.5, 25, 0, 0, cm), ('rotation', 3, 30, 0, 0)],
         'halo': [('position', 0, 0, 0, 0), ('position', 1.0, 0, 2, 0, cm), ('position', 3, 0, 4, 0), ('scale', 0, 1, 1, 1), ('scale', 2.4, 1, 1, 1), ('scale', 3, 0, 0, 0)]},
        wpair(0, 0, 0, 'linear'), wpair(-40, -50, 2), wpair(-40, -50, 3), arms(0, 0, 0, 0, 'linear'), arms(2, 20, -10, 10), arms(3, 20, -10, 10))
    return [anim(n + 'spawn', 3.0, 'once', spawn, G), anim(n + 'idle', 3.0, 'loop', idle, G),
            anim(n + 'walk', 1.6, 'loop', walk, G), anim(n + 'attack', 1.2, 'once', attack, G),
            anim(n + 'offer', 2.0, 'hold', offer, G), anim(n + 'cast', 2.0, 'once', cast, G),
            anim(n + 'reveal', 4.0, 'hold', reveal, G), anim(n + 'death', 3.0, 'hold', death, G)]

PAIRS = [('upper_', 1.0, 0.0), ('mid_', .8, .7), ('low_', .6, 1.4)]
def anims_belzar(G):
    n = 'animation.belzar.'
    def wings(t, rx=0, ry=0, rz=0, it=cm, scale=None):
        d = {}
        for p, k, _ in PAIRS:
            d[f'right_{p}wing'] = [('rotation', t, rx, ry, rz * k, it)]
            d[f'left_{p}wing'] = [('rotation', t, rx, -ry, -rz * k, it)]
            if scale is not None:
                d[f'right_{p}wing'].append(('scale', t, scale, scale, scale, it))
                d[f'left_{p}wing'].append(('scale', t, scale, scale, scale, it))
        return d
    def arms(t, rx, rz=0, frx=0, it=cm, only=None):
        d = {}
        for s, sg in (('right', 1), ('left', -1)):
            if only and s != only: continue
            d[f'{s}_arm'] = [('rotation', t, rx, 0, rz * sg, it)]
            d[f'{s}_forearm'] = [('rotation', t, frx, 0, 0, it)]
        return d
    def wing_wave(a, L, yb=0):
        d = {}
        for p, k, ph in PAIRS:
            d[f'right_{p}wing'] = [(r[0], r[1], 0, yb, r[4] * k, cm) for r in rot_wave(a, L, 2, ph)]
            d[f'left_{p}wing'] = [(r[0], r[1], 0, -yb, -r[4] * k, cm) for r in rot_wave(a, L, 2, ph)]
        return d
    # spawn: 껍데기를 찢고 날개 6장이 터져 나옴
    spawn = merge(
        {'body': [('rotation', 0, 26, 0, 0), ('rotation', 1.0, 30, 0, 0, cm), ('rotation', 1.4, -14, 0, 0), ('rotation', 2.2, -6, 0, 0, cm), ('rotation', 3, 0, 0, 0, cm),
                  ('position', 0, 0, -3, 0), ('position', 1.0, 0, -4, 0, cm), ('position', 1.4, 0, 2, 0), ('position', 3, 0, 0, 0, cm)],
         'head': [('rotation', 0, 30, 0, 0), ('rotation', 1.0, 34, 0, 0, cm), ('rotation', 1.5, -30, 0, 0), ('rotation', 2.4, -24, 0, 0, cm), ('rotation', 3, 0, 0, 0, cm)],
         'tail': [('rotation', 0, 30, 0, 0), ('rotation', 1.0, 30, 0, 0), ('rotation', 1.6, -20, 0, 0, cm), ('rotation', 3, 0, 0, 0, cm)],
         'broken_halo': [('rotation', 0, 0, 0, 0), ('rotation', 1.4, 0, 0, 0), ('rotation', 1.6, 10, 0, -12), ('rotation', 3, 0, 0, 0, cm)]},
        wings(0, 0, -70, -30, 'linear', .3), wings(1.0, 0, -70, -30, cm, .3), wings(1.4, 0, 18, 30, 'linear', 1.15), wings(2.2, 0, 10, 18, cm, 1), wings(3, 0, 0, 0, cm, 1),
        arms(0, 150, -24, 65, 'linear'), arms(1.0, 150, -24, 65), arms(1.4, 40, 55, -10, 'linear'), arms(2.4, 30, 45, -5), arms(3, 0, 0, 0))
    L = 2.4
    idle = merge({'body': pos_wave(.8, L), 'head': rot_wave(3, L, 0, 1.2),
                  'tail': [(r[0], r[1], 0, r[2], 0, cm) for r in rot_wave(14, L, 0)],
                  'tail3': rot_wave(-10, L, 0, 1),
                  'right_arm': rot_wave(5, L, 0, .4), 'left_arm': rot_wave(-4, L, 0, .4),
                  'broken_halo': rot_wave(4, L, 2, .3)}, wing_wave(9, L))
    L = 1.4
    walk = merge({'body': [(k[0], k[1], 12 + k[2], 0, 0, cm) for k in rot_wave(3, L, 0)],
                  'tail': [(r[0], r[1], -10, r[2], 0, cm) for r in rot_wave(20, L, 0)],
                  'right_arm': [('rotation', t, -18 + v, 0, 10, cm) for _, t, v, *_ in rot_wave(8, L, 0)],
                  'left_arm': [('rotation', t, -14 - v, 0, -10, cm) for _, t, v, *_ in rot_wave(8, L, 0)]},
                 wing_wave(16, L, -8))
    walk['body'] = merge({'body': walk['body']}, {'body': pos_wave(.7, L)})['body']
    # attack: 오른손 할퀴기 (대각 내려베기)
    attack = merge(
        {'right_arm': [('rotation', 0, 0, 0, 0), ('rotation', .35, 150, 0, 45, cm), ('rotation', .55, 40, 0, -35), ('rotation', 1.0, 0, 0, 0, cm)],
         'right_forearm': [('rotation', 0, 0, 0, 0), ('rotation', .35, -20, 0, 0, cm), ('rotation', .55, 15, 0, 0), ('rotation', 1.0, 0, 0, 0, cm)],
         'body': [('rotation', 0, 0, 0, 0), ('rotation', .35, -6, 22, 0, cm), ('rotation', .55, 12, -24, 0), ('rotation', 1.0, 0, 0, 0, cm)],
         'tail': [('rotation', 0, 0, 0, 0), ('rotation', .35, 0, -20, 0, cm), ('rotation', .6, 0, 30, 0), ('rotation', 1.0, 0, 0, 0, cm)]},
        wings(0, 0, 0, 0, 'linear'), wings(.35, 0, 15, 20), wings(.6, 0, -15, -10), wings(1.0, 0, 0, 0))
    # collect(수금): 장부를 높이 들었다가 바닥에 내리찍음
    collect = merge(
        {'left_arm': [('rotation', 0, 0, 0, 0), ('rotation', .7, 170, 0, -10, cm), ('rotation', .95, 175, 0, -10, cm), ('rotation', 1.15, 55, 0, 0), ('rotation', 1.8, 0, 0, 0, cm)],
         'left_forearm': [('rotation', 0, 0, 0, 0), ('rotation', .7, -40, 0, 0, cm), ('rotation', 1.15, 0, 0, 0), ('rotation', 1.8, 0, 0, 0, cm)],
         'ledger_book': [('rotation', 0, 0, 0, 0), ('rotation', .7, 60, 0, 0, cm), ('rotation', 1.15, -20, 0, 0), ('rotation', 1.8, 0, 0, 0, cm)],
         'right_arm': [('rotation', 0, 0, 0, 0), ('rotation', .7, 80, 0, 20, cm), ('rotation', 1.15, 70, 0, 30), ('rotation', 1.8, 0, 0, 0, cm)],
         'body': [('rotation', 0, 0, 0, 0), ('rotation', .7, -14, 0, 0, cm), ('rotation', 1.15, 22, 0, 0), ('rotation', 1.8, 0, 0, 0, cm),
                  ('position', 0, 0, 0, 0), ('position', .7, 0, 3, 0, cm), ('position', 1.15, 0, -4, 0), ('position', 1.8, 0, 0, 0, cm)],
         'head': [('rotation', 0, 0, 0, 0), ('rotation', .7, -20, 0, 0, cm), ('rotation', 1.15, 14, 0, 0), ('rotation', 1.8, 0, 0, 0, cm)]},
        wings(0, 0, 0, 0, 'linear'), wings(.7, 0, 20, 34), wings(1.15, 0, -10, -20, 'linear'), wings(1.8, 0, 0, 0))
    # tail_swing: 몸을 돌려 꼬리로 후방 휩쓸기
    tail_swing = merge(
        {'body': [('rotation', 0, 0, 0, 0), ('rotation', .4, 0, -40, 0, cm), ('rotation', .75, 0, 95, 0), ('rotation', 1.2, 0, 0, 0, cm)],
         'tail': [('rotation', 0, 0, 0, 0), ('rotation', .4, -20, 50, 0, cm), ('rotation', .75, -30, -60, 0), ('rotation', 1.2, 0, 0, 0, cm)],
         'tail2': [('rotation', 0, 0, 0, 0), ('rotation', .4, 0, 25, 0, cm), ('rotation', .8, 0, -30, 0), ('rotation', 1.2, 0, 0, 0, cm)]},
        wings(0, 0, 0, 0, 'linear'), wings(.4, 0, -25, -10), wings(.75, 0, 20, 15), wings(1.2, 0, 0, 0))
    cast = merge(
        {'body': [('position', 0, 0, 0, 0), ('position', .7, 0, 5, 0, cm), ('position', 1.6, 0, 5, 0, cm), ('position', 2, 0, 0, 0, cm),
                  ('rotation', 0, 0, 0, 0), ('rotation', .7, -12, 0, 0, cm), ('rotation', 1.6, -12, 0, 0, cm), ('rotation', 2, 0, 0, 0, cm)],
         'head': [('rotation', 0, 0, 0, 0), ('rotation', .7, -26, 0, 0, cm), ('rotation', 1.6, -26, 0, 0, cm), ('rotation', 2, 0, 0, 0, cm)],
         'tail': [('rotation', 0, 0, 0, 0), ('rotation', .7, -25, 0, 0, cm), ('rotation', 1.6, -25, 0, 0, cm), ('rotation', 2, 0, 0, 0, cm)]},
        arms(0, 0, 0, 0, 'linear'), arms(.7, 100, 70, -10), arms(1.6, 100, 70, -10), arms(2, 0, 0, 0),
        wings(0, 0, 0, 0, 'linear'), wings(.7, 0, 22, 30), wings(1.6, 0, 22, 30), wings(2, 0, 0, 0))
    # death(봉인): 경련 → 무릎 꿇듯 주저앉음 → 날개가 오그라들며 사라짐
    death = merge(
        {'body': [('rotation', 0, 0, 0, 0), ('rotation', .3, -16, 8, 0), ('rotation', .6, -12, -10, 0), ('rotation', .9, -18, 6, 0),
                  ('rotation', 2.2, 34, 0, 0, cm), ('rotation', 4, 40, 0, 0, cm),
                  ('position', 0, 0, 0, 0), ('position', 1.0, 0, 3, 0, cm), ('position', 2.2, 0, -8, 0, cm), ('position', 4, 0, -10, 0, cm)],
         'head': [('rotation', 0, 0, 0, 0), ('rotation', .9, -35, 0, 0, cm), ('rotation', 2.4, 35, 0, 0, cm), ('rotation', 4, 40, 0, 0)],
         'tail': [('rotation', 0, 0, 0, 0), ('rotation', 2.2, 40, 0, 0, cm), ('rotation', 4, 45, 0, 0)],
         'broken_halo': [('position', 0, 0, 0, 0), ('position', 2.4, 0, -14, 0, cm), ('position', 4, 0, -14, 0),
                         ('rotation', 0, 0, 0, 0), ('rotation', 2.4, 70, 0, 30, cm), ('rotation', 4, 80, 0, 30)]},
        arms(0, 0, 0, 0, 'linear'), arms(.9, 160, 40, -20), arms(2.4, 20, 10, 30), arms(4, 15, 10, 30),
        wings(0, 0, 0, 0, 'linear', 1), wings(.9, 0, 25, 40, cm, 1.1), wings(2.6, 0, -60, -40, cm, .6), wings(4, 0, -70, -45, cm, 0))
    return [anim(n + 'spawn', 3.0, 'once', spawn, G), anim(n + 'idle', 2.4, 'loop', idle, G),
            anim(n + 'walk', 1.4, 'loop', walk, G), anim(n + 'attack', 1.0, 'once', attack, G),
            anim(n + 'collect', 1.8, 'once', collect, G), anim(n + 'tail_swing', 1.2, 'once', tail_swing, G),
            anim(n + 'cast', 2.0, 'once', cast, G), anim(n + 'death', 4.0, 'hold', death, G)]

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'; os.makedirs(O, exist_ok=True)
    apply_palette(ELYSIA); export('elysia', build('elysia'), anims_elysia, 7, O + 'elysia.bbmodel', (1.4, 3.6, 2.8))
    apply_palette(ELYSIA_SHINY); export('elysia_shiny', build('elysia'), anims_elysia, 7, O + 'elysia_shiny.bbmodel', (1.4, 3.6, 2.8))
    apply_palette(BELZAR); export('belzar', build('belzar'), anims_belzar, 8, O + 'belzar.bbmodel', (1.6, 3.8, 2.8))
    apply_palette(BELZAR_SHINY); export('belzar_shiny', build('belzar'), anims_belzar, 8, O + 'belzar_shiny.bbmodel', (1.6, 3.8, 2.8))
