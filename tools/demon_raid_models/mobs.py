"""룰레타·솜니아 레이드 잡몹 6종 (+이로치).
룰레타: chip_imp(칩 임프) / card_soldier(카드 병정) / dice_mage(주사위 술사)
솜니아: sleepy_moth(졸음 나방) / sleepwalker(몽유 거인) / nightmare_doll(악몽 인형)"""
from common import *
import math, sys

MOBS = {}
def mob(fn): MOBS[fn.__name__] = fn; return fn

# =====================================================================
@mob
def chip_imp():
    PAL = dict(SKIN=(170, 42, 46), DSKIN=(104, 22, 28), CHIP=(196, 36, 44), CHIPE=(238, 232, 220), CHIPD=(120, 18, 26),
               HORN=(232, 220, 190), EYE=(255, 216, 70, 255), MOUTH=(30, 6, 10), GOLD=(228, 182, 72))
    SH = dict(SKIN=(40, 40, 52), DSKIN=(20, 20, 28), CHIP=(30, 30, 40), CHIPE=(230, 190, 70), CHIPD=(16, 16, 22),
              HORN=(240, 200, 90), EYE=(110, 255, 200, 255), MOUTH=(0, 40, 30), GOLD=(110, 255, 200))
    CYc = 11
    def chip_p(box):
        (X0, Y0, _), (X1, Y1, _) = box
        def p(img, face, r, rng):
            x1, y1, x2, y2 = r
            for ly in range(y2 - y1):
                for lx in range(x2 - x1):
                    if face in ('north', 'south'):
                        x = (X1 - lx - .5) if face == 'north' else (X0 + lx + .5); y = Y1 - ly - .5
                        rr = math.hypot(x, y - CYc); th = math.degrees(math.atan2(x, y - CYc)) % 360
                        if rr > 7.3: img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                        c = PAL['CHIP']
                        if rr > 5.6 and int(th // 30) % 2 == 0: c = PAL['CHIPE']
                        elif 3.6 < rr < 4.6: c = PAL['CHIPE']
                        elif rr < 2.2: c = PAL['GOLD']
                    else:
                        c = PAL['CHIPE'] if (lx + ly) % 4 < 2 else PAL['CHIP']
                    img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.05, .05))))
        return p
    def face(img, r, rng):
        for ex in (1, 4): px(img, r, ex, 2, col(PAL['EYE']))
        for lx in range(1, 5): px(img, r, lx, 4, col(PAL['MOUTH']))
        px(img, r, 2, 4, col(PAL['HORN'])); px(img, r, 4, 4, col(PAL['HORN']))
    def build():
        skin = sol(PAL, 'SKIN'); horn = tipped_up(PAL, 'HORN', 'DSKIN', 1)
        bars = []
        for i, (frm, to) in enumerate([([-4, 4, -2], [4, 18, 2]), ([-6, 6, -2], [6, 16, 2]), ([-7, 8, -2], [7, 14, 2])]):
            bars.append(C(f'chip{i+1}', frm, to, chip_p((frm, to))))
        def limb(s):
            m, P, Rt = side(s)
            arm = Group(f'{s}_arm', P([6, 12, 0]), [C(f'{s}_arm', [6, 8, -1], [8, 13, 1], skin, m),
                                                     C(f'{s}_claw', [6, 6, -1.5], [8, 8, 0.5], tipped(PAL, 'SKIN', 'HORN', 1), m)], Rt([0, 0, 10]))
            leg = Group(f'{s}_leg', P([2.5, 5, 0]), [C(f'{s}_leg', [1.5, 1, -1], [3.5, 5, 1], skin, m),
                                                     C(f'{s}_foot', [1, 0, -2.5], [4, 1, 1], sol(PAL, 'DSKIN'), m)])
            return arm, leg
        ra, rl = limb('right'); la, ll = limb('left')
        head = Group('head', [0, 17, 0], [
            C('head', [-3, 17, -3], [3, 22, 2], faces(skin, north=face)),
            C('right_horn', [2, 21, -1], [3, 24, 0], horn), C('left_horn', [-3, 21, -1], [-2, 24, 0], horn),
            C('right_ear', [3, 19, -1], [5, 20, 0], skin), C('left_ear', [-5, 19, -1], [-3, 20, 0], skin)])
        tail = Group('tail', [0, 8, 2], [C('tail', [-.5, 7.5, 2], [.5, 8.5, 7], skin),
                                         C('tail_tip', [-1, 7, 7], [1, 9, 9], sol(PAL, 'DSKIN'))], [-20, 0, 0])
        body = Group('body', [0, 11, 0], [*bars, head, ra, la, tail])
        return Group('root', [0, 0, 0], [body, rl, ll])
    def anims(G):
        L = .6
        tr = {'right_leg': [R(0, 30), R(L / 2, -30), R(L, 30)], 'left_leg': [R(0, -30), R(L / 2, 30), R(L, -30)],
              'body': [R(0, 0, 0, 6), R(L / 2, 0, 0, -6), R(L, 0, 0, 6)],
              'right_arm': [R(0, -25), R(L / 2, 25), R(L, -25)], 'left_arm': [R(0, 25), R(L / 2, -25), R(L, 25)],
              'tail': [R(0, 0, 15), R(L / 2, 0, -15), R(L, 0, 15)]}
        spec = [
            ('spawn', 1.0, 'once', {'root': [S(0, .2, .2, .2), S(.6, 1.2, 1.2, 1.2), S(.8)], 'body': [Z(0), R(.6, 0, 360), R(1.0, 0, 360)]}),
            ('idle', 2.0, 'loop', {'body': [R(0, 0, 0, -4), R(1, 0, 0, 4), R(2, 0, 0, -4)], 'head': osc('rotation', 2, -6, 6, 0, 4, 1),
                                   'tail': osc('rotation', 2, -20, 20, 0, 4, 1)}),
            ('walk', L, 'loop', tr),
            ('attack', 1.0, 'once', {   # 몸을 웅크려 칩처럼 굴러 들이받기
                'root': [T(0), T(.25, 0, 0, 2), T(.7, 0, 0, -10), T(1.0)],
                'body': [Z(0), R(.25, 15), R(.7, -360, 0, 0, 'linear'), R(.71, 0, 0, 0, 'linear'), R(1.0)],
                'right_arm': [Z(0), R(.25, -40, 0, -30), R(.7, -40, 0, -30), R(1.0)],
                'left_arm': [Z(0), R(.25, -40, 0, 30), R(.7, -40, 0, 30), R(1.0)]}),
            ('death', 1.4, 'hold', {   # 쓰러져 빙글 돌다 납작
                'body': [Z(0), R(.4, 0, 0, 30), R(1.0, 0, 0, 90), R(1.4, 0, 0, 90)],
                'root': [T(0), T(.4, 0, 2), T(1.0, 0, -2), T(1.4, 0, -3)]})]
        return build_anims('animation.chip_imp.', spec, G)
    return build, anims, PAL, SH, (0.8, 1.5, 1.2)

