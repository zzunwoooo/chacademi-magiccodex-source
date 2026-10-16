// BroomTrails — 빗자루 비행 궤적 팩 (클라이언트 배포용). 자동 생성 파일: rare_layers.js + unique_layers.js + legend_layers.js + pack_footer.js 를 이어 붙임 (build_pack.sh).
// 2026-10-16 사용자 확정: 일반 = basic, 희귀 11종 / 유일 7종 / 전설 7종은 빗자루마다 전용 궤적.
// 희귀 등급 빗자루 궤적 레이어 (원점 = 빗자루 솔 끝, +Z = 빗자루 앞(코), +Y = 위). 2026-10-16 기획:
//  rare_plus(차큐스 200·300) 기본보다 조금 더 화려 / rare_gold(고급스러운) 금빛 / wave1(레이서 1) 물결 / wave2(레이서 2) 더 센 물결
//  leaf_*(차큐스 400 나무 5종) 나뭇잎이 흩날림 / galaxy(초보 마녀) 은하수
// 가림 방지 원칙은 기본/전설과 같음: 줄기 0.2~0.3 s, 곧바로 옅어짐. 잎·별만 0.6~1.3 s 로 오래 남되 작고 띄엄띄엄.
const IV = [1, 1, 1], PALE = [0.85, 0.95, 1.0], SKY = [0.6, 0.82, 1.0];
const GOLD = [1.0, 0.84, 0.42], AMBER = [1.0, 0.62, 0.22], CREAM = [1.0, 0.95, 0.8];
const AQUA = [0.45, 0.92, 1.0], SEA = [0.25, 0.6, 1.0], DEEP = [0.2, 0.38, 0.95];
const NAVY = [0.22, 0.25, 0.75], VIO = [0.58, 0.42, 1.0], PINK = [1.0, 0.6, 0.9], STAR = [0.85, 0.9, 1.0];
const O = [0, 0, 0];
const at = (l, o) => Object.assign({}, l, o);

// ---- 공통: 기본 궤적 (일반 등급과 같은 수치) ----
const B_CORE = { name:"01 WindStreak", mat:"b_streak", mode:"stretch", vs:0.25, ls:5, fudge:0, world:true, pos:O, rateDist:10,
    life:[0.195,0.24], size:[0.05,0.065], shape:{type:"sphere", r:0.03, th:1}, vel:{z:[-0.7,-0.5], space:"local"}, drag:1.5,
    color:[[0,IV],[0.4,PALE],[1,SKY]], alpha:[[0,0.85],[0.4,0.38],[1,0]], sizeLife:[[0,1],[1,0.35]], onStop:0.12 };
