// MordianGraspHand — 모르디안의 원령 "검은 손아귀" (impact). hitPoint = ground under the target player's feet, surfaceNormal = up.
// One instance ≈ 0.95 s: ink pool opens (0–0.08) → a huge shadow hand (4 fingers + thumb, black claws with violet rims) shoots up open (0–0.3)
// → snaps shut around the player (0.3–0.52, snap flash/splash at 0.42) → sinks back into the closing pool (0.55–0.95).
// The mod plays it 3 times per player at 0.8 s intervals (instances overlap by ~0.15 s — intended). Radius ≈ 2 m.
const TAU = Math.PI * 2, RAND = [0, TAU], YAW = [0, [0, TAU], 0];
const INKC = [0.035, 0.024, 0.055], SHC = [0.165, 0.086, 0.25], VIC = [0.54, 0.235, 1.0], LIC = [0.84, 0.59, 1.0], WLC = [0.95, 0.9, 1.0];
const F44 = { x: 4, y: 4 }, F28 = { x: 2, y: 8 };

function merge(parts) { const verts = [], uvs = [], tris = []; for (const p of parts) { const o = verts.length; verts.push(...p.verts); uvs.push(...p.uvs); tris.push(...p.tris.map(i => i + o)); } return { verts, uvs, tris }; }
function ringStrip(r, hw, seg) { return ribbon(seg, s => { const phi = -s * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r - hw), mul(d, r + hw)]; }); }
function band(r0, r1, seg) { return ribbon(seg, s => { const phi = -s * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r0), mul(d, r1)]; }); }
function runeBand(r, hw, n) { const parts = []; for (let k = 0; k < n; k++) parts.push(ribbon(16, s => { const phi = -(k + s) / n * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r + hw), mul(d, r - hw)]; })); return merge(parts); }
function tube(f, w, seg, taper) {
  const fr = s => { const p = f(s), q = f(Math.min(1, s + 0.01)), p0 = f(Math.max(0, s - 0.01)); let t = [q[0] - p0[0], q[1] - p0[1], q[2] - p0[2]]; const l = Math.hypot(...t) || 1; t = mul(t, 1 / l);
    let a = [0, 0, 1]; const b = [t[1] * a[2] - t[2] * a[1], t[2] * a[0] - t[0] * a[2], t[0] * a[1] - t[1] * a[0]]; return [p, a, b]; };
  const ww = s => w * (taper ? (1 - 0.35 * s) : 1);
  return merge([ribbon(seg, s => { const [p, a] = fr(s); return [add(p, mul(a, -ww(s))), add(p, mul(a, ww(s)))]; }),
                ribbon(seg, s => { const [p, , b] = fr(s); return [add(p, mul(b, -ww(s))), add(p, mul(b, ww(s)))]; })]);
}
// finger: unit length along +Y, knuckle bend + claw curl toward −X (the hand's centre after yaw), lies in the local XY plane
const digit = (w, curl, L, dz, dx) => tube(s => [dx * s - curl * L * (0.25 * s + 0.75 * Math.pow(s, 2.2)), L * Math.sin(s * Math.PI * 0.45) / Math.sin(Math.PI * 0.45), dz * (0.3 + s)], w, 18, true);
// a finger = a bundle of ink thorns: one thick claw + two thinner, shorter thorns splayed beside it
const finger = (w, curl) => merge([digit(w, curl, 1.0, 0, 0), digit(w * 0.55, curl * 1.2, 0.72, 0.1, 0.06), digit(w * 0.5, curl * 0.9, 0.6, -0.11, 0.08)]);
const EXTRA_MESHES = {
  finger: finger(0.11, 0.42),
  thumb: finger(0.14, 0.5),
  hRings: merge([ringStrip(1.0, 0.014, 96), ringStrip(0.94, 0.007, 96), ringStrip(0.58, 0.01, 72)]),
  hRunes: runeBand(0.76, 0.085, 10),
  shock: band(0.8, 1.0, 96),
  shockLine: ringStrip(1.0, 0.008, 96),
};

