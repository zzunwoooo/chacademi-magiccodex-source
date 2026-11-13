// ===== Siege_Beasts — 공성전 잡몹(네발 마수 8종) 짤패턴 VFX. F = 지옥불(주황), R = 진홍, N = 어둠(보라), G = 독(연두) =====
// 전부 static(ram_trail 만 attached): 원점 = 마수 발밑, +Z = 마수가 보는 방향. 모션(animation.siege_*.…)과 같은 순간에 재생하면 타격 시점이 맞는다.
// 빨간 예고 장판은 서버가 그린다 → 범위 표시는 넣지 않았다. 도약·돌진·텔포의 이동은 서버가 하고, 착지/충돌 연출은 그 지점에서 재생.
const WHITE = [1, 1, 1], FIRE = [1, 0.62, 0.22], YEL = [1, 0.9, 0.5], RED = [1, 0.25, 0.14], CRIM = [0.45, 0.03, 0.06], PUR = [0.62, 0.25, 0.9], DEEP = [0.2, 0.05, 0.35], LIME = [0.6, 1, 0.25], DGRN = [0.1, 0.4, 0.05];
const CF = { hot: YEL, mid: FIRE, low: RED }, CR = { hot: [1, 0.8, 0.6], mid: RED, low: CRIM }, CN = { hot: [0.95, 0.7, 1], mid: PUR, low: DEEP }, CG = { hot: [0.9, 1, 0.6], mid: LIME, low: DGRN };
const TH = { F: [1, 0.36, 0.07], R: [1, 0.09, 0.04], N: [0.42, 0.08, 0.85], G: [0.3, 0.9, 0.08] }, XM = {};
for (const [k, c] of Object.entries(TH)) { XM['gate' + k] = { tex: 'T_GateSigil', tint: c.map(v => v * 2.6), add: true }; XM['flame' + k] = { tex: 'T_Flame_Sheet', tint: c.map(v => v * 2.5), add: true }; XM['claw' + k] = { tex: 'T_ClawMark', tint: c.map(v => v * 2.7), add: true }; XM['spiral' + k] = { tex: 'T_Spiral', tint: c.map(v => v * 2.4), add: true }; }
XM.tongue = { tex: 'T_Band', tint: [2.2, 0.35, 0.5], add: true }; XM.needle = { tex: 'T_Band', tint: [1.0, 2.6, 0.4], add: true };
const MATS = mkMats(TH, XM);
KIT = KITS.demon;
const COL = { F: CF, R: CR, N: CN, G: CG };
// ---- 작은 부품 ----
const burst = (tag, p, t, s, sf, ground) => impactBurst(tag, p, t, s, MBs(sf, true), COL[sf], { n: clampN(s, 0.6, 2.2), ground: ground ?? true });
const ringG = (tag, p, t, r0, r1, sf, life, a) => ({ name: `${tag} Rg`, mat: 'line' + sf, mode: 'mesh', mesh: 'cOuter', fudge: 29, pos: [p[0], p[1] + 0.06, p[2]], size: 1, rot3: YAW, bursts: [[t, 1]], life: life ?? 0.5, sizeLife: [[0, r0, 0, (r1 - r0) * 2.4], [1, r1]], alpha: [[0, a ?? 1], [0.6, (a ?? 1) * 0.7], [1, 0]] });
const ringUp = (tag, p, t, r0, r1, sf, life) => ({ name: `${tag} RingV`, mat: 'line' + sf, mode: 'mesh', mesh: 'ringV', fudge: 3, pos: p, size: 1, rot3: [0, 0, 0], bursts: [[t, 1]], life: life ?? 0.45, sizeLife: [[0, r0, 0, (r1 - r0) * 2.4], [1, r1]], alpha: [[0, 1], [1, 0]] });
const crackQ = (tag, p, t, R, sf, life) => ({ name: `${tag} Crk`, mat: 'crack' + sf, mode: 'mesh', mesh: 'quad', fudge: 33, pos: [p[0], 0.04, p[2]], size: R * 2.1, rot3: YAW, bursts: [[t, 1]], life: life ?? 1.4, alpha: [[0, 1], [0.4, 0.7], [1, 0]] });
const clawB = (tag, p, t, size, sf, rot, life) => ({ name: `${tag} Claw`, mat: 'claw' + sf, fudge: -6, pos: p, size, rot: [rot, rot], bursts: [[t, 1]], life: life ?? 0.32, sizeLife: [[0, 0.5], [0.25, 1.1], [1, 1.2]], alpha: [[0, 1], [0.5, 1], [1, 0]] });
const sparks = (tag, p, t, n, sf, sp, o) => Object.assign({ name: `${tag} Spk`, mat: 'dot' + sf, mode: 'stretch', vs: 0.05, ls: 1.3, fudge: -5, pos: p, life: [0.4, 0.8], size: [0.05, 0.1], shape: { type: 'sphere', r: 0.3, th: 1 }, bursts: [[t, n]], speed: [sp * 0.4, sp], drag: 2, gravity: 0.7,
  color: [[0, COL[sf].hot], [0.5, COL[sf].mid], [1, COL[sf].low]], alpha: [[0, 1], [0.7, 1], [1, 0]] }, o && o.vel ? { speed: undefined } : {}, o || {});
const dust = (tag, p, t, n, R, o) => Object.assign({ name: `${tag} Dst`, mat: 'dustN', fudge: 15, pos: [p[0], p[1] + 0.4, p[2]], life: [0.7, 1.2], size: [R * 0.5, R * 0.9], rot: [0, TAU], shape: { type: 'circle', r: R * 0.6, th: 0.6, y: 0 }, bursts: [[t, n]], vel: { rad: [1, 4], y: [0.3, 1.5] }, drag: 2,
  sizeLife: [[0, 0.5], [1, 1.6]], alpha: [[0, 0.5], [1, 0]], flip: { x: 4, y: 4 } }, o || {});
