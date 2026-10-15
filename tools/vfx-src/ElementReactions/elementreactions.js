// ElementReactions — 15 small one-shot reaction effects (차카데미 원소 반응).
// One pack, one impact system per reaction. Short (0.15–0.5 s, void rift 1 s), restrained, nothing lingers.
// Server placement (magic-mythic Reactions): P = target feet (up = world up, forward = yaw), instance scale = target height / 1.8
// for body effects; area effects (steam, rainbow, sandstorm, eclipse burst) are authored at their real radius and played at scale 1.
// shock: P = source chest, bolt runs along local +Z for BOLT_LEN m; the server sets yaw toward the next target and scale = distance / BOLT_LEN.
const BOLT_LEN = 3;
const WHITE = [1, 1, 1];
const F44 = { x: 4, y: 4 }, F22 = { x: 2, y: 2 };
const TAU = 2 * Math.PI;

var EXTRA_MESHES = {
  // two crossed planes from z = 0 to BOLT_LEN (u along the bolt) so the bolt reads from the side and from above
  bolt: {
    verts: [[-0.16, 0, 0], [0.16, 0, 0], [0.16, 0, BOLT_LEN], [-0.16, 0, BOLT_LEN],
            [0, -0.16, 0], [0, 0.16, 0], [0, 0.16, BOLT_LEN], [0, -0.16, BOLT_LEN]],
    uvs: [[0, 0], [0, 1], [1, 1], [1, 0], [0, 0], [0, 1], [1, 1], [1, 0]],
    tris: [0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7],
  },
};

const fade = [[0, 1], [0.6, 0.8], [1, 0]];
const popIn = [[0, 0], [0.12, 1], [0.7, 0.7], [1, 0]];

// ---------------------------------------------------------------- 1 증기 폭발 (화염+물) 0.3 s, 2-block radius
const STEAM = [
  { name: "Flash", mat: "glowWarm", pos: [0, 0.8, 0], bursts: [[0, 1]], life: 0.14, size: 1.0, sizeLife: [[0, 0.5], [1, 1.1]], alpha: [[0, 0.8], [1, 0]] },
  { name: "Puffs", mat: "steam", fudge: 4, pos: [0, 0.6, 0], bursts: [[0, 12]], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [2.5, 4], drag: 7, life: [0.3, 0.42], size: [0.55, 0.85], rot: [0, TAU], vel: { y: [0.4, 0.9], space: "world" },
    sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0.75], [0.5, 0.5], [1, 0]], flip: F44 },
  { name: "Ring", mat: "ringPale", mode: "mesh", mesh: "quad", pos: [0, 0.05, 0], bursts: [[0, 1]], life: 0.28, size: 4,
    rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.2, 0, 2.5], [1, 1]], alpha: [[0, 0.7], [1, 0]] },
  { name: "Drops", mat: "dotWater", fudge: -3, pos: [0, 0.6, 0], bursts: [[0, 10]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [3, 5], drag: 2, gravity: 1.2, life: [0.22, 0.32], size: [0.04, 0.07], alpha: fade },
];

// ---------------------------------------------------------------- 2 불씨 확산 (화염+바람) 0.2 s (also played on each spread target)
const WILDFIRE = [
  { name: "Glow", mat: "glowFire", pos: [0, 0.9, 0], bursts: [[0, 1]], life: 0.15, size: 0.7, sizeLife: [[0, 0.6], [1, 1]], alpha: [[0, 0.9], [1, 0]] },
  { name: "Sparks", mat: "streakFire", mode: "stretch", vs: 0.07, ls: 1.4, fudge: -3, pos: [0, 0.9, 0], bursts: [[0, 14]],
    shape: { type: "sphere", r: 0.15, th: 1 }, speed: [4, 7], drag: 4, gravity: 0.4, life: [0.14, 0.24], size: [0.04, 0.07], alpha: fade },
  { name: "Embers", mat: "dotFire", fudge: -4, pos: [0, 0.9, 0], bursts: [[0, 6]], shape: { type: "sphere", r: 0.25, th: 1 },
    speed: [0.6, 1.4], drag: 2, gravity: -0.2, life: [0.25, 0.32], size: [0.03, 0.05], color: [[0, [1, 0.85, 0.4]], [1, [1, 0.3, 0.08]]], alpha: fade },
];

