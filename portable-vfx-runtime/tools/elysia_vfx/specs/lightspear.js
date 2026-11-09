// Elysia_LightSpear — 엘리시아 기본 공격(빛의 창). projectile → impact. 창은 깃털 꼬리를 끌며 날아가 꽂힌다.
function spearM(L, w, z0) { return merge([
  { verts: [[-w / 2, 0, z0], [w / 2, 0, z0], [-w / 2, 0, z0 + L], [w / 2, 0, z0 + L]], uvs: [[0, 0], [0, 1], [1, 0], [1, 1]], tris: [0, 1, 2, 1, 3, 2] },
  { verts: [[0, -w / 2, z0], [0, w / 2, z0], [0, -w / 2, z0 + L], [0, w / 2, z0 + L]], uvs: [[0, 0], [0, 1], [1, 0], [1, 1]], tris: [0, 1, 2, 1, 3, 2] }]); }
var EXTRA_MESHES = { spear: spearM(2.2, 0.34, -1.9), spearCore: spearM(1.9, 0.12, -1.6), ringV: ribbon(48, s => { const p = s * TAU, d = [Math.cos(p), Math.sin(p), 0]; return [mul(d, 0.95), mul(d, 1.05)]; }),
  ground: merge([ringM(1, 0.04, 0.03), ringM(0.9, 0.012, 0.03), polygram(6, 2, 0.88, 0.014, 0.035)]) };
