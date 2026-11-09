"""해방된 악마 (freed_demon) v2 — 고퀄 버전.
거대하고 구부정한 악마: 큰 숫양뿔(왼쪽은 부러짐)+왕관뿔, 산양 해골형 머리, 송곳니 가득한 턱,
가슴에 깨진 봉인 문양이 불타는 흉터, 뼈 갑각 어깨, 긴 팔과 큰 발톱, 끊어진 족쇄 사슬,
털 덮인 산양 다리와 갈라진 발굽, 거대한 박쥐 날개, 칼날 꼬리."""
from common import *
import math

SKIN=(128,30,34); DSKIN=(70,14,20); HL=(170,56,50); BELLY=(96,24,28)
FUR=(74,44,38); DFUR=(34,20,20)
HORN=(44,34,36); DHORN=(22,16,18); HORNTIP=(255,120,40)
BONE=(206,190,160); DBONE=(120,104,84)
IRON=(80,76,86); DIRON=(40,38,46); RUST=(120,64,40)
EMBER=(255,140,40); EMBER2=(255,214,110)
EYE=(255,92,30,255); EYE_LIE=(200,96,255,255)
MEMB=(96,22,30,235); DMEMB=(54,10,18,235)
LEATHER=(58,36,30); CLOTH=(30,18,22); GUM=(110,20,28); TOOTH=(240,232,210)
SHINY=dict(SKIN=(46,34,70), DSKIN=(22,16,36), HL=(84,66,120), BELLY=(36,26,56),
           FUR=(222,214,226), DFUR=(150,140,160),
           HORN=(216,180,96), DHORN=(140,104,44), HORNTIP=(120,240,255),
           BONE=(240,236,250), DBONE=(150,146,170),
           IRON=(200,170,100), DIRON=(120,96,52), RUST=(90,200,220),
           EMBER=(90,220,255), EMBER2=(210,250,255),
           EYE=(255,226,90,255), EYE_LIE=(255,90,170,255),
           MEMB=(46,34,80,235), DMEMB=(22,16,44,235),
           LEATHER=(40,30,60), CLOTH=(14,12,24), GUM=(60,30,90), TOOTH=(255,252,240))
HITBOX=(2.6, 6.0, 4.9)
def apply_palette(p): globals().update(p)
def lerp(a,b,f): return tuple(a[i]+(b[i]-a[i])*f for i in range(3))

