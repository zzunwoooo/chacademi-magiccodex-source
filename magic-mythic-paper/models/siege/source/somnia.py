"""몽마 솜니아 (somnia) — 꿈을 먹는 나방 악마. 감긴 눈 무늬 날개 4장, 양 두개골 얼굴, 이불처럼 흘러내리는 몸."""
from common import *
import math, os, json, base64, io
from PIL import Image

ID = 'somnia'
PAL = dict(
    FUR=(214, 200, 232), DFUR=(148, 128, 182), CLOTH=(42, 42, 100), DCLOTH=(24, 22, 62), STAR=(240, 214, 120),
    MOON=(250, 240, 200), BONE=(232, 224, 206), DBONE=(158, 146, 126), SOCKET=(14, 8, 28), EYE=(150, 232, 255, 255),
    WING=(122, 98, 160), DWING=(68, 50, 100), WEDGE=(214, 200, 230), VEIN=(52, 36, 80), LID=(34, 24, 58),
    LASH=(240, 230, 255), SKIN=(98, 82, 124), DSKIN=(58, 46, 80), HORN=(190, 172, 142), DHORN=(108, 94, 74),
    ANT=(92, 78, 112),
)
SHINY = dict(
    FUR=(255, 236, 196), DFUR=(214, 170, 110), CLOTH=(150, 40, 60), DCLOTH=(92, 20, 36), STAR=(150, 230, 255),
    MOON=(200, 240, 255), BONE=(60, 54, 70), DBONE=(34, 30, 42), SOCKET=(255, 240, 220), EYE=(255, 120, 60, 255),
    WING=(226, 164, 76), DWING=(170, 92, 40), WEDGE=(255, 236, 190), VEIN=(120, 52, 22), LID=(110, 40, 20),
    LASH=(60, 20, 10), SKIN=(208, 160, 110), DSKIN=(150, 104, 64), HORN=(70, 60, 80), DHORN=(30, 24, 40),
    ANT=(200, 150, 90),
)
HITBOX = (3.0, 7.8, 6.2)
FLOAT = 10

def apply_palette(p): PAL.update(p)

# ---------------- 페인터 ----------------
def fur(ragg=0.4):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r
        for y in range(y1, y2):
            for x in range(x1, x2):
                c = lerp(PAL['FUR'], PAL['DFUR'], rng.random() * .55 + (.25 if (x + y) % 3 == 0 else 0))
                img.putpixel((x, y), col(c, SHADE[face] * (1 + rng.uniform(-.06, .06))))
        if face not in ('up', 'down'):
            for x in range(x1, x2):
                if rng.random() < ragg: img.putpixel((x, y2 - 1), (0, 0, 0, 0))
                if rng.random() < ragg * .4: img.putpixel((x, y2 - 2), (0, 0, 0, 0))
    return p

