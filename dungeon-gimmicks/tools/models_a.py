from kit import *

# =============== A. 입력 장치 ===============

@model('gm_fire_brazier','원소 화로','A','fire',(0.8,2.0,1.2),
       '불 속성이 닿으면 점화되어 회로에 마력을 보낸다. 물·바람이 닿으면 꺼진다.',
       'idle=꺼짐 / activate=점화 / active=타오름 / deactivate=소화')
def fire_brazier():
    base,y = plinth('plinth',12,12,2,tiers=2)
    pil = box('pillar',0,y,0,6,10,6,runed(stone(),'north','south','east','west',runec=A('ACCD')))
    collar = box('collar',0,14,0,8,2,8,metal(BRONZE))
    fins = [C(f'fin_{i}',[sx*4-1,12,sz*4-1],[sx*4+1,17,sz*4+1],metal(BRONZED),rot=[0,45,0],origin=[sx*4,12,sz*4])
            for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])]
    bowl = [box('bowl_bottom',0,16,0,10,2,10,metal(BRONZE)),
            C('rim_n',[-6,18,-6],[6,21,-5],metal(BRONZE)), C('rim_s',[-6,18,5],[6,21,6],metal(BRONZE)),
            C('rim_e',[5,18,-5],[6,21,5],metal(BRONZE)),   C('rim_w',[-6,18,-5],[-5,21,5],metal(BRONZE)),
            C('lip_n',[-7,21,-7],[7,22,-5],metal(BRONZED)), C('lip_s',[-7,21,5],[7,22,7],metal(BRONZED)),
            C('lip_e',[5,21,-5],[7,22,5],metal(BRONZED)),   C('lip_w',[-7,21,-5],[-5,22,5],metal(BRONZED)),
            box('coal',0,18,0,10,1,10,stone((52,40,36),mortar=False)),
            box('coal_lump1',-2,19,1,3,1,3,stone((70,50,40),mortar=False)),
            box('coal_lump2',2,19,-2,3,1,2,stone((70,50,40),mortar=False))]
    fl = flame()
    flame_g = Group('flame',[0,19,0],[
        plane_n('flame_a',-4,19,4,31,0,fl), plane_n('flame_b',-4,19,4,31,0,fl,rot=[0,60,0],origin=[0,19,0]),
        plane_n('flame_c',-4,19,4,31,0,fl,rot=[0,120,0],origin=[0,19,0]),
        plane_n('flame_core',-2,19,2,25,0,flame((255,200,90),(255,255,230)),rot=[0,90,0],origin=[0,19,0])])
    g=glyph()
    runes = Group('runes',[0,9,0],[plane_n('rune_n',-2,6,2,13,-3.1,g), plane_n('rune_s',-2,6,2,13,3.1,g),
                                    plane_e('rune_e',-2,6,2,13,3.1,g), plane_e('rune_w',-2,6,2,13,-3.1,g)])
    root = Group('root',[0,0,0],[Group('base',[0,0,0],base+[pil,collar]+fins),
                                 Group('bowl',[0,16,0],bowl+[flame_g]), runes])
    def an(G):
        return std('gm_fire_brazier',G,
            idle={'flame':hide(),'runes':hide()},
            activate={'flame':grow(0,.6),'runes':grow(.2,.8)},
            active={'flame':[('scale',0,1,1,1,cm),('scale',.5,1.08,1.15,1.08,cm),('scale',1,.95,.92,.95,cm),('scale',1.5,1.05,1.1,1.05,cm),('scale',2,1,1,1,cm)]
                             +[('rotation',0,0,0,0,cm),('rotation',1,0,12,0,cm),('rotation',2,0,0,0,cm)],
                    'runes':show()},
            deactivate={'flame':shrink(0,.5),'runes':shrink(.2,.8)},
            spawn=spawn_rise())
    return root, an

@model('gm_ice_seal','얼음 봉인 결정','A','fire',(1.0,1.6,1.0),
       '불로 얼음을 녹이면 안에 봉인된 원소 조각이 드러나 떠오른다.',
       'idle=봉인 / activate=녹으며 개방 / active=조각 부유')
def ice_seal():
    base,y = plinth('plinth',14,14,2,tiers=1, paint=stone(moss=.15))
    ice=crystal((170,225,255),a=165); iced=crystal((120,190,240),a=185)
    shards=[]
    spec=[('ice_main',0,0,8,18,0,0),('ice_n',0,-4,5,12,-25,0),('ice_s',1,4,5,11,22,0),
          ('ice_e',4,0,5,13,0,-24),('ice_w',-4,1,4,10,0,26),('ice_ne',3,-3,3,8,-18,-20),('ice_sw',-3,3,3,7,18,20)]
    for nm,x,z,s,h,rx,rz in spec:
        p = ice if nm=='ice_main' else iced
        g = Group(nm,[x,y,z],[C(nm+'_body',[x-s/2,y,z-s/2],[x+s/2,y+h,z+s/2],p,rot=[0,45,0],origin=[x,y,z]),
                               C(nm+'_tip',[x-(s-2)/2,y+h,z-(s-2)/2],[x+(s-2)/2,y+h+2,z+(s-2)/2],p,rot=[0,45,0],origin=[x,y,z])],
                  [rx,0,rz])
        shards.append(g)
    use_fire = dict(ELEM['fire'])
    core = Group('core',[0,10,0], gem('core_gem',0,8,0,4,5,crystal(use_fire['GEM'],a=255)) +
                 [C('core_band',[-2.5,10,-2.5],[2.5,11,2.5],metal((214,170,80)),rot=[0,45,0],origin=[0,10,0])])
    root = Group('root',[0,0,0],[Group('base',[0,0,0],base+[box('chip1',5,2,4,2,1,2,stone()),box('chip2',-5,2,-4,2,1,3,stone())]),
                                 Group('ice',[0,y,0],shards), core])
    def an(G):
        melt={}
        for i,(nm,*_) in enumerate(spec):
            t0=.1*i
            melt[nm]=[('scale',t0,1,1,1),('scale',t0+.25,.9,.6,.9,cm),('scale',t0+.7,0,0,0,cm)]
        return std('gm_ice_seal',G, L=dict(activate=1.6,active=3.0),
            idle={'core':[('rotation',0,0,0,0),('rotation',3,0,90,0)]},
            activate=merge(melt,{'core':[('position',0,0,0,0),('position',.9,0,0,0),('position',1.6,0,6,0,cm),
                                         ('rotation',0,0,0,0),('rotation',1.6,0,360,0,cm)]}),
            active=merge({nm:hide() for nm,*_ in spec},
                         {'core':[('position',0,0,6,0,cm),('position',1.5,0,7.2,0,cm),('position',3,0,6,0,cm)]+spin_n(1,3)}),
            spawn=spawn_pop())
    return root, an

