// AS_Afterimage — 잔상. system: flicker (보스 발밑 r 2, 0.6초: 검은 실루엣 줄무늬가 떨리며 흩어짐 — 직전 패턴의 잔상 시전 신호).
var EXTRA_MESHES = Object.assign({}, ASH_MESHES);
const FLICKER = [
  { name: "G1 Ring", mat: "ringVio", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.06, 0], life: 0.45, size: 5, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.6], [1, 1.2]], alpha: [[0, 0.8], [1, 0]] },
  ...fxScorch("G2", [0, 0, 0], 0, 3.5, 0.6),
  // dark vertical silhouette streaks jittering sideways (ink + ember rim copies offset in time)
  { name: "M1 GhostInk", mat: "mist", mode: "stretch", vs: 0.0, ls: 1, fudge: 34, pos: [0, 3.4, 0], life: [0.25, 0.4], size: [2.4, 3.2], shape: { type: "sphere", r: 1.4, th: 0.3 },
    vel: { x: [-6, 6], space: "world" }, drag: 6, bursts: [[0, 4], [0.08, 4], [0.16, 3]], sizeLife: [[0, 1.2], [1, 0.8]], alpha: [[0, 0.8], [1, 0]], flip: F44 },
  { name: "M2 GhostStreak", mat: "streakCrim", mode: "stretch", vs: 0.09, ls: 6, fudge: -3, pos: [0, 3.5, 0], life: [0.15, 0.25], size: [0.3, 0.6], shape: { type: "sphere", r: 1.6, th: 0.4 },
    vel: { x: [-14, 14], space: "world" }, drag: 8, bursts: [[0, 6], [0.08, 6], [0.16, 5]], alpha: [[0, 0.85], [1, 0]] },
  { name: "M3 VertStreak", mat: "streakOra", mode: "stretch", vs: 0.1, ls: 5, fudge: -4, pos: [0, 1.0, 0], life: [0.18, 0.3], size: [0.12, 0.22], shape: { type: "circle", r: 1.8, th: 0.5, y: 0 },
    vel: { y: [14, 20], space: "world" }, bursts: [[0.02, 10], [0.12, 8]], alpha: [[0, 0.9], [1, 0]] },
  { name: "T1 Flash", mat: "starOra", fudge: -8, pos: [0, 6.2, -0.4], life: 0.14, size: 1.5, rot: [0, 45 * D2R], bursts: [[0.0, 1], [0.12, 1]], sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  ...fxFlakes("T2", [0, 3, 0], 0.02, 26, 2, [0.5, 0.9], [0.3, 1.2]),
  ...fxEmbers("T3", [0, 3, 0], 0.02, 20, 2),
];
const SYSTEMS = [{ id: "flicker", role: "static", loop: false, duration: 0.7, layers: FLICKER }];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_Afterimage", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: 2, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 1.6 }, DEMO_BG, { scenarios: {
    flicker: sc("잔상", { place: { flicker: { at: [0, 0, 0], start: 0.05 } }, targets: [[0, 0, 0, 0]], camera: cam([0, 3, 0], 13, -60, 15) }),
  } }),
};
