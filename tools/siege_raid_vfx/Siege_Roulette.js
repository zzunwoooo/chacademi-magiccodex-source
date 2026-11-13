// ===== Siege_Roulette — 공성전 2공세 · 도박의 악마 룰레타(봉인 전) 짤패턴 VFX =====
const WHITE = [1, 1, 1], RED = [1, 0.3, 0.25], CRIM = [0.6, 0.05, 0.1], GOLD = [1, 0.84, 0.4], AMB = [1, 0.55, 0.15], PUR = [0.7, 0.35, 1], DEEP = [0.25, 0.06, 0.4];
const CR = { hot: WHITE, mid: RED, low: CRIM }, CG = { hot: WHITE, mid: GOLD, low: AMB }, CP = { hot: [1, 0.8, 1], mid: PUR, low: DEEP };
const MATS = mkMats({ R: [1, 0.14, 0.1], G: [1, 0.7, 0.2], P: [0.58, 0.18, 1] }, {
  wheelR: { tex: 'T_Wheel', tint: [2.6, 0.4, 0.3], add: true }, wheelG: { tex: 'T_Wheel', tint: [2.6, 1.9, 0.55], add: true }, wheelP: { tex: 'T_Wheel', tint: [1.5, 0.5, 2.6], add: true },
  card: { tex: 'T_Card', tint: [1.15, 1.1, 1.05], add: false }, chipR: { tex: 'T_Chip', tint: [1.3, 0.3, 0.28], add: false }, chipK: { tex: 'T_Chip', tint: [0.42, 0.4, 0.5], add: false },
  coin: { tex: 'T_Coin', tint: [1.6, 1.25, 0.55], add: false }, die: { tex: 'T_Die', tint: [1.15, 1.1, 1.05], add: false },
  hearts: { tex: 'T_Hearts', tint: [2.4, 0.5, 0.4], add: true }, diamond: { tex: 'T_Diamond', tint: [2.4, 0.6, 0.45], add: true }, spade: { tex: 'T_Spade', tint: [0.3, 0.27, 0.36], add: false }, club: { tex: 'T_Club', tint: [0.3, 0.27, 0.36], add: false } });
KIT = KITS.roulette;
const suits = (tag, t, n, pos, shape, v) => ['hearts', 'diamond', 'spade', 'club'].map((m, i) => ({ name: `${tag} Suit${i}`, mat: m, fudge: -4 + i, pos, life: [0.8, 1.3], size: [0.35, 0.6], rot: [-0.4, 0.4], rotZ: [-2, 2], shape,
  bursts: [[t, Math.max(1, Math.round(n / 4))]], vel: v, drag: 1.5, gravity: 0.3, sizeLife: [[0, 0.3, 0, 4], [0.2, 1], [1, 0.8]], alpha: [[0, 0], [0.1, 1], [0.75, 1], [1, 0]] }));
const cardsFly = (tag, T, len, w, n) => ({ name: `${tag} Cards`, mat: 'card', fudge: -3, pos: [0, 1.2, 0.5], life: [0.3, 0.4], size: [0.5, 0.75], rot: [0, TAU], rotZ: [-14, 14], shape: { type: 'box', sx: w * 0.6, sy: 0.8, sz: 0.4 }, bursts: [[T, n]],
  vel: { z: [len / 0.36 * 0.85, len / 0.36 * 1.1] }, alpha: [[0, 1], [0.85, 1], [1, 0]] });
