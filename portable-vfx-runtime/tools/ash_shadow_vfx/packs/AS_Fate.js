// AS_Fate — 등장·처치·50% 귀환. systems: intro(static 보스 r 6, 3초: 재가 솟아 형체, 촛불 고리 점화) · death(static r 6, 3초: 재로 흩어짐, 촛불 꺼짐)
//   vanish(static r 3, 0.8초: 재로 무너져 사라짐) · appear(static r 3, 0.8초: 경기장 중앙에 재가 모여 나타남)
const R = 6, HEAD = 6.2;
var EXTRA_MESHES = Object.assign({
  haloV: grid(96, 1, (u, v) => { const p = u * TAU, rr = 1 - 0.04 + v * 0.08; return [Math.cos(p) * rr, Math.sin(p) * rr, 0]; }),
}, MC_MESHES);
const INTRO = [
  ...magicCircle("B0", R, [0, 0, 0], 0, 3.0, "Ora", 2.2),
  { name: "B1 Scorch", mat: "crack", mode: "mesh", mesh: "quad", fudge: 44, pos: [0, 0.04, 0], life: 3.0, size: 8, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.3, 0, 2], [0.3, 1]], alpha: [[0, 0], [0.15, 0.8], [0.85, 0.7], [1, 0]] },
  // build-up: ash rises in growing waves and gathers into a column (the body forms)
  { name: "M1 AshRise", mat: "mist", fudge: 35, life: [1.0, 1.4], size: [1.6, 2.8], shape: { type: "circle", r: 3.5, th: 0.4, y: 0.2 },
    bursts: [[0, 6], [0.5, 8], [1.0, 10], [1.5, 12]], vel: { rad: [-2.2, -1.2], y: [2.5, 4.5], orb: [1, 2] }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.7], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
  { name: "M2 FlakeSpiral", mat: "flake", fudge: 12, life: [1.0, 1.6], size: [0.18, 0.36], shape: { type: "circle", r: 4, th: 0.3, y: 0.2 },
    bursts: [[0, 20], [0.4, 30], [0.8, 40], [1.2, 50], [1.6, 60]], vel: { orb: [4, 6], rad: [-2, -1.2], y: [3, 5] }, rot: [0, TAU], rotZ: [-4, 4],
    alpha: [[0, 0], [0.1, 1], [0.8, 0.9], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "M2b EmberSpiral", mat: "streakOra", mode: "stretch", vs: 0.06, ls: 2, fudge: -5, life: [0.6, 0.9], size: [0.06, 0.11], shape: { type: "circle", r: 3.6, th: 0.2, y: 0.2 },
    bursts: [[0.2, 16], [0.6, 24], [1.0, 32], [1.4, 40], [1.8, 50]], vel: { orb: [5, 7], rad: [-2.5, -1.5], y: [3, 6] }, alpha: [[0, 0], [0.15, 1], [1, 0]] },
  { name: "M2c HotFlakes", mat: "flakeHot", fudge: 11, life: [0.9, 1.4], size: [0.14, 0.26], shape: { type: "circle", r: 3.4, th: 0.4, y: 0.2 },
    bursts: [[0.3, 10], [0.8, 16], [1.3, 22], [1.8, 28]], vel: { orb: [3, 5], rad: [-1.8, -1], y: [3, 5] }, rot: [0, TAU], rotZ: [-4, 4], alpha: [[0, 0], [0.15, 1], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "M3 Column", mat: "mist", fudge: 33, pos: [0, 0.3, 0], life: [1.2, 1.6], size: [2.2, 3.2], shape: { type: "sphere", r: 0.8, th: 0.6 }, bursts: [[1.6, 10]],
    vel: { y: [3, 5], space: "world" }, drag: 1.2, rot: [0, TAU], alpha: [[0, 0], [0.15, 0.8], [1, 0]], sizeLife: [[0, 0.5], [1, 1.4]], flip: F44 },
  // climax 2.2: the candle halo ignites behind the head
  { name: "M4 Halo", mat: "lineOra", mode: "mesh", mesh: "haloV", fudge: 4, pos: [0, HEAD, 0.9], life: 0.8, size: 1.6, rotLife: [0, 0, 1.5], bursts: [[2.2, 1]],
    sizeLife: [[0, 0.2, 0, 4], [0.25, 1.3], [0.5, 1.15], [1, 1.1]], alpha: [[0, 0], [0.1, 1], [0.7, 0.9], [1, 0]] },
  { name: "M5 HaloFire", mat: "candle", fudge: 3, pos: [0, HEAD, 0.9], life: [0.4, 0.7], size: [0.5, 0.9], shape: { type: "sphere", r: 1.8, th: 0.05 }, bursts: [[2.2, 30]],
    vel: { y: [1, 2], space: "world" }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  ...fxShockE("M6", [0, 0, 0], 2.2, 2 * R + 2, 0.6, 0.6), ...fxShockE("M6b", [0, 0, 0], 2.36, 2 * R * 0.7, 0.5, 0.5),
  ...fxPillars("M7", [0, 0, 0], 2.2, 10, R * 0.85, 5, "pillarOra"),
  ...fxFlashE("T1", [0, HEAD, 0.9], 2.2, 3.6),
  ...fxRays("T2", [0, HEAD, 0.9], 2.22, 20, [10, 15], "streakOra", 4.5),
  ...fxSparks("T3", [0, 3, 0], 2.2, 44, [6, 12]),
  ...fxEmbers("T4", [0, 0.5, 0], 0.3, 60, 4),
];
const DEATH = [
  { name: "B1 ScorchFade", mat: "crack", mode: "mesh", mesh: "quad", fudge: 44, pos: [0, 0.04, 0], life: 3.0, size: 7, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    alpha: [[0, 0], [0.1, 0.8], [0.6, 0.5], [1, 0]] },
  // the body crumbles: big ash release upward + dark smoke, chest light dies
  { name: "M1 Crumble", mat: "flake", fudge: 12, pos: [0, 3.2, 0], life: [1.6, 2.6], size: [0.22, 0.42], shape: { type: "sphere", r: 1.8, th: 0.8 },
    bursts: [[0, 50], [0.4, 60], [0.9, 50]], vel: { y: [0.8, 2.4], space: "world" }, noise: { s: 1, f: 1, sp: 0.8 }, rot: [0, TAU], rotZ: [-3, 3],
    alpha: [[0, 0], [0.08, 1], [0.8, 0.8], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "M1b HotCrumble", mat: "flakeHot", fudge: 11, pos: [0, 3.2, 0], life: [1.0, 1.8], size: [0.16, 0.3], shape: { type: "sphere", r: 1.8, th: 0.8 },
    bursts: [[0, 40], [0.5, 40]], vel: { y: [1, 2.6], space: "world" }, noise: { s: 1, f: 1, sp: 0.8 }, rot: [0, TAU], rotZ: [-3, 3], alpha: [[0, 0], [0.08, 1], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "M2 Smoke", mat: "mist", fudge: 35, pos: [0, 2.6, 0], life: [1.6, 2.4], size: [2, 3.4], shape: { type: "sphere", r: 1.4, th: 0.6 }, bursts: [[0.1, 10], [0.6, 10], [1.1, 8]],
    vel: { y: [0.8, 1.8], rad: [0.4, 1.2], space: "world" }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.65], [1, 0]], sizeLife: [[0, 0.6], [1, 1.5]], flip: F44 },
  { name: "M3 HeartDie", mat: "glowEmber", fudge: 2, pos: [0, 3.6, -0.6], life: 1.2, size: 3, bursts: [[0, 1]], sizeLife: [[0, 1.2], [1, 0.1]], alpha: [[0, 0.8], [0.6, 0.5], [1, 0]] },
  // candles snuff out one by one (small smoke curls on the halo)
  ...Array.from({ length: 7 }, (_, k) => { const a = (20 + k * 23.3) * Math.PI / 180; return { name: `M4 Snuff${k}`, mat: "mist", fudge: 34,
    pos: [Math.cos(a) * 1.9, HEAD + Math.sin(a) * 1.9, 0.9], life: [0.9, 1.3], size: [0.5, 0.8], bursts: [[0.3 + k * 0.15, 3]], vel: { y: [0.8, 1.4], space: "world" },
    rot: [0, TAU], alpha: [[0, 0], [0.2, 0.6], [1, 0]], sizeLife: [[0, 0.5], [1, 1.5]], flip: F44 }; }),
  ...Array.from({ length: 7 }, (_, k) => { const a = (20 + k * 23.3) * Math.PI / 180; return { name: `M5 LastFlame${k}`, mat: "candle", fudge: 3,
    pos: [Math.cos(a) * 1.9, HEAD + Math.sin(a) * 1.9, 0.9], life: 0.3 + k * 0.15, size: 0.7, bursts: [[0, 1]], sizeLife: [[0, 1], [0.8, 0.8], [1, 0]], alpha: [[0, 1], [0.85, 1], [1, 0]] }; }),
  ...fxShockE("M6", [0, 0, 0], 0.05, 2 * R, 0.6, 0.45),
  ...fxFlashE("T1", [0, 3.6, -0.6], 0, 2.6),
  ...fxEmbers("T2", [0, 3, 0], 0.1, 60, 2),
  { name: "T3 FallingEmbers", mat: "dotFire", fudge: -6, pos: [0, 4, 0], life: [1.2, 2.0], size: [0.05, 0.1], shape: { type: "sphere", r: 2, th: 0.7 }, bursts: [[0.2, 40]],
    gravity: 0.25, color: [[0, YEL], [0.5, ORA], [1, CRIM]], alpha: [[0, 1], [0.8, 0.8], [1, 0]] },
  ...fxFlakes("T4", [0, 0.2, 0], 1.2, 40, R * 0.7, [1.2, 1.8], [0.3, 1]),
];
const VANISH = [
  { name: "V1 Collapse", mat: "mist", fudge: 35, pos: [0, 2.5, 0], life: [0.5, 0.75], size: [1.6, 2.6], shape: { type: "sphere", r: 1.6, th: 0.5 }, bursts: [[0, 18]],
    vel: { y: [-4, -2], rad: [0.6, 1.6], space: "world" }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.75], [1, 0]], sizeLife: [[0, 1], [1, 1.4]], flip: F44 },
  { name: "V2 Flakes", mat: "flakeHot", fudge: 12, pos: [0, 3, 0], life: [0.5, 0.8], size: [0.18, 0.32], shape: { type: "sphere", r: 1.8, th: 0.8 }, bursts: [[0, 50]],
    vel: { y: [-3, -1], space: "world" }, speed: [1, 3], rot: [0, TAU], rotZ: [-5, 5], alpha: [[0, 1], [0.8, 1], [1, 0]], flip: { x: 2, y: 2 } },
  ...fxShockE("V3", [0, 0, 0], 0.35, 6.5, 0.4, 0.5),
  { name: "V4 Pool", mat: "scorch", mode: "mesh", mesh: "quad", fudge: 46, pos: [0, 0.03, 0], life: 0.8, size: 4, rot3: [0, [0, TAU], 0], bursts: [[0.2, 1]],
    sizeLife: [[0, 0.4], [0.3, 1], [1, 0.6]], alpha: [[0, 0], [0.2, 0.9], [1, 0]] },
  ...fxEmbers("V5", [0, 1, 0], 0.1, 24, 1.2),
];
const APPEAR = [
  ...magicCircle("A0", 3, [0, 0, 0], 0, 1.0, "Ora", 0.5),
  { name: "A1 Gather", mat: "mist", fudge: 35, pos: [0, 2, 0], life: [0.4, 0.55], size: [1.4, 2.4], shape: { type: "sphere", r: 3, th: 0.3 }, bursts: [[0, 16]], vel: { rad: [-6, -4.5] },
    rot: [0, TAU], alpha: [[0, 0], [0.3, 0.7], [1, 0]], sizeLife: [[0, 1.2], [1, 0.5]], flip: F44 },
  { name: "A2 FlakesIn", mat: "flake", fudge: 12, pos: [0, 2.5, 0], life: [0.4, 0.5], size: [0.1, 0.22], shape: { type: "sphere", r: 3.2, th: 0.2 }, bursts: [[0, 50]],
    vel: { rad: [-7, -5] }, rot: [0, TAU], rotZ: [-5, 5], alpha: [[0, 0], [0.2, 1], [1, 0.5]], flip: { x: 2, y: 2 } },
  { name: "A3 Column", mat: "pillarOra", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.5, size: 8, rot3: [0, [0, TAU], 0], bursts: [[0.45, 1]],
    sizeLife: [[0, 0.2, 0, 4], [0.3, 1]], alpha: [[0, 0], [0.15, 1], [1, 0]] },
  ...fxFlashE("A4", [0, 3, 0], 0.45, 3),
  ...fxShockE("A5", [0, 0, 0], 0.45, 7, 0.45, 0.55),
  ...fxSparks("A6", [0, 2.5, 0], 0.45, 26, [5, 9]),
];
const SCENE = {
  name: "AS_Fate", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "intro", role: "static", loop: false, duration: 3.4, layers: INTRO },
    { id: "death", role: "static", loop: false, duration: 3.4, layers: DEATH },
    { id: "vanish", role: "static", loop: false, duration: 1.2, layers: VANISH },
    { id: "appear", role: "static", loop: false, duration: 1.2, layers: APPEAR },
  ],
  demo: Object.assign({ interval: 4.8, dummy: { height: 7.2 } }, DEMO_BG, { scenarios: {
    intro: { label: "등장", path: [[0, [0, 0, 0], 0]], hits: [], place: { intro: { at: [0, 0, 0], start: 0 }, death: { start: 99 }, vanish: { start: 99 }, appear: { start: 99 } }, camera: cam([0, 3, 0], 26, -70, 18) },
    death: { label: "처치", path: [[0, [0, 0, 0], 0]], hits: [], place: { death: { at: [0, 0, 0], start: 0.2 }, intro: { start: 99 }, vanish: { start: 99 }, appear: { start: 99 } }, camera: cam([0, 3.5, 0], 20, -70, 15) },
    ret: { label: "귀환", path: [[0, [0, 0, 0], 0]], hits: [], place: { vanish: { at: [7, 0, 5], start: 0.2 }, appear: { at: [0, 0, 0], start: 1.0 }, intro: { start: 99 }, death: { start: 99 } }, camera: cam([3, 2, 2], 24, -80, 22) },
  } }),
};
