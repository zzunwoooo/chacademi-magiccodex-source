const TAU = Math.PI * 2;
const INK = [0.035,0.024,0.055], SHD = [0.165,0.086,0.25], VIO = [0.54,0.235,1.0], LIL = [0.84,0.59,1.0], WL = [0.95,0.9,1.0], DV = [0.33,0.12,0.7];
const sst = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
function merge(parts) { const o = { verts:[], uvs:[], tris:[] };
  for (const p of parts) { const b = o.verts.length; o.verts.push(...p.verts); o.uvs.push(...p.uvs); p.tris.forEach(i => o.tris.push(i + b)); } return o; }
const dXZ = phi => [Math.cos(phi), 0, Math.sin(phi)];
// flat ring strip, V across (0 inner, 1 outer), centre (cx, cz)
function ringStrip(r, w, seg, y, cx = 0, cz = 0) { return ribbon(seg, s => { const d = dXZ(-s * TAU), c = [cx, y, cz]; return [add(c, mul(d, r - w / 2)), add(c, mul(d, r + w / 2))]; }); }
function lineSeg(a, b, w, y) { const dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz), nx = -dz / L * w / 2, nz = dx / L * w / 2;
  return { verts:[[a[0]-nx,y,a[1]-nz],[a[0]+nx,y,a[1]+nz],[b[0]-nx,y,b[1]-nz],[b[0]+nx,y,b[1]+nz]], uvs:[[0,0],[0,1],[1,0],[1,1]], tris:[0,1,2,1,3,2] }; }
// rune band: n arc segments, each mapping U 0..1 onto the rune strip
function runeBand(r, w, n, y) { const parts = [];
  for (let k = 0; k < n; k++) parts.push(ribbon(12, s => { const d = dXZ(-(k + s) * TAU / n); return [add([0,y,0], mul(d, r - w / 2)), add([0,y,0], mul(d, r + w / 2))]; }));
  return merge(parts); }
function polygram(n, step, r, w, y, rot = 0) { const P = []; for (let k = 0; k < n; k++) { const a = rot + k * TAU / n; P.push([r * Math.cos(a), r * Math.sin(a)]); }
  const parts = []; for (let k = 0; k < n; k++) parts.push(lineSeg(P[k], P[(k + step) % n], w, y)); return { parts, P }; }
// crossed vertical planes (V up), base at (cx,0,cz), tip leaning outward by `lean`·h
function spike(cx, cz, w, h, lean, planes = 2) { const parts = [], L = Math.hypot(cx, cz) || 1, ox = cx / L * lean * h, oz = cz / L * lean * h;
  for (let k = 0; k < planes; k++) { const a = k * Math.PI / planes, ux = Math.cos(a) * w / 2, uz = Math.sin(a) * w / 2;
    parts.push({ verts:[[cx-ux,0,cz-uz],[cx+ux,0,cz+uz],[cx-ux+ox,h,cz-uz+oz],[cx+ux+ox,h,cz+uz+oz]], uvs:[[0,0],[1,0],[0,1],[1,1]], tris:[0,1,2,1,3,2] }); }
  return merge(parts); }
function crown(n, r, w, h, lean, seed) { const parts = [];
  for (let k = 0; k < n; k++) { const j = Math.sin(k * 12.9898 + seed) * 43758.5453, f = j - Math.floor(j), a = (k + 0.3 * f) * TAU / n;
    parts.push(spike(r * Math.cos(a), r * Math.sin(a), w * (0.8 + 0.4 * f), h * (0.65 + 0.5 * f), lean)); }
  return merge(parts); }

