// PR_SoulChain — 5. 영혼 사슬 (가장 먼 적 ↔ 가장 체력 낮은 적을 묶음, 5초 안에 끊어야 함).
// systems: chain(link: 플레이어 A 가슴 → B 가슴, 매 프레임) · bind(attached: 묶인 플레이어 발밑 빨간 카운트다운 + 족쇄) · snap(impact: 끊김) · fail(impact: 못 끊으면 폭발)
const HOLD = 5;
var EXTRA_MESHES = Object.assign({ shackle: ringStrip(1, 0.12, 0.6) }, MC_MESHES);
const PROG = [[0, 0, 0, 1], [1, 1, 1, 0]];
const CHAIN = [
  { name: "L01 Links", mat: "chain", mode: "stretch", vs: 0.0, ls: 1, fudge: 0, life: [0.9, 1.1], size: [0.28, 0.34], rate: 26, bursts: [[0, 22]],
    path: { progress: PROG, bend: { x: [-0.15, 0.15], y: [-0.25, -0.05] }, wave: { amp: [0.05, 0.15], cycles: [1, 2], phase: [0, 1], vr: 0.4 } }, alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]] },
  { name: "L02 SoulFlow", mat: "streakSoul", mode: "stretch", vs: 0.06, ls: 2.2, fudge: -3, life: [0.4, 0.6], size: [0.08, 0.14], rate: 30,
    path: { progress: PROG, bend: { x: [-0.3, 0.3], y: [-0.3, 0.1] }, wave: { amp: [0.1, 0.3], cycles: [1, 3], phase: [0, 1], vr: 0.6 } }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "L03 RedPulse", mat: "chainRed", mode: "stretch", vs: 0.0, ls: 1, fudge: -1, life: 0.5, size: 0.42, bursts: [[0, 8, 10, 0.5]],
    path: { progress: [[0, 0], [1, 1]], bend: { x: [-0.1, 0.1], y: [-0.2, -0.1] } }, alpha: [[0, 0], [0.2, 0.9], [0.8, 0.9], [1, 0]] },
  { name: "L04 Motes", mat: "dotSoul", fudge: -7, life: [0.8, 1.2], size: [0.04, 0.09], rate: 24, path: { progress: [[0, 0], [1, 1]], bend: { x: [-0.6, 0.6], y: [-0.4, 0.4] } },
    alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "L05 EndGlowA", mat: "glowSoul", fudge: 2, life: 0.5, size: 0.9, rate: 4, path: { progress: [[0, 0], [1, 0]] }, alpha: [[0, 0], [0.5, 0.5], [1, 0]] },
  { name: "L06 EndGlowB", mat: "glowSoul", fudge: 2, life: 0.5, size: 0.9, rate: 4, path: { progress: [[0, 1], [1, 1]] }, alpha: [[0, 0], [0.5, 0.5], [1, 0]] },
];
const BIND = [
  { name: "B01 Shackle", mat: "lineSoul", mode: "mesh", mesh: "shackle", fudge: 20, pos: [0, 0, 0], life: HOLD + 0.3, size: 0.55, rotLife: [0, 2, 0], bursts: [[0, 1]], alpha: [[0, 0], [0.04, 1], [0.95, 1], [1, 0]] },
  { name: "B02 Shackle2", mat: "lineSoul", mode: "mesh", mesh: "shackle", fudge: 20, pos: [0, 0.35, 0], life: HOLD + 0.3, size: 0.48, rotLife: [0, -2.6, 0], bursts: [[0, 1]], alpha: [[0, 0], [0.04, 0.8], [0.95, 0.8], [1, 0]] },
  { name: "B03 Countdown", mat: "warnGrow", mode: "mesh", mesh: "quad", fudge: 58, pos: [0, 0.04, 0], life: HOLD, size: 2.6, bursts: [[0, 1]], sizeLife: [[0, 1], [1, 0.05]], alpha: [[0, 0], [0.04, 1], [0.97, 1], [1, 0]] },
  { name: "B04 Rim", mat: "warnRim", mode: "mesh", mesh: "quad", fudge: 56, pos: [0, 0.05, 0], life: HOLD, size: 2.6, rotLife: [0, 0.6, 0], bursts: [[0, 1]], alpha: [[0, 0], [0.04, 0.9], [0.97, 0.9], [1, 0]] },
  { name: "B05 SoulRise", mat: "soulFlame", fudge: 0, life: [0.4, 0.7], size: [0.25, 0.45], rate: 10, shape: { type: "circle", r: 0.5, th: 0.2, y: 0.1 }, vel: { y: [0.8, 1.5] }, flip: F44 },
];
const SNAP = [...fxFlash("S1", [0, 0, 0], 0, 1.6, "starSoul"), ...fxSparks("S2", [0, 0, 0], 0, 26, [5, 10], "streakSoul"), ...fxShards("S3", [0, 0, 0], 0, 10, 0.2),
  { name: "S4 Links", mat: "chain", mode: "stretch", vs: 0.1, ls: 1, fudge: 0, life: [0.5, 0.9], size: [0.26, 0.32], bursts: [[0, 10]], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [3, 6], gravity: 1.4, drag: 1, alpha: [[0, 1], [0.7, 1], [1, 0]] },
  ...fxMotes("S5", [0, -0.5, 0], 0.02, 16, 0.6)];
