"""기믹 소품: 석상 / 찬송 석주 3종 / 빛의 핵 / 봉인진 / 천사의 손 / 장부 / 은총의 깃털 / 상환 증서"""
from common import *
import os

MARBLE = (226, 220, 206); VEIN = (160, 148, 128); GOLD = (230, 186, 78); GOLD_HI = (255, 238, 168)
SKIN = (246, 224, 206); ROBE = (238, 234, 224); PAGE = (232, 218, 182); INK = (60, 30, 30); LEDGER = (112, 30, 30)
IRON = (84, 80, 86); RED = (190, 30, 34); EMBER = (214, 74, 34); WOOD = (110, 72, 42)
mb = lambda: stone(MARBLE, VEIN)
g = lambda: gold(GOLD, GOLD_HI)

def statue_face(img, r, rng):
    for lx, ly in [(1, 3), (2, 3), (4, 3), (5, 3)]: dot(img, r, lx, ly, (150, 138, 120))
    for lx, ly in [(1, 4), (5, 4), (3, 5)]: dot(img, r, lx, ly, (110, 104, 100))   # 눈물 자국 같은 금

# ---------------- 1. 석상 (그림자에 숨는 엄폐물) ----------------
def build_statue():
    ped = Group('pedestal', [0, 0, 0], [C('plinth', [-10, 0, -8], [10, 2, 8], mb()), C('pedestal', [-9, 2, -7], [9, 8, 7], mb()),
                                        C('pedestal_lip', [-9.5, 8, -7.5], [9.5, 9, 7.5], mb()),
                                        C('plaque', [-3, 3, -8], [3, 6, -7], g())])
    def wingslab(side):
        m = side == 'left'
        k = [C(f'{side}_wing_main', [2, 12, 3], [10, 34, 6], mb(), m), C(f'{side}_wing_top', [3, 34, 3.5], [8, 38, 5.5], mb(), m),
             C(f'{side}_wing_tip', [8, 10, 3.5], [11, 26, 5.5], mb(), m)]
        for i in range(4): k.append(C(f'{side}_wing_ridge{i}', [3 + i * 2, 13 + i, 2], [4 + i * 2, 33, 3], mb(), m))
        return Group(f'{side}_wing', MP([2, 30, 4], m), k, MR([0, -12, 4], m))
    head = Group('statue_head', [0, 33, 0], [C('statue_head', [-3.5, 33, -3.5], [3.5, 40, 3.5], with_faces(mb(), north=statue_face)),
                                             C('statue_hair', [-4, 37, -4], [4, 41, 4], mb()), C('statue_veil', [-4, 30, 2], [4, 39, 4.5 + .5], mb())], [14, 0, 0])
    body = Group('statue_body', [0, 9, 0], [
        C('robe_low', [-6, 9, -4.5], [6, 20, 4.5], mb()), C('robe_mid', [-5, 20, -4], [5, 27, 4], mb()), C('chest', [-4.5, 27, -3.5], [4.5, 33, 3.5], mb()),
        C('praying_hands', [-1.5, 26, -6], [1.5, 31, -4], mb()), C('right_sleeve', [2, 24, -6], [5, 31, -2], mb()), C('left_sleeve', [-5, 24, -6], [-2, 31, -2], mb()),
        C('robe_fold0', [-3, 9, -5.5], [-2, 20, -4.5], mb()), C('robe_fold1', [2, 9, -5.5], [3, 18, -4.5], mb()),
        head, wingslab('right'), wingslab('left')])
    return Group('root', [0, 0, 0], [ped, body])
