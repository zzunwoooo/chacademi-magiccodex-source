// Belzar_Collection — 수금(장부 내리찍기). static, P = 내리찍는 지점(지면), 반경 R = 3 m. 판정 = 0 s (벨자르 collect 모션 1.15 s 지점에 맞춰 모드가 시작).
const R = 3;
var EXTRA_MESHES = {
  cOuter: merge([ringM(1, 0.035, 0.03), ringM(0.95, 0.012, 0.03), ticks(40, 0.96, 1.04, 0.01, 0.03)]),
  cStar: merge([polygram(6, 2, 0.92, 0.016, 0.035), vtxCircles(6, 0.92, 0.06, 0.012, 0.035), ringM(0.6, 0.02, 0.04)]),
  cRune: runeArcs(12, 0.78, 0.12, 0.032),
};
const st = (nm, mat, mesh, f, w, a) => ({ name: nm, mat, mode: "mesh", mesh, fudge: f, size: R, rot3: YAW, rotLife: [0, w, 0], bursts: [[0, 1]], life: 1.6,
  sizeLife: [[0, 1.35, 0, -5], [0.1, 1]], alpha: [[0, 1], [0.12, a], [0.6, a * 0.75], [1, 0]] });
const L = [
  st("S01 Outer", "hLine", "cOuter", 40, 0.4, 1), st("S02 Star", "hLine", "cStar", 39, -0.7, 0.9), st("S03 Rune", "hRune", "cRune", 38, 0.6, 0.85),
  { name: "S04 Sigil", mat: "hSigil", mode: "mesh", mesh: "quad", fudge: 37, pos: [0, 0.05, 0], size: R * 1.15, rot3: YAW, bursts: [[0, 1]], life: 1.5, sizeLife: [[0, 1.4, 0, -5], [0.1, 1]], alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
  { name: "S05 Crack", mat: "hCrack", mode: "mesh", mesh: "quad", fudge: 43, pos: [0, 0.025, 0], size: R * 2, rot3: YAW, bursts: [[0, 1]], life: 1.8, sizeLife: [[0, 0.3, 0, 5], [0.1, 1]], alpha: [[0, 1], [0.6, 0.7], [1, 0]] },
  { name: "I01 Flash", mat: "hStar", fudge: -8, pos: [0, 0.5, 0], size: 4.5, rot: [0, TAU], bursts: [[0, 1]], life: 0.22, sizeLife: [[0, 0.3], [0.25, 1], [1, 0]] },
  { name: "I02 Flare", mat: "hFlare", fudge: -7, pos: [0, 0.6, 0], size: 5.5, rot: [0, TAU], bursts: [[0, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.15]], alpha: [[0, 0.8], [1, 0]] },
  { name: "I03 Wave1", mat: "hWave", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], size: R * 2.7, rot3: YAW, bursts: [[0, 1]], life: 0.45, sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.85], [1, 0]] },
  { name: "I04 Wave2", mat: "hWave", mode: "mesh", mesh: "quad", fudge: 31, pos: [0, 0.08, 0], size: R * 2.2, rot3: YAW, bursts: [[0.1, 1]], life: 0.45, sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.6], [1, 0]] },
  { name: "I05 RingSharp", mat: "hLine", mode: "mesh", mesh: "cOuter", fudge: 29, size: R, rot3: YAW, bursts: [[0.03, 1]], life: 0.35, sizeLife: [[0, 0.2, 0, 3.5], [1, 1.3]], alpha: [[0, 1], [1, 0]] },
  { name: "I06 Pages", mat: "page", fudge: 13, pos: [0, 0.4, 0], life: [1.2, 1.8], size: [0.24, 0.36], rot: [0, TAU], rotZ: [-6, 6], shape: { type: "circle", r: 0.5, th: 1, y: 0 }, bursts: [[0, 22]],
    vel: { rad: [3, 6], y: [3, 6] }, drag: 1.6, gravity: 0.35, noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "I07 Chains", mat: "chain", mode: "stretch", ls: 1.4, vs: 0.05, fudge: 2, pos: [0, 0.3, 0], life: [0.3, 0.45], size: [0.14, 0.2], shape: { type: "circle", r: 0.3, th: 1, y: 0 }, bursts: [[0, 10]],
    vel: { rad: [10, 14] }, drag: 5, alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "I08 Mist", mat: "mist", fudge: 25, life: [0.9, 1.3], size: [1.2, 2.0], rot: [0, TAU], shape: { type: "circle", r: R * 0.5, th: 1, y: 0.3 }, bursts: [[0.02, 12]],
    vel: { rad: [2, 3.5], y: [0.2, 0.6] }, drag: 2, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
  { name: "I09 Flames", mat: "hFlame", fudge: 3, life: [0.4, 0.7], size: [0.7, 1.2], rot: [0, TAU], shape: { type: "circle", r: R * 0.9, th: 0.4, y: 0.3 }, bursts: [[0.02, 20]],
    vel: { y: [1.5, 3] }, sizeLife: [[0, 0.5, 0, 3], [1, 1.1]], flip: { x: 4, y: 4 } },
  { name: "I10 Sparks", mat: "hDot", mode: "stretch", vs: 0.05, ls: 1.3, fudge: -6, pos: [0, 0.3, 0], life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: "sphere", r: 0.3, th: 1 }, bursts: [[0, 44]],
    speed: [5, 11], drag: 2.4, gravity: 0.6, color: [[0, EMB], [0.5, CRIM], [1, DEEP]], alpha: [[0, 1], [1, 0]] },
  { name: "I11 Debris", mat: "shardDark", fudge: 17, pos: [0, 0.3, 0], life: [0.6, 0.9], size: [0.15, 0.3], rot: [0, TAU], rotZ: [-10, 10], shape: { type: "sphere", r: 0.3, th: 1 }, bursts: [[0, 14]],
    speed: [4, 8], drag: 1, gravity: 1.5, alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "I12 UpRays", mat: "hStreak", mode: "stretch", vs: 0.06, ls: 3, fudge: -2, life: [0.35, 0.55], size: [0.06, 0.12], shape: { type: "circle", r: R * 0.8, th: 1, y: 0.1 }, bursts: [[0, 16]],
    vel: { y: [8, 13] }, drag: 2.5, alpha: [[0, 1], [1, 0]] },
  { name: "I13 Embers", mat: "hDot", fudge: -7, life: [1.0, 1.6], size: [0.04, 0.07], shape: { type: "circle", r: R * 0.8, th: 1, y: 0.2 }, bursts: [[0.1, 30]],
    vel: { y: [0.8, 1.8] }, noise: { s: 0.7, f: 1, sp: 0.5 }, color: [[0, EMB], [1, DEEP]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
];
const MATS = {
  hLine: { tex: "T_Line", tint: [2.6, 0.75, 0.32], add: true }, hRune: { tex: "T_Rune", tint: [2.4, 0.65, 0.28], add: true }, hSigil: { tex: "T_SigilHell", tint: [2.4, 0.6, 0.26], add: true },
  hCrack: { tex: "T_Crack", tint: [2.4, 0.55, 0.22], add: true }, hStar: { tex: "T_Star", tint: [2.8, 1.3, 0.7], add: true }, hFlare: { tex: "T_Flare", tint: [2.4, 0.8, 0.35], add: true },
  hWave: { tex: "T_Wave", tint: [1.9, 0.5, 0.22], add: true }, page: { tex: "T_Page", tint: [1, 1, 1], add: false }, chain: { tex: "T_Chain", tint: [1.6, 0.7, 0.45], add: false },
  mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false }, hFlame: { tex: "T_HellFlame_Sheet", tint: [1.7, 1.2, 1.0], add: true }, hDot: { tex: "T_Dot", tint: [2.4, 1.1, 0.55], add: true },
  shardDark: { tex: "T_ShardDark", tint: [2.2, 0.8, 0.4], add: false }, hStreak: { tex: "T_Streak", tint: [2.6, 0.9, 0.4], add: true },
};
const SCENE = {
  name: "Belzar_Collection", bloom: BLOOM_HELL, tonemapping: "neutral",
  scale: { input: "areaRadius", reference: R, min: 0.5, max: 3 },
  systems: [{ id: "collect", role: "static", loop: false, duration: 0.2, layers: L }],
  demo: Object.assign({}, DEMO_BG, { interval: 2.8,
    scenarios: { center: { label: "수금", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0], targets: [[1, 0, 0.5, 200]],
      camera: { target: [0, 1.2, 0], dist: 11, yaw: -90, pitch: 26 } } } }),
};
