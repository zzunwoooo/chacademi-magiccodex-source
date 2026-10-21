#!/usr/bin/env python3
"""운영 설정 overlay(deployment/operating-config/**)와 원본 설정의 차이를 검사하고, 원본에서 overlay를 다시 만든다.

python3 표준 라이브러리만 쓴다 (PyYAML 없음, 아래의 작은 YAML 파서는 이 저장소 설정 파일이 쓰는 문법만 지원).

  python3 tools/operating_overlay.py check        # 배포 전 필수. 차이가 있으면 종료 코드 1
  python3 tools/operating_overlay.py detect       # overlay와 원본이 다른 곳 전부 + 의도된 운영 값(keep)인지 표시
  python3 tools/operating_overlay.py regenerate   # 원본으로 overlay 재생성(의도된 운영 값·보류 항목은 유지) + manifest sha256 갱신

설정: deployment/operating-config-overrides.json
  files    overlay 파일 ↔ 원본 파일 대응, entries = 마법 하나하나가 들어 있는 맵 경로
  keep     의도된 운영 값(overlay 쪽 값을 유지). 재생성 시 그대로 옮기고 목록을 출력
  pending  다른 작업 묶음이 고치는 중이라 이번에는 건드리지 않는 항목. 재생성 시 overlay 그대로, 검사 시 경고만
  donation 기증 목록(donation-spells.yml)이 배울 수 있는 마법을 모두 담는지 확인
"""
import hashlib
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG = os.path.join(REPO, 'deployment', 'operating-config-overrides.json')
MANIFEST = os.path.join(REPO, 'deployment', 'operating-config-manifest.json')


# ---------------------------------------------------------------- 작은 YAML 파서 (블록 맵·리스트, 흐름 [] {}, 따옴표, >- |)
class YamlError(ValueError):
    pass


_INT = re.compile(r'^[-+]?(0|[1-9][0-9_]*)$')
_FLOAT = re.compile(r'^[-+]?([0-9][0-9_]*)?\.[0-9_]*([eE][-+][0-9]+)?$|^[-+]?[0-9][0-9_]*[eE][-+]?[0-9]+$')


def _plain(s):
    t = s.strip()
    if t in ('', '~', 'null', 'Null', 'NULL'):
        return None
    if t in ('true', 'True', 'TRUE', 'yes', 'Yes', 'YES', 'on', 'On', 'ON'):
        return True
    if t in ('false', 'False', 'FALSE', 'no', 'No', 'NO', 'off', 'Off', 'OFF'):
        return False
    if _INT.match(t):
        return int(t.replace('_', ''))
    if _FLOAT.match(t) and t not in ('.', '-.', '+.'):
        try:
            return float(t.replace('_', ''))
        except ValueError:
            return t
    if t in ('.inf', '.Inf', '+.inf'):
        return float('inf')
    if t in ('-.inf', '-.Inf'):
        return float('-inf')
    return t


def _dq(s, i):
    """s[i] == '"'. (값, 다음 위치)"""
    out = []
    i += 1
    esc = {'n': '\n', 't': '\t', '"': '"', '\\': '\\', '/': '/', '0': '\0', 'r': '\r', ' ': ' '}
    while i < len(s):
        c = s[i]
        if c == '\\':
            n = s[i + 1]
            if n == 'u':
                out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
            if n == 'x':
                out.append(chr(int(s[i + 2:i + 4], 16))); i += 4; continue
            out.append(esc.get(n, n)); i += 2; continue
        if c == '"':
            return ''.join(out), i + 1
        out.append(c); i += 1
    raise YamlError('닫히지 않은 큰따옴표: ' + s)


def _sq(s, i):
    out = []
    i += 1
    while i < len(s):
        c = s[i]
        if c == "'":
            if i + 1 < len(s) and s[i + 1] == "'":
                out.append("'"); i += 2; continue
            return ''.join(out), i + 1
        out.append(c); i += 1
    raise YamlError('닫히지 않은 작은따옴표: ' + s)


