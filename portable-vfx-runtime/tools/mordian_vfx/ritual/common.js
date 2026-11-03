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
