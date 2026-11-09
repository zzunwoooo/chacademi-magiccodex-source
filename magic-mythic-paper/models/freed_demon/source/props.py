from common import *
cm='catmullrom'
O='/mnt/user-data/outputs/'
STONE=(104,98,112); DSTONE=(66,60,74); MOSS=(70,84,58); RUNE=(255,132,48); OBS=(34,26,40)
IRON=(82,78,88); DIRON=(46,42,52); WOOD=(78,40,30); DWOOD=(52,26,22); GOLD=(206,156,58)
FLESH=(150,40,56); TOOTH=(238,230,208); EYE=(255,60,40,255)
CH=(196,52,32); CH2=(120,24,18); CHG=(255,150,60)

def stone(base=None, crack=0.03):
    b=base or STONE
    def p(img, face, rect, rng):
        solid(b,0.1)(img,face,rect,rng)
        x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
        for _ in range(int(w*h*crack)):
            x=rng.randrange(w); y=rng.randrange(h)
            for s in range(rng.randint(2,5)):
                if 0<=x<w and 0<=y<h: img.putpixel((x1+x,y1+y), col(DSTONE,.7))
                x+=rng.choice([-1,0,1]); y+=rng.choice([0,1])
        if face=='up':
            for _ in range(int(w*h*0.04)):
                img.putpixel((x1+rng.randrange(w),y1+rng.randrange(h)), col(MOSS,rng.uniform(.8,1.1)))
    return p
def runes(base):
    def p(img, face, rect, rng):
        stone(base,0.01)(img,face,rect,rng)
        x1,y1,x2,y2=rect
        for y in range(y1+1,y2-1,2):
            for x in range(x1+1,x2-1):
                if rng.random()<.45: img.putpixel((x,y), col(DSTONE,.6))
    return p

# ---------------------------------------------------------------- 진실의 석상
def statue():
    def plaque(img,r,rng):
        x1,y1,x2,y2=r
        for y in range(y1,y2):
            for x in range(x1,x2):
                e = x in(x1,x2-1) or y in(y1,y2-1)
                img.putpixel((x,y), col(DSTONE if e else (126,120,134), rng.uniform(.9,1.05)))
    def hood_face(img,r,rng):      # 두건 속 어둠 + 빈 눈구멍
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1
        for y in range(1,h):
            for x in range(1,w-1): px(img,r,x,y,(20,16,24,255))
        px(img,r,2,3,(60,54,66,255)); px(img,r,w-3,3,(60,54,66,255))
    tablet=Group('tablet',[0,27,-5],[
        C('right_forearm',[3,25,-7],[5,27,-2],stone()),
        C('left_forearm',[-5,25,-7],[-3,27,-2],stone()),
        C('right_hand',[2,26,-8],[4,29,-7],stone()),
        C('left_hand',[-4,26,-8],[-2,29,-7],stone()),
        C('tablet',[-3,24,-8.5],[3,32,-7.5],with_faces(runes((118,112,126)),north=plaque)),
        C('tablet_top',[-2,32,-8.5],[2,33,-7.5],stone())])
    hood=Group('hood',[0,31,0],[
        C('hood',[-3.5,31,-4],[3.5,38,3],with_faces(stone(),north=hood_face)),
        C('hood_peak',[-2.5,38,-2],[2.5,39,2],stone()),
        C('right_horn1',[3,35,-2],[5,37,0],stone(DSTONE)),
        C('right_horn2',[4,36,-1],[6,40,1],stone(DSTONE)),
        C('right_horn3',[5,39,0],[6,42,2],stone(DSTONE)),
        C('left_horn1',[-5,35,-2],[-3,37,0],stone(DSTONE)),
        C('left_horn2',[-6,36,-1],[-4,40,1],stone(DSTONE)),
        C('left_horn3',[-6,39,0],[-5,42,2],stone(DSTONE))])
    figure=Group('figure',[0,16,0],[
        C('robe_low',[-5,16,-4],[5,24,4],stone()),
        C('robe_fold_front',[-4,16,-5],[4,19,-4],stone(DSTONE)),
        C('robe_up',[-4,24,-3.5],[4,31,3.5],stone()),
        C('right_shoulder',[4,27,-3],[6,31,2],stone()),
        C('left_shoulder',[-6,27,-3],[-4,31,2],stone()),
        C('right_wing',[2,18,3.5],[4,32,5.5],stone(DSTONE),rot=[0,-20,0],origin=[3,25,4.5]),
        C('left_wing',[-4,18,3.5],[-2,32,5.5],stone(DSTONE),rot=[0,20,0],origin=[-3,25,4.5]),
        C('right_wing_tip',[3,30,4],[5,35,6],stone(DSTONE),rot=[0,-20,-15],origin=[4,32,5]),
        C('left_wing_tip',[-5,30,4],[-3,35,6],stone(DSTONE),rot=[0,20,15],origin=[-4,32,5]),
        tablet, hood])
    ped=Group('pedestal',[0,0,0],[
        C('base',[-8,0,-8],[8,3,8],stone()),
        C('step',[-7,3,-7],[7,5,7],stone(DSTONE)),
        C('column',[-6,5,-6],[6,14,6],runes(STONE)),
        C('plaque',[-4,7,-7],[4,12,-6],with_faces(stone(),north=plaque)),
        C('cap',[-7,14,-7],[7,16,7],stone()),
        C('corner_fr',[5,5,-7],[7,14,-5],stone(DSTONE)),
        C('corner_fl',[-7,5,-7],[-5,14,-5],stone(DSTONE)),
        C('corner_br',[5,5,5],[7,14,7],stone(DSTONE)),
        C('corner_bl',[-7,5,5],[-5,14,7],stone(DSTONE))])
    return Group('root',[0,0,0],[ped, figure])