@model('gm_wind_turbine','풍차 터빈','A','wind',(0.8,2.6,2.0),
       '바람이 닿으면 날개가 돌고, 도는 동안만 회로에 마력을 공급한다.',
       'idle=멈춤(흔들림) / activate=가동 / active=회전 / deactivate=감속')
def wind_turbine():
    base,y = plinth('plinth',12,12,2,tiers=2)
    post=[box('post',0,y,0,4,26,4,wood()), box('post_band1',0,10,0,5,1,5,metal(BRONZE)),
          box('post_band2',0,20,0,5,1,5,metal(BRONZE)),
          *[C(f'strut_{i}',[-0.5,4,-4.5],[0.5,12,-3.5],wood(WOODD),rot=[20,90*i,0],origin=[0,4,0]) for i in range(4)]]
    nac=[box('nacelle',0,29,0,6,6,10,runed(metal(BRONZE),'east','west')),
         box('nacelle_top',0,35,1,4,1,7,metal(BRONZED)),
         box('tail_boom',0,31,7,2,2,6,wood()),
         C('tail_fin',[-0.5,29,10],[0.5,37,14],runed(wood((200,200,190)),'east','west'))]
    blades=[]
    for i in range(4):
        a=90*i
        blades.append(Group(f'blade_{i}',[0,32,-6],[
            C(f'blade_arm_{i}',[-1,33,-6.5],[1,39,-5.5],wood(WOODD)),
            C(f'blade_sail_{i}',[-2.5,37,-6.5],[2.5,49,-5.5],runed(wood((226,232,220)),'north','south',runec=A('ACCD')),rot=[0,12,0],origin=[0,37,-6])],
            [0,0,a]))
    rotor=Group('rotor',[0,32,-6],[box('hub',0,30,-6.5,4,4,3,metal(BRONZE)),
                                   box('hub_cap',0,31,-8.5,2,2,2,glow())]+blades)
    root=Group('root',[0,0,0],[Group('tower',[0,0,0],base+post),Group('head',[0,29,0],nac+[rotor])])
    def an(G):
        return std('gm_wind_turbine',G, L=dict(active=1.0,activate=1.5,deactivate=2.0),
            idle={'rotor':[('rotation',0,0,0,-8,cm),('rotation',1,0,0,8,cm),('rotation',2,0,0,-8,cm)],
                  'head':[('rotation',0,0,-4,0,cm),('rotation',1,0,4,0,cm),('rotation',2,0,-4,0,cm)]},
            activate={'rotor':[('rotation',0,0,0,0),('rotation',.75,0,0,-200,'linear'),('rotation',1.5,0,0,-720,'linear')]},
            active={'rotor':spin_n(2,1.0,-1)},
            deactivate={'rotor':[('rotation',0,0,0,0),('rotation',1,0,0,-300,cm),('rotation',2,0,0,-360,cm)]})
    return root, an

@model('gm_wind_bell','바람 종','A','wind',(1.2,2.0,1.5),
       '바람으로 울리면 음 하나가 남는다. 여러 개를 두고 정해진 순서대로 울리는 퍼즐용.',
       'idle=정지 / activate=울림(흔들림) / active=잔향 흔들림')
def wind_bell():
    parts=[]
    for s in (1,-1):
        parts += [C(f'foot_{s}',[s*7-3,0,-3],[s*7+3,3,3],stone(),mirror=False),
                  C(f'post_{s}',[s*7-1.5,3,-1.5],[s*7+1.5,30,1.5],wood()),
                  C(f'post_cap_{s}',[s*7-2,30,-2],[s*7+2,32,2],metal(BRONZE))]
    parts += [box('beam',0,32,0,20,3,4,runed(wood(),'north','south')),
              C('roof_n',[-11,35,-4],[11,36,0],wood(WOODD),rot=[20,0,0],origin=[0,35,0]),
              C('roof_s',[-11,35,0],[11,36,4],wood(WOODD),rot=[-20,0,0],origin=[0,35,0]),
              C('ridge',[-11,36,-1],[11,37,1],metal(BRONZE))]
    br=metal((120,200,180)); brd=metal((70,140,124))
    clap=Group('clapper',[0,26,0],[box('clap_rod',0,19,0,1,7,1,metal(IRON)),box('clap_ball',0,17,0,2,2,2,metal(IRON))])
    bell=Group('bell',[0,32,0],[box('hanger',0,29,0,2,3,2,metal(IRON)),
        box('bell_crown',0,27,0,4,2,4,br), box('bell_top',0,25,0,6,2,6,runed(br,'north','south','east','west',runec=A('GLOW'))),
        box('bell_mid',0,21,0,8,4,8,runed(br,'north','south','east','west',runec=A('GLOW'))),
        box('bell_lip',0,19,0,10,2,10,brd), clap])
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],parts),bell])
    def an(G):
        sw=[('rotation',0,0,0,0),('rotation',.15,22,0,0,cm),('rotation',.45,-16,0,0,cm),('rotation',.75,10,0,0,cm),
            ('rotation',1.05,-6,0,0,cm),('rotation',1.35,3,0,0,cm),('rotation',1.6,0,0,0,cm)]
        cl=[('rotation',0,0,0,0),('rotation',.15,-25,0,0,cm),('rotation',.3,20,0,0,cm),('rotation',.6,-12,0,0,cm),('rotation',1.0,6,0,0,cm),('rotation',1.6,0,0,0,cm)]
        return std('gm_wind_bell',G, L=dict(activate=1.6),
            idle={'bell':[('rotation',0,0,0,-1.5,cm),('rotation',1,0,0,1.5,cm),('rotation',2,0,0,-1.5,cm)]},
            activate={'bell':sw,'clapper':cl},
            active={'bell':[('rotation',0,4,0,0,cm),('rotation',1,-4,0,0,cm),('rotation',2,4,0,0,cm)],
                    'clapper':[('rotation',0,-5,0,0,cm),('rotation',1,5,0,0,cm),('rotation',2,-5,0,0,cm)]})
    return root, an

