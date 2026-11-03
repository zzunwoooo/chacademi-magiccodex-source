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
// MordianRitualRune — 기믹 ② "금지된 의식 — 역순 해제". One ground rune (radius ~1.5 m), P = rune centre on the ground, +Z = server facing.
// 5 glyph variants k = 1..5, each with its own systems: draw{k} (0.8 s) → hold{k} (≤ 30 s loop-free hold) → erase{k} (1.5 s) | wrong{k} (0.9 s).
// Shared outer ring + rune band; the glyph inside is the variant. Line work = mesh strips with one line profile texture (T_Line) and a reveal wipe (T_LineWipe 2x8).
const GS = 0.9;          // glyph scale (glyph space ±1 → ±0.9 m)
const YG = 0.035, YR = 0.03, YB = 0.028;
const circ = (cx, cz, r, a0) => ({ circle:[cx, cz, r, a0 || 0] });
const GLYPHS = [
  [ { pts:[[0,0.95],[0,-0.95]] }, { pts:[[-0.66,0.56],[0,0.0],[0.66,0.56]] }, { pts:[[-0.52,-0.6],[0.52,-0.6]] } ],                                  // 1: stem + V + bar
  [ { pts:[[0,0.98],[0.86,-0.52],[-0.86,-0.52],[0,0.98]] }, { pts:[[0,0.98],[0,-0.52]] }, circ(0, 0.02, 0.22, Math.PI/2) ],                       // 2: triangle + spine + eye
  [ { pts:[[0,0.96],[0.7,0],[0,-0.96],[-0.7,0],[0,0.96]] }, { pts:[[-0.98,0],[0.98,0]] }, { pts:[[-0.42,0.62],[-0.7,0.86]] }, { pts:[[0.42,-0.62],[0.7,-0.86]] } ], // 3: crossed diamond + ticks
  [ { pts:[[-0.72,0.5],[0.02,-0.78]] }, { pts:[[-0.36,0.72],[0.38,-0.56]] }, { pts:[[0.0,0.92],[0.74,-0.34]] }, { arc:[0, 0, 0.92, -0.35, -2.4] } ], // 4: three scratches + crescent
  [ { pts:[[-0.62,0.8],[0.62,0.8],[-0.62,-0.8],[0.62,-0.8]] }, circ(0, 0, 0.24, 0), { pts:[[-0.9,0.32],[-0.9,-0.32]] }, { pts:[[0.9,0.32],[0.9,-0.32]] } ], // 5: Z + ring + side bars
];
const strokePts = st => st.pts ? st.pts.map(p => [p[0]*GS, p[1]*GS])
  : st.circle ? arcPts(st.circle[0]*GS, st.circle[1]*GS, st.circle[2]*GS, st.circle[3], TAU, 40)
  : arcPts(st.arc[0]*GS, st.arc[1]*GS, st.arc[2]*GS, st.arc[3], st.arc[4] - st.arc[3] < 0 ? -(st.arc[4] - st.arc[3]) : st.arc[4] - st.arc[3], 40);
var EXTRA_MESHES = {
  ringO: cleanMesh(ringMesh(1.48, 0.06, YR, Math.PI/2)),
  ringI: cleanMesh(ringMesh(1.22, 0.035, YR, -Math.PI/2, -TAU)),
  rings: merge([ringMesh(1.48, 0.06, YR), ringMesh(1.22, 0.035, YR), ringMesh(1.56, 0.018, YR)].map(cleanMesh)),
  band: runeBand(1.25, 1.45, 12, YB),
};
// fix ringI (span must be positive for arcPts; draw it the other way round by starting at -90°)
EXTRA_MESHES.ringI = cleanMesh(polyRibbon(arcPts(0, 0, 1.22, -Math.PI/2, -TAU, 96), 0.035, YR));
const GW = 0.075;
GLYPHS.forEach((g, k) => {
  const parts = g.map(st => polyRibbon(strokePts(st), GW, YG));
  parts.forEach((p, i) => { EXTRA_MESHES[`g${k+1}s${i}`] = cleanMesh(p); });
  EXTRA_MESHES[`glyph${k+1}`] = merge(parts.map(cleanMesh));
});

