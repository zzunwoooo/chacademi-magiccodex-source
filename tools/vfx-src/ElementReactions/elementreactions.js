// ElementReactions v2 (2026-10-20, CC rework) — one impact system per reaction / placement.
// Owner: reactions now give crowd control (normal CC 3 s, hard CC 1 s). Effects read clearly but stay short;
// the long part of a CC is shown by the status icons above the head, the VFX shows the moment it lands
// (hard CC: the VFX holds for its 1 s).
//
// Placement (server ReactionVfx):
//   body systems   P = target feet, up = world up. Authored for a 1.8 m humanoid. Instance scale
//                  s = clamp((height/1.8 + width/0.6) / 2, 0.6, 4) — width/height from the ModelEngine hitbox when modelled.
//   area systems   P = centre feet. Authored at the gameplay radius (steam 3, wildfire 2, sandstorm 4, rainbow 4);
//                  scale = (radius + targetWidth/2) / (radius + 0.3) so a big mob's ring reaches past its body.
//   shock          P = source chest, bolt along local +Z for BOLT_LEN; yaw toward the next target, scale = distance / BOLT_LEN.
const BOLT_LEN = 3;
const TAU = 2 * Math.PI;
const F44 = { x: 4, y: 4 }, F22 = { x: 2, y: 2 };

// ---------------------------------------------------------------- meshes
function merge(parts) {
  const verts = [], uvs = [], tris = [];
  for (const p of parts) { const o = verts.length; verts.push(...p.verts); uvs.push(...p.uvs); tris.push(...p.tris.map(i => i + o)); }
  return { verts, uvs, tris };
}
const ringStrip = (r, w, seg = 96) => ribbon(seg, s => {
  const phi = -s * TAU, c = Math.cos(phi), n = Math.sin(phi);
  return [[c * (r - w / 2), 0, n * (r - w / 2)], [c * (r + w / 2), 0, n * (r + w / 2)]];
});
// N upright quads standing on a circle of radius r (each U 0..1 across, V 0 = ground .. 1 = top), facing outward
function uprightRing(n, r, w, h, lean = 0) {
  const parts = [];
  for (let k = 0; k < n; k++) {
    const a = k / n * TAU, c = Math.cos(a), s = Math.sin(a), tx = -s * w / 2, tz = c * w / 2, lx = c * lean, lz = s * lean;
    parts.push({ verts: [[c * r - tx, 0, s * r - tz], [c * r + tx, 0, s * r + tz], [c * r + tx + lx, h, s * r + tz + lz], [c * r - tx + lx, h, s * r - tz + lz]],
      uvs: [[0, 0], [1, 0], [1, 1], [0, 1]], tris: [0, 1, 2, 0, 2, 3] });
  }
  return merge(parts);
}
// continuous cylinder band (U around once, V up)
const band = (r, h, seg = 64) => ribbon(seg, s => { const phi = -s * TAU, c = Math.cos(phi), n = Math.sin(phi); return [[c * r, 0, n * r], [c * r, h, n * r]]; });

var EXTRA_MESHES = {
  ring: ringStrip(1, 0.05),            // thin crisp ring, size = radius
  ringW: ringStrip(1, 0.14),           // wider ring
  rings2: merge([ringStrip(1, 0.045), ringStrip(0.86, 0.025)]),
  flameRing: uprightRing(14, 1, 0.55, 1),       // flame tongues standing on the ring (radius 1, height 1)
  spikeRing: uprightRing(7, 0.42, 0.36, 1, -0.16), // crystal / earth spikes leaning in over the legs
  chainBand: band(1, 0.24),
  dustBand: band(1, 1),
  bolt: {
    verts: [[-0.22, 0, 0], [0.22, 0, 0], [0.22, 0, BOLT_LEN], [-0.22, 0, BOLT_LEN],
            [0, -0.22, 0], [0, 0.22, 0], [0, 0.22, BOLT_LEN], [0, -0.22, BOLT_LEN]],
    uvs: [[0, 0], [0, 1], [1, 1], [1, 0], [0, 0], [0, 1], [1, 1], [1, 0]],
    tris: [0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7],
  },
};

// ---------------------------------------------------------------- shared helpers
const fade = [[0, 1], [0.6, 0.8], [1, 0]];
const popIn = [[0, 0], [0.12, 1], [0.7, 0.8], [1, 0]];
const hold = (life, out = 0.18) => [[0, 0], [Math.min(0.1, 0.08 / life), 1], [1 - out / life, 0.9], [1, 0]];
// expanding ground ring (mesh): radius 0 → R
const groundRing = (name, mat, R, t0, life, a = 0.9, mesh = "ring", fudge = 0) => ({ name, mat, mode: "mesh", mesh, fudge, pos: [0, 0.06, 0], bursts: [[t0, 1]],
  life, size: R, rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.12, 0, 2.6], [0.6, 0.96], [1, 1]], alpha: [[0, a], [0.55, a * 0.7], [1, 0]] });
// held ground decal (quad mesh), quick grow then hold
const decal = (name, mat, D, life, extra = {}) => Object.assign({ name, mat, mode: "mesh", mesh: "quad", fudge: 3, pos: [0, 0.04, 0], bursts: [[0, 1]],
  life, size: D, rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.4, 0, 3], [0.18, 1], [1, 1.04]], alpha: hold(life) }, extra);
const flash = (name, mat, y, size, life = 0.16, a = 0.85) => ({ name, mat, pos: [0, y, 0], bursts: [[0, 1]], life, size,
  sizeLife: [[0, 0.4], [0.25, 1], [1, 1.1]], alpha: [[0, a], [1, 0]] });
const sparks = (name, mat, y, n, sp, life, sz = [0.05, 0.08], extra = {}) => Object.assign({ name, mat, mode: "stretch", vs: 0.07, ls: 1.6, fudge: -3,
  pos: [0, y, 0], bursts: [[0, n]], shape: { type: "sphere", r: 0.2, th: 1 }, speed: sp, drag: 3.5, gravity: 0.5, life, size: sz, alpha: fade }, extra);
const motes = (name, mat, y, n, r, life, t0 = 0, extra = {}) => Object.assign({ name, mat, fudge: -2, pos: [0, y, 0], bursts: [[t0, n]],
  shape: { type: "circle", r, th: 0.8 }, speed: [0, 0.2], vel: { y: [0.6, 1.4], space: "world" }, drag: 1, life, size: [0.06, 0.11],
  alpha: [[0, 0], [0.15, 1], [0.7, 0.8], [1, 0]] }, extra);
const icon = (name, mat, y, size, life, t0 = 0.04, extra = {}) => Object.assign({ name, mat, fudge: -4, pos: [0, y, 0], bursts: [[t0, 1]], life, size,
  sizeLife: [[0, 1.6], [0.14, 0.95], [0.3, 1], [1, 1]], alpha: hold(life, 0.2) }, extra);

