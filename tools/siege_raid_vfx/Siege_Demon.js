// ===== Siege_Demon — 공성전 최종 · 해방된 악마(전성기) 짤패턴 VFX. F = 지옥불(주황), R = 진홍, N = 어둠(보라) =====
const WHITE = [1, 1, 1], FIRE = [1, 0.62, 0.22], YEL = [1, 0.9, 0.5], RED = [1, 0.25, 0.14], CRIM = [0.45, 0.03, 0.06], PUR = [0.62, 0.25, 0.9], DEEP = [0.2, 0.05, 0.35];
const CF = { hot: YEL, mid: FIRE, low: RED }, CR = { hot: [1, 0.8, 0.6], mid: RED, low: CRIM }, CN = { hot: [0.95, 0.7, 1], mid: PUR, low: DEEP };
const MATS = mkMats({ F: [1, 0.36, 0.07], R: [1, 0.09, 0.04], N: [0.42, 0.08, 0.85] }, {
  gateF: { tex: 'T_GateSigil', tint: [2.7, 1.0, 0.2], add: true }, gateR: { tex: 'T_GateSigil', tint: [2.7, 0.3, 0.12], add: true }, gateN: { tex: 'T_GateSigil', tint: [1.3, 0.3, 2.5], add: true },
  flameF: { tex: 'T_Flame_Sheet', tint: [2.5, 0.95, 0.2], add: true }, flameR: { tex: 'T_Flame_Sheet', tint: [2.5, 0.28, 0.1], add: true }, flameN: { tex: 'T_Flame_Sheet', tint: [1.2, 0.28, 2.3], add: true },
  clawF: { tex: 'T_ClawMark', tint: [2.7, 1.0, 0.2], add: true }, clawR: { tex: 'T_ClawMark', tint: [2.7, 0.3, 0.12], add: true }, clawN: { tex: 'T_ClawMark', tint: [1.3, 0.3, 2.5], add: true },
  blade: { tex: 'T_Blade', tint: [1.5, 0.45, 2.4], add: true }, bladeF: { tex: 'T_Blade', tint: [2.6, 0.9, 0.2], add: true } });
KIT = KITS.demon;
const flames = (tag, t, n, pos, shape, sz, sf) => ({ name: `${tag} Flames`, mat: 'flame' + (sf || 'F'), fudge: 3, pos, life: [0.55, 0.95], size: sz || [1.8, 3], shape, bursts: Array.isArray(t) ? t.map((x, i) => [x, Math.round(n * (1 - i * 0.3))]) : [[t, n]],
  vel: { y: [2.5, 6], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.3]], alpha: [[0, 0], [0.15, 0.85], [1, 0]], flip: { x: 4, y: 4 } });
const embers = (tag, t, n, pos, shape) => ({ name: `${tag} Embers`, mat: 'dotF', fudge: -6, pos, life: [1.0, 1.9], size: [0.05, 0.1], shape, bursts: [[t, n]], vel: { y: [1.5, 4] }, noise: { s: 1, f: 1, sp: 0.7 }, color: [[0, YEL], [0.5, FIRE], [1, RED]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] });
const swordFall = (tag, T, z, h) => ({ name: `${tag} Blade`, mat: 'blade', mode: 'mesh', mesh: 'spear', fudge: 5, pos: [0, 0, z], size: h, rot3: [0, 0.5, 0], bursts: [[T - 0.1, 1]], life: 0.75, sizeLife: [[0, 1.8], [0.13, 1], [1, 1]], alpha: [[0, 0], [0.1, 1], [0.7, 1], [1, 0]] });

const S = [];
// 해방된 악마의 공격 방식: 크고, 빠르고, 몸으로 직접 온다. 지나간 자리는 불탄다.
const swingFan = (tag, dir) => blastFan(tag, 150 * D2R, 10, 0.45, 'F', CF, { s2: 'R', rise: 1.3, extra: [...swingBlade(tag, 150 * D2R, 10, 0.45, dir, 'bandF', 'pillarR'),
  flames(tag, [0.5, 0.62], 22, [0, 0.6, 4.5], { type: 'box', sx: 17, sy: 0.3, sz: 8 }), embers(tag, 0.5, 40, [0, 0.4, 4.5], { type: 'box', sx: 18, sy: 0.3, sz: 9 })] });
