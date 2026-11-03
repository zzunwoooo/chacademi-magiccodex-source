"""모르디안의 원령 기믹 소품 3종.
- mordian_stake       ① 결박의 그림자 말뚝 (높이 약 2.6블록, 바닥에 박힌 검은 말뚝 + 사슬 + 보라 균열)
- mordian_shadow      ③ 각자의 그림자 (플레이어 크기 그림자 인형, 얼굴 대신 긁힌 자국)
- mordian_seal_pillar ⑥ 대마법사의 봉인석 기둥 (높이 약 4.5블록, 회백 돌 + 금빛 봉인 룬)
"""
from gen import *
import math, os, json

def C(name, frm, to, paint, **kw): return Cube(name, frm, to, paint, **kw)
def mirror_x(v): return [-v[0],v[1],v[2]]
def lerp(a,b,f): return tuple(int(a[i]+(b[i]-a[i])*f) for i in range(3))

# ------------------------------------------------ palette (shiny 없음)
INK=(14,9,20); INK2=(30,18,44); VEIN=(118,52,190); GLOW=(214,150,255,255)
GOLD=(140,112,58); DGOLD=(78,60,30)
STONE=(176,170,160); DSTONE=(118,112,106); LSTONE=(214,208,196); SEAL=(236,214,150,255); SEAL2=(248,240,210,255)

