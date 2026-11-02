// ===== 재의 그림자 그룹 B 공용 (lib.js 다음에 붙음) — 재·잿불·장치 빛 재료와 메시 =====
const ASHC = [0.55, 0.53, 0.52], EMB = [1, 0.48, 0.14], DEMB = [0.62, 0.14, 0.06], GLD = [1, 0.9, 0.6];
const ASH_MATS = {
  flake: { tex: "T_AshFlake", tint: [1.15, 1.1, 1.05], add: false }, flakeHot: { tex: "T_AshFlake", tint: [2.4, 1.5, 0.9], add: true },
  hexGold: { tex: "T_HexGold", tint: [1.6, 1.35, 0.8], add: true },
  fissure: { tex: "T_Fissure", tint: [2.6, 1.5, 0.8], add: true }, fissureHot: { tex: "T_Fissure", tint: [3.2, 2.3, 1.5], add: true },
  fissureGold: { tex: "T_Fissure", tint: [2.6, 2.3, 1.5], add: true },
  scorch: { tex: "T_Scorch", tint: [1, 1, 1], add: false },
  candle: { tex: "T_CandleFlame", tint: [2.4, 1.6, 1.0], add: true }, candleGold: { tex: "T_CandleFlame", tint: [2.5, 2.2, 1.4], add: true },
  beamGold: { tex: "T_GoldStreak", tint: [2.2, 2.0, 1.5], add: true }, beamEmber: { tex: "T_FireStreak", tint: [2.8, 1.5, 0.7], add: true },
  shardHot: { tex: "T_InkShard", tint: [2.4, 1.6, 1.2], add: false },
  glowEmber: { tex: "T_Glow", tint: [1.5, 0.55, 0.15], add: true }, glowPale: { tex: "T_Glow", tint: [1.4, 1.2, 0.75], add: true },
  sparkGold: { tex: "T_GoldStreak", tint: [2.4, 2.2, 1.6], add: true },
};
const MATS = Object.assign({}, FX_MATS, ASH_MATS);
// n radial strips from radius r0 to 1 (unit, size = radius), width w (unit), first at angle a0 deg (0 = +Z). V 0 = inner → 1 = outer
function radialStrips(n, r0, w, a0 = 0, y = 0.03) {
  return merge(Array.from({ length: n }, (_, k) => { const a = (a0 + k * 360 / n) * Math.PI / 180, dx = Math.sin(a), dz = Math.cos(a), nx = dz, nz = -dx;
    return grid(1, 8, (u, v) => { const r = r0 + (1 - r0) * v; return [dx * r + nx * (u - 0.5) * w, y, dz * r + nz * (u - 0.5) * w]; }); }));
}
// flakes of ash drifting up over a disc
const fxFlakes = (tag, pos, t, n, r = 2, life = [1.2, 2.2], up = [0.5, 1.6], sz = [0.08, 0.18], mat = "flake") => [
  { name: tag + " Flakes", mat, fudge: 12, pos, life, size: sz, shape: { type: "circle", r, th: 1, y: 0 }, bursts: [[t, n]], rot: [0, TAU], rotZ: [-3, 3],
    vel: { y: up, space: "world" }, noise: { s: 0.8, f: 1.2, sp: 0.6 }, alpha: [[0, 0], [0.1, 1], [0.75, 0.9], [1, 0]], sizeLife: [[0, 0.6], [0.2, 1], [1, 0.7]], flip: { x: 2, y: 2 } }];
const fxAshBurst = (tag, pos, t, n, sp = [3, 7]) => [
  { name: tag + " AshBurst", mat: "flake", fudge: 13, pos, life: [0.6, 1.2], size: [0.1, 0.22], shape: { type: "sphere", r: 0.4, th: 1 }, speed: sp, drag: 2.2, gravity: 0.35,
    rot: [0, TAU], rotZ: [-6, 6], bursts: [[t, n]], alpha: [[0, 1], [0.7, 1], [1, 0]], flip: { x: 2, y: 2 } }];
Object.assign(MATS, {
  shockEmber: { tex: "T_Shock", tint: [1.9, 0.62, 0.2], add: true }, shockAsh: { tex: "T_Shock", tint: [0.1, 0.08, 0.08], add: false },
  shockGoldA: { tex: "T_Shock", tint: [1.9, 1.6, 0.8], add: true }, ringEmber: { tex: "T_Ring", tint: [1.9, 0.7, 0.25], add: true },
});
// 잿불 충격파: 주황 파동 + 바로 뒤 검은 재 파동 (보라·베이지 돔 없음)
const fxShockE = (tag, pos, t, size, life = 0.5, a = 0.6) => [
  { name: tag + " ShockE", mat: "shockEmber", mode: "mesh", mesh: "quad", fudge: 30, pos: [pos[0], pos[1] + 0.06, pos[2]], life, size, rot3: [0, [0, TAU], 0], bursts: [[t, 1]],
    sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, a], [0.5, a * 0.6], [1, 0]] },
  { name: tag + " ShockAsh", mat: "shockAsh", mode: "mesh", mesh: "quad", fudge: 32, pos: [pos[0], pos[1] + 0.05, pos[2]], life: life * 1.3, size: size * 0.85, rot3: [0, [0, TAU], 0], bursts: [[t + 0.06, 1]],
    sizeLife: [[0, 0.1, 0, 2.5], [1, 1]], alpha: [[0, 0.75], [0.6, 0.5], [1, 0]] }];
const fxFlashE = (tag, pos, t, size = 2.5, mat = "starOra", glow = "glowEmber") => [
  { name: tag + " Flash", mat, fudge: -8, pos, life: 0.18, size, rot: [0, 45 * D2R], bursts: [[t, 1]], sizeLife: [[0, 0.2], [0.25, 1.05], [1, 0]] },
  { name: tag + " FlashGlow", mat: glow, fudge: -2, pos, life: 0.28, size: size * 0.6, bursts: [[t, 1]], sizeLife: [[0, 0.4], [0.2, 1], [1, 1.1]], alpha: [[0, 0.4], [1, 0]] }];
Object.assign(MATS, { pillarPale: { tex: "T_GoldStreak", tint: [2.0, 1.8, 1.3], add: true } });
