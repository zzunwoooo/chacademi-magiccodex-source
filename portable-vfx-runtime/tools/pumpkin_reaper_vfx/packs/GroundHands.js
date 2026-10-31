// PR_GroundHands — 6. 땅속의 손 (가장 체력 낮은 적을 따라오는 원형 장판 5개, 반경 1.4블록, 예고 0.6초).
// systems: slam(보스 손 내리꽂기) · hand(해골 손 솟구침). 모드 예고(원 반경 1.4, 0.6초)를 대상 위치에 0.35초 간격 5번, 각 예고 끝에 같은 자리 hand.
const R = 1.4, T = 0.6;
var EXTRA_MESHES = Object.assign({ claw: merge([grid(1, 1, (u, v) => [(u - 0.5) * 0.5, v, 0]), grid(1, 1, (u, v) => [0, v, (u - 0.5) * 0.5])]) }, MC_MESHES);
const HAND = [
  // base
  ...fxCrack("G1", [0, 0, 0], 0, 3.2, 1.3),
  ...fxShock("G2", [0, 0, 0], 0.02, 2 * R + 0.6, "shockSoul", 0.45),
  { name: "G3 Rune", mat: "sigilSoul", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.06, 0], life: 1.0, size: 2.4, rotLife: [0, 1.2, 0], bursts: [[0, 1]], sizeLife: [[0, 0.6, 0, 2], [0.2, 1]], alpha: [[0, 0], [0.08, 0.9], [0.6, 0.7], [1, 0]] },
  // mid: five bone fingers bursting up in a ring + palm in the middle, grasping inward then sinking
  { name: "M1 Fingers", mat: "bone", mode: "mesh", mesh: "claw", fudge: 10, pos: [0, -0.1, 0], life: [0.85, 1.0], size: [1.7, 2.3], shape: { type: "circle", r: 0.7, th: 0.1, y: 0 },
    rot3: [[-0.55, -0.35], [0, TAU], [-0.15, 0.15]], rotLife: [[0.5, 0.8], 0, 0], bursts: [[0, 6]], sizeLife: [[0, 0.05, 0, 6], [0.18, 1.05], [0.3, 1], [0.75, 1], [1, 0.2]] },
  { name: "M2 FingersSmall", mat: "bone", mode: "mesh", mesh: "claw", fudge: 11, pos: [0, -0.1, 0], life: [0.8, 0.95], size: [1.0, 1.4], shape: { type: "circle", r: 0.4, th: 0.3, y: 0 },
    rot3: [[-0.5, -0.2], [0, TAU], 0], rotLife: [[0.4, 0.7], 0, 0], bursts: [[0.04, 3]], sizeLife: [[0, 0.05, 0, 6], [0.2, 1], [0.75, 1], [1, 0.2]] },
  { name: "M3 SoulCore", mat: "soulFlame", fudge: 0, pos: [0, 0.4, 0], life: [0.4, 0.7], size: [0.8, 1.3], rot: [0, TAU], shape: { type: "sphere", r: 0.4, th: 1 }, speed: [0.5, 1.5],
    vel: { y: [1.5, 3], space: "world" }, bursts: [[0, 10], [0.15, 6]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 },
  // top
  { name: "T1 Dirt", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.3, fudge: 25, pos: [0, 0.2, 0], life: [0.5, 0.8], size: [0.12, 0.24], shape: { type: "circle", r: 0.8, th: 0.5, y: 0 },
    speed: [2, 4], vel: { y: [4, 7], space: "world" }, gravity: 1.6, bursts: [[0, 18]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxSparks("T2", [0, 0.4, 0], 0, 20, [4, 8], "streakSoul", 0.6),
  ...fxRays("T3", [0, 0.3, 0], 0.03, 8, [7, 11], "streakSoul", 3),
  ...fxMist("T4", [0, 0.3, 0], 0.02, 6, 0.6, [1.0, 1.6], [0.8, 1.6]),
  ...fxMotes("T5", [0, 0.2, 0], 0.05, 18, 1.0),
  ...fxFlash("T6", [0, 0.5, 0], 0, 1.8, "starSoul"),
];
const SLAM = [...fxShock("S1", [0, 0, 1.5], 0, 4, "shockSoul"), ...fxCrack("S2", [0, 0, 1.5], 0, 3), ...fxSparks("S3", [0, 0.3, 1.5], 0, 16, [3, 7], "streakSoul"), ...fxMist("S4", [0, 0.3, 1.5], 0, 5, 0.8)];
const MATS = FX_MATS;
const SPOTS = [[1, 0, 5], [1.6, 0, 6.6], [2.3, 0, 8.1], [3.2, 0, 9.4], [4.2, 0, 10.6]];
const place = { slam: { at: [0, 0, 0], start: 0 } };
const SCENE = {
  name: "PR_GroundHands", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "slam", role: "static", loop: false, duration: 1.2, layers: SLAM },
    { id: "hand", role: "static", loop: false, duration: 1.6, layers: HAND },
  ],
  demo: Object.assign({ interval: 3.6 }, DEMO_BG, { scenarios: {
    one: sc("손 하나", { place: { hand: { at: SPOTS[0], start: 0.05 + T }, slam: { at: [0, 0, 0], start: 0.01 } }, targets: [[1, 0, 5, 180]], camera: cam([1, 1, 5], 8, -60, 26) }),
  } }),
};
