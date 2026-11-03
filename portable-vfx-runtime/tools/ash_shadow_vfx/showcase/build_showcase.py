"""재의 그림자 전체 패턴 미리보기: VFX 팩 합치기 + 예고(미리보기 전용) + 보스·잡몹 모델 모션 glb + 패턴별 장면."""
import json, os, shutil, math, subprocess, sys
sys.path.insert(0, os.path.dirname(__file__))
import bb2glb

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = '/home/claude/boss/portable-vfx-runtime/assets'
MODELS = '/home/claude/boss/magic-mythic-paper/models/ash_shadow'
OUT = os.path.join(HERE, 'merged'); shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT + '/textures'); os.makedirs(OUT + '/models')

# ------------------------------------------------------------------ 1. 팩 합치기
packs = sorted(p for p in os.listdir(ASSETS) if p.startswith(('AS_', 'AM_'))) 
merged = None
for p in packs:
    src = os.path.join(HERE, 'tg/out/TG') if p == 'TG' else os.path.join(ASSETS, p)
    v = json.load(open(os.path.join(src, 'vfx.json'))); pre = p.lower()
    if merged is None:
        merged = {k: v[k] for k in v}; merged.update(name='AshShadow_AllPatterns', materials={}, meshes={}, systems=[], models={}, scale=None, sequence=None, salvo=None, width=None)
    for mk, m in v['materials'].items():
        m = dict(m); tex = m['texture']; nt = 'textures/' + pre + '__' + os.path.basename(tex)
        shutil.copy(os.path.join(src, tex), os.path.join(OUT, nt)); m['texture'] = nt
        merged['materials'][pre + '__' + mk] = m
    for mk, m in (v.get('meshes') or {}).items(): merged['meshes'][pre + '__' + mk] = m
    for s in v['systems']:
        s = json.loads(json.dumps(s)); s['id'] = pre + '__' + s['id']
        for L in s['layers']:
            L['material'] = pre + '__' + L['material']
            r = L.get('render') or {}
            if r.get('mesh'): r['mesh'] = pre + '__' + r['mesh']
        merged['systems'].append(s)
    for k in ('required',):
        merged['features'][k] = sorted(set(merged['features'].get(k, [])) | set(v['features'].get(k, [])))
merged['features']['byLayer'] = {}
SYS = {s['id'] for s in merged['systems']}
def sid(x):
    assert x in SYS or x.startswith('tg__'), x; return x

# ------------------------------------------------------------------ 2. 모델 glb
M = [bb2glb.Model(os.path.join(MODELS, f)) for f in ('ash_shadow.bbmodel', 'ash_shadow_enraged.bbmodel')]
for f in ('ash_hound', 'wax_brute', 'lantern_stag', 'ash_hound_shiny', 'wax_brute_shiny', 'lantern_stag_shiny'):
    M.append(bb2glb.Model(os.path.join(MODELS, 'minions', f + '.bbmodel')))
B, E, HOUND, BRUTE, STAG, HOUNDS, BRUTES, STAGS = range(8)
CLIPS = {}
def clip(name, *segs):  # segs = (model, anim, start)
    CLIPS[name] = [(m, a, s, None) for m, a, s in segs]; return name

# ------------------------------------------------------------------ 3. 장면
DEV = [[9, 0, 0], [-4.5, 0, -7.79], [-4.5, 0, 7.79]]   # 장치 자리 (보스 정면 +Z 기준 90°·210°·330°, 반경 9)
def dev_prop(p, t=0, gone=None):
    d = {"c": [p[0], 0.9, p[2]], "e": [0.45, 0.9, 0.45], "shade": [1.0, 0.85, 0.45], "t": t}
    if gone is not None: d["gone"] = gone
    return d
def actor(path, hidden=True, height=1.8): return {"path": path, "hidden": hidden, "height": height}
def still(p, yaw=0): return [[0, p, yaw]]
def ev(t, system, actor=0, **kw): d = {"t": round(t, 3), "system": sid(system), "actor": actor}; d.update(kw); return d
def cam(target, dist, yaw, pitch): return {"target": target, "dist": dist, "yaw": yaw, "pitch": pitch}
SC = {}
def scene(key, label, length, clipname, path, actors, events, props=None, camera=None, targetOffset=None):
    S = {"label": label, "interval": length, "from": [0, 0, 0], "to": [0, 0, 0], "normal": [0, 1, 0], "flight": 0.01, "anchor": [0, 0, 0], "hits": [],
         "path": path, "actors": actors, "events": events, "character": {"clip": clipname, "start": 0}}
    if props is not None: S["props"] = props
    if camera: S["camera"] = camera
    if targetOffset: S["targetOffset"] = targetOffset
    SC[key] = S

