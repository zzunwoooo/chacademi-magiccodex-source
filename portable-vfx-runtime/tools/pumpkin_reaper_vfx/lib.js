// ===== Pumpkin Reaper shared spec library (prepended to every pack spec) =====
// Unity coords: +Y up, +Z = boss facing (static basis from the server), +X = boss right.
const TAU = Math.PI * 2;
const WH=[1,1,1], ORA=[1,0.55,0.12], YEL=[1,0.85,0.4], CRIM=[0.75,0.12,0.08], VIO=[0.62,0.3,1], DVIO=[0.3,0.1,0.55], TEAL=[0.3,1,0.82], DTEAL=[0.1,0.5,0.55], RED=[1,0.18,0.1], PINK=[1,0.5,0.85];
const drnd = (a, b) => a + Math.random() * (b - a);
function grid(nu, nv, f) {
  const verts = [], uvs = [], tris = [];
  for (let j = 0; j <= nv; j++) for (let i = 0; i <= nu; i++) { const u = i / nu, v = j / nv; verts.push(f(u, v)); uvs.push([u, v]); }
  for (let j = 0; j < nv; j++) for (let i = 0; i < nu; i++) { const k = j * (nu + 1) + i, n = k + nu + 1; tris.push(k, n, k + 1, k + 1, n, n + 1); }
  return { verts, uvs, tris };
}
function merge(parts) {
  const out = { verts: [], uvs: [], tris: [] };
  for (const p of parts) { const o = out.verts.length; out.verts.push(...p.verts); out.uvs.push(...p.uvs); out.tris.push(...p.tris.map(i => i + o)); }
  return out;
}
// ground sector (fan) centred on +Z: U along the arc, V 0 = centre → 1 = rim. size = radius
const sector = (spanDeg, y = 0.02) => grid(64, 6, (u, v) => { const a = (-0.5 + u) * spanDeg * Math.PI / 180; return [Math.sin(a) * v, y, Math.cos(a) * v]; });
// ground rectangle from the origin forward along +Z: U across (width w, relative), V 0 = start → 1 = far end. size = length
const strip = (w, y = 0.02) => grid(2, 12, (u, v) => [(u - 0.5) * w, y, v]);
// thin ring strip on the ground (radius r, width w), U around
const ringStrip = (r, w, y = 0.02, seg = 128) => grid(seg, 1, (u, v) => { const p = -u * TAU, rr = r - w / 2 + v * w; return [Math.cos(p) * rr, y, Math.sin(p) * rr]; });
// n identical arc segments each mapping U 0..1 (rune bands)
const runeBand = (r, w, n, y = 0.025) => merge(Array.from({ length: n }, (_, k) => grid(24, 1, (u, v) => {
  const p = -(k + u) / n * TAU, rr = r - w / 2 + v * w; return [Math.cos(p) * rr, y, Math.sin(p) * rr]; })));
// polygram {n/k}: straight strips between vertices on radius r
function polygram(n, k, r, w, y = 0.03) {
  const P = i => { const a = i / n * TAU; return [Math.cos(a) * r, Math.sin(a) * r]; }, parts = [];
  for (let i = 0; i < n; i++) { const a = P(i), b = P((i + k) % n), dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz), nx = -dz / L * w / 2, nz = dx / L * w / 2;
    parts.push(grid(1, 1, (u, v) => [a[0] + dx * u + nx * (v * 2 - 1), y, a[1] + dz * u + nz * (v * 2 - 1)])); }
  return merge(parts);
}
// horizontal crescent arc (blade sweep) at height y: from angle a0 to a1 (deg, 0 = +Z, + = right/+X). U along the sweep (head = U 1), V 0 inner → 1 outer edge.
const arc = (a0, a1, inner, y, tilt = 0) => grid(80, 1, (u, v) => {
  const a = (a0 + (a1 - a0) * u) * Math.PI / 180, prof = Math.pow(Math.sin(Math.PI * Math.min(1, u * 1.08)), 0.6), r = 1 - (1 - inner) * prof * (1 - v);
  return [Math.sin(a) * r, y + Math.sin(a) * tilt * r * 0.25, Math.cos(a) * r]; });
// vertical crossed quad pillar: height 1, width w (V 0 bottom → 1 top)
const pillar = w => merge([grid(1, 1, (u, v) => [(u - 0.5) * w, v, 0]), grid(1, 1, (u, v) => [0, v, (u - 0.5) * w])]);