# =====================================================================
@mob
def card_soldier():
    PAL = dict(CARD=(238, 232, 218), INK=(186, 28, 40), BLACK=(30, 26, 34), IRON=(70, 66, 80), DIRON=(40, 38, 48),
               GOLD=(226, 180, 70), EYE=(255, 70, 60, 255), SKIN=(140, 34, 40), BLADE=(210, 214, 226))
    SH = dict(CARD=(36, 34, 46), INK=(90, 220, 255), BLACK=(230, 226, 214), IRON=(200, 200, 214), DIRON=(140, 140, 156),
              GOLD=(140, 230, 255), EYE=(90, 220, 255, 255), SKIN=(40, 110, 130), BLADE=(255, 214, 100))
    def card_front(img, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for lx in range(w):
            px(img, r, lx, 1, col(PAL['INK'])); px(img, r, lx, h - 2, col(PAL['INK']))
        for ly in range(1, h - 1):
            px(img, r, 1, ly, col(PAL['INK'])); px(img, r, w - 2, ly, col(PAL['INK']))
        cx, cy = w // 2, h // 2       # 큰 스페이드
        for dy in range(-4, 4):
            half = (dy + 5) // 2 if dy < 1 else 3 - dy
            for dx in range(-half, half + 1): px(img, r, cx + dx, cy + dy, col(PAL['INK']))
        for dy in (4, 5): px(img, r, cx, cy + dy, col(PAL['INK']))
        for p_ in ((3, 3), (w - 4, h - 4)): px(img, r, *p_, col(PAL['INK']))
    def card_back(img, r, rng):
        x1, y1, x2, y2 = r
        for ly in range(1, y2 - y1 - 1):
            for lx in range(1, x2 - x1 - 1):
                px(img, r, lx, ly, col(PAL['INK'] if (lx + ly) % 3 == 0 else PAL['BLACK']))
    def visor(img, r, rng):
        for lx in range(1, 7): px(img, r, lx, 3, col(PAL['BLACK']))
        px(img, r, 2, 3, col(PAL['EYE'])); px(img, r, 5, 3, col(PAL['EYE']))
    def build():
        iron = sol(PAL, 'IRON'); diron = sol(PAL, 'DIRON'); blk = sol(PAL, 'BLACK')
        def arm(s):
            m, P, Rt = side(s)
            extra = []
            if s == 'right':
                extra = [Group('sword', [8, 16, -2], [
                    C('sword_grip', [7.5, 14, -3], [8.5, 18, -2], sol(PAL, 'BLACK')),
                    C('sword_guard', [6.5, 17, -4], [9.5, 18, -1], sol(PAL, 'GOLD')),
                    C('sword_blade', [7.5, 18, -3], [8.5, 36, -2], tipped_up(PAL, 'BLADE', 'INK', 2))], [-70, 0, 0])]
            else:
                extra = [C('left_shield', [-11, 14, -5], [-10, 24, 3], faces(sol(PAL, 'CARD', .03), west=card_back, east=card_back))]
            fore = Group(f'{s}_forearm', P([8.5, 23, 0]), [C(f'{s}_forearm', [7.5, 16, -1], [9.5, 24, 1], blk, m),
                                                            C(f'{s}_hand', [7, 14, -1.5], [10, 17, 1.5], diron, m), *extra], [20, 0, 0])
            return Group(f'{s}_arm', P([8.5, 32, 0]), [C(f'{s}_upper_arm', [7.5, 23, -1], [9.5, 32, 1], blk, m),
                                                      C(f'{s}_pauldron', [6, 30, -3], [11, 35, 3], trim(PAL, 'IRON', 'GOLD'), m), fore])
        def leg(s):
            m, P, Rt = side(s)
            shin = Group(f'{s}_shin', P([3.5, 7, 0]), [C(f'{s}_shin', [2.5, 2, -1], [4.5, 7, 1], blk, m),
                                                       C(f'{s}_boot', [2, 0, -3], [5, 3, 1.5], diron, m)])
            return Group(f'{s}_leg', P([3.5, 14, 0]), [C(f'{s}_thigh', [2.5, 7, -1], [4.5, 14, 1], blk, m), shin])
        head = Group('head', [0, 35, 0], [
            C('helmet', [-4, 35, -4], [4, 42, 4], faces(iron, north=visor)),
            C('helmet_crest', [-1, 42, -3], [1, 46, 4], sol(PAL, 'INK')),
            C('right_helm_horn', [4, 39, -1], [6, 41, 1], sol(PAL, 'GOLD')), C('right_helm_horn2', [5, 41, -1], [6, 45, 0], sol(PAL, 'GOLD')),
            C('left_helm_horn', [-6, 39, -1], [-4, 41, 1], sol(PAL, 'GOLD')), C('left_helm_horn2', [-6, 41, -1], [-5, 45, 0], sol(PAL, 'GOLD'))])
        body = Group('body', [0, 14, 0], [
            C('card_body', [-7, 13, -1], [7, 35, 1], faces(sol(PAL, 'CARD', .03), north=card_front, south=card_back)),
            C('belt', [-6, 14, -2], [6, 16, 2], sol(PAL, 'DIRON')), C('belt_buckle', [-1, 13.5, -3], [1, 16.5, -2], sol(PAL, 'GOLD')),
            C('gorget', [-4, 33, -2], [4, 36, 2], iron), head, arm('right'), arm('left')])
        return Group('root', [0, 0, 0], [body, leg('right'), leg('left')])
    def anims(G):
        L = 1.0
        swing = lambda t0, a, b: [('rotation', t0, *a, CM), ('rotation', t0 + .15, *b, CM)]
        spec = [
            ('spawn', 1.4, 'once', {'root': [R(0, 0, 90), R(.8, 0, -20), R(1.0, 0, 0), S(0, .1, 1, 1), S(.8, 1, 1, 1)],
                                    'right_arm': [Z(0), R(.8, 120), R(1.4)]}),
            ('idle', 2.4, 'loop', {'body': osc('position', 2.4, 0, -.4, 0, 4, 1), 'head': osc('rotation', 2.4, -4, 4, 0, 4, 1),
                                   'right_arm': osc('rotation', 2.4, -3, 3, 0, 4, 0)}),
            ('walk', L, 'loop', mirror_tracks(biped_walk(L, 28, 14, 30, .4))),
            ('attack', 1.6, 'once', {   # 3연 베기
                'right_arm': [Z(0), R(.2, 40, 0, 70), R(.35, 80, 0, -30), R(.65, 60, 0, -40), R(.8, 70, 0, 60), R(1.1, 110), R(1.25, 50), R(1.6)],
                'right_forearm': [Z(0), R(.35, -10), R(1.1, -30), R(1.25, 10), R(1.6)],
                'body': [Z(0), R(.2, 0, 25), R(.35, 0, -25), R(.65, 0, -30), R(.8, 0, 25), R(1.1, -10), R(1.25, 15), R(1.6)],
                'root': [T(0), T(.35, 0, 0, -2), T(.8, 0, 0, -4), T(1.25, 0, 0, -6), T(1.6)]}),
            ('death', 1.6, 'hold', {   # 카드처럼 납작하게 뒤로 쓰러짐
                'root': [Z(0), R(.3, -10), R(1.0, 88), R(1.6, 88), T(0), T(1.0, 0, 0, 0)],
                'right_arm': [Z(0), R(1.0, -40, 0, 40)], 'left_arm': [Z(0), R(1.0, -40, 0, -40)]})]
        return build_anims('animation.card_soldier.', spec, G)
    return build, anims, PAL, SH, (0.9, 2.8, 2.3)

# =====================================================================
@mob
def dice_mage():
    PAL = dict(DIE=(236, 230, 218), PIP=(30, 24, 34), EYE=(255, 60, 70, 255), ROBE=(70, 26, 92), DROBE=(38, 14, 52),
               TRIM=(226, 180, 70), SKIN=(150, 36, 44), HORN=(40, 32, 44))
    SH = dict(DIE=(30, 30, 40), PIP=(250, 214, 90), EYE=(90, 255, 160, 255), ROBE=(220, 214, 196), DROBE=(170, 160, 140),
              TRIM=(60, 170, 120), SKIN=(50, 110, 90), HORN=(240, 220, 160))
    def head_die(img, face, r, rng):
        dice(PAL, 'DIE', 'PIP', None, .14)(img, face, r, rng)
        if face == 'north':   # 1의 눈 자리에 빛나는 눈알
            w, h = r[2] - r[0], r[3] - r[1]
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    px(img, r, w // 2 + dx, h // 2 + dy, col(PAL['EYE'] if (dx, dy) != (0, 0) else PAL['PIP']))
            for i in range(3): px(img, r, 1 + i, i, col(PAL['PIP']))   # 금 간 모서리
    def build():
        robe = grad(PAL, 'ROBE', 'DROBE'); skin = sol(PAL, 'SKIN')
        def arm(s):
            m, P, Rt = side(s)
            held = [C('held_die', [5, 9, -6], [8, 12, -3], dice(PAL, 'DIE', 'PIP', None, .2))] if s == 'right' else []
            hand = Group(f'{s}_hand', P([6.5, 13, -1]), [C(f'{s}_hand', [5, 11, -3], [8, 14, 0], skin, m),
                                                         C(f'{s}_claw', [5, 9, -3], [8, 11, -2], tipped(PAL, 'SKIN', 'HORN', 1), m), *held])
            return Group(f'{s}_arm', P([6.5, 23, 0]), [
                C(f'{s}_sleeve', [5, 13, -2.5], [9, 23, 2.5], robe, m),
                C(f'{s}_cuff', [4.5, 13, -3], [9.5, 15, 3], sol(PAL, 'TRIM'), m), hand], Rt([10, 0, 10]))
        head = Group('head', [0, 24, 0], [
            C('die_head', [-4.5, 24, -4.5], [4.5, 33, 4.5], head_die),
            C('right_horn', [3, 31, -1], [5, 35, 1], tipped_up(PAL, 'HORN', 'TRIM', 1)),
            C('left_horn', [-5, 31, -1], [-3, 35, 1], tipped_up(PAL, 'HORN', 'TRIM', 1)),
            C('hood', [-5.5, 23, 1], [5.5, 31, 6], robe)])
        body = Group('body', [0, 12, 0], [
            C('robe_upper', [-5, 14, -4], [5, 24, 4], faces(robe, north=lambda i, r, g: [px(i, r, (r[2] - r[0]) // 2, y, col(PAL['TRIM'])) for y in range(r[3] - r[1])])),
            C('mantle', [-6, 20, -5], [6, 24, 5], trim(PAL, 'DROBE', 'TRIM')),
            Group('robe_lower', [0, 14, 0], [C('robe_lower', [-6, 0, -5], [6, 14, 5], ragged(robe, .3, 1)),
                                             C('robe_hem', [-7, 0, -6], [7, 3, 6], ragged(trim(PAL, 'DROBE', 'TRIM'), .3, 1))]),
            head, arm('right'), arm('left')])
        return Group('root', [0, 0, 0], [body])
    def anims(G):
        L = 1.2
        spec = [
            ('spawn', 1.4, 'once', {'root': [T(0, 0, -34), T(1.0, 0, 1), T(1.4)], 'head': [Z(0), R(1.0, 0, 360), R(1.4, 0, 360)]}),
            ('idle', 3.0, 'loop', {'head': [R(0, 0, -8), R(1.5, 0, 8), R(3, 0, -8)], 'body': osc('position', 3, 0, .6, 0, 4, 1),
                                   'right_hand': osc('rotation', 3, -10, 10, 0, 4, 0)}),
            ('walk', L, 'loop', {'robe_lower': [R(0, 0, 0, 5), R(L / 2, 0, 0, -5), R(L, 0, 0, 5)],
                                 'body': [T(0), T(L / 4, 0, .6), T(L / 2), T(3 * L / 4, 0, .6), T(L), R(0, 6), R(L, 6)],
                                 'right_arm': [R(0, -12), R(L / 2, 12), R(L, -12)], 'left_arm': [R(0, 12), R(L / 2, -12), R(L, 12)],
                                 'head': [R(0, 0, 4), R(L / 2, 0, -4), R(L, 0, 4)]}),
            ('attack', 1.4, 'once', {   # 주사위 투척: 뒤로 젖혔다가 던짐
                'right_arm': [Z(0), R(.5, -150, 0, 10), R(.75, 70, 0, 0), R(1.0, 60), R(1.4)],
                'body': [Z(0), R(.5, -12, 20), R(.75, 12, -15), R(1.4)],
                'head': [Z(0), R(.5, -15), R(.75, 10), R(1.4)]}),
            ('death', 1.6, 'hold', {   # 로브가 꺼지고 주사위 머리가 굴러떨어짐
                'robe_lower': [S(0), S(1.0, 1.3, .2, 1.3), S(1.6, 1.3, .2, 1.3)],
                'body': [T(0), T(1.0, 0, -10), T(1.6, 0, -10)],
                'head': [T(0), T(.6, 0, 4, -2), T(1.2, 0, -14, -10), T(1.6, 0, -14, -12), R(0), R(1.2, -180), R(1.6, -270)]})]
        return build_anims('animation.dice_mage.', spec, G)
    return build, anims, PAL, SH, (0.8, 2.2, 1.75)

# =====================================================================
@mob
def sleepy_moth():
    PAL = dict(FUR=(214, 200, 232), DFUR=(150, 128, 184), WING=(124, 100, 164), DWING=(70, 52, 104), EDGE=(218, 204, 234),
               LID=(34, 24, 58), EYE=(40, 30, 70, 255), ANT=(92, 78, 112))
    SH = dict(FUR=(255, 230, 190), DFUR=(214, 166, 106), WING=(230, 170, 80), DWING=(176, 96, 42), EDGE=(255, 240, 200),
              LID=(120, 40, 20), EYE=(255, 120, 60, 255), ANT=(200, 150, 90))
    def wing(box, kind):
        (X0, Y0, Z0), (X1, Y1, Z1) = box
        ax0, ax1 = min(abs(X0), abs(X1)), max(abs(X0), abs(X1))
        def p(img, face, r, rng):
            x1, y1, x2, y2 = r
            for ly in range(y2 - y1):
                for lx in range(x2 - x1):
                    if face not in ('north', 'south'): img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                    x = (X1 - lx - .5) if face == 'north' else (X0 + lx + .5); y = Y1 - ly - .5
                    t = (abs(x) - ax0) / (ax1 - ax0); v = (y - Y0) / (Y1 - Y0)
                    if kind == 'fore': ok = ((t - .5) / .55) ** 2 + ((v - .5 - .2 * t) / .5) ** 2 <= 1
                    else: ok = ((t - .45) / .55) ** 2 + ((v - .6 + .2 * t) / .45) ** 2 <= 1
                    if not ok: img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                    c = lerp(PAL['WING'], PAL['DWING'], 1 - v)
                    if ((t - .55) ** 2 + (v - .5) ** 2) < .03: c = PAL['EDGE']
                    if abs((t - .55)) < .2 and abs(v - .5 + 0.6 * (t - .55) ** 2) < .05: c = PAL['LID']
                    img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.05, .05))))
        return p
    def face(img, r, rng):
        for ex in (0, 3):
            px(img, r, ex, 1, col(PAL['EYE'])); px(img, r, ex + 1, 1, col(PAL['EYE']))
            px(img, r, ex, 2, col(PAL['LID'])); px(img, r, ex + 1, 2, col(PAL['LID']))
    def build():
        f = sol(PAL, 'FUR', .15)
        def wings(s):
            m, P, Rt = side(s)
            fb = ([2, 10, -2], [14, 20, -1]); hb = ([2, 4, 0], [11, 12, 1])
            if m: fb = ([-14, 10, -2], [-2, 20, -1]); hb = ([-11, 4, 0], [-2, 12, 1])
            return [Group(f'{s}_wing', P([2, 11, -1.5]), [Cube(f'{s}_wing', *fb, wing(fb, 'fore'))], Rt([0, -15, 10])),
                    Group(f'{s}_hindwing', P([2, 10, .5]), [Cube(f'{s}_hindwing', *hb, wing(hb, 'hind'))], Rt([0, -25, -10]))]
        def ant(s):
            m, P, Rt = side(s)
            return Group(f'{s}_antenna', P([1, 13, -6]), [C(f'{s}_antenna', [.5, 13, -7], [1.5, 18, -6], sol(PAL, 'ANT'), m),
                                                         C(f'{s}_antenna_plume', [0, 14, -7], [3, 18, -6], ragged(sol(PAL, 'ANT'), .4, 1), m)], Rt([-25, 0, -20]))
        head = Group('head', [0, 10, -4], [C('head', [-2.5, 8, -7.5], [2.5, 13, -4], faces(f, north=face)),
                                            C('head_fluff', [-3, 11, -6], [3, 14, -3], ragged(f, .5, 1)), ant('right'), ant('left')])
        abdomen = Group('abdomen', [0, 10, 3], [C('abdomen', [-2, 6, 3], [2, 10, 10], stripes(PAL, 'FUR', 'DFUR', 3, 1, False)),
                                                C('abdomen_tip', [-1, 6, 10], [1, 8, 12], sol(PAL, 'DFUR'))], [15, 0, 0])
        legs = [C(f'leg{i}', [x, 5, z], [x + 1, 8, z + 1], sol(PAL, 'DFUR')) for i, (x, z) in enumerate([(-2.5, -2), (1.5, -2), (-2.5, 1), (1.5, 1)])]
        body = Group('body', [0, 10, 0], [C('thorax', [-3, 7, -4], [3, 13, 3], ragged(f, .3, 1)), *legs, head, abdomen,
                                          *wings('right'), *wings('left')])
        root = Group('root', [0, 0, 0], [body]); shift(root, 6); return root
    def anims(G):
        L = .4
        flap = mirror_tracks({'right_wing': [R(0, 0, 0, 40), R(L / 2, 0, 0, -30), R(L, 0, 0, 40)],
                              'right_hindwing': [R(0, 0, 0, 30), R(L / 2, 0, 0, -25), R(L, 0, 0, 30)]})
        def withf(d): t = dict(flap); t.update(d); return t
        spec = [
            ('spawn', 1.0, 'once', withf({'root': [S(0, .1, .1, .1), S(.8, 1.1, 1.1, 1.1), S(1.0), T(0, 0, -6), T(1.0)]})),
            ('idle', L, 'loop', withf({'root': [T(0), T(L / 2, 0, 1), T(L)], 'abdomen': [R(0, 5), R(L / 2, -5), R(L, 5)]})),
            ('walk', L, 'loop', withf({'body': [R(0, 15), R(L, 15)], 'root': [T(0), T(L / 2, 0, 1.2), T(L)]})),
            ('attack', .9, 'once', withf({'root': [T(0), T(.3, 0, 2, 4), T(.5, 0, -1, -10), T(.9)],
                                          'body': [Z(0), R(.3, -20), R(.5, 30), R(.9)]})),
            ('death', 1.4, 'hold', mirror_tracks({'root': [T(0), T(1.0, 0, -6), T(1.4, 0, -6), R(0), R(1.0, 0, 0, 160), R(1.4, 0, 0, 180)],
                                                   'right_wing': [Z(0), R(.6, 0, 0, 60), R(1.4, 0, 0, 70)]}))]
        return build_anims('animation.sleepy_moth.', spec, G)
    return build, anims, PAL, SH, (0.8, 1.3, 1.0)

# =====================================================================
@mob
def sleepwalker():
    PAL = dict(SKIN=(150, 160, 190), DSKIN=(100, 106, 140), GOWN=(214, 210, 236), DGOWN=(150, 144, 190), CAP=(60, 60, 130),
               DCAP=(36, 36, 90), STAR=(240, 214, 120), LID=(60, 54, 90), MOUTH=(40, 30, 60), NAIL=(210, 200, 220))
    SH = dict(SKIN=(214, 150, 120), DSKIN=(160, 100, 80), GOWN=(60, 60, 80), DGOWN=(34, 34, 50), CAP=(200, 40, 60),
              DCAP=(130, 20, 40), STAR=(120, 230, 255), LID=(110, 50, 40), MOUTH=(70, 20, 20), NAIL=(255, 220, 140))
    def face(img, r, rng):
        x1, y1, x2, y2 = r; w = x2 - x1
        for ex in (2, w - 6):
            for dx in range(4): px(img, r, ex + dx, 5 + (1 if dx in (1, 2) else 0), col(PAL['LID']))   # 감은 눈
        for lx in range(w // 2 - 2, w // 2 + 2):
            px(img, r, lx, 10, col(PAL['MOUTH'])); px(img, r, lx, 11, col(PAL['MOUTH']))
        px(img, r, w // 2 + 1, 12, col(PAL['NAIL'], .9))   # 침
    def gown(): return stripes(PAL, 'GOWN', 'DGOWN', 5, 1)
    def build():
        skin = sol(PAL, 'SKIN'); claw = tipped(PAL, 'SKIN', 'NAIL', 2)
        def arm(s):
            m, P, Rt = side(s)
            fingers = [C(f'{s}_finger{i+1}', [x, 12, -3], [x + 1, 17, -1], claw, m) for i, x in enumerate((9.5, 11, 12.5, 14))]
            hand = Group(f'{s}_hand', P([12, 22, -2]), [C(f'{s}_palm', [9.5, 17, -4], [14.5, 23, 0], skin, m), *fingers])
            fore = Group(f'{s}_forearm', P([12, 36, 0]), [
                C(f'{s}_forearm', [10, 23, -2], [14, 37, 2], skin, m),
                C(f'{s}_sleeve_cuff', [9.5, 33, -2.5], [14.5, 37, 2.5], ragged(gown(), .4, 1), m), hand], [10, 0, 0])
            return Group(f'{s}_arm', P([12, 46, 0]), [
                C(f'{s}_upper_arm', [9.5, 35, -3], [14.5, 48, 3], gown(), m), fore], Rt([75, 0, -5]))   # 앞으로 뻗은 몽유 자세
        def leg(s):
            m, P, Rt = side(s)
            return Group(f'{s}_leg', P([4.5, 12, 0]), [
                C(f'{s}_leg', [2, 3, -2.5], [7, 12, 2.5], skin, m),
                C(f'{s}_foot', [1.5, 0, -6], [7.5, 3, 3], sol(PAL, 'DSKIN'), m)])
        cap_tip = Group('cap_tip', [0, 64, 3], [
            C('cap_tip', [-3, 58, 3], [3, 64, 8], stripes(PAL, 'CAP', 'DCAP', 3, 1, False)),
            C('cap_pom', [-2, 54, 5], [2, 58, 9], sol(PAL, 'STAR'))], [0, 0, 0])
        head = Group('head', [0, 50, 0], [
            C('head', [-7, 49, -8], [7, 62, 5], faces(sol(PAL, 'SKIN', .05), north=face)),
            C('nose', [-1, 53, -10], [1, 57, -8], skin),
            C('right_ear', [7, 53, -3], [9, 57, 0], skin), C('left_ear', [-9, 53, -3], [-7, 57, 0], skin),
            C('cap_band', [-8, 60, -9], [8, 63, 6], sol(PAL, 'STAR')),
            C('cap', [-7, 63, -7], [7, 68, 5], stripes(PAL, 'CAP', 'DCAP', 3, 1, False)), cap_tip])
        hem = Group('gown_hem', [0, 26, 0], [C('gown_lower', [-11, 8, -8], [11, 26, 8], ragged(gown(), .35, 2))])
        body = Group('body', [0, 26, 0], [
            C('gown_upper', [-10, 26, -7], [10, 48, 7], gown()),
            C('belly', [-8, 30, -9], [8, 42, -7], gown()),
            C('collar', [-6, 46, -7.5], [6, 49, 5], sol(PAL, 'DGOWN')), hem, head, arm('right'), arm('left')])
        return Group('root', [0, 0, 0], [body, leg('right'), leg('left')])
    def anims(G):
        L = 1.8
        spec = [
            ('spawn', 2.0, 'once', {'root': [T(0, 0, -50), T(1.6, 0, 0), T(2.0)], 'head': [R(0, 40), R(1.6, 40), R(2.0)],
                                    'right_arm': [R(0, -60), R(1.6, -60), R(2.0)], 'left_arm': [R(0, -60), R(1.6, -60), R(2.0)]}),
            ('idle', 4.0, 'loop', {'body': osc('rotation', 4, -4, 4, 0, 4, 2), 'head': osc('rotation', 4, -8, 8, .5, 4, 2),
                                   'right_arm': osc('rotation', 4, -5, 5, 0, 4, 0), 'left_arm': osc('rotation', 4, -5, 5, 1.5, 4, 0),
                                   'cap_tip': osc('rotation', 4, -10, 10, 1, 4, 0)}),
            ('walk', L, 'loop', mirror_tracks({   # 비틀대는 걸음
                'right_leg': [R(0, 20), R(L / 2, -20), R(L, 20)],
                'body': [R(0, 0, 0, 6), R(L / 2, 0, 0, -6), R(L, 0, 0, 6), T(0), T(L / 4, 0, -.8), T(L / 2), T(3 * L / 4, 0, -.8), T(L)],
                'head': [R(0, 10, 0, -8), R(L / 2, 10, 0, 8), R(L, 10, 0, -8)],
                'right_arm': [R(0, 5), R(L / 2, -5), R(L, 5)],
                'gown_hem': [R(0, 0, 0, -4), R(L / 2, 0, 0, 4), R(L, 0, 0, -4)],
                'cap_tip': [R(0, 0, 0, 15), R(L / 2, 0, 0, -15), R(L, 0, 0, 15)]}) | {'left_leg': [R(0, -20), R(L / 2, 20), R(L, -20)]}),
            ('attack', 1.6, 'once', mirror_tracks({   # 큰 손 휘두르기 (반원)
                'body': [Z(0), R(.6, -10, 30), R(.9, 15, -35), R(1.2, 12, -30), R(1.6)],
                'right_arm': [Z(0), R(.6, 50, 0, 60), R(.9, 0, 0, -50), R(1.2, 0, 0, -45), R(1.6)],
                'head': [Z(0), R(.6, -10), R(.9, 15), R(1.6)]})),
            ('death', 2.2, 'hold', {   # 그대로 앞으로 쓰러져 잠듦
                'root': [Z(0), R(.5, 5), R(1.6, -88), R(2.2, -88)],
                'head': [Z(0), R(1.6, -20, 0, 20)], 'cap_tip': [Z(0), R(1.6, 40)]})]
        return build_anims('animation.sleepwalker.', spec, G)
    return build, anims, PAL, SH, (1.6, 4.3, 3.5)

# =====================================================================
@mob
def nightmare_doll():
    PAL = dict(CLOTH=(220, 208, 196), DCLOTH=(160, 146, 134), DRESS=(110, 34, 60), DDRESS=(64, 18, 36), BUTTON=(24, 20, 30),
               STITCH=(150, 30, 40), HAIR=(40, 30, 46), WOOD=(110, 74, 50), STRING=(230, 230, 240), NEEDLE=(200, 206, 220))
    SH = dict(CLOTH=(50, 46, 60), DCLOTH=(30, 28, 40), DRESS=(220, 200, 120), DDRESS=(160, 130, 60), BUTTON=(110, 255, 220),
              STITCH=(110, 255, 220), HAIR=(240, 236, 226), WOOD=(220, 210, 190), STRING=(255, 120, 200), NEEDLE=(255, 210, 90))
    def face(img, r, rng):
        x1, y1, x2, y2 = r; w = x2 - x1
        for ex in (1, w - 3):   # 단추 눈
            for dx in range(2):
                for dy in range(2): px(img, r, ex + dx, 2 + dy, col(PAL['BUTTON']))
        for lx in range(1, w - 1):   # 꿰맨 입
            px(img, r, lx, 6, col(PAL['STITCH']))
            if lx % 2: px(img, r, lx, 5, col(PAL['STITCH'])); px(img, r, lx, 7, col(PAL['STITCH']))
    def build():
        cl = sol(PAL, 'CLOTH', .05); dr = stripes(PAL, 'DRESS', 'DDRESS', 3, 1); st = sol(PAL, 'STRING', .02)
        def arm(s):
            m, P, Rt = side(s)
            held = [C(f'{s}_needle', [5, 7, -6], [6, 8, -1], tipped(PAL, 'NEEDLE', 'STITCH', 1), m)]
            fore = Group(f'{s}_forearm', P([5.5, 12, 0]), [C(f'{s}_forearm', [4.5, 8, -1], [6.5, 12, 1], cl, m),
                                                           C(f'{s}_hand', [4.5, 7, -1], [6.5, 8, 1], sol(PAL, 'DCLOTH'), m), *held,
                                                           C(f'{s}_hand_string', [5, 8, 1], [6, 40, 2], st, m)], [10, 0, 0])
            return Group(f'{s}_arm', P([5.5, 16, 0]), [C(f'{s}_upper_arm', [4.5, 12, -1], [6.5, 16, 1], dr, m), fore], Rt([0, 0, 20]))
        def leg(s):
            m, P, Rt = side(s)
            return Group(f'{s}_leg', P([1.5, 9, 0]), [C(f'{s}_leg', [.5, 2, -1], [2.5, 9, 1], stripes(PAL, 'CLOTH', 'DDRESS', 2, 1, False), m),
                                                      C(f'{s}_shoe', [0, 0, -2], [3, 2, 1], sol(PAL, 'BUTTON'), m),
                                                      C(f'{s}_knee_string', [1, 6, 1], [2, 34, 2], st, m)])
        head = Group('head', [0, 17, 0], [
            C('head', [-4, 17, -4], [4, 25, 4], faces(cl, north=face)),
            C('hair', [-4.5, 21, -1], [4.5, 26, 4.5], ragged(sol(PAL, 'HAIR'), .5, 2)),
            C('right_pigtail', [4, 16, 0], [6, 23, 2], ragged(sol(PAL, 'HAIR'), .5, 1)),
            C('left_pigtail', [-6, 16, 0], [-4, 23, 2], ragged(sol(PAL, 'HAIR'), .5, 1)),
            C('head_string', [0, 25, 0], [1, 40, 1], st)])
        body = Group('body', [0, 9, 0], [
            C('torso', [-3, 12, -2], [3, 17, 2], dr),
            C('skirt', [-4, 8, -3], [4, 12, 3], ragged(dr, .4, 1)),
            C('collar', [-3.5, 16, -2.5], [3.5, 17, 2.5], sol(PAL, 'CLOTH')), head, arm('right'), arm('left')])
        control = Group('control', [0, 40, 0], [
            C('control_bar_x', [-8, 40, -1], [8, 41, 1], sol(PAL, 'WOOD')),
            C('control_bar_z', [-1, 40, -7], [1, 41, 7], sol(PAL, 'WOOD')),
            C('control_hook', [-.5, 41, -.5], [.5, 44, .5], sol(PAL, 'WOOD'))])
        root = Group('root', [0, 0, 0], [body, leg('right'), leg('left'), control]); shift(root, 4); return root
    def anims(G):
        L = 1.0
        dangle = lambda L, a: osc('rotation', L, -a, a, 0, 4, 2)
        spec = [
            ('spawn', 1.6, 'once', {'root': [T(0, 0, 30), T(1.0, 0, -2), T(1.3, 0, 1), T(1.6)],
                                    'body': [R(0, 0, 0, 40), R(1.0, 0, 0, -20), R(1.6)]}),
            ('idle', 3.0, 'loop', mirror_tracks({'body': dangle(3, 5), 'head': [R(0, 20, 0, 10), R(1.5, 20, 0, -10), R(3, 20, 0, 10)],
                                                 'right_arm': osc('rotation', 3, -10, 10, 0, 4, 0), 'right_leg': osc('rotation', 3, -8, 8, 1, 4, 0),
                                                 'control': dangle(3, 4)})),
            ('walk', L, 'loop', {   # 줄에 끌려가는 듯 깡총
                'root': [T(0), T(L / 2, 0, 3), T(L)], 'right_leg': [R(0, 30), R(L / 2, -30), R(L, 30)], 'left_leg': [R(0, -30), R(L / 2, 30), R(L, -30)],
                'right_arm': [R(0, -40), R(L / 2, 30), R(L, -40)], 'left_arm': [R(0, 30), R(L / 2, -40), R(L, 30)],
                'head': [R(0, 10, 0, 15), R(L / 2, 10, 0, -15), R(L, 10, 0, 15)]}),
            ('attack', 1.0, 'once', {   # 바늘 3연발: 팔을 번갈아 휙
                'right_arm': [Z(0), R(.15, -120), R(.3, 60), R(.65, -120), R(.8, 60), R(1.0)],
                'left_arm': [Z(0), R(.35, -120), R(.5, 60), R(1.0)],
                'head': [Z(0), R(.15, 0, 0, 25), R(.3, 0, 0, -25), R(.5, 0, 0, 25), R(.8, 0, 0, -25), R(1.0)]}),
            ('death', 1.6, 'hold', {   # 줄이 끊어진 듯 털썩
                'root': [T(0), T(.2, 0, 2), T(.7, 0, -4), T(1.6, 0, -4)], 'control': [T(0), T(.2, 0, 20), T(1.6, 0, 60)],
                'body': [Z(0), R(.7, 40, 0, 20), R(1.6, 60, 0, 20)], 'head': [Z(0), R(.7, 50, 0, 30)],
                'right_arm': [Z(0), R(.7, 30, 0, 60)], 'left_arm': [Z(0), R(.7, 30, 0, -70)],
                'right_leg': [Z(0), R(.7, -80)], 'left_leg': [Z(0), R(.7, -70, 0, 10)]})]
        return build_anims('animation.nightmare_doll.', spec, G)
    return build, anims, PAL, SH, (0.7, 2.0, 1.6)

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    names = sys.argv[1:] or list(MOBS)
    for i, n in enumerate(names):
        build, anims, PAL, SH, HB = MOBS[n]()
        export(n, build(), anims, 100 + i, O + n + '.bbmodel', HB)
        PAL.update(SH)
        export(n + '_shiny', build(), anims, 100 + i, O + n + '_shiny.bbmodel', HB)
