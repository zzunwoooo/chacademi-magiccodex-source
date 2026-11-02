"""재의 그림자 던전 잡몹 3종 (4족)
- ash_hound   재 사냥개   : 빠른 근접. 작은 재 가면, 드러난 잿불 갈비뼈, 연기 꼬리
- wax_brute   촛농 짐승   : 탱커 근접. 등에 불 켜진 촛불 숲, 녹아내린 촛농 등껍질, 큰 가면
- lantern_stag 등불 사슴  : 원거리. 촛불 달린 숯가지 뿔, 입에 문 등불을 휘둘러 잿불을 던짐
VFX 기준점: fx_* 빈 본 (큐브 없음) — 공격 VFX 붙일 위치.
"""
import models3 as M
from models3 import C, G_, box, Group, Cube, mx, r5, glow, skin, ashcloth, metal, claw, wax, core, solidp, chain_link, rgb, mix, CLEAR, SIDE, P_
from gen import anim, export, SHADE, px, HITBOX_JSON
import os, math

BASE_PAL={k:getattr(M,k) for k in M.SHINY}
SHINY_PAL=dict(M.SHINY)
def pal(p): M.apply_palette(p)

def mask_art(art):
    """작은 가면: art = {(lx,ly): 'F'|'E'|'C'} 앞면에만."""
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                edge=(x in(x1,x2-1)) or (y in(y1,y2-1))
                img.putpixel((x,y),rgb(P_('MASK'),SHADE[face]*(0.88 if edge else 1)))
        if face=='north':
            for (lx,ly),k in art.items():
                c={'F':P_('MASK_FEAT'),'E':P_('EMBER'),'C':P_('MASK_D')}[k]
                px(img,rect,lx,ly,rgb(c))
    return p
def sym(w, pts, k='F'):
    out={}
    for lx,ly in pts: out[(lx,ly)]=k; out[(w-1-lx,ly)]=k
    return out

def waxdrip(cut=0.35):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; h=max(1,y2-y1)
        for y in range(y1,y2):
            for x in range(x1,x2):
                c=P_('WAX') if (x*7)%5 else P_('WAX_D')
                img.putpixel((x,y),rgb(c,SHADE[face]*(1+rng.uniform(-.05,.05))))
        if face not in SIDE: return
        for x in range(x1,x2):
            L=rng.choice([0,0,1,2,3,int(h*cut)])
            for y in range(y2-L,y2): img.putpixel((x,y),CLEAR)
            if y2-L-1>=y1 and rng.random()<0.3: img.putpixel((x,y2-L-1),rgb(P_('WAX_D')))
    return p

def candle(name, x, y, z, h, lit=True, rot=None):
    """y = 밑면. 초 + 심지 + 불꽃(발광 큐브)."""
    o=[x,y,z]
    out=[box(f'{name}',[x,y+h/2,z],[2,h,2],wax(),rot=rot,origin=o),
         box(f'{name}_wick',[x,y+h+0.5,z],[1,1,1],glow('VOID'),rot=rot,origin=o)]
    if lit: out.append(box(f'{name}_flame',[x,y+h+2,z],[1,2,1],glow('EMBER'),rot=rot,origin=o))
    return out

def fx(name, at): return Group(name,list(at),[])   # VFX 기준점(빈 본)

def leg(pre, m, hip, upper, lower, paw, pw=2, lower_name=None, up_paint=None, lo_paint=None, claws=True, hoof=False, rot=None, lrot=None):
    """hip=[x,y,z] 오른쪽 기준. upper/lower = 길이. 아래로 뻗는 다리."""
    x,y,z=hip; up_paint=up_paint or skin('BODY'); lo_paint=lo_paint or skin('BODY2')
    ky=y-upper
    kids=[box(f'{pre}_paw',[x,paw/2,z-0.5],[pw+1,paw,pw+2],claw() if hoof else lo_paint,m)]
    if claws and not hoof:
        for i,dx in enumerate([-1,1] if pw<3 else [-1,0,1]):
            kids.append(box(f'{pre}_claw{i}',[x+dx*(1 if pw<3 else 1),0.5,z-(pw+2)/2-1],[1,1,2],claw(),m))
    lower_g=G_(lower_name or f'{pre}_lower',[x,ky,z],[box(f'{pre}_lower',[x,(ky+paw)/2,z],[pw,ky-paw,pw],lo_paint,m)]+kids,lrot,m)
    return G_(pre,[x,y,z],[box(f'{pre}_upper',[x,y-upper/2,z],[pw+1,upper,pw+1],up_paint,m),lower_g],rot,m)

