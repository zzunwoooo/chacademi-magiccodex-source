// ---- shared Mordian helpers (prepended to each pack spec) ----
const TAU = Math.PI * 2;
const TRI = (p) => [[0,0],[0.5,p],[1,0]];
// Mordian palette (sRGB 0..1)
const C_INK = [0.035,0.024,0.055], C_SHD = [0.165,0.086,0.25], C_VIO = [0.54,0.235,1.0], C_LIL = [0.84,0.59,1.0], C_WL = [0.95,0.9,1.0];
const C_SIL = [0.957,0.94,1.0], C_GLD = [0.91,0.85,0.66], C_WH = [1,1,1];
function merge(parts) {
  const verts = [], uvs = [], tris = [];
  for (const p of parts) { const o = verts.length; p.verts.forEach(v => verts.push(v.slice())); p.uvs.forEach(u => uvs.push(u.slice())); p.tris.forEach(i => tris.push(i + o)); }
  return { verts, uvs, tris };
}
// flat ribbon along a 2D polyline (x, z) on the ground at height y; U = cumulative length 0..1 (stroke order), V across
function polyRibbon(pts, w, y) {
  const n = pts.length, L = [0];
  for (let i = 1; i < n; i++) L.push(L[i-1] + Math.hypot(pts[i][0]-pts[i-1][0], pts[i][1]-pts[i-1][1]));
  const tot = L[n-1] || 1, verts = [], uvs = [], tris = [];
  const segN = i => { const a = pts[Math.max(0,i)], b = pts[Math.min(n-1,i+1)]; const dx = b[0]-a[0], dz = b[1]-a[1], l = Math.hypot(dx,dz) || 1; return [-dz/l, dx/l]; };
  for (let i = 0; i < n; i++) {
    const n0 = segN(i === 0 ? 0 : i-1), n1 = segN(i === n-1 ? n-2 : i);
    let nx = n0[0] + n1[0], nz = n0[1] + n1[1]; const l = Math.hypot(nx, nz) || 1; nx /= l; nz /= l;
    const m = Math.min(2.5, 1 / Math.max(0.4, nx*n1[0] + nz*n1[1])), h = w * 0.5 * m;
    verts.push([pts[i][0] - nx*h, y, pts[i][1] - nz*h], [pts[i][0] + nx*h, y, pts[i][1] + nz*h]);
    uvs.push([L[i]/tot, 0], [L[i]/tot, 1]);
  }
  for (let i = 0; i < n-1; i++) { const k = i*2; tris.push(k,k+1,k+2, k+1,k+3,k+2); }
  return { verts, uvs, tris, len: tot };
}
// arc points around (cx, cz): phi = a0 - s*span (head leads a +Y spin, SCHEMA convention)
function arcPts(cx, cz, r, a0, span, n) { const p = []; for (let i = 0; i <= n; i++) { const ph = a0 - span * i / n; p.push([cx + r*Math.cos(ph), cz + r*Math.sin(ph)]); } return p; }
function ringMesh(r, w, y, a0, span) { return polyRibbon(arcPts(0, 0, r, a0 || 0, span || TAU, Math.max(24, Math.round(96 * (span || TAU) / TAU * Math.min(3, Math.max(1, r / 2))))), w, y); }
// rune band = N identical arc segments, each mapping U 0..1 of the rune strip; V 0 = inner edge
function runeBand(r0, r1, N, y, gap) {
  const parts = []; gap = gap || 0.04;
  for (let k = 0; k < N; k++) {
    const a0 = -k / N * TAU, span = TAU / N * (1 - gap), verts = [], uvs = [], tris = [], seg = 12;
    for (let i = 0; i <= seg; i++) { const ph = a0 - span * i / seg, c = Math.cos(ph), s = Math.sin(ph);
      verts.push([c*r0, y, s*r0], [c*r1, y, s*r1]); uvs.push([i/seg, 0], [i/seg, 1]); }
    for (let i = 0; i < seg; i++) { const q = i*2; tris.push(q,q+1,q+2, q+1,q+3,q+2); }
    parts.push({ verts, uvs, tris });
  }
  return merge(parts);
}
// star polygon {n/k} on radius r: returns an array of [p0, p1] line segments
function polygram(n, k, r, rot) { const v = []; for (let i = 0; i < n; i++) { const a = (rot || 0) + i / n * TAU; v.push([r*Math.cos(a), r*Math.sin(a)]); }
  const segs = []; for (let i = 0; i < n; i++) segs.push([v[i], v[(i + k) % n]]); return { v, segs }; }
