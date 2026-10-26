#!/usr/bin/env python3
"""몹 스펙 (메모 #109, 2026-10-26) — 야생 커스텀 몹(CA_Wild_*) 체력·근접 피해·몹 스킬 피해를 등급(1·2·3성·보스)별 규칙으로 맞춘다.

표준 라이브러리만. 계산 근거·표: docs/plans/mob-spec.md, docs/plans/mob-spec/model.py

  python3 tools/mob_spec.py apply              # 저장소 파일에 적용 (여러 번 돌려도 같음: 항상 baseline.json 원래 값에서 계산)
  python3 tools/mob_spec.py check              # 저장소 파일이 규칙과 같은지 (다르면 종료 코드 1)
  python3 tools/mob_spec.py apply-mobs <파일>   # 운영 서버의 몹 파일(예: wild/plugins/MythicMobs/mobs/chacademia_custom_wild.yml)에서
                                               # CA_Wild_* 의 Health·Damage 줄만 바꿈. 다른 줄(운영에만 있는 Skills 등)은 그대로.
  python3 tools/mob_spec.py baseline           # (처음 한 번) 바꾸기 전 값을 docs/plans/mob-spec/baseline.json 에 기록

  apply 뒤에는 꼭: python3 tools/operating_overlay.py regenerate → check (배포본 deployment/operating-config/MythicMobs/
  Skills/chacademi_mob_skills.yml · Mobs/chacademi_mob_minions.yml 이 같이 바뀜).
  야생 몹 파일(chacademia_custom_wild.yml)은 overlay 에 없음: 운영본에만 있는 Skills 줄(몹 스킬 연결)이 있어서 통째로 바꾸면 안 됨 → apply-mobs.

규칙
  - 등급 = creature-spawns-paper habitats.yml 의 rarity (변색종도 같은 등급, 같은 수치).
  - Health = 등급 체력. Damage(근접, 바닐라 방어 적용) = 원래 0 이면 0 그대로(공격 안 하는 몹), 아니면 등급 근접 피해.
  - 몹 스킬 flat (chacademi_mob_skills.yml, 플레이어 마법 방어 적용) = 원래 값 × 등급 배율 (소수 첫째 자리 반올림) → 스킬끼리 차이는 그대로.
  - 소환수 CHA_MINION_* = minion 체력·근접 피해.
  - id·스킬 이름·모양·예고 시간은 바꾸지 않음.
"""
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HABITATS = os.path.join(REPO, 'creature-spawns-paper', 'src', 'main', 'resources', 'habitats.yml')
WILD = os.path.join(REPO, 'reference', 'output', 'wildlife-ground-v3', 'chacademia_custom_wild.yml')  # 운영 몹 파일의 저장소 사본 (운영본엔 Skills 줄이 더 있음)
SKILLS = os.path.join(REPO, 'magic-mythic-paper', 'mythicmobs', 'Skills', 'chacademi_mob_skills.yml')
MINIONS = os.path.join(REPO, 'magic-mythic-paper', 'mythicmobs', 'Mobs', 'chacademi_mob_minions.yml')
BASELINE = os.path.join(REPO, 'docs', 'plans', 'mob-spec', 'baseline.json')

# ---- 등급 규칙 (오너 #109: Claude 가 정함). 근거 표는 docs/plans/mob-spec.md
# 2차 조정(같은 날): 근접까지 넣어 계산하니 2성 80/5/×1.3 은 대충이 피해도 짐, 3성 450/14/×2.5 는 적당이 피해도 짐 → 2성 60/3/×1.1 · 3성 380/8/×1.8
TIERS = {
    '1':      {'label': '1성',  'hp': 40,   'melee': 0,  'mult': 1.0},
    '2':      {'label': '2성',  'hp': 60,   'melee': 3,  'mult': 1.1},
    '3':      {'label': '3성',  'hp': 380,  'melee': 8,  'mult': 1.8},
    'boss':   {'label': '보스', 'hp': 2000, 'melee': 24, 'mult': 3.5},
    'minion': {'label': '소환수', 'hp': 30, 'melee': 4,  'mult': 1.0},
}
# 수동 연결: 이름 끝에 몹 id 가 없는 손 관리 스킬
HAND_SKILLS = {'CHA_MOB_seal_circle': 'mf_warden_mage', 'CHA_MOB_frost_laser': 'glacialcore'}

_TOP = re.compile(r'^"?([A-Za-z][A-Za-z0-9_]*)"?:\s*(#.*)?$')
_STAT = re.compile(r'^(\s+)("?)(Health|Damage)("?):(\s*)([-+]?[0-9]+(?:\.[0-9]+)?)(\s*(?:#.*)?)$')
_SKILL = re.compile(r'^(CHA_MOB_[A-Za-z0-9_]+):\s*$')
_FLAT = re.compile(r'(?<=[{;])flat=([0-9]+(?:\.[0-9]+)?)')


