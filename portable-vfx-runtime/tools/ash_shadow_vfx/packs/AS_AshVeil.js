// AS_AshVeil — 재의 장막. systems: wrap (보스 r 3, 1.2초: 등손이 감싸며 재 소용돌이) · burst (원 반경 7, 1.0초: 재 장막 폭발 — 서버가 2번 재생).
const R = 7;
var EXTRA_MESHES = Object.assign({}, ASH_MESHES);
const WRAP = [
  ...fxScorch("G1", [0, 0, 0], 0, 6, 1.2),
  { name: "G2 RingIn", mat: "ringVio", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], life: 1.1, size: 8, rot3: [0, [0, TAU], 0], rotLife: [0, 2, 0], bursts: [[0, 1]],
    sizeLife: [[0, 1.1], [1, 0.4]], alpha: [[0, 0], [0.2, 0.75], [0.9, 0.6], [1, 0]] },
  { name: "M1 Vortex", mat: "mist", fudge: 35, pos: [0, 0.5, 0], life: [0.8, 1.1], size: [1.8, 2.8], rot: [0, TAU], shape: { type: "circle", r: 3.5, th: 0.3, y: 0 },
    vel: { orb: [5, 7], rad: [-2.5, -1.5], y: [1.5, 3.5], space: "world" }, rate: 16, sizeLife: [[0, 0.7], [1, 1.1]], alpha: [[0, 0], [0.2, 0.75], [1, 0]], flip: F44 },
  { name: "M2 VortexPale", mat: "ashMist", fudge: 36, pos: [0, 1.5, 0], life: [0.7, 1.0], size: [1.4, 2.2], rot: [0, TAU], shape: { type: "circle", r: 3, th: 0.3, y: 0 },
    vel: { orb: [6, 8], rad: [-2, -1], y: [2, 4], space: "world" }, rate: 10, alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: F44 },
  { name: "M3 FlakesSpiral", mat: "flake", fudge: 22, pos: [0, 1, 0], life: [0.7, 1.1], size: [0.07, 0.16], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "circle", r: 3.6, th: 0.4, y: 0 },
    vel: { orb: [7, 9], rad: [-2.5, -1.5], y: [2, 5], space: "world" }, rate: 60, alpha: [[0, 0], [0.1, 1], [1, 0]] },
  { name: "T1 EmberSpiral", mat: "dotFire", fudge: -6, pos: [0, 0.6, 0], life: [0.6, 1.0], size: [0.05, 0.09], shape: { type: "circle", r: 3.2, th: 0.2, y: 0 },
    vel: { orb: [7, 9], rad: [-2, -1], y: [2, 5], space: "world" }, rate: 45, color: [[0, EMBY], [1, CRIM]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  { name: "T2 Glow", mat: "glowVio", fudge: -2, pos: [0, 3, 0], life: 1.2, size: 3.5, bursts: [[0, 1]], sizeLife: [[0, 1.2], [1, 0.5]], alpha: [[0, 0], [0.5, 0.2], [1, 0]] },
];
const BURST = [
  ...fxScorch("G1", [0, 0, 0], 0, 2 * R, 1.0), ...fxCrack("G2", [0, 0, 0], 0.02, 2 * R * 0.8, 1.0),
  { name: "G3 InkShock", mat: "shockInk", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.05, 0], life: 0.55, size: 2 * R + 2, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 0.75], [1, 0]] },
  ...fxShock("M1", [0, 0, 0], 0.02, 2 * R + 1, "shockOra", 0.45),
  { name: "M3 Dome", mat: "mist", fudge: 34, pos: [0, 1.2, 0], life: [0.6, 0.9], size: [2.4, 3.6], rot: [0, TAU], shape: { type: "sphere", r: 1.5, th: 0.5 },
    speed: [8, 12], drag: 3, vel: { y: [0.5, 2], space: "world" }, bursts: [[0, 22]], sizeLife: [[0, 0.6], [1, 1.4]], alpha: [[0, 0], [0.08, 0.85], [1, 0]], flip: F44 },
  { name: "M4 DomePale", mat: "ashMist", fudge: 35, pos: [0, 1.6, 0], life: [0.5, 0.8], size: [2, 3], rot: [0, TAU], shape: { type: "sphere", r: 1.5, th: 0.5 },
    speed: [9, 13], drag: 3, bursts: [[0.02, 12]], sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.1, 0.55], [1, 0]], flip: F44 },
  { name: "M5 EdgeFlames", mat: "flame", fudge: 0, pos: [0, 0.3, 0], life: [0.35, 0.55], size: [1.0, 1.6], rot: [0, TAU], shape: { type: "circle", r: R - 0.5, th: 0.1, y: 0 },
    vel: { y: [1, 2.4], space: "world" }, bursts: [[0.08, 22]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 },
  ...fxFlash("T1", [0, 1.5, 0], 0, 4.5),
  { name: "T2 FlakesOut", mat: "flake", fudge: 22, pos: [0, 1.5, 0], life: [0.6, 1.0], size: [0.07, 0.16], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "sphere", r: 1.2, th: 0.5 },
    speed: [8, 14], drag: 2.5, vel: { y: [0.5, 2], space: "world" }, bursts: [[0, 60]], alpha: [[0, 1], [0.7, 0.9], [1, 0]] },
  ...fxSparks("T3", [0, 1.0, 0], 0.01, 30, [8, 14]),
  ...fxRays("T4", [0, 1.0, 0], 0.02, 14, [10, 15], "streakOra", 4),
  ...fxEmbers("T5", [0, 1, 0], 0.05, 30, 5),
];
const SYSTEMS = [
  { id: "wrap", role: "static", loop: false, duration: 1.3, layers: WRAP },
  { id: "burst", role: "static", loop: false, duration: 1.1, layers: BURST },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_AshVeil", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 3.4 }, DEMO_BG, { scenarios: {
    veil: sc("장막", { place: { wrap: { at: [0, 0, 0], start: 0.05 }, burst: { at: [0, 0, 6], start: 1.3 } }, targets: [[0, 0, 0, 0], [0.5, 0, 6, 180]], camera: cam([0, 1.5, 3], 22, -60, 32) }),
  } }),
};