def statue_anims(Gs):
    n='animation.truth_statue.'
    return [
      anim(n+'reveal',2.0,'once',{     # 진실의 석상: 석판을 들어 올림
        'tablet':[('rotation',0,0,0,0),('rotation',1.0,-35,0,0,cm),('position',0,0,0,0),('position',1.0,0,4,0,cm),('rotation',2.0,-35,0,0),('position',2.0,0,4,0)],
        'hood':[('rotation',0,0,0,0),('rotation',1.0,15,0,0,cm),('rotation',2.0,15,0,0)]},Gs),
      anim(n+'crumble',2.0,'hold',{    # 거짓 석상: 머리·석판이 떨어짐
        'hood':[('rotation',0,0,0,0),('rotation',.15,0,0,6),('rotation',.3,0,0,-6),('rotation',.9,-60,0,35,cm),('position',0,0,0,0),('position',.9,6,-14,-6,cm)],
        'tablet':[('rotation',0,0,0,0),('rotation',.8,70,0,0,cm),('position',0,0,0,0),('position',.8,0,-10,-4,cm)],
        'figure':[('rotation',0,0,0,0),('rotation',.3,0,0,0),('rotation',1.2,8,0,-5,cm)]},Gs),
      anim(n+'idle',4.0,'loop',{'hood':[('rotation',0,0,0,0),('rotation',4.0,0,0,0)]},Gs)]

# ---------------------------------------------------------------- 지옥 사슬 (줄넘기)
def chain():
    kids=[]; x=0; i=0
    link=frame(CH,CH2); hot=veins(CH,CHG,0.15)
    while x+12<=192:
        p=hot if i%3==0 else link; nm=f'link{i:02d}'
        if i%2==0:   # 가로 고리 (xz 평면)
            kids+= [C(nm+'_a',[x,3,-3],[x+12,5,-1],p),C(nm+'_b',[x,3,1],[x+12,5,3],p),
                    C(nm+'_c',[x,3,-1],[x+2,5,1],p),C(nm+'_d',[x+10,3,-1],[x+12,5,1],p)]
        else:        # 세로 고리 (xy 평면)
            kids+= [C(nm+'_a',[x,6,-1],[x+12,8,1],p),C(nm+'_b',[x,0,-1],[x+12,2,1],p),
                    C(nm+'_c',[x,2,-1],[x+2,6,1],p),C(nm+'_d',[x+10,2,-1],[x+12,6,1],p)]
            if i%4==1: kids+=[C(nm+'_spike_u',[x+5,8,-1],[x+7,11,1],solid(CH2)),C(nm+'_spike_d',[x+5,-3,-1],[x+7,0,1],solid(CH2))]
        x+=9; i+=1
    kids+=[C('end_ring',[x+1,1,-3],[x+3,7,3],frame(CH2,CH)),C('end_hook',[x+3,3,-1],[x+7,5,1],solid(CH2)),
           C('end_hook_tip',[x+5,5,-1],[x+7,8,1],solid(CH2))]
    return Group('root',[0,4,0],[Group('chain',[0,4,0],kids)])