# =====================================================================
# 1) 재 사냥개
# =====================================================================
def build_hound():
    m_art={**sym(7,[(1,2),(2,2),(1,3)]),(2,3):'E',(4,3):'E',(3,0):'C',(3,1):'C',(2,5):'F',(3,5):'F',(4,5):'F'}
    jaw=Group('jaw',[0,15,-17],[
        C('jaw',[-2,13,-22],[2,15,-17],skin('BODY2')),
        C('teeth_low',[-1.5,15,-22],[1.5,16,-21],solidp('MASK')),
        fx('fx_bite',[0,15,-23])],[6,0,0])
    ears=[C('ear_r',[1.5,22,-15],[3.5,26,-14],ashcloth('BODY2',0.5,0.3,0.1,False),rot=[-15,0,-20],origin=[2.5,22,-14.5]),
          C('ear_l',[-3.5,22,-15],[-1.5,26,-14],ashcloth('BODY2',0.5,0.3,0.1,False),rot=[-15,0,20],origin=[-2.5,22,-14.5])]
    head=Group('head',[0,18,-11],[
        C('skull',[-3,15,-17],[3,22,-11],skin('BODY')),
        C('snout',[-2,15,-22],[2,18,-17],skin('BODY')),
        C('mask',[-3.5,16,-17.5],[3.5,22,-17.5],mask_art(m_art)),
        C('mask_snout',[-2,17,-22.5],[2,18,-17.5],mask_art({})),
        C('teeth_up',[-1.5,14,-22],[1.5,15,-21],solidp('MASK')),
        jaw,*ears],[-18,0,0])
    neck=Group('neck',[0,17,-7],[C('neck',[-2.5,15,-15],[2.5,21,-6],skin('BODY')),
        C('ruff',[-3.5,17,-11],[3.5,22,-6],ashcloth('CLOTH',0.4,0.4)),head],[22,0,0])
    tail=Group('tail',[0,17,9],[
        C('tail1',[-1,16,9],[1,18,15],skin('BODY2')),
        Group('tail2',[0,17,15],[C('tail2',[-1.5,16,15],[1.5,19,22],ashcloth('CLOTH_D',0.3,0.5,0.2,False))],[18,0,0])],[-30,0,0])
    body=Group('body',[0,15,0],[
        C('chest',[-4,11,-9],[4,20,1],skin('BODY')),
        C('ribcage',[-4.5,11,-6],[4.5,17,1],M.cracked('BODY2',2.0)),
        C('waist',[-3,12,1],[3,18,10],skin('BODY')),
        C('hip',[-3.5,12,6],[3.5,18,10],skin('BODY2')),
        C('mane',[-4.5,18,-9],[4.5,22,1],ashcloth('CLOTH',0.35,0.45)),
        C('spine_fur',[-2,18,1],[2,20,9],ashcloth('CLOTH_D',0.3,0.5)),
        *[C(f'spine{i}',[-0.5,20,z],[0.5,22,z+1],claw()) for i,z in enumerate([2,5,8])],
        neck,tail,fx('fx_body',[0,16,0])])
    legs=[]
    for m,s in((False,'right'),(True,'left')):
        legs.append(leg(f'{s}_front_leg',m,[3,14,-5],7,None,1,2,f'{s}_front_forearm',skin('BODY'),skin('BODY2'),lrot=[0,0,0]))
        legs.append(leg(f'{s}_hind_leg',m,[2.5,15,7],7,None,1,2,f'{s}_hind_shin',skin('BODY'),skin('BODY2')))
    # 앞발 발톱 VFX 기준점
    legs[0].children[1].children.append(fx('fx_claw_r',[3,0,-8]))
    return Group('root',[0,0,0],[body,*legs])

