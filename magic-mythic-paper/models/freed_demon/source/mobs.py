from common import *
import sys
cm='catmullrom'
def sw(a,L): return [('rotation',0,a,0,0,cm),('rotation',L/2,-a,0,0,cm),('rotation',L,a,0,0,cm)]
def biped_walk(L,leg,arm,lift,bob,extra=None):
    t={'right_leg':sw(leg,L),'left_leg':sw(-leg,L),'right_arm':sw(-arm,L),'left_arm':sw(arm,L),
       'right_shin':[('rotation',0,0,0,0,cm),('rotation',L/2,0,0,0,cm),('rotation',L*.75,-lift,0,0,cm),('rotation',L,0,0,0,cm)],
       'left_shin':[('rotation',0,0,0,0,cm),('rotation',L*.25,-lift,0,0,cm),('rotation',L/2,0,0,0,cm),('rotation',L,0,0,0,cm)],
       'body':[('position',0,0,0,0,cm),('position',L*.25,0,-bob,0,cm),('position',L/2,0,0,0,cm),('position',L*.75,0,-bob,0,cm),('position',L,0,0,0,cm)]}
    t.update(extra or {}); return t
def loopk(bone, ch, vals, L):
    n=len(vals); return [(ch,L*i/(n-1),*v,cm) for i,v in enumerate(vals)]

