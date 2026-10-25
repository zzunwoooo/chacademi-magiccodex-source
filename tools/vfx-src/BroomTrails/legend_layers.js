// ================= 전설 등급 궤적 (rare_layers.js + unique_layers.js 뒤에 붙음) — 전설답게 크고 화려하게 =================
// l_heart(마법소녀) 핑크 하트 + 노랑·파랑 컨페티 / l_darkheart(타락한 마법소녀) 검은 하트 + 보라·노랑 컨페티
// l_dragon(드래고니아) 파란 드래곤 브레스 / l_butterfly(블러디 플라이) 빨간 홀로그램 나비 / l_foxfire(아홉꼬리) 하늘색 여우불(빗자루 둘레를 도는 구슬 + 남는 불꽃)
// l_angel(엔젤) 천사 깃털 / l_devil(데빌) 악마 불꽃
const HPINK = [1.0,0.55,0.8], HPINK2 = [0.95,0.4,1.0], YEL = [1.0,0.85,0.2], BLU = [0.3,0.6,1.0], DPURP = [0.55,0.25,0.95];
const BFIRE = [0.4,0.85,1.0], BFIRE2 = [0.15,0.35,1.0], FOX = [0.55,0.9,1.0], FOX2 = [0.3,0.65,1.0];
const DFIRE = [1.0,0.45,0.15], DFIRE2 = [0.85,0.1,0.08];
const TIPGLOW = (mat, c1, c2, size) => ({ name:"00 TipGlow", mat, fudge:2, pos:[0,0,0.1], rate:14, life:0.3, size,
    color:[[0,c1],[1,c2]], alpha:[[0,0],[0.3,0.35],[1,0]], sizeLife:[[0,0.8],[1,1.1]], onStop:0.1 });
const LCORE = (mat, col, ex) => at(B_CORE, Object.assign({ name:"01 Core", mat, size:[0.09,0.11], life:[0.27,0.32], rateDist:12, color:col, alpha:[[0,0.95],[0.4,0.45],[1,0]], onStop:0.15 }, ex || {}));
const CONFETTI = (name, col, rate) => ({ name, mat:"l_confetti", fudge:-4, world:true, pos:O, rateDist:rate,
    life:[0.8,1.1], size:[0.08,0.11], speed:[1.2,2.4], shape:{type:"sphere", r:0.15, th:1}, drag:3, gravity:0.25,
    rot:[0,6.28], rotZ:[-6,6], flip:{x:4, y:1, cycles:4}, color:[[0,col],[1,col]], alpha:[[0,1],[0.8,1],[1,0]], onStop:0.2 });
const HEARTS = (name, mat, rate, col, ex) => at({ name, mat, fudge:-3, world:true, pos:O, rateDist:rate,
    life:[0.75,1.05], size:[0.26,0.38], speed:[0.3,0.8], shape:{type:"sphere", r:0.2, th:1}, drag:2, gravity:-0.08,
    rot:[-0.35,0.35], rotZ:[-0.8,0.8], noise:{s:0.3, f:1.2, sp:0.6}, color:col, alpha:[[0,1],[0.75,0.9],[1,0]],
    sizeLife:[[0,0],[0.12,1.25],[0.25,1],[0.8,0.9],[1,0]], onStop:0.2 }, ex || {});
const SPARK = (name, mat, rate, col) => ({ name, mat, fudge:-3, world:true, pos:O, rateDist:rate,
    life:[0.45,0.65], size:[0.08,0.14], speed:[0.2,0.5], shape:{type:"sphere", r:0.25, th:1}, drag:2, gravity:0.05,
    rot:[0,0.8], color:col, alpha:[[0,1],[0.7,0.8],[1,0]], sizeLife:[[0,0],[0.15,1],[0.4,0.55],[0.6,0.85],[1,0]], onStop:0.15 });
const FIRE = (name, mat, pos, rate, life, size, vz, grav, col, al, ex) => at({ name, mat, fudge:3, world:true, pos, rateDist:rate,
    life, size, speed:[0.1,0.4], shape:{type:"sphere", r:0.08, th:1}, vel:{z:vz, space:"local"}, drag:3, gravity:grav,
    noise:{s:0.6, f:1.4, sp:1.2}, rot:[-0.6,0.6], rotZ:[-1,1], flip:{x:4, y:1, cycles:3},
    color:col, alpha:[[0,0],[0.08,al],[0.55,al * 0.55],[1,0]], sizeLife:[[0,0.4],[0.35,1],[1,1.3]], onStop:0.15 }, ex || {});

