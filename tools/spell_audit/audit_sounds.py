#!/usr/bin/env python3
"""전체 활성 마법 사운드 정의 유무 검수 (오픈 전 메모 #47). 목록만 만들고 사운드를 지정하지 않는다.

  python3 tools/spell_audit/audit_sounds.py              # 운영 overlay 기준 요약 + 사운드 없는 마법 목록
  python3 tools/spell_audit/audit_sounds.py --source     # 원본 mana-spells.yml 기준
  python3 tools/spell_audit/audit_sounds.py --tsv out.tsv
  종료 코드: 사운드 없는 활성 마법이 있으면 1

마법 시전 사운드가 나는 경로 (코드 근거)
  A. MagicCodex 모드 SpellCastSounds (client/SpellCastSounds.java) 의 큐 id = 마법 id.
     서버가 시전 OK 를 돌려주면 ScreenVfxClient.onCast(id) -> SpellCastSounds.byId(id) -> magiccodex:<event> 재생.
     큐가 있어도 sounds.json 의 이벤트와 assets/magiccodex/sounds/<name>.ogg 가 있어야 실제로 들림 -> 셋 다 확인.
  B. 서버 쪽 (참고로 검사): MythicMobs 스킬(chacademi_spells.yml)의 sound 메커닉, PortableVFX 팩 vfx.json 의 sound 항목.
     2026-10-25 기준 둘 다 0건이라 A 가 유일한 마법별 시전음.
  (원소 공용음 wind.soft 등은 UI 효과음으로만 쓰이고 마법 시전에 연결돼 있지 않음.)
"""
import argparse
import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import spell_catalog as sc  # noqa: E402

CUES = sc.FABRIC + '/java/school/magiccodex/client/SpellCastSounds.java'
SOUNDS_JSON = sc.ASSETS + '/magiccodex/sounds.json'


def cues():
    src = sc.read(CUES)
    return {m.group(1): {'label': m.group(2), 'event': m.group(3)}
            for m in re.finditer(r'new Cue\("([^"]+)","([^"]*)","([^"]+)",', src)}


def sound_events():
    with open(sc.path(SOUNDS_JSON), encoding='utf-8') as f:
        return json.load(f)


def ogg_exists(name):
    """'magiccodex:cast/x' -> assets/magiccodex/sounds/cast/x.ogg"""
    ns, p = name.split(':', 1) if ':' in name else ('minecraft', name)
    if ns != 'magiccodex':
        return None  # 바닐라 소리는 클라이언트 기본 자산
    return os.path.isfile(sc.path(sc.ASSETS + '/magiccodex/sounds/' + p + '.ogg'))


def server_sound_spells(source):
    """MythicMobs 스킬에서 sound 메커닉을 쓰는 마법 id (spell= 인자 기준) + vfx.json 에 sound 가 든 팩 폴더."""
    out = set()
    text = sc.read(sc.MYTHIC_SOURCE if source else sc.MYTHIC_OVERLAY)
    for block in re.split(r'\n(?=CHA_)', text):
        if re.search(r'^\s*-\s*(sound|playsound)\{', block, re.M | re.I):
            m = re.search(r'spell=([a-z0-9_]+)', block)
            if m:
                out.add(m.group(1))
    packs = set()
    for f in glob.glob(sc.path('portable-vfx-runtime/assets/*/vfx.json')):
        with open(f, encoding='utf-8') as fh:
            if re.search(r'"sound', fh.read(), re.I):
                packs.add(os.path.basename(os.path.dirname(f)))
    return out, packs


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--source', action='store_true')
    ap.add_argument('--tsv')
    a = ap.parse_args(argv)

    spells, mana_file = sc.active_spells(a.source)
    table, events = cues(), sound_events()
    server, packs = server_sound_spells(a.source)
    active_ids = {s['id'] for s in spells}

    stale = sorted(set(table) - active_ids)
    # 이름이 바뀐 마법 힌트: 사운드 없는 마법의 아이콘 파일 이름이 안 쓰이는 큐 id 와 같으면 (예: feather_fall 아이콘 = feather_step.png)
    defaults, rules = sc.default_spells(), sc.loader_icon_rules()

    def hint(sid):
        d = defaults.get(sid)
        icon = sc.effective_icon(sid, d['icon'] if d else '', rules)
        base = os.path.splitext(os.path.basename(icon))[0] if icon else ''
        return base if base in stale else ''

    rows, none, broken = [], [], []
    for s in spells:
        sid = s['id']
        cue = table.get(sid)
        state, why = 'ok', ''
        if cue is None:
            state, why = ('server' if sid in server else 'none'), ('모드 시전음 큐 없음' + (' (MythicMobs sound 있음)' if sid in server else ''))
        else:
            ev = events.get(cue['event'])
            if ev is None:
                state, why = 'broken', 'sounds.json 에 이벤트 없음: ' + cue['event']
            else:
                names = [x if isinstance(x, str) else x.get('name', '') for x in ev.get('sounds', [])]
                missing = [n for n in names if ogg_exists(n) is False]
                if not names:
                    state, why = 'broken', 'sounds.json 이벤트에 파일 없음'
                elif missing:
                    state, why = 'broken', 'ogg 없음: ' + ', '.join(missing)
        label = '%s %s (%s)' % (s['number'] if s['number'] is not None else '-', s['name'], sid)
        h = hint(sid) if state == 'none' else ''
        if h:
            why += ' / 힌트: 안 쓰이는 큐 %s(%s)와 아이콘이 같음 - 이름 바뀐 마법일 수 있음 (오너 확인)' % (h, table[h]['label'])
        if state == 'none':
            none.append(label + (' - 힌트: 예전 큐 ' + h + '(' + table[h]['label'] + ')' if h else ''))
        elif state == 'broken':
            broken.append(label + ' - ' + why)
        rows.append([s['number'], sid, s['name'], cue['event'] if cue else '', state, why])

    print('활성 마법 %d개 (기준: %s)' % (len(spells), mana_file))
    print('  모드 시전음 있음: %d · 사운드 없음: %d · 정의는 있는데 파일 깨짐: %d'
          % (sum(1 for r in rows if r[4] == 'ok'), len(none), len(broken)))
    print('  서버 MythicMobs sound 메커닉: %d개 마법 · PortableVFX 팩 sound 항목: %d개 팩' % (len(server), len(packs)))
    print('\n[사운드 없음] %d개' % len(none))
    for x in none:
        print('  ' + x)
    if broken:
        print('\n[정의는 있는데 파일 깨짐] %d개' % len(broken))
        for x in broken:
            print('  ' + x)
    print('\n[참고: 큐는 있지만 활성 목록에 없는 마법] %d개 (꺼져 있거나 2026-10-20 버린 마법, 동작 영향 없음)' % len(stale))
    print('  ' + ', '.join(stale))
    if a.tsv:
        sc.write_report(a.tsv, ['도감번호', 'id', '이름', '사운드 이벤트', '상태', '사유'], rows)
        print('\n표 저장: ' + a.tsv)
    return 1 if (none or broken) else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
