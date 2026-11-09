// ===== Roulette_Boss — 도박의 악마 룰레타 보스 패턴 VFX (한 팩, 시스템 17개) =====
// 좌표: attached/static P = 지정 위치 발밑, +Z = 서버가 넘기는 수평 방향(보스 정면 / 구역 방향). 크기는 기획 수치로 고정(1블록 = 1 m).
// 보스 키 약 8 m, 등 뒤 룰렛 휠 중심 = 보스 발밑 기준 (0, 6.4, -1.05), 휠 반지름 2.1 m.
const GOLD = [1, 0.84, 0.45], PALE = [1, 0.97, 0.86], AMBER = [1, 0.6, 0.2], RED = [1, 0.28, 0.2], DEEP = [0.55, 0.05, 0.08], CRIM = [1, 0.15, 0.12];
const CG = { hot: PALE, mid: GOLD, low: AMBER }, CR = { hot: [1, 0.85, 0.6], mid: RED, low: DEEP };
const WHEEL = [0, 6.4, -1.05], WR = 2.1, HEAD = 6.3, ARENA = 18, TABLE = 4;
const MG = { line: 'line', rune: 'rune', sigil: 'sigil', glow: 'glow' }, MR = { line: 'lineR', rune: 'runeR', sigil: 'sigilR', glow: 'glowR' };
const BG = { star: 'star', flare: 'flare', glow: 'glow', wave: 'wave', streak: 'streak', dot: 'dot', shardD: 'shardD', shardL: 'shardL', mist: 'mist', ring: 'line', motes: 'dot' };
const BR = { star: 'starR', flare: 'flareR', glow: 'glowR', wave: 'waveR', streak: 'streakR', dot: 'dotR', shardD: 'shardDR', mist: 'mist', ring: 'lineR', motes: 'dotR' };
// 무늬 4종 튀기기
const suitPop = (tag, t, n, pos, shape, vel, o) => ['spade', 'hearts', 'diamond', 'club'].map((s, i) => Object.assign({
  name: `${tag} ${s}`, mat: s, fudge: s === 'spade' || s === 'club' ? 12 : -4, pos, life: [0.9, 1.4], size: [0.28, 0.42], rot: [-0.4, 0.4], rotZ: [-2, 2], shape,
  bursts: [[t + i * 0.02, Math.max(1, Math.round(n / 4))]], vel, drag: 1.4, sizeLife: [[0, 0.3, 0, 4], [0.15, 1], [1, 0.8]], alpha: [[0, 0], [0.1, 1], [0.75, 1], [1, 0]] }, o || {}));

var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, {
  wheelRing: merge([ringV(0.05), ticksV(36, 0.9, 1.0, 0.012)]),
  wheelSpokes: ticksV(6, 0.25, 0.92, 0.03),
  wheelHalo: ringV(0.22),
  spokes6: merge([0, 1, 2, 3, 4, 5].map(i => { const a = Math.PI / 2 + Math.PI / 6 + i * TAU / 6; return strip([TABLE * Math.cos(a), TABLE * Math.sin(a)], [ARENA * Math.cos(a), ARENA * Math.sin(a)], 0.12, 0.04); })),
  arena: merge([ringM(ARENA, 0.18, 0.03), ringM(ARENA - 0.5, 0.06, 0.03), ringM(TABLE, 0.14, 0.04), ringM(TABLE + 0.35, 0.05, 0.04)]),
  arenaRune: runeArcs(24, ARENA - 1.4, 0.9, 0.035),
  wedge: fan(Math.PI / 3, TABLE, ARENA, 0.05),
  wedgeEdge: fanEdge(Math.PI / 3, TABLE, ARENA, 0.16, 0.06),
  wedgeArc: ringM(1, 0.035, 0.07, 24, Math.PI / 3, Math.PI / 2 + Math.PI / 6),
  disc8: fan(TAU, 0.4, 8, 0.05, 72), disc8Edge: merge([ringM(8, 0.14, 0.06), ringM(7.6, 0.05, 0.06)]),
  annulus: fan(TAU, 8, ARENA, 0.05, 96), annulusEdge: merge([ringM(8, 0.14, 0.06), ringM(ARENA, 0.16, 0.06), ringM(8.4, 0.05, 0.06)]),
  half10: fan(Math.PI, 0.6, 10, 0.05, 48), half10Edge: fanEdge(Math.PI, 0.6, 10, 0.14, 0.06),
  sweep: crescentXZ(Math.PI * 0.95, 0.08),
  cross: merge([strip([-0, 0], [0, 16], 3, 0.05), strip([0, 0], [0, -16], 3, 0.05), strip([0, 0], [16, 0], 3, 0.05), strip([0, 0], [-16, 0], 3, 0.05)]),
  crossEdge: merge([[1, 0], [-1, 0], [0, 1], [0, -1]].flatMap(([dx, dz]) => [strip([dz * 1.5, dx * 1.5 * 0 + (dx ? 0 : 0)], [dz * 1.5 + dx * 16, dz ? dz * 16 : 0], 0.1, 0.06)])),
  laneW: strip([0, 1.3], [0, 16], 3, 0.05), laneWEdge: merge([strip([-1.5, 1.3], [-1.5, 16], 0.1, 0.06), strip([1.5, 1.3], [1.5, 16], 0.1, 0.06), strip([-1.5, 16], [1.5, 16], 0.1, 0.06)]),
  lane: strip([0, 0], [0, 16], 2, 0.05), laneEdge: merge([strip([-1, 0], [-1, 16], 0.1, 0.06), strip([1, 0], [1, 16], 0.1, 0.06)]),
  pillar2: pillarM(1, 2.2), pillar4: pillarM(1, 4), pillar5: pillarM(1, 5), pillar6: pillarM(1, 5.6), pillar8: pillarM(1, 8), laneSlash: strip([0, 1.3], [0, 16], 2.4, 0.15), cardM: { verts: [[-0.32, 0, -0.48], [0.32, 0, -0.48], [0.32, 0, 0.48], [-0.32, 0, 0.48]], uvs: [[0, 1], [1, 1], [1, 0], [0, 0]], tris: [0, 1, 2, 0, 2, 3] },
  halo: merge([ringM(0.55, 0.05, 0), ringM(0.42, 0.02, 0)]),
});
// cross 외곽선은 직접 다시 만든다 (팔 4개 양옆 선)
EXTRA_MESHES.crossEdge = merge([0, 1, 2, 3].flatMap(i => { const a = i * Math.PI / 2, c = Math.cos(a), s = Math.sin(a), nx = -s, nz = c;
  return [strip([c * 1.5 + nx * 1.5, s * 1.5 + nz * 1.5], [c * 16 + nx * 1.5, s * 16 + nz * 1.5], 0.1, 0.06), strip([c * 1.5 - nx * 1.5, s * 1.5 - nz * 1.5], [c * 16 - nx * 1.5, s * 16 - nz * 1.5], 0.1, 0.06), strip([c * 16 + nx * 1.5, s * 16 + nz * 1.5], [c * 16 - nx * 1.5, s * 16 - nz * 1.5], 0.1, 0.06)]; }));

