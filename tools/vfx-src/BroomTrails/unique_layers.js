// ================= 유일 등급 궤적 (rare_layers.js 의 공통 정의 뒤에 붙음) =================
// u_dark(차큐스 500) 아주 어두운 고급 / u_classic(미드나잇) 클래식 고급 / u_emerald 초록 + 에너지 고리 / u_crimson 빨강 + 에너지 고리
// u_sonic(쏜살같은) 소닉붐 고리 연속 / u_holo(건메탈) 마법공학 홀로그램 물결 / u_twin(바이올렛 트윈테일) 고리 두 줄 + 보라 궤적
// 에너지 고리 = 빗자루 축에 수직으로 선 고리(사각 메시를 x 90° 세움), 솔 끝에서 생겨 커지며 사라짐, 이동 거리마다 하나씩.
const EMER = [0.3,1.0,0.6], EMER_D = [0.05,0.55,0.32], RED = [1.0,0.32,0.28], RED_D = [0.6,0.05,0.08];
const SILVER = [0.82,0.85,0.92], CHAMP = [1.0,0.92,0.75], NAVYS = [0.1,0.14,0.32], HOLO = [0.4,0.95,1.0], HOLO_D = [0.15,0.55,0.9];
const PURP = [0.72,0.45,1.0], PURP_D = [0.42,0.18,0.85];
const CORE = (mat, col, ex) => at(B_CORE, Object.assign({ name:"01 Core", mat, size:[0.06,0.075], life:[0.22,0.27], color:col, alpha:[[0,0.95],[0.4,0.42],[1,0]] }, ex || {}));
const RING = (name, mat, pos, rate, size, life, col, al, ex) => at({ name, mat, mode:"mesh", mesh:"quad", fudge:2, world:true, pos, rateDist:rate,
    life, size, rot3:[[1.5708,1.5708],[0,0],[0,0]], vel:{z:[-1.2,-1.2], space:"local"}, drag:2,
    color:col, alpha:[[0,0],[0.08,al],[0.5,al * 0.5],[1,0]], sizeLife:[[0,0.35],[0.5,0.85],[1,1.1]], onStop:0.1 }, ex || {});
const SMOKE = (name, mat, pos, vx, rate, size, life, col, al) => ({ name, mat, fudge:5, world:true, pos, rateDist:rate,
    life, size, speed:[0.05,0.2], shape:{type:"sphere", r:0.08, th:1}, vel:{x:vx, z:[-0.4,-0.2], space:"local"}, drag:1.5,
    noise:{s:0.25, f:1.0, sp:0.5}, rot:[0,6.28], rotZ:[-0.5,0.5], color:col, alpha:[[0,0],[0.12,al],[0.6,al * 0.45],[1,0]], sizeLife:[[0,0.45],[0.4,1],[1,1.25]], onStop:0.15 });
const GLINT = (name, mat, rate, size, col, ex) => at({ name, mat, fudge:-3, world:true, pos:O, rateDist:rate,
    life:[0.35,0.5], size, speed:[0.15,0.4], shape:{type:"sphere", r:0.16, th:1}, drag:2.5, rot:[0,0.8],
    color:col, alpha:[[0,1],[0.6,0.7],[1,0]], sizeLife:[[0,0],[0.15,1],[0.45,0.5],[0.65,0.8],[1,0]], onStop:0.1 }, ex || {});
