// ElementReactions v3 (2026-10-20, CC rework) — one impact system per reaction / placement.
// Look: painted per-reaction textures (gen_er2.py, colour baked: white core -> hue -> deep hue), few layers, short.
// The long part of a CC is shown by the status icons above the head; the VFX shows the moment it lands
// (hard CC 1 s: the VFX holds for that second).
//
// Placement (server ReactionVfx):
//   body systems   P = target feet, up = world up. Authored for a 1.8 m humanoid. Instance scale
//                  s = clamp((height/1.8 + width/0.6) / 2, 0.6, 4) — width/height from the ModelEngine hitbox when modelled.
//   area systems   P = centre feet. Authored at the gameplay radius (steam 3, wildfire 2, sandstorm 4, rainbow 4);
//                  scale = (radius + targetWidth/2) / (radius + 0.3).
//   shock          P = source chest, bolt along local +Z for BOLT_LEN; yaw toward the next target, scale = distance / BOLT_LEN.
const BOLT_LEN = 3;
const TAU = 2 * Math.PI;
const F44 = { x: 4, y: 4 }, F22 = { x: 2, y: 2 };

var EXTRA_MESHES = {
  bolt: {
    verts: [[-0.25, 0, 0], [0.25, 0, 0], [0.25, 0, BOLT_LEN], [-0.25, 0, BOLT_LEN],
            [0, -0.25, 0], [0, 0.25, 0], [0, 0.25, BOLT_LEN], [0, -0.25, BOLT_LEN]],
    uvs: [[0, 0], [0, 1], [1, 1], [1, 0], [0, 0], [0, 1], [1, 1], [1, 0]],
    tris: [0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7],
  },
};

// ---------------------------------------------------------------- helpers
const fade = [[0, 1], [0.6, 0.8], [1, 0]];
const popIn = [[0, 0], [0.12, 1], [0.7, 0.8], [1, 0]];
const hold = (life, out = 0.18) => [[0, 0], [Math.min(0.1, 0.08 / life), 1], [1 - out / life, 0.9], [1, 0]];
const decal = (name, mat, D, life, extra = {}) => Object.assign({ name, mat, mode: "mesh", mesh: "quad", fudge: 3, pos: [0, 0.04, 0], bursts: [[0, 1]],
  life, size: D, rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.45, 0, 3], [0.18, 1], [1, 1.04]], alpha: hold(life) }, extra);
const wave = (name, mat, D, t0, life, a = 0.9) => ({ name, mat, mode: "mesh", mesh: "quad", fudge: 1, pos: [0, 0.07, 0], bursts: [[t0, 1]], life, size: D,
  rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.15, 0, 2.6], [0.65, 0.97], [1, 1]], alpha: [[0, a], [0.5, a * 0.75], [1, 0]] });
const flash = (name, mat, y, size, life = 0.16, a = 0.85) => ({ name, mat, pos: [0, y, 0], bursts: [[0, 1]], life, size,
  sizeLife: [[0, 0.4], [0.25, 1], [1, 1.1]], alpha: [[0, a], [1, 0]] });
const sparks = (name, mat, y, n, sp, life, sz = [0.05, 0.08], extra = {}) => Object.assign({ name, mat, mode: "stretch", vs: 0.07, ls: 1.6, fudge: -3,
  pos: [0, y, 0], bursts: [[0, n]], shape: { type: "sphere", r: 0.2, th: 1 }, speed: sp, drag: 3.5, gravity: 0.5, life, size: sz, alpha: fade }, extra);
const icon = (name, mat, y, size, life, t0 = 0.04, extra = {}) => Object.assign({ name, mat, fudge: -4, pos: [0, y, 0], bursts: [[t0, 1]], life, size,
  sizeLife: [[0, 1.5], [0.14, 0.95], [0.3, 1], [1, 1]], alpha: hold(life, 0.2) }, extra);

// ---------------------------------------------------------------- 1 증기 폭발 (화+물) area r3: small damage + lift. 0.7 s
const STEAM = [
  flash("Flash", "glowWarm", 0.8, 1.5, 0.16),
  wave("Wave", "waveSteam", 6.4, 0, 0.42),
  { name: "Billow", mat: "steam", fudge: 4, pos: [0, 0.35, 0], bursts: [[0, 14]], shape: { type: "circle", r: 0.4, th: 1 },
    speed: [6, 8], drag: 5.5, life: [0.5, 0.7], size: [1.0, 1.5], rot: [0, TAU], vel: { y: [0.5, 1.0], space: "world" },
    sizeLife: [[0, 0.5], [1, 1.4]], alpha: [[0, 0], [0.08, 0.9], [0.5, 0.5], [1, 0]], flip: F44 },
  { name: "Column", mat: "steam", fudge: 5, pos: [0, 0.3, 0], bursts: [[0, 6]], shape: { type: "circle", r: 0.3, th: 1 },
    speed: [0.2, 0.5], vel: { y: [3.5, 5], space: "world" }, drag: 2.5, life: [0.45, 0.6], size: [0.9, 1.2], rot: [0, TAU],
    sizeLife: [[0, 0.5], [1, 1.5]], alpha: [[0, 0.9], [0.5, 0.5], [1, 0]], flip: F44 },
  sparks("Drops", "streakWater", 0.6, 14, [5, 8], [0.28, 0.4], [0.05, 0.08], { gravity: 1.6, drag: 2 }),
];

