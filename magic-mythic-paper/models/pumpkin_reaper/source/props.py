# 하베스트 보스 기믹 소품 8종 (reaper_pumpkin_<face> x5, reaper_lantern, reaper_candy, reaper_scarecrow)
import sys, os, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen import *

def mirror_x(v): return [-v[0], v[1], v[2]]
def C(name, frm, to, paint, mirror=False, **kw):
    if mirror:
        frm, to = [-to[0], frm[1], frm[2]], [-frm[0], to[1], to[2]]
        if 'origin' in kw: kw['origin'] = mirror_x(kw['origin'])
        if 'rot' in kw: kw['rot'] = [kw['rot'][0], -kw['rot'][1], -kw['rot'][2]]
    return Cube(name, frm, to, paint, **kw)

PUMP=(228,124,32); PUMP_D=(172,80,20); PUMP_L=(246,160,60); CARVE=(42,16,6); GLOW=(255,214,70)
STEM=(86,60,34); VINE=(104,112,44); LEAF=(84,120,40)
IRON=(52,48,58); IRON_L=(92,86,100); GLASS=(255,170,60,150); FLAME=(255,236,140)
CANDY=(255,120,40); CANDY_W=(255,244,214); WRAP=(240,110,200); WRAP_D=(170,60,150)
BURLAP=(176,146,98); BURLAP_D=(128,102,66); STRAW=(232,198,92); STRAW_D=(176,140,52)
SHIRT=(150,52,40); SHIRT_D=(96,30,26); PANTS=(70,78,110); PANTS_D=(46,52,78); HAT=(64,52,40); HAT_D=(38,30,24); ROPE=(150,124,70)
SIDES=('north','south','east','west')

