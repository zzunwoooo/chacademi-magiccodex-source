// lib_m.js — 잡몹(AM_*) 공용: 중립 텍스처 + 팔레트별 머티리얼 (기본 "" / 이로치 "S"). lib.js 다음에 붙여서 export.
const AM_PAL = {
  "": { hot: [2.8, 1.6, 0.75], flame: [2.2, 1.1, 0.45], star: [2.8, 1.8, 1.0], glow: [1.5, 0.55, 0.15], dot: [2.6, 1.4, 0.55], fleck: [0.9, 0.86, 0.82],
        mist: [0.30, 0.26, 0.26], shock: [2.2, 0.95, 0.4], dust: [0.36, 0.31, 0.29], crack: [2.0, 0.9, 0.4], line: [2.4, 1.2, 0.5], shard: [1.8, 1.0, 0.6],
        arc: [2.2, 1.0, 0.4], wax: [1.05, 0.97, 0.82] },
  S: { hot: [1.5, 2.5, 2.9], flame: [0.7, 1.8, 2.6], star: [1.8, 2.6, 2.9], glow: [0.35, 1.1, 1.7], dot: [2.4, 2.0, 0.8], fleck: [1.0, 1.0, 1.05],
       mist: [0.95, 0.95, 1.0], shock: [0.7, 1.9, 2.6], dust: [0.92, 0.92, 0.95], crack: [0.8, 1.8, 2.6], line: [2.4, 2.0, 0.9], shard: [0.9, 1.8, 2.6],
       arc: [0.8, 1.9, 2.6], wax: [0.45, 0.5, 0.78] },
};
function amMats() {
  const out = {};
  for (const s of ["", "S"]) {
    const P = AM_PAL[s];
    Object.assign(out, {
      ["aFlame" + s]: { tex: "T_AFlame_Sheet", tint: P.flame, add: true }, ["aFlameHot" + s]: { tex: "T_AFlame_Sheet", tint: P.hot, add: true },
      ["aMist" + s]: { tex: "T_AMist_Sheet", tint: P.mist, add: false }, ["aStreak" + s]: { tex: "T_AStreak", tint: P.hot, add: true },
      ["aStar" + s]: { tex: "T_AStar", tint: P.star, add: true }, ["aGlow" + s]: { tex: "T_AGlow", tint: P.glow, add: true },
      ["aDot" + s]: { tex: "T_ADot", tint: P.dot, add: true }, ["aFleck" + s]: { tex: "T_ADot", tint: P.fleck, add: false },
      ["aShock" + s]: { tex: "T_AShock", tint: P.shock, add: true }, ["aDust" + s]: { tex: "T_ADust", tint: P.dust, add: false },
      ["aCrack" + s]: { tex: "T_ACrack", tint: P.crack, add: false }, ["aLine" + s]: { tex: "T_ALine", tint: P.line, add: true },
      ["aShard" + s]: { tex: "T_AShard", tint: P.shard, add: false }, ["aFang" + s]: { tex: "T_AFang", tint: P.arc, add: true },
      ["aArc" + s]: { tex: "T_AArc", tint: P.arc, add: true }, ["aWax" + s]: { tex: "T_AWax", tint: P.wax, add: false },
      ["aSigil" + s]: { tex: "T_ASigil", tint: P.line, add: true },
    });
  }
  return out;
}
const AM_MATS = amMats();
// ---- building blocks (s = "" | "S") ----
const aFlash = (tag, pos, t, size, s) => [
  { name: tag + " Flash", mat: "aStar" + s, fudge: -8, pos, life: 0.2, size, rot: [0, 45 * D2R], bursts: [[t, 1]], sizeLife: [[0, 0.2], [0.25, 1.1], [1, 0]] },
  { name: tag + " FlashGlow", mat: "aGlow" + s, fudge: -2, pos, life: 0.32, size: size * 0.75, bursts: [[t, 1]], sizeLife: [[0, 0.4], [0.2, 1], [1, 1.1]], alpha: [[0, 0.45], [1, 0]] }];
const aSparks = (tag, pos, t, n, s, sp = [5, 10], r = 0.4) => [
  { name: tag + " Sparks", mat: "aStreak" + s, mode: "stretch", vs: 0.07, ls: 1.6, fudge: -5, pos, life: [0.3, 0.55], size: [0.05, 0.1], shape: { type: "sphere", r, th: 1 },
    speed: sp, drag: 3, gravity: 0.7, bursts: [[t, n]], alpha: [[0, 1], [0.7, 1], [1, 0]] }];
const aRays = (tag, pos, t, n, s, sp = [8, 13], ls = 3.5) => [
  { name: tag + " Rays", mat: "aStreak" + s, mode: "stretch", vs: 0.05, ls, fudge: -6, pos, life: [0.2, 0.32], size: [0.1, 0.2], shape: { type: "sphere", r: 0.4, th: 1 },
    speed: sp, drag: 6, bursts: [[t, n]], alpha: [[0, 0.85], [1, 0]] }];
const aShock = (tag, pos, t, size, s, life = 0.45) => [
  { name: tag + " Shock", mat: "aShock" + s, mode: "mesh", mesh: "quad", fudge: 30, pos: [pos[0], pos[1] + 0.06, pos[2]], life, size, rot3: [0, [0, TAU], 0], bursts: [[t, 1]],
    sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.8], [0.5, 0.5], [1, 0]] },
  { name: tag + " Shock2", mat: "aShock" + s, mode: "mesh", mesh: "quad", fudge: 31, pos: [pos[0], pos[1] + 0.07, pos[2]], life: life * 1.3, size: size * 0.78, rot3: [0, [0, TAU], 0], bursts: [[t + 0.08, 1]],
    sizeLife: [[0, 0.1, 0, 2.5], [1, 1]], alpha: [[0, 0.55], [0.5, 0.35], [1, 0]] }];
