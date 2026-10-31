// PR_LastLantern — 기믹3 마지막 등불. 등불·사탕 본체는 Blockbench 모델. 빛 반경 장판은 PR_LanternLight(별도 팩, areaRadius로 반경 조절).
// systems: darkFall(보스: 어둠이 내려앉음) · lanternDrop(등불 떨어진 자리) · carry(attached: 등불 든 사람, 체력 감소 연출) · deposit(사탕 넣음: 반경 커짐)
// · lightOut(등불 꺼짐) · (휩쓸기 예고는 모드: 경기장 원 0.9초) · sweep(한 바퀴 휩쓸기) — 빛 밖 사망자에는 PR_Harvested
const ARENA = 20, TW = 0.9;
var EXTRA_MESHES = Object.assign({ bigArc: arc(0, 340, 0.8, 0) }, MC_MESHES);
const DARK = [
  { name: "D1 DarkWave", mat: "mist", mode: "mesh", mesh: "quad", fudge: 70, pos: [0, 0.1, 0], life: 1.6, size: 2 * ARENA, bursts: [[0, 1]], sizeLife: [[0, 0.05, 0, 1.2], [1, 1]], alpha: [[0, 0.9], [0.7, 0.7], [1, 0]], flip: F44 },
  ...fxShock("D2", [0, 0, 0], 0, 2 * ARENA, "shockVio", 1.4), ...fxMist("D3", [0, 2, 0], 0, 24, 3, [2.4, 3.6], [4, 7]),
  ...fxBats("D4", [0, 3, 0], 0, 20, 2), ...fxFlash("D5", [0, 2.8, 0], 0, 3, "starVio"),
  { name: "D6 Snuff", mat: "ghost", fudge: 5, pos: [0, 2, 0], life: [1, 1.6], size: [0.6, 1.0], shape: { type: "sphere", r: 1, th: 1 }, speed: [4, 8], drag: 1.2, vel: { y: [1, 2], space: "world" }, bursts: [[0.05, 14]], alpha: [[0, 0], [0.1, 0.8], [1, 0]] },
];
const DROP = [...fxFlash("L1", [0, 0.5, 0], 0, 2.6, "starGold"), ...fxShock("L2", [0, 0, 0], 0, 4, "shockGold"), ...fxSparks("L3", [0, 0.4, 0], 0, 18, [3, 7], "streakOra"),
  ...magicCircle("L4", 1.8, [0, 0, 0], 0, 1.4, "Gold", 0.1), ...fxMotes("L5", [0, 0.2, 0], 0.05, 20, 1, "dotGold")];