// ---- the hand: 4 fingers spread on the far side, thumb opposite. Each digit = 3 handed-off particles (rise / snap / sink) with matching rotation.
// rot3 = [x, yaw, tilt]; Euler Z (tilt) then X then Y (yaw). Yaw ψ = −a puts local +X outward along azimuth a; +tilt leans the digit inward.
const T_SNAP = 0.3, T_SINK = 0.52, T_END = 0.95;
const OPEN0 = -0.75, OPEN1 = -0.58, SHUT = 0.62, SHUT2 = 0.82;
const DIGITS = [ // [azimuth deg, base radius m, length m, mesh]
  [-62, 1.05, 2.5, "finger"], [-22, 1.15, 2.8, "finger"], [20, 1.15, 2.7, "finger"], [58, 1.05, 2.3, "finger"], [180, 1.0, 2.0, "thumb"],
];
const HAND = [];
DIGITS.forEach(([deg, r, L, mesh], i) => {
  const a = deg * Math.PI / 180, pos = [Math.cos(a) * r, 0.0, Math.sin(a) * r], yaw = -a, dl = i * 0.015;
  HAND.push(
    { name: "H" + (i + 1) + "a Rise", mat: "claw", mode: "mesh", mesh, fudge: 8, pos, life: T_SNAP - dl, size: L, rot3: [0, yaw, OPEN0], rotLife: [0, 0, (OPEN1 - OPEN0) / (T_SNAP - dl)],
      bursts: [[dl, 1]], sizeLife: [[0, 0.0, 0, 7], [0.55, 1.06], [1, 1.0]], alpha: [[0, 1], [0.99, 1], [1, 0]] },
    { name: "H" + (i + 1) + "b Snap", mat: "claw", mode: "mesh", mesh, fudge: 8, pos, life: T_SINK - T_SNAP, size: L, rot3: [0, yaw, OPEN1], rotLife: [0, 0, (SHUT - OPEN1) / (T_SINK - T_SNAP)],
      bursts: [[T_SNAP, 1]], sizeLife: [[0, 1.0], [1, 0.97]], alpha: [[0, 1], [0.99, 1], [1, 0]] },
    { name: "H" + (i + 1) + "c Sink", mat: "claw", mode: "mesh", mesh, fudge: 8, pos, life: T_END - T_SINK, size: L, rot3: [0, yaw, SHUT], rotLife: [0, 0, (SHUT2 - SHUT) / (T_END - T_SINK)],
      bursts: [[T_SINK, 1]], sizeLife: [[0, 0.97, 0, 0], [0.25, 0.92], [1, 0.0, -2, 0]], alpha: [[0, 1], [0.85, 1], [1, 0]] });
});