@model('gm_water_wheel','물레방아','A','water',(1.6,1.8,1.0),
       '물이 닿거나 흐르면 바퀴가 돌며 마력을 만든다. 물길 수문과 연쇄 가능.',
       'idle=정지 / activate=돌기 시작 / active=회전 / deactivate=멈춤')
def water_wheel():
    fr=[]
    for s in (1,-1):
        fr += [C(f'leg_a_{s}',[s*9-1,0,-6],[s*9+1,18,-4],wood(),rot=[-18,0,0],origin=[s*9,0,-5]),
               C(f'leg_b_{s}',[s*9-1,0,4],[s*9+1,18,6],wood(),rot=[18,0,0],origin=[s*9,0,5]),
               C(f'bearing_{s}',[s*9-2,15,-2],[s*9+2,19,2],metal(BRONZE))]
    trough=[box('trough_floor',0,0,0,16,1,22,stone()),C('trough_e',[7,1,-11],[8,4,11],stone()),C('trough_w',[-8,1,-11],[-7,4,11],stone()),
            plane_u('trough_water',-7,-11,7,11,3,water(170))]
    wl=[]
    rimp=wood(WOODD)
    wl += ring('rim_r',4.5,17,0,11,12,0,0,0,None) if False else []
    for s in (4.5,-4.5):
        for i in range(12):
            a=30*i; r=math.radians(a)
            yy,zz=17+11*math.cos(r),11*math.sin(r)
            wl.append(C(f'rim_{int(s>0)}_{i}',[s-0.5,yy-0.5,zz-3],[s+0.5,yy+0.5,zz+3],rimp,rot=[a,0,0],origin=[s,yy,zz]))
    for i in range(6):
        a=30*i
        wl.append(C(f'spoke_{i}',[-0.5,6,-0.5],[0.5,28,0.5],wood(),rot=[a,0,0],origin=[0,17,0]))
    for i in range(12):
        a=30*i+15; r=math.radians(a); yy,zz=17+10.5*math.cos(r),10.5*math.sin(r)
        wl.append(C(f'paddle_{i}',[-5,yy-2,zz-0.5],[5,yy+2,zz+0.5],runed(wood((150,110,70)),'north','south',runec=A('ACC')),rot=[a,0,0],origin=[0,yy,zz]))
    wl += [C('axle',[-10,16,-1],[10,18,1],metal(IRON)), box('hub',0,15,0,4,4,4,metal(BRONZE)),
           C('hub_gem_r',[2,16,-1],[2.2,18,1],glow()), C('hub_gem_l',[-2.2,16,-1],[-2,18,1],glow())]
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],fr+trough),Group('wheel',[0,17,0],wl)])
    def an(G):
        return std('gm_water_wheel',G,L=dict(active=3.0,activate=1.5,deactivate=2.0),
            idle={'wheel':[('rotation',0,-2,0,0,cm),('rotation',1,2,0,0,cm),('rotation',2,-2,0,0,cm)]},
            activate={'wheel':[('rotation',0,0,0,0),('rotation',1.5,-180,0,0,'linear')]},
            active={'wheel':spin_n(0,3.0,-1)},
            deactivate={'wheel':[('rotation',0,0,0,0),('rotation',1,-60,0,0,cm),('rotation',2,-80,0,0,cm)]})
    return root, an

@model('gm_water_pillar','수위 기둥','A','water',(0.8,2.0,1.6),
       '물을 넣을 때마다 수위가 한 칸씩 오른다. 정해진 높이에서 작동.',
       'idle=비어 있음 / level_1~3=단계 수위 / activate=가득 참 / active=출렁임')
