// ===== 100년 전 공성전 레이드 — 짤패턴 VFX 공용 빌더 (lib.js 뒤에 붙임) =====
// 모든 시스템은 static: 원점 = 서버가 찍는 지점(발밑), +Z = 서버가 넘기는 수평 방향. 1블록 = 1 m.
// 빨간 예고 장판은 서버 예고 시스템이 그린다 → 범위 채움·외곽선은 그리지 않고, 시전 표시(작은 마법진·모이는 빛)와 타격 연출만 만든다.
// 시간 규약: t=0 예고 시작, t=T 타격.
const FWD = -Math.PI / 2;
const SM = { pilT: pillarM(0.35, 1), pilW: pillarM(1, 1), cyl: cylPanels(24, 3, 0.16), spear: pillarM(0.25, 1), chainM: pillarM(0.22, 1) };
const alt = (i, sf, s2) => KIT.keepSide ? sf : (i % 2 ? s2 : sf);   // 벨자르는 흰 쪽/검은 쪽 연출이 섞이면 안 됨
let KIT = null;   // 보스별 연출 묶음 (아래 KITS). spec 맨 위에서 KIT = KITS.<보스> 로 고른다.
const clampN = (v, a, b) => Math.max(a, Math.min(b, v));
function mkMats(themes, extra) {
  const base = { line: ['T_Line', 2.4], rune: ['T_Rune', 2.2], sigil: ['T_Sigil', 2.2], glow: ['T_Glow', 1.3], star: ['T_Star', 2.7], flare: ['T_Flare', 2.2], wave: ['T_Wave', 1.8],
    streak: ['T_Streak', 2.5], dot: ['T_Dot', 2.5], pillar: ['T_Pillar', 2.1], band: ['T_Band', 1.8], crack: ['T_Crack', 2.2], shardL: ['T_ShardLight', 2.4], slash: ['T_Slash_Sheet', 2.4], dust: ['T_Dust_Sheet', 1.3] };
  const out = { mist: { tex: 'T_DarkMist_Sheet', tint: [1, 1, 1], add: false } };
  for (const [suf, c] of Object.entries(themes)) {
    for (const n in base) out[n + suf] = { tex: base[n][0], tint: c.map(v => +(v * base[n][1]).toFixed(3)), add: true };
    out['shardD' + suf] = { tex: 'T_ShardDark', tint: c.map(v => +(v * 2.3).toFixed(3)), add: false };
  }
  return Object.assign(out, extra || {});
}
const MCs = s => ({ line: 'line' + s, rune: 'rune' + s, sigil: 'sigil' + s, glow: 'glow' + s });
const MBs = (s, dark) => Object.assign({ star: 'star' + s, flare: 'flare' + s, glow: 'glow' + s, wave: 'wave' + s, streak: 'streak' + s, dot: 'dot' + s, shardL: 'shardL' + s, ring: 'line' + s, motes: 'dot' + s },
  dark ? { shardD: 'shardD' + s, mist: 'mist' } : {});