const PROJ = [
  { name: "P01 Spear", mat: "streak", mode: "mesh", mesh: "spear", fudge: -2, rate: 30, bursts: [[0, 1]], life: 0.07, size: 1, alpha: [[0, 1], [1, 1]] },
  { name: "P02 SpearCore", mat: "core", mode: "mesh", mesh: "spearCore", fudge: -3, rate: 30, bursts: [[0, 1]], life: 0.07, size: 1 },
  { name: "P03 Tip", mat: "star", fudge: -6, rate: 30, life: 0.08, size: 0.75, rot: [0, TAU] },
  { name: "P04 Glow", mat: "glow", fudge: 0, rate: 20, life: 0.15, size: 1.3, alpha: [[0, 0.6], [1, 0]] },
  { name: "P05 Trail", mat: "streak", mode: "stretch", vs: 0.03, ls: 1.2, fudge: 0, world: true, rateDist: 18, life: [0.2, 0.3], size: [0.12, 0.2], alpha: [[0, 0.7], [1, 0]] },
  { name: "P06 Feathers", mat: "feather", fudge: -1, world: true, rateDist: 2.5, life: [0.6, 0.9], size: [0.14, 0.22], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "sphere", r: 0.15, th: 1 },
    vel: { y: [-0.4, 0.2] }, noise: { s: 0.5, f: 1, sp: 0.5 }, alpha: [[0, 1], [0.7, 0.8], [1, 0]] },
  { name: "P07 Motes", mat: "dot", fudge: -6, world: true, rateDist: 10, life: [0.4, 0.7], size: [0.03, 0.06], shape: { type: "sphere", r: 0.2, th: 1 }, speed: [0.2, 0.8],
    color: [[0, PALE], [1, AMBER]], alpha: [[0, 1], [1, 0]] },
  { name: "P08 SpinRing", mat: "line", mode: "mesh", mesh: "ringV", fudge: -1, rate: 10, life: 0.2, size: 0.35, rot3: [0, 0, [0, TAU]], sizeLife: [[0, 0.6], [1, 1.4]], alpha: [[0, 0.8], [1, 0]] },
];
const IMP = [
  { name: "I01 Flash", mat: "star", fudge: -8, pos: [0, 0.6, 0], size: 2.6, rot: [0, TAU], bursts: [[0, 1]], life: 0.2, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: "I02 Flare", mat: "flare", fudge: -7, pos: [0, 0.6, 0], size: 3, rot: [0, TAU], bursts: [[0, 1]], life: 0.35, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.75], [1, 0]] },
  { name: "I03 Spear", mat: "streak", mode: "stretch", vs: 0.05, ls: 1, fudge: -2, pos: [0, 0.5, 0], life: 0.5, size: 0.35, bursts: [[0, 1]], vel: { y: -0.4 }, alpha: [[0, 1], [1, 0]] },
  { name: "I04 Ground", mat: "line", mode: "mesh", mesh: "ground", fudge: 30, size: 1.3, rot3: YAW, rotLife: [0, 2, 0], bursts: [[0, 1]], life: 0.8, sizeLife: [[0, 0.3, 0, 3], [0.3, 1], [1, 1.1]], alpha: [[0, 1], [1, 0]] },
  { name: "I05 Wave", mat: "wave", mode: "mesh", mesh: "quad", fudge: 31, pos: [0, 0.05, 0], size: 3.4, rot3: YAW, bursts: [[0, 1], [0.08, 1]], life: 0.4, sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.75], [1, 0]] },
  { name: "I06 Rays", mat: "streak", mode: "stretch", vs: 0.07, ls: 3, fudge: -3, pos: [0, 0.3, 0], life: [0.25, 0.4], size: [0.08, 0.14], bursts: [[0, 14]], shape: { type: "circle", r: 0.2, th: 1, y: 0 },
    vel: { rad: [8, 12], y: [1, 3] }, drag: 5, alpha: [[0, 1], [1, 0]] },
  { name: "I07 Sparks", mat: "dot", mode: "stretch", vs: 0.05, ls: 1.2, fudge: -6, pos: [0, 0.5, 0], life: [0.4, 0.7], size: [0.04, 0.08], bursts: [[0, 30]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [4, 9], drag: 2.5, gravity: 0.6, color: [[0, PALE], [1, AMBER]], alpha: [[0, 1], [1, 0]] },
  { name: "I08 Feathers", mat: "feather", fudge: -1, pos: [0, 0.6, 0], life: [0.8, 1.2], size: [0.18, 0.28], rot: [0, TAU], rotZ: [-3, 3], bursts: [[0, 10]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [2.5, 4.5], drag: 3, gravity: 0.12, alpha: [[0, 1], [0.7, 0.8], [1, 0]] },
  { name: "I09 DarkShards", mat: "shardDark", fudge: 16, pos: [0, 0.5, 0], life: [0.4, 0.7], size: [0.12, 0.22], rot: [0, TAU], rotZ: [-10, 10], bursts: [[0, 8]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [3, 6], drag: 1, gravity: 1.3, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "I10 Dust", mat: "dust", fudge: 20, pos: [0, 0.4, 0], life: [0.6, 0.9], size: [0.7, 1.2], rot: [0, TAU], bursts: [[0.02, 6]], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [1.2, 2.2], drag: 2, vel: { y: [0.3, 0.6], space: "world" }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.5], [1, 0]], flip: { x: 4, y: 4 } },
];
const MATS = {
  streak: { tex: "T_Streak", tint: [2.6, 2.2, 1.2], add: true }, core: { tex: "T_Streak", tint: [3.0, 3.0, 2.6], add: true }, star: { tex: "T_Star", tint: [2.8, 2.5, 1.6], add: true },
  glow: { tex: "T_Glow", tint: [1.5, 1.15, 0.5], add: true }, feather: { tex: "T_Feather", tint: [2.0, 1.8, 1.3], add: true }, dot: { tex: "T_Dot", tint: [2.3, 2.0, 1.2], add: true },
  line: { tex: "T_Line", tint: [2.5, 2.1, 1.1], add: true }, flare: { tex: "T_Flare", tint: [2.2, 1.8, 0.95], add: true }, wave: { tex: "T_Wave", tint: [1.8, 1.5, 0.75], add: true },
  shardDark: { tex: "T_ShardDark", tint: [1.6, 1.3, 0.7], add: false }, dust: { tex: "T_HolyDust_Sheet", tint: [1.4, 1.2, 0.9], add: true },
};
const SCENE = {
  name: "Elysia_LightSpear", bloom: BLOOM_HOLY, tonemapping: "neutral",
  systems: [{ id: "projectile", role: "projectile", loop: true, duration: 1.0, layers: PROJ }, { id: "impact", role: "impact", loop: false, duration: 1.0, layers: IMP }],
  demo: Object.assign({}, DEMO_BG, { interval: 2.6,
    scenarios: {
      ground: { label: "바닥", from: [-5, 3.2, 0], to: [1.4, 0, 0], normal: [0, 1, 0], flight: 0.5, camera: { target: [-0.6, 1.0, 0], dist: 8, yaw: -90, pitch: 14 } },
      wall: { label: "벽", from: [-5, 2.0, 0], to: [1.6, 1.3, 0], normal: [-1, 0, 0], flight: 0.5, wall: { x: 1.6 }, camera: { target: [0.2, 1.2, 0], dist: 8, yaw: -112, pitch: 14 } },
      air: { label: "공중", from: [-5, 2.0, 0], to: [1.0, 1.7, 0], normal: null, flight: 0.5, camera: { target: [-0.4, 1.1, 0], dist: 8, yaw: -90, pitch: 12 } },
    } }),
};
