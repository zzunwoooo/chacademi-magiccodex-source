from kit import *

# =============== B. 연결 장치 (마력 회로) ===============

def conduit_run(tag, axis, a0, a1, y=3):
    """pipe segment along axis ('x' or 'z') from a0 to a1, centered at y"""
    def bx(name, lo, hi, w, h, paint):
        if axis=='z': return C(name,[-w/2,y-h/2,lo],[w/2,y+h/2,hi],paint)
        return C(name,[lo,y-h/2,-w/2],[hi,y+h/2,w/2],paint)
    L=a1-a0
    parts=[]
    # 4 corner rails
    for i,(u,v) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)]):
        if axis=='z': parts.append(C(f'{tag}_rail_{i}',[u*2-(1 if u>0 else 0),y+v*2-(1 if v>0 else 0),a0],[u*2+(0 if u>0 else 1),y+v*2+(0 if v>0 else 1),a1],metal(BRONZE)))
        else: parts.append(C(f'{tag}_rail_{i}',[a0,y+v*2-(1 if v>0 else 0),u*2-(1 if u>0 else 0)],[a1,y+v*2+(0 if v>0 else 1),u*2+(0 if u>0 else 1)],metal(BRONZE)))
    gl=glow((190,220,240),a=70)
    if axis=='z':
        parts+=[C(f'{tag}_glass_e',[1.9,y-1,a0],[1.9,y+1,a1],gl),C(f'{tag}_glass_w',[-1.9,y-1,a0],[-1.9,y+1,a1],gl),
                C(f'{tag}_glass_u',[-1,y+1.9,a0],[1,y+1.9,a1],gl)]
    else:
        parts+=[C(f'{tag}_glass_n',[a0,y-1,-1.9],[a1,y+1,-1.9],gl),C(f'{tag}_glass_s',[a0,y-1,1.9],[a1,y+1,1.9],gl),
                C(f'{tag}_glass_u',[a0,y+1.9,-1],[a1,y+1.9,1],gl)]
    parts.append(bx(f'{tag}_core_dim',a0,a1,2,2,solid(A('ACCD'),.05)))
    n=max(1,int(abs(L)//6))
    for k in range(n):
        c=a0+(k+.5)*L/n
        parts.append(bx(f'{tag}_band_{k}',c-.5,c+.5,5,5,metal(BRONZED)))
    if axis=='z': parts.append(C(f'{tag}_mount',[-3,0,a0+L/2-2],[3,1,a0+L/2+2],stone()))
    else: parts.append(C(f'{tag}_mount',[a0+L/2-2,0,-3],[a0+L/2+2,1,3],stone()))
    core=bx(f'{tag}_core',a0,a1,2,2,glow())
    core.inflate=0.05
    return parts, core

def conduit_model(id_, ko, runs, junction):
    @model(id_,ko,'B','mana',(0.5,0.4,0.2),
           '장치 사이에 마력을 전달하는 도관. 마력이 흐르면 내부 코어가 빛나며 맥동한다.',
           'idle=비활성 / activate=충전 / active=맥동 / deactivate=소멸')
    def build():
        parts=[]; cores=[]
        for t,ax,a0,a1 in runs:
            p,c=conduit_run(t,ax,a0,a1); parts+=p; cores.append(c)
        if junction:
            parts+=[box('junction',0,0,0,6,6,6,runed(metal(BRONZE),'north','east','up',runec=A('GLOW'))),box('junction_cap',0,6,0,4,1,4,metal(BRONZED))]
            cores.append(box('junction_gem',0,6.5,0,2,1,2,glow()))
        root=Group('root',[0,0,0],[Group('pipe',[0,0,0],parts),Group('core',[0,3,0],cores)])
        def an(G):
            return std(id_,G,L=dict(activate=.6,active=1.0,deactivate=.5),
                idle={'core':hide()},
                activate={'core':[('scale',0,1,0,1),('scale',.3,1,1.4,1),('scale',.6,1,1,1,cm)]},
                active={'core':[('scale',0,1,1,1,cm),('scale',.5,1,1.35,1,cm),('scale',1,1,1,1,cm)]},
                deactivate={'core':shrink(0,.5)},spawn=spawn_pop(.6))
        return root, an
    return build

conduit_model('gm_conduit_straight','마력 도관 (직선)',[('a','z',-8,8)],False)
conduit_model('gm_conduit_corner','마력 도관 (코너)',[('a','z',-8,-3),('b','x',3,8)],True)
conduit_model('gm_conduit_tee','마력 도관 (분기)',[('a','z',-8,-3),('b','z',3,8),('c','x',3,8)],True)

@model('gm_mana_capacitor','마력 축전기','B','mana',(0.9,1.9,1.4),
       '마력을 받으면 일정 시간 충전 상태를 유지한다. 시간차 퍼즐용.',
       'idle=방전 / activate=충전 / active=충전 유지(맥동) / deactivate=서서히 방전')
def mana_capacitor():
    base,y=plinth('plinth',14,14,2,tiers=2)
    gl=glow((190,220,240),a=60)
    tank=[box('tank_bottom',0,4,0,12,2,12,metal(BRONZE)),C('tank_bottom_oct',[-6,4,-6],[6,6,6],metal(BRONZE),rot=[0,45,0],origin=[0,4,0]),
          box('tank_top',0,22,0,12,2,12,metal(BRONZE)),C('tank_top_oct',[-6,22,-6],[6,24,6],metal(BRONZE),rot=[0,45,0],origin=[0,22,0]),
          box('tank_cap',0,24,0,6,2,6,metal(BRONZED)),box('terminal',0,26,0,2,3,2,metal(IRON)),box('terminal_tip',0,29,0,3,1,3,crystal(A('GEM'),a=255)),
          box('glass',0,6,0,9,16,9,gl),C('glass_oct',[-4.5,6,-4.5],[4.5,22,4.5],gl,rot=[0,45,0],origin=[0,6,0])]
    tank+=[C(f'rail_{i}',[-0.5,6,-5.5],[0.5,22,-4.5],metal(BRONZED),rot=[0,90*i+45,0],origin=[0,6,0]) for i in range(4)]
    tank+=ring('coil_a',0,10,0,6,8,1,1,1,metal((200,120,60)),plane='xz')+ring('coil_b',0,17,0,6,8,1,1,1,metal((200,120,60)),plane='xz')
    tank+=[C('jack_in',[6,8,-1.5],[9,11,1.5],metal(BRONZE)),C('jack_out',[-9,8,-1.5],[-6,11,1.5],metal(BRONZE))]
    core=Group('core',[0,6,0],[box('core',0,6,0,4,16,4,glow()),C('core_twist',[-2,8,-2],[2,20,2],crystal(A('GEM'),a=200),rot=[0,45,0],origin=[0,6,0])])
    dim=box('core_dim',0,6,0,2,16,2,solid(A('ACCD')))
    root=Group('root',[0,0,0],[Group('body',[0,0,0],base+tank+[dim]),core])
    def an(G):
        return std('gm_mana_capacitor',G,L=dict(activate=1.2,active=1.5,deactivate=3.0),
            idle={'core':[('scale',0,1,0,1)]},
            activate={'core':[('scale',0,1,0,1),('scale',1.0,1.15,1.05,1.15,cm),('scale',1.2,1,1,1,cm)]},
            active={'core':[('scale',0,1,1,1,cm),('scale',.75,1.12,1,1.12,cm),('scale',1.5,1,1,1,cm)]+spin_n(1,1.5)},
            deactivate={'core':[('scale',0,1,1,1),('scale',3,1,0,1,'linear')]})
    return root, an

@model('gm_logic_altar','논리 받침대 (AND)','B','mana',(1.0,1.6,1.2),
       '연결된 입력 3개가 모두 켜져야 중앙 수정이 깨어나 출력한다. 협동 퍼즐의 핵심.',
       'in_1~in_3=각 입력 점등 / activate=전부 켜짐→출력 / active=출력 유지 / deactivate=소등')
def logic_altar():
    base=[box('base',0,0,0,16,3,16,stone()),C('base_oct',[-8,0,-8],[8,3,8],stone(),rot=[0,45,0],origin=[0,0,0]),
          box('top',0,3,0,13,2,13,with_faces(stone(STONEL,mortar=False),up=rune_face(A('ACCD'),.3))),C('top_oct',[-6.5,3,-6.5],[6.5,5,6.5],stone(STONEL,mortar=False),rot=[0,45,0],origin=[0,3,0])]
    center=[box('c_pillar',0,5,0,4,12,4,runed(stone(STONEL),'north','south',runec=A('ACCD'))),box('c_cup',0,17,0,6,2,6,metal(BRONZE))]
    ins=[]
    for i in range(3):
        a=120*i
        r=math.radians(a); x,z=5.5*math.sin(r),-5.5*math.cos(r)
        center+= [C(f'in_post_{i}',[x-1,5,z-1],[x+1,10,z+1],metal(BRONZE)),C(f'in_cup_{i}',[x-1.5,10,z-1.5],[x+1.5,11,z+1.5],metal(BRONZED)),
                  C(f'in_gem_dim_{i}',[x-1,11,z-1],[x+1,13,z+1],solid(A('ACCD')),rot=[0,45,0],origin=[x,11,z]),
                  C(f'in_link_{i}',[x*0.45-0.5,5,z*0.45-0.5],[x*0.45+0.5,5.3,z*0.45+0.5],metal(BRONZED))]
        ins.append(Group(f'in_{i+1}',[x,12,z],[C(f'in_gem_{i}',[x-1.5,11,z-1.5],[x+1.5,14,z+1.5],crystal(A('GEM'),a=255),rot=[0,45,0],origin=[x,11,z])]))
    out=Group('output',[0,22,0],gem('out_gem',0,19,0,4,6,crystal(A('GLOW'),a=240)))
    root=Group('root',[0,0,0],[Group('altar',[0,0,0],base+center)]+ins+[out])
    def an(G):
        hid={f'in_{i}':hide() for i in (1,2,3)}
        on={f'in_{i}':show() for i in (1,2,3)}
        return std('gm_logic_altar',G,L=dict(activate=1.2,active=2.0),
            idle={**hid,'output':[('scale',0,.6,.6,.6),('position',0,0,-2,0)]},
            activate={**on,'output':[('scale',0,.6,.6,.6),('scale',.8,1.2,1.2,1.2,cm),('scale',1.2,1,1,1,cm),
                                     ('position',0,0,-2,0),('position',1.2,0,2,0,cm),('rotation',0,0,0,0),('rotation',1.2,0,360,0,cm)]},
            active={**on,'output':[('position',0,0,2,0,cm),('position',1,0,2.8,0,cm),('position',2,0,2,0,cm)]+spin_n(1,2)},
            deactivate={**{f'in_{i}':shrink(0,.3) for i in (1,2,3)},'output':[('scale',0,1,1,1),('scale',1,.6,.6,.6,cm),('position',0,0,2,0),('position',1,0,-2,0,cm)]},
            extra={f'in_{i}':(0.4,'hold',{f'in_{i}':grow(0,.4)}) for i in (1,2,3)})
    return root, an

@model('gm_splitter_crystal','분배 수정','B','mana',(0.9,1.7,1.2),
       '들어온 마력을 세 갈래로 나누어 보낸다.',
       'idle=정지 / activate=분배 시작 / active=회전 분배 / deactivate=정지')
def splitter_crystal():
    base,y=plinth('plinth',12,12,2,tiers=1)
    legs=[Group(f'leg_g{i}',[0,2,0],[C(f'leg_{i}',[-1,2,-6],[1,11,-4],metal(BRONZE),rot=[24,0,0],origin=[0,2,-5])],[0,120*i,0]) for i in range(3)]
    hub=[box('hub',0,10,0,6,3,6,runed(metal(BRONZE),'north','east','west','south',runec=A('GLOW'))),box('hub_top',0,13,0,4,1,4,metal(BRONZED))]
    arms=[]
    for i in range(3):
        arms.append(Group(f'arm_{i}',[0,12,0],[C(f'arm_{i}',[-0.5,11.5,-10],[0.5,12.5,-3],metal(BRONZED)),
                    C(f'arm_cup_{i}',[-1.5,11,-12],[1.5,13,-9],metal(BRONZE)),
                    C(f'arm_gem_{i}',[-1,13,-11.5],[1,15,-9.5],crystal(A('GEM'),a=255),rot=[0,45,0],origin=[0,13,-10.5])],[0,120*i+60,0]))
    cr=Group('crystal',[0,14,0],gem('crystal',0,16,0,4,9,crystal(A('GEM'),a=230))+
             [C('shard_a',[-1,15,-1],[1,21,1],crystal(A('GLOW'),a=230),rot=[0,0,25],origin=[0,15,0]),C('shard_b',[-1,15,-1],[1,20,1],crystal(A('GLOW'),a=230),rot=[0,0,-25],origin=[0,15,0])])
    spin=Group('spinner',[0,12,0],arms)
    root=Group('root',[0,0,0],[Group('stand',[0,0,0],base+legs+hub),spin,cr])
    def an(G):
        return std('gm_splitter_crystal',G,L=dict(active=2.0,activate=.8),
            idle={'crystal':[('position',0,0,0,0,cm),('position',1,0,.5,0,cm),('position',2,0,0,0,cm)]},
            activate={'spinner':[('rotation',0,0,0,0),('rotation',.8,0,120,0,cm)],'crystal':[('scale',0,1,1,1),('scale',.4,1.25,1.25,1.25,cm),('scale',.8,1,1,1,cm)]},
            active={'spinner':spin_n(1,2.0,-.5),'crystal':spin_n(1,2.0)},
            deactivate={'crystal':[('position',0,0,0,0)]})
    return root, an

@model('gm_rotating_mirror','회전 반사 거울','B','light',(0.9,1.5,1.0),
       '빛줄기의 방향을 꺾는 거울. 칠 때마다 90도씩 돌아간다.',
       'rot_0~3=방향 고정 / turn=90도 회전 / activate=빛 받음(테두리 발광)')
def rotating_mirror():
    base=[box('base',0,0,0,14,2,14,stone()),C('base_oct',[-7,0,-7],[7,2,7],stone(),rot=[0,45,0],origin=[0,0,0])]
    tur=[box('turntable',0,2,0,10,2,10,with_faces(metal(BRONZE),up=rune_face(A('ACCD'),.3))),
         C('upright_r',[5,4,-1.5],[7,18,1.5],metal(BRONZE)),C('upright_l',[-7,4,-1.5],[-5,18,1.5],metal(BRONZE)),
         C('upright_cap_r',[4.5,18,-2],[7.5,19,2],metal(BRONZED)),C('upright_cap_l',[-7.5,18,-2],[-4.5,19,2],metal(BRONZED)),
         C('notch',[-0.5,4,-5.5],[0.5,5,-4.5],crystal(A('GEM'),a=255))]
    mir=Group('mirror',[0,12,0],[C('mframe_t',[-5,17,-1],[5,18,1],metal((214,170,80))),C('mframe_b',[-5,6,-1],[5,7,1],metal((214,170,80))),
           C('mframe_r',[4,7,-1],[5,17,1],metal((214,170,80))),C('mframe_l',[-5,7,-1],[-4,17,1],metal((214,170,80))),
           C('mback',[-4,7,0.2],[4,17,0.8],stone(STONED,mortar=False)),
           plane_n('mglass',-4,7,4,17,-0.2,glow((206,226,240))),
           C('pin_r',[4.5,11.5,-0.5],[5.5,12.5,0.5],metal(IRON)),C('pin_l',[-5.5,11.5,-0.5],[-4.5,12.5,0.5],metal(IRON))],[0,0,0])
    glo=Group('glow',[0,12,-0.3],[plane_n('mglow',-5,6,5,18,-1.2,frame_glow())])
    mir.children.append(glo)
    turret=Group('turret',[0,2,0],tur+[mir])
    root=Group('root',[0,0,0],[Group('base',[0,0,0],base),turret])
    def an(G):
        r=lambda a,b:{'turret':[('rotation',0,0,a,0),('rotation',.35,0,b+(5 if b>a else -5),0),('rotation',.5,0,b,0,cm)]}
        return std('gm_rotating_mirror',G,L=dict(activate=.5),
            idle={'glow':hide(),'mirror':[('rotation',0,-12,0,0)]},
            activate={'glow':grow(0,.5)},active={'glow':pulse(1.5,.98,1.04)},
            extra={'turn':(0.5,'once',r(0,-90)),'rot_0':(0.5,'hold',r(90,0)),'rot_1':(0.5,'hold',r(0,-90)),
                   'rot_2':(0.5,'hold',r(-90,-180)),'rot_3':(0.5,'hold',r(-180,-270))})
    return root, an

@model('gm_prism','프리즘','B','light',(0.8,1.5,1.0),
       '흰빛을 원소 색으로 나눈다. 색 지정 빛 수신 수정과 세트.',
       'idle=정지 / activate=빛 받음 / active=회전 분광')
def prism():
    base,y=plinth('plinth',10,10,2,tiers=2)
    stand=[box('stem',0,4,0,3,6,3,metal(BRONZE)),box('cradle',0,10,0,10,1,4,metal(BRONZED)),
           C('cradle_r',[4,11,-1.5],[5,15,1.5],metal(BRONZE)),C('cradle_l',[-5,11,-1.5],[-4,15,1.5],metal(BRONZE))]
    cry=crystal((236,240,255),a=170)
    pr=Group('prism',[0,17,0],[C('prism_body',[-4,13,-4],[4,21,4],cry,rot=[0,0,45],origin=[0,17,0]),
             C('prism_core',[-1.5,15.5,-3],[1.5,18.5,3],crystal((255,255,255),a=200),rot=[0,0,45],origin=[0,17,0]),
             C('cap_n',[-3,14,-4.6],[3,20,-4],metal((214,170,80)),rot=[0,0,45],origin=[0,17,0]),
             C('cap_s',[-3,14,4],[3,20,4.6],metal((214,170,80)),rot=[0,0,45],origin=[0,17,0])]+
             [C(f'band_{k}',[-4.6,16.5,-2.5+2*k],[4.6,17.5,-1.5+2*k],glow(ELEM[e]['GEM']),rot=[0,0,45],origin=[0,17,0]) for k,e in enumerate(['fire','water','earth'])])
    root=Group('root',[0,0,0],[Group('stand',[0,0,0],base+stand),pr])
    def an(G):
        return std('gm_prism',G,L=dict(activate=1.0,active=4.0),
            idle={'prism':[('rotation',0,0,0,0,cm),('rotation',1,0,0,6,cm),('rotation',2,0,0,0,cm)]},
            activate={'prism':[('rotation',0,0,0,0),('rotation',1,0,0,90,cm),('scale',0,1,1,1),('scale',.5,1.1,1.1,1.1,cm),('scale',1,1,1,1,cm)]},
            active={'prism':spin_n(2,4.0)})
    return root, an

# =============== C. 출력 장치 ===============

@model('gm_teleport_gate','원소 텔레포트 게이트','C','mana',(2.0,3.2,1.6),
       '마력이 연결되면 포탈 면이 열린다. 두 개를 쌍으로 배치해 이동.',
       'idle=닫힘 / activate=개방 / active=포탈 유지 / deactivate=닫힘')
def teleport_gate():
    st=runed(stone(STONEL),'north','south',runec=A('ACCD'))
    parts=[box('step',0,0,0,40,2,12,stone()),box('step2',0,2,0,34,1,10,stone(STONEL))]
    for s in (1,-1):
        parts+=[C(f'pil_base_{s}',[s*16-3.5,3,-3.5],[s*16+3.5,7,3.5],stone()),C(f'pillar_{s}',[s*16-2.5,7,-2.5],[s*16+2.5,36,2.5],st),
                C(f'pil_band_{s}',[s*16-3,20,-3],[s*16+3,21,3],metal(BRONZE)),C(f'pil_band2_{s}',[s*16-3,33,-3],[s*16+3,34,3],metal(BRONZE))]
    for i in range(9):
        a=180*i/8; r=math.radians(a); x,yy=16*math.cos(r),36+16*math.sin(r)
        parts.append(C(f'arch_{i}',[x-2.5,yy-3.5,-2.5],[x+2.5,yy+3.5,2.5],stone(STONEL,mortar=False),rot=[0,0,a],origin=[x,yy,0]))
    parts+=[C('keystone',[-3,49,-3],[3,55,3],stone()),C('key_gem',[-1.5,50.5,-3.6],[1.5,53.5,-3],crystal(A('GEM'),a=255),rot=[0,0,45],origin=[0,52,-3.3])]
    por=Group('portal',[0,24,0],[plane_n('portal',-13,3,13,44,0,portal()),
                                 plane_n('portal_top',-9,40,9,48,0,portal(),rot=[0,0,0])])
    g=glyph()
    runes=Group('runes',[0,20,0],[plane_n(f'rune_{s}',s*16-1.5,10,s*16+1.5,30,-2.6,g) for s in (1,-1)])
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],parts),por,runes])
    def an(G):
        return std('gm_teleport_gate',G,L=dict(activate=1.4,active=2.0,deactivate=.8,spawn=1.6),
            idle={'portal':hide(),'runes':hide()},
            activate={'runes':grow(0,.5),'portal':[('scale',0,0,0,0),('scale',.4,0,0,0),('scale',1.1,1.05,1.05,1,cm),('scale',1.4,1,1,1,cm)]},
            active={'runes':show(),'portal':[('scale',0,1,1,1,cm),('scale',1,1.02,1.01,1,cm),('scale',2,1,1,1,cm)]},
            deactivate={'portal':shrink(0,.5),'runes':shrink(.2,.8)},spawn=spawn_rise(-12,1.6))
    return root, an