const DUST = (name, mat, rate, col, ex) => at({ name, mat, fudge:-2, world:true, pos:O, rateDist:rate,
    life:[0.3,0.45], size:[0.016,0.028], speed:[0.3,0.8], shape:{type:"sphere", r:0.15, th:1}, drag:2.5, gravity:0.06,
    noise:{s:0.4, f:1.6, sp:0.8}, color:col, alpha:[[0,1],[0.5,0.6],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.12 }, ex || {});

// ---- u_dark: 차큐스 500 — 아예 어두운 색. 검보라 연기(알파) + 가는 은빛 줄기 + 은빛 반짝임 ----
const U_DARK = [
  SMOKE("02 ShadowL", "u_smoke", [-0.06,-0.03,0], [-0.3,-0.1], 7, [0.34,0.46], [0.45,0.55], [[0,[0.1,0.08,0.14]],[1,[0.03,0.02,0.05]]], 0.85),
  SMOKE("03 ShadowR", "u_smoke", [ 0.06,-0.03,0], [0.1,0.3], 7, [0.34,0.46], [0.45,0.55], [[0,[0.1,0.08,0.14]],[1,[0.03,0.02,0.05]]], 0.85),
  at(B_CORE, { name:"04 Shadow", mat:"u_shadow", fudge:3, size:[0.11,0.13], life:[0.26,0.3], color:[[0,[0.08,0.06,0.1]],[1,[0.02,0.02,0.03]]], alpha:[[0,0.85],[0.4,0.45],[1,0]] }),
  CORE("u_silver", [[0,IV],[0.3,SILVER],[1,[0.35,0.35,0.45]]], { size:[0.035,0.045], fudge:-1 }),
  GLINT("05 Glint", "u_star_silver", 1.4, [0.06,0.1], [[0,IV],[1,SILVER]]),
];
const U_DARK_LITE = [ at(U_DARK[2], { name:"01 ShadowLite", rateDist:6 }), at(U_DARK[3], { name:"02 CoreLite", rateDist:6 }) ];
// ---- u_classic: 미드나잇 — 남색 연기 + 상아·샴페인 줄기 + 은빛 반짝임 + 금가루 ----
const U_CLASSIC = [
  SMOKE("02 NavyL", "u_smoke", [-0.06,-0.03,0], [-0.3,-0.1], 4, [0.22,0.3], [0.38,0.46], [[0,[0.16,0.2,0.42]],[1,[0.06,0.08,0.2]]], 0.4),
  SMOKE("03 NavyR", "u_smoke", [ 0.06,-0.03,0], [0.1,0.3], 4, [0.22,0.3], [0.38,0.46], [[0,[0.16,0.2,0.42]],[1,[0.06,0.08,0.2]]], 0.4),
  CORE("u_ivory", [[0,IV],[0.35,CHAMP],[1,[0.55,0.62,0.85]]]),
  SIDE(-1, "u_ivory", [[0,IV],[1,SILVER]]), SIDE(1, "u_ivory", [[0,IV],[1,SILVER]]),
  GLINT("06 Glint", "u_star_silver", 1.6, [0.06,0.1], [[0,IV],[1,SILVER]]),
  DUST("07 GoldDust", "g_dot", 4, [[0,CREAM],[1,GOLD]]),
];
const U_CLASSIC_LITE = [ at(U_CLASSIC[2], { name:"01 CoreLite", rateDist:6, size:[0.07,0.085] }) ];
// ---- u_emerald: 에메랄드 — 에메랄드 줄기 + 초록 에너지 고리 + 금빛 가루 ----
const U_EMERALD = [
  CORE("u_emerald", [[0,IV],[0.3,EMER],[1,EMER_D]]),
  SIDE(-1, "u_emerald", [[0,IV],[1,EMER]]), SIDE(1, "u_emerald", [[0,IV],[1,EMER]]),
  RING("04 Ring", "u_ring_emerald", [0,0,-0.05], 0.75, [0.85,0.95], [0.34,0.38], [[0,IV],[0.3,EMER],[1,EMER_D]], 0.85),
  DUST("05 GoldDust", "g_dot", 4, [[0,CREAM],[1,GOLD]]),
  GLINT("06 Glint", "u_star_emerald", 0.9, [0.05,0.08], [[0,IV],[1,EMER]]),
];
const U_EMERALD_LITE = [ at(U_EMERALD[0], { name:"01 CoreLite", rateDist:6, size:[0.07,0.085] }), at(U_EMERALD[3], { name:"02 RingLite", rateDist:0.4 }) ];
// ---- u_crimson: 크림슨 — 붉은 줄기 + 빨간 에너지 고리 + 검붉은 연기 + 불씨 ----
const U_CRIMSON = [
  SMOKE("02 SmokeL", "u_smoke", [-0.05,-0.03,0], [-0.25,-0.1], 3, [0.2,0.28], [0.34,0.42], [[0,[0.35,0.04,0.06]],[1,[0.08,0.01,0.02]]], 0.4),
  SMOKE("03 SmokeR", "u_smoke", [ 0.05,-0.03,0], [0.1,0.25], 3, [0.2,0.28], [0.34,0.42], [[0,[0.35,0.04,0.06]],[1,[0.08,0.01,0.02]]], 0.4),
  CORE("u_crimson", [[0,IV],[0.3,RED],[1,RED_D]]),
  RING("05 Ring", "u_ring_crimson", [0,0,-0.05], 0.75, [0.85,0.95], [0.34,0.38], [[0,IV],[0.3,RED],[1,RED_D]], 0.85),
  DUST("06 Embers", "u_dot_red", 4.5, [[0,[1,0.85,0.6]],[0.5,RED],[1,RED_D]], { gravity:-0.05 }),
  GLINT("07 Glint", "u_star_red", 0.8, [0.05,0.08], [[0,IV],[1,RED]]),
];
const U_CRIMSON_LITE = [ at(U_CRIMSON[2], { name:"01 CoreLite", rateDist:6, size:[0.07,0.085] }), at(U_CRIMSON[3], { name:"02 RingLite", rateDist:0.4 }) ];
// ---- u_sonic: 쏜살같은 빗자루 — 촘촘히 이어지는 소닉붐 고리(굵은 앞 테두리) + 원뿔처럼 벌어지는 바람 줄기 ----
const U_SONIC = [
  CORE("u_sonic", [[0,IV],[0.4,PALE],[1,SKY]], { size:[0.065,0.08] }),
  SIDE(-1, "u_sonic", [[0,IV],[1,PALE]], { vel:{x:[-1.2,-0.8], z:[-0.6,-0.4], space:"local"}, rateDist:8 }),
  SIDE(1, "u_sonic", [[0,IV],[1,PALE]], { vel:{x:[0.8,1.2], z:[-0.6,-0.4], space:"local"}, rateDist:8 }),
  at(SIDE(1, "u_sonic", [[0,IV],[1,PALE]], { vel:{y:[0.8,1.2], z:[-0.6,-0.4], space:"local"}, rateDist:6 }), { name:"04 SideStreakU", pos:[0,0.08,0.05] }),
  at(SIDE(1, "u_sonic", [[0,IV],[1,PALE]], { vel:{y:[-1.2,-0.8], z:[-0.6,-0.4], space:"local"}, rateDist:6 }), { name:"05 SideStreakD", pos:[0,-0.08,0.05] }),
  RING("06 Shock", "u_shock", [0,0,-0.05], 1.7, [0.8,0.9], [0.26,0.3], [[0,IV],[0.5,PALE],[1,SKY]], 0.75, { sizeLife:[[0,0.3],[0.4,0.9],[1,1.35]] }),
];
const U_SONIC_LITE = [ at(U_SONIC[0], { name:"01 CoreLite", rateDist:6, size:[0.075,0.09] }), at(U_SONIC[5], { name:"02 ShockLite", rateDist:0.6 }) ];
// ---- u_holo: 건메탈 레이서 — 마법공학 홀로그램. 주사선 물결 띠가 출렁이고, 홀로 문양이 깜빡이며 흩어짐 ----
const HOLOWAVE = (name, pos, vx, size, rate, al) => ({ name, mat:"u_holowave", mode:"stretch", vs:0, ls:6.5, fudge:3, world:true, pos, rateDist:rate,
    life:[0.32,0.36], size, shape:{type:"sphere", r:0.02, th:1}, vel:{x:vx, z:[-0.7,-0.7], space:"local"}, drag:1.5,
    color:[[0,IV],[0.3,HOLO],[1,HOLO_D]], alpha:[[0,0],[0.1,al],[0.3,al * 0.6],[0.4,al],[0.6,al * 0.4],[1,0]], sizeLife:[[0,0.7],[0.3,1],[1,1.1]], onStop:0.1 });
const U_HOLO = [
  CORE("u_holo", [[0,IV],[0.3,HOLO],[1,HOLO_D]], { size:[0.05,0.065] }),
  HOLOWAVE("02 HoloWaveL", [-0.1,0.02,0], [-0.25,-0.15], [0.2,0.22], 1.1, 0.8),
  HOLOWAVE("03 HoloWaveR", [ 0.1,-0.04,0], [0.15,0.25], [0.2,0.22], 1.1, 0.8),
  { name:"04 Glyphs", mat:"u_glyph", fudge:-3, world:true, pos:O, rateDist:2.2,
    life:[0.4,0.55], size:[0.07,0.1], speed:[0.3,0.7], shape:{type:"sphere", r:0.2, th:1}, drag:3, flip:{x:4, y:1, cycles:2},
    color:[[0,IV],[1,HOLO]], alpha:[[0,0],[0.08,1],[0.2,0.3],[0.3,1],[0.5,0.4],[0.6,0.9],[1,0]], sizeLife:[[0,0.4],[0.1,1],[1,0.7]], onStop:0.1 },
  DUST("05 Bits", "u_dot_holo", 6, [[0,IV],[1,HOLO]], { gravity:0 }),
];
const U_HOLO_LITE = [ at(U_HOLO[0], { name:"01 CoreLite", rateDist:6, size:[0.065,0.08] }), at(U_HOLO[1], { name:"02 HoloLite", rateDist:0.8 }) ];
// ---- u_twin: 바이올렛 트윈테일 — 두 솔 다발에서 각각 보라 줄기 + 에너지 고리 두 줄 ----
const TW = 0.13;
const U_TWIN = [
  at(CORE("u_violet", [[0,IV],[0.3,PURP],[1,PURP_D]], { size:[0.05,0.065] }), { name:"01 CoreL", pos:[-TW,0,0] }),
  at(CORE("u_violet", [[0,IV],[0.3,PURP],[1,PURP_D]], { size:[0.05,0.065] }), { name:"02 CoreR", pos:[ TW,0,0] }),
  RING("03 RingL", "u_ring_violet", [-TW,0,-0.05], 0.9, [0.55,0.62], [0.3,0.34], [[0,IV],[0.3,PURP],[1,PURP_D]], 0.85),
  RING("04 RingR", "u_ring_violet", [ TW,0,-0.05], 0.9, [0.55,0.62], [0.3,0.34], [[0,IV],[0.3,PURP],[1,PURP_D]], 0.85),
  SMOKE("05 Haze", "u_haze", [0,-0.03,0], [-0.1,0.1], 3, [0.3,0.4], [0.36,0.44], [[0,PURP],[1,PURP_D]], 0.22),
  DUST("06 Dust", "u_dot_violet", 5, [[0,IV],[0.5,PURP],[1,PINK]]),
];
const U_TWIN_LITE = [ at(U_TWIN[0], { name:"01 CoreLLite", rateDist:5 }), at(U_TWIN[1], { name:"02 CoreRLite", rateDist:5 }) ];

Object.assign(RARE_MATS, {
  u_smoke:{tex:"T_Nebula", tint:[1,1,1], add:false},
  u_shadow:{tex:"T_TrailStreak", tint:[1,1,1], add:false},
  u_silver:{tex:"T_TrailStreak", tint:[1.6,1.65,1.8], add:true},
  u_star_silver:{tex:"T_Star4", tint:[1.9,1.95,2.1], add:true},
  u_ivory:{tex:"T_TrailStreak", tint:[1.8,1.7,1.5], add:true},
  u_emerald:{tex:"T_TrailStreak", tint:[1.3,2.0,1.5], add:true},
  u_ring_emerald:{tex:"T_Ring", tint:[1.2,2.0,1.4], add:true},
  u_star_emerald:{tex:"T_Star4", tint:[1.5,2.2,1.7], add:true},
  u_crimson:{tex:"T_TrailStreak", tint:[2.1,1.3,1.2], add:true},
  u_ring_crimson:{tex:"T_Ring", tint:[2.1,1.2,1.1], add:true},
  u_star_red:{tex:"T_Star4", tint:[2.2,1.5,1.3], add:true},
  u_dot_red:{tex:"T_Dot", tint:[2.0,1.3,1.0], add:true},
  u_sonic:{tex:"T_TrailStreak", tint:[1.8,1.9,2.1], add:true},
  u_shock:{tex:"T_ShockRing", tint:[1.5,1.7,1.9], add:true},
  u_holo:{tex:"T_TrailStreak", tint:[1.3,1.9,2.2], add:true},
  u_holowave:{tex:"T_HoloWave", tint:[1.3,1.9,2.2], add:true},
  u_glyph:{tex:"T_HoloGlyph", tint:[1.4,2.0,2.3], add:true},
  u_dot_holo:{tex:"T_Dot", tint:[1.4,2.0,2.3], add:true},
  u_violet:{tex:"T_TrailStreak", tint:[1.7,1.4,2.2], add:true},
  u_ring_violet:{tex:"T_Ring", tint:[1.7,1.3,2.2], add:true},
  u_haze:{tex:"T_Nebula", tint:[1.2,1.0,1.5], add:true},
  u_dot_violet:{tex:"T_Dot", tint:[1.8,1.5,2.2], add:true},
});
Object.assign(TRAILS, { u_dark:U_DARK, u_classic:U_CLASSIC, u_emerald:U_EMERALD, u_crimson:U_CRIMSON, u_sonic:U_SONIC, u_holo:U_HOLO, u_twin:U_TWIN });
Object.assign(TRAILS_LITE, { u_dark:U_DARK_LITE, u_classic:U_CLASSIC_LITE, u_emerald:U_EMERALD_LITE, u_crimson:U_CRIMSON_LITE, u_sonic:U_SONIC_LITE, u_holo:U_HOLO_LITE, u_twin:U_TWIN_LITE });