BOSS = still([0, 0, 0])
def P(path): return actor(path, hidden=False)
WIDE = cam([0, 2, 4], 30, -35, 32)

# 등장 + 장치 3개
evs = [ev(0, 'as_fate__intro')]
for i, d in enumerate(DEV):
    evs += [ev(1.2 + 0.3 * i, 'as_device__respawn', 2 + i), ev(1.4 + 0.3 * i, 'as_device__light', 2 + i), ev(2.3, 'as_device__tether', 2 + i, to=0, from_=None)]
for e in evs:
    if 'from_' in e: e.pop('from_'); e['from'] = [0, 1.2, 0]
scene('intro', '등장 · 장치 3개 켜짐', 5.5, clip('c_intro', (B, 'spawn', 0), (B, 'idle', 3.4)), BOSS,
      [actor(BOSS)] + [P(still([0, 0, 11], 180))] + [actor(still(d)) for d in DEV], evs,
      props=[dev_prop(d, 1.2 + 0.3 * i) for i, d in enumerate(DEV)], camera=WIDE, targetOffset=[0, 3.2, 0])
# 장치 회복 상태
evs = []
for i in range(3): evs += [ev(0, 'as_device__light', 2 + i), ev(0, 'as_device__tether', 2 + i, to=0, **{"from": [0, 1.2, 0]})]
scene('heal', '1페이즈 대기 · 장치 3개 = 초당 4% 회복', 4, clip('c_idle', (B, 'idle', 0)), BOSS,
      [actor(BOSS), P(still([0, 0, 11], 180))] + [actor(still(d)) for d in DEV], evs, props=[dev_prop(d) for d in DEV], camera=WIDE, targetOffset=[0, 3.2, 0])
# 기본 공격
scene('claw', '기본 공격 · 앞팔 할퀴기 (부채꼴 r6 150°, 예고 0.4초)', 2.2, clip('c_claw', (B, 'attack', 0)), BOSS,
      [actor(BOSS), P(still([1, 0, 4.5], 180))], [ev(0, 'tg__tg_cone6', yaw=0), ev(0.4, 'as_clawswipe__swipe', yaw=0)], camera=cam([0, 2, 3], 18, -40, 30))
# 1 잿빛 돌진 ×2 (장치 파괴 시연)
dpath = [[0, [0, 0, 0], 0], [0.6, [0, 0, 0], 0], [0.9, [0, 0, 15], 0], [1.6, [0, 0, 15], 180], [2.2, [0, 0, 15], 180], [2.5, [0, 0, 0], 180]]
dev1 = [1.2, 0, 8]
scene('dash', '[유도] 잿빛 돌진 ×2 (직선 18×4, 예고 0.6초) · 판정 안 장치 파괴', 3.4, clip('c_dash', (B, 'skill_dash', 0), (B, 'skill_dash', 1.6)), dpath,
      [actor(dpath), P([[0, [-1.5, 0, 9], 180], [0.45, [-3.6, 0, 9.5], 180], [1.6, [-3.6, 0, 9.5], 180], [2.0, [-1, 0, 6], 200]]), actor(still(dev1))],
      [ev(0, 'as_ashdash__windup', yaw=0), ev(0, 'tg__tg_line18', yaw=0), ev(0.6, 'as_ashdash__dash', yaw=0), ev(0.6, 'as_device__break', 2),
       ev(1.6, 'as_ashdash__windup', yaw=180), ev(1.6, 'tg__tg_line18', yaw=180), ev(2.2, 'as_ashdash__dash', yaw=180)],
      props=[dev_prop(dev1, 0, 0.62)], camera=cam([0, 2, 8], 30, -70, 30))
# 2 그림자 손 내려찍기 ×3
dev2 = [-0.8, 0, 10]
hits = [0.55, 1.15, 1.75]; yaws = [0, -8, 8]
evs = []
for h, y in zip(hits, yaws): evs += [ev(h - 0.45, 'tg__tg_line14', yaw=y), ev(h, 'as_handslam__slam', yaw=y)]
evs.append(ev(1.15, 'as_device__break', 2))
scene('slam', '[유도] 그림자 손 내려찍기 ×3 (직선 14×4, 예고 0.45초씩)', 3.0, clip('c_slam', (B, 'skill_slam', 0)), BOSS,
      [actor(BOSS), P([[0, [2.5, 0, 8], 180], [0.4, [3.2, 0, 8], 180], [1.0, [-2.8, 0, 8], 180], [1.6, [2.6, 0, 9], 180]]), actor(still(dev2))], evs,
      props=[dev_prop(dev2, 0, 1.17)], camera=cam([0, 2, 6], 26, -55, 32))
