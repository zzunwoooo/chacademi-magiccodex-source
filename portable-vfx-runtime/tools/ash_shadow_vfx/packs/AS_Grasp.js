// AS_Grasp — 그림자 손아귀. 모드 예고: 원 반경 6 (대상). system: grasp (발동 순간, 1.2초: 바닥에서 검은 손이 솟아 0.35초에 움켜쥠).
const R = 6, TC = 0.35;
var EXTRA_MESHES = Object.assign({ bigHand: pillar(0.62), finger: pillar(0.5) }, ASH_MESHES);
const FINGERS = 7;
const GRASP = [
  // base: dark pool spreading over the zone + crack + rune line circle (ember) that contracts at the clench
  { name: "G1 Pool", mat: "pool", mode: "mesh", mesh: "quad", fudge: 46, pos: [0, 0.03, 0], life: 1.2, size: 2 * R, rot3: [0, [0, TAU], 0], rotLife: [0, -0.5, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.5, 0, 3], [0.2, 1], [0.8, 0.95], [1, 0.7]], alpha: [[0, 0], [0.06, 0.95], [0.8, 0.9], [1, 0]] },
  { name: "G2 Ring", mat: "lineOra", mode: "mesh", mesh: "mcRing1", fudge: 40, pos: [0, 0.05, 0], life: 1.0, size: R, bursts: [[0, 1]],
    sizeLife: [[0, 1], [TC / 1.0, 0.45, 0, 0], [1, 0.35]], alpha: [[0, 0], [0.05, 1], [0.6, 0.8], [1, 0]] },
  { name: "G3 Runes", mat: "runeOra", mode: "mesh", mesh: "mcRunesOut", fudge: 41, pos: [0, 0.06, 0], life: 1.0, size: R, rotLife: [0, 1.2, 0], bursts: [[0, 1]],
    sizeLife: [[0, 1], [TC, 0.5], [1, 0.4]], alpha: [[0, 0], [0.06, 0.9], [0.6, 0.7], [1, 0]] },
  ...fxCrack("G4", [0, 0, 0], TC, 7, 0.9),
  // mid: the big hand rising in the centre, fingers bursting up around it then folding inward (grasp), rim flash at the clench
  { name: "M1 BigHand", mat: "shadowHand", mode: "mesh", mesh: "bigHand", fudge: 10, pos: [0, -0.4, 0], life: 1.0, size: 6.5, rot3: [0, 0.4, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.05, 0, 5], [0.28, 1.05], [0.36, 0.85], [0.75, 0.8], [1, 0.1]] },
  { name: "M2 BigRim", mat: "shadowHandHot", mode: "mesh", mesh: "bigHand", fudge: 9, pos: [0, -0.4, 0], life: 0.7, size: 6.6, rot3: [0, 0.4, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.05, 0, 5], [0.4, 1.05], [0.5, 0.85], [1, 0.6]], alpha: [[0, 0.4], [0.45, 1], [1, 0]] },
  ...Array.from({ length: FINGERS }, (_, k) => { const a = k / FINGERS * TAU, x = Math.cos(a) * 4.4, z = Math.sin(a) * 4.4, yaw = Math.PI / 2 - a;
    return { name: "M3" + k + " Finger", mat: "shadowHand", mode: "mesh", mesh: "finger", fudge: 11, pos: [x, -0.3, z], life: 0.95, size: 3.6, rot3: [0.25, yaw, 0],
      rotLife: [-2.2, 0, 0], bursts: [[0.03 + (k % 3) * 0.03, 1]], sizeLife: [[0, 0.05, 0, 6], [0.25, 1], [0.8, 0.9], [1, 0]] }; }),
  { name: "M4 ClenchFlash", mat: "starOra", fudge: -8, pos: [0, 2.6, 0], life: 0.2, size: 4, rot: [0, 45 * D2R], bursts: [[TC, 1]], sizeLife: [[0, 0.2], [0.3, 1.1], [1, 0]] },
  { name: "M5 ShockIn", mat: "shockVio", mode: "mesh", mesh: "quad", fudge: 30, pos: [0, 0.07, 0], life: 0.35, size: 2 * R + 1, rot3: [0, [0, TAU], 0], bursts: [[0.02, 1]],
    sizeLife: [[0, 1], [1, 0.2]], alpha: [[0, 0.8], [1, 0]] },
  { name: "M6 TendrilsIn", mat: "mist", fudge: 35, pos: [0, 0.6, 0], life: [0.4, 0.6], size: [1.2, 2], rot: [0, TAU], shape: { type: "circle", r: 5.6, th: 0.2, y: 0 },
    vel: { rad: [-12, -9], space: "world" }, bursts: [[0.02, 16]], sizeLife: [[0, 1], [1, 0.5]], alpha: [[0, 0], [0.2, 0.75], [1, 0]], flip: F44 },
  // top: inward sparks, embers, flakes, rising ember wisps at the clench
  { name: "T1 SparksIn", mat: "streakOra", mode: "stretch", vs: 0.06, ls: 1.6, fudge: -5, pos: [0, 0.5, 0], life: [0.25, 0.35], size: [0.05, 0.09], shape: { type: "circle", r: 5.8, th: 0.15, y: 0 },
    vel: { rad: [-18, -14], space: "world" }, bursts: [[0.02, 30]], alpha: [[0, 1], [1, 0]] },
  ...fxFlames("T2", [0, 1.8, 0], TC, 10, 1.0),
  ...fxSparks("T3", [0, 2.0, 0], TC, 22, [5, 10]),
  ...fxEmbers("T4", [0, 1.4, 0], TC, 30, 2),
  ...fxFlakes("T5", [0, 1.0, 0], 0.1, 24, 4),
];
const SYSTEMS = [{ id: "grasp", role: "static", loop: false, duration: 1.3, layers: GRASP }];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "AS_Grasp", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 2.6 }, DEMO_BG, { scenarios: {
    grasp: sc("손아귀", { place: { grasp: { at: [0, 0, 0], start: 0.05 } }, targets: [[1, 0, 1, 200], [-2, 0, 3, 150]], camera: cam([0, 1.8, 0], 16, -50, 28) }),
  } }),
};
