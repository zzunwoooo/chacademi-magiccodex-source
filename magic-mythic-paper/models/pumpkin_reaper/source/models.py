from gen import *
import random

def mirror_x(v): return [-v[0],v[1],v[2]]
def C(name, frm, to, paint, mirror=False, **kw):
    if mirror:
        frm,to=[-to[0],frm[1],frm[2]],[-frm[0],to[1],to[2]]
        if 'origin' in kw: kw['origin']=mirror_x(kw['origin'])
        if 'rot' in kw: kw['rot']=[kw['rot'][0],-kw['rot'][1],-kw['rot'][2]]
    return Cube(name, frm, to, paint, **kw)

# ---------------- palette ----------------
ROBE=(86,56,98); ROBE_D=(52,32,64); TRIM=(138,128,58); TEAL=(84,214,182)
PUMP=(226,122,30); PUMP_D=(170,78,18); CARVE=(40,16,6); GLOW=(255,214,70)
BONE=(86,72,62); BONE_D=(54,44,38); WOOD=(94,58,36); IRON=(112,108,118)
BLADE=(168,164,180); EDGE=(222,218,232); CRACK=(255,140,30); STEM=(84,58,34); VINE=(110,108,46)
SHINY=dict(ROBE=(44,44,54), ROBE_D=(22,22,30), TRIM=(176,160,190), TEAL=(236,96,206),
           PUMP=(224,218,200), PUMP_D=(170,164,150), CARVE=(20,30,40), GLOW=(120,255,240),
           BONE=(196,188,172), BONE_D=(140,132,120), WOOD=(46,40,52), IRON=(60,58,70),
           BLADE=(70,66,86), EDGE=(150,240,236), CRACK=(110,240,255), STEM=(60,70,80), VINE=(90,110,130))
HITBOX=(1.6, 3.3, 2.75)
def apply_palette(p): globals().update(p)
G_=lambda n: globals()[n]

# ---------------- painters (colors looked up at paint time) ----------------
SIDES=('north','south','east','west')
def solidn(name, noise=0.08):
    def p(img, face, rect, rng): solid(G_(name),noise)(img,face,rect,rng)
    return p

def cloth(name, dname, cut=0, top=0, slit=None):
    """천: 세로 결 + 아래(또는 위) 끝을 들쭉날쭉하게 투명 처리. slit=(x0,x1) 가운데 찢어진 틈"""
    def p(img, face, rect, rng):
        base,dark=G_(name),G_(dname); x1,y1,x2,y2=rect; w=x2-x1; h=y2-y1
        cols=[rng.random() for _ in range(w)]
        for lx in range(w):
            c=dark if cols[lx]<0.28 else base
            cb=int(rng.random()**1.6*cut) if face in SIDES and cut else 0
            ct=int(rng.random()**1.6*top) if face in SIDES and top else 0
            for ly in range(h):
                a=255
                if ly>=h-cb or ly<ct: a=0
                if slit and face in ('north','south') and slit[0]<=lx<slit[1] and ly>h*0.35 and rng.random()<0.75: a=0
                f=SHADE[face]*(1+rng.uniform(-.1,.1))*(0.85 if ly>h-3 and cb==0 and cut else 1)
                img.putpixel((x1+lx,y1+ly),col(c,f,a))
    return p

def lining(name):
    def p(img, face, rect, rng):
        base=G_(name); x1,y1,x2,y2=rect; h=max(1,y2-y1)
        for y in range(y1,y2):
            for x in range(x1,x2):
                f=0.75+0.45*(y-y1)/h+rng.uniform(-.06,.06)
                img.putpixel((x,y),col(base,f))
    return p

FACE=["............",
      "............",
      ".#........#.",
      ".##......##.",
      ".###....###.",
      "..##....##..",
      ".....##.....",
      ".#........#.",
      ".##########.",
      "..##.##.##..",
      "...######..."]