// ---------- 1. spin: 룰렛 돌리기 (attached, 보스, 반복 — 바늘이 멈출 때 서버가 정지) ----------
const LW = 4;
const SPIN = [
  { name: 'S01 WheelHalo', mat: 'glow', mode: 'mesh', mesh: 'wheelHalo', fudge: 6, pos: WHEEL, size: WR * 1.05, rot3: [0, 0, 0], rotLife: [0, 0, 6], rate: 2.5, life: 0.8, bursts: [[0, 2]], alpha: [[0, 0], [0.3, 0.45], [1, 0]], onStop: 0.2 },
  { name: 'S02 WheelRing', mat: 'line', mode: 'mesh', mesh: 'wheelRing', fudge: 2, pos: WHEEL, size: WR * 1.06, rot3: [0, 0, 0], rotLife: [0, 0, 9], rate: 1 / 0.85, life: 1, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 1], [0.85, 1], [1, 0]], onStop: 0.2 },
  { name: 'S03 Spokes', mat: 'lineR', mode: 'mesh', mesh: 'wheelSpokes', fudge: 3, pos: WHEEL, size: WR, rot3: [0, 0, 0], rotLife: [0, 0, 14], rate: 4, life: 0.35, bursts: [[0, 1]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], onStop: 0.1 },
  { name: 'S04 RimSparks', mat: 'dot', mode: 'stretch', vs: 0.05, ls: 1.4, fudge: -5, pos: WHEEL, life: [0.3, 0.5], size: [0.05, 0.09], shape: { type: 'box', sx: 4.4, sy: 4.4, sz: 0.2 }, rate: 50,
    vel: { y: [1, 3], space: 'world' }, speed: [1, 3], drag: 2, color: [[0, PALE], [1, AMBER]], alpha: [[0, 1], [1, 0]], onStop: 0.15 },
  { name: 'S05 WheelSuits', mat: 'hearts', fudge: -3, pos: WHEEL, life: [0.6, 0.9], size: [0.22, 0.32], rot: [-0.3, 0.3], shape: { type: 'box', sx: 4.2, sy: 4.2, sz: 0.2 }, rate: 6, vel: { y: [0.6, 1.2], space: 'world' }, alpha: [[0, 0], [0.2, 1], [1, 0]], onStop: 0.2 },
  { name: 'S06 WheelSuitsK', mat: 'spade', fudge: 12, pos: WHEEL, life: [0.6, 0.9], size: [0.22, 0.32], rot: [-0.3, 0.3], shape: { type: 'box', sx: 4.2, sy: 4.2, sz: 0.2 }, rate: 6, vel: { y: [0.6, 1.2], space: 'world' }, alpha: [[0, 0], [0.2, 1], [1, 0]], onStop: 0.2 },
  heldLoop('S07 FloorOuter', 'line', 'cOuter', 40, symW(1, LW) * 0 + 0.3, 1, 5.5, [0, 0, 0], LW),
  heldLoop('S08 FloorStar', 'line', 'cHex', 39, symW(6, LW, -1), 0.85, 5.5, [0, 0, 0], LW),
  heldLoop('S09 FloorRune', 'rune', 'cRune', 38, symW(14, LW), 0.8, 5.5, [0, 0, 0], LW),
  heldLoop('S10 FloorSigil', 'sigil', 'quad', 37, symW(6, LW, -1), 0.6, 6.2, [0, 0.05, 0], LW),
  { name: 'S11 FloorGlow', mat: 'glow', mode: 'mesh', mesh: 'quad', fudge: 45, pos: [0, 0.02, 0], size: 12, rot3: YAW, rate: 1 / (0.85 * LW), life: LW, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 0.3], [0.85, 0.3], [1, 0]], onStop: 0.3 },
  { name: 'S12 RiseMotes', mat: 'dot', fudge: -6, life: [1.2, 1.8], size: [0.04, 0.08], shape: { type: 'circle', r: 5, th: 1, y: 0.1 }, rate: 26, bursts: [[0, 12]],
    vel: { y: [1, 2.2] }, noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, PALE], [1, GOLD]], alpha: [[0, 0], [0.15, 1], [1, 0]], onStop: 0.3 },
  { name: 'S13 Chips', mat: 'chipR', fudge: 10, life: [1.0, 1.4], size: [0.3, 0.42], rot: [0, TAU], rotZ: [-4, 4], shape: { type: 'circle', r: 5.2, th: 0.3, y: 0.3 }, rate: 6,
    vel: { orb: [1.8, 2.4], y: [1.2, 2.2] }, alpha: [[0, 0], [0.15, 1], [0.8, 1], [1, 0]], onStop: 0.25 },
  { name: 'S14 ChipsK', mat: 'chipK', fudge: 11, life: [1.0, 1.4], size: [0.3, 0.42], rot: [0, TAU], rotZ: [-4, 4], shape: { type: 'circle', r: 5.2, th: 0.3, y: 0.3 }, rate: 5,
    vel: { orb: [1.8, 2.4], y: [1.2, 2.2] }, alpha: [[0, 0], [0.15, 1], [0.8, 1], [1, 0]], onStop: 0.25 },
  { name: 'S15 OrbitStreak', mat: 'streak', mode: 'stretch', vs: 0.06, ls: 2.2, fudge: -3, life: [0.5, 0.7], size: [0.06, 0.1], shape: { type: 'circle', r: 5.5, th: 0, y: 0.6 }, rate: 14,
    vel: { orb: [5, 6], y: [0.2, 0.6] }, alpha: [[0, 0], [0.2, 1], [1, 0]], onStop: 0.2 },
];

// ---------- 2. betlock: 베팅 마감 (static, 경기장 중앙 = 보스 발밑, 1.6 s) ----------
const BL = [
  { name: 'L01 ArenaStamp', mat: 'line', mode: 'mesh', mesh: 'arena', fudge: 40, size: 1, rot3: YAW, bursts: [[0, 1]], life: 1.6, sizeLife: [[0, 1.12, 0, -2], [0.12, 1]], alpha: [[0, 0], [0.06, 1], [0.6, 0.8], [1, 0]] },
  { name: 'L02 ArenaRune', mat: 'rune', mode: 'mesh', mesh: 'arenaRune', fudge: 39, size: 1, rot3: YAW, rotLife: [0, 0.25, 0], bursts: [[0.04, 1]], life: 1.5, sizeLife: [[0, 1.1, 0, -2], [0.12, 1]], alpha: [[0, 0], [0.08, 0.9], [0.6, 0.7], [1, 0]] },
  { name: 'L03 Spokes', mat: 'lineR', mode: 'mesh', mesh: 'spokes6', fudge: 38, size: 1, rot3: [0, 0, 0], bursts: [[0.06, 1]], life: 1.4, alpha: [[0, 0], [0.06, 1], [0.6, 0.75], [1, 0]] },
  { name: 'L04 SpokeFlash', mat: 'line', mode: 'mesh', mesh: 'spokes6', fudge: 37, size: 1, rot3: [0, 0, 0], bursts: [[0.06, 1]], life: 0.35, alpha: [[0, 1], [1, 0]] },
  { name: 'L05 ArenaFlare', mat: 'line', mode: 'mesh', mesh: 'cOuter', fudge: 36, size: ARENA, rot3: YAW, bursts: [[0.06, 1]], life: 0.5, sizeLife: [[0, 1], [1, 1.06]], alpha: [[0, 1], [1, 0]] },
  { name: 'L06 RimRays', mat: 'streak', mode: 'stretch', vs: 0.06, ls: 3.5, fudge: -2, life: [0.45, 0.7], size: [0.1, 0.18], shape: { type: 'circle', r: ARENA, th: 0, y: 0.1 }, bursts: [[0.06, 60]],
    vel: { y: [8, 13] }, drag: 2.2, alpha: [[0, 1], [1, 0]] },
  { name: 'L07 TableRays', mat: 'streakR', mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, life: [0.4, 0.6], size: [0.08, 0.14], shape: { type: 'circle', r: TABLE, th: 0, y: 0.1 }, bursts: [[0.06, 24]],
    vel: { y: [7, 11] }, drag: 2.2, alpha: [[0, 1], [1, 0]] },
  { name: 'L08 WheelLock', mat: 'star', fudge: -8, pos: WHEEL, size: 5, rot: [0, TAU], bursts: [[0, 1]], life: 0.25, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: 'L09 WheelFlare', mat: 'flare', fudge: -7, pos: WHEEL, size: 6.5, rot: [0, TAU], bursts: [[0, 1]], life: 0.5, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.15]], alpha: [[0, 0.8], [1, 0]] },
  { name: 'L10 WheelRingFlash', mat: 'line', mode: 'mesh', mesh: 'wheelRing', fudge: 1, pos: WHEEL, size: WR * 1.06, rot3: [0, 0, 0], bursts: [[0, 1]], life: 0.45, sizeLife: [[0, 1], [1, 1.3]], alpha: [[0, 1], [1, 0]] },
  { name: 'L11 Wave', mat: 'wave', mode: 'mesh', mesh: 'quad', fudge: 30, pos: [0, 0.07, 0], size: ARENA * 2.4, rot3: YAW, bursts: [[0.06, 1]], life: 0.6, sizeLife: [[0, 0.1, 0, 2], [1, 1]], alpha: [[0, 0.6], [1, 0]] },
  { name: 'L12 Coins', mat: 'coin', fudge: 8, pos: [0, 0.5, 0], life: [1.0, 1.4], size: [0.32, 0.45], rot: [0, TAU], rotZ: [-6, 6], shape: { type: 'circle', r: TABLE, th: 1, y: 0 }, bursts: [[0.06, 28]],
    vel: { y: [6, 9], rad: [1, 3] }, gravity: 1.2, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  ...suitPop('L13', 0.08, 24, [0, 0.3, 0], { type: 'circle', r: ARENA, th: 0.05, y: 0.2 }, { y: [2, 4] }),
  { name: 'L14 Chips', mat: 'chipR', fudge: 10, life: [0.8, 1.2], size: [0.35, 0.5], rot: [0, TAU], rotZ: [-6, 6], shape: { type: 'circle', r: TABLE, th: 0, y: 0.3 }, bursts: [[0.06, 18]],
    vel: { y: [4, 7], rad: [3, 6] }, gravity: 1.3, alpha: [[0, 1], [0.85, 1], [1, 0]] },
];

