// MordianChainSpin — 모르디안의 원령 shackle-chain spin (attached to the boss feet, facing fixed at cast).
// t = 0 = chain_spin motion 0.6 s (arms out). Two full turns 0 → 1.3 s (motion 0.6 → 1.9), shock ring at 1.3 s.
// Spin = clockwise seen from above (+Y rotation, Unity). Two chains (one per arm) start at the boss's right (+X) / left (−X).
const TAU = Math.PI * 2, SPIN = 1.3, OMEGA = 2 * TAU / SPIN;           // 9.67 rad/s
const VIO = [0.54,0.235,1.0], LIL = [0.84,0.59,1.0], WLI = [0.95,0.9,1.0], GLD = [1,0.82,0.5], GLDD = [0.75,0.55,0.25];
function merge(parts) { const verts = [], uvs = [], tris = []; for (const m of parts) { const o = verts.length; verts.push(...m.verts); uvs.push(...m.uvs); tris.push(...m.tris.map(i => i + o)); } return { verts, uvs, tris }; }
const P3 = (phi, r, y) => [Math.cos(phi) * r, y, Math.sin(phi) * r];
// XZ arcs: phi = -s*span, head (U=1) leads a +Y spin. The head sits at phi = -span, so a start rotation of -span puts the head on +X.
const arcBand = (span, r0, y0, r1, y1, taper = true) => ribbon(96, s => { const phi = -s * span, k = taper ? 0.55 + 0.45 * Math.pow(s, 0.5) : 1;
  return [P3(phi, r0 + (r1 - r0) * (1 - k) * 0.5, y0 + (y1 - y0) * (1 - k) * 0.5), P3(phi, r1 - (r1 - r0) * (1 - k) * 0.5, y1 - (y1 - y0) * (1 - k) * 0.5)]; });
const ring = (r, hw, y, seg = 128) => ribbon(seg, s => { const phi = -s * TAU; return [P3(phi, r - hw, y), P3(phi, r + hw, y)]; });
const CH_SPAN = 150 * D2R, SM_SPAN = 170 * D2R, LN_SPAN = 95 * D2R;
const EXTRA_MESHES = {
  chainBand: arcBand(CH_SPAN, 5.0, 0.85, 5.5, 2.05),                 // dark band with gold links (outer, sagging chain tip blur)
  chainBandIn: arcBand(CH_SPAN * 0.85, 3.7, 1.45, 4.1, 2.35),        // second (inner) chain layer
  spinSmear: arcBand(SM_SPAN, 5.9, 1.45, 1.4, 2.8, false),   // (listed outer → inner: texture V=1 = outer edge after the preview's V flip)              // violet afterglow sector from the hands to the tip
  spinLine: ribbon(64, s => { const phi = -s * LN_SPAN, hw = 0.004 + 0.016 * Math.pow(Math.sin(Math.PI * s), 0.7); return [P3(phi, 1 - hw, 0), P3(phi, 1 + hw, 0)]; }),
  groundRings: merge([ring(5.75, 0.05, 0.03), ring(6.15, 0.025, 0.03), ring(2.2, 0.03, 0.03)]),
  groundRingsIn: merge([ring(5.95, 0.018, 0.035), ring(5.45, 0.018, 0.035)]),
  scratchArc: ribbon(32, s => { const phi = -s * 42 * D2R; return [P3(phi, 4.6, 0.04), P3(phi, 5.9, 0.04)]; }),
  shockLine: ring(1, 0.012, 0.05),
};
const A0 = -CH_SPAN, AS = -SM_SPAN;                                     // start rotations: chain heads on +X / −X
const ARM = a => [0, [a, a], 0];
const hold = (life, a) => ({ life, alpha:[[0,0],[0.06,a],[0.88,a],[1,0]] });