// MordianForbiddenRing — 모르디안의 원령 "금지된 원" (static, P = boss centre on the ground, +Z = boss facing).
// Started by the mod at boss `roar` 0.8 (t = 0 here).
//   stage 1 (0 → 0.45): black ink pillars erupt in a chain around the DONUT (r 5..13), from the front around both sides to the back.
//   stage 2 (1.0): the INNER DISC (r < 5) erupts around the boss. Forbidden rune-circle line work flashes only at each eruption, then fades.
const RI = 5.0, RO = 13.0, RM = 9.0, TS = 1.0;
const at = (r, a, y = 0) => [r * Math.sin(a), y, r * Math.cos(a)];      // a = angle from +Z (boss front)
const outerStar = polygram(8, 3, 12.55, 0.08, 0.03, Math.PI / 8);
const innerStar = polygram(6, 2, 4.7, 0.06, 0.035, 0);
var EXTRA_MESHES = {
  outerLines: merge([ringStrip(RO, 0.18, 200, 0.03), ringStrip(12.55, 0.07, 200, 0.03), ringStrip(RI + 0.45, 0.06, 120, 0.03), ...outerStar.parts,
                     ...outerStar.P.map(p => ringStrip(0.42, 0.06, 24, 0.03, p[0], p[1])), ...outerStar.P.map(p => ringStrip(0.22, 0.04, 20, 0.03, p[0], p[1]))]),
  outerRunes: runeBand(11.75, 0.85, 28, 0.035),
  innerLines: merge([ringStrip(RI, 0.16, 140, 0.04), ringStrip(4.7, 0.05, 120, 0.04), ringStrip(2.1, 0.05, 72, 0.04), ...innerStar.parts,
                     ...innerStar.P.map(p => ringStrip(0.3, 0.05, 20, 0.04, p[0], p[1]))]),
  innerRunes: runeBand(4.2, 0.55, 12, 0.045),
  ringUnit: merge([ringStrip(1.0, 0.022, 140, 0), ringStrip(0.96, 0.008, 140, 0)]),
  rimUnit: ribbon(160, s => { const d = dXZ(-s * TAU); return [d, add(d, [0, 0.12, 0])]; }),
  pillar: spike(0, 0, 0.3, 1, 0, 3),
  crownIn: crown(14, 2.4, 0.4, 3.0, 0.3, 2.3),
  arc: ribbon(48, s => { const d = dXZ(-s * Math.PI * 0.7); return [mul(d, 0.95), mul(d, 1.05)]; }),
};

// ---------------------------------------------------------------- stage 1: chain eruption around the donut
const CH = [];  // [angle, t]
for (let k = 0; k <= 8; k++) { const t = 0.055 * k; CH.push([k * Math.PI / 8, t]); if (k > 0 && k < 8) CH.push([-k * Math.PI / 8, t]); }
const chainLayers = [];
CH.forEach(([a, t], i) => {
  const r = RM + 0.8 * Math.sin(i * 2.7), p = at(r, a), n = String(i).padStart(2, "0");
  chainLayers.push(
    { name:`C${n}a Pillar`, mat:"pillar", mode:"mesh", mesh:"pillar", fudge:16, pos:p, life:0.75, size:[4.8,6.4], rot3:[[-0.12,0.12],[0,TAU],[-0.12,0.12]], rotLife:[0,[-1,1],0],
      bursts:[[t,1]], sizeLife:[[0,0.1,0,7],[0.12,1],[0.55,0.95],[1,0.35,-1,0]], alpha:[[0,1],[0.65,0.95],[1,0]] },
    { name:`C${n}e SidePillars`, mat:"pillar", mode:"mesh", mesh:"pillar", fudge:17, pos:p, life:[0.5,0.65], size:[2.4,3.8], rot3:[[-0.25,0.25],[0,TAU],[-0.25,0.25]],
      shape:{type:"circle", r:3.0, th:0.35}, bursts:[[t + 0.03,2]], sizeLife:[[0,0.1,0,7],[0.14,1],[0.5,0.95],[1,0.3,-1,0]], alpha:[[0,1],[0.65,0.95],[1,0]] },
    { name:`C${n}b Geyser`, mat:"ink", fudge:14, pos:add(p, [0,1.6,0]), life:[0.5,0.7], size:[3.0,4.4], rot:[0,TAU], rotZ:[-1.2,1.2], bursts:[[t + 0.02,3]],
      shape:{type:"sphere", r:0.8, th:1}, speed:[1,3], drag:3, vel:{y:[1.5,3]}, flip:{x:4,y:4}, sizeLife:[[0,0.4,0,3],[0.4,1],[1,1.1]] },
    { name:`C${n}c Thorns`, mat:"spikeH", mode:"stretch", vs:0.06, ls:1.3, fudge:12, pos:p, life:[0.25,0.38], size:[0.3,0.5],
      shape:{type:"circle", r:2.4, th:1, y:0.1}, bursts:[[t,6]], vel:{y:[10,16]}, drag:3, gravity:2.2, alpha:[[0,1],[0.7,0.9],[1,0]] },
    { name:`C${n}d HitRing`, mat:"lineHot", mode:"mesh", mesh:"ringUnit", fudge:22, pos:add(p, [0,0.06,0]), life:0.4, size:2.8, rot3:[0,[0,TAU],0], bursts:[[t,1]],
      color:[[0,WL],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.5,0.6],[1,0]], sizeLife:[[0,0.1,0,4],[0.45,1],[1,1.05]] },
    { name:`C${n}f HitStar`, mat:"star", fudge:-8, pos:add(p, [0,1.2,0]), life:0.16, size:3.2, rot:[0,0.6], bursts:[[t,1]], sizeLife:[[0,0.4],[0.25,1],[1,0]] },
  );
});