def quilt(ragg=0.0):
    """이불 천: 남색 + 세로 주름 + 작은 별·달 무늬."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for y in range(y1, y2):
            for x in range(x1, x2):
                lx = x - x1
                c = PAL['DCLOTH'] if (face not in ('up', 'down') and lx % 6 in (0, 5)) else PAL['CLOTH']
                img.putpixel((x, y), col(c, SHADE[face] * (1 + rng.uniform(-.05, .05))))
        if face not in ('up', 'down') and w >= 6:
            for _ in range(max(1, w * h // 70)):
                lx, ly = rng.randrange(1, w - 1), rng.randrange(1, max(2, h - 2))
                if rng.random() < .8:
                    px(img, r, lx, ly, col(PAL['STAR']))
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if rng.random() < .5: px(img, r, lx + dx, ly + dy, col(PAL['STAR'], .75))
                else:
                    for dy in range(3): px(img, r, lx, ly + dy, col(PAL['MOON']))
                    px(img, r, lx + 1, ly, col(PAL['MOON'])); px(img, r, lx + 1, ly + 2, col(PAL['MOON']))
        if ragg and face not in ('up', 'down'):
            for x in range(x1, x2):
                lx = x - x1
                cut = 1 + int(1.5 + 1.5 * math.sin(lx * 0.9 + rng.random()))
                for k in range(cut): img.putpixel((x, y2 - 1 - k), (0, 0, 0, 0))
    return p

def skull_face(img, r, rng):
    x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
    for ex in (1, w - 5):            # 큰 눈구멍 + 작은 빛
        for dx in range(4):
            for dy in range(3, 8):
                if not ((dx in (0, 3)) and dy in (3, 7)): px(img, r, ex + dx, dy, col(PAL['SOCKET']))
        px(img, r, ex + 1 + (1 if ex > 3 else 0), 5, col(PAL['EYE']))
    for ly in range(1, 4):           # 이마 갈라짐
        px(img, r, w // 2, ly, col(PAL['DBONE']))

def snout_face(img, r, rng):
    x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
    for dx in (-2, -1, 1, 2):
        for dy in (2, 3, 4): px(img, r, w // 2 + dx - (1 if dx > 0 else 0), dy, col(PAL['SOCKET']))
    for lx in range(1, w - 1):       # 이빨
        px(img, r, lx, h - 2, col(PAL['SOCKET']) if lx % 2 else col(PAL['BONE'], 1.1))
        px(img, r, lx, h - 1, col(PAL['SOCKET']))

def wing_paint(box, kind):
    """날개 판: 실루엣 밖은 투명. 감긴 눈 무늬 + 날개맥 + 밝은 가장자리. kind='fore'|'hind'."""
    (X0, Y0, Z0), (X1, Y1, Z1) = box
    ax0, ax1 = min(abs(X0), abs(X1)), max(abs(X0), abs(X1))
    def inside(t, v):   # t: 몸쪽 0 → 끝 1, v: 위 0 → 아래 1
        if kind == 'fore':
            top = 0.10 + 0.55 * (1 - t) ** 2.2
            bot = 0.98 - 0.30 * t ** 1.4 + 0.035 * math.sin(t * 26)
            return top <= v <= bot and t <= 0.98 - 0.25 * max(0, v - .55)
        top = 0.04 + 0.08 * t
        bot = 0.62 + 0.30 * (1 - t) ** 0.6 + 0.04 * math.sin(t * 22)
        return top <= v <= bot and ((t - .05) / .95) ** 2 + ((v - .45) / .58) ** 2 <= 1
    eye_t, eye_v, eye_r = (0.58, 0.55, 0.20) if kind == 'fore' else (0.50, 0.42, 0.22)
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
                # 가장자리 밝은 띠
                edge = not (inside(min(1, t + .045), v) and inside(t, min(1, v + .05)) and inside(t, max(0, v - .05)))
                if edge: c = PAL['WEDGE']
                # 날개맥 (몸에서 부채꼴로)
                ang = math.atan2(v - (0.75 if kind == 'fore' else 0.2), t + 0.05)
                if not edge and abs((ang * 7) % 1 - .5) < .07: c = PAL['VEIN']
                # 감긴 눈 무늬
                dt, dv = (t - eye_t) / eye_r, (v - eye_v) / (eye_r * 0.75)
                d = dt * dt + dv * dv
                if d < 1:
                    c = lerp(PAL['WEDGE'], PAL['WING'], d)
                    lid = -0.35 + 0.6 * dt * dt            # 감긴 눈꺼풀 곡선(아래로 볼록)
                    if abs(dv - (0.15 - 0.55 * (1 - dt * dt))) < .14: c = PAL['LID']
                    elif dv > (0.15 - 0.55 * (1 - dt * dt)) + .14 and d < .85: c = lerp(PAL['DWING'], PAL['LID'], .4)
                    if abs(d - 1) < .12: c = PAL['LID']
                    # 속눈썹
                    lt = 0.15 - 0.55 * (1 - dt * dt)
                    if dv > lt and dv < lt + .45 and abs((dt * 5) % 1 - .5) < .12 and abs(dt) < .85: c = PAL['LASH']
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.04, .04))))
    return p

def feather(img, face, r, rng):
    """깃털 더듬이: 빗살 모양 (사이사이 투명)."""
    x1, y1, x2, y2 = r
    for y in range(y1, y2):
        for x in range(x1, x2):
            if face in ('north', 'south') and (y - y1) % 2 == 1 and (x - x1) not in (0,):
                img.putpixel((x, y), (0, 0, 0, 0)); continue
            img.putpixel((x, y), col(lerp(PAL['ANT'], PAL['FUR'], rng.random() * .4), SHADE[face]))

# ---------------- 모델 ----------------
def build():
    bone = sol(PAL, 'BONE', .06); dbone = sol(PAL, 'DBONE'); skin = sol(PAL, 'SKIN')
    claw = tipped(PAL, 'DSKIN', 'BONE', 3); horn = sol(PAL, 'HORN', .1); dhorn = sol(PAL, 'DHORN', .1)

    def ram_horn(s):
        m, P, Rt = side(s)
        return Group(f'{s}_horn', P([7, 96, -3]), [
            C(f'{s}_horn1', [7, 94, -6], [12, 100, 0], horn, m),
            C(f'{s}_horn2', [8, 92, 0], [13, 100, 6], horn, m),
            C(f'{s}_horn3', [8, 85, 3], [13, 93, 8], horn, m),
            C(f'{s}_horn4', [8.5, 83, -3], [12.5, 88, 4], dhorn, m),
            C(f'{s}_horn5', [9, 85, -6], [12, 89, -3], dhorn, m)])

    def antenna(s):
        m, P, Rt = side(s)
        tip = Group(f'{s}_antenna2', P([3, 110, -6]), [
            C(f'{s}_antenna_stem2', [2.5, 110, -6.5], [3.5, 124, -5.5], sol(PAL, 'ANT'), m),
            C(f'{s}_antenna_plume2', [1, 111, -7], [6, 123, -6], feather, m)], Rt([-20, 0, -10]))
        return Group(f'{s}_antenna', P([3, 99, -6]), [
            C(f'{s}_antenna_stem', [2.5, 99, -6.5], [3.5, 111, -5.5], sol(PAL, 'ANT'), m),
            C(f'{s}_antenna_plume', [1, 101, -7], [6, 111, -6], feather, m), tip], Rt([-18, 0, -22]))

    def arm(s):
        m, P, Rt = side(s)
        fingers = [C(f'{s}_finger{i+1}', [x, 28, -2], [x + 1, 40, -1], claw, m) for i, x in enumerate((9, 10.5, 12, 13.5))]
        hand = Group(f'{s}_hand', P([11.5, 42, -1]), [
            C(f'{s}_palm', [9, 39, -3], [14, 44, 1], skin, m), *fingers,
            C(f'{s}_thumb', [8, 34, -3], [9, 40, -2], claw, m)])
        fore = Group(f'{s}_forearm', P([11.5, 60, -1]), [
            C(f'{s}_forearm', [10, 43, -2.5], [13, 61, 0.5], skin, m),
            C(f'{s}_wrist_fluff', [9, 44, -3.5], [14, 48, 1.5], fur(.5), m), hand], [30, 0, 0])
        return Group(f'{s}_arm', P([11.5, 78, -1]), [
            C(f'{s}_upper_arm', [10, 59, -3], [13, 79, 1], skin, m),
            C(f'{s}_shoulder_fluff', [8, 72, -5], [15, 80, 3], fur(.5), m), fore], Rt([10, 0, 18]))

    def wings(s):
        m, P, Rt = side(s)
        sx = -1 if m else 1
        fb = ([8, 74, 6], [58, 118, 7]); hb = ([8, 38, 7], [46, 76, 8])
        if m: fb = ([-58, 74, 6], [-8, 118, 7]); hb = ([-46, 38, 7], [-8, 76, 8])
        fore = Group(f'{s}_wing', P([8, 82, 6.5]), [
            Cube(f'{s}_wing', *fb, wing_paint(fb, 'fore')),
            C(f'{s}_wing_root', [6, 78, 4], [10, 88, 8], fur(.3), m)], Rt([0, -18, 12]))
        hind = Group(f'{s}_hindwing', P([8, 72, 7.5]), [
            Cube(f'{s}_hindwing', *hb, wing_paint(hb, 'hind'))], Rt([0, -26, -8]))
        return [fore, hind]

    head = Group('head', [0, 88, -4], [
        C('skull', [-7, 86, -12], [7, 100, 2], faces(bone, north=skull_face)),
        C('skull_crown', [-5, 99, -10], [5, 102, 0], bone),
        C('snout', [-5, 84, -18], [5, 94, -12], faces(bone, north=snout_face)),
        C('snout_bridge', [-3, 92, -17], [3, 96, -12], dbone),
        C('jaw', [-4, 81, -17], [4, 84, -10], dbone),
        ram_horn('right'), ram_horn('left'), antenna('right'), antenna('left')])

    drape3 = Group('drape3', [0, 22, 0], [
        C('drape3', [-14, 4, -11], [14, 22, 11], quilt(1)),
        C('drape3_fold_r', [14, 6, -6], [16, 22, 6], quilt(1)),
        C('drape3_fold_l', [-16, 8, -4], [-14, 22, 8], quilt(1))])
    drape2 = Group('drape2', [0, 40, 0], [
        C('drape2', [-13, 22, -10], [13, 40, 10], quilt()),
        C('drape2_tassel', [-2, 18, -11], [2, 26, -10], sol(PAL, 'STAR')), drape3])
    drape1 = Group('drape1', [0, 62, 0], [
        C('drape1', [-12, 40, -9], [12, 62, 9], quilt()),
        C('drape_hem', [-13, 58, -10], [13, 62, 10], stripes(PAL, 'DCLOTH', 'STAR', 5, 1)), drape2])

    body = Group('body', [0, 70, 0], [
        C('thorax', [-10, 62, -8], [10, 84, 8], fur(.1)),
        C('ruff', [-14, 78, -11], [14, 86, 10], fur(.55)),
        C('ruff_back', [-11, 84, 0], [11, 90, 10], fur(.55)),
        C('ruff_front', [-9, 74, -12], [9, 80, -10], fur(.6)),
        drape1, head, arm('right'), arm('left'), *wings('right'), *wings('left')])
    root = Group('root', [0, 0, 0], [body])
    shift(root, FLOAT)
    return root

# ---------------- 애니메이션 ----------------
def anims(G):
    L = 1.6
    def wf(L, a, b, ph=0): return osc('rotation', L, a, b, ph, 4, 1)   # 날개 y축 퍼덕임
    idle = mirror_tracks({
        'root': osc('position', 4, -1.5, 1.5, 0, 4, 1),
        'body': osc('rotation', 4, -2, 2, 1, 4, 2),
        'right_wing': wf(4, -8, 8), 'right_hindwing': wf(4, -6, 6, .6),
        'right_antenna': osc('rotation', 4, -6, 6, .3, 4, 0), 'right_antenna2': osc('rotation', 4, -10, 10, 1, 4, 0),
        'right_arm': osc('rotation', 4, -4, 4, .5, 4, 0), 'right_forearm': osc('rotation', 4, -6, 6, 1.2, 4, 0),
        'head': osc('rotation', 4, -4, 4, .8, 4, 0),
        'drape1': osc('rotation', 4, -3, 3, 0, 4, 2), 'drape2': osc('rotation', 4, -5, 5, 1, 4, 2),
        'drape3': osc('rotation', 4, -8, 8, 2, 4, 2)})
    walk = mirror_tracks({   # 미끄러지듯 비행: 앞으로 숙이고 날개 퍼덕임
        'root': osc('position', L, -2, 2, 0, 4, 1),
        'body': [R(0, 14), R(L, 14)],
        'head': [R(0, -10), R(L, -10)],
        'right_wing': [R(0, 0, -30, 10), R(L / 2, 0, 20, 4), R(L, 0, -30, 10)],
        'right_hindwing': [R(0, 0, -20), R(L / 2, 0, 18), R(L, 0, -20)],
        'right_arm': [R(0, -20, 0, -6), R(L, -20, 0, -6)],
        'right_antenna': [R(0, -20), R(L, -20)],
        'drape1': [R(0, -10), R(L / 2, -14), R(L, -10)], 'drape2': [R(0, -10), R(L / 2, -6), R(L, -10)],
        'drape3': [R(0, -12, 0, 6), R(L / 2, -16, 0, -6), R(L, -12, 0, 6)]})
    spawn = mirror_tracks({   # 날개로 몸을 감싼 고치 → 활짝 펼침
        'root': [T(0, 0, -20), T(1.0, 0, 0), S(0, .7, .7, .7), S(1.0, 1, 1, 1)],
        'right_wing': [R(0, 0, 95, -20), R(1.4, 0, 95, -20), R(2.0, 0, -25, 25), R(2.6)],
        'right_hindwing': [R(0, 0, 100, 10), R(1.5, 0, 100, 10), R(2.1, 0, -20), R(2.6)],
        'head': [R(0, 40), R(1.5, 40), R(2.1, -20), R(2.6)],
        'right_arm': [R(0, 60, 0, -30), R(1.5, 60, 0, -30), R(2.1, -30, 0, 40), R(2.6)],
        'right_antenna': [R(0, 40), R(1.6, 40), R(2.2, -15), R(2.6)]})
    attack = {   # 오른손 긴 손가락 할퀴기
        'root': [T(0), T(.4, 0, 2, 3), T(.55, 0, -1, -8), T(.9, 0, -1, -8), T(1.2)],
        'body': [Z(0), R(.4, -8, 25), R(.55, 18, -25), R(.9, 15, -20), R(1.2)],
        'right_arm': [Z(0), R(.4, 40, 0, 80), R(.55, 95, 0, -30), R(.9, 85, 0, -25), R(1.2)],
        'right_forearm': [Z(0), R(.4, -30), R(.55, 0), R(1.2)],
        'right_hand': [Z(0), R(.4, -30), R(.55, 30), R(1.2)],
        'right_wing': [Z(0), R(.4, 0, -25), R(.55, 0, 15), R(1.2)],
        'left_wing': [Z(0), R(.4, 0, 25), R(.55, 0, -15), R(1.2)],
        'head': [Z(0), R(.4, -10, 15), R(.55, 10, -15), R(1.2)]}
    death = mirror_tracks({   # 날개가 접히며 바닥으로 내려앉아 천만 남은 듯 무너짐
        'root': [T(0), T(.6, 0, 4), T(2.2, 0, -FLOAT - 2), T(3.0, 0, -FLOAT - 4), S(0), S(2.2, 1.1, .85, 1.1), S(3.0, 1.2, .6, 1.2)],
        'right_wing': [Z(0), R(.6, 0, -40, 30), R(2.0, 0, -70, -40), R(3.0, 0, -75, -55)],
        'right_hindwing': [Z(0), R(.6, 0, -30), R(2.0, 0, -60, -30), R(3.0, 0, -65, -35)],
        'head': [Z(0), R(.6, -40), R(1.8, 30), R(3.0, 55)],
        'right_arm': [Z(0), R(.6, -30, 0, 50), R(2.2, 10, 0, 10), R(3.0, 0, 0, 5)],
        'right_antenna': [Z(0), R(1.5, 40), R(3.0, 70)],
        'drape1': [Z(0), R(2.2, 10), R(3.0, 15)], 'drape3': [Z(0), R(2.2, -30), R(3.0, -40)]})
    # ---- 패턴 모션 ----
    lullaby = mirror_tracks({   # 자장가 (루프 2.4): 좌우로 흔들며 두 팔로 지휘하듯
        'root': [T(0, -3, 0), T(1.2, 3, 0), T(2.4, -3, 0)],
        'body': [R(0, 0, 0, 8), R(1.2, 0, 0, -8), R(2.4, 0, 0, 8)],
        'head': [R(0, 10, 0, -10), R(1.2, 10, 0, 10), R(2.4, 10, 0, -10)],
        'right_arm': [R(0, 60, 0, 30), R(.6, 75, 0, 15), R(1.2, 60, 0, 30), R(1.8, 75, 0, 15), R(2.4, 60, 0, 30)],
        'right_hand': [R(0, -30), R(1.2, 10), R(2.4, -30)],
        'right_wing': wf(2.4, -12, 12), 'right_hindwing': wf(2.4, -10, 10, .8)})
    wing_gust = mirror_tracks({   # 날개 바람: 날개 뒤로 젖혔다가 앞으로 강하게 휘두름
        'root': [T(0), T(.5, 0, 4, 4), T(.8, 0, 0, -3), T(1.4)],
        'body': [Z(0), R(.5, -15), R(.8, 15), R(1.4)],
        'right_wing': [Z(0), R(.5, 0, -60, 20), R(.8, 0, 55, -5), R(1.1, 0, 45, -5), R(1.4)],
        'right_hindwing': [Z(0), R(.55, 0, -55), R(.85, 0, 50), R(1.4)],
        'right_arm': [Z(0), R(.5, -40, 0, 50), R(.8, 40, 0, 20), R(1.4)],
        'head': [Z(0), R(.5, -20), R(.8, 15), R(1.4)]})
    feather_rain = mirror_tracks({   # 깃털 비: 위로 떠오르며 날개를 활짝 펴고 떨게 함
        'root': [T(0), T(.6, 0, 12), T(2.0, 0, 12), T(2.6)],
        'body': [Z(0), R(.6, -14), R(2.0, -14), R(2.6)],
        'head': [Z(0), R(.6, -30), R(2.0, -30), R(2.6)],
        'right_wing': [Z(0), R(.6, 0, -5, 35), R(.8, 0, 5, 40), R(1.0, 0, -5, 35), R(1.2, 0, 5, 40), R(1.4, 0, -5, 35), R(1.6, 0, 5, 40), R(2.0, 0, 0, 35), R(2.6)],
        'right_hindwing': [Z(0), R(.6, 0, 0, 20), R(1.0, 0, 8, 22), R(1.4, 0, 0, 20), R(2.0, 0, 0, 20), R(2.6)],
        'right_arm': [Z(0), R(.6, 150, 0, 30), R(2.0, 150, 0, 30), R(2.6)],
        'right_antenna': [Z(0), R(.6, -25, 0, -15), R(2.0, -25, 0, -15), R(2.6)]})
    pillow_press = mirror_tracks({   # 베개 짓누르기: 두 팔 머리 위로 → 앞 바닥으로 내리누름
        'root': [T(0), T(.6, 0, 10, 4), T(.85, 0, -6, -8), T(1.3, 0, -6, -8), T(1.8)],
        'body': [Z(0), R(.6, -20), R(.85, 30), R(1.3, 28), R(1.8)],
        'right_arm': [Z(0), R(.6, 170, 0, 20), R(.85, 70, 0, 10), R(1.3, 65, 0, 10), R(1.8)],
        'right_forearm': [Z(0), R(.6, -10), R(.85, 10), R(1.8)],
        'right_hand': [Z(0), R(.85, 40), R(1.3, 40), R(1.8)],
        'right_wing': [Z(0), R(.6, 0, -30, 20), R(.85, 0, 30), R(1.8)],
        'head': [Z(0), R(.6, -15), R(.85, 20), R(1.8)]})
    dream_cast = mirror_tracks({   # 꿈 패턴 시전 (루프 2.0): 손을 앞으로 내밀어 손가락을 꼼지락
        'root': osc('position', 2, 2, 5, 0, 4, 1),
        'body': [R(0, -8), R(2, -8)],
        'right_arm': [R(0, 80, 0, 25), R(1, 88, 0, 20), R(2, 80, 0, 25)],
        'right_forearm': [R(0, 10), R(1, 20), R(2, 10)],
        'right_hand': [R(0, -30), R(.5, -10), R(1, -35), R(1.5, -10), R(2, -30)],
        'right_wing': wf(2, -10, 10), 'right_hindwing': wf(2, -8, 8, 1)})
    eyelid = mirror_tracks({   # 눈꺼풀: 날개가 앞으로 감싸 닫혔다가 활짝
        'right_wing': [Z(0), R(.5, 0, -35, 10), R(.9, 0, 85, 0), R(1.4, 0, 85, 0), R(1.7, 0, -20, 15), R(2.2)],
        'right_hindwing': [Z(0), R(.5, 0, -30), R(.95, 0, 85), R(1.4, 0, 85), R(1.75, 0, -15), R(2.2)],
        'body': [Z(0), R(.5, -10), R(.9, 12), R(1.4, 12), R(1.7, -10), R(2.2)],
        'head': [Z(0), R(.9, 25), R(1.4, 25), R(1.7, -20), R(2.2)],
        'right_arm': [Z(0), R(.9, 40, 0, -30), R(1.4, 40, 0, -30), R(2.2)]})
    wake = mirror_tracks({   # "일어나": 머리 젖혀 외침, 날개·팔 활짝
        'root': [T(0), T(.4, 0, -4), T(.7, 0, 6), T(1.8, 0, 6), T(2.2)],
        'body': [Z(0), R(.4, 15), R(.7, -20), R(1.8, -18), R(2.2)],
        'head': [Z(0), R(.4, 20), R(.7, -45), R(.9, -40), R(1.1, -45), R(1.8, -42), R(2.2)],
        'right_wing': [Z(0), R(.4, 0, 30), R(.7, 0, -30, 30), R(1.8, 0, -28, 28), R(2.2)],
        'right_hindwing': [Z(0), R(.7, 0, -25, 15), R(1.8, 0, -25, 15), R(2.2)],
        'right_arm': [Z(0), R(.4, 30, 0, -20), R(.7, 10, 0, 85), R(1.8, 10, 0, 80), R(2.2)]})
    groggy = mirror_tracks({   # 그로기 (루프 3.0): 바닥 가까이 내려앉아 날개 축 처짐
        'root': [T(0, 0, -FLOAT + 1), T(1.5, 0, -FLOAT), T(3.0, 0, -FLOAT + 1)],
        'body': [R(0, 20), R(1.5, 24), R(3.0, 20)],
        'head': [R(0, 35, 0, 10), R(1.5, 40, 0, 12), R(3.0, 35, 0, 10)],
        'right_wing': [R(0, 0, -30, -45), R(1.5, 0, -32, -50), R(3.0, 0, -30, -45)],
        'right_hindwing': [R(0, 0, -25, -20), R(3.0, 0, -25, -20)],
        'right_arm': [R(0, -10, 0, -12), R(3.0, -10, 0, -12)],
        'right_antenna': [R(0, 45), R(1.5, 50), R(3.0, 45)],
        'drape3': [R(0, -25), R(3.0, -25)]})
    enrage = mirror_tracks({   # 광폭화 (경계 붕괴): 몸을 웅크렸다가 날개 4장을 한계까지 펼침
        'root': [T(0), T(.6, 0, -6), T(1.1, 0, 14), T(2.2, 0, 14), T(2.8)],
        'body': [Z(0), R(.6, 30), R(1.1, -24), R(2.2, -20), R(2.8)],
        'head': [Z(0), R(.6, 40), R(1.1, -50), R(1.3, -44), R(1.5, -50), R(2.2, -46), R(2.8)],
        'right_wing': [Z(0), R(.6, 0, 80, -10), R(1.1, 0, -40, 40), R(1.3, 0, -30, 44), R(1.5, 0, -40, 40), R(2.2, 0, -38, 40), R(2.8)],
        'right_hindwing': [Z(0), R(.6, 0, 80), R(1.1, 0, -40, 25), R(2.2, 0, -38, 25), R(2.8)],
        'right_arm': [Z(0), R(.6, 50, 0, -40), R(1.1, -20, 0, 100), R(2.2, -20, 0, 95), R(2.8)],
        'right_antenna': [Z(0), R(1.1, -35, 0, -20), R(2.2, -35, 0, -20), R(2.8)]})
    spec = [('spawn', 2.6, 'once', spawn), ('idle', 4.0, 'loop', idle), ('walk', L, 'loop', walk),
            ('attack', 1.2, 'once', attack), ('death', 3.0, 'hold', death),
            ('lullaby', 2.4, 'loop', lullaby), ('wing_gust', 1.4, 'once', wing_gust),
            ('feather_rain', 2.6, 'once', feather_rain), ('pillow_press', 1.8, 'once', pillow_press),
            ('dream_cast', 2.0, 'loop', dream_cast), ('eyelid', 2.2, 'once', eyelid), ('wake', 2.2, 'once', wake),
            ('groggy', 3.0, 'loop', groggy), ('enrage', 2.8, 'once', enrage)]
    return build_anims(f'animation.{ID}.', spec, G)

def make_veil(src, dst, name, alpha=120):
    """현실 쪽 반투명 버전: 모양·애니메이션 동일, 텍스처 알파만 낮춤."""
    m = json.load(open(src))
    t = m['textures'][0]
    img = Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')
    px_ = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px_[x, y]
            if a > 0: px_[x, y] = (min(255, r + 25), min(255, g + 25), min(255, b + 35), min(a, alpha))
    buf = io.BytesIO(); img.save(buf, 'PNG')
    t['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    t['name'] = name + '.png'; m['name'] = name; m['model_identifier'] = name
    json.dump(m, open(dst, 'w'), indent=1, ensure_ascii=False)
    print(name, 'veil')

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    export(ID, build(), anims, 21, O + ID + '.bbmodel', HITBOX)
    make_veil(O + ID + '.bbmodel', O + ID + '_veil.bbmodel', ID + '_veil')
    apply_palette(SHINY)
    export(ID + '_shiny', build(), anims, 21, O + ID + '_shiny.bbmodel', HITBOX)
    make_veil(O + ID + '_shiny.bbmodel', O + ID + '_shiny_veil.bbmodel', ID + '_shiny_veil')
