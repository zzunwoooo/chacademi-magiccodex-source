"""2026-10-31 신규 마법 150종 (301~450, 기획: 프로젝트 문서 '차카데미 신규 마법 150종 기획안', VFX: PC 차카데미 vfx_Claude\\<Bundle>).
오너: "실제 기능 개발도 하고, 모험 일지도 다 적어봐". 마나/쿨/피해/반경은 임시값 (밸런스 미확정), 발견 조건은 나중에 한꺼번에.

Same row shape and outputs as tools/nova_spells.py (that file's docstring), rows in tools/nova150/{wind,fire,water,earth,light,dark}.py,
Java in Nova150<Element>.java (chanova types registered from NovaMech). research = 도감 '연구 기록' (모험일지).
"""
import os, re, sys, hashlib
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE); REPO = os.path.dirname(ROOT)
sys.path.insert(0, HERE)
import arcana_spells as _a
from nova.common import N, ev, cast  # noqa: F401
from nova150 import wind, fire, water, earth, light, dark   # noqa: E402
M = wind.M + fire.M + water.M + earth.M + light.M + dark.M
EXTRA = {}
for _m in (wind, fire, water, earth, light, dark): EXTRA.update(getattr(_m, 'EXTRA', {}))

def routes():
    # utility = the MagicCodexBridge handles it (SpellUtility / LifeSpells); the Mythic skill only notes the cast
    return [(i, 'CHA_' + i, tgt, rng, base, ratio, all(l.startswith('chanote') for l in lines), lines, note) for (o, i, nm, cat, tgt, rng, base, ratio, mana, cd, lines, note, *_r) in M]

MARK = '  # ---- 301~450 신규 150종 (magic-mythic-paper/tools/nova150_spells.py) ----'

