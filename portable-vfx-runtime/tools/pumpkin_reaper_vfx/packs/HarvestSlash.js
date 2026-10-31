// PR_HarvestSlash — 1. 수확 베기 (예고는 모드 chatelegraph: 240° 부채꼴 반경 7, 0.45초). systems: slash (오른→왼) / slashBack (왼→오른, 2연타의 두 번째).
const R = 7, T = 0.45;
var EXTRA_MESHES = {
  arcR2L: arc(125, -125, 0.55, 0),          // boss right → left (sweep through the front)
  arcL2R: arc(-125, 125, 0.55, 0),
  arcThinR2L: arc(118, -118, 0.85, 0),
  arcThinL2R: arc(-118, 118, 0.85, 0),
  groundR2L: arc(120, -120, 0.75, 0.02 / R),
  groundL2R: arc(-120, 120, 0.75, 0.02 / R),
};
const FRONT = [[0, 1.1, 3.6], [2.9, 1.1, 2.0], [-2.9, 1.1, 2.0], [4.4, 1.0, -1.4], [-4.4, 1.0, -1.4]];
function slashLayers(dir) {   // dir: "R2L" | "L2R"
  const s = dir === "R2L" ? 1 : -1, d0 = 0.0;
  const order = s > 0 ? [3, 1, 0, 2, 4] : [4, 2, 0, 1, 3];
  const L = [
    // --- base: ground answers (scar decal following the blade + crack + low violet mist)
    { name: "G01 GroundScar", mat: "slashGround", mode: "mesh", mesh: "ground" + dir, fudge: 40, pos: [0, 0.04, 0], life: 0.7, size: R, bursts: [[0.03, 1]], flip: true },
    { name: "G02 GroundScarDeep", mat: "slashGroundDeep", mode: "mesh", mesh: "ground" + dir, fudge: 41, pos: [0, 0.03, 0], life: 0.9, size: R * 0.82, bursts: [[0.05, 1]], flip: true },
    { name: "G03 Cracks", mat: "crack", mode: "mesh", mesh: "quad", fudge: 44, pos: [0, 0.05, 3.2], life: 1.1, size: 4.5, rot3: [0, [0, TAU], 0], bursts: [[0.1, 1]],
      sizeLife: [[0, 0.5, 0, 3], [0.25, 1]], alpha: [[0, 0], [0.08, 1], [0.6, 0.8], [1, 0]] },
    { name: "G04 LowMist", mat: "mist", fudge: 35, life: [0.9, 1.3], size: [1.6, 2.6], shape: { type: "circle", r: R * 0.6, th: 0.5, y: 0.25 }, off: [0, 0.25, 2.2],
      bursts: [[0.08, 9]], vel: { rad: [0.6, 1.4], y: [0.05, 0.2] }, drag: 1.4, rot: [0, TAU], rotZ: [-0.5, 0.5], alpha: [[0, 0], [0.2, 0.55], [1, 0]], sizeLife: [[0, 0.6], [1, 1.2]], flip: { x: 4, y: 4 } },
    // --- mid: the blade sweep (dark ink body behind, hot orange edge, violet echo, second thinner cut)
    { name: "M01 InkSweep", mat: "slashInk", mode: "mesh", mesh: "arc" + dir, fudge: 30, pos: [0, 1.15, 0], life: 0.42, size: R * 1.02, rot3: [-4 * s * D2R, 0, 0], bursts: [[0, 1]], flip: true },
    { name: "M02 BladeSweep", mat: "slash", mode: "mesh", mesh: "arc" + dir, fudge: 10, pos: [0, 1.2, 0], life: 0.38, size: R, bursts: [[0, 1]], flip: true },
    { name: "M03 BladeEcho", mat: "slashViolet", mode: "mesh", mesh: "arc" + dir, fudge: 12, pos: [0, 1.45, 0], life: 0.45, size: R * 0.86, rot3: [6 * D2R, 0, 0], bursts: [[0.04, 1]], flip: true },
    { name: "M04 ThinCut", mat: "slashHot", mode: "mesh", mesh: "arcThin" + dir, fudge: 8, pos: [0, 0.9, 0], life: 0.3, size: R * 1.04, rot3: [-7 * D2R, 0, 0], bursts: [[0.02, 1]], flip: true },
    { name: "M05 WideGhost", mat: "slashViolet", mode: "mesh", mesh: "arcThin" + dir, fudge: 13, pos: [0, 1.3, 0], life: 0.55, size: R * 1.1, bursts: [[0.08, 1]], flip: true, alpha: [[0, 0.7], [1, 0.7]] },
    // --- top: flame bursts popping along the arc (in sweep order), rays, sparks, shards, bats, motes
    ...order.map((i, n) => ({ name: "T0" + (n + 1) + " ArcFlames", mat: "flame", fudge: 0, pos: FRONT[i], life: [0.35, 0.55], size: [0.9, 1.5], rot: [0, TAU], rotZ: [-4, 4],
      shape: { type: "sphere", r: 0.5, th: 1 }, speed: [1, 3], drag: 4, vel: { y: [0.6, 1.4], space: "world" }, bursts: [[0.04 + n * 0.045, 5]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: { x: 4, y: 4 } })),
    ...order.map((i, n) => ({ name: "T1" + n + " Sparks", mat: "streak", mode: "stretch", vs: 0.07, ls: 1.6, fudge: -5, pos: FRONT[i], life: [0.3, 0.55], size: [0.05, 0.1],
      shape: { type: "sphere", r: 0.4, th: 1 }, speed: [5, 11], drag: 3, gravity: 0.7, bursts: [[0.05 + n * 0.045, 9]], alpha: [[0, 1], [0.7, 1], [1, 0]] })),
    { name: "T20 GodRays", mat: "streak", mode: "stretch", vs: 0.05, ls: 4.5, fudge: -6, pos: [0, 1.1, 1.5], life: [0.22, 0.32], size: [0.12, 0.22],
      shape: { type: "circle", r: 1.0, th: 1, y: 0 }, speed: [9, 15], drag: 6, bursts: [[0.12, 14]], color: [[0, YEL], [1, ORA]], alpha: [[0, 0.9], [1, 0]] },
    { name: "T21 TipFlash", mat: "star", fudge: -8, pos: [0, 1.2, R * 0.92], life: 0.18, size: 3.2, rot: [0, 45 * D2R], bursts: [[0.13, 1]], sizeLife: [[0, 0.2], [0.25, 1.1], [1, 0]] },
    { name: "T22 InkShards", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.4, fudge: 25, pos: [0, 1.1, 2.4], life: [0.45, 0.8], size: [0.14, 0.28],
      shape: { type: "sphere", r: 2.2, th: 0.4 }, speed: [3, 7], drag: 1, gravity: 1.3, bursts: [[0.08, 14]], alpha: [[0, 1], [0.75, 1], [1, 0]] },
    { name: "T23 Bats", mat: "bat", fudge: 20, pos: [0, 1.4, 2.0], life: [0.7, 1.1], size: [0.35, 0.6], shape: { type: "sphere", r: 1.5, th: 0.6 },
      speed: [3, 6], drag: 1.5, vel: { y: [1.2, 2.4], space: "world" }, noise: { s: 1.5, f: 2.5, sp: 1.2 }, bursts: [[0.12, 7]],
      sizeLife: [[0, 0.5], [0.15, 1], [0.5, 0.75], [0.6, 1], [0.8, 0.75], [1, 0.4]], alpha: [[0, 0], [0.1, 1], [0.8, 1], [1, 0]] },
    { name: "T24 SoulMotes", mat: "dotSoul", fudge: -7, pos: [0, 0.4, 2.6], life: [1.0, 1.8], size: [0.05, 0.12], shape: { type: "circle", r: R * 0.6, th: 0.7, y: 0 },
      bursts: [[0.15, 30]], vel: { y: [0.6, 1.6], space: "world" }, noise: { s: 0.6, f: 1, sp: 0.6 }, alpha: [[0, 0], [0.1, 1], [0.7, 0.8], [1, 0]], sizeLife: [[0, 1], [1, 0.3]] },
    { name: "T25 Embers", mat: "dotFire", fudge: -6, pos: [0, 0.9, 2.6], life: [0.8, 1.5], size: [0.04, 0.09], shape: { type: "sphere", r: 3, th: 0.6 },
      bursts: [[0.08, 40]], speed: [0.5, 2.5], drag: 1.5, gravity: -0.1, noise: { s: 1, f: 1, sp: 0.7 }, color: [[0, YEL], [0.5, ORA], [1, CRIM]], alpha: [[0, 0], [0.05, 1], [0.7, 0.9], [1, 0]] },
  ];
  return L;
}
const MATS = Object.assign({}, WARN_MATS, {
  slash: { tex: "T_ScytheSlash_Sheet", tint: [2.6, 1.6, 1.2], add: true },
  slashHot: { tex: "T_ScytheSlash_Sheet", tint: [3.0, 2.2, 1.6], add: true },
  slashViolet: { tex: "T_ScytheSlash_Sheet", tint: [1.1, 0.6, 2.2], add: true },
  slashInk: { tex: "T_ScytheSlash_Sheet", tint: [0.22, 0.08, 0.3], add: false },
  slashGround: { tex: "T_ScytheSlash_Sheet", tint: [2.2, 1.0, 0.9], add: true },
  slashGroundDeep: { tex: "T_ScytheSlash_Sheet", tint: [0.15, 0.05, 0.2], add: false },
  crack: { tex: "T_Crack", tint: [1.6, 1.1, 0.9], add: false },
  mist: { tex: "T_Mist_Sheet", tint: [1, 1, 1], add: false },
  flame: { tex: "T_PumpkinFlame_Sheet", tint: [2.0, 1.4, 1.1], add: true },
  streak: { tex: "T_FireStreak", tint: [2.6, 1.8, 1.2], add: true },
  star: { tex: "T_Star", tint: [2.8, 1.9, 1.2], add: true },
  shard: { tex: "T_InkShard", tint: [1.6, 1.2, 1.2], add: false },
  bat: { tex: "T_Bat", tint: [1.4, 1.2, 1.6], add: false },
  dotSoul: { tex: "T_Dot", tint: [0.8, 2.4, 2.0], add: true },
  dotFire: { tex: "T_Dot", tint: [2.4, 1.5, 0.7], add: true },
});
const SCENE = {
  name: "PR_HarvestSlash", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "slash", role: "static", loop: false, duration: 1.9, layers: slashLayers("R2L") },
    { id: "slashBack", role: "static", loop: false, duration: 1.9, layers: slashLayers("L2R") },
  ],
  demo: Object.assign({ interval: 3.4 }, DEMO_BG, {
    scenarios: {
      single: { label: "1타", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0], hits: [],
        targets: [[0, 0, 0, 0], [2.5, 0, 4.5, 200], [-3, 0, 3, 150], [5.5, 0, -2, 260]],
        place: { slash: { at: [0, 0, 0], start: T }, slashBack: { at: [0, 0, 0], start: 99 } },
        camera: { target: [0, 0.8, 2.5], dist: 17, yaw: -90, pitch: 42 } },
      double: { label: "2연타", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0], hits: [],
        targets: [[0, 0, 0, 0], [2.5, 0, 4.5, 200], [-3, 0, 3, 150]],
        place: { slash: { at: [0, 0, 0], start: T }, slashBack: { at: [0, 0, 0], start: T + 0.65 } },
        camera: { target: [0, 0.8, 2.5], dist: 17, yaw: -90, pitch: 42 } },
    },
  }),
};
