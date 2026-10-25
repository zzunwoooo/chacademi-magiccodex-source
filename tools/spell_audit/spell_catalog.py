"""차카데미 마법 검수 스크립트 공용: '현재 활성 마법' 목록과 마법별 아이콘·사운드 근거를 저장소 파일에서 읽는다.

활성 마법의 기준 (오픈 전 메모 #26 근거)
  서버가 실제로 시전을 받아 주는 마법 = MagicCodexBridge mana-spells.yml 의 spells.<id> 중 enabled 가 false 가 아닌 것
  (ManaSpells.load: enabled:false 는 byId 에 안 들어가 시전 시 UNKNOWN). 운영 서버에 올라가는 값은
  deployment/operating-config/MagicCodexBridge/mana-spells.yml (원본과 같음. 예전 운영 전용 keep vine_ladder·light_mark 는
  2026-10-26 두 마법을 기획에서 지우며 뺐다). 기본은 운영 overlay, --source 로 원본.
  도감 번호 = PortableVFX spell-catalog.yml number (= 모드 기본 마법 파일 order). 이름 = mana-spells.yml name.

python3 표준 라이브러리만으로 돈다 (PyYAML 이 있으면 그걸 쓰고, 없으면 tools/operating_overlay.py 의 작은 파서).
"""
import glob
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(REPO, 'tools'))

MANA_OVERLAY = 'deployment/operating-config/MagicCodexBridge/mana-spells.yml'
MANA_SOURCE = 'magic-codex-paper/src/main/resources/mana-spells.yml'
RUNTIME_OVERLAY = 'deployment/operating-config/MagicCodexBridge/spell-runtime.yml'
RUNTIME_SOURCE = 'magic-mythic-paper/server-config/spell-runtime.yml'
VFX_OVERLAY = 'deployment/operating-config/PortableVFX/spell-catalog.yml'
VFX_SOURCE = 'portable-vfx-runtime/paper/src/main/resources/spell-catalog.yml'
MYTHIC_OVERLAY = 'deployment/operating-config/MythicMobs/Skills/chacademi_spells.yml'
MYTHIC_SOURCE = 'magic-mythic-paper/mythicmobs/Skills/chacademi_spells.yml'
DISCOVERIES = 'magic-discovery-paper/src/main/resources/discoveries.yml'
FABRIC = 'magic-codex-fabric/src/main'
ASSETS = FABRIC + '/resources/assets'
DEFAULT_SPELLS = ASSETS + '/magiccodex/default-spells'


def path(rel):
    return os.path.join(REPO, rel)


def rel(p):
    return os.path.relpath(p, REPO).replace(os.sep, '/')


def load_yaml(rel_path):
    p = path(rel_path)
    try:
        import yaml  # noqa: PLC0415
        with open(p, encoding='utf-8') as f:
            return yaml.safe_load(f)
    except ImportError:
        import operating_overlay  # noqa: PLC0415
        return operating_overlay.load_yaml(p)


def read(rel_path):
    with open(path(rel_path), encoding='utf-8') as f:
        return f.read()


def _top(text, key):
    """맨 앞 칸 'key: 값' 한 줄 (기본 마법 파일은 여러 줄 따옴표가 있어 작은 파서가 못 읽으므로 필요한 키만 줄로 읽는다)."""
    m = re.search(r'^' + re.escape(key) + r':[ \t]*(.*)$', text, re.M)
    if not m:
        return None
    v = m.group(1).strip()
    if len(v) >= 2 and v[0] == v[-1] and v[0] in '\'"':
        v = v[1:-1]
    return v


def default_spells():
    """모드에 들어 있는 기본 마법 파일: id -> {file, name, order, icon, category}."""
    out = {}
    for f in sorted(glob.glob(path(DEFAULT_SPELLS + '/*.yml'))):
        with open(f, encoding='utf-8') as fh:
            t = fh.read()
        sid = _top(t, 'id')
        if not sid:
            continue
        order = _top(t, 'order')
        out[sid] = {'file': rel(f), 'name': _top(t, 'name') or '', 'icon': _top(t, 'icon') or '',
                    'category': _top(t, 'category') or '', 'order': int(order) if order and order.lstrip('-').isdigit() else None}
    return out


