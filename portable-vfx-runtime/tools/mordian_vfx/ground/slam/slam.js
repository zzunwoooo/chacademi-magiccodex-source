// MordianSlam — 모르디안의 원령 양팔 내려찍기 지면 강타 (impact, role impact).
// t = 0 = slam motion 0.95 s (both hands hit the ground). hitPoint = ground point 4–6 m in front of the boss, surfaceNormal = up (local +Y).
// Visual only: damage / knockback are the mod's. No warning decal (the game has its own telegraph).
const TAU = Math.PI * 2, RAND = [0, TAU], YAW = [0, [0, TAU], 0];
const INKC = [0.035, 0.024, 0.055], SHC = [0.165, 0.086, 0.25], VIC = [0.54, 0.235, 1.0], LIC = [0.84, 0.59, 1.0], WLC = [0.95, 0.9, 1.0];
const F44 = { x: 4, y: 4 }, F28 = { x: 2, y: 8 };

function merge(parts) { const verts = [], uvs = [], tris = []; for (const p of parts) { const o = verts.length; verts.push(...p.verts); uvs.push(...p.uvs); tris.push(...p.tris.map(i => i + o)); } return { verts, uvs, tris }; }
function ringStrip(r, hw, seg) { return ribbon(seg, s => { const phi = -s * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r - hw), mul(d, r + hw)]; }); }
function band(r0, r1, seg) { return ribbon(seg, s => { const phi = -s * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r0), mul(d, r1)]; }); } // V0 inner, V1 outer
function runeBand(r, hw, n) { const parts = []; for (let k = 0; k < n; k++) parts.push(ribbon(16, s => { const phi = -(k + s) / n * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r + hw), mul(d, r - hw)]; })); return merge(parts); }
function segLine(a, b, hw) { const dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz), n = [-dz / L * hw, 0, dx / L * hw];
  return ribbon(1, s => { const p = [a[0] + dx * s, 0, a[1] + dz * s]; return [add(p, mul(n, -1)), add(p, n)]; }); }
function polygram(r, n, step, hw) { const P = []; for (let k = 0; k < n; k++) { const a = k / n * TAU + Math.PI / n; P.push([Math.cos(a) * r, Math.sin(a) * r]); }
  const parts = []; for (let k = 0; k < n; k++) parts.push(segLine(P[k], P[(k + step) % n], hw)); return merge(parts); }
// crossed-ribbon spike along a straight/curved axis: base b, outward dir o (unit XZ), tilt (rad from vertical), length L, half width w
function spikeAt(b, o, tilt, L, w, bend) {
  const side = [-o[2], 0, o[0]];
  const axis = s => add(b, add(mul(o, Math.sin(tilt) * L * s + bend * L * s * s), [0, Math.cos(tilt) * L * s, 0]));
  const nrm = [o[0] * Math.cos(tilt), -Math.sin(tilt), o[2] * Math.cos(tilt)];
  return merge([ribbon(6, s => { const p = axis(s); return [add(p, mul(side, -w)), add(p, mul(side, w))]; }),
                ribbon(6, s => { const p = axis(s); return [add(p, mul(nrm, -w)), add(p, mul(nrm, w))]; })]);
}
let SEED = 11; const srnd = (a, b) => { SEED = (SEED * 16807) % 2147483647; return a + (b - a) * (SEED / 2147483647); };
function crown(n, r0, r1, l0, l1, t0, t1, w) { const parts = []; for (let k = 0; k < n; k++) { const a = (k + srnd(-0.3, 0.3)) / n * TAU, o = [Math.cos(a), 0, Math.sin(a)], r = srnd(r0, r1);
  parts.push(spikeAt(mul(o, r), o, srnd(t0, t1), srnd(l0, l1), w * srnd(0.8, 1.2), srnd(-0.1, 0.15))); } return merge(parts); }