def solidc(c, noise=0.08): return solid(c, noise)
def ribbed(base, dark, light, every=4):
    def p(img, face, rect, rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                lx=x-x1; c = dark if (face in SIDES or face=='up') and lx%every==0 else (light if lx%every==2 and rng.random()<0.5 else base)
                img.putpixel((x,y), col(c, SHADE[face]*(1+rng.uniform(-.06,.06))))
    return p

# ------------------------------------------------------------------ pumpkins (16x13 face)
FACES = {
 'smile': ["................","................","..##........##..","..###......###..","..####....####..","................",".......##.......",
           "................",".#............#.",".##.##.##.##.##.","..############..","....##....##....","................"],
 'angry': ["................",".#............#.","..##........##..","..####....####..","...###....###...","................",".......##.......",
           "................","..############..","..#.##.##.##.#..","...##########...","................","................"],
 'sad':   ["................","................","..###......###..","..####....####..","...##......##...","...#........#...","...#...##...#...",
           "................",".....######.....","....##....##....","...##......##...","................","................"],
 'blank': ["................","................","...##......##...","..####....####..","..####....####..","...##......##...","................",
           "................","................","...##########...","...##########...","................","................"],
 'surprised': ["................","..####....####..",".##..##..##..##.",".##..##..##..##.","..####....####..","................","................",
           "......####......",".....##..##.....",".....##..##.....","......####......","................","................"],
}
def pumpkin_body(face_rows):
    def p(img, face, rect, rng):
        if face != 'north': ribbed(PUMP, PUMP_D, PUMP_L)(img, face, rect, rng); return
        x1,y1,x2,y2=rect   # 얼굴 면: 결무늬 없이 고르게 + 파낸 곳 둘레를 어둡게 (표정이 멀리서도 읽히게)
        for y in range(y1,y2):
            for x in range(x1,x2): img.putpixel((x,y), col(PUMP, 1+rng.uniform(-.04,.04)))
        H,W=len(face_rows),len(face_rows[0])
        cut=lambda lx,ly: 0<=ly<H and 0<=lx<W and face_rows[ly][lx]=='#'
        for ly in range(H):
            for lx in range(W):
                if cut(lx,ly): px(img, rect, lx, ly, (*CARVE,255))
                elif any(cut(lx+dx,ly+dy) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): px(img, rect, lx, ly, col((120,52,12),1))
    return p
def face_glow(face_rows):
    def p(img, face, rect, rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2): img.putpixel((x,y),(0,0,0,0))
        if face=='north':
            for ly,row in enumerate(face_rows):
                for lx,ch in enumerate(row):
                    if ch=='#':
                        edge = any(not (0<=ly+dy<len(face_rows) and 0<=lx+dx<16 and face_rows[ly+dy][lx+dx]=='#') for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
                        px(img, rect, lx, ly, col((255,206,60) if edge else (255,246,190), 1.0))
    return p
def pumpkin(fname):
    rows = FACES[fname]
    body = Group('body', [0,0,0], [
        C('pumpkin', [-8,0,-8], [8,13,8], pumpkin_body(rows)),
        C('rib_side', [-9,1,-5], [9,12,5], ribbed(PUMP, PUMP_D, PUMP_L)),
        C('rib_back', [-5,1,-3], [5,12,9], ribbed(PUMP, PUMP_D, PUMP_L)),
        C('cap', [-5,13,-5], [5,14,5], solidc(PUMP_D)),
        Group('stem', [0,14,0], [C('stem', [-1,14,-1], [1,17,1], solidc(STEM)),
                                C('stem_tip', [-1,16,-1], [2,18,1], solidc(STEM))], [6,0,-14]),
        C('leaf', [2,13.5,-6], [7,14.5,-2], solidc(LEAF), rot=[0,-20,8], origin=[2,14,-4]),
        C('vine_a', [-6,13,3], [-2,14,4], solidc(VINE)),
        C('vine_b', [-9,9,3], [-8,13,4], solidc(VINE)),
        C('vine_c', [-7,13,-7], [-5,14,-4], solidc(VINE)),
        Group('face_glow', [0,6,-8], [C('face_glow', [-8,0,-8.12], [8,13,-8.12], face_glow(rows))]),
    ])
    return Group('root', [0,0,0], [body])
def pumpkin_anims(G, name):
    n=f'animation.{name}.'; cm='catmullrom'
    return [
        anim(n+'spawn', 1.0, 'once', {'root':[('position',0,0,-16,0),('position',0.55,0,1.5,0,cm),('position',0.75,0,-0.5,0,cm),('position',1.0,0,0,0,cm)],
                                       'body':[('rotation',0,0,0,-14),('rotation',0.4,0,0,10,cm),('rotation',0.7,0,0,-5,cm),('rotation',1.0,0,0,0,cm)]}, G),
        anim(n+'idle', 2.4, 'loop', {'body':[('scale',0,1,1,1),('scale',1.2,1.03,0.97,1.03,cm),('scale',2.4,1,1,1,cm)],
                                       'face_glow':[('scale',0,1,1,1),('scale',0.6,1.02,1.02,1),('scale',1.2,0.99,0.99,1),('scale',1.8,1.02,1.02,1),('scale',2.4,1,1,1)],
                                       'stem':[('rotation',0,0,0,0),('rotation',1.2,0,0,4,cm),('rotation',2.4,0,0,0,cm)]}, G),
        anim(n+'hit', 0.4, 'once', {'body':[('scale',0,1,1,1),('scale',0.08,1.12,0.88,1.12),('scale',0.2,0.95,1.06,0.95,cm),('scale',0.4,1,1,1,cm)]}, G),
    ]

# ------------------------------------------------------------------ lantern
def glass(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            lit = 1.15 if (y-y1) > (y2-y1)*0.5 else 1.0
            img.putpixel((x,y), col(GLASS, lit*(1+rng.uniform(-.08,.08)), 150))
def flame(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            k=(y-y1)/max(1,y2-y1-1)
            img.putpixel((x,y), col((255,int(236-80*(1-k)),int(140-100*(1-k))), 1.0))
def lantern():
    body = Group('body', [0,13,0], [
        C('base', [-4,0,-4], [4,1,4], solidc(IRON)),
        C('foot', [-3,-1,-3], [3,0,3], solidc(IRON_L)),
        *[C(f'post{i}', [x,1,z], [x+1,9,z+1], solidc(IRON_L)) for i,(x,z) in enumerate(((-4,-4),(3,-4),(-4,3),(3,3)))],
        C('glass', [-3,1,-3], [3,9,3], glass),
        Group('flame', [0,2,0], [C('flame', [-1,2,-1], [1,6,1], flame), C('wick', [-0.5,1,-0.5], [0.5,2,0.5], solidc((40,30,20)))]),
        C('cap', [-4,9,-4], [4,10,4], solidc(IRON)),
        C('roof1', [-3,10,-3], [3,11,3], solidc(IRON_L)),
        C('roof2', [-2,11,-2], [2,12,2], solidc(IRON)),
        C('bat_l', [-6,10,-0.5], [-3,11,0.5], solidc(IRON_L), rot=[0,0,-20], origin=[-3,10.5,0]),
        C('bat_r', [3,10,-0.5], [6,11,0.5], solidc(IRON_L), rot=[0,0,20], origin=[3,10.5,0]),
        C('handle_l', [-2,12,-0.5], [-1,15,0.5], solidc(IRON_L)),
        C('handle_r', [1,12,-0.5], [2,15,0.5], solidc(IRON_L)),
        C('handle_top', [-2,15,-0.5], [2,16,0.5], solidc(IRON_L)),
    ])
    sw = Group('swing', [0,16,0], [body])
    root = Group('root', [0,0,0], [sw])
    return root
def lantern_anims(G, name):
    n=f'animation.{name}.'; cm='catmullrom'
    return [
        anim(n+'spawn', 0.8, 'once', {'root':[('position',0,0,10,0),('position',0.45,0,-1,0,cm),('position',0.8,0,0,0,cm)],
                                      'swing':[('rotation',0,0,0,18),('rotation',0.45,0,0,-10,cm),('rotation',0.8,0,0,0,cm)]}, G),
        anim(n+'idle', 2.0, 'loop', {'swing':[('rotation',0,0,0,5),('rotation',1.0,0,0,-5,cm),('rotation',2.0,0,0,5,cm)],
                                     'flame':[('scale',0,1,1,1),('scale',0.3,1.15,1.25,1.15),('scale',0.6,0.9,0.85,0.9),('scale',1.0,1.1,1.2,1.1),('scale',1.4,0.95,0.9,0.95),('scale',2.0,1,1,1)]}, G),
    ]

# ------------------------------------------------------------------ candy
def stripes(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            c = CANDY_W if ((x-x1)+(y-y1)) % 4 < 2 else CANDY
            img.putpixel((x,y), col(c, SHADE[face]*(1+rng.uniform(-.04,.04))))
def wrap(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            c = WRAP_D if (y-y1)%2==0 else WRAP
            img.putpixel((x,y), col(c, SHADE[face]*(1+rng.uniform(-.06,.06)), 235))
def candy():
    spin = Group('spin', [0,3,0], [
        C('core', [-3,1,-2], [3,5,2], stripes),
        C('core_x', [-2,0,-2], [2,6,2], stripes),
        C('core_z', [-2,1,-3], [2,5,3], stripes),
        C('knot_l', [-4,2,-1], [-3,4,1], solidc(WRAP_D)),
        C('fan_l', [-7,0,-1], [-4,6,1], wrap),
        C('fan_l2', [-6,1,-2], [-4,5,2], wrap),
        C('knot_r', [3,2,-1], [4,4,1], solidc(WRAP_D)),
        C('fan_r', [4,0,-1], [7,6,1], wrap),
        C('fan_r2', [4,1,-2], [6,5,2], wrap),
    ])
    bob = Group('bob', [0,0,0], [spin])
    return Group('root', [0,0,0], [bob])
def candy_anims(G, name):
    n=f'animation.{name}.'; cm='catmullrom'
    return [
        anim(n+'spawn', 0.6, 'once', {'root':[('scale',0,0.2,0.2,0.2),('scale',0.3,1.3,1.3,1.3,cm),('scale',0.6,1,1,1,cm)]}, G),
        anim(n+'idle', 3.0, 'loop', {'spin':[('rotation',0,0,0,0),('rotation',1.5,0,180,0),('rotation',3.0,0,360,0)],
                                     'bob':[('position',0,0,1,0),('position',1.5,0,2.5,0,cm),('position',3.0,0,1,0,cm)]}, G),
    ]

# ------------------------------------------------------------------ scarecrow
def burlap(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            weave = ((x-x1)%2==0) ^ ((y-y1)%2==0)
            img.putpixel((x,y), col(BURLAP_D if weave and rng.random()<0.5 else BURLAP, SHADE[face]*(1+rng.uniform(-.08,.08))))
SACK_FACE = ["..........",
             ".#.#..#.#.",
             "..#....#..",
             ".#.#..#.#.",
             "..........",
             "....##....",
             "..........",
             ".########.",
             "..#.##.#..",
             ".........."]
def sack_head(img, face, rect, rng):
    burlap(img, face, rect, rng)
    if face == 'north':
        for ly,row in enumerate(SACK_FACE):
            for lx,ch in enumerate(row):
                if ch=='#': px(img, rect, lx, ly, (30,20,14,255))
SACK_GLOW = ["..........","..........","..........","..........","..........","..........","..........",
             "..######..","...#..#...",".........."]
def sack_glow(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2): img.putpixel((x,y),(0,0,0,0))
    if face=='north':
        for ly,row in enumerate(SACK_GLOW):
            for lx,ch in enumerate(row):
                if ch=='#': px(img, rect, lx, ly, col(GLOW, 1.0))
        for lx,ly in ((2,2),(7,2)): px(img, rect, lx, ly, col((255,140,40),1.0))
def plaid(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            lx,ly=x-x1,y-y1; c = SHIRT_D if (lx%4==0 or ly%4==0) else SHIRT
            if lx%4==0 and ly%4==0: c=(60,20,18)
            img.putpixel((x,y), col(c, SHADE[face]*(1+rng.uniform(-.05,.05))))
def straw(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            cut = face in SIDES and (y - y1) >= (y2-y1) - 1 - int(rng.random()*2)
            img.putpixel((x,y), col(STRAW_D if rng.random()<0.35 else STRAW, SHADE[face]*(1+rng.uniform(-.1,.1)), 0 if cut and rng.random()<0.6 else 255))
def pants(img, face, rect, rng):
    solid(PANTS, 0.08)(img, face, rect, rng)
    x1,y1,x2,y2=rect
    if face=='north' and x2-x1>=3 and y2-y1>=4:   # patch
        for y in range(y1+1,y1+3):
            for x in range(x1+1,x1+3): img.putpixel((x,y), col((150,120,60),1))
def scarecrow():
    def arm(side):
        m = side=='left'; P = lambda v: mirror_x(v) if m else v
        hand = Group(f'{side}_hand', P([12,22,0]), [
            C(f'{side}_straw_hand', [12,20.5,-1.5], [15,23.5,1.5], straw, m),
            C(f'{side}_straw_tuft', [14,19,-1], [16,22,1], straw, m)])
        fore = Group(f'{side}_forearm', P([8,22,0]), [
            C(f'{side}_sleeve2', [8,20.5,-2], [12,23.5,2], plaid, m), hand])
        return Group(f'{side}_arm', P([4,22,0]), [
            C(f'{side}_sleeve1', [4,20.5,-2], [8,23.5,2], plaid, m),
            C(f'{side}_cuff', [7,20,-2.5], [8,24,2.5], solidc(SHIRT_D), m), fore])
    def leg(side):
        m = side=='left'; P = lambda v: mirror_x(v) if m else v
        shin = Group(f'{side}_shin', P([2,7,0]), [
            C(f'{side}_shin', [0.5,2,-1.5], [3.5,7,1.5], pants, m),
            C(f'{side}_straw_foot', [0,0,-2.5], [4,2,1.5], straw, m)])
        return Group(f'{side}_leg', P([2,13,0]), [C(f'{side}_thigh', [0.5,7,-1.5], [3.5,13,1.5], pants, m), shin])
    head = Group('head', [0,24,0], [
        C('sack', [-4.5,24,-4.5], [4.5,33,4.5], sack_head),
        C('sack_top', [-3.5,33,-3.5], [3.5,34,3.5], burlap),
        C('neck_rope', [-3,24,-3.5], [3,25,3.5], solidc(ROPE)),
        C('neck_straw', [-4,22.5,-4], [4,24,4], straw),
        Group('face_glow', [0,28,-4.5], [C('face_glow', [-5,24,-4.62], [5,34,-4.62], sack_glow)]),
        Group('hat', [0,33,0], [
            C('hat_brim', [-7.5,33,-7.5], [7.5,34,7.5], solidc(HAT_D)),
            C('hat_band', [-4.5,34,-4.5], [4.5,35,4.5], solidc((140,60,30))),
            C('hat_crown', [-4,35,-4], [4,38,4], solidc(HAT)),
            C('hat_tip', [-2.5,38,-2.5], [2.5,40,2.5], solidc(HAT), rot=[0,0,-12], origin=[0,38,0]),
            C('hat_tip2', [-1,40,-1], [2,42,1], solidc(HAT_D), rot=[0,0,-28], origin=[0,40,0]),
            C('hat_straw', [-5,32,3], [5,33,6], straw)], [0,0,-8]),
    ])
    body = Group('body', [0,13,0], [
        C('torso', [-4,13,-2.5], [4,24,2.5], plaid),
        C('belt', [-4.5,13,-3], [4.5,14,3], solidc(ROPE)),
        C('waist_straw', [-4,11.5,-2.5], [4,13,2.5], straw),
        C('patch', [1,18,-2.7], [3,20,-2.5], solidc((70,90,60))),
        C('crossbar', [-14,21.5,-0.5], [14,22.5,0.5], solidc((110,78,44))),
        C('pole', [-0.5,13,2.5], [0.5,24,3.5], solidc((110,78,44))),
        head, arm('right'), arm('left'),
    ])
    return Group('root', [0,0,0], [body, leg('right'), leg('left')])
def scarecrow_anims(G, name):
    n=f'animation.{name}.'; cm='catmullrom'; L=0.9
    sw = lambda a: [('rotation',0,a,0,0,cm),('rotation',L/2,-a,0,0,cm),('rotation',L,a,0,0,cm)]
    return [
        anim(n+'spawn', 1.4, 'once', {'root':[('position',0,0,-34,0),('position',0.9,0,2,0,cm),('position',1.1,0,0,0,cm)],
                                       'body':[('rotation',0,0,0,-20),('rotation',0.8,0,0,12,cm),('rotation',1.1,0,0,-6,cm),('rotation',1.4,0,0,0,cm)],
                                       'head':[('rotation',0,30,0,0),('rotation',1.1,30,0,0),('rotation',1.3,-10,0,10,cm),('rotation',1.4,0,0,0,cm)]}, G),
        anim(n+'idle', 2.6, 'loop', {'body':[('rotation',0,0,0,2),('rotation',1.3,0,0,-2,cm),('rotation',2.6,0,0,2,cm)],
                                     'head':[('rotation',0,0,0,-6),('rotation',0.9,4,0,8,cm),('rotation',1.8,0,0,-4,cm),('rotation',2.6,0,0,-6,cm)],
                                     'hat':[('rotation',0,0,0,0),('rotation',1.3,-3,0,3,cm),('rotation',2.6,0,0,0,cm)],
                                     'right_hand':[('rotation',0,0,0,0),('rotation',1.3,0,0,-12,cm),('rotation',2.6,0,0,0,cm)],
                                     'left_hand':[('rotation',0,0,0,0),('rotation',1.3,0,0,12,cm),('rotation',2.6,0,0,0,cm)]}, G),
        anim(n+'walk', L, 'loop', {'right_leg':sw(26),'left_leg':sw(-26),
                                   'right_shin':[('rotation',0,0,0,0,cm),('rotation',0.45,0,0,0,cm),('rotation',0.675,-30,0,0,cm),('rotation',0.9,0,0,0,cm)],
                                   'left_shin':[('rotation',0,0,0,0,cm),('rotation',0.225,-30,0,0,cm),('rotation',0.45,0,0,0,cm),('rotation',0.9,0,0,0,cm)],
                                   'body':[('position',0,0,0,0,cm),('position',0.225,0,-0.8,0,cm),('position',0.45,0,0,0,cm),('position',0.675,0,-0.8,0,cm),('position',0.9,0,0,0,cm),
                                           ('rotation',0,0,0,5,cm),('rotation',0.45,0,0,-5,cm),('rotation',0.9,0,0,5,cm)],
                                   'head':[('rotation',0,0,0,-8,cm),('rotation',0.45,0,0,8,cm),('rotation',0.9,0,0,-8,cm)]}, G),
        anim(n+'attack', 0.9, 'once', {'right_arm':[('rotation',0,0,0,0),('rotation',0.3,0,40,30,cm),('rotation',0.45,0,-70,-10),('rotation',0.9,0,0,0,cm)],
                                       'body':[('rotation',0,0,0,0),('rotation',0.3,0,25,0,cm),('rotation',0.45,-8,-25,0),('rotation',0.9,0,0,0,cm)],
                                       'head':[('rotation',0,0,0,0),('rotation',0.4,-15,0,0,cm),('rotation',0.9,0,0,0,cm)]}, G),
        anim(n+'death', 1.6, 'hold', {'body':[('rotation',0,0,0,0),('rotation',0.3,-10,0,0,cm),('rotation',0.9,80,0,0,cm),('position',0,0,0,0),('position',0.9,0,-8,-3,cm)],
                                      'head':[('position',0,0,0,0),('position',0.5,0,3,0,cm),('position',1.2,0,-12,-10,cm),('rotation',0,0,0,0),('rotation',1.2,40,0,60,cm)],
                                      'hat':[('position',0,0,0,0),('position',0.6,0,6,2,cm),('position',1.4,0,-14,4,cm),('rotation',0,0,0,0),('rotation',1.4,-30,0,-80,cm)],
                                      'right_leg':[('rotation',0,0,0,0),('rotation',0.9,-60,0,10,cm)],'left_leg':[('rotation',0,0,0,0),('rotation',0.9,-50,0,-10,cm)],
                                      'face_glow':[('scale',0,1,1,1),('scale',1.0,1,1,1),('scale',1.4,0,0,0)]}, G),
    ]

if __name__ == '__main__':
    O = sys.argv[1] if len(sys.argv) > 1 else '/mnt/user-data/outputs/props/'
    os.makedirs(O, exist_ok=True)
    import gen; gen.HITBOX_JSON = O + 'hitboxes.json'
    if os.path.exists(gen.HITBOX_JSON): os.remove(gen.HITBOX_JSON)
    for f in FACES:
        nm = f'reaper_pumpkin_{f}'
        export(nm, pumpkin(f), lambda G, nm=nm: pumpkin_anims(G, nm), 11, O + nm + '.bbmodel', (1.1, 0.95, 0.6))
    export('reaper_lantern', lantern(), lambda G: lantern_anims(G, 'reaper_lantern'), 12, O + 'reaper_lantern.bbmodel', (0.5, 0.75, 0.5))
    export('reaper_candy', candy(), lambda G: candy_anims(G, 'reaper_candy'), 13, O + 'reaper_candy.bbmodel', (0.5, 0.45, 0.3))
    export('reaper_scarecrow', scarecrow(), lambda G: scarecrow_anims(G, 'reaper_scarecrow'), 14, O + 'reaper_scarecrow.bbmodel', (0.7, 2.6, 1.85))
