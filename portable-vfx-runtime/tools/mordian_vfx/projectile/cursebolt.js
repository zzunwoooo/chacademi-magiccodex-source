// MordianCurseBolt — 모르디안의 원령 "저주 화살". One bolt of the 5-way fan (the game fires 5 instances from the boss hand at brand 0.4 s).
// projectile (local +Z = travel) → impact (local +Y = surface normal; air hit: -incoming). Ink body + violet glowing rims, no warning decals.
const TAU = Math.PI * 2;
const LIL = [0.84,0.59,1.0], VIO = [0.54,0.24,1.0], DEEP = [0.31,0.12,0.6], WHL = [0.95,0.9,1.0];
const F44 = {x:4, y:4}, F28 = {x:2, y:8};
// crossed arrowhead planes along +Z (U = along travel, head at U=1), length 1 × width 0.5 (size scales)
const quadZ = (axis) => ({ verts: axis === "x"
    ? [[-0.25,0,-0.75],[0.25,0,-0.75],[0.25,0,0.25],[-0.25,0,0.25]]
    : [[0,-0.25,-0.75],[0,0.25,-0.75],[0,0.25,0.25],[0,-0.25,0.25]],
  uvs:[[0,0],[0,1],[1,1],[1,0]], tris:[0,1,2,0,2,3] });
var EXTRA_MESHES = { headH: quadZ("x"), headV: quadZ("y") };
const HOLD = 6;   // one held particle per piece (flight is far shorter; the hit clears local layers)

const PROJECTILE = [
  // focal: barbed ink arrowhead, two crossed planes slowly rolling about the flight axis
  { name:"P01 HeadH", mat:"arrow", mode:"mesh", mesh:"headH", fudge:-2, bursts:[[0,1]], life:HOLD, size:1.3, rotLife:[0,0,2.2],
    alpha:[[0,1],[1,1]], sizeLife:[[0,0.4,0,6],[0.012,1],[1,1]] },
  { name:"P02 HeadV", mat:"arrow", mode:"mesh", mesh:"headV", fudge:-2, bursts:[[0,1]], life:HOLD, size:1.3, rotLife:[0,0,2.2],
    alpha:[[0,1],[1,1]], sizeLife:[[0,0.4,0,6],[0.012,1],[1,1]] },
  // boiling black core with violet rim, at the arrow's heart
  { name:"P03 CoreOrb", mat:"orb", fudge:-3, pos:[0,0,-0.05], rate:22, bursts:[[0,2]], life:[0.14,0.22], size:[0.5,0.62], rot:[0,TAU], rotZ:[-5,5],
    alpha:[[0,0.4],[0.3,1],[1,0]], sizeLife:[[0,0.8],[1,1.1]] },
  { name:"P04 RimGlow", mat:"glow", fudge:0, pos:[0,0,-0.05], rate:14, bursts:[[0,1]], life:0.22, size:0.85, color:[[0,VIO],[1,VIO]],
    alpha:[[0,0],[0.3,0.18],[1,0]] },
  // fast violet arcs coiling around the flight axis (counter-rotating pair)
  { name:"P05 CoilArcsA", mat:"stroke", mode:"mesh", mesh:"spin", fudge:-4, rate:9, life:[0.22,0.3], size:[0.2,0.27], pos:[0,0,-0.25],
    rot3:[0,0,[0,TAU]], rotLife:[0,0,[16,22]], flip:F28 },
  { name:"P06 CoilArcsB", mat:"stroke", mode:"mesh", mesh:"spin", fudge:-4, rate:7, life:[0.25,0.32], size:[0.26,0.34], pos:[0,0,-0.5],
    rot3:[0,0,[0,TAU]], rotLife:[0,0,[-14,-10]], flip:F28 },
  // ink tail: ribbon trail + world ink puffs shed behind
  { name:"P07 InkRibbon", mat:"inkTrail", fudge:8, pos:[0,0,-0.2], bursts:[[0,1]], life:HOLD, size:0.34,
    trail:{ life:0.055, mat:"inkTrail", minDist:0.04, color:[[0,[1,1,1]],[1,[0.6,0.5,0.8]]] }, alpha:[[0,1],[1,1]] },
  { name:"P08 InkTrail", mat:"ink", fudge:14, world:true, rateDist:9, life:[0.35,0.55], size:[0.28,0.46], rot:[0,TAU], rotZ:[-1.5,1.5],
    shape:{type:"sphere", r:0.08, th:1}, speed:[0.1,0.4], drag:2, vel:{y:[0.15,0.35], space:"world"}, sizeLife:[[0,0.7],[1,1.35]], flip:F44 },
  { name:"P09 InkWisps", mat:"tendril", mode:"stretch", vs:0.05, ls:1.6, fudge:12, world:true, rateDist:3, life:[0.25,0.4], size:[0.1,0.16],
    shape:{type:"sphere", r:0.1, th:1}, speed:[0.6,1.4], drag:3, alpha:[[0,0.9],[1,0]] },
  // violet embers and sparks
  { name:"P10 Embers", mat:"dot", fudge:-6, world:true, rateDist:12, life:[0.5,0.9], size:[0.03,0.06], speed:[0.3,1.2],
    shape:{type:"sphere", r:0.18, th:1}, gravity:-0.08, drag:1.2, noise:{s:0.7,f:1.2,sp:0.6},
    color:[[0,LIL],[0.5,VIO],[1,DEEP]], alpha:[[0,1],[0.7,0.9],[1,0]] },
  { name:"P11 Sparks", mat:"streak", mode:"stretch", vs:0.06, ls:1.4, fudge:-5, world:true, rateDist:4, life:[0.15,0.3], size:[0.03,0.06],
    speed:[1,2.5], shape:{type:"sphere", r:0.12, th:1}, alpha:[[0,1],[1,0]] },
  // launch at the boss hand (world space, stays where the bolt left): tiny star + ink puff + ring
  { name:"P12 LaunchStar", mat:"star", fudge:-7, world:true, bursts:[[0,1]], life:0.22, size:1.3, rot:[0,0.8],
    sizeLife:[[0,0.3],[0.25,1],[1,0]] },
  { name:"P13 LaunchInk", mat:"ink", fudge:14, world:true, bursts:[[0,5]], life:[0.35,0.5], size:[0.35,0.55], rot:[0,TAU],
    shape:{type:"sphere", r:0.1, th:1}, speed:[0.8,1.8], drag:4, sizeLife:[[0,0.5],[1,1.2]], flip:F44 },
  { name:"P14 LaunchRing", mat:"ring", mode:"mesh", mesh:"quad", fudge:2, bursts:[[0,1]], life:0.28, size:1.3, rot3:[Math.PI/2,0,0],
    sizeLife:[[0,0.25,0,3],[1,1]], alpha:[[0,0.9],[1,0]] },
];