// ---------------------------------------------------------------- DRAW (0..0.8 s): strokes appear in stroke order
const T_END = 1.0;      // keep layers last until here (hold{k} starts at 0.8 and fades in over 0.15 s)
function drawLayers(k) {
  const g = GLYPHS[k], L = [];
  // base: ink stain spreading under the rune, dark mist behind
  L.push({ name:"D01 InkStain", mat:"inkDecal", mode:"mesh", mesh:"quad", fudge:60, pos:[0,0.01,0], life:1.05, size:3.9, rot3:[0,0.7*k,0], bursts:[[0,1]],
    sizeLife:[[0,0.35,0,2.5],[0.35,1]], alpha:[[0,0],[0.15,0.55],[0.8,0.55],[1,0]] });
  L.push({ name:"D02 GroundGlow", mat:"glowFloor", mode:"mesh", mesh:"quad", fudge:55, pos:[0,0.015,0], life:1.0, size:3.6, bursts:[[0.1,1]],
    color:[[0,C_VIO],[1,C_VIO]], alpha:[[0,0],[0.5,0.22],[0.85,0.22],[1,0]] });
  L.push({ name:"D03 InkPuffs", mat:"inkMist", fudge:40, life:[0.8,1.1], size:[0.7,1.1], shape:{type:"circle", r:1.4, th:0.3, y:0.2}, bursts:[[0,6],[0.25,4]],
    vel:{y:[0.15,0.4], orb:[-0.6,-0.3]}, rot:[0,TAU], alpha:[[0,0],[0.2,0.55],[1,0]], flip:{x:4,y:4} });
  // rings: outer clockwise from the front, inner counter-clockwise, faint outer hairline
  const ringsDraw = [["ringO", 0.0, 0.26], ["ringI", 0.06, 0.26]];
  ringsDraw.forEach(([m, t0, d], i) => {
    L.push({ name:`D1${i} RingDraw_${m}`, mat:"wipe", mode:"mesh", mesh:m, fudge:20, life:d, size:1, bursts:[[t0,1]], flip:{x:2,y:8}, color:[[0,C_LIL],[1,C_LIL]] });
    L.push({ name:`D1${i} RingKeep_${m}`, mat:"line", mode:"mesh", mesh:m, fudge:21, life:T_END - t0 - d, size:1, bursts:[[t0 + d,1]],
      color:[[0,C_LIL],[1,C_LIL]], alpha:[[0,1],[1 - 0.15/(T_END - t0 - d),1],[1,0]] });
  });
  L.push({ name:"D15 RingPen", mat:"star", fudge:-2, pos:[0,0.12,0], life:0.26, size:0.45, shape:{type:"none", off:[0,0,1.48]}, bursts:[[0,1]], vel:{orb:TAU/0.26}, rotZ:6 });
  L.push({ name:"D16 BandIn", mat:"band", mode:"mesh", mesh:"band", fudge:22, life:T_END - 0.2, size:1, bursts:[[0.2,1]], rot3:[0,0.3,0], rotLife:[0,-0.375,0],
    sizeLife:[[0,1.12,0,-1.5],[0.3,1]], alpha:[[0,0],[0.3,0.85],[1 - 0.15/(T_END-0.2),0.85],[1,0]] });
  // glyph strokes in order: reveal wipe (pen head) → keep; pen star rides each straight segment
  const lens = g.map(st => polyRibbon(strokePts(st), GW, YG).len), total = lens.reduce((a, b) => a + b, 0);
  const T0 = 0.24, T1 = 0.72, GAP = 0.03, span = T1 - T0 - GAP * (g.length - 1);
  let t = T0;
  g.forEach((st, i) => {
    const d = Math.max(0.07, span * lens[i] / total), pts = strokePts(st), m = `g${k+1}s${i}`;
    L.push({ name:`G${i} Wipe`, mat:"wipe", mode:"mesh", mesh:m, fudge:10, life:d, size:1, bursts:[[t,1]], flip:{x:2,y:8}, color:[[0,C_WL],[1,C_LIL]] });
    L.push({ name:`G${i} Keep`, mat:"line", mode:"mesh", mesh:m, fudge:11, life:T_END - t - d, size:1, bursts:[[t + d,1]],
      color:[[0,C_WL],[0.4,C_LIL],[1,C_LIL]], alpha:[[0,1],[1 - 0.15/(T_END - t - d),1],[1,0]] });
    // pen: one moving star per straight segment (circles: orbit about the circle centre)
    if (st.pts) {
      let segL = []; for (let j = 1; j < pts.length; j++) segL.push(Math.hypot(pts[j][0]-pts[j-1][0], pts[j][1]-pts[j-1][1]));
      const sl = segL.reduce((a, b) => a + b, 0); let ts = t;
      segL.forEach((l, j) => { const dj = d * l / sl, a = pts[j], b = pts[j+1];
        L.push({ name:`G${i} Pen${j}`, mat:"star", fudge:-3, pos:[a[0], 0.1, a[1]], life:dj + 0.02, size:0.32, bursts:[[ts,1]], rot:[0,1], rotZ:5,
          vel:{ x:(b[0]-a[0])/dj, z:(b[1]-a[1])/dj } });
        ts += dj; });
    } else {
      const c = st.circle || st.arc, a0 = c[3], sp = st.circle ? TAU : Math.abs(c[4] - c[3]);
      L.push({ name:`G${i} PenArc`, mat:"star", fudge:-3, pos:[c[0]*GS, 0.1, c[1]*GS], shape:{type:"none", off:[Math.cos(a0)*c[2]*GS, 0, Math.sin(a0)*c[2]*GS]}, life:d, size:0.32,
        bursts:[[t,1]], rotZ:5, vel:{ orb:sp/d } });
    }
    L.push({ name:`G${i} Flecks`, mat:"dot", fudge:-4, pos:[pts[0][0], 0.08, pts[0][1]], life:[0.35,0.6], size:[0.04,0.08], bursts:[[t,6]], speed:[0.6,1.6],
      shape:{type:"sphere", r:0.05, th:1}, vel:{y:[0.4,1.0]}, drag:2, color:[[0,C_WL],[1,C_VIO]], alpha:[[0,1],[1,0]] });
    t += d + GAP;
  });
  // completion: glyph flare, small ring pulse, rising motes
  L.push({ name:"D30 GlyphFlare", mat:"lineHot", mode:"mesh", mesh:`glyph${k+1}`, fudge:8, life:0.35, size:1, bursts:[[0.74,1]],
    sizeLife:[[0,1],[1,1.05]], color:[[0,C_WL],[1,C_LIL]], alpha:[[0,0.9],[1,0]] });
  L.push({ name:"D31 PulseRing", mat:"ring", mode:"mesh", mesh:"quad", fudge:12, pos:[0,0.05,0], life:0.45, size:4.2, bursts:[[0.74,1]],
    sizeLife:[[0,0.55,0,1.2],[1,1]], color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0.8],[1,0]] });
  L.push({ name:"D32 Motes", mat:"dot", fudge:-5, life:[0.9,1.5], size:[0.04,0.09], shape:{type:"circle", r:1.45, th:1, y:0.05}, bursts:[[0.3,8],[0.6,10],[0.75,22]],
    vel:{y:[0.5,1.2]}, noise:{s:0.3, f:1.2, sp:0.6}, color:[[0,C_WL],[0.5,C_LIL],[1,C_VIO]], alpha:[[0,0],[0.15,1],[0.7,0.8],[1,0]] });
  L.push({ name:"D33 CoreSpark", mat:"star", fudge:-6, pos:[0,0.25,0], life:0.3, size:0.9, bursts:[[0.74,1]], rot:[0,0.8], sizeLife:[[0,0.3],[0.25,1],[1,0]] });
  return L;
}

