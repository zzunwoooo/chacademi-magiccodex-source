"""무기 방향 해결기 — 팔이 어떻게 움직이든 무기(검·지팡이)가 '가리켜야 하는 방향'을 정확히 향하도록
무기 본의 회전 키를 계산해 넣는다. (팔 회전을 따라가기만 하면 머리 위로 든 검이 뒤로 눕는 등 어색해짐)

- 좌표: 모델 공간. 정면 = -z, 모델의 오른쪽 = +x, 위 = +y.
- 방향은 (phi, pitch) 도 단위: phi 0 = 정면, + = 오른쪽(+x)으로 돎, pitch + = 위. 예) 위 = (0, 86), 아래 = (0, -86), 오른쪽 수평 = (90, 0)
- 회전 합성은 Blockbench 와 같게: 유효 오일러 = 본 기본 회전 + 애니메이션 값, 행렬 = Rz·Ry·Rx.
"""
import math
import numpy as np

def rotm(r):
    x, y, z = [math.radians(a) for a in r]
    Rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    Ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    Rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def euler(R):
    sy = -R[2, 0]; sy = max(-1, min(1, sy))
    y = math.asin(sy)
    if abs(sy) > 0.9999:
        x = math.atan2(-R[1, 2], R[1, 1]); z = 0.0
    else:
        x = math.atan2(R[2, 1], R[2, 2]); z = math.atan2(R[1, 0], R[0, 0])
    return [math.degrees(x), math.degrees(y), math.degrees(z)]

def dirv(phi, pitch):
    p, q = math.radians(phi), math.radians(pitch)
    return np.array([math.sin(p) * math.cos(q), math.sin(q), -math.cos(p) * math.cos(q)])

def frame(d, n):
    """무기 축(Y) = d, 날의 넓은 면 법선(Z) ≈ n 인 회전 행렬."""
    d = d / np.linalg.norm(d); n = np.array(n, float)
    if abs(np.dot(n, d)) > 0.92: n = np.array([0, 1.0, 0]) if abs(d[1]) < 0.9 else np.array([0, 0, -1.0])
    z = n - np.dot(n, d) * d; z /= np.linalg.norm(z); x = np.cross(d, z)
    return np.column_stack([x, d, z])

def _sample(keys, t):
    """rotation 키 [(ch, t, x, y, z, interp)] 를 시각 t 에서 평가 (catmullrom / linear)."""
    ks = sorted([k for k in keys if k[0] == 'rotation'], key=lambda k: k[1])
    if not ks: return [0, 0, 0]
    if t <= ks[0][1]: return list(ks[0][2:5])
    if t >= ks[-1][1]: return list(ks[-1][2:5])
    for i in range(len(ks) - 1):
        a, b = ks[i], ks[i + 1]
        if a[1] <= t <= b[1]:
            f = (t - a[1]) / ((b[1] - a[1]) or 1)
            ia = a[5] if len(a) > 5 else 'linear'; ib = b[5] if len(b) > 5 else 'linear'
            if ia == 'catmullrom' or ib == 'catmullrom':
                p0 = ks[max(0, i - 1)]; p3 = ks[min(len(ks) - 1, i + 2)]; out = []
                for c in (2, 3, 4):
                    P0, P1, P2, P3 = p0[c], a[c], b[c], p3[c]; f2 = f * f; f3 = f2 * f
                    out.append(.5 * ((2 * P1) + (-P0 + P2) * f + (2 * P0 - 5 * P1 + 4 * P2 - P3) * f2 + (-P0 + 3 * P1 - 3 * P2 + P3) * f3))
                return out
            return [a[c] + (b[c] - a[c]) * f for c in (2, 3, 4)]
    return list(ks[-1][2:5])

def _groups(root):
    out = {}
    def walk(g):
        out[g.name] = g
        for c in g.children:
            if hasattr(c, 'children'): walk(c)
    walk(root); return out

def aim_rest(root, chain, weapon, phi, pitch, n=(0, 0, -1), axis=1):
    """기본 자세에서 무기가 (phi, pitch) 를 향하도록 무기 그룹의 기본 회전을 정한다."""
    G = _groups(root); P = np.eye(3)
    for b in chain: P = P @ rotm(G[b].rot)
    G[weapon].rot = [round(v, 3) for v in euler(P.T @ frame(axis * dirv(phi, pitch), n))]

