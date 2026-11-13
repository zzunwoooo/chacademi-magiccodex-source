"""100년 전 공성전 레이드 잡몹 v2 — 악마가 부리는 네발 마수 8종 (성문으로 쏟아져 나오는 무리).
v2: 통짜 상자 몸을 버리고 '깊은 가슴 - 잘록한 허리 - 솟은 골반' + 짐승 관절(역관절) 다리 + 숙인 목으로 다시 만듦. 크기 2~3배. 개체별 짤패턴 모션.
전부 쇠 목줄(불씨 낙인·끊긴 사슬) = 악마에게 부려지는 짐승.
공통: spawn(제자리 등장) / portal_out(지옥문 포탈에서 튀어나옴 — VFX 팩 Siege_Portal 과 짝) / idle / walk / attack / death
  siege_hound   지옥견      근접·도약   attack 물기 / lunge_bite 도약 물어뜯기 / howl 무리 울음
  siege_ram     뿔 돌격수   근접·돌진   attack 뿔 치켜올리기 / charge_ready·charge_run·charge_hit 직선 돌진
  siege_lizard  가시 도마뱀 원거리      attack 꼬리 쓸기 / spine_volley 등 가시 발사
  siege_toad    불씨 두꺼비 원거리·끌당 attack 불씨 뱉기 / tongue_pull 혀로 끌어당기기 / burst 자폭
  siege_twin    쌍두 마견   근접·원거리 attack 두 번 물기 / twin_breath 쌍두 화염 숨결
  siege_behemoth 공성 거수  공성        attack 지진 발구르기 / gate_ram 성문 들이받기
  siege_stalker 날개 추적자 도약·급강하 attack 할퀴기 / pounce 도약 덮치기 / dive 급강하
  siege_stinger 독침 마수   원거리·텔포 attack 꼬리 찌르기 / sting_shot 독침 발사 / blink_out·blink_in 땅속 이동
"""
from common import *
from weapon_ik import _sample
import gen as _gen
import math, os, sys

def _pack(cubes):
    for W, H in [(64, 64), (128, 64), (128, 128), (256, 128), (256, 256), (512, 256), (512, 512), (1024, 512), (1024, 1024)]:
        x = y = rowh = 0; ok = True
        for c in sorted(cubes, key=lambda c: -(c.size[1] + c.size[2])):
            w, h, d = c.size; bw, bh = 2 * (w + d), d + h
            if x + bw > W: x = 0; y += rowh; rowh = 0
            if y + bh > H: ok = False; break
            c.uv = [x, y]; x += bw; rowh = max(rowh, bh)
        if ok: return W, H
    raise Exception('too big')
_gen.pack = _pack

PAL = {}
h5 = lambda v: round(v * 2) / 2
def B(name, c, s, paint, m=False, **kw):
    """상자: 시작점 c(0.5 단위로 반올림) + 정수 크기 s."""
    c = [h5(v) for v in c]; s = [int(round(v)) for v in s]
    return C(name, c, [c[0] + s[0], c[1] + s[1], c[2] + s[2]], paint, m, **kw)

