// Elysia_Hymn — 찬송 석주(계시의 제안). static 2종, P = 석주 꼭대기 종 보석(높이 약 2.8 m)에 모드가 놓음.
//  ring  : 맞게 쳤을 때 (1.3 s) — 음표가 솟고 고리가 퍼진다
//  wrong : 틀렸을 때 (0.9 s) — 붉게 일그러진 음표가 깨져 떨어짐 (전멸기 자체는 별도)
var EXTRA_MESHES = { ringV: ribbon(64, s => { const p = s * TAU, d = [Math.cos(p), Math.sin(p), 0]; return [mul(d, 0.95), mul(d, 1.05)]; }),
  ringH: merge([ringM(1, 0.04, 0), ringM(0.9, 0.012, 0), ticks(16, 0.92, 1.08, 0.015, 0)]) };
const RING = [
  { name: "N01 Flash", mat: "star", fudge: -8, size: 1.4, rot: [0, TAU], bursts: [[0, 1]], life: 0.2, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  { name: "N02 Glow", mat: "glow", fudge: 0, size: 1.6, bursts: [[0, 1]], life: 0.6, sizeLife: [[0, 0.5], [0.2, 1], [1, 0.8]], alpha: [[0, 0.6], [1, 0]] },
  { name: "N03 RingH1", mat: "line", mode: "mesh", mesh: "ringH", fudge: -2, size: 1, rot3: YAW, rotLife: [0, 2, 0], bursts: [[0, 1]], life: 0.7, sizeLife: [[0, 0.3, 0, 2], [1, 1.6]], alpha: [[0, 1], [1, 0]] },
  { name: "N04 RingH2", mat: "line", mode: "mesh", mesh: "ringH", fudge: -2, size: 0.8, rot3: YAW, rotLife: [0, -2.5, 0], bursts: [[0.12, 1], [0.24, 1]], life: 0.6, sizeLife: [[0, 0.3, 0, 2], [1, 1.5]], alpha: [[0, 0.8], [1, 0]] },
  { name: "N05 Notes", mat: "note", fudge: -5, life: [0.9, 1.2], size: [0.28, 0.4], rot: [-0.3, 0.3], shape: { type: "circle", r: 0.35, th: 1, y: 0 }, bursts: [[0.02, 4], [0.15, 3], [0.3, 3]],
    vel: { y: [1.2, 2.0], rad: [0.4, 0.9] }, noise: { s: 0.3, f: 0.8, sp: 0.4 }, sizeLife: [[0, 0.3, 0, 4], [0.2, 1], [1, 0.9]], alpha: [[0, 0], [0.12, 1], [0.75, 0.9], [1, 0]] },
  { name: "N06 Sparkle", mat: "star", fudge: -9, life: [0.3, 0.5], size: [0.12, 0.22], rot: [0, TAU], shape: { type: "sphere", r: 0.8, th: 1 }, bursts: [[0.05, 8], [0.25, 6]], sizeLife: [[0, 0.2], [0.3, 1], [1, 0]] },
  { name: "N07 Motes", mat: "dot", fudge: -6, life: [0.8, 1.2], size: [0.04, 0.07], shape: { type: "sphere", r: 0.3, th: 1 }, bursts: [[0.02, 20]], speed: [0.6, 1.4], vel: { y: [0.5, 1] },
    color: [[0, PALE], [1, GOLD]], alpha: [[0, 1], [1, 0]] },
  { name: "N08 PillarDown", mat: "streak", mode: "stretch", vs: 0.06, ls: 2, fudge: -3, life: [0.3, 0.45], size: [0.05, 0.08], shape: { type: "circle", r: 0.55, th: 0, y: -0.2 }, bursts: [[0.02, 10]],
    vel: { y: [-6, -4] }, alpha: [[0, 1], [1, 0]] },
];
const WRONG = [
  { name: "W01 Distort", mat: "hNote", fudge: -5, size: 0.6, rot: [0.4, 0.4], bursts: [[0, 1]], life: 0.3, sizeLife: [[0, 1], [0.5, 1.25], [1, 0.4]], alpha: [[0, 1], [1, 0]] },
  { name: "W02 Flare", mat: "hFlare", fudge: -7, size: 1.4, rot: [0, TAU], bursts: [[0.12, 1]], life: 0.3, sizeLife: [[0, 0.3], [0.3, 1], [1, 1.1]], alpha: [[0, 0.8], [1, 0]] },
  { name: "W03 Ring", mat: "hLine", mode: "mesh", mesh: "ringV", fudge: -3, size: 1.0, rot3: [0, [0, TAU], 0], bursts: [[0.12, 1]], life: 0.3, sizeLife: [[0, 0.3, 0, 3], [1, 1.3]], alpha: [[0, 1], [1, 0]] },
  { name: "W04 FallNotes", mat: "hNote", fudge: -4, life: [0.6, 0.8], size: [0.2, 0.3], rot: [0, TAU], rotZ: [-6, 6], shape: { type: "sphere", r: 0.2, th: 1 }, bursts: [[0.12, 5]],
    speed: [1.5, 2.5], gravity: 1.2, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "W05 Shards", mat: "shardDark", fudge: 16, life: [0.5, 0.7], size: [0.08, 0.14], rot: [0, TAU], rotZ: [-12, 12], shape: { type: "sphere", r: 0.15, th: 1 }, bursts: [[0.12, 10]],
    speed: [2, 3.5], gravity: 1.5, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "W06 Mist", mat: "mist", fudge: 22, life: [0.6, 0.8], size: [0.5, 0.8], rot: [0, TAU], shape: { type: "sphere", r: 0.2, th: 1 }, bursts: [[0.14, 5]], speed: [0.5, 1],
    sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
];
const MATS = {
  star: { tex: "T_Star", tint: [2.6, 2.3, 1.5], add: true }, glow: { tex: "T_Glow", tint: [1.4, 1.1, 0.5], add: true }, line: { tex: "T_Line", tint: [2.5, 2.1, 1.1], add: true },
  note: { tex: "T_Note", tint: [2.3, 2.0, 1.1], add: true }, dot: { tex: "T_Dot", tint: [2.3, 2.0, 1.2], add: true }, streak: { tex: "T_Streak", tint: [2.3, 1.9, 1.0], add: true },
  hNote: { tex: "T_Note", tint: [2.4, 0.6, 0.3], add: true }, hFlare: { tex: "T_Flare", tint: [2.4, 0.7, 0.35], add: true }, hLine: { tex: "T_Line", tint: [2.4, 0.6, 0.3], add: true },
  shardDark: { tex: "T_ShardDark", tint: [2.2, 0.7, 0.35], add: false }, mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false },
};
const SCENE = {
  name: "Elysia_Hymn", bloom: BLOOM_HOLY, tonemapping: "neutral",
  systems: [{ id: "ring", role: "static", loop: false, duration: 0.4, layers: RING }, { id: "wrong", role: "static", loop: false, duration: 0.2, layers: WRONG }],
  demo: Object.assign({}, DEMO_BG, { interval: 4,
    scenarios: { all: { label: "정답 → 오답", path: [[0, [0, 0, 0], 0]], actors: [{ path: [[0, [0, 2.8, 0], 0]], hidden: true }],
      events: [{ t: 0.2, system: "ring", actor: 0 }, { t: 2.0, system: "wrong", actor: 0 }],
      props: [{ c: [0, 1.25, 0], e: [0.4, 1.25, 0.4], shade: [0.7, 0.68, 0.64] }], camera: { target: [0, 2.4, 0], dist: 6, yaw: -90, pitch: 8 } } } }),
};