// ---------- warning builders (all warnings red; t0 = start, T = seconds until the hit) ----------
const RAMPUP = T => [[0, 0], [Math.min(0.12, 0.15 / (T + 0.3)), 0.85], [0.9, 1], [1, 0]];
function warnCircle(tag, R, t0, T, pos = [0, 0, 0], fade = 0.25) {
  const L = T + fade, k = T / L, Y = [0, [0, TAU], 0];
  return [
    { name: tag + " WarnFill", mat: "warnFill", mode: "mesh", mesh: "quad", fudge: 60, pos: [pos[0], pos[1] + 0.03, pos[2]], life: L, size: 2 * R, rot3: Y, bursts: [[t0, 1]],
      sizeLife: [[0, 0.85, 0, 2], [Math.min(0.15, k * 0.3), 1], [1, 1]], alpha: [[0, 0], [0.08, 0.8], [k * 0.98, 1], [Math.min(0.999, k + 0.04), 1], [1, 0]] },
    { name: tag + " WarnGrow", mat: "warnGrow", mode: "mesh", mesh: "quad", fudge: 58, pos: [pos[0], pos[1] + 0.035, pos[2]], life: T, size: 2 * R, rot3: Y, bursts: [[t0, 1]],
      sizeLife: [[0, 0.02], [1, 1]], alpha: [[0, 0], [0.1, 0.9], [0.95, 1], [1, 0]] },
    { name: tag + " WarnRim", mat: "warnRim", mode: "mesh", mesh: "quad", fudge: 56, pos: [pos[0], pos[1] + 0.04, pos[2]], life: L, size: 2 * R, rot3: Y, rotLife: [0, 0.35, 0], bursts: [[t0, 1]],
      sizeLife: [[0, 1.08, 0, -1], [Math.min(0.12, k * 0.3), 1], [1, 1]], alpha: [[0, 0], [0.05, 0.9], [k * 0.98, 1], [Math.min(0.999, k + 0.02), 1], [1, 0]] },
    { name: tag + " WarnFlash", mat: "warnRim", mode: "mesh", mesh: "quad", fudge: 55, pos: [pos[0], pos[1] + 0.045, pos[2]], life: 0.3, size: 2 * R, rot3: Y, bursts: [[t0 + T - 0.04, 1]],
      sizeLife: [[0, 1], [1, 1.06]], alpha: [[0, 1], [1, 0]] },
  ];
}
// sector / strip warnings use the band texture on a mesh (mesh name given)
function warnMesh(tag, mesh, size, t0, T, pos = [0, 0, 0], rotY = 0, fade = 0.25) {
  const L = T + fade, k = T / L, rr = [0, rotY, 0];
  return [
    { name: tag + " WarnZone", mat: "warnBand", mode: "mesh", mesh, fudge: 60, pos, life: L, size, rot3: rr, bursts: [[t0, 1]],
      sizeLife: [[0, 0.9, 0, 1.5], [Math.min(0.15, k * 0.3), 1], [1, 1]], alpha: [[0, 0], [0.06, 0.75], [k * 0.98, 1], [Math.min(0.999, k + 0.04), 1], [1, 0]] },
    { name: tag + " WarnGrow", mat: "warnBandHot", mode: "mesh", mesh, fudge: 58, pos: [pos[0], pos[1] + 0.005, pos[2]], life: T, size, rot3: rr, bursts: [[t0, 1]],
      sizeLife: [[0, 0.03], [1, 1]], alpha: [[0, 0], [0.1, 0.7], [0.95, 0.9], [1, 0]] },
    { name: tag + " WarnFlash", mat: "warnBandHot", mode: "mesh", mesh, fudge: 55, pos: [pos[0], pos[1] + 0.01, pos[2]], life: 0.28, size, rot3: rr, bursts: [[t0 + T - 0.04, 1]],
      alpha: [[0, 1], [1, 0]] },
  ];
}
const WARN_MATS = {
  warnFill: { tex: "T_WarnFill", tint: [1.05, 0.28, 0.24], add: true },
  warnGrow: { tex: "T_WarnGrow", tint: [1.9, 0.45, 0.35], add: true },
  warnRim: { tex: "T_WarnRim", tint: [2.2, 0.6, 0.45], add: true },
  warnBand: { tex: "T_WarnBand", tint: [1.1, 0.3, 0.26], add: true },
  warnBandHot: { tex: "T_WarnBand", tint: [2.2, 0.55, 0.42], add: true },
};
const BLOOM = { threshold: 0.95, intensity: 1.45, scatter: 0.7, tint: [1, 0.9, 1] };
const DEMO_BG = { bg: [0.10, 0.08, 0.12], floor: [0.08, 0.07, 0.09], grid: [0.06, 0.05, 0.07] };