// ---------------------------------------------------------------- 2 불씨 확산 (화+바람) area r2: burn 3 s. 0.7 s
const WILDFIRE = [
  decal("Char", "char", 4.4, 0.7, { fudge: 7, alpha: [[0, 0], [0.1, 0.55], [0.6, 0.4], [1, 0]] }),
  decal("FireRing", "fireRing", 4.6, 0.65, { fudge: 2, rotLife: [0, 1.2, 0], sizeLife: [[0, 0.3, 0, 3], [0.22, 1], [1, 1.06]], alpha: [[0, 0], [0.08, 1], [0.55, 0.85], [1, 0]] }),
  { name: "Flames", mat: "flame", fudge: 0, pos: [0, 0.35, 0], bursts: [[0.02, 16]], shape: { type: "circle", r: 2, th: 0.15 },
    speed: [0, 0.1], vel: { y: [1.2, 1.8], space: "world", orb: 2.5 }, life: [0.4, 0.55], size: [0.75, 1.05], alpha: fade, flip: F44 },
  { name: "Gust", mat: "streakFire", mode: "stretch", vs: 0.06, ls: 1.8, fudge: -3, pos: [0, 0.5, 0], bursts: [[0, 18]], shape: { type: "circle", r: 0.5, th: 1 },
    speed: [3, 5], vel: { y: [0.4, 1.0], space: "world", orb: 6 }, drag: 2, life: [0.35, 0.5], size: [0.05, 0.08], alpha: fade },
];
const BURN = [
  { name: "Flames", mat: "flame", fudge: 0, pos: [0, 0.3, 0], bursts: [[0, 6]], shape: { type: "circle", r: 0.3, th: 1 },
    speed: [0, 0.2], vel: { y: [1.4, 2.2], space: "world" }, life: [0.35, 0.5], size: [0.6, 0.85], alpha: fade, flip: F44 },
  sparks("Embers", "streakFire", 0.9, 8, [2, 4], [0.25, 0.4], [0.04, 0.06], { gravity: -0.4 }),
];

// ---------------------------------------------------------------- 3 용암 분출 (화+대지): heal seal 3 s. 0.85 s
const MAGMA = [
  decal("Char", "char", 2.6, 0.85, { fudge: 7, alpha: [[0, 0], [0.08, 0.6], [0.7, 0.45], [1, 0]] }),
  decal("Lava", "lava", 2.6, 0.85, { fudge: 2, alpha: [[0, 0], [0.06, 1], [0.6, 0.8], [1, 0]] }),
  { name: "Spout", mat: "flame", fudge: 0, pos: [0, 0.15, 0], bursts: [[0, 5]], shape: { type: "circle", r: 0.25, th: 1 },
    speed: [0, 0.2], vel: { y: [2.6, 3.4], space: "world" }, drag: 2, life: [0.35, 0.45], size: [0.7, 1.0], alpha: fade, flip: F44 },
  { name: "Blobs", mat: "dotLava", fudge: -3, pos: [0, 0.1, 0], bursts: [[0.04, 9]], shape: { type: "circle", r: 0.35, th: 1 },
    speed: [0.6, 1.4], vel: { y: [3, 4.5], space: "world" }, gravity: 1.8, life: [0.45, 0.6], size: [0.09, 0.14],
    color: [[0, [1, 0.9, 0.55]], [1, [1, 0.3, 0.08]]], alpha: fade },
  icon("Seal", "sealFire", 2.25, 0.7, 0.8, 0.1),
];

// ---------------------------------------------------------------- 4 태양 플레어 (화+빛): glow 3 s + weaken 3 s. 0.7 s
const FLARE = [
  { name: "Sun", mat: "sun", fudge: -2, pos: [0, 1.05, 0], bursts: [[0, 1]], life: 0.4, size: 2.8, rot: [0, TAU], rotZ: [0.8, 0.8],
    sizeLife: [[0, 0.25, 0, 3], [0.2, 1.05], [1, 0.8]], alpha: [[0, 1], [0.5, 0.75], [1, 0]] },
  flash("Glow", "glowSun", 1.05, 1.3, 0.2, 0.7),
  wave("Wave", "waveSun", 3.2, 0, 0.4),
  { name: "Drain", mat: "streakSun", mode: "stretch", vs: 0.05, ls: 2, fudge: -2, pos: [0, 2.2, 0], bursts: [[0.12, 10]], shape: { type: "circle", r: 0.5, th: 0.4 },
    speed: [0, 0.1], vel: { y: [-3.2, -2.4], space: "world" }, life: [0.32, 0.42], size: [0.05, 0.08], alpha: popIn },
  icon("Weak", "downSun", 2.25, 0.55, 0.6, 0.15),
];