// ---------------------------------------------------------------- HOLD (≤ 30 s): finished rune breathes softly
const HL = 30, HA = (a) => [[0,0],[0.15/HL,a],[1 - 0.15/HL,a],[1,0]];
function holdLayers(k) { return [
  { name:"H01 InkStain", mat:"inkDecal", mode:"mesh", mesh:"quad", fudge:60, pos:[0,0.01,0], life:HL, size:3.9, rot3:[0,0.7*k,0], bursts:[[0,1]], alpha:HA(0.55), onStop:0.15 },
  { name:"H02 GroundGlow", mat:"glowFloor", mode:"mesh", mesh:"quad", fudge:55, pos:[0,0.015,0], life:HL, size:3.6, bursts:[[0,1]], color:[[0,C_VIO],[1,C_VIO]], alpha:HA(0.22), onStop:0.15 },
  { name:"H03 Rings", mat:"line", mode:"mesh", mesh:"rings", fudge:21, life:HL, size:1, bursts:[[0,1]], color:[[0,C_LIL],[1,C_LIL]], alpha:HA(0.95), onStop:0.15 },
  { name:"H04 Band", mat:"band", mode:"mesh", mesh:"band", fudge:22, life:HL, size:1, rot3:[0,0,0], rotLife:[0,-0.22,0], bursts:[[0,1]], alpha:HA(0.85), onStop:0.15 },
  { name:"H05 Glyph", mat:"line", mode:"mesh", mesh:`glyph${k+1}`, fudge:11, life:HL, size:1, bursts:[[0,1]], color:[[0,C_LIL],[1,C_LIL]], alpha:HA(1), onStop:0.15 },
  { name:"H06 GlyphPulse", mat:"lineHot", mode:"mesh", mesh:`glyph${k+1}`, fudge:9, life:1.7, size:1, rate:1/1.7, bursts:[[0.4,1]], sizeLife:[[0,1],[1,1.03]],
    color:[[0,C_WL],[1,C_LIL]], alpha:TRI(0.55), onStop:0.1 },
  { name:"H07 RingPulse", mat:"lineHot", mode:"mesh", mesh:"rings", fudge:19, life:1.7, size:1, rate:1/1.7, bursts:[[0.4,1]], color:[[0,C_LIL],[1,C_VIO]], alpha:TRI(0.4), onStop:0.1 },
  { name:"H08 Motes", mat:"dot", fudge:-5, life:[1.4,2.2], size:[0.035,0.08], shape:{type:"circle", r:1.45, th:1, y:0.05}, rate:7, bursts:[[0,4]],
    vel:{y:[0.35,0.8]}, noise:{s:0.25, f:1.2, sp:0.5}, color:[[0,C_WL],[0.5,C_LIL],[1,C_VIO]], alpha:[[0,0],[0.2,0.9],[0.7,0.7],[1,0]], onStop:0.2 },
  { name:"H09 InkWisps", mat:"inkMist", fudge:40, life:[1.6,2.2], size:[0.6,1.0], shape:{type:"circle", r:1.5, th:0.2, y:0.15}, rate:2.2, bursts:[[0,2]],
    vel:{y:[0.12,0.3], orb:[-0.25,-0.1]}, rot:[0,TAU], rotZ:[-0.3,0.3], alpha:[[0,0],[0.3,0.4],[1,0]], flip:{x:4,y:4}, onStop:0.3 },
  { name:"H10 RimTwinkle", mat:"star", fudge:-6, life:[0.35,0.6], size:[0.12,0.24], shape:{type:"circle", r:1.48, th:0, y:0.06}, rate:3.5, rot:[0,0.8],
    sizeLife:[[0,0.2],[0.3,1],[1,0]] },
]; }

