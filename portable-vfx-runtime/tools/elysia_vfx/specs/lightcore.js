// Elysia_LightCore — 빛의 핵(축복의 제안 정공법 / 빌린 힘의 반환). static 2종, P = 핵 결정 중심(모드가 소품 위치에 놓음).
//  hit   : 핵을 칠 때마다 (0.5 s)
//  break : 핵 파괴 (1.3 s) — 0.12 s 수축 후 폭발, 위로 짧은 빛기둥
var EXTRA_MESHES = { col: merge([{ verts: [[-0.15, 0, 0], [0.15, 0, 0], [-0.15, 1, 0], [0.15, 1, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }, { verts: [[0, 0, -0.15], [0, 0, 0.15], [0, 1, -0.15], [0, 1, 0.15]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]), ringV: ribbon(64, s => { const p = s * TAU, d = [Math.cos(p), Math.sin(p), 0]; return [mul(d, 0.96), mul(d, 1.04)]; }) };
const HIT = [
  { name: "H01 Flash", mat: "star", fudge: -8, size: 1.3, rot: [0, TAU], bursts: [[0, 1]], life: 0.18, sizeLife: [[0, 0.4], [0.3, 1], [1, 0]] },
  { name: "H02 Glow", mat: "glow", fudge: 0, size: 1.4, bursts: [[0, 1]], life: 0.3, sizeLife: [[0, 0.5], [0.3, 1], [1, 0.8]], alpha: [[0, 0.6], [1, 0]] },
  { name: "H03 Ring", mat: "ring", fudge: -4, size: 1.6, bursts: [[0, 1]], life: 0.3, sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "H04 Shards", mat: "shardLight", fudge: -3, life: [0.35, 0.55], size: [0.08, 0.15], rot: [0, TAU], rotZ: [-14, 14], bursts: [[0, 9]], shape: { type: "sphere", r: 0.15, th: 1 },
    speed: [3, 5], drag: 3, gravity: 0.8, alpha: [[0, 1], [1, 0]] },
  { name: "H05 Sparks", mat: "dot", mode: "stretch", vs: 0.05, ls: 1.2, fudge: -6, life: [0.25, 0.45], size: [0.04, 0.08], bursts: [[0, 18]], shape: { type: "sphere", r: 0.1, th: 1 },
    speed: [4, 8], drag: 4, color: [[0, PALE], [1, AMBER]], alpha: [[0, 1], [1, 0]] },
  { name: "H06 Crack", mat: "line", mode: "mesh", mesh: "ringV", fudge: -2, size: 0.55, rot3: [[0, TAU], [0, TAU], 0], bursts: [[0, 2]], life: 0.25, sizeLife: [[0, 0.5, 0, 3], [1, 1.2]], alpha: [[0, 1], [1, 0]] },
];
const BREAK = [
  { name: "K01 Implode", mat: "ring", fudge: -4, size: 2.2, bursts: [[0, 1]], life: 0.14, sizeLife: [[0, 1], [1, 0.15]], alpha: [[0, 0], [0.4, 1], [1, 1]] },
  { name: "K02 InSpark", mat: "streak", mode: "stretch", vs: 0.08, ls: 1.3, fudge: -5, life: 0.13, size: [0.05, 0.08], shape: { type: "sphere", r: 1.2, th: 0 }, bursts: [[0, 16]], vel: { rad: [-9, -8] }, alpha: [[0, 0.3], [1, 1]] },
  { name: "K03 Flash", mat: "star", fudge: -8, size: 3, rot: [0, TAU], bursts: [[0.13, 1]], life: 0.25, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: "K04 Flare", mat: "flare", fudge: -7, size: 3.6, rot: [0, TAU], rotZ: [-0.8, 0.8], bursts: [[0.13, 1]], life: 0.45, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.15]], alpha: [[0, 0.8], [1, 0]] },
  { name: "K05 Wave1", mat: "wave", fudge: -2, size: 4, bursts: [[0.13, 1]], life: 0.45, sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.75], [1, 0]] },
  { name: "K06 Wave2", mat: "line", mode: "mesh", mesh: "ringV", fudge: -3, size: 1.8, rot3: [[0, TAU], [0, TAU], 0], bursts: [[0.13, 1], [0.2, 1]], life: 0.4, sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "K07 Column", mat: "pillar", mode: "mesh", mesh: "col", fudge: 5, pos: [0, -0.6, 0], size: 1, rot3: YAW, rotLife: [0, 3, 0], bursts: [[0.13, 1]], life: 0.6,
    sizeLife: [[0, 1, 0, 10], [0.25, 5], [1, 5.5]], alpha: [[0, 1], [0.5, 0.7], [1, 0]] },
  { name: "K08 Shards", mat: "shardLight", fudge: -3, life: [0.6, 1.0], size: [0.14, 0.3], rot: [0, TAU], rotZ: [-10, 10], bursts: [[0.13, 22]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [4, 8], drag: 1.5, gravity: 1, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "K09 DarkShards", mat: "shardDark", fudge: 18, life: [0.6, 0.9], size: [0.12, 0.24], rot: [0, TAU], rotZ: [-10, 10], bursts: [[0.14, 8]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [3, 6], drag: 1, gravity: 1.3, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "K10 Sparks", mat: "dot", mode: "stretch", vs: 0.05, ls: 1.3, fudge: -6, life: [0.5, 0.9], size: [0.05, 0.1], bursts: [[0.13, 40]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [5, 11], drag: 2.5, gravity: 0.5, color: [[0, PALE], [0.5, GOLD], [1, AMBER]], alpha: [[0, 1], [1, 0]] },
  { name: "K11 Motes", mat: "dot", fudge: -6, life: [0.9, 1.4], size: [0.04, 0.08], bursts: [[0.2, 24]], shape: { type: "sphere", r: 0.8, th: 1 },
    vel: { y: [0.5, 1.4] }, noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, PALE], [1, GOLD]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  { name: "K12 Dust", mat: "dust", fudge: 20, life: [0.7, 1.0], size: [0.8, 1.3], rot: [0, TAU], bursts: [[0.15, 6]], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [1.5, 2.5], drag: 2, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
];
const MATS = {
  star: { tex: "T_Star", tint: [2.8, 2.5, 1.6], add: true }, glow: { tex: "T_Glow", tint: [1.5, 1.2, 0.55], add: true }, ring: { tex: "T_Ring", tint: [2.4, 2.0, 1.0], add: true },
  shardLight: { tex: "T_ShardLight", tint: [2.4, 2.1, 1.2], add: true }, shardDark: { tex: "T_ShardDark", tint: [1.6, 1.3, 0.7], add: false },
  dot: { tex: "T_Dot", tint: [2.4, 2.0, 1.2], add: true }, line: { tex: "T_Line", tint: [2.6, 2.2, 1.2], add: true }, streak: { tex: "T_Streak", tint: [2.5, 2.1, 1.2], add: true },
  flare: { tex: "T_Flare", tint: [2.3, 1.9, 1.0], add: true }, wave: { tex: "T_Wave", tint: [1.9, 1.6, 0.8], add: true }, pillar: { tex: "T_PillarHoly", tint: [2.0, 1.8, 1.3], add: true },
  dust: { tex: "T_HolyDust_Sheet", tint: [1.4, 1.2, 0.9], add: true },
};
const SCENE = {
  name: "Elysia_LightCore", bloom: BLOOM_HOLY, tonemapping: "neutral",
  systems: [{ id: "hit", role: "static", loop: false, duration: 0.2, layers: HIT }, { id: "break", role: "static", loop: false, duration: 0.3, layers: BREAK }],
  demo: Object.assign({}, DEMO_BG, { interval: 4,
    scenarios: { all: { label: "타격 ×3 → 파괴", path: [[0, [0, 0, 0], 0]], actors: [{ path: [[0, [0, 1.4, 0], 0]], hidden: true }],
      events: [{ t: 0.2, system: "hit", actor: 0 }, { t: 0.8, system: "hit", actor: 0 }, { t: 1.4, system: "hit", actor: 0 }, { t: 2.0, system: "break", actor: 0 }],
      camera: { target: [0, 1.6, 0], dist: 7, yaw: -90, pitch: 12 } } } }),
};
