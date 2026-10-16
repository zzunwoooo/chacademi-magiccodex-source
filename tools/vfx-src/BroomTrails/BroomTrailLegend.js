// BroomTrailLegend — 가장 높은 등급(전설) 빗자루 비행 궤적 예시 "별빛 오로라". attached, loop: 탑승 중 계속, 내릴 때 stop.
// 원점 = 탑승자 발 위치, +Z = 비행(수평) 방향, 빗자루 솔 끝 ≈ (0, 0.72, -1.05).
// 구성: 금빛-흰 중심 줄기 + 좌우로 흔들리는 청록→보라 오로라 띠 2줄 + 반짝이며 천천히 떨어지는 별 + 솔 끝의 은은한 빛.
// 가림 방지(타이밍): 중심 줄기 0.4 s, 오로라 0.5 s, 별 0.6~0.85 s(작고 띄엄띄엄). 전부 태어난 직후가 가장 진하고 수명 40~50% 에 반 이하.
// 오로라는 탑승자 몸보다 아래(솔 높이 이하)로만 흘러 F5 시점에서 캐릭터를 가리지 않게 함. 내릴 때(stop) 남은 입자는 0.15 s 안에 사라짐.
const IV = [1, 1, 1], GOLD = [1.0, 0.86, 0.5], CYAN = [0.45, 0.95, 1.0], VIO = [0.62, 0.45, 1.0], MAG = [0.95, 0.5, 0.95];
const TAIL = [0, 0.72, -1.05];
const LAYERS = [
  // 솔 끝 은은한 빛 (탑승 중 항상, 정지해도 유지 — 등급 표시)
  { name:"01 BristleGlow", mat:"glow", fudge:2, pos:[0,0.72,-0.95], rate:14, life:0.3, size:[0.5,0.6],
    color:[[0,GOLD],[1,VIO]], alpha:[[0,0],[0.3,0.32],[1,0]], sizeLife:[[0,0.8],[1,1.1]], onStop:0.1 },
  // 금빛-흰 중심 줄기
  { name:"02 CoreStreak", mat:"streak", mode:"stretch", vs:0.25, ls:5, fudge:0, world:true, pos:TAIL, rateDist:12,
    life:[0.36,0.42], size:[0.07,0.085], shape:{type:"sphere", r:0.03, th:1}, vel:{z:[-0.7,-0.5], space:"local"}, drag:1.5,
    color:[[0,IV],[0.35,GOLD],[1,MAG]], alpha:[[0,0.95],[0.4,0.42],[1,0]], sizeLife:[[0,1],[1,0.3]], onStop:0.15 },
  // 오로라 띠 좌/우: 솔 아래쪽으로 퍼지며 노이즈로 일렁임 (반투명, 가산)
  { name:"03 AuroraL", mat:"aurora", mode:"stretch", vs:0.3, ls:3.2, fudge:4, world:true, pos:[-0.16,0.6,-1.0], rateDist:9,
    life:[0.45,0.55], size:[0.14,0.18], shape:{type:"sphere", r:0.04, th:1}, vel:{x:[-0.55,-0.35], y:[-0.35,-0.2], z:[-0.6,-0.4], space:"local"}, drag:1.2,
    noise:{s:0.35, f:1.2, sp:0.6}, color:[[0,CYAN],[0.5,VIO],[1,MAG]], alpha:[[0,0],[0.08,0.55],[0.45,0.25],[1,0]], sizeLife:[[0,0.7],[0.3,1],[1,0.6]], onStop:0.15 },
  { name:"04 AuroraR", mat:"aurora", mode:"stretch", vs:0.3, ls:3.2, fudge:4, world:true, pos:[0.16,0.6,-1.0], rateDist:9,
    life:[0.45,0.55], size:[0.14,0.18], shape:{type:"sphere", r:0.04, th:1}, vel:{x:[0.35,0.55], y:[-0.35,-0.2], z:[-0.6,-0.4], space:"local"}, drag:1.2,
    noise:{s:0.35, f:1.2, sp:0.6}, color:[[0,CYAN],[0.5,VIO],[1,MAG]], alpha:[[0,0],[0.08,0.55],[0.45,0.25],[1,0]], sizeLife:[[0,0.7],[0.3,1],[1,0.6]], onStop:0.15 },
  // 별: 띄엄띄엄 생겨 반짝(커졌다 작아짐)이며 천천히 아래로 떨어짐
  { name:"05 Stars", mat:"star", fudge:-3, world:true, pos:TAIL, rateDist:2.2,
    life:[0.6,0.85], size:[0.1,0.16], speed:[0.2,0.5], shape:{type:"sphere", r:0.22, th:1}, drag:2, gravity:0.12,
    rot:[0,Math.PI/4], rotZ:[-2,2], color:[[0,IV],[0.5,GOLD],[1,CYAN]], alpha:[[0,1],[0.7,0.8],[1,0]],
    sizeLife:[[0,0],[0.15,1],[0.4,0.55],[0.6,0.85],[1,0]], onStop:0.15 },
  // 작은 금빛 가루
  { name:"06 Dust", mat:"dot", fudge:-2, world:true, pos:TAIL, rateDist:7,
    life:[0.4,0.6], size:[0.02,0.035], speed:[0.3,0.8], shape:{type:"sphere", r:0.15, th:1}, drag:2.5, gravity:0.05,
    noise:{s:0.5, f:1.6, sp:0.8}, color:[[0,IV],[0.4,GOLD],[1,VIO]], alpha:[[0,1],[0.5,0.6],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.12 },
];
const MATS = {
  streak:{tex:"T_TrailStreak", tint:[1.9,1.8,1.6], add:true},
  aurora:{tex:"T_AuroraWisp", tint:[1.2,1.3,1.6], add:true},
  star:{tex:"T_Star4", tint:[2.2,2.1,1.9], add:true},
  dot:{tex:"T_Dot", tint:[2.0,1.8,1.5], add:true},
  glow:{tex:"T_Glow", tint:[1.0,0.85,0.9], add:true},
};

// ---------------- demo (preview only, the mod ignores it) — same flights as BroomTrailBasic ----------------
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
  name: "BroomTrailLegend",
  bloom:{ threshold:0.95, intensity:1.2, scatter:0.65, tint:[1.0,0.95,1.0] }, tonemapping:"neutral",
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