def seal_door(id_, ko, W, H, hb):
    @model(id_,ko,'C','mana',hb,'문양 봉인이 풀리면 양쪽으로 열리는 봉인문.',
           'idle=잠김 / activate=봉인 해제→개방 / active=열린 상태 / deactivate=닫힘')
    def build():
        hw=W//2
        st=stone(); stl=stone(STONEL,mortar=False)
        frame=[C('post_r',[hw,0,-3],[hw+4,H,3],st),C('post_l',[-hw-4,0,-3],[-hw,H,3],st),
               C('post_rune_r',[hw+1,4,-3.2],[hw+3,H-4,-3.2],glyph(A('ACC'))),C('post_rune_l',[-hw-3,4,-3.2],[-hw-1,H-4,-3.2],glyph(A('ACC'))),
               C('lintel',[-hw-5,H,-3.5],[hw+5,H+5,3.5],st),C('lintel_cap',[-hw-6,H+5,-4],[hw+6,H+6,4],metal(BRONZE)),
               C('sill',[-hw-4,0,-3.5],[hw+4,1,3.5],st)]
        def leaf(side):
            sg=1 if side=='r' else -1
            x0,x1=(0,hw) if sg>0 else (-hw,0)
            hinge=hw*sg
            parts=[C(f'leaf_{side}',[x0,1,-1],[x1,H,1],stl),
                   C(f'trim_top_{side}',[x0,H-2,-1.5],[x1,H-1,-1],metal(BRONZE)),C(f'trim_bot_{side}',[x0,2,-1.5],[x1,3,-1],metal(BRONZE)),
                   C(f'trim_mid_{side}',[x0,H//2-1,-1.5],[x1,H//2,-1],metal(BRONZE)),
                   C(f'trim_edge_{side}',[x1-1 if sg>0 else x0,1,-1.5],[x1 if sg>0 else x0+1,H,-1],metal(BRONZED)),
                   C(f'panel_{side}',[x0+2,H//2+2,-2],[x1-2,H-4,-1],stone(STONE,mortar=False)),
                   C(f'panel2_{side}',[x0+2,4,-2],[x1-2,H//2-2,-1],stone(STONE,mortar=False)),
                   C(f'stud_{side}_a',[(x0+x1)/2-1,H*3//4-1,-2.5],[(x0+x1)/2+1,H*3//4+1,-2],metal((214,170,80))),
                   C(f'stud_{side}_b',[(x0+x1)/2-1,H//4-1,-2.5],[(x0+x1)/2+1,H//4+1,-2],metal((214,170,80)))]
            return Group(f'door_{side}',[hinge,0,0],parts)
        cy=H//2+(2 if H>40 else 1)
        seal=[]
        n=10 if W>30 else 8; R=6 if W>30 else 4
        for i in range(n):
            a=360*i/n; r=math.radians(a); x,yy=R*math.cos(r),cy+R*math.sin(r)
            seal.append(C(f'seal_ring_{i}',[x-0.5,yy-1.5,-3],[x+0.5,yy+1.5,-2],metal((214,170,80)),rot=[0,0,a],origin=[x,yy,-2]))
        seal+=[C('seal_gem',[-2,cy-2,-3.5],[2,cy+2,-2.5],crystal(A('GEM'),a=255),rot=[0,0,45],origin=[0,cy,-2.5]),
               C('seal_bar',[-R-2,cy-0.5,-2.5],[R+2,cy+0.5,-2],metal(BRONZED))]
        sg=Group('seal',[0,cy,-2],seal)
        root=Group('root',[0,0,0],[Group('frame',[0,0,0],frame),leaf('r'),leaf('l'),sg])
        def an(G):
            return std(id_,G,L=dict(activate=2.2,deactivate=1.2),
                idle={},
                activate={'seal':[('rotation',0,0,0,0),('rotation',.8,0,0,180,cm),('scale',0,1,1,1),('scale',.8,1.1,1.1,1.1,cm),('scale',1.1,0,0,0,cm)],
                          'door_r':[('rotation',0,0,0,0),('rotation',1.1,0,0,0),('rotation',2.2,0,100,0,cm)],
                          'door_l':[('rotation',0,0,0,0),('rotation',1.1,0,0,0),('rotation',2.2,0,-100,0,cm)]},
                active={'seal':hide(),'door_r':[('rotation',0,0,100,0)],'door_l':[('rotation',0,0,-100,0)]},
                deactivate={'door_r':[('rotation',0,0,100,0),('rotation',1,0,0,0,cm)],'door_l':[('rotation',0,0,-100,0),('rotation',1,0,0,0,cm)],
                            'seal':grow(.9,1.2)},spawn=spawn_pop(.8))
        return root, an
    return build
seal_door('gm_seal_door','봉인문 (대형 3x4)',48,60,(3.0,4.0,2.0))
seal_door('gm_seal_door_small','봉인문 (소형 2x3)',28,44,(2.0,3.0,1.5))

@model('gm_floating_platform','부유 발판','C','wind',(1.8,0.6,0.4),
       '작동하면 떠오르거나 정해진 경로를 왕복하는 발판(경로 이동은 게임 쪽).',
       'idle=가라앉음 / activate=떠오름 / active=부유 / deactivate=하강')
def floating_platform():
    st=stone(moss=.2)
    top=[box('deck',0,0,0,28,3,28,with_faces(st,up=rune_face(A('ACCD'),.25))),
         *[C(f'edge_{i}',[-14,3,-14],[14,4,-13],metal(BRONZE),rot=[0,90*i,0],origin=[0,0,0]) for i in range(4)],
         *[box(f'corner_gem_{i}',sx*12.5,3,sz*12.5,2,2,2,crystal(A('GEM'),a=255)) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])]]
    under=[box('rock_1',0,-5,0,22,5,22,stone(STONED)),box('rock_2',1,-9,-1,14,4,14,stone(STONED)),box('rock_3',-1,-12,1,8,3,8,stone(STONED)),
           box('rock_4',6,-7,5,6,2,6,stone(STONED)),box('rock_5',-6,-7,-4,5,2,6,stone(STONED))]
    cr=Group('crystal',[0,-12,0],[C('crystal_a',[-2,-20,-2],[2,-11,2],crystal(A('GEM'),a=240),rot=[0,45,0],origin=[0,-12,0]),
                                   C('crystal_b',[-1,-23,-1],[1,-20,1],crystal(A('GLOW'),a=240),rot=[0,45,0],origin=[0,-12,0])])
    p=Group('platform',[0,0,0],top+under+[cr])
    root=Group('root',[0,0,0],[p])
    def an(G):
        return std('gm_floating_platform',G,L=dict(activate=1.5,active=3.0,deactivate=1.5),
            idle={'platform':[('position',0,0,-3,0)],'crystal':[('rotation',0,0,0,0),('rotation',2,0,90,0)]},
            activate={'platform':[('position',0,0,-3,0),('position',1.2,0,1,0,cm),('position',1.5,0,0,0,cm)]},
            active={'platform':[('position',0,0,0,0,cm),('position',1.5,0,1,0,cm),('position',3,0,0,0,cm),
                                ('rotation',0,1,0,-1,cm),('rotation',1.5,-1,0,1,cm),('rotation',3,1,0,-1,cm)],'crystal':spin_n(1,3)},
            deactivate={'platform':[('position',0,0,0,0),('position',1.5,0,-3,0,cm)]})
    return root, an

@model('gm_rising_pillar','승강 석주','C','earth',(0.9,3.0,2.8),
       '바닥에서 기둥이 솟아올라 계단·발판이 된다. 평소엔 땅속(바닥 고리만 보임).',
       'idle=땅속 / activate=솟아오름 / active=올라온 상태 / deactivate=가라앉음')
def rising_pillar():
    ringp=[C(f'ground_{i}',[-8,0,-8],[8,1,-7],metal(BRONZE),rot=[0,90*i,0],origin=[0,0,0]) for i in range(4)]
    ringp+=[box(f'ground_gem_{i}',sx*7.5,0,sz*7.5,2,1.0,2,crystal(A('GEM'),a=255)) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])]
    st=runed(stone(),'north','south','east','west',runec=A('ACCD'))
    col_=[box('shaft',0,0,0,12,40,12,st),box('band_1',0,12,0,13,1,13,metal(BRONZED)),box('band_2',0,28,0,13,1,13,metal(BRONZED)),
          box('capital',0,40,0,14,3,14,stone(STONEL)),box('top',0,43,0,14,1,14,with_faces(stone(STONEL,mortar=False),up=rune_face(A('ACCD'),.3))),
          *[box(f'cap_knob_{i}',sx*6.5,40,sz*6.5,2,4,2,metal(BRONZE)) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])]]
    p=Group('pillar',[0,0,0],col_)
    root=Group('root',[0,0,0],[Group('ground',[0,0,0],ringp),p])
    def an(G):
        return std('gm_rising_pillar',G,L=dict(activate=2.0,deactivate=2.0),
            idle={'pillar':[('position',0,0,-44,0)]},
            activate={'pillar':[('position',0,0,-44,0),('position',1.7,0,1,0,cm),('position',2.0,0,0,0,cm),
                                ('rotation',0,0,0,0),('rotation',.3,0,0,1),('rotation',.6,0,0,-1),('rotation',.9,0,0,1),('rotation',1.2,0,0,-1),('rotation',1.5,0,0,0)]},
            active={'pillar':[('position',0,0,0,0)]},
            deactivate={'pillar':[('position',0,0,0,0),('position',2,0,-44,0,cm)]},spawn=spawn_pop(.6))
    return root, an

@model('gm_sealed_chest','봉인 보물상자','C','*',(0.9,0.9,0.6),
       '퍼즐 보상 상자. 원소 자물쇠가 풀리면 뚜껑이 열린다. 6속성 텍스처 변형.',
       'idle=잠김 / activate=자물쇠 해제→개봉 / active=열린 상태',variants=['fire','wind','water','earth','light','dark'])
def sealed_chest():
    w=wood(); tr=metal((214,170,80)); acc=metal(A('ACC'))
    body=[box('body',0,0,0,14,9,12,w),box('foot',0,0,0,15,1,13,metal(BRONZED))]
    body+=[C(f'corner_{i}',[sx*7-(1 if sx>0 else 0),0,sz*6-(1 if sz>0 else 0)],[sx*7+(0 if sx>0 else 1),9,sz*6+(0 if sz>0 else 1)],tr,mirror=False) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])]
    body+=[C('band_front',[-7.2,4,-6.2],[7.2,5,-6],acc),C('band_back',[-7.2,4,6],[7.2,5,6.2],acc)]
    lid=Group('lid',[0,9,6],[C('lid',[-7,9,-6],[7,12,6],w),C('lid_top',[-6,12,-5],[6,14,5],w),C('lid_crown',[-5,14,-4],[5,15,4],w),
              C('lid_trim',[-7.3,9,-6.3],[7.3,10,6.3],tr),C('lid_strap_r',[3,12,-5.2],[4,14.2,5.2],acc),C('lid_strap_l',[-4,12,-5.2],[-3,14.2,5.2],acc),
              C('lid_hasp',[-1,8,-6.6],[1,11,-6],tr)])
    lock=Group('lock',[0,7,-6.5],[C('lock_plate',[-2.5,4.5,-7],[2.5,9.5,-6],tr),C('lock_gem',[-1.5,5.5,-7.6],[1.5,8.5,-6.8],crystal(A('GEM'),a=255),rot=[0,0,45],origin=[0,7,-7.2]),
            C('lock_ring',[-2,4.5,-7.4],[2,5.5,-6.9],acc)])
    inner=Group('treasure',[0,8,0],[box('gold_1',-2,7,1,4,2,4,metal((240,200,90))),box('gold_2',3,7,-1,3,1,3,metal((240,200,90))),
            C('gem_in',[-1,9,-2],[1,11,0],crystal(A('GEM'),a=255),rot=[0,45,0],origin=[0,9,-1])])
    root=Group('root',[0,0,0],[Group('chest',[0,0,0],body+[inner]),lid,lock])
    def an(G):
        return std('gm_sealed_chest',G,L=dict(activate=1.6),
            idle={'lock':[('scale',0,1,1,1)]},
            activate={'lock':[('rotation',0,0,0,0),('rotation',.6,0,0,360,cm),('scale',.5,1,1,1),('scale',.8,0,0,0,cm)],
                      'lid':[('rotation',0,0,0,0),('rotation',.8,0,0,0),('rotation',1.3,-110,0,0,cm),('rotation',1.6,-100,0,0,cm)]},
            active={'lock':hide(),'lid':[('rotation',0,-100,0,0)]},spawn=spawn_pop(.8))
    return root, an

@model('gm_wind_vent','바람 분출구','C','wind',(1.0,0.3,0.2),
       '작동하면 위로 날려 주는 상승기류를 뿜는 바닥 송풍구(기류 효과는 게임 쪽).',
       'idle=멈춤 / activate=가동 / active=고속 회전 / deactivate=감속')
def wind_vent():
    fr=[C(f'rim_{i}',[-8,0,-8],[8,3,-6],stone(),rot=[0,90*i,0],origin=[0,0,0]) for i in range(4)]
    fr+=[box('pit',0,0,0,12,0.0,12,stone(STONED)),box('hub_post',0,0,0,2,2,2,metal(IRON))]
    fr+=[C(f'grate_{i}',[-6,2.5,-4.5+3*i],[6,3,-3.5+3*i],metal(IRON)) for i in range(4)]
    fr+=[box(f'rim_gem_{i}',sx*7,3,sz*7,2,1.0,2,crystal(A('GEM'),a=255)) for i,(sx,sz) in enumerate([(1,1),(1,-1),(-1,1),(-1,-1)])]
    fan=Group('fan',[0,1.5,0],[box('fan_hub',0,1,0,3,1.5,3,metal(BRONZE))]+
              [C(f'blade_{i}',[0,1.2,-1],[5.5,1.8,1.0],runed(metal((200,210,205)),'up',runec=A('ACC')),rot=[20,90*i,0],origin=[0,1.5,0]) for i in range(4)])
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],fr),fan])
    def an(G):
        return std('gm_wind_vent',G,L=dict(active=.5,activate=1.0,deactivate=1.5),
            idle={},activate={'fan':[('rotation',0,0,0,0),('rotation',1,0,-540,0,'linear')]},
            active={'fan':spin_n(1,.5,-1)},
            deactivate={'fan':[('rotation',0,0,0,0),('rotation',1.5,0,-400,0,cm)]})
    return root, an