def _flow(s, i):
    """흐름 값 하나. (값, 다음 위치)"""
    while i < len(s) and s[i] in ' \t':
        i += 1
    c = s[i]
    if c == '[':
        out = []; i += 1
        while True:
            while s[i] in ' \t,':
                i += 1
            if s[i] == ']':
                return out, i + 1
            v, i = _flow(s, i)
            out.append(v)
            while s[i] in ' \t':
                i += 1
            if s[i] == ',':
                i += 1
    if c == '{':
        out = {}; i += 1
        while True:
            while s[i] in ' \t,':
                i += 1
            if s[i] == '}':
                return out, i + 1
            k, i = _flow(s, i)
            while s[i] in ' \t':
                i += 1
            if s[i] != ':':
                raise YamlError('흐름 맵 키 뒤에 ":" 없음: ' + s)
            v, i = _flow(s, i + 1)
            out[k] = v
            while s[i] in ' \t':
                i += 1
            if s[i] == ',':
                i += 1
    if c == '"':
        return _dq(s, i)
    if c == "'":
        return _sq(s, i)
    j = i
    while j < len(s) and s[j] not in ',]}' and not (s[j] == ':' and (j + 1 == len(s) or s[j + 1] in ' ,]}')):
        j += 1
    return _plain(s[i:j]), j


def _cut_comment(s):
    """일반 문자열에서 ' #' 뒤는 주석."""
    m = re.search(r'(^|\s)#', s)
    return s[:m.start()] if m else s


def _closed(s):
    depth = 0; q = None; i = 0
    while i < len(s):
        c = s[i]
        if q:
            if c == '\\' and q == '"':
                i += 2; continue
            if c == q:
                q = None
        elif c in '"\'':
            q = c
        elif c in '[{':
            depth += 1
        elif c in ']}':
            depth -= 1
        i += 1
    return depth <= 0


class _Lines:
    def __init__(self, text):
        self.raw = text.split('\n')
        self.items = []  # [indent, content, raw index]
        for n, line in enumerate(self.raw):
            if '\t' in line[:len(line) - len(line.lstrip())]:
                raise YamlError('탭 들여쓰기 %d줄' % (n + 1))
            s = line.strip()
            if not s or s.startswith('#'):
                continue
            if s in ('---', '...'):
                continue
            self.items.append([len(line) - len(line.lstrip(' ')), line.strip(' ').rstrip(), n])


def _key_split(content):
    """'key: value' 이면 (key, rest) 아니면 None."""
    if content[0] in '"\'':
        k, j = (_dq if content[0] == '"' else _sq)(content, 0)
        rest = content[j:].lstrip(' ')
        if rest.startswith(':') and (len(rest) == 1 or rest[1] == ' '):
            return k, rest[1:].strip(' ')
        return None
    if content[0] in '[{':
        return None
    m = re.search(r':( |$)', content)
    if not m:
        return None
    key = content[:m.start()].rstrip()
    if '#' in key and re.search(r'\s#', key):
        return None
    return _plain(key) if _INT.match(key) else key, content[m.end():].strip(' ')


