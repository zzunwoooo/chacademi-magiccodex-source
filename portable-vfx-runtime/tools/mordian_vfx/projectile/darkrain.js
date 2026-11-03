// MordianDarkRain — 모르디안의 원령 "어둠의 비". One ink meteor falling ~15–20 m almost vertically onto the arena (the game drops many, staggered).
// projectile (local +Z = travel, i.e. down) → impact (local +Y = surface normal; air hit: -incoming). Kept light: shared small textures, modest counts.
const TAU = Math.PI * 2;
const LIL = [0.84,0.59,1.0], VIO = [0.54,0.24,1.0], DEEP = [0.31,0.12,0.6], WHL = [0.95,0.9,1.0];
const F44 = {x:4, y:4};
// crossed comet planes along +Z (U = along travel, head at U=1): z −0.85..0.15, half width 0.2 (× size)
const quadZ = (axis) => ({ verts: axis === "x"
    ? [[-0.2,0,-0.85],[0.2,0,-0.85],[0.2,0,0.15],[-0.2,0,0.15]]
    : [[0,-0.2,-0.85],[0,0.2,-0.85],[0,0.2,0.15],[0,-0.2,0.15]],
  uvs:[[0,0],[0,1],[1,1],[1,0]], tris:[0,1,2,0,2,3] });
var EXTRA_MESHES = { cometH: quadZ("x"), cometV: quadZ("y") };
const HOLD = 6;

const PROJECTILE = [
  // focal: ink comet (black body, violet rim, ragged tail) as two crossed planes rolling slowly + boiling ink core at the head
  { name:"P01 CometH", mat:"comet", mode:"mesh", mesh:"cometH", fudge:-2, bursts:[[0,1]], life:HOLD, size:2.8, rotLife:[0,0,1.6],
    sizeLife:[[0,0.3,0,8],[0.02,1],[1,1]] },
  { name:"P02 CometV", mat:"comet", mode:"mesh", mesh:"cometV", fudge:-2, bursts:[[0,1]], life:HOLD, size:2.8, rotLife:[0,0,1.6],
    sizeLife:[[0,0.3,0,8],[0.02,1],[1,1]] },
  { name:"P03 CoreOrb", mat:"orb", fudge:-3, pos:[0,0,0.05], rate:18, bursts:[[0,2]], life:[0.15,0.24], size:[0.85,1.05], rot:[0,TAU], rotZ:[-4,4],
    alpha:[[0,0.4],[0.3,1],[1,0]], sizeLife:[[0,0.85],[1,1.1]] },
  { name:"P04 RimGlow", mat:"glow", fudge:0, rate:10, bursts:[[0,1]], life:0.25, size:1.4, color:[[0,VIO],[1,VIO]], alpha:[[0,0],[0.3,0.2],[1,0]] },
  // long ink tail: ribbon trail + world ink puffs + violet embers
  { name:"P05 InkRibbon", mat:"inkTrail", fudge:8, pos:[0,0,-0.3], bursts:[[0,1]], life:HOLD, size:0.62,
    trail:{ life:0.04, mat:"inkTrail", minDist:0.08, color:[[0,[1,1,1]],[1,[0.6,0.5,0.8]]] } },
  { name:"P06 InkTrail", mat:"ink", fudge:14, world:true, rateDist:5.5, life:[0.45,0.7], size:[0.6,1.0], rot:[0,TAU], rotZ:[-1.2,1.2],
    shape:{type:"sphere", r:0.15, th:1}, speed:[0.2,0.5], drag:2, sizeLife:[[0,0.7],[1,1.4]], flip:F44 },
  { name:"P07 Embers", mat:"dot", fudge:-6, world:true, rateDist:4, life:[0.5,0.9], size:[0.04,0.08], speed:[0.5,1.5],
    shape:{type:"sphere", r:0.25, th:1}, drag:1.2, noise:{s:0.8,f:1,sp:0.6}, vel:{y:[0.3,0.8], space:"world"},
    color:[[0,LIL],[0.5,VIO],[1,DEEP]], alpha:[[0,1],[0.7,0.9],[1,0]] },
];