def active_spells(source=False):
    """[{id, name, number, enabled_file}] 도감 번호 순 (번호 없는 것은 뒤에 파일 순)."""
    mana_file = MANA_SOURCE if source else MANA_OVERLAY
    mana = (load_yaml(mana_file) or {}).get('spells') or {}
    vfx = (load_yaml(VFX_SOURCE if source else VFX_OVERLAY) or {}).get('spells') or {}
    defaults = default_spells()
    rows = []
    for i, (sid, s) in enumerate(mana.items()):
        if not isinstance(s, dict) or s.get('enabled', True) is False:
            continue
        number = (vfx.get(sid) or {}).get('number')
        if number is None:
            number = (defaults.get(sid) or {}).get('order')
        rows.append({'id': sid, 'name': s.get('name', sid), 'number': number, 'file_index': i,
                     'permission': s.get('permission', ''), 'command': s.get('command', '')})
    rows.sort(key=lambda r: (r['number'] is None, r['number'] if r['number'] is not None else 0, r['file_index']))
    return rows, mana_file


# ---------------------------------------------------------------- 클라이언트 아이콘 경로 계산 (SpellYamlLoader.parse 와 같은 규칙)

def _java_strings(block):
    return re.findall(r'"([^"]*)"', block)


def loader_icon_rules():
    """SpellYamlLoader / MobSpellIcons 의 아이콘 자동 교체 규칙을 소스에서 읽는다 (코드가 바뀌면 같이 따라감)."""
    loader = read(FABRIC + '/java/school/magiccodex/client/SpellYamlLoader.java')
    mob = read(FABRIC + '/java/school/magiccodex/client/MobSpellIcons.java')

    def set_of(name):
        m = re.search(name + r'\s*=\s*Set\.of\(([^;]*)\);', loader, re.S)
        return set(_java_strings(m.group(1))) if m else set()

    def map_of(src, name):
        m = re.search(name + r'\s*=\s*Map\.ofEntries\((.*?)\);', src, re.S)
        if not m:
            return {}
        return dict(re.findall(r'Map\.entry\("([^"]+)",\s*"([^"]*)"\)', m.group(1)))

    return {'reviewed': set_of('REVIEWED_ICON_IDS'), 'prepared': set_of('PREPARED_20261020_ICON_IDS'),
            'replaced': map_of(loader, 'REPLACED_ICON_PREVIOUS_PATHS'), 'mob_previous': map_of(mob, 'PREVIOUS')}


def effective_icon(sid, icon, rules):
    """SpellYamlLoader.parse 의 아이콘 결정 (기본 마법 파일 그대로일 때 모드가 실제로 그릴 경로)."""
    icon = icon or ''
    if not icon and sid in rules['reviewed']:
        icon = 'magiccodex:textures/spells/reviewed_20261016/' + sid + '.png'
    if not icon and sid in rules['prepared']:
        icon = 'magiccodex:textures/spells/reviewed_20261020/' + sid + '.png'
    if sid in rules['replaced'] and (not icon or rules['replaced'][sid] == icon):
        icon = 'magiccodex:textures/spells/reviewed_20261020/' + sid + '.png'
    prev = rules['mob_previous'].get(sid)
    if prev is not None and (not icon or prev == icon):
        icon = 'magiccodex:textures/spells/mob_skill/' + sid + '.png'
    return icon


def icon_file(icon):
    """'ns:textures/a/b.png' -> 'assets/ns/textures/a/b.png' (리소스팩 안 상대 경로)."""
    if ':' not in icon:
        return None
    ns, p = icon.split(':', 1)
    return 'assets/' + ns + '/' + p


def json_load(rel_path):
    with open(path(rel_path), encoding='utf-8') as f:
        return json.load(f)


def write_report(out_path, header, rows):
    """탭 구분 표 (엑셀에서 바로 열림)."""
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, 'w', encoding='utf-8-sig', newline='') as f:
        f.write('\t'.join(header) + '\n')
        for r in rows:
            f.write('\t'.join('' if v is None else str(v) for v in r) + '\n')
