// TW_Thrust — 시침 찌르기. systems: windup (static 보스 발밑 r 2.5, 0.8초: 모래가 발밑으로 빨려듦, 칼끝 빛) · thrust (static 직선 길이 16 · 폭 4, 1.3초)
const L = 16;
var EXTRA_MESHES = Object.assign({ band: strip(0.25, 0.02), bandThin: strip(0.08, 0.03), edgeL: grid(1, 12, (u, v) => [-0.125 + (u - 0.5) * 0.012, 0.03, v]), edgeR: grid(1, 12, (u, v) => [0.125 + (u - 0.5) * 0.012, 0.03, v]) }, TIME_MESHES);
const WINDUP = [
  ...clockCircle("B0", 2.5, [0, 0, 0], 0, 0.8, "Gold"),
  { name: "M1 SandIn", mat: "sand", fudge: -4, life: [0.4, 0.6], size: [0.05, 0.1], shape: { type: "circle", r: 3.2, th: 0.3, y: 0.2 }, bursts: [[0, 20], [0.2, 30], [0.4, 40]],
    vel: { rad: [-7, -5], y: [0.5, 1.5], orb: [2, 3] }, alpha: [[0, 0], [0.2, 1], [1, 0.6]] },
  { name: "M2 Spiral", mat: "streakGold", mode: "stretch", vs: 0.05, ls: 2, fudge: -5, life: [0.3, 0.5], size: [0.05, 0.09], shape: { type: "circle", r: 2.6, th: 0.2, y: 0.3 },
    bursts: [[0.1, 12], [0.35, 20]], vel: { rad: [-6, -4], orb: [4, 6], y: [1, 2] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  ...fxMist("M3", [0, 0.3, 0], 0.05, 6, 1.6, [1.2, 2.0], [-1, -0.4]),
  ...fxFlashT("T1", [1.6, 2.4, 1.2], 0.55, 2.2, "starGold", "glowGold"),
];
const PTS = [0.15, 0.3, 0.45, 0.6, 0.75, 0.9].map(k => [0, 0.6, k * L]);
const THRUST = [
  // base: 바닥에 길게 그어진 시간 균열 띠 + 양쪽 눈금 선 + 물결
  { name: "G01 BandInk", mat: "sweepInk", mode: "mesh", mesh: "band", fudge: 46, pos: [0, 0.02, 0], life: 1.1, size: L, bursts: [[0, 1]], sizeLife: [[0, 0.1, 0, 8], [0.1, 1]],
    alpha: [[0, 0], [0.05, 0.8], [0.7, 0.6], [1, 0]] },
  { name: "G02 EdgeL", mat: "lineSoul", mode: "mesh", mesh: "edgeL", fudge: 42, pos: [0, 0.03, 0], life: 0.9, size: L, bursts: [[0.02, 1]], sizeLife: [[0, 0.1, 0, 8], [0.1, 1]], alpha: [[0, 0], [0.05, 1], [0.6, 0.7], [1, 0]] },
  { name: "G03 EdgeR", mat: "lineSoul", mode: "mesh", mesh: "edgeR", fudge: 42, pos: [0, 0.03, 0], life: 0.9, size: L, bursts: [[0.02, 1]], sizeLife: [[0, 0.1, 0, 8], [0.1, 1]], alpha: [[0, 0], [0.05, 1], [0.6, 0.7], [1, 0]] },
  ...PTS.map((p, n) => ({ name: "G1" + n + " Ripple", mat: "ripple", mode: "mesh", mesh: "quad", fudge: 28, pos: [0, 0.07, p[2]], life: 0.6, size: 4, rot3: [0, [0, TAU], 0],
    bursts: [[0.02 + n * 0.025, 1]], sizeLife: [[0, 0.2, 0, 2], [1, 1]], alpha: [[0, 0], [0.1, 0.6], [1, 0]] })),
  ...fxCrack("G20", [0, 0, L], 0.12, 5, 1.0),
  // mid: 거대한 시침이 앞으로 꽂힘 (남색 그림자 + 금빛 몸) + 빛 줄기 + 끝에서 폭발
  { name: "M01 SpearInk", mat: "handInk", mode: "mesh", mesh: "handFlat", fudge: 9, pos: [0, 1.4, 0], life: 0.5, size: L * 1.02, bursts: [[0, 1]], sizeLife: [[0, 0.15, 0, 8], [0.15, 1]], alpha: [[0, 0.8], [0.7, 0.7], [1, 0]] },
  { name: "M02 Spear", mat: "hand", mode: "mesh", mesh: "handFlat", fudge: 8, pos: [0, 1.45, 0], life: 0.45, size: L, bursts: [[0, 1]], sizeLife: [[0, 0.15, 0, 8], [0.15, 1]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "M03 Beam", mat: "sweep", mode: "mesh", mesh: "bandThin", fudge: 7, pos: [0, 1.5, 0], life: 0.35, size: L, rot3: [0, 0, 0], bursts: [[0.02, 1]], sizeLife: [[0, 0.1, 0, 8], [0.12, 1]], alpha: [[0, 1], [1, 0]], flip: true },
  { name: "M04 Streaks", mat: "streakSoul", mode: "stretch", vs: 0.1, ls: 5, fudge: -6, pos: [0, 1.3, 1], life: [0.2, 0.32], size: [0.1, 0.2], shape: { type: "circle", r: 1.2, th: 0.6, y: 0 },
    vel: { z: [30, 45] }, bursts: [[0, 18]], alpha: [[0, 1], [1, 0]] },
  ...fxShockT("M05", [0, 0, L], 0.14, 7, 0.5, 0.6),
  ...fxGears("M06", [0, 1.2, L], 0.14, 8, 0.6, "gear", [3, 7]),
  ...fxMist("M07", [0, 0.4, L * 0.6], 0.05, 10, 2, [1.4, 2.4], [0.4, 1]),
  // top: 길을 따라 모래·불티, 끝 섬광
  ...PTS.map((p, n) => ({ name: "T0" + n + " Sand", mat: "sand", fudge: -4, pos: p, life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: "sphere", r: 0.8, th: 1 },
    speed: [2, 4], drag: 1.5, gravity: 1.2, vel: { y: [1, 3], space: "world" }, bursts: [[0.02 + n * 0.025, 10]], alpha: [[0, 1], [0.8, 1], [1, 0]] })),
  ...fxSparks("T1", [0, 1.2, L], 0.14, 30, [6, 12], "streakGold"),
  ...fxFlashT("T2", [0, 1.4, L], 0.14, 3.5),
  ...fxMotes("T3", [0, 0.4, L * 0.5], 0.1, 30, 2, "dotSoul"),
];
const SYSTEMS = [
  { id: "windup", role: "static", loop: false, duration: 0.8, layers: WINDUP },
  { id: "thrust", role: "static", loop: false, duration: 1.3, layers: THRUST },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "TW_Thrust", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: L, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 2.6 }, DEMO_BG, { scenarios: {
    thrust: sc("시침 찌르기", { place: { windup: { at: [0, 0, 0], start: 0.0 }, thrust: { at: [0, 0, 0], start: 0.7 } }, camera: cam([0, 1, 8], 20, -60, 30) }),
  } }),
};
