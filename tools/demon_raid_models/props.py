"""기믹 소품 5종 (이로치·히트박스 없음 — ModelEngine/디스플레이용).
룰레타: roulette_dice(큰 주사위), roulette_chip_tower(칩 탑), roulette_coin(동전)
솜니아: dream_bell(종탑 종), dream_pillow(거대 베개)"""
from common import *
import math, sys

PAL = dict(DIE=(238, 232, 220), PIP=(170, 24, 36), DIEE=(196, 188, 172),
           CHIP_R=(196, 38, 46), CHIP_K=(34, 30, 40), CHIP_G=(34, 140, 76), CHIP_E=(238, 232, 220), GOLD=(228, 182, 72),
           DGOLD=(150, 106, 36), SKIN=(150, 34, 42), DSKIN=(92, 18, 26), EYE=(255, 214, 70),
           WOOD=(70, 52, 90), DWOOD=(40, 30, 58), BELL=(196, 190, 220), DBELL=(118, 110, 150), MOON=(250, 236, 180),
           STAR=(240, 214, 120), PILLOW=(220, 214, 240), DPILLOW=(170, 160, 204), LACE=(250, 248, 255), TASSEL=(240, 200, 110))
PROPS = {}
def prop(fn): PROPS[fn.__name__] = fn; return fn

# ---------------------------------------------------------------------
@prop
def roulette_dice():
    """폭 3블록 주사위. roll(루프) + land_1~6(멈춘 뒤 그 눈이 위)."""
    def build():
        return Group('root', [0, 0, 0], [Group('die', [0, 24, 0], [
            C('die', [-24, 0, -24], [24, 48, 24], dice(PAL, 'DIE', 'PIP', 'DIEE', .1))])])
    # 위로 올라오는 면: north=1, south=6, east=3, west=4, up=2, down=5
    target = {1: (90, 0), 6: (-90, 0), 3: (0, 90), 4: (0, -90), 2: (0, 0), 5: (180, 0)}
    def anims(G):
        spec = [('roll', .8, 'loop', {'die': [R(0, 0, 0, 0, 'linear'), R(.8, -360, 0, 0, 'linear')]}),
                ('idle', 2.0, 'loop', {'die': [R(0), R(2.0)]})]
        for k, (x, z) in target.items():
            spec.append((f'land_{k}', 1.6, 'hold', {
                'die': [R(0, 0, 0, 0, 'linear'), R(.8, -540 + x, 0, z * .5, 'linear'), R(1.2, -720 + x - 8, 0, z), R(1.4, -720 + x + 4, 0, z), R(1.6, -720 + x, 0, z)],
                'root': [T(0), T(.4, 0, 10), T(.8, 0, 0), T(1.0, 0, 4), T(1.2, 0, 0), T(1.6)]}))
        return build_anims('animation.roulette_dice.', sorted(spec, key=lambda s: s[0]), G)
    return build, anims