// ---------------------------------------------------------------- ERASE (1.5 s): light dies from the rim inward, rune breaks into ink flakes
function eraseLayers(k) { const gm = `glyph${k+1}`; return [
  { name:"E01 InkStainFade", mat:"inkDecal", mode:"mesh", mesh:"quad", fudge:60, pos:[0,0.01,0], life:1.7, size:3.9, rot3:[0,0.7*k,0], bursts:[[0,1]],
    sizeLife:[[0,1],[1,0.55]], alpha:[[0,0.55],[0.6,0.6],[1,0]] },
  { name:"E02 GroundGlowFade", mat:"glowFloor", mode:"mesh", mesh:"quad", fudge:55, pos:[0,0.015,0], life:1.5, size:3.6, bursts:[[0,1]], sizeLife:[[0,1],[1,0.3]],
    color:[[0,C_VIO],[1,C_VIO]], alpha:[[0,0.25],[1,0]] },
  // rim first: hairline + outer rings flare then go out (0 → 0.5 s), band flares off (hides its rotation phase)
  { name:"E03 RingsOut", mat:"line", mode:"mesh", mesh:"rings", fudge:21, life:0.55, size:1, bursts:[[0,1]], sizeLife:[[0,1],[1,0.97]],
    color:[[0,C_WL],[0.4,C_LIL],[1,C_VIO]], alpha:[[0,1],[0.3,1],[1,0]] },
  { name:"E04 RingsInk", mat:"inkLine", mode:"mesh", mesh:"rings", fudge:23, life:0.8, size:1, bursts:[[0.15,1]], sizeLife:[[0,0.98],[1,0.9]],
    alpha:[[0,0],[0.3,0.75],[1,0]] },
  { name:"E05 BandOut", mat:"band", mode:"mesh", mesh:"band", fudge:22, life:0.7, size:1, bursts:[[0,1]], rotLife:[0,-0.8,0], sizeLife:[[0,1],[1,0.9]],
    color:[[0,C_WL],[1,C_VIO]], alpha:[[0,1],[0.25,0.9],[1,0]] },
  // glyph: stays lit while the rim dies, then shrinks to the centre and turns to ink
  { name:"E06 GlyphOut", mat:"line", mode:"mesh", mesh:gm, fudge:11, life:1.45, size:1, bursts:[[0,1]], sizeLife:[[0,1],[0.35,0.98],[1,0.35,-1.2,0]],
    color:[[0,C_LIL],[0.5,C_VIO],[1,C_SHD]], alpha:[[0,1],[0.35,1],[0.85,0.35],[1,0]] },
  { name:"E07 GlyphInk", mat:"inkLineWide", mode:"mesh", mesh:gm, fudge:-1, life:1.15, size:1, bursts:[[0.35,1]], sizeLife:[[0,0.98],[1,0.3,-1.0,0]],
    alpha:[[0,0],[0.25,0.85],[0.75,0.7],[1,0]] },
  { name:"E08 StepFlare", mat:"lineHot", mode:"mesh", mesh:gm, fudge:9, life:0.4, size:1, bursts:[[0,1]], color:[[0,C_WL],[1,C_LIL]], alpha:[[0,0.8],[1,0]] },
  // contracting light rings (outside → in)
  { name:"E09 Contract", mat:"ring", mode:"mesh", mesh:"quad", fudge:14, pos:[0,0.05,0], life:1.4, size:3.8, bursts:[[0.05,1]],
    sizeLife:[[0,1,0,-0.6],[1,0.06]], color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0],[0.1,0.85],[0.8,0.7],[1,0]] },
  { name:"E10 Contract2", mat:"ring", mode:"mesh", mesh:"quad", fudge:14, pos:[0,0.06,0], life:1.0, size:3.0, bursts:[[0.45,1]],
    sizeLife:[[0,1,0,-0.8],[1,0.05]], color:[[0,C_WL],[1,C_VIO]], alpha:[[0,0],[0.15,0.6],[1,0]] },
  // ink flakes peel off in rings that close in on the centre
  ...[[0.08,1.45,12],[0.4,1.1,10],[0.75,0.75,9],[1.05,0.42,8],[1.35,0.12,14]].map(([t0, r, n], i) => (
    { name:`E2${i} Flakes`, mat:"shard", fudge:30, life:[0.6,1.0], size:[0.12,0.24], shape:{type:"circle", r:r, th:0.15, y:0.08}, bursts:[[t0,n]],
      speed:[0.3,0.9], vel:{y:[1.0,2.0]}, gravity:0.35, drag:1.2, rot:[0,TAU], rotZ:[-7,7], alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,0.6],[0.2,1],[1,0.6]] })),
  ...[[0.1,1.4,5],[0.55,0.9,4],[1.0,0.45,4]].map(([t0, r, n], i) => (
    { name:`E3${i} InkPuff`, mat:"inkMist", fudge:40, life:[0.8,1.1], size:[0.6,1.0], shape:{type:"circle", r:r, th:0.2, y:0.2}, bursts:[[t0,n]],
      vel:{y:[0.3,0.6], rad:[-0.4,-0.2]}, rot:[0,TAU], alpha:[[0,0],[0.2,0.6],[1,0]], flip:{x:4,y:4} })),
  { name:"E40 RisingMotes", mat:"dot", fudge:-5, life:[0.8,1.3], size:[0.04,0.08], shape:{type:"circle", r:1.4, th:1, y:0.05}, bursts:[[0,14],[0.3,10],[0.6,8],[0.9,6]],
    vel:{y:[0.8,1.6], rad:[-0.6,-0.2]}, noise:{s:0.3, f:1.2, sp:0.6}, color:[[0,C_WL],[1,C_VIO]], alpha:[[0,0],[0.15,1],[1,0]] },
  // end (1.45 s): last spark pops at the centre and drifts up as dust
  { name:"E50 EndSpark", mat:"star", fudge:-6, pos:[0,0.2,0], life:0.28, size:0.75, bursts:[[1.42,1]], rot:[0,0.8], sizeLife:[[0,0.3],[0.25,1],[1,0]] },
  { name:"E51 EndDust", mat:"dot", fudge:-5, life:[0.9,1.5], size:[0.04,0.08], shape:{type:"sphere", r:0.2, th:1, y:0.15}, bursts:[[1.42,22]], speed:[0.3,1.0],
    vel:{y:[0.6,1.4]}, noise:{s:0.4, f:1.2, sp:0.6}, color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,1],[0.7,0.7],[1,0]] },
  { name:"E52 EndShards", mat:"shard", fudge:30, life:[0.6,0.9], size:[0.1,0.2], shape:{type:"sphere", r:0.15, th:1, y:0.12}, bursts:[[1.42,10]], speed:[1.2,2.4],
    vel:{y:[1.2,2.0]}, gravity:0.8, rot:[0,TAU], rotZ:[-8,8], alpha:[[0,1],[1,0]] },
]; }

