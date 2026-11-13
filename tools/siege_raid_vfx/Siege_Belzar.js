// ===== Siege_Belzar — 공성전 3공세 · 천사 벨자르(봉인 전) 짤패턴 VFX. 흰 날개 쪽 = W(금빛 흰색), 검은 날개 쪽 = K(검붉은색) =====
const WHITE = [1, 1, 1], GOLD = [1, 0.88, 0.55], AMB = [1, 0.62, 0.2], RED = [1, 0.28, 0.2], CRIM = [0.5, 0.04, 0.08], EMB = [1, 0.5, 0.15];
const CW = { hot: WHITE, mid: GOLD, low: AMB }, CK = { hot: [1, 0.75, 0.55], mid: RED, low: CRIM };
const MATS = mkMats({ W: [1, 0.8, 0.4], K: [1, 0.1, 0.07], E: [1, 0.4, 0.09] }, {
  featherW: { tex: 'T_Feather', tint: [2.5, 2.3, 1.7], add: true }, featherK: { tex: 'T_Feather', tint: [0.16, 0.08, 0.1], add: false }, featherE: { tex: 'T_Feather', tint: [2.4, 0.7, 0.4], add: true },
  haloSig: { tex: 'T_HaloSigil', tint: [2.7, 2.2, 1.1], add: true }, thornSig: { tex: 'T_ThornSigil', tint: [2.6, 0.32, 0.22], add: true }, cross: { tex: 'T_Cross', tint: [2.8, 2.5, 1.5], add: true },
  chain: { tex: 'T_Chain', tint: [2.3, 0.6, 0.25], add: true },
  halo: { tex: 'T_Ring', tint: [2.6, 2.2, 1.2], add: true }, haloK: { tex: 'T_Ring', tint: [2.4, 0.5, 0.4], add: true } });
KIT = KITS.belzar;
const feathers = (tag, t, n, pos, sp, mat, o) => Object.assign({ name: `${tag} Feathers${mat}`, mat, fudge: mat === 'featherK' ? 18 : -1, pos, life: [1.0, 1.7], size: [0.28, 0.46], rot: [0, TAU], rotZ: [-3, 3],
  shape: { type: 'sphere', r: 0.5, th: 1 }, bursts: [[t, n]], speed: sp, drag: 3, gravity: 0.12, noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.08, 1], [0.7, 0.85], [1, 0]] }, o || {});
const both = (tag, t, n, pos, sp, o) => [feathers(tag, t, n, pos, sp, 'featherW', o), feathers(tag, t, n, pos, sp, 'featherK', o)];
const halo = (tag, mat, T, h, size) => ({ name: `${tag} Halo`, mat, mode: 'mesh', mesh: 'quad', fudge: -9, pos: [0, h, 0], size, rot3: YAW, rotLife: [0, 3, 0], bursts: [[0, 1]], life: T + 0.3, sizeLife: [[0, 0.3, 0, 3], [0.6, 1], [0.8, 1.25], [1, 0.2]], alpha: [[0, 0], [0.2, 1], [0.85, 1], [1, 0]] });
const lightSpear = (tag, sf, T, h) => ({ name: `${tag} Spear`, mat: sf === 'W' ? 'cross' : 'chain', mode: 'mesh', mesh: sf === 'W' ? 'spear' : 'chainM', fudge: 5, pos: [0, 0, 0], size: h, rot3: YAW, bursts: [[T - 0.08, 1]], life: 0.5, sizeLife: [[0, 1.6], [0.3, 1], [1, 0.9]], alpha: [[0, 0], [0.15, 1], [0.6, 0.8], [1, 0]] });
const feaFly = (tag, T, len, w, n, mat) => ({ name: `${tag} FeatherFly`, mat, mode: 'stretch', vs: 0.03, ls: 1.4, fudge: -3, pos: [0, 1.2, 0.5], life: [0.3, 0.4], size: [0.5, 0.75], shape: { type: 'box', sx: w * 0.6, sy: 0.8, sz: 0.4 }, bursts: [[T, n]],
  vel: { z: [len / 0.36 * 0.85, len / 0.36 * 1.1] }, alpha: [[0, 1], [0.85, 1], [1, 0]] });

