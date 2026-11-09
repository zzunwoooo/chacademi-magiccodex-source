// Elysia_Judgment — 심판의 빛(1페이즈, 성) / 지옥불(2페이즈 그림자 몰수). static, P = 낙하 지점(지면).
// 0~0.35 s: 하늘에서 빛이 모이고 바닥 마법진이 그려짐(예비) → 0.35 s 낙하(판정 시점) → ~1.9 s 잔광.
// 빨간 예고 장판은 모드의 예고 시스템이 그린다(여기 없음). 반경 R = 2.5 m, runtime areaRadius 로 크기 조절.
const R = 2.5, T0 = 0.35;
function pillarM(w, h) { return merge([
  { verts: [[-w / 2, 0, 0], [w / 2, 0, 0], [-w / 2, h, 0], [w / 2, h, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] },
  { verts: [[0, 0, -w / 2], [0, 0, w / 2], [0, h, -w / 2], [0, h, w / 2]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]); }
var EXTRA_MESHES = {
  cOuter: merge([ringM(1, 0.03, 0.03), ringM(0.95, 0.012, 0.03)]),
  cStar: merge([polygram(8, 3, 0.93, 0.014, 0.035), vtxCircles(8, 0.93, 0.05, 0.012, 0.035)]),
  cInner: merge([ringM(0.62, 0.02, 0.04), ringM(0.57, 0.01, 0.04), polygram(8, 2, 0.57, 0.012, 0.04, Math.PI / 8)]),
  cRune: runeArcs(14, 0.79, 0.13, 0.032),
  cTicks: ticks(48, 0.96, 1.04, 0.01, 0.03),
  beam: pillarM(1, 14), beamCore: pillarM(0.4, 14),
  sweep: crescentXZ(Math.PI * 0.9, 0.08),
};
function judgment(hell) {
  const c1 = hell ? "hLine" : "line", rn = hell ? "hRune" : "rune", sg = hell ? "hSigil" : "sigil", pl = hell ? "hPillar" : "pillar",
        gl = hell ? "hGlow" : "glow", st = hell ? "hStar" : "star", wv = hell ? "hWave" : "wave", sk = hell ? "hStreak" : "streak",
        dt = hell ? "hDot" : "dot", fl = hell ? "hFlare" : "flare", sl = hell ? "hSlash" : "slash";
  const HOT = hell ? EMB : PALE, MID = hell ? CRIM : GOLD, LOW = hell ? DEEP : AMBER;
  const E = T0 + 1.55;  // 원이 사라지는 시각
  const circ = (nm, mesh, f, w, t0, a1) => ({ name: nm, mat: c1, mode: "mesh", mesh, fudge: f, size: R, rot3: YAW, rotLife: [0, w, 0], bursts: [[t0, 1]], life: E - t0,
    sizeLife: [[0, 0.82, 0, 1.5], [0.18, 1]], alpha: [[0, 0], [(T0 - t0) / (E - t0) * 0.6, a1 * 0.7], [(T0 - t0) / (E - t0), a1 * 0.7], [(T0 + 0.05 - t0) / (E - t0), 1], [0.7, 0.75], [1, 0]] });
  const L = [
    // --- 바닥 마법진 (그려지며 회전, 낙하 순간 번쩍) ---
    circ("C01 OuterRing", "cOuter", 40, 0.35, 0.0, 1),
    circ("C02 Ticks", "cTicks", 39, -0.2, 0.03, 0.8),
    circ("C03 Octagram", "cStar", 38, -0.55, 0.06, 0.9),
    circ("C04 InnerRing", "cInner", 37, 0.8, 0.1, 0.9),
    { name: "C05 RuneBand", mat: rn, mode: "mesh", mesh: "cRune", fudge: 36, size: R, rot3: YAW, rotLife: [0, 0.5, 0], bursts: [[0.04, 1]], life: E - 0.04,
      alpha: [[0, 0], [0.18, 0.7], [0.2, 1], [0.7, 0.7], [1, 0]], sizeLife: [[0, 0.9], [0.2, 1]] },
    { name: "C06 Sigil", mat: sg, mode: "mesh", mesh: "quad", fudge: 35, pos: [0, 0.05, 0], size: R * 1.12, rot3: YAW, rotLife: [0, -0.3, 0], bursts: [[0.08, 1]], life: E - 0.08,
      alpha: [[0, 0], [0.17, 0.75], [0.19, 1], [0.65, 0.7], [1, 0]], sizeLife: [[0, 0.6, 0, 2], [0.17, 1], [0.2, 1.06], [1, 0.96]] },
    { name: "C07 CircleFlare", mat: c1, mode: "mesh", mesh: "cOuter", fudge: 34, size: R, rot3: YAW, bursts: [[T0, 1]], life: 0.5,
      sizeLife: [[0, 1], [1, 1.25]], alpha: [[0, 1], [1, 0]] },
    { name: "C08 FloorGlow", mat: gl, mode: "mesh", mesh: "quad", fudge: 44, pos: [0, 0.02, 0], size: R * 2.4, rot3: YAW, bursts: [[0, 1]], life: E,
      alpha: [[0, 0], [T0 / E, 0.35], [(T0 + 0.05) / E, 0.6], [0.6, 0.25], [1, 0]] },
    // --- 예비: 하늘에서 내려와 모이는 빛줄기 + 가는 예비 광선 ---
    { name: "A01 Gather", mat: sk, mode: "stretch", vs: 0.06, ls: 2.5, fudge: 5, life: [0.25, 0.32], size: [0.08, 0.16], shape: { type: "circle", r: 1.8, th: 1, y: 9 },
      bursts: [[0, 6], [0.08, 8], [0.16, 10]], vel: { y: [-28, -22], rad: [-4, -2] }, alpha: [[0, 0], [0.2, 1], [1, 0.6]] },
    { name: "A02 PreBeam", mat: pl, mode: "mesh", mesh: "beamCore", fudge: 8, size: 0.6, rot3: YAW, bursts: [[0.08, 1]], life: T0 - 0.06,
      alpha: [[0, 0], [0.5, 0.35], [1, 0.6]], sizeLife: [[0, 0.3], [1, 1]] },
    { name: "A03 SkyGlow", mat: gl, fudge: 9, pos: [0, 9, 0], size: 4, bursts: [[0, 1]], life: T0 + 0.4, alpha: [[0, 0], [0.6, 0.45], [1, 0]], sizeLife: [[0, 0.5], [0.6, 1], [1, 1.4]] },
    // --- 낙하: 기둥, 섬광, 충격파 ---
    { name: "B01 Beam", mat: pl, mode: "mesh", mesh: "beam", fudge: 6, size: 1, rot3: YAW, rotLife: [0, 3, 0], bursts: [[T0, 1]], life: 0.75,
      sizeLife: [[0, 2.4, 0, -6], [0.25, 1.15], [1, 0.45]], alpha: [[0, 1], [0.6, 0.85], [1, 0]] },
    { name: "B02 BeamCore", mat: pl, mode: "mesh", mesh: "beamCore", fudge: 4, size: 1, rot3: YAW, rotLife: [0, -4, 0], bursts: [[T0, 1]], life: 0.55,
      sizeLife: [[0, 1.6], [0.2, 1], [1, 0.3]], alpha: [[0, 1], [1, 0]] },
    { name: "B03 Flash", mat: st, fudge: -8, pos: [0, 0.6, 0], size: 4.5, rot: [0, TAU], bursts: [[T0, 1]], life: 0.22, sizeLife: [[0, 0.3], [0.25, 1.1], [1, 0]] },
    { name: "B04 FlareBurst", mat: fl, fudge: -6, pos: [0, 0.8, 0], size: 5.5, rot: [0, TAU], rotZ: [-0.6, 0.6], bursts: [[T0, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.2]], alpha: [[0, 0.85], [1, 0]] },
    { name: "B05 CoreGlow", mat: gl, fudge: 0, pos: [0, 0.7, 0], size: 2.6, bursts: [[T0, 1]], life: 0.45, sizeLife: [[0, 0.5], [0.2, 1], [1, 1.1]], alpha: [[0, 0.6], [1, 0]] },
    { name: "B06 Wave1", mat: wv, mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], size: R * 2.6, rot3: YAW, bursts: [[T0, 1]], life: 0.45, sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.85], [0.6, 0.6], [1, 0]] },
    { name: "B07 Wave2", mat: wv, mode: "mesh", mesh: "quad", fudge: 31, pos: [0, 0.08, 0], size: R * 2.1, rot3: YAW, bursts: [[T0 + 0.09, 1]], life: 0.45, sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.6], [1, 0]] },
    { name: "B08 RingSharp", mat: c1, mode: "mesh", mesh: "cOuter", fudge: 29, size: R, rot3: YAW, bursts: [[T0 + 0.03, 1]], life: 0.35, sizeLife: [[0, 0.2, 0, 3.5], [1, 1.35]], alpha: [[0, 1], [1, 0]] },
    { name: "B09 GroundSweep", mat: sl, mode: "mesh", mesh: "sweep", fudge: 3, size: [R * 0.8, R], rot3: YAW, rotLife: [0, [7, 10], 0], bursts: [[T0, 2], [T0 + 0.08, 1]], life: [0.35, 0.45], flip: { x: 2, y: 8 }, pos: [0, 0.15, 0] },
    { name: "B10 Rays", mat: sk, mode: "stretch", vs: 0.08, ls: 4, fudge: -3, pos: [0, 0.4, 0], life: [0.3, 0.5], size: [0.12, 0.22], bursts: [[T0, 18]],
      shape: { type: "circle", r: 0.3, th: 1, y: 0 }, vel: { rad: [10, 16], y: [0.5, 3] }, drag: 5, alpha: [[0, 1], [1, 0]] },
    { name: "B11 UpRays", mat: sk, mode: "stretch", vs: 0.06, ls: 3, fudge: -2, life: [0.4, 0.6], size: [0.06, 0.14], bursts: [[T0, 14], [T0 + 0.1, 10]],
      shape: { type: "circle", r: R * 0.7, th: 1, y: 0.1 }, vel: { y: [8, 14] }, drag: 2.5, alpha: [[0, 1], [1, 0]] },
    { name: "B12 Sparks", mat: dt, mode: "stretch", vs: 0.05, ls: 1.2, fudge: -5, pos: [0, 0.3, 0], life: [0.5, 0.9], size: [0.05, 0.1], bursts: [[T0, 40]],
      shape: { type: "sphere", r: 0.3, th: 1 }, speed: [4, 10], drag: 2.2, gravity: 0.6, color: [[0, HOT], [0.5, MID], [1, LOW]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
    { name: "B13 DarkShards", mat: "shardDark", fudge: 20, pos: [0, 0.4, 0], life: [0.5, 0.8], size: [0.18, 0.34], rot: [0, TAU], rotZ: [-10, 10], bursts: [[T0, 10]],
      shape: { type: "sphere", r: 0.3, th: 1 }, speed: [4, 8], drag: 1.2, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  ];
  if (hell) L.push(
    { name: "H01 Flames", mat: "hFlame", fudge: 2, life: [0.5, 0.8], size: [0.8, 1.4], rot: [0, TAU], shape: { type: "circle", r: R * 0.8, th: 1, y: 0.3 }, bursts: [[T0, 22], [T0 + 0.12, 12]],
      vel: { y: [1.5, 3] }, sizeLife: [[0, 0.5, 0, 3], [1, 1.1]], flip: { x: 4, y: 4 } },
    { name: "H02 FlameColumn", mat: "hFlame", fudge: 1, life: [0.4, 0.6], size: [1.0, 1.6], rot: [0, TAU], shape: { type: "circle", r: 0.4, th: 1, y: 0.4 }, bursts: [[T0, 14]],
      vel: { y: [6, 10] }, drag: 2, sizeLife: [[0, 0.6], [1, 1.2]], flip: { x: 4, y: 4 } },
    { name: "H03 Mist", mat: "mist", fudge: 25, life: [0.9, 1.3], size: [1.4, 2.2], rot: [0, TAU], shape: { type: "circle", r: R * 0.7, th: 1, y: 0.4 }, bursts: [[T0 + 0.05, 10]],
      vel: { rad: [0.8, 1.4], y: [0.3, 0.7] }, drag: 1.5, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
    { name: "H04 Crack", mat: "hCrack", mode: "mesh", mesh: "quad", fudge: 43, pos: [0, 0.025, 0], size: R * 1.9, rot3: YAW, bursts: [[T0, 1]], life: 1.6,
      sizeLife: [[0, 0.4, 0, 4], [0.12, 1]], alpha: [[0, 1], [0.5, 0.8], [1, 0]] },
    { name: "H05 Embers", mat: "hDot", fudge: -6, life: [1.0, 1.8], size: [0.04, 0.08], shape: { type: "circle", r: R * 0.8, th: 1, y: 0.2 }, bursts: [[T0 + 0.05, 40]],
      vel: { y: [1, 2.5] }, noise: { s: 0.8, f: 1, sp: 0.6 }, color: [[0, EMB], [0.6, CRIM], [1, DEEP]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] });
  else L.push(
    { name: "L01 Feathers", mat: "feather", fudge: -1, life: [0.9, 1.4], size: [0.25, 0.42], rot: [0, TAU], rotZ: [-3, 3], shape: { type: "sphere", r: 0.4, th: 1, y: 0.8 }, bursts: [[T0 + 0.02, 14]],
      speed: [3, 6], drag: 3, gravity: 0.12, noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.08, 1], [0.7, 0.8], [1, 0]] },
    { name: "L02 Dust", mat: "dust", fudge: 22, life: [0.7, 1.1], size: [1.0, 1.7], rot: [0, TAU], shape: { type: "circle", r: R * 0.6, th: 1, y: 0.3 }, bursts: [[T0 + 0.03, 10]],
      vel: { rad: [1.5, 2.5], y: [0.2, 0.6] }, drag: 2, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.15, 0.6], [1, 0]], flip: { x: 4, y: 4 } },
    { name: "L03 Motes", mat: "dot", fudge: -6, life: [1.0, 1.8], size: [0.04, 0.09], shape: { type: "circle", r: R * 0.8, th: 1, y: 0.1 }, bursts: [[T0 + 0.05, 36]],
      vel: { y: [0.8, 2] }, noise: { s: 0.5, f: 1, sp: 0.5 }, color: [[0, PALE], [1, AMBER]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] },
    { name: "L04 Sparkle", mat: "star", fudge: -9, life: [0.3, 0.55], size: [0.18, 0.36], rot: [0, TAU], shape: { type: "circle", r: R * 0.9, th: 1, y: 0.2 }, bursts: [[T0 + 0.1, 10], [T0 + 0.35, 8]],
      sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] });
  return L;
}
const MATS = {
  line: { tex: "T_Line", tint: [2.6, 2.1, 1.1], add: true }, rune: { tex: "T_Rune", tint: [2.4, 1.9, 0.9], add: true }, sigil: { tex: "T_SigilHoly", tint: [2.3, 1.8, 0.85], add: true },
  pillar: { tex: "T_PillarHoly", tint: [2.2, 2.0, 1.6], add: true }, glow: { tex: "T_Glow", tint: [1.4, 1.0, 0.4], add: true }, star: { tex: "T_Star", tint: [2.6, 2.3, 1.6], add: true },
  wave: { tex: "T_Wave", tint: [1.9, 1.5, 0.7], add: true }, streak: { tex: "T_Streak", tint: [2.6, 2.1, 1.2], add: true }, dot: { tex: "T_Dot", tint: [2.4, 2.0, 1.2], add: true },
  flare: { tex: "T_Flare", tint: [2.2, 1.8, 1.0], add: true }, slash: { tex: "T_SlashHoly_Sheet", tint: [1.9, 1.7, 1.3], add: true },
  feather: { tex: "T_Feather", tint: [2.0, 1.8, 1.3], add: true }, dust: { tex: "T_HolyDust_Sheet", tint: [1.4, 1.2, 0.9], add: true },
  shardDark: { tex: "T_ShardDark", tint: [1.6, 1.3, 0.8], add: false },
  hLine: { tex: "T_Line", tint: [2.6, 0.7, 0.35], add: true }, hRune: { tex: "T_Rune", tint: [2.4, 0.6, 0.3], add: true }, hSigil: { tex: "T_SigilHell", tint: [2.4, 0.55, 0.28], add: true },
  hPillar: { tex: "T_PillarHell", tint: [2.2, 1.6, 1.3], add: true }, hGlow: { tex: "T_Glow", tint: [1.4, 0.3, 0.12], add: true }, hStar: { tex: "T_Star", tint: [2.6, 1.4, 0.8], add: true },
  hWave: { tex: "T_Wave", tint: [1.9, 0.5, 0.25], add: true }, hStreak: { tex: "T_Streak", tint: [2.6, 1.1, 0.5], add: true }, hDot: { tex: "T_Dot", tint: [2.4, 1.2, 0.6], add: true },
  hFlare: { tex: "T_Flare", tint: [2.3, 0.9, 0.4], add: true }, hSlash: { tex: "T_SlashHell_Sheet", tint: [1.9, 1.5, 1.3], add: true },
  hFlame: { tex: "T_HellFlame_Sheet", tint: [1.7, 1.2, 1.0], add: true }, mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false },
  hCrack: { tex: "T_Crack", tint: [2.4, 0.6, 0.25], add: true },
};
const HELL = (typeof KIND !== "undefined" && KIND === "hell");
const SCENE = {
  name: HELL ? "Belzar_Hellfire" : "Elysia_Judgment", bloom: HELL ? BLOOM_HELL : BLOOM_HOLY, tonemapping: "neutral",
  scale: { input: "areaRadius", reference: R, min: 0.5, max: 3 },
  systems: [{ id: HELL ? "hellfire" : "judgment", role: "static", loop: false, duration: 0.8, layers: judgment(HELL) }],
  demo: Object.assign({}, DEMO_BG, { interval: 3.2,
    scenarios: { center: { label: HELL ? "지옥불" : "심판의 빛", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0],
      targets: [[1.2, 0, 0.6, 200]], camera: { target: [0, 1.6, 0], dist: 11, yaw: -90, pitch: 24 } } } }),
};