class _Parser:
    def __init__(self, text):
        self.L = _Lines(text)
        self.it = self.L.items

    def parse(self):
        if not self.it:
            return None
        v, i = self.block(0, self.it[0][0])
        if i != len(self.it):
            raise YamlError('해석하지 못한 줄 %d: %s' % (self.it[i][2] + 1, self.it[i][1]))
        return v

    def block(self, i, indent):
        c = self.it[i][1]
        if c == '-' or c.startswith('- '):
            return self.seq(i, indent)
        if c[0] in '[{':
            return self.scalar(c, i, indent - 1)
        return self.mapping(i, indent)

    def scalar(self, rest, i, indent):
        """값 문자열 rest(i번째 항목 줄의 일부). 여러 줄 흐름·일반 문자열 이어붙임. (값, 다음 i)"""
        if rest[0] in '|>':
            m = re.match(r'^([|>])([-+]?)\s*(#.*)?$', rest)
            if not m:
                raise YamlError('블록 문자열: ' + rest)
            return self.block_scalar(i, indent, m.group(1), m.group(2))
        if rest[0] in '[{':
            text = rest; j = i + 1
            while not _closed(text):
                text += ' ' + self.it[j][1]; j += 1
            v, k = _flow(text, 0)
            if _cut_comment(text[k:]).strip():
                raise YamlError('흐름 값 뒤 내용: ' + text)
            return v, j
        if rest[0] == '"':
            v, k = _dq(rest, 0)
            return v, i + 1
        if rest[0] == "'":
            v, k = _sq(rest, 0)
            return v, i + 1
        text = _cut_comment(rest).rstrip()
        j = i + 1
        while j < len(self.it) and self.it[j][0] > indent:
            text += ' ' + _cut_comment(self.it[j][1]).strip(); j += 1
        return _plain(text), j

    def block_scalar(self, i, indent, style, chomp):
        raw = self.L.raw
        start = self.it[i][2] + 1
        n = start
        body = []
        block_indent = None
        while n < len(raw):
            line = raw[n]
            if line.strip() == '':
                body.append(''); n += 1; continue
            ind = len(line) - len(line.lstrip(' '))
            if ind <= indent:
                break
            if block_indent is None:
                block_indent = ind
            body.append(line[block_indent:]); n += 1
        while body and body[-1] == '':
            body.pop()
        if style == '|':
            text = '\n'.join(body)
        else:
            text = ''; prev_blank = True
            for b in body:
                if b == '':
                    text += '\n'; prev_blank = True; continue
                text += ('' if prev_blank else ' ') + b; prev_blank = False
        if chomp != '-':
            text += '\n'
        j = i + 1
        while j < len(self.it) and self.it[j][2] < n:
            j += 1
        return text, j

    def mapping(self, i, indent):
        out = {}
        while i < len(self.it) and self.it[i][0] == indent:
            content = self.it[i][1]
            if content.startswith('- ') or content == '-':
                break
            kv = _key_split(content)
            if kv is None:
                raise YamlError('맵 키가 아님 %d줄: %s' % (self.it[i][2] + 1, content))
            key, rest = kv
            rest = rest if not rest.startswith('#') else ''
            if rest == '':
                j = i + 1
                if j < len(self.it) and self.it[j][0] > indent:
                    v, i = self.block(j, self.it[j][0])
                elif j < len(self.it) and self.it[j][0] == indent and (self.it[j][1] == '-' or self.it[j][1].startswith('- ')):
                    v, i = self.seq(j, indent)
                else:
                    v, i = None, j
            else:
                v, i = self.scalar(rest, i, indent)
            out[key] = v
        if i < len(self.it) and self.it[i][0] > indent:
            raise YamlError('들여쓰기 오류 %d줄' % (self.it[i][2] + 1))
        return out, i

    def seq(self, i, indent):
        out = []
        while i < len(self.it) and self.it[i][0] == indent and (self.it[i][1] == '-' or self.it[i][1].startswith('- ')):
            content = self.it[i][1]
            rest = content[1:].lstrip(' ')
            if rest == '' or rest.startswith('#'):
                j = i + 1
                if j < len(self.it) and self.it[j][0] > indent:
                    v, i = self.block(j, self.it[j][0])
                else:
                    v, i = None, j
            elif rest[0] not in '"\'[{' and _key_split(rest) is not None or rest[0] in '"\'' and _key_split(rest) is not None:
                sub = indent + (len(content) - len(rest))
                self.it[i] = [sub, rest, self.it[i][2]]
                v, i = self.mapping(i, sub)
            else:
                v, i = self.scalar(rest, i, indent)
            out.append(v)
        return out, i