// ---------------------------------------------------------------- 3 용암 분출 (화염+대지) 0.4 s, crack under the feet
const MAGMA = [
  { name: "Crack", mat: "crackLava", mode: "mesh", mesh: "quad", fudge: 2, pos: [0, 0.03, 0], bursts: [[0, 1]], life: 0.4, size: 1.5,
    rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.7, 0, 2], [0.25, 1], [1, 1]], color: [[0, [1, 0.9, 0.6]], [0.4, [1, 0.5, 0.15]], [1, [0.7, 0.12, 0.05]]],
    alpha: [[0, 0], [0.08, 1], [0.6, 0.8], [1, 0]] },
  { name: "Glow", mat: "glowFire", pos: [0, 0.15, 0], bursts: [[0, 1]], life: 0.3, size: 1.1, sizeLife: [[0, 0.5], [1, 1]], alpha: [[0, 0.6], [1, 0]] },
  { name: "Lava", mat: "dotFire", fudge: -3, pos: [0, 0.1, 0], bursts: [[0.03, 8]], shape: { type: "circle", r: 0.35, th: 1 },
    speed: [0.3, 0.9], vel: { y: [2.2, 3.4], space: "world" }, gravity: 1.4, life: [0.3, 0.38], size: [0.05, 0.09],
    color: [[0, [1, 0.85, 0.45]], [1, [1, 0.25, 0.06]]], alpha: fade },
];

// ---------------------------------------------------------------- 4 태양 플레어 (화염+빛) 0.2 s flash
const FLARE = [
  { name: "Star", mat: "starSun", fudge: -4, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.2, size: 1.6, rot: [0, 45 * D2R],
    sizeLife: [[0, 0.2], [0.25, 1.1], [1, 0]] },
  { name: "Glow", mat: "glowSun", pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.18, size: 1.2, sizeLife: [[0, 0.4], [0.3, 1], [1, 0.9]], alpha: [[0, 0.9], [1, 0]] },
  { name: "Ring", mat: "ringSun", fudge: 1, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.2, size: 1.6, sizeLife: [[0, 0.25, 0, 2], [1, 1]], alpha: [[0, 0.9], [1, 0]] },
];

// ---------------------------------------------------------------- 5 잿불 낙인 (화염+어둠) 0.3 s brand
const EMBER_CURSE = [
  { name: "Sigil", mat: "sigilEmber", pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.32, size: 0.7, rot: [0, TAU], rotZ: [2, 2],
    sizeLife: [[0, 1.5], [0.3, 1], [1, 0.9]], color: [[0, [1, 0.8, 0.45]], [1, [0.9, 0.2, 0.08]]], alpha: popIn },
  { name: "Mist", mat: "mistDark", fudge: 6, pos: [0, 1.0, 0], bursts: [[0, 3]], shape: { type: "sphere", r: 0.15, th: 1 }, speed: [0.2, 0.5],
    life: [0.3, 0.36], size: [0.5, 0.7], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.45], [1, 0]], flip: F44 },
  { name: "Embers", mat: "dotFire", fudge: -3, pos: [0, 1.0, 0], bursts: [[0.05, 6]], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [0.2, 0.6], vel: { y: [0.6, 1.0], space: "world" }, life: [0.25, 0.32], size: [0.03, 0.05],
    color: [[0, [1, 0.7, 0.3]], [1, [0.8, 0.15, 0.05]]], alpha: fade },
];

