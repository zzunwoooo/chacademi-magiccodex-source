# 전투 스펙 계산 (2026-10-26 오너 결정 적용판: 장신구 강화·펫 도감 체력 20 그대로 포함)
# 실행: python3 docs/plans/combat-spec/model.py   (표준 라이브러리만)
# 수치 출처: magic-codex-paper/src/main/resources/enhancement.yml (마법봉·장신구 등급, 강화 단계), config.yml (기본 마나),
#           magic-mythic-paper/tools/spells.py PVP_K (PvP 등급 계수), MagicArmor.java (마법 방어)
import sys

# ---- 설정과 같은 값 (enhancement.yml) ----
WAND = {'수습': (20, 50, 0), '일반': (30, 50, 0), '고급': (45, 120, 0), '유일': (50, 135, 0), '전설': (55, 150, 0)}  # 지능, 최대 마나, 가속
WAND_STAGE = [5, 5, 5, 5, 5, 5, 5, 2, 2, 2]                       # 성공 n번째의 지능 상승 (지능 중심 코어)
WAND_GAIN = {'power': 1.0, 'mana': 2.5, 'haste': 0.5}               # 단계값 × 이 배율 × 코어 성향 배율
ACC = {'일반': (3, 1, 30, 0.5), '고급': (6, 3, 60, 1.3), '유일': (7, 3, 80, 1.7), '전설': (9, 3, 100, 2.3)}  # 지능, 체력, 최대 마나, 초당 회복
ACC_STAGE = [2, 2, 2, 1, 1]                                         # 장신구 강화 단계값 (마법봉의 40%/50%)
ACC_GAIN = {'power': 0.5, 'mana': 2.5, 'regen': 0.15}               # 장신구 강화: 지능(작게)·최대 마나·초당 회복
BASE_MANA, BASE_REGEN = 300, 8
CAP, PER_POINT = 0.45, 0.0125                                       # 마법 방어 = min(45%, (방어력+보호 EPF)×1.25%)
PVP_K = {'초급': .027, '중급': .045, '고급': .091, '궁극(쉬움)': .186, '궁극(연계)': .537}
ULT_MANA = {'궁극(쉬움)': 450, '궁극(연계)': 600}

def affinity(stat, primary):  # magic-codex-protocol CoreAffinity.multiplier (0 지능 · 1 마나 · 2 가속/회복)
    return (1 if stat == primary else .5) / (1 if stat == 0 else .5)

def build(wand, wand_succ, acc, acc_succ, acc_core, pet_hp, armor, epf, art, wand_core=0):
    s = WAND_STAGE[:wand_succ]
    I = WAND[wand][0] + sum(s) * WAND_GAIN['power'] * affinity(0, wand_core)
    mana = BASE_MANA + WAND[wand][1] + sum(s) * WAND_GAIN['mana'] * affinity(1, wand_core)
    haste = WAND[wand][2] + sum(s) * WAND_GAIN['haste'] * affinity(2, wand_core)
    regen, hp = BASE_REGEN, 20 + pet_hp
    for g in acc:
        a = ACC[g]; t = sum(ACC_STAGE[:acc_succ])
        I += a[0] + t * ACC_GAIN['power'] * affinity(0, acc_core)
        hp += a[1]
        mana += a[2] + t * ACC_GAIN['mana'] * affinity(1, acc_core)
        regen += a[3] + t * ACC_GAIN['regen'] * affinity(2, acc_core)
    dr = min(CAP, (armor + epf) * PER_POINT)
    return dict(I=I, hp=hp, dr=dr, ehp=hp / (1 - dr), mana=mana, regen=regen, haste=haste, art=min(1.2, art))

# 세 수준 (가정은 제안서 §5와 같음. 펫 도감 체력: 대충 +5 · 적당 +10 · 한계 +20(최대, 오너: 20 유지))
L = {
    '대충': build('수습', 1, [], 0, 0, 5, 15, 0, 1.0),
    '적당': build('고급', 7, ['고급'] * 3, 3, 0, 10, 20, 10, 1.08),
    '한계': build('전설', 10, ['전설'] * 3, 5, 0, 20, 20, 16, 1.12),
}
N = list(L)
# 손에 초급 3 · 중급 2 · 고급 1, 기본 쿨타임마다 사용(평균 쿨 9.5 / 18.5 / 55초, 평균 마나 24.6 / 77 / 276.5), 명중률 50%
CASTS = {'초급': 3 * 60 / 9.5, '중급': 2 * 60 / 18.5, '고급': 60 / 55.4}
COST = {'초급': 24.6, '중급': 77.0, '고급': 276.5}