// 1. 대검 3연격 — 오른쪽 휘두르기 → 왼쪽 휘두르기 → 정면 내려베기. 한 번 벨 때마다 한 걸음씩 다가온다
S.push(sys('swing_r', swingFan('sr', 1), 0.45)); S.push(sys('swing_l', swingFan('sl', -1), 0.45));
S.push(sys('cleave', blastLane('cv', 5, 18, 0.5, 'F', CF, { s2: 'R', h: 7, extra: [swordFall('cv', 0.5, 8, 16), flames('cv', [0.52, 0.64], 22, [0, 0.6, 9], { type: 'box', sx: 4.5, sy: 0.3, sz: 17 }), embers('cv', 0.6, 40, [0, 0.4, 9], { type: 'box', sx: 5, sy: 0.3, sz: 18 })] }), 0.5));
// 2. 도약 강타 — 플레이어 위치로 뛰어올라 내려찍고(구덩이), 충격파가 1.2초에 걸쳐 전장으로 퍼진다(뛰어넘거나 벗어나야 함)
S.push(sys('leap_crater', blastCircle('lc', 6, 0.7, 'F', CF, { s2: 'R', extra: [
  { name: 'lc Shadow', mat: 'mist', mode: 'mesh', mesh: 'quad', fudge: 36, pos: [0, 0.04, 0], size: 12, rot3: YAW, bursts: [[0, 1]], life: 0.7, sizeLife: [[0, 0.2], [1, 1]], alpha: [[0, 0], [0.9, 0.8], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'lc Body', mat: 'flareN', fudge: -9, pos: [0, 26, 0], size: 12, rot: [0, TAU], bursts: [[0.45, 1]], life: 0.26, vel: { y: [-96, -96] }, alpha: [[0, 0], [0.2, 1], [1, 1]] },
  flames('lc', [0.72, 0.86, 1.0], 26, [0, 0.6, 0], { type: 'circle', r: 5.6, th: 1, y: 0 }, [2.4, 4]), embers('lc', 0.8, 60, [0, 0.4, 0], { type: 'circle', r: 6, th: 1, y: 0 })] }), 0.7));
S.push(sysD('leap_wave', travelRing('lw', 6, 24, 0, 1.2, 'R', CR, { s2: 'F' }), 1.6));
// 3. 지옥 돌진 — 전장을 가로질러 돌진, 지나간 길은 5초 동안 불탄다
S.push(sys('dash', blastLane('da', 6, 32, 0.8, 'F', CF, { s2: 'N', h: 6, extra: [
  { name: 'da Body', mat: 'flareN', fudge: -7, pos: [0, 4, 0], size: [9, 11], rot: [0, TAU], bursts: [[0.8, 1], [0.84, 1], [0.88, 1], [0.92, 1]], life: 0.5, vel: { z: [64, 64] }, alpha: [[0, 0.8], [1, 0]] }] }), 0.8));
S.push({ id: 'dash_fire', role: 'static', orientation: 'facing', loop: false, duration: 5, layers: [
  { name: 'df Flames', mat: 'flameF', fudge: 3, pos: [0, 1.2, 16], life: [0.6, 0.95], size: [2.2, 3.8], shape: { type: 'box', sx: 5.5, sy: 0.3, sz: 31 }, rate: 46, vel: { y: [2.5, 6], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.3]], alpha: [[0, 0], [0.15, 0.8], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'df FlamesR', mat: 'flameR', fudge: 4, pos: [0, 0.9, 16], life: [0.5, 0.8], size: [1.6, 2.8], shape: { type: 'box', sx: 5.8, sy: 0.3, sz: 31.5 }, rate: 30, vel: { y: [1.5, 4], space: 'world' }, sizeLife: [[0, 0.5], [1, 1.2]], alpha: [[0, 0], [0.15, 0.7], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'df Embers', mat: 'dotF', fudge: -6, pos: [0, 0.4, 16], life: [1.0, 1.8], size: [0.05, 0.1], shape: { type: 'box', sx: 6, sy: 0.3, sz: 32 }, rate: 50, vel: { y: [2, 5] }, noise: { s: 1, f: 1, sp: 0.7 }, color: [[0, YEL], [0.5, FIRE], [1, RED]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] },
  { name: 'df Scorch', mat: 'crackF', mode: 'mesh', mesh: 'daCrack', fudge: 33, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 5.4, alpha: [[0, 0], [0.05, 0.8], [0.85, 0.6], [1, 0]] },
  { name: 'df Mist', mat: 'mist', fudge: 25, pos: [0, 1.6, 16], life: [1.2, 1.8], size: [2, 3.4], rot: [0, TAU], shape: { type: 'box', sx: 5, sy: 0.3, sz: 30 }, rate: 6, vel: { y: [0.8, 1.6], space: 'world' }, sizeLife: [[0, 0.6], [1, 1.4]], alpha: [[0, 0], [0.2, 0.4], [1, 0]], flip: { x: 4, y: 4 } }] });
// 4. 마검 투척 — 마검이 꽂혀 1.5초 달아오른 뒤 폭발하고, 꽂힌 자리에서 보스 손으로 되돌아오며 그 길을 벤다
S.push(sys('sword_throw', blastCircle('sx', 7, 1.9, 'N', CN, { s2: 'F', extra: [
  { name: 'sx Fly', mat: 'blade', mode: 'mesh', mesh: 'spear', fudge: 5, pos: [0, 30, -18], size: 14, rot3: [-0.55, 0.5, 0], bursts: [[0, 1]], life: 0.32, vel: { y: [-94, -94], z: [56, 56] }, alpha: [[0, 0], [0.15, 1], [1, 1]] },
  { name: 'sx Stuck', mat: 'blade', mode: 'mesh', mesh: 'spear', fudge: 5, size: 13, rot3: [-0.25, 0.5, 0], bursts: [[0.32, 1]], life: 1.75, alpha: [[0, 1], [0.9, 1], [1, 0]] },
  { name: 'sx StuckGlow', mat: 'bladeF', mode: 'mesh', mesh: 'spear', fudge: 4, size: 13.4, rot3: [-0.25, 0.5, 0], bursts: [[0.32, 1]], life: 1.6, alpha: [[0, 0], [0.9, 1], [1, 0.6]] },
  ...miniBurst('sxLand', [0, 0, 0], 0.32, 1.4, 'N', CN),
  flames('sx', [1.92, 2.06, 2.2], 30, [0, 0.6, 0], { type: 'circle', r: 6.5, th: 1, y: 0 }, [2.4, 4], 'N')] }), 1.9));
S.push(sys('sword_recall', blastLane('sc', 3, 22, 0.3, 'N', CN, { s2: 'R', h: 4, extra: [
  { name: 'sc Blade', mat: 'blade', mode: 'mesh', mesh: 'spear', fudge: 5, pos: [0, 1.6, 0], size: 10, rot3: [Math.PI / 2, 0, 0], rotLife: [0, 0, 26], bursts: [[0.3, 1]], life: 0.3, vel: { z: [73, 73] }, alpha: [[0, 1], [0.9, 1], [1, 0]] }] }), 0.3));
// 5. 지면 가르기 — 균열이 0.9초에 걸쳐 플레이어 쪽으로 달려가며 차례로 불기둥이 솟는다
S.push(sysD('fissure_run', travelLane('ft', 3, 28, 0.5, 0.9, 'F', CF, { s2: 'R' }), 2.0));
// 6. 지옥불 숨결 — 좁은 불줄기를 좌→우로 훑는다 (서버가 30°씩 돌려 가며 7번)
S.push(sys('breath_seg', blastFan('bs', 30 * D2R, 16, 0.25, 'F', CF, { s2: 'R', rise: 0.9, extra: [
  { name: 'bs Jet', mat: 'flameF', mode: 'stretch', vs: 0.04, ls: 1.2, fudge: 3, pos: [0, 2.5, 0.5], life: [0.4, 0.55], size: [1.8, 3.2], shape: { type: 'box', sx: 0.8, sy: 0.8, sz: 0.3 }, bursts: [[0.25, 14], [0.33, 12], [0.41, 10]], vel: { z: [26, 38], x: [-5, 5], y: [-5, -2] }, drag: 1.2,
    sizeLife: [[0, 0.4], [1, 1.6]], alpha: [[0, 0], [0.1, 1], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'bs JetR', mat: 'flameR', fudge: 4, pos: [0, 2.5, 0.5], life: [0.45, 0.6], size: [2.2, 3.6], shape: { type: 'box', sx: 0.8, sy: 0.8, sz: 0.3 }, bursts: [[0.27, 10], [0.37, 8]], vel: { z: [22, 34], x: [-5, 5], y: [-5, -2] }, drag: 1.2,
    sizeLife: [[0, 0.4], [1, 1.7]], alpha: [[0, 0], [0.1, 0.9], [1, 0]], flip: { x: 4, y: 4 } }] }), 0.25));
// 7. 추적 불기둥 — 0.4초마다 플레이어 발밑에서 솟는다 (6번)
S.push(sys('hell_pillar', blastCircle('hp', 2.5, 0.6, 'F', CF, { s2: 'R', extra: [...KITS.demon.column('hpK2', [0, 2, 0], 0.66, 2.4, 'R', CR)] }), 0.6));
// 8. 네 날개 강풍 — 넓게 밀어내 불길 쪽으로 몬다
S.push(sys('wing_storm', blastFan('wg', 160 * D2R, 18, 0.8, 'N', CN, { s2: 'F', rise: 1.6, extra: [embers('wg', 0.85, 60, [0, 2, 3], { type: 'box', sx: 10, sy: 4, sz: 2 })] }), 0.8));
// 9. 포효 — 느리게 퍼지는 큰 파동 (서버가 간격을 두고 3번, 사이로 지나가야 함)
S.push(sysD('roar_wave', travelRing('rw', 2, 28, 0.5, 1.6, 'F', CF, { s2: 'N' }), 2.4));
// 10. 종말의 일격 (광폭화 후) — 전장 절반 → 반대쪽 절반
S.push(sys('doom_half', blastRect('dm', 18, 48, 1.5, 'F', CF, { s2: 'R', extra: [
  ...[[-5, -16], [4, -8], [-3, 0], [5, 9], [-4, 17]].map(([x, z], i) => Object.assign(swordFall('dm' + i, 1.5 + i * 0.03, z, 20), { pos: [x, 0, z] })),
  embers('dm', 1.6, 160, [0, 0.4, 0], { type: 'box', sx: 18, sy: 0.3, sz: 48 })] }), 1.5));
S.push(sys('gate_strike', blastLane('gs', 6, 24, 1.5, 'F', CF, { s2: 'N', h: 9, extra: [swordFall('gs', 1.5, 21, 24), embers('gs', 1.6, 80, [0, 0.5, 20], { type: 'box', sx: 8, sy: 0.3, sz: 8 }),
  ...[0, 0.12, 0.24].map((dt, i) => ({ name: `gs Shock${i}`, mat: 'line' + (i % 2 ? 'R' : 'F'), mode: 'mesh', mesh: 'cOuter', fudge: 29, pos: [0, 0, 22], size: 1, rot3: YAW, bursts: [[1.5 + dt, 1]], life: 0.6, sizeLife: [[0, 1, 0, 30], [1, 10]], alpha: [[0, 1], [0.6, 0.7], [1, 0]] }))] }), 1.5));
S.push(sys('enrage', enrageFx('en', 9, 12, 'F', 'N', CF).concat([flames('en', [0.62, 0.8, 1.0], 50, [0, 1, 0], { type: 'circle', r: 8, th: 1, y: 0 }, [3, 5]), embers('en', 0.7, 140, [0, 1, 0], { type: 'circle', r: 9, th: 1, y: 0 })]), 1.2));

var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, SM);
const O = HID(0, 0, 0);
const SCENE = {
  name: 'Siege_Demon', bloom: { threshold: 0.95, intensity: 1.35, scatter: 0.7, tint: [1, 0.82, 0.72] }, tonemapping: 'neutral', systems: S,
  demo: Object.assign({}, DEMO_BG, { interval: 6, dummy: { height: 1.8 }, scenarios: {
    combo: scn('1. 대검 3연격 (오른쪽 → 왼쪽 → 내려베기, 다가오며)', [HID(0, 0), HID(0, 3), HID(0, 6)], [{ t: 0.2, system: 'swing_r', actor: 0 }, { t: 0.85, system: 'swing_l', actor: 1 }, { t: 1.5, system: 'cleave', actor: 2 }], cam([0, 3, 8], 36, -60, 30)),
    leap: scn('2. 도약 강타 → 퍼지는 충격파', [HID(0, 4)], [{ t: 0.2, system: 'leap_crater', actor: 0 }, { t: 0.9, system: 'leap_wave', actor: 0 }], cam([0, 3, 4], 56, -80, 34)),
    dash: scn('3. 지옥 돌진 → 불길 5초', [O], [{ t: 0.2, system: 'dash', actor: 0 }, { t: 1.0, system: 'dash_fire', actor: 0 }], cam([0, 3, 16], 44, -60, 28)),
    sword_throw: scn('4. 마검 투척 → 폭발 → 되돌아오며 벰', [HID(0, 14), HID(0, 14, 180)], [{ t: 0.2, system: 'sword_throw', actor: 0 }, { t: 2.4, system: 'sword_recall', actor: 1 }], cam([0, 3, 8], 38, -60, 28)),
    fissure: scn('5. 지면 가르기 (달려가는 균열)', [HID(0, 0, -18), HID(0, 0, 0), HID(0, 0, 18)], [0, 1, 2].map(a => ({ t: 0.2 + a * 0.25, system: 'fissure_run', actor: a })), cam([0, 3, 12], 44, -75, 34)),
    breath: scn('6. 지옥불 숨결 (좌 → 우로 훑음)', Array.from({ length: 7 }, (_, i) => HID(0, 0, -90 + i * 30)), Array.from({ length: 7 }, (_, i) => ({ t: 0.2 + i * 0.22, system: 'breath_seg', actor: i })), cam([0, 3, 6], 44, -90, 38)),
    hell_pillar: scn('7. 추적 불기둥 (0.4초마다 발밑)', Array.from({ length: 6 }, (_, i) => HID(-6 + i * 2.4, 2 + Math.sin(i * 0.9) * 3)).concat([{ path: [[0, [-6, 0, 2], 90], [3, [7, 0, 3], 90]] }]), Array.from({ length: 6 }, (_, i) => ({ t: 0.2 + i * 0.4, system: 'hell_pillar', actor: i })), cam([0, 3, 2], 28, -75, 28)),
    wing_storm: scn('8. 네 날개 강풍', [O], [{ t: 0.2, system: 'wing_storm', actor: 0 }], cam([0, 3, 8], 40, -55, 30)),
    roar: scn('9. 포효 (느린 파동 3번)', [O], [0, 1, 2].map(i => ({ t: 0.2 + i * 0.9, system: 'roar_wave', actor: 0 })), cam([0, 3, 0], 68, -90, 36)),
    doom: scn('10. 종말의 일격 (절반 → 반대쪽 절반)', [HID(9, 0), HID(-9, 0)], [{ t: 0.2, system: 'doom_half', actor: 0 }, { t: 1.8, system: 'doom_half', actor: 1 }], cam([0, 3, 0], 74, -60, 40)),
    gate_strike: scn('성문 강타', [O], [{ t: 0.2, system: 'gate_strike', actor: 0 }], cam([0, 4, 12], 38, -60, 26)),
    enrage: scn('광폭화', [O], [{ t: 0.2, system: 'enrage', actor: 0 }], cam([0, 6, 0], 38, -70, 22)),
  } }),
};
