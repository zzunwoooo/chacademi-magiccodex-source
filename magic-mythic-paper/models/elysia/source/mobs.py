"""잡몹 3종: 성가대 아기천사 / 이자 징수 임프 / 채무자의 망령"""
from common import *
import os

def apply_palette(p): globals().update(p)

# =====================================================================
# 1. 성가대 아기천사 (choir_cherub)
# =====================================================================
CHERUB = dict(SKIN=(250, 222, 200), SKIN_D=(226, 186, 160), CLOTH=(246, 244, 236), GOLD=(232, 188, 80), GOLD_HI=(255, 236, 160),
              HAIR=(246, 210, 120), HAIR_D=(210, 168, 80), EYE=(60, 44, 40), CHEEK=(244, 156, 150), BOOK=(150, 40, 46),
              PAGE=(240, 230, 204), FEA=(250, 248, 242), FEA_TIP=(214, 206, 190), IMP_SHADOW=(26, 8, 30, 110))
CHERUB_SHINY = dict(CHERUB, CLOTH=(220, 230, 252), GOLD=(214, 128, 150), GOLD_HI=(255, 206, 220), HAIR=(236, 236, 250),
                    HAIR_D=(176, 182, 216), BOOK=(40, 70, 150), FEA=(236, 244, 255), FEA_TIP=(150, 186, 236), EYE=(60, 90, 170))

def cherub_face(img, r, rng):
    for lx, ly in [(1, 3), (2, 3), (5, 3), (6, 3), (1, 4), (2, 4), (5, 4), (6, 4)]: dot(img, r, lx, ly, EYE)
    for lx, ly in [(2, 3), (6, 3)]: dot(img, r, lx, ly, (255, 255, 255))
    for lx, ly in [(0, 5), (7, 5)]: dot(img, r, lx, ly, CHEEK)
    for lx in (3, 4): dot(img, r, lx, 5, (190, 100, 96))

def imp_shadow(u, v, rng):
    x, y = u - .5, v - .5; ax = abs(x); a = IMP_SHADOW
    if (x / .16) ** 2 + ((y - .05) / .22) ** 2 < 1: return a
    if x * x + (y + .22) ** 2 < .13 ** 2: return a
    if (ax - .1) ** 2 + (y + .38) ** 2 < .05 ** 2: return a
    if .15 < ax < .42 and abs(y + .02 - (ax - .15) * .6) < .06 * (1 - (ax - .15) / .3) + .015: return a
    if .2 < y < .44 and abs(x - .06 * math.sin(y * 12)) < .025: return a
    return None