// ---------------------------------------------------------------- 1 증기 폭발 (화+물) area r3: small damage + lift. 0.8 s
const STEAM = [
  flash("Flash", "glowWarm", 0.8, 1.6, 0.18),
  { name: "Core", mat: "starWarm", fudge: -4, pos: [0, 0.8, 0], bursts: [[0, 1]], life: 0.2, size: 2.2, rot: [0, TAU], sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  groundRing("Shock", "ringPale", 3, 0, 0.42, 0.95, "ringW"),
  groundRing("Shock2", "ringSteam", 2.8, 0.07, 0.5, 0.7),
  decal("Scald", "crackSteam", 3.4, 0.6, { alpha: [[0, 0], [0.08, 0.7], [0.5, 0.45], [1, 0]] }),
  { name: "PuffsOut", mat: "steamAdd", fudge: 4, pos: [0, 0.35, 0], bursts: [[0, 22]], shape: { type: "circle", r: 0.4, th: 1 },
    speed: [6, 8.5], drag: 5.5, life: [0.55, 0.75], size: [0.9, 1.4], rot: [0, TAU], vel: { y: [0.6, 1.2], space: "world" },
    sizeLife: [[0, 0.5], [1, 1.5]], alpha: [[0, 0], [0.08, 0.75], [0.5, 0.4], [1, 0]], flip: F44 },
  { name: "Column", mat: "steamAdd", fudge: 5, pos: [0, 0.3, 0], bursts: [[0, 8], [0.08, 6]], shape: { type: "circle", r: 0.35, th: 1 },
    speed: [0.2, 0.6], vel: { y: [3.5, 5.5], space: "world" }, drag: 2.5, life: [0.5, 0.7], size: [0.8, 1.2], rot: [0, TAU],
    sizeLife: [[0, 0.5], [1, 1.6]], alpha: [[0, 0.8], [0.5, 0.45], [1, 0]], flip: F44 },
  { name: "Drops", mat: "streakWater", mode: "stretch", vs: 0.06, ls: 1.4, fudge: -3, pos: [0, 0.6, 0], bursts: [[0, 22]], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [5, 8], drag: 2, gravity: 1.6, life: [0.3, 0.45], size: [0.05, 0.08], alpha: fade },
  sparks("Hot", "streakFire", 0.8, 12, [5, 9], [0.18, 0.3]),
];

// ---------------------------------------------------------------- 2 불씨 확산 (화+바람) area r2: burn 3 s. 0.8 s
const WILDFIRE = [
  flash("Flash", "glowFire", 0.9, 1.4, 0.18),
  groundRing("Ring", "ringFire", 2, 0, 0.45, 1, "ringW"),
  decal("Scorch", "crackLava", 3.2, 0.7, { alpha: [[0, 0], [0.08, 0.8], [0.5, 0.5], [1, 0]] }),
  { name: "Wall", mat: "flameTongue", mode: "mesh", mesh: "flameRing", fudge: 1, pos: [0, 0, 0], bursts: [[0.02, 1]], life: 0.6, size: 2,
    rot3: [0, [0, TAU], 0], rotLife: [0, 1.6, 0], sizeLife: [[0, 0.25, 0, 3], [0.25, 1], [1, 1.05]], alpha: [[0, 0], [0.1, 1], [0.6, 0.8], [1, 0]], flip: F44 },
  { name: "Tongues", mat: "flameTongue", fudge: 0, pos: [0, 0.4, 0], bursts: [[0.03, 14]], shape: { type: "circle", r: 1.9, th: 0.25 },
    speed: [0, 0.2], vel: { y: [0.8, 1.4], space: "world", orb: 3 }, life: [0.4, 0.55], size: [0.7, 1.0], sizeLife: [[0, 0.4], [0.3, 1], [1, 0.8]], alpha: fade, flip: F44 },
  { name: "Swirl", mat: "streakFire", mode: "stretch", vs: 0.06, ls: 1.8, fudge: -3, pos: [0, 0.5, 0], bursts: [[0, 24]], shape: { type: "circle", r: 0.6, th: 1 },
    speed: [3, 5], vel: { y: [0.4, 1.2], space: "world", orb: 6 }, drag: 2, life: [0.35, 0.55], size: [0.05, 0.08], alpha: fade },
  motes("Embers", "dotFire", 0.3, 18, 2, [0.5, 0.75], 0.05, { size: [0.04, 0.07] }),
];
// on every burned enemy (body)
const BURN = [
  flash("Glow", "glowFire", 0.9, 1.1, 0.2),
  { name: "Flames", mat: "flameTongue", fudge: 0, pos: [0, 0.3, 0], bursts: [[0, 6]], shape: { type: "circle", r: 0.35, th: 1 },
    speed: [0, 0.2], vel: { y: [1.2, 2], space: "world" }, life: [0.35, 0.5], size: [0.5, 0.75], alpha: fade, flip: F44 },
  sparks("Sparks", "streakFire", 0.9, 10, [3, 6], [0.2, 0.32]),
];

// ---------------------------------------------------------------- 3 용암 분출 (화+대지) body: heal seal 3 s. 0.9 s
const MAGMA = [
  decal("Crack", "crackLava", 2.4, 0.9, { color: [[0, [1, 0.95, 0.7]], [0.3, [1, 0.6, 0.2]], [1, [0.75, 0.15, 0.05]]] }),
  groundRing("Ring", "ringFire", 1.3, 0, 0.4, 0.9),
  flash("Glow", "glowFire", 0.25, 1.6, 0.3, 0.7),
  { name: "Spurt", mat: "streakLava", mode: "stretch", vs: 0.05, ls: 1.2, fudge: -2, pos: [0, 0.1, 0], bursts: [[0.02, 14]], shape: { type: "circle", r: 0.5, th: 1 },
    speed: [0.4, 1.2], vel: { y: [4, 6], space: "world" }, gravity: 1.8, life: [0.4, 0.6], size: [0.07, 0.12], alpha: fade },
  { name: "Blobs", mat: "dotFire", fudge: -3, pos: [0, 0.1, 0], bursts: [[0.04, 10]], shape: { type: "circle", r: 0.4, th: 1 },
    speed: [0.6, 1.4], vel: { y: [2.5, 4], space: "world" }, gravity: 1.6, life: [0.45, 0.6], size: [0.1, 0.16],
    color: [[0, [1, 0.85, 0.45]], [1, [1, 0.25, 0.06]]], alpha: fade },
  { name: "Chain", mat: "chainFire", mode: "mesh", mesh: "chainBand", fudge: -1, pos: [0, 0.75, 0], bursts: [[0.06, 1]], life: 0.8, size: 0.7,
    rotLife: [0, 3, 0], sizeLife: [[0, 1.8, 0, -4], [0.2, 1], [1, 0.95]], alpha: hold(0.8) },
  icon("Seal", "sealFire", 2.25, 0.75, 0.85, 0.08),
  { name: "Smoke", mat: "mistDark", fudge: 7, pos: [0, 0.3, 0], bursts: [[0.05, 4]], shape: { type: "circle", r: 0.5, th: 1 }, speed: [0.2, 0.5],
    vel: { y: [0.6, 1.0], space: "world" }, life: [0.6, 0.8], size: [0.8, 1.1], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.4], [1, 0]], flip: F44 },
];

