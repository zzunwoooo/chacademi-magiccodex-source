from kit import *

VARS=['fire','wind','water','earth','light','dark']
# =============== D. 조각 / 수집형 ===============

@model('gm_element_fragment','원소 조각','D','*',(0.5,0.8,0.5),
       '던전 곳곳에 숨겨진 수집품. 떠서 회전하며, 주우면 사라진다. 6속성 텍스처 변형.',
       'idle=부유 회전 / pickup=흡수되며 사라짐',variants=VARS)
def element_fragment():
    gd=metal((214,170,80))
    body=[C('core',[-2,5,-2],[2,12,2],crystal(A('GEM'),a=245),rot=[0,45,0],origin=[0,8,0]),
          C('core_tip',[-1,12,-1],[1,14,1],crystal(A('GLOW'),a=245),rot=[0,45,0],origin=[0,8,0]),
          C('core_bot',[-1,3,-1],[1,5,1],crystal(A('GEM'),a=245),rot=[0,45,0],origin=[0,8,0]),
          C('shard_r',[2,6,-1],[4,10,1],crystal(A('GEM'),a=230),rot=[0,0,-20],origin=[3,8,0]),
          C('shard_l',[-4,7,-1],[-2,10,1],crystal(A('GEM'),a=230),rot=[0,0,20],origin=[-3,8,0]),
          C('clasp',[-2.5,7.5,-2.5],[2.5,8.5,2.5],gd,rot=[0,45,0],origin=[0,8,0]),
          C('clasp_gem',[-0.5,7.5,-3.7],[0.5,8.5,-3.4],glow())]
    frag=Group('fragment',[0,8,0],body)
    root=Group('root',[0,0,0],[frag])
    def an(G):
        n='animation.gm_element_fragment.'
        return [anim(n+'spawn',1.0,'once',{'fragment':[('scale',0,0,0,0),('scale',.6,1.3,1.3,1.3,cm),('scale',1,1,1,1,cm),('position',0,0,-4,0),('position',1,0,0,0,cm)]},G),
                anim(n+'idle',3.0,'loop',{'fragment':[('position',0,0,0,0,cm),('position',1.5,0,1.5,0,cm),('position',3,0,0,0,cm)]+spin_n(1,3)},G),
                anim(n+'pickup',0.8,'hold',{'fragment':[('position',0,0,0,0),('position',.5,0,6,0,cm),('scale',0,1,1,1),('scale',.4,1.3,1.3,1.3,cm),('scale',.8,0,0,0,cm),
                                                         ('rotation',0,0,0,0),('rotation',.8,0,540,0)]},G)]
    return root, an

@model('gm_fragment_altar','조각 받침 제단','D','mana',(1.4,1.4,1.0),
       '조각을 끼우면 해당 속성 홈이 점등된다. 6개가 모두 모이면 중앙 열쇠석이 깨어난다.',
       'insert_fire~insert_dark=각 조각 장착 / activate=완성 / active=열쇠석 회전')