const LINE_HOLD = [[0,0],[0.03,0.95],[0.35,0.7],[0.62,0.45],[1,0]];
const STAGE1 = [
  // ---- forbidden rune circle (outer): flashes with the chain, fades before stage 2
  { name:"O01 OuterLines", mat:"line", mode:"mesh", mesh:"outerLines", fudge:20, life:1.25, size:1, rot3:[0,0,0], rotLife:[0,0.1,0], bursts:[[0,1]],
    color:[[0,WL],[0.2,LIL],[1,VIO]], alpha:LINE_HOLD, sizeLife:[[0,1.04,0,-0.5],[0.08,1],[1,1]] },
  { name:"O02 OuterLinesHot", mat:"lineHot", mode:"mesh", mesh:"outerLines", fudge:19, life:0.3, size:1, rot3:[0,0,0], rotLife:[0,0.1,0], bursts:[[0,1]],
    color:[[0,WL],[1,LIL]], alpha:[[0,1],[1,0]], sizeLife:[[0,1.04,0,-0.5],[0.25,1],[1,1]] },
  { name:"O03 OuterLinesHot2", mat:"lineHot", mode:"mesh", mesh:"outerLines", fudge:19, life:0.3, size:1, rot3:[0,0.1 * 0.28,0], rotLife:[0,0.1,0], bursts:[[0.28,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0.75],[1,0]] },
  { name:"O04 OuterRunes", mat:"runes", mode:"mesh", mesh:"outerRunes", fudge:21, life:1.2, size:1, rotLife:[0,-0.16,0], bursts:[[0.02,1]],
    color:[[0,WL],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.04,1],[0.35,0.75],[0.65,0.4],[1,0]] },
  { name:"O05 OuterRim", mat:"fade", mode:"mesh", mesh:"rimUnit", fudge:8, pos:[0,0.02,0], life:0.6, size:RO, bursts:[[0.0,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.06,0.9],[0.4,0.5],[1,0]] },
  { name:"O06 InnerEdgeRim", mat:"fade", mode:"mesh", mesh:"rimUnit", fudge:8, pos:[0,0.02,0], life:0.5, size:RI + 0.45, bursts:[[0.0,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.06,0.6],[0.4,0.35],[1,0]], sizeLife:[[0,1],[1,1]] },
  // ---- donut-wide: ink stains, dark mist, rays, motes (staged, growing)
  { name:"O07 Stains", mat:"splat", mode:"mesh", mesh:"quad", fudge:45, life:[1.4,1.7], size:[4,6], rot3:[0,[0,TAU],0], shape:{type:"circle", r:12.2, th:0.6, y:0.012},
    bursts:[[0.02,3],[0.12,3],[0.22,3],[0.32,3],[0.42,4]], alpha:[[0,0],[0.04,0.8],[0.5,0.6],[1,0]], sizeLife:[[0,0.3,0,5],[0.1,1],[1,1.03]] },
  { name:"O08 DarkMist", mat:"mist", fudge:30, life:[1.1,1.5], size:[3.2,5], rot:[0,TAU], rotZ:[-0.4,0.4], shape:{type:"circle", r:12, th:0.55, y:1.2},
    bursts:[[0.08,8],[0.22,8],[0.36,10]], vel:{y:[0.4,1.0], space:"world"}, alpha:[[0,0],[0.15,0.6],[0.6,0.4],[1,0]], sizeLife:[[0,0.6],[1,1.25]] },
  { name:"O09 Rays", mat:"ray", mode:"stretch", vs:0.03, ls:4, fudge:-6, life:[0.25,0.4], size:[0.14,0.26], shape:{type:"circle", r:12, th:0.6, y:0.4},
    bursts:[[0.02,8],[0.14,10],[0.26,12],[0.38,14]], vel:{y:[14,22]}, color:[[0,LIL],[1,VIO]], alpha:[[0,1],[0.6,0.8],[1,0]] },
  { name:"O10 Motes", mat:"dot", fudge:-4, life:[1.0,1.6], size:[0.06,0.12], shape:{type:"circle", r:12.8, th:0.6, y:0.2},
    bursts:[[0.05,30],[0.25,30],[0.45,30]], vel:{y:[1,2.6]}, noise:{s:0.6, f:0.8, sp:0.6}, color:[[0,WL],[0.4,LIL],[1,VIO]], alpha:[[0,0],[0.12,1],[0.7,0.7],[1,0]] },
  { name:"O11 Shards", mat:"shard", mode:"stretch", vs:0.05, ls:1.1, fudge:11, life:[0.5,0.8], size:[0.16,0.3], shape:{type:"circle", r:RM, th:0.3, y:1.2},
    speed:[2,5], vel:{y:[5,9]}, gravity:1.5, bursts:[[0.05,12],[0.2,12],[0.35,14]], alpha:[[0,1],[0.8,1],[1,0]] },
  // ---- anticipation of stage 2: the inner circle tightens, light is drawn toward it
  { name:"A01 InnerTighten", mat:"lineHot", mode:"mesh", mesh:"ringUnit", fudge:18, pos:[0,0.05,0], life:TS - 0.55, size:RI, rotLife:[0,-2.2,0], bursts:[[0.55,1]],
    color:[[0,VIO],[1,WL]], alpha:[[0,0],[0.4,0.5],[0.95,1],[1,0]], sizeLife:[[0,1.5],[1,1,-0.3,0]] },
  { name:"A02 InnerRunesWake", mat:"runes", mode:"mesh", mesh:"innerRunes", fudge:21, life:TS - 0.6 + 0.02, size:1, rotLife:[0,1.0,0], bursts:[[0.6,1]],
    color:[[0,VIO],[1,LIL]], alpha:[[0,0],[1,0.45]] },
  { name:"A03 Inflow", mat:"dot", fudge:-5, life:[0.38,0.45], size:[0.07,0.12], shape:{type:"circle", r:RM, th:0.4, y:0.4},
    bursts:[[0.55,26],[0.7,26]], vel:{rad:[-11,-9], y:[0.2,1]}, color:[[0,VIO],[1,WL]], alpha:[[0,0],[0.3,1],[1,0.5]],
    trail:{life:0.12, mat:"streak", minDist:0.08, color:[[0,LIL],[1,VIO]]} },
];