def write_all():
    ids = [r[1] for r in M]; assert len(ids) == len(set(ids)), 'duplicate id'
    import spell_order   # 2026-10-25 renumber: number/order come from tools/spell_order.py
    rows = sorted((spell_order.number(i, o), i, nm, cat, mana, cd, en, desc, disc, res, bundle.lower(), hold, P)
                  for (o, i, nm, cat, tgt, rng, base, ratio, mana, cd, lines, note, temp, desc, disc, res, bundle, hold, P, en) in M)
    folders = {bundle.lower(): bundle for (*_x, bundle, hold, P, en) in M}
    ds = os.path.join(REPO, 'magic-codex-fabric/src/main/resources/assets/magiccodex/default-spells')
    idx = [l for l in _a._read(os.path.join(ds, 'index.txt')).split('\n') if l.strip()]
    hashes = [l.rstrip('\r') for l in open(os.path.join(ds, 'known-hashes.tsv'), encoding='utf-8', newline='').read().split('\n') if l.strip()]
    for (o, i, nm, cat, mana, cd, en, desc, disc, res, *_r) in rows:
        fn = f'{o:03d}_{i}.yml'; path = os.path.join(ds, fn)
        old = _a._read(path) if os.path.exists(path) else ''
        icon = (re.search(r'(?m)^icon: (.*)$', old) or [None, "''"])[1]
        y = (f"id: {i}\nname: {nm}\ncategory: {cat}\ndescription: {_a._q(desc)}\nicon: {icon}\npermission: magic.learned.{i}\n"
             f"discovery:\n  description: {_a._q(disc) if disc else _a._q('아직 알려지지 않았습니다.')}\nresearch: {_a._q(res) if res else chr(39)*2}\n"
             f"cast:\n  command: 마법 {i}\n  cooldown-seconds: {cd}\n  mana-cost: {mana}\norder: {o}\n")
        _a._write_if(path, y)
        if f'{o:03d}_{i}' not in idx: idx.append(f'{o:03d}_{i}')
        h = hashlib.sha256(y.encode('utf-8')).hexdigest()
        if f'{fn}\t{h}' not in hashes: hashes.append(f'{fn}\t{h}')
    idx.sort(key=lambda l: (int(l.split('_', 1)[0]) if l.split('_', 1)[0].isdigit() else 9999, l))
    _a._write_if(os.path.join(ds, 'index.txt'), '\n'.join(idx) + '\n')
    with open(os.path.join(ds, 'known-hashes.tsv'), 'w', encoding='utf-8', newline='') as f: f.write('\r\n'.join(hashes) + '\r\n')
    _a._upsert(os.path.join(REPO, 'magic-codex-paper/src/main/resources/mana-spells.yml'), [
        (i, (lambda old, i=i, nm=nm, mana=mana, cd=cd, en=en:
             f"  {i}:\n    name: {nm}\n    enabled: {'true' if en else 'false'}\n    command: 마법 {i}\n    permission: magic.learned.{i}\n    mana-cost: {mana}\n    cooldown-seconds: {cd}\n"))
        for (o, i, nm, cat, mana, cd, en, *_r) in rows], MARK)
    def cat_entry(old, o, i, nm, mana, cd, en, pack, P):
        icon = (_a._field(old, 'icon') if old else None) or "''"
        ph_ = ("    phases:\n" + _a._phase_yaml(pack, P)) if P else "    phases: []\n"
        return (f"  {i}:\n    number: {o}\n    name: {nm}\n    permission: magic.learned.{i}\n    icon: {icon}\n    mana-cost: {mana}\n    cooldown-seconds: {cd}\n"
                f"    reviewed: {'true' if en else 'false'}\n    enabled: {'true' if en else 'false'}\n    aliases: []\n" + ph_ +
                f"    preparation-status: server-events-connected\n")
    _a._upsert(os.path.join(REPO, 'portable-vfx-runtime/paper/src/main/resources/spell-catalog.yml'), [
        (i, (lambda old, o=o, i=i, nm=nm, mana=mana, cd=cd, en=en, pack=pack, P=P: cat_entry(old, o, i, nm, mana, cd, en, pack, P)))
        for (o, i, nm, cat, mana, cd, en, desc, disc, res, pack, hold, P) in rows], MARK)
    def bind_entry(old, o, i, pack, hold, P):
        ph_ = ("    phases:\n" + _a._phase_yaml(pack, P)) if P else "    phases: []\n"
        return (f"  {i}:\n    folder: {folders[pack]}\n    number: {o}\n    range: 30\n    speed: 1\n    trajectory: server-events\n    max-ticks: {hold + 100}\n"
                f"    sequence:\n      sustainStart: 0\n    holdTicks: {hold}\n" + ph_ +
                "    review: nova150 301~450 (tools/nova150_spells.py) - phases from the delivered VFX NOTES, events sent by chanova; game QA pending\n")
    _a._upsert(os.path.join(REPO, 'portable-vfx-runtime/paper/src/main/resources/spell-bindings.yml'), [
        (i, (lambda old, o=o, i=i, pack=pack, hold=hold, P=P: bind_entry(old, o, i, pack, hold, P)))
        for (o, i, nm, cat, mana, cd, en, desc, disc, res, pack, hold, P) in rows], MARK)
    t = ['| # | id | 이름 | 대상 | 마나 / 쿨 | 피해 (base + 마력×) | 구현 | 임시값 | VFX 팩 |', '|---|---|---|---|---|---|---|---|---|']
    for (o, i, nm, cat, tgt, rng, base, ratio, mana, cd, lines, note, temp, desc, disc, res, bundle, hold, P, en) in sorted(M):
        dmg = f'{base} + {ratio}' if base or ratio else '-'
        t.append(f'| {o} | `{i}` | {nm} | {tgt} {rng} | {mana} / {cd}초 | {dmg} | {note} | {temp} | {bundle}{"" if en else " (꺼짐)"} |')
    doc = _a._read(os.path.join(HERE, 'nova150_spells_doc.md')).replace('{{TABLE}}', '\n'.join(t))
    _a._write_if(os.path.join(REPO, 'docs/handoff/NOVA150_301_450.md'), doc)
    print(len(M), 'nova150 spells written')

if __name__ == '__main__':
    write_all()
