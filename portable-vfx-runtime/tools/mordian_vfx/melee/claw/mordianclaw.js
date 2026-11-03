// MordianClaw — 모르디안의 원령 giant three-line claw swipe (static).
// P = boss feet (ground under the boss centre), M = boss horizontal facing (+Z forward, +X = boss's right).
// claw_right: boss's upper-right → lower-left (attack, start at motion 0.45 s). claw_left: mirror (attack2).
// t = 0 = hit start; the stroke head sweeps the whole arc in ~0.18 s; everything is gone by ~1.1 s.
const TAU = Math.PI * 2;
const INKC = [0.07,0.05,0.1], VIO = [0.54,0.235,1.0], LIL = [0.84,0.59,1.0], WLI = [0.95,0.9,1.0], SHD = [0.3,0.15,0.48];
const SWEEP = 0.18;                                       // head reaches U=s at age = SWEEP * s^(1/0.7) (T_ClawSheet wipe, life 0.5)
const tAt = s => SWEEP * Math.pow(s, 1 / 0.7) * (0.5 / 0.5);
// ---------------------------------------------------------------- claw path (right hand, boss frame, metres)
function C(s, side) {                                     // centre of the middle claw line
  const th = (58 - 120 * s) * D2R, r = 3.5 + 2.1 * Math.sin(Math.PI * s), y = 4.2 - 3.6 * Math.pow(s, 1.15);
  return [side * Math.sin(th) * r, y, Math.cos(th) * r];
}
function frame(s, side) {                                 // T = tangent, R = horizontal radial, Wv = width dir (in the cylinder surface, ⊥ T)
  const e = 1e-3, a = C(Math.max(0, s - e), side), b = C(Math.min(1, s + e), side), T = norm(sub(b, a));
  const p = C(s, side), R = norm([p[0], 0, p[2]]); let Wv = norm(cross(R, T)); if (Wv[1] < 0) Wv = mul(Wv, -1);
  return { p, T, R, Wv };
}
function line(side, off, s0, s1, hw, rOff = 0) {          // one claw ribbon: offset `off` m along Wv, half width hw
  return ribbon(72, u => { const s = s0 + (s1 - s0) * u, f = frame(s, side), c = add(add(f.p, mul(f.Wv, off)), mul(f.R, rOff)),
      h = hw * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * Math.min(1, u * 1.08)), 0.6));
    return [sub(c, mul(f.Wv, h)), add(c, mul(f.Wv, h))]; });
}
function merge(parts) { const verts = [], uvs = [], tris = []; for (const m of parts) { const o = verts.length; verts.push(...m.verts); uvs.push(...m.uvs); tris.push(...m.tris.map(i => i + o)); } return { verts, uvs, tris }; }
function groundScratch(side) {                            // curved band on the ground under the lower half of the arc (3 gouges in V)
  return ribbon(48, u => { const s = 0.42 + 0.66 * u, p = C(Math.min(s, 1), side), q = s > 1 ? add(p, mul(frame(1, side).T, (s - 1) * 6)) : p;
    const f = frame(Math.min(s, 1), side), Tg = norm([f.T[0], 0, f.T[2]]), Nn = [Tg[2], 0, -Tg[0]], g = [q[0] * 0.92, 0.035, q[2] * 0.92];
    return [sub(g, mul(Nn, 0.85)), add(g, mul(Nn, 0.85))]; });
}
const EXTRA_MESHES = {};
for (const [k, side] of [["R", 1], ["L", -1]]) {
  EXTRA_MESHES["claw" + k] = merge([line(side, 0.66, 0.05, 0.93, 0.32), line(side, 0, 0, 1, 0.4), line(side, -0.66, 0.1, 0.98, 0.32)]);
  EXTRA_MESHES["clawCore" + k] = merge([line(side, 0.66 + 0.14, 0.05, 0.93, 0.07), line(side, 0.17, 0, 1, 0.09), line(side, -0.66 + 0.14, 0.1, 0.98, 0.07)]);
  EXTRA_MESHES["clawWake" + k] = line(side, 0, 0, 1, 1.25, -0.35);
  EXTRA_MESHES["scratch" + k] = groundScratch(side);
}

