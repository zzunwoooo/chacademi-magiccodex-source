"""도박의 악마 룰레타 · 봉인 전 (roulette_past) — 100년 전 전성기 "판돈의 왕".
팔 여섯(카드·주사위 / 지팡이·칩 탑 / 맨손 발톱), 왕관 얹은 실크햇, 붉은 망토, 등 뒤 태양 모양 대형 룰렛(바늘 둘)."""
from common import *
from weapon_ik import apply as weapon_apply
import roulette as RO
import math, os

ID = 'roulette_past'
PAL = RO.PAL
BASE = dict(
    SKIN=(168, 30, 40), DSKIN=(100, 14, 24), SUIT=(34, 22, 44), DSUIT=(16, 10, 22), SATIN=(92, 60, 110),
    VEST=(120, 22, 52), SHIRT=(234, 226, 208), GOLD=(244, 196, 76), DGOLD=(160, 112, 34),
    HORN=(240, 230, 204), DHORN=(110, 80, 58), EYE=(255, 226, 80, 255), PUPIL=(20, 6, 8), MOUTH=(34, 6, 12),
    TEETH=(244, 238, 218), BOW=(204, 30, 44), WOOD=(64, 34, 26), DWOOD=(38, 20, 16), CARD=(240, 234, 222),
    CARDINK=(184, 26, 36), CAPE=(150, 20, 34), DCAPE=(86, 8, 20), ERMINE=(240, 236, 226), GEM=(90, 230, 190, 255),
    SEC0=(198, 40, 46), SEC1=(30, 28, 36), SEC2=(34, 142, 78), SEC3=(44, 94, 196), SEC4=(132, 54, 176), SEC5=(232, 184, 40))
SHINY = dict(
    SKIN=(46, 128, 146), DSKIN=(20, 72, 88), SUIT=(236, 230, 216), DSUIT=(184, 174, 156), SATIN=(160, 190, 200),
    VEST=(30, 34, 60), SHIRT=(40, 40, 54), GOLD=(210, 218, 232), DGOLD=(122, 130, 148),
    HORN=(40, 34, 48), DHORN=(170, 70, 210), EYE=(255, 90, 210, 255), PUPIL=(30, 0, 30), MOUTH=(10, 30, 40),
    TEETH=(250, 250, 255), BOW=(40, 160, 190), WOOD=(220, 214, 200), DWOOD=(150, 140, 126), CARD=(30, 30, 40),
    CARDINK=(80, 220, 240), CAPE=(30, 60, 120), DCAPE=(14, 28, 66), ERMINE=(226, 236, 246), GEM=(255, 120, 220, 255))
PAL.clear(); PAL.update(BASE)
HITBOX = (2.6, 8.4, 6.1)
RO.CY = CY = 106
RO.RAD = RAD = 40
def apply_palette(p): PAL.update(p)

def ermine(img, face, r, rng):
    x1, y1, x2, y2 = r
    for y in range(y1, y2):
        for x in range(x1, x2):
            c = PAL['DSUIT'] if ((x - x1) % 5 == 2 and (y - y1) % 4 == 1) else PAL['ERMINE']
            img.putpixel((x, y), col(c, SHADE[face] * (1 + rng.uniform(-.05, .05))))

def monocle(img, face, r, rng):
    x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
    for y in range(y1, y2):
        for x in range(x1, x2):
            lx, ly = x - x1, y - y1
            edge = lx in (0, w - 1) or ly in (0, h - 1)
            corner = (lx in (0, w - 1)) and (ly in (0, h - 1))
            img.putpixel((x, y), (0, 0, 0, 0) if (corner or (not edge and face in ('north', 'south'))) else col(PAL['GOLD'], SHADE[face]))

