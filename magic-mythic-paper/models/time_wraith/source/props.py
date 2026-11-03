"""시간의 망령 3인 보스 기믹 소품
time_prison      시간 감옥 (기믹1) — 거대한 모래시계 우리
time_floor_clock 바닥 시계 문자판 (기믹2)
time_hand_hour / time_hand_minute / time_hand_second  바늘 3종 (기믹2) — 원점 = 시계 중심, 12시(북쪽, -z)를 가리킴. 각도는 엔티티 yaw로 돌림
time_bullet      시곗바늘 탄환 (기믹3 멈춘 세계)
time_player_echo 유저 잔상 (2페이즈)
"""
import math, os
import models as M
from models import C, G, metal, glow, ring, gear, cloth, bulb, fill, rgba, SHADE, px, clamp
from gen import Group, anim, export
import gen

M.P.update(IRON=(70,66,84), HOUR=(214,96,72), MINUTE=(232,186,80), SECOND=(110,230,240),
           ECHO_B=(110,200,230), ECHO_L=(200,246,255), ECHO_D=(60,120,160))
cm='catmullrom'
R,Pz,S=M.R,M.Pz,M.S
O='/mnt/user-data/outputs/'

def bars(key):
    """세로 눈금이 새겨진 쇠기둥."""
    def p(img,face,rect,rng):
        metal(key)(img,face,rect,rng)
        if face in ('north','south','east','west'):
            x1,y1,x2,y2=rect
            for y in range(y1+3,y2,6): img.putpixel(((x1+x2)//2,y),rgba(M.P['SAND'],1))
    return p

def band(key):
    """테두리 띠: 시계 눈금 반복 무늬."""
    def p(img,face,rect,rng):
        metal(key)(img,face,rect,rng)
        if face in ('north','south','east','west'):
            x1,y1,x2,y2=rect
            for x in range(x1+1,x2,4):
                for y in range(y1+1,y2-1): img.putpixel((x,y),rgba(M.P['BRASS_D'],0.9))
            for x in range(x1+3,x2,16):
                for y in range(y1+1,y2-1): img.putpixel((x,y),rgba(M.P['SAND'],1))
    return p

def gear_ew(key):
    base=gear(key)
    def p(img,face,rect,rng):
        f={'east':'north','west':'south'}.get(face,face if face not in ('north','south') else 'up')
        base(img,f,rect,rng)
    return p

# ---------------- 1. 시간 감옥 ----------------
def prison():
    kids=[
        C('base',[-24,0,-24],[24,4,24],band('BRASS_D')),
        C('base_rim',[-26,0,-26],[26,2,26],metal('IRON')),
        C('top',[-24,68,-24],[24,72,24],band('BRASS_D')),
        C('top_rim',[-26,72,-26],[26,74,26],metal('IRON')),
        C('top_dial',[-8,74,-8],[8,76,8],metal('BRASS',True)),
        C('top_finial',[-2,76,-2],[2,82,2],metal('BRASS')),
        C('top_finial_tip',[-1,82,-1],[1,85,1],glow('SAND')),
    ]
    # 기둥: 모서리 4 + 변마다 2
    k=0
    for x in (-22,-8,6,20):
        for z in (-22,20):
            kids.append(C(f'bar{k}',[x,4,z],[x+2,68,z+2],bars('BRASS' if x in (-22,20) else 'IRON'))); k+=1
    for z in (-8,6):
        for x in (-22,20):
            kids.append(C(f'bar{k}',[x,4,z],[x+2,68,z+2],bars('IRON'))); k+=1
    # 모서리 기둥 보강
    for i,(x,z) in enumerate([(-23,-23),(19,-23),(-23,19),(19,19)]):
        kids.append(C(f'corner{i}',[x,4,z],[x+4,10,z+4],metal('BRASS',True)))
        kids.append(C(f'corner_top{i}',[x,62,z],[x+4,68,z+4],metal('BRASS',True)))
    # 가운데 허리 고리 (모래시계 잘록한 부분 느낌)
    for i,(f,t) in enumerate([([-23,34,-23],[23,36,-21]),([-23,34,21],[23,36,23]),([-23,34,-21],[-21,36,21]),([21,34,-21],[23,36,21])]):
        kids.append(C(f'waist{i}',f,t,metal('BRASS_D')))
    # 옆면 톱니
    gears=[]
    for i,(f,t) in enumerate([([-6,52,-25],[6,64,-24]),([-6,52,24],[6,64,25])]):
        gears.append(Group(f'gear{i}',[0,58,f[2]+0.5],[C(f'gear{i}',f,t,gear('BRASS'))]))
    for i,(f,t) in enumerate([([-25,52,-6],[-24,64,6]),([24,52,-6],[25,64,6])]):
        gears.append(Group(f'gear{i+2}',[f[0]+0.5,58,0],[C(f'gear{i+2}',f,t,gear_ew('BRASS'))]))
    # 모래: 천장 아래 위쪽 모래(줄어듦), 바닥 둘레 아래쪽 모래(쌓임)
    sand_up=Group('sand_upper',[0,68,0],[C('sand_upper',[-20,60,-20],[20,68,20],bulb(0))])
    sand_low=Group('sand_lower',[0,4,0],[
        C('sand_lower_n',[-20,4,-20],[20,8,-14],bulb(0)), C('sand_lower_s',[-20,4,14],[20,8,20],bulb(0)),
        C('sand_lower_w',[-20,4,-14],[-14,8,14],bulb(0)), C('sand_lower_e',[14,4,-14],[20,8,14],bulb(0))])
    cage=Group('cage',[0,36,0],kids+gears+[sand_up,sand_low])
    return Group('root',[0,0,0],[cage])

def prison_anims(Gs):
    n='animation.time_prison.'; A=[]
    g=lambda s: {f'gear{i}':[R(0,0,0,0,'linear'),R(s,0,0,360*(-1)**i,'linear')] if i<2 else [R(0,0,0,0,'linear'),R(s,360*(-1)**i,0,0,'linear')] for i in range(4)}
    A.append(anim(n+'spawn',1.2,'once',{'cage':[Pz(0,0,60,0),Pz(.6,0,-2,0,'linear'),Pz(.8,0,2,0),Pz(1.2,0,0,0),S(0,1.2,1,1.2),S(.6,1.2,1,1.2),S(.7,1.1,0.9,1.1),S(1.2,1,1,1)],
                                         'sand_lower':[S(0,1,0.1,1),S(1.2,1,0.1,1)]},Gs))
    A.append(anim(n+'idle',2.0,'loop',{**g(2.0),'sand_lower':[S(0,1,0.1,1),S(2,1,0.1,1)]},Gs))
    # drain: 모래가 다 떨어지는 시간 = 감옥 제한 시간. 길이 10초 — ModelEngine에서 재생 속도로 맞추면 됨
    A.append(anim(n+'drain',10.0,'hold',{**g(10.0),'sand_upper':[S(0,1,1,1,'linear'),S(10,1,0.05,1,'linear')],
                                          'sand_lower':[S(0,1,0.1,1,'linear'),S(10,1,1.6,1,'linear')]},Gs))
    A.append(anim(n+'hit',0.35,'once',{'cage':[S(0,1,1,1),S(.08,1.05,0.97,1.05),S(.35,1,1,1),R(0,0,0,0),R(.08,0,3,0),R(.18,0,-2,0),R(.35,0,0,0)]},Gs))
    A.append(anim(n+'death',1.2,'hold',{   # 깨짐: 위판이 튀고 통째로 무너져 사라짐
        'cage':[S(0,1,1,1),S(.15,1.12,1.05,1.12),S(.9,1.1,0.3,1.1),S(1.2,0,0,0),Pz(0,0,0,0),Pz(.9,0,-10,0)],
        'sand_upper':[S(0,1,1,1),S(.4,1,0.1,1)]},Gs))
    return A

# ---------------- 2. 바닥 시계 ----------------
def floor_clock():
    kids=[]
    for k in range(24):   # 바깥 고리
        kids.append(Group(f'ring{k}',[0,0,0],[C(f'ring{k}',[-15,0,-112],[15,2,-104],band('BRASS_D'))],[0,15*k,0]))
    for k in range(24):   # 안쪽 고리
        kids.append(Group(f'inner{k}',[0,0,0],[C(f'inner{k}',[-9,0,-74],[9,1,-70],metal('IRON'))],[0,15*k,0]))
    for h in range(12):   # 시 표식: 12시는 금빛 이중, 3/6/9시는 큰 막대
        if h==0:
            kc=[C('mark0_a',[-6,0,-102],[-2,2,-82],glow('SAND')),C('mark0_b',[2,0,-102],[6,2,-82],glow('SAND'))]
        elif h%3==0: kc=[C(f'mark{h}',[-3,0,-102],[3,2,-82],metal('BRASS',True))]
        else: kc=[C(f'mark{h}',[-2,0,-102],[2,1,-88],metal('BRASS'))]
        kids.append(Group(f'mark{h}',[0,0,0],kc,[0,-30*h,0]))
    for mnt in range(60):   # 분 눈금 (시 표식 자리 제외)
        if mnt%5==0: continue
        kids.append(Group(f'tick{mnt}',[0,0,0],[C(f'tick{mnt}',[-0.5,0,-103],[0.5,1,-98],metal('BRASS_D'))],[0,-6*mnt,0]))
    hub=Group('hub',[0,0,0],[
        C('hub_base',[-10,0,-10],[10,2,10],metal('IRON')),
        C('hub_mid',[-7,2,-7],[7,4,7],metal('BRASS',True)),
        C('hub_gear',[-6,4,-6],[6,5,6],metal('BRASS_D')),
        C('hub_core',[-2,5,-2],[2,6,2],glow('SAND'))])
    face=Group('face',[0,0,0],kids+[hub])
    return Group('root',[0,0,0],[face])

def clock_anims(Gs):
    n='animation.time_floor_clock.'
    return [
      anim(n+'spawn',1.5,'once',{'face':[S(0,0.05,1,0.05),S(1.0,1.05,1,1.05),S(1.5,1,1,1),R(0,0,-90,0),R(1.2,0,0,0)]},Gs),
      anim(n+'idle',4.0,'loop',{'hub':[R(0,0,0,0,'linear'),R(4,0,-90,0,'linear')]},Gs),
      anim(n+'success',1.2,'once',{'face':[S(0,1,1,1),S(.2,1.04,2,1.04),S(1.2,1,1,1)],'hub':[R(0,0,0,0,'linear'),R(1.2,0,-720,0,'linear')]},Gs),
      anim(n+'fail',1.0,'once',{'face':[R(0,0,0,0),R(.1,0,4,0),R(.2,0,-4,0),R(.3,0,3,0),R(.45,0,0,0)]},Gs),
      anim(n+'death',1.0,'hold',{'face':[S(0,1,1,1),S(1.0,0,1,0)]},Gs),
    ]

# ---------------- 3. 바늘 3종 ----------------
def hand(kind):
    if kind=='hour':   L,W,key,tail=60,8,'HOUR',14
    elif kind=='minute': L,W,key,tail=90,6,'MINUTE',18
    else: L,W,key,tail=104,2,'SECOND',24
    hw=W/2; y0,y1=2,5
    kids=[C('shaft',[-hw,y0,-L+8],[hw,y1,0],metal('IRON') if kind!='second' else glow(key)),
          C('shaft_inlay',[-max(0.5,hw-2),y1,-L+10],[max(0.5,hw-2),y1+1,-4],glow(key))]
    if kind!='second':
        kids+=[C('head_a',[-hw-3,y0,-L+8],[hw+3,y1,-L+12],metal(key,True)),
               C('head_b',[-hw-1,y0,-L+4],[hw+1,y1,-L+8],metal(key)),
               C('head_c',[-1,y0,-L],[1,y1,-L+4],glow(key))]
    else:
        kids+=[C('head_b',[-2,y0,-L+4],[2,y1,-L+8],glow(key)),C('head_c',[-1,y0,-L],[1,y1,-L+4],glow('EDGE'))]
    kids+=[C('tail',[-max(1,hw-1),y0,0],[max(1,hw-1),y1,tail],metal('IRON')),
           C('tail_weight',[-5,y0-1,tail],[5,y1+1,tail+8],metal(key,True)),
           C('tail_weight_b',[-3,y0-1,tail+8],[3,y1+1,tail+10],metal('BRASS_D')),
           C('pin',[-3,0,-3],[3,y1+2,3],metal('BRASS',True)),
           C('pin_cap',[-1,y1+2,-1],[1,y1+3,1],glow('SAND')),
           Group('tip_point',[0,3.5,-L+2],[]), Group('tail_point',[0,3.5,tail+5],[])]
    return Group('root',[0,0,0],[Group('hand',[0,0,0],kids)])

def hand_anims(kind):
    def f(Gs):
        n=f'animation.time_hand_{kind}.'
        return [
          anim(n+'spawn',0.8,'once',{'hand':[S(0,0,1,0),S(.6,1.1,1,1.1),S(.8,1,1,1),Pz(0,0,8,0),Pz(.6,0,0,0)]},Gs),
          anim(n+'idle',2.0,'loop',{'hand':[Pz(0,0,0,0),Pz(1,0,0.5,0),Pz(2,0,0,0)]},Gs),
          # 칸 이동 반동 — 실제 각도는 엔티티 yaw로 바꾸고, 이 모션은 덜컥 하는 손맛만 줌
          anim(n+'tick_forward',0.3,'once',{'hand':[R(0,0,0,0),R(.06,0,-6,0),R(.15,0,2,0),R(.3,0,0,0)]},Gs),
          anim(n+'tick_back',0.3,'once',{'hand':[R(0,0,0,0),R(.06,0,6,0),R(.15,0,-2,0),R(.3,0,0,0)]},Gs),
          anim(n+'locked',1.0,'once',{'hand':[S(0,1,1,1),S(.15,1.2,1.6,1.05),S(1,1,1,1),Pz(0,0,0,0),Pz(.15,0,2,0),Pz(1,0,0,0)]},Gs),
          anim(n+'death',0.6,'hold',{'hand':[S(0,1,1,1),S(.6,0,0,0)]},Gs),
        ]
    return f

# ---------------- 4. 시곗바늘 탄환 ----------------
def bullet():
    kids=[C('shaft',[-1,-1,-8],[1,1,10],metal('IRON')),
          C('edge',[-0.5,1,-8],[0.5,2,8],glow('SECOND')),
          C('head_a',[-3,-1,-11],[3,1,-8],metal('BRASS',True)),
          C('head_b',[-2,-1,-13],[2,1,-11],metal('BRASS')),
          C('head_c',[-1,-1,-15],[1,1,-13],glow('EDGE')),
          C('tail_ring',[-3,-3,10],[3,3,11],ring('BRASS',1.2)),
          C('tail_fin',[-0.5,-2,11],[0.5,2,14],metal('BRASS_D'))]
    return Group('root',[0,0,0],[Group('bullet',[0,0,0],kids)])
def bullet_anims(Gs):
    n='animation.time_bullet.'
    return [
      anim(n+'spawn',0.3,'once',{'bullet':[S(0,0,0,0),S(.2,1.2,1.2,1.2),S(.3,1,1,1)]},Gs),
      anim(n+'idle',0.6,'loop',{'bullet':[R(0,0,0,0,'linear'),R(.6,0,0,360,'linear')]},Gs),   # 날아갈 때 회전
      anim(n+'frozen',0.5,'loop',{'bullet':[Pz(0,0,0,0),Pz(.1,0.2,0,0),Pz(.2,-0.2,0.1,0),Pz(.3,0,-0.1,0),Pz(.5,0,0,0)]},Gs),  # 멈춘 세계: 공중에서 미세 떨림
      anim(n+'death',0.25,'hold',{'bullet':[S(0,1,1,1),S(.1,1.5,1.5,0.6),S(.25,0,0,0)]},Gs),
    ]

# ---------------- 5. 유저 잔상 ----------------
def echo_paint(key):
    """잔상: 단색 + 가로 스캔라인 구멍(완전 투명)."""
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                img.putpixel((x,y),rgba(M.P[key],SHADE[face]*rng.uniform(.92,1.08)))
        if face in ('up','down'): return
        for y in range(y1,y2):
            if (y-y1)%3==2:
                for x in range(x1,x2):
                    if rng.random()<0.55: img.putpixel((x,y),(0,0,0,0))
        for x in (x1,x2-1):   # 밝은 외곽
            for y in range(y1,y2):
                if img.getpixel((x,y))[3]: img.putpixel((x,y),rgba(M.P['ECHO_L'],1))
    return p
def player_echo():
    B='ECHO_B'
    head=Group('head',[0,24,0],[C('head',[-4,24,-4],[4,32,4],echo_paint(B)),
        C('eye_l',[-3,27,-4.1],[-1,28,-4.1],glow('ECHO_L')),C('eye_r',[1,27,-4.1],[3,28,-4.1],glow('ECHO_L'))])
    body=Group('body',[0,24,0],[C('torso',[-4,12,-2],[4,24,2],echo_paint(B)), head,
        Group('right_arm',[5,22,0],[C('right_arm',[4,12,-2],[8,24,2],echo_paint('ECHO_D'))]),
        Group('left_arm',[-5,22,0],[C('left_arm',[-8,12,-2],[-4,24,2],echo_paint('ECHO_D'))])])
    legs=[Group('right_leg',[2,12,0],[C('right_leg',[0,0,-2],[4,12,2],echo_paint('ECHO_D'))]),
          Group('left_leg',[-2,12,0],[C('left_leg',[-4,0,-2],[0,12,2],echo_paint('ECHO_D'))])]
    return Group('root',[0,0,0],[body]+legs)
def echo_anims(Gs):
    n='animation.time_player_echo.'; L=0.8
    sw=lambda a:[R(0,a,0,0),R(L/2,-a,0,0),R(L,a,0,0)]
    return [
      anim(n+'spawn',0.4,'once',{'root':[S(0,0.2,1.2,0.2),S(.25,1.1,1,1.1),S(.4,1,1,1)]},Gs),
      anim(n+'idle',2.0,'loop',{'body':[Pz(0,0,0,0),Pz(1,0,-0.3,0),Pz(2,0,0,0)],'right_arm':[R(0,0,0,3),R(1,0,0,5),R(2,0,0,3)],'left_arm':[R(0,0,0,-3),R(1,0,0,-5),R(2,0,0,-3)]},Gs),
      anim(n+'walk',L,'loop',{'right_leg':sw(30),'left_leg':sw(-30),'right_arm':sw(-25),'left_arm':sw(25)},Gs),
      anim(n+'hit',0.4,'once',{'root':[S(0,1,1,1),S(.08,1.3,0.8,1.3),S(.4,1,1,1)],'body':[R(0,0,0,0),R(.08,-15,0,0),R(.4,0,0,0)]},Gs),
      anim(n+'death',0.5,'hold',{'root':[S(0,1,1,1),S(.2,0.3,1.4,0.3),S(.5,0,1.8,0)]},Gs),
    ]

if __name__=='__main__':
    export('time_prison', prison(), prison_anims, 11, O+'time_prison.bbmodel', (3.0,4.6,2.3))
    export('time_floor_clock', floor_clock(), clock_anims, 12, O+'time_floor_clock.bbmodel', (2.0,0.4,0.2))
    for i,k in enumerate(['hour','minute','second']):
        export(f'time_hand_{k}', hand(k), hand_anims(k), 13+i, O+f'time_hand_{k}.bbmodel', (0.8,0.4,0.2))
    export('time_bullet', bullet(), bullet_anims, 16, O+'time_bullet.bbmodel', (0.5,0.5,0.25))
    export('time_player_echo', player_echo(), echo_anims, 17, O+'time_player_echo.bbmodel', (0.6,1.8,1.62))