@model('gm_sluice_gate','물길 수문','C','water',(1.2,1.6,1.0),
       '열리면 물이 흘러 물레방아·수위 기둥 같은 다른 장치를 연쇄 작동시킨다.',
       'idle=닫힘 / activate=수문 상승 / active=열림 / deactivate=하강')
def sluice_gate():
    st=stone()
    fr=[box('channel',0,0,0,20,2,16,st),C('wall_r',[8,2,-8],[10,6,8],st),C('wall_l',[-10,2,-8],[-8,6,8],st),
        C('post_r',[8,2,-2],[11,28,2],runed(wood(),'north',runec=A('ACCD'))),C('post_l',[-11,2,-2],[-8,28,2],runed(wood(),'north',runec=A('ACCD'))),
        C('beam',[-12,28,-2.5],[12,31,2.5],wood(WOODD)),C('guide_r',[7,2,-1],[8,28,1],metal(IRON)),C('guide_l',[-8,2,-1],[-7,28,1],metal(IRON)),
        plane_u('water',-8,-8,8,8,4,water(170))]
    gate=Group('gate',[0,2,0],[C('panel',[-7,2,-0.5],[7,16,0.5],wood()),*[C(f'plank_line_{i}',[-7,4+3*i,-1],[7,4.5+3*i,-0.5],metal(BRONZED)) for i in range(4)],
             C('gate_rod',[-0.5,16,-0.5],[0.5,30,0.5],metal(IRON))])
    crank=Group('crank',[0,33,-3],[*[C(f'crank_spoke_{i}',[-0.5,29,-3.5],[0.5,37,-2.5],metal(BRONZE),rot=[0,0,45*i],origin=[0,33,-3]) for i in range(4)],
              *[c for c in ring('crank_rim',0,33,-3,4,8,1,1,1,metal((214,170,80)),plane='xy')],
              box('crank_hub',0,32,-3.5,2,2,2,crystal(A('GEM'),a=255))])
    frs=fr+[C('crank_axle',[-0.5,32.5,-3],[0.5,33.5,0],metal(IRON)),C('crank_base',[-2,31,-2],[2,33,2],wood(WOODD))]
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],frs),gate,crank])
    def an(G):
        return std('gm_sluice_gate',G,L=dict(activate=1.8,deactivate=1.5),
            idle={},activate={'gate':[('position',0,0,0,0),('position',1.8,0,12,0,cm)],'crank':[('rotation',0,0,0,0),('rotation',1.8,0,0,-540,cm)]},
            active={'gate':[('position',0,0,12,0)]},
            deactivate={'gate':[('position',0,0,12,0),('position',1.2,0,-.5,0,cm),('position',1.5,0,0,0,cm)],'crank':[('rotation',0,0,0,0),('rotation',1.5,0,0,540,cm)]})
    return root, an