const rocks = (tag, p, t, n, s, sf) => ({ name: `${tag} Rk`, mat: 'shardD' + (sf || 'R'), fudge: -3, pos: [p[0], p[1] + 0.3, p[2]], life: [0.7, 1.1], size: [0.3 * s, 0.8 * s], rot: [0, TAU], rotZ: [-7, 7], shape: { type: 'circle', r: 0.8 * s, th: 1, y: 0 }, bursts: [[t, n]], vel: { y: [5 * s ** 0.5, 12 * s ** 0.5], rad: [1, 5] }, gravity: 2.4, alpha: [[0, 1], [0.8, 1], [1, 0]] });
const flamesAt = (tag, p, ts, n, R, sf, sz) => ({ name: `${tag} Flm`, mat: 'flame' + (sf || 'F'), fudge: 3, pos: [p[0], p[1] + 0.4, p[2]], life: [0.55, 0.95], size: sz || [1.2, 2.2], shape: { type: 'circle', r: R, th: 1, y: 0 }, bursts: ts.map(t => [t, n]), vel: { y: [2, 5], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.3]], alpha: [[0, 0], [0.15, 0.85], [1, 0]], flip: { x: 4, y: 4 } });
const embers = (tag, p, t, n, R, sf) => ({ name: `${tag} Emb`, mat: 'dot' + (sf || 'F'), fudge: -6, pos: [p[0], p[1] + 0.4, p[2]], life: [1.0, 1.8], size: [0.05, 0.1], shape: { type: 'circle', r: R, th: 1, y: 0 }, bursts: [[t, n]], vel: { y: [1.5, 4.5] }, noise: { s: 1, f: 1, sp: 0.7 },
  color: [[0, COL[sf || 'F'].hot], [0.5, COL[sf || 'F'].mid], [1, COL[sf || 'F'].low]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] });
const glowB = (tag, p, t, size, sf, life, a) => ({ name: `${tag} Glw`, mat: 'glow' + sf, fudge: 6, pos: p, size, bursts: [[t, 1]], life, sizeLife: [[0, 0.3], [0.8, 1], [1, 1.2]], alpha: [[0, 0], [0.7, a ?? 0.5], [1, 0]] });
// 물기: 위아래 이빨 자국이 맞물린다
const bite = (tag, p, t, s, sf) => [clawB(tag + 'u', [p[0], p[1] + 0.5 * s, p[2]], t, 2.4 * s, sf, 0.6), clawB(tag + 'd', [p[0], p[1] - 0.3 * s, p[2]], t + 0.03, 2.4 * s, sf, -2.5),
  { name: `${tag} Snap`, mat: 'star' + sf, fudge: -8, pos: p, size: 2.2 * s, rot: [0, TAU], bursts: [[t, 1]], life: 0.18, sizeLife: [[0, 0.4], [0.3, 1.1], [1, 0]] }, sparks(tag, p, t, Math.round(12 * s), sf, 7 * s)];
const S = [];
// ================= 지옥견 =================
S.push(sysD('hound_bite', bite('hb', [0, 1.6, 2.6], 0.45, 0.9, 'F'), 1.0));
S.push(sysD('hound_lunge', [dust('hl0', [0, 0, 0], 0.35, 5, 1.6), ...bite('hl', [0, 1.4, 2.8], 0.6, 1.3, 'F'), ...burst('hlb', [0, 1.2, 2.8], 0.6, 0.6, 'R', false),
  ...[0.72, 0.84, 0.96, 1.08].map((t, i) => clawB('hls' + i, [(i % 2 ? -0.5 : 0.5), 1.3, 2.8], t, 1.9, i % 2 ? 'R' : 'F', i * 1.3)), sparks('hl2', [0, 1.3, 2.8], 0.72, 10, 'R', 6), sparks('hl3', [0, 1.3, 2.8], 0.96, 10, 'R', 6)], 1.5));
S.push(sysD('hound_howl', [...[0.6, 0.85, 1.1, 1.35, 1.6].map((t, i) => ringG('hw' + i, [0, 0, 0], t, 1, 8, i % 2 ? 'F' : 'R', 0.8, 0.8)), ...[0.6, 0.95, 1.3].map((t, i) => ringUp('hwv' + i, [0, 3.4, 1.6], t, 0.4, 2.6, 'R', 0.6)),
  glowB('hw', [0, 2.6, 1], 0.4, 5, 'R', 1.5, 0.45), embers('hw', [0, 0, 0], 0.6, 50, 7, 'R'), embers('hw2', [0, 0, 0], 1.2, 40, 7, 'F'),
  { name: 'hw Rally', mat: 'streakR', mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, life: [0.5, 0.8], size: [0.08, 0.16], shape: { type: 'circle', r: 6.5, th: 1, y: 0.1 }, bursts: [[0.7, 26], [1.1, 26], [1.5, 20]], vel: { y: [6, 12] }, drag: 2, alpha: [[0, 0], [0.2, 1], [1, 0]] }], 2.3));
// ================= 뿔 돌격수 =================
S.push(sysD('ram_toss', [...KIT.column('rt', [0, 0, 3.4], 0.55, 1.0, 'F', CF), ...burst('rtb', [0, 0, 3.4], 0.55, 1.1, 'F'), crackQ('rt', [0, 0, 3.4], 0.55, 2.6, 'F'), ringG('rt', [0, 0, 3.4], 0.55, 0.6, 3.6, 'F'),
  rocks('rt', [0, 0, 3.4], 0.55, 14, 1.3), dust('rt', [0, 0, 3.4], 0.58, 6, 2.4),
  { name: 'rt Up', mat: 'streakF', mode: 'stretch', vs: 0.06, ls: 3.4, fudge: -3, pos: [0, 0.3, 3.4], life: [0.35, 0.55], size: [0.1, 0.2], shape: { type: 'circle', r: 1.6, th: 1, y: 0 }, bursts: [[0.55, 24]], vel: { y: [12, 22] }, drag: 2, alpha: [[0, 1], [1, 0]] }], 1.4));
