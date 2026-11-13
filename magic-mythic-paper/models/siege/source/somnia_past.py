"""몽마 솜니아 · 봉인 전 (somnia_past) — 100년 전 전성기. 날개 여섯 장의 눈이 전부 떠 있고,
머리 뒤에 초승달 관, 팔 넷(위 오른손에 달 지팡이, 아래 왼손에 꿈의 종), 찢기지 않은 밤하늘 예복."""
from common import *
from weapon_ik import apply as weapon_apply
import somnia as SO
import math, os

ID = 'somnia_past'
PAL = SO.PAL
BASE = dict(
    FUR=(244, 240, 252), DFUR=(176, 160, 214), CLOTH=(60, 32, 112), DCLOTH=(30, 14, 66), STAR=(248, 214, 110),
    MOON=(255, 244, 200), BONE=(240, 232, 214), DBONE=(168, 154, 132), SOCKET=(14, 8, 28), EYE=(255, 120, 210, 255),
    WING=(72, 50, 152), DWING=(28, 18, 74), WEDGE=(246, 214, 120), VEIN=(40, 26, 90), LID=(22, 12, 46),
    LASH=(255, 240, 190), SKIN=(98, 82, 124), DSKIN=(58, 46, 80), HORN=(216, 182, 106), DHORN=(132, 98, 52),
    ANT=(124, 104, 156), IRIS=(110, 232, 255), PUPIL=(10, 6, 24), GOLD=(238, 198, 92), DGOLD=(152, 110, 42),
    SCLERA=(250, 246, 255))
SHINY = dict(
    FUR=(64, 54, 70), DFUR=(30, 24, 36), CLOTH=(124, 22, 38), DCLOTH=(62, 8, 18), STAR=(255, 184, 96),
    MOON=(255, 130, 96), BONE=(228, 222, 216), DBONE=(150, 140, 134), SOCKET=(20, 4, 8), EYE=(255, 232, 96, 255),
    WING=(152, 30, 42), DWING=(60, 8, 16), WEDGE=(255, 172, 92), VEIN=(40, 4, 10), LID=(30, 4, 8),
    LASH=(255, 214, 156), SKIN=(72, 42, 52), DSKIN=(40, 20, 28), HORN=(44, 36, 48), DHORN=(16, 12, 18),
    ANT=(124, 42, 48), IRIS=(255, 232, 96), PUPIL=(40, 0, 6), GOLD=(212, 216, 228), DGOLD=(122, 128, 142),
    SCLERA=(255, 236, 226))
PAL.clear(); PAL.update(BASE)
HITBOX = (3.2, 9.2, 6.5)
FLOAT = 12
def apply_palette(p): PAL.update(p)

# ---------------- 페인터 ----------------
def wing_paint(box, kind):
    """날개 판(두께 1): 실루엣 밖은 투명. 뜬 눈 무늬(홍채·세로 동공·속눈썹) + 날개맥 + 금빛 가장자리."""
    (X0, Y0, Z0), (X1, Y1, Z1) = box
    ax0, ax1 = min(abs(X0), abs(X1)), max(abs(X0), abs(X1))
    def inside(t, v):
        if kind == 'fore':
            top = 0.06 + 0.58 * (1 - t) ** 2.2
            bot = 0.98 - 0.32 * t ** 1.4 + 0.035 * math.sin(t * 26)
            return top <= v <= bot and t <= 0.985 - 0.22 * max(0, v - .5)
        top = 0.04 + 0.08 * t
        bot = 0.62 + 0.30 * (1 - t) ** 0.6 + 0.04 * math.sin(t * 22)
        return top <= v <= bot and ((t - .05) / .95) ** 2 + ((v - .45) / .58) ** 2 <= 1
    eye_t, eye_v, eye_r = (0.57, 0.52, 0.24) if kind == 'fore' else (0.50, 0.42, 0.25)
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                if face not in ('north', 'south'):
                    img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                x = (X1 - lx - .5) if face == 'north' else (X0 + lx + .5)
                y = Y1 - ly - .5
                t = (abs(x) - ax0) / (ax1 - ax0); v = (Y1 - y) / (Y1 - Y0)
                if not inside(t, v):
                    img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                c = lerp(PAL['WING'], PAL['DWING'], min(1, v * .7 + (1 - t) * .35))
                edge = not (inside(min(1, t + .04), v) and inside(t, min(1, v + .045)) and inside(t, max(0, v - .045)))
                if edge: c = PAL['WEDGE']
                ang = math.atan2(v - (0.75 if kind == 'fore' else 0.2), t + 0.05)
                if not edge and abs((ang * 8) % 1 - .5) < .06: c = PAL['VEIN']
                # 뜬 눈: 아몬드 모양 흰자 + 홍채 + 세로 동공
                dt, dv = (t - eye_t) / eye_r, (v - eye_v) / (eye_r * 0.9)
                lim = 0.62 * (1 - dt * dt) if abs(dt) < 1 else -1
                if abs(dt) < 1 and abs(dv) < lim + .16:
                    if abs(dv) > lim: c = PAL['LID']
                    else:
                        rr = math.hypot(dt * 1.15, dv)
                        if rr < .52:
                            c = lerp(PAL['IRIS'], PAL['WING'], max(0, rr - .3) * 2.2)
                            if abs(dt) < .09 and abs(dv) < .44: c = PAL['PUPIL']
                            if rr > .46: c = PAL['LID']
                        else: c = PAL['SCLERA']
                elif abs(dt) < 1.25 and lim + .16 <= abs(dv) < lim + .16 + .34 and abs((dt * 4.5) % 1 - .5) < .13 and not edge:
                    c = PAL['LASH']
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.04, .04))))
    return p

def crescent(box):
    """머리 뒤 초승달 판: 뿔처럼 위로 솟은 달. 달 밖은 투명."""
    (X0, Y0, Z0), (X1, Y1, Z1) = box
    cx, cy = 0, (Y0 + Y1) / 2; Ro = (X1 - X0) / 2
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                if face not in ('north', 'south'):
                    img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                x = (X1 - lx - .5) if face == 'north' else (X0 + lx + .5); y = Y1 - ly - .5
                d = math.hypot(x - cx, y - cy); d2 = math.hypot(x - cx, y - (cy + Ro * .42))
                if d > Ro or d2 < Ro * .86:
                    img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                c = PAL['MOON'] if d < Ro - 1.6 and d2 > Ro * .86 + 1.4 else PAL['GOLD']
                if c == PAL['MOON'] and rng.random() < .1: c = PAL['STAR']
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.04, .04))))
    return p