const SPIN_LAYERS = [];
for (const [k, off] of [["R", 0], ["L", Math.PI]]) {                   // one particle per arm per spinning layer
  SPIN_LAYERS.push(
    Object.assign({ name:`S${k}1 ChainBand`, mat:"chain", mode:"mesh", mesh:"chainBand", fudge:12, size:1, rot3:ARM(A0 + off), rotLife:[0,OMEGA,0], bursts:[[0,1]],
      sizeLife:[[0,0.55,0,3],[0.12,1],[1,1]] }, hold(SPIN, 1)),
    Object.assign({ name:`S${k}2 ChainEcho`, mat:"chain", mode:"mesh", mesh:"chainBand", fudge:13, size:1.03, rot3:ARM(A0 + off - 0.35), rotLife:[0,OMEGA,0], bursts:[[0.035,1]],
      sizeLife:[[0,0.55,0,3],[0.12,1],[1,1]] }, hold(SPIN - 0.035, 0.45)),
    Object.assign({ name:`S${k}3 ChainInner`, mat:"chain", mode:"mesh", mesh:"chainBandIn", fudge:11, size:1, rot3:ARM(A0 * 0.85 + off + 0.15), rotLife:[0,OMEGA,0], bursts:[[0,1]],
      sizeLife:[[0,0.6,0,3],[0.12,1],[1,1]] }, hold(SPIN, 0.85)),
    Object.assign({ name:`S${k}4 Afterglow`, mat:"smear", mode:"mesh", mesh:"spinSmear", fudge:4, size:1, rot3:ARM(AS + off), rotLife:[0,OMEGA,0], bursts:[[0,1]],
      color:[[0,LIL],[0.5,VIO],[1,VIO]], sizeLife:[[0,0.5,0,3],[0.12,1],[1,1]] }, hold(SPIN, 0.8)),
    Object.assign({ name:`S${k}5 AfterglowInk`, mat:"smearInk", mode:"mesh", mesh:"spinSmear", fudge:20, size:1.02, rot3:ARM(AS + off - 0.25), rotLife:[0,OMEGA,0], bursts:[[0,1]],
      sizeLife:[[0,0.5,0,3],[0.12,1],[1,1]] }, hold(SPIN, 0.55)),
  );
}

