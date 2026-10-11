import math, random
import gen
from gen import *

gen.HITBOX_JSON = '/mnt/user-data/outputs/gimmicks/hitboxes.json'

# ---------------- palettes ----------------
ELEM = {
    'fire':  dict(ACC=(232,92,40),  ACCD=(150,44,24),  GLOW=(255,190,80),  GEM=(255,120,60)),
    'wind':  dict(ACC=(88,214,180), ACCD=(40,130,112), GLOW=(190,255,232), GEM=(120,240,200)),
    'water': dict(ACC=(60,140,232), ACCD=(30,74,150),  GLOW=(150,215,255), GEM=(90,170,255)),
    'earth': dict(ACC=(150,190,70), ACCD=(88,112,40),  GLOW=(214,240,120), GEM=(176,206,90)),
    'light': dict(ACC=(246,214,110),ACCD=(176,136,50), GLOW=(255,250,210), GEM=(255,232,150)),
    'dark':  dict(ACC=(150,90,214), ACCD=(76,40,120),  GLOW=(214,170,255), GEM=(170,110,240)),
    'mana':  dict(ACC=(110,170,255),ACCD=(56,80,170),  GLOW=(200,235,255), GEM=(140,200,255)),
}
STONE=(150,146,136); STONED=(104,100,94); STONEL=(182,178,166)
BRONZE=(176,128,62); BRONZED=(112,78,38); IRON=(92,94,102); WOOD=(122,86,52); WOODD=(84,58,34)
CUR = dict(ELEM['mana'])
def use(elem): CUR.clear(); CUR.update(ELEM[elem])
def A(k): return CUR[k]

