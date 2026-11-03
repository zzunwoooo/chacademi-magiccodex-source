// MordianShadowGate — 모르디안의 원령 그림자 이동 (static, 2 systems).
//   sink : vanish motion t=0 (boss melts into the floor 0.3–1.2 s): ink pool spreads, tentacles rise and drag the body down, vortex, final gulp at 1.15 s.
//   rise : emerge motion t=0 at the new position (rises 0–0.9 s, lands 0.9 s): pool opens, ink column / tendrils erupt upward, peak burst at 0.9 s, gone ~1.4 s.
// P = ground point under the boss (static, local +Y = up). Authored radius 3 m; runtime areaRadius scales the whole cast (player shadow clones: areaRadius ~1 → ×0.33).
const TAU = Math.PI * 2, RAND = [0, TAU], YAW = [0, [0, TAU], 0], R = 3;
const INKC = [0.035, 0.024, 0.055], SHC = [0.165, 0.086, 0.25], VIC = [0.54, 0.235, 1.0], LIC = [0.84, 0.59, 1.0], WLC = [0.95, 0.9, 1.0];
const F44 = { x: 4, y: 4 }, F28 = { x: 2, y: 8 };

function merge(parts) { const verts = [], uvs = [], tris = []; for (const p of parts) { const o = verts.length; verts.push(...p.verts); uvs.push(...p.uvs); tris.push(...p.tris.map(i => i + o)); } return { verts, uvs, tris }; }
function ringStrip(r, hw, seg) { return ribbon(seg, s => { const phi = -s * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r - hw), mul(d, r + hw)]; }); }
function band(r0, r1, seg) { return ribbon(seg, s => { const phi = -s * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r0), mul(d, r1)]; }); }
function runeBand(r, hw, n) { const parts = []; for (let k = 0; k < n; k++) parts.push(ribbon(16, s => { const phi = -(k + s) / n * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [mul(d, r + hw), mul(d, r - hw)]; })); return merge(parts); }
function segLine(a, b, hw) { const dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz), n = [-dz / L * hw, 0, dx / L * hw];
  return ribbon(1, s => { const p = [a[0] + dx * s, 0, a[1] + dz * s]; return [add(p, mul(n, -1)), add(p, n)]; }); }
function polygram(r, n, step, hw, rot) { const P = []; for (let k = 0; k < n; k++) { const a = k / n * TAU + (rot || 0); P.push([Math.cos(a) * r, Math.sin(a) * r]); }
  const parts = []; for (let k = 0; k < n; k++) parts.push(segLine(P[k], P[(k + step) % n], hw)); return merge(parts); }
function smallCircles(r, n, cr, hw, rot) { const parts = []; for (let k = 0; k < n; k++) { const a = k / n * TAU + (rot || 0), c = [Math.cos(a) * r, 0, Math.sin(a) * r];
  parts.push(ribbon(24, s => { const phi = -s * TAU, d = [Math.cos(phi), 0, Math.sin(phi)]; return [add(c, mul(d, cr - hw)), add(c, mul(d, cr + hw))]; })); } return merge(parts); }
// crossed-ribbon tube along a curve f(s) (s 0 base → 1 tip), half width w; U = s (texture tip at U=1)
function tube(f, w, seg) {
  const fr = s => { const p = f(s), q = f(Math.min(1, s + 0.01)), p0 = f(Math.max(0, s - 0.01)); let t = [q[0] - p0[0], q[1] - p0[1], q[2] - p0[2]]; const l = Math.hypot(...t) || 1; t = mul(t, 1 / l);
    let a = [t[2], 0, -t[0]]; const la = Math.hypot(...a); a = la < 1e-4 ? [1, 0, 0] : mul(a, 1 / la);
    const b = [t[1] * a[2] - t[2] * a[1], t[2] * a[0] - t[0] * a[2], t[0] * a[1] - t[1] * a[0]]; return [p, a, b]; };
  return merge([ribbon(seg, s => { const [p, a] = fr(s); return [add(p, mul(a, -w)), add(p, mul(a, w))]; }),
                ribbon(seg, s => { const [p, , b] = fr(s); return [add(p, mul(b, -w)), add(p, mul(b, w))]; })]);
}
let SEED = 23; const srnd = (a, b) => { SEED = (SEED * 16807) % 2147483647; return a + (b - a) * (SEED / 2147483647); };
// sink tentacles: rise around the body from radius r, lean in and curl over the centre
function tentacles(n, r0, r1, h0, h1, w, a0) { const parts = []; for (let k = 0; k < n; k++) {
  const a = a0 + (k + srnd(-0.25, 0.25)) / n * TAU, o = [Math.cos(a), 0, Math.sin(a)], sd = [-o[2], 0, o[0]], r = srnd(r0, r1), H = srnd(h0, h1), wig = srnd(0.15, 0.35), ph = srnd(0, TAU);
  parts.push(tube(s => add(add(mul(o, r * (1 - 0.75 * Math.pow(s, 1.6))), mul(sd, wig * Math.sin(s * 5 + ph) * s)), [0, H * Math.sin(s * Math.PI * 0.62) / Math.sin(Math.PI * 0.62), 0]), w * srnd(0.8, 1.2), 14)); }
  return merge(parts); }