// vertical crossed quads (pillar / spike), base at y = 0, height h, width w, V 0 = bottom
function crossV(h, w, n) { const parts = []; n = n || 2;
  for (let k = 0; k < n; k++) { const a = k / n * Math.PI, cx = Math.cos(a) * w / 2, cz = Math.sin(a) * w / 2;
    parts.push({ verts:[[-cx,0,-cz],[cx,0,cz],[cx,h,cz],[-cx,h,-cz]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3] }); }
  return merge(parts); }
// 3D ribbon along a curve f(s) → point, with a width direction wdir(s); U along (s), V across
function ribbon3(seg, f, wdir, w) { const verts = [], uvs = [], tris = [];
  for (let i = 0; i <= seg; i++) { const s = i / seg, p = f(s), d = wdir(s), hw = (typeof w === 'function' ? w(s) : w) / 2;
    verts.push([p[0]-d[0]*hw, p[1]-d[1]*hw, p[2]-d[2]*hw], [p[0]+d[0]*hw, p[1]+d[1]*hw, p[2]+d[2]*hw]); uvs.push([s,0],[s,1]); }
  for (let i = 0; i < seg; i++) { const k = i*2; tris.push(k,k+1,k+2, k+1,k+3,k+2); }
  return { verts, uvs, tris }; }
function cleanMesh(m) { return { verts: m.verts, uvs: m.uvs, tris: m.tris }; }
// MordianOfferingCircle — 기믹 ④ "제물의 마법진". P = circle centre on the ground (radius ~2.5 m), +Z = server facing.
// systems: circle (4 s ramp to the judgment, holds afterwards until stopped) / circle3 (same, 3 s ramp for later rounds)
//          success (right head-count: light drifts up quietly) / fail (wrong count: ink blast + shadow spikes + violet shock ring).
// The judgment number (1~3) is the game's text display — not part of this pack.
const R = 2.5, Y0 = 0.03;
const HEPTA = polygram(7, 3, 1.95, Math.PI/2), INNER = polygram(7, 2, 0.95, -Math.PI/2);
var EXTRA_MESHES = {
  rings: merge([ringMesh(2.5, 0.075, Y0), ringMesh(2.38, 0.03, Y0), ringMesh(2.62, 0.02, Y0), ringMesh(1.95, 0.05, Y0), ringMesh(0.95, 0.04, Y0)].map(cleanMesh)),
  ringA: cleanMesh(ringMesh(2.5, 0.075, Y0, Math.PI/2)),
  ringB: cleanMesh(polyRibbon(arcPts(0, 0, 1.95, -Math.PI/2, -TAU, 140), 0.05, Y0)),
  ringC: cleanMesh(ringMesh(0.95, 0.04, Y0, 0)),
  band: runeBand(2.0, 2.34, 16, Y0 - 0.002),
  poly: merge([...HEPTA.segs.map(s => polyRibbon([s[0], s[1]], 0.045, Y0 + 0.002)), ...HEPTA.v.map(v => polyRibbon(arcPts(v[0], v[1], 0.16, 0, TAU, 28), 0.03, Y0 + 0.003))].map(cleanMesh)),
  inner: merge([...INNER.segs.map(s => polyRibbon([s[0], s[1]], 0.035, Y0 + 0.002)), runeBand(1.0, 1.2, 10, Y0)].map(cleanMesh)),
};
HEPTA.segs.forEach((s, i) => { EXTRA_MESHES[`hl${i}`] = cleanMesh(polyRibbon([s[0], s[1]], 0.045, Y0 + 0.002)); });

