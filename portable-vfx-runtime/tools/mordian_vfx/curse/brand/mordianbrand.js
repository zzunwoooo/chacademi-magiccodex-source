// MordianBrand — 모르디안의 원령 "개인 낙인 폭발".
// mark  (attached, the BRANDED PLAYER: feet = origin, +Z = facing; started by the mod at boss `brand` 0.4): a violet rune seal with the
//        three-line scratch mark turns above the head, a small rune circle turns at the feet; both speed up and brighten for 3.0 s.
// burst (impact, at the player's position at mark + 3.0 s, local +Y = up): black ink explosion + violet shock ring to r = 4 m,
//        ink thorns rising, scratch fragments flying out.
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

const footStar = polygram(6, 2, 0.6, 0.016, 0.02, Math.PI / 6);
var EXTRA_MESHES = {
  // feet: rune circle r 0.75 (line work)
  footLines: merge([ringStrip(0.76, 0.035, 72, 0.02), ringStrip(0.62, 0.018, 64, 0.02), ringStrip(0.2, 0.014, 32, 0.02), ...footStar.parts,
                    ...footStar.P.map(p => ringStrip(0.045, 0.014, 16, 0.02, p[0], p[1]))]),
  footRunes: runeBand(0.69, 0.095, 8, 0.025),
  // burst: unit ring line work (size = radius in m)
  ringUnit: merge([ringStrip(1.0, 0.022, 120, 0), ringStrip(0.955, 0.009, 120, 0)]),
  runeUnit: runeBand(0.9, 0.07, 16, 0),
  // boundary rim wall at r = 1 (V up: bright bottom), height 0.18 (size 4 → 0.72 m)
  rimUnit: ribbon(120, s => { const d = dXZ(-s * TAU); return [d, add(d, [0, 0.18, 0])]; }),
  // fast arc stroke (XZ, U along: head at U=1 leads a +Y spin)
  arc: ribbon(48, s => { const d = dXZ(-s * Math.PI * 0.75); return [mul(d, 0.94), mul(d, 1.06)]; }),
  crownIn: crown(7, 0.35, 0.34, 2.4, 0.22, 1.7),
  crownOut: crown(11, 1.5, 0.28, 1.35, 0.35, 4.1),
  geyser: merge([spike(0, 0, 0.7, 1, 0, 3)]),
  quadV: { verts:[[-0.5,-0.5,0],[0.5,-0.5,0],[0.5,0.5,0],[-0.5,0.5,0]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3] },
};