# ---------------- painters ----------------
def stone(base=None, mortar=True, moss=0.0):
    def p(img, face, rect, rng):
        b = base or STONE
        x1,y1,x2,y2 = rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                f = SHADE[face]*(1+rng.uniform(-.09,.09))
                c = col(b,f)
                ly, lx = y-y1, x-x1
                if mortar and face not in ('up','down') and (y2-y1)>=4:
                    if ly%4==3 or (lx+(4 if (ly//4)%2 else 0))%8==7: c = col(b,f*.72)
                if moss and face=='up' and rng.random()<moss: c = col((86,120,60),f)
                if rng.random()<.03: c = col(b,f*.8)
                img.putpixel((x,y),c)
    return p

def metal(base, hi=True):
    def p(img, face, rect, rng):
        x1,y1,x2,y2 = rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                f = SHADE[face]*(1+rng.uniform(-.04,.04))
                if hi and y==y1 and face not in ('up','down'): f*=1.18
                if hi and y==y2-1 and face not in ('up','down'): f*=.8
                img.putpixel((x,y),col(base,f))
    return p

def wood(base=None):
    def p(img, face, rect, rng):
        b = base or WOOD
        x1,y1,x2,y2 = rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                f = SHADE[face]*(1+rng.uniform(-.05,.05))
                if (x-x1)%3==0: f*=.86
                img.putpixel((x,y),col(b,f))
    return p

def glow(c=None, a=255):
    def p(img, face, rect, rng):
        b = c or A('GLOW')
        x1,y1,x2,y2 = rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                img.putpixel((x,y),col(b,1+rng.uniform(-.04,.04),a))
    return p

def crystal(c=None, a=235):
    def p(img, face, rect, rng):
        b = c or A('GEM')
        x1,y1,x2,y2 = rect; h = max(1,y2-y1)
        for y in range(y1,y2):
            for x in range(x1,x2):
                t = (y-y1)/h
                f = SHADE[face]*(1.25-.45*t)*(1+rng.uniform(-.05,.05))
                cc = col(b,f,a)
                if (x-x1)==(y-y1)%max(1,x2-x1) and rng.random()<.5: cc = col(b,1.45,a)
                img.putpixel((x,y),cc)
    return p

def water(a=180):
    def p(img, face, rect, rng):
        x1,y1,x2,y2 = rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                f = SHADE[face]*(1+rng.uniform(-.06,.06))
                c = (60,130,220)
                if face=='up' and rng.random()<.12: c=(170,220,255)
                img.putpixel((x,y),col(c,f,a))
    return p

def flame(base=None, tip=None):
    def p(img, face, rect, rng):
        x1,y1,x2,y2 = rect; h=max(1,y2-y1); w=max(1,x2-x1)
        lo = base or (255,120,30); hi = tip or (255,236,140)
        for y in range(y1,y2):
            t = (y-y1)/h   # 0 top .. 1 bottom
            for x in range(x1,x2):
                u = abs((x-x1+.5)/w-.5)*2
                if u > .25+.75*t+rng.uniform(-.1,.1): continue  # tapered tip
                c = tuple(int(hi[i]*(1-t)+lo[i]*t) for i in range(3))
                img.putpixel((x,y),col(c,1+rng.uniform(-.05,.05),235))
    return p

def rune_face(runec=None, density=.35):
    """stamp a symmetric glyph on a face"""
    def f(img, rect, rng):
        x1,y1,x2,y2 = rect; w,h = x2-x1, y2-y1
        if w<3 or h<3: return
        c = col(runec or A('ACC'),1.1)
        cx = w//2
        for ly in range(1,h-1):
            if rng.random()<density:
                dx = rng.randint(0,max(0,cx-1))
                px(img,rect,cx-1-dx,ly,c); px(img,rect,w-cx+dx,ly,c)
        for ly in range(1,h-1): px(img,rect,cx-(1 if w%2==0 else 0),ly,c) if ly%2 else None
    return f

def runed(painter, *faces, runec=None):
    return with_faces(painter, **{f: rune_face(runec) for f in faces})

# ---------------- geometry helpers ----------------
def mirror_x(v): return [-v[0],v[1],v[2]]
def C(name, frm, to, paint, mirror=False, **kw):
    if mirror:
        frm,to=[-to[0],frm[1],frm[2]],[-frm[0],to[1],to[2]]
        if 'origin' in kw: kw['origin']=mirror_x(kw['origin'])
        if 'rot' in kw: kw['rot']=[kw['rot'][0],-kw['rot'][1],-kw['rot'][2]]
    frm=[float(v) for v in frm]; to=[float(v) for v in to]
    for i in range(3):   # Box UV: snap sizes to integers (keep center); tiny sizes -> plane
        sz=to[i]-frm[i]
        if abs(sz-round(sz))>1e-6:
            n = 0 if sz<0.35 else max(1,round(sz))
            c=(frm[i]+to[i])/2; frm[i]=c-n/2; to[i]=c+n/2
    return Cube(name, frm, to, paint, **kw)

def box(name, cx, y, cz, w, h, d, paint, **kw):
    """box centered on cx,cz from y up"""
    return C(name,[cx-w/2,y,cz-d/2],[cx+w/2,y+h,cz+d/2],paint,**kw)

def ring(name, cx, cy, cz, radius, n, seg_w, seg_d, thick, paint, plane='xy', phase=0):
    """polygon ring of n cube segments. plane xy = faces north (axis z); 'xz' horizontal; 'yz' axis x"""
    out=[]
    L = math.ceil(2*math.pi*radius/n)+1
    for i in range(n):
        a = 360*i/n + phase; r = math.radians(a)
        if plane=='xy':
            px_,py_ = cx+radius*math.cos(r), cy+radius*math.sin(r)
            c = C(f'{name}_{i}',[px_-seg_w/2,py_-L/2,cz-seg_d/2],[px_+seg_w/2,py_+L/2,cz+seg_d/2],paint,
                  rot=[0,0,round(a,2)],origin=[px_,py_,cz])
        elif plane=='xz':
            px_,pz_ = cx+radius*math.cos(r), cz+radius*math.sin(r)
            c = C(f'{name}_{i}',[px_-seg_w/2,cy,pz_-L/2],[px_+seg_w/2,cy+thick,pz_+L/2],paint,
                  rot=[0,-round(a,2),0],origin=[px_,cy,pz_])
        else:  # yz, axis x
            py_,pz_ = cy+radius*math.cos(r), cz+radius*math.sin(r)
            c = C(f'{name}_{i}',[cx-seg_d/2,py_-seg_w/2,pz_-L/2],[cx+seg_d/2,py_+seg_w/2,pz_+L/2],paint,
                  rot=[round(a,2),0,0],origin=[cx,py_,pz_])
        out.append(c)
    return out

def plinth(name, w, d, h=3, y=0, tiers=2, paint=None):
    """stepped stone base"""
    p = paint or stone()
    out=[]; yy=y
    for i in range(tiers):
        ww, dd = w-2*i, d-2*i
        out.append(box(f'{name}_{i}',0,yy,0,ww,h,dd,p)); yy+=h
    return out, yy

def gem(name, cx, y, cz, s, hgt, paint=None, rot_y=45):
    """faceted gem: center column + rotated crown + point"""
    p = paint or crystal()
    return [
        C(name+'_core',[cx-s/2,y,cz-s/2],[cx+s/2,y+hgt,cz+s/2],p,rot=[0,rot_y,0],origin=[cx,y,cz]),
        C(name+'_tip',[cx-(s-2)/2,y+hgt,cz-(s-2)/2],[cx+(s-2)/2,y+hgt+max(1,s//2),cz+(s-2)/2],p,rot=[0,rot_y,0],origin=[cx,y,cz]),
        C(name+'_base',[cx-(s-2)/2,y-max(1,s//2),cz-(s-2)/2],[cx+(s-2)/2,y,cz+(s-2)/2],p,rot=[0,rot_y,0],origin=[cx,y,cz]),
    ] if s>2 else [C(name+'_core',[cx-s/2,y,cz-s/2],[cx+s/2,y+hgt,cz+s/2],p,rot=[0,rot_y,0],origin=[cx,y,cz])]

# ---------------- animation helpers ----------------
cm='catmullrom'
def show(t=0):  return [('scale',t,1,1,1)]
def hide(t=0):  return [('scale',t,0,0,0)]
def grow(t0,t1,over=1.12):
    return [('scale',t0,0,0,0),('scale',t0+(t1-t0)*.7,over,over,over,cm),('scale',t1,1,1,1,cm)]
def shrink(t0,t1):
    return [('scale',t0,1,1,1),('scale',t1,0,0,0,cm)]
def spin(axis, L, turns=1, t0=0):
    v=[0,0,0]; v[axis]=360*turns
    return [('rotation',t0,0,0,0),('rotation',t0+L,*v)]
def spin_n(axis, L, turns=1):
    """spin with intermediate keys (safer for interpolation)"""
    ks=[]
    for i in range(5):
        v=[0,0,0]; v[axis]=360*turns*i/4
        ks.append(('rotation',L*i/4,*v))
    return ks
def bob(L, amp=.6, ch='position'):
    return [(ch,0,0,0,0,cm),(ch,L/2,0,amp,0,cm),(ch,L,0,0,0,cm)]
def pulse(L, lo=.9, hi=1.08):
    return [('scale',0,lo,lo,lo,cm),('scale',L/2,hi,hi,hi,cm),('scale',L,lo,lo,lo,cm)]
def spawn_rise(dy=-8, L=1.2):
    return {'root':[('position',0,0,dy,0),('position',L*.75,0,.5,0,cm),('position',L,0,0,0,cm),
                    ('scale',0,.6,.6,.6),('scale',L,1,1,1,cm)]}
def spawn_pop(L=1.0):
    return {'root':[('scale',0,.1,.1,.1),('scale',L*.6,1.12,1.12,1.12,cm),('scale',L,1,1,1,cm)]}
def merge(*ds):
    out={}
    for d in ds:
        for k,v in d.items(): out.setdefault(k,[]).extend(v)
    for k,v in out.items():
        seen=set(); out[k]=[x for x in v if not ((x[0],x[1]) in seen or seen.add((x[0],x[1])))]
    return out

def std(id_, G, *, spawn=None, idle=None, activate=None, active=None, deactivate=None,
        L=dict(spawn=1.2, idle=2.0, activate=1.0, active=2.0, deactivate=1.0), extra=None):
    """standard gimmick animation set: spawn / idle(off) / activate / active(on loop) / deactivate"""
    n=f'animation.{id_}.'; Ls=dict(dict(spawn=1.2, idle=2.0, activate=1.0, active=2.0, deactivate=1.0), **L)
    out=[anim(n+'spawn',Ls['spawn'],'once',spawn or spawn_pop(Ls['spawn']),G),
         anim(n+'idle',Ls['idle'],'loop',idle or {},G)]
    if activate is not None: out.append(anim(n+'activate',Ls['activate'],'hold',activate,G))
    if active is not None: out.append(anim(n+'active',Ls['active'],'loop',active,G))
    if deactivate is not None: out.append(anim(n+'deactivate',Ls['deactivate'],'hold',deactivate,G))
    for name,(length,loop,tr) in (extra or {}).items():
        out.append(anim(n+name,length,loop,tr,G))
    return out

def glyph(c=None, a=255, density=.55):
    """transparent plane with a symmetric rune glyph in glow color"""
    def p(img, face, rect, rng):
        if face not in ('north','south','up','down','east','west'): return
        x1,y1,x2,y2 = rect; w,h = x2-x1, y2-y1
        b = c or A('GLOW')
        if w<=2 or h<=2:
            for y in range(y1,y2):
                for x in range(x1,x2): img.putpixel((x,y),col(b,1,a))
            return
        cx=w/2
        pts=set()
        for ly in range(h):
            if ly in (0,h-1) or rng.random()<density:
                dx=rng.randint(0,max(0,int(cx)-1))
                pts.add((int(cx)-1-dx,ly)); pts.add((w-int(cx)+dx,ly))
            if ly%3!=2: pts.add((int(cx)-(1 if w%2==0 else 0),ly)); pts.add((w-int(cx)-(0 if w%2==0 else 1),ly))
        for (lx,ly) in pts: px(img,rect,lx,ly,col(b,1,a))
    return p
def frame_glow(c=None, a=255):
    """transparent plane with only the border lit"""
    def p(img, face, rect, rng):
        x1,y1,x2,y2 = rect; b=c or A('GLOW')
        for y in range(y1,y2):
            for x in range(x1,x2):
                if x in (x1,x2-1) or y in (y1,y2-1): img.putpixel((x,y),col(b,1,a))
    return p
def portal(c=None, a=200):
    def p(img, face, rect, rng):
        x1,y1,x2,y2 = rect; b=c or A('ACC'); g=A('GLOW'); w,h=x2-x1,y2-y1
        for y in range(y1,y2):
            for x in range(x1,x2):
                u=(x-x1+.5)/w-.5; v=(y-y1+.5)/h-.5; r=(u*u*4+v*v*4)**.5
                ang=math.atan2(v,u); s=(math.sin(ang*3+r*12)+1)/2
                cc=tuple(int(b[i]*(1-s)+g[i]*s) for i in range(3))
                img.putpixel((x,y),col(cc,1.0,a))
    return p
def plane_n(name,x1,y1,x2,y2,z,paint,**kw):  # plane facing north/south at z
    return C(name,[x1,y1,z],[x2,y2,z],paint,**kw)
def plane_e(name,z1,y1,z2,y2,x,paint,**kw):  # plane facing east/west at x
    return C(name,[x,y1,z1],[x,y2,z2],paint,**kw)
def plane_u(name,x1,z1,x2,z2,y,paint,**kw):
    return C(name,[x1,y,z1],[x2,y,z2],paint,**kw)

REG=[]
def model(id_, ko, cat, elem, hitbox, desc, anims_desc, variants=None):
    """register: build fn returns root Group; anims fn(G) defined as build.anims"""
    def deco(fn):
        REG.append(dict(id=id_, ko=ko, cat=cat, elem=elem, hitbox=hitbox, desc=desc,
                        anims_desc=anims_desc, build=fn, variants=variants))
        return fn
    return deco