// rotation stages: copies hand over at the stage times with rot3 = accumulated angle, so the spin speeds up without jumps
const GROUPS = [ // [mesh, mat, base ω (rad/s), fudge, alpha scale]
  ["rings", "line", 0, 21, 1], ["band", "band", 0.22, 22, 0.9], ["poly", "line", -0.16, 20, 0.95], ["inner", "band", 0.4, 19, 0.85],
];
const MULT = [1, 1.9, 3.4, 6.0], STA = [0.55, 0.72, 0.88, 1.0];
const STCOL = [C_VIO, C_LIL, C_LIL, C_WL];
function circleLayers(T) {
  const L = [], ts = [0.42, 0.36 * T, 0.6 * T, 0.8 * T], HOLD = 12, OV = 0.06;
  // base: ink stain + floor glow that grows with the charge; dark wisps creeping round the rim
  L.push({ name:"C01 InkStain", mat:"inkDecal", mode:"mesh", mesh:"quad", fudge:60, pos:[0,0.01,0], life:HOLD, size:6.6, bursts:[[0,1]],
    sizeLife:[[0,0.4,0,3],[0.04,1]], alpha:[[0,0],[0.02,0.5],[0.97,0.5],[1,0]], onStop:0.35 });
  L.push({ name:"C02 FloorGlow", mat:"glowFloor", mode:"mesh", mesh:"quad", fudge:55, pos:[0,0.015,0], life:HOLD, size:6.2, bursts:[[0,1]],
    color:[[0,C_VIO],[T/HOLD,C_LIL],[1,C_LIL]], alpha:[[0,0],[0.4/HOLD,0.12],[T/HOLD,0.3],[0.97,0.3],[1,0]], onStop:0.35 });
  L.push({ name:"C03 CoreGlow", mat:"glowFloor", mode:"mesh", mesh:"quad", fudge:54, pos:[0,0.02,0], life:HOLD, size:2.4, bursts:[[0.2,1]],
    color:[[0,C_VIO],[T/HOLD,C_LIL],[1,C_LIL]], alpha:[[0,0],[0.3/HOLD,0.12],[T/HOLD,0.26],[0.97,0.26],[1,0]], onStop:0.35 });
  L.push({ name:"C04 RimWisps", mat:"inkMist", fudge:45, life:[1.4,2.0], size:[0.9,1.5], shape:{type:"circle", r:2.75, th:0.15, y:0.2}, rate:5, bursts:[[0,6]],
    vel:{y:[0.1,0.3], orb:[-0.5,-0.25]}, rot:[0,TAU], rotZ:[-0.3,0.3], alpha:[[0,0],[0.3,0.5],[1,0]], flip:{x:4,y:4}, onStop:0.3 });
  // draw-in (0 → 0.45): rings traced by pen heads, heptagram lines shot one by one
  [["ringA",0,0.34],["ringB",0.05,0.3],["ringC",0.1,0.26]].forEach(([m,t0,d],i) => {
    L.push({ name:`C1${i} Draw_${m}`, mat:"wipe", mode:"mesh", mesh:m, fudge:18, life:d, size:1, bursts:[[t0,1]], flip:{x:2,y:8}, color:[[0,C_LIL],[1,C_LIL]] });
    L.push({ name:`C1${i} Keep_${m}`, mat:"line", mode:"mesh", mesh:m, fudge:18, life:0.5 - t0 - d, size:1, bursts:[[t0+d,1]], color:[[0,C_VIO],[1,C_VIO]],
      alpha:[[0,STA[0]],[0.6,STA[0]],[1,0]] });
  });
  HEPTA.segs.forEach((s, i) => {
    const t0 = 0.1 + i * 0.035;
    L.push({ name:`C2${i} DrawLine`, mat:"wipe", mode:"mesh", mesh:`hl${i}`, fudge:17, life:0.12, size:1, bursts:[[t0,1]], flip:{x:2,y:8}, color:[[0,C_WL],[1,C_LIL]] });
    L.push({ name:`C2${i} KeepLine`, mat:"line", mode:"mesh", mesh:`hl${i}`, fudge:17, life:0.5 - t0 - 0.12, size:1, bursts:[[t0 + 0.12,1]], color:[[0,C_VIO],[1,C_VIO]],
      alpha:[[0,STA[0]],[0.6,STA[0]],[1,0]] });
  });
  L.push({ name:"C30 SigilIn", mat:"sigil", mode:"mesh", mesh:"quad", fudge:16, pos:[0,Y0+0.004,0], life:0.3, size:1.75, bursts:[[0.2,1]],
    sizeLife:[[0,1.3,0,-1.5],[1,1]], color:[[0,C_WL],[1,C_LIL]], alpha:[[0,0],[1,0.7]] });
  // held, staged rotating layers
  const G2 = GROUPS.concat([["quad", "sigil", -0.3, 16, 0.75]]);
  G2.forEach(([mesh, mat, w, fudge, as], gi) => {
    let th = 0;
    for (let k = 0; k < 4; k++) {
      const t0 = k === 0 ? (mesh === "poly" || mesh === "rings" ? 0.42 : 0.42) : ts[k] - OV, t1 = k < 3 ? ts[k+1] + OV : t0 + HOLD, life = t1 - t0, om = w * MULT[k];
      const fadeIn = k === 0 ? 0.08 : OV * 2, a = STA[k] * as;
      L.push({ name:`S${gi}${k} ${mesh}_stage${k}`, mat:k === 3 && mat === "line" ? "lineHot" : mat, mode:"mesh", mesh, fudge, pos:mesh === "quad" ? [0,Y0+0.004,0] : [0,0,0],
        life, size:mesh === "quad" ? 1.75 : 1, rot3:[0, th, 0], rotLife:[0, om, 0], bursts:[[t0,1]],
        color:[[0,STCOL[k]],[1,STCOL[k]]], alpha:k < 3 ? [[0,0],[fadeIn/life,a],[1 - OV*2/life,a],[1,0]] : [[0,0],[fadeIn/life,a],[1 - 0.35/life,a],[1,0]],
        onStop:k === 3 ? 0.35 : 0 });
      if (k < 3) th += om * (ts[k+1] - OV - t0);   // angle where the next copy starts
    }
  });
  // heartbeat: pulses come faster and brighter toward the judgment
  const beats = [0.25, 0.42, 0.56, 0.67, 0.76, 0.83, 0.89, 0.94, 0.98].map(f => f * T);
  beats.forEach((b, i) => {
    const s = (i + 1) / beats.length;
    L.push({ name:`P${i} RingBeat`, mat:"lineHot", mode:"mesh", mesh:"rings", fudge:15, life:0.32, size:1, bursts:[[b,1]], sizeLife:[[0,1],[1,1.03]],
      color:[[0,C_WL],[1,C_LIL]], alpha:[[0,0.35 + 0.45 * s],[1,0]] });
    L.push({ name:`P${i} WaveBeat`, mat:"ring", mode:"mesh", mesh:"quad", fudge:14, pos:[0,0.05,0], life:0.5, size:6.6, bursts:[[b,1]], sizeLife:[[0,0.92],[1,1.06]],
      color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0.25 + 0.4 * s],[1,0]] });
    L.push({ name:`P${i} RimMotes`, mat:"dot", fudge:-5, life:[0.8,1.4], size:[0.04,0.09], shape:{type:"circle", r:2.5, th:0.1, y:0.05}, bursts:[[b, Math.round(6 + 22 * s)]],
      vel:{y:[0.8,1.8 + 1.5 * s]}, noise:{s:0.3, f:1.2, sp:0.6}, color:[[0,C_WL],[0.5,C_LIL],[1,C_VIO]], alpha:[[0,0],[0.15,1],[0.7,0.8],[1,0]] });
  });
  L.push({ name:"C40 Motes", mat:"dot", fudge:-5, life:[1.4,2.2], size:[0.035,0.07], shape:{type:"circle", r:2.4, th:1, y:0.05}, rate:10, bursts:[[0.3,8]],
    vel:{y:[0.4,0.9], orb:[0.2,0.5]}, noise:{s:0.25, f:1.2, sp:0.5}, color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0],[0.2,0.9],[0.7,0.7],[1,0]], onStop:0.3 });
  L.push({ name:"C41 RimStreaks", mat:"streak", mode:"stretch", vs:0.08, ls:1.6, fudge:-4, life:[0.35,0.55], size:[0.05,0.1], shape:{type:"circle", r:2.5, th:0.04, y:0.05},
    bursts:[[0.6*T,3,6,0.12],[0.8*T,5,8,0.07]], vel:{y:[3,5.5]}, color:[[0,C_WL],[1,C_LIL]], alpha:[[0,0],[0.15,1],[1,0]] });
  L.push({ name:"C42 Twinkle", mat:"star", fudge:-6, life:[0.3,0.5], size:[0.14,0.26], shape:{type:"circle", r:2.0, th:1, y:0.06}, rate:5, rot:[0,0.8],
    sizeLife:[[0,0.2],[0.3,1],[1,0]] });
  return L;
}

