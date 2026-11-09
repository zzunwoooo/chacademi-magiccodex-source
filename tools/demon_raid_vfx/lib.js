// ===== 룰레타·솜니아 레이드 공용 메시·레이어 도우미 (각 팩 spec 앞에 붙여서 export) =====
const TAU = Math.PI * 2;
function merge(parts) {
  const verts = [], uvs = [], tris = [];
  for (const p of parts) { const o = verts.length; verts.push(...p.verts); uvs.push(...p.uvs); for (const t of p.tris) tris.push(t + o); }
  return { verts, uvs, tris };
}
// XZ 평면 직선 띠 a=[x,z] → b, 폭 w, 높이 y (u = 길이, v = 폭)
function strip(a, b, w, y) {
  const dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz) || 1, nx = -dz / L * w / 2, nz = dx / L * w / 2;
  return { verts: [[a[0] - nx, y, a[1] - nz], [a[0] + nx, y, a[1] + nz], [b[0] - nx, y, b[1] - nz], [b[0] + nx, y, b[1] + nz]],
           uvs: [[0, 0], [0, 1], [1, 0], [1, 1]], tris: [0, 1, 2, 1, 3, 2] };
}
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
function runeArcs(N, r, w, y, gap) {
  gap = gap ?? 0.06; const parts = [], span = TAU / N * (1 - gap);
  for (let i = 0; i < N; i++) parts.push(ringM(r, w, y, 16, span, -i * TAU / N));
  return merge(parts);
}
function ticks(n, r0, r1, w, y) { const parts = []; for (let i = 0; i < n; i++) { const p = i * TAU / n; parts.push(strip([r0 * Math.cos(p), r0 * Math.sin(p)], [r1 * Math.cos(p), r1 * Math.sin(p)], w, y)); } return merge(parts); }
// 부채꼴 채움: +Z 중심, span 라디안, 반지름 r0→r1 (V = 안→밖)
function fan(span, r0, r1, y, seg) {
  return ribbon(seg ?? 48, s => { const p = Math.PI / 2 + span / 2 - s * span, d = [Math.cos(p), 0, Math.sin(p)]; return [add(mul(d, r0), [0, y, 0]), add(mul(d, r1), [0, y, 0])]; });
}
// 부채꼴 외곽선 (반지름선 2 + 바깥 호 + 안쪽 호)
function fanEdge(span, r0, r1, w, y) {
  const a1 = Math.PI / 2 + span / 2, a2 = Math.PI / 2 - span / 2;
  return merge([strip([r0 * Math.cos(a1), r0 * Math.sin(a1)], [r1 * Math.cos(a1), r1 * Math.sin(a1)], w, y),
                strip([r0 * Math.cos(a2), r0 * Math.sin(a2)], [r1 * Math.cos(a2), r1 * Math.sin(a2)], w, y),
                ringM(r1, w, y, 32, span, a1), ringM(r0, w, y, 16, span, a1)]);
}
// 세로 원(XY 평면) 반지름 1
function ringV(w) { w = w ?? 0.08; return ribbon(64, s => { const p = s * TAU, d = [Math.cos(p), Math.sin(p), 0]; return [mul(d, 1 - w / 2), mul(d, 1 + w / 2)]; }); }
function ticksV(n, r0, r1, w) { const parts = []; for (let i = 0; i < n; i++) { const p = i * TAU / n, c = Math.cos(p), s = Math.sin(p), nx = -s * w / 2, ny = c * w / 2;
  parts.push({ verts: [[c * r0 - nx, s * r0 - ny, 0], [c * r0 + nx, s * r0 + ny, 0], [c * r1 - nx, s * r1 - ny, 0], [c * r1 + nx, s * r1 + ny, 0]], uvs: [[0, 0], [0, 1], [1, 0], [1, 1]], tris: [0, 1, 2, 1, 3, 2] }); }
  return merge(parts); }