# ---------------- 페인터 ----------------
def pelt(k1='HIDE', k2='DHIDE', vein=.008, streak=.45):
    """털가죽: 위→아래 어두워짐 + 세로 털결 + 가장자리 음영 + 불씨 핏줄."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        st = [rng.random() for _ in range(w)]
        for ly in range(h):
            g = ly / max(1, h - 1) if face not in ('up', 'down') else (.1 if face == 'up' else .95)
            for lx in range(w):
                c = lerp(PAL[k1], PAL[k2], g * .85)
                if st[lx] < streak * .5 and (ly + int(st[lx] * 40)) % 5 < 3: c = lerp(c, PAL[k2], .45)
                elif st[lx] > 1 - streak * .25 and (ly + lx) % 4 == 0: c = lerp(c, PAL['HL'], .5)
                f = SHADE[face] * (1 + rng.uniform(-.06, .06))
                if min(lx, w - 1 - lx, ly, h - 1 - ly) == 0 and w > 3 and h > 3: f *= .82
                img.putpixel((x1 + lx, y1 + ly), col(c, f))
        for _ in range(int(w * h * vein + rng.random() * .6)):
            if w < 4 or h < 4: break
            x = rng.randrange(w); y = rng.randrange(h)
            for s in range(rng.randint(3, 7)):
                if 0 <= x < w and 0 <= y < h: img.putpixel((x1 + x, y1 + y), col(PAL['EMBER2'] if s == 0 else PAL['EMBER'], rng.uniform(.8, 1)))
                x += rng.choice([-1, 0, 1]); y += rng.choice([0, 1, 1])
    return p
def scales(k1='HIDE', k2='DHIDE', n=4):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            g = ly / max(1, h - 1) if face not in ('up', 'down') else (.1 if face == 'up' else .9)
            for lx in range(w):
                c = lerp(PAL[k1], PAL[k2], g * .7)
                if (lx + ly) % n == 0 or (lx - ly) % n == 0: c = lerp(c, PAL[k2], .75)
                elif (lx + ly) % n == 1 and (lx - ly) % n == 1: c = lerp(c, PAL['HL'], .6)
                f = SHADE[face] * (1 + rng.uniform(-.05, .05))
                if min(lx, w - 1 - lx, ly, h - 1 - ly) == 0 and w > 3 and h > 3: f *= .84
                img.putpixel((x1 + lx, y1 + ly), col(c, f))
    return p
def ribbed(base):
    """갈비뼈 사이로 속불이 비치는 옆구리."""
    def p(img, face, r, rng):
        base(img, face, r, rng)
        if face not in ('east', 'west'): return
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for lx in range(2, w - 2, 3):
            for ly in range(int(h * .3), h - 2):
                sl = int((ly - h * .3) * .25)
                xx = lx + (sl if face == 'east' else -sl)
                if 1 <= xx < w - 1:
                    img.putpixel((x1 + xx, y1 + ly), col(PAL['EMBER'] if (ly % 3) else PAL['EMBER2'], rng.uniform(.8, 1)))
                    if xx + 1 < w - 1: img.putpixel((x1 + xx + 1, y1 + ly), col(PAL['BONE'], SHADE[face] * rng.uniform(.8, 1)))
    return p
def glow(k='EMBER'): return sol(PAL, k, .03)
def bonep(): return grad(PAL, 'BONE', 'DBONE')
def hornp(): return stripes(PAL, 'HORN', 'DHORN', 3, 1, vertical=False)
def dark(): return sol(PAL, 'DHIDE', .07)
def iron(): return trim(PAL, 'IRON', 'DIRON')
def plate():
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                e = min(lx, w - 1 - lx, ly, h - 1 - ly)
                c = PAL['DIRON'] if e == 0 else PAL['IRON']
                if e == 1 and (lx + ly) % 4 == 0 and w > 4 and h > 4: c = PAL['BONE']
                elif e > 1 and rng.random() < .05: c = lerp(PAL['IRON'], PAL['EMBER'], .5)   # 달궈진 얼룩
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.07, .07))))
    return p
def fur(n=3, k='MANE', tip=None):
    """갈기: 아래쪽 끝이 들쭉날쭉(투명), tip 색으로 끝 물들임."""
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        cut = [rng.randint(0, n) for _ in range(w)] if face not in ('up', 'down') else [0] * w
        for lx in range(w):
            for ly in range(h):
                if ly >= h - cut[lx]: img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                c = lerp(PAL[k], PAL['DHIDE'], rng.choice([.1, .3, .5]))
                if tip and face != 'down' and ly < 2: c = PAL[tip]
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.1, .1))))
    return p
def teeth(up=False):
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for lx in range(w):
            for ly in range(h):
                gap = face in ('east', 'west') and lx % 2 == 1 and ((ly == 0) if up else (ly == h - 1))
                img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0) if gap else col(PAL['BONE'] if (ly < h - 1) != up or h == 1 else PAL['DBONE'], SHADE[face]))
    return p
def membrane():
    def p(img, face, r, rng):
        x1, y1, x2, y2 = r; w, h = x2 - x1, y2 - y1
        for ly in range(h):
            for lx in range(w):
                if face not in ('up', 'down'): img.putpixel((x1 + lx, y1 + ly), col(PAL['DHIDE'], SHADE[face])); continue
                u = lx / max(1, w - 1); v = ly / max(1, h - 1)
                if v > .62 + .36 * abs(math.sin(u * math.pi * 3)): img.putpixel((x1 + lx, y1 + ly), (0, 0, 0, 0)); continue
                c = PAL['DHIDE'] if (lx % max(3, w // 3) == 0 or ly < 2) else lerp(PAL['MANE'], PAL['DHIDE'], v * .8)
                if rng.random() < .01: c = PAL['EMBER']
                img.putpixel((x1 + lx, y1 + ly), col(c, SHADE[face] * (1 + rng.uniform(-.06, .06))))
    return p

# ---------------- 다리 (역관절: 각도 누적으로 발이 바닥에 닿도록 높이를 계산) ----------------
def leg_height(segs, ph):
    a = 0; y = ph
    for l, ang, w in segs: a += ang; y += l * math.cos(math.radians(a))
    return h5(y)
def leg_reach(segs):
    a = 0; z = 0
    for l, ang, w in segs: a += ang; z -= l * math.sin(math.radians(a))
    return z
def make_leg(s, pref, names, segs, x, z, sp, skin):
    m, P, Rt = side(s); ph = sp.get('ph', 3); pw = sp.get('pw', segs[-1][2] + 2); pl = sp.get('pl', pw + 3)
    py = leg_height(segs, ph); ys = [py]
    for l, a, w in segs: ys.append(ys[-1] - l)
    tot = sum(a for l, a, w in segs)
    toes = sp.get('toes', 3); tw = max(1, pw // toes)
    pk = [B(f'{s}_{pref}_pad', (x - pw / 2, ys[-1] - ph, z - pl + pw / 2 + 1), (pw, ph, pl - 2), dark(), m)]
    for i in range(toes):
        tx = x - pw / 2 + i * (pw - tw) / max(1, toes - 1)
        pk += [B(f'{s}_{pref}_toe{i+1}', (tx, ys[-1] - ph, z - pl + pw / 2 - 1), (tw, max(1, ph - 1), 2), dark(), m),
               B(f'{s}_{pref}_claw{i+1}', (tx + (tw - 1) / 2, ys[-1] - ph, z - pl + pw / 2 - 1 - sp.get('claw', 3)), (1, 1, sp.get('claw', 3)), sol(PAL, 'CLAW'), m)]
    g = Group(f'{s}_{pref}_paw', P([x, ys[-1], z]), pk, [-tot, 0, 0])
    for i in reversed(range(len(segs))):
        l, a, w = segs[i]; nm = names[i]
        k = [B(f'{s}_{nm}', (x - w / 2, ys[i + 1] - 1, z - w / 2), (w, l + 2, w), skin if i == 0 else (pelt('DHIDE', 'DHIDE', 0) if i == 2 else skin), m)]
        if i == 0:   # 어깨/엉덩이 근육 덩어리
            bw = w + 2
            k.append(B(f'{s}_{pref}_muscle', (x - w / 2 + 1, ys[1] + l * .35, z - bw / 2 - 1), (w, int(l * .7) + 3, bw + 2), skin, m, rot=[-10 if pref == 'front' else 12, 0, -6], origin=[x, py, z]))
            if sp.get('spur'): k.append(B(f'{s}_{pref}_spur', (x + w / 2, ys[1] + l * .5, z - 1), (sp['spur'], 2, 2), bonep(), m, rot=[0, 0, 30], origin=[x + w / 2, ys[1] + l * .5, z]))
        if i == 1:
            k.append(B(f'{s}_{pref}_joint', (x - w / 2 - .5, ys[1] - 2, z - w / 2 - .5), (w + 1, 3, w + 1), dark(), m))
            if pref == 'hind' or sp.get('elbow'): k.append(B(f'{s}_{pref}_hook', (x - .5, ys[1] - 2, z + w / 2), (1, 2, sp.get('hook', 3)), bonep(), m, rot=[25, 0, 0], origin=[x, ys[1] - 1, z + w / 2]))
        if i == 2 and sp.get('fetlock'): k.append(B(f'{s}_{pref}_fetlock', (x - w / 2 - 1, ys[i + 1], z - w / 2 - 1), (w + 2, 3, w + 2), fur(2), m))
        k.append(g)
        g = Group(f'{s}_{nm}', P([x, ys[i], z]), k, [a, 0, 0])
    return g

# ---------------- 머리 ----------------
def make_head(sp, K, sfx='', dx=0, yaw=0):
    NL, NW, NH, HW, HH, HL, MW, MH, ML = [sp[k] for k in ('NL', 'NW', 'NH', 'HW', 'HH', 'HL', 'MW', 'MH', 'ML')]
    skin = K['skin']; ny = K['fy'] + sp.get('neck_y', 0); zN = K['zS'] - sp['CL'] / 2 + 2; JT = sp.get('JT', 2)
    z0 = zN - NL + 1; top = ny + HH / 2; ty = ny - HH / 2; zc = z0 - HL; zm = zc - ML
    jk = [B('jaw' + sfx, (dx - MW, ty - 2 - JT, zm + 1), (MW * 2, JT, ML + 2), dark()),
          B('chin' + sfx, (dx - MW + 1, ty - 3 - JT, zm + 3), (MW * 2 - 2, 1, max(2, ML - 3)), dark()),
          B('tongue' + sfx, (dx - 1, ty - 2, zm + 2), (2, 1, ML - 1), sol(PAL, 'TONGUE')),
          B('right_low_teeth' + sfx, (dx + MW - 2, ty - 2, zm + 2), (1, 2, max(2, ML - 3)), teeth(True)),
          B('left_low_teeth' + sfx, (dx - MW + 1, ty - 2, zm + 2), (1, 2, max(2, ML - 3)), teeth(True)),
          B('right_low_fang' + sfx, (dx + MW - 2, ty - 2, zm + 1), (1, 3, 1), bonep()), B('left_low_fang' + sfx, (dx - MW + 1, ty - 2, zm + 1), (1, 3, 1), bonep())]
    jaw = Group('jaw' + sfx, [dx, ty - 1, zc + 2], jk)
    ey = ty + MH
    hk = [B('cranium' + sfx, (dx - HW, ty, zc), (HW * 2, HH, HL + 1), skin),
          B('brow' + sfx, (dx - HW, top - 1, zc - 1), (HW * 2, 2, 3), dark()),
          B('muzzle' + sfx, (dx - MW, ty, zm), (MW * 2, MH, ML), skin),
          B('muzzle_ridge' + sfx, (dx - MW + 1, ty + MH, zm + 2), (MW * 2 - 2, 1, ML - 2), dark()),
          B('nose' + sfx, (dx - 1, ty + MH - 2, zm - 1), (2, 2, 1), dark()),
          B('right_eye' + sfx, (dx + HW - 2, ey, zc - .2), (2, 1, 0), glow('EYE')), B('left_eye' + sfx, (dx - HW, ey, zc - .2), (2, 1, 0), glow('EYE')),
          B('right_eye_side' + sfx, (dx + HW + .2, ey, zc), (0, 1, 2), glow('EYE')), B('left_eye_side' + sfx, (dx - HW - .2, ey, zc), (0, 1, 2), glow('EYE')),
          B('right_teeth' + sfx, (dx + MW - 1, ty - 2, zm + 1), (1, 2, max(2, ML - 2)), teeth()), B('left_teeth' + sfx, (dx - MW, ty - 2, zm + 1), (1, 2, max(2, ML - 2)), teeth()),
          B('right_fang' + sfx, (dx + MW - 1, ty - sp.get('fang', 4), zm), (1, sp.get('fang', 4), 1), bonep()), B('left_fang' + sfx, (dx - MW, ty - sp.get('fang', 4), zm), (1, sp.get('fang', 4), 1), bonep()),
          B('right_cheek' + sfx, (dx + HW, ty + 1, zc + 1), (1, HH - 3, HL - 2), dark()), B('left_cheek' + sfx, (dx - HW - 1, ty + 1, zc + 1), (1, HH - 3, HL - 2), dark()), jaw]
    head = Group('head' + sfx, [dx, ny, z0], hk, [sp.get('hr', 0), 0, 0])
    zc2 = zN - NL / 2
    nk = [B('neck' + sfx, (dx - NW, ny - NH / 2, zN - NL), (NW * 2, NH, NL + 3), skin),
          B('throat' + sfx, (dx - NW + 1, ny - NH / 2 - 2, zN - NL + 2), (NW * 2 - 2, 2, NL), pelt('BELLY', 'DHIDE', 0)),
          B('collar' + sfx, (dx - NW - 1, ny - NH / 2 - 1, zc2 - 1), (NW * 2 + 2, NH + 2, 2), iron()),
          B('collar_brand' + sfx, (dx - 1, ny + NH / 2 + 1, zc2 - 1), (2, 1, 2), glow()),
          B('collar_spike_r' + sfx, (dx + NW + 1, ny - 1, zc2 - 1), (2, 2, 2), bonep()), B('collar_spike_l' + sfx, (dx - NW - 3, ny - 1, zc2 - 1), (2, 2, 2), bonep()),
          B('chain1' + sfx, (dx - 1, ny - NH / 2 - 4, zc2 - .5), (2, 3, 1), sol(PAL, 'DIRON')), B('chain2' + sfx, (dx - .5, ny - NH / 2 - 7, zc2 - 1), (1, 3, 2), sol(PAL, 'DIRON'), rot=[0, 0, 12], origin=[dx, ny - NH / 2 - 4, zc2]),
          B('chain3' + sfx, (dx - 1, ny - NH / 2 - 10, zc2 - .5), (2, 3, 1), sol(PAL, 'DIRON'), rot=[14, 0, 20], origin=[dx, ny - NH / 2 - 4, zc2]), head]
    neck = Group('neck' + sfx, [dx, ny, zN], nk, [sp.get('nr', 0), yaw, 0])
    return neck, dict(hk=hk, jk=jk, nk=nk, dx=dx, top=top, ty=ty, zc=zc, zm=zm, z0=z0, ny=ny, zN=zN, ey=ey, sfx=sfx, HW=HW, HH=HH, HL=HL, MW=MW, MH=MH, ML=ML, NW=NW, NH=NH, NL=NL)

# ---------------- 몸 ----------------
def beast(sp, extra=None):
    skin = (scales if sp.get('scaly') else pelt)(); ph = sp.get('ph', 3)
    fy, hy = leg_height(sp['fl'], ph), leg_height(sp['hl'], ph)
    BL, CW, CD, CL, WW, WD, PW, PD, PL, RL, WH = [sp[k] for k in ('BL', 'CW', 'CD', 'CL', 'WW', 'WD', 'PW', 'PD', 'PL', 'RL', 'WH')]
    zS, zH = -BL / 2, BL / 2; ct, pt = fy + 4, hy + 3; wt = h5(min(ct, pt) - 1 + sp.get('arch', 0)); wz0, wz1 = zS + CL / 2, zH - PL / 2
    K = dict(skin=skin, fy=fy, hy=hy, zS=zS, zH=zH, ct=ct, pt=pt, wt=wt, sp=sp)
    belly = pelt('BELLY', 'DHIDE', 0)
    ck = [B('ribcage', (-CW, ct - CD, zS - CL / 2), (CW * 2, CD, CL), ribbed(skin) if sp.get('ribs', True) else skin),
          B('keel', (-CW + 2, ct - CD - 2, zS - CL / 2 + 1), (CW * 2 - 4, 2, CL - 4), belly),
          B('brisket', (-CW + 1, ct - CD + 3, zS - CL / 2 - 2), (CW * 2 - 2, CD - 5, 2), skin),
          B('withers', (-CW + 2, ct, zS - CL / 2 + 1), (CW * 2 - 4, WH, int(CL * .7)), skin),
          B('right_scapula', (CW - 1, ct - int(CD * .45), zS - 3), (2, int(CD * .45) + 2, 6), dark(), rot=[-14, 0, -8], origin=[CW, ct, zS]),
          B('left_scapula', (-CW - 1, ct - int(CD * .45), zS - 3), (2, int(CD * .45) + 2, 6), dark(), rot=[-14, 0, 8], origin=[-CW, ct, zS])]
    necks = []
    for sfx, dx, yaw in sp.get('heads', [('', 0, 0)]):
        ng, H = make_head(sp, K, sfx, dx, yaw); necks.append(H); ck.append(ng)
    for s in ('right', 'left'):
        ck.append(make_leg(s, 'front', ('front_leg', 'front_forearm', 'front_wrist'), sp['fl'], CW - sp['fl'][0][2] / 2 + sp.get('fx', 2), zS, sp, skin))
    chest = Group('chest', [0, h5(wt - WD / 2), wz0], ck)
    # 꼬리
    tg = None; segs = sp['tail']; z = zH + PL / 2 + RL - 1 + sum(l for l, w, r in segs)
    for i in reversed(range(len(segs))):
        l, w, r = segs[i]; z -= l; ty = pt - 2 + sp.get('tail_y', 0)
        k = [B(f'tail{i+1}', (-w / 2, ty - w / 2, z), (w, w, l + 1), skin)]
        if sp.get('tail_spikes') and i % 2 == 0 and w >= 2: k.append(B(f'tail_spike{i+1}', (-.5, ty + w / 2, z + 1), (1, sp['tail_spikes'], 2), bonep(), rot=[-25, 0, 0], origin=[0, ty + w / 2, z + 2]))
        if i == len(segs) - 1 and sp.get('tip'): k += sp['tip'](z + l, ty)
        if tg: k.append(tg)
        tg = Group('tail' if i == 0 else f'tail{i+1}', [0, ty, z], k, [r, 0, 0])
    bk = [B('pelvis', (-PW, pt - PD, zH - PL / 2), (PW * 2, PD, PL), skin),
          B('rump', (-PW + 1, pt - PD + 3, zH + PL / 2), (PW * 2 - 2, PD - 4, RL), skin),
          B('waist', (-WW, wt - WD, wz0 - 1), (WW * 2, WD, int(round(wz1 - wz0)) + 2), ribbed(skin) if sp.get('ribs', True) else skin),
          B('underbelly', (-WW + 1, wt - WD - 1, wz0), (WW * 2 - 2, 1, max(1, int(round(wz1 - wz0)) - 1)), belly),
          B('right_hip_bone', (PW - 1, pt - 2, zH - 2), (2, 3, 4), bonep(), rot=[0, 0, -20], origin=[PW, pt, zH]), B('left_hip_bone', (-PW - 1, pt - 2, zH - 2), (2, 3, 4), bonep(), rot=[0, 0, 20], origin=[-PW, pt, zH]),
          chest, tg]
    # 등줄기 가시/갈기
    kind, n, hgt = sp.get('ridge', ('spike', 8, 4)); rk = []
    def topy(z): return ct + WH if z < zS + CL * .2 else (ct if z < wz0 else (wt if z < wz1 else pt))
    for i in range(n):
        z = zS - CL / 2 + 2 + (BL + CL / 2 + PL / 2 - 4) * i / max(1, n - 1); y = topy(z); hh = max(2, int(round(hgt * (1 - .5 * i / n) + (i % 2))))
        if kind == 'spike': c = B(f'spine{i+1}', (-.5 if i % 2 else -1, y - 1, z), (1 if i % 2 else 2, hh + 1, 2), bonep(), rot=[-28 - 4 * (i % 3), 0, (6 if i % 2 else -6)], origin=[0, y, z + 1])
        elif kind == 'fin': c = B(f'fin{i+1}', (-.5, y - 1, z - 1), (1, hh + 1, 4), fur(2, 'MANE', 'EMBER'), rot=[-20, 0, 0], origin=[0, y, z + 1])
        else: c = B(f'mane{i+1}', (-1.5, y - 1, z - 1), (3, hh + 1, 4), fur(3, 'MANE', sp.get('mane_tip')), rot=[-34, 0, (10 if i % 2 else -10)], origin=[0, y, z + 1])
        (ck if z < wz0 else bk).append(c)
    body = Group('body', [0, h5(pt - PD / 2), zH], bk)
    hind = [make_leg(s, 'hind', ('hind_leg', 'hind_shin', 'hind_hock'), sp['hl'], PW - sp['hl'][0][2] / 2 + sp.get('hx', 2), zH, sp, skin) for s in ('right', 'left')]
    root = Group('root', [0, 0, 0], [body] + hind)
    K.update(ck=ck, bk=bk, necks=necks, chest=chest, body=body, root=root, CW=CW, CD=CD, CL=CL, PW=PW, PD=PD, PL=PL, WW=WW, WH=WH, BL=BL, wz0=wz0, wz1=wz1, RL=RL, WD=WD)
    if extra: extra(K)
    return root, K

# ---------------- 개체별 외형 ----------------
def ear(H, h=5, lean=-30, w=2, out=14):
    d, t, z = H['dx'], H['top'], H['z0']; s = H['sfx']
    return [B('right_ear' + s, (d + H['HW'] - w, t - 1, z - 3), (w, h, 2), dark(), rot=[lean, 0, -out], origin=[d + H['HW'] - 1, t, z - 2]),
            B('left_ear' + s, (d - H['HW'], t - 1, z - 3), (w, h, 2), dark(), rot=[lean, 0, out], origin=[d - H['HW'] + 1, t, z - 2])]
def horn_chain(name, x, y, z, segs, paint, m=False):
    """뿔: (크기 w, 길이 l, 회전) 마디를 이어 붙인다 — 중첩 그룹이라 자연스럽게 휜다."""
    g = None; ys = [y]
    for w, l, r in segs: ys.append(ys[-1] + l)
    for i in reversed(range(len(segs))):
        w, l, r = segs[i]
        k = [B(f'{name}{i+1}', (x - w / 2, ys[i], z - w / 2), (w, l + 1, w), paint if i < len(segs) - 1 else glow(), m)]
        if g: k.append(g)
        g = Group(f'{name}{i+1}' if i else name, [-x if m else x, ys[i], z], k, [r[0], -r[1] if m else r[1], -r[2] if m else r[2]])
    return g

def x_hound(K):   # 지옥견: 마른 사냥개. 갈비 사이 속불, 불붙은 갈기, 찢긴 귀, 불씨 꼬리
    H = K['necks'][0]; ct, zS = K['ct'], K['zS']
    H['hk'] += ear(H, 7, -40, 2, 20) + [B('scar', (H['dx'] + 1, H['top'], H['zc'] + 2), (1, 1, 4), glow())]
    H['nk'] += [B(f'ruff{i+1}', (-2 + (i % 2), H['ny'] + H['NH'] / 2 - 1, H['zN'] - 2 - i * 3), (3, 5, 3), fur(3, 'MANE', 'EMBER'), rot=[-40, 0, -8 + 8 * (i % 2)], origin=[0, H['ny'] + H['NH'] / 2, H['zN'] - i * 3]) for i in range(3)]
    K['ck'] += [B('right_shoulder_spur', (K['CW'], ct - 3, zS - 1), (4, 2, 2), bonep(), rot=[0, 0, 35]), B('left_shoulder_spur', (-K['CW'] - 4, ct - 3, zS - 1), (4, 2, 2), bonep(), rot=[0, 0, -35])]
def x_ram(K):     # 뿔 돌격수: 들소 체형. 어깨 혹과 긴 갈기, 말린 큰 뿔, 쇠 이마판, 턱수염
    H = K['necks'][0]; ct, zS, CW = K['ct'], K['zS'], K['CW']; d, t, z0, zc, HW = H['dx'], H['top'], H['z0'], H['zc'], H['HW']
    K['ck'] += [B(f'hump_tuft{i+1}', (-7 + (i % 3) * 5, ct + 7, zS - 12 + (i // 3) * 6), (4, 7, 4), fur(4), rot=[-30 - 6 * (i // 3), 0, -14 + 14 * (i % 3)], origin=[-5 + (i % 3) * 5, ct + 8, zS - 10 + (i // 3) * 6]) for i in range(9)]
    K['ck'] += [B('hump_mane_side_r', (CW - 1, ct - 8, zS - 9), (2, 12, 12), fur(5)), B('hump_mane_side_l', (-CW - 1, ct - 8, zS - 9), (2, 12, 12), fur(5)),
                B('chest_plate', (-CW + 2, ct - K['CD'] + 4, zS - K['CL'] / 2 - 3), (CW * 2 - 4, 10, 1), plate())]
    for s in ('right', 'left'):
        m = s == 'left'
        H['hk'].append(horn_chain(f'{s}_horn', HW, t - 4, z0 - 4, [(5, 5, (-20, 0, -70)), (5, 6, (40, 0, -10)), (4, 6, (60, 0, 0)), (4, 5, (60, 0, 0)), (3, 5, (55, 0, 10)), (2, 4, (40, 0, 10))], hornp(), m))
    H['hk'] += [B('skull_plate', (d - HW - 1, t - 3, zc - 2), (HW * 2 + 2, 4, H['HL'] + 1), plate()), B('plate_spike1', (d - 1, t + 1, zc - 1), (2, 4, 2), bonep(), rot=[-30, 0, 0]),
                B('plate_spike2', (d - 1, t + 1, zc + 3), (2, 3, 2), bonep(), rot=[-20, 0, 0]),
                B('nose_ring', (d - 2, H['ty'] - 1, H['zm'] - 1), (4, 3, 1), sol(PAL, 'DIRON')), B('tusk_r', (d + H['MW'], H['ty'] - 1, H['zm'] + 1), (1, 5, 2), bonep(), rot=[20, 0, -15]), B('tusk_l', (d - H['MW'] - 1, H['ty'] - 1, H['zm'] + 1), (1, 5, 2), bonep(), rot=[20, 0, 15])]
    H['jk'] += [B('beard', (d - 2, H['ty'] - 12, H['zm'] + 3), (4, 8, 3), fur(4))]
def x_lizard(K):  # 가시 도마뱀: 길고 낮은 몸. 등에 긴 가시 다발(발사), 목 프릴, 곤봉 꼬리
    H = K['necks'][0]; ct, wt, pt, zS, zH = K['ct'], K['wt'], K['pt'], K['zS'], K['zH']; sk = []
    for i in range(9):
        z = zS - 6 + i * 5; y = ct if z < K['wz0'] else (wt if z < K['wz1'] else pt)
        for sx in (-1, 1):
            L = 12 - abs(i - 3) * 1
            sk.append(B(f'quill_{"r" if sx > 0 else "l"}{i+1}', (sx * 3 - .5, y - 1, z), (1, L, 1), stripes(PAL, 'BONE', 'MANE', 4, 1, vertical=False), rot=[-38 - i * 2, 0, -sx * (16 + 4 * (i % 3))], origin=[sx * 3, y, z + .5]))
    K['body'].children.append(Group('spines', [0, wt, 0], sk))
    d, t, z0, HW = H['dx'], H['top'], H['z0'], H['HW']
    H['nk'] += [B('frill_r', (H['NW'], H['ny'] - 6, H['zN'] - H['NL'] + 1), (9, 13, 1), fur(3, 'MANE', 'EMBER'), rot=[0, 30, -10], origin=[H['NW'], H['ny'], H['zN'] - H['NL'] + 1]),
                B('frill_l', (-H['NW'] - 9, H['ny'] - 6, H['zN'] - H['NL'] + 1), (9, 13, 1), fur(3, 'MANE', 'EMBER'), rot=[0, -30, 10], origin=[-H['NW'], H['ny'], H['zN'] - H['NL'] + 1])]
    H['hk'] += [B('crest', (d - .5, t, H['zc']), (1, 3, H['HL']), bonep()), B('horn_r', (d + HW - 1, t - 1, z0 - 2), (2, 2, 6), bonep(), rot=[20, -20, 0]), B('horn_l', (d - HW - 1, t - 1, z0 - 2), (2, 2, 6), bonep(), rot=[20, 20, 0])]
    for i, z in enumerate((zS - 6, zS + 2, zS + 12, zH - 6)):
        (K['ck'] if z < K['wz0'] else K['bk']).append(B(f'osteo_r{i+1}', (K['WW'] - 1, (ct if z < K['wz0'] else wt) - 4, z), (3, 3, 4), plate()))
        (K['ck'] if z < K['wz0'] else K['bk']).append(B(f'osteo_l{i+1}', (-K['WW'] - 2, (ct if z < K['wz0'] else wt) - 4, z), (3, 3, 4), plate()))
def x_toad(K):    # 불씨 두꺼비: 부푼 몸. 턱 밑 불씨 주머니, 등의 빛나는 종기, 눈자루, 늘어나는 혀
    H = K['necks'][0]; ct, wt, pt, zS, CW = K['ct'], K['wt'], K['pt'], K['zS'], K['CW']; d, t = H['dx'], H['top']
    K['chest'].children.append(Group('sac', [0, H['ty'] - 2, H['zN'] - 4], [
        B('sac', (-9, H['ty'] - 15, H['zN'] - 12), (18, 13, 12), glow()), B('sac_core', (-6, H['ty'] - 13, H['zN'] - 13), (12, 9, 1), glow('EMBER2')),
        B('sac_vein_r', (9, H['ty'] - 13, H['zN'] - 10), (1, 9, 8), dark()), B('sac_vein_l', (-10, H['ty'] - 13, H['zN'] - 10), (1, 9, 8), dark()), B('sac_band', (-10, H['ty'] - 9, H['zN'] - 13), (20, 2, 14), dark())]))
    for k, (x, z, s, y) in enumerate(((-9, -12, 5, ct), (4, -14, 4, ct), (-3, -6, 6, ct), (7, -4, 4, ct), (-10, 2, 4, wt), (1, 4, 5, wt), (6, 8, 3, pt), (-6, 10, 4, pt), (-1, -16, 3, ct))):
        (K['ck'] if z < K['wz0'] else K['bk']).append(B(f'boil{k+1}', (x, y - 1, z), (s, s - 1, s), glow() if k % 2 == 0 else dark(), rot=[0, 20 * (k % 3), 0]))
    H['hk'] += [B('eye_stalk_r', (d + H['HW'] - 5, t - 1, H['zc'] + 1), (5, 5, 5), dark()), B('eye_stalk_l', (d - H['HW'], t - 1, H['zc'] + 1), (5, 5, 5), dark()),
                B('big_eye_r', (d + H['HW'] - 4, t + 1, H['zc'] + .8), (3, 2, 0), glow('EYE')), B('big_eye_l', (d - H['HW'] + 1, t + 1, H['zc'] + .8), (3, 2, 0), glow('EYE')),
                B('lip', (d - H['MW'] - 1, H['ty'], H['zm'] - 1), (H['MW'] * 2 + 2, 2, 2), dark())]
    H['hk'].append(Group('tongue', [d, H['ty'] - 2, H['zc']], [B('tongue_long', (d - 2, H['ty'] - 3, H['zc'] - 4), (4, 2, 4), sol(PAL, 'TONGUE')),
                                                         Group('tongue_tip', [d, H['ty'] - 2, H['zc'] - 4], [B('tongue_tip', (d - 3, H['ty'] - 4, H['zc'] - 7), (6, 4, 3), glow())])]))
def x_twin(K):    # 쌍두 마견: 머리 둘. 두 목줄을 잇는 쇠 멍에, 한쪽은 뿔, 한쪽은 흉터
    ct, zS, CW = K['ct'], K['zS'], K['CW']
    for j, H in enumerate(K['necks']):
        H['hk'] += ear(H, 6, -36, 2, 18)
        H['nk'] += [B(f'ruff{H["sfx"]}{i+1}', (H['dx'] - 1.5, H['ny'] + H['NH'] / 2 - 1, H['zN'] - 2 - i * 4), (3, 6, 3), fur(3, 'MANE', 'EMBER'), rot=[-40, 0, 8 * (-1) ** i], origin=[H['dx'], H['ny'] + H['NH'] / 2, H['zN'] - i * 4]) for i in range(2)]
        if j == 0: H['hk'] += [B('brow_horn_r', (H['dx'] + 1, H['top'], H['zc']), (2, 6, 2), hornp(), rot=[-30, 0, -14]), B('brow_horn_l', (H['dx'] - 3, H['top'], H['zc']), (2, 6, 2), hornp(), rot=[-30, 0, 14])]
        else: H['hk'] += [B('scar2', (H['dx'] - H['HW'] - .2, H['ty'] + 2, H['zc'] + 1), (0, 5, 5), glow()), B('muzzle_iron2', (H['dx'] - H['MW'] - .5, H['ty'] + 1, H['zm'] + 3), (H['MW'] * 2 + 1, H['MH'], 2), iron())]
    y = K['necks'][0]['ny']; z = K['necks'][0]['zN'] - 6
    K['ck'] += [B('yoke', (-CW, y - 1, zS - K['CL'] / 2 - 1), (CW * 2, 3, 2), iron()), B('yoke_brand', (-2, y - 2, zS - K['CL'] / 2 - 2), (4, 5, 1), glow()),
                B('shoulder_plate_r', (CW - 1, ct - 5, zS - 6), (3, 7, 10), plate()), B('shoulder_plate_l', (-CW - 2, ct - 5, zS - 6), (3, 7, 10), plate())]
def x_behemoth(K):  # 공성 거수: 갑주 덩치. 엄니·코뿔, 등에 공성추와 화로 탑, 갈고리 사슬
    H = K['necks'][0]; ct, wt, pt, zS, zH, CW, PW = K['ct'], K['wt'], K['pt'], K['zS'], K['zH'], K['CW'], K['PW']; d, t, zc, zm, HW, MW = H['dx'], H['top'], H['zc'], H['zm'], H['HW'], H['MW']
    ty = ct + K['WH']
    K['ck'] += [B('back_armor', (-CW - 1, ct - 2, zS - 18), (CW * 2 + 2, 4, 36), plate()), B('side_armor_r', (CW, ct - 26, zS - 16), (3, 24, 30), plate()), B('side_armor_l', (-CW - 3, ct - 26, zS - 16), (3, 24, 30), plate()),
                B('tower_base', (-12, ty, zS - 6), (24, 4, 26), plate()), B('ram_log', (-3, ty + 4, zS - 46), (6, 6, 70), hornp()), B('ram_head', (-5, ty + 2, zS - 56), (10, 10, 10), plate()),
                B('ram_spike', (-2, ty + 5, zS - 64), (4, 4, 8), bonep()), B('ram_band1', (-4, ty + 3, zS - 30), (8, 8, 3), iron()), B('ram_band2', (-4, ty + 3, zS - 10), (8, 8, 3), iron())]
    for i, (x, z) in enumerate(((-11, zS - 5), (9, zS - 5), (-11, zS + 17), (9, zS + 17))):
        K['ck'].append(B(f'tower_post{i+1}', (x, ty + 4, z), (2, 20, 2), iron()))
    K['ck'] += [B('tower_roof', (-13, ty + 24, zS - 7), (26, 3, 28), plate()), B('brazier', (-4, ty + 27, zS + 2), (8, 4, 8), iron()), B('brazier_fire', (-3, ty + 31, zS + 3), (6, 6, 6), glow()), B('brazier_fire2', (-1, ty + 37, zS + 5), (2, 4, 2), glow('EMBER2')),
                B('banner_r', (13, ty + 6, zS - 4), (1, 18, 10), fur(4, 'MANE', 'EMBER')), B('banner_l', (-14, ty + 6, zS - 4), (1, 18, 10), fur(4, 'MANE', 'EMBER'))]
    for i, z in enumerate((zS - 14, zS - 2, zS + 10)):
        K['ck'] += [B(f'armor_spike_r{i+1}', (CW + 2, ct - 6, z), (3, 9, 3), bonep(), rot=[0, 0, -40]), B(f'armor_spike_l{i+1}', (-CW - 5, ct - 6, z), (3, 9, 3), bonep(), rot=[0, 0, 40]),
                    B(f'chain_r{i+1}', (CW + 3, ct - 40, z + 4), (1, 16, 2), sol(PAL, 'DIRON')), B(f'chain_l{i+1}', (-CW - 4, ct - 40, z + 4), (1, 16, 2), sol(PAL, 'DIRON')),
                    B(f'hook_r{i+1}', (CW + 2, ct - 44, z + 3), (3, 4, 4), bonep()), B(f'hook_l{i+1}', (-CW - 5, ct - 44, z + 3), (3, 4, 4), bonep())]
    K['bk'] += [B('rump_armor', (-PW - 1, pt - 4, zH - 12), (PW * 2 + 2, 6, 26), plate()), B('rump_side_r', (PW, pt - 22, zH - 8), (2, 18, 18), plate()), B('rump_side_l', (-PW - 2, pt - 22, zH - 8), (2, 18, 18), plate())]
    H['hk'] += [B('face_plate', (d - HW - 1, t - 7, zc - 3), (HW * 2 + 2, 9, H['HL'] + 2), plate()),
                horn_chain('nose_horn', 0, H['ty'] + H['MH'], zm + 4, [(6, 7, (-20, 0, 0)), (5, 7, (10, 0, 0)), (3, 6, (14, 0, 0))], hornp()),
                horn_chain('right_tusk', MW + 1, H['ty'] - 2, zm + 5, [(4, 8, (-100, 0, -20)), (3, 8, (30, 0, 0)), (2, 7, (36, 0, 0))], bonep()),
                horn_chain('left_tusk', MW + 1, H['ty'] - 2, zm + 5, [(4, 8, (-100, 0, -20)), (3, 8, (30, 0, 0)), (2, 7, (36, 0, 0))], bonep(), True),
                horn_chain('right_horn', HW, t - 3, zc + 8, [(6, 8, (-10, 0, -60)), (5, 8, (0, 0, 40)), (3, 7, (0, 0, 34))], hornp()),
                horn_chain('left_horn', HW, t - 3, zc + 8, [(6, 8, (-10, 0, -60)), (5, 8, (0, 0, 40)), (3, 7, (0, 0, 34))], hornp(), True)]
def x_stalker(K):  # 날개 추적자: 표범 체형. 박쥐 날개(2마디), 검치, 채찍 꼬리
    H = K['necks'][0]; ct, zS = K['ct'], K['zS']
    for s in ('right', 'left'):
        m, P, Rt = side(s)
        tipg = Group(f'{s}_wing2', P([30, ct + 1, zS - 2]), [B(f'{s}_wing2', (30, ct + .5, zS - 6), (30, 1, 26), membrane(), m), B(f'{s}_wing2_bone', (30, ct + 1, zS - 7), (30, 2, 2), dark(), m),
                                                              B(f'{s}_wing_hook', (58, ct + 1, zS - 10), (2, 2, 4), bonep(), m)], Rt([0, 0, -36]))
        K['chest'].children.append(Group(f'{s}_wing', P([4, ct, zS - 2]), [
            B(f'{s}_wing', (4, ct + .5, zS - 6), (26, 1, 30), membrane(), m), B(f'{s}_wing_bone', (4, ct + 1, zS - 7), (26, 2, 2), dark(), m),
            B(f'{s}_wing_thumb', (28, ct + 3, zS - 9), (2, 5, 2), bonep(), m), tipg], Rt([0, -28, 50])))
    H['hk'] += ear(H, 5, 0, 2, 30) + [B('saber_r', (H['dx'] + H['MW'] - 1, H['ty'] - 9, H['zm'] + 1), (1, 6, 2), bonep()), B('saber_l', (H['dx'] - H['MW'], H['ty'] - 9, H['zm'] + 1), (1, 6, 2), bonep()),
                               B('ear_tuft_r', (H['dx'] + H['HW'], H['top'] - 4, H['z0'] - 2), (3, 2, 1), fur(1)), B('ear_tuft_l', (H['dx'] - H['HW'] - 3, H['top'] - 4, H['z0'] - 2), (3, 2, 1), fur(1))]
def x_stinger(K):  # 독침 마수: 넓적한 갑각. 겹친 등판, 큰 집게턱, 눈 여섯, 머리 위로 말린 마디 꼬리와 독침
    H = K['necks'][0]; ct, wt, pt, zS, zH, CW = K['ct'], K['wt'], K['pt'], K['zS'], K['zH'], K['CW']; d, t, zc, zm, HW = H['dx'], H['top'], H['zc'], H['zm'], H['HW']
    for i in range(5):
        z = zS - 8 + i * 8; y = ct if z < K['wz0'] else (wt if z < K['wz1'] - 4 else pt); w = CW + 2 - abs(i - 1)
        (K['ck'] if z < K['wz0'] else K['bk']).append(B(f'shell{i+1}', (-w, y - 1, z), (w * 2, 3, 9), plate(), rot=[8, 0, 0], origin=[0, y, z + 9]))
        (K['ck'] if z < K['wz0'] else K['bk']).append(B(f'shell_spike_r{i+1}', (w - 1, y, z + 3), (5, 2, 2), bonep(), rot=[0, -20, 25]))
        (K['ck'] if z < K['wz0'] else K['bk']).append(B(f'shell_spike_l{i+1}', (-w - 4, y, z + 3), (5, 2, 2), bonep(), rot=[0, 20, -25]))
    for s in ('right', 'left'):
        m, P, Rt = side(s)
        H['hk'].append(Group(f'{s}_mandible', P([H['MW'] + 1, H['ty'], zc - 1]), [
            B(f'{s}_mandible', (H['MW'], H['ty'] - 2, zm - 6), (3, 3, H['ML'] + 6), bonep(), m), B(f'{s}_mandible_tip', (H['MW'] - 2, H['ty'] - 2, zm - 8), (3, 2, 3), glow(), m),
            B(f'{s}_mandible_barb', (H['MW'] + 3, H['ty'] - 1, zm - 2), (2, 1, 2), bonep(), m)], Rt([0, -14, 0])))
    H['hk'] += [B(f'eye{i}_r', (d + HW - 1 - i * 2, t - .2 + .3, zc + 1 + i * 2), (2, 0, 2), glow('EYE')) for i in range(2)] + [B(f'eye{i}_l', (d - HW - 1 + i * 2, t + .1, zc + 1 + i * 2), (2, 0, 2), glow('EYE')) for i in range(2)]

def sting_tip(z, y): return [B('stinger_bulb', (-4, y - 4, z), (8, 8, 9), glow('EMBER2')), B('stinger_bulb_band', (-5, y - 5, z + 3), (10, 10, 2), dark()),
                             B('stinger', (-1, y - 1, z + 9), (2, 2, 9), bonep()), B('stinger_tip', (-.5, y - .5, z + 18), (1, 1, 4), glow()), B('stinger_barb', (-.5, y - 4, z + 8), (1, 3, 5), bonep())]
def ember_tip(z, y): return [B('tail_tuft', (-2, y - 2, z), (4, 4, 6), fur(3, 'MANE', 'EMBER')), B('tail_ember', (-1, y - 1, z + 5), (2, 2, 4), glow()), B('tail_ember2', (-.5, y - .5, z + 9), (1, 1, 3), glow('EMBER2'))]
def club_tip(z, y): return [B('tail_club', (-5, y - 4, z), (10, 8, 11), plate()), B('club_spike_r', (5, y - 1, z + 3), (6, 2, 3), bonep()), B('club_spike_l', (-11, y - 1, z + 3), (6, 2, 3), bonep()),
                            B('club_spike_u', (-1, y + 4, z + 3), (2, 6, 3), bonep()), B('club_spike_b', (-1, y - 1, z + 11), (2, 2, 5), bonep())]
def blade_tip(z, y): return [B('tail_blade', (-.5, y - 4, z), (1, 8, 10), bonep()), B('tail_blade_tip', (-.5, y - 2, z + 10), (1, 4, 5), bonep())]
def twin_tip(z, y): return [B('tail_blade', (-.5, y - 4, z), (1, 8, 9), bonep()), B('tail_tuft', (-2, y - 2, z - 3), (4, 4, 4), fur(3, 'MANE', 'EMBER'))]
def horn_tip(z, y): return [B('tail_tuft', (-3, y - 3, z), (6, 6, 7), fur(4))]

RED = dict(HIDE=(150, 30, 32), DHIDE=(62, 10, 16), HL=(206, 70, 50), BELLY=(104, 22, 26), MANE=(40, 22, 26), BONE=(220, 204, 172), DBONE=(124, 108, 88), IRON=(46, 40, 50), DIRON=(20, 16, 22),
           EMBER=(255, 140, 40), EMBER2=(255, 222, 124), EYE=(255, 200, 60, 255), CLAW=(24, 16, 20), HORN=(70, 54, 56), DHORN=(26, 18, 22), TONGUE=(190, 50, 70))
def pal(**kw): d = dict(RED); d.update(kw); return d
MOBS = [
    dict(id='siege_hound', kr='지옥견', extra=x_hound, hit=(1.3, 2.6, 2.3), wl=.5, spawn='leap',
         sp=dict(fl=[(13, -22, 5), (13, 30, 3), (6, -24, 3)], hl=[(14, 24, 6), (13, -58, 4), (10, 46, 3)], BL=34, CW=6, CD=15, CL=18, WW=4, WD=7, PW=5, PD=10, PL=11, RL=4, WH=3, arch=2,
                 NL=12, NW=3, NH=7, nr=30, neck_y=1, HW=4, HH=7, HL=8, MW=3, MH=4, ML=10, hr=-38, fang=5, ridge=('mane', 9, 6), mane_tip='EMBER', spur=3,
                 tail=[(8, 3, 40), (8, 2, 12), (8, 2, -10), (8, 1, -16)], tip=ember_tip),
         pal=pal(), shiny=pal(HIDE=(52, 60, 84), DHIDE=(18, 22, 38), HL=(110, 130, 170), BELLY=(36, 42, 62), MANE=(220, 226, 236), EMBER=(90, 220, 255), EMBER2=(214, 250, 255), EYE=(120, 240, 255, 255))),
    dict(id='siege_ram', kr='뿔 돌격수', extra=x_ram, hit=(2.2, 3.9, 2.6), wl=.9, spawn='dig',
         sp=dict(fl=[(15, -12, 9), (13, 18, 7), (6, -10, 6)], hl=[(15, 16, 9), (12, -40, 6), (9, 30, 5)], BL=36, CW=12, CD=24, CL=24, WW=9, WD=15, PW=9, PD=15, PL=14, RL=4, WH=9, pw=8, toes=2, claw=2,
                 NL=8, NW=7, NH=13, nr=-22, neck_y=-4, HW=7, HH=11, HL=10, MW=5, MH=7, ML=9, hr=-8, fang=2, JT=3, ridge=('spike', 6, 5), fetlock=True, ribs=False,
                 tail=[(7, 3, 55), (7, 2, 12), (6, 2, 6)], tip=horn_tip),
         pal=pal(HIDE=(118, 44, 30), DHIDE=(50, 16, 12), HL=(170, 84, 50), BELLY=(90, 34, 26), MANE=(30, 18, 18)),
         shiny=pal(HIDE=(226, 220, 210), DHIDE=(132, 122, 118), HL=(255, 250, 240), BELLY=(190, 180, 170), MANE=(120, 30, 40), HORN=(230, 180, 70), DHORN=(140, 96, 30), EYE=(255, 60, 60, 255))),
    dict(id='siege_lizard', kr='가시 도마뱀', extra=x_lizard, hit=(2.2, 1.7, 1.2), wl=.7, spawn='dig',
         sp=dict(fl=[(8, -34, 6), (7, 52, 5), (3, -18, 4)], hl=[(9, 30, 7), (8, -62, 5), (5, 32, 4)], BL=44, CW=10, CD=11, CL=22, WW=9, WD=9, PW=8, PD=9, PL=14, RL=6, WH=1, fx=5, hx=5, pw=7, pl=11, claw=4, scaly=True, ribs=False,
                 NL=10, NW=6, NH=7, nr=8, neck_y=-1, HW=7, HH=6, HL=10, MW=6, MH=4, ML=12, hr=-12, fang=3, ridge=('spike', 4, 3),
                 tail=[(12, 8, 6), (12, 7, -4), (12, 6, -4), (11, 5, -3), (10, 4, 0), (9, 3, 4)], tail_y=-1, tail_spikes=4, tip=club_tip),
         pal=pal(HIDE=(104, 62, 36), DHIDE=(42, 20, 14), HL=(170, 120, 70), BELLY=(160, 118, 76), MANE=(176, 40, 30)),
         shiny=pal(HIDE=(40, 110, 96), DHIDE=(12, 46, 44), HL=(110, 200, 170), BELLY=(150, 200, 170), MANE=(240, 200, 80), EMBER=(160, 255, 120), EMBER2=(230, 255, 200), EYE=(255, 120, 220, 255))),
    dict(id='siege_toad', kr='불씨 두꺼비', extra=x_toad, hit=(2.4, 1.9, 1.5), wl=.8, spawn='leap',
         sp=dict(fl=[(9, -28, 6), (8, 52, 5), (3, -24, 4)], hl=[(11, 52, 9), (11, -112, 6), (6, 60, 5)], BL=22, CW=14, CD=19, CL=18, WW=14, WD=18, PW=12, PD=16, PL=12, RL=3, WH=2, fx=4, hx=5, pw=9, pl=12, claw=2, scaly=True, ribs=False,
                 NL=3, NW=11, NH=12, nr=14, neck_y=-3, HW=12, HH=9, HL=9, MW=12, MH=5, ML=7, hr=-4, fang=2, JT=3, ridge=('spike', 3, 2),
                 tail=[(4, 3, 30)]),
         pal=pal(HIDE=(126, 40, 28), DHIDE=(46, 12, 12), HL=(196, 96, 50), BELLY=(206, 116, 54), MANE=(60, 20, 16)),
         shiny=pal(HIDE=(70, 40, 110), DHIDE=(26, 12, 48), HL=(140, 100, 200), BELLY=(150, 110, 220), MANE=(30, 14, 50), EMBER=(120, 255, 170), EMBER2=(220, 255, 230), EYE=(255, 240, 120, 255))),
    dict(id='siege_twin', kr='쌍두 마견', extra=x_twin, hit=(2.0, 3.4, 3.0), wl=.7, spawn='leap',
         sp=dict(fl=[(16, -20, 7), (15, 28, 5), (7, -22, 4)], hl=[(17, 22, 8), (15, -56, 5), (11, 44, 4)], BL=40, CW=10, CD=19, CL=22, WW=7, WD=11, PW=8, PD=13, PL=13, RL=4, WH=4, arch=2,
                 NL=13, NW=4, NH=9, nr=32, neck_y=0, HW=5, HH=9, HL=9, MW=4, MH=5, ML=11, hr=-38, fang=5, heads=[('', 6, -18), ('2', -6, 18)], ridge=('spike', 9, 7), spur=4,
                 tail=[(9, 4, 34), (9, 3, 10), (9, 2, -8), (9, 2, -14)], tip=twin_tip),
         pal=pal(HIDE=(70, 28, 44), DHIDE=(24, 8, 18), HL=(130, 60, 84), BELLY=(46, 18, 30), MANE=(150, 30, 34), EYE=(255, 90, 40, 255)),
         shiny=pal(HIDE=(230, 226, 236), DHIDE=(140, 134, 164), HL=(255, 255, 255), BELLY=(200, 196, 214), MANE=(60, 120, 220), EMBER=(120, 180, 255), EMBER2=(226, 240, 255), EYE=(60, 160, 255, 255))),
    dict(id='siege_behemoth', kr='공성 거수', extra=x_behemoth, hit=(4.6, 7.6, 5.4), wl=1.8, spawn='dig',
         sp=dict(fl=[(30, -10, 16), (26, 16, 13), (10, -8, 11)], hl=[(30, 14, 17), (26, -36, 13), (14, 26, 11)], BL=72, CW=24, CD=46, CL=46, WW=20, WD=32, PW=20, PD=34, PL=30, RL=8, WH=8, ph=5, pw=16, pl=20, claw=5, fx=3, hx=3, ribs=False,
                 NL=12, NW=13, NH=24, nr=-20, neck_y=-10, HW=13, HH=20, HL=18, MW=9, MH=12, ML=18, hr=-4, fang=4, JT=5, ridge=('spike', 3, 4),
                 tail=[(14, 8, 55), (14, 6, 12), (13, 5, 8), (12, 4, 4)], tail_spikes=5, tip=club_tip),
         pal=pal(HIDE=(110, 34, 34), DHIDE=(42, 10, 14), HL=(170, 70, 56), BELLY=(84, 26, 28)),
         shiny=pal(HIDE=(44, 40, 60), DHIDE=(14, 12, 24), HL=(90, 84, 120), BELLY=(34, 30, 48), IRON=(214, 190, 110), DIRON=(130, 104, 44), EMBER=(190, 120, 255), EMBER2=(236, 214, 255), EYE=(200, 140, 255, 255))),
    dict(id='siege_stalker', kr='날개 추적자', extra=x_stalker, hit=(1.4, 2.8, 2.4), wl=.6, spawn='drop',
         sp=dict(fl=[(14, -20, 5), (14, 28, 4), (6, -24, 3)], hl=[(15, 24, 7), (14, -58, 4), (10, 46, 3)], BL=38, CW=7, CD=13, CL=18, WW=5, WD=8, PW=6, PD=10, PL=11, RL=4, WH=2, claw=5,
                 NL=10, NW=4, NH=7, nr=16, neck_y=0, HW=5, HH=7, HL=8, MW=4, MH=4, ML=6, hr=-24, fang=3, ridge=('fin', 7, 4), ribs=False, elbow=True,
                 tail=[(9, 3, 30), (9, 3, -6), (9, 2, -8), (9, 2, -10), (9, 2, -10), (8, 1, -8)], tip=blade_tip),
         pal=pal(HIDE=(50, 32, 58), DHIDE=(16, 8, 22), HL=(104, 70, 120), BELLY=(74, 48, 80), MANE=(124, 24, 40), EYE=(255, 70, 110, 255)),
         shiny=pal(HIDE=(236, 200, 120), DHIDE=(144, 104, 46), HL=(255, 240, 190), BELLY=(250, 230, 180), MANE=(40, 30, 30), EMBER=(255, 90, 90), EMBER2=(255, 200, 200), EYE=(60, 230, 200, 255))),
    dict(id='siege_stinger', kr='독침 마수', extra=x_stinger, hit=(2.0, 1.8, 1.3), wl=.7, spawn='dig',
         sp=dict(fl=[(10, -30, 6), (10, 46, 5), (4, -16, 4)], hl=[(11, 30, 7), (10, -64, 5), (6, 34, 4)], BL=34, CW=10, CD=11, CL=20, WW=8, WD=9, PW=9, PD=10, PL=14, RL=4, WH=1, fx=5, hx=5, pw=7, claw=4, toes=2, scaly=True, ribs=False,
                 NL=5, NW=6, NH=7, nr=0, neck_y=-2, HW=7, HH=7, HL=8, MW=5, MH=4, ML=6, hr=-8, fang=2, ridge=('spike', 2, 2),
                 tail=[(12, 8, -52), (12, 7, -36), (12, 6, -32), (11, 6, -28), (11, 5, -26), (10, 5, -22)], tail_y=0, tip=sting_tip),
         pal=pal(HIDE=(90, 32, 64), DHIDE=(32, 8, 24), HL=(160, 80, 120), BELLY=(124, 52, 84), EMBER=(190, 255, 80), EMBER2=(236, 255, 190), EYE=(190, 255, 80, 255)),
         shiny=pal(HIDE=(30, 70, 110), DHIDE=(8, 26, 48), HL=(90, 150, 200), BELLY=(60, 120, 160), EMBER=(255, 110, 200), EMBER2=(255, 220, 240), EYE=(255, 110, 200, 255))),
]

# ---------------- 애니메이션 ----------------
# 규약: 앞(-z)을 향한 몸통·목·머리는 +x = 들기 / 턱 -x = 벌림 / 다리 +x = 앞으로, -x = 뒤로 접힘
FL = ('right_front_leg', 'left_front_leg'); HLg = ('right_hind_leg', 'left_hind_leg')
def rotx(v, a):
    a = math.radians(a); return (v[0], v[1] * math.cos(a) - v[2] * math.sin(a), v[1] * math.sin(a) + v[2] * math.cos(a))
def follow(tr, length, K):
    """몸통(body)·가슴(chest)이 들리거나 숙여질 때 앞다리 어깨가 따라가도록 앞다리 position 키를 만든다 (가슴이 다리에서 빠지지 않게)."""
    # 앞다리는 chest 의 자식이라 자동으로 따라간다 → 할 일 없음. (구조 설명용으로 남김)
    return tr

def make_anims(M, K):
    sp = M['sp']; ID = M['id']; heads = sp.get('heads', [('', 0, 0)]); tn = len(sp['tail']); tails = ['tail'] + [f'tail{i+1}' for i in range(1, tn)]
    H = K['ct'] + K['WH']; big = ID == 'siege_behemoth'
    def AH(name, keys, delay=0):   # 모든 머리에 같은 키 (둘째 머리는 delay 만큼 늦게)
        return {name + sfx: [(k[0], round(k[1] + delay * j, 3), *k[2:]) if k[1] > 0 else k for k in keys] for j, (sfx, _, _) in enumerate(heads)}
    def legs4(f=None, h=None, fl=None, hs=None):
        d = {}
        if f: d.update({n: list(f) for n in FL})
        if h: d.update({n: list(h) for n in HLg})
        if fl: d.update({n: list(fl) for n in ('right_front_forearm', 'left_front_forearm')})
        if hs: d.update({n: list(hs) for n in ('right_hind_shin', 'left_hind_shin')})
        return d
    def anims(G):
        L = M['wl']; h, q = L / 2, L / 4; fa, ha, lift = (14, 12, 20) if big else (26, 22, 32)
        sw = lambda a: [R(0, a), R(h, -a), R(L, a)]
        lf = lambda t, m=1: [Z(0), R(round(t - q, 3)), R(t, -lift * m), R(round(min(L, t + q), 3)), Z(L)]
        walk = {'right_front_leg': sw(fa), 'left_hind_leg': sw(ha), 'left_front_leg': sw(-fa), 'right_hind_leg': sw(-ha),
                'right_front_forearm': lf(3 * q), 'left_hind_shin': lf(3 * q), 'left_front_forearm': lf(q), 'right_hind_shin': lf(q),
                'right_front_wrist': lf(3 * q, -.6), 'left_front_wrist': lf(q, -.6), 'left_hind_hock': lf(3 * q, -.5), 'right_hind_hock': lf(q, -.5),
                'body': [T(0), T(q, 0, -1), T(h), T(3 * q, 0, -1), T(L), R(0, 0, 0, 2), R(h, 0, 0, -2), R(L, 0, 0, 2)],
                'chest': [R(0, 0, -3), R(h, 0, 3), R(L, 0, -3)], 'tail': [R(0, 0, 12), R(h, 0, -12), R(L, 0, 12)],
                **AH('neck', [R(0, -4), R(q, 3), R(h, -4), R(3 * q, 3), R(L, -4)]), **AH('head', [R(0, 3), R(q, -2), R(h, 3), R(3 * q, -2), R(L, 3)])}
        if tn > 1: walk[tails[1]] = [R(0, 0, -10), R(h, 0, 10), R(L, 0, -10)]
        idle = {'chest': [T(0), T(1.5, 0, .8), T(3)], 'tail': osc('rotation', 3, -14, 14, 0, 4, 1), **AH('neck', [R(0, 0, -5), R(1.5, 4, 5), R(3, 0, -5)]),
                **AH('head', [R(0, 0, -6), R(1.5, 3, 6), R(3, 0, -6)]), **AH('jaw', [Z(0), R(1.5, -10), Z(3)])}
        if tn > 1: idle[tails[1]] = osc('rotation', 3, -10, 10, 1, 4, 1)
        if tn > 2: idle[tails[2]] = osc('rotation', 3, -10, 10, 2, 4, 1)
        if len(heads) > 1: idle['neck2'] = [R(0, 2, 8), R(1.5, -3, -6), R(3, 2, 8)]; idle['jaw2'] = [R(0, -12), R(1.5, 0), R(3, -12)]
        # ---- 등장 ----
        s = M['spawn']
        if s == 'leap':
            sl = .9; spawn = {'root': [T(0, 0, -H, 20), T(.3, 0, H * .6, 6), T(.5, 0, 0, 0), T(.9), S(0, .5, .5, .5), S(.3, 1, 1, 1)], 'body': [R(0, 34), R(.3, 12), R(.5, -8), R(.9)],
                              **legs4([R(0, 50), R(.3, 50), R(.5, 10), R(.9)], [R(0, -40), R(.3, -40), R(.5, 14), R(.9)]), **AH('jaw', [R(0, -34), R(.5, -40), R(.9)]), 'tail': [R(0, -30), R(.5, 20), R(.9)]}
        elif s == 'drop':
            sl = 1.0; spawn = {'root': [T(0, 0, H * 4, 30), T(.45, 0, 2, 0, 'linear'), T(.6, 0, 0, 0), T(1.0)], 'body': [R(0, -30), R(.45, -20), R(.6, 8), R(1.0)],
                               'right_wing': [R(0, 0, 0, 30), R(.45, 0, 0, 30), R(.6, 0, 0, -30), R(1.0)], 'left_wing': [R(0, 0, 0, -30), R(.45, 0, 0, -30), R(.6, 0, 0, 30), R(1.0)],
                               **legs4([R(0, 40), R(.45, 40), R(.6), R(1.0)]), **AH('jaw', [R(0, -34), R(.6, -34), R(1.0)])}
        else:
            sl = d = 1.8 if big else 1.2
            spawn = {'root': [T(0, 0, -H - 10, 0), T(d * .7, 0, 2, 0), T(d * .85, 0, 0, 0), T(d)], 'body': [R(0, 26), R(d * .7, 10), R(d * .85, -4), R(d)],
                     'right_front_leg': [R(0, 60), R(d * .2, 10), R(d * .4, 60), R(d * .6, 10), R(d * .85), R(d)], 'left_front_leg': [R(0, 10), R(d * .2, 60), R(d * .4, 10), R(d * .6, 60), R(d * .85), R(d)],
                     **AH('neck', [R(0, 20), R(d * .7, 26), R(d)]), **AH('jaw', [R(0, -10), R(d * .7, -38), R(d)])}
        # ---- 사망 ----
        Wd = K['CW']; dl = 2.2 if big else 1.4; t1, t2 = dl * .3, dl * .65
        death = {'root': [R(0), R(t1, 0, 0, -14), R(t2, 0, 0, -86), R(dl, 0, 0, -90), T(0), T(t1, 0, 2, 0), T(t2, 0, Wd, 0), T(dl, 0, Wd, 0)],
                 'right_front_leg': [Z(0), R(t2, 24), R(dl, 30)], 'left_front_leg': [Z(0), R(t2, 8), R(dl, 12)], 'right_hind_leg': [Z(0), R(t2, -24), R(dl, -30)], 'left_hind_leg': [Z(0), R(t2, -8), R(dl, -12)],
                 **AH('neck', [Z(0), R(t1, 30), R(t2, -12), R(dl, -20)]), **AH('jaw', [Z(0), R(t1, -44), R(dl, -24)]), 'tail': [Z(0), R(t1, -30), R(dl, 24)]}
        bite = lambda dl_=0, al=1.0: {'root': [T(0), T(.3, 0, 0, 3), T(.45, 0, 0, -10, 'linear'), T(.7 + dl_, 0, 0, -10), T(al)], 'body': [Z(0), R(.3, -6), R(.45, 5, 0, 0, 'linear'), R(al)],
                                      **AH('neck', [Z(0), R(.3, 16), R(.45, -18, 0, 0, 'linear'), R(.7, -12), R(al)], dl_), **AH('head', [Z(0), R(.3, 18), R(.45, -10, 0, 0, 'linear'), R(al)], dl_),
                                      **AH('jaw', [Z(0), R(.3, -50), R(.43, -50), R(.5, 0, 0, 0, 'linear'), R(al)], dl_),
                                      **legs4([Z(0), R(.45, 30, 0, 0, 'linear'), R(al)], [Z(0), R(.45, -24, 0, 0, 'linear'), R(al)]), 'tail': [Z(0), R(.3, 30), R(.45, -20), R(al)]}
        # 포탈에서 튀어나옴 (Siege_Portal 의 지옥문 + spawn_burst 와 짝): 문 안쪽(뒤)에서 작게 시작해 앞으로 뛰쳐나와 착지
        pa, pb, pln = (.6, 1.0, 1.6) if big else (.22, .42, .8); D = K['BL'] * .9
        portal_out = {'root': [T(0, 0, 2, D), T(pa, 0, H * .14, D * .3), T(pb, 0, 0, 0), T(pln), S(0, .2, .2, .2), S(pa, 1, 1, 1), S(pln)], 'body': [R(0, 8), R(pa, 16), R(pb, -9), R(pln)],
                      **legs4([R(0, 56), R(pa, 56), R(pb, 12), R(pln)], [R(0, -44), R(pa, -44), R(pb, 16), R(pln)]), **AH('neck', [R(0, -10), R(pa, 14), R(pb, -8), R(pln)]),
                      **AH('jaw', [R(0, -20), R(pa, -46), R(pb, -40), R(pln)]), 'tail': [R(0, -30), R(pb, 24), R(pln)]}
        spec = [('spawn', round(sl, 2), 'once', spawn), ('portal_out', pln, 'once', portal_out), ('idle', 3.0, 'loop', idle), ('walk', L, 'loop', walk)]
        if ID == 'siege_hound':
            spec += [('attack', 1.0, 'once', bite()),
                     ('lunge_bite', 1.5, 'once', {   # 도약 물어뜯기: 웅크림(0.35) → 뛰어올라 0.6초에 물고 → 머리를 흔들어 찢는다 (수평 이동은 서버)
                         'root': [T(0), T(.35, 0, -4, 4), T(.48, 0, 16, -4), T(.6, 0, 2, -12, 'linear'), T(1.1, 0, 0, -12), T(1.5)], 'body': [Z(0), R(.35, -12), R(.48, 24), R(.6, -10, 0, 0, 'linear'), R(1.5)],
                         **legs4([Z(0), R(.35, -20), R(.48, 64), R(.6, 26, 0, 0, 'linear'), R(1.5)], [Z(0), R(.35, 30), R(.48, -50), R(.6, 10, 0, 0, 'linear'), R(1.5)]),
                         'neck': [Z(0), R(.35, -10), R(.5, 20), R(.6, -16, 0, 0, 'linear'), R(.72, -14, 26), R(.84, -14, -26), R(.96, -14, 22), R(1.08, -14, -18), R(1.5)],
                         'head': [Z(0), R(.6, -8), R(.72, 0, 0, 20), R(.84, 0, 0, -20), R(.96, 0, 0, 16), R(1.08, 0, 0, -12), R(1.5)],
                         'jaw': [Z(0), R(.4, -54), R(.58, -54), R(.62, -6, 0, 0, 'linear'), R(1.1, -6), R(1.5)], 'tail': [Z(0), R(.48, -40), R(.6, 30), R(1.5)]}),
                     ('howl', 2.2, 'once', {       # 무리 울음: 주저앉아 하늘로 길게 운다 (0.6~1.7초, 주변 마수 강화용)
                         'body': [Z(0), R(.5, 22), R(1.7, 22), R(2.2), T(0), T(.5, 0, -4, 4), T(1.7, 0, -4, 4), T(2.2)], **legs4(None, [Z(0), R(.5, 26), R(1.7, 26), R(2.2)], None, [Z(0), R(.5, -40), R(1.7, -40), R(2.2)]),
                         'neck': [Z(0), R(.3, -20), R(.6, 44), R(1.1, 50), R(1.7, 44), R(2.2)], 'head': [Z(0), R(.6, 30), R(1.7, 34), R(2.2)], 'jaw': [Z(0), R(.6, -40), R(1.1, -46), R(1.7, -40), R(1.9)],
                         'tail': [Z(0), R(.6, -30), R(1.7, -30), R(2.2)]})]
        elif ID == 'siege_ram':
            spec += [('attack', 1.2, 'once', {     # 뿔 치켜올리기: 머리를 박고(0.4) 0.55초에 위로 걷어 올린다 (띄우기)
                         'root': [T(0), T(.4, 0, -2, 2), T(.55, 0, 2, -10, 'linear'), T(.8, 0, 0, -10), T(1.2)], 'body': [Z(0), R(.4, -8), R(.55, 14, 0, 0, 'linear'), R(.8, 10), R(1.2)],
                         'neck': [Z(0), R(.4, -34), R(.55, 46, 0, 0, 'linear'), R(.8, 38), R(1.2)], 'head': [Z(0), R(.4, -30), R(.55, 34, 0, 0, 'linear'), R(.8, 26), R(1.2)],
                         **legs4([Z(0), R(.4, -10), R(.55, 30, 0, 0, 'linear'), R(1.2)], [Z(0), R(.4, 14), R(.55, -26, 0, 0, 'linear'), R(1.2)]), 'jaw': [Z(0), R(.55, -30), R(1.2)], 'tail': [Z(0), R(.4, 40), R(.55, -30), R(1.2)]}),
                     ('charge_ready', 1.0, 'once', {   # 돌진 준비: 머리를 낮추고 앞발로 땅을 두 번 긁는다 (끝 자세 유지 → charge_run)
                         'body': [Z(0), R(.3, -6), R(1.0, -8), T(0), T(.3, 0, -2, 2), T(1.0, 0, -3, 4)], 'neck': [Z(0), R(.3, -30), R(1.0, -34)], 'head': [Z(0), R(.3, -26), R(1.0, -30)],
                         'right_front_leg': [Z(0), R(.25, 34), R(.45, -24, 0, 0, 'linear'), R(.6, 34), R(.8, -24, 0, 0, 'linear'), R(1.0, 0)], 'right_front_forearm': [Z(0), R(.25, -50), R(.45, 0), R(.6, -50), R(.8, 0), R(1.0)],
                         'jaw': [Z(0), R(.45, -20), R(.6, 0), R(.8, -20), R(1.0, -10)], 'tail': [Z(0), R(.3, 50), R(1.0, 60)]}),
                     ('charge_run', .36, 'loop', {     # 돌진 질주(루프): 머리를 박은 채 전력 질주
                         'body': [R(0, -8), R(.18, -4), R(.36, -8), T(0, 0, -3, 4), T(.18, 0, 1, 4), T(.36, 0, -3, 4)], 'neck': [R(0, -34), R(.36, -34)], 'head': [R(0, -30), R(.36, -30)],
                         **legs4([R(0, 46), R(.18, -40), R(.36, 46)], [R(0, -40), R(.18, 40), R(.36, -40)], [R(0, -10), R(.09, -50), R(.18, -10), R(.36, -10)], [R(0, -10), R(.27, -50), R(.36, -10)]),
                         'tail': [R(0, 60), R(.18, 50), R(.36, 60)], 'jaw': [R(0, -10), R(.36, -10)]}),
                     ('charge_hit', 1.0, 'once', {     # 돌진 충돌: 0.1초에 들이받고 반동으로 머리가 튄다
                         'root': [T(0, 0, 0, 0), T(.1, 0, 0, -10, 'linear'), T(.3, 0, 0, 4), T(1.0)], 'body': [R(0, -8), R(.1, -12, 0, 0, 'linear'), R(.3, 10), R(1.0), T(0, 0, -3, 4), T(.3, 0, 0, 0), T(1.0)],
                         'neck': [R(0, -34), R(.1, -40, 0, 0, 'linear'), R(.3, 24), R(.6, -6), R(1.0)], 'head': [R(0, -30), R(.1, -34), R(.3, 20), R(1.0)],
                         **legs4([R(0, 30), R(.1, 40), R(.3, -10), R(1.0)], [R(0, -30), R(.3, 10), R(1.0)]), 'tail': [R(0, 60), R(.3, -20), R(1.0)], 'jaw': [R(0, -10), R(.3, -34), R(1.0)]})]
        elif ID == 'siege_lizard':
            tw = {t: [Z(0), R(.35, 0, 26), R(.6, 0, -18, 0, 'linear'), R(1.2)] for t in tails}
            spec += [('attack', 1.2, 'once', {     # 꼬리 쓸기: 몸을 통째로 한 바퀴 돌려 곤봉 꼬리로 친다 (0.35~0.6초)
                         'root': [R(0, 0, 0, 0), R(.35, 0, -44, 0), R(.6, 0, 230, 0, 'linear'), R(1.2, 0, 360, 0)], 'body': [Z(0), R(.35, 0, 0, -8), R(.6, 0, 0, 10), R(1.2)], **tw,
                         'jaw': [Z(0), R(.35, -34), R(.9, -34), R(1.2)], 'neck': [Z(0), R(.35, 0, 30), R(.6, 0, -20), R(1.2)]}),
                     ('spine_volley', 1.8, 'once', {   # 등 가시 발사(원거리): 등을 부풀려 가시를 세우고(0.7) 0.8초에 쏜다 → 가시가 사라졌다가 1.2초부터 다시 돋는다
                         'body': [Z(0), R(.7, 10), R(.8, -8, 0, 0, 'linear'), R(1.1, -6), R(1.8), T(0), T(.7, 0, 3, 2), T(.8, 0, -2, -3, 'linear'), T(1.8)],
                         'chest': [Z(0), R(.7, -16), R(.8, 6, 0, 0, 'linear'), R(1.8)], 'neck': [Z(0), R(.7, -20), R(.8, 14, 0, 0, 'linear'), R(1.8)], 'jaw': [Z(0), R(.7, -10), R(.8, -44, 0, 0, 'linear'), R(1.3, -30), R(1.8)],
                         'spines': [S(0), S(.5, 1.1, 1.35, 1.1), S(.7, 1.15, 1.5, 1.15), S(.8, 1, .05, 1, 'linear'), S(1.2, 1, .05, 1), S(1.7, 1, 1, 1), S(1.8)], 'tail': [Z(0), R(.7, 30), R(.8, -10, 0, 0, 'linear'), R(1.8)],
                         **legs4([Z(0), R(.7, -14), R(.8, 10, 0, 0, 'linear'), R(1.8)])})]
        elif ID == 'siege_toad':
            spec += [('attack', 1.3, 'once', {     # 불씨 뱉기(원거리 포물선): 주머니를 부풀리고(0.5) 0.6초에 뱉는다
                         'body': [Z(0), R(.5, 16), R(.6, -8, 0, 0, 'linear'), R(1.3)], 'sac': [S(0), S(.5, 1.5, 1.5, 1.5), S(.6, .8, .8, .8, 'linear'), S(1.3)],
                         'neck': [Z(0), R(.5, 20), R(.6, -10, 0, 0, 'linear'), R(1.3)], 'jaw': [Z(0), R(.5, -6), R(.6, -50, 0, 0, 'linear'), R(.9, -40), R(1.3)],
                         'root': [T(0), T(.6, 0, 0, 0), T(.66, 0, 0, 4, 'linear'), T(1.3)]}),
                     ('tongue_pull', 1.8, 'once', {   # 혀 끌어당기기(끌당): 입을 벌리고(0.35) 0.45초에 혀가 뻗어 붙고, 0.8~1.15초에 당겨 온다
                         'body': [Z(0), R(.35, 8), R(.45, -6, 0, 0, 'linear'), R(.8, -6), R(1.15, 14), R(1.8)], 'neck': [Z(0), R(.35, 14), R(.45, 0, 0, 0, 'linear'), R(.8, 0), R(1.15, 16), R(1.8)],
                         'jaw': [Z(0), R(.35, -56), R(1.1, -56), R(1.2, 0, 0, 0, 'linear'), R(1.35, -10), R(1.5, 0), R(1.8)],
                         'tongue': [S(0), S(.35), S(.45, 1, 1, 16, 'linear'), S(.8, 1, 1, 16), S(1.15, 1, 1, 1, 'linear'), S(1.8)],
                         'tongue_tip': [S(0), S(.35), S(.45, 1, 1, 1 / 16, 'linear'), S(.8, 1, 1, 1 / 16), S(1.15, 1, 1, 1, 'linear'), S(1.8)],
                         'sac': [S(0), S(.35, 1.2, 1.2, 1.2), S(.45, .9, .9, .9), S(1.15, 1.3, 1.3, 1.3), S(1.8)], 'root': [T(0), T(.8, 0, 0, 0), T(1.15, 0, 0, 5), T(1.8)]}),
                     ('burst', 1.5, 'hold', {         # 자폭: 온몸이 부풀어 0.9초에 터진다 (터지면 납작해진 채 유지)
                         'body': [S(0), S(.7, 1.3, 1.3, 1.3), S(.86, 1.5, 1.5, 1.5), S(.92, 1.3, .2, 1.3, 'linear'), S(1.5, 1.3, .15, 1.3)], 'sac': [S(0), S(.86, 2, 2, 2), S(.92, .1, .1, .1, 'linear'), S(1.5, .1, .1, .1)],
                         'jaw': [Z(0), R(.86, -30), R(.92, -60, 0, 0, 'linear')],
                         **{n: [Z(0), R(.92, 0, 0, 50 * (1 if 'right' in n else -1), 'linear'), R(1.5, 0, 0, 50 * (1 if 'right' in n else -1))] for n in HLg}})]
            idle['sac'] = [S(0), S(1.5, 1.12, 1.12, 1.12), S(3)]
        elif ID == 'siege_twin':
            spec += [('attack', 1.2, 'once', bite(.18, 1.2)),
                     ('twin_breath', 2.4, 'once', {   # 쌍두 화염 숨결(원거리 부채꼴): 두 머리가 뒤로 젖혔다가(0.55) 0.7~1.9초 동안 바깥에서 안쪽으로 교차하며 뿜는다
                         'body': [Z(0), R(.55, 10), R(.7, -6, 0, 0, 'linear'), R(1.9, -6), R(2.4), T(0), T(.55, 0, 0, 3), T(.7, 0, -2, -2, 'linear'), T(1.9, 0, -2, -2), T(2.4)],
                         'neck': [Z(0), R(.55, 30, -20), R(.7, -20, -40, 0, 'linear'), R(1.9, -20, 34, 0, 'linear'), R(2.4)], 'neck2': [Z(0), R(.55, 30, 20), R(.7, -20, 40, 0, 'linear'), R(1.9, -20, -34, 0, 'linear'), R(2.4)],
                         'head': [Z(0), R(.55, 20), R(.7, 6, 0, 0, 'linear'), R(1.9, 6), R(2.4)], 'head2': [Z(0), R(.55, 20), R(.7, 6, 0, 0, 'linear'), R(1.9, 6), R(2.4)],
                         'jaw': [Z(0), R(.55, -10), R(.7, -56, 0, 0, 'linear'), R(1.9, -52), R(2.1)], 'jaw2': [Z(0), R(.55, -10), R(.7, -56, 0, 0, 'linear'), R(1.9, -52), R(2.1)],
                         **legs4([Z(0), R(.7, 14, 0, 0, 'linear'), R(1.9, 14), R(2.4)]), 'tail': [Z(0), R(.55, 40), R(1.9, 30), R(2.4)]})]
        elif ID == 'siege_behemoth':
            spec += [('attack', 2.0, 'once', {     # 지진 발구르기: 앞몸을 들어 올렸다가(0.9) 1.05초에 내려찍는다 (주변 원형 충격)
                         'body': [Z(0), R(.7, 20), R(.9, 22), R(1.05, -4, 0, 0, 'linear'), R(1.5, -3), R(2.0), T(0), T(.7, 0, 2, 4), T(1.05, 0, -3, -4, 'linear'), T(1.5, 0, -3, -4), T(2.0)],
                         'chest': [Z(0), R(.7, 10), R(.9, 12), R(1.05, -4, 0, 0, 'linear'), R(2.0)],
                         **legs4([Z(0), R(.7, 30), R(.9, 36), R(1.05, -24, 0, 0, 'linear'), R(1.5, -20), R(2.0)], [Z(0), R(.7, -18), R(1.05, 0, 0, 0, 'linear'), R(2.0)], [Z(0), R(.7, -50), R(1.05, 0, 0, 0, 'linear'), R(2.0)]),
                         'neck': [Z(0), R(.7, 20), R(1.05, -20, 0, 0, 'linear'), R(1.5, -14), R(2.0)], 'jaw': [Z(0), R(.7, -30), R(1.5, -24), R(2.0)], 'tail': [Z(0), R(.7, -40), R(1.05, 24), R(2.0)]}),
                     ('gate_ram', 2.6, 'once', {     # 성문 들이받기: 뒤로 물러나 머리를 낮추고(1.2) 1.45초에 공성추째 들이받는다
                         'root': [T(0), T(1.2, 0, 0, 14), T(1.45, 0, 0, -22, 'linear'), T(1.9, 0, 0, -22), T(2.6)], 'body': [Z(0), R(1.2, 4), R(1.45, -6, 0, 0, 'linear'), R(1.9, -5), R(2.6), T(0), T(1.2, 0, -3, 0), T(1.9, 0, -3, 0), T(2.6)],
                         'neck': [Z(0), R(1.2, -22), R(1.45, -30, 0, 0, 'linear'), R(1.9, -24), R(2.6)], 'head': [Z(0), R(1.2, -16), R(1.9, -16), R(2.6)],
                         'right_front_leg': [Z(0), R(.3, 30), R(.5, -14, 0, 0, 'linear'), R(.8, 30), R(1.0, -14, 0, 0, 'linear'), R(1.2, -16), R(1.45, 30, 0, 0, 'linear'), R(2.6)],
                         'left_front_leg': [Z(0), R(1.2, -16), R(1.45, 30, 0, 0, 'linear'), R(2.6)], **legs4(None, [Z(0), R(1.2, 16), R(1.45, -26, 0, 0, 'linear'), R(2.6)]),
                         'jaw': [Z(0), R(1.2, -10), R(1.45, -34, 0, 0, 'linear'), R(2.6)], 'tail': [Z(0), R(1.2, 40), R(1.45, -20), R(2.6)]})]
        elif ID == 'siege_stalker':
            wing = lambda keys: {'right_wing': [R(t, 0, 0, a) for t, a in keys], 'left_wing': [R(t, 0, 0, -a) for t, a in keys], 'right_wing2': [R(t, 0, 0, a * .6) for t, a in keys], 'left_wing2': [R(t, 0, 0, -a * .6) for t, a in keys]}
            idle.update(wing([(0, 0), (1.5, 8), (3, 0)])); walk.update(wing([(0, 6), (h, -6), (L, 6)])); death.update({'right_wing': [Z(0), R(t2, 0, 0, -60), R(dl, 0, 0, -64)], 'left_wing': [Z(0), R(t2, 0, 0, 40), R(dl, 0, 0, 30)]})
            spec += [('attack', .9, 'once', {      # 할퀴기: 오른 앞발을 들어(0.3) 0.42초에 긁어내린다
                         'body': [Z(0), R(.3, 10, 0, -6), R(.42, -6, 0, 6, 'linear'), R(.9)], 'right_front_leg': [Z(0), R(.3, 80, 0, -20), R(.42, 20, 0, 16, 'linear'), R(.6, 10), R(.9)],
                         'right_front_forearm': [Z(0), R(.3, -70), R(.42, -6, 0, 0, 'linear'), R(.9)], 'neck': [Z(0), R(.3, 14, 14), R(.42, -10, -10, 0, 'linear'), R(.9)], 'jaw': [Z(0), R(.3, -40), R(.6, -30), R(.9)],
                         'root': [T(0), T(.42, 0, 0, -6, 'linear'), T(.9)], **wing([(0, 0), (.3, 20), (.42, -10), (.9, 0)])}),
                     ('pounce', 1.5, 'once', {     # 도약 덮치기(후방 진입): 웅크림(0.35) → 높이 뛰어 0.85초에 덮쳐 누른다 (수평 이동은 서버)
                         'root': [T(0), T(.35, 0, -5, 4), T(.6, 0, 44, 0), T(.85, 0, 0, -10, 'linear'), T(1.2, 0, 0, -10), T(1.5)], 'body': [Z(0), R(.35, -14), R(.6, 20), R(.85, -16, 0, 0, 'linear'), R(1.2, -12), R(1.5)],
                         **legs4([Z(0), R(.35, -20), R(.6, 70), R(.85, 30, 0, 0, 'linear'), R(1.5)], [Z(0), R(.35, 34), R(.6, -54), R(.85, 14, 0, 0, 'linear'), R(1.5)], [Z(0), R(.6, -40), R(.85, 0, 0, 0, 'linear'), R(1.5)]),
                         **wing([(0, 0), (.35, -24), (.6, 40), (.85, -30), (1.5, 0)]), 'neck': [Z(0), R(.6, -24), R(.85, 12, 0, 0, 'linear'), R(1.5)],
                         'jaw': [Z(0), R(.5, -50), R(.83, -50), R(.9, 0, 0, 0, 'linear'), R(1.5)], 'tail': [Z(0), R(.6, -40), R(.85, 30), R(1.5)]}),
                     ('dive', 2.0, 'once', {       # 급강하: 날개를 쳐 솟아오르고(0.3~1.0) 1.3초에 발톱을 세워 내리꽂는다
                         'root': [T(0), T(.3, 0, -4, 0), T(1.0, 0, 90, 10), T(1.15, 0, 92, 10), T(1.3, 0, 0, -8, 'linear'), T(1.7, 0, 0, -8), T(2.0)], 'body': [Z(0), R(.3, -10), R(1.0, 30), R(1.15, -50), R(1.3, -14, 0, 0, 'linear'), R(2.0)],
                         **wing([(0, 0), (.3, -30), (.45, 40), (.6, -30), (.75, 40), (.9, -30), (1.05, 44), (1.15, 50), (1.3, -34), (2.0, 0)]),
                         **legs4([Z(0), R(1.0, 30), R(1.15, 80), R(1.3, 30, 0, 0, 'linear'), R(2.0)], [Z(0), R(1.0, -30), R(1.15, 40), R(1.3, 10, 0, 0, 'linear'), R(2.0)]),
                         'jaw': [Z(0), R(1.0, -20), R(1.25, -54), R(1.35, 0, 0, 0, 'linear'), R(2.0)], 'tail': [Z(0), R(1.0, 30), R(1.3, -30), R(2.0)]})]
        else:   # siege_stinger
            def tk(a0, a1, hold, end, b0=None, b1=None):   # 꼬리: 밑동 마디는 a, 끝 마디는 b
                b0 = a0 if b0 is None else b0; b1 = a1 if b1 is None else b1
                return {t: ([Z(0), R(hold[0], a0), R(hold[1], a1, 0, 0, 'linear'), R(hold[2], a1 * .9), R(end)] if i < 2 else [Z(0), R(hold[0], b0), R(hold[1], b1, 0, 0, 'linear'), R(hold[2], b1 * .9), R(end)]) for i, t in enumerate(tails)}
            mand = lambda keys: {'right_mandible': [R(t, 0, a) for t, a in keys], 'left_mandible': [R(t, 0, -a) for t, a in keys]}
            idle.update(mand([(0, 0), (.75, 10), (1.5, 0), (2.25, 10), (3, 0)]))
            spec += [('attack', 1.1, 'once', {     # 꼬리 찌르기(근접): 꼬리를 뒤로 젖혔다가(0.35) 0.5초에 머리 너머 앞을 찍는다
                         'root': [T(0), T(.35, 0, 0, 3), T(.5, 0, 0, -6, 'linear'), T(.8, 0, 0, -6), T(1.1)], 'body': [Z(0), R(.35, 8), R(.5, -12, 0, 0, 'linear'), R(.8, -10), R(1.1)],
                         **tk(16, -34, (.35, .5, .8), 1.1, -8, 20), 'neck': [Z(0), R(.35, 10), R(.5, -12), R(1.1)], **mand([(0, 0), (.35, 30), (.5, -6), (1.1, 0)])}),
                     ('sting_shot', 1.4, 'once', {   # 독침 발사(원거리 직선): 꼬리를 높이 세워 조준하고(0.6) 0.7초에 침을 쏜다 (독샘이 부풀었다 꺼짐)
                         'body': [Z(0), R(.6, 12), R(.7, -4, 0, 0, 'linear'), R(1.4), T(0), T(.6, 0, -2, 2), T(1.4)], **tk(20, -26, (.6, .7, 1.0), 1.4, 10, 22),
                         tails[-1]: [Z(0), R(.6, 14), R(.7, 26, 0, 0, 'linear'), R(1.0, 20), R(1.4), S(0), S(.6, 1.4, 1.4, 1.4), S(.7, .8, .8, .8, 'linear'), S(1.4)],
                         'neck': [Z(0), R(.6, -10), R(1.4)], **mand([(0, 0), (.6, 26), (1.4, 0)])}),
                     ('blink_out', .8, 'hold', {     # 땅속 이동(텔포) 1: 몸을 말고 땅으로 가라앉는다 (0.6초에 사라짐 → 서버가 위치 이동)
                         'root': [T(0), T(.2, 0, 3, 0), T(.6, 0, -H - 30, 0), T(.8, 0, -H - 30, 0), S(0), S(.6, .7, .7, .7), S(.8, .7, .7, .7), R(0), R(.6, 0, 200, 0)],
                         'body': [Z(0), R(.2, 14), R(.6, -30)], **tk(-10, -24, (.2, .6, .8), .8), **legs4([Z(0), R(.6, 40)], [Z(0), R(.6, -30)])}),
                     ('blink_in', 1.0, 'once', {     # 땅속 이동(텔포) 2: 다른 곳에서 솟구치며 0.4초에 꼬리로 찌른다
                         'root': [T(0, 0, -H - 30, 0), T(.3, 0, 8, 0), T(.42, 0, 0, 0), T(1.0), S(0, .7, .7, .7), S(.3, 1, 1, 1), S(1.0)], 'body': [R(0, 30), R(.3, 14), R(.42, -12, 0, 0, 'linear'), R(1.0)],
                         **{t: ([R(0, 16), R(.3, 16), R(.42, -34, 0, 0, 'linear'), R(.7, -30), R(1.0)] if i < 2 else [R(0, -8), R(.3, -8), R(.42, 20, 0, 0, 'linear'), R(.7, 18), R(1.0)]) for i, t in enumerate(tails)},
                         **mand([(0, 30), (.42, -6), (1.0, 0)]), **legs4([R(0, 40), R(.42), R(1.0)], [R(0, -30), R(.42), R(1.0)])})]
        spec.insert(5 if len(spec) > 5 else len(spec), ('death', dl, 'hold', death))
        return build_anims(f'animation.{ID}.', spec, G)
    return anims

if __name__ == '__main__':
    O = '/mnt/user-data/outputs/'
    for k, M in enumerate(MOBS):
        if len(sys.argv) > 1 and M['id'] not in sys.argv[1:]: continue
        for sfx, pl in (('', M['pal']), ('_shiny', M['shiny'])):
            PAL.clear(); PAL.update(pl); root, K = beast(M['sp'], M['extra'])
            export(M['id'] + sfx, root, make_anims(M, K), 60 + k, O + M['id'] + sfx + '.bbmodel', M['hit'])
