// AS_CremationRing — 화장의 고리. systems: inner (원 반경 5, 1.0초) · outer (도넛 안 5 · 밖 12, 1.0초). 기준 12 — 서버는 둘 다 배율 1로 재생.
const RO = 12, RI = 5;
var EXTRA_MESHES = Object.assign({ ringIn: ringStrip(1, 0.04), ringOut: ringStrip(1, 0.025), donutBand: ringStrip((RO + RI) / 2 / RO, (RO - RI) / RO, 0.02, 128) }, ASH_MESHES);
const INNER = [
  ...fxScorch("G1", [0, 0, 0], 0, 2 * RI, 1.0), ...fxCrack("G2", [0, 0, 0], 0.02, 2 * RI * 0.8, 1.0),
  { name: "G3 EdgeLine", mat: "lineOra", mode: "mesh", mesh: "ringIn", fudge: 40, pos: [0, 0.06, 0], life: 0.8, size: RI, bursts: [[0, 1]], sizeLife: [[0, 0.9], [0.2, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "G4 Runes", mat: "runeOra", mode: "mesh", mesh: "mcRunesOut", fudge: 41, pos: [0, 0.07, 0], life: 0.8, size: RI, rotLife: [0, 1.5, 0], bursts: [[0, 1]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  ...fxPillars("M1", [0, 0, 0], 0.0, 10, RI * 0.8, 5),
  ...fxPillars("M2", [0, 0, 0], 0.12, 6, RI * 0.5, 4),
  { name: "M3 Flames", mat: "flame", fudge: 0, pos: [0, 0.3, 0], life: [0.4, 0.65], size: [1.1, 1.9], rot: [0, TAU], shape: { type: "circle", r: RI, th: 1, y: 0 },
    vel: { y: [1.5, 3.5], space: "world" }, bursts: [[0, 26], [0.15, 14]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 },
  ...fxShock("M4", [0, 0, 0], 0, 2 * RI + 1, "shockOra", 0.45),
  ...fxFlash("T1", [0, 1, 0], 0, 4),
  ...fxSparks("T2", [0, 0.8, 0], 0.02, 30, [6, 12], "streakOra", 2.5),
  ...fxEmbers("T3", [0, 1, 0], 0.05, 40, RI * 0.8),
  ...fxFlakes("T4", [0, 1, 0], 0.08, 26, RI * 0.8),
];
const OUTER = [
  { name: "G1 BandInk", mat: "bandInk", mode: "mesh", mesh: "donutBand", fudge: 46, pos: [0, 0.02, 0], life: 1.0, size: RO, rotLife: [0, -0.3, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.9], [0.15, 1]], alpha: [[0, 0], [0.05, 0.85], [0.7, 0.7], [1, 0]] },
  { name: "G2 Band", mat: "band", mode: "mesh", mesh: "donutBand", fudge: 42, pos: [0, 0.04, 0], life: 0.8, size: RO, rotLife: [0, 0.4, 0], bursts: [[0.02, 1]],
    sizeLife: [[0, 0.9], [0.15, 1]], alpha: [[0, 0], [0.05, 0.9], [0.6, 0.6], [1, 0]] },
  { name: "G3 LineIn", mat: "lineOra", mode: "mesh", mesh: "ringOut", fudge: 40, pos: [0, 0.06, 0], life: 0.8, size: RI, bursts: [[0, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "G4 LineOut", mat: "lineOra", mode: "mesh", mesh: "ringOut", fudge: 40, pos: [0, 0.06, 0], life: 0.8, size: RO, bursts: [[0, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "M1 Pillars", mat: "pillarOra", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: [0.35, 0.55], size: 5, shape: { type: "circle", r: (RO + RI) / 2, th: 0.3, y: 0 }, rot3: [0, [0, TAU], 0],
    bursts: [[0, 16], [0.12, 12]], sizeLife: [[0, 0.2, 0, 4], [0.3, 1], [1, 1.05]], alpha: [[0, 0], [0.1, 1], [0.5, 0.7], [1, 0]] },
  { name: "M2 Flames", mat: "flame", fudge: 0, pos: [0, 0.3, 0], life: [0.4, 0.65], size: [1.2, 2.0], rot: [0, TAU], shape: { type: "circle", r: (RO + RI) / 2, th: 0.35, y: 0 },
    vel: { y: [1.5, 3.5], space: "world" }, bursts: [[0, 50], [0.15, 30]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 },
  { name: "M3 Mist", mat: "mist", fudge: 35, pos: [0, 0.6, 0], life: [0.8, 1.1], size: [1.8, 2.8], rot: [0, TAU], shape: { type: "circle", r: (RO + RI) / 2, th: 0.4, y: 0 },
    vel: { rad: [1, 2.5], y: [0.3, 1], space: "world" }, drag: 1.2, bursts: [[0.05, 22]], sizeLife: [[0, 0.6], [1, 1.2]], alpha: [[0, 0], [0.2, 0.6], [1, 0]], flip: F44 },
  { name: "M4 ShockOut", mat: "ringOra", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.08, 0], life: 0.5, size: 2 * RO + 2, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.42, 0, 2], [1, 1]], alpha: [[0, 0.8], [1, 0]] },
  { name: "T1 Sparks", mat: "streakOra", mode: "stretch", vs: 0.07, ls: 1.6, fudge: -5, pos: [0, 0.6, 0], life: [0.3, 0.55], size: [0.05, 0.1], shape: { type: "circle", r: (RO + RI) / 2, th: 0.35, y: 0 },
    speed: [3, 7], vel: { y: [4, 8], space: "world" }, gravity: 1, bursts: [[0.02, 50]], alpha: [[0, 1], [1, 0]] },
  { name: "T2 Embers", mat: "dotFire", fudge: -6, pos: [0, 0.6, 0], life: [0.8, 1.4], size: [0.05, 0.1], shape: { type: "circle", r: (RO + RI) / 2, th: 0.4, y: 0 },
    vel: { y: [0.6, 1.8], space: "world" }, noise: { s: 0.8, f: 1, sp: 0.6 }, bursts: [[0.05, 60]], color: [[0, EMBY], [0.5, EMB], [1, CRIM]], alpha: [[0, 0], [0.05, 1], [1, 0]] },
  { name: "T3 Flakes", mat: "flake", fudge: 22, pos: [0, 0.8, 0], life: [1, 1.6], size: [0.07, 0.16], rot: [0, TAU], rotZ: [-3, 3], shape: { type: "circle", r: (RO + RI) / 2, th: 0.4, y: 0 },
    vel: { y: [0.4, 1.4], space: "world" }, noise: { s: 0.8, f: 1.2, sp: 0.7 }, bursts: [[0.06, 40]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
];
const SYSTEMS = [
  { id: "inner", role: "static", loop: false, duration: 1.1, layers: INNER },
  { id: "outer", role: "static", loop: false, duration: 1.2, layers: OUTER },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_CremationRing", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: RO, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 3.0 }, DEMO_BG, { scenarios: {
    ring: sc("안→밖", { place: { inner: { at: [0, 0, 0], start: 0.05 }, outer: { at: [0, 0, 0], start: 1.25 } }, targets: [[0, 0, 0, 0], [2, 0, 2, 180], [7, 0, 6, 200]], camera: cam([0, 1, 0], 30, -60, 40) }),
  } }),
};