// ---------- 3. win: 적중 (attached, 맞힌 플레이어, 1.8 s) ----------
const WIN = [
  { name: 'W01 Column', mat: 'pillar', mode: 'mesh', mesh: 'pillar6', fudge: 8, size: 1.6, rot3: YAW, rotLife: [0, 2.5, 0], bursts: [[0, 1]], life: 1.1, sizeLife: [[0, 0.2, 0, 5], [0.15, 1], [1, 0.6]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  { name: 'W02 Flash', mat: 'star', fudge: -8, pos: [0, 1.2, 0], size: 3.4, rot: [0, TAU], bursts: [[0, 1]], life: 0.25, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: 'W03 Flare', mat: 'flare', fudge: -7, pos: [0, 1.3, 0], size: 4, rot: [0, TAU], bursts: [[0, 1]], life: 0.45, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.15]], alpha: [[0, 0.8], [1, 0]] },
  { name: 'W04 Ring', mat: 'line', mode: 'mesh', mesh: 'cOuter', fudge: 30, size: 1.4, rot3: YAW, rotLife: [0, 2, 0], bursts: [[0, 1]], life: 1.2, sizeLife: [[0, 0.3, 0, 3], [0.3, 1], [1, 1.1]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  { name: 'W05 RuneFoot', mat: 'rune', mode: 'mesh', mesh: 'cRune2', fudge: 31, size: 2.6, rot3: YAW, rotLife: [0, -1.5, 0], bursts: [[0.02, 1]], life: 1.2, sizeLife: [[0, 0.3, 0, 3], [0.3, 1]], alpha: [[0, 0], [0.15, 1], [0.6, 0.8], [1, 0]] },
  { name: 'W06 CoinFountain', mat: 'coin', fudge: 8, pos: [0, 0.4, 0], life: [1.1, 1.5], size: [0.26, 0.36], rot: [0, TAU], rotZ: [-8, 8], shape: { type: 'circle', r: 0.3, th: 1, y: 0 },
    bursts: [[0, 14], [0.15, 12], [0.3, 10]], vel: { y: [6, 9], rad: [1.5, 3.2] }, gravity: 1.3, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'W07 Spiral', mat: 'streak', mode: 'stretch', vs: 0.06, ls: 2, fudge: -3, life: [0.6, 0.9], size: [0.06, 0.1], shape: { type: 'circle', r: 0.9, th: 0, y: 0.2 }, bursts: [[0, 20], [0.2, 16]],
    vel: { orb: [6, 7], y: [3, 5] }, alpha: [[0, 1], [1, 0]] },
  ...suitPop('W08', 0.05, 12, [0, 0.8, 0], { type: 'circle', r: 0.8, th: 1, y: 0 }, { orb: [2.5, 3.5], y: [1.5, 2.5] }),
  { name: 'W09 Sparkle', mat: 'star', fudge: -9, life: [0.3, 0.55], size: [0.18, 0.34], rot: [0, TAU], shape: { type: 'sphere', r: 1.3, th: 1 }, pos: [0, 1.3, 0], bursts: [[0.05, 10], [0.35, 8], [0.7, 6]], sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: 'W10 Motes', mat: 'dot', fudge: -6, life: [1.0, 1.6], size: [0.04, 0.08], shape: { type: 'circle', r: 1, th: 1, y: 0.1 }, bursts: [[0, 30]], vel: { y: [1.2, 2.6] }, noise: { s: 0.5, f: 1, sp: 0.5 },
    color: [[0, PALE], [1, GOLD]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  { name: 'W11 Halo', mat: 'line', mode: 'mesh', mesh: 'halo', fudge: -2, pos: [0, 2.35, 0], size: 1, rot3: [0, 0, 0], rotLife: [0, 3, 0], bursts: [[0.1, 1]], life: 1.4, sizeLife: [[0, 1.8, 0, -4], [0.15, 1]], alpha: [[0, 0], [0.1, 1], [0.75, 0.9], [1, 0]] },
];

// ---------- 4. lose: 틀린 5구역 동시 폭발 (static, P = 경기장 중앙, +Z = 바늘이 멈춘(안전) 구역 가운데 방향 → 나머지 5구역이 터짐) ----------
const SC = [];
for (let k = 1; k <= 5; k++) {
  const a = k * Math.PI / 3, sa = Math.sin(a), ca = Math.cos(a), at = d => [d * sa, 0, d * ca], t0 = 0.025 * k;
  SC.push(
    { name: `X${k}0 WedgeFill`, mat: 'fanR', mode: 'mesh', mesh: 'wedge', fudge: 42, size: 1, rot3: [0, a, 0], bursts: [[t0, 1]], life: 1.3, alpha: [[0, 0], [0.04, 0.95], [0.25, 0.6], [1, 0]] },
    { name: `X${k}1 WedgeEdge`, mat: 'lineR', mode: 'mesh', mesh: 'wedgeEdge', fudge: 40, size: 1, rot3: [0, a, 0], bursts: [[t0, 1]], life: 1.2, alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
    { name: `X${k}2 SweepArc`, mat: 'lineR', mode: 'mesh', mesh: 'wedgeArc', fudge: 38, size: TABLE, rot3: [0, a, 0], bursts: [[t0, 1], [t0 + 0.08, 1]], life: 0.45, sizeLife: [[0, 1], [1, ARENA / TABLE]], alpha: [[0, 1], [0.8, 0.8], [1, 0]] },
    { name: `X${k}3 Crack`, mat: 'crack', mode: 'mesh', mesh: 'quad', fudge: 43, pos: at(11), size: 11, rot3: YAW, bursts: [[t0, 1]], life: 1.5, sizeLife: [[0, 0.4, 0, 4], [0.12, 1]], alpha: [[0, 1], [0.5, 0.75], [1, 0]] });
  [[8, 2.8, 0.9], [14, 4.6, 1.2]].forEach(([d, rr, m], i) => {
    const P = at(d), t = t0 + 0.04 * i, sh = { type: 'circle', r: rr, th: 1, y: 0.1 };
    SC.push(
      { name: `X${k}${i}4 Pillar`, mat: 'pillarR', mode: 'mesh', mesh: 'pillar2', fudge: 7, pos: P, size: rr * 1.1, rot3: YAW, rotLife: [0, 3, 0], bursts: [[t, 1]], life: 0.7, sizeLife: [[0, 2, 0, -6], [0.25, 1], [1, 0.4]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
      { name: `X${k}${i}5 Flames`, mat: 'streakR', mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, pos: P, life: [0.4, 0.65], size: [0.12, 0.24], shape: sh, bursts: [[t, Math.round(20 * m)], [t + 0.1, Math.round(10 * m)]], vel: { y: [8, 14] }, drag: 2.4, alpha: [[0, 1], [1, 0]] },
      { name: `X${k}${i}6 Sparks`, mat: 'dotR', mode: 'stretch', vs: 0.05, ls: 1.3, fudge: -5, pos: P, life: [0.5, 0.9], size: [0.05, 0.1], shape: sh, bursts: [[t, Math.round(22 * m)]], vel: { y: [4, 9], rad: [1, 3] }, drag: 2, gravity: 0.6, color: [[0, [1, 0.85, 0.6]], [0.5, RED], [1, DEEP]], alpha: [[0, 1], [1, 0]] },
      { name: `X${k}${i}7 Chips`, mat: (k + i) % 2 ? 'chipK' : 'chipR', fudge: 10, pos: P, life: [0.8, 1.2], size: [0.32, 0.46], rot: [0, TAU], rotZ: [-8, 8], shape: sh, bursts: [[t, Math.round(7 * m)]], vel: { y: [5, 8], rad: [1, 2.5] }, gravity: 1.4, alpha: [[0, 1], [0.85, 1], [1, 0]] },
      { name: `X${k}${i}8 Shards`, mat: 'shardDR', fudge: 20, pos: P, life: [0.5, 0.8], size: [0.2, 0.36], rot: [0, TAU], rotZ: [-10, 10], shape: sh, bursts: [[t, Math.round(6 * m)]], vel: { y: [4, 7], rad: [1, 3] }, drag: 1, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] },
      { name: `X${k}${i}9 Smoke`, mat: 'mist', fudge: 25, pos: P, life: [1.0, 1.4], size: [1.6 * m, 2.4 * m], rot: [0, TAU], shape: sh, bursts: [[t + 0.05, Math.round(4 * m)]], vel: { y: [0.5, 1.2] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.6], [1, 0]], flip: { x: 4, y: 4 } });
  });
  SC.push({ name: `X${k}F Flash`, mat: 'starR', fudge: -8, pos: [11 * sa, 0.8, 11 * ca], size: 4.5, rot: [0, TAU], bursts: [[t0, 1]], life: 0.22, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] });
  SC.push(Object.assign(suitPop(`X${k}S`, t0 + 0.04, 4, [11 * sa, 0.4, 11 * ca], { type: 'circle', r: 5, th: 1, y: 0 }, { y: [3, 6] })[k % 4], { bursts: [[t0 + 0.04, 4]] }));
}
// 안전 구역 표시: 바늘이 멈춘 구역 테두리가 금빛으로 번쩍 (같은 시스템, +Z 쪽)
SC.push({ name: 'X0 SafeEdge', mat: 'line', mode: 'mesh', mesh: 'wedgeEdge', fudge: 39, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.4, alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  { name: 'X0 SafeFill', mat: 'fanG', mode: 'mesh', mesh: 'wedge', fudge: 41, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.4, alpha: [[0, 0], [0.05, 0.35], [1, 0]] });

// ---------- 5. table: 가운데 테이블 피격 (static, 경기장 중앙, 30% 버전) ----------
const TB = [
  { name: 'T01 Disc', mat: 'fanG', mode: 'mesh', mesh: 'cOuter', fudge: 40, size: TABLE, rot3: YAW, bursts: [[0, 1]], life: 0.8, sizeLife: [[0, 0.6, 0, 2], [0.2, 1]], alpha: [[0, 1], [1, 0]] },
  ...impactBurst('T1', [0, 0, 0], 0, 1.2, { star: 'star', flare: 'flare', wave: 'wave', streak: 'streak', dot: 'dot', shardL: 'shardL', ring: 'line' }, CG, { waveR: TABLE / 1.2, n: 0.8 }),
];

// ---------- 6. trick: 속임수 회차 경고 — 휠 테두리 금빛 반짝임 (attached, 보스, 1.4 s) ----------
const TR = [
  { name: 'K01 RimFlash', mat: 'line', mode: 'mesh', mesh: 'wheelRing', fudge: 1, pos: WHEEL, size: WR * 1.08, rot3: [0, 0, 0], bursts: [[0, 1], [0.35, 1], [0.7, 1]], life: 0.35, sizeLife: [[0, 1], [1, 1.12]], alpha: [[0, 1], [1, 0]] },
  { name: 'K02 RimGlint', mat: 'star', fudge: -9, pos: WHEEL, life: [0.25, 0.4], size: [0.4, 0.7], rot: [0, TAU], shape: { type: 'box', sx: 4.4, sy: 4.4, sz: 0.1 }, bursts: [[0, 6], [0.2, 6], [0.4, 6], [0.6, 6], [0.8, 6]], sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: 'K03 Halo', mat: 'glow', mode: 'mesh', mesh: 'wheelHalo', fudge: 5, pos: WHEEL, size: WR * 1.1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.2, alpha: [[0, 0], [0.2, 0.55], [0.8, 0.45], [1, 0]] },
  { name: 'K04 Coins', mat: 'coin', fudge: 8, pos: WHEEL, life: [0.7, 1.0], size: [0.22, 0.3], rot: [0, TAU], rotZ: [-8, 8], shape: { type: 'box', sx: 4.2, sy: 4.2, sz: 0.1 }, bursts: [[0.05, 10]], speed: [1, 2], gravity: 0.8, alpha: [[0, 1], [0.8, 1], [1, 0]] },
];

// ---------- 7. card: 카드 투사체 + 8. card_hit ----------
const CARD = [
  { name: 'P01 Card', mat: 'card', mode: 'mesh', mesh: 'cardM', fudge: -1, size: 1.3, rot3: [0, 0, 0], rotLife: [0, 22, 0], rate: 30, bursts: [[0, 1]], life: 0.05 },
  { name: 'P02 Glow', mat: 'glowR', fudge: 0, rate: 20, life: 0.15, size: 1.3, alpha: [[0, 0.5], [1, 0]] },
  { name: 'P03 Trail', mat: 'streakR', mode: 'stretch', vs: 0.03, ls: 1.4, fudge: 0, world: true, rateDist: 16, life: [0.18, 0.28], size: [0.14, 0.22], alpha: [[0, 0.8], [1, 0]] },
  { name: 'P04 GoldTrail', mat: 'streak', mode: 'stretch', vs: 0.03, ls: 1.0, fudge: 0, world: true, rateDist: 10, life: [0.15, 0.22], size: [0.05, 0.08], shape: { type: 'sphere', r: 0.3, th: 1 }, alpha: [[0, 1], [1, 0]] },
  { name: 'P05 Suits', mat: 'hearts', fudge: -2, world: true, rateDist: 2, life: [0.4, 0.6], size: [0.16, 0.24], rot: [-0.5, 0.5], shape: { type: 'sphere', r: 0.25, th: 1 }, vel: { y: [0.3, 0.8] }, alpha: [[0, 1], [1, 0]] },
];
const CARD_HIT = [
  { name: 'H01 Slash', mat: 'slashR', mode: 'mesh', mesh: 'sweep', fudge: 4, pos: [0, 0.9, 0], size: 1.6, rot3: [[0, TAU], [0, TAU], [0, TAU]], bursts: [[0, 2]], life: 0.3, flip: { x: 2, y: 8 } },
  ...impactBurst('H1', [0, 0.3, 0], 0, 0.55, { star: 'starR', flare: 'flareR', streak: 'streakR', dot: 'dotR', shardD: 'shardDR' }, CR, { ground: false, n: 0.7 }),
  { name: 'H02 CardShreds', mat: 'card', fudge: 9, pos: [0, 0.8, 0], life: [0.5, 0.8], size: [0.18, 0.28], rot: [0, TAU], rotZ: [-12, 12], shape: { type: 'sphere', r: 0.2, th: 1 }, bursts: [[0, 8]], speed: [3, 5], drag: 1.5, gravity: 1, alpha: [[0, 1], [0.8, 1], [1, 0]] },
];

// ---------- 9. chip_drop: 칩 탑 낙하 착지 (static, 반경 3) ----------
const CHIP = [
  ...magicCircle('C0', 3, 0, 0.12, 1.4, { line: 'lineR', rune: 'rune' }, { star: 'cHex' }),
  ...impactBurst('C1', [0, 0, 0], 0.12, 1.1, BR, CR, { waveR: 2.7 }),
  { name: 'C20 Chips', mat: 'chipR', fudge: 10, pos: [0, 0.4, 0], life: [0.7, 1.1], size: [0.3, 0.45], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'circle', r: 0.8, th: 1, y: 0 }, bursts: [[0.12, 14]], vel: { y: [4, 7], rad: [3, 6] }, gravity: 1.4, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'C21 ChipsK', mat: 'chipK', fudge: 11, pos: [0, 0.4, 0], life: [0.7, 1.1], size: [0.3, 0.45], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'circle', r: 0.8, th: 1, y: 0 }, bursts: [[0.12, 10]], vel: { y: [4, 7], rad: [3, 6] }, gravity: 1.4, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'C22 Coins', mat: 'coin', fudge: 8, pos: [0, 0.4, 0], life: [0.7, 1.0], size: [0.22, 0.3], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'circle', r: 0.8, th: 1, y: 0 }, bursts: [[0.12, 12]], vel: { y: [5, 8], rad: [2, 4] }, gravity: 1.3, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'C23 Crack', mat: 'crack', mode: 'mesh', mesh: 'quad', fudge: 43, pos: [0, 0.025, 0], size: 6, rot3: YAW, bursts: [[0.12, 1]], life: 1.4, sizeLife: [[0, 0.4, 0, 4], [0.12, 1]], alpha: [[0, 1], [0.5, 0.75], [1, 0]] },
  { name: 'C24 Dust', mat: 'dust', fudge: 22, life: [0.7, 1.0], size: [1.0, 1.6], rot: [0, TAU], shape: { type: 'circle', r: 1.6, th: 1, y: 0.3 }, bursts: [[0.13, 10]], vel: { rad: [1.5, 2.5], y: [0.2, 0.6] }, drag: 2, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
];

// ---------- 10. dice_trail: 굴러가는 큰 주사위 꼬리 (attached, 주사위 소품, 반복) ----------
const DT = [
  { name: 'D01 Dust', mat: 'dust', fudge: 22, world: true, rateDist: 3, life: [0.7, 1.0], size: [0.9, 1.5], rot: [0, TAU], shape: { type: 'circle', r: 1.4, th: 1, y: 0.2 }, vel: { y: [0.3, 0.8] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.5], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.3 },
  { name: 'D02 GroundLine', mat: 'streakR', mode: 'stretch', vs: 0.0, ls: 1, fudge: 0, world: true, rateDist: 6, life: [0.3, 0.45], size: [0.3, 0.45], shape: { type: 'circle', r: 1.3, th: 0.2, y: 0.08 }, vel: { y: [0.2, 0.5] }, alpha: [[0, 0.8], [1, 0]], onStop: 0.2 },
  { name: 'D03 Sparks', mat: 'dotR', mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, world: true, rateDist: 10, life: [0.3, 0.5], size: [0.05, 0.09], shape: { type: 'circle', r: 1.5, th: 0.2, y: 0.1 }, speed: [2, 4], vel: { y: [2, 4] }, gravity: 0.8, color: [[0, [1, 0.85, 0.6]], [1, RED]], alpha: [[0, 1], [1, 0]], onStop: 0.2 },
  { name: 'D04 Pips', mat: 'dot', fudge: -6, world: true, rateDist: 2, life: [0.6, 0.9], size: [0.14, 0.2], shape: { type: 'sphere', r: 1.5, th: 0 }, pos: [0, 1.5, 0], vel: { y: [0.6, 1.2] }, color: [[0, PALE], [1, RED]], alpha: [[0, 1], [1, 0]], onStop: 0.2 },
];

// ---------- 11. pip_1 ~ pip_6: 주사위 눈 수만큼 고르게 방사 (static, P = 멈춘 주사위 발밑, +Z = 첫 줄 방향, 줄 길이 16 m 폭 2 m) ----------
function laneLayers(tag, a, t0, m) {
  const sa = Math.sin(a), ca = Math.cos(a), at = (d, y) => [d * sa, y ?? 0, d * ca], L = [
    { name: `${tag}0 Fill`, mat: m.band, mode: 'mesh', mesh: m.lane, fudge: 42, size: 1, rot3: [0, a, 0], bursts: [[t0, 1]], life: 0.9, alpha: [[0, 0], [0.05, 0.8], [0.3, 0.45], [1, 0]] },
    { name: `${tag}1 Edge`, mat: m.line, mode: 'mesh', mesh: m.edge, fudge: 40, size: 1, rot3: [0, a, 0], bursts: [[t0, 1]], life: 0.9, alpha: [[0, 1], [0.6, 0.6], [1, 0]] }];
  if (m.slash) L.push({ name: `${tag}2 Slash`, mat: m.slash, mode: 'mesh', mesh: m.slashMesh, fudge: 5, size: 1, rot3: [0, a, 0], bursts: [[t0, 1]], life: 0.35, flip: { x: 2, y: 8 } });
  m.pts.forEach(([d, rr], i) => {
    const sh = { type: 'circle', r: rr, th: 1, y: 0.1 }, t = t0 + 0.03 * i;
    L.push({ name: `${tag}${i}3 Rays`, mat: m.streak, mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, pos: at(d), life: [0.35, 0.55], size: [0.1, 0.18], shape: sh, bursts: [[t, m.nRay]], vel: { y: [8, 13] }, drag: 2.4, alpha: [[0, 1], [1, 0]] },
      { name: `${tag}${i}4 Sparks`, mat: m.dot, mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: at(d, 0.2), life: [0.4, 0.7], size: [0.05, 0.09], shape: sh, bursts: [[t, m.nRay]], vel: { y: [3, 7] }, speed: [1, 3], gravity: 0.6, color: m.col, alpha: [[0, 1], [1, 0]] },
      { name: `${tag}${i}5 Bits`, mat: m.bit, fudge: 9, pos: at(d, 0.3), life: [0.6, 0.9], size: m.bitSize, rot: [0, TAU], rotZ: [-6, 6], shape: sh, bursts: [[t, m.nBit]], vel: { y: [4, 6] }, gravity: 1.2, alpha: [[0, 1], [0.8, 1], [1, 0]] });
    if (i % 2 === 0) L.push({ name: `${tag}${i}6 Smoke`, mat: 'mist', fudge: 25, pos: at(d, 0.4), life: [0.9, 1.2], size: [1.2, 1.8], rot: [0, TAU], shape: sh, bursts: [[t + 0.04, 3]], vel: { y: [0.4, 1] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } });
  });
  return L;
}
const PIPM = { band: 'bandR', lane: 'lane', edge: 'laneEdge', line: 'lineR', streak: 'streakR', dot: 'dotR', bit: 'die', bitSize: [0.45, 0.6], nRay: 10, nBit: 2,
  col: [[0, [1, 0.85, 0.6]], [1, DEEP]], pts: [[3, 1.1], [7, 1.1], [11, 1.1], [15, 1.1]] };
const PIPS = [1, 2, 3, 4, 5, 6].map(n => {
  const L = [{ name: 'N00 HeadFlash', mat: 'starR', fudge: -8, pos: [0, 0.8, 0], size: 3, rot: [0, TAU], bursts: [[0, 1]], life: 0.22, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
             { name: 'N01 HeadRing', mat: 'lineR', mode: 'mesh', mesh: 'cHex', fudge: 39, size: 2, rot3: YAW, bursts: [[0, 1]], life: 0.6, sizeLife: [[0, 0.5, 0, 3], [1, 1.2]], alpha: [[0, 1], [1, 0]] }];
  for (let k = 0; k < n; k++) L.push(...laneLayers(`N${k + 1}`, k * TAU / n, 0.02 * k, PIPM));
  return L;
});

// ---------- 12/13. dealer_cross (십자) / dealer_x (X자) — static, 보스 발밑, 팔 길이 16 m 폭 3 m ----------
const DHM = { band: 'bandG', lane: 'laneW', edge: 'laneWEdge', line: 'line', streak: 'streak', dot: 'dot', bit: 'card', bitSize: [0.4, 0.55], nRay: 9, nBit: 2,
  slash: 'slash', slashMesh: 'laneSlash', col: [[0, PALE], [1, AMBER]], pts: [[4, 1.5], [8, 1.5], [12, 1.5], [15.5, 1.5]] };
function dealer(off) {
  const L = [
    { name: 'G01 Hub', mat: 'line', mode: 'mesh', mesh: 'cHex', fudge: 39, size: 2.6, rot3: [0, off, 0], rotLife: [0, 2, 0], bursts: [[0, 1]], life: 0.9, sizeLife: [[0, 1.4, 0, -3], [0.2, 1]], alpha: [[0, 1], [1, 0]] },
    { name: 'G02 HubFlash', mat: 'star', fudge: -8, pos: [0, 1, 0], size: 4, rot: [0, TAU], bursts: [[0, 1]], life: 0.22, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
    { name: 'G03 HubFlare', mat: 'flare', fudge: -7, pos: [0, 1, 0], size: 5, rot: [0, TAU], bursts: [[0, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.7], [1, 0]] }];
  for (let k = 0; k < 4; k++) L.push(...laneLayers(`G${k + 1}`, off + k * Math.PI / 2, 0, DHM));
  return L;
}
const DCROSS = dealer(0), DX = dealer(Math.PI / 4);

// ---------- 13/14. coin_heads (안쪽 원 r8) / coin_tails (바깥 고리 8~18) — static, 보스 발밑 ----------
function coinZone(tag, fill, edge, inner) {
  const L = [
    { name: `${tag}01 Fill`, mat: 'fanG', mode: 'mesh', mesh: fill, fudge: 42, size: 1, rot3: YAW, bursts: [[0, 1]], life: 1.2, alpha: [[0, 0], [0.04, 0.6], [0.3, 0.32], [1, 0]] },
    { name: `${tag}02 Edge`, mat: 'line', mode: 'mesh', mesh: edge, fudge: 40, size: 1, rot3: YAW, rotLife: [0, 0.4, 0], bursts: [[0, 1]], life: 1.2, alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
    { name: `${tag}03 Wave`, mat: 'wave', mode: 'mesh', mesh: 'quad', fudge: 30, pos: [0, 0.07, 0], size: inner ? 20 : 44, rot3: YAW, bursts: [[0, 1]], life: 0.55, sizeLife: [[0, inner ? 0.1 : 0.4, 0, 2], [1, 1]], alpha: [[0, 0.7], [1, 0]] },
  ];
  const ring = inner ? { type: 'circle', r: 7.5, th: 1, y: 0.1 } : { type: 'circle', r: 17.5, th: 0.55, y: 0.1 };
  L.push(
    { name: `${tag}04 Rays`, mat: 'streak', mode: 'stretch', vs: 0.06, ls: 3.5, fudge: -2, life: [0.4, 0.65], size: [0.1, 0.2], shape: ring, bursts: [[0.02, inner ? 50 : 110]], vel: { y: [8, 14] }, drag: 2.3, alpha: [[0, 1], [1, 0]] },
    { name: `${tag}05 Coins`, mat: 'coin', fudge: 8, pos: [0, 0.4, 0], life: [0.8, 1.2], size: [0.32, 0.45], rot: [0, TAU], rotZ: [-8, 8], shape: ring, bursts: [[0.02, inner ? 22 : 44]], vel: { y: [5, 8] }, gravity: 1.3, alpha: [[0, 1], [0.85, 1], [1, 0]] },
    { name: `${tag}06 Sparks`, mat: 'dot', mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, life: [0.5, 0.8], size: [0.05, 0.1], shape: ring, bursts: [[0.02, inner ? 50 : 100]], vel: { y: [3, 7] }, speed: [1, 3], gravity: 0.6, color: [[0, PALE], [0.5, GOLD], [1, AMBER]], alpha: [[0, 1], [1, 0]] },
    { name: `${tag}07 Pillars`, mat: 'pillar', mode: 'mesh', mesh: 'pillar5', fudge: 7, size: 1.2, rot3: YAW, shape: ring, bursts: [[0.02, inner ? 6 : 12]], life: 0.6, sizeLife: [[0, 1.6, 0, -5], [0.3, 1], [1, 0.4]], alpha: [[0, 1], [1, 0]] },
    { name: `${tag}08 Shards`, mat: 'shardD', fudge: 20, pos: [0, 0.4, 0], life: [0.5, 0.8], size: [0.2, 0.36], rot: [0, TAU], rotZ: [-10, 10], shape: ring, bursts: [[0.02, inner ? 14 : 28]], vel: { y: [4, 7] }, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] },
    { name: `${tag}09 Smoke`, mat: 'mist', fudge: 25, life: [1.0, 1.4], size: [1.6, 2.4], rot: [0, TAU], shape: ring, bursts: [[0.05, inner ? 8 : 16]], vel: { y: [0.4, 1] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 } });
  return L;
}
const HEADS = coinZone('O', 'disc8', 'disc8Edge', true), TAILS = coinZone('Q', 'annulus', 'annulusEdge', false);

// ---------- 15. all_in: 올인 (static, 보스 발밑, 앞 반원 r10) ----------
const ALLIN = [
  { name: 'A01 HalfFill', mat: 'fanR', mode: 'mesh', mesh: 'half10', fudge: 42, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.3, alpha: [[0, 0], [0.04, 0.95], [0.3, 0.55], [1, 0]] },
  { name: 'A02 HalfEdge', mat: 'lineR', mode: 'mesh', mesh: 'half10Edge', fudge: 40, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.2, alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
  { name: 'A03 Sweep', mat: 'slashR', mode: 'mesh', mesh: 'sweep', fudge: 4, pos: [0, 0.3, 0], size: 9.5, rot3: [0, -Math.PI / 2, 0], rotLife: [0, -2.5, 0], bursts: [[0, 1]], life: 0.4, flip: { x: 2, y: 8 } },
  { name: 'A04 Sweep2', mat: 'slash', mode: 'mesh', mesh: 'sweep', fudge: 5, pos: [0, 0.9, 0], size: 7.5, rot3: [0, -Math.PI / 2, 0], rotLife: [0, -2.8, 0], bursts: [[0.04, 1]], life: 0.36, flip: { x: 2, y: 8 } },
  { name: 'A05 Crack', mat: 'crack', mode: 'mesh', mesh: 'quad', fudge: 43, pos: [0, 0.03, 4], size: 12, rot3: YAW, bursts: [[0, 1]], life: 1.6, sizeLife: [[0, 0.4, 0, 4], [0.12, 1]], alpha: [[0, 1], [0.5, 0.75], [1, 0]] },
  ...impactBurst('A1', [0, 0, 3], 0, 2.0, BR, CR, { waveR: 3.5, n: 1.4 }),
  { name: 'A20 ChipAvalanche', mat: 'chipR', fudge: 10, pos: [0, 0.4, 3], life: [0.8, 1.2], size: [0.35, 0.5], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'box', sx: 14, sy: 0.2, sz: 6 }, bursts: [[0, 24]], vel: { y: [5, 8], z: [2, 5] }, gravity: 1.4, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'A21 ChipAvalancheK', mat: 'chipK', fudge: 11, pos: [0, 0.4, 3], life: [0.8, 1.2], size: [0.35, 0.5], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'box', sx: 14, sy: 0.2, sz: 6 }, bursts: [[0, 18]], vel: { y: [5, 8], z: [2, 5] }, gravity: 1.4, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'A22 Coins', mat: 'coin', fudge: 8, pos: [0, 0.4, 5], life: [0.8, 1.2], size: [0.3, 0.42], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'box', sx: 16, sy: 0.2, sz: 8 }, bursts: [[0.03, 30]], vel: { y: [6, 9] }, gravity: 1.3, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'A23 Rays', mat: 'streakR', mode: 'stretch', vs: 0.06, ls: 3.5, fudge: -2, pos: [0, 0.1, 5], life: [0.4, 0.65], size: [0.12, 0.22], shape: { type: 'box', sx: 18, sy: 0.1, sz: 9 }, bursts: [[0.02, 60]], vel: { y: [9, 15] }, drag: 2.3, alpha: [[0, 1], [1, 0]] },
  ...suitPop('A3', 0.04, 20, [0, 0.4, 5], { type: 'box', sx: 16, sy: 0.2, sz: 8 }, { y: [3, 6] }),
];

// ---------- 16. recall: 칩 회수 (link: source = 바닥 칩, target = 보스 가슴) ----------
const lin = (a, b) => [[0, a, 0, b - a], [1, b, b - a, 0]];
const RC = [
  { name: 'R01 Chip', mat: 'chipR', fudge: 10, life: 0.5, size: 0.45, rot: [0, TAU], rotZ: [8, 12], bursts: [[0, 1]], path: { progress: [[0, 0, 0, 0.4], [1, 1, 3, 0]], bend: { x: [-0.3, 0.3], y: [0.25, 0.45] } } },
  { name: 'R02 Glow', mat: 'glowR', fudge: 0, life: 0.5, size: 1, bursts: [[0, 1]], path: { progress: [[0, 0, 0, 0.4], [1, 1, 3, 0]], bend: { x: [-0.3, 0.3], y: [0.25, 0.45] } }, alpha: [[0, 0.5], [1, 0.2]] },
  { name: 'R03 Streak', mat: 'streakR', mode: 'stretch', vs: 0.04, ls: 1.4, fudge: -1, life: 0.5, size: [0.1, 0.16], bursts: [[0, 6]], path: { progress: [[0, 0, 0, 0.4], [1, 1, 3, 0]], bend: { x: [-0.3, 0.3], y: [0.25, 0.45] } }, alpha: [[0, 0.9], [1, 0.3]] },
  { name: 'R04 Lift', mat: 'dotR', mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, life: [0.3, 0.5], size: [0.05, 0.08], shape: { type: 'sphere', r: 0.3, th: 1 }, bursts: [[0, 12]], path: { progress: [[0, 0, 0, 0], [1, 0.08, 0, 0]] }, sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 1], [1, 0]] },
  { name: 'R05 Arrive', mat: 'flareR', fudge: -6, life: 0.3, size: 1.6, rot: [0, TAU], bursts: [[0.48, 1]], path: { progress: [[0, 1, 0, 0], [1, 1, 0, 0]] }, sizeLife: [[0, 0.3], [0.3, 1], [1, 1.1]], alpha: [[0, 0.8], [1, 0]] },
];

// ---------- 17. groggy (attached, 보스, 반복) / 18. enrage (static, 보스) / 19. enrage_aura (attached, 반복) ----------
const GROG = [
  { name: 'Y01 Orbit', mat: 'chipR', fudge: 10, pos: [0, HEAD, 0], life: 2.4, size: 0.45, rot: [0, TAU], rotZ: [3, 4], shape: { type: 'circle', r: 1.4, th: 0, y: 0 }, rate: 2.5, bursts: [[0, 5]], vel: { orb: [2.6, 2.6] }, alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]], onStop: 0.2 },
  { name: 'Y02 OrbitStars', mat: 'star', fudge: -8, pos: [0, HEAD + 0.3, 0], life: 2.4, size: [0.5, 0.7], shape: { type: 'circle', r: 1.2, th: 0, y: 0 }, rate: 2.5, bursts: [[0, 5]], vel: { orb: [-2.2, -2.2] }, sizeLife: [[0, 0.6], [0.25, 1.1], [0.5, 0.7], [0.75, 1.1], [1, 0.6]], alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]], onStop: 0.2 },
  { name: 'Y03 Suits', mat: 'spade', fudge: 12, pos: [0, HEAD + 0.6, 0], life: 2.4, size: 0.4, rot: [-0.3, 0.3], shape: { type: 'circle', r: 1.0, th: 0, y: 0 }, rate: 1.2, bursts: [[0, 3]], vel: { orb: [3, 3] }, alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]], onStop: 0.2 },
  { name: 'Y04 Halo', mat: 'line', mode: 'mesh', mesh: 'cOuter', fudge: -1, pos: [0, HEAD + 0.1, 0], size: 1.5, rot3: [0, 0, 0], rotLife: [0, symW(1, 2), 0], rate: 1 / 1.7, life: 2, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 0.6], [0.85, 0.6], [1, 0]], onStop: 0.2 },
  { name: 'Y05 Smoke', mat: 'mist', fudge: 25, pos: [0, HEAD - 0.5, 0], life: [1.4, 1.8], size: [0.8, 1.2], rot: [0, TAU], shape: { type: 'sphere', r: 1.2, th: 1 }, rate: 3, vel: { y: [0.3, 0.6] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.3, 0.4], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.3 },
];
const ENR = [
  ...magicCircle('E0', 7, 0, 0.6, 2.4, MR),
  { name: 'E10 Implode', mat: 'lineR', mode: 'mesh', mesh: 'wheelRing', fudge: 1, pos: WHEEL, size: 5, rot3: [0, 0, 0], rotLife: [0, 0, -12], bursts: [[0, 1]], life: 0.6, sizeLife: [[0, 1.6], [1, 0.4]], alpha: [[0, 0], [0.3, 1], [1, 1]] },
  { name: 'E11 InStreak', mat: 'streakR', mode: 'stretch', vs: 0.08, ls: 1.5, fudge: -4, pos: WHEEL, life: 0.55, size: [0.06, 0.1], shape: { type: 'sphere', r: 5, th: 0 }, bursts: [[0, 20], [0.2, 24]], vel: { rad: [-9, -8] }, alpha: [[0, 0.2], [1, 1]] },
  ...impactBurst('E2', [0, 0, 0], 0.6, 2.6, BR, CR, { waveR: 3.5, n: 1.6 }),
  { name: 'E30 Column', mat: 'pillarR', mode: 'mesh', mesh: 'pillar4', fudge: 7, size: 4, rot3: YAW, rotLife: [0, 2, 0], bursts: [[0.6, 1]], life: 1.2, sizeLife: [[0, 2.5, 0, -5], [0.2, 1.4], [1, 0.6]], alpha: [[0, 1], [0.5, 0.75], [1, 0]] },
  { name: 'E31 GoldColumn', mat: 'pillar', mode: 'mesh', mesh: 'pillar8', fudge: 6, size: 2, rot3: YAW, rotLife: [0, -3, 0], bursts: [[0.6, 1]], life: 0.9, sizeLife: [[0, 2, 0, -5], [0.2, 1], [1, 0.4]], alpha: [[0, 1], [1, 0]] },
  { name: 'E32 WheelBurst', mat: 'flareR', fudge: -7, pos: WHEEL, size: 9, rot: [0, TAU], bursts: [[0.6, 1]], life: 0.6, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.2]], alpha: [[0, 0.8], [1, 0]] },
  { name: 'E33 WheelRings', mat: 'line', mode: 'mesh', mesh: 'wheelRing', fudge: 0, pos: WHEEL, size: WR, rot3: [0, 0, 0], bursts: [[0.6, 1], [0.7, 1], [0.8, 1]], life: 0.5, sizeLife: [[0, 1], [1, 2.6]], alpha: [[0, 1], [1, 0]] },
  { name: 'E34 Chips', mat: 'chipR', fudge: 10, pos: [0, 3, 0], life: [1.0, 1.5], size: [0.4, 0.6], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'sphere', r: 1, th: 1 }, bursts: [[0.6, 30]], speed: [7, 12], drag: 1.2, gravity: 1, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'E35 Coins', mat: 'coin', fudge: 8, pos: [0, 3, 0], life: [1.0, 1.5], size: [0.3, 0.45], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'sphere', r: 1, th: 1 }, bursts: [[0.6, 30]], speed: [7, 12], drag: 1.2, gravity: 1, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  ...suitPop('E4', 0.62, 28, [0, 3, 0], { type: 'sphere', r: 1, th: 1 }, { rad: [5, 9], y: [1, 3] }),
];
const ENA = [
  heldLoop('V01 Ring', 'lineR', 'cOuter', 40, 0.4, 0.9, 4.5, [0, 0, 0], 4),
  heldLoop('V02 Rune', 'runeR', 'cRune', 39, symW(14, 4, -1), 0.8, 4.5, [0, 0, 0], 4),
  { name: 'V03 Embers', mat: 'dotR', fudge: -7, life: [1.2, 1.8], size: [0.05, 0.09], shape: { type: 'circle', r: 3.5, th: 1, y: 0.2 }, rate: 30, bursts: [[0, 15]], vel: { y: [1.2, 2.6] }, noise: { s: 0.6, f: 1, sp: 0.5 },
    color: [[0, [1, 0.85, 0.6]], [0.6, RED], [1, DEEP]], alpha: [[0, 0], [0.1, 1], [1, 0]], onStop: 0.3 },
  { name: 'V04 WheelFire', mat: 'streakR', mode: 'stretch', vs: 0.06, ls: 2, fudge: -3, pos: WHEEL, life: [0.35, 0.55], size: [0.08, 0.14], shape: { type: 'box', sx: 4.6, sy: 4.6, sz: 0.2 }, rate: 40, vel: { y: [3, 6], space: 'world' }, alpha: [[0, 1], [1, 0]], onStop: 0.15 },
  { name: 'V05 WheelHalo', mat: 'glowR', mode: 'mesh', mesh: 'wheelHalo', fudge: 6, pos: WHEEL, size: WR * 1.1, rot3: [0, 0, 0], rotLife: [0, 0, -4], rate: 1.5, life: 1.2, bursts: [[0, 2]], alpha: [[0, 0], [0.3, 0.5], [1, 0]], onStop: 0.2 },
  { name: 'V06 Smoke', mat: 'mist', fudge: 25, life: [1.4, 2.0], size: [1.2, 1.8], rot: [0, TAU], shape: { type: 'circle', r: 3, th: 1, y: 0.3 }, rate: 5, vel: { y: [0.4, 0.8] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.3, 0.45], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.3 },
];