def water_pillar():
    base,y=plinth('plinth',12,12,2,tiers=2)
    posts=[C(f'post_{i}',[sx*5-1,4,sz*5-1],[sx*5+1,28,sz*5+1],runed(metal(BRONZE),'north','east',runec=A('ACCD'))) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])]
    gl=glow((200,230,255),a=55)
    glass=[plane_n('glass_n',-4,4,4,28,-4,gl),plane_n('glass_s',-4,4,4,28,4,gl),plane_e('glass_e',-4,4,4,28,4,gl),plane_e('glass_w',-4,4,4,28,-4,gl)]
    marks=[C(f'mark_{i}',[-4,4+6*i,-4.2],[4,4.5+6*i,-4.2],metal(BRONZED)) for i in range(1,4)]
    cap=[box('cap',0,28,0,12,2,12,stone()),box('cap2',0,30,0,8,2,8,stone()),box('spout',0,30,-5,2,2,4,metal(BRONZE))]
    gm=Group('cap_gem',[0,32,0],gem('gem',0,33,0,4,3))
    wat=Group('water',[0,4,0],[box('water',0,4,0,7,24,7,water(170))])
    root=Group('root',[0,0,0],[Group('body',[0,0,0],base+posts+glass+marks+cap),wat,gm])
    def an(G):
        lv=lambda a,b:{'water':[('scale',0,1,a,1),('scale',.8,1,b,1,cm)]}
        return std('gm_water_pillar',G,
            idle={'water':[('scale',0,1,.02,1)],'cap_gem':[('rotation',0,0,0,0),('rotation',2,0,90,0)]},
            activate=merge(lv(.75,1.0),{'cap_gem':[('position',0,0,0,0),('position',1,0,2,0,cm)]}),
            active={'water':[('scale',0,1,1,1,cm),('scale',1,1,.98,1,cm),('scale',2,1,1,1,cm)],
                    'cap_gem':[('position',0,0,2,0)]+spin_n(1,2)},
            deactivate={'water':[('scale',0,1,1,1),('scale',1,1,.02,1,cm)]},
            extra={'level_1':(0.8,'hold',lv(.02,.25)),'level_2':(0.8,'hold',lv(.25,.5)),'level_3':(0.8,'hold',lv(.5,.75))})
    return root, an

@model('gm_vine_pot','씨앗 화분','A','earth',(0.8,2.8,0.9),
       '대지 속성이 닿으면 덩굴이 위로 자라 사다리가 된다.',
       'idle=새싹 / activate=덩굴 성장 / active=잎 흔들림 / deactivate=시듦')
def vine_pot():
    terr=(176,96,62)
    pot=[box('pot_foot',0,0,0,8,2,8,stone(terr,mortar=False)),box('pot_body',0,2,0,10,6,10,runed(stone(terr,mortar=False),'north','south','east','west',runec=A('ACCD'))),
         box('pot_neck',0,8,0,9,1,9,stone((150,80,52),mortar=False)),box('pot_rim',0,9,0,12,2,12,stone(terr,mortar=False)),
         box('soil',0,10.5,0,10,0,10,stone((92,64,44),mortar=False))]
    leaf=solid((92,160,64)); stem=solid((70,112,46))
    sprout=Group('sprout',[0,11,0],[box('sprout_stem',0,10.5,0,1,3,1,stem),
            C('sprout_leaf_a',[0,13,-0.5],[3,13,1.5],leaf,rot=[0,0,20],origin=[0,13,0]),
            C('sprout_leaf_b',[-3,13,-1.5],[0,13,0.5],leaf,rot=[0,0,-20],origin=[0,13,0])])
    segs=[]; prev=None
    for i in range(4):
        y0=11+8*i
        kids=[box(f'vine_stem_{i}',0,y0,0,2,8,2,stem),
              C(f'vine_twist_{i}',[-1.5,y0+2,-1.5],[1.5,y0+5,1.5],stem,rot=[0,45,0],origin=[0,y0,0]),
              C(f'leaf_{i}_a',[0,y0+3,-3],[0,y0+7,1],solid((104,176,72)),rot=[0,0,0],origin=[0,y0,0]),
              plane_u(f'leaf_{i}_b',1,-2,6,2,y0+5,solid((92,160,64)),rot=[0,0,-25],origin=[1,y0+5,0]),
              plane_u(f'leaf_{i}_c',-6,-2,-1,2,y0+2,solid((92,160,64)),rot=[0,0,25],origin=[-1,y0+2,0]),
              C(f'rung_{i}',[-4,y0+4,-0.5],[4,y0+5,0.5],stem)]
        if i==3: kids.append(Group('bloom',[0,y0+8,0],gem('bloom_gem',0,y0+9,0,3,2,crystal(A('GEM'),a=255))))
        g=Group(f'vine_{i}',[0,y0,0],kids+([prev] if False else []))
        segs.append(g)
    # nest segments so each grows from the previous
    for i in range(2,-1,-1): segs[i].children.append(segs[i+1])
    root=Group('root',[0,0,0],[Group('pot',[0,0,0],pot),sprout,segs[0]])
    def an(G):
        act={'sprout':shrink(0,.3)}
        for i in range(4): act[f'vine_{i}']=[('scale',0,0,0,0),('scale',.3+.35*i,0,0,0),('scale',.3+.35*i+.35,1.1,1.1,1.1,cm),('scale',.3+.35*i+.5,1,1,1,cm)]
        return std('gm_vine_pot',G,L=dict(activate=2.0,active=3.0,deactivate=1.2),
            idle={'vine_0':hide(),'sprout':[('rotation',0,0,0,-6,cm),('rotation',1,0,0,6,cm),('rotation',2,0,0,-6,cm)]},
            activate=act,
            active={'sprout':hide(),**{f'vine_{i}':[('rotation',0,0,0,-2,cm),('rotation',1.5,0,0,2,cm),('rotation',3,0,0,-2,cm)] for i in range(1,4)}},
            deactivate={'vine_0':[('scale',0,1,1,1),('scale',1.0,1,0,1,cm)],'sprout':grow(.8,1.2)})
    return root, an

@model('gm_pressure_plate','대지 압력판','A','earth',(1.0,0.2,0.1),
       '무거운 물체나 대지 속성이 올라가 있는 동안만 작동한다.',
       'idle=올라옴 / activate=눌림 / active=눌린 상태 / deactivate=복귀')