function miniBurst(tag, p, t, s, sf, col) {
  return [
    { name: `${tag} Flash`, mat: 'star' + sf, fudge: -8, pos: [p[0], p[1] + 0.5 * s, p[2]], size: 2.8 * s, rot: [0, TAU], bursts: [[t, 1]], life: 0.2, sizeLife: [[0, 0.3], [0.25, 1.1], [1, 0]] },
    { name: `${tag} Flare`, mat: 'flare' + sf, fudge: -7, pos: [p[0], p[1] + 0.5 * s, p[2]], size: 3.4 * s, rot: [0, TAU], rotZ: [-0.6, 0.6], bursts: [[t, 1]], life: 0.38, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.2]], alpha: [[0, 0.8], [1, 0]] },
    { name: `${tag} Sparks`, mat: 'dot' + sf, mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [p[0], p[1] + 0.3, p[2]], life: [0.4, 0.8], size: [0.05 * s, 0.1 * s], bursts: [[t, 14]],
      shape: { type: 'sphere', r: 0.3 * s, th: 1 }, speed: [3 * s, 8 * s], drag: 2.2, gravity: 0.6, color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 1], [0.7, 1], [1, 0]] }].concat(KIT.column(tag + 'k', p, t, s, sf, col));
}
// 시전 표시: 원점의 작은 마법진 + 모여드는 빛 (0 → T)
function castMark(tag, R, T, sf, col) {
  return KIT.mark(tag + 'm', R, T, sf, col).concat([
    { name: `${tag} Gather`, mat: 'dot' + sf, fudge: -6, pos: [0, 0.3, 0], life: [T * 0.5, T * 0.9], size: [0.05, 0.1], shape: { type: 'circle', r: R * 1.6, th: 0.2, y: 0.2 },
      bursts: [[0, Math.round(10 + R * 4)], [T * 0.4, Math.round(8 + R * 3)]], vel: { rad: [-R * 1.6 / T * 0.9, -R * 1.6 / T * 0.6], y: [0.5, 2] }, color: [[0, col.mid], [1, col.hot]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
    { name: `${tag} Charge`, mat: 'glow' + sf, fudge: 2, pos: [0, 1.0, 0], size: R * 2.2, bursts: [[0, 1]], life: T, sizeLife: [[0, 0.3], [1, 1]], alpha: [[0, 0], [0.8, 0.3], [1, 0]] }]);
}
// ---------- 원형 폭발 (반경 R) ----------
function blastCircle(tag, R, T, sf, col, o) {
  o = o || {}; const s2 = o.s2 || sf, sc = Math.max(0.8, R / 2.6), L = [];
  L.push(...KIT.mark(tag + 'c', R, T, sf, col));
  L.push(...impactBurst(tag + 'b', [0, 0, 0], T, sc, MBs(s2, o.dark ?? true), col, { n: clampN(R / 3.5, 0.6, 2.2) }));
  L.push(...KIT.column(tag + 'k', [0, 0, 0], T, sc * 1.5, KIT.keepSide ? sf : s2, col));
  L.push({ name: `${tag} Crack`, mat: 'crack' + s2, mode: 'mesh', mesh: 'quad', fudge: 33, pos: [0, 0.04, 0], size: R * 2.1, rot3: YAW, bursts: [[T, 1]], life: 1.4, alpha: [[0, 1], [0.4, 0.7], [1, 0]] });
  L.push({ name: `${tag} Gather`, mat: 'dot' + sf, fudge: -6, life: [T * 0.5, T * 0.9], size: [0.05, 0.1], shape: { type: 'circle', r: R, th: 0.2, y: 0.2 }, bursts: [[0, Math.round(10 + R * 3)], [T * 0.4, Math.round(8 + R * 2)]],
    vel: { rad: [-R / T * 0.9, -R / T * 0.6], y: [0.5, 2] }, color: [[0, col.mid], [1, col.hot]], alpha: [[0, 0], [0.2, 1], [1, 0]] });
  L.push({ name: `${tag} RimFlares`, mat: 'flare' + sf, fudge: -6, life: 0.35, size: [1.2 * sc, 1.8 * sc], rot: [0, TAU], shape: { type: 'circle', r: R * 0.85, th: 0, y: 0.5 }, bursts: [[T + 0.06, Math.round(clampN(R * 1.6, 5, 14))]], sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.75], [1, 0]] });
  return L.concat(o.extra || []);
}
// ---------- 고리 폭발 (r0 → r1 로 퍼지는 벽, 도넛 범위) ----------
function blastRing(tag, r0, r1, T, sf, col, o) {
  o = o || {}; const rm = (r0 + r1) / 2, a0 = Math.max(0.4, r0), th = (r1 - r0) / r1, n = clampN(rm / 4, 1, 4), L = [];
  const pre = [[0, 0], [0.5, 0.55], [0.92, 1], [1, 0]];
  if (KIT.lineArt) L.push({ name: `${tag} PreRune`, mat: 'rune' + sf, mode: 'mesh', mesh: 'cRune', fudge: 36, size: rm / 0.79, rot3: YAW, rotLife: [0, 0.5, 0], bursts: [[0, 1]], life: T + 0.25, sizeLife: [[0, 0.92], [0.3, 1]], alpha: pre });
  if (KIT.lineArt) L.push({ name: `${tag} PreTicks`, mat: 'line' + sf, mode: 'mesh', mesh: 'cTicks', fudge: 38, size: rm, rot3: YAW, rotLife: [0, -0.4, 0], bursts: [[0.03, 1]], life: T + 0.2, sizeLife: [[0, 0.92], [0.3, 1]], alpha: pre });
  L.push({ name: `${tag} Gather`, mat: 'dot' + sf, fudge: -6, life: [T * 0.5, T * 0.9], size: [0.05, 0.1], shape: { type: 'circle', r: r1, th: th, y: 0.2 }, bursts: [[0, Math.round(16 * n)], [T * 0.4, Math.round(12 * n)]], vel: { y: [1.5, 4] }, color: [[0, col.mid], [1, col.hot]], alpha: [[0, 0], [0.2, 1], [1, 0]] });
  L.push({ name: `${tag} Wall`, mat: 'band' + sf, mode: 'mesh', mesh: 'cyl', fudge: 6, size: 1, rot3: YAW, rotLife: [0, 0.8, 0], bursts: [[T, 1]], life: 0.45, sizeLife: [[0, a0, 0, (r1 - a0) * 2.4], [1, r1]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] });
  L.push({ name: `${tag} Wall2`, mat: 'pillar' + (o.s2 || sf), mode: 'mesh', mesh: 'cyl', fudge: 7, size: 1, rot3: YAW, rotLife: [0, -0.6, 0], bursts: [[T + 0.06, 1]], life: 0.45, sizeLife: [[0, a0, 0, (r1 - a0) * 2.4], [1, r1 * 0.96]], alpha: [[0, 0.7], [1, 0]] });
  L.push({ name: `${tag} Floor`, mat: 'line' + sf, mode: 'mesh', mesh: 'cOuter', fudge: 30, size: 1, rot3: YAW, bursts: [[T, 1]], life: 0.5, sizeLife: [[0, a0, 0, (r1 - a0) * 2.4], [1, r1]], alpha: [[0, 1], [0.7, 0.8], [1, 0]] });
  L.push({ name: `${tag} Wave`, mat: 'wave' + sf, mode: 'mesh', mesh: 'quad', fudge: 31, pos: [0, 0.06, 0], size: r1 * 2.5, rot3: YAW, bursts: [[T, 1]], life: 0.5, sizeLife: [[0, a0 / r1, 0, 2.2], [1, 1]], alpha: [[0, 0.38], [0.6, 0.25], [1, 0]] });
  L.push({ name: `${tag} UpRays`, mat: 'streak' + sf, mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, life: [0.4, 0.6], size: [0.08, 0.16], shape: { type: 'circle', r: r1, th: th, y: 0.1 }, bursts: [[T + 0.05, Math.round(26 * n)], [T + 0.16, Math.round(18 * n)]], vel: { y: [8, 15] }, drag: 2.5, alpha: [[0, 1], [1, 0]] });
  L.push({ name: `${tag} Flares`, mat: 'flare' + (o.s2 || sf), fudge: -6, life: 0.35, size: [1.6, 2.6], rot: [0, TAU], shape: { type: 'circle', r: rm, th: 0, y: 0.6 }, bursts: [[T + 0.08, Math.round(7 * n)]], sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.8], [1, 0]] });
  L.push({ name: `${tag} Sparks`, mat: 'dot' + sf, mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, life: [0.5, 0.9], size: [0.06, 0.12], shape: { type: 'circle', r: r1, th: th, y: 0.3 }, bursts: [[T + 0.05, Math.round(40 * n)]], vel: { rad: [2, 6], y: [2, 7] }, drag: 2, gravity: 0.6,
    color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 1], [0.7, 1], [1, 0]] });
  L.push({ name: `${tag} DarkShards`, mat: 'shardD' + (o.s2 || sf), fudge: 20, life: [0.5, 0.8], size: [0.2, 0.36], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'circle', r: r1, th: th, y: 0.4 }, bursts: [[T + 0.05, Math.round(9 * n)]], vel: { rad: [1, 4], y: [3, 7] }, drag: 1.2, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] });
  L.push({ name: `${tag} Mist`, mat: 'mist', fudge: 25, life: [0.9, 1.3], size: [1.6, 2.6], rot: [0, TAU], shape: { type: 'circle', r: r1, th: th, y: 0.5 }, bursts: [[T + 0.08, Math.round(7 * n)]], vel: { rad: [0.5, 1.5], y: [0.3, 0.7], space: 'world' }, drag: 1.5,
    sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 } });
  return L.concat(KIT.ringfx(tag + 'k', r0, r1, T, sf, col), o.extra || []);
}
// ---------- 직선 폭발 (원점에서 +Z 로 폭 w, 길이 len) ----------
function blastLane(tag, w, len, T, sf, col, o) {
  o = o || {}; const s2 = o.s2 || sf, n = clampN(len * w / 40, 0.6, 3), L = [], H = o.h ?? clampN(w * 1.1, 2.5, 7);
  SM[tag + 'Crack'] = strip([0, 0], [0, len], w * 0.95, 0.05);
  L.push(...castMark(tag, clampN(w * 0.45, 0.9, 2.6), T, sf, col));
  L.push({ name: `${tag} Crack`, mat: 'crack' + s2, mode: 'mesh', mesh: tag + 'Crack', fudge: 33, size: 1, rot3: [0, 0, 0], bursts: [[T, 1]], life: 1.2, alpha: [[0, 1], [0.4, 0.7], [1, 0]] });
  L.push({ name: `${tag} Band`, mat: 'band' + sf, mode: 'mesh', mesh: tag + 'Crack', fudge: 32, pos: [0, 0.03, 0], size: 1, rot3: [0, 0, 0], bursts: [[T, 1]], life: 0.3, alpha: [[0, 1], [1, 0]] });
  L.push({ name: `${tag} Beam`, mat: 'streak' + sf, mode: 'stretch', vs: 0.05, ls: 3.5, fudge: -3, pos: [0, 1.1, 0.5], life: [0.22, 0.3], size: [0.14, 0.3], shape: { type: 'box', sx: w * 0.8, sy: 1.6, sz: 0.5 }, bursts: [[T, Math.round(26 * n)], [T + 0.06, Math.round(16 * n)]],
    vel: { z: [len / 0.26 * 0.8, len / 0.26 * 1.15] }, drag: 0.4, alpha: [[0, 1], [0.8, 1], [1, 0]] });
  L.push({ name: `${tag} UpRays`, mat: 'streak' + s2, mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, pos: [0, 0.1, len / 2], life: [0.4, 0.6], size: [0.07, 0.15], shape: { type: 'box', sx: w, sy: 0.1, sz: len }, bursts: [[T + 0.05, Math.round(34 * n)], [T + 0.15, Math.round(20 * n)]], vel: { y: [7, 14] }, drag: 2.5, alpha: [[0, 1], [1, 0]] });
  L.push({ name: `${tag} Sparks`, mat: 'dot' + sf, mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [0, 0.3, len / 2], life: [0.5, 0.9], size: [0.06, 0.12], shape: { type: 'box', sx: w, sy: 0.3, sz: len }, bursts: [[T + 0.04, Math.round(50 * n)]], vel: { x: [-4, 4], y: [2, 8], z: [-2, 6] }, drag: 2, gravity: 0.6,
    color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 1], [0.7, 1], [1, 0]] });
  L.push({ name: `${tag} DarkShards`, mat: 'shardD' + s2, fudge: 20, pos: [0, 0.4, len / 2], life: [0.5, 0.8], size: [0.2, 0.36], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'box', sx: w, sy: 0.3, sz: len }, bursts: [[T + 0.04, Math.round(12 * n)]], vel: { x: [-3, 3], y: [3, 8], z: [0, 5] }, drag: 1.2, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] });
  L.push({ name: `${tag} Mist`, mat: 'mist', fudge: 25, pos: [0, 0.5, len / 2], life: [0.9, 1.3], size: [1.4, 2.4], rot: [0, TAU], shape: { type: 'box', sx: w, sy: 0.3, sz: len }, bursts: [[T + 0.07, Math.round(8 * n)]], vel: { y: [0.3, 0.8], space: 'world' }, drag: 1.5,
    sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 } });
  const step = Math.max(3.5, w * 1.3), k = Math.max(1, Math.round(len / step));
  for (let i = 0; i < k; i++) L.push(...miniBurst(`${tag}p${i}`, [0, 0, (i + 0.6) * len / k], T + 0.02 * i, clampN(w / 3.2, 0.6, 1.8), alt(i, sf, s2), col));
  L.push(...impactBurst(tag + 'e', [0, 0, len - Math.min(2, w / 2)], T + 0.02 * k, clampN(w / 3, 0.7, 2), MBs(s2, true), col, { n: 0.7 }));
  return L.concat(KIT.stream(tag + 's', w, len, T, sf, col), o.extra || []);
}
// ---------- 부채꼴 베기 (원점에서 +Z 중심, span 라디안, 반경 R) ----------
function blastFan(tag, span, R, T, sf, col, o) {
  o = o || {}; const s2 = o.s2 || sf, n = clampN(span * R * R / 120, 0.6, 3.2), L = [], tz = Math.tan(Math.min(span, 2.6) / 2);
  SM[tag + 'Cres'] = crescentXZ(Math.min(span * 0.95, TAU * 0.6), 0.07); SM[tag + 'CresT'] = crescentXZ(Math.min(span * 0.95, TAU * 0.6), 0.03);
  SM[tag + 'Arc'] = merge([ringM(1, 0.035, 0.05, 48, span, Math.PI / 2 + span / 2), ringM(0.96, 0.012, 0.05, 48, span * 0.9, Math.PI / 2 + span * 0.45)]);
  L.push(...castMark(tag, clampN(R * 0.16, 0.9, 2.4), T, sf, col));
  for (let i = 0; i < 5; i++) L.push({ name: `${tag} Slash${i}`, mat: KIT.slash(i % 2 ? s2 : sf), mode: 'mesh', mesh: tag + (i % 2 ? 'CresT' : 'Cres'), fudge: 4 + i, pos: [0, 0.4 + i * (o.rise ?? 0.6), 0], size: 1, rot3: [0, FWD, 0],
    bursts: [[T + 0.03 * i, 1]], life: 0.5, sizeLife: [[0, R * 0.2, 0, R * 2], [1, R * (1 - i * 0.05)]], flip: { x: 2, y: 8 } });
  if (KIT.arcs) [0.45, 0.75, 1].forEach((f, i) => L.push({ name: `${tag} Arc${i}`, mat: 'line' + (i == 1 ? s2 : sf), mode: 'mesh', mesh: tag + 'Arc', fudge: 30 + i, size: 1, rot3: [0, 0, 0], bursts: [[T + 0.05 * i, 1]], life: 0.5,
    sizeLife: [[0, R * f * 0.8, 0, 2], [1, R * f * 1.04]], alpha: [[0, 1], [0.6, 0.7], [1, 0]] }));
  L.push({ name: `${tag} Streaks`, mat: 'streak' + sf, mode: 'stretch', vs: 0.06, ls: 2.6, fudge: -3, pos: [0, 1, 0.6], life: [0.3, 0.45], size: [0.1, 0.2], shape: { type: 'box', sx: 1.5, sy: 2, sz: 0.5 }, bursts: [[T, Math.round(30 * n)], [T + 0.08, Math.round(20 * n)]],
    vel: { z: [R * 2.2, R * 3.2], x: [-R * 2.4 * Math.min(tz, 2.2), R * 2.4 * Math.min(tz, 2.2)], y: [-1, 2] }, drag: 1.4, alpha: [[0, 1], [1, 0]] });
  L.push({ name: `${tag} Sparks`, mat: 'dot' + s2, mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [0, 0.6, 1], life: [0.5, 0.9], size: [0.06, 0.12], shape: { type: 'box', sx: 2, sy: 1, sz: 1 }, bursts: [[T + 0.02, Math.round(46 * n)]],
    vel: { z: [R * 0.8, R * 2.2], x: [-R * 1.6 * Math.min(tz, 2.2), R * 1.6 * Math.min(tz, 2.2)], y: [1, 6] }, drag: 2.4, gravity: 0.6, color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 1], [0.7, 1], [1, 0]] });
  L.push({ name: `${tag} DarkShards`, mat: 'shardD' + s2, fudge: 20, pos: [0, 0.6, 1], life: [0.5, 0.8], size: [0.2, 0.36], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'box', sx: 2, sy: 1, sz: 1 }, bursts: [[T + 0.03, Math.round(10 * n)]],
    vel: { z: [R * 0.6, R * 1.6], x: [-R * 1.2 * Math.min(tz, 2.2), R * 1.2 * Math.min(tz, 2.2)], y: [2, 6] }, drag: 1.4, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] });
  const m = span > 2.4 ? 5 : 3;
  for (let i = 0; i < m; i++) { const a = -span * 0.38 + span * 0.76 * i / (m - 1), rr = R * 0.62; L.push(...miniBurst(`${tag}p${i}`, [Math.sin(a) * rr, 0, Math.cos(a) * rr], T + 0.08 + 0.02 * i, clampN(R / 7, 0.7, 1.9), alt(i + 1, sf, s2), col)); }
  L.push({ name: `${tag} Mist`, mat: 'mist', fudge: 25, pos: [0, 0.5, R * 0.5], life: [0.9, 1.3], size: [1.4, 2.4], rot: [0, TAU], shape: { type: 'box', sx: R * Math.min(1.6, tz), sy: 0.3, sz: R * 0.8 }, bursts: [[T + 0.1, Math.round(6 * n)]], vel: { z: [1, 3], y: [0.3, 0.8], space: 'world' }, drag: 1.5,
    sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 } });
  return L.concat(KIT.sweep(tag + 's', span, R, T, sf, col), o.extra || []);
}
// ---------- 사각 구역 폭발 (원점 중심, x 폭 w, z 길이 d) ----------
function blastRect(tag, w, d, T, sf, col, o) {
  o = o || {}; const s2 = o.s2 || sf, A = w * d, n = clampN(A / 150, 0.8, 5), L = [], Rm = Math.min(w, d) / 2 * 0.8;
  SM[tag + 'Floor'] = strip([0, -d / 2], [0, d / 2], w * 0.98, 0.05);
  L.push(...KIT.mark(tag + 'c', clampN(Rm, 2, 7), T, sf, col));
  L.push({ name: `${tag} Gather`, mat: 'dot' + sf, fudge: -6, pos: [0, 0.2, 0], life: [T * 0.5, T * 0.9], size: [0.05, 0.11], shape: { type: 'box', sx: w, sy: 0.2, sz: d }, bursts: [[0, Math.round(24 * n)], [T * 0.4, Math.round(18 * n)]], vel: { y: [1.5, 4.5] }, color: [[0, col.mid], [1, col.hot]], alpha: [[0, 0], [0.2, 1], [1, 0]] });
  L.push({ name: `${tag} Band`, mat: 'band' + sf, mode: 'mesh', mesh: tag + 'Floor', fudge: 32, size: 1, rot3: [0, 0, 0], bursts: [[T, 1]], life: 0.25, alpha: [[0, 0.45], [1, 0]] });
  L.push({ name: `${tag} Crack`, mat: 'crack' + s2, mode: 'mesh', mesh: tag + 'Floor', fudge: 33, pos: [0, 0.02, 0], size: 1, rot3: [0, 0, 0], bursts: [[T, 1]], life: 1.3, alpha: [[0, 1], [0.4, 0.6], [1, 0]] });
  L.push({ name: `${tag} Flares`, mat: 'flare' + s2, fudge: -6, pos: [0, 0.7, 0], life: 0.38, size: [2, 3.4], rot: [0, TAU], shape: { type: 'box', sx: w * 0.9, sy: 0.2, sz: d * 0.9 }, bursts: [[T, Math.round(7 * n)], [T + 0.1, Math.round(5 * n)]], sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.8], [1, 0]] });
  L.push({ name: `${tag} Stars`, mat: 'star' + sf, fudge: -8, pos: [0, 0.8, 0], life: 0.22, size: [1.6, 2.6], rot: [0, TAU], shape: { type: 'box', sx: w * 0.9, sy: 0.2, sz: d * 0.9 }, bursts: [[T, Math.round(6 * n)]], sizeLife: [[0, 0.3], [0.25, 1.1], [1, 0]] });
  L.push({ name: `${tag} UpRays`, mat: 'streak' + sf, mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, pos: [0, 0.1, 0], life: [0.4, 0.65], size: [0.08, 0.17], shape: { type: 'box', sx: w, sy: 0.1, sz: d }, bursts: [[T + 0.04, Math.round(40 * n)], [T + 0.15, Math.round(26 * n)]], vel: { y: [8, 16] }, drag: 2.5, alpha: [[0, 1], [1, 0]] });
  L.push({ name: `${tag} Sparks`, mat: 'dot' + s2, mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [0, 0.3, 0], life: [0.5, 1.0], size: [0.06, 0.13], shape: { type: 'box', sx: w, sy: 0.3, sz: d }, bursts: [[T + 0.04, Math.round(60 * n)]], vel: { x: [-4, 4], y: [3, 9], z: [-4, 4] }, drag: 2, gravity: 0.6,
    color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 1], [0.7, 1], [1, 0]] });
  L.push({ name: `${tag} DarkShards`, mat: 'shardD' + s2, fudge: 20, pos: [0, 0.4, 0], life: [0.5, 0.9], size: [0.22, 0.4], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'box', sx: w, sy: 0.3, sz: d }, bursts: [[T + 0.04, Math.round(14 * n)]], vel: { x: [-3, 3], y: [4, 9], z: [-3, 3] }, drag: 1.2, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] });
  L.push({ name: `${tag} Mist`, mat: 'mist', fudge: 25, pos: [0, 0.5, 0], life: [1.0, 1.5], size: [1.8, 3], rot: [0, TAU], shape: { type: 'box', sx: w, sy: 0.3, sz: d }, bursts: [[T + 0.08, Math.round(9 * n)]], vel: { y: [0.3, 0.8], space: 'world' }, drag: 1.5,
    sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 } });
  L.push({ name: `${tag} Motes`, mat: 'dot' + sf, fudge: -6, pos: [0, 0.3, 0], life: [1.0, 1.8], size: [0.05, 0.09], shape: { type: 'box', sx: w, sy: 0.3, sz: d }, bursts: [[T + 0.1, Math.round(30 * n)]], vel: { y: [1, 2.5] }, noise: { s: 0.8, f: 1, sp: 0.6 },
    color: [[0, col.hot], [0.6, col.mid], [1, col.low]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] });
  const gx = Math.max(1, Math.round(w / 9)), gz = Math.max(1, Math.round(d / 9));
  for (let i = 0; i < gx; i++) for (let j = 0; j < gz; j++) L.push(...KIT.column(`${tag}k${i}_${j}`, [(i + 0.5) / gx * w - w / 2, 0, (j + 0.5) / gz * d - d / 2], T + 0.03 * ((i + j) % 4), 1.6, alt(i + j, sf, s2), col));
  return L.concat(KIT.field(tag + 'f', w, d, T, sf, col), o.extra || []);
}
// ---------- 광폭화 / 등장 강조 (보스 발밑, 키 h) ----------
function enrageFx(tag, R, h, sf, s2, col) {
  const T = 0.6, L = KIT.mark(tag + 'c', R, T, sf, col);
  L.push(...impactBurst(tag + 'b', [0, h * 0.35, 0], T, R / 2.4, MBs(s2, true), col, { n: 2, ground: false }));
  L.push(...impactBurst(tag + 'g', [0, 0, 0], T + 0.05, R / 2.8, MBs(sf, true), col, { n: 1.4 }));
  L.push(...KIT.column(tag + 'k', [0, 0, 0], T, h * 0.42, s2, col), ...KIT.column(tag + 'k2', [0, h * 0.4, 0], T + 0.12, h * 0.3, sf, col));
  [0, 0.14, 0.28].forEach((dt, i) => L.push({ name: `${tag} Shock${i}`, mat: 'line' + (i % 2 ? s2 : sf), mode: 'mesh', mesh: 'cOuter', fudge: 29, size: 1, rot3: YAW, bursts: [[T + dt, 1]], life: 0.7, sizeLife: [[0, R * 0.4, 0, R * 6], [1, R * 3]], alpha: [[0, 1], [0.6, 0.7], [1, 0]] }));
  L.push({ name: `${tag} Aura`, mat: 'streak' + s2, mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, life: [0.6, 1.0], size: [0.1, 0.2], shape: { type: 'circle', r: R * 0.8, th: 0.6, y: 0.2 }, rate: 60, bursts: [[0, 20]], vel: { y: [6, 12] }, drag: 1.5, alpha: [[0, 0], [0.2, 1], [1, 0]] });
  return L;
}
// ---------- 데모 도우미 ----------
const HID = (x, z, yaw) => ({ path: [[0, [x, 0, z], yaw || 0]], hidden: true });
const cam = (t, d, yaw, pitch) => ({ target: t, dist: d, yaw, pitch });
const scn = (label, actors, events, camera) => ({ label, path: [[0, [0, 0, 0], 0]], actors, events, camera });
// orientation 'facing' 은 미리보기에서 방향을 보여 주기 위한 것. 클라이언트용 묶음에서는 client_compat fix 가 빼며, 실제 방향은 서버가 넘기는 수평 방향 기준이다.
const sys = (id, layers, T) => ({ id, role: 'static', orientation: 'facing', loop: false, duration: (T ?? 1) + 0.5, layers });