// ---------- reusable FX building blocks (pos = local point, t = burst time) ----------
const F44 = { x: 4, y: 4 };
const fxFlash = (tag, pos, t, size = 3, mat = "starOra") => [
  { name: tag + " Flash", mat, fudge: -8, pos, life: 0.2, size, rot: [0, 45 * D2R], bursts: [[t, 1]], sizeLife: [[0, 0.2], [0.25, 1.1], [1, 0]] },
  { name: tag + " FlashGlow", mat: mat === "starSoul" ? "glowSoul" : mat === "starPink" ? "glowPink" : "glowVio", fudge: -2, pos, life: 0.3, size: size * 0.8, bursts: [[t, 1]], sizeLife: [[0, 0.4], [0.2, 1], [1, 1.1]], alpha: [[0, 0.5], [1, 0]] }];
const fxFlames = (tag, pos, t, n, r = 0.5, mat = "flame", sz = [0.9, 1.5], up = [0.8, 1.8]) => [
  { name: tag + " Flames", mat, fudge: 0, pos, life: [0.4, 0.7], size: sz, rot: [0, TAU], rotZ: [-4, 4], shape: { type: "sphere", r, th: 1 }, speed: [1, 3.5], drag: 4,
    vel: { y: up, space: "world" }, bursts: [[t, n]], sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 }];
const fxSparks = (tag, pos, t, n, sp = [5, 11], mat = "streakOra", r = 0.4) => [
  { name: tag + " Sparks", mat, mode: "stretch", vs: 0.07, ls: 1.6, fudge: -5, pos, life: [0.3, 0.6], size: [0.05, 0.1], shape: { type: "sphere", r, th: 1 },
    speed: sp, drag: 3, gravity: 0.7, bursts: [[t, n]], alpha: [[0, 1], [0.7, 1], [1, 0]] }];
const fxRays = (tag, pos, t, n, sp = [9, 15], mat = "streakOra", ls = 4.5) => [
  { name: tag + " Rays", mat, mode: "stretch", vs: 0.05, ls, fudge: -6, pos, life: [0.22, 0.34], size: [0.12, 0.24], shape: { type: "sphere", r: 0.5, th: 1 },
    speed: sp, drag: 6, bursts: [[t, n]], alpha: [[0, 0.9], [1, 0]] }];
const BIGRING = { shockOra: "ringOra", shockVio: "ringVio", shockGold: "ringGold", shockSoul: "ringSoul", shockPink: "ringPink" };
const fxShock = (tag, pos, t, size, mat0 = "shockOra", life = 0.5, mat = size > 15 ? BIGRING[mat0] : mat0) => [
  { name: tag + " Shock", mat, mode: "mesh", mesh: "quad", fudge: 30, pos: [pos[0], pos[1] + 0.06, pos[2]], life, size, rot3: [0, [0, TAU], 0], bursts: [[t, 1]],
    sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.75], [0.5, 0.5], [1, 0]] },
  { name: tag + " Shock2", mat: mat === "shockOra" ? "shockVio" : mat === "ringOra" ? "ringVio" : mat, mode: "mesh", mesh: "quad", fudge: 31, pos: [pos[0], pos[1] + 0.07, pos[2]], life: life * 1.3, size: size * 0.8, rot3: [0, [0, TAU], 0], bursts: [[t + 0.08, 1]],
    sizeLife: [[0, 0.1, 0, 2.5], [1, 1]], alpha: [[0, 0.6], [0.5, 0.4], [1, 0]] }];
const fxShards = (tag, pos, t, n, r = 0.5, sp = [3, 7]) => [
  { name: tag + " Shards", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.4, fudge: 25, pos, life: [0.45, 0.8], size: [0.14, 0.28], shape: { type: "sphere", r, th: 0.5 },
    speed: sp, drag: 1, gravity: 1.3, bursts: [[t, n]], alpha: [[0, 1], [0.75, 1], [1, 0]] }];
