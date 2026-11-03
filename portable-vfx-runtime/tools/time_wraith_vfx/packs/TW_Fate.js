// TW_Fate — 등장·귀환·그로기·가짜 클리어·되감기 부활·2페이즈·처치·유저 잔상. systems:
//   intro (static 보스 r 6, 3.0초) · vanish (static r 3, 0.8) · appear (static r 3, 0.8) · groggy (static r 4, 1.2: 추락) · stun (attached 보스 루프 2초)
//   fakeclear (static 보스 r 6, 2.6초: 무너지며 모래로 흩어짐 + 금빛 '끝' 섬광) · rewind (static 보스 r 8, 2.6초: 모든 것이 거꾸로 빨려듦, 바늘 역회전)
//   phase2 (attached 보스 루프 2초: 2페이즈 오라 — 청록 잔상 깜빡임, 역회전 고리) · death (static 보스 r 6, 3.0초)
//   echo_hit (static r 1.2, 0.7초: 유저 잔상이 맞아 깨짐 — 맞은 플레이어 발밑이 아니라 잔상 위치)
const R = 6, HEAD = 6.6, GL = 2.0;
var EXTRA_MESHES = Object.assign({}, TIME_MESHES);
const INTRO = [
  ...clockCircle("B0", R, [0, 0, 0], 0, 3.0, "Soul", 2.2),
  { name: "M1 SandRise", mat: "sand", fudge: -4, life: [1.0, 1.4], size: [0.05, 0.1], shape: { type: "circle", r: 4, th: 0.3, y: 0.2 },
    bursts: [[0, 30], [0.5, 40], [1.0, 60], [1.5, 80]], vel: { rad: [-2, -1], y: [3, 5], orb: [3, 5] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "M2 Gears", mat: "gearTeal", fudge: 6, life: [1.2, 1.6], size: [0.4, 0.8], shape: { type: "circle", r: 3.5, th: 0.3, y: 0.5 }, bursts: [[0.3, 6], [0.9, 8], [1.5, 10]],
    vel: { orb: [2, 3], rad: [-1.5, -1], y: [2, 3.5] }, rot: [0, TAU], rotZ: [-5, 5], alpha: [[0, 0], [0.2, 1], [1, 0]] },
  ...fxMist("M3", [0, 0.4, 0], 1.6, 12, 1.2, [2, 3], [0.5, 1.2]),
  { name: "M4 Halo", mat: "lineGold", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, HEAD, 0.9], life: 0.8, size: 1.8, rotLife: [0, 0, 2], bursts: [[2.2, 1]],
    sizeLife: [[0, 0.2, 0, 4], [0.25, 1.3], [0.5, 1.15], [1, 1.1]], alpha: [[0, 0], [0.1, 1], [0.7, 0.9], [1, 0]] },
  { name: "M5 HaloDial", mat: "dialGold", fudge: 3, pos: [0, HEAD, 0.9], life: 0.8, size: 4.2, bursts: [[2.2, 1]], sizeLife: [[0, 0.3, 0, 3], [0.3, 1], [1, 1.05]], alpha: [[0, 0], [0.1, 0.8], [1, 0]] },
  ...fxShockT("M6", [0, 0, 0], 2.2, 2 * R + 2, 0.6, 0.6), ...fxShockT("M6b", [0, 0, 0], 2.36, 2 * R * 0.7, 0.5, 0.5, "shockGold"),
  ...fxPillars("M7", [0, 0, 0], 2.2, 12, R * 0.85, 5, "pillarSoul"),
  ...fxFlashT("T1", [0, HEAD, 0.9], 2.2, 3.6, "starGold", "glowGold"),
  ...fxRays("T2", [0, HEAD, 0.9], 2.22, 20, [10, 15], "streakSoul", 4.5),
  ...fxMotes("T3", [0, 0.3, 0], 0.3, 60, 4, "dotGold"),
];
const VANISH = [
  { name: "M1 Sand", mat: "sand", fudge: -4, pos: [0, 3, 0], life: [0.4, 0.7], size: [0.06, 0.12], shape: { type: "sphere", r: 2, th: 0.8 }, bursts: [[0, 140]], vel: { y: [-4, -1], space: "world" }, alpha: [[0, 1], [1, 0]] },
  ...fxRipple("M2", [0, 0, 0], 0, 6, 0.6, "ripple", 0.8, [1, 0.1]),
  ...fxFlashT("T1", [0, 3, 0], 0.05, 3),
];
const APPEAR = [
  { name: "M1 Sand", mat: "sand", fudge: -4, life: [0.4, 0.6], size: [0.06, 0.12], shape: { type: "circle", r: 3, th: 0.3, y: 0.3 }, bursts: [[0, 120]], vel: { rad: [-6, -4], y: [3, 7] }, alpha: [[0, 0], [0.3, 1], [1, 0]] },
  ...fxRipple("M2", [0, 0, 0], 0, 6, 0.6, "ripple", 0.8),
  ...fxShockT("M3", [0, 0, 0], 0.35, 7, 0.4, 0.5),
  ...fxFlashT("T1", [0, 3, 0], 0.35, 3),
];
const GROGGY = [
  ...fxCrack("B1", [0, 0, 0], 0.08, 7, 1.4),
  ...fxShockT("M1", [0, 0, 0], 0.08, 9, 0.5, 0.55),
  { name: "M2 HaloDrop", mat: "lineGold", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, 6.2, 0.9], life: 0.5, size: 1.6, rot3: [0.2, 0, 0], rotLife: [0, 0, 3], bursts: [[0, 1]], vel: { y: [-8, -8], space: "world" },
    alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxGears("M3", [0, 1, 0], 0.45, 10, 1, "gear", [3, 6]),
  ...fxSand("M4", [0, 0.6, 0], 0.08, 80, 1.5, [3, 6]),
  ...fxMist("M5", [0, 0.4, 0], 0.1, 12, 1.5, [1.6, 2.6], [1, 2]),
  ...fxFlashT("T1", [0, 1, 0], 0.08, 3.4),
  ...fxSparks("T2", [0, 0.6, 0], 0.08, 30, [5, 10], "streakSoul"),
];
const STUN = [
  { name: "S1 Halo", mat: "lineGold", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, 4.6, -0.3], life: GL, size: 1.1, rot3: [1.25, 0, 0], rotLife: [0, 0, 1.2], rate: 1 / GL, bursts: [[0, 1]], onStop: 0.25,
    alpha: [[0, 0.9], [0.3, 0.4], [0.5, 0.9], [1, 0.9]] },
  { name: "S2 Stars", mat: "starGold", fudge: -7, pos: [0, 4.9, -0.3], life: [0.6, 0.9], size: [0.4, 0.7], shape: { type: "circle", r: 1.0, th: 0, y: 0 }, rate: 8, vel: { orb: [4, 5] }, sizeLife: [[0, 0], [0.3, 1], [1, 0]] },
  { name: "S3 SandLeak", mat: "sand", fudge: -4, pos: [0, 2.6, -0.6], life: [0.8, 1.2], size: [0.04, 0.08], shape: { type: "sphere", r: 0.8, th: 0.5 }, rate: 24, gravity: 1.0, alpha: [[0, 1], [1, 0]] },
  { name: "S4 Gears", mat: "gearTeal", fudge: 6, pos: [0, 4.6, -0.3], life: [0.8, 1.2], size: [0.25, 0.4], shape: { type: "circle", r: 1.2, th: 0, y: 0 }, rate: 3, vel: { orb: [-3, -2] }, rot: [0, TAU], rotZ: [-3, 3],
    alpha: [[0, 0], [0.3, 1], [1, 0]] },
  { name: "S5 Smoke", mat: "mist", fudge: 35, pos: [0, 1.8, 0], life: [1.4, 2.0], size: [1.2, 2.0], shape: { type: "sphere", r: 1.1, th: 0.6 }, rate: 4, vel: { y: [0.4, 0.9], space: "world" }, rot: [0, TAU],
    alpha: [[0, 0], [0.25, 0.5], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
];
const FAKE = [
  // 0~1.4 무너짐: 몸에서 모래가 쏟아지고 후광이 떨어짐 → 1.5 금빛 '끝' 섬광 (가짜 클리어) → 모래 먼지
  { name: "M1 Crumble", mat: "sand", fudge: -4, pos: [0, 3.4, 0], life: [0.8, 1.2], size: [0.06, 0.13], shape: { type: "sphere", r: 1.8, th: 0.8 }, bursts: [[0, 80], [0.4, 100], [0.8, 120], [1.2, 80]],
    gravity: 1.2, vel: { y: [-1, 1], space: "world" }, alpha: [[0, 1], [1, 0.3]] },
  { name: "M2 HaloFall", mat: "lineGold", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, HEAD, 0.9], life: 1.3, size: 1.8, rotLife: [0, 0, 4], bursts: [[0.2, 1]], vel: { y: [-4, -4], space: "world" },
    alpha: [[0, 1], [0.8, 0.8], [1, 0]] },
  ...fxMist("M3", [0, 0.6, 0], 0.6, 16, 1.6, [2, 3], [0.5, 1.4]),
  ...fxRipple("M4", [0, 0, 0], 1.5, 2 * R + 4, 1.0, "rippleGold", 0.8),
  { name: "M5 Clear", mat: "pillarGold", mode: "mesh", mesh: "pillar", fudge: 5, life: 0.9, size: 14, bursts: [[1.5, 1]], sizeLife: [[0, 0.2, 0, 4], [0.3, 1.3], [1, 1.4]], alpha: [[0, 1], [1, 0]] },
  ...fxFlashT("T1", [0, 2.5, 0], 1.5, 6, "starGold", "glowGold"),
  ...fxRays("T2", [0, 2, 0], 1.52, 24, [10, 16], "streakGold", 5),
  ...fxMotes("T3", [0, 0.4, 0], 1.6, 80, R, "dotGold"),
];
const REWIND = [
  // 모든 것이 거꾸로: 바깥에서 모래·톱니·물결이 안으로 빨려듦, 문자판 바늘 역회전, 2.4초에 다시 일어섬
  { name: "B1 Dial", mat: "dial", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.06, 0], life: 2.5, size: 2 * R + 4, rotLife: [0, -2.4, 0], bursts: [[0, 1]],
    sizeLife: [[0, 1.2, 0, -1], [0.85, 0.9], [1, 0.2]], alpha: [[0, 0], [0.1, 0.7], [0.85, 0.7], [1, 0]] },
  { name: "B2 Ticks", mat: "lineSoul", mode: "mesh", mesh: "mcTicks", fudge: 43, pos: [0, 0.05, 0], life: 2.5, size: R + 2, rotLife: [0, -1.2, 0], bursts: [[0, 1]], alpha: [[0, 0], [0.1, 0.9], [0.85, 0.8], [1, 0]] },
  { name: "B3 HandSweep", mat: "handTeal", mode: "mesh", mesh: "handFlat", fudge: 8, pos: [0, 0.2, 0], life: 2.4, size: R + 1, rotLife: [0, -TAU * 1.5, 0], bursts: [[0, 1]], alpha: [[0, 0], [0.1, 1], [0.9, 1], [1, 0]] },
  { name: "M1 SandIn", mat: "sand", fudge: -4, life: [0.6, 0.8], size: [0.05, 0.11], shape: { type: "circle", r: R + 2, th: 0.4, y: 0.5 }, bursts: [[0.1, 60], [0.6, 90], [1.1, 120], [1.6, 160]],
    vel: { rad: [-12, -8], y: [2, 5], orb: [-4, -2] }, alpha: [[0, 0], [0.3, 1], [1, 0.4]] },
  { name: "M2 GearIn", mat: "gearTeal", fudge: 6, life: [0.7, 0.9], size: [0.4, 0.8], shape: { type: "circle", r: R + 1, th: 0.4, y: 1 }, bursts: [[0.3, 8], [0.9, 10], [1.5, 12]],
    vel: { rad: [-9, -6], y: [2, 4], orb: [-3, -2] }, rot: [0, TAU], rotZ: [5, 8], alpha: [[0, 0], [0.3, 1], [1, 0]] },
  { name: "M3 RippleIn", mat: "ripple", mode: "mesh", mesh: "quad", fudge: 28, pos: [0, 0.1, 0], life: 0.8, size: 2 * R + 6, bursts: [[0.2, 1], [0.8, 1], [1.4, 1]], sizeLife: [[0, 1], [1, 0.05]], alpha: [[0, 0], [0.2, 0.8], [1, 0]] },
  { name: "M4 StreakIn", mat: "streakSoul", mode: "stretch", vs: 0.08, ls: 3, fudge: -5, life: [0.3, 0.45], size: [0.08, 0.15], shape: { type: "circle", r: R + 3, th: 0.5, y: 1.5 }, bursts: [[0.5, 20], [1.0, 30], [1.5, 40], [2.0, 50]],
    vel: { rad: [-30, -20] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  ...fxShockT("M5", [0, 0, 0], 2.4, 2 * R + 6, 0.7, 0.7),
  ...fxFlashT("T1", [0, HEAD, 0.9], 2.4, 5),
  ...fxRays("T2", [0, HEAD, 0.9], 2.42, 26, [10, 16], "streakSoul", 5),
  ...fxPillars("T3", [0, 0, 0], 2.4, 14, R, 6, "pillarSoul"),
];
const PH2 = [
  { name: "S1 RevRing", mat: "lineSoul", mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: GL, size: 3.2, rotLife: [0, -2, 0], rate: 1 / GL, bursts: [[0, 1]], onStop: 0.25, alpha: [[0, 0.8], [1, 0.8]] },
  { name: "S2 RevRunes", mat: "runeSoul", mode: "mesh", mesh: "mcRunesOut", fudge: 50, pos: [0, 0.05, 0], life: GL, size: 3.4, rotLife: [0, 1.4, 0], rate: 1 / GL, bursts: [[0, 1]], onStop: 0.25, alpha: [[0, 0.7], [1, 0.7]] },
  { name: "S3 Halo", mat: "lineSoul", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, HEAD, 0.9], life: GL, size: 2.0, rotLife: [0, 0, -3], rate: 1 / GL, bursts: [[0, 1]], onStop: 0.25,
    alpha: [[0, 0.9], [0.45, 0.4], [0.5, 1], [1, 0.9]] },
  { name: "S4 Ghost", mat: "handInk", mode: "mesh", mesh: "handV", fudge: 20, pos: [0, 0, 0], life: 0.5, size: 7, rate: 2, rot3: [0, [0, TAU], 0], alpha: [[0, 0], [0.2, 0.35], [1, 0]] },
  { name: "S5 SandUp", mat: "sandTeal", fudge: -4, life: [1.0, 1.4], size: [0.04, 0.08], shape: { type: "circle", r: 2.4, th: 0.6, y: 0.2 }, rate: 30, vel: { y: [1.5, 3], orb: [-2, -1], space: "world" },
    alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "S6 Sparks", mat: "streakSoul", mode: "stretch", vs: 0.06, ls: 1.6, fudge: -5, pos: [0, 3, 0], life: [0.3, 0.5], size: [0.04, 0.08], shape: { type: "sphere", r: 1.8, th: 0.5 }, rate: 14, speed: [1, 2], alpha: [[0, 1], [1, 0]] },
  { name: "S7 Mist", mat: "mist", fudge: 35, pos: [0, 0.6, 0], life: [1.4, 2.0], size: [1.4, 2.2], shape: { type: "circle", r: 1.8, th: 0.5, y: 0 }, rate: 4, vel: { y: [0.3, 0.7], space: "world" }, rot: [0, TAU],
    alpha: [[0, 0], [0.25, 0.45], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
];
const DEATH = [
  { name: "M1 Crumble", mat: "sand", fudge: -4, pos: [0, 3.4, 0], life: [1.0, 1.6], size: [0.06, 0.14], shape: { type: "sphere", r: 1.8, th: 0.8 }, bursts: [[0, 120], [0.5, 140], [1.0, 120]],
    vel: { y: [0.6, 2.2], space: "world" }, noise: { s: 1, f: 1, sp: 0.8 }, alpha: [[0, 1], [1, 0]] },
  { name: "M2 HaloBreak", mat: "lineGold", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, HEAD, 0.9], life: 0.9, size: 1.8, bursts: [[0.6, 1]], sizeLife: [[0, 1], [1, 2.6]], alpha: [[0, 1], [1, 0]] },
  ...fxGears("M3", [0, HEAD, 0.9], 0.6, 16, 1.4, "gear", [4, 8], [0.4, 0.9]),
  { name: "M4 Hourglass", mat: "hglassTeal", fudge: 3, pos: [0, 3.6, -0.6], life: 1.6, size: 3, bursts: [[1.2, 1]], sizeLife: [[0, 0.5, 0, 2], [0.3, 1], [1, 0.3]], alpha: [[0, 0], [0.2, 1], [0.7, 0.9], [1, 0]] },
  ...fxMist("M5", [0, 0.8, 0], 0.3, 18, 1.8, [2, 3.2], [0.5, 1.2]),
  ...fxShockT("M6", [0, 0, 0], 0.6, 2 * R + 2, 0.6, 0.55),
  ...fxRipple("M7", [0, 0, 0], 1.2, 2 * R + 4, 1.2, "ripple", 0.7),
  ...fxFlashT("T1", [0, 3.6, -0.6], 1.2, 4),
  ...fxMotes("T2", [0, 0.5, 0], 0.2, 90, R, "dotSoul", [1.6, 2.6]),
];
const ECHOHIT = [
  { name: "M1 Shards", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.4, fudge: 25, pos: [0, 1, 0], life: [0.4, 0.6], size: [0.12, 0.24], shape: { type: "sphere", r: 0.5, th: 0.5 },
    speed: [4, 8], gravity: 1.2, bursts: [[0, 20]], alpha: [[0, 1], [1, 0]] },
  { name: "M2 Sand", mat: "sandTeal", fudge: -4, pos: [0, 1, 0], life: [0.4, 0.7], size: [0.05, 0.1], shape: { type: "sphere", r: 0.5, th: 0.6 }, speed: [3, 6], drag: 2, bursts: [[0, 40]], alpha: [[0, 1], [1, 0]] },
  ...fxRipple("M3", [0, 0, 0], 0, 3, 0.5, "ripple", 0.8),
  ...fxFlashT("T1", [0, 1, 0], 0, 2.4),
];
const SYSTEMS = [
  { id: "intro", role: "static", loop: false, duration: 3.0, layers: INTRO },
  { id: "vanish", role: "static", loop: false, duration: 0.8, layers: VANISH },
  { id: "appear", role: "static", loop: false, duration: 0.8, layers: APPEAR },
  { id: "groggy", role: "static", loop: false, duration: 1.3, layers: GROGGY },
  { id: "stun", role: "attached", loop: true, duration: GL, layers: STUN },
  { id: "fakeclear", role: "static", loop: false, duration: 2.6, layers: FAKE },
  { id: "rewind", role: "static", loop: false, duration: 2.6, layers: REWIND },
  { id: "phase2", role: "attached", loop: true, duration: GL, layers: PH2 },
  { id: "death", role: "static", loop: false, duration: 3.0, layers: DEATH },
  { id: "echo_hit", role: "static", loop: false, duration: 0.7, layers: ECHOHIT },
];
const MATS = usedMats(SYSTEMS);
const OFF = {}; for (const s of SYSTEMS) OFF[s.id] = { start: 99 };
const P = (o) => Object.assign({}, OFF, o);
const SCENE = {
  name: "TW_Fate", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.1, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 5.0, dummy: { height: 7 } }, DEMO_BG, { scenarios: {
    intro: sc("등장", { place: P({ intro: { at: [0, 0, 0], start: 0 } }), camera: cam([0, 3, 0], 20, -50, 18) }),
    fake: sc("가짜 클리어 → 되감기", { place: P({ fakeclear: { at: [0, 0, 0], start: 0 }, rewind: { at: [0, 0, 0], start: 2.4 } }), camera: cam([0, 3, 0], 22, -50, 20) }),
    phase2: sc("2페이즈 오라 + 잔상 맞음", { place: P({ phase2: { start: 0 }, echo_hit: { at: [4, 0, 2], start: 1.0 } }), camera: cam([0, 3, 0], 18, -50, 20) }),
    groggy: sc("그로기", { place: P({ groggy: { at: [0, 0, 0], start: 0.1 }, stun: { start: 1.0 } }), camera: cam([0, 2, 0], 16, -50, 18) }),
    death: sc("처치", { place: P({ death: { at: [0, 0, 0], start: 0 } }), camera: cam([0, 3, 0], 20, -50, 18) }),
  } }),
};
