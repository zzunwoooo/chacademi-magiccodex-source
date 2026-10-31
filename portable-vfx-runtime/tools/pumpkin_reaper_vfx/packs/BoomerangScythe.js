// PR_BoomerangScythe — 3. 부메랑 낫 (가장 먼 적 방향 직선 18블록, 폭 2). systems: (예고는 모드: 직선 18×2, 0.55초) launch → scythe(투사체, 서버가 왕복 이동) → catch(손으로 돌아옴)
const LEN = 18, WD = 2.0, T = 0.55;
var EXTRA_MESHES = Object.assign({ bladeSpin: arc(0, 250, 0.45, 0), bladeThin: arc(0, 200, 0.82, 0) }, MC_MESHES);
const SCY = [
  { name: "P01 Glow", mat: "glowVio", fudge: 0, loop: true, rate: 12, life: 0.3, size: 1.4, alpha: [[0, 0], [0.2, 0.35], [1, 0]] },
  { name: "P02 BladeInk", mat: "slashInk", mode: "mesh", mesh: "bladeSpin", fudge: 20, loop: true, rate: 10, life: 0.2, size: 2.4, rot3: [0, [0, TAU], 0], rotLife: [0, -30, 0], flip: { x: 2, y: 8 } },
  { name: "P03 Blade", mat: "slashHot", mode: "mesh", mesh: "bladeSpin", fudge: 10, loop: true, rate: 14, life: 0.18, size: 2.2, rot3: [0, [0, TAU], 0], rotLife: [0, -32, 0], flip: { x: 2, y: 8 } },
  { name: "P04 BladeEcho", mat: "slashViolet", mode: "mesh", mesh: "bladeThin", fudge: 12, loop: true, rate: 10, life: 0.22, size: 2.6, rot3: [[-0.2, 0.2], [0, TAU], 0], rotLife: [0, -28, 0], flip: { x: 2, y: 8 } },
  { name: "P05 SoulCore", mat: "soulFlame", fudge: 0, loop: true, rate: 30, life: [0.2, 0.32], size: [0.35, 0.55], rot: [0, TAU], shape: { type: "sphere", r: 0.15, th: 1 }, flip: F44 },
  { name: "P06 TrailGhost", mat: "slashViolet", mode: "mesh", mesh: "bladeThin", fudge: 14, loop: true, world: true, rateDist: 2.2, life: 0.3, size: 1.6, rot3: [0, [0, TAU], 0], rotLife: [0, -10, 0],
    alpha: [[0, 0.55], [1, 0]], flip: { x: 2, y: 8 } },
  { name: "P07 TrailFlame", mat: "flame", fudge: 0, loop: true, world: true, rateDist: 8, life: [0.25, 0.45], size: [0.4, 0.75], rot: [0, TAU], shape: { type: "sphere", r: 0.5, th: 1 },
    vel: { y: [0.3, 0.8] }, sizeLife: [[0, 1], [1, 0.3]], flip: F44 },
  { name: "P08 TrailMist", mat: "mist", fudge: 30, loop: true, world: true, rateDist: 2.5, life: [0.5, 0.8], size: [0.8, 1.3], rot: [0, TAU], shape: { type: "sphere", r: 0.4, th: 1 },
    alpha: [[0, 0], [0.15, 0.5], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
  { name: "P09 TrailSparks", mat: "streakOra", mode: "stretch", vs: 0.08, ls: 1.5, fudge: -5, loop: true, world: true, rateDist: 7, life: [0.2, 0.4], size: [0.04, 0.08],
    speed: [2, 5], shape: { type: "sphere", r: 0.7, th: 1 }, gravity: 0.4, alpha: [[0, 1], [1, 0]] },
  { name: "P10 TrailMotes", mat: "dotSoul", fudge: -7, loop: true, world: true, rateDist: 6, life: [0.6, 1.0], size: [0.04, 0.09], shape: { type: "sphere", r: 0.8, th: 1 },
    vel: { y: [0.4, 1] }, noise: { s: 0.5, f: 1, sp: 0.5 }, alpha: [[0, 1], [1, 0]] },
];
const CATCH = [...fxFlash("C1", [0, 0, 0], 0, 2.2, "starVio"), ...fxSparks("C2", [0, 0, 0], 0, 16, [3, 7], "streakVio"), ...fxMotes("C3", [0, 0, 0], 0.02, 14, 0.5)];
const LAUNCH = [...fxFlash("L1", [0.8, 1.6, 0.8], 0, 2.4, "starOra"), ...fxSparks("L2", [0.8, 1.6, 0.8], 0, 18), ...fxMist("L3", [0.8, 1.4, 1.2], 0, 4, 0.5, [1, 1.6], [0.5, 1])];
const MATS = FX_MATS;
const PATH = [[0.8, 1.6, 0.8], [1.5, 1.5, 6], [0.8, 1.4, 12], [-0.4, 1.4, 17.2], [-1.6, 1.5, 12], [-1.4, 1.6, 6], [0.8, 1.6, 0.8]];
const SCENE = {
  name: "PR_BoomerangScythe", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "launch", role: "static", loop: false, duration: 1.2, layers: LAUNCH },
    { id: "scythe", role: "projectile", loop: true, duration: 60, layers: SCY },
    { id: "catch", role: "impact", loop: false, duration: 1.2, layers: CATCH },
  ],
  demo: Object.assign({ interval: 2.8 }, DEMO_BG, { scenarios: {
    base: { label: "기본 왕복", path: [[0, [0, 0, 0], 0]], fire: { at: T, stop: [] }, flightPath: PATH, flight: 1.4, normal: null, anchor: [0, 0, 0],
      place: { launch: { at: [0, 0, 0], start: T } }, targets: [[0, 0, 17, 180], [3, 0, 6, 220]], camera: cam([0, 1, 8], 22, -60, 40) } } }),
};
