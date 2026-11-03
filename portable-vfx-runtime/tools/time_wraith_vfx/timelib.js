// ===== 시간의 망령 공용 (lib.js 다음에 붙음) — 청록(시간) · 금빛(모래·황동) · 남색(그림자) 재료와 메시 =====
// 텍스처는 흰~회색 위주 (gen_time.py), 색은 아래 tint. Soul* = 청록, Gold* = 금빛 (lib.js FX_MATS).
const TEALC = [0.55, 1, 0.98], DTEAL2 = [0.12, 0.55, 0.62], GOLDC = [1, 0.84, 0.45], SANDC = [1, 0.74, 0.32], INDC = [0.32, 0.28, 0.7];
const T_MATS = {
  sand: { tex: "T_Sand", tint: [2.4, 1.7, 0.7], add: true }, sandDim: { tex: "T_Sand", tint: [1.3, 0.95, 0.45], add: true },
  sandTeal: { tex: "T_Sand", tint: [0.8, 2.2, 2.3], add: true },
  hand: { tex: "T_ClockHand", tint: [2.4, 1.85, 0.85], add: true }, handTeal: { tex: "T_ClockHand", tint: [0.7, 2.3, 2.5], add: true },
  handInk: { tex: "T_ClockHand", tint: [0.09, 0.09, 0.16], add: false },
  gear: { tex: "T_Gear", tint: [2.0, 1.5, 0.65], add: true }, gearTeal: { tex: "T_Gear", tint: [0.6, 1.9, 2.1], add: true }, gearInk: { tex: "T_Gear", tint: [0.1, 0.1, 0.18], add: false },
  dial: { tex: "T_Dial", tint: [0.7, 2.1, 2.3], add: true }, dialGold: { tex: "T_Dial", tint: [2.3, 1.8, 0.8], add: true },
  hglass: { tex: "T_Hourglass", tint: [2.3, 1.8, 0.8], add: true }, hglassTeal: { tex: "T_Hourglass", tint: [0.7, 2.2, 2.4], add: true },
  sweep: { tex: "T_Sweep_Sheet", tint: [0.75, 2.4, 2.6], add: true }, sweepGold: { tex: "T_Sweep_Sheet", tint: [2.6, 2.0, 0.9], add: true },
  sweepInk: { tex: "T_Sweep_Sheet", tint: [0.1, 0.1, 0.2], add: false },
  ripple: { tex: "T_Ripple", tint: [0.6, 2.0, 2.2], add: true }, rippleGold: { tex: "T_Ripple", tint: [2.2, 1.7, 0.75], add: true },
  rippleGrey: { tex: "T_Ripple", tint: [1.3, 1.4, 1.7], add: true },
  flameTeal: { tex: "T_SoulFlame_Sheet", tint: [1.1, 1.7, 1.8], add: true },
  shard: { tex: "T_InkShard", tint: [1.2, 1.3, 1.4], add: false }, crack: { tex: "T_Crack", tint: [1.3, 1.4, 1.5], add: false },
  mist: { tex: "T_Mist_Sheet", tint: [1, 1, 1], add: false },
  shockInk: { tex: "T_Shock", tint: [0.08, 0.08, 0.16], add: false }, ringInk: { tex: "T_Ring", tint: [0.08, 0.08, 0.16], add: false },
  streakGold: { tex: "T_WhiteStreak", tint: [2.6, 2.0, 0.85], add: true }, pillarGold: { tex: "T_WhiteStreak", tint: [2.5, 1.95, 0.85], add: true },
  glowInd: { tex: "T_Glow", tint: [0.5, 0.45, 1.3], add: true }, starIce: { tex: "T_Star", tint: [1.8, 2.6, 2.8], add: true },
};
const ALL_MATS = Object.assign({}, FX_MATS, T_MATS);
function usedMats(systems) {
  const used = new Set();
  for (const s of systems) for (const L of s.layers) { used.add(L.mat); if (L.trail && L.trail.mat) used.add(L.trail.mat); }
  const out = {}; for (const k of used) { if (!ALL_MATS[k]) throw new Error("no material " + k); out[k] = ALL_MATS[k]; } return out;
}
const TIME_MESHES = Object.assign({}, MC_MESHES, {
  mcRing4: ringStrip(0.38, 0.012), mcTicks: merge(Array.from({ length: 60 }, (_, k) => { const a = k / 60 * TAU, L0 = k % 5 ? 0.9 : 0.84, w = k % 5 ? 0.006 : 0.014;
    return grid(1, 1, (u, v) => { const r = L0 + (0.97 - L0) * v, nx = Math.cos(a + Math.PI / 2) * w * (u * 2 - 1), nz = Math.sin(a + Math.PI / 2) * w * (u * 2 - 1); return [Math.cos(a) * r + nx, 0.03, Math.sin(a) * r + nz]; }); })),
  vRing: grid(96, 1, (u, v) => { const p = u * TAU, rr = 1 - 0.04 + v * 0.08; return [Math.cos(p) * rr, Math.sin(p) * rr, 0]; }),
  handV: grid(1, 1, (u, v) => [(u - 0.5) * 0.25, v, 0]),          // 세운 시곗바늘 (V 0 = 아래 꼬리 → 1 = 끝)
  handFlat: grid(1, 1, (u, v) => [(u - 0.5) * 0.25, 0.04, v]),    // 바닥에 누운 시곗바늘 (+Z 로)
});
// 청록 충격파 + 바로 뒤 남색 그림자 파동
const fxShockT = (tag, pos, t, size, life = 0.5, a = 0.6, mat = "shockSoul") => [
  { name: tag + " ShockT", mat, mode: "mesh", mesh: "quad", fudge: 30, pos: [pos[0], pos[1] + 0.06, pos[2]], life, size, rot3: [0, [0, TAU], 0], bursts: [[t, 1]],
    sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, a], [0.5, a * 0.6], [1, 0]] },
  { name: tag + " ShockInk", mat: "shockInk", mode: "mesh", mesh: "quad", fudge: 32, pos: [pos[0], pos[1] + 0.05, pos[2]], life: life * 1.3, size: size * 0.85, rot3: [0, [0, TAU], 0], bursts: [[t + 0.06, 1]],
    sizeLife: [[0, 0.1, 0, 2.5], [1, 1]], alpha: [[0, 0.6], [0.6, 0.4], [1, 0]] }];