def anims_hound(G):
    n='animation.ash_hound.'; cm='catmullrom'; LIN='linear'
    W=0.8
    return [
      anim(n+'spawn',1.6,'once',{
        'root':[('position',0,0,-14,0),('position',0.8,0,1,0,cm),('position',1.0,0,0,0,cm),('scale',0,.6,.6,.6),('scale',0.8,1,1,1,cm)],
        'neck':[('rotation',0,40,0,0),('rotation',0.9,30,0,0,cm),('rotation',1.2,-15,0,0,cm),('rotation',1.6,0,0,0,cm)],
        'head':[('rotation',0,0,0,0),('rotation',1.0,0,0,0),('rotation',1.1,0,0,15,LIN),('rotation',1.2,0,0,-15,LIN),('rotation',1.3,0,0,10,LIN),('rotation',1.6,0,0,0,cm)],
        'jaw':[('rotation',0,0,0,0),('rotation',1.2,-30,0,0,cm),('rotation',1.6,0,0,0,cm)]},G),
      anim(n+'idle',2.0,'loop',{
        'body':[('position',0,0,0,0,cm),('position',1.0,0,-0.3,0,cm),('position',2.0,0,0,0,cm)],
        'head':[('rotation',0,0,10,0,cm),('rotation',1.0,-4,-10,0,cm),('rotation',2.0,0,10,0,cm)],
        'jaw':[('rotation',0,0,0,0,cm),('rotation',1.0,-8,0,0,cm),('rotation',2.0,0,0,0,cm)],
        'tail':[('rotation',0,0,12,0,cm),('rotation',1.0,0,-12,0,cm),('rotation',2.0,0,12,0,cm)],
        'tail2':[('rotation',0,0,-10,0,cm),('rotation',1.0,0,10,0,cm),('rotation',2.0,0,-10,0,cm)]},G),
      anim(n+'walk',W,'loop',M_walk(W,26,22,32,'%s_front_forearm','%s_hind_shin',0.5,{
        'neck':[('rotation',0,4,0,0,cm),('rotation',W/2,-2,0,0,cm),('rotation',W,4,0,0,cm)],
        'tail':[('rotation',0,0,10,0,cm),('rotation',W/2,0,-10,0,cm),('rotation',W,0,10,0,cm)]}),G),
      anim(n+'attack',0.9,'once',{   # 몸을 낮췄다가 물어뜯기 (타격 0.38s)
        'body':[('rotation',0,0,0,0),('rotation',0.25,-8,0,0,cm),('rotation',0.38,10,0,0,LIN),('rotation',0.9,0,0,0,cm),
                ('position',0,0,0,0),('position',0.25,0,-1.5,2,cm),('position',0.38,0,0.5,-5,LIN),('position',0.55,0,0,-5),('position',0.9,0,0,0,cm)],
        'neck':[('rotation',0,0,0,0),('rotation',0.25,-15,0,0,cm),('rotation',0.38,-25,0,0,LIN),('rotation',0.9,0,0,0,cm)],
        'jaw':[('rotation',0,0,0,0),('rotation',0.25,-45,0,0,cm),('rotation',0.38,4,0,0,LIN),('rotation',0.6,0,0,0),('rotation',0.9,0,0,0)],
        'right_front_leg':[('rotation',0,0,0,0),('rotation',0.38,40,0,0,LIN),('rotation',0.9,0,0,0,cm)],
        'left_front_leg':[('rotation',0,0,0,0),('rotation',0.38,35,0,0,LIN),('rotation',0.9,0,0,0,cm)],
        'right_hind_leg':[('rotation',0,0,0,0),('rotation',0.25,15,0,0,cm),('rotation',0.38,-25,0,0,LIN),('rotation',0.9,0,0,0,cm)],
        'left_hind_leg':[('rotation',0,0,0,0),('rotation',0.25,15,0,0,cm),('rotation',0.38,-25,0,0,LIN),('rotation',0.9,0,0,0,cm)]},G),
      anim(n+'death',1.6,'hold',{
        'root':[('rotation',0,0,0,0),('rotation',0.6,0,0,85,cm),('position',0,0,0,0),('position',0.6,0,1,0,cm),('position',1.6,0,-1,0,cm)],
        'neck':[('rotation',0,0,0,0),('rotation',0.4,-20,0,0,cm),('rotation',1.2,20,0,0,cm)],
        'jaw':[('rotation',0,0,0,0),('rotation',0.4,-35,0,0,cm),('rotation',1.4,-15,0,0,cm)],
        'right_front_leg':[('rotation',0,0,0,0),('rotation',1.0,30,0,0,cm)],'left_front_leg':[('rotation',0,0,0,0),('rotation',1.0,20,0,0,cm)],
        'right_hind_leg':[('rotation',0,0,0,0),('rotation',1.0,-25,0,0,cm)],'left_hind_leg':[('rotation',0,0,0,0),('rotation',1.0,-15,0,0,cm)],
        'tail':[('rotation',0,0,0,0),('rotation',1.0,-30,0,0,cm)]},G),
    ]