// ---------------------------------------------------------------- 6 연쇄 번개 (물+바람) 0.2 s per jump, P = source chest
const SHOCK = [
  { name: "Bolt", mat: "bolt", mode: "mesh", mesh: "bolt", bursts: [[0, 1]], life: 0.2, size: 1, flip: { x: 1, y: 4, cycles: 2 },
    alpha: [[0, 1], [0.6, 1], [1, 0]] },
  { name: "SrcGlow", mat: "glowBolt", pos: [0, 0, 0], bursts: [[0, 1]], life: 0.12, size: 0.6, alpha: [[0, 0.8], [1, 0]] },
  { name: "HitGlow", mat: "glowBolt", pos: [0, 0, BOLT_LEN], bursts: [[0, 1]], life: 0.16, size: 0.9, sizeLife: [[0, 0.5], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "HitSparks", mat: "streakBolt", mode: "stretch", vs: 0.06, ls: 1.4, fudge: -3, pos: [0, 0, BOLT_LEN], bursts: [[0, 8]],
    shape: { type: "sphere", r: 0.1, th: 1 }, speed: [3, 6], drag: 4, life: [0.12, 0.2], size: [0.03, 0.05], alpha: fade },
];

// ---------------------------------------------------------------- 7 진흙 (물+대지) 0.3 s splash at the feet
const MUD = [
  { name: "Splat", mat: "mud", mode: "mesh", mesh: "quad", fudge: 6, pos: [0, 0.03, 0], bursts: [[0, 1]], life: 0.32, size: 1.3,
    rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.4, 0, 3], [0.3, 1], [1, 1.05]], alpha: [[0, 0.85], [0.6, 0.7], [1, 0]], flip: F44 },
  { name: "Clods", mat: "chunkMud", fudge: 2, pos: [0, 0.15, 0], bursts: [[0, 8]], shape: { type: "circle", r: 0.3, th: 1 },
    speed: [1, 2], vel: { y: [1.6, 2.6], space: "world" }, gravity: 2.2, life: [0.26, 0.34], size: [0.1, 0.18], rot: [0, TAU], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "Drops", mat: "dotMud", fudge: 1, pos: [0, 0.15, 0], bursts: [[0, 10]], shape: { type: "circle", r: 0.25, th: 1 },
    speed: [1.5, 3], vel: { y: [1.2, 2.2], space: "world" }, gravity: 2.5, life: [0.24, 0.3], size: [0.04, 0.07], alpha: fade },
];

// ---------------------------------------------------------------- 8 무지개 (물+빛) 0.5 s, allies within 4 blocks
const RB1 = [[0, [1, 0.45, 0.45]], [0.5, [1, 0.85, 0.4]], [1, [0.6, 1, 0.5]]];
const RB2 = [[0, [0.5, 1, 0.7]], [0.5, [0.45, 0.85, 1]], [1, [0.6, 0.55, 1]]];
const RB3 = [[0, [0.75, 0.55, 1]], [0.5, [1, 0.5, 0.85]], [1, [1, 0.6, 0.5]]];
const rbMotes = (name, col, seed) => ({ name, mat: "dotSoft", fudge: -2, pos: [0, 0.2, 0], bursts: [[0.02 * seed, 8]],
  shape: { type: "circle", r: 2.4, th: 0.85 }, speed: [0, 0.2], vel: { y: [0.8, 1.6], space: "world" }, drag: 1,
  life: [0.4, 0.5], size: [0.06, 0.1], color: col, alpha: [[0, 0], [0.15, 1], [0.7, 0.8], [1, 0]] });
const RAINBOW = [
  { name: "Ring", mat: "ringSoft", mode: "mesh", mesh: "quad", pos: [0, 0.05, 0], bursts: [[0, 1]], life: 0.5, size: 8,
    rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.15, 0, 2.2], [1, 1]],
    color: [[0, [1, 0.5, 0.5]], [0.25, [1, 0.9, 0.45]], [0.5, [0.5, 1, 0.6]], [0.75, [0.45, 0.75, 1]], [1, [0.75, 0.5, 1]]],
    alpha: [[0, 0.8], [0.6, 0.5], [1, 0]] },
  { name: "Glow", mat: "glowSoft", pos: [0, 0.9, 0], bursts: [[0, 1]], life: 0.25, size: 0.9, alpha: [[0, 0.6], [1, 0]] },
  rbMotes("Motes1", RB1, 0), rbMotes("Motes2", RB2, 1), rbMotes("Motes3", RB3, 2),
];