// ---------------------------------------------------------------- MARK (3.0 s)
const T = 3.0, HY = 2.55;          // seal height above the feet (player 1.8 m)
// three speed phases of the turning seal; each copy starts at the previous one's angle → no jump at the cross-fade
const W1 = 0.9, W2 = 2.2, W3 = 5.0, P1 = 1.0, P2 = 2.0, XF = 0.12;
const A2 = W1 * P1, A3 = A2 + W2 * (P2 - P1);
const FW1 = 0.6, FW2 = 1.5, FW3 = 3.6, FA2 = FW1 * P1, FA3 = FA2 + FW2 * (P2 - P1);
const fadeIO = (life, a, inT = XF) => [[0,0],[inT / life, a],[1 - XF / life, a],[1,0]];
const MARK = [
  // ---- head seal: dark ink backing → violet glow → turning rune ring (3 speed phases) → counter-turning scratch emblem
  { name:"M01 SealInk", mat:"mist", fudge:30, pos:[0,HY,0], life:T, size:1.7, bursts:[[0,1]], rot:[0,TAU], rotZ:0.3,
    alpha:[[0,0],[0.06,0.5],[0.85,0.62],[1,0]], sizeLife:[[0,0.4,0,3],[0.08,1],[0.85,1.05],[1,0.6]] },
  { name:"M02 SealGlow", mat:"glow", fudge:5, pos:[0,HY,0], life:T, size:1.55, bursts:[[0,1]],
    color:[[0,DV],[0.6,VIO],[1,LIL]], alpha:[[0,0],[0.05,0.18],[0.6,0.26],[0.92,0.38],[1,0]], sizeLife:[[0,0.6],[0.1,1],[0.9,1.2],[1,0.7]] },
  { name:"M03 SealRingSlow", mat:"sealRing", fudge:2, pos:[0,HY,0], life:P1 + XF, size:1.25, rot:0, rotZ:W1, bursts:[[0,1]],
    color:[[0,LIL],[1,LIL]], alpha:[[0,0],[0.1,0.8],[1 - XF / (P1 + XF), 0.8],[1,0]], sizeLife:[[0,1.6,0,-3],[0.15,1],[1,1]] },
  { name:"M04 SealRingMid", mat:"sealRing", fudge:2, pos:[0,HY,0], life:P2 - P1 + XF, size:1.29, rot:A2, rotZ:W2, bursts:[[P1,1]],
    color:[[0,LIL],[1,WL]], alpha:fadeIO(P2 - P1 + XF, 0.9) },
  { name:"M05 SealRingFast", mat:"sealRing", fudge:2, pos:[0,HY,0], life:T - P2, size:1.33, rot:A3, rotZ:W3, bursts:[[P2,1]],
    color:[[0,WL],[1,WL]], alpha:[[0,0],[XF / (T - P2), 1],[0.85,1],[1,0]], sizeLife:[[0,1],[0.6,1.04],[0.88,1.1],[1,0.45]] },
  { name:"M06 SealCore", mat:"sealCore", fudge:1, pos:[0,HY,0], life:T, size:0.82, bursts:[[0,1]], rotZ:-0.35,
    color:[[0,LIL],[0.6,LIL],[1,WL]], alpha:[[0,0],[0.04,1],[0.92,1],[1,0]], sizeLife:[[0,2.2,0,-6],[0.06,1],[0.85,1.08],[1,0.4]] },
  { name:"M07 SealHotCore", mat:"sealCore", fudge:0, pos:[0,HY,0], life:T - 1.6, size:0.82, bursts:[[1.6,1]], rot:-0.35 * 1.6, rotZ:-0.35,
    color:[[0,VIO],[1,WL]], alpha:[[0,0],[0.5,0.35],[0.88,0.8],[1,0]], sizeLife:[[0,1],[0.82,1.08],[1,0.4]] },
  // ---- designation strike (t = 0): converging ring → scratch pops in big and settles, small flash
  { name:"M08 InRing", mat:"sealRing", fudge:-1, pos:[0,HY,0], life:0.22, size:2.6, rot:0.5, rotZ:-4, bursts:[[0,1]],
    color:[[0,VIO],[1,LIL]], alpha:[[0,0],[0.3,0.7],[1,0]], sizeLife:[[0,1],[1,0.38,-1.2,0]] },
  { name:"M09 BrandScratch", mat:"scratch", fudge:-2, pos:[0,HY,0], life:0.45, size:1.5, bursts:[[0.14,1]],
    color:[[0,WL],[1,VIO]], alpha:[[0,1],[0.4,0.8],[1,0]], sizeLife:[[0,0.4,0,6],[0.2,1],[1,0.55]] },
  { name:"M10 BrandFlash", mat:"star", fudge:-3, pos:[0,HY,0], life:0.2, size:1.5, rot:[0,0.6], bursts:[[0.14,1]],
    sizeLife:[[0,0.3],[0.25,1],[1,0]] },
  { name:"M11 BrandStreaks", mat:"streak", mode:"stretch", vs:0.05, ls:1.6, fudge:-3, pos:[0,HY,0], life:[0.18,0.3], size:[0.03,0.06],
    shape:{type:"sphere", r:0.1, th:1}, speed:[4,7], drag:6, bursts:[[0.14,10]], color:[[0,WL],[1,VIO]], alpha:[[0,1],[1,0]] },
  // ---- orbiting scratch glyphs around the seal: 3 speed phases
  { name:"M12 OrbitGlyphs1", mat:"scratch", fudge:-1, pos:[0,HY,0], life:P1 + 0.1, size:[0.16,0.2], shape:{type:"circle", r:0.9, th:0},
    bursts:[[0.12,3]], vel:{orb:W1 * 1.6}, rotZ:[-1,1], color:[[0,LIL],[1,LIL]], alpha:[[0,0],[0.15,0.85],[0.85,0.85],[1,0]] },
  { name:"M13 OrbitGlyphs2", mat:"scratch", fudge:-1, pos:[0,HY,0], life:P2 - P1 + 0.1, size:[0.17,0.22], shape:{type:"circle", r:0.88, th:0},
    bursts:[[P1,4]], vel:{orb:W2 * 1.4}, rotZ:[-2,2], color:[[0,LIL],[1,WL]], alpha:[[0,0],[0.1,0.9],[0.85,0.9],[1,0]] },
  { name:"M14 OrbitGlyphs3", mat:"scratch", fudge:-1, pos:[0,HY,0], life:T - P2, size:[0.18,0.24], shape:{type:"circle", r:0.86, th:0},
    bursts:[[P2,5]], vel:{orb:W3 * 1.1, rad:[-0.25,-0.2]}, rotZ:[-3,3], color:[[0,WL],[1,WL]], alpha:[[0,0],[0.1,1],[0.85,1],[1,0]] },
  // ---- heartbeat pulses out of the seal, closer and closer
  { name:"M15 Pulse", mat:"sealRing", fudge:3, pos:[0,HY,0], life:0.3, size:1.3, rot:[0,TAU], rotZ:1.5,
    bursts:[[0.55,1],[1.2,1],[1.7,1],[2.05,1],[2.3,1],[2.48,1],[2.6,1],[2.68,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0.55],[1,0]], sizeLife:[[0,1],[1,1.75,1.2,0]] },
  // ---- ink drips falling from the seal (dark accents)
  { name:"M16 InkDrips", mat:"shard", mode:"stretch", vs:0.06, ls:1.2, fudge:12, world:true, pos:[0,HY - 0.25,0], life:[0.45,0.7], size:[0.05,0.09],
    shape:{type:"circle", r:0.35, th:0.6}, gravity:0.5, vel:{y:[-0.6,-0.2]}, bursts:[[0.3,2],[0.8,2],[1.2,3],[1.6,3],[2.0,4],[2.3,4],[2.6,5]],
    alpha:[[0,0],[0.15,0.9],[0.8,0.9],[1,0]] },
  // ---- feet: rune circle (line work + rune band) turning in 3 speed phases, ink pool + glow below, scratch in the middle
  { name:"F01 InkPool", mat:"splat", mode:"mesh", mesh:"quad", fudge:40, pos:[0,0.012,0], life:T, size:1.9, rot3:[0,0.6,0], bursts:[[0,1]],
    alpha:[[0,0],[0.08,0.6],[0.9,0.7],[1,0]], sizeLife:[[0,0.3,0,3],[0.1,1],[1,1.05]] },
  { name:"F02 FootGlow", mat:"glow", mode:"mesh", mesh:"quad", fudge:35, pos:[0,0.016,0], life:T, size:2.2, bursts:[[0,1]],
    color:[[0,DV],[0.7,VIO],[1,LIL]], alpha:[[0,0],[0.08,0.2],[0.7,0.3],[0.95,0.42],[1,0]] },
  { name:"F03 FootLines1", mat:"line", mode:"mesh", mesh:"footLines", fudge:20, life:P1 + XF, size:1, rot3:[0,0,0], rotLife:[0,FW1,0], bursts:[[0,1]],
    color:[[0,LIL],[1,LIL]], alpha:[[0,0],[0.1,0.8],[1 - XF / (P1 + XF),0.8],[1,0]], sizeLife:[[0,0.3,0,4],[0.18,1],[1,1]] },
  { name:"F04 FootLines2", mat:"line", mode:"mesh", mesh:"footLines", fudge:20, life:P2 - P1 + XF, size:1, rot3:[0,FA2,0], rotLife:[0,FW2,0], bursts:[[P1,1]],
    color:[[0,LIL],[1,WL]], alpha:fadeIO(P2 - P1 + XF, 0.9) },
  { name:"F05 FootLines3", mat:"line", mode:"mesh", mesh:"footLines", fudge:20, life:T - P2, size:1, rot3:[0,FA3,0], rotLife:[0,FW3,0], bursts:[[P2,1]],
    color:[[0,WL],[1,WL]], alpha:[[0,0],[XF / (T - P2),1],[0.85,1],[1,0]], sizeLife:[[0,1],[0.85,1.04],[1,0.5]] },
  { name:"F06 FootRunes", mat:"runes", mode:"mesh", mesh:"footRunes", fudge:21, life:T, size:1, rotLife:[0,-0.8,0], bursts:[[0.1,1]],
    color:[[0,VIO],[0.6,LIL],[1,WL]], alpha:[[0,0],[0.1,0.8],[0.9,1],[1,0]], sizeLife:[[0,1.3,0,-2],[0.1,1],[0.9,1],[1,0.6]] },
  { name:"F07 FootScratch", mat:"scratch", mode:"mesh", mesh:"quad", fudge:19, pos:[0,0.03,0], life:T - 0.2, size:0.55, rot3:[0,0.4,0], bursts:[[0.2,1]],
    color:[[0,VIO],[1,WL]], alpha:[[0,0],[0.1,0.7],[0.9,1],[1,0]] },
  // ---- rising violet motes + dark wisps: staged, more and more
  { name:"F08 Motes", mat:"dot", fudge:-4, life:[0.8,1.2], size:[0.04,0.08], shape:{type:"circle", r:0.75, th:0.3, y:0.1},
    bursts:[[0,4],[0.4,4],[0.8,5],[1.2,6],[1.6,7],[1.9,8],[2.2,9],[2.45,10],[2.65,10]],
    vel:{y:[0.9,1.8], orb:[0.6,1.4]}, noise:{s:0.3, f:1.2, sp:0.7}, color:[[0,WL],[0.5,LIL],[1,VIO]], alpha:[[0,0],[0.15,1],[0.75,0.8],[1,0]] },
  { name:"F09 Wisps", mat:"wisp", mode:"stretch", vs:0.25, ls:2.2, fudge:25, life:[0.7,1.0], size:[0.12,0.2], shape:{type:"circle", r:0.6, th:0.4, y:0.05},
    bursts:[[0.1,2],[0.6,2],[1.1,3],[1.5,3],[1.9,4],[2.3,4]], vel:{y:[1.1,1.8], orb:[0.8,1.2]}, alpha:[[0,0],[0.2,0.75],[0.7,0.6],[1,0]],
    sizeLife:[[0,0.6],[0.4,1],[1,0.8]] },
  // ---- last 0.45 s: everything is pulled into the player (anticipation of the burst)
  { name:"F10 Contract", mat:"lineHot", mode:"mesh", mesh:"ringUnit", fudge:18, pos:[0,0.04,0], life:0.45, size:1.7, rot3:[0,0.3,0], rotLife:[0,-3,0], bursts:[[T - 0.45,1]],
    color:[[0,VIO],[1,WL]], alpha:[[0,0],[0.3,0.7],[0.92,1],[1,0]], sizeLife:[[0,1],[1,0.25,-1.4,0]] },
  { name:"F10b ContractRunes", mat:"runes", mode:"mesh", mesh:"runeUnit", fudge:18, pos:[0,0.045,0], life:0.45, size:1.5, rotLife:[0,4,0], bursts:[[T - 0.45,1]],
    color:[[0,VIO],[1,LIL]], alpha:[[0,0],[0.3,0.6],[0.92,0.9],[1,0]], sizeLife:[[0,1],[1,0.3,-1.4,0]] },
  { name:"F11 Inflow", mat:"dot", fudge:-5, life:[0.32,0.42], size:[0.05,0.09], shape:{type:"circle", r:2.0, th:0.3, y:0.3},
    bursts:[[T - 0.45,14],[T - 0.3,14]], vel:{rad:[-5.5,-4.5], y:[0.6,1.6]}, color:[[0,VIO],[1,WL]], alpha:[[0,0],[0.3,1],[1,0.6]],
    trail:{life:0.12, mat:"streak", minDist:0.04, color:[[0,LIL],[1,VIO]]} },
  { name:"F12 SealFlare", mat:"star", fudge:-3, pos:[0,HY,0], life:0.32, size:1.6, rot:[0,0.5], rotZ:2, bursts:[[T - 0.32,1]],
    sizeLife:[[0,0.2],[0.75,1],[1,0.3]], alpha:[[0,0.4],[0.75,1],[1,0]] },
];

