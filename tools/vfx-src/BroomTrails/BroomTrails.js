// BroomTrails — 빗자루 비행 궤적 팩 (클라이언트 배포용). 모션 모드(chacademy-motion BroomTrails)가 켜고 끈다.
// 원점 P = 빗자루 솔 끝(모델 뒤끝 중심). M = 빗자루 방향: +Z = 빗자루 앞(코), +Y = 빗자루 위. 모드가 매 틱 move/basis 로 옮긴다.
// 레이어 수치는 사용자 승인 미리보기(BroomTrailBasic / BroomTrailLegend, 2026-10-16)와 같고 위치만 솔 끝 기준으로 옮겼다.
// *_lite = 멀리 있는 사람용(중심 줄기 위주, 입자 수 약 1/8). 모드가 입자 예산·거리로 고른다.
// 역할 static: 미리보기의 attached(발 + 몸 방향) 대신 모드가 빗자루의 보간 위치·기울기를 직접 넘기기 위함.
const IV = [1, 1, 1], PALE = [0.85, 0.95, 1.0], SKY = [0.6, 0.82, 1.0];
const GOLD = [1.0, 0.86, 0.5], CYAN = [0.45, 0.95, 1.0], VIO = [0.62, 0.45, 1.0], MAG = [0.95, 0.5, 0.95];
const O = [0, 0, 0];

// ---------------- 기본 (일반 등급) ----------------
const B_CORE = { name:"01 WindStreak", mat:"b_streak", mode:"stretch", vs:0.25, ls:5, fudge:0, world:true, pos:O, rateDist:10,
    life:[0.26,0.32], size:[0.05,0.065], shape:{type:"sphere", r:0.03, th:1}, vel:{z:[-0.7,-0.5], space:"local"}, drag:1.5,
    color:[[0,IV],[0.4,PALE],[1,SKY]], alpha:[[0,0.85],[0.4,0.38],[1,0]], sizeLife:[[0,1],[1,0.35]], onStop:0.12 };
