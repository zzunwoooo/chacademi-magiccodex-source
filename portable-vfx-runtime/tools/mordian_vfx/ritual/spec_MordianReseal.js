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
// MordianReseal — final "재봉인" (5 s, synced with the boss `sealed` motion; boss death is judged at 5.0).
// P = boss centre on the ground, +Z = server facing (boss front). The archmage's seal: cold silver-white / pale gold light pressing down the violet shadow.
// 0–0.6 giant seal circle (r 8 m) drawn · 0.3–2.4 light chains from the ground hold the boss · ink torn from the body · 3.2–4.4 ink vortex sucked to the centre +
// converging light rings · 4.4 small seal flash + light pillar + outward waves · → 5.0 circle cools away · a small light stays where the mask fell (+Z 1.3 m).
const Y0 = 0.035, BC = [0, 3.8, 0];
const OCTA = polygram(8, 3, 6.5, Math.PI/8), STAR = polygram(8, 2, 3.2, 0);
var EXTRA_MESHES = {
  ro1: cleanMesh(ringMesh(8.0, 0.16, Y0, Math.PI/2, Math.PI)),
  ro2: cleanMesh(ringMesh(8.0, 0.16, Y0, -Math.PI/2, Math.PI)),
  r2: cleanMesh(polyRibbon(arcPts(0, 0, 7.7, 0, -TAU, 200), 0.06, Y0)),
  r3: cleanMesh(ringMesh(6.5, 0.11, Y0, -Math.PI/2)),
  r4: cleanMesh(ringMesh(3.2, 0.08, Y0, Math.PI/2)),
  r5: cleanMesh(polyRibbon(arcPts(0, 0, 2.25, 0, -TAU, 120), 0.06, Y0)),
  rings: merge([ringMesh(8.0, 0.16, Y0), ringMesh(7.7, 0.06, Y0), ringMesh(8.3, 0.03, Y0), ringMesh(6.5, 0.11, Y0), ringMesh(3.2, 0.08, Y0), ringMesh(2.25, 0.06, Y0)].map(cleanMesh)),
  band: runeBand(6.62, 7.58, 24, Y0 - 0.003),
  octa: merge([...OCTA.segs.map(s => polyRibbon([s[0], s[1]], 0.08, Y0 + 0.002)), ...OCTA.v.map(v => polyRibbon(arcPts(v[0], v[1], 0.5, 0, TAU, 32), 0.05, Y0 + 0.003)),
               ...OCTA.v.map(v => polyRibbon(arcPts(v[0], v[1], 0.32, 0, TAU, 24), 0.03, Y0 + 0.003))].map(cleanMesh)),
  inner: merge([...STAR.segs.map(s => polyRibbon([s[0], s[1]], 0.06, Y0 + 0.002)), runeBand(2.32, 3.08, 16, Y0)].map(cleanMesh)),
  unitRing: cleanMesh(ringMesh(1.0, 0.035, 0, 0)),
  pillar: crossV(1, 0.1, 2),
  pillarWide: crossV(1, 0.28, 2),
};
OCTA.segs.forEach((s, i) => { EXTRA_MESHES[`ol${i}`] = cleanMesh(polyRibbon([s[0], s[1]], 0.08, Y0 + 0.002)); });
// light chains: ground anchor (r 6, angle a) → boss body (r 0.7, height h); two crossed ribbons, U 0 at the ground
const CH = [[15, 3.0], [75, 4.3], [135, 2.5], [195, 3.9], [255, 4.7], [315, 3.3]].map(([deg, h]) => {
  const a = deg * D2R, P0 = [6.0*Math.cos(a), 0.05, 6.0*Math.sin(a)], P2 = [0.75*Math.cos(a), h, 0.75*Math.sin(a)];
  const C1 = add(mul(add(P0, P2), 0.5), [0, 0.7, 0]);
  const f = s => add(add(mul(P0, (1-s)*(1-s)), mul(C1, 2*s*(1-s))), mul(P2, s*s));
  const tan = s => norm(add(mul(sub(C1, P0), 2*(1-s)), mul(sub(P2, C1), 2*s)));
  const side = [-Math.sin(a), 0, Math.cos(a)], up = s => norm(cross(tan(s), side));
  return { a, P0, P2, f };
});
CH.forEach((c, i) => {
  const side = [-Math.sin(c.a), 0, Math.cos(c.a)];
  const tan = s => norm(sub(c.f(Math.min(1, s + 0.01)), c.f(Math.max(0, s - 0.01))));
  EXTRA_MESHES[`chain${i}`] = merge([ribbon3(40, c.f, s => side, 0.46), ribbon3(40, c.f, s => norm(cross(tan(s), side)), 0.46)]);
  EXTRA_MESHES[`chainGlow${i}`] = merge([ribbon3(40, c.f, s => side, 0.7), ribbon3(40, c.f, s => norm(cross(tan(s), side)), 0.7)]);
});
// ink vortex: spiral ribbon from r 3.8 / y 2.6 down to the centre, head (U = 1) at the centre leads a +Y spin
EXTRA_MESHES.swirl = ribbon3(80, s => { const ph = -s * TAU * 1.3, r = 3.8 * (1 - s) + 0.2, y = 2.6 * (1 - s) ** 1.4 + 0.1; return [r*Math.cos(ph), y, r*Math.sin(ph)]; },
  s => [0, 1, 0], s => 0.2 + 1.3 * Math.sin(Math.PI * Math.min(1, s * 1.1)) ** 0.8);