# ---------------------------------------------------------------------
@prop
def roulette_chip_tower():
    """칩 12개 탑 (폭 1.5블록). drop(위에서 떨어짐) / topple(무너지며 흩어짐) / idle."""
    keys = ['CHIP_R', 'CHIP_K', 'CHIP_G']
    def chip_p(k):
        def p(img, face, r, rng):
            x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
            for ly in range(h):
                for lx in range(w):
                    if face in ('up', 'down'):
                        cx, cy = lx + .5 - w / 2, ly + .5 - h / 2; rr = math.hypot(cx, cy)
                        if rr > w / 2: img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                        th = math.degrees(math.atan2(cx, cy)) % 360
                        c = PAL['CHIP_E'] if (rr > w / 2 - 3 and int(th // 30) % 2 == 0) or abs(rr - w / 4) < .6 else PAL[k]
                    else:
                        c = PAL['CHIP_E'] if (lx // 3) % 2 == 0 else PAL[k]
                    img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.05, .05))))
        return p
    def build():
        chips = []
        for i in range(12):
            ox, oz = round(math.sin(i * 2.1) * 1.5), round(math.cos(i * 1.7) * 1.5)
            chips.append(Group(f'chip{i+1}', [ox, i * 3 + 1.5, oz], [
                C(f'chip{i+1}', [ox - 12, i * 3, oz - 12], [ox + 12, i * 3 + 3, oz + 12], chip_p(keys[i % 3]))]))
        return Group('root', [0, 0, 0], [Group('tower', [0, 0, 0], chips)])
    def anims(G):
        topple = {}
        for i in range(12):
            a = i * 0.9; d = 10 + i * 3
            topple[f'chip{i+1}'] = [T(0), T(.5, math.sin(a) * d * .5, 4 - i * .5, math.cos(a) * d * .5), T(1.0, math.sin(a) * d, -i * 3, math.cos(a) * d),
                                    R(0), R(1.0, 60 + i * 20, 0, 40 - i * 15), R(1.2, 90, 0, 0)]
        spec = [('idle', 2.0, 'loop', {'tower': [R(0, 0, 0, 0), R(2.0, 0, 0, 0)]}),
                ('drop', 1.2, 'once', {'root': [T(0, 0, 160, 0, 'linear'), T(.7, 0, 0, 0, 'linear'), T(.85, 0, 4), T(1.0, 0, 0), T(1.2)],
                                       'tower': [S(0), S(.7), S(.8, 1.15, .8, 1.15), S(1.0)]}),
                ('topple', 1.2, 'hold', topple)]
        return build_anims('animation.roulette_chip_tower.', spec, G)
    return build, anims

# ---------------------------------------------------------------------
@prop
def roulette_coin():
    """지름 2블록 동전. 앞면=악마 얼굴, 뒷면=룰렛 휠. flip_heads / flip_tails (튀어올라 돌다 기울어 결과를 보여주고 눕기)."""
    RD, CY = 16, 16
    def coin_p(box):
        (X0, Y0, _), (X1, Y1, _) = box
        def p(img, face, r, rng):
            x1, y1, x2, y2 = r
            for ly in range(y2 - y1):
                for lx in range(x2 - x1):
                    if face not in ('north', 'south'):
                        c = PAL['DGOLD'] if (lx + ly) % 2 else PAL['GOLD']
                        img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face])); continue
                    x = (X1 - lx - .5) if face == 'north' else (X0 + lx + .5); y = Y1 - ly - .5
                    dx, dy = x, y - CY; rr = math.hypot(dx, dy)
                    if rr > RD: img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                    c = PAL['GOLD']
                    if rr > RD - 2: c = PAL['DGOLD']
                    elif face == 'north':   # 앞면: 뿔 달린 악마 얼굴 (정면에서 본 좌우: 화면 x = -x)
                        sx, sy = -dx, dy
                        if abs(abs(sx) - 6) < 1.5 and 4 < sy < 11 - abs(abs(sx) - 6): c = PAL['DGOLD']     # 뿔
                        if math.hypot(sx, sy + 1) < 7.5: c = PAL['DGOLD']                                   # 얼굴
                        if math.hypot(abs(sx) - 3, sy - 1) < 1.3: c = PAL['GOLD']                             # 눈
                        if -6 < sy < -3 and abs(sx) < 4.5 - (sy + 6) * .3 and sy < -4 + abs(sx) * .2: c = PAL['GOLD']   # 웃음
                    else:                   # 뒷면: 6구역 휠
                        th = math.degrees(math.atan2(dx, dy)) % 360
                        if rr < 3: c = PAL['DGOLD']
                        elif abs(((th + 30) % 60) - 30) > 28.5 or abs(rr - (RD - 5)) < .6: c = PAL['DGOLD']
                    img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.06, .06))))
        return p
    def build():
        rows = []
        for y0 in range(-RD, RD, 4):
            y1 = y0 + 4; yi = 0 if y0 < 0 < y1 else min(abs(y0), abs(y1))
            hw = int(math.ceil(math.sqrt(RD * RD - yi * yi)))
            frm, to = [-hw, CY + y0, -1.5], [hw, CY + y1, 1.5]
            rows.append(C(f'coin_row{(y0 + RD) // 4 + 1}', frm, to, coin_p((frm, to))))
        return Group('root', [0, 0, 0], [Group('coin', [0, CY, 0], rows)])
    def flip(end_x):
        return {'root': [T(0), T(.7, 0, 70), T(1.4, 0, 0), T(1.55, 0, 4), T(1.7, 0, 0), T(2.4, 0, -CY + 1.5)],
                'coin': [R(0, 0, 0, 0, 'linear'), R(1.4, -1440, 0, 0, 'linear'), R(1.7, -1440 + end_x * .2), R(1.9, -1440 + end_x * .35, 0, 6),
                         R(2.1, -1440 + end_x * .2, 0, -4), R(2.4, -1440 + end_x)]}
    def anims(G):
        spec = [('idle', 3.0, 'loop', {'coin': [R(0, 0, 0, 0, 'linear'), R(3.0, 0, 360, 0, 'linear')]}),
                ('flip_heads', 2.4, 'hold', flip(90)),    # 앞면이 위로
                ('flip_tails', 2.4, 'hold', flip(-90))]   # 뒷면이 위로
        return build_anims('animation.roulette_coin.', spec, G)
    return build, anims