const CARRY = [
  { name: "C1 Glow", mat: "glowGold", fudge: 2, pos: [0.45, 1.0, 0.35], life: 0.6, size: 1.8, rate: 4, bursts: [[0, 2]], alpha: [[0, 0], [0.5, 0.4], [1, 0]] },
  { name: "C2 Motes", mat: "dotGold", fudge: -6, pos: [0.45, 1.0, 0.35], life: [0.8, 1.4], size: [0.04, 0.08], rate: 10, shape: { type: "sphere", r: 0.4, th: 1 }, vel: { y: [0.3, 0.8] }, alpha: [[0, 1], [1, 0]] },
  // life being drained: violet drops pulled off the body into the lantern
  { name: "C3 Drain", mat: "streakVio", mode: "stretch", vs: 0.08, ls: 1.6, fudge: -3, pos: [0, 1.1, 0], life: [0.35, 0.5], size: [0.06, 0.1], rate: 12, shape: { type: "sphere", r: 0.7, th: 0.2 }, vel: { rad: [-3, -2] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "C4 Wisp", mat: "flameVio", fudge: 0, pos: [0, 0.2, 0], life: [0.5, 0.8], size: [0.3, 0.5], rate: 6, shape: { type: "circle", r: 0.5, th: 0.3, y: 0 }, vel: { y: [0.6, 1.2] }, rot: [0, TAU], flip: F44 },
];
const DEPOSIT = [...fxFlash("P1", [0, 0.8, 0], 0, 2.4, "starGold"),
  { name: "P2 Ring", mat: "ringGold", mode: "mesh", mesh: "quad", fudge: 40, pos: [0, 0.06, 0], life: 0.9, size: 6, bursts: [[0, 1]], sizeLife: [[0, 0.2, 0, 2], [1, 1]], alpha: [[0, 1], [1, 0]] },
  { name: "P3 Swirl", mat: "starPink", fudge: -7, pos: [0, 0.8, 0], life: [0.4, 0.6], size: [0.15, 0.3], shape: { type: "circle", r: 1, th: 0.2, y: 0 }, vel: { orb: [6, 8], rad: [-2, -1.5] }, bursts: [[0, 16]], sizeLife: [[0, 1], [1, 0]] },
  { name: "P4 Up", mat: "pillarGold", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.6, size: 3, bursts: [[0.05, 1]], rot3: [0, [0, TAU], 0], sizeLife: [[0, 0.3, 0, 3], [0.3, 1]], alpha: [[0, 0], [0.15, 0.8], [1, 0]] },
  ...fxMotes("P5", [0, 0.2, 0], 0.05, 20, 1.2, "dotGold")];
const OUT = [...fxMist("O1", [0, 0.8, 0], 0, 8, 0.3, [0.8, 1.4], [0.6, 1.2]), ...fxEmbers("O2", [0, 0.8, 0], 0, 16, 0.3),
  { name: "O3 Ghost", mat: "ghost", fudge: 5, pos: [0, 1, 0], life: 1.2, size: 1.0, bursts: [[0.1, 1]], vel: { y: 1.6, space: "world" }, alpha: [[0, 0], [0.2, 0.8], [1, 0]] }];
const SWEEP = [
  { name: "W1 Ink", mat: "slashInk", mode: "mesh", mesh: "bigArc", fudge: 30, pos: [0, 1.1, 0], life: 0.7, size: ARENA, rotLife: [0, -9, 0], bursts: [[0, 1]], flip: true },
  { name: "W2 Blade", mat: "slash", mode: "mesh", mesh: "bigArc", fudge: 10, pos: [0, 1.2, 0], life: 0.65, size: ARENA, rotLife: [0, -9, 0], bursts: [[0, 1]], flip: true },
  { name: "W3 Echo", mat: "slashViolet", mode: "mesh", mesh: "bigArc", fudge: 12, pos: [0, 1.6, 0], life: 0.8, size: ARENA * 0.85, rot3: [0, 1, 0], rotLife: [0, -8, 0], bursts: [[0.06, 1]], flip: true },
  ...fxShock("W4", [0, 0, 0], 0.05, 2 * ARENA + 4, "shockOra", 0.9), ...fxCrack("W5", [0, 0, 0], 0, 8, 2.0),
  { name: "W6 Sparks", mat: "streakOra", mode: "stretch", vs: 0.06, ls: 2, fudge: -5, life: [0.3, 0.55], size: [0.08, 0.14], shape: { type: "circle", r: ARENA * 0.85, th: 0.25, y: 1.1 },
    bursts: [[0.05, 80]], vel: { orb: [-12, -8], rad: [1, 3] }, drag: 2, gravity: 0.5, alpha: [[0, 1], [0.7, 1], [1, 0]] },
  ...fxBats("W7", [0, 2, 0], 0.1, 14, 6), ...fxEmbers("W8", [0, 1.5, 0], 0.1, 90, ARENA * 0.6),
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_LastLantern", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    { id: "darkFall", role: "static", loop: false, duration: 2.5, layers: DARK },
    { id: "lanternDrop", role: "static", loop: false, duration: 1.6, layers: DROP },
    { id: "carry", role: "attached", loop: true, duration: 2, layers: CARRY },
    { id: "deposit", role: "static", loop: false, duration: 1.4, layers: DEPOSIT },
    { id: "lightOut", role: "static", loop: false, duration: 1.6, layers: OUT },
    { id: "sweep", role: "static", loop: false, duration: 2.0, layers: SWEEP },
  ],
  demo: Object.assign({ interval: 4.4, dummy: { height: 1.8 } }, DEMO_BG, { scenarios: {
    start: { label: "어둠 + 등불", path: [[0, [2, 0, 6], 180]], hits: [], place: { darkFall: { at: [0, 0, 0], start: 0 }, lanternDrop: { at: [0, 0, 6], start: 1.2 }, carry: { start: 2.2 },
      deposit: { at: [2, 0, 6], start: 3.0 }, lightOut: { start: 99 }, sweep: { start: 99 } }, camera: cam([0, 1, 4], 30, -90, 40) },
    end: { label: "휩쓸기", path: [[0, [2, 0, 6], 180]], hits: [], place: { sweep: { at: [0, 0, 0], start: TW }, darkFall: { start: 99 }, lanternDrop: { start: 99 },
      carry: { start: 99 }, deposit: { start: 99 }, lightOut: { start: 99 } }, camera: cam([0, 1.5, 0], 46, -90, 48) },
  } }),
};
