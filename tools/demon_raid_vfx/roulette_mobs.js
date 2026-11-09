// ===== Roulette_Mobs — 룰레타 레이드 잡몹 3종 공격 VFX =====
// 칩 임프(chip_imp) / 카드 병정(card_soldier) / 주사위 술사(dice_mage). P = 몹 발밑, +Z = 몹 정면(서버가 넘기는 방향).
const GOLD = [1, 0.84, 0.45], PALE = [1, 0.97, 0.86], AMBER = [1, 0.6, 0.2], RED = [1, 0.28, 0.2], DEEP = [0.55, 0.05, 0.08];
const CG = { hot: PALE, mid: GOLD, low: AMBER }, CR = { hot: [1, 0.85, 0.6], mid: RED, low: DEEP };
var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, {
  arc120: crescentXZ(Math.PI * 2 / 3, 0.09), arc120t: crescentXZ(Math.PI * 2 / 3, 0.03), vslash: crescentXY(Math.PI * 0.8, 0.08),
  cone4: fan(Math.PI * 2 / 3, 0.4, 4, 0.05, 32), cone4Edge: fanEdge(Math.PI * 2 / 3, 0.4, 4, 0.08, 0.06),
  dieM: { verts: [[-0.3, 0, -0.3], [0.3, 0, -0.3], [0.3, 0, 0.3], [-0.3, 0, 0.3]], uvs: [[0, 1], [1, 1], [1, 0], [0, 0]], tris: [0, 1, 2, 0, 2, 3] },
});
const FWD = -Math.PI / 2; // crescentXZ(+X 중심)를 +Z 로

