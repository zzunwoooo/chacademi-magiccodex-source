import json
O='/mnt/user-data/outputs/'
a=json.load(open(O+'pumpkin_reaper.bbmodel')); b=json.load(open(O+'pumpkin_reaper_shiny.bbmodel'))
els={e['uuid']:{'n':e['name'],'f':e['from'],'t':e['to'],'r':e['rotation'],'o':e['origin'],'i':e.get('inflate',0),
     'uv':{k:v['uv'] for k,v in e['faces'].items()}} for e in a['elements']}
def ol(g):
    return {'n':g['name'],'o':g['origin'],'r':g.get('rotation',[0,0,0]),
            'c':[ol(c) if isinstance(c,dict) else c for c in g['children']]}
root=[ol(g) for g in a['outliner'] if g.get('export',True)]
anims=[]
for an in a['animations']:
    tr={}
    for v in an['animators'].values():
        for k in v['keyframes']:
            d=k['data_points'][0]
            tr.setdefault(v['name'],{}).setdefault(k['channel'],[]).append([k['time'],float(d['x']),float(d['y']),float(d['z']),k['interpolation']])
    for bn in tr.values():
        for ch in bn.values(): ch.sort(key=lambda x:x[0])
    anims.append({'name':an['name'].split('.')[-1],'len':an['length'],'loop':an['loop'],'tr':tr})
DATA={'W':a['resolution']['width'],'H':a['resolution']['height'],'els':els,'root':root,'anims':anims,
      'tex':[a['textures'][0]['source'],b['textures'][0]['source']]}
html=open('viewer_template.html',encoding='utf-8').read().replace('/*DATA*/null',json.dumps(DATA,separators=(',',':')))
open(O+'pumpkin_reaper_preview.html','w',encoding='utf-8').write(html)
print(len(html))
