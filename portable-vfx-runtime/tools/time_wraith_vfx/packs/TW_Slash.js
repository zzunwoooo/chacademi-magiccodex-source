// TW_Slash — 초침 베기. 모드 예고: 부채꼴. systems: swipe (기본 공격, 150° r 6) · second_a (2연타 1타, 오른→왼, 160° r 8) · second_b (2타, 왼→오른)
// 원점 = 보스 발밑, +Z = 정면. 발동 순간 재생.
var EXTRA_MESHES = Object.assign({
  sweepHi: arc(80, -75, 0.62, 0, 0.6), sweepLo: arc(78, -78, 0.74, 0, 0.6), groundArc: arc(75, -75, 0.5, 0.004), groundArc2: arc(72, -72, 0.76, 0.005),
  sweepHiR: arc(-80, 75, 0.62, 0, -0.6), sweepLoR: arc(-78, 78, 0.74, 0, -0.6), groundArcR: arc(-75, 75, 0.5, 0.004), groundArc2R: arc(-72, 72, 0.76, 0.005),
}, TIME_MESHES);
function slash(R, dir, half) {   // dir = 1 오른→왼, -1 왼→오른
  const S = dir > 0 ? "" : "R", a0 = dir * half * D2R, w = -dir * 2 * half * D2R / 0.22;
  const PTS = [0.8, 0.4, 0, -0.4, -0.8].map(k => { const a = dir * k * half * D2R; return [Math.sin(a) * R * 0.7, 1.4, Math.cos(a) * R * 0.7]; });
  return [
    // base: 바닥에 새겨진 초침 자국 + 문자판 눈금 + 시간 물결
    { name: "G01 Scar", mat: "sweepInk", mode: "mesh", mesh: "groundArc" + S, fudge: 45, pos: [0, 0.03, 0], life: 0.85, size: R, bursts: [[0.03, 1]], flip: true },
    { name: "G02 ScarTeal", mat: "sweep", mode: "mesh", mesh: "groundArc2" + S, fudge: 41, pos: [0, 0.05, 0], life: 0.7, size: R * 0.96, bursts: [[0.05, 1]], flip: true },
    { name: "G03 Ticks", mat: "lineSoul", mode: "mesh", mesh: "mcTicks", fudge: 43, pos: [0, 0.04, 0], life: 0.7, size: R * 1.02, bursts: [[0.05, 1]],
      sizeLife: [[0, 0.9, 0, 1], [0.2, 1]], alpha: [[0, 0], [0.08, 0.7], [0.6, 0.5], [1, 0]] },
    ...fxRipple("G04", [0, 0, R * 0.55], 0.06, R * 1.2, 0.7, "ripple", 0.5),
    ...fxCrack("G05", [0, 0, R * 0.6], 0.08, R * 0.7, 0.8),
    ...fxMist("G06", [0, 0.3, R * 0.55], 0.06, 8, R * 0.4, [1.2, 2.2], [0.6, 1.2]),
    // mid: 거대한 초침이 휩쓸고 지나감 + 두 겹 휩쓸기 (남색 몸 + 청록 날)
    { name: "M01 HandShadow", mat: "handInk", mode: "mesh", mesh: "handFlat", fudge: 9, pos: [0, 1.6, 0], life: 0.26, size: R * 1.05, rot3: [0, a0, 0], rotLife: [0, w, 0], bursts: [[0, 1]],
      alpha: [[0, 0.8], [0.8, 0.8], [1, 0]] },
    { name: "M02 Hand", mat: "handTeal", mode: "mesh", mesh: "handFlat", fudge: 8, pos: [0, 1.65, 0], life: 0.26, size: R, rot3: [0, a0, 0], rotLife: [0, w, 0], bursts: [[0, 1]],
      alpha: [[0, 1], [0.8, 1], [1, 0]] },
    { name: "M03 InkHi", mat: "sweepInk", mode: "mesh", mesh: "sweepHi" + S, fudge: 30, pos: [0, 2.3, 0], life: 0.42, size: R * 1.02, rot3: [-8 * D2R, 0, 0], bursts: [[0, 1]], flip: true },
    { name: "M04 SweepHi", mat: "sweep", mode: "mesh", mesh: "sweepHi" + S, fudge: 10, pos: [0, 2.35, 0], life: 0.36, size: R, rot3: [-8 * D2R, 0, 0], bursts: [[0, 1]], flip: true },
    { name: "M05 InkLo", mat: "sweepInk", mode: "mesh", mesh: "sweepLo" + S, fudge: 31, pos: [0, 1.1, 0], life: 0.42, size: R * 0.98, rot3: [-3 * D2R, 0, 0], bursts: [[0.05, 1]], flip: true },
    { name: "M06 SweepLo", mat: "sweepGold", mode: "mesh", mesh: "sweepLo" + S, fudge: 11, pos: [0, 1.15, 0], life: 0.36, size: R * 0.96, rot3: [-3 * D2R, 0, 0], bursts: [[0.05, 1]], flip: true },
    { name: "M07 Echo", mat: "sweep", mode: "mesh", mesh: "sweepHi" + S, fudge: 13, pos: [0, 1.8, 0], life: 0.55, size: R * 1.12, rot3: [-6 * D2R, 0, 0], bursts: [[0.1, 1]], flip: true, alpha: [[0, 0.45], [1, 0.45]] },
    ...fxShockT("M08", [0, 0, R * 0.3], 0.06, R * 1.1, 0.45, 0.5),
    // top: 휩쓴 자리에서 튀는 불티 · 모래 · 작은 톱니 · 반짝임
    ...PTS.map((p, n) => ({ name: "T0" + n + " Sparks", mat: "streakSoul", mode: "stretch", vs: 0.07, ls: 1.6, fudge: -5, pos: p, life: [0.25, 0.5], size: [0.05, 0.1],
      shape: { type: "sphere", r: 0.5, th: 1 }, speed: [5, 10], drag: 3, gravity: 0.8, bursts: [[0.02 + n * 0.045, 8]], alpha: [[0, 1], [0.7, 1], [1, 0]] })),
    ...PTS.map((p, n) => ({ name: "T1" + n + " Sand", mat: "sand", fudge: -4, pos: p, life: [0.5, 0.9], size: [0.05, 0.1], shape: { type: "sphere", r: 0.6, th: 1 },
      speed: [2, 5], drag: 1.5, gravity: 1.4, bursts: [[0.03 + n * 0.045, 10]], alpha: [[0, 1], [0.8, 1], [1, 0]] })),
    ...fxGears("T2", [0, 1.4, R * 0.6], 0.1, 5, R * 0.3, "gearTeal", [2, 5], [0.25, 0.5]),
    ...fxFlashT("T3", [0, 1.6, R * 0.6], 0.06, 3),
    ...fxMotes("T4", [0, 0.3, R * 0.55], 0.1, 24, R * 0.5, "dotGold"),
  ];
}
const SYSTEMS = [
  { id: "swipe", role: "static", loop: false, duration: 1.0, layers: slash(6, 1, 75) },
  { id: "second_a", role: "static", loop: false, duration: 1.0, layers: slash(8, 1, 80) },
  { id: "second_b", role: "static", loop: false, duration: 1.0, layers: slash(8, -1, 80) },
];
const MATS = usedMats(SYSTEMS);
const SCENE = {
  name: "TW_Slash", bloom: BLOOM, tonemapping: "neutral", scale: { input: "areaRadius", reference: 6, min: 0.3, max: 3 },
  systems: SYSTEMS,
  demo: Object.assign({ interval: 2.4 }, DEMO_BG, { scenarios: {
    swipe: sc("기본 공격", { place: { swipe: { at: [0, 0, 0], start: 0.05 }, second_a: { start: 99 }, second_b: { start: 99 } }, camera: cam([0, 1, 3], 13, -40, 35) }),
    second: sc("초침 베기 2연타", { place: { swipe: { start: 99 }, second_a: { at: [0, 0, 0], start: 0.05 }, second_b: { at: [0, 0, 0], start: 0.5 } }, camera: cam([0, 1, 4], 16, -30, 35) }),
  } }),
};
