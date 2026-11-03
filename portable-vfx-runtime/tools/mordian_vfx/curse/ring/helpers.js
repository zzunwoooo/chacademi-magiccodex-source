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

