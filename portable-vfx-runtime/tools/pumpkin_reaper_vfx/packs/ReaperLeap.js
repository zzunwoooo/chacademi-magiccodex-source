// PR_ReaperLeap — 10. 수확자의 도약 (가장 체력 낮은 적에게 도약, 원형 반경 5블록, 예고 0.7초 이상 — 서버가 도약 시간만큼 늘림).
// systems: (예고는 모드: 착지점 원 반경 5) launch(보스 발밑) · slam(착지)
const R = 5, T = 1.15;
var EXTRA_MESHES = Object.assign({ cross1: arc(-60, 60, 0.6, 0), cross2: arc(120, 240, 0.6, 0) }, MC_MESHES);
const LAUNCH = [...fxShock("L1", [0, 0, 0], 0, 5, "shockVio"), ...fxMist("L2", [0, 0.3, 0], 0, 10, 1.5, [1.4, 2.2], [2, 4]), ...fxCrack("L3", [0, 0, 0], 0, 3.5),
  { name: "L4 UpStreaks", mat: "streakVio", mode: "stretch", vs: 0.06, ls: 3, fudge: -4, life: [0.3, 0.5], size: [0.1, 0.18], shape: { type: "circle", r: 1.2, th: 0.6, y: 0.1 }, vel: { y: [10, 16], space: "world" }, bursts: [[0, 20]], alpha: [[0, 1], [1, 0]] },
  ...fxBats("L5", [0, 1.2, 0], 0.05, 6, 0.8)];
const SLAM = [
  // base
  ...fxCrack("G1", [0, 0, 0], 0, 9, 2.0), ...fxShock("G2", [0, 0, 0], 0, 2 * R + 2, "shockOra", 0.6), ...fxShock("G3", [0, 0, 0], 0.12, 2 * R + 4, "shockVio", 0.8),
  { name: "G4 GroundX1", mat: "slash", mode: "mesh", mesh: "cross1", fudge: 40, pos: [0, 0.05, 0], life: 0.6, size: R * 0.9, rot3: [0, 0.6, 0], bursts: [[0, 1]], flip: true },
  { name: "G5 GroundX2", mat: "slash", mode: "mesh", mesh: "cross2", fudge: 40, pos: [0, 0.05, 0], life: 0.6, size: R * 0.9, rot3: [0, 0.6, 0], bursts: [[0.02, 1]], flip: true },
  ...magicCircle("G6", R * 0.75, [0, 0, 0], 0, 1.4, "Ora", 0),
  // mid
  ...fxFlames("M1", [0, 0.6, 0], 0, 18, 1.2, "flame", [1.2, 2.0], [1.5, 3]),
  ...fxPillars("M2", [0, 0, 0], 0.03, 8, R * 0.7, 4.2),
  ...fxMist("M3", [0, 0.6, 0], 0.05, 12, 1.5, [1.6, 2.6], [3, 5]),
  // top
  ...fxFlash("T1", [0, 1.0, 0], 0, 4, "starOra"), ...fxRays("T2", [0, 1, 0], 0.02, 20, [12, 18]), ...fxSparks("T3", [0, 0.6, 0], 0, 40, [7, 14]),
  ...fxShards("T4", [0, 0.6, 0], 0, 22, 1, [6, 11]), ...fxEmbers("T5", [0, 1, 0], 0.05, 60, 2.5), ...fxMotes("T6", [0, 0.2, 0], 0.1, 30, R * 0.7), ...fxBats("T7", [0, 1.2, 0], 0.08, 8, 1.2),
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_ReaperLeap", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "launch", role: "static", loop: false, duration: 1.5, layers: LAUNCH },
    { id: "slam", role: "static", loop: false, duration: 2.5, layers: SLAM },
  ],
  demo: Object.assign({ interval: 3.8 }, DEMO_BG, { scenarios: {
    base: sc("기본", { place: { launch: { at: [0, 0, 0], start: 0.5 }, slam: { at: [0, 0, 8], start: T } }, targets: [[0, 0, 0, 0], [0, 0, 8, 180], [3, 0, 10, 200]],
      camera: cam([0, 1, 5], 20, -90, 38) }) } }),
};