// ---------------------------------------------------------------- WRONG (0.9 s): rune twists black, short violet cracks spit out (no red)
function wrongLayers(k) { const gm = `glyph${k+1}`; const WOB = [[0,1],[0.07,1.12],[0.14,0.93],[0.22,1.07],[0.3,0.96],[0.45,1.02],[1,1]]; return [
  { name:"W01 DarkGlyphA", mat:"inkLineWide", mode:"mesh", mesh:gm, fudge:-1, life:0.9, size:1, rot3:[0,0.05,0], rotLife:[0,-0.12,0], bursts:[[0,1]], sizeLife:WOB,
    alpha:[[0,0],[0.05,0.95],[0.6,0.85],[1,0]] },
  { name:"W02 DarkGlyphB", mat:"inkLineWide", mode:"mesh", mesh:gm, fudge:-1, life:0.75, size:1.06, rot3:[0,-0.09,0], rotLife:[0,0.2,0], bursts:[[0.02,1]],
    sizeLife:[[0,0.92],[0.1,1.05],[0.2,0.97],[0.35,1.03],[1,1]], alpha:[[0,0],[0.08,0.6],[1,0]] },
  { name:"W03 DarkRings", mat:"inkLineWide", mode:"mesh", mesh:"rings", fudge:-1, life:0.8, size:1, bursts:[[0,1]], sizeLife:[[0,1],[0.1,1.05],[0.25,0.97],[1,1]],
    alpha:[[0,0],[0.08,0.8],[0.6,0.6],[1,0]] },
  { name:"W03b Shroud", mat:"inkDecal", mode:"mesh", mesh:"quad", fudge:0, pos:[0,0.05,0], life:0.85, size:3.6, rot3:[0,[0,TAU],0], bursts:[[0,1]],
    sizeLife:[[0,0.6,0,3],[0.2,1.05],[1,1]], alpha:[[0,0],[0.06,0.7],[0.55,0.55],[1,0]] },
  { name:"W03c ShroudMist", mat:"inkMist", fudge:-2, life:[0.6,0.85], size:[1.0,1.5], shape:{type:"circle", r:0.9, th:1, y:0.15}, bursts:[[0,7]],
    vel:{y:[0.1,0.3], orb:[-1.2,-0.6]}, rot:[0,TAU], alpha:[[0,0],[0.1,0.75],[1,0]], flip:{x:4,y:4} },
  { name:"W04 Flicker", mat:"lineHot", mode:"mesh", mesh:gm, fudge:10, life:0.6, size:1, bursts:[[0,1]], color:[[0,C_LIL],[1,C_VIO]],
    alpha:[[0,0.9],[0.08,0.1],[0.16,0.8],[0.26,0],[0.38,0.55],[0.5,0],[1,0]] },
  { name:"W05 CrackDecal", mat:"crack", mode:"mesh", mesh:"quad", fudge:-3, pos:[0,0.045,0], life:0.6, size:3.4, rot3:[0,[0,TAU],0], bursts:[[0.02,1]],
    sizeLife:[[0,0.55,0,3],[0.25,1]], color:[[0,C_LIL],[0.3,C_VIO],[1,C_VIO]], alpha:[[0,0.9],[0.5,0.6],[1,0]] },
  { name:"W06 Scratch", mat:"scratch", mode:"mesh", mesh:"quad", fudge:7, pos:[0,0.07,0], life:0.38, size:2.3, rot3:[0,[0,TAU],0], bursts:[[0.05,1]],
    sizeLife:[[0,0.8],[1,1.08]], color:[[0,C_WL],[1,C_VIO]], alpha:[[0,1],[0.4,0.8],[1,0]] },
  { name:"W07 CrackSparks", mat:"streak", mode:"stretch", vs:0.05, ls:1.2, fudge:-4, life:[0.18,0.3], size:[0.04,0.08], shape:{type:"circle", r:0.25, th:1, y:0.08},
    bursts:[[0.02,16],[0.12,8]], speed:[4,7.5], vel:{y:[0.2,1.2]}, drag:4, color:[[0,C_WL],[1,C_LIL]], alpha:[[0,1],[1,0]] },
  { name:"W08 ShockRing", mat:"ring", mode:"mesh", mesh:"quad", fudge:14, pos:[0,0.06,0], life:0.35, size:4.0, bursts:[[0.02,1]], sizeLife:[[0,0.25,0,2.5],[1,1]],
    color:[[0,C_LIL],[1,C_VIO]], alpha:[[0,0.75],[1,0]] },
  { name:"W09 InkSpit", mat:"shard", fudge:30, life:[0.4,0.7], size:[0.12,0.22], shape:{type:"circle", r:0.9, th:1, y:0.08}, bursts:[[0.03,14]], speed:[1.5,3],
    vel:{y:[1.5,3]}, gravity:1.0, rot:[0,TAU], rotZ:[-9,9], alpha:[[0,1],[1,0]] },
  { name:"W10 InkPuff", mat:"inkMist", fudge:40, life:[0.6,0.9], size:[0.8,1.3], shape:{type:"circle", r:0.8, th:1, y:0.25}, bursts:[[0.02,6]], speed:[0.3,0.8],
    vel:{y:[0.3,0.6]}, rot:[0,TAU], alpha:[[0,0],[0.15,0.7],[1,0]], flip:{x:4,y:4} },
  { name:"W11 Flash", mat:"star", fudge:-6, pos:[0,0.2,0], life:0.18, size:1.1, bursts:[[0.02,1]], rot:[0,0.8], color:[[0,C_LIL],[1,C_VIO]], sizeLife:[[0,0.4],[0.3,1],[1,0]] },
]; }

