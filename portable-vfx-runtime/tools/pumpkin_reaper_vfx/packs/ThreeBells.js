// PR_ThreeBells — 기믹2 세 번의 종소리. systems: split(분열) · toll(종소리 1번, 서버가 3번) · candleViolet/Orange/Teal(attached, 분신 손 촛불)
// · hitViolet/Orange/Teal(impact, 분신 맞음) · merge(분신 합체) · (전체 베기 예고는 모드: 경기장 원 0.9초) · sweep(실패: 두 바퀴 전체 베기)
const ARENA = 20, TW = 0.9;
var EXTRA_MESHES = Object.assign({ bigArc: arc(0, 330, 0.8, 0), bigThin: arc(0, 300, 0.92, 0) }, MC_MESHES);
const SPLIT = [...fxFlash("S1", [0, 2, 0], 0, 3.4, "starVio"), ...fxMist("S2", [0, 1.4, 0], 0, 14, 1, [1.6, 2.6], [2, 4]), ...fxShock("S3", [0, 0, 0], 0, 8, "shockVio"),
  { name: "S4 Wisps", mat: "streakVio", mode: "stretch", vs: 0.06, ls: 3, fudge: -3, pos: [0, 1.8, 0], life: [0.5, 0.7], size: [0.25, 0.4], shape: { type: "circle", r: 0.5, th: 1, y: 0 }, speed: [6, 9], drag: 2, bursts: [[0.02, 3]], alpha: [[0, 1], [1, 0]] },
  ...fxBats("S5", [0, 2, 0], 0.05, 10, 1), ...fxSparks("S6", [0, 1.8, 0], 0, 24, [5, 10], "streakVio")];
const TOLL = [
  { name: "B1 Wave", mat: "ringGold", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.1, 0], life: 1.2, size: 2 * ARENA, bursts: [[0, 1]], sizeLife: [[0, 0.05, 0, 1.5], [1, 1]], alpha: [[0, 0.9], [0.6, 0.6], [1, 0]] },
  { name: "B2 Wave2", mat: "ringVio", mode: "mesh", mesh: "quad", fudge: 31, pos: [0, 0.12, 0], life: 1.4, size: 2 * ARENA, bursts: [[0.12, 1]], sizeLife: [[0, 0.05, 0, 1.5], [1, 1]], alpha: [[0, 0.7], [0.6, 0.4], [1, 0]] },
  { name: "B3 AirRing", mat: "ringGold", mode: "mesh", mesh: "quad", fudge: 20, pos: [0, 3, 0], life: 0.7, size: 10, bursts: [[0, 1]], sizeLife: [[0, 0.1, 0, 2], [1, 1]], alpha: [[0, 1], [1, 0]] },
  ...fxFlash("B4", [0, 3.4, 0], 0, 3, "starGold"), ...magicCircle("B5", 3.4, [0, 0, 0], 0, 0.9, "Gold", 0),
  { name: "B6 Notes", mat: "starGold", fudge: -7, pos: [0, 3, 0], life: [0.6, 1.0], size: [0.2, 0.4], shape: { type: "sphere", r: 1, th: 0.5 }, speed: [2, 4], drag: 1.5, bursts: [[0, 16]], sizeLife: [[0, 0], [0.3, 1], [1, 0]] },
];
const candle = (mat, tint) => [
  { name: "C1 Flame", mat, fudge: 0, pos: [-1.1, 2.4, 0.4], life: [0.25, 0.4], size: [0.45, 0.7], rate: 26, shape: { type: "sphere", r: 0.06, th: 1 }, vel: { y: [0.6, 1.2] }, rot: [0, TAU], rotZ: [-2, 2], flip: F44 },
  { name: "C2 Glow", mat: tint, fudge: 2, pos: [-1.1, 2.5, 0.4], life: 0.6, size: 1.6, rate: 4, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.45], [1, 0]] },
  { name: "C3 Ring", mat: "line" + (mat === "soulFlame" ? "Soul" : mat === "flame" ? "Ora" : "Vio"), mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: 2, size: 1.6, rotLife: [0, 1, 0], rate: 1, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.9], [1, 0]] },
  { name: "C4 Motes", mat: tint === "glowSoul" ? "dotSoul" : tint === "glowOra" ? "dotFire" : "dotVio", fudge: -6, pos: [0, 1.4, 0], life: [0.8, 1.2], size: [0.04, 0.08], rate: 10, shape: { type: "sphere", r: 0.8, th: 1 }, vel: { y: [0.3, 0.8] }, alpha: [[0, 1], [1, 0]] },
];
const hit = (star, streak, shock) => [...fxFlash("H1", [0, 0, 0], 0, 2.4, star), ...fxSparks("H2", [0, 0, 0], 0, 20, [4, 9], streak, 0.2), ...fxShock("H3", [0, -1.4, 0], 0, 3, shock), ...fxShards("H4", [0, 0, 0], 0, 8, 0.2)];
const MERGE = [
  { name: "M1 Streams", mat: "streakVio", mode: "stretch", vs: 0.06, ls: 3, fudge: -3, pos: [0, 1.8, 0], life: [0.35, 0.45], size: [0.25, 0.4], shape: { type: "circle", r: 6, th: 0.1, y: 0 }, vel: { rad: [-16, -13] }, bursts: [[0, 12]], alpha: [[0, 1], [1, 0]] },
  ...fxFlash("M2", [0, 2, 0], 0.4, 4, "starVio"), ...fxShock("M3", [0, 0, 0], 0.4, 8, "shockVio"), ...fxMist("M4", [0, 1.4, 0], 0.35, 10, 0.8, [1.6, 2.6], [2, 3])];