def pumpkin(img, face, rect, rng):
    x1,y1,x2,y2=rect; w=x2-x1
    for y in range(y1,y2):
        for x in range(x1,x2):
            lx=x-x1; c=PUMP_D if (face in SIDES and lx%4==0) or (face=='up' and (lx%4==0)) else PUMP
            f=SHADE[face]*(1+rng.uniform(-.07,.07))
            img.putpixel((x,y),col(c,f))
    if face=='north':
        for ly,row in enumerate(FACE):
            for lx,ch in enumerate(row):
                if ch=='#': px(img,rect,lx,ly,(*CARVE,255))
def face_glow(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2): img.putpixel((x,y),(0,0,0,0))
    if face=='north':
        for ly,row in enumerate(FACE):
            for lx,ch in enumerate(row):
                if ch=='#':
                    inner = ly in (3,4,8)
                    c=col(GLOW,1.08 if inner else 0.92); px(img,rect,lx,ly,c)

def blade(img, face, rect, rng):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            c=EDGE if (face in ('east','west') and y==y2-1) else BLADE
            img.putpixel((x,y),col(c,SHADE[face]*(1+rng.uniform(-.09,.09))))

CRACK_PTS=[(-5.5,47.2),(-8,46.2),(-10.5,47),(-13,45.6),(-15.5,45.8),(-18,44),(-19.8,42.6),(-21.2,40.4),(-22.6,38.6),(-23.4,36)]
def crack_plane(z0,z1,y0,y1):
    def p(img, face, rect, rng):
        x1,y1_,x2,y2=rect
        for y in range(y1_,y2):
            for x in range(x1,x2): img.putpixel((x,y),(0,0,0,0))
        if face not in ('east','west'): return
        w=x2-x1
        pts=[]
        for (za,ya),(zb,yb) in zip(CRACK_PTS,CRACK_PTS[1:]):
            for i in range(12):
                t=i/12; pts.append((za+(zb-za)*t, ya+(yb-ya)*t))
        for z,y in pts:
            u = (z1-z) if face=='east' else (z-z0)
            v = (y1-y)
            lx,ly=int(u),int(v)
            if 0<=lx<w and 0<=ly<y2-y1_:
                img.putpixel((x1+lx,y1_+ly),col(CRACK,1.0))
    return p