def D(t, a): return PVP_K[t] * (40 + L[a]['I']) * L[a]['art']
def hastef(a): return (100 + L[a]['haste']) / 100
def manaf(a, secs=60):
    need = sum(CASTS[t] * COST[t] for t in CASTS) * hastef(a) * secs / 60
    return min(1, (L[a]['mana'] + secs * L[a]['regen']) / need)

def table(out=sys.stdout):
    p = lambda *x: print(*x, file=out)
    p('| | ' + ' | '.join(N) + ' |'); p('|---|' + '---|' * len(N))
    for k, lab in [('I', '지능'), ('hp', '최대 체력'), ('dr', '마법 방어'), ('ehp', '실효 체력'), ('mana', '최대 마나'), ('regen', '초당 마나 회복'), ('haste', '마법 가속')]:
        p(f'| {lab} | ' + ' | '.join((f"{L[a][k]*100:.1f}%" if k == 'dr' else f"{L[a][k]:.1f}") for a in N) + ' |')
    p('\n| 공격 → 방어 | 초급만 몇 발 | 섞어서 몇 발 | 고급 한 방 | 연계 궁극 | 쉬운 궁극 | 예상 시간 |')
    p('|---|---|---|---|---|---|---|')
    T = {}
    for a in N:
        f = manaf(a)
        for d in N:
            e = L[d]['ehp']
            tot = sum(CASTS[t] * D(t, a) for t in CASTS)
            mix = tot / sum(CASTS.values())
            dpm = .5 * f * hastef(a) * tot
            T[a, d] = e / dpm * 60
            p(f"| {a} → {d} | {e/D('초급',a):.1f} | {e/mix:.1f} | {D('고급',a)/e*100:.0f}% | {D('궁극(연계)',a)/e*100:.0f}% | {D('궁극(쉬움)',a)/e*100:.0f}% | {T[a,d]:.0f}초 |")
    p(f"\n- 한계 vs 대충: {T['한계','대충']:.0f}초 vs {T['대충','한계']:.0f}초 → {T['대충','한계']/T['한계','대충']:.1f}배")
    p(f"- 적당 vs 한계: {T['적당','한계']:.0f}초 vs {T['한계','적당']:.0f}초 → {T['적당','한계']/T['한계','적당']:.2f}배 (목표 1.6 이내)")
    p(f"- 적당 vs 대충: {T['적당','대충']:.0f}초 vs {T['대충','적당']:.0f}초 → {T['대충','적당']/T['적당','대충']:.1f}배")
    p('\n결투 60초 동안 마나로 쓸 수 있는 횟수 (최대 마나 + 60초 회복, 한 종류만 쓴다고 할 때) / 쿨타임 기준 1분 사용량 대비')
    p('| | 초급(24.6) | 중급(77) | 고급(276.5) | 쉬운 궁극(450) | 연계 궁극(600) | 쿨타임마다 다 쓸 수 있나 |')
    p('|---|---|---|---|---|---|---|')
    for a in N:
        pool = L[a]['mana'] + 60 * L[a]['regen']
        p(f"| {a} | {pool/24.6:.0f} | {pool/77:.0f} | {pool/276.5:.1f} | {pool/450:.1f} | {pool/600:.1f} | {manaf(a)*100:.0f}% |")
    return T

def variants():
    """적당↔한계 1.6배를 넘을 때 어떤 손잡이가 얼마나 줄이는지 (python3 model.py variants)."""
    global L, N
    import io
    keep = dict(L)
    def run(name, levels):
        global L, N
        L = levels; N = list(L); T = table(io.StringIO())
        print(f"| {name} | {T['대충','대충']:.0f} / {T['적당','적당']:.0f} / {T['한계','한계']:.0f}초 | {T['적당','한계']/T['한계','적당']:.2f}배 | {T['대충','한계']/T['한계','대충']:.1f}배 |")
    low = keep['대충']
    print('| 경우 | 같은 스펙 대충/적당/한계 | 적당↔한계 | 한계↔대충 |\n|---|---|---|---|')
    run('적용값 (위 표)', keep)
    run('한계 펫 도감 +16 (제안서 권장, 오너는 20 유지)', {'대충': low, '적당': keep['적당'], '한계': build('전설', 10, ['전설'] * 3, 5, 0, 16, 20, 16, 1.12)})
    run('적당 = 펫 도감 헤비(+13) · 다이아+보호 III(EPF 12)', {'대충': low, '적당': build('고급', 7, ['고급'] * 3, 3, 0, 13, 20, 12, 1.08), '한계': keep['한계']})
    run('대충 = 수습 마법봉 추가 마나 50 → 300', {'대충': dict(low, mana=low['mana'] + 250), '적당': keep['적당'], '한계': keep['한계']})
    L = keep; N = list(L)

if __name__ == '__main__':
    variants() if sys.argv[1:] == ['variants'] else table()
