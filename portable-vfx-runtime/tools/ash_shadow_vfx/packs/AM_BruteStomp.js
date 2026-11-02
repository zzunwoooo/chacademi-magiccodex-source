// AM_BruteStomp — 촛농 짐승 앞발 내려찍기. static 원 r 4.5, 1.2초. 원점 = 짐승 앞발 착지점(서버가 예고 원 중심에서 재생), 발동 = 0초.
const R = 4.5;
var EXTRA_MESHES = Object.assign({ crackLine: strip(0.035, 0.0) }, MC_MESHES);
function stomp(s) {
  const radial = Array.from({ length: 8 }, (_, k) => ({ name: "B4 Radial" + k, mat: "aLine" + s, mode: "mesh", mesh: "crackLine", fudge: 46, pos: [0, 0.05, 0], life: 0.8, size: R * (0.8 + 0.2 * (k % 2)),
    rot3: [0, k * Math.PI / 4 + 0.2, 0], bursts: [[0.02, 1]], sizeLife: [[0, 0.1, 0, 4], [0.25, 1]], alpha: [[0, 0], [0.06, 0.9], [0.5, 0.6], [1, 0]] }));
  return [
    // base: scorched crack, line-work rings + sigil, radial crack lines, dust wave
    ...aCrackD("B1", [0, 0, 0], 0, 6.5, s, 1.2),
    ...aRings("B2", R, [0, 0, 0], 0, 1.0, s),
    ...aDustWave("B3", [0, 0, 0], 0.02, 2 * R + 2.5, s, 0.8),
    ...radial,
    // mid: shock waves, flame tongues around the ring, wax droplets, low ash cloud
    ...aShock("M1", [0, 0, 0], 0, 2 * R + 0.8, s, 0.45),
    ...aShock("M2", [0, 0, 0], 0.16, 2 * R + 2.0, s, 0.5),
    ...aFlames("M3", [0, 0.2, 0], 0.03, 12, s, 0, [0.8, 1.4], [1.5, 3.0], { type: "circle", r: R * 0.7, th: 0.4, y: 0 }),
    ...aFlames("M4", [0, 0.2, 0], 0.0, 6, s, 0.6, [1.0, 1.6], [1.0, 2.2]),
    { name: "M5 Wax", mat: "aWax" + s, fudge: 20, pos: [0, 0.4, 0], life: [0.6, 1.0], size: [0.16, 0.32], shape: { type: "sphere", r: 0.7, th: 0.5 },
      speed: [2.5, 5.5], vel: { y: [3.5, 6.5], space: "world" }, gravity: 1.5, drag: 0.6, bursts: [[0.0, 14], [0.08, 6]], alpha: [[0, 1], [0.85, 1], [1, 0]],
      sizeLife: [[0, 0.6], [0.2, 1], [1, 0.8]] },
    ...aMistP("M6", [0, 0.3, 0], 0.03, 10, s, 1.0, [1.6, 2.6], [2.5, 4.5]),
    // top: flash, rays, sparks, embers, ash, smoke of snuffed candles
    ...aFlash("T1", [0, 0.5, 0], 0, 2.8, s),
    ...aRays("T2", [0, 0.4, 0], 0.01, 10, s, [9, 14], 3.5),
    ...aSparks("T3", [0, 0.4, 0], 0, 30, s, [5, 11], 0.6),
    ...aEmbers("T4", [0, 0.4, 0], 0.05, 28, s, R * 0.6),
    ...aFlecks("T5", [0, 0.5, 0], 0.02, 20, s, 1.2),
    ...aShards("T6", [0, 0.3, 0], 0, 12, s, 0.5, [4, 8]),
    { name: "T7 SnuffSmoke", mat: "aMist" + s, fudge: 33, pos: [0, 0.3, 0], life: [0.9, 1.2], size: [0.4, 0.7], shape: { type: "circle", r: R * 0.5, th: 0.8, y: 0 },
      vel: { y: [1.4, 2.4], space: "world" }, noise: { s: 0.6, f: 1.2, sp: 0.6 }, rot: [0, TAU], bursts: [[0.2, 8]], alpha: [[0, 0], [0.2, 0.7], [1, 0]], sizeLife: [[0, 0.5], [1, 1.6]], flip: F44 },
  ];
}
const MATS = AM_MATS;
const SCENE = {
  name: "AM_BruteStomp", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: [
    { id: "stomp", role: "static", loop: false, duration: 1.2, layers: stomp("") },
    { id: "stomp_shiny", role: "static", loop: false, duration: 1.2, layers: stomp("S") },
  ],
  demo: Object.assign({ interval: 2.4 }, DEMO_M, { scenarios: {
    base: sc("기본", { place: { stomp: { at: [0, 0, 0], start: 0.05 }, stomp_shiny: { at: [80, 0, 0], start: 0.05 } }, targets: [[2.5, 0, 2.5, 200]], camera: cam([0, 0.6, 0], 12, -45, 30) }),
    shiny: sc("이로치", { place: { stomp_shiny: { at: [0, 0, 0], start: 0.05 }, stomp: { at: [80, 0, 0], start: 0.05 } }, targets: [[2.5, 0, 2.5, 200]], camera: cam([0, 0.6, 0], 12, -45, 30) }),
  } }),
};
