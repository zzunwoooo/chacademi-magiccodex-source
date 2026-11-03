// MordianSealBeam (모르디안의 원령 — 기믹 ⑥ 대마법사의 봉인)
// The boss fires a ~2-block-wide black beam (black core + violet rim + swirling violet edge + flowing ink ribbons) at a seal-stone pillar and holds it.
// Every 6–8 s the game moves it to another target. A player who steps into the line becomes the beam's end and blocks it.
// Systems:
//   beam              link, loop: source = boss beam point (hand / chest), target = the pillar's surface point facing the boss, or the blocking player's chest. Every frame.
//                      The game simply moves the target point to the player's chest while someone blocks (no restart).
//   beam_blocked_hit  attached to the BLOCKING PLAYER (feet, facing): violet sparks, ink and scratch flashes off the body. Start when blocking starts, stop when it ends.
//   pillar_crack      impact, one shot per crack stack on an unblocked pillar: hitPoint = beam end on the pillar surface, surfaceNormal = pillar normal there (toward the boss).
//   beam_switch       link, one shot when the beam changes target: stop the old `beam` (it fades in 0.08 s), play beam_switch with the OLD two points, then start a new `beam`.
const TAU = Math.PI * 2;
const WH = [0.97,0.92,1], LIL = [0.84,0.6,1], VIO = [0.54,0.24,1], DVIO = [0.3,0.12,0.6];
const lin = (a, b) => [[0, a, 0, b - a], [1, b, b - a, 0]];
const cst = s => [[0, s, 0, 0], [1, s, 0, 0]];
const range = n => Array.from({ length: n }, (_, k) => k);
const NOWAVE = { amp:0, cycles:1, phase:0, vr:1 };
const STR = { x:0, y:0 };
const pth = (progress, bend = STR, wave = NOWAVE) => ({ progress, bend, wave });
// fixed-length stretched segments (length = size·ls + |v|·vs, vs tiny: boss dashes / moving blockers change linkLength fast and must not stretch them)
const NB = 20, DS = 1 / NB;
const inward = k => (k < NB / 2 ? -1 : 1);
const seg = (k, dir = inward(k)) => { const c = (k + 0.5) * DS; return lin(c - dir * 0.15 * DS, c + dir * 0.15 * DS); };
const AT_END = { path:pth(cst(1)) };
const HEAD = [[0,0,0,1.5],[1,1,0.5,0]], T_HEAD = 0.16;

// ---------------------------------------------------------------- BEAM (loop)
const segLayer = (name, mat, k, o) => ({ name:`${name}${String(k).padStart(2,"0")}`, mat, mode:"stretch", ls:(o.len ?? 1.15) / ((o.size[0] + o.size[1]) / 2), vs:0.02, fudge:o.fudge, life:o.life,
  size:o.size, rate:o.rate, bursts:[[0, o.pre ?? 2]], path:{ progress:seg(k), bend:o.bend || STR, wave:o.wave || NOWAVE }, color:o.color,
  alpha:[[0,0],[0.25,o.a],[0.75,o.a],[1,0]], onStop:0.08 });