# ---------------------------------------------------------------------
@prop
def dream_bell():
    """꿈 맵 종탑의 종 (높이 약 3.5블록). idle / ring(칠 때 흔들림) / glow_idle(빛나는 종: 살짝 떠서 흔들림)."""
    def build():
        wood = sol(PAL, 'WOOD'); dwood = sol(PAL, 'DWOOD'); bell = grad(PAL, 'BELL', 'DBELL'); gold = sol(PAL, 'STAR')
        def post(s):
            m, P, Rt = side(s)
            return [C(f'{s}_post', [9, 0, -2], [12, 44, 2], wood, m), C(f'{s}_post_base', [8, 0, -4], [13, 3, 4], dwood, m),
                    C(f'{s}_post_cap', [8.5, 44, -2.5], [12.5, 46, 2.5], gold, m)]
        clapper = Group('clapper', [0, 34, 0], [C('clapper_rod', [-.5, 22, -.5], [.5, 34, .5], sol(PAL, 'DBELL')),
                                                 C('clapper_ball', [-1.5, 19, -1.5], [1.5, 22, 1.5], gold)])
        bell_g = Group('bell', [0, 44, 0], [
            C('bell_yoke', [-5, 40, -2], [5, 44, 2], dwood),
            C('bell_crown', [-3, 37, -3], [3, 40, 3], bell),
            C('bell_shoulder', [-5, 33, -5], [5, 37, 5], bell),
            C('bell_waist', [-6, 26, -6], [6, 33, 6], faces(bell, north=lambda i, r, g: [px(i, r, x, 2, col(PAL['MOON'])) for x in range(2, 10)])),
            C('bell_lip', [-8, 22, -8], [8, 26, 8], trim(PAL, 'BELL', 'STAR')), clapper])
        roof = [C('beam', [-14, 44, -3], [14, 48, 3], wood), C('roof_l', [-15, 48, -5], [0, 50, 5], dwood),
                C('roof_r', [0, 48, -5], [15, 50, 5], dwood), C('roof_top', [-9, 50, -4], [9, 52, 4], dwood),
                C('moon', [-2, 52, -1], [2, 58, 1], faces(sol(PAL, 'MOON'), north=lambda i, r, g: [px(i, r, 2, y, (0, 0, 0, 0)) for y in range(1, 5)])),
                C('moon_tip', [-3, 56, -1], [-1, 58, 1], sol(PAL, 'MOON'))]
        return Group('root', [0, 0, 0], [*post('right'), *post('left'), *roof, bell_g])
    def anims(G):
        spec = [('idle', 4.0, 'loop', {'bell': osc('rotation', 4, -2, 2, 0, 4, 2), 'clapper': osc('rotation', 4, -3, 3, 1, 4, 2)}),
                ('ring', 2.4, 'once', {'bell': [Z(0), R(.15, 0, 0, 32), R(.5, 0, 0, -26), R(.85, 0, 0, 18), R(1.2, 0, 0, -11), R(1.6, 0, 0, 6), R(2.0, 0, 0, -2), R(2.4)],
                                       'clapper': [Z(0), R(.2, 0, 0, -30), R(.55, 0, 0, 28), R(.9, 0, 0, -20), R(1.3, 0, 0, 10), R(2.4)]}),
                ('glow_idle', 2.0, 'loop', {'bell': [R(0, 0, 0, -6), R(1.0, 0, 0, 6), R(2.0, 0, 0, -6)],
                                            'clapper': [R(0, 0, 0, 8), R(1.0, 0, 0, -8), R(2.0, 0, 0, 8)]})]
        return build_anims('animation.dream_bell.', spec, G)
    return build, anims

