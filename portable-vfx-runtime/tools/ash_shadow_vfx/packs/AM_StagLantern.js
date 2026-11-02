// AM_StagLantern — 등불 사슴 원거리 (잿불탄). charge(static, 등불 위치에서 0.8초: 불 모으기) → bolt(projectile, 서버 chamobshot 이 이동) → hit(impact, 착탄 원 r 2.5, 1.0초)
// charge 원점 = 사슴 발밑·정면 (서버 chavfx at=caster), 레이어는 등불 위치(위 2.1, 앞 1.0)에 둠.
// 모션 lantern_stag attack: 0~0.6초 모으기, 0.75초 발사.
const R = 2.5;
var EXTRA_MESHES = Object.assign({}, MC_MESHES);
function charge(s) {   // 원점 = 사슴 발밑 (서버 chavfx), 등불 = 위 2.1 · 앞 1.0
  const L0 = [0, 2.1, 1.0];
  return [
    { name: "C1 Glow", mat: "aGlow" + s, fudge: 2, pos: L0, life: 0.8, size: 0.9, bursts: [[0, 1]], sizeLife: [[0, 0.3], [0.85, 1.1], [1, 0.2]], alpha: [[0, 0], [0.3, 0.3], [0.85, 0.45], [1, 0]] },
    { name: "C2 Core", mat: "aFlameHot" + s, fudge: 0, pos: L0, life: [0.2, 0.3], size: [0.35, 0.6], rot: [0, TAU], shape: { type: "sphere", r: 0.08, th: 1 },
      rate: 0, bursts: [[0, 2], [0.15, 3], [0.3, 3], [0.45, 4], [0.6, 5]], sizeLife: [[0, 0.5, 0, 3], [1, 1.1]], flip: F44 },
    { name: "C3 SuckDots", mat: "aDot" + s, fudge: -6, pos: L0, life: [0.35, 0.5], size: [0.05, 0.1], shape: { type: "sphere", r: 1.4, th: 0.3 },
      vel: { rad: [-3.6, -2.6] }, bursts: [[0, 6], [0.15, 8], [0.3, 10], [0.45, 14]], alpha: [[0, 0], [0.2, 1], [0.85, 1], [1, 0]], sizeLife: [[0, 1], [1, 0.4]] },
    { name: "C4 SuckStreak", mat: "aStreak" + s, mode: "stretch", vs: 0.06, ls: 1.4, fudge: -5, pos: L0, life: [0.3, 0.42], size: [0.05, 0.09], shape: { type: "sphere", r: 1.2, th: 0.2 },
      vel: { rad: [-4, -3] }, bursts: [[0.1, 6], [0.3, 8], [0.5, 10]], alpha: [[0, 0], [0.3, 1], [1, 0]] },
    { name: "C5 Ring", mat: "aLine" + s, mode: "mesh", mesh: "mcRing1", fudge: 3, pos: L0, life: 0.8, size: 0.9, rot3: [Math.PI / 2, 0, 0], rotLife: [0, 0, 4], bursts: [[0, 1]],
      sizeLife: [[0, 1.4], [0.85, 0.5], [1, 1.6]], alpha: [[0, 0], [0.2, 0.8], [0.85, 1], [1, 0]] },
    { name: "C6 Ring2", mat: "aLine" + s, mode: "mesh", mesh: "mcRing3", fudge: 3, pos: L0, life: 0.8, size: 0.9, rot3: [Math.PI / 2, 0.6, 0], rotLife: [0, 0, -5], bursts: [[0.05, 1]],
      sizeLife: [[0, 1.6], [0.85, 0.6], [1, 1.8]], alpha: [[0, 0], [0.2, 0.6], [0.85, 0.9], [1, 0]] },
    ...aFlash("C7", L0, 0.72, 1.6, s),
    ...aSparks("C8", L0, 0.72, 10, s, [3, 6], 0.15),
    { name: "C9 Smoke", mat: "aMist" + s, fudge: 33, pos: L0, life: [0.6, 0.9], size: [0.4, 0.7], shape: { type: "sphere", r: 0.2, th: 1 }, rate: 10,
      vel: { y: [0.6, 1.2], space: "world" }, rot: [0, TAU], alpha: [[0, 0], [0.2, 0.5], [1, 0]], sizeLife: [[0, 0.5], [1, 1.4]], flip: F44 },
  ];
}
function bolt(s) {
  return [
    { name: "P01 Core", mat: "aFlameHot" + s, fudge: 0, loop: true, rate: 40, life: [0.16, 0.26], size: [0.6, 0.85], rot: [0, TAU], rotZ: [-5, 5], shape: { type: "sphere", r: 0.08, th: 1 }, flip: F44 },
    { name: "P02 Halo", mat: "aGlow" + s, fudge: 1, loop: true, rate: 10, life: 0.3, size: 1.3, alpha: [[0, 0], [0.2, 0.4], [1, 0]] },
    { name: "P03 Star", mat: "aStar" + s, fudge: -2, loop: true, rate: 12, life: 0.14, size: [0.6, 0.9], rot: [0, TAU], alpha: [[0, 0.7], [1, 0]] },
    { name: "P04 TrailFlame", mat: "aFlame" + s, fudge: 0, loop: true, world: true, rateDist: 14, life: [0.22, 0.4], size: [0.3, 0.55], rot: [0, TAU], shape: { type: "sphere", r: 0.12, th: 1 },
      vel: { y: [0.3, 0.8] }, sizeLife: [[0, 1], [1, 0.3]], flip: F44 },
    { name: "P05 TrailSmoke", mat: "aMist" + s, fudge: 30, loop: true, world: true, rateDist: 3, life: [0.5, 0.8], size: [0.5, 0.9], rot: [0, TAU], shape: { type: "sphere", r: 0.15, th: 1 },
      vel: { y: [0.3, 0.6] }, alpha: [[0, 0], [0.15, 0.5], [1, 0]], sizeLife: [[0, 0.6], [1, 1.3]], flip: F44 },
    { name: "P06 Seeds", mat: "aDot" + s, fudge: -6, loop: true, world: true, rateDist: 10, life: [0.4, 0.8], size: [0.04, 0.07], speed: [0.5, 1.5], shape: { type: "sphere", r: 0.2, th: 1 },
      gravity: 0.4, alpha: [[0, 1], [0.7, 1], [1, 0]] },
    { name: "P07 Ash", mat: "aFleck" + s, fudge: 22, loop: true, world: true, rateDist: 5, life: [0.5, 0.9], size: [0.05, 0.08], speed: [0.3, 1], shape: { type: "sphere", r: 0.2, th: 1 },
      gravity: 0.2, alpha: [[0, 0.9], [1, 0]] },
  ];
}
function hit(s) {
  const P = [0, 0.7, 0];
  return [
    ...aCrackD("B1", [0, 0, 0], 0.02, 3.8, s, 1.0),
    ...aRings("B2", R, [0, 0, 0], 0, 0.85, s),
    ...aDustWave("B3", [0, 0, 0], 0.03, 2 * R + 1.5, s, 0.6),
    ...aShock("M1", [0, 0, 0], 0, 2 * R + 0.6, s, 0.45),
    { name: "M2 Fireball", mat: "aFlame" + s, fudge: 0, pos: P, bursts: [[0, 14], [0.06, 6]], shape: { type: "sphere", r: 0.3, th: 1 }, speed: [2.2, 4.8], drag: 5, life: [0.4, 0.65], size: [0.8, 1.4],
      rot: [0, TAU], vel: { y: [0.3, 1], space: "world" }, sizeLife: [[0, 0.5, 0, 3], [1, 1.2]], flip: F44 },
    ...aFlames("M3", [0, 0.1, 0], 0.03, 8, s, 0, [0.6, 1.0], [1.4, 2.6], { type: "circle", r: R * 0.6, th: 0.5, y: 0 }),
    ...aMistP("M4", P, 0.05, 6, s, 0.4, [1.1, 1.8], [1.2, 2.6]),
    ...aFlash("T1", P, 0, 2.6, s),
    ...aRays("T2", P, 0.02, 9, s, [8, 13], 3.2),
    ...aSparks("T3", P, 0, 24, s, [4, 10]),
    ...aEmbers("T4", P, 0.05, 24, s, 0.8),
    ...aFlecks("T5", P, 0.03, 14, s, 0.6),
    ...aShards("T6", P, 0, 10, s, 0.3, [3, 7]),
  ];
}
const MATS = AM_MATS;
const LANT = [0, 2.1, 1.0], TO = [0, 0, 9], VIA = [0, 3.2, 4.8];
const evs = s => [{ t: 0.05, system: "charge" + s, actor: 0 }, { t: 0.8, system: "bolt" + s, actor: 0, from: LANT, to: TO, via: VIA, flight: 0.7, normal: [0, 1, 0], impact: "hit" + s }];
const SCENE = {
  name: "AM_StagLantern", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: [
    { id: "charge", role: "static", loop: false, duration: 0.8, layers: charge("") },
    { id: "bolt", role: "projectile", loop: true, duration: 60, layers: bolt("") },
    { id: "hit", role: "impact", loop: false, duration: 1.0, layers: hit("") },
    { id: "charge_shiny", role: "static", loop: false, duration: 0.8, layers: charge("S") },
    { id: "bolt_shiny", role: "projectile", loop: true, duration: 60, layers: bolt("S") },
    { id: "hit_shiny", role: "impact", loop: false, duration: 1.0, layers: hit("S") },
  ],
  demo: Object.assign({ interval: 3.0 }, DEMO_M, { scenarios: {
    base: { label: "기본", actors: [{ path: [[0, [0, 0, 0], 0]] }], events: evs(""), targets: [[0, 0, 9, 180]], camera: cam([0, 1.2, 4.5], 11, -70, 18) },
    shiny: { label: "이로치", actors: [{ path: [[0, [0, 0, 0], 0]] }], events: evs("_shiny"), targets: [[0, 0, 9, 180]], camera: cam([0, 1.2, 4.5], 11, -70, 18) },
  } }),
};
