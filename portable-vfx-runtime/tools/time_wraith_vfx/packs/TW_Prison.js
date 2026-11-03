// TW_Prison — 기믹1 시간 감옥. systems: cast (static 보스 발밑 r 2, 1.0초: 움켜쥐는 손에 모래) · trap (static 감옥 자리 r 2.5, 1.4초: 모래시계가 내려와 가둠)
//   hold (attached 감옥 따라감, 루프 2초: 안쪽에 떨어지는 모래, 허리 높이 로마숫자 고리) · break (static r 3, 1.4초: 감옥이 깨짐, 유리·황동 조각) · fail (static r 2, 1.6초: 모래가 다 떨어져 재로 바스러짐)
const R = 2.5, HL = 2.0;
var EXTRA_MESHES = Object.assign({ hRunes: runeBand(1, 0.25, 12, 0) }, TIME_MESHES);
const CAST = [
  ...fxSand("M1", [-1.6, 6.5, 1], 0.5, 40, 0.5, [1, 3], "sand", [-1, 1]),
  ...fxFlashT("T1", [-1.6, 6.5, 1], 0.55, 2.4, "starGold", "glowGold"),
  { name: "M2 Hourglass", mat: "hglass", fudge: 3, pos: [-1.6, 7.6, 1], life: 0.8, size: 1.4, bursts: [[0.2, 1]], sizeLife: [[0, 0.4, 0, 3], [0.3, 1], [1, 1.2]], alpha: [[0, 0], [0.2, 1], [0.7, 0.8], [1, 0]] },
  ...fxMotes("T2", [0, 0.3, 0], 0.1, 20, 2, "dotGold"),
];
const TRAP = [
  ...clockCircle("B0", R, [0, 0, 0], 0, 1.35, "Gold", 0.45),
  { name: "A1 HourglassDrop", mat: "hglass", fudge: 3, pos: [0, 9, 0], life: 0.45, size: 4.5, bursts: [[0, 1]], vel: { y: -14, space: "world" }, alpha: [[0, 0], [0.3, 1], [1, 1]] },
  { name: "A2 SandRain", mat: "sand", mode: "stretch", vs: 0.06, ls: 2.4, fudge: -4, pos: [0, 7, 0], life: [0.4, 0.45], size: [0.05, 0.1], shape: { type: "circle", r: R * 0.9, th: 1, y: 0 },
    vel: { y: [-16, -14], space: "world" }, bursts: [[0, 40], [0.15, 40]], alpha: [[0, 0.8], [1, 1]] },
  ...fxShockT("M1", [0, 0, 0], 0.45, 2 * R + 3, 0.5, 0.6, "shockGold"),
  { name: "M2 Bars", mat: "pillarGold", mode: "mesh", mesh: "pillar", fudge: 5, life: 0.6, size: 4.6, shape: { type: "circle", r: R * 0.95, th: 0, y: 0 }, rot3: [0, [0, TAU], 0], bursts: [[0.45, 12]],
    sizeLife: [[0, 0.2, 0, 4], [0.3, 1], [1, 1]], alpha: [[0, 0], [0.1, 1], [0.6, 0.7], [1, 0]] },
  { name: "M3 Seal", mat: "runeGold", mode: "mesh", mesh: "hRunes", fudge: 4, pos: [0, 2.3, 0], life: 0.9, size: R * 1.05, rotLife: [0, 1.2, 0], bursts: [[0.45, 1]],
    sizeLife: [[0, 1.6, 0, -3], [0.25, 1]], alpha: [[0, 0], [0.15, 1], [0.8, 0.8], [1, 0]] },
  ...fxMist("M4", [0, 0.4, 0], 0.46, 10, R, [1.4, 2.4], [1.5, 3]),
  ...fxFlashT("T1", [0, 1.4, 0], 0.45, 3.2, "starGold", "glowGold"),
  ...fxSparks("T2", [0, 1, 0], 0.45, 26, [5, 10], "streakGold"),
  ...fxGears("T3", [0, 1.4, 0], 0.46, 6, 1.2, "gear"),
];
const HOLD = [
  { name: "H1 SandFall", mat: "sand", mode: "stretch", vs: 0.05, ls: 2, fudge: -4, pos: [0, 4.2, 0], life: [0.5, 0.6], size: [0.04, 0.08], shape: { type: "circle", r: R * 0.75, th: 1, y: 0 },
    rate: 40, vel: { y: [-7.5, -6.5], space: "world" }, alpha: [[0, 0], [0.1, 1], [1, 1]] },
  { name: "H2 Runes", mat: "runeGold", mode: "mesh", mesh: "hRunes", fudge: 4, pos: [0, 2.3, 0], life: HL, size: R * 1.05, rotLife: [0, 0.8, 0], rate: 1 / HL, bursts: [[0, 1]], onStop: 0.25,
    alpha: [[0, 0.75], [1, 0.75]] },
  { name: "H3 RunesIn", mat: "runeSoul", mode: "mesh", mesh: "hRunes", fudge: 4, pos: [0, 3.4, 0], life: HL, size: R * 0.9, rotLife: [0, -1.2, 0], rate: 1 / HL, bursts: [[0, 1]], onStop: 0.25,
    alpha: [[0, 0.5], [1, 0.5]] },
  { name: "H4 Glow", mat: "glowGold", fudge: 2, pos: [0, 2, 0], life: 1.0, size: 3.6, rate: 2, bursts: [[0, 1]], onStop: 0.2, alpha: [[0, 0], [0.4, 0.25], [1, 0]] },
  { name: "H5 Motes", mat: "dotGold", fudge: -7, life: [1, 1.6], size: [0.05, 0.1], shape: { type: "circle", r: R, th: 0.3, y: 0.2 }, rate: 14, vel: { y: [0.8, 1.6], space: "world" },
    alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "H6 Ground", mat: "rippleGold", mode: "mesh", mesh: "quad", fudge: 44, pos: [0, 0.05, 0], life: HL, size: 2 * R + 0.6, rotLife: [0, 0.3, 0], rate: 1 / HL, bursts: [[0, 1]], onStop: 0.25,
    alpha: [[0, 0.55], [1, 0.55]] },
];
const BREAK = [
  { name: "M1 Glass", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.4, fudge: 25, pos: [0, 2.2, 0], life: [0.6, 1.0], size: [0.2, 0.4], shape: { type: "sphere", r: 1.6, th: 0.3 },
    speed: [5, 10], drag: 1, gravity: 1.4, bursts: [[0, 40]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxGears("M2", [0, 2.2, 0], 0, 12, 1.4, "gear", [5, 9], [0.4, 0.8]),
  { name: "M3 SandBurst", mat: "sand", fudge: -4, pos: [0, 2.2, 0], life: [0.6, 1.1], size: [0.05, 0.12], shape: { type: "sphere", r: 1.4, th: 0.5 }, speed: [4, 9], drag: 1.6, gravity: 1.2,
    bursts: [[0, 120]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxShockT("M4", [0, 0, 0], 0, 2 * R + 4, 0.6, 0.65, "shockGold"),
  ...fxRipple("M5", [0, 0, 0], 0.03, 2 * R + 3, 0.8, "rippleGold", 0.7),
  { name: "M6 Ring", mat: "lineGold", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, 2.3, 0], life: 0.5, size: R, rot3: [Math.PI / 2, 0, 0], bursts: [[0, 1]], sizeLife: [[0, 1], [1, 2.2]], alpha: [[0, 1], [1, 0]] },
  ...fxFlashT("T1", [0, 2.2, 0], 0, 4.5, "starGold", "glowGold"),
  ...fxRays("T2", [0, 2.2, 0], 0.02, 18, [10, 15], "streakGold", 4),
  ...fxMotes("T3", [0, 0.5, 0], 0.1, 40, R + 1, "dotGold"),
  ...fxMist("T4", [0, 0.5, 0], 0.05, 10, R, [1.6, 2.6], [1.5, 3]),
];
const FAIL = [
  { name: "M1 SandEngulf", mat: "sand", fudge: -4, pos: [0, 4, 0], life: [0.6, 0.9], size: [0.06, 0.12], shape: { type: "circle", r: 1.2, th: 1, y: 0 }, bursts: [[0, 60], [0.2, 80], [0.4, 80]],
    vel: { y: [-8, -6], rad: [-1, -0.3], space: "world" }, alpha: [[0, 1], [1, 1]] },
  { name: "M2 Hourglass", mat: "hglass", fudge: 3, pos: [0, 4.2, 0], life: 1.2, size: 2.6, bursts: [[0, 1]], rotZ: [3, 3], sizeLife: [[0, 0.5, 0, 2], [0.3, 1], [1, 0.4]], alpha: [[0, 0], [0.15, 1], [0.7, 0.9], [1, 0]] },
  ...fxMist("M3", [0, 0.6, 0], 0.5, 14, 1.2, [1.6, 2.6], [0.5, 1.2]),
  { name: "M4 Dust", mat: "sandDim", fudge: -3, pos: [0, 1.2, 0], life: [1.0, 1.6], size: [0.06, 0.14], shape: { type: "sphere", r: 0.9, th: 0.8 }, bursts: [[0.6, 80]],
    vel: { y: [0.6, 2], space: "world" }, noise: { s: 0.8, f: 1.2, sp: 0.7 }, alpha: [[0, 1], [1, 0]] },
  ...fxShockT("M5", [0, 0, 0], 0.6, 6, 0.5, 0.5, "shockGold"),
  ...fxFlashT("T1", [0, 1.2, 0], 0.6, 2.6, "starGold", "glowGold"),
];
const SYSTEMS = [
  { id: "cast", role: "static", loop: false, duration: 1.0, layers: CAST },
  { id: "trap", role: "static", loop: false, duration: 1.4, layers: TRAP },
  { id: "hold", role: "attached", loop: true, duration: HL, layers: HOLD },
  { id: "break", role: "static", loop: false, duration: 1.4, layers: BREAK },
  { id: "fail", role: "static", loop: false, duration: 1.6, layers: FAIL },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "TW_Prison", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 4.5, dummy: { height: 1.8 } }, DEMO_BG, { scenarios: {
    trap: sc("가둠 → 유지", { place: { cast: { start: 99 }, trap: { at: [0, 0, 0], start: 0.05 }, hold: { start: 1.0 }, break: { start: 99 }, fail: { start: 99 } }, camera: cam([0, 2, 0], 11, -50, 18) }),
    brk: sc("깨짐", { place: { cast: { start: 99 }, trap: { start: 99 }, hold: { start: 0 }, break: { at: [0, 0, 0], start: 1.0 }, fail: { start: 99 } }, stops: { hold: 1.0 }, camera: cam([0, 2, 0], 12, -50, 18) }),
    fail: sc("실패", { place: { cast: { start: 99 }, trap: { start: 99 }, hold: { start: 0 }, break: { start: 99 }, fail: { at: [0, 0, 0], start: 0.8 } }, stops: { hold: 0.8 }, camera: cam([0, 2, 0], 11, -50, 18) }),
  } }),
};