# =====================================================================
# 속삭이는 임프
# =====================================================================
class Imp:
    id='whisper_imp'
    P=dict(SKIN=(104,58,120), DSKIN=(66,34,82), BELLY=(150,104,150), HORN=(46,36,40), EYE=(200,255,90,255),
           CLAW=(228,218,196), TOOTH=(240,234,214), CLOTH=(60,40,30), GOLD=(206,160,64), MEMB=(84,40,96,235))
    SHINY=dict(SKIN=(214,176,96), DSKIN=(156,116,52), BELLY=(240,214,150), HORN=(70,40,30), EYE=(255,70,60,255),
           CLAW=(250,246,232), TOOTH=(255,252,240), CLOTH=(110,30,40), GOLD=(120,220,255), MEMB=(196,140,60,235))
    HITBOX=(0.6,1.05,0.88)
    def build(s):
        g=globals()
        def face(img,r,rng):   # 6x5 앞면: 큰 웃음
            for lx in range(1,5): px(img,r,lx,3,(30,10,20,255))
            for lx in (1,4): px(img,r,lx,2,(30,10,20,255))
            px(img,r,2,4,col(g['TOOTH'])); px(img,r,3,4,col(g['TOOTH']))
        def ear(side):
            m=side=='left'
            return G(f'{side}_ear',[3,15,-1.5],[
                C(f'{side}_ear',[3,14,-2],[7,16,-1],solid(g['SKIN']),m),
                C(f'{side}_ear_tip',[6,15,-2],[9,16,-1],solid(g['DSKIN']),m),
                C(f'{side}_earring',[4,13,-2],[5,14,-1],solid(g['GOLD']),m)],[0,0,18],mirror=m)
        head=Group('head',[0,12,-1],[
            C('head',[-3,12,-4],[3,17,1],with_faces(solid(g['SKIN']),north=face)),
            C('nose',[-1,13,-5],[1,15,-4],solid(g['DSKIN'])),
            C('brow',[-3,16,-4.5],[3,17,-3.5],solid(g['DSKIN'])),
            C('right_eye',[0.5,14,-4.1],[2.5,15,-4.1],glow(g['EYE'])),
            C('left_eye',[-2.5,14,-4.1],[-0.5,15,-4.1],glow(g['EYE'])),
            C('right_horn',[1.5,17,-3],[2.5,19,-2],solid(g['HORN']),rot=[-20,0,-15]),
            C('left_horn',[-2.5,17,-3],[-1.5,19,-2],solid(g['HORN']),rot=[-20,0,15]),
            C('chin',[-1.5,11,-4],[1.5,12,-2],solid(g['DSKIN'])),
            ear('right'), ear('left')])
        def arm(side):
            m=side=='left'
            hand=G(f'{side}_hand',[3.5,5,0],[
                C(f'{side}_hand',[2.5,4,-1],[4.5,5,1],solid(g['DSKIN']),m),
                C(f'{side}_claw1',[2.5,2,-1],[3.5,4,0],solid(g['CLAW']),m),
                C(f'{side}_claw2',[3.5,2,0],[4.5,4,1],solid(g['CLAW']),m)],mirror=m)
            fore=G(f'{side}_forearm',[3.5,8,0],[
                C(f'{side}_forearm',[2.5,5,-1],[4.5,8,1],solid(g['SKIN']),m),
                C(f'{side}_bracelet',[2.5,6,-1.5],[4.5,7,1.5],solid(g['GOLD']),m), hand],[15,0,0],mirror=m)
            return G(f'{side}_arm',[3.5,11.5,0],[C(f'{side}_upper_arm',[2.5,8,-1],[4.5,12,1],solid(g['SKIN']),m), fore],[0,0,8],mirror=m)
        def wing(side):
            m=side=='left'
            return G(f'{side}_wing',[1,11,2],[
                C(f'{side}_wing_bone',[1,11,2],[6,12,3],solid(g['DSKIN']),m),
                C(f'{side}_wing_finger',[5,8,2],[6,11,3],solid(g['DSKIN']),m),
                C(f'{side}_wing_membrane',[1,7,2.5],[6,11,2.5],tatter(g['MEMB'],2),m)],[0,-30,20],mirror=m)
        tail=Group('tail',[0,7,1.5],[
            C('tail1',[-0.5,6.5,1.5],[0.5,7.5,6.5],solid(g['SKIN'])),
            Group('tail2',[0,7,6.5],[
                C('tail2',[-0.5,6.5,6.5],[0.5,7.5,10.5],solid(g['DSKIN'])),
                C('tail_spade',[-1,6.5,9.5],[1,7.5,11.5],solid(g['HORN']),rot=[0,45,0],origin=[0,7,10.5])],[-35,0,0])],[15,0,0])
        torso=Group('torso',[0,8,0],[
            C('torso',[-2.5,8,-2],[2.5,12,2],solid(g['SKIN'])),
            C('belly',[-1.5,8.5,-2.5],[1.5,11.5,-2],solid(g['BELLY'])) if False else C('belly',[-1.5,8,-3],[1.5,11,-2],solid(g['BELLY'])),
            C('hump',[-2,11,1],[2,13,3],solid(g['DSKIN'])),
            head, arm('right'), arm('left'), wing('right'), wing('left')],[-18,0,0])
        body=Group('body',[0,7,0],[
            C('pelvis',[-2,6,-1.5],[2,8,1.5],solid(g['DSKIN'])),
            C('belt',[-2.5,7,-2],[2.5,8,2],solid(g['CLOTH'])),
            C('pouch',[1.5,5,-2.5],[3.5,7,-1.5],solid(g['CLOTH'])),
            C('whisper_horn',[-3.5,5,-2],[-2.5,7,1],solid(g['GOLD']),rot=[0,0,20]),
            Group('loin',[0,7,-2.2],[C('loin',[-1.5,3,-2.2],[1.5,7,-2.2],tatter((*g['CLOTH'],255),2))]),
            torso, tail])
        def leg(side):
            m=side=='left'
            shin=G(f'{side}_shin',[1.5,4,0],[
                C(f'{side}_shin',[0.5,1,0],[2.5,4,2],solid(g['DSKIN']),m),
                C(f'{side}_foot',[0.5,0,-2],[2.5,1,1],solid(g['DSKIN']),m),
                C(f'{side}_toe_claw',[1,0,-3],[2,1,-2],solid(g['CLAW']),m)],mirror=m)
            return G(f'{side}_leg',[1.5,7,0],[C(f'{side}_thigh',[0.5,4,-1.5],[2.5,7,0.5],solid(g['SKIN']),m),
                C(f'{side}_knee_tuft',[0.5,4,-2],[2.5,5,-1],solid(g['DSKIN']),m), shin],mirror=m)
        return Group('root',[0,0,0],[body, leg('right'), leg('left')])
    def anims(s,Gs):
        n='animation.whisper_imp.'; A=[]
        A.append(anim(n+'spawn',1.4,'once',{
            'root':[('position',0,0,-10,0),('position',.5,0,-2,0,cm),('position',.75,0,3,0,cm),('position',1.0,0,0,0,cm),
                    ('scale',0,.6,.6,.6),('scale',.75,1.05,1.05,1.05,cm),('scale',1.0,1,1,1)],
            'torso':[('rotation',0,-45,0,0),('rotation',.75,0,0,0,cm),('rotation',1.4,0,0,0)],
            'right_ear':[('rotation',0,0,0,-30),('rotation',.9,0,0,15,cm),('rotation',1.4,0,0,0,cm)],
            'left_ear':[('rotation',0,0,0,30),('rotation',.9,0,0,-15,cm),('rotation',1.4,0,0,0,cm)],
            'right_arm':[('rotation',0,0,0,0),('rotation',.75,-20,0,60,cm),('rotation',1.4,0,0,0,cm)],
            'left_arm':[('rotation',0,0,0,0),('rotation',.75,-20,0,-60,cm),('rotation',1.4,0,0,0,cm)]},Gs))
        L=1.6
        A.append(anim(n+'idle',L,'loop',{
            'torso':[('rotation',0,0,0,0,cm),('rotation',L/2,4,0,0,cm),('rotation',L,0,0,0,cm)],
            'head':[('rotation',0,0,-10,0,cm),('rotation',L/2,0,10,4,cm),('rotation',L,0,-10,0,cm)],
            'right_ear':[('rotation',0,0,0,0,cm),('rotation',L*.3,0,0,12,cm),('rotation',L*.45,0,0,0,cm),('rotation',L,0,0,0,cm)],
            'left_ear':[('rotation',0,0,0,0,cm),('rotation',L*.6,0,0,-12,cm),('rotation',L*.75,0,0,0,cm),('rotation',L,0,0,0,cm)],
            'right_arm':[('rotation',0,0,0,0,cm),('rotation',L/2,20,0,0,cm),('rotation',L,0,0,0,cm)],   # 손을 입가로
            'right_forearm':[('rotation',0,0,0,0,cm),('rotation',L/2,40,0,0,cm),('rotation',L,0,0,0,cm)],
            'right_wing':[('rotation',0,0,0,0,cm),('rotation',L/4,0,0,25,cm),('rotation',L/2,0,0,0,cm),('rotation',L*.75,0,0,25,cm),('rotation',L,0,0,0,cm)],
            'left_wing':[('rotation',0,0,0,0,cm),('rotation',L/4,0,0,-25,cm),('rotation',L/2,0,0,0,cm),('rotation',L*.75,0,0,-25,cm),('rotation',L,0,0,0,cm)],
            'tail':[('rotation',0,0,-20,0,cm),('rotation',L/2,0,20,0,cm),('rotation',L,0,-20,0,cm)],
            'tail2':[('rotation',0,0,15,0,cm),('rotation',L/2,0,-15,0,cm),('rotation',L,0,15,0,cm)]},Gs))
        L=0.6
        A.append(anim(n+'walk',L,'loop',biped_walk(L,32,24,40,0.5,{
            'tail':[('rotation',0,0,-18,0,cm),('rotation',L/2,0,18,0,cm),('rotation',L,0,-18,0,cm)],
            'right_ear':[('rotation',0,0,0,8,cm),('rotation',L/2,0,0,-4,cm),('rotation',L,0,0,8,cm)],
            'left_ear':[('rotation',0,0,0,-8,cm),('rotation',L/2,0,0,4,cm),('rotation',L,0,0,-8,cm)],
            'right_wing':[('rotation',0,0,0,0,cm),('rotation',L/2,0,0,20,cm),('rotation',L,0,0,0,cm)],
            'left_wing':[('rotation',0,0,0,0,cm),('rotation',L/2,0,0,-20,cm),('rotation',L,0,0,0,cm)]}),Gs))
        A.append(anim(n+'attack',0.8,'once',{   # 양손 밀치기
            'right_arm':[('rotation',0,0,0,0),('rotation',.25,-40,0,20,cm),('rotation',.38,85,0,-5),('rotation',.8,0,0,0,cm)],
            'left_arm':[('rotation',0,0,0,0),('rotation',.25,-40,0,-20,cm),('rotation',.38,85,0,5),('rotation',.8,0,0,0,cm)],
            'right_forearm':[('rotation',0,0,0,0),('rotation',.25,40,0,0,cm),('rotation',.38,-10,0,0),('rotation',.8,0,0,0,cm)],
            'left_forearm':[('rotation',0,0,0,0),('rotation',.25,40,0,0,cm),('rotation',.38,-10,0,0),('rotation',.8,0,0,0,cm)],
            'torso':[('rotation',0,0,0,0),('rotation',.25,10,0,0,cm),('rotation',.38,-25,0,0),('rotation',.8,0,0,0,cm)],
            'body':[('position',0,0,0,0),('position',.38,0,0,-2),('position',.8,0,0,0,cm)]},Gs))
        A.append(anim(n+'death',1.2,'hold',{
            'root':[('rotation',0,0,0,0),('rotation',.6,85,0,0,cm),('position',0,0,0,0),('position',.3,0,2,0,cm),('position',.6,0,1,2,cm)],
            'right_ear':[('rotation',0,0,0,0),('rotation',.8,0,0,-40,cm)],
            'left_ear':[('rotation',0,0,0,0),('rotation',.8,0,0,40,cm)],
            'right_arm':[('rotation',0,0,0,0),('rotation',.6,-20,0,60,cm)],
            'left_arm':[('rotation',0,0,0,0),('rotation',.6,-20,0,-60,cm)],
            'tail':[('rotation',0,0,0,0),('rotation',.8,40,0,0,cm)]},Gs))
        return A