const MATS = {
  line:{tex:"T_Line", tint:[1.6,1.35,2.0], add:true},
  lineHot:{tex:"T_Line", tint:[2.2,1.9,2.6], add:true},
  wipe:{tex:"T_LineWipe", tint:[2.0,1.7,2.5], add:true},
  band:{tex:"T_RuneStrip", tint:[1.4,1.15,1.9], add:true},
  inkLine:{tex:"T_Line", tint:[0.05,0.035,0.08], add:false},
  inkLineWide:{tex:"T_InkLine", tint:[1,1,1], add:false},
  inkDecal:{tex:"T_InkSplat", tint:[1,1,1], add:false},
  glowFloor:{tex:"T_Glow", tint:[1.2,1.0,1.6], add:true},
  inkMist:{tex:"T_InkMist_Sheet", tint:[1,1,1], add:false},
  shard:{tex:"T_InkShard", tint:[2.0,1.8,2.4], add:false},
  star:{tex:"T_Star", tint:[2.2,1.9,2.6], add:true},
  dot:{tex:"T_Dot", tint:[2.0,1.7,2.4], add:true},
  ring:{tex:"T_Ring", tint:[1.6,1.3,2.1], add:true},
  crack:{tex:"T_Crack", tint:[1.8,1.5,2.3], add:true},
  scratch:{tex:"T_Scratch", tint:[1.7,1.4,2.2], add:true},
  streak:{tex:"T_Streak", tint:[2.0,1.7,2.5], add:true},
};