def build_cherub():
    sk = shaded(SKIN, grad=.08)
    def leg(side):
        m = side == 'left'
        shin = Group(f'{side}_shin', MP([1.5, 5, 0], m), [
            C(f'{side}_shin', [.5, 3, -1], [2.5, 5, 1], sk, m),
            C(f'{side}_foot', [.5, 2, -2], [2.5, 3, 1], sk, m)], [-15, 0, 0])
        return Group(f'{side}_leg', MP([1.5, 8, 0], m), [C(f'{side}_thigh', [.5, 5, -1.5], [2.5, 8, 1.5], sk, m), shin], MR([20, 0, 4], m))
    def arm(side):
        m = side == 'left'
        fore = Group(f'{side}_forearm', MP([4, 10.5, 0], m), [
            C(f'{side}_forearm', [3, 8, -1], [5, 11, 1], sk, m),
            C(f'{side}_hand', [3, 7, -1], [5, 8, 1], shaded(SKIN_D), m)], MR([55, 0, 0], m))
        return Group(f'{side}_arm', MP([4, 13, 0], m), [C(f'{side}_upper_arm', [3, 10, -1], [5, 13, 1], sk, m), fore], MR([20, 0, -14], m))
    book = Group('hymn_book', [0, 11, -6.5], [
        Group('book_left', [0, 11, -6.5], [C('book_left_cover', [-4, 9, -7], [0, 14, -6], shaded(BOOK)),
                                            C('book_left_page', [-3.5, 9.5, -6], [-.5, 13.5, -6], shaded(PAGE, noise=.02))], [0, 22, 0]),
        Group('book_right', [0, 11, -6.5], [C('book_right_cover', [0, 9, -7], [4, 14, -6], shaded(BOOK)),
                                             C('book_right_page', [.5, 9.5, -6], [3.5, 13.5, -6], shaded(PAGE, noise=.02))], [0, -22, 0]),
        C('book_ribbon', [-.5, 6, -6.5], [.5, 9, -6.5], shaded(GOLD))], [-30, 0, 0])
    hk = [C('head', [-4, 14, -4], [4, 21, 4], with_faces(sk, north=cherub_face)),
          C('hair_cap', [-4.5, 20, -4.5], [4.5, 22, 4.5], hair(HAIR, HAIR_D)),
          C('hair_back', [-4.5, 16, 3.5], [4.5, 21, 4.5], hair(HAIR, HAIR_D)),
          C('forelock', [-1, 21, -5], [1, 23, -3], hair(HAIR, HAIR_D))]
    for i, (x, z) in enumerate([(-4, -2), (2.5, -2), (-4, 1.5), (2.5, 1.5), (-1, 2.5)]):
        hk.append(C(f'curl{i}', [x, 21, z], [x + 2, 23, z + 2], hair(HAIR, HAIR_D)))
    hk.append(halo('halo', 25, 3, gold(GOLD, GOLD_HI)))
    head = Group('head', [0, 14, 0], hk)
    fp = feather(FEA, FEA_TIP, None); cv = feather(FEA, FEA, None)
    body = Group('body', [0, 8, 0], [
        C('torso', [-3, 8, -2.5], [3, 14, 2.5], sk),
        C('belly', [-2, 9, -3.5], [2, 12, -2.5], sk),
        C('cloth', [-3.5, 7, -3], [3.5, 10, 3], robe(CLOTH, GOLD, 1)),
        C('cloth_drape', [1, 10, -3], [4, 13, -2], robe(CLOTH, ph=2)),
        C('cloth_tail', [-3, 4, 2], [0, 7, 3], robe(CLOTH, GOLD, 1, 3)),
        head, arm('right'), arm('left'), book, leg('right'), leg('left'),
        wing('right', '', (1, 13, 2.5), 3, 2, 3, 1, cv, fp, [-5, -25, 25], rise=1, second=False),
        wing('left', '', (1, 13, 2.5), 3, 2, 3, 1, cv, fp, [-5, -25, 25], rise=1, second=False)])
    shift(body, 3)
    return Group('root', [0, 0, 0], [body, Group('false_shadow', [0, 0, 0], [C('false_shadow', [-8, .1, -8], [8, .1, 8], decal(imp_shadow))])])