def fragment_altar():
    st=stone(); stl=stone(STONEL,mortar=False)
    base=[box('base',0,0,0,22,3,22,st),C('base_oct',[-11,0,-11],[11,3,11],st,rot=[0,45,0],origin=[0,0,0]),
          box('step',0,3,0,18,2,18,with_faces(stl,up=rune_face(A('ACCD'),.2))),C('step_oct',[-9,3,-9],[9,5,9],stl,rot=[0,45,0],origin=[0,3,0]),
          box('center_post',0,5,0,6,10,6,runed(st,'north','south','east','west',runec=A('ACCD'))),box('center_cup',0,15,0,8,2,8,metal(BRONZE))]
    slots=[]
    for i,e in enumerate(VARS):
        a=math.radians(60*i); x,z=7*math.sin(a),-7*math.cos(a)
        base+=[C(f'socket_{e}',[x-1.5,5,z-1.5],[x+1.5,7,z+1.5],metal(BRONZE)),C(f'socket_ring_{e}',[x-2,5,z-2],[x+2,5.5,z+2],metal(BRONZED)),
               C(f'socket_mark_{e}',[x-1,7,z-1],[x+1,7.2,z+1],solid(ELEM[e]['ACCD']))]
        slots.append(Group(f'slot_{e}',[x,7,z],[C(f'slot_gem_{e}',[x-1,7,z-1],[x+1,11,z+1],crystal(ELEM[e]['GEM'],a=255),rot=[0,45,0],origin=[x,7,z]),
                                                C(f'slot_tip_{e}',[x-0.5,11,z-0.5],[x+0.5,12,z+0.5],crystal(ELEM[e]['GLOW'],a=255),rot=[0,45,0],origin=[x,7,z])]))
    key=Group('keystone',[0,20,0],gem('key',0,18,0,4,6,crystal((240,240,255),a=240))+
              [C('key_ring',[-3,20.5,-0.5],[3,21.5,0.5],metal((214,170,80))),C('key_ring2',[-0.5,20.5,-3],[0.5,21.5,3],metal((214,170,80)))])
    root=Group('root',[0,0,0],[Group('altar',[0,0,0],base)]+slots+[key])
    def an(G):
        hid={f'slot_{e}':hide() for e in VARS}
        on={f'slot_{e}':show() for e in VARS}
        ins={f'insert_{e}':(0.6,'hold',{f'slot_{e}':[('scale',0,0,0,0),('position',0,0,4,0),('scale',.3,1.2,1.2,1.2,cm),('position',.5,0,0,0,cm),('scale',.6,1,1,1,cm)]}) for e in VARS}
        return std('gm_fragment_altar',G,L=dict(activate=1.6,active=2.0),
            idle={**hid,'keystone':[('scale',0,.7,.7,.7),('position',0,0,-1,0)]},
            activate={**on,'keystone':[('scale',0,.7,.7,.7),('scale',1.2,1.25,1.25,1.25,cm),('scale',1.6,1,1,1,cm),('position',0,0,-1,0),('position',1.6,0,4,0,cm),
                                         ('rotation',0,0,0,0),('rotation',1.6,0,720,0,cm)]},
            active={**on,'keystone':[('position',0,0,4,0,cm),('position',1,0,5,0,cm),('position',2,0,4,0,cm)]+spin_n(1,2)},
            extra=ins)
    return root, an

@model('gm_fragment_cache','숨겨진 조각 보관함','D','*',(0.8,0.9,0.6),
       '특정 원소로만 열리는 작은 성물함. 안에 원소 조각이 들어 있다. 6속성 변형.',
       'idle=봉인 / activate=개방 / active=열림',variants=VARS)
def fragment_cache():
    st=stone(STONEL,mortar=False); acc=metal(A('ACC'))
    body=[box('base',0,0,0,12,2,12,stone()),box('box',0,2,0,10,7,10,runed(st,'north','south','east','west',runec=A('ACCD'))),
          *[box(f'pillar_{i}',sx*5,2,sz*5,2,9,2,metal(BRONZE)) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])],
          *[box(f'pillar_gem_{i}',sx*5,11,sz*5,2,1,2,crystal(A('GEM'),a=255)) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])],
          box('inner',0,9,0,8,0.0,8,stone((60,56,60),mortar=False))]
    lids=[]
    for i in range(4):
        g=Group(f'lid_{i}',[0,9,-4],[C(f'lid_{i}',[-4,9,-4],[4,10,0],st),C(f'lid_{i}_edge',[-4,10,-4],[4,11,-3],acc),
                C(f'lid_{i}_peak',[-2,10,-2],[2,11.5,0],stone(STONE,mortar=False))],[0,0,0])
        lids.append(Group(f'lid_rot_{i}',[0,9,0],[g],[0,90*i,0]))
    seal=Group('seal',[0,12,0],[C('seal_gem',[-1.5,11,-1.5],[1.5,14,1.5],crystal(A('GEM'),a=255),rot=[0,45,0],origin=[0,12,0])])
    frag=Group('fragment',[0,9,0],[C('frag',[-1,7,-1],[1,12,1],crystal(A('GEM'),a=255),rot=[0,45,0],origin=[0,9,0]),
                                    C('frag_tip',[-0.5,12,-0.5],[0.5,13,0.5],crystal(A('GLOW'),a=255),rot=[0,45,0],origin=[0,9,0])])
    root=Group('root',[0,0,0],[Group('body',[0,0,0],body),frag]+lids+[seal])
    def an(G):
        op={f'lid_{i}':[('rotation',0,0,0,0),('rotation',.3+.1*i,0,0,0),('rotation',.9+.1*i,100,0,0,cm)] for i in range(4)}
        return std('gm_fragment_cache',G,L=dict(activate=1.6),
            idle={'fragment':[('position',0,0,-4,0)]},
            activate={**op,'seal':[('scale',0,1,1,1),('scale',.3,1.3,1.3,1.3,cm),('scale',.5,0,0,0,cm)],
                      'fragment':[('position',0,0,-4,0),('position',.9,0,-4,0),('position',1.6,0,3,0,cm)]},
            active={**{f'lid_{i}':[('rotation',0,100,0,0)] for i in range(4)},'seal':hide(),
                    'fragment':[('position',0,0,3,0,cm),('position',1,0,4,0,cm),('position',2,0,3,0,cm)]+spin_n(1,2)})
    return root, an

