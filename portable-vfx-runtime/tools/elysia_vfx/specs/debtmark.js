// Elysia_DebtMark — 빚(은총 ✦) 표시 3종.
//  gain  (attached, 대상 플레이어): 은혜를 받아 ✦가 하나 새겨짐 — 머리 위 금빛 별이 박히고 발밑에 작은 계약 고리. 약 1.3 s.
//  pay   (attached, 대상 플레이어): 수금·상환 증서로 빚 1을 갚음 — 붉게 변한 ✦가 깨져 흩어짐. 약 1.1 s.
//  chain (link, 반복): 빚의 사슬 — source = 플레이어 가슴, target = 벨자르 가슴. 사슬 고리가 플레이어 → 보스로 끌려간다. 모드가 정지 신호로 끈다.
//  (✦ 개수 홀로그램 자체는 텍스트 디스플레이로 모드가 그림)
const HEAD = 2.25;
const lin = (a, b) => [[0, a, 0, b - a], [1, b, b - a, 0]];
const CURVE = { bend: { x: 0, y: -0.35 }, wave: { amp: 0.06, cycles: 3, phase: 0, vr: 1 } };
var EXTRA_MESHES = {
  foot: merge([ringM(0.6, 0.02, 0.03), ringM(0.54, 0.008, 0.03), polygram(5, 2, 0.54, 0.01, 0.035, -Math.PI / 2)]),
  footRune: runeArcs(8, 0.46, 0.09, 0.03),
};
const GAIN = [
  { name: "G01 Converge", mat: "dot", fudge: -6, life: 0.45, size: [0.05, 0.1], shape: { type: "sphere", r: 1.1, th: 0 }, pos: [0, HEAD, 0], bursts: [[0, 22]],
    vel: { rad: [-2.6, -2.2] }, color: [[0, PALE], [1, GOLD]], alpha: [[0, 0], [0.3, 1], [1, 1]] },
  { name: "G02 ConvergeStreak", mat: "streak", mode: "stretch", vs: 0.12, ls: 1.4, fudge: -4, life: 0.4, size: [0.05, 0.08], shape: { type: "sphere", r: 0.9, th: 0 }, pos: [0, HEAD, 0], bursts: [[0.05, 12]],
    vel: { rad: [-2.4, -2.0] }, alpha: [[0, 0], [0.4, 1], [1, 0.5]] },
  { name: "G03 StarBody", mat: "star", fudge: -8, pos: [0, HEAD, 0], size: 0.9, bursts: [[0.42, 1]], life: 0.85, rot: [0, 0], rotZ: [0.6, 0.6],
    sizeLife: [[0, 0.1, 0, 6], [0.15, 1.3], [0.35, 0.9], [1, 0]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "G04 Flare", mat: "flare", fudge: -7, pos: [0, HEAD, 0], size: 1.4, rot: [0, TAU], bursts: [[0.42, 1]], life: 0.35, sizeLife: [[0, 0.3], [0.3, 1], [1, 1.2]], alpha: [[0, 0.8], [1, 0]] },
  { name: "G05 Ring", mat: "ring", fudge: -5, pos: [0, HEAD, 0], size: 1.1, bursts: [[0.42, 1]], life: 0.4, sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "G06 Glow", mat: "glow", fudge: 0, pos: [0, HEAD, 0], size: 1.2, bursts: [[0.0, 1]], life: 1.2, sizeLife: [[0, 0.3], [0.35, 1], [1, 0.4]], alpha: [[0, 0], [0.35, 0.55], [1, 0]] },
  { name: "G07 Sparkle", mat: "dot", mode: "stretch", vs: 0.05, ls: 1.2, fudge: -6, pos: [0, HEAD, 0], life: [0.35, 0.6], size: [0.04, 0.07], bursts: [[0.42, 16]],
    shape: { type: "sphere", r: 0.1, th: 1 }, speed: [2, 4], drag: 4, color: [[0, PALE], [1, AMBER]], alpha: [[0, 1], [1, 0]] },
  { name: "G08 FootRing", mat: "line", mode: "mesh", mesh: "foot", fudge: 30, size: 1, rot3: YAW, rotLife: [0, 1.2, 0], bursts: [[0.42, 1]], life: 0.9,
    sizeLife: [[0, 1.4, 0, -3], [0.3, 1]], alpha: [[0, 0], [0.15, 1], [0.6, 0.8], [1, 0]] },
  { name: "G09 FootRune", mat: "rune", mode: "mesh", mesh: "footRune", fudge: 31, size: 1, rot3: YAW, rotLife: [0, -0.9, 0], bursts: [[0.45, 1]], life: 0.85,
    sizeLife: [[0, 1.3, 0, -3], [0.3, 1]], alpha: [[0, 0], [0.2, 0.9], [0.6, 0.7], [1, 0]] },
  { name: "G10 Stamp", mat: "streak", mode: "stretch", vs: 0.1, ls: 1.6, fudge: -3, pos: [0, HEAD, 0], life: 0.22, size: 0.12, bursts: [[0.42, 1]], vel: { y: -10 }, alpha: [[0, 1], [1, 0]] },
];
const PAY = [
  { name: "P01 RedStar", mat: "hStar", fudge: -8, pos: [0, HEAD, 0], size: 0.85, rot: [0.6, 0.6], bursts: [[0, 1]], life: 0.35,
    sizeLife: [[0, 1], [0.6, 1.15], [1, 0.2]], alpha: [[0, 1], [1, 0]] },
  { name: "P02 CrackFlash", mat: "hFlare", fudge: -7, pos: [0, HEAD, 0], size: 1.3, rot: [0, TAU], bursts: [[0.18, 1]], life: 0.3, sizeLife: [[0, 0.3], [0.3, 1], [1, 1.1]], alpha: [[0, 0.8], [1, 0]] },
  { name: "P03 Shards", mat: "shardDark", fudge: 15, pos: [0, HEAD, 0], life: [0.6, 0.9], size: [0.1, 0.18], rot: [0, TAU], rotZ: [-12, 12], bursts: [[0.18, 12]],
    shape: { type: "sphere", r: 0.1, th: 1 }, speed: [1.8, 3.2], drag: 1, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "P04 GoldShards", mat: "shardLight", fudge: -4, pos: [0, HEAD, 0], life: [0.5, 0.8], size: [0.08, 0.14], rot: [0, TAU], rotZ: [-12, 12], bursts: [[0.18, 10]],
    shape: { type: "sphere", r: 0.1, th: 1 }, speed: [2, 3.5], drag: 1, gravity: 1.2, alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "P05 Embers", mat: "hDot", fudge: -6, pos: [0, HEAD, 0], life: [0.6, 1.0], size: [0.04, 0.07], bursts: [[0.18, 18]], shape: { type: "sphere", r: 0.15, th: 1 },
    speed: [0.8, 2], drag: 1.5, gravity: -0.1, color: [[0, EMB], [1, DEEP]], alpha: [[0, 1], [1, 0]] },
  { name: "P06 Ring", mat: "hRing", fudge: -5, pos: [0, HEAD, 0], size: 1.0, bursts: [[0.18, 1]], life: 0.35, sizeLife: [[0, 0.3, 0, 3], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "P07 Mist", mat: "mist", fudge: 22, pos: [0, HEAD, 0], life: [0.7, 1.0], size: [0.5, 0.8], rot: [0, TAU], bursts: [[0.2, 4]], shape: { type: "sphere", r: 0.15, th: 1 },
    speed: [0.3, 0.6], sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 } },
  { name: "P08 Page", mat: "page", fudge: 14, pos: [0, HEAD - 0.2, 0], life: [0.8, 1.1], size: [0.14, 0.2], rot: [0, TAU], rotZ: [-6, 6], bursts: [[0.2, 5]], shape: { type: "sphere", r: 0.2, th: 1 },
    speed: [0.6, 1.2], gravity: 0.25, drag: 1, alpha: [[0, 1], [0.7, 1], [1, 0]] },
];
const CHAIN = [
  { name: "C01 Links", mat: "chain", mode: "stretch", ls: 1.0, vs: 0.0, fudge: 0, life: 1.2, size: 0.13, rate: 16, bursts: [[0, 16]],
    path: { progress: lin(0, 1), ...CURVE }, color: [[0, [1, 0.7, 0.5]], [1, [1, 0.45, 0.3]]], alpha: [[0, 0], [0.06, 1], [0.94, 1], [1, 0]], onStop: 0.2 },
  { name: "C02 Haze", mat: "hStreak", mode: "stretch", ls: 0.6, vs: 0.05, fudge: 2, life: 1.2, size: 0.22, rate: 14, bursts: [[0, 14]],
    path: { progress: lin(0, 1), ...CURVE }, alpha: [[0, 0], [0.1, 0.4], [0.9, 0.4], [1, 0]], onStop: 0.2 },
  { name: "C03 Embers", mat: "hDot", fudge: -6, life: 1.2, size: [0.03, 0.06], rate: 10, path: { progress: lin(0, 1), bend: { x: [-0.2, 0.2], y: [-0.5, -0.1] }, wave: { amp: [0.05, 0.2], cycles: [1, 3], phase: [0, TAU], vr: 1 } },
    color: [[0, EMB], [1, CRIM]], alpha: [[0, 0], [0.2, 1], [1, 0]], onStop: 0.2 },
  { name: "C06 HotLine", mat: "hStreak", mode: "stretch", ls: 0.5, vs: 0.03, fudge: -1, life: 1.2, size: 0.05, rate: 24, bursts: [[0, 24]],
    path: { progress: lin(0, 1), ...CURVE }, alpha: [[0, 0], [0.1, 0.9], [0.9, 0.9], [1, 0]], onStop: 0.2 },
  { name: "C04 Cuff", mat: "hRing", fudge: -3, life: 0.8, size: 0.55, rate: 2.5, bursts: [[0, 1]], path: { progress: [[0, 0, 0, 0], [1, 0, 0, 0]] },
    sizeLife: [[0, 0.6], [1, 1.1]], alpha: [[0, 0], [0.3, 0.8], [1, 0]], onStop: 0.2 },
  { name: "C05 CuffMist", mat: "mist", fudge: 20, life: 1.0, size: [0.35, 0.5], rot: [0, TAU], rate: 3, path: { progress: [[0, 0, 0, 0], [1, 0, 0, 0]] },
    sizeLife: [[0, 0.6], [1, 1.2]], alpha: [[0, 0], [0.3, 0.45], [1, 0]], flip: { x: 4, y: 4 }, onStop: 0.2 },
];
const MATS = {
  dot: { tex: "T_Dot", tint: [2.4, 2.0, 1.2], add: true }, streak: { tex: "T_Streak", tint: [2.4, 2.0, 1.1], add: true }, star: { tex: "T_Star", tint: [2.8, 2.3, 1.2], add: true },
  flare: { tex: "T_Flare", tint: [2.2, 1.8, 0.9], add: true }, ring: { tex: "T_Ring", tint: [2.2, 1.8, 0.9], add: true }, glow: { tex: "T_Glow", tint: [1.3, 1.0, 0.4], add: true },
  line: { tex: "T_Line", tint: [2.4, 1.9, 1.0], add: true }, rune: { tex: "T_Rune", tint: [2.2, 1.8, 0.9], add: true },
  hStar: { tex: "T_Star", tint: [2.8, 0.9, 0.5], add: true }, hFlare: { tex: "T_Flare", tint: [2.4, 0.8, 0.4], add: true }, hRing: { tex: "T_Ring", tint: [2.3, 0.6, 0.3], add: true },
  hDot: { tex: "T_Dot", tint: [2.4, 1.1, 0.6], add: true }, hStreak: { tex: "T_Streak", tint: [1.8, 0.35, 0.15], add: true },
  shardDark: { tex: "T_ShardDark", tint: [2.2, 0.8, 0.4], add: false }, shardLight: { tex: "T_ShardLight", tint: [2.2, 1.7, 0.8], add: true },
  mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false }, page: { tex: "T_Page", tint: [1, 1, 1], add: false },
  chain: { tex: "T_Chain", tint: [1.5, 0.7, 0.45], add: false },
};
const P0 = [[0, [0, 0, 0], 0]];
const SCENE = {
  name: "Elysia_DebtMark", bloom: BLOOM_HOLY, tonemapping: "neutral",
  systems: [
    { id: "gain", role: "attached", loop: false, duration: 0.5, layers: GAIN },
    { id: "pay", role: "attached", loop: false, duration: 0.3, layers: PAY },
    { id: "chain", role: "link", loop: true, duration: 2, layers: CHAIN },
  ],
  demo: Object.assign({}, DEMO_BG, { interval: 5.5, dummy: { height: 1.8 },
    scenarios: {
      all: { label: "획득 → 사슬 → 상환", path: P0, targetOffset: [0, 1.3, 0],
        actors: [{ path: [[0, [0, 0, 0], 0]] }, { path: [[0, [0, 0, 6], 180]] }],
        events: [{ t: 0.1, system: "gain", actor: 0 }, { t: 1.4, system: "chain", actor: 0, from: [0, 1.25, 0], to: 1 }, { t: 3.6, system: "pay", actor: 0 }, { t: 4.4, stop: ["chain"] }],
        camera: { target: [0, 1.5, 2.5], dist: 9, yaw: -50, pitch: 16 } },
    } }),
};