// ---------------------------------------------------------------- 9 심연 (물+어둠) 0.4 s whirlpool under the target
const ABYSS = [
  { name: "Swirl", mat: "swirlAbyss", mode: "mesh", mesh: "quad", pos: [0, 0.04, 0], bursts: [[0, 1]], life: 0.4, size: 1.7,
    rot3: [0, [0, TAU], 0], rotLife: [0, -9, 0], sizeLife: [[0, 1.3], [1, 0.7]],
    color: [[0, [0.55, 0.9, 1]], [1, [0.55, 0.3, 1]]], alpha: [[0, 0], [0.15, 1], [0.7, 0.8], [1, 0]] },
  { name: "Mist", mat: "mistAbyss", mode: "mesh", mesh: "quad", fudge: 8, pos: [0, 0.03, 0], bursts: [[0, 1]], life: 0.4, size: 1.4,
    rot3: [0, [0, TAU], 0], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: F44 },
  { name: "Bubbles", mat: "dotWater", fudge: -2, pos: [0, 0.1, 0], bursts: [[0.04, 8]], shape: { type: "circle", r: 0.5, th: 0.6 },
    speed: [0, 0.2], vel: { y: [1.0, 1.8], space: "world", orb: 3 }, life: [0.3, 0.36], size: [0.04, 0.07], alpha: fade },
];

// ---------------------------------------------------------------- 10 모래 회오리 (바람+대지) 0.5 s, pulls a 3-block circle inward
const SAND = [0.95, 0.78, 0.5];
const SANDSTORM = [
  { name: "Swirl", mat: "swirlSand", mode: "mesh", mesh: "quad", pos: [0, 0.08, 0], bursts: [[0, 1]], life: 0.5, size: 6.4,
    rot3: [0, [0, TAU], 0], rotLife: [0, -7, 0], sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 0], [0.15, 0.8], [0.7, 0.6], [1, 0]] },
  { name: "Grains", mat: "grain", fudge: -1, pos: [0, 0.25, 0], bursts: [[0, 26]], shape: { type: "circle", r: 3, th: 0.25 },
    speed: [0, 0.1], vel: { y: [0.1, 0.5], space: "world", orb: -5, rad: -5 }, life: [0.42, 0.5], size: [0.05, 0.09],
    alpha: [[0, 0], [0.15, 1], [0.75, 0.9], [1, 0]] },
  { name: "Dust", mat: "dust", fudge: 6, pos: [0, 0.3, 0], bursts: [[0.25, 4]], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [0.3, 0.8],
    vel: { y: [0.2, 0.5], space: "world" }, life: [0.25, 0.3], size: [0.5, 0.75], rot: [0, TAU], alpha: [[0, 0.5], [1, 0]], flip: F44 },
];

// ---------------------------------------------------------------- 11 섬광 (바람+빛) 0.15 s
const FLASH = [
  { name: "Glow", mat: "glowWhite", pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.15, size: 1.8, sizeLife: [[0, 0.5], [0.2, 1], [1, 1.1]], alpha: [[0, 0.9], [1, 0]] },
  { name: "Star", mat: "starWhite", fudge: -4, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.12, size: 2.2, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  { name: "Ring", mat: "ringWhite", fudge: 1, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 0.15, size: 2.0, sizeLife: [[0, 0.2, 0, 2.5], [1, 1]], alpha: [[0, 0.8], [1, 0]] },
];

// ---------------------------------------------------------------- 12 공허 균열 (바람+어둠) 1 s, marks the point the target returns to
const VOID_GUST = [
  { name: "Rift", mat: "rift", pos: [0, 1.0, 0], bursts: [[0, 1]], life: 1.0, size: 1.5,
    sizeLife: [[0, 0.15], [0.12, 1], [0.85, 1], [1, 0.1]], color: [[0, [0.85, 0.7, 1]], [1, [0.55, 0.3, 1]]], alpha: [[0, 0], [0.1, 1], [0.8, 0.85], [1, 0]] },
  { name: "Mist", mat: "mistVoid", fudge: 6, pos: [0, 1.0, 0], bursts: [[0, 2]], shape: { type: "sphere", r: 0.1, th: 1 }, speed: [0, 0.1],
    life: [0.95, 1.0], size: [0.8, 1.0], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.45], [0.8, 0.35], [1, 0]], flip: F44 },
  { name: "Motes", mat: "dotVoid", fudge: -2, pos: [0, 1.0, 0], rate: 10, shape: { type: "sphere", r: 0.6, th: 0.3 }, speed: [0, 0.1],
    vel: { rad: -1.2, orb: 2 }, life: [0.35, 0.45], size: [0.03, 0.05], alpha: [[0, 0], [0.3, 1], [1, 0]] },
  { name: "Glow", mat: "glowVoid", fudge: 3, pos: [0, 1.0, 0], bursts: [[0, 1]], life: 1.0, size: 1.0, sizeLife: [[0, 0.3], [0.15, 1], [0.85, 1], [1, 0.2]], alpha: [[0, 0], [0.1, 0.5], [0.8, 0.4], [1, 0]] },
];

