// Elysia_Blessing — 축복의 제안 수락(금빛 깃털을 주움): 플레이어 머리 위 후광 + 깃털 오라. attached phased: begin → sustain(버프 시간) → end(후광이 부서짐).
// P = 플레이어 발, 정면 = 시선 수평 방향. 후광 높이 HY = 2.15 m (키 1.8 m 기준).
const HY = 2.15, LIFE = 3, HALF = LIFE / 2, W12 = (TAU / 12) / HALF;
var EXTRA_MESHES = {
  halo: merge([ringM(0.34, 0.075, 0), ringM(0.34, 0.02, 0.012), ringM(0.27, 0.02, 0), vtxCircles(12, 0.34, 0.025, 0.014, 0.01)]),
  haloRays: ticks(12, 0.38, 0.5, 0.012, 0),
  feet: merge([ringM(0.75, 0.025, 0.03), polygram(6, 2, 0.7, 0.012, 0.035), ringM(0.66, 0.01, 0.03)]),
};
const HB = [0, HY, 0];
const BEGIN = [
  { name: "B01 FeatherSwirl", mat: "feather", fudge: -1, life: 0.8, size: [0.22, 0.3], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "circle", r: 1.0, th: 0, y: 0.4 }, bursts: [[0, 10]],
    vel: { orb: [4, 5], rad: [-1.2, -1.0], y: [1.8, 2.4] }, alpha: [[0, 0], [0.2, 1], [0.8, 1], [1, 0]] },
  { name: "B02 HaloForm", mat: "line", mode: "mesh", mesh: "halo", fudge: -2, pos: HB, size: 1, rot3: [0, 0, 0], rotLife: [0, W12, 0], bursts: [[0.45, 1]], life: LIFE - 0.45,
    sizeLife: [[0, 2.2, 0, -5], [0.15, 1]], alpha: [[0, 0], [0.1, 1], [(HALF - 0.45) / (LIFE - 0.45), 1], [1, 0]] },
  { name: "B03 HaloFlash", mat: "flare", fudge: -6, pos: HB, size: 1.4, rot: [0, TAU], bursts: [[0.6, 1]], life: 0.35, sizeLife: [[0, 0.3], [0.3, 1], [1, 1.1]], alpha: [[0, 0.8], [1, 0]] },
  { name: "B04 Feet", mat: "line", mode: "mesh", mesh: "feet", fudge: 30, size: 1, rot3: YAW, rotLife: [0, 1.5, 0], bursts: [[0, 1]], life: 1.0,
    sizeLife: [[0, 0.4, 0, 3], [0.3, 1], [1, 1.1]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "B05 Up", mat: "streak", mode: "stretch", vs: 0.06, ls: 2, fudge: -3, life: [0.4, 0.6], size: [0.05, 0.09], shape: { type: "circle", r: 0.6, th: 1, y: 0.1 }, bursts: [[0.05, 16]],
    vel: { y: [5, 7] }, drag: 2.5, alpha: [[0, 1], [1, 0]] },
  { name: "B06 BodyGlow", mat: "glow", fudge: 4, pos: [0, 1.0, 0], size: 2.2, bursts: [[0.1, 1]], life: 0.8, sizeLife: [[0, 0.5], [0.4, 1], [1, 1]], alpha: [[0, 0], [0.4, 0.4], [1, 0]] },
];
const SUSTAIN = [
  { name: "S01 Halo", mat: "line", mode: "mesh", mesh: "halo", fudge: -2, pos: HB, size: 1, rot3: [0, 0, 0], rotLife: [0, W12, 0], rate: 1 / HALF, life: LIFE, alpha: [[0, 0], [0.5, 1], [1, 0]], onStop: 0.15 },
  { name: "S02 HaloRays", mat: "line", mode: "mesh", mesh: "haloRays", fudge: -1, pos: HB, size: 1, rot3: [0, 0, 0], rotLife: [0, -W12, 0], rate: 1 / HALF, life: LIFE, alpha: [[0, 0], [0.5, 0.6], [1, 0]], onStop: 0.15 },
  { name: "S03 HaloGlow", mat: "glow", fudge: 2, pos: HB, size: 0.75, rate: 2, life: 1.0, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.22], [1, 0]], onStop: 0.15 },
  { name: "S04 OrbitFeathers", mat: "feather", fudge: -1, life: 2.4, size: [0.16, 0.22], rot: [0, TAU], rotZ: [-0.6, 0.6], shape: { type: "circle", r: 0.75, th: 0, y: 1.1 }, rate: 2.5, bursts: [[0, 5]],
    vel: { orb: [1.6, 2.0], y: [-0.15, 0.15] }, alpha: [[0, 0], [0.2, 0.9], [0.8, 0.9], [1, 0]], onStop: 0.25 },
  { name: "S05 Motes", mat: "dot", fudge: -6, life: [1.0, 1.6], size: [0.03, 0.06], shape: { type: "circle", r: 0.55, th: 1, y: 0.2 }, rate: 14,
    vel: { y: [0.7, 1.4] }, noise: { s: 0.3, f: 1, sp: 0.4 }, color: [[0, PALE], [1, GOLD]], alpha: [[0, 0], [0.15, 1], [1, 0]], onStop: 0.2 },
  { name: "S06 HaloSpark", mat: "star", fudge: -8, pos: HB, life: [0.25, 0.4], size: [0.1, 0.18], rot: [0, TAU], shape: { type: "circle", r: 0.34, th: 0, y: 0 }, rate: 5, sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: "S07 HandSpark", mat: "streak", mode: "stretch", vs: 0.05, ls: 1.6, fudge: -3, life: [0.3, 0.5], size: [0.03, 0.06], shape: { type: "circle", r: 0.45, th: 1, y: 1.0 }, rate: 6,
    vel: { y: [1.5, 2.5] }, alpha: [[0, 1], [1, 0]] },
];
const END = [
  { name: "E01 HaloCrack", mat: "line", mode: "mesh", mesh: "halo", fudge: -2, pos: HB, size: 1, rot3: YAW, bursts: [[0, 1]], life: 0.3, sizeLife: [[0, 1], [1, 1.35]], alpha: [[0, 1], [1, 0]] },
  { name: "E02 Shards", mat: "shardLight", fudge: -4, pos: HB, life: [0.5, 0.8], size: [0.07, 0.12], rot: [0, TAU], rotZ: [-12, 12], shape: { type: "circle", r: 0.34, th: 0, y: 0 }, bursts: [[0, 14]],
    vel: { rad: [1.2, 2] }, gravity: 1.2, alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "E03 Flash", mat: "flare", fudge: -6, pos: HB, size: 1.1, rot: [0, TAU], bursts: [[0, 1]], life: 0.25, sizeLife: [[0, 0.4], [0.3, 1], [1, 1]], alpha: [[0, 0.7], [1, 0]] },
  { name: "E04 FallFeathers", mat: "feather", fudge: -1, life: [1.0, 1.4], size: [0.14, 0.2], rot: [0, TAU], rotZ: [-2, 2], shape: { type: "circle", r: 0.7, th: 0.5, y: 1.2 }, bursts: [[0, 6]],
    vel: { y: [-0.5, -0.3] }, noise: { s: 0.4, f: 0.8, sp: 0.4 }, alpha: [[0, 0.9], [1, 0]] },
];
const MATS = {
  line: { tex: "T_Line", tint: [2.6, 2.1, 1.0], add: true }, flare: { tex: "T_Flare", tint: [2.2, 1.8, 0.9], add: true }, glow: { tex: "T_Glow", tint: [1.3, 1.0, 0.45], add: true },
  feather: { tex: "T_Feather", tint: [2.0, 1.8, 1.25], add: true }, streak: { tex: "T_Streak", tint: [2.4, 2.0, 1.1], add: true }, dot: { tex: "T_Dot", tint: [2.3, 2.0, 1.2], add: true },
  star: { tex: "T_Star", tint: [2.6, 2.3, 1.4], add: true }, shardLight: { tex: "T_ShardLight", tint: [2.3, 1.9, 0.9], add: true },
};
const SCENE = {
  name: "Elysia_Blessing", bloom: BLOOM_HOLY, tonemapping: "neutral",
  sequence: { sustainStart: HALF, hold: { default: 5, min: 1, max: 60 } },
  systems: [
    { id: "begin", role: "attached", phase: "begin", loop: false, duration: 0.7, layers: BEGIN },
    { id: "sustain", role: "attached", phase: "sustain", loop: true, duration: 6, layers: SUSTAIN },
    { id: "end", role: "attached", phase: "end", loop: false, duration: 0.2, layers: END },
  ],
  demo: Object.assign({}, DEMO_BG, { endTail: 1.5, dummy: { height: 1.8 },
    scenarios: { walk: { label: "걷는 중", path: [[0, [0, 0, 0], 0], [3, [0, 0, 0], 0], [6, [0, 0, 3], 0], [8, [2, 0, 4], 60]],
      camera: { follow: true, target: [0, 1.3, 0], dist: 5.5, yaw: -60, pitch: 14 } } } }),
};
