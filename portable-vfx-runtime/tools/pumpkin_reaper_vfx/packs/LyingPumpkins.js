// PR_LyingPumpkins — 기믹1 거짓말하는 호박들. 호박 본체는 Blockbench 모델. systems: plant(보스 낫 꽂기) · rise(호박 솟음, 호박마다) (전멸 예고는 모드: 경기장 원 15초)
// · truth(진짜 호박 부숨) · lie(가짜 호박 폭발, 호박마다) · wipe(실패: 경기장 전멸 폭발, 경기장 중심) — 즉사한 플레이어에는 PR_Harvested
const ARENA = 20, COUNT = 15;
var EXTRA_MESHES = MC_MESHES;
const PLANT = [...fxCrack("P1", [0, 0, 1.8], 0, 5, 2), ...fxShock("P2", [0, 0, 1.8], 0, 8, "shockOra"), ...fxSparks("P3", [0, 0.3, 1.8], 0, 24, [5, 10]),
  ...fxFlames("P4", [0, 0.3, 1.8], 0, 8, 0.6), ...magicCircle("P5", 3.2, [0, 0, 0], 0.1, COUNT, "Ora"), ...fxMist("P6", [0, 0.3, 1.8], 0, 6, 0.8)];
const RISE = [
  { name: "R1 Dirt", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.3, fudge: 25, pos: [0, 0.2, 0], life: [0.5, 0.8], size: [0.12, 0.22], shape: { type: "circle", r: 0.7, th: 0.5, y: 0 },
    speed: [1.5, 3], vel: { y: [4, 6.5], space: "world" }, gravity: 1.6, bursts: [[0, 16]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxCrack("R2", [0, 0, 0], 0, 2.6, 2.0), ...fxMist("R3", [0, 0.4, 0], 0, 6, 0.5, [1.0, 1.6], [0.8, 1.5]), ...fxFlames("R4", [0, 0.5, 0], 0.15, 6, 0.4),
  ...fxEmbers("R5", [0, 0.6, 0], 0.15, 20, 0.6), ...fxShock("R6", [0, 0, 0], 0, 3, "shockOra"),
  { name: "R7 Halo", mat: "ringGold", mode: "mesh", mesh: "quad", fudge: 40, pos: [0, 0.05, 0], life: COUNT, size: 2.4, rotLife: [0, 0.5, 0], bursts: [[0.3, 1]], sizeLife: [[0, 0.3, 0, 3], [0.03, 1]], alpha: [[0, 0], [0.02, 0.6], [0.97, 0.6], [1, 0]] },
];
const TRUTH = [
  ...fxFlash("T1", [0, 0.8, 0], 0, 3.2, "starGold"), ...fxRays("T2", [0, 0.8, 0], 0, 18, [10, 15], "pillarGold", 4), ...fxPillars("T3", [0, 0, 0], 0.03, 6, 0.8, 5, "pillarGold"),
  { name: "T4 Seeds", mat: "dotGold", fudge: -6, pos: [0, 0.8, 0], life: [0.8, 1.4], size: [0.05, 0.12], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [3, 7], drag: 2, gravity: 0.4, bursts: [[0, 50]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxShock("T5", [0, 0, 0], 0, 6, "shockGold"), ...magicCircle("T6", 2.2, [0, 0, 0], 0, 1.6, "Gold", 0),
  { name: "T7 Stars", mat: "starGold", fudge: -7, pos: [0, 1.2, 0], life: [0.6, 1.1], size: [0.2, 0.45], shape: { type: "sphere", r: 1.2, th: 0.6 }, vel: { y: [0.5, 1.5], space: "world" }, bursts: [[0.05, 20]], sizeLife: [[0, 0], [0.3, 1], [1, 0]] },
];
const LIE = [
  ...fxFlash("L1", [0, 0.8, 0], 0, 3.2, "starOra"), ...fxFlames("L2", [0, 0.8, 0], 0, 18, 0.5, "flame", [1.2, 2.0], [1, 2.4]), ...fxShock("L3", [0, 0, 0], 0, 8),
  ...fxCrack("L4", [0, 0, 0], 0, 5), ...fxRays("L5", [0, 0.8, 0], 0.02, 14, [10, 15]), ...fxSparks("L6", [0, 0.8, 0], 0, 34, [6, 12]), ...fxShards("L7", [0, 0.8, 0], 0, 18, 0.4, [5, 10]),
  ...fxMist("L8", [0, 1, 0], 0.05, 10, 0.6, [1.4, 2.2], [2, 4]), ...fxBats("L9", [0, 1.2, 0], 0.05, 6, 0.6), ...fxEmbers("L10", [0, 1, 0], 0.05, 40, 1),
];
const WIPE = [
  ...fxFlash("X1", [0, 2, 0], 0, 8, "starOra"), ...fxShock("X2", [0, 0, 0], 0, 2 * ARENA + 4, "shockOra", 1.0), ...fxShock("X3", [0, 0, 0], 0.2, 2 * ARENA + 6, "shockVio", 1.2),
  ...fxPillars("X4", [0, 0, 0], 0.05, 24, ARENA * 0.7, 8), ...fxFlames("X5", [0, 1, 0], 0.05, 40, ARENA * 0.6, "flame", [2.0, 3.2], [2, 4]),
  ...fxRays("X6", [0, 2, 0], 0.03, 30, [18, 26], "streakOra", 6), ...fxMist("X7", [0, 1, 0], 0.1, 20, ARENA * 0.5, [3, 5], [2, 4]), ...fxEmbers("X8", [0, 2, 0], 0.1, 120, ARENA * 0.6),
  ...fxBats("X9", [0, 2, 0], 0.1, 20, ARENA * 0.4),
];
const MATS = FX_MATS;
const P3 = [[-5, 0, 9], [0, 0, 11], [5, 0, 9]];
const SCENE = {
  name: "PR_LyingPumpkins", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "plant", role: "static", loop: false, duration: COUNT, layers: PLANT },
    { id: "rise", role: "static", loop: false, duration: COUNT, layers: RISE },
    { id: "truth", role: "static", loop: false, duration: 2.0, layers: TRUTH },
    { id: "lie", role: "static", loop: false, duration: 2.0, layers: LIE },
    { id: "wipe", role: "static", loop: false, duration: 3.0, layers: WIPE },
  ],
  demo: Object.assign({ interval: 5 }, DEMO_BG, { scenarios: {
    truth: sc("정답", { place: { plant: { at: [0, 0, 0], start: 0 }, rise: { at: P3[1], start: 0.6 }, truth: { at: P3[1], start: 2.4 }, lie: { start: 99 }, wipe: { start: 99 } },
      clears: { plant: 2.4, rise: 2.4 }, targets: [[0, 0, 0, 0], [-2, 0, 6, 180], [2, 0, 6, 180]], camera: cam([0, 1, 6], 30, -90, 40) }),
    fail: sc("실패 (전멸)", { place: { plant: { at: [0, 0, 0], start: 0 }, rise: { at: P3[0], start: 0.6 }, lie: { at: P3[0], start: 2.4 }, wipe: { at: [0, 0, 0], start: 2.7 }, truth: { start: 99 } },
      targets: [[0, 0, 0, 0], [-2, 0, 6, 180], [2, 0, 6, 180]], camera: cam([0, 1, 4], 42, -90, 45) }),
  } }),
};