const cardsBurst = (tag, t, n, pos, sp) => ({ name: `${tag} CardBurst`, mat: 'card', fudge: -3, pos, life: [0.8, 1.3], size: [0.4, 0.65], rot: [0, TAU], rotZ: [-10, 10], shape: { type: 'sphere', r: 0.5, th: 1 }, bursts: [[t, n]], speed: sp, drag: 2, gravity: 0.5, alpha: [[0, 1], [0.8, 1], [1, 0]] });
const fallObj = (tag, mat, T, h, size, spin) => ({ name: `${tag} Fall`, mat, fudge: -9, pos: [0, h, 0], size, rot: [0, TAU], rotZ: [spin, spin], bursts: [[0, 1]], life: T + 0.05, vel: { y: [-(h - 0.6) / T, -(h - 0.6) / T] }, sizeLife: [[0, 0.5], [0.2, 1], [1, 1]], alpha: [[0, 0], [0.15, 1], [0.95, 1], [1, 0]] });
const coins = (tag, t, n, pos, sp) => ({ name: `${tag} Coins`, mat: 'coin', fudge: -4, pos, life: [0.8, 1.3], size: [0.25, 0.4], rot: [0, TAU], rotZ: [-12, 12], shape: { type: 'sphere', r: 0.5, th: 1 }, bursts: [[t, n]], speed: sp, drag: 1.2, gravity: 1.6, alpha: [[0, 1], [0.85, 1], [1, 0]] });

const S = [];
// 룰레타의 공격 방식: 빠르고, 여러 번이고, 결과가 운에 달렸다.
// 1. 카드 부메랑 — 빠르게 나갔다가(0.4초) 같은 길로 되돌아온다(서버가 끝 지점에서 반대 방향으로 card_back 소환)
S.push(sys('card_out', blastLane('co', 1.5, 18, 0.4, 'R', CR, { s2: 'G', h: 2.5, extra: [cardsFly('co', 0.4, 18, 1.5, 9)] }), 0.4));
S.push(sys('card_back', blastLane('cb', 1.5, 18, 0.25, 'G', CG, { s2: 'R', h: 2.5, extra: [cardsFly('cb', 0.25, 18, 1.5, 9)] }), 0.25));
// 2. 튕기는 주사위 — 8칸씩 세 번 튀고, 멈춘 눈에 따라 폭발 크기가 달라진다 (1~2 작게 / 3~4 중간 / 5~6 크게)
S.push(sysD('dice_hop', [
  { name: 'dh Die', mat: 'die', fudge: -9, pos: [0, 1.2, 0], size: 2.6, rot: [0, TAU], rotZ: [11, 11], bursts: [[0, 1]], life: 0.5, vel: { z: [16, 16] }, sizeLife: [[0, 1], [0.5, 1.5], [1, 1]], alpha: [[0, 1], [0.92, 1], [1, 0]] },
  { name: 'dh Trail', mat: 'flareP', fudge: -6, pos: [0, 1.2, 0], size: [2, 2.8], rot: [0, TAU], bursts: [[0, 1], [0.08, 1], [0.16, 1]], life: 0.45, vel: { z: [14, 15.5] }, alpha: [[0, 0.55], [1, 0]] },
  ...miniBurst('dhA', [0, 0, 0], 0, 0.7, 'P', CP), ...miniBurst('dhB', [0, 0, 8], 0.5, 0.9, 'G', CG)], 0.9));
[['s', 2, 2, '1~2'], ['m', 4, 4, '3~4'], ['l', 6.5, 6, '5~6']].forEach(([k, R, pips]) => S.push(sys('dice_pop_' + k, blastCircle('dp' + k, R, 0.3, 'P', CP, { s2: 'G', extra: [
  { name: `dp${k} Die`, mat: 'die', fudge: -9, pos: [0, 1.4 + R * 0.2, 0], size: 2.2 + R * 0.35, rot: [0, TAU], rotZ: [3, 3], bursts: [[0, 1]], life: 0.36, sizeLife: [[0, 1], [0.8, 1], [0.93, 1.5], [1, 0.2]], alpha: [[0, 1], [0.95, 1], [1, 0]] },
  { name: `dp${k} Pips`, mat: 'dotG', fudge: -8, pos: [0, 1.4, 0], life: [0.5, 0.9], size: [0.35, 0.55], shape: { type: 'sphere', r: 0.5, th: 1 }, bursts: [[0.3, pips * 3]], speed: [R * 1.5, R * 3], drag: 2.5, alpha: [[0, 1], [0.7, 1], [1, 0]] }] }), 0.3)));
