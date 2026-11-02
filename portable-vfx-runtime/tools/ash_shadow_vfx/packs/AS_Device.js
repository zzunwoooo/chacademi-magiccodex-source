// AS_Device — 빛 발산 장치 (모델 gm_light_receiver 재사용). systems:
//   light(attached 장치 루프: 창백한 금빛 맥동) · tether(link 루프 장치→보스 회복 빛줄기, 보스 쪽 끝은 재로 검게)
//   break(static 장치 r 2.5, 1.2초: 빛 유리 파편, 빛이 꺼지며 재) · respawn(static 장치 r 2, 1.5초: 빛 기둥이 내려와 켜짐)
const GL = 2.0, TOP = 1.4;
var EXTRA_MESHES = Object.assign({ thinPillar: pillar(0.14) }, MC_MESHES);
const LIGHT = [
  { name: "L1 Core", mat: "glowPale", fudge: 2, pos: [0, TOP, 0], life: 1.0, size: 2.0, rate: 2, onStop: 0.2, bursts: [[0, 1]], sizeLife: [[0, 0.85], [0.5, 1.1], [1, 0.85]], alpha: [[0, 0], [0.3, 0.6], [1, 0]] },
  { name: "L2 Star", mat: "starGold", fudge: -7, pos: [0, TOP, 0], life: 0.6, size: 1.2, rate: 3, rot: [0, TAU], rotZ: [-0.5, 0.5], alpha: [[0, 0], [0.4, 0.8], [1, 0]] },
  { name: "L3 Beam", mat: "pillarPale", mode: "mesh", mesh: "thinPillar", fudge: 5, pos: [0, 0.2, 0], life: GL, size: 6, rot3: [0, [0, TAU], 0], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]],
    alpha: [[0, 0], [0.5, 0.2], [1, 0]] },
  { name: "L4 Ring", mat: "lineGold", mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: GL, size: 1.6, rotLife: [0, 0.6, 0], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.9], [1, 0]] },
  { name: "L5 Runes", mat: "runeGold", mode: "mesh", mesh: "mcRunesOut", fudge: 51, pos: [0, 0.05, 0], life: GL, size: 1.6, rotLife: [0, -0.4, 0], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.7], [1, 0]] },
  { name: "L6 FloorGlow", mat: "glowPale", mode: "mesh", mesh: "quad", fudge: 70, pos: [0, 0.03, 0], life: GL, size: 4.5, rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.22], [1, 0]] },
  { name: "L7 Motes", mat: "dotGold", fudge: -6, life: [1.2, 2.0], size: [0.04, 0.09], rate: 18, shape: { type: "circle", r: 1.2, th: 0.8, y: 0.2 }, vel: { y: [0.8, 1.8] },
    noise: { s: 0.4, f: 1, sp: 0.5 }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "L8 Glints", mat: "sparkGold", mode: "stretch", vs: 0.06, ls: 1.8, fudge: -5, pos: [0, TOP, 0], life: [0.4, 0.7], size: [0.04, 0.07], shape: { type: "sphere", r: 0.3, th: 1 },
    rate: 10, speed: [0.8, 1.6], vel: { y: [0.6, 1.2] }, alpha: [[0, 1], [1, 0]] },
];
const PROG = [[0, 0, 0, 1.2], [1, 1, 0.7, 0]];
const TETHER = [
  { name: "T1 Flow", mat: "beamGold", mode: "stretch", vs: 0.0, ls: 3.2, fudge: -3, life: [0.5, 0.7], size: [0.1, 0.16], rate: 90, onStop: 0.15, bursts: [[0, 14]],
    path: { progress: PROG, bend: { x: [-0.1, 0.1], y: [0.1, 0.4] }, wave: { amp: [0.05, 0.15], cycles: [1, 2], phase: [0, 1], vr: 0.4 } },
    color: [[0, GLD], [0.75, GLD], [1, [0.35, 0.22, 0.12]]], alpha: [[0, 0], [0.12, 0.85], [0.85, 0.7], [1, 0]] },
  { name: "T2 Thread", mat: "glowPale", fudge: -2, life: 0.5, size: 0.5, rate: 110, onStop: 0.1,
    path: { progress: [[0, 0], [1, 1]], bend: { x: [-0.05, 0.05], y: [0.25, 0.3] } }, alpha: [[0, 0], [0.2, 0.22], [0.8, 0.22], [1, 0]] },
  { name: "T3 Motes", mat: "dotGold", fudge: -7, life: [0.7, 1.1], size: [0.04, 0.08], rate: 20, path: { progress: [[0, 0], [1, 1]], bend: { x: [-0.4, 0.4], y: [-0.2, 0.6] } },
    alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "T4 SinkAsh", mat: "flake", fudge: 12, life: [0.5, 0.8], size: [0.08, 0.14], rate: 10, path: { progress: [[0, 0.8], [1, 1]], bend: { x: [-0.3, 0.3], y: [0, 0.5] } },
    rot: [0, TAU], rotZ: [-4, 4], alpha: [[0, 0], [0.3, 1], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "T5 SourceGlow", mat: "glowPale", fudge: 2, life: 0.6, size: 1.2, rate: 4, path: { progress: [[0, 0], [1, 0]] }, alpha: [[0, 0], [0.5, 0.4], [1, 0]] },
];
const BREAK = [
  ...fxFlashE("B1", [0, TOP, 0], 0, 2.8, "starGold", "glowGold"),
  { name: "B2 Glass", mat: "sparkGold", mode: "stretch", vs: 0.08, ls: 1.5, fudge: -5, pos: [0, TOP, 0], life: [0.4, 0.8], size: [0.07, 0.14], shape: { type: "sphere", r: 0.4, th: 1 },
    speed: [4, 9], drag: 2, gravity: 1, bursts: [[0, 40]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
  { name: "B3 Glints", mat: "starGold", fudge: -7, pos: [0, TOP, 0], life: [0.3, 0.6], size: [0.25, 0.5], shape: { type: "sphere", r: 0.4, th: 1 }, speed: [2, 5], drag: 2, gravity: 0.8,
    bursts: [[0.02, 14]], sizeLife: [[0, 1], [1, 0]] },
  { name: "B4 BeamCollapse", mat: "pillarPale", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0.2, 0], life: 0.45, size: 6, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 1], [1, 0.05]], alpha: [[0, 0.8], [1, 0]] },
  { name: "B5 Shock", mat: "shockGoldA", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], life: 0.45, size: 5.5, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.6], [1, 0]] },
  // the light dies: ash and dark smoke take its place
  { name: "B6 Smoke", mat: "mist", fudge: 35, pos: [0, 0.9, 0], life: [0.9, 1.3], size: [1.2, 2.0], shape: { type: "sphere", r: 0.6, th: 0.5 }, bursts: [[0.12, 12]],
    vel: { y: [1, 2.2], rad: [0.5, 1.2], space: "world" }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.7], [1, 0]], sizeLife: [[0, 0.5], [1, 1.4]], flip: F44 },
  ...fxFlakes("B7", [0, 0.3, 0], 0.15, 30, 1.6, [1.0, 1.6], [0.8, 2]),
  ...fxCrack("B8", [0, 0, 0], 0.05, 3.5, 1.2),
  { name: "B9 RingOut", mat: "lineGold", mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: 0.6, size: 1.6, bursts: [[0, 1]], sizeLife: [[0, 1], [1, 2.4]], alpha: [[0, 1], [1, 0]] },
];
const RESPAWN = [
  ...magicCircle("R0", 2, [0, 0, 0], 0, 1.5, "Gold", 0.95),
  { name: "R1 Descend", mat: "pillarPale", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.7, size: 9, rot3: [0, [0, TAU], 0], bursts: [[0.25, 1]],
    sizeLife: [[0, 0.3, 0, 3], [0.4, 1], [1, 1]], alpha: [[0, 0], [0.2, 0.9], [1, 0]] },
  { name: "R2 Gather", mat: "dotGold", fudge: -7, life: [0.5, 0.7], size: [0.05, 0.1], shape: { type: "sphere", r: 2.5, th: 0.2 }, pos: [0, TOP, 0], bursts: [[0.1, 30]],
    vel: { rad: [-4.5, -3.5] }, alpha: [[0, 0], [0.3, 1], [1, 0]] },
  ...fxFlashE("R3", [0, TOP, 0], 0.95, 2.6, "starGold", "glowGold"),
  { name: "R4 Shock", mat: "shockGoldA", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], life: 0.45, size: 4.5, rot3: [0, [0, TAU], 0], bursts: [[0.95, 1]],
    sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.55], [1, 0]] },
  { name: "R5 Sparks", mat: "sparkGold", mode: "stretch", vs: 0.07, ls: 1.8, fudge: -5, pos: [0, TOP, 0], life: [0.4, 0.7], size: [0.05, 0.1], shape: { type: "sphere", r: 0.3, th: 1 },
    speed: [3, 7], drag: 2.5, bursts: [[0.95, 26]], alpha: [[0, 1], [1, 0]] },
  { name: "R6 Motes", mat: "dotGold", fudge: -6, life: [1, 1.6], size: [0.04, 0.09], shape: { type: "circle", r: 1.6, th: 1, y: 0.1 }, bursts: [[0.95, 24]], vel: { y: [0.8, 1.8], space: "world" },
    alpha: [[0, 0], [0.2, 1], [1, 0]] },
];
const SCENE = {
  name: "AS_Device", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "light", role: "attached", loop: true, duration: GL, layers: LIGHT },
    { id: "tether", role: "link", loop: true, duration: GL, layers: TETHER },
    { id: "break", role: "static", loop: false, duration: 1.6, layers: BREAK },
    { id: "respawn", role: "static", loop: false, duration: 1.8, layers: RESPAWN },
  ],
  demo: Object.assign({ interval: 4.5, dummy: { height: 1.6 } }, DEMO_BG, { scenarios: {
    light: { label: "켜짐 + 회복 줄기", source: [0, TOP, 0], target: [-8, 3.6, 6], path: [[0, [0, 0, 0], 0]], targets: [[-8, 0, 6, 120]], hits: [],
      place: { break: { start: 99 }, respawn: { start: 99 } }, camera: cam([-3, 2, 3], 15, -120, 25) },
    brk: { label: "파괴 → 재점화", source: [0, TOP, 0], target: [-8, 3.6, 6], path: [[0, [0, 0, 0], 0]], targets: [[-8, 0, 6, 120]], hits: [],
      stops: { light: 0.3, tether: 0.3 }, place: { break: { at: [0, 0, 0], start: 0.3 }, respawn: { at: [0, 0, 0], start: 2.3 } }, camera: cam([0, 1.2, 0], 9, -80, 22) },
  } }),
};
