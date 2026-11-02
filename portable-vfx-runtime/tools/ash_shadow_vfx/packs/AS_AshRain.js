// AS_AshRain — 추적하는 재. systems: cast (보스 발밑, 2.2초: 손 들고 4번 튕김 0.6/0.9/1.2/1.5초) · fall (대상 원 반경 4 — 서버는 발동 0.25초 전에 재생: 0~0.25초 낙하, 0.25초 착탄).
const R = 4, TI = 0.25;
var EXTRA_MESHES = Object.assign({}, ASH_MESHES);
const HANDS = [[4.2, 5.4, -0.6], [-4.2, 5.4, -0.6]];
const FLICK = [0.6, 0.9, 1.2, 1.5];
const CAST = [
  // ground ash circle under the boss (line work, draws on, flares on each flick)
  ...magicCircle("G", 3.2, [0, 0, 0], 0.0, 2.1, "Ora"),
  ...FLICK.map((t, i) => ({ name: "G9" + i + " Flare", mat: "ringOra", mode: "mesh", mesh: "quad", fudge: 29, pos: [0, 0.07, 0], life: 0.3, size: 7, rot3: [0, i, 0], bursts: [[t, 1]],
    sizeLife: [[0, 0.8], [1, 1.15]], alpha: [[0, 0.8], [1, 0]] })),
  // halo fountain above the head: ember fountain + glow + rising flakes
  { name: "M01 HaloFountain", mat: "flame", fudge: 0, pos: [0, 7.2, -0.8], life: [0.4, 0.7], size: [0.6, 1.1], rot: [0, TAU], shape: { type: "circle", r: 1.6, th: 0.2, y: 0 },
    speed: [1, 2], vel: { y: [2, 4], space: "world" }, gravity: 0.6, rate: 22, sizeLife: [[0, 0.4, 0, 3], [1, 1.0]], flip: F44 },
  { name: "M02 HaloGlow", mat: "glowOra", fudge: -2, pos: [0, 7.2, -0.8], life: 2.1, size: 2.6, bursts: [[0, 1]], sizeLife: [[0, 0.5], [0.2, 1], [0.9, 1.05], [1, 0.6]], alpha: [[0, 0], [0.15, 0.22], [0.85, 0.22], [1, 0]] },
  { name: "M03 FountainSparks", mat: "streakOra", mode: "stretch", vs: 0.06, ls: 1.5, fudge: -5, pos: [0, 7.4, -0.8], life: [0.4, 0.7], size: [0.04, 0.08], shape: { type: "circle", r: 1.5, th: 0.3, y: 0 },
    vel: { y: [4, 8], space: "world" }, speed: [1, 3], gravity: 1.2, rate: 30, alpha: [[0, 1], [0.7, 1], [1, 0]] },
  // finger flicks: alternating hands — spark burst + small flash + embers thrown up
  ...FLICK.flatMap((t, i) => { const p = HANDS[i % 2]; return [
    { name: "T" + i + "0 Flick", mat: "starOra", fudge: -8, pos: p, life: 0.14, size: 1.6, rot: [0, 45 * D2R], bursts: [[t, 1]], sizeLife: [[0, 0.2], [0.3, 1.1], [1, 0]] },
    { name: "T" + i + "1 FlickSparks", mat: "streakOra", mode: "stretch", vs: 0.07, ls: 1.6, fudge: -5, pos: p, life: [0.25, 0.45], size: [0.05, 0.09], shape: { type: "sphere", r: 0.3, th: 1 },
      speed: [6, 11], vel: { y: [3, 6], space: "world" }, gravity: 1.2, bursts: [[t, 14]], alpha: [[0, 1], [1, 0]] },
    { name: "T" + i + "2 FlickEmber", mat: "flame", fudge: 0, pos: p, life: [0.25, 0.4], size: [0.5, 0.9], rot: [0, TAU], shape: { type: "sphere", r: 0.3, th: 1 },
      vel: { y: [3, 6], space: "world" }, bursts: [[t, 4]], sizeLife: [[0, 0.4, 0, 3], [1, 1]], flip: F44 },
  ]; }),
  ...fxFlakes("T9", [0, 3, 0], 0.2, 30, 3, [1.2, 1.9], [0.6, 1.6]),
];
const FALL = [
  // the falling ember chunk (0 → 0.25 s): hot core, dark ash body, long trail streaks
  { name: "A01 ChunkCore", mat: "flame", fudge: 0, pos: [1.0, 10, -1.2], life: TI, size: 1.8, rot: [0, TAU], bursts: [[0, 1]], vel: { x: -4, y: -40, z: 4.8, space: "world" }, flip: F44, sizeLife: [[0, 0.8], [1, 1]] },
  { name: "A02 ChunkGlow", mat: "glowOra", fudge: -2, pos: [1.0, 10, -1.2], life: TI, size: 1.6, bursts: [[0, 1]], vel: { x: -4, y: -40, z: 4.8, space: "world" }, alpha: [[0, 0.5], [1, 0.6]] },
  { name: "A03 ChunkTrail", mat: "streakOra", mode: "stretch", vs: 0.09, ls: 3, fudge: -4, pos: [1.0, 10, -1.2], life: [0.1, 0.18], size: [0.2, 0.35], shape: { type: "sphere", r: 0.4, th: 1 },
    vel: { x: -4, y: -40, z: 4.8, space: "world" }, rate: 90, alpha: [[0, 0.9], [1, 0]] },
  { name: "A04 AshTrail", mat: "mist", fudge: 35, pos: [1.0, 10, -1.2], life: [0.3, 0.45], size: [0.8, 1.3], rot: [0, TAU], vel: { x: -2, y: -18, z: 2.4, space: "world" }, rate: 40, flip: F44,
    alpha: [[0, 0.6], [1, 0]] },
  // impact @ 0.25
  ...fxFlash("T1", [0, 0.6, 0], TI, 3.8),
  ...fxShock("M1", [0, 0, 0], TI, 2 * R + 1, "shockOra", 0.45),
  { name: "M3 Ring", mat: "ringOra", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.07, 0], life: 0.5, size: 2 * R, rot3: [0, [0, TAU], 0], bursts: [[TI, 1]],
    sizeLife: [[0, 0.7], [1, 1.05]], alpha: [[0, 0.9], [1, 0]] },
  ...fxCrack("G1", [0, 0, 0], TI, 2 * R * 0.9, 1.0), ...fxScorch("G2", [0, 0, 0], TI, 2 * R, 1.0),
  ...fxFlames("M4", [0, 0.4, 0], TI, 14, 1.4, "flame", [1.0, 1.7]),
  { name: "M5 Pillar", mat: "pillarOra", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.35, size: 5, rot3: [0, [0, TAU], 0], bursts: [[TI, 1]],
    sizeLife: [[0, 0.2, 0, 4], [0.3, 1], [1, 1.05]], alpha: [[0, 0], [0.1, 1], [1, 0]] },
  ...fxShards("M6", [0, 0.5, 0], TI, 16, 1.2, [3, 7]),
  ...fxMist("M7", [0, 0.4, 0], TI, 8, 1.6),
  ...fxSparks("T2", [0, 0.5, 0], TI, 26, [6, 12]),
  ...fxEmbers("T3", [0, 0.6, 0], TI + 0.02, 26, 2),
  ...fxFlakes("T4", [0, 0.5, 0], TI + 0.04, 20, 2),
];
const SYSTEMS = [
  { id: "cast", role: "static", loop: false, duration: 2.3, layers: CAST },
  { id: "fall", role: "static", loop: false, duration: 1.1, layers: FALL },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_AshRain", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 3.2 }, DEMO_BG, { scenarios: {
    rain: sc("재의 비", { place: { cast: { at: [0, 0, 0], start: 0.05 }, fall: { at: [0, 0, 8], start: 0.45 } }, targets: [[0, 0, 0, 0], [0, 0, 8, 180]], camera: cam([0, 2.5, 4], 18, -60, 25) }),
    fall: sc("착탄", { place: { fall: { at: [0, 0, 0], start: 0.05 } }, targets: [[0.5, 0, 0.5, 180]], camera: cam([0, 1.5, 0], 11, -40, 30) }),
  } }),
};
