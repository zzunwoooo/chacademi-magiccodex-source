#!/usr/bin/env python3
"""전체 활성 마법 아이콘 누락 검수 (오픈 전 메모 #40). 저장소 안 자료만 본다 (서버·런처 접속 없음).

  python3 tools/spell_audit/audit_icons.py            # 운영 overlay 기준, 요약 + 누락 목록
  python3 tools/spell_audit/audit_icons.py --source   # 원본 mana-spells.yml 기준
  python3 tools/spell_audit/audit_icons.py --tsv out.tsv    # 마법별 전체 표 저장
  python3 tools/spell_audit/audit_icons.py --pack <리소스팩 폴더|assets 폴더>  # 런처 리소스팩을 직접 대조 (여러 번 가능)
  종료 코드: 확실한 누락(경로 비어 있음/형식 오류/어디에도 근거 없음)이 있으면 1

마법마다 보는 것
  1. 모드 기본 마법 파일(default-spells/NNN_<id>.yml)의 icon: -> SpellYamlLoader 의 자동 교체 규칙을 적용한 '실제 그리는 경로'
  2. 그 PNG 가 어디 있는가
     mod      = 모드 jar 안 (magic-codex-fabric/src/main/resources/assets/...)
     pack     = --pack 으로 준 리소스팩 안
     manifest = 저장소에는 PNG 가 없지만 예전 배포 리소스팩(chacademia-spells / chacademia-spells-expansion-3) 기록에 같은 경로가 있음
                (reference/output/discovery-editor-v1/icon-manifest.json, spell-classification-editor-v2/icon-manifest.json)
                -> 런처 리소스팩에 실제로 들어 있는지는 런처/PC 에서만 확인 가능
     none     = 어디에도 근거 없음 (누락 의심)
  3. 같은 마법의 다른 아이콘 기록이 서로 다른지 (발견 알림 discoveries.yml icon, PortableVFX spell-catalog.yml icon) - 참고용
"""
import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import spell_catalog as sc  # noqa: E402

ICON_RE = re.compile(r'^[a-z0-9_.-]+:textures/[a-z0-9_./-]+\.png$')
MANIFEST_V1 = 'reference/output/discovery-editor-v1/icon-manifest.json'
MANIFEST_V2 = 'reference/output/spell-classification-editor-v2/icon-manifest.json'


def manifest_paths():
    """예전 배포 리소스팩에 있었다고 기록된 아이콘 경로 집합 (assets/ns/... 형태) + 마법별 기록."""
    paths, by_id = set(), {}
    try:
        for row in sc.json_load(MANIFEST_V1):
            f = sc.icon_file(row.get('icon', ''))
            if f:
                paths.add(f)
                by_id.setdefault(row['id'], set()).add(f)
    except (OSError, ValueError):
        pass
    try:
        for row in sc.json_load(MANIFEST_V2):
            src = row.get('source', '').replace('\\', '/')
            i = src.find('assets/')
            if i >= 0:
                paths.add(src[i:])
                by_id.setdefault(row['id'], set()).add(src[i:])
    except (OSError, ValueError):
        pass
    return paths, by_id


