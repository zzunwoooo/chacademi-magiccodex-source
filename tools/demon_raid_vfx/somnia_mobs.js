// ===== Somnia_Mobs — 솜니아 레이드 잡몹 3종 공격 VFX =====
// 졸음 나방(sleepy_moth) / 몽유 거인(sleepwalker) / 악몽 인형(nightmare_doll). P = 몹 발밑, +Z = 몹 정면.
const LAV = [0.82, 0.68, 1], VIO = [0.55, 0.3, 0.95], WHITE = [1, 1, 1], DEEPV = [0.25, 0.08, 0.45];
const CV = { hot: WHITE, mid: LAV, low: VIO }, CN = { hot: [0.9, 0.7, 1], mid: VIO, low: DEEPV };
const FWD = -Math.PI / 2;
var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, {
  half45: fan(Math.PI, 0.5, 4.5, 0.05, 32), half45Edge: fanEdge(Math.PI, 0.5, 4.5, 0.1, 0.06), arc180: crescentXZ(Math.PI, 0.09), arc180t: crescentXZ(Math.PI, 0.03),
  needleM: merge([{ verts: [[-0.03, 0, -0.45], [0.03, 0, -0.45], [0.03, 0, 0.45], [-0.03, 0, 0.45]], uvs: [[0, 0], [0, 1], [1, 1], [1, 0]], tris: [0, 1, 2, 0, 2, 3] },
                  { verts: [[0, -0.03, -0.45], [0, 0.03, -0.45], [0, 0.03, 0.45], [0, -0.03, 0.45]], uvs: [[0, 0], [0, 1], [1, 1], [1, 0]], tris: [0, 1, 2, 0, 2, 3] }]),
  needleLine: merge([strip([0, 0], [0, 14], 1, 0.05)]), needleEdge: merge([strip([-0.5, 0], [-0.5, 14], 0.06, 0.06), strip([0.5, 0], [0.5, 14], 0.06, 0.06)]),
});
// 졸음 나방: 들이받기 (정면 0.8 m) + 둔화 가루
const MOTH = [
  ...impactBurst('M', [0, 0.6, 0.8], 0, 0.4, { star: 'star', flare: 'flare', streak: 'streak', dot: 'dot', shardL: 'shardL' }, CV, { ground: false, n: 0.6 }),
  { name: 'M10 Dust', mat: 'dust', fudge: 22, pos: [0, 1.1, 0.9], life: [0.8, 1.1], size: [0.6, 0.9], rot: [0, TAU], shape: { type: 'sphere', r: 0.3, th: 1 }, bursts: [[0, 6]], speed: [0.6, 1.2], vel: { y: [-0.4, -0.1] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'M11 Scales', mat: 'dot', fudge: -6, pos: [0, 1.1, 0.9], life: [0.8, 1.2], size: [0.04, 0.07], shape: { type: 'sphere', r: 0.4, th: 1 }, bursts: [[0, 24]], speed: [0.5, 1.5], vel: { y: [-0.6, -0.2] }, noise: { s: 0.4, f: 1, sp: 0.5 }, color: [[0, WHITE], [1, LAV]], alpha: [[0, 1], [1, 0]] },
  { name: 'M12 Zzz', mat: 'zzz', fudge: -4, pos: [0, 1.4, 0.9], life: [0.9, 1.2], size: [0.25, 0.35], rot: [-0.3, 0.2], bursts: [[0.05, 2]], shape: { type: 'sphere', r: 0.3, th: 1 }, vel: { y: [0.6, 1] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
];
// 몽유 거인: 큰 손 휘두르기 (앞 반원 반경 4.5)
const WALKER = [
  { name: 'W01 Fill', mat: 'fanV', mode: 'mesh', mesh: 'half45', fudge: 42, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 0.6, alpha: [[0, 0.45], [1, 0]] },
  { name: 'W02 Edge', mat: 'line', mode: 'mesh', mesh: 'half45Edge', fudge: 40, size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 0.55, alpha: [[0, 1], [1, 0]] },
  { name: 'W03 Sweep', mat: 'slash', mode: 'mesh', mesh: 'arc180', fudge: 4, pos: [0, 1.6, 0], size: 4.2, rot3: [0, FWD, 0.1], rotLife: [0, 4, 0], bursts: [[0, 1]], life: 0.36, flip: { x: 2, y: 8 } },
  { name: 'W04 Sweep2', mat: 'slashN', mode: 'mesh', mesh: 'arc180t', fudge: 5, pos: [0, 2.2, 0], size: 3.8, rot3: [0, FWD, 0.1], rotLife: [0, 4.3, 0], bursts: [[0.04, 1]], life: 0.32, flip: { x: 2, y: 8 } },
  { name: 'W05 Wind', mat: 'streak', mode: 'stretch', vs: 0.06, ls: 2.2, fudge: -3, pos: [0, 1.4, 2], life: [0.3, 0.5], size: [0.06, 0.1], shape: { type: 'box', sx: 7, sy: 2, sz: 3 }, bursts: [[0.05, 26]], vel: { x: [-10, -6], z: [2, 5] }, drag: 1.5, alpha: [[0, 1], [1, 0]] },
  { name: 'W06 Dust', mat: 'dust', fudge: 22, pos: [0, 0.4, 2.5], life: [0.7, 1.0], size: [1.0, 1.5], rot: [0, TAU], shape: { type: 'box', sx: 7, sy: 0.2, sz: 3 }, bursts: [[0.06, 8]], vel: { x: [-3, -1], y: [0.2, 0.5] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.5], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'W07 Stars', mat: 'star', fudge: -9, pos: [0, 1.6, 2.5], life: [0.3, 0.5], size: [0.18, 0.3], rot: [0, TAU], shape: { type: 'box', sx: 7, sy: 2, sz: 3 }, bursts: [[0.08, 8]], sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
];
// 악몽 인형: 바늘 투사체 (3연발) + 착탄
const NEEDLE = [
  { name: 'N01 Needle', mat: 'needle', mode: 'mesh', mesh: 'needleM', fudge: -1, size: 1, rot3: [0, 0, 0], rate: 30, bursts: [[0, 1]], life: 0.05 },
  { name: 'N02 Glow', mat: 'glowN', fudge: 0, rate: 20, life: 0.12, size: 0.7, alpha: [[0, 0.6], [1, 0]] },
  { name: 'N03 Thread', mat: 'streakN', mode: 'stretch', vs: 0.03, ls: 1.4, fudge: 0, world: true, rateDist: 18, life: [0.25, 0.35], size: [0.04, 0.06], alpha: [[0, 0.9], [1, 0]] },
  { name: 'N04 Motes', mat: 'dotN', fudge: -6, world: true, rateDist: 6, life: [0.3, 0.5], size: [0.04, 0.07], shape: { type: 'sphere', r: 0.15, th: 1 }, color: [[0, [0.9, 0.7, 1]], [1, VIO]], alpha: [[0, 1], [1, 0]] },
];
const NEEDLE_HIT = [
  ...impactBurst('H', [0, 0, 0], 0, 0.35, { star: 'starN', flare: 'flareN', streak: 'streakN', dot: 'dotN', shardD: 'shardD' }, CN, { ground: false, n: 0.5 }),
  { name: 'H10 Stitch', mat: 'lineN', mode: 'mesh', mesh: 'cOuter', fudge: -2, size: 0.5, rot3: [[0, TAU], [0, TAU], 0], bursts: [[0, 2]], life: 0.3, sizeLife: [[0, 0.3, 0, 3], [1, 1.2]], alpha: [[0, 1], [1, 0]] },
  { name: 'H11 Mist', mat: 'mist', fudge: 25, life: [0.5, 0.7], size: [0.4, 0.6], rot: [0, TAU], shape: { type: 'sphere', r: 0.15, th: 1 }, bursts: [[0.02, 3]], speed: [0.4, 0.8], sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
];
const MATS = {
  line: { tex: 'T_Line', tint: [1.9, 1.6, 2.6], add: true }, lineN: { tex: 'T_Line', tint: [1.7, 0.8, 2.6], add: true },
  glowN: { tex: 'T_Glow', tint: [0.9, 0.35, 1.4], add: true }, star: { tex: 'T_Star', tint: [2.4, 2.2, 2.8], add: true }, starN: { tex: 'T_Star', tint: [2.2, 1.2, 2.8], add: true },
  flare: { tex: 'T_Flare', tint: [1.8, 1.5, 2.4], add: true }, flareN: { tex: 'T_Flare', tint: [1.6, 0.6, 2.3], add: true },
  streak: { tex: 'T_Streak', tint: [2.0, 1.8, 2.6], add: true }, streakN: { tex: 'T_Streak', tint: [1.8, 0.8, 2.6], add: true },
  dot: { tex: 'T_Dot', tint: [2.2, 2.0, 2.6], add: true }, dotN: { tex: 'T_Dot', tint: [2.0, 1.1, 2.6], add: true },
  fanV: { tex: 'T_FanFill', tint: [0.9, 0.6, 1.6], add: true }, dust: { tex: 'T_Dust_Sheet', tint: [1.1, 0.95, 1.6], add: true }, mist: { tex: 'T_DarkMist_Sheet', tint: [1, 1, 1], add: false },
  shardD: { tex: 'T_ShardDark', tint: [1.7, 0.9, 2.4], add: false }, shardL: { tex: 'T_ShardLight', tint: [2.0, 1.8, 2.6], add: true },
  slash: { tex: 'T_Slash_Sheet', tint: [1.8, 1.6, 2.6], add: true }, slashN: { tex: 'T_Slash_Sheet', tint: [1.6, 0.8, 2.6], add: true },
  zzz: { tex: 'T_Zzz', tint: [1.9, 1.7, 2.6], add: true }, needle: { tex: 'T_Streak', tint: [2.6, 2.4, 2.8], add: true },
};
const M0 = { path: [[0, [0, 0, 0], 0]], hidden: true };
const SCENE = {
  name: 'Somnia_Mobs', bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.7, tint: [0.85, 0.8, 1] }, tonemapping: 'neutral',
  systems: [
    { id: 'moth_hit', role: 'static', loop: false, duration: 0.2, layers: MOTH },
    { id: 'walker_swing', role: 'static', loop: false, duration: 0.2, layers: WALKER },
    { id: 'needle', role: 'projectile', loop: true, duration: 1.0, layers: NEEDLE },
    { id: 'needle_hit', role: 'impact', loop: false, duration: 0.2, layers: NEEDLE_HIT },
  ],
  demo: Object.assign({}, DEMO_BG, { interval: 2.6, dummy: { height: 1.8 },
    scenarios: {
      moth: { label: '졸음 나방: 들이받기', path: [[0, [0, 0, 0], 0]], actors: [M0, { path: [[0, [0, 0, 1.3], 180]] }], events: [{ t: 0.3, system: 'moth_hit', actor: 0 }], camera: { target: [0, 1, 0.8], dist: 6, yaw: -60, pitch: 15 } },
      walker: { label: '몽유 거인: 큰 손 휘두르기', path: [[0, [0, 0, 0], 0]], actors: [M0, { path: [[0, [1, 0, 3], 180]] }], events: [{ t: 0.3, system: 'walker_swing', actor: 0 }], camera: { target: [0, 1.2, 2], dist: 10, yaw: -55, pitch: 24 } },
      doll: { label: '악몽 인형: 바늘 3연발', path: [[0, [0, 0, 0], 0]], actors: [M0, { path: [[0, [0, 0, 10], 180]] }],
        events: [0.2, 0.4, 0.6].map((t, i) => ({ t, system: 'needle', actor: 0, from: [0.4, 1.4, 0.3], to: [(i - 1) * 0.4, 1.2, 10], normal: null, flight: 0.35, impact: 'needle_hit' })), camera: { target: [0, 1.3, 5], dist: 10, yaw: -70, pitch: 12 } },
    } }),
};
