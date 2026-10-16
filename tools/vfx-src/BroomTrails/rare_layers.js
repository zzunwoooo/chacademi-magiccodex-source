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