# 3 추적하는 재 ×4
ppath = [[0, [-4, 0, 9], 180], [2.4, [4, 0, 9], 180]]
evs = [ev(0, 'as_ashrain__cast')]
for k in range(4):
    t0 = 0.6 + 0.3 * k
    evs += [ev(t0, 'tg__tg_c4', 1), ev(t0 + 0.25, 'as_ashrain__fall', 1)]
scene('rain', '[유도] 추적하는 재 ×4 (원 r4, 그때 대상 위치, 예고 0.5초)', 3.0, clip('c_rain', (B, 'skill_ash_rain', 0)), BOSS,
      [actor(BOSS), P(ppath)], evs, camera=cam([0, 2, 6], 26, -40, 34))
# 4 잠행 → 솟구침
spath = [[0, [0, 0, 0], 0], [1.09, [0, 0, 0], 0], [1.1, [0, 0, 9], 0]]
scene('sink', '[유도] 잠행 → 솟구침 (원 r5 예고 1.1초 → 원 r8 0.4초)', 3.4, clip('c_sink', (B, 'skill_sink', 0), (B, 'skill_emerge', 1.1)), spath,
      [actor(spath), P([[0, [0, 0, 9], 180], [0.7, [0, 0, 9], 180], [1.0, [4.5, 0, 12], 200], [1.5, [8.5, 0, 13], 200]])],
      [ev(0, 'as_shadowsink__sink'), ev(0, 'tg__tg_c5s', 1), ev(1.1, 'as_shadowsink__emerge'), ev(1.1, 'tg__tg_c8')],
      camera=cam([0, 2, 6], 28, -45, 32))
# 5 그림자 손아귀
scene('grasp', '[견제] 그림자 손아귀 (원 r6, 예고 0.5초 → 속박 1초 + 끌어당김)', 2.6, clip('c_grasp', (B, 'skill_grasp', 0)), BOSS,
      [actor(BOSS), P([[0, [0, 0, 10], 180], [1.5, [0, 0, 10], 180], [1.8, [0, 0, 4], 180]])],
      [ev(0, 'tg__tg_c6', 1), ev(0.5, 'as_grasp__grasp', 1)], camera=cam([0, 2, 5], 24, -50, 30))
# 6 잔상 → 직전 패턴(손 내려찍기) 한 번 더
evs = [ev(0, 'as_afterimage__flicker')]
for h, y in zip(hits, yaws): evs += [ev(0.5 + h - 0.45, 'tg__tg_line14', yaw=y), ev(0.5 + h, 'as_handslam__slam', yaw=y)]
scene('afterimage', '[견제] 잔상 → 0.5초 뒤 직전 패턴(예: 내려찍기) 한 번 더', 3.4, clip('c_after', (B, 'skill_afterimage', 0), (B, 'skill_slam', 0.5)), BOSS,
      [actor(BOSS), P(still([2.5, 0, 9], 180))], evs, camera=cam([0, 2, 6], 26, -55, 32))
# 7 재의 장막
scene('veil', '[견제] 재의 장막 (실명 1.5초 + 각자 발밑 원 r7 ×2, 예고 0.7초)', 3.0, clip('c_veil', (B, 'skill_veil', 0)), BOSS,
      [actor(BOSS), P([[0, [0, 0, 10], 180], [1.0, [0, 0, 10], 180], [1.3, [5, 0, 12], 200]])],
      [ev(0, 'as_ashveil__wrap'), ev(0.6, 'tg__tg_c7', 1), ev(1.3, 'as_ashveil__burst', 1), ev(1.1, 'tg__tg_c7', 1), ev(1.8, 'as_ashveil__burst', 1)],
      camera=cam([0, 2, 6], 30, -45, 32))
