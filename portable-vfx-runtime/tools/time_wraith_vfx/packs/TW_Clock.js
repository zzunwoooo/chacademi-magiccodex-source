// TW_Clock — 기믹2 바닥의 시계. systems: summon (static 시계 중심 r 7, 2.0초: 문자판이 바닥에 펼쳐짐) · tick (static 바늘 끝/꼬리 r 1, 0.5초: 맞아서 한 칸 움직임)
//   deny (static r 1, 0.5초: 틀린 원소 — 회색으로 튕겨냄) · success (static r 7, 1.8초: 세 바늘이 맞음, 종소리 금빛) · fail (static r 7, 1.6초: 시계가 깨지며 폭발 — 전멸)
const R = 7;
var EXTRA_MESHES = Object.assign({}, TIME_MESHES);
const SUMMON = [
  ...clockCircle("B0", R, [0, 0, 0], 0, 1.95, "Soul", 1.2),
  { name: "B1 Dial", mat: "dial", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.06, 0], life: 1.9, size: 2 * R, bursts: [[0.2, 1]], sizeLife: [[0, 0.1, 0, 3], [0.3, 1], [1, 1]],
    alpha: [[0, 0], [0.2, 0.42], [0.85, 0.35], [1, 0]] },
  { name: "M1 GearL", mat: "gear", mode: "mesh", mesh: "quad", fudge: 46, pos: [-R * 0.55, 0.05, -R * 0.2], life: 1.6, size: 4, rotLife: [0, 2, 0], bursts: [[0.3, 1]], alpha: [[0, 0], [0.2, 0.6], [0.8, 0.5], [1, 0]] },
  { name: "M2 GearR", mat: "gearTeal", mode: "mesh", mesh: "quad", fudge: 46, pos: [R * 0.5, 0.05, R * 0.3], life: 1.6, size: 3, rotLife: [0, -2.6, 0], bursts: [[0.35, 1]], alpha: [[0, 0], [0.2, 0.6], [0.8, 0.5], [1, 0]] },
  { name: "M3 Sweep", mat: "sweep", mode: "mesh", mesh: "handFlat", fudge: 8, pos: [0, 0.2, 0], life: 1.0, size: R, rot3: [0, 0, 0], rotLife: [0, TAU, 0], bursts: [[0.2, 1]], alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]] },
  { name: "M4 SandRing", mat: "sand", fudge: -4, life: [0.6, 1.0], size: [0.05, 0.1], shape: { type: "circle", r: R, th: 0.1, y: 0.1 }, bursts: [[0.2, 60], [0.7, 60], [1.2, 80]],
    vel: { y: [1, 3], orb: [1, 2], space: "world" }, alpha: [[0, 1], [1, 0]] },
  ...fxShockT("M5", [0, 0, 0], 1.2, 2 * R + 3, 0.6, 0.6),
  ...fxPillars("M6", [0, 0, 0], 1.2, 12, R, 4, "pillarSoul"),
  ...fxFlashT("T1", [0, 1, 0], 1.2, 4),
  ...fxMotes("T2", [0, 0.3, 0], 0.3, 50, R, "dotSoul"),
];
const TICK = [
  { name: "M1 Ring", mat: "ringSoul", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.1, 0], life: 0.35, size: 2.2, rot3: [0, [0, TAU], 0], bursts: [[0, 1]], sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 0.9], [1, 0]] },
  ...fxFlashT("T1", [0, 0.6, 0], 0, 1.8),
  ...fxSparks("T2", [0, 0.5, 0], 0, 14, [3, 6], "streakSoul", 0.2),
  ...fxGears("T3", [0, 0.6, 0], 0, 2, 0.2, "gearTeal", [2, 4], [0.2, 0.35]),
];
const DENY = [
  { name: "M1 Ring", mat: "ringInk", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.1, 0], life: 0.35, size: 2, rot3: [0, [0, TAU], 0], bursts: [[0, 1]], sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 0.8], [1, 0]] },
  ...fxMist("M2", [0, 0.6, 0], 0, 4, 0.4, [0.8, 1.2], [0.5, 1]),
  { name: "T1 Grey", mat: "starIce", fudge: -8, pos: [0, 0.6, 0], life: 0.15, size: 1, bursts: [[0, 1]], alpha: [[0, 0.5], [1, 0]] },
];
const SUCCESS = [
  ...clockCircle("B0", R, [0, 0, 0], 0, 1.75, "Gold", 0.1),
  { name: "M1 Chime", mat: "rippleGold", mode: "mesh", mesh: "quad", fudge: 28, pos: [0, 0.1, 0], life: 0.9, size: 2 * R + 4, rot3: [0, 0, 0], bursts: [[0.1, 1], [0.5, 1], [0.9, 1]],
    sizeLife: [[0, 0.2, 0, 2], [1, 1]], alpha: [[0, 0], [0.1, 0.8], [1, 0]] },
  { name: "M2 Pillar", mat: "pillarGold", mode: "mesh", mesh: "pillar", fudge: 5, life: 1.0, size: 12, bursts: [[0.1, 1]], sizeLife: [[0, 0.2, 0, 4], [0.3, 1.2], [1, 1.3]], alpha: [[0, 1], [1, 0]] },
  ...fxPillars("M3", [0, 0, 0], 0.15, 12, R, 5, "pillarGold"),
  ...fxShockT("M4", [0, 0, 0], 0.1, 2 * R + 5, 0.7, 0.6, "shockGold"),
  ...fxFlashT("T1", [0, 2, 0], 0.1, 5, "starGold", "glowGold"),
  ...fxRays("T2", [0, 1.5, 0], 0.12, 24, [10, 16], "streakGold", 5),
  ...fxSand("T3", [0, 1, 0], 0.15, 120, R * 0.5, [3, 7], "sand", [3, 6]),
  ...fxMotes("T4", [0, 0.3, 0], 0.2, 60, R, "dotGold"),
];
const FAIL = [
  ...fxCrack("B1", [0, 0, 0], 0, 2 * R + 2, 1.5),
  { name: "M1 DialShatter", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.5, fudge: 25, life: [0.6, 1.1], size: [0.25, 0.5], shape: { type: "circle", r: R, th: 0.8, y: 0.2 },
    vel: { y: [5, 11], rad: [2, 5], space: "world" }, gravity: 1.4, bursts: [[0.05, 80]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxGears("M2", [0, 1, 0], 0.05, 20, R * 0.7, "gearInk", [5, 10], [0.5, 1.0]),
  ...fxShockT("M3", [0, 0, 0], 0.05, 2 * R + 8, 0.7, 0.7),
  { name: "M4 Column", mat: "pillarSoul", mode: "mesh", mesh: "pillar", fudge: 5, life: 0.7, size: 14, bursts: [[0.05, 1]], sizeLife: [[0, 0.2, 0, 4], [0.3, 1.5], [1, 1.8]], alpha: [[0, 1], [1, 0]] },
  ...fxMist("M5", [0, 0.5, 0], 0.08, 24, R * 0.7, [2.4, 3.6], [2, 4]),
  ...fxFlashT("T1", [0, 1.5, 0], 0.05, 6),
  ...fxRays("T2", [0, 1, 0], 0.06, 30, [12, 18], "streakSoul", 5),
];
const SYSTEMS = [
  { id: "summon", role: "static", loop: false, duration: 2.0, layers: SUMMON },
  { id: "tick", role: "static", loop: false, duration: 0.5, layers: TICK },
  { id: "deny", role: "static", loop: false, duration: 0.5, layers: DENY },
  { id: "success", role: "static", loop: false, duration: 1.8, layers: SUCCESS },
  { id: "fail", role: "static", loop: false, duration: 1.6, layers: FAIL },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "TW_Clock", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.1, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 4.0 }, DEMO_BG, { scenarios: {
    summon: sc("시계 펼침", { place: { summon: { at: [0, 0, 0], start: 0.05 }, tick: { at: [0, 0, 5], start: 2.4 }, deny: { at: [2, 0, 3], start: 3.0 }, success: { start: 99 }, fail: { start: 99 } }, camera: cam([0, 0, 0], 22, -30, 45) }),
    success: sc("성공", { place: { summon: { start: 99 }, tick: { start: 99 }, deny: { start: 99 }, success: { at: [0, 0, 0], start: 0.05 }, fail: { start: 99 } }, camera: cam([0, 2, 0], 24, -30, 30) }),
    fail: sc("실패", { place: { summon: { start: 99 }, tick: { start: 99 }, deny: { start: 99 }, success: { start: 99 }, fail: { at: [0, 0, 0], start: 0.05 } }, camera: cam([0, 2, 0], 24, -30, 30) }),
  } }),
};