def solve(root, tracks, length, chain, weapon, targets, axis=1, fps=24):
    """tracks[weapon] 의 rotation 키를 targets 에 맞춰 새로 쓴다.
    targets: [(t, phi, pitch[, n])] — 사이 구간은 각도를 선형 보간. n = 날의 넓은 면이 향할 쪽(생략 시 직전 값 유지, 처음엔 정면)."""
    G = _groups(root); base = G[weapon].rot
    tg = sorted(targets, key=lambda k: k[0]); nprev = np.array([0, 0, -1.0]); full = []
    for k in tg:
        if len(k) > 3 and k[3] is not None: nprev = np.array(k[3], float)
        full.append((k[0], k[1], k[2], nprev.copy()))
    def target(t):
        if t <= full[0][0]: a = b = full[0]; f = 0
        elif t >= full[-1][0]: a = b = full[-1]; f = 0
        else:
            for i in range(len(full) - 1):
                if full[i][0] <= t <= full[i + 1][0]: a, b = full[i], full[i + 1]; break
            f = (t - a[0]) / ((b[0] - a[0]) or 1); f = f * f * (3 - 2 * f)
        return dirv(a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f), a[3] + (b[3] - a[3]) * f
    times = sorted(set([round(i / fps, 4) for i in range(int(round(length * fps)) + 1)] + [round(length, 4)] + [round(k[0], 4) for k in tg if 0 <= k[0] <= length]))
    keys = []; prev = None
    for t in times:
        P = np.eye(3)
        for b in chain: P = P @ rotm([G[b].rot[i] + v for i, v in enumerate(_sample(tracks.get(b, []), t))])
        d, n = target(t); e = euler(P.T @ frame(axis * d, n)); off = [e[i] - base[i] for i in range(3)]
        if prev is not None: off = [o - 360 * round((o - p) / 360) for o, p in zip(off, prev)]
        prev = off; keys.append(('rotation', t, round(off[0], 2), round(off[1], 2), round(off[2], 2), 'linear'))
    tracks[weapon] = [k for k in tracks.get(weapon, []) if k[0] != 'rotation'] + keys

# ---------------------------------------------------------------- 손(손목) 해결기
# 무기는 손에 '고정'(주먹 안)되어 있고, 손목이 돌아서 무기 방향을 맞춘다 → 무기가 손에서 따로 놀지 않는다.
def rest_dir(root, chain, hand, weapon, axis=1):
    """기본 자세에서 무기가 향하는 (phi, pitch, n)."""
    G = _groups(root); P = np.eye(3)
    for b in chain + [hand, weapon]: P = P @ rotm(G[b].rot)
    d = axis * P[:, 1]; n = P[:, 2]
    return (math.degrees(math.atan2(d[0], -d[2])), math.degrees(math.asin(max(-1, min(1, d[1])))), tuple(n))

def solve_hand(root, tracks, length, chain, hand, weapon, targets=None, axis=1, fps=24):
    """tracks[hand] 의 rotation 키를 새로 쓴다. targets: [(t, phi, pitch[, n])], None = 기본 자세 방향 유지.
    phi/pitch 자리에 'rest' 를 쓰면 기본 방향."""
    G = _groups(root); base = G[hand].rot; Rw = rotm(G[weapon].rot)
    r0 = rest_dir(root, chain, hand, weapon, axis)
    tg = sorted(targets or [(0, 'rest')], key=lambda k: k[0]); nprev = np.array(r0[2], float); full = []
    for k in tg:
        if k[1] == 'rest': full.append((k[0], r0[0], r0[1], np.array(r0[2], float))); nprev = np.array(r0[2], float); continue
        if len(k) > 3 and k[3] is not None: nprev = np.array(k[3], float)
        full.append((k[0], k[1], k[2], nprev.copy()))
    def target(t):
        if t <= full[0][0]: a = b = full[0]; f = 0
        elif t >= full[-1][0]: a = b = full[-1]; f = 0
        else:
            for i in range(len(full) - 1):
                if full[i][0] <= t <= full[i + 1][0]: a, b = full[i], full[i + 1]; break
            f = (t - a[0]) / ((b[0] - a[0]) or 1); f = f * f * (3 - 2 * f)
        n = a[3] + (b[3] - a[3]) * f
        if np.linalg.norm(n) < 1e-3: n = a[3]
        return dirv(a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f), n
    times = sorted(set([round(i / fps, 4) for i in range(int(round(length * fps)) + 1)] + [round(length, 4)] + [round(k[0], 4) for k in tg if 0 <= k[0] <= length]))
    keys = []; prev = None; worst = 0
    for t in times:
        P = np.eye(3)
        for b in chain: P = P @ rotm([G[b].rot[i] + v for i, v in enumerate(_sample(tracks.get(b, []), t))])
        d, n = target(t); Rh = P.T @ frame(axis * d, n) @ Rw.T
        e = euler(Rh); off = [e[i] - base[i] for i in range(3)]
        if prev is not None: off = [o - 360 * round((o - p) / 360) for o, p in zip(off, prev)]
        prev = off; keys.append(('rotation', t, round(off[0], 2), round(off[1], 2), round(off[2], 2), 'linear'))
        worst = max(worst, math.degrees(math.acos(max(-1, min(1, (np.trace(Rh @ rotm(base).T) - 1) / 2)))))
    tracks[hand] = [k for k in tracks.get(hand, []) if k[0] != 'rotation'] + keys
    tracks[weapon] = [k for k in tracks.get(weapon, []) if k[0] == 'scale']
    if not tracks[weapon]: del tracks[weapon]
    return worst

def apply(G, spec, chain, hand, weapon, WT, axis=1):
    """spec 의 모든 애니메이션에 손목 해결기를 적용. 반환: {이름: 손목이 기본에서 가장 많이 꺾인 각도}."""
    root = [g for g in G if g.name == 'root'][0]
    return {name: round(solve_hand(root, tr, ln, chain, hand, weapon, WT.get(name), axis)) for name, ln, lp, tr in spec}
