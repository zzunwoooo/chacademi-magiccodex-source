// PR_CandyRain — 9. 사탕 비 (광역 메테오). 보스 공중 → 사탕 메테오 1~2파(작은 원 반경 1.3, 예고 0.8) → 마지막 거대 막대사탕(나눠 맞기 원 반경 3, 예고 2.0).
// systems: aura(attached 보스, 공중 대기 루프) · meteor(투사체) · meteorHit(착탄 + 끈적 장판 3초) · shareMark(나눠 맞기 표시: 사람 셋 아이콘, 원 자체는 모드 예고) · lollipopTrail(투사체: 거대 막대사탕 모델에 붙는 꼬리) · lollipopHit(대폭발)
const RM = 1.3, TM = 0.8, RS = 3, TS = 2.0, GOO = 3;
var EXTRA_MESHES = MC_MESHES;
const AURA = [
  ...magicCircle("A", 2.6, [0, 0.1, 0], 0, 30, "Vio").map(l => Object.assign(l, { bursts: [[0, 1]] })),
  { name: "A9 OrbitCandy", mat: "candy", fudge: 5, pos: [0, 1.6, 0], life: 4, size: [0.35, 0.5], shape: { type: "circle", r: 2.2, th: 0, y: 0 }, vel: { orb: [1.5, 2.2], y: [-0.1, 0.1] }, rate: 1.5, bursts: [[0, 6]],
    rot: [0, TAU], rotZ: [-2, 2], alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]] },
  { name: "A10 Sparkles", mat: "starPink", fudge: -7, pos: [0, 1.6, 0], life: [0.4, 0.8], size: [0.15, 0.3], rate: 10, shape: { type: "sphere", r: 2.4, th: 0.5 }, sizeLife: [[0, 0], [0.4, 1], [1, 0]] },
  { name: "A11 Drips", mat: "dotPink", fudge: -6, pos: [0, 0.4, 0], life: [0.6, 1.0], size: [0.05, 0.1], rate: 14, shape: { type: "circle", r: 1.5, th: 1, y: 0 }, vel: { y: [-3, -1.5] }, alpha: [[0, 1], [1, 0]] },
];
const METEOR = [
  { name: "P1 Candy", mat: "candy", fudge: 0, loop: true, rate: 12, life: 0.1, size: 0.9, rot: [0, TAU], alpha: [[0, 1], [1, 1]] },
  { name: "P2 Glow", mat: "glowPink", fudge: 2, loop: true, rate: 10, life: 0.25, size: 1.3, alpha: [[0, 0], [0.3, 0.45], [1, 0]] },
  { name: "P3 TrailFlame", mat: "flame", fudge: 1, loop: true, world: true, rateDist: 12, life: [0.2, 0.35], size: [0.4, 0.7], rot: [0, TAU], shape: { type: "sphere", r: 0.2, th: 1 }, sizeLife: [[0, 1], [1, 0.3]], flip: F44 },
  { name: "P4 TrailStreak", mat: "streakPink", mode: "stretch", vs: 0.12, ls: 2, fudge: -3, loop: true, world: true, rateDist: 8, life: [0.2, 0.35], size: [0.1, 0.18], shape: { type: "sphere", r: 0.15, th: 1 }, speed: [0.5, 1], alpha: [[0, 1], [1, 0]] },
  { name: "P5 TrailSprinkles", mat: "dotPink", fudge: -6, loop: true, world: true, rateDist: 10, life: [0.4, 0.8], size: [0.04, 0.08], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [0.3, 1], color: [[0, WH], [0.5, PINK], [1, YEL]], alpha: [[0, 1], [1, 0]] },
];
const HIT = [
  ...fxFlash("H1", [0, 0.6, 0], 0, 2.2, "starPink"), ...fxShock("H2", [0, 0, 0], 0, 2 * RM + 0.6, "shockPink", 0.45),
  { name: "H3 Splash", mat: "candy", fudge: 5, pos: [0, 0.4, 0], life: [0.5, 0.8], size: [0.2, 0.35], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [3, 6], gravity: 1.6, rot: [0, TAU], rotZ: [-8, 8], vel: { y: [2, 4], space: "world" }, bursts: [[0, 6]] },
  { name: "H4 Sprinkles", mat: "dotPink", fudge: -6, pos: [0, 0.4, 0], life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [3, 7], drag: 2, gravity: 0.8, bursts: [[0, 30]],
    color: [[0, WH], [0.4, PINK], [1, YEL]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxFlames("H5", [0, 0.4, 0], 0, 6, 0.3, "flame", [0.7, 1.1], [1, 2]),
  // sticky candy pool (slows) — held GOO s
  { name: "H6 Goo", mat: "shockPink", mode: "mesh", mesh: "quad", fudge: 46, pos: [0, 0.04, 0], life: GOO, size: 2.2, rot3: [0, [0, TAU], 0], bursts: [[0.05, 1]], sizeLife: [[0, 0.4, 0, 3], [0.05, 1], [1, 0.85]], alpha: [[0, 0], [0.03, 0.9], [0.8, 0.7], [1, 0]] },
  { name: "H7 GooFill", mat: "glowPink", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.03, 0], life: GOO, size: 2.0, bursts: [[0.05, 1]], alpha: [[0, 0], [0.03, 0.5], [0.8, 0.4], [1, 0]] },
  { name: "H8 Bubbles", mat: "dotPink", fudge: -6, life: [0.5, 0.9], size: [0.06, 0.12], rate: 8, shape: { type: "circle", r: 0.8, th: 1, y: 0.05 }, vel: { y: [0.3, 0.7], space: "world" }, sizeLife: [[0, 0.3], [0.8, 1], [1, 1.4]], alpha: [[0, 0], [0.2, 1], [0.9, 1], [1, 0]] },
];
const SHARE = [
  { name: "S1 Marker", mat: "warnShare", mode: "mesh", mesh: "quad", fudge: 57, pos: [0, 0.05, 0], life: TS + 0.25, size: 2 * RS, rotLife: [0, 0.3, 0], bursts: [[0, 1]],
    sizeLife: [[0, 1.15, 0, -1], [0.08, 1], [1, 1]], alpha: [[0, 0], [0.05, 1], [0.88, 1], [0.9, 1], [1, 0]] },
  { name: "S2 Pulse", mat: "warnRim", mode: "mesh", mesh: "quad", fudge: 54, pos: [0, 0.05, 0], life: 0.5, size: 2 * RS, bursts: [[0.3, 1, 4, 0.45]], sizeLife: [[0, 1.3], [1, 1]], alpha: [[0, 0], [0.3, 0.8], [1, 0]] },
];
const LTRAIL = [
  { name: "B1 Swirl", mat: "streakPink", mode: "stretch", vs: 0.1, ls: 3, fudge: -3, loop: true, world: true, rateDist: 14, life: [0.3, 0.5], size: [0.2, 0.35], shape: { type: "sphere", r: 0.8, th: 1 }, speed: [0.5, 1], alpha: [[0, 1], [1, 0]] },
  { name: "B2 Flame", mat: "flame", fudge: 1, loop: true, world: true, rateDist: 10, life: [0.3, 0.55], size: [1.0, 1.8], rot: [0, TAU], shape: { type: "sphere", r: 0.6, th: 1 }, sizeLife: [[0, 1], [1, 0.3]], flip: F44 },
  { name: "B3 Mist", mat: "mist", fudge: 30, loop: true, world: true, rateDist: 3, life: [0.6, 1.0], size: [1.4, 2.4], rot: [0, TAU], shape: { type: "sphere", r: 0.6, th: 1 }, alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: F44 },
  { name: "B4 Glow", mat: "glowPink", fudge: 2, loop: true, rate: 10, life: 0.3, size: 3, alpha: [[0, 0], [0.3, 0.4], [1, 0]] },
];
const LHIT = [
  ...fxFlash("X1", [0, 1.2, 0], 0, 5, "starPink"), ...fxShock("X2", [0, 0, 0], 0, 22, "shockPink", 0.8), ...fxShock("X3", [0, 0, 0], 0.15, 16, "shockVio", 0.9),
  ...fxCrack("X4", [0, 0, 0], 0, 8, 2.0), ...magicCircle("X5", RS * 1.2, [0, 0, 0], 0, 1.5, "Vio", 0),
  ...fxFlames("X6", [0, 0.8, 0], 0, 20, 1.2, "flame", [1.4, 2.2], [1.5, 3]), ...fxPillars("X7", [0, 0, 0], 0.03, 10, RS, 5, "pillarOra"),
  ...fxRays("X8", [0, 1.2, 0], 0.02, 24, [14, 20], "streakPink", 5), ...fxSparks("X9", [0, 0.8, 0], 0, 50, [8, 16], "streakPink"),
  { name: "X10 Candies", mat: "candy", fudge: 5, pos: [0, 0.8, 0], life: [0.8, 1.2], size: [0.35, 0.6], shape: { type: "sphere", r: 0.5, th: 1 }, speed: [7, 12], gravity: 1.2, drag: 0.6, rot: [0, TAU], rotZ: [-8, 8], vel: { y: [3, 6], space: "world" }, bursts: [[0, 24]] },
  ...fxMist("X11", [0, 0.8, 0], 0.05, 8, 1.5, [1.6, 2.4], [3, 5]), ...fxBats("X12", [0, 1.4, 0], 0.08, 10, 1.5), ...fxEmbers("X13", [0, 1, 0], 0.05, 60, 3),
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_CandyRain", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "aura", role: "attached", loop: true, duration: 30, layers: AURA },
    { id: "meteor", role: "projectile", loop: true, duration: 60, layers: METEOR },
    { id: "meteorHit", role: "impact", loop: false, duration: GOO + 0.5, layers: HIT },
    { id: "shareMark", role: "static", loop: false, duration: TS + 0.3, layers: SHARE },
    { id: "lollipopTrail", role: "projectile", loop: true, duration: 60, layers: LTRAIL },
    { id: "lollipopHit", role: "impact", loop: false, duration: 2.5, layers: LHIT },
  ],
  demo: Object.assign({ interval: 3.4, dummy: { height: 3.3 } }, DEMO_BG, { scenarios: {
    meteor: { label: "메테오 1발", path: [[0, [0, 0, -4], 0]], fire: { at: TM - 0.55, stop: [] }, flightPath: [[1, 9, 1], [2, 4.5, 3], [2.5, 0.05, 4]], flight: 0.55, normal: [0, 1, 0],
      place: { shareMark: { start: 99 }, aura: { start: 0 } }, targets: [[2.5, 0, 4, 180]], camera: cam([1.5, 1.5, 2], 14, -60, 30) },
    share: { label: "나눠 맞기", path: [[0, [0, 0, -4], 0]], fire: { at: TS - 0.7, stop: [] }, flightPath: [[0, 14, 4], [0, 7, 4], [0, 0.05, 4]], flight: 0.7, normal: [0, 1, 0],
      hits: [{ t: TS, point: [0, 0, 4], normal: [0, 1, 0], incoming: [0, -1, 0], system: "lollipopHit" }], place: { shareMark: { at: [0, 0, 4], start: 0 }, aura: { start: 0 } }, targets: [[0.8, 0, 4.5, 180], [-0.8, 0, 4.4, 160], [0, 0, 3.3, 200]], camera: cam([0, 1.5, 3], 18, -60, 35) },
  } }),
};
