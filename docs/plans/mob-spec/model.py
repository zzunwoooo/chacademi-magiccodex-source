# 몹 스펙 계산 (메모 #109, 2026-10-26). 실행: python3 docs/plans/mob-spec/model.py   (표준 라이브러리만)
# 플레이어 세 수준(대충·적당·한계)은 docs/plans/combat-spec/model.py 를 그대로 불러 씀 (지능·체력·마법 방어·마나·가속).
# 몹 수치는 tools/mob_spec.py 의 TIERS 를 불러 씀 → 표와 실제 파일이 같은 값.
import importlib.util, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


CS = _load('combat_spec_model', os.path.join(REPO, 'docs', 'plans', 'combat-spec', 'model.py'))
MS = _load('mob_spec_tool', os.path.join(REPO, 'tools', 'mob_spec.py'))
L, N = CS.L, CS.N
SK = MS.tier_skill_avg()  # 등급별 스킬 평균 피해 (원래 값 × 배율)


def tier(k):
    T = dict(MS.TIERS[k])
    s = SK.get(k, {})
    T['basic'] = s.get('basic', 0.0)
    T['big'] = s.get('big', T['basic'])
    return T

# ---- 플레이어 → 몹 (몹 상대 피해 = 마법 base + ratio × 지능, combat-spec 표 §1 등급 평균. 몹은 바닐라 방어 = 허스크 방어 2 → 거의 0)
PVE = {'초급': (3.3, .27, 9.5, 24.6), '중급': (5.0, .38, 18.5, 77.0), '고급': (7.4, .52, 55.4, 276.5)}  # base, ratio, 쿨, 마나
HAND = ['초급'] * 3 + ['중급'] * 2 + ['고급']  # 손에 든 마법 (combat-spec 과 같음)
CAST_GAP = 1.0      # 시전 사이 최소 간격(초): 모션 + 조준
HIT_PVE = 0.7       # 몹 상대 명중률 (PvP 50% 보다 높게: 몹이 크고 쫓아옴)


def pve_damage(t, a):
    b, r, _, _ = PVE[t]
    return (b + r * L[a]['I']) * L[a]['art']


def ttk(hp, a, party=1, limit=900.0):
    """몹 체력 hp 를 a 수준 플레이어 party 명이 깎는 시간(초). 쿨이 돈 마법 중 피해가 큰 것부터, 마나가 모자라면 못 씀."""
    if hp <= 0:
        return 0.0
    haste = CS.hastef(a)
    ready = [0.0] * len(HAND)
    mana, cap, regen = L[a]['mana'], L[a]['mana'], L[a]['regen']
    t, nxt, done, dt = 0.0, 0.0, 0.0, 0.05
    while t < limit:
        if t >= nxt:
            best = None
            for i, s in enumerate(HAND):
                # 고급은 남은 체력이 그 한 방의 4배보다 적으면 아껴 둠 (작은 몹에 마나 276 을 쓰지 않음)
                if s == '고급' and hp - done < 4 * pve_damage(s, a) * HIT_PVE * party:
                    continue
                if ready[i] <= t and mana >= PVE[s][3] and (best is None or pve_damage(s, a) > pve_damage(HAND[best], a)):
                    best = i
            if best is not None:
                s = HAND[best]
                mana -= PVE[s][3]
                ready[best] = t + PVE[s][2] / haste
                done += pve_damage(s, a) * HIT_PVE * party
                nxt = t + CAST_GAP
                if done >= hp:
                    return t + CAST_GAP / 2
        mana = min(cap, mana + regen * dt)
        t += dt
    return float('inf')


def spells_to_kill(hp, a, kind='초급'):
    return hp / pve_damage(kind, a)


# ---- 몹 → 플레이어
# 몹 스킬(chatelegraph·chamobshot flat=)은 마법 → 플레이어 마법 방어 (이번에 몹 마법에도 적용, MagicArmor 상한 45%).
# 몹 근접(Mythic Damage, meleeattack)은 물리 → 바닐라 방어구·보호 인첸트.
GEAR = {'대충': (15, 0, 0), '적당': (20, 8, 10), '한계': (20, 12, 16)}  # 방어력, 방어 강도, 보호 EPF (combat-spec 가정)
BASIC_EVERY, BIG_EVERY = 4.0, 20.0   # 기본기 약 4초(onTimer 40 · 60% · 쿨 2~3), 큰 기술 약 20초(onTimer 300 · 35% + 멀리서 맞으면 원거리 반격)
LAND_BASIC, LAND_BIG = 0.7, 0.4      # 피하며 싸울 때 맞는 비율 (기본기는 예고 없음 0.4초 · 큰 기술은 빨간 예고 1.0~1.2초)


def vanilla(dmg, a):
    armor, tough, epf = GEAR[a]
    red = min(20, max(armor / 5, armor - 4 * dmg / (tough + 8))) / 25
    return dmg * (1 - red) * (1 - min(20, epf) * .04)