// ---------------------------------------------------------------- 13 수정화 (대지+빛) 0.3 s crystal glints on the body
const CRYSTAL = [
  { name: "Shards", mat: "shard", pos: [0, 0.9, 0], bursts: [[0, 7]], shape: { type: "sphere", r: 0.45, th: 0.3 }, speed: [0, 0.15],
    life: [0.26, 0.32], size: [0.22, 0.36], rot: [-0.4, 0.4], sizeLife: [[0, 0, 0, 6], [0.25, 1], [0.75, 1], [1, 0]],
    color: [[0, [0.9, 1, 1]], [1, [0.55, 0.85, 1]]] },
  { name: "Glints", mat: "starIce", fudge: -3, pos: [0, 0.9, 0], bursts: [[0.04, 3], [0.12, 2]], shape: { type: "sphere", r: 0.5, th: 0.4 },
    life: 0.14, size: [0.25, 0.4], sizeLife: [[0, 0.2], [0.35, 1], [1, 0]] },
  { name: "Glow", mat: "glowIce", fudge: 2, pos: [0, 0.9, 0], bursts: [[0, 1]], life: 0.25, size: 1.1, alpha: [[0, 0.5], [1, 0]] },
];

// ---------------------------------------------------------------- 14 매장 (대지+어둠) 0.3 s earth burst at the feet
const BIND = [
  { name: "Clods", mat: "chunkDark", fudge: 2, pos: [0, 0.1, 0], bursts: [[0, 10]], shape: { type: "circle", r: 0.35, th: 1 },
    speed: [0.6, 1.4], vel: { y: [1.6, 2.6], space: "world" }, gravity: 2.4, life: [0.26, 0.32], size: [0.1, 0.2], rot: [0, TAU], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "Dust", mat: "dustDark", mode: "mesh", mesh: "quad", fudge: 6, pos: [0, 0.03, 0], bursts: [[0, 1]], life: 0.3, size: 1.4,
    rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.5, 0, 3], [0.3, 1], [1, 1.05]], alpha: [[0, 0.7], [1, 0]], flip: F44 },
  { name: "Seam", mat: "crackDark", mode: "mesh", mesh: "quad", fudge: 3, pos: [0, 0.04, 0], bursts: [[0, 1]], life: 0.3, size: 1.1,
    rot3: [0, [0, TAU], 0], alpha: [[0, 0], [0.1, 0.8], [1, 0]] },
];

// ---------------------------------------------------------------- 15 일식 (빛+어둠) 0.5 s orb, burst at 0.5 s (server damage follows 10 ticks later)
const ECL_BURST = 0.45;
const ECLIPSE = [
  { name: "Disc", mat: "disc", fudge: 2, pos: [0, 1.1, 0], bursts: [[0, 1]], life: 0.5, size: 0.55, sizeLife: [[0, 0], [0.3, 1], [0.85, 1], [1, 0]] },
  { name: "Corona", mat: "ringEclipse", fudge: -2, pos: [0, 1.1, 0], bursts: [[0, 1]], life: 0.5, size: 0.75, rot: [0, TAU], rotZ: [3, 3],
    sizeLife: [[0, 0], [0.3, 1], [0.85, 1.05], [1, 0]], color: [[0, [1, 0.85, 0.55]], [1, [0.8, 0.5, 1]]] },
  { name: "Halo", mat: "glowEclipse", fudge: 4, pos: [0, 1.1, 0], bursts: [[0, 1]], life: 0.5, size: 1.3, sizeLife: [[0, 0], [0.3, 1], [1, 0.6]], alpha: [[0, 0.5], [1, 0]] },
  { name: "Burst", mat: "ringEclipse", mode: "mesh", mesh: "quad", pos: [0, 0.05, 0], bursts: [[ECL_BURST, 1]], life: 0.25, size: 6,
    rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.1, 0, 2.5], [1, 1]], color: [[0, [1, 0.85, 0.6]], [1, [0.7, 0.45, 1]]], alpha: [[0, 1], [1, 0]] },
  { name: "Pop", mat: "starWhite", fudge: -4, pos: [0, 1.1, 0], bursts: [[ECL_BURST, 1]], life: 0.12, size: 1.2, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
];