def anims_cherub(G):
    n = 'animation.choir_cherub.'
    flut = lambda L, a, yb=0: {'right_wing': [(r[0], r[1], 0, yb, r[4], cm) for r in rot_wave(a, L, 2, n=8)],
                                'left_wing': [(r[0], r[1], 0, -yb, -r[4], cm) for r in rot_wave(a, L, 2, n=8)]}
    spawn = merge({'root': [('scale', 0, 0, 0, 0), ('scale', .5, 1.2, 1.2, 1.2, cm), ('scale', .7, 1, 1, 1, cm), ('scale', 1.2, 1, 1, 1),
                            ('rotation', 0, 0, 0, 0), ('rotation', .6, 0, 360, 0, cm), ('rotation', 1.2, 0, 360, 0)],
                   'halo': [('position', 0, 0, 6, 0), ('position', .8, 0, 0, 0, cm), ('position', 1.2, 0, 0, 0)]}, flut(.3, 22))
    spawn['right_wing'] = [k for k in spawn['right_wing']]
    L = 2.0
    idle = merge({'body': pos_wave(1.0, L), 'head': rot_wave(4, L, 2, 1), 'halo': [('rotation', 0, 0, 0, 0), ('rotation', L, 0, 360, 0)],
                  'right_leg': rot_wave(12, L, 0), 'left_leg': rot_wave(-12, L, 0), 'hymn_book': rot_wave(4, L, 0, 2)},
                 {'right_wing': [(r[0], r[1], 0, 0, r[4], cm) for r in rot_wave(20, L / 4, 2)] + [(x[0], x[1] + L / 4 * k, *x[2:]) for k in (1, 2, 3) for x in [(r[0], r[1], 0, 0, r[4], cm) for r in rot_wave(20, L / 4, 2)][1:]],
                  'left_wing': [(r[0], r[1], 0, 0, -r[4], cm) for r in rot_wave(20, L / 4, 2)] + [(x[0], x[1] + L / 4 * k, *x[2:]) for k in (1, 2, 3) for x in [(r[0], r[1], 0, 0, -r[4], cm) for r in rot_wave(20, L / 4, 2)][1:]]})
    L = .8
    walk = merge({'body': [('rotation', 0, 18, 0, 0), ('rotation', L, 18, 0, 0)] + [], 'right_leg': [(*r[:2], 30 + r[2], 0, 0, cm) for r in rot_wave(10, L)],
                  'left_leg': [(*r[:2], 30 - r[2], 0, 0, cm) for r in rot_wave(10, L)], 'halo': [('rotation', 0, 0, 0, 0), ('rotation', L, 0, 360, 0)]},
                 {'right_wing': [(r[0], r[1], 0, 0, r[4], cm) for r in rot_wave(26, L / 2, 2)] + [(x[0], x[1] + L / 2, *x[2:]) for x in [(r[0], r[1], 0, 0, r[4], cm) for r in rot_wave(26, L / 2, 2)][1:]],
                  'left_wing': [(r[0], r[1], 0, 0, -r[4], cm) for r in rot_wave(26, L / 2, 2)] + [(x[0], x[1] + L / 2, *x[2:]) for x in [(r[0], r[1], 0, 0, -r[4], cm) for r in rot_wave(26, L / 2, 2)][1:]]})
    walk['body'] = merge({'body': walk['body']}, {'body': pos_wave(.6, L)})['body']
    # attack: 찬송 — 책을 높이 들고 고개를 젖혀 노래
    attack = {'head': [('rotation', 0, 0, 0, 0), ('rotation', .4, -25, 0, 0, cm), ('rotation', .9, -25, 0, 0, cm), ('rotation', 1.3, 0, 0, 0, cm)],
              'right_arm': [('rotation', 0, 0, 0, 0), ('rotation', .4, 60, 0, 0, cm), ('rotation', .9, 60, 0, 0, cm), ('rotation', 1.3, 0, 0, 0, cm)],
              'left_arm': [('rotation', 0, 0, 0, 0), ('rotation', .4, 60, 0, 0, cm), ('rotation', .9, 60, 0, 0, cm), ('rotation', 1.3, 0, 0, 0, cm)],
              'hymn_book': [('position', 0, 0, 0, 0), ('position', .4, 0, 5, -1, cm), ('position', .9, 0, 5, -1, cm), ('position', 1.3, 0, 0, 0, cm),
                            ('rotation', 0, 0, 0, 0), ('rotation', .4, -40, 0, 0, cm), ('rotation', .9, -40, 0, 0, cm), ('rotation', 1.3, 0, 0, 0, cm)],
              'body': [('position', 0, 0, 0, 0), ('position', .4, 0, 2, 0, cm), ('position', .65, 0, 1.4, 0, cm), ('position', .9, 0, 2, 0, cm), ('position', 1.3, 0, 0, 0, cm)]}
    death = {'body': [('rotation', 0, 0, 0, 0), ('rotation', .8, 0, 0, 90, cm), ('rotation', 1.5, 0, 0, 90),
                      ('position', 0, 0, 0, 0), ('position', .3, 0, 2, 0, cm), ('position', .8, 0, -7, 0), ('position', 1.5, 0, -7, 0)],
             'halo': [('position', 0, 0, 0, 0), ('position', .8, 0, 3, 0, cm), ('position', 1.5, 2, -6, 0)],
             'false_shadow': [('scale', 0, 1, 1, 1), ('scale', 1.0, 0, 1, 0, cm), ('scale', 1.5, 0, 1, 0)],
             'right_wing': [('rotation', 0, 0, 0, 0), ('rotation', .8, 0, -30, -20, cm), ('rotation', 1.5, 0, -30, -20)],
             'left_wing': [('rotation', 0, 0, 0, 0), ('rotation', .8, 0, 30, 20, cm), ('rotation', 1.5, 0, 30, 20)]}
    return [anim(n + 'spawn', 1.2, 'once', spawn, G), anim(n + 'idle', 2.0, 'loop', idle, G), anim(n + 'walk', .8, 'loop', walk, G),
            anim(n + 'attack', 1.3, 'once', attack, G), anim(n + 'death', 1.5, 'hold', death, G)]

# =====================================================================
# 2. 이자 징수 임프 (interest_imp)
# =====================================================================
IMP = dict(SKIN=(150, 42, 48), SKIN_D=(92, 22, 30), BELLY=(196, 98, 84), HORN=(214, 196, 158), HORN_TIP=(40, 26, 26),
           EYE=(255, 222, 60, 255), CLAW=(30, 20, 20), PAPER=(232, 216, 172), INK=(60, 30, 30), WOOD=(110, 70, 40),
           GOLD=(220, 176, 60), GOLD_HI=(255, 230, 140), CLOTH=(56, 40, 44), MEMB=(110, 30, 40), QUILL=(240, 236, 226))
IMP_SHINY = dict(IMP, SKIN=(92, 52, 150), SKIN_D=(52, 26, 96), BELLY=(150, 112, 200), EYE=(80, 240, 255, 255), MEMB=(60, 34, 110),
                 HORN=(60, 50, 70), HORN_TIP=(220, 210, 240), GOLD=(170, 200, 220))

