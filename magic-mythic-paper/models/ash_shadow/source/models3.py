"""재의 그림자 v3 — 새 컨셉
하얀 재 가면 / 머리 뒤 꺼진 촛불 고리 / 등에서 솟은 거대한 그림자 손 2개 / 가슴 등불 감옥에 갇힌 빛 / 재로 바스러지는 옷자락.
광폭화: 가면 절반이 깨져 불타는 얼굴, 촛불 고리 점화, 등손 비대화, 다리 실체화, 등불 감옥 파열.
"""
import gen, math, os
from gen import *

# ---------------- packer (대형 몹) ----------------
def pack2(cubes):
    for W,H in [(128,128),(256,128),(256,256),(512,256),(512,512),(1024,512)]:
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
def r5(v): return round(v*2)/2
def C(name, frm, to, paint, m=False, rot=None, origin=None):
    for a,b in zip(frm,to): assert abs((b-a)-round(b-a))<1e-6, (name,frm,to)
    if m:
        frm,to=[-to[0],frm[1],frm[2]],[-frm[0],to[1],to[2]]
        if origin: origin=mx(origin)
        if rot: rot=[rot[0],-rot[1],-rot[2]]
    return Cube(name, list(frm), list(to), paint, rot=rot, origin=origin)
def G_(name, origin, kids, rot=None, m=False):
    if m:
        origin=mx(origin)
        if rot: rot=[rot[0],-rot[1],-rot[2]]
    return Group(name, list(origin), kids, rot)
def box(name,c,s,paint,m=False,rot=None,origin=None):
    """중심 c, 크기 s(정수)로 큐브."""
    f=[c[i]-s[i]/2 for i in range(3)]; t=[c[i]+s[i]/2 for i in range(3)]
    return C(name,f,t,paint,m,rot,origin)
def shift(node, dy):
    node.origin=[node.origin[0],node.origin[1]+dy,node.origin[2]]
    for c in node.children:
        if isinstance(c,Cube):
            c.frm=[c.frm[0],c.frm[1]+dy,c.frm[2]]; c.to=[c.to[0],c.to[1]+dy,c.to[2]]
            c.origin=[c.origin[0],c.origin[1]+dy,c.origin[2]]
        else: shift(c,dy)

# ---------------- palette ----------------
BODY=(30,26,34); BODY2=(46,40,50)
CLOTH=(60,50,60); CLOTH_D=(38,31,39)
ASH=(146,140,138); ASH_L=(198,192,186)
MASK=(228,221,208); MASK_D=(170,162,152); MASK_FEAT=(12,10,14)
VOID=(10,8,12)
METAL=(88,80,76); METAL_D=(56,50,48)
WAX=(206,196,176); WAX_D=(150,140,122)
EMBER=(255,148,58); EMBER_D=(186,66,24); CRACK=(255,116,36); CRACK2=(208,54,18); CORE=(255,238,192)
SHINY=dict(BODY=(214,210,204),BODY2=(236,232,226),CLOTH=(236,232,224),CLOTH_D=(196,190,182),
    ASH=(208,172,92),ASH_L=(240,214,142),MASK=(32,34,52),MASK_D=(20,22,36),MASK_FEAT=(170,226,255),
    VOID=(16,20,40),METAL=(222,180,74),METAL_D=(158,116,42),WAX=(70,76,104),WAX_D=(46,50,74),
    EMBER=(122,228,255),EMBER_D=(42,128,222),CRACK=(140,238,255),CRACK2=(58,124,236),CORE=(236,252,255))
def apply_palette(p): globals().update(p)
P_=lambda n: globals()[n]
def cl(v): return max(0,min(255,int(v)))
def rgb(c,f=1.0): return (cl(c[0]*f),cl(c[1]*f),cl(c[2]*f),255)
def mix(a,b,t): return tuple(a[i]+(b[i]-a[i])*t for i in range(3))
CLEAR=(0,0,0,0)
SIDE=('north','south','east','west')

# ---------------- painters ----------------
def ashcloth(cname, fade=0.55, cut=0.3, ember=0.2, fold=True):
    """위는 짙은 천, 아래로 갈수록 재(회백색)로 바래다가 찢겨 사라짐."""
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; w,h=x2-x1,max(1,y2-y1); ph=rng.uniform(0,6)
        base=P_(cname)
        for y in range(y1,y2):
            t=(y-y1)/h if face in SIDE else 0.2
            a=0 if t<fade else ((t-fade)/(1-fade))**1.3
            for x in range(x1,x2):
                f=SHADE[face]*(1+(0.06*math.sin((x-x1)*0.9+ph) if fold else 0))*(1+rng.uniform(-.06,.06))
                c=mix(base,P_('ASH'),min(1,a*1.1))
                if a>0.2 and rng.random()<a*0.25: c=P_('ASH_L')
                img.putpixel((x,y),rgb(c,f))
        if face not in SIDE or cut<=0: return
        mxl=max(1,int(h*cut)); L=rng.randint(0,mxl)
        for x in range(x1,x2):
            L=max(0,min(mxl,L+rng.choice([-2,-1,0,0,1,2])))
            if rng.random()<0.15: L=min(mxl,L+rng.randint(2,5))
            for y in range(y2-L,y2): img.putpixel((x,y),CLEAR)
            ey=y2-L-1
            if ey>=y1 and rng.random()<ember: img.putpixel((x,ey),rgb(P_('EMBER_D')))
    return p

def skin(cname='BODY', vein=0.0):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; ph=rng.uniform(0,6)
        for y in range(y1,y2):
            for x in range(x1,x2):
                f=SHADE[face]*(1+0.07*math.sin((y-y1)*0.7+(x-x1)*0.3+ph))*(1+rng.uniform(-.05,.05))
                img.putpixel((x,y),rgb(P_(cname),f))
                if rng.random()<0.005: img.putpixel((x,y),rgb(P_('ASH'),0.8))
        if vein>0: _cracks(img,rect,rng,vein)
    return p

