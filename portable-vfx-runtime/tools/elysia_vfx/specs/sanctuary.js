// Elysia_Sanctuary — 치유의 성역(1페이즈 상시 힐 장판). static phased: begin → sustain(유지 시간) → end. P = 장판 중심(지면), 반경 R = 2 m.
// 원 회전: 8겹/14겹 대칭 메시를 LIFE 4 s 짜리 복사본 2개가 반씩 겹치며 이어 받는다(어떤 유지 시간이든 끊김 없음). sustainStart = LIFE/2 에서 각도가 정확히 맞는다.
const R = 2, LIFE = 4, H = LIFE / 2, W8 = (TAU / 8) / H, W14 = (TAU / 14) / H;
function vtxPillars(n, r, w, h) { const parts = []; for (let i = 0; i < n; i++) { const p = i * TAU / n, x = r * Math.cos(p), z = r * Math.sin(p);
  parts.push({ verts: [[x - w / 2, 0, z], [x + w / 2, 0, z], [x - w / 2, h, z], [x + w / 2, h, z]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] },
             { verts: [[x, 0, z - w / 2], [x, 0, z + w / 2], [x, h, z - w / 2], [x, h, z + w / 2]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }); }
  return merge(parts); }
var EXTRA_MESHES = {
  cOuter: merge([ringM(1, 0.035, 0.03), ringM(0.95, 0.014, 0.03), ticks(32, 0.96, 1.03, 0.012, 0.03)]),
  cStar: merge([polygram(8, 3, 0.93, 0.016, 0.035), vtxCircles(8, 0.93, 0.055, 0.014, 0.035), ringM(0.6, 0.022, 0.04), polygram(8, 2, 0.56, 0.012, 0.04, Math.PI / 8)]),
  cRune: runeArcs(14, 0.78, 0.13, 0.032),
  pillars: vtxPillars(8, 0.93, 0.14, 1.2),
  column: merge([{ verts: [[-0.5, 0, 0], [0.5, 0, 0], [-0.5, 1, 0], [0.5, 1, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] },
                 { verts: [[0, 0, -0.5], [0, 0, 0.5], [0, 1, -0.5], [0, 1, 0.5]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]),
};
// begin 쪽 원: 0..1.2 s 그려지고 2 s까지 유지, 2..4 s 선형으로 사라짐 (sustain 첫 복사본과 정확히 교대)
const held = (nm, mat, mesh, f, w, t0, a, sz) => ({ name: nm, mat, mode: "mesh", mesh, fudge: f, size: sz ?? R, rot3: [0, w * t0, 0], rotLife: [0, w, 0], bursts: [[t0, 1]], life: LIFE - t0,
  sizeLife: [[0, 0.85, 0, 1.2], [Math.min(0.9, 1.0 / (LIFE - t0)), 1]], alpha: [[0, 0], [1.0 / (LIFE - t0), a], [(H - t0) / (LIFE - t0), a], [1, 0]] });
const loopL = (nm, mat, mesh, f, w, a, sz) => ({ name: nm, mat, mode: "mesh", mesh, fudge: f, size: sz ?? R, rot3: [0, 0, 0], rotLife: [0, w, 0], rate: 1 / H, life: LIFE,
  alpha: [[0, 0], [0.5, a], [1, 0]], onStop: 0.6 });
const BEGIN = [
  held("B01 Outer", "line", "cOuter", 40, W8, 0, 1),
  held("B02 Star", "line", "cStar", 39, -W8, 0.12, 0.9),
  held("B03 Runes", "rune", "cRune", 38, W14, 0.25, 0.85),
  held("B04 Pillars", "pillar", "pillars", 10, -W8, 0.5, 0.7),
  { name: "B05 Floor", mat: "glow", mode: "mesh", mesh: "quad", fudge: 45, pos: [0, 0.02, 0], size: R * 2.6, rot3: YAW, bursts: [[0, 1]], life: LIFE,
    alpha: [[0, 0], [0.25, 0.45], [0.5, 0.45], [1, 0]] },
  { name: "B06 DrawSpark", mat: "dot", fudge: -6, life: [0.4, 0.7], size: [0.06, 0.12], shape: { type: "circle", r: R, th: 0, y: 0.05 }, bursts: [[0, 16], [0.2, 16], [0.4, 12]],
    vel: { y: [0.5, 1.5] }, color: [[0, PALE], [1, GOLD]], alpha: [[0, 1], [1, 0]] },
  { name: "B07 RiseBurst", mat: "streak", mode: "stretch", vs: 0.06, ls: 2.4, fudge: -3, life: [0.5, 0.8], size: [0.06, 0.12], shape: { type: "circle", r: R * 0.9, th: 1, y: 0.05 }, bursts: [[0.5, 24]],
    vel: { y: [5, 9] }, drag: 2.5, alpha: [[0, 1], [1, 0]] },
  { name: "B08 Bloom", mat: "flare", fudge: -4, pos: [0, 0.3, 0], size: 3.2, rot: [0, TAU], bursts: [[0.5, 1]], life: 0.5, sizeLife: [[0, 0.3], [0.3, 1], [1, 1.1]], alpha: [[0, 0.6], [1, 0]] },
  { name: "B09 Wave", mat: "wave", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.05, 0], size: R * 2.4, rot3: YAW, bursts: [[0.5, 1]], life: 0.6, sizeLife: [[0, 0.2, 0, 2.5], [1, 1]], alpha: [[0, 0.7], [1, 0]] },
];
const SUSTAIN = [
  loopL("S01 Outer", "line", "cOuter", 40, W8, 1),
  loopL("S02 Star", "line", "cStar", 39, -W8, 0.9),
  loopL("S03 Runes", "rune", "cRune", 38, W14, 0.85),
  loopL("S04 Pillars", "pillar", "pillars", 10, -W8, 0.7),
  { name: "S05 Floor", mat: "glow", mode: "mesh", mesh: "quad", fudge: 45, pos: [0, 0.02, 0], size: R * 2.6, rot3: YAW, rate: 1 / H, life: LIFE, alpha: [[0, 0], [0.5, 0.45], [1, 0]], onStop: 0.6 },
  { name: "S06 Column", mat: "pillar", mode: "mesh", mesh: "column", fudge: 12, size: 1.6, rot3: YAW, rotLife: [0, 0.4, 0], rate: 0.8, life: 2.5, bursts: [[0, 1]],
    sizeLife: [[0, 0.9], [1, 1.1]], alpha: [[0, 0], [0.5, 0.28], [1, 0]], onStop: 0.4 },
  { name: "S07 Motes", mat: "dot", fudge: -6, life: [1.6, 2.4], size: [0.04, 0.09], shape: { type: "circle", r: R * 0.95, th: 1, y: 0.1 }, rate: 22, bursts: [[0, 20]],
    vel: { y: [0.5, 1.2] }, noise: { s: 0.35, f: 0.8, sp: 0.4 }, color: [[0, PALE], [1, GOLD]], alpha: [[0, 0], [0.15, 1], [0.8, 0.8], [1, 0]], onStop: 0.3 },
  { name: "S08 Feathers", mat: "feather", fudge: -1, life: [2.2, 3.0], size: [0.2, 0.32], rot: [0, TAU], rotZ: [-1.2, 1.2], shape: { type: "circle", r: R * 0.85, th: 1, y: 2.4 }, rate: 2.2, bursts: [[0, 3]],
    vel: { y: [-0.6, -0.35] }, noise: { s: 0.5, f: 0.6, sp: 0.4 }, alpha: [[0, 0], [0.15, 0.9], [0.8, 0.8], [1, 0]], onStop: 0.4 },
  { name: "S09 Sparkle", mat: "star", fudge: -9, life: [0.35, 0.6], size: [0.14, 0.28], rot: [0, TAU], shape: { type: "circle", r: R * 0.95, th: 1, y: 0.2 }, rate: 6,
    sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: "S10 Dust", mat: "dust", fudge: 22, life: [2.0, 2.8], size: [0.9, 1.5], rot: [0, TAU], rotZ: [-0.3, 0.3], shape: { type: "circle", r: R * 0.8, th: 1, y: 0.25 }, rate: 2.5, bursts: [[0, 3]],
    vel: { y: [0.05, 0.2], orb: [0.15, 0.3] }, alpha: [[0, 0], [0.3, 0.4], [0.7, 0.4], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.6 },
  { name: "S11 RimSpark", mat: "streak", mode: "stretch", vs: 0.05, ls: 2, fudge: -3, life: [0.6, 0.9], size: [0.04, 0.08], shape: { type: "circle", r: R, th: 0, y: 0.05 }, rate: 7,
    vel: { y: [2, 3.5] }, drag: 1.2, alpha: [[0, 1], [1, 0]] },
  // 거짓 성역의 흔적: 아주 옅은 검은 실오라기가 가끔 바닥에서 피어오름
  { name: "S12 HiddenShade", mat: "mist", fudge: 26, life: [1.6, 2.2], size: [0.4, 0.7], rot: [0, TAU], shape: { type: "circle", r: R * 0.6, th: 1, y: 0.1 }, rate: 1.2,
    vel: { y: [0.3, 0.5] }, sizeLife: [[0, 0.5], [1, 1.3]], alpha: [[0, 0], [0.3, 0.35], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.4 },
];
const END = [
  { name: "E01 Flare", mat: "line", mode: "mesh", mesh: "cOuter", fudge: 34, size: R, rot3: YAW, bursts: [[0, 1]], life: 0.7, sizeLife: [[0, 1], [1, 0.6]], alpha: [[0, 1], [1, 0]] },
  { name: "E02 StarShrink", mat: "line", mode: "mesh", mesh: "cStar", fudge: 33, size: R, rot3: YAW, rotLife: [0, -2, 0], bursts: [[0, 1]], life: 0.6, sizeLife: [[0, 1], [1, 0.3]], alpha: [[0, 0.9], [1, 0]] },
  { name: "E03 LastMotes", mat: "dot", fudge: -6, life: [0.9, 1.4], size: [0.05, 0.1], shape: { type: "circle", r: R * 0.9, th: 1, y: 0.1 }, bursts: [[0, 40]],
    vel: { y: [1.5, 3] }, drag: 0.8, color: [[0, PALE], [1, AMBER]], alpha: [[0, 1], [1, 0]] },
  { name: "E04 Up", mat: "streak", mode: "stretch", vs: 0.06, ls: 2.2, fudge: -3, life: [0.4, 0.6], size: [0.05, 0.1], shape: { type: "circle", r: R * 0.9, th: 1, y: 0.05 }, bursts: [[0, 18]],
    vel: { y: [5, 8] }, drag: 2.5, alpha: [[0, 1], [1, 0]] },
];
const MATS = {
  line: { tex: "T_Line", tint: [2.5, 2.0, 1.05], add: true }, rune: { tex: "T_Rune", tint: [2.3, 1.85, 0.9], add: true },
  pillar: { tex: "T_PillarHoly", tint: [1.6, 1.4, 1.0], add: true }, glow: { tex: "T_Glow", tint: [1.2, 0.95, 0.45], add: true },
  dot: { tex: "T_Dot", tint: [2.3, 2.0, 1.2], add: true }, streak: { tex: "T_Streak", tint: [2.4, 2.0, 1.1], add: true }, flare: { tex: "T_Flare", tint: [2.0, 1.7, 1.0], add: true },
  wave: { tex: "T_Wave", tint: [1.7, 1.4, 0.7], add: true }, star: { tex: "T_Star", tint: [2.4, 2.2, 1.5], add: true }, feather: { tex: "T_Feather", tint: [1.9, 1.75, 1.3], add: true },
  dust: { tex: "T_HolyDust_Sheet", tint: [1.3, 1.15, 0.9], add: true }, mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false },
};
const SCENE = {
  name: "Elysia_Sanctuary", bloom: BLOOM_HOLY, tonemapping: "neutral",
  sequence: { sustainStart: H, hold: { default: 6, min: 1, max: 60 } },
  scale: { input: "areaRadius", reference: R, min: 0.5, max: 3 },
  systems: [
    { id: "begin", role: "static", phase: "begin", loop: false, duration: 0.6, layers: BEGIN },
    { id: "sustain", role: "static", phase: "sustain", loop: true, duration: 8, layers: SUSTAIN },
    { id: "end", role: "static", phase: "end", loop: false, duration: 0.3, layers: END },
  ],
  demo: Object.assign({}, DEMO_BG, { endTail: 1.8,
    scenarios: { center: { label: "기본", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0],
      targets: [[0.6, 0, 0.3, 200]], camera: { target: [0, 1.0, 0], dist: 9, yaw: -90, pitch: 28 } } } }),
};