// ---------------------------------------------------------------- circle: draw (0–0.6) → hold (rotating) → flare 4.4 → cool → gone 5.0
const L = [];
const DRAWN = [["ro1",0.0,0.3],["ro2",0.0,0.3],["r2",0.05,0.32],["r3",0.1,0.3],["r4",0.24,0.22],["r5",0.3,0.2]];
DRAWN.forEach(([m,t0,d], i) => {
  L.push({ name:`A0${i} Draw_${m}`, mat:"wipe", mode:"mesh", mesh:m, fudge:18, life:d, size:1, bursts:[[t0,1]], flip:{x:2,y:8}, color:[[0,C_WH],[1,C_SIL]] });
  L.push({ name:`A0${i} Keep_${m}`, mat:"line", mode:"mesh", mesh:m, fudge:18, life:0.66 - t0 - d, size:1, bursts:[[t0+d,1]], color:[[0,C_SIL],[1,C_SIL]], alpha:[[0,1],[0.7,1],[1,0]] });
});
OCTA.segs.forEach((s, i) => { const t0 = 0.14 + i * 0.03;
  L.push({ name:`A1${i} DrawLine`, mat:"wipe", mode:"mesh", mesh:`ol${i}`, fudge:17, life:0.14, size:1, bursts:[[t0,1]], flip:{x:2,y:8}, color:[[0,C_WH],[1,C_GLD]] });
  L.push({ name:`A1${i} KeepLine`, mat:"line", mode:"mesh", mesh:`ol${i}`, fudge:17, life:0.66 - t0 - 0.14, size:1, bursts:[[t0 + 0.14,1]], color:[[0,C_GLD],[1,C_GLD]], alpha:[[0,1],[0.7,1],[1,0]] });
});
L.push({ name:"A20 DrawPenA", mat:"star", fudge:-3, pos:[0,0.15,0], life:0.3, size:1.0, shape:{type:"none", off:[0,0,8]}, bursts:[[0,1]], vel:{orb:Math.PI/0.3}, rotZ:6 });
L.push({ name:"A21 DrawPenB", mat:"star", fudge:-3, pos:[0,0.15,0], life:0.3, size:1.0, shape:{type:"none", off:[0,0,-8]}, bursts:[[0,1]], vel:{orb:Math.PI/0.3}, rotZ:6 });
L.push({ name:"A22 DrawSparks", mat:"dot", fudge:-4, life:[0.4,0.8], size:[0.06,0.12], shape:{type:"circle", r:8, th:0, y:0.05}, bursts:[[0.05,30],[0.18,30]],
  vel:{y:[0.8,2.2]}, drag:1, color:[[0,C_WH],[1,C_GLD]], alpha:[[0,1],[1,0]] });
