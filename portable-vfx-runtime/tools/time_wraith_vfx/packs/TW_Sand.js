// TW_Sand — 모래 낙하 3연속. systems: cast (static 보스 발밑 r 3, 1.8초: 든 손 위로 모래 분수, 0.6/1.0/1.4초 움켜쥘 때 반짝) · fall (static 원 r 4, 1.1초: 0~0.25 위에서 모래 기둥 → 0.25 착탄)
const R = 4, HAND = 7.5;
var EXTRA_MESHES = Object.assign({}, TIME_MESHES);
const CAST = [
  { name: "B1 Hourglass", mat: "hglass", fudge: 3, pos: [-1.6, HAND + 1.6, 0.6], life: 1.6, size: 1.6, bursts: [[0.2, 1]], sizeLife: [[0, 0.3, 0, 3], [0.2, 1], [1, 1]], alpha: [[0, 0], [0.15, 0.9], [0.85, 0.8], [1, 0]] },
  { name: "B2 HourglassRing", mat: "lineGold", mode: "mesh", mesh: "vRing", fudge: 4, pos: [-1.6, HAND + 1.6, 0.6], life: 1.6, size: 1.2, rotLife: [0, 0, 1.5], bursts: [[0.2, 1]], alpha: [[0, 0], [0.15, 0.9], [0.85, 0.8], [1, 0]] },
  { name: "M1 Fountain", mat: "sand", fudge: -4, pos: [-1.6, HAND, 0.6], life: [0.7, 1.1], size: [0.05, 0.1], shape: { type: "sphere", r: 0.4, th: 1 }, rate: 0,
    bursts: [[0.3, 20], [0.6, 40], [1.0, 40], [1.4, 40]], speed: [2, 4], vel: { y: [4, 7], space: "world" }, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...[0.6, 1.0, 1.4].flatMap((t, k) => fxFlashT("T" + k, [-1.6, HAND, 0.6], t, 2, "starGold", "glowGold")),
  ...fxSandFall("M2", [0, 0, 0], 0.4, 40, 2.5, 3, 0.5),
  ...fxMotes("M3", [0, 0.3, 0], 0.1, 30, 2.5, "dotGold"),
];
const FALL = [
  // 0~0.25 위에서 쏟아지는 모래 기둥
  { name: "A1 Column", mat: "sand", mode: "stretch", vs: 0.08, ls: 3, fudge: -4, pos: [0, 9, 0], life: [0.24, 0.26], size: [0.08, 0.15], shape: { type: "circle", r: R * 0.6, th: 1, y: 0 },
    vel: { y: [-38, -34], space: "world" }, bursts: [[0, 60]], alpha: [[0, 0.9], [1, 1]] },
  { name: "A2 Stream", mat: "streakGold", mode: "stretch", vs: 0.06, ls: 4, fudge: -5, pos: [0, 9, 0], life: [0.22, 0.25], size: [0.12, 0.2], shape: { type: "circle", r: R * 0.4, th: 1, y: 0 },
    vel: { y: [-40, -36], space: "world" }, bursts: [[0, 20]], alpha: [[0, 0.8], [1, 1]] },
  // 0.25 착탄: 모래 왕관 튐 + 바닥 모래 원 + 물결 + 먼지
  { name: "G1 SandDisk", mat: "rippleGold", mode: "mesh", mesh: "quad", fudge: 44, pos: [0, 0.04, 0], life: 0.9, size: 2 * R, rot3: [0, [0, TAU], 0], bursts: [[0.25, 1]],
    sizeLife: [[0, 0.3, 0, 3], [0.25, 1]], alpha: [[0, 0], [0.05, 0.9], [0.7, 0.5], [1, 0]] },
  ...clockCircle("G2", R, [0, 0, 0], 0.22, 0.85, "Gold", 0.25),
  { name: "M1 Crown", mat: "sand", fudge: -4, pos: [0, 0.3, 0], life: [0.5, 0.9], size: [0.06, 0.12], shape: { type: "circle", r: R * 0.5, th: 0.5, y: 0 }, bursts: [[0.25, 90]],
    speed: [3, 6], vel: { rad: [3, 6], y: [4, 8], space: "world" }, gravity: 1.6, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxShockT("M2", [0, 0, 0], 0.25, 2 * R + 1, 0.5, 0.6, "shockGold"),
  ...fxMist("M3", [0, 0.4, 0], 0.26, 12, R * 0.6, [1.6, 2.6], [1.5, 3]),
  ...fxPillars("M4", [0, 0, 0], 0.26, 6, R * 0.7, 3, "pillarGold"),
  ...fxFlashT("T1", [0, 0.8, 0], 0.25, 3.4, "starGold", "glowGold"),
  ...fxSparks("T2", [0, 0.6, 0], 0.25, 30, [6, 11], "streakGold"),
  ...fxGears("T3", [0, 0.8, 0], 0.26, 5, 1, "gear"),
  ...fxMotes("T4", [0, 0.3, 0], 0.3, 26, R * 0.8, "dotSoul"),
];
const SYSTEMS = [
  { id: "cast", role: "static", loop: false, duration: 1.8, layers: CAST },
  { id: "fall", role: "static", loop: false, duration: 1.1, layers: FALL },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "TW_Sand", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 2.6 }, DEMO_BG, { scenarios: {
    fall: sc("모래 낙하", { place: { cast: { start: 99 }, fall: { at: [0, 0, 0], start: 0.05 } }, camera: cam([0, 2, 0], 14, -50, 25) }),
    cast: sc("시전", { place: { cast: { at: [0, 0, 0], start: 0.05 }, fall: { start: 99 } }, camera: cam([0, 5, 0], 16, -60, 15) }),
  } }),
};