// 3. 칩 도미노 — 칩 탑이 0.8초에 걸쳐 차례로 쓰러지며 나아간다
S.push(sysD('chip_domino', travelLane('cdm', 3, 20, 0.5, 0.8, 'R', CR, { s2: 'G' }), 1.9));
// 4. 동전 던지기 — 동전이 떨어지는 순간 앞면(안쪽 원) / 뒷면(바깥 고리) 중 하나만 터진다
S.push(sysD('coin_toss', [
  { name: 'ct Coin', mat: 'coin', fudge: -9, pos: [0, 8, 0], size: 4.2, rot: [0, TAU], rotZ: [16, 16], bursts: [[0, 1]], life: 1.15, vel: { y: [9.5, 9.5] }, gravity: 1.9, alpha: [[0, 0], [0.08, 1], [0.95, 1], [1, 0]] },
  { name: 'ct Glint', mat: 'starG', fudge: -10, pos: [0, 12, 0], size: 5, rot: [0, TAU], bursts: [[0.5, 1]], life: 0.3, sizeLife: [[0, 0.3], [0.3, 1.1], [1, 0]] },
  hold('ct Wheel', 'wheelG', 34, 0, 1.3, { rotLife: [0, 5, 0], alpha: [[0, 0], [0.2, 0.45], [0.85, 0.5], [1, 0]] }), hold('ct WheelIn', 'wheelR', 16.5, 0, 1.3, { rotLife: [0, -8, 0], alpha: [[0, 0], [0.2, 0.6], [0.85, 0.6], [1, 0]] })], 1.3));
S.push(sys('coin_heads_in', blastCircle('ci', 8, 0.3, 'G', CG, { s2: 'R', extra: [coins('ci', 0.3, 40, [0, 1, 0], [6, 13])] }), 0.3));
S.push(sys('coin_tails_out', blastRing('cu', 8, 16, 0.3, 'R', CR, { s2: 'G', extra: [coins('cu', 0.3, 30, [0, 1, 0], [12, 20])] }), 0.3));
// 5. 슬롯머신 — 릴이 1초 돌다 멈추면 전장을 가로지르는 줄이 터진다 (서버가 5줄 중 무작위 3줄)
S.push(sys('slot_lane', blastLane('sl', 4, 40, 1.0, 'G', CG, { s2: 'R', h: 5, extra: [
  ...['hearts', 'diamond', 'spade', 'club'].map((m, i) => ({ name: `sl Reel${i}`, mat: m, fudge: -9, pos: [0, 3.2, 1], size: 2.6, bursts: [[i * 0.1, 1], [0.4 + i * 0.1, 1]], life: 0.1, vel: { y: [-6, -6] }, alpha: [[0, 0], [0.3, 1], [0.7, 1], [1, 0]] })),
  { name: 'sl Seven', mat: 'coin', fudge: -9, pos: [0, 3.2, 1], size: 3, bursts: [[0.8, 1]], life: 0.45, sizeLife: [[0, 0.3, 0, 6], [0.3, 1.15], [1, 1]], alpha: [[0, 1], [0.8, 1], [1, 0]] },
  coins('sl', 1.0, 40, [0, 1.5, 20], [4, 10]), cardsFly('sl', 1.0, 40, 4, 12)] }), 1.0));
// 6. 룰렛 난사 — 바늘이 가리킨 구역이 순서대로 터진다
[['sector_red', 'R', CR, 'G'], ['sector_gold', 'G', CG, 'R'], ['sector_violet', 'P', CP, 'G']].forEach(([id, sf, c, s2]) => S.push(sys(id, blastFan(id.slice(7, 9), Math.PI / 3, 16, 1.0, sf, c, { s2, rise: 1.0, extra: [
  { name: `${id} Needle`, mat: 'line' + sf, mode: 'mesh', mesh: 'needle', fudge: 5, pos: [0, 0.3, 0], size: 1, rot3: [0, 0, 0], bursts: [[0, 1]], life: 1.25, alpha: [[0, 0], [0.3, 0.5], [0.78, 0.9], [0.82, 1], [1, 0]] },
  ...suits(id, 1.0, 12, [0, 1, 8], { type: 'box', sx: 8, sy: 1, sz: 12 }, { y: [2, 7], z: [-2, 4] }), coins(id, 1.0, 14, [0, 1, 9], [4, 9])] }), 1.0)));