def chain_anims(name):
    def f(Gs):
        n=f'animation.{name}.'; L=0.8
        return [anim(n+'idle',L,'loop',{'chain':[('rotation',0,0,0,2,cm),('rotation',L/2,0,0,-2,cm),('rotation',L,0,0,2,cm)]},Gs)]
    return f

# ---------------------------------------------------------------- 미믹 보상 상자
def mimic():
    def lockface(img,r,rng):
        x1,y1,x2,y2=r; w=x2-x1
        px(img,r,1,1,col(EYE)); px(img,r,1,2,(20,8,10,255))
    teeth_low=[C(f'tooth_low{i}',[-6+i*3,8,-5.5],[-5+i*3,10,-4.5],solid(TOOTH)) for i in range(5)]
    teeth_up=[C(f'tooth_up{i}',[-4.5+i*3,6,-5.5],[-3.5+i*3,8,-4.5],solid(TOOTH)) for i in range(4)]
    lid=Group('lid',[0,8,6],[
        C('lid',[-7,8,-6],[7,12,6],frame(WOOD,DWOOD)),
        C('lid_top',[-6,12,-5],[6,13,5],solid(DWOOD)),
        C('lid_band_r',[2,7.5,-6.5],[4,13.5,6.5],frame(IRON,DIRON)) if False else C('lid_band_r',[2,8,-7],[4,13,7],frame(IRON,DIRON)),
        C('lid_band_l',[-4,8,-7],[-2,13,7],frame(IRON,DIRON)),
        C('lock',[-1.5,7,-8],[1.5,11,-7],with_faces(solid(GOLD),north=lockface)),
        C('lid_horn_r',[5,13,-3],[6,15,-2],solid(OBS),rot=[-20,0,-20]),
        C('lid_horn_l',[-6,13,-3],[-5,15,-2],solid(OBS),rot=[-20,0,20]),
        C('lid_inner',[-6,7,-5],[6,8,5],solid(FLESH))]+teeth_up)
    tongue=Group('tongue',[0,8,3],[C('tongue',[-2,7,-4],[2,8,3],solid(FLESH)),C('tongue_tip',[-1.5,7,-6],[1.5,8,-4],solid((180,60,76)))])
    base=Group('base',[0,0,0],[
        C('box',[-7,0,-6],[7,8,6],frame(WOOD,DWOOD)),
        C('inner',[-6,7,-5],[6,8,5],solid((60,10,20))),
        C('corner_fr',[6,0,-7],[8,8,-5],solid(IRON)),
        C('corner_fl',[-8,0,-7],[-6,8,-5],solid(IRON)),
        C('corner_br',[6,0,5],[8,8,7],solid(IRON)),
        C('corner_bl',[-8,0,5],[-6,8,7],solid(IRON)),
        C('base_band',[-7,1,-7],[7,2,-6],solid(GOLD)),
        C('base_band_back',[-7,1,6],[7,2,7],solid(GOLD))]+teeth_low)
    return Group('root',[0,0,0],[base, tongue, lid])
def mimic_anims(Gs):
    n='animation.mimic_chest.'
    L=3.0
    return [
      anim(n+'idle',L,'loop',{'lid':[('rotation',0,0,0,0,cm),('rotation',L*.45,0,0,0,cm),('rotation',L*.5,3,0,0,cm),('rotation',L*.55,0,0,0,cm),('rotation',L,0,0,0,cm)]},Gs),
      anim(n+'open',1.2,'once',{   # 상자 연 순간: 입 벌리고 혀 내밀기
        'lid':[('rotation',0,0,0,0),('rotation',.15,8,0,0),('rotation',.5,75,0,0,cm),('rotation',1.2,70,0,0,cm)],
        'tongue':[('rotation',0,0,0,0),('rotation',.5,0,0,0),('rotation',.9,25,0,0,cm),('position',0,0,0,0),('position',.9,0,1,-4,cm),('rotation',1.2,20,0,0,cm),('position',1.2,0,1,-4)],
        'root':[('position',0,0,0,0),('position',.5,0,2,0,cm),('position',.8,0,0,0,cm)]},Gs),
      anim(n+'bite',0.8,'once',{   # 덥석 (열려 있는 상태에서 시작)
        'lid':[('rotation',0,70,0,0),('rotation',.2,85,0,0,cm),('rotation',.32,0,0,0),('rotation',.8,0,0,0)],
        'tongue':[('position',0,0,1,-4),('position',.3,0,0,0),('rotation',0,20,0,0),('rotation',.3,0,0,0)],
        'root':[('position',0,0,0,0),('position',.32,0,1,-5),('position',.8,0,0,0,cm),('rotation',0,0,0,0),('rotation',.32,-12,0,0),('rotation',.8,0,0,0,cm)]},Gs)]