# ---------------- 페인터 ----------------
def flesh(top=None, bot=None, crack=0.012, muscle=False):
    """피부: 위→아래 그라데이션 + 가장자리 어둡게(AO) + 근육결 + 드문 불씨 균열"""
    def p(img, face, r, rng):
        t=top or HL; b=bot or DSKIN; mid=SKIN
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        for ly in range(h):
            g=ly/max(1,h-1) if face not in('up','down') else 0.35
            base=lerp(t,mid,min(1,g*2)) if g<.5 else lerp(mid,b,(g-.5)*2)
            for lx in range(w):
                f=SHADE[face]*(1+rng.uniform(-.07,.07))
                edge=min(lx,w-1-lx,ly,h-1-ly)
                if edge==0 and w>2 and h>2: f*=.78
                if muscle and face in('north','south') and w>=6 and (lx==w//2 or (ly%5==4 and 1<lx<w-2)): f*=.84
                img.putpixel((x1+lx,y1+ly),col(base,f))
        for _ in range(int(w*h*crack)):
            x=rng.randrange(w); y=rng.randrange(h)
            for s in range(rng.randint(3,7)):
                if 0<=x<w and 0<=y<h:
                    img.putpixel((x1+x,y1+y),col(EMBER2 if s==0 else EMBER,rng.uniform(.85,1)))
                x+=rng.choice([-1,0,1]); y+=rng.choice([0,1,1])
    return p

def fur(base=None, dark=None, ragged=3):
    """산양 털: 세로 털결 + 아래 가장자리 들쭉날쭉(투명)"""
    def p(img, face, r, rng):
        b=base or FUR; d=dark or DFUR
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        cut=[rng.randint(0,ragged) for _ in range(w)] if face not in('up','down') else [0]*w
        streak=[rng.random() for _ in range(w)]
        for lx in range(w):
            for ly in range(h):
                if ly>=h-cut[lx]: img.putpixel((x1+lx,y1+ly),(0,0,0,0)); continue
                c=lerp(b,d,0.6 if streak[lx]<.3 else (0.15 if streak[lx]>.8 else .35))
                f=SHADE[face]*(1+rng.uniform(-.12,.12))*(1.05-.25*ly/max(1,h))
                img.putpixel((x1+lx,y1+ly),col(c,f))
    return p

def horn(tipfrac=0.0):
    """뿔: 가로 고리 마디 + 끝으로 갈수록 불씨색"""
    def p(img, face, r, rng):
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        for ly in range(h):
            for lx in range(w):
                ring = (ly%3==2) if face not in('up','down') else (lx%3==2)
                c=DHORN if ring else HORN
                if tipfrac>0: c=lerp(c,HORNTIP,tipfrac*(ly/max(1,h-1) if face not in('up','down') else 1))
                img.putpixel((x1+lx,y1+ly),col(c,SHADE[face]*(1+rng.uniform(-.08,.08))))
    return p

def bone(dark=False):
    def p(img, face, r, rng):
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        for ly in range(h):
            g=ly/max(1,h-1)
            for lx in range(w):
                c=lerp(DBONE,BONE,.35) if dark else lerp(BONE,DBONE,g*.6)
                f=SHADE[face]*(1+rng.uniform(-.08,.08))
                if min(lx,w-1-lx,ly,h-1-ly)==0 and w>2 and h>2: f*=.82
                img.putpixel((x1+lx,y1+ly),col(c,f))
        for _ in range(int(w*h*0.02)):
            x=rng.randrange(w); y=rng.randrange(h)
            for s in range(3):
                if 0<=x<w and 0<=y<h: img.putpixel((x1+x,y1+y),col(DBONE,.7))
                y+=1; x+=rng.choice([-1,0,1])
    return p

def claw():
    def p(img, face, r, rng):
        x1,y1,x2,y2=r; h=y2-y1
        for y in range(y1,y2):
            g=(y-y1)/max(1,h-1)
            for x in range(x1,x2):
                c=lerp(DHORN,BONE,g) if face not in('up','down') else DHORN
                img.putpixel((x,y),col(c,SHADE[face]*(1+rng.uniform(-.06,.06))))
    return p

def iron(runes=0.0):
    def p(img, face, r, rng):
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        for ly in range(h):
            for lx in range(w):
                e=min(lx,w-1-lx,ly,h-1-ly)==0
                c=IRON if e else DIRON
                if rng.random()<.08: c=RUST
                img.putpixel((x1+lx,y1+ly),col(c,SHADE[face]*(1+rng.uniform(-.1,.1))))
        if runes and face not in('up','down') and h>=3:
            ly=h//2
            for lx in range(1,w-1):
                if rng.random()<runes: px(img,r,lx,ly,col(EMBER))
    return p

def chainp():
    def p(img, face, r, rng):
        x1,y1,x2,y2=r
        for y in range(y1,y2):
            for x in range(x1,x2):
                c=DIRON if (x-x1+y-y1)%3==0 else IRON
                if rng.random()<.07: c=RUST
                img.putpixel((x,y),col(c,SHADE[face]*(1+rng.uniform(-.1,.1))))
    return p

def membrane(fingers=None, slope=0.0):
    """날개막: 손가락 사이가 활처럼 파인 아래 가장자리 + 핏줄 + 작은 구멍"""
    def p(img, face, r, rng):
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        fs=sorted(fingers) if fingers else [0,w-1]
        for lx in range(w):
            mx = lx if face=='north' else w-1-lx
            a=max([f for f in fs if f<=mx] or [0]); b=min([f for f in fs if f>mx] or [w])
            span=max(1,b-a); t=(mx-a)/span
            cut=int(4*t*(1-t)*min(h*0.45,span*0.8))+rng.randint(0,1)+int(slope*mx)
            for ly in range(h):
                if ly>=h-cut or rng.random()<.01: img.putpixel((x1+lx,y1+ly),(0,0,0,0)); continue
                g=ly/max(1,h-1)
                cc=lerp(MEMB,DMEMB,g*.8)
                if mx in fs or abs(mx-a)<1 or (ly%8==0 and rng.random()<.35): cc=lerp(cc,DSKIN,.7)
                img.putpixel((x1+lx,y1+ly),col(cc,(1+rng.uniform(-.08,.08)),MEMB[3]))
    return p

def tatter_cloth(depth=4):
    return tatter((CLOTH[0],CLOTH[1],CLOTH[2],255),depth,0.02)

def skull_face(img, r, rng):
    """머리 정면 12x11: 깊은 눈구멍, 이마 주름"""
    w,h=r[2]-r[0],r[3]-r[1]
    for lx in range(w):
        px(img,r,lx,h-1,col(DSKIN,.7))
    for sx in (1,w-6):                 # 눈구멍(어둡게) — 눈은 별도 평면
        for dx in range(5):
            for dy in range(3,7): px(img,r,sx+dx,dy,(26,6,10,255))
    for lx in range(3,w-3): px(img,r,lx,1,col(DSKIN,.75))
    px(img,r,w//2,2,col(DSKIN,.7)); px(img,r,w//2-1,3,col(DSKIN,.7))

def snout_face(img, r, rng):
    w,h=r[2]-r[0],r[3]-r[1]
    for lx in (1,2,w-3,w-2): px(img,r,lx,1,(20,4,8,255))   # 콧구멍
    for lx in range(w): px(img,r,lx,h-1,(30,6,10,255))     # 입술선

def seal_scar(img, r, rng):
    """가슴: 깨진 봉인 문양이 타오르는 흉터"""
    w,h=r[2]-r[0],r[3]-r[1]; cx,cy=(w-1)/2,(h-1)/2+0.5
    R=min(w,h*1.6)/2.4
    for ly in range(h):
        for lx in range(w):
            dx=(lx-cx)/1.0; dy=(ly-cy)*1.6
            d=math.hypot(dx,dy); a=math.atan2(dy,dx)
            on = abs(d-R)<.8 and not (0.3<a<0.9)                  # 깨진 원
            on = on or (d<R and abs(math.sin(a*3))<.09 and d>R*.35)  # 별 가지
            on = on or abs(d-R*.35)<.6
            if on: px(img,r,lx,ly,col(EMBER if rng.random()<.8 else EMBER2))
    x,y=int(cx+R*.7),0                                            # 갈라진 금
    while y<h:
        px(img,r,x,y,col(EMBER2)); y+=1; x+=rng.choice([-1,0,0,1])

def abs_paint(img, face, r, rng):
    flesh(SKIN,BELLY,0.0)(img,face,r,rng)

# ---------------- 모델 ----------------
def move(node,dx,dy,dz):
    node.origin=[node.origin[0]+dx,node.origin[1]+dy,node.origin[2]+dz]
    for c in node.children:
        if isinstance(c,Cube):
            c.frm=[c.frm[0]+dx,c.frm[1]+dy,c.frm[2]+dz]; c.to=[c.to[0]+dx,c.to[1]+dy,c.to[2]+dz]
            c.origin=[c.origin[0]+dx,c.origin[1]+dy,c.origin[2]+dz]
        else: move(c,dx,dy,dz)
def build():
    sk=flesh(); skm=flesh(muscle=True); dk=flesh(SKIN,DSKIN,0.006)
    # ===== 머리 =====
    def ram_horn(side, broken):
        m=side=='left'
        segs=[([5,80,-10],[10,86,-4],0),([8,84,-8],[13,89,-2],0),([11,86,-4],[16,91,1],.1)]
        if not broken:
            segs+=[([13,84,0],[18,89,5],.2),([15,79,2],[19,85,6],.35),([15,75,-1],[18,80,3],.55),([15,73,-4],[17,76,-1],1.0)]
        kids=[C(f'{side}_ram_horn{i+1}',a,b,horn(t),m) for i,(a,b,t) in enumerate(segs)]
        if broken:
            kids+=[C(f'{side}_ram_horn_stub1',[12,91,-3],[14,93,-1],horn(.8),m),
                   C(f'{side}_ram_horn_stub2',[14,90,-1],[16,92,1],horn(.9),m),
                   C(f'{side}_ram_horn_crack',[12,85,-5],[13,86,-4],glow(EMBER),m)]
        crown=[C(f'{side}_crown_horn1',[2,82,-9],[5,87,-6],horn(),m),
               C(f'{side}_crown_horn2',[2.5,86,-8],[4.5,91,-5],horn(.2),m),
               C(f'{side}_crown_horn3',[3,90,-7],[4,95,-4],horn(.7),m)]
        return G(f'{side}_horn',[6,82,-7],kids+crown,mirror=m)
    teeth=[]
    for s,m in (('right',False),('left',True)):
        teeth+=[C(f'{s}_fang',[2.5,69,-21],[3.5,73,-20],bone(),m),
                C(f'{s}_tooth1',[4,71,-20],[5,73,-19],bone(),m),
                C(f'{s}_tooth2',[4,71,-18],[5,73,-17],bone(),m),
                C(f'{s}_cheek_spike1',[7,76,-11],[11,78,-9],bone(True),m,rot=[0,-25,10],origin=[6,77,-10]),
                C(f'{s}_cheek_spike2',[7,73,-9],[10,75,-7],bone(True),m,rot=[0,-20,-10],origin=[6,74,-8]),
                C(f'{s}_ear',[7,78,-7],[9,82,0],dk,m,rot=[15,-15,0],origin=[7,80,-6]),
                C(f'{s}_ear_tip',[7.5,80,0],[8.5,82,3],dk,m,rot=[15,-15,0],origin=[7,80,-6])]
    jaw_kids=[C('jaw',[-6,69,-20],[6,73,-6],with_faces(dk,up=lambda img,r,rng:[img.putpixel((x,y),col(GUM,rng.uniform(.85,1))) for x in range(r[0],r[2]) for y in range(r[1],r[3])])),
              C('jaw_tip',[-4,68,-21],[4,70,-17],dk),
              C('chin_spike_c',[-1,63,-17],[1,68,-15],bone(True)),
              C('chin_spike_r',[2.5,64,-15],[4.5,68,-13],bone(True),rot=[0,0,-10]),
              C('chin_spike_l',[-4.5,64,-15],[-2.5,68,-13],bone(True),rot=[0,0,10])]
    for s,m in (('right',False),('left',True)):
        jaw_kids+=[C(f'{s}_low_fang',[3,73,-20],[4,76,-19],bone(),m),
                   C(f'{s}_low_tooth',[4.5,73,-17],[5.5,75,-16],bone(),m)]
    jaw=Group('jaw',[0,73,-7],jaw_kids)
    eyes=Group('eyes',[0,78.5,-14],[
        C('right_eye',[1.5,79,-14.1],[5.5,80,-14.1],glow(EYE)),
        C('left_eye',[-5.5,79,-14.1],[-1.5,80,-14.1],glow(EYE)),
        C('right_pupil_glow',[3,78,-14.15],[4,79,-14.15],glow(EYE)),
        C('left_pupil_glow',[-4,78,-14.15],[-3,79,-14.15],glow(EYE))])
    eyes_lie=Group('eyes_lie',[0,78.5,-14],[
        C('right_eye_lie',[1.5,79,-14.2],[5.5,80,-14.2],glow(EYE_LIE)),
        C('left_eye_lie',[-5.5,79,-14.2],[-1.5,80,-14.2],glow(EYE_LIE)),
        C('right_pupil_lie',[3,78,-14.25],[4,79,-14.25],glow(EYE_LIE)),
        C('left_pupil_lie',[-4,78,-14.25],[-3,79,-14.25],glow(EYE_LIE))])
    head=Group('head',[0,74,-5],[
        C('skull',[-7,72,-14],[7,84,-2],with_faces(sk,north=skull_face)),
        C('skull_back',[-6,73,-2],[6,83,1],dk),
        C('brow',[-8,80,-15],[8,83,-12],dk),
        C('brow_spike_r',[3,82,-15],[5,85,-13],bone(True),rot=[-25,0,-15]),
        C('brow_spike_l',[-5,82,-15],[-3,85,-13],bone(True),rot=[-25,0,15]),
        C('snout',[-5,73,-21],[5,79,-14],with_faces(sk,north=snout_face)),
        C('nose_bridge',[-2.5,78,-20],[2.5,80,-14],dk),
        C('nose_ridge_spike',[-0.5,80,-17],[0.5,82,-15],bone(True)),
        jaw, eyes, eyes_lie, ram_horn('right',False), ram_horn('left',True)]+teeth)
    move(head,0,4,-3)
    # ===== 팔 =====
    def arm(side):
        m=side=='left'
        fingers=[]
        for i,z in enumerate([-5,-2.5,0,2.5]):
            fingers+=[C(f'{side}_finger{i+1}',[17,24,z],[19,30,z+2],dk,m),
                      C(f'{side}_claw{i+1}',[17,18,z],[19,24,z+2],claw(),m),
                      C(f'{side}_claw_tip{i+1}',[17.5,14,z+0.5],[18.5,18,z+1.5],claw(),m)]
        hand=G(f'{side}_hand',[18,36,-0.5],[
            C(f'{side}_palm',[14.5,30,-5.5],[21.5,36,4.5],sk,m),
            C(f'{side}_knuckles',[16,29,-6],[20,32,5],dk,m),
            C(f'{side}_thumb',[14,26,-7],[16,32,-5],dk,m),
            C(f'{side}_thumb_claw',[14,21,-7],[16,26,-5],claw(),m)]+fingers,mirror=m)
        links=[]
        for i in range(5):
            y=40-i*5
            if i%2==0: links.append(C(f'{side}_chain_link{i+1}',[17.5,y-5,4.5],[18.5,y+1,7.5],chainp(),m))
            else:      links.append(C(f'{side}_chain_link{i+1}',[16.5,y-5,5.5],[19.5,y+1,6.5],chainp(),m))
        links.append(C(f'{side}_chain_broken',[17.5,12,5],[18.5,15,7],glow(EMBER),m))
        chain=G(f'{side}_chain',[18,41,6],links,mirror=m)
        fore=G(f'{side}_forearm',[18,53,-0.5],[
            C(f'{side}_forearm',[14.5,36,-5],[21.5,54,4],skm,m),
            C(f'{side}_forearm_back',[15,40,4],[21,52,5],dk,m),
            C(f'{side}_shackle',[13.5,40,-6],[22.5,45,5],iron(.5),m),
            C(f'{side}_shackle_rivet',[22.5,41.5,-1.5],[23.5,43.5,0.5],iron(),m),
            C(f'{side}_arm_blade1',[21,48,-1],[26,50,1],bone(True),m,rot=[0,0,25],origin=[21,49,0]),
            C(f'{side}_arm_blade2',[21,44,-1],[25,46,1],bone(True),m,rot=[0,0,25],origin=[21,45,0]),
            C(f'{side}_elbow_spike',[17,52,4],[19,55,9],bone(True),m,rot=[-30,0,0],origin=[18,53,5]),
            chain, hand],[10,0,0],mirror=m)
        return G(f'{side}_arm',[16,70,-1],[
            C(f'{side}_pauldron',[13,68,-7],[21,75,5],bone(True),m),
            C(f'{side}_pauldron_rim',[12,66,-8],[22,68,6],bone(True),m),
            C(f'{side}_pauldron_spike1',[15,75,-5],[17,82,-3],bone(),m,rot=[-10,0,-20],origin=[16,75,-4]),
            C(f'{side}_pauldron_spike2',[18,74,-1],[20,80,1],bone(),m,rot=[0,0,-30],origin=[19,74,0]),
            C(f'{side}_pauldron_spike3',[14,75,2],[16,79,4],bone(),m,rot=[20,0,-15],origin=[15,75,3]),
            C(f'{side}_upper_arm',[14,52,-5],[22,70,4],skm,m),
            C(f'{side}_bicep',[15,56,-6],[21,66,-5],sk,m),
            fore],[0,0,8],mirror=m)
    # ===== 날개 =====
    def wing(side):
        m=side=='left'
        tip=G(f'{side}_wing_tip',[28,70,7.5],[
            C(f'{side}_wing_bone2',[28,69,6],[52,72,9],dk,m),
            C(f'{side}_wing_hook',[50,72,6.5],[52,77,8.5],claw(),m),
            C(f'{side}_wing_hook_tip',[50.5,77,7],[51.5,80,8],claw(),m),
            C(f'{side}_wing_finger1',[50,40,6.5],[52,69,8.5],dk,m),
            C(f'{side}_wing_finger2',[42,42,6.5],[44,69,8.5],dk,m),
            C(f'{side}_wing_finger3',[35,46,6.5],[37,69,8.5],dk,m),
            C(f'{side}_wing_claw1',[50.5,37,7],[51.5,40,8],claw(),m),
            C(f'{side}_wing_claw2',[42.5,39,7],[43.5,42,8],claw(),m),
            C(f'{side}_wing_membrane_tip',[28,36,7.5],[52,69,7.5],membrane([7,14,22,23],0.25),m)],[0,0,14],mirror=m)
        return G(f'{side}_wing',[8,70,7],[
            C(f'{side}_wing_root',[6,66,5],[12,73,10],sk,m),
            C(f'{side}_wing_bone1',[8,69,6],[28,72,9],dk,m),
            C(f'{side}_wing_joint',[26,67,5],[30,73,10],bone(True),m),
            C(f'{side}_wing_joint_spike',[27,73,6.5],[29,77,8.5],bone(),m),
            C(f'{side}_wing_membrane',[9,42,7.5],[28,69,7.5],membrane([0,19],0.6),m),
            tip],[0,-34,6],mirror=m)
    # ===== 꼬리 =====
    tail=Group('tail',[0,46,6],[
        C('tail1',[-3,43,6],[3,49,16],sk),
        C('tail1_spike',[-1,49,9],[1,53,12],bone(True),rot=[-30,0,0]),
        Group('tail2',[0,46,16],[
            C('tail2',[-2.5,43.5,16],[2.5,48.5,26],sk),
            C('tail2_spike',[-1,48.5,19],[1,52.5,22],bone(True),rot=[-30,0,0]),
            Group('tail3',[0,46,26],[
                C('tail3',[-2,44,26],[2,48,35],dk),
                C('tail3_spike',[-0.5,48,29],[0.5,51,32],bone(True),rot=[-30,0,0]),
                Group('tail4',[0,46,35],[
                    C('tail4',[-1.5,44.5,35],[1.5,47.5,43],dk),
                    C('tail_blade',[-0.5,43,42],[0.5,49,48],bone(),rot=[45,0,0],origin=[0,46,45]),
                    C('tail_blade_spike',[-1,44.5,48],[1,47.5,52],bone(True))],[-15,0,0])],[-18,0,0])],[-18,0,0])],[28,0,0])
    # ===== 몸통 =====
    spines=[C(f'spine{i+1}',[-1,y,6],[1,y+5,9],bone(),rot=[-35,0,0],origin=[0,y,7]) for i,y in enumerate([56,61,66,71])]
    abs_=[]
    for row in range(3):
        for s,m in (('right',False),('left',True)):
            abs_.append(C(f'{s}_ab{row+1}',[0.5,51+row*2.5,-6],[4.5,53+row*2.5,-5],flesh(SKIN,BELLY,0),m))
    collar_links=[C('collar_link1',[-1,68,-9],[1,72,-8],chainp()),C('collar_link2',[-1.5,65,-9.5],[1.5,68,-8.5],chainp()),
                  C('collar_link3',[-0.5,62,-9],[0.5,65,-8],glow(EMBER))]
    chest=Group('chest',[0,50,0],[
        C('abdomen',[-8,50,-5],[8,58,5],flesh(SKIN,BELLY,0.004)),
        C('ribcage',[-13,58,-7],[13,74,7],skm),
        C('pecs',[-12,64,-8],[12,73,-7],with_faces(flesh(HL,SKIN,0.0),north=seal_scar)),
        C('right_ribs',[13,59,-5],[14,66,3],stripes(SKIN,DSKIN,2)),
        C('left_ribs',[-14,59,-5],[-13,66,3],stripes(SKIN,DSKIN,2)),
        C('traps',[-11,70,-4],[11,78,7],sk),
        C('neck',[-5,72,-10],[5,80,1],dk),
        C('collar',[-6.5,72,-8],[6.5,75,3],iron(.4)),
        C('back_plate',[-8,60,7],[8,70,8],bone(True))]+abs_+spines+collar_links+
        [head, arm('right'), arm('left'), wing('right'), wing('left')],[-12,0,0])
    skulls=[]
    for s,m,x in (('right',False,4),('left',True,4)):
        skulls+=[C(f'{s}_belt_skull',[x,42,-9],[x+3,45,-6],bone(),m),
                 C(f'{s}_belt_skull_jaw',[x+0.5,41,-8.5],[x+2.5,42,-6.5],bone(True),m)]
    body=Group('body',[0,46,0],[
        C('pelvis',[-9,42,-6],[9,50,6],dk),
        C('belt',[-9.5,46,-6.5],[9.5,49,6.5],iron(.2)),
        C('belt_buckle',[-2,45,-7.5],[2,50,-6.5],iron(.6)),
        Group('loin_front',[0,46,-7],[C('loin_front',[-4,26,-7],[4,46,-7],tatter_cloth(5))]),
        Group('loin_back',[0,46,7],[C('loin_back',[-7,22,7],[7,46,7],tatter_cloth(6))]),
        chest, tail]+skulls)
    # ===== 다리 =====
    def leg(side):
        m=side=='left'
        foot=G(f'{side}_foot',[7.5,21,3],[
            C(f'{side}_hock',[4.5,11,0],[10.5,21,6],dk,m),
            C(f'{side}_heel_spike',[6.5,14,6],[8.5,17,10],bone(True),m),
            C(f'{side}_meta',[5,4,-2],[10,11,4],dk,m),
            C(f'{side}_ankle_fur',[3.5,4,-3],[11.5,8,5],fur(ragged=2),m),
            C(f'{side}_hoof_in',[4,0,-6],[7,5,3],horn(),m),
            C(f'{side}_hoof_out',[8,0,-6],[11,5,3],horn(),m),
            C(f'{side}_dewclaw',[6.5,2,3],[8.5,5,5],horn(),m)],mirror=m)
        shin=G(f'{side}_shin',[7.5,33,-3],[
            C(f'{side}_shin',[4.5,20,-3],[10.5,33,3],fur(),m),
            C(f'{side}_shin_front',[5.5,22,-4],[9.5,31,-3],sk,m),
            C(f'{side}_knee_spike',[6.5,30,-6],[8.5,35,-4],bone(),m,rot=[-20,0,0],origin=[7.5,32,-5]),
            foot],mirror=m)
        return G(f'{side}_leg',[7,44,0],[
            C(f'{side}_thigh',[3,32,-6],[12,46,3],fur(),m),
            C(f'{side}_thigh_fringe',[2.5,28,-6.5],[12.5,33,3.5],fur(ragged=4),m),
            shin],mirror=m)
    return Group('root',[0,0,0],[body, leg('right'), leg('left')])

# ---------------- 애니메이션 ----------------
# 규약: +x = 몸통/머리는 뒤로 젖힘(얼굴이 위로), 늘어진 팔다리는 앞으로. 앞으로 숙이기 = -x
def anims(Gs):
    n='animation.freed_demon.'; cm='catmullrom'
    def sw(a,L): return [('rotation',0,a,0,0,cm),('rotation',L/2,-a,0,0,cm),('rotation',L,a,0,0,cm)]
    def mir(tr):
        out=dict(tr)
        for k,v in tr.items():
            if k.startswith('right_') and 'left_'+k[6:] not in tr:
                out['left_'+k[6:]]=[(c,t,x,-y,-z,*r) if c=='rotation' else ((c,t,-x,y,z,*r) if c=='position' else (c,t,x,y,z,*r)) for (c,t,x,y,z,*r) in v]
        return out
    Z=lambda t:('rotation',t,0,0,0,cm)
    A=[]
    def add(name,L,loop,tr):
        for k,v in tr.items():
            seen=set(); tr[k]=[x for x in v if not ((x[0],x[1]) in seen or seen.add((x[0],x[1])))]
        A.append(anim(n+name,L,loop,tr,Gs))
    KNEEL=-16
    # 등장: 무릎 꿇고 사슬에 묶인 채 웅크림 → 몸부림 → 족쇄 끊으며 일어나 포효
    add('spawn',4.0,'once',mir({
        'body':[('position',0,0,KNEEL,0),('position',2.0,0,KNEEL,0),('position',2.7,0,0,0,cm),('position',4.0,0,0,0)],
        'right_leg':[('rotation',0,70,0,0),('rotation',2.0,70,0,0),('rotation',2.7,0,0,0,cm)],
        'right_shin':[('rotation',0,-100,0,0),('rotation',2.0,-100,0,0),('rotation',2.7,0,0,0,cm)],
        'right_foot':[('rotation',0,30,0,0),('rotation',2.0,30,0,0),('rotation',2.7,0,0,0,cm)],
        'chest':[('rotation',0,-38,0,0),('rotation',.8,-34,6,4,cm),('rotation',1.2,-40,-6,-4,cm),('rotation',1.6,-34,6,4,cm),('rotation',2.0,-38,0,0,cm),
                 ('rotation',2.4,22,0,0,cm),('rotation',3.4,16,0,0,cm),('rotation',4.0,0,0,0,cm)],
        'head':[('rotation',0,-30,0,0),('rotation',2.0,-30,0,0),('rotation',2.4,32,0,0,cm),('rotation',3.4,26,0,0,cm),('rotation',4.0,0,0,0,cm)],
        'jaw':[Z(0),('rotation',2.3,0,0,0),('rotation',2.5,-30,0,0,cm),('rotation',3.4,-26,0,0,cm),Z(3.9)],
        'right_arm':[('rotation',0,55,0,-38),('rotation',2.0,55,0,-38),('rotation',2.4,10,0,110,cm),('rotation',3.4,0,0,100,cm),Z(4.0)],
        'right_chain':[Z(0),('rotation',2.0,0,0,0),('rotation',2.4,-60,0,0,cm),('rotation',3.0,20,0,0,cm),Z(3.6)],
        'right_wing':[('rotation',0,0,-45,-18),('rotation',2.0,0,-45,-18),('rotation',2.5,0,12,30,cm),('rotation',3.4,0,6,26,cm),Z(4.0)],
        'right_wing_tip':[('rotation',0,0,0,-45),('rotation',2.0,0,0,-45),('rotation',2.5,0,0,12,cm),Z(4.0)],
        'tail':[('rotation',0,40,0,0),('rotation',2.0,40,0,0),('rotation',2.6,-15,0,0,cm),Z(4.0)]}))
    L=3.0
    add('idle',L,'loop',mir({
        'chest':[('rotation',0,0,0,0,cm),('rotation',L/2,3,0,0,cm),('rotation',L,0,0,0,cm),('position',0,0,0,0,cm),('position',L/2,0,.6,0,cm),('position',L,0,0,0,cm)],
        'head':[('rotation',0,-3,-4,0,cm),('rotation',L/2,3,4,0,cm),('rotation',L,-3,-4,0,cm)],
        'jaw':[Z(0),('rotation',L/2,-6,0,0,cm),Z(L)],
        'right_wing':[Z(0),('rotation',L/2,0,-5,8,cm),Z(L)],
        'right_wing_tip':[Z(0),('rotation',L/2,0,0,7,cm),Z(L)],
        'right_arm':[Z(0),('rotation',L/2,-3,0,3,cm),Z(L)],
        'right_hand':[Z(0),('rotation',L/2,10,0,0,cm),Z(L)],
        'right_chain':[('rotation',0,4,0,0,cm),('rotation',L/2,-4,0,0,cm),('rotation',L,4,0,0,cm)],
        'tail':[('rotation',0,0,-10,0,cm),('rotation',L/2,0,10,0,cm),('rotation',L,0,-10,0,cm)],
        'tail2':[('rotation',0,0,8,0,cm),('rotation',L/2,0,-8,0,cm),('rotation',L,0,8,0,cm)],
        'tail3':[('rotation',0,0,10,0,cm),('rotation',L/2,0,-10,0,cm),('rotation',L,0,10,0,cm)],
        'tail4':[('rotation',0,0,12,0,cm),('rotation',L/2,0,-12,0,cm),('rotation',L,0,12,0,cm)],
        'loin_front':[Z(0),('rotation',L/2,4,0,0,cm),Z(L)],
        'loin_back':[Z(0),('rotation',L/2,-4,0,0,cm),Z(L)]}))
    L=1.6
    add('walk',L,'loop',{
        'right_leg':sw(24,L),'left_leg':sw(-24,L),'right_arm':sw(-16,L),'left_arm':sw(16,L),
        'right_shin':[Z(0),('rotation',L/2,0,0,0,cm),('rotation',L*.75,-34,0,0,cm),Z(L)],
        'left_shin':[Z(0),('rotation',L*.25,-34,0,0,cm),('rotation',L/2,0,0,0,cm),Z(L)],
        'body':[('position',0,0,0,0,cm),('position',L*.25,0,-1,0,cm),('position',L/2,0,0,0,cm),('position',L*.75,0,-1,0,cm),('position',L,0,0,0,cm),
                ('rotation',0,0,5,0,cm),('rotation',L/2,0,-5,0,cm),('rotation',L,0,5,0,cm)],
        'chest':[('rotation',0,-6,0,0,cm),('rotation',L,-6,0,0,cm)],
        'head':[('rotation',0,4,0,0,cm),('rotation',L*.25,6,0,0,cm),('rotation',L/2,4,0,0,cm),('rotation',L*.75,6,0,0,cm),('rotation',L,4,0,0,cm)],
        'right_wing':[('rotation',0,0,-6,4,cm),('rotation',L/2,0,-6,-2,cm),('rotation',L,0,-6,4,cm)],
        'left_wing':[('rotation',0,0,6,-4,cm),('rotation',L/2,0,6,2,cm),('rotation',L,0,6,-4,cm)],
        'right_chain':[('rotation',0,-15,0,0,cm),('rotation',L/2,15,0,0,cm),('rotation',L,-15,0,0,cm)],
        'left_chain':[('rotation',0,15,0,0,cm),('rotation',L/2,-15,0,0,cm),('rotation',L,15,0,0,cm)],
        'tail':[('rotation',0,0,-12,0,cm),('rotation',L/2,0,12,0,cm),('rotation',L,0,-12,0,cm)],
        'tail2':[('rotation',0,0,10,0,cm),('rotation',L/2,0,-10,0,cm),('rotation',L,0,10,0,cm)],
        'tail3':[('rotation',0,0,10,0,cm),('rotation',L/2,0,-10,0,cm),('rotation',L,0,10,0,cm)]})
    # 기본 공격: 오른손 크게 할퀴기
    add('attack',1.2,'once',{
        'right_arm':[Z(0),('rotation',.4,-40,0,50,cm),('rotation',.55,80,0,-20),('rotation',.8,72,0,-25,cm),Z(1.2)],
        'right_forearm':[Z(0),('rotation',.4,30,0,0,cm),('rotation',.55,0,0,0),Z(1.2)],
        'right_hand':[Z(0),('rotation',.4,-30,0,0,cm),('rotation',.55,25,0,0),Z(1.2)],
        'chest':[Z(0),('rotation',.4,4,-22,0,cm),('rotation',.55,-14,26,0),Z(1.2)],
        'jaw':[Z(0),('rotation',.5,-22,0,0,cm),Z(1.2)],
        'body':[('position',0,0,0,0),('position',.55,0,0,-3),('position',1.2,0,0,0,cm)]})
    # 쌍갈래 할퀴기: 오른손 → 반대 각도 왼손
    add('claw_combo',1.7,'once',{
        'right_arm':[Z(0),('rotation',.3,-40,0,50,cm),('rotation',.42,80,0,-20),('rotation',.8,30,0,0,cm),Z(1.7)],
        'left_arm':[Z(0),('rotation',.6,-60,0,-30,cm),('rotation',.9,95,0,20),('rotation',1.15,85,0,15,cm),Z(1.7)],
        'right_hand':[Z(0),('rotation',.3,-30,0,0,cm),('rotation',.42,25,0,0),Z(1.0)],
        'left_hand':[Z(0),('rotation',.6,-30,0,0,cm),('rotation',.9,25,0,0),Z(1.7)],
        'chest':[Z(0),('rotation',.3,4,-22,0,cm),('rotation',.42,-12,26,0),('rotation',.75,4,26,0,cm),('rotation',.9,-14,-26,0),Z(1.7)],
        'body':[('position',0,0,0,0),('position',.42,0,0,-3),('position',.9,0,0,-6),('position',1.7,0,0,0,cm)]})
    # 지연 내려찍기: 양손 높이 → 멈칫 → 내려찍기
    add('slam',1.9,'once',mir({
        'right_arm':[Z(0),('rotation',.5,165,0,15,cm),('rotation',1.0,170,0,12,cm),('rotation',1.12,45,0,5),('rotation',1.45,45,0,5),Z(1.9)],
        'right_hand':[Z(0),('rotation',.5,-20,0,0,cm),('rotation',1.12,30,0,0),Z(1.9)],
        'chest':[Z(0),('rotation',.5,15,0,0,cm),('rotation',1.0,18,0,0,cm),('rotation',1.12,-32,0,0),('rotation',1.45,-30,0,0),Z(1.9)],
        'head':[Z(0),('rotation',.5,18,0,0,cm),('rotation',1.12,-8,0,0),Z(1.9)],
        'jaw':[Z(0),('rotation',.5,-25,0,0,cm),('rotation',1.12,-8,0,0),Z(1.9)],
        'body':[('position',0,0,0,0),('position',.5,0,2,0,cm),('position',1.12,0,-6,0),('position',1.45,0,-6,0),('position',1.9,0,0,0,cm)],
        'right_leg':[Z(0),('rotation',1.12,22,0,0),('rotation',1.45,22,0,0),Z(1.9)],
        'right_shin':[Z(0),('rotation',1.12,-34,0,0),('rotation',1.45,-34,0,0),Z(1.9)],
        'right_wing':[Z(0),('rotation',.5,0,0,26,cm),('rotation',1.12,0,-20,-10),Z(1.9)]}))
    # 돌진 자세 (실제 이동은 게임)
    add('dash',1.0,'once',mir({
        'chest':[Z(0),('rotation',.2,-32,0,0,cm),('rotation',.75,-32,0,0),Z(1.0)],
        'head':[Z(0),('rotation',.2,22,0,0,cm),('rotation',.75,22,0,0),Z(1.0)],
        'right_arm':[Z(0),('rotation',.2,-50,0,15,cm),('rotation',.75,-50,0,15),Z(1.0)],
        'right_wing':[Z(0),('rotation',.2,0,-38,-6,cm),('rotation',.75,0,-38,-6),Z(1.0)],
        'right_leg':[Z(0),('rotation',.2,25,0,0,cm),('rotation',.75,25,0,0),Z(1.0)],
        'left_leg':[Z(0),('rotation',.2,-25,0,0,cm),('rotation',.75,-25,0,0),Z(1.0)],
        'tail':[Z(0),('rotation',.2,-25,0,0,cm),('rotation',.75,-25,0,0),Z(1.0)]}))
    # 기믹 시전 / 표식 보여주기
    add('cast',2.2,'once',mir({
        'right_arm':[Z(0),('rotation',.5,20,0,110,cm),('rotation',1.7,25,0,115,cm),Z(2.2)],
        'right_hand':[Z(0),('rotation',.5,-40,0,0,cm),('rotation',1.7,-40,0,0),Z(2.2)],
        'chest':[Z(0),('rotation',.5,16,0,0,cm),('rotation',1.7,16,0,0),Z(2.2)],
        'head':[Z(0),('rotation',.5,30,0,0,cm),('rotation',1.7,30,0,0),Z(2.2)],
        'jaw':[Z(0),('rotation',.5,-30,0,0,cm),('rotation',1.7,-30,0,0),Z(2.2)],
        'right_wing':[Z(0),('rotation',.5,0,12,30,cm),('rotation',1.7,0,12,30),Z(2.2)],
        'right_wing_tip':[Z(0),('rotation',.5,0,0,15,cm),('rotation',1.7,0,0,15),Z(2.2)]}))
    # 도발 (루프)
    L=2.0
    add('taunt',L,'loop',mir({
        'right_arm':[('rotation',0,-10,0,40,cm),('rotation',L/2,-14,0,48,cm),('rotation',L,-10,0,40,cm)],
        'right_hand':[Z(0),('rotation',L/2,-30,0,0,cm),Z(L)],
        'chest':[('rotation',0,10,0,0,cm),('rotation',L/2,12,0,0,cm),('rotation',L,10,0,0,cm)],
        'head':[('rotation',0,8,0,10,cm),('rotation',L/2,6,0,-10,cm),('rotation',L,8,0,10,cm)],
        'jaw':[('rotation',0,-8,0,0,cm),('rotation',L/2,-14,0,0,cm),('rotation',L,-8,0,0,cm)],
        'tail':[('rotation',0,-15,-15,0,cm),('rotation',L/2,-15,15,0,cm),('rotation',L,-15,-15,0,cm)]}))
    # 날개 폭풍
    add('wing_gust',1.5,'once',mir({
        'right_wing':[Z(0),('rotation',.55,0,55,-5,cm),('rotation',.72,0,-30,30),('rotation',1.0,0,-25,25,cm),Z(1.5)],
        'right_wing_tip':[Z(0),('rotation',.55,0,30,0,cm),('rotation',.72,0,0,15),Z(1.5)],
        'chest':[Z(0),('rotation',.55,-18,0,0,cm),('rotation',.72,12,0,0),Z(1.5)],
        'right_arm':[Z(0),('rotation',.55,30,0,-20,cm),('rotation',.72,-10,0,40),Z(1.5)]}))
    # 줄넘기 회전 (루프)
    L=1.2
    add('chain_spin',L,'loop',mir({
        'root':[('rotation',0,0,0,0),('rotation',L/2,0,-180,0),('rotation',L,0,-360,0)],
        'right_arm':[('rotation',0,85,0,80,cm),('rotation',L/2,80,0,84,cm),('rotation',L,85,0,80,cm)],
        'chest':[('rotation',0,6,0,0),('rotation',L,6,0,0)],
        'right_wing':[('rotation',0,0,10,20),('rotation',L,0,10,20)],
        'tail':[('rotation',0,-10,25,0),('rotation',L,-10,25,0)]}))
    # 가짜 사망: 비틀거리다 뒤로 쓰러짐
    add('fake_death',3.2,'hold',{
        'root':[Z(0),('rotation',1.5,0,0,0),('rotation',2.3,85,0,0,cm),('rotation',2.45,80,0,0),('rotation',2.6,85,0,0),
                ('position',0,0,0,0),('position',.6,0,0,3,cm),('position',1.5,0,0,5,cm),('position',2.3,0,3,5,cm)],
        'chest':[Z(0),('rotation',.4,20,10,0,cm),('rotation',1.0,-15,-10,0,cm),('rotation',1.5,-10,0,0,cm),('rotation',2.3,10,0,0,cm)],
        'head':[Z(0),('rotation',.4,30,0,0,cm),('rotation',1.5,-20,0,15,cm),('rotation',2.5,10,0,25,cm)],
        'jaw':[Z(0),('rotation',.4,-28,0,0,cm),('rotation',2.5,-14,0,0,cm)],
        'right_arm':[Z(0),('rotation',.4,-20,0,50,cm),('rotation',1.5,20,0,15,cm),('rotation',2.5,-30,0,70,cm)],
        'left_arm':[Z(0),('rotation',.4,-20,0,-50,cm),('rotation',1.5,40,0,-10,cm),('rotation',2.5,-30,0,-60,cm)],
        'right_wing':[Z(0),('rotation',1.5,0,10,-25,cm),('rotation',2.5,0,25,-40,cm)],
        'left_wing':[Z(0),('rotation',1.5,0,-10,25,cm),('rotation',2.5,0,-25,40,cm)],
        'right_leg':[Z(0),('rotation',1.0,15,0,0,cm),('rotation',2.5,-10,0,0,cm)],
        'left_shin':[Z(0),('rotation',1.0,-35,0,0,cm),('rotation',2.5,-10,0,0,cm)]})
    # 진짜 사망: 무릎 꿇고 앞으로 고꾸라지며 가라앉음
    add('death',4.2,'hold',mir({
        'body':[('position',0,0,0,0),('position',1.0,0,KNEEL,0,cm),('position',2.6,0,KNEEL,0)],
        'right_leg':[Z(0),('rotation',1.0,70,0,0,cm)],
        'right_shin':[Z(0),('rotation',1.0,-100,0,0,cm)],
        'right_foot':[Z(0),('rotation',1.0,30,0,0,cm)],
        'chest':[Z(0),('rotation',.5,25,0,0,cm),('rotation',1.4,-20,0,0,cm),('rotation',2.5,-55,0,0,cm)],
        'head':[Z(0),('rotation',.5,35,0,0,cm),('rotation',1.4,-20,0,0,cm),('rotation',2.5,-35,0,0,cm)],
        'jaw':[Z(0),('rotation',.5,-32,0,0,cm),('rotation',2.0,-12,0,0,cm)],
        'right_arm':[Z(0),('rotation',.5,-30,0,60,cm),('rotation',1.4,30,0,10,cm),('rotation',2.5,60,0,5,cm)],
        'right_wing':[Z(0),('rotation',1.4,0,20,-30,cm),('rotation',2.5,0,35,-50,cm)],
        'root':[('position',0,0,0,0),('position',2.5,0,0,0),('position',4.2,0,-34,0,cm)]}))
    return A

if __name__=='__main__':
    O='/mnt/user-data/outputs/'
    export('freed_demon', build(), anims, 7, O+'freed_demon.bbmodel', HITBOX)
    hide_groups(O+'freed_demon.bbmodel',['eyes_lie'])
    apply_palette(SHINY)
    export('freed_demon_shiny', build(), anims, 7, O+'freed_demon_shiny.bbmodel', HITBOX)
    hide_groups(O+'freed_demon_shiny.bbmodel',['eyes_lie'])