def face_paint(img, r, rng):
    RO.face_paint(img, r, rng)
    w = r[2] - r[0]
    for lx, ly in ((w // 2 - 1, 2), (w // 2, 2), (w // 2 - 1, 3), (w // 2, 3)): px(img, r, lx, ly, col(PAL['GEM']))   # 이마 보석

def build():
    suit = RO.suit_p(); skin = sol(PAL, 'SKIN'); dskin = sol(PAL, 'DSKIN'); gold = sol(PAL, 'GOLD', .1)
    dgold = sol(PAL, 'DGOLD'); shirt = sol(PAL, 'SHIRT', .04); claw = tipped(PAL, 'DSKIN', 'HORN', 2)
    horn = tipped_up(PAL, 'HORN', 'DHORN', 2); gem = sol(PAL, 'GEM', .02); cape = grad(PAL, 'CAPE', 'DCAPE')
    card = lambda: faces(sol(PAL, 'CARD', .03), north=RO.card_face, south=RO.card_face)

    def leg(s):
        m, P, Rt = side(s)
        shin = Group(f'{s}_shin', P([7, 26, 0]), [
            C(f'{s}_shin', [3.5, 7, -3.5], [10.5, 26, 3.5], suit, m),
            C(f'{s}_greave', [3, 12, -4.5], [11, 22, -3.5], gold, m),
            C(f'{s}_spat', [3, 4, -4], [11, 9, 4], trim(PAL, 'SHIRT', 'DSUIT'), m),
            C(f'{s}_shoe', [3, 0, -8], [11, 4, 4], sol(PAL, 'DSUIT', .05), m),
            C(f'{s}_shoe_tip', [4, 0, -11], [10, 3, -8], sol(PAL, 'DSUIT', .05), m),
            C(f'{s}_shoe_curl', [6, 3, -12], [8, 6, -10], gold, m),
            C(f'{s}_buckle', [5, 2, -9], [9, 4, -8], gold, m)])
        return Group(f'{s}_leg', P([7, 44, 0]), [
            C(f'{s}_thigh', [3, 26, -4], [11, 46, 4], suit, m),
            C(f'{s}_knee', [4, 24, -5], [10, 29, -4], gold, m), shin])

    def upper_arm(s):   # 위 팔: 오른손 카드 부채(5장), 왼손 주사위 둘
        m, P, Rt = side(s)
        hold = []
        if s == 'right':
            for i, a in enumerate((-36, -18, 0, 18, 36)):
                hold.append(Group(f'right_card{i+1}', [17.5, 27, -6], [
                    C(f'right_card{i+1}', [15, 26, -7 - i * .0], [20, 35, -6], card())], [0, 0, a]))
        else:
            hold += [C('left_die', [-21, 26, -9], [-14, 33, -2], dice(PAL, 'CARD', 'CARDINK')),
                     C('left_die2', [-19, 33, -8], [-14, 38, -3], dice(PAL, 'BOW', 'CARD'), rot=[0, 25, 0])]
        fingers = [C(f'{s}_finger{i+1}', [x, 27, -4], [x + 2, 34, -2], claw, m) for i, x in enumerate((14.5, 16.5, 18.5))]
        hand = Group(f'{s}_hand', P([17.5, 42, 0]), [
            C(f'{s}_palm', [14.5, 34, -4], [20.5, 42, 3], skin, m), *fingers,
            C(f'{s}_ring', [16, 31, -4.5], [19, 32, -1.5], gold, m),
            C(f'{s}_thumb', [13, 33, -4], [15, 39, -2], claw, m), *hold])
        fore = Group(f'{s}_forearm', P([17.5, 63, 0]), [
            C(f'{s}_forearm', [14.5, 46, -3.5], [20.5, 64, 3.5], suit, m),
            C(f'{s}_cuff', [14, 42, -4], [21, 46, 4], faces(shirt, east=lambda i, r, g: px(i, r, 3, 2, col(PAL['GOLD'])),
                                                             west=lambda i, r, g: px(i, r, 3, 2, col(PAL['GOLD']))), m),
            C(f'{s}_cuff_band', [14, 46, -4], [21, 48, 4], gold, m), hand], [25, 0, 0])
        return Group(f'{s}_arm', P([17.5, 82, 0]), [
            C(f'{s}_upper_arm', [14, 62, -4], [21, 84, 4], suit, m),
            C(f'{s}_shoulder', [13, 78, -5], [22, 86, 5], sol(PAL, 'SUIT'), m),
            C(f'{s}_epaulette', [12, 86, -6], [24, 88, 6], gold, m),
            C(f'{s}_epaulette_fringe', [19, 81, -6.5], [24.5, 86, 6.5], ragged(stripes(PAL, 'GOLD', 'DGOLD', 2, 1), .4, 2), m),
            C(f'{s}_epaulette_gem', [16, 88, -1.5], [19, 90, 1.5], gem, m), fore], Rt([-8, 0, 12]))

    def mid_arm(s):   # 가운데 팔: 오른손 지팡이, 왼손 칩 탑
        m, P, Rt = side(s)
        hold = []
        if s == 'right':
            hold.append(Group('cane', [17, 36, -1], [
                C('cane_shaft', [16, -6, -2], [18, 44, 0], sol(PAL, 'DSUIT', .05)),
                C('cane_ferrule', [15.5, -8, -2.5], [18.5, -5, .5], gold),
                C('cane_collar', [15.5, 42, -2.5], [18.5, 45, .5], gold),
                C('cane_head', [14.5, 45, -3.5], [19.5, 50, 1.5], dice(PAL, 'BOW', 'GOLD', 'DGOLD')),
                C('cane_gem', [16, 50, -2], [18, 52, 0], gem)], [-62, 0, -24]))
        else:
            for i in range(6):
                hold.append(C(f'chip{i+1}', [-20, 26 + i * 2, -5 + (i % 2) * .5 - .25], [-14, 28 + i * 2, 1 + (i % 2) * .5 - .25],
                              stripes(PAL, ('SEC0', 'SEC2', 'SEC3', 'SEC5', 'SEC4', 'SEC1')[i], 'CARD', 3, 1)))
        fingers = [C(f'{s}_finger3_{i+1}', [x, 30, -3], [x + 2, 36, -1], claw, m) for i, x in enumerate((14, 16, 18))]
        if s == 'right':   # 지팡이를 쥔 손: 손가락이 자루를 감는다 (자루와 같은 기울기)
            fingers = [Group('right_grip3', [17, 36, -1], [C(f'right_finger3_{i+1}', [15, 32 + i * 2.5, -4], [19, 34 + i * 2.5, -3], claw) for i in range(3)]
                             + [C('right_finger3_side', [19, 32, -4], [20, 39, 0], claw), C('right_thumb3', [15, 35, 1], [19, 37, 2], claw)], [-62, 0, -24])]
        hand = Group(f'{s}_hand3', P([17, 40, 0]), [
            C(f'{s}_palm3', [14, 35, -3], [20, 41, 3], skin, m), *fingers, *hold])
        fore = Group(f'{s}_forearm3', P([17, 57, 0]), [
            C(f'{s}_forearm3', [14.5, 43, -3], [19.5, 58, 3], suit, m),
            C(f'{s}_cuff3', [14, 40, -3.5], [20, 44, 3.5], shirt, m), hand], [52, 0, 0])
        return Group(f'{s}_arm3', P([16, 73, 3]), [
            C(f'{s}_upper_arm3', [14, 56, 0], [20, 75, 6], suit, m), fore], Rt([4, 0, 26]))

    def lower_arm(s):   # 아래 팔: 맨살, 발톱
        m, P, Rt = side(s)
        fingers = [C(f'{s}_finger2_{i+1}', [x, 16, -3], [x + 1, 24, -1], claw, m) for i, x in enumerate((12, 14, 16))]
        hand = Group(f'{s}_hand2', P([14.5, 30, 0]), [C(f'{s}_palm2', [12, 24, -3], [17, 30, 2], skin, m), *fingers])
        fore = Group(f'{s}_forearm2', P([14.5, 49, 2]), [
            C(f'{s}_forearm2', [12, 30, -0.5], [17, 50, 4.5], skin, m),
            C(f'{s}_bracelet', [11.5, 38, -1], [17.5, 42, 5], gold, m),
            C(f'{s}_bracelet2', [11.5, 33, -1], [17.5, 35, 5], gold, m), hand], [40, 0, 0])
        return Group(f'{s}_arm2', P([13, 62, 3]), [
            C(f'{s}_upper_arm2', [11, 44, 0], [16, 64, 6], skin, m),
            C(f'{s}_elbow_spike', [14, 44, 5], [16, 48, 10], horn, m), fore], Rt([12, 0, 48]))

    def ear(s):
        m, P, Rt = side(s)
        return Group(f'{s}_ear', P([9, 97, 0]), [
            C(f'{s}_ear', [9, 94, -2], [13, 100, 1], skin, m),
            C(f'{s}_ear_tip', [12, 98, -1], [15, 104, 0], dskin, m),
            C(f'{s}_earring', [10, 91, -1], [12, 94, 0], gold, m)], Rt([0, 0, -15]))

    def horn_(s):
        m, P, Rt = side(s)
        tip = Group(f'{s}_horn_tip', P([15, 119, 0]), [
            C(f'{s}_horn2', [13, 118, -2], [17, 130, 2], sol(PAL, 'HORN'), m),
            C(f'{s}_horn3', [14, 130, -1.5], [17, 139, 1.5], horn, m),
            C(f'{s}_horn_band', [12.5, 121, -2.5], [17.5, 123, 2.5], gold, m)], Rt([0, 0, 26]))
        return Group(f'{s}_horn', P([9, 114, 0]), [
            C(f'{s}_horn1', [8, 112, -3], [15, 119, 3], sol(PAL, 'HORN'), m), tip,
            Group(f'{s}_fore_horn', P([7, 110, -8]), [
                C(f'{s}_fore_horn1', [6, 108, -13], [9, 111, -8], sol(PAL, 'HORN'), m),
                C(f'{s}_fore_horn2', [6.5, 108.5, -17], [8.5, 110.5, -13], horn, m)], Rt([-30, -20, 0]))], Rt([0, 0, -32]))

    crown = [C('crown_band', [-10.5, 124, -10.5], [10.5, 128, 9.5], faces(gold, north=lambda i, r, g: [px(i, r, x, 1, col(PAL['GEM'])) for x in (3, 4, 10, 11, 17, 18)]))]
    for i, (x, z) in enumerate([(-10, -10), (-4, -10), (2, -10), (8, -10), (-10, 7), (-4, 7), (2, 7), (8, 7), (-10, -2), (8, -2)]):
        crown.append(C(f'crown_point{i+1}', [x, 128, z], [x + 2, 132 + (2 if i in (1, 2) else 0), z + 2], tipped_up(PAL, 'GOLD', 'BOW', 1)))
    hat = Group('hat', [0, 106, 0], [
        C('hat_brim', [-14, 105, -14], [14, 107, 13], sol(PAL, 'DSUIT', .05)),
        C('hat_crown', [-9, 107, -9], [9, 125, 8], sol(PAL, 'SUIT', .05)),
        C('hat_band', [-10, 107, -10], [10, 111, 9], stripes(PAL, 'BOW', 'GOLD', 6, 1)),
        C('hat_card', [4, 109, -11], [8, 116, -10], faces(sol(PAL, 'CARD', .03), north=RO.card_face)),
        *crown, horn_('right'), horn_('left')])

    head = Group('head', [0, 88, 0], [
        C('neck', [-4, 82, -4], [4, 90, 4], skin),
        C('head', [-9, 88, -9], [9, 106, 8], faces(sol(PAL, 'SKIN', .06), north=face_paint)),
        C('brow', [-9, 100, -10], [9, 102, -9], dskin),
        C('nose', [-1, 95, -11], [1, 99, -9], skin),
        C('goatee', [-1, 80, -9], [1, 89, -7], tipped(PAL, 'DSKIN', 'SUIT', 3)),
        C('goatee_ring', [-1.5, 82, -9.5], [1.5, 84, -6.5], gold),
        C('monocle', [-8, 95, -10.5], [-2, 101, -9.5], monocle),
        C('monocle_chain', [-8.5, 86, -9.5], [-8.5, 95, -9.5 + 1], gold),
        ear('right'), ear('left'), hat])

    wk = []
    for y0 in range(-RAD, RAD, 4):
        y1 = y0 + 4; yi = 0 if y0 < 0 < y1 else min(abs(y0), abs(y1))
        hw = int(math.ceil(math.sqrt(max(0, RAD * RAD - yi * yi))))
        frm, to = [-hw, CY + y0, 17], [hw, CY + y1, 19]
        wk.append(C(f'wheel_row{(y0 + RAD) // 4 + 1}', frm, to, RO.wheel_paint((frm, to))))
    for i in range(12):   # 태양 살: 바깥으로 뻗은 금 칼날
        a = -i * 30
        long = i % 2 == 0
        wk.append(Cube(f'wheel_ray{i+1}', [-3, CY + RAD - 1, 16.5], [3, CY + RAD + (9 if long else 6), 18.5], gold, rot=[0, 0, a], origin=[0, CY, 17.5]))
        wk.append(Cube(f'wheel_ray_tip{i+1}', [-1.5, CY + RAD + (9 if long else 6), 17], [1.5, CY + RAD + (15 if long else 10), 18],
                       tipped_up(PAL, 'GOLD', 'BOW', 2) if long else gold, rot=[0, 0, a], origin=[0, CY, 17.5]))
        wk.append(Cube(f'wheel_peg{i+1}', [-1.5, CY + RAD - 4, 15.5], [1.5, CY + RAD - 1, 17], gem if long else dgold, rot=[0, 0, a + 15], origin=[0, CY, 17]))
    wk += [
        C('wheel_hub', [-5, CY - 5, 15.5], [5, CY + 5, 17], dgold),
        Group('needle', [0, CY, 15], [
            C('needle', [-1, CY, 14.5], [1, CY + RAD - 6, 15.5], tipped_up(PAL, 'GOLD', 'BOW', 4)),
            C('needle_tip', [-2, CY + RAD - 12, 14.5], [2, CY + RAD - 7, 15.5], sol(PAL, 'BOW')),
            C('needle_weight', [-2, CY - 8, 14.5], [2, CY, 15.5], gold)]),
        Group('needle2', [0, CY, 14], [
            C('needle2', [-1, CY, 13.5], [1, CY + RAD - 18, 14.5], sol(PAL, 'CARD', .04)),
            C('needle2_tip', [-2, CY + RAD - 22, 13.5], [2, CY + RAD - 18, 14.5], gem),
            C('needle_cap', [-3, CY - 3, 12.5], [3, CY + 3, 13.5], dgold)], [0, 0, 180])]
    wheel = Group('wheel', [0, CY, 18], wk)

    mantle = Group('mantle', [0, 86, 10], [
        C('mantle', [-22, 14, 10], [22, 86, 12], cape),
        C('mantle_hem', [-23, 8, 9.5], [23, 16, 12.5], ermine),
        C('mantle_side_r', [20, 20, 2], [23, 84, 10], cape), C('mantle_side_l', [-23, 20, 2], [-20, 84, 10], cape),
        C('mantle_collar', [-16, 84, 2], [16, 92, 12], ermine),
        C('mantle_clasp_r', [8, 82, -10.5], [12, 86, -9.5], gold), C('mantle_clasp_l', [-12, 82, -10.5], [-8, 86, -9.5], gold),
        C('mantle_chain', [-8, 83, -10.5], [8, 84, -9.5], gold)], [-6, 0, 0])
    coattail = Group('coattail', [0, 50, 8], [
        C('coattail_r', [1, 16, 7], [11, 50, 9], ragged(suit, .2, 1)),
        C('coattail_l', [-11, 16, 7], [-1, 50, 9], ragged(suit, .2, 1))])
    tail_tip = Group('tail3', [0, 45.5, 29], [
        C('tail_spade', [-3, 43.5, 29], [3, 47.5, 34], sol(PAL, 'DSKIN')),
        C('tail_spade_tip', [-1, 44.5, 34], [1, 46.5, 37], sol(PAL, 'DSKIN')),
        C('tail_ring', [-1.5, 44, 26], [1.5, 47, 28], gold)])
    tail2 = Group('tail2', [0, 45.5, 17], [C('tail2', [-1, 44.5, 17], [1, 46.5, 29], skin), tail_tip], [25, 0, 0])
    tail = Group('tail1', [0, 45.5, 8], [C('tail1', [-1.5, 44, 8], [1.5, 47, 17], skin), tail2], [-20, 0, 0])

    body = Group('body', [0, 44, 0], [
        C('pelvis', [-12, 40, -7], [12, 50, 7], suit),
        C('belt', [-12.5, 48, -7.5], [12.5, 51, 7.5], gold),
        C('belt_buckle', [-3, 46, -8.5], [3, 52, -7.5], dice(PAL, 'DGOLD', 'BOW')),
        C('waist', [-11, 50, -7], [11, 62, 7], sol(PAL, 'VEST')),
        C('vest_front', [-10, 51, -8], [10, 74, -7], faces(sol(PAL, 'VEST'), north=RO.vest_front)),
        C('chest', [-15, 62, -9], [15, 84, 9], suit),
        C('lapel_r', [4, 66, -10], [10, 82, -9], sol(PAL, 'SATIN')), C('lapel_l', [-10, 66, -10], [-4, 82, -9], sol(PAL, 'SATIN')),
        C('shirt_front', [-4, 74, -10], [4, 84, -9], shirt),
        C('bowtie', [-5, 79, -11], [5, 82, -10], sol(PAL, 'BOW')), C('bowtie_knot', [-1, 78, -12], [1, 83, -11], gem),
        C('collar', [-9, 82, 3], [9, 90, 9], sol(PAL, 'DSUIT')),
        C('sash', [-15.5, 64, -9.5], [15.5, 68, 9.5], sol(PAL, 'BOW')),
        C('medal', [9, 72, -10.5], [13, 77, -9.5], dice(PAL, 'GOLD', 'BOW')),
        C('wheel_mount', [-3, 80, 9], [3, 94, 17], dgold),
        mantle, coattail, tail, head, wheel,
        upper_arm('right'), upper_arm('left'), mid_arm('right'), mid_arm('left'), lower_arm('right'), lower_arm('left')])
    return Group('root', [0, 0, 0], [body, leg('right'), leg('left')])

# ---------------- 애니메이션 ----------------
NEEDLE_SPIN = 6.0
def needle_spin(k):
    total = 360 * 5 + 60 * k; keys = []; keys2 = []; n = 16; stop = NEEDLE_SPIN - .3
    for i in range(n + 1):
        f = i / n; t = round(stop * f, 3); e = 1 - (1 - f) ** 3
        keys.append(R(t, 0, 0, round(total * e, 2), 'linear')); keys2.append(R(t, 0, 0, round(-(360 * 4) * e, 2), 'linear'))
    keys.append(R(NEEDLE_SPIN, 0, 0, total)); keys2.append(R(NEEDLE_SPIN, 0, 0, -1440))
    return {'needle': keys, 'needle2': keys2}

def anims(G):
    L = 1.5
    walk = mirror_tracks(biped_walk(L, 22, 12, 28, .8, extra={
        'right_arm2': [R(0, 8), R(L / 2, -8), R(L, 8)], 'right_arm3': [R(0, -5), R(L / 2, 5), R(L, -5)],
        'coattail': [R(0, 6), R(L / 4, 12), R(L / 2, 6), R(3 * L / 4, 12), R(L, 6)],
        'mantle': [R(0, 8), R(L / 4, 13), R(L / 2, 8), R(3 * L / 4, 13), R(L, 8)],
        'tail1': [R(0, 0, 10), R(L / 2, 0, -10), R(L, 0, 10)],
        'head': [R(0, 0, 3), R(L / 2, 0, -3), R(L, 0, 3)],
        'wheel': [R(0, 0, 0, 2), R(L / 2, 0, 0, -2), R(L, 0, 0, 2)]}))
    walk['left_arm2'] = [R(0, -8), R(L / 2, 8), R(L, -8)]; walk['left_arm3'] = [R(0, 5), R(L / 2, -5), R(L, 5)]
    idle = mirror_tracks({
        'body': osc('position', 3, 0, -1, 0, 4, 1), 'head': [R(0, 2, 6), R(1.5, -2, -6), R(3, 2, 6)],
        'right_arm': osc('rotation', 3, -4, 3, 0, 4, 0), 'right_arm3': osc('rotation', 3, -3, 3, 1, 4, 0),
        'right_hand2': [R(0, -10), R(.5, 10), R(1, -10), R(1.5, 10), R(2, -10), R(2.5, 10), R(3, -10)],
        'right_forearm2': osc('rotation', 3, -5, 5, 1, 4, 0),
        'tail1': osc('rotation', 3, -12, 12, 0, 4, 1), 'tail2': osc('rotation', 3, -10, 10, 1, 4, 1),
        'coattail': osc('rotation', 3, 0, 4, 0, 4, 0), 'mantle': osc('rotation', 3, 0, 3, 1, 4, 0),
        'needle': [R(0, 0, 0, -8), R(1.5, 0, 0, 8), R(3, 0, 0, -8)],
        'needle2': [R(0, 0, 0, 0, 'linear'), R(3, 0, 0, -360, 'linear')]})
    spawn = mirror_tracks({   # 망토로 몸을 감싼 채 솟아올라 여섯 팔을 펼치며 인사
        'root': [T(0, 0, -150, 0), T(1.3, 0, 4, 0), T(1.6, 0, 0, 0), S(0, .8, .5, .8), S(1.3, 1, 1.06, 1), S(1.6, 1, 1, 1)],
        'body': [Z(0), R(1.6), R(2.0, 26), R(2.6, 26), R(3.2)],
        'head': [Z(0), R(2.0, 10), R(2.6, 10), R(3.2)],
        'hat': [T(0), T(2.0), T(2.3, 0, 8, -3), T(2.6, 0, 8, -3), T(2.9)],
        'right_arm': [R(0, 60, 0, -30), R(1.5, 60, 0, -30), R(2.0, 20, 0, 80), R(2.6, 20, 0, 80), R(3.2)],
        'right_arm3': [R(0, 40, 0, -20), R(1.6, 40, 0, -20), R(2.05, 10, 0, 50), R(2.6, 10, 0, 50), R(3.2)],
        'right_arm2': [R(0, 30, 0, -40), R(1.7, 30, 0, -40), R(2.1, 20, 0, 20), R(2.6, 20, 0, 20), R(3.2)],
        'mantle': [R(0, -4), R(1.3, 30), R(2.0, 10), R(3.2)],
        'needle': [Z(0), R(1.3, 0, 0, 0), R(3.2, 0, 0, 720)], 'needle2': [Z(0), R(1.3, 0, 0, 0), R(3.2, 0, 0, -1080)]})
    attack = {   # 근접: 지팡이 가로 후려치기 + 아래 팔 따라 할퀴기
        'root': [T(0), T(.4, 0, 0, 3), T(.55, 0, 0, -6), T(.9, 0, 0, -6), T(1.2)],
        'body': [Z(0), R(.4, 0, 32), R(.55, 6, -36), R(.9, 6, -30), R(1.2)],
        'right_arm3': [Z(0), R(.4, 30, 0, 70), R(.55, 60, 0, -36), R(.9, 56, 0, -30), R(1.2)],
        'cane': [Z(0), R(.4, 20, 0, 30), R(.55, 62, 0, 24), R(.9, 58, 0, 24), R(1.2)],
        'right_arm2': [Z(0), R(.45, 30, 0, 60), R(.62, 70, 0, -30), R(.95, 60, 0, -25), R(1.2)],
        'right_arm': [Z(0), R(.4, -10, 0, 30), R(1.2)], 'left_arm': [Z(0), R(.4, -20, 0, -15), R(.55, -10, 0, -25), R(1.2)],
        'head': [Z(0), R(.4, 5, 15), R(.55, -5, -15), R(1.2)],
        'mantle': [Z(0), R(.55, 16, 0, -8), R(1.2)], 'coattail': [Z(0), R(.55, 20, 0, -10), R(1.2)]}
    death = mirror_tracks({   # 봉인: 무릎 꿇고 고꾸라짐, 왕관 모자가 굴러 떨어지고 바늘이 풀려 돎
        'root': [T(0), T(.8, 0, -12, 0), T(2.2, 0, -14, -10), R(0), R(.8, 0), R(2.2, -80), R(3.4, -80)],
        'right_leg': [Z(0), R(.8, 70), R(3.4, 70)], 'right_shin': [Z(0), R(.8, -95), R(3.4, -95)],
        'head': [Z(0), R(.8, 25), R(2.2, -20), R(3.4, -20)],
        'hat': [T(0), T(.8), T(1.4, 0, 14, -16), T(2.4, 0, -40, -60), T(3.4, 0, -42, -64), R(0), R(1.4, -60), R(2.4, -200), R(3.4, -180)],
        'right_arm': [Z(0), R(.8, -20, 0, 25), R(2.2, 150, 0, 30), R(3.4, 150, 0, 30)],
        'right_arm3': [Z(0), R(.8, -10, 0, 20), R(2.2, 130, 0, 40), R(3.4, 130, 0, 40)],
        'right_arm2': [Z(0), R(.8, -30, 0, 10), R(2.2, 120, 0, 50), R(3.4, 120, 0, 50)],
        'mantle': [Z(0), R(2.2, -30), R(3.4, -34)],
        'needle': [Z(0), R(1.0, 0, 0, 300), R(2.8, 0, 0, 560), R(3.4, 0, 0, 575)],
        'needle2': [Z(0), R(1.0, 0, 0, -200), R(2.8, 0, 0, -380), R(3.4, 0, 0, -390)],
        'wheel': [Z(0), R(2.2, 0, 0, 8), R(2.5, 0, 0, 14), R(3.4, 0, 0, 14)]})
    death['left_leg'] = [Z(0), R(.8, 30), R(3.4, 30)]; death['left_shin'] = [Z(0), R(.8, -40), R(3.4, -40)]
    spin_cast = mirror_tracks({   # 룰렛 돌리기 (루프 2.0)
        'body': [R(0, -6), R(1.0, -10), R(2.0, -6)], 'head': [R(0, -16, 10), R(1.0, -20, -10), R(2.0, -16, 10)],
        'right_arm': [R(0, 160, 0, 10), R(.5, 175, 0, -10), R(1.0, 150, 0, 25), R(1.5, 175, 0, -10), R(2.0, 160, 0, 10)],
        'right_forearm': [R(0, -20), R(1.0, -35), R(2.0, -20)],
        'right_arm3': [R(0, 20, 0, 50), R(1.0, 26, 0, 58), R(2.0, 20, 0, 50)],
        'right_arm2': [R(0, 20, 0, 40), R(1.0, 25, 0, 50), R(2.0, 20, 0, 40)],
        'right_hand2': [R(0, -40), R(1.0, -55), R(2.0, -40)], 'tail1': osc('rotation', 2, -15, 15, 0, 4, 1)})
    spin_cast['left_arm'] = [R(0, 150, 0, -25), R(.5, 175, 0, 10), R(1.0, 160, 0, -10), R(1.5, 175, 0, 10), R(2.0, 150, 0, -25)]
    call_bets = mirror_tracks({   # 베팅 마감: 여섯 팔 활짝
        'body': [Z(0), R(.4, -12), R(1.2, -12), R(1.6)], 'head': [Z(0), R(.4, -20), R(.8, -26), R(1.2, -20), R(1.6)],
        'right_arm': [Z(0), R(.4, 20, 0, 85), R(1.2, 20, 0, 85), R(1.6)], 'right_forearm': [Z(0), R(.4, 40), R(1.2, 40), R(1.6)],
        'right_arm3': [Z(0), R(.4, 20, 0, 60), R(1.2, 20, 0, 60), R(1.6)],
        'right_arm2': [Z(0), R(.4, 30, 0, 36), R(1.2, 30, 0, 36), R(1.6)], 'right_hand2': [Z(0), R(.4, -60), R(1.2, -60), R(1.6)],
        'mantle': [Z(0), R(.4, 18), R(1.2, 16), R(1.6)]})
    card_throw = mirror_tracks({   # 카드 날리기
        'root': [T(0), T(.35, 0, 0, 3), T(.5, 0, 0, -2), T(1.0)], 'body': [Z(0), R(.35, 10), R(.5, -8), R(1.0)],
        'right_arm': [Z(0), R(.35, 70, 0, -40), R(.5, 80, 0, 60), R(.75, 75, 0, 55), R(1.0)],
        'right_forearm': [Z(0), R(.35, 50), R(.5, 0), R(1.0)], 'right_hand': [Z(0), R(.35, 30), R(.5, -40), R(1.0)],
        'right_arm2': [Z(0), R(.4, 60, 0, -30), R(.55, 70, 0, 50), R(.8, 65, 0, 45), R(1.0)],
        'right_forearm2': [Z(0), R(.4, 30), R(.55, -10), R(1.0)]})
    cane_slam = {   # 지팡이 내리찍기: 바닥을 쿵 (칩 탑 낙하·충격파 계열)
        'root': [T(0), T(.5, 0, 3), T(.7, 0, -3), T(1.1, 0, -3), T(1.5)],
        'body': [Z(0), R(.5, -12), R(.7, 16), R(1.1, 14), R(1.5)], 'head': [Z(0), R(.5, -20), R(.7, 12), R(1.5)],
        'right_arm3': [Z(0), R(.5, 110, 0, 10), R(.7, 36, 0, 0), R(1.1, 34, 0, 0), R(1.5)],
        'right_forearm3': [Z(0), R(.5, -40), R(.7, 0), R(1.5)],
        'cane': [Z(0), R(.5, 30, 0, 20), R(.7, 26, 0, 24), R(1.1, 26, 0, 24), R(1.5)],
        'left_arm': [Z(0), R(.5, 20, 0, -40), R(1.1, 20, 0, -40), R(1.5)],
        'mantle': [Z(0), R(.5, -6), R(.7, 20), R(1.5)], 'hat': [T(0), T(.7, 0, 2), T(.85, 0, 0), T(1.5)]}
    dice_roll = {   # 주사위 굴리기: 왼 위팔 언더핸드
        'root': [T(0), T(.4, 0, -3, 4), T(.6, 0, -2, -4), T(1.2)],
        'body': [Z(0), R(.4, 14, -15), R(.6, 22, 15), R(.9, 18, 10), R(1.2)],
        'left_arm': [Z(0), R(.4, -60, 0, -10), R(.6, 70, 0, -5), R(.9, 60, 0, -5), R(1.2)],
        'left_forearm': [Z(0), R(.4, 10), R(.6, -10), R(1.2)],
        'right_leg': [Z(0), R(.4, 20), R(.6, 30), R(1.2)], 'left_leg': [Z(0), R(.4, -15), R(1.2)], 'head': [Z(0), R(.6, 15), R(1.2)]}
    all_in = mirror_tracks({   # 올인: 여섯 팔을 모아 들었다가 앞 반원 내려찍기
        'root': [T(0), T(.6, 0, 6, 4), T(.85, 0, -6, -10), T(1.2, 0, -6, -10), T(1.6)],
        'body': [Z(0), R(.6, -20), R(.85, 35), R(1.2, 32), R(1.6)], 'head': [Z(0), R(.6, -15), R(.85, -10), R(1.6)],
        'right_arm': [Z(0), R(.6, 170, 0, 30), R(.85, 60, 0, 20), R(1.2, 55, 0, 18), R(1.6)],
        'right_forearm': [Z(0), R(.6, -10), R(.85, 20), R(1.6)],
        'right_arm3': [Z(0), R(.6, 150, 0, 40), R(.85, 50, 0, 30), R(1.2, 46, 0, 28), R(1.6)],
        'right_arm2': [Z(0), R(.6, 140, 0, 30), R(.85, 50, 0, 20), R(1.2, 45, 0, 18), R(1.6)],
        'right_leg': [Z(0), R(.6, -10), R(.85, 30), R(1.2, 30), R(1.6)], 'right_shin': [Z(0), R(.85, -30), R(1.2, -30), R(1.6)],
        'mantle': [Z(0), R(.6, -10), R(.85, 34), R(1.6)], 'coattail': [Z(0), R(.6, -10), R(.85, 30), R(1.6)]})
    all_in['left_leg'] = [Z(0), R(.85, -25), R(1.2, -25), R(1.6)]; all_in['left_shin'] = [Z(0), R(1.6)]
    groggy = mirror_tracks({
        'root': [T(0, 0, -11, 0), T(1.5, 0, -12, 0), T(3.0, 0, -11, 0)],
        'right_leg': [R(0, 75), R(3.0, 75)], 'right_shin': [R(0, -95), R(3.0, -95)],
        'body': [R(0, 22, 0, 4), R(1.5, 26, 0, 6), R(3.0, 22, 0, 4)], 'head': [R(0, 25, 0, 12), R(1.5, 30, 0, 14), R(3.0, 25, 0, 12)],
        'hat': [T(0, 3, -1, -2), T(3.0, 3, -1, -2), R(0, 0, 0, -22), R(3.0, 0, 0, -22)],
        'right_arm': [R(0, -10, 0, -8), R(1.5, -6, 0, -6), R(3.0, -10, 0, -8)], 'right_forearm': [R(0, -20), R(3.0, -20)],
        'right_arm3': [R(0, -6, 0, -14), R(3.0, -6, 0, -14)], 'right_arm2': [R(0, -10, 0, -30), R(3.0, -10, 0, -30)],
        'needle': [R(0, 0, 0, 170), R(1.5, 0, 0, 185), R(3.0, 0, 0, 170)], 'needle2': [R(0, 0, 0, 10), R(1.5, 0, 0, -6), R(3.0, 0, 0, 10)],
        'tail1': [R(0, 30), R(3.0, 30)]})
    groggy['left_leg'] = [R(0, -30), R(3.0, -30)]; groggy['left_shin'] = [R(0, -10), R(3.0, -10)]
    enrage = mirror_tracks({
        'root': [T(0), T(.5, 0, -4), T(.9, 0, 4), T(2.0, 0, 4), T(2.4)],
        'body': [Z(0), R(.5, 15), R(.9, -18), R(1.1, -14), R(1.3, -18), R(1.5, -14), R(1.7, -18), R(2.0, -16), R(2.4)],
        'head': [Z(0), R(.5, 20), R(.9, -45), R(1.2, -38), R(1.5, -45), R(1.8, -38), R(2.0, -42), R(2.4)],
        'right_arm': [Z(0), R(.5, 20, 0, -20), R(.9, 175, 0, 25), R(2.0, 170, 0, 30), R(2.4)],
        'right_arm3': [Z(0), R(.5, 20, 0, -10), R(.9, 100, 0, 60), R(2.0, 96, 0, 60), R(2.4)],
        'right_arm2': [Z(0), R(.5, 20, 0, 10), R(.9, 60, 0, 44), R(2.0, 56, 0, 44), R(2.4)],
        'mantle': [Z(0), R(.9, 26), R(2.0, 22), R(2.4)],
        'hat': [T(0), T(.9, 0, 5, 2), T(1.1, 0, 2, 2), T(1.3, 0, 5, 2), T(1.5, 0, 2, 2), T(2.4)],
        'needle': [Z(0), R(2.4, 0, 0, 1440, 'linear')], 'needle2': [Z(0), R(2.4, 0, 0, -2160, 'linear')],
        'tail1': [Z(0), R(.9, -40), R(2.0, -40), R(2.4)]})
    needle_fast = {'needle': [R(0, 0, 0, 0, 'linear'), R(.5, 0, 0, 360, 'linear')], 'needle2': [R(0, 0, 0, 0, 'linear'), R(.5, 0, 0, -360, 'linear')]}
    # ---- 공성전 짤패턴 모션 (VFX 팩 Siege_Roulette 와 짝) ----
    card_boomerang = {   # 1 카드 부메랑: 0.4초에 던지고 1.25초에 되받는다
        'body': [Z(0), R(.25, -8, 20), R(.4, 10, -22, 0, 'linear'), R(.9, 6, -14), R(1.25, -4, 10), Z(1.6)], 'head': [Z(0), R(.4, 6, -10), R(1.25, -4, 8), Z(1.6)],
        'right_arm': [Z(0), R(.25, 70, 0, -40), R(.4, 84, 0, 60, 'linear'), R(.9, 78, 0, 50), R(1.25, 60, 0, -16), Z(1.6)],
        'right_forearm': [Z(0), R(.25, 50), R(.4, 0, 0, 0, 'linear'), R(1.25, 40), Z(1.6)], 'right_hand': [Z(0), R(.25, 30), R(.4, -40, 0, 0, 'linear'), R(1.25, 20), Z(1.6)],
        'left_arm3': [Z(0), R(.4, 20, 0, -30), R(1.25, 10, 0, -20), Z(1.6)], 'coattail': [Z(0), R(.4, 14), Z(1.6)]}
    chip_domino = {   # 3 칩 도미노: 왼쪽 가운데 손의 칩 탑을 0.5초에 밀어 쓰러뜨린다
        'body': [Z(0), R(.3, -6, 14), R(.5, 14, -16, 0, 'linear'), R(.9, 12, -12), Z(1.4)], 'head': [Z(0), R(.5, 10), Z(1.4)],
        'left_arm3': [Z(0), R(.3, -24, 0, -10), R(.5, 74, 0, -8, 'linear'), R(.9, 70, 0, -8), Z(1.4)], 'left_forearm3': [Z(0), R(.3, 20), R(.5, -34, 0, 0, 'linear'), Z(1.4)],
        'right_arm2': [Z(0), R(.5, 30, 0, 20), Z(1.4)], 'root': [T(0), T(.5, 0, -2, -3, 'linear'), T(1.4)]}
    coin_flip = {   # 4 동전 던지기: 0.2초에 튕겨 올리고, 1.35초에 받아 결과를 본다
        'body': [Z(0), R(.2, 4), R(.4, -10), R(1.2, -10), R(1.35, 10), R(1.75, 10), Z(2.0)], 'head': [Z(0), R(.2, 10), R(.4, -34), R(1.15, -30), R(1.35, 16), R(1.75, 16), Z(2.0)],
        'right_arm2': [Z(0), R(.14, 60, 0, 10), R(.22, 112, 0, 10, 'linear'), R(1.1, 100, 0, 10), R(1.35, 70, 0, -20), R(1.75, 70, 0, -20), Z(2.0)],
        'right_forearm2': [Z(0), R(.14, 40), R(.22, -10, 0, 0, 'linear'), R(1.35, 30), Z(2.0)], 'right_hand2': [Z(0), R(.14, 30), R(.22, -40, 0, 0, 'linear'), Z(2.0)],
        'left_arm2': [Z(0), R(1.2, 40, 0, 10), R(1.35, 78, 0, 26, 'linear'), R(1.5, 60, 0, 40), R(1.75, 60, 0, 40), Z(2.0)],
        'left_arm': [Z(0), R(.4, 20, 0, -40), R(1.2, 20, 0, -40), Z(1.6)]}
    slot_pull = {   # 5 슬롯머신: 오른 위팔로 레버를 당기고(0.3초), 1.0초 동안 릴이 돈다
        'body': [Z(0), R(.15, -6), R(.32, 12, 0, 0, 'linear'), R(1.0, 6), R(1.15, -6), Z(1.6)], 'head': [Z(0), R(.32, 8), R(1.0, 0), R(1.15, -14), Z(1.6)],
        'right_arm': [Z(0), R(.15, 172, 0, 12), R(.32, 40, 0, 6, 'linear'), R(1.0, 30, 0, 6), Z(1.6)], 'right_forearm': [Z(0), R(.15, -10), R(.32, 30, 0, 0, 'linear'), Z(1.6)],
        'left_arm': [Z(0), R(1.0, 0), R(1.15, 30, 0, -70), R(1.4, 26, 0, -66), Z(1.6)], 'right_arm2': [Z(0), R(1.0, 0), R(1.15, 30, 0, 50), R(1.4, 26, 0, 46), Z(1.6)],
        'left_arm2': [Z(0), R(1.0, 0), R(1.15, 30, 0, -50), R(1.4, 26, 0, -46), Z(1.6)],
        'needle': [R(0, 0, 0, 0, 'linear'), R(.3, 0, 0, 0, 'linear'), R(1.0, 0, 0, 1080, 'linear'), R(1.6, 0, 0, 1080)], 'needle2': [R(0, 0, 0, 0, 'linear'), R(.3, 0, 0, 0, 'linear'), R(1.0, 0, 0, -1440, 'linear'), R(1.6, 0, 0, -1440)]}
    six_hands = {'body': [Z(0)] + [R(round(.55 + i * .32, 2), 10 + (i % 2) * 4, (-14 if i % 2 else 14)) for i in range(6)] + [Z(2.7)],
                 'head': [Z(0), R(.5, 8), R(2.2, 8), Z(2.7)], 'root': [T(0), T(.5, 0, -2, -2), T(2.2, 0, -2, -2), T(2.7)]}   # 7 딜러의 여섯 손: 0.55초부터 0.32초 간격으로 여섯 팔이 번갈아 내려친다
    for i, (arm, sg) in enumerate((('right_arm', 1), ('left_arm', -1), ('right_arm3', 1), ('left_arm3', -1), ('right_arm2', 1), ('left_arm2', -1))):
        t = .55 + i * .32
        six_hands[arm] = [Z(0), R(round(t - .22, 2), 150, 0, 10 * sg), R(round(t, 2), 42, 0, 0, 'linear'), R(round(t + .2, 2), 40), Z(2.7)]
    jackpot = mirror_tracks({   # 9 잭팟: 고개를 젖혀 웃으며 여섯 팔을 흔든다 (동전 비 동안)
        'body': [Z(0), R(.4, -16), R(.7, -12), R(1.0, -16), R(1.3, -12), R(1.6, -16), R(2.0, -14), R(2.4)],
        'head': [Z(0), R(.4, -40), R(.7, -34), R(1.0, -40), R(1.3, -34), R(1.6, -40), R(2.0, -36), R(2.4)],
        'right_arm': [Z(0), R(.4, 170, 0, 30), R(.8, 176, 0, 16), R(1.2, 170, 0, 30), R(1.6, 176, 0, 16), R(2.0, 170, 0, 30), R(2.4)],
        'right_arm3': [Z(0), R(.4, 100, 0, 60), R(1.0, 110, 0, 50), R(1.6, 100, 0, 60), R(2.0, 104, 0, 56), R(2.4)],
        'right_arm2': [Z(0), R(.4, 60, 0, 44), R(1.0, 66, 0, 38), R(1.6, 60, 0, 44), R(2.0, 62, 0, 42), R(2.4)],
        'hat': [T(0), T(.4, 0, 5, 2), T(.7, 0, 2, 2), T(1.0, 0, 5, 2), T(1.3, 0, 2, 2), T(2.4)],
        'needle': [Z(0), R(2.4, 0, 0, 1440, 'linear')], 'needle2': [Z(0), R(2.4, 0, 0, -2160, 'linear')]})
    gate_strike = mirror_tracks({   # 성문 강타: 1.2초 동안 여섯 팔을 모아 들고 1.5초에 내려찍음
        'root': [T(0), T(1.2, 0, 6, 4), T(1.5, 0, -6, -10, 'linear'), T(1.9, 0, -6, -10), T(2.3)],
        'body': [Z(0), R(1.2, -20), R(1.5, 35, 0, 0, 'linear'), R(1.9, 32), R(2.3)], 'head': [Z(0), R(1.2, -15), R(1.5, 12, 0, 0, 'linear'), R(2.3)],
        'right_arm': [Z(0), R(1.2, 170, 0, 30), R(1.5, 60, 0, 20, 'linear'), R(1.9, 55, 0, 18), R(2.3)], 'right_forearm': [Z(0), R(1.2, -10), R(1.5, 20, 0, 0, 'linear'), R(2.3)],
        'right_arm3': [Z(0), R(1.2, 150, 0, 40), R(1.5, 50, 0, 30, 'linear'), R(1.9, 46, 0, 28), R(2.3)],
        'right_arm2': [Z(0), R(1.2, 140, 0, 30), R(1.5, 50, 0, 20, 'linear'), R(1.9, 45, 0, 18), R(2.3)],
        'right_leg': [Z(0), R(1.2, -10), R(1.5, 30, 0, 0, 'linear'), R(1.9, 30), R(2.3)], 'right_shin': [Z(0), R(1.5, -30, 0, 0, 'linear'), R(1.9, -30), R(2.3)],
        'mantle': [Z(0), R(1.2, -10), R(1.5, 34, 0, 0, 'linear'), R(2.3)], 'coattail': [Z(0), R(1.2, -10), R(1.5, 30, 0, 0, 'linear'), R(2.3)]})
    gate_strike['left_leg'] = [Z(0), R(1.5, -25, 0, 0, 'linear'), R(1.9, -25), R(2.3)]; gate_strike['left_shin'] = [Z(0), R(2.3)]
    spec = [('spawn', 3.2, 'once', spawn), ('idle', 3.0, 'loop', idle), ('walk', L, 'loop', walk),
            ('attack', 1.2, 'once', attack), ('death', 3.4, 'hold', death),
            ('spin_cast', 2.0, 'loop', spin_cast), ('call_bets', 1.6, 'once', call_bets),
            ('card_throw', 1.0, 'once', card_throw), ('cane_slam', 1.5, 'once', cane_slam),
            ('dice_roll', 1.2, 'once', dice_roll), ('all_in', 1.6, 'once', all_in),
            ('groggy', 3.0, 'loop', groggy), ('enrage', 2.4, 'once', enrage), ('needle_fast', .5, 'loop', needle_fast)]
    for k in range(6): spec.append((f'needle_stop_{k}', NEEDLE_SPIN, 'hold', needle_spin(k)))
    spec += [('card_boomerang', 1.6, 'once', card_boomerang), ('chip_domino', 1.4, 'once', chip_domino), ('coin_flip', 2.0, 'once', coin_flip), ('slot_pull', 1.6, 'once', slot_pull),
             ('six_hands', 2.7, 'once', six_hands), ('jackpot', 2.4, 'once', jackpot), ('gate_strike', 2.3, 'once', gate_strike)]
    for _n, _l, _p, _tr in spec:   # 망토는 뒤로 날려야 하므로 x 회전 부호를 뒤집음 (+x = 앞으로)
        if 'mantle' in _tr: _tr['mantle'] = [(k[0], k[1], -k[2], *k[3:]) if k[0] == 'rotation' else k for k in _tr['mantle']]
    for _n, _l, _p, _tr in spec:   # 몸통·머리는 +x가 뒤로 젖힘이라, 숙임/젖힘 의도대로 x 부호를 뒤집음
        if _n in ('death',): continue
        for _b in ('body', 'head'):
            if _b in _tr: _tr[_b] = [(k[0], k[1], -k[2], *k[3:]) if k[0] == 'rotation' else k for k in _tr[_b]]
    # ---- 지팡이: 손에 고정, 손목이 지팡이 '끝(바닥 쪽)' 방향을 맞춘다. (t, phi, pitch[, 법선]) ----
    U = 'rest'
    t6 = .55 + 2 * .32
    WT = {'attack': [(0, U), (.4, 112, 6), (.55, -58, -5), (.9, -68, -5), (1.2, U)],
          'cane_slam': [(0, U), (.5, 0, -82), (.7, 0, -88), (1.1, 0, -88), (1.5, U)],
          'six_hands': [(0, U), (round(t6 - .22, 2), 0, 122), (round(t6, 2), 0, -28), (round(t6 + .2, 2), 0, -32), (2.7, U)],
          'jackpot': [(0, U), (.4, 50, 72), (2.0, 50, 72), (2.4, U)],
          'gate_strike': [(0, U), (1.2, 0, 130), (1.5, 0, -34), (1.9, 0, -38), (2.3, U)]}
    WRIST_DEV.update(weapon_apply(G, spec, ['body', 'right_arm3', 'right_forearm3'], 'right_hand3', 'cane', WT, axis=-1))
    return build_anims(f'animation.{ID}.', spec, G)
WRIST_DEV = {}

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    export(ID, build(), anims, 41, O + ID + '.bbmodel', HITBOX)
    apply_palette(SHINY)
    export(ID + '_shiny', build(), anims, 41, O + ID + '_shiny.bbmodel', HITBOX)
