// 예고 장판 (미리보기 전용 — 실제 게임에서는 모드가 그림). 빨강.
var EXTRA_MESHES = Object.assign({
  tgCone150: sector(150),
  tgL18: strip(4 / 18), tgL14: strip(4 / 14), tgSeg: strip(7 / 10), tgL10: strip(3 / 10),
}, MC_MESHES);
const C = (id, R, T) => ({ id, role: "static", loop: false, duration: T + 0.35, layers: warnCircle(id, R, 0, T) });
const M = (id, mesh, size, T) => ({ id, role: "static", loop: false, duration: T + 0.35, layers: warnMesh(id, mesh, size, 0, T) });
const SCENE = {
  name: "TG", bloom: BLOOM, tonemapping: "neutral",
  systems: [
    M("tg_cone6", "tgCone150", 6, 0.4), M("tg_line18", "tgL18", 18, 0.6), M("tg_line14", "tgL14", 14, 0.45),
    M("tg_seg", "tgSeg", 10, 0.6), M("tg_line10", "tgL10", 10, 0.5),
    C("tg_c4", 4, 0.5), C("tg_c5s", 5, 1.1), C("tg_c8", 8, 0.4), C("tg_c6", 6, 0.5), C("tg_c7", 7, 0.7), C("tg_c5", 5, 0.6),
    C("tg_c45", 4.5, 0.75), C("tg_c25", 2.5, 1.5),
  ],
  demo: Object.assign({ interval: 2 }, DEMO_BG, { scenarios: { a: sc("a", { place: { tg_c4: { at: [0, 0, 0], start: 0.05 } }, camera: cam([0, 0, 0], 14, -40, 40) }) } }),
};
const MATS = WARN_MATS;