def _cracks(img,rect,rng,density,glow=True):
    x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
    if w*h<8: return
    n=max(1,int(w*h*density/60)); pts=[]
    def line(x,y,steps,main):
        for s in range(steps):
            if 0<=x<w and 0<=y<h: pts.append((x,y))
            d=main if rng.random()<0.6 else rng.choice([(1,0),(-1,0),(0,1),(1,1),(-1,1)])
            x+=d[0]; y+=d[1]
            if rng.random()<0.08 and steps>6: line(x,y,steps//2,rng.choice([(1,1),(-1,1),(1,0),(-1,0)]))
    for _ in range(n): line(rng.randrange(w),rng.randrange(h),rng.randint(6,14),rng.choice([(0,1),(1,1),(-1,1)]))
    S=set(pts)
    if glow:
        for (x,y) in pts:
            for dx,dy in((1,0),(-1,0),(0,1),(0,-1)):
                q=(x+dx,y+dy)
                if q not in S and 0<=q[0]<w and 0<=q[1]<h and rng.random()<0.5: px(img,rect,q[0],q[1],rgb(P_('EMBER_D'),0.8))
    for (x,y) in pts: px(img,rect,x,y,rgb(P_('CRACK') if rng.random()<0.75 else P_('EMBER')))

def cracked(cname='BODY', d=1.0): return skin(cname, d)

def solidp(cname, var=0.05, shade=True):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                f=(SHADE[face] if shade else 1)*(1+rng.uniform(-var,var))
                img.putpixel((x,y),rgb(P_(cname),f))
    return p
def glow(cname): return solidp(cname,0.04,False)

def metal(cname='METAL'):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                c=P_('METAL_D') if rng.random()<0.12 else P_(cname)
                img.putpixel((x,y),rgb(c,SHADE[face]*(1.15 if y==y1 else 1)*(1+rng.uniform(-.08,.08))))
    return p

def claw():
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; h=max(1,y2-y1)
        for y in range(y1,y2):
            t=(y-y1)/h
            for x in range(x1,x2):
                c=P_('EMBER_D') if (t>0.7 and face in SIDE) else P_('VOID')
                img.putpixel((x,y),rgb(c,SHADE[face]))
    return p

def wax():
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                c=P_('WAX_D') if (face in SIDE and (y-y1)>0 and rng.random()<0.2) else P_('WAX')
                img.putpixel((x,y),rgb(c,SHADE[face]*(1+rng.uniform(-.05,.05))))
    return p

def core():
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
        for y in range(y1,y2):
            for x in range(x1,x2):
                dx=((x-x1)+.5-w/2)/max(1,w/2); dy=((y-y1)+.5-h/2)/max(1,h/2); d=(dx*dx+dy*dy)**.5
                c=P_('CORE') if d<0.35 else P_('EMBER') if d<0.7 else P_('CRACK2')
                img.putpixel((x,y),rgb(c))
    return p

MASK_ART={ # 12x16 앞면 (lx,ly) -> 키
}
def _mask_art():
    a={}
    # 눈구멍: 바깥쪽이 위로 치켜 올라간 날카로운 눈
    for lx,ly in [(1,5),(2,5),(2,6),(3,6),(4,6),(2,7),(3,7),(4,7),(3,8),(4,8)]:
        a[(lx,ly)]='F'; a[(11-lx,ly)]='F'
    a[(3,7)]='E'; a[(8,7)]='E'
    # 눈물 자국 (검게 흘러내림)
    for ly in range(9,15): a[(3,ly)]='T' if ly%3 else 'F'
    for ly in range(9,13): a[(8,ly)]='T'
    # 꿰맨 입
    for lx in range(3,9): a[(lx,12)]='F'
    for lx in (3,5,7): a[(lx,11)]='F'; a[(lx+1,13)]='F'
    # 이마 균열
    for lx,ly in [(6,0),(6,1),(5,2),(5,3),(6,4)]: a[(lx,ly)]='C'
    return a
MASK_ART=_mask_art()

def mask_paint(broken=False):
    def p(img,face,rect,rng):
        x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
        for y in range(y1,y2):
            for x in range(x1,x2):
                edge=(x in(x1,x2-1)) or (y in(y1,y2-1))
                f=SHADE[face]*(0.86 if edge else 1)*(1+rng.uniform(-.04,.04))
                img.putpixel((x,y),rgb(P_('MASK'),f))
        if face=='north':
            for (lx,ly),k in MASK_ART.items():
                if lx<w and ly<h:
                    c={'F':P_('MASK_FEAT'),'E':P_('EMBER'),'T':P_('MASK_D'),'C':P_('MASK_D')}[k]
                    if k=='T': c=mix(P_('MASK_FEAT'),P_('MASK'),0.25)
                    px(img,rect,lx,ly,rgb(c))
            if broken:   # 깨진 가장자리(왼쪽 끝 = 큐브 -x 쪽 = 텍스처 오른쪽)
                for ly in range(h):
                    cut=rng.choice([0,0,1,1,2])
                    for k in range(cut): px(img,rect,w-1-k,ly,CLEAR)
                    px(img,rect,w-1-cut,ly,rgb(P_('MASK_D'),0.8))
    return p

def chain_link():
    base=metal()
    def p(img,face,rect,rng):
        base(img,face,rect,rng)
        x1,y1,x2,y2=rect
        if x2-x1>=3 and y2-y1>=4 and face in SIDE:
            for y in range(y1+1,y2-1):
                for x in range(x1+1,x2-1): img.putpixel((x,y),CLEAR)
    return p

# ---------------- parts ----------------
def claw_hand(pre, m, cx, top, cz, palm, fz, seg, fw, mat, curl=(8,12,18), thumb=True, grip=0):
    pw,ph,pd=palm
    kids=[C(f'{pre}_palm',[cx-pw/2,top-ph,cz-pd/2],[cx+pw/2,top,cz+pd/2],mat,m)]
    y0=top-ph; L1,L2,L3=seg; w2=max(1,fw-1)
    for i,z in enumerate(fz):
        zc=cz+z
        tip=G_(f'{pre}_f{i}c',[cx,y0-L1-L2,zc],[box(f'{pre}_f{i}_claw',[cx,y0-L1-L2-L3/2,zc],[w2,L3,w2],claw(),m)],[curl[2]+grip,0,0],m)
        mid=G_(f'{pre}_f{i}b',[cx,y0-L1,zc],[box(f'{pre}_f{i}_mid',[cx,y0-L1-L2/2,zc],[w2,L2,w2],mat,m),tip],[curl[1]+grip,0,0],m)
        kids.append(G_(f'{pre}_f{i}',[cx,y0,zc],[box(f'{pre}_f{i}_base',[cx,y0-L1/2,zc],[fw,L1,fw],mat,m),mid],
                       [curl[0]+grip,0,(i-(len(fz)-1)/2)*4],m))
    if thumb:
        tx=cx-pw/2; tz=cz-pd/2
        tt=G_(f'{pre}_thumb_t',[tx-1,top-ph-L1+2,tz-1],[box(f'{pre}_thumb_claw',[tx-1,top-ph-L1+2-L3/2,tz-1],[w2,L3,w2],claw(),m)],[25+grip,0,0],m)
        kids.append(G_(f'{pre}_thumb',[tx,top-2,tz],[box(f'{pre}_thumb',[tx-1,top-2-(ph+L1-4)/2,tz-1],[fw,ph+L1-4,fw],mat,m),tt],[18,0,22],m))
    return kids

def chain(name, x, y, z, n, m=False, step=3):
    out=[]
    for i in range(n):
        yy=y-i*step
        if i%2==0: out.append(C(f'{name}{i}',[x-1.5,yy-4,z-0.5],[x+1.5,yy,z+0.5],chain_link(),m))
        else:      out.append(C(f'{name}{i}',[x-0.5,yy-4,z-1.5],[x+0.5,yy,z+1.5],chain_link(),m))
    return out

def halo(cy, cz, r, lit, n=14, candles=(30,60,90,120,150), extra_spikes=False):
    kids=[]; seg=math.ceil(2*math.pi*r/n)+1
    for i in range(n):
        a=2*math.pi*i/n; c=[r5(r*math.cos(a)),r5(cy+r*math.sin(a)),cz]
        kids.append(box(f'halo_seg{i}',c,[seg,2,2],metal(),rot=[0,0,math.degrees(a)+90],origin=c))
    for j,deg in enumerate(candles):
        a=math.radians(deg); rr=r+4
        c=[r5(rr*math.cos(a)),r5(cy+rr*math.sin(a)),cz]
        rot=[0,0,deg-90]
        parts=[box(f'candle{j}',c,[2,6,2],wax(),rot=rot,origin=c)]
        tipc=[r5((rr+3.5)*math.cos(a)),r5(cy+(rr+3.5)*math.sin(a)),cz]
        parts.append(box(f'wick{j}',tipc,[1,1,1],glow('VOID'),rot=rot,origin=tipc))
        if lit:
            fc=[r5((rr+5)*math.cos(a)),r5(cy+(rr+5)*math.sin(a)),cz]
            parts.append(box(f'flame{j}',fc,[1,2,1],glow('EMBER'),rot=rot,origin=fc))
        dc=[r5((rr-2.5)*math.cos(a)),r5(cy+(rr-2.5)*math.sin(a)),cz]
        parts.append(box(f'drip{j}',dc,[3,1,3],wax(),rot=rot,origin=dc))
        kids+=parts
    if extra_spikes:
        for j,deg in enumerate((0,180,210,240,270,300,330)):
            a=math.radians(deg); rr=r+3
            c=[r5(rr*math.cos(a)),r5(cy+rr*math.sin(a)),cz]
            kids.append(box(f'halo_spike{j}',c,[1,5,1],claw(),rot=[0,0,deg-90],origin=c))
    return Group('halo',[0,cy,cz],kids,[-12,0,0])

# =====================================================================
def build(enr=False):
    k=1.25 if enr else 1.0
    body_p=cracked('BODY',1.1) if enr else skin('BODY')
    body2_p=cracked('BODY2',0.9) if enr else skin('BODY2')

    # ---------- 하체 ----------
    strips_f=[]; strips_b=[]
    xs=[(-9,30),(-6,36),(-3,28),(0,38),(3,30),(6,34)]
    for i,(x,L) in enumerate(xs):
        strips_f.append(C(f'strip_f{i}',[x,44-L,-8],[x+3,44,-7],ashcloth('CLOTH_D',0.4,0.35),rot=[-6-(i%2)*4,0,0],origin=[x+1.5,44,-7.5]))
        strips_b.append(C(f'strip_b{i}',[x,44-L-4,7],[x+3,44,8],ashcloth('CLOTH_D',0.4,0.35),rot=[8+(i%2)*4,0,0],origin=[x+1.5,44,7.5]))
    for i,(z,L) in enumerate([(-5,32),(-1,38),(3,30)]):
        for m in(False,True):
            (strips_f if i==0 else strips_b).append(C(f'strip_s{i}{"l" if m else "r"}',[9,44-L,z],[10,44,z+3],ashcloth('CLOTH_D',0.4,0.35),m,rot=[0,0,-8],origin=[9.5,44,z+1.5]))
    skirt_kids=[Group('strips_f',[0,44,-7.5],strips_f), Group('strips_b',[0,44,7.5],strips_b),
                C('skirt_top',[-9,34,-7],[9,46,7],ashcloth('CLOTH',0.7,0.0))]
    if True:
        tail=Group('tail',[0,22,1],[
            C('tail1',[-5,10,-4],[5,22,5],ashcloth('CLOTH',0.45,0.25)),
            Group('tail2',[0,10,1],[C('tail2',[-3,0,-2],[3,10,4],ashcloth('CLOTH_D',0.3,0.45))],[-14,0,0])],[-10,0,0])
        skirt_kids+=[C('skirt_mid',[-8,22,-6],[8,34,6],ashcloth('CLOTH',0.55,0.2)), tail]
    skirt=Group('skirt',[0,46,0],skirt_kids)

    # ---------- 몸통 ----------
    cage=[C('cage_top',[-5,68,-9],[5,70,-7],metal()),C('cage_bot',[-5,56,-9],[5,58,-7],metal()),
          C('cage_light',[-4,58,-8],[4,68,-8],core())]
    bars=[(-5,-4),(-2,-1),(1,2),(4,5)]
    for i,(a,b) in enumerate(bars):
        if enr and i in(1,2):
            cage.append(C(f'cage_bar{i}',[a,58,-10],[b,68,-9],metal(),rot=[-25,0,(12 if i==1 else -12)],origin=[(a+b)/2,63,-9.5]))
        else:
            cage.append(C(f'cage_bar{i}',[a,58,-10],[b,68,-9],metal()))
    heart=Group('heart',[0,63,-8],[C('heart',[-2,61,-9],[2,65,-7],glow('CORE')),C('heart_glow',[-3,60,-8.5],[3,66,-8.5],glow('EMBER'))])
    torso=[
        C('waist',[-6,46,-5],[6,56,5],body_p),
        C('chest',[-10,56,-7],[10,74,7],body_p),
        C('ribs_r',[5,58,-8],[10,72,-7],body2_p),
        C('ribs_l',[-10,58,-8],[-5,72,-7],body2_p),
        *cage, heart,
        C('shroud_back',[-14,58,4],[14,80,9],ashcloth('CLOTH',0.6,0.3)),
        C('shroud_r',[10,56,-9],[14,80,4],ashcloth('CLOTH',0.6,0.3)),
        C('shroud_l',[-14,56,-9],[-10,80,4],ashcloth('CLOTH',0.6,0.3)),
        C('collar',[-10,74,-10],[10,80,-7],ashcloth('CLOTH_D',0.7,0.2)),
        C('lapel_r',[5,44,-10],[10,74,-8],ashcloth('CLOTH_D',0.5,0.3),rot=[0,0,-4],origin=[7.5,74,-9]),
        C('lapel_l',[-10,44,-10],[-5,74,-8],ashcloth('CLOTH_D',0.5,0.3),rot=[0,0,4],origin=[-7.5,74,-9]),
        C('hump',[-8,74,-2],[8,82,8],ashcloth('CLOTH',0.8,0.0)),
    ]

    # ---------- 머리 ----------
    hair=[]
    for i,x in enumerate([-5,-3,-1,1,3,5]):
        L1=18-abs(x); L2=14+(i%3)*3
        if enr: L2+=4
        low=Group(f'hair{i}b',[x,96-L1,4],[box(f'hair{i}b',[x,96-L1-L2/2,4],[1,L2,1],ashcloth('BODY2',0.4,0.3,0.1,False))],[6,0,0])
        hair.append(Group(f'hair{i}',[x,96,4],[box(f'hair{i}',[x,96-L1/2,4],[2,L1,2],skin('BODY2')),low],[18,0,(x)*2]))
    head_kids=[
        C('neck',[-3,72,-6],[3,82,2],body_p),
        C('skull',[-5,82,-6],[5,96,4],body_p),
        *hair,
    ]
    if not enr:
        head_kids+=[
            C('mask',[-6,80,-12],[6,96,-4],mask_paint()),
            C('mask_top',[-5,96,-11],[5,98,-5],mask_paint()),
            C('mask_chin',[-4,77,-11],[4,80,-5],mask_paint()),
            C('mask_cheek_r',[6,82,-11],[7,93,-6],mask_paint()),
            C('mask_cheek_l',[-7,82,-11],[-6,93,-6],mask_paint()),
        ]
    else:
        jaw=Group('jaw',[-2,82,-6],[
            C('jaw',[-6,76,-11],[0,82,-6],cracked('BODY',1.4)),
            C('teeth_low',[-5,81,-11.5],[0,82,-10.5],solidp('MASK')),
            C('maw',[-5,80,-10],[0,85,-10],glow('CRACK')),
        ],[-8,0,0])
        head_kids+=[
            C('mask',[0,80,-12],[6,96,-4],mask_paint(broken=True)),
            C('mask_top',[0,96,-11],[5,98,-5],mask_paint()),
            C('mask_cheek_r',[6,82,-11],[7,93,-6],mask_paint()),
            C('face_burn',[-6,82,-10],[0,96,-4],cracked('BODY',2.4)),
            C('brow_burn',[-6,92,-11],[0,94,-9],cracked('BODY2',1.0),rot=[0,0,12],origin=[0,93,-10]),
            C('socket_l',[-5,87,-10.2],[-1,91,-10.2],glow('VOID')),
            C('eye_l',[-4,88,-10.4],[-2,90,-10.4],glow('EMBER')),
            C('pupil_l',[-3.5,88,-10.6],[-2.5,90,-10.6],glow('CORE')),
            C('teeth_up',[-5,82,-11.5],[0,84,-10.5],solidp('MASK')),
            C('shard',[-9,92,-12],[-7,96,-11],mask_paint(),rot=[0,0,30],origin=[-8,94,-11.5]),
            jaw,
        ]
    head_kids.append(halo(95,8,13 if not enr else 15, enr, candles=(30,60,90,120,150) if not enr else (20,45,70,90,110,135,160), extra_spikes=enr))
    head=Group('head',[0,78,-3],head_kids,[12,0,0])

    # ---------- 앞팔 (가는 팔, 종 모양 소매) ----------
    def front_arm(side):
        m=side=='left'
        hk=claw_hand(side,m,13,34,0,(5,6,6),[-2,0,2],(6,6,6),2,skin('BODY2'),curl=(10,14,20))
        if m:
            hk.append(C('shackle',[10,30,-3.5],[16,33,3.5],metal(),True))
            hk.append(G_('chain',[13,30,0],chain('chain',13,30,0,6,True),None,True))
        hand=G_(f'{side}_hand',[13,34,0],hk,[6,0,0],m)
        fore=G_(f'{side}_forearm',[13,54,0],[
            C(f'{side}_forearm',[11.5,34,-1.5],[14.5,55,1.5],skin('BODY2',0.6 if enr else 0),m),
            C(f'{side}_sleeve',[9,38,-4],[17,56,4],ashcloth('CLOTH',0.35,0.45),m),
            C(f'{side}_cuff',[10,52,-3],[16,56,3],ashcloth('CLOTH_D',0.9,0.0),m),
            hand],[26,0,0],m)
        return G_(f'{side}_arm',[12,74,0],[
            C(f'{side}_shoulder',[9,70,-4],[16,78,4],ashcloth('CLOTH',0.7,0.1),m),
            C(f'{side}_upper',[11,54,-2],[15,74,2],skin('BODY2',0.6 if enr else 0),m),
            C(f'{side}_elbow',[11.5,52,-1],[14.5,56,3],skin('BODY'),m),
            fore],[6,0,12],m)

    # ---------- 등에서 솟은 거대한 손 ----------
    def back_arm(side):
        m=side=='left'
        w=6 if enr else 5; U=round(24*k); F=round(22*k)
        sx,sy,sz=7,74,9
        palm=(8,9,8) if enr else (7,8,7)
        fz=[-3,-1,1,3] if enr else [-2.5,-0.5,1.5]
        hk=claw_hand(f'{side}_back',m,sx,sy+U-F,sz,palm,fz,(round(9*k),round(8*k),round(8*k)),3 if enr else 2,
                     cracked('BODY',1.2) if enr else skin('BODY'),curl=(14,18,24))
        hand=G_(f'{side}_back_hand',[sx,sy+U-F,sz],hk,[8,0,0],m)
        spikes=[]
        if enr:
            for i,yy in enumerate([sy+6,sy+12,sy+18]):
                spikes.append(C(f'{side}_back_spike{i}',[sx+w/2,yy,sz-0.5],[sx+w/2+4,yy+2,sz+0.5],claw(),m,rot=[0,0,30],origin=[sx+w/2,yy+1,sz]))
        fore=G_(f'{side}_back_fore',[sx,sy+U,sz],[
            box(f'{side}_back_fore',[sx,sy+U-F/2,sz],[w-1,F,w-1],cracked('BODY',1.0) if enr else skin('BODY')  ,m),
            box(f'{side}_back_wrist',[sx,sy+U-F+2,sz],[w,3,w],ashcloth('CLOTH_D',0.8,0.0),m),
            hand],[12,0,78],m)
        return G_(f'{side}_back',[sx,sy,sz],[
            box(f'{side}_back_root',[sx,sy+2,sz],[w+2,6,w+2],ashcloth('CLOTH_D',0.6,0.2),m),
            box(f'{side}_back_upper',[sx,sy+U/2,sz],[w,U,w],cracked('BODY',1.0) if enr else skin('BODY'),m),
            box(f'{side}_back_elbow',[sx,sy+U,sz+1],[w+1,4,w+1],skin('BODY2'),m),
            *spikes, fore],[12,0,-55],m)

    body=Group('body',[0,46,0],[*torso,skirt,head,front_arm('right'),front_arm('left'),back_arm('right'),back_arm('left')])
    kids=[body]
    if False:
        def leg(side):
            m=side=='left'
            toes=[]
            for i,x in enumerate([3,5,7]):
                toes.append(C(f'{side}_toe{i}',[x-1,0,-9],[x+1,2,-5],cracked('BODY',0.5),m))
                toes.append(C(f'{side}_toeclaw{i}',[x-0.5,0,-12],[x+0.5,2,-9],claw(),m))
            foot=G_(f'{side}_foot',[5,6,1],[
                C(f'{side}_ankle',[3,3,-2],[7,8,3],cracked('BODY',0.8),m),
                C(f'{side}_foot',[2,0,-5],[8,3,3],cracked('BODY',0.8),m),
                C(f'{side}_heel',[4,0,3],[6,3,6],claw(),m), *toes],None,m)
            shin=G_(f'{side}_shin',[5,26,-1],[
                C(f'{side}_knee',[2.5,23,-4],[7.5,29,1],skin('BODY2',0.5),m),
                C(f'{side}_shin',[3,6,-1],[7,25,3],cracked('BODY',1.2),m),
                C(f'{side}_shin_rag',[2,14,-2],[8,22,4],ashcloth('CLOTH_D',0.4,0.4),m),
                foot],[-10,0,0],m)
            return G_(f'{side}_leg',[5,44,0],[
                C(f'{side}_thigh',[2,26,-3],[8,44,3],cracked('BODY',1.2),m),
                C(f'{side}_hip_rag',[1,32,-4],[9,44,4],ashcloth('CLOTH',0.4,0.45),m),
                shin],[8,0,0],m)
        kids+=[leg('right'),leg('left')]
    root=Group('root',[0,0,0],kids)
    shift(root,12)
    return root

HB_BASE=(2.6,7.2,6.1)
HB_ENR=(3.0,7.6,6.2)

# =====================================================================
# 애니메이션
# =====================================================================
cm='catmullrom'; LIN='linear'
def R(*pts,i=cm): return [('rotation',p[0],p[1],p[2],p[3],i if len(p)<5 else p[4]) for p in pts]
def Pp(*pts,i=cm): return [('position',p[0],p[1],p[2],p[3],i if len(p)<5 else p[4]) for p in pts]
def S(*pts,i=cm): return [('scale',p[0],p[1],p[1],p[1],i if len(p)<3 else p[2]) if len(p)==2 else ('scale',p[0],p[1],p[2],p[3],cm) for p in pts]
def cyc(a,L,ph=0,ch='rotation',axis=0,base=0):
    out=[]
    for j in range(5):
        v=[0,0,0]; v[axis]=base+a*math.cos(ph+j*math.pi/2); out.append((ch,L*j/4,*v,cm))
    return out
def fingers(pre,n,keys): return {f'{pre}_f{i}':keys for i in range(n)}
def merge(*ds):
    out={}
    for d in ds:
        for k,v in d.items(): out.setdefault(k,[]).extend(v)
    return out

def build_anims(enr, groups):
    gset={g.name for g in groups}
    n='animation.ash_shadow_enraged.' if enr else 'animation.ash_shadow.'
    NF=4 if enr else 3
    sp=0.75 if enr else 1.0     # 광폭화는 25% 빠름
    T=lambda t: round(t*sp,3)
    A=[]
    def add(name,L,loop,tracks):
        for arm in ('right_back','left_back'):
            if arm in tracks:
                tracks[arm]=[(x[0],x[1],x[2],x[3],(x[4]*0.35 if x[0]=='rotation' else x[4]),x[5]) for x in tracks[arm]]
        for arm in ('right_arm','left_arm'):
            if arm in tracks:
                tracks[arm]=[(x[0],x[1],x[2],x[3],(-x[4] if (x[0]=='rotation' and x[2]<100) else x[4]),x[5]) for x in tracks[arm]]
        tr={}
        for k,v in tracks.items():
            if k not in gset: continue
            v=sorted(v,key=lambda x:(x[0],x[1]))
            seen=set(); vv=[]
            for x in v:
                key=(x[0],round(x[1],3))
                if key in seen: continue
                seen.add(key); vv.append(x)
            tr[k]=vv
        A.append(anim(n+name,round(L,3),loop,tr,groups))
    BF=lambda side,keys: fingers(f'{side}_back',NF,keys)
    FF=lambda side,keys: fingers(side,3,keys)
    legs_idle={}

    # ---------- 공통 대기 ----------
    L=3.2 if not enr else 2.4
    add('idle',L,'loop',merge({
        'body':cyc(2 if not enr else 1.2,L,0,'position',1),
        'heart':[('scale',0,1,1,1,cm),('scale',L*0.08,1.25,1.25,1.25,cm),('scale',L*0.16,1,1,1,cm),('scale',L*0.24,1.2,1.2,1.2,cm),('scale',L*0.34,1,1,1,cm),('scale',L,1,1,1,cm)],
        'head':R((0,0,0,4),(L*0.45,-3,4,-3),(L*0.5,-3,4,12,LIN),(L*0.55,-3,4,-3,LIN),(L,0,0,4)),
        'halo':cyc(6,L,0,'rotation',2),
        'right_back':cyc(4,L,0.4),'left_back':cyc(4,L,2.0),
        'right_back_fore':cyc(5,L,1.2),'left_back_fore':cyc(5,L,2.8),
        'right_arm':cyc(3,L,0.2),'left_arm':cyc(3,L,1.8),
        'skirt':cyc(3,L,0.6),'tail':cyc(6,L,1.4),'strips_f':cyc(4,L,1.0),'strips_b':cyc(5,L,2.0),
        **{f'hair{i}':cyc(4,L,i*0.9,'rotation',2) for i in range(6)},
        'jaw':cyc(4,L,0,'rotation',0,-2)},
        BF('right',cyc(8,L,0.5)),BF('left',cyc(8,L,2.1)),FF('right',cyc(6,L,0)),FF('left',cyc(6,L,1.5))))

    # ---------- 이동 ----------
    if True:
        L=2.0 if not enr else 1.5
        add('walk',L,'loop',merge({
            'body':R((0,-14,0,0),(L,-14,0,0))+cyc(2,L,0,'position',1),
            'head':R((0,14,0,0),(L/2,10,0,0),(L,14,0,0)),
            'skirt':R((0,-10,0,0),(L/2,-16,0,0),(L,-10,0,0)),
            'tail':R((0,-24,0,0),(L/2,-14,0,0),(L,-24,0,0)),
            'strips_f':R((0,-25,0,0),(L/2,-18,0,0),(L,-25,0,0)),'strips_b':R((0,-30,0,0),(L/2,-20,0,0),(L,-30,0,0)),
            'right_arm':R((0,-28,0,0),(L/2,-16,0,0),(L,-28,0,0)),'left_arm':R((0,-16,0,0),(L/2,-28,0,0),(L,-16,0,0)),
            'right_back':R((0,10,0,0),(L/2,16,0,0),(L,10,0,0)),'left_back':R((0,16,0,0),(L/2,10,0,0),(L,16,0,0)),
            **{f'hair{i}':R((0,-15,0,0),(L/2,-25,0,0),(L,-15,0,0)) for i in range(6)}},
            BF('right',cyc(6,L,0)),BF('left',cyc(6,L,1.6))))
    else:
        L=1.3; q=L/4
        sw=lambda a:R((0,a,0,0),(L/2,-a,0,0),(L,a,0,0))
        lift=lambda t:R((0,0,0,0),(max(0.01,t-q),0,0,0),(t,-40,0,0),(min(L,t+q),0,0,0))+([('rotation',L,0,0,0,cm)] if t+q<L-1e-6 else [])
        add('walk',L,'loop',merge({
            'right_leg':sw(28),'left_leg':sw(-28),'right_shin':lift(3*q),'left_shin':lift(q),
            'right_arm':sw(-14),'left_arm':sw(14),
            'body':Pp((0,0,0,0),(q,0,-2,0),(2*q,0,0,0),(3*q,0,-2,0),(L,0,0,0))+R((0,-12,0,0),(L,-12,0,0)),
            'head':R((0,8,0,0),(q,11,0,0),(2*q,8,0,0),(3*q,11,0,0),(L,8,0,0)),
            'right_back':R((0,0,0,0),(L/2,10,0,0),(L,0,0,0)),'left_back':R((0,10,0,0),(L/2,0,0,0),(L,10,0,0)),
            'skirt':R((0,-8,0,0),(L/2,-3,0,0),(L,-8,0,0)),'strips_b':R((0,-18,0,0),(L/2,-10,0,0),(L,-18,0,0)),
            **{f'hair{i}':R((0,-12,0,0),(L/2,-20,0,0),(L,-12,0,0)) for i in range(6)}}))

    # ---------- 기본 공격: 앞팔 할퀴기 ----------
    L=T(1.2)
    add('attack',L,'once',merge({
        'right_arm':R((0,0,0,0),(T(.35),40,0,-70),(T(.5),45,0,25,LIN),(T(.7),40,0,25),(L,0,0,0)),
        'right_forearm':R((0,0,0,0),(T(.35),-30,0,0),(T(.5),10,0,0,LIN),(L,0,0,0)),
        'body':R((0,0,0,0),(T(.35),4,22,0),(T(.5),-12,-24,0,LIN),(T(.7),-12,-24,0),(L,0,0,0)),
        'head':R((0,0,0,0),(T(.35),-6,-10,0),(T(.5),6,14,0,LIN),(L,0,0,0)),
        'right_back':R((0,0,0,0),(T(.35),15,0,-10),(T(.5),-10,0,0),(L,0,0,0))},
        FF('right',R((0,0,0,0),(T(.35),-30,0,0),(T(.5),35,0,0,LIN),(L,0,0,0)))))

    # ---------- 패턴 1: 잿빛 돌진 ----------
    L=T(1.4)
    add('skill_dash',L,'once',merge({
        'body':R((0,0,0,0),(T(.5),18,0,0),(T(.62),-40,0,0,LIN),(T(.9),-40,0,0),(L,0,0,0))+Pp((0,0,0,0),(T(.5),0,-3,6),(T(.62),0,-4,-18,LIN),(T(.9),0,-4,-18),(L,0,0,0)),
        'head':R((0,0,0,0),(T(.5),-12,0,0),(T(.62),30,0,0,LIN),(T(.9),30,0,0),(L,0,0,0)),
        'right_back':R((0,0,0,0),(T(.5),45,0,-20),(T(.62),-70,0,-5,LIN),(T(.9),-70,0,-5),(L,0,0,0)),
        'left_back':R((0,0,0,0),(T(.5),45,0,20),(T(.62),-70,0,5,LIN),(T(.9),-70,0,5),(L,0,0,0)),
        'right_arm':R((0,0,0,0),(T(.5),-40,0,-10),(T(.62),-60,0,0),(L,0,0,0)),
        'left_arm':R((0,0,0,0),(T(.5),-40,0,10),(T(.62),-60,0,0),(L,0,0,0)),
        'skirt':R((0,0,0,0),(T(.62),-30,0,0),(L,0,0,0)),'tail':R((0,0,0,0),(T(.62),-40,0,0),(L,0,0,0)),
        'strips_f':R((0,0,0,0),(T(.62),-45,0,0),(L,0,0,0)),'strips_b':R((0,0,0,0),(T(.62),-45,0,0),(L,0,0,0)),
        'right_leg':R((0,0,0,0),(T(.5),35,0,0),(T(.62),-30,0,0,LIN),(T(.9),-30,0,0),(L,0,0,0)),
        'left_leg':R((0,0,0,0),(T(.5),-10,0,0),(T(.62),40,0,0,LIN),(T(.9),40,0,0),(L,0,0,0)),
        'right_shin':R((0,0,0,0),(T(.5),-45,0,0),(T(.62),-5,0,0),(L,0,0,0))},
        BF('right',R((0,0,0,0),(T(.5),-30,0,0),(T(.62),30,0,0),(L,0,0,0))),BF('left',R((0,0,0,0),(T(.5),-30,0,0),(T(.62),30,0,0),(L,0,0,0)))))

    # ---------- 패턴 2: 그림자 손 내려찍기 (3연타 / 광폭화 4연타) ----------
    hits=[0.55,1.15,1.75]+([2.35] if enr else [])
    L=T(hits[-1]+0.75)
    tr={'right_back':[('rotation',0,0,0,0,cm)],'left_back':[('rotation',0,0,0,0,cm)],
        'right_back_fore':[('rotation',0,0,0,0,cm)],'left_back_fore':[('rotation',0,0,0,0,cm)],
        'body':[('rotation',0,0,0,0,cm),('position',0,0,0,0,cm)],'head':[('rotation',0,0,0,0,cm)]}
    for j,h in enumerate(hits):
        side='right' if j%2==0 else 'left'; sgn=1 if side=='right' else -1
        a,hh,b=T(h-0.4),T(h),T(h+0.3)
        tr[f'{side}_back']+= [('rotation',a,40,0,-10*sgn,cm),('rotation',hh,-95,0,10*sgn,LIN),('rotation',b,-90,0,10*sgn,cm),('rotation',T(h+0.55),0,0,0,cm)]
        tr[f'{side}_back_fore']+=[('rotation',a,40,0,0,cm),('rotation',hh,-40,0,0,LIN),('rotation',T(h+0.55),0,0,0,cm)]
        tr['body']+=[('rotation',a,8,8*sgn,0,cm),('rotation',hh,-16,-6*sgn,0,LIN),('position',a,0,2,0,cm),('position',hh,0,-4,0,LIN),('position',T(h+0.3),0,-3,0,cm)]
        tr['head']+=[('rotation',a,-8,0,0,cm),('rotation',hh,10,0,0,LIN)]
    for k in tr: tr[k].append((tr[k][0][0],L,0,0,0,cm))
    tr['body'].append(('position',L,0,0,0,cm))
    add('skill_slam',L,'once',merge(tr,BF('right',R((0,0,0,0),(T(.3),-35,0,0),(T(.55),40,0,0,LIN),(L,0,0,0))),
                                       BF('left',R((0,0,0,0),(T(.9),-35,0,0),(T(1.15),40,0,0,LIN),(L,0,0,0)))))

    # ---------- 패턴 3: 추적하는 재 (손가락 튕기기 4번) ----------
    L=T(2.2)
    flick=lambda side,t0:[(t0,0,0,0),(t0+T(.12),-35,0,0),(t0+T(.2),30,0,0)]
    rf=[];lf=[]
    for j,t0 in enumerate([T(.6),T(.9),T(1.2),T(1.5)]):
        (rf if j%2==0 else lf).extend(flick('',t0))
    add('skill_ash_rain',L,'once',merge({
        'right_arm':R((0,0,0,0),(T(.45),150,0,-25),(T(1.7),150,0,-25),(L,0,0,0)),
        'left_arm':R((0,0,0,0),(T(.45),150,0,25),(T(1.7),150,0,25),(L,0,0,0)),
        'right_forearm':R((0,0,0,0),(T(.45),-20,0,0),*[(t,-20+x,0,0) for t,x,_,_ in rf],(T(1.7),-20,0,0),(L,0,0,0)),
        'left_forearm':R((0,0,0,0),(T(.45),-20,0,0),*[(t,-20+x,0,0) for t,x,_,_ in lf],(T(1.7),-20,0,0),(L,0,0,0)),
        'head':R((0,0,0,0),(T(.45),25,0,0),(T(1.7),25,0,0),(L,0,0,0)),
        'halo':R((0,0,0,0),(T(1.7),0,0,180),(L,0,0,180)),
        'body':R((0,0,0,0),(T(.45),10,0,0),(T(1.7),10,0,0),(L,0,0,0)),
        'right_back':R((0,0,0,0),(T(.45),20,0,-25),(T(1.7),20,0,-25),(L,0,0,0)),
        'left_back':R((0,0,0,0),(T(.45),20,0,25),(T(1.7),20,0,25),(L,0,0,0))},
        FF('right',R((0,0,0,0),(T(.45),-40,0,0),(T(1.7),-40,0,0),(L,0,0,0))),FF('left',R((0,0,0,0),(T(.45),-40,0,0),(T(1.7),-40,0,0),(L,0,0,0)))))

    # ---------- 패턴 4: 그림자 잠행 / 솟구침 ----------
    L=T(1.0)
    add('skill_sink',L,'hold',{
        'root':Pp((0,0,0,0),(T(.25),0,4,0),(L,0,-110,0,LIN))+[('scale',0,1,1,1,cm),('scale',T(.25),1.05,0.95,1.05,cm),('scale',L,1.3,0.2,1.3,cm)],
        'right_back':R((0,0,0,0),(T(.25),-20,0,-40),(L,40,0,-70)),'left_back':R((0,0,0,0),(T(.25),-20,0,40),(L,40,0,70)),
        'head':R((0,0,0,0),(T(.25),20,0,0),(L,-30,0,0)),
        'right_arm':R((0,0,0,0),(L,-20,0,-60)),'left_arm':R((0,0,0,0),(L,-20,0,60))})
    L=T(1.1)
    add('skill_emerge',L,'once',{
        'root':Pp((0,0,-110,0),(T(.3),0,14,0,LIN),(T(.5),0,4,0),(L,0,0,0))+[('scale',0,1.2,0.3,1.2,cm),('scale',T(.3),0.9,1.2,0.9,cm),('scale',T(.5),1.05,1,1.05,cm),('scale',L,1,1,1,cm)],
        'right_back':R((0,0,0,0),(T(.3),-30,0,-75,LIN),(T(.6),-25,0,-70),(L,0,0,0)),
        'left_back':R((0,0,0,0),(T(.3),-30,0,75,LIN),(T(.6),-25,0,70),(L,0,0,0)),
        'right_arm':R((0,0,0,0),(T(.3),20,0,-80),(L,0,0,0)),'left_arm':R((0,0,0,0),(T(.3),20,0,80),(L,0,0,0)),
        'head':R((0,-20,0,0),(T(.3),30,0,0),(L,0,0,0)),
        'body':R((0,0,0,0),(T(.3),14,0,0),(L,0,0,0))})

    # ---------- 패턴 5: 그림자 손아귀 ----------
    L=T(1.7)
    add('skill_grasp',L,'once',merge({
        'right_back':R((0,0,0,0),(T(.35),35,0,-15),(T(.55),-105,0,8,LIN),(T(.95),-105,0,8),(T(1.25),30,0,-10),(L,0,0,0)),
        'right_back_fore':R((0,0,0,0),(T(.35),20,0,0),(T(.55),-60,0,0,LIN),(T(.95),-60,0,0),(T(1.25),20,0,0),(L,0,0,0)),
        'right_back_hand':R((0,0,0,0),(T(.55),-30,0,0),(T(.95),-30,0,0),(L,0,0,0)),
        'body':R((0,0,0,0),(T(.35),6,12,0),(T(.55),-14,-10,0,LIN),(T(1.25),8,6,0),(L,0,0,0)),
        'head':R((0,0,0,0),(T(.55),12,-8,0),(T(1.25),-6,0,0),(L,0,0,0)),
        'left_arm':R((0,0,0,0),(T(.55),30,0,20),(L,0,0,0))},
        BF('right',R((0,0,0,0),(T(.55),-45,0,0,LIN),(T(.8),-45,0,0),(T(.9),70,0,0,LIN),(T(1.25),70,0,0),(L,0,0,0)))))

    # ---------- 패턴 6: 잔상 (경련) ----------
    L=T(0.7)
    add('skill_afterimage',L,'once',{
        'head':R((0,0,0,0),(T(.08),0,25,18,LIN),(T(.16),0,-20,-14,LIN),(T(.24),0,10,8,LIN),(T(.4),0,0,0)),
        'body':R((0,0,0,0),(T(.1),0,-14,0,LIN),(T(.2),0,10,0,LIN),(T(.4),0,0,0))+Pp((0,0,0,0),(T(.1),0,0,3,LIN),(T(.2),0,0,-3,LIN),(L,0,0,0)),
        'right_back':R((0,0,0,0),(T(.12),-25,0,-15,LIN),(T(.4),0,0,0)),'left_back':R((0,0,0,0),(T(.18),-25,0,15,LIN),(T(.45),0,0,0)),
        'halo':R((0,0,0,0),(T(.1),0,0,40,LIN),(L,0,0,0))})

    # ---------- 패턴 7: 재의 장막 ----------
    L=T(1.9)
    add('skill_veil',L,'once',merge({
        'right_back':R((0,0,0,0),(T(.45),-55,0,55),(T(1.15),-55,0,55),(T(1.3),15,0,-65,LIN),(T(1.55),10,0,-60),(L,0,0,0)),
        'left_back':R((0,0,0,0),(T(.45),-55,0,-55),(T(1.15),-55,0,-55),(T(1.3),15,0,65,LIN),(T(1.55),10,0,60),(L,0,0,0)),
        'right_back_fore':R((0,0,0,0),(T(.45),-30,0,0),(T(1.15),-30,0,0),(T(1.3),10,0,0),(L,0,0,0)),
        'left_back_fore':R((0,0,0,0),(T(.45),-30,0,0),(T(1.15),-30,0,0),(T(1.3),10,0,0),(L,0,0,0)),
        'head':R((0,0,0,0),(T(.45),-30,0,0),(T(1.15),-30,0,0),(T(1.3),25,0,0,LIN),(L,0,0,0)),
        'body':R((0,0,0,0),(T(.45),-15,0,0),(T(1.15),-15,0,0),(T(1.3),15,0,0,LIN),(L,0,0,0)),
        'right_arm':R((0,0,0,0),(T(.45),60,0,25),(T(1.15),60,0,25),(T(1.3),0,0,-70,LIN),(L,0,0,0)),
        'left_arm':R((0,0,0,0),(T(.45),60,0,-25),(T(1.15),60,0,-25),(T(1.3),0,0,70,LIN),(L,0,0,0)),
        'halo':[('scale',0,1,1,1,cm),('scale',T(1.15),0.8,0.8,0.8,cm),('scale',T(1.3),1.25,1.25,1.25,LIN),('scale',L,1,1,1,cm)]},
        BF('right',R((0,0,0,0),(T(.45),50,0,0),(T(1.15),50,0,0),(T(1.3),-40,0,0,LIN),(L,0,0,0))),
        BF('left',R((0,0,0,0),(T(.45),50,0,0),(T(1.15),50,0,0),(T(1.3),-40,0,0,LIN),(L,0,0,0)))))

    # ---------- 패턴 8: 화장의 고리 (3회 내리치기) ----------
    L=T(2.4)
    tr={'right_back':[('rotation',0,0,0,0,cm)],'left_back':[('rotation',0,0,0,0,cm)],'body':[('rotation',0,0,0,0,cm),('position',0,0,0,0,cm)],
        'right_arm':[('rotation',0,0,0,0,cm)],'left_arm':[('rotation',0,0,0,0,cm)],'head':[('rotation',0,0,0,0,cm)],'halo':[('rotation',0,0,0,0,cm)]}
    for j,h in enumerate([0.65,1.25,1.85]):
        a,hh=T(h-0.35),T(h)
        tr['right_back']+=[('rotation',a,45,0,-35,cm),('rotation',hh,-80,0,-5,LIN)]
        tr['left_back']+=[('rotation',a,45,0,35,cm),('rotation',hh,-80,0,5,LIN)]
        tr['right_arm']+=[('rotation',a,150,0,-20,cm),('rotation',hh,40,0,-10,LIN)]
        tr['left_arm']+=[('rotation',a,150,0,20,cm),('rotation',hh,40,0,10,LIN)]
        tr['body']+=[('rotation',a,10,0,0,cm),('rotation',hh,-22,0,0,LIN),('position',a,0,3,0,cm),('position',hh,0,-5,0,LIN)]
        tr['head']+=[('rotation',a,20,0,0,cm),('rotation',hh,-10,0,0,LIN)]
        tr['halo']+=[('rotation',hh,0,0,120*(j+1),LIN)]
    for k in tr: tr[k].append((tr[k][0][0],L,0,0,(360 if k=='halo' else 0),cm))
    tr['body'].append(('position',L,0,0,0,cm))
    add('skill_ring',L,'once',tr)

    # ---------- 패턴 9: 빛의 수호 (장치 지목) ----------
    if not enr:
        L=1.3
        add('skill_ward',L,'once',merge({
            'right_arm':R((0,0,0,0),(.4,85,0,-35),(.9,85,0,-35),(L,0,0,0)),
            'right_forearm':R((0,0,0,0),(.4,-20,0,0),(.9,-20,0,0),(L,0,0,0)),
            'head':R((0,0,0,0),(.4,5,-25,0),(.9,5,-25,0),(L,0,0,0)),
            'body':R((0,0,0,0),(.4,0,-15,0),(.9,0,-15,0),(L,0,0,0)),
            'halo':R((0,0,0,0),(.4,20,-20,0),(.9,20,-20,30),(L,0,0,0)),
            'heart':[('scale',0,1,1,1,cm),('scale',.4,1.4,1.4,1.4,cm),('scale',.9,1.4,1.4,1.4,cm),('scale',L,1,1,1,cm)]},
            FF('right',R((0,0,0,0),(.4,-50,0,0),(.9,-50,0,0),(L,0,0,0)))))

        # ---------- 패턴 10: 발악 (장치 0개) ----------
        L=2.2
        add('skill_desperate',L,'once',merge({
            'head':R((0,0,0,0),(.35,40,0,0),(.45,40,0,10,LIN),(.55,40,0,-10,LIN),(.65,40,0,10,LIN),(.75,40,0,-8,LIN),(1.0,35,0,0),(L,0,0,0)),
            'body':R((0,0,0,0),(.35,18,0,0),(1.0,18,0,0),(1.15,-10,0,0,LIN),(1.6,-10,0,0),(L,0,0,0))+[('scale',0,1,1,1,cm),('scale',.9,1.06,1.06,1.06,cm),('scale',1.0,0.95,0.95,0.95,LIN),('scale',1.45,1.06,1.06,1.06,cm),('scale',1.55,0.95,0.95,0.95,LIN),('scale',L,1,1,1,cm)],
            'right_back':R((0,0,0,0),(.35,-20,0,-80),(1.0,-20,0,-80),(1.15,-70,0,-10,LIN),(1.6,-70,0,-10),(L,0,0,0)),
            'left_back':R((0,0,0,0),(.35,-20,0,80),(1.0,-20,0,80),(1.15,-70,0,10,LIN),(1.6,-70,0,10),(L,0,0,0)),
            'right_arm':R((0,0,0,0),(.35,0,0,-85),(1.6,0,0,-85),(L,0,0,0)),'left_arm':R((0,0,0,0),(.35,0,0,85),(1.6,0,0,85),(L,0,0,0)),
            'heart':[('scale',0,1,1,1,cm),('scale',.35,1.6,1.6,1.6,cm),('scale',1.6,1.6,1.6,1.6,cm),('scale',L,1,1,1,cm)],
            'halo':R((0,0,0,0),(L,0,0,-360))},
            BF('right',R((0,0,0,0),(.35,-45,0,0),(1.6,-45,0,0),(L,0,0,0))),BF('left',R((0,0,0,0),(.35,-45,0,0),(1.6,-45,0,0),(L,0,0,0)))))

        # ---------- 50% 탄막 ----------
        L=1.8
        add('barrage_start',L,'hold',merge({
            'root':Pp((0,0,0,0),(1.2,0,18,0),(L,0,16,0)),
            'right_arm':R((0,0,0,0),(1.2,0,0,-85),(L,0,0,-85)),'left_arm':R((0,0,0,0),(1.2,0,0,85),(L,0,0,85)),
            'right_back':R((0,0,0,0),(1.2,-10,0,-65),(L,-10,0,-65)),'left_back':R((0,0,0,0),(1.2,-10,0,65),(L,-10,0,65)),
            'right_back_fore':R((0,0,0,0),(1.2,-60,0,0),(L,-60,0,0)),'left_back_fore':R((0,0,0,0),(1.2,-60,0,0),(L,-60,0,0)),
            'head':R((0,0,0,0),(1.2,28,0,0),(L,28,0,0)),
            'halo':R((0,0,0,0),(L,0,0,180))+[('scale',0,1,1,1,cm),('scale',1.2,1.35,1.35,1.35,cm),('scale',L,1.35,1.35,1.35,cm)],
            'skirt':R((0,0,0,0),(1.2,8,0,0),(L,8,0,0)),'tail':R((0,0,0,0),(1.2,15,0,0),(L,15,0,0))},
            BF('right',R((0,0,0,0),(1.2,-40,0,0),(L,-40,0,0))),BF('left',R((0,0,0,0),(1.2,-40,0,0),(L,-40,0,0)))))
        L=2.0
        add('barrage_loop',L,'loop',merge({
            'root':cyc(2,L,0,'position',1,16),
            'right_arm':R((0,0,0,-85),(L/2,0,0,-78),(L,0,0,-85)),'left_arm':R((0,0,0,85),(L/2,0,0,78),(L,0,0,85)),
            'right_back':R((0,-10,0,-65),(L/2,-16,0,-60),(L,-10,0,-65)),'left_back':R((0,-10,0,65),(L/2,-16,0,60),(L,-10,0,65)),
            'right_back_fore':R((0,-60,0,0),(L,-60,0,0)),'left_back_fore':R((0,-60,0,0),(L,-60,0,0)),
            'head':R((0,28,0,0),(L/2,24,0,4),(L,28,0,0)),
            'halo':[('rotation',0,0,0,180,LIN),('rotation',L,0,0,540,LIN),('scale',0,1.35,1.35,1.35,cm),('scale',L,1.35,1.35,1.35,cm)],
            'heart':[('scale',0,1.3,1.3,1.3,cm),('scale',L/2,1.5,1.5,1.5,cm),('scale',L,1.3,1.3,1.3,cm)],
            'skirt':R((0,8,0,0),(L/2,4,0,0),(L,8,0,0)),'tail':cyc(8,L,0,'rotation',2,0)},
            BF('right',R((0,-40,0,0),(L/2,-25,0,0),(L,-40,0,0))),BF('left',R((0,-40,0,0),(L/2,-25,0,0),(L,-40,0,0)))))

        # ---------- 그로기 ----------
        L=1.1
        add('groggy_start',L,'hold',merge({
            'root':Pp((0,0,16,0),(.45,0,-8,0,LIN),(.6,0,-5,0),(L,0,-7,0)),
            'body':R((0,0,0,0),(.45,-38,0,0),(L,-42,0,6)),
            'head':R((0,28,0,0),(.45,-40,0,0),(L,-48,0,14)),
            'right_arm':R((0,0,0,-85),(.45,-5,0,-15),(L,-5,0,-8)),'left_arm':R((0,0,0,85),(.45,-5,0,15),(L,-5,0,8)),
            'right_back':R((0,-10,0,-65),(.45,-80,0,-40),(L,-95,0,-45)),'left_back':R((0,-10,0,65),(.45,-80,0,40),(L,-95,0,45)),
            'right_back_fore':R((0,-60,0,0),(.45,20,0,0),(L,30,0,0)),'left_back_fore':R((0,-60,0,0),(.45,20,0,0),(L,30,0,0)),
            'halo':[('rotation',0,0,0,0,cm),('rotation',.45,50,0,25,cm),('rotation',L,60,0,30,cm),('position',0,0,0,0,cm),('position',.45,0,-8,4,cm),('position',L,0,-10,5,cm),('scale',0,1.35,1.35,1.35,cm),('scale',.45,1,1,1,cm)],
            'heart':[('scale',0,1.3,1.3,1.3,cm),('scale',L,0.6,0.6,0.6,cm)]},
            BF('right',R((0,-40,0,0),(.45,40,0,0),(L,50,0,0))),BF('left',R((0,-40,0,0),(.45,40,0,0),(L,50,0,0)))))
        L=2.4
        add('groggy_loop',L,'loop',merge({
            'root':Pp((0,0,-7,0),(L/2,0,-8,0),(L,0,-7,0)),
            'body':R((0,-42,0,6),(L/2,-39,0,4),(L,-42,0,6)),
            'head':R((0,-48,0,14),(L*0.6,-46,0,10),(L*0.65,-46,0,22,LIN),(L*0.7,-46,0,10,LIN),(L,-48,0,14)),
            'right_arm':R((0,-5,0,-8),(L/2,-2,0,-6),(L,-5,0,-8)),'left_arm':R((0,-5,0,8),(L/2,-2,0,6),(L,-5,0,8)),
            'right_back':R((0,-95,0,-45),(L/2,-92,0,-43),(L,-95,0,-45)),'left_back':R((0,-95,0,45),(L/2,-92,0,43),(L,-95,0,45)),
            'right_back_fore':R((0,30,0,0),(L,30,0,0)),'left_back_fore':R((0,30,0,0),(L,30,0,0)),
            'halo':[('rotation',0,60,0,30,cm),('rotation',L,60,0,30,cm),('position',0,0,-10,5,cm),('position',L,0,-10,5,cm)],
            'heart':[('scale',0,0.6,0.6,0.6,cm),('scale',L*0.3,0.9,0.9,0.9,LIN),('scale',L*0.35,0.6,0.6,0.6,LIN),('scale',L,0.6,0.6,0.6,cm)]},
            BF('right',R((0,50,0,0),(L,50,0,0))),BF('left',R((0,50,0,0),(L,50,0,0)))))

        # ---------- 변신 (장치 흡수) ----------
        L=3.0
        add('transform',L,'hold',merge({
            'root':Pp((0,0,-7,0),(.6,0,6,0),(2.4,0,10,0),(L,0,-4,0)),
            'body':R((0,-42,0,0),(.6,10,0,0),(2.4,22,0,0),(L,-35,0,0)),
            'head':R((0,-48,0,0),(.6,30,0,0),(2.4,40,0,0),(L,-30,0,0)),
            'right_arm':R((0,0,0,0),(.6,40,0,-60),(1.2,40,0,-40),(1.8,40,0,-70),(2.4,40,0,-50),(L,10,0,-10)),
            'left_arm':R((0,0,0,0),(.6,40,0,60),(1.2,40,0,40),(1.8,40,0,70),(2.4,40,0,50),(L,10,0,10)),
            'right_back':R((0,-95,0,-45),(.6,-10,0,-70),(1.2,-30,0,-30),(1.8,-10,0,-75),(2.4,-35,0,-25),(L,-60,0,10)),
            'left_back':R((0,-95,0,45),(.6,-10,0,70),(1.2,-30,0,30),(1.8,-10,0,75),(2.4,-35,0,25),(L,-60,0,-10)),
            'heart':[('scale',0,0.6,0.6,0.6,cm),('scale',1.0,1.4,1.4,1.4,cm),('scale',1.6,1.8,1.8,1.8,cm),('scale',2.2,2.2,2.2,2.2,cm),('scale',2.5,2.6,2.6,2.6,cm),('scale',L,0.4,0.4,0.4,LIN)],
            'halo':[('rotation',0,60,0,30,cm),('rotation',.6,0,0,0,cm),('rotation',2.4,0,0,720,cm),('rotation',L,0,0,720,cm),('position',0,0,-10,5,cm),('position',.6,0,0,0,cm),('scale',0,1,1,1,cm),('scale',2.4,1.4,1.4,1.4,cm),('scale',L,0.8,0.8,0.8,cm)],
            'skirt':R((0,0,0,0),(2.4,18,0,0),(L,-10,0,0)),'tail':R((0,0,0,0),(2.4,25,0,0),(L,0,0,0))},
            BF('right',R((0,0,0,0),(.6,-50,0,0),(2.4,-50,0,0),(L,60,0,0))),BF('left',R((0,0,0,0),(.6,-50,0,0),(2.4,-50,0,0),(L,60,0,0)))))

    # ---------- 등장 ----------
    if not enr:
        L=3.4
        add('spawn',L,'once',merge({
            'root':Pp((0,0,-110,0),(1.8,0,4,0),(2.1,0,0,0))+[('scale',0,.7,.7,.7,cm),('scale',1.8,1,1,1,cm)],
            'head':R((0,-55,0,0),(1.8,-45,0,0),(2.3,25,0,0,LIN),(2.6,22,0,0),(L,0,0,0)),
            'right_back':R((0,70,0,10),(1.8,60,0,5),(2.3,-25,0,-70,LIN),(2.8,-20,0,-65),(L,0,0,0)),
            'left_back':R((0,70,0,-10),(1.8,60,0,-5),(2.3,-25,0,70,LIN),(2.8,-20,0,65),(L,0,0,0)),
            'right_arm':R((0,10,0,0),(2.3,20,0,-50),(L,0,0,0)),'left_arm':R((0,10,0,0),(2.3,20,0,50),(L,0,0,0)),
            'halo':[('rotation',0,0,0,0,cm),('rotation',2.4,0,0,360,cm),('rotation',L,0,0,360,cm),('scale',0,0.2,0.2,0.2,cm),('scale',2.2,1.2,1.2,1.2,cm),('scale',L,1,1,1,cm)],
            'heart':[('scale',0,0.2,0.2,0.2,cm),('scale',2.3,1.6,1.6,1.6,LIN),('scale',L,1,1,1,cm)]},
            BF('right',R((0,60,0,0),(1.8,60,0,0),(2.3,-45,0,0,LIN),(L,0,0,0))),BF('left',R((0,60,0,0),(1.8,60,0,0),(2.3,-45,0,0,LIN),(L,0,0,0)))))
    else:   # 변신 직후 이어지는 실체화 + 포효
        L=2.8
        add('spawn',L,'once',merge({
            'body':R((0,-35,0,0),(1.0,-30,0,0),(1.5,14,0,0),(2.2,12,0,0),(L,0,0,0))+Pp((0,0,-14,-3),(1.0,0,-12,-3),(1.5,0,1,0),(L,0,0,0)),
            'right_leg':R((0,60,0,0),(1.0,55,0,0),(1.5,0,0,0)),'left_leg':R((0,60,0,0),(1.0,55,0,0),(1.5,0,0,0)),
            'right_shin':R((0,-95,0,0),(1.0,-90,0,0),(1.5,0,0,0)),'left_shin':R((0,-95,0,0),(1.0,-90,0,0),(1.5,0,0,0)),
            'right_foot':R((0,35,0,0),(1.0,32,0,0),(1.5,0,0,0)),'left_foot':R((0,35,0,0),(1.0,32,0,0),(1.5,0,0,0)),
            'head':R((0,-30,0,0),(1.0,-25,0,0),(1.5,38,0,0),(1.6,38,0,6,LIN),(1.7,38,0,-6,LIN),(1.8,38,0,6,LIN),(2.2,34,0,0),(L,0,0,0)),
            'jaw':R((0,0,0,0),(1.3,0,0,0),(1.5,-40,0,0),(2.2,-38,0,0),(L,0,0,0)),
            'right_back':R((0,-60,0,10),(1.0,-55,0,0),(1.5,-15,0,-80,LIN),(2.2,-15,0,-78),(L,0,0,0)),
            'left_back':R((0,-60,0,-10),(1.0,-55,0,0),(1.5,-15,0,80,LIN),(2.2,-15,0,78),(L,0,0,0)),
            'right_arm':R((0,10,0,-10),(1.5,-10,0,-70),(2.2,-10,0,-68),(L,0,0,0)),'left_arm':R((0,10,0,10),(1.5,-10,0,70),(2.2,-10,0,68),(L,0,0,0)),
            'halo':[('rotation',0,0,0,0,cm),('rotation',1.5,0,0,180,LIN),('rotation',L,0,0,360,cm),('scale',0,0.8,0.8,0.8,cm),('scale',1.5,1.3,1.3,1.3,cm),('scale',L,1,1,1,cm)]},
            BF('right',R((0,60,0,0),(1.0,60,0,0),(1.5,-45,0,0,LIN),(2.2,-45,0,0),(L,0,0,0))),BF('left',R((0,60,0,0),(1.0,60,0,0),(1.5,-45,0,0,LIN),(2.2,-45,0,0),(L,0,0,0)))))

    # ---------- 사망 ----------
    if not enr:
        L=3.0
        add('death',L,'hold',merge({
            'head':R((0,0,0,0),(.5,40,0,0),(.6,40,0,12,LIN),(.7,40,0,-12,LIN),(.8,40,0,0),(2.4,-40,0,0)),
            'right_back':R((0,0,0,0),(.6,-30,0,-85),(2.6,40,0,-20)),'left_back':R((0,0,0,0),(.6,-30,0,85),(2.6,40,0,20)),
            'right_arm':R((0,0,0,0),(.6,-10,0,-80),(2.6,20,0,0)),'left_arm':R((0,0,0,0),(.6,-10,0,80),(2.6,20,0,0)),
            'root':Pp((0,0,0,0),(.6,0,6,0),(2.8,0,-60,0))+[('scale',0,1,1,1,cm),('scale',.6,1.08,1.08,1.08,cm),('scale',2.8,.2,.2,.2,cm)],
            'heart':[('scale',0,1,1,1,cm),('scale',.6,2,2,2,cm),('scale',1.4,0.1,0.1,0.1,cm)],
            'halo':[('rotation',0,0,0,0,cm),('rotation',2.0,70,0,90,cm),('position',0,0,0,0,cm),('position',2.0,0,-20,10,cm)]}))
    else:
        L=3.2
        add('death',L,'hold',merge({
            'head':R((0,0,0,0),(.5,40,0,0),(.6,40,0,14,LIN),(.7,40,0,-14,LIN),(.9,40,0,0),(2.4,-35,0,0)),
            'jaw':R((0,0,0,0),(.5,-40,0,0),(2.4,-25,0,0)),
            'right_leg':R((0,0,0,0),(1.0,0,0,0),(1.6,60,0,0)),'left_leg':R((0,0,0,0),(1.0,0,0,0),(1.6,60,0,0)),
            'right_shin':R((0,0,0,0),(1.0,0,0,0),(1.6,-110,0,0)),'left_shin':R((0,0,0,0),(1.0,0,0,0),(1.6,-110,0,0)),
            'right_foot':R((0,0,0,0),(1.6,50,0,0)),'left_foot':R((0,0,0,0),(1.6,50,0,0)),
            'body':Pp((0,0,0,0),(1.0,0,0,0),(1.6,0,-24,-4),(L,0,-25,-5))+R((0,0,0,0),(.6,15,0,0),(1.6,0,0,0),(2.6,-70,0,0)),
            'right_back':R((0,0,0,0),(.6,-30,0,-85),(1.6,-20,0,-80),(2.8,50,0,-30)),'left_back':R((0,0,0,0),(.6,-30,0,85),(1.6,-20,0,80),(2.8,50,0,30)),
            'right_arm':R((0,0,0,0),(.6,-10,0,-75),(2.6,40,0,-10)),'left_arm':R((0,0,0,0),(.6,-10,0,75),(2.6,40,0,10)),
            'halo':[('rotation',0,0,0,0,cm),('rotation',2.4,80,0,120,cm),('position',0,0,0,0,cm),('position',2.4,0,-24,14,cm)]}))
    return A

if __name__=='__main__':
    O='/mnt/user-data/outputs/'
    if os.path.exists(HITBOX_JSON): os.remove(HITBOX_JSON)
    export('ash_shadow', build(False), lambda g: build_anims(False,g), 11, O+'ash_shadow.bbmodel', HB_BASE)
    export('ash_shadow_enraged', build(True), lambda g: build_anims(True,g), 13, O+'ash_shadow_enraged.bbmodel', HB_ENR)
    apply_palette(SHINY)
    export('ash_shadow_shiny', build(False), lambda g: build_anims(False,g), 11, O+'ash_shadow_shiny.bbmodel', HB_BASE)
    export('ash_shadow_enraged_shiny', build(True), lambda g: build_anims(True,g), 13, O+'ash_shadow_enraged_shiny.bbmodel', HB_ENR)