// ---------------------------------------------------------------- SUCCESS (1.6 s): calm — light lifts off the circle and scatters upward
const SUCCESS = [
  { name:"U01 RingsLift", mat:"lineHot", mode:"mesh", mesh:"rings", fudge:20, life:1.3, size:1, bursts:[[0,1]], vel:{y:0.7}, sizeLife:[[0,1],[1,0.86]],
    color:[[0,C_WL],[1,C_LIL]], alpha:[[0,0.85],[0.5,0.45],[1,0]] },
  { name:"U02 RingsLift2", mat:"line", mode:"mesh", mesh:"rings", fudge:20, life:1.4, size:0.92, bursts:[[0.15,1]], vel:{y:1.2}, sizeLife:[[0,1],[1,0.75]],
    color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0],[0.1,0.55],[1,0]] },
  { name:"U03 FloorFade", mat:"glowFloor", mode:"mesh", mesh:"quad", fudge:55, pos:[0,0.015,0], life:1.2, size:6.2, bursts:[[0,1]], color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0.3],[1,0]] },
  { name:"U04 InkFade", mat:"inkDecal", mode:"mesh", mesh:"quad", fudge:60, pos:[0,0.01,0], life:0.9, size:6.6, bursts:[[0,1]], alpha:[[0,0.5],[1,0]] },
  { name:"U05 MotesUp", mat:"dot", fudge:-5, life:[1.2,2.0], size:[0.04,0.1], shape:{type:"circle", r:2.45, th:1, y:0.08}, bursts:[[0,50],[0.2,30],[0.45,20]],
    vel:{y:[1.0,2.4]}, noise:{s:0.4, f:1.0, sp:0.6}, color:[[0,C_WL],[0.5,C_LIL],[1,C_VIO]], alpha:[[0,0],[0.1,1],[0.7,0.8],[1,0]], sizeLife:[[0,1],[1,0.4]] },
  { name:"U06 StreaksUp", mat:"streak", mode:"stretch", vs:0.08, ls:1.4, fudge:-4, life:[0.5,0.8], size:[0.05,0.1], shape:{type:"circle", r:2.4, th:1, y:0.05},
    bursts:[[0,22],[0.15,12]], vel:{y:[3,5]}, color:[[0,C_WL],[1,C_LIL]], alpha:[[0,0],[0.15,0.9],[1,0]] },
  { name:"U07 SoftWave", mat:"ring", mode:"mesh", mesh:"quad", fudge:14, pos:[0,0.05,0], life:0.9, size:6.8, bursts:[[0,1]], sizeLife:[[0,0.95],[1,1.12]],
    color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0.5],[1,0]] },
  { name:"U08 Twinkles", mat:"star", fudge:-6, life:[0.4,0.7], size:[0.2,0.38], shape:{type:"circle", r:2.3, th:1, y:0.3}, bursts:[[0.05,10],[0.3,8]], rot:[0,0.8],
    vel:{y:[0.4,0.9]}, sizeLife:[[0,0.2],[0.3,1],[1,0]] },
  { name:"U09 CenterLift", mat:"glow", fudge:-2, pos:[0,0.6,0], life:0.9, size:1.6, bursts:[[0,1]], vel:{y:1.2}, color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0.3],[1,0]] },
];

