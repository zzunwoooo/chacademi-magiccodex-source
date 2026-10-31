// PR_ReturnGuard — 귀환(흩어짐/나타남) + 패턴 중 무적 보호막. systems: vanish(static) · appear(static, 소환 위치) · guard(attached 루프, 서버가 패턴 끝에 정지) · deflect(impact, 무적 중 맞을 때)
var EXTRA_MESHES = Object.assign({
  shell: grid(36, 16, (u, v) => { const p = -u * TAU, th = v * Math.PI; return [Math.sin(th) * Math.cos(p), (1 - Math.cos(th)) * 0.95, Math.sin(th) * Math.sin(p)]; }),
}, MC_MESHES);
const VANISH = [
  { name: "V1 Collapse", mat: "mist", fudge: 35, pos: [0, 1.6, 0], life: [0.5, 0.75], size: [1.4, 2.2], shape: { type: "sphere", r: 1.4, th: 0.5 }, bursts: [[0, 16]],
    vel: { y: [-3, -1.5], rad: [0.5, 1.5], space: "world" }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.65], [1, 0]], sizeLife: [[0, 1], [1, 1.4]], flip: F44 },
  { name: "V2 Ghosts", mat: "ghost", fudge: 5, pos: [0, 1.5, 0], life: [0.7, 1.1], size: [0.6, 1.0], shape: { type: "sphere", r: 0.8, th: 1 }, speed: [1, 3], vel: { y: [2, 4], space: "world" }, bursts: [[0.15, 8]], alpha: [[0, 0], [0.15, 0.9], [1, 0]] },
  ...fxBats("V3", [0, 2, 0], 0.1, 10, 1), ...fxShock("V4", [0, 0, 0], 0.4, 5, "shockVio"),
  { name: "V5 Sink", mat: "sigilVio", mode: "mesh", mesh: "quad", fudge: 47, pos: [0, 0.05, 0], life: 0.8, size: 3.4, rotLife: [0, 3, 0], bursts: [[0, 1]], sizeLife: [[0, 0.6], [0.3, 1], [1, 0.1]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
  ...fxMotes("V6", [0, 0.2, 0], 0.3, 20, 1.2, "dotVio"),
];
const APPEAR = [
  ...magicCircle("A0", 2.8, [0, 0, 0], 0, 1.2, "Vio", 0.55),
  { name: "A1 Gather", mat: "mist", fudge: 35, pos: [0, 1.2, 0], life: [0.4, 0.6], size: [1.0, 1.8], shape: { type: "sphere", r: 2.2, th: 0.3 }, bursts: [[0, 14]], vel: { rad: [-4.5, -3.5] }, rot: [0, TAU],
    alpha: [[0, 0], [0.3, 0.6], [1, 0]], sizeLife: [[0, 1.2], [1, 0.5]], flip: F44 },
  { name: "A2 Swirl", mat: "streakVio", mode: "stretch", vs: 0.06, ls: 2, fudge: -3, life: [0.4, 0.6], size: [0.08, 0.14], shape: { type: "circle", r: 2, th: 0.2, y: 0.1 }, vel: { orb: [6, 9], rad: [-3, -2], y: [3, 5] }, bursts: [[0, 30]], alpha: [[0, 1], [1, 0]] },
  { name: "A3 Column", mat: "pillarSoul", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.55, size: 5, bursts: [[0.5, 1]], rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.2, 0, 4], [0.3, 1]], alpha: [[0, 0], [0.15, 1], [1, 0]] },
  ...fxFlash("A4", [0, 2.4, 0], 0.55, 3.0, "starVio"), ...fxShock("A5", [0, 0, 0], 0.55, 6, "shockVio"), ...fxSparks("A6", [0, 2, 0], 0.55, 20, [4, 8], "streakVio"),
];
const GL = 2.0;   // guard loop: overlapping held copies (rate 1/life + triangle alpha) so any hold length works
const GUARD = [
  { name: "G1 Shell", mat: "hex", mode: "mesh", mesh: "shell", fudge: -2, pos: [0, -0.2, 0], life: GL, size: 1.9, rotLife: [0, 0.25, 0], rate: 1 / GL * 2, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.42], [1, 0]] },
  { name: "G2 ShellInner", mat: "hex", mode: "mesh", mesh: "shell", fudge: -1, pos: [0, -0.2, 0], life: GL, size: 1.75, rotLife: [0, -0.35, 0], rate: 1 / GL * 2, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.18], [1, 0]] },
  { name: "G3 RuneRing", mat: "runeVio", mode: "mesh", mesh: "mcRunesOut", fudge: 50, pos: [0, 0.05, 0], life: GL, size: 2.6, rotLife: [0, 0.6, 0], rate: 1 / GL * 2, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.9], [1, 0]] },
  { name: "G4 Ring", mat: "lineVio", mode: "mesh", mesh: "mcRing1", fudge: 51, pos: [0, 0.05, 0], life: GL, size: 2.3, rotLife: [0, -0.4, 0], rate: 1 / GL * 2, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.9], [1, 0]] },
  { name: "G5 Wisps", mat: "soulFlame", fudge: 0, life: [0.8, 1.2], size: [0.3, 0.5], rate: 10, shape: { type: "circle", r: 1.9, th: 0, y: 1.6 }, vel: { orb: [2, 3], y: [-0.3, 0.3] }, rot: [0, TAU], flip: F44 },
  { name: "G6 Motes", mat: "dotVio", fudge: -6, life: [1, 1.6], size: [0.04, 0.08], rate: 14, shape: { type: "circle", r: 1.8, th: 0.3, y: 0.1 }, vel: { y: [0.6, 1.4] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
];
const DEFLECT = [...fxFlash("D1", [0, 0, 0], 0, 1.6, "starVio"), ...fxSparks("D2", [0, 0, 0], 0, 18, [4, 8], "streakVio", 0.2),
  { name: "D3 HexRipple", mat: "hex", mode: "mesh", mesh: "quad", fudge: -3, pos: [0, 0, 0], life: 0.35, size: 1.6, rot3: [Math.PI / 2, 0, 0], bursts: [[0, 1]], sizeLife: [[0, 0.3], [1, 1.2]], alpha: [[0, 1], [1, 0]] },
  { name: "D4 Ring", mat: "ringVio", mode: "mesh", mesh: "quad", fudge: -4, pos: [0, 0.01, 0], life: 0.3, size: 1.4, rot3: [Math.PI / 2, 0, 0], bursts: [[0, 1]], sizeLife: [[0, 0.2], [1, 1]], alpha: [[0, 1], [1, 0]] }];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_ReturnGuard", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "vanish", role: "static", loop: false, duration: 1.5, layers: VANISH },
    { id: "appear", role: "static", loop: false, duration: 1.5, layers: APPEAR },
    { id: "guard", role: "attached", loop: true, duration: GL, layers: GUARD },
    { id: "deflect", role: "impact", loop: false, duration: 0.8, layers: DEFLECT },
  ],
  demo: Object.assign({ interval: 4.5, dummy: { height: 3.3 } }, DEMO_BG, { scenarios: {
    ret: { label: "귀환", path: [[0, [0, 0, 0], 0]], hits: [], place: { vanish: { at: [6, 0, 4], start: 0 }, appear: { at: [0, 0, 0], start: 0.7 }, guard: { start: 99 } }, camera: cam([3, 1.4, 2], 15, -70, 28) },
    guard: { label: "무적", path: [[0, [0, 0, 0], 0]], place: { vanish: { start: 99 }, appear: { start: 99 } },
      hits: [{ t: 1.0, point: [0.4, 1.6, -1.85], normal: [0, 0, -1], incoming: [0, 0, 1], system: "deflect" }, { t: 1.8, point: [-1.2, 1.2, -1.4], normal: [-0.6, 0, -0.8], incoming: [0.6, 0, 0.8], system: "deflect" }],
      darts: true, camera: cam([0, 1.6, 0], 8, -110, 15) },
  } }),
};