@model('gm_broken_tablet','부서진 석판','D','mana',(1.0,1.6,1.2),
       '흩어진 석판 조각 4개를 모아 맞추면 다음 퍼즐의 힌트 문양이 드러난다.',
       'place_1~4=조각 끼움 / activate=완성 발광 / active=문양 유지')
def broken_tablet():
    st=stone(); stl=stone(STONEL,mortar=False)
    fr=[box('base',0,0,0,18,3,8,st),box('base2',0,3,0,16,1,6,stl),
        C('frame_r',[7,4,-2],[9,26,2],metal(BRONZE)),C('frame_l',[-9,4,-2],[-7,26,2],metal(BRONZE)),
        C('frame_t',[-9,26,-2],[9,28,2],metal(BRONZE)),C('frame_b',[-7,4,-1],[7,5,1],metal(BRONZED)),
        C('back',[-7,5,0.5],[7,26,1.5],stone(STONED,mortar=False)),
        C('crest',[-3,28,-1],[3,30,1],metal((214,170,80))),C('crest_gem',[-1,28.5,-1.6],[1,30.5,-1],crystal(A('GEM'),a=255))]
    pcs=[]
    rects=[(-7,15.5,0,26),(0,15.5,7,26),(-7,5,0,15.5),(0,5,7,15.5)]
    for i,(x1,y1,x2,y2) in enumerate(rects):
        yy1,yy2=round(y1),round(y2)
        pcs.append(Group(f'piece_{i+1}',[(x1+x2)/2,(yy1+yy2)/2,-0.5],[C(f'piece_{i+1}',[x1,yy1,-0.5],[x2,yy2,0.5],with_faces(stl,north=rune_face(A('ACCD'),.5))),
                     C(f'piece_{i+1}_chip',[x1+1,yy1+1,-0.8],[x1+3,yy1+2,-0.5],stone(STONE,mortar=False))]))
    glo=Group('glow',[0,15.5,-0.6],[plane_n('glyph',-6,6,6,25,-0.7,glyph()),plane_n('glyph_frame',-7,5,7,26,-0.65,frame_glow())])
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],fr)]+pcs+[glo])
    def an(G):
        hid={f'piece_{i}':hide() for i in range(1,5)}; on={f'piece_{i}':show() for i in range(1,5)}
        pl={f'place_{i}':(0.6,'hold',{f'piece_{i}':[('scale',0,0,0,0),('position',0,0,0,-6),('scale',.3,1,1,1,cm),('position',.6,0,0,0,cm)]}) for i in range(1,5)}
        return std('gm_broken_tablet',G,L=dict(activate=1.0),
            idle={**hid,'glow':hide()},activate={**on,'glow':grow(0,1.0)},active={**on,'glow':pulse(2,.98,1.03)},extra=pl)
    return root, an

@model('gm_spirit_lantern','영혼 등불','D','fire',(0.5,0.9,0.6),
       '들고 옮겨야 하는 불씨. 물에 닿거나 시간이 지나면 꺼진다(손에 드는 아이템 겸용).',
       'idle=타오름 / extinguish=꺼짐 / relight=재점화')