// =====================================================================================================
// 보스별 연출 묶음 (KITS). 같은 범위 틀이라도 바닥 문양·타격 연출·휘두르는 것·흩날리는 것이 보스마다 다르다.
//   mark   = 바닥 표식 (0 → T 준비, T 에 번쩍)        column = 타격 지점에서 솟는 것
//   stream = 직선 범위를 따라 흐르는 것                 sweep  = 부채꼴을 휩쓰는 것
//   field  = 넓은 구역 전체에 깔리는 것                 ringfx = 고리 범위를 따라 도는 것
// =====================================================================================================
const gq = (name, mat, size, t0, life, o) => Object.assign({ name, mat, mode: 'mesh', mesh: 'quad', fudge: 34, pos: [0, 0.05, 0], size, rot3: [0, 0, 0], bursts: [[t0, 1]], life }, o || {});
const pop = [[0, 0.2, 0, 6], [0.2, 1.08], [0.4, 1], [1, 1]];
const KITS = {};

// ---------- 몽마 솜니아: 감긴 눈이 뜨인다 · 달 · 깃털 · 방울 ----------
KITS.somnia = {
  lineArt: true, arcs: true, slash: sf => 'slash' + sf,
  mark(tag, R, T, sf, col) { return [
    gq(`${tag} EyeClosed`, 'eyeC', R * 2.15, 0, T + 0.02, { sizeLife: [[0, 0.86], [1, 1]], alpha: [[0, 0], [0.3, 0.9], [0.92, 1], [1, 0]] }),
    gq(`${tag} EyeOpen`, 'eyeO', R * 2.2, T, 0.95, { fudge: 33, sizeLife: [[0, 0.9, 0, 2], [0.15, 1.08], [1, 1]], alpha: [[0, 1], [0.5, 0.85], [1, 0]] }),
    gq(`${tag} EyeFlash`, 'eyeO', R * 2.2, T, 0.3, { fudge: 32, pos: [0, 0.07, 0], sizeLife: [[0, 1], [1, 1.25]], alpha: [[0, 0.9], [1, 0]] }),
    { name: `${tag} Twinkle`, mat: 'star' + sf, fudge: -8, life: [0.35, 0.7], size: [0.25, 0.5], rot: [0, TAU], shape: { type: 'circle', r: R, th: 0.1, y: 0.3 }, bursts: [[0, Math.round(4 + R)], [T * 0.5, Math.round(4 + R)], [T, Math.round(6 + R * 1.5)]], sizeLife: [[0, 0.2], [0.4, 1], [1, 0]] },
    { name: `${tag} Bubbles`, mat: 'bubble', fudge: -3, life: [T * 0.7, T * 1.1], size: [0.18, 0.4], shape: { type: 'circle', r: R * 0.9, th: 1, y: 0.1 }, bursts: [[0, Math.round(5 + R * 2)], [T * 0.4, Math.round(4 + R)]], vel: { y: [1, 2.6] }, noise: { s: 0.5, f: 1, sp: 0.5 }, sizeLife: [[0, 0.4], [0.8, 1], [1, 1.5]], alpha: [[0, 0], [0.2, 0.9], [0.9, 0.8], [1, 0]] }]; },
  column(tag, p, t, s, sf, col) { return [
    { name: `${tag} Moon`, mat: 'moon', fudge: -9, pos: [p[0], p[1] + 1.7 * s, p[2]], size: 2.3 * s, rot: [-0.5, 0.5], rotZ: [-1, 1], bursts: [[t, 1]], life: 0.6, vel: { y: [1.5 * s, 1.5 * s] }, sizeLife: [[0, 0.3, 0, 5], [0.25, 1.1], [1, 0.9]], alpha: [[0, 1], [0.6, 0.9], [1, 0]] },
    { name: `${tag} Swirl`, mat: 'spiral', fudge: -2, pos: [p[0], p[1] + 0.9 * s, p[2]], size: 3.2 * s, rot: [0, TAU], rotZ: [5, 5], bursts: [[t, 1]], life: 0.6, sizeLife: [[0, 0.4], [1, 1.2]], alpha: [[0, 0.8], [1, 0]] },
    { name: `${tag} Feathers`, mat: 'feather', fudge: -1, pos: [p[0], p[1] + 0.8 * s, p[2]], life: [0.9, 1.5], size: [0.24, 0.4], rot: [0, TAU], rotZ: [-3, 3], shape: { type: 'sphere', r: 0.4 * s, th: 1 }, bursts: [[t, Math.round(7 * s)]], speed: [2 * s, 6 * s], drag: 3, gravity: 0.12,
      noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.08, 1], [0.7, 0.85], [1, 0]] }]; },
  stream(tag, w, len, T, sf, col) { return [
    { name: `${tag} MoonWave`, mat: 'moon', fudge: -8, pos: [0, 1.4, 0.5], size: clampN(w * 0.9, 1.2, 4), rotZ: [8, 8], bursts: [[T, 1], [T + 0.06, 1], [T + 0.12, 1]], life: 0.36, vel: { z: [len / 0.36, len / 0.36] }, alpha: [[0, 1], [0.85, 1], [1, 0]] },
    { name: `${tag} FeatherTrail`, mat: 'feather', fudge: -1, pos: [0, 1, len / 2], life: [0.9, 1.4], size: [0.24, 0.4], rot: [0, TAU], rotZ: [-3, 3], shape: { type: 'box', sx: w * 0.8, sy: 1, sz: len }, bursts: [[T + 0.1, Math.round(len * 0.9)]], vel: { y: [0.4, 1.4], z: [1, 3] }, drag: 2, gravity: 0.1,
      noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.1, 1], [0.7, 0.85], [1, 0]] }]; },
  sweep(tag, span, R, T, sf, col) { const tz = Math.min(2.2, Math.tan(Math.min(span, 2.6) / 2)); return [
    { name: `${tag} Moons`, mat: 'moon', fudge: -8, pos: [0, 1.6, 0.6], size: [1.2, 2.2], rot: [0, TAU], rotZ: [-8, 8], shape: { type: 'box', sx: 1, sy: 1.5, sz: 0.3 }, bursts: [[T, 5], [T + 0.08, 4]], life: [0.4, 0.5], vel: { z: [R * 1.6, R * 2.3], x: [-R * 1.6 * tz, R * 1.6 * tz] }, drag: 1, alpha: [[0, 1], [0.8, 1], [1, 0]] }]; },
  field(tag, w, d, T, sf, col) { const n = clampN(w * d / 150, 0.8, 5); return [
    { name: `${tag} Eyes`, mat: 'eyeO', fudge: -7, pos: [0, 2.4, 0], life: [0.7, 1.0], size: [1.6, 2.6], rot: [-0.2, 0.2], shape: { type: 'box', sx: w * 0.9, sy: 1.5, sz: d * 0.9 }, bursts: [[T - 0.12, Math.round(5 * n)]], sizeLife: [[0, 0.2, 0, 5], [0.25, 1], [1, 0.9]], alpha: [[0, 0], [0.15, 1], [0.7, 0.9], [1, 0]] },
    { name: `${tag} Feathers`, mat: 'feather', fudge: -1, pos: [0, 1, 0], life: [1.0, 1.6], size: [0.26, 0.42], rot: [0, TAU], rotZ: [-3, 3], shape: { type: 'box', sx: w, sy: 1, sz: d }, bursts: [[T + 0.05, Math.round(20 * n)]], vel: { y: [1, 4] }, drag: 2, gravity: 0.1, noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.1, 1], [0.7, 0.85], [1, 0]] }]; },
  ringfx(tag, r0, r1, T, sf, col) { const rm = (r0 + r1) / 2; return [
    { name: `${tag} Zzz`, mat: 'zzz', fudge: -4, life: [1.0, 1.4], size: [0.45, 0.75], rot: [-0.4, 0.2], shape: { type: 'circle', r: rm, th: 0.05, y: 1.2 }, bursts: [[T + 0.05, Math.round(4 + rm * 0.8)]], vel: { y: [0.8, 1.6] }, sizeLife: [[0, 0.3, 0, 3], [0.25, 1], [1, 1.2]], alpha: [[0, 0], [0.15, 1], [0.75, 0.9], [1, 0]] },
    { name: `${tag} Bubbles`, mat: 'bubble', fudge: -3, life: [0.8, 1.3], size: [0.25, 0.5], shape: { type: 'circle', r: r1, th: (r1 - r0) / r1, y: 0.3 }, bursts: [[T + 0.05, Math.round(6 + rm * 1.4)]], vel: { y: [1.5, 3.5] }, noise: { s: 0.5, f: 1, sp: 0.5 }, alpha: [[0, 0], [0.15, 0.9], [0.85, 0.8], [1, 0]] }]; },
};