def anims_statue(G):
    n = 'animation.grace_statue.'
    crumble = {'statue_head': [('position', 0, 0, 0, 0), ('position', .4, 0, 1, 0, cm), ('position', 1.2, 4, -24, -6), ('rotation', 0, 0, 0, 0), ('rotation', 1.2, 70, 0, 50)],
               'right_wing': [('rotation', 0, 0, 0, 0), ('rotation', 1.2, 0, 0, -70, cm), ('position', 0, 0, 0, 0), ('position', 1.2, 4, -12, 0)],
               'left_wing': [('rotation', 0, 0, 0, 0), ('rotation', 1.3, 0, 0, 60, cm), ('position', 0, 0, 0, 0), ('position', 1.3, -4, -14, 0)],
               'statue_body': [('rotation', 0, 0, 0, 0), ('rotation', .2, 0, 0, 3), ('rotation', .35, 0, 0, -3), ('rotation', 1.5, 25, 0, 0, cm),
                               ('scale', 0, 1, 1, 1), ('scale', .5, 1, 1, 1), ('scale', 1.5, 1, .25, 1, cm)]}
    shake = {'root': [('rotation', 0, 0, 0, 0), ('rotation', .1, 0, 0, 2), ('rotation', .2, 0, 0, -2), ('rotation', .3, 0, 0, 1.5), ('rotation', .5, 0, 0, 0)]}
    return [anim(n + 'idle', 1, 'loop', {}, G), anim(n + 'shake', .5, 'once', shake, G), anim(n + 'crumble', 1.5, 'hold', crumble, G)]

