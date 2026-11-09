// Elysia_Salvation — 구원(목숨 빌리기)과 그 회수.
//  revive (static, P = 쓰러진 플레이어 발): 하늘에서 빛기둥 + 발밑 성광진, 1.4 s에 부활 폭발 (천사의 손 모델은 따로). 약 2.8 s.
//  seize  (attached, 플레이어): 목숨 회수 — 가슴에서 붉은 심장이 뽑혀 올라가 깨짐. 약 1.6 s.
//  drain  (attached, 반복): 회복 불가 12초 동안 붙는 검붉은 기운. 모드가 정지 신호로 끈다.
const REV = 1.4;
var EXTRA_MESHES = {
  cOuter: merge([ringM(1, 0.035, 0.03), ringM(0.94, 0.012, 0.03), ticks(24, 0.95, 1.04, 0.012, 0.03)]),
  cStar: merge([polygram(6, 2, 0.9, 0.016, 0.035), vtxCircles(6, 0.9, 0.06, 0.012, 0.035), ringM(0.55, 0.018, 0.04)]),
  cRune: runeArcs(10, 0.74, 0.12, 0.032),
  beam: merge([{ verts: [[-0.5, 0, 0], [0.5, 0, 0], [-0.5, 12, 0], [0.5, 12, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] },
               { verts: [[0, 0, -0.5], [0, 0, 0.5], [0, 12, -0.5], [0, 12, 0.5]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]),
  crack: merge([ringM(0.9, 0.03, 0.03), ringM(0.82, 0.01, 0.03), polygram(5, 2, 0.82, 0.012, 0.035, -Math.PI / 2)]),
};
const circ = (nm, mesh, mat, f, w, t0, a) => ({ name: nm, mat, mode: "mesh", mesh, fudge: f, size: 1.4, rot3: YAW, rotLife: [0, w, 0], bursts: [[t0, 1]], life: 2.6 - t0,
  sizeLife: [[0, 0.7, 0, 1.6], [0.2, 1]], alpha: [[0, 0], [0.15, a], [(REV - t0) / (2.6 - t0), a], [(REV + 0.05 - t0) / (2.6 - t0), 1], [0.8, 0.6], [1, 0]] });
const REVIVE = [
  circ("R01 Outer", "cOuter", "line", 40, 0.5, 0, 1), circ("R02 Star", "cStar", "line", 39, -0.8, 0.1, 0.9), circ("R03 Runes", "cRune", "rune", 38, 0.6, 0.2, 0.85),
  { name: "R04 Beam", mat: "pillar", mode: "mesh", mesh: "beam", fudge: 8, size: 1.2, rot3: YAW, rotLife: [0, 1.5, 0], bursts: [[0, 1]], life: 2.4,
    sizeLife: [[0, 0.1, 0, 3], [0.15, 1], [REV / 2.4, 1], [(REV + 0.08) / 2.4, 1.8], [1, 0.4]], alpha: [[0, 0], [0.1, 0.55], [0.55, 0.7], [0.62, 1], [1, 0]] },
  { name: "R05 FallFeathers", mat: "feather", fudge: -1, life: [1.4, 1.8], size: [0.2, 0.3], rot: [0, TAU], rotZ: [-1.5, 1.5], shape: { type: "circle", r: 0.9, th: 1, y: 4 }, bursts: [[0, 6], [0.3, 6], [0.6, 4]],
    vel: { y: [-2.2, -1.6] }, noise: { s: 0.5, f: 0.8, sp: 0.4 }, alpha: [[0, 0], [0.15, 1], [0.8, 0.9], [1, 0]] },
  { name: "R06 DownMotes", mat: "streak", mode: "stretch", vs: 0.06, ls: 2.2, fudge: -3, life: [0.6, 0.9], size: [0.05, 0.08], shape: { type: "circle", r: 0.7, th: 1, y: 6 }, rate: 14,
    vel: { y: [-8, -6] }, alpha: [[0, 0], [0.2, 1], [1, 0.6]] },
  { name: "R07 RiseGlow", mat: "glow", fudge: 2, pos: [0, 0.9, 0], size: 2, bursts: [[0.3, 1]], life: 1.3, sizeLife: [[0, 0.4], [1, 1]], alpha: [[0, 0], [0.8, 0.55], [1, 0]] },
  // 부활 순간
  { name: "R10 Flash", mat: "star", fudge: -8, pos: [0, 1.0, 0], size: 3.5, rot: [0, TAU], bursts: [[REV, 1]], life: 0.25, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: "R11 Flare", mat: "flare", fudge: -7, pos: [0, 1.0, 0], size: 4, rot: [0, TAU], bursts: [[REV, 1]], life: 0.45, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.15]], alpha: [[0, 0.75], [1, 0]] },
  { name: "R12 Wave", mat: "wave", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], size: 5, rot3: YAW, bursts: [[REV, 1], [REV + 0.1, 1]], life: 0.5, sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.7], [1, 0]] },
  { name: "R13 Up", mat: "streak", mode: "stretch", vs: 0.06, ls: 2.6, fudge: -3, life: [0.5, 0.8], size: [0.06, 0.12], shape: { type: "circle", r: 1.0, th: 1, y: 0.1 }, bursts: [[REV, 26]],
    vel: { y: [7, 12] }, drag: 2.5, alpha: [[0, 1], [1, 0]] },
  { name: "R14 Burst", mat: "feather", fudge: -1, pos: [0, 1.0, 0], life: [0.9, 1.3], size: [0.22, 0.34], rot: [0, TAU], rotZ: [-3, 3], bursts: [[REV, 14]], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [3, 5.5], drag: 3, gravity: 0.15, alpha: [[0, 1], [0.7, 0.9], [1, 0]] },
  { name: "R15 Motes", mat: "dot", fudge: -6, life: [1.0, 1.6], size: [0.04, 0.09], shape: { type: "circle", r: 1.2, th: 1, y: 0.1 }, bursts: [[REV, 34]],
    vel: { y: [1, 2.4] }, noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, PALE], [1, AMBER]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
];
const SEIZE = [
  { name: "Z01 InMist", mat: "mist", fudge: 24, pos: [0, 1.2, 0], life: 0.5, size: [0.6, 0.9], rot: [0, TAU], shape: { type: "sphere", r: 1.2, th: 0 }, bursts: [[0, 10]],
    vel: { rad: [-2.4, -2.0] }, sizeLife: [[0, 1], [1, 0.4]], alpha: [[0, 0], [0.3, 0.6], [1, 0.3]], flip: { x: 4, y: 4 } },
  { name: "Z02 Chains", mat: "chain", mode: "stretch", ls: 1.2, vs: 0.02, fudge: 2, pos: [0, 1.2, 0], life: 0.45, size: 0.12, shape: { type: "sphere", r: 1.6, th: 0 }, bursts: [[0.02, 8]],
    vel: { rad: [-3.6, -3.2] }, alpha: [[0, 0], [0.2, 1], [0.9, 1], [1, 0]] },
  { name: "Z03 Heart", mat: "heart", fudge: -6, pos: [0, 1.25, -0.3], size: 0.5, bursts: [[0.35, 1]], life: 0.55, vel: { y: 2.0 },
    sizeLife: [[0, 0.3, 0, 4], [0.25, 1], [0.6, 0.9], [0.8, 1.15], [1, 0.4]], alpha: [[0, 0], [0.15, 1], [0.85, 1], [1, 0]] },
  { name: "Z04 HeartGlow", mat: "hGlow", fudge: 0, pos: [0, 1.25, -0.3], size: 1.1, bursts: [[0.35, 1]], life: 0.55, vel: { y: 2.0 }, alpha: [[0, 0], [0.3, 0.7], [1, 0]] },
  { name: "Z05 Tether", mat: "hStreak", mode: "stretch", vs: 0.05, ls: 1, fudge: 1, pos: [0, 1.25, -0.3], life: 0.5, size: [0.04, 0.07], bursts: [[0.38, 10]], vel: { y: [1.6, 2.0] }, alpha: [[0, 1], [1, 0]] },
  { name: "Z06 Crush", mat: "hFlare", fudge: -7, pos: [0, 2.35, -0.3], size: 1.8, rot: [0, TAU], bursts: [[0.9, 1]], life: 0.35, sizeLife: [[0, 0.3], [0.3, 1], [1, 1.1]], alpha: [[0, 0.85], [1, 0]] },
  { name: "Z07 Shards", mat: "shardDark", fudge: 16, pos: [0, 2.35, -0.3], life: [0.5, 0.8], size: [0.1, 0.18], rot: [0, TAU], rotZ: [-12, 12], bursts: [[0.9, 12]],
    shape: { type: "sphere", r: 0.1, th: 1 }, speed: [2, 4], drag: 1.2, gravity: 1.3, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "Z08 Embers", mat: "hDot", mode: "stretch", vs: 0.05, ls: 1.2, fudge: -6, pos: [0, 2.35, -0.3], life: [0.4, 0.7], size: [0.04, 0.08], bursts: [[0.9, 20]],
    shape: { type: "sphere", r: 0.1, th: 1 }, speed: [3, 6], drag: 3, color: [[0, EMB], [1, DEEP]], alpha: [[0, 1], [1, 0]] },
  { name: "Z09 FootCrack", mat: "hLine", mode: "mesh", mesh: "crack", fudge: 30, size: 1, rot3: YAW, rotLife: [0, -1.5, 0], bursts: [[0.05, 1]], life: 1.4,
    sizeLife: [[0, 1.4, 0, -3], [0.25, 1]], alpha: [[0, 0], [0.2, 1], [0.7, 0.7], [1, 0]] },
  { name: "Z10 FloorCrack", mat: "hCrack", mode: "mesh", mesh: "quad", fudge: 43, pos: [0, 0.025, 0], size: 2.2, rot3: YAW, bursts: [[0.9, 1]], life: 0.8, sizeLife: [[0, 0.5, 0, 3], [0.2, 1]], alpha: [[0, 1], [1, 0]] },
];
const DRAIN = [
  { name: "D01 Wisps", mat: "mist", fudge: 24, life: [1.0, 1.4], size: [0.35, 0.55], rot: [0, TAU], shape: { type: "circle", r: 0.45, th: 1, y: 0.2 }, rate: 7, bursts: [[0, 4]],
    vel: { y: [0.6, 1.0] }, sizeLife: [[0, 0.6], [1, 1.2]], alpha: [[0, 0], [0.3, 0.5], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.3 },
  { name: "D02 Embers", mat: "hDot", fudge: -6, life: [0.8, 1.2], size: [0.03, 0.06], shape: { type: "circle", r: 0.5, th: 1, y: 0.3 }, rate: 10,
    vel: { y: [0.6, 1.2] }, noise: { s: 0.3, f: 1, sp: 0.4 }, color: [[0, EMB], [1, DEEP]], alpha: [[0, 0], [0.15, 1], [1, 0]], onStop: 0.2 },
  { name: "D03 Ring", mat: "hLine", mode: "mesh", mesh: "crack", fudge: 30, size: 0.75, rot3: [0, 0, 0], rotLife: [0, (TAU / 5) / 1.0, 0], rate: 0.5, life: 2, bursts: [[0, 1]],
    alpha: [[0, 0], [0.5, 0.55], [1, 0]], onStop: 0.3 },
  { name: "D04 HeartBeat", mat: "heart", fudge: -6, pos: [0, 2.3, 0], size: 0.22, rate: 1.1, life: 0.6, bursts: [[0, 1]], sizeLife: [[0, 0.7], [0.15, 1.15], [0.35, 0.9], [1, 0.8]],
    alpha: [[0, 0], [0.15, 0.8], [1, 0]], onStop: 0.1 },
];
const MATS = {
  line: { tex: "T_Line", tint: [2.5, 2.0, 1.0], add: true }, rune: { tex: "T_Rune", tint: [2.2, 1.8, 0.85], add: true }, pillar: { tex: "T_PillarHoly", tint: [2.0, 1.8, 1.3], add: true },
  feather: { tex: "T_Feather", tint: [2.0, 1.8, 1.3], add: true }, streak: { tex: "T_Streak", tint: [2.4, 2.0, 1.1], add: true }, glow: { tex: "T_Glow", tint: [1.3, 1.0, 0.45], add: true },
  star: { tex: "T_Star", tint: [2.8, 2.5, 1.6], add: true }, flare: { tex: "T_Flare", tint: [2.2, 1.8, 0.95], add: true }, wave: { tex: "T_Wave", tint: [1.8, 1.5, 0.75], add: true },
  dot: { tex: "T_Dot", tint: [2.3, 2.0, 1.2], add: true },
  mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false }, chain: { tex: "T_Chain", tint: [1.4, 0.6, 0.4], add: false }, heart: { tex: "T_Heart", tint: [2.6, 0.5, 0.3], add: true },
  hGlow: { tex: "T_Glow", tint: [1.6, 0.25, 0.1], add: true }, hStreak: { tex: "T_Streak", tint: [2.2, 0.5, 0.25], add: true }, hFlare: { tex: "T_Flare", tint: [2.4, 0.7, 0.35], add: true },
  shardDark: { tex: "T_ShardDark", tint: [2.2, 0.7, 0.35], add: false }, hDot: { tex: "T_Dot", tint: [2.4, 1.0, 0.5], add: true }, hLine: { tex: "T_Line", tint: [2.4, 0.6, 0.3], add: true },
  hCrack: { tex: "T_Crack", tint: [2.2, 0.5, 0.22], add: true },
};
const SCENE = {
  name: "Elysia_Salvation", bloom: BLOOM_HOLY, tonemapping: "neutral",
  systems: [{ id: "revive", role: "static", loop: false, duration: 1.7, layers: REVIVE }, { id: "seize", role: "attached", loop: false, duration: 1.0, layers: SEIZE },
            { id: "drain", role: "attached", loop: true, duration: 2, layers: DRAIN }],
  demo: Object.assign({}, DEMO_BG, { interval: 9, dummy: { height: 1.8 },
    scenarios: { all: { label: "부활 → 목숨 회수 → 회복 불가", path: [[0, [0, 0, 0], 0]], actors: [{ path: [[0, [0, 0, 0], 0]] }],
      events: [{ t: 0.1, system: "revive", actor: 0 }, { t: 3.6, system: "seize", actor: 0 }, { t: 4.6, system: "drain", actor: 0 }, { t: 7.6, stop: ["drain"] }],
      camera: { target: [0, 1.5, 0], dist: 7.5, yaw: -70, pitch: 14 } } } }),
};