// held groups [mesh, mat, ω, start, fudge, colour, size]
const HOLDG = [["rings","line",0,0.58,21,C_SIL,1], ["octa","line",-0.08,0.58,20,C_GLD,1], ["band","band",0.15,0.3,22,C_SIL,1], ["inner","band",0.32,0.36,19,C_GLD,1], ["quad","sigil",-0.35,0.4,16,C_SIL,4.6]];
const TF = 4.4, TE = 5.0;
HOLDG.forEach(([mesh, mat, w, t0, fudge, col, size], gi) => {
  const life = TE - t0, k = t => (t - t0) / life, base = mesh === "rings" || mesh === "octa" ? 0.95 : 0.85;
  L.push({ name:`H${gi} ${mesh}`, mat, mode:"mesh", mesh, fudge, pos:mesh === "quad" ? [0,Y0+0.005,0] : [0,0,0], life, size, rotLife:[0,w,0], bursts:[[t0,1]],
    sizeLife:mesh === "quad" || mesh === "band" || mesh === "inner" ? [[0,1.12,0,-1.2],[Math.min(0.9, 0.25/life),1],[1,1]] : [[0,1],[1,1]],
    color:[[0,col],[k(3.2),col],[k(TF),C_WH],[k(4.75),C_GLD],[1,C_GLD]],
    alpha:[[0,0],[0.08/life,base],[k(3.2),base * 0.85],[k(TF),1],[k(4.75),0.45],[1,0]] });
  if (w !== 0 || mesh === "rings") // flare copy at 4.4, rotation lined up with the held copy
    L.push({ name:`F${gi} ${mesh}_flare`, mat:mat === "line" ? "lineHot" : mat, mode:"mesh", mesh, fudge:fudge - 6, pos:mesh === "quad" ? [0,Y0+0.008,0] : [0,0.004,0], life:0.55, size,
      rot3:[0, w * (TF - t0), 0], rotLife:[0,w,0], bursts:[[TF,1]], sizeLife:[[0,1],[1,mesh === "quad" ? 1.15 : 1.02]], color:[[0,C_WH],[1,C_GLD]], alpha:[[0,0.9],[1,0]] });
});
// the ground answers at 4.4: pillars on the 8 octagram vertices (vertex angle − held rotation)
const thO = -0.08 * (TF - 0.58);
OCTA.v.forEach((v, i) => { const a = Math.atan2(v[1], v[0]) - thO, r = 6.5, p = [r*Math.cos(a), 0.02, r*Math.sin(a)];
  L.push({ name:`V${i} VertexPillar`, mat:"pillar", mode:"mesh", mesh:"pillar", fudge:5, pos:p, life:0.55, size:4.5, bursts:[[TF + 0.02 * (i % 2),1]],
    sizeLife:[[0,0.2,0,6],[0.15,1],[1,0.8]], color:[[0,C_WH],[1,C_GLD]], alpha:[[0,1],[1,0]] });
});
L.push({ name:"V10 VertexStars", mat:"star", fudge:-6, life:0.35, size:1.1, shape:{type:"circle", r:6.5, th:0, y:0.3}, bursts:[[TF,8]], rot:[0,0.8], sizeLife:[[0,0.3],[0.25,1],[1,0]] });

// base / dark accents: violet shadow pool under the boss, shrinking as it is sealed; dark mist
L.push({ name:"B01 ShadowPool", mat:"inkDecal", mode:"mesh", mesh:"quad", fudge:60, pos:[0,0.012,0], life:4.5, size:6.5, rot3:[0,0.6,0], bursts:[[0,1]],
  sizeLife:[[0,1],[3.1/4.5,0.95],[4.35/4.5,0.08,-2,0],[1,0.05]], alpha:[[0,0.7],[0.95,0.7],[1,0]] });
L.push({ name:"B02 ShadowGlow", mat:"vioGlow", mode:"mesh", mesh:"quad", fudge:58, pos:[0,0.018,0], life:4.4, size:6.0, bursts:[[0,1]],
  sizeLife:[[0,1],[3.1/4.4,0.9],[1,0.1]], color:[[0,C_VIO],[0.6,C_VIO],[1,C_SHD]], alpha:[[0,0.45],[0.5,0.3],[1,0]] });
L.push({ name:"B03 SealFloor", mat:"goldGlow", mode:"mesh", mesh:"quad", fudge:57, pos:[0,0.016,0], life:4.6, size:17, bursts:[[0.2,1]],
  color:[[0,C_SIL],[0.85,C_GLD],[1,C_GLD]], alpha:[[0,0],[0.1,0.12],[0.6,0.14],[0.9,0.22],[1,0]] });
