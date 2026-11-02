// AS_Barrage — 50% 그림자 탄막 (25초, 보스 무적·회복 없음). systems:
//   start(static 보스 발밑 r 4, 1.8초: 떠오르며 촛불 고리가 확 타오름) · aura(attached 보스 루프: 도는 재 조각·고리 불꽃, 서버가 탄막 끝에 정지)
//   shard(projectile: 서버가 2틱마다 이동, 높이 1칸 수평 탄) · pop(static r 0.6, 탄이 맞거나 사라질 때)
const R = 4, GL = 2.0, HEAD = 6.2;
var EXTRA_MESHES = Object.assign({
  blade: merge([grid(1, 1, (u, v) => [(v - 0.5) * 0.22, 0, (u - 0.5)]), grid(1, 1, (u, v) => [0, (v - 0.5) * 0.22, (u - 0.5)])]),
  haloV: grid(96, 1, (u, v) => { const p = u * TAU, rr = 1 - 0.04 + v * 0.08; return [Math.cos(p) * rr, Math.sin(p) * rr, 0]; }),
}, MC_MESHES);
const START = [
  ...magicCircle("B0", R, [0, 0, 0], 0, 1.8, "Ora", 1.15),
  { name: "B1 Scorch", mat: "crack", mode: "mesh", mesh: "quad", fudge: 44, pos: [0, 0.04, 0], life: 1.8, size: 5.5, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.4, 0, 3], [0.2, 1]], alpha: [[0, 0], [0.1, 0.8], [0.8, 0.7], [1, 0]] },
  { name: "M1 AshVortex", mat: "mist", fudge: 35, life: [0.9, 1.3], size: [2.0, 3.2], shape: { type: "circle", r: 3.2, th: 0.3, y: 0.3 }, rate: 14, bursts: [[0, 6]],
    vel: { orb: [3, 4], rad: [-1, -0.4], y: [1.5, 3] }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.55], [1, 0]], sizeLife: [[0, 0.6], [1, 1.2]], flip: F44 },
  { name: "M2 FlakeSpiral", mat: "flake", fudge: 12, life: [1.0, 1.6], size: [0.1, 0.2], shape: { type: "circle", r: 3.4, th: 0.2, y: 0.2 }, rate: 40,
    vel: { orb: [4, 6], rad: [-1.2, -0.6], y: [2.5, 4.5] }, rot: [0, TAU], rotZ: [-4, 4], alpha: [[0, 0], [0.1, 1], [0.8, 0.9], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "M3 HaloRing", mat: "lineOra", mode: "mesh", mesh: "haloV", fudge: 4, pos: [0, HEAD, 0.9], life: 1.8, size: 1.6, rotLife: [0, 0, 1.2], bursts: [[0, 1]],
    sizeLife: [[0, 0.6], [0.6, 1], [0.66, 1.35], [0.8, 1.25], [1, 1.2]], alpha: [[0, 0], [0.1, 0.9], [0.9, 0.9], [1, 0]] },
  { name: "M4 HaloFlames", mat: "candle", fudge: 3, pos: [0, HEAD, 0.9], life: [0.35, 0.55], size: [0.5, 0.9], shape: { type: "sphere", r: 1.7, th: 0.05 }, rate: 22,
    bursts: [[1.15, 26]], vel: { y: [1, 2], space: "world" }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  ...fxPillars("M5", [0, 0, 0], 1.15, 8, R * 0.85, 4.5, "pillarOra"),
  ...fxShockE("M6", [0, 0, 0], 1.15, 2 * R + 2, 0.55, 0.55),
  ...fxFlashE("T1", [0, HEAD, 0.9], 1.15, 3.2),
  ...fxRays("T2", [0, HEAD, 0.9], 1.17, 16, [9, 14], "streakOra", 4),
  ...fxSparks("T3", [0, 2, 0], 1.15, 30, [5, 10]),
  ...fxEmbers("T4", [0, 0.5, 0], 0.4, 40, 3),
];
const AURA = [
  { name: "A1 OrbitShards", mat: "shardHot", mode: "stretch", vs: 0.05, ls: 1.6, fudge: 24, life: [1.2, 1.8], size: [0.16, 0.3], shape: { type: "circle", r: 2.8, th: 0.15, y: 2.6 },
    rate: 14, bursts: [[0, 10]], vel: { orb: [4, 6], y: [-0.6, 0.8] }, alpha: [[0, 0], [0.15, 1], [0.85, 1], [1, 0]] },
  { name: "A2 OrbitFlakes", mat: "flake", fudge: 12, life: [1.4, 2.0], size: [0.08, 0.16], shape: { type: "circle", r: 3.2, th: 0.4, y: 1.5 }, rate: 26,
    vel: { orb: [2.5, 4], y: [0.3, 1.2] }, rot: [0, TAU], rotZ: [-3, 3], alpha: [[0, 0], [0.1, 1], [0.8, 0.9], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "A3 HaloFire", mat: "candle", fudge: 3, pos: [0, HEAD, 0.9], life: [0.3, 0.5], size: [0.45, 0.8], shape: { type: "sphere", r: 1.9, th: 0.05 }, rate: 30,
    vel: { y: [0.8, 1.6], space: "world" }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "A4 HaloRing", mat: "lineOra", mode: "mesh", mesh: "haloV", fudge: 4, pos: [0, HEAD, 0.9], life: GL, size: 2.1, rotLife: [0, 0, 1.5], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]],
    alpha: [[0, 0], [0.5, 0.95], [1, 0]] },
  { name: "A5 GroundRunes", mat: "runeOra", mode: "mesh", mesh: "mcRunesOut", fudge: 50, pos: [0, 0.05, 0], life: GL, size: 3.4, rotLife: [0, 0.5, 0], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]],
    alpha: [[0, 0], [0.5, 0.75], [1, 0]] },
  { name: "A6 GroundRing", mat: "lineOra", mode: "mesh", mesh: "mcRing1", fudge: 51, pos: [0, 0.05, 0], life: GL, size: 3.6, rotLife: [0, -0.4, 0], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]],
    alpha: [[0, 0], [0.5, 0.85], [1, 0]] },
  { name: "A7 Mist", mat: "mist", fudge: 35, life: [1.5, 2.2], size: [1.6, 2.6], shape: { type: "circle", r: 2, th: 0.5, y: 0.4 }, rate: 5, vel: { orb: [1, 2], y: [0.2, 0.6] },
    rot: [0, TAU], alpha: [[0, 0], [0.3, 0.5], [1, 0]], flip: F44 },
  { name: "A8 Embers", mat: "dotFire", fudge: -6, life: [1, 1.8], size: [0.04, 0.09], rate: 24, shape: { type: "circle", r: 3, th: 1, y: 0.2 }, vel: { y: [0.8, 2] },
    noise: { s: 0.8, f: 1, sp: 0.6 }, color: [[0, YEL], [0.5, ORA], [1, CRIM]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
];
const SHARD = [
  { name: "P01 Body", mat: "shardHot", mode: "mesh", mesh: "blade", fudge: 0, loop: true, rate: 30, life: 0.08, size: 1.15, alpha: [[0, 1], [1, 1]] },
  { name: "P02 Glow", mat: "glowEmber", fudge: 2, loop: true, rate: 12, life: 0.25, size: 1.4, alpha: [[0, 0], [0.3, 0.7], [1, 0]] },
  { name: "P02b Rim", mat: "fissureHot", mode: "mesh", mesh: "blade", fudge: -1, loop: true, rate: 30, life: 0.08, size: 1.3, alpha: [[0, 0.55], [1, 0.55]] },
  { name: "P03 Core", mat: "starOra", fudge: -2, loop: true, rate: 16, life: 0.15, size: [0.55, 0.75], rot: [0, TAU], alpha: [[0, 0.9], [1, 0.4]] },
  { name: "P04 TrailFire", mat: "flame", fudge: 1, loop: true, world: true, rateDist: 9, life: [0.18, 0.3], size: [0.25, 0.45], rot: [0, TAU], shape: { type: "sphere", r: 0.08, th: 1 },
    sizeLife: [[0, 1], [1, 0.3]], flip: F44 },
  { name: "P05 TrailAsh", mat: "flake", fudge: 12, loop: true, world: true, rateDist: 5, life: [0.4, 0.7], size: [0.06, 0.12], shape: { type: "sphere", r: 0.12, th: 1 },
    vel: { y: [0.2, 0.6] }, rot: [0, TAU], alpha: [[0, 1], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "P06 TrailMist", mat: "mist", fudge: 30, loop: true, world: true, rateDist: 2, life: [0.35, 0.6], size: [0.35, 0.6], rot: [0, TAU], alpha: [[0, 0], [0.2, 0.45], [1, 0]], flip: F44 },
];
const POP = [...fxFlashE("I1", [0, 0, 0], 0, 1.0), ...fxSparks("I2", [0, 0, 0], 0, 12, [3, 6], "streakOra", 0.15), ...fxAshBurst("I3", [0, 0, 0], 0, 8, [2, 4]),
  { name: "I4 Ring", mat: "shockEmber", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0, 0], life: 0.3, size: 1.4, rot3: [Math.PI / 2, 0, 0], bursts: [[0, 1]], sizeLife: [[0, 0.2], [1, 1]], alpha: [[0, 0.6], [1, 0]] }];
const SCENE = {
  name: "AS_Barrage", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "start", role: "static", loop: false, duration: 2.2, layers: START },
    { id: "aura", role: "attached", loop: true, duration: GL, layers: AURA },
    { id: "shard", role: "projectile", loop: true, duration: 60, layers: SHARD },
    { id: "pop", role: "static", loop: false, duration: 0.6, layers: POP },
  ],
  demo: Object.assign({ interval: 4.2, dummy: { height: 7.2 } }, DEMO_BG, { scenarios: {
    start: sc("탄막 시작 + 오라", { path: [[0, [0, 0, 0], 0]], fire: { at: 99, stop: [] }, place: { start: { at: [0, 0, 0], start: 0 }, aura: { start: 1.3 }, pop: { start: 99 } }, camera: cam([0, 3, 0], 22, -70, 18) }),
    shard: { label: "탄", path: [[0, [0, 0, 0], 0]], fire: { at: 0.2, stop: [] }, flightPath: [[-6, 1, 3], [6, 1, 3]], flight: 1.4, normal: [0, 1, 0], anchor: [0, 0, 0], targets: [[7, 0, 3, 270]], hits: [],
      place: { start: { start: 99 }, aura: { start: 99 }, pop: { at: [6, 1, 3], start: 1.6 } }, camera: cam([0, 1, 3], 11, -90, 20) },
  } }),
};