def magic(dmg, a):
    return dmg * (1 - L[a]['dr'])


def ttd(tier_key, a, careful=True):
    """가만히 맞기만 하면(careful=False) / 피하며 싸우면(True) 몇 초에 쓰러지나 (몹 1마리, 스킬만)."""
    T = tier(tier_key)
    lb, lg = (LAND_BASIC, LAND_BIG) if careful else (1, 1)
    dps = magic(T['basic'], a) * lb / BASIC_EVERY + magic(T['big'], a) * lg / BIG_EVERY
    return L[a]['hp'] / dps if dps > 0 else float('inf')


def fmt(x):
    return '—' if x == float('inf') else f'{x:.0f}'


def table(out=sys.stdout):
    p = lambda *x: print(*x, file=out)
    tiers = [k for k in MS.TIERS if k != 'minion']
    p('플레이어 (combat-spec 그대로): ' + ' · '.join(f"{a} 지능 {L[a]['I']:.0f} 체력 {L[a]['hp']:.0f} 마법 방어 {L[a]['dr']*100:.1f}%" for a in N))
    p('몹 상대 초급 한 방: ' + ' · '.join(f"{a} {pve_damage('초급', a):.1f}" for a in N))
    p('\n| 등급 | 체력 | 기본기 / 큰 기술 / 근접 | ' + ' | '.join(f'{a} 처치(초) · 초급 몇 방' for a in N) + ' | ' + ' | '.join(f'{a} 버팀(초)' for a in N) + ' |')
    p('|---|---|---|' + '---|' * (2 * len(N)))
    for k in tiers:
        T = tier(k)
        kill = ' | '.join(f"{fmt(ttk(T['hp'], a))} · {spells_to_kill(T['hp'], a):.1f}" for a in N)
        live = ' | '.join(f"{fmt(ttd(k, a))}" for a in N)
        p(f"| {T['label']} | {T['hp']} | {T['basic']:.1f} / {T['big']:.1f} / {T['melee']:g} | {kill} | {live} |")
    p('\n버팀 = 몹 1마리 스킬을 피하며(기본기 70% · 큰 기술 40% 맞음) 싸울 때 쓰러지기까지. 근접은 따로 (아래).')
    p('가만히 서서 다 맞으면 버팀(초): ' + ' · '.join(T['label'] + ' ' + '/'.join(fmt(ttd(k, a, False)) for a in N) for k, T in ((k, tier(k)) for k in tiers)) + '  (대충/적당/한계)')
    p('\n몇 번 맞으면 쓰러지나 (가득 찬 체력 기준)')
    p('| 등급 | ' + ' | '.join(f'{a} 기본기 / 큰 기술 / 근접' for a in N) + ' |')
    p('|---|' + '---|' * len(N))
    for k in tiers:
        T = tier(k)
        cells = []
        for a in N:
            hp = L[a]['hp']
            b = hp / magic(T['basic'], a) if T['basic'] else float('inf')
            g = hp / magic(T['big'], a) if T['big'] else float('inf')
            m = hp / vanilla(T['melee'], a) if T['melee'] else float('inf')
            cells.append(' / '.join('—' if x == float('inf') else f'{x:.1f}' for x in (b, g, m)))
        p(f"| {T['label']} | " + ' | '.join(cells) + ' |')
    boss = MS.TIERS['boss']['hp']
    p('\n보스 파티: ' + ' · '.join(f"적당 {n}명 {fmt(ttk(boss, '적당', n))}초" for n in (1, 2, 3, 4))
      + f" · 한계 1명 {fmt(ttk(boss, '한계'))}초 · 한계 2명 {fmt(ttk(boss, '한계', 2))}초 · 대충 4명 {fmt(ttk(boss, '대충', 4))}초")


def current():
    """바꾸기 전 (몹 체력 20/55/195/1050, 스킬 평균 그대로, 몹 스킬에 바닐라 방어)."""
    old = {'1성': (20, 2, 2, 0), '2성': (55, 3.37, 5.42, 5), '3성': (195, 3.68, 5.23, 12.15), '보스': (1050, 3, 8, 18.9)}
    print('| 등급 | 체력 | ' + ' | '.join(f'{a} 처치(초)' for a in N) + ' | ' + ' | '.join(f'{a} 기본기 몇 번 (바닐라 방어)' for a in N) + ' |')
    print('|---|---|' + '---|' * (2 * len(N)))
    for k, (hp, b, g, m) in old.items():
        print(f'| {k} | {hp} | ' + ' | '.join(fmt(ttk(hp, a)) for a in N) + ' | ' + ' | '.join(f"{L[a]['hp']/vanilla(b, a):.0f}" for a in N) + ' |')


if __name__ == '__main__':
    current() if sys.argv[1:] == ['current'] else table()