// 7. 딜러의 여섯 손 — 0.35초 예고의 작은 내려찍기가 한 사람에게 6번 연달아 따라간다
S.push(sys('hand_slam', blastCircle('hs', 2.5, 0.35, 'R', CR, { s2: 'G', extra: [fallObj('hs', 'chipR', 0.35, 7, 2.6, 8)] }), 0.35));
// 8. 올인 — 여섯 팔을 한꺼번에 내려찍는 큰 반원
S.push(sys('all_in', blastFan('ai', Math.PI, 12, 0.9, 'R', CR, { s2: 'G', rise: 1.2, extra: [cardsBurst('ai', 0.9, 24, [0, 2, 3], [6, 14]), coins('ai', 0.9, 30, [0, 1.5, 4], [6, 13]),
  ...[-4, -2.4, -0.8, 0.8, 2.4, 4].flatMap((x, i) => KITS.roulette.column(`aiF${i}`, [x * 1.6, 0, 4 + Math.cos(x * 0.5) * 3], 0.88 + 0.02 * i, 1.5, 'R', CR))] }), 0.9));
// 9. 잭팟 (광폭화 후) — 동전 비
S.push(sys('jackpot', blastCircle('jp', 2.5, 0.7, 'G', CG, { s2: 'R', extra: [fallObj('jp', 'coin', 0.7, 12, 2.4, 10), coins('jp', 0.7, 16, [0, 0.6, 0], [4, 9]),
  { name: 'jp CoinRain', mat: 'coin', mode: 'stretch', vs: 0.02, ls: 1.2, fudge: -4, pos: [0, 11, 0], life: 0.75, size: [0.3, 0.45], shape: { type: 'circle', r: 2, th: 1, y: 0 }, bursts: [[0, 6], [0.2, 6]], vel: { y: [-15, -13] }, alpha: [[0, 0], [0.15, 1], [0.95, 1], [1, 0]] }] }), 0.7));
S.push(sys('gate_strike', blastLane('gs', 5, 24, 1.5, 'G', CG, { s2: 'R', h: 7, extra: [cardsBurst('gs', 1.5, 30, [0, 2, 22], [6, 14]), coins('gs', 1.5, 30, [0, 2, 22], [6, 14]),
  ...[-2, -1.2, -0.4, 0.4, 1.2, 2].flatMap((x, i) => KITS.roulette.column(`gsF${i}`, [x, 0, 21 + (i % 2) * 1.5], 1.48 + 0.02 * i, 2, i % 2 ? 'G' : 'R', CG))] }), 1.5));
S.push(sys('enrage', enrageFx('en', 6, 8.5, 'R', 'G', CG).concat([cardsBurst('en', 0.6, 40, [0, 5, 0], [8, 16]), coins('en', 0.6, 50, [0, 4, 0], [8, 16]), ...suits('en', 0.6, 24, [0, 4, 0], { type: 'sphere', r: 1, th: 1 }, { rad: [5, 12], y: [2, 8] })]), 1.2));

