// AM_HoundBite — 재 사냥개 물어뜯기 (몸통 박치기 = 예고 없음). static 부채꼴 r 3.5, 100°, 0.6초. 원점 = 사냥개 발밑, +Z = 정면. 맞물림 0.1초.
const R = 3.5;
var EXTRA_MESHES = Object.assign({ biteArc: sector(100, 0.0) }, MC_MESHES);
function bite(s) {
  const P = [0, 0.85, 1.9], C = 0.1;
  return [
    // base: reach arc on the ground + scorch
    { name: "B1 Arc", mat: "aArc" + s, mode: "mesh", mesh: "biteArc", fudge: 40, pos: [0, 0.04, 0], life: 0.38, size: R, bursts: [[0.02, 1]],
      sizeLife: [[0, 0.45, 0, 3], [0.35, 1]], alpha: [[0, 0.6], [0.5, 0.4], [1, 0]] },
    ...aCrackD("B2", [0, 0, 2.1], C, 2.4, s, 0.6),
    ...aDustWave("B3", [0, 0, 1.9], C, 3.0, s, 0.5),
    // mid: upper + lower ember fangs snapping shut
    { name: "M1 FangUp", mat: "aFang" + s, mode: "mesh", mesh: "quad", fudge: -3, pos: [0, 1.45, 1.9], life: 0.26, size: 1.15, rot3: [Math.PI / 2, 0, 0], bursts: [[0, 1]],
      vel: { y: [-6, -6], space: "world" }, drag: 14, sizeLife: [[0, 0.6, 0, 3], [0.35, 1.05], [1, 1.15]], alpha: [[0, 0], [0.12, 1], [0.6, 0.9], [1, 0]] },
    { name: "M2 FangDown", mat: "aFang" + s, mode: "mesh", mesh: "quad", fudge: -3, pos: [0, 0.3, 1.9], life: 0.26, size: 1.05, rot3: [Math.PI / 2, 0, Math.PI], bursts: [[0, 1]],
      vel: { y: [5, 5], space: "world" }, drag: 14, sizeLife: [[0, 0.6, 0, 3], [0.35, 1.05], [1, 1.15]], alpha: [[0, 0], [0.12, 1], [0.6, 0.9], [1, 0]] },
    { name: "M3 FangEcho", mat: "aFang" + s, mode: "mesh", mesh: "quad", fudge: -1, pos: [0, 1.05, 2.05], life: 0.3, size: 1.5, rot3: [Math.PI / 2, 0, 0], bursts: [[C, 1]],
      sizeLife: [[0, 0.9], [1, 1.4]], alpha: [[0, 0.3], [1, 0]] },
    { name: "M4 FangEcho2", mat: "aFang" + s, mode: "mesh", mesh: "quad", fudge: -1, pos: [0, 0.65, 2.05], life: 0.3, size: 1.35, rot3: [Math.PI / 2, 0, Math.PI], bursts: [[C, 1]],
      sizeLife: [[0, 0.9], [1, 1.4]], alpha: [[0, 0.3], [1, 0]] },
    ...aFlames("M5", P, C, 6, s, 0.25, [0.5, 0.9], [0.6, 1.4]),
    // top: clench flash, sparks forward, embers, ash, dark shards, smoke
    ...aFlash("T1", P, C, 1.3, s),
    { name: "T2 SparksFwd", mat: "aStreak" + s, mode: "stretch", vs: 0.07, ls: 1.6, fudge: -5, pos: P, life: [0.25, 0.45], size: [0.05, 0.1], shape: { type: "sphere", r: 0.15, th: 1 },
      vel: { z: [6, 11] }, speed: [1, 3], drag: 3, gravity: 0.7, bursts: [[C, 16]], alpha: [[0, 1], [0.7, 1], [1, 0]] },
    ...aSparks("T3", P, C, 10, s, [3, 6]),
    ...aRays("T4", P, C + 0.01, 6, s, [7, 11], 2.6),
    ...aEmbers("T5", P, C, 14, s, 0.5),
    ...aFlecks("T6", P, C, 12, s, 0.6),
    ...aShards("T7", P, C, 8, s, 0.3, [3, 6]),
    ...aMistP("T8", [0, 0.6, 2.0], C + 0.02, 4, s, 0.4, [0.8, 1.3], [0.4, 1.0]),
  ];
}
const MATS = AM_MATS;
const SCENE = {
  name: "AM_HoundBite", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: [
    { id: "bite", role: "static", loop: false, duration: 0.6
      , layers: bite("") },
    { id: "bite_shiny", role: "static", loop: false, duration: 0.6
      , layers: bite("S") },
  ],
  demo: Object.assign({ interval: 2.0 }, DEMO_M, { scenarios: {
    base: sc("기본", { place: { bite: { at: [0, 0, 0], start: 0.05 }, bite_shiny: { at: [60, 0, 0], start: 0.05 } }, targets: [[0, 0, 2.2, 180]], camera: cam([0, 0.9, 1.6], 6.5, -55, 18) }),
    shiny: sc("이로치", { place: { bite_shiny: { at: [0, 0, 0], start: 0.05 }, bite: { at: [60, 0, 0], start: 0.05 } }, targets: [[0, 0, 2.2, 180]], camera: cam([0, 0.9, 1.6], 6.5, -55, 18) }),
  } }),
};