# ---------------------------------------------------------------- 중앙 제단
def altar():
    def seal(img,r,rng):   # 금 간 봉인 문양
        x1,y1,x2,y2=r; w,h=x2-x1,y2-y1; cx,cy=(w-1)/2,(h-1)/2
        import math
        for ly in range(h):
            for lx in range(w):
                d=math.hypot(lx-cx,ly-cy); a=math.atan2(ly-cy,lx-cx)
                if abs(d-w*0.42)<.7 or abs(d-w*0.26)<.6 or (d<w*0.42 and abs(math.sin(a*2.5))<.12):
                    px(img,r,lx,ly,col(RUNE,rng.uniform(.8,1.0)))
        x,y=int(cx),0
        while y<h:
            px(img,r,x,y,(14,10,16,255)); x+=rng.choice([-1,0,1]); y+=1
    kids=[C('tier1',[-24,0,-24],[24,2,24],stone()),
          C('tier2',[-20,2,-20],[20,4,20],stone(DSTONE)),
          C('tier3',[-15,4,-15],[15,6,15],runes(STONE)),
          C('dais',[-10,6,-10],[10,8,10],stone(DSTONE)),
          C('seal_stone',[-8,8,-8],[8,9,8],with_faces(stone(OBS,0),up=seal))]
    heights=[(1,1,12),(1,-1,7),(-1,1,9),(-1,-1,14)]
    for sx,sz,hgt in heights:
        nm=f'pillar_{"r" if sx>0 else "l"}{"b" if sz>0 else "f"}'
        x0=18 if sx>0 else -23; z0=18 if sz>0 else -23
        kids+=[C(nm,[x0,2,z0],[x0+5,2+hgt,z0+5],runes(STONE)),
               C(nm+'_base',[x0-1,2,z0-1],[x0+6,4,z0+6],stone(DSTONE)),
               C(nm+'_broken',[x0+1,2+hgt,z0+1],[x0+3,4+hgt,z0+3],stone()),
               C(nm+'_shackle',[x0-0.5,hgt-2,z0-0.5],[x0+5.5,hgt,z0+5.5],veins(IRON,RUNE,0.05))]
        # 끊어진 사슬: 기둥에서 중앙 쪽으로 늘어짐
        ox=x0+2.5; oz=z0+2.5
        for k in range(3):
            px_=ox-sx*(3+k*3); pz=oz-sz*(3+k*3); py=hgt-3-k*3 if hgt-3-k*3>4 else 4+(2-k)
            kids.append(C(f'{nm}_chain{k}',[px_-1,py,pz-1],[px_+1,py+3,pz+1],frame(DIRON,IRON),rot=[0,45,0]))
    kids+=[C('broken_stake1',[11,6,-4],[13,10,-2],stone(OBS),rot=[20,0,15]),
           C('broken_stake2',[-13,6,3],[-11,9,5],stone(OBS),rot=[-15,0,-20]),
           C('rubble1',[13,4,9],[16,6,12],stone()),
           C('rubble2',[-16,4,-12],[-13,5,-9],stone(DSTONE))]
    return Group('root',[0,0,0],kids)

if __name__=='__main__':
    export('truth_statue', statue(), statue_anims, 21, O+'truth_statue.bbmodel', (1.0,2.6,2.3))
    export('hell_chain_red', chain(), chain_anims('hell_chain_red'), 22, O+'hell_chain_red.bbmodel')
    CH,CH2,CHG=(34,28,40),(14,10,18),(170,90,255)
    export('hell_chain_black', chain(), chain_anims('hell_chain_black'), 22, O+'hell_chain_black.bbmodel')
    export('mimic_chest', mimic(), mimic_anims, 23, O+'mimic_chest.bbmodel', (0.9,0.85,0.6))
    export('demon_altar', altar(), lambda Gs:[], 24, O+'demon_altar.bbmodel')