def ink(rag=0.25, veins=True, glow=0.0):
    def p(img, face, r, rng):
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1; ph=rng.uniform(0,6.28)
        for ly in range(h):
            for lx in range(w):
                c=lerp(INK2,INK,ly/max(1,h-1)*0.8 if face not in('up','down') else .4)
                if veins and face not in('up','down') and w>=3 and h>=4 and abs(lx-(w/2+math.sin(ly*.4+ph)*w*.25))<.55 and (ly//3)%3!=0:
                    c=lerp(c,VEIN,.4)
                a=255
                if face not in('up','down') and ly==h-1 and rng.random()<rag: a=0
                img.putpixel((x1+lx,y1+ly),col(c,SHADE[face]*(1+rng.uniform(-.1,.1)),a))
        if glow and face in('north','south','east','west') and w>=3 and h>=3:
            for _ in range(int(w*h*glow+rng.random())):
                px(img,r,rng.randrange(w),rng.randrange(h),col(GLOW))
    return p

def chain(img, face, r, rng):
    x1,y1,x2,y2=r
    for y in range(y1,y2):
        for x in range(x1,x2):
            img.putpixel((x,y),col(DGOLD if (x-x1+y-y1)%3==0 else GOLD,SHADE[face]*(1+rng.uniform(-.1,.1))))

def stake_body(img, face, r, rng):
    ink(0,True)(img,face,r,rng)
    x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
    if face in ('north','south','east','west') and h>8:
        # 세 줄 긁힘 (보라 발광)
        for k in range(3):
            for i in range(min(h-2,10)):
                x=1+k+i//3; y=2+i
                if 0<=x<w: px(img,r,x,y,col(GLOW if i%4 else VEIN))

def mask_face(img, face, r, rng):
    """그림자 얼굴: 칠흑 + 세 줄 긁힘 + 보라 눈 두 점."""
    ink(0,False)(img,face,r,rng)
    if face!='north': return
    x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
    for ex in (1,w-3):
        px(img,r,ex,3,col(GLOW)); px(img,r,ex+1,3,col(GLOW,.8))
    for k,(sx,sy) in enumerate([(w-1,0),(w-1,2),(w-3,0)]):
        for i in range(h):
            x=sx-i; y=sy+i
            if 0<=x<w and 0<=y<h: px(img,r,x,y,col((170,120,230)))

def stone(top=False, runes=False):
    def p(img, face, r, rng):
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        for ly in range(h):
            for lx in range(w):
                c=STONE
                if (ly%6==0 and face not in('up','down')): c=DSTONE
                n=rng.random()
                if n<.12: c=DSTONE
                elif n>.93: c=LSTONE
                img.putpixel((x1+lx,y1+ly),col(c,SHADE[face]*(1+rng.uniform(-.06,.06))))
        if runes and face in('north','south','east','west') and w>=4 and h>=8:
            cx=w//2
            for ly in range(2,h-2):
                if ly%5 in (0,1,2):
                    px(img,r,cx,ly,col(SEAL))
                    if ly%5==1: px(img,r,cx-1,ly,col(SEAL2)); px(img,r,cx+1,ly,col(SEAL2))
            for ly in (1,h-2):
                for lx in range(1,w-1): px(img,r,lx,ly,col(SEAL,0.9))
    return p

def solid_c(c):
    def p(img, face, r, rng):
        x1,y1,x2,y2=r
        for y in range(y1,y2):
            for x in range(x1,x2): img.putpixel((x,y),col(c,SHADE[face]*(1+rng.uniform(-.06,.06))))
    return p

# ------------------------------------------------ ① 그림자 말뚝
def stake():
    body=ink(0.3,True,0.004)
    top=Group('top',[0,30,0],[
        C('spike',[-2,30,-2],[2,38,2],stake_body),
        C('tip',[-1,38,-1],[1,44,1],ink(0,False)),
        C('tip_glow',[-0.5,44,-0.5],[0.5,46,0.5],solid_c(GLOW)),
        C('shard_r',[2,32,-1],[4,38,1],body,rot=[0,0,-22.5],origin=[2,32,0]),
        C('shard_l',[-4,30,-1],[-2,35,1],body,rot=[0,0,22.5],origin=[-2,30,0]),
    ])
    shaft=Group('shaft',[0,4,0],[
        C('shaft',[-3,4,-3],[3,30,3],stake_body),
        C('collar',[-4,18,-4],[4,21,4],chain),
        C('chain_a',[3.5,8,-0.5],[4.5,18,0.5],chain),
        C('chain_b',[-4.5,10,-0.5],[-3.5,18,0.5],chain),
        C('chain_c',[-0.5,6,3.5],[0.5,18,4.5],chain),
        top,
    ])
    base=Group('base',[0,0,0],[
        C('pool',[-9,0,-9],[9,1,9],ink(0.5,False,0.002)),
        C('root1',[-7,0,-2],[-3,3,2],body), C('root2',[3,0,-3],[8,4,1],body),
        C('root3',[-2,0,3],[2,3,8],body),  C('root4',[-1,0,-8],[2,3,-3],body),
        C('mound',[-5,1,-5],[5,5,5],ink(0.2,True)),
    ])
    return Group('root',[0,0,0],[base,shaft])

def stake_anims(G):
    n='animation.mordian_stake.'; cm='catmullrom'
    return [
      anim(n+'spawn',1.0,'once',{'shaft':[('position',0,0,-30,0),('position',0.5,0,3,0,cm),('position',0.7,0,0,0,cm)],
                                 'base':[('scale',0,0.2,1,0.2),('scale',0.4,1.1,1,1.1,cm),('scale',0.6,1,1,1)]},G),
      anim(n+'idle',3.0,'loop',{'top':[('rotation',0,0,0,0,cm),('rotation',1.5,0,8,0,cm),('rotation',3.0,0,0,0,cm)],
                                'shaft':[('scale',0,1,1,1,cm),('scale',1.5,1.03,1,1.03,cm),('scale',3,1,1,1,cm)]},G),
      anim(n+'hit',0.3,'once',{'shaft':[('rotation',0,0,0,0),('rotation',0.08,6,0,-5),('rotation',0.3,0,0,0,cm)]},G),
      anim(n+'death',1.0,'hold',{'shaft':[('rotation',0,0,0,0),('rotation',0.2,8,0,10),('position',0,0,0,0),('position',1.0,0,-32,0,cm),
                                          ('scale',0,1,1,1),('scale',1.0,0.6,1,0.6,cm)],
                                 'base':[('scale',0,1,1,1),('scale',1.0,0.2,1,0.2,cm)]},G),
    ]

# ------------------------------------------------ ③ 각자의 그림자 (플레이어 크기)
def shadow():
    body=ink(0.3,True); tat=ink(0.6,False)
    def arm(side):
        m=side=='left'; P=lambda v: mirror_x(v) if m else v
        def Cm(nm,a,b,p):
            if m: a,b=[-b[0],a[1],a[2]],[-a[0],b[1],b[2]]
            return C(nm,a,b,p)
        fore=Group(f'{side}_forearm',P([5,13,0]),[
            Cm(f'{side}_forearm',[4,8,-1],[6,13,1],body),
            Cm(f'{side}_claw',[4,5,-1],[6,8,1],ink(0,False,0.06)),
        ])
        return Group(f'{side}_arm',P([5,22,0]),[Cm(f'{side}_upper',[4,13,-1],[6,22,1],body),
                                                Cm(f'{side}_tatter',[6,14,-1],[7,20,1],tat), fore])
    head=Group('head',[0,23,0],[
        C('head',[-3.5,23,-3.5],[3.5,30,3.5],mask_face),
        C('hood',[-4,27,-4],[4,31,3],ink(0.4,False)),
        C('wisp',[-1,30,0],[1,34,2],tat),
    ])
    body_g=Group('body',[0,12,0],[
        C('torso',[-4,12,-2],[4,23,2],body),
        C('cloak',[-4.5,4,1],[4.5,22,3],tat),
        head, arm('right'), arm('left'),
    ])
    tail=Group('tail',[0,12,0],[
        C('waist',[-3.5,6,-2],[3.5,12,2],body),
        C('tail1',[-3,2,-1.5],[3,6,1.5],tat),
        C('tail2',[-1.5,0,-1],[1.5,2,1],tat),
    ])
    return Group('root',[0,0,0],[tail,body_g])

def shadow_anims(G):
    n='animation.mordian_shadow.'; cm='catmullrom'; Z=lambda t:('rotation',t,0,0,0,cm)
    return [
      anim(n+'spawn',1.0,'once',{'root':[('position',0,0,-34,0),('position',0.7,0,1,0,cm),('position',0.9,0,0,0,cm),
                                          ('scale',0,1.3,0.3,1.3),('scale',0.7,0.95,1.05,0.95,cm),('scale',0.9,1,1,1)]},G),
      anim(n+'idle',2.0,'loop',{'root':[('position',0,0,0,0,cm),('position',1,0,1,0,cm),('position',2,0,0,0,cm)],
                                'tail':[('rotation',0,0,0,-6,cm),('rotation',1,0,0,6,cm),('rotation',2,0,0,-6,cm)],
                                'right_arm':[('rotation',0,-5,0,8,cm),('rotation',1,5,0,12,cm),('rotation',2,-5,0,8,cm)],
                                'left_arm':[('rotation',0,-5,0,-8,cm),('rotation',1,5,0,-12,cm),('rotation',2,-5,0,-8,cm)]},G),
      anim(n+'walk',0.8,'loop',{'body':[('rotation',0,-14,0,0,cm),('rotation',0.8,-14,0,0,cm)],
                                'head':[('rotation',0,10,0,0,cm),('rotation',0.8,10,0,0,cm)],
                                'tail':[('rotation',0,-20,0,-8,cm),('rotation',0.4,-20,0,8,cm),('rotation',0.8,-20,0,-8,cm)],
                                'right_arm':[('rotation',0,-35,0,10,cm),('rotation',0.4,-45,0,14,cm),('rotation',0.8,-35,0,10,cm)],
                                'left_arm':[('rotation',0,-35,0,-10,cm),('rotation',0.4,-45,0,-14,cm),('rotation',0.8,-35,0,-10,cm)],
                                'root':[('position',0,0,1,0,cm),('position',0.2,0,0,0,cm),('position',0.4,0,1,0,cm),('position',0.6,0,0,0,cm),('position',0.8,0,1,0,cm)]},G),
      anim(n+'attack',0.8,'once',{'body':[Z(0),('rotation',0.25,10,0,0,cm),('rotation',0.4,-22,0,0),Z(0.8)],
                                  'right_arm':[Z(0),('rotation',0.25,150,0,10,cm),('rotation',0.4,40,0,-10),Z(0.8)],
                                  'left_arm':[Z(0),('rotation',0.25,150,0,-10,cm),('rotation',0.4,40,0,10),Z(0.8)]},G),
      anim(n+'death',1.0,'hold',{'root':[('position',0,0,0,0),('position',1.0,0,-34,0,cm),('scale',0,1,1,1),('scale',1.0,1.4,0.3,1.4,cm)],
                                 'head':[Z(0),('rotation',0.4,-30,0,0,cm)]},G),
    ]

# ------------------------------------------------ ⑥ 봉인석 기둥
def pillar():
    st=stone(); rn=stone(runes=True)
    cap=Group('cap',[0,62,0],[
        C('cap',[-7,62,-7],[7,66,7],st),
        C('cap_top',[-5,66,-5],[5,69,5],st),
        C('seal_gem',[-2,69,-2],[2,73,2],solid_c(SEAL2)),
        C('seal_ring',[-3,70,-3],[3,71,3],solid_c(SEAL)),
    ])
    shaft=Group('shaft',[0,8,0],[
        C('shaft',[-5,8,-5],[5,62,5],rn),
        C('band1',[-6,22,-6],[6,24,6],solid_c(GOLD)),
        C('band2',[-6,44,-6],[6,46,6],solid_c(GOLD)),
        C('chip1',[5,30,-2],[6,36,2],st), C('chip2',[-6,48,-1],[-5,52,3],st),
        cap,
    ])
    base=Group('base',[0,0,0],[
        C('plinth',[-9,0,-9],[9,4,9],st),
        C('plinth2',[-7,4,-7],[7,8,7],rn),
        C('rubble1',[9,0,2],[12,2,5],st), C('rubble2',[-12,0,-5],[-9,2,-1],st),
    ])
    return Group('root',[0,0,0],[base,shaft])

def pillar_anims(G):
    n='animation.mordian_seal_pillar.'; cm='catmullrom'
    return [
      anim(n+'spawn',1.2,'once',{'root':[('position',0,0,-72,0),('position',0.9,0,2,0,cm),('position',1.2,0,0,0,cm)]},G),
      anim(n+'idle',3.0,'loop',{'cap':[('position',0,0,0,0,cm),('position',1.5,0,0.6,0,cm),('position',3,0,0,0,cm)]},G),
      anim(n+'hit',0.4,'once',{'shaft':[('rotation',0,0,0,0),('rotation',0.08,0,0,2.5),('rotation',0.16,0,0,-2),('rotation',0.4,0,0,0,cm)]},G),
      anim(n+'death',1.2,'hold',{'root':[('position',0,0,0,0),('position',1.2,0,-72,0,cm)]},G),
    ]

if __name__=='__main__':
    import gen
    O='/home/claude/mord/props_out/'; os.makedirs(O,exist_ok=True)
    gen.HITBOX_JSON=O+'hitboxes.json'
    if os.path.exists(gen.HITBOX_JSON): os.remove(gen.HITBOX_JSON)
    export('mordian_stake', stake(), stake_anims, 11, O+'mordian_stake.bbmodel', (1.0,2.8,2.4))
    export('mordian_shadow', shadow(), shadow_anims, 12, O+'mordian_shadow.bbmodel', (0.7,2.0,1.7))
    export('mordian_seal_pillar', pillar(), pillar_anims, 13, O+'mordian_seal_pillar.bbmodel', (1.3,4.6,4.0))
