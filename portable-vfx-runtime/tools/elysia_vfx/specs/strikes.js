// Belzar_Strikes — 벨자르 근접 기본기 2종. static, P = 벨자르 발밑, 정면(+Z) = 벨자르가 보는 방향(서버가 시전자 수평 방향을 넘김).
//  claw : 할퀴기 — 앞쪽에 붉은 발톱 자국 3줄 + 검은 잉크 잔상 (0.55 s, 모션 0.35~0.55 s 타격에 맞춰 시작)
//  tail : 꼬리 휩쓸기 — 뒤쪽 반원을 쓸고 지나가는 지면 초승달 + 불티 (0.8 s)
var EXTRA_MESHES = { claw: crescentXY(Math.PI * 0.7, 0.07), sweep: crescentXZ(Math.PI * 1.1, 0.1), sweepThin: crescentXZ(Math.PI * 1.1, 0.03) };
const CLAW = [];
[-0.45, 0, 0.45].forEach((o, i) => {
  // 대각선 내려베기: XY 초승달을 앞쪽(+Z 1.6 m)에 세우고 Z축으로 기울임
  CLAW.push({ name: `C0${i} Claw`, mat: "hSlash", mode: "mesh", mesh: "claw", fudge: 4, pos: [o * 1.4, 2.3 + o * 0.4, 1.8], size: 2.4, rot3: [0, 0, -0.9], bursts: [[0.02 * i, 1]], life: 0.36, flip: { x: 2, y: 8 } });
  CLAW.push({ name: `C1${i} Ink`, mat: "inkSlash", mode: "mesh", mesh: "claw", fudge: 10, pos: [o * 1.4, 2.3 + o * 0.4, 1.85], size: 2.5, rot3: [0, 0, -0.9], bursts: [[0.02 * i + 0.03, 1]], life: 0.4, flip: { x: 2, y: 8 } });
});
CLAW.push(
  { name: "C20 Flash", mat: "hStar", fudge: -8, pos: [0, 1.6, 1.7], size: 2, rot: [0, TAU], bursts: [[0.1, 1]], life: 0.18, sizeLife: [[0, 0.3], [0.3, 1], [1, 0]] },
  { name: "C21 Sparks", mat: "hDot", mode: "stretch", vs: 0.05, ls: 1.3, fudge: -6, pos: [0, 1.6, 1.8], life: [0.3, 0.5], size: [0.04, 0.08], shape: { type: "sphere", r: 0.5, th: 1 }, bursts: [[0.08, 24]],
    speed: [3, 7], drag: 3, gravity: 0.6, color: [[0, EMB], [1, CRIM]], alpha: [[0, 1], [1, 0]] },
  { name: "C22 Shards", mat: "shardDark", fudge: 16, pos: [0, 1.6, 1.8], life: [0.35, 0.55], size: [0.1, 0.2], rot: [0, TAU], rotZ: [-12, 12], shape: { type: "sphere", r: 0.4, th: 1 }, bursts: [[0.08, 8]],
    speed: [3, 5], gravity: 1.4, alpha: [[0, 1], [1, 0]] },
  { name: "C23 Mist", mat: "mist", fudge: 22, pos: [0, 1.4, 1.6], life: [0.5, 0.7], size: [0.8, 1.2], rot: [0, TAU], shape: { type: "sphere", r: 0.5, th: 1 }, bursts: [[0.08, 5]],
    vel: { z: [1, 2] }, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 } });
// 꼬리: 뒤쪽(−Z) 반원. crescentXZ는 +X축 기준 ±span/2 → rot3 Y로 뒤를 향하게
const BACK = [0, Math.PI / 2, 0];
const TAIL = [
  { name: "T01 Sweep", mat: "hSlash", mode: "mesh", mesh: "sweep", fudge: 4, pos: [0, 0.35, 0], size: 3.2, rot3: BACK, rotLife: [0, -6, 0], bursts: [[0, 1]], life: 0.4, flip: { x: 2, y: 8 } },
  { name: "T02 SweepHigh", mat: "hSlash", mode: "mesh", mesh: "sweepThin", fudge: 5, pos: [0, 0.9, 0], size: 2.8, rot3: BACK, rotLife: [0, -6.5, 0], bursts: [[0.04, 1]], life: 0.35, flip: { x: 2, y: 8 } },
  { name: "T03 Ink", mat: "inkSlash", mode: "mesh", mesh: "sweep", fudge: 10, pos: [0, 0.32, 0], size: 3.3, rot3: BACK, rotLife: [0, -6, 0], bursts: [[0.04, 1]], life: 0.45, flip: { x: 2, y: 8 } },
  { name: "T04 Dust", mat: "mist", fudge: 22, life: [0.6, 0.9], size: [0.9, 1.4], rot: [0, TAU], shape: { type: "circle", r: 3, th: 0.2, y: 0.3 }, bursts: [[0.1, 10]],
    vel: { rad: [1, 2], y: [0.2, 0.5] }, drag: 1.5, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.5], [1, 0]], flip: { x: 4, y: 4 }, off: [0, 0, -1.2] },
  { name: "T05 Embers", mat: "hDot", mode: "stretch", vs: 0.05, ls: 1.2, fudge: -6, life: [0.4, 0.7], size: [0.04, 0.08], shape: { type: "circle", r: 3, th: 0.2, y: 0.3 }, bursts: [[0.05, 30]],
    vel: { rad: [3, 5], y: [1, 2.5] }, drag: 2, gravity: 0.5, color: [[0, EMB], [1, DEEP]], alpha: [[0, 1], [1, 0]], off: [0, 0, -1.2] },
  { name: "T06 GroundCrack", mat: "hCrack", mode: "mesh", mesh: "quad", fudge: 43, pos: [0, 0.025, -1.6], size: 3.6, rot3: YAW, bursts: [[0.1, 1]], life: 0.9, sizeLife: [[0, 0.5, 0, 3], [0.2, 1]], alpha: [[0, 0.9], [1, 0]] },
];
const MATS = {
  hSlash: { tex: "T_SlashHell_Sheet", tint: [2.2, 1.6, 1.4], add: true }, inkSlash: { tex: "T_SlashHell_Sheet", tint: [0.22, 0.05, 0.07], add: false }, hStar: { tex: "T_Star", tint: [2.8, 1.2, 0.7], add: true },
  hDot: { tex: "T_Dot", tint: [2.4, 1.1, 0.55], add: true }, shardDark: { tex: "T_ShardDark", tint: [2.2, 0.8, 0.4], add: false }, mist: { tex: "T_DarkMist_Sheet", tint: [1, 1, 1], add: false },
  hCrack: { tex: "T_Crack", tint: [2.2, 0.5, 0.22], add: true },
};
const SCENE = {
  name: "Belzar_Strikes", bloom: BLOOM_HELL, tonemapping: "neutral",
  systems: [{ id: "claw", role: "static", loop: false, duration: 0.2, layers: CLAW }, { id: "tail", role: "static", loop: false, duration: 0.2, layers: TAIL }],
  demo: Object.assign({}, DEMO_BG, { interval: 3, dummy: { height: 3.8 },
    scenarios: { all: { label: "할퀴기 → 꼬리 휩쓸기", path: [[0, [0, 0, 0], 0]], actors: [{ path: [[0, [0, 0, 0], 0]] }],
      events: [{ t: 0.2, system: "claw", actor: 0 }, { t: 1.4, system: "tail", actor: 0 }], camera: { target: [0, 1.5, 0], dist: 10, yaw: -50, pitch: 20 } } } }),
};