// rise geyser: tendrils hug the column then flare outward at the top
function geyser(n, r0, r1, h0, h1, w, a0) { const parts = []; for (let k = 0; k < n; k++) {
  const a = a0 + (k + srnd(-0.3, 0.3)) / n * TAU, o = [Math.cos(a), 0, Math.sin(a)], sd = [-o[2], 0, o[0]], r = srnd(r0, r1), H = srnd(h0, h1), tw = srnd(-0.5, 0.5);
  parts.push(tube(s => add(add(mul(o, r * (0.55 + 0.45 * s) + 1.4 * Math.pow(s, 3)), mul(sd, tw * s * s)), [0, H * s, 0]), w * srnd(0.8, 1.2), 14)); }
  return merge(parts); }
const EXTRA_MESHES = {
  cRings: merge([ringStrip(1.0, 0.010, 128), ringStrip(0.955, 0.005, 128), ringStrip(0.66, 0.007, 96), ringStrip(0.3, 0.006, 64)]),
  cRunes: runeBand(0.81, 0.075, 16),
  cRunesIn: runeBand(0.48, 0.05, 10),
  cStar: merge([polygram(0.66, 8, 3, 0.006), polygram(0.66, 8, 2, 0.004, 0), smallCircles(0.66, 8, 0.045, 0.004)]),
  shock: band(0.8, 1.0, 128),
  shockLine: ringStrip(1.0, 0.006, 128),
  tentA: tentacles(6, 1.8, 2.6, 3.6, 5.6, 0.3, 0),
  tentB: tentacles(6, 2.0, 2.9, 2.2, 3.6, 0.22, 0.5),
  geyserA: geyser(7, 0.9, 1.4, 4.5, 6.8, 0.24, 0),
  geyserB: geyser(8, 1.3, 1.9, 2.2, 3.6, 0.17, 0.4),
};

// ---- rotation helpers: held rotating circle ω, flare copies start with rot3 = ω·(tStart − t0)
const W_RING = 0.5, W_RUNE = -0.8, W_RUNEIN = 1.1, W_STAR = 0.35;

