// ===== Somnia_Boss — 몽마 솜니아 보스 패턴 VFX (한 팩) =====
// P = 지정 위치 발밑, +Z = 서버가 넘기는 수평 방향. 크기는 기획 수치로 고정(1블록 = 1 m). 보스는 떠 있음(가슴 약 4.6 m, 머리 약 6.1 m).
const LAV = [0.82, 0.68, 1], VIO = [0.55, 0.3, 0.95], CYAN = [0.55, 0.9, 1], MOONC = [1, 0.95, 0.78], DEEPV = [0.25, 0.08, 0.45], WHITE = [1, 1, 1], STARG = [1, 0.86, 0.5];
const CV = { hot: WHITE, mid: LAV, low: VIO }, CC = { hot: WHITE, mid: CYAN, low: [0.3, 0.45, 1] }, CN = { hot: [0.9, 0.7, 1], mid: VIO, low: DEEPV };
const CHEST = 4.6, HEADY = 6.1, ARENA = 16;
const MV = { line: 'line', rune: 'rune', sigil: 'sigil', glow: 'glow' }, MC = { line: 'lineC', rune: 'runeC', sigil: 'sigilC', glow: 'glowC' };
const BV = { star: 'star', flare: 'flare', glow: 'glow', wave: 'wave', streak: 'streak', dot: 'dot', shardL: 'shardL', ring: 'line', motes: 'dot' };
const BN = { star: 'starN', flare: 'flareN', glow: 'glowN', wave: 'waveN', streak: 'streakN', dot: 'dotN', shardD: 'shardD', mist: 'mist', ring: 'lineN', motes: 'dotN' };
const FWD = -Math.PI / 2;
const featherBurst = (tag, t, n, pos, sp, o) => Object.assign({ name: `${tag} Feathers`, mat: 'feather', fudge: -1, pos, life: [1.0, 1.6], size: [0.26, 0.42], rot: [0, TAU], rotZ: [-3, 3],
  shape: { type: 'sphere', r: 0.4, th: 1 }, bursts: [[t, n]], speed: sp, drag: 3, gravity: 0.12, noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.08, 1], [0.7, 0.85], [1, 0]] }, o || {});
const zzz = (tag, t, n, pos, o) => Object.assign({ name: `${tag} Zzz`, mat: 'zzz', fudge: -4, pos, life: [1.2, 1.6], size: [0.35, 0.6], rot: [-0.4, 0.2], shape: { type: 'sphere', r: 0.5, th: 1 },
  bursts: [[t, n]], vel: { y: [0.8, 1.4], x: [0.2, 0.5] }, noise: { s: 0.3, f: 0.6, sp: 0.3 }, sizeLife: [[0, 0.3, 0, 3], [0.25, 1], [1, 1.2]], alpha: [[0, 0], [0.15, 1], [0.75, 0.9], [1, 0]] }, o || {});

