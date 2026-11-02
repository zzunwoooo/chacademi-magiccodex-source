// AS_ClawSwipe — 기본 공격 앞팔 할퀴기. 모드 예고: 부채꼴 150° 반경 6. system: swipe (static, 발동 순간 재생, 원점 = 보스 발밑, +Z = 정면).
const R = 6;
var EXTRA_MESHES = Object.assign({
  sweepHi: arc(80, -70, 0.62, 0, 0.6), sweepMid: arc(85, -75, 0.7, 0, 0.6), sweepLo: arc(78, -78, 0.78, 0, 0.6),
  groundArc: arc(75, -75, 0.55, 0.02 / R), groundArc2: arc(70, -70, 0.78, 0.025 / R),
}, ASH_MESHES);
const ARCPT = a => [Math.sin(a * D2R) * 4.2, 1.4, Math.cos(a * D2R) * 4.2];
const PTS = [60, 30, 0, -30, -60].map(ARCPT);
const SWIPE = [
  // base: scorched claw scars on the ground following the sweep + cracks + low ash smoke
  { name: "G01 ScarInk", mat: "clawInk", mode: "mesh", mesh: "groundArc", fudge: 45, pos: [0, 0.03, 0], life: 0.9, size: R, bursts: [[0.04, 1]], flip: true },
  { name: "G02 ScarHot", mat: "clawCrim", mode: "mesh", mesh: "groundArc2", fudge: 41, pos: [0, 0.05, 0], life: 0.75, size: R * 0.95, bursts: [[0.06, 1]], flip: true },
  ...fxScorch("G03", [0, 0, 3.4], 0.05, 4.6, 1.0),
  ...fxCrack("G04", [0, 0, 3.6], 0.08, 4.2, 0.9),
  ...fxMist("G05", [0, 0.3, 3.2], 0.06, 8, 2.4, [1.4, 2.4], [0.6, 1.4]),
  // mid: three claw strokes (dark ink body behind, hot ember edges), stacked high→low like a downward rake
  { name: "M01 InkHi", mat: "clawInk", mode: "mesh", mesh: "sweepHi", fudge: 30, pos: [0, 2.7, 0], life: 0.42, size: R * 1.02, rot3: [-10 * D2R, 0, 0], bursts: [[0, 1]], flip: true },
  { name: "M02 ClawHi", mat: "claw", mode: "mesh", mesh: "sweepHi", fudge: 10, pos: [0, 2.75, 0], life: 0.36, size: R, rot3: [-10 * D2R, 0, 0], bursts: [[0, 1]], flip: true },
  { name: "M03 InkMid", mat: "clawInk", mode: "mesh", mesh: "sweepMid", fudge: 31, pos: [0, 1.85, 0], life: 0.42, size: R * 1.0, rot3: [-6 * D2R, 0, 0], bursts: [[0.035, 1]], flip: true },
  { name: "M04 ClawMid", mat: "clawHot", mode: "mesh", mesh: "sweepMid", fudge: 11, pos: [0, 1.9, 0], life: 0.36, size: R * 0.98, rot3: [-6 * D2R, 0, 0], bursts: [[0.035, 1]], flip: true },
  { name: "M05 InkLo", mat: "clawInk", mode: "mesh", mesh: "sweepLo", fudge: 32, pos: [0, 1.05, 0], life: 0.42, size: R * 0.98, rot3: [-3 * D2R, 0, 0], bursts: [[0.07, 1]], flip: true },
  { name: "M06 ClawLo", mat: "claw", mode: "mesh", mesh: "sweepLo", fudge: 12, pos: [0, 1.1, 0], life: 0.36, size: R * 0.96, rot3: [-3 * D2R, 0, 0], bursts: [[0.07, 1]], flip: true },
  { name: "M07 CrimEcho", mat: "clawCrim", mode: "mesh", mesh: "sweepMid", fudge: 13, pos: [0, 2.2, 0], life: 0.55, size: R * 1.12, rot3: [-8 * D2R, 0, 0], bursts: [[0.09, 1]], flip: true, alpha: [[0, 0.6], [1, 0.6]] },
  { name: "M08 GlowSweep", mat: "clawCrim", mode: "mesh", mesh: "sweepMid", fudge: 14, pos: [0, 1.9, 0], life: 0.5, size: R * 0.9, rot3: [-6 * D2R, 0, 0], bursts: [[0.0, 1]], flip: true, alpha: [[0, 0.5], [1, 0.5]] },
  ...fxShock("M09", [0, 0, 1.5], 0.06, 7, "shockVio", 0.45),
  // top: ember flames popping along the sweep, sparks, rays, shards, ash flakes, embers
  ...PTS.map((p, n) => ({ name: "T0" + n + " ArcFlames", mat: "flame", fudge: 0, pos: p, life: [0.3, 0.5], size: [0.8, 1.4], rot: [0, TAU], rotZ: [-4, 4],
    shape: { type: "sphere", r: 0.5, th: 1 }, speed: [1, 3], drag: 4, vel: { y: [0.5, 1.3], space: "world" }, bursts: [[0.03 + n * 0.04, 4]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 })),
  ...PTS.map((p, n) => ({ name: "T1" + n + " Sparks", mat: "streakOra", mode: "stretch", vs: 0.07, ls: 1.6, fudge: -5, pos: p, life: [0.25, 0.5], size: [0.05, 0.1],
    shape: { type: "sphere", r: 0.5, th: 1 }, speed: [5, 10], drag: 3, gravity: 0.8, bursts: [[0.04 + n * 0.04, 8]], alpha: [[0, 1], [0.7, 1], [1, 0]] })),
  { name: "T20 Rays", mat: "streakOra", mode: "stretch", vs: 0.05, ls: 4, fudge: -6, pos: [0, 1.6, 2.2], life: [0.2, 0.3], size: [0.1, 0.2], shape: { type: "circle", r: 1.2, th: 1, y: 0 },
    speed: [8, 13], drag: 6, bursts: [[0.1, 12]], color: [[0, EMBY], [1, EMB]], alpha: [[0, 0.85], [1, 0]] },
  { name: "T21 TipFlash", mat: "starOra", fudge: -8, pos: [-3.9, 1.6, 2.4], life: 0.16, size: 2.6, rot: [0, 45 * D2R], bursts: [[0.17, 1]], sizeLife: [[0, 0.2], [0.25, 1.1], [1, 0]] },
  ...fxShards("T22", [0, 1.6, 3.0], 0.06, 14, 2.4, [3, 7]),
  ...fxFlakes("T23", [0, 1.4, 3.0], 0.08, 26, 2.6),
  ...fxEmbers("T24", [0, 1.2, 3.0], 0.06, 34, 3),
];
const SYSTEMS = [{ id: "swipe", role: "static", loop: false, duration: 1.0, layers: SWIPE }];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_ClawSwipe", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 2.4 }, DEMO_BG, { scenarios: {
    swipe: sc("할퀴기", { place: { swipe: { at: [0, 0, 0], start: 0.05 } }, targets: [[0, 0, 0, 0], [1.5, 0, 4, 200], [-2.5, 0, 3, 150]], camera: cam([0, 1.2, 2.5], 14, -60, 32) }),
  } }),
};