// ---------------------------------------------------------------- 5 잿불 낙인 (화+어둠): mana burn. 0.85 s
const EMBER_CURSE = [
  decal("Sigil", "emberSigil", 2.0, 0.85, { fudge: 2, rotLife: [0, 2.2, 0], alpha: [[0, 0], [0.08, 1], [0.7, 0.85], [1, 0]] }),
  { name: "Mist", mat: "mistDark", fudge: 7, pos: [0, 0.8, 0], bursts: [[0, 4]], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [0.2, 0.5],
    life: [0.6, 0.8], size: [0.9, 1.2], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.45], [1, 0]], flip: F44 },
  { name: "ManaOut", mat: "manaShard", fudge: -2, pos: [0, 1.0, 0], bursts: [[0.03, 9]], shape: { type: "sphere", r: 0.35, th: 0.5 },
    speed: [1.5, 2.4], vel: { y: [1.6, 2.4], space: "world", orb: 4 }, drag: 2, life: [0.5, 0.65], size: [0.3, 0.42], rot: [-0.5, 0.5],
    color: [[0, [1, 1, 1]], [0.55, [1, 1, 1]], [0.75, [1, 0.6, 0.35]], [1, [0.9, 0.3, 0.15]]], alpha: [[0, 0], [0.1, 1], [0.7, 0.9], [1, 0]] },
  { name: "Burn", mat: "flame", fudge: 0, pos: [0, 1.7, 0], bursts: [[0.32, 5]], shape: { type: "sphere", r: 0.45, th: 0.5 },
    speed: [0.2, 0.5], vel: { y: [0.8, 1.2], space: "world" }, life: [0.3, 0.4], size: [0.35, 0.5], alpha: fade, flip: F44 },
  icon("Mana", "manaCrack", 2.25, 0.55, 0.7, 0.05, { color: [[0, [0.6, 0.85, 1]], [0.45, [0.7, 0.75, 1]], [0.65, [1, 0.6, 0.3]], [1, [1, 0.35, 0.15]]] }),
];