const C = 0.7, CRACK_YAW = 0.4;  // explosion centre along +Y; crack decal + glow share one yaw so the glowing cracks sit on the dark ones
const IMPACT = [
  // top: short small flash (violet, no whiteout)
  { name:"I01 FlashStar", mat:"star", fudge:-8, pos:[0,C,0], bursts:[[0,1]], life:0.18, size:3.2, rot:[0,0.8], sizeLife:[[0,0.25],[0.25,1.1],[1,0]] },
  { name:"I02 FlashGlow", mat:"glow", fudge:-1, pos:[0,C,0], bursts:[[0,1]], life:0.3, size:2.8, color:[[0,VIO],[1,DEEP]],
    sizeLife:[[0,0.5],[0.2,1],[1,1.15]], alpha:[[0,0.4],[1,0]] },
  // mid: ink blast (radius ~3 m), ink crown spikes up, ground splash spikes outward, black debris thrown up
  { name:"I03 InkBlast", mat:"ink", fudge:15, pos:[0,0.55,0], bursts:[[0,12],[0.05,6]], shape:{type:"sphere", r:0.45, th:1},
    speed:[5,9], drag:4.2, life:[0.65,0.95], size:[1.4,2.3], rot:[0,TAU], rotZ:[-0.8,0.8], vel:{y:[0.3,0.7], space:"world"},
    sizeLife:[[0,0.4,0,3],[1,1.25]], flip:F44 },
  { name:"I04 InkCrown", mat:"tendril", mode:"stretch", vs:0.1, ls:2.6, fudge:13, pos:[0,0.3,0], bursts:[[0,12]],
    shape:{type:"sphere", r:0.2, th:1}, speed:[7,11], vel:{y:[5,8]}, drag:5, life:[0.3,0.45], size:[0.45,0.7],
    sizeLife:[[0,1],[1,0.35]], alpha:[[0,1],[0.7,1],[1,0]] },
  { name:"I05 GroundSplash", mat:"tendril", mode:"stretch", vs:0.1, ls:2.2, fudge:13, pos:[0,0.15,0], bursts:[[0.01,12]],
    shape:{type:"circle", r:0.5, th:0.2}, speed:[11,15], drag:6, life:[0.3,0.45], size:[0.4,0.6],
    sizeLife:[[0,1],[1,0.4]], alpha:[[0,1],[0.7,1],[1,0]] },
  { name:"I06 Debris", mat:"shard", mode:"stretch", vs:0.06, ls:1.3, fudge:12, pos:[0,0.4,0], bursts:[[0,12]],
    shape:{type:"sphere", r:0.3, th:1}, speed:[3.5,7], vel:{y:[5,8], space:"world"}, gravity:2.2, drag:0.3, life:[0.9,1.3], size:[0.2,0.34],
    alpha:[[0,1],[0.8,1],[1,0]] },
  // base: cracked ink decal (dark + violet cracks), additive crack flare, shock rings to the edge
  { name:"I07 CrackDecal", mat:"crackDecal", mode:"mesh", mesh:"quad", fudge:30, pos:[0,0.03,0], bursts:[[0.02,1]], life:3.2, size:6.4,
    rot3:[0,CRACK_YAW,0], sizeLife:[[0,0.4,0,6],[0.04,1],[1,1.02]], alpha:[[0,0],[0.015,1],[0.7,0.85],[1,0]] },
  { name:"I08 CrackFlare", mat:"crackGlow", mode:"mesh", mesh:"quad", fudge:-2, pos:[0,0.05,0], bursts:[[0.02,1]], life:1.1, size:6.4,
    rot3:[0,CRACK_YAW,0], sizeLife:[[0,0.4,0,6],[0.04,1],[1,1.02]], alpha:[[0,0],[0.03,1],[0.3,0.5],[1,0]] },
  { name:"I09 ShockRing", mat:"ring", mode:"mesh", mesh:"quad", fudge:3, pos:[0,0.07,0], bursts:[[0,1]], life:0.38, size:6.8,
    rot3:[0,[0,TAU],0], sizeLife:[[0,0.1,0,3],[1,1]], alpha:[[0,0.8],[0.5,0.55],[1,0]] },
  { name:"I10 ShockRing2", mat:"ring", mode:"mesh", mesh:"quad", fudge:3, pos:[0,0.08,0], bursts:[[0.08,1]], life:0.5, size:5.2,
    rot3:[0,[0,TAU],0], sizeLife:[[0,0.15,0,2.5],[1,1]], alpha:[[0,0.6],[1,0]] },
  // violet dust pushed out along the ground, rising motes, sparks
  { name:"I11 Dust", mat:"dust", fudge:18, pos:[0,0.35,0], bursts:[[0.03,8]], shape:{type:"circle", r:1.0, th:0.6},
    speed:[2.5,4], drag:3, vel:{y:[0.25,0.5], space:"world"}, life:[1.0,1.5], size:[1.3,2.0], rot:[0,TAU], rotZ:[-0.5,0.5],
    sizeLife:[[0,0.5],[1,1.3]], alpha:[[0,0],[0.12,0.32],[1,0]] },
  { name:"I12 Motes", mat:"dot", fudge:-6, pos:[0,0.4,0], bursts:[[0.05,16],[0.25,8]], shape:{type:"sphere", r:1.6, th:1},
    speed:[0.3,1.2], drag:1.5, vel:{y:[0.6,1.3], space:"world"}, noise:{s:0.8,f:1,sp:0.6}, life:[1.0,1.7], size:[0.04,0.08],
    color:[[0,WHL],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.08,1],[0.7,0.8],[1,0]] },
  { name:"I13 Sparks", mat:"streak", mode:"stretch", vs:0.08, ls:1.5, fudge:-5, pos:[0,C*0.6,0], bursts:[[0,14]],
    shape:{type:"sphere", r:0.25, th:1}, speed:[6,12], drag:3, gravity:0.8, life:[0.3,0.55], size:[0.05,0.09], alpha:[[0,1],[0.7,1],[1,0]] },
  // dark accent behind: translucent black mist
  { name:"I14 DarkMist", mat:"mist", fudge:25, pos:[0,0.8,0], bursts:[[0.06,4]], shape:{type:"sphere", r:0.8, th:1},
    speed:[0.4,0.9], drag:2, vel:{y:[0.3,0.5], space:"world"}, life:[1.1,1.5], size:[1.8,2.6], rot:[0,TAU], rotZ:[-0.3,0.3],
    sizeLife:[[0,0.6],[1,1.25]], alpha:[[0,0],[0.15,0.5],[1,0]], flip:F44 },
];