# =====================================================================
# 2) 촛농 짐승
# =====================================================================
def build_brute():
    m_art={**sym(11,[(1,3),(2,3),(3,3),(2,4),(3,4),(3,5)]),(2,4):'E',(8,4):'E',
           **{(x,8):'F' for x in range(3,8)},(3,7):'F',(7,7):'F',(6,0):'C',(6,1):'C',(5,2):'C'}
    jaw=Group('jaw',[0,13,-12],[
        C('jaw',[-4,9,-19],[4,13,-12],skin('BODY')),
        C('tusk_r',[2.5,12,-19],[3.5,16,-18],solidp('MASK')),C('tusk_l',[-3.5,12,-19],[-2.5,16,-18],solidp('MASK')),
        fx('fx_maw',[0,12,-20])],[4,0,0])
    horn=lambda m,s:G_(f'{s}_horn',[5,21,-13],[C(f'{s}_horn1',[4,20,-14],[7,23,-11],waxdrip(0),m),
        G_(f'{s}_horn2',[7,22,-12.5],[C(f'{s}_horn2',[6,22,-13.5],[8,27,-11.5],waxdrip(0),m),
            G_(f'{s}_horn3',[7,27,-12.5],[C(f'{s}_horn3',[6.5,27,-13],[7.5,31,-12],claw(),m)],[-25,0,-10],m)],[0,0,-35],m)],None,m)
    head=Group('head',[0,18,-10],[
        C('head',[-5,12,-19],[5,22,-10],skin('BODY')),
        C('mask',[-5.5,13,-19.5],[5.5,23,-19.5],mask_art(m_art)),
        C('mask_rim',[-5.5,22,-19],[5.5,24,-13],waxdrip(0.5)),
        C('teeth_up',[-3.5,12,-19.5],[3.5,13,-18.5],solidp('MASK')),
        jaw,horn(False,'right'),horn(True,'left')],[10,0,0])
    candles=[]
    spots=[(-5,-6,7),(0,-8,9),(5,-5,6),(-3,-1,10),(3,0,8),(-6,4,6),(1,5,11),(6,6,7),(-2,9,6)]
    for i,(x,z,h) in enumerate(spots):
        candles+=candle(f'candle{i}',x,30,z,h,True,rot=[(z%3-1)*6,0,(x%3-1)*6])
    body=Group('body',[0,20,0],[
        C('torso',[-8,12,-10],[8,27,10],skin('BODY')),
        C('belly',[-6,10,-8],[6,13,8],M.cracked('BODY2',1.5)),
        C('wax_back',[-9,24,-11],[9,30,10],waxdrip(0.5)),
        C('wax_hump',[-7,28,-13],[7,33,-4],waxdrip(0.4)),
        C('wax_skirt_r',[8,14,-9],[9,26,9],waxdrip(0.4)),C('wax_skirt_l',[-9,14,-9],[-8,26,9],waxdrip(0.4)),
        C('tail',[-2,18,10],[2,22,15],skin('BODY2'),rot=[-20,0,0],origin=[0,20,10]),
        Group('candles',[0,30,0],candles),
        head,fx('fx_body',[0,22,0]),fx('fx_back',[0,40,0])])
    legs=[]
    for m,s in((False,'right'),(True,'left')):
        legs.append(leg(f'{s}_front_leg',m,[6,16,-6],7,None,2,3,f'{s}_front_forearm',skin('BODY'),M.cracked('BODY2',1.0)))
        legs.append(leg(f'{s}_hind_leg',m,[6,16,6],7,None,2,3,f'{s}_hind_shin',skin('BODY'),M.cracked('BODY2',1.0)))
    legs[0].children[1].children.append(fx('fx_stomp_r',[6,0,-6]))
    return Group('root',[0,0,0],[body,*legs])