def habitats():
    with open(HABITATS, encoding='utf-8') as f:
        return json.load(f)['habitats']


def tier_of_mob(mob_id, hab):
    """CA_Wild_<key>[_shiny] → 등급 키, 모르면 None."""
    if not mob_id.startswith('CA_Wild_'):
        return None
    key = mob_id[len('CA_Wild_'):]
    if key.endswith('_shiny'):
        key = key[:-len('_shiny')]
    h = hab.get(key)
    return None if h is None else str(h['rarity'])


def mob_of_skill(name, keys):
    base = name[:-len('_shiny')] if name.endswith('_shiny') else name
    if base in HAND_SKILLS:
        return HAND_SKILLS[base]
    rest = base[len('CHA_MOB_'):]
    for k in keys:  # 긴 id 먼저 (bear_black 이 bear 보다 먼저)
        if rest.endswith('_' + k):
            return k
    return None


def num(x):
    x = round(float(x), 1)
    return str(int(x)) if x == int(x) else f'{x:.1f}'


# ---------------------------------------------------------------- 몹 파일 (Health / Damage 줄만)
def scan_stats(text):
    """{mob_id: {'Health': 값, 'Damage': 값}} — 최상위 키 바로 아래(첫 들여쓰기 단계)의 Health·Damage 만."""
    out, cur, depth = {}, None, None
    for line in text.splitlines():
        m = _TOP.match(line)
        if m:
            cur, depth = m.group(1), None
            continue
        if cur is None or not line.strip() or line.lstrip().startswith('#'):
            continue
        ind = len(line) - len(line.lstrip(' '))
        if depth is None:
            depth = ind
        s = _STAT.match(line)
        if s and len(s.group(1)) == depth:
            out.setdefault(cur, {})[s.group(3)] = float(s.group(6))
    return out


def rewrite_stats(text, want):
    """want(mob_id, key, old) → 새 값 또는 None(그대로). (새 글, 바뀐 줄 수)"""
    lines, cur, depth, changed = text.split('\n'), None, None, 0
    for i, line in enumerate(lines):
        m = _TOP.match(line)
        if m:
            cur, depth = m.group(1), None
            continue
        if cur is None or not line.strip() or line.lstrip().startswith('#'):
            continue
        ind = len(line) - len(line.lstrip(' '))
        if depth is None:
            depth = ind
        s = _STAT.match(line)
        if not s or len(s.group(1)) != depth:
            continue
        new = want(cur, s.group(3), float(s.group(6)))
        if new is None:
            continue
        nl = f'{s.group(1)}{s.group(2)}{s.group(3)}{s.group(4)}:{s.group(5)}{num(new)}{s.group(7)}'
        if nl != line:
            lines[i] = nl
            changed += 1
    return '\n'.join(lines), changed


def wild_rule(base, hab):
    """원래 값(baseline) 기준 새 값. base = {mob: {Health, Damage}}"""
    def want(mob, key, old):
        t = tier_of_mob(mob, hab)
        if t is None:
            return None
        if key == 'Health':
            return TIERS[t]['hp']
        orig = base.get(mob, {}).get('Damage', old)
        return 0 if orig <= 0 else TIERS[t]['melee']
    return want


def minion_rule(mob, key, old):
    if not mob.startswith('CHA_MINION_'):
        return None
    return TIERS['minion']['hp'] if key == 'Health' else TIERS['minion']['melee']


# ---------------------------------------------------------------- 몹 스킬 (flat=)
def scan_flats(text):
    """{skill: [flat, ...]} 줄 순서·한 줄 안 순서대로. 그리고 {skill: 'basic'|'big'|'special'|'hand'}"""
    flats, kinds, cur, kind = {}, {}, None, 'hand'
    for line in text.splitlines():
        if line.startswith('# ---- '):
            kind = 'basic' if '기본기' in line else 'special' if '특수기' in line else 'big'
        m = _SKILL.match(line)
        if m:
            cur = m.group(1)
            flats[cur] = []
            kinds[cur] = kind
            continue
        if cur and not line.startswith(' ') and line.strip() and not line.startswith('#'):
            cur = None
        if cur:
            flats[cur] += [float(x) for x in _FLAT.findall(line)]
    return flats, kinds