const BASIC = [
  B_CORE,
  { name:"02 SideStreakL", mat:"b_streak", mode:"stretch", vs:0.2, ls:4, fudge:1, world:true, pos:[-0.1,-0.02,0.05], rateDist:6,
    life:[0.18,0.24], size:[0.025,0.035], shape:{type:"sphere", r:0.03, th:1}, vel:{x:[-0.5,-0.3], z:[-0.6,-0.4], space:"local"}, drag:2,
    color:[[0,IV],[1,SKY]], alpha:[[0,0.6],[0.5,0.25],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 },
  { name:"03 SideStreakR", mat:"b_streak", mode:"stretch", vs:0.2, ls:4, fudge:1, world:true, pos:[0.1,-0.02,0.05], rateDist:6,
    life:[0.18,0.24], size:[0.025,0.035], shape:{type:"sphere", r:0.03, th:1}, vel:{x:[0.3,0.5], z:[-0.6,-0.4], space:"local"}, drag:2,
    color:[[0,IV],[1,SKY]], alpha:[[0,0.6],[0.5,0.25],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 },
  { name:"04 Motes", mat:"b_dot", fudge:-2, world:true, pos:O, rateDist:1.6,
    life:[0.35,0.5], size:[0.025,0.04], speed:[0.2,0.6], shape:{type:"sphere", r:0.12, th:1}, drag:2,
    noise:{s:0.5, f:1.5, sp:0.8}, color:[[0,IV],[1,SKY]], alpha:[[0,0],[0.15,0.9],[0.6,0.5],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 },
];
const BASIC_LITE = [ Object.assign({}, B_CORE, { name:"01 WindStreakLite", rateDist:6, size:[0.06,0.075] }) ];

// ---------------- 전설 "별빛 오로라" ----------------
const L_CORE = { name:"02 CoreStreak", mat:"l_streak", mode:"stretch", vs:0.25, ls:5, fudge:0, world:true, pos:O, rateDist:12,
    life:[0.36,0.42], size:[0.07,0.085], shape:{type:"sphere", r:0.03, th:1}, vel:{z:[-0.7,-0.5], space:"local"}, drag:1.5,
    color:[[0,IV],[0.35,GOLD],[1,MAG]], alpha:[[0,0.95],[0.4,0.42],[1,0]], sizeLife:[[0,1],[1,0.3]], onStop:0.15 };
const L_STARS = { name:"05 Stars", mat:"l_star", fudge:-3, world:true, pos:O, rateDist:2.2,
    life:[0.6,0.85], size:[0.1,0.16], speed:[0.2,0.5], shape:{type:"sphere", r:0.22, th:1}, drag:2, gravity:0.12,
    rot:[0,Math.PI/4], rotZ:[-2,2], color:[[0,IV],[0.5,GOLD],[1,CYAN]], alpha:[[0,1],[0.7,0.8],[1,0]],
    sizeLife:[[0,0],[0.15,1],[0.4,0.55],[0.6,0.85],[1,0]], onStop:0.15 };
const LEGEND = [
  { name:"01 BristleGlow", mat:"l_glow", fudge:2, pos:[0,0,0.1], rate:14, life:0.3, size:[0.5,0.6],
    color:[[0,GOLD],[1,VIO]], alpha:[[0,0],[0.3,0.32],[1,0]], sizeLife:[[0,0.8],[1,1.1]], onStop:0.1 },
  L_CORE,
  { name:"03 AuroraL", mat:"l_aurora", mode:"stretch", vs:0.3, ls:3.2, fudge:4, world:true, pos:[-0.16,-0.12,0.05], rateDist:9,
    life:[0.45,0.55], size:[0.14,0.18], shape:{type:"sphere", r:0.04, th:1}, vel:{x:[-0.55,-0.35], y:[-0.35,-0.2], z:[-0.6,-0.4], space:"local"}, drag:1.2,
    noise:{s:0.35, f:1.2, sp:0.6}, color:[[0,CYAN],[0.5,VIO],[1,MAG]], alpha:[[0,0],[0.08,0.55],[0.45,0.25],[1,0]], sizeLife:[[0,0.7],[0.3,1],[1,0.6]], onStop:0.15 },
  { name:"04 AuroraR", mat:"l_aurora", mode:"stretch", vs:0.3, ls:3.2, fudge:4, world:true, pos:[0.16,-0.12,0.05], rateDist:9,
    life:[0.45,0.55], size:[0.14,0.18], shape:{type:"sphere", r:0.04, th:1}, vel:{x:[0.35,0.55], y:[-0.35,-0.2], z:[-0.6,-0.4], space:"local"}, drag:1.2,
    noise:{s:0.35, f:1.2, sp:0.6}, color:[[0,CYAN],[0.5,VIO],[1,MAG]], alpha:[[0,0],[0.08,0.55],[0.45,0.25],[1,0]], sizeLife:[[0,0.7],[0.3,1],[1,0.6]], onStop:0.15 },
  L_STARS,
  { name:"06 Dust", mat:"l_dot", fudge:-2, world:true, pos:O, rateDist:7,
    life:[0.4,0.6], size:[0.02,0.035], speed:[0.3,0.8], shape:{type:"sphere", r:0.15, th:1}, drag:2.5, gravity:0.05,
    noise:{s:0.5, f:1.6, sp:0.8}, color:[[0,IV],[0.4,GOLD],[1,VIO]], alpha:[[0,1],[0.5,0.6],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.12 },
];
const LEGEND_LITE = [
  Object.assign({}, L_CORE, { name:"01 CoreStreakLite", rateDist:7, size:[0.085,0.1] }),
  Object.assign({}, L_STARS, { name:"02 StarsLite", rateDist:1.0 }),
];

const MATS = {
  b_streak:{tex:"T_TrailStreak", tint:[1.5,1.7,1.9], add:true},
  b_dot:{tex:"T_Dot", tint:[1.6,1.8,2.0], add:true},
  l_streak:{tex:"T_TrailStreak", tint:[1.9,1.8,1.6], add:true},
  l_aurora:{tex:"T_AuroraWisp", tint:[1.2,1.3,1.6], add:true},
  l_star:{tex:"T_Star4", tint:[2.2,2.1,1.9], add:true},
  l_dot:{tex:"T_Dot", tint:[2.0,1.8,1.5], add:true},
  l_glow:{tex:"T_Glow", tint:[1.0,0.85,0.9], add:true},
};
const sys = (id, layers) => ({ id, role:"static", loop:true, duration:1.0, layers });
const SCENE = {
  name: "BroomTrails",
  bloom:{ threshold:0.95, intensity:1.2, scatter:0.65, tint:[1.0,0.97,1.0] }, tonemapping:"neutral",
  systems:[ sys("basic", BASIC), sys("basic_lite", BASIC_LITE), sys("legend", LEGEND), sys("legend_lite", LEGEND_LITE) ],
  // 이 팩의 미리보기는 정지 상태(솔 끝 빛만 보임)다. 비행 모습은 BroomTrail_Compare.html(같은 레이어, attached 미리보기)에서 확인.
  demo:{ interval:3, bg:[0.52,0.62,0.74], floor:[0.33,0.42,0.30], grid:[0.38,0.48,0.34], dummy:false,
    scenarios:{ hover:{ label:"정지(솔 끝)", anchor:[0,1,0], camera:{ target:[0,1,0], dist:3, yaw:-120, pitch:15 } } } },
};
