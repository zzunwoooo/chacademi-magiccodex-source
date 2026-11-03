"""시간의 망령 (time_wraith) — 1인 던전 기믹형 보스
가슴 모래시계 감옥 / 부서진 문자판 후광(시침·분침) / 초침 검 / 진자 사슬 / 문자판 가면 / 부유하는 찢긴 로브
"""
import gen, math, os
from gen import *

# ---------- 대형 몹용 패커 ----------
def pack2(cubes):
    for W,H in [(128,128),(256,128),(256,256),(512,256),(512,512)]:
        x=y=rowh=0; ok=True
        for c in sorted(cubes, key=lambda c:(-(c.size[1]+c.size[2]), -(c.size[0]+c.size[2]))):
            w,h,d=c.size; bw,bh=2*(w+d), d+h
            if bw>W: ok=False; break
            if x+bw>W: x=0; y+=rowh; rowh=0
            if y+bh>H: ok=False; break
            c.uv=[x,y]; x+=bw; rowh=max(rowh,bh)
        if ok: return W,H
    raise Exception('too big')
gen.pack=pack2

def mx(v): return [-v[0],v[1],v[2]]
def C(name, frm, to, paint, m=False, rot=None, origin=None):
    for a,b in zip(frm,to): assert abs((b-a)-round(b-a))<1e-6, (name,frm,to)
    if m:
        frm,to=[-to[0],frm[1],frm[2]],[-frm[0],to[1],to[2]]
        if origin: origin=mx(origin)
        if rot: rot=[rot[0],-rot[1],-rot[2]]
    return Cube(name, list(frm), list(to), paint, rot=rot, origin=origin)
def G(name, origin, kids, rot=None, m=False):
    if m:
        origin=mx(origin)
        if rot: rot=[rot[0],-rot[1],-rot[2]]
    return Group(name, list(origin), kids, rot)

# ---------- 팔레트 ----------
P=dict(
  ROBE=(46,42,92), ROBE_D=(28,25,60), ROBE_L=(84,78,140),
  VOID=(14,12,26), BRASS=(190,146,62), BRASS_D=(120,84,36),
  IVORY=(226,216,190), IVORY_D=(150,138,112),
  GLOW=(120,255,236), SAND=(255,190,84), SAND_D=(214,132,44), GLASS=(150,190,200),
  CHAIN=(96,96,110), BLADE=(64,70,92), EDGE=(150,240,255), LEATHER=(58,36,30))
SHINY=dict(
  ROBE=(220,214,230), ROBE_D=(160,150,184), ROBE_L=(250,246,255),
  VOID=(40,22,58), BRASS=(232,150,150), BRASS_D=(164,88,104),
  IVORY=(36,30,48), IVORY_D=(80,64,100),
  GLOW=(255,120,220), SAND=(200,140,255), SAND_D=(140,80,210), GLASS=(230,210,255),
  CHAIN=(200,190,214), BLADE=(230,226,240), EDGE=(255,150,230), LEATHER=(120,70,110))
ECHO=dict(   # 잔상(과거의 몸) — 같은 모델, 창백한 청백색
  ROBE=(120,170,196), ROBE_D=(76,118,150), ROBE_L=(176,220,236),
  VOID=(40,70,96), BRASS=(170,220,236), BRASS_D=(100,150,176),
  IVORY=(220,246,255), IVORY_D=(140,190,210),
  GLOW=(255,255,255), SAND=(200,250,255), SAND_D=(140,210,230), GLASS=(220,246,255),
  CHAIN=(130,180,200), BLADE=(110,160,190), EDGE=(255,255,255), LEATHER=(80,120,150))
BASE=dict(P)
def apply_palette(p): P.clear(); P.update(BASE); P.update(p)

HITBOX=(2.6, 7.4, 6.0)

# ---------- 페인터 (팔레트는 그릴 때 조회) ----------
def rgba(c,f=1.0,a=255): return (clamp(c[0]*f),clamp(c[1]*f),clamp(c[2]*f),a)
def fill(img,rect,face,key,rng,noise=0.07):
    x1,y1,x2,y2=rect
    for y in range(y1,y2):
        for x in range(x1,x2):
            img.putpixel((x,y),rgba(P[key],SHADE[face]*(1+rng.uniform(-noise,noise))))

