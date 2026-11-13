"""도박의 악마 룰레타 (roulette) — 팔 넷 달린 딜러 악마, 등 뒤에 6구역 룰렛 휠(바늘 본 회전)."""
from common import *
import math, os

ID = 'roulette'
# 휠 구역 색은 바닥 6구역과 짝이라 이로치에서도 바꾸지 않음 (k=0 맨 위부터 정면에서 볼 때 시계방향)
SECTOR_KEYS = ['SEC0', 'SEC1', 'SEC2', 'SEC3', 'SEC4', 'SEC5']
PAL = dict(
    SKIN=(150, 34, 42), DSKIN=(92, 18, 26), SUIT=(30, 26, 36), DSUIT=(14, 12, 18), SATIN=(52, 44, 62),
    VEST=(96, 24, 62), SHIRT=(226, 216, 196), GOLD=(228, 182, 72), DGOLD=(146, 104, 34),
    HORN=(232, 222, 196), DHORN=(96, 74, 58), EYE=(255, 214, 70, 255), PUPIL=(20, 6, 8), MOUTH=(34, 6, 12),
    TEETH=(238, 232, 212), BOW=(186, 30, 44), WOOD=(64, 34, 26), DWOOD=(38, 20, 16), CARD=(238, 232, 220),
    CARDINK=(180, 26, 36),
    SEC0=(198, 40, 46), SEC1=(30, 28, 36), SEC2=(34, 142, 78), SEC3=(44, 94, 196), SEC4=(132, 54, 176), SEC5=(232, 184, 40),
)
SHINY = dict(
    SKIN=(42, 122, 140), DSKIN=(20, 70, 86), SUIT=(232, 226, 212), DSUIT=(180, 170, 152), SATIN=(206, 196, 178),
    VEST=(30, 34, 58), SHIRT=(40, 40, 52), GOLD=(206, 214, 228), DGOLD=(120, 128, 146),
    HORN=(40, 34, 48), DHORN=(170, 70, 210), EYE=(255, 90, 210, 255), PUPIL=(30, 0, 30), MOUTH=(10, 30, 40),
    TEETH=(250, 250, 255), BOW=(40, 160, 190), WOOD=(220, 214, 200), DWOOD=(150, 140, 126), CARD=(30, 30, 40),
    CARDINK=(80, 220, 240),
)
HITBOX = (2.4, 8.0, 6.1)
CY = 102         # 휠 중심 높이
RAD = 34         # 휠 반지름

def apply_palette(p): PAL.update(p)

# ---------------- 페인터 ----------------
def suit_p():   # 검은 정장 + 은은한 핀스트라이프
    return stripes(PAL, 'SUIT', 'SATIN', period=4, width=1)