def spirit_lantern():
    br=metal(BRONZE); brd=metal(BRONZED)
    frame=[box('base',0,0,0,8,2,8,br),box('base_foot',0,0,0,6,0.0,6,brd),
           *[box(f'post_{i}',sx*3.5,2,sz*3.5,1,8,1,brd) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])],
           box('cap',0,10,0,8,1,8,br),box('cap2',0,11,0,6,1,6,br),box('cap3',0,12,0,4,1,4,brd),
           C('handle_r',[2,13,-0.5],[3,16,0.5],metal(IRON)),C('handle_l',[-3,13,-0.5],[-2,16,0.5],metal(IRON)),C('handle_t',[-3,16,-0.5],[3,17,0.5],metal(IRON)),
           box('wick_cup',0,2,0,3,1,3,brd),box('wick',0,3,0,1,1,1,solid((40,40,40)))]
    gl=glow((255,220,170),a=70)
    glass=[plane_n('glass_n',-3,2,3,10,-3,gl),plane_n('glass_s',-3,2,3,10,3,gl),plane_e('glass_e',-3,2,3,10,3,gl),plane_e('glass_w',-3,2,3,10,-3,gl)]
    fl=flame((255,140,60),(255,250,200))
    flame_g=Group('flame',[0,4,0],[plane_n('fl_a',-1.5,4,1.5,9,0,fl),plane_n('fl_b',-1.5,4,1.5,9,0,fl,rot=[0,90,0],origin=[0,4,0]),
                                    box('fl_core',0,4,0,1,2,1,glow((255,240,200)))])
    root=Group('root',[0,0,0],[Group('lantern',[0,0,0],frame+glass+[flame_g])])
    def an(G):
        n='animation.gm_spirit_lantern.'
        return [anim(n+'spawn',.8,'once',spawn_pop(.8),G),
                anim(n+'idle',1.2,'loop',{'flame':[('scale',0,1,1,1,cm),('scale',.3,1.1,1.25,1.1,cm),('scale',.7,.92,.9,.92,cm),('scale',1.2,1,1,1,cm),
                                                     ('rotation',0,0,0,0,cm),('rotation',.6,0,20,0,cm),('rotation',1.2,0,0,0,cm)],
                                           'lantern':[('rotation',0,0,0,-2,cm),('rotation',.6,0,0,2,cm),('rotation',1.2,0,0,-2,cm)]},G),
                anim(n+'extinguish',.8,'hold',{'flame':[('scale',0,1,1,1),('scale',.2,1.2,1.4,1.2,cm),('scale',.8,0,0,0,cm)]},G),
                anim(n+'relight',.8,'hold',{'flame':grow(0,.8)},G)]
    return root, an

# =============== E. 협동 전용 ===============

@model('gm_twin_resonance','쌍둥이 공명석','E','mana',(0.8,2.0,1.5),
       '멀리 떨어진 두 개를 거의 동시에(1초 안) 쳐야 작동한다. 한 개만 맞으면 잠깐 떨리다 멈춘다.',
       'idle=잠잠 / hit=한쪽만 맞음(떨림) / activate=공명 성공 / active=공명 유지')
def twin_resonance():
    base,y=plinth('plinth',12,12,2,tiers=2,paint=stone(moss=.1))
    fork=[box('fork_base',0,4,0,8,3,6,runed(stone(STONEL),'north','south',runec=A('ACCD'))),box('fork_bridge',0,7,0,10,2,4,metal(BRONZE))]
    prongs=[]
    for s in (1,-1):
        prongs.append(Group(f'prong_{"r" if s>0 else "l"}',[s*4,9,0],[C(f'prong_{s}',[s*4-1.5,9,-1.5],[s*4+1.5,27,1.5],crystal(A('GEM'),a=230)),
                     C(f'prong_tip_{s}',[s*4-1,27,-1],[s*4+1,30,1],crystal(A('GLOW'),a=240)),
                     C(f'prong_band_{s}',[s*4-2,14,-2],[s*4+2,15,2],metal((214,170,80))),C(f'prong_band2_{s}',[s*4-2,21,-2],[s*4+2,22,2],metal((214,170,80)))]))
    orb=Group('orb',[0,18,0],[C('orb',[-1.5,16.5,-1.5],[1.5,19.5,1.5],crystal((255,255,255),a=240),rot=[45,45,0],origin=[0,18,0]),
                             C('orb_in',[-1,17,-1],[1,19,1],glow(),rot=[45,45,0],origin=[0,18,0])])
    root=Group('root',[0,0,0],[Group('stand',[0,0,0],base+fork)]+prongs+[orb])
    def an(G):
        vib=lambda L,a,n:[('rotation',L*i/n,0,0,(a if i%2 else -a)*(1-i/n)) for i in range(n+1)]
        return std('gm_twin_resonance',G,L=dict(activate=1.2,active=.6),
            idle={'orb':[('scale',0,.5,.5,.5),('position',0,0,-1,0)]},
            activate={'prong_r':vib(1.2,4,16),'prong_l':[(c,t,x,y,-z) for c,t,x,y,z in vib(1.2,4,16)],
                      'orb':[('scale',0,.5,.5,.5),('scale',.8,1.3,1.3,1.3,cm),('scale',1.2,1,1,1,cm),('position',0,0,-1,0),('position',1.2,0,1,0,cm)]},
            active={'prong_r':[('rotation',0,0,0,-1),('rotation',.15,0,0,1),('rotation',.3,0,0,-1),('rotation',.45,0,0,1),('rotation',.6,0,0,-1)],
                    'prong_l':[('rotation',0,0,0,1),('rotation',.15,0,0,-1),('rotation',.3,0,0,1),('rotation',.45,0,0,-1),('rotation',.6,0,0,1)],
                    'orb':[('position',0,0,1,0)]+spin_n(1,.6)},
            extra={'hit':(0.8,'once',{'prong_r':vib(.8,3,10),'prong_l':[(c,t,x,y,-z) for c,t,x,y,z in vib(.8,3,10)]})})
    return root, an