def cloth(key='ROBE', rag=0, runes=False):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; h=y2-y1
        fill(img,rect,face,key,rng)
        # 세로 주름
        for x in range(x1,x2):
            if rng.random()<0.3:
                for y in range(y1,y2):
                    if rng.random()<0.85: img.putpixel((x,y),rgba(P['ROBE_D'],SHADE[face]*rng.uniform(.9,1.05)))
        if face in ('up','down'): return
        # 아래로 갈수록 모래빛으로 바램
        for y in range(y1,y2):
            t=(y-y1)/max(1,h-1)
            if t>0.6:
                for x in range(x1,x2):
                    if rng.random()<(t-0.6)*1.2:
                        img.putpixel((x,y),rgba(P['ROBE_L'],SHADE[face]*rng.uniform(.85,1)))
        if runes:   # 금빛 눈금 무늬
            for _ in range((x2-x1)*h//220):
                rx=rng.randrange(x1,x2); ry=rng.randrange(y1,max(y1+1,y2-3))
                for k in range(rng.choice([1,2,3])):
                    if ry+k<y2: img.putpixel((rx,ry+k),rgba(P['SAND'],1.0))
        if rag:     # 찢긴 끝: 완전 투명
            for x in range(x1,x2):
                cut=rng.randint(0,rag)
                for y in range(y2-cut,y2): img.putpixel((x,y),(0,0,0,0))
    return p

def skin(key='VOID'):
    def p(img,face,rect,rng):
        fill(img,rect,face,key,rng,0.12)
        x1,y1,x2,y2=rect
        if face not in ('up','down'):
            for _ in range((x2-x1)*(y2-y1)//40):   # 금 간 잿빛 균열
                x=rng.randrange(x1,x2); y=rng.randrange(y1,y2)
                img.putpixel((x,y),rgba(P['GLOW'],0.55))
    return p

def metal(key='BRASS', rivets=False):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect
        fill(img,rect,face,key,rng,0.06)
        for x in range(x1,x2):
            for y in range(y1,y2):
                if (y==y2-1 or x==x2-1) and (x2-x1>2 and y2-y1>2):
                    img.putpixel((x,y),rgba(P[key],SHADE[face]*0.7))
                elif (y==y1) and face!='down' and rng.random()<0.5:
                    img.putpixel((x,y),rgba(P[key],SHADE[face]*1.25))
        if rivets and x2-x1>=4 and y2-y1>=3:
            for lx in (1,x2-x1-2): px(img,rect,lx,1,rgba(P['BRASS_D'],0.8))
    return p

def glow(key='GLOW'):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                img.putpixel((x,y),rgba(P[key],rng.uniform(.95,1.05)))
    return p

def bulb(glass_rows=0, key='SAND'):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                if face in ('up','down') or y-y1>=glass_rows:
                    c=P[key] if rng.random()>0.25 else P['SAND_D']
                    img.putpixel((x,y),rgba(c,rng.uniform(.95,1.08)))
                else:
                    img.putpixel((x,y),rgba(P['GLASS'],SHADE[face]*rng.uniform(.95,1.05)))
        if face=='up' and glass_rows:
            for y in range(y1,y2):
                for x in range(x1,x2): img.putpixel((x,y),rgba(P['GLASS'],1.0))
    return p

def ring(key='BRASS', hole=1):
    """큰 면 가운데 구멍(완전 투명)."""
    def p(img,face,rect,rng):
        metal(key)(img,face,rect,rng)
        if face in ('north','south'):
            x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
            cx,cy=(w-1)/2,(h-1)/2
            for ly in range(h):
                for lx in range(w):
                    d=math.hypot(lx-cx,ly-cy)
                    if d>max(w,h)/2+0.2: px(img,rect,lx,ly,(0,0,0,0))
                    elif d<=hole: px(img,rect,lx,ly,(0,0,0,0))
    return p

def link(axis='ns'):
    def p(img,face,rect,rng):
        metal('CHAIN')(img,face,rect,rng)
        if (axis=='ns' and face in ('north','south')) or (axis=='ew' and face in ('east','west')):
            x1,y1,x2,y2=rect
            for ly in range(1,y2-y1-1):
                for lx in range(1,x2-x1-1): px(img,rect,lx,ly,(0,0,0,0))
    return p

def gear(key='BRASS_D'):
    def p(img,face,rect,rng):
        metal(key)(img,face,rect,rng)
        if face in ('north','south'):
            x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1; cx,cy=(w-1)/2,(h-1)/2
            for ly in range(h):
                for lx in range(w):
                    a=math.atan2(ly-cy,lx-cx); d=math.hypot(lx-cx,ly-cy)
                    tooth=(math.cos(a*8)>0)
                    if d>w/2-(0 if tooth else 1)+0.1 or d<1: px(img,rect,lx,ly,(0,0,0,0))
    return p

def halo_seg():
    def p(img,face,rect,rng):
        metal('BRASS')(img,face,rect,rng)
        if face in ('north','south'):
            x1,y1,x2,y2=rect; w=x2-x1
            for ly in range(y2-y1-1): px(img,rect,w//2,ly,rgba(P['VOID'],1))
            px(img,rect,1,0,rgba(P['SAND'],1)); px(img,rect,w-2,0,rgba(P['SAND'],1))
    return p

def mask_face():
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
        fill(img,rect,face,'IVORY',rng,0.05)
        if face!='north': return
        cx,cy=(w-1)/2,(h-1)/2+1.5
        for ly in range(h):
            for lx in range(w):
                d=math.hypot(lx-cx,ly-cy)
                if d>w/2+0.4: px(img,rect,lx,ly,(0,0,0,0))
                elif d>w/2-0.8: px(img,rect,lx,ly,rgba(P['IVORY_D'],1))
        for a in range(0,360,30):   # 문자판 눈금
            r=w/2-1.6; lx=int(round(cx+r*math.sin(math.radians(a)))); ly=int(round(cy-r*math.cos(math.radians(a))))
            if 0<=ly<h: px(img,rect,lx,ly,rgba(P['BRASS_D'],0.8))
        # 금: 오른쪽 위에서 가운데로
        x,y=w-3,0
        while y<h-2 and x>2:
            px(img,rect,x,y,rgba(P['VOID'],1)); y+=1
            if rng.random()<0.55: x-=1
        # 눈구멍 테두리
        for lx in range(1,6): px(img,rect,lx,4,rgba(P['VOID'],1)); px(img,rect,lx,7,rgba(P['VOID'],1))
        for lx in range(7,11): px(img,rect,lx,4,rgba(P['VOID'],1)); px(img,rect,lx,6,rgba(P['VOID'],1))
    return p

def jaw_face():
    def p(img,face,rect,rng):
        fill(img,rect,face,'IVORY',rng,0.05)
        x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
        if face=='north':
            for lx in range(1,w-1): px(img,rect,lx,0,rgba(P['VOID'],1))
            for lx in range(1,w-1,2): px(img,rect,lx,1,rgba(P['VOID'],1))   # 꿰맨 이빨
            for lx in (0,w-1):
                for ly in range(h-1,h): px(img,rect,lx,ly,(0,0,0,0))
            for lx in range(w):
                if lx<2 or lx>w-3: px(img,rect,lx,h-1,(0,0,0,0))
    return p

def blade_p():
    def p(img,face,rect,rng):
        fill(img,rect,face,'BLADE',rng,0.06)
        x1,y1,x2,y2=rect
        if face in ('north','south'):
            for y in range(y1,y2):
                img.putpixel((x1,y),rgba(P['EDGE'],1)); img.putpixel((x2-1,y),rgba(P['EDGE'],0.9))
            for y in range(y1+2,y2,6):   # 초 눈금
                img.putpixel(((x1+x2)//2,y),rgba(P['SAND'],1))
    return p

def dial_plane():
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1; cx,cy=(w-1)/2,(h-1)/2
        for ly in range(h):
            for lx in range(w):
                d=math.hypot(lx-cx,ly-cy)
                c=(0,0,0,0) if d>w/2+0.3 else rgba(P['SAND'],rng.uniform(.95,1.05))
                px(img,rect,lx,ly,c)
        for ly in range(int(cy)+1): px(img,rect,int(cx),ly,rgba(P['VOID'],1))
        for lx in range(int(cx),w-1): px(img,rect,lx,int(cy)+1,rgba(P['VOID'],1))
    return p

# ---------- 모델 ----------
def finger_set(side, m, curl, spread, claw_key):
    kids=[]
    xs=[15.5,16.5,17.5,18.5]
    for i,x in enumerate(xs):
        tip=G(f'{side}_finger{i}_tip',[x+.5,22,-1],[
            C(f'{side}_finger{i}_b',[x,17,-1.5],[x+1,22,-.5],skin('VOID'),m),
            C(f'{side}_claw{i}',[x,14,-1.5],[x+1,17,-.5],metal(claw_key),m)],[curl[1],0,0],m)
        kids.append(G(f'{side}_finger{i}',[x+.5,27,-1],[
            C(f'{side}_finger{i}_a',[x,22,-1.5],[x+1,27,-.5],skin('VOID'),m), tip],[curl[0],0,spread[i]],m))
    kids.append(G(f'{side}_thumb',[15.5,30,-2],[
        C(f'{side}_thumb_a',[14.5,25,-3],[15.5,30,-2],skin('VOID'),m),
        C(f'{side}_thumb_claw',[14.5,23,-3],[15.5,25,-2],metal(claw_key),m)],[20,0,-15],m))
    return kids

def arm(side):
    m=side=='left'
    if side=='right':
        extra=[G('blade',[17.5,24,-1],[
            C('grip',[16.5,18,-2],[18.5,30,0],metal('LEATHER')),
            C('counterweight',[14,30,-1.5],[21,37,-.5],ring('BRASS',1.2)),
            C('pommel',[17,37,-1.5],[18,39,-.5],metal('BRASS')),
            C('blade_collar',[15.5,14,-2],[19.5,18,0],metal('BRASS',True)),
            C('blade_collar_b',[16,12,-1.5],[19,14,-.5],metal('BRASS_D')),
            C('blade',[16,-24,-1.5],[19,12,-.5],blade_p()),
            C('blade_spine',[17,-20,-2],[18,10,0],metal('BRASS_D')),
            C('blade_tip',[16.5,-28,-1.5],[18.5,-24,-.5],blade_p()),
            C('blade_point',[17,-32,-1.5],[18,-28,-.5],glow('EDGE')),
            Group('fx_blade_tip',[17.5,-32,-1],[]),
        ],[15,0,-8])]
        curl=(55,60)
    else:
        links=[]
        for i in range(3):
            y=27-i*4
            links.append(C(f'chain_link{i}',[16,y-4,-1.5],[19,y,-.5],link('ns'),m) if i%2==0
                         else C(f'chain_link{i}',[17,y-4,-2.5],[18,y,.5],link('ew'),m))
        bob=[C('bob_a',[13.5,7,-2],[21.5,13,0],metal('BRASS'),m),
             C('bob_b',[14.5,6,-2],[20.5,14,0],metal('BRASS'),m),
             C('bob_c',[15.5,5,-2],[19.5,15,0],metal('BRASS_D'),m),
             C('bob_d',[12.5,8,-2],[22.5,12,0],metal('BRASS_D'),m),
             C('bob_cap',[16.5,15,-1.5],[18.5,16,-.5],metal('BRASS'),m),
             C('bob_dial',[14.5,7,-2.1],[20.5,13,-2.1],dial_plane(),m),
             C('bob_dial_back',[14.5,7,0.1],[20.5,13,0.1],dial_plane(),m),
             G('fx_pendulum',[17.5,10,-1],[],None,m)]
        extra=[G('pendulum',[17.5,27,-1],links+[G('pendulum_bob',[17.5,15,-1],bob,None,m)],[-62,0,-28],m)]
        curl=(10,15)
    hand=G(f'{side}_hand',[17.5,33,0],[
        C(f'{side}_palm',[15.5,27,-2],[19.5,33,1],skin('VOID'),m),
        C(f'{side}_knuckle',[15,30,-2.5],[20,32,-1.5],metal('BRASS_D'),m),
        *finger_set(side,m,curl,[-6,-2,2,6],'BRASS_D'), *extra],[12,0,0],m)
    fore=G(f'{side}_forearm',[17.5,52,0],[
        C(f'{side}_forearm',[15.5,33,-2],[19.5,52,2],skin('VOID'),m),
        C(f'{side}_bracer',[15,39,-2.5],[20,47,2.5],metal('BRASS',True),m),
        C(f'{side}_bracer_band',[14.5,43,-3],[20.5,45,3],metal('BRASS_D'),m),
        C(f'{side}_cuff',[14.5,47,-3.5],[20.5,53,3.5],cloth('ROBE',3),m),
        hand],[38,0,0],m)
    return G(f'{side}_arm',[17,72,0],[
        C(f'{side}_pauldron',[11,68,-6],[23,76,6],metal('BRASS',True),m),
        C(f'{side}_pauldron_rim',[12,65,-7],[24,68,7],metal('BRASS_D'),m),
        C(f'{side}_pauldron_gear',[14,70,-7.5],[20,76,-6.5],gear('BRASS_D'),m),
        C(f'{side}_sleeve',[13,52,-5],[22,66,5],cloth('ROBE',5,True),m),
        C(f'{side}_upper_arm',[15,50,-2.5],[20,66,2.5],skin('VOID'),m),
        fore],[6,0,22],m)

def build():
    # --- 머리 ---
    halo_kids=[]
    for k in range(12):
        if k in (4,5): continue
        halo_kids.append(Group(f'halo_seg{k}',[0,92,12],[C(f'halo_seg{k}',[-3.5,104,11],[3.5,107,13],halo_seg())],[0,0,30*k]))
    halo_kids+=[
        Group('halo_shard',[0,92,12],[C('halo_shard',[-3,108,11],[3,110,13],halo_seg(),rot=[0,0,25],origin=[0,109,12])],[0,0,150]),
        C('halo_hub',[-1.5,90.5,11],[1.5,93.5,13],metal('BRASS')),
        Group('halo_minute',[0,92,12],[C('minute_hand',[-0.5,92,11.5],[0.5,103,12.5],metal('BRASS_D')),
                                       C('minute_tip',[-1,101,11.5],[1,103,12.5],glow('SAND'))],[0,0,-40]),
        Group('halo_hour',[0,92,12],[C('hour_hand',[-0.5,92,11.2],[0.5,99,12.2],metal('BRASS_D')),
                                     C('hour_tip',[-1.5,97,11.2],[1.5,99,12.2],metal('BRASS'))],[0,0,115]),
    ]
    halo=Group('halo',[0,92,12],halo_kids)
    jaw=Group('jaw',[0,86,-7.5],[C('jaw',[-5,82,-8],[5,86,-7],jaw_face())])
    head=Group('head',[0,79,-1],[
        C('neck',[-3,76,-3],[3,82,3],skin('VOID')),
        C('hood_void',[-7,82,-6],[7,98,6],glow('VOID')),
        C('hood_top',[-9,98,-9],[9,101,9],cloth('ROBE')),
        C('hood_side_r',[7,82,-8],[10,99,8],cloth('ROBE',2)),
        C('hood_side_l',[-10,82,-8],[-7,99,8],cloth('ROBE',2)),
        C('hood_back',[-8,82,6],[8,99,9],cloth('ROBE',2,True)),
        C('hood_brim',[-9,95,-12],[9,99,-9],cloth('ROBE_D')),
        C('hood_brim_r',[7,83,-12],[10,96,-8],cloth('ROBE_D',3)),
        C('hood_brim_l',[-10,83,-12],[-7,96,-8],cloth('ROBE_D',3)),
        C('hood_peak',[-3,100,5],[3,104,11],cloth('ROBE')),
        C('hood_peak2',[-1,102,10],[1,105,14],cloth('ROBE_D')),
        C('hood_trim',[-9,99,-12.5],[9,100,-11.5],metal('BRASS')),
        C('mask',[-6,86,-8],[6,98,-7],mask_face()),
        jaw,
        C('eye_l',[-5,89.5,-8.1],[-2,91.5,-8.1],glow('GLOW')),
        C('eye_r',[2,90,-8.1],[4,91,-8.1],glow('GLOW')),
        C('eye_streak',[-4,86,-8.1],[-3,89,-8.1],glow('SAND')),
        halo, Group('fx_eye',[-3.5,90.5,-9],[]),
    ])
    # --- 모래시계 감옥 ---
    hg=Group('hourglass',[0,60,-9.5],[
        C('hg_top',[-6,69,-13],[6,71,-6],metal('BRASS',True)),
        C('hg_bottom',[-6,49,-13],[6,51,-6],metal('BRASS',True)),
        C('hg_finial_t',[-1,71,-10.5],[1,73,-8.5],metal('BRASS_D')),
        C('hg_finial_b',[-1,47,-10.5],[1,49,-8.5],metal('BRASS_D')),
        C('hg_pillar_fr',[5,51,-13],[6,69,-12],metal('BRASS_D')),
        C('hg_pillar_fl',[-6,51,-13],[-5,69,-12],metal('BRASS_D')),
        C('hg_pillar_br',[5,51,-7],[6,69,-6],metal('BRASS_D')),
        C('hg_pillar_bl',[-6,51,-7],[-5,69,-6],metal('BRASS_D')),
        C('hg_upper_wide',[-4,63,-12],[4,68,-7],bulb(2)),
        C('hg_upper_narrow',[-2,60,-11],[2,63,-8],bulb(0)),
        C('hg_neck',[-0.5,58,-10],[0.5,60,-9],glow('SAND')),
        C('hg_lower_narrow',[-2,55,-11],[2,58,-8],bulb(2)),
        C('hg_lower_wide',[-4,51,-12],[4,55,-7],bulb(1)),
        Group('fx_hourglass',[0,60,-12],[]),
    ])
    torso=[
        C('chest_core',[-11,46,-7],[11,72,7],skin('VOID')),
        C('coat_back',[-12,46,6],[12,74,9],cloth('ROBE',0,True)),
        C('coat_side_r',[11,46,-7],[13,74,7],cloth('ROBE')),
        C('coat_side_l',[-13,46,-7],[-11,74,7],cloth('ROBE')),
        C('lapel_r',[6,46,-8],[12,72,-7],cloth('ROBE_D')),
        C('lapel_l',[-12,46,-8],[-6,72,-7],cloth('ROBE_D')),
        C('lapel_trim_r',[6,46,-8.5],[7,72,-7.5],metal('BRASS')),
        C('lapel_trim_l',[-7,46,-8.5],[-6,72,-7.5],metal('BRASS')),
        C('mantle',[-14,70,-8],[14,76,8],cloth('ROBE',0,True)),
        C('collar_back',[-10,74,5],[10,87,9],cloth('ROBE_D')),
        C('collar_r',[8,74,-6],[11,85,6],cloth('ROBE_D')),
        C('collar_l',[-11,74,-6],[-8,85,6],cloth('ROBE_D')),
        C('collar_trim',[-10,87,5],[10,88,9],metal('BRASS')),
        C('belt',[-13,42,-10],[13,46,10],metal('BRASS_D',True)),
        C('belt_buckle',[-3,41,-11],[3,47,-10],ring('BRASS',0.8)),
        C('belt_gear',[-12,33,-11],[-5,40,-10],gear('BRASS')),
        C('belt_gear2',[-6,30,-10.5],[-2,34,-9.5],gear('BRASS_D')),
        C('belt_chain',[-11,40,-10.5],[-10,42,-9.5],metal('CHAIN')),
        hg,
    ]
    # --- 찢긴 로브 하단 ---
    skirt=[
        C('skirt_core',[-11,20,-7],[11,44,7],skin('VOID')),
        Group('skirt_front',[0,44,-8],[C('skirt_front',[-12,12,-9],[12,44,-7],cloth('ROBE',9,True))]),
        Group('skirt_back',[0,44,8],[C('skirt_back',[-13,8,7],[13,44,9],cloth('ROBE',10,True))]),
        Group('skirt_r',[13,44,0],[C('skirt_r',[12,14,-8],[14,44,8],cloth('ROBE',8,True))]),
        Group('skirt_l',[-13,44,0],[C('skirt_l',[-14,14,-8],[-12,44,8],cloth('ROBE',8,True))]),
    ]
    # 넓게 퍼지는 아랫단 (종 모양 실루엣)
    skirt+=[
        Group('hem_front',[0,26,-10],[C('hem_front',[-15,6,-11],[15,26,-9],cloth('ROBE_D',8,True))]),
        Group('hem_back',[0,26,10],[C('hem_back',[-16,2,9],[16,26,11],cloth('ROBE_D',10,True))]),
        Group('hem_r',[15,26,0],[C('hem_r',[14,6,-10],[16,26,10],cloth('ROBE_D',8,True))]),
        Group('hem_l',[-15,26,0],[C('hem_l',[-16,6,-10],[-14,26,10],cloth('ROBE_D',8,True))]),
        C('hem_trim_f',[-15,25,-11.5],[15,26,-10.5],metal('BRASS')),
        C('hem_trim_b',[-16,25,10.5],[16,26,11.5],metal('BRASS')),
    ]
    tat=[(-10,-9.5),(-3,-9.5),(5,-9.5),(-9,8.5),(2,8.5),(8,8.5)]
    for i,(x,z) in enumerate(tat):
        top=14 if z<0 else 10
        skirt.append(Group(f'tatter{i}',[x+2,top+2,z],[C(f'tatter{i}',[x,top-14,z-0.5],[x+4,top+2,z+0.5],cloth('ROBE_D',6))]))
    def back_hand(side, length, ang, tilt):
        m=side=='l'
        return G(f'back_hand_{side}',[4,70,9],[
            C(f'back_hand_{side}_base',[2,68,8],[6,72,12],metal('BRASS_D',True),m),
            C(f'back_hand_{side}_shaft',[3,72,9],[5,72+length,11],metal('BRASS'),m),
            C(f'back_hand_{side}_ring',[1,72+length//3,9.5],[7,78+length//3,10.5],ring('BRASS',1.2),m),
            C(f'back_hand_{side}_blade',[2.5,72+length,9.5],[5.5,72+length+6,10.5],metal('BRASS'),m),
            C(f'back_hand_{side}_tip',[3.5,72+length+6,9.5],[4.5,72+length+9,10.5],glow('SAND'),m),
        ],[tilt,0,-ang],m)
    torso.append(Group('back_hands',[0,70,9],[back_hand('r',40,40,-22), back_hand('l',28,58,-16)]))
    body=Group('body',[0,44,0],torso+skirt+[head, arm('right'), arm('left')])
    shift(body,6)
    return Group('root',[0,0,0],[body])

def shift(node, dy):
    node.origin=[node.origin[0],node.origin[1]+dy,node.origin[2]]
    for c in node.children:
        if isinstance(c,Cube):
            c.frm=[c.frm[0],c.frm[1]+dy,c.frm[2]]; c.to=[c.to[0],c.to[1]+dy,c.to[2]]
            c.origin=[c.origin[0],c.origin[1]+dy,c.origin[2]]
        else: shift(c,dy)

# ---------- 애니메이션 ----------
cm='catmullrom'
def R(t,x,y,z,i=cm): return ('rotation',t,x,y,z,i)
def Pz(t,x,y,z,i=cm): return ('position',t,x,y,z,i)
def S(t,x,y,z,i=cm): return ('scale',t,x,y,z,i)
def loop(vals, L, ch=R):
    n=len(vals); return [ch(L*i/n,*v) for i,v in enumerate(vals)]+[ch(L,*vals[0])]

def idle_tracks(L, amp=1.0):
    return {
      'body':[Pz(0,0,0,0),Pz(L/2,0,-1.6*amp,0),Pz(L,0,0,0)],
      'head':[R(0,2,0,0),R(L/2,-2,3,0),R(L,2,0,0)],
      'halo_minute':[R(0,0,0,0,'linear'),R(L,0,0,-360,'linear')],
      'halo_hour':[R(0,0,0,0,'linear'),R(L,0,0,-30,'linear')],
      'pendulum':[R(0,0,0,14),R(L/2,0,0,-14),R(L,0,0,14)],
      'right_arm':[R(0,0,0,0),R(L/2,-3,0,2),R(L,0,0,0)],
      'left_arm':[R(0,0,0,0),R(L/2,-3,0,-2),R(L,0,0,0)],
      'skirt_front':[R(0,0,0,0),R(L/2,3,0,0),R(L,0,0,0)],
      'skirt_back':[R(0,0,0,0),R(L/2,-3,0,0),R(L,0,0,0)],
      **{f'tatter{i}':[R(0,0,0,0),R(L*(0.25+0.1*i)%L or 0.1,(6 if i<3 else -6),0,3*(-1)**i),R(L,0,0,0)] for i in range(6)},
    }

def anims(Gs):
    n='animation.time_wraith.'; A=[]
    def a(name,L,lp,tr):
        # 서 있는 본(body/head)은 +x가 뒤로 젖힘 → 설계값(+x=앞으로 숙임) 부호 맞춤
        for b in ('body','head'):
            if b in tr: tr[b]=[(k[0],k[1],-k[2])+tuple(k[3:]) if k[0]=='rotation' else k for k in tr[b]]
        A.append(anim(n+name,L,lp,tr,Gs))
    T=lambda t,v: [R(t,*v)]
    a('spawn',2.4,'once',{
        'root':[Pz(0,0,-40,0),Pz(1.2,0,-6,0),Pz(1.6,0,2,0),Pz(2.0,0,0,0)],
        'body':[R(0,45,0,0),R(1.2,30,0,0),R(1.7,-14,0,0),R(2.2,0,0,0)],
        'head':[R(0,40,0,0),R(1.4,30,0,0),R(1.8,-25,0,0),R(2.4,0,0,0)],
        'jaw':[R(0,0,0,0),R(1.6,0,0,0),R(1.8,28,0,0),R(2.2,0,0,0)],
        'right_arm':[R(0,0,0,-30),R(1.3,0,0,-20),R(1.8,-20,0,25),R(2.4,0,0,0)],
        'left_arm':[R(0,0,0,30),R(1.3,0,0,20),R(1.8,-20,0,-25),R(2.4,0,0,0)],
        'halo':[S(0,0,0,0),S(1.0,0.1,0.1,0.1),S(1.7,1.25,1.25,1.25),S(2.0,1,1,1)],
        'halo_minute':[R(0,0,0,0,'linear'),R(2.0,0,0,1080,'linear')],
        'halo_hour':[R(0,0,0,0,'linear'),R(2.0,0,0,360,'linear')],
        'hourglass':[R(0,0,0,0),R(1.6,0,0,0),R(2.2,0,0,180),R(2.21,0,0,0,'step')] if False else [R(0,0,0,0),R(1.5,0,0,0),R(2.2,0,0,180)],
    })
    # 등장 끝에 모래시계를 180도 뒤집음 = 시작 자세와 동일 형태(대칭)라 이어짐
    a('idle',3.0,'loop',idle_tracks(3.0))
    L=1.6
    a('walk',L,'loop',{
        'body':[R(0,14,0,0),R(L/2,16,0,0),R(L,14,0,0)],
        'root':[Pz(0,0,0,0),Pz(L/4,0,-1.2,0),Pz(L/2,0,0,0),Pz(3*L/4,0,-1.2,0),Pz(L,0,0,0)],
        'head':[R(0,-10,0,0),R(L/2,-8,0,0),R(L,-10,0,0)],
        'right_arm':[R(0,-14,0,4),R(L/2,-18,0,6),R(L,-14,0,4)],
        'left_arm':[R(0,-14,0,-4),R(L/2,-18,0,-6),R(L,-14,0,-4)],
        'pendulum':[R(0,-15,0,6),R(L/2,-22,0,-6),R(L,-15,0,6)],
        'skirt_front':[R(0,-14,0,0),R(L/2,-20,0,0),R(L,-14,0,0)],
        'skirt_back':[R(0,-18,0,0),R(L/2,-24,0,0),R(L,-18,0,0)],
        'skirt_r':[R(0,0,0,4),R(L/2,0,0,-2),R(L,0,0,4)],
        'skirt_l':[R(0,0,0,-4),R(L/2,0,0,2),R(L,0,0,-4)],
        **{f'tatter{i}':[R(0,-24,0,0),R(L*(0.3+0.12*i),-34,0,4*(-1)**i),R(L,-24,0,0)] for i in range(6)},
        'halo_minute':[R(0,0,0,0,'linear'),R(L,0,0,-360,'linear')],
    })
    # 기본 공격 = 초침 베기 1타
    a('attack',1.0,'once',{
        'body':[R(0,0,0,0),R(.35,-6,-25,0),R(.5,14,30,0),R(1,0,0,0)],
        'right_arm':[R(0,0,0,0),R(.35,140,0,30),R(.5,40,-30,-10,'linear'),R(.65,30,-40,-15),R(1,0,0,0)],
        'right_forearm':[R(0,0,0,0),R(.35,-30,0,0),R(.5,0,0,0),R(1,0,0,0)],
        'blade':[R(0,0,0,0),R(.35,30,0,0),R(.5,0,0,0),R(1,0,0,0)],
        'head':[R(0,0,0,0),R(.35,-8,-15,0),R(.5,10,15,0),R(1,0,0,0)],
        'root':[Pz(0,0,0,0),Pz(.5,0,0,-6),Pz(1,0,0,0)],
    })
    a('death',3.0,'hold',{
        'halo_minute':[R(0,0,0,0,'linear'),R(1.6,0,0,-1440,'linear'),R(1.8,0,0,-1440)],
        'halo_hour':[R(0,0,0,0,'linear'),R(1.6,0,0,-360,'linear')],
        'halo':[R(0,0,0,0),R(1.2,0,0,0),R(1.8,40,0,25),Pz(0,0,0,0),Pz(1.2,0,0,0),Pz(2.2,0,-30,4),S(0,1,1,1),S(2.0,1.1,1.1,1.1),S(2.6,0,0,0)],
        'hourglass':[R(0,0,0,0),R(1.2,0,0,0),R(1.6,0,0,90),R(2.0,0,0,180)],
        'jaw':[R(0,0,0,0),R(.4,32,0,0),R(1.2,30,0,0),R(1.8,10,0,0)],
        'head':[R(0,0,0,0),R(.4,-30,0,0),R(1.2,-25,0,6),R(2.0,40,0,10)],
        'body':[R(0,0,0,0),R(.4,-12,0,0),R(1.2,-10,0,0),R(2.2,35,0,6)],
        'right_arm':[R(0,0,0,0),R(.4,-30,0,40),R(1.2,-20,0,35),R(2.2,10,0,-5)],
        'left_arm':[R(0,0,0,0),R(.4,-30,0,-40),R(1.2,-20,0,-35),R(2.2,10,0,5)],
        'blade':[R(0,0,0,0),R(1.6,0,0,0),R(2.2,40,0,20)],
        'root':[Pz(0,0,0,0),Pz(1.2,0,0,0),Pz(2.4,0,-26,0),Pz(3.0,0,-34,0),S(0,1,1,1),S(2.2,1,1,1),S(3.0,0.6,0.15,0.6)],
    })
    # ===== 짤패턴 =====
    a('skill_second_slash',1.5,'once',{   # 초침 베기 2연타 (오른→왼, 왼→오른)
        'body':[R(0,0,0,0),R(.3,-6,-30,0),R(.45,12,30,0),R(.7,4,40,0),R(.9,12,-35,0),R(1.5,0,0,0)],
        'right_arm':[R(0,0,0,0),R(.3,95,0,55),R(.45,70,0,-40,'linear'),R(.7,75,0,-45),R(.9,80,0,60,'linear'),R(1.5,0,0,0)],
        'right_forearm':[R(0,0,0,0),R(.3,-30,0,0),R(.45,-10,0,0),R(.9,-10,0,0),R(1.5,0,0,0)],
        'blade':[R(0,0,0,0),R(.3,45,0,0),R(.9,45,0,0),R(1.5,0,0,0)],
        'left_arm':[R(0,0,0,0),R(.3,-20,0,-25),R(.9,-20,0,-25),R(1.5,0,0,0)],
        'root':[Pz(0,0,0,0),Pz(.45,0,0,-8),Pz(.9,0,0,-14),Pz(1.5,0,0,0)],
        'head':[R(0,0,0,0),R(.3,0,-20,0),R(.45,0,20,0),R(.7,0,25,0),R(.9,0,-20,0),R(1.5,0,0,0)],
    })
    a('skill_hour_thrust',1.3,'once',{   # 시침 찌르기: 뒤로 당겼다 직선 관통
        'body':[R(0,0,0,0),R(.45,-14,-30,0),R(.6,22,10,0),R(.9,18,8,0),R(1.3,0,0,0)],
        'right_arm':[R(0,0,0,0),R(.45,-40,0,10),R(.6,82,0,-18,'linear'),R(.9,82,0,-18),R(1.3,0,0,0)],
        'right_forearm':[R(0,0,0,0),R(.45,-40,0,0),R(.6,-38,0,0),R(.9,-38,0,0),R(1.3,0,0,0)],
        'blade':[R(0,0,0,0),R(.45,40,0,10),R(.6,30,0,10),R(.9,30,0,10),R(1.3,0,0,0)],
        'left_arm':[R(0,0,0,0),R(.45,30,0,-30),R(.6,-30,0,-40),R(1.3,0,0,0)],
        'root':[Pz(0,0,0,0),Pz(.45,0,0,6),Pz(.6,0,0,-26,'linear'),Pz(.9,0,0,-28),Pz(1.3,0,0,0)],
        'head':[R(0,0,0,0),R(.45,-10,25,0),R(.6,8,-5,0),R(1.3,0,0,0)],
    })
    a('skill_sand_fall',1.8,'once',{   # 모래 낙하 3연속: 왼손을 들고 3번 움켜쥠
        'left_arm':[R(0,0,0,0),R(.35,150,0,-25),R(1.5,150,0,-25),R(1.8,0,0,0)],
        'left_forearm':[R(0,0,0,0),R(.35,-20,0,0),R(1.5,-20,0,0),R(1.8,0,0,0)],
        **{f'left_finger{i}':[R(0,0,0,0),R(.5,0,0,0),R(.6,70,0,0),R(.75,0,0,0),R(.9,70,0,0),R(1.05,0,0,0),R(1.2,70,0,0),R(1.4,0,0,0)] for i in range(4)},
        'hourglass':[R(0,0,0,0),R(.35,0,0,-20),R(1.4,0,0,20),R(1.8,0,0,0)],
        'body':[R(0,0,0,0),R(.35,-10,0,0),R(1.5,-10,0,0),R(1.8,0,0,0)],
        'head':[R(0,0,0,0),R(.35,-18,0,0),R(.6,-14,0,0),R(.9,-18,0,0),R(1.2,-14,0,0),R(1.8,0,0,0)],
        'root':[Pz(0,0,0,0),Pz(.35,0,4,0),Pz(1.5,0,4,0),Pz(1.8,0,0,0)],
    })
    a('skill_pendulum',1.8,'once',{   # 진자 휘두르기: 한 바퀴 → 반대로 한 바퀴
        'left_arm':[R(0,0,0,0),R(.3,0,0,-75),R(1.5,0,0,-75),R(1.8,0,0,0)],
        'left_forearm':[R(0,0,0,0),R(.3,-35,0,0),R(1.5,-35,0,0),R(1.8,0,0,0)],
        'pendulum':[R(0,0,0,0),R(.3,60,0,-60),R(1.5,60,0,-60),R(1.8,0,0,0)],
        'body':[R(0,0,0,0,'linear'),R(.3,0,0,0,'linear'),R(.85,0,360,0,'linear'),R(.95,0,360,0,'linear'),R(1.5,0,0,0,'linear'),R(1.8,0,0,0)],
        'right_arm':[R(0,0,0,0),R(.3,-20,0,30),R(1.5,-20,0,30),R(1.8,0,0,0)],
        'skirt_front':[R(0,0,0,0),R(.5,-20,0,0),R(1.2,-20,0,0),R(1.8,0,0,0)],
        'skirt_back':[R(0,0,0,0),R(.5,-20,0,0),R(1.2,-20,0,0),R(1.8,0,0,0)],
    })
    a('skill_stasis_orb',1.2,'once',{   # 정지 구슬: 양손 모아 밀어냄
        'right_arm':[R(0,0,0,0),R(.4,70,0,-30),R(.6,95,0,-15),R(1.2,0,0,0)],
        'left_arm':[R(0,0,0,0),R(.4,70,0,30),R(.6,95,0,15),R(1.2,0,0,0)],
        'right_forearm':[R(0,0,0,0),R(.4,-50,0,0),R(.6,-20,0,0),R(1.2,0,0,0)],
        'left_forearm':[R(0,0,0,0),R(.4,-50,0,0),R(.6,-20,0,0),R(1.2,0,0,0)],
        'blade':[R(0,0,0,0),R(.4,-40,0,20),R(1.2,0,0,0)],
        'body':[R(0,0,0,0),R(.4,-8,0,0),R(.6,10,0,0),R(1.2,0,0,0)],
        'hourglass':[R(0,0,0,0),R(.4,0,0,0),R(.6,0,0,180),R(1.2,0,0,180)],
        'root':[Pz(0,0,0,0),Pz(.6,0,0,-4),Pz(1.2,0,0,0)],
    })
    a('skill_foresight',1.2,'once',{   # 앞당긴 미래: 고개를 꺾고 대상 지목
        'head':[R(0,0,0,0),R(.25,0,0,25),R(.45,-5,0,-20),R(.9,-5,0,-20),R(1.2,0,0,0)],
        'left_arm':[R(0,0,0,0),R(.45,100,0,10),R(.9,100,0,10),R(1.2,0,0,0)],
        'left_forearm':[R(0,0,0,0),R(.45,-30,0,0),R(1.2,0,0,0)],
        **{f'left_finger{i}':[R(0,0,0,0),R(.45,80 if i else -10,0,0),R(.9,80 if i else -10,0,0),R(1.2,0,0,0)] for i in range(4)},
        'halo_minute':[R(0,0,0,0,'linear'),R(.9,0,0,-720,'linear'),R(1.2,0,0,-720)],
        'halo_hour':[R(0,0,0,0,'linear'),R(.9,0,0,-60,'linear')],
        'halo':[S(0,1,1,1),S(.45,1.15,1.15,1.15),S(1.2,1,1,1)],
    })
    # ===== 기믹3 멈춘 세계 =====
    a('world_stop_start',2.2,'once',{
        'root':[Pz(0,0,0,0),Pz(1.2,0,14,0),Pz(2.2,0,12,0)],
        'body':[R(0,0,0,0),R(.6,20,0,0),R(1.4,-10,0,0),R(2.2,-6,0,0)],
        'head':[R(0,0,0,0),R(.6,25,0,0),R(1.4,-30,0,0),R(2.2,-20,0,0)],
        'jaw':[R(0,0,0,0),R(1.2,0,0,0),R(1.5,30,0,0),R(2.2,6,0,0)],
        'right_arm':[R(0,0,0,0),R(.6,30,0,-10),R(1.4,-10,0,85),R(2.2,-6,0,80)],
        'left_arm':[R(0,0,0,0),R(.6,30,0,10),R(1.4,-10,0,-85),R(2.2,-6,0,-80)],
        'halo':[S(0,1,1,1),S(1.4,1.6,1.6,1.6),S(2.2,1.5,1.5,1.5)],
        'halo_minute':[R(0,0,0,0,'linear'),R(1.4,0,0,-720,'linear'),R(2.2,0,0,-720,'linear')],
        'hourglass':[R(0,0,0,0),R(1.2,0,0,0),R(1.6,0,0,90)],
        'pendulum':[R(0,0,0,0),R(1.4,0,0,0),R(1.6,0,0,40),R(2.2,0,0,40)],
    })
    a('world_stop_loop',3.0,'loop',{   # 모래시계 옆으로 누운 채 정지, 미세 떨림
        'root':[Pz(0,0,12,0),Pz(1.5,0,13,0),Pz(3,0,12,0)],
        'body':[R(0,-6,0,0),R(3,-6,0,0)],'head':[R(0,-20,0,0),R(.1,-20,0,1),R(.2,-20,0,-1),R(.3,-20,0,0),R(3,-20,0,0)],
        'jaw':[R(0,6,0,0),R(3,6,0,0)],
        'right_arm':[R(0,-6,0,80),R(1.5,-8,0,82),R(3,-6,0,80)],'left_arm':[R(0,-6,0,-80),R(1.5,-8,0,-82),R(3,-6,0,-80)],
        'halo':[S(0,1.5,1.5,1.5),S(3,1.5,1.5,1.5),R(0,0,0,0,'linear'),R(3,0,0,-90,'linear')],
        'halo_minute':[R(0,0,0,-720),R(3,0,0,-720)],
        'hourglass':[R(0,0,0,90),R(3,0,0,90)],
        'pendulum':[R(0,0,0,40),R(3,0,0,40)],
    })
    a('groggy_start',1.4,'once',{   # 맵패턴 성공: 추락
        'root':[Pz(0,0,12,0),Pz(.5,0,-4,0,'linear'),Pz(.7,0,-2,0),Pz(1.4,0,-6,0)],
        'body':[R(0,-6,0,0),R(.5,30,0,0),R(.8,26,0,6),R(1.4,24,0,8)],
        'head':[R(0,-20,0,0),R(.5,30,0,0),R(1.4,45,0,15)],
        'jaw':[R(0,6,0,0),R(.5,30,0,0),R(1.4,24,0,0)],
        'right_arm':[R(0,-6,0,80),R(.5,40,0,10),R(1.4,30,0,-10)],
        'left_arm':[R(0,-6,0,-80),R(.5,40,0,-10),R(1.4,30,0,10)],
        'halo':[S(0,1.5,1.5,1.5),S(.5,0.9,0.9,0.9),S(1.4,1,1,1),R(0,0,0,0),R(.6,0,0,-30),R(1.4,25,0,-20)],
        'hourglass':[R(0,0,0,90),R(.6,0,0,200),R(1.4,0,0,180)],
        'pendulum':[R(0,0,0,40),R(.6,0,0,-30),R(1.4,0,0,0)],
        'blade':[R(0,0,0,0),R(.6,30,0,0),R(1.4,20,0,0)],
    })
    a('groggy_loop',2.0,'loop',{
        'root':[Pz(0,0,-6,0),Pz(1,0,-5,0),Pz(2,0,-6,0)],
        'body':[R(0,24,0,8),R(1,21,0,8),R(2,24,0,8)],
        'head':[R(0,45,0,15),R(1,48,0,12),R(2,45,0,15)],
        'jaw':[R(0,24,0,0),R(2,24,0,0)],
        'right_arm':[R(0,30,0,-10),R(1,34,0,-10),R(2,30,0,-10)],
        'left_arm':[R(0,30,0,10),R(1,34,0,10),R(2,30,0,10)],
        'halo':[R(0,25,0,-20),R(2,25,0,-20)],
        'hourglass':[R(0,0,0,180),R(2,0,0,180)],
        'blade':[R(0,20,0,0),R(2,20,0,0)],
        'halo_minute':[R(0,0,0,0),R(.2,0,0,-6,'step'),R(1,0,0,-6),R(1.2,0,0,0,'step'),R(2,0,0,0)],
    })
    # ===== 기믹1 시간 감옥 / 기믹2 바닥 시계 =====
    a('skill_prison',1.8,'once',{   # 딜 1등을 칼끝으로 지목 → 왼손으로 움켜쥐어 가둠
        'right_arm':[R(0,0,0,0),R(.5,95,0,10),R(1.3,95,0,10),R(1.8,0,0,0)],
        'right_forearm':[R(0,0,0,0),R(.5,-35,0,0),R(1.3,-35,0,0),R(1.8,0,0,0)],
        'blade':[R(0,0,0,0),R(.5,-10,0,8),R(1.3,-10,0,8),R(1.8,0,0,0)],
        'left_arm':[R(0,0,0,0),R(.6,80,0,-30),R(.9,95,0,-15),R(1.3,95,0,-15),R(1.8,0,0,0)],
        **{f'left_finger{i}':[R(0,0,0,0),R(.6,-10,0,0),R(.9,85,0,0,'linear'),R(1.3,85,0,0),R(1.8,0,0,0)] for i in range(4)},
        'hourglass':[R(0,0,0,0),R(.85,0,0,0),R(1.2,0,0,180)],
        'head':[R(0,0,0,0),R(.5,8,-10,0),R(1.3,8,-10,0),R(1.8,0,0,0)],
        'jaw':[R(0,0,0,0),R(.85,0,0,0),R(1.0,24,0,0),R(1.4,0,0,0)],
        'body':[R(0,0,0,0),R(.5,-6,-12,0),R(.9,10,8,0),R(1.8,0,0,0)],
        'halo':[S(0,1,1,1),S(.9,1.2,1.2,1.2),S(1.8,1,1,1)],
    })
    a('skill_clock_summon',2.4,'once',{   # 떠올랐다가 두 손바닥으로 바닥을 내리쳐 시계를 펼침
        'root':[Pz(0,0,0,0),Pz(.9,0,10,0),Pz(1.3,0,-4,0,'linear'),Pz(2.4,0,0,0)],
        'body':[R(0,0,0,0),R(.9,-12,0,0),R(1.3,34,0,0),R(1.9,30,0,0),R(2.4,0,0,0)],
        'head':[R(0,0,0,0),R(.9,-25,0,0),R(1.3,-10,0,0),R(2.4,0,0,0)],
        'right_arm':[R(0,0,0,0),R(.9,-20,0,95),R(1.3,60,0,10,'linear'),R(1.9,60,0,10),R(2.4,0,0,0)],
        'left_arm':[R(0,0,0,0),R(.9,-20,0,-95),R(1.3,60,0,-10,'linear'),R(1.9,60,0,-10),R(2.4,0,0,0)],
        'right_forearm':[R(0,0,0,0),R(1.3,-30,0,0),R(2.4,0,0,0)],
        'left_forearm':[R(0,0,0,0),R(1.3,-30,0,0),R(2.4,0,0,0)],
        'halo':[S(0,1,1,1),S(.9,1.5,1.5,1.5),S(1.3,1.1,1.1,1.1),S(2.4,1,1,1)],
        'halo_minute':[R(0,0,0,0,'linear'),R(1.3,0,0,-1080,'linear'),R(2.4,0,0,-1080)],
        'halo_hour':[R(0,0,0,0,'linear'),R(1.3,0,0,-180,'linear'),R(2.4,0,0,-180)],
        'skirt_front':[R(0,0,0,0),R(1.3,-25,0,0),R(2.4,0,0,0)],
        'skirt_back':[R(0,0,0,0),R(1.3,25,0,0),R(2.4,0,0,0)],
    })
    a('world_stop_release',1.0,'once',{   # 손가락을 튕겨 멈춘 탄환을 한꺼번에 놓아줌 (world_stop_loop 자세에서 이어짐)
        'root':[Pz(0,0,12,0),Pz(.3,0,13,0),Pz(1.0,0,0,0)],
        'body':[R(0,-6,0,0),R(.3,-10,0,0),R(.45,14,0,0),R(1.0,0,0,0)],
        'head':[R(0,-20,0,0),R(.3,-25,0,0),R(.45,10,0,0),R(1.0,0,0,0)],
        'jaw':[R(0,6,0,0),R(.45,30,0,0),R(1.0,0,0,0)],
        'right_arm':[R(0,-6,0,80),R(.45,30,0,30),R(1.0,0,0,0)],
        'left_arm':[R(0,-6,0,-80),R(.3,110,0,-20),R(.45,120,0,-10),R(1.0,0,0,0)],
        **{f'left_finger{i}':[R(0,0,0,0),R(.3,70,0,0),R(.4,-15,0,0,'linear'),R(1.0,0,0,0)] for i in range(4)},
        'halo':[S(0,1.5,1.5,1.5),S(.45,1.8,1.8,1.8),S(1.0,1,1,1)],
        'halo_minute':[R(0,0,0,-720),R(.45,0,0,-720,'linear'),R(1.0,0,0,-1080,'linear')],
        'hourglass':[R(0,0,0,90),R(.45,0,0,90),R(1.0,0,0,180)],
        'pendulum':[R(0,0,0,40),R(.45,0,0,-30),R(1.0,0,0,0)],
    })
    # ===== 가짜 클리어 → 2페이즈 =====
    fake={
        'halo_minute':[R(0,0,0,0,'linear'),R(1.6,0,0,-1440,'linear'),R(2.6,0,0,-1440)],
        'halo_hour':[R(0,0,0,0,'linear'),R(1.6,0,0,-360,'linear'),R(2.6,0,0,-360)],
        'halo':[R(0,0,0,0),R(1.2,0,0,0),R(2.0,40,0,25),R(2.6,40,0,25),Pz(0,0,0,0),Pz(1.2,0,0,0),Pz(2.2,0,-22,4),Pz(2.6,0,-22,4)],
        'hourglass':[R(0,0,0,0),R(1.2,0,0,0),R(1.6,0,0,90),R(2.0,0,0,180),R(2.6,0,0,180)],
        'jaw':[R(0,0,0,0),R(.4,32,0,0),R(1.2,30,0,0),R(1.8,10,0,0),R(2.6,10,0,0)],
        'head':[R(0,0,0,0),R(.4,-30,0,0),R(1.2,-25,0,6),R(2.0,40,0,10),R(2.6,40,0,10)],
        'body':[R(0,0,0,0),R(.4,-12,0,0),R(1.2,-10,0,0),R(2.2,32,0,6),R(2.6,32,0,6)],
        'right_arm':[R(0,0,0,0),R(.4,-30,0,40),R(1.2,-20,0,35),R(2.2,10,0,-5),R(2.6,10,0,-5)],
        'left_arm':[R(0,0,0,0),R(.4,-30,0,-40),R(1.2,-20,0,-35),R(2.2,10,0,5),R(2.6,10,0,5)],
        'blade':[R(0,0,0,0),R(1.6,0,0,0),R(2.2,40,0,20),R(2.6,40,0,20)],
        'root':[Pz(0,0,0,0),Pz(1.2,0,0,0),Pz(2.2,0,-14,0),Pz(2.6,0,-14,0)],
    }
    import copy
    a('fake_death',2.6,'hold',copy.deepcopy(fake))   # 사망과 같은 연출이지만 사라지지 않고 쓰러진 채 멈춤
    rev={k:[(x[0],round(2.6-x[1],4))+tuple(x[2:5])+(('catmullrom',) if len(x)<6 or x[5]!='linear' else ('linear',)) for x in v] for k,v in fake.items()}
    a('revive',2.6,'once',rev)   # fake_death를 그대로 거꾸로 재생 (시간이 되감기며 부활)
    a('phase2_roar',2.6,'once',{   # 2페이즈 시작: 모래시계 2바퀴, 포효
        'root':[Pz(0,0,0,0),Pz(.8,0,4,0),Pz(1.6,0,6,0),Pz(2.6,0,0,0)],
        'body':[R(0,0,0,0),R(.8,10,0,0),R(1.4,-22,0,0),R(2.0,-18,0,0),R(2.6,0,0,0)],
        'head':[R(0,0,0,0),R(.8,10,0,0),R(1.4,-40,0,0),R(2.0,-35,0,0),R(2.6,0,0,0)],
        'jaw':[R(0,0,0,0),R(.8,0,0,0),R(1.4,40,0,0),R(2.0,40,0,0),R(2.6,0,0,0)],
        'right_arm':[R(0,0,0,0),R(.8,40,0,-20),R(1.4,-30,0,80),R(2.0,-30,0,80),R(2.6,0,0,0)],
        'left_arm':[R(0,0,0,0),R(.8,40,0,20),R(1.4,-30,0,-80),R(2.0,-30,0,-80),R(2.6,0,0,0)],
        'hourglass':[R(0,0,0,0,'linear'),R(.8,0,0,0,'linear'),R(2.0,0,0,720,'linear'),R(2.6,0,0,720)],
        'halo':[S(0,1,1,1),S(1.4,1.5,1.5,1.5),S(2.6,1,1,1)],
        'halo_minute':[R(0,0,0,0,'linear'),R(2.4,0,0,-2160,'linear')],
        'halo_hour':[R(0,0,0,0,'linear'),R(2.4,0,0,-720,'linear')],
        'pendulum':[R(0,0,0,0),R(1.4,0,0,50),R(2.0,0,0,-50),R(2.6,0,0,0)],
    })
    return A

if __name__=='__main__':
    O='/mnt/user-data/outputs/'
    if os.path.exists(gen.HITBOX_JSON): os.remove(gen.HITBOX_JSON)
    export('time_wraith', build(), anims, 7, O+'time_wraith.bbmodel', HITBOX)
    apply_palette(SHINY); export('time_wraith_shiny', build(), anims, 7, O+'time_wraith_shiny.bbmodel', HITBOX)