// ---------------------------------------------------------------- 6 연쇄 번개 (물+바람): bolt per jump + disarm 1 s on each struck target
const SHOCK = [
  { name: "Bolt", mat: "bolt", mode: "mesh", mesh: "bolt", bursts: [[0, 1]], life: 0.24, size: 1, flip: { x: 1, y: 4, cycles: 3 }, alpha: [[0, 1], [0.6, 1], [1, 0]] },
  { name: "SrcGlow", mat: "glowBolt", pos: [0, 0, 0], bursts: [[0, 1]], life: 0.16, size: 1.0, alpha: [[0, 0.9], [1, 0]] },
  { name: "HitStar", mat: "starBolt", fudge: -4, pos: [0, 0, BOLT_LEN], bursts: [[0, 1]], life: 0.18, size: 1.8, rot: [0, TAU], sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  sparks("HitSparks", "streakBolt", 0, 12, [4, 8], [0.15, 0.26], [0.04, 0.06], { pos: [0, 0, BOLT_LEN], gravity: 0 }),
];
const DISARM = [
  decal("Glow", "glowBoltDecal", 1.5, 1.0, { alpha: [[0, 0], [0.05, 0.6], [0.8, 0.4], [1, 0]] }),
  { name: "Arcs", mat: "arc", fudge: -3, pos: [0, 1.0, 0], rate: 14, bursts: [[0, 3]], shape: { type: "sphere", r: 0.42, th: 0.4 },
    life: [0.08, 0.13], size: [0.6, 0.9], rot: [0, TAU], alpha: [[0, 1], [1, 0.4]], flip: F22 },
  { name: "ArcsLow", mat: "arc", fudge: -3, pos: [0, 0.35, 0], rate: 7, shape: { type: "circle", r: 0.45, th: 0.3 },
    life: [0.08, 0.12], size: [0.45, 0.65], rot: [0, TAU], alpha: [[0, 1], [1, 0.3]], flip: F22 },
];

// ---------------------------------------------------------------- 7 진흙 (물+대지): slow 3 s. 0.85 s
const MUD = [
  decal("Splat", "mud", 2.4, 0.85, { fudge: 6, sizeLife: [[0, 0.3, 0, 4], [0.15, 1], [1, 1.03]], alpha: [[0, 1], [0.7, 0.9], [1, 0]] }),
  wave("Wave", "waveMud", 2.8, 0, 0.4, 0.75),
  { name: "Clods", mat: "chunkMud", fudge: 2, pos: [0, 0.15, 0], bursts: [[0, 10]], shape: { type: "circle", r: 0.35, th: 1 },
    speed: [1.4, 2.4], vel: { y: [2.2, 3.2], space: "world" }, gravity: 2.4, life: [0.4, 0.5], size: [0.14, 0.22], rot: [0, TAU], alpha: [[0, 1], [0.75, 1], [1, 0]] },
  sparks("Water", "streakWater", 0.3, 10, [2.5, 4], [0.25, 0.35], [0.04, 0.06], { vel: { y: [2, 3], space: "world" }, gravity: 2 }),
  icon("Slow", "downMud", 2.25, 0.55, 0.7, 0.12),
];

// ---------------------------------------------------------------- 8 무지개 (물+빛) area r4 at the caster: heal allies. 0.9 s
const RAINBOW = [
  decal("Prism", "prism", 8.6, 0.9, { fudge: 2, rotLife: [0, 0.8, 0], sizeLife: [[0, 0.15, 0, 2.6], [0.4, 1], [1, 1.02]], alpha: [[0, 0], [0.08, 1], [0.6, 0.7], [1, 0]] }),
  flash("Glow", "glowSoft", 1.0, 1.4, 0.3, 0.6),
  { name: "Pillar", mat: "streakSoft", mode: "stretch", vs: 0.05, ls: 3, fudge: -2, pos: [0, 0.2, 0], bursts: [[0, 5]], shape: { type: "circle", r: 0.35, th: 1 },
    speed: [0, 0.1], vel: { y: [5, 6.5], space: "world" }, drag: 3, life: [0.4, 0.5], size: [0.08, 0.12],
    color: [[0, [1, 0.7, 0.7]], [0.5, [0.7, 1, 0.8]], [1, [0.75, 0.75, 1]]], alpha: popIn },
  { name: "Crosses", mat: "cross", fudge: -3, pos: [0, 0.5, 0], bursts: [[0.06, 7]], shape: { type: "circle", r: 3.2, th: 0.9 },
    speed: [0, 0.1], vel: { y: [1.0, 1.5], space: "world" }, life: [0.6, 0.75], size: [0.38, 0.5], alpha: [[0, 0], [0.15, 1], [0.7, 0.8], [1, 0]] },
];
const HEAL = [
  { name: "Crosses", mat: "cross", fudge: -3, pos: [0, 0.6, 0], bursts: [[0, 3]], shape: { type: "circle", r: 0.45, th: 0.6 },
    speed: [0, 0.1], vel: { y: [1.2, 1.7], space: "world" }, life: [0.5, 0.65], size: [0.32, 0.42], alpha: [[0, 0], [0.15, 1], [0.7, 0.8], [1, 0]] },
  flash("Glow", "glowHeal", 1.0, 1.1, 0.3, 0.55),
  sparks("Motes", "streakHeal", 0.3, 8, [0.2, 0.6], [0.4, 0.55], [0.04, 0.06], { vel: { y: [1.4, 2], space: "world" }, gravity: 0, drag: 1 }),
];

// ---------------------------------------------------------------- 9 심연 (물+어둠): poison 3 s. 0.85 s
const ABYSS = [
  decal("Pool", "poison", 2.2, 0.85, { fudge: 3, rotLife: [0, -1.5, 0], alpha: [[0, 0], [0.08, 0.75], [0.7, 0.6], [1, 0]] }),
  { name: "Bubbles", mat: "bubble", fudge: -2, pos: [0, 0.15, 0], bursts: [[0, 5]], rate: 14, shape: { type: "circle", r: 0.5, th: 0.8 },
    speed: [0, 0.2], vel: { y: [1.0, 1.8], space: "world" }, life: [0.35, 0.5], size: [0.12, 0.22],
    sizeLife: [[0, 0.4], [0.8, 1], [0.9, 1.4], [1, 1.6]], alpha: [[0, 0], [0.15, 1], [0.85, 0.9], [1, 0]] },
  { name: "Fume", mat: "steamPoison", fudge: 4, pos: [0, 0.4, 0], bursts: [[0, 5]], shape: { type: "circle", r: 0.4, th: 1 }, speed: [0.3, 0.5],
    vel: { y: [0.8, 1.3], space: "world" }, life: [0.55, 0.75], size: [0.8, 1.1], rot: [0, TAU], sizeLife: [[0, 0.6], [1, 1.4]],
    alpha: [[0, 0], [0.15, 0.55], [1, 0]], flip: F44 },
  { name: "Splash", mat: "dotPoison", fudge: -3, pos: [0, 0.2, 0], bursts: [[0, 10]], shape: { type: "circle", r: 0.3, th: 1 },
    speed: [1.5, 2.8], vel: { y: [2.5, 3.5], space: "world" }, gravity: 2.4, life: [0.35, 0.5], size: [0.06, 0.1], alpha: fade },
];

// ---------------------------------------------------------------- 10 모래 회오리 (바람+대지) area r4 pull + per pulled enemy 고정 1 s
const SANDSTORM = [
  decal("Vortex", "sandVortex", 8.8, 0.7, { fudge: 2, rotLife: [0, -7, 0], sizeLife: [[0, 1.05], [1, 0.35]], alpha: [[0, 0], [0.12, 1], [0.7, 0.75], [1, 0]] }),
  { name: "Grains", mat: "grain", fudge: -1, pos: [0, 0.3, 0], bursts: [[0, 30]], shape: { type: "circle", r: 4, th: 0.3 },
    speed: [0, 0.1], vel: { y: [0.2, 0.6], space: "world", orb: -5, rad: -6 }, life: [0.5, 0.65], size: [0.05, 0.09], alpha: [[0, 0], [0.12, 1], [0.8, 0.9], [1, 0]] },
  { name: "Dust", mat: "dust", fudge: 6, pos: [0, 0.4, 0], bursts: [[0, 9]], shape: { type: "circle", r: 3.2, th: 0.6 }, speed: [0.1, 0.3],
    vel: { y: [0.3, 0.6], space: "world", orb: -3, rad: -3 }, life: [0.45, 0.6], size: [1.1, 1.5], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: F44 },
];
const PIN = [
  decal("Vortex", "sandVortex", 1.7, 1.0, { fudge: 2, rotLife: [0, -6, 0], alpha: [[0, 0], [0.08, 0.95], [0.82, 0.8], [1, 0]] }),
  { name: "Grains", mat: "grain", fudge: -1, pos: [0, 0.25, 0], rate: 24, shape: { type: "circle", r: 0.55, th: 0.3 }, speed: [0, 0.1],
    vel: { y: [0.1, 0.4], space: "world", orb: -7 }, life: [0.3, 0.4], size: [0.05, 0.08], alpha: popIn },
  { name: "Dust", mat: "dust", fudge: 6, pos: [0, 0.2, 0], bursts: [[0, 3]], shape: { type: "circle", r: 0.4, th: 1 }, speed: [0.2, 0.4],
    life: [0.6, 0.8], size: [0.8, 1.0], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.4], [1, 0]], flip: F44 },
];