// ---------------------------------------------------------------- 4 태양 플레어 (화+빛) body: glow 3 s + weaken 3 s. 0.8 s
const FLARE = [
  { name: "Star", mat: "starSun", fudge: -4, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.28, size: 3.0, rot: [0, 45 * D2R], sizeLife: [[0, 0.2], [0.25, 1.1], [1, 0]] },
  flash("Glow", "glowSun", 1.0, 1.4, 0.22, 0.8),
  { name: "Corona", mat: "ringSunB", fudge: 1, pos: [0, 1.0, 0], bursts: [[0, 1], [0.07, 1]], life: 0.32, size: 2.4, sizeLife: [[0, 0.25, 0, 2], [1, 1]], alpha: [[0, 0.95], [1, 0]] },
  { name: "Rays", mat: "streakSun", mode: "stretch", vs: 0.05, ls: 4, fudge: -3, pos: [0, 1.0, 0], bursts: [[0, 14]], shape: { type: "sphere", r: 0.1, th: 1 },
    speed: [5, 8], drag: 7, life: [0.22, 0.32], size: [0.06, 0.1], alpha: fade },
  groundRing("Ring", "ringSun", 1.5, 0, 0.45, 0.9, "rings2"),
  { name: "Drain", mat: "streakSun", mode: "stretch", vs: 0.05, ls: 2.2, fudge: -2, pos: [0, 2.2, 0], bursts: [[0.1, 8], [0.25, 8]], shape: { type: "circle", r: 0.55, th: 0.4 },
    speed: [0, 0.1], vel: { y: [-3.5, -2.5], space: "world" }, life: [0.32, 0.45], size: [0.05, 0.08], alpha: popIn },
  icon("Weak", "downSun", 2.25, 0.6, 0.7, 0.12, { vel: { y: [-0.4, -0.4] } }),
];

// ---------------------------------------------------------------- 5 잿불 낙인 (화+어둠) body: mana burn (instant). 0.9 s
const EMBER_CURSE = [
  decal("Sigil", "sigilEmber", 1.9, 0.85, { rotLife: [0, 2.5, 0], alpha: [[0, 0], [0.08, 1], [0.7, 0.8], [1, 0]] }),
  groundRing("Ring", "ringEmber", 1.4, 0, 0.4, 0.9),
  { name: "Mist", mat: "mistDark", fudge: 7, pos: [0, 0.8, 0], bursts: [[0, 5]], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [0.2, 0.6],
    life: [0.6, 0.8], size: [0.9, 1.2], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: F44 },
  { name: "ManaOut", mat: "shardMana", fudge: -2, pos: [0, 1.0, 0], bursts: [[0.03, 12]], shape: { type: "sphere", r: 0.35, th: 0.5 },
    speed: [1.5, 2.6], vel: { y: [1.5, 2.5], space: "world", orb: 4 }, drag: 2, life: [0.5, 0.7], size: [0.18, 0.28], rot: [-0.5, 0.5],
    color: [[0, [0.55, 0.85, 1]], [0.45, [0.6, 0.6, 1]], [0.7, [1, 0.55, 0.25]], [1, [0.9, 0.2, 0.05]]], alpha: [[0, 0], [0.1, 1], [0.7, 0.9], [1, 0]] },
  { name: "Burn", mat: "dotFire", fudge: -3, pos: [0, 1.6, 0], bursts: [[0.3, 14]], shape: { type: "sphere", r: 0.6, th: 0.5 },
    speed: [0.3, 0.8], vel: { y: [0.8, 1.4], space: "world" }, life: [0.35, 0.5], size: [0.04, 0.07],
    color: [[0, [1, 0.8, 0.4]], [1, [0.8, 0.15, 0.05]]], alpha: fade },
  icon("Mana", "manaCrack", 2.2, 0.7, 0.75, 0.05, { color: [[0, [0.6, 0.85, 1]], [0.45, [0.7, 0.7, 1]], [0.65, [1, 0.6, 0.3]], [1, [1, 0.3, 0.1]]] }),
];

