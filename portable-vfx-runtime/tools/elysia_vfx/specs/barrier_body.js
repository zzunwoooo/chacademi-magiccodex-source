// 방벽 2종 (같은 구조, 색·역할만 다름)
//  holy = Elysia_LightBarrier: 축복의 제안 — 엘리시아를 감싸는 빛의 성벽. static phased, P = 보스 발밑, 반경 3.2 m. end = 성벽이 깨짐(빛의 핵 3개 성공 또는 시간 종료 시 모드가 end 재생).
//  hell = Belzar_DebtShield: 빌린 힘의 반환 — 벨자르에게 씌워지는 검붉은 보호막. attached phased(보스를 따라감), 반경 2.6 m.
const HB_ = KIND === "hell";
const RAD = HB_ ? 2.5 : 3.6, CY = HB_ ? 1.9 : 0, LIFE = 4, HALF = LIFE / 2, W12 = (TAU / 12) / HALF, W8 = (TAU / 8) / HALF;
var EXTRA_MESHES = {
  dome: HB_ ? spherePanels(12, 4) : domePanels(12, 4),
  base: merge([ringM(1, 0.03, 0.03), ringM(0.96, 0.012, 0.03), ticks(48, 1.0, 1.06, 0.01, 0.03), polygram(8, 3, 0.9, 0.012, 0.035)]),
  baseRune: runeArcs(12, 0.82, 0.1, 0.03),
  band: cylPanels(12, 4, 0.22),
  latLines: merge([0.25, 0.5, 0.75].map(k => { const el = k * Math.PI / 2; return ringM(Math.cos(el), 0.012, Math.sin(el), 96); })),
};
const M = HB_ ? { line: "hLine", hex: "hHex", rune: "hRune", glow: "hGlow", dot: "hDot", streak: "hStreak", flare: "hFlare", wave: "hWave", shard: "shardDark", shardL: "hShard" }
             : { line: "line", hex: "hex", rune: "rune", glow: "glow", dot: "dot", streak: "streak", flare: "flare", wave: "wave", shard: "shardLight", shardL: "shardLight" };
const HOT = HB_ ? EMB : PALE, MID = HB_ ? CRIM : GOLD;
const lp = (nm, mat, mesh, f, w, a, sz, pos) => ({ name: nm, mat, mode: "mesh", mesh, fudge: f, size: sz ?? RAD, pos: pos ?? [0, 0, 0], rot3: [0, 0, 0], rotLife: [0, w, 0], rate: 1 / HALF, life: LIFE,
  alpha: [[0, 0], [0.5, a], [1, 0]], onStop: 0.3 });
const hb = (nm, mat, mesh, f, w, t0, a, sz) => ({ name: nm, mat, mode: "mesh", mesh, fudge: f, size: sz ?? RAD, rot3: [0, w * t0, 0], rotLife: [0, w, 0], bursts: [[t0, 1]], life: LIFE - t0,
  sizeLife: [[0, 0.3, 0, 2.5], [Math.min(0.9, 0.7 / (LIFE - t0)), 1]], alpha: [[0, 0], [0.5 / (LIFE - t0), Math.min(1, a * 1.3)], [0.9 / (LIFE - t0), a], [(HALF - t0) / (LIFE - t0), a], [1, 0]] });