def anims_brute(G):
    n='animation.wax_brute.'; cm='catmullrom'; LIN='linear'; W=1.4
    return [
      anim(n+'spawn',2.2,'once',{
        'root':[('position',0,0,-20,0),('position',1.2,0,1,0,cm),('position',1.4,0,0,0,cm)],
        'body':[('rotation',0,20,0,0),('rotation',1.2,10,0,0,cm),('rotation',1.6,18,0,0,cm),('rotation',2.2,0,0,0,cm)],
        'head':[('rotation',0,-20,0,0),('rotation',1.4,-10,0,0,cm),('rotation',1.7,25,0,0,cm),('rotation',2.2,0,0,0,cm)],
        'jaw':[('rotation',0,0,0,0),('rotation',1.4,0,0,0),('rotation',1.7,-35,0,0,cm),('rotation',2.2,0,0,0,cm)]},G),
      anim(n+'idle',2.8,'loop',{
        'body':[('position',0,0,0,0,cm),('position',1.4,0,-0.6,0,cm),('position',2.8,0,0,0,cm),('scale',0,1,1,1,cm),('scale',1.4,1.02,1.02,1.02,cm),('scale',2.8,1,1,1,cm)],
        'head':[('rotation',0,0,6,0,cm),('rotation',1.4,4,-6,0,cm),('rotation',2.8,0,6,0,cm)],
        'jaw':[('rotation',0,0,0,0,cm),('rotation',1.4,-6,0,0,cm),('rotation',2.8,0,0,0,cm)]},G),
      anim(n+'walk',W,'loop',M_walk(W,18,16,26,'%s_front_forearm','%s_hind_shin',0.8,{
        'head':[('rotation',0,0,5,0,cm),('rotation',W/2,0,-5,0,cm),('rotation',W,0,5,0,cm)],
        'body':None}),G),
      anim(n+'attack',1.5,'once',{   # 앞발 들고 일어섰다가 내려찍기 (타격 0.75s)
        'body':[('rotation',0,0,0,0),('rotation',0.55,28,0,0,cm),('rotation',0.75,-8,0,0,LIN),('rotation',1.0,-8,0,0),('rotation',1.5,0,0,0,cm),
                ('position',0,0,0,0),('position',0.55,0,5,1,cm),('position',0.75,0,-1,-3,LIN),('position',1.0,0,-1,-3),('position',1.5,0,0,0,cm)],
        'right_front_leg':[('rotation',0,0,0,0),('rotation',0.55,60,0,0,cm),('rotation',0.75,-5,0,0,LIN),('rotation',1.5,0,0,0,cm)],
        'left_front_leg':[('rotation',0,0,0,0),('rotation',0.55,55,0,0,cm),('rotation',0.75,-5,0,0,LIN),('rotation',1.5,0,0,0,cm)],
        'right_front_forearm':[('rotation',0,0,0,0),('rotation',0.55,-40,0,0,cm),('rotation',0.75,0,0,0,LIN)],
        'left_front_forearm':[('rotation',0,0,0,0),('rotation',0.55,-40,0,0,cm),('rotation',0.75,0,0,0,LIN)],
        'right_hind_leg':[('rotation',0,0,0,0),('rotation',0.55,-25,0,0,cm),('rotation',0.75,0,0,0,LIN)],
        'left_hind_leg':[('rotation',0,0,0,0),('rotation',0.55,-25,0,0,cm),('rotation',0.75,0,0,0,LIN)],
        'head':[('rotation',0,0,0,0),('rotation',0.55,20,0,0,cm),('rotation',0.75,-15,0,0,LIN),('rotation',1.5,0,0,0,cm)],
        'jaw':[('rotation',0,0,0,0),('rotation',0.55,-30,0,0,cm),('rotation',0.75,0,0,0,LIN)]},G),
      anim(n+'death',2.0,'hold',{
        'right_front_leg':[('rotation',0,0,0,0),('rotation',0.6,-60,0,10,cm)],'left_front_leg':[('rotation',0,0,0,0),('rotation',0.6,-60,0,-10,cm)],
        'right_hind_leg':[('rotation',0,0,0,0),('rotation',0.9,50,0,10,cm)],'left_hind_leg':[('rotation',0,0,0,0),('rotation',0.9,50,0,-10,cm)],
        'body':[('position',0,0,0,0),('position',0.9,0,-9,0,cm),('rotation',0,0,0,0),('rotation',0.6,-12,0,0,cm),('rotation',1.4,-6,0,12,cm)],
        'head':[('rotation',0,0,0,0),('rotation',0.5,25,0,0,cm),('rotation',1.4,-20,0,0,cm)],
        'jaw':[('rotation',0,0,0,0),('rotation',0.5,-35,0,0,cm),('rotation',1.4,-10,0,0,cm)]},G),
    ]

