import sys, os, json, importlib
import kit
from kit import REG, use, ELEM
import gen
O='/mnt/user-data/outputs/gimmicks/'
mods=sys.argv[1:] or ['models_a','models_b','models_c']
for m in mods: importlib.import_module(m)
if os.path.exists(gen.HITBOX_JSON): os.remove(gen.HITBOX_JSON)
out=[]
for r in REG:
    for v in (r['variants'] or [r['elem']]):
        use(v)
        id_ = r['id'] if not r['variants'] else f"{r['id']}_{v}"
        root,an=r['build']()
        def an2(G, an=an, base=r['id'], id_=id_):
            res=an(G)
            for a in res: a['name']=a['name'].replace(f'animation.{base}.',f'animation.{id_}.')
            return res
        gen.export(id_, root, an2, 1, O+id_+'.bbmodel', r['hitbox'])
        out.append(dict(id=id_, base=r['id'], ko=r['ko']+(f" ({ {'fire':'화염','wind':'바람','water':'물','earth':'대지','light':'빛','dark':'어둠'}[v]})" if r['variants'] else ''),
                        cat=r['cat'], elem=v, desc=r['desc'], anims=r['anims_desc']))
json.dump(out,open(O+'index.json','w'),ensure_ascii=False,indent=1)