// ---------------------------------------------------------------- 6 연쇄 번개 (물+바람): bolt per jump + disarm 1 s on each struck target
const SHOCK = [
  { name: "Bolt", mat: "bolt", mode: "mesh", mesh: "bolt", bursts: [[0, 1]], life: 0.24, size: 1, flip: { x: 1, y: 4, cycles: 3 }, alpha: [[0, 1], [0.6, 1], [1, 0]] },
  { name: "Bolt2", mat: "boltDim", mode: "mesh", mesh: "bolt", bursts: [[0.03, 1]], life: 0.2, size: 1, rot3: [0, 0, 0.6], flip: { x: 1, y: 4, cycles: 2 }, alpha: [[0, 0.8], [1, 0]] },
  { name: "SrcGlow", mat: "glowBolt", pos: [0, 0, 0], bursts: [[0, 1]], life: 0.16, size: 1.0, alpha: [[0, 0.9], [1, 0]] },
  { name: "HitGlow", mat: "glowBolt", pos: [0, 0, BOLT_LEN], bursts: [[0, 1]], life: 0.22, size: 1.5, sizeLife: [[0, 0.5], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "HitStar", mat: "starBolt", fudge: -4, pos: [0, 0, BOLT_LEN], bursts: [[0, 1]], life: 0.16, size: 1.6, rot: [0, TAU], sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  { name: "HitSparks", mat: "streakBolt", mode: "stretch", vs: 0.06, ls: 1.6, fudge: -3, pos: [0, 0, BOLT_LEN], bursts: [[0, 14]],
    shape: { type: "sphere", r: 0.1, th: 1 }, speed: [4, 8], drag: 4, life: [0.15, 0.26], size: [0.04, 0.06], alpha: fade },
];
const DISARM = [ // 1 s, on each target the chain struck
  groundRing("Ring", "ringBolt", 1.1, 0, 0.35, 0.9),
  decal("Glow", "glowBoltDecal", 1.6, 1.0, { alpha: [[0, 0], [0.05, 0.5], [0.8, 0.3], [1, 0]] }),
  { name: "Arcs", mat: "arcBolt", fudge: -3, pos: [0, 1.1, 0], rate: 20, bursts: [[0, 3]], shape: { type: "sphere", r: 0.45, th: 0.4 },
    life: [0.08, 0.13], size: [0.7, 1.0], rot: [0, TAU], alpha: [[0, 1], [1, 0.4]], flip: F22 },
  { name: "ArcsLow", mat: "arcBolt", fudge: -3, pos: [0, 0.35, 0], rate: 10, shape: { type: "circle", r: 0.5, th: 0.3 },
    life: [0.08, 0.12], size: [0.5, 0.7], rot: [0, TAU], alpha: [[0, 1], [1, 0.3]], flip: F22 },
  { name: "Crackle", mat: "streakBolt", mode: "stretch", vs: 0.06, ls: 1.2, fudge: -3, pos: [0, 1.0, 0], rate: 22, shape: { type: "sphere", r: 0.4, th: 0.5 },
    speed: [2, 4], drag: 5, life: [0.1, 0.18], size: [0.03, 0.05], alpha: fade },
  { name: "Hands", mat: "glowBolt", pos: [0, 1.0, 0], bursts: [[0, 1]], life: 1.0, size: 1.2, sizeLife: [[0, 1], [0.5, 0.85], [1, 1]], alpha: [[0, 0], [0.05, 0.5], [0.8, 0.35], [1, 0]] },
];
const DISARM_LEN = 1.0;

// ---------------------------------------------------------------- 7 진흙 (물+대지) body: slow 3 s. 0.9 s
const MUD = [
  decal("Splat", "mud", 2.4, 0.9, { fudge: 6, alpha: [[0, 0.9], [0.6, 0.75], [1, 0]], flip: F44 }),
  groundRing("Ring", "ringMud", 1.5, 0, 0.45, 1, "ringW"),
  decal("Sink", "swirlMud", 1.8, 0.9, { fudge: 2, rotLife: [0, 4, 0], alpha: [[0, 0], [0.1, 0.9], [0.7, 0.7], [1, 0]] }),
  icon("Slow", "downMud", 2.25, 0.6, 0.8, 0.1),
  { name: "Ankle", mat: "mudBand", mode: "mesh", mesh: "dustBand", fudge: 5, pos: [0, 0, 0], bursts: [[0.03, 1]], life: 0.85, size: 0.5,
    sizeLife: [[0, 1.6, 0, -3], [0.25, 1], [1, 0.9]], alpha: hold(0.85), flip: F44 },
  { name: "Clods", mat: "chunkMud", fudge: 2, pos: [0, 0.15, 0], bursts: [[0, 14]], shape: { type: "circle", r: 0.35, th: 1 },
    speed: [1.4, 2.6], vel: { y: [2.2, 3.4], space: "world" }, gravity: 2.4, life: [0.4, 0.55], size: [0.13, 0.22], rot: [0, TAU], alpha: [[0, 1], [0.75, 1], [1, 0]] },
  { name: "Drops", mat: "dotMud", fudge: 1, pos: [0, 0.2, 0], bursts: [[0, 16]], shape: { type: "circle", r: 0.3, th: 1 },
    speed: [2, 3.5], vel: { y: [1.8, 3], space: "world" }, gravity: 2.6, life: [0.35, 0.45], size: [0.05, 0.09], alpha: fade },
  { name: "Water", mat: "streakWater", mode: "stretch", vs: 0.05, ls: 1.4, fudge: -3, pos: [0, 0.3, 0], bursts: [[0, 12]], shape: { type: "circle", r: 0.3, th: 1 },
    speed: [2.5, 4], vel: { y: [2, 3], space: "world" }, gravity: 2, life: [0.25, 0.35], size: [0.04, 0.06], alpha: fade },
  { name: "Drip", mat: "dotMud", fudge: 1, pos: [0, 1.2, 0], rate: 14, shape: { type: "sphere", r: 0.35, th: 0.3 }, speed: [0, 0.1], gravity: 1.2,
    life: [0.35, 0.45], size: [0.05, 0.08], alpha: fade },
];

// ---------------------------------------------------------------- 8 무지개 (물+빛) area r4 at the caster: heal allies. 1.0 s (+ per ally HEAL)
const RB = [[0, [1, 0.5, 0.5]], [0.2, [1, 0.85, 0.45]], [0.4, [0.6, 1, 0.55]], [0.6, [0.45, 0.85, 1]], [0.8, [0.65, 0.55, 1]], [1, [1, 0.55, 0.85]]];
const RB1 = [[0, [1, 0.5, 0.5]], [0.5, [1, 0.85, 0.4]], [1, [0.6, 1, 0.5]]];
const RB2 = [[0, [0.5, 1, 0.7]], [0.5, [0.45, 0.85, 1]], [1, [0.6, 0.55, 1]]];
const RB3 = [[0, [0.75, 0.55, 1]], [0.5, [1, 0.5, 0.85]], [1, [1, 0.6, 0.5]]];
const RAINBOW = [
  Object.assign(groundRing("Ring", "ringSoft", 4, 0, 0.7, 0.95, "ringW"), { color: RB }),
  Object.assign(groundRing("Ring2", "ringSoft", 3.6, 0.1, 0.75, 0.7, "rings2"), { color: RB }),
  decal("Wash", "glowSoftDecal", 8, 0.8, { color: [[0, [0.7, 1, 0.8]], [1, [0.7, 0.8, 1]]], alpha: [[0, 0], [0.1, 0.35], [0.6, 0.25], [1, 0]] }),
  { name: "Pillar", mat: "streakSoft", mode: "stretch", vs: 0.05, ls: 3, fudge: -2, pos: [0, 0.2, 0], bursts: [[0, 6]], shape: { type: "circle", r: 0.4, th: 1 },
    speed: [0, 0.1], vel: { y: [5, 7], space: "world" }, drag: 3, life: [0.4, 0.55], size: [0.08, 0.12], color: RB, alpha: popIn },
  flash("Glow", "glowSoft", 1.0, 1.6, 0.3, 0.7),
  motes("Motes1", "dotSoft", 0.2, 12, 3.6, [0.6, 0.85], 0.02, { color: RB1 }),
  motes("Motes2", "dotSoft", 0.2, 12, 3.6, [0.6, 0.85], 0.08, { color: RB2 }),
  motes("Motes3", "dotSoft", 0.2, 12, 3.6, [0.6, 0.85], 0.14, { color: RB3 }),
  { name: "Crosses", mat: "crossHeal", fudge: -3, pos: [0, 0.5, 0], bursts: [[0.05, 6], [0.2, 5]], shape: { type: "circle", r: 3, th: 0.9 },
    speed: [0, 0.1], vel: { y: [1.0, 1.6], space: "world" }, life: [0.6, 0.8], size: [0.35, 0.5], alpha: [[0, 0], [0.15, 1], [0.7, 0.8], [1, 0]] },
];
const HEAL = [ // on each healed ally (body)
  groundRing("Ring", "ringHeal", 0.9, 0, 0.45, 0.9),
  { name: "Crosses", mat: "crossHeal", fudge: -3, pos: [0, 0.6, 0], bursts: [[0, 4]], shape: { type: "circle", r: 0.5, th: 0.6 },
    speed: [0, 0.1], vel: { y: [1.2, 1.8], space: "world" }, life: [0.5, 0.7], size: [0.3, 0.42], alpha: [[0, 0], [0.15, 1], [0.7, 0.8], [1, 0]] },
  motes("Motes", "dotHeal", 0.2, 10, 0.6, [0.5, 0.7]),
  flash("Glow", "glowHeal", 1.0, 1.0, 0.3, 0.5),
];

// ---------------------------------------------------------------- 9 심연 (물+어둠) body: poison 3 s. 0.9 s
const ABYSS = [
  decal("Pool", "swirlPoison", 2.2, 0.9, { fudge: 2, rotLife: [0, -6, 0], color: [[0, [0.7, 1, 0.5]], [1, [0.65, 0.4, 1]]] }),
  decal("Dark", "mistAbyss", 2.0, 0.9, { fudge: 8, alpha: [[0, 0], [0.12, 0.6], [0.7, 0.5], [1, 0]], flip: F44 }),
  groundRing("Ring", "ringPoison", 1.4, 0, 0.4, 0.9),
  { name: "Bubbles", mat: "bubblePoison", fudge: -2, pos: [0, 0.15, 0], bursts: [[0, 6]], rate: 18, shape: { type: "circle", r: 0.55, th: 0.8 },
    speed: [0, 0.2], vel: { y: [1.0, 1.9], space: "world", orb: 2 }, life: [0.35, 0.55], size: [0.1, 0.2],
    sizeLife: [[0, 0.4], [0.8, 1], [0.9, 1.4], [1, 1.6]], alpha: [[0, 0], [0.15, 1], [0.85, 0.9], [1, 0]] },
  { name: "Fume", mat: "steamPoison", fudge: 4, pos: [0, 0.4, 0], bursts: [[0, 6]], shape: { type: "circle", r: 0.45, th: 1 }, speed: [0.3, 0.6],
    vel: { y: [0.8, 1.4], space: "world" }, life: [0.6, 0.8], size: [0.7, 1.0], rot: [0, TAU], sizeLife: [[0, 0.6], [1, 1.4]],
    alpha: [[0, 0], [0.15, 0.6], [1, 0]], flip: F44 },
  { name: "Splash", mat: "dotPoison", fudge: -3, pos: [0, 0.2, 0], bursts: [[0, 14]], shape: { type: "circle", r: 0.3, th: 1 },
    speed: [1.5, 3], vel: { y: [2.5, 3.5], space: "world" }, gravity: 2.4, life: [0.35, 0.5], size: [0.05, 0.09], alpha: fade },
  { name: "Drip", mat: "dotPoison", fudge: -3, pos: [0, 1.3, 0], rate: 12, shape: { type: "sphere", r: 0.35, th: 0.3 }, speed: [0, 0.1], gravity: 1,
    life: [0.35, 0.45], size: [0.05, 0.08], alpha: fade },
];

// ---------------------------------------------------------------- 10 모래 회오리 (바람+대지) area r4 pull + per pulled enemy 고정 1 s
const SANDSTORM = [
  decal("Swirl", "swirlSand", 8.6, 0.7, { fudge: 2, rotLife: [0, -8, 0], sizeLife: [[0, 1.05], [1, 0.35]], alpha: [[0, 0], [0.12, 0.9], [0.7, 0.7], [1, 0]] }),
  decal("Swirl2", "swirlSand", 6.4, 0.7, { fudge: 2, rotLife: [0, -11, 0], sizeLife: [[0, 1.1], [1, 0.25]], alpha: [[0, 0], [0.12, 0.6], [0.7, 0.5], [1, 0]] }),
  Object.assign(groundRing("Edge", "ringSand", 4, 0, 0.7, 0.9, "ringW"), { sizeLife: [[0, 1], [1, 0.25]], alpha: [[0, 0], [0.08, 0.95], [0.7, 0.6], [1, 0]] }),
  { name: "Grains", mat: "grain", fudge: -1, pos: [0, 0.3, 0], bursts: [[0, 40], [0.12, 24]], shape: { type: "circle", r: 4, th: 0.3 },
    speed: [0, 0.1], vel: { y: [0.2, 0.7], space: "world", orb: -5, rad: -6 }, life: [0.5, 0.65], size: [0.05, 0.1], alpha: [[0, 0], [0.12, 1], [0.8, 0.9], [1, 0]] },
  { name: "Streaks", mat: "streakSand", mode: "stretch", vs: 0.07, ls: 2.2, fudge: -2, pos: [0, 0.5, 0], bursts: [[0, 22]], shape: { type: "circle", r: 3.6, th: 0.4 },
    speed: [0, 0.1], vel: { y: [0.2, 0.6], space: "world", orb: -6, rad: -5 }, life: [0.4, 0.55], size: [0.06, 0.1], alpha: popIn },
  { name: "Dust", mat: "dust", fudge: 6, pos: [0, 0.4, 0], bursts: [[0, 10], [0.25, 6]], shape: { type: "circle", r: 3.2, th: 0.6 }, speed: [0.1, 0.3],
    vel: { y: [0.3, 0.7], space: "world", orb: -3, rad: -3 }, life: [0.45, 0.6], size: [1.0, 1.4], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.45], [1, 0]], flip: F44 },
  { name: "Center", mat: "dust", fudge: 6, pos: [0, 0.6, 0], bursts: [[0.4, 6]], shape: { type: "sphere", r: 0.4, th: 1 }, speed: [0.8, 1.6],
    vel: { y: [0.4, 0.8], space: "world" }, life: [0.35, 0.45], size: [0.9, 1.2], rot: [0, TAU], alpha: [[0, 0.5], [1, 0]], flip: F44 },
];
const PIN = [ // 고정 1 s on each pulled enemy (body)
  groundRing("Ring", "ringSand", 0.85, 0, 0.3, 0.9, "ringW"),
  decal("Seal", "sigilSand", 1.5, 1.0, { rotLife: [0, -3, 0], alpha: [[0, 0], [0.08, 0.9], [0.82, 0.75], [1, 0]] }),
  { name: "Band", mat: "sandBand", mode: "mesh", mesh: "chainBand", fudge: -1, pos: [0, 0.15, 0], bursts: [[0, 1]], life: 1.0, size: 0.6,
    rotLife: [0, -5, 0], sizeLife: [[0, 1.6, 0, -4], [0.15, 1], [1, 1]], alpha: hold(1.0) },
  { name: "Grains", mat: "grain", fudge: -1, pos: [0, 0.2, 0], rate: 30, shape: { type: "circle", r: 0.6, th: 0.3 }, speed: [0, 0.1],
    vel: { y: [0.1, 0.4], space: "world", orb: -7 }, life: [0.3, 0.4], size: [0.04, 0.07], alpha: popIn },
];
const PIN_LEN = 1.0;