const BEGIN = [
  hb("B01 Base", M.line, "base", 40, W8, 0, 1),
  hb("B02 BaseRune", M.rune, "baseRune", 39, -W12, 0.1, 0.85),
  Object.assign(hb("B03 Dome", M.hex, "dome", 6, W12, 0.25, 0.7), { pos: [0, CY, 0] }),
  Object.assign(hb("B04 Lat", M.line, "latLines", 5, -W12, 0.35, 0.55), { pos: [0, CY, 0] }),
  hb("B05 Band", M.hex, "band", 7, -W12, 0.2, 0.5, RAD),
  { name: "B06 Rise", mat: M.streak, mode: "stretch", vs: 0.06, ls: 2.4, fudge: -3, life: [0.4, 0.6], size: [0.06, 0.12], shape: { type: "circle", r: RAD, th: 0, y: 0.05 }, bursts: [[0.1, 30]],
    vel: { y: [6, 10] }, drag: 2, alpha: [[0, 1], [1, 0]] },
  { name: "B07 Flash", mat: M.flare, fudge: -6, pos: [0, RAD * 0.55, 0], size: RAD * 1.6, rot: [0, TAU], bursts: [[0.35, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.55], [1, 0]] },
  { name: "B08 Wave", mat: M.wave, mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], size: RAD * 2.8, rot3: YAW, bursts: [[0.3, 1]], life: 0.5, sizeLife: [[0, 0.3, 0, 2], [1, 1]], alpha: [[0, 0.7], [1, 0]] },
];
const SUSTAIN = [
  lp("S01 Base", M.line, "base", 40, W8, 1),
  lp("S02 BaseRune", M.rune, "baseRune", 39, -W12, 0.85),
  lp("S03 Dome", M.hex, "dome", 6, W12, 0.7, RAD, [0, CY, 0]),
  lp("S04 Lat", M.line, "latLines", 5, -W12, 0.55, RAD, [0, CY, 0]),
  lp("S05 Band", M.hex, "band", 7, -W12, 0.5),
  { name: "S06 Shimmer", mat: M.hex, mode: "mesh", mesh: "dome", fudge: 4, pos: [0, CY, 0], size: RAD * 1.005, rot3: YAW, rotLife: [0, [0.3, 0.6], 0], rate: 0.9, life: 1.4,
    sizeLife: [[0, 0.98], [1, 1.01]], alpha: [[0, 0], [0.4, 0.25], [1, 0]], onStop: 0.3 },
  { name: "S07 SurfaceSpark", mat: M.dot, fudge: -6, pos: [0, CY, 0], life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: "sphere", r: RAD, th: 0 }, rate: 30,
    vel: { y: [0.3, 0.8] }, color: [[0, HOT], [1, MID]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "S08 RimRise", mat: M.streak, mode: "stretch", vs: 0.05, ls: 2, fudge: -3, life: [0.6, 0.9], size: [0.04, 0.08], shape: { type: "circle", r: RAD, th: 0, y: 0.05 }, rate: 12,
    vel: { y: [2.5, 4] }, drag: 1, alpha: [[0, 1], [1, 0]] },
  { name: "S09 Floor", mat: M.glow, mode: "mesh", mesh: "quad", fudge: 45, pos: [0, 0.02, 0], size: RAD * 2.4, rot3: YAW, rate: 1 / HALF, life: LIFE, alpha: [[0, 0], [0.5, 0.35], [1, 0]], onStop: 0.3 },
];
if (HB_) SUSTAIN.push(
  { name: "S10 Embers", mat: "hDot", fudge: -7, life: [1.2, 1.8], size: [0.04, 0.07], shape: { type: "circle", r: RAD * 0.9, th: 1, y: 0.2 }, rate: 16,
    vel: { y: [0.8, 1.6] }, noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, EMB], [1, DEEP]], alpha: [[0, 0], [0.1, 1], [1, 0]], onStop: 0.3 },
  { name: "S11 Mist", mat: "mist", fudge: 26, life: [1.6, 2.2], size: [1.0, 1.6], rot: [0, TAU], shape: { type: "circle", r: RAD * 0.8, th: 1, y: 0.3 }, rate: 3, bursts: [[0, 3]],
    vel: { orb: [0.3, 0.5], y: [0.1, 0.3] }, alpha: [[0, 0], [0.3, 0.45], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.4 });
else SUSTAIN.push(
  { name: "S10 Feathers", mat: "feather", fudge: -1, life: [2.2, 3], size: [0.18, 0.28], rot: [0, TAU], rotZ: [-1, 1], shape: { type: "sphere", r: RAD * 0.95, th: 0 }, rate: 2.2,
    vel: { y: [-0.4, -0.2] }, noise: { s: 0.4, f: 0.6, sp: 0.3 }, alpha: [[0, 0], [0.2, 0.8], [0.8, 0.8], [1, 0]], onStop: 0.4 });