const S = [];
// 벨자르의 공격 방식: 두 박자로 뒤집히고, 표식이 나중에 터진다.
// 1. 청구서 표식 — 플레이어 발밑에 3초 붙어 있다가(attached) 그 자리에서 터진다
S.push({ id: 'debt_mark', role: 'attached', loop: false, duration: 3, layers: [
  { name: 'dm Thorn', mat: 'thornSig', mode: 'mesh', mesh: 'quad', fudge: 34, pos: [0, 0.06, 0], size: 8.4, rot3: YAW, rotLife: [0, -1.2, 0], bursts: [[0, 1]], life: 3, sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 0], [0.08, 1], [0.95, 1], [1, 0]] },
  ...[0, 1, 2].map(i => ({ name: `dm Tick${i}`, mat: 'haloK', mode: 'mesh', mesh: 'quad', fudge: 33, pos: [0, 0.08, 0], size: 9, rot3: YAW, bursts: [[i, 1]], life: 0.9, sizeLife: [[0, 1], [1, 0.25]], alpha: [[0, 0], [0.2, 1], [1, 0]] })),
  { name: 'dm Links', mat: 'haloK', fudge: -5, pos: [0, 1.1, 0], life: 3, size: 0.55, shape: { type: 'circle', r: 0.9, th: 0, y: 0 }, bursts: [[0, 5]], vel: { orb: [3, 3] }, alpha: [[0, 0], [0.08, 1], [0.95, 1], [1, 0]] },
  { name: 'dm Mark', mat: 'starK', fudge: -9, pos: [0, 2.5, 0], size: 0.9, rot: [0, TAU], rotZ: [2, 2], bursts: [[0, 1]], life: 3, sizeLife: [[0, 0.6], [0.9, 1], [1, 2.4]], alpha: [[0, 0], [0.08, 1], [0.97, 1], [1, 0]] },
  { name: 'dm Embers', mat: 'dotE', fudge: -6, pos: [0, 0.2, 0], life: [0.6, 1.0], size: [0.05, 0.1], shape: { type: 'circle', r: 1.2, th: 1, y: 0 }, rate: 14, world: true, vel: { y: [1, 2.5] }, alpha: [[0, 0], [0.2, 1], [1, 0]] }] });
S.push(sys('debt_due', blastCircle('dd', 4, 0.15, 'K', CK, { s2: 'E', extra: [feathers('dd', 0.15, 16, [0, 1.2, 0], [4, 9], 'featherK')] }), 0.15));
// 2. 흑백 반전 — 흰 안쪽 원 → 곧바로 검은 바깥 고리 (또는 그 반대 순서). 안전한 곳이 뒤집힌다
S.push(sys('white_in', blastCircle('wi', 8, 0.9, 'W', CW, { s2: 'W', dark: false, extra: [halo('wi', 'halo', 0.9, 10, 9), feathers('wi', 0.9, 30, [0, 1.5, 0], [5, 12], 'featherW')] }), 0.9));
S.push(sys('black_out', blastRing('bo', 8, 18, 0.9, 'K', CK, { s2: 'E', extra: [feathers('bo', 0.9, 40, [0, 1.5, 0], [2, 5], 'featherK', { shape: { type: 'circle', r: 18, th: 0.55, y: 0 } }),
  ...[0, 1, 2, 3, 4, 5].flatMap(i => KITS.belzar.column('boC' + i, [Math.cos(i * TAU / 6) * 13, 0, Math.sin(i * TAU / 6) * 13], 0.9 + i * 0.02, 1.8, 'K', CK))] }), 0.9));
S.push(sys('white_out', blastRing('wo', 8, 18, 0.9, 'W', CW, { s2: 'W', extra: [feathers('wo', 0.9, 40, [0, 1.5, 0], [2, 5], 'featherW', { shape: { type: 'circle', r: 18, th: 0.55, y: 0 } }),
  ...[0, 1, 2, 3, 4, 5].flatMap(i => KITS.belzar.column('woC' + i, [Math.cos(i * TAU / 6) * 13, 0, Math.sin(i * TAU / 6) * 13], 0.9 + i * 0.02, 1.8, 'W', CW))] }), 0.9));