def pressure_plate():
    fr=[C('frame_n',[-8,0,-8],[8,2,-6],stone()),C('frame_s',[-8,0,6],[8,2,8],stone()),
        C('frame_e',[6,0,-6],[8,2,6],stone()),C('frame_w',[-8,0,-6],[-6,2,6],stone()),
        box('pit',0,0,0,12,0.0,12,stone(STONED))]
    fr+= [box(f'corner_{i}',sx*7,2,sz*7,2,1,2,metal(BRONZE)) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])]
    plate=Group('plate',[0,1,0],[box('plate',0,0.5,0,12,2,12,with_faces(stone(STONEL,mortar=False),up=rune_face(A('ACCD')))),
                                 box('plate_boss',0,2.5,0,4,0.0,4,stone(STONEL))])
    glo=Group('glow',[0,2.6,0],[plane_u('glow_ring',-5,-5,5,5,2.6,frame_glow()),plane_u('glow_rune',-3,-3,3,3,2.6,glyph())])
    plate.children.append(glo)
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],fr),plate])
    def an(G):
        return std('gm_pressure_plate',G,L=dict(activate=.4,deactivate=.6),
            idle={'glow':hide()},
            activate={'plate':[('position',0,0,0,0),('position',.25,0,-1.7,0),('position',.4,0,-1.5,0,cm)],'glow':grow(.1,.4)},
            active={'plate':[('position',0,0,-1.5,0)],'glow':pulse(2,.95,1.05)},
            deactivate={'plate':[('position',0,0,-1.5,0),('position',.6,0,0,0,cm)],'glow':shrink(0,.3)},
            spawn=spawn_pop(.8))
    return root, an

@model('gm_light_receiver','빛 수신 수정','A','light',(0.8,1.8,1.4),
       '빛이 닿으면 발톱 받침이 열리고 수정이 떠올라 회전한다. 색 지정 퍼즐용.',
       'idle=잠듦 / activate=개방 / active=회전 / deactivate=닫힘')
def light_receiver():
    base,y=plinth('plinth',10,10,2,tiers=2)
    col_=[box('column',0,4,0,4,9,4,runed(stone(STONEL),'north','south',runec=A('ACCD'))),box('cup',0,13,0,8,2,8,metal(BRONZE)),box('cup_in',0,15,0,6,1,6,metal(BRONZED))]
    claws=[]
    for i in range(4):
        a=90*i+45
        claws.append(Group(f'claw_{i}',[0,15,0],[
            C(f'claw_{i}_low',[-1,15,-4.5],[1,21,-3],metal(BRONZE),rot=[-15,0,0],origin=[0,15,-4]),
            C(f'claw_{i}_tip',[-0.5,20,-3.5],[0.5,24,-2.5],metal((220,180,90)),rot=[25,0,0],origin=[0,20,-3])],[0,a,0]))
    cr=Group('crystal',[0,20,0],gem('crystal',0,17,0,4,6,crystal(A('GEM'),a=240)))
    root=Group('root',[0,0,0],[Group('stand',[0,0,0],base+col_)]+claws+[cr])
    def an(G):
        op=lambda a:{f'claw_{i}':[('rotation',0,0,0,0),('rotation',.6,-a,0,0,cm)] for i in range(4)}
        cl={f'claw_{i}':[('rotation',0,-30,0,0),('rotation',.6,0,0,0,cm)] for i in range(4)}
        return std('gm_light_receiver',G,
            idle={'crystal':[('position',0,0,0,0,cm),('position',1,0,.4,0,cm),('position',2,0,0,0,cm)]},
            activate=merge(op(30),{'crystal':[('position',0,0,0,0),('position',1,0,4,0,cm),('rotation',0,0,0,0),('rotation',1,0,180,0,cm)]}),
            active=merge({f'claw_{i}':[('rotation',0,-30,0,0)] for i in range(4)},
                         {'crystal':[('position',0,0,4,0,cm),('position',1,0,4.8,0,cm),('position',2,0,4,0,cm)]+spin_n(1,2)}),
            deactivate=merge(cl,{'crystal':[('position',0,0,4,0),('position',.8,0,0,0,cm)]}))
    return root, an

@model('gm_sundial','해시계 석판','A','light',(1.0,0.6,0.4),
       '빛 방향에 따라 바늘이 다른 문양을 가리킨다. 다른 퍼즐의 힌트 장치.',
       'idle=정지 / point_n·e·s·w=방향 가리키기 / activate=정답 발광')
def sundial():
    s=stone(STONEL)
    base=[box('slab',0,0,0,16,2,16,stone()),C('slab_oct',[-8,0,-8],[8,2,8],stone(),rot=[0,45,0],origin=[0,0,0]),
          box('face',0,2,0,13,1,13,with_faces(s,up=rune_face(A('ACCD'),.3))),C('face_oct',[-6.5,2,-6.5],[6.5,3,6.5],s,rot=[0,45,0],origin=[0,2,0])]
    marks=[]
    for i in range(8):
        a=math.radians(45*i); x,z=6*math.sin(a),-6*math.cos(a)
        marks.append(C(f'hour_{i}',[x-0.5,3,z-0.5],[x+0.5,3.5 if i%2 else 4,z+0.5],metal(BRONZE)))
    gno=[C('gnomon_1',[-0.5,3,0],[0.5,4,6],metal(BRONZE)),C('gnomon_2',[-0.5,4,1],[0.5,6,6],metal(BRONZE)),
         C('gnomon_3',[-0.5,6,3],[0.5,8,6],metal(BRONZE)),C('gnomon_4',[-0.5,8,5],[0.5,10,6],metal(BRONZED))]
    ptr=Group('pointer',[0,3.5,0],[box('pointer_hub',0,3,0,2,1.0,2,metal((220,180,90))),C('pointer_bar',[-0.5,3.5,-5],[0.5,4.5,0],metal((220,180,90))),
                                   C('pointer_tip',[-1,3.5,-6.5],[1,4.5,-5],glow())])
    glo=Group('glow',[0,3.1,0],[plane_u('glow_ring',-6,-6,6,6,3.1,frame_glow()),plane_u('glow_ring_oct',-6,-6,6,6,3.1,frame_glow(),rot=[0,45,0],origin=[0,3.1,0])])
    root=Group('root',[0,0,0],[Group('dial',[0,0,0],base+marks+gno),ptr,glo])
    def an(G):
        pt=lambda a:{'pointer':[('rotation',0,0,0,0),('rotation',.8,0,a,0,cm)]}
        return std('gm_sundial',G,L=dict(activate=.8),
            idle={'glow':hide()},
            activate={'glow':grow(0,.8)},
            active={'glow':pulse(2,.98,1.03)},
            extra={'point_n':(0.8,'hold',pt(0)),'point_e':(0.8,'hold',pt(-90)),'point_s':(0.8,'hold',pt(180)),'point_w':(0.8,'hold',pt(90))})
    return root, an