const fxFlashT = (tag, pos, t, size = 2.5, mat = "starSoul", glow = "glowSoul") => [
  { name: tag + " Flash", mat, fudge: -8, pos, life: 0.18, size, rot: [0, 45 * D2R], bursts: [[t, 1]], sizeLife: [[0, 0.2], [0.25, 1.05], [1, 0]] },
  { name: tag + " FlashGlow", mat: glow, fudge: -2, pos, life: 0.28, size: size * 0.55, bursts: [[t, 1]], sizeLife: [[0, 0.4], [0.2, 1], [1, 1.1]], alpha: [[0, 0.35], [1, 0]] }];
// 모래: 흩날림 / 쏟아짐
const fxSand = (tag, pos, t, n, r = 1.5, sp = [2, 5], mat = "sand", up = [1, 3]) => [
  { name: tag + " Sand", mat, fudge: -4, pos, life: [0.5, 1.0], size: [0.05, 0.11], shape: { type: "sphere", r, th: 1 }, speed: sp, drag: 1.5, gravity: 1.2,
    vel: { y: up, space: "world" }, rot: [0, TAU], bursts: [[t, n]], alpha: [[0, 1], [0.75, 1], [1, 0]] }];
const fxSandFall = (tag, pos, t, n, r = 1.5, h = 6, life = 0.35) => [
  { name: tag + " SandFall", mat: "sand", mode: "stretch", vs: 0.06, ls: 2.2, fudge: -4, pos: [pos[0], pos[1] + h, pos[2]], life: [life * 0.9, life], size: [0.06, 0.12],
    shape: { type: "circle", r, th: 1, y: 0 }, vel: { y: [-h / life * 1.05, -h / life * 0.95], space: "world" }, bursts: [[t, n]], alpha: [[0, 0.9], [1, 1]] }];
// 톱니 조각이 돌며 튐 (금빛 / 청록)
const fxGears = (tag, pos, t, n, r = 1, mat = "gear", sp = [3, 6], sz = [0.3, 0.6]) => [
  { name: tag + " Gears", mat, fudge: 6, pos, life: [0.6, 1.0], size: sz, shape: { type: "sphere", r, th: 0.6 }, speed: sp, drag: 1.6, gravity: 0.8,
    rot: [0, TAU], rotZ: [-8, 8], bursts: [[t, n]], sizeLife: [[0, 0.5], [0.15, 1], [1, 0.8]], alpha: [[0, 1], [0.75, 1], [1, 0]] }];
// 바닥 시간 물결 (여러 겹 동심원)
const fxRipple = (tag, pos, t, size, life = 0.8, mat = "ripple", a = 0.75, grow = [0.2, 1]) => [
  { name: tag + " Ripple", mat, mode: "mesh", mesh: "quad", fudge: 28, pos: [pos[0], pos[1] + 0.07, pos[2]], life, size, rot3: [0, [0, TAU], 0], bursts: [[t, 1]],
    sizeLife: [[0, grow[0], 0, 2], [1, grow[1]]], alpha: [[0, 0], [0.1, a], [0.6, a * 0.6], [1, 0]] }];
// 문자판 바닥 원 (선 + 로마숫자 띠 + 눈금 + 문자판 문양), t0 에 그려지고 hold 동안 유지
function clockCircle(tag, R, pos, t0, hold, tone = "Soul", flareAt = null) {
  const out = magicCircle(tag, R, pos, t0, hold, tone, flareAt);
  out.push({ name: tag + " Ticks", mat: "line" + tone, mode: "mesh", mesh: "mcTicks", fudge: 51, pos: [pos[0], pos[1] + 0.05, pos[2]], life: hold, size: R, rotLife: [0, 0.12, 0],
    bursts: [[t0, 1]], sizeLife: [[0, 0.85, 0, 1.5], [Math.min(0.3, 1.2 / hold), 1], [1, 0.92]], alpha: [[0, 0], [Math.min(0.25, 0.6 / hold), 0.9], [0.85, 0.9], [1, 0]] });
  return out;
}
const fxIceMotes = (tag, pos, t, n, r = 2) => fxMotes(tag, pos, t, n, r, "dotSoul");
