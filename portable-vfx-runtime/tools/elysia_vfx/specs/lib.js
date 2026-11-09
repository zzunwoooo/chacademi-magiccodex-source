// ===== 엘리시아 레이드 공용 메시·도우미 (각 spec 앞에 붙여서 export) =====
const TAU = Math.PI * 2;
function merge(parts) {
  const verts = [], uvs = [], tris = [];
  for (const p of parts) { const o = verts.length; verts.push(...p.verts); uvs.push(...p.uvs); for (const t of p.tris) tris.push(t + o); }
  return { verts, uvs, tris };
}
// XZ 평면 직선 띠 (u = 길이 방향, v = 폭)
function strip(a, b, w, y) {
  const dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz) || 1, nx = -dz / L * w / 2, nz = dx / L * w / 2;
  return { verts: [[a[0] - nx, y, a[1] - nz], [a[0] + nx, y, a[1] + nz], [b[0] - nx, y, b[1] - nz], [b[0] + nx, y, b[1] + nz]],
           uvs: [[0, 0], [0, 1], [1, 0], [1, 1]], tris: [0, 1, 2, 1, 3, 2] };
}
// XZ 고리 띠 (반지름 r, 폭 w), 머리(U=1)가 +Y 회전 방향을 이끈다
function ringM(r, w, y, seg, span, a0) {
  span = span ?? TAU; a0 = a0 ?? 0; seg = seg ?? 96;
  return ribbon(seg, s => { const p = a0 - s * span, d = [Math.cos(p), 0, Math.sin(p)]; return [add(mul(d, r - w / 2), [0, y, 0]), add(mul(d, r + w / 2), [0, y, 0])]; });
}
function polygram(n, k, r, w, y, a0) {
  a0 = a0 ?? 0; const P = i => [r * Math.cos(a0 + i * TAU / n), r * Math.sin(a0 + i * TAU / n)], parts = [];
  for (let i = 0; i < n; i++) parts.push(strip(P(i), P((i + k) % n), w, y));
  return merge(parts);
}
function vtxCircles(n, r, rc, w, y, a0) {
  a0 = a0 ?? 0; const parts = [];
  for (let i = 0; i < n; i++) { const p = a0 + i * TAU / n, cx = r * Math.cos(p), cz = r * Math.sin(p);
    parts.push(ribbon(24, s => { const q = -s * TAU, d = [Math.cos(q), 0, Math.sin(q)]; return [[cx + d[0] * (rc - w / 2), y, cz + d[2] * (rc - w / 2)], [cx + d[0] * (rc + w / 2), y, cz + d[2] * (rc + w / 2)]]; })); }
  return merge(parts);
}
// 룬 띠: N개의 호, 각 호가 룬 텍스처 U 0..1 전체를 쓴다
function runeArcs(N, r, w, y, gap) {
  gap = gap ?? 0.06; const parts = [], span = TAU / N * (1 - gap);
  for (let i = 0; i < N; i++) parts.push(ringM(r, w, y, 16, span, -i * TAU / N));
  return merge(parts);
}
// 방사 눈금선
function ticks(n, r0, r1, w, y) { const parts = []; for (let i = 0; i < n; i++) { const p = i * TAU / n; parts.push(strip([r0 * Math.cos(p), r0 * Math.sin(p)], [r1 * Math.cos(p), r1 * Math.sin(p)], w, y)); } return merge(parts); }
// 교차한 세로 사각 2장 (빛기둥): 폭 1, 높이 0..1
const pillarX = merge([
  { verts: [[-0.5, 0, 0], [0.5, 0, 0], [-0.5, 1, 0], [0.5, 1, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] },
  { verts: [[0, 0, -0.5], [0, 0, 0.5], [0, 1, -0.5], [0, 1, 0.5]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]);
// 세로 원통 벽 (반지름 1, 높이 0..1), U 둘레(n분할마다 0..1 반복), V 아래 1 → 위 0
function cylPanels(n, segs, h) {
  h = h ?? 1;
  const parts = [];
  for (let i = 0; i < n; i++) parts.push(ribbon(segs ?? 6, s => { const p = -(i + s) * TAU / n, d = [Math.cos(p), 0, Math.sin(p)]; return [d, add(d, [0, h, 0])]; }));
  parts.forEach(p => p.uvs = p.uvs.map(([u, v]) => [u, 1 - v]));
  return merge(parts);
}
// 반구 패널 (반지름 1): 경도 n, 위도 m 칸, 칸마다 UV 0..1
function domePanels(n, m) {
  const parts = [];
  for (let j = 0; j < m; j++) for (let i = 0; i < n; i++) {
    const P = (a, b) => { const th = -(i + a) * TAU / n, el = (j + b) / m * Math.PI / 2; return [Math.cos(el) * Math.cos(th), Math.sin(el), Math.cos(el) * Math.sin(th)]; };
    parts.push({ verts: [P(0, 0), P(1, 0), P(0, 1), P(1, 1)], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] });
  }
  return merge(parts);
}
// XY 평면 초승달 (베기용): 반지름 1, span 라디안, 머리 U=1
function crescentXY(span, thick) { return ribbon(64, s => { const p = -span / 2 + s * span, hw = 0.01 + thick * Math.pow(Math.sin(Math.PI * s), 0.8), d = [Math.cos(p), Math.sin(p), 0]; return [mul(d, 1 - hw), mul(d, 1 + hw * 0.35)]; }); }
// XZ 평면 초승달 (지면 휩쓸기)
function crescentXZ(span, thick) { return ribbon(64, s => { const p = span / 2 - s * span, hw = 0.01 + thick * Math.pow(Math.sin(Math.PI * s), 0.8), d = [Math.cos(p), 0, Math.sin(p)]; return [mul(d, 1 - hw), mul(d, 1 + hw * 0.35)]; }); }
const YAW = [0, [0, TAU], 0];
const COL = { white: [1, 1, 1] };
const GOLD = [1, 0.86, 0.5], AMBER = [1, 0.62, 0.22], PALE = [1, 0.97, 0.88], CRIM = [1, 0.25, 0.12], DEEP = [0.6, 0.05, 0.06], EMB = [1, 0.55, 0.2];
const BLOOM_HOLY = { threshold: 0.95, intensity: 1.3, scatter: 0.7, tint: [1, 0.92, 0.75] };
const BLOOM_HELL = { threshold: 0.95, intensity: 1.35, scatter: 0.7, tint: [1, 0.6, 0.5] };
const DEMO_BG = { bg: [0.16, 0.16, 0.19], floor: [0.12, 0.12, 0.14], grid: [0.2, 0.2, 0.23] };

// 구 패널 (반지름 1, 중심 원점): 위 반구 + 뒤집은 아래 반구
function spherePanels(n, m) { const up = domePanels(n, m), dn = domePanels(n, m);
  dn.verts = dn.verts.map(([x, y, z]) => [x, -y, z]); dn.tris = dn.tris.map((t, i) => dn.tris[i - i % 3 + [0, 2, 1][i % 3]]); return merge([up, dn]); }