# 8 화장의 고리: 안 → 팔각 바깥 → 안
evs = [ev(0.05, 'tg__tg_c5'), ev(0.65, 'as_cremationring__inner'), ev(1.25, 'as_cremationring__outer'), ev(1.25, 'tg__tg_c5'), ev(1.85, 'as_cremationring__inner')]
for s in range(8):
    a = math.radians(s * 45 + 22.5); rx, rz = math.cos(a), math.sin(a); tx, tz = -math.sin(a), math.cos(a)
    sx, sz = rx * 8.5 - tx * 5, rz * 8.5 - tz * 5
    evs.append(ev(0.65, 'tg__tg_seg', **{"from": [-sx, 0, sz], "yaw": round(math.degrees(math.atan2(-tx, tz)), 2)}))
scene('ring', '[견제] 화장의 고리 (안 r5 → 바깥 r5~12 → 안, 예고 0.6초씩)', 2.8, clip('c_ring', (B, 'skill_ring', 0)), BOSS,
      [actor(BOSS), P([[0, [0, 0, 3], 180], [0.55, [0, 0, 7], 180], [1.15, [0, 0, 3], 180]])], evs, camera=cam([0, 2, 0], 34, -35, 45))
# 9 빛의 수호
scene('ward', '[견제] 빛의 수호 (장치 하나 2초 보호막)', 3.0, clip('c_ward', (B, 'skill_ward', 0)), still([0, 0, 0], 90),
      [actor(still([0, 0, 0], 90)), P(still([0, 0, 11], 180))] + [actor(still(d)) for d in DEV],
      [ev(0, 'as_device__light', 2 + i) for i in range(3)] + [ev(0.4, 'as_ward__shield', 2)], props=[dev_prop(d) for d in DEV], camera=cam([4, 2, 0], 22, -30, 28))
# 10 발악 (장치 0개 직후)
evs = []
for w, off in ((0, 0), (1, 22.5)):
    for k in range(8): evs.append(ev(0.3 + 0.6 * w, 'tg__tg_line10', yaw=k * 45 + off))
    evs.append(ev(0.8 + 0.6 * w, 'as_desperate__scream', yaw=off))
scene('desperate', '발악 · 장치가 0개가 된 직후 (8방향 직선 10×3 ×2, 예고 0.5초)', 2.8, clip('c_desp', (B, 'skill_desperate', 0)), BOSS,
      [actor(BOSS), P(still([2, 0, 6], 180))], evs, camera=cam([0, 2, 0], 30, -35, 45))
# 11 50% 그림자 탄막: 서버(AshShadowBoss.Barrage)와 같은 시간표로 25초 전체 + 그로기 진입
T0 = 1.5; ARENA = 16; EDGE = ARENA + 1
evs = [ev(0, 'as_fate__vanish'), ev(0.4, 'as_fate__appear'), ev(T0, 'as_barrage__start'), ev(T0, 'as_barrage__aura')]
import random; rng = random.Random(7)
def fire(age, x, z, deg, spt):
    a = math.radians(deg); vx, vz = -math.sin(a), math.cos(a); spd = spt * 20
    b = x * vx + z * vz; c = x * x + z * z - EDGE * EDGE; d = -b + math.sqrt(b * b - c)   # 경기장 밖까지 거리
    d = min(d, spd * 8)
    evs.append(ev(T0 + age / 20, 'as_barrage__shard', **{"from": [round(x, 2), 1.0, round(z, 2)], "to": [round(x + vx * d, 2), 1.0, round(z + vz * d, 2)], "flight": round(d / spd, 2), "normal": None, "impact": "none"}))