const BEAM = [
  ...range(NB).flatMap(k => [
    segLayer("B0 Core", "body", k, { life:0.2, rate:11, size:[1.25,1.4], a:0.95, fudge:-7 }),
    segLayer("B1 Edge", "edge", k, { life:0.2, rate:9, size:[1.85,2.05], a:0.6, fudge:-1, color:[[0,LIL],[1,VIO]] }),
    segLayer("B2 Aura", "haze", k, { life:0.3, rate:4, size:[2.6,3.0], a:0.1, fudge:8, color:[[0,VIO],[1,DVIO]] }),
    segLayer("B3 Spine", "body", k, { life:0.14, rate:12, size:[0.55,0.65], a:1, fudge:-8 }),               // denser black spine in the middle
  ]),
  // swirl at the rim: violet streaks + ink ribbons on a helix, both handednesses, flowing toward the target
  ...[1, -1].map((vr, j) => ({ name:`C0${j} Swirl`, mat:"streak", mode:"stretch", ls:0.4, vs:0.03, fudge:-9, life:[0.55,0.7], size:[0.1,0.16], rate:44, bursts:[[0,20]],
    path:{ progress:lin(0.02, 0.97), bend:STR, wave:{amp:[0.78,0.92], cycles:[4.2,5.2], phase:[0,TAU], vr} }, color:[[0,WH],[0.4,LIL],[1,VIO]],
    alpha:[[0,0],[0.12,1],[0.88,1],[1,0]], onStop:0.08 })),
  ...[1, -1].map((vr, j) => ({ name:`C1${j} InkRibbon`, mat:"ink", mode:"stretch", ls:0.6, vs:0.035, fudge:-8, life:[0.7,0.9], size:[0.12,0.2], rate:22, bursts:[[0,12]],
    path:{ progress:lin(0.02, 0.97), bend:STR, wave:{amp:[0.6,0.85], cycles:[1.6,2.2], phase:[0,TAU], vr} }, alpha:[[0,0],[0.12,1],[0.88,1],[1,0]], onStop:0.08 })),
  ...range(NB).map(k => ({ name:`C2${String(k).padStart(2,"0")} Crackle`, mat:"bolt", mode:"stretch", ls:1.2 / 0.32, vs:0.02, fudge:-4, life:0.09, size:[0.25,0.4], rate:0.7,
    flip:{ x:1, y:4, cycles:2 }, path:{ progress:seg(k), bend:{ x:[-2.2,2.2], y:[-2.2,2.2] }, wave:{amp:[0.05,0.2], cycles:[2,4], phase:[0,TAU], vr:1} },
    color:[[0,WH],[1,VIO]], alpha:[[0,0.5],[0.2,1],[1,0]], onStop:0.03 })),
  { name:"C30 Motes", mat:"dot", fudge:-2, life:[0.9,1.4], size:[0.05,0.1], rate:30, path:{ progress:lin(0.05, 0.95), bend:{x:[-2.4,2.4], y:[-1.5,2.8]}, wave:{amp:[0,0.3], cycles:1, phase:[0,TAU], vr:1} },
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.2,0.9],[1,0]], sizeLife:[[0,1],[1,0.4]], onStop:0.1 },
  // ignition: a head runs to the target in 0.16 s
  { name:"D00 HeadStar", mat:"star", fudge:-6, life:T_HEAD, size:1.2, rot:[0,0.8], bursts:[[0,1]], path:pth(HEAD), color:[[0,WH],[1,LIL]], alpha:[[0,1],[0.9,1],[1,0]] },
  { name:"D01 HeadBody", mat:"body", mode:"stretch", ls:0.5, vs:0.035, fudge:-7, life:T_HEAD, size:1.4, bursts:[[0,1],[0.03,1],[0.06,1]], path:pth(HEAD), alpha:[[0,1],[0.9,1],[1,0]] },
  { name:"D02 HeadEdge", mat:"edge", mode:"stretch", ls:0.5, vs:0.035, fudge:-1, life:T_HEAD, size:2.2, bursts:[[0,1],[0.03,1]], path:pth(HEAD), color:[[0,WH],[1,LIL]], alpha:[[0,1],[1,0.6]] },
  // origin at the boss: rotating seal rings facing along the beam, scratch sigil, glow, dark mist
  { name:"E00 OriginRing", mat:"shackle", mode:"mesh", mesh:"ringXY", fudge:-1, life:60, size:2.1, rot3:[0,0,0], rotLife:[0,0,1.6], bursts:[[0,1]],
    alpha:[[0,0],[0.003,1],[0.998,1],[1,0]], sizeLife:[[0,0.3,0,0],[0.0025,1.15],[0.004,1],[1,1]], onStop:0.08 },
  { name:"E01 OriginRing2", mat:"ring", mode:"mesh", mesh:"ringXY", fudge:-1, life:60, size:2.8, rot3:[0,0,0], rotLife:[0,0,-0.9], bursts:[[0,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.003,0.7],[0.998,0.7],[1,0]], sizeLife:[[0,0.4,0,0],[0.004,1],[1,1]], onStop:0.08 },
  { name:"E02 OriginPulse", mat:"ring", mode:"mesh", mesh:"ringXY", fudge:-1, life:0.5, size:2.4, rot3:[0,0,[0,TAU]], rate:3, bursts:[[0,1]],
    color:[[0,WH],[1,VIO]], alpha:[[0,0.8],[1,0]], sizeLife:[[0,1.25,0,-0.9],[1,0.55]], onStop:0.05 },
  { name:"E03 OriginSigil", mat:"scratch", fudge:-6, life:0.6, size:[1.0,1.2], rot:[-0.25,0.25], rate:2.4, bursts:[[0,1]], color:[[0,WH],[1,VIO]],
    alpha:[[0,0],[0.15,0.85],[1,0]], sizeLife:[[0,0.8],[0.3,1],[1,1.1]], onStop:0.05 },
  { name:"E04 OriginGlow", mat:"glow", fudge:-4, life:0.3, size:[1.5,1.7], rate:10, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.5,0.32],[1,0]], onStop:0.05 },
  { name:"E05 OriginCore", mat:"dark", fudge:7, life:0.4, size:[1.0,1.15], rate:8, bursts:[[0,3]], rot:[0,TAU], alpha:[[0,0],[0.3,0.9],[1,0]], onStop:0.05 },
  { name:"E06 OriginMist", mat:"mist", world:true, fudge:9, life:[0.8,1.2], size:[0.8,1.3], speed:[0.3,0.9], shape:{type:"sphere", r:0.4}, rate:10, rot:[0,TAU],
    flip:{x:4,y:4}, drag:1.5, alpha:[[0,0.55],[1,0]] },
  { name:"E07 OriginSparks", mat:"dot", mode:"stretch", ls:1, vs:0.03, world:true, fudge:-4, life:[0.25,0.45], size:[0.03,0.05], speed:[2,4],
    shape:{type:"sphere", r:0.4}, rate:24, bursts:[[0,20]], drag:3, color:[[0,WH],[0.4,LIL],[1,VIO]], alpha:[[0,1],[1,0]] },
  { name:"E08 OriginFlash", mat:"star", fudge:-8, life:0.22, size:2.2, rot:[0,0.8], bursts:[[0,1]], color:[[0,WH],[1,VIO]], alpha:[[0,1],[1,0]] },
  // end of the beam: splash on the target
  { name:"F00 EndStar", mat:"star", fudge:-6, life:0.1, size:[0.8,1.2], rot:[0,0.8], rate:12, bursts:[[T_HEAD,2]], ...AT_END, color:[[0,WH],[1,LIL]], alpha:[[0,1],[1,0]], onStop:0.02 },
  { name:"F01 EndGlow", mat:"glow", fudge:-4, life:0.25, size:[1.5,1.8], rate:10, ...AT_END, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.4,0.38],[1,0]], onStop:0.05 },
  { name:"F02 EndRipple", mat:"ring", mode:"mesh", mesh:"ringXY", fudge:-1, life:0.35, size:2.0, rot3:[0,0,[0,TAU]], rate:5, bursts:[[T_HEAD,1]], ...AT_END,
    color:[[0,WH],[1,VIO]], alpha:[[0,0.8],[1,0]], sizeLife:[[0,0.5,0,1.5],[1,1.4]], onStop:0.05 },
  { name:"F03 EndSplash", mat:"inkshard", mode:"stretch", ls:1.4, vs:0.06, fudge:3, life:[0.22,0.32], size:[0.1,0.16], rate:26,
    path:{ progress:[[0,1,0,-0.25],[1,0.94,0,0]], bend:{x:[-6,6], y:[-6,6]}, wave:NOWAVE }, alpha:[[0,1],[1,0]], onStop:0.03 },
  { name:"F04 EndSparks", mat:"dot", mode:"stretch", ls:1, vs:0.05, fudge:-4, life:[0.2,0.3], size:[0.03,0.05], rate:40,
    path:{ progress:[[0,1,0,-0.2],[1,0.95,0,0]], bend:{x:[-8,8], y:[-8,8]}, wave:NOWAVE }, color:[[0,WH],[0.4,LIL],[1,VIO]], alpha:[[0,1],[1,0]], onStop:0.03 },
];

