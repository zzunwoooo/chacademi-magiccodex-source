"""모르디안의 원령 (mordian_wraith) — 차카데미 3인 기믹 보스. 형태를 알아볼 수 없는 검은 그림자 덩어리 + 긁힌 가면."""
from gen import *
import math

def shift(node, dy):
    node.origin=[node.origin[0],node.origin[1]+dy,node.origin[2]]
    for c in node.children:
        if isinstance(c,Cube):
            c.frm=[c.frm[0],c.frm[1]+dy,c.frm[2]]; c.to=[c.to[0],c.to[1]+dy,c.to[2]]
            c.origin=[c.origin[0],c.origin[1]+dy,c.origin[2]]
        else: shift(c,dy)
def mirror_x(v): return [-v[0],v[1],v[2]]
def C(name, frm, to, paint, mirror=False, **kw):
    if mirror:
        frm,to=[-to[0],frm[1],frm[2]],[-frm[0],to[1],to[2]]
        if 'origin' in kw: kw['origin']=mirror_x(kw['origin'])
        if 'rot' in kw: kw['rot']=[kw['rot'][0],-kw['rot'][1],-kw['rot'][2]]
    return Cube(name, frm, to, paint, **kw)

# ---------------- 팔레트 ----------------
SHADOW=(22,15,30); DSHADOW=(9,6,14); VEIN=(104,46,160); EYE=(214,150,255,255)
VOID=(3,1,6); MASK=(212,204,186); DMASK=(150,140,124); SCRATCH=(34,22,40)
CHAIN=(140,112,58); DCHAIN=(78,60,30); CLAWTIP=(150,84,220)
SHINY=dict(SHADOW=(34,8,12), DSHADOW=(14,3,5), VEIN=(190,30,44), EYE=(255,206,96,255),
           VOID=(6,1,2), MASK=(52,50,58), DMASK=(30,28,34), SCRATCH=(226,60,64),
           CHAIN=(186,190,200), DCHAIN=(96,100,110), CLAWTIP=(232,54,60))
HITBOX=(2.5, 7.6, 6.2)
def apply_palette(p): globals().update(p)

def lerp(a,b,f): return tuple(int(a[i]+(b[i]-a[i])*f) for i in range(3))