L.push({ name:"B04 ShadowMist", mat:"inkMist", fudge:3, life:[1.2,1.8], size:[1.6,2.6], shape:{type:"circle", r:2.6, th:1, y:0.4}, bursts:[[0,6],[0.6,5],[1.3,5],[2.0,5],[2.7,4]],
  vel:{y:[0.2,0.6], orb:[0.3,0.6]}, rot:[0,TAU], rotZ:[-0.4,0.4], alpha:[[0,0],[0.3,0.6],[1,0]], flip:{x:4,y:4} });
L.push({ name:"B05 Motes", mat:"dot", fudge:-5, life:[1.2,2.0], size:[0.05,0.11], shape:{type:"circle", r:7.8, th:1, y:0.05}, bursts:[[0.4,40],[1.2,30],[2.0,30],[2.8,30]],
  vel:{y:[0.6,1.6]}, noise:{s:0.4, f:0.8, sp:0.6}, color:[[0,C_WH],[0.5,C_SIL],[1,C_GLD]], alpha:[[0,0],[0.15,1],[0.7,0.7],[1,0]] });

L.push({ name:"B06 ShadowWisps", mat:"vioDot", fudge:-4, life:[0.8,1.3], size:[0.06,0.13], shape:{type:"circle", r:2.6, th:1, y:0.1}, rate:0, bursts:[[0,30],[0.5,24],[1.0,20],[1.6,16],[2.2,12],[2.8,8]],
  vel:{y:[0.8,2.0], orb:[0.4,0.8]}, noise:{s:0.4, f:1, sp:0.6}, color:[[0,C_LIL],[0.5,C_VIO],[1,C_SHD]], alpha:[[0,0],[0.15,1],[1,0]] });
// chains (0.3–2.4): a light trace shoots up, the chain appears and strains with the struggle, then dissolves into gold dust
CH.forEach((c, i) => {
  const t0 = 0.3 + i * 0.07, tEnd = 2.42, life = tEnd - t0 - 0.1, kk = t => (t - (t0 + 0.1)) / life;
  L.push({ name:`K${i}0 ChainShoot`, mat:"wipe", mode:"mesh", mesh:`chain${i}`, fudge:9, life:0.26, size:1, bursts:[[t0,1]], flip:{x:2,y:8}, color:[[0,C_WH],[1,C_SIL]] });
  L.push({ name:`K${i}1 Chain`, mat:"chain", mode:"mesh", mesh:`chain${i}`, fudge:8, life, size:1, bursts:[[t0 + 0.1,1]],
    color:[[0,C_WH],[0.5,C_SIL],[1,C_GLD]],
    alpha:[[0,0],[0.06,1],[kk(0.9),0.85],[kk(0.95),1],[kk(1.5),0.8],[kk(1.55),1],[kk(2.0),0.85],[kk(2.05),1],[0.93,1],[1,0]] });
  L.push({ name:`K${i}2 ChainGlow`, mat:"lineSoft", mode:"mesh", mesh:`chainGlow${i}`, fudge:10, life, size:1, bursts:[[t0 + 0.1,1]],
    color:[[0,C_SIL],[1,C_GLD]], alpha:[[0,0],[0.06,0.18],[0.93,0.18],[1,0]] });
  L.push({ name:`K${i}3 Strain`, mat:"lineHot", mode:"mesh", mesh:`chainGlow${i}`, fudge:7, life:0.22, size:1, bursts:[[0.9,1],[1.5,1],[2.0,1]],
    color:[[0,C_WH],[1,C_GLD]], alpha:[[0,0.4],[1,0]] });
  L.push({ name:`K${i}4 Anchor`, mat:"ring", mode:"mesh", mesh:"quad", fudge:13, pos:[c.P0[0], 0.06, c.P0[2]], life:0.45, size:2.2, bursts:[[t0,1]],
    sizeLife:[[0,0.2,0,3],[1,1]], color:[[0,C_WH],[1,C_GLD]], alpha:[[0,0.9],[1,0]] });
  L.push({ name:`K${i}5 AnchorStar`, mat:"star", fudge:-6, pos:[c.P0[0], 0.3, c.P0[2]], life:0.3, size:1.4, bursts:[[t0,1]], rot:[0,0.8], sizeLife:[[0,0.4],[0.2,1],[1,0]] });
  L.push({ name:`K${i}6 GripSparks`, mat:"dot", fudge:-5, pos:c.P2, life:[0.4,0.7], size:[0.06,0.12], shape:{type:"sphere", r:0.3, th:1}, bursts:[[t0 + 0.28,10],[0.9,6],[1.5,6],[2.0,6]],
    speed:[1.5,3.5], drag:2.5, color:[[0,C_WH],[1,C_GLD]], alpha:[[0,1],[1,0]] });
  [0.25, 0.55, 0.85].forEach((s, j) => { const p = c.f(s);
    L.push({ name:`K${i}7${j} Dissolve`, mat:"dot", fudge:-5, pos:p, life:[0.6,1.1], size:[0.05,0.11], shape:{type:"sphere", r:0.35, th:1}, bursts:[[2.32,9]],
      speed:[0.3,1.0], vel:{y:[-1.5,-0.4]}, noise:{s:0.4, f:1, sp:0.6}, color:[[0,C_WH],[1,C_GLD]], alpha:[[0,1],[1,0]] }); });
});