// ---------------------------------------------------------------- BLOCKED HIT (attached to the blocking player, loop)
const CY = 1.3;
const BLOCK = [
  { name:"G00 Flash", mat:"star", fudge:-8, pos:[0,CY,0], life:0.22, size:1.6, rot:[0,0.8], bursts:[[0,1]], color:[[0,WH],[1,VIO]], alpha:[[0,1],[1,0]] },
  { name:"G01 Burst", mat:"ring", mode:"mesh", mesh:"quad", fudge:-1, pos:[0,CY,0], life:0.3, size:1.4, rot3:[0,0,0], bursts:[[0,1]], color:[[0,WH],[1,VIO]],
    alpha:[[0,0.9],[1,0]], sizeLife:[[0,0.4,0,2.4],[1,1.5]] },
  { name:"G02 Glow", mat:"glow", fudge:-4, pos:[0,CY,0], life:0.22, size:[1.3,1.5], rate:12, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.4,0.45],[1,0]], onStop:0.05 },
  { name:"G03 Stars", mat:"star", fudge:-6, life:0.1, size:[0.5,0.9], rot:[0,0.8], rate:10, shape:{type:"sphere", r:0.3, off:[0,CY,0]}, color:[[0,WH],[1,LIL]], alpha:[[0,1],[1,0]], onStop:0.02 },
  { name:"G04 Sparks", mat:"dot", mode:"stretch", ls:1, vs:0.035, world:true, fudge:-4, life:[0.25,0.45], size:[0.03,0.055], speed:[3,6.5],
    shape:{type:"sphere", r:0.3, off:[0,CY,0]}, rate:70, bursts:[[0,24]], drag:2.5, gravity:0.8, color:[[0,WH],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.7,0.9],[1,0]] },
  { name:"G05 Flames", mat:"streak", mode:"stretch", ls:1.2, vs:0.04, world:true, fudge:-3, life:[0.18,0.3], size:[0.1,0.16], speed:[2,4],
    shape:{type:"sphere", r:0.3, off:[0,CY,0]}, rate:30, drag:3, vel:{y:[0.5,1.5], space:"world"}, color:[[0,WH],[0.5,LIL],[1,VIO]], alpha:[[0,1],[1,0]] },
  { name:"G06 Ink", mat:"inkshard", mode:"stretch", ls:1.8, vs:0.03, world:true, fudge:5, life:[0.35,0.55], size:[0.07,0.12], speed:[2,4.5],
    shape:{type:"sphere", r:0.3, off:[0,CY,0]}, rate:22, bursts:[[0,10]], drag:2.5, gravity:0.9, alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.5]] },
  { name:"G07 Scratch", mat:"scratch", fudge:-7, life:0.3, size:[0.8,1.0], rot:[-0.5,0.5], rate:3, bursts:[[0.05,1]], shape:{type:"sphere", r:0.25, off:[0,CY,0]},
    color:[[0,WH],[1,VIO]], alpha:[[0,0],[0.12,1],[1,0]], sizeLife:[[0,0.8],[0.25,1],[1,1.1]], onStop:0.05 },
  { name:"G08 BodyRing", mat:"ring", mode:"mesh", mesh:"quad", fudge:-1, pos:[0,CY - 0.3,0], life:0.45, size:1.1, rot3:[0,[0,TAU],0], rate:3, color:[[0,LIL],[1,VIO]],
    alpha:[[0,0.8],[1,0]], sizeLife:[[0,0.6,0,1.5],[1,1.35]], onStop:0.05 },
  { name:"G09 Mist", mat:"mist", world:true, fudge:9, life:[0.6,0.9], size:[0.7,1.0], shape:{type:"circle", r:0.4, th:0.4, off:[0,0.3,0]}, rate:8, rot:[0,TAU],
    flip:{x:4,y:4}, vel:{y:0.4, space:"world"}, alpha:[[0,0.5],[1,0]] },
  { name:"G10 Motes", mat:"dot", world:true, fudge:-3, life:[0.6,1.0], size:[0.04,0.07], shape:{type:"circle", r:0.5, th:0.3, off:[0,0.6,0]}, rate:16,
    vel:{y:[0.7,1.3], space:"world"}, noise:{s:0.3, f:1.2, sp:0.6}, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.2,1],[1,0]], sizeLife:[[0,1],[1,0.3]] },
];