# =====================================================================
# 계약의 집행자
# =====================================================================
class Enforcer:
    id='contract_enforcer'
    P=dict(IRON=(58,54,64), DIRON=(32,30,38), EDGE=(110,104,118), TABARD=(110,22,30), DTAB=(66,12,18), SIGIL=(255,140,50),
           LEATHER=(60,40,32), PAPER=(214,196,156), HORN=(40,32,36), SKIN=(90,26,34))
    SHINY=dict(IRON=(206,196,170), DIRON=(150,136,104), EDGE=(250,236,190), TABARD=(30,44,96), DTAB=(18,26,60), SIGIL=(110,220,255),
           LEATHER=(90,60,40), PAPER=(230,220,190), HORN=(230,214,170), SKIN=(60,60,90))
    HITBOX=(1.0,2.75,2.25)
    def build(s):
        g=globals()
        def sig(img,r,rng):      # 문장(계약 인장) 무늬
            x1,y1,x2,y2=r; w,h=x2-x1,y2-y1; cx,cy=w//2,h//2
            for lx in range(w):
                for ly in range(h):
                    d=abs(lx-cx+.5)+abs(ly-cy+.5)
                    if abs(d-min(w,h)/2.6)<0.8 or (lx==cx and abs(ly-cy)<h/3): px(img,r,lx,ly,col(g['SIGIL']))
        helm=Group('head',[0,33,-0.5],[
            C('helm',[-4,33,-4.5],[4,41,3.5],frame(g['IRON'],g['EDGE'])),
            C('faceplate',[-3.5,34,-5.5],[3.5,39,-4.5],solid(g['DIRON'])),
            C('visor_slit',[-2.5,37,-5.6],[2.5,38,-5.6],glow(g['SIGIL'])),
            C('helm_crest',[-0.5,41,-4],[0.5,43,3],solid(g['EDGE'])),
            C('right_horn1',[4,37,-2],[6,39,0],solid(g['HORN'])),
            C('right_horn2',[5,38,-4],[7,40,-2],solid(g['HORN'])),
            C('right_horn3',[5.5,39,-6],[6.5,41,-4],solid(g['HORN'])),
            C('left_horn1',[-6,37,-2],[-4,39,0],solid(g['HORN'])),
            C('left_horn2',[-7,38,-4],[-5,40,-2],solid(g['HORN'])),
            C('left_horn3',[-6.5,39,-6],[-5.5,41,-4],solid(g['HORN'])),
            C('hood_back',[-4.5,32,3],[4.5,40,4],solid(g['DTAB']))])
        def arm(side):
            m=side=='left'
            hand_kids=[C(f'{side}_hand',[7,11,-2.5],[11,14,2.5],solid(g['DIRON']),m),
                       C(f'{side}_finger_claw',[7,9,-2.5],[8,11,2.5],solid(g['EDGE']),m)]
            if side=='right':
                brand=Group('brand',[9,12.5,0],[
                    C('brand_shaft',[8.5,8,-0.5],[9.5,24,0.5],solid(g['LEATHER'])),
                    C('brand_grip',[8,10,-1],[10,15,1],stripes(g['LEATHER'],g['DIRON'],2)),
                    C('brand_pommel',[8,7,-1],[10,9,1],solid(g['EDGE'])),
                    C('brand_neck',[8,24,-1],[10,26,1],solid(g['IRON'])),
                    C('brand_head',[6.5,26,-2.5],[11.5,31,2.5],with_faces(frame(g['DIRON'],g['EDGE']),up=sig)),
                    C('brand_flange_r',[11.5,27,-1],[12.5,30,1],solid(g['EDGE'])),
                    C('brand_flange_l',[5.5,27,-1],[6.5,30,1],solid(g['EDGE']))],[-30,0,0])
                hand_kids.append(brand)
            hand=G(f'{side}_hand',[9,13,0],hand_kids,mirror=m)
            fore=G(f'{side}_forearm',[9,22,0],[
                C(f'{side}_gauntlet',[6.5,14,-3],[11.5,22,3],frame(g['IRON'],g['EDGE']),m),
                C(f'{side}_gauntlet_spike',[11.5,17,-0.5],[12.5,20,0.5],solid(g['EDGE']),m), hand],[30 if side=='right' else 10,0,0],mirror=m)
            return G(f'{side}_arm',[9,30,0],[
                C(f'{side}_pauldron',[6,29,-4.5],[12,34,4.5],frame(g['IRON'],g['EDGE']),m),
                C(f'{side}_pauldron_spike1',[9,34,-2],[10,37,-1],solid(g['EDGE']),m),
                C(f'{side}_pauldron_spike2',[10.5,33,1],[11.5,36,2],solid(g['EDGE']),m),
                C(f'{side}_upper_arm',[7,22,-2.5],[11,30,2.5],solid(g['DIRON']),m), fore],[0,0,5],mirror=m)
        ledger=Group('ledger',[-6.5,20,0],[       # 사슬에 매단 계약서 장부
            C('ledger_chain1',[-7,18,-0.5],[-6,20,0.5],solid(g['EDGE'])),
            C('ledger_chain2',[-7,16,-0.5],[-6,18,0.5],solid(g['DIRON'])),
            C('ledger_cover',[-8,10,-2.5],[-6,16,2.5],frame(g['DTAB'],g['EDGE'])),
            C('ledger_pages',[-8.5,10.5,-2],[-7.5,15.5,2],stripes(g['PAPER'],(150,130,100),2)) if False else C('ledger_pages',[-9,11,-2],[-8,15,2],stripes(g['PAPER'],(150,130,100),2)),
            C('ledger_clasp',[-8.5,12,-3],[-6.5,14,-2.5],solid(g['SIGIL'])) if False else C('ledger_clasp',[-8,12,-3],[-6,14,-2],with_faces(solid(g['EDGE']),north=sig))])
        torso=Group('torso',[0,21,0],[
            C('breastplate',[-7,21,-4],[7,32,4],frame(g['IRON'],g['EDGE'])),
            C('plate_ridge',[-5,23,-5],[5,31,-4],with_faces(solid(g['DIRON']),north=sig)),
            C('back_plate',[-6,22,4],[6,31,5],solid(g['DIRON'])),
            C('gorget',[-4,31,-3.5],[4,33,3.5],solid(g['EDGE'])),
            helm, arm('right'), arm('left'),
            Group('cape',[0,32,5.2],[C('cape',[-6,5,5.2],[6,32,5.2],tatter((*g['DTAB'],255),4))])])
        body=Group('body',[0,18,0],[
            C('faulds',[-6,17,-3.5],[6,21,3.5],stripes(g['IRON'],g['DIRON'],2)),
            C('belt',[-6.5,19,-4],[6.5,21,4],solid(g['LEATHER'])),
            C('belt_seal',[-1.5,18,-5],[1.5,21,-4],with_faces(solid(g['EDGE']),north=sig)),
            Group('tabard',[0,19,-4.2],[C('tabard',[-4,6,-4.2],[4,19,-4.2],tatter((*g['TABARD'],255),3))]),
            ledger, torso])
        def leg(side):
            m=side=='left'
            shin=G(f'{side}_shin',[3.5,11,0],[
                C(f'{side}_greave',[1,3,-2.5],[6,11,2.5],frame(g['IRON'],g['EDGE']),m),
                C(f'{side}_knee',[0.5,9,-3.5],[6.5,12,-2.5],solid(g['EDGE']),m),
                C(f'{side}_boot',[0.5,0,-3.5],[6.5,3,2.5],solid(g['DIRON']),m),
                C(f'{side}_boot_spike',[3,0,-4.5],[4,2,-3.5],solid(g['EDGE']),m)],mirror=m)
            return G(f'{side}_leg',[3.5,18,0],[C(f'{side}_thigh',[1,11,-2.5],[6,18,2.5],solid(g['DIRON']),m), shin],mirror=m)
        return Group('root',[0,0,0],[body, leg('right'), leg('left')])
    def anims(s,Gs):
        n='animation.contract_enforcer.'; A=[]
        A.append(anim(n+'spawn',2.4,'once',{
            'root':[('position',0,0,-30,0),('position',1.4,0,0,0,cm),('position',2.4,0,0,0)],
            'torso':[('rotation',0,-20,0,0),('rotation',1.4,-15,0,0,cm),('rotation',1.9,8,0,0,cm),('rotation',2.4,0,0,0,cm)],
            'head':[('rotation',0,-25,0,0),('rotation',1.4,-20,0,0),('rotation',1.9,10,0,0,cm),('rotation',2.4,0,0,0,cm)],
            'right_arm':[('rotation',0,0,0,0),('rotation',1.4,0,0,0),('rotation',1.9,-30,0,10,cm),('rotation',2.4,0,0,0,cm)]},Gs))
        L=3.0
        A.append(anim(n+'idle',L,'loop',{
            'torso':[('rotation',0,0,0,0,cm),('rotation',L/2,2,0,0,cm),('rotation',L,0,0,0,cm),('position',0,0,0,0,cm),('position',L/2,0,.3,0,cm),('position',L,0,0,0,cm)],
            'head':[('rotation',0,0,-6,0,cm),('rotation',L/2,0,6,0,cm),('rotation',L,0,-6,0,cm)],
            'ledger':[('rotation',0,0,0,4,cm),('rotation',L/2,0,0,-4,cm),('rotation',L,0,0,4,cm)],
            'cape':[('rotation',0,-4,0,0,cm),('rotation',L/2,-8,0,0,cm),('rotation',L,-4,0,0,cm)],
            'tabard':[('rotation',0,0,0,0,cm),('rotation',L/2,4,0,0,cm),('rotation',L,0,0,0,cm)]},Gs))
        L=1.5
        A.append(anim(n+'walk',L,'loop',biped_walk(L,22,12,28,0.6,{
            'ledger':[('rotation',0,-12,0,0,cm),('rotation',L/2,12,0,0,cm),('rotation',L,-12,0,0,cm)],
            'cape':[('rotation',0,-10,0,0,cm),('rotation',L/2,-14,0,0,cm),('rotation',L,-10,0,0,cm)],
            'tabard':[('rotation',0,8,0,0,cm),('rotation',L/2,-8,0,0,cm),('rotation',L,8,0,0,cm)],
            'torso':[('rotation',0,0,4,0,cm),('rotation',L/2,0,-4,0,cm),('rotation',L,0,4,0,cm)]}),Gs))
        A.append(anim(n+'attack',1.6,'once',{   # 낙인 인두 머리 위로 → 내리찍기
            'right_arm':[('rotation',0,0,0,0),('rotation',.6,150,0,10,cm),('rotation',.85,155,0,10,cm),('rotation',1.0,45,0,0),('rotation',1.3,45,0,0),('rotation',1.6,0,0,0,cm)],
            'right_forearm':[('rotation',0,0,0,0),('rotation',.6,-20,0,0,cm),('rotation',1.0,-30,0,0),('rotation',1.6,0,0,0,cm)],
            'brand':[('rotation',0,0,0,0),('rotation',.6,60,0,0,cm),('rotation',1.0,40,0,0),('rotation',1.6,0,0,0,cm)],
            'torso':[('rotation',0,0,0,0),('rotation',.6,12,-10,0,cm),('rotation',1.0,-22,10,0),('rotation',1.3,-20,8,0),('rotation',1.6,0,0,0,cm)],
            'body':[('position',0,0,0,0),('position',1.0,0,-1.5,-1.5),('position',1.6,0,0,0,cm)],
            'head':[('rotation',0,0,0,0),('rotation',.6,10,0,0,cm),('rotation',1.0,-10,0,0),('rotation',1.6,0,0,0,cm)]},Gs))
        A.append(anim(n+'death',2.0,'hold',{
            'body':[('position',0,0,0,0),('position',.6,0,-6,0,cm)],
            'right_leg':[('rotation',0,0,0,0),('rotation',.6,80,0,0,cm)],
            'left_leg':[('rotation',0,0,0,0),('rotation',.6,80,0,0,cm)],
            'right_shin':[('rotation',0,0,0,0),('rotation',.6,-90,0,0,cm)],
            'left_shin':[('rotation',0,0,0,0),('rotation',.6,-90,0,0,cm)],
            'torso':[('rotation',0,0,0,0),('rotation',.6,-10,0,0,cm),('rotation',1.4,-60,0,0,cm)],
            'head':[('rotation',0,0,0,0),('rotation',1.4,-30,0,0,cm)],
            'right_arm':[('rotation',0,0,0,0),('rotation',1.0,40,0,30,cm)],
            'brand':[('rotation',0,0,0,0),('rotation',.8,70,0,0,cm),('position',0,0,0,0),('position',.8,4,-6,0,cm)],
            'left_arm':[('rotation',0,0,0,0),('rotation',1.0,40,0,-20,cm)]},Gs))
        return A