// ---------------------------------------------------------------- BURST (impact at the branded player's feet, ~1.8 s)
const R = 4.0;
const BURST = [
  // ---- flash (short, small)
  { name:"B01 Flash", mat:"star", fudge:-10, pos:[0,1.0,0], life:0.22, size:3.2, rot:[0,0.6], bursts:[[0,1]], sizeLife:[[0,0.4],[0.2,1],[1,0]] },
  { name:"B02 FlashGlow", mat:"glow", fudge:-9, pos:[0,1.0,0], life:0.28, size:2.6, bursts:[[0,1]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.5],[1,0]],
    sizeLife:[[0,0.6],[1,1.2]] },
  // ---- black ink explosion (dark body, lilac rim)
  { name:"B03 InkCore", mat:"ink", fudge:14, pos:[0,1.0,0], life:[0.55,0.75], size:[3.0,4.2], rot:[0,TAU], rotZ:[-1.5,1.5], bursts:[[0.0,5],[0.05,4]],
    shape:{type:"sphere", r:0.4, th:1}, speed:[1.0,2.5], drag:4, vel:{y:[0.3,0.8], space:"world"}, flip:{x:4,y:4}, sizeLife:[[0,0.5,0,3],[0.4,1],[1,1.1]] },
  { name:"B03b InkBillow", mat:"mist", fudge:28, pos:[0,1.2,0], life:[0.7,0.95], size:[2.6,3.6], rot:[0,TAU], rotZ:[-0.8,0.8], bursts:[[0.03,6]],
    shape:{type:"sphere", r:0.5, th:1}, speed:[2,3.5], drag:3, vel:{y:[0.6,1.2], space:"world"}, alpha:[[0,0],[0.1,0.85],[0.6,0.55],[1,0]], sizeLife:[[0,0.5,0,2],[1,1.3]] },
  { name:"B04 InkSpray", mat:"ink", fudge:13, pos:[0,0.6,0], life:[0.5,0.7], size:[1.3,2.0], rot:[0,TAU], bursts:[[0.02,14]],
    shape:{type:"circle", r:0.6, th:0.5}, speed:[5,8], drag:4.5, vel:{y:[1,2.5]}, flip:{x:4,y:4}, sizeLife:[[0,0.5],[0.5,1],[1,1]] },
  { name:"B05 Geyser", mat:"pillar", mode:"mesh", mesh:"geyser", fudge:16, life:0.55, size:3.0, rot3:[0,[0,TAU],0], rotLife:[0,1.5,0], bursts:[[0,1]],
    sizeLife:[[0,0.15,0,6],[0.15,1],[0.6,0.95],[1,0.3]], alpha:[[0,1],[0.6,0.9],[1,0]] },
  // ---- violet shock: line ring + wash expanding to r = 4, rune ring echo, boundary flare
  { name:"B06 ShockWash", mat:"shock", mode:"mesh", mesh:"quad", fudge:22, pos:[0,0.05,0], life:0.5, size:2 * R / 0.9, bursts:[[0.0,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0.6],[0.5,0.4],[1,0]], sizeLife:[[0,0.06,0,4],[0.5,1],[1,1.02]] },
  { name:"B07 ShockLine", mat:"lineHot", mode:"mesh", mesh:"ringUnit", fudge:20, pos:[0,0.07,0], life:0.75, size:R, rot3:[0,[0,TAU],0], bursts:[[0.0,1]],
    color:[[0,WL],[0.4,LIL],[1,VIO]], alpha:[[0,1],[0.45,1],[1,0]], sizeLife:[[0,0.05,0,4.5],[0.38,1],[1,1.01]] },
  { name:"B08 ShockLine2", mat:"line", mode:"mesh", mesh:"ringUnit", fudge:20, pos:[0,0.08,0], life:0.6, size:R * 0.97, rot3:[0,[0,TAU],0], bursts:[[0.07,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0.9],[1,0]], sizeLife:[[0,0.05,0,3.5],[0.5,1],[1,1]] },
  { name:"B09 RuneEcho", mat:"runes", mode:"mesh", mesh:"runeUnit", fudge:21, pos:[0,0.06,0], life:0.9, size:R, rot3:[0,[0,TAU],0], rotLife:[0,-1.2,0], bursts:[[0.05,1]],
    color:[[0,WL],[0.4,LIL],[1,VIO]], alpha:[[0,0],[0.08,1],[0.55,0.75],[1,0]], sizeLife:[[0,0.1,0,3.5],[0.3,1],[1,1.02]] },
  { name:"B10 RimFlare", mat:"fade", mode:"mesh", mesh:"rimUnit", fudge:8, pos:[0,0.02,0], life:0.55, size:R, rot3:[0,0,0], bursts:[[0.17,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.12,0.9],[0.5,0.55],[1,0]], sizeLife:[[0,1],[0.12,1.03],[1,1.0]] },
  { name:"B11 RimThorns", mat:"spikeH", mode:"stretch", vs:0.05, ls:1.3, fudge:9, life:[0.22,0.32], size:[0.2,0.32], shape:{type:"circle", r:R, th:0, y:0.05},
    bursts:[[0.17,26]], vel:{y:[5,8]}, drag:6, alpha:[[0,1],[0.7,0.9],[1,0]] },
  // ---- rising ink thorns (mesh crowns + flying stretched spikes)
  { name:"B12 CrownIn", mat:"spike", mode:"mesh", mesh:"crownIn", fudge:15, life:0.75, size:1, rot3:[0,[0,TAU],0], bursts:[[0.0,1]],
    sizeLife:[[0,0.1,0,7],[0.14,1],[0.55,1],[1,0.25,-1,0]], alpha:[[0,1],[0.7,1],[1,0]] },
  { name:"B13 CrownOut", mat:"spike", mode:"mesh", mesh:"crownOut", fudge:15, life:0.7, size:1, rot3:[0,[0,TAU],0], bursts:[[0.06,1]],
    sizeLife:[[0,0.35,0,5],[0.18,1.1],[0.55,1.05],[1,0.4,-1,0]], alpha:[[0,1],[0.7,1],[1,0]] },
  { name:"B14 Thorns", mat:"spikeH", mode:"stretch", vs:0.06, ls:1.2, fudge:12, life:[0.25,0.38], size:[0.22,0.4], shape:{type:"circle", r:2.8, th:0.85, y:0.1},
    bursts:[[0.03,9],[0.1,6]], vel:{y:[9,14]}, drag:3, gravity:2.2, alpha:[[0,1],[0.7,0.9],[1,0]] },
  // ---- scratch fragments + slash shards flying out, god rays, spinning arcs, sparks
  { name:"B15 ScratchFrags", mat:"scratch", fudge:-4, pos:[0,1.0,0], life:[0.45,0.7], size:[0.45,0.8], rot:[0,TAU], rotZ:[-9,9],
    shape:{type:"sphere", r:0.3, th:1}, speed:[7,11], drag:3.5, gravity:0.4, bursts:[[0.02,12]], color:[[0,WL],[0.5,LIL],[1,VIO]],
    alpha:[[0,1],[0.6,0.9],[1,0]], sizeLife:[[0,1],[1,0.5]] },
  { name:"B16 SlashShards", mat:"shard", mode:"stretch", vs:0.05, ls:1.1, fudge:11, pos:[0,0.9,0], life:[0.55,0.9], size:[0.14,0.26],
    shape:{type:"sphere", r:0.3, th:1}, speed:[8,14], drag:2.2, gravity:1.2, bursts:[[0.02,22]], alpha:[[0,1],[0.8,1],[1,0]] },
  { name:"B17 GodRays", mat:"ray", mode:"stretch", vs:0.03, ls:4, fudge:-6, pos:[0,0.4,0], life:[0.22,0.36], size:[0.1,0.2],
    shape:{type:"sphere", r:0.4, th:1}, speed:[1,3], vel:{y:[12,20]}, bursts:[[0.0,10],[0.06,8]], color:[[0,LIL],[1,VIO]], alpha:[[0,1],[0.6,0.8],[1,0]] },
  { name:"B18 Arcs", mat:"arc", mode:"mesh", mesh:"arc", fudge:-2, life:[0.26,0.36], size:[1.3,2.6], shape:{type:"box", sy:1.2, off:[0,1.0,0]},
    rot3:[[-0.35,0.35],[0,TAU],[-0.35,0.35]], rotLife:[0,[11,15],0], bursts:[[0.02,3],[0.09,3]], color:[[0,WL],[1,VIO]],
    alpha:[[0,1],[0.6,0.8],[1,0]], sizeLife:[[0,0.7],[1,1.2]] },
  { name:"B19 Sparks", mat:"streak", mode:"stretch", vs:0.05, ls:1, fudge:-5, pos:[0,0.9,0], life:[0.35,0.6], size:[0.04,0.08],
    shape:{type:"sphere", r:0.3, th:1}, speed:[10,17], drag:2, gravity:0.8, bursts:[[0.0,30]], color:[[0,WL],[0.5,LIL],[1,VIO]], alpha:[[0,1],[1,0]] },
  // ---- the ground answers: ink splat + glowing scratch scars, dark mist rolling out, motes rising
  { name:"B20 GroundSplat", mat:"splat", mode:"mesh", mesh:"quad", fudge:45, pos:[0,0.015,0], life:1.8, size:6.2, rot3:[0,[0,TAU],0], bursts:[[0.0,1]],
    alpha:[[0,0],[0.03,0.9],[0.6,0.75],[1,0]], sizeLife:[[0,0.3,0,5],[0.08,1],[1,1.03]] },
  { name:"B21 GroundScratch", mat:"scratch", mode:"mesh", mesh:"quad", fudge:19, pos:[0,0.03,0], life:1.1, size:3.6, rot3:[0,[0,TAU],0], bursts:[[0.03,1]],
    color:[[0,WL],[0.3,LIL],[1,VIO]], alpha:[[0,0],[0.05,1],[0.45,0.7],[1,0]], sizeLife:[[0,0.5,0,4],[0.12,1],[1,1]] },
  { name:"B22 InkMist", mat:"mist", fudge:30, life:[0.9,1.3], size:[1.8,2.8], rot:[0,TAU], rotZ:[-0.6,0.6], shape:{type:"circle", r:0.6, th:1, y:0.6},
    speed:[5,8], drag:3, bursts:[[0.04,12]], vel:{y:[0.3,0.8], space:"world"}, alpha:[[0,0],[0.15,0.6],[0.6,0.4],[1,0]], sizeLife:[[0,0.5],[1,1.3]] },
  { name:"B23 Motes", mat:"dot", fudge:-4, life:[1.0,1.6], size:[0.05,0.1], shape:{type:"circle", r:R, th:1, y:0.1},
    bursts:[[0.08,26],[0.3,18],[0.6,10]], vel:{y:[0.8,2.2]}, noise:{s:0.5, f:1, sp:0.6}, color:[[0,WL],[0.4,LIL],[1,VIO]], alpha:[[0,0],[0.12,1],[0.7,0.7],[1,0]] },
  { name:"B24 Wisps", mat:"wisp", mode:"stretch", vs:0.25, ls:2.4, fudge:26, life:[0.8,1.1], size:[0.18,0.3], shape:{type:"circle", r:R * 0.7, th:1, y:0.05},
    bursts:[[0.15,10],[0.35,6]], vel:{y:[1.2,2.0]}, alpha:[[0,0],[0.2,0.7],[0.7,0.5],[1,0]] },
];

