// ===== Siege_Somnia — 공성전 1공세 · 몽마 솜니아(봉인 전) 짤패턴 VFX =====
const WHITE = [1, 1, 1], LAV = [0.82, 0.68, 1], VIO = [0.55, 0.3, 0.95], GOLD = [1, 0.86, 0.5], AMB = [1, 0.6, 0.2], CYAN = [0.55, 0.9, 1], BLUE = [0.3, 0.45, 1], DEEP = [0.25, 0.08, 0.45];
const CV = { hot: WHITE, mid: LAV, low: VIO }, CG = { hot: WHITE, mid: GOLD, low: AMB }, CC = { hot: WHITE, mid: CYAN, low: BLUE }, CN = { hot: [0.9, 0.7, 1], mid: VIO, low: DEEP };
const MATS = mkMats({ V: [0.56, 0.36, 1], G: [1, 0.76, 0.28], C: [0.28, 0.78, 1], N: [0.46, 0.12, 0.95] }, {
  feather: { tex: 'T_Feather', tint: [2.0, 1.8, 2.6], add: true }, featherG: { tex: 'T_Feather', tint: [2.4, 2.1, 1.3], add: true },
  note: { tex: 'T_Note', tint: [2.0, 1.8, 2.6], add: true }, eyeO: { tex: 'T_EyeOpen', tint: [2.7, 2.2, 1.2], add: true }, eyeC: { tex: 'T_EyeClosed', tint: [1.5, 1.1, 2.7], add: true },
  bubble: { tex: 'T_Bubble', tint: [1.4, 1.9, 2.4], add: true }, zzz: { tex: 'T_Zzz', tint: [1.9, 1.7, 2.6], add: true }, moon: { tex: 'T_Moon', tint: [2.5, 2.3, 1.6], add: true }, spiral: { tex: 'T_Spiral', tint: [1.2, 0.8, 2.1], add: true } });
KIT = KITS.somnia;
const feathers = (tag, t, n, pos, sp, mat, o) => Object.assign({ name: `${tag} Feathers`, mat: mat || 'feather', fudge: -1, pos, life: [1.0, 1.6], size: [0.26, 0.42], rot: [0, TAU], rotZ: [-3, 3],
  shape: { type: 'sphere', r: 0.5, th: 1 }, bursts: [[t, n]], speed: sp, drag: 3, gravity: 0.12, noise: { s: 0.6, f: 0.8, sp: 0.5 }, alpha: [[0, 0], [0.08, 1], [0.7, 0.85], [1, 0]] }, o || {});
const moonFall = (tag, T, h, size) => [
  { name: `${tag} Moon`, mat: 'moon', fudge: -9, pos: [0, h, 0], size, rot: [-0.3, 0.3], bursts: [[0, 1]], life: T + 0.1, vel: { y: [-(h - 1) / T, -(h - 1) / T] }, sizeLife: [[0, 0.4], [0.3, 1], [1, 1.15]], alpha: [[0, 0], [0.2, 1], [0.92, 1], [1, 0]] },
  { name: `${tag} MoonTrail`, mat: 'streakG', mode: 'stretch', vs: 0.08, ls: 3, fudge: -4, pos: [0, h, 0], life: [0.3, 0.5], size: [0.1, 0.2], shape: { type: 'sphere', r: size * 0.3, th: 1 }, bursts: [[T * 0.3, 10], [T * 0.6, 12], [T * 0.85, 14]], vel: { y: [-(h) / T * 1.1, -(h) / T * 0.8] }, alpha: [[0, 1], [1, 0]] }];
const notes = (tag, t, n, r, v) => ({ name: `${tag} Notes`, mat: 'note', fudge: -4, life: [1.0, 1.5], size: [0.4, 0.65], rot: [-0.3, 0.3], shape: { type: 'circle', r, th: 0.1, y: 1.4 }, bursts: [[t, n]], vel: { rad: v, y: [0.4, 1.2] }, noise: { s: 0.3, f: 0.8, sp: 0.4 },
  sizeLife: [[0, 0.3, 0, 4], [0.2, 1], [1, 0.9]], alpha: [[0, 0], [0.12, 1], [0.75, 0.9], [1, 0]] });

