from gen import *
import json

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
def G(name, origin, children, rot=None, mirror=False):
    if mirror:
        origin=mirror_x(origin)
        if rot: rot=[rot[0],-rot[1],-rot[2]]
    return Group(name, origin, children, rot)

def walk_tracks(L, fa, ha, lift, lower_f, lower_h, bob, extra=None):
    cm='catmullrom'; h=L/2; q=L/4
    sw=lambda a:[('rotation',0,a,0,0,cm),('rotation',h,-a,0,0,cm),('rotation',L,a,0,0,cm)]
    lift_mid=lambda t:[('rotation',0,0,0,0,cm),('rotation',t-q,0,0,0,cm),('rotation',t,-lift,0,0,cm),('rotation',min(L,t+q),0,0,0,cm)]+([('rotation',L,0,0,0,cm)] if t+q<L else [])
    tr={'right_front_leg':sw(fa),'left_hind_leg':sw(ha),'left_front_leg':sw(-fa),'right_hind_leg':sw(-ha),
        lower_f%'right':lift_mid(3*q), lower_h%'left':lift_mid(3*q),
        lower_f%'left':lift_mid(q), lower_h%'right':lift_mid(q),
        'body':[('position',0,0,0,0,cm),('position',q,0,-bob,0,cm),('position',h,0,0,0,cm),('position',3*q,0,-bob,0,cm),('position',L,0,0,0,cm)]}
    for k,v in tr.items():
        seen=set(); tr[k]=[x for x in v if not ((x[0],x[1]) in seen or seen.add((x[0],x[1])))]
    tr.update(extra or {})
    return tr

# ---------- painters ----------
def veins(base, vein, prob=0.02, noise=0.08):
    def p(img, face, rect, rng):
        solid(base,noise)(img,face,rect,rng)
        x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
        for _ in range(int(w*h*prob)):
            x=rng.randrange(w); y=rng.randrange(h)
            for s in range(rng.randint(2,5)):
                if 0<=x<w and 0<=y<h: img.putpixel((x1+x,y1+y), col(vein, rng.uniform(.75,1.0)))
                x+=rng.choice([-1,0,1]); y+=rng.choice([0,1,1])
    return p

def vgrad(top, bottom, noise=0.07):
    def p(img, face, rect, rng):
        x1,y1,x2,y2=rect; h=y2-y1
        for y in range(y1,y2):
            t=(y-y1)/max(1,h-1) if face not in('up','down') else 0
            b=[top[i]*(1-t)+bottom[i]*t for i in range(3)]
            for x in range(x1,x2):
                img.putpixel((x,y), col(b, SHADE[face]*(1+rng.uniform(-noise,noise))))
    return p

def tatter(base, depth=3, holes=0.03, noise=0.1):
    """얇은 막/천: 아래 가장자리 너덜너덜(투명)"""
    def p(img, face, rect, rng):
        x1,y1,x2,y2=rect; w,h=x2-x1,y2-y1
        cut=[rng.randint(0,depth) for _ in range(w)]
        a0=base[3] if len(base)>3 else 255
        for x in range(w):
            for y in range(h):
                if y>=h-cut[x] or rng.random()<holes: img.putpixel((x1+x,y1+y),(0,0,0,0)); continue
                f=SHADE[face]*(1+rng.uniform(-noise,noise))*(1.08-0.25*y/max(1,h))
                img.putpixel((x1+x,y1+y), col(base,f,a0))
    return p

def stripes(base, line, every=2, noise=0.06, vertical=False):
    def p(img, face, rect, rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                k=(x-x1) if vertical else (y-y1)
                c=line if k%every==every-1 else base
                img.putpixel((x,y), col(c, SHADE[face]*(1+rng.uniform(-noise,noise))))
    return p

def chainp(base, dark):
    def p(img, face, rect, rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                k=(x-x1)+(y-y1)
                c=dark if k%3==0 else base
                img.putpixel((x,y), col(c, SHADE[face]*(1+rng.uniform(-.08,.08))))
    return p

def glow(c):
    def p(img, face, rect, rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2): img.putpixel((x,y), col(c, rng.uniform(.92,1.05)))
    return p

def frame(base, edge, noise=0.07):
    """테두리만 다른 색"""
    def p(img, face, rect, rng):
        x1,y1,x2,y2=rect
        for y in range(y1,y2):
            for x in range(x1,x2):
                e = x in(x1,x2-1) or y in(y1,y2-1)
                img.putpixel((x,y), col(edge if e else base, SHADE[face]*(1+rng.uniform(-noise,noise))))
    return p

def hide_groups(path, names):
    m=json.load(open(path))
    ids=[]
    def walk(n):
        if isinstance(n,str): return
        if n['name'] in names:
            n['visibility']=False
            def coll(x):
                if isinstance(x,str): ids.append(x)
                else:
                    x['visibility']=False
                    for c in x['children']: coll(c)
            for c in n['children']: coll(c)
        for c in n['children']: walk(c)
    for n in m['outliner']: walk(n)
    for e in m['elements']:
        if e['uuid'] in ids: e['visibility']=False
    json.dump(m, open(path,'w'), indent=1, ensure_ascii=False)