# =====================================================================
# 3) 등불 사슴 (원거리)
# =====================================================================
def build_stag():
    m_art={(1,1):'F',(1,2):'E',(3,1):'F',(3,2):'E',(2,0):'C'}
    def antler(m,s):
        tines=[]
        for i,(yy,ang) in enumerate([(44,30),(48,-20)]):
            tines.append(G_(f'{s}_tine{i}',[4.5,yy,-10],[C(f'{s}_tine{i}',[4,yy,-10.5],[5,yy+5,-9.5],skin('BODY2'),m)]+
                            candle(f'{s}_tine{i}_c',4.5,yy+5,-10,3,True)[:0],[0,0,-ang],m))
        tip_c=[box(f'{s}_candle',[4.5,52.5,-10],[2,3,2],wax(),m),box(f'{s}_wick',[4.5,54.5,-10],[1,1,1],glow('VOID'),m),
               box(f'{s}_flame',[4.5,56,-10],[1,2,1],glow('EMBER'),m)]
        side_c=[box(f'{s}_candle2',[8.5,48.5,-10],[2,3,2],wax(),m),box(f'{s}_wick2',[8.5,50.5,-10],[1,1,1],glow('VOID'),m),
                box(f'{s}_flame2',[8.5,52,-10],[1,2,1],glow('EMBER'),m)]
        return G_(f'{s}_antler',[2,40,-10],[
            C(f'{s}_beam1',[1.5,40,-10.5],[3.5,45,-9.5],skin('BODY2'),m,rot=[0,0,-25],origin=[2,40,-10]),
            C(f'{s}_beam2',[4,44,-10.5],[5,52,-9.5],skin('BODY2'),m),
            C(f'{s}_branch',[5,45,-10.5],[9,46,-9.5],skin('BODY2'),m),
            C(f'{s}_branch2',[8,46,-10.5],[9,47,-9.5],skin('BODY2'),m),
            *tines,*tip_c,*side_c],None,m)
    lantern=Group('lantern',[0,33,-17],[
        C('lantern_ring',[-1,31,-17.5],[1,33,-16.5],metal()),
        Group('lantern_cage',[0,31,-17],[
            C('lantern_top',[-2,29,-19],[2,30,-15],metal()),
            C('lantern_core',[-1.5,25,-18.5],[1.5,29,-15.5],core()),
            *[C(f'lantern_bar{i}',[bx-0.5,25,bz-0.5],[bx+0.5,29,bz+0.5],metal()) for i,(bx,bz) in enumerate([(-1.5,-18.5),(1.5,-18.5),(-1.5,-15.5),(1.5,-15.5)])],
            C('lantern_base',[-2,24,-19],[2,25,-15],metal()),
            fx('fx_lantern',[0,27,-17])],[0,0,0])])
    jaw=Group('jaw',[0,33,-13],[C('jaw',[-1.5,32,-18],[1.5,33,-13],skin('BODY2'))],[0,0,0])
    head=Group('head',[0,37,-9],[
        C('skull',[-2.5,34,-14],[2.5,40,-8],skin('BODY')),
        C('mask',[-2.5,35,-14.5],[2.5,40,-14.5],mask_art(m_art)),
        C('snout',[-1.5,33,-18],[1.5,36,-14],mask_art({(0,1):'F',(2,1):'F'})),
        C('ear_r',[2.5,37,-11],[5.5,38,-10],ashcloth('BODY2',0.5,0.3,0.1,False),rot=[0,0,-20],origin=[2.5,37.5,-10.5]),
        C('ear_l',[-5.5,37,-11],[-2.5,38,-10],ashcloth('BODY2',0.5,0.3,0.1,False),rot=[0,0,20],origin=[-2.5,37.5,-10.5]),
        antler(False,'right'),antler(True,'left'),jaw,lantern],[0,0,0])
    neck=Group('neck',[0,27,-6],[
        C('neck',[-2,26,-10],[2,38,-6],skin('BODY')),
        C('neck_mane',[-2.5,28,-7],[2.5,38,-5],ashcloth('CLOTH',0.4,0.4)),
        head],[-12,0,0])
    body=Group('body',[0,24,0],[
        C('torso',[-3.5,20,-8],[3.5,27,8],skin('BODY')),
        C('ribs',[-4,20,-6],[4,25,0],M.cracked('BODY2',1.6)),
        C('shroud',[-4.5,25,-9],[4.5,28,6],ashcloth('CLOTH',0.6,0.0)),
        C('shroud_r',[4,14,-8],[5,27,5],ashcloth('CLOTH',0.35,0.45)),
        C('shroud_l',[-5,14,-8],[-4,27,5],ashcloth('CLOTH',0.35,0.45)),
        C('shroud_back',[-4,12,5],[4,27,6],ashcloth('CLOTH_D',0.35,0.45),rot=[10,0,0],origin=[0,27,5.5]),
        Group('tail',[0,25,8],[C('tail',[-1,21,8],[1,25,10],ashcloth('CLOTH_D',0.3,0.4,0.1,False))],[20,0,0]),
        neck,fx('fx_body',[0,24,0])])
    legs=[]
    for m,s in((False,'right'),(True,'left')):
        legs.append(leg(f'{s}_front_leg',m,[2.5,21,-6],10,None,2,2,f'{s}_front_forearm',skin('BODY'),skin('BODY2'),hoof=True))
        legs.append(leg(f'{s}_hind_leg',m,[2.5,21,6],10,None,2,2,f'{s}_hind_shin',skin('BODY'),skin('BODY2'),hoof=True,rot=[-10,0,0],lrot=[18,0,0]))
    return Group('root',[0,0,0],[body,*legs])