def rewrite_flats(text, base, hab):
    keys = sorted(hab, key=len, reverse=True)
    lines, cur, idx, changed, unknown = text.split('\n'), None, 0, 0, []
    for i, line in enumerate(lines):
        m = _SKILL.match(line)
        if m:
            cur, idx = m.group(1), 0
            continue
        if cur and not line.startswith(' ') and line.strip() and not line.startswith('#'):
            cur = None
        if not cur or 'flat=' not in line:
            continue
        mob = mob_of_skill(cur, keys)
        if mob is None or cur not in base:
            unknown.append(cur)
            continue
        mult = TIERS[str(hab[mob]['rarity'])]['mult']

        def sub(mm):
            nonlocal idx
            orig = base[cur][idx] if idx < len(base[cur]) else float(mm.group(1))
            idx += 1
            return 'flat=' + num(orig * mult)
        nl = _FLAT.sub(sub, line)
        if nl != line:
            lines[i] = nl
            changed += 1
    return '\n'.join(lines), changed, sorted(set(unknown))


# ---------------------------------------------------------------- 명령
def read(p):
    with open(p, encoding='utf-8', newline='') as f:
        return f.read()


def write(p, s):
    with open(p, 'w', encoding='utf-8', newline='') as f:
        f.write(s)


def load_baseline():
    with open(BASELINE, encoding='utf-8') as f:
        return json.load(f)


def make_baseline():
    if os.path.exists(BASELINE):
        print('이미 있음:', os.path.relpath(BASELINE, REPO))
        return
    hab = habitats()
    flats, kinds = scan_flats(read(SKILLS))
    keys = sorted(hab, key=len, reverse=True)
    skills = {}
    for name, fl in flats.items():
        mob = mob_of_skill(name, keys)
        skills[name] = {'mob': mob, 'tier': None if mob is None else str(hab[mob]['rarity']), 'kind': kinds[name], 'flat': fl}
    data = {'_comment': '몹 스펙 #109 적용 전 원래 값 (tools/mob_spec.py baseline, 2026-10-26). 규칙은 항상 이 값에서 계산.',
            'wild': scan_stats(read(WILD)), 'minions': scan_stats(read(MINIONS)), 'skills': skills}
    os.makedirs(os.path.dirname(BASELINE), exist_ok=True)
    with open(BASELINE, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write('\n')
    print('기록:', os.path.relpath(BASELINE, REPO), len(data['wild']), '몹 ·', len(skills), '스킬')


def planned():
    """(파일, 새 글, 바뀐 줄 수) 목록 — 저장소 파일."""
    base, hab = load_baseline(), habitats()
    wild, n1 = rewrite_stats(read(WILD), wild_rule(base['wild'], hab))
    sk, n2, unknown = rewrite_flats(read(SKILLS), {k: v['flat'] for k, v in base['skills'].items()}, hab)
    mi, n3 = rewrite_stats(read(MINIONS), minion_rule)
    if unknown:
        print('몹을 못 찾은 스킬 (그대로 둠):', ', '.join(unknown))
    return [(WILD, wild, n1), (SKILLS, sk, n2), (MINIONS, mi, n3)]


def tier_skill_avg():
    """등급별 원래 스킬 평균 × 배율 (model.py 용): {tier: {'basic': 평균, 'big': 평균, 'special': 평균}}"""
    base = load_baseline()['skills']
    acc = {}
    for v in base.values():
        if v['tier'] is None or not v['flat']:
            continue
        kind = 'big' if v['kind'] == 'hand' else v['kind']
        acc.setdefault(v['tier'], {}).setdefault(kind, []).append(max(v['flat']))
    return {t: {k: sum(x) / len(x) * TIERS[t]['mult'] for k, x in d.items()} for t, d in acc.items()}


def main(argv):
    cmd = argv[0] if argv else 'check'
    if cmd == 'baseline':
        make_baseline()
    elif cmd in ('apply', 'check'):
        bad = 0
        for path, new, n in planned():
            rel = os.path.relpath(path, REPO)
            if read(path) == new:
                print(f'{rel}: 규칙과 같음')
                continue
            bad += 1
            if cmd == 'apply':
                write(path, new)
                print(f'{rel}: {n}줄 바꿈')
            else:
                print(f'{rel}: 규칙과 다름 ({n}줄)')
        if cmd == 'check' and bad:
            return 1
    elif cmd == 'apply-mobs':
        if len(argv) < 2:
            print('사용: python3 tools/mob_spec.py apply-mobs <몹 파일>')
            return 2
        base, hab = load_baseline(), habitats()
        path = argv[1]
        text = read(path)
        known = scan_stats(text)
        missing = [m for m in known if m.startswith('CA_Wild_') and tier_of_mob(m, hab) is None]
        new, n = rewrite_stats(text, wild_rule(base['wild'], hab))
        if new != text:
            write(path, new)
        print(f'{path}: {n}줄 바꿈 (CA_Wild_ {sum(1 for m in known if m.startswith("CA_Wild_"))}마리)')
        if missing:
            print('등급을 모르는 몹 (그대로 둠):', ', '.join(missing))
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
