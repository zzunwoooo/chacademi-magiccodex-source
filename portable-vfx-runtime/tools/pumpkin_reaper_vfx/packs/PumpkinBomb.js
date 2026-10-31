// PR_PumpkinBomb — 4. 호박 폭탄 (세 명 발밑에 하나씩, 원형 반경 2.2블록, 예고 1.5초). systems: (예고는 모드: 대상 발밑 원 반경 2.2, 1.5초) throw(보스 손) → bomb(투사체 포물선) → boom(착탄)
const R = 2.2, T = 1.5;
var EXTRA_MESHES = MC_MESHES;
const BOMB = [
  { name: "P01 Face", mat: "sigilOra", fudge: -1, loop: true, rate: 20, life: 0.12, size: 1.1, rot: [-0.3, 0.3], alpha: [[0, 0.9], [1, 0.9]] },
  { name: "P02 Core", mat: "flame", fudge: 0, loop: true, rate: 40, life: [0.18, 0.28], size: [0.7, 1.0], rot: [0, TAU], rotZ: [-5, 5], shape: { type: "sphere", r: 0.1, th: 1 }, flip: F44 },
  { name: "P03 Glow", mat: "glowOra", fudge: 1, loop: true, rate: 10, life: 0.3, size: 1.4, alpha: [[0, 0], [0.2, 0.35], [1, 0]] },
  { name: "P04 TrailFlame", mat: "flame", fudge: 0, loop: true, world: true, rateDist: 12, life: [0.25, 0.45], size: [0.35, 0.7], rot: [0, TAU], shape: { type: "sphere", r: 0.2, th: 1 },
    vel: { y: [0.3, 0.8] }, sizeLife: [[0, 1], [1, 0.3]], flip: F44 },
  { name: "P05 TrailMist", mat: "mist", fudge: 30, loop: true, world: true, rateDist: 3, life: [0.5, 0.9], size: [0.6, 1.1], rot: [0, TAU], shape: { type: "sphere", r: 0.2, th: 1 },
    vel: { y: [0.3, 0.6] }, alpha: [[0, 0], [0.15, 0.55], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
  { name: "P06 Seeds", mat: "dotFire", fudge: -6, loop: true, world: true, rateDist: 10, life: [0.5, 1.0], size: [0.04, 0.08], speed: [0.5, 1.5], shape: { type: "sphere", r: 0.25, th: 1 },
    gravity: 0.4, color: [[0, YEL], [1, CRIM]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
];
const BOOM = [
  ...fxFlash("I1", [0, 0.9, 0], 0, 3.0, "starOra"),
  { name: "I2 Fireball", mat: "flame", fudge: 0, pos: [0, 0.9, 0], bursts: [[0, 16], [0.06, 8]], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [2.5, 5.5], drag: 5, life: [0.45, 0.75], size: [0.9, 1.6],
    rot: [0, TAU], vel: { y: [0.3, 1], space: "world" }, sizeLife: [[0, 0.5, 0, 3], [1, 1.2]], flip: F44 },
  { name: "I3 SoulPuff", mat: "soulFlame", fudge: 1, pos: [0, 0.8, 0], bursts: [[0.05, 8]], shape: { type: "sphere", r: 0.4, th: 1 }, speed: [2, 4], drag: 4, life: [0.4, 0.6], size: [0.6, 1.0],
    rot: [0, TAU], vel: { y: [0.5, 1.2], space: "world" }, flip: F44 },
  ...fxMist("I4", [0, 0.9, 0], 0.05, 10, 0.4, [1.2, 2.0], [1.5, 3]),
  ...fxShock("I5", [0, 0, 0], 0, 2 * R + 0.8),
  ...fxCrack("I6", [0, 0, 0], 0.02, 3.6),
  ...fxRays("I7", [0, 0.9, 0], 0.02, 12, [8, 13], "streakOra", 3),
  ...fxSparks("I8", [0, 0.9, 0], 0, 34, [5, 11]),
  ...fxShards("I9", [0, 0.9, 0], 0, 14, 0.3, [4, 8]),
  ...fxBats("I10", [0, 1.0, 0], 0.06, 5, 0.5),
  ...fxEmbers("I11", [0, 0.9, 0], 0.05, 40, 0.8),
  ...fxPillars("I12", [0, 0, 0], 0.02, 5, R * 0.6, 2.4),
];
const THROW = [...fxFlash("H1", [-0.8, 1.9, 0.6], 0, 1.8, "starOra"), ...fxSparks("H2", [-0.8, 1.9, 0.6], 0, 10, [3, 6]), ...fxFlames("H3", [-0.8, 1.9, 0.6], 0, 4, 0.2, "flame", [0.5, 0.8])];
const MATS = FX_MATS;
const TGT = [3.0, 0, 7.5];
const ARC = [[-0.8, 1.9, 0.6], [0.2, 4.6, 2.8], [1.5, 6.0, 4.6], [2.4, 4.4, 6.4], [3.0, 0.05, 7.5]];
const SCENE = {
  name: "PR_PumpkinBomb", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "throw", role: "static", loop: false, duration: 1.0, layers: THROW },
    { id: "bomb", role: "projectile", loop: true, duration: 60, layers: BOMB },
    { id: "boom", role: "impact", loop: false, duration: 2.0, layers: BOOM },
  ],
  demo: Object.assign({ interval: 3.6 }, DEMO_BG, { scenarios: {
    base: { label: "기본", path: [[0, [0, 0, 0], 0]], fire: { at: T - 1.0, stop: [] }, flightPath: ARC, flight: 1.0, normal: [0, 1, 0], anchor: [0, 0, 0],
      place: { throw: { at: [0, 0, 0], start: T - 1.0 } }, targets: [[3.0, 0, 7.5, 180]], camera: cam([1.5, 1.5, 4.5], 15, -60, 32) } } }),
};