const fxBats = (tag, pos, t, n, r = 1) => [
  { name: tag + " Bats", mat: "bat", fudge: 20, pos, life: [0.8, 1.3], size: [0.35, 0.6], shape: { type: "sphere", r, th: 0.6 }, speed: [3, 6], drag: 1.5,
    vel: { y: [1.2, 2.6], space: "world" }, noise: { s: 1.5, f: 2.5, sp: 1.2 }, bursts: [[t, n]],
    sizeLife: [[0, 0.5], [0.15, 1], [0.35, 0.7], [0.5, 1], [0.7, 0.7], [0.85, 1], [1, 0.4]], alpha: [[0, 0], [0.1, 1], [0.8, 1], [1, 0]] }];
const fxMotes = (tag, pos, t, n, r = 2, mat = "dotSoul", life = [1.0, 1.8]) => [
  { name: tag + " Motes", mat, fudge: -7, pos, life, size: [0.05, 0.12], shape: { type: "circle", r, th: 0.8, y: 0 }, bursts: [[t, n]],
    vel: { y: [0.6, 1.6], space: "world" }, noise: { s: 0.6, f: 1, sp: 0.6 }, alpha: [[0, 0], [0.1, 1], [0.7, 0.8], [1, 0]], sizeLife: [[0, 1], [1, 0.3]] }];
const fxEmbers = (tag, pos, t, n, r = 2) => [
  { name: tag + " Embers", mat: "dotFire", fudge: -6, pos, life: [0.8, 1.6], size: [0.04, 0.09], shape: { type: "sphere", r, th: 0.6 }, bursts: [[t, n]],
    speed: [0.5, 2.5], drag: 1.5, gravity: -0.1, noise: { s: 1, f: 1, sp: 0.7 }, color: [[0, YEL], [0.5, ORA], [1, CRIM]], alpha: [[0, 0], [0.05, 1], [0.7, 0.9], [1, 0]] }];
const fxMist = (tag, pos, t, n, r = 1.5, sz = [1.4, 2.4], rad = [0.6, 1.4]) => [
  { name: tag + " Mist", mat: "mist", fudge: 35, pos, life: [0.9, 1.4], size: sz, shape: { type: "sphere", r, th: 0.6 }, bursts: [[t, n]],
    vel: { rad, y: [0.05, 0.3], space: "world" }, drag: 1.4, rot: [0, TAU], rotZ: [-0.5, 0.5], alpha: [[0, 0], [0.2, 0.55], [1, 0]], sizeLife: [[0, 0.6], [1, 1.25]], flip: F44 }];
const fxCrack = (tag, pos, t, size, life = 1.4) => [
  { name: tag + " Crack", mat: "crack", mode: "mesh", mesh: "quad", fudge: 44, pos: [pos[0], pos[1] + 0.05, pos[2]], life, size, rot3: [0, [0, TAU], 0], bursts: [[t, 1]],
    sizeLife: [[0, 0.5, 0, 3], [0.2, 1]], alpha: [[0, 0], [0.06, 1], [0.6, 0.8], [1, 0]] }];
const fxPillars = (tag, pos, t, n, r, h = 3, mat = "pillarOra") => [
  { name: tag + " Pillars", mat, mode: "mesh", mesh: "pillar", fudge: 5, pos, life: [0.35, 0.55], size: h, shape: { type: "circle", r, th: 0.3, y: 0 }, rot3: [0, [0, TAU], 0],
    bursts: [[t, n]], sizeLife: [[0, 0.2, 0, 4], [0.3, 1], [1, 1.05]], alpha: [[0, 0], [0.1, 1], [0.5, 0.7], [1, 0]] }];