function clawLayers(k, side) {
  const P = s => C(s, side), F = s => frame(s, side);
  const L = [
    // ---- base: ground answers (three gouges + ink splat where the claw tips hit the ground)
    { name:"01 GroundScratchInk", mat:"scratchInk", mode:"mesh", mesh:"scratch" + k, fudge:40, life:0.95, size:1, bursts:[[0.1,1]],
      alpha:[[0,0],[0.06,1],[0.55,0.9],[1,0]] },
    { name:"02 GroundScratchGlow", mat:"scratchGlow", mode:"mesh", mesh:"scratch" + k, fudge:38, life:0.55, size:1, bursts:[[0.1,1]],
      color:[[0,WLI],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.05,1],[0.4,0.7],[1,0]] },
    { name:"03 TipSplat", mat:"splat", mode:"mesh", mesh:"quad", fudge:36, pos:add(mul(P(0.97), 0.95), [0, -P(0.97)[1] + 0.04, 0]), life:0.7, size:[2.6,3.0],
      rot3:[0,[0,TAU],0], bursts:[[SWEEP,1]], flip:{x:4,y:4} },
    // ---- mid: the focal shape = 3 parallel crescent claws (wake behind, ink body, violet rim glow, hot core line, echo)
    { name:"04 ClawWake", mat:"wakeInk", mode:"mesh", mesh:"clawWake" + k, fudge:24, life:0.6, size:1, bursts:[[0,1]], flip:{x:1,y:16},
      alpha:[[0,0.5],[1,0.5]] },
    { name:"05 ClawInk", mat:"clawInk", mode:"mesh", mesh:"claw" + k, fudge:14, life:0.5, size:1, bursts:[[0,1]], flip:{x:1,y:16} },
    { name:"06 ClawGlow", mat:"clawGlow", mode:"mesh", mesh:"claw" + k, fudge:2, life:0.5, size:1, bursts:[[0,1]], flip:{x:1,y:16} },
    { name:"07 ClawCore", mat:"clawCore", mode:"mesh", mesh:"clawCore" + k, fudge:0, life:0.42, size:1, bursts:[[0,1]], flip:{x:1,y:16},
      alpha:[[0,1],[0.6,1],[1,0]] },
    { name:"08 ClawEcho", mat:"clawGlow", mode:"mesh", mesh:"claw" + k, fudge:3, life:0.5, size:1.06, bursts:[[0.05,1]], flip:{x:1,y:16},
      alpha:[[0,0.25],[1,0.25]], color:[[0,LIL],[1,VIO]] },
    // ---- top: flash where the hand starts the swipe
    { name:"09 StartStar", mat:"star", fudge:-10, pos:P(0.02), life:0.16, size:2.2, rot:[0,45 * D2R], bursts:[[0,1]],
      sizeLife:[[0,0.3],[0.25,1],[1,0]] },
    { name:"10 StartGlow", mat:"glow", fudge:-6, pos:P(0.03), life:0.22, size:2.0, bursts:[[0,1]],
      color:[[0,LIL],[1,VIO]], alpha:[[0,0.32],[1,0]], sizeLife:[[0,0.5],[0.3,1],[1,1.15]] },
    { name:"11 TipStar", mat:"star", fudge:-10, pos:add(P(0.98), [0, 0.15, 0]), life:0.14, size:1.6, rot:[0,45 * D2R], bursts:[[SWEEP,1]],
      sizeLife:[[0,0.3],[0.25,1],[1,0]] },
    { name:"12 TipSparks", mat:"streak", mode:"stretch", vs:0.06, ls:2.2, fudge:-8, pos:add(P(0.97), [0, 0.1, 0]), life:[0.25,0.45], size:[0.05,0.1],
      speed:[3,7], drag:3, gravity:0.6, shape:{type:"sphere", r:0.2, th:1}, bursts:[[SWEEP,18]], vel:{y:[1,3]},
      color:[[0,WLI],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.4]] },
    { name:"13 Motes", mat:"dot", fudge:-6, pos:P(0.5), life:[1.0,1.6], size:[0.05,0.12], speed:[0.2,0.8], shape:{type:"sphere", r:2.6, th:1},
      bursts:[[0.08,22],[0.22,16]], vel:{y:[0.6,1.6]}, drag:1.2, noise:{s:0.6, f:0.8, sp:0.6},
      color:[[0,WLI],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.1,1],[0.6,0.8],[1,0]] },
  ];
  // ---- along the arc: staged bursts follow the stroke head (sparks flung along the swipe, ink shards, dark mist, violet puff)
  const NS = 5;
  for (let i = 0; i < NS; i++) {
    const s = (i + 0.6) / NS, f = F(s), t = +tAt(s).toFixed(3), tg = f.T, tag = String(14 + i * 4);
    const fl = (a, b) => [Math.min(tg[0] * a, tg[0] * b), Math.max(tg[0] * a, tg[0] * b)];
    const vt = (a, b, up = [0, 0]) => ({ x:fl(a, b), y:[Math.min(tg[1] * a, tg[1] * b) + up[0], Math.max(tg[1] * a, tg[1] * b) + up[1]],
      z:[Math.min(tg[2] * a, tg[2] * b), Math.max(tg[2] * a, tg[2] * b)], space:"local" });
    L.push(
      { name:`${tag} ArcSparks${i}`, mat:"streak", mode:"stretch", vs:0.05, ls:2.4, fudge:-8, pos:f.p, life:[0.22,0.4], size:[0.05,0.1],
        speed:[1,3.5], drag:2.5, shape:{type:"sphere", r:0.6, th:1}, bursts:[[t,9]], vel:vt(4,9,[-0.5,0.8]),
        color:[[0,WLI],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.3]] },
      { name:`${(+tag + 1)} ArcShards${i}`, mat:"shard", mode:"stretch", vs:0.08, ls:1.4, fudge:12, pos:f.p, life:[0.4,0.65], size:[0.12,0.24],
        speed:[1.5,3.5], drag:1.5, gravity:0.9, shape:{type:"sphere", r:0.5, th:1}, bursts:[[t + 0.02,4],[t + 0.16,6]], vel:vt(1.5,4),
        alpha:[[0,0],[0.08,1],[0.7,0.9],[1,0]], sizeLife:[[0,1],[1,0.5]] },
      { name:`${(+tag + 2)} ArcMist${i}`, mat:"mist", fudge:22, pos:f.p, life:[0.7,1.0], size:[1.7,2.6], rot:[0,TAU], rotZ:[-0.6,0.6],
        shape:{type:"sphere", r:0.55, th:1}, bursts:[[t + 0.03,3]], vel:vt(0.4,1.0,[0.1,0.4]), flip:{x:4,y:4},
        alpha:[[0,0],[0.15,0.75],[0.6,0.6],[1,0]], sizeLife:[[0,0.6],[1,1.2]] },
      { name:`${(+tag + 3)} ArcEmbers${i}`, mat:"dot", fudge:-5, pos:f.p, life:[0.5,1.0], size:[0.05,0.11], speed:[0.3,1.2],
        shape:{type:"sphere", r:0.6, th:1}, bursts:[[t + 0.04,10],[t + 0.18,6]], vel:vt(0.6,1.8,[0.4,1.2]), drag:1.5, noise:{s:0.9, f:1.0, sp:0.8},
        color:[[0,WLI],[0.25,LIL],[1,VIO]], alpha:[[0,0],[0.08,1],[0.6,0.85],[1,0]], sizeLife:[[0,1],[0.7,0.8],[1,0]] },
    );
  }
  return L;
}
const MATS = {
  clawInk:{tex:"T_ClawSheet", tint:[2.2,1.9,2.6], add:false},       // ink body stays dark, rim blooms
  clawGlow:{tex:"T_ClawSheet", tint:[1.5,1.1,2.3], add:true},
  wakeInk:{tex:"T_ClawSheet", tint:[0.7,0.55,0.9], add:false},
  clawCore:{tex:"T_ClawSheet", tint:[3.0,2.6,3.6], add:true},
  scratchInk:{tex:"T_Scratch3", tint:[2.0,1.7,2.4], add:false},
  scratchGlow:{tex:"T_Scratch3", tint:[2.2,1.7,3.0], add:true},
  splat:{tex:"T_InkSplat_Sheet", tint:[1.8,1.5,2.2], add:false},
  mist:{tex:"T_InkMist_Sheet", tint:[1,1,1], add:false},
  shard:{tex:"T_InkShard", tint:[2.0,1.7,2.4], add:false},
  streak:{tex:"T_Streak", tint:[2.4,1.9,3.2], add:true},
  star:{tex:"T_Star4", tint:[2.4,2.1,3.0], add:true},
  glow:{tex:"T_Glow", tint:[0.9,0.45,1.8], add:true},
  dot:{tex:"T_Dot", tint:[2.0,1.6,2.8], add:true},
};
const BOSS = { c:[0,3.8,0], e:[0.85,3.4,0.65], shade:[0.07,0.05,0.1] };
const TGT = [[1.2,0,4.6,180],[-2.6,0,4.2,160],[3.2,0,3.6,200]];
const SCENE = {
  name: "MordianClaw",
  bloom:{ threshold:0.95, intensity:1.4, scatter:0.7, tint:[0.95,0.88,1.0] }, tonemapping:"neutral",
  systems:[
    { id:"claw_right", role:"static", loop:false, duration:0.4, layers:clawLayers("R", 1) },
    { id:"claw_left", role:"static", loop:false, duration:0.4, layers:clawLayers("L", -1) },
  ],
  demo:{
    interval:1.4, bg:[0.36,0.35,0.38], floor:[0.30,0.29,0.31], grid:[0.36,0.35,0.38], props:[BOSS],
    scenarios:{
      front:{ label:"정면(오른손)", targets:TGT, place:{ claw_left:{ at:[0,0,0], start:99 } }, camera:{ target:[0,2.0,2.5], dist:15, yaw:75, pitch:14 } },
      frontL:{ label:"정면(왼손)", targets:TGT, place:{ claw_right:{ at:[0,0,0], start:99 } }, camera:{ target:[0,2.0,2.5], dist:15, yaw:105, pitch:14 } },
      combo:{ label:"연타 (오른→왼)", targets:TGT, place:{ claw_right:{ at:[0,0,0], start:0 }, claw_left:{ at:[0,0,0], start:0.45 } }, camera:{ target:[0,2.0,2.5], dist:16, yaw:90, pitch:20 } },
      player:{ label:"플레이어 시점", targets:TGT.slice(1), place:{ claw_left:{ at:[0,0,0], start:99 } }, camera:{ target:[0,2.6,0], dist:8.5, yaw:100, pitch:8 } },
      top:{ label:"위", targets:TGT, place:{ claw_left:{ at:[0,0,0], start:99 } }, camera:{ target:[0,0,3], dist:15, yaw:91, pitch:75 } },
    },
  },
};