// ---------------------------------------------------------------- FAIL (1.6 s): black ink blast, shadow spikes from below, violet shock ring
const SPIKE = { mode:"mesh", mesh:"spike", rot3:[[-0.3,0.3],[0,TAU],[-0.3,0.3]] };
const SPK_LIFE = [[0,0,0,9],[0.09,1.08],[0.16,1],[0.65,0.97],[1,0,-2,0]];
const FAIL = [
  { name:"F01 Flash", mat:"star", fudge:-8, pos:[0,0.9,0], life:0.22, size:3.0, bursts:[[0,1]], rot:[0,0.8], color:[[0,C_LIL],[1,C_VIO]], sizeLife:[[0,0.4],[0.25,1],[1,0]] },
  { name:"F02 FlashGlow", mat:"glow", fudge:-7, pos:[0,0.8,0], life:0.25, size:3.2, bursts:[[0,1]], color:[[0,C_VIO],[1,C_VIO]], alpha:[[0,0.35],[1,0]] },
  { name:"F03 RingsBurst", mat:"lineHot", mode:"mesh", mesh:"rings", fudge:20, life:0.3, size:1, bursts:[[0,1]], sizeLife:[[0,1],[1,1.08]],
    color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,1],[1,0]] },
  { name:"F04 InkSplat", mat:"inkDecal", mode:"mesh", mesh:"quad", fudge:60, pos:[0,0.012,0], life:1.6, size:7.0, rot3:[0,[0,TAU],0], bursts:[[0,1]],
    sizeLife:[[0,0.3,0,6],[0.12,1]], alpha:[[0,0.85],[0.7,0.75],[1,0]] },
  { name:"F05 Cracks", mat:"crack", mode:"mesh", mesh:"quad", fudge:50, pos:[0,0.03,0], life:0.9, size:6.4, rot3:[0,[0,TAU],0], bursts:[[0.02,1]],
    sizeLife:[[0,0.5,0,4],[0.2,1]], color:[[0,C_LIL],[0.3,C_VIO],[1,C_VIO]], alpha:[[0,1],[0.5,0.7],[1,0]] },
  { name:"F06 Scratch", mat:"scratch", mode:"mesh", mesh:"quad", fudge:12, pos:[0,0.06,0], life:0.45, size:4.6, rot3:[0,[0,TAU],0], bursts:[[0.03,1]],
    sizeLife:[[0,0.85],[1,1.1]], color:[[0,C_WL],[1,C_VIO]], alpha:[[0,1],[0.4,0.75],[1,0]] },
  { name:"F07 Shock", mat:"ring", mode:"mesh", mesh:"quad", fudge:14, pos:[0,0.07,0], life:0.5, size:9, bursts:[[0.02,1]], sizeLife:[[0,0.08,0,2.5],[1,1]],
    color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0.85],[0.6,0.5],[1,0]] },
  { name:"F08 Shock2", mat:"ring", mode:"mesh", mesh:"quad", fudge:14, pos:[0,0.08,0], life:0.65, size:11, rot3:[0,1,0], bursts:[[0.1,1]], sizeLife:[[0,0.08,0,2.2],[1,1]],
    color:[[0,C_VIO],[1,C_SHD]], alpha:[[0,0.6],[1,0]] },
  Object.assign({ name:"F10 SpikesCore", mat:"spike", fudge:30, life:[1.1,1.3], size:[2.2,3.4], shape:{type:"circle", r:0.7, th:1, y:0}, bursts:[[0.01,6]], sizeLife:SPK_LIFE }, SPIKE),
  Object.assign({ name:"F11 SpikesRing", mat:"spike", fudge:31, life:[0.9,1.2], size:[1.1,2.1], shape:{type:"circle", r:2.2, th:0.5, y:0}, bursts:[[0.04,9],[0.09,7]], sizeLife:SPK_LIFE }, SPIKE),
  Object.assign({ name:"F12 SpikesOuter", mat:"spike", fudge:32, life:[0.7,1.0], size:[0.7,1.3], shape:{type:"circle", r:2.7, th:0.12, y:0}, bursts:[[0.12,12]], sizeLife:SPK_LIFE }, SPIKE),
  { name:"F13 InkBlast", mat:"inkMist", fudge:40, life:[0.8,1.2], size:[1.3,2.2], shape:{type:"sphere", r:0.6, th:1, y:0.7}, bursts:[[0,14]], speed:[3,6], drag:3.2,
    vel:{y:[0.3,0.8]}, rot:[0,TAU], rotZ:[-0.6,0.6], alpha:[[0,0.9],[0.5,0.65],[1,0]], sizeLife:[[0,0.5],[0.3,1],[1,1.25]], flip:{x:4,y:4} },
  { name:"F14 InkRoll", mat:"inkMist", fudge:41, life:[0.9,1.3], size:[1.2,1.8], shape:{type:"circle", r:2.3, th:0.3, y:0.3}, bursts:[[0.05,12]], vel:{rad:[1.5,2.5], y:[0.1,0.4]},
    drag:1.5, rot:[0,TAU], alpha:[[0,0],[0.15,0.75],[1,0]], flip:{x:4,y:4} },
  { name:"F15 InkShards", mat:"shard", fudge:35, life:[0.7,1.2], size:[0.15,0.32], shape:{type:"sphere", r:0.5, th:1, y:0.6}, bursts:[[0,40]], speed:[4,9],
    vel:{y:[2,5]}, gravity:1.2, drag:0.8, rot:[0,TAU], rotZ:[-10,10], alpha:[[0,1],[0.8,1],[1,0]] },
  { name:"F16 VioletSparks", mat:"streak", mode:"stretch", vs:0.05, ls:1.2, fudge:-4, life:[0.25,0.45], size:[0.05,0.1], shape:{type:"sphere", r:0.4, th:1, y:0.5},
    bursts:[[0,30]], speed:[7,13], drag:3, color:[[0,C_WL],[1,C_LIL]], alpha:[[0,1],[1,0]] },
  { name:"F17 Embers", mat:"dot", fudge:-5, life:[0.8,1.4], size:[0.05,0.1], shape:{type:"circle", r:2.4, th:1, y:0.2}, bursts:[[0.1,30]], vel:{y:[1,2.5]},
    noise:{s:0.6, f:1, sp:0.8}, color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,1],[1,0]] },
];