@model('gm_shadow_candelabra','그림자 촛대','A','dark',(0.8,1.9,1.5),
       '어둠이 닿으면 불이 꺼지면서 작동한다(불 퍼즐의 반대). 중앙의 눈이 열린다.',
       'idle=촛불 켜짐 / activate=소등·눈 발광 / active=눈 맥동 / deactivate=재점화')
def shadow_candelabra():
    ir=metal((70,62,82)); wax=solid((96,70,126))
    feet=[C(f'foot_{i}',[-1,0,-6],[1,2,0],ir,rot=[0,120*i,0],origin=[0,0,0]) for i in range(3)]
    feet+=[C(f'claw_{i}',[-1.5,0,-7],[1.5,1,-5],ir,rot=[0,120*i,0],origin=[0,0,0]) for i in range(3)]
    stem=[box('base_disc',0,1,0,5,2,5,ir),box('stem',0,3,0,2,16,2,ir),box('knot',0,10,0,3,2,3,metal(BRONZE)),
          box('arm',0,19,0,14,1,2,ir),C('arm_hook_r',[6,17,-1],[7,19,1],ir),C('arm_hook_l',[-7,17,-1],[-6,19,1],ir)]
    cups=[]; flames=[]
    fl=flame((150,80,230),(230,200,255))
    for i,(x,y) in enumerate([(-6,20),(0,22),(6,20)]):
        cups+=[box(f'cup_{i}',x,y,0,4,2,4,metal(BRONZE)),box(f'candle_{i}',x,y+2,0,2,4 if i!=1 else 5,2,wax)]
        top=y+6 if i!=1 else y+7
        flames.append(Group(f'flame_{i}',[x,top,0],[plane_n(f'fl_{i}_a',x-1.5,top,x+1.5,top+5,0,fl),
                                                    plane_n(f'fl_{i}_b',x-1.5,top,x+1.5,top+5,0,fl,rot=[0,90,0],origin=[x,top,0])]))
    stem.append(box('mid_post',0,20,0,2,2,2,ir))
    eye=Group('eye',[0,13,-1.6],[C('eye_frame',[-2,11,-2],[2,15,-1.5],metal(BRONZE)),
                                 plane_n('eye_iris',-1.5,11.5,1.5,14.5,-2.1,glyph(A('GLOW')))])
    root=Group('root',[0,0,0],[Group('stand',[0,0,0],feet+stem+cups)]+flames+[eye])
    def an(G):
        fls=[f'flame_{i}' for i in range(3)]
        flick=lambda k:[('scale',0,1,1,1,cm),('scale',.4+k*.1,1.1,1.2,1.1,cm),('scale',.9,.95,.9,.95,cm),('scale',1.5,1,1,1,cm)]
        return std('gm_shadow_candelabra',G,L=dict(idle=1.5,activate=1.2,deactivate=1.0),
            idle={**{f:flick(i) for i,f in enumerate(fls)},'eye':[('scale',0,1,.15,1)]},
            activate={**{f:shrink(.15*i,.15*i+.4) for i,f in enumerate(fls)},'eye':[('scale',0,1,.15,1),('scale',.6,1,.15,1),('scale',1.2,1,1,1,cm)]},
            active={**{f:hide() for f in fls},'eye':pulse(2,.95,1.08)},
            deactivate={**{f:grow(.2*i,.2*i+.5) for i,f in enumerate(fls)},'eye':[('scale',0,1,1,1),('scale',.5,1,.15,1,cm)]})
    return root, an

@model('gm_sleeping_statue','잠든 석상','A','dark',(0.9,1.8,1.5),
       '어둠을 먹이면 눈을 뜨고 고개를 돌려 숨겨진 길을 바라본다(힌트 장치).',
       'idle=잠듦 / activate=눈 뜨고 고개 돌림 / active=눈 맥동 / deactivate=다시 잠듦')