SM.needle = merge([strip([0, 0.5], [0, 15.5], 0.5, 0), strip([0, 0.5], [0, 13], 0.16, 0.02)]);
var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, SM);
const O = HID(0, 0, 0);
const SCENE = {
  name: 'Siege_Roulette', bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.7, tint: [1, 0.85, 0.8] }, tonemapping: 'neutral', systems: S,
  demo: Object.assign({}, DEMO_BG, { interval: 5, dummy: { height: 1.8 }, scenarios: {
    card_boomerang: scn('1. 카드 부메랑 (나갔다가 되돌아옴)', [-30, -15, 0, 15, 30].map(a => HID(0, 0, a)).concat([-30, -15, 0, 15, 30].map(a => HID(Math.sin(a * D2R) * 18, Math.cos(a * D2R) * 18, a + 180))),
      Array.from({ length: 10 }, (_, i) => ({ t: i < 5 ? 0.2 : 1.0, system: i < 5 ? 'card_out' : 'card_back', actor: i })), cam([0, 2, 8], 34, -90, 40)),
    dice: scn('2. 튕기는 주사위 (세 번 튀고 눈만큼 폭발)', [HID(-4, -12), HID(-4, -4), HID(-4, 4), HID(-4, 12), HID(4, -12), HID(4, -4), HID(4, 4), HID(4, 12)],
      [{ t: 0.2, system: 'dice_hop', actor: 0 }, { t: 0.7, system: 'dice_hop', actor: 1 }, { t: 1.2, system: 'dice_hop', actor: 2 }, { t: 1.7, system: 'dice_pop_l', actor: 3 },
       { t: 0.2, system: 'dice_hop', actor: 4 }, { t: 0.7, system: 'dice_hop', actor: 5 }, { t: 1.2, system: 'dice_hop', actor: 6 }, { t: 1.7, system: 'dice_pop_s', actor: 7 }], cam([0, 2, 0], 44, -60, 34)),
    chip_domino: scn('3. 칩 도미노 (차례로 쓰러짐)', [HID(0, 0, -20), HID(0, 0, 20)], [{ t: 0.2, system: 'chip_domino', actor: 0 }, { t: 0.5, system: 'chip_domino', actor: 1 }], cam([0, 2, 10], 30, -60, 30)),
    coin_heads: scn('4-A. 동전 던지기 → 앞면 (안쪽)', [O], [{ t: 0.2, system: 'coin_toss', actor: 0 }, { t: 1.35, system: 'coin_heads_in', actor: 0 }], cam([0, 3, 0], 46, -90, 34)),
    coin_tails: scn('4-B. 동전 던지기 → 뒷면 (바깥)', [O], [{ t: 0.2, system: 'coin_toss', actor: 0 }, { t: 1.35, system: 'coin_tails_out', actor: 0 }], cam([0, 3, 0], 46, -90, 34)),
    slot: scn('5. 슬롯머신 (5줄 중 3줄)', [HID(-14, -20), HID(0, -20), HID(7, -20)], [0, 1, 2].map(a => ({ t: 0.2 + a * 0.15, system: 'slot_lane', actor: a })), cam([0, 2, 0], 66, -60, 40)),
    roulette: scn('6. 룰렛 난사 (3구역 순서대로)', [HID(0, 0, 0), HID(0, 0, 120), HID(0, 0, 240)], [{ t: 0.2, system: 'sector_red', actor: 0 }, { t: 0.8, system: 'sector_gold', actor: 1 }, { t: 1.4, system: 'sector_violet', actor: 2 }], cam([0, 2, 0], 44, -90, 40)),
    hands: scn('7. 딜러의 여섯 손 (6연타 추적)', Array.from({ length: 6 }, (_, i) => HID(-5 + i * 2, 3 + Math.sin(i) * 2)).concat([{ path: [[0, [-5, 0, 3], 90], [2.4, [6, 0, 4], 90]] }]), Array.from({ length: 6 }, (_, i) => ({ t: 0.2 + i * 0.32, system: 'hand_slam', actor: i })), cam([0, 2, 3], 24, -75, 32)),
    all_in: scn('8. 올인', [O], [{ t: 0.2, system: 'all_in', actor: 0 }], cam([0, 2, 5], 28, -55, 30)),
    jackpot: scn('9. 잭팟 (광폭화 후)', Array.from({ length: 24 }, (_, i) => HID(Math.sin(i * 2.4) * (2 + i * 0.6), Math.cos(i * 2.4) * (2 + i * 0.6))), Array.from({ length: 24 }, (_, i) => ({ t: 0.2 + i * 0.08, system: 'jackpot', actor: i })), cam([0, 2, 0], 36, -70, 34)),
    gate_strike: scn('성문 강타', [O], [{ t: 0.2, system: 'gate_strike', actor: 0 }], cam([0, 3, 12], 34, -60, 26)),
    enrage: scn('광폭화', [O], [{ t: 0.2, system: 'enrage', actor: 0 }], cam([0, 5, 0], 30, -70, 22)),
  } }),
};