// ink torn from the body (struggle beats), dark shards with violet rims; violet sparks that die out
const BEATS = [[0.45,14],[0.9,20],[1.5,22],[2.0,20],[2.6,14],[3.0,10]];
L.push({ name:"I01 InkTorn", mat:"shard", fudge:2, pos:BC, life:[0.8,1.4], size:[0.35,0.8], shape:{type:"box", sx:1.6, sy:3.8, sz:1.4}, bursts:BEATS.map(([t,n]) => [t,n]),
  speed:[0,0.5], vel:{rad:[2.5,5.0], y:[0.5,2.0]}, gravity:0.35, drag:0.9, rot:[0,TAU], rotZ:[-7,7], alpha:[[0,1],[0.8,0.9],[1,0]], sizeLife:[[0,0.6],[0.15,1],[1,0.7]] });
L.push({ name:"I02 InkTornMist", mat:"inkMist", fudge:3, pos:BC, life:[0.8,1.2], size:[1.4,2.4], shape:{type:"box", sx:1.4, sy:3.4, sz:1.2}, bursts:BEATS.map(([t,n]) => [t, Math.round(n/3)]),
  vel:{rad:[1.2,2.2], y:[0.2,0.6]}, drag:1.2, rot:[0,TAU], alpha:[[0,0],[0.15,0.85],[1,0]], flip:{x:4,y:4} });
L.push({ name:"I03 VioletSparks", mat:"vioDot", fudge:-4, pos:BC, life:[0.4,0.7], size:[0.06,0.12], shape:{type:"box", sx:1.6, sy:3.6, sz:1.4}, bursts:BEATS.map(([t,n]) => [t, n]),
  vel:{rad:[1.5,3.5]}, drag:2, color:[[0,C_LIL],[1,C_SHD]], alpha:[[0,1],[1,0]] });

// 3.2–4.4: the boss is sucked in — ink vortex to the centre, converging light rings
[3.2, 3.4, 3.6, 3.8].forEach((t, i) => L.push({ name:`X0${i} Swirl`, mat:"inkStroke", mode:"mesh", mesh:"swirl", fudge:1, life:4.45 - t, size:1, rot3:[0, i * Math.PI / 2, 0],
  rotLife:[0, 2.6 + i * 0.5, 0], bursts:[[t,1]], sizeLife:[[0,1.15],[1,0.25,-0.5,0]], alpha:[[0,0],[0.15,0.9],[0.8,0.85],[1,0]] }));
L.push({ name:"X10 InkVortex", mat:"shard", fudge:1, life:[0.8,1.1], size:[0.18,0.36], shape:{type:"circle", r:4.2, th:0.5, y:2.2}, bursts:[[3.2,24],[3.45,30],[3.7,34],[3.95,26]],
  vel:{orb:[2.5,3.8], rad:[-4.5,-3.2], y:[-2.6,-1.4]}, rot:[0,TAU], rotZ:[-8,8], alpha:[[0,0],[0.15,1],[0.85,1],[1,0]], sizeLife:[[0,1],[1,0.4]] });
