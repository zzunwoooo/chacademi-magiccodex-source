// TW_WorldStop — 기믹3 멈춘 세계. systems:
//   start (static 보스 발밑 r 4, 1.6초: 시간을 멈추는 손가락 튕김) · freeze (static 경기장 중심 r 18, 1.6초: 회색 시간 물결이 방 전체로 퍼짐)
//   origin (static 탄환 출발 방향의 경기장 가장자리 r 2, 2.6초: 빛나는 문자판 숫자 기둥 = 예고 동안 방향 표시)
//   shield (attached 방패 플레이어, 루프 2초: 머리 위 금빛 모래시계 + 발밑 고리) · bullet (projectile: 청록 시곗바늘 탄환)
//   block (static r 1, 0.5초: 방패가 막음, 금빛 튕김) · hit (static r 1, 0.6초: 탄에 맞음) · release (static 경기장 중심 r 18, 1.0초: 멈춘 시간이 풀림)
const RA = 18, SL = 2.0;
var EXTRA_MESHES = Object.assign({ bulletQ: grid(1, 1, (u, v) => [(u - 0.5) * 0.35, 0, (v - 0.5) * 1.6]), bulletV: grid(1, 1, (u, v) => [0, (u - 0.5) * 0.35, (v - 0.5) * 1.6]) }, TIME_MESHES);
const START = [
  ...clockCircle("B0", 4, [0, 0, 0], 0, 1.55, "Soul", 0.4),
  { name: "M1 Snap", mat: "starIce", fudge: -8, pos: [-1.6, 7.5, 0.8], life: 0.25, size: 4, bursts: [[0.4, 1]], sizeLife: [[0, 0.2], [0.25, 1.1], [1, 0]] },
  { name: "M2 Halo", mat: "lineSoul", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, 6.2, 0.9], life: 1.2, size: 2.4, rotLife: [0, 0, -4], bursts: [[0.3, 1]], sizeLife: [[0, 0.3, 0, 3], [0.3, 1.3], [1, 1.2]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] },
  ...fxRipple("M3", [0, 0, 0], 0.4, 10, 1.0, "rippleGrey", 0.8),
  ...fxShockT("M4", [0, 0, 0], 0.4, 10, 0.6, 0.6),
  ...fxRays("T1", [-1.6, 7.5, 0.8], 0.41, 16, [8, 13], "streakSoul", 3.5),
  ...fxGears("T2", [0, 4, 0], 0.42, 8, 2, "gearTeal"),
];
const FREEZE = [
  { name: "M1 Wave", mat: "rippleGrey", mode: "mesh", mesh: "quad", fudge: 28, pos: [0, 0.1, 0], life: 1.2, size: 2 * RA + 2, bursts: [[0, 1], [0.25, 1]], sizeLife: [[0, 0.05, 0, 2], [1, 1]],
    alpha: [[0, 0], [0.1, 0.7], [0.7, 0.4], [1, 0]] },
  { name: "M2 Dome", mat: "lineSoul", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, 0.2, 0], life: 1.2, size: RA, rot3: [Math.PI / 2, 0, 0], bursts: [[0.05, 1]], sizeLife: [[0, 0.05, 0, 2], [1, 1]],
    alpha: [[0, 0], [0.1, 0.9], [1, 0]] },
  { name: "M3 Stillness", mat: "dotSoul", fudge: -7, life: [1.2, 1.6], size: [0.05, 0.1], shape: { type: "circle", r: RA, th: 1, y: 1.2 }, bursts: [[0.1, 160]], alpha: [[0, 0], [0.2, 1], [0.8, 1], [1, 0]] },
  { name: "M4 Ticks", mat: "lineSoul", mode: "mesh", mesh: "mcTicks", fudge: 43, pos: [0, 0.05, 0], life: 1.5, size: RA, bursts: [[0.1, 1]], alpha: [[0, 0], [0.15, 0.8], [0.8, 0.6], [1, 0]] },
  { name: "M5 Runes", mat: "runeSoul", mode: "mesh", mesh: "mcRunesOut", fudge: 50, pos: [0, 0.05, 0], life: 1.5, size: RA * 1.05, bursts: [[0.15, 1]], alpha: [[0, 0], [0.15, 0.8], [0.8, 0.6], [1, 0]] },
];
const ORIGIN = [
  { name: "M1 Pillar", mat: "pillarSoul", mode: "mesh", mesh: "pillar", fudge: 5, life: 2.5, size: 9, bursts: [[0, 1]], sizeLife: [[0, 0.2, 0, 4], [0.15, 1], [1, 1]], alpha: [[0, 0], [0.08, 1], [0.85, 0.9], [1, 0]] },
  { name: "M2 Dial", mat: "dial", fudge: 3, pos: [0, 5, 0], life: 2.5, size: 3.4, bursts: [[0, 1]], sizeLife: [[0, 0.2, 0, 4], [0.15, 1], [1, 1]], alpha: [[0, 0], [0.08, 1], [0.85, 0.9], [1, 0]] },
  { name: "M3 Ring", mat: "ringSoul", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.1, 0], life: 0.8, size: 4, rot3: [0, [0, TAU], 0], bursts: [[0, 1], [0.8, 1], [1.6, 1]], sizeLife: [[0, 0.3], [1, 1]], alpha: [[0, 0.9], [1, 0]] },
  { name: "M4 Rise", mat: "dotSoul", fudge: -7, life: [0.8, 1.2], size: [0.05, 0.1], shape: { type: "circle", r: 1.2, th: 0.5, y: 0.2 }, rate: 30, vel: { y: [3, 6], space: "world" }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  ...fxFlashT("T1", [0, 5, 0], 0, 3),
];
const SHIELD = [
  { name: "S1 Hourglass", mat: "hglass", fudge: 3, pos: [0, 2.9, 0], life: SL, size: 1.0, rate: 1 / SL, bursts: [[0, 1]], onStop: 0.2, alpha: [[0, 0.9], [0.5, 1], [1, 0.9]] },
  { name: "S2 Ring", mat: "lineGold", mode: "mesh", mesh: "vRing", fudge: 4, pos: [0, 2.9, 0], life: SL, size: 0.75, rotLife: [0, 3, 0], rate: 1 / SL, bursts: [[0, 1]], onStop: 0.2, alpha: [[0, 0.9], [1, 0.9]] },
  { name: "S3 Ground", mat: "lineGold", mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: SL, size: 1.3, rotLife: [0, 1, 0], rate: 1 / SL, bursts: [[0, 1]], onStop: 0.2, alpha: [[0, 0.9], [1, 0.9]] },
  { name: "S4 GroundRunes", mat: "runeGold", mode: "mesh", mesh: "mcRunesIn", fudge: 50, pos: [0, 0.05, 0], life: SL, size: 1.5, rotLife: [0, -1, 0], rate: 1 / SL, bursts: [[0, 1]], onStop: 0.2, alpha: [[0, 0.8], [1, 0.8]] },
  { name: "S5 Sand", mat: "sand", fudge: -4, life: [0.6, 1.0], size: [0.04, 0.08], shape: { type: "circle", r: 0.8, th: 0.5, y: 0.2 }, rate: 20, vel: { y: [1, 2], orb: [2, 3], space: "world" }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "S6 Glow", mat: "glowGold", fudge: 2, pos: [0, 1.1, 0], life: 1.0, size: 2.4, rate: 2, bursts: [[0, 1]], onStop: 0.2, alpha: [[0, 0], [0.4, 0.2], [1, 0]] },
];
const BULLET = [
  { name: "P01 Body", mat: "handTeal", mode: "mesh", mesh: "bulletQ", fudge: 0, loop: true, rate: 30, life: 0.08, size: 1.0, alpha: [[0, 1], [1, 1]] },
  { name: "P02 BodyV", mat: "handTeal", mode: "mesh", mesh: "bulletV", fudge: 0, loop: true, rate: 30, life: 0.08, size: 1.0, alpha: [[0, 1], [1, 1]] },
  { name: "P03 Glow", mat: "glowSoul", fudge: 2, loop: true, rate: 12, life: 0.25, size: 1.2, alpha: [[0, 0], [0.3, 0.6], [1, 0]] },
  { name: "P04 Core", mat: "starSoul", fudge: -2, loop: true, rate: 16, life: 0.15, size: [0.5, 0.7], pos: [0, 0, 0.7], rot: [0, TAU], alpha: [[0, 0.9], [1, 0.4]] },
  { name: "P05 Trail", mat: "streakSoul", mode: "stretch", vs: 0.1, ls: 2, fudge: -5, loop: true, world: true, rateDist: 6, life: [0.15, 0.25], size: [0.1, 0.18], shape: { type: "sphere", r: 0.08, th: 1 }, alpha: [[0, 0.8], [1, 0]] },
  { name: "P06 Sand", mat: "sandTeal", fudge: -4, loop: true, world: true, rateDist: 5, life: [0.3, 0.5], size: [0.04, 0.08], shape: { type: "sphere", r: 0.15, th: 1 }, vel: { y: [-0.6, 0.6] }, alpha: [[0, 1], [1, 0]] },
];
const BLOCK = [
  { name: "M1 Hex", mat: "dialGold", fudge: 3, pos: [0, 1.2, 0], life: 0.3, size: 1.8, bursts: [[0, 1]], sizeLife: [[0, 0.6], [0.3, 1.1], [1, 1.2]], alpha: [[0, 1], [1, 0]] },
  ...fxFlashT("T1", [0, 1.2, 0], 0, 1.8, "starGold", "glowGold"),
  ...fxSparks("T2", [0, 1.2, 0], 0, 14, [4, 8], "streakGold", 0.2),
];
const HIT = [
  ...fxFlashT("T1", [0, 1.2, 0], 0, 2.2),
  ...fxSparks("T2", [0, 1.2, 0], 0, 18, [4, 9], "streakSoul", 0.2),
  { name: "M1 Shards", mat: "shard", mode: "stretch", vs: 0.08, ls: 1.3, fudge: 25, pos: [0, 1.2, 0], life: [0.3, 0.5], size: [0.1, 0.2], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [4, 8], gravity: 1, bursts: [[0, 12]], alpha: [[0, 1], [1, 0]] },
];
const RELEASE = [
  { name: "M1 Wave", mat: "ripple", mode: "mesh", mesh: "quad", fudge: 28, pos: [0, 0.1, 0], life: 0.8, size: 2 * RA + 2, bursts: [[0, 1]], sizeLife: [[0, 1], [1, 0.05]], alpha: [[0, 0], [0.15, 0.7], [1, 0]] },
  { name: "M2 Rush", mat: "streakSoul", mode: "stretch", vs: 0.08, ls: 3, fudge: -5, life: [0.3, 0.45], size: [0.08, 0.15], shape: { type: "circle", r: RA, th: 0.6, y: 1 }, bursts: [[0, 60]],
    vel: { rad: [-40, -30] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  ...fxFlashT("T1", [0, 3, 0], 0.7, 4),
];
const SYSTEMS = [
  { id: "start", role: "static", loop: false, duration: 1.6, layers: START },
  { id: "freeze", role: "static", loop: false, duration: 1.6, layers: FREEZE },
  { id: "origin", role: "static", loop: false, duration: 2.6, layers: ORIGIN },
  { id: "shield", role: "attached", loop: true, duration: SL, layers: SHIELD },
  { id: "bullet", role: "projectile", loop: true, duration: 60, layers: BULLET },
  { id: "block", role: "static", loop: false, duration: 0.5, layers: BLOCK },
  { id: "hit", role: "static", loop: false, duration: 0.6, layers: HIT },
  { id: "release", role: "static", loop: false, duration: 1.0, layers: RELEASE },
];
const MATS = usedMats(SYSTEMS);
const NOP = { start: { start: 99 }, freeze: { start: 99 }, origin: { start: 99 }, shield: { start: 99 }, block: { start: 99 }, hit: { start: 99 }, release: { start: 99 } };
const SCENE = {
  name: "TW_WorldStop", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: RA, min: 0.05, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 4.0, dummy: { height: 1.8 } }, DEMO_BG, { scenarios: {
    stop: { label: "시간 정지", path: [[0, [0, 0, 0], 0]], fire: { at: 99, stop: [] }, hits: [], place: Object.assign({}, NOP, { start: { at: [0, 0, 0], start: 0 }, freeze: { at: [0, 0, 0], start: 0.4 }, origin: { at: [0, 0, 12], start: 0.2 } }),
      camera: cam([0, 2, 4], 30, -40, 30) },
    shield: { label: "방패 + 탄", path: [[0, [0, 0, 0], 0]], fire: { at: 0.4, stop: [] }, flightPath: [[0, 1.2, 10], [0, 1.2, 0.6]], flight: 0.6, normal: [0, 1, 0], anchor: [0, 0, 0], targets: [], hits: [],
      place: Object.assign({}, NOP, { shield: { start: 0 }, block: { at: [0, 0, 0.6], start: 1.0 }, hit: { at: [3, 0, 2], start: 1.2 }, release: { at: [0, 0, 0], start: 1.6 } }), camera: cam([0, 1.5, 3], 12, -60, 18) },
  } }),
};