def sleeping_statue():
    s=stone(STONEL,mortar=False); sd=stone(STONE,mortar=False)
    ped=[box('ped',0,0,0,14,3,12,stone()),box('ped_top',0,3,0,12,2,10,runed(stone(),'north',runec=A('ACCD')))]
    bust=[box('chest',0,5,0,12,6,7,s),C('shoulder_r',[5,8,-3],[8,11,3],s),C('shoulder_l',[-8,8,-3],[-5,11,3],s),
          box('collar',0,11,0,8,1,5,sd),box('neck',0,12,0,4,2,4,s),
          C('robe_fold_1',[-3,5,-3.6],[-2,10,-3.5],sd),C('robe_fold_2',[2,5,-3.6],[3,10,-3.5],sd)]
    lids=[]
    for side,x in (('r',2.5),('l',-2.5)):
        lids.append(Group(f'lid_{side}',[x,19,-4.6],[C(f'lid_{side}',[x-1.5,18,-5],[x+1.5,19.5,-4],s)]))
    eyes=Group('eyes',[0,18.5,-4.2],[plane_n('eye_r',1,17.5,4,19,-4.15,glow()),plane_n('eye_l',-4,17.5,-1,19,-4.15,glow())])
    head=Group('head',[0,14,0],[box('head',0,14,0,9,10,8,s),C('brow',[-4.5,19.5,-4.5],[4.5,20.5,-4],sd),
        C('nose',[-1,16,-5],[1,19,-4],s),C('mouth',[-1.5,15,-4.2],[1.5,15.5,-4.1],sd),
        C('ear_r',[4.5,16,-1],[5.5,20,1],s),C('ear_l',[-5.5,16,-1],[-4.5,20,1],s),
        C('sock_r',[1,17.5,-4.1],[4,19,-4],sd),C('sock_l',[-4,17.5,-4.1],[-1,19,-4],sd),
        box('hood',0,22,0.5,10,3,9,runed(stone((116,104,140),mortar=False),'north',runec=A('GLOW'))),C('hood_back',[-5,14,3.5],[5,22,5],stone((116,104,140),mortar=False)),
        eyes]+lids)
    root=Group('root',[0,0,0],[Group('body',[0,0,0],ped+bust+[head])])
    def an(G):
        return std('gm_sleeping_statue',G,L=dict(activate=1.6,deactivate=1.2,active=2.0),
            idle={'eyes':hide(),'head':[('rotation',0,4,0,0,cm),('rotation',1,5,0,0,cm),('rotation',2,4,0,0,cm)]},
            activate={'lid_r':[('rotation',0,0,0,0),('rotation',.5,-85,0,0,cm)],'lid_l':[('rotation',0,0,0,0),('rotation',.5,-85,0,0,cm)],
                      'eyes':grow(.2,.6),'head':[('rotation',0,4,0,0),('rotation',.7,4,0,0),('rotation',1.6,-4,28,0,cm)]},
            active={'lid_r':[('rotation',0,-85,0,0)],'lid_l':[('rotation',0,-85,0,0)],'head':[('rotation',0,-4,28,0)],'eyes':pulse(2,.9,1.1)},
            deactivate={'lid_r':[('rotation',0,-85,0,0),('rotation',.6,0,0,0,cm)],'lid_l':[('rotation',0,-85,0,0),('rotation',.6,0,0,0,cm)],
                        'eyes':shrink(0,.4),'head':[('rotation',0,-4,28,0),('rotation',1.2,4,0,0,cm)]})
    return root, an

VARS=['fire','wind','water','earth','light','dark']
@model('gm_element_monument','원소 비석','A','*',(0.8,2.4,1.6),
       '같은 속성이 닿아야 작동하는 오벨리스크. 6속성 텍스처 변형(모양 동일).',
       'idle=문양 부유 / activate=문양 각성 / active=빠른 회전 / deactivate=잠잠해짐', variants=VARS)
def element_monument():
    base,y=plinth('plinth',12,12,2,tiers=2,paint=stone(moss=.1))
    sh=runed(stone(STONEL),'north','south','east','west',runec=A('ACC'))
    body=[box('shaft',0,4,0,6,18,6,sh),box('shaft_band',0,10,0,7,1,7,metal(BRONZE)),box('shaft_band2',0,20,0,7,1,7,metal(BRONZE)),
          box('cap1',0,22,0,8,2,8,stone()),box('cap2',0,24,0,4,2,4,stone(STONEL)),box('cap3',0,26,0,2,2,2,metal(BRONZE))]
    horns=[C(f'horn_{i}',[-0.5,21,-5],[0.5,26,-4],metal(BRONZE),rot=[-20,90*i,0],origin=[0,21,0]) for i in range(4)]
    g=glyph()
    runes=Group('runes',[0,13,0],[plane_n('rune_n',-2,11,2,19,-3.1,g),plane_n('rune_s',-2,11,2,19,3.1,g),
                                  plane_e('rune_e',-2,11,2,19,3.1,g),plane_e('rune_w',-2,11,2,19,-3.1,g)])
    emb=[]
    for i in range(8):
        a=45*i; r=math.radians(a); x,yy=4*math.cos(r),36+4*math.sin(r)
        emb.append(C(f'emb_ring_{i}',[x-0.5,yy-1.5,-0.5],[x+0.5,yy+1.5,0.5],metal((214,170,80)),rot=[0,0,a],origin=[x,yy,0]))
    emb+=[C('emb_gem',[-1.5,34.5,-1.5],[1.5,37.5,1.5],crystal(A('GEM'),a=255),rot=[0,0,45],origin=[0,36,0]),
          C('emb_gem_in',[-1,35,-2],[1,37,2],glow(),rot=[0,0,45],origin=[0,36,0])]
    emb+=[C(f'emb_spike_{i}',[-0.5,39.5,-0.5],[0.5,41.5,0.5],metal((214,170,80)),rot=[0,0,90*i],origin=[0,36,0]) for i in range(4)]
    root=Group('root',[0,0,0],[Group('obelisk',[0,0,0],base+body+horns),runes,Group('emblem',[0,36,0],emb)])
    def an(G):
        return std('gm_element_monument',G,L=dict(idle=3.0,activate=1.2,active=1.5),
            idle={'runes':hide(),'emblem':[('position',0,0,0,0,cm),('position',1.5,0,.8,0,cm),('position',3,0,0,0,cm)]+spin_n(1,3,.25)},
            activate={'runes':grow(0,.6),'emblem':[('position',0,0,0,0),('position',.6,0,3,0,cm),('position',1.2,0,2,0,cm),('rotation',0,0,0,0),('rotation',1.2,0,720,0,cm),
                       ('scale',0,1,1,1),('scale',.6,1.3,1.3,1.3,cm),('scale',1.2,1.1,1.1,1.1,cm)]},
            active={'runes':show(),'emblem':[('position',0,0,2,0,cm),('position',.75,0,2.6,0,cm),('position',1.5,0,2,0,cm),('scale',0,1.1,1.1,1.1)]+spin_n(1,1.5)},
            deactivate={'runes':shrink(0,.5),'emblem':[('position',0,0,2,0),('position',1,0,0,0,cm),('scale',0,1.1,1.1,1.1),('scale',1,1,1,1,cm)]})
    return root, an