// held magic circle from line work (unit radius meshes, size = radius). t0 = appear, hold = seconds held, end = fade length
function magicCircle(tag, R, pos, t0, hold, tone = "Vio", flareAt = null) {
  const L = hold, M = (n, mesh, size, w, rot, a = 0.9, f = 50) => ({ name: tag + " " + n, mat: "line" + tone, mode: "mesh", mesh, fudge: f, pos: [pos[0], pos[1] + 0.05, pos[2]], life: L, size: R * size,
      rot3: [0, 0, 0], rotLife: [0, rot, 0], bursts: [[t0, 1]], sizeLife: [[0, 0.82, 0, 1.5], [Math.min(0.3, 1.2 / L), 1], [0.92, 1], [1, 0.9]],
      alpha: [[0, 0], [Math.min(0.25, w / L), a], [0.85, a], [1, 0]] });
  const out = [
    M("Ring1", "mcRing1", 1, 0.3, 0.25), M("Ring2", "mcRing2", 1, 0.5, -0.35), M("Ring3", "mcRing3", 1, 0.7, 0.5),
    M("Octagram", "mcOcta", 1, 0.6, 0.25, 0.8), M("Star", "mcStar", 1, 0.8, -0.6, 0.7),
    Object.assign(M("RunesOut", "mcRunesOut", 1, 0.5, 0.25, 0.95), { mat: "rune" + tone }), Object.assign(M("RunesIn", "mcRunesIn", 1, 0.7, -0.35, 0.85), { mat: "rune" + tone }),
    { name: tag + " Sigil", mat: "sigil" + tone, mode: "mesh", mesh: "quad", fudge: 49, pos: [pos[0], pos[1] + 0.06, pos[2]], life: L, size: R * 1.08, rotLife: [0, -0.2, 0], bursts: [[t0 + 0.2, 1]],
      sizeLife: [[0, 0.6, 0, 2], [Math.min(0.3, 1 / L), 1], [1, 0.95]], alpha: [[0, 0], [Math.min(0.3, 0.8 / L), 0.75], [0.85, 0.75], [1, 0]] },
  ];
  if (flareAt != null) out.push({ name: tag + " SigilFlare", mat: "sigil" + tone, mode: "mesh", mesh: "quad", fudge: 48, pos: [pos[0], pos[1] + 0.07, pos[2]], life: 0.45, size: R * 1.1,
    rot3: [0, -0.2 * (flareAt - t0 - 0.2), 0], rotLife: [0, -0.2, 0], bursts: [[flareAt, 1]], sizeLife: [[0, 1], [1, 1.25]], alpha: [[0, 1], [1, 0]] });
  return out;
}
const MC_MESHES = { mcRing1: ringStrip(1, 0.02), mcRing2: ringStrip(0.9, 0.012), mcRing3: ringStrip(0.62, 0.016), mcOcta: polygram(8, 3, 0.9, 0.012), mcStar: polygram(8, 2, 0.62, 0.01),
  mcRunesOut: runeBand(0.78, 0.13, 16), mcRunesIn: runeBand(0.5, 0.1, 10), pillar: pillar(0.35) };