const SWEEP = [
  ...fxCrack("W1", [0, 0, 0], 0, 8, 2.4),
  { name: "W2 Ink", mat: "slashInk", mode: "mesh", mesh: "bigArc", fudge: 30, pos: [0, 1.1, 0], life: 1.2, size: ARENA, rotLife: [0, -10.5, 0], bursts: [[0, 1]], flip: true },
  { name: "W3 Blade", mat: "slash", mode: "mesh", mesh: "bigArc", fudge: 10, pos: [0, 1.2, 0], life: 1.2, size: ARENA, rotLife: [0, -10.5, 0], bursts: [[0, 1]], flip: true },
  { name: "W4 Blade2", mat: "slashHot", mode: "mesh", mesh: "bigThin", fudge: 9, pos: [0, 0.9, 0], life: 1.0, size: ARENA * 1.02, rot3: [0, 2, 0], rotLife: [0, -12, 0], bursts: [[0.15, 1]], flip: true },
  { name: "W5 Echo", mat: "slashViolet", mode: "mesh", mesh: "bigArc", fudge: 12, pos: [0, 1.6, 0], life: 1.3, size: ARENA * 0.8, rot3: [0, 1, 0], rotLife: [0, -9, 0], bursts: [[0.08, 1]], flip: true },
  ...fxShock("W6", [0, 0, 0], 0.1, 2 * ARENA + 4, "shockOra", 1.0),
  { name: "W7 RingFlames", mat: "flame", fudge: 0, life: [0.5, 0.8], size: [1.6, 2.6], rot: [0, TAU], shape: { type: "circle", r: ARENA * 0.8, th: 0.2, y: 0.8 }, bursts: [[0.1, 30], [0.5, 30], [0.9, 24]],
    vel: { y: [1, 2.4], space: "world" }, sizeLife: [[0, 0.4, 0, 3], [1, 1.1]], flip: F44 },
  { name: "W8 Sparks", mat: "streakOra", mode: "stretch", vs: 0.06, ls: 2, fudge: -5, life: [0.3, 0.55], size: [0.08, 0.14], shape: { type: "circle", r: ARENA * 0.85, th: 0.25, y: 1.1 },
    bursts: [[0.05, 60], [0.45, 60], [0.85, 50]], vel: { orb: [-14, -9], rad: [1, 3] }, drag: 2, gravity: 0.5, alpha: [[0, 1], [0.7, 1], [1, 0]] },
  ...fxBats("W9", [0, 2, 0], 0.1, 16, 6), ...fxEmbers("W10", [0, 1.5, 0], 0.1, 100, ARENA * 0.6),
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_ThreeBells", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "split", role: "static", loop: false, duration: 1.5, layers: SPLIT },
    { id: "toll", role: "static", loop: false, duration: 1.5, layers: TOLL },
    { id: "candleViolet", role: "attached", loop: true, duration: 2, layers: candle("flameVio", "glowVio") },
    { id: "candleOrange", role: "attached", loop: true, duration: 2, layers: candle("flame", "glowOra") },
    { id: "candleTeal", role: "attached", loop: true, duration: 2, layers: candle("soulFlame", "glowSoul") },
    { id: "hitViolet", role: "impact", loop: false, duration: 1, layers: hit("starVio", "streakVio", "shockVio") },
    { id: "hitOrange", role: "impact", loop: false, duration: 1, layers: hit("starOra", "streakOra", "shockOra") },
    { id: "hitTeal", role: "impact", loop: false, duration: 1, layers: hit("starSoul", "streakSoul", "shockSoul") },
    { id: "merge", role: "static", loop: false, duration: 1.5, layers: MERGE },
    { id: "sweep", role: "static", loop: false, duration: 2.6, layers: SWEEP },
  ],
  demo: Object.assign({ interval: 4.6, dummy: { height: 3.3 } }, DEMO_BG, { scenarios: {
    toll: { label: "분열 + 종소리", path: [[0, [0, 0, 6], 180]], hits: [], place: { split: { at: [0, 0, 0], start: 0 }, toll: { at: [0, 0, 0], start: 0.9 }, merge: { start: 99 }, sweep: { start: 99 },
      candleViolet: { start: 99 }, candleOrange: { start: 0.4 }, candleTeal: { start: 99 } }, camera: cam([0, 1.5, 3], 28, -90, 40) },
    fail: { label: "실패 (전체 베기)", path: [[0, [0, 0, 6], 180]], hits: [], place: { merge: { at: [0, 0, 0], start: 0 }, sweep: { at: [0, 0, 0], start: 0.4 + TW },
      split: { start: 99 }, toll: { start: 99 }, candleViolet: { start: 99 }, candleOrange: { start: 99 }, candleTeal: { start: 99 } }, camera: cam([0, 1.5, 0], 46, -90, 48) },
  } }),
};