@model('gm_light_bridge','빛의 다리','C','light',(1.0,0.4,0.2),
       '마력이 흐르는 동안만 실체화되는 다리 한 칸(2블록 길이). 이어 붙여 사용.',
       'idle=꺼짐 / activate=발판 순차 생성 / active=유지 / deactivate=소멸')
def light_bridge():
    fr=[]
    for z in (-15,15):
        for s in (1,-1):
            fr+=[C(f'pylon_{z}_{s}',[s*8-1,0,z-1],[s*8+1,8,z+1],metal(BRONZE)),C(f'pylon_cap_{z}_{s}',[s*8-1.5,8,z-1.5],[s*8+1.5,9,z+1.5],metal((214,170,80))),
                 C(f'pylon_gem_{z}_{s}',[s*8-1,9,z-1],[s*8+1,11,z+1],crystal(A('GEM'),a=255),rot=[0,45,0],origin=[s*8,9,z])]
        fr.append(C(f'sill_{z}',[-8,0,z-1],[8,1,z+1],stone()))
    planks=[]
    gl=glow(A('GLOW'),a=170)
    for i in range(6):
        z0=-14+i*28/6
        planks.append(Group(f'plank_{i}',[0,1,z0+2],[C(f'plank_{i}',[-7,0,z0+0.2],[7,1,z0+4.2],gl),C(f'plank_edge_{i}',[-7,1,z0+0.2],[7,1,z0+4.2],frame_glow())]))
    rails=Group('rails',[0,6,0],[C('rail_r',[7.5,6,-14],[8.5,6.5,14],glow()),C('rail_l',[-8.5,6,-14],[-7.5,6.5,14],glow())])
    root=Group('root',[0,0,0],[Group('frame',[0,0,0],fr),Group('deck',[0,1,0],planks),rails])
    def an(G):
        act={f'plank_{i}':[('scale',0,0,0,0),('scale',.15*i,0,0,0),('scale',.15*i+.3,1,1,1,cm)] for i in range(6)}
        act['rails']=[('scale',0,1,0,0),('scale',1.2,1,1,1,cm)]
        return std('gm_light_bridge',G,L=dict(activate=1.2,deactivate=.9),
            idle={**{f'plank_{i}':hide() for i in range(6)},'rails':hide()},
            activate=act,active={'rails':pulse(2,.98,1.02)},
            deactivate={**{f'plank_{i}':[('scale',0,1,1,1),('scale',.12*(5-i),1,1,1),('scale',.12*(5-i)+.3,0,0,0,cm)] for i in range(6)},'rails':shrink(0,.4)})
    return root, an
