// AS_Absorb — 변신(남은 장치 흡수) + 광폭화. systems:
//   beam(link 장치→보스 1.0초: 장치 빛이 재로 변해 빨려듦, 장치당 1번) · burst(static 보스 r 6, 1.6초: 가슴 등불 터짐) · enrage(attached 보스 루프: 잿불 균열 오라)
const R = 6, GL = 2.0, CHEST = 3.6;
var EXTRA_MESHES = Object.assign({}, MC_MESHES);
const PROG = [[0, 0, 0, 1.4], [1, 1, 0.6, 0]];
const BEAM = [
  { name: "L01 Stream", mat: "beamGold", mode: "stretch", vs: 0.0, ls: 3.0, fudge: -3, life: [0.35, 0.5], size: [0.25, 0.42], rate: 90, bursts: [[0, 20]],
    path: { progress: PROG, bend: { x: [-0.3, 0.3], y: [0.1, 0.6] }, wave: { amp: [0.1, 0.3], cycles: [1, 2.5], phase: [0, 1], vr: 0.5 } },
    color: [[0, GLD], [0.55, EMB], [1, DEMB]], alpha: [[0, 0], [0.12, 1], [0.85, 1], [1, 0]] },
  { name: "L02 Core", mat: "beamEmber", mode: "stretch", vs: 0.0, ls: 1.6, fudge: -4, life: 0.35, size: 0.7, rate: 30,
    path: { progress: [[0, 0], [1, 1]], bend: { x: [-0.1, 0.1], y: [0.2, 0.4] } }, color: [[0, GLD], [1, EMB]], alpha: [[0, 0], [0.2, 0.9], [0.9, 0.9], [1, 0]] },
  { name: "L03 AshFlow", mat: "flake", fudge: 12, life: [0.4, 0.6], size: [0.1, 0.2], rate: 50, rot: [0, TAU], rotZ: [-5, 5],
    path: { progress: [[0, 0.3], [1, 1]], bend: { x: [-0.6, 0.6], y: [-0.2, 0.8] } }, alpha: [[0, 0], [0.2, 1], [1, 0.6]], flip: { x: 2, y: 2 } },
  { name: "L03b PathGlow", mat: "glowGold", fudge: 3, life: [0.3, 0.45], size: [0.8, 1.3], rate: 40, path: { progress: [[0, 0], [1, 1]], bend: { x: [-0.2, 0.2], y: [0.2, 0.5] } },
    color: [[0, GLD], [1, EMB]], alpha: [[0, 0], [0.3, 0.35], [1, 0]] },
  { name: "L04 SourceFlare", mat: "starGold", fudge: -7, life: 0.3, size: [0.8, 1.2], rate: 8, path: { progress: [[0, 0], [1, 0]] }, alpha: [[0, 0.9], [1, 0]] },
  { name: "L05 SourceGlow", mat: "glowGold", fudge: 2, life: 0.4, size: 1.6, rate: 5, path: { progress: [[0, 0], [1, 0]] }, alpha: [[0, 0], [0.4, 0.45], [1, 0]] },
  { name: "L06 SinkGlow", mat: "glowEmber", fudge: 2, life: 0.4, size: 2.0, rate: 5, path: { progress: [[0, 1], [1, 1]] }, alpha: [[0, 0], [0.4, 0.5], [1, 0]] },
  { name: "L07 Motes", mat: "dotGold", fudge: -7, life: [0.4, 0.7], size: [0.05, 0.1], rate: 40, path: { progress: [[0, 0], [1, 1]], bend: { x: [-0.8, 0.8], y: [-0.4, 0.9] } },
    color: [[0, GLD], [1, EMB]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
];
const BURST = [
  ...magicCircle("B0", R, [0, 0, 0], 0, 1.6, "Ora", 0.1),
  ...fxCrack("B1", [0, 0, 0], 0.08, 9, 1.6),
  { name: "B2 Scorch", mat: "scorch", mode: "mesh", mesh: "quad", fudge: 46, pos: [0, 0.03, 0], life: 1.6, size: 9, rot3: [0, [0, TAU], 0], bursts: [[0.08, 1]],
    sizeLife: [[0, 0.4, 0, 4], [0.2, 1]], alpha: [[0, 0], [0.05, 0.8], [0.8, 0.7], [1, 0]] },
  // anticipation: the chest lantern swells, then bursts at 0.1
  { name: "M0 Swell", mat: "glowEmber", fudge: 1, pos: [0, CHEST, -0.6], life: 0.14, size: 3, bursts: [[0, 1]], sizeLife: [[0, 0.3], [1, 1.4]], alpha: [[0, 0.3], [1, 0.8]] },
  ...fxShockE("M1", [0, 0, 0], 0.1, 2 * R + 2, 0.6, 0.6), ...fxShockE("M1b", [0, 0, 0], 0.26, 2 * R * 0.75, 0.5, 0.5),
  { name: "M2 ChestWave", mat: "shockEmber", mode: "mesh", mesh: "quad", fudge: 29, pos: [0, CHEST, -0.6], life: 0.45, size: 7, rot3: [Math.PI / 2, 0, 0], bursts: [[0.1, 1], [0.22, 1]],
    sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.6], [1, 0]] },
  { name: "M3 Fireball", mat: "flame", fudge: 0, pos: [0, CHEST, -0.6], life: [0.45, 0.75], size: [1.4, 2.4], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "sphere", r: 0.5, th: 1 },
    speed: [3, 7], drag: 4, vel: { y: [0.4, 1.2], space: "world" }, bursts: [[0.1, 20], [0.18, 10]], sizeLife: [[0, 0.4, 0, 3], [1, 1.15]], flip: F44 },
  { name: "M4 AshPillars", mat: "mist", fudge: 34, life: [1.0, 1.5], size: [1.4, 2.4], shape: { type: "circle", r: R * 0.7, th: 0.3, y: 0.2 }, bursts: [[0.12, 18]],
    vel: { y: [3, 6], space: "world" }, drag: 1.2, rot: [0, TAU], alpha: [[0, 0], [0.12, 0.65], [1, 0]], sizeLife: [[0, 0.5], [1, 1.4]], flip: F44 },
  ...fxPillars("M5", [0, 0, 0], 0.12, 10, R * 0.8, 5, "pillarOra"),
  ...fxFlashE("T1", [0, CHEST, -0.6], 0.1, 4),
  ...fxRays("T2", [0, CHEST, -0.6], 0.11, 20, [10, 16], "streakOra", 4.5),
  ...fxSparks("T3", [0, CHEST, -0.6], 0.1, 50, [6, 13]),
  ...fxAshBurst("T4", [0, CHEST, -0.6], 0.1, 40, [5, 10]),
  ...fxFlakes("T5", [0, 0.2, 0], 0.3, 50, R, [1.0, 1.6], [1, 2.5]),
  ...fxEmbers("T6", [0, 1, 0], 0.2, 50, R * 0.6),
];
const ENRAGE = [
  { name: "E1 BodyFlames", mat: "flame", fudge: 0, pos: [0, 2.6, 0], life: [0.35, 0.6], size: [0.8, 1.4], rot: [0, TAU], rotZ: [-3, 3], shape: { type: "sphere", r: 1.6, th: 0.4 }, rate: 46,
    vel: { y: [1.2, 2.4], space: "world" }, sizeLife: [[0, 0.4, 0, 3], [1, 1]], alpha: [[0, 0], [0.15, 0.85], [1, 0]], flip: F44 },
  { name: "E2 RisingSparks", mat: "streakOra", mode: "stretch", vs: 0.07, ls: 1.8, fudge: -5, pos: [0, 2.6, 0], life: [0.5, 0.9], size: [0.04, 0.08], shape: { type: "sphere", r: 1.6, th: 0.5 },
    rate: 30, vel: { y: [2, 4], space: "world" }, noise: { s: 0.8, f: 1.5, sp: 0.8 }, alpha: [[0, 1], [1, 0]] },
  { name: "E3 HeatMist", mat: "mist", fudge: 35, life: [1.4, 2.0], size: [1.4, 2.4], shape: { type: "circle", r: 1.6, th: 0.5, y: 0.3 }, rate: 6, vel: { y: [0.5, 1.2], space: "world" },
    rot: [0, TAU], alpha: [[0, 0], [0.25, 0.5], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
  { name: "E4 GroundScorch", mat: "crack", mode: "mesh", mesh: "quad", fudge: 44, pos: [0, 0.04, 0], life: GL, size: 5.5, rot3: [0, [0, TAU], 0], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]],
    alpha: [[0, 0], [0.5, 0.75], [1, 0]] },
  { name: "E5 GroundRing", mat: "lineOra", mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: GL, size: 2.8, rotLife: [0, 0.8, 0], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]],
    alpha: [[0, 0], [0.5, 0.75], [1, 0]] },
  { name: "E6 CoreGlow", mat: "glowEmber", fudge: 2, pos: [0, CHEST, -0.6], life: 0.8, size: 2.4, rate: 3, bursts: [[0, 1]], alpha: [[0, 0], [0.3, 0.5], [1, 0]] },
  { name: "E7 Flakes", mat: "flake", fudge: 12, life: [1.2, 1.8], size: [0.08, 0.16], shape: { type: "circle", r: 2.4, th: 1, y: 0.3 }, rate: 20, vel: { y: [0.8, 1.8], space: "world" },
    noise: { s: 0.6, f: 1, sp: 0.6 }, rot: [0, TAU], rotZ: [-3, 3], alpha: [[0, 0], [0.1, 1], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "E8 Embers", mat: "dotFire", fudge: -6, life: [1, 1.6], size: [0.04, 0.09], rate: 20, shape: { type: "circle", r: 2.8, th: 1, y: 0.1 }, vel: { y: [0.6, 1.6] },
    color: [[0, YEL], [0.5, ORA], [1, CRIM]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
];
const SCENE = {
  name: "AS_Absorb", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "beam", role: "link", loop: false, duration: 1.0, layers: BEAM },
    { id: "burst", role: "static", loop: false, duration: 2.0, layers: BURST },
    { id: "enrage", role: "attached", loop: true, duration: GL, layers: ENRAGE },
  ],
  demo: Object.assign({ interval: 4.5, dummy: { height: 7.2 } }, DEMO_BG, { scenarios: {
    beam: { label: "장치 흡수", source: [6, 1.4, 6], target: [0, CHEST, 0], path: [[0, [0, 0, 0], 225]], targets: [[6, 0, 6, 45]], hits: [],
      place: { burst: { start: 99 }, enrage: { start: 99 } }, camera: cam([3, 2.5, 3], 15, -100, 22) },
    burst: sc("변신 폭발 + 광폭화", { path: [[0, [0, 0, 0], 0]], place: { burst: { at: [0, 0, 0], start: 0.2 }, enrage: { start: 1.2 }, beam: { start: 99 } }, camera: cam([0, 3, 0], 24, -70, 20) }),
  } }),
};