const FX_MATS = Object.assign({}, WARN_MATS, {
  slash: { tex: "T_ScytheSlash_Sheet", tint: [2.6, 1.6, 1.2], add: true }, slashHot: { tex: "T_ScytheSlash_Sheet", tint: [3.0, 2.2, 1.6], add: true },
  slashViolet: { tex: "T_ScytheSlash_Sheet", tint: [1.1, 0.6, 2.2], add: true }, slashSoul: { tex: "T_ScytheSlash_Sheet", tint: [0.6, 2.2, 1.9], add: true },
  slashInk: { tex: "T_ScytheSlash_Sheet", tint: [0.22, 0.08, 0.3], add: false },
  crack: { tex: "T_Crack", tint: [1.6, 1.1, 0.9], add: false }, mist: { tex: "T_Mist_Sheet", tint: [1, 1, 1], add: false },
  flame: { tex: "T_PumpkinFlame_Sheet", tint: [2.0, 1.4, 1.1], add: true }, soulFlame: { tex: "T_SoulFlame_Sheet", tint: [1.4, 2.2, 2.0], add: true }, flameVio: { tex: "T_VioletFlame_Sheet", tint: [1.8, 1.3, 2.2], add: true },
  streakOra: { tex: "T_FireStreak", tint: [2.6, 1.8, 1.2], add: true }, streakSoul: { tex: "T_SoulStreak", tint: [1.4, 2.6, 2.4], add: true },
  streakVio: { tex: "T_SoulStreak", tint: [1.8, 0.9, 2.8], add: true }, streakPink: { tex: "T_SoulStreak", tint: [2.8, 1.2, 2.2], add: true },
  starOra: { tex: "T_Star", tint: [2.8, 1.9, 1.2], add: true }, starSoul: { tex: "T_Star", tint: [1.4, 2.8, 2.5], add: true }, starPink: { tex: "T_Star", tint: [2.8, 1.5, 2.4], add: true },
  starVio: { tex: "T_Star", tint: [2.0, 1.3, 2.9], add: true }, starGold: { tex: "T_Star", tint: [2.9, 2.4, 1.2], add: true },
  glowVio: { tex: "T_Glow", tint: [0.9, 0.4, 1.6], add: true }, glowSoul: { tex: "T_Glow", tint: [0.3, 1.3, 1.1], add: true }, glowPink: { tex: "T_Glow", tint: [1.5, 0.5, 1.1], add: true },
  glowOra: { tex: "T_Glow", tint: [1.6, 0.7, 0.2], add: true }, glowGold: { tex: "T_Glow", tint: [1.7, 1.3, 0.4], add: true },
  shard: { tex: "T_InkShard", tint: [1.6, 1.2, 1.2], add: false }, bat: { tex: "T_Bat", tint: [1.4, 1.2, 1.6], add: false },
  dotSoul: { tex: "T_Dot", tint: [0.8, 2.4, 2.0], add: true }, dotFire: { tex: "T_Dot", tint: [2.4, 1.5, 0.7], add: true }, dotVio: { tex: "T_Dot", tint: [1.6, 0.9, 2.6], add: true },
  dotGold: { tex: "T_Dot", tint: [2.6, 2.1, 0.9], add: true }, dotPink: { tex: "T_Dot", tint: [2.6, 1.2, 2.0], add: true },
  shockOra: { tex: "T_Shock", tint: [2.0, 0.85, 0.35], add: true }, shockVio: { tex: "T_Shock", tint: [1.3, 0.6, 2.3], add: true }, shockSoul: { tex: "T_Shock", tint: [0.5, 2.0, 1.8], add: true },
  shockPink: { tex: "T_Shock", tint: [2.4, 0.9, 1.8], add: true }, shockGold: { tex: "T_Shock", tint: [2.4, 1.9, 0.8], add: true },
  ringVio: { tex: "T_Ring", tint: [1.4, 0.7, 2.4], add: true }, ringOra: { tex: "T_Ring", tint: [2.4, 1.2, 0.5], add: true }, ringPink: { tex: "T_Ring", tint: [2.4, 0.9, 1.8], add: true }, ringGold: { tex: "T_Ring", tint: [2.4, 1.9, 0.9], add: true }, ringSoul: { tex: "T_Ring", tint: [0.6, 2.2, 1.9], add: true },
  pillarOra: { tex: "T_FireStreak", tint: [2.4, 1.5, 0.9], add: true }, pillarSoul: { tex: "T_SoulStreak", tint: [1.0, 2.4, 2.1], add: true }, pillarGold: { tex: "T_FireStreak", tint: [2.6, 2.2, 1.2], add: true },
  lineVio: { tex: "T_Line", tint: [1.5, 0.7, 2.6], add: true }, lineSoul: { tex: "T_Line", tint: [0.6, 2.2, 1.9], add: true }, lineOra: { tex: "T_Line", tint: [2.4, 1.3, 0.6], add: true }, lineGold: { tex: "T_Line", tint: [2.5, 2.0, 0.9], add: true },
  runeVio: { tex: "T_RuneStrip", tint: [1.4, 0.7, 2.4], add: true }, runeSoul: { tex: "T_RuneStrip", tint: [0.6, 2.1, 1.8], add: true }, runeOra: { tex: "T_RuneStrip", tint: [2.3, 1.2, 0.5], add: true }, runeGold: { tex: "T_RuneStrip", tint: [2.4, 1.9, 0.8], add: true },
  sigilVio: { tex: "T_Sigil", tint: [1.4, 0.7, 2.4], add: true }, sigilSoul: { tex: "T_Sigil", tint: [0.6, 2.1, 1.8], add: true }, sigilOra: { tex: "T_Sigil", tint: [2.3, 1.2, 0.5], add: true }, sigilGold: { tex: "T_Sigil", tint: [2.4, 1.9, 0.8], add: true },
  ghost: { tex: "T_Ghost", tint: [1.2, 2.0, 1.8], add: true }, bone: { tex: "T_BoneClaw", tint: [1.3, 1.3, 1.3], add: false }, chain: { tex: "T_Chain", tint: [1.2, 2.2, 2.0], add: true },
  chainRed: { tex: "T_Chain", tint: [2.4, 0.6, 0.5], add: true }, candy: { tex: "T_Candy", tint: [1.2, 1.2, 1.2], add: false }, hex: { tex: "T_Hex", tint: [1.3, 0.8, 2.0], add: true },
  warnShare: { tex: "T_WarnShare", tint: [2.0, 0.55, 0.45], add: true },
});
const cam = (target, dist, yaw, pitch) => ({ target, dist, yaw, pitch });
const sc = (label, o) => Object.assign({ label, from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0], hits: [] }, o);