const MATS = {
  comet:{tex:"T_Comet", tint:[2.0,1.7,2.5], add:false},
  orb:{tex:"T_InkOrb", tint:[2.0,1.7,2.5], add:false},
  glow:{tex:"T_Glow", tint:[1.1,0.8,1.6], add:true},
  inkTrail:{tex:"T_InkTrail", tint:[1.8,1.5,2.4], add:false},
  ink:{tex:"T_InkPuff_Sheet", tint:[1.6,1.4,2.0], add:false},
  mist:{tex:"T_InkPuff_Sheet", tint:[1,1,1.2], add:false},
  tendril:{tex:"T_Tendril", tint:[1.8,1.5,2.3], add:false},
  shard:{tex:"T_Shard", tint:[1.9,1.6,2.4], add:false},
  dot:{tex:"T_Dot", tint:[1.9,1.5,2.6], add:true},
  streak:{tex:"T_Streak", tint:[2.0,1.7,2.7], add:true},
  star:{tex:"T_Star", tint:[1.9,1.6,2.5], add:true},
  ring:{tex:"T_Ring", tint:[1.7,1.3,2.4], add:true},
  dust:{tex:"T_Dust", tint:[0.42,0.2,0.85], add:false},
  crackDecal:{tex:"T_Crack", tint:[0.95,0.7,1.35], add:false},
  crackGlow:{tex:"T_Crack", tint:[2.0,1.6,2.8], add:true},
};

// demo (non-normative). Meteors start ~18 m up with a small horizontal drift. "rain" = the game pattern: staggered drops across the arena.
const DROPS = [[0,0,0,0.0],[-4.5,0,3,0.25],[4,0,-3.5,0.45],[-3,0,-5,0.7],[5,0,4,0.9],[1,0,6,1.15],[-6,0,-1,1.35],[2.5,0,-7,1.6]];
const SCENE = {
  name: "MordianDarkRain",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.7, tint:[0.95,0.88,1.0] }, tonemapping:"neutral",
  systems:[
    { id:"projectile", role:"projectile", loop:false, duration:HOLD, layers:PROJECTILE },
    { id:"impact",     role:"impact",     loop:false, duration:3.4, layers:IMPACT },
  ],
  demo:{
    interval:3.4, bg:[0.08,0.075,0.1], floor:[0.11,0.105,0.12], grid:[0.075,0.07,0.085],
    scenarios:{
      ground:{ label:"바닥", from:[1.5,18,0.6], to:[0,0,0], normal:[0,1,0], flight:0.85,
               camera:{ target:[0,3.5,0], dist:17, yaw:-90, pitch:12 } },
      close: { label:"착탄 근접", from:[1.5,18,0.6], to:[0,0,0], normal:[0,1,0], flight:0.85,
               camera:{ target:[0,0.8,0], dist:9, yaw:-110, pitch:24 } },
      fall:  { label:"낙하 근접", from:[1.5,18,0.6], to:[0,0,0], normal:[0,1,0], flight:0.85,
               camera:{ target:[0.6,8,0.2], dist:7, yaw:-100, pitch:4 } },
      air:   { label:"공중", from:[1.5,18,0.6], to:[0.2,3.5,0.1], normal:null, flight:0.7,
               camera:{ target:[0,4,0], dist:14, yaw:-90, pitch:8 } },
      rain:  { label:"경기장 전역 (게임 패턴)", interval:4.4, actors:[{path:[[0,[0,0,0],0]], hidden:true}],
               events: DROPS.map(([x,y,z,t]) => ({ t, system:"projectile", actor:0, from:[x + 1.5, 18, z + 0.6], to:[x, 0, z], normal:[0,1,0], flight:0.85 })),
               camera:{ target:[0,1.5,0], dist:26, yaw:-70, pitch:30 } },
    },
  },
};