# =====================================================================
# 거짓 성배 (기본) / 진짜 성배 (색 바리에이션)
# =====================================================================
class Grail:
    id='false_grail'
    P=dict(GOLD=(206,156,58), DGOLD=(140,96,34), LIQ=(130,14,30,255), LIQ2=(70,6,16,255), GEM=(220,30,40,255), BONE=(60,44,40))
    SHINY=dict(GOLD=(214,170,70), DGOLD=(150,110,40), LIQ=(255,236,150,255), LIQ2=(255,206,90,255), GEM=(90,190,255,255), BONE=(240,230,210))
    SHINY_NAME='true_grail'
    HITBOX=(0.7,1.15,0.7)
    def build(s):
        g=globals()
        def liquid(img,r,rng):
            x1,y1,x2,y2=r
            for y in range(y1,y2):
                for x in range(x1,x2):
                    e = x in(x1,x2-1) or y in(y1,y2-1)
                    c = g['GOLD'] if e else (g['LIQ'] if rng.random()<.75 else g['LIQ2'])
                    img.putpixel((x,y), col(c, rng.uniform(.95,1.08)))
        cup=Group('cup',[0,8,0],[
            C('bowl_base',[-3,7,-3],[3,8,3],solid(g['DGOLD'])),
            C('bowl_mid',[-4,8,-4],[4,11,4],stripes(g['GOLD'],g['DGOLD'],3)),
            C('bowl_top',[-5,11,-5],[5,14,5],with_faces(frame(g['GOLD'],g['DGOLD']),up=liquid)),
            C('rim_front',[-5,14,-5.5],[5,15,-4.5],solid(g['GOLD'])),
            C('rim_back',[-5,14,4.5],[5,15,5.5],solid(g['GOLD'])),
            C('rim_right',[4.5,14,-5],[5.5,15,5],solid(g['GOLD'])),
            C('rim_left',[-5.5,14,-5],[-4.5,15,5],solid(g['GOLD'])),
            C('rim_spike_fr',[4,15,-5],[5,17,-4],solid(g['DGOLD'])),
            C('rim_spike_fl',[-5,15,-5],[-4,17,-4],solid(g['DGOLD'])),
            C('rim_spike_br',[4,15,4],[5,17,5],solid(g['DGOLD'])),
            C('rim_spike_bl',[-5,15,4],[-4,17,5],solid(g['DGOLD'])),
            C('gem_front',[-1,9,-5],[1,11,-4],glow(g['GEM'])),
            C('gem_back',[-1,9,4],[1,11,5],glow(g['GEM'])),
            C('gem_right',[4,9,-1],[5,11,1],glow(g['GEM'])),
            C('gem_left',[-5,9,-1],[-4,11,1],glow(g['GEM'])),
            C('right_handle_top',[5,11,-1],[7,12,1],solid(g['GOLD'])),
            C('right_handle_mid',[6,8,-1],[7,11,1],solid(g['GOLD'])),
            C('right_handle_low',[5,8,-1],[6,9,1],solid(g['GOLD'])),
            C('left_handle_top',[-7,11,-1],[-5,12,1],solid(g['GOLD'])),
            C('left_handle_mid',[-7,8,-1],[-6,11,1],solid(g['GOLD'])),
            C('left_handle_low',[-6,8,-1],[-5,9,1],solid(g['GOLD']))])
        stem=Group('stem',[0,2,0],[
            C('foot',[-4,0,-4],[4,1,4],frame(g['GOLD'],g['DGOLD'])),
            C('foot_step',[-3,1,-3],[3,2,3],solid(g['DGOLD'])),
            C('claw_fr',[3,0,-5],[4,1,-3],solid(g['BONE'])),
            C('claw_fl',[-4,0,-5],[-3,1,-3],solid(g['BONE'])),
            C('claw_br',[3,0,3],[4,1,5],solid(g['BONE'])),
            C('claw_bl',[-4,0,3],[-3,1,5],solid(g['BONE'])),
            C('stem',[-1,2,-1],[1,7,1],solid(g['GOLD'])),
            C('knot',[-2,4,-2],[2,6,2],frame(g['GOLD'],g['DGOLD'])),
            C('knot_gem',[-0.5,4.5,-2.5],[0.5,5.5,-2],glow(g['GEM'])) if False else C('knot_gem',[-0.5,4,-3],[0.5,5,-2],glow(g['GEM']))])
        root=Group('root',[0,0,0],[Group('body',[0,0,0],[stem,cup])])
        shift(root,3)
        return root
    def anims(s,Gs):
        n='animation.false_grail.'; A=[]
        A.append(anim(n+'spawn',1.5,'once',{
            'root':[('position',0,0,-14,0),('position',1.0,0,1,0,cm),('position',1.5,0,0,0,cm),
                    ('rotation',0,0,-180,0),('rotation',1.0,0,0,0,cm)]},Gs))
        L=3.0
        hov=[('position',0,0,0,0,cm),('position',L/2,0,1,0,cm),('position',L,0,0,0,cm),('rotation',0,0,0,0),('rotation',L,0,-360,0)]
        A.append(anim(n+'idle',L,'loop',{'body':hov},Gs))
        A.append(anim(n+'walk',L,'loop',{'body':hov},Gs))
        A.append(anim(n+'attack',3.0,'once',{     # 회복해주는 척 → 부르르 떨며 부풀어 폭발 (가짜 성배)
            'body':[('scale',0,1,1,1),('scale',1.0,1,1,1),('scale',2.6,1.35,1.35,1.35,cm),('scale',3.0,1.5,1.5,1.5),
                    ('rotation',0,0,0,0),('rotation',1.0,0,0,0),('rotation',1.6,0,0,6),('rotation',1.8,0,0,-6),('rotation',2.0,0,0,8),('rotation',2.2,0,0,-8),('rotation',2.4,0,0,10),('rotation',2.6,0,0,-10),('rotation',3.0,0,0,0)],
            'cup':[('rotation',0,0,0,0),('rotation',.6,-25,0,0,cm),('rotation',1.0,0,0,0,cm)]},Gs))
        A.append(anim(n+'pour',2.0,'once',{        # 진짜 성배: 기울여 붓기(회복)
            'body':[('rotation',0,0,0,0),('rotation',.6,-35,0,0,cm),('rotation',1.4,-35,0,0),('rotation',2.0,0,0,0,cm),
                    ('position',0,0,0,0),('position',.6,0,1.5,0,cm),('position',2.0,0,0,0,cm)]},Gs))
        A.append(anim(n+'death',1.4,'hold',{
            'root':[('position',0,0,0,0),('position',.5,0,-3,0,cm),('rotation',0,0,0,0),('rotation',.7,0,0,90,cm),('position',.7,3,-2,0,cm)]},Gs))
        return A