// ---------------------------------------------------------------- stage 2: the inner disc erupts around the boss (t = TS)
const IN = [];
for (let k = 0; k < 6; k++) IN.push([Math.PI / 6 + k * Math.PI / 3, TS + 0.015 * k]);
const innerLayers = [];
IN.forEach(([a, t], i) => {
  const p = at(3.4, a);
  innerLayers.push(
    { name:`I${i}a Pillar`, mat:"pillar", mode:"mesh", mesh:"pillar", fudge:16, pos:p, life:0.8, size:[5.6,7], rot3:[[-0.12,0.12],[0,TAU],[-0.12,0.12]], rotLife:[0,[-1,1],0],
      bursts:[[t,1]], sizeLife:[[0,0.1,0,7],[0.12,1],[0.55,0.95],[1,0.35,-1,0]], alpha:[[0,1],[0.65,0.95],[1,0]] },
    { name:`I${i}b Thorns`, mat:"spikeH", mode:"stretch", vs:0.06, ls:1.3, fudge:12, pos:p, life:[0.25,0.38], size:[0.32,0.55],
      shape:{type:"circle", r:1.8, th:1, y:0.1}, bursts:[[t,6]], vel:{y:[11,17]}, drag:3, gravity:2.2, alpha:[[0,1],[0.7,0.9],[1,0]] },
    { name:`I${i}c Flash`, mat:"star", fudge:-8, pos:add(p, [0,1.3,0]), life:0.18, size:4, rot:[0,0.6], bursts:[[t,1]], sizeLife:[[0,0.4],[0.25,1],[1,0]] },
  );
});
const STAGE2 = [
  // ---- forbidden circle (inner): hot flash → held → fade
  { name:"N01 InnerLines", mat:"line", mode:"mesh", mesh:"innerLines", fudge:20, life:1.4, size:1, rot3:[0,0,0], rotLife:[0,-0.15,0], bursts:[[TS,1]],
    color:[[0,WL],[0.2,LIL],[1,VIO]], alpha:[[0,0],[0.02,1],[0.3,0.7],[0.6,0.4],[1,0]], sizeLife:[[0,1.05,0,-0.5],[0.06,1],[1,1]] },
  { name:"N02 InnerLinesHot", mat:"lineHot", mode:"mesh", mesh:"innerLines", fudge:19, life:0.3, size:1, rot3:[0,0,0], rotLife:[0,-0.15,0], bursts:[[TS,1]],
    color:[[0,WL],[1,LIL]], alpha:[[0,1],[1,0]], sizeLife:[[0,1.05,0,-0.5],[0.2,1],[1,1]] },
  { name:"N03 InnerRunes", mat:"runes", mode:"mesh", mesh:"innerRunes", fudge:21, life:1.4, size:1, rot3:[0,1.0 * (TS - 0.6),0], rotLife:[0,1.0,0], bursts:[[TS,1]],
    color:[[0,WL],[0.3,LIL],[1,VIO]], alpha:[[0,0.45],[0.03,1],[0.4,0.7],[1,0]] },
  { name:"N04 Sigil", mat:"sigil", mode:"mesh", mesh:"quad", fudge:23, pos:[0,0.03,0], life:1.4, size:2 * 4.7 / 0.95, rot3:[0,0,0], rotLife:[0,0.22,0], bursts:[[TS,1]],
    color:[[0,WL],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.03,0.9],[0.4,0.6],[1,0]], sizeLife:[[0,0.85,0,3],[0.08,1],[1,1.02]] },
  { name:"N05 ShockWash", mat:"shock", mode:"mesh", mesh:"quad", fudge:24, pos:[0,0.05,0], life:0.45, size:2 * RI / 0.9, bursts:[[TS,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0.65],[0.5,0.4],[1,0]], sizeLife:[[0,0.1,0,4],[0.55,1],[1,1.01]] },
  { name:"N06 EdgeRim", mat:"fade", mode:"mesh", mesh:"rimUnit", fudge:8, pos:[0,0.02,0], life:0.6, size:RI, bursts:[[TS + 0.12,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.1,1],[0.45,0.55],[1,0]], sizeLife:[[0,1],[1,1]] },
  { name:"N07 Crown", mat:"spike", mode:"mesh", mesh:"crownIn", fudge:15, life:0.8, size:1, rot3:[0,[0,TAU],0], bursts:[[TS + 0.03,1]],
    sizeLife:[[0,0.4,0,5],[0.15,1],[0.55,1],[1,0.4,-1,0]], alpha:[[0,1],[0.7,1],[1,0]] },
  { name:"N08 InkBurst", mat:"ink", fudge:14, life:[0.55,0.75], size:[3.2,4.6], rot:[0,TAU], rotZ:[-1.2,1.2], shape:{type:"circle", r:4.3, th:0.7, y:1.2},
    bursts:[[TS,8],[TS + 0.05,6]], speed:[1,2.5], drag:3, vel:{y:[1,2.5]}, flip:{x:4,y:4}, sizeLife:[[0,0.4,0,3],[0.4,1],[1,1.1]] },
  { name:"N09 Billow", mat:"mist", fudge:30, life:[0.9,1.3], size:[3,4.5], rot:[0,TAU], rotZ:[-0.6,0.6], shape:{type:"circle", r:4.0, th:0.7, y:1.4},
    bursts:[[TS + 0.03,10]], speed:[1,2], vel:{y:[0.6,1.2], space:"world"}, alpha:[[0,0],[0.12,0.8],[0.6,0.5],[1,0]], sizeLife:[[0,0.5,0,2],[1,1.3]] },
  { name:"N10 Rays", mat:"ray", mode:"stretch", vs:0.03, ls:4.5, fudge:-6, life:[0.25,0.4], size:[0.16,0.3], shape:{type:"circle", r:4.6, th:0.6, y:0.4},
    bursts:[[TS,14],[TS + 0.07,10]], vel:{y:[16,26]}, color:[[0,LIL],[1,VIO]], alpha:[[0,1],[0.6,0.8],[1,0]] },
  { name:"N11 ScratchFrags", mat:"scratch", fudge:-4, life:[0.5,0.75], size:[0.6,1.0], rot:[0,TAU], rotZ:[-8,8], shape:{type:"circle", r:1.8, th:0.3, y:2.0},
    speed:[7,10], drag:2.2, vel:{y:[1,3]}, gravity:0.4, bursts:[[TS + 0.02,16]], color:[[0,WL],[0.5,LIL],[1,VIO]], alpha:[[0,1],[0.6,0.9],[1,0]], sizeLife:[[0,1],[1,0.5]] },
  { name:"N12 SlashShards", mat:"shard", mode:"stretch", vs:0.05, ls:1.1, fudge:11, life:[0.55,0.85], size:[0.18,0.3], shape:{type:"circle", r:2, th:0.5, y:1.6},
    speed:[8,13], drag:1.6, vel:{y:[3,6]}, gravity:1.4, bursts:[[TS + 0.02,24]], alpha:[[0,1],[0.8,1],[1,0]] },
  { name:"N13 Arcs", mat:"arc", mode:"mesh", mesh:"arc", fudge:-2, life:[0.3,0.42], size:[3.0,4.6], shape:{type:"box", sy:2.4, off:[0,2.2,0]},
    rot3:[[-0.25,0.25],[0,TAU],[-0.25,0.25]], rotLife:[0,[7,10],0], bursts:[[TS + 0.02,3],[TS + 0.1,3]], color:[[0,WL],[1,VIO]],
    alpha:[[0,1],[0.6,0.8],[1,0]], sizeLife:[[0,0.8],[1,1.12]] },
  { name:"N14 GroundScratch", mat:"scratch", mode:"mesh", mesh:"quad", fudge:19, pos:[0,0.035,0], life:1.2, size:7.5, rot3:[0,0.5,0], bursts:[[TS + 0.02,1]],
    color:[[0,WL],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.04,0.9],[0.45,0.6],[1,0]], sizeLife:[[0,0.5,0,4],[0.1,1],[1,1]] },
  { name:"N15 Stains", mat:"splat", mode:"mesh", mesh:"quad", fudge:45, life:[1.3,1.5], size:[3,4.5], rot3:[0,[0,TAU],0], shape:{type:"circle", r:4.4, th:0.75, y:0.013},
    bursts:[[TS,5]], alpha:[[0,0],[0.04,0.8],[0.5,0.6],[1,0]], sizeLife:[[0,0.3,0,5],[0.1,1],[1,1.03]] },
  { name:"N16 Motes", mat:"dot", fudge:-4, life:[1.0,1.5], size:[0.06,0.12], shape:{type:"circle", r:RI, th:1, y:0.2},
    bursts:[[TS + 0.05,40],[TS + 0.3,20]], vel:{y:[1,2.6]}, noise:{s:0.6, f:0.8, sp:0.6}, color:[[0,WL],[0.4,LIL],[1,VIO]], alpha:[[0,0],[0.12,1],[0.7,0.7],[1,0]] },
  { name:"N17 Sparks", mat:"streak", mode:"stretch", vs:0.05, ls:1, fudge:-5, life:[0.35,0.6], size:[0.05,0.09], shape:{type:"circle", r:3.4, th:0.4, y:1},
    speed:[8,14], drag:2, vel:{y:[3,7]}, gravity:0.8, bursts:[[TS,36]], color:[[0,WL],[0.5,LIL],[1,VIO]], alpha:[[0,1],[1,0]] },
];