def face_paint(img, r, rng):
    x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
    # 볼 그늘
    for ly in range(9, h):
        for lx in (0, 1, w - 2, w - 1): px(img, r, lx, ly, col(PAL['DSKIN']))
    # 눈썹 (안쪽으로 찡그림)
    for i in range(5):
        px(img, r, 2 + i, 4 + (i // 2 if i < 4 else 2), col(PAL['DSKIN'], .7))
        px(img, r, w - 3 - i, 4 + (i // 2 if i < 4 else 2), col(PAL['DSKIN'], .7))
    # 눈: 노란 홍채 + 세로 동공
    for ex in (3, w - 7):
        for dx in range(4):
            for dy in range(2):
                px(img, r, ex + dx, 7 + dy, col(PAL['EYE']))
        px(img, r, ex + 2, 7, col(PAL['PUPIL'])); px(img, r, ex + 2, 8, col(PAL['PUPIL']))
    # 귀까지 찢어진 웃음
    for lx in range(1, w - 1):
        cy = 12 - (1 if lx < 3 or lx > w - 4 else 0) - (1 if lx < 2 or lx > w - 3 else 0)
        px(img, r, lx, cy, col(PAL['MOUTH'])); px(img, r, lx, cy + 1, col(PAL['MOUTH']))
        if 2 < lx < w - 3:
            px(img, r, lx, cy + 2, col(PAL['MOUTH']))
            px(img, r, lx, cy, col(PAL['TEETH']) if lx % 2 == 0 else col(PAL['MOUTH']))
            px(img, r, lx, cy + 2, col(PAL['TEETH']) if lx % 2 == 1 else col(PAL['MOUTH']))

def vest_front(img, r, rng):
    x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
    # V자 셔츠 + 금단추 + 시곗줄
    for ly in range(h):
        half = max(0, 3 - ly // 3)
        for lx in range(w // 2 - half, w // 2 + half): px(img, r, lx, ly, col(PAL['SHIRT']))
    for ly in range(9, h - 1, 4):
        px(img, r, w // 2, ly, col(PAL['GOLD'], 1.1)); px(img, r, w // 2 - 1, ly, col(PAL['DGOLD']))
    for i in range(7):
        px(img, r, 3 + i, 15 + int(1.6 * math.sin(i / 2.2)), col(PAL['GOLD']))

def card_face(img, r, rng):
    x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
    for lx in range(w):
        px(img, r, lx, 0, col(PAL['CARDINK'])); px(img, r, lx, h - 1, col(PAL['CARDINK']))
    for ly in range(h):
        px(img, r, 0, ly, col(PAL['CARDINK'])); px(img, r, w - 1, ly, col(PAL['CARDINK']))
    cx, cy = w // 2, h // 2
    for dx, dy in [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]: px(img, r, cx + dx, cy + dy, col(PAL['CARDINK']))

def wheel_paint(cube_box):
    (X0, Y0, Z0), (X1, Y1, Z1) = cube_box
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                if face == 'north':
                    x = X1 - lx - .5; y = Y1 - ly - .5
                elif face == 'south':
                    x = X0 + lx + .5; y = Y1 - ly - .5
                else:
                    img.putpixel((x1 + lx, y1 + ly), col(PAL['DGOLD'], SHADE[face] * (1 + rng.uniform(-.08, .08))))
                    continue
                dy = y - CY; rr = math.hypot(x, dy)
                if rr > RAD + .2:
                    img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                f = SHADE[face] * (1 + rng.uniform(-.05, .05))
                if face == 'south':   # 뒷면: 검은 나무 + 금 고리
                    c = PAL['GOLD'] if abs(rr - (RAD - 3)) < .7 or rr < 4 else (PAL['DWOOD'] if int(rr) % 5 == 0 else PAL['WOOD'])
                    img.putpixel((x1 + lx, y1 + ly), col(c, f)); continue
                th = math.degrees(math.atan2(-x, dy)) % 360       # 정면에서 볼 때 위=0, 시계방향
                k = int(((th + 30) % 360) // 60)
                edge = abs(((th + 30) % 60) - 0)
                edge = min(edge, 60 - edge) * math.pi / 180 * rr        # 경계선까지 거리(px)
                if rr > RAD - 2.5: c = PAL['GOLD'] if int(th // 15) % 2 == 0 else PAL['DGOLD']
                elif rr > RAD - 3.5: c = PAL['DWOOD']
                elif rr < 5: c = PAL['DGOLD']
                elif rr < 6.2: c = PAL['GOLD']
                elif edge < 0.7: c = PAL['GOLD']
                else:
                    c = PAL[SECTOR_KEYS[k]]
                    if abs(rr - (RAD - 8)) < .6: c = lerp(c, PAL['GOLD'], .55)   # 안쪽 숫자 고리
                img.putpixel((x1 + lx, y1 + ly), col(c, f))
    return p

# ---------------- 모델 ----------------
def build():
    suit = suit_p(); skin = sol(PAL, 'SKIN'); dskin = sol(PAL, 'DSKIN'); gold = sol(PAL, 'GOLD', .1)
    dgold = sol(PAL, 'DGOLD'); shirt = sol(PAL, 'SHIRT', .04); claw = tipped(PAL, 'DSKIN', 'HORN', 2)
    horn = tipped_up(PAL, 'HORN', 'DHORN', 2)

    def leg(s):
        m, P, Rt = side(s)
        shin = Group(f'{s}_shin', P([7, 26, 0]), [
            C(f'{s}_shin', [3.5, 7, -3.5], [10.5, 26, 3.5], suit, m),
            C(f'{s}_spat', [3, 4, -4], [11, 9, 4], trim(PAL, 'SHIRT', 'DSUIT'), m),
            C(f'{s}_shoe', [3, 0, -8], [11, 4, 4], sol(PAL, 'DSUIT', .05), m),
            C(f'{s}_shoe_tip', [4, 0, -11], [10, 3, -8], sol(PAL, 'DSUIT', .05), m),
            C(f'{s}_buckle', [5, 2, -9], [9, 4, -8], gold, m),
        ])
        return Group(f'{s}_leg', P([7, 44, 0]), [
            C(f'{s}_thigh', [3, 26, -4], [11, 46, 4], suit, m), shin])

    def upper_arm(s):
        m, P, Rt = side(s)
        hold = []
        if s == 'right':   # 카드 부채
            for i, a in enumerate((-22, 0, 22)):
                hold.append(Group(f'right_card{i+1}', [17.5, 27, -6], [
                    C(f'right_card{i+1}', [15, 26, -7], [20, 34, -6], faces(sol(PAL, 'CARD', .03), north=card_face, south=card_face))],
                    [0, 0, a]))
        else:              # 주사위
            hold.append(C('left_die', [-21, 26, -9], [-14, 33, -2], dice(PAL, 'CARD', 'CARDINK')))
        fingers = [C(f'{s}_finger{i+1}', [x, 27, -4], [x + 2, 34, -2], claw, m) for i, x in enumerate((14.5, 16.5, 18.5))]
        hand = Group(f'{s}_hand', P([17.5, 42, 0]), [
            C(f'{s}_palm', [14.5, 34, -4], [20.5, 42, 3], skin, m), *fingers,
            C(f'{s}_thumb', [13, 33, -4], [15, 39, -2], claw, m), *hold])
        fore = Group(f'{s}_forearm', P([17.5, 63, 0]), [
            C(f'{s}_forearm', [14.5, 46, -3.5], [20.5, 64, 3.5], suit, m),
            C(f'{s}_cuff', [14, 42, -4], [21, 46, 4], faces(shirt, east=lambda i, r, g: px(i, r, 3, 2, col(PAL['GOLD'])),
                                                             west=lambda i, r, g: px(i, r, 3, 2, col(PAL['GOLD']))), m),
            hand], [25, 0, 0])
        return Group(f'{s}_arm', P([17.5, 82, 0]), [
            C(f'{s}_upper_arm', [14, 62, -4], [21, 84, 4], suit, m),
            C(f'{s}_shoulder', [13, 78, -5], [22, 86, 5], sol(PAL, 'SUIT'), m), fore], Rt([-8, 0, 12]))

    def lower_arm(s):
        m, P, Rt = side(s)
        fingers = [C(f'{s}_finger2_{i+1}', [x, 18, -3], [x + 1, 24, -1], claw, m) for i, x in enumerate((12, 14, 16))]
        hand = Group(f'{s}_hand2', P([14.5, 30, 0]), [
            C(f'{s}_palm2', [12, 24, -3], [17, 30, 2], skin, m), *fingers])
        fore = Group(f'{s}_forearm2', P([14.5, 49, 2]), [
            C(f'{s}_forearm2', [12, 30, -0.5], [17, 50, 4.5], skin, m),
            C(f'{s}_bracelet', [11.5, 38, -1], [17.5, 42, 5], gold, m), hand], [40, 0, 0])
        return Group(f'{s}_arm2', P([13, 66, 3]), [
            C(f'{s}_upper_arm2', [11, 48, 0], [16, 68, 6], skin, m),
            C(f'{s}_elbow_spike', [14, 48, 5], [16, 52, 9], horn, m), fore], Rt([12, 0, 40]))

    def ear(s):
        m, P, Rt = side(s)
        return Group(f'{s}_ear', P([9, 97, 0]), [
            C(f'{s}_ear', [9, 94, -2], [13, 100, 1], skin, m),
            C(f'{s}_ear_tip', [12, 98, -1], [15, 103, 0], dskin, m)], Rt([0, 0, -15]))

    def horn_(s):
        m, P, Rt = side(s)
        tip = Group(f'{s}_horn_tip', P([14, 117, 0]), [
            C(f'{s}_horn2', [12, 116, -2], [16, 125, 2], sol(PAL, 'HORN'), m),
            C(f'{s}_horn3', [13, 125, -1.5], [16, 131, 1.5], horn, m)], Rt([0, 0, 24]))
        return Group(f'{s}_horn', P([9, 114, 0]), [
            C(f'{s}_horn1', [8, 112, -2.5], [14, 117, 2.5], sol(PAL, 'HORN'), m), tip], Rt([0, 0, -32]))

    hat = Group('hat', [0, 106, 0], [
        C('hat_brim', [-14, 105, -14], [14, 107, 13], sol(PAL, 'DSUIT', .05)),
        C('hat_crown', [-9, 107, -9], [9, 125, 8], sol(PAL, 'SUIT', .05)),
        C('hat_top', [-10, 124, -10], [10, 126, 9], sol(PAL, 'DSUIT', .05)),
        C('hat_band', [-10, 107, -10], [10, 111, 9], stripes(PAL, 'BOW', 'GOLD', period=6, width=1)),
        C('hat_card', [4, 109, -11], [8, 116, -10], faces(sol(PAL, 'CARD', .03), north=card_face)),
        horn_('right'), horn_('left')])

    head = Group('head', [0, 88, 0], [
        C('neck', [-4, 82, -4], [4, 90, 4], skin),
        C('head', [-9, 88, -9], [9, 106, 8], faces(sol(PAL, 'SKIN', .06), north=face_paint)),
        C('brow', [-9, 100, -10], [9, 102, -9], dskin),
        C('nose', [-1, 95, -11], [1, 99, -9], skin),
        C('goatee', [-1, 82, -9], [1, 89, -7], tipped(PAL, 'DSKIN', 'SUIT', 3)),
        ear('right'), ear('left'), hat])

    wheel_children = []
    for y0 in range(-RAD, RAD, 4):
        y1 = y0 + 4
        yi = 0 if y0 < 0 < y1 else min(abs(y0), abs(y1))
        hw = int(math.ceil(math.sqrt(max(0, RAD * RAD - yi * yi))))
        frm, to = [-hw, CY + y0, 15], [hw, CY + y1, 17]
        wheel_children.append(C(f'wheel_row{(y0 + RAD) // 4 + 1}', frm, to, wheel_paint((frm, to))))
    for i in range(12):
        a = math.radians(i * 30 + 15); rr = RAD + 1.5
        cx, cy = round(rr * math.sin(a) * 2) / 2, round((CY + rr * math.cos(a)) * 2) / 2
        wheel_children.append(C(f'wheel_peg{i+1}', [cx - 1.5, cy - 1.5, 14], [cx + 1.5, cy + 1.5, 17], gold))
    wheel_children += [
        C('wheel_crest', [-3, CY + RAD, 14], [3, CY + RAD + 5, 17], faces(gold, north=lambda i, r, g: [px(i, r, x, 2, col(PAL['BOW'])) for x in (1, 4)])),
        C('wheel_crest_horn_r', [3, CY + RAD + 3, 15], [5, CY + RAD + 7, 16], horn),
        C('wheel_crest_horn_l', [-5, CY + RAD + 3, 15], [-3, CY + RAD + 7, 16], horn),
        C('wheel_hub', [-4, CY - 4, 14], [4, CY + 4, 15], dgold),
        Group('needle', [0, CY, 13.5], [
            C('needle', [-1, CY, 13], [1, CY + RAD - 5, 14], tipped_up(PAL, 'GOLD', 'BOW', 4)),
            C('needle_tip', [-2, CY + RAD - 10, 13], [2, CY + RAD - 6, 14], sol(PAL, 'BOW')),
            C('needle_weight', [-2, CY - 7, 13], [2, CY, 14], gold),
            C('needle_cap', [-3, CY - 3, 12], [3, CY + 3, 13], dgold)])]
    wheel = Group('wheel', [0, CY, 16], wheel_children)

    coattail = Group('coattail', [0, 50, 8], [
        C('coattail_r', [1, 22, 7], [11, 50, 9], ragged(suit, .2, 1)),
        C('coattail_l', [-11, 22, 7], [-1, 50, 9], ragged(suit, .2, 1))])
    tail_tip = Group('tail3', [0, 45.5, 27], [
        C('tail_spade', [-3, 43.5, 27], [3, 47.5, 31], sol(PAL, 'DSKIN')),
        C('tail_spade_tip', [-1, 44.5, 31], [1, 46.5, 33], sol(PAL, 'DSKIN'))])
    tail2 = Group('tail2', [0, 45.5, 17], [C('tail2', [-1, 44.5, 17], [1, 46.5, 27], skin), tail_tip], [25, 0, 0])
    tail = Group('tail1', [0, 45.5, 8], [C('tail1', [-1.5, 44, 8], [1.5, 47, 17], skin), tail2], [-20, 0, 0])

    body = Group('body', [0, 44, 0], [
        C('pelvis', [-12, 40, -7], [12, 50, 7], suit),
        C('waist', [-11, 50, -7], [11, 62, 7], sol(PAL, 'VEST')),
        C('vest_front', [-10, 48, -8], [10, 74, -7], faces(sol(PAL, 'VEST'), north=vest_front)),
        C('chest', [-15, 62, -9], [15, 84, 9], suit),
        C('lapel_r', [4, 66, -10], [10, 82, -9], sol(PAL, 'SATIN')),
        C('lapel_l', [-10, 66, -10], [-4, 82, -9], sol(PAL, 'SATIN')),
        C('shirt_front', [-4, 74, -10], [4, 84, -9], shirt),
        C('bowtie', [-5, 79, -11], [5, 82, -10], sol(PAL, 'BOW')),
        C('bowtie_knot', [-1, 78, -12], [1, 83, -11], sol(PAL, 'BOW', .02)),
        C('collar', [-9, 82, 3], [9, 90, 9], sol(PAL, 'DSUIT')),
        C('pocket_square', [9, 76, -10], [13, 79, -9], sol(PAL, 'GOLD')),
        C('wheel_mount', [-3, 78, 9], [3, 90, 15], dgold),
        coattail, tail, head, wheel,
        upper_arm('right'), upper_arm('left'), lower_arm('right'), lower_arm('left')])
    return Group('root', [0, 0, 0], [body, leg('right'), leg('left')])

# ---------------- 애니메이션 ----------------
NEEDLE_SPIN = 6.0

def needle_spin(k, trick=False):
    """바늘: 빠르게 5바퀴 → 감속 → 구역 k에 멈춤. trick=한 칸 앞에 멈춘 척하다 k로 넘어감."""
    total = 360 * 5 + 60 * k
    keys = []
    n = 16
    stop = 5.2 if trick else NEEDLE_SPIN - .3
    fake = total - 60 + 24                     # 한 칸 앞 구역 끝자락
    end = fake if trick else total
    for i in range(n + 1):
        f = i / n; t = round(stop * f, 3)
        keys.append(R(t, 0, 0, round(end * (1 - (1 - f) ** 3), 2), 'linear'))
    if trick:
        keys += [R(5.9, 0, 0, fake + 1), R(6.15, 0, 0, total + 6), R(6.35, 0, 0, total - 2), R(6.5, 0, 0, total)]
    else:
        keys += [R(NEEDLE_SPIN, 0, 0, total)]
    return {'needle': keys}

def anims(G):
    L = 1.4
    walk = mirror_tracks(biped_walk(L, 24, 14, 30, .8, extra={
        'right_arm2': [R(0, 8), R(L / 2, -8), R(L, 8)],
        'coattail': [R(0, 6), R(L / 4, 12), R(L / 2, 6), R(3 * L / 4, 12), R(L, 6)],
        'tail1': [R(0, 0, 10), R(L / 2, 0, -10), R(L, 0, 10)],
        'head': [R(0, 0, 3), R(L / 2, 0, -3), R(L, 0, 3)],
        'wheel': [R(0, 0, 0, 2), R(L / 2, 0, 0, -2), R(L, 0, 0, 2)]}))
    walk['left_arm2'] = [R(0, -8), R(L / 2, 8), R(L, -8)]

    idle = mirror_tracks({
        'body': osc('position', 3, 0, -1, 0, 4, 1),
        'head': [R(0, 2, 6), R(1.5, -2, -6), R(3, 2, 6)],
        'right_arm': osc('rotation', 3, -4, 3, 0, 4, 0),
        'right_hand2': [R(0, -10), R(.5, 10), R(1, -10), R(1.5, 10), R(2, -10), R(2.5, 10), R(3, -10)],
        'right_forearm2': osc('rotation', 3, -5, 5, 1, 4, 0),
        'tail1': osc('rotation', 3, -12, 12, 0, 4, 1),
        'tail2': osc('rotation', 3, -10, 10, 1, 4, 1),
        'coattail': osc('rotation', 3, 0, 4, 0, 4, 0),
        'needle': [R(0, 0, 0, -8), R(1.5, 0, 0, 8), R(3, 0, 0, -8)]})

    spawn = mirror_tracks({   # 바닥에서 솟아 모자 들어 인사
        'root': [T(0, 0, -130, 0), T(1.2, 0, 4, 0), T(1.5, 0, 0, 0), S(0, .8, .5, .8), S(1.2, 1, 1.06, 1), S(1.5, 1, 1, 1)],
        'body': [Z(0), R(1.5), R(1.9, 28), R(2.5, 28), R(3.0)],
        'head': [Z(0), R(1.9, 10), R(2.5, 10), R(3.0)],
        'hat': [T(0), T(1.9), T(2.2, 0, 8, -3), T(2.5, 0, 8, -3), T(2.8)],
        'right_arm': [Z(0), R(1.5), R(1.9, 150, 0, 15), R(2.5, 150, 0, 15), R(3.0)],
        'right_forearm': [Z(0), R(1.9, 30), R(2.5, 30), R(3.0)],
        'left_arm': [Z(0), R(1.9, -30, 0, -10), R(2.5, -30, 0, -10), R(3.0)],
        'right_arm2': [Z(0), R(1.9, 40, 0, -20), R(2.5, 40, 0, -20), R(3.0)],
        'left_arm2': [Z(0), R(1.9, 40, 0, 20), R(2.5, 40, 0, 20), R(3.0)],
        'needle': [Z(0), R(1.2, 0, 0, 0), R(3.0, 0, 0, 720)]})

    attack = {   # 근접: 위 오른팔 손등 후려치기 + 아래 팔 따라 할퀴기
        'root': [T(0), T(.4, 0, 0, 3), T(.55, 0, 0, -6), T(.9, 0, 0, -6), T(1.2)],
        'body': [Z(0), R(.4, 0, 30), R(.55, 6, -35), R(.9, 6, -30), R(1.2)],
        'right_arm': [Z(0), R(.4, 40, 0, 70), R(.55, 85, 0, -40), R(.9, 80, 0, -35), R(1.2)],
        'right_forearm': [Z(0), R(.4, -20), R(.55, 5), R(1.2)],
        'right_arm2': [Z(0), R(.45, 30, 0, 60), R(.62, 70, 0, -30), R(.95, 60, 0, -25), R(1.2)],
        'left_arm': [Z(0), R(.4, -20, 0, -15), R(.55, -10, 0, -25), R(1.2)],
        'head': [Z(0), R(.4, 5, 15), R(.55, -5, -15), R(1.2)],
        'coattail': [Z(0), R(.55, 20, 0, -10), R(1.2)]}

    death = mirror_tracks({   # 무릎 꿇고 앞으로 고꾸라짐, 모자가 굴러 떨어짐, 바늘이 풀려 돎
        'root': [T(0), T(.8, 0, -12, 0), T(2.0, 0, -14, -10), R(0), R(.8, 0), R(2.0, -80), R(3.0, -80)],
        'right_leg': [Z(0), R(.8, 70), R(3.0, 70)],
        'right_shin': [Z(0), R(.8, -95), R(3.0, -95)],
        'head': [Z(0), R(.8, 25), R(2.0, -20), R(3.0, -20)],
        'hat': [T(0), T(.8), T(1.4, 0, 14, -16), T(2.2, 0, -40, -60), T(3.0, 0, -42, -64), R(0), R(1.4, -60), R(2.2, -200), R(3.0, -180)],
        'right_arm': [Z(0), R(.8, -20, 0, 25), R(2.0, 150, 0, 30), R(3.0, 150, 0, 30)],
        'right_arm2': [Z(0), R(.8, -30, 0, 10), R(2.0, 120, 0, 50), R(3.0, 120, 0, 50)],
        'needle': [Z(0), R(1.0, 0, 0, 300), R(2.6, 0, 0, 560), R(3.0, 0, 0, 575)],
        'wheel': [Z(0), R(2.0, 0, 0, 8), R(2.3, 0, 0, 14), R(3.0, 0, 0, 14)]})
    death['left_leg'] = [Z(0), R(.8, 30), R(3.0, 30)]
    death['left_shin'] = [Z(0), R(.8, -40), R(3.0, -40)]

    # ---- 패턴 모션 ----
    spin_cast = mirror_tracks({   # 룰렛 돌리기 (루프 2.0): 위 팔로 휠 쪽을 쓸어 돌리고 아래 팔은 펼침
        'body': [R(0, -6), R(1.0, -10), R(2.0, -6)],
        'head': [R(0, -16, 10), R(1.0, -20, -10), R(2.0, -16, 10)],
        'right_arm': [R(0, 160, 0, 10), R(.5, 175, 0, -10), R(1.0, 150, 0, 25), R(1.5, 175, 0, -10), R(2.0, 160, 0, 10)],
        'right_forearm': [R(0, -20), R(1.0, -35), R(2.0, -20)],
        'right_arm2': [R(0, 20, 0, 60), R(1.0, 25, 0, 70), R(2.0, 20, 0, 60)],
        'right_hand2': [R(0, -40), R(1.0, -55), R(2.0, -40)],
        'tail1': osc('rotation', 2, -15, 15, 0, 4, 1)})
    spin_cast['left_arm'] = [R(0, 150, 0, -25), R(.5, 175, 0, 10), R(1.0, 160, 0, -10), R(1.5, 175, 0, 10), R(2.0, 150, 0, -25)]
    call_bets = mirror_tracks({   # 베팅 마감: 네 팔 활짝, 손바닥 위
        'body': [Z(0), R(.4, -12), R(1.2, -12), R(1.6)],
        'head': [Z(0), R(.4, -20), R(.8, -26), R(1.2, -20), R(1.6)],
        'right_arm': [Z(0), R(.4, 20, 0, 85), R(1.2, 20, 0, 85), R(1.6)],
        'right_forearm': [Z(0), R(.4, 40), R(1.2, 40), R(1.6)],
        'right_hand': [Z(0), R(.4, -50), R(1.2, -50), R(1.6)],
        'right_arm2': [Z(0), R(.4, 30, 0, 60), R(1.2, 30, 0, 60), R(1.6)],
        'right_hand2': [Z(0), R(.4, -60), R(1.2, -60), R(1.6)]})
    card_throw = mirror_tracks({   # 카드 날리기: 네 팔이 몸 앞에서 바깥으로 휙
        'root': [T(0), T(.35, 0, 0, 3), T(.5, 0, 0, -2), T(1.0)],
        'body': [Z(0), R(.35, 10), R(.5, -8), R(1.0)],
        'right_arm': [Z(0), R(.35, 70, 0, -40), R(.5, 80, 0, 60), R(.75, 75, 0, 55), R(1.0)],
        'right_forearm': [Z(0), R(.35, 50), R(.5, 0), R(1.0)],
        'right_hand': [Z(0), R(.35, 30), R(.5, -40), R(1.0)],
        'right_arm2': [Z(0), R(.4, 60, 0, -30), R(.55, 70, 0, 70), R(.8, 65, 0, 65), R(1.0)],
        'right_forearm2': [Z(0), R(.4, 30), R(.55, -10), R(1.0)]})
    chip_drop = mirror_tracks({   # 칩 탑 낙하: 왼팔 하늘로 손가락 튕김 → 아래로 가리킴
        'body': [Z(0), R(.3, -8), R(.7, 6), R(1.2)],
        'head': [Z(0), R(.3, -25), R(.7, 10), R(1.2)],
        'right_arm2': [Z(0), R(.3, -10, 0, 20), R(.7, 30, 0, 30), R(1.2)]})
    chip_drop['left_arm'] = [Z(0), R(.3, 175, 0, 5), R(.45, 178, 0, 5), R(.7, 70, 0, -10), R(1.2)]
    chip_drop['left_forearm'] = [Z(0), R(.3, -10), R(.7, 10), R(1.2)]
    chip_drop['left_hand'] = [Z(0), R(.4, -30), R(.45, 20), R(.7, 30), R(1.2)]
    dice_roll = {   # 주사위 굴리기: 왼 위팔 언더핸드 투구
        'root': [T(0), T(.4, 0, -3, 4), T(.6, 0, -2, -4), T(1.2)],
        'body': [Z(0), R(.4, 14, -15), R(.6, 22, 15), R(.9, 18, 10), R(1.2)],
        'left_arm': [Z(0), R(.4, -60, 0, -10), R(.6, 70, 0, -5), R(.9, 60, 0, -5), R(1.2)],
        'left_forearm': [Z(0), R(.4, 10), R(.6, -10), R(1.2)],
        'right_arm': [Z(0), R(.4, 30, 0, 20), R(.6, -20, 0, 30), R(1.2)],
        'right_leg': [Z(0), R(.4, 20), R(.6, 30), R(1.2)],
        'left_leg': [Z(0), R(.4, -15), R(1.2)],
        'head': [Z(0), R(.6, 15), R(1.2)]}
    dealer_hand = mirror_tracks({   # 딜러의 손: 위 팔 십자 → 아래 팔 X자
        'body': [Z(0), R(.35, -6), R(.7, 4), R(1.05, -6), R(1.4)],
        'right_arm': [Z(0), R(.3, 15, 0, 90), R(.6, 15, 0, 90), R(.75, 10, 0, 60), R(1.4)],
        'right_forearm': [Z(0), R(.3, -20), R(.6, -20), R(1.4)],
        'right_arm2': [Z(0), R(.3, 10, 0, 20), R(.65, 40, 0, 45), R(1.05, 40, 0, 45), R(1.4)],
        'right_forearm2': [Z(0), R(.65, -30), R(1.05, -30), R(1.4)],
        'head': [Z(0), R(.35, -10), R(.75, 10), R(1.4)]})
    coin_flip = {   # 동전 던지기: 오른 아래 손 엄지 튕김 → 위를 올려다봄
        'body': [Z(0), R(.4, 4), R(.6, -10), R(1.3, -10), R(1.6)],
        'head': [Z(0), R(.4, 10), R(.6, -35), R(1.3, -30), R(1.6)],
        'right_arm2': [Z(0), R(.4, 60, 0, 10), R(.55, 110, 0, 10), R(1.3, 100, 0, 10), R(1.6)],
        'right_forearm2': [Z(0), R(.4, 40), R(.55, -10), R(1.6)],
        'right_hand2': [Z(0), R(.4, 30), R(.55, -40), R(1.6)],
        'left_arm': [Z(0), R(.6, 20, 0, -40), R(1.3, 20, 0, -40), R(1.6)]}
    all_in = mirror_tracks({   # 올인: 네 팔 모아 들었다가 앞 반원 휩쓸며 내려찍기
        'root': [T(0), T(.6, 0, 6, 4), T(.85, 0, -6, -10), T(1.2, 0, -6, -10), T(1.6)],
        'body': [Z(0), R(.6, -20), R(.85, 35), R(1.2, 32), R(1.6)],
        'head': [Z(0), R(.6, -15), R(.85, -10), R(1.6)],
        'right_arm': [Z(0), R(.6, 170, 0, 30), R(.85, 60, 0, 20), R(1.2, 55, 0, 18), R(1.6)],
        'right_forearm': [Z(0), R(.6, -10), R(.85, 20), R(1.6)],
        'right_arm2': [Z(0), R(.6, 140, 0, 50), R(.85, 50, 0, 40), R(1.2, 45, 0, 38), R(1.6)],
        'right_leg': [Z(0), R(.6, -10), R(.85, 30), R(1.2, 30), R(1.6)],
        'right_shin': [Z(0), R(.85, -30), R(1.2, -30), R(1.6)],
        'coattail': [Z(0), R(.6, -10), R(.85, 30), R(1.6)],
        'hat': [T(0), T(.6, 0, 2), T(.85, 0, -1), T(1.6)]})
    all_in['left_leg'] = [Z(0), R(.85, -25), R(1.2, -25), R(1.6)]
    all_in['left_shin'] = [Z(0), R(1.6)]
    chip_recall = mirror_tracks({   # 칩 회수: 팔 뻗었다가 가슴으로 끌어모음
        'body': [Z(0), R(.4, 8), R(.9, -12), R(1.4)],
        'right_arm': [Z(0), R(.4, 90, 0, 45), R(.9, 40, 0, -25), R(1.1, 40, 0, -25), R(1.4)],
        'right_forearm': [Z(0), R(.4, -10), R(.9, 70), R(1.1, 70), R(1.4)],
        'right_hand': [Z(0), R(.4, -30), R(.9, 30), R(1.4)],
        'right_arm2': [Z(0), R(.4, 80, 0, 50), R(.9, 30, 0, -10), R(1.1, 30, 0, -10), R(1.4)],
        'right_forearm2': [Z(0), R(.9, 60), R(1.1, 60), R(1.4)]})
    groggy = mirror_tracks({   # 그로기 (루프 3.0): 한 무릎 꿇고 축 늘어짐, 모자 삐딱
        'root': [T(0, 0, -11, 0), T(1.5, 0, -12, 0), T(3.0, 0, -11, 0)],
        'right_leg': [R(0, 75), R(3.0, 75)],
        'right_shin': [R(0, -95), R(3.0, -95)],
        'body': [R(0, 22, 0, 4), R(1.5, 26, 0, 6), R(3.0, 22, 0, 4)],
        'head': [R(0, 25, 0, 12), R(1.5, 30, 0, 14), R(3.0, 25, 0, 12)],
        'hat': [T(0, 3, -1, -2), T(3.0, 3, -1, -2), R(0, 0, 0, -22), R(3.0, 0, 0, -22)],
        'right_arm': [R(0, -10, 0, -8), R(1.5, -6, 0, -6), R(3.0, -10, 0, -8)],
        'right_forearm': [R(0, -20), R(3.0, -20)],
        'right_arm2': [R(0, -10, 0, -20), R(3.0, -10, 0, -20)],
        'needle': [R(0, 0, 0, 170), R(1.5, 0, 0, 185), R(3.0, 0, 0, 170)],
        'tail1': [R(0, 30), R(3.0, 30)]})
    groggy['left_leg'] = [R(0, -30), R(3.0, -30)]
    groggy['left_shin'] = [R(0, -10), R(3.0, -10)]
    enrage = mirror_tracks({   # 광폭화: 고개 젖혀 웃으며 네 팔을 하늘로, 바늘 폭주
        'root': [T(0), T(.5, 0, -4), T(.9, 0, 4), T(2.0, 0, 4), T(2.4)],
        'body': [Z(0), R(.5, 15), R(.9, -18), R(1.1, -14), R(1.3, -18), R(1.5, -14), R(1.7, -18), R(2.0, -16), R(2.4)],
        'head': [Z(0), R(.5, 20), R(.9, -45), R(1.2, -38), R(1.5, -45), R(1.8, -38), R(2.0, -42), R(2.4)],
        'right_arm': [Z(0), R(.5, 20, 0, -20), R(.9, 175, 0, 25), R(2.0, 170, 0, 30), R(2.4)],
        'right_arm2': [Z(0), R(.5, 20, 0, 10), R(.9, 120, 0, 70), R(2.0, 115, 0, 70), R(2.4)],
        'right_hand': [Z(0), R(.9, -40), R(2.4)],
        'hat': [T(0), T(.9, 0, 5, 2), T(1.1, 0, 2, 2), T(1.3, 0, 5, 2), T(1.5, 0, 2, 2), T(2.4)],
        'needle': [Z(0), R(2.4, 0, 0, 1440, 'linear')],
        'tail1': [Z(0), R(.9, -40), R(2.0, -40), R(2.4)]})
    needle_fast = {'needle': [R(0, 0, 0, 0, 'linear'), R(.5, 0, 0, 360, 'linear')]}

    spec = [('spawn', 3.0, 'once', spawn), ('idle', 3.0, 'loop', idle), ('walk', L, 'loop', walk),
            ('attack', 1.2, 'once', attack), ('death', 3.0, 'hold', death),
            ('spin_cast', 2.0, 'loop', spin_cast), ('call_bets', 1.6, 'once', call_bets),
            ('card_throw', 1.0, 'once', card_throw), ('chip_drop', 1.2, 'once', chip_drop),
            ('dice_roll', 1.2, 'once', dice_roll), ('dealer_hand', 1.4, 'once', dealer_hand),
            ('coin_flip', 1.6, 'once', coin_flip), ('all_in', 1.6, 'once', all_in),
            ('chip_recall', 1.4, 'once', chip_recall), ('groggy', 3.0, 'loop', groggy),
            ('enrage', 2.4, 'once', enrage), ('needle_fast', .5, 'loop', needle_fast)]
    for k in range(6):
        spec.append((f'needle_stop_{k}', NEEDLE_SPIN, 'hold', needle_spin(k)))
    for k in range(6):
        spec.append((f'needle_trick_{k}', 6.5, 'hold', needle_spin(k, True)))
    return build_anims(f'animation.{ID}.', spec, G)

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    export(ID, build(), anims, 11, O + ID + '.bbmodel', HITBOX)
    apply_palette(SHINY)
    export(ID + '_shiny', build(), anims, 11, O + ID + '_shiny.bbmodel', HITBOX)