const GRASP = HAND.concat([
  // base: pool (palm) + small rune ring + scratch at the snap
  { name: "B01 Pool", mat: "pool", mode: "mesh", mesh: "quad", fudge: 60, pos: [0, 0.03, 0], life: T_END + 0.05, size: 4.6, rot3: YAW, rotLife: [0, 0.6, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.15, 0, 9], [0.1, 1.05], [0.2, 1.0], [0.6, 1.0], [1, 0.0]], alpha: [[0, 0], [0.02, 1], [0.9, 1], [1, 0]] },
  { name: "B02 PoolRim", mat: "poolGlow", mode: "mesh", mesh: "quad", fudge: 34, pos: [0, 0.04, 0], life: T_END + 0.05, size: 4.6, rot3: YAW, rotLife: [0, 0.6, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.15, 0, 9], [0.1, 1.05], [0.2, 1.0], [0.6, 1.0], [1, 0.0]], alpha: [[0, 1], [0.4, 0.8], [0.5, 1], [1, 0]] },
  { name: "B03 Rings", mat: "line", mode: "mesh", mesh: "hRings", fudge: 24, pos: [0, 0.06, 0], life: 0.9, size: 2.0, rotLife: [0, 1.2, 0], rot3: YAW, bursts: [[0, 1]],
    sizeLife: [[0, 0.4, 0, 6], [0.1, 1], [0.6, 1], [1, 0.3]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  { name: "B04 Runes", mat: "runes", mode: "mesh", mesh: "hRunes", fudge: 23, pos: [0, 0.065, 0], life: 0.88, size: 2.0, rotLife: [0, -1.6, 0], rot3: YAW, bursts: [[0.02, 1]],
    sizeLife: [[0, 0.4, 0, 6], [0.1, 1], [0.6, 1], [1, 0.3]], alpha: [[0, 1], [0.6, 0.85], [1, 0]] },
  { name: "B05 OpenRing", mat: "shock", mode: "mesh", mesh: "shock", fudge: 15, pos: [0, 0.1, 0], life: 0.3, size: 2.4, rot3: YAW, bursts: [[0, 1]],
    sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 0.8], [1, 0]] },
  { name: "B06 OpenLine", mat: "line", mode: "mesh", mesh: "shockLine", fudge: 14, pos: [0, 0.11, 0], life: 0.3, size: 2.45, rot3: YAW, bursts: [[0, 1]],
    sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "B07 SnapScratch", mat: "scratch", mode: "mesh", mesh: "quad", fudge: 18, pos: [0, 0.08, 0], life: 0.45, size: 2.6, rot3: YAW, bursts: [[T_SNAP + 0.12, 1]],
    sizeLife: [[0, 0.75, 0, 3], [0.2, 1.05], [1, 1.1]], alpha: [[0, 1], [0.4, 0.8], [1, 0]] },
  { name: "B08 SnapRing", mat: "shockDim", mode: "mesh", mesh: "shock", fudge: 16, pos: [0, 0.12, 0], life: 0.3, size: 1.9, rot3: YAW, bursts: [[T_SNAP + 0.12, 1]],
    sizeLife: [[0, 1.0], [1, 0.25]], alpha: [[0, 0.7], [1, 0]] },
  // mid: ink splashes (open + snap), shards, palm mist
  { name: "M01 OpenSplash", mat: "claw", mode: "stretch", vs: 0.07, ls: 1.4, fudge: 6, pos: [0, 0.1, 0], bursts: [[0, 12]], shape: { type: "circle", r: 1.3, th: 0.5 },
    speed: [1.5, 3.5], vel: { y: [5, 8], space: "world" }, drag: 2, gravity: 2.2, life: [0.3, 0.45], size: [0.22, 0.4], sizeLife: [[0, 1], [1, 0.4]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M02 SnapSplash", mat: "claw", mode: "stretch", vs: 0.07, ls: 1.3, fudge: 6, pos: [0, 1.2, 0], bursts: [[T_SNAP + 0.12, 14]], shape: { type: "sphere", r: 0.4, th: 0.5 },
    speed: [4, 7], vel: { y: [1.5, 3.5], space: "world" }, drag: 3, gravity: 2.5, life: [0.3, 0.45], size: [0.2, 0.36], sizeLife: [[0, 1], [1, 0.4]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M03 Drops", mat: "shard", fudge: 5, pos: [0, 0.2, 0], bursts: [[0.02, 10], [T_SNAP + 0.12, 10]], shape: { type: "circle", r: 1.4, th: 0.6 },
    speed: [1, 3], vel: { y: [3, 6], space: "world" }, gravity: 3, rot: RAND, rotZ: [-10, 10], life: [0.45, 0.7], size: [0.1, 0.2], alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "M04 PalmMist", mat: "mist", fudge: 45, bursts: [[0.03, 6], [T_SNAP, 4]], shape: { type: "circle", r: 1.3, th: 0.6, y: 0.35 },
    vel: { y: [0.3, 0.8], space: "world", orb: [1.2, 2.0] }, rot: RAND, rotZ: [-0.8, 0.8], life: [0.6, 0.8], size: [1.1, 1.7],
    color: [[0, INKC], [1, SHC]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], sizeLife: [[0, 0.6], [1, 1.2]], flip: F44 },
  { name: "M05 Swirl", mat: "stroke", mode: "mesh", mesh: "ground", fudge: 20, pos: [0, 0.09, 0], life: [0.35, 0.45], size: [1.3, 1.7], rot3: YAW, rotLife: [0, [-8, -6], 0],
    bursts: [[0.0, 2], [0.2, 2], [T_SNAP + 0.1, 3]], sizeLife: [[0, 1.4], [1, 0.5]], flip: F28 },
  // top: snap flash + sparks + motes; rising streaks along the fingers
  { name: "T01 SnapStar", mat: "star", fudge: -10, pos: [0, 1.25, 0], life: 0.16, size: 2.4, rot: [0, 0.4], bursts: [[T_SNAP + 0.12, 1]],
    sizeLife: [[0, 0.3], [0.25, 1], [1, 0]], alpha: [[0, 0.9], [1, 0.4]] },
  { name: "T02 SnapGlow", mat: "glow", fudge: -2, pos: [0, 1.1, 0], life: 0.22, size: 2.0, bursts: [[T_SNAP + 0.12, 1]],
    sizeLife: [[0, 0.6], [0.3, 1], [1, 1.1]], alpha: [[0, 0.32], [1, 0]] },
  { name: "T03 SnapSparks", mat: "streak", mode: "stretch", vs: 0.06, ls: 1.4, fudge: -7, pos: [0, 1.2, 0], bursts: [[T_SNAP + 0.12, 18]], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [5, 9], drag: 3, gravity: 1, life: [0.25, 0.45], size: [0.04, 0.08], color: [[0, WLC], [1, VIC]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "T04 RiseStreaks", mat: "streak", mode: "stretch", vs: 0.04, ls: 3.0, fudge: -6, bursts: [[0.0, 6], [0.08, 6]], shape: { type: "circle", r: 1.2, th: 0.3, y: 0.1 },
    vel: { y: [10, 14], space: "world" }, drag: 4, life: [0.22, 0.32], size: [0.07, 0.11], color: [[0, LIC], [1, VIC]], alpha: [[0, 1], [1, 0]] },
  { name: "T05 Motes", mat: "mote", fudge: -8, bursts: [[0.05, 10], [T_SNAP + 0.12, 14], [0.7, 8]], shape: { type: "circle", r: 1.7, th: 1, y: 0.15 },
    vel: { y: [0.8, 1.8], space: "world" }, noise: { s: 0.4, f: 1.2, sp: 0.8 }, life: [0.6, 1.0], size: [0.04, 0.08], color: [[0, LIC], [1, VIC]],
    alpha: [[0, 0], [0.12, 1], [0.7, 0.8], [1, 0]], sizeLife: [[0, 0.5], [0.15, 1], [1, 0.3]] },
]);

const MATS = {
  pool: { tex: "T_InkPool", tint: [2.2, 2.2, 2.2], add: false },
  poolGlow: { tex: "T_InkPool", tint: [1.6, 0.9, 2.8], add: true },
  line: { tex: "T_Line", tint: [1.8, 1.2, 2.8], add: true },
  runes: { tex: "T_RuneStrip", tint: [1.8, 1.3, 2.8], add: true },
  shock: { tex: "T_ShockBand", tint: [1.4, 0.85, 2.4], add: true },
  shockDim: { tex: "T_ShockBand", tint: [0.9, 0.5, 1.8], add: true },
  stroke: { tex: "T_Stroke_Sheet", tint: [1.8, 1.2, 2.8], add: true },
  claw: { tex: "T_ClawStrip", tint: [2.3, 2.3, 2.3], add: false },
  shard: { tex: "T_Shard", tint: [2.3, 2.3, 2.3], add: false },
  star: { tex: "T_Star", tint: [2.0, 1.7, 2.6], add: true },
  glow: { tex: "T_Glow", tint: [1.1, 0.55, 2.2], add: true },
  streak: { tex: "T_Streak", tint: [2.2, 1.8, 2.8], add: true },
  scratch: { tex: "T_Scratch", tint: [2.0, 1.6, 2.6], add: true },
  mote: { tex: "T_Dot", tint: [2.2, 1.8, 2.8], add: true },
  mist: { tex: "T_Puff_Sheet", tint: [1, 1, 1], add: false },
};

// demo: 3 players, each grabbed 3 times 0.8 s apart (players drift a little between grabs); player k starts 0.15·k s later
const PL = [[[0, 0, 0], [0.5, 0, 0.3], [1.0, 0, 0.2]], [[-4.5, 0, 3.0], [-4.9, 0, 3.6], [-5.2, 0, 4.0]], [[3.5, 0, 4.2], [3.9, 0, 4.6], [4.4, 0, 4.7]]];
const HITS = [], TG = [];
PL.forEach((pts, k) => pts.forEach((p, j) => HITS.push({ t: +(0.2 + 0.15 * k + 0.8 * j).toFixed(2), point: p, normal: [0, 1, 0], incoming: [0, -1, 0] })));
HITS.sort((a, b) => a.t - b.t);
PL.forEach(pts => TG.push([pts[1][0], 0, pts[1][2], 200]));
const SCENE = {
  name: "MordianGraspHand",
  bloom: { threshold: 0.95, intensity: 1.4, scatter: 0.7, tint: [0.9, 0.8, 1.0] }, tonemapping: "neutral",
  systems: [{ id: "grasp", role: "impact", loop: false, duration: 1.0, layers: GRASP }],
  demo: {
    interval: 3.4, maxImpacts: 12, dummy: { height: 1.8 }, bg: [0.16, 0.15, 0.18], floor: [0.3, 0.29, 0.29], grid: [0.36, 0.35, 0.35],
    scenarios: {
      triple: { label: "3명 × 3회 (0.8초 간격)", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, targets: TG, hits: HITS,
        camera: { target: [-0.5, 1.0, 2.4], dist: 15, yaw: -75, pitch: 24 } },
      single: { label: "한 번 (근접)", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, targets: [[0, 0, 0, 200]],
        hits: [{ t: 0.2, point: [0, 0, 0], normal: [0, 1, 0], incoming: [0, -1, 0] }],
        camera: { target: [0, 1.1, 0], dist: 6.5, yaw: -60, pitch: 14 } },
      wall: { label: "벽", from: [-3, 1.2, 0], to: [1.6, 1.2, 0], normal: [-1, 0, 0], flight: 0.01, wall: { x: 1.6 },
        hits: [{ t: 0.2, point: [1.6, 1.2, 0], normal: [-1, 0, 0], incoming: [1, 0, 0] }], camera: { target: [0.6, 1.2, 0], dist: 7, yaw: -120, pitch: 12 } },
      air: { label: "공중", from: [-3, 2, 0], to: [0, 2, 0], normal: null, flight: 0.01,
        hits: [{ t: 0.2, point: [0, 2, 0], normal: null, incoming: [0, -1, 0] }], camera: { target: [0, 2.5, 0], dist: 7, yaw: -90, pitch: 10 } },
    },
  },
};