// ---------------------------------------------------------------- 11 섬광 (바람+빛) — unchanged from v1 (owner has not decided its new effect)
const FLASH = [
  { name: "Glow", mat: "glowWhite", pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.15, size: 1.8, sizeLife: [[0, 0.5], [0.2, 1], [1, 1.1]], alpha: [[0, 0.9], [1, 0]] },
  { name: "Star", mat: "starWhite", fudge: -4, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.12, size: 2.2, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  { name: "Ring", mat: "ringWhite", fudge: 1, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.15, size: 2.0, sizeLife: [[0, 0.2, 0, 2.5], [1, 1]], alpha: [[0, 0.8], [1, 0]] },
];

// ---------------------------------------------------------------- 12 공허 균열 (바람+어둠) body: fear 1 s
const VOID_GUST = [
  { name: "Rift", mat: "rift", pos: [0, 1.1, 0], bursts: [[0, 1]], life: 0.45, size: 2.0,
    sizeLife: [[0, 0.15], [0.15, 1], [0.7, 1], [1, 0.1]], color: [[0, [0.85, 0.7, 1]], [1, [0.55, 0.3, 1]]], alpha: [[0, 0], [0.1, 1], [0.7, 0.85], [1, 0]] },
  flash("Glow", "glowVoid", 1.1, 1.8, 0.3, 0.6),
  groundRing("Ring", "ringVoid", 1.6, 0, 0.4, 0.9, "ringW"),
  decal("Dark", "mistVoid", 2.2, 1.0, { fudge: 8, alpha: [[0, 0], [0.1, 0.55], [0.8, 0.45], [1, 0]], flip: F44 }),
  { name: "Blast", mat: "wispVoid", mode: "stretch", vs: 0.08, ls: 2.6, fudge: -2, pos: [0, 1.0, 0], bursts: [[0, 14]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [5, 8], drag: 6, life: [0.3, 0.42], size: [0.25, 0.35], alpha: fade },
  { name: "Wisps", mat: "wispVoid", mode: "stretch", vs: 0.12, ls: 2.4, fudge: -2, pos: [0, 1.7, 0], bursts: [[0.05, 3]], rate: 8, shape: { type: "circle", r: 0.45, th: 0.2 },
    speed: [0, 0.1], vel: { y: [0.1, 0.4], orb: 7 }, life: [0.35, 0.5], size: [0.16, 0.24], alpha: popIn },
  { name: "Motes", mat: "dotVoid", fudge: -3, pos: [0, 1.0, 0], rate: 16, shape: { type: "sphere", r: 0.7, th: 0.3 }, speed: [0, 0.1],
    vel: { rad: 1.2, orb: 3 }, life: [0.35, 0.45], size: [0.04, 0.06], alpha: [[0, 0], [0.3, 1], [1, 0]] },
];
const VOID_LEN = 1.0;

// ---------------------------------------------------------------- 13 수정화 (대지+빛) body: stun 1 s
const CRYSTAL = [
  groundRing("Ring", "ringIce", 1.3, 0, 0.35, 0.95, "ringW"),
  decal("Frost", "glowIceDecal", 1.8, 1.0, { alpha: [[0, 0], [0.06, 0.6], [0.85, 0.45], [1, 0]] }),
  { name: "Spikes", mat: "shard", mode: "mesh", mesh: "spikeRing", fudge: -1, pos: [0, 0, 0], bursts: [[0, 1]], life: 1.0, size: 1.1,
    rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.05, 0, 9], [0.1, 1.05], [0.15, 1], [0.86, 1], [1, 0.2]], color: [[0, [0.95, 1, 1]], [1, [0.6, 0.85, 1]]],
    alpha: [[0, 1], [0.86, 1], [1, 0]] },
  { name: "SpikesIn", mat: "shard", mode: "mesh", mesh: "spikeRing", fudge: -1, pos: [0, 0, 0], bursts: [[0.03, 1]], life: 0.97, size: 0.75,
    rot3: [0, 25 * D2R, 0], sizeLife: [[0, 0.05, 0, 9], [0.1, 1.05], [0.15, 1], [0.86, 1], [1, 0.2]], color: [[0, [1, 1, 0.9]], [1, [0.85, 0.9, 1]]],
    alpha: [[0, 1], [0.86, 1], [1, 0]] },
  { name: "Glints", mat: "starIce", fudge: -4, pos: [0, 0.8, 0], bursts: [[0.05, 3], [0.3, 2], [0.6, 2]], shape: { type: "sphere", r: 0.5, th: 0.4 },
    life: 0.18, size: [0.35, 0.5], sizeLife: [[0, 0.2], [0.35, 1], [1, 0]] },
  { name: "Stars", mat: "starGold", fudge: -4, pos: [0, 2.15, 0], bursts: [[0.05, 3]], shape: { type: "circle", r: 0.35, th: 0 }, speed: [0, 0],
    vel: { orb: 6 }, life: 0.95, size: [0.5, 0.55], alpha: hold(0.95) },
  { name: "Shatter", mat: "shard", fudge: -2, pos: [0, 0.6, 0], bursts: [[0.88, 12]], shape: { type: "sphere", r: 0.4, th: 0.5 }, speed: [2.5, 4.5],
    drag: 2, gravity: 1.4, life: [0.25, 0.35], size: [0.1, 0.18], rot: [0, TAU], rotZ: [-8, 8], alpha: fade },
  flash("Pop", "glowIce", 0.8, 1.6, 0.2, 0.7),
];
const CRYSTAL_LEN = 1.0;

