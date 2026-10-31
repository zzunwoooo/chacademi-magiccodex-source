// PR_Harvested — 즉사 공통 연출 "수확" (기믹 실패·빛 밖에서 휩쓸림 등으로 죽은 플레이어 위치에 impact). 낫 X자 베기 → 영혼이 빠져나감.
var EXTRA_MESHES = Object.assign({ xa: arc(-70, 70, 0.55, 0), xb: arc(110, 250, 0.55, 0) }, MC_MESHES);
const L = [
  { name: "X1 CutA", mat: "slashHot", mode: "mesh", mesh: "xa", fudge: 10, pos: [0, 1.1, 0], life: 0.32, size: 2.4, rot3: [0, 0, 0.9], bursts: [[0, 1]], flip: true },
  { name: "X2 CutB", mat: "slashHot", mode: "mesh", mesh: "xa", fudge: 10, pos: [0, 1.1, 0], life: 0.32, size: 2.4, rot3: [0, 0, -0.9], bursts: [[0.1, 1]], flip: true },
  { name: "X3 InkA", mat: "slashInk", mode: "mesh", mesh: "xa", fudge: 30, pos: [0, 1.1, 0], life: 0.4, size: 2.5, rot3: [0, 0, 0.9], bursts: [[0, 1]], flip: true },
  { name: "X4 InkB", mat: "slashInk", mode: "mesh", mesh: "xa", fudge: 30, pos: [0, 1.1, 0], life: 0.4, size: 2.5, rot3: [0, 0, -0.9], bursts: [[0.1, 1]], flip: true },
  ...fxFlash("X5", [0, 1.1, 0], 0.12, 2.6, "starSoul"), ...fxSparks("X6", [0, 1.1, 0], 0.1, 24, [4, 9], "streakSoul"), ...fxShards("X7", [0, 1.1, 0], 0.1, 10, 0.3),
  ...fxShock("X8", [0, 0, 0], 0.12, 4, "shockSoul"), ...fxCrack("X9", [0, 0, 0], 0.12, 2.6),
  { name: "S1 Soul", mat: "ghost", fudge: 4, pos: [0, 1.0, 0], life: 1.4, size: 1.3, bursts: [[0.25, 1]], vel: { y: 2.0, space: "world" }, drag: 0.5, noise: { s: 0.3, f: 0.6, sp: 0.4 },
    sizeLife: [[0, 0.4], [0.2, 1], [1, 1.3]], alpha: [[0, 0], [0.15, 1], [0.7, 0.8], [1, 0]] },
  { name: "S2 SoulTrail", mat: "soulFlame", fudge: 0, pos: [0, 1.0, 0], life: [0.5, 0.8], size: [0.4, 0.7], shape: { type: "sphere", r: 0.3, th: 1 }, vel: { y: [1.5, 2.5], space: "world" }, bursts: [[0.25, 6], [0.5, 6], [0.75, 4]], flip: F44 },
  ...fxMotes("S3", [0, 0.2, 0], 0.25, 24, 0.8), ...fxMist("S4", [0, 0.8, 0], 0.1, 6, 0.4, [1, 1.6], [0.8, 1.6]),
  { name: "S5 Pillar", mat: "pillarSoul", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.7, size: 5, bursts: [[0.25, 1]], rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.2, 0, 4], [0.3, 1]], alpha: [[0, 0], [0.15, 0.8], [1, 0]] },
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_Harvested", bloom: BLOOM, tonemapping: "neutral",
  systems: [{ id: "harvest", role: "impact", loop: false, duration: 2.2, layers: L }],
  demo: Object.assign({ interval: 2.6 }, DEMO_BG, { scenarios: { base: { label: "기본", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, hits: [{ t: 0.1, point: [0, 0, 0], normal: [0, 1, 0], incoming: [0, -1, 0] }], targets: [[0, 0, 0, 0]], camera: cam([0, 1.4, 0], 7, -100, 12) } } }),
};