def third_eye(img, r, rng):
    SO.skull_face(img, r, rng)
    w = r[2] - r[0]
    for dx in (-1, 0):
        for dy in (0, 1, 2): px(img, r, w // 2 + dx, dy, col(PAL['SOCKET']))
    px(img, r, w // 2 - 1, 1, col(PAL['EYE'])); px(img, r, w // 2, 1, col(PAL['EYE']))

def robe_p(hem=0, ragg=0):
    """밤하늘 예복: SO.quilt + 아래 금실 단."""
    q = SO.quilt(ragg)
    def p(img, face, r, rng):
        q(img, face, r, rng)
        if hem and face not in ('up', 'down'):
            x1, y1, x2, y2 = r
            for x in range(x1, x2):
                for k in range(hem):
                    if img.getpixel((x, y2 - 1 - k))[3]:
                        img.putpixel((x, y2 - 1 - k), col(PAL['GOLD'] if (k == hem - 1 or (x - x1) % 4 == 0) else PAL['DGOLD'], SHADE[face]))
    return p

# ---------------- 모델 ----------------
def build():
    bone = sol(PAL, 'BONE', .06); dbone = sol(PAL, 'DBONE'); skin = sol(PAL, 'SKIN')
    claw = tipped(PAL, 'DSKIN', 'BONE', 3); horn = sol(PAL, 'HORN', .1); dhorn = sol(PAL, 'DHORN', .1)
    gold = sol(PAL, 'GOLD', .1); dgold = sol(PAL, 'DGOLD', .1); fur = SO.fur; glow = sol(PAL, 'EYE', .02)

    def ram_horn(s):
        m, P, Rt = side(s)
        return Group(f'{s}_horn', P([7, 96, -3]), [
            C(f'{s}_horn1', [7, 94, -6], [13, 101, 0], horn, m),
            C(f'{s}_horn2', [9, 92, 0], [15, 101, 7], horn, m),
            C(f'{s}_horn3', [10, 83, 4], [16, 93, 10], horn, m),
            C(f'{s}_horn4', [11, 79, -4], [16, 85, 5], horn, m),
            C(f'{s}_horn5', [11.5, 82, -9], [15.5, 88, -4], dhorn, m),
            C(f'{s}_horn6', [12, 88, -11], [15, 92, -7], dhorn, m),
            C(f'{s}_horn_ring', [8.5, 91.5, -.5], [15.5, 101.5, 1.5], gold, m),
            # 위로 솟은 두 번째 뿔
            Group(f'{s}_spire', P([4, 101, -2]), [
                C(f'{s}_spire1', [3, 100, -4], [7, 110, 0], horn, m),
                C(f'{s}_spire2', [3.5, 110, -3.5], [6.5, 119, -.5], horn, m),
                C(f'{s}_spire3', [4, 119, -3], [6, 126, -1], tipped_up(PAL, 'HORN', 'DHORN', 3), m)], Rt([8, 0, -20]))])

    def antenna(s):
        m, P, Rt = side(s)
        tip = Group(f'{s}_antenna2', P([3, 112, -6]), [
            C(f'{s}_antenna_stem2', [2.5, 112, -6.5], [3.5, 128, -5.5], sol(PAL, 'ANT'), m),
            C(f'{s}_antenna_plume2', [0, 113, -7], [7, 127, -6], SO.feather, m)], Rt([-20, 0, -10]))
        return Group(f'{s}_antenna', P([3, 99, -6]), [
            C(f'{s}_antenna_stem', [2.5, 99, -6.5], [3.5, 113, -5.5], sol(PAL, 'ANT'), m),
            C(f'{s}_antenna_plume', [0, 101, -7], [7, 113, -6], SO.feather, m), tip], Rt([-24, 0, -38]))

    def staff():
        return Group('staff', [11.5, 41, -1.5], [
            C('staff_shaft', [11, 6, -2], [12, 112, -1], dgold),
            C('staff_grip', [10.5, 36, -2.5], [12.5, 46, -.5], sol(PAL, 'CLOTH')),
            C('staff_foot', [10.5, 4, -2.5], [12.5, 7, -.5], gold),
            C('staff_collar', [10, 108, -3], [13, 112, 0], gold),
            C('staff_moon_base', [8, 112, -2], [15, 114, -1], gold),
            C('staff_moon_r', [15, 113, -2], [17, 122, -1], gold), C('staff_moon_l', [6, 113, -2], [8, 122, -1], gold),
            C('staff_moon_tip_r', [14, 122, -2], [16, 126, -1], sol(PAL, 'MOON')), C('staff_moon_tip_l', [7, 122, -2], [9, 126, -1], sol(PAL, 'MOON')),
            C('staff_star', [10.5, 116, -2.5], [12.5, 120, -.5], glow),
            C('staff_bell_cord', [13.5, 104, -1.5], [13.5, 108, -1.5 + 1], dgold),
            C('staff_bell', [12.5, 100, -3], [15.5, 104, 0], gold),
            C('staff_ribbon', [9, 96, -1.5], [10, 108, -1.5], ragged(sol(PAL, 'CLOTH'), .5, 2))], [-42, 0, -16])

    def arm(s):
        m, P, Rt = side(s)
        fingers = [C(f'{s}_finger{i+1}', [x, 28, -2], [x + 1, 40, -1], claw, m) for i, x in enumerate((9, 10.5, 12, 13.5))]
        hk = [C(f'{s}_palm', [9, 39, -3], [14, 44, 1], skin, m), *fingers, C(f'{s}_thumb', [8, 34, -3], [9, 40, -2], claw, m)]
        if s == 'right':   # 지팡이를 쥔 손: 손가락이 자루를 감는다 (자루와 같은 기울기)
            grip = Group('right_grip', [11.5, 41, -1.5], [C(f'right_finger{i+1}', [10, 38 + i * 1.5, -4], [13, 39 + i * 1.5, -3], claw) for i in range(4)]
                         + [C('right_finger_side', [13, 38, -4], [14, 43, 0], claw), C('right_thumb', [10, 40, 0], [13, 41, 1], claw)], [-42, 0, -16])
            hk = [C('right_palm', [9, 39, -3], [14, 44, 1], skin), grip, staff()]
        hand = Group(f'{s}_hand', P([11.5, 42, -1]), hk)
        fore = Group(f'{s}_forearm', P([11.5, 60, -1]), [
            C(f'{s}_forearm', [10, 43, -2.5], [13, 61, 0.5], skin, m),
            C(f'{s}_bracer', [9.5, 46, -3], [13.5, 54, 1], gold, m),
            C(f'{s}_wrist_fluff', [9, 43, -3.5], [14, 47, 1.5], fur(.5), m), hand], [30, 0, 0])
        return Group(f'{s}_arm', P([11.5, 78, -1]), [
            C(f'{s}_upper_arm', [10, 59, -3], [13, 79, 1], skin, m),
            C(f'{s}_shoulder_fluff', [8, 72, -5], [16, 81, 3], fur(.5), m),
            C(f'{s}_shoulder_plate', [9, 80, -4], [16, 82, 2], gold, m),
            C(f'{s}_shoulder_moon', [14, 82, -2], [16, 87, 0], sol(PAL, 'MOON'), m), fore], Rt([10, 0, 18]))

    def arm2(s):   # 아래 팔 (가슴 앞으로 모음)
        m, P, Rt = side(s)
        fingers = [C(f'{s}_finger2_{i+1}', [x, 32, -5], [x + 1, 40, -4], claw, m) for i, x in enumerate((8, 9.5, 11))]
        hk = [C(f'{s}_palm2', [8, 40, -6], [12, 44, -3], skin, m), *fingers]
        if s == 'left':
            hk.append(Group('bell', [-10, 40, -4.5], [
                C('bell_handle', [-10.5, 34, -5], [-9.5, 40, -4], dgold),
                C('bell_crown', [-11.5, 32, -6], [-8.5, 34, -3], gold),
                C('bell_body', [-12.5, 27, -7], [-7.5, 32, -2], gold),
                C('bell_lip', [-13, 26, -7.5], [-7, 27, -1.5], dgold),
                C('bell_clapper', [-10.5, 24, -5], [-9.5, 27, -4], sol(PAL, 'MOON'))], [-80, 0, 0]))
        hand = Group(f'{s}_hand2', P([10, 44, -4.5]), hk)
        fore = Group(f'{s}_forearm2', P([10, 56, -4.5]), [
            C(f'{s}_forearm2', [8.5, 44, -6], [11.5, 57, -3], skin, m),
            C(f'{s}_bracelet2', [8, 47, -6.5], [12, 50, -2.5], gold, m), hand], [58, 0, 0])
        return Group(f'{s}_arm2', P([10, 69, -4.5]), [
            C(f'{s}_upper_arm2', [8.5, 55, -6], [11.5, 70, -3], skin, m),
            C(f'{s}_shoulder_fluff2', [7.5, 66, -7], [12.5, 71, -2], fur(.5), m), fore], Rt([28, 0, 6]))

    def wings(s):
        m, P, Rt = side(s)
        def plate(name, lo, hi, kind, origin, rot, extra=()):
            b = (lo, hi)
            if m: b = ([-hi[0], lo[1], lo[2]], [-lo[0], hi[1], hi[2]])
            return Group(f'{s}_{name}', P(origin), [Cube(f'{s}_{name}', b[0], b[1], wing_paint(b, kind)), *extra], Rt(rot))
        return [
            plate('wing', [8, 80, 6], [72, 134, 7], 'fore', [8, 88, 6.5], [0, -18, 14],
                  [C(f'{s}_wing_root', [6, 82, 4], [11, 94, 8], fur(.3), m)]),
            plate('midwing', [8, 48, 7], [64, 90, 8], 'hind', [8, 80, 7.5], [0, -24, -2]),
            plate('hindwing', [8, 16, 8], [44, 56, 9], 'hind', [8, 62, 8.5], [0, -30, -12])]

    mb = ([-18, 96, 4], [18, 132, 5])
    head = Group('head', [0, 88, -4], [
        C('skull', [-7, 86, -12], [7, 100, 2], faces(bone, north=third_eye)),
        C('skull_crown', [-5, 99, -10], [5, 102, 0], bone),
        C('snout', [-5, 84, -18], [5, 94, -12], faces(bone, north=SO.snout_face)),
        C('snout_bridge', [-3, 92, -17], [3, 96, -12], dbone),
        C('jaw', [-4, 81, -17], [4, 84, -10], dbone),
        C('diadem', [-7.5, 97, -12.5], [7.5, 99, -11.5], gold),
        C('diadem_gem', [-1, 99, -13], [1, 102, -12], glow),
        C('diadem_drop_r', [6, 92, -12.5], [7, 97, -12.5 + 1], gold), C('diadem_drop_l', [-7, 92, -12.5], [-6, 97, -11.5], gold),
        Group('moon', [0, 104, 4.5], [Cube('moon', mb[0], mb[1], crescent(mb))]),
        ram_horn('right'), ram_horn('left'), antenna('right'), antenna('left')])

    drape3 = Group('drape3', [0, 22, 0], [
        C('drape3', [-17, 2, -13], [17, 22, 13], robe_p(2, 0)),
        C('drape3_fold_r', [17, 4, -7], [19, 22, 7], robe_p(2)),
        C('drape3_fold_l', [-19, 4, -7], [-17, 22, 7], robe_p(2)),
        C('drape3_train', [-10, 0, 13], [10, 16, 16], robe_p(2, 1))])
    drape2 = Group('drape2', [0, 40, 0], [
        C('drape2', [-15, 22, -11], [15, 40, 11], robe_p(1)),
        C('drape2_band', [-15.5, 37, -11.5], [15.5, 40, 11.5], stripes(PAL, 'DGOLD', 'GOLD', 4, 2)), drape3])
    drape1 = Group('drape1', [0, 62, 0], [
        C('drape1', [-13, 40, -10], [13, 62, 10], robe_p(0)),
        C('belt', [-13.5, 57, -10.5], [13.5, 62, 10.5], stripes(PAL, 'DGOLD', 'GOLD', 5, 1)),
        C('belt_moon', [-3, 56, -11.5], [3, 63, -10.5], sol(PAL, 'MOON', .03)),
        C('tabard', [-4, 14, -14], [4, 56, -13 + 0], robe_p(3, 1)),
        C('tabard_star', [-1.5, 40, -14.5], [1.5, 43, -14], glow),
        C('side_ribbon_r', [13, 30, -2], [14, 58, 2], ragged(sol(PAL, 'STAR', .05), .5, 2)),
        C('side_ribbon_l', [-14, 30, -2], [-13, 58, 2], ragged(sol(PAL, 'STAR', .05), .5, 2)), drape2])

    cape = Group('cape', [0, 84, 9], [
        C('cape', [-13, 30, 9], [13, 84, 11], robe_p(2, 1)),
        C('cape_collar', [-12, 84, 6], [12, 92, 11], fur(.5))], [-8, 0, 0])

    body = Group('body', [0, 70, 0], [
        C('thorax', [-10, 62, -8], [10, 84, 8], fur(.1)),
        C('chest_plate', [-6, 68, -9.5], [6, 80, -8], faces(gold, north=lambda i, r, g: [px(i, r, x, y, col(PAL['EYE'])) for x, y in ((5, 4), (6, 4), (5, 5), (6, 5), (4, 5), (7, 5), (5, 6), (6, 6))])),
        C('ruff', [-15, 78, -11], [15, 86, 10], fur(.55)),
        C('ruff_back', [-11, 84, 0], [11, 90, 10], fur(.55)),
        C('ruff_front', [-9, 74, -12], [9, 80, -10], fur(.6)),
        drape1, cape, head, arm('right'), arm('left'), arm2('right'), arm2('left'), *wings('right'), *wings('left')])
    root = Group('root', [0, 0, 0], [body])
    shift(root, FLOAT)
    return root

# ---------------- 애니메이션 ----------------
def anims(G):
    L = 1.6
    def wf(L, a, b, ph=0): return osc('rotation', L, a, b, ph, 4, 1)
    def W(keys_fore, mid=.85, hind=.7, dt=.05):
        """세 쌍 날개를 한 번에: fore 키(t,x,y,z)를 비율로 줄여 mid/hind에."""
        return {'right_wing': [R(*k) for k in keys_fore],
                'right_midwing': [R(k[0] + (dt if 0 < i < len(keys_fore) - 1 else 0), *[v * mid for v in k[1:]]) for i, k in enumerate(keys_fore)],
                'right_hindwing': [R(k[0] + (2 * dt if 0 < i < len(keys_fore) - 1 else 0), *[v * hind for v in k[1:]]) for i, k in enumerate(keys_fore)]}
    idle = mirror_tracks({
        'root': osc('position', 4, -1.5, 1.5, 0, 4, 1), 'body': osc('rotation', 4, -2, 2, 1, 4, 2),
        'right_wing': wf(4, -8, 8), 'right_midwing': wf(4, -7, 7, .5), 'right_hindwing': wf(4, -6, 6, 1),
        'right_antenna': osc('rotation', 4, -6, 6, .3, 4, 0), 'right_antenna2': osc('rotation', 4, -10, 10, 1, 4, 0),
        'right_arm': osc('rotation', 4, -3, 3, .5, 4, 0), 'right_arm2': osc('rotation', 4, -3, 3, 1.5, 4, 0),
        'head': osc('rotation', 4, -4, 4, .8, 4, 0), 'moon': osc('rotation', 4, -3, 3, 0, 4, 2),
        'bell': osc('rotation', 4, -8, 8, 0, 4, 2), 'cape': osc('rotation', 4, 0, 5, 0, 4, 0),
        'drape1': osc('rotation', 4, -2, 2, 0, 4, 2), 'drape2': osc('rotation', 4, -4, 4, 1, 4, 2),
        'drape3': osc('rotation', 4, -6, 6, 2, 4, 2)})
    walk = mirror_tracks({
        'root': osc('position', L, -2, 2, 0, 4, 1), 'body': [R(0, 12), R(L, 12)], 'head': [R(0, -9), R(L, -9)],
        **W([(0, 0, -30, 10), (L / 2, 0, 20, 4), (L, 0, -30, 10)], dt=0),
        'right_arm': [R(0, -16, 0, -4), R(L, -16, 0, -4)], 'right_antenna': [R(0, -20), R(L, -20)],
        'cape': [R(0, 16), R(L / 2, 22), R(L, 16)],
        'drape1': [R(0, -9), R(L / 2, -13), R(L, -9)], 'drape2': [R(0, -9), R(L / 2, -5), R(L, -9)],
        'drape3': [R(0, -11, 0, 5), R(L / 2, -15, 0, -5), R(L, -11, 0, 5)]})
    spawn = mirror_tracks({   # 여섯 날개로 몸을 감싼 고치 → 위에서부터 차례로 눈을 뜨듯 펼침
        'root': [T(0, 0, 30), T(1.2, 0, 0), S(0, .8, .8, .8), S(1.2, 1, 1, 1)],
        'right_wing': [R(0, 0, 95, -20), R(1.4, 0, 95, -20), R(1.9, 0, -25, 25), R(3.0)],
        'right_midwing': [R(0, 0, 100, 0), R(1.7, 0, 100, 0), R(2.2, 0, -22, 12), R(3.0)],
        'right_hindwing': [R(0, 0, 100, 12), R(2.0, 0, 100, 12), R(2.5, 0, -20), R(3.0)],
        'head': [R(0, 40), R(1.5, 40), R(2.1, -20), R(3.0)],
        'right_arm': [R(0, 40, 0, -20), R(1.5, 40, 0, -20), R(2.1, 0, 0, 30), R(3.0)],
        'right_antenna': [R(0, 40), R(1.6, 40), R(2.2, -15), R(3.0)],
        'moon': [S(0, .2, .2, .2), S(2.0, .2, .2, .2), S(2.6, 1.15, 1.15, 1.15), S(3.0, 1, 1, 1)]})
    attack = {   # 지팡이 내려치기 (오른손)
        'root': [T(0), T(.4, 0, 2, 3), T(.58, 0, -1, -8), T(.9, 0, -1, -8), T(1.2)],
        'body': [Z(0), R(.4, -12, 12), R(.58, 22, -10), R(.9, 18, -8), R(1.2)],
        'right_arm': [Z(0), R(.4, 130, 0, 20), R(.58, 50, 0, -6), R(.9, 46, 0, -6), R(1.2)],
        'right_forearm': [Z(0), R(.4, -20), R(.58, 10), R(1.2)],
        'staff': [Z(0), R(.4, -30), R(.58, 62), R(.9, 58), R(1.2)],
        'right_wing': [Z(0), R(.4, 0, -25), R(.58, 0, 15), R(1.2)], 'left_wing': [Z(0), R(.4, 0, 25), R(.58, 0, -15), R(1.2)],
        'head': [Z(0), R(.4, -10, 10), R(.58, 12, -8), R(1.2)]}
    death = mirror_tracks({   # 봉인: 눈 달린 날개가 하나씩 접히고 달이 떨어지며 가라앉음
        'root': [T(0), T(.6, 0, 4), T(2.6, 0, -FLOAT - 2), T(3.6, 0, -FLOAT - 4), S(0), S(2.6, 1.08, .88, 1.08), S(3.6, 1.15, .65, 1.15)],
        'right_wing': [Z(0), R(.6, 0, -40, 30), R(1.6, 0, 88, -10), R(3.6, 0, 92, -30)],
        'right_midwing': [Z(0), R(.9, 0, -34, 20), R(2.0, 0, 90, 0), R(3.6, 0, 94, -10)],
        'right_hindwing': [Z(0), R(1.2, 0, -30), R(2.4, 0, 92, 10), R(3.6, 0, 95, 10)],
        'head': [Z(0), R(.6, -40), R(2.0, 30), R(3.6, 55)],
        'moon': [T(0), T(1.8), T(2.6, 0, -30, 6), T(3.6, 0, -34, 8), R(0), R(1.8), R(2.6, 60, 0, 30), R(3.6, 80, 0, 36)],
        'right_arm': [Z(0), R(.6, -30, 0, 50), R(2.4, 10, 0, 10), R(3.6, 0, 0, 5)],
        'staff': [Z(0), R(1.6), R(2.6, 70, 0, 20), R(3.6, 84, 0, 24)],
        'right_antenna': [Z(0), R(1.5, 40), R(3.6, 70)],
        'drape1': [Z(0), R(2.6, 10), R(3.6, 15)], 'drape3': [Z(0), R(2.6, -30), R(3.6, -40)]})
    lullaby = mirror_tracks({   # 자장가 (루프 2.4): 종을 흔들며 좌우로 흔들림
        'root': [T(0, -3, 0), T(1.2, 3, 0), T(2.4, -3, 0)],
        'body': [R(0, 0, 0, 7), R(1.2, 0, 0, -7), R(2.4, 0, 0, 7)],
        'head': [R(0, 10, 0, -10), R(1.2, 10, 0, 10), R(2.4, 10, 0, -10)],
        'right_wing': wf(2.4, -12, 12), 'right_midwing': wf(2.4, -11, 11, .5), 'right_hindwing': wf(2.4, -10, 10, 1)})
    lullaby['left_arm2'] = [R(0, 30, 0, -10), R(.6, 44, 0, -4), R(1.2, 30, 0, -10), R(1.8, 44, 0, -4), R(2.4, 30, 0, -10)]
    lullaby['bell'] = [R(0, 0, 0, -30), R(.3, 0, 0, 30), R(.6, 0, 0, -30), R(.9, 0, 0, 30), R(1.2, 0, 0, -30), R(1.5, 0, 0, 30), R(1.8, 0, 0, -30), R(2.1, 0, 0, 30), R(2.4, 0, 0, -30)]
    wing_gust = mirror_tracks({   # 날개 바람: 여섯 장을 뒤로 젖혔다가 앞으로
        'root': [T(0), T(.5, 0, 4, 4), T(.8, 0, 0, -3), T(1.4)],
        'body': [Z(0), R(.5, -15), R(.8, 15), R(1.4)],
        **W([(0, 0, 0, 0), (.5, 0, -60, 20), (.8, 0, 55, -5), (1.1, 0, 45, -5), (1.4, 0, 0, 0)]),
        'right_arm2': [Z(0), R(.5, -30, 0, 40), R(.8, 30, 0, 10), R(1.4)],
        'head': [Z(0), R(.5, -20), R(.8, 15), R(1.4)]})
    gaze = mirror_tracks({   # 만 개의 시선: 떠올라 여섯 날개를 정면으로 활짝 펴고 버팀 (눈 무늬가 전부 앞을 봄)
        'root': [T(0), T(.7, 0, 14), T(2.4, 0, 14), T(3.0)],
        'body': [Z(0), R(.7, -12), R(2.4, -12), R(3.0)],
        'head': [Z(0), R(.7, 18), R(2.4, 18), R(3.0)],
        'right_wing': [Z(0), R(.7, 0, 18, 30), R(1.0, 0, 22, 34), R(1.3, 0, 18, 30), R(2.4, 0, 18, 30), R(3.0)],
        'right_midwing': [Z(0), R(.8, 0, 24, 6), R(2.4, 0, 24, 6), R(3.0)],
        'right_hindwing': [Z(0), R(.9, 0, 30, -22), R(2.4, 0, 30, -22), R(3.0)],
        'right_arm': [Z(0), R(.7, 30, 0, 50), R(2.4, 30, 0, 50), R(3.0)],
        'staff': [Z(0), R(.7, -20, 0, -30), R(2.4, -20, 0, -30), R(3.0)],
        'right_arm2': [Z(0), R(.7, -20, 0, 50), R(2.4, -20, 0, 50), R(3.0)],
        'moon': [S(0), S(.7, 1.2, 1.2, 1.2), S(2.4, 1.2, 1.2, 1.2), S(3.0)]})
    staff_cast = {   # 지팡이 시전: 달 지팡이를 높이 들어 올림
        'root': [T(0), T(.6, 0, 6), T(1.6, 0, 6), T(2.0)],
        'body': [Z(0), R(.6, -10), R(1.6, -10), R(2.0)], 'head': [Z(0), R(.6, -24), R(1.6, -24), R(2.0)],
        'right_arm': [Z(0), R(.6, 150, 0, 14), R(1.1, 156, 0, 10), R(1.6, 150, 0, 14), R(2.0)],
        'right_forearm': [Z(0), R(.6, -25), R(1.6, -25), R(2.0)],
        'staff': [Z(0), R(.6, -84, 0, 6), R(1.6, -84, 0, 6), R(2.0)],
        'left_arm': [Z(0), R(.6, 20, 0, -50), R(1.6, 20, 0, -50), R(2.0)],
        'right_wing': [Z(0), R(.6, 0, -10, 26), R(1.6, 0, -10, 26), R(2.0)], 'left_wing': [Z(0), R(.6, 0, 10, -26), R(1.6, 0, 10, -26), R(2.0)],
        'right_midwing': [Z(0), R(.6, 0, -8, 12), R(1.6, 0, -8, 12), R(2.0)], 'left_midwing': [Z(0), R(.6, 0, 8, -12), R(1.6, 0, 8, -12), R(2.0)]}
    press = mirror_tracks({   # 짓누르기: 위 두 팔을 머리 위로 → 앞 바닥으로
        'root': [T(0), T(.6, 0, 10, 4), T(.85, 0, -8, -8), T(1.3, 0, -8, -8), T(1.8)],
        'body': [Z(0), R(.6, -20), R(.85, 30), R(1.3, 28), R(1.8)],
        'right_arm': [Z(0), R(.6, 170, 0, 20), R(.85, 70, 0, 10), R(1.3, 65, 0, 10), R(1.8)],
        'right_forearm': [Z(0), R(.6, -10), R(.85, 10), R(1.8)],
        **W([(0, 0, 0, 0), (.6, 0, -30, 20), (.85, 0, 30, 0), (1.8, 0, 0, 0)]),
        'head': [Z(0), R(.6, -15), R(.85, 20), R(1.8)]})
    groggy = mirror_tracks({
        'root': [T(0, 0, -FLOAT + 1), T(1.5, 0, -FLOAT), T(3.0, 0, -FLOAT + 1)],
        'body': [R(0, 20), R(1.5, 24), R(3.0, 20)], 'head': [R(0, 35, 0, 10), R(1.5, 40, 0, 12), R(3.0, 35, 0, 10)],
        'right_wing': [R(0, 0, -30, -45), R(1.5, 0, -32, -50), R(3.0, 0, -30, -45)],
        'right_midwing': [R(0, 0, -28, -30), R(3.0, 0, -28, -30)], 'right_hindwing': [R(0, 0, -25, -16), R(3.0, 0, -25, -16)],
        'right_arm': [R(0, -10, 0, -12), R(3.0, -10, 0, -12)], 'right_antenna': [R(0, 45), R(1.5, 50), R(3.0, 45)],
        'moon': [R(0, 0, 0, 24), R(3.0, 0, 0, 24)], 'drape3': [R(0, -25), R(3.0, -25)]})
    enrage = mirror_tracks({
        'root': [T(0), T(.6, 0, -6), T(1.1, 0, 16), T(2.2, 0, 16), T(2.8)],
        'body': [Z(0), R(.6, 30), R(1.1, -24), R(2.2, -20), R(2.8)],
        'head': [Z(0), R(.6, 40), R(1.1, -50), R(1.3, -44), R(1.5, -50), R(2.2, -46), R(2.8)],
        **W([(0, 0, 0, 0), (.6, 0, 80, -10), (1.1, 0, -40, 40), (1.3, 0, -30, 44), (1.5, 0, -40, 40), (2.2, 0, -38, 40), (2.8, 0, 0, 0)], dt=0),
        'right_arm': [Z(0), R(.6, 50, 0, -40), R(1.1, -20, 0, 100), R(2.2, -20, 0, 95), R(2.8)],
        'right_arm2': [Z(0), R(.6, 30, 0, -10), R(1.1, -30, 0, 70), R(2.2, -30, 0, 66), R(2.8)],
        'moon': [S(0), S(1.1, 1.35, 1.35, 1.35), S(2.2, 1.3, 1.3, 1.3), S(2.8)],
        'right_antenna': [Z(0), R(1.1, -35, 0, -20), R(2.2, -35, 0, -20), R(2.8)]})
    # ---- 공성전 짤패턴 모션 (VFX 팩 Siege_Somnia 와 짝) ----
    orb_summon = mirror_tracks({   # 1 꿈방울: 아래 두 손을 모았다가 방울을 불어 내보냄 (0.8초에 방울 소환)
        'body': [Z(0), R(.5, 10), R(.8, -8), R(1.6)], 'head': [Z(0), R(.5, 14), R(.8, -10), R(1.6)],
        'right_arm2': [Z(0), R(.5, 30, 0, -14), R(.8, 60, 0, 34), R(1.2, 55, 0, 36), R(1.6)],
        'right_forearm2': [Z(0), R(.5, 20), R(.8, -30), R(1.6)],
        'right_wing': [Z(0), R(.5, 0, 14, 0), R(.8, 0, -16, 10), R(1.6)], 'right_midwing': [Z(0), R(.55, 0, 12, 0), R(.85, 0, -14, 6), R(1.6)]})
    gaze_sweep = mirror_tracks({   # 2 돌아가는 시선: 떠올라 날개 눈을 앞으로 펴고, 1.0초부터 5초 동안 몸째로 돈다 (초당 40°)
        'root': [T(0), T(.8, 0, 14), T(5.8, 0, 14), T(6.2), R(0, 0, 0, 0, 'linear'), R(1.0, 0, 0, 0, 'linear'), R(6.0, 0, 200, 0, 'linear'), R(6.2, 0, 360, 0, 'linear')],
        'body': [Z(0), R(.8, -12), R(5.8, -12), R(6.2)], 'head': [Z(0), R(.8, 18), R(5.8, 18), R(6.2)],
        'right_wing': [Z(0), R(.8, 0, 18, 30), R(5.8, 0, 18, 30), R(6.2)], 'right_midwing': [Z(0), R(.9, 0, 24, 6), R(5.8, 0, 24, 6), R(6.2)],
        'right_hindwing': [Z(0), R(1.0, 0, 30, -22), R(5.8, 0, 30, -22), R(6.2)],
        'right_arm': [Z(0), R(.8, 30, 0, 50), R(5.8, 30, 0, 50), R(6.2)], 'right_arm2': [Z(0), R(.8, -20, 0, 50), R(5.8, -20, 0, 50), R(6.2)],
        'moon': [S(0), S(.8, 1.2, 1.2, 1.2), S(5.8, 1.2, 1.2, 1.2), S(6.2)]})
    gaze_sweep['staff'] = [Z(0), R(.8, -20, 0, -30), R(5.8, -20, 0, -30), R(6.2)]
    mo = [Z(0), R(.6, 150, 0, 14)] + [R(round(.6 + k * .2, 2), 150 + 9 * math.cos(k * math.pi / 2), 0, 14 + 9 * math.sin(k * math.pi / 2)) for k in range(1, 28)] + [R(6.2, 150, 0, 14), Z(6.6)]
    moon_orbit = {   # 3 달의 궤도: 달 지팡이를 높이 들고 6초 동안 원을 그린다
        'root': [T(0), T(.6, 0, 6), T(6.2, 0, 6), T(6.6)], 'body': [Z(0), R(.6, -10), R(6.2, -10), Z(6.6)], 'head': [Z(0), R(.6, -24), R(6.2, -24), Z(6.6)],
        'right_arm': mo, 'right_forearm': [Z(0), R(.6, -25), R(6.2, -25), Z(6.6)], 'staff': [Z(0), R(.6, -84, 0, 6), R(6.2, -84, 0, 6), Z(6.6)],
        'left_arm': [Z(0), R(.6, 20, 0, -50), R(6.2, 20, 0, -50), Z(6.6)], 'moon': [S(0), S(.6, 1.3, 1.3, 1.3), S(6.2, 1.3, 1.3, 1.3), S(6.6)],
        'right_wing': [Z(0), R(.6, 0, -10, 26), R(6.2, 0, -10, 26), Z(6.6)], 'left_wing': [Z(0), R(.6, 0, 10, -26), R(6.2, 0, 10, -26), Z(6.6)]}
    lullaby_wave = mirror_tracks({   # 4 자장가 물결: 종을 크게 한 번 친다 (0.7초에 물결)
        'body': [Z(0), R(.5, -8), R(.7, 10), R(2.9)], 'head': [Z(0), R(.5, -12), R(.7, 10), R(2.9)],
        'right_wing': [Z(0), R(.5, 0, -14, 10), R(.75, 0, 16, 0), R(2.9)], 'right_midwing': [Z(0), R(.55, 0, -12, 6), R(.8, 0, 14, 0), R(2.9)]})
    lullaby_wave['left_arm2'] = [Z(0), R(.5, 70, 0, -20), R(.7, 20, 0, -6), R(1.2, 34, 0, -8), R(2.9)]
    lullaby_wave['bell'] = [Z(0), R(.5, 0, 0, 40), R(.7, 0, 0, -50), R(.95, 0, 0, 36), R(1.2, 0, 0, -26), R(1.5, 0, 0, 16), R(1.9, 0, 0, -8), R(2.9)]
    echo_cast = {   # 5 악몽의 잔상: 왼손으로 가리키고 손가락을 튕긴다
        'body': [Z(0), R(.4, 8, 12), R(.9, 8, 12), Z(1.2)], 'head': [Z(0), R(.4, 10, 10), R(.9, 10, 10), Z(1.2)],
        'left_arm': [Z(0), R(.4, 84, 0, -8), R(.9, 84, 0, -8), Z(1.2)], 'left_forearm': [Z(0), R(.4, -28), R(.9, -28), Z(1.2)],
        'left_hand': [Z(0), R(.4, -30), R(.55, -30), R(.62, 30), R(.9, 20), Z(1.2)]}
    yawn = mirror_tracks({   # 6 하품: 2.0초 동안 들이마시고(빨아들임) 2.3초에 내뱉음(폭발)
        'body': [Z(0), R(2.0, -18), R(2.3, 20, 0, 0, 'linear'), R(2.6, 16), R(3.0)], 'head': [Z(0), R(.5, -20), R(2.0, -42), R(2.3, 14, 0, 0, 'linear'), R(3.0)],
        'root': [T(0), T(2.0, 0, 6, 4), T(2.3, 0, -2, -4, 'linear'), T(3.0)],
        'right_arm': [Z(0), R(.6, 10, 0, 60), R(2.0, -10, 0, 86), R(2.3, 60, 0, 20, 'linear'), R(3.0)],
        'right_arm2': [Z(0), R(.6, -20, 0, 40), R(2.0, -34, 0, 60), R(2.3, 30, 0, 10, 'linear'), R(3.0)],
        'right_wing': [Z(0), R(2.0, 0, -36, 24), R(2.3, 0, 44, 0, 'linear'), R(3.0)], 'right_midwing': [Z(0), R(2.0, 0, -32, 12), R(2.33, 0, 40, 0, 'linear'), R(3.0)],
        'right_hindwing': [Z(0), R(2.0, 0, -28, 0), R(2.36, 0, 36, 0, 'linear'), R(3.0)], 'right_antenna': [Z(0), R(2.0, -30), R(2.3, 20), R(3.0)]})
    fog_cast = {   # 7 잠의 안개: 왼손으로 크게 뿌린다 (0.6초부터 안개)
        'body': [Z(0), R(.35, -6, -16), R(.7, 8, 20), Z(1.4)], 'head': [Z(0), R(.35, -8), R(.7, 8), Z(1.4)],
        'left_arm': [Z(0), R(.35, 130, 0, 30), R(.7, 60, 0, -60), R(1.0, 50, 0, -50), Z(1.4)], 'left_forearm': [Z(0), R(.35, -30), R(.7, 0), Z(1.4)],
        'left_hand': [Z(0), R(.35, 30), R(.7, -40), Z(1.4)], 'left_wing': [Z(0), R(.35, 0, 16, 0), R(.7, 0, -20, -10), Z(1.4)]}
    eyelid = mirror_tracks({   # 8 눈꺼풀: 날개를 세 번에 걸쳐 앞으로 닫는다 (0.8 / 1.5 / 2.2초)
        'body': [Z(0), R(2.2, 12), R(2.6)], 'head': [Z(0), R(2.2, 22), R(2.6)],
        **W([(0, 0, 0, 0), (.65, 0, -10, 6), (.8, 0, 30, 0), (1.35, 0, 22, 0), (1.5, 0, 60, 0), (2.05, 0, 52, 0), (2.2, 0, 90, 0), (2.4, 0, 86, 0), (2.6, 0, 0, 0)], dt=0),
        'right_arm': [Z(0), R(2.2, 40, 0, -30), R(2.6)]})
    full_moon = {   # 9 만월 낙하: 2.0초 동안 달 지팡이를 치켜들고 있다가 내리꽂는다
        'root': [T(0), T(.8, 0, 10), T(1.9, 0, 12), T(2.1, 0, -6, -6, 'linear'), T(2.5, 0, -6, -6), T(2.8)],
        'body': [Z(0), R(.8, -14), R(1.9, -16), R(2.1, 26, 0, 0, 'linear'), R(2.5, 22), Z(2.8)], 'head': [Z(0), R(.8, -30), R(1.9, -32), R(2.1, 16, 0, 0, 'linear'), Z(2.8)],
        'right_arm': [Z(0), R(.8, 160, 0, 10), R(1.9, 166, 0, 8), R(2.1, 50, 0, -6, 'linear'), R(2.5, 46, 0, -6), Z(2.8)],
        'right_forearm': [Z(0), R(.8, -25), R(1.9, -25), R(2.1, 10, 0, 0, 'linear'), Z(2.8)],
        'staff': [Z(0), R(.8, -84, 0, 6), R(1.9, -84, 0, 6), R(2.1, 60, 0, 0, 'linear'), R(2.5, 56), Z(2.8)],
        'left_arm': [Z(0), R(.8, 150, 0, -16), R(1.9, 156, 0, -14), R(2.1, 50, 0, 6, 'linear'), Z(2.8)],
        'moon': [S(0), S(.8, 1.4, 1.4, 1.4), S(1.9, 1.6, 1.6, 1.6), S(2.1, 1, 1, 1), S(2.8)],
        'right_wing': [Z(0), R(.8, 0, -12, 30), R(1.9, 0, -14, 32), R(2.1, 0, 30, 0, 'linear'), Z(2.8)], 'left_wing': [Z(0), R(.8, 0, 12, -30), R(1.9, 0, 14, -32), R(2.1, 0, -30, 0, 'linear'), Z(2.8)]}
    gate_strike = mirror_tracks({   # 성문 강타: 1.3초 동안 치켜들고 1.5초에 내려찍음
        'root': [T(0), T(1.3, 0, 12, 5), T(1.5, 0, -8, -9, 'linear'), T(1.9, 0, -8, -9), T(2.3)],
        'body': [Z(0), R(1.3, -22), R(1.5, 30, 0, 0, 'linear'), R(1.9, 28), R(2.3)], 'head': [Z(0), R(1.3, -16), R(1.5, 20, 0, 0, 'linear'), R(2.3)],
        'right_arm': [Z(0), R(1.3, 172, 0, 18), R(1.5, 68, 0, 10, 'linear'), R(1.9, 64, 0, 10), R(2.3)], 'right_forearm': [Z(0), R(1.3, -10), R(1.5, 10, 0, 0, 'linear'), R(2.3)],
        **W([(0, 0, 0, 0), (1.3, 0, -32, 22), (1.5, 0, 32, 0), (2.3, 0, 0, 0)])})
    spec = [('spawn', 3.0, 'once', spawn), ('idle', 4.0, 'loop', idle), ('walk', L, 'loop', walk),
            ('attack', 1.2, 'once', attack), ('death', 3.6, 'hold', death),
            ('lullaby', 2.4, 'loop', lullaby), ('wing_gust', 1.4, 'once', wing_gust), ('gaze', 3.0, 'once', gaze),
            ('staff_cast', 2.0, 'once', staff_cast), ('press', 1.8, 'once', press),
            ('groggy', 3.0, 'loop', groggy), ('enrage', 2.8, 'once', enrage)]
    spec += [('orb_summon', 1.6, 'once', orb_summon), ('gaze_sweep', 6.2, 'once', gaze_sweep), ('moon_orbit', 6.6, 'once', moon_orbit), ('lullaby_wave', 2.9, 'once', lullaby_wave),
             ('echo_cast', 1.2, 'once', echo_cast), ('yawn', 3.0, 'once', yawn), ('fog_cast', 1.4, 'once', fog_cast), ('eyelid', 2.6, 'once', eyelid),
             ('full_moon', 2.8, 'once', full_moon), ('gate_strike', 2.3, 'once', gate_strike)]
    for _n, _l, _p, _tr in spec:   # 망토는 뒤로 날려야 하므로 x 회전 부호를 뒤집음 (+x = 앞으로)
        if 'cape' in _tr: _tr['cape'] = [(k[0], k[1], -k[2], *k[3:]) if k[0] == 'rotation' else k for k in _tr['cape']]
    for _n, _l, _p, _tr in spec:   # 몸통·머리는 +x가 뒤로 젖힘이라, 숙임/젖힘 의도대로 x 부호를 뒤집음
        if _n in (): continue
        for _b in ('body', 'head'):
            if _b in _tr: _tr[_b] = [(k[0], k[1], -k[2], *k[3:]) if k[0] == 'rotation' else k for k in _tr[_b]]
    # ---- 달 지팡이: 손에 고정, 손목이 지팡이 머리 방향을 맞춘다. (t, phi, pitch) / phi 0=정면 +=오른쪽, pitch +=위 ----
    U = 'rest'
    WT = {'attack': [(0, U), (.4, 0, 138), (.58, 0, -4), (.9, 0, -8), (1.2, U)],
          'death': [(0, U), (1.6, U), (2.6, -40, 12), (3.6, -60, -8)],
          'press': [(0, U), (.6, 0, 104), (.85, 0, 6), (1.3, 0, 2), (1.8, U)],
          'moon_orbit': [(0, U), (.6, 0, 74)] + [(round(.6 + k * .2, 2), k * 90, 74) for k in range(1, 28)] + [(6.2, 27 * 90, 80), (6.6, U)],
          'full_moon': [(0, U), (.8, 0, 88), (1.9, 0, 98), (2.1, 0, 0), (2.5, 0, -4), (2.8, U)],
          'gate_strike': [(0, U), (1.3, 0, 112), (1.5, 0, 0), (1.9, 0, -3), (2.3, U)]}
    WRIST_DEV.update(weapon_apply(G, spec, ['body', 'right_arm', 'right_forearm'], 'right_hand', 'staff', WT))
    return build_anims(f'animation.{ID}.', spec, G)
WRIST_DEV = {}

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    export(ID, build(), anims, 31, O + ID + '.bbmodel', HITBOX)
    apply_palette(SHINY)
    export(ID + '_shiny', build(), anims, 31, O + ID + '_shiny.bbmodel', HITBOX)
