// PR_LanternLight — 기믹3 등불 빛 반경(안전지대). attached(등불 든 사람 발밑). 반경은 runtime areaRadius(기준 4블록, 사탕 넣을수록 서버가 키움/시간 지나면 줄임).
// 안쪽 = 따뜻한 금빛, 경계선 바로 바깥 = 빨간 위험선 (빛 밖은 죽음).
const R = 4, LL = 2.4;
var EXTRA_MESHES = Object.assign({ edge: ringStrip(1, 0.03), danger: ringStrip(1.06, 0.07), dangerOut: ringStrip(1.14, 0.05) }, MC_MESHES);
const held = (name, mat, mesh, size, rot, a, f, y = 0.05) => ({ name, mat, mode: "mesh", mesh, fudge: f, pos: [0, y, 0], life: LL, size, rotLife: [0, rot, 0], rate: 2 / LL, bursts: [[0, 2]],
  alpha: [[0, 0], [0.5, a], [1, 0]] });
const LIGHT = [
  held("H1 Floor", "glowGold", "quad", 2 * R * 1.05, 0, 0.22, 70, 0.03),
  held("H2 Edge", "lineGold", "edge", R, 0.3, 0.95, 50), held("H3 Ring2", "lineGold", "mcRing2", R, -0.2, 0.6, 51), held("H4 Runes", "runeGold", "mcRunesOut", R, 0.15, 0.6, 52),
  held("H5 Danger", "warnBandHot", "danger", R, 0, 0.85, 49), held("H6 DangerOut", "warnBand", "dangerOut", R, 0, 0.6, 49),
  { name: "H7 Motes", mat: "dotGold", fudge: -6, life: [1.2, 2.0], size: [0.04, 0.09], rate: 16, shape: { type: "circle", r: R * 0.85, th: 1, y: 0.1 }, vel: { y: [0.3, 0.8] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "H8 EdgeSparks", mat: "dotGold", fudge: -6, life: [0.5, 0.9], size: [0.05, 0.1], rate: 20, shape: { type: "circle", r: R, th: 0, y: 0.1 }, vel: { y: [0.6, 1.4] }, alpha: [[0, 1], [1, 0]] },
  { name: "H9 DarkBeyond", mat: "mist", fudge: 75, life: [1.8, 2.6], size: [2, 3], rate: 5, shape: { type: "circle", r: R * 1.4, th: 0.15, y: 0.6 }, rot: [0, TAU], alpha: [[0, 0], [0.3, 0.5], [1, 0]], flip: F44 },
];
const MATS = FX_MATS;
const SCENE = {
  name: "PR_LanternLight", bloom: BLOOM, tonemapping: "neutral",
  scale: { input: "areaRadius", reference: R, min: 0.3, max: 3 },
  systems: [{ id: "light", role: "attached", loop: true, duration: LL, layers: LIGHT }],
  demo: Object.assign({ interval: 4, dummy: { height: 1.8 } }, DEMO_BG, { scenarios: {
    base: { label: "기본 (반경 4)", path: [[0, [0, 0, 0], 0], [4, [2, 0, 1], 30]], hits: [], camera: Object.assign(cam([0, 0.5, 0], 14, -90, 50), { follow: true }) } } }),
};
