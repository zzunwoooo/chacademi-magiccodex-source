// AS_AshDash — 잿빛 돌진. 모드 예고: 직선 길이 18 · 폭 4. systems: windup (돌진 준비, 보스 발밑 r 2.5, 예고 시작 때) · dash (발동 순간, 원점 = 출발점, +Z 로 18).
const L = 18;
var EXTRA_MESHES = Object.assign({ bandWide: strip(0.3, 0.02), bandCore: strip(0.2, 0.03), bandThin: strip(0.07, 0.04) }, ASH_MESHES);
const WINDUP = [
  ...fxScorch("G1", [0, 0, 0], 0, 4.5, 0.8),
  { name: "G2 RingIn", mat: "ringVio", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], life: 0.55, size: 6, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 1.2], [1, 0.25]], alpha: [[0, 0], [0.2, 0.8], [1, 0]] },
  { name: "G3 RingIn2", mat: "ringOra", mode: "mesh", mesh: "quad", fudge: 29, pos: [0, 0.07, 0], life: 0.45, size: 4.5, rot3: [0, [0, TAU], 0], bursts: [[0.12, 1]],
    sizeLife: [[0, 1.3], [1, 0.2]], alpha: [[0, 0], [0.2, 0.7], [1, 0]] },
  { name: "M1 SuckFlakes", mat: "flake", fudge: 22, pos: [0, 0.8, 0], life: [0.4, 0.55], size: [0.08, 0.18], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "circle", r: 3.2, th: 0.3, y: 0 },
    vel: { rad: [-6, -4], y: [0.5, 1.5], space: "world" }, bursts: [[0, 22], [0.15, 18], [0.3, 14]], alpha: [[0, 0], [0.15, 1], [1, 0]] },
  { name: "M2 SuckMist", mat: "mist", fudge: 35, pos: [0, 0.6, 0], life: [0.45, 0.6], size: [1.5, 2.4], rot: [0, TAU], shape: { type: "circle", r: 2.8, th: 0.3, y: 0 },
    vel: { rad: [-4, -2.5], space: "world" }, bursts: [[0, 8], [0.18, 6]], sizeLife: [[0, 1], [1, 0.5]], alpha: [[0, 0], [0.2, 0.6], [1, 0]], flip: F44 },
  { name: "M3 BackEmbers", mat: "flame", fudge: 0, pos: [0, 5.6, -1.6], life: [0.3, 0.5], size: [0.7, 1.2], rot: [0, TAU], shape: { type: "sphere", r: 2.4, th: 0.5 },
    vel: { y: [0.4, 1.2], space: "world" }, bursts: [[0.05, 6], [0.22, 8], [0.4, 10]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 },
  { name: "T1 InSparks", mat: "streakOra", mode: "stretch", vs: 0.06, ls: 1.4, fudge: -5, pos: [0, 0.5, 0], life: [0.25, 0.4], size: [0.04, 0.08], shape: { type: "circle", r: 2.8, th: 0.2, y: 0 },
    vel: { rad: [-9, -6], space: "world" }, bursts: [[0.1, 14], [0.3, 14]], alpha: [[0, 1], [1, 0]] },
  { name: "T2 Glow", mat: "glowVio", fudge: -2, pos: [0, 1.0, 0], life: 0.6, size: 3.2, bursts: [[0, 1]], sizeLife: [[0, 0.4], [0.8, 1], [1, 0.6]], alpha: [[0, 0], [0.7, 0.35], [1, 0]] },
];
const ALONG = [2, 5, 8, 11, 14, 17];
const DASH = [
  // base: scorched ash band along the path + ember streak band + thin hot core line + scorch/crack decals
  { name: "G01 InkBand", mat: "bandInk", mode: "mesh", mesh: "bandWide", fudge: 46, pos: [0, 0.02, 0], life: 1.2, size: L, bursts: [[0, 1]],
    sizeLife: [[0, 0.05, 0, 8], [0.15, 1]], alpha: [[0, 0], [0.05, 0.9], [0.7, 0.75], [1, 0]] },
  { name: "G02 EmberBand", mat: "band", mode: "mesh", mesh: "bandCore", fudge: 42, pos: [0, 0.04, 0], life: 1.0, size: L, bursts: [[0.02, 1]],
    sizeLife: [[0, 0.05, 0, 8], [0.15, 1]], alpha: [[0, 0], [0.05, 1], [0.5, 0.7], [1, 0]] },
  ...ALONG.filter((_, i) => i % 2 === 0).flatMap((z, i) => fxScorch("G1" + i, [0, 0, z], 0.03 + i * 0.03, 3.6, 1.1)),
  ...fxCrack("G20", [0, 0, L - 0.5], 0.14, 5, 1.1),
  // mid: ash wake billowing along the path (staggered like the boss passing), ink shards thrown sideways, flame tongues
  ...ALONG.map((z, i) => ({ name: "M0" + i + " Wake", mat: i % 2 ? "ashMist" : "mist", fudge: 35, pos: [0, 0.8, z], life: [0.7, 1.0], size: [1.6, 2.6], rot: [0, TAU], rotZ: [-0.6, 0.6],
    shape: { type: "sphere", r: 1.2, th: 0.6 }, vel: { x: [-1.6, 1.6], y: [0.3, 1], z: [1, 3], space: "world" }, drag: 1.5, bursts: [[i * 0.025, 5]],
    sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.65], [1, 0]], flip: F44 })),
  ...ALONG.map((z, i) => ({ name: "M1" + i + " Flames", mat: "flame", fudge: 0, pos: [0, 0.6, z], life: [0.3, 0.5], size: [0.9, 1.5], rot: [0, TAU], rotZ: [-4, 4],
    shape: { type: "sphere", r: 1.0, th: 0.7 }, vel: { y: [0.6, 1.6], z: [2, 4], space: "world" }, bursts: [[0.01 + i * 0.025, 4]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 })),
  ...ALONG.map((z, i) => ({ name: "M2" + i + " SideShards", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.3, fudge: 25, pos: [0, 0.6, z], life: [0.4, 0.7], size: [0.12, 0.24],
    shape: { type: "sphere", r: 0.8, th: 0.5 }, vel: { x: [-7, 7], y: [1, 4], space: "world" }, gravity: 1.4, bursts: [[0.02 + i * 0.025, 4]], alpha: [[0, 1], [0.8, 1], [1, 0]] })),
  { name: "M30 SpeedLines", mat: "streakOra", mode: "stretch", vs: 0.12, ls: 5, fudge: -4, pos: [0, 1.4, 4], life: [0.18, 0.3], size: [0.08, 0.16], shape: { type: "sphere", r: 1.6, th: 0.4 },
    vel: { z: [30, 42], space: "world" }, bursts: [[0, 16], [0.06, 12]], alpha: [[0, 0.9], [1, 0]] },
  // top: end burst (flash, shocks, flames, sparks, rays, flakes) + embers rising along the trail
  ...fxFlash("T1", [0, 1.2, L], 0.15, 3.4),
  ...fxShock("T2", [0, 0, L], 0.15, 9, "shockVio", 0.5),
  ...fxFlames("T3", [0, 0.5, L], 0.15, 12, 1.2),
  ...fxSparks("T4", [0, 0.8, L], 0.15, 24, [6, 12]),
  ...fxRays("T5", [0, 0.8, L], 0.17, 10, [9, 14], "streakOra", 3.5),
  ...fxMist("T6", [0, 0.6, L], 0.15, 9, 1.6),
  ...fxFlakes("T7", [0, 0.6, L / 2], 0.05, 40, 3.2, [1.0, 1.8]),
  { name: "T8 TrailEmbers", mat: "dotFire", fudge: -6, pos: [0, 0.4, L / 2], life: [0.8, 1.5], size: [0.04, 0.09], shape: { type: "sphere", r: 4, th: 0.3 },
    bursts: [[0.04, 45]], vel: { y: [0.4, 1.4], space: "world" }, noise: { s: 0.9, f: 1, sp: 0.6 }, color: [[0, EMBY], [0.5, EMB], [1, CRIM]], alpha: [[0, 0], [0.05, 1], [0.7, 0.9], [1, 0]] },
];
const SYSTEMS = [
  { id: "windup", role: "static", loop: false, duration: 0.8, layers: WINDUP },
  { id: "dash", role: "static", loop: false, duration: 1.3, layers: DASH },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_AshDash", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: L, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 3.0 }, DEMO_BG, { scenarios: {
    dash: sc("돌진", { place: { windup: { at: [0, 0, 0], start: 0.05 }, dash: { at: [0, 0, 0], start: 0.65 } }, targets: [[0, 0, 0, 0], [0.5, 0, 9, 180], [2.5, 0, 14, 200]], camera: cam([0, 1, 9], 22, -55, 35) }),
  } }),
};