// ---------- 도박의 악마 룰레타: 룰렛 휠이 돈다 · 칩 탑 · 카드 · 동전 ----------
const chipQuad = (name, mat, p, y, size, t, life) => ({ name, mat, mode: 'mesh', mesh: 'quad', fudge: 10, pos: [p[0], p[1] + y, p[2]], size, rot3: YAW, bursts: [[t, 1]], life, sizeLife: pop, alpha: [[0, 1], [0.8, 1], [1, 0]] });
KITS.roulette = {
  lineArt: false, arcs: false, slash: sf => 'slash' + sf,
  mark(tag, R, T, sf, col) { const L = [
    gq(`${tag} Wheel`, 'wheel' + sf, R * 2.15, 0, T + 0.5, { rot3: YAW, rotLife: [0, 7, 0], sizeLife: [[0, 0.8, 0, 1.5], [0.4, 1]], alpha: [[0, 0], [0.25, 0.85], [0.6, 1], [0.75, 1], [1, 0]] }),
    gq(`${tag} WheelBack`, 'wheel' + (sf === 'G' ? 'R' : 'G'), R * 1.5, 0.05, T + 0.4, { fudge: 35, rot3: YAW, rotLife: [0, -11, 0], alpha: [[0, 0], [0.3, 0.6], [0.8, 0.7], [1, 0]] }),
    gq(`${tag} WheelFlash`, 'wheel' + sf, R * 2.15, T, 0.35, { fudge: 32, pos: [0, 0.07, 0], rot3: YAW, rotLife: [0, 3, 0], sizeLife: [[0, 1], [1, 1.22]], alpha: [[0, 1], [1, 0]] })];
    const n = R < 1.6 ? 4 : 6;
    for (let i = 0; i < n; i++) { const a = i * TAU / n; L.push(chipQuad(`${tag} Peg${i}`, i % 2 ? 'chipK' : 'chipR', [Math.cos(a) * R * 0.86, 0, Math.sin(a) * R * 0.86], 0.09, clampN(R * 0.36, 0.5, 1.6), T * 0.8 * i / n, T + 0.45 - T * 0.8 * i / n)); }
    return L; },
  column(tag, p, t, s, sf, col) { const L = [];
    for (let i = 0; i < 5; i++) L.push(chipQuad(`${tag} Stack${i}`, i % 2 ? 'chipK' : 'chipR', p, 0.14 + i * 0.34 * s, 1.5 * s, t + i * 0.035, 0.75 - i * 0.035));
    L.push({ name: `${tag} CardFountain`, mat: 'card', fudge: -3, pos: [p[0], p[1] + 0.6 * s, p[2]], life: [0.7, 1.1], size: [0.4, 0.62], rot: [0, TAU], rotZ: [-12, 12], shape: { type: 'sphere', r: 0.3 * s, th: 1 }, bursts: [[t + 0.05, Math.round(6 * s)]], vel: { y: [6 * s, 11 * s], rad: [1, 3] }, drag: 1.4, gravity: 1.8, alpha: [[0, 1], [0.85, 1], [1, 0]] });
    L.push({ name: `${tag} Coins`, mat: 'coin', fudge: -4, pos: [p[0], p[1] + 0.5 * s, p[2]], life: [0.7, 1.1], size: [0.22, 0.36], rot: [0, TAU], rotZ: [-12, 12], shape: { type: 'sphere', r: 0.3 * s, th: 1 }, bursts: [[t + 0.03, Math.round(8 * s)]], speed: [3 * s, 7 * s], drag: 1.2, gravity: 1.6, alpha: [[0, 1], [0.85, 1], [1, 0]] });
    return L; },
  stream(tag, w, len, T, sf, col) { return [
    { name: `${tag} CardStream`, mat: 'card', fudge: -3, pos: [0, 1.2, 0.5], life: [0.32, 0.4], size: [0.45, 0.7], rot: [0, TAU], rotZ: [-14, 14], shape: { type: 'box', sx: w * 0.7, sy: 1, sz: 0.4 }, bursts: [[T, Math.round(6 + w * 2)], [T + 0.07, Math.round(4 + w)]], vel: { z: [len / 0.38 * 0.85, len / 0.38 * 1.1] }, alpha: [[0, 1], [0.85, 1], [1, 0]] },
    { name: `${tag} CardFall`, mat: 'card', fudge: -3, pos: [0, 1.4, len / 2], life: [0.8, 1.3], size: [0.36, 0.56], rot: [0, TAU], rotZ: [-8, 8], shape: { type: 'box', sx: w, sy: 1, sz: len }, bursts: [[T + 0.12, Math.round(len * 0.7)]], vel: { y: [1, 3.5] }, drag: 1.5, gravity: 1.2, alpha: [[0, 1], [0.8, 1], [1, 0]] }]; },
  sweep(tag, span, R, T, sf, col) { const tz = Math.min(2.2, Math.tan(Math.min(span, 2.6) / 2)); return [
    { name: `${tag} CardFan`, mat: 'card', fudge: -3, pos: [0, 1.3, 0.6], life: [0.4, 0.55], size: [0.5, 0.8], rot: [0, TAU], rotZ: [-14, 14], shape: { type: 'box', sx: 1.5, sy: 1.6, sz: 0.3 }, bursts: [[T, Math.round(10 + R)], [T + 0.07, Math.round(6 + R * 0.6)]], vel: { z: [R * 1.5, R * 2.2], x: [-R * 1.7 * tz, R * 1.7 * tz], y: [-0.5, 1.5] }, drag: 1, alpha: [[0, 1], [0.85, 1], [1, 0]] },
    { name: `${tag} CoinSpray`, mat: 'coin', fudge: -4, pos: [0, 1, 1], life: [0.7, 1.1], size: [0.22, 0.36], rot: [0, TAU], rotZ: [-12, 12], shape: { type: 'box', sx: 2, sy: 1, sz: 1 }, bursts: [[T + 0.03, Math.round(10 + R)]], vel: { z: [R * 0.7, R * 1.8], x: [-R * 1.3 * tz, R * 1.3 * tz], y: [2, 6] }, drag: 1.4, gravity: 1.6, alpha: [[0, 1], [0.85, 1], [1, 0]] }]; },
  field(tag, w, d, T, sf, col) { const n = clampN(w * d / 150, 0.8, 5); return [
    { name: `${tag} CardStorm`, mat: 'card', fudge: -3, pos: [0, 1.2, 0], life: [0.8, 1.3], size: [0.4, 0.62], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'box', sx: w, sy: 1, sz: d }, bursts: [[T + 0.04, Math.round(24 * n)]], vel: { y: [3, 9], x: [-2, 2], z: [-2, 2] }, drag: 1.5, gravity: 1.6, alpha: [[0, 1], [0.8, 1], [1, 0]] }]; },
  ringfx(tag, r0, r1, T, sf, col) { const rm = (r0 + r1) / 2; return [
    { name: `${tag} ChipRoll`, mat: 'chipR', fudge: -4, life: [0.5, 0.7], size: [0.5, 0.8], rot: [0, TAU], rotZ: [-14, 14], shape: { type: 'circle', r: Math.max(0.5, r0), th: 0, y: 0.6 }, bursts: [[T, Math.round(8 + rm)]], vel: { rad: [(r1 - r0) * 1.8, (r1 - r0) * 2.4], y: [1, 3] }, drag: 1, gravity: 1.2, alpha: [[0, 1], [0.85, 1], [1, 0]] },
    { name: `${tag} ChipRollK`, mat: 'chipK', fudge: -4, life: [0.5, 0.7], size: [0.5, 0.8], rot: [0, TAU], rotZ: [-14, 14], shape: { type: 'circle', r: Math.max(0.5, r0), th: 0, y: 0.6 }, bursts: [[T + 0.04, Math.round(6 + rm)]], vel: { rad: [(r1 - r0) * 1.8, (r1 - r0) * 2.4], y: [1, 3] }, drag: 1, gravity: 1.2, alpha: [[0, 1], [0.85, 1], [1, 0]] }]; },
};