const LAYERS = [
  // ---------------- base: ground line work (rings draw on, hold, flare at the end) + chain scrape scratches + ink dust swept along the ring
  { name:"01 GroundRings", mat:"line", mode:"mesh", mesh:"groundRings", fudge:30, life:1.75, size:1, rot3:[0,[0,TAU],0], rotLife:[0,0.6,0], bursts:[[0,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.1,0.55],[0.7,0.55],[0.76,1],[1,0]], sizeLife:[[0,0.9,0,1],[0.12,1],[0.74,1],[1,1.08]] },
  { name:"02 GroundRingsIn", mat:"line", mode:"mesh", mesh:"groundRingsIn", fudge:31, life:1.6, size:1, rot3:[0,[0,TAU],0], rotLife:[0,-1.2,0], bursts:[[0.1,1]],
    color:[[0,VIO],[1,VIO]], alpha:[[0,0],[0.12,0.6],[0.78,0.6],[1,0]] },
  { name:"03 ScrapeInk", mat:"scratchInk", mode:"mesh", mesh:"scratchArc", fudge:34, life:[0.7,0.9], size:[0.95,1.05], rot3:[0,[0,TAU],0], bursts:[[0.08,2,8,0.15]],
    alpha:[[0,0],[0.08,0.9],[0.6,0.75],[1,0]] },
  { name:"04 ScrapeGlow", mat:"scratchGlow", mode:"mesh", mesh:"scratchArc", fudge:33, life:[0.4,0.55], size:[0.95,1.05], rot3:[0,[0,TAU],0], bursts:[[0.08,2,8,0.15]],
    color:[[0,WLI],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.08,1],[1,0]] },
  { name:"05 InkDust", mat:"mist", fudge:22, life:[0.6,0.9], size:[1.4,2.4], rot:[0,TAU], rotZ:[-1,1], shape:{type:"circle", r:5.4, th:0.12, y:0.35},
    bursts:[[0.1,5],[0.3,7],[0.5,9],[0.7,11],[0.9,12],[1.1,12]], vel:{orb:[OMEGA * 0.35, OMEGA * 0.55], y:[0.4,1.2], rad:[0.5,1.5]}, flip:{x:4,y:4},
    alpha:[[0,0],[0.15,0.8],[0.6,0.6],[1,0]], sizeLife:[[0,0.5],[1,1.25]] },
  { name:"06 InkSpray", mat:"splatB", fudge:21, life:[0.35,0.5], size:[0.9,1.4], rot:[0,TAU], shape:{type:"circle", r:5.3, th:0.1, y:0.3},
    bursts:[[0.12,3],[0.35,4],[0.6,5],[0.85,6],[1.05,6]], vel:{orb:[OMEGA * 0.4, OMEGA * 0.6], y:[0.2,0.8]}, flip:{x:4,y:4} },
  { name:"07 InkChips", mat:"shard", mode:"stretch", vs:0.08, ls:1.4, fudge:18, life:[0.4,0.7], size:[0.12,0.22], speed:[2,4],
    shape:{type:"circle", r:5.3, th:0.15, y:0.25}, bursts:[[0.1,6,12,0.1]], vel:{orb:[OMEGA * 0.5, OMEGA * 0.8], y:[1.5,3.5]}, gravity:1.0, drag:1,
    alpha:[[0,0],[0.08,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.5]] },
  // ---------------- top: start flashes at the hands, speed lines, motes
  { name:"08 HandFlash", mat:"star", fudge:-10, life:0.18, size:1.6, rot:[0,45 * D2R], shape:{type:"box", sy:0, sx:4.4, sz:0}, pos:[0,2.8,0], bursts:[[0,2]],
    sizeLife:[[0,0.3],[0.25,1],[1,0]] },
  { name:"09 SpeedLines", mat:"line", mode:"mesh", mesh:"spinLine", fudge:-2, life:[0.22,0.32], size:[3.5,6.3], rot3:[[-0.12,0.12],[0,TAU],[-0.12,0.12]],
    shape:{type:"box", sy:2.6}, pos:[0,1.9,0], rotLife:[0,[OMEGA * 1.1, OMEGA * 1.4],0], bursts:[[0.05,2],[0.2,3],[0.35,3],[0.5,4],[0.65,4],[0.8,5],[0.95,5],[1.1,5]],
    color:[[0,WLI],[0.4,LIL],[1,VIO]], alpha:[[0,0],[0.15,0.9],[0.6,0.7],[1,0]], sizeLife:[[0,0.92],[1,1.04]] },
  { name:"10 Motes", mat:"dot", fudge:-6, life:[1.0,1.8], size:[0.05,0.12], speed:[0.2,0.8], shape:{type:"circle", r:4.6, th:0.8, y:1.0},
    bursts:[[0.2,10],[0.6,14],[1.0,18],[SPIN,24]], vel:{y:[0.6,1.6], orb:[1,2]}, drag:1, noise:{s:0.6, f:0.8, sp:0.6},
    color:[[0,WLI],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.1,1],[0.6,0.8],[1,0]] },
  { name:"11 BossMist", mat:"mist", fudge:24, life:[0.9,1.3], size:[2.2,3.2], rot:[0,TAU], rotZ:[-0.5,0.5], shape:{type:"circle", r:1.4, th:1, y:1.2},
    bursts:[[0,4],[0.4,4],[0.8,4]], vel:{orb:[2,3], y:[0.2,0.6]}, flip:{x:4,y:4}, alpha:[[0,0],[0.2,0.55],[0.6,0.45],[1,0]], sizeLife:[[0,0.6],[1,1.2]] },
  // ---------------- end (t = 1.3): shock ring outward + ink ring + flash, rings flare (01)
  { name:"12 ShockRing", mat:"shock", mode:"mesh", mesh:"quad", fudge:28, pos:[0,0.08,0], life:0.45, size:12, rot3:[0,[0,TAU],0], bursts:[[SPIN,1]],
    sizeLife:[[0,0.92,0,1.2],[1,1.55]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.85],[0.5,0.6],[1,0]] },
  { name:"13 ShockLine", mat:"line", mode:"mesh", mesh:"shockLine", fudge:26, pos:[0,0.1,0], life:0.38, size:6, bursts:[[SPIN,1]],
    sizeLife:[[0,1,0,4],[1,1.75]], color:[[0,WLI],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.6,0.8],[1,0]] },
  { name:"14 ShockLine2", mat:"line", mode:"mesh", mesh:"shockLine", fudge:27, pos:[0,1.0,0], life:0.32, size:5.6, rot3:[[-0.1,0.1],0,[-0.1,0.1]], bursts:[[SPIN + 0.04,1]],
    sizeLife:[[0,1,0,3],[1,1.5]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.8],[1,0]] },
  { name:"15 ShockInk", mat:"mist", fudge:23, life:[0.55,0.8], size:[1.4,2.2], rot:[0,TAU], shape:{type:"circle", r:5.6, th:0.05, y:0.4}, bursts:[[SPIN,22]],
    speed:[3,5], drag:4, vel:{y:[0.3,0.8]}, flip:{x:4,y:4}, alpha:[[0,0],[0.1,0.85],[0.5,0.6],[1,0]], sizeLife:[[0,0.5],[1,1.3]] },
  { name:"16 ShockSparks", mat:"streak", mode:"stretch", vs:0.05, ls:2.5, fudge:-8, life:[0.25,0.45], size:[0.06,0.11], shape:{type:"circle", r:5.6, th:0.05, y:0.5},
    bursts:[[SPIN,36]], speed:[6,11], drag:4, vel:{y:[0.5,2.5]}, color:[[0,WLI],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.3]] },
  { name:"17 EndFlash", mat:"glow", fudge:-4, pos:[0,1.6,0], life:0.25, size:4.5, bursts:[[SPIN,1]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.3],[1,0]], sizeLife:[[0,0.5],[0.3,1],[1,1.1]] },
  { name:"18 ChainSparks", mat:"goldSpark", mode:"stretch", vs:0.05, ls:2.5, fudge:-8, life:[0.25,0.45], size:[0.05,0.09], speed:[0.5,2],
    shape:{type:"circle", r:5.3, th:0.06, y:1.3}, bursts:[[0.05,5,25,0.05]], vel:{orb:[OMEGA * 0.7, OMEGA * 0.95], y:[0.5,2]},
    drag:2, gravity:0.8, color:[[0,[1,0.95,0.8]],[0.4,GLD],[1,GLDD]], alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.3]] },
  ...SPIN_LAYERS,
];
const MATS = {
  chain:{tex:"T_ChainBand", tint:[1.8,1.6,1.9], add:false},
  smear:{tex:"T_SpinSmear", tint:[1.6,1.1,2.4], add:true},
  smearInk:{tex:"T_SpinSmear", tint:[0.08,0.05,0.12], add:false},
  line:{tex:"T_Line", tint:[1.8,1.3,2.8], add:true},
  scratchInk:{tex:"T_Scratch3", tint:[2.0,1.7,2.4], add:false},
  scratchGlow:{tex:"T_Scratch3", tint:[2.2,1.7,3.0], add:true},
  mist:{tex:"T_InkMist_Sheet", tint:[1,1,1], add:false},
  splatB:{tex:"T_InkSplat_Sheet", tint:[1.6,1.4,2.0], add:false},
  shard:{tex:"T_InkShard", tint:[2.0,1.7,2.4], add:false},
  goldSpark:{tex:"T_Dot", tint:[2.4,1.8,0.9], add:true},
  streak:{tex:"T_Streak", tint:[2.4,1.9,3.2], add:true},
  star:{tex:"T_Star4", tint:[2.4,2.1,3.0], add:true},
  shock:{tex:"T_ShockRing", tint:[1.6,1.2,2.4], add:true},
  glow:{tex:"T_Glow", tint:[0.9,0.45,1.8], add:true},
  dot:{tex:"T_Dot", tint:[2.0,1.6,2.8], add:true},
};
const STAY = [[0, [0,0,0], 0], [3, [0,0,0], 0]];
const TGT = [[0,0,4.6,180],[-4.2,0,2.4,120],[3.8,0,-3.0,-40],[5.2,0,2.0,250]];
const SCENE = {
  name: "MordianChainSpin",
  bloom:{ threshold:0.95, intensity:1.4, scatter:0.7, tint:[0.95,0.88,1.0] }, tonemapping:"neutral",
  systems:[ { id:"spin", role:"attached", loop:false, duration:SPIN + 0.05, layers:LAYERS } ],
  demo:{
    interval:2.3, bg:[0.36,0.35,0.38], floor:[0.30,0.29,0.31], grid:[0.36,0.35,0.38], dummy:false, props:[{ c:[0,3.8,0], e:[0.85,3.4,0.65], shade:[0.07,0.05,0.1] }],
    scenarios:{
      wide:{ label:"전체", path:STAY, targets:TGT, camera:{ target:[0,1.5,0], dist:20, yaw:70, pitch:28 } },
      player:{ label:"플레이어 시점", path:STAY, targets:TGT, camera:{ target:[0,2.2,0], dist:9, yaw:95, pitch:10 } },
      top:{ label:"위", path:STAY, targets:TGT, camera:{ target:[0,0,0], dist:20, yaw:90, pitch:80 } },
    },
  },
};