// ---------------------------------------------------------------- 11 섬광 (바람+빛) — owner has not decided its new effect; kept simple
const FLASH = [
  { name: "Star", mat: "starWhite", fudge: -4, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.16, size: 2.4, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  flash("Glow", "glowWhite", 1.0, 1.6, 0.16),
  wave("Wave", "waveWhite", 3, 0, 0.3, 0.8),
];

// ---------------------------------------------------------------- 12 공허 균열 (바람+어둠): fear 1 s
const VOID_GUST = [
  decal("Ink", "voidInk", 2.6, 1.0, { fudge: 8, sizeLife: [[0, 0.3, 0, 4], [0.15, 1], [1, 1.05]], alpha: [[0, 0], [0.06, 0.9], [0.8, 0.75], [1, 0]] }),
  decal("Rim", "voidRim", 2.62, 1.0, { fudge: 2, sizeLife: [[0, 0.3, 0, 4], [0.15, 1], [1, 1.05]], alpha: [[0, 0], [0.06, 1], [0.8, 0.7], [1, 0]] }),
  { name: "Blast", mat: "wisp", mode: "stretch", vs: 0.08, ls: 2.4, fudge: -2, pos: [0, 1.0, 0], bursts: [[0, 10]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [5, 7.5], drag: 6, life: [0.3, 0.4], size: [0.28, 0.38], alpha: fade },
  { name: "Wisps", mat: "wisp", mode: "stretch", vs: 0.12, ls: 2.2, fudge: -2, pos: [0, 1.75, 0], bursts: [[0.05, 2]], rate: 6, shape: { type: "circle", r: 0.42, th: 0.2 },
    speed: [0, 0.1], vel: { y: [0.1, 0.3], orb: 7 }, life: [0.35, 0.45], size: [0.2, 0.28], alpha: popIn },
  { name: "Mist", mat: "mistVoid", fudge: 7, pos: [0, 1.0, 0], bursts: [[0, 3]], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [0.3, 0.6],
    life: [0.7, 0.9], size: [1.0, 1.3], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.45], [1, 0]], flip: F44 },
];

// ---------------------------------------------------------------- 13 수정화 (대지+빛): stun 1 s
const CRYSTAL = [
  decal("Frost", "glowIceDecal", 1.9, 1.0, { alpha: [[0, 0], [0.06, 0.6], [0.85, 0.45], [1, 0]] }),
  wave("Wave", "waveIce", 2.6, 0, 0.35),
  { name: "Cluster", mat: "crystal", fudge: -1, pos: [0, 0.55, 0], bursts: [[0, 1]], life: 1.0, size: 1.5,
    sizeLife: [[0, 0.05, 0, 9], [0.1, 1.06], [0.15, 1], [0.86, 1], [1, 0.3]], alpha: [[0, 1], [0.86, 1], [1, 0]] },
  { name: "Side", mat: "crystal", fudge: -1, pos: [0, 0.3, 0], bursts: [[0.03, 3]], shape: { type: "circle", r: 0.45, th: 0 }, speed: [0, 0],
    life: 0.95, size: [0.7, 0.9], sizeLife: [[0, 0.05, 0, 9], [0.1, 1.06], [0.15, 1], [0.85, 1], [1, 0.3]], alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "Glints", mat: "starIce", fudge: -4, pos: [0, 0.9, 0], bursts: [[0.08, 2], [0.4, 2]], shape: { type: "sphere", r: 0.45, th: 0.4 },
    life: 0.2, size: [0.4, 0.55], sizeLife: [[0, 0.2], [0.35, 1], [1, 0]] },
  { name: "Stars", mat: "starGold", fudge: -4, pos: [0, 2.15, 0], bursts: [[0.05, 3]], shape: { type: "circle", r: 0.32, th: 0 }, speed: [0, 0],
    vel: { orb: 6 }, life: 0.95, size: [0.6, 0.65], alpha: hold(0.95) },
  { name: "Shatter", mat: "manaShardIce", fudge: -2, pos: [0, 0.6, 0], bursts: [[0.88, 10]], shape: { type: "sphere", r: 0.4, th: 0.5 }, speed: [2.5, 4],
    drag: 2, gravity: 1.4, life: [0.25, 0.35], size: [0.16, 0.24], rot: [0, TAU], rotZ: [-8, 8], alpha: fade },
];