const C = 0.5, CLAW_YAW = 0.25;  // claw decal + flare share one yaw (relative to the incoming direction) so the cuts line up  // explosion centre along +Y (small burst)
const IMPACT = [
  // anticipation-free hit: short small flash
  { name:"I01 FlashStar", mat:"star", fudge:-8, pos:[0,C,0], bursts:[[0,1]], life:0.2, size:2.2, rot:[0,0.8], sizeLife:[[0,0.25],[0.25,1.1],[1,0]] },
  { name:"I02 FlashGlow", mat:"glow", fudge:-1, pos:[0,C,0], bursts:[[0,1]], life:0.28, size:1.8, color:[[0,VIO],[1,DEEP]],
    sizeLife:[[0,0.5],[0.2,1],[1,1.15]], alpha:[[0,0.45],[1,0]] },
  // dark mid: ink bloom + ink splash spikes + flying ink shards
  { name:"I03 InkBurst", mat:"ink", fudge:15, pos:[0,C,0], bursts:[[0,12],[0.05,7]], shape:{type:"sphere", r:0.2, th:1},
    speed:[1.8,3.8], drag:5, life:[0.55,0.8], size:[0.75,1.25], rot:[0,TAU], rotZ:[-1,1], vel:{y:[0.2,0.6], space:"world"},
    sizeLife:[[0,0.45,0,3],[1,1.25]], flip:F44 },
  { name:"I04 InkSpikes", mat:"tendril", mode:"stretch", vs:0.12, ls:2.4, fudge:13, pos:[0,C*0.6,0], bursts:[[0,16]],
    shape:{type:"sphere", r:0.12, th:1}, speed:[6,10], drag:6, life:[0.25,0.38], size:[0.24,0.38], sizeLife:[[0,1],[1,0.35]], alpha:[[0,1],[0.7,1],[1,0]] },
  { name:"I05 InkShards", mat:"shard", mode:"stretch", vs:0.07, ls:1.3, fudge:12, pos:[0,C,0], bursts:[[0,10]],
    shape:{type:"sphere", r:0.15, th:1}, speed:[3,6.5], drag:0.6, gravity:1.3, life:[0.45,0.8], size:[0.08,0.15], alpha:[[0,1],[0.75,1],[1,0]] },
  // base: three-claw scratch decal (dark ink + violet cut lines), additive flare of the same cuts, violet rings + rune ring
  { name:"I06 ClawDecal", mat:"clawDecal", mode:"mesh", mesh:"quad", fudge:30, pos:[0,0.03,0], bursts:[[0.02,1]], life:2.6, size:2.3,
    rot3:[0,CLAW_YAW,0], sizeLife:[[0,0.55,0,6],[0.06,1],[1,1.02]], alpha:[[0,0],[0.02,1],[0.65,0.9],[1,0]] },
  { name:"I07 ClawFlare", mat:"clawGlow", mode:"mesh", mesh:"quad", fudge:-2, pos:[0,0.05,0], bursts:[[0.02,1]], life:0.9, size:2.3,
    rot3:[0,CLAW_YAW,0], sizeLife:[[0,0.55,0,6],[0.06,1],[1,1.02]], alpha:[[0,0],[0.03,1],[0.3,0.55],[1,0]] },
  { name:"I08 RingFast", mat:"ring", mode:"mesh", mesh:"quad", fudge:3, pos:[0,0.06,0], bursts:[[0,1]], life:0.32, size:3.0,
    rot3:[0,[0,TAU],0], sizeLife:[[0,0.15,0,3],[1,1]], alpha:[[0,0.85],[0.5,0.6],[1,0]] },
  { name:"I09 RingSlow", mat:"ring", mode:"mesh", mesh:"quad", fudge:3, pos:[0,0.07,0], bursts:[[0.08,1]], life:0.5, size:2.2,
    rot3:[0,[0,TAU],0], sizeLife:[[0,0.2,0,2.5],[1,1]], alpha:[[0,0.7],[1,0]] },
  { name:"I10 RuneRing", mat:"rune", mode:"mesh", mesh:"quad", fudge:4, pos:[0,0.08,0], bursts:[[0.03,1]], life:0.9, size:1.7,
    rot3:[0,[0,TAU],0], rotLife:[0,2.2,0], sizeLife:[[0,0.6,0,4],[0.2,1],[1,1.15]], alpha:[[0,0],[0.08,0.9],[0.6,0.6],[1,0]] },
  // top: quick violet arcs (counter-rotating), sparks, rising motes
  { name:"I11 Arcs", mat:"stroke", mode:"mesh", mesh:"slash", fudge:-3, bursts:[[0,1],[0.04,1],[0.09,1]], pos:[0,C*0.7,0],
    shape:{type:"sphere", r:0.1, th:1}, life:[0.22,0.3], size:[0.7,1.0],
    rot3:[[-0.5,0.5],[0,TAU],[-0.5,0.5]], rotLife:[0,[13,18],0], flip:F28 },
  { name:"I12 ArcsRev", mat:"stroke", mode:"mesh", mesh:"slash", fudge:-3, bursts:[[0.03,1],[0.1,1]], pos:[0,C*0.5,0],
    life:[0.25,0.32], size:[0.9,1.2], rot3:[[-0.3,0.3],[0,TAU],[-0.3,0.3]], rotLife:[0,[-15,-10],0], flip:F28 },
  { name:"I13 Sparks", mat:"streak", mode:"stretch", vs:0.08, ls:1.4, fudge:-5, pos:[0,C,0], bursts:[[0,26]],
    shape:{type:"sphere", r:0.15, th:1}, speed:[4,9], drag:3, gravity:0.6, life:[0.25,0.5], size:[0.035,0.07], alpha:[[0,1],[0.7,1],[1,0]] },
  { name:"I14 Motes", mat:"dot", fudge:-6, pos:[0,C*0.6,0], bursts:[[0.04,22],[0.2,10]], shape:{type:"sphere", r:0.5, th:1},
    speed:[0.3,1.4], drag:1.5, vel:{y:[0.5,1.1], space:"world"}, noise:{s:0.8,f:1.1,sp:0.6}, life:[0.9,1.6], size:[0.03,0.07],
    color:[[0,WHL],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.08,1],[0.7,0.8],[1,0]] },
  // dark accent behind: translucent black mist lingering
  { name:"I15 DarkMist", mat:"mist", fudge:25, pos:[0,C*0.8,0], bursts:[[0.06,4]], shape:{type:"sphere", r:0.35, th:1},
    speed:[0.3,0.7], drag:2, vel:{y:[0.25,0.45], space:"world"}, life:[1.0,1.4], size:[1.0,1.5], rot:[0,TAU], rotZ:[-0.4,0.4],
    sizeLife:[[0,0.6],[1,1.3]], alpha:[[0,0],[0.15,0.55],[1,0]], flip:F44 },
];