def pack_files(packs):
    found = set()
    for p in packs:
        root = os.path.abspath(p)
        if os.path.basename(root) != 'assets' and os.path.isdir(os.path.join(root, 'assets')):
            root = os.path.join(root, 'assets')
        base = os.path.dirname(root)
        for d, _, files in os.walk(root):
            for f in files:
                if f.lower().endswith('.png'):
                    found.add(os.path.relpath(os.path.join(d, f), base).replace(os.sep, '/'))
    return found


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--source', action='store_true', help='원본 mana-spells.yml 기준 (기본: 운영 overlay)')
    ap.add_argument('--tsv', help='마법별 전체 표를 이 파일로 저장')
    ap.add_argument('--pack', action='append', default=[], help='런처 리소스팩 폴더(압축 푼 것). 여러 번 가능')
    a = ap.parse_args(argv)

    spells, mana_file = sc.active_spells(a.source)
    defaults = sc.default_spells()
    rules = sc.loader_icon_rules()
    mod_root = sc.path(sc.ASSETS)
    man, man_by_id = manifest_paths()
    packs = pack_files(a.pack)
    disc = (sc.load_yaml(sc.DISCOVERIES) or {}).get('spells') or {}
    vfx = (sc.load_yaml(sc.VFX_SOURCE if a.source else sc.VFX_OVERLAY) or {}).get('spells') or {}

    rows, missing, unverified, notes = [], [], [], []
    for s in spells:
        sid = s['id']
        d = defaults.get(sid)
        raw = d['icon'] if d else ''
        icon = sc.effective_icon(sid, raw, rules)
        f = sc.icon_file(icon) if icon else None
        if not d:
            where, why = 'none', '모드 기본 마법 파일 없음 (도감에 안 보임)'
        elif not icon:
            where, why = 'none', 'icon 비어 있음'
        elif not ICON_RE.match(icon):
            where, why = 'none', 'icon 형식 오류: ' + icon
        elif os.path.isfile(os.path.join(mod_root, f[len('assets/'):])):
            where, why = 'mod', ''
        elif f in packs:
            where, why = 'pack', ''
        elif f in man:
            where, why = 'manifest', '저장소에 PNG 없음 - 예전 배포 리소스팩 기록에만 있음 (런처 확인 필요)'
        else:
            other = sorted(man_by_id.get(sid, ()))
            where = 'none'
            why = '어디에도 PNG 근거 없음' + (' (예전 기록 경로: ' + ', '.join(other) + ')' if other else '')
        di = (disc.get(sid) or {}).get('icon', '') if isinstance(disc.get(sid), dict) else ''
        vi = (vfx.get(sid) or {}).get('icon', '') if isinstance(vfx.get(sid), dict) else ''
        diff = []
        if di and icon and di != icon:
            diff.append('발견알림=' + di)
        if vi and icon and vi != icon:
            diff.append('VFX카탈로그=' + vi)
        rows.append([s['number'], sid, s['name'], raw, icon, where, why, '; '.join(diff)])
        label = '%s %s (%s)' % (s['number'] if s['number'] is not None else '-', s['name'], sid)
        if where == 'none':
            missing.append(label + ' - ' + why)
        elif where == 'manifest':
            unverified.append(label + ' - ' + icon)
        if diff:
            notes.append(label + ' - 모드=' + icon + ' / ' + ', '.join(diff))

    counts = {}
    for r in rows:
        counts[r[5]] = counts.get(r[5], 0) + 1
    print('활성 마법 %d개 (기준: %s)' % (len(spells), mana_file))
    print('  모드 jar 안 PNG: %d · --pack 리소스팩: %d · 예전 리소스팩 기록만(런처 확인 필요): %d · 근거 없음(누락): %d'
          % (counts.get('mod', 0), counts.get('pack', 0), counts.get('manifest', 0), counts.get('none', 0)))
    print('\n[누락] %d개' % len(missing))
    for m in missing:
        print('  ' + m)
    if unverified and not a.pack:
        print('\n[런처 리소스팩에서만 확인 가능] %d개 (목록은 --tsv). --pack 으로 리소스팩 폴더를 주면 실제 파일과 대조합니다.' % len(unverified))
    elif unverified:
        print('\n[리소스팩에 없음 - 예전 기록에만] %d개' % len(unverified))
        for m in unverified:
            print('  ' + m)
    print('\n[참고: 다른 곳에 적힌 아이콘 경로와 다름] %d개 (모드 도감은 모드 경로를 씀)' % len(notes))
    for m in notes[:40]:
        print('  ' + m)
    if len(notes) > 40:
        print('  … 나머지는 --tsv')
    if a.tsv:
        sc.write_report(a.tsv, ['도감번호', 'id', '이름', '기본파일 icon', '모드가 그리는 경로', '위치', '사유', '다른 기록'], rows)
        print('\n표 저장: ' + a.tsv)
    return 1 if missing else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