# ---------------- 페인터 ----------------
def smoke(ragged=0.3, eyes=0.0035, bottom_dark=True):
    """그림자 덩어리: 검보라 노이즈 + 흐르는 보라 맥 + 드문 발광 눈 틈 + 들쭉날쭉한 가장자리(투명)."""
    def p(img, face, r, rng):
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        ph=rng.uniform(0,6.28)
        for ly in range(h):
            for lx in range(w):
                f=SHADE[face]*(1+rng.uniform(-.12,.12))
                g=ly/max(1,h-1) if face not in('up','down') else .5
                c=lerp(SHADOW,DSHADOW,g*0.8 if bottom_dark else .3)
                wave=math.sin(ly*0.35+ph)*w*0.22
                if face not in('up','down') and w>=4 and h>=6 and abs(lx-(w/2+wave))<0.55 and (ly//3+int(ph))%3!=0:
                    c=lerp(c,VEIN,.35+rng.uniform(0,.15))
                a=255
                if face not in('up','down') and ly==h-1 and rng.random()<ragged: a=0
                if face not in('up','down') and ly==h-2 and rng.random()<ragged*.35: a=0
                img.putpixel((x1+lx,y1+ly),col(c,f,a))
        if face in('north','east','west','south') and w>=5 and h>=5:
            n=int(w*h*eyes+rng.random())
            for _ in range(n):
                lx=rng.randrange(1,w-2); ly=rng.randrange(1,h-2)
                px(img,r,lx,ly,col(EYE)); px(img,r,lx+1,ly,col(EYE,0.85))
                px(img,r,lx,ly+1,col(VOID)); px(img,r,lx+1,ly+1,col(VOID))
    return p

def hood_face():
    """후드 정면: 안쪽으로 빨려 들어가는 빈 공동."""
    base=smoke(0.15,0)
    def p(img, face, r, rng):
        base(img,face,r,rng)
        if face!='north': return
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1; cx,cy=w/2,h*0.58
        for ly in range(h):
            for lx in range(w):
                d=((lx+.5-cx)/(w*0.38))**2+((ly+.5-cy)/(h*0.42))**2
                if d<1: px(img,r,lx,ly,col(lerp(VOID,DSHADOW,d**2),1+rng.uniform(-.05,.05)))
    return p

def mask_paint(img, face, r, rng):
    """긁혀서 지워진 얼굴 — 기록 속 모르디안의 얼굴처럼 날카롭게 긁힌 가면."""
    x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
    for ly in range(h):
        for lx in range(w):
            c=lerp(MASK,DMASK,ly/max(1,h-1)*0.5+rng.uniform(0,.15))
            img.putpixel((x1+lx,y1+ly),col(c,SHADE[face]*(1+rng.uniform(-.05,.05))))
    if face!='north':
        return
    # 깨진 모서리
    for lx,ly in [(0,0),(1,0),(0,1),(w-1,h-1),(w-2,h-1),(w-1,h-2),(w-1,0)]:
        px(img,r,lx,ly,(0,0,0,0))
    # 눈구멍 + 발광
    for ex in (2,w-5):
        for dx in range(3):
            px(img,r,ex+dx,4,col(VOID)); px(img,r,ex+dx,5,col(VOID))
        px(img,r,ex+1,4,col(EYE)); px(img,r,ex+1,5,col(EYE,.7))
    # 날카로운 긁힘 4줄 (오른쪽 위 → 왼쪽 아래)
    for sx,sy,ln in [(w-1,0,13),(w-1,3,11),(w-3,0,8)]:
        for i in range(ln):
            x=sx-i; y=sy+i
            if 0<=x<w and 0<=y<h:
                px(img,r,x,y,col(SCRATCH,1+rng.uniform(-.1,.1)))
                if y+1<h: px(img,r,x,y+1,col(SCRATCH,.75))
    # 입 대신 갈라진 틈
    for lx in range(3,w-3):
        if rng.random()<.75: px(img,r,lx,h-3,col(VOID))

def claw(img, face, r, rng):
    x1,y1,x2,y2=r; h=y2-y1
    for y in range(y1,y2):
        for x in range(x1,x2):
            ly=y-y1
            c=CLAWTIP if (face not in('up','down') and ly>=h-3) else lerp(DSHADOW,VOID,.4)
            img.putpixel((x,y),col(c,SHADE[face]*(1+rng.uniform(-.06,.06))))

def chain(img, face, r, rng):
    x1,y1,x2,y2=r
    for y in range(y1,y2):
        for x in range(x1,x2):
            c=DCHAIN if (x-x1+y-y1)%3==0 else CHAIN
            img.putpixel((x,y),col(c,SHADE[face]*(1+rng.uniform(-.1,.1))))

# ---------------- 모델 ----------------
def build():
    sm=smoke(); sm2=smoke(0.5,0.002)

    def arm(side):
        m=side=='left'; P=lambda v: mirror_x(v) if m else v
        Rt=lambda r: [r[0],-r[1],-r[2]] if m else r
        claws=[C(f'{side}_claw{i+1}',[x,20,-5],[x+2,34,-3],claw,m) for i,x in enumerate([17,19.5,22])]
        hand=Group(f'{side}_hand',P([21,40,-1]),[
            C(f'{side}_palm',[17,34,-5],[25,41,3],sm,m),
            C(f'{side}_knuckle',[16.5,32,-6],[24.5,35,-2],sm,m),
            *claws,
            C(f'{side}_claw_back',[23,22,-1],[25,34,1],claw,m),
            C(f'{side}_thumb',[24.5,27,-4],[26.5,36,-2],claw,m),
        ],[10,0,0])
        chain_g=Group(f'{side}_chain',P([21,44,4]),[
            C(f'{side}_chain_link1',[20.5,37,3.5],[21.5,43,5.5],chain,m),
            C(f'{side}_chain_link2',[20,32,4],[22,38,5],chain,m),
            C(f'{side}_chain_link3',[20.5,27,3.5],[21.5,33,5.5],chain,m),
        ])
        fore=Group(f'{side}_forearm',P([21,64,-1]),[
            C(f'{side}_forearm',[18,40,-4],[24,66,3],sm,m),
            C(f'{side}_forearm_tatter',[17,46,-1],[19,60,4],sm2,m),
            C(f'{side}_shackle_n',[17,44,-5],[25,46,-4],chain,m),
            C(f'{side}_shackle_s',[17,44,3],[25,46,4],chain,m),
            C(f'{side}_shackle_e',[24,44,-4],[25,46,3],chain,m),
            C(f'{side}_shackle_w',[17,44,-4],[18,46,3],chain,m),
            chain_g, hand,
        ],[18,0,0])
        return Group(f'{side}_arm',P([21,88,-2]),[
            C(f'{side}_upper_arm',[17,62,-6],[25,90,4],sm,m),
            C(f'{side}_upper_tatter',[24,66,-3],[27,84,3],sm2,m),
            C(f'{side}_elbow_lump',[17,62,-2],[24,70,5],sm,m),
            fore,
        ],Rt([12,0,24]))

    def tendril(side):
        m=side=='left'; P=lambda v: mirror_x(v) if m else v
        Rt=lambda r: [r[0],-r[1],-r[2]] if m else r
        t3=Group(f'{side}_tendril3',P([9,121,10]),[
            C(f'{side}_tendril3',[7.5,120,8.5],[10.5,132,11.5],sm2,m),
            C(f'{side}_tendril_tip',[8,131,9],[10,138,11],claw,m),
        ],Rt([18,0,-8]))
        t2=Group(f'{side}_tendril2',P([9,107,10]),[
            C(f'{side}_tendril2',[7,106,8],[11,122,12],sm2,m), t3],Rt([12,0,-10]))
        return Group(f'{side}_tendril1',P([9,90,10]),[
            C(f'{side}_tendril1',[6.5,88,7.5],[11.5,108,12.5],sm,m),
            C(f'{side}_tendril1_root',[5,86,6],[13,94,14],sm,m), t2],Rt([22,0,-22]))

    mask=Group('mask',[0,98,-19.5],[
        C('mask',[-6,91,-20],[6,105,-19],mask_paint),
        C('mask_shard',[-5,88,-20],[-1,91,-19],mask_paint),
    ],[0,0,10])
    head=Group('head',[0,90,-4],[
        C('hood',[-11,88,-18],[11,112,4],hood_face()),
        C('hood_crown',[-8,108,-14],[8,118,2],sm),
        C('hood_peak',[-4,114,-19],[4,122,-9],sm2),
        C('hood_brim',[-10,104,-21],[10,110,-18],sm2),
        C('hood_drape_r',[11,86,-16],[14,104,0],sm2),
        C('hood_drape_l',[-14,86,-16],[-11,104,0],sm2),
        C('hood_back',[-9,90,4],[9,108,9],sm),
        C('hood_lump_r',[8,98,-8],[15,112,4],sm2),
        C('hood_tatter',[-14,94,2],[-9,114,7],sm2),
        mask,
    ])
    tail3=Group('tail3',[0,28,1],[
        C('tail3',[-5,14,-3],[5,28,6],sm2),
        C('tail3_wisp',[-3,8,-1],[3,16,4],sm2),
        C('tail_tip',[-1,3,0],[2,9,3],sm2),
    ])
    tail2=Group('tail2',[0,42,0],[
        C('tail2',[-8,26,-6],[8,42,7],sm),
        C('tail2_lump',[3,28,0],[10,36,8],sm2),
        C('tail2_lump2',[-10,32,-4],[-5,40,3],sm2),
        tail3])
    tail1=Group('tail1',[0,56,0],[
        C('tail1',[-11,40,-9],[11,58,9],sm),
        C('tail1_lump_l',[-14,44,-4],[-8,54,6],sm2),
        C('tail1_lump_r',[7,42,-6],[14,52,4],sm2),
        C('tail1_drip',[-4,36,-10],[4,46,-6],sm2),
        tail2])
    body=Group('body',[0,60,0],[
        C('chest',[-16,56,-12],[16,82,12],sm),
        C('hump',[-14,78,-6],[14,94,14],sm),
        C('belly',[-12,58,-16],[12,76,-12],sm),
        C('side_r',[16,60,-8],[20,76,6],sm2),
        C('side_l',[-20,60,-8],[-16,76,6],sm2),
        C('shoulder_r',[12,80,-8],[24,92,8],sm),
        C('shoulder_l',[-24,80,-8],[-12,92,8],sm),
        C('collar',[-12,86,-14],[12,92,-8],sm2),
        C('spike1',[-10,92,6],[-6,102,10],sm2),
        C('spike2',[4,94,4],[8,106,8],sm2),
        C('spike3',[-2,90,10],[2,99,17],sm2),
        C('spike4',[10,90,2],[14,98,6],sm2),
        C('spike5',[-15,88,0],[-11,96,4],sm2),
        C('back_drip',[-8,60,12],[6,74,16],sm2),
        C('rib_r',[8,62,-14],[14,70,-12],sm2),
        C('rib_l',[-14,66,-14],[-8,74,-12],sm2),
        C('mass_r',[14,52,-10],[24,66,6],sm),
        C('mass_l',[-22,56,-6],[-14,70,10],sm),
        C('shroud_back',[-16,46,8],[14,62,14],sm2),
        C('tatter1',[-14,40,-12],[-10,56,-8],sm2),
        C('tatter2',[8,36,-12],[12,54,-8],sm2),
        C('tatter3',[17,40,0],[21,56,4],sm2),
        C('tatter4',[-20,44,2],[-16,58,6],sm2),
        C('tatter5',[-6,40,10],[-2,58,14],sm2),
        C('shoulder_hump_l',[-26,88,-4],[-16,98,8],sm),
        C('neck_mass',[-14,84,-6],[-4,94,6],sm2),
        head, arm('right'), arm('left'), tendril('right'), tendril('left'), tail1,
    ])
    root=Group('root',[0,0,0],[body])
    return root

# ---------------- 애니메이션 ----------------
def anims(G):
    n='animation.mordian_wraith.'; cm='catmullrom'
    def osc(ch,L,a,b,phase=0,steps=4,axis=0):
        """루프용 사인 왕복 키: axis 축에 a~b."""
        out=[]
        for i in range(steps+1):
            t=L*i/steps; v=(a+b)/2+(b-a)/2*math.sin(2*math.pi*i/steps+phase)
            xyz=[0,0,0]; xyz[axis]=round(v,3); out.append((ch,round(t,4),*xyz,cm))
        return out
    def mirror_tracks(tr):
        out=dict(tr)
        for k,v in tr.items():
            if k.startswith('right_') and 'left_'+k[6:] not in tr:
                out['left_'+k[6:]]=[(c,t,x,-y if c=='rotation' else y,-z if c=='rotation' else z,*rest) if c!='position'
                                   else (c,t,-x,y,z,*rest) for (c,t,x,y,z,*rest) in v]
        return out
    Z=lambda t:('rotation',t,0,0,0,cm)

    spawn=mirror_tracks({
        'root':[('position',0,0,-120,0),('position',2.0,0,4,0,cm),('position',2.4,0,0,0,cm),
                ('scale',0,0.6,0.3,0.6),('scale',1.6,1,1.15,1,cm),('scale',2.3,1,1,1,cm)],
        'head':[('rotation',0,40,0,0),('rotation',1.8,40,0,0,cm),('rotation',2.4,-18,0,0,cm),('rotation',3.0,0,0,0,cm)],
        'mask':[('scale',0,0,0,0),('scale',1.8,0,0,0),('scale',2.3,1.3,1.3,1.3,cm),('scale',2.6,1,1,1,cm)],
        'right_arm':[('rotation',0,0,0,-55),('rotation',1.6,0,0,-55,cm),('rotation',2.3,-10,0,18,cm),('rotation',3.0,0,0,0,cm)],
        'right_forearm':[('rotation',0,40,0,0),('rotation',1.8,40,0,0,cm),('rotation',2.4,-10,0,0,cm),Z(3.0)],
        'right_tendril1':[('rotation',0,-70,0,0),('rotation',1.9,-70,0,0,cm),('rotation',2.6,8,0,0,cm),Z(3.0)],
        'tail1':[('rotation',0,-30,0,0),('rotation',2.2,10,0,0,cm),Z(3.0)],
    })
    idle=mirror_tracks({
        'root':osc('position',4,0,3,0,4,1),
        'body':osc('rotation',4,-2.5,2.5,0,4,2),
        'tail1':osc('rotation',4,-6,6,1.0,4,2),
        'tail2':osc('rotation',4,-9,9,2.0,4,2),
        'tail3':osc('rotation',4,-12,12,3.0,4,2),
        'head':osc('rotation',4,-6,6,0.5,4,1),
        'mask':osc('rotation',4,-4,4,1.5,4,2),
        'right_arm':osc('rotation',4,-7,3,0.7,4,0),
        'right_forearm':osc('rotation',4,-4,8,1.2,4,0),
        'right_hand':osc('rotation',4,-8,8,2.0,4,0),
        'right_chain':osc('rotation',4,-10,10,0.3,4,0),
        'right_tendril1':osc('rotation',4,-6,10,0,4,0),
        'right_tendril2':osc('rotation',4,-12,12,1.0,4,0),
        'right_tendril3':osc('rotation',4,-16,16,2.0,4,0),
    })
    # 걷기 = 다리 없이 미끄러지듯 부유 이동. 앞으로 숙이고 꼬리·팔·촉수가 뒤로 끌림.
    L=2.0
    def lean(v): return [('rotation',0,v,0,0,cm),('rotation',L,v,0,0,cm)]
    walk=mirror_tracks({
        'root':osc('position',L,-1.5,1.5,0,4,1),
        'body':[('rotation',0,-12,-3,0,cm),('rotation',L/2,-12,3,0,cm),('rotation',L,-12,-3,0,cm)],
        'head':lean(8),
        'tail1':[('rotation',0,-22,0,-6,cm),('rotation',L/2,-22,0,6,cm),('rotation',L,-22,0,-6,cm)],
        'tail2':[('rotation',0,-14,0,8,cm),('rotation',L/2,-14,0,-8,cm),('rotation',L,-14,0,8,cm)],
        'tail3':[('rotation',0,-12,0,-10,cm),('rotation',L/2,-12,0,10,cm),('rotation',L,-12,0,-10,cm)],
        'right_arm':[('rotation',0,-18,0,0,cm),('rotation',L/2,-26,0,4,cm),('rotation',L,-18,0,0,cm)],
        'right_forearm':[('rotation',0,-10,0,0,cm),('rotation',L/2,-18,0,0,cm),('rotation',L,-10,0,0,cm)],
        'right_chain':[('rotation',0,-25,0,0,cm),('rotation',L/2,-35,0,0,cm),('rotation',L,-25,0,0,cm)],
        'right_tendril1':[('rotation',0,15,0,0,cm),('rotation',L/2,22,0,0,cm),('rotation',L,15,0,0,cm)],
        'right_tendril2':osc('rotation',L,0,14,0.5,4,0),
        'right_tendril3':osc('rotation',L,0,18,1.5,4,0),
    })
    # 기본 공격: 오른손 큰 대각선 할퀴기 (근접 범위 넓게)
    attack={
        'root':[('position',0,0,0,0),('position',0.45,0,2,4,cm),('position',0.65,0,0,-10),('position',1.0,0,0,-10),('position',1.4,0,0,0,cm)],
        'body':[Z(0),('rotation',0.45,8,22,0,cm),('rotation',0.65,-10,-28,0),('rotation',1.0,-8,-24,0),Z(1.4)],
        'head':[Z(0),('rotation',0.45,10,10,0,cm),('rotation',0.65,-10,-12,0),Z(1.4)],
        'right_arm':[Z(0),('rotation',0.45,35,0,75,cm),('rotation',0.65,80,0,-30),('rotation',1.0,70,0,-25),Z(1.4)],
        'right_forearm':[Z(0),('rotation',0.45,-30,0,0,cm),('rotation',0.65,8,0,0),Z(1.4)],
        'right_hand':[Z(0),('rotation',0.45,-25,0,0,cm),('rotation',0.65,30,0,0),Z(1.4)],
        'left_arm':[Z(0),('rotation',0.45,-15,0,-10,cm),('rotation',0.65,-25,0,-20),Z(1.4)],
        'tail1':[Z(0),('rotation',0.65,-20,0,10),Z(1.4)],
    }
    attack2={  # 왼손 반대 방향 할퀴기 (연속기용)
        k.replace('right_','@').replace('left_','right_').replace('@','left_'):
        [(c,t,x,-y,-z,*r) if c=='rotation' else (c,t,-x,y,z,*r) for (c,t,x,y,z,*r) in v]
        for k,v in attack.items()}
    death={
        'root':[('position',0,0,0,0),('position',0.6,0,6,0,cm),('position',2.4,0,-95,0,cm),('position',3.0,0,-95,0)],
        'head':[Z(0),('rotation',0.6,-35,0,0,cm),('rotation',1.6,-20,0,0,cm),('rotation',2.0,0,0,0,cm)],
        'right_arm':[Z(0),('rotation',0.6,-20,0,60,cm),('rotation',1.6,150,0,25,cm),('rotation',2.4,170,0,10,cm)],
        'left_arm':[Z(0),('rotation',0.6,-20,0,-60,cm),('rotation',1.7,145,0,-25,cm),('rotation',2.4,170,0,-10,cm)],
        'right_hand':[Z(0),('rotation',1.2,-40,0,0,cm),('rotation',2.4,-20,0,0,cm)],
        'left_hand':[Z(0),('rotation',1.2,-40,0,0,cm),('rotation',2.4,-20,0,0,cm)],
        'right_tendril1':[Z(0),('rotation',0.6,-30,0,20,cm),('rotation',2.0,-10,0,40,cm)],
        'left_tendril1':[Z(0),('rotation',0.6,-30,0,-20,cm),('rotation',2.0,-10,0,-40,cm)],
        'tail1':[Z(0),('rotation',1.0,15,0,15,cm),('rotation',2.0,-10,0,-10,cm)],
        # 원령이 바닥으로 빨려 들어가고 긁힌 가면만 바닥에 떨어져 남는다
        'mask':[('position',0,0,0,0),('position',1.9,0,0,0),('position',2.4,0,3,-6,cm),('position',2.75,0,-2.5,-6,cm),('position',3.0,0,-2.5,-6),
                ('rotation',0,0,0,0),('rotation',1.9,0,0,0),('rotation',2.75,-90,0,25,cm),('rotation',2.9,-84,0,25),('rotation',3.0,-90,0,25)],
    }
    # 내려찍기: 두 팔을 머리 위로 들어 앞 바닥을 강타 (전방 넓은 범위)
    slam=mirror_tracks({
        'root':[('position',0,0,0,0),('position',0.7,0,10,3,cm),('position',0.95,0,-4,-6),('position',1.3,0,-4,-6),('position',1.8,0,0,0,cm)],
        'body':[Z(0),('rotation',0.7,18,0,0,cm),('rotation',0.95,-24,0,0),('rotation',1.3,-22,0,0),Z(1.8)],
        'head':[Z(0),('rotation',0.7,15,0,0,cm),('rotation',0.95,-6,0,0),Z(1.8)],
        'right_arm':[Z(0),('rotation',0.7,160,0,-10,cm),('rotation',0.95,55,0,-12),('rotation',1.3,55,0,-12),Z(1.8)],
        'right_forearm':[Z(0),('rotation',0.7,-25,0,0,cm),('rotation',0.95,10,0,0),Z(1.8)],
        'right_hand':[Z(0),('rotation',0.7,-20,0,0,cm),('rotation',0.95,40,0,0),Z(1.8)],
        'right_tendril1':[Z(0),('rotation',0.7,-25,0,0,cm),('rotation',0.95,20,0,0),Z(1.8)],
        'tail1':[Z(0),('rotation',0.7,15,0,0,cm),('rotation',0.95,-15,0,0),Z(1.8)],
    })
    # 의식 시전(루프): 두 팔을 앞으로 벌려 들고 손끝을 떨며 주문 — 금지된 의식·봉인 패턴 시작
    cast=mirror_tracks({
        'root':osc('position',2.4,6,9,0,4,1),
        'body':[('rotation',0,-10,0,0,cm),('rotation',2.4,-10,0,0,cm)],
        'head':[('rotation',0,-22,0,0,cm),('rotation',1.2,-26,0,0,cm),('rotation',2.4,-22,0,0,cm)],
        'right_arm':[('rotation',0,70,0,45,cm),('rotation',1.2,78,0,40,cm),('rotation',2.4,70,0,45,cm)],
        'right_forearm':[('rotation',0,15,0,0,cm),('rotation',1.2,25,0,0,cm),('rotation',2.4,15,0,0,cm)],
        'right_hand':[('rotation',0,-35,0,0,cm),('rotation',0.6,-45,0,0,cm),('rotation',1.2,-30,0,0,cm),('rotation',1.8,-45,0,0,cm),('rotation',2.4,-35,0,0,cm)],
        'right_tendril1':[('rotation',0,-25,0,25,cm),('rotation',1.2,-32,0,30,cm),('rotation',2.4,-25,0,25,cm)],
        'right_tendril2':osc('rotation',2.4,-10,10,0,4,0),
        'tail1':osc('rotation',2.4,-8,8,0,4,2),
        'mask':osc('rotation',2.4,-5,5,1,4,2),
    })
    # 사슬 던지기: 두 팔 뒤로 젖혔다가 앞으로 내던짐 — 결박의 사슬
    tether=mirror_tracks({
        'root':[('position',0,0,0,0),('position',0.5,0,0,6,cm),('position',0.75,0,0,-8),('position',1.6,0,0,0,cm)],
        'body':[Z(0),('rotation',0.5,15,0,0,cm),('rotation',0.75,-18,0,0),Z(1.6)],
        'right_arm':[Z(0),('rotation',0.5,-40,0,30,cm),('rotation',0.75,95,0,-5),('rotation',1.1,90,0,-5),Z(1.6)],
        'right_forearm':[Z(0),('rotation',0.5,-30,0,0,cm),('rotation',0.75,-5,0,0),Z(1.6)],
        'right_hand':[Z(0),('rotation',0.75,50,0,0),Z(1.6)],
        'right_chain':[Z(0),('rotation',0.5,40,0,0,cm),('rotation',0.75,-80,0,0),('rotation',1.1,-60,0,0),Z(1.6)],
        'head':[Z(0),('rotation',0.5,10,0,0,cm),('rotation',0.75,-8,0,0),Z(1.6)],
    })
    # 낙인 찍기: 오른손 손가락으로 한 명을 가리킴 — 저주의 낙인
    brand={
        'body':[Z(0),('rotation',0.4,-6,15,0,cm),('rotation',0.9,-6,15,0),Z(1.4)],
        'head':[Z(0),('rotation',0.4,8,10,-10,cm),('rotation',0.9,8,10,-10),Z(1.4)],
        'mask':[Z(0),('rotation',0.4,0,0,-15,cm),('rotation',0.9,0,0,-15),Z(1.4)],
        'right_arm':[Z(0),('rotation',0.4,88,0,-6,cm),('rotation',0.55,92,0,-6),('rotation',0.9,90,0,-6),Z(1.4)],
        'right_forearm':[Z(0),('rotation',0.4,-15,0,0,cm),('rotation',0.9,-15,0,0),Z(1.4)],
        'right_hand':[Z(0),('rotation',0.4,-20,0,0,cm),('rotation',0.9,-20,0,0),Z(1.4)],
    }
    # 포효: 머리 젖히고 팔·촉수 활짝 — 기믹 시작 신호/광폭화
    roar=mirror_tracks({
        'root':[('position',0,0,0,0),('position',0.5,0,-4,0,cm),('position',0.8,0,6,0,cm),('position',1.7,0,6,0),('position',2.2,0,0,0,cm)],
        'body':[Z(0),('rotation',0.5,14,0,0,cm),('rotation',0.8,-16,0,0,cm),('rotation',1.7,-14,0,0),Z(2.2)],
        'head':[Z(0),('rotation',0.5,20,0,0,cm),('rotation',0.8,-38,0,0,cm),('rotation',1.0,-34,0,4),('rotation',1.2,-38,0,-4),('rotation',1.4,-34,0,4),('rotation',1.7,-36,0,0),Z(2.2)],
        'mask':[Z(0),('rotation',0.9,0,0,6),('rotation',1.0,0,0,-6),('rotation',1.1,0,0,6),('rotation',1.2,0,0,-6),('rotation',1.3,0,0,0),Z(2.2)],
        'right_arm':[Z(0),('rotation',0.5,20,0,-25,cm),('rotation',0.8,-15,0,80,cm),('rotation',1.7,-12,0,76),Z(2.2)],
        'right_forearm':[Z(0),('rotation',0.8,-20,0,0,cm),Z(2.2)],
        'right_hand':[Z(0),('rotation',0.8,-40,0,0,cm),Z(2.2)],
        'right_tendril1':[Z(0),('rotation',0.5,20,0,0,cm),('rotation',0.8,-40,0,30,cm),('rotation',1.7,-36,0,28),Z(2.2)],
        'right_tendril2':[Z(0),('rotation',0.8,-20,0,0,cm),Z(2.2)],
        'right_tendril3':[Z(0),('rotation',0.8,-25,0,0,cm),Z(2.2)],
        'tail1':[Z(0),('rotation',0.8,-12,0,0,cm),Z(2.2)],
    })
    # 바닥으로 녹아 사라지기 / 다시 솟아오르기 — 순간이동·각자의 그림자
    vanish=mirror_tracks({
        'root':[('position',0,0,0,0),('position',0.3,0,4,0,cm),('position',1.2,0,-125,0,cm),
                ('scale',0,1,1,1),('scale',0.3,0.9,1.1,0.9,cm),('scale',1.2,1.4,0.4,1.4,cm)],
        'right_arm':[Z(0),('rotation',0.4,-20,0,40,cm),('rotation',1.2,-40,0,70,cm)],
        'head':[Z(0),('rotation',0.4,25,0,0,cm)],
    })
    emerge=mirror_tracks({
        'root':[('position',0,0,-125,0),('position',0.9,0,4,0,cm),('position',1.2,0,0,0,cm),
                ('scale',0,1.4,0.4,1.4),('scale',0.9,0.92,1.08,0.92,cm),('scale',1.2,1,1,1,cm)],
        'right_arm':[('rotation',0,-40,0,70),('rotation',0.9,-10,0,20,cm),Z(1.2)],
        'head':[('rotation',0,25,0,0),('rotation',0.9,-10,0,0,cm),Z(1.2)],
    })
    # 봉인에 저항(루프): 팔이 아래·뒤로 끌려가며 몸부림 — 최종 기믹 '대마법사의 봉인'
    Ls=1.6
    seal=mirror_tracks({
        'root':[('position',0,0,-8,0,cm),('position',0.4,0,-6,1,cm),('position',0.8,0,-9,-1,cm),('position',1.2,0,-6,1,cm),('position',Ls,0,-8,0,cm)],
        'body':[('rotation',0,18,-6,4,cm),('rotation',0.4,14,6,-4,cm),('rotation',0.8,20,-8,5,cm),('rotation',1.2,14,6,-3,cm),('rotation',Ls,18,-6,4,cm)],
        'head':[('rotation',0,-10,12,0,cm),('rotation',0.4,-20,-14,0,cm),('rotation',0.8,-8,14,0,cm),('rotation',1.2,-22,-12,0,cm),('rotation',Ls,-10,12,0,cm)],
        'right_arm':[('rotation',0,-35,0,-8,cm),('rotation',0.4,-25,0,4,cm),('rotation',0.8,-40,0,-10,cm),('rotation',1.2,-28,0,2,cm),('rotation',Ls,-35,0,-8,cm)],
        'right_forearm':[('rotation',0,-25,0,0,cm),('rotation',0.8,-10,0,0,cm),('rotation',Ls,-25,0,0,cm)],
        'right_hand':osc('rotation',Ls,-50,-10,0,4,0),
        'right_chain':osc('rotation',Ls,-30,30,0,4,0),
        'right_tendril1':osc('rotation',Ls,-30,20,0.7,4,0),
        'right_tendril3':osc('rotation',Ls,-25,25,1.4,4,0),
        'tail1':osc('rotation',Ls,-15,15,0,4,2),
    })
    # 그로기(루프): 바닥 가까이 내려앉아 축 늘어짐 — 기믹 성공 후 딜 타임
    Lg=3.0
    groggy=mirror_tracks({
        'root':osc('position',Lg,-30,-27,0,4,1),
        'body':[('rotation',0,28,0,0,cm),('rotation',Lg/2,32,0,0,cm),('rotation',Lg,28,0,0,cm)],
        'head':[('rotation',0,30,0,8,cm),('rotation',Lg/2,34,0,10,cm),('rotation',Lg,30,0,8,cm)],
        'mask':[('rotation',0,0,0,8,cm),('rotation',Lg,0,0,8,cm)],
        'right_arm':[('rotation',0,-25,0,-20,cm),('rotation',Lg/2,-22,0,-18,cm),('rotation',Lg,-25,0,-20,cm)],
        'right_forearm':[('rotation',0,-15,0,0,cm),('rotation',Lg,-15,0,0,cm)],
        'right_tendril1':[('rotation',0,40,0,25,cm),('rotation',Lg/2,46,0,28,cm),('rotation',Lg,40,0,25,cm)],
        'right_tendril2':[('rotation',0,30,0,0,cm),('rotation',Lg,30,0,0,cm)],
        'tail1':[('rotation',0,-30,0,0,cm),('rotation',Lg,-30,0,0,cm)],
    })
    # 사슬 회전: 양팔을 옆으로 뻗고 족쇄 사슬을 휘두르며 두 바퀴 (짤패턴 10)
    Lc=2.4
    chain_spin=mirror_tracks({
        'root':[('rotation',0,0,0,0),('rotation',0.45,0,-35,0,cm),('rotation',0.6,0,-20,0),('rotation',1.9,0,720,0),('rotation',Lc,0,720,0),
                ('position',0,0,0,0),('position',0.45,0,-3,0,cm),('position',0.6,0,2,0),('position',1.9,0,2,0),('position',Lc,0,0,0,cm)],
        'body':[Z(0),('rotation',0.45,10,0,0,cm),('rotation',0.6,-6,0,0),('rotation',1.9,-6,0,0),Z(Lc)],
        'head':[Z(0),('rotation',0.45,14,0,0,cm),('rotation',0.6,-8,0,0),('rotation',1.9,-8,0,0),Z(Lc)],
        'right_arm':[Z(0),('rotation',0.45,-10,0,45,cm),('rotation',0.6,-5,0,72),('rotation',1.9,-5,0,72),Z(Lc)],
        'right_forearm':[Z(0),('rotation',0.6,-15,0,0,cm),('rotation',1.9,-15,0,0),Z(Lc)],
        'right_hand':[Z(0),('rotation',0.6,-30,0,0,cm),('rotation',1.9,-30,0,0),Z(Lc)],
        'right_chain':[Z(0),('rotation',0.45,25,0,0,cm),('rotation',0.6,-70,0,30),('rotation',1.25,-75,0,38),('rotation',1.9,-70,0,30),Z(Lc)],
        'right_tendril1':[Z(0),('rotation',0.6,-20,0,30,cm),('rotation',1.9,-20,0,30),Z(Lc)],
        'right_tendril2':[Z(0),('rotation',0.6,-15,0,10,cm),('rotation',1.9,-15,0,10),Z(Lc)],
        'tail1':[Z(0),('rotation',0.6,-10,0,18,cm),('rotation',1.25,-10,0,22),('rotation',1.9,-10,0,18),Z(Lc)],
        'tail2':[Z(0),('rotation',0.6,0,0,14,cm),('rotation',1.9,0,0,14),Z(Lc)],
    })
    # 그림자 돌진: 뒤로 웅크렸다가 앞으로 확 쏘아지는 자세 (게임이 실제 이동, 짤패턴 11 — 3번 연속 재생)
    Ld=1.1
    dash=mirror_tracks({
        'root':[('position',0,0,0,0),('position',0.3,0,-3,6,cm),('position',0.42,0,2,-8),('position',0.75,0,2,-8),('position',Ld,0,0,0,cm)],
        'body':[Z(0),('rotation',0.3,14,0,0,cm),('rotation',0.42,-32,0,0),('rotation',0.75,-28,0,0),Z(Ld)],
        'head':[Z(0),('rotation',0.3,-6,0,0,cm),('rotation',0.42,24,0,0),('rotation',0.75,22,0,0),Z(Ld)],
        'right_arm':[Z(0),('rotation',0.3,30,0,20,cm),('rotation',0.42,-55,0,10),('rotation',0.75,-50,0,8),Z(Ld)],
        'right_forearm':[Z(0),('rotation',0.42,-20,0,0),('rotation',0.75,-18,0,0),Z(Ld)],
        'right_hand':[Z(0),('rotation',0.42,-25,0,0),Z(Ld)],
        'right_chain':[Z(0),('rotation',0.42,-60,0,0),('rotation',0.75,-50,0,0),Z(Ld)],
        'right_tendril1':[Z(0),('rotation',0.3,-15,0,0,cm),('rotation',0.42,35,0,-5),('rotation',0.75,32,0,-5),Z(Ld)],
        'right_tendril2':[Z(0),('rotation',0.42,20,0,0),Z(Ld)],
        'right_tendril3':[Z(0),('rotation',0.42,20,0,0),Z(Ld)],
        'tail1':[Z(0),('rotation',0.3,12,0,0,cm),('rotation',0.42,-40,0,0),('rotation',0.75,-36,0,0),Z(Ld)],
        'tail2':[Z(0),('rotation',0.42,-20,0,0),Z(Ld)],
        'tail3':[Z(0),('rotation',0.42,-20,0,0),Z(Ld)],
    })
    # 재봉인(최종, hold): 봉인 사슬에 팔이 바닥으로 끌려가고, 몸부림치다 마지막에 한 팔을 뻗은 채 바닥으로 빨려 들어감.
    # 긁힌 가면만 벗겨져 바닥에 떨어져 남는다. 끝나면 사망 판정.
    Le=5.0
    sealed=mirror_tracks({
        'root':[('position',0,0,0,0),('position',0.35,0,-6,0),('position',0.5,0,-4,0),('position',2.2,0,-10,0,cm),('position',3.2,0,-40,0,cm),
                ('position',3.5,0,-36,0),('position',4.4,0,-98,0,cm),('position',Le,0,-98,0),
                ('scale',0,1,1,1),('scale',0.35,1.08,0.9,1.08),('scale',0.6,1,1,1,cm),('scale',2.2,1.02,0.96,1.02),('scale',4.4,1.15,0.75,1.15,cm),('scale',Le,1.15,0.75,1.15)],
        'body':[Z(0),('rotation',0.35,22,0,0),('rotation',0.8,16,8,6,cm),('rotation',1.2,24,-8,-6,cm),('rotation',1.6,14,8,5,cm),('rotation',2.0,24,-8,-5,cm),
                ('rotation',2.4,18,0,0,cm),('rotation',3.2,-12,0,0,cm),('rotation',4.4,-20,0,0,cm)],
        'head':[Z(0),('rotation',0.35,-30,0,0),('rotation',0.8,-20,18,0,cm),('rotation',1.2,-34,-18,0,cm),('rotation',1.6,-20,16,0,cm),('rotation',2.0,-34,-16,0,cm),
                ('rotation',2.6,-40,0,0,cm),('rotation',3.4,-45,0,0,cm),('rotation',4.4,-30,0,0,cm)],
        # 양팔: 사슬이 바닥으로 끌어당김(뒤·아래로) → 몸부림 → 마지막에 오른팔만 위로 뻗음
        'right_arm':[Z(0),('rotation',0.35,-40,0,-25),('rotation',0.8,-30,0,-15,cm),('rotation',1.2,-45,0,-28,cm),('rotation',1.6,-30,0,-14,cm),('rotation',2.0,-45,0,-26,cm),
                     ('rotation',2.6,-35,0,-20,cm),('rotation',3.2,150,0,15,cm),('rotation',4.0,170,0,8,cm),('rotation',4.4,175,0,5)],
        'left_arm':[Z(0),('rotation',0.35,-40,0,25),('rotation',0.8,-46,0,28,cm),('rotation',1.2,-30,0,14,cm),('rotation',1.6,-46,0,28,cm),('rotation',2.0,-30,0,14,cm),
                    ('rotation',2.6,-45,0,25,cm),('rotation',3.2,-55,0,30,cm),('rotation',4.4,-60,0,30)],
        'right_forearm':[Z(0),('rotation',0.35,-25,0,0),('rotation',2.6,-25,0,0,cm),('rotation',3.2,-5,0,0,cm),('rotation',4.4,0,0,0)],
        'left_forearm':[Z(0),('rotation',0.35,-25,0,0),('rotation',4.4,-30,0,0,cm)],
        'right_hand':[Z(0),('rotation',0.8,-50,0,0,cm),('rotation',1.6,-20,0,0,cm),('rotation',2.4,-50,0,0,cm),('rotation',3.4,-55,0,0,cm),('rotation',4.0,-15,0,0,cm),('rotation',4.4,-60,0,0,cm)],
        'left_hand':[Z(0),('rotation',0.8,-20,0,0,cm),('rotation',1.6,-50,0,0,cm),('rotation',2.4,-20,0,0,cm),('rotation',4.4,-50,0,0,cm)],
        'right_chain':[Z(0),('rotation',0.35,45,0,0),('rotation',2.6,40,0,0,cm),('rotation',3.2,-20,0,0,cm),Z(4.4)],
        'right_tendril1':[Z(0),('rotation',0.35,-35,0,25),('rotation',1.0,-20,0,10,cm),('rotation',1.6,-40,0,30,cm),('rotation',2.4,-25,0,15,cm),('rotation',3.4,-50,0,40,cm),('rotation',4.4,-30,0,45,cm)],
        'right_tendril2':[Z(0),('rotation',1.0,-25,0,0,cm),('rotation',2.0,10,0,0,cm),('rotation',3.4,-30,0,0,cm)],
        'right_tendril3':[Z(0),('rotation',1.0,-30,0,0,cm),('rotation',2.0,15,0,0,cm),('rotation',3.4,-35,0,0,cm)],
        'tail1':[Z(0),('rotation',0.8,15,0,15,cm),('rotation',1.6,15,0,-15,cm),('rotation',2.4,15,0,12,cm),('rotation',3.4,30,0,0,cm)],
        'tail2':[Z(0),('rotation',1.2,0,0,-18,cm),('rotation',2.0,0,0,18,cm),('rotation',3.4,20,0,0,cm)],
        # 가면: 몸부림 중 흔들리다 3.5초에 벗겨져 바닥에 떨어짐 (root가 내려가도 바닥 높이에 남도록 위치 보정)
        'mask':[('position',0,0,0,0),('position',3.3,0,0,0),('position',3.6,0,10,-6,cm),('position',4.4,0,4,-10,cm),('position',4.6,0,-1.5,-10,cm),('position',Le,0,-1.5,-10),
                Z(0),('rotation',0.9,0,0,10,cm),('rotation',1.3,0,0,-10,cm),('rotation',1.7,0,0,8,cm),('rotation',2.1,0,0,-8,cm),('rotation',3.3,0,0,0,cm),
                ('rotation',4.4,-80,0,25,cm),('rotation',4.6,-90,0,25),('rotation',4.75,-84,0,25),('rotation',Le,-90,0,25)],
    })
    # death = 재봉인(sealed) 끝 자세 그대로 유지. 이 보스는 재봉인 연출 끝에만 죽으므로,
    # ModelEngine 이 사망 때 death 를 재생해도 모델이 솟아오르지 않게 한다.
    death={}
    for bone,keys in sealed.items():
        lastv={}
        for k in keys: lastv[k[0]]=k
        death[bone]=[(ch,t,x,y,z) for ch,(c_,_,x,y,z,*r) in lastv.items() for t in (0,1.0)]
    A=[('spawn',3.0,'once',spawn),('idle',4.0,'loop',idle),('walk',L,'loop',walk),('attack',1.4,'once',attack),
       ('death',3.0,'hold',death),('attack2',1.4,'once',attack2),('slam',1.8,'once',slam),('cast',2.4,'loop',cast),
       ('tether',1.6,'once',tether),('brand',1.4,'once',brand),('roar',2.2,'once',roar),('vanish',1.2,'hold',vanish),
       ('emerge',1.2,'once',emerge),('seal_struggle',Ls,'loop',seal),('groggy',Lg,'loop',groggy),
       ('chain_spin',Lc,'once',chain_spin),('dash',Ld,'once',dash),('sealed',Le,'hold',sealed)]
    out=[]
    for name,ln,lp,tr in A:
        for k,v in tr.items():   # 같은 채널·시간 중복 제거
            seen=set(); tr[k]=[x for x in v if not ((x[0],x[1]) in seen or seen.add((x[0],x[1])))]
        out.append(anim(n+name,ln,lp,tr,G))
    return out

def floated():
    r=build(); shift(r,4); return r

if __name__=='__main__':
    import os
    O='/mnt/user-data/outputs/'
    if os.path.exists(HITBOX_JSON): os.remove(HITBOX_JSON)
    export('mordian_wraith', floated(), anims, 7, O+'mordian_wraith.bbmodel', HITBOX)
    apply_palette(SHINY)
    export('mordian_wraith_shiny', floated(), anims, 7, O+'mordian_wraith_shiny.bbmodel', HITBOX)