const MATS = {
  arrow:{tex:"T_ArrowHead", tint:[2.1,1.8,2.6], add:false},
  orb:{tex:"T_InkOrb", tint:[2.0,1.7,2.5], add:false},
  glow:{tex:"T_Glow", tint:[1.1,0.8,1.6], add:true},
  stroke:{tex:"T_Stroke_Sheet", tint:[2.2,1.8,2.8], add:true},
  inkTrail:{tex:"T_InkTrail", tint:[1.8,1.5,2.4], add:false},
  ink:{tex:"T_InkPuff_Sheet", tint:[1.6,1.4,2.0], add:false},
  mist:{tex:"T_InkPuff_Sheet", tint:[1,1,1.2], add:false},
  tendril:{tex:"T_Tendril", tint:[1.8,1.5,2.3], add:false},
  shard:{tex:"T_Shard", tint:[1.9,1.6,2.4], add:false},
  dot:{tex:"T_Dot", tint:[1.9,1.5,2.6], add:true},
  streak:{tex:"T_Streak", tint:[2.0,1.7,2.7], add:true},
  star:{tex:"T_Star", tint:[1.9,1.6,2.5], add:true},
  ring:{tex:"T_Ring", tint:[1.7,1.3,2.4], add:true},
  rune:{tex:"T_RuneRing", tint:[1.7,1.3,2.4], add:true},
  clawDecal:{tex:"T_Claw", tint:[0.95,0.7,1.35], add:false},
  clawGlow:{tex:"T_Claw", tint:[2.0,1.6,2.8], add:true},
};