S.push(sys('black_in', blastCircle('bi', 8, 0.9, 'K', CK, { s2: 'E', extra: [halo('bi', 'haloK', 0.9, 10, 9), feathers('bi', 0.9, 30, [0, 1.5, 0], [5, 12], 'featherK')] }), 0.9));
// 3. 사슬 포박 — 가장 먼 사람을 사슬로 끌어온 뒤 바로 앞을 할퀸다
S.push(sys('debt_chain', blastLane('dc', 2, 24, 0.7, 'K', CK, { s2: 'E', h: 2.5, extra: [
  { name: 'dc Chain', mat: 'chain', mode: 'mesh', mesh: 'chain', fudge: 4, pos: [0, 1.2, 0], size: 1, rot3: [0, 0, 0], bursts: [[0.7, 1]], life: 0.9, alpha: [[0, 1], [0.6, 0.9], [1, 0]] },
  { name: 'dc Links', mat: 'haloK', fudge: -5, pos: [0, 1.2, 12], life: 0.9, size: [0.5, 0.7], rot: [0, TAU], shape: { type: 'box', sx: 0.1, sy: 0.1, sz: 23 }, bursts: [[0.7, 26]], vel: { z: [-10, -6] }, alpha: [[0, 1], [0.7, 1], [1, 0]] }] }), 0.7));
S.push(sys('claw', blastFan('cl', 100 * D2R, 5, 0.3, 'K', CK, { s2: 'E', extra: swingBlade('cl', 100 * D2R, 5, 0.3, 1, 'bandK', 'slashK') }), 0.3));
// 4. 깃털 지뢰 — 흰 깃털이 6초 동안 박혀 있다가, 검은 파동이 지나가면 터진다
S.push(sysD('feather_mine', [
  hold('fm Sigil', 'haloSig', 3.4, 0, 6, { rotLife: [0, 0.8, 0] }),
  { name: 'fm Feather', mat: 'cross', mode: 'mesh', mesh: 'spear', fudge: 5, size: 3.2, rot3: [0.12, 0.6, 0.08], bursts: [[0, 1]], life: 6, sizeLife: [[0, 2.2], [0.03, 1], [1, 1]], alpha: [[0, 0], [0.02, 1], [0.95, 1], [1, 0]] },
  { name: 'fm Plume', mat: 'featherW', fudge: -2, pos: [0, 2.6, 0], size: 1.5, rot: [0.6, 0.6], bursts: [[0.1, 1]], life: 5.9, alpha: [[0, 0], [0.03, 1], [0.95, 1], [1, 0]] },
  { name: 'fm Motes', mat: 'dotW', fudge: -6, pos: [0, 0.3, 0], life: [0.8, 1.3], size: [0.05, 0.09], shape: { type: 'circle', r: 1.4, th: 1, y: 0 }, rate: 8, vel: { y: [0.8, 1.8] }, alpha: [[0, 0], [0.2, 1], [1, 0]] }], 6));
S.push(sys('mine_burst', blastCircle('mb', 3.5, 0.2, 'K', CK, { s2: 'E', extra: [...both('mb', 0.2, 12, [0, 1.2, 0], [4, 9])] }), 0.2));
// 5. 좁혀드는 후광 — 바깥에서 안으로 1.6초에 걸쳐 조여 오는 고리 (가운데로 몰린다)
S.push(sysD('halo_close', travelRing('hc', 18, 1, 0.8, 1.6, 'W', CW, { s2: 'W', extra: [
  ...[[9, 36], [6, 24], [3, 12]].map(([h, sz], i) => ({ name: `hc Halo${i}`, mat: 'halo', mode: 'mesh', mesh: 'quad', fudge: -6, pos: [0, h, 0], size: sz * 1.25, rot3: YAW, rotLife: [0, 1.5, 0], bursts: [[0, 1]], life: 2.4, sizeLife: [[0, 1], [0.33, 1], [1, 0.06]], alpha: [[0, 0], [0.2, 0.9], [0.9, 1], [1, 0]] })),
  hold('hc Sigil', 'haloSig', 37, 0, 2.5, { rotLife: [0, 0.5, 0], sizeLife: [[0, 1], [0.33, 1], [1, 0.08]], alpha: [[0, 0], [0.2, 0.6], [0.9, 0.9], [1, 0]] }),
  ...KITS.belzar.column('hcC', [0, 0, 0], 2.4, 3, 'W', CW)] }), 2.6));