const SIDE = (sx, mat, col, extra) => at({ name: sx < 0 ? "02 SideStreakL" : "03 SideStreakR", mat, mode:"stretch", vs:0.2, ls:4, fudge:1, world:true,
    pos:[sx * 0.1,-0.02,0.05], rateDist:6, life:[0.135,0.18], size:[0.025,0.035], shape:{type:"sphere", r:0.03, th:1},
    vel:{x: sx < 0 ? [-0.5,-0.3] : [0.3,0.5], z:[-0.6,-0.4], space:"local"}, drag:2,
    color:col, alpha:[[0,0.6],[0.5,0.25],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 }, extra || {});

// ================= rare_plus: 차큐스 200 / 300 — 기본 + 조금 굵은 줄기 + 은은한 반짝이 =================
const RP_CORE = at(B_CORE, { mat:"rp_streak", size:[0.06,0.075], life:[0.21,0.255], color:[[0,IV],[0.4,PALE],[1,[0.5,0.75,1.0]]] });
const RARE_PLUS = [
  RP_CORE,
  SIDE(-1, "rp_streak", [[0,IV],[1,SKY]]), SIDE(1, "rp_streak", [[0,IV],[1,SKY]]),
  { name:"04 Twinkle", mat:"rp_star", fudge:-3, world:true, pos:O, rateDist:1.3,
    life:[0.32,0.45], size:[0.06,0.1], speed:[0.15,0.4], shape:{type:"sphere", r:0.14, th:1}, drag:2.5,
    rot:[0,0.8], color:[[0,IV],[1,SKY]], alpha:[[0,1],[0.6,0.7],[1,0]], sizeLife:[[0,0],[0.15,1],[0.45,0.5],[0.65,0.8],[1,0]], onStop:0.1 },
  { name:"05 Motes", mat:"rp_dot", fudge:-2, world:true, pos:O, rateDist:2.4,
    life:[0.26,0.37], size:[0.025,0.04], speed:[0.2,0.6], shape:{type:"sphere", r:0.12, th:1}, drag:2,
    noise:{s:0.5, f:1.5, sp:0.8}, color:[[0,IV],[1,SKY]], alpha:[[0,0],[0.15,0.9],[0.6,0.5],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 },
];
const RARE_PLUS_LITE = [ at(RP_CORE, { name:"01 CoreLite", rateDist:6, size:[0.07,0.085] }) ];

// ================= rare_gold: 고급스러운 빗자루 — 금빛 줄기 + 떨어지는 금박 + 금가루 =================
const G_CORE = at(B_CORE, { name:"01 GoldStreak", mat:"g_streak", size:[0.06,0.075], life:[0.22,0.27], color:[[0,CREAM],[0.35,GOLD],[1,AMBER]], alpha:[[0,0.9],[0.4,0.4],[1,0]] });
const G_LEAF = { name:"04 GoldLeaf", mat:"g_star", fudge:-3, world:true, pos:O, rateDist:3.6,
    life:[0.5,0.7], size:[0.07,0.12], speed:[0.15,0.45], shape:{type:"sphere", r:0.14, th:1}, drag:3, gravity:0.18,
    rot:[0,0.8], rotZ:[-3,3], color:[[0,CREAM],[0.4,GOLD],[1,AMBER]], alpha:[[0,1],[0.7,0.75],[1,0]],
    sizeLife:[[0,0],[0.1,1],[0.3,0.45],[0.5,0.9],[0.7,0.4],[1,0]], onStop:0.12 };
const RARE_GOLD = [
  { name:"00 TipGlow", mat:"g_glow", fudge:2, pos:[0,0,0.08], rate:12, life:0.3, size:[0.32,0.4],
    color:[[0,GOLD],[1,AMBER]], alpha:[[0,0],[0.3,0.22],[1,0]], sizeLife:[[0,0.8],[1,1.1]], onStop:0.1 },
  G_CORE,
  SIDE(-1, "g_streak", [[0,CREAM],[1,GOLD]], { rateDist:7 }), SIDE(1, "g_streak", [[0,CREAM],[1,GOLD]], { rateDist:7 }),
  G_LEAF,
  { name:"05 GoldDust", mat:"g_dot", fudge:-2, world:true, pos:O, rateDist:6,
    life:[0.3,0.45], size:[0.018,0.03], speed:[0.3,0.8], shape:{type:"sphere", r:0.15, th:1}, drag:2.5, gravity:0.08,
    noise:{s:0.4, f:1.6, sp:0.8}, color:[[0,CREAM],[1,GOLD]], alpha:[[0,1],[0.5,0.6],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.12 },
];
const RARE_GOLD_LITE = [ at(G_CORE, { name:"01 GoldLite", rateDist:6, size:[0.07,0.085] }), at(G_LEAF, { name:"02 GoldLeafLite", rateDist:0.8 }) ];

// ================= wave1 / wave2: 차큐스 레이서 1 / 2 — 물결 =================
// 물결 띠: 사인 물결 두 줄이 엇갈린 텍스처(T_Ripple)를 늘린 입자로 이어 붙임. 겹쳐 흐르며 출렁이는 물길처럼 보인다.
const RIPPLE = (name, pos, vx, size, rate, life, col, al) => ({ name, mat:"w_ripple", mode:"stretch", vs:0, ls:7.5, fudge:3, world:true, pos, rateDist:rate,
    life, size, shape:{type:"sphere", r:0.02, th:1}, vel:{x:vx, z:[-0.7,-0.7], space:"local"}, drag:1.5,
    color:col, alpha:[[0,0],[0.1,al],[0.55,al * 0.5],[1,0]], sizeLife:[[0,0.7],[0.3,1],[1,1.1]], onStop:0.12 });
const SPRAY = (rate, col) => ({ name:"06 Spray", mat:"w_dot", fudge:-2, world:true, pos:[0,-0.04,0], rateDist:rate,
    life:[0.3,0.45], size:[0.022,0.04], speed:[0.6,1.2], shape:{type:"sphere", r:0.1, th:1}, drag:2, gravity:0.35,
    color:col, alpha:[[0,1],[0.5,0.6],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 });
const W1_CORE = at(B_CORE, { name:"01 CoreStreak", mat:"w_streak", size:[0.055,0.07], color:[[0,IV],[0.35,AQUA],[1,SEA]] });
const WAVE1 = [
  W1_CORE,
  RIPPLE("02 Ripple", [0,-0.02,0], [-0.05,0.05], [0.17,0.19], 1.1, [0.3,0.34], [[0,IV],[0.35,AQUA],[1,SEA]], 0.75),
  SPRAY(2.5, [[0,IV],[1,AQUA]]),
];
const WAVE1_LITE = [ at(W1_CORE, { name:"01 CoreLite", rateDist:6, size:[0.065,0.08] }), at(WAVE1[1], { name:"02 RippleLite", rateDist:0.8 }) ];
const W2_CORE = at(B_CORE, { name:"01 CoreStreak", mat:"w_streak", size:[0.065,0.08], life:[0.22,0.27], color:[[0,IV],[0.3,AQUA],[1,DEEP]], alpha:[[0,0.95],[0.4,0.42],[1,0]] });
const WAVE2 = [
  { name:"00 TipGlow", mat:"w_glow", fudge:2, pos:[0,0,0.08], rate:12, life:0.3, size:[0.3,0.38],
    color:[[0,AQUA],[1,SEA]], alpha:[[0,0],[0.3,0.2],[1,0]], sizeLife:[[0,0.8],[1,1.1]], onStop:0.1 },
  W2_CORE,
  RIPPLE("02 RippleC", [0,-0.02,0], [-0.05,0.05], [0.21,0.24], 1.2, [0.32,0.36], [[0,IV],[0.3,AQUA],[1,DEEP]], 0.85),
  RIPPLE("03 RippleL", [-0.12,-0.06,0.02], [-0.45,-0.3], [0.14,0.16], 0.9, [0.28,0.32], [[0,PALE],[0.4,SEA],[1,DEEP]], 0.6),
  RIPPLE("04 RippleR", [ 0.12,-0.06,0.02], [0.3,0.45], [0.14,0.16], 0.9, [0.28,0.32], [[0,PALE],[0.4,SEA],[1,DEEP]], 0.6),
  { name:"05 Crest", mat:"w_crest", mode:"stretch", vs:0.25, ls:3, fudge:3, world:true, pos:[0,-0.02,0], rateDist:5,
    life:[0.22,0.28], size:[0.1,0.13], shape:{type:"sphere", r:0.04, th:1}, vel:{z:[-0.7,-0.5], space:"local"}, drag:1.5,
    noise:{s:0.8, f:1.0, sp:1.4}, color:[[0,IV],[0.5,AQUA],[1,SEA]], alpha:[[0,0],[0.1,0.4],[0.5,0.2],[1,0]], sizeLife:[[0,0.7],[0.3,1],[1,0.6]], onStop:0.12 },
  SPRAY(4.5, [[0,IV],[0.5,AQUA],[1,SEA]]),
];
const WAVE2_LITE = [ at(W2_CORE, { name:"01 CoreLite", rateDist:6, size:[0.075,0.09] }), at(WAVE2[2], { name:"02 RippleLite", rateDist:0.9 }) ];

// ================= leaf_*: 차큐스 400 나무 5종 — 그 나무의 잎(벚나무는 꽃잎)이 흩날림 =================
// 잎은 색이 든 텍스처 + 알파 블렌드, 4칸 플립북(정면→옆면→뒷면)으로 빙글빙글 뒤집히는 느낌. 회전 + 노이즈로 팔랑임, 천천히 떨어짐.
function leafSet(key, opt) {
  const core = at(B_CORE, { name:"01 Breeze", mat:"lf_streak", size:[0.045,0.06], color:[[0,IV],[0.4,opt.tint],[1,opt.tint2]], alpha:[[0,0.65],[0.4,0.28],[1,0]] });
  const leaf = { name:"02 Leaves", mat:"leaf_" + key, fudge:-3, world:true, pos:O, rateDist:opt.rate,
    life:opt.life || [0.9,1.3], size:opt.size, speed:[0.3,0.75], shape:{type:"sphere", r:0.16, th:1}, drag:2.2, gravity:opt.grav,
    noise:{s:opt.flutter || 0.7, f:1.3, sp:0.9}, rot:[0,6.28], rotZ:[-3.2,3.2], flip:{x:4, y:1, cycles:opt.cycles || 3},
    color:[[0,IV],[1,[0.92,0.92,0.92]]], alpha:[[0,0],[0.06,1],[0.75,0.95],[1,0]], sizeLife:[[0,0.5],[0.12,1],[1,0.85]], onStop:0.2 };
  const motes = { name:"03 Motes", mat:"lf_dot", fudge:-2, world:true, pos:O, rateDist:1.5,
    life:[0.3,0.45], size:[0.02,0.032], speed:[0.2,0.5], shape:{type:"sphere", r:0.12, th:1}, drag:2,
    noise:{s:0.5, f:1.5, sp:0.8}, color:[[0,IV],[1,opt.tint]], alpha:[[0,0],[0.15,0.8],[0.6,0.4],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 };
  return { full:[core, leaf, motes], lite:[ at(leaf, { name:"01 LeavesLite", rateDist: opt.rate * 0.4 }) ] };
}
const LEAF = {
  oak:      leafSet("oak",      { tint:[0.8,1.0,0.65], tint2:[0.6,0.85,0.45], rate:3.0, size:[0.2,0.26],  grav:0.14 }),
  cherry:   leafSet("cherry",   { tint:[1.0,0.85,0.92], tint2:[1.0,0.65,0.82], rate:4.5, size:[0.14,0.18], grav:0.05, flutter:1.0, life:[1.0,1.4], cycles:4 }),
  birch:    leafSet("birch",    { tint:[1.0,1.0,0.7],  tint2:[0.95,0.85,0.45], rate:3.4, size:[0.16,0.21], grav:0.12 }),
  mahogany: leafSet("mahogany", { tint:[1.0,0.8,0.65], tint2:[0.9,0.55,0.4],  rate:3.0, size:[0.19,0.25], grav:0.16 }),
  bamboo:   leafSet("bamboo",   { tint:[0.85,1.0,0.7], tint2:[0.6,0.9,0.5],   rate:2.8, size:[0.26,0.33],  grav:0.1, cycles:2 }),
};

// ================= galaxy: 초보 마녀의 빗자루 — 은하수 =================
const GX_CORE = at(B_CORE, { name:"01 StarStreak", mat:"gx_streak", size:[0.05,0.065], color:[[0,IV],[0.35,STAR],[1,VIO]] });
const GX_STARS = { name:"04 Stars", mat:"gx_star", fudge:-3, world:true, pos:O, rateDist:3.6,
    life:[0.55,0.8], size:[0.06,0.12], speed:[0.1,0.35], shape:{type:"sphere", r:0.25, th:1}, drag:2, gravity:0.03,
    rot:[0,0.8], color:[[0,IV],[0.5,STAR],[1,PINK]], alpha:[[0,1],[0.7,0.8],[1,0]],
    sizeLife:[[0,0],[0.12,1],[0.3,0.4],[0.5,0.9],[0.7,0.35],[0.85,0.7],[1,0]], onStop:0.15 };
const GALAXY = [
  GX_CORE,
  { name:"02 NebulaL", mat:"gx_neb", fudge:4, world:true, pos:[-0.08,-0.05,0], rateDist:3.2,
    life:[0.5,0.65], size:[0.32,0.45], speed:[0.1,0.3], shape:{type:"sphere", r:0.12, th:1}, vel:{x:[-0.4,-0.2], z:[-0.4,-0.2], space:"local"}, drag:1.5,
    noise:{s:0.3, f:1.0, sp:0.5}, rot:[0,6.28], rotZ:[-0.6,0.6], color:[[0,VIO],[0.5,NAVY],[1,PINK]], alpha:[[0,0],[0.15,0.45],[0.6,0.22],[1,0]], sizeLife:[[0,0.5],[0.4,1],[1,1.2]], onStop:0.15 },
  { name:"03 NebulaR", mat:"gx_neb", fudge:4, world:true, pos:[0.08,-0.05,0], rateDist:3.2,
    life:[0.5,0.65], size:[0.32,0.45], speed:[0.1,0.3], shape:{type:"sphere", r:0.12, th:1}, vel:{x:[0.2,0.4], z:[-0.4,-0.2], space:"local"}, drag:1.5,
    noise:{s:0.3, f:1.0, sp:0.5}, rot:[0,6.28], rotZ:[-0.6,0.6], color:[[0,NAVY],[0.5,VIO],[1,[0.35,0.5,1.0]]], alpha:[[0,0],[0.15,0.45],[0.6,0.22],[1,0]], sizeLife:[[0,0.5],[0.4,1],[1,1.2]], onStop:0.15 },
  GX_STARS,
  { name:"05 StarDust", mat:"gx_dot", fudge:-2, world:true, pos:O, rateDist:9,
    life:[0.4,0.6], size:[0.012,0.024], speed:[0.2,0.6], shape:{type:"sphere", r:0.2, th:1}, drag:2.5,
    noise:{s:0.4, f:1.6, sp:0.8}, color:[[0,IV],[1,STAR]], alpha:[[0,1],[0.5,0.7],[1,0]], sizeLife:[[0,1],[1,0.5]], onStop:0.12 },
];
const GALAXY_LITE = [ at(GX_CORE, { name:"01 CoreLite", rateDist:6, size:[0.06,0.075] }), at(GX_STARS, { name:"02 StarsLite", rateDist:1.0 }) ];

const RARE_MATS = {
  b_streak:{tex:"T_TrailStreak", tint:[1.5,1.7,1.9], add:true},
  b_dot:{tex:"T_Dot", tint:[1.6,1.8,2.0], add:true},
  rp_streak:{tex:"T_TrailStreak", tint:[1.6,1.8,2.0], add:true},
  rp_star:{tex:"T_Star4", tint:[1.8,1.9,2.1], add:true},
  rp_dot:{tex:"T_Dot", tint:[1.6,1.8,2.0], add:true},
  g_streak:{tex:"T_TrailStreak", tint:[2.0,1.75,1.3], add:true},
  g_star:{tex:"T_Star4", tint:[2.2,1.9,1.3], add:true},
  g_dot:{tex:"T_Dot", tint:[2.0,1.7,1.2], add:true},
  g_glow:{tex:"T_Glow", tint:[1.0,0.8,0.5], add:true},
  w_streak:{tex:"T_TrailStreak", tint:[1.4,1.8,2.0], add:true},
  w_ripple:{tex:"T_Ripple", tint:[1.4,1.8,2.1], add:true},
  w_dot:{tex:"T_Dot", tint:[1.5,1.9,2.1], add:true},
  w_crest:{tex:"T_WaveStreak", tint:[1.2,1.6,1.9], add:true},
  w_glow:{tex:"T_Glow", tint:[0.6,0.9,1.1], add:true},
  lf_streak:{tex:"T_TrailStreak", tint:[1.3,1.4,1.3], add:true},
  lf_dot:{tex:"T_Dot", tint:[1.5,1.6,1.3], add:true},
  leaf_oak:{tex:"T_Leaf_Oak", tint:[1,1,1], add:false},
  leaf_cherry:{tex:"T_Leaf_Cherry", tint:[1,1,1], add:false},
  leaf_birch:{tex:"T_Leaf_Birch", tint:[1,1,1], add:false},
  leaf_mahogany:{tex:"T_Leaf_Mahogany", tint:[1,1,1], add:false},
  leaf_bamboo:{tex:"T_Leaf_Bamboo", tint:[1,1,1], add:false},
  gx_streak:{tex:"T_TrailStreak", tint:[1.6,1.6,2.0], add:true},
  gx_neb:{tex:"T_Nebula", tint:[1.0,1.0,1.3], add:true},
  gx_star:{tex:"T_Star4", tint:[2.1,2.1,2.3], add:true},
  gx_dot:{tex:"T_Dot", tint:[1.8,1.8,2.1], add:true},
};
const BASIC = [ B_CORE, SIDE(-1, "b_streak", [[0,IV],[1,SKY]]), SIDE(1, "b_streak", [[0,IV],[1,SKY]]),
  { name:"04 Motes", mat:"b_dot", fudge:-2, world:true, pos:O, rateDist:1.6,
    life:[0.262,0.375], size:[0.025,0.04], speed:[0.2,0.6], shape:{type:"sphere", r:0.12, th:1}, drag:2,
    noise:{s:0.5, f:1.5, sp:0.8}, color:[[0,IV],[1,SKY]], alpha:[[0,0],[0.15,0.9],[0.6,0.5],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 } ];
const TRAILS = { basic:BASIC, rare_plus:RARE_PLUS, rare_gold:RARE_GOLD, wave1:WAVE1, wave2:WAVE2,
  leaf_oak:LEAF.oak.full, leaf_cherry:LEAF.cherry.full, leaf_birch:LEAF.birch.full, leaf_mahogany:LEAF.mahogany.full, leaf_bamboo:LEAF.bamboo.full, galaxy:GALAXY };
const TRAILS_LITE = { rare_plus:RARE_PLUS_LITE, rare_gold:RARE_GOLD_LITE, wave1:WAVE1_LITE, wave2:WAVE2_LITE,
  leaf_oak:LEAF.oak.lite, leaf_cherry:LEAF.cherry.lite, leaf_birch:LEAF.birch.lite, leaf_mahogany:LEAF.mahogany.lite, leaf_bamboo:LEAF.bamboo.lite, galaxy:GALAXY_LITE };
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

// ================= 팩 구성 (모드가 켜고 끄는 시스템). 원점 P = 빗자루 솔 끝, M = 빗자루 방향(+Z 코, +Y 위) =================
// 역할 static: 모드(BroomTrails)가 매 틱 move/basis 로 옮긴다. *_lite = 멀리 있는 사람용(입자 수 적음).
// 시스템 ID = 궤적 키. 효과 ID = claude:broomtrails/<키>. 연결표는 chacademy-motion 의 broom_trails.json.
const MATS = RARE_MATS;
const sys = (id, layers) => ({ id, role:"static", loop:true, duration:1.0, layers });
const PACK_KEYS = Object.keys(TRAILS);
const SYSTEMS = [];
for (const k of PACK_KEYS) {
  SYSTEMS.push(sys(k, TRAILS[k]));
  SYSTEMS.push(sys(k + "_lite", k === "basic" ? [ Object.assign({}, B_CORE, { name:"01 WindStreakLite", rateDist:6, size:[0.06,0.075] }) ] : TRAILS_LITE[k]));
}
const SCENE = {
  name: "BroomTrails",
  bloom:{ threshold:0.95, intensity:1.2, scatter:0.65, tint:[1.0,0.97,1.0] }, tonemapping:"neutral",
  systems: SYSTEMS,
  // 이 팩의 미리보기는 정지 상태다. 비행 모습은 BroomTrails_Preview.html(빗자루별 attached 미리보기)에서 확인.
  demo:{ interval:3, bg:[0.52,0.62,0.74], floor:[0.33,0.42,0.30], grid:[0.38,0.48,0.34], dummy:false,
    scenarios:{ hover:{ label:"정지(솔 끝)", anchor:[0,1,0], camera:{ target:[0,1,0], dist:3, yaw:-120, pitch:15 } } } },
};
