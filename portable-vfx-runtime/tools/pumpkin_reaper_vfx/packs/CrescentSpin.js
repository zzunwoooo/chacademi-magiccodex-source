// PR_CrescentSpin — 2. 반달 회전베기 (보스 중심 원형 반경 8블록). systems: spin (예고는 모드: 원 반경 8, 0.55초)
const R = 8, T = 0.55;
var EXTRA_MESHES = Object.assign({ spinArc: arc(0, 330, 0.62, 0), spinThin: arc(0, 300, 0.86, 0), groundRing: ringStrip(0.94, 0.07), groundArc: arc(0, 320, 0.8, 0) }, MC_MESHES);
const SPIN = [
  // base: ground ring scar + crack + shock out to the edge + mist pushed out
  { name: "G01 RingScar", mat: "slash", mode: "mesh", mesh: "groundArc", fudge: 40, pos: [0, 0.04, 0], life: 0.6, size: R, rot3: [0, [0, TAU], 0], rotLife: [0, -14, 0], bursts: [[0.05, 1]], flip: true },
  { name: "G02 EdgeRing", mat: "lineOra", mode: "mesh", mesh: "groundRing", fudge: 42, pos: [0, 0.05, 0], life: 0.7, size: R, bursts: [[0.1, 1]], sizeLife: [[0, 0.3, 0, 2], [0.5, 1]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  ...fxCrack("G03", [0, 0, 0], 0.08, 6),
  ...fxShock("G04", [0, 0, 0], 0.1, 2 * R + 1, "shockOra", 0.55),
  ...fxMist("G05", [0, 0.3, 0], 0.1, 12, 3, [1.8, 2.8], [3, 5]),
  // mid: two laps of the blade (dark ink under, hot edge, violet echo above), thin cuts tilted
  { name: "M01 SpinInk", mat: "slashInk", mode: "mesh", mesh: "spinArc", fudge: 30, pos: [0, 1.1, 0], life: 0.5, size: R * 1.02, rot3: [0, [0, TAU], 0], rotLife: [0, -16, 0], bursts: [[0, 1]], flip: true },
  { name: "M02 SpinBlade", mat: "slash", mode: "mesh", mesh: "spinArc", fudge: 10, pos: [0, 1.2, 0], life: 0.45, size: R, rot3: [0, 0, 0], rotLife: [0, -16, 0], bursts: [[0, 1]], flip: true },
  { name: "M03 SpinBlade2", mat: "slashHot", mode: "mesh", mesh: "spinThin", fudge: 9, pos: [0, 0.95, 0], life: 0.4, size: R * 1.03, rot3: [-5 * D2R, 2.0, 0], rotLife: [0, -18, 0], bursts: [[0.12, 1]], flip: true },
  { name: "M04 SpinEcho", mat: "slashViolet", mode: "mesh", mesh: "spinArc", fudge: 12, pos: [0, 1.5, 0], life: 0.55, size: R * 0.85, rot3: [6 * D2R, 1.0, 0], rotLife: [0, -14, 0], bursts: [[0.06, 1]], flip: true },
  { name: "M05 SpinWide", mat: "slashViolet", mode: "mesh", mesh: "spinThin", fudge: 13, pos: [0, 1.3, 0], life: 0.6, size: R * 1.12, rot3: [0, 3.0, 0], rotLife: [0, -12, 0], bursts: [[0.18, 1]], flip: true },
  // top: tangential sparks, ring of flame bursts, rays, bats, shards, motes, embers
  { name: "T01 TangentSparks", mat: "streakOra", mode: "stretch", vs: 0.06, ls: 1.8, fudge: -5, life: [0.3, 0.55], size: [0.05, 0.1], shape: { type: "circle", r: R * 0.85, th: 0.25, y: 1.1 },
    bursts: [[0.02, 20], [0.15, 20], [0.28, 14]], vel: { orb: [-14, -9], rad: [1, 3] }, drag: 2, gravity: 0.5, alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "T02 RingFlames", mat: "flame", fudge: 0, life: [0.4, 0.65], size: [1.0, 1.6], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "circle", r: R * 0.75, th: 0.15, y: 0.6 },
    bursts: [[0.08, 10], [0.22, 10]], vel: { y: [1, 2], rad: [1, 2], space: "world" }, drag: 3, sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 },
  ...fxRays("T03", [0, 1.1, 0], 0.18, 18, [11, 17]),
  ...fxBats("T04", [0, 1.4, 0], 0.15, 10, 2.5),
  ...fxShards("T05", [0, 1.1, 0], 0.08, 18, 3, [5, 9]),
  ...fxMotes("T06", [0, 0.3, 0], 0.2, 36, R * 0.7),
  ...fxEmbers("T07", [0, 1.0, 0], 0.08, 50, 4),
  ...fxFlash("T08", [0, 1.3, 0], 0.0, 3.5, "starVio"),
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_CrescentSpin", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "spin", role: "static", loop: false, duration: 1.9, layers: SPIN },
  ],
  demo: Object.assign({ interval: 3.0 }, DEMO_BG, { scenarios: {
    base: sc("기본", { targets: [[0, 0, 0, 0], [3, 0, 5, 200], [-6, 0, 2, 120], [2, 0, -6, 330]], place: { spin: { at: [0, 0, 0], start: T } }, camera: cam([0, 0.8, 1], 21, -90, 45) }) } }),
};