var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, {
  staffWall: cylPanels(24, 3, 0.2), cone12: fan(Math.PI * 2 / 3, 0.6, 12, 0.05, 40), cone12Edge: fanEdge(Math.PI * 2 / 3, 0.6, 12, 0.14, 0.06),
  gust: crescentXZ(Math.PI * 0.6, 0.06), gustT: crescentXZ(Math.PI * 0.6, 0.025),
  lane3: strip([0, 0], [0, 18], 3, 0.05), lane3Edge: merge([strip([-1.5, 0], [-1.5, 18], 0.1, 0.06), strip([1.5, 0], [1.5, 18], 0.1, 0.06)]),
  step: { verts: [[-1.5, 0, -0.75], [1.5, 0, -0.75], [1.5, 0, 0.75], [-1.5, 0, 0.75]], uvs: [[0, 0], [1, 0], [1, 1], [0, 1]], tris: [0, 1, 2, 0, 2, 3] },
  lidR: ribbon(48, s => { const p = Math.PI / 2 - s * Math.PI, d = [Math.cos(p), 0, Math.sin(p)]; return [[1.5, 0.05, d[2] * 0.2], [1.5 + d[0] * 14.5, 0.05, d[2] * ARENA]]; }),
  lidL: ribbon(48, s => { const p = Math.PI / 2 - s * Math.PI, d = [Math.cos(p), 0, Math.sin(p)]; return [[-1.5, 0.05, d[2] * 0.2], [-1.5 - d[0] * 14.5, 0.05, d[2] * ARENA]]; }),
  lidEdges: merge([strip([1.5, -ARENA], [1.5, ARENA], 0.14, 0.06), strip([-1.5, -ARENA], [-1.5, ARENA], 0.14, 0.06), ringM(ARENA, 0.14, 0.06)]),
  half16: fan(Math.PI, 0.6, ARENA, 0.05, 48), half16Edge: fanEdge(Math.PI, 0.6, ARENA, 0.16, 0.06),
  pillar3: pillarM(1, 3), pillar8: pillarM(1, 8), pillar14: pillarM(1, 14),
  wallLine: merge([wallZ(ARENA, 4), { verts: [[0, 0, 0], [0, 0, -ARENA], [0, 4, 0], [0, 4, -ARENA]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]),
  floorLine: strip([0, -ARENA], [0, ARENA], 0.5, 0.06),
  halo: merge([ringM(0.5, 0.05, 0), ringM(0.4, 0.02, 0)]), ringV: ringV(0.06),
  arenaRing: merge([ringM(ARENA, 0.16, 0.04), ringM(ARENA - 0.5, 0.06, 0.04)]), arenaRune: runeArcs(24, ARENA - 1.3, 0.8, 0.035),
});

// ---------- lullaby: 자장가 고리 3겹 (static, 보스 발밑) ----------
const LUL = [];
[0, 0.5, 1.0].forEach((t, i) => LUL.push(
  { name: `L${i}1 StaffWall`, mat: 'staff', mode: 'mesh', mesh: 'staffWall', fudge: 6, size: 1, rot3: YAW, rotLife: [0, 0.6, 0], bursts: [[t, 1]], life: 1.6, sizeLife: [[0, 1.5, 0, 9.5], [1, ARENA, 9.5, 0]], alpha: [[0, 0], [0.08, 0.9], [0.8, 0.7], [1, 0]] },
  { name: `L${i}2 FloorRing`, mat: 'line', mode: 'mesh', mesh: 'cOuter', fudge: 30, size: 1, rot3: YAW, bursts: [[t, 1]], life: 1.6, sizeLife: [[0, 1.5, 0, 9.5], [1, ARENA, 9.5, 0]], alpha: [[0, 0], [0.08, 1], [0.8, 0.8], [1, 0]] },
  { name: `L${i}3 Notes`, mat: 'note', fudge: -4, life: [1.0, 1.5], size: [0.4, 0.6], rot: [-0.3, 0.3], shape: { type: 'circle', r: 1.5, th: 0, y: 1.2 }, bursts: [[t, 14]], vel: { rad: [7, 9.5], y: [0.3, 0.9] }, noise: { s: 0.3, f: 0.8, sp: 0.4 },
    sizeLife: [[0, 0.3, 0, 4], [0.2, 1], [1, 0.9]], alpha: [[0, 0], [0.12, 1], [0.75, 0.9], [1, 0]] },
  { name: `L${i}4 Motes`, mat: 'dot', fudge: -6, life: [1.2, 1.6], size: [0.05, 0.09], shape: { type: 'circle', r: 1.5, th: 0, y: 0.4 }, bursts: [[t, 30]], vel: { rad: [8, 10], y: [0.5, 1.2] }, color: [[0, WHITE], [1, LAV]], alpha: [[0, 0], [0.1, 1], [1, 0]] }));
LUL.push(
  { name: 'L90 Halo', mat: 'glow', fudge: 2, pos: [0, CHEST, 0], size: 5, bursts: [[0, 1]], life: 1.8, alpha: [[0, 0], [0.2, 0.35], [1, 0]] },
  { name: 'L91 MoonSigil', mat: 'sigil', mode: 'mesh', mesh: 'quad', fudge: 35, pos: [0, 0.05, 0], size: 7, rot3: YAW, rotLife: [0, -0.5, 0], bursts: [[0, 1]], life: 2.4, sizeLife: [[0, 0.6, 0, 2], [0.15, 1]], alpha: [[0, 0], [0.12, 0.8], [0.7, 0.6], [1, 0]] },
  { name: 'L92 Rune', mat: 'rune', mode: 'mesh', mesh: 'cRune', fudge: 36, size: 4.2, rot3: YAW, rotLife: [0, 0.6, 0], bursts: [[0, 1]], life: 2.4, sizeLife: [[0, 0.6, 0, 2], [0.15, 1]], alpha: [[0, 0], [0.12, 0.9], [0.7, 0.7], [1, 0]] });
// drowsy: 자장가에 맞은 플레이어 (attached, 2.5 s)
const DROW = [
  zzz('D01', 0, 2, [0, 2.0, 0]), zzz('D02', 0.6, 2, [0, 2.0, 0]), zzz('D03', 1.2, 2, [0, 2.0, 0]),
  { name: 'D04 Haze', mat: 'dust', fudge: 22, pos: [0, 1.0, 0], life: [1.2, 1.6], size: [0.9, 1.4], rot: [0, TAU], shape: { type: 'circle', r: 0.6, th: 1, y: 0 }, bursts: [[0, 5], [0.8, 3]], vel: { orb: [1, 1.5], y: [0.2, 0.4] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'D05 FootRing', mat: 'line', mode: 'mesh', mesh: 'cOuter', fudge: 30, size: 0.8, rot3: YAW, rotLife: [0, 1.5, 0], bursts: [[0, 1]], life: 2.2, sizeLife: [[0, 1.4, 0, -3], [0.15, 1]], alpha: [[0, 0], [0.1, 1], [0.7, 0.7], [1, 0]] },
  { name: 'D06 Stars', mat: 'star', fudge: -8, pos: [0, 1.9, 0], life: 1.2, size: [0.18, 0.28], shape: { type: 'circle', r: 0.55, th: 0, y: 0 }, bursts: [[0, 4], [0.6, 4], [1.2, 4]], vel: { orb: [2.5, 2.5], y: [-0.3, -0.1] }, sizeLife: [[0, 0.3], [0.3, 1], [1, 0.3]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: 'D07 Hit', mat: 'flare', fudge: -7, pos: [0, 1.2, 0], size: 2.2, rot: [0, TAU], bursts: [[0, 1]], life: 0.35, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.7], [1, 0]] },
];

// ---------- gust: 날개 바람 (static, 보스 발밑, +Z 부채꼴 120° 반경 12) ----------
const GUST = [
  { name: 'G01 Fill', mat: 'fanV', mode: 'mesh', mesh: 'cone12', fudge: 42, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.0, alpha: [[0, 0], [0.05, 0.55], [0.35, 0.3], [1, 0]] },
  { name: 'G02 Edge', mat: 'line', mode: 'mesh', mesh: 'cone12Edge', fudge: 40, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 0.9, alpha: [[0, 1], [0.6, 0.6], [1, 0]] },
];
for (let i = 0; i < 5; i++) GUST.push(
  { name: `G1${i} Gust`, mat: i % 2 ? 'slashC' : 'slash', mode: 'mesh', mesh: i % 2 ? 'gustT' : 'gust', fudge: 4 + i, pos: [0, 0.4 + i * 0.7, 0], size: 2, rot3: [0, FWD, 0], bursts: [[0.04 * i, 1]], life: 0.5, sizeLife: [[0, 1], [1, 6.2]], flip: { x: 2, y: 8 } });
GUST.push(
  { name: 'G20 Streaks', mat: 'streak', mode: 'stretch', vs: 0.06, ls: 2.4, fudge: -3, pos: [0, 1, 0.8], life: [0.4, 0.6], size: [0.08, 0.14], shape: { type: 'box', sx: 3, sy: 3, sz: 0.5 }, bursts: [[0, 40], [0.12, 30]], vel: { z: [16, 24], x: [-8, 8], y: [-1, 2] }, drag: 1.2, alpha: [[0, 1], [1, 0]] },
  featherBurst('G21', 0, 26, [0, 2, 1], [1, 2], { vel: { z: [10, 15], x: [-6, 6], y: [0, 2] }, drag: 1.4, life: [0.9, 1.3] }),
  { name: 'G22 Dust', mat: 'dust', fudge: 22, pos: [0, 0.4, 2], life: [0.8, 1.1], size: [1.2, 1.8], rot: [0, TAU], shape: { type: 'box', sx: 4, sy: 0.4, sz: 1 }, bursts: [[0.02, 12]], vel: { z: [8, 12], x: [-5, 5] }, drag: 1.6, sizeLife: [[0, 0.6], [1, 1.5]], alpha: [[0, 0], [0.15, 0.5], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'G23 Sparkle', mat: 'star', fudge: -9, pos: [0, 1.5, 3], life: [0.3, 0.5], size: [0.2, 0.36], rot: [0, TAU], shape: { type: 'box', sx: 8, sy: 3, sz: 6 }, bursts: [[0.05, 12], [0.25, 8]], vel: { z: [4, 8] }, sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: 'G24 WingFlash', mat: 'flare', fudge: -7, pos: [0, CHEST, 0.6], size: 7, rot: [0, TAU], bursts: [[0, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.6], [1, 0]] });

// ---------- feather: 깃털 비 한 발 (static, 낙하 지점, 반경 2) ----------
const FTH = [
  { name: 'F01 Fall', mat: 'feather', fudge: -2, pos: [0, 9, 0], life: 0.32, size: 1.6, rot: [3.14, 3.14], bursts: [[0, 1]], vel: { y: -26 } },
  { name: 'F02 FallTrail', mat: 'streak', mode: 'stretch', vs: 0.04, ls: 1.6, fudge: -1, pos: [0, 9, 0], life: [0.2, 0.32], size: [0.12, 0.2], bursts: [[0, 6]], vel: { y: [-26, -24] }, alpha: [[0, 0.6], [1, 0.9]] },
  ...magicCircle('F1', 2, 0, 0.32, 1.3, MV, { star: 'cHex' }),
  ...impactBurst('F2', [0, 0, 0], 0.32, 0.8, BV, CV, { waveR: 2.2, n: 0.8 }),
  featherBurst('F30', 0.32, 10, [0, 0.6, 0], [2.5, 4.5]),
  { name: 'F31 Stuck', mat: 'feather', fudge: -2, pos: [0, 0.55, 0], size: 1.2, rot: [3.14, 3.14], bursts: [[0.32, 1]], life: 0.9, alpha: [[0, 1], [0.6, 0.9], [1, 0]] },
];

// ---------- pillow: 베개 짓누르기 (static, 반경 5) ----------
const PIL = [
  ...magicCircle('P0', 5, 0, 0.6, 2.0, MV),
  ...impactBurst('P1', [0, 0, 0], 0.6, 1.8, BV, CV, { waveR: 3, n: 1.3, flareA: 0.6 }),
  featherBurst('P20', 0.6, 40, [0, 0.8, 0], [5, 9], { life: [1.4, 2.0] }),
  { name: 'P21 Cloud', mat: 'dust', fudge: 22, life: [1.1, 1.5], size: [1.8, 2.8], rot: [0, TAU], shape: { type: 'circle', r: 2.5, th: 1, y: 0.4 }, bursts: [[0.62, 16]], vel: { rad: [3, 5], y: [0.2, 0.6] }, drag: 2, sizeLife: [[0, 0.6], [1, 1.4]], alpha: [[0, 0], [0.15, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
  zzz('P22', 0.7, 6, [0, 1.6, 0], { shape: { type: 'circle', r: 2.5, th: 1, y: 0 } }),
  { name: 'P23 Sparkle', mat: 'star', fudge: -9, life: [0.35, 0.6], size: [0.2, 0.4], rot: [0, TAU], shape: { type: 'circle', r: 4.5, th: 1, y: 0.3 }, bursts: [[0.65, 12], [0.9, 10]], sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
];

// ---------- pool: 잠의 웅덩이 (static 반복 — 자리 옮길 때 서버가 정지 후 새로 재생) / pool_open / pool_close ----------
const LP = 4;
const POOL = [
  heldLoop('W01 Spiral', 'spiral', 'quad', 41, symW(3, LP), 0.9, 4, [0, 0.04, 0], LP),
  heldLoop('W02 Spiral2', 'spiralC', 'quad', 42, symW(3, LP, 2), 0.5, 3, [0, 0.05, 0], LP),
  heldLoop('W03 Rim', 'line', 'cOuter', 40, 0.3, 1, 1.8, [0, 0, 0], LP),
  heldLoop('W04 Rune', 'rune', 'cRune2', 39, symW(10, LP, -1), 0.85, 3.4, [0, 0, 0], LP),
  { name: 'W05 Glow', mat: 'glow', mode: 'mesh', mesh: 'quad', fudge: 45, pos: [0, 0.02, 0], size: 5, rot3: YAW, rate: 1 / (0.85 * LP), life: LP, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 0.45], [0.85, 0.45], [1, 0]], onStop: 0.3 },
  { name: 'W06 Bubbles', mat: 'bubble', fudge: -3, life: [1.4, 2.0], size: [0.18, 0.36], shape: { type: 'circle', r: 1.5, th: 1, y: 0.1 }, rate: 8, bursts: [[0, 6]], vel: { y: [0.6, 1.2] }, noise: { s: 0.3, f: 0.8, sp: 0.4 }, sizeLife: [[0, 0.3], [0.2, 1], [1, 1.1]], alpha: [[0, 0], [0.15, 1], [0.85, 0.8], [1, 0]], onStop: 0.3 },
  { name: 'W07 Swirl', mat: 'dot', fudge: -6, life: [1.0, 1.4], size: [0.04, 0.08], shape: { type: 'circle', r: 1.8, th: 0, y: 0.1 }, rate: 30, bursts: [[0, 15]], vel: { orb: [3, 4], rad: [-1.2, -0.8], y: [0.3, 0.8] }, color: [[0, WHITE], [0.5, CYAN], [1, VIO]], alpha: [[0, 0], [0.15, 1], [1, 0]], onStop: 0.2 },
  { name: 'W08 Twinkle', mat: 'star', fudge: -9, life: [0.4, 0.7], size: [0.14, 0.26], rot: [0, TAU], shape: { type: 'circle', r: 1.6, th: 1, y: 0.4 }, rate: 6, sizeLife: [[0, 0.2], [0.3, 1], [1, 0]], onStop: 0.1 },
  { name: 'W09 Column', mat: 'pillarC', mode: 'mesh', mesh: 'pillar3', fudge: 9, size: 1.4, rot3: YAW, rotLife: [0, 0.5, 0], rate: 0.8, life: 2.5, bursts: [[0, 1]], alpha: [[0, 0], [0.5, 0.35], [1, 0]], onStop: 0.3 },
];
const POOL_OPEN = [
  ...impactBurst('O', [0, 0, 0], 0, 0.6, { star: 'starC', flare: 'flareC', wave: 'waveC', streak: 'streakC', dot: 'dotC', ring: 'lineC' }, CC, { waveR: 2, n: 0.6 }),
  { name: 'O20 Draw', mat: 'lineC', mode: 'mesh', mesh: 'cOuter', fudge: 30, size: 1.8, rot3: YAW, rotLife: [0, 3, 0], bursts: [[0, 1]], life: 0.6, sizeLife: [[0, 0.1, 0, 4], [0.5, 1]], alpha: [[0, 1], [1, 0]] },
];
const POOL_CLOSE = [
  { name: 'C01 Shrink', mat: 'spiral', mode: 'mesh', mesh: 'quad', fudge: 41, pos: [0, 0.04, 0], size: 4, rot3: YAW, rotLife: [0, 6, 0], bursts: [[0, 1]], life: 0.6, sizeLife: [[0, 1], [1, 0.05]], alpha: [[0, 0.9], [1, 0]] },
  { name: 'C02 Pop', mat: 'flareC', fudge: -7, pos: [0, 0.5, 0], size: 2.5, rot: [0, TAU], bursts: [[0.5, 1]], life: 0.3, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.7], [1, 0]] },
  { name: 'C03 Bubbles', mat: 'bubble', fudge: -3, pos: [0, 0.3, 0], life: [0.5, 0.8], size: [0.2, 0.36], shape: { type: 'sphere', r: 0.3, th: 1 }, bursts: [[0.5, 12]], speed: [2, 4], drag: 2, alpha: [[0, 1], [1, 0]] },
];
// enter: 웅덩이로 빠져듦 / exit: 종을 쳐서 현실로 튕겨 나옴 (attached, 플레이어)
const ENTER = [
  { name: 'N01 Spiral', mat: 'spiralC', mode: 'mesh', mesh: 'quad', fudge: 41, pos: [0, 0.05, 0], size: 3.5, rot3: YAW, rotLife: [0, 7, 0], bursts: [[0, 1]], life: 0.8, sizeLife: [[0, 1.4], [1, 0.1]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: 'N02 SuckStars', mat: 'star', fudge: -8, pos: [0, 1, 0], life: 0.5, size: [0.2, 0.32], shape: { type: 'sphere', r: 2, th: 0 }, bursts: [[0, 14], [0.2, 10]], vel: { rad: [-4, -3.6] }, alpha: [[0, 0], [0.3, 1], [1, 1]] },
  { name: 'N03 Column', mat: 'pillarC', mode: 'mesh', mesh: 'pillar3', fudge: 9, size: 1.2, rot3: YAW, rotLife: [0, 4, 0], bursts: [[0, 1]], life: 0.8, sizeLife: [[0, 0.2, 0, 4], [0.3, 1], [1, 0.2]], alpha: [[0, 1], [1, 0]] },
  { name: 'N04 Flash', mat: 'starC', fudge: -8, pos: [0, 0.8, 0], size: 3, rot: [0, TAU], bursts: [[0.7, 1]], life: 0.25, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  { name: 'N05 Bubbles', mat: 'bubble', fudge: -3, pos: [0, 0.6, 0], life: [0.6, 0.9], size: [0.18, 0.32], shape: { type: 'circle', r: 0.8, th: 1, y: 0 }, bursts: [[0.1, 14]], vel: { y: [1.5, 3] }, alpha: [[0, 1], [1, 0]] },
];
const EXIT = [
  { name: 'X01 Flash', mat: 'starG', fudge: -8, pos: [0, 1, 0], size: 3.2, rot: [0, TAU], bursts: [[0, 1]], life: 0.25, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  { name: 'X02 Ring', mat: 'lineG', mode: 'mesh', mesh: 'ringV', fudge: -2, pos: [0, 1, 0], size: 1, rot3: [[0, TAU], [0, TAU], 0], bursts: [[0, 3]], life: 0.45, sizeLife: [[0, 0.2, 0, 3], [1, 1.6]], alpha: [[0, 1], [1, 0]] },
  { name: 'X03 BubblePop', mat: 'bubble', fudge: -3, pos: [0, 1, 0], life: [0.5, 0.8], size: [0.22, 0.4], shape: { type: 'sphere', r: 0.4, th: 1 }, bursts: [[0, 16]], speed: [3, 6], drag: 2.5, alpha: [[0, 1], [1, 0]] },
  { name: 'X04 Stars', mat: 'star', fudge: -9, pos: [0, 1, 0], life: [0.4, 0.7], size: [0.2, 0.34], rot: [0, TAU], shape: { type: 'sphere', r: 0.4, th: 1 }, bursts: [[0, 10]], speed: [2, 4], drag: 2, sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: 'X05 Up', mat: 'streakG', mode: 'stretch', vs: 0.06, ls: 2.2, fudge: -3, life: [0.4, 0.6], size: [0.05, 0.1], shape: { type: 'circle', r: 0.8, th: 1, y: 0.1 }, bursts: [[0, 18]], vel: { y: [6, 10] }, drag: 2.5, alpha: [[0, 1], [1, 0]] },
];

// ---------- bell_ring (static, 종탑 발밑 — 종 높이 2.9 m) / bell_glow (static 반복, 빛나는 종) ----------
const BELLY = 2.9;
const BRING = [
  { name: 'B01 Flash', mat: 'starG', fudge: -8, pos: [0, BELLY, 0], size: 4, rot: [0, TAU], bursts: [[0, 1]], life: 0.25, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: 'B02 Flare', mat: 'flareG', fudge: -7, pos: [0, BELLY, 0], size: 5, rot: [0, TAU], bursts: [[0, 1]], life: 0.5, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.15]], alpha: [[0, 0.8], [1, 0]] },
  { name: 'B03 SoundRings', mat: 'lineG', mode: 'mesh', mesh: 'cOuter', fudge: -2, pos: [0, BELLY - 0.4, 0], size: 1, rot3: YAW, bursts: [[0, 1], [0.2, 1], [0.4, 1], [0.6, 1]], life: 0.9, sizeLife: [[0, 0.6, 0, 6], [1, 6]], alpha: [[0, 1], [1, 0]] },
  { name: 'B04 Notes', mat: 'noteG', fudge: -4, pos: [0, BELLY, 0], life: [1.0, 1.4], size: [0.4, 0.6], rot: [-0.3, 0.3], shape: { type: 'circle', r: 0.6, th: 1, y: 0 }, bursts: [[0, 6], [0.25, 5], [0.5, 4]], vel: { rad: [1.5, 3], y: [1, 2] }, noise: { s: 0.3, f: 0.8, sp: 0.4 }, sizeLife: [[0, 0.3, 0, 4], [0.2, 1], [1, 0.9]], alpha: [[0, 0], [0.12, 1], [0.75, 0.9], [1, 0]] },
  { name: 'B05 Dawn', mat: 'pillarG', mode: 'mesh', mesh: 'pillar14', fudge: 8, size: 2.2, rot3: YAW, rotLife: [0, 1.5, 0], bursts: [[0, 1]], life: 1.3, sizeLife: [[0, 0.2, 0, 5], [0.15, 1], [1, 0.4]], alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
  { name: 'B06 FloorWave', mat: 'waveG', mode: 'mesh', mesh: 'quad', fudge: 30, pos: [0, 0.06, 0], size: 9, rot3: YAW, bursts: [[0, 1], [0.15, 1]], life: 0.6, sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.75], [1, 0]] },
  { name: 'B07 Sparkle', mat: 'starG', fudge: -9, life: [0.35, 0.6], size: [0.18, 0.34], rot: [0, TAU], shape: { type: 'sphere', r: 2.5, th: 1 }, pos: [0, BELLY, 0], bursts: [[0.05, 14], [0.35, 10]], sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: 'B08 Motes', mat: 'dotG', fudge: -6, life: [1.2, 1.8], size: [0.05, 0.09], shape: { type: 'circle', r: 2, th: 1, y: 0.1 }, bursts: [[0, 30]], vel: { y: [1.5, 3] }, noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, WHITE], [1, STARG]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
];
const BGLOW = [
  { name: 'K01 Halo', mat: 'glowG', fudge: 2, pos: [0, BELLY, 0], size: 3.2, rate: 2, life: 1.0, bursts: [[0, 2]], sizeLife: [[0, 0.9], [0.5, 1.05], [1, 0.9]], alpha: [[0, 0], [0.5, 0.45], [1, 0]], onStop: 0.2 },
  heldLoop('K02 Ring', 'lineG', 'cOuter', 30, 0.5, 0.9, 1.6, [0, 0, 0], 4),
  heldLoop('K03 Rune', 'runeG', 'cRune2', 31, symW(10, 4), 0.8, 3, [0, 0, 0], 4),
  { name: 'K04 Beam', mat: 'pillarG', mode: 'mesh', mesh: 'pillar8', fudge: 9, size: 0.9, rot3: YAW, rotLife: [0, 0.6, 0], rate: 0.8, life: 2.5, bursts: [[0, 1]], alpha: [[0, 0], [0.5, 0.4], [1, 0]], onStop: 0.3 },
  { name: 'K05 Motes', mat: 'dotG', fudge: -6, life: [1.2, 1.8], size: [0.04, 0.08], shape: { type: 'circle', r: 1.2, th: 1, y: 0.1 }, rate: 14, bursts: [[0, 8]], vel: { y: [0.8, 1.6] }, noise: { s: 0.4, f: 1, sp: 0.4 }, color: [[0, WHITE], [1, STARG]], alpha: [[0, 0], [0.15, 1], [1, 0]], onStop: 0.3 },
  { name: 'K06 Twinkle', mat: 'starG', fudge: -9, pos: [0, BELLY, 0], life: [0.4, 0.7], size: [0.16, 0.28], rot: [0, TAU], shape: { type: 'sphere', r: 1, th: 1 }, rate: 4, sizeLife: [[0, 0.2], [0.3, 1], [1, 0]], onStop: 0.1 },
];

// ---------- stairs: 떨어지는 계단 한 줄 (static, 줄 시작점, +Z 길이 18 m 폭 3 m — 서버가 줄마다 순차 재생) ----------
const STR = [
  { name: 'S01 Fill', mat: 'bandN', mode: 'mesh', mesh: 'lane3', fudge: 42, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.1, alpha: [[0, 0], [0.3, 0.5], [0.4, 0.9], [1, 0]] },
  { name: 'S02 Edge', mat: 'lineN', mode: 'mesh', mesh: 'lane3Edge', fudge: 40, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.1, alpha: [[0, 0.6], [0.35, 1], [1, 0]] },
];
for (let i = 0; i < 9; i++) {
  const z = 1 + i * 2, t = 0.04 * i;
  STR.push({ name: `S1${i} Step`, mat: 'band', mode: 'mesh', mesh: 'step', fudge: 6, pos: [0, 7, z], size: 1, rot3: [0.15, 0, 0], bursts: [[t, 1]], life: 0.4, vel: { y: -17.5 }, alpha: [[0, 0], [0.2, 1], [1, 1]] });
}
STR.push(
  { name: 'S20 Land', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, pos: [0, 0.1, 9], life: [0.35, 0.55], size: [0.1, 0.18], shape: { type: 'box', sx: 3, sy: 0.1, sz: 18 }, bursts: [[0.4, 40]], vel: { y: [7, 12] }, drag: 2.4, alpha: [[0, 1], [1, 0]] },
  { name: 'S21 Shards', mat: 'shardD', fudge: 20, pos: [0, 0.3, 9], life: [0.5, 0.8], size: [0.2, 0.36], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'box', sx: 3, sy: 0.1, sz: 18 }, bursts: [[0.4, 18]], vel: { y: [4, 7] }, speed: [1, 3], gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: 'S22 Sparks', mat: 'dotN', mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [0, 0.2, 9], life: [0.4, 0.7], size: [0.05, 0.09], shape: { type: 'box', sx: 3, sy: 0.1, sz: 18 }, bursts: [[0.4, 36]], vel: { y: [3, 6] }, speed: [1, 3], gravity: 0.6, color: [[0, WHITE], [0.5, LAV], [1, VIO]], alpha: [[0, 1], [1, 0]] },
  { name: 'S23 Mist', mat: 'mist', fudge: 25, pos: [0, 0.4, 9], life: [0.9, 1.2], size: [1.4, 2.0], rot: [0, TAU], shape: { type: 'box', sx: 3, sy: 0.1, sz: 17 }, bursts: [[0.42, 8]], vel: { y: [0.4, 1] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } });

// ---------- eyelid: 눈꺼풀 (static, 경기장 중앙, +Z 방향 가운데 폭 3 m 띠만 안전) ----------
const EYE = [
  { name: 'E01 LidR', mat: 'fanN', mode: 'mesh', mesh: 'lidR', fudge: 42, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.3, alpha: [[0, 0], [0.06, 0.9], [0.3, 0.55], [1, 0]] },
  { name: 'E02 LidL', mat: 'fanN', mode: 'mesh', mesh: 'lidL', fudge: 42, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.3, alpha: [[0, 0], [0.06, 0.9], [0.3, 0.55], [1, 0]] },
  { name: 'E03 Edges', mat: 'lineN', mode: 'mesh', mesh: 'lidEdges', fudge: 40, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.2, alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
  { name: 'E05 LashR', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 2.4, fudge: -3, pos: [1.6, 0.3, 0], life: [0.3, 0.5], size: [0.1, 0.18], shape: { type: 'box', sx: 0.2, sy: 0.4, sz: 30 }, bursts: [[0.02, 40]], vel: { x: [3, 6], y: [3, 6] }, drag: 2, alpha: [[0, 1], [1, 0]] },
  { name: 'E06 LashL', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 2.4, fudge: -3, pos: [-1.6, 0.3, 0], life: [0.3, 0.5], size: [0.1, 0.18], shape: { type: 'box', sx: 0.2, sy: 0.4, sz: 30 }, bursts: [[0.02, 40]], vel: { x: [-6, -3], y: [3, 6] }, drag: 2, alpha: [[0, 1], [1, 0]] },
  { name: 'E07 MistR', mat: 'mist', fudge: 25, pos: [8, 0.4, 0], life: [1.0, 1.4], size: [2, 3], rot: [0, TAU], shape: { type: 'box', sx: 12, sy: 0.2, sz: 24 }, bursts: [[0.04, 14]], vel: { y: [0.4, 1] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'E08 MistL', mat: 'mist', fudge: 25, pos: [-8, 0.4, 0], life: [1.0, 1.4], size: [2, 3], rot: [0, TAU], shape: { type: 'box', sx: 12, sy: 0.2, sz: 24 }, bursts: [[0.04, 14]], vel: { y: [0.4, 1] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'E09 SparksR', mat: 'dotN', mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [8, 0.2, 0], life: [0.4, 0.7], size: [0.05, 0.1], shape: { type: 'box', sx: 13, sy: 0.1, sz: 26 }, bursts: [[0.02, 60]], vel: { y: [3, 7] }, gravity: 0.5, color: [[0, WHITE], [0.5, LAV], [1, VIO]], alpha: [[0, 1], [1, 0]] },
  { name: 'E10 SparksL', mat: 'dotN', mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [-8, 0.2, 0], life: [0.4, 0.7], size: [0.05, 0.1], shape: { type: 'box', sx: 13, sy: 0.1, sz: 26 }, bursts: [[0.02, 60]], vel: { y: [3, 7] }, gravity: 0.5, color: [[0, WHITE], [0.5, LAV], [1, VIO]], alpha: [[0, 1], [1, 0]] },
  { name: 'E11 Flash', mat: 'flareN', fudge: -7, pos: [0, 3, 0], size: 14, rot: [0, TAU], bursts: [[0, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.55], [1, 0]] },
];

// ---------- clone: 악몽 분신에 붙는 기운 (attached 반복) ----------
const CLONE = [
  { name: 'M01 Body', mat: 'mist', fudge: 25, pos: [0, 1.0, 0], life: [0.8, 1.2], size: [0.8, 1.2], rot: [0, TAU], shape: { type: 'sphere', r: 0.5, th: 1 }, rate: 14, bursts: [[0, 6]], vel: { y: [0.3, 0.7] }, sizeLife: [[0, 0.6], [1, 1.2]], alpha: [[0, 0], [0.2, 0.7], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.2 },
  { name: 'M02 Trail', mat: 'mist', fudge: 26, world: true, rateDist: 4, life: [0.6, 0.9], size: [0.7, 1.0], rot: [0, TAU], shape: { type: 'sphere', r: 0.4, th: 1 }, pos: [0, 0.9, 0], sizeLife: [[0, 0.8], [1, 0.4]], alpha: [[0, 0.6], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.2 },
  { name: 'M03 Eyes', mat: 'starN', fudge: -8, pos: [0, 1.6, 0.25], life: 0.6, size: 0.25, rate: 3, bursts: [[0, 1]], shape: { type: 'box', sx: 0.3, sy: 0.05, sz: 0.05 }, sizeLife: [[0, 0.5], [0.3, 1], [1, 0.5]], alpha: [[0, 0], [0.3, 1], [1, 0]], onStop: 0.1 },
  { name: 'M04 Embers', mat: 'dotN', fudge: -6, pos: [0, 0.8, 0], life: [0.6, 1.0], size: [0.04, 0.07], shape: { type: 'sphere', r: 0.5, th: 1 }, rate: 16, vel: { y: [0.6, 1.2] }, color: [[0, [0.9, 0.7, 1]], [1, VIO]], alpha: [[0, 0], [0.2, 1], [1, 0]], onStop: 0.2 },
  { name: 'M06 Aura', mat: 'glowN', fudge: 3, pos: [0, 1.0, 0], size: 2.4, rate: 2, life: 1.0, bursts: [[0, 2]], sizeLife: [[0, 0.9], [0.5, 1.05], [1, 0.9]], alpha: [[0, 0], [0.5, 0.45], [1, 0]], onStop: 0.2 },
  { name: 'M07 Wisps', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 1.8, fudge: -3, pos: [0, 0.6, 0], life: [0.4, 0.6], size: [0.05, 0.09], shape: { type: 'circle', r: 0.5, th: 1, y: 0 }, rate: 14, vel: { y: [2, 3.5] }, alpha: [[0, 1], [1, 0]], onStop: 0.2 },
  { name: 'M05 Foot', mat: 'lineN', mode: 'mesh', mesh: 'cOuter', fudge: 30, size: 0.7, rot3: [0, 0, 0], rotLife: [0, symW(1, 2) * 3, 0], rate: 1 / 1.7, life: 2, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 0.7], [0.85, 0.7], [1, 0]], onStop: 0.2 },
];

// ---------- collapse: 꿈 붕괴 (static, 보스 발밑, +Z 반원 반경 16 바닥이 꺼짐) ----------
const COL = [
  { name: 'Q01 Fill', mat: 'fanN', mode: 'mesh', mesh: 'half16', fudge: 42, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.6, alpha: [[0, 0], [0.04, 0.95], [0.4, 0.6], [1, 0]] },
  { name: 'Q02 Edge', mat: 'lineN', mode: 'mesh', mesh: 'half16Edge', fudge: 40, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.5, alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
  { name: 'Q03 Crack', mat: 'crackN', mode: 'mesh', mesh: 'quad', fudge: 43, pos: [0, 0.03, 7], size: 18, rot3: YAW, bursts: [[0, 1]], life: 1.6, sizeLife: [[0, 0.3, 0, 4], [0.12, 1]], alpha: [[0, 1], [0.5, 0.8], [1, 0]] },
  { name: 'Q04 FallShards', mat: 'shardD', fudge: 20, pos: [0, 0.1, 8], life: [0.9, 1.3], size: [0.4, 0.8], rot: [0, TAU], rotZ: [-4, 4], shape: { type: 'box', sx: 28, sy: 0.1, sz: 14 }, bursts: [[0.05, 50]], vel: { y: [-1, 0.5] }, gravity: 1.2, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: 'Q05 FallLight', mat: 'shardL', fudge: -4, pos: [0, 0.1, 8], life: [0.9, 1.3], size: [0.2, 0.4], rot: [0, TAU], rotZ: [-6, 6], shape: { type: 'box', sx: 28, sy: 0.1, sz: 14 }, bursts: [[0.05, 40]], vel: { y: [-1, 0.5] }, gravity: 1.1, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: 'Q06 RimRays', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 3.5, fudge: -2, pos: [0, 0.1, 8], life: [0.4, 0.6], size: [0.12, 0.22], shape: { type: 'box', sx: 30, sy: 0.1, sz: 15 }, bursts: [[0, 70]], vel: { y: [8, 14] }, drag: 2.3, alpha: [[0, 1], [1, 0]] },
  { name: 'Q07 Mist', mat: 'mist', fudge: 25, pos: [0, 0.4, 8], life: [1.2, 1.6], size: [2.2, 3.4], rot: [0, TAU], shape: { type: 'box', sx: 28, sy: 0.2, sz: 14 }, bursts: [[0.05, 22]], vel: { y: [0.6, 1.3] }, sizeLife: [[0, 0.6], [1, 1.4]], alpha: [[0, 0], [0.2, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'Q08 Stars', mat: 'star', fudge: -9, pos: [0, 2, 8], life: [0.6, 1.0], size: [0.2, 0.36], rot: [0, TAU], shape: { type: 'box', sx: 28, sy: 3, sz: 14 }, bursts: [[0.05, 24]], vel: { y: [-4, -2] }, sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: 'Q09 Flash', mat: 'flareN', fudge: -7, pos: [0, 2, 7], size: 14, rot: [0, TAU], bursts: [[0, 1]], life: 0.45, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.55], [1, 0]] },
];

// ---------- groggy (attached 반복, 보스가 바닥 가까이 내려앉음) / eternal: 영원한 잠 (attached, 플레이어 사망) ----------
const GROG = [
  zzz('Y01', 0, 1, [0.5, 4.6, 0], { rate: 1.6, onStop: 0.2 }),
  { name: 'Y02 OrbitStars', mat: 'starG', fudge: -8, pos: [0, 4.8, 0], life: 2.4, size: [0.45, 0.6], shape: { type: 'circle', r: 1.3, th: 0, y: 0 }, rate: 2.5, bursts: [[0, 5]], vel: { orb: [2.4, 2.4] }, sizeLife: [[0, 0.6], [0.25, 1.1], [0.5, 0.7], [0.75, 1.1], [1, 0.6]], alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]], onStop: 0.2 },
  { name: 'Y03 OrbitMoons', mat: 'moon', fudge: -6, pos: [0, 5.1, 0], life: 2.4, size: 0.5, rot: [-0.3, 0.3], shape: { type: 'circle', r: 1.0, th: 0, y: 0 }, rate: 1.2, bursts: [[0, 3]], vel: { orb: [-3, -3] }, alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]], onStop: 0.2 },
  { name: 'Y04 Halo', mat: 'line', mode: 'mesh', mesh: 'cOuter', fudge: -1, pos: [0, 4.7, 0], size: 1.4, rot3: [0, 0, 0], rotLife: [0, 0.5, 0], rate: 1 / 1.7, life: 2, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 0.6], [0.85, 0.6], [1, 0]], onStop: 0.2 },
  { name: 'Y05 Dust', mat: 'dust', fudge: 22, pos: [0, 1.5, 0], life: [1.6, 2.2], size: [1.2, 1.8], rot: [0, TAU], shape: { type: 'circle', r: 2.5, th: 1, y: 0 }, rate: 3, vel: { y: [0.1, 0.3], orb: [0.3, 0.5] }, alpha: [[0, 0], [0.3, 0.4], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.3 },
];
const ETER = [
  { name: 'T01 Moon', mat: 'moon', fudge: -6, pos: [0, 2.6, 0], size: 1.2, rot: [0.3, 0.3], bursts: [[0, 1]], life: 1.8, sizeLife: [[0, 0.3, 0, 3], [0.2, 1], [0.8, 1], [1, 0.2]], alpha: [[0, 0], [0.15, 1], [0.8, 1], [1, 0]] },
  { name: 'T02 Sigil', mat: 'sigil', mode: 'mesh', mesh: 'quad', fudge: 35, pos: [0, 0.05, 0], size: 2.6, rot3: YAW, rotLife: [0, 1, 0], bursts: [[0, 1]], life: 1.8, sizeLife: [[0, 1.4, 0, -3], [0.2, 1], [1, 0.4]], alpha: [[0, 0], [0.15, 1], [0.8, 0.8], [1, 0]] },
  { name: 'T03 FallStars', mat: 'star', fudge: -8, pos: [0, 1, 0], life: 0.7, size: [0.2, 0.32], shape: { type: 'sphere', r: 2.2, th: 0 }, bursts: [[0, 12], [0.3, 12], [0.6, 10]], vel: { rad: [-3.2, -2.8] }, alpha: [[0, 0], [0.3, 1], [1, 1]] },
  { name: 'T04 Mist', mat: 'mist', fudge: 25, pos: [0, 0.6, 0], life: [1.2, 1.6], size: [1, 1.5], rot: [0, TAU], shape: { type: 'circle', r: 0.8, th: 1, y: 0 }, bursts: [[0.2, 8]], vel: { y: [0.3, 0.6], orb: [0.8, 1.2] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
  zzz('T05', 0.4, 4, [0, 1.6, 0]),
  { name: 'T06 Close', mat: 'flareN', fudge: -7, pos: [0, 1.2, 0], size: 3, rot: [0, TAU], bursts: [[1.4, 1]], life: 0.35, sizeLife: [[0, 1], [1, 0.2]], alpha: [[0, 0.8], [1, 0]] },
];

// ---------- dreamfall: 50% 깨지 않는 꿈 진입 (static, 경기장 중앙, 2.6 s) / wake: "일어나" (static, 보스) ----------
const DF = [
  { name: 'A01 ArenaRing', mat: 'lineN', mode: 'mesh', mesh: 'arenaRing', fudge: 40, size: 1, rot3: YAW, bursts: [[0, 1]], life: 2.6, sizeLife: [[0, 1.15, 0, -1], [0.3, 1]], alpha: [[0, 0], [0.15, 1], [0.8, 0.8], [1, 0]] },
  { name: 'A02 ArenaRune', mat: 'runeN', mode: 'mesh', mesh: 'arenaRune', fudge: 39, size: 1, rot3: YAW, rotLife: [0, 0.3, 0], bursts: [[0.1, 1]], life: 2.5, alpha: [[0, 0], [0.15, 0.9], [0.8, 0.7], [1, 0]] },
  { name: 'A03 EyeSigil', mat: 'sigilN', mode: 'mesh', mesh: 'quad', fudge: 38, pos: [0, 0.05, 0], size: 26, rot3: [0, 0, 0], rotLife: [0, -0.15, 0], bursts: [[0.2, 1]], life: 2.4, sizeLife: [[0, 1.3, 0, -2], [0.3, 1], [0.85, 0.95], [1, 0.3]], alpha: [[0, 0], [0.2, 0.9], [0.8, 0.8], [1, 0]] },
  { name: 'A04 FallStars', mat: 'star', fudge: -9, life: [0.8, 1.2], size: [0.25, 0.45], rot: [0, TAU], shape: { type: 'circle', r: 15, th: 1, y: 10 }, bursts: [[0, 20], [0.4, 30], [0.8, 40], [1.2, 50]], vel: { y: [-10, -7] }, sizeLife: [[0, 0.5], [0.5, 1], [1, 0.3]] },
  { name: 'A05 Pull', mat: 'streakN', mode: 'stretch', vs: 0.08, ls: 2, fudge: -4, pos: [0, 1, 0], life: 0.8, size: [0.1, 0.18], shape: { type: 'circle', r: 16, th: 0, y: 0 }, bursts: [[0.8, 40], [1.2, 60], [1.6, 80]], vel: { rad: [-20, -18] }, alpha: [[0, 0], [0.3, 1], [1, 0.6]] },
  { name: 'A06 Implode', mat: 'flareN', fudge: -7, pos: [0, 2, 0], size: 18, rot: [0, TAU], bursts: [[2.2, 1]], life: 0.4, sizeLife: [[0, 1], [1, 0.1]], alpha: [[0, 0], [0.3, 0.7], [1, 1]] },
  { name: 'A07 Flash', mat: 'starN', fudge: -8, pos: [0, 2, 0], size: 10, rot: [0, TAU], bursts: [[2.6, 1]], life: 0.3, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: 'A08 Mist', mat: 'mist', fudge: 25, life: [1.6, 2.2], size: [2.5, 4], rot: [0, TAU], shape: { type: 'circle', r: 14, th: 0.5, y: 0.4 }, bursts: [[0, 16], [0.8, 16]], vel: { rad: [-3, -1.5], y: [0.2, 0.5] }, sizeLife: [[0, 0.6], [1, 1.4]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
];
const WAKE = [
  ...magicCircle('U0', 6, 0, 0.5, 2.2, { line: 'lineG', rune: 'runeG', sigil: 'sigilG', glow: 'glowG' }),
  ...impactBurst('U1', [0, 0, 0], 0.5, 2.6, { star: 'starG', flare: 'flareG', glow: 'glowG', wave: 'waveG', streak: 'streakG', dot: 'dotG', shardL: 'shardL', ring: 'lineG', motes: 'dotG' }, { hot: WHITE, mid: STARG, low: [1, 0.6, 0.3] }, { waveR: 4, n: 1.4, flareA: 0.6 }),
  { name: 'U20 Dawn', mat: 'pillarG', mode: 'mesh', mesh: 'pillar14', fudge: 8, size: 4, rot3: YAW, rotLife: [0, 1, 0], bursts: [[0.5, 1]], life: 1.6, sizeLife: [[0, 0.3, 0, 4], [0.15, 1], [1, 0.5]], alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
  { name: 'U21 Rings', mat: 'lineG', mode: 'mesh', mesh: 'ringV', fudge: -2, pos: [0, CHEST, 0], size: 2, rot3: [[0, TAU], [0, TAU], 0], bursts: [[0.5, 3], [0.65, 3]], life: 0.6, sizeLife: [[0, 0.3, 0, 3], [1, 2.6]], alpha: [[0, 1], [1, 0]] },
  featherBurst('U22', 0.5, 30, [0, CHEST, 0], [6, 10], { mat: 'featherG', life: [1.4, 2.0] }),
];

// ---------- boundary: 30% 경계 붕괴 — 천천히 도는 경계선 (static 반복, 경기장 중앙, +Z 축이 경계, 0.12 rad/s) / enrage (static, 보스) ----------
const BW = 0.12;
const BND = [
  { name: 'Z01 Wall', mat: 'bandN', mode: 'mesh', mesh: 'wallLine', fudge: 8, size: 1, rot3: [0, 0, 0], rotLife: [0, BW, 0], bursts: [[0, 1]], life: 60, alpha: [[0, 0], [0.01, 0.75], [0.995, 0.75], [1, 0]], onStop: 0.3 },
  { name: 'Z02 FloorLine', mat: 'lineN', mode: 'mesh', mesh: 'floorLine', fudge: 40, size: 1, rot3: [0, 0, 0], rotLife: [0, BW, 0], bursts: [[0, 1]], life: 60, alpha: [[0, 0], [0.01, 1], [0.995, 1], [1, 0]], onStop: 0.3 },
  { name: 'Z03 WallSpark', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 2, fudge: -3, life: [0.5, 0.8], size: [0.06, 0.1], shape: { type: 'circle', r: 1.2, th: 1, y: 0.1 }, rate: 16, vel: { y: [3, 5] }, alpha: [[0, 1], [1, 0]], onStop: 0.2 },
  heldLoop('Z04 Pivot', 'lineN', 'cHex', 39, symW(6, 4), 0.9, 2.5, [0, 0, 0], 4),
  heldLoop('Z05 ArenaRing', 'lineN', 'arenaRing', 41, 0.1, 0.6, 1, [0, 0, 0], 4),
  { name: 'Z06 Motes', mat: 'dotN', fudge: -6, life: [1.6, 2.4], size: [0.05, 0.09], shape: { type: 'circle', r: 15, th: 1, y: 0.2 }, rate: 40, bursts: [[0, 30]], vel: { y: [0.5, 1.2] }, noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, [0.9, 0.7, 1]], [1, VIO]], alpha: [[0, 0], [0.15, 1], [1, 0]], onStop: 0.3 },
  { name: 'Z07 Stars', mat: 'star', fudge: -9, life: [0.5, 0.8], size: [0.18, 0.3], rot: [0, TAU], shape: { type: 'circle', r: 15, th: 1, y: 1.5 }, rate: 10, sizeLife: [[0, 0.2], [0.3, 1], [1, 0]], onStop: 0.1 },
];
const ENR = [
  ...magicCircle('R0', 8, 0, 0.6, 2.6, { line: 'lineN', rune: 'runeN', sigil: 'sigilN', glow: 'glowN' }),
  { name: 'R10 Gather', mat: 'streakN', mode: 'stretch', vs: 0.08, ls: 1.6, fudge: -4, pos: [0, CHEST, 0], life: 0.55, size: [0.06, 0.12], shape: { type: 'sphere', r: 6, th: 0 }, bursts: [[0, 24], [0.25, 30]], vel: { rad: [-11, -10] }, alpha: [[0, 0.2], [1, 1]] },
  ...impactBurst('R1', [0, 0, 0], 0.6, 3, BN, CN, { waveR: 4, n: 1.6, flareA: 0.6 }),
  { name: 'R20 Moon', mat: 'moon', fudge: -6, pos: [0, CHEST + 2.5, 0.5], size: 5, rot: [0.4, 0.4], bursts: [[0.6, 1]], life: 1.6, sizeLife: [[0, 0.3, 0, 4], [0.2, 1], [1, 1.1]], alpha: [[0, 0], [0.1, 1], [0.7, 0.8], [1, 0]] },
  { name: 'R21 Wings', mat: 'slash', mode: 'mesh', mesh: 'gust', fudge: 5, pos: [0, CHEST, 0.3], size: [5, 7], rot3: [[-0.3, 0.3], [0, TAU], [0, TAU]], bursts: [[0.6, 6]], life: 0.6, sizeLife: [[0, 0.3, 0, 4], [0.4, 1], [1, 1.15]], flip: { x: 2, y: 8 } },
  { name: 'R22 Column', mat: 'pillarN', mode: 'mesh', mesh: 'pillar14', fudge: 7, size: 4, rot3: YAW, rotLife: [0, 2, 0], bursts: [[0.6, 1]], life: 1.2, sizeLife: [[0, 2.5, 0, -5], [0.2, 1.3], [1, 0.6]], alpha: [[0, 1], [0.5, 0.75], [1, 0]] },
  featherBurst('R23', 0.6, 36, [0, CHEST, 0], [7, 12], { mat: 'featherN', life: [1.4, 2.0] }),
  { name: 'R24 StarBurst', mat: 'star', fudge: -9, pos: [0, CHEST, 0], life: [0.6, 1.0], size: [0.25, 0.45], rot: [0, TAU], shape: { type: 'sphere', r: 0.6, th: 1 }, bursts: [[0.6, 30]], speed: [6, 12], drag: 2, sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
];

const MATS = {
  line: { tex: 'T_Line', tint: [1.9, 1.6, 2.6], add: true }, lineC: { tex: 'T_Line', tint: [1.2, 2.2, 2.6], add: true }, lineG: { tex: 'T_Line', tint: [2.5, 2.1, 1.2], add: true }, lineN: { tex: 'T_Line', tint: [1.7, 0.8, 2.6], add: true },
  rune: { tex: 'T_Rune', tint: [1.7, 1.4, 2.4], add: true }, runeC: { tex: 'T_Rune', tint: [1.1, 2.0, 2.4], add: true }, runeG: { tex: 'T_Rune', tint: [2.3, 1.9, 1.1], add: true }, runeN: { tex: 'T_Rune', tint: [1.5, 0.7, 2.4], add: true },
  sigil: { tex: 'T_Sigil', tint: [1.7, 1.4, 2.4], add: true }, sigilC: { tex: 'T_Sigil', tint: [1.1, 2.0, 2.4], add: true }, sigilG: { tex: 'T_Sigil', tint: [2.3, 1.9, 1.1], add: true }, sigilN: { tex: 'T_Sigil', tint: [1.5, 0.6, 2.3], add: true },
  glow: { tex: 'T_Glow', tint: [1.0, 0.75, 1.5], add: true }, glowC: { tex: 'T_Glow', tint: [0.6, 1.2, 1.5], add: true }, glowG: { tex: 'T_Glow', tint: [1.4, 1.1, 0.5], add: true }, glowN: { tex: 'T_Glow', tint: [0.9, 0.35, 1.4], add: true },
  star: { tex: 'T_Star', tint: [2.4, 2.2, 2.8], add: true }, starC: { tex: 'T_Star', tint: [1.6, 2.6, 2.8], add: true }, starG: { tex: 'T_Star', tint: [2.8, 2.4, 1.5], add: true }, starN: { tex: 'T_Star', tint: [2.2, 1.2, 2.8], add: true },
  flare: { tex: 'T_Flare', tint: [1.8, 1.5, 2.4], add: true }, flareC: { tex: 'T_Flare', tint: [1.1, 1.9, 2.4], add: true }, flareG: { tex: 'T_Flare', tint: [2.3, 1.9, 1.0], add: true }, flareN: { tex: 'T_Flare', tint: [1.6, 0.6, 2.3], add: true },
  wave: { tex: 'T_Wave', tint: [1.4, 1.1, 2.0], add: true }, waveC: { tex: 'T_Wave', tint: [0.8, 1.6, 2.0], add: true }, waveG: { tex: 'T_Wave', tint: [1.9, 1.5, 0.7], add: true }, waveN: { tex: 'T_Wave', tint: [1.2, 0.4, 1.9], add: true },
  streak: { tex: 'T_Streak', tint: [2.0, 1.8, 2.6], add: true }, streakC: { tex: 'T_Streak', tint: [1.3, 2.2, 2.6], add: true }, streakG: { tex: 'T_Streak', tint: [2.6, 2.1, 1.1], add: true }, streakN: { tex: 'T_Streak', tint: [1.8, 0.8, 2.6], add: true },
  dot: { tex: 'T_Dot', tint: [2.2, 2.0, 2.6], add: true }, dotC: { tex: 'T_Dot', tint: [1.4, 2.2, 2.6], add: true }, dotG: { tex: 'T_Dot', tint: [2.4, 2.1, 1.3], add: true }, dotN: { tex: 'T_Dot', tint: [2.0, 1.1, 2.6], add: true },
  pillarC: { tex: 'T_Pillar', tint: [1.0, 1.8, 2.3], add: true }, pillarG: { tex: 'T_Pillar', tint: [2.2, 1.8, 1.0], add: true }, pillarN: { tex: 'T_Pillar', tint: [1.5, 0.6, 2.2], add: true },
  fanV: { tex: 'T_FanFill', tint: [0.9, 0.6, 1.6], add: true }, fanN: { tex: 'T_FanFill', tint: [1.0, 0.3, 1.5], add: true },
  band: { tex: 'T_Band', tint: [1.4, 1.1, 2.2], add: true }, bandC: { tex: 'T_Band', tint: [0.9, 1.7, 2.2], add: true }, bandN: { tex: 'T_Band', tint: [1.1, 0.4, 1.7], add: true }, staff: { tex: 'T_Staff', tint: [1.6, 1.3, 2.6], add: true },
  feather: { tex: 'T_Feather', tint: [2.0, 1.8, 2.6], add: true }, featherG: { tex: 'T_Feather', tint: [2.4, 2.1, 1.3], add: true }, featherN: { tex: 'T_Feather', tint: [1.6, 0.8, 2.4], add: true },
  zzz: { tex: 'T_Zzz', tint: [1.9, 1.7, 2.6], add: true }, note: { tex: 'T_Note', tint: [2.0, 1.8, 2.6], add: true }, noteG: { tex: 'T_Note', tint: [2.5, 2.1, 1.2], add: true },
  moon: { tex: 'T_Moon', tint: [2.5, 2.3, 1.6], add: true }, bubble: { tex: 'T_Bubble', tint: [1.4, 1.9, 2.4], add: true },
  spiral: { tex: 'T_Spiral', tint: [1.2, 0.8, 2.1], add: true }, spiralC: { tex: 'T_Spiral', tint: [0.8, 1.6, 2.2], add: true },
  dust: { tex: 'T_Dust_Sheet', tint: [1.1, 0.95, 1.6], add: true }, mist: { tex: 'T_DarkMist_Sheet', tint: [1, 1, 1], add: false },
  shardD: { tex: 'T_ShardDark', tint: [1.7, 0.9, 2.4], add: false }, shardL: { tex: 'T_ShardLight', tint: [2.0, 1.8, 2.6], add: true }, crackN: { tex: 'T_Crack', tint: [1.4, 0.6, 2.4], add: true },
  slash: { tex: 'T_Slash_Sheet', tint: [1.8, 1.6, 2.6], add: true }, slashC: { tex: 'T_Slash_Sheet', tint: [1.2, 2.0, 2.6], add: true },
};

// ---------- 데모 ----------
const BOSS = { path: [[0, [0, 0, 0], 0]], hidden: true };
const cam = (t, d, yaw, pitch) => ({ target: t, dist: d, yaw, pitch });
const SCENE = {
  name: 'Somnia_Boss', bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.7, tint: [0.85, 0.8, 1] }, tonemapping: 'neutral',
  systems: [
    { id: 'lullaby', role: 'static', loop: false, duration: 0.3, layers: LUL },
    { id: 'drowsy', role: 'attached', loop: false, duration: 1.4, layers: DROW },
    { id: 'gust', role: 'static', loop: false, duration: 0.3, layers: GUST },
    { id: 'feather', role: 'static', loop: false, duration: 0.4, layers: FTH },
    { id: 'pillow', role: 'static', loop: false, duration: 0.7, layers: PIL },
    { id: 'pool', role: 'static', loop: true, duration: 2, layers: POOL },
    { id: 'pool_open', role: 'static', loop: false, duration: 0.2, layers: POOL_OPEN },
    { id: 'pool_close', role: 'static', loop: false, duration: 0.6, layers: POOL_CLOSE },
    { id: 'enter', role: 'attached', loop: false, duration: 0.8, layers: ENTER },
    { id: 'exit', role: 'attached', loop: false, duration: 0.2, layers: EXIT },
    { id: 'bell_ring', role: 'static', loop: false, duration: 0.7, layers: BRING },
    { id: 'bell_glow', role: 'static', loop: true, duration: 2, layers: BGLOW },
    { id: 'stairs', role: 'static', loop: false, duration: 0.5, layers: STR },
    { id: 'eyelid', role: 'static', loop: false, duration: 0.2, layers: EYE },
    { id: 'clone', role: 'attached', loop: true, duration: 2, layers: CLONE },
    { id: 'collapse', role: 'static', loop: false, duration: 0.2, layers: COL },
    { id: 'groggy', role: 'attached', loop: true, duration: 2, layers: GROG },
    { id: 'eternal', role: 'attached', loop: false, duration: 1.5, layers: ETER },
    { id: 'dreamfall', role: 'static', loop: false, duration: 2.7, layers: DF },
    { id: 'wake', role: 'static', loop: false, duration: 0.7, layers: WAKE },
    { id: 'boundary', role: 'static', loop: true, duration: 60, layers: BND },
    { id: 'enrage', role: 'static', loop: false, duration: 0.7, layers: ENR },
  ],
  demo: Object.assign({}, DEMO_BG, { interval: 7, dummy: { height: 1.8 },
    scenarios: {
      lullaby: { label: '자장가 고리 → 맞은 플레이어 졸음', path: [[0, [0, 0, 0], 0]], actors: [BOSS, { path: [[0, [0, 0, 7], 180]] }],
        events: [{ t: 0.2, system: 'lullaby', actor: 0 }, { t: 0.9, system: 'drowsy', actor: 1 }], camera: cam([0, 2, 0], 30, -90, 32) },
      gust: { label: '날개 바람', path: [[0, [0, 0, 0], 0]], actors: [BOSS], events: [{ t: 0.3, system: 'gust', actor: 0 }], camera: cam([0, 2, 5], 22, -55, 30) },
      feather: { label: '깃털 비', path: [[0, [0, 0, 0], 0]], actors: [BOSS, ...[[3, 4], [-5, 6], [6, 9], [-2, 11], [1, 7], [-7, 2]].map(([x, z]) => ({ path: [[0, [x, 0, z], 0]], hidden: true }))],
        events: [1, 2, 3, 4, 5, 6].map((a, i) => ({ t: 0.2 + i * 0.18, system: 'feather', actor: a })), camera: cam([0, 2, 6], 24, -70, 30) },
      pillow: { label: '베개 짓누르기', path: [[0, [0, 0, 0], 0]], actors: [BOSS, { path: [[0, [0, 0, 5], 0]], hidden: true }], events: [{ t: 0.2, system: 'pillow', actor: 1 }], camera: cam([0, 1.5, 5], 18, -60, 28) },
      pool: { label: '잠의 웅덩이 → 들어감 → 꿈에서 종 → 깨어남', path: [[0, [0, 0, 0], 0]],
        actors: [{ path: [[0, [0, 0, 0], 0]], hidden: true }, { path: [[0, [0, 0, 3], 180], [1.6, [0, 0, 0.2], 180], [2.6, [0, 0, 0.2], 180], [2.7, [5, 0, 3], 0]] }, { path: [[0, [6, 0, 5], 0]], hidden: true }],
        events: [{ t: 0.1, system: 'pool_open', actor: 0 }, { t: 0.1, system: 'pool', actor: 0 }, { t: 1.7, system: 'enter', actor: 1 }, { t: 2.8, system: 'bell_glow', actor: 2 }, { t: 4.0, system: 'bell_ring', actor: 2 }, { t: 4.1, stop: ['bell_glow'] }, { t: 4.3, system: 'exit', actor: 1 }, { t: 5.2, stop: ['pool'] }, { t: 5.2, system: 'pool_close', actor: 0 }],
        camera: cam([2, 1.5, 2], 14, -60, 22) },
      dream: { label: '꿈 패턴: 계단 3줄 → 눈꺼풀 → 붕괴', path: [[0, [0, 0, 0], 0]], actors: [BOSS, ...[-5, 0, 5].map(x => ({ path: [[0, [x, 0, -9], 0]], hidden: true }))],
        events: [{ t: 0.2, system: 'stairs', actor: 1 }, { t: 0.5, system: 'stairs', actor: 2 }, { t: 0.8, system: 'stairs', actor: 3 }, { t: 2.4, system: 'eyelid', actor: 0 }, { t: 4.4, system: 'collapse', actor: 0 }],
        camera: cam([0, 2, 0], 36, -90, 42) },
      clone: { label: '악몽 분신', path: [[0, [0, 0, 0], 0]], actors: [{ path: [[0, [-4, 0, 0], 90], [3, [4, 0, 0], 90], [6, [-4, 0, 0], 270]] }], events: [{ t: 0, system: 'clone', actor: 0 }], camera: cam([0, 1, 0], 10, -90, 15) },
      phase: { label: '50% 깨지 않는 꿈 → "일어나"', path: [[0, [0, 0, 0], 0]], actors: [BOSS], events: [{ t: 0.1, system: 'dreamfall', actor: 0 }, { t: 3.5, system: 'wake', actor: 0 }], camera: cam([0, 2, 0], 38, -90, 35) },
      state: { label: '그로기 / 플레이어 영원한 잠', path: [[0, [0, 0, 0], 0]], actors: [BOSS, { path: [[0, [4, 0, 2], 0]] }],
        events: [{ t: 0, system: 'groggy', actor: 0 }, { t: 3, stop: ['groggy'] }, { t: 1, system: 'eternal', actor: 1 }], camera: cam([1, 3, 0], 14, -70, 16) },
      enrage: { label: '30% 광폭화 → 경계 붕괴', path: [[0, [0, 0, 0], 0]], actors: [BOSS], events: [{ t: 0.1, system: 'enrage', actor: 0 }, { t: 2.2, system: 'boundary', actor: 0 }], camera: cam([0, 3, 0], 36, -70, 30) },
    } }),
};