// ---------------------------------------------------------------- PILLAR CRACK (impact on the pillar surface, local +Y = surface normal)
const CRACK = [
  { name:"H00 Crack", mat:"crack", mode:"mesh", mesh:"quad", fudge:1, pos:[0,0.03,0], life:2.6, size:1.75, rot3:[0,[0,TAU],0], bursts:[[0,1]], color:[[0,WH],[0.15,LIL],[1,VIO]],
    alpha:[[0,0],[0.02,1],[0.6,0.85],[1,0]], sizeLife:[[0,0.35,0,8],[0.06,1],[1,1]] },
  { name:"H01 Crack2", mat:"crack", mode:"mesh", mesh:"quad", fudge:1, pos:[0,0.035,0], life:2.2, size:1.1, rot3:[0,[0,TAU],0], bursts:[[0.06,1]], color:[[0,WH],[1,VIO]],
    alpha:[[0,0],[0.03,1],[0.5,0.7],[1,0]], sizeLife:[[0,0.4,0,6],[0.07,1],[1,1]] },
  { name:"H02 ScratchMark", mat:"scratch", mode:"mesh", mesh:"quad", fudge:0, pos:[0,0.04,0], life:1.6, size:0.85, rot3:[0,[0,TAU],0], bursts:[[0,1]], color:[[0,WH],[1,VIO]],
    alpha:[[0,0],[0.04,1],[0.3,0.8],[1,0]], sizeLife:[[0,0.7],[0.1,1],[1,1.05]] },
  { name:"H03 Ripple", mat:"ring", mode:"mesh", mesh:"quad", fudge:-1, pos:[0,0.05,0], life:0.35, size:1.4, rot3:[0,0,0], bursts:[[0,1]], color:[[0,WH],[1,VIO]],
    alpha:[[0,0.85],[1,0]], sizeLife:[[0,0.3,0,2.5],[1,1.2]] },
  { name:"H04 Glow", mat:"glow", fudge:-4, pos:[0,0.25,0], life:1.2, size:1.3, bursts:[[0,1]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.6],[0.2,0.35],[1,0]] },
  { name:"H05 Flash", mat:"star", fudge:-6, pos:[0,0.2,0], life:0.18, size:1.3, rot:[0,0.8], bursts:[[0,1]], color:[[0,WH],[1,LIL]], alpha:[[0,1],[1,0]] },
  { name:"H06 Chips", mat:"inkshard", mode:"stretch", ls:1.6, vs:0.03, world:true, fudge:5, life:[0.4,0.6], size:[0.07,0.12], speed:[1,2.5],
    shape:{type:"sphere", r:0.15}, vel:{y:[2,4]}, bursts:[[0,14]], drag:2, gravity:1.2, alpha:[[0,1],[0.7,0.9],[1,0]] },
  { name:"H07 Sparks", mat:"dot", mode:"stretch", ls:1, vs:0.035, world:true, fudge:-4, life:[0.25,0.45], size:[0.03,0.05], speed:[1.5,3],
    shape:{type:"sphere", r:0.12}, vel:{y:[2.5,5]}, bursts:[[0,20]], drag:2.5, gravity:0.8, color:[[0,WH],[0.4,LIL],[1,VIO]], alpha:[[0,1],[1,0]] },
  { name:"H08 Dust", mat:"mist", world:true, fudge:9, life:[0.7,1.0], size:[0.6,0.9], speed:[0.3,0.8], shape:{type:"sphere", r:0.2, off:[0,0.3,0]}, bursts:[[0,4]],
    rot:[0,TAU], flip:{x:4,y:4}, drag:2, alpha:[[0,0.6],[1,0]] },
  { name:"H09 Motes", mat:"dot", world:true, fudge:-3, life:[0.8,1.3], size:[0.04,0.07], shape:{type:"sphere", r:0.4, off:[0,0.2,0]}, rate:14,
    vel:{y:[0.5,1.0], space:"world"}, noise:{s:0.3, f:1.2, sp:0.6}, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.2,1],[1,0]] },
];