const END = [
  { name: "E01 DomeShatter", mat: M.hex, mode: "mesh", mesh: "dome", fudge: 6, pos: [0, CY, 0], size: RAD, rot3: YAW, bursts: [[0, 1]], life: 0.35, sizeLife: [[0, 1], [1, 1.25]], alpha: [[0, 1], [1, 0]] },
  { name: "E02 Flash", mat: M.flare, fudge: -6, pos: [0, RAD * 0.5, 0], size: RAD * 2, rot: [0, TAU], bursts: [[0, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.1]], alpha: [[0, 0.7], [1, 0]] },
  { name: "E03 Panels", pos: [0, CY, 0], mat: M.shardL, fudge: -4, life: [0.6, 1.0], size: [0.3, 0.6], rot: [0, TAU], rotZ: [-8, 8], shape: { type: "sphere", r: RAD, th: 0 }, bursts: [[0, 36]],
    vel: { rad: [2, 4] }, gravity: 0.9, drag: 0.6, alpha: [[0, 1], [0.7, 0.9], [1, 0]] },
  { name: "E04 DarkPanels", pos: [0, CY, 0], mat: M.shard, fudge: 18, life: [0.6, 1.0], size: [0.25, 0.45], rot: [0, TAU], rotZ: [-8, 8], shape: { type: "sphere", r: RAD, th: 0 }, bursts: [[0.02, 16]],
    vel: { rad: [1.5, 3] }, gravity: 1.1, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "E05 Wave", mat: M.wave, mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], size: RAD * 3.6, rot3: YAW, bursts: [[0, 1], [0.1, 1]], life: 0.55, sizeLife: [[0, 0.3, 0, 2], [1, 1]], alpha: [[0, 0.75], [1, 0]] },
  { name: "E06 Sparks", pos: [0, CY, 0], mat: M.dot, mode: "stretch", vs: 0.05, ls: 1.3, fudge: -6, life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: "sphere", r: RAD, th: 0 }, bursts: [[0, 50]],
    vel: { rad: [3, 6] }, drag: 2, gravity: 0.5, color: [[0, HOT], [1, MID]], alpha: [[0, 1], [1, 0]] },
  { name: "E07 BaseFade", mat: M.line, mode: "mesh", mesh: "base", fudge: 40, size: RAD, rot3: YAW, bursts: [[0, 1]], life: 0.8, sizeLife: [[0, 1], [1, 1.15]], alpha: [[0, 1], [1, 0]] },
];
const MATS = {
  line: { tex: "T_Line", tint: [2.5, 2.0, 1.0], add: true }, hex: { tex: "T_Hex", tint: [2.0, 1.6, 0.75], add: true }, rune: { tex: "T_Rune", tint: [2.2, 1.8, 0.85], add: true },
  glow: { tex: "T_Glow", tint: [1.1, 0.85, 0.35], add: true }, dot: { tex: "T_Dot", tint: [2.3, 2.0, 1.2], add: true }, streak: { tex: "T_Streak", tint: [2.3, 1.9, 1.0], add: true },
  flare: { tex: "T_Flare", tint: [2.0, 1.7, 0.9], add: true }, wave: { tex: "T_Wave", tint: [1.7, 1.4, 0.7], add: true }, shardLight: { tex: "T_ShardLight", tint: [2.0, 1.7, 0.8], add: true },
  feather: { tex: "T_Feather", tint: [1.8, 1.65, 1.2], add: true },
  hLine: { tex: "T_Line", tint: [2.4, 0.85, 0.35], add: true }, hHex: { tex: "T_Hex", tint: [1.5, 0.55, 0.22], add: true }, hRune: { tex: "T_Rune", tint: [2.2, 0.7, 0.3], add: true },
  hGlow: { tex: "T_Glow", tint: [1.1, 0.3, 0.12], add: true }, hDot: { tex: "T_Dot", tint: [2.4, 1.1, 0.55], add: true }, hStreak: { tex: "T_Streak", tint: [2.3, 0.9, 0.4], add: true },
  hFlare: { tex: "T_Flare", tint: [2.2, 0.85, 0.4], add: true }, hWave: { tex: "T_Wave", tint: [1.7, 0.5, 0.22], add: true }, hShard: { tex: "T_ShardLight", tint: [2.2, 1.0, 0.4], add: true },
  shardDark: { tex: "T_ShardDark", tint: [2.0, 0.9, 0.4], add: false }, mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false },
};
const ROLE = HB_ ? "attached" : "static";
const SCENE = {
  name: HB_ ? "Belzar_DebtShield" : "Elysia_LightBarrier", bloom: HB_ ? BLOOM_HELL : BLOOM_HOLY, tonemapping: "neutral",
  sequence: { sustainStart: HALF, hold: { default: 5, min: 1, max: 60 } },
  systems: [
    { id: "begin", role: ROLE, phase: "begin", loop: false, duration: 0.6, layers: BEGIN },
    { id: "sustain", role: ROLE, phase: "sustain", loop: true, duration: 8, layers: SUSTAIN },
    { id: "end", role: ROLE, phase: "end", loop: false, duration: 0.2, layers: END },
  ],
  demo: Object.assign({}, DEMO_BG, { endTail: 1.6, dummy: { height: HB_ ? 3.8 : 1.8 },
    scenarios: HB_ ? { boss: { label: "보스에 씌움", path: [[0, [0, 0, 0], 0], [4, [0, 0, 0], 0], [7, [0, 0, 3], 0]], camera: { follow: true, target: [0, 1.8, 0], dist: 12, yaw: -60, pitch: 18 } } }
                   : { center: { label: "기본", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0], targets: [[0, 0, 0, 180], [5, 0, 2, 250]],
                         camera: { target: [0, 1.8, 0], dist: 14, yaw: -90, pitch: 20 } } } }),
};