const MATS = {
  line: { tex: 'T_Line', tint: [2.6, 2.05, 0.95], add: true }, lineR: { tex: 'T_Line', tint: [2.6, 0.6, 0.38], add: true },
  rune: { tex: 'T_Rune', tint: [2.3, 1.8, 0.8], add: true }, runeR: { tex: 'T_Rune', tint: [2.4, 0.6, 0.35], add: true },
  sigil: { tex: 'T_Sigil', tint: [2.2, 1.7, 0.8], add: true }, sigilR: { tex: 'T_Sigil', tint: [2.3, 0.55, 0.3], add: true },
  glow: { tex: 'T_Glow', tint: [1.3, 0.95, 0.4], add: true }, glowR: { tex: 'T_Glow', tint: [1.4, 0.3, 0.15], add: true },
  star: { tex: 'T_Star', tint: [2.8, 2.3, 1.4], add: true }, starR: { tex: 'T_Star', tint: [2.8, 1.1, 0.7], add: true },
  flare: { tex: 'T_Flare', tint: [2.2, 1.8, 0.9], add: true }, flareR: { tex: 'T_Flare', tint: [2.3, 0.75, 0.4], add: true },
  wave: { tex: 'T_Wave', tint: [1.9, 1.5, 0.7], add: true }, waveR: { tex: 'T_Wave', tint: [1.9, 0.5, 0.25], add: true },
  streak: { tex: 'T_Streak', tint: [2.6, 2.1, 1.1], add: true }, streakR: { tex: 'T_Streak', tint: [2.6, 0.85, 0.45], add: true },
  dot: { tex: 'T_Dot', tint: [2.4, 2.0, 1.2], add: true }, dotR: { tex: 'T_Dot', tint: [2.4, 1.1, 0.6], add: true },
  pillar: { tex: 'T_Pillar', tint: [2.2, 1.8, 0.9], add: true }, pillarR: { tex: 'T_Pillar', tint: [2.2, 0.6, 0.35], add: true },
  fanR: { tex: 'T_FanFill', tint: [1.6, 0.25, 0.15], add: true }, fanG: { tex: 'T_FanFill', tint: [1.3, 0.78, 0.2], add: true },
  bandR: { tex: 'T_Band', tint: [1.8, 0.35, 0.2], add: true }, bandG: { tex: 'T_Band', tint: [1.5, 0.95, 0.28], add: true },
  shardD: { tex: 'T_ShardDark', tint: [2.2, 1.6, 0.7], add: false }, shardDR: { tex: 'T_ShardDark', tint: [2.2, 0.7, 0.4], add: false },
  shardL: { tex: 'T_ShardLight', tint: [2.3, 1.9, 0.9], add: true }, crack: { tex: 'T_Crack', tint: [2.3, 0.6, 0.25], add: true },
  mist: { tex: 'T_DarkMist_Sheet', tint: [1, 1, 1], add: false }, dust: { tex: 'T_Dust_Sheet', tint: [1.4, 1.1, 0.6], add: true },
  slash: { tex: 'T_Slash_Sheet', tint: [2.3, 1.9, 1.0], add: true }, slashR: { tex: 'T_Slash_Sheet', tint: [2.4, 0.75, 0.45], add: true },
  card: { tex: 'T_Card', tint: [1.15, 1.1, 1.05], add: false }, chipR: { tex: 'T_Chip', tint: [1.3, 0.3, 0.28], add: false }, chipK: { tex: 'T_Chip', tint: [0.42, 0.4, 0.5], add: false },
  coin: { tex: 'T_Coin', tint: [1.6, 1.25, 0.55], add: false }, die: { tex: 'T_Die', tint: [1.15, 1.1, 1.05], add: false },
  spade: { tex: 'T_Spade', tint: [0.3, 0.27, 0.36], add: false }, club: { tex: 'T_Club', tint: [0.3, 0.27, 0.36], add: false },
  hearts: { tex: 'T_Hearts', tint: [2.4, 0.5, 0.4], add: true }, diamond: { tex: 'T_Diamond', tint: [2.4, 0.6, 0.45], add: true },
};