def imp_face(img, r, rng):
    for lx in range(1, 6): dot(img, r, lx, 4, (40, 10, 14))
    for lx in (1, 3, 5): dot(img, r, lx, 4, (236, 226, 206))
    for lx, ly in [(0, 1), (6, 1)]: dot(img, r, lx, ly, SKIN_D)
def bill_paper(img, r, rng):
    x1, y1, x2, y2 = r
    for y in range(1, y2 - y1 - 1, 1):
        for x in range(0, x2 - x1):
            if rng.random() < .55: dot(img, r, x, y, INK)
    dot(img, r, 0, y2 - y1 - 1, (190, 30, 30))

def build_imp():
    sk = shaded(SKIN); skd = shaded(SKIN_D)
    def leg(side):
        m = side == 'left'
        foot = Group(f'{side}_foot', MP([2, 2, .5], m), [
            C(f'{side}_foot', [1, 0, -2.5], [3, 2, 1.5], skd, m),
            C(f'{side}_toe_claw0', [1, 0, -3.5], [2, 1, -2.5], shaded(CLAW), m),
            C(f'{side}_toe_claw1', [2, 0, -3.5], [3, 1, -2.5], shaded(CLAW), m)], MR([18, 0, 0], m))
        shin = Group(f'{side}_shin', MP([2, 5.5, 0], m), [C(f'{side}_shin', [1, 2, -.5], [3, 6, 1.5], skd, m), foot], MR([-40, 0, 0], m))
        return Group(f'{side}_leg', MP([2, 9, 0], m), [C(f'{side}_thigh', [.5, 5, -1.5], [3.5, 9, 1.5], sk, m), shin], MR([24, 0, 0], m))
    def arm(side):
        m = side == 'left'
        hk = [C(f'{side}_hand', [3, 4, -2], [5, 6, 0], sk, m),
              C(f'{side}_claw0', [3, 2, -2], [4, 4, -1], shaded(CLAW), m),
              C(f'{side}_claw1', [4, 2, -2], [5, 4, -1], shaded(CLAW), m)]
        if side == 'right':
            hk.append(Group('quill', [4, 5, -1], [C('quill_shaft', [3.5, 4, -1.5], [4.5, 11, -.5], shaded(QUILL)),
                                                  C('quill_vane', [4, 6, -3], [4, 11, -1], feather(QUILL, (200, 196, 186))),
                                                  C('quill_nib', [3.5, 3, -1.5], [4.5, 4, -.5], shaded(INK))], [-35, 0, 0]))
        else:
            hk.append(Group('bill', [-4, 4.5, -1], [C('bill_roll', [-5.5, 4, -2.5], [-2.5, 5, -1.5], shaded(WOOD)),
                                                    C('bill_paper', [-5, -1, -2], [-3, 4, -2], with_faces(shaded(PAPER, noise=.03), north=bill_paper, south=bill_paper))], [0, 0, 0]))
        hand = Group(f'{side}_hand', MP([4, 6, -1], m), hk)
        fore = Group(f'{side}_forearm', MP([4, 10, -1], m), [C(f'{side}_forearm', [3.5, 6, -1.5], [4.5 + .5, 10, -.5], sk, m) if False else
                                                             C(f'{side}_forearm', [3, 6, -2], [5, 10, 0], sk, m), hand], MR([30, 0, 0], m))
        return Group(f'{side}_arm', MP([4, 14, -1], m), [C(f'{side}_upper_arm', [3, 10, -2], [5, 14, 0], sk, m), fore], MR([-10, 0, -10], m))
    def ear(side):
        m = side == 'left'
        return Group(f'{side}_ear', MP([3.5, 18, -2], m), [
            C(f'{side}_ear', [3.5, 17, -2.5], [7.5, 19, -1.5], skd, m),
            C(f'{side}_ear_tip', [7.5, 18, -2.5], [9.5, 19, -1.5], skd, m)], MR([0, 0, 18], m))
    def horn(side):
        m = side == 'left'
        return [seg(f'{side}_horn0', [2, 20.5, -3], [3.2, 23.5, -1.8], 2, shaded(HORN), m),
                seg(f'{side}_horn1', [3.2, 23, -1.8], [3.4, 26, .5], 1, shaded(HORN_TIP), m)]
    head = Group('head', [0, 15, -1], [
        C('head', [-3.5, 15, -5], [3.5, 21, 1], with_faces(sk, north=imp_face)),
        C('brow', [-3.5, 19, -5.5], [3.5, 20, -4.5], skd),
        C('nose', [-1, 17, -7], [1, 19, -5], sk),
        C('nose_tip', [-.5, 16, -7], [.5, 17, -6], skd),
        C('chin', [-2, 14, -4.5], [2, 15, -1.5], sk),
        C('right_eye', [1, 18, -5.2], [3, 19, -5.2], glow(EYE)),
        C('left_eye', [-3, 18, -5.2], [-1, 19, -5.2], glow(EYE)),
        ear('right'), ear('left')] + horn('right') + horn('left'), [8, 0, 0])
    tail = None
    for i in reversed(range(4)):
        z0 = 1.5 + 2 * i
        kids = [C(f'tail{i}', [-.5, 8.5, z0], [.5, 9.5, z0 + 2], skd)]
        if i == 3: kids += [C('tail_spade', [-1.5, 8.5, z0 + 2], [1.5, 9.5, z0 + 4], skd)]
        if tail: kids.append(tail)
        tail = Group(f'tail{i}', [0, 9, z0], kids, [10 if i == 0 else -22, 0, 0])
    def bwing(side):
        m = side == 'left'
        return Group(f'{side}_wing', MP([1.5, 14, 2], m), [
            C(f'{side}_wing_bone', [1.5, 14, 2], [7.5, 15, 3], shaded(SKIN_D), m),
            C(f'{side}_wing_finger', [6.5, 10, 2], [7.5, 15, 3], shaded(SKIN_D), m),
            C(f'{side}_wing_membrane', [1.5, 10, 2.5], [7.5, 14, 2.5], robe(MEMB, torn=2, ph=3), m)], MR([0, -30, 25], m))
    body = Group('body', [0, 9, 0], [
        C('pelvis', [-2.5, 8, -1.5], [2.5, 10, 1.5], skd),
        C('loincloth', [-2, 6, -2], [2, 9, -1], robe(CLOTH, torn=1)),
        C('belt', [-3, 9, -2], [3, 10, 2], shaded(WOOD)),
        C('coin_pouch', [2.5, 7, -2], [4.5, 9.5 + .5, 0], gold(GOLD, GOLD_HI)),
        Group('chest', [0, 10, 0], [
            C('torso', [-3, 10, -2], [3, 15, 2], sk),
            C('belly', [-2, 10, -3], [2, 13, -2], shaded(BELLY)),
            C('back_spines', [-.5, 12, 2], [.5, 15, 3], shaded(HORN_TIP)),
            head, arm('right'), arm('left'), bwing('right'), bwing('left')], [16, 0, 0]),
        tail])
    return Group('root', [0, 0, 0], [body, leg('right'), leg('left')])

