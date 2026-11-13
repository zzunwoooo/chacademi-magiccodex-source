// ===== Siege_Portal — 공성전 잡몹(마수) 소환 연출. 지옥문 포탈에서 마수가 쏟아져 나온다. F = 지옥불(주황), R = 진홍, N = 어둠(보라) =====
// 전부 static: 원점 = 포탈이 서는 바닥 지점, +Z = 마수가 튀어나오는 방향(서버가 넘기는 수평 방향). 포탈 면은 XY 평면(세로로 선 문).
// 흐름: portal_open(0.9초에 열림) → portal_hold(8초 유지, 더 길면 다시 재생) → 마수가 나올 때마다 spawn_burst → portal_close.
// *_big = 공성 거수용 큰 문. rift = 땅을 파고 올라오는 마수용 바닥 균열.
const WHITE = [1, 1, 1], FIRE = [1, 0.62, 0.22], YEL = [1, 0.9, 0.5], RED = [1, 0.25, 0.14], CRIM = [0.45, 0.03, 0.06], PUR = [0.62, 0.25, 0.9], DEEP = [0.2, 0.05, 0.35];
const CF = { hot: YEL, mid: FIRE, low: RED }, CN = { hot: [0.95, 0.7, 1], mid: PUR, low: DEEP };
const MATS = mkMats({ F: [1, 0.36, 0.07], R: [1, 0.09, 0.04], N: [0.42, 0.08, 0.85] }, {
  gateF: { tex: 'T_GateSigil', tint: [2.7, 1.0, 0.2], add: true }, gateN: { tex: 'T_GateSigil', tint: [1.3, 0.3, 2.5], add: true },
  flameF: { tex: 'T_Flame_Sheet', tint: [2.5, 0.95, 0.2], add: true }, flameN: { tex: 'T_Flame_Sheet', tint: [1.2, 0.28, 2.3], add: true },
  spiralN: { tex: 'T_Spiral', tint: [1.1, 0.25, 2.3], add: true }, spiralF: { tex: 'T_Spiral', tint: [2.4, 0.8, 0.18], add: true }, spiralR: { tex: 'T_Spiral', tint: [2.2, 0.2, 0.1], add: true },
  gateR: { tex: 'T_GateSigil', tint: [2.7, 0.3, 0.12], add: true }, flameR: { tex: 'T_Flame_Sheet', tint: [2.5, 0.28, 0.1], add: true },
  clawF: { tex: 'T_ClawMark', tint: [2.7, 1.0, 0.2], add: true }, clawR: { tex: 'T_ClawMark', tint: [2.7, 0.3, 0.12], add: true }, clawN: { tex: 'T_ClawMark', tint: [1.3, 0.3, 2.5], add: true },
  voidD: { tex: 'T_Glow', tint: [0.03, 0, 0.05], add: false } });