for age in range(0, 461):
    if age <= 70 and age % 6 == 0:
        for k in range(5): fire(age, 0, 0, k * 72 + age * 1.5, 0.30)
    elif 80 <= age <= 160 and (age - 80) % 20 == 0:
        off = 0 if ((age - 80) // 20) % 2 == 0 else 11.25
        for k in range(16): fire(age, 0, 0, k * 22.5 + off, 0.26)
    elif 170 <= age <= 260 and age % 4 == 0:
        b0 = (age - 170) * 3.5; fire(age, 0, 0, b0, 0.30); fire(age, 0, 0, b0 + 180, 0.30)
    elif 270 <= age <= 340 and (age - 270) % 10 == 0:
        b0 = (age - 270) * 1.5
        for k in range(4):
            for j in range(3): fire(age + max(1, j * 3), 0, 0, k * 90 + b0, 0.34)
    elif 350 <= age <= 420 and (age - 350) % 35 == 0:
        rot = ((age - 350) // 35) * 30; r = ARENA - 1; gap = rng.randrange(6)
        for side in range(6):
            if side == gap: continue
            a0, a1 = math.radians(rot + side * 60), math.radians(rot + side * 60 + 60)
            for j in range(6):
                u = (j + 0.5) / 6; x = (math.cos(a0) * (1 - u) + math.cos(a1) * u) * r; z = (math.sin(a0) * (1 - u) + math.sin(a1) * u) * r
                fire(age, x, z, math.degrees(math.atan2(x, -z)), 0.22)
    elif 430 <= age <= 460 and (age - 430) % 15 == 0:
        off = (age - 430) // 15 * 5.0
        for k in range(24): fire(age, 0, 0, k * 15 + off, 0.36)
TEND = T0 + 25
evs += [ev(TEND + 0.45, 'as_groggy__fall'), ev(TEND + 1.1, 'as_groggy__stun')]
# 플레이어: 경기장 안을 돌며 피함
bpath = []; 
for i in range(0, 60):
    t = i * 0.5; ang = math.radians(150 + 40 * math.sin(t * 0.37) + t * 9); rr = 7 + 3 * math.sin(t * 0.6)
    bpath.append([round(t, 2), [round(-math.sin(ang) * rr, 2), 0, round(math.cos(ang) * rr, 2)], round(math.degrees(ang) + 180, 1)])
segs = [(B, 'barrage_start', T0)] + [(B, 'barrage_loop', T0 + 1.8 + 2.0 * k) for k in range(12)] + [(B, 'groggy_start', TEND), (B, 'groggy_loop', TEND + 1.1)]
scene('barrage', '50% · 그림자 탄막 25초 전체 (별 → 고리 → 2중 나선 → 십자 → 좁혀오는 육각 → 마무리 고리) → 그로기 · 두 번 맞으면 죽음', TEND + 3.5,
      clip('c_barrage', *segs), BOSS, [actor(BOSS), P(bpath)], evs, camera=cam([0, 1, 0], 40, -30, 60))
SC['barrage']['stops'] = {sid('as_barrage__aura'): TEND}
print('barrage shots', sum(1 for e in evs if e['system'] == 'as_barrage__shard'))
# 12 그로기 10초
scene('groggy', '탄막 버팀 → 그로기 10초 (공격 안 함, 극딜 구간)', 4.0, clip('c_groggy', (B, 'groggy_start', 0), (B, 'groggy_loop', 1.1)), BOSS,
      [actor(BOSS), P(still([0, 0, 6], 180))], [ev(0.45, 'as_groggy__fall'), ev(1.1, 'as_groggy__stun')], camera=cam([0, 2, 2], 22, -40, 25))
# 13 변신: 남은 장치 흡수 → 광폭화
evs = []
for i in range(3):
    evs += [ev(0, 'as_device__light', 2 + i), ev(0.6 + 0.6 * i, 'as_absorb__beam', 2 + i, to=0, **{"from": [0, 1.2, 0]}), ev(0.6 + 0.6 * i, 'as_device__break', 2 + i)]
evs += [ev(3.0, 'as_absorb__burst'), ev(3.0, 'as_absorb__enrage')]
scene('transform', '변신 · 남은 장치를 하나씩 흡수 → 모델 교체 → 광폭화 등장', 6.6, clip('c_trans', (B, 'transform', 0), (E, 'spawn', 3.0)), BOSS,
      [actor(BOSS), P(still([0, 0, 12], 180))] + [actor(still(d)) for d in DEV], evs,
      props=[dev_prop(d, 0, 0.62 + 0.6 * i) for i, d in enumerate(DEV)], camera=WIDE, targetOffset=[0, 3.2, 0])
# 14 광폭화 내려찍기 4연타
evs = [ev(0, 'as_absorb__enrage')]
for k, h in enumerate([0.64, 1.24, 1.84, 2.44]):
    y = [0, -10, 10, 0][k]; evs += [ev(h - 0.34, 'tg__tg_line14', yaw=y), ev(h, 'as_handslam__slam', yaw=y)]
scene('enraged_slam', '광폭화 · 손 내려찍기 4연타 (예고 25% 짧음, 흡수 3개면 5연타)', 3.4, clip('c_eslam', (E, 'skill_slam', 0)), BOSS,
      [actor(BOSS), P([[0, [2.5, 0, 8], 180], [0.5, [-2.5, 0, 8], 180], [1.1, [2.6, 0, 9], 180], [1.7, [-2.4, 0, 8], 180]])], evs,
      camera=cam([0, 2, 6], 26, -55, 32))
# 15 광폭화 돌진
scene('enraged_dash', '광폭화 · 잿빛 돌진 (다리 없이 부유, 더 빠르게)', 2.4, clip('c_edash', (E, 'skill_dash', 0)), [[0, [0, 0, 0], 0], [0.45, [0, 0, 0], 0], [0.68, [0, 0, 15], 0]],
      [actor([[0, [0, 0, 0], 0], [0.45, [0, 0, 0], 0], [0.68, [0, 0, 15], 0]]), P([[0, [-1.5, 0, 9], 180], [0.35, [-3.6, 0, 9.5], 180]])],
      [ev(0, 'as_absorb__enrage'), ev(0, 'as_ashdash__windup'), ev(0, 'tg__tg_line18', yaw=0), ev(0.45, 'as_ashdash__dash', yaw=0)], camera=cam([0, 2, 8], 30, -70, 30))
# 16 처치
scene('death', '처치 · 재로 흩어짐', 3.8, clip('c_death', (E, 'death', 0)), BOSS, [actor(BOSS), P(still([0, 0, 8], 180))],
      [ev(0.5, 'as_fate__death')], camera=cam([0, 2, 2], 24, -40, 25))

# 잡몹 (기본 + 이로치)
for shiny in (False, True):
    sfx = '_shiny' if shiny else ''; tag = ' (이로치)' if shiny else ''
    scene('hound' + sfx, '잡몹 · 재 사냥개 물어뜯기 (체술, 0.38초 판정)' + tag, 1.6, clip('c_hound' + sfx, (HOUNDS if shiny else HOUND, 'attack', 0)), BOSS,
          [actor(BOSS), P(still([0, 0, 2.6], 180))], [ev(0.38, 'am_houndbite__bite' + sfx, yaw=0)], camera=cam([0, 1, 1.5], 8, -50, 20))
    scene('brute' + sfx, '잡몹 · 촛농 짐승 내려찍기 (원 r4.5, 예고 0.75초)' + tag, 2.2, clip('c_brute' + sfx, (BRUTES if shiny else BRUTE, 'attack', 0)), BOSS,
          [actor(BOSS), P(still([1.5, 0, 3.5], 180))], [ev(0, 'tg__tg_c45'), ev(0.75, 'am_brutestomp__stomp' + sfx)], camera=cam([0, 1, 1], 14, -45, 30))
    scene('stag' + sfx, '잡몹 · 등불 사슴 잿불탄 (대상 원 r2.5, 예고 1.5초)' + tag, 2.8, clip('c_stag' + sfx, (STAGS if shiny else STAG, 'attack', 0)), BOSS,
          [actor(BOSS), P(still([0, 0, 10], 180))],
          [ev(0, 'am_staglantern__charge' + sfx), ev(0, 'tg__tg_c25', 1),
           ev(0.75, 'am_staglantern__bolt' + sfx, **{"from": [0, 2.2, 1.0], "to": [0, 0.3, 10], "flight": 0.75, "normal": [0, 1, 0]}),
           ev(1.5, 'am_staglantern__hit' + sfx, 1)], camera=cam([0, 1.5, 5], 16, -60, 25))

for S in SC.values(): S['events'] = [e for e in S['events'] if not e['system'].startswith('tg__')]
bb2glb.build(M, CLIPS, os.path.join(OUT, 'models/ash_showcase.glb'))
merged['demo'] = {"note": "예고 장판은 기존 시스템(chatelegraph)이 그림 - 여기선 VFX·모션만", "interval": 3, "bg": [0.07, 0.06, 0.08], "floor": [0.08, 0.07, 0.085], "grid": [0.06, 0.05, 0.07],
                  "dummy": {"height": 1.8}, "character": {"file": "models/ash_showcase.glb", "clip": "c_idle", "start": 0, "shading": {"mode": "lambert", "ambient": 0.75, "diffuse": 0.55}},
                  "scenarios": SC}
json.dump(merged, open(os.path.join(OUT, 'vfx.json'), 'w'), ensure_ascii=False)
print(len(merged['systems']), 'systems', len(SC), 'scenes')
r = subprocess.run(['python3', os.path.join(HERE, 'engine/build_preview.py'), OUT, os.path.join(HERE, 'ash_shadow_all_patterns.html'), '--accent', '#ff7a2a'], capture_output=True, text=True)
print(r.stdout, r.stderr[-2000:])