@model('gm_switch_lever','스위치 레버','A','mana',(0.7,1.1,0.6),
       '직접 당기거나 원거리 마법으로 당기는 스위치. 켜지면 전면 표시등이 밝아진다.',
       'idle=꺼짐(뒤로) / activate=당김 / active=켜짐 / deactivate=되돌림')
def switch_lever():
    ir=metal(IRON)
    base=[box('base',0,0,0,10,4,8,runed(stone(),'north',runec=A('ACCD'))),box('base_top',0,4,0,8,1,6,ir),
          C('slot_r',[1,4,-3],[2,5.2,3],metal((50,50,56))),C('slot_l',[-2,4,-3],[-1,5.2,3],metal((50,50,56))),
          C('cheek_r',[3,4,-2],[4,8,2],metal(BRONZE)),C('cheek_l',[-4,4,-2],[-3,8,2],metal(BRONZE)),
          C('pin',[-4.5,5.5,-0.5],[4.5,6.5,0.5],metal(BRONZED))]
    lev=Group('lever',[0,6,0],[box('shaft',0,5,0,2,13,2,wood()),box('band',0,13,0,3,1,3,metal(BRONZE)),
              box('grip',0,15,0,3,4,3,wood(WOODD)),box('knob',0,19,0,4,2,4,metal(BRONZE)),box('knob_gem',0,21,0,2,1,2,crystal(A('GEM'),a=255))],[30,0,0])
    lamp=Group('lamp',[0,2,-4.1],[plane_n('lamp',-1.5,0.5,1.5,3.5,-4.1,glow()),plane_n('lamp_frame',-2.5,0,2.5,4,-4.05,frame_glow(BRONZE))])
    root=Group('root',[0,0,0],[Group('base',[0,0,0],base),lev,lamp])
    def an(G):
        return std('gm_switch_lever',G,L=dict(activate=.45,deactivate=.45),
            idle={'lamp':[('scale',0,0,0,0)]},
            activate={'lever':[('rotation',0,0,0,0),('rotation',.3,-66,0,0),('rotation',.45,-60,0,0,cm)],'lamp':grow(.25,.45)},
            active={'lever':[('rotation',0,-60,0,0)],'lamp':pulse(1.5,.96,1.04)},
            deactivate={'lever':[('rotation',0,-60,0,0),('rotation',.3,6,0,0),('rotation',.45,0,0,0,cm)],'lamp':shrink(0,.2)},
            spawn=spawn_pop(.6))
    return root, an

@model('gm_rotary_dial','회전 다이얼','A','mana',(1.0,1.0,0.5),
       '벽에 거는 다이얼. 칠 때마다 90도씩 돌아가며, 문양을 맞추면 테두리가 빛난다.',
       'pos_0~3=각 방향 고정 / turn=한 칸 회전 / activate=정답 발광')
def rotary_dial():
    back=[box('back',0,0,4,16,16,2,stone()),C('back_oct',[-8,0,3],[8,16,5],stone(),rot=[0,0,45],origin=[0,8,4])]
    syms=[]
    for i,e in enumerate(['fire','water','earth','wind']):
        a=math.radians(90*i); x,yy=6.5*math.sin(a),8+6.5*math.cos(a)
        syms.append(C(f'sym_{e}',[x-1,yy-1,2.5],[x+1,yy+1,3],crystal(ELEM[e]['GEM'],a=255),rot=[0,0,45],origin=[x,yy,2.75]))
    dial=[]
    for i in range(12):
        a=30*i; r=math.radians(a); x,yy=5*math.cos(r),8+5*math.sin(r)
        dial.append(C(f'dial_rim_{i}',[x-0.5,yy-1.5,1],[x+0.5,yy+1.5,3],metal(BRONZE),rot=[0,0,a],origin=[x,yy,2]))
    dial+=[box('dial_disc',0,4,2,8,8,1,with_faces(stone(STONEL,mortar=False),north=rune_face(A('ACCD')))),
           C('dial_disc_oct',[-4,4,1.5],[4,12,2.5],stone(STONEL,mortar=False),rot=[0,0,45],origin=[0,8,2]),
           C('arrow',[-0.5,8,0],[0.5,13,1],metal((220,180,90))),C('arrow_tip',[-1.5,12,0],[1.5,13,1],metal((220,180,90))),
           box('knob',0,6.5,-0.5,3,3,2,metal(BRONZED)),box('knob_cap',0,7,-1.5,2,2,1,crystal(A('GEM'),a=255))]
    dg=Group('dial',[0,8,2],dial)
    glo=Group('glow',[0,8,0.9],[plane_n(f'glow_ring_{k}',-6,2,6,14,0.9,frame_glow(),rot=[0,0,45*k],origin=[0,8,0.9]) for k in range(2)])
    root=Group('root',[0,0,0],[Group('plate',[0,0,0],back+syms),dg,glo])
    def an(G):
        pos=lambda a,b:{'dial':[('rotation',0,0,0,a),('rotation',.35,0,0,b-5*(1 if b<a else -1)),('rotation',.5,0,0,b,cm)]}
        return std('gm_rotary_dial',G,L=dict(activate=.8),
            idle={'glow':hide()},activate={'glow':grow(0,.8)},active={'glow':pulse(1.5,.98,1.04)},
            extra={'turn':(0.5,'once',pos(0,-90)),
                   'pos_0':(0.5,'hold',pos(90,0)),'pos_1':(0.5,'hold',pos(0,-90)),
                   'pos_2':(0.5,'hold',pos(-90,-180)),'pos_3':(0.5,'hold',pos(-180,-270))},
            spawn=spawn_pop(.6))
    return root, an