def anims_stag(G):
    n='animation.lantern_stag.'; cm='catmullrom'; LIN='linear'; W=1.2
    return [
      anim(n+'spawn',2.2,'once',{   # 무릎 꿇은 채로 재 속에서 일어남
        'root':[('position',0,0,-10,0),('position',1.0,0,-6,0,cm),('position',1.6,0,0,0,cm)],
        'right_front_leg':[('rotation',0,-80,0,0),('rotation',1.0,-70,0,0,cm),('rotation',1.6,0,0,0,cm)],
        'left_front_leg':[('rotation',0,-80,0,0),('rotation',1.0,-70,0,0,cm),('rotation',1.6,0,0,0,cm)],
        'right_front_forearm':[('rotation',0,120,0,0),('rotation',1.0,110,0,0,cm),('rotation',1.6,0,0,0,cm)],
        'left_front_forearm':[('rotation',0,120,0,0),('rotation',1.0,110,0,0,cm),('rotation',1.6,0,0,0,cm)],
        'neck':[('rotation',0,40,0,0),('rotation',1.2,30,0,0,cm),('rotation',1.8,-10,0,0,cm),('rotation',2.2,0,0,0,cm)],
        'lantern_cage':[('rotation',0,0,0,0),('rotation',1.8,25,0,0,cm),('rotation',2.2,0,0,0,cm)]},G),
      anim(n+'idle',2.8,'loop',{
        'neck':[('rotation',0,0,6,0,cm),('rotation',1.4,3,-6,0,cm),('rotation',2.8,0,6,0,cm)],
        'head':[('rotation',0,0,0,4,cm),('rotation',1.4,0,0,-4,cm),('rotation',2.8,0,0,4,cm)],
        'lantern_cage':[('rotation',0,8,0,4,cm),('rotation',1.4,-8,0,-4,cm),('rotation',2.8,8,0,4,cm)],
        'body':[('position',0,0,0,0,cm),('position',1.4,0,-0.3,0,cm),('position',2.8,0,0,0,cm)],
        'tail':[('rotation',0,0,10,0,cm),('rotation',1.4,0,-10,0,cm),('rotation',2.8,0,10,0,cm)]},G),
      anim(n+'walk',W,'loop',M_walk(W,22,20,34,'%s_front_forearm','%s_hind_shin',0.5,{
        'neck':[('rotation',0,4,0,0,cm),('rotation',W/2,-2,0,0,cm),('rotation',W,4,0,0,cm)],
        'lantern_cage':[('rotation',0,18,0,0,cm),('rotation',W/2,-14,0,0,cm),('rotation',W,18,0,0,cm)]}),G),
      anim(n+'attack',1.4,'once',{   # 원거리: 고개를 숙여 등불에 불을 모았다가(0~0.6) 위로 젖히며 던짐(0.75 발사)
        'neck':[('rotation',0,0,0,0),('rotation',0.6,30,0,0,cm),('rotation',0.75,-35,0,0,LIN),('rotation',0.95,-30,0,0),('rotation',1.4,0,0,0,cm)],
        'head':[('rotation',0,0,0,0),('rotation',0.6,15,0,0,cm),('rotation',0.75,-20,0,0,LIN),('rotation',1.4,0,0,0,cm)],
        'lantern_cage':[('rotation',0,0,0,0),('rotation',0.6,-20,0,0,cm),('rotation',0.75,-80,0,0,LIN),('rotation',0.95,-60,0,0),('rotation',1.4,0,0,0,cm),
                        ('scale',0,1,1,1),('scale',0.6,1.35,1.35,1.35,cm),('scale',0.75,1,1,1,LIN)],
        'body':[('rotation',0,0,0,0),('rotation',0.6,-6,0,0,cm),('rotation',0.75,8,0,0,LIN),('rotation',1.4,0,0,0,cm)],
        'right_front_leg':[('rotation',0,0,0,0),('rotation',0.6,12,0,0,cm),('rotation',0.75,-6,0,0,LIN),('rotation',1.4,0,0,0,cm)],
        'left_front_leg':[('rotation',0,0,0,0),('rotation',0.6,12,0,0,cm),('rotation',0.75,-6,0,0,LIN),('rotation',1.4,0,0,0,cm)]},G),
      anim(n+'death',2.2,'hold',{
        'right_front_leg':[('rotation',0,0,0,0),('rotation',0.6,-75,0,0,cm)],'left_front_leg':[('rotation',0,0,0,0),('rotation',0.6,-75,0,0,cm)],
        'right_front_forearm':[('rotation',0,0,0,0),('rotation',0.6,110,0,0,cm)],'left_front_forearm':[('rotation',0,0,0,0),('rotation',0.6,110,0,0,cm)],
        'root':[('position',0,0,0,0),('position',0.6,0,-6,0,cm),('position',1.4,0,-2,0,cm),('rotation',0,0,0,0),('rotation',0.6,0,0,0),('rotation',1.4,0,0,80,cm)],
        'neck':[('rotation',0,0,0,0),('rotation',0.6,25,0,0,cm),('rotation',1.6,45,0,0,cm)],
        'lantern_cage':[('rotation',0,0,0,0),('rotation',1.6,60,0,0,cm)]},G),
    ]