const MATS = {
  glowWarm: { tex: "T_Glow", tint: [2.0, 1.6, 1.2], add: true },
  steam: { tex: "T_Puff_Sheet", tint: [0.95, 0.97, 1.0], add: false },
  ringPale: { tex: "T_Ring", tint: [1.6, 1.8, 2.0], add: true },
  dotWater: { tex: "T_Dot", tint: [1.2, 1.9, 2.6], add: true },
  glowFire: { tex: "T_Glow", tint: [2.2, 1.0, 0.35], add: true },
  streakFire: { tex: "T_Streak", tint: [2.6, 1.7, 0.8], add: true },
  dotFire: { tex: "T_Dot", tint: [2.4, 1.6, 0.9], add: true },
  crackLava: { tex: "T_Crack", tint: [2.4, 1.5, 0.9], add: true },
  starSun: { tex: "T_Star", tint: [2.8, 2.4, 1.4], add: true },
  glowSun: { tex: "T_Glow", tint: [2.4, 1.8, 0.7], add: true },
  ringSun: { tex: "T_Ring", tint: [2.4, 1.9, 1.0], add: true },
  sigilEmber: { tex: "T_Sigil", tint: [2.2, 1.6, 1.2], add: true },
  mistDark: { tex: "T_Puff_Sheet", tint: [0.22, 0.1, 0.1], add: false },
  bolt: { tex: "T_Bolt_Sheet", tint: [1.7, 2.1, 3.0], add: true },
  glowBolt: { tex: "T_Glow", tint: [1.3, 1.8, 3.0], add: true },
  streakBolt: { tex: "T_Streak", tint: [1.8, 2.2, 3.0], add: true },
  mud: { tex: "T_Puff_Sheet", tint: [0.42, 0.3, 0.2], add: false },
  chunkMud: { tex: "T_Chunk_Sheet", tint: [0.55, 0.4, 0.27], add: false },
  dotMud: { tex: "T_Dot", tint: [0.45, 0.33, 0.22], add: false },
  ringSoft: { tex: "T_Ring", tint: [1.7, 1.7, 1.7], add: true },
  glowSoft: { tex: "T_Glow", tint: [1.8, 1.8, 1.8], add: true },
  dotSoft: { tex: "T_Dot", tint: [2.0, 2.0, 2.0], add: true },
  swirlAbyss: { tex: "T_Swirl", tint: [1.6, 1.8, 2.6], add: true },
  mistAbyss: { tex: "T_Puff_Sheet", tint: [0.1, 0.12, 0.25], add: false },
  swirlSand: { tex: "T_Swirl", tint: [1.5, 1.2, 0.75], add: true },
  grain: { tex: "T_Dot", tint: SAND, add: false },
  dust: { tex: "T_Puff_Sheet", tint: [0.85, 0.72, 0.5], add: false },
  glowWhite: { tex: "T_Glow", tint: [2.4, 2.4, 2.3], add: true },
  starWhite: { tex: "T_Star", tint: [2.8, 2.8, 2.6], add: true },
  ringWhite: { tex: "T_Ring", tint: [2.2, 2.2, 2.1], add: true },
  rift: { tex: "T_Rift", tint: [2.0, 1.6, 2.8], add: true },
  mistVoid: { tex: "T_Puff_Sheet", tint: [0.15, 0.06, 0.22], add: false },
  dotVoid: { tex: "T_Dot", tint: [1.8, 1.2, 2.6], add: true },
  glowVoid: { tex: "T_Glow", tint: [1.2, 0.6, 2.0], add: true },
  shard: { tex: "T_Shard", tint: [1.6, 2.0, 2.2], add: true },
  starIce: { tex: "T_Star", tint: [2.2, 2.6, 2.8], add: true },
  glowIce: { tex: "T_Glow", tint: [1.2, 1.8, 2.2], add: true },
  chunkDark: { tex: "T_Chunk_Sheet", tint: [0.32, 0.25, 0.22], add: false },
  dustDark: { tex: "T_Puff_Sheet", tint: [0.25, 0.2, 0.2], add: false },
  crackDark: { tex: "T_Crack", tint: [1.1, 0.6, 1.4], add: true },
  disc: { tex: "T_Disc", tint: [1, 1, 1], add: false },
  ringEclipse: { tex: "T_Ring", tint: [2.4, 2.0, 2.4], add: true },
  glowEclipse: { tex: "T_Glow", tint: [1.4, 0.8, 1.8], add: true },
};