var _spike = crossV(1, 0.36, 3); EXTRA_MESHES.spike = _spike;
const MATS = {
  line:{tex:"T_Line", tint:[1.6,1.35,2.0], add:true},
  lineHot:{tex:"T_Line", tint:[2.2,1.9,2.6], add:true},
  wipe:{tex:"T_LineWipe", tint:[2.0,1.7,2.5], add:true},
  band:{tex:"T_RuneStrip", tint:[1.4,1.15,1.9], add:true},
  sigil:{tex:"T_Sigil", tint:[1.6,1.35,2.1], add:true},
  inkDecal:{tex:"T_InkSplat", tint:[1,1,1], add:false},
  glowFloor:{tex:"T_Glow", tint:[1.2,1.0,1.6], add:true},
  glow:{tex:"T_Glow", tint:[1.4,1.1,1.9], add:true},
  inkMist:{tex:"T_InkMist_Sheet", tint:[1,1,1], add:false},
  shard:{tex:"T_InkShard", tint:[2.0,1.8,2.4], add:false},
  spike:{tex:"T_Spike", tint:[2.2,1.9,2.6], add:false},
  star:{tex:"T_Star", tint:[2.2,1.9,2.6], add:true},
  dot:{tex:"T_Dot", tint:[2.0,1.7,2.4], add:true},
  ring:{tex:"T_Ring", tint:[1.6,1.3,2.1], add:true},
  crack:{tex:"T_Crack", tint:[1.8,1.5,2.3], add:true},
  scratch:{tex:"T_Scratch", tint:[1.7,1.4,2.2], add:true},
  streak:{tex:"T_Streak", tint:[2.0,1.7,2.5], add:true},
};
const H0 = { path:[[0,[0,0,0],0]], hidden:true }, MEN = [{ path:[[0,[0.9,0,0.4],200]] }, { path:[[0,[-1.0,0,-0.6],40]] }];
const CAM = { target:[0,0.6,0], dist:9.5, yaw:-90, pitch:42 };
const SCENE = {
  name:"MordianOfferingCircle",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.65, tint:[0.9,0.8,1.0] }, tonemapping:"neutral",
  systems:[
    { id:"circle", role:"static", loop:false, duration:4.0, layers:circleLayers(4) },
    { id:"circle3", role:"static", loop:false, duration:3.0, layers:circleLayers(3) },
    { id:"success", role:"static", loop:false, duration:0.5, layers:SUCCESS },
    { id:"fail", role:"static", loop:false, duration:0.2, layers:FAIL },
  ],
  demo:{
    interval:6.5, bg:[0.12,0.11,0.14], floor:[0.2,0.19,0.22], grid:[0.17,0.16,0.19],
    scenarios:{
      success:{ label:"4초 → 성공", interval:6.5, actors:[H0, ...MEN], events:[{t:0, system:"circle", actor:0}, {t:4, stop:["circle"]}, {t:4, system:"success", actor:0}], camera:CAM },
      fail:{ label:"4초 → 실패", interval:6.5, actors:[H0, ...MEN], events:[{t:0, system:"circle", actor:0}, {t:4, stop:["circle"]}, {t:4, system:"fail", actor:0}], camera:CAM },
      fail3:{ label:"3초 라운드 → 실패", interval:5.5, actors:[H0, MEN[0]], events:[{t:0, system:"circle3", actor:0}, {t:3, stop:["circle3"]}, {t:3, system:"fail", actor:0}], camera:CAM },
      top:{ label:"위에서 (4초 → 성공)", interval:6.5, actors:[H0], events:[{t:0, system:"circle", actor:0}, {t:4, stop:["circle"]}, {t:4, system:"success", actor:0}],
        camera:{ target:[0,0,0], dist:8.5, yaw:-90, pitch:80 } },
    },
  },
};