def load_yaml_text(text):
    return _Parser(text).parse()


def load_yaml(path):
    with open(path, encoding='utf-8') as f:
        return load_yaml_text(f.read())


# ---------------------------------------------------------------- 텍스트 블록 조작 (재생성용: 원본 글자를 최대한 그대로 둠)
def _meaningful(line):
    s = line.strip()
    return s != '' and not s.startswith('#')


def _ind(line):
    return len(line) - len(line.lstrip(' '))


def _line_key(line):
    s = line.strip()
    if s.startswith('- ') or s == '-':
        return None
    kv = _key_split(s)
    return None if kv is None else str(kv[0])


def _block_end(lines, start, end_limit):
    """lines[start] 키 줄의 블록 끝(뒤쪽 빈 줄·주석 제외)."""
    ind = _ind(lines[start])
    j = start + 1
    while j < end_limit:
        line = lines[j]
        if _meaningful(line):
            li = _ind(line)
            if li < ind or li == ind and not line.strip().startswith('-'):
                break
        j += 1
    while j - 1 > start and not _meaningful(lines[j - 1]):
        j -= 1
    return j


def _child_indent(lines, start, end):
    for j in range(start, end):
        if _meaningful(lines[j]):
            return _ind(lines[j])
    return None


def find_block(lines, path):
    """path(키 목록)의 블록 (시작, 끝) 줄 번호. 없으면 (None, (덧붙일 위치, 들여쓰기))."""
    lo, hi, base = 0, len(lines), -2
    while hi > 0 and not _meaningful(lines[hi - 1]):
        hi -= 1
    for depth, key in enumerate(path):
        inner_lo = lo if depth == 0 else lo + 1
        ci = _child_indent(lines, inner_lo, hi)
        found = None
        if ci is not None:
            for j in range(inner_lo, hi):
                if _meaningful(lines[j]) and _ind(lines[j]) == ci and _line_key(lines[j]) == str(key):
                    found = j
                    break
        if found is None:
            return None, (hi, ci if ci is not None else base + 2)
        base = _ind(lines[found])
        lo, hi = found, _block_end(lines, found, hi)
    return (lo, hi), None


def _reindent(block, delta):
    out = []
    for line in block:
        if line.strip() == '':
            out.append(line)
        elif delta >= 0:
            out.append(' ' * delta + line)
        else:
            if _ind(line) < -delta:
                raise YamlError('들여쓰기를 줄일 수 없음: ' + line)
            out.append(line[-delta:])
    return out


def transplant(target_lines, donor_lines, path):
    """donor(운영 overlay)의 path 블록을 target(재생성 중인 원본 글)에 그대로 옮긴다."""
    got, _ = find_block(donor_lines, path)
    if got is None:
        raise YamlError('운영 overlay에 없는 경로: ' + '.'.join(map(str, path)))
    block = donor_lines[got[0]:got[1]]
    donor_indent = _ind(block[0])
    here, where = find_block(target_lines, path)
    if here is not None:
        delta = _ind(target_lines[here[0]]) - donor_indent
        return target_lines[:here[0]] + _reindent(block, delta) + target_lines[here[1]:]
    at, indent = where
    return target_lines[:at] + _reindent(block, indent - donor_indent) + target_lines[at:]


# ---------------------------------------------------------------- 비교
def get_path(doc, path):
    cur = doc
    for k in path:
        if not isinstance(cur, dict) or k not in cur:
            return KeyError
        cur = cur[k]
    return cur


def set_path(doc, path, value):
    cur = doc
    for k in path[:-1]:
        cur = cur.setdefault(k, {})
    if value is KeyError:
        cur.pop(path[-1], None)
    else:
        cur[path[-1]] = value


_TOKEN = re.compile(r'(element|spell)=([A-Za-z0-9_]+)')