const SYS = [
  ["steam", "증기 폭발", STEAM, 0.3], ["wildfire", "불씨 확산", WILDFIRE, 0.2], ["magma", "용암 분출", MAGMA, 0.4],
  ["flare", "태양 플레어", FLARE, 0.2], ["ember_curse", "잿불 낙인", EMBER_CURSE, 0.3], ["shock", "연쇄 번개", SHOCK, 0.2],
  ["mud", "진흙", MUD, 0.3], ["rainbow", "무지개", RAINBOW, 0.5], ["abyss", "심연", ABYSS, 0.4],
  ["sandstorm", "모래 회오리", SANDSTORM, 0.5], ["flash", "섬광", FLASH, 0.15], ["void_gust", "공허 균열", VOID_GUST, 1.0],
  ["crystal", "수정화", CRYSTAL, 0.3], ["bind", "매장", BIND, 0.3], ["eclipse", "일식", ECLIPSE, 0.5],
];

const UP = [0, 1, 0], FWD = [0, 0, 1];
const scen = {};
for (const [id, label] of SYS) {
  if (id === "shock") continue;
  const area = ["steam", "rainbow", "sandstorm", "eclipse"].includes(id);
  scen[id] = { label, targets: area ? [[0, 0, 0, 0], [2.2, 0, 1.2, -120], [-2.0, 0, 1.6, 140], [0.6, 0, -2.6, 10]] : [[0, 0, 0, 0]],
    hits: [{ t: 0.3, point: [0, 0, 0], normal: UP, incoming: FWD, system: id }],
    camera: { target: [0, 0.8, 0], dist: area ? 8.5 : 4.2, yaw: -35, pitch: area ? 34 : 18 } };
}
// chain lightning: three jumps of BOLT_LEN between dummies 3 m apart (in game the server scales each jump to its distance)
scen.shock = { label: "연쇄 번개", targets: [[0, 0, 0, 0], [0, 0, 3, 180], [0, 0, 6, 0], [0, 0, 9, 180]],
  hits: [0, 1, 2].map(k => ({ t: 0.3 + 0.2 * k, point: [0, 1.0, 3 * k], normal: UP, incoming: FWD, system: "shock" })),
  camera: { target: [0, 0.9, 4.5], dist: 9, yaw: -70, pitch: 16 } };
// all fifteen in a 5x3 grid
scen.all = { label: "전체 15종", targets: [], hits: [], camera: { target: [0, 0.6, 3.5], dist: 15, yaw: 0, pitch: 38 } };
SYS.forEach(([id], i) => {
  const x = (i % 5 - 2) * 3, z = Math.floor(i / 5) * 3.5;
  if (id === "shock") { scen.all.targets.push([x, 0, z, 0]); scen.all.hits.push({ t: 0.3, point: [x - 0.0, 1.0, z - 3], normal: UP, incoming: FWD, system: id }); return; }
  scen.all.targets.push([x, 0, z, 0]); scen.all.hits.push({ t: 0.3 + 0.0 * i, point: [x, 0, z], normal: UP, incoming: FWD, system: id });
});

const SCENE = {
  name: "ElementReactions",
  bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.65, tint: [1, 0.95, 0.92] }, tonemapping: "neutral",
  systems: SYS.map(([id, , layers, len]) => ({ id, role: "impact", loop: false, duration: Math.max(0.2, len), layers })),
  demo: {
    interval: 2.0, maxImpacts: 16, dummy: { height: 1.8 }, bg: [0.33, 0.34, 0.36], floor: [0.42, 0.42, 0.43], grid: [0.47, 0.47, 0.48],
    scenarios: scen,
  },
};
