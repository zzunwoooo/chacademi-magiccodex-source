// PR_GhostDash — 7. 유령 돌진 (가장 먼 적 방향 직선 14블록, 폭 2.6, 예고 0.6초). 지나간 자리에 청록 저주 장판 10초.
// systems: vanish(사라짐) · appear(경기장 끝에서 나타남) · (예고는 모드: 직선 14×2.6, 0.6초) · dash(attached, 보스 따라감) · curse(장판 하나, 서버가 경로를 따라 2블록마다)
const LEN = 14, WD = 2.6, T = 0.6, CURSE = 10;
var EXTRA_MESHES = MC_MESHES;
const VANISH = [
  { name: "V1 Implode", mat: "mist", fudge: 35, pos: [0, 1.4, 0], life: [0.5, 0.7], size: [1.2, 2.0], shape: { type: "sphere", r: 1.6, th: 0.3 }, bursts: [[0, 14]],
    vel: { rad: [-3.5, -2.5] }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.6], [1, 0]], sizeLife: [[0, 1.2], [1, 0.4]], flip: F44 },
  { name: "V2 Ghosts", mat: "ghost", fudge: 5, pos: [0, 1.2, 0], life: [0.6, 1.0], size: [0.6, 1.0], shape: { type: "sphere", r: 0.6, th: 1 }, speed: [2, 4], drag: 2,
    vel: { y: [1.5, 3], space: "world" }, bursts: [[0.2, 6]], alpha: [[0, 0], [0.15, 0.9], [1, 0]] },
  ...fxBats("V3", [0, 1.6, 0], 0.25, 9, 0.8), ...fxFlash("V4", [0, 1.5, 0], 0.3, 2.2, "starVio"),
  { name: "V5 Ring", mat: "ringVio", mode: "mesh", mesh: "quad", fudge: 40, pos: [0, 0.05, 0], life: 0.6, size: 3.5, bursts: [[0, 1]], sizeLife: [[0, 1.2], [1, 0.2]], alpha: [[0, 0], [0.3, 1], [1, 0]] },
];
const APPEAR = [
  { name: "A1 Gather", mat: "mist", fudge: 35, pos: [0, 1.2, 0], life: [0.4, 0.6], size: [1.0, 1.8], shape: { type: "sphere", r: 1.8, th: 0.3 }, bursts: [[0, 12]],
    vel: { rad: [-4, -3] }, rot: [0, TAU], alpha: [[0, 0], [0.3, 0.6], [1, 0]], sizeLife: [[0, 1.2], [1, 0.5]], flip: F44 },
  { name: "A2 Column", mat: "pillarSoul", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.5, size: 4, bursts: [[0.25, 1]], rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.2, 0, 4], [0.3, 1]], alpha: [[0, 0], [0.15, 1], [1, 0]] },
  ...fxFlash("A3", [0, 1.6, 0], 0.28, 2.6, "starVio"), ...fxShock("A4", [0, 0, 0], 0.3, 4, "shockVio"), ...fxMotes("A5", [0, 0.2, 0], 0.3, 18, 1.2, "dotVio"),
];
const DASH = [
  { name: "D1 Streaks", mat: "streakVio", mode: "stretch", vs: 0.05, ls: 3, fudge: -3, world: true, rateDist: 14, life: [0.25, 0.45], size: [0.1, 0.2], shape: { type: "box", sy: 1.2, sx: 0.8, sz: 0.3 }, off: [0, 1.4, 0],
    speed: [0.2, 0.6], alpha: [[0, 0.9], [1, 0]] },
  { name: "D2 Afterimage", mat: "ghost", fudge: 6, world: true, rateDist: 0.55, life: 0.35, size: 2.2, pos: [0, 1.6, 0], alpha: [[0, 0.35], [1, 0]], sizeLife: [[0, 1], [1, 1.2]] },
  { name: "D3 Mist", mat: "mist", fudge: 35, world: true, rateDist: 3, life: [0.6, 0.9], size: [1.2, 2.0], shape: { type: "sphere", r: 0.6, th: 1 }, off: [0, 1.0, 0], rot: [0, TAU],
    alpha: [[0, 0], [0.2, 0.55], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
  { name: "D4 SoulFlames", mat: "soulFlame", fudge: 0, world: true, rateDist: 6, life: [0.3, 0.5], size: [0.4, 0.8], shape: { type: "box", sy: 0.8, sx: 0.8, sz: 0.3 }, off: [0, 0.8, 0], rot: [0, TAU],
    vel: { y: [0.6, 1.4] }, flip: F44 },
  { name: "D5 Motes", mat: "dotSoul", fudge: -7, world: true, rateDist: 8, life: [0.6, 1.0], size: [0.04, 0.1], shape: { type: "sphere", r: 1, th: 1 }, off: [0, 1.2, 0], vel: { y: [0.4, 1] }, alpha: [[0, 1], [1, 0]] },
];
const CURSE_L = [
  { name: "C1 Pool", mat: "sigilSoul", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.05, 0], life: CURSE, size: 2.2, rot3: [0, [0, TAU], 0], rotLife: [0, 0.4, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.5, 0, 3], [0.03, 1], [0.95, 1], [1, 0.8]], alpha: [[0, 0], [0.02, 0.9], [0.92, 0.75], [1, 0]] },
  { name: "C2 Glow", mat: "warnFill", mode: "mesh", mesh: "quad", fudge: 60, pos: [0, 0.03, 0], life: CURSE, size: 2.4, bursts: [[0, 1]], alpha: [[0, 0], [0.02, 0.6], [0.92, 0.5], [1, 0]] },
  { name: "C3 Flames", mat: "soulFlame", fudge: 0, life: [0.5, 0.9], size: [0.3, 0.6], rate: 7, shape: { type: "circle", r: 0.9, th: 0.8, y: 0.1 }, vel: { y: [0.6, 1.2], space: "world" }, rot: [0, TAU], flip: F44 },
  { name: "C4 Motes", mat: "dotSoul", fudge: -7, life: [1, 1.6], size: [0.04, 0.08], rate: 6, shape: { type: "circle", r: 1, th: 1, y: 0.05 }, vel: { y: [0.4, 0.9], space: "world" }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_GhostDash", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "vanish", role: "static", loop: false, duration: 1.5, layers: VANISH },
    { id: "appear", role: "static", loop: false, duration: 1.5, layers: APPEAR },
    { id: "dash", role: "attached", loop: false, duration: 0.6, layers: DASH },
    { id: "curse", role: "static", loop: false, duration: CURSE - 1.5, layers: CURSE_L },
  ],
  demo: Object.assign({ interval: 4.0, dummy: { height: 3.3 } }, DEMO_BG, { scenarios: {
    base: { label: "기본", path: [[0, [0, 0, -3], 0], [0.9, [0, 0, -3], 0], [1.2, [0, 0, -3], 0], [1.6, [0, 0, 11], 0]], hits: [], anchor: [0, 0, -3],
      place: { vanish: { at: [-6, 0, -1], start: 0 }, appear: { at: [0, 0, -3], start: 0.5 }, dash: { start: 1.2 }, curse: { at: [0, 0, 3], start: 1.45 } },
      targets: [[0, 0, 11, 180]], camera: cam([0, 1, 4], 20, -40, 35) } } }),
};