const EXTRA_MESHES = {
  ringA: merge([ringStrip(1.0, 0.012, 128), ringStrip(0.93, 0.006, 128), ringStrip(0.62, 0.008, 96)]),
  runeA: runeBand(0.78, 0.07, 14),
  starA: merge([polygram(0.6, 8, 3, 0.006), polygram(0.6, 8, 2, 0.004)]),
  shock: band(0.82, 1.0, 128),
  shockLine: ringStrip(1.0, 0.006, 128),
  crownIn: crown(10, 0.3, 0.9, 2.0, 3.4, 0.12, 0.45, 0.3),
  crownOut: crown(16, 1.3, 2.6, 1.0, 2.0, 0.35, 0.8, 0.22),
  crownFar: crown(14, 2.8, 3.9, 0.5, 1.1, 0.5, 0.95, 0.16),
};

const IMPACT = [
  // ---------------------------------------------------- BASE: crack decal (dark body + violet rim), its glow, scorch scratch, rune circle flare, shock rings
  { name: "G01 CrackDark", mat: "crack", mode: "mesh", mesh: "quad", fudge: 60, pos: [0, 0.03, 0], life: 2.4, size: 9.5, rot3: YAW, bursts: [[0, 1]],
    sizeLife: [[0, 0.25, 0, 6], [0.06, 1], [1, 1]], alpha: [[0, 0], [0.01, 1], [0.75, 1], [1, 0]] },
  { name: "G02 CrackGlow", mat: "crackGlow", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.04, 0], life: 0.9, size: 9.5, rot3: YAW, bursts: [[0, 1]],
    sizeLife: [[0, 0.25, 0, 6], [0.15, 1], [1, 1]], alpha: [[0, 0.9], [0.25, 1], [1, 0]] },
  { name: "G03 Scratch", mat: "scratch", mode: "mesh", mesh: "quad", fudge: 25, pos: [0, 0.06, 0], life: 0.8, size: 4.6, rot3: YAW, bursts: [[0.02, 1]],
    sizeLife: [[0, 0.7, 0, 3], [0.2, 1.05], [1, 1.12]], alpha: [[0, 1], [0.3, 0.9], [1, 0]] },
  { name: "G04 ScratchDark", mat: "crack2", mode: "mesh", mesh: "quad", fudge: 58, pos: [0, 0.035, 0], life: 2.2, size: 4.6, rot3: YAW, bursts: [[0.02, 1]],
    alpha: [[0, 0], [0.05, 0.85], [0.7, 0.85], [1, 0]] },
  { name: "G05 CircleRings", mat: "line", mode: "mesh", mesh: "ringA", fudge: 22, pos: [0, 0.07, 0], life: 1.0, size: 3.0, rot3: YAW, rotLife: [0, 0.8, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.6, 0, 3], [0.2, 1], [1, 1.25]], alpha: [[0, 1], [0.4, 0.8], [1, 0]] },
  { name: "G06 CircleRunes", mat: "runes", mode: "mesh", mesh: "runeA", fudge: 21, pos: [0, 0.075, 0], life: 1.0, size: 3.0, rot3: YAW, rotLife: [0, -1.2, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.6, 0, 3], [0.2, 1], [1, 1.2]], alpha: [[0, 1], [0.35, 0.85], [1, 0]] },
  { name: "G07 CircleStar", mat: "line", mode: "mesh", mesh: "starA", fudge: 20, pos: [0, 0.08, 0], life: 0.85, size: 3.0, rot3: YAW, rotLife: [0, 1.6, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.5, 0, 3], [0.2, 1], [1, 1.15]], alpha: [[0, 1], [0.5, 0.7], [1, 0]] },
  { name: "G08 ShockA", mat: "shock", mode: "mesh", mesh: "shock", fudge: 15, pos: [0, 0.1, 0], life: 0.5, size: 8.0, rot3: YAW, bursts: [[0, 1]],
    sizeLife: [[0, 0.08, 0, 3.2], [1, 1]], alpha: [[0, 0.85], [0.55, 0.6], [1, 0]] },
  { name: "G09 ShockB", mat: "shock", mode: "mesh", mesh: "shock", fudge: 14, pos: [0, 0.14, 0], life: 0.62, size: 6.6, rot3: YAW, bursts: [[0.08, 1]],
    sizeLife: [[0, 0.08, 0, 3], [1, 1]], alpha: [[0, 0.75], [0.5, 0.55], [1, 0]] },
  { name: "G10 ShockC", mat: "shockDim", mode: "mesh", mesh: "shock", fudge: 13, pos: [0, 0.18, 0], life: 0.8, size: 5.0, rot3: YAW, bursts: [[0.17, 1]],
    sizeLife: [[0, 0.1, 0, 2.6], [1, 1]], alpha: [[0, 0.65], [0.5, 0.45], [1, 0]] },
  { name: "G11 ShockLine", mat: "line", mode: "mesh", mesh: "shockLine", fudge: 12, pos: [0, 0.12, 0], life: 0.5, size: 8.1, rot3: YAW, bursts: [[0, 1]],
    sizeLife: [[0, 0.08, 0, 3.2], [1, 1]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  { name: "G12 GroundSwirl", mat: "stroke", mode: "mesh", mesh: "ground", fudge: 18, pos: [0, 0.05, 0], life: [0.4, 0.55], size: [2.6, 3.6], rot3: YAW, rotLife: [0, [5, 7], 0],
    bursts: [[0, 2], [0.06, 2]], sizeLife: [[0, 0.5, 0, 2], [1, 1.5]], flip: F28 },

  // ---------------------------------------------------- MID: ink spike crowns erupting, black shards with violet rims, ink tongues
  { name: "M01 CrownInner", mat: "spike", mode: "mesh", mesh: "crownIn", fudge: 8, life: 1.0, size: 1.0, rot3: YAW, bursts: [[0, 1]],
    sizeLife: [[0, 0.05, 0, 8], [0.09, 1.08], [0.18, 1], [0.6, 0.98], [1, 0.05]], alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "M02 CrownOuter", mat: "spike", mode: "mesh", mesh: "crownOut", fudge: 9, life: 0.9, size: 1.0, rot3: YAW, bursts: [[0.05, 1]],
    sizeLife: [[0, 0.3, 0, 6], [0.1, 1.06], [0.2, 1], [0.55, 0.98], [1, 0.1]], alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "M03 CrownFar", mat: "spike", mode: "mesh", mesh: "crownFar", fudge: 10, life: 0.75, size: 1.0, rot3: YAW, bursts: [[0.11, 1]],
    sizeLife: [[0, 0.5, 0, 5], [0.12, 1.05], [0.25, 1], [1, 0.15]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M04 InkTongues", mat: "spike", mode: "stretch", vs: 0.07, ls: 1.6, fudge: 6, pos: [0, 0.3, 0], bursts: [[0, 16], [0.05, 8]],
    shape: { type: "circle", r: 0.8, th: 1 }, vel: { y: [7, 12], space: "world" }, speed: [2, 5], drag: 5, gravity: 1.2,
    life: [0.35, 0.55], size: [0.35, 0.6], sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M05 ShardsUp", mat: "shard", mode: "stretch", vs: 0.06, ls: 1.2, fudge: 5, pos: [0, 0.4, 0], bursts: [[0, 22], [0.06, 10]],
    shape: { type: "circle", r: 1.2, th: 1 }, vel: { y: [6, 11], space: "world" }, speed: [2, 6], drag: 0.8, gravity: 3.2,
    life: [0.6, 1.0], size: [0.16, 0.3], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M06 ShardsSpin", mat: "shard", fudge: 5, pos: [0, 0.4, 0], bursts: [[0.02, 14]], shape: { type: "circle", r: 1.6, th: 1 },
    vel: { y: [4, 8], space: "world" }, speed: [3, 7], drag: 0.6, gravity: 3.4, rot: RAND, rotZ: [-14, 14],
    life: [0.7, 1.1], size: [0.18, 0.36], alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "M07 InkSplat", mat: "mist", fudge: 40, pos: [0, 0.3, 0], bursts: [[0, 12]], shape: { type: "circle", r: 0.8, th: 1 },
    speed: [5, 9], drag: 5, vel: { y: [0.6, 1.4], space: "world" }, rot: RAND, rotZ: [-1, 1], life: [0.5, 0.8], size: [1.0, 1.7],
    color: [[0, INKC], [1, SHC]], alpha: [[0, 0.85], [0.5, 0.6], [1, 0]], sizeLife: [[0, 0.5], [1, 1.3]], flip: F44 },

  // ---------------------------------------------------- TOP: flash, glow, rays, sparks
  { name: "T01 FlashStar", mat: "star", fudge: -10, pos: [0, 0.7, 0], life: 0.2, size: 5.0, rot: [0, 0.3], bursts: [[0, 1]],
    sizeLife: [[0, 0.3], [0.2, 1.1], [1, 0]], alpha: [[0, 1], [1, 0.6]] },
  { name: "T02 FlashGlow", mat: "glow", fudge: -2, pos: [0, 0.6, 0], life: 0.28, size: 3.4, bursts: [[0, 1]],
    sizeLife: [[0, 0.5], [0.25, 1], [1, 1.2]], alpha: [[0, 0.35], [1, 0]] },
  { name: "T03 GroundRays", mat: "streak", mode: "stretch", vs: 0.05, ls: 3.5, fudge: -6, pos: [0, 0.25, 0], bursts: [[0, 28]],
    shape: { type: "circle", r: 0.4, th: 1 }, speed: [14, 22], drag: 5, life: [0.25, 0.4], size: [0.12, 0.2],
    color: [[0, WLC], [0.4, LIC], [1, VIC]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  { name: "T04 UpRays", mat: "streak", mode: "stretch", vs: 0.04, ls: 4.5, fudge: -6, pos: [0, 0.2, 0], bursts: [[0.01, 10]],
    shape: { type: "circle", r: 1.0, th: 1 }, vel: { y: [14, 20] }, speed: [0.5, 2], drag: 4, life: [0.3, 0.45], size: [0.12, 0.2],
    color: [[0, LIC], [1, VIC]], alpha: [[0, 1], [1, 0]] },
  { name: "T05 Sparks", mat: "streak", mode: "stretch", vs: 0.06, ls: 1.4, fudge: -7, pos: [0, 0.4, 0], bursts: [[0, 36]],
    shape: { type: "sphere", r: 0.4, th: 1 }, vel: { y: [3, 6], space: "world" }, speed: [6, 12], drag: 2.5, gravity: 1.2,
    life: [0.4, 0.8], size: [0.05, 0.1], color: [[0, WLC], [1, VIC]], alpha: [[0, 1], [0.7, 1], [1, 0]] },

  // ---------------------------------------------------- LINGER: violet dust rising, dark mist behind, motes
  { name: "L01 VioletDust", mat: "dust", fudge: 3, bursts: [[0.04, 12], [0.2, 8]], shape: { type: "circle", r: 3.2, th: 0.8, y: 0.3 },
    vel: { y: [0.8, 1.6], space: "world", rad: [0.4, 1.0] }, drag: 1.2, rot: RAND, rotZ: [-0.6, 0.6], life: [1.3, 2.0], size: [1.2, 2.0],
    alpha: [[0, 0], [0.12, 0.3], [0.6, 0.18], [1, 0]], sizeLife: [[0, 0.6], [1, 1.4]], flip: F44 },
  { name: "L02 DarkMist", mat: "mist", fudge: 45, bursts: [[0.06, 12]], shape: { type: "circle", r: 3.0, th: 0.7, y: 0.4 },
    vel: { y: [0.4, 0.9], space: "world", rad: [0.6, 1.2] }, drag: 1.4, rot: RAND, rotZ: [-0.4, 0.4], life: [1.5, 2.2], size: [2.0, 3.2],
    color: [[0, INKC], [1, SHC]], alpha: [[0, 0], [0.15, 0.45], [0.6, 0.3], [1, 0]], sizeLife: [[0, 0.6], [1, 1.35]], flip: F44 },
  { name: "L03 Motes", mat: "mote", fudge: -8, bursts: [[0.05, 40], [0.3, 25]], shape: { type: "circle", r: 3.5, th: 1, y: 0.2 },
    vel: { y: [0.8, 2.0], space: "world" }, noise: { s: 0.5, f: 1.2, sp: 0.8 }, life: [1.2, 2.2], size: [0.05, 0.11],
    color: [[0, LIC], [1, VIC]], alpha: [[0, 0], [0.1, 1], [0.7, 0.8], [1, 0]], sizeLife: [[0, 0.5], [0.15, 1], [1, 0.3]] },
  { name: "L04 CrackEmbers", mat: "mote", fudge: -8, bursts: [[0.1, 20]], shape: { type: "circle", r: 4.2, th: 0.9, y: 0.05 },
    vel: { y: [0.3, 0.8], space: "world" }, life: [0.8, 1.6], size: [0.04, 0.08], color: [[0, LIC], [1, VIC]],
    alpha: [[0, 0], [0.15, 1], [1, 0]] },
];

const MATS = {
  crack: { tex: "T_Crack", tint: [2.2, 2.2, 2.2], add: false },
  crack2: { tex: "T_Scratch", tint: [0.05, 0.03, 0.08], add: false },
  crackGlow: { tex: "T_Crack", tint: [1.7, 0.9, 2.8], add: true },
  scratch: { tex: "T_Scratch", tint: [2.0, 1.6, 2.6], add: true },
  line: { tex: "T_Line", tint: [1.8, 1.2, 2.8], add: true },
  runes: { tex: "T_RuneStrip", tint: [1.8, 1.3, 2.8], add: true },
  shock: { tex: "T_ShockBand", tint: [1.4, 0.85, 2.4], add: true },
  shockDim: { tex: "T_ShockBand", tint: [0.9, 0.5, 1.8], add: true },
  stroke: { tex: "T_Stroke_Sheet", tint: [1.8, 1.2, 2.8], add: true },
  spike: { tex: "T_ClawStrip", tint: [2.3, 2.3, 2.3], add: false },
  shard: { tex: "T_Shard", tint: [2.3, 2.3, 2.3], add: false },
  star: { tex: "T_Star", tint: [2.0, 1.7, 2.6], add: true },
  glow: { tex: "T_Glow", tint: [1.1, 0.55, 2.2], add: true },
  streak: { tex: "T_Streak", tint: [2.2, 1.8, 2.8], add: true },
  mote: { tex: "T_Dot", tint: [2.2, 1.8, 2.8], add: true },
  dust: { tex: "T_Puff_Sheet", tint: [0.6, 0.28, 1.3], add: true },
  mist: { tex: "T_Puff_Sheet", tint: [1, 1, 1], add: false },
};

const PLAYERS = [[2.0, 0, 1.5, 200], [-2.6, 0, 2.4, 140], [1.0, 0, -3.2, 330]];
const SCENE = {
  name: "MordianSlam",
  bloom: { threshold: 0.95, intensity: 1.4, scatter: 0.7, tint: [0.9, 0.8, 1.0] }, tonemapping: "neutral",
  systems: [{ id: "impact", role: "impact", loop: false, duration: 2.4, layers: IMPACT }],
  demo: {
    interval: 3.2, dummy: { height: 1.8 }, bg: [0.16, 0.15, 0.18], floor: [0.3, 0.29, 0.29], grid: [0.36, 0.35, 0.35],
    scenarios: {
      ground: { label: "지면 강타", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, targets: PLAYERS,
        hits: [{ t: 0.2, point: [0, 0, 0], normal: [0, 1, 0], incoming: [0, -1, 0] }],
        camera: { target: [0, 1.0, 0], dist: 15, yaw: -70, pitch: 26 } },
      close: { label: "근접 (플레이어 시점)", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, targets: PLAYERS,
        hits: [{ t: 0.2, point: [0, 0, 0], normal: [0, 1, 0], incoming: [0, -1, 0] }],
        camera: { target: [0, 1.4, 0], dist: 7.5, yaw: -40, pitch: 12 } },
      wall: { label: "벽", from: [-4, 1.5, 0], to: [1.6, 1.5, 0], normal: [-1, 0, 0], flight: 0.01, wall: { x: 1.6 },
        hits: [{ t: 0.2, point: [1.6, 1.5, 0], normal: [-1, 0, 0], incoming: [1, 0, 0] }],
        camera: { target: [0.4, 1.5, 0], dist: 13, yaw: -115, pitch: 14 } },
      air: { label: "공중", from: [-4, 2, 0], to: [0, 2, 0], normal: null, flight: 0.01,
        hits: [{ t: 0.2, point: [0, 2.5, 0], normal: null, incoming: [0, -1, 0] }],
        camera: { target: [0, 2.5, 0], dist: 13, yaw: -90, pitch: 12 } },
    },
  },
};