# ---------------- 2. 찬송 석주 (낮은음/가운데음/높은음) ----------------
def note_glyph(cnt, colr):
    def f(img, r, rng):
        x1, y1, x2, y2 = r; w = x2 - x1
        xs = [w // 2] if cnt == 1 else ([1, w - 3] if cnt == 2 else [0, w // 2 - 1, w - 2])
        for i, x in enumerate(xs):
            hy = 7 - i % 2
            for dx in (0, 1):
                for dy in (0, 1): dot(img, r, x + dx, hy + dy, colr)
            for yy in range(1, hy): dot(img, r, x + 1, yy, colr)
        if cnt > 1:
            for xx in range(xs[0] + 1, xs[-1] + 2): dot(img, r, xx, 1, colr)
    return f
def build_pillar(cnt, colr):
    return Group('root', [0, 0, 0], [
        Group('pillar_base', [0, 0, 0], [C('base', [-7, 0, -7], [7, 3, 7], mb()), C('base_step', [-6, 3, -6], [6, 5, 6], mb())]),
        Group('pillar_shaft', [0, 5, 0], [C('shaft', [-4.5, 5, -4.5], [4.5, 36, 4.5], mb())] +
              [C(f'flute{i}', [x, 6, z], [x + 1, 35, z + 1], mb()) for i, (x, z) in enumerate([(-5.5, -2), (4.5, -2), (-2, 4.5), (1, 4.5), (4.5, 1), (-5.5, 1)])] +
              [C('rune_plate', [-3.5, 14, -5.5], [3.5, 26, -4.5], shaded(MARBLE)), C('rune_glyph', [-3, 15, -5.6], [3, 25, -5.6], decal(lambda u, v, rng: None) if False else
                                                                                                    with_faces(glow((0, 0, 0, 0)), north=note_glyph(cnt, colr + (255,)))),
               C('inscription_band', [-5, 10, -5], [5, 11, 5], g())]),
        Group('capital', [0, 36, 0], [C('capital', [-6, 36, -6], [6, 39, 6], mb()), C('capital_top', [-7, 39, -7], [7, 41, 7], mb()),
                                      Group('bell_gem', [0, 42.5, 0], [C('bell_gem', [-1.5, 41, -1.5], [1.5, 44, 1.5], glow(colr + (255,)))]),
                                      C('bell_gem_cap', [-2, 44, -2], [2, 45, 2], g())])])
def anims_pillar(G, nm):
    n = f'animation.{nm}.'
    ring = {'bell_gem': [('scale', 0, 1, 1, 1), ('scale', .15, 1.6, 1.6, 1.6, cm), ('scale', .8, 1, 1, 1, cm)],
            'capital': [('position', 0, 0, 0, 0), ('position', .12, 0, .8, 0, cm), ('position', .5, 0, 0, 0, cm)],
            'pillar_shaft': [('scale', 0, 1, 1, 1), ('scale', .1, 1.04, 1, 1.04), ('scale', .4, 1, 1, 1, cm)]}
    wrong = {'root': [('rotation', 0, 0, 0, 0), ('rotation', .08, 0, 0, 3), ('rotation', .16, 0, 0, -3), ('rotation', .24, 0, 0, 2), ('rotation', .32, 0, 0, -2), ('rotation', .5, 0, 0, 0)],
             'bell_gem': [('scale', 0, 1, 1, 1), ('scale', .2, .5, .5, .5), ('scale', .5, 1, 1, 1, cm)]}
    idle = {'bell_gem': [('rotation', 0, 0, 0, 0), ('rotation', 4, 0, 360, 0)]}
    return [anim(n + 'idle', 4, 'loop', idle, G), anim(n + 'ring', .8, 'once', ring, G), anim(n + 'wrong', .5, 'once', wrong, G)]

# ---------------- 3. 빛의 핵 ----------------
def build_core():
    cry = shaded((255, 246, 200), noise=.05, grad=.25)
    core = Group('crystal', [0, 22, 0], [C('crystal_mid', [-3, 19, -3], [3, 25, 3], cry), C('crystal_up', [-2, 25, -2], [2, 28, 2], cry),
                                          C('crystal_top', [-1, 28, -1], [1, 30, 1], cry), C('crystal_dn', [-2, 16, -2], [2, 19, 2], cry),
                                          C('crystal_bot', [-1, 14, -1], [1, 16, 1], cry), C('crystal_heart', [-1.5, 20.5, -3.2], [1.5, 23.5, -3.2], glow((255, 255, 255, 255)))])
    def ring(nm, rot):
        return Group(nm, [0, 22, 0], [C(nm + '_a', [-6, 21.5, -6], [6, 22.5, -5], g()), C(nm + '_b', [-6, 21.5, 5], [6, 22.5, 6], g()),
                                      C(nm + '_c', [-6, 21.5, -5], [-5, 22.5, 5], g()), C(nm + '_d', [5, 21.5, -5], [6, 22.5, 5], g()),
                                      C(nm + '_gem', [-.5, 21, -7], [.5, 23, -6], glow((255, 220, 120, 255)))], rot)
    return Group('root', [0, 0, 0], [core, ring('ring_outer', [30, 0, 15]), ring('ring_inner', [-25, 45, -20])])
def anims_core(G):
    n = 'animation.light_core.'
    idle = {'crystal': pos_wave(1, 2, 1) + [], 'ring_outer': [('rotation', 0, 0, 0, 0), ('rotation', 2, 0, 360, 0)], 'ring_inner': [('rotation', 0, 0, 0, 0), ('rotation', 2, 0, -360, 0)]}
    idle['crystal'] = merge({'crystal': idle['crystal']}, {'crystal': [('rotation', 0, 0, 0, 0), ('rotation', 2, 0, 180, 0)]})['crystal']
    hit = {'crystal': [('scale', 0, 1, 1, 1), ('scale', .08, .8, 1.2, .8), ('scale', .3, 1, 1, 1, cm), ('rotation', 0, 0, 0, 0), ('rotation', .08, 0, 0, 12), ('rotation', .3, 0, 0, 0, cm)]}
    brk = {'crystal': [('scale', 0, 1, 1, 1), ('scale', .2, 1.4, 1.4, 1.4, cm), ('scale', .5, 0, 0, 0)],
           'ring_outer': [('position', 0, 0, 0, 0), ('position', .8, 6, -16, 0, cm), ('rotation', 0, 0, 0, 0), ('rotation', .8, 80, 0, 0)],
           'ring_inner': [('position', 0, 0, 0, 0), ('position', .8, -6, -16, 2, cm), ('rotation', 0, 0, 0, 0), ('rotation', .8, 0, 0, 90)]}
    return [anim(n + 'idle', 2, 'loop', idle, G), anim(n + 'hit', .3, 'once', hit, G), anim(n + 'break', .8, 'hold', brk, G)]

# ---------------- 4. 봉인진 ----------------
def seal_fn(u, v, rng):
    x, y = u - .5, v - .5; r = math.hypot(x, y); a = math.atan2(y, x)
    c = (255, 214, 110, 230)
    if .46 < r < .49 or .36 < r < .375: return c
    if .38 < r < .45 and (math.sin(a * 24) > .6 and abs(math.sin((r - .38) * 60)) > .3): return (255, 200, 90, 200)
    for k in range(3):                                   # 삼각 봉인
        t = a - k * 2 * math.pi / 3 - math.pi / 2
        d = r * math.cos(((t + math.pi / 3) % (2 * math.pi / 3)) - math.pi / 3)
        if abs(d - .18) < .012 and r < .36: return c
    if r < .05: return c
    return None
def build_seal():
    kids = [Group('seal_disc', [0, 0, 0], [C('seal_disc', [-24, .15, -24], [24, .15, 24], decal(seal_fn))]),
            C('center_plate', [-4, 0, -4], [4, 1, 4], mb()), Group('center_gem', [0, 1.5, 0], [C('center_gem', [-1, 1, -1], [1, 2, 1], glow((255, 226, 140, 255)))])]
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4; x = round(17 * math.cos(a)); z = round(17 * math.sin(a))
        kids.append(Group(f'obelisk{i}', [x, 0, z], [C(f'obelisk{i}_base', [x - 2, 0, z - 2], [x + 2, 2, z + 2], mb()),
                                                     C(f'obelisk{i}', [x - 1, 2, z - 1], [x + 1, 9, z + 1], mb()),
                                                     C(f'obelisk{i}_tip', [x - .5, 9, z - .5], [x + .5, 11, z + .5], g()),
                                                     C(f'obelisk{i}_rune', [x - .5, 4, z - 1.1], [x + .5, 7, z - 1.1], glow((255, 214, 110, 255)))]))
    return Group('root', [0, 0, 0], kids)
def anims_seal(G):
    n = 'animation.seal_circle.'
    idle = {'seal_disc': [('rotation', 0, 0, 0, 0), ('rotation', 12, 0, 360, 0)]}
    act = {'seal_disc': [('rotation', 0, 0, 0, 0), ('rotation', 3, 0, 720, 0, cm)],
           'center_gem': [('position', 0, 0, 0, 0), ('position', 1.5, 0, 3, 0, cm), ('position', 3, 0, 3, 0), ('scale', 0, 1, 1, 1), ('scale', 1.5, 2, 2, 2, cm), ('scale', 3, 2, 2, 2)]}
    for i in range(4):
        act[f'obelisk{i}'] = [('position', 0, 0, 0, 0), ('position', .3 + .3 * i, 0, 0, 0), ('position', .7 + .3 * i, 0, 3, 0, cm), ('position', 3, 0, 3, 0)]
    return [anim(n + 'idle', 12, 'loop', idle, G), anim(n + 'activate', 3, 'hold', act, G)]

# ---------------- 5. 천사의 손 ----------------
def build_hand():
    sk = shaded(SKIN, grad=.1)
    def finger(nm, x, length, spread):
        k0 = Group(nm, [x + 1.5, 18, -2], [C(nm + '_base', [x, 18, -4], [x + 3, 26, 0], sk),
               Group(nm + '_tip', [x + 1.5, 26, -2], [C(nm + '_tip', [x + .5, 26, -3.5], [x + 2.5, 26 + length, -.5], sk),
                                                     C(nm + '_nail', [x + .5, 26 + length - 2, -4], [x + 2.5, 26 + length, -3.5 + .5], shaded((250, 236, 226)))], [-15, 0, 0])], [-20, 0, spread])
        return k0
    palm = Group('palm', [0, 12, 0], [C('palm', [-7, 8, -4], [7, 19, 1], sk), C('palm_pad', [-6, 9, -5], [6, 13, -4], sk),
                                      finger('index', 3.5, 6, -5), finger('middle', .5, 7, -1), finger('ring', -2.5, 6, 3), finger('pinky', -5.5, 4, 8),
                                      Group('thumb', [7, 11, -2], [C('thumb', [6, 11, -4], [9, 18, 0], sk), C('thumb_tip', [7, 18, -3.5], [9, 22, -.5], sk)], [-10, 0, -30])])
    wrist = Group('wrist', [0, 8, 0], [C('wrist', [-5, 0, -3.5], [5, 9, 1.5], sk), C('cuff', [-7, -2, -5], [7, 2, 3], g()),
                                       C('sleeve', [-8, -14, -6], [8, -2, 4], robe(ROBE, GOLD, 0, 2)), C('sleeve_rim', [-8.5, -4, -6.5], [8.5, -2, 4.5], g()), palm])
    root = Group('root', [0, 0, 0], [wrist])
    shift(root, 16)
    root.origin = [0, 0, 0]
    return root
def anims_hand(G):
    n = 'animation.angel_hand.'
    fingers = ['index', 'middle', 'ring', 'pinky']
    spawn = {'wrist': [('position', 0, 0, 40, 0), ('position', 1.4, 0, -1, 0, cm), ('position', 1.8, 0, 0, 0, cm)],
             'palm': [('rotation', 0, -30, 0, 0), ('rotation', 1.6, 6, 0, 0, cm), ('rotation', 2, 0, 0, 0, cm)]}
    idle = {'wrist': pos_wave(1.2, 3), 'palm': rot_wave(3, 3, 0, 1)}
    for i, f in enumerate(fingers): idle[f] = rot_wave(4, 3, 0, i * .5)
    grab = {f: [('rotation', 0, 0, 0, 0), ('rotation', .5, 75, 0, 0, cm), ('rotation', 1.2, 75, 0, 0)] for f in fingers}
    grab.update({f + '_tip': [('rotation', 0, 0, 0, 0), ('rotation', .5, 70, 0, 0, cm), ('rotation', 1.2, 70, 0, 0)] for f in fingers})
    grab['thumb'] = [('rotation', 0, 0, 0, 0), ('rotation', .5, 30, 0, 40, cm), ('rotation', 1.2, 30, 0, 40)]
    vanish = {'wrist': [('position', 0, 0, 0, 0), ('position', 1.5, 0, 40, 0, cm), ('scale', 0, 1, 1, 1), ('scale', 1.5, .2, .2, .2, cm)]}
    return [anim(n + 'spawn', 2, 'once', spawn, G), anim(n + 'idle', 3, 'loop', idle, G), anim(n + 'grab', 1.2, 'hold', grab, G), anim(n + 'vanish', 1.5, 'hold', vanish, G)]

# ---------------- 6. 장부 (하늘에 펼쳐지는 거대한 장부) ----------------
def ledger_page(img, r, rng):
    x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
    for y in range(2, h - 2, 2):
        for x in range(2, w - 2):
            if rng.random() < .6: dot(img, r, x, y, INK)
        if rng.random() < .35: dot(img, r, w - 3, y, RED)        # 붉은 숫자(빚)
def eye_emblem(img, r, rng):
    x1, y1, x2, y2 = r; cx, cy = (x2 - x1) // 2, (y2 - y1) // 2
    for dx in range(-5, 6):
        dy = int(2.5 * (1 - (dx / 5.5) ** 2))
        dot(img, r, cx + dx, cy - dy, GOLD); dot(img, r, cx + dx, cy + dy, GOLD)
    for dx in (-1, 0):
        for dy in (-1, 0): dot(img, r, cx + dx, cy + dy, (230, 50, 40))
def build_ledger():
    pg = with_faces(shaded(PAGE, noise=.03, grad=.04), up=ledger_page)
    def half(side):
        m = side == 'left'
        return Group(f'{side}_half', [0, 0, 0], [
            C(f'{side}_cover', [0, -1, -12], [20, 0, 12], with_faces(shaded(LEDGER), down=eye_emblem), m),
            C(f'{side}_pages', [.5, 0, -11.5], [19.5, 2, 11.5], pg, m),
            C(f'{side}_corner_a', [17, -1.5, -12.5], [20, .5, -9.5], g(), m), C(f'{side}_corner_b', [17, -1.5, 9.5], [20, .5, 12.5], g(), m),
            C(f'{side}_chain0', [19.5, -9, -11.5], [19.5, -1, -10.5], shaded(IRON), m), C(f'{side}_chain1', [19.5, -9, 10.5], [19.5, -1, 11.5], shaded(IRON), m)],
            MR([0, 0, 8], m))
    flip = Group('turning_page', [0, 2, 0], [C('turning_page', [0, 2, -11], [19, 2, 11], with_faces(shaded(PAGE, noise=.03), up=ledger_page, down=ledger_page))], [0, 0, 8])
    book = Group('book', [0, 40, 0], [half('right'), half('left'), flip,
                                      C('spine', [-1.5, -2, -12], [1.5, 1, 12], shaded(LEDGER)),
                                      C('ribbon0', [-.5, -10, -6], [.5, 2, -6], shaded(RED)), C('ribbon1', [-.5, -7, 4], [.5, 2, 4], shaded(GOLD)),
                                      C('lock', [-2, -3, -13], [2, 1, -12], g())], [-50, 0, 0])
    for c in all_cubes(book): pass
    shift(book, 0)
    for gname in ['right_half', 'left_half', 'turning_page']: pass
    _offset(book, 40)
    return Group('root', [0, 0, 0], [book])
def _offset(node, dy):
    for c in node.children:
        if isinstance(c, Cube):
            c.frm = [c.frm[0], c.frm[1] + dy, c.frm[2]]; c.to = [c.to[0], c.to[1] + dy, c.to[2]]; c.origin = [c.origin[0], c.origin[1] + dy, c.origin[2]]
        else:
            c.origin = [c.origin[0], c.origin[1] + dy, c.origin[2]]; _offset(c, dy)
def anims_ledger(G):
    n = 'animation.ledger.'
    spawn = {'right_half': [('rotation', 0, 0, 0, 82), ('rotation', .6, 0, 0, 82), ('rotation', 1.6, 0, 0, 0, cm), ('rotation', 2, 0, 0, 0)],
             'left_half': [('rotation', 0, 0, 0, -82), ('rotation', .6, 0, 0, -82), ('rotation', 1.6, 0, 0, 0, cm), ('rotation', 2, 0, 0, 0)],
             'turning_page': [('rotation', 0, 0, 0, 82), ('rotation', .6, 0, 0, 82), ('rotation', 1.6, 0, 0, 0, cm)],
             'book': [('position', 0, 0, 20, 0), ('position', 1.2, 0, 0, 0, cm), ('scale', 0, .3, .3, .3), ('scale', 1.2, 1, 1, 1, cm)]}
    idle = {'book': pos_wave(1.5, 4) , 'turning_page': rot_wave(3, 2, 2)}
    idle['book'] = merge({'book': idle['book']}, {'book': rot_wave(3, 4, 0, 1)})['book']
    flip = {'turning_page': [('rotation', 0, 0, 0, 0), ('rotation', .7, 0, 0, -170, cm), ('rotation', 1, 0, 0, -164, cm)]}
    burn = {'book': [('rotation', 0, 0, 0, 0), ('rotation', .3, 4, 0, 3), ('rotation', .6, -4, 0, -3), ('rotation', 2.5, 40, 30, 20, cm),
                     ('position', 0, 0, 0, 0), ('position', 2.5, 0, -20, 0, cm), ('scale', 0, 1, 1, 1), ('scale', 1.2, 1.05, 1.05, 1.05), ('scale', 2.5, 0, 0, 0, cm)],
            'right_half': [('rotation', 0, 0, 0, 0), ('rotation', 1.5, 0, 0, 60, cm), ('rotation', 2.5, 0, 0, 60)],
            'left_half': [('rotation', 0, 0, 0, 0), ('rotation', 1.5, 0, 0, -60, cm), ('rotation', 2.5, 0, 0, -60)]}
    return [anim(n + 'spawn', 2, 'once', spawn, G), anim(n + 'idle', 4, 'loop', idle, G), anim(n + 'flip', 1, 'once', flip, G), anim(n + 'burn', 2.5, 'hold', burn, G)]

# ---------------- 7. 은총의 깃털 / 8. 상환 증서 ----------------
def build_feather():
    fe = feather((250, 214, 110), (255, 246, 200), (210, 160, 60))
    return Group('root', [0, 0, 0], [Group('item', [0, 8, 0], [
        C('feather_vane', [-2, 4, -.5], [2, 15, .5], fe), C('feather_vane_low', [-1.5, 2, -.5], [1.5, 4, .5], fe),
        C('feather_quill', [-.5, 0, -.5], [.5, 4, .5], shaded((220, 170, 70))), C('feather_glint', [-1, 11, -.6], [0, 13, -.6], glow((255, 255, 240, 255)))], [0, 0, 18])])
def build_deed():
    pap = shaded(PAGE, noise=.03)
    return Group('root', [0, 0, 0], [Group('item', [0, 8, 0], [
        C('deed_roll', [-5, 6, -1.5], [5, 9, 1.5], with_faces(pap, north=ledger_page)), C('deed_end_r', [5, 5.5, -2], [6, 9.5, 2], shaded(WOOD)),
        C('deed_end_l', [-6, 5.5, -2], [-5, 9.5, 2], shaded(WOOD)), C('deed_sheet', [-4, 2, -1.6], [4, 6, -1.6], with_faces(pap, north=ledger_page)),
        C('deed_ribbon', [-.5, 1, -1.8], [.5, 10, -1.8], shaded(RED)), C('deed_seal', [-1.5, 3, -2.5], [1.5, 6, -1.5], shaded((170, 30, 30))),
        C('deed_seal_mark', [-.5, 4, -2.6], [.5, 5, -2.6], glow(GOLD + (255,)))], [0, 0, 10])])
def anims_item(nm):
    def f(G):
        return [anim(f'animation.{nm}.idle', 3, 'loop', merge({'item': [('rotation', 0, 0, 0, 0), ('rotation', 3, 0, 360, 0)]}, {'item': pos_wave(1.2, 3)}), G),
                anim(f'animation.{nm}.pickup', .6, 'hold', {'item': [('position', 0, 0, 0, 0), ('position', .6, 0, 10, 0, cm), ('scale', 0, 1, 1, 1), ('scale', .2, 1.3, 1.3, 1.3), ('scale', .6, 0, 0, 0, cm)]}, G)]
    return f

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    export('grace_statue', build_statue(), anims_statue, 3, O + 'grace_statue.bbmodel', (1.3, 2.6, 2.2))
    for nm, cnt, colr in [('hymn_pillar_low', 1, (255, 170, 60)), ('hymn_pillar_mid', 2, (130, 236, 150)), ('hymn_pillar_high', 3, (130, 196, 255))]:
        export(nm, build_pillar(cnt, colr), lambda G, nm=nm: anims_pillar(G, nm), 4, O + nm + '.bbmodel', (.9, 2.8, 2.4))
    export('light_core', build_core(), anims_core, 5, O + 'light_core.bbmodel', (.8, 2.0, 1.4))
    export('seal_circle', build_seal(), anims_seal, 6, O + 'seal_circle.bbmodel', (1.5, .2, .1))
    export('angel_hand', build_hand(), anims_hand, 7, O + 'angel_hand.bbmodel', (1.0, 2.6, 2.0))
    export('ledger', build_ledger(), anims_ledger, 8, O + 'ledger.bbmodel', (2.5, 3.0, 2.6))
    export('grace_feather', build_feather(), anims_item('grace_feather'), 9, O + 'grace_feather.bbmodel', (.5, 1.4, 1.0))
    export('repayment_deed', build_deed(), anims_item('repayment_deed'), 9, O + 'repayment_deed.bbmodel', (.6, 1.1, .8))