S.push(sysA('ram_trail', [
  { name: 'rtr Dust', mat: 'dustN', fudge: 15, pos: [0, 0.5, -1.5], life: [0.6, 1.0], size: [1.4, 2.4], rot: [0, TAU], shape: { type: 'box', sx: 2, sy: 0.2, sz: 1 }, rate: 16, world: true, vel: { y: [0.5, 1.5] }, sizeLife: [[0, 0.5], [1, 1.7]], alpha: [[0, 0.5], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'rtr Fire', mat: 'flameF', fudge: 3, pos: [0, 0.4, -1], life: [0.4, 0.7], size: [0.9, 1.6], shape: { type: 'box', sx: 2.2, sy: 0.2, sz: 0.6 }, rate: 14, world: true, vel: { y: [1.5, 3.5], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.2]], alpha: [[0, 0], [0.2, 0.8], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'rtr Lines', mat: 'streakF', mode: 'stretch', vs: 0.06, ls: 3, fudge: -4, pos: [0, 1.8, 1.5], life: [0.2, 0.35], size: [0.07, 0.14], shape: { type: 'box', sx: 2.6, sy: 2.6, sz: 0.4 }, rate: 34, vel: { z: [-22, -12] }, alpha: [[0, 0], [0.3, 0.9], [1, 0]] },
  { name: 'rtr Embers', mat: 'dotF', fudge: -6, pos: [0, 1, -1], life: [0.6, 1.2], size: [0.05, 0.1], shape: { type: 'box', sx: 2.4, sy: 1.6, sz: 1 }, rate: 30, world: true, vel: { y: [0.5, 3] }, noise: { s: 1, f: 1, sp: 0.7 }, color: [[0, YEL], [0.5, FIRE], [1, RED]], alpha: [[0, 1], [0.8, 0.8], [1, 0]] },
  { name: 'rtr HornGlow', mat: 'glowF', fudge: 6, pos: [0, 2.2, 3], size: 3.4, bursts: [[0, 1]], life: 2, alpha: [[0, 0.5], [0.5, 0.75], [1, 0.5]] }]));
S.push(sysD('ram_hit', [...burst('rhb', [0, 2, 3.6], 0.1, 1.5, 'F', false), ...burst('rhg', [0, 0, 3.6], 0.12, 1.0, 'R'), ringUp('rh', [0, 2.2, 3.8], 0.1, 0.6, 4.4, 'F'), ringUp('rh2', [0, 2.2, 3.8], 0.2, 0.6, 5.6, 'R', 0.55),
  crackQ('rh', [0, 0, 3.6], 0.1, 3.4, 'F'), rocks('rh', [0, 0, 3.6], 0.1, 20, 1.4), dust('rh', [0, 0, 3], 0.14, 10, 3),
  sparks('rh', [0, 2.2, 3.6], 0.1, 40, 'F', 14, { vel: { z: [-8, 4] } })], 1.2));
// ================= 가시 도마뱀 =================
S.push(sysD('lizard_sweep', [ringG('ls', [0, 0, 0], 0.4, 2, 7, 'F', 0.5), ringG('ls2', [0, 0, 0], 0.48, 2, 6.4, 'R', 0.5, 0.7), dust('ls', [0, 0, 0], 0.45, 14, 6.5, { shape: { type: 'circle', r: 5, th: 0.25, y: 0 } }),
  { name: 'ls Arc', mat: 'streakF', mode: 'stretch', vs: 0.06, ls: 2.6, fudge: -4, pos: [0, 1, 0], life: [0.25, 0.4], size: [0.1, 0.2], shape: { type: 'circle', r: 5.4, th: 0.12, y: 0 }, bursts: [[0.38, 30], [0.48, 30], [0.58, 24]], vel: { orb: [9, 14] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  sparks('ls', [0, 0.8, 0], 0.45, 30, 'F', 9, { shape: { type: 'circle', r: 5.2, th: 0.1, y: 0 } }), rocks('ls', [0, 0, 0], 0.5, 12, 1, 'R')], 1.3));
S.push(sysD('lizard_spines', [glowB('lq', [0, 2.2, 0], 0.2, 4.6, 'R', 0.65, 0.5), ringG('lq', [0, 0, 0], 0.8, 1, 4, 'R', 0.4), ringUp('lq', [0, 2.4, 1], 0.8, 0.5, 3, 'R'),
  { name: 'lq Spines', mat: 'streakR', mode: 'stretch', vs: 0.07, ls: 5, fudge: -4, pos: [0, 2.4, 0.5], life: [0.55, 0.7], size: [0.16, 0.24], shape: { type: 'box', sx: 2.2, sy: 0.6, sz: 2 }, bursts: [[0.8, 10], [0.86, 8]], vel: { z: [24, 32], y: [2, 5], x: [-5, 5] }, gravity: 1.4,
    color: [[0, [1, 0.95, 0.85]], [1, RED]], alpha: [[0, 1], [0.9, 1], [1, 0]] },
  { name: 'lq SpineGlow', mat: 'dotR', fudge: -5, pos: [0, 2.4, 0.5], life: [0.55, 0.7], size: [0.25, 0.4], shape: { type: 'box', sx: 2.2, sy: 0.6, sz: 2 }, bursts: [[0.8, 10], [0.86, 8]], vel: { z: [24, 32], y: [2, 5], x: [-5, 5] }, gravity: 1.4, alpha: [[0, 1], [1, 0.4]] },
  { name: 'lq Hits', mat: 'starR', fudge: -8, pos: [0, 0.5, 15], life: 0.2, size: [1, 1.8], rot: [0, TAU], shape: { type: 'box', sx: 9, sy: 0.4, sz: 7 }, bursts: [[1.38, 6], [1.46, 6], [1.54, 4]], sizeLife: [[0, 0.4], [0.3, 1.1], [1, 0]] },
  { name: 'lq HitSparks', mat: 'dotR', mode: 'stretch', vs: 0.05, ls: 1.3, fudge: -5, pos: [0, 0.4, 15], life: [0.3, 0.6], size: [0.05, 0.1], shape: { type: 'box', sx: 9, sy: 0.3, sz: 7 }, bursts: [[1.4, 40], [1.5, 30]], vel: { y: [2, 7], x: [-3, 3], z: [-3, 3] }, gravity: 1, color: [[0, CR.hot], [1, CR.low]], alpha: [[0, 1], [1, 0]] },
  dust('lq', [0, 0, 15], 1.45, 8, 6)], 2.2));
// ================= 불씨 두꺼비 =================
const glob = (dt, sz) => ({ name: `ts Glob${dt}`, mat: 'glowF', fudge: -6, pos: [0, 1.6, 1.6], size: sz, bursts: [[0.6 + dt, 1]], life: 0.9, vel: { z: 10.9, y: 7.2 }, gravity: 2, alpha: [[0, 1], [0.95, 1], [1, 0]] });
S.push(sysD('toad_spit', [glowB('ts', [0, 1.2, 1.2], 0.1, 3.4, 'F', 0.5, 0.6), sparks('ts', [0, 1.6, 1.6], 0.6, 14, 'F', 6), glob(0, 1.5), glob(0.04, 1.1), glob(0.08, 0.8), glob(0.12, 0.5),
  Object.assign(glob(0, 2.2), { name: 'ts GlobFlame', mat: 'flameF', flip: { x: 4, y: 4 } }),
  ...burst('tsb', [0, 0, 10], 1.5, 1.2, 'F'), ...KIT.column('tsk', [0, 0, 10], 1.5, 0.9, 'F', CF), crackQ('ts', [0, 0, 10], 1.5, 3, 'F', 3), ringG('ts', [0, 0, 10], 1.5, 0.5, 3.6, 'F'),
  flamesAt('tsp', [0, 0, 10], [1.6, 1.9, 2.2, 2.5, 2.8, 3.1, 3.4], 6, 2.6), embers('ts', [0, 0, 10], 1.55, 40, 2.8), embers('ts2', [0, 0, 10], 2.5, 26, 2.8)], 4.2));
S.push(sysD('toad_tongue', [
  { name: 'tt Tongue', mat: 'tongue', mode: 'mesh', mesh: 'lashZ', fudge: 2, pos: [0, 1.5, 1.2], size: 12, rot3: [0, 0, 0], bursts: [[0.35, 1]], life: 0.8, sizeLife: [[0, 0.02], [0.125, 1], [0.56, 1], [1, 0.02]], alpha: [[0, 1], [0.95, 1], [1, 0]] },
  { name: 'tt TongueCore', mat: 'bandF', mode: 'mesh', mesh: 'lashZ', fudge: 1, pos: [0, 1.55, 1.2], size: 12, rot3: [0, 0, 0], bursts: [[0.35, 1]], life: 0.8, sizeLife: [[0, 0.02], [0.125, 1], [0.56, 1], [1, 0.02]], alpha: [[0, 0.7], [0.95, 0.7], [1, 0]] },
  ...burst('ttb', [0, 1.5, 13.2], 0.45, 0.6, 'R', false), ringUp('tt', [0, 1.5, 13.2], 0.45, 0.3, 2, 'R'), glowB('tt', [0, 1.5, 13.2], 0.45, 2.4, 'R', 0.4, 0.8),
  { name: 'tt Pull', mat: 'streakR', mode: 'stretch', vs: 0.06, ls: 3, fudge: -4, pos: [0, 1.5, 7], life: [0.25, 0.4], size: [0.08, 0.16], shape: { type: 'box', sx: 1.2, sy: 1.2, sz: 12 }, bursts: [[0.8, 24], [0.9, 24], [1.0, 18]], vel: { z: [-34, -22] }, alpha: [[0, 0], [0.3, 1], [1, 0]] },
  dust('tt', [0, 0, 6], 0.85, 8, 3, { shape: { type: 'box', sx: 1.5, sy: 0.2, sz: 10 } }), ...bite('ttg', [0, 1.4, 2], 1.18, 1.0, 'F')], 1.8));
S.push(sysD('toad_burst', [glowB('tb', [0, 1.4, 0], 0, 7, 'F', 0.9, 0.8), flamesAt('tb0', [0, 0, 0], [0.3, 0.5, 0.7], 5, 1.5),
  ...burst('tbb', [0, 0.8, 0], 0.9, 1.9, 'F'), ...KIT.column('tbk', [0, 0, 0], 0.9, 1.7, 'F', CF), crackQ('tb', [0, 0, 0], 0.9, 5, 'F', 2.4), ringG('tb', [0, 0, 0], 0.9, 1, 5.5, 'F'), ringG('tb2', [0, 0, 0], 1.0, 1, 6.4, 'R', 0.6, 0.7),
  rocks('tb', [0, 0, 0], 0.9, 22, 1.5), flamesAt('tb1', [0, 0, 0], [0.95, 1.1, 1.3, 1.6], 12, 4.4, 'F', [1.6, 3]), embers('tb', [0, 0, 0], 0.95, 120, 5), dust('tb', [0, 0, 0], 0.95, 12, 5)], 2.6));
// ================= 쌍두 마견 =================
S.push(sysD('twin_bite', [...bite('wb1', [1.1, 2.2, 3.2], 0.5, 1.1, 'F'), ...bite('wb2', [-1.1, 2.2, 3.2], 0.68, 1.1, 'N')], 1.2));
const stream = (tag, x0, sgn, sf) => Array.from({ length: 6 }, (_, k) => { const t = 0.7 + k * 0.2, vx = sgn * (5 - k * 2);
  return { name: `${tag} Stream${k}`, mat: 'flame' + sf, fudge: 3, pos: [x0, 2.6, 3], life: [0.6, 0.85], size: [1.4, 2.4], shape: { type: 'sphere', r: 0.4, th: 1 }, bursts: [[t, 5], [t + 0.07, 5], [t + 0.14, 4]], vel: { z: [10, 14], x: [vx - 1, vx + 1], y: [-1.6, -0.4] }, drag: 0.6,
    sizeLife: [[0, 0.35], [1, 1.7]], alpha: [[0, 0], [0.12, 0.9], [0.7, 0.7], [1, 0]], flip: { x: 4, y: 4 } }; });
S.push(sysD('twin_breath', [glowB('wr1', [1.1, 2.8, 2.6], 0.2, 2.6, 'F', 0.5, 0.7), glowB('wr2', [-1.1, 2.8, 2.6], 0.2, 2.6, 'N', 0.5, 0.7), ...stream('wr1', 1.1, 1, 'F'), ...stream('wr2', -1.1, -1, 'N'),
  { name: 'wr Scorch', mat: 'crackF', mode: 'mesh', mesh: 'quad', fudge: 33, pos: [0, 0.04, 9], size: 13, rot3: [0, 0, 0], bursts: [[0.9, 1]], life: 2.2, alpha: [[0, 0], [0.2, 0.7], [0.7, 0.6], [1, 0]] },
  { name: 'wr Sparks', mat: 'dotF', mode: 'stretch', vs: 0.05, ls: 1.4, fudge: -5, pos: [0, 2.4, 3], life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: 'box', sx: 3, sy: 0.6, sz: 0.4 }, bursts: [0.7, 0.9, 1.1, 1.3, 1.5, 1.7].map(t => [t, 16]), vel: { z: [8, 18], x: [-6, 6], y: [-2, 2] }, drag: 1.2,
    color: [[0, YEL], [0.5, FIRE], [1, PUR]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  flamesAt('wrg', [0, 0, 9], [1.0, 1.25, 1.5, 1.75, 2.0], 7, 4.5), embers('wr', [0, 0, 9], 1.2, 60, 5), embers('wr2', [0, 0, 9], 1.8, 40, 5, 'N')], 3.0));
// ================= 공성 거수 =================
S.push(sysD('behemoth_quake', [dust('bq0', [0, 0, 4], 0.7, 6, 5), ...burst('bqb', [0, 0, 5], 1.05, 2.4, 'F'), ...KIT.column('bqk', [3, 0, 5], 1.05, 1.4, 'F', CF), ...KIT.column('bqk2', [-3, 0, 5], 1.05, 1.4, 'F', CF),
  crackQ('bq', [0, 0, 5], 1.05, 10, 'F', 2.6), crackQ('bq2', [0, 0, 5], 1.1, 6, 'R', 2), ...[0, 0.14, 0.28].map((dt, i) => ringG('bq' + i, [0, 0, 5], 1.05 + dt, 2, 10 + i, i % 2 ? 'R' : 'F', 0.75)),
  rocks('bq', [0, 0, 5], 1.05, 44, 2.6), dust('bq', [0, 0, 5], 1.08, 24, 10, { shape: { type: 'circle', r: 8, th: 0.5, y: 0 } }),
  { name: 'bq Spikes', mat: 'pillarF', mode: 'mesh', mesh: 'pilT', fudge: 4, pos: [0, 0, 5], size: [2, 4], rot3: YAW, shape: { type: 'circle', r: 8, th: 0.6, y: 0 }, bursts: [[1.1, 14], [1.2, 12]], life: 0.5, sizeLife: [[0, 0.2], [0.25, 1], [1, 0.9]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  embers('bq', [0, 0, 5], 1.1, 140, 9)], 3.0));
S.push(sysD('behemoth_ram', [dust('br0', [0, 0, 0], 0.4, 8, 5), ...burst('brb', [0, 5, 8], 1.45, 2.6, 'F', false), ...burst('brg', [0, 0, 8], 1.48, 1.6, 'R'),
  ...[0, 0.1, 0.22].map((dt, i) => ringUp('br' + i, [0, 5, 8.2], 1.45 + dt, 1, 7 + i * 2.4, i % 2 ? 'R' : 'F', 0.6)), crackQ('br', [0, 0, 8], 1.45, 7, 'F', 2.4),
  rocks('br', [0, 0, 8], 1.45, 40, 2.4), dust('br', [0, 0, 7], 1.5, 18, 7),
  sparks('br', [0, 5, 8], 1.45, 90, 'F', 20, { vel: { z: [-14, 2] }, size: [0.08, 0.16] }),
  { name: 'br Debris', mat: 'shardDR', fudge: -3, pos: [0, 5, 8], life: [0.8, 1.3], size: [0.5, 1.4], rot: [0, TAU], rotZ: [-7, 7], shape: { type: 'box', sx: 6, sy: 6, sz: 0.5 }, bursts: [[1.45, 30]], vel: { z: [-10, -2], y: [2, 9], x: [-6, 6] }, gravity: 2.2, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  flamesAt('br', [0, 0, 8], [1.5, 1.7, 1.95], 12, 4, 'F', [2, 3.6]), embers('br', [0, 0, 8], 1.5, 120, 6)], 3.0));
// ================= 날개 추적자 =================
S.push(sysD('stalker_claw', [...[0, 1, 2].map(i => clawB('sc' + i, [0.3 - i * 0.3, 2.2 - i * 0.25, 2.8], 0.42 + i * 0.025, 3.0, 'N', -0.9)), ...burst('scb', [0, 1.8, 2.8], 0.42, 0.55, 'N', false), sparks('sc', [0, 1.8, 2.8], 0.42, 22, 'N', 9, { vel: { x: [-9, -2], y: [-6, 0] } })], 0.9));
S.push(sysD('stalker_pounce', [dust('sp0', [0, 0, 0], 0.35, 6, 2), ...burst('spb', [0, 0, 0], 0.85, 1.2, 'N'), ringG('sp', [0, 0, 0], 0.85, 0.8, 4.2, 'N'), crackQ('sp', [0, 0, 0], 0.85, 3.2, 'N'),
  { name: 'sp ClawL', mat: 'clawN', mode: 'mesh', mesh: 'quad', fudge: 31, pos: [0.9, 0.07, 1.6], size: 3, rot3: [0, 0.3, 0], bursts: [[0.85, 1]], life: 0.9, alpha: [[0, 1], [0.5, 0.8], [1, 0]] },
  { name: 'sp ClawR', mat: 'clawN', mode: 'mesh', mesh: 'quad', fudge: 31, pos: [-0.9, 0.07, 1.6], size: 3, rot3: [0, -0.3, 0], bursts: [[0.85, 1]], life: 0.9, alpha: [[0, 1], [0.5, 0.8], [1, 0]] },
  { name: 'sp Fall', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 3, fudge: -4, pos: [0, 3, -1], life: [0.2, 0.3], size: [0.1, 0.2], shape: { type: 'sphere', r: 1.6, th: 1 }, bursts: [[0.72, 20]], vel: { y: [-22, -12], z: [3, 8] }, alpha: [[0, 0], [0.3, 1], [1, 0]] },
  rocks('sp', [0, 0, 0], 0.85, 12, 1, 'N'), dust('sp', [0, 0, 0], 0.88, 10, 3.6), ...bite('spj', [0, 1.2, 2.6], 0.88, 1.0, 'N')], 1.6));
S.push(sysD('stalker_dive', [{ name: 'sd Gust', mat: 'dustN', fudge: 15, pos: [0, 0.4, 0], life: [0.5, 0.8], size: [1.4, 2.4], rot: [0, TAU], shape: { type: 'circle', r: 2.4, th: 0.4, y: 0 }, bursts: [[0.3, 6], [0.45, 5], [0.6, 5], [0.75, 4]], vel: { rad: [3, 7] }, drag: 2, sizeLife: [[0, 0.5], [1, 1.6]], alpha: [[0, 0.4], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'sd Streak', mat: 'pillarN', mode: 'mesh', mesh: 'pilT', fudge: 2, pos: [0, 0, 0], size: 14, rot3: YAW, bursts: [[1.2, 1]], life: 0.35, sizeLife: [[0, 1], [0.3, 1], [1, 0.3]], alpha: [[0, 0], [0.25, 1], [0.6, 0.8], [1, 0]] },
  { name: 'sd Fall', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 4, fudge: -4, pos: [0, 8, 0], life: [0.2, 0.3], size: [0.1, 0.22], shape: { type: 'sphere', r: 1.8, th: 1 }, bursts: [[1.18, 30]], vel: { y: [-40, -24] }, alpha: [[0, 0], [0.3, 1], [1, 0]] },
  ...burst('sdb', [0, 0, 0], 1.3, 1.6, 'N'), ...KIT.column('sdk', [0, 0, 0], 1.3, 1.1, 'N', CN), ringG('sd', [0, 0, 0], 1.3, 1, 5.4, 'N'), ringG('sd2', [0, 0, 0], 1.4, 1, 6.2, 'R', 0.55, 0.7), crackQ('sd', [0, 0, 0], 1.3, 4.6, 'N', 2),
  rocks('sd', [0, 0, 0], 1.3, 22, 1.5, 'N'), dust('sd', [0, 0, 0], 1.33, 14, 5), embers('sd', [0, 0, 0], 1.35, 70, 4.5, 'N')], 2.6));
// ================= 독침 마수 =================
const venom = (tag, p, t, n, R) => ({ name: `${tag} Venom`, mat: 'dotG', fudge: -5, pos: [p[0], p[1] + 0.4, p[2]], life: [0.6, 1.1], size: [0.1, 0.22], shape: { type: 'sphere', r: 0.3, th: 1 }, bursts: [[t, n]], vel: { y: [3, 9], rad: [1, R * 2.2] }, gravity: 2, color: [[0, CG.hot], [0.6, CG.mid], [1, CG.low]], alpha: [[0, 1], [0.8, 1], [1, 0]] });
const cloud = (tag, p, ts, n, R) => ({ name: `${tag} Cloud`, mat: 'dustG', fudge: 15, pos: [p[0], p[1] + 0.7, p[2]], life: [1.0, 1.6], size: [R * 0.7, R * 1.2], rot: [0, TAU], shape: { type: 'circle', r: R * 0.7, th: 1, y: 0 }, bursts: ts.map(t => [t, n]), vel: { y: [0.2, 0.9] }, noise: { s: 0.6, f: 0.8, sp: 0.5 }, sizeLife: [[0, 0.5], [1, 1.5]], alpha: [[0, 0], [0.25, 0.45], [1, 0]], flip: { x: 4, y: 4 } });
S.push(sysD('stinger_stab', [{ name: 'ss Spike', mat: 'pillarG', mode: 'mesh', mesh: 'pilT', fudge: 2, pos: [0, 0, 4.6], size: 5, rot3: YAW, bursts: [[0.47, 1]], life: 0.35, sizeLife: [[0, 1.6], [0.2, 1], [1, 0.8]], alpha: [[0, 0], [0.15, 1], [0.6, 0.9], [1, 0]] },
  ...burst('ssb', [0, 0, 4.6], 0.5, 0.8, 'G'), crackQ('ss', [0, 0, 4.6], 0.5, 2.2, 'G', 2.2), ringG('ss', [0, 0, 4.6], 0.5, 0.4, 2.6, 'G'), venom('ss', [0, 0, 4.6], 0.5, 26, 1.6), cloud('ss', [0, 0, 4.6], [0.55, 0.9, 1.3], 4, 2)], 2.4));
S.push(sysD('stinger_shot', [glowB('sh', [0, 3.6, 0.5], 0.1, 3, 'G', 0.6, 0.8), ringUp('sh', [0, 3.4, 1.6], 0.7, 0.3, 2.2, 'G'), sparks('sh', [0, 3.4, 1.6], 0.7, 14, 'G', 7),
  { name: 'sh Line', mat: 'needle', mode: 'mesh', mesh: 'lashZ', fudge: 1, pos: [0, 2.2, 1.6], size: 16, rot3: [0, 0, 0], bursts: [[0.7, 1]], life: 0.3, sizeLife: [[0, 0.02], [0.3, 1], [1, 1]], alpha: [[0, 1], [0.4, 0.9], [1, 0]] },
  { name: 'sh Needles', mat: 'streakG', mode: 'stretch', vs: 0.07, ls: 6, fudge: -4, pos: [0, 2.3, 1.6], life: [0.3, 0.36], size: [0.14, 0.2], shape: { type: 'sphere', r: 0.2, th: 1 }, bursts: [[0.7, 3], [0.74, 2]], vel: { z: [44, 50], y: [-3.4, -2.6] }, alpha: [[0, 1], [0.9, 1], [1, 0]] },
  { name: 'sh Drip', mat: 'dotG', fudge: -5, pos: [0, 2.2, 9], life: [0.5, 0.9], size: [0.06, 0.12], shape: { type: 'box', sx: 0.3, sy: 0.3, sz: 15 }, bursts: [[0.78, 40]], vel: { y: [-2.5, -0.5] }, gravity: 1, color: [[0, CG.hot], [1, CG.low]], alpha: [[0, 1], [1, 0]] },
  ...burst('shb', [0, 1.2, 17.4], 1.02, 0.9, 'G', false), ringG('sh', [0, 0, 17.4], 1.04, 0.4, 3, 'G'), crackQ('sh', [0, 0, 17.4], 1.04, 2.4, 'G', 2.4), venom('sh', [0, 0.6, 17.4], 1.02, 30, 2), cloud('sh', [0, 0, 17.4], [1.06, 1.4, 1.8, 2.2], 5, 2.6)], 3.6));
const swirl = (tag, t, life, grow, sf) => [{ name: `${tag} Swirl`, mat: 'spiral' + sf, mode: 'mesh', mesh: 'quad', fudge: 34, pos: [0, 0.06, 0], size: 4.2, rot3: YAW, rotLife: [0, grow ? -5 : 5, 0], bursts: [[t, 1]], life, sizeLife: grow ? [[0, 0.1], [0.4, 1], [1, 1.1]] : [[0, 1.1], [0.6, 0.9], [1, 0.05]], alpha: [[0, 0], [0.2, 1], [0.8, 0.9], [1, 0]] },
  { name: `${tag} Swirl2`, mat: 'spiralG', mode: 'mesh', mesh: 'quad', fudge: 33, pos: [0, 0.08, 0], size: 2.6, rot3: YAW, rotLife: [0, grow ? 7 : -7, 0], bursts: [[t, 1]], life, sizeLife: grow ? [[0, 0.1], [0.4, 1], [1, 1.1]] : [[0, 1.1], [0.6, 0.9], [1, 0.05]], alpha: [[0, 0], [0.2, 0.8], [0.8, 0.7], [1, 0]] }];
S.push(sysD('stinger_blink_out', [...swirl('bo', 0, 0.8, false, 'N'), dust('bo', [0, 0, 0], 0.2, 8, 3), rocks('bo', [0, 0, 0], 0.25, 8, 0.8, 'N'),
  { name: 'bo Sink', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 3, fudge: -4, pos: [0, 2.4, 0], life: [0.25, 0.4], size: [0.08, 0.18], shape: { type: 'sphere', r: 2, th: 0.6 }, bursts: [[0.25, 20], [0.4, 20], [0.52, 14]], vel: { y: [-12, -6] }, alpha: [[0, 0], [0.3, 1], [1, 0]] },
  ...burst('bob', [0, 0.3, 0], 0.6, 0.7, 'N'), ringG('bo', [0, 0, 0], 0.6, 2.6, 0.4, 'N', 0.35), cloud('bo', [0, 0, 0], [0.6], 4, 2)], 1.4));
S.push(sysD('stinger_blink_in', [...swirl('bi', 0, 0.9, true, 'N'), crackQ('bi', [0, 0, 0], 0.22, 3.4, 'G', 1.8), ...burst('bib', [0, 0, 0], 0.26, 1.2, 'N'), ringG('bi', [0, 0, 0], 0.26, 0.6, 4.4, 'G'),
  rocks('bi', [0, 0, 0], 0.24, 20, 1.3, 'N'), dust('bi', [0, 0, 0], 0.28, 12, 3.6),
  { name: 'bi Rise', mat: 'streakN', mode: 'stretch', vs: 0.06, ls: 3, fudge: -4, pos: [0, 0.3, 0], life: [0.3, 0.45], size: [0.08, 0.18], shape: { type: 'circle', r: 2, th: 1, y: 0 }, bursts: [[0.22, 30]], vel: { y: [10, 20] }, drag: 2, alpha: [[0, 1], [1, 0]] },
  { name: 'bi Spike', mat: 'pillarG', mode: 'mesh', mesh: 'pilT', fudge: 2, pos: [0, 0, 4.6], size: 5, rot3: YAW, bursts: [[0.39, 1]], life: 0.35, sizeLife: [[0, 1.6], [0.2, 1], [1, 0.8]], alpha: [[0, 0], [0.15, 1], [0.6, 0.9], [1, 0]] },
  ...burst('bis', [0, 0, 4.6], 0.42, 0.8, 'G'), venom('bi', [0, 0, 4.6], 0.42, 26, 1.6), cloud('bi', [0, 0, 4.6], [0.46, 0.8, 1.2], 4, 2)], 2.2));

S.forEach(x => x.layers.forEach(l => { if (l.speed === undefined) delete l.speed; }));
var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, SM, { ringV: ringV(0.05), lashZ: { verts: [[-0.018, 0, 0], [0.018, 0, 0], [-0.018, 0, 1], [0.018, 0, 1]], uvs: [[0, 0.5], [1, 0.5], [0, 0.5], [1, 0.5]].map((u, i) => [i % 2, i < 2 ? 0 : 1]), tris: [0, 1, 2, 1, 3, 2] } });
const O = HID(0, 0, 0), one = (label, id, c) => scn(label, [O], [{ t: 0.2, system: id, actor: 0 }], c);
const SCENE = {
  name: 'Siege_Beasts', bloom: { threshold: 0.95, intensity: 1.35, scatter: 0.7, tint: [1, 0.82, 0.72] }, tonemapping: 'neutral', systems: S,
  demo: Object.assign({}, DEMO_BG, { interval: 5, dummy: { height: 1.8 }, scenarios: {
    hound_bite: one('지옥견 · 물기', 'hound_bite', cam([0, 1.5, 2], 12, -60, 14)), hound_lunge: one('지옥견 · 도약 물어뜯기', 'hound_lunge', cam([0, 1.5, 2], 13, -60, 14)), hound_howl: one('지옥견 · 무리 울음', 'hound_howl', cam([0, 2, 0], 26, -60, 28)),
    ram_toss: one('뿔 돌격수 · 뿔 치켜올리기', 'ram_toss', cam([0, 2, 3], 16, -60, 16)),
    ram_charge: scn('뿔 돌격수 · 돌진(불길 자국) → 충돌', [MOVER([[0, [0, 0, -14], 0], [0.2, [0, 0, -14], 0], [1.5, [0, 0, 6], 0], [4, [0, 0, 6], 0]])], [{ t: 0.2, system: 'ram_trail', actor: 0 }, { t: 1.5, stop: ['ram_trail'] }, { t: 1.4, system: 'ram_hit', actor: 0 }], cam([0, 2, 0], 30, -50, 22)),
    lizard_sweep: one('가시 도마뱀 · 꼬리 쓸기', 'lizard_sweep', cam([0, 1, 0], 20, -60, 34)), lizard_spines: one('가시 도마뱀 · 등 가시 발사 (원거리)', 'lizard_spines', cam([0, 2, 8], 30, -35, 20)),
    toad_spit: one('불씨 두꺼비 · 불씨 뱉기 (원거리)', 'toad_spit', cam([0, 2, 5], 22, -30, 16)), toad_tongue: one('불씨 두꺼비 · 혀 끌어당기기 (끌당)', 'toad_tongue', cam([0, 2, 6], 22, -35, 18)), toad_burst: one('불씨 두꺼비 · 자폭', 'toad_burst', cam([0, 2, 0], 22, -60, 26)),
    twin_bite: one('쌍두 마견 · 두 번 물기', 'twin_bite', cam([0, 2, 2.5], 13, -60, 12)), twin_breath: one('쌍두 마견 · 쌍두 화염 숨결 (원거리)', 'twin_breath', cam([0, 2, 7], 26, -40, 22)),
    behemoth_quake: one('공성 거수 · 지진 발구르기', 'behemoth_quake', cam([0, 3, 5], 40, -60, 30)), behemoth_ram: one('공성 거수 · 성문 들이받기', 'behemoth_ram', cam([0, 4, 6], 36, -35, 16)),
    stalker_claw: one('날개 추적자 · 할퀴기', 'stalker_claw', cam([0, 1.8, 2], 12, -60, 12)), stalker_pounce: one('날개 추적자 · 도약 덮치기', 'stalker_pounce', cam([0, 1.5, 0], 18, -60, 24)), stalker_dive: one('날개 추적자 · 급강하', 'stalker_dive', cam([0, 4, 0], 28, -60, 20)),
    stinger_stab: one('독침 마수 · 꼬리 찌르기', 'stinger_stab', cam([0, 1.5, 3.5], 14, -55, 20)), stinger_shot: one('독침 마수 · 독침 발사 (원거리)', 'stinger_shot', cam([0, 2, 9], 30, -32, 18)),
    stinger_blink: scn('독침 마수 · 땅속 이동 (사라짐 → 다른 곳에서 솟구쳐 찌름)', [HID(-5, 0, 0), HID(5, 4, 180)], [{ t: 0.2, system: 'stinger_blink_out', actor: 0 }, { t: 1.4, system: 'stinger_blink_in', actor: 1 }], cam([0, 1.5, 2], 24, -60, 28)),
  } }),
};