const MATS = {
  line:{tex:"T_Line", tint:[1.7,1.4,2.6], add:true},
  lineHot:{tex:"T_Line", tint:[2.3,1.9,3.0], add:true},
  runes:{tex:"T_RuneStrip", tint:[1.8,1.45,2.6], add:true},
  sigil:{tex:"T_ForbidSigil", tint:[1.7,1.4,2.5], add:true},
  scratch:{tex:"T_Scratch", tint:[2.1,1.7,2.9], add:true},
  star:{tex:"T_Star", tint:[2.0,1.7,2.6], add:true},
  dot:{tex:"T_Dot", tint:[2.0,1.6,2.7], add:true},
  streak:{tex:"T_Streak", tint:[2.0,1.6,2.8], add:true},
  ray:{tex:"T_Streak", tint:[1.5,1.1,2.4], add:true},
  arc:{tex:"T_Streak", tint:[2.0,1.6,2.8], add:true},
  shock:{tex:"T_Shock", tint:[1.4,1.1,2.2], add:true},
  fade:{tex:"T_Fade", tint:[1.5,1.1,2.4], add:true},
  ink:{tex:"T_InkSplash_Sheet", tint:[2.2,1.9,2.6], add:false},
  mist:{tex:"T_InkMist", tint:[1,1,1], add:false},
  spike:{tex:"T_InkSpike", tint:[2.2,1.9,2.6], add:false},
  spikeH:{tex:"T_InkSpikeH", tint:[2.2,1.9,2.6], add:false},
  pillar:{tex:"T_Pillar", tint:[2.0,1.7,2.6], add:false},
  shard:{tex:"T_SlashShard", tint:[2.2,1.9,2.6], add:false},
  splat:{tex:"T_InkSplat", tint:[1.15,1.0,1.45], add:false},
};