def _mythic_missing_tokens(src_entry, op_entry):
    """미몹 스킬 줄에서 원본에 있는 element=/spell= 이 overlay에 없는 것."""
    want = set(); have = set()
    for line in (src_entry or {}).get('Skills') or []:
        want.update(_TOKEN.findall(str(line)))
    for line in (op_entry or {}).get('Skills') or []:
        have.update(_TOKEN.findall(str(line)))
    return sorted('%s=%s' % t for t in want - have)


def describe_entry_drift(kind, key, src, op):
    """항목 하나의 차이를 사람이 읽는 줄로."""
    rows = []
    if kind == 'mythic':
        miss = _mythic_missing_tokens(src, op)
        if miss:
            rows.append(('필드 누락', '%s: %s' % (key, ', '.join(miss))))
        if (src or {}).get('Skills') != (op or {}).get('Skills') and not miss:
            rows.append(('값 다름', '%s: Skills 줄이 원본과 다름' % key))
        elif (src or {}).get('Skills') != (op or {}).get('Skills'):
            pass
        others = sorted(k for k in set(src) | set(op) if k != 'Skills' and src.get(k, KeyError) != op.get(k, KeyError))
        if others:
            rows.append(('값 다름', '%s: %s' % (key, ', '.join(others))))
        return rows
    if isinstance(src, dict) and isinstance(op, dict):
        if src.get('enabled') is True and op.get('enabled') is False:
            rows.append(('꺼짐', key))
        missing = sorted(k for k in src if k not in op)
        if missing:
            rows.append(('필드 누락', '%s: %s' % (key, ', '.join(missing))))
        changed = sorted(k for k in src if k in op and src[k] != op[k] and not (k == 'enabled' and src[k] is True and op[k] is False))
        if changed:
            rows.append(('값 다름', '%s: %s' % (key, ', '.join('%s %s→%s' % (k, _short(op[k]), _short(src[k])) for k in changed))))
        extra = sorted(k for k in op if k not in src)
        if extra:
            rows.append(('남는 필드', '%s: %s' % (key, ', '.join(extra))))
        return rows
    if src != op:
        rows.append(('값 다름', '%s: %s → %s' % (key, _short(op), _short(src))))
    return rows


def _short(v):
    s = json.dumps(v, ensure_ascii=False)
    return s if len(s) <= 40 else s[:37] + '...'


def file_drift(spec, src_doc, op_doc, keep_paths, pending_paths):
    """(차이 목록[(분류, 설명)], 보류 목록)"""
    expected = json.loads(json.dumps(src_doc))
    for p in keep_paths + pending_paths:
        set_path(expected, p, get_path(op_doc, p))
    entries = spec.get('entries')
    kind = spec.get('kind', 'yaml')
    rows = []
    epath = entries.split('.') if entries else []
    e_src = get_path(expected, epath) if epath else expected
    e_op = get_path(op_doc, epath) if epath else op_doc
    if e_src is KeyError or e_op is KeyError or not isinstance(e_src, dict) or not isinstance(e_op, dict):
        return [('구조', '%s 맵이 없음' % (entries or '최상위'))], []
    for k in e_src:
        if k not in e_op:
            rows.append(('누락 항목', str(k)))
        elif e_src[k] != e_op[k]:
            rows.extend(describe_entry_drift(kind, str(k), e_src[k], e_op[k]))
    for k in e_op:
        if k not in e_src:
            rows.append(('남는 항목', str(k)))
    if epath:
        rest_src = dict(expected); rest_op = dict(op_doc)
        rest_src.pop(epath[0], None); rest_op.pop(epath[0], None)
        for k in sorted(set(rest_src) | set(rest_op), key=str):
            if rest_src.get(k, KeyError) != rest_op.get(k, KeyError):
                rows.append(('값 다름', '최상위 %s' % k))
    pend = []
    for p in pending_paths:
        s = get_path(src_doc, p); o = get_path(op_doc, p)
        if s != o:
            if kind == 'mythic' and isinstance(s, dict) and isinstance(o, dict):
                miss = _mythic_missing_tokens(s, o)
                pend.append('%s (원본과 다름%s)' % ('.'.join(map(str, p)), ', 누락 ' + ', '.join(miss) if miss else ''))
            else:
                pend.append('%s (원본과 다름)' % '.'.join(map(str, p)))
    return rows, pend