L.push({ name:"X11 InkVortexMist", mat:"inkMist", fudge:3, life:[0.8,1.0], size:[1.2,2.0], shape:{type:"circle", r:3.6, th:0.4, y:1.2}, bursts:[[3.2,8],[3.5,8],[3.8,6]],
  vel:{orb:[1.8,2.6], rad:[-3.5,-2.5], y:[-1.0,-0.4]}, rot:[0,TAU], alpha:[[0,0],[0.2,0.75],[1,0]], sizeLife:[[0,1],[1,0.4]], flip:{x:4,y:4} });
[3.3, 3.6, 3.85, 4.05, 4.2].forEach((t, i) => {
  L.push({ name:`X2${i} ConvergeRing`, mat:"lineHot", mode:"mesh", mesh:"unitRing", fudge:12, pos:[0,0.07,0], life:TF - t, size:7.6 - 0.5 * i, rotLife:[0,1.5,0], bursts:[[t,1]],
    sizeLife:[[0,1,0,-0.4],[1,0.03]], color:[[0,C_SIL],[1,C_WH]], alpha:[[0,0],[0.15,0.6 + 0.08 * i],[1,0.9]] });
  L.push({ name:`X2${i} ConvergeHalo`, mat:"lineSoft", mode:"mesh", mesh:"unitRing", fudge:13, pos:[0,0.06,0], life:TF - t, size:7.6 - 0.5 * i, bursts:[[t,1]],
    sizeLife:[[0,1.03,0,-0.4],[1,0.04]], color:[[0,C_GLD],[1,C_GLD]], alpha:[[0,0],[0.2,0.5],[1,0.6]] });
});
[[2.0,3.35],[4.2,3.55],[5.6,3.75]].forEach(([y, t], i) => L.push({ name:`X3${i} BodyRing`, mat:"lineHot", mode:"mesh", mesh:"unitRing", fudge:11, pos:[0,y,0], life:TF - t, size:2.6 - 0.4 * i, rotLife:[0,-2,0],
  bursts:[[t,1]], vel:{y:-(y - 0.3) / (TF - t)}, sizeLife:[[0,1],[1,0.08]], color:[[0,C_SIL],[1,C_GLD]], alpha:[[0,0],[0.2,0.6],[1,0.7]] }));
L.push({ name:"X40 GoldInflow", mat:"dot", fudge:-5, life:[0.7,1.0], size:[0.06,0.12], shape:{type:"circle", r:7.5, th:0.6, y:0.2}, bursts:[[3.3,30],[3.6,40],[3.9,40]],
  vel:{rad:[-8,-6], orb:[0.6,1.0], y:[0,0.5]}, color:[[0,C_GLD],[1,C_WH]], alpha:[[0,0],[0.2,1],[0.9,1],[1,0]] });

// 4.4: seal complete — small short flash, light pillar, outward waves, rays
L.push({ name:"Z01 Flash", mat:"star", fudge:-9, pos:[0,0.9,0], life:0.24, size:3.4, bursts:[[TF,1]], rot:[0,0.6], sizeLife:[[0,0.4],[0.25,1],[1,0]] });
L.push({ name:"Z02 FlashGlow", mat:"goldGlow", fudge:-8, pos:[0,0.7,0], life:0.3, size:3.0, bursts:[[TF,1]], color:[[0,C_SIL],[1,C_GLD]], alpha:[[0,0.4],[1,0]] });
L.push({ name:"Z03 Pillar", mat:"pillar", mode:"mesh", mesh:"pillar", fudge:4, pos:[0,0,0], life:0.75, size:16, bursts:[[TF,1]], rotLife:[0,1.2,0],
  sizeLife:[[0,0.15,0,7],[0.12,1],[1,0.92]], color:[[0,C_WH],[0.5,C_SIL],[1,C_GLD]], alpha:[[0,1],[0.4,0.8],[1,0]] });
L.push({ name:"Z04 PillarWide", mat:"pillarSoft", mode:"mesh", mesh:"pillarWide", fudge:6, pos:[0,0,0], life:0.6, size:12, bursts:[[TF,1]],
  sizeLife:[[0,0.2,0,6],[0.15,1],[1,1]], color:[[0,C_SIL],[1,C_GLD]], alpha:[[0,0.5],[1,0]] });