const aDustWave = (tag, pos, t, size, s, life = 0.7) => [
  { name: tag + " DustWave", mat: "aDust" + s, mode: "mesh", mesh: "quad", fudge: 36, pos: [pos[0], pos[1] + 0.05, pos[2]], life, size, rot3: [0, [0, TAU], 0], bursts: [[t, 1]],
    sizeLife: [[0, 0.25, 0, 2.5], [1, 1]], alpha: [[0, 0.7], [0.5, 0.45], [1, 0]] }];
const aCrackD = (tag, pos, t, size, s, life = 1.2) => [
  { name: tag + " Crack", mat: "aCrack" + s, mode: "mesh", mesh: "quad", fudge: 44, pos: [pos[0], pos[1] + 0.04, pos[2]], life, size, rot3: [0, [0, TAU], 0], bursts: [[t, 1]],
    sizeLife: [[0, 0.5, 0, 3], [0.2, 1]], alpha: [[0, 0], [0.06, 1], [0.6, 0.85], [1, 0]] }];
const aEmbers = (tag, pos, t, n, s, r = 1.2) => [
  { name: tag + " Embers", mat: "aDot" + s, fudge: -6, pos, life: [0.7, 1.3], size: [0.04, 0.09], shape: { type: "sphere", r, th: 0.6 }, bursts: [[t, n]],
    speed: [0.5, 2.2], drag: 1.5, vel: { y: [0.6, 1.6], space: "world" }, noise: { s: 1, f: 1, sp: 0.7 }, alpha: [[0, 0], [0.05, 1], [0.7, 0.9], [1, 0]], sizeLife: [[0, 1], [1, 0.4]] }];
const aFlecks = (tag, pos, t, n, s, r = 1.0) => [
  { name: tag + " AshFlecks", mat: "aFleck" + s, fudge: 22, pos, life: [0.7, 1.2], size: [0.05, 0.1], shape: { type: "sphere", r, th: 0.6 }, bursts: [[t, n]],
    speed: [1, 3], drag: 2, gravity: 0.25, noise: { s: 0.8, f: 1.2, sp: 0.6 }, alpha: [[0, 0], [0.1, 0.9], [0.8, 0.7], [1, 0]] }];
const aMistP = (tag, pos, t, n, s, r = 0.8, sz = [1.0, 1.7], rad = [0.6, 1.4]) => [
  { name: tag + " Mist", mat: "aMist" + s, fudge: 35, pos, life: [0.8, 1.2], size: sz, shape: { type: "sphere", r, th: 0.6 }, bursts: [[t, n]],
    vel: { rad, y: [0.1, 0.4], space: "world" }, drag: 1.4, rot: [0, TAU], rotZ: [-0.5, 0.5], alpha: [[0, 0], [0.2, 0.6], [1, 0]], sizeLife: [[0, 0.6], [1, 1.25]], flip: F44 }];
const aShards = (tag, pos, t, n, s, r = 0.4, sp = [3, 6]) => [
  { name: tag + " Shards", mat: "aShard" + s, mode: "stretch", vs: 0.08, ls: 1.3, fudge: 25, pos, life: [0.45, 0.75], size: [0.12, 0.24], shape: { type: "sphere", r, th: 0.5 },
    speed: sp, drag: 1, gravity: 1.3, bursts: [[t, n]], alpha: [[0, 1], [0.75, 1], [1, 0]] }];
const aFlames = (tag, pos, t, n, s, r = 0.5, sz = [0.7, 1.2], up = [0.8, 1.8], shape = null) => [
  { name: tag + " Flames", mat: "aFlame" + s, fudge: 0, pos, life: [0.35, 0.6], size: sz, rot: [0, TAU], rotZ: [-4, 4], shape: shape || { type: "sphere", r, th: 1 }, speed: [0.5, 2.5], drag: 4,
    vel: { y: up, space: "world" }, bursts: [[t, n]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 }];
// crisp ground line work (unit-radius meshes from MC_MESHES, size = radius). t0 appear, L = life
const aRings = (tag, R, pos, t0, L, s) => {
  const M = (n, mesh, size, rot, a, f, dt = 0) => ({ name: tag + " " + n, mat: "aLine" + s, mode: "mesh", mesh, fudge: f, pos: [pos[0], pos[1] + 0.05, pos[2]], life: L, size: R * size,
    rot3: [0, [0, TAU], 0], rotLife: [0, rot, 0], bursts: [[t0 + dt, 1]], sizeLife: [[0, 0.7, 0, 2], [0.18, 1], [1, 1.04]], alpha: [[0, 0], [0.08, a], [0.45, a * 0.6], [1, 0]] });
  return [M("Ring1", "mcRing1", 1, 0.5, 0.95, 50), M("Ring2", "mcRing2", 1, -0.8, 0.7, 51, 0.03), M("Ring3", "mcRing3", 1, 1.0, 0.75, 52, 0.06),
    { name: tag + " Sigil", mat: "aSigil" + s, mode: "mesh", mesh: "quad", fudge: 49, pos: [pos[0], pos[1] + 0.06, pos[2]], life: L, size: R * 2.0, rot3: [0, [0, TAU], 0], rotLife: [0, -0.6, 0],
      bursts: [[t0 + 0.02, 1]], sizeLife: [[0, 0.6, 0, 2], [0.2, 1], [1, 1.02]], alpha: [[0, 0], [0.1, 0.8], [0.55, 0.6], [1, 0]] }];
};
const DEMO_M = { bg: [0.10, 0.09, 0.11], floor: [0.09, 0.08, 0.09], grid: [0.06, 0.055, 0.065] };
