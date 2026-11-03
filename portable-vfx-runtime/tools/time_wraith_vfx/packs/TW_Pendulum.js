// TW_Pendulum — 진자 휘두르기. systems: inner (static 원 r 6, 1.1초: 진자 원반이 한 바퀴 휩쓸고 지나감) · outer (static 도넛 r 6~12 (기준 12), 1.2초: 바깥 띠에서 모래·청록 기둥)
const R = 6, RO = 12;
var EXTRA_MESHES = Object.assign({ outerBand: ringStrip(0.75, 0.5, 0.02, 128), innerBand: ringStrip(0.92, 0.12, 0.03) }, TIME_MESHES);
const DISC = n => Array.from({ length: n }, (_, k) => { const a = k / n * TAU; return [Math.sin(a) * R * 0.75, 1.2, Math.cos(a) * R * 0.75]; });
const INNER = [
  ...clockCircle("B0", R, [0, 0, 0], 0, 1.05, "Gold", 0.05),
  // 진자 원반: 금빛 문자판이 원을 그리며 한 바퀴 (0~0.35초) + 꼬리 휩쓸기
  { name: "M1 Disc", mat: "dialGold", fudge: 5, pos: [0, 1.6, 0], life: 0.38, size: 2.4, shape: { type: "circle", r: R * 0.75, th: 0, y: 0 }, bursts: [[0, 1]], vel: { orb: [16, 16] },
    rot: [0, TAU], rotZ: [8, 8], alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "M2 DiscTrail", mat: "streakGold", mode: "stretch", vs: 0.12, ls: 3, fudge: -5, pos: [0, 1.5, 0], life: [0.25, 0.35], size: [0.2, 0.35], shape: { type: "circle", r: R * 0.75, th: 0.1, y: 0 },
    bursts: [[0.02, 14], [0.12, 14], [0.22, 14]], vel: { orb: [14, 16] }, alpha: [[0, 0.9], [1, 0]] },
  { name: "M3 Ring", mat: "sweepGold", mode: "mesh", mesh: "innerBand", fudge: 12, pos: [0, 0.4, 0], life: 0.45, size: R, rotLife: [0, -4, 0], bursts: [[0, 1]], flip: true },
  ...fxShockT("M4", [0, 0, 0], 0.05, 2 * R + 1, 0.5, 0.6, "shockGold"),
  ...DISC(8).map((p, n) => ({ name: "M5" + n + " Sand", mat: "sand", fudge: -4, pos: p, life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: "sphere", r: 0.6, th: 1 },
    speed: [2, 5], gravity: 1.3, vel: { y: [1, 3], space: "world" }, bursts: [[0.02 + n * 0.045, 12]], alpha: [[0, 1], [0.8, 1], [1, 0]] })),
  ...fxRipple("G1", [0, 0, 0], 0.05, 2 * R, 0.8, "rippleGold", 0.6),
  ...fxMist("G2", [0, 0.3, 0], 0.05, 10, R * 0.6, [1.4, 2.4], [1, 2]),
  ...fxFlashT("T1", [0, 1.2, 0], 0.05, 3, "starGold", "glowGold"),
  ...fxGears("T2", [0, 1, 0], 0.1, 6, R * 0.6, "gear"),
];
const OUTER = [
  { name: "B1 Band", mat: "rippleGold", mode: "mesh", mesh: "outerBand", fudge: 44, pos: [0, 0.03, 0], life: 1.0, size: RO, rotLife: [0, 0.6, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.95, 0, 1], [0.2, 1]], alpha: [[0, 0], [0.05, 0.7], [0.7, 0.5], [1, 0]] },
  { name: "B2 RuneOut", mat: "runeSoul", mode: "mesh", mesh: "mcRunesOut", fudge: 50, pos: [0, 0.05, 0], life: 1.0, size: RO, rotLife: [0, 0.3, 0], bursts: [[0, 1]], alpha: [[0, 0], [0.1, 0.9], [0.8, 0.8], [1, 0]] },
  { name: "B3 Ring1", mat: "lineSoul", mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: 1.0, size: RO, bursts: [[0, 1]], alpha: [[0, 0], [0.1, 0.9], [0.8, 0.8], [1, 0]] },
  { name: "B4 RingIn", mat: "lineSoul", mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: 1.0, size: R, bursts: [[0, 1]], alpha: [[0, 0], [0.1, 0.9], [0.8, 0.8], [1, 0]] },
  { name: "M1 Pillars", mat: "pillarSoul", mode: "mesh", mesh: "pillar", fudge: 5, life: [0.35, 0.55], size: 4, shape: { type: "circle", r: (R + RO) / 2, th: 0.6, y: 0 }, rot3: [0, [0, TAU], 0],
    bursts: [[0.02, 14], [0.12, 14]], sizeLife: [[0, 0.2, 0, 4], [0.3, 1], [1, 1.05]], alpha: [[0, 0], [0.1, 1], [0.5, 0.7], [1, 0]] },
  { name: "M2 SandUp", mat: "sand", fudge: -4, life: [0.6, 1.0], size: [0.05, 0.11], shape: { type: "circle", r: (R + RO) / 2, th: 0.6, y: 0.2 }, bursts: [[0.02, 120]],
    vel: { y: [4, 8], rad: [1, 2], space: "world" }, gravity: 1.5, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M3 Mist", mat: "mist", fudge: 35, life: [0.9, 1.3], size: [1.8, 3], shape: { type: "circle", r: (R + RO) / 2, th: 0.5, y: 0.3 }, bursts: [[0.03, 22]],
    vel: { y: [0.6, 1.4], rad: [0.5, 1.2], space: "world" }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.6], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
  ...fxShockT("M4", [0, 0, 0], 0.03, 2 * RO + 2, 0.6, 0.5),
  { name: "T1 Sparks", mat: "streakSoul", mode: "stretch", vs: 0.07, ls: 1.8, fudge: -5, life: [0.3, 0.5], size: [0.05, 0.1], shape: { type: "circle", r: (R + RO) / 2, th: 0.6, y: 0.4 },
    vel: { y: [5, 10], space: "world" }, bursts: [[0.03, 50]], gravity: 0.7, alpha: [[0, 1], [1, 0]] },
  { name: "T2 Gears", mat: "gearTeal", fudge: 6, life: [0.6, 1.0], size: [0.35, 0.6], shape: { type: "circle", r: (R + RO) / 2, th: 0.6, y: 0.5 }, bursts: [[0.05, 10]],
    vel: { y: [3, 6], space: "world" }, gravity: 0.8, rot: [0, TAU], rotZ: [-8, 8], alpha: [[0, 1], [0.75, 1], [1, 0]] },
];
const SYSTEMS = [
  { id: "inner", role: "static", loop: false, duration: 1.1, layers: INNER },
  { id: "outer", role: "static", loop: false, duration: 1.2, layers: OUTER },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "TW_Pendulum", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: RO, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 3.0 }, DEMO_BG, { scenarios: {
    both: sc("안쪽 → 바깥", { place: { inner: { at: [0, 0, 0], start: 0.05 }, outer: { at: [0, 0, 0], start: 1.0 } }, camera: cam([0, 1, 0], 28, -40, 40) }),
  } }),
};