@model('gm_steam_valve','증기 밸브','E','mana',(1.0,2.0,1.4),
       '불(왼쪽)과 물(오른쪽)이 동시에 들어가야 증기 압력이 차서 작동한다. 두 속성 협동.',
       'fire_in / water_in=각 입력 점등 / activate=압력 최대·밸브 회전 / active=압력 유지 / deactivate=배출')
def steam_valve():
    ir=metal(IRON); br=metal(BRONZE)
    body=[box('foot',0,0,0,14,2,14,stone()),box('tank',0,2,0,12,18,12,metal((120,96,80))),C('tank_oct',[-6,2,-6],[6,20,6],metal((120,96,80)),rot=[0,45,0],origin=[0,2,0]),
          box('tank_band1',0,5,0,13,1,13,br),box('tank_band2',0,16,0,13,1,13,br),box('dome',0,20,0,10,2,10,br),box('dome2',0,22,0,6,2,6,br),
          box('chimney',0,24,0,3,6,3,ir)]
    body+=[C('pipe_fire',[-12,6,-1.5],[-6,9,1.5],ir),C('pipe_fire_flange',[-13,5,-2],[-12,10,2],metal((180,70,40))),
           C('pipe_water',[6,6,-1.5],[12,9,1.5],ir),C('pipe_water_flange',[12,5,-2],[13,10,2],metal((60,110,190)))]
    fl=Group('fire_lamp',[-9,11,0],[box('fire_lamp',-9,9,0,2,2,2,glow(ELEM['fire']['GLOW']))])
    wl=Group('water_lamp',[9,11,0],[box('water_lamp',9,9,0,2,2,2,glow(ELEM['water']['GLOW']))])
    body+=[box('fire_lamp_dim',-9,9,0,1,1,1,solid((90,40,30))),box('water_lamp_dim',9,9,0,1,1,1,solid((30,50,90)))]
    gauge=[C('gauge_rim',[-3,10,-7],[3,16,-6],br),C('gauge_face',[-2.5,10.5,-7.1],[2.5,15.5,-7.1],solid((230,224,200)))]
    needle=Group('needle',[0,13,-7.3],[C('needle',[-0.5,13,-7.4],[0.5,15.5,-7.3],solid((180,40,40)))],[0,0,60])
    wheel=Group('wheel',[0,22,-6],[*[c for c in ring('wheel_rim',0,22,-6,3,8,1,1,1,metal((180,60,50)),plane='xy')],
            C('wheel_spoke_a',[-3,21.5,-6.5],[3,22.5,-5.5],ir),C('wheel_spoke_b',[-0.5,19,-6.5],[0.5,25,-5.5],ir)])
    body+=[C('wheel_stem',[-0.5,21.5,-6],[0.5,22.5,-4],ir)]
    cap=Group('chimney_cap',[0,30,0],[box('chimney_cap',0,30,0,5,1,5,br),box('chimney_cap2',0,31,0,3,1,3,br)])
    root=Group('root',[0,0,0],[Group('boiler',[0,0,0],body+gauge),fl,wl,needle,wheel,cap])
    def an(G):
        return std('gm_steam_valve',G,L=dict(activate=1.5,active=.6,deactivate=1.5),
            idle={'fire_lamp':hide(),'water_lamp':hide()},
            activate={'fire_lamp':show(),'water_lamp':show(),'needle':[('rotation',0,0,0,0),('rotation',1,0,0,-120,cm),('rotation',1.5,0,0,-115,cm)],
                      'wheel':[('rotation',0,0,0,0),('rotation',1.5,0,0,-360,cm)],
                      'chimney_cap':[('position',0,0,0,0),('position',1.1,0,0,0),('position',1.3,0,3,0,cm),('position',1.5,0,2,0,cm)]},
            active={'fire_lamp':show(),'water_lamp':show(),'needle':[('rotation',0,0,0,-115),('rotation',.15,0,0,-120),('rotation',.3,0,0,-113),('rotation',.45,0,0,-119),('rotation',.6,0,0,-115)],
                    'chimney_cap':[('position',0,0,2,0),('position',.3,0,2.6,0),('position',.6,0,2,0)]},
            deactivate={'fire_lamp':shrink(0,.3),'water_lamp':shrink(0,.3),'needle':[('rotation',0,0,0,-115),('rotation',1.5,0,0,0,cm)],
                        'chimney_cap':[('position',0,0,2,0),('position',.6,0,0,0,cm)]},
            extra={'fire_in':(0.6,'hold',{'fire_lamp':grow(0,.4),'needle':[('rotation',0,0,0,0),('rotation',.6,0,0,-40,cm)]}),
                   'water_in':(0.6,'hold',{'water_lamp':grow(0,.4),'needle':[('rotation',0,0,0,0),('rotation',.6,0,0,-40,cm)]})})
    return root, an

