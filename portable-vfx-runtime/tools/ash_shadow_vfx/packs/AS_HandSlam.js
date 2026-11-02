// AS_HandSlam — 그림자 손 내려찍기 (등손, 좌우 번갈아 3~4연타). 모드 예고: 직선 길이 14 · 폭 4. system: slam (발동 순간, 원점 = 보스 발밑, +Z 로 14).
const L = 14, HZ = 9.5;
var EXTRA_MESHES = Object.assign({ armBand: strip(0.2, 0.02), handQ: pillar(0.55) }, ASH_MESHES);
const RING = n => Array.from({ length: n }, (_, k) => { const a = k / n * TAU; return [Math.cos(a) * 2.6, 0.3, HZ + Math.sin(a) * 2.6]; });
const SLAM = [
  // falling hand (fingers down) and its smear just before contact
  { name: "A01 FallingHand", mat: "shadowHand", fudge: 9, pos: [0, 7.5, HZ], life: 0.12, size: 6.5, rot: [Math.PI, Math.PI], bursts: [[0, 1]], vel: { y: -55, space: "world" }, alpha: [[0, 0.9], [1, 1]] },
  { name: "A02 FallingRim", mat: "shadowHandHot", fudge: 8, pos: [0, 7.5, HZ], life: 0.12, size: 6.6, rot: [Math.PI, Math.PI], bursts: [[0, 1]], vel: { y: -55, space: "world" }, alpha: [[0, 0.6], [1, 0.9]] },
  { name: "A03 Smear", mat: "streakCrim", mode: "stretch", vs: 0.1, ls: 3, fudge: -4, pos: [0, 6, HZ], life: [0.12, 0.16], size: [0.25, 0.45], shape: { type: "circle", r: 2.4, th: 0.6, y: 0 },
    vel: { y: -50, space: "world" }, bursts: [[0, 10]], alpha: [[0, 0.8], [1, 0]] },
  // base: hand print slammed into the ground + arm band toward the boss + cracks + scorch
  { name: "G01 HandPrint", mat: "hand", mode: "mesh", mesh: "quad", fudge: 45, pos: [0, 0.04, HZ], life: 1.2, size: 8, rot3: [0, 0, 0], bursts: [[0.1, 1]],
    sizeLife: [[0, 1.25, 0, -2], [0.06, 1], [1, 1]], alpha: [[0, 0], [0.03, 1], [0.7, 0.9], [1, 0]] },
  { name: "G02 PrintRim", mat: "handHot", mode: "mesh", mesh: "quad", fudge: 41, pos: [0, 0.06, HZ], life: 0.8, size: 8, rot3: [0, 0, 0], bursts: [[0.1, 1]],
    sizeLife: [[0, 1.25, 0, -2], [0.08, 1], [1, 1.02]], alpha: [[0, 0], [0.04, 1], [0.4, 0.6], [1, 0]] },
  { name: "G03 ArmBand", mat: "bandInk", mode: "mesh", mesh: "armBand", fudge: 46, pos: [0, 0.02, 0], life: 1.0, size: L * 0.75, bursts: [[0.1, 1]],
    sizeLife: [[0, 0.2, 0, 6], [0.12, 1]], alpha: [[0, 0], [0.05, 0.8], [0.7, 0.6], [1, 0]] },
  ...fxCrack("G04", [0, 0, HZ], 0.1, 9, 1.2), ...fxCrack("G05", [0, 0, 4.5], 0.14, 5, 1.0),
  ...fxScorch("G06", [0, 0, HZ], 0.1, 7.5, 1.2),
  // mid: impact — shocks (dark + ember), ring of flame tongues around the print, dirt/ash shards thrown up, ash mist
  ...fxShock("M01", [0, 0, HZ], 0.1, 10, "shockOra", 0.5),
  { name: "M03 InkShock", mat: "shockInk", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.05, HZ], life: 0.6, size: 12, rot3: [0, [0, TAU], 0], bursts: [[0.12, 1]],
    sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 0.7], [1, 0]] },
  ...RING(8).map((p, i) => ({ name: "M1" + i + " Tongues", mat: "flame", fudge: 0, pos: p, life: [0.35, 0.6], size: [1.0, 1.7], rot: [0, TAU], rotZ: [-4, 4],
    shape: { type: "sphere", r: 0.5, th: 1 }, vel: { y: [1.2, 2.6], space: "world" }, bursts: [[0.1 + (i % 3) * 0.02, 3]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 })),
  { name: "M20 Debris", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.3, fudge: 25, pos: [0, 0.3, HZ], life: [0.5, 0.9], size: [0.15, 0.3], shape: { type: "circle", r: 3, th: 0.6, y: 0 },
    speed: [2, 5], vel: { y: [5, 9], space: "world" }, gravity: 1.6, bursts: [[0.1, 24]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ...fxMist("M21", [0, 0.4, HZ], 0.11, 12, 3, [1.8, 3.0], [1.5, 3]),
  // top: flash, sparks, rays up, embers + ash flakes
  ...fxFlash("T1", [0, 0.8, HZ], 0.1, 4.2),
  ...fxSparks("T2", [0, 0.6, HZ], 0.1, 34, [7, 14], "streakOra", 2.4),
  { name: "T3 RaysUp", mat: "streakOra", mode: "stretch", vs: 0.05, ls: 4, fudge: -6, pos: [0, 0.3, HZ], life: [0.22, 0.34], size: [0.12, 0.22], shape: { type: "circle", r: 2.5, th: 0.5, y: 0 },
    vel: { y: [10, 16], space: "world" }, bursts: [[0.11, 12]], color: [[0, EMBY], [1, EMB]], alpha: [[0, 0.9], [1, 0]] },
  ...fxEmbers("T4", [0, 0.8, HZ], 0.12, 40, 3),
  ...fxFlakes("T5", [0, 0.6, HZ], 0.14, 30, 3, [1.0, 1.7]),
];
const SYSTEMS = [{ id: "slam", role: "static", loop: false, duration: 1.4, layers: SLAM }];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_HandSlam", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: L, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 2.6 }, DEMO_BG, { scenarios: {
    slam: sc("내려찍기", { place: { slam: { at: [0, 0, 0], start: 0.05 } }, targets: [[0, 0, 0, 0], [0.5, 0, 9, 180], [1.5, 0, 12, 200]], camera: cam([0, 1, 8], 18, -55, 35) }),
  } }),
};