KIT = KITS.demon;
const VQ = { verts: [[-1, -1, 0], [1, -1, 0], [-1, 1, 0], [1, 1, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] };
const SPIN = w => ({ rot3: [0, 0, [0, TAU]], rotLife: [0, 0, w] });
const fadeHold = D => [[0, 0], [0.4 / D, 1], [1 - 0.5 / D, 1], [1, 0]];
// 문 본체(세로 소용돌이 + 테두리 + 문양). t0 에 나타나 life 동안 유지. grow = 나타날 때 커지는 시간 비율
function gateBody(tag, R, t0, life, o) {
  o = o || {}; const c = [0, R + 0.4, 0], g = o.grow ?? 0.1, sl = o.shrink ? [[0, 1], [0.6, 1.08], [1, 0]] : [[0, 0.05], [g, 1.06], [Math.min(0.99, g * 1.6), 1], [1, 1]];
  const al = o.shrink ? [[0, 1], [0.7, 1], [1, 0]] : (o.hold ? fadeHold(life) : [[0, 0], [g, 1], [1, 1]]), k = o.shrink ? 3 : 1;
  const V = (name, mat, size, w, fudge, a, z) => Object.assign({ name: `${tag} ${name}`, mat, mode: 'mesh', mesh: 'vquad', fudge, pos: [0, c[1], z || 0], size, bursts: [[t0, 1]], life, sizeLife: sl, alpha: al.map(([t, v]) => [t, v * a]) }, SPIN(w * k));
  return [
    V('Void', 'voidD', R * 1.05, 0.3, 12, 0.96, -0.06),
    V('Vortex', 'spiralN', R * 1.12, 1.6, 8, 0.95), V('Vortex2', 'spiralR', R * 0.86, -2.4, 7, 0.7, 0.03), V('Vortex3', 'spiralF', R * 0.5, 3.6, 6, 0.8, 0.06),
    V('Sigil', 'gateF', R * 1.22, -0.35, 5, 0.75, 0.09), V('SigilBack', 'gateN', R * 1.5, 0.2, 10, 0.4, -0.03),
    Object.assign({ name: `${tag} Rim`, mat: 'lineF', mode: 'mesh', mesh: 'ringV', fudge: 4, pos: [0, c[1], 0.1], size: R * 1.02, bursts: [[t0, 1]], life, sizeLife: sl, alpha: al }, SPIN(0.5 * k)),
    Object.assign({ name: `${tag} Rim2`, mat: 'lineR', mode: 'mesh', mesh: 'ringV', fudge: 4, pos: [0, c[1], 0.1], size: R * 1.14, bursts: [[t0, 1]], life, sizeLife: sl, alpha: al.map(([t, v]) => [t, v * 0.7]) }, SPIN(-0.3 * k)),
    Object.assign({ name: `${tag} Ticks`, mat: 'lineF', mode: 'mesh', mesh: 'ticksV', fudge: 4, pos: [0, c[1], 0.1], size: R * 1.14, bursts: [[t0, 1]], life, sizeLife: sl, alpha: al.map(([t, v]) => [t, v * 0.8]) }, SPIN(-0.9 * k)),
    { name: `${tag} Halo`, mat: 'glowN', fudge: 14, pos: c, size: R * 3.2, bursts: [[t0, 1]], life, sizeLife: sl, alpha: al.map(([t, v]) => [t, v * 0.28]) },
    { name: `${tag} Floor`, mat: 'gateF', mode: 'mesh', mesh: 'quad', fudge: 36, pos: [0, 0.05, R * 0.5], size: R * 1.5, rot3: YAW, rotLife: [0, 0.3, 0], bursts: [[t0, 1]], life, sizeLife: sl, alpha: al.map(([t, v]) => [t, v * 0.6]) }];
}
// 문에서 흘러나오는 것들 (불길·불씨·바닥을 기는 어둠). rate 는 초당 개수
function gateFlow(tag, R, t0, n) {
  const c = [0, R + 0.4, 0];
  return [
    { name: `${tag} Flames`, mat: 'flameF', fudge: 3, pos: [0, 0.4, 0.2], life: [0.6, 1.0], size: [R * 0.5, R * 0.9], shape: { type: 'box', sx: R * 1.7, sy: 0.3, sz: 0.4 }, rate: 9 * n, delay: t0, vel: { y: [2, 5], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.3]], alpha: [[0, 0], [0.15, 0.8], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} RimFlames`, mat: 'flameN', fudge: 3, pos: c, life: [0.5, 0.8], size: [R * 0.35, R * 0.6], shape: { type: 'box', sx: R * 2, sy: R * 2, sz: 0.3 }, rate: 7 * n, delay: t0, vel: { y: [1.5, 3.5], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.2]], alpha: [[0, 0], [0.2, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Embers`, mat: 'dotF', fudge: -6, pos: c, life: [0.9, 1.7], size: [0.05, 0.11], shape: { type: 'box', sx: R * 1.5, sy: R * 1.5, sz: 0.2 }, rate: 34 * n, delay: t0, vel: { z: [2.5, 8], y: [-0.5, 2.5] }, noise: { s: 1, f: 1, sp: 0.8 }, color: [[0, YEL], [0.5, FIRE], [1, RED]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] },
    { name: `${tag} Streaks`, mat: 'streakF', mode: 'stretch', vs: 0.06, ls: 2.4, fudge: -4, pos: c, life: [0.3, 0.55], size: [0.07, 0.14], shape: { type: 'box', sx: R * 1.3, sy: R * 1.3, sz: 0.1 }, rate: 16 * n, delay: t0, vel: { z: [9, 18] }, drag: 1.5, alpha: [[0, 0], [0.2, 0.9], [1, 0]] },
    { name: `${tag} Mist`, mat: 'mist', fudge: 16, pos: [0, 0.5, 0.6], life: [1.2, 2.0], size: [R * 0.8, R * 1.3], rot: [0, TAU], shape: { type: 'box', sx: R * 1.8, sy: 0.3, sz: 0.6 }, rate: 5 * n, delay: t0, vel: { z: [1.5, 4], y: [0, 0.5] }, sizeLife: [[0, 0.6], [1, 1.5]], alpha: [[0, 0], [0.25, 0.5], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Suck`, mat: 'dotN', fudge: -5, pos: [0, c[1], -0.2], life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: 'sphere', r: R * 1.7, th: 0.15 }, rate: 20 * n, delay: t0, speed: [-R * 2.6, -R * 1.6], color: [[0, CN.mid], [1, CN.hot]], alpha: [[0, 0], [0.3, 1], [1, 0]] }];
}
function portalOpen(tag, R) {
  const c = [0, R + 0.4, 0], T = 0.9, s = R / 3;
  return [
    { name: `${tag} Tear`, mat: 'pillarR', mode: 'mesh', mesh: 'pilT', fudge: 2, pos: [0, 0.2, 0], size: R * 2.3, bursts: [[0.12, 1]], life: 0.85, sizeLife: [[0, 0.05], [0.5, 1], [1, 1.05]], alpha: [[0, 0], [0.2, 1], [0.85, 1], [1, 0]] },
    { name: `${tag} TearCore`, mat: 'pillarF', mode: 'mesh', mesh: 'pilT', fudge: 1, pos: [0, 0.2, 0], size: R * 2.1, bursts: [[0.3, 1]], life: 0.65, sizeLife: [[0, 0.05], [0.5, 1], [1, 1]], alpha: [[0, 0], [0.3, 1], [0.85, 1], [1, 0]] },
    { name: `${tag} Gather`, mat: 'dotF', fudge: -6, pos: c, life: [0.5, 0.8], size: [0.06, 0.12], shape: { type: 'sphere', r: R * 2, th: 0.2 }, bursts: [[0, Math.round(26 * s)], [0.3, Math.round(22 * s)]], speed: [-R * 3.2, -R * 2], color: [[0, FIRE], [1, YEL]], alpha: [[0, 0], [0.3, 1], [1, 0]] },
    { name: `${tag} FloorDraw`, mat: 'gateF', mode: 'mesh', mesh: 'quad', fudge: 36, pos: [0, 0.05, R * 0.5], size: R * 1.5, rot3: YAW, rotLife: [0, 1.2, 0], bursts: [[0, 1]], life: T, sizeLife: [[0, 0.3], [1, 1]], alpha: [[0, 0], [0.5, 0.8], [1, 0.6]] },
    ...gateBody(tag + 'g', R, T - 0.15, 0.75, { grow: 0.35 }),
    ...impactBurst(tag + 'b', [0, R - 0.1, 0.3], T, s * 1.3, MBs('F', true), CF, { n: 1.4, ground: false }),
    Object.assign({ name: `${tag} Pulse`, mat: 'lineF', mode: 'mesh', mesh: 'ringV', fudge: 3, pos: [0, c[1], 0.2], size: R, bursts: [[T, 1], [T + 0.12, 1]], life: 0.5, sizeLife: [[0, 0.6], [1, 1.9]], alpha: [[0, 1], [1, 0]] }, SPIN(0)),
    { name: `${tag} Blast`, mat: 'streakF', mode: 'stretch', vs: 0.06, ls: 3, fudge: -4, pos: c, life: [0.35, 0.6], size: [0.1, 0.2], shape: { type: 'box', sx: R * 1.4, sy: R * 1.4, sz: 0.1 }, bursts: [[T, Math.round(46 * s)]], vel: { z: [10, 24], y: [-2, 3] }, drag: 1.6, alpha: [[0, 1], [1, 0]] },
    { name: `${tag} Shards`, mat: 'shardDR', fudge: -3, pos: c, life: [0.6, 1.0], size: [0.3 * s, 0.7 * s], rot: [0, TAU], rotZ: [-6, 6], shape: { type: 'sphere', r: R * 0.6, th: 1 }, bursts: [[T, Math.round(14 * s)]], speed: [3, 9], gravity: 1, alpha: [[0, 1], [0.7, 1], [1, 0]] }];
}
function portalClose(tag, R) {
  const c = [0, R + 0.4, 0], T = 0.55, s = R / 3;
  return [...gateBody(tag + 'g', R, 0, T + 0.05, { shrink: true }),
    { name: `${tag} Inhale`, mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 2.5, fudge: -4, pos: c, life: [0.3, 0.5], size: [0.08, 0.16], shape: { type: 'sphere', r: R * 2.2, th: 0.2 }, bursts: [[0, Math.round(40 * s)], [0.2, Math.round(30 * s)]], speed: [-R * 5, -R * 3], alpha: [[0, 0], [0.3, 1], [1, 0]] },
    ...impactBurst(tag + 'b', [0, R - 0.1, 0], T, s * 0.9, MBs('N', true), CN, { n: 0.9, ground: false }),
    { name: `${tag} Seam`, mat: 'pillarN', mode: 'mesh', mesh: 'pilT', fudge: 2, pos: [0, 0.2, 0], size: R * 2.3, bursts: [[T - 0.1, 1]], life: 0.5, sizeLife: [[0, 1], [1, 0.9]], alpha: [[0, 0.9], [1, 0]] },
    { name: `${tag} Ash`, mat: 'dotF', fudge: -6, pos: c, life: [1.0, 1.8], size: [0.05, 0.1], shape: { type: 'sphere', r: R * 0.8, th: 1 }, bursts: [[T, Math.round(50 * s)]], vel: { y: [-1.5, 0.5] }, noise: { s: 1, f: 1, sp: 0.6 }, color: [[0, YEL], [0.5, FIRE], [1, RED]], alpha: [[0, 1], [0.7, 0.8], [1, 0]] }];
}
// 마수 한 마리가 문을 뚫고 나오는 순간 (문 위치·방향 그대로 재생)
function spawnBurst(tag, R) {
  const c = [0, R * 0.75, 0.3], s = R / 3;
  return [
    Object.assign({ name: `${tag} Ring`, mat: 'lineF', mode: 'mesh', mesh: 'ringV', fudge: 3, pos: c, size: R * 0.8, bursts: [[0, 1], [0.08, 1]], life: 0.4, sizeLife: [[0, 0.3], [1, 1.5]], alpha: [[0, 1], [1, 0]] }, SPIN(0)),
    { name: `${tag} Flash`, mat: 'starF', fudge: -8, pos: c, size: 3.2 * s, rot: [0, TAU], bursts: [[0, 1]], life: 0.22, sizeLife: [[0, 0.4], [0.3, 1.1], [1, 0]] },
    { name: `${tag} Flare`, mat: 'flareN', fudge: -7, pos: c, size: 4.6 * s, rot: [0, TAU], bursts: [[0, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.2]], alpha: [[0, 0.8], [1, 0]] },
    { name: `${tag} Jet`, mat: 'streakF', mode: 'stretch', vs: 0.06, ls: 3, fudge: -4, pos: c, life: [0.3, 0.5], size: [0.1, 0.2], shape: { type: 'sphere', r: R * 0.5, th: 1 }, bursts: [[0, Math.round(26 * s)]], vel: { z: [10, 22], y: [-3, 3], x: [-4, 4] }, drag: 1.8, alpha: [[0, 1], [1, 0]] },
    { name: `${tag} Trail`, mat: 'flameF', fudge: 3, pos: [0, 0.5, 0.5], life: [0.4, 0.7], size: [R * 0.4, R * 0.7], shape: { type: 'box', sx: R, sy: 0.3, sz: 0.4 }, bursts: [[0, Math.round(8 * s)], [0.1, Math.round(6 * s)]], vel: { z: [5, 12], y: [0.5, 2] }, drag: 2, sizeLife: [[0, 0.5], [1, 1.2]], alpha: [[0, 0], [0.15, 0.8], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Dust`, mat: 'dustN', fudge: 15, pos: [0, 0.4, 1.5], life: [0.6, 1.0], size: [R * 0.5, R * 0.9], rot: [0, TAU], shape: { type: 'box', sx: R, sy: 0.2, sz: 1 }, bursts: [[0.06, Math.round(6 * s)]], vel: { z: [3, 8], x: [-2, 2] }, drag: 2.5, sizeLife: [[0, 0.5], [1, 1.5]], alpha: [[0, 0.5], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Sparks`, mat: 'dotF', fudge: -5, pos: c, life: [0.5, 1.0], size: [0.05, 0.1], shape: { type: 'sphere', r: R * 0.4, th: 1 }, bursts: [[0, Math.round(20 * s)]], vel: { z: [3, 10], y: [0, 5], x: [-3, 3] }, gravity: 1, drag: 1, color: [[0, YEL], [0.5, FIRE], [1, RED]], alpha: [[0, 1], [0.7, 1], [1, 0]] }];
}
// 땅을 파고 올라오는 마수용: 바닥이 갈라지고(0.45초) 불길과 돌조각이 솟는다
function rift(tag, R) {
  const T = 0.45, s = R / 2.5;
  return [
    { name: `${tag} Sigil`, mat: 'gateF', mode: 'mesh', mesh: 'quad', fudge: 36, pos: [0, 0.05, 0], size: R * 1.3, rot3: YAW, rotLife: [0, 0.8, 0], bursts: [[0, 1]], life: 1.6, sizeLife: [[0, 0.3], [0.25, 1], [1, 1.05]], alpha: [[0, 0], [0.2, 0.9], [0.7, 0.7], [1, 0]] },
    { name: `${tag} Crack`, mat: 'crackF', mode: 'mesh', mesh: 'quad', fudge: 33, pos: [0, 0.04, 0], size: R * 2.2, rot3: YAW, bursts: [[T - 0.25, 1]], life: 1.6, sizeLife: [[0, 0.3], [0.15, 1], [1, 1]], alpha: [[0, 1], [0.5, 0.8], [1, 0]] },
    { name: `${tag} Crack2`, mat: 'crackR', mode: 'mesh', mesh: 'quad', fudge: 34, pos: [0, 0.04, 0], size: R * 2.8, rot3: YAW, bursts: [[T, 1]], life: 1.2, alpha: [[0, 0.8], [1, 0]] },
    ...impactBurst(tag + 'b', [0, 0, 0], T, s, MBs('F', true), CF, { n: 1.2 }),
    ...KIT.column(tag + 'k', [0, 0, 0], T, s * 1.3, 'F', CF),
    { name: `${tag} Jets`, mat: 'flameF', fudge: 3, pos: [0, 0.3, 0], life: [0.5, 0.9], size: [R * 0.5, R * 0.9], shape: { type: 'circle', r: R * 0.8, th: 1, y: 0 }, bursts: [[T, Math.round(14 * s)], [T + 0.15, Math.round(10 * s)], [T + 0.35, Math.round(6 * s)]], vel: { y: [3, 8], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.3]], alpha: [[0, 0], [0.15, 0.85], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Rocks`, mat: 'shardDR', fudge: -3, pos: [0, 0.3, 0], life: [0.7, 1.1], size: [0.3 * s, 0.8 * s], rot: [0, TAU], rotZ: [-7, 7], shape: { type: 'circle', r: R * 0.7, th: 1, y: 0 }, bursts: [[T, Math.round(18 * s)]], vel: { y: [6, 13], rad: [1, 5] }, gravity: 2.4, alpha: [[0, 1], [0.8, 1], [1, 0]] },
    { name: `${tag} Dust`, mat: 'dustN', fudge: 15, pos: [0, 0.4, 0], life: [0.8, 1.3], size: [R * 0.6, R * 1.1], rot: [0, TAU], shape: { type: 'circle', r: R * 0.9, th: 0.6, y: 0 }, bursts: [[T, Math.round(10 * s)]], vel: { rad: [1, 4], y: [0.3, 1.5] }, drag: 2, sizeLife: [[0, 0.5], [1, 1.6]], alpha: [[0, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
    { name: `${tag} Embers`, mat: 'dotF', fudge: -6, pos: [0, 0.4, 0], life: [1.0, 1.8], size: [0.05, 0.1], shape: { type: 'circle', r: R, th: 1, y: 0 }, bursts: [[T, Math.round(40 * s)]], vel: { y: [1.5, 5] }, noise: { s: 1, f: 1, sp: 0.7 }, color: [[0, YEL], [0.5, FIRE], [1, RED]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] }];
}
const S = [], HOLD = 8;
for (const [sfx, R] of [['', 3], ['_big', 6.5]]) {
  S.push({ id: 'portal_open' + sfx, role: 'static', orientation: 'facing', loop: false, duration: 1.5, layers: portalOpen('po' + sfx, R) });
  S.push({ id: 'portal_hold' + sfx, role: 'static', orientation: 'facing', loop: false, duration: HOLD, layers: gateBody('ph' + sfx, R, 0, HOLD, { hold: true, grow: 0.02 }).concat(gateFlow('pf' + sfx, R, 0, R / 3).map(l => Object.assign(l, { stopAt: HOLD - 0.6 }))) });
  S.push({ id: 'portal_close' + sfx, role: 'static', orientation: 'facing', loop: false, duration: 1.4, layers: portalClose('pc' + sfx, R) });
  S.push({ id: 'spawn_burst' + sfx, role: 'static', orientation: 'facing', loop: false, duration: 1.2, layers: spawnBurst('sb' + sfx, R) });
}
S.push({ id: 'rift', role: 'static', orientation: 'facing', loop: false, duration: 2.0, layers: rift('rf', 2.5) });
S.push({ id: 'rift_big', role: 'static', orientation: 'facing', loop: false, duration: 2.0, layers: rift('rfb', 6) });
S.forEach(s => s.layers.forEach(l => { delete l.delay; delete l.stopAt; }));

var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, SM, { vquad: VQ, ringV: ringV(0.05), ticksV: ticksV(24, 1.02, 1.12, 0.03) });
const O = HID(0, 0, 0);
const pour = (sfx, n, dt) => [{ t: 0.2, system: 'portal_open' + sfx, actor: 0 }, { t: 1.0, system: 'portal_hold' + sfx, actor: 0 }].concat(Array.from({ length: n }, (_, i) => ({ t: 1.5 + i * dt, system: 'spawn_burst' + sfx, actor: 0 })), [{ t: 8.6, system: 'portal_close' + sfx, actor: 0 }]);
const SCENE = {
  name: 'Siege_Portal', bloom: { threshold: 0.95, intensity: 1.35, scatter: 0.7, tint: [1, 0.82, 0.72] }, tonemapping: 'neutral', systems: S,
  demo: Object.assign({}, DEMO_BG, { interval: 11, dummy: { height: 1.8 }, scenarios: {
    portal: scn('지옥문: 열림 → 마수가 쏟아져 나옴(나올 때마다 터짐) → 닫힘', [O], pour('', 14, 0.5), cam([0, 3, 3], 22, -55, 16)),
    portal_side: scn('지옥문 · 옆에서', [O], pour('', 14, 0.5), cam([0, 3, 4], 22, -8, 10)),
    portal_big: scn('큰 지옥문 (공성 거수용)', [O], pour('_big', 4, 1.6), cam([0, 6, 5], 40, -55, 14)),
    rift: scn('땅 균열 (땅을 파고 올라오는 마수) · 여러 곳에서 연달아', [HID(0, 0), HID(-5, 3), HID(4, 5), HID(-2, 8), HID(6, -1), HID(-7, -2)], [0, 1, 2, 3, 4, 5].map(i => ({ t: 0.2 + i * 0.35, system: 'rift', actor: i })), cam([0, 2, 3], 24, -60, 30)),
    rift_big: scn('큰 땅 균열 (공성 거수)', [O], [{ t: 0.2, system: 'rift_big', actor: 0 }], cam([0, 3, 0], 30, -60, 28)),
  } }),
};