// 세로 원통 벽 (반지름 1, 높이 0..h), 칸마다 U 0..1, V 아래 1 → 위 0
function cylPanels(n, segs, h) {
  h = h ?? 1; const parts = [];
  for (let i = 0; i < n; i++) parts.push(ribbon(segs ?? 6, s => { const p = -(i + s) * TAU / n, d = [Math.cos(p), 0, Math.sin(p)]; return [d, add(d, [0, h, 0])]; }));
  parts.forEach(p => p.uvs = p.uvs.map(([u, v]) => [u, 1 - v]));
  return merge(parts);
}
// 교차 세로 사각 2장: 폭 w, 높이 0..h
function pillarM(w, h) { return merge([
  { verts: [[-w / 2, 0, 0], [w / 2, 0, 0], [-w / 2, h, 0], [w / 2, h, 0]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] },
  { verts: [[0, 0, -w / 2], [0, 0, w / 2], [0, h, -w / 2], [0, h, w / 2]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }]); }
// 세로 벽 띠: +Z 방향 길이 L, 높이 h (U = 길이, V 아래 1 → 위 0)
function wallZ(L, h, x) { x = x ?? 0; return { verts: [[x, 0, 0], [x, 0, L], [x, h, 0], [x, h, L]], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] }; }
function crescentXY(span, thick) { return ribbon(64, s => { const p = -span / 2 + s * span, hw = 0.01 + thick * Math.pow(Math.sin(Math.PI * s), 0.8), d = [Math.cos(p), Math.sin(p), 0]; return [mul(d, 1 - hw), mul(d, 1 + hw * 0.35)]; }); }
function crescentXZ(span, thick) { return ribbon(64, s => { const p = span / 2 - s * span, hw = 0.01 + thick * Math.pow(Math.sin(Math.PI * s), 0.8), d = [Math.cos(p), 0, Math.sin(p)]; return [mul(d, 1 - hw), mul(d, 1 + hw * 0.35)]; }); }
function domePanels(n, m) {
  const parts = [];
  for (let j = 0; j < m; j++) for (let i = 0; i < n; i++) {
    const P = (a, b) => { const th = -(i + a) * TAU / n, el = (j + b) / m * Math.PI / 2; return [Math.cos(el) * Math.cos(th), Math.sin(el), Math.cos(el) * Math.sin(th)]; };
    parts.push({ verts: [P(0, 0), P(1, 0), P(0, 1), P(1, 1)], uvs: [[0, 1], [1, 1], [0, 0], [1, 0]], tris: [0, 1, 2, 1, 3, 2] });
  }
  return merge(parts);
}
const YAW = [0, [0, TAU], 0];
const DEMO_BG = { bg: [0.13, 0.12, 0.16], floor: [0.11, 0.1, 0.13], grid: [0.19, 0.18, 0.22] };
// 공용 마법진 메시 (반지름 1)
const CIRCLE_MESHES = {
  cOuter: merge([ringM(1, 0.03, 0.03), ringM(0.95, 0.012, 0.03)]),
  cTicks: ticks(48, 0.96, 1.04, 0.01, 0.03),
  cStar: merge([polygram(8, 3, 0.93, 0.014, 0.035), vtxCircles(8, 0.93, 0.05, 0.012, 0.035)]),
  cInner: merge([ringM(0.62, 0.02, 0.04), ringM(0.57, 0.01, 0.04), polygram(8, 2, 0.57, 0.012, 0.04, Math.PI / 8)]),
  cHex: merge([polygram(6, 2, 0.92, 0.016, 0.035), vtxCircles(6, 0.92, 0.06, 0.012, 0.035), ringM(0.6, 0.02, 0.04)]),
  cRune: runeArcs(14, 0.79, 0.13, 0.032),
  cRune2: runeArcs(10, 0.45, 0.1, 0.034),
};