// ---------------------------------------------------------------- 14 매장 (대지+어둠): root 1 s
const BIND = [
  decal("Ink", "voidInk", 2.0, 1.0, { fudge: 8, alpha: [[0, 0], [0.06, 0.7], [0.85, 0.55], [1, 0]] }),
  { name: "Spikes", mat: "spikes", fudge: 2, pos: [0, 0.75, 0], bursts: [[0, 1]], life: 1.0, size: 1.9,
    sizeLife: [[0, 0.05, 0, 8], [0.12, 1.05], [0.17, 1], [0.85, 1], [1, 0.2]], alpha: [[0, 1], [0.85, 1], [1, 0]] },
  { name: "Glow", mat: "spikesGlow", fudge: -1, pos: [0, 0.75, 0], bursts: [[0, 1]], life: 1.0, size: 1.9,
    sizeLife: [[0, 0.05, 0, 8], [0.12, 1.05], [0.17, 1], [0.85, 1], [1, 0.2]], alpha: [[0, 0.9], [0.5, 1], [0.85, 0.7], [1, 0]] },
  { name: "Clods", mat: "chunkDark", fudge: 2, pos: [0, 0.1, 0], bursts: [[0, 10], [0.88, 6]], shape: { type: "circle", r: 0.4, th: 1 },
    speed: [0.8, 1.6], vel: { y: [2, 3], space: "world" }, gravity: 2.4, life: [0.35, 0.45], size: [0.12, 0.2], rot: [0, TAU], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "Motes", mat: "dotVoid", fudge: -3, pos: [0, 0.1, 0], rate: 10, shape: { type: "circle", r: 0.5, th: 0.6 }, speed: [0, 0.1],
    vel: { y: [0.6, 1.0], space: "world" }, life: [0.4, 0.5], size: [0.04, 0.06], alpha: popIn },
];

// ---------------------------------------------------------------- 15 일식 (빛+어둠): sleep 1 s
const ECLIPSE = [
  { name: "Disc", mat: "disc", fudge: 2, pos: [0, 2.25, 0], bursts: [[0, 1]], life: 1.0, size: 0.42, sizeLife: [[0, 0], [0.12, 1.1], [0.2, 1], [0.85, 1], [1, 0]] },
  { name: "Corona", mat: "corona", fudge: -2, pos: [0, 2.25, 0], bursts: [[0, 1]], life: 1.0, size: 1.1, rot: [0, TAU], rotZ: [1.2, 1.2],
    sizeLife: [[0, 0], [0.12, 1.1], [0.2, 1], [0.85, 1.04], [1, 0]], alpha: hold(1.0, 0.15) },
  { name: "Moon", mat: "moon", fudge: -4, pos: [-0.42, 2.6, 0], bursts: [[0.08, 1]], life: 0.92, size: 0.4, rot: [-0.3, -0.3],
    sizeLife: [[0, 1.5], [0.14, 0.95], [0.3, 1], [1, 1]], alpha: hold(0.92, 0.2) },
  { name: "Zs", mat: "zSleep", fudge: -4, pos: [0.35, 2.1, 0], bursts: [[0.12, 1], [0.42, 1], [0.7, 1]], speed: [0, 0],
    vel: { x: [0.35, 0.45], y: [0.55, 0.65], space: "world" }, life: 0.5, size: [0.24, 0.3], sizeLife: [[0, 0.5], [0.3, 1], [1, 1.2]], alpha: popIn },
  decal("Calm", "glowEclipseDecal", 1.9, 1.0, { alpha: [[0, 0], [0.08, 0.55], [0.8, 0.4], [1, 0]] }),
  { name: "Motes", mat: "dotEclipse", fudge: -3, pos: [0, 2.6, 0], rate: 10, shape: { type: "circle", r: 0.6, th: 0.5 }, speed: [0, 0.1],
    vel: { y: [-0.9, -0.5], space: "world" }, life: [0.6, 0.8], size: [0.04, 0.07], alpha: popIn },
];

