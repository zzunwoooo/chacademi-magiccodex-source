// BroomTrailBasic — 가장 기본(일반 등급) 빗자루 비행 궤적. attached, loop: 탑승 중 계속 켜 두고, 내릴 때 stop.
// 원점 = 탑승자 발 위치(모드가 넘기는 빗자루 탑승 위치), +Z = 비행(수평) 방향. 빗자루 솔 끝 ≈ (0, 0.72, -1.05).
// 궤적은 전부 world 공간 + rateOverDistance → 멈추면 새로 안 생기고, 빠를수록 꼬리가 길어진다(꼬리 길이 ≈ 속도 × 수명).
// 가림 방지: 수명 0.3 s 안팎, 태어난 직후 가장 진하고 수명 40% 에서 이미 반 이하로 흐려짐. 내릴 때(stop) 남은 입자는 0.12 s 안에 사라짐.
const IV = [1, 1, 1], PALE = [0.85, 0.95, 1.0], SKY = [0.6, 0.82, 1.0];
const TAIL = [0, 0.72, -1.05];
const LAYERS = [
  // 솔 끝에서 이어지는 얇은 바람 줄기 (겹치는 짧은 stretched 조각이 이어져 한 줄의 띠처럼 보임)
  { name:"01 WindStreak", mat:"streak", mode:"stretch", vs:0.25, ls:5, fudge:0, world:true, pos:TAIL, rateDist:10,
    life:[0.26,0.32], size:[0.05,0.065], shape:{type:"sphere", r:0.03, th:1}, vel:{z:[-0.7,-0.5], space:"local"}, drag:1.5,
    color:[[0,IV],[0.4,PALE],[1,SKY]], alpha:[[0,0.85],[0.4,0.38],[1,0]], sizeLife:[[0,1],[1,0.35]], onStop:0.12 },
  // 양옆으로 살짝 벌어지는 보조 줄기 2개 (가늘고 더 빨리 사라짐)
  { name:"02 SideStreakL", mat:"streak", mode:"stretch", vs:0.2, ls:4, fudge:1, world:true, pos:[-0.1,0.7,-1.0], rateDist:6,
    life:[0.18,0.24], size:[0.025,0.035], shape:{type:"sphere", r:0.03, th:1}, vel:{x:[-0.5,-0.3], z:[-0.6,-0.4], space:"local"}, drag:2,
    color:[[0,IV],[1,SKY]], alpha:[[0,0.6],[0.5,0.25],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 },
  { name:"03 SideStreakR", mat:"streak", mode:"stretch", vs:0.2, ls:4, fudge:1, world:true, pos:[0.1,0.7,-1.0], rateDist:6,
    life:[0.18,0.24], size:[0.025,0.035], shape:{type:"sphere", r:0.03, th:1}, vel:{x:[0.3,0.5], z:[-0.6,-0.4], space:"local"}, drag:2,
    color:[[0,IV],[1,SKY]], alpha:[[0,0.6],[0.5,0.25],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 },
  // 작은 바람 입자 몇 개 (흩어지며 0.5 s 안에 사라짐)
  { name:"04 Motes", mat:"dot", fudge:-2, world:true, pos:TAIL, rateDist:1.6,
    life:[0.35,0.5], size:[0.025,0.04], speed:[0.2,0.6], shape:{type:"sphere", r:0.12, th:1}, drag:2,
    noise:{s:0.5, f:1.5, sp:0.8}, color:[[0,IV],[1,SKY]], alpha:[[0,0],[0.15,0.9],[0.6,0.5],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 },
];
const MATS = {
  streak:{tex:"T_TrailStreak", tint:[1.5,1.7,1.9], add:true},
  dot:{tex:"T_Dot", tint:[1.6,1.8,2.0], add:true},
};

// ---------------- demo (preview only, the mod ignores it) ----------------
// fly(segs): [dur, v0, v1, yawRateDeg/s] 구간을 0.05 s 단위로 적분해 탑승자 발 위치 경로를 만든다 (높이 3 m 비행).
function fly(segs, y0) {
  const K = []; let t = 0, x = 0, z = 0, yaw = 0, dt = 0.05; K.push([0, [0, y0, 0], 0]);
  for (const [dur, v0, v1, yr] of segs) {
    const n = Math.max(1, Math.round(dur / dt));
    for (let i = 1; i <= n; i++) { const u = (i - 0.5) / n, v = v0 + (v1 - v0) * u; yaw += yr * dt; x += Math.sin(yaw * D2R) * v * dt; z += Math.cos(yaw * D2R) * v * dt; t += dt;
      K.push([+t.toFixed(3), [+x.toFixed(3), y0, +z.toFixed(3)], +yaw.toFixed(2)]); }
  }
  return K;
}
const segTotal = segs => segs.reduce((a, s) => a + s[0], 0);
function scen(label, segs, extra) {
  const T = segTotal(segs);
  return Object.assign({ label, path: fly(segs, 3), stops:{ trail: +(T - 0.6).toFixed(2) }, interval: T + 1.2,
    camera:{ follow:true, targetY:1.0, dist:5.2, yaw:-125, pitch:14 } }, extra || {});
}
const SCENE = {
  name: "BroomTrailBasic",
  bloom:{ threshold:0.95, intensity:1.0, scatter:0.6, tint:[0.95,1.0,1.0] }, tonemapping:"neutral",
  systems:[ { id:"trail", role:"attached", loop:true, duration:1.0, layers:LAYERS } ],
  demo:{
    interval:7, bg:[0.52,0.62,0.74], floor:[0.33,0.42,0.30], grid:[0.38,0.48,0.34], dummy:{height:1.8},
    character:{ file:"models/BroomRider.glb", clip:"fly" },
    scenarios:{
      cruise:{ ...scen("순항 12 m/s → 감속·정지 → 내림", [[3.0,12,12,0],[1.2,12,0,0],[1.4,0,0,0]]) },
      turn:  { ...scen("선회 14 m/s", [[1.0,14,14,0],[2.0,14,14,80],[1.0,14,14,0],[1.0,14,0,0],[1.0,0,0,0]]) },
      boost: { ...scen("느리게 6 → 가속 22 → 급정거", [[1.2,6,6,0],[0.6,6,22,0],[1.5,22,22,0],[0.8,22,0,0],[1.4,0,0,0]]) },
      slow:  { ...scen("저속 3 m/s", [[4.0,3,3,0],[0.6,3,0,0],[1.2,0,0,0]]) },
    },
  },
};