// ---------------------------------------------------------------- 14 매장 (대지+어둠) body: root 1 s
const BIND = [
  decal("Seam", "crackDark", 2.2, 1.0, { alpha: [[0, 0], [0.06, 0.9], [0.85, 0.7], [1, 0]] }),
  decal("Dust", "dustDark", 2.0, 0.5, { fudge: 7, alpha: [[0, 0.7], [1, 0]], flip: F44 }),
  groundRing("Ring", "ringVoid", 1.3, 0, 0.35, 0.9, "ringW"),
  { name: "Spikes", mat: "shardDark", mode: "mesh", mesh: "spikeRing", fudge: 2, pos: [0, 0, 0], bursts: [[0, 1]], life: 1.0, size: 1.0,
    rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.05, 0, 8], [0.12, 1.05], [0.17, 1], [0.85, 1], [1, 0.1]], alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "Rims", mat: "shardRim", mode: "mesh", mesh: "spikeRing", fudge: -1, pos: [0, 0, 0], bursts: [[0, 1]], life: 1.0, size: 1.03,
    rot3: [0, 0, 0], sizeLife: [[0, 0.05, 0, 8], [0.12, 1.05], [0.17, 1], [0.85, 1], [1, 0.1]], alpha: [[0, 0.9], [0.85, 0.7], [1, 0]] },
  { name: "Chain", mat: "chainVoid", mode: "mesh", mesh: "chainBand", fudge: -2, pos: [0, 0.2, 0], bursts: [[0.06, 1]], life: 0.94, size: 0.5,
    rotLife: [0, 4, 0], sizeLife: [[0, 1.8, 0, -4], [0.18, 1], [1, 1]], alpha: hold(0.94) },
  { name: "Clods", mat: "chunkDark", fudge: 2, pos: [0, 0.1, 0], bursts: [[0, 12], [0.88, 8]], shape: { type: "circle", r: 0.4, th: 1 },
    speed: [0.8, 1.6], vel: { y: [2, 3], space: "world" }, gravity: 2.4, life: [0.35, 0.45], size: [0.12, 0.22], rot: [0, TAU], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "Motes", mat: "dotVoid", fudge: -3, pos: [0, 0.1, 0], rate: 14, shape: { type: "circle", r: 0.5, th: 0.6 }, speed: [0, 0.1],
    vel: { y: [0.6, 1.0], space: "world" }, life: [0.4, 0.5], size: [0.04, 0.06], alpha: popIn },
];
const BIND_LEN = 1.0;

// ---------------------------------------------------------------- 15 일식 (빛+어둠) body: sleep 1 s
const ECLIPSE = [
  { name: "Disc", mat: "disc", fudge: 2, pos: [0, 2.2, 0], bursts: [[0, 1]], life: 1.0, size: 0.5, sizeLife: [[0, 0], [0.12, 1.1], [0.2, 1], [0.85, 1], [1, 0]] },
  { name: "Corona", mat: "ringEclipse", fudge: -2, pos: [0, 2.2, 0], bursts: [[0, 1]], life: 1.0, size: 0.72, rot: [0, TAU], rotZ: [2, 2],
    sizeLife: [[0, 0], [0.12, 1.1], [0.2, 1], [0.85, 1.05], [1, 0]], color: [[0, [1, 0.85, 0.55]], [1, [0.8, 0.5, 1]]] },
  { name: "Halo", mat: "glowEclipse", fudge: 4, pos: [0, 2.2, 0], bursts: [[0, 1]], life: 1.0, size: 1.3, sizeLife: [[0, 0], [0.15, 1], [1, 0.7]], alpha: hold(1.0, 0.25) },
  flash("Pop", "glowEclipse", 1.0, 2.0, 0.3, 0.6),
  groundRing("Ring", "ringEclipse", 1.5, 0, 0.45, 0.9, "rings2"),
  decal("Calm", "glowEclipseDecal", 2.0, 1.0, { alpha: [[0, 0], [0.08, 0.45], [0.8, 0.35], [1, 0]] }),
  icon("Moon", "moon", 2.55, 0.42, 0.95, 0.06, { pos: [-0.42, 2.55, 0], rot: [-0.3, -0.3] }),
  { name: "Zs", mat: "zSleep", fudge: -4, pos: [0.25, 2.0, 0], bursts: [[0.1, 1], [0.38, 1], [0.66, 1]], speed: [0, 0],
    vel: { x: [0.35, 0.45], y: [0.6, 0.7], space: "world" }, life: 0.55, size: [0.22, 0.3], sizeLife: [[0, 0.5], [0.3, 1], [1, 1.2]], alpha: popIn },
  { name: "Motes", mat: "dotEclipse", fudge: -3, pos: [0, 2.6, 0], rate: 14, shape: { type: "circle", r: 0.6, th: 0.5 }, speed: [0, 0.1],
    vel: { y: [-0.9, -0.5], space: "world" }, life: [0.6, 0.8], size: [0.04, 0.07], alpha: popIn },
];
const ECLIPSE_LEN = 1.0;