# =====================================================================
# 도망치는 그림자 (가짜 사망 후 본체)
# =====================================================================
class Shadow:
    id='fleeing_shadow'
    P=dict(DARK=(22,14,22,255), DARK2=(42,24,36,255), EYE=(255,40,40,255), BONE=(70,50,56))
    SHINY=dict(DARK=(226,222,236,255), DARK2=(180,172,200,255), EYE=(255,200,60,255), BONE=(250,240,220))
    HITBOX=(0.8,0.75,0.5)
    def build(s):
        g=globals()
        dk=lambda: tatter(g['DARK'],2,0.02,0.15)
        def leg(side,front):
            m=side=='left'; z=-3 if front else 3
            nm=f'{side}_{"front" if front else "hind"}'
            low=G(f'{nm}_forearm' if front else f'{nm}_shin',[6.5,6.5,z],[
                C(f'{nm}_lower',[6,0,z-0.5],[7,7,z+0.5],solid(g['DARK2']),m),
                C(f'{nm}_claw',[6,0,z-1.5],[7,1,z-0.5],solid(g['BONE']),m)],mirror=m)
            return G(f'{nm}_leg',[3,6.5,z],[C(f'{nm}_upper',[3,6,z-0.5],[7,7,z+0.5],solid(g['DARK2']),m),
                C(f'{nm}_knee',[6,6,z-1],[8,8,z+1],solid(g['DARK']),m), low],[0,0,15],mirror=m)
        head=Group('head',[0,7,-4],[
            C('head',[-2.5,5,-8],[2.5,9,-4],dk()),
            C('maw',[-2,4,-8.5],[2,5,-5],solid(g['DARK2'])),
            C('right_eye',[0.5,7,-8.1],[2,8,-8.1] if False else [2,8,-8.1],glow(g['EYE'])) if False else C('right_eye',[0,7,-8.1],[2,8,-8.1],glow(g['EYE'])),
            C('left_eye',[-2,7,-8.1],[0,8,-8.1],glow(g['EYE'])),
            C('right_horn',[1.5,9,-6],[2.5,11,-5],solid(g['BONE']),rot=[-30,0,-15]),
            C('left_horn',[-2.5,9,-6],[-1.5,10,-5],solid(g['BONE']),rot=[-30,0,15])])
        tail=Group('tail',[0,6,4],[
            C('tail1',[-1.5,5,4],[1.5,7,8],dk()),
            Group('tail2',[0,6,8],[C('tail2',[-1,5,8],[1,6,12],dk()),
                Group('tail3',[0,5.5,12],[C('tail3',[-0.5,5,12],[0.5,6,15],dk())],[-20,0,0])],[-15,0,0])])
        body=Group('body',[0,6,0],[
            C('body',[-3,4,-4],[3,8,4],dk()),
            C('hump',[-2.5,8,-2],[2.5,10,3],dk()),
            C('spine1',[-0.5,10,-1],[0.5,12,0],solid(g['BONE']),rot=[-25,0,0]),
            C('spine2',[-0.5,10,1],[0.5,11,2],solid(g['BONE']),rot=[-25,0,0]),
            C('spine3',[-0.5,8,3],[0.5,9,4],solid(g['BONE']),rot=[-25,0,0]),
            C('shroud',[-3.5,2,-3],[3.5,4,4],tatter(g['DARK2'],2,0.05)),
            head, tail])
        return Group('root',[0,0,0],[body, leg('right',True),leg('left',True),leg('right',False),leg('left',False)])
    def anims(s,Gs):
        n='animation.fleeing_shadow.'; A=[]
        A.append(anim(n+'spawn',0.8,'once',{'root':[('scale',0,.2,.2,.2),('scale',.5,1.1,1.1,1.1,cm),('scale',.8,1,1,1)],
            'head':[('rotation',0,0,0,0),('rotation',.5,25,0,0,cm),('rotation',.8,0,0,0,cm)]},Gs))
        L=1.0
        A.append(anim(n+'idle',L,'loop',{'body':[('position',0,0,0,0,cm),('position',L/2,0,-.4,0,cm),('position',L,0,0,0,cm)],
            'head':[('rotation',0,0,-15,0,cm),('rotation',L/2,0,15,0,cm),('rotation',L,0,-15,0,cm)],
            'tail':[('rotation',0,0,-20,0,cm),('rotation',L/2,0,20,0,cm),('rotation',L,0,-20,0,cm)]},Gs))
        L=0.5
        A.append(anim(n+'walk',L,'loop',walk_tracks(L,30,26,35,'%s_front_forearm','%s_hind_shin',0.5,{
            'tail':[('rotation',0,0,-25,0,cm),('rotation',L/2,0,25,0,cm),('rotation',L,0,-25,0,cm)],
            'head':[('rotation',0,8,0,0,cm),('rotation',L,8,0,0,cm)]}),Gs))
        A.append(anim(n+'attack',0.6,'once',{'head':[('rotation',0,0,0,0),('rotation',.2,25,0,0,cm),('rotation',.3,-15,0,0),('rotation',.6,0,0,0,cm)],
            'body':[('position',0,0,0,0),('position',.3,0,0,-2),('position',.6,0,0,0,cm)]},Gs))
        A.append(anim(n+'death',1.2,'hold',{'root':[('scale',0,1,1,1),('scale',.3,1.2,.7,1.2,cm),('scale',1.2,.05,.05,.05,cm)],
            'head':[('rotation',0,0,0,0),('rotation',.3,35,0,0,cm)]},Gs))
        return A

O='/mnt/user-data/outputs/'
def run(M, seed):
    m=M(); globals().update(M.P)
    export(m.id, m.build(), m.anims, seed, O+m.id+'.bbmodel', M.HITBOX)
    globals().update(M.SHINY)
    sid=getattr(M,'SHINY_NAME',m.id+'_shiny')
    if sid==m.id+'_shiny':
        export(sid, m.build(), m.anims, seed, O+sid+'.bbmodel', M.HITBOX)
    else:   # 진짜 성배: 같은 지오메트리 별도 몹
        export(sid, m.build(), lambda Gs:[dict(a,name=a['name'].replace(m.id,sid)) for a in m.anims(Gs)], seed, O+sid+'.bbmodel', M.HITBOX)

if __name__=='__main__':
    for i,M in enumerate([Imp,Enforcer,Grail,Shadow]): run(M, 11+i)
