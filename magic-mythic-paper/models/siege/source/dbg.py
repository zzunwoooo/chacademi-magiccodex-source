"""빠른 포즈 확인용 렌더(면 단위 단색). python3 dbg.py file.bbmodel out.png anim:t,t,... [yaw] [scale]"""
import json, base64, io, math, sys
from PIL import Image, ImageDraw
import numpy as np
from render import rotm
def samp(kfs, ch, t, d):
    k = sorted([k for k in kfs if k['channel'] == ch], key=lambda k: k['time'])
    if not k: return d
    v = lambda k: [float(k['data_points'][0][a]) for a in 'xyz']
    if t <= k[0]['time']: return v(k[0])
    for a, b in zip(k, k[1:]):
        if t <= b['time']:
            f = (t - a['time']) / ((b['time'] - a['time']) or 1); return [p + (q - p) * f for p, q in zip(v(a), v(b))]
    return v(k[-1])
def frame(m, tex, anim, t, yaw, size, scale, hi=()):
    el = {e['uuid']: e for e in m['elements']}; A = {}
    if anim:
        a = [x for x in m['animations'] if x['name'].endswith('.' + anim)][0]; A = {v['name']: v['keyframes'] for v in a['animators'].values()}
    polys = []
    def walk(g, M, R0, mark):
        if g.get('visibility') is False: return
        k = A.get(g['name'], []); o = np.array(g['origin'], float)
        p = samp(k, 'position', t, [0, 0, 0]); r = samp(k, 'rotation', t, [0, 0, 0]); s = samp(k, 'scale', t, [1, 1, 1])
        r = [-r[0], -r[1], r[2]]; p = [-p[0], p[1], p[2]]
        if max(s) < .01: return
        R = rotm([a + b for a, b in zip(g.get('rotation', [0, 0, 0]), r)])
        M2 = (lambda M, o, R, s, p: (lambda q: M(o + R @ (np.array(s) * (q - o)) + np.array(p))))(M, o, R, s, p)
        mk = mark or g['name'] in hi
        for c in g['children']:
            if isinstance(c, str):
                e = el[c]
                if e.get('visibility') is False: continue
                oc = np.array(e['origin'], float); Rc = rotm(e['rotation']); f, tt = np.array(e['from'], float), np.array(e['to'], float)
                X0, Y0, Z0 = f; X1, Y1, Z1 = tt
                F = {'north': ((X1, Y1, Z0), (X0, Y1, Z0), (X0, Y0, Z0), (X1, Y0, Z0)), 'south': ((X0, Y1, Z1), (X1, Y1, Z1), (X1, Y0, Z1), (X0, Y0, Z1)),
                     'east': ((X1, Y1, Z1), (X1, Y1, Z0), (X1, Y0, Z0), (X1, Y0, Z1)), 'west': ((X0, Y1, Z0), (X0, Y1, Z1), (X0, Y0, Z1), (X0, Y0, Z0)),
                     'up': ((X1, Y1, Z1), (X0, Y1, Z1), (X0, Y1, Z0), (X1, Y1, Z0)), 'down': ((X1, Y0, Z0), (X0, Y0, Z0), (X0, Y0, Z1), (X1, Y0, Z1))}
                for face, fd in e['faces'].items():
                    uv = fd['uv']; x1, x2 = sorted([uv[0], uv[2]]); y1, y2 = sorted([uv[1], uv[3]])
                    if x2 <= x1 or y2 <= y1: continue
                    reg = tex[int(y1):int(y2), int(x1):int(x2)].reshape(-1, 4).astype(float); w = reg[:, 3:4]
                    if w.sum() < 1: continue
                    col = (reg[:, :3] * w).sum(0) / w.sum()
                    if mk: col = col * .5 + np.array([255, 60, 200]) * .5
                    polys.append(([M2(oc + Rc @ (np.array(q, float) - oc)) for q in F[face]], col, face))
            else: walk(c, M2, None, mk)
    for g in m['outliner']:
        if g.get('export', True): walk(g, lambda q: q, None, False)
    yw = math.radians(yaw); pt = math.radians(-14)
    def proj(p):
        x, y, z = p; xr = x * math.cos(yw) - z * math.sin(yw); zr = x * math.sin(yw) + z * math.cos(yw)
        return (size[0] / 2 - xr * scale, size[1] - 30 - (y * math.cos(pt) - zr * math.sin(pt)) * scale, y * math.sin(pt) + zr * math.cos(pt))
    img = Image.new('RGB', size, (228, 220, 205)); d = ImageDraw.Draw(img); out = []
    L = {'north': 1, 'south': .75, 'east': .85, 'west': .85, 'up': 1.08, 'down': .6}
    for pts, col, face in polys:
        pp = [proj(p) for p in pts]; out.append((sum(p[2] for p in pp) / 4, [(p[0], p[1]) for p in pp], tuple(int(min(255, v * L[face])) for v in col)))
    out.sort(key=lambda q: -q[0])
    for z, pp, c in out: d.polygon(pp, fill=c, outline=tuple(int(v * .8) for v in c))
    d.line([(0, size[1] - 30), (size[0], size[1] - 30)], fill=(150, 120, 100))
    d.text((4, 4), f'{anim} {t:.2f} yaw{yaw}', fill=(0, 0, 0)); return img
def strip(path, out, jobs, yaws=(-35, -90), size=(300, 380), scale=1.5, hi=()):
    m = json.load(open(path)); tex = np.array(Image.open(io.BytesIO(base64.b64decode(m['textures'][0]['source'].split(',')[1]))).convert('RGBA'))
    rows = []
    for anim, ts in jobs:
        for yaw in yaws:
            row = Image.new('RGB', (size[0] * len(ts), size[1]))
            for i, t in enumerate(ts): row.paste(frame(m, tex, anim, t, yaw, size, scale, hi), (i * size[0], 0))
            rows.append(row)
    W = max(r.width for r in rows); s = Image.new('RGB', (W, size[1] * len(rows)), (255, 255, 255))
    for i, r in enumerate(rows): s.paste(r, (0, i * size[1]))
    s.save(out); return out