// ---------- 천사 벨자르: W(흰 쪽) = 내려오는 후광·빛의 십자창·흰 깃털 / 그 밖(검은 쪽) = 가시 문양·솟는 사슬·검은 깃털 ----------
KITS.belzar = {
  keepSide: true, lineArt: false, get arcs() { return true; }, slash: sf => 'slash' + sf,
  mark(tag, R, T, sf, col) {
    if (sf === 'W') return [
      gq(`${tag} HaloSigil`, 'haloSig', R * 2.15, 0, T + 0.7, { rot3: YAW, rotLife: [0, 0.6, 0], sizeLife: [[0, 0.85, 0, 1], [0.3, 1]], alpha: [[0, 0], [0.3, 0.8], [0.55, 1], [0.7, 0.9], [1, 0]] }),
      ...[[7, 2.2], [5, 1.6], [3, 1.05]].map(([h, k], i) => ({ name: `${tag} HaloDown${i}`, mat: 'halo', mode: 'mesh', mesh: 'quad', fudge: -6, pos: [0, h, 0], size: R * k * 1.25, rot3: YAW, rotLife: [0, 2, 0], bursts: [[0, 1]], life: T, vel: { y: [-(h - 0.2) / T, -(h - 0.2) / T] }, alpha: [[0, 0], [0.25, 1], [0.95, 1], [1, 0]] })),
      gq(`${tag} HaloFlash`, 'halo', R * 2.7, T, 0.4, { fudge: 31, pos: [0, 0.08, 0], sizeLife: [[0, 0.7, 0, 2], [1, 1.3]], alpha: [[0, 1], [1, 0]] })];
    return [
      gq(`${tag} ThornSigil`, 'thornSig', R * 2.25, 0, T + 0.8, { rot3: YAW, rotLife: [0, -0.8, 0], sizeLife: [[0, 1.2, 0, -1.5], [0.35, 1], [0.6, 1.04], [1, 0.98]], alpha: [[0, 0], [0.2, 0.7], [0.4, 0.5], [0.55, 1], [0.75, 0.9], [1, 0]] }),
      { name: `${tag} DarkCreep`, mat: 'mist', fudge: 26, pos: [0, 0.3, 0], life: [T * 0.8, T * 1.1], size: [R * 0.7, R * 1.1], rot: [0, TAU], shape: { type: 'circle', r: R * 0.9, th: 0.3, y: 0 }, bursts: [[0, Math.round(5 + R)]], vel: { orb: [1, 2], rad: [-R / T * 0.4, -R / T * 0.2] }, alpha: [[0, 0], [0.3, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
      { name: `${tag} EmberCreep`, mat: 'dotE', fudge: -6, pos: [0, 0.15, 0], life: [T * 0.6, T], size: [0.06, 0.12], shape: { type: 'circle', r: R, th: 0.1, y: 0 }, bursts: [[0, Math.round(10 + R * 3)], [T * 0.4, Math.round(8 + R * 2)]], vel: { rad: [-R / T * 0.9, -R / T * 0.5], orb: [1, 2.5] }, alpha: [[0, 0], [0.2, 1], [1, 0]] }]; },
  column(tag, p, t, s, sf, col) {
    if (sf === 'W') return [
      { name: `${tag} LightSpear`, mat: 'cross', mode: 'mesh', mesh: 'spear', fudge: 5, pos: p, size: 9 * s, rot3: [0, 0.6, 0], bursts: [[t - 0.05, 1]], life: 0.6, sizeLife: [[0, 1.7], [0.25, 1], [1, 0.95]], alpha: [[0, 0], [0.15, 1], [0.6, 0.85], [1, 0]] },
      { name: `${tag} HaloRise`, mat: 'halo', mode: 'mesh', mesh: 'quad', fudge: -6, pos: [p[0], p[1] + 0.3, p[2]], size: [2.4 * s, 2.4 * s], rot3: YAW, bursts: [[t, 1], [t + 0.1, 1], [t + 0.2, 1]], life: 0.6, vel: { y: [5 * s, 5 * s] }, sizeLife: [[0, 0.5], [1, 1.5]], alpha: [[0, 1], [1, 0]] },
      { name: `${tag} WhiteFeathers`, mat: 'featherW', fudge: -1, pos: [p[0], p[1] + 1.2 * s, p[2]], life: [1.0, 1.6], size: [0.26, 0.42], rot: [0, TAU], rotZ: [-3, 3], shape: { type: 'sphere', r: 0.4 * s, th: 1 }, bursts: [[t, Math.round(7 * s)]], speed: [2 * s, 6 * s], drag: 3, gravity: 0.1, noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.08, 1], [0.7, 0.85], [1, 0]] }];
    return [
      ...[0, 1, 2].map(i => { const a = i * TAU / 3 + 0.4; return { name: `${tag} Chain${i}`, mat: 'chain', mode: 'mesh', mesh: 'chainM', fudge: 5, pos: [p[0] + Math.cos(a) * 0.5 * s, p[1], p[2] + Math.sin(a) * 0.5 * s], size: 6 * s, rot3: [Math.sin(a) * 0.22, a, -Math.cos(a) * 0.22],
        bursts: [[t + i * 0.03, 1]], life: 0.85, sizeLife: [[0, 0.05, 0, 7], [0.22, 1.05], [0.4, 1], [1, 1]], alpha: [[0, 1], [0.7, 0.9], [1, 0]] }; }),
      { name: `${tag} DarkFlame`, mat: 'dustE', fudge: 3, pos: [p[0], p[1] + 0.6 * s, p[2]], life: [0.6, 0.9], size: [1.3 * s, 2.1 * s], rot: [0, TAU], shape: { type: 'sphere', r: 0.5 * s, th: 1 }, bursts: [[t, Math.round(4 * s)]], vel: { y: [2 * s, 4.5 * s], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.3]], alpha: [[0, 0], [0.15, 0.85], [1, 0]], flip: { x: 4, y: 4 } },
      { name: `${tag} BlackFeathers`, mat: 'featherK', fudge: 18, pos: [p[0], p[1] + 1.2 * s, p[2]], life: [1.0, 1.6], size: [0.28, 0.46], rot: [0, TAU], rotZ: [-3, 3], shape: { type: 'sphere', r: 0.4 * s, th: 1 }, bursts: [[t, Math.round(7 * s)]], speed: [2 * s, 6 * s], drag: 3, gravity: 0.14, noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.08, 1], [0.7, 0.85], [1, 0]] }]; },
  stream(tag, w, len, T, sf, col) { const mat = sf === 'W' ? 'featherW' : 'featherE'; return [
    { name: `${tag} FeatherLance`, mat, mode: 'stretch', vs: 0.03, ls: 1.5, fudge: -3, pos: [0, 1.2, 0.5], life: [0.3, 0.4], size: [0.5, 0.8], shape: { type: 'box', sx: w * 0.7, sy: 1, sz: 0.4 }, bursts: [[T, Math.round(6 + w * 2)], [T + 0.06, Math.round(4 + w)]], vel: { z: [len / 0.36 * 0.85, len / 0.36 * 1.1] }, alpha: [[0, 1], [0.85, 1], [1, 0]] }]; },
  sweep(tag, span, R, T, sf, col) { const tz = Math.min(2.2, Math.tan(Math.min(span, 2.6) / 2)), mat = sf === 'W' ? 'featherW' : 'featherK'; return [
    { name: `${tag} FeatherSweep`, mat, fudge: sf === 'W' ? -1 : 18, pos: [0, 1.4, 0.8], life: [0.9, 1.4], size: [0.28, 0.46], rot: [0, TAU], rotZ: [-4, 4], shape: { type: 'box', sx: 2, sy: 1.6, sz: 0.5 }, bursts: [[T, Math.round(14 + R * 1.5)]], vel: { z: [R * 0.7, R * 1.6], x: [-R * 1.2 * tz, R * 1.2 * tz], y: [0, 2] }, drag: 2.4, gravity: 0.12,
      noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.08, 1], [0.7, 0.85], [1, 0]] }]; },
  field(tag, w, d, T, sf, col) { return []; },
  ringfx(tag, r0, r1, T, sf, col) { return []; },
};