@model('gm_balance_scale','천칭 저울','E','earth',(1.0,3.0,2.6),
       '양쪽 접시에 한 명씩 올라가 균형을 맞춰야 작동한다. 접시는 3블록 간격.',
       'idle=균형(빈 상태) / tilt_left·tilt_right=한쪽으로 기울어짐 / activate=균형 성공 발광')
def balance_scale():
    st=stone(); br=metal(BRONZE)
    base=[box('base',0,0,0,14,3,14,st),box('base2',0,3,0,10,2,10,stone(STONEL)),box('column',0,5,0,6,30,6,runed(st,'north','south',runec=A('ACCD'))),
          box('column_cap',0,35,0,8,3,8,br),box('pivot_block',0,38,0,4,4,6,br)]
    glo=Group('glow',[0,20,-3.1],[plane_n('col_glyph',-2,8,2,32,-3.1,glyph())])
    beam=[C('beam',[-26,40,-1.5],[26,43,1.5],wood()),C('beam_band_r',[10,39.5,-2],[12,43.5,2],br),C('beam_band_l',[-12,39.5,-2],[-10,43.5,2],br),
          C('beam_end_r',[24,39,-2],[27,44,2],br),C('beam_end_l',[-27,39,-2],[-24,44,2],br),C('pointer',[-0.5,43,-0.5],[0.5,50,0.5],metal((214,170,80)))]
    pans=[]
    for s,nm in ((1,'pan_r'),(-1,'pan_l')):
        x=s*25
        pans.append(Group(nm,[x,40,0],[C(f'{nm}_chain_a',[x-0.5,24,-0.5],[x+0.5,40,0.5],metal(IRON),rot=[0,0,14],origin=[x,40,0]),
            C(f'{nm}_chain_b',[x-0.5,24,-0.5],[x+0.5,40,0.5],metal(IRON),rot=[0,0,-14],origin=[x,40,0]),
            C(f'{nm}_chain_c',[x-0.5,24,-0.5],[x+0.5,40,0.5],metal(IRON),rot=[14,0,0],origin=[x,40,0]),
            C(f'{nm}_chain_d',[x-0.5,24,-0.5],[x+0.5,40,0.5],metal(IRON),rot=[-14,0,0],origin=[x,40,0]),
            C(f'{nm}_plate',[x-8,23,-8],[x+8,25,8],with_faces(stone(STONEL,mortar=False),up=rune_face(A('ACCD'),.25))),
            C(f'{nm}_rim',[x-8.5,22,-8.5],[x+8.5,23,8.5],br)]))
    beam_g=Group('beam',[0,41,0],beam+pans)
    root=Group('root',[0,0,0],[Group('stand',[0,0,0],base),glo,beam_g])
    def an(G):
        t=lambda a:{'beam':[('rotation',0,0,0,0),('rotation',1,0,0,a,cm)],'pan_r':[('rotation',0,0,0,0),('rotation',1,0,0,-a,cm)],'pan_l':[('rotation',0,0,0,0),('rotation',1,0,0,-a,cm)]}
        return std('gm_balance_scale',G,L=dict(activate=1.0,idle=3.0),
            idle={'glow':hide(),'beam':[('rotation',0,0,0,-1,cm),('rotation',1.5,0,0,1,cm),('rotation',3,0,0,-1,cm)],
                  'pan_r':[('rotation',0,0,0,1,cm),('rotation',1.5,0,0,-1,cm),('rotation',3,0,0,1,cm)],'pan_l':[('rotation',0,0,0,1,cm),('rotation',1.5,0,0,-1,cm),('rotation',3,0,0,1,cm)]},
            activate={'glow':grow(0,1.0)},active={'glow':pulse(2,.98,1.03)},
            extra={'tilt_right':(1.0,'hold',t(-14)),'tilt_left':(1.0,'hold',t(14))})
    return root, an