// ---------------------------------------------------------------- materials (HDR tint; textures are grey-white)
const MATS = {
  glowWarm: { tex: "T_Glow", tint: [2.4, 1.7, 1.1], add: true },
  starWarm: { tex: "T_Star", tint: [2.6, 2.2, 1.6], add: true },
  ringPale: { tex: "T_Line", tint: [1.8, 2.2, 2.6], add: true },
  ringSteam: { tex: "T_Line", tint: [2.4, 1.6, 1.0], add: true },
  crackSteam: { tex: "T_Crack", tint: [1.6, 1.4, 1.2], add: true },
  steamAdd: { tex: "T_Puff_Sheet", tint: [0.75, 0.8, 0.88], add: true },
  streakWater: { tex: "T_Streak", tint: [1.3, 2.0, 2.8], add: true },
  streakFire: { tex: "T_Streak", tint: [2.8, 1.6, 0.6], add: true },
  glowFire: { tex: "T_Glow", tint: [2.4, 1.0, 0.3], add: true },
  ringFire: { tex: "T_Line", tint: [2.8, 1.5, 0.5], add: true },
  crackLava: { tex: "T_Crack", tint: [2.6, 1.4, 0.6], add: true },
  flameTongue: { tex: "T_FlameTongue_Sheet", tint: [2.4, 1.2, 0.4], add: true },
  dotFire: { tex: "T_Dot", tint: [2.6, 1.6, 0.8], add: true },
  streakLava: { tex: "T_Streak", tint: [2.8, 1.3, 0.4], add: true },
  chainFire: { tex: "T_Chain", tint: [2.6, 1.4, 0.5], add: true },
  sealFire: { tex: "T_Seal", tint: [2.8, 1.5, 0.6], add: true },
  mistDark: { tex: "T_Puff_Sheet", tint: [0.2, 0.09, 0.09], add: false },
  starSun: { tex: "T_Star", tint: [2.8, 2.4, 1.3], add: true },
  glowSun: { tex: "T_Glow", tint: [2.4, 1.8, 0.6], add: true },
  ringSun: { tex: "T_Line", tint: [2.8, 2.2, 1.0], add: true },
  ringSunB: { tex: "T_Ring", tint: [2.8, 2.2, 1.0], add: true },
  streakSun: { tex: "T_Streak", tint: [2.8, 2.3, 1.1], add: true },
  downSun: { tex: "T_Down", tint: [2.6, 2.0, 0.9], add: true },
  sigilEmber: { tex: "T_Sigil", tint: [2.6, 1.2, 0.5], add: true },
  ringEmber: { tex: "T_Line", tint: [2.4, 1.0, 0.8], add: true },
  shardMana: { tex: "T_Shard", tint: [2.0, 2.2, 2.6], add: true },
  manaCrack: { tex: "T_ManaCrack", tint: [2.2, 2.2, 2.4], add: true },
  bolt: { tex: "T_Bolt_Sheet", tint: [1.8, 2.3, 3.2], add: true },
  boltDim: { tex: "T_Bolt_Sheet", tint: [1.0, 1.5, 2.6], add: true },
  glowBolt: { tex: "T_Glow", tint: [1.3, 1.9, 3.0], add: true },
  starBolt: { tex: "T_Star", tint: [2.0, 2.5, 3.0], add: true },
  streakBolt: { tex: "T_Streak", tint: [1.8, 2.3, 3.0], add: true },
  ringBolt: { tex: "T_Line", tint: [1.6, 2.2, 3.0], add: true },
  glowBoltDecal: { tex: "T_Glow", tint: [0.9, 1.4, 2.4], add: true },
  arcBolt: { tex: "T_Arc_Sheet", tint: [1.8, 2.4, 3.2], add: true },
  mud: { tex: "T_Puff_Sheet", tint: [0.42, 0.3, 0.2], add: false },
  mudBand: { tex: "T_Puff_Sheet", tint: [0.9, 0.6, 0.3], add: true },
  ringMud: { tex: "T_Line", tint: [2.4, 1.6, 0.8], add: true },
  swirlMud: { tex: "T_Swirl", tint: [1.4, 0.95, 0.5], add: true },
  downMud: { tex: "T_Down", tint: [2.2, 1.5, 0.8], add: true },
  ringWater: { tex: "T_Line", tint: [1.4, 2.0, 2.8], add: true },
  chunkMud: { tex: "T_Chunk_Sheet", tint: [0.55, 0.4, 0.27], add: false },
  dotMud: { tex: "T_Dot", tint: [0.45, 0.33, 0.22], add: false },
  ringSoft: { tex: "T_Line", tint: [2.2, 2.2, 2.2], add: true },
  glowSoftDecal: { tex: "T_Glow", tint: [1.2, 1.2, 1.2], add: true },
  streakSoft: { tex: "T_Streak", tint: [2.2, 2.2, 2.2], add: true },
  glowSoft: { tex: "T_Glow", tint: [1.8, 1.8, 1.8], add: true },
  dotSoft: { tex: "T_Dot", tint: [2.0, 2.0, 2.0], add: true },
  crossHeal: { tex: "T_Cross", tint: [1.4, 2.6, 1.5], add: true },
  ringHeal: { tex: "T_Line", tint: [1.4, 2.6, 1.6], add: true },
  dotHeal: { tex: "T_Dot", tint: [1.6, 2.6, 1.6], add: true },
  glowHeal: { tex: "T_Glow", tint: [1.0, 2.0, 1.1], add: true },
  swirlPoison: { tex: "T_Swirl", tint: [1.6, 2.2, 1.6], add: true },
  mistAbyss: { tex: "T_Puff_Sheet", tint: [0.1, 0.06, 0.18], add: false },
  ringPoison: { tex: "T_Line", tint: [1.5, 2.6, 1.0], add: true },
  bubblePoison: { tex: "T_Bubble", tint: [1.6, 2.6, 1.2], add: true },
  steamPoison: { tex: "T_Puff_Sheet", tint: [0.35, 0.6, 0.25], add: true },
  dotPoison: { tex: "T_Dot", tint: [1.6, 2.6, 1.1], add: true },
  swirlSand: { tex: "T_Swirl", tint: [1.7, 1.3, 0.75], add: true },
  ringSand: { tex: "T_Line", tint: [2.6, 1.9, 1.0], add: true },
  grain: { tex: "T_Dot", tint: [1.0, 0.82, 0.52], add: false },
  streakSand: { tex: "T_Streak", tint: [2.2, 1.7, 1.0], add: true },
  dust: { tex: "T_Puff_Sheet", tint: [0.85, 0.72, 0.5], add: false },
  sigilSand: { tex: "T_Sigil", tint: [2.4, 1.8, 0.9], add: true },
  sandBand: { tex: "T_Chain", tint: [2.4, 1.8, 0.9], add: true },
  glowWhite: { tex: "T_Glow", tint: [2.4, 2.4, 2.3], add: true },
  starWhite: { tex: "T_Star", tint: [2.8, 2.8, 2.6], add: true },
  ringWhite: { tex: "T_Ring", tint: [2.2, 2.2, 2.1], add: true },
  rift: { tex: "T_Rift", tint: [2.0, 1.5, 2.8], add: true },
  glowVoid: { tex: "T_Glow", tint: [1.3, 0.6, 2.2], add: true },
  ringVoid: { tex: "T_Line", tint: [1.8, 1.0, 2.8], add: true },
  mistVoid: { tex: "T_Puff_Sheet", tint: [0.14, 0.05, 0.22], add: false },
  wispVoid: { tex: "T_Wisp", tint: [1.6, 0.9, 2.6], add: true },
  dotVoid: { tex: "T_Dot", tint: [1.8, 1.1, 2.8], add: true },
  ringIce: { tex: "T_Line", tint: [1.9, 2.5, 2.8], add: true },
  glowIceDecal: { tex: "T_Glow", tint: [1.0, 1.6, 2.0], add: true },
  shard: { tex: "T_Shard", tint: [1.7, 2.1, 2.4], add: true },
  starIce: { tex: "T_Star", tint: [2.2, 2.6, 2.8], add: true },
  starGold: { tex: "T_Star", tint: [2.8, 2.4, 1.2], add: true },
  glowIce: { tex: "T_Glow", tint: [1.2, 1.8, 2.2], add: true },
  crackDark: { tex: "T_Crack", tint: [1.4, 0.7, 2.0], add: true },
  dustDark: { tex: "T_Puff_Sheet", tint: [0.25, 0.2, 0.2], add: false },
  shardDark: { tex: "T_Shard", tint: [0.16, 0.1, 0.2], add: false },
  shardRim: { tex: "T_Shard", tint: [1.2, 0.6, 2.0], add: true },
  chainVoid: { tex: "T_Chain", tint: [1.8, 1.0, 2.8], add: true },
  chunkDark: { tex: "T_Chunk_Sheet", tint: [0.32, 0.25, 0.22], add: false },
  disc: { tex: "T_Disc", tint: [1, 1, 1], add: false },
  ringEclipse: { tex: "T_Ring", tint: [2.4, 2.0, 2.4], add: true },
  glowEclipse: { tex: "T_Glow", tint: [1.4, 0.8, 1.9], add: true },
  glowEclipseDecal: { tex: "T_Glow", tint: [0.9, 0.6, 1.5], add: true },
  moon: { tex: "T_Moon", tint: [2.6, 2.2, 1.6], add: true },
  zSleep: { tex: "T_Z", tint: [2.2, 1.9, 2.8], add: true },
  dotEclipse: { tex: "T_Dot", tint: [2.2, 1.7, 2.6], add: true },
};

