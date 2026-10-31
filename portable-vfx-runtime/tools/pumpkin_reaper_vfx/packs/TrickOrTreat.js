// PR_TrickOrTreat — 8. 장난칠까 사탕 줄까. 사탕 본체는 Blockbench 모델(가까이 가면 머리 위로). VFX: scatter(보스) · land(착지) — 떨어질 자리 예고는 모드(원 반경 0.9, 0.5초)
// · treat(attached, 회복) · trick(attached, 저주: 썩은 호박 시야 가림 동안 머리 주변 연출)
const R = 0.9, T = 0.5;
var EXTRA_MESHES = MC_MESHES;
const SCATTER = [
  ...fxFlash("S1", [-1.2, 1.9, 0.8], 0, 3.4, "starPink"), ...fxFlames("S0", [-1.2, 1.9, 0.8], 0, 8, 0.3, "flame", [0.8, 1.3], [0.5, 1.2]), ...fxShock("S8", [0, 0, 0], 0, 6, "shockPink", 0.5),
  { name: "S2 Candies", mat: "candy", fudge: 5, pos: [-1.2, 1.9, 0.8], life: [0.7, 1.0], size: [0.6, 0.9], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [5, 8], drag: 1, gravity: 1.2, rot: [0, TAU], rotZ: [-8, 8],
    vel: { y: [3, 5], space: "world" }, bursts: [[0, 14]] },
  { name: "S3 Confetti", mat: "dotPink", fudge: -6, pos: [-1.2, 1.9, 0.8], life: [0.8, 1.4], size: [0.08, 0.18], shape: { type: "sphere", r: 0.4, th: 1 }, speed: [3, 9], drag: 2.5, gravity: 0.6, bursts: [[0, 70]],
    color: [[0, WH], [0.3, PINK], [1, ORA]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "S4 Stars", mat: "starGold", fudge: -7, pos: [-1.2, 1.9, 0.8], life: [0.5, 0.9], size: [0.2, 0.45], shape: { type: "sphere", r: 0.5, th: 1 }, speed: [2, 5], drag: 2, bursts: [[0.02, 14]], rot: [0, 0.8], sizeLife: [[0, 0.2], [0.2, 1], [1, 0]] },
  ...fxBats("S5", [-1.2, 2.0, 0.8], 0.05, 6, 0.5), ...fxMist("S6", [-1.2, 1.8, 0.8], 0, 5, 0.4, [1.0, 1.6], [1, 2]),
  ...fxSparks("S7", [-1.2, 1.9, 0.8], 0, 18, [4, 8], "streakPink"),
];
const LAND = [...fxShock("L1", [0, 0, 0], 0, 2.8, "shockPink", 0.45), ...fxFlash("L2", [0, 0.4, 0], 0, 2.0, "starPink"), ...fxSparks("L6", [0, 0.3, 0], 0, 14, [3, 6], "streakPink"),
  { name: "L3 Pop", mat: "dotPink", fudge: -6, pos: [0, 0.3, 0], life: [0.4, 0.7], size: [0.05, 0.1], shape: { type: "sphere", r: 0.2, th: 1 }, speed: [2, 4], drag: 2, gravity: 0.8, bursts: [[0, 18]], color: [[0, WH], [1, PINK]], alpha: [[0, 1], [1, 0]] },
  { name: "L4 Glow", mat: "glowPink", fudge: 2, pos: [0, 0.35, 0], life: 3.0, size: 1.0, bursts: [[0, 1]], alpha: [[0, 0], [0.05, 0.4], [0.8, 0.3], [1, 0]] },
  { name: "L5 Twinkle", mat: "starPink", fudge: -7, pos: [0, 0.4, 0], life: [0.4, 0.7], size: [0.15, 0.3], rate: 4, shape: { type: "sphere", r: 0.4, th: 1 }, sizeLife: [[0, 0], [0.4, 1], [1, 0]] },
];
const TREAT = [
  { name: "H1 Ring", mat: "ringGold", mode: "mesh", mesh: "quad", fudge: 40, pos: [0, 0.05, 0], life: 0.9, size: 2.4, bursts: [[0, 1]], sizeLife: [[0, 0.3, 0, 2], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "H2 Sparkles", mat: "starGold", fudge: -7, life: [0.7, 1.2], size: [0.15, 0.32], shape: { type: "circle", r: 0.6, th: 1, y: 0.2 }, bursts: [[0, 16], [0.3, 10]], vel: { y: [1.2, 2.4] }, rot: [0, 0.8],
    sizeLife: [[0, 0.2], [0.2, 1], [1, 0]] },
  { name: "H3 Motes", mat: "dotGold", fudge: -6, life: [0.8, 1.4], size: [0.04, 0.09], shape: { type: "circle", r: 0.5, th: 1, y: 0.1 }, bursts: [[0, 24]], vel: { y: [1, 2.2], orb: [2, 3] }, alpha: [[0, 1], [1, 0]] },
  { name: "H4 Column", mat: "pillarGold", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.6, size: 2.4, bursts: [[0, 1]], rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.3, 0, 3], [0.3, 1]], alpha: [[0, 0], [0.15, 0.7], [1, 0]] },
];
const TRICK = [
  { name: "K1 Puff", mat: "mist", fudge: 35, pos: [0, 1.7, 0], life: [0.6, 0.9], size: [0.8, 1.3], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [0.6, 1.4], bursts: [[0, 8]], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.6], [1, 0]], flip: F44 },
  { name: "K2 OrbitBats", mat: "bat", fudge: 20, pos: [0, 2.0, 0], life: 3.0, size: [0.22, 0.32], shape: { type: "circle", r: 0.7, th: 0, y: 0 }, vel: { orb: [3, 4] }, bursts: [[0, 4]],
    sizeLife: [[0, 0.6], [0.1, 1], [0.2, 0.7], [0.3, 1], [0.4, 0.7], [0.5, 1], [0.6, 0.7], [0.7, 1], [0.8, 0.7], [0.9, 1], [1, 0]], alpha: [[0, 0], [0.05, 1], [0.9, 1], [1, 0]] },
  { name: "K3 CurseFlame", mat: "flame", fudge: 0, pos: [0, 2.2, 0], life: [0.3, 0.5], size: [0.3, 0.55], rate: 14, shape: { type: "sphere", r: 0.25, th: 1 }, vel: { y: [0.6, 1.2] }, rot: [0, TAU], flip: F44 },
  { name: "K4 Motes", mat: "dotVio", fudge: -6, pos: [0, 1.8, 0], life: [0.6, 1.0], size: [0.04, 0.08], rate: 12, shape: { type: "sphere", r: 0.5, th: 1 }, vel: { y: [0.2, 0.6] }, alpha: [[0, 1], [1, 0]] },
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_TrickOrTreat", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "scatter", role: "static", loop: false, duration: 1.6, layers: SCATTER },
    { id: "land", role: "static", loop: false, duration: 3.0, layers: LAND },
    { id: "treat", role: "attached", loop: false, duration: 1.5, layers: TREAT },
    { id: "trick", role: "attached", loop: false, duration: 3.0, layers: TRICK },
  ],
  demo: Object.assign({ interval: 4.0, dummy: { height: 1.8 } }, DEMO_BG, { scenarios: {
    scatter: { label: "뿌리기", path: [[0, [3, 0, 6], 180]], hits: [], anchor: [0, 0, 0], place: { scatter: { at: [0, 0, 0], start: 0 }, land: { at: [2, 0, 5], start: 0.9 }, treat: { start: 99 }, trick: { start: 99 } },
      camera: cam([1, 1, 3], 13, -60, 32) },
    treat: { label: "사탕(회복)", path: [[0, [0, 0, 0], 0]], hits: [], place: { scatter: { start: 99 }, land: { start: 99 }, trick: { start: 99 } }, camera: cam([0, 1, 0], 5, -60, 15) },
    trick: { label: "장난(저주)", path: [[0, [0, 0, 0], 0]], hits: [], place: { scatter: { start: 99 }, land: { start: 99 }, treat: { start: 99 } }, camera: cam([0, 1.4, 0], 5, -60, 15) },
  } }),
};