const S = [];
// 솜니아의 공격 방식: 느리고, 따라오고, 오래 남는다.
// 1. 꿈방울 — 방울 몹을 따라다니는 연출(attached) + 닿았을 때 터짐
S.push(sysA('dream_orb', followOrb('do', { core: 'bubble', glow: 'glowV', trail: 'bubble', ground: 'eyeC' }, 2.2, 1.4).concat([
  { name: 'do Zzz', mat: 'zzz', fudge: -4, pos: [0, 2.4, 0], life: [1.0, 1.4], size: [0.35, 0.55], rot: [-0.4, 0.2], rate: 1.6, world: true, vel: { y: [0.8, 1.4] }, sizeLife: [[0, 0.3, 0, 3], [0.25, 1], [1, 1.2]], alpha: [[0, 0], [0.15, 1], [0.75, 0.9], [1, 0]], onStop: 0.2 },
  { name: 'do Moon', mat: 'moon', fudge: -9, pos: [0, 1.4, 0], size: 1.0, rate: 1 / 2.55, life: 3, bursts: [[0, 1]], rotZ: [1.2, 1.2], alpha: [[0, 0], [0.15, 1], [0.85, 1], [1, 0]], onStop: 0.3 }])));
S.push(sys('dream_orb_pop', blastCircle('dp', 2.5, 0.15, 'V', CV, { s2: 'C', dark: false, extra: [
  { name: 'dp BubbleBurst', mat: 'bubble', fudge: -3, pos: [0, 1.2, 0], life: [0.6, 1.0], size: [0.3, 0.7], shape: { type: 'sphere', r: 0.6, th: 1 }, bursts: [[0.15, 22]], speed: [3, 8], drag: 3, alpha: [[0, 1], [0.7, 0.9], [1, 0]] }] }), 0.15));
// 2. 돌아가는 시선 — 날개 눈에서 나온 광선이 5초 동안 천천히 돈다 (서버가 120° 간격으로 3개 소환)
S.push(sysD('gaze_sweep', sweepBeam('gz', 26, 1.6, 1.0, 5, 40 * D2R, 'C', CC, { s2: 'V', h: 2, extra: [
  { name: 'gz Eye', mat: 'eyeO', fudge: -9, pos: [0, 3.6, 0], size: 4.2, bursts: [[0, 1]], life: 6, sizeLife: [[0, 0.2, 0, 4], [0.1, 1], [1, 1]], alpha: [[0, 0], [0.08, 1], [0.95, 1], [1, 0]] }] }), 6.2));
// 3. 달의 궤도 — 달 셋이 보스 둘레를 돌며 6초 동안 바깥으로 나선을 그린다
S.push(sysD('moon_orbit', [
  { name: 'mo Moons', mat: 'moon', fudge: -9, pos: [0, 1.6, 0], size: 2.8, rotZ: [2, 2], shape: { type: 'circle', r: 3, th: 0, y: 0 }, bursts: [[0.6, 3]], life: 6, vel: { orb: [1.15, 1.15], rad: [2.3, 2.3] }, alpha: [[0, 0], [0.06, 1], [0.94, 1], [1, 0]] },
  { name: 'mo MoonGlow', mat: 'glowG', fudge: 2, pos: [0, 1.6, 0], size: 5, shape: { type: 'circle', r: 3, th: 0, y: 0 }, bursts: [[0.6, 3]], life: 6, vel: { orb: [1.15, 1.15], rad: [2.3, 2.3] }, alpha: [[0, 0], [0.06, 0.35], [0.94, 0.35], [1, 0]] },
  hold('mo Spiral', 'spiral', 10, 0, 6.6, { rotLife: [0, 1.15, 0], sizeLife: [[0, 0.6], [1, 3.4]], alpha: [[0, 0], [0.1, 0.5], [0.9, 0.35], [1, 0]] }),
  hold('mo Eye', 'eyeC', 7, 0, 0.62, {}), hold('mo EyeOpen', 'eyeO', 7, 0.6, 6, { rot3: [0, 0, 0], alpha: [[0, 0], [0.05, 0.9], [0.9, 0.6], [1, 0]] }),
  { name: 'mo Stars', mat: 'starG', fudge: -8, pos: [0, 1.2, 0], life: [0.4, 0.8], size: [0.25, 0.5], rot: [0, TAU], shape: { type: 'circle', r: 17, th: 1, y: 0 }, rate: 14, sizeLife: [[0, 0.2], [0.4, 1], [1, 0]] },
  { name: 'mo Feathers', mat: 'feather', fudge: -1, pos: [0, 2, 0], life: [1.4, 2.0], size: [0.24, 0.4], rot: [0, TAU], rotZ: [-3, 3], shape: { type: 'circle', r: 4, th: 1, y: 0 }, rate: 8, vel: { orb: [1, 1.4], rad: [1.5, 2.5], y: [-0.3, 0.4] }, alpha: [[0, 0], [0.1, 1], [0.7, 0.85], [1, 0]] }], 6.6));