// ================================================================ SINK (vanish), duration 1.6
const T_GULP = 1.15;
const SINK = [
  // base: ink pool + rune circle
  { name: "S01 Pool", mat: "pool", mode: "mesh", mesh: "quad", fudge: 60, pos: [0, 0.03, 0], life: 1.6, size: 2 * R * 1.3, rot3: YAW, rotLife: [0, 0.35, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.15, 0, 4], [0.2, 1.0], [0.68, 1.02], [0.82, 0.7], [1, 0.0]], alpha: [[0, 0], [0.03, 1], [0.92, 1], [1, 0]] },
  { name: "S02 PoolRim", mat: "poolGlow", mode: "mesh", mesh: "quad", fudge: 34, pos: [0, 0.04, 0], life: 1.6, size: 2 * R * 1.3, rot3: YAW, rotLife: [0, 0.35, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.15, 0, 4], [0.2, 1.0], [0.68, 1.02], [0.82, 0.7], [1, 0.0]], alpha: [[0, 0], [0.1, 0.8], [0.7, 0.9], [1, 0]] },
  { name: "S03 CircleRings", mat: "line", mode: "mesh", mesh: "cRings", fudge: 24, pos: [0, 0.06, 0], life: 1.55, size: R, rot3: [0, 0, 0], rotLife: [0, W_RING, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.5, 0, 3], [0.15, 1], [0.7, 1], [0.85, 0.75], [1, 0.1]], alpha: [[0, 0], [0.08, 0.9], [0.7, 0.8], [1, 0]] },
  { name: "S04 CircleRunes", mat: "runes", mode: "mesh", mesh: "cRunes", fudge: 23, pos: [0, 0.065, 0], life: 1.5, size: R, rotLife: [0, W_RUNE, 0], bursts: [[0.05, 1]],
    sizeLife: [[0, 0.6, 0, 3], [0.15, 1], [0.7, 1], [0.85, 0.75], [1, 0.1]], alpha: [[0, 0], [0.12, 0.9], [0.7, 0.85], [1, 0]] },
  { name: "S05 CircleRunesIn", mat: "runes", mode: "mesh", mesh: "cRunesIn", fudge: 22, pos: [0, 0.07, 0], life: 1.45, size: R, rotLife: [0, W_RUNEIN, 0], bursts: [[0.1, 1]],
    sizeLife: [[0, 0.6, 0, 3], [0.15, 1], [0.72, 1], [1, 0.1]], alpha: [[0, 0], [0.12, 0.8], [0.7, 0.8], [1, 0]] },
  { name: "S06 CircleStar", mat: "line", mode: "mesh", mesh: "cStar", fudge: 21, pos: [0, 0.075, 0], life: 1.45, size: R, rotLife: [0, W_STAR, 0], bursts: [[0.1, 1]],
    sizeLife: [[0, 0.4, 0, 3], [0.2, 1], [0.72, 1], [1, 0.1]], alpha: [[0, 0], [0.15, 0.8], [0.7, 0.7], [1, 0]] },
  // vortex strokes spiralling inward, staged (more and faster toward the gulp)
  { name: "S07 Vortex", mat: "stroke", mode: "mesh", mesh: "ground", fudge: 20, pos: [0, 0.09, 0], life: [0.45, 0.6], size: [2.6, 3.2], rot3: YAW, rotLife: [0, [-7, -5], 0],
    bursts: [[0.1, 2], [0.3, 2], [0.5, 3], [0.68, 3], [0.85, 4], [1.0, 3]], sizeLife: [[0, 1.3], [1, 0.35]] , flip: F28 },
  // mid: tentacles grab the body and pull it down (sucked into the centre)
  { name: "S08 TentaclesA", mat: "claw", mode: "mesh", mesh: "tentA", fudge: 8, life: 1.15, size: 1, rot3: YAW, rotLife: [0, 0.9, 0], bursts: [[0.12, 1]],
    sizeLife: [[0, 0.15, 0, 5], [0.22, 1.0], [0.45, 1.0], [1, 0.02, -1.2, 0]], alpha: [[0, 1], [0.9, 1], [1, 0]] },
  { name: "S09 TentaclesB", mat: "claw", mode: "mesh", mesh: "tentB", fudge: 9, life: 1.0, size: 1, rot3: YAW, rotLife: [0, -1.1, 0], bursts: [[0.22, 1]],
    sizeLife: [[0, 0.2, 0, 5], [0.22, 1.0], [0.4, 1.0], [1, 0.02, -1.2, 0]], alpha: [[0, 1], [0.9, 1], [1, 0]] },
  { name: "S10 MeltDrips", mat: "claw", mode: "stretch", vs: 0.05, ls: 2.6, fudge: 6, bursts: [[0.3, 5], [0.5, 7], [0.7, 9], [0.9, 8]],
    shape: { type: "circle", r: 1.1, th: 1, y: 3.5 }, vel: { y: [-11, -8], space: "world" }, life: [0.3, 0.45], size: [0.14, 0.24],
    sizeLife: [[0, 0.6], [1, 1]], alpha: [[0, 0], [0.15, 1], [0.85, 1], [1, 0]] },
  { name: "S11 EdgeSplash", mat: "claw", mode: "stretch", vs: 0.07, ls: 1.3, fudge: 7, bursts: [[0.02, 14]], shape: { type: "circle", r: 2.2, th: 0.3, y: 0.1 },
    vel: { y: [3.5, 6], space: "world" }, speed: [1, 2.5], gravity: 2.2, drag: 1, life: [0.35, 0.55], size: [0.25, 0.45], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "S12 InkBlobsIn", mat: "shard", fudge: 5, bursts: [[0.35, 6], [0.6, 8], [0.85, 10]], shape: { type: "circle", r: 2.8, th: 0.4, y: 0.4 },
    vel: { rad: [-4.5, -3], y: [-0.3, 0.2] }, rot: RAND, rotZ: [-8, 8], life: [0.4, 0.6], size: [0.18, 0.34], sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 0], [0.1, 1], [1, 1]] },
  // top: lilac motes and streaks sucked inward, final gulp flash
  { name: "S13 MotesIn", mat: "mote", fudge: -8, bursts: [[0.2, 12], [0.45, 18], [0.7, 24], [0.9, 20]], shape: { type: "circle", r: 3.0, th: 0.5, y: 0.3 },
    vel: { rad: [-4, -2.5], orb: [1.5, 2.5], y: [0.2, 1.2] }, life: [0.5, 0.7], size: [0.05, 0.1], color: [[0, LIC], [1, VIC]],
    alpha: [[0, 0], [0.15, 1], [0.8, 1], [1, 0]], sizeLife: [[0, 1], [1, 0.3]] },
  { name: "S14 StreaksIn", mat: "streak", mode: "stretch", vs: 0.05, ls: 2.0, fudge: -6, bursts: [[0.5, 8], [0.75, 12], [0.95, 14]], shape: { type: "circle", r: 3.0, th: 0.2, y: 0.25 },
    vel: { rad: [-8, -6], orb: [2, 3] }, life: [0.25, 0.35], size: [0.07, 0.12], color: [[0, LIC], [1, VIC]], alpha: [[0, 0], [0.2, 1], [1, 0.3]] },
  { name: "S15 MistIn", mat: "mist", fudge: 45, bursts: [[0.1, 6], [0.4, 6], [0.7, 6]], shape: { type: "circle", r: 2.6, th: 0.3, y: 0.5 },
    vel: { rad: [-2.2, -1.4], orb: [1, 1.6], y: [-0.2, 0.2] }, rot: RAND, rotZ: [-0.8, 0.8], life: [0.8, 1.0], size: [1.6, 2.4],
    color: [[0, INKC], [1, SHC]], alpha: [[0, 0], [0.25, 0.5], [1, 0]], sizeLife: [[0, 1], [1, 0.5]], flip: F44 },
  { name: "S16 GulpRing", mat: "shock", mode: "mesh", mesh: "shock", fudge: 15, pos: [0, 0.1, 0], life: 0.3, size: R * 1.1, rot3: YAW, bursts: [[T_GULP - 0.2, 1]],
    sizeLife: [[0, 1], [1, 0.1]], alpha: [[0, 0], [0.3, 0.7], [1, 0.3]] },
  { name: "S17 GulpFlash", mat: "star", fudge: -10, pos: [0, 0.35, 0], life: 0.22, size: 3.0, rot: [0, 0.4], bursts: [[T_GULP, 1]],
    sizeLife: [[0, 0.2], [0.25, 1], [1, 0]], alpha: [[0, 1], [1, 0.5]] },
  { name: "S18 GulpScratch", mat: "scratch", mode: "mesh", mesh: "quad", fudge: 18, pos: [0, 0.08, 0], life: 0.5, size: 2.8, rot3: YAW, bursts: [[T_GULP, 1]],
    sizeLife: [[0, 0.8], [1, 1.1]], alpha: [[0, 1], [1, 0]] },
  { name: "S19 GulpPuff", mat: "mote", fudge: -8, bursts: [[T_GULP, 24]], shape: { type: "circle", r: 0.5, th: 1, y: 0.2 },
    vel: { y: [2, 4], space: "world" }, speed: [0.5, 1.5], drag: 2, noise: { s: 0.4, f: 1.2, sp: 0.8 }, life: [0.6, 1.0], size: [0.05, 0.1],
    color: [[0, LIC], [1, VIC]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
];

// ================================================================ RISE (emerge), duration 1.6
const T_PEAK = 0.9;
const RISE = [
  { name: "R01 Pool", mat: "pool", mode: "mesh", mesh: "quad", fudge: 60, pos: [0, 0.03, 0], life: 1.45, size: 2 * R * 1.3, rot3: YAW, rotLife: [0, -0.4, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.1, 0, 6], [0.12, 1.06], [0.2, 1.0], [0.62, 1.04], [0.85, 0.6], [1, 0]], alpha: [[0, 0], [0.02, 1], [0.9, 1], [1, 0]] },
  { name: "R02 PoolRim", mat: "poolGlow", mode: "mesh", mesh: "quad", fudge: 34, pos: [0, 0.04, 0], life: 1.45, size: 2 * R * 1.3, rot3: YAW, rotLife: [0, -0.4, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.1, 0, 6], [0.12, 1.06], [0.2, 1.0], [0.62, 1.04], [0.85, 0.6], [1, 0]], alpha: [[0, 0.9], [0.55, 0.9], [0.62, 1], [1, 0]] },
  { name: "R03 CircleRings", mat: "line", mode: "mesh", mesh: "cRings", fudge: 24, pos: [0, 0.06, 0], life: 1.4, size: R, rotLife: [0, -W_RING, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.4, 0, 4], [0.12, 1], [0.64, 1.02], [0.85, 0.7], [1, 0.1]], alpha: [[0, 0.5], [0.1, 1], [0.6, 0.85], [1, 0]] },
  { name: "R04 CircleRunes", mat: "runes", mode: "mesh", mesh: "cRunes", fudge: 23, pos: [0, 0.065, 0], life: 1.35, size: R, rotLife: [0, -W_RUNE, 0], bursts: [[0.04, 1]],
    sizeLife: [[0, 0.5, 0, 4], [0.12, 1], [0.64, 1.02], [0.85, 0.7], [1, 0.1]], alpha: [[0, 0], [0.1, 0.95], [0.6, 0.85], [1, 0]] },
  { name: "R05 CircleStar", mat: "line", mode: "mesh", mesh: "cStar", fudge: 21, pos: [0, 0.075, 0], life: 1.3, size: R, rotLife: [0, -W_STAR, 0], bursts: [[0.08, 1]],
    sizeLife: [[0, 0.4, 0, 4], [0.15, 1], [0.64, 1.02], [1, 0.1]], alpha: [[0, 0], [0.12, 0.8], [0.6, 0.75], [1, 0]] },
  { name: "R06 CircleFlare", mat: "line", mode: "mesh", mesh: "cRings", fudge: 19, pos: [0, 0.08, 0], life: 0.45, size: R, rot3: [0, -W_RING * T_PEAK, 0], rotLife: [0, -W_RING, 0], bursts: [[T_PEAK, 1]],
    sizeLife: [[0, 1, 0, 2], [1, 1.45]], alpha: [[0, 1], [1, 0]] },
  { name: "R07 VortexOut", mat: "stroke", mode: "mesh", mesh: "ground", fudge: 20, pos: [0, 0.09, 0], life: [0.4, 0.55], size: [2.0, 2.6], rot3: YAW, rotLife: [0, [5, 7], 0],
    bursts: [[0.0, 3], [0.2, 2], [0.4, 3], [0.6, 3], [T_PEAK, 4]], sizeLife: [[0, 0.5], [1, 1.4]], flip: F28 },
  // ink column: geyser tendrils grow with the body, burst open at the peak; stretched ink spikes stream upward, staged
  { name: "R08 GeyserA", mat: "claw", mode: "mesh", mesh: "geyserA", fudge: 8, life: 1.15, size: 1, rot3: YAW, rotLife: [0, 0.7, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.05, 0, 1.6], [0.78, 1.0], [0.86, 1.22], [1, 1.32]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "R09 GeyserB", mat: "claw", mode: "mesh", mesh: "geyserB", fudge: 9, life: 1.1, size: 1, rot3: YAW, rotLife: [0, -0.9, 0], bursts: [[0.06, 1]],
    sizeLife: [[0, 0.1, 0, 2], [0.77, 1.0], [0.86, 1.25], [1, 1.35]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "R10 InkStream", mat: "claw", mode: "stretch", vs: 0.06, ls: 1.8, fudge: 6, bursts: [[0, 6], [0.15, 6], [0.3, 8], [0.45, 10], [0.6, 12], [0.75, 14]],
    shape: { type: "circle", r: 1.3, th: 0.5, y: 0.2 }, vel: { y: [9, 14], space: "world" }, speed: [0.3, 1.2], drag: 1.2, gravity: 1.0,
    life: [0.45, 0.65], size: [0.3, 0.55], sizeLife: [[0, 1], [1, 0.5]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "R11 RaysUp", mat: "streak", mode: "stretch", vs: 0.04, ls: 4, fudge: -6, bursts: [[0.05, 4], [0.25, 6], [0.5, 8], [0.7, 10]],
    shape: { type: "circle", r: 1.6, th: 0.6, y: 0.1 }, vel: { y: [12, 18], space: "world" }, drag: 2, life: [0.3, 0.45], size: [0.08, 0.14],
    color: [[0, LIC], [1, VIC]], alpha: [[0, 1], [1, 0]] },
  { name: "R12 ShardsUp", mat: "shard", fudge: 5, bursts: [[0.1, 4], [0.35, 6], [0.6, 8]], shape: { type: "circle", r: 1.6, th: 0.6, y: 0.3 },
    vel: { y: [6, 10], space: "world" }, speed: [1, 3], drag: 0.6, gravity: 2.6, rot: RAND, rotZ: [-10, 10], life: [0.6, 0.9], size: [0.16, 0.3],
    alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "R13 BaseMist", mat: "mist", fudge: 45, bursts: [[0, 6], [0.3, 4], [0.6, 4]], shape: { type: "circle", r: 2.2, th: 0.5, y: 0.5 },
    vel: { y: [0.6, 1.4], space: "world", orb: [1, 1.6] }, rot: RAND, rotZ: [-0.6, 0.6], life: [0.8, 1.1], size: [1.6, 2.6],
    color: [[0, INKC], [1, SHC]], alpha: [[0, 0], [0.2, 0.45], [1, 0]], sizeLife: [[0, 0.6], [1, 1.2]], flip: F44 },
  // peak burst at 0.9 s (landing)
  { name: "R14 PeakStar", mat: "star", fudge: -10, pos: [0, 0.35, 0], life: 0.22, size: 5.0, rot: [0, 0.4], bursts: [[T_PEAK, 1]],
    sizeLife: [[0, 0.3], [0.2, 1.1], [1, 0]], alpha: [[0, 1], [1, 0.5]] },
  { name: "R15 PeakGlow", mat: "glow", fudge: -2, pos: [0, 1.0, 0], life: 0.3, size: 3.0, bursts: [[T_PEAK, 1]],
    sizeLife: [[0, 0.6], [0.3, 1], [1, 1.2]], alpha: [[0, 0.35], [1, 0]] },
  { name: "R16 PeakShock", mat: "shock", mode: "mesh", mesh: "shock", fudge: 15, pos: [0, 0.12, 0], life: 0.45, size: R * 1.6, rot3: YAW, bursts: [[T_PEAK, 1]],
    sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.85], [0.5, 0.6], [1, 0]] },
  { name: "R17 PeakShock2", mat: "shockDim", mode: "mesh", mesh: "shock", fudge: 14, pos: [0, 0.16, 0], life: 0.55, size: R * 1.2, rot3: YAW, bursts: [[T_PEAK + 0.08, 1]],
    sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.7], [0.5, 0.5], [1, 0]] },
  { name: "R18 PeakLine", mat: "line", mode: "mesh", mesh: "shockLine", fudge: 13, pos: [0, 0.13, 0], life: 0.45, size: R * 1.62, rot3: YAW, bursts: [[T_PEAK, 1]],
    sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 1], [0.6, 0.8], [1, 0]] },
  { name: "R19 PeakScratch", mat: "scratch", mode: "mesh", mesh: "quad", fudge: 18, pos: [0, 0.09, 0], life: 0.55, size: 3.6, rot3: YAW, bursts: [[T_PEAK, 1]],
    sizeLife: [[0, 0.7, 0, 3], [0.2, 1.05], [1, 1.12]], alpha: [[0, 1], [0.4, 0.85], [1, 0]] },
  { name: "R20 PeakShards", mat: "shard", mode: "stretch", vs: 0.06, ls: 1.2, fudge: 5, pos: [0, 1.0, 0], bursts: [[T_PEAK, 26]], shape: { type: "sphere", r: 0.8, th: 0.5 },
    speed: [6, 11], vel: { y: [2, 5], space: "world" }, drag: 1, gravity: 3, life: [0.5, 0.8], size: [0.16, 0.3], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "R21 PeakTongues", mat: "claw", mode: "stretch", vs: 0.07, ls: 1.5, fudge: 6, pos: [0, 0.3, 0], bursts: [[T_PEAK, 16]], shape: { type: "circle", r: 1.4, th: 0.4 },
    speed: [5, 9], vel: { y: [3, 6], space: "world" }, drag: 4, gravity: 2, life: [0.3, 0.45], size: [0.35, 0.6], sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "R22 PeakSparks", mat: "streak", mode: "stretch", vs: 0.06, ls: 1.4, fudge: -7, pos: [0, 0.8, 0], bursts: [[T_PEAK, 30]], shape: { type: "sphere", r: 0.5, th: 1 },
    speed: [7, 12], drag: 2.5, gravity: 1.2, life: [0.35, 0.6], size: [0.05, 0.09], color: [[0, WLC], [1, VIC]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "R23 PeakDust", mat: "dust", fudge: 3, bursts: [[T_PEAK + 0.03, 10]], shape: { type: "circle", r: 2.4, th: 0.6, y: 0.3 },
    vel: { y: [0.8, 1.5], space: "world", rad: [0.8, 1.6] }, drag: 1.5, rot: RAND, rotZ: [-0.6, 0.6], life: [0.9, 1.3], size: [1.2, 1.9],
    alpha: [[0, 0], [0.15, 0.3], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
  { name: "R24 Motes", mat: "mote", fudge: -8, bursts: [[0.2, 10], [0.5, 14], [T_PEAK, 30]], shape: { type: "circle", r: 2.6, th: 1, y: 0.2 },
    vel: { y: [1.0, 2.4], space: "world" }, noise: { s: 0.5, f: 1.2, sp: 0.8 }, life: [0.8, 1.4], size: [0.05, 0.1], color: [[0, LIC], [1, VIC]],
    alpha: [[0, 0], [0.1, 1], [0.7, 0.8], [1, 0]], sizeLife: [[0, 0.5], [0.15, 1], [1, 0.3]] },
];

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
  dust: { tex: "T_Puff_Sheet", tint: [0.6, 0.28, 1.3], add: true },
  mist: { tex: "T_Puff_Sheet", tint: [1, 1, 1], add: false },
};

const T_RISE = 1.9;
function scen(label, camera, h, A, B, th) {
  const ev = (e) => (th != null ? Object.assign(e, { targetHeight: th }) : e);
  return Object.assign({ label, camera,
    actors: [
      { path: [[0, A, 0]], height: h, reveal: [[0, 1], [0.3, 1], [1.2, 0]] },
      { path: [[0, B, 200]], height: h, reveal: [[0, 0], [T_RISE, 0], [T_RISE + 0.9, 1]] },
    ],
    events: [ev({ t: 0, system: "sink", actor: 0 }), ev({ t: T_RISE, system: "rise", actor: 1 })] }, th != null ? { targetHeight: th } : {});
}
const A0 = [0, 0, 0], B0 = [6, 0, -3];
const SCENE = {
  name: "MordianShadowGate",
  bloom: { threshold: 0.95, intensity: 1.4, scatter: 0.7, tint: [0.9, 0.8, 1.0] }, tonemapping: "neutral",
  scale: { input: "areaRadius", reference: R, min: 0.25, max: 1.6 },
  systems: [
    { id: "sink", role: "static", loop: false, duration: 1.6, layers: SINK },
    { id: "rise", role: "static", loop: false, duration: 1.6, layers: RISE },
  ],
  demo: {
    interval: 4.6, dummy: { height: 1.8 }, bg: [0.16, 0.15, 0.18], floor: [0.3, 0.29, 0.29], grid: [0.36, 0.35, 0.35],
    scenarios: {
      full: scen("보스 (사라짐 → 솟아오름)", { target: [3, 2.0, -1.5], dist: 17, yaw: -80, pitch: 18 }, 7.6, A0, B0),
      sink: scen("사라짐 근접", { target: [0, 2.0, 0], dist: 10, yaw: -70, pitch: 16 }, 7.6, A0, B0),
      rise: scen("솟아오름 근접", { target: [6, 2.4, -3], dist: 10.5, yaw: -70, pitch: 14 }, 7.6, A0, B0),
      clone: scen("그림자 분신 (areaRadius 1 → ×0.33)", { target: [3.6, 2.4, -1.5], dist: 19.5, yaw: -75, pitch: 20 }, 1.8, [0, 0, 0], [2.4, 0, -1], 1.0),
    },
  },
};