# ---------------------------------------------------------------- 명령
def load_config():
    with open(CONFIG, encoding='utf-8') as f:
        return json.load(f)


def _paths(cfg, section, overlay):
    return [x['path'] for x in cfg.get(section, []) if x['file'] == overlay]


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        h.update(f.read())
    return h.hexdigest()


def check_donation(cfg):
    d = cfg.get('donation')
    if not d:
        return []
    cat = load_yaml(os.path.join(REPO, d['catalog'])).get('spells') or {}
    spells = load_yaml(os.path.join(REPO, d['spells'])).get('spells') or {}
    exclude = set(d.get('exclude', {}))
    orphans = set(d.get('orphans', []))
    rows = []
    for sid in spells:
        if sid in exclude:
            if sid in cat:
                rows.append(('기증 제외인데 목록에 있음', sid))
        elif sid not in cat:
            rows.append(('기증 목록 누락', sid))
    for sid, v in cat.items():
        if sid not in spells and sid not in orphans:
            rows.append(('기증 목록에 없는 마법 ID', sid))
        if not isinstance(v, dict) or v.get('permission') != 'magic.learned.' + sid:
            rows.append(('기증 권한 줄 이상', sid))
    return rows


def cmd_check(cfg, verbose=False):
    total = 0
    manifest = {}
    if os.path.exists(MANIFEST):
        with open(MANIFEST, encoding='utf-8') as f:
            manifest = {m['path']: m for m in json.load(f)}
    for spec in cfg['files']:
        op_path = os.path.join(REPO, spec['overlay']); src_path = os.path.join(REPO, spec['source'])
        rows, pend = file_drift(spec, load_yaml(src_path), load_yaml(op_path), _paths(cfg, 'keep', spec['overlay']), _paths(cfg, 'pending', spec['overlay']))
        m = manifest.get(spec['overlay'])
        if m is None:
            rows.append(('manifest', '항목 없음'))
        elif m.get('sha256') != sha256(op_path):
            rows.append(('manifest', 'sha256 불일치 (regenerate 로 갱신)'))
        status = '차이 %d' % len(rows) if rows else '일치'
        print('[%s] %s ← %s' % (status, spec['overlay'], spec['source']))
        summary = {}
        for cat, text in rows:
            summary.setdefault(cat, []).append(text)
        for cat, items in summary.items():
            shown = items if verbose or len(items) <= 12 else items[:12] + ['… 외 %d개 (-v 로 전부)' % (len(items) - 12)]
            print('   %s %d: %s' % (cat, len(items), ' | '.join(shown)))
        for p in pend:
            print('   보류(다른 작업 묶음 담당, 실패로 세지 않음): ' + p)
        total += len(rows)
    drows = check_donation(cfg)
    if cfg.get('donation'):
        print('[%s] 기증 목록 %s ↔ %s' % ('차이 %d' % len(drows) if drows else '일치', cfg['donation']['catalog'], cfg['donation']['spells']))
        for cat, text in drows:
            print('   %s: %s' % (cat, text))
        total += len(drows)
    print('결과: %s' % ('차이 없음' if total == 0 else '차이 %d건 — 배포 전에 "python3 tools/operating_overlay.py regenerate" 후 다시 검사하세요' % total))
    return 0 if total == 0 else 1