const FAIL = [...fxFlash("F1", [0, 1, 0], 0, 2.0, "starSoul"), ...fxShock("F2", [0, 0, 0], 0, 5, "shockSoul"), ...fxMist("F3", [0, 1, 0], 0, 8, 0.5, [1.2, 2.0], [1.5, 3]),
  { name: "F4 SoulBlast", mat: "soulFlame", fudge: 0, pos: [0, 1, 0], bursts: [[0, 16]], shape: { type: "sphere", r: 0.4, th: 1 }, speed: [3, 6], drag: 4, life: [0.45, 0.75], size: [0.8, 1.4], rot: [0, TAU], vel: { y: [0.5, 1.2], space: "world" }, flip: F44 },
  ...fxRays("F5", [0, 1, 0], 0.02, 12, [8, 12], "streakSoul", 3), ...fxPillars("F6", [0, 0, 0], 0.02, 4, 0.8, 3, "pillarSoul"), ...fxBats("F7", [0, 1, 0], 0.05, 5, 0.5)];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_SoulChain", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "chain", role: "link", loop: true, duration: HOLD, layers: CHAIN },
    { id: "bind", role: "attached", loop: false, duration: HOLD, layers: BIND },
    { id: "snap", role: "impact", loop: false, duration: 1.4, layers: SNAP },
    { id: "fail", role: "impact", loop: false, duration: 2.0, layers: FAIL },
  ],
  demo: Object.assign({ interval: 5.6, dummy: { height: 1.8 } }, DEMO_BG, { scenarios: {
    base: { label: "묶임", source: [-3, 1.2, 5], target: [3, 1.2, 5], path: [[0, [3, 0, 5], -90]], targets: [[-3, 0, 5, 90]], hits: [],
      camera: cam([0, 1, 5], 11, -90, 25) },
    snap: { label: "끊김", source: [-3, 1.2, 5], target: [3, 1.2, 5], path: [[0, [3, 0, 5], -90]], targets: [[-3, 0, 5, 90]], stops: { chain: 1.5, bind: 1.5 },
      hits: [{ t: 1.5, point: [0, 1.2, 5], normal: null, incoming: [1, 0, 0], system: "snap" }], camera: cam([0, 1, 5], 11, -90, 25) },
    fail: { label: "실패", source: [-3, 1.2, 5], target: [3, 1.2, 5], path: [[0, [3, 0, 5], -90]], targets: [[-3, 0, 5, 90]], stops: { chain: 1.5, bind: 1.5 },
      hits: [{ t: 1.5, point: [3, 0, 5], normal: [0, 1, 0], incoming: [0, -1, 0], system: "fail" }, { t: 1.5, point: [-3, 0, 5], normal: [0, 1, 0], incoming: [0, -1, 0], system: "fail" }], camera: cam([0, 1, 5], 13, -90, 28) },
  } }),
};