const MATS = {
  glowWarm: { tex: "T_Glow", tint: [2.4, 1.7, 1.1], add: true },
  waveSteam: { tex: "T_Wave", tint: [1.6, 1.9, 2.2], add: true },
  steam: { tex: "T_Steam_Sheet", tint: [1.6, 1.6, 1.65], add: true },
  streakWater: { tex: "T_Streak", tint: [1.3, 2.0, 2.8], add: true },
  streakFire: { tex: "T_Streak", tint: [2.8, 1.6, 0.6], add: true },
  char: { tex: "T_Char", tint: [0.16, 0.11, 0.1], add: false },
  fireRing: { tex: "T_FireRing", tint: [1.7, 1.6, 1.5], add: true },
  flame: { tex: "T_Flame_Sheet", tint: [1.6, 1.5, 1.4], add: true },
  lava: { tex: "T_Lava", tint: [1.8, 1.6, 1.5], add: true },
  dotLava: { tex: "T_Dot", tint: [2.6, 1.6, 0.8], add: true },
  sealFire: { tex: "T_Seal", tint: [2.8, 1.5, 0.6], add: true },
  sun: { tex: "T_Sun", tint: [1.7, 1.6, 1.4], add: true },
  glowSun: { tex: "T_Glow", tint: [2.4, 1.8, 0.6], add: true },
  waveSun: { tex: "T_Wave", tint: [2.6, 2.1, 1.0], add: true },
  streakSun: { tex: "T_Streak", tint: [2.8, 2.3, 1.1], add: true },
  downSun: { tex: "T_Down", tint: [2.6, 2.0, 0.9], add: true },
  emberSigil: { tex: "T_EmberSigil", tint: [1.8, 1.6, 1.5], add: true },
  mistDark: { tex: "T_Mist_Sheet", tint: [0.2, 0.09, 0.09], add: false },
  manaShard: { tex: "T_ManaShard", tint: [1.6, 1.7, 1.9], add: true },
  manaShardIce: { tex: "T_ManaShard", tint: [1.6, 1.9, 2.0], add: true },
  manaCrack: { tex: "T_ManaCrack", tint: [2.2, 2.2, 2.4], add: true },
  bolt: { tex: "T_Bolt_Sheet", tint: [1.7, 1.7, 1.9], add: true },
  glowBolt: { tex: "T_Glow", tint: [1.3, 1.9, 3.0], add: true },
  starBolt: { tex: "T_Star", tint: [2.0, 2.5, 3.0], add: true },
  streakBolt: { tex: "T_Streak", tint: [1.8, 2.3, 3.0], add: true },
  glowBoltDecal: { tex: "T_Glow", tint: [0.9, 1.4, 2.4], add: true },
  arc: { tex: "T_Arc_Sheet", tint: [1.7, 1.7, 1.9], add: true },
  mud: { tex: "T_Mud", tint: [1, 1, 1], add: false },
  waveMud: { tex: "T_Wave", tint: [1.6, 1.15, 0.7], add: true },
  chunkMud: { tex: "T_Chunk_Sheet", tint: [0.55, 0.4, 0.27], add: false },
  downMud: { tex: "T_Down", tint: [2.2, 1.5, 0.8], add: true },
  prism: { tex: "T_Prism", tint: [1.7, 1.7, 1.7], add: true },
  glowSoft: { tex: "T_Glow", tint: [1.8, 1.8, 1.8], add: true },
  streakSoft: { tex: "T_Streak", tint: [2.2, 2.2, 2.2], add: true },
  cross: { tex: "T_Cross", tint: [1.5, 1.7, 1.5], add: true },
  glowHeal: { tex: "T_Glow", tint: [1.0, 2.0, 1.1], add: true },
  streakHeal: { tex: "T_Streak", tint: [1.4, 2.6, 1.5], add: true },
  poison: { tex: "T_Poison", tint: [1.15, 1.2, 1.15], add: true },
  bubble: { tex: "T_Bubble", tint: [1.6, 1.7, 1.5], add: true },
  steamPoison: { tex: "T_Steam_Sheet", tint: [0.35, 0.65, 0.3], add: true },
  dotPoison: { tex: "T_Dot", tint: [1.6, 2.6, 1.1], add: true },
  sandVortex: { tex: "T_SandVortex", tint: [1.6, 1.5, 1.4], add: true },
  grain: { tex: "T_Dot", tint: [1.0, 0.82, 0.52], add: false },
  dust: { tex: "T_Mist_Sheet", tint: [0.85, 0.72, 0.5], add: false },
  starWhite: { tex: "T_Star", tint: [2.8, 2.8, 2.6], add: true },
  glowWhite: { tex: "T_Glow", tint: [2.4, 2.4, 2.3], add: true },
  waveWhite: { tex: "T_Wave", tint: [2.2, 2.2, 2.1], add: true },
  voidInk: { tex: "T_VoidInk", tint: [1, 1, 1], add: false },
  voidRim: { tex: "T_VoidRim", tint: [1.8, 1.6, 2.0], add: true },
  wisp: { tex: "T_Wisp", tint: [1.6, 1.4, 1.9], add: true },
  mistVoid: { tex: "T_Mist_Sheet", tint: [0.14, 0.05, 0.22], add: false },
  glowIceDecal: { tex: "T_Glow", tint: [1.0, 1.6, 2.0], add: true },
  waveIce: { tex: "T_Wave", tint: [1.9, 2.4, 2.7], add: true },
  crystal: { tex: "T_Crystal", tint: [1.4, 1.45, 1.5], add: true },
  starIce: { tex: "T_Star", tint: [2.2, 2.6, 2.8], add: true },
  starGold: { tex: "T_Star", tint: [2.8, 2.4, 1.2], add: true },
  spikes: { tex: "T_Spikes", tint: [1, 1, 1], add: false },
  spikesGlow: { tex: "T_SpikesGlow", tint: [1.8, 1.5, 2.0], add: true },
  chunkDark: { tex: "T_Chunk_Sheet", tint: [0.3, 0.24, 0.28], add: false },
  dotVoid: { tex: "T_Dot", tint: [1.8, 1.1, 2.8], add: true },
  disc: { tex: "T_Disc", tint: [1, 1, 1], add: false },
  corona: { tex: "T_Corona", tint: [1.7, 1.6, 1.8], add: true },
  moon: { tex: "T_Moon", tint: [2.6, 2.2, 1.6], add: true },
  zSleep: { tex: "T_Z", tint: [2.2, 1.9, 2.8], add: true },
  glowEclipseDecal: { tex: "T_Glow", tint: [0.9, 0.6, 1.5], add: true },
  dotEclipse: { tex: "T_Dot", tint: [2.2, 1.7, 2.6], add: true },
};