# ---------------- walk_tracks (스킬 템플릿) ----------------
def M_walk(L, fa, ha, lift, lower_f, lower_h, bob, extra=None):
    cm='catmullrom'; h=L/2; q=L/4
    sw=lambda a:[('rotation',0,a,0,0,cm),('rotation',h,-a,0,0,cm),('rotation',L,a,0,0,cm)]
    lift_mid=lambda t:[('rotation',0,0,0,0,cm),('rotation',t-q,0,0,0,cm),('rotation',t,-lift,0,0,cm),('rotation',min(L,t+q),0,0,0,cm)]+([('rotation',L,0,0,0,cm)] if t+q<L else [])
    tr={'right_front_leg':sw(fa),'left_hind_leg':sw(ha),'left_front_leg':sw(-fa),'right_hind_leg':sw(-ha),
        lower_f%'right':lift_mid(3*q), lower_h%'left':lift_mid(3*q),
        lower_f%'left':lift_mid(q), lower_h%'right':lift_mid(q),
        'body':[('position',0,0,0,0,cm),('position',q,0,-bob,0,cm),('position',h,0,0,0,cm),('position',3*q,0,-bob,0,cm),('position',L,0,0,0,cm)]}
    for k,v in tr.items():
        seen=set(); tr[k]=[x for x in v if not ((x[0],x[1]) in seen or seen.add((x[0],x[1])))]
    for k,v in (extra or {}).items():
        if v is not None: tr[k]=v
    return tr

MOBS=[('ash_hound',build_hound,anims_hound,(0.9,1.5,1.2),21),
      ('wax_brute',build_brute,anims_brute,(1.4,1.9,1.1),22),
      ('lantern_stag',build_stag,anims_stag,(0.8,2.6,2.3),23)]

if __name__=='__main__':
    O='/mnt/user-data/outputs/'
    for mid,b,a,hb,seed in MOBS:
        pal(BASE_PAL); export(mid,b(),a,seed,O+mid+'.bbmodel',hb)
        pal(SHINY_PAL); export(mid+'_shiny',b(),a,seed,O+mid+'_shiny.bbmodel',hb)
    pal(BASE_PAL)