// ---------- 해방된 악마: 지옥문 문양이 달아오른다 · 솟구치는 불기둥 · 검은 연기 · 불씨 ----------
KITS.demon = {
  lineArt: false, arcs: false, slash: sf => 'slash' + sf,
  mark(tag, R, T, sf, col) { return [
    gq(`${tag} Gate`, 'gate' + sf, R * 2.3, 0, T + 0.9, { rot3: YAW, rotLife: [0, 0.25, 0], sizeLife: [[0, 0.9], [0.5, 1], [1, 1.03]], alpha: [[0, 0], [0.15, 0.55], [0.3, 0.35], [0.45, 0.8], [0.52, 1], [0.7, 0.85], [1, 0]] }),
    gq(`${tag} Heat`, 'crack' + sf, R * 2.1, 0, T + 0.1, { fudge: 35, pos: [0, 0.03, 0], rot3: YAW, alpha: [[0, 0], [0.6, 0.5], [0.95, 0.9], [1, 0]] }),
    gq(`${tag} GateFlash`, 'gate' + sf, R * 2.3, T, 0.4, { fudge: 32, pos: [0, 0.08, 0], rot3: YAW, sizeLife: [[0, 1], [1, 1.3]], alpha: [[0, 1], [1, 0]] }),
    { name: `${tag} RimFire`, mat: 'flame' + sf, fudge: 3, life: [0.45, 0.7], size: [0.8, 1.4], shape: { type: 'circle', r: R * 0.95, th: 0.06, y: 0.5 }, bursts: [[T * 0.2, Math.round(4 + R)], [T * 0.5, Math.round(5 + R * 1.5)], [T * 0.8, Math.round(6 + R * 2)]], vel: { y: [1, 2.5], space: 'world' }, sizeLife: [[0, 0.6], [1, 1.2]], alpha: [[0, 0], [0.2, 0.9], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Embers`, mat: 'dot' + sf, fudge: -6, life: [0.8, 1.4], size: [0.05, 0.1], shape: { type: 'circle', r: R, th: 1, y: 0.1 }, bursts: [[0, Math.round(8 + R * 2)], [T * 0.5, Math.round(10 + R * 3)]], vel: { y: [1.5, 4] }, noise: { s: 1, f: 1, sp: 0.7 }, color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] }]; },
  column(tag, p, t, s, sf, col) { return [
    { name: `${tag} FireCore`, mat: 'flame' + sf, fudge: 2, pos: [p[0], p[1] + 2.2 * s, p[2]], size: 5.2 * s, bursts: [[t, 1], [t + 0.07, 1]], life: 0.55, vel: { y: [3 * s, 4 * s], space: 'world' }, sizeLife: [[0, 0.4, 0, 4], [0.3, 1], [1, 1.15]], alpha: [[0, 1], [0.6, 0.85], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} FireTongues`, mat: 'flame' + (sf === 'F' ? 'R' : sf), fudge: 3, pos: [p[0], p[1] + 1.0 * s, p[2]], life: [0.45, 0.75], size: [1.8 * s, 3 * s], shape: { type: 'circle', r: 0.9 * s, th: 1, y: 0 }, bursts: [[t, Math.round(5 * s)], [t + 0.1, Math.round(4 * s)]], vel: { y: [4 * s, 8 * s], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.25]], alpha: [[0, 0], [0.15, 0.95], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Smoke`, mat: 'mist', fudge: 25, pos: [p[0], p[1] + 2.5 * s, p[2]], life: [1.0, 1.5], size: [1.6 * s, 2.6 * s], rot: [0, TAU], shape: { type: 'sphere', r: 0.6 * s, th: 1 }, bursts: [[t + 0.15, Math.round(3 * s)]], vel: { y: [1.5, 3], space: 'world' }, drag: 1, sizeLife: [[0, 0.6], [1, 1.5]], alpha: [[0, 0], [0.25, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Cinders`, mat: 'dot' + sf, mode: 'stretch', vs: 0.05, ls: 1.4, fudge: -5, pos: [p[0], p[1] + 0.6 * s, p[2]], life: [0.6, 1.1], size: [0.06 * s, 0.12 * s], shape: { type: 'sphere', r: 0.4 * s, th: 1 }, bursts: [[t, Math.round(12 * s)]], vel: { y: [5 * s, 12 * s], rad: [1, 4] }, drag: 1.6, gravity: 0.9,
      color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 1], [0.7, 1], [1, 0]] }]; },
  stream(tag, w, len, T, sf, col) { return [
    { name: `${tag} FireWall`, mat: 'flame' + sf, fudge: 3, pos: [0, 1.3, len / 2], life: [0.5, 0.85], size: [clampN(w * 0.7, 1.6, 4), clampN(w * 1.1, 2.4, 6)], shape: { type: 'box', sx: w * 0.7, sy: 0.3, sz: len }, bursts: [[T + 0.02, Math.round(len * 0.9)], [T + 0.14, Math.round(len * 0.6)]], vel: { y: [3, 7], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.25]], alpha: [[0, 0], [0.15, 0.95], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Smoke`, mat: 'mist', fudge: 25, pos: [0, 3, len / 2], life: [1.0, 1.6], size: [2, 3.4], rot: [0, TAU], shape: { type: 'box', sx: w * 0.8, sy: 0.5, sz: len }, bursts: [[T + 0.2, Math.round(len * 0.25)]], vel: { y: [1.5, 3], space: 'world' }, sizeLife: [[0, 0.6], [1, 1.5]], alpha: [[0, 0], [0.25, 0.55], [1, 0]], flip: { x: 4, y: 4 } }]; },
  sweep(tag, span, R, T, sf, col) { const tz = Math.min(1.8, Math.tan(Math.min(span, 2.6) / 2)); return [
    { name: `${tag} Claws`, mat: 'claw' + sf, mode: 'mesh', mesh: 'quad', fudge: 28, pos: [0, 0.07, R * 0.55], size: R * 0.9, rot3: [0, 0, 0], bursts: [[T, 1]], life: 0.9, sizeLife: [[0, 0.7, 0, 3], [0.15, 1], [1, 1.03]], alpha: [[0, 1], [0.5, 0.8], [1, 0]] },
    { name: `${tag} FireFan`, mat: 'flame' + sf, fudge: 3, pos: [0, 1.2, R * 0.5], life: [0.5, 0.85], size: [2, 3.6], shape: { type: 'box', sx: R * Math.min(1.7, tz * 1.1), sy: 0.3, sz: R * 0.85 }, bursts: [[T + 0.06, Math.round(10 + R * 1.6)], [T + 0.18, Math.round(6 + R)]], vel: { y: [3, 7], z: [1, 4], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.25]], alpha: [[0, 0], [0.15, 0.95], [1, 0]], flip: { x: 4, y: 4 } }]; },
  field(tag, w, d, T, sf, col) { const n = clampN(w * d / 150, 0.8, 5); return [
    { name: `${tag} Inferno`, mat: 'flame' + sf, fudge: 3, pos: [0, 1.4, 0], life: [0.6, 1.0], size: [3, 5.5], shape: { type: 'box', sx: w * 0.95, sy: 0.3, sz: d * 0.95 }, bursts: [[T + 0.02, Math.round(26 * n)], [T + 0.16, Math.round(18 * n)], [T + 0.3, Math.round(10 * n)]], vel: { y: [3, 8], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.25]], alpha: [[0, 0], [0.15, 0.95], [1, 0]], flip: { x: 4, y: 4 } }]; },
  ringfx(tag, r0, r1, T, sf, col) { const rm = (r0 + r1) / 2; return [
    { name: `${tag} FireRing`, mat: 'flame' + sf, fudge: 3, pos: [0, 1.2, 0], life: [0.5, 0.85], size: [2, 3.4], shape: { type: 'circle', r: r1, th: (r1 - r0) / r1, y: 0 }, bursts: [[T + 0.03, Math.round(10 + rm * 2.4)], [T + 0.15, Math.round(6 + rm * 1.4)]], vel: { y: [3, 7], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.25]], alpha: [[0, 0], [0.15, 0.95], [1, 0]], flip: { x: 4, y: 4 } }]; },
};

// =====================================================================================================
// 움직임이 다른 공격 틀 (보스마다 "공격하는 방식" 자체를 다르게 하기 위한 것)
// =====================================================================================================
const shiftL = (layers, d) => layers.map(l => Object.assign(l, { pos: [(l.pos ? l.pos[0] : 0) + d[0], (l.pos ? l.pos[1] : 0) + d[1], (l.pos ? l.pos[2] : 0) + d[2]] }));
const hold = (name, mat, size, t0, dur, o) => Object.assign({ name, mat, mode: 'mesh', mesh: 'quad', fudge: 34, pos: [0, 0.05, 0], size, rot3: YAW, bursts: [[t0, 1]], life: dur,
  alpha: [[0, 0], [Math.min(0.2, 0.4 / dur), 1], [Math.max(0.8, 1 - 0.4 / dur), 1], [1, 0]] }, o || {});
// 움직이는 고리: 반경 ra → rb 로 dur 초 동안 이동 (ra > rb 면 바깥에서 안으로 좁혀듦)
function travelRing(tag, ra, rb, T, dur, sf, col, o) {
  o = o || {}; const lo = Math.min(ra, rb), hi = Math.max(ra, rb), n = clampN(hi / 5, 1, 4), SL = [[0, Math.max(0.3, ra)], [1, Math.max(0.3, rb)]], L = [];
  L.push({ name: `${tag} Wall`, mat: 'band' + sf, mode: 'mesh', mesh: 'cyl', fudge: 6, size: 1, rot3: YAW, rotLife: [0, 0.8, 0], bursts: [[T, 1]], life: dur, sizeLife: SL, alpha: [[0, 0], [0.08, 1], [0.85, 0.9], [1, 0]] });
  L.push({ name: `${tag} Wall2`, mat: 'pillar' + (o.s2 || sf), mode: 'mesh', mesh: 'cyl', fudge: 7, size: 1, rot3: YAW, rotLife: [0, -0.6, 0], bursts: [[T + 0.05, 1]], life: dur, sizeLife: SL, alpha: [[0, 0], [0.08, 0.7], [0.85, 0.6], [1, 0]] });
  L.push({ name: `${tag} Floor`, mat: 'line' + sf, mode: 'mesh', mesh: 'cOuter', fudge: 30, size: 1, rot3: YAW, bursts: [[T, 1]], life: dur, sizeLife: SL, alpha: [[0, 0], [0.08, 1], [0.85, 0.9], [1, 0]] });
  L.push({ name: `${tag} Sparks`, mat: 'dot' + sf, mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, life: [0.4, 0.8], size: [0.06, 0.12], shape: { type: 'circle', r: hi, th: (hi - lo) / hi, y: 0.3 }, bursts: Array.from({ length: 5 }, (_, i) => [T + dur * i / 5, Math.round(14 * n)]), vel: { y: [2, 7] }, drag: 2, gravity: 0.6,
    color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 1], [0.7, 1], [1, 0]] });
  for (let i = 0; i < 4; i++) { const f = (i + 0.5) / 4, r = ra + (rb - ra) * f, w = Math.abs(rb - ra) / 4; L.push(...KIT.ringfx(`${tag}k${i}`, Math.max(0, r - w / 2), r + w / 2, T + dur * f - 0.05, sf, col)); }
  return L.concat(o.extra || []);
}
// 차례로 터지며 나아가는 직선: 원점에서 +Z 로 travel 초에 걸쳐 진행
function travelLane(tag, w, len, T, travel, sf, col, o) {
  o = o || {}; const s2 = o.s2 || sf, k = Math.max(2, Math.round(len / Math.max(2.6, w * 0.9))), seg = len / k, L = [];
  SM[tag + 'Seg'] = strip([0, -seg / 2], [0, seg / 2], w * 0.95, 0.05);
  L.push(...castMark(tag, clampN(w * 0.45, 0.9, 2.4), T, sf, col));
  for (let i = 0; i < k; i++) { const z = (i + 0.5) * seg, t = T + travel * i / (k - 1);
    L.push({ name: `${tag} Crack${i}`, mat: 'crack' + s2, mode: 'mesh', mesh: tag + 'Seg', fudge: 33, pos: [0, 0, z], size: 1, rot3: [0, 0, 0], bursts: [[t, 1]], life: 1.1, alpha: [[0, 1], [0.4, 0.7], [1, 0]] });
    L.push({ name: `${tag} Band${i}`, mat: 'band' + sf, mode: 'mesh', mesh: tag + 'Seg', fudge: 32, pos: [0, 0.03, z], size: 1, rot3: [0, 0, 0], bursts: [[t, 1]], life: 0.25, alpha: [[0, 0.8], [1, 0]] });
    L.push(...miniBurst(`${tag}p${i}`, [0, 0, z], t, clampN(w / 2.8, 0.7, 2), alt(i, sf, s2), col)); }
  return L.concat(o.extra || []);
}
// 원점을 축으로 도는 광선: 길이 len, 폭 w, T 뒤부터 dur 초 동안 초당 omega 라디안 회전
function sweepBeam(tag, len, w, T, dur, omega, sf, col, o) {
  o = o || {}; const L = [], h = o.h ?? 1.2;
  SM[tag + 'Beam'] = merge([strip([0, 0.8], [0, len], w, h), wallZ(len, h * 2, 0), strip([0, 0.8], [0, len], w * 1.6, 0.06)]);
  SM[tag + 'Aim'] = strip([0, 0.8], [0, len], 0.12, 0.06);
  L.push({ name: `${tag} Aim`, mat: 'line' + sf, mode: 'mesh', mesh: tag + 'Aim', fudge: 38, size: 1, rot3: [0, 0, 0], rotLife: [0, omega, 0], bursts: [[0, 1]], life: T + dur, alpha: [[0, 0], [0.1, 0.7], [0.9, 0.7], [1, 0]] });
  L.push({ name: `${tag} Beam`, mat: 'band' + sf, mode: 'mesh', mesh: tag + 'Beam', fudge: 6, size: 1, rot3: [0, omega * T, 0], rotLife: [0, omega, 0], bursts: [[T, 1]], life: dur, alpha: [[0, 0], [Math.min(0.1, 0.15 / dur), 1], [1 - Math.min(0.1, 0.2 / dur), 1], [1, 0]] });
  L.push({ name: `${tag} BeamCore`, mat: 'pillar' + (o.s2 || sf), mode: 'mesh', mesh: tag + 'Beam', fudge: 5, size: 1, rot3: [0, omega * T, 0], rotLife: [0, omega, 0], bursts: [[T, 1]], life: dur, alpha: [[0, 0], [Math.min(0.1, 0.15 / dur), 0.7], [1 - Math.min(0.1, 0.2 / dur), 0.7], [1, 0]] });
  L.push({ name: `${tag} Source`, mat: 'flare' + sf, fudge: -7, pos: [0, h + 0.4, 0], size: 3.4, rot: [0, TAU], rotZ: [2, 2], bursts: [[0, 1]], life: T + dur, sizeLife: [[0, 0.3], [T / (T + dur), 1], [1, 0.9]], alpha: [[0, 0], [T / (T + dur) * 0.9, 0.7], [T / (T + dur), 1], [0.95, 1], [1, 0]] });
  L.push({ name: `${tag} Sparks`, mat: 'dot' + sf, fudge: -6, pos: [0, 0.4, 0], life: [0.5, 0.9], size: [0.06, 0.12], shape: { type: 'circle', r: len, th: 1, y: 0 }, rate: 40, vel: { y: [1, 4] }, color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 0], [0.2, 1], [1, 0]] });
  return L.concat(o.extra || []);
}
// 휘두르는 칼날: 부채꼴 span 을 dir(+1 = 한쪽에서 반대쪽으로) 방향으로 0.24초에 훑는다
function swingBlade(tag, span, R, T, dir, mat, mat2) {
  SM[tag + 'Swing'] = merge([strip([0, 0.8], [0, R], 1.1, 1.3), wallZ(R, 2.2, 0)]);
  const w = dir * span / 0.24, r0 = -dir * span / 2;
  return [0, 0.03, 0.06].map((dt, i) => ({ name: `${tag} Swing${i}`, mat: i ? mat2 : mat, mode: 'mesh', mesh: tag + 'Swing', fudge: 4 + i, size: 1, rot3: [0, r0 - w * dt * 0.0, 0], rotLife: [0, w, 0], bursts: [[T - 0.12 + dt, 1]], life: 0.24,
    alpha: [[0, 0], [0.15, i ? 0.5 : 1], [0.8, i ? 0.4 : 0.9], [1, 0]] }));
}
// 따라다니는 것(attached, loop): 중심 덩어리 + 꼬리
function followOrb(tag, mats, size, y) {
  const LIFE = 3, keep = (name, mat, sz, extra) => Object.assign({ name, mat, pos: [0, y, 0], size: sz, rate: 1 / (0.85 * LIFE), life: LIFE, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 1], [0.85, 1], [1, 0]], onStop: 0.3 }, extra || {});
  return [keep(`${tag} Core`, mats.core, size, { fudge: -8, rotZ: [0.6, 0.6] }), keep(`${tag} Halo`, mats.glow, size * 1.9, { fudge: 2, alpha: [[0, 0], [0.15, 0.4], [0.85, 0.4], [1, 0]] }),
    { name: `${tag} Trail`, mat: mats.trail, fudge: -4, pos: [0, y, 0], life: [0.6, 1.0], size: [size * 0.2, size * 0.4], rot: [0, TAU], shape: { type: 'sphere', r: size * 0.3, th: 1 }, rate: 14, world: true, vel: { y: [0.2, 0.8] }, sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 0.9], [1, 0]] },
    { name: `${tag} Ground`, mat: mats.ground, mode: 'mesh', mesh: 'quad', fudge: 34, pos: [0, 0.05, 0], size: size * 1.6, rot3: YAW, rotLife: [0, 1.5, 0], rate: 1 / (0.85 * LIFE), life: LIFE, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 0.8], [0.85, 0.8], [1, 0]], onStop: 0.3 }];
}
const sysD = (id, layers, dur) => ({ id, role: 'static', orientation: 'facing', loop: false, duration: dur, layers });
const sysA = (id, layers) => ({ id, role: 'attached', loop: true, duration: 2, layers });
const MOVER = (pts) => ({ path: pts, hidden: true });