// ---------- 데모 ----------
const BOSS = { path: [[0, [0, 0, 0], 0]], hidden: true };
const cam = (t, d, yaw, pitch) => ({ target: t, dist: d, yaw, pitch });
const SCENE = {
  name: 'Roulette_Boss', bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.7, tint: [1, 0.85, 0.7] }, tonemapping: 'neutral',
  systems: [
    { id: 'spin', role: 'attached', loop: true, duration: 2, layers: SPIN },
    { id: 'betlock', role: 'static', loop: false, duration: 0.2, layers: BL },
    { id: 'win', role: 'attached', loop: false, duration: 0.8, layers: WIN },
    { id: 'lose', role: 'static', loop: false, duration: 0.2, layers: SC },
    { id: 'table', role: 'static', loop: false, duration: 0.2, layers: TB },
    { id: 'trick', role: 'attached', loop: false, duration: 1.0, layers: TR },
    { id: 'card', role: 'projectile', loop: true, duration: 1.0, layers: CARD },
    { id: 'card_hit', role: 'impact', loop: false, duration: 0.2, layers: CARD_HIT },
    { id: 'chip_drop', role: 'static', loop: false, duration: 0.3, layers: CHIP },
    { id: 'dice_trail', role: 'attached', loop: true, duration: 2, layers: DT },
    ...PIPS.map((L, i) => ({ id: `pip_${i + 1}`, role: 'static', loop: false, duration: 0.2, layers: L })),
    { id: 'dealer_cross', role: 'static', loop: false, duration: 0.2, layers: DCROSS },
    { id: 'dealer_x', role: 'static', loop: false, duration: 0.2, layers: DX },
    { id: 'coin_heads', role: 'static', loop: false, duration: 0.2, layers: HEADS },
    { id: 'coin_tails', role: 'static', loop: false, duration: 0.2, layers: TAILS },
    { id: 'all_in', role: 'static', loop: false, duration: 0.2, layers: ALLIN },
    { id: 'recall', role: 'link', loop: false, duration: 0.2, layers: RC },
    { id: 'groggy', role: 'attached', loop: true, duration: 2, layers: GROG },
    { id: 'enrage', role: 'static', loop: false, duration: 1.0, layers: ENR },
    { id: 'enrage_aura', role: 'attached', loop: true, duration: 2, layers: ENA },
  ],
  demo: Object.assign({}, DEMO_BG, { interval: 8, dummy: { height: 1.8 },
    scenarios: {
      roulette: { label: '룰렛: 회전 → 속임수 → 마감 → 적중 / 나머지 5구역 폭발', path: [[0, [0, 0, 0], 0]],
        actors: [BOSS, { path: [[0, [0, 0, 11], 0]] }],
        events: [{ t: 0.1, system: 'spin', actor: 0 }, { t: 2.0, system: 'trick', actor: 0 }, { t: 3.6, stop: ['spin'] }, { t: 3.6, system: 'betlock', actor: 0 },
          { t: 4.6, system: 'win', actor: 1 }, { t: 5.4, system: 'lose', actor: 0 }, { t: 5.4, system: 'table', actor: 0 }],
        camera: cam([0, 3, 2], 34, -90, 38) },
      cards: { label: '카드 날리기', path: [[0, [0, 0, 0], 0]], actors: [BOSS],
        events: [-30, -15, 0, 15, 30].map((a, i) => ({ t: 0.2 + i * 0.03, system: 'card', actor: 0, from: [0, 4.2, 1.2], to: [18 * Math.sin(a * D2R), 0.9, 18 * Math.cos(a * D2R)], normal: null, flight: 0.45, impact: 'card_hit' }))
          .concat([-22, -8, 8, 22].map((a, i) => ({ t: 1.0 + i * 0.03, system: 'card', actor: 0, from: [0, 4.2, 1.2], to: [18 * Math.sin(a * D2R), 0.9, 18 * Math.cos(a * D2R)], normal: null, flight: 0.45, impact: 'card_hit' }))),
        camera: cam([0, 2, 8], 26, -60, 30) },
      chips: { label: '칩 탑 낙하 ×4', path: [[0, [0, 0, 0], 0]], actors: [BOSS, ...[[3, 5], [-4, 7], [5, 10], [-2, 12]].map(([x, z]) => ({ path: [[0, [x, 0, z], 0]], hidden: true }))],
        events: [1, 2, 3, 4].map((a, i) => ({ t: 0.3 + i * 0.3, system: 'chip_drop', actor: a })), camera: cam([0, 1, 8], 22, -70, 30) },
      dice: { label: '주사위 굴리기 → 눈 3 → 눈 6', path: [[0, [0, 0, 0], 0]],
        actors: [BOSS, { path: [[0, [-12, 0, 8], 90], [2.0, [6, 0, 8], 90]], hidden: true }, { path: [[0, [6, 0, 8], 0]], hidden: true }],
        events: [{ t: 0, system: 'dice_trail', actor: 1 }, { t: 2.0, stop: ['dice_trail'] }, { t: 2.3, system: 'pip_3', actor: 2 }, { t: 4.3, system: 'pip_6', actor: 2 }],
        camera: cam([0, 1, 8], 30, -90, 40) },
      dealer: { label: '딜러의 손: 십자 → X자', path: [[0, [0, 0, 0], 0]], actors: [BOSS],
        events: [{ t: 0.3, system: 'dealer_cross', actor: 0 }, { t: 1.6, system: 'dealer_x', actor: 0 }], camera: cam([0, 1, 0], 34, -90, 50) },
      coin: { label: '동전: 앞면(안쪽) → 뒷면(바깥)', path: [[0, [0, 0, 0], 0]], actors: [BOSS],
        events: [{ t: 0.3, system: 'coin_heads', actor: 0 }, { t: 2.6, system: 'coin_tails', actor: 0 }], camera: cam([0, 1, 0], 40, -90, 45) },
      allin: { label: '올인', path: [[0, [0, 0, 0], 0]], actors: [BOSS], events: [{ t: 0.3, system: 'all_in', actor: 0 }], camera: cam([0, 1, 4], 24, -55, 32) },
      recall: { label: '칩 회수', path: [[0, [0, 0, 0], 0]], actors: [BOSS, ...[[6, 6], [-7, 5], [3, 10], [-4, 11], [8, 2], [-9, 1]].map(([x, z]) => ({ path: [[0, [x, 0, z], 0]], hidden: true }))],
        events: [1, 2, 3, 4, 5, 6].map((a, i) => ({ t: 0.2 + i * 0.08, system: 'recall', actor: a, from: [0, 0.2, 0], to: 0 })), targetOffset: [0, 4, 0], camera: cam([0, 3, 4], 22, -70, 24) },
      state: { label: '그로기 → 광폭화', path: [[0, [0, 0, 0], 0]], actors: [BOSS],
        events: [{ t: 0, system: 'groggy', actor: 0 }, { t: 3.0, stop: ['groggy'] }, { t: 3.2, system: 'enrage', actor: 0 }, { t: 4.4, system: 'enrage_aura', actor: 0 }],
        camera: cam([0, 4, 0], 22, -60, 18) },
    } }),
};
