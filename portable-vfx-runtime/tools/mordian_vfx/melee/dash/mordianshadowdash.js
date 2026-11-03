// MordianShadowDash — 모르디안의 원령 shadow dash (attached to the boss feet, facing = dash direction, fixed per instance).
// t = 0 = dash motion start (0 s). 0.25–0.42 crouch: ink gathers at the feet. 0.42 launch (start-point splash). 0.42–0.75 the boss shoots forward:
// world-space ink/mist tail + violet scratch lines along the path (rateOverDistance, so any dash length works). 0.75 arrival skid. Gone by ~1.1–1.3 s.
// One new instance per dash (3 in a row = 3 instances).
const TAU = Math.PI * 2, T0 = 0.42, T1 = 0.75;
const VIO = [0.54,0.235,1.0], LIL = [0.84,0.59,1.0], WLI = [0.95,0.9,1.0];
const EXTRA_MESHES = {
  scratchSeg: ribbon(16, s => { const z = (s - 0.5) * 2.4; return [[-0.75, 0.04, z], [0.75, 0.04, z]]; }),   // U along the dash (+Z), 3 gouges across
  ringLine: ribbon(96, s => { const phi = -s * TAU; return [[Math.cos(phi) * 0.985, 0, Math.sin(phi) * 0.985], [Math.cos(phi) * 1.015, 0, Math.sin(phi) * 1.015]]; }),
};
const every = (a, b, n, dt) => [[a, n, Math.max(1, Math.round((b - a) / dt)), dt]];
const LAYERS = [
  // ---------------- crouch (0.25–0.42): ink pools and is sucked in at the feet, motes converge
  { name:"01 PoolInk", mat:"splatFlat", mode:"mesh", mesh:"quad", fudge:40, world:true, pos:[0,0.05,0], life:0.5, size:4.2, rot3:[0,[0,TAU],0], bursts:[[0.22,1]],
    flip:{x:4,y:4}, sizeLife:[[0,0.5,0,2],[0.4,1],[1,1.1]] },
  { name:"02 GatherMist", mat:"mist", fudge:24, life:[0.22,0.3], size:[1.6,2.4], rot:[0,TAU], rotZ:[-2,2], shape:{type:"circle", r:2.6, th:0.3, y:0.6},
    bursts:[[0.2,4],[0.27,5],[0.33,6]], vel:{rad:[-9,-6], y:[0.5,1.5]}, flip:{x:4,y:4}, alpha:[[0,0],[0.3,0.75],[1,0]], sizeLife:[[0,1.1],[1,0.5]] },
  { name:"03 GatherMotes", mat:"dot", fudge:-6, life:[0.18,0.25], size:[0.06,0.12], shape:{type:"circle", r:3.0, th:0.4, y:1.0},
    bursts:[[0.2,10],[0.27,12],[0.33,14]], vel:{rad:[-14,-10], y:[1,3]}, color:[[0,LIL],[1,WLI]], alpha:[[0,0],[0.3,1],[1,0.6]] },
  // ---------------- launch (0.42) at the start point (world)
  { name:"04 LaunchSplatFlat", mat:"splatFlat", mode:"mesh", mesh:"quad", fudge:39, world:true, pos:[0,0.06,-0.4], life:0.75, size:[5,5.6], rot3:[0,[0,TAU],0], bursts:[[T0,1]], flip:{x:4,y:4} },
  { name:"05 LaunchCrown", mat:"splat", fudge:16, world:true, pos:[0,1.2,-0.6], life:0.4, size:[2.6,3.2], rot:[0,TAU], bursts:[[T0,2]], flip:{x:4,y:4} },
  { name:"06 LaunchChips", mat:"shard", mode:"stretch", vs:0.07, ls:1.5, fudge:14, world:true, pos:[0,0.6,0], life:[0.4,0.7], size:[0.14,0.26], speed:[2,5],
    shape:{type:"sphere", r:0.8, th:1}, bursts:[[T0,22]], vel:{z:[-6,-2], y:[1,4], space:"local"}, drag:2, gravity:1.2,
    alpha:[[0,0],[0.06,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.5]] },
  { name:"07 LaunchRing", mat:"line", mode:"mesh", mesh:"ringLine", fudge:30, world:true, pos:[0,0.08,0], life:0.32, size:1.5, bursts:[[T0,1]],
    sizeLife:[[0,1,0,4],[1,3.4]], color:[[0,WLI],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.6,0.7],[1,0]] },
  { name:"08 LaunchFlash", mat:"star", fudge:-10, world:true, pos:[0,1.0,0.3], life:0.16, size:2.4, rot:[0,45 * D2R], bursts:[[T0,1]], sizeLife:[[0,0.3],[0.25,1],[1,0]] },
  { name:"09 LaunchMist", mat:"mist", fudge:22, world:true, life:[0.6,0.9], size:[2.4,3.4], rot:[0,TAU], rotZ:[-0.5,0.5], shape:{type:"sphere", r:1.0, th:1, y:1.6},
    bursts:[[T0,7]], speed:[1,3], drag:2.5, vel:{z:[-1.5,-0.5], y:[0.3,0.9], space:"local"}, flip:{x:4,y:4}, alpha:[[0,0],[0.1,0.8],[0.6,0.6],[1,0]], sizeLife:[[0,0.6],[1,1.25]] },
  // ---------------- travel (0.42–0.75): world-space tail left along the path
  { name:"10 TailMist", mat:"mist", fudge:21, world:true, rateDist:3.2, life:[0.6,0.95], size:[3.0,4.6], rot:[0,TAU], rotZ:[-0.6,0.6],
    shape:{type:"sphere", r:2.0, th:1}, pos:[0,3.3,-0.6], vel:{z:[-1.2,-0.3], y:[0.1,0.5], space:"local"}, noise:{s:0.4, f:0.6, sp:0.5}, flip:{x:4,y:4},
    alpha:[[0,0],[0.08,1],[0.5,0.75],[1,0]], sizeLife:[[0,0.7],[1,1.2]] },
  { name:"10b TailMistLow", mat:"mist", fudge:21.5, world:true, rateDist:1.6, life:[0.5,0.8], size:[2.2,3.2], rot:[0,TAU], rotZ:[-0.6,0.6],
    shape:{type:"sphere", r:1.1, th:1}, pos:[0,1.3,-0.4], vel:{z:[-1.0,-0.2], y:[0.2,0.6], space:"local"}, flip:{x:4,y:4},
    alpha:[[0,0],[0.1,0.9],[0.5,0.65],[1,0]], sizeLife:[[0,0.7],[1,1.2]] },
  { name:"11 TailInk", mat:"inkTail", mode:"stretch", vs:0.5, ls:4.0, fudge:18, world:true, rateDist:6.5, life:[0.4,0.7], size:[0.22,0.6],
    shape:{type:"sphere", r:2.2, th:0.8}, pos:[0,3.2,-0.3], speed:[0,0.6], vel:{z:[-5,-0.6], y:[-0.6,0.6], space:"local"},
    alpha:[[0,0],[0.08,0.95],[0.6,0.7],[1,0]], sizeLife:[[0,1],[1,0.6]] },
  { name:"12 TailWisps", mat:"streak", mode:"stretch", vs:0.2, ls:4.0, fudge:-3, world:true, rateDist:6, life:[0.2,0.4], size:[0.06,0.12],
    shape:{type:"sphere", r:2.3, th:0.7}, pos:[0,3.2,-0.2], vel:{z:[-7,-2], y:[-0.4,0.4], space:"local"},
    color:[[0,WLI],[0.35,LIL],[1,VIO]], alpha:[[0,0],[0.1,1],[0.6,0.8],[1,0]], sizeLife:[[0,1],[1,0.4]] },
  { name:"13 TailMotes", mat:"dot", fudge:-6, world:true, rateDist:5, life:[0.6,1.1], size:[0.05,0.11], speed:[0.2,0.8],
    shape:{type:"box", sy:5, sx:2.2, sz:0.8}, pos:[0,2.8,-0.4], vel:{y:[0.5,1.4]}, drag:1, noise:{s:0.6, f:0.8, sp:0.6},
    color:[[0,WLI],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.1,1],[0.6,0.8],[1,0]] },
  { name:"14 PathScratchInk", mat:"scratchInk", mode:"mesh", mesh:"scratchSeg", fudge:36, world:true, rateDist:0.5, life:0.95, size:1, pos:[0,0,-1.0],
    alpha:[[0,0],[0.05,0.95],[0.55,0.85],[1,0]] },
  { name:"15 PathScratchGlow", mat:"scratchGlow", mode:"mesh", mesh:"scratchSeg", fudge:35, world:true, rateDist:0.5, life:0.6, size:1, pos:[0,0.01,-1.0],
    color:[[0,WLI],[0.25,LIL],[1,VIO]], alpha:[[0,0],[0.05,1],[0.45,0.7],[1,0]] },
  { name:"16 PathDust", mat:"mist", fudge:20, world:true, rateDist:1.6, life:[0.4,0.6], size:[1.2,1.8], rot:[0,TAU], shape:{type:"box", sy:0.2, sx:1.6, sz:0.5}, pos:[0,0.4,-0.5],
    vel:{y:[0.4,1.0], x:[-1.5,1.5], space:"local"}, flip:{x:4,y:4}, alpha:[[0,0],[0.15,0.75],[1,0]], sizeLife:[[0,0.6],[1,1.3]] },
  // ---------------- body wrap while moving (local, follows the boss)
  { name:"17 BodyMist", mat:"mist", fudge:23, life:[0.22,0.32], size:[2.4,3.4], rot:[0,TAU], rotZ:[-1.5,1.5], shape:{type:"box", sy:5.6, sx:1.4, sz:0.8}, pos:[0,3.2,0],
    bursts:every(T0, T1, 3, 0.04), vel:{z:[-5,-2]}, flip:{x:4,y:4}, alpha:[[0,0],[0.15,0.7],[1,0]], sizeLife:[[0,0.8],[1,1.2]] },
  { name:"18 SpeedLines", mat:"streak", mode:"stretch", vs:0.03, ls:6.0, fudge:-4, life:[0.1,0.16], size:[0.05,0.09], shape:{type:"box", sy:6.4, sx:3.4, sz:1.0},
    pos:[0,3.3,1.6], bursts:every(T0, T1 - 0.04, 4, 0.03), vel:{z:[-22,-14]}, color:[[0,WLI],[0.4,LIL],[1,VIO]], alpha:[[0,0],[0.2,0.9],[1,0]] },
  // ---------------- arrival skid (0.75)
  { name:"20 SkidSpray", mat:"splat", fudge:16, world:true, pos:[0,0.8,1.0], life:0.38, size:[1.8,2.4], rot:[0,TAU], bursts:[[T1,2]], flip:{x:4,y:4} },
  { name:"21 SkidChips", mat:"shard", mode:"stretch", vs:0.07, ls:1.4, fudge:14, world:true, pos:[0,0.4,1.2], life:[0.35,0.6], size:[0.12,0.22], speed:[1.5,4],
    shape:{type:"sphere", r:0.7, th:1}, bursts:[[T1,14]], vel:{z:[2,6], y:[1,3], space:"local"}, drag:2, gravity:1.2, alpha:[[0,0],[0.06,1],[0.7,0.9],[1,0]] },
  { name:"22 SkidMist", mat:"mist", fudge:22, world:true, life:[0.5,0.8], size:[2.2,3.2], rot:[0,TAU], shape:{type:"box", sy:2.5, sx:2.2, sz:1.0}, pos:[0,1.5,0.4],
    bursts:[[T1,6]], vel:{z:[0.5,2.0], y:[0.2,0.6], space:"local"}, flip:{x:4,y:4}, alpha:[[0,0],[0.12,0.75],[1,0]], sizeLife:[[0,0.7],[1,1.25]] },
  { name:"23 SkidSparks", mat:"streak", mode:"stretch", vs:0.05, ls:2.2, fudge:-8, world:true, pos:[0,0.2,1.0], life:[0.2,0.4], size:[0.05,0.1], speed:[3,7],
    shape:{type:"sphere", r:0.4, th:1}, bursts:[[T1,18]], vel:{z:[2,6], y:[1,2.5], space:"local"}, drag:3, gravity:0.6,
    color:[[0,WLI],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.3]] },
];
const MATS = {
  mist:{tex:"T_InkMist_Sheet", tint:[1,1,1], add:false},
  splat:{tex:"T_InkSplat_Sheet", tint:[1.7,1.4,2.1], add:false},
  splatFlat:{tex:"T_InkSplat_Sheet", tint:[1.7,1.4,2.1], add:false},
  shard:{tex:"T_InkShard", tint:[2.0,1.7,2.4], add:false},
  inkTail:{tex:"T_InkTail", tint:[1.0,0.9,1.2], add:false},
  scratchInk:{tex:"T_Scratch3", tint:[2.0,1.7,2.4], add:false},
  scratchGlow:{tex:"T_Scratch3", tint:[2.2,1.7,3.0], add:true},
  line:{tex:"T_Line", tint:[1.8,1.3,2.8], add:true},
  streak:{tex:"T_Streak", tint:[2.8,2.1,3.8], add:true},
  star:{tex:"T_Star4", tint:[2.4,2.1,3.0], add:true},
  glow:{tex:"T_Glow", tint:[0.9,0.45,1.8], add:true},
  dot:{tex:"T_Dot", tint:[2.0,1.6,2.8], add:true},
};
const DASH = (d, yaw = 0) => { const f = [Math.sin(yaw * D2R) * d, 0, Math.cos(yaw * D2R) * d];
  return [[0, [0,0,0], yaw], [T0, [0,0,0], yaw], [T1, f, yaw], [1.6, f, yaw]]; };
const SCENE = {
  name: "MordianShadowDash",
  bloom:{ threshold:0.95, intensity:1.4, scatter:0.7, tint:[0.95,0.88,1.0] }, tonemapping:"neutral",
  systems:[ { id:"dash", role:"attached", loop:false, duration:T1 + 0.02, layers:LAYERS } ],
  demo:{
    interval:1.7, bg:[0.36,0.35,0.38], floor:[0.30,0.29,0.31], grid:[0.36,0.35,0.38], dummy:{height:7.6, tone:0.25},
    scenarios:{
      side:{ label:"옆 (8 m)", path:DASH(8), camera:{ target:[0,2.6,4], dist:19, yaw:15, pitch:16 } },
      toward:{ label:"플레이어 쪽으로", path:DASH(8), camera:{ target:[0,2.6,7], dist:13, yaw:75, pitch:12 } },
      behind:{ label:"뒤에서", path:DASH(8), camera:{ target:[0,2.6,4], dist:16, yaw:-115, pitch:20 } },
      short:{ label:"짧게 (4 m)", path:DASH(4, 40), camera:{ target:[1.3,2.6,1.5], dist:15, yaw:-20, pitch:18 } },
    },
  },
};