const BOSS = [{ c:[0,4.3,0], e:[1.2,3.3,0.9], shade:[0.09,0.06,0.13] }];
const PLAYERS = [[0,0,3.2,180],[-2.6,0,-1.8,60],[7.5,0,4.5,240],[-8.5,0,6,120],[2,0,-10.5,10],[-10.8,0,-3,80]];
const base = { from:[0,0,0], to:[0,0,0], normal:[0,1,0], flight:0.01, anchor:[0,0,0], targets:PLAYERS };
const SCENE = {
  name: "MordianForbiddenRing",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.7, tint:[0.9,0.85,1.0] }, tonemapping:"neutral",
  systems:[ { id:"forbidden_ring", role:"static", loop:false, duration:1.2, layers:[...STAGE1, ...chainLayers, ...STAGE2, ...innerLayers] } ],
  demo:{
    interval:3.2, bg:[0.13,0.12,0.16], floor:[0.10,0.095,0.12], grid:[0.07,0.065,0.09], dummy:{ height:1.8 }, props:BOSS,
    scenarios:{
      wide:  Object.assign({ label:"전체", camera:{ target:[0,1.5,0], dist:28, yaw:-60, pitch:30 } }, base),
      low:   Object.assign({ label:"지면 시점 (도넛 안)", camera:{ target:[0,2.5,0], dist:20, yaw:-110, pitch:9 } }, base),
      top:   Object.assign({ label:"범위 (위)", camera:{ target:[0,0,0], dist:36, yaw:-90, pitch:72 } }, base),
      inner: Object.assign({ label:"안쪽 원 근접", camera:{ target:[0,2,0], dist:13, yaw:-75, pitch:16 } }, base),
    },
  },
};