// ---- 마법소녀 ----
const L_HEART = [
  TIPGLOW("lh_glow", HPINK, HPINK2, [0.65,0.75]),
  LCORE("lh_streak", [[0,IV],[0.35,HPINK],[1,HPINK2]]),
  HEARTS("02 Hearts", "lh_heart", 4.0, [[0,IV],[0.25,HPINK],[1,HPINK2]]),
  CONFETTI("03 ConfettiY", YEL, 5), CONFETTI("04 ConfettiB", BLU, 5),
  SPARK("05 Sparkle", "lh_star", 2.2, [[0,IV],[0.5,HPINK],[1,YEL]]),
];
const L_HEART_LITE = [ at(L_HEART[1], { name:"01 CoreLite", rateDist:7 }), at(L_HEART[2], { name:"02 HeartsLite", rateDist:0.9 }) ];
// ---- 타락한 마법소녀 ----
const L_DARKHEART = [
  TIPGLOW("ld_glow", DPURP, [0.3,0.1,0.5], [0.65,0.75]),
  at(B_CORE, { name:"01 Shadow", mat:"u_shadow", fudge:3, size:[0.14,0.17], life:[0.3,0.34], color:[[0,[0.15,0.05,0.2]],[1,[0.03,0.01,0.05]]], alpha:[[0,0.85],[0.4,0.45],[1,0]], onStop:0.15 }),
  LCORE("ld_streak", [[0,[1,0.85,1]],[0.35,DPURP],[1,[0.3,0.1,0.6]]], { size:[0.05,0.06], fudge:-1, name:"02 Core" }),
  HEARTS("03 DarkHearts", "ld_heart", 3.6, [[0,IV],[1,IV]], { alpha:[[0,1],[0.8,1],[1,0]] }),
  CONFETTI("04 ConfettiP", DPURP, 5), CONFETTI("05 ConfettiY", YEL, 4),
  SPARK("06 Sparkle", "ld_star", 1.8, [[0,IV],[0.5,DPURP],[1,YEL]]),
];
const L_DARKHEART_LITE = [ at(L_DARKHEART[1], { name:"01 ShadowLite", rateDist:7 }), at(L_DARKHEART[3], { name:"02 HeartsLite", rateDist:0.8 }) ];
// ---- 드래고니아: 파란 드래곤 브레스 — 솔 끝에서 뒤로 뿜어지는 파란 불길 + 하얀 중심 + 위로 튀는 불씨 ----
const L_DRAGON = [
  TIPGLOW("ldr_glow", BFIRE, BFIRE2, [0.75,0.9]),
  FIRE("02 BreathOuter", "ldr_fire", [0,0,0], 12, [0.42,0.52], [0.7,0.95], [-3.5,-2.5], -0.1, [[0,IV],[0.2,BFIRE],[0.6,BFIRE2],[1,[0.1,0.1,0.5]]], 0.75),
  FIRE("03 BreathCore", "ldr_fire", [0,0,0], 12, [0.26,0.32], [0.38,0.48], [-4,-3], 0, [[0,IV],[0.4,[0.8,0.95,1]],[1,BFIRE]], 0.95, { fudge:1 }),
  LCORE("ldr_streak", [[0,IV],[0.3,BFIRE],[1,BFIRE2]], { size:[0.1,0.12] }),
  DUST("04 Embers", "ldr_dot", 7, [[0,IV],[0.5,BFIRE],[1,BFIRE2]], { gravity:-0.25, size:[0.025,0.04], life:[0.4,0.6], speed:[0.6,1.4] }),
];
const L_DRAGON_LITE = [ at(L_DRAGON[3], { name:"01 CoreLite", rateDist:7 }), at(L_DRAGON[1], { name:"02 BreathLite", rateDist:2.5 }) ];
// ---- 블러디 플라이: 빨간 홀로그램 나비가 날갯짓하며 흩날림 ----
const L_BUTTERFLY = [
  TIPGLOW("lb_glow", RED, RED_D, [0.6,0.7]),
  LCORE("lb_streak", [[0,IV],[0.3,RED],[1,RED_D]]),
  { name:"02 Butterflies", mat:"lb_fly", fudge:-3, world:true, pos:O, rateDist:3.4,
    life:[1.0,1.4], size:[0.32,0.42], speed:[0.5,1.0], shape:{type:"sphere", r:0.25, th:1}, drag:2.2, gravity:-0.06,
    noise:{s:0.9, f:1.1, sp:0.9}, rot:[-0.4,0.4], rotZ:[-0.6,0.6], flip:{x:4, y:1, cycles:7},
    color:[[0,[1,0.55,0.55]],[0.2,[1,0.18,0.25]],[1,[0.75,0.05,0.12]]], alpha:[[0,0],[0.06,1],[0.3,0.75],[0.35,1],[0.6,0.6],[0.65,0.95],[1,0]],
    sizeLife:[[0,0.4],[0.12,1],[1,0.85]], onStop:0.2 },
  SMOKE("03 Haze", "lb_haze", [0,-0.03,0], [-0.1,0.1], 3, [0.3,0.4], [0.36,0.44], [[0,RED],[1,RED_D]], 0.18),
  DUST("04 Bits", "lb_dot", 6, [[0,IV],[1,RED]], { gravity:0 }),
];
const L_BUTTERFLY_LITE = [ at(L_BUTTERFLY[1], { name:"01 CoreLite", rateDist:7 }), at(L_BUTTERFLY[2], { name:"02 FlyLite", rateDist:0.7 }) ];
// ---- 아홉꼬리: 하늘색 여우불. 솔 끝 둘레를 도는 여우불 구슬(꼬리 달림, 아리 W 느낌) + 지나간 자리에 남아 일렁이는 여우불 ----
const L_FOXFIRE = [
  TIPGLOW("lf2_glow", FOX, FOX2, [0.7,0.8]),
  { name:"02 Orbs", mat:"lf2_orb", fudge:-1, pos:[0,0.05,0.05], rate:3.4, life:[0.9,0.9], size:[0.28,0.32],
    shape:{type:"circle", r:0.6, th:0}, vel:{orb:[5,5], space:"local"}, color:[[0,IV],[1,FOX]],
    alpha:[[0,0],[0.1,1],[0.85,1],[1,0]], sizeLife:[[0,0.4],[0.12,1],[0.85,1],[1,0.3]],
    trail:{ mat:"lf2_trail", life:0.6, minDist:0.03, color:[[0,IV],[0.4,FOX],[1,FOX2]] }, onStop:0.15 },
  LCORE("lf2_streak", [[0,IV],[0.3,FOX],[1,FOX2]], { size:[0.1,0.12] }),
  FIRE("03 Foxfire", "lf2_fire", [0,0,0], 6, [0.55,0.75], [0.3,0.42], [-0.6,-0.3], -0.35, [[0,IV],[0.25,FOX],[1,FOX2]], 0.85, { fudge:-2, speed:[0.3,0.8], shape:{type:"sphere", r:0.3, th:1} }),
  DUST("04 Motes", "lf2_dot", 5, [[0,IV],[1,FOX]], { gravity:-0.1 }),
];
const L_FOXFIRE_LITE = [ at(L_FOXFIRE[2], { name:"01 CoreLite", rateDist:7 }), at(L_FOXFIRE[1], { name:"02 OrbsLite" }) ];
// ---- 차큐스 1000 (엔젤): 하얀 깃털이 뒤집히며 흩날림 + 금빛 반짝임 ----
const L_ANGEL = [
  TIPGLOW("la_glow", [1,0.95,0.8], GOLD, [0.7,0.8]),
  LCORE("la_streak", [[0,IV],[0.35,CREAM],[1,GOLD]]),
  { name:"02 Feathers", mat:"la_feather", fudge:-3, world:true, pos:O, rateDist:4.6,
    life:[1.0,1.4], size:[0.28,0.38], speed:[0.4,0.8], shape:{type:"sphere", r:0.22, th:1}, drag:2.5, gravity:0.07,
    noise:{s:0.6, f:1.2, sp:0.8}, rot:[0,6.28], rotZ:[-2,2], flip:{x:4, y:1, cycles:3},
    color:[[0,IV],[1,[0.95,0.93,0.88]]], alpha:[[0,0],[0.06,1],[0.75,0.95],[1,0]], sizeLife:[[0,0.5],[0.12,1],[1,0.9]], onStop:0.2 },
  SPARK("03 Sparkle", "la_star", 2.4, [[0,IV],[0.5,GOLD],[1,CREAM]]),
  DUST("04 GoldDust", "g_dot", 6, [[0,CREAM],[1,GOLD]]),
];
const L_ANGEL_LITE = [ at(L_ANGEL[1], { name:"01 CoreLite", rateDist:7 }), at(L_ANGEL[2], { name:"02 FeathersLite", rateDist:1.0 }) ];
// ---- 차큐스 1000 (데빌): 위로 휘날리는 붉은 악마 불꽃 + 검은 연기 + 불씨 ----
const L_DEVIL = [
  TIPGLOW("ldv_glow", DFIRE, DFIRE2, [0.75,0.9]),
  SMOKE("02 Smoke", "u_smoke", [0,0.05,0], [-0.15,0.15], 5, [0.35,0.5], [0.5,0.65], [[0,[0.18,0.04,0.04]],[1,[0.04,0.01,0.01]]], 0.6),
  FIRE("03 Flames", "ldv_fire", [0,0,0], 12, [0.42,0.55], [0.55,0.75], [-1.5,-1.0], -0.6, [[0,[1,0.95,0.7]],[0.2,DFIRE],[0.6,DFIRE2],[1,[0.3,0.02,0.02]]], 0.85),
  LCORE("ldv_streak", [[0,[1,0.9,0.7]],[0.3,DFIRE],[1,DFIRE2]], { size:[0.1,0.12] }),
  DUST("04 Embers", "ldv_dot", 8, [[0,[1,0.9,0.6]],[0.5,DFIRE],[1,DFIRE2]], { gravity:-0.3, size:[0.025,0.04], life:[0.45,0.65], speed:[0.6,1.4] }),
];
const L_DEVIL_LITE = [ at(L_DEVIL[3], { name:"01 CoreLite", rateDist:7 }), at(L_DEVIL[2], { name:"02 FlamesLite", rateDist:2.5 }) ];