// 6. 거울 일격 — 본체(흰 쪽)와 반대편 분신(검은 쪽)이 같은 줄을 마주 보고 동시에 벤다
S.push(sys('mirror_white', blastLane('mw', 5, 20, 0.8, 'W', CW, { s2: 'W', h: 5, extra: [feaFly('mw', 0.8, 20, 5, 14, 'featherW')] }), 0.8));
S.push(sys('mirror_black', blastLane('mk', 5, 20, 0.8, 'K', CK, { s2: 'E', h: 5, extra: [feaFly('mk', 0.8, 20, 5, 14, 'featherE')] }), 0.8));
// 7. 행진하는 창 — 빛의 창이 0.25초 간격으로 한 사람을 향해 줄지어 꽂힌다
S.push(sys('spear_step', blastCircle('st', 2, 0.45, 'W', CW, { s2: 'W', dark: false, extra: [lightSpear('st', 'W', 0.45, 16)] }), 0.45));
// 8. 복리 — 같은 자리에 세 번, 터질 때마다 커진다
[[3, 1], [5.5, 2], [8, 3]].forEach(([R, i]) => S.push(sys('interest_' + i, blastCircle('in' + i, R, 0.8, 'K', CK, { s2: 'E', extra: [feathers('in' + i, 0.8, 8 * i, [0, 1.2, 0], [3 + i, 7 + i * 2], 'featherK'),
  ...Array.from({ length: i }, (_, k) => ({ name: `in${i} Tally${k}`, mat: 'haloK', fudge: -9, pos: [(k - (i - 1) / 2) * 1.4, 3.4, 0], size: 1.2, bursts: [[0, 1]], life: 0.85, alpha: [[0, 0], [0.2, 1], [0.9, 1], [1, 0]] }))] }), 0.8)));
// 9. 최후 청구 (광폭화 후) — 체스판 흰 칸 → 검은 칸
S.push(sys('final_white', blastRect('fw', 12, 12, 1.0, 'W', CW, { s2: 'W', extra: [halo('fw', 'halo', 1.0, 10, 8), lightSpear('fw', 'W', 1.0, 24)] }), 1.0));
S.push(sys('final_black', blastRect('fb', 12, 12, 1.0, 'K', CK, { s2: 'E', extra: [halo('fb', 'haloK', 1.0, 10, 8), lightSpear('fb', 'K', 1.0, 24)] }), 1.0));
S.push(sys('gate_strike', blastLane('gs', 5, 24, 1.5, 'K', CK, { s2: 'E', h: 7, extra: [...both('gs', 1.5, 26, [0, 2, 22], [5, 12]),
  ...KITS.belzar.column('gsL', [0, 0, 21], 1.5, 3.2, 'K', CK), ...KITS.belzar.column('gsL2', [0, 0, 21], 1.56, 2.4, 'W', CW)] }), 1.5));
S.push(sys('enrage', enrageFx('en', 6, 8, 'W', 'K', CK).concat(both('en', 0.6, 40, [0, 4.5, 0], [8, 16], { life: [1.4, 2.2] })).concat([halo('en', 'halo', 0.6, 9.5, 6), halo('en2', 'haloK', 0.6, 10, 9)]), 1.2));