L.push({ name:"Z05 Wave", mat:"lineHot", mode:"mesh", mesh:"unitRing", fudge:12, pos:[0,0.09,0], life:0.7, size:11, bursts:[[TF,1]], sizeLife:[[0,0.04,0,2.4],[1,1]],
  color:[[0,C_WH],[1,C_GLD]], alpha:[[0,0.9],[0.6,0.6],[1,0]] });
L.push({ name:"Z05b WaveSoft", mat:"ring", mode:"mesh", mesh:"quad", fudge:12, pos:[0,0.085,0], life:0.6, size:26, bursts:[[TF,1]], sizeLife:[[0,0.05,0,2.4],[1,1]],
  color:[[0,C_SIL],[1,C_GLD]], alpha:[[0,0.4],[1,0]] });
L.push({ name:"Z06 Wave2", mat:"line", mode:"mesh", mesh:"unitRing", fudge:12, pos:[0,0.1,0], life:0.85, size:13.5, bursts:[[TF + 0.12,1]], sizeLife:[[0,0.04,0,2.2],[1,1]],
  color:[[0,C_SIL],[1,C_GLD]], alpha:[[0,0.7],[1,0]] });
L.push({ name:"Z07 CircleWave", mat:"lineHot", mode:"mesh", mesh:"rings", fudge:13, pos:[0,0.006,0], life:0.6, size:1, bursts:[[TF + 0.04,1]], sizeLife:[[0,1],[1,1.35]],
  color:[[0,C_WH],[1,C_GLD]], alpha:[[0,0.7],[1,0]] });
L.push({ name:"Z08 Rays", mat:"streak", mode:"stretch", vs:0.06, ls:4, fudge:-7, life:[0.35,0.55], size:[0.12,0.25], shape:{type:"circle", r:0.6, th:1, y:0.3}, bursts:[[TF,16]],
  vel:{y:[18,30]}, color:[[0,C_WH],[1,C_GLD]], alpha:[[0,1],[0.6,0.7],[1,0]] });
L.push({ name:"Z09 GroundRays", mat:"streak", mode:"stretch", vs:0.04, ls:3, fudge:-7, life:[0.3,0.45], size:[0.12,0.22], shape:{type:"circle", r:0.8, th:1, y:0.12}, bursts:[[TF,24]],
  speed:[16,26], color:[[0,C_WH],[1,C_GLD]], alpha:[[0,1],[1,0]] });
L.push({ name:"Z10 RisingDust", mat:"dot", fudge:-5, life:[1.0,1.6], size:[0.05,0.11], shape:{type:"circle", r:7.5, th:1, y:0.05}, bursts:[[TF,70],[TF + 0.2,40]],
  vel:{y:[0.8,2.2]}, noise:{s:0.4, f:0.8, sp:0.6}, color:[[0,C_WH],[0.5,C_SIL],[1,C_GLD]], alpha:[[0,0],[0.1,1],[0.7,0.6],[1,0]] });
L.push({ name:"Z11 InkLast", mat:"shard", fudge:1, pos:[0,0.2,0], life:[0.4,0.6], size:[0.15,0.3], shape:{type:"sphere", r:0.3, th:1}, bursts:[[TF - 0.02,14]], speed:[3,6],
  vel:{y:[-0.5,0.5]}, drag:4, rot:[0,TAU], rotZ:[-9,9], alpha:[[0,1],[1,0]], sizeLife:[[0,1],[1,0.3]] });

// the light that stays where the mask fell (+Z 1.3 m)
const MP = [0, 0.14, 1.3];
L.push({ name:"M01 MaskLight", mat:"star", fudge:-6, pos:MP, life:3.2, size:0.7, bursts:[[4.55,1]], rot:[0.3,0.3], rotZ:0.25,
  sizeLife:[[0,0.2,0,4],[0.08,1.1],[0.2,0.9],[1,0.7]], alpha:[[0,0],[0.06,1],[0.75,0.8],[1,0]] });