const SYS = [];
for (let k = 0; k < 5; k++) {
  SYS.push({ id:`draw${k+1}`, role:"static", loop:false, duration:0.85, layers:drawLayers(k) });
  SYS.push({ id:`hold${k+1}`, role:"static", loop:false, duration:HL, layers:holdLayers(k) });
  SYS.push({ id:`erase${k+1}`, role:"static", loop:false, duration:1.5, layers:eraseLayers(k) });
  SYS.push({ id:`wrong${k+1}`, role:"static", loop:false, duration:0.9, layers:wrongLayers(k) });
}
// demo
const H0 = { path:[[0,[0,0,0],0]], hidden:true };
const walker = (tIn, at) => ({ path:[[0,[at[0], 0, at[2] - 4], 0],[tIn - 0.6,[at[0], 0, at[2] - 4], 0],[tIn,[at[0] + 0.2, 0, at[2] - 0.3], 0],[tIn + 2.5,[at[0] + 0.2, 0, at[2] - 0.3], 0],[tIn + 3.2,[at[0] + 1.5, 0, at[2] + 3], 0]] });
const PENT = [0,1,2,3,4].map(i => { const a = Math.PI/2 + i / 5 * TAU; return [Math.cos(a) * 4.2, 0, Math.sin(a) * 4.2]; });
function single(k) { return { label:`룬 ${k}`, interval:5.2, actors:[H0, walker(3.0, [0,0,0])],
  events:[{t:0, system:`draw${k}`, actor:0}, {t:0.8, system:`hold${k}`, actor:0}, {t:3.0, stop:[`hold${k}`]}, {t:3.0, system:`erase${k}`, actor:0}],
  camera:{ target:[0,0.2,0], dist:5.8, yaw:-90, pitch:64 } }; }