// demo (non-normative): boss hand ≈ 3 m up; the "fan" scenario shows the game's 5-way spread as 5 independent instances
const HAND = [-6, 3.0, 0];
const fanTo = [[2.6,0,-3.6],[3.4,0,-1.8],[3.8,0,0],[3.4,0,1.8],[2.6,0,3.6]];
const SCENE = {
  name: "MordianCurseBolt",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.7, tint:[0.95,0.88,1.0] }, tonemapping:"neutral",
  systems:[
    { id:"projectile", role:"projectile", loop:false, duration:HOLD, layers:PROJECTILE },
    { id:"impact",     role:"impact",     loop:false, duration:2.8, layers:IMPACT },
  ],
  demo:{
    interval:3.2, bg:[0.08,0.075,0.1], floor:[0.11,0.105,0.12], grid:[0.075,0.07,0.085],
    scenarios:{
      ground:{ label:"바닥", from:HAND, to:[2.4,0,0], normal:[0,1,0], flight:0.55,
               camera:{ target:[-1.6,1.2,0], dist:9.5, yaw:-90, pitch:16 } },
      wall:  { label:"벽", from:HAND, to:[2.4,1.4,0], normal:[-1,0,0], flight:0.55, wall:{ x:2.4 },
               camera:{ target:[-0.8,1.4,0], dist:9.0, yaw:-118, pitch:12 } },
      air:   { label:"공중", from:HAND, to:[1.8,1.6,0], normal:null, flight:0.55,
               camera:{ target:[-1.6,1.6,0], dist:9.0, yaw:-90, pitch:10 } },
      close: { label:"착탄 근접", from:HAND, to:[2.4,0,0], normal:[0,1,0], flight:0.55,
               camera:{ target:[2.2,0.4,0], dist:3.6, yaw:-120, pitch:28 } },
      fly:   { label:"탄체 근접", from:[-8,2.2,0], to:[6,1.2,0], normal:null, flight:1.6,
               camera:{ target:[-2.5,1.8,0], dist:3.2, yaw:-150, pitch:8 } },
      fan:   { label:"5갈래 (게임 패턴)", interval:3.4, actors:[{path:[[0,[-6,0,0],90]], hidden:true}],
               events: fanTo.map((to) => ({ t:0.2, system:"projectile", actor:0, from:[0,3.0,0], to, normal:[0,1,0], flight:0.55 })),
               camera:{ target:[-0.5,0.8,0], dist:13, yaw:-60, pitch:30 } },
    },
  },
};
