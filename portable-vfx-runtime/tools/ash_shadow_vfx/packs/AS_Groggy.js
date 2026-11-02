// AS_Groggy — 탄막 성공 → 10초 그로기. fall(static 보스 r 4, 1.2초: 바닥으로 추락, 고리가 떨어져 부서짐) · stun(attached 루프: 재가 새어 나옴, 깨진 고리 불티)
const R = 4, GL = 2.0, HEAD = 4.2;
var EXTRA_MESHES = Object.assign({
  haloV: grid(96, 1, (u, v) => { const p = u * TAU, rr = 1 - 0.04 + v * 0.08; return [Math.cos(p) * rr, Math.sin(p) * rr, 0]; }),
  haloBroken: merge([0, 1, 2, 3].map(k => grid(16, 1, (u, v) => { const p = (k / 4 + u * 0.18) * TAU, rr = 1 - 0.05 + v * 0.1; return [Math.cos(p) * rr, Math.sin(p) * rr, 0]; }))),
}, MC_MESHES);
const FALL = [
  ...fxCrack("B1", [0, 0, 0], 0.08, 7, 1.5),
  { name: "B2 Scorch", mat: "scorch", mode: "mesh", mesh: "quad", fudge: 46, pos: [0, 0.03, 0], life: 1.5, size: 6, rot3: [0, [0, TAU], 0], bursts: [[0.08, 1]],
    sizeLife: [[0, 0.4, 0, 4], [0.2, 1]], alpha: [[0, 0], [0.05, 0.85], [0.8, 0.7], [1, 0]] },
  ...fxShockE("M1", [0, 0, 0], 0.08, 2 * R + 1.5, 0.5, 0.55),
  { name: "M2 DustRing", mat: "mist", fudge: 35, pos: [0, 0.3, 0], life: [0.8, 1.2], size: [1.4, 2.4], shape: { type: "circle", r: 1.2, th: 0.4, y: 0 }, bursts: [[0.08, 18]],
    vel: { rad: [4, 7], y: [0.2, 0.8], space: "world" }, drag: 2.5, rot: [0, TAU], alpha: [[0, 0], [0.15, 0.65], [1, 0]], sizeLife: [[0, 0.5], [1, 1.3]], flip: F44 },
  // the halo drops from head height and breaks: ring pieces + wax shards + snuffed candle smoke
  { name: "M3 HaloDrop", mat: "lineOra", mode: "mesh", mesh: "haloBroken", fudge: 4, pos: [0, 6.2, 0.9], life: 0.5, size: 1.6, rot3: [0.2, 0, 0], rotLife: [0, 0, 3], bursts: [[0, 1]],
    vel: { y: [-8, -8], space: "world" }, sizeLife: [[0, 1], [1, 1.3]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M4 WaxShards", mat: "shardHot", mode: "stretch", vs: 0.08, ls: 1.4, fudge: 25, pos: [0, 2.2, 0.9], life: [0.5, 0.9], size: [0.12, 0.24], shape: { type: "sphere", r: 1.2, th: 0.5 },
    speed: [3, 6], vel: { y: [2, 5], space: "world" }, gravity: 1.6, bursts: [[0.45, 18]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "M5 Snuff", mat: "mist", fudge: 34, pos: [0, 2.0, 0.9], life: [0.9, 1.4], size: [0.6, 1.1], shape: { type: "sphere", r: 1.4, th: 0.3 }, bursts: [[0.5, 10]],
    vel: { y: [1, 2.2], space: "world" }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.6], [1, 0]], sizeLife: [[0, 0.5], [1, 1.4]], flip: F44 },
  { name: "M6 ImpactFire", mat: "flame", fudge: 0, pos: [0, 0.4, 0], life: [0.4, 0.7], size: [1.2, 2.2], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "circle", r: 1.5, th: 0.6, y: 0 },
    speed: [2, 4], drag: 3, vel: { rad: [2, 4], y: [1, 2.5], space: "world" }, bursts: [[0.08, 18]], sizeLife: [[0, 0.3, 0, 3], [1, 1.1]], flip: F44 },
  { name: "M7 AshColumn", mat: "mist", fudge: 33, pos: [0, 0.5, 0], life: [0.9, 1.4], size: [1.6, 2.6], shape: { type: "sphere", r: 0.8, th: 0.5 }, bursts: [[0.1, 12]],
    vel: { y: [2.5, 5], space: "world" }, drag: 1.5, rot: [0, TAU], alpha: [[0, 0], [0.12, 0.7], [1, 0]], sizeLife: [[0, 0.5], [1, 1.4]], flip: F44 },
  ...fxPillars("M8", [0, 0, 0], 0.1, 6, 2.2, 3, "pillarOra"),
  ...fxRays("M9", [0, 0.8, 0], 0.1, 14, [8, 13], "streakOra", 3.5),
  ...fxFlashE("T1", [0, 1.0, 0], 0.08, 3.4),
  ...fxSparks("T2", [0, 0.6, 0], 0.08, 34, [5, 10]),
  ...fxAshBurst("T3", [0, 1.4, 0], 0.08, 30, [4, 8]),
  ...fxEmbers("T4", [0, 2.2, 0.9], 0.45, 30, 1.2),
];
const STUN = [
  { name: "S1 AshLeak", mat: "flake", fudge: 12, life: [1.2, 1.9], size: [0.12, 0.24], shape: { type: "sphere", r: 1.3, th: 0.8 }, pos: [0, 2.2, 0], rate: 40,
    vel: { y: [0.6, 1.5], space: "world" }, noise: { s: 0.6, f: 1, sp: 0.6 }, rot: [0, TAU], rotZ: [-3, 3], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]], flip: { x: 2, y: 2 } },
  { name: "S2 Smoke", mat: "mist", fudge: 35, life: [1.4, 2.0], size: [1.2, 2.0], shape: { type: "sphere", r: 1.1, th: 0.6 }, pos: [0, 1.8, 0], rate: 5,
    vel: { y: [0.4, 0.9], space: "world" }, rot: [0, TAU], alpha: [[0, 0], [0.25, 0.5], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
  { name: "S3 BrokenHalo", mat: "lineOra", mode: "mesh", mesh: "haloBroken", fudge: 4, pos: [0, HEAD, -0.3], life: GL, size: 1.1, rot3: [1.25, 0, 0], rotLife: [0, 0, 1.2], rate: 2 / GL, onStop: 0.25,
    bursts: [[0, 2]], alpha: [[0, 0], [0.2, 0.9], [0.3, 0.35], [0.4, 0.9], [0.62, 0.5], [0.7, 0.9], [1, 0]] },
  { name: "S4 DizzyStars", mat: "starOra", fudge: -7, pos: [0, HEAD + 0.3, -0.3], life: [0.6, 0.9], size: [0.4, 0.7], shape: { type: "circle", r: 1.0, th: 0, y: 0 }, rate: 8,
    vel: { orb: [4, 5] }, sizeLife: [[0, 0], [0.3, 1], [1, 0]] },
  { name: "S5 Sparks", mat: "streakOra", mode: "stretch", vs: 0.06, ls: 1.6, fudge: -5, pos: [0, HEAD, -0.3], life: [0.3, 0.5], size: [0.04, 0.08], shape: { type: "circle", r: 1.1, th: 0, y: 0 },
    rate: 18, speed: [1, 2], gravity: 0.8, alpha: [[0, 1], [1, 0]] },
  { name: "S6 GroundAsh", mat: "scorch", mode: "mesh", mesh: "quad", fudge: 46, pos: [0, 0.03, 0], life: GL, size: 5, rot3: [0, [0, TAU], 0], rate: 2 / GL, onStop: 0.25, bursts: [[0, 2]],
    alpha: [[0, 0], [0.5, 0.6], [1, 0]] },
  { name: "S8 HeartGlow", mat: "glowEmber", fudge: 2, pos: [0, 2.6, -0.6], life: 1.0, size: 2.2, rate: 2, onStop: 0.2, bursts: [[0, 1]], alpha: [[0, 0], [0.15, 0.55], [0.3, 0.2], [0.45, 0.5], [1, 0]] },
  { name: "S9 Drips", mat: "dotFire", fudge: -6, pos: [0, 2.4, -0.4], life: [0.6, 1.0], size: [0.06, 0.12], shape: { type: "sphere", r: 0.9, th: 0.5 }, rate: 12, gravity: 1.2,
    color: [[0, YEL], [0.5, ORA], [1, CRIM]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  { name: "S10 HaloFlicker", mat: "candle", fudge: 3, pos: [0, HEAD, -0.3], life: [0.25, 0.45], size: [0.35, 0.6], shape: { type: "circle", r: 1.1, th: 0, y: 0 }, rate: 10,
    vel: { y: [0.5, 1.0], space: "world" }, alpha: [[0, 0], [0.3, 1], [1, 0]] },
  { name: "S7 Embers", mat: "dotFire", fudge: -6, life: [0.9, 1.5], size: [0.04, 0.08], rate: 14, shape: { type: "circle", r: 2.2, th: 1, y: 0.1 }, vel: { y: [0.3, 0.8] },
    color: [[0, YEL], [0.5, ORA], [1, CRIM]], alpha: [[0, 0], [0.2, 1], [1, 0]] },
];
const SCENE = {
  name: "AS_Groggy", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "fall", role: "static", loop: false, duration: 1.6, layers: FALL },
    { id: "stun", role: "attached", loop: true, duration: GL, layers: STUN },
  ],
  demo: Object.assign({ interval: 4.5, dummy: { height: 6.4 } }, DEMO_BG, { scenarios: {
    base: { label: "그로기", path: [[0, [0, 0, 0], 0]], hits: [], place: { fall: { at: [0, 0, 0], start: 0.2 }, stun: { start: 1.0 } }, camera: cam([0, 2, 0], 16, -60, 18) } } }),
};