const MATS = {
  line:{tex:"T_Line", tint:[1.7,1.4,2.6], add:true},
  lineHot:{tex:"T_Line", tint:[2.3,1.9,3.0], add:true},
  runes:{tex:"T_RuneStrip", tint:[1.8,1.45,2.6], add:true},
  sealRing:{tex:"T_SealRing", tint:[1.9,1.55,2.7], add:true},
  sealCore:{tex:"T_SealCore", tint:[2.1,1.7,2.9], add:true},
  scratch:{tex:"T_Scratch", tint:[2.1,1.7,2.9], add:true},
  glow:{tex:"T_Glow", tint:[1.1,1.0,1.3], add:true},
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
  splat:{tex:"T_InkSplat", tint:[1.6,1.4,2.0], add:false},
  wisp:{tex:"T_WispH", tint:[1.6,1.3,2.2], add:false},
};

// demo: the brand lands on actor 0 at t = 0 (boss `brand` 0.4), the burst plays at the actor's feet at t = 3.0
const RUN = [[0,[0,0,0],200],[0.6,[0,0,0],200],[2.6,[-4.2,0,3.2],300],[3.6,[-4.2,0,3.2],300]];
const BOSS = [{ c:[0,4.3,-9], e:[1.2,3.3,0.9], shade:[0.09,0.06,0.13] }];
const ALLY = [[3.2,0,1.5,250],[-2.2,0,-3.4,30],[5.6,0,-2.5,280]];
const ev = [{ t:0, system:"mark", actor:0 }];
const hit = p => [{ t:3.0, point:p, normal:[0,1,0], incoming:[0,-1,0], system:"burst" }];
const SCENE = {
  name: "MordianBrand",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.7, tint:[0.9,0.85,1.0] }, tonemapping:"neutral",
  systems:[
    { id:"mark", role:"attached", loop:false, duration:T, layers:MARK },
    { id:"burst", role:"impact", loop:false, duration:0.7, layers:BURST },
  ],
  demo:{
    interval:4.6, bg:[0.13,0.12,0.16], floor:[0.10,0.095,0.12], grid:[0.07,0.065,0.09], dummy:{ height:1.8 }, props:BOSS,
    scenarios:{
      stand:{ label:"기본 (서 있음)", actors:[{ path:[[0,[0,0,0],200]] }], events:ev, hits:hit([0,0,0]), targets:ALLY,
              camera:{ target:[0,1.3,0], dist:11, yaw:-70, pitch:18 } },
      run:  { label:"이동 중 (따라감)", actors:[{ path:RUN }], events:ev, hits:hit([-4.2,0,3.2]), targets:ALLY,
              camera:{ target:[-2,1.3,1.5], dist:13, yaw:-70, pitch:20 } },
      close:{ label:"근접 (머리 위 인장)", actors:[{ path:[[0,[0,0,0],160]] }], events:ev, hits:hit([0,0,0]),
              camera:{ target:[0,1.7,0], dist:4.2, yaw:-80, pitch:10 } },
      mid:  { label:"폭발 근접", actors:[{ path:[[0,[0,0,0],200]] }], events:ev, hits:hit([0,0,0]), targets:ALLY,
              camera:{ target:[0,1.2,0], dist:7.5, yaw:-75, pitch:12 } },
      top:  { label:"폭발 범위 (위)", actors:[{ path:[[0,[0,0,0],200]] }], events:ev, hits:hit([0,0,0]), targets:ALLY,
              camera:{ target:[0,0,0], dist:14, yaw:-90, pitch:62 } },
    },
  },
};