# ---------------------------------------------------------------------
@prop
def dream_pillow():
    """베개 짓누르기용 거대 베개 (폭 3블록). drop(떨어져 꾹 눌림) / vanish(줄어들며 사라짐)."""
    def pillow_p():
        def p(img, face, r, rng):
            x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
            for ly in range(h):
                for lx in range(w):
                    c = PAL['PILLOW']
                    if face in ('up', 'down'):
                        cx, cy = (lx + .5) / w - .5, (ly + .5) / h - .5
                        c = lerp(PAL['PILLOW'], PAL['DPILLOW'], min(1, (cx * cx + cy * cy) * 3))
                        if (lx + ly) % 9 == 0: c = PAL['DPILLOW']
                    elif ly in (0, h - 1): c = PAL['LACE']
                    img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.03, .03))))
        return p
    def build():
        tassels = [C(f'tassel{i+1}', [x - 1, 6, z - 1], [x + 1, 10, z + 1], sol(PAL, 'TASSEL')) for i, (x, z) in
                   enumerate([(-24, -16), (24, -16), (-24, 16), (24, 16)])]
        return Group('root', [0, 0, 0], [Group('pillow', [0, 0, 0], [
            C('pillow_core', [-22, 0, -14], [22, 14, 14], pillow_p()),
            C('pillow_puff', [-18, 2, -16], [18, 12, 16], pillow_p()),
            C('pillow_side_r', [22, 2, -11], [24, 12, 11], pillow_p()),
            C('pillow_side_l', [-24, 2, -11], [-22, 12, 11], pillow_p()), *tassels])])
    def anims(G):
        spec = [('idle', 2.0, 'loop', {'pillow': [S(0), S(1.0, 1.02, .97, 1.02), S(2.0)]}),
                ('drop', 1.4, 'once', {'root': [T(0, 0, 120, 0, 'linear'), T(.6, 0, 0, 0, 'linear'), T(1.4)],
                                       'pillow': [S(0), S(.6), S(.7, 1.3, .45, 1.3), S(1.0, 1.15, .7, 1.15), S(1.4, 1.1, .8, 1.1),
                                                  R(0, 10, 0, -8), R(.6, 0, 0, 0)]}),
                ('vanish', .8, 'hold', {'pillow': [S(0, 1.1, .8, 1.1), S(.3, 1.2, 1.2, 1.2), S(.8, 0, 0, 0)]})]
        return build_anims('animation.dream_pillow.', spec, G)
    return build, anims

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    for i, n in enumerate(sys.argv[1:] or list(PROPS)):
        b, a = PROPS[n]()
        export(n, b(), a, 300 + i, O + n + '.bbmodel')