# ---------------- build ----------------
def build():
    def arm(side, holding):
        m=side=='left'; P=lambda v: mirror_x(v) if m else v; s=side
        hand_kids=[C(f'{s}_palm',[8,13,-2],[11,16,1],solidn('BONE'),m),
                   C(f'{s}_wrist',[9,16,-1],[10,19,0],solidn('BONE_D'),m)]
        if holding:
            for i,y in enumerate((14,12.5,11)):
                hand_kids.append(C(f'{s}_grip{i}',[8,y-1,-5],[11,y,-4],solidn('BONE'),m))
            hand_kids.append(C(f'{s}_thumb',[7,13,-4],[8,15,-2],solidn('BONE_D'),m))
        else:
            for i,x in enumerate((7.5,9,10.5)):
                hand_kids.append(C(f'{s}_finger{i}',[x,9,-2],[x+1,13,-1],solidn('BONE'),m,rot=[0,0,(i-1)*12],origin=P([x+.5,13,-1.5])))
                hand_kids.append(C(f'{s}_claw{i}',[x,6,-2],[x+1,9,-1],solidn('BONE_D'),m,rot=[-40,0,(i-1)*12],origin=P([x+.5,9,-1.5])))
            hand_kids.append(C(f'{s}_thumb',[7,11,-3],[8,14,-2],solidn('BONE_D'),m))
            hand_kids.append(C(f'{s}_thumb_claw',[7,9,-4],[8,11,-3],solidn('BONE_D'),m))
        hand=Group(f'{s}_hand',P([9.5,17,-0.5]),hand_kids)
        if holding: hand.children.append(scythe())
        fore=Group(f'{s}_forearm',P([9.5,25,0]),[
            C(f'{s}_sleeve_low',[6,17,-4],[13,25,4],cloth('ROBE','ROBE_D',cut=5),m),
            Group(f'{s}_sleeve_glow',P([9.5,20,0]),[C(f'{s}_sleeve_lining',[7,18,-3],[12,23,3],lining('TEAL'),m)]),
            hand],[-20 if holding else -30,0,0])
        return Group(f'{s}_arm',P([9,33,0]),[
            C(f'{s}_sleeve_up',[7,25,-3],[12,34,3],cloth('ROBE','ROBE_D'),m), fore],[18,0,18] if not m else [35,0,-34])
    head=Group('head',[0,37,0],[
        C('pumpkin',[-6,37,-6],[6,48,6],pumpkin),
        C('pumpkin_rib',[-7,38,-3],[7,47,3],pumpkin),
        C('pumpkin_cap',[-4,48,-4],[4,49,4],solidn('PUMP_D')),
        Group('stem',[0,49,0],[
            C('stem',[-1,49,-1],[1,53,1],solidn('STEM')),
            C('stem_tip',[0,52,-1],[3,54,1],solidn('STEM'))],[0,0,-14]),
        C('vine_a',[-5,48,2],[-1,49,3],solidn('VINE')),
        C('vine_b',[-7,43,3],[-6,48,4],solidn('VINE')),
        C('vine_c',[2,48,-5],[5,49,-4],solidn('VINE')),
        C('vine_leaf',[4,47,-6],[6,48,-4],solidn('VINE')),
        Group('face_glow',[0,42,-6],[C('face_glow',[-6,37,-6.15],[6,48,-6.15],face_glow)])])
    collar=Group('collar',[0,34,0],[
        C('collar_back',[-10,32,4],[10,47,6],cloth('ROBE','ROBE_D',top=7)),
        C('collar_back2',[-8,40,6],[8,49,7],cloth('ROBE_D','ROBE',top=6)),
        C('collar_side',[-12,31,-5],[-10,44,5],cloth('ROBE','ROBE_D',top=6),rot=[0,0,-12],origin=[-11,32,0]),
        C('collar_side',[-12,31,-5],[-10,44,5],cloth('ROBE','ROBE_D',top=6),True,rot=[0,0,-12],origin=[-11,32,0]),
        C('collar_wing',[-16,33,-3],[-13,45,3],cloth('ROBE_D','ROBE',top=6),rot=[0,0,-28],origin=[-13,33,0]),
        C('collar_wing',[-16,33,-3],[-13,45,3],cloth('ROBE_D','ROBE',top=6),True,rot=[0,0,-28],origin=[-13,33,0]),
        C('collar_front',[-8,32,-6],[8,36,-4],cloth('ROBE','ROBE_D',cut=2)),
        C('neck_rope',[-6,35,-6.5],[6,36,-5.5],solidn('TRIM')),
        C('rope_tail_l',[-5,28,-7],[-4,35,-6],cloth('TRIM','VINE',cut=2)),
        C('rope_tail_r',[4,29,-7],[5,35,-6],cloth('TRIM','VINE',cut=2)),
        C('amulet',[-1,30,-7],[1,33,-6],solidn('TRIM')),
        C('shoulder',[-13,28,-5],[-7,34,5],cloth('ROBE','ROBE_D',cut=4)),
        C('shoulder',[-13,28,-5],[-7,34,5],cloth('ROBE','ROBE_D',cut=4),True)])
    robe_front=Group('robe_front',[0,25,-4],[
        C('robe_front_l',[-8,3,-5],[0,25,-4],cloth('ROBE','ROBE_D',cut=7,slit=(4,8))),
        C('robe_front_r',[0,1,-5],[8,25,-4],cloth('ROBE','ROBE_D',cut=7,slit=(0,4))),
        Group('robe_glow',[0,14,-3.5],[C('robe_lining',[-6,3,-4],[6,22,-3],lining('TEAL'))])],[8,0,0])
    robe_back=Group('robe_back',[0,25,4],[
        C('robe_back',[-9,1,4],[9,25,5],cloth('ROBE','ROBE_D',cut=9)),
        C('robe_trail',[-6,0,5],[6,14,6],cloth('ROBE','ROBE_D',cut=6))],[-10,0,0])
    def side_robe(sd):
        m=sd=='left'
        return Group(f'robe_{sd}',mirror_x([8,25,0]) if m else [8,25,0],[
            C(f'robe_{sd}',[8,2,-4],[9,25,4],cloth('ROBE','ROBE_D',cut=8),m),
            C(f'robe_{sd}_flap',[9,6,-3],[10,22,3],cloth('ROBE_D','ROBE',cut=6),m)],[0,0,10 if not m else -10])
    strands=Group('robe_strands',[0,12,-5],rot=[10,0,0],children=[
        C('strand_a',[-7,-1,-6],[-5,9,-5],cloth('ROBE_D','ROBE',cut=4)),
        C('strand_b',[-3,0,-6],[-2,7,-5],cloth('ROBE','ROBE_D',cut=3)),
        C('strand_c',[2,-1,-6],[4,8,-5],cloth('ROBE_D','ROBE',cut=4)),
        C('strand_d',[6,1,-6],[7,9,-5],cloth('ROBE','ROBE_D',cut=3))])
    body=Group('body',[0,22,0],[
        C('chest',[-7,24,-4],[7,35,4],cloth('ROBE','ROBE_D')),
        C('belt',[-7.5,26,-4.5],[7.5,27,4.5],solidn('TRIM')),
        C('belt_knot',[1,24,-5.5],[3,27,-4.5],solidn('TRIM')),
        C('skirt_core',[-7,10,-4],[7,24,4],solidn('ROBE_D')),
        collar, head, robe_front, robe_back, side_robe('right'), side_robe('left'), strands,
        arm('right',True), arm('left',False)])
    return Group('root',[0,0,0],[body])