L.push({ name:"M02 MaskGlow", mat:"goldGlow", fudge:-5, pos:MP, life:3.2, size:1.0, bursts:[[4.55,1]], color:[[0,C_SIL],[1,C_GLD]], alpha:[[0,0],[0.06,0.4],[0.75,0.3],[1,0]] });
L.push({ name:"M03 MaskRing", mat:"ring", mode:"mesh", mesh:"quad", fudge:12, pos:[MP[0],0.05,MP[2]], life:3.0, size:1.0, bursts:[[4.6,1]], sizeLife:[[0,0.5,0,3],[0.15,1]],
  color:[[0,C_SIL],[1,C_GLD]], alpha:[[0,0],[0.1,0.5],[0.7,0.35],[1,0]] });
L.push({ name:"M04 MaskMotes", mat:"dot", fudge:-5, pos:MP, life:[1.2,1.8], size:[0.04,0.07], shape:{type:"sphere", r:0.2, th:1}, bursts:[[4.6,4],[4.75,3],[4.9,3]],
  vel:{y:[0.3,0.6]}, noise:{s:0.2, f:1.2, sp:0.5}, color:[[0,C_WH],[1,C_GLD]], alpha:[[0,0],[0.2,1],[1,0]] });

const MATS = {
  line:{tex:"T_Line", tint:[1.6,1.55,1.75], add:true},
  lineHot:{tex:"T_Line", tint:[2.3,2.2,2.4], add:true},
  lineSoft:{tex:"T_Line", tint:[1.1,1.05,1.2], add:true},
  wipe:{tex:"T_LineWipe", tint:[2.2,2.1,2.4], add:true},
  band:{tex:"T_RuneStrip", tint:[1.45,1.4,1.6], add:true},
  sigil:{tex:"T_Sigil", tint:[1.6,1.55,1.75], add:true},
  chain:{tex:"T_Chain", tint:[1.25,1.2,1.35], add:true},
  pillar:{tex:"T_Pillar", tint:[2.2,2.1,2.3], add:true},
  pillarSoft:{tex:"T_Pillar", tint:[1.2,1.15,1.25], add:true},
  goldGlow:{tex:"T_Glow", tint:[1.3,1.25,1.15], add:true},
  vioGlow:{tex:"T_Glow", tint:[1.2,1.0,1.6], add:true},
  inkDecal:{tex:"T_InkSplat", tint:[1,1,1], add:false},
  inkMist:{tex:"T_InkMist_Sheet", tint:[1,1,1], add:false},
  inkStroke:{tex:"T_InkStroke", tint:[1.6,1.5,2.0], add:false},
  shard:{tex:"T_InkShard", tint:[2.0,1.8,2.4], add:false},
  star:{tex:"T_Star", tint:[2.2,2.15,2.3], add:true},
  dot:{tex:"T_Dot", tint:[2.0,1.95,2.1], add:true},
  vioDot:{tex:"T_Dot", tint:[1.6,1.2,2.2], add:true},
  ring:{tex:"T_Ring", tint:[1.6,1.55,1.7], add:true},
  streak:{tex:"T_Streak", tint:[2.1,2.0,2.2], add:true},
};
const BOSS = [{ c:[0,4.4,0], e:[0.85,2.6,0.7], shade:[0.07,0.05,0.1], gone:4.0 }];
const SCENE = {
  name:"MordianReseal",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.65, tint:[1.0,0.97,0.92] }, tonemapping:"neutral",
  systems:[ { id:"reseal", role:"static", loop:false, duration:TE, layers:L } ],
  demo:{
    interval:7.2, bg:[0.12,0.11,0.14], floor:[0.2,0.19,0.22], grid:[0.17,0.16,0.19],
    scenarios:{
      wide:{ label:"전체", anchor:[0,0,0], props:BOSS, from:[0,0,0], to:[0,0,0], normal:[0,1,0], flight:0.01, camera:{ target:[0,2.2,0], dist:24, yaw:-90, pitch:28 } },
      top:{ label:"위에서", anchor:[0,0,0], props:BOSS, from:[0,0,0], to:[0,0,0], normal:[0,1,0], flight:0.01, camera:{ target:[0,0,0], dist:22, yaw:-90, pitch:78 } },
      close:{ label:"가까이", anchor:[0,0,0], props:BOSS, from:[0,0,0], to:[0,0,0], normal:[0,1,0], flight:0.01, camera:{ target:[0,2.5,0], dist:13, yaw:-60, pitch:16 } },
    },
  },
};