// 4. 자장가 물결 — 2.2초에 걸쳐 느리게 퍼지는 고리 (서버가 간격을 두고 3번)
S.push(sysD('lullaby_wave', travelRing('lu', 2, 18, 0.7, 2.2, 'V', CV, { s2: 'C', extra: [notes('lu', 0.7, 14, 3, [5, 7.5]), notes('lu2', 1.5, 14, 9, [3, 5]),
  hold('lu Eye', 'eyeC', 5, 0, 0.9, {})] }), 3.2));
// 5. 악몽의 잔상 — 플레이어가 지나온 자리에 1.5초 늦게 터지는 발자국
S.push(sys('nightmare_echo', blastCircle('ne', 2, 0.5, 'N', CN, { s2: 'V', extra: [
  { name: 'ne Shade', mat: 'mist', fudge: 24, pos: [0, 1, 0], life: 0.6, size: [1.6, 2], rot: [0, TAU], shape: { type: 'box', sx: 0.3, sy: 1.2, sz: 0.3 }, bursts: [[0, 4]], alpha: [[0, 0], [0.3, 0.8], [0.8, 0.8], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'ne Eyes', mat: 'starN', fudge: -8, pos: [0, 1.6, 0], life: 0.55, size: 0.3, shape: { type: 'box', sx: 0.35, sy: 0.05, sz: 0.05 }, bursts: [[0, 2]], alpha: [[0, 0], [0.3, 1], [0.9, 1], [1, 0]] }] }), 0.5));
// 6. 하품 — 2초 동안 보스 쪽으로 빨아들인 뒤 보스 주변이 터진다
S.push(sysD('yawn_pull', [
  hold('yp Spiral', 'spiral', 36, 0, 2.2, { rotLife: [0, -3.5, 0], sizeLife: [[0, 1], [1, 0.35]], alpha: [[0, 0], [0.15, 0.6], [0.85, 0.7], [1, 0]] }),
  hold('yp Eye', 'eyeC', 14, 0, 2.2, { rot3: [0, 0, 0] }),
  { name: 'yp Streaks', mat: 'streakV', mode: 'stretch', vs: 0.06, ls: 2.4, fudge: -3, pos: [0, 1.2, 0], life: [0.7, 1.0], size: [0.1, 0.2], shape: { type: 'circle', r: 17, th: 0.35, y: 0 }, rate: 110, vel: { rad: [-15, -10], orb: [0.5, 1] }, alpha: [[0, 0], [0.2, 1], [1, 0]] },
  { name: 'yp Bubbles', mat: 'bubble', fudge: -3, pos: [0, 1, 0], life: [0.9, 1.3], size: [0.25, 0.5], shape: { type: 'circle', r: 16, th: 0.4, y: 0 }, rate: 26, vel: { rad: [-11, -8], y: [0.3, 1] }, alpha: [[0, 0], [0.2, 0.9], [0.9, 0.8], [1, 0]] },
  { name: 'yp Feathers', mat: 'feather', fudge: -1, pos: [0, 1.4, 0], life: [0.9, 1.3], size: [0.26, 0.42], rot: [0, TAU], rotZ: [-4, 4], shape: { type: 'circle', r: 16, th: 0.4, y: 0 }, rate: 22, vel: { rad: [-11, -8], orb: [0.5, 1.2] }, alpha: [[0, 0], [0.2, 1], [0.9, 0.85], [1, 0]] },
  { name: 'yp Mouth', mat: 'glowN', fudge: 2, pos: [0, 6, 0], size: 9, bursts: [[0, 1]], life: 2.2, sizeLife: [[0, 0.4], [1, 1.3]], alpha: [[0, 0], [0.8, 0.5], [1, 0]] }], 2.2));
S.push(sys('yawn_burst', blastCircle('yb', 7, 0.3, 'N', CN, { s2: 'V', extra: [feathers('yb', 0.3, 30, [0, 1.5, 0], [6, 14])] }), 0.3));
// 7. 잠의 안개 — 8초 동안 남는 구역 (들어가면 둔화 + 지속 피해)
S.push(sysD('sleep_fog', [
  hold('sf Eye', 'eyeC', 10.6, 0, 8, { rot3: [0, 0, 0] }), hold('sf Ring', 'lineV', 5, 0, 8, { mesh: 'cOuter', rotLife: [0, 0.4, 0] }),
  { name: 'sf Fog', mat: 'dustV', fudge: 20, pos: [0, 0.8, 0], life: [1.4, 2.0], size: [2.2, 3.4], rot: [0, TAU], shape: { type: 'circle', r: 4.4, th: 1, y: 0 }, rate: 10, bursts: [[0, 8]], vel: { orb: [0.3, 0.6], y: [0.1, 0.3] }, sizeLife: [[0, 0.7], [1, 1.3]], alpha: [[0, 0], [0.25, 0.55], [1, 0]], flip: { x: 4, y: 4 } },
  { name: 'sf Zzz', mat: 'zzz', fudge: -4, pos: [0, 1.6, 0], life: [1.2, 1.6], size: [0.4, 0.65], rot: [-0.4, 0.2], shape: { type: 'circle', r: 4, th: 1, y: 0 }, rate: 2.2, vel: { y: [0.8, 1.4] }, sizeLife: [[0, 0.3, 0, 3], [0.25, 1], [1, 1.2]], alpha: [[0, 0], [0.15, 1], [0.75, 0.9], [1, 0]] },
  { name: 'sf Bubbles', mat: 'bubble', fudge: -3, life: [1.2, 1.8], size: [0.2, 0.42], shape: { type: 'circle', r: 4.6, th: 1, y: 0.1 }, rate: 7, vel: { y: [0.7, 1.5] }, noise: { s: 0.5, f: 1, sp: 0.5 }, alpha: [[0, 0], [0.2, 0.9], [0.9, 0.8], [1, 0]] },
  { name: 'sf Twinkle', mat: 'starV', fudge: -8, pos: [0, 0.6, 0], life: [0.4, 0.8], size: [0.2, 0.4], rot: [0, TAU], shape: { type: 'circle', r: 5, th: 0.05, y: 0 }, rate: 7, sizeLife: [[0, 0.2], [0.4, 1], [1, 0]] }], 8));
// 8. 눈꺼풀 — 양쪽 끝에서 가운데로 닫혀 오는 띠 (서버가 바깥 줄부터 차례로, 가운데 띠만 안전)
S.push(sys('eyelid_band', blastRect('eb', 36, 6, 0.8, 'N', CN, { s2: 'V', extra: [
  { name: 'eb Lashes', mat: 'streakV', mode: 'stretch', vs: 0.05, ls: 4, fudge: -3, pos: [0, 0.3, 0], life: [0.35, 0.5], size: [0.12, 0.22], shape: { type: 'box', sx: 35, sy: 0.1, sz: 0.4 }, bursts: [[0.8, 40]], vel: { y: [9, 14], z: [-2, 2] }, drag: 3, alpha: [[0, 1], [1, 0]] }] }), 0.8));
// 9. 만월 낙하 (광폭화 후) — 2초에 걸쳐 내려오는 큰 달
S.push(sys('full_moon', blastCircle('fm', 8, 2.0, 'G', CG, { s2: 'V', extra: moonFall('fmM', 2.0, 26, 13).concat([feathers('fm', 2.0, 40, [0, 1.5, 0], [6, 14]),
  { name: 'fm Shadow', mat: 'glowN', mode: 'mesh', mesh: 'quad', fudge: 36, pos: [0, 0.04, 0], size: 17, rot3: YAW, bursts: [[0, 1]], life: 2.0, sizeLife: [[0, 0.3], [1, 1]], alpha: [[0, 0], [0.9, 0.6], [1, 0]] }]) }), 2.0));
S.push(sys('gate_strike', blastLane('gs', 5, 24, 1.5, 'G', CG, { s2: 'N', h: 7, extra: moonFall('gsM', 1.5, 16, 7).map(l => Object.assign(l, { pos: [0, l.pos[1], 22] })) }), 1.5));
S.push(sys('enrage', enrageFx('en', 7, 9, 'V', 'N', CN).concat([feathers('en', 0.6, 60, [0, 5, 0], [8, 16], 'feather', { life: [1.4, 2.2] })]), 1.2));

var EXTRA_MESHES = Object.assign({}, CIRCLE_MESHES, SM);
const O = HID(0, 0, 0);
const SCENE = {
  name: 'Siege_Somnia', bloom: { threshold: 0.95, intensity: 1.3, scatter: 0.7, tint: [0.85, 0.8, 1] }, tonemapping: 'neutral', systems: S,
  demo: Object.assign({}, DEMO_BG, { interval: 7, dummy: { height: 1.8 }, scenarios: {
    dream_orb: scn('1. 꿈방울 (쫓아오다 닿으면 터짐)', [MOVER([[0, [-10, 0, 8], 0], [4, [0, 0, 0], 0]]), MOVER([[0, [9, 0, 10], 0], [4, [1.5, 0, 1], 0]]), MOVER([[0, [2, 0, -11], 0], [4, [-1, 0, -1.5], 0]]), HID(0, 0), HID(1.5, 1), HID(-1, -1.5), { path: [[0, [0, 0, 0], 0]] }],
      [0, 1, 2].map(a => ({ t: 0, system: 'dream_orb', actor: a })).concat([{ t: 4, stop: ['dream_orb'] }, ...[3, 4, 5].map(a => ({ t: 4, system: 'dream_orb_pop', actor: a }))]), cam([0, 1.5, 2], 26, -70, 30)),
    gaze_sweep: scn('2. 돌아가는 시선 (광선 3개가 5초간 회전)', [HID(0, 0, 0), HID(0, 0, 120), HID(0, 0, 240)], [0, 1, 2].map(a => ({ t: 0.2, system: 'gaze_sweep', actor: a })), cam([0, 2, 0], 60, -90, 44)),
    moon_orbit: scn('3. 달의 궤도 (나선으로 퍼지는 달 셋)', [O], [{ t: 0.2, system: 'moon_orbit', actor: 0 }], cam([0, 2, 0], 48, -90, 42)),
    lullaby_wave: scn('4. 자장가 물결 (느린 고리 3번)', [O], [0, 1, 2].map(i => ({ t: 0.2 + i * 1.1, system: 'lullaby_wave', actor: 0 })), cam([0, 2, 0], 50, -90, 36)),
    nightmare_echo: scn('5. 악몽의 잔상 (지나온 자리가 늦게 터짐)', Array.from({ length: 10 }, (_, i) => HID(-9 + i * 2, Math.sin(i * 0.7) * 4)).concat([{ path: [[0, [-9, 0, 0], 90], [3, [11, 0, 0], 90]] }]),
      Array.from({ length: 10 }, (_, i) => ({ t: 1.5 + i * 0.3, system: 'nightmare_echo', actor: i })), cam([0, 1.5, 0], 30, -90, 34)),
    yawn: scn('6. 하품 (빨아들인 뒤 폭발)', [O], [{ t: 0.2, system: 'yawn_pull', actor: 0 }, { t: 2.3, system: 'yawn_burst', actor: 0 }], cam([0, 3, 0], 48, -80, 34)),
    sleep_fog: scn('7. 잠의 안개 (8초 남는 구역)', [HID(-6, 3), HID(5, -2), HID(1, 8)], [0, 1, 2].map((a, i) => ({ t: 0.2 + i * 0.6, system: 'sleep_fog', actor: a })), cam([0, 1.5, 2], 30, -70, 34)),
    eyelid: scn('8. 눈꺼풀 (양끝에서 가운데로 닫힘)', [-21, 21, -15, 15, -9, 9].map(z => HID(0, z)), [0, 1, 2, 3, 4, 5].map(a => ({ t: 0.2 + Math.floor(a / 2) * 0.7, system: 'eyelid_band', actor: a })), cam([0, 2, 0], 70, -70, 38)),
    full_moon: scn('9. 만월 낙하 (광폭화 후)', [HID(0, 4)], [{ t: 0.2, system: 'full_moon', actor: 0 }], cam([0, 5, 4], 40, -65, 22)),
    gate_strike: scn('성문 강타', [O], [{ t: 0.2, system: 'gate_strike', actor: 0 }], cam([0, 3, 12], 34, -60, 26)),
    enrage: scn('광폭화', [O], [{ t: 0.2, system: 'enrage', actor: 0 }], cam([0, 5, 0], 30, -70, 22)),
  } }),
};
