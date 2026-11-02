// AS_Desperate — 발악 (장치 0개가 된 직후 1회). scream: 보스 중심 원 r 10, 8방향 균열(길이 10 · 폭 3)에서 잿불 분출 + 비명 충격파.
// 서버는 0.5초 예고 뒤 scream 을 재생, 두 번째는 yaw +22.5° 로 한 번 더 (사이 각도 8방향).
const R = 10;
var EXTRA_MESHES = Object.assign({
  cracks: radialStrips(8, 0.07, 0.11), cracksWide: radialStrips(8, 0.05, 0.3), cracksCore: radialStrips(8, 0.07, 0.05),
}, MC_MESHES);
const DIRS = Array.from({ length: 8 }, (_, k) => k * 45 * Math.PI / 180);
const eruptions = [];
DIRS.forEach((a, k) => [0.3, 0.55, 0.8, 0.97].forEach((f, j) => {
  const p = [Math.sin(a) * R * f, 0.1, Math.cos(a) * R * f], t = 0.04 + f * 0.16;
  eruptions.push({ name: `E${k}${j} Erupt`, mat: "flame", fudge: 0, pos: p, life: [0.4, 0.7], size: [1.2, 2.2], rot: [0, TAU], rotZ: [-4, 4], shape: { type: "sphere", r: 0.8, th: 1 },
    speed: [0.5, 1.5], drag: 3, vel: { y: [4, 8], space: "world" }, bursts: [[t, 6]], sizeLife: [[0, 0.3, 0, 3], [1, 1.1]], flip: F44 });
  if (j % 2 === 1) eruptions.push({ name: `E${k}${j} Shards`, mat: "shard", mode: "stretch", vs: 0.08, ls: 1.4, fudge: 25, pos: p, life: [0.5, 0.9], size: [0.12, 0.24],
    shape: { type: "sphere", r: 0.6, th: 1 }, speed: [1, 3], vel: { y: [4, 7], space: "world" }, gravity: 1.5, bursts: [[t, 5]], alpha: [[0, 1], [0.8, 1], [1, 0]] });
}));
const SCREAM = [
  // base: scorch + fissures (shoot outward), center crack, rune ring flare
  { name: "B1 Scorch", mat: "scorch", mode: "mesh", mesh: "cracksWide", fudge: 46, pos: [0, 0.02, 0], life: 1.4, size: R, bursts: [[0, 1]],
    sizeLife: [[0, 0.1, 0, 5], [0.18, 1], [1, 1]], alpha: [[0, 0], [0.05, 0.9], [0.7, 0.8], [1, 0]] },
  { name: "B2 Fissure", mat: "fissure", mode: "mesh", mesh: "cracks", fudge: 40, pos: [0, 0.04, 0], life: 1.25, size: R, bursts: [[0, 1]],
    sizeLife: [[0, 0.08, 0, 5], [0.16, 1], [1, 1]], alpha: [[0, 0], [0.04, 1], [0.55, 0.85], [1, 0]] },
  { name: "B3 FissureCore", mat: "fissureHot", mode: "mesh", mesh: "cracksCore", fudge: 39, pos: [0, 0.05, 0], life: 0.7, size: R, bursts: [[0.02, 1]],
    sizeLife: [[0, 0.08, 0, 5], [0.25, 1], [1, 1.02]], alpha: [[0, 0], [0.05, 1], [1, 0]] },
  ...fxCrack("B4", [0, 0, 0], 0, 7, 1.3),
  { name: "B5 RuneFlare", mat: "runeOra", mode: "mesh", mesh: "mcRunesOut", fudge: 50, pos: [0, 0.05, 0], life: 0.7, size: 3.2, rotLife: [0, 2.2, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.5, 0, 3], [0.3, 1], [1, 1.25]], alpha: [[0, 0], [0.08, 1], [1, 0]] },
  { name: "B6 Ring", mat: "lineOra", mode: "mesh", mesh: "mcRing1", fudge: 51, pos: [0, 0.05, 0], life: 0.55, size: 2.6, rotLife: [0, -1.5, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.4], [1, 1.6]], alpha: [[0, 1], [1, 0]] },
  // mid: scream shock waves (center), eruptions along the 8 cracks, end pillars
  ...fxShockE("M1", [0, 0, 0], 0, 2 * R + 2, 0.6, 0.55), ...fxShockE("M1b", [0, 0, 0], 0.14, 2 * R * 0.7, 0.5, 0.45),
  { name: "M2 ScreamWave", mat: "shockEmber", mode: "mesh", mesh: "quad", fudge: 29, pos: [0, 1.4, 0], life: 0.45, size: 6, rot3: [Math.PI / 2, 0, 0], bursts: [[0, 1], [0.12, 1]],
    sizeLife: [[0, 0.2, 0, 3], [1, 1]], alpha: [[0, 0.45], [1, 0]] },
  ...eruptions,
  ...fxPillars("M3", [0, 0, 0], 0.17, 8, R * 0.95, 4, "pillarOra"),
  { name: "M4 DarkMist", mat: "mist", fudge: 35, pos: [0, 0.8, 0], life: [0.9, 1.3], size: [1.8, 3], shape: { type: "sphere", r: 1, th: 0.5 }, bursts: [[0.02, 16]],
    vel: { rad: [3, 6], y: [0.2, 0.8], space: "world" }, drag: 2, rot: [0, TAU], rotZ: [-0.6, 0.6], alpha: [[0, 0], [0.15, 0.6], [1, 0]], sizeLife: [[0, 0.6], [1, 1.4]], flip: F44 },
  // top: flash, rays, sparks, flakes, embers
  ...fxFlashE("T1", [0, 1.6, 0], 0, 3.2),
  ...fxRays("T2", [0, 1.3, 0], 0.01, 18, [10, 16], "streakOra", 4.5),
  ...fxSparks("T3", [0, 1.0, 0], 0, 40, [6, 13]),
  ...fxFlakes("T4", [0, 0.2, 0], 0.15, 60, R, [1.0, 1.8], [0.8, 2.2]),
  ...fxEmbers("T5", [0, 0.4, 0], 0.1, 50, R * 0.6),
  ...fxAshBurst("T6", [0, 1.2, 0], 0, 24, [4, 9]),
];
const SCENE = {
  name: "AS_Desperate", bloom: BLOOM, tonemapping: "neutral",
  scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: [{ id: "scream", role: "static", loop: false, duration: 1.6, layers: SCREAM }],
  demo: Object.assign({ interval: 3.2, dummy: { height: 7.2 } }, DEMO_BG, { scenarios: {
    base: { label: "발악 1파", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0], hits: [], targets: [[4, 0, 6, 200]],
      place: { scream: { at: [0, 0, 0], start: 0.3 } }, camera: cam([0, 1, 0], 30, -90, 45) },
    close: { label: "가까이", from: [0, 0, 0], to: [0, 0, 0], normal: [0, 1, 0], flight: 0.01, anchor: [0, 0, 0], hits: [], targets: [[4, 0, 6, 200]],
      place: { scream: { at: [0, 0, 0], start: 0.3 } }, camera: cam([0, 1.5, 3], 14, -60, 25) },
  } }),
};