// ---------------------------------------------------------------- SWITCH (one shot with the old endpoints): the beam thins out and breaks into ink
const SWITCH = [
  ...range(NB).flatMap(k => [
    { name:`J0${String(k).padStart(2,"0")} CoreOut`, mat:"body", mode:"stretch", ls:1.15 / 1.3, vs:0.02, fudge:-7, life:0.28, size:1.3, bursts:[[0.006 * k, 1]],
      path:{ progress:seg(k), bend:STR, wave:NOWAVE }, alpha:[[0,0.95],[1,0]], sizeLife:[[0,1,0,-1.6],[1,0.05]] },
    { name:`J1${String(k).padStart(2,"0")} EdgeOut`, mat:"edge", mode:"stretch", ls:1.15 / 2.0, vs:0.02, fudge:-1, life:0.22, size:2.0, bursts:[[0.006 * k, 1]],
      path:{ progress:seg(k), bend:STR, wave:NOWAVE }, color:[[0,WH],[1,VIO]], alpha:[[0,1],[1,0]], sizeLife:[[0,1],[1,0.15]] },
    { name:`J2${String(k).padStart(2,"0")} Ink`, mat:"inkshard", mode:"stretch", ls:1.8, vs:0.08, fudge:3, life:[0.4,0.6], size:[0.1,0.16], bursts:[[0.006 * k + 0.04, 2]],
      path:{ progress:[[0,(k + 0.5) * DS,0,0.2],[1,(k + 0.5) * DS + 0.03,0,0]], bend:{x:[-1.8,1.8], y:[-1.2,1.8]}, wave:{amp:[0,0.3], cycles:[1,2], phase:[0,TAU], vr:1} },
      alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1.2],[1,0.4]] },
  ]),
  { name:"J30 Puffs", mat:"mist", fudge:8, life:[0.6,0.9], size:[0.8,1.2], rot:[0,TAU], flip:{x:4,y:4}, bursts:[[0.03,10]],
    path:{ progress:[[0,0.05],[1,0.95]], bend:{x:[-0.8,0.8], y:[-0.6,0.8]}, wave:NOWAVE }, alpha:[[0,0.7],[1,0]], sizeLife:[[0,0.6],[1,1.2]] },
  { name:"J31 Motes", mat:"dot", fudge:-3, life:[0.7,1.1], size:[0.05,0.09], bursts:[[0.02,36]],
    path:{ progress:[[0,0.05],[1,0.95,0.9,0.9]], bend:{x:[-2,2], y:[-1,2.5]}, wave:{amp:[0,0.3], cycles:1, phase:[0,TAU], vr:1} }, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.15,1],[1,0]] },
  { name:"J32 OriginOut", mat:"shackle", mode:"mesh", mesh:"ringXY", fudge:-1, life:0.35, size:2.1, rot3:[0,0,0], rotLife:[0,0,5], bursts:[[0,1]], color:[[0,WH],[1,VIO]],
    alpha:[[0,1],[1,0]], sizeLife:[[0,1,0,-2],[1,0.3]] },
  { name:"J33 OriginPuff", mat:"glow", fudge:-4, life:0.3, size:1.6, bursts:[[0,1]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.5],[1,0]] },
  { name:"J34 EndOut", mat:"glow", fudge:-4, life:0.3, size:1.6, bursts:[[0,1]], ...AT_END, color:[[0,LIL],[1,VIO]], alpha:[[0,0.5],[1,0]] },
];