def anims_imp(G):
    n = 'animation.interest_imp.'
    spawn = {'root': [('position', 0, 0, -10, 0), ('position', .5, 0, 3, 0, cm), ('position', .8, 0, 0, 0, cm), ('position', 1.2, 0, 0, 0),
                      ('scale', 0, .6, .6, .6), ('scale', .5, 1.1, 1.1, 1.1, cm), ('scale', .8, 1, 1, 1)],
             'chest': [('rotation', 0, 30, 0, 0), ('rotation', .8, -10, 0, 0, cm), ('rotation', 1.2, 0, 0, 0, cm)],
             'head': [('rotation', 0, 0, 0, 0), ('rotation', .8, -15, 20, 0, cm), ('rotation', 1.0, -15, -20, 0, cm), ('rotation', 1.2, 0, 0, 0, cm)]}
    L = 1.6
    idle = {'chest': rot_wave(3, L, 0), 'tail0': [(r[0], r[1], 0, r[2], 0, cm) for r in rot_wave(18, L)],
            'right_ear': rot_wave(8, L / 2, 2, 0, 4) + [], 'quill': rot_wave(14, L / 4, 0) ,
            'right_wing': rot_wave(10, L, 2), 'left_wing': [(r[0], r[1], 0, 0, -r[4], cm) for r in rot_wave(10, L, 2)],
            'head': rot_wave(4, L, 2, 1)}
    idle['quill'] = [k for k in idle['quill']]
    L = .7; q = L / 4
    leg = lambda a: [('rotation', 0, a, 0, 0, cm), ('rotation', L / 2, -a, 0, 0, cm), ('rotation', L, a, 0, 0, cm)]
    walk = {'right_leg': leg(28), 'left_leg': leg(-28), 'right_arm': leg(-20), 'left_arm': leg(20),
            'right_shin': [('rotation', 0, 0, 0, 0, cm), ('rotation', L / 2, 0, 0, 0, cm), ('rotation', 3 * q, -30, 0, 0, cm), ('rotation', L, 0, 0, 0, cm)],
            'left_shin': [('rotation', 0, 0, 0, 0, cm), ('rotation', q, -30, 0, 0, cm), ('rotation', L / 2, 0, 0, 0, cm), ('rotation', L, 0, 0, 0, cm)],
            'body': [('position', 0, 0, 0, 0, cm), ('position', q, 0, -.5, 0, cm), ('position', L / 2, 0, 0, 0, cm), ('position', 3 * q, 0, -.5, 0, cm), ('position', L, 0, 0, 0, cm)],
            'tail0': [(r[0], r[1], 0, r[2], 0, cm) for r in rot_wave(20, L)], 'head': rot_wave(-4, L / 2, 0) }
    walk['head'] = [(x[0], x[1], *x[2:]) for x in walk['head']] + [(x[0], x[1] + L / 2, *x[2:]) for x in walk['head'][1:]]
    attack = {'root': [('position', 0, 0, 0, 0), ('position', .3, 0, 0, 2, cm), ('position', .5, 0, 2, -6), ('position', 1.0, 0, 0, 0, cm)],
              'chest': [('rotation', 0, 0, 0, 0), ('rotation', .3, -15, 0, 0, cm), ('rotation', .5, 25, 0, 0), ('rotation', 1.0, 0, 0, 0, cm)],
              'right_arm': [('rotation', 0, 0, 0, 0), ('rotation', .3, 150, 0, 20, cm), ('rotation', .5, 40, 0, 0), ('rotation', 1.0, 0, 0, 0, cm)],
              'left_arm': [('rotation', 0, 0, 0, 0), ('rotation', .3, 150, 0, 20, cm), ('rotation', .5, 40, 0, 0), ('rotation', 1.0, 0, 0, 0, cm)],
              'right_leg': [('rotation', 0, 0, 0, 0), ('rotation', .3, 20, 0, 0, cm), ('rotation', .5, -30, 0, 0), ('rotation', 1.0, 0, 0, 0, cm)],
              'left_leg': [('rotation', 0, 0, 0, 0), ('rotation', .3, 20, 0, 0, cm), ('rotation', .5, -30, 0, 0), ('rotation', 1.0, 0, 0, 0, cm)]}
    death = {'root': [('rotation', 0, 0, 0, 0), ('rotation', .6, -90, 0, 0, cm), ('rotation', 1.4, -90, 0, 0),
                      ('position', 0, 0, 0, 0), ('position', .3, 0, 3, 0, cm), ('position', .6, 0, 1, 3), ('position', 1.4, 0, 1, 3)],
             'bill': [('position', 0, 0, 0, 0), ('position', .6, 0, 2, 0, cm), ('position', 1.4, 3, -3, 2, cm), ('rotation', 0, 0, 0, 0), ('rotation', 1.4, 60, 0, 40, cm)],
             'tail0': [('rotation', 0, 0, 0, 0), ('rotation', 1.4, 30, 0, 0, cm)]}
    return [anim(n + 'spawn', 1.2, 'once', spawn, G), anim(n + 'idle', 1.6, 'loop', idle, G), anim(n + 'walk', .7, 'loop', walk, G),
            anim(n + 'attack', 1.0, 'once', attack, G), anim(n + 'death', 1.4, 'hold', death, G)]

