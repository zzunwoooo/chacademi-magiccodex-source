// AS_ShadowSink — 그림자 잠행 / 솟구침. systems: sink (보스 발밑 r 3, 1.0초: 먹물 웅덩이로 녹아듦) · emerge (원 반경 5, 1.4초: 솟구침, 0.4초에 바깥 충격 링 r 8).
const R = 5;
var EXTRA_MESHES = Object.assign({ handQ: pillar(0.6) }, ASH_MESHES);
const SINK = [
  { name: "G1 Pool", mat: "pool", mode: "mesh", mesh: "quad", fudge: 46, pos: [0, 0.03, 0], life: 1.0, size: 6.5, rot3: [0, [0, TAU], 0], rotLife: [0, 0.6, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.3, 0, 3], [0.35, 1], [0.85, 1], [1, 0.7]], alpha: [[0, 0], [0.1, 1], [0.8, 1], [1, 0]] },
  { name: "G2 RimIn", mat: "ringVio", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], life: 0.9, size: 7, rot3: [0, [0, TAU], 0], bursts: [[0.05, 1]],
    sizeLife: [[0, 1.1], [1, 0.35]], alpha: [[0, 0], [0.2, 0.8], [1, 0]] },
  { name: "M1 Hands", mat: "shadowHand", mode: "mesh", mesh: "handQ", fudge: 10, pos: [0, -0.2, 0], life: [0.5, 0.8], size: [1.4, 2.4], shape: { type: "circle", r: 2.0, th: 0.8, y: 0 },
    rot3: [[-0.4, 0.4], [0, TAU], [-0.3, 0.3]], rotLife: [[-1.2, 1.2], 0, 0], bursts: [[0.08, 3], [0.2, 3], [0.35, 3]], sizeLife: [[0, 0.1, 0, 5], [0.25, 1], [0.6, 0.9], [1, 0]] },
  { name: "M2 HandsRim", mat: "shadowHandHot", mode: "mesh", mesh: "handQ", fudge: 9, pos: [0, -0.2, 0], life: [0.5, 0.7], size: [1.4, 2.2], shape: { type: "circle", r: 2.0, th: 0.8, y: 0 },
    rot3: [[-0.4, 0.4], [0, TAU], [-0.3, 0.3]], bursts: [[0.12, 3], [0.28, 2]], sizeLife: [[0, 0.1, 0, 5], [0.25, 1], [1, 0]], alpha: [[0, 0.7], [1, 0]] },
  { name: "M3 SinkMist", mat: "mist", fudge: 35, pos: [0, 1.6, 0], life: [0.6, 0.9], size: [1.8, 3.0], rot: [0, TAU], shape: { type: "sphere", r: 1.8, th: 0.6 },
    vel: { y: [-3, -1.5], rad: [-1.5, -0.5], space: "world" }, bursts: [[0, 8], [0.15, 6]], sizeLife: [[0, 1], [1, 0.4]], alpha: [[0, 0], [0.2, 0.7], [1, 0]], flip: F44 },
  { name: "T1 FlakesDown", mat: "flake", fudge: 22, pos: [0, 4, 0], life: [0.5, 0.8], size: [0.07, 0.16], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "sphere", r: 2.5, th: 0.6 },
    vel: { y: [-7, -4], rad: [-2, -1], space: "world" }, bursts: [[0, 24], [0.2, 18]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  { name: "T2 Embers", mat: "dotFire", fudge: -6, pos: [0, 0.2, 0], life: [0.4, 0.8], size: [0.04, 0.08], shape: { type: "circle", r: 3, th: 0.2, y: 0 },
    vel: { rad: [-3, -2], y: [0.2, 0.8], space: "world" }, bursts: [[0.05, 26]], color: [[0, EMBY], [1, CRIM]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  { name: "T3 Glow", mat: "glowVio", fudge: -2, pos: [0, 0.4, 0], life: 0.8, size: 4, bursts: [[0, 1]], sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 0.35], [1, 0]] },
];
const EMERGE = [
  // base: pool bursts open, cracks, scorch
  { name: "G1 Pool", mat: "pool", mode: "mesh", mesh: "quad", fudge: 46, pos: [0, 0.03, 0], life: 1.2, size: 2 * R, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.4, 0, 4], [0.12, 1], [1, 1.05]], alpha: [[0, 0], [0.05, 1], [0.7, 0.8], [1, 0]] },
  ...fxCrack("G2", [0, 0, 0], 0.02, 2 * R, 1.3), ...fxScorch("G3", [0, 0, 0], 0.02, 2 * R + 1, 1.3),
  // mid: dark column + ember column, shadow hands bursting outward, ink shock + outer shock to r 8 at 0.4 s
  { name: "M01 InkColumn", mat: "mist", fudge: 34, pos: [0, 0.5, 0], life: [0.5, 0.8], size: [2.2, 3.4], rot: [0, TAU], shape: { type: "circle", r: 1.6, th: 0.5, y: 0 },
    vel: { y: [6, 11], space: "world" }, drag: 2, bursts: [[0, 14]], sizeLife: [[0, 0.6], [1, 1.4]], alpha: [[0, 0], [0.1, 0.8], [1, 0]], flip: F44 },
  { name: "M02 EmberColumn", mat: "pillarOra", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.45, size: 8, rot3: [0, [0, TAU], 0], bursts: [[0, 1], [0.05, 1]],
    sizeLife: [[0, 0.2, 0, 4], [0.3, 1], [1, 1.05]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  { name: "M03 Hands", mat: "shadowHand", mode: "mesh", mesh: "handQ", fudge: 10, pos: [0, -0.3, 0], life: [0.6, 0.9], size: [2.2, 3.4], shape: { type: "circle", r: 2.6, th: 0.5, y: 0 },
    rot3: [[0.3, 0.7], [0, TAU], 0], rotLife: [[0.6, 1.0], 0, 0], bursts: [[0.02, 7]], sizeLife: [[0, 0.1, 0, 6], [0.2, 1], [0.6, 1], [1, 0]] },
  { name: "M04 HandsRim", mat: "shadowHandHot", mode: "mesh", mesh: "handQ", fudge: 9, pos: [0, -0.3, 0], life: [0.4, 0.6], size: [2.2, 3.2], shape: { type: "circle", r: 2.6, th: 0.5, y: 0 },
    rot3: [[0.3, 0.7], [0, TAU], 0], bursts: [[0.03, 5]], sizeLife: [[0, 0.1, 0, 6], [0.25, 1], [1, 0]], alpha: [[0, 0.8], [1, 0]] },
  ...fxShock("M05", [0, 0, 0], 0.0, 2 * R + 1, "shockOra", 0.45),
  { name: "M07 OuterShock", mat: "ringOra", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.08, 0], life: 0.5, size: 16, rot3: [0, [0, TAU], 0], bursts: [[0.4, 1]],
    sizeLife: [[0, 0.55, 0, 2], [1, 1]], alpha: [[0, 0.85], [0.6, 0.6], [1, 0]] },
  { name: "M08 OuterInk", mat: "shockInk", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.05, 0], life: 0.6, size: 17, rot3: [0, [0, TAU], 0], bursts: [[0.4, 1]],
    sizeLife: [[0, 0.55, 0, 2], [1, 1]], alpha: [[0, 0.6], [1, 0]] },
  { name: "M09 OuterFlames", mat: "flame", fudge: 0, pos: [0, 0.3, 0], life: [0.35, 0.55], size: [1.0, 1.6], rot: [0, TAU], shape: { type: "circle", r: 7, th: 0.12, y: 0 },
    vel: { y: [1, 2.5], space: "world" }, bursts: [[0.42, 18]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 },
  // top
  ...fxFlash("T1", [0, 1.2, 0], 0.0, 4.4),
  ...fxSparks("T2", [0, 0.8, 0], 0.0, 30, [7, 13]),
  ...fxShards("T3", [0, 0.8, 0], 0.02, 20, 1.6, [4, 8]),
  ...fxFlakes("T4", [0, 1.0, 0], 0.05, 34, 3.2),
  ...fxEmbers("T5", [0, 1.0, 0], 0.05, 40, 4),
];
const SYSTEMS = [
  { id: "sink", role: "static", loop: false, duration: 1.1, layers: SINK },
  { id: "emerge", role: "static", loop: false, duration: 1.5, layers: EMERGE },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_ShadowSink", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 3.4 }, DEMO_BG, { scenarios: {
    both: sc("잠행→솟구침", { place: { sink: { at: [0, 0, 0], start: 0.05 }, emerge: { at: [0, 0, 7], start: 1.3 } }, targets: [[0, 0, 0, 0], [0.5, 0, 7.5, 180]], camera: cam([0, 1.5, 4], 18, -60, 30) }),
  } }),
};