const SCENE = {
  name:"MordianRitualRune",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.65, tint:[0.9,0.8,1.0] }, tonemapping:"neutral",
  systems:SYS,
  demo:{
    interval:5.2, bg:[0.12,0.11,0.14], floor:[0.2,0.19,0.22], grid:[0.17,0.16,0.19],
    scenarios:{
      r1:single(1), r2:single(2), r3:single(3), r4:single(4), r5:single(5),
      wrong:{ label:"틀린 순서 (룬 4)", interval:4.6, actors:[H0, walker(2.0, [0,0,0])],
        events:[{t:0, system:"draw4", actor:0}, {t:0.8, system:"hold4", actor:0}, {t:2.0, system:"wrong4", actor:0}],
        camera:{ target:[0,0.2,0], dist:5.8, yaw:-90, pitch:64 } },
      all:{ label:"5개 그리기 → 역순 해제", interval:16,
        actors:PENT.map(p => ({ path:[[0, p, 0]], hidden:true })),
        events:[
          ...PENT.map((p, i) => ({ t:i * 1.0, system:`draw${i+1}`, actor:i })),
          ...PENT.map((p, i) => ({ t:i * 1.0 + 0.8, system:`hold${i+1}`, actor:i })),
          { t:5.6, system:"wrong2", actor:1 },
          ...[4,3,2,1,0].flatMap((i, j) => [{ t:6.6 + j * 1.8, stop:[`hold${i+1}`] }, { t:6.6 + j * 1.8, system:`erase${i+1}`, actor:i }]),
        ],
        camera:{ target:[0,0.2,0], dist:13, yaw:-90, pitch:55 } },
    },
  },
};