@model('gm_chain_pillar','연결 사슬 기둥','E','mana',(0.8,1.8,1.3),
       '두 플레이어가 각자 기둥의 손잡이를 잡고 있는 동안만 둘 사이에 빛의 다리가 생긴다(쌍 배치).',
       'idle=대기 / activate=잡음 / active=연결 유지 / deactivate=놓음')
def chain_pillar():
    st=stone()
    base,y=plinth('plinth',12,12,2,tiers=2)
    col_=[box('column',0,4,0,6,20,6,runed(st,'north','south','east','west',runec=A('ACCD'))),box('cap',0,24,0,8,2,8,metal(BRONZE))]
    ringg=Group('anchor',[0,28,0],[*ring('anchor_ring',0,28,0,3,8,1,1,1,metal((214,170,80)),plane='xy'),
                C('anchor_gem',[-1,27,-1],[1,29,1],crystal(A('GEM'),a=255),rot=[0,0,45],origin=[0,28,0])])
    col_+=[box('anchor_post',0,26,0,2,0.0,2,metal(IRON))]
    handle=Group('handle',[0,14,-3],[C('handle_arm_t',[-0.5,17,-6],[0.5,18,-3],metal(IRON)),C('handle_arm_b',[-0.5,11,-6],[0.5,12,-3],metal(IRON)),
                 C('handle_grip',[-1,11,-7],[1,18,-5],wood(WOODD)),C('handle_band',[-1.5,14,-7.5],[1.5,15,-4.5],metal(BRONZE))])
    lamp=Group('lamp',[0,22,-3.2],[plane_n('lamp',-2,19,2,23,-3.2,glyph())])
    root=Group('root',[0,0,0],[Group('pillar',[0,0,0],base+col_),ringg,handle,lamp])
    def an(G):
        return std('gm_chain_pillar',G,L=dict(activate=.6,active=1.0,deactivate=.6),
            idle={'lamp':hide(),'anchor':[('rotation',0,0,0,0),('rotation',2,0,90,0)]},
            activate={'handle':[('rotation',0,0,0,0),('rotation',.4,25,0,0,cm)],'lamp':grow(.2,.6),'anchor':[('rotation',0,0,0,0),('rotation',.6,0,180,0,cm)]},
            active={'handle':[('rotation',0,25,0,0)],'lamp':pulse(1.0,.95,1.05),'anchor':spin_n(1,1.0)},
            deactivate={'handle':[('rotation',0,25,0,0),('rotation',.4,0,0,0,cm)],'lamp':shrink(0,.4)})
    return root, an

@model('gm_mirror_cage','거울 감옥','E','light',(1.2,2.2,1.6),
       '한 명이 갇히고, 위의 광원 수정을 밖의 동료가 거울로 받아 맞혀 주면 문이 열린다.',
       'idle=잠김 / activate=빛 받음→문 개방 / active=열림 / deactivate=다시 잠김')
