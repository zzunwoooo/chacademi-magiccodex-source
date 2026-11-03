// TW_Stasis — 정지 구슬 · 앞당긴 미래. systems: orb (static 원 r 3, 1.3초: 시간이 멈춘 구체 → 깨짐, 맞으면 둔화) · foresight (static 원 r 4.5, 1.1초: 미래의 잔상 자리, 바늘 4개가 모여 꽂힘)
const R = 3, RF = 4.5;
var EXTRA_MESHES = Object.assign({}, TIME_MESHES);
const ORB = [
  ...clockCircle("B0", R, [0, 0, 0], 0, 1.25, "Soul", 0.32),
  // 멈춘 구체: 세로 문자판 + 고리 두 겹 (서로 다른 축) → 0.32초에 깨짐
  { name: "M1 Dial", mat: "dial", fudge: 4, pos: [0, 1.4, 0], life: 0.34, size: 2.8, bursts: [[0, 1]], sizeLife: [[0, 0.3, 0, 4], [0.4, 1], [1, 1.08]], alpha: [[0, 0], [0.2, 1], [1, 1]] },
  { name: "M2 RingA", mat: "lineSoul", mode: "mesh", mesh: "vRing", fudge: 5, pos: [0, 1.4, 0], life: 0.34, size: 1.5, rot3: [0.4, 0, 0], rotLife: [0, 3, 0], bursts: [[0, 1]], alpha: [[0, 0], [0.2, 1], [1, 1]] },
  { name: "M3 RingB", mat: "lineSoul", mode: "mesh", mesh: "vRing", fudge: 5, pos: [0, 1.4, 0], life: 0.34, size: 1.3, rot3: [-0.6, 1.2, 0], rotLife: [0, -2.4, 0], bursts: [[0, 1]], alpha: [[0, 0], [0.2, 1], [1, 1]] },
  { name: "M4 Still", mat: "sandTeal", fudge: -4, pos: [0, 1.4, 0], life: 0.34, size: [0.05, 0.1], shape: { type: "sphere", r: 1.4, th: 1 }, bursts: [[0, 40]], alpha: [[0, 0], [0.3, 1], [1, 1]] },
  { name: "M5 Glow", mat: "glowSoul", fudge: 2, pos: [0, 1.4, 0], life: 0.36, size: 3.2, bursts: [[0, 1]], alpha: [[0, 0], [0.3, 0.3], [1, 0.35]] },
  // 0.32 깨짐: 유리 같은 조각, 물결 두 겹, 멈췄던 알갱이가 한꺼번에 흩어짐
  { name: "M6 Shards", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.4, fudge: 25, pos: [0, 1.4, 0], life: [0.5, 0.8], size: [0.16, 0.3], shape: { type: "sphere", r: 1.2, th: 0.4 },
    speed: [4, 8], drag: 1.2, gravity: 1.2, bursts: [[0.32, 26]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M7 Burst", mat: "sandTeal", fudge: -4, pos: [0, 1.4, 0], life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: "sphere", r: 1.2, th: 0.6 }, speed: [4, 8], drag: 2, bursts: [[0.32, 60]], alpha: [[0, 1], [1, 0]] },
  ...fxShockT("M8", [0, 0, 0], 0.32, 2 * R + 1, 0.5, 0.6),
  ...fxRipple("M9", [0, 0, 0], 0.33, 2 * R, 0.7, "rippleGrey", 0.6),
  ...fxFlashT("T1", [0, 1.4, 0], 0.32, 3, "starIce"),
  ...fxSparks("T2", [0, 1.4, 0], 0.32, 26, [5, 10], "streakSoul"),
  ...fxMotes("T3", [0, 0.3, 0], 0.35, 24, R, "dotSoul"),
];
const DIRS = [0, 90, 180, 270];
const FORESIGHT = [
  ...clockCircle("B0", RF, [0, 0, 0], 0, 1.05, "Soul", 0.18),
  // 바늘 4개가 바깥에서 가운데로 꽂힘 (0~0.18)
  ...DIRS.map((d, k) => ({ name: "M0" + k + " Hand", mat: k % 2 ? "hand" : "handTeal", mode: "mesh", mesh: "handFlat", fudge: 8, pos: [Math.sin(d * D2R) * RF * 1.4, 0.8, Math.cos(d * D2R) * RF * 1.4],
    life: 0.22, size: RF * 1.1, rot3: [0, (d + 180) * D2R, 0], bursts: [[0, 1]], vel: { x: -Math.sin(d * D2R) * RF * 6, z: -Math.cos(d * D2R) * RF * 6, space: "world" }, alpha: [[0, 0.3], [0.5, 1], [1, 0]] })),
  { name: "M1 Pillar", mat: "pillarSoul", mode: "mesh", mesh: "pillar", fudge: 5, life: 0.5, size: 7, bursts: [[0.18, 1]], sizeLife: [[0, 0.2, 0, 4], [0.3, 1.2], [1, 1.3]], alpha: [[0, 1], [1, 0]] },
  { name: "M2 Afterimage", mat: "handInk", mode: "mesh", mesh: "handV", fudge: 20, pos: [0, 0, 0], life: 0.6, size: 3.2, rot3: [0, [0, TAU], 0], bursts: [[0.18, 3]], alpha: [[0, 0.7], [1, 0]] },
  ...fxShockT("M3", [0, 0, 0], 0.18, 2 * RF + 1, 0.5, 0.65),
  ...fxRipple("M4", [0, 0, 0], 0.2, 2 * RF, 0.7, "ripple", 0.6),
  ...fxCrack("M5", [0, 0, 0], 0.18, RF * 1.4, 0.9),
  ...fxMist("M6", [0, 0.3, 0], 0.2, 8, RF * 0.5, [1.4, 2.4], [1, 2]),
  ...fxFlashT("T1", [0, 1.0, 0], 0.18, 3.6),
  ...fxRays("T2", [0, 0.8, 0], 0.19, 14, [9, 14], "streakSoul", 3.5),
  ...fxSand("T3", [0, 0.5, 0], 0.2, 40, 1, [3, 6], "sand"),
];
const SYSTEMS = [
  { id: "orb", role: "static", loop: false, duration: 1.3, layers: ORB },
  { id: "foresight", role: "static", loop: false, duration: 1.1, layers: FORESIGHT },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "TW_Stasis", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 2.6 }, DEMO_BG, { scenarios: {
    orb: sc("정지 구슬", { place: { orb: { at: [0, 0, 0], start: 0.05 }, foresight: { start: 99 } }, camera: cam([0, 1.2, 0], 10, -50, 20) }),
    foresight: sc("앞당긴 미래", { place: { orb: { start: 99 }, foresight: { at: [0, 0, 0], start: 0.05 } }, camera: cam([0, 1.2, 0], 13, -50, 30) }),
  } }),
};