// id, label, layers, duration (s), kind (body | area | bolt)
const SYS = [
  ["steam", "증기 폭발 (범위 3)", STEAM, 0.8, "area"],
  ["wildfire", "불씨 확산 (범위 2)", WILDFIRE, 0.8, "area"],
  ["wildfire_burn", "불씨 확산 — 화상 대상", BURN, 0.5, "body"],
  ["magma", "용암 분출 — 회복봉인", MAGMA, 0.9, "body"],
  ["flare", "태양 플레어 — 발광+약화", FLARE, 0.8, "body"],
  ["ember_curse", "잿불 낙인 — 마나번", EMBER_CURSE, 0.9, "body"],
  ["shock", "연쇄 번개", SHOCK, 0.26, "bolt"],
  ["shock_disarm", "연쇄 번개 — 무장해제 1초", DISARM, DISARM_LEN, "body"],
  ["mud", "진흙 — 둔화", MUD, 0.9, "body"],
  ["rainbow", "무지개 (아군 범위 4)", RAINBOW, 1.0, "area"],
  ["rainbow_heal", "무지개 — 회복 대상", HEAL, 0.7, "body"],
  ["abyss", "심연 — 독", ABYSS, 0.9, "body"],
  ["sandstorm", "모래 회오리 (범위 4)", SANDSTORM, 0.7, "area"],
  ["sandstorm_pin", "모래 회오리 — 고정 1초", PIN, PIN_LEN, "body"],
  ["flash", "섬광 (기존 유지)", FLASH, 0.2, "body"],
  ["void_gust", "공허 균열 — 공포 1초", VOID_GUST, VOID_LEN, "body"],
  ["crystal", "수정화 — 기절 1초", CRYSTAL, CRYSTAL_LEN, "body"],
  ["bind", "매장 — 속박 1초", BIND, BIND_LEN, "body"],
  ["eclipse", "일식 — 수면 1초", ECLIPSE, ECLIPSE_LEN, "body"],
];

// ---------------------------------------------------------------- demo
const UP = [0, 1, 0], FWD = [0, 0, 1];
const scen = {};
const RING_DUMMIES = r => [[0, 0, 0, 0], [r * 0.75, 0, r * 0.45, -120], [-r * 0.7, 0, r * 0.5, 140], [r * 0.2, 0, -r * 0.8, 10]];
for (const [id, label, , , kind] of SYS) {
  if (kind === "bolt") continue;
  const area = kind === "area";
  const r = { steam: 3, wildfire: 2, rainbow: 4, sandstorm: 4 }[id] || 0;
  const followUp = { wildfire: "wildfire_burn", rainbow: "rainbow_heal", sandstorm: "sandstorm_pin" }[id];
  const hits = [{ t: 0.3, point: [0, 0, 0], normal: UP, incoming: FWD, system: id }];
  if (followUp) for (const [x, , z] of RING_DUMMIES(r).slice(1)) hits.push({ t: id === "sandstorm" ? 0.75 : 0.32, point: [x * (id === "sandstorm" ? 0.35 : 1), 0, z * (id === "sandstorm" ? 0.35 : 1)], normal: UP, incoming: FWD, system: followUp });
  scen[id] = { label, targets: area ? RING_DUMMIES(r) : [[0, 0, 0, 0]], hits,
    camera: { target: [0, 0.9, 0], dist: area ? 4 + r * 2 : 4.6, yaw: -35, pitch: area ? 34 : 14 } };
}
// chain lightning: three jumps, each struck target gets the disarm crackle
scen.shock = { label: "연쇄 번개 + 무장해제", targets: [[0, 0, 0, 0], [0, 0, 3, 180], [0, 0, 6, 0], [0, 0, 9, 180]],
  hits: [0, 1, 2].flatMap(k => [{ t: 0.3 + 0.15 * k, point: [0, 1.0, 3 * k], normal: UP, incoming: FWD, system: "shock" },
                                 { t: 0.3 + 0.15 * k, point: [0, 0, 3 * (k + 1)], normal: UP, incoming: FWD, system: "shock_disarm" }]),
  camera: { target: [0, 0.9, 4.5], dist: 9, yaw: -70, pitch: 16 } };
// every body reaction side by side
const BODY = SYS.filter(s => s[4] === "body" && !s[0].includes("_"));
scen.all = { label: "단일 대상 전체", targets: [], hits: [], camera: { target: [0, 0.8, 1.8], dist: 13, yaw: 0, pitch: 30 } };
BODY.forEach(([id], i) => {
  const x = (i % 5 - 2) * 2.6, z = Math.floor(i / 5) * 3.2;
  scen.all.targets.push([x, 0, z, 0]); scen.all.hits.push({ t: 0.3, point: [x, 0, z], normal: UP, incoming: FWD, system: id });
});

const SCENE = {
  name: "ElementReactions",
  bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.65, tint: [1, 0.95, 0.92] }, tonemapping: "neutral",
  systems: SYS.map(([id, , layers, len]) => ({ id, role: "impact", loop: false, duration: Math.max(0.2, len), layers })),
  demo: {
    interval: 2.0, maxImpacts: 24, dummy: { height: 1.8 }, bg: [0.33, 0.34, 0.36], floor: [0.42, 0.42, 0.43], grid: [0.47, 0.47, 0.48],
    scenarios: scen,
  },
};