// ---------- 레이어 도우미 ----------
// 일회성 바닥 마법진: t0 부터 그려지고 T 에 번쩍, E 에 사라짐. m = 머티리얼 이름표 {line, rune, sigil?, glow?}
function magicCircle(tag, R, t0, T, E, m, o) {
  o = o || {}; const y = o.y ?? 0, star = o.star ?? 'cStar', L = [], k = (T - t0) / (E - t0);
  const circ = (nm, mesh, f, w, dt, a1) => ({ name: `${tag} ${nm}`, mat: m.line, mode: 'mesh', mesh, fudge: f, pos: [0, y, 0], size: R, rot3: YAW, rotLife: [0, w, 0], bursts: [[t0 + dt, 1]], life: E - t0 - dt,
    sizeLife: [[0, 0.82, 0, 1.5], [Math.min(0.5, 0.25 + dt), 1]], alpha: [[0, 0], [Math.max(0.02, k * 0.55), a1 * 0.7], [Math.max(0.03, k), a1 * 0.75], [Math.min(0.97, k + 0.04), 1], [Math.max(k + 0.05, 0.75), 0.75], [1, 0]] });
  L.push(circ('Outer', 'cOuter', 40, 0.35, 0, 1), circ('Ticks', 'cTicks', 39, -0.2, 0.03, 0.8), circ('Star', star, 38, -0.55, 0.06, 0.9), circ('Inner', 'cInner', 37, 0.8, 0.1, 0.9));
  L.push({ name: `${tag} Rune`, mat: m.rune, mode: 'mesh', mesh: 'cRune', fudge: 36, pos: [0, y, 0], size: R, rot3: YAW, rotLife: [0, 0.5, 0], bursts: [[t0 + 0.04, 1]], life: E - t0 - 0.04,
    sizeLife: [[0, 0.9], [0.25, 1]], alpha: [[0, 0], [Math.max(0.03, k), 0.75], [Math.min(0.97, k + 0.04), 1], [Math.max(k + 0.05, 0.75), 0.7], [1, 0]] });
  if (m.sigil) L.push({ name: `${tag} Sigil`, mat: m.sigil, mode: 'mesh', mesh: 'quad', fudge: 35, pos: [0, y + 0.05, 0], size: R * 1.12, rot3: YAW, rotLife: [0, -0.3, 0], bursts: [[t0 + 0.08, 1]], life: E - t0 - 0.08,
    sizeLife: [[0, 0.6, 0, 2], [Math.max(0.05, k), 1], [Math.min(0.98, k + 0.03), 1.06], [1, 0.96]], alpha: [[0, 0], [Math.max(0.03, k), 0.7], [Math.min(0.97, k + 0.03), 1], [0.7, 0.65], [1, 0]] });
  L.push({ name: `${tag} Flare`, mat: m.line, mode: 'mesh', mesh: 'cOuter', fudge: 34, pos: [0, y, 0], size: R, rot3: YAW, bursts: [[T, 1]], life: 0.5, sizeLife: [[0, 1], [1, 1.25]], alpha: [[0, 1], [1, 0]] });
  if (m.glow) L.push({ name: `${tag} FloorGlow`, mat: m.glow, mode: 'mesh', mesh: 'quad', fudge: 44, pos: [0, y + 0.02, 0], size: R * 2.4, rot3: YAW, bursts: [[t0, 1]], life: E - t0,
    alpha: [[0, 0], [Math.max(0.03, k), 0.3], [Math.min(0.97, k + 0.03), 0.55], [0.6, 0.22], [1, 0]] });
  return L;
}
// 충격 폭발 (점 p, 시각 t, 크기 s). m = {star, flare, glow?, wave?, streak, dot, shardD?, shardL?, mist?, line?}, col = {hot, mid, low} (rgb ≤ 1)
function impactBurst(tag, p, t, s, m, col, o) {
  o = o || {}; const g = o.ground ?? true, L = [], up = [p[0], p[1] + 0.6 * s, p[2]];
  L.push({ name: `${tag} Flash`, mat: m.star, fudge: -8, pos: up, size: 3.2 * s, rot: [0, TAU], bursts: [[t, 1]], life: 0.2, sizeLife: [[0, 0.3], [0.25, 1.1], [1, 0]] });
  L.push({ name: `${tag} Flare`, mat: m.flare, fudge: -7, pos: up, size: 4 * s, rot: [0, TAU], rotZ: [-0.6, 0.6], bursts: [[t, 1]], life: 0.4, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.2]], alpha: [[0, o.flareA ?? 0.8], [1, 0]] });
  if (m.glow) L.push({ name: `${tag} Core`, mat: m.glow, fudge: 0, pos: up, size: 2.2 * s, bursts: [[t, 1]], life: 0.45, sizeLife: [[0, 0.5], [0.2, 1], [1, 1.1]], alpha: [[0, 0.55], [1, 0]] });
  if (g && m.wave) {
    L.push({ name: `${tag} Wave1`, mat: m.wave, mode: 'mesh', mesh: 'quad', fudge: 30, pos: [p[0], p[1] + 0.06, p[2]], size: (o.waveR ?? 2.5) * 2.6 * s, rot3: YAW, bursts: [[t, 1]], life: 0.45, sizeLife: [[0, 0.1, 0, 3], [1, 1]], alpha: [[0, 0.85], [0.6, 0.6], [1, 0]] });
    L.push({ name: `${tag} Wave2`, mat: m.wave, mode: 'mesh', mesh: 'quad', fudge: 31, pos: [p[0], p[1] + 0.08, p[2]], size: (o.waveR ?? 2.5) * 2.1 * s, rot3: YAW, bursts: [[t + 0.09, 1]], life: 0.45, sizeLife: [[0, 0.15, 0, 3], [1, 1]], alpha: [[0, 0.6], [1, 0]] });
  }
  L.push({ name: `${tag} Rays`, mat: m.streak, mode: 'stretch', vs: 0.08, ls: 4, fudge: -3, pos: [p[0], p[1] + 0.4 * s, p[2]], life: [0.3, 0.5], size: [0.12 * s, 0.22 * s], bursts: [[t, Math.round(18 * (o.n ?? 1))]],
    shape: { type: 'circle', r: 0.3 * s, th: 1, y: 0 }, vel: { rad: [10 * s, 16 * s], y: [0.5, 3 * s] }, drag: 5, alpha: [[0, 1], [1, 0]] });
  L.push({ name: `${tag} UpRays`, mat: m.streak, mode: 'stretch', vs: 0.06, ls: 3, fudge: -2, pos: p, life: [0.4, 0.6], size: [0.06 * s, 0.14 * s], bursts: [[t, Math.round(12 * (o.n ?? 1))], [t + 0.1, Math.round(8 * (o.n ?? 1))]],
    shape: { type: 'circle', r: (o.waveR ?? 2.5) * 0.7 * s, th: 1, y: 0.1 }, vel: { y: [8 * s, 14 * s] }, drag: 2.5, alpha: [[0, 1], [1, 0]] });
  L.push({ name: `${tag} Sparks`, mat: m.dot, mode: 'stretch', vs: 0.05, ls: 1.2, fudge: -5, pos: [p[0], p[1] + 0.3 * s, p[2]], life: [0.5, 0.9], size: [0.05 * s, 0.1 * s], bursts: [[t, Math.round(40 * (o.n ?? 1))]],
    shape: { type: 'sphere', r: 0.3 * s, th: 1 }, speed: [4 * s, 10 * s], drag: 2.2, gravity: 0.6, color: [[0, col.hot], [0.5, col.mid], [1, col.low]], alpha: [[0, 1], [0.7, 1], [1, 0]] });
  if (m.shardD) L.push({ name: `${tag} DarkShards`, mat: m.shardD, fudge: 20, pos: [p[0], p[1] + 0.4 * s, p[2]], life: [0.5, 0.8], size: [0.18 * s, 0.34 * s], rot: [0, TAU], rotZ: [-10, 10], bursts: [[t, Math.round(10 * (o.n ?? 1))]],
    shape: { type: 'sphere', r: 0.3 * s, th: 1 }, speed: [4 * s, 8 * s], drag: 1.2, gravity: 1.4, alpha: [[0, 1], [0.8, 1], [1, 0]] });
  if (m.shardL) L.push({ name: `${tag} LightShards`, mat: m.shardL, fudge: -4, pos: [p[0], p[1] + 0.4 * s, p[2]], life: [0.5, 0.8], size: [0.12 * s, 0.24 * s], rot: [0, TAU], rotZ: [-10, 10], bursts: [[t, Math.round(12 * (o.n ?? 1))]],
    shape: { type: 'sphere', r: 0.3 * s, th: 1 }, speed: [4 * s, 8 * s], drag: 1.5, gravity: 1.0, alpha: [[0, 1], [0.8, 1], [1, 0]] });
  if (m.mist) L.push({ name: `${tag} Mist`, mat: m.mist, fudge: 25, life: [0.9, 1.3], size: [1.2 * s, 2 * s], rot: [0, TAU], pos: p, shape: { type: 'circle', r: (o.waveR ?? 2.5) * 0.6 * s, th: 1, y: 0.4 * s },
    bursts: [[t + 0.03, Math.round(8 * (o.n ?? 1))]], vel: { rad: [0.8 * s, 1.6 * s], y: [0.3, 0.7], space: 'world' }, drag: 1.5, sizeLife: [[0, 0.6], [1, 1.3]], alpha: [[0, 0], [0.2, 0.55], [1, 0]], flip: { x: 4, y: 4 } });
  if (m.ring) L.push({ name: `${tag} RingSharp`, mat: m.ring, mode: 'mesh', mesh: 'cOuter', fudge: 29, pos: [p[0], p[1], p[2]], size: (o.waveR ?? 2.5) * s, rot3: YAW, bursts: [[t + 0.03, 1]], life: 0.35, sizeLife: [[0, 0.2, 0, 3.5], [1, 1.35]], alpha: [[0, 1], [1, 0]] });
  if (m.motes) L.push({ name: `${tag} Motes`, mat: m.motes, fudge: -6, life: [1.0, 1.8], size: [0.04 * s, 0.08 * s], pos: p, shape: { type: 'circle', r: (o.waveR ?? 2.5) * 0.8 * s, th: 1, y: 0.2 },
    bursts: [[t + 0.05, Math.round(30 * (o.n ?? 1))]], vel: { y: [1, 2.5] }, noise: { s: 0.8, f: 1, sp: 0.6 }, color: [[0, col.hot], [0.6, col.mid], [1, col.low]], alpha: [[0, 0], [0.1, 1], [0.8, 0.8], [1, 0]] });
  return L;
}
// 계속 도는 유지 레이어 (loop 시스템용): 복사본이 85% 지점에서 이어 받음. 회전 w 는 symW(n, LIFE) 로 n겹 대칭에 맞춤
function symW(n, LIFE, sign) { return (sign ?? 1) * (TAU / n) / (0.85 * (LIFE ?? 4)); }
function heldLoop(nm, mat, mesh, f, w, a, sz, pos, LIFE) {
  LIFE = LIFE ?? 4;
  return { name: nm, mat, mode: 'mesh', mesh, fudge: f, size: sz, pos: pos ?? [0, 0, 0], rot3: [0, 0, 0], rotLife: [0, w, 0], rate: 1 / (0.85 * LIFE), life: LIFE, bursts: [[0, 1]],
    alpha: [[0, 0], [0.15, a], [0.85, a], [1, 0]], onStop: 0.3 };
}