// ---------------------------------------------------------------- meshes / materials
var EXTRA_MESHES = {
  ringXY: { verts:[[-0.5,-0.5,0],[0.5,-0.5,0],[0.5,0.5,0],[-0.5,0.5,0]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3] },
};
const MATS = {
  body:{tex:"T_BeamBody", tint:[2.3,2.1,2.5], add:false},
  dark:{tex:"T_Glow", tint:[0.03,0.02,0.05], add:false},
  edge:{tex:"T_BeamEdge", tint:[1.9,1.3,2.6], add:true},
  haze:{tex:"T_Glow", tint:[0.5,0.22,0.95], add:true},
  ink:{tex:"T_InkRibbon", tint:[2.4,2.2,2.6], add:false},
  inkshard:{tex:"T_InkShard", tint:[2.4,2.2,2.6], add:false},
  streak:{tex:"T_VioletStreak", tint:[2.2,1.6,2.8], add:true},
  bolt:{tex:"T_Lightning", tint:[2.0,1.4,2.8], add:true},
  glow:{tex:"T_Glow", tint:[1.2,0.65,2.1], add:true},
  star:{tex:"T_Star", tint:[2.1,1.7,2.6], add:true},
  dot:{tex:"T_Dot", tint:[1.6,1.0,2.4], add:true},
  ring:{tex:"T_ThinRing", tint:[1.7,1.15,2.5], add:true},
  shackle:{tex:"T_ShackleRing", tint:[1.5,0.95,2.3], add:true},
  scratch:{tex:"T_Scratch3", tint:[2.0,1.4,2.6], add:true},
  crack:{tex:"T_Crack", tint:[2.2,1.5,2.8], add:true},
  mist:{tex:"T_Mist", tint:[1,1,1], add:false},
};

