
// ================= 팩 구성 (모드가 켜고 끄는 시스템). 원점 P = 빗자루 솔 끝, M = 빗자루 방향(+Z 코, +Y 위) =================
// 역할 static: 모드(BroomTrails)가 매 틱 move/basis 로 옮긴다. *_lite = 멀리 있는 사람용(입자 수 적음).
// 시스템 ID = 궤적 키. 효과 ID = claude:broomtrails/<키>. 연결표는 chacademy-motion 의 broom_trails.json.
const sys = (id, layers) => ({ id, role:"static", loop:true, duration:1.0, layers });
// 클라이언트 한도: 팩 하나에 시스템 16개 미만(ClaudeEffect 검사) → 4개 팩으로 나눔. PACK_NAME / PACK_KEYS 는 build_pack.py 가 앞에 넣는다.
const SYSTEMS = [];
for (const k of PACK_KEYS) {
  SYSTEMS.push(sys(k, TRAILS[k]));
  SYSTEMS.push(sys(k + "_lite", k === "basic" ? [ Object.assign({}, B_CORE, { name:"01 WindStreakLite", rateDist:6, size:[0.06,0.075] }) ] : TRAILS_LITE[k]));
}
// 이 팩에서 쓰는 재질만 넣는다 (안 쓰는 텍스처를 팩에 싣지 않도록).
const MATS = {};
for (const sy of SYSTEMS) for (const l of sy.layers) { MATS[l.mat] = RARE_MATS[l.mat]; if (l.trail) MATS[l.trail.mat] = RARE_MATS[l.trail.mat]; }
const SCENE = {
  name: PACK_NAME,
  bloom:{ threshold:0.95, intensity:1.2, scatter:0.65, tint:[1.0,0.97,1.0] }, tonemapping:"neutral",
  systems: SYSTEMS,
  // 이 팩의 미리보기는 정지 상태다. 비행 모습은 BroomTrails_Preview.html(빗자루별 attached 미리보기)에서 확인.
  demo:{ interval:3, bg:[0.52,0.62,0.74], floor:[0.33,0.42,0.30], grid:[0.38,0.48,0.34], dummy:false,
    scenarios:{ hover:{ label:"정지(솔 끝)", anchor:[0,1,0], camera:{ target:[0,1,0], dist:3, yaw:-120, pitch:15 } } } },
};