const SYS = [
  ["steam", "증기 폭발 (범위 3)", STEAM, 0.7, "area"],
  ["wildfire", "불씨 확산 (범위 2)", WILDFIRE, 0.7, "area"],
  ["wildfire_burn", "불씨 확산 — 화상 대상", BURN, 0.5, "body"],
  ["magma", "용암 분출 — 회복봉인", MAGMA, 0.85, "body"],
  ["flare", "태양 플레어 — 발광+약화", FLARE, 0.7, "body"],
  ["ember_curse", "잿불 낙인 — 마나번", EMBER_CURSE, 0.85, "body"],
  ["shock", "연쇄 번개", SHOCK, 0.26, "bolt"],
  ["shock_disarm", "연쇄 번개 — 무장해제 1초", DISARM, 1.0, "body"],
  ["mud", "진흙 — 둔화", MUD, 0.85, "body"],
  ["rainbow", "무지개 (아군 범위 4)", RAINBOW, 0.9, "area"],
  ["rainbow_heal", "무지개 — 회복 대상", HEAL, 0.65, "body"],
  ["abyss", "심연 — 독", ABYSS, 0.85, "body"],
  ["sandstorm", "모래 회오리 (범위 4)", SANDSTORM, 0.7, "area"],
  ["sandstorm_pin", "모래 회오리 — 고정 1초", PIN, 1.0, "body"],
  ["flash", "섬광 (미정)", FLASH, 0.3, "body"],
  ["void_gust", "공허 균열 — 공포 1초", VOID_GUST, 1.0, "body"],
  ["crystal", "수정화 — 기절 1초", CRYSTAL, 1.0, "body"],
  ["bind", "매장 — 속박 1초", BIND, 1.0, "body"],
  ["eclipse", "일식 — 수면 1초", ECLIPSE, 1.0, "body"],
];

const UP = [0, 1, 0], FWD = [0, 0, 1];
const scen = {};
const RING_DUMMIES = r => [[0, 0, 0, 0], [r * 0.75, 0, r * 0.45, -120], [-r * 0.7, 0, r * 0.5, 140], [r * 0.2, 0, -r * 0.8, 10]];
for (const [id, label, , , kind] of SYS) {
  if (kind === "bolt") continue;
  const area = kind === "area";
  const r = { steam: 3, wildfire: 2, rainbow: 4, sandstorm: 4 }[id] || 0;
  const followUp = { wildfire: "wildfire_burn", rainbow: "rainbow_heal", sandstorm: "sandstorm_pin" }[id];
  const hits = [{ t: 0.3, point: [0, 0, 0], normal: UP, incoming: FWD, system: id }];
  if (followUp) for (const [x, , z] of RING_DUMMIES(r).slice(1)) hits.push({ t: id === "sandstorm" ? 0.75 : 0.32, point: [x, 0, z], normal: UP, incoming: FWD, system: followUp });
  scen[id] = { label, targets: area ? RING_DUMMIES(r) : [[0, 0, 0, 0]], hits,
    camera: { target: [0, 0.9, 0], dist: area ? 4 + r * 2 : 4.6, yaw: -35, pitch: area ? 34 : 14 } };
}
scen.shock = { label: "연쇄 번개 + 무장해제", targets: [[0, 0, 0, 0], [0, 0, 3, 180], [0, 0, 6, 0], [0, 0, 9, 180]],
  hits: [0, 1, 2].flatMap(k => [{ t: 0.3 + 0.15 * k, point: [0, 1.0, 3 * k], normal: UP, incoming: FWD, system: "shock" },
                                 { t: 0.3 + 0.15 * k, point: [0, 0, 3 * (k + 1)], normal: UP, incoming: FWD, system: "shock_disarm" }]),
  camera: { target: [0, 0.9, 4.5], dist: 9, yaw: -70, pitch: 16 } };
const BODY = ["magma", "flare", "ember_curse", "mud", "abyss", "void_gust", "crystal", "bind", "eclipse", "flash"];
scen.all = { label: "단일 대상 전체", targets: [], hits: [], camera: { target: [0, 0.9, 2], dist: 13, yaw: -15, pitch: 22 } };
BODY.forEach((id, i) => {
  const x = (i % 5 - 2) * 2.6, z = Math.floor(i / 5) * 3.2;
  scen.all.targets.push([x, 0, z, 0]); scen.all.hits.push({ t: 0.3, point: [x, 0, z], normal: UP, incoming: FWD, system: id });
});

const SCENE = {
  name: "ElementReactions",
  // Existing PortableVFX scaleInput module, including the lightning bolt minimum.
  scale: { input: "targetHeight", unit: "m", reference: 1.8, min: 0.15, max: 4 },
  bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.65, tint: [1, 0.95, 0.92] }, tonemapping: "neutral",
  systems: SYS.map(([id, , layers, len]) => ({ id, role: "impact", loop: false, duration: Math.max(0.2, len), layers })),
  demo: {
    interval: 2.0, maxImpacts: 24, dummy: { height: 1.8 }, bg: [0.33, 0.34, 0.36], floor: [0.42, 0.42, 0.43], grid: [0.47, 0.47, 0.48],
    scenarios: scen,
  },
};