// ---------------------------------------------------------------- demo (non-normative)
// boss stand-in at the origin facing +Z, beam point FROM; pillar A at (-4.5, 9), pillar B at (5, 9) (boxes 1.6 m wide, faces toward the boss at z = 8.2)
const FROM = [0.6, 3.6, 1.4], TOFF = [0, 1.4, 0];
const BLK = [-3.05, 0, 6.6];
const PLAYER = [[0,[-0.6,0,4.2],-30],[3.6,[-0.6,0,4.2],-30],[4.4,BLK,-30],[7.0,BLK,-30],[8.0,[-0.8,0,5.0],-30],[12,[-0.8,0,5.0],-30]];
const END1 = [[0,[-4.5,0,8.2],0],[4.39,[-4.5,0,8.2],0],[4.41,BLK,0],[12,BLK,0]];
const PROPS = [ { c:[0,4.6,-0.4], e:[1.0,2.4,0.8], shade:[0.13,0.09,0.2] }, { c:[0,6.6,0.42], e:[0.35,0.4,0.05], shade:[0.85,0.85,0.88] },
  { c:[-4.5,2.6,9], e:[0.8,2.6,0.8], shade:[0.42,0.4,0.46] }, { c:[5,2.6,9], e:[0.8,2.6,0.8], shade:[0.42,0.4,0.46] } ];
const crackAt = (t, x) => ({ t, point:[x, 1.4, 8.2], normal:[0,0,-1], incoming:[0,0,1], system:"pillar_crack" });
const SCENE = {
  name: "MordianSealBeam",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.7, tint:[0.95,0.88,1.0] }, tonemapping:"neutral",
  systems:[
    { id:"beam", role:"link", loop:true, duration:60, layers:BEAM },
    { id:"beam_blocked_hit", role:"attached", loop:true, duration:1, layers:BLOCK },
    { id:"pillar_crack", role:"impact", loop:false, duration:0.1, layers:CRACK },
    { id:"beam_switch", role:"link", loop:false, duration:0.2, layers:SWITCH },
  ],
  demo:{
    interval:11.5, bg:[0.2,0.19,0.24], floor:[0.24,0.23,0.27], grid:[0.2,0.19,0.23], dummy:{height:1.8}, props:PROPS,
    scenarios:{},
  },
};
const EVS = [
  { t:0, system:"beam", actor:0, to:2, from:FROM, follow:true },
  { t:4.41, system:"beam_blocked_hit", actor:1 }, { t:7.0, stop:["beam_blocked_hit", "beam"] },
  { t:7.0, system:"beam_switch", actor:0, to:2, from:FROM, follow:true },
  { t:7.05, system:"beam", actor:0, to:3, from:FROM, follow:true },
];
const ACT = [{ path:[[0,[0,0,0],0]], hidden:true }, { path:PLAYER }, { path:END1, hidden:true }, { path:[[0,[5,0,8.2],0]], hidden:true }];
const HITS = [crackAt(1.4, -4.5), crackAt(2.9, -4.5), crackAt(8.6, 5), crackAt(10.1, 5)];
SCENE.demo.scenarios = {
  wide:{ label:"기둥 A → 플레이어가 막음 → 기둥 B로 전환", targetOffset:TOFF, actors:ACT, events:EVS, hits:HITS, camera:{ target:[0,2.2,5], dist:16, yaw:15, pitch:30 } },
  block:{ label:"막는 플레이어 근접", targetOffset:TOFF, actors:ACT, events:EVS, hits:HITS, camera:{ target:[-2.6,1.8,6.2], dist:5.5, yaw:-160, pitch:10 } },
  pillar:{ label:"기둥 균열 근접", targetOffset:TOFF, actors:ACT, events:EVS, hits:HITS, camera:{ target:[-4.5,1.8,8], dist:5.5, yaw:-120, pitch:8 } },
  side:{ label:"측면", targetOffset:TOFF, actors:ACT, events:EVS, hits:HITS, camera:{ target:[-1.5,2.6,4.6], dist:10, yaw:150, pitch:8 } },
};
