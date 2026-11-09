// Belzar_ContractBurn — 15% 계약 파기(최종).
//  seal (static, P = 봉인진 중심): 봉인진 하나에 3초간 서 있을 때 켜짐 — 금·적 빛기둥이 솟고 장부 쪽으로 빛줄기 (약 1.6 s, 3번 각각 재생)
//  burn (static, P = 하늘의 장부 중심): 장부가 불타고, 진짜 엘리시아의 빛이 내려와 가면을 찢음 → 보스 처치 (약 4.5 s)
const TP = 2.2;   // 불길 → 정화 전환 시점
var EXTRA_MESHES = {
  sOuter: merge([ringM(1, 0.04, 0.03), ringM(0.93, 0.012, 0.03), ticks(24, 0.94, 1.05, 0.012, 0.03), polygram(4, 1, 0.9, 0.014, 0.035, Math.PI / 4)]),
  sRune: runeArcs(8, 0.75, 0.14, 0.032),
  beam: merge([{ verts: [[-0.5, 0, 0], [0.5, 0, 0], [-0.5, 10, 0], [0.5, 10, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] },
               { verts: [[0, 0, -0.5], [0, 0, 0.5], [0, 10, -0.5], [0, 10, 0.5]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]),
  skyBeam: merge([{ verts: [[-0.5, -2, 0], [0.5, -2, 0], [-0.5, 14, 0], [0.5, 14, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] },
                  { verts: [[0, -2, -0.5], [0, -2, 0.5], [0, 14, -0.5], [0, 14, 0.5]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]),
  ringV: ribbon(64, s => { const p = s * TAU, d = [Math.cos(p), Math.sin(p), 0]; return [mul(d, 0.96), mul(d, 1.04)]; }),
  bigCircle: merge([ringM(1, 0.025, 0), ringM(0.95, 0.01, 0), polygram(8, 3, 0.93, 0.012, 0), vtxCircles(8, 0.93, 0.05, 0.01, 0), ringM(0.6, 0.015, 0), polygram(8, 2, 0.57, 0.01, 0, Math.PI / 8)]),
  bigRune: runeArcs(16, 0.78, 0.1, 0),
};
const SEAL = [
  { name: "S01 Ring", mat: "line", mode: "mesh", mesh: "sOuter", fudge: 40, size: 1.5, rot3: YAW, rotLife: [0, 1.2, 0], bursts: [[0, 1]], life: 1.6, sizeLife: [[0, 1.4, 0, -4], [0.15, 1]], alpha: [[0, 0], [0.08, 1], [0.7, 0.8], [1, 0]] },
  { name: "S02 Rune", mat: "rune", mode: "mesh", mesh: "sRune", fudge: 39, size: 1.5, rot3: YAW, rotLife: [0, -1, 0], bursts: [[0.05, 1]], life: 1.55, sizeLife: [[0, 1.4, 0, -4], [0.15, 1]], alpha: [[0, 0], [0.08, 0.9], [0.7, 0.7], [1, 0]] },
  { name: "S03 Beam", mat: "pillar", mode: "mesh", mesh: "beam", fudge: 8, size: 0.8, rot3: YAW, rotLife: [0, 2, 0], bursts: [[0.05, 1]], life: 1.4, sizeLife: [[0, 0.2, 0, 5], [0.15, 1], [1, 0.6]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  { name: "S04 BeamRed", mat: "hPillar", mode: "mesh", mesh: "beam", fudge: 9, size: 1.3, rot3: YAW, rotLife: [0, -1.5, 0], bursts: [[0.1, 1]], life: 1.2, sizeLife: [[0, 0.3, 0, 4], [0.2, 1], [1, 0.8]], alpha: [[0, 0.6], [1, 0]] },
  { name: "S05 Flash", mat: "star", fudge: -8, pos: [0, 0.5, 0], size: 2.5, rot: [0, TAU], bursts: [[0.05, 1]], life: 0.22, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  { name: "S06 Up", mat: "streak", mode: "stretch", vs: 0.06, ls: 3, fudge: -3, life: [0.5, 0.8], size: [0.06, 0.12], shape: { type: "circle", r: 1.2, th: 1, y: 0.1 }, bursts: [[0.05, 22]],
    vel: { y: [8, 13] }, drag: 1.5, alpha: [[0, 1], [1, 0]] },
  { name: "S07 Embers", mat: "hDot", fudge: -6, life: [0.9, 1.4], size: [0.04, 0.08], shape: { type: "circle", r: 1.3, th: 1, y: 0.1 }, bursts: [[0.08, 24]], vel: { y: [1.5, 3] },
    noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, EMB], [1, CRIM]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  { name: "S08 Motes", mat: "dot", fudge: -6, life: [0.9, 1.4], size: [0.04, 0.08], shape: { type: "circle", r: 1.3, th: 1, y: 0.1 }, bursts: [[0.08, 24]], vel: { y: [1.5, 3] },
    noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, PALE], [1, GOLD]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
];
const BURN = [
  // 장부가 불탐 (0 ~ TP)
  { name: "B01 Fire", mat: "hFlame", fudge: 3, life: [0.5, 0.8], size: [0.9, 1.5], rot: [0, TAU], shape: { type: "box", sx: 3.2, sy: 0.3, sz: 2.2 },
    bursts: [[0, 6], [0.4, 10], [0.8, 14], [1.2, 18], [1.6, 22]], vel: { y: [1.5, 3], space: "world" }, sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: { x: 4, y: 4 } },
  { name: "B02 Pages", mat: "page", fudge: 13, life: [1.4, 2.0], size: [0.24, 0.36], rot: [0, TAU], rotZ: [-5, 5], shape: { type: "box", sx: 3, sy: 0.2, sz: 2 },
    bursts: [[0.3, 4], [0.8, 6], [1.3, 8], [1.8, 10]], vel: { y: [0.8, 1.6], space: "world" }, noise: { s: 0.7, f: 0.8, sp: 0.5 }, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "B03 Smoke", mat: "mist", fudge: 25, life: [1.4, 1.8], size: [1.4, 2.2], rot: [0, TAU], shape: { type: "box", sx: 3, sy: 0.3, sz: 2 }, bursts: [[0.2, 3], [0.8, 4], [1.4, 6]],
    vel: { y: [0.6, 1.2], space: "world" }, sizeLife: [[0, 0.6], [1, 1.4]], alpha: [[0, 0], [0.2, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
  { name: "B04 Embers", mat: "hDot", fudge: -6, life: [1.0, 1.6], size: [0.04, 0.08], shape: { type: "box", sx: 3.2, sy: 0.3, sz: 2.2 }, rate: 40, vel: { y: [1, 2.4], space: "world" },
    noise: { s: 0.7, f: 1, sp: 0.6 }, color: [[0, EMB], [0.6, CRIM], [1, DEEP]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  // 하늘에서 진짜 엘리시아의 빛
  { name: "C01 SkyCircle", mat: "line", mode: "mesh", mesh: "bigCircle", fudge: 6, pos: [0, 3.2, 0], size: 4, rot3: YAW, rotLife: [0, 0.4, 0], bursts: [[1.2, 1]], life: 3.3,
    sizeLife: [[0, 0.6, 0, 1.5], [0.3, 1]], alpha: [[0, 0], [0.25, 0.9], [0.32, 1], [0.8, 0.8], [1, 0]] },
  { name: "C02 SkyRune", mat: "rune", mode: "mesh", mesh: "bigRune", fudge: 5, pos: [0, 3.2, 0], size: 4, rot3: YAW, rotLife: [0, -0.5, 0], bursts: [[1.3, 1]], life: 3.2,
    sizeLife: [[0, 0.6, 0, 1.5], [0.3, 1]], alpha: [[0, 0], [0.25, 0.85], [0.8, 0.7], [1, 0]] },
  { name: "C03 Beam", mat: "pillar", mode: "mesh", mesh: "skyBeam", fudge: 7, size: 1, rot3: YAW, rotLife: [0, 2, 0], bursts: [[TP, 1]], life: 1.6,
    sizeLife: [[0, 4.5, 0, -6], [0.2, 2.6], [1, 1.2]], alpha: [[0, 1], [0.5, 0.8], [1, 0]] },
  { name: "C04 Flash", mat: "star", fudge: -8, size: 7, rot: [0, TAU], bursts: [[TP, 1]], life: 0.28, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: "C05 Flare", mat: "flare", fudge: -7, size: 9, rot: [0, TAU], rotZ: [-0.4, 0.4], bursts: [[TP, 1]], life: 0.6, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.2]], alpha: [[0, 0.8], [1, 0]] },
  { name: "C06 Rings", mat: "line", mode: "mesh", mesh: "ringV", fudge: -2, size: 3, rot3: [[0, TAU], [0, TAU], 0], bursts: [[TP, 2], [TP + 0.12, 2]], life: 0.5, sizeLife: [[0, 0.2, 0, 3], [1, 1.3]], alpha: [[0, 1], [1, 0]] },
  { name: "C07 Wave", mat: "wave", fudge: -3, size: 10, bursts: [[TP, 1], [TP + 0.15, 1]], life: 0.6, sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.7], [1, 0]] },
  { name: "C08 BurnOut", mat: "hFlare", fudge: -6, size: 5, rot: [0, TAU], bursts: [[TP + 0.05, 1]], life: 0.5, sizeLife: [[0, 0.5], [1, 1.3]], alpha: [[0, 0.6], [1, 0]] },
  { name: "C09 Feathers", mat: "feather", fudge: -1, life: [1.6, 2.4], size: [0.28, 0.42], rot: [0, TAU], rotZ: [-2, 2], shape: { type: "sphere", r: 0.6, th: 1 }, bursts: [[TP, 30]],
    speed: [4, 8], drag: 2.2, gravity: 0.1, noise: { s: 0.6, f: 0.7, sp: 0.4 }, alpha: [[0, 1], [0.8, 0.9], [1, 0]] },
  { name: "C10 Shards", mat: "shardDark", fudge: 17, life: [0.8, 1.2], size: [0.25, 0.45], rot: [0, TAU], rotZ: [-10, 10], shape: { type: "sphere", r: 0.6, th: 1 }, bursts: [[TP, 18]],
    speed: [6, 10], drag: 1, gravity: 1.2, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "C11 Sparks", mat: "dot", mode: "stretch", vs: 0.05, ls: 1.4, fudge: -6, life: [0.7, 1.1], size: [0.05, 0.1], shape: { type: "sphere", r: 0.5, th: 1 }, bursts: [[TP, 70]],
    speed: [7, 14], drag: 2.2, gravity: 0.4, color: [[0, PALE], [0.5, GOLD], [1, AMBER]], alpha: [[0, 1], [1, 0]] },
  { name: "C12 Rain", mat: "dot", fudge: -6, life: [1.6, 2.4], size: [0.04, 0.09], shape: { type: "circle", r: 5, th: 1, y: 4 }, rate: 0, bursts: [[TP + 0.3, 30], [TP + 0.8, 30], [TP + 1.3, 20]],
    vel: { y: [-1.2, -0.7] }, noise: { s: 0.4, f: 0.8, sp: 0.4 }, color: [[0, PALE], [1, GOLD]], alpha: [[0, 0], [0.15, 1], [1, 0]] },
  { name: "C13 SoftGlow", mat: "glow", fudge: 2, size: 3.5, bursts: [[TP, 1]], life: 1.4, sizeLife: [[0, 0.5], [0.2, 1], [1, 1.2]], alpha: [[0, 0.22], [1, 0]] },
];
const MATS = {
  line: { tex: "T_Line", tint: [2.6, 2.1, 1.05], add: true }, rune: { tex: "T_Rune", tint: [2.3, 1.85, 0.9], add: true }, pillar: { tex: "T_PillarHoly", tint: [2.2, 2.0, 1.6], add: true },
  hPillar: { tex: "T_PillarHell", tint: [1.8, 1.2, 1.0], add: true }, star: { tex: "T_Star", tint: [2.8, 2.5, 1.7], add: true }, streak: { tex: "T_Streak", tint: [2.5, 2.1, 1.1], add: true },
  hDot: { tex: "T_Dot", tint: [2.4, 1.1, 0.55], add: true }, dot: { tex: "T_Dot", tint: [2.4, 2.1, 1.3], add: true }, hFlame: { tex: "T_HellFlame_Sheet", tint: [1.7, 1.2, 1.0], add: true },
  page: { tex: "T_Page", tint: [1, 1, 1], add: false }, mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false }, flare: { tex: "T_Flare", tint: [2.3, 1.9, 1.0], add: true },
  wave: { tex: "T_Wave", tint: [1.9, 1.6, 0.85], add: true }, hFlare: { tex: "T_Flare", tint: [2.2, 0.7, 0.3], add: true }, feather: { tex: "T_Feather", tint: [2.0, 1.85, 1.4], add: true },
  shardDark: { tex: "T_ShardDark", tint: [2.0, 1.0, 0.5], add: false }, glow: { tex: "T_Glow", tint: [1.3, 1.05, 0.5], add: true },
};
const SCENE = {
  name: "Belzar_ContractBurn", bloom: BLOOM_HOLY, tonemapping: "neutral",
  systems: [{ id: "seal", role: "static", loop: false, duration: 0.2, layers: SEAL }, { id: "burn", role: "static", loop: false, duration: 3.6, layers: BURN }],
  demo: Object.assign({}, DEMO_BG, { interval: 9,
    scenarios: { all: { label: "봉인진 ×3 → 장부 불태움", path: [[0, [0, 0, 0], 0]],
      actors: [{ path: [[0, [-3, 0, 2], 0]], hidden: true }, { path: [[0, [3, 0, 2], 0]], hidden: true }, { path: [[0, [0, 0, -3], 0]], hidden: true }, { path: [[0, [0, 5, 0], 0]], hidden: true }],
      events: [{ t: 0.2, system: "seal", actor: 0 }, { t: 1.0, system: "seal", actor: 1 }, { t: 1.8, system: "seal", actor: 2 }, { t: 2.6, system: "burn", actor: 3 }],
      camera: { target: [0, 4.5, 0], dist: 17, yaw: -90, pitch: 8 } } } }),
};