def mirror_cage():
    br=metal(BRONZE); ir=metal(IRON)
    base=[box('floor',0,0,0,22,2,22,stone()),C('floor_oct',[-11,0,-11],[11,2,11],stone(),rot=[0,45,0],origin=[0,0,0]),
          box('floor_rune',0,2,0,14,0.0,14,with_faces(stone(STONEL,mortar=False),up=rune_face(A('ACCD'),.3)))]
    bars=[]; door=[]
    for i in range(12):
        a=30*i; r=math.radians(a); x,z=9*math.sin(r),-9*math.cos(r)
        c=C(f'bar_{i}',[x-0.5,2,z-0.5],[x+0.5,30,z+0.5],ir)
        (door if i in (11,0,1) else bars).append(c)
    top=[*ring('top_ring',0,30,0,9,12,1,1,2,br,plane='xz'),*ring('mid_ring',0,16,0,9,12,1,1,1,metal(BRONZED),plane='xz'),
         box('dome',0,32,0,12,2,12,br),box('dome2',0,34,0,6,2,6,br)]
    em=Group('emitter',[0,36,0],[C('em_cradle',[-2,36,-2],[2,37,2],metal((214,170,80))),
           C('em_crystal',[-1.5,37,-1.5],[1.5,43,1.5],crystal(A('GEM'),a=240),rot=[0,45,0],origin=[0,37,0]),
           C('em_tip',[-0.5,43,-0.5],[0.5,45,0.5],crystal(A('GLOW'),a=255),rot=[0,45,0],origin=[0,37,0])])
    dg=Group('door',[0,2,-9],door+[C('door_lock',[-2,14,-10],[2,18,-9.5],br)])
    root=Group('root',[0,0,0],[Group('cage',[0,0,0],base+bars+top),dg,em])
    def an(G):
        return std('gm_mirror_cage',G,L=dict(activate=1.6,deactivate=1.0),
            idle={'emitter':[('rotation',0,0,0,0),('rotation',2,0,90,0)]},
            activate={'emitter':[('scale',0,1,1,1),('scale',.5,1.3,1.3,1.3,cm),('scale',.8,1,1,1,cm)],'door':[('position',0,0,0,0),('position',.6,0,0,0),('position',1.6,0,-26,0,cm)]},
            active={'door':[('position',0,0,-26,0)],'emitter':spin_n(1,2)},
            deactivate={'door':[('position',0,0,-26,0),('position',1,0,0,0,cm)]})
    return root, an

@model('gm_timed_door','시간차 문 (내리닫이)','E','mana',(2.0,3.2,2.0),
       '한 명이 압력판을 누르는 동안만 열리고(창살이 바닥으로 내려감), 떼면 모래시계가 뒤집히며 천천히 닫힌다.',
       'idle=닫힘 / activate=창살 하강·모래시계 뒤집힘 / active=열림 / deactivate=천천히 닫힘')
def timed_door():
    st=stone(); br=metal(BRONZE); ir=metal(IRON)
    fr=[C('post_r',[12,0,-4],[16,44,4],runed(st,'north',runec=A('ACCD'))),C('post_l',[-16,0,-4],[-12,44,4],runed(st,'north',runec=A('ACCD'))),
        C('housing',[-17,44,-5],[17,52,5],st),C('housing_trim',[-17,43,-5.5],[17,44,5.5],br),C('sill',[-12,0,-2],[12,1,2],stone(STONED))]
    gate=Group('gate',[0,0,0],[*[C(f'gbar_v{i}',[-11+i*3.6-0.5,1,-0.5],[-11+i*3.6+0.5,44,0.5],ir) for i in range(7)],
                              *[C(f'gbar_h{i}',[-12,8+i*10,-1],[12,9+i*10,1],ir) for i in range(4)],
                              *[C(f'gspike_{i}',[-11+i*3.6-0.5,0,-0.5],[-11+i*3.6+0.5,1,0.5],metal((200,200,210))) for i in range(7)]])
    hg=Group('hourglass',[0,58,-1],[box('hg_top',0,63,-1,8,1,6,wood(WOODD)),box('hg_bot',0,52,-1,8,1,6,wood(WOODD)),
             *[C(f'hg_post_{i}',[sx*3.5-0.5,53,-1+sz*2.5-0.5],[sx*3.5+0.5,63,-1+sz*2.5+0.5],br) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])],
             C('hg_glass_t',[-2.5,59,-3.5],[2.5,62,1.5],glow((220,235,240),a=80)),C('hg_glass_b',[-2.5,53,-3.5],[2.5,56,1.5],glow((220,235,240),a=80)),
             C('hg_neck',[-0.5,56,-1.5],[0.5,59,-0.5],glow((220,235,240),a=80)),
             C('sand_t',[-2,59,-3],[2,61,1],solid((230,200,120))),C('sand_b',[-2,53,-3],[2,54,1],solid((230,200,120)))])
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],fr),gate,hg])
    def an(G):
        return std('gm_timed_door',G,L=dict(activate=1.2,deactivate=4.0),
            idle={},
            activate={'gate':[('position',0,0,0,0),('position',1.2,0,-43,0,cm)],'hourglass':[('rotation',0,0,0,0),('rotation',.8,0,0,180,cm)]},
            active={'gate':[('position',0,0,-43,0)],'hourglass':[('rotation',0,0,0,180)]},
            deactivate={'gate':[('position',0,0,-43,0),('position',4,0,0,0,'linear')],'hourglass':[('rotation',0,0,0,180),('rotation',.6,0,0,360,cm)]})
    return root, an