// 칩 임프: 굴러 들이받기 (정면 0.8 m)
const IMP_HIT = [
  ...impactBurst('I', [0, 0.1, 0.9], 0, 0.45, { star: 'starR', flare: 'flareR', streak: 'streakR', dot: 'dot', shardD: 'shardDR' }, CR, { ground: false, n: 0.6 }),
  { name: 'I10 Ring', mat: 'lineR', mode: 'mesh', mesh: 'cOuter', fudge: 30, pos: [0, 0.05, 0.9], size: 1.2, rot3: YAW, bursts: [[0, 1]], life: 0.35, sizeLife: [[0, 0.3, 0, 3], [1, 1.2]], alpha: [[0, 1], [1, 0]] },
  { name: 'I11 Chips', mat: 'chipR', fudge: 10, pos: [0, 0.5, 0.9], life: [0.5, 0.8], size: [0.18, 0.26], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'sphere', r: 0.2, th: 1 }, bursts: [[0, 6]], speed: [2, 4], gravity: 1.3, alpha: [[0, 1], [0.8, 1], [1, 0]] },
];
// 칩 임프 사망: 동전 폭발 (반경 2)
const IMP_POP = [
  ...magicCircle('P0', 2, 0, 0.1, 1.0, { line: 'lineR', rune: 'rune' }, { star: 'cHex' }),
  ...impactBurst('P1', [0, 0, 0], 0.1, 0.8, { star: 'star', flare: 'flareR', wave: 'waveR', streak: 'streak', dot: 'dot', shardD: 'shardDR', ring: 'lineR' }, CG, { waveR: 2.5 }),
  { name: 'P20 Coins', mat: 'coin', fudge: 8, pos: [0, 0.5, 0], life: [0.7, 1.0], size: [0.22, 0.3], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'sphere', r: 0.3, th: 1 }, bursts: [[0.1, 16]], speed: [4, 7], gravity: 1.3, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: 'P21 Chips', mat: 'chipR', fudge: 10, pos: [0, 0.5, 0], life: [0.6, 0.9], size: [0.22, 0.3], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'sphere', r: 0.3, th: 1 }, bursts: [[0.1, 8]], speed: [3, 6], gravity: 1.3, alpha: [[0, 1], [0.85, 1], [1, 0]] },
];
// 카드 병정: 3연 베기 (부채꼴 120° 반경 4) a = 오른쪽→왼쪽, b = 왼쪽→오른쪽, c = 내려베기
function slash(tag, dir, mode) {
  const L = [
    { name: `${tag}01 Fill`, mat: 'fanR', mode: 'mesh', mesh: 'cone4', fudge: 42, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 0.45, alpha: [[0, 0.38], [1, 0]] },
    { name: `${tag}02 Edge`, mat: 'lineR', mode: 'mesh', mesh: 'cone4Edge', fudge: 40, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 0.4, alpha: [[0, 1], [1, 0]] }];
  if (mode === 'v') L.push(
    { name: `${tag}03 Slash`, mat: 'slashR', mode: 'mesh', mesh: 'vslash', fudge: 4, pos: [0, 1.4, 1.6], size: 2.2, rot3: [0, Math.PI / 2, -Math.PI / 2 + 0.2], bursts: [[0, 1]], life: 0.3, flip: { x: 2, y: 8 } },
    { name: `${tag}04 Slash2`, mat: 'slash', mode: 'mesh', mesh: 'vslash', fudge: 5, pos: [0, 1.4, 1.7], size: 2.0, rot3: [0, Math.PI / 2, -Math.PI / 2 + 0.2], bursts: [[0.03, 1]], life: 0.28, flip: { x: 2, y: 8 } },
    { name: `${tag}05 Ground`, mat: 'crack', mode: 'mesh', mesh: 'quad', fudge: 43, pos: [0, 0.03, 2.4], size: 3, rot3: YAW, bursts: [[0.08, 1]], life: 0.8, sizeLife: [[0, 0.4, 0, 4], [0.15, 1]], alpha: [[0, 1], [1, 0]] });
  else L.push(
    { name: `${tag}03 Slash`, mat: 'slashR', mode: 'mesh', mesh: 'arc120', fudge: 4, pos: [0, 1.1, 0], size: 3.6, rot3: [0, FWD, dir * 0.12], rotLife: [0, -dir * 4, 0], bursts: [[0, 1]], life: 0.3, flip: { x: 2, y: 8 } },
    { name: `${tag}04 Slash2`, mat: 'slash', mode: 'mesh', mesh: 'arc120t', fudge: 5, pos: [0, 1.25, 0], size: 3.2, rot3: [0, FWD, dir * 0.12], rotLife: [0, -dir * 4.4, 0], bursts: [[0.03, 1]], life: 0.28, flip: { x: 2, y: 8 } });
  L.push(
    { name: `${tag}06 Sparks`, mat: 'dot', mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [0, 1.1, 2.4], life: [0.3, 0.5], size: [0.04, 0.08], shape: { type: 'sphere', r: 1, th: 1 }, bursts: [[0.05, 18]], speed: [3, 6], drag: 3, gravity: 0.6, color: [[0, PALE], [1, RED]], alpha: [[0, 1], [1, 0]] },
    { name: `${tag}07 Cards`, mat: 'card', fudge: 9, pos: [0, 1.1, 2.2], life: [0.4, 0.7], size: [0.2, 0.3], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'sphere', r: 0.8, th: 1 }, bursts: [[0.05, 4]], speed: [2, 4], gravity: 1, alpha: [[0, 1], [0.8, 1], [1, 0]] },
    { name: `${tag}08 Flash`, mat: 'starR', fudge: -8, pos: [0, 1.1, 2], size: 1.8, rot: [0, TAU], bursts: [[0.05, 1]], life: 0.16, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] });
  return L;
}
// 주사위 술사: 주사위 투사체 + 착탄 (반경 2.5)
const DICE = [
  { name: 'D01 Die', mat: 'die', fudge: -1, rate: 30, bursts: [[0, 1]], life: 0.05, size: 0.7, rot: [0, TAU] },
  { name: 'D02 Glow', mat: 'glowR', fudge: 0, rate: 20, life: 0.15, size: 1.2, alpha: [[0, 0.5], [1, 0]] },
  { name: 'D03 Trail', mat: 'streakR', mode: 'stretch', vs: 0.03, ls: 1.2, fudge: 0, world: true, rateDist: 14, life: [0.2, 0.3], size: [0.12, 0.2], alpha: [[0, 0.8], [1, 0]] },
  { name: 'D04 Pips', mat: 'dot', fudge: -6, world: true, rateDist: 6, life: [0.4, 0.6], size: [0.08, 0.12], shape: { type: 'sphere', r: 0.3, th: 1 }, vel: { y: [-0.4, 0.4] }, color: [[0, PALE], [1, RED]], alpha: [[0, 1], [1, 0]] },
];
const DICE_HIT = [
  ...magicCircle('H0', 2.5, 0, 0.08, 1.0, { line: 'lineR', rune: 'rune' }, { star: 'cHex' }),
  ...impactBurst('H1', [0, 0, 0], 0.08, 0.9, { star: 'starR', flare: 'flareR', wave: 'waveR', streak: 'streakR', dot: 'dotR', shardD: 'shardDR', mist: 'mist', ring: 'lineR' }, CR, { waveR: 2.6 }),
  { name: 'H20 Dice', mat: 'die', fudge: 9, pos: [0, 0.4, 0], life: [0.6, 0.9], size: [0.3, 0.42], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'sphere', r: 0.3, th: 1 }, bursts: [[0.08, 6]], speed: [3, 6], gravity: 1.3, alpha: [[0, 1], [0.8, 1], [1, 0]] },
];
const MATS = {
  line: { tex: 'T_Line', tint: [2.6, 2.05, 0.95], add: true }, lineR: { tex: 'T_Line', tint: [2.6, 0.6, 0.38], add: true },
  rune: { tex: 'T_Rune', tint: [2.3, 1.8, 0.8], add: true }, glowR: { tex: 'T_Glow', tint: [1.4, 0.3, 0.15], add: true },
  star: { tex: 'T_Star', tint: [2.8, 2.3, 1.4], add: true }, starR: { tex: 'T_Star', tint: [2.8, 1.1, 0.7], add: true },
  flareR: { tex: 'T_Flare', tint: [2.3, 0.75, 0.4], add: true }, waveR: { tex: 'T_Wave', tint: [1.9, 0.5, 0.25], add: true },
  streak: { tex: 'T_Streak', tint: [2.6, 2.1, 1.1], add: true }, streakR: { tex: 'T_Streak', tint: [2.6, 0.85, 0.45], add: true },
  dot: { tex: 'T_Dot', tint: [2.4, 2.0, 1.2], add: true }, dotR: { tex: 'T_Dot', tint: [2.4, 1.1, 0.6], add: true },
  fanR: { tex: 'T_FanFill', tint: [1.6, 0.25, 0.15], add: true }, shardDR: { tex: 'T_ShardDark', tint: [2.2, 0.7, 0.4], add: false },
  crack: { tex: 'T_Crack', tint: [2.3, 0.6, 0.25], add: true }, mist: { tex: 'T_DarkMist_Sheet', tint: [1, 1, 1], add: false },
  slash: { tex: 'T_Slash_Sheet', tint: [2.3, 1.9, 1.0], add: true }, slashR: { tex: 'T_Slash_Sheet', tint: [2.4, 0.75, 0.45], add: true },
  card: { tex: 'T_Card', tint: [1.15, 1.1, 1.05], add: false }, chipR: { tex: 'T_Chip', tint: [1.3, 0.3, 0.28], add: false },
  coin: { tex: 'T_Coin', tint: [1.6, 1.25, 0.55], add: false }, die: { tex: 'T_Die', tint: [1.15, 1.1, 1.05], add: false },
};
const M0 = { path: [[0, [0, 0, 0], 0]], hidden: true };
const SCENE = {
  name: 'Roulette_Mobs', bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.7, tint: [1, 0.85, 0.7] }, tonemapping: 'neutral',
  systems: [
    { id: 'imp_hit', role: 'static', loop: false, duration: 0.2, layers: IMP_HIT },
    { id: 'imp_pop', role: 'static', loop: false, duration: 0.2, layers: IMP_POP },
    { id: 'slash_a', role: 'static', loop: false, duration: 0.2, layers: slash('A', 1, 'h') },
    { id: 'slash_b', role: 'static', loop: false, duration: 0.2, layers: slash('B', -1, 'h') },
    { id: 'slash_c', role: 'static', loop: false, duration: 0.2, layers: slash('C', 1, 'v') },
    { id: 'dice', role: 'projectile', loop: true, duration: 1.0, layers: DICE },
    { id: 'dice_hit', role: 'impact', loop: false, duration: 0.2, layers: DICE_HIT },
  ],
  demo: Object.assign({}, DEMO_BG, { interval: 3.2, dummy: { height: 1.8 },
    scenarios: {
      imp: { label: '칩 임프: 들이받기 → 사망 동전 폭발', path: [[0, [0, 0, 0], 0]], actors: [M0, { path: [[0, [0, 0, 1.4], 180]] }],
        events: [{ t: 0.3, system: 'imp_hit', actor: 0 }, { t: 1.4, system: 'imp_pop', actor: 0 }], camera: { target: [0, 0.8, 0.8], dist: 7, yaw: -60, pitch: 20 } },
      soldier: { label: '카드 병정: 3연 베기', path: [[0, [0, 0, 0], 0]], actors: [M0, { path: [[0, [0, 0, 2.6], 180]] }],
        events: [{ t: 0.3, system: 'slash_a', actor: 0 }, { t: 0.75, system: 'slash_b', actor: 0 }, { t: 1.2, system: 'slash_c', actor: 0 }], camera: { target: [0, 1, 1.5], dist: 8, yaw: -55, pitch: 22 } },
      mage: { label: '주사위 술사: 주사위 투척', path: [[0, [0, 0, 0], 0]], actors: [M0],
        events: [{ t: 0.3, system: 'dice', actor: 0, from: [0.4, 1.8, 0.4], to: [0, 0, 9], normal: [0, 1, 0], flight: 1.1, via: [0, 5, 4.5], impact: 'dice_hit' }], camera: { target: [0, 1.5, 4.5], dist: 11, yaw: -70, pitch: 18 } },
    } }),
};