def scythe():
    return Group('scythe',[9.5,13,-4.5],rot=[0,0,-18],children=[
        C('shaft',[8.5,2,-5.5],[10.5,46,-3.5],solidn('WOOD',.1)),
        C('pommel',[8,0,-6],[11,2,-3],solidn('IRON')),
        C('band_a',[8,9,-6],[11,10,-3],solidn('IRON')),
        C('band_b',[8,21,-6],[11,22,-3],solidn('IRON')),
        C('band_c',[8,37,-6],[11,38,-3],solidn('IRON')),
        C('wrap',[8,30,-6],[11,32,-3],solidn('TRIM')),
        C('wrap_tail',[9,25,-6.5],[10,30,-5.5],solidn('TRIM')),
        C('socket',[7.5,43,-7],[11.5,49,-2],solidn('IRON')),
        C('socket_bolt',[7,45,-5],[12,47,-4],solidn('BLADE')),
        C('back_hook',[9,45,-2],[10,48,1],blade),
        C('blade_1',[9,44,-13],[10,49,-7],blade),
        C('blade_2',[9,42,-18],[10,47,-13],blade),
        C('blade_3',[9,39,-21],[10,44,-18],blade),
        C('blade_4',[9,35,-23],[10,40,-21],blade),
        C('blade_5',[9,31,-24],[10,36,-23],blade),
        C('blade_tip',[9,28,-24],[10,31,-23.5],blade) if False else C('blade_tip',[9,28,-25],[10,31,-24],blade),
        Group('scythe_glow',[9.5,42,-15],[
            C('crack_w',[8.95,34,-25],[8.95,49,-5],crack_plane(-25,-5,34,49)),
            C('crack_e',[10.05,34,-25],[10.05,49,-5],crack_plane(-25,-5,34,49))])])