# =====================================================================
# 3. 채무자의 망령 (debtor_wraith)
# =====================================================================
WRAITH = dict(ROBE=(118, 128, 132), ROBE_D=(70, 78, 84), BONE=(198, 196, 182), IRON=(72, 70, 76), RUST=(130, 76, 50),
              EYE=(170, 255, 236, 255), VOID=(16, 18, 22), PAPER=(226, 210, 168), INK=(70, 40, 30), SEAL=(170, 30, 30))
WRAITH_SHINY = dict(WRAITH, ROBE=(150, 120, 170), ROBE_D=(90, 66, 110), EYE=(255, 200, 90, 255), IRON=(150, 130, 70), RUST=(200, 160, 70))

def deed(img, r, rng):
    x1, y1, x2, y2 = r
    for y in range(1, y2 - y1 - 1):
        for x in range(0, x2 - x1):
            if rng.random() < .45: dot(img, r, x, y, INK)
    dot(img, r, (x2 - x1) // 2, y2 - y1 - 1, SEAL)
def void_face(img, r, rng):
    x1, y1, x2, y2 = r
    for lx, ly in [(1, 2), (4, 2)]: dot(img, r, lx, ly, EYE)
    for lx in range(1, 5): dot(img, r, lx, 4, (40, 44, 48))

def build_wraith():
    rb = lambda ph=0, t=0: robe(ROBE, ph=ph, torn=t, dark=ROBE_D)
    def arm(side):
        m = side == 'left'
        links = []
        for i in range(3):
            x = 3.5 - i * 1.5 if not m else 3.5 - i * 1.5
            links.append(C(f'{side}_link{i}', [x - 1.5, 5 - i * .5, -1], [x - .5 + 0, 6 - i * .5, 1] if i % 2 == 0 else [x - .5, 6 - i * .5, -1], shaded(IRON), m)
                         if False else C(f'{side}_link{i}', [x - 1.5, 4.5 - i * .5, -.5 if i % 2 else 0], [x - .5, 6.5 - i * .5, .5 if i % 2 else 0], shaded(IRON), m))
        hand = Group(f'{side}_hand', MP([5, 8, 0], m), [
            C(f'{side}_palm', [4, 6, -1], [6, 8, 1], shaded(BONE), m),
            C(f'{side}_finger0', [4, 4, -1], [5, 6, 0], shaded(BONE), m),
            C(f'{side}_finger1', [5, 3, -1], [6, 6, 0], shaded(BONE), m),
            C(f'{side}_finger2', [4.5, 4, 0], [5.5, 6, 1], shaded(BONE), m)] + links)
        fore = Group(f'{side}_forearm', MP([5, 15, 0], m), [
            C(f'{side}_forearm', [4.5, 8, -.5], [5.5, 15, .5], shaded(BONE), m),
            C(f'{side}_sleeve', [3.5, 12, -2], [6.5, 16, 2], rb(4, 2), m),
            C(f'{side}_shackle', [3.5, 8, -1.5], [6.5, 10, 1.5], shaded(IRON), m),
            C(f'{side}_shackle_rivet', [4.5, 8.5, -2], [5.5, 9.5, -1.5 + .5], shaded(RUST), m), hand], MR([48, 0, 10], m))
        return Group(f'{side}_arm', MP([5, 21, 0], m), [C(f'{side}_upper_sleeve', [3.5, 15, -2], [6.5, 21, 2], rb(3), m), fore], MR([10, 0, -6], m))
    head = Group('head', [0, 22, 0], [
        C('hood', [-4, 22, -4], [4, 30, 4], with_faces(rb(1), north=lambda img, r, rng: [dot(img, r, x, y, VOID) for x in range(1, 7) for y in range(2, 7)])),
        C('hood_brim', [-4.5, 28, -5], [4.5, 30, -4], rb(2)),
        C('right_hood_side', [3.5, 22, -5], [4.5, 28, -4], rb(3)),
        C('left_hood_side', [-4.5, 22, -5], [-3.5, 28, -4], rb(4)),
        C('hood_tip', [-2, 27, 4], [2, 30, 6], rb(5)),
        C('hood_tip2', [-1, 25, 6], [1, 28, 7], rb(6)),
        C('face_void', [-3, 23, -4.3], [3, 28, -4.3], with_faces(glow((16, 18, 22, 255)), north=void_face))], [18, 0, 0])
    chain = [seg('ball_chain0', [0, 11, 3.5], [0, 8, 6], 1, shaded(IRON)), seg('ball_chain1', [0, 8, 6], [0, 5, 8], 1, shaded(IRON)),
             seg('ball_chain2', [0, 5, 8], [0, 3.5, 9], 1, shaded(IRON))]
    ball = Group('iron_ball', [0, 3, 9.5], [C('iron_ball', [-2, 0, 7.5], [2, 4, 11.5], stone(IRON, crack=RUST)),
                                            C('iron_ball_band', [-2.5, 1.5, 7], [2.5, 2.5, 12], shaded(RUST))])
    body = Group('body', [0, 10, 0], [
        C('torso', [-4, 14, -3], [4, 22, 3], rb(0)),
        C('shoulders', [-5, 19, -3.5], [5, 22, 3.5], rb(1)),
        C('lower_robe', [-5, 6, -3.5], [5, 14, 3.5], rb(2, 3)),
        C('rag0', [-4.5, 1, -3.6], [-2.5, 6, -3.6], rb(3, 3)),
        C('rag1', [.5, 0, -3.6], [3.5, 6, -3.6], rb(4, 3)),
        C('rag2', [-3, 2, 3.6], [0, 6, 3.6], rb(5, 3)),
        C('rag3', [5.1, 1, -2], [5.1, 6, 1], rb(6, 3)),
        C('waist_chain', [-4.5, 11, -4], [4.5, 12, 3.5 + .5], shaded(IRON)),
        C('chest_deed', [-2, 15, -3.3], [2, 20, -3.3], with_faces(shaded(PAPER, noise=.03), north=deed)),
        C('deed_nail', [-.5, 19, -3.8], [.5, 20, -3.3 + .5], shaded(RUST)),
        head, arm('right'), arm('left')] + chain + [ball])
    shift(body, 2)
    return Group('root', [0, 0, 0], [body])

def anims_wraith(G):
    n = 'animation.debtor_wraith.'
    spawn = {'root': [('position', 0, 0, -26, 0), ('position', 1.6, 0, 1, 0, cm), ('position', 2.0, 0, 0, 0, cm)],
             'head': [('rotation', 0, 30, 0, 0), ('rotation', 1.6, 30, 0, 0, cm), ('rotation', 2.0, 0, 0, 0, cm)],
             'right_arm': [('rotation', 0, 160, 0, 0), ('rotation', 1.4, 120, 0, 0, cm), ('rotation', 2.0, 0, 0, 0, cm)],
             'left_arm': [('rotation', 0, 160, 0, 0), ('rotation', 1.4, 120, 0, 0, cm), ('rotation', 2.0, 0, 0, 0, cm)]}
    L = 3.0
    idle = {'body': pos_wave(1.0, L), 'head': rot_wave(4, L, 2, 1), 'right_arm': rot_wave(6, L, 0, .5), 'left_arm': rot_wave(6, L, 0, 2),
            'iron_ball': rot_wave(10, L, 0, 1.4), 'ball_chain0': [(r[0], r[1], 0, 0, r[2], cm) for r in rot_wave(4, L, 0)]}
    L = 2.0
    walk = {'body': [(k[0], k[1], 14 + k[2], 0, 0, cm) for k in rot_wave(3, L)], 'right_arm': [(r[0], r[1], 30 + r[2], 0, 0, cm) for r in rot_wave(10, L)],
            'left_arm': [(r[0], r[1], 30 - r[2], 0, 0, cm) for r in rot_wave(10, L)], 'iron_ball': rot_wave(18, L, 0, 1)}
    walk['body'] = merge({'body': walk['body']}, {'body': pos_wave(.8, L)})['body']
    attack = {'right_arm': [('rotation', 0, 0, 0, 0), ('rotation', .5, 120, 0, -20, cm), ('rotation', .75, 50, 0, 10), ('rotation', 1.4, 0, 0, 0, cm)],
              'left_arm': [('rotation', 0, 0, 0, 0), ('rotation', .5, 120, 0, -20, cm), ('rotation', .75, 50, 0, 10), ('rotation', 1.4, 0, 0, 0, cm)],
              'head': [('rotation', 0, 0, 0, 0), ('rotation', .5, -20, 0, 0, cm), ('rotation', .75, 20, 0, 0), ('rotation', 1.4, 0, 0, 0, cm)],
              'root': [('position', 0, 0, 0, 0), ('position', .5, 0, 1, 2, cm), ('position', .75, 0, 0, -5), ('position', 1.4, 0, 0, 0, cm)]}
    death = {'root': [('position', 0, 0, 0, 0), ('position', 2.0, 0, 6, 0, cm), ('scale', 0, 1, 1, 1), ('scale', .6, 1.1, .9, 1.1, cm), ('scale', 2.0, .1, 1.4, .1, cm)],
             'head': [('rotation', 0, 0, 0, 0), ('rotation', .6, -40, 0, 0, cm), ('rotation', 2.0, -40, 0, 0)],
             'right_arm': [('rotation', 0, 0, 0, 0), ('rotation', .6, 170, 0, 20, cm), ('rotation', 2.0, 170, 0, 20)],
             'left_arm': [('rotation', 0, 0, 0, 0), ('rotation', .6, 170, 0, 20, cm), ('rotation', 2.0, 170, 0, 20)],
             'iron_ball': [('position', 0, 0, 0, 0), ('position', 1.0, 0, -6, 0, cm), ('position', 2.0, 0, -8, 0)]}
    return [anim(n + 'spawn', 2.0, 'once', spawn, G), anim(n + 'idle', 3.0, 'loop', idle, G), anim(n + 'walk', 2.0, 'loop', walk, G),
            anim(n + 'attack', 1.4, 'once', attack, G), anim(n + 'death', 2.0, 'hold', death, G)]

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    for nm, pal, sh, b, a, hb in [('choir_cherub', CHERUB, CHERUB_SHINY, build_cherub, anims_cherub, (.6, 1.6, 1.15)),
                                  ('interest_imp', IMP, IMP_SHINY, build_imp, anims_imp, (.6, 1.2, .95)),
                                  ('debtor_wraith', WRAITH, WRAITH_SHINY, build_wraith, anims_wraith, (.8, 2.0, 1.6))]:
        apply_palette(pal); export(nm, b(), a, 11, O + nm + '.bbmodel', hb)
        apply_palette(sh); export(nm + '_shiny', b(), a, 11, O + nm + '_shiny.bbmodel', hb)