SM.chain = merge(Array.from({ length: 12 }, (_, i) => { const p = wallZ(2, 0.6, 0); p.verts = p.verts.map(v => [v[0], v[1] - 0.3, v[2] + i * 2]); p.uvs = [[0, 0], [0, 1], [1, 0], [1, 1]]; return p; }));
var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, SM);
const O = HID(0, 0, 0);
const SCENE = {
  name: 'Siege_Belzar', bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.7, tint: [1, 0.9, 0.82] }, tonemapping: 'neutral', systems: S,
  demo: Object.assign({}, DEMO_BG, { interval: 6, dummy: { height: 1.8 }, scenarios: {
    debt_mark: scn('1. 청구서 표식 (3초 뒤 그 자리에서 터짐)', [{ path: [[0, [-5, 0, 2], 90], [3.2, [4, 0, 4], 90]] }, { path: [[0, [3, 0, -3], 0], [3.2, [5, 0, -6], 0]] }, HID(4, 4), HID(5, -6)],
      [{ t: 0.2, system: 'debt_mark', actor: 0 }, { t: 0.2, system: 'debt_mark', actor: 1 }, { t: 3.2, system: 'debt_due', actor: 2 }, { t: 3.2, system: 'debt_due', actor: 3 }], cam([0, 1.5, 0], 26, -70, 32)),
    invert_a: scn('2-A. 흑백 반전 (흰 안쪽 → 검은 바깥)', [O], [{ t: 0.2, system: 'white_in', actor: 0 }, { t: 1.0, system: 'black_out', actor: 0 }], cam([0, 3, 0], 54, -90, 36)),
    invert_b: scn('2-B. 흑백 반전 (흰 바깥 → 검은 안쪽)', [O], [{ t: 0.2, system: 'white_out', actor: 0 }, { t: 1.0, system: 'black_in', actor: 0 }], cam([0, 3, 0], 54, -90, 36)),
    chain: scn('3. 사슬 포박 → 할퀴기', [O], [{ t: 0.2, system: 'debt_chain', actor: 0 }, { t: 1.2, system: 'claw', actor: 0 }], cam([0, 2, 10], 30, -60, 26)),
    feather_mine: scn('4. 깃털 지뢰 (박힌 뒤 검은 파동에 터짐)', [HID(-5, 3), HID(4, 6), HID(0, -4), HID(-3, 8), HID(6, 0)],
      [0, 1, 2, 3, 4].map(a => ({ t: 0.2 + a * 0.15, system: 'feather_mine', actor: a })).concat([0, 1, 2, 3, 4].map(a => ({ t: 3.0 + a * 0.12, system: 'mine_burst', actor: a }))), cam([0, 2, 2], 30, -70, 32)),
    halo_close: scn('5. 좁혀드는 후광 (바깥에서 안으로)', [O], [{ t: 0.2, system: 'halo_close', actor: 0 }], cam([0, 3, 0], 56, -90, 34)),
    mirror: scn('6. 거울 일격 (마주 보고 동시에)', [HID(0, -10, 0), HID(0, 10, 180)], [{ t: 0.2, system: 'mirror_white', actor: 0 }, { t: 0.2, system: 'mirror_black', actor: 1 }], cam([0, 2, 0], 36, -50, 30)),
    spear_march: scn('7. 행진하는 창 (줄지어 따라옴)', Array.from({ length: 8 }, (_, i) => HID(-8 + i * 2.2, -2 + Math.sin(i * 0.6) * 3)), Array.from({ length: 8 }, (_, i) => ({ t: 0.2 + i * 0.25, system: 'spear_step', actor: i })), cam([0, 3, 0], 30, -90, 30)),
    interest: scn('8. 복리 (세 번, 점점 커짐)', [HID(0, 4)], [1, 2, 3].map(i => ({ t: 0.2 + (i - 1) * 1.0, system: 'interest_' + i, actor: 0 })), cam([0, 2, 4], 32, -65, 30)),
    final: scn('9. 최후 청구 (체스판 흰 칸 → 검은 칸)', Array.from({ length: 12 }, (_, i) => HID(-12 + (i % 3) * 12, -18 + Math.floor(i / 3) * 12)),
      Array.from({ length: 12 }, (_, i) => { const w = ((i % 3) + Math.floor(i / 3)) % 2 === 0; return { t: w ? 0.2 : 1.3, system: w ? 'final_white' : 'final_black', actor: i }; }), cam([0, 2, 0], 72, -60, 42)),
    gate_strike: scn('성문 강타', [O], [{ t: 0.2, system: 'gate_strike', actor: 0 }], cam([0, 3, 12], 34, -60, 26)),
    enrage: scn('광폭화', [O], [{ t: 0.2, system: 'enrage', actor: 0 }], cam([0, 5, 0], 30, -70, 22)),
  } }),
};