def cmd_detect(cfg):
    for spec in cfg['files']:
        src = load_yaml(os.path.join(REPO, spec['source'])); op = load_yaml(os.path.join(REPO, spec['overlay']))
        keep = {tuple(p) for p in _paths(cfg, 'keep', spec['overlay'])}
        pend = {tuple(p) for p in _paths(cfg, 'pending', spec['overlay'])}
        rows, _ = file_drift(spec, src, op, [], [])
        print('== %s (%d)' % (spec['overlay'], len(rows)))
        for cat, text in rows:
            key = text.split(':')[0]
            entries = spec.get('entries')
            p = tuple((entries.split('.') if entries else []) + [key])
            flag = '  [유지: 의도된 운영 값]' if any(p[:len(k)] == k or k[:len(p)] == p for k in keep) else ('  [보류]' if any(p[:len(k)] == k for k in pend) else '')
            print('   %s: %s%s' % (cat, text, flag))
        for k in sorted(keep):
            if len(k) <= (len(spec['entries'].split('.')) if spec.get('entries') else 0):
                print('   유지(최상위): %s' % '.'.join(k))
    return 0


def cmd_regenerate(cfg):
    manifest = []
    if os.path.exists(MANIFEST):
        with open(MANIFEST, encoding='utf-8') as f:
            manifest = json.load(f)
    by_path = {m['path']: m for m in manifest}
    print('의도된 운영 값(유지):')
    for spec in cfg['files']:
        op_path = os.path.join(REPO, spec['overlay']); src_path = os.path.join(REPO, spec['source'])
        with open(src_path, encoding='utf-8') as f:
            src_text = f.read()
        with open(op_path, encoding='utf-8') as f:
            op_text = f.read()
        keep = _paths(cfg, 'keep', spec['overlay']); pend = _paths(cfg, 'pending', spec['overlay'])
        lines = src_text.split('\n'); donor = op_text.split('\n')
        for p in keep + pend:
            lines = transplant(lines, donor, p)
        for x in cfg.get('keep', []):
            if x['file'] == spec['overlay']:
                print('   %s :: %s — %s' % (spec['overlay'], '.'.join(map(str, x['path'])), x.get('reason', '')))
        for x in cfg.get('pending', []):
            if x['file'] == spec['overlay']:
                print('   %s :: %s — 보류(%s): %s' % (spec['overlay'], '.'.join(map(str, x['path'])), x.get('owner', ''), x.get('reason', '')))
        new_text = '\n'.join(lines)
        if not new_text.endswith('\n'):
            new_text += '\n'
        # 결과 검증: 원본 + 유지 값과 의미가 같아야 한다
        src_doc = load_yaml_text(src_text); op_doc = load_yaml_text(op_text); new_doc = load_yaml_text(new_text)
        expected = json.loads(json.dumps(src_doc))
        for p in keep + pend:
            set_path(expected, p, get_path(op_doc, p))
        if json.loads(json.dumps(new_doc)) != expected:
            raise SystemExit('재생성 검증 실패: ' + spec['overlay'])
        with open(op_path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(new_text)
        entry = by_path.get(spec['overlay'])
        if entry is None:
            entry = {'path': spec['overlay'], 'sha256': '', 'sameOnSchoolAndWild': True, 'role': 'non-secret gameplay configuration; deployment overlay, not player data'}
            manifest.append(entry); by_path[spec['overlay']] = entry
        entry['sha256'] = sha256(op_path)
    with open(MANIFEST, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(manifest, ensure_ascii=False, indent=2))
    print('재생성 완료. manifest sha256 갱신: ' + os.path.relpath(MANIFEST, REPO))
    return cmd_check(cfg)


def main(argv):
    if not argv or argv[0] in ('-h', '--help'):
        print(__doc__); return 0 if argv else 2
    cfg = load_config()
    if argv[0] == 'check':
        return cmd_check(cfg, verbose='-v' in argv)
    if argv[0] == 'detect':
        return cmd_detect(cfg)
    if argv[0] == 'regenerate':
        return cmd_regenerate(cfg)
    print(__doc__); return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
