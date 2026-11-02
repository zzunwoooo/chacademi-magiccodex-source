// AS_Ward — 빛의 수호 (장치 2개 이하일 때). shield: 장치를 따라가는 창백한 금빛 육각 막 2.2초 (반경 약 1.6 고정). 그동안 장치는 맞아도 안 부서짐.
const HOLD = 2.2;
var EXTRA_MESHES = Object.assign({
  shell: grid(36, 16, (u, v) => { const p = -u * TAU, th = v * Math.PI; return [Math.sin(th) * Math.cos(p), (1 - Math.cos(th)) * 0.95, Math.sin(th) * Math.sin(p)]; }),
}, MC_MESHES);
const SHIELD = [
  // base: gold ground circle (line work) drawn in, rotating
  { name: "B1 Ring", mat: "lineGold", mode: "mesh", mesh: "mcRing1", fudge: 50, pos: [0, 0.05, 0], life: HOLD, size: 2.1, rotLife: [0, 0.6, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.5, 0, 3], [0.12, 1], [0.9, 1], [1, 1.1]], alpha: [[0, 0], [0.08, 1], [0.9, 0.9], [1, 0]] },
  { name: "B2 Runes", mat: "runeGold", mode: "mesh", mesh: "mcRunesOut", fudge: 51, pos: [0, 0.05, 0], life: HOLD, size: 2.1, rotLife: [0, -0.9, 0], bursts: [[0.05, 1]],
    sizeLife: [[0, 0.6, 0, 3], [0.15, 1]], alpha: [[0, 0], [0.12, 0.9], [0.9, 0.8], [1, 0]] },
  { name: "B3 Star", mat: "lineGold", mode: "mesh", mesh: "mcOcta", fudge: 52, pos: [0, 0.05, 0], life: HOLD, size: 1.9, rotLife: [0, 0.4, 0], bursts: [[0.1, 1]],
    alpha: [[0, 0], [0.15, 0.7], [0.9, 0.6], [1, 0]] },
  { name: "B4 FloorGlow", mat: "glowPale", mode: "mesh", mesh: "quad", fudge: 70, pos: [0, 0.03, 0], life: HOLD, size: 4, bursts: [[0, 1]], alpha: [[0, 0], [0.1, 0.25], [0.9, 0.2], [1, 0]] },
  // mid: hex shell pops up (overshoot), inner counter shell, ripple pulses
  { name: "M1 Shell", mat: "hexGold", mode: "mesh", mesh: "shell", fudge: -2, pos: [0, -0.1, 0], life: HOLD, size: 1.65, rotLife: [0, 0.3, 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.3, 0, 5], [0.08, 1.08], [0.14, 1], [0.92, 1], [1, 1.12]], alpha: [[0, 0], [0.06, 0.38], [0.88, 0.28], [1, 0]] },
  { name: "M2 ShellInner", mat: "hexGold", mode: "mesh", mesh: "shell", fudge: -1, pos: [0, -0.1, 0], life: HOLD, size: 1.5, rotLife: [0, -0.5, 0], bursts: [[0.04, 1]],
    sizeLife: [[0, 0.3, 0, 5], [0.1, 1]], alpha: [[0, 0], [0.1, 0.12], [0.88, 0.1], [1, 0]] },
  { name: "M3 Ripple", mat: "hexGold", mode: "mesh", mesh: "shell", fudge: -3, pos: [0, -0.1, 0], life: 0.4, size: 1.7, bursts: [[0.55, 1], [1.15, 1], [1.75, 1]],
    sizeLife: [[0, 1], [1, 1.12]], alpha: [[0, 0.45], [1, 0]] },
  { name: "M4 Pillar", mat: "pillarPale", mode: "mesh", mesh: "pillar", fudge: 5, pos: [0, 0, 0], life: 0.5, size: 4.5, rot3: [0, [0, TAU], 0], bursts: [[0, 1]],
    sizeLife: [[0, 0.2, 0, 4], [0.3, 1]], alpha: [[0, 0], [0.12, 0.9], [1, 0]] },
  // top: ping flash, motes rising, sparks around the shell, shatter at the end
  ...fxFlashE("T1", [0, 1.2, 0], 0, 2.2, "starGold", "glowGold"),
  { name: "T2 Motes", mat: "dotGold", fudge: -6, life: [0.8, 1.3], size: [0.04, 0.09], rate: 22, shape: { type: "circle", r: 1.5, th: 0.4, y: 0.1 }, vel: { y: [0.6, 1.5] },
    alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "T3 Glints", mat: "starGold", fudge: -7, life: [0.25, 0.4], size: [0.25, 0.45], rate: 6, shape: { type: "sphere", r: 1.6, th: 0.05 }, pos: [0, 0.6, 0],
    sizeLife: [[0, 0], [0.4, 1], [1, 0]] },
  { name: "T4 Orbit", mat: "sparkGold", mode: "stretch", vs: 0.05, ls: 2.2, fudge: -5, life: [0.5, 0.7], size: [0.05, 0.09], rate: 16, shape: { type: "circle", r: 1.6, th: 0, y: 0.6 },
    vel: { orb: [3, 5], y: [-0.3, 0.6] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: "T5 Shatter", mat: "sparkGold", mode: "stretch", vs: 0.06, ls: 1.6, fudge: -5, pos: [0, 1, 0], life: [0.35, 0.6], size: [0.06, 0.12], shape: { type: "sphere", r: 1.5, th: 0.1 },
    speed: [2, 5], drag: 2, gravity: 0.6, bursts: [[HOLD - 0.15, 26]], alpha: [[0, 1], [1, 0]] },
];
const SCENE = {
  name: "AS_Ward", bloom: BLOOM, tonemapping: "neutral",
  systems: [{ id: "shield", role: "attached", loop: false, duration: HOLD + 0.6, layers: SHIELD }],
  demo: Object.assign({ interval: 3.4, dummy: { height: 1.6 } }, DEMO_BG, { scenarios: {
    base: { label: "장치 수호", path: [[0, [0, 0, 0], 0]], hits: [], camera: cam([0, 1, 0], 7, -70, 22) } } }),
};