Object.assign(RARE_MATS, {
  lh_glow:{tex:"T_Glow", tint:[1.0,0.7,0.9], add:true}, lh_streak:{tex:"T_TrailStreak", tint:[2.0,1.5,1.9], add:true},
  lh_heart:{tex:"T_HeartGlow", tint:[1.8,1.3,1.6], add:true}, lh_star:{tex:"T_Star4", tint:[2.2,1.9,2.1], add:true},
  l_confetti:{tex:"T_Confetti", tint:[1,1,1], add:false},
  ld_glow:{tex:"T_Glow", tint:[0.8,0.5,1.0], add:true}, ld_streak:{tex:"T_TrailStreak", tint:[1.7,1.3,2.1], add:true},
  ld_heart:{tex:"T_HeartDark", tint:[1,1,1], add:false}, ld_star:{tex:"T_Star4", tint:[1.9,1.6,2.2], add:true},
  ldr_glow:{tex:"T_Glow", tint:[0.6,0.9,1.2], add:true}, ldr_fire:{tex:"T_Flame", tint:[1.3,1.7,2.2], add:true},
  ldr_streak:{tex:"T_TrailStreak", tint:[1.5,1.9,2.2], add:true}, ldr_dot:{tex:"T_Dot", tint:[1.5,1.9,2.3], add:true},
  lb_glow:{tex:"T_Glow", tint:[1.1,0.5,0.5], add:true}, lb_streak:{tex:"T_TrailStreak", tint:[2.1,1.3,1.3], add:true},
  lb_fly:{tex:"T_Butterfly", tint:[1,1,1], add:false}, lb_haze:{tex:"T_Nebula", tint:[1.4,0.8,0.8], add:true}, lb_dot:{tex:"T_Dot", tint:[2.0,1.3,1.3], add:true},
  lf2_glow:{tex:"T_Glow", tint:[0.7,1.0,1.2], add:true}, lf2_orb:{tex:"T_Glow", tint:[2.4,2.9,3.2], add:true},
  lf2_trail:{tex:"T_TrailStreak", tint:[1.5,2.0,2.3], add:true}, lf2_streak:{tex:"T_TrailStreak", tint:[1.5,2.0,2.3], add:true},
  lf2_fire:{tex:"T_Flame", tint:[1.4,1.9,2.2], add:true}, lf2_dot:{tex:"T_Dot", tint:[1.5,2.0,2.3], add:true},
  la_glow:{tex:"T_Glow", tint:[1.1,1.0,0.8], add:true}, la_streak:{tex:"T_TrailStreak", tint:[2.0,1.9,1.6], add:true},
  la_feather:{tex:"T_Feather", tint:[1,1,1], add:false}, la_star:{tex:"T_Star4", tint:[2.2,2.0,1.6], add:true},
  ldv_glow:{tex:"T_Glow", tint:[1.2,0.6,0.4], add:true}, ldv_fire:{tex:"T_Flame", tint:[2.2,1.5,1.1], add:true},
  ldv_streak:{tex:"T_TrailStreak", tint:[2.2,1.5,1.2], add:true}, ldv_dot:{tex:"T_Dot", tint:[2.2,1.5,1.0], add:true},
});
Object.assign(TRAILS, { l_heart:L_HEART, l_darkheart:L_DARKHEART, l_dragon:L_DRAGON, l_butterfly:L_BUTTERFLY, l_foxfire:L_FOXFIRE, l_angel:L_ANGEL, l_devil:L_DEVIL });
Object.assign(TRAILS_LITE, { l_heart:L_HEART_LITE, l_darkheart:L_DARKHEART_LITE, l_dragon:L_DRAGON_LITE, l_butterfly:L_BUTTERFLY_LITE, l_foxfire:L_FOXFIRE_LITE, l_angel:L_ANGEL_LITE, l_devil:L_DEVIL_LITE });
