// MordianChainBind (모르디안의 원령 — 기믹 ① 결박의 사슬)
// A black chain (faded-gold rimmed ink links, wound shadow band, violet afterglow) binds two players for up to 20 s. Safe distance 8–14 m.
// Link frame (SCHEMA §5): source = player A chest (feet + 1.0 m), target = player B chest, every frame; z runs A → B.
// Systems (all role "link", same two points), started / stopped by the game:
//   bind_hold   base loop (start at bind; stop at the end → links fade in 0.25 s via onStop) — chain + shackle rings at both players
//   bind_slack  extra warning loop while distance < 8 m: the chain sags and shadow coils wind around it (start when entering, stop when leaving)
//   bind_taut   extra warning loop while distance > 14 m: straight violet arcs, crack light and sparks run along the strained chain
//   bind_break  one shot at the end (time out / mechanic resolved / punishment): the chain snaps into ink + gold shards
const TAU = Math.PI * 2;
const WH = [0.97,0.92,1], LIL = [0.84,0.6,1], VIO = [0.54,0.24,1], DVIO = [0.3,0.12,0.6], GOLD = [1,0.86,0.55];
const LIFE_HOLD = 60, FORM = 0.35;
const tailK = tail => 1 - tail / LIFE_HOLD;
const lin = (a, b) => [[0, a, 0, b - a], [1, b, b - a, 0]];
const cst = s => [[0, s, 0, 0], [1, s, 0, 0]];
const range = n => Array.from({ length: n }, (_, k) => k);
const NOWAVE = { amp:0, cycles:1, phase:0, vr:1 };
const SAG = { x:0, y:-0.28 };
const pth = (progress, bend = SAG, wave = NOWAVE) => ({ progress, bend, wave });

// ---------------------------------------------------------------- chain links: stationary, alternating face-on / edge-on, drawn from both players to the middle
const N = 28, LINK = 0.46;
const sK = k => (k + 0.5) / N;
const linkMesh = k => (k % 2 ? "linkH" : "linkV");
const linkMat = k => (k % 2 ? "bar" : "link");
const LINKS = range(N).map(k => {
  const t0 = FORM * 0.9 * (1 - 2 * Math.abs(sK(k) - 0.5)), kp = 0.06 / LIFE_HOLD;
  return { name:`H${String(k).padStart(2,"0")} Link`, mat:linkMat(k), mode:"mesh", mesh:linkMesh(k), fudge:2, life:LIFE_HOLD, size:LINK, rot3:[0,0,0],
    bursts:[[t0,1]], path:pth(cst(sK(k))), alpha:[[0,0],[kp * 0.4,1],[tailK(0.25),1],[1,0]], sizeLife:[[0,1.6],[kp,1],[tailK(0.25),1],[1,0.6]], onStop:0.25 };
});
// shadow band along the chain: NB segments (head travels 0.5·DS, quad trails 0.9·DS; lengths follow linkLength)
// fixed-length stretched segments (SCHEMA §4.7: length = size·ls + |v|·vs; vs kept tiny so players walking apart/together never stretch them past the ends)
const NB = 20, DS = 1 / NB;
const seg = (k, dir, nb = NB) => { const ds = 1 / nb, c = (k + 0.5) * ds; return lin(c - dir * 0.15 * ds, c + dir * 0.15 * ds); };
const inward = (k, nb = NB) => (k < nb / 2 ? -1 : 1);       // first half trails toward B, second half toward A → nothing sticks out past a player
const bandSeg = (tag, k, o) => {
  const life = o.life, nb = o.nb ?? NB, dir = inward(k, nb), bend = o.bend || SAG, wave = o.wave || NOWAVE, len = o.len ?? 0.8, vs = 0.02;
  const out = [];
  if (o.band) out.push({ name:`${tag}${String(k).padStart(2,"0")} Band`, mat:o.bandMat || "band", mode:"stretch", ls:len / ((o.band[0] + o.band[1]) / 2), vs, fudge:4, life, size:o.band,
    bursts:o.bursts, rate:o.rate, path:{ progress:seg(k, dir, nb), bend, wave }, alpha:[[0,0],[0.25,o.bandA ?? 0.85],[0.75,o.bandA ?? 0.85],[1,0]], onStop:o.stop ?? 0.2 });
  if (o.haze) out.push({ name:`${tag}${String(k).padStart(2,"0")} Haze`, mat:o.hazeMat || "haze", mode:"stretch", ls:len / ((o.haze[0] + o.haze[1]) / 2), vs, fudge:6, life, size:o.haze,
    bursts:o.bursts, rate:o.rate, path:{ progress:seg(k, dir, nb), bend, wave }, color:o.hazeColor || [[0,LIL],[1,VIO]],
    alpha:[[0,0],[0.3,o.hazeA ?? 0.14],[0.7,o.hazeA ?? 0.14],[1,0]], onStop:o.stop ?? 0.2 });
  return out;
};
// both ends: shackle ring (XY, the chain passes through it), waist ring (XZ), small glow — `at` = {} (A, link source) or {path} (B, target)
const AT_B = { path:pth(cst(1), { x:0, y:0 }) };
const endHeld = (tag, at, t0) => [
  { name:`${tag}0 Shackle`, mat:"shackle", mode:"mesh", mesh:"ringXY", fudge:-1, life:LIFE_HOLD, size:0.55, rot3:[0,0,0], rotLife:[0,0,1.2], bursts:[[t0,1]], ...at,
    alpha:[[0,0],[0.002,1],[tailK(0.25),1],[1,0]], sizeLife:[[0,0.3,0,0],[0.003,1.15],[0.006,1],[tailK(0.25),1],[1,1.4]], onStop:0.25 },
  { name:`${tag}1 Waist`, mat:"ring", mode:"mesh", mesh:"quad", fudge:-1, life:LIFE_HOLD, size:1.05, rot3:[0,0,0], rotLife:[0,0.6,0], bursts:[[t0,1]], ...at,
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.003,0.7],[tailK(0.25),0.7],[1,0]], sizeLife:[[0,1.6],[0.004,1],[tailK(0.25),1],[1,1.3]], onStop:0.25 },
  { name:`${tag}2 Glow`, mat:"glow", fudge:-3, life:1.2, size:[0.55,0.65], rate:1.6, ...at, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.5,0.3],[1,0]], onStop:0.2 },
  { name:`${tag}3 Flash`, mat:"star", fudge:-6, life:0.22, size:0.9, rot:[0,0.8], bursts:[[t0,1]], ...at, color:[[0,WH],[1,VIO]], alpha:[[0,1],[1,0]],
    sizeLife:[[0,0.4],[0.3,1],[1,0.6]] },
  { name:`${tag}4 InkCurl`, mat:"ink", mode:"mesh", mesh:"curl", fudge:3, life:2.4, size:[0.42,0.5], rot3:[0,0,[0,TAU]], rotLife:[0,0,-1.2], rate:1.25,
    bursts:[[t0,2]], ...at, alpha:[[0,0],[0.3,0.9],[0.7,0.9],[1,0]], onStop:0.2 },
];
const HOLD = [
  ...LINKS,
  ...range(NB).flatMap(k => bandSeg("B", k, { life:0.6, rate:3.4, bursts:[[FORM * 0.9 * (1 - 2 * Math.abs((k + 0.5) / NB - 0.5)), 2]], band:[0.22,0.26], haze:[0.4,0.48], hazeA:0.2 })),
  { name:"C00 Motes", mat:"dot", fudge:-3, life:[2.2,3.2], size:[0.04,0.07], rate:14,
    path:{ progress:lin(0.06, 0.94), bend:{x:[-0.35,0.35], y:[0.0,0.7]}, wave:{amp:[0,0.15], cycles:[0.5,1.5], phase:[0,TAU], vr:1} },
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.15,0.9],[0.8,0.7],[1,0]], sizeLife:[[0,0.6],[0.3,1],[1,0.5]], onStop:0.2 },
  ...[0.2, 0.45, 0.7, 0.9].map((s, j) => ({ name:`C1${j} Glint`, mat:"star", fudge:-5, life:[0.14,0.2], size:[0.16,0.24], rot:[0,0.8], rate:1.2,
    path:pth(cst(s)), color:[[0,WH],[1,GOLD]], alpha:[[0,0],[0.3,1],[1,0]], onStop:0.05 })),
  { name:"C20 Pulse", mat:"streak", mode:"stretch", ls:1.2, vs:0.02, fudge:-2, life:2.2, size:0.08, rate:0.9, path:pth(lin(0.03, 0.97)),
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.1,0.7],[0.9,0.7],[1,0]], onStop:0.15 },
  // form: two heads from both players meet in the middle
  { name:"C30 HeadA", mat:"star", fudge:-6, life:FORM * 0.9, size:0.45, rot:[0,0.8], bursts:[[0,1]], path:pth([[0,0,0,0.9],[1,0.5,0.4,0]]),
    color:[[0,WH],[1,LIL]], alpha:[[0,1],[0.9,1],[1,0]] },
  { name:"C31 HeadB", mat:"star", fudge:-6, life:FORM * 0.9, size:0.45, rot:[0,0.8], bursts:[[0,1]], path:pth([[0,1,0,-0.9],[1,0.5,-0.4,0]]),
    color:[[0,WH],[1,LIL]], alpha:[[0,1],[0.9,1],[1,0]] },
  { name:"C32 MeetFlash", mat:"glow", fudge:-7, life:0.3, size:1.0, bursts:[[FORM * 0.88,1]], path:pth(cst(0.5)), color:[[0,LIL],[1,VIO]], alpha:[[0,0.7],[1,0]],
    sizeLife:[[0,0.4],[0.25,1],[1,0.8]] },
  { name:"C33 MeetScratch", mat:"scratch", fudge:-6, life:0.45, size:0.8, rot:[-0.2,0.2], bursts:[[FORM * 0.88,1]], path:pth(cst(0.5)), color:[[0,WH],[1,VIO]],
    alpha:[[0,0],[0.1,1],[0.5,0.7],[1,0]], sizeLife:[[0,0.7],[0.2,1],[1,1.1]] },
  ...endHeld("A", {}, 0), ...endHeld("E", AT_B, 0),
  { name:"A5 Mist", mat:"mist", world:true, fudge:9, life:[0.7,1.0], size:[0.5,0.7], shape:{type:"sphere", r:0.25}, rate:4, rot:[0,TAU], flip:{x:4,y:4},
    vel:{y:-0.5, space:"world"}, alpha:[[0,0.45],[1,0]], onStop:0.2 },
];

// ---------------------------------------------------------------- SLACK (distance < 8 m): chain sags, shadow coils wind around it
const DROOP = { x:[-0.08,0.08], y:[-1.05,-0.85] };
const SLACK = [
  ...range(14).flatMap(k => bandSeg("S", k, { nb:14, len:0.75, life:0.9, rate:2.6, bursts:[[0,2]], band:[0.16,0.2], bandMat:"bar2", bandA:0.75, bend:DROOP, dir:1 })),     // sagging ghost chain
  ...range(14).flatMap(k => bandSeg("T", k, { nb:14, len:0.8, life:0.7, rate:3, bursts:[[0,2]], haze:[0.55,0.7], hazeMat:"dark", hazeA:0.5, bend:{ x:0, y:-0.65 }, dir:-1,
    hazeColor:[[0,[1,1,1]],[1,[1,1,1]]] })),                                                                                                                  // drooping dark mist
  { name:"U00 CoilsA", mat:"ink", mode:"stretch", ls:0.6, vs:0.02, fudge:3, life:[1.4,1.8], size:[0.07,0.11], rate:16, bursts:[[0,10]],
    path:{ progress:lin(0.03, 0.97), bend:{ x:0, y:-0.45 }, wave:{amp:[0.22,0.3], cycles:[9,11], phase:[0,TAU], vr:1} }, alpha:[[0,0],[0.1,1],[0.9,1],[1,0]], onStop:0.25 },
  { name:"U01 CoilsB", mat:"ink", mode:"stretch", ls:0.6, vs:0.02, fudge:3, life:[1.4,1.8], size:[0.07,0.11], rate:16, bursts:[[0,10]],
    path:{ progress:lin(0.97, 0.03), bend:{ x:0, y:-0.45 }, wave:{amp:[0.22,0.3], cycles:[9,11], phase:[0,TAU], vr:-1} }, alpha:[[0,0],[0.1,1],[0.9,1],[1,0]], onStop:0.25 },
  { name:"U02 CoilGlow", mat:"streak", mode:"stretch", ls:0.8, vs:0.02, fudge:-2, life:[1.2,1.5], size:[0.04,0.06], rate:10,
    path:{ progress:lin(0.97, 0.03), bend:{ x:0, y:-0.45 }, wave:{amp:[0.26,0.32], cycles:[9,11], phase:[0,TAU], vr:-1} }, color:[[0,LIL],[1,VIO]],
    alpha:[[0,0],[0.15,0.8],[0.85,0.8],[1,0]], onStop:0.2 },
  { name:"U03 Drips", mat:"inkshard", fudge:5, life:[0.5,0.7], size:[0.14,0.22], rot:[1.45,1.7], rate:14,
    path:{ progress:[[0,0.3],[1,0.3]], bend:{ x:[-0.1,0.1], y:[-1.2,-0.9] }, wave:NOWAVE }, alpha:[[0,0],[0.2,1],[1,0]], sizeLife:[[0,0.5],[1,1.2]], onStop:0.1 },
  { name:"U04 Drips2", mat:"inkshard", fudge:5, life:[0.5,0.7], size:[0.14,0.22], rot:[1.45,1.7], rate:14,
    path:{ progress:[[0,0.65],[1,0.65]], bend:{ x:[-0.1,0.1], y:[-1.2,-0.9] }, wave:NOWAVE }, alpha:[[0,0],[0.2,1],[1,0]], sizeLife:[[0,0.5],[1,1.2]], onStop:0.1 },
  // shackles tighten (warning pulse at both players, 1.6 / s)
  ...[["V", {}], ["W", AT_B]].flatMap(([tag, at]) => [
    { name:`${tag}0 Tighten`, mat:"shackle", mode:"mesh", mesh:"ringXY", fudge:-2, life:0.6, size:0.55, rot3:[0,0,[0,TAU]], rate:1.6, bursts:[[0,1]], ...at,
      color:[[0,WH],[1,VIO]], alpha:[[0,0],[0.15,0.9],[1,0]], sizeLife:[[0,2.0,0,-2],[1,0.95]], onStop:0.1 },
    { name:`${tag}1 WaistTighten`, mat:"ring", mode:"mesh", mesh:"quad", fudge:-2, life:0.6, size:1.0, rot3:[0,[0,TAU],0], rate:1.6, bursts:[[0,1]], ...at,
      color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.15,0.8],[1,0]], sizeLife:[[0,1.7,0,-1.4],[1,0.85]], onStop:0.1 },
    { name:`${tag}2 Coil`, mat:"ink", mode:"mesh", mesh:"coil", fudge:3, life:1.0, size:0.5, rot3:[0,[0,TAU],0], rotLife:[0,-3,0], rate:1.4, bursts:[[0,1]], ...at,
      alpha:[[0,0],[0.25,0.9],[0.75,0.9],[1,0]], sizeLife:[[0,1.4],[1,0.9]], onStop:0.15 },
  ]),
];

// ---------------------------------------------------------------- TAUT (distance > 14 m): straight, violet current and crack light along the chain
const STRAIGHT = { x:0, y:-0.05 };
const TAUT = [
  ...range(NB).flatMap(k => bandSeg("K", k, { len:0.95, life:0.12, rate:14, bursts:[[0,2]], haze:[0.12,0.16], hazeMat:"crack", hazeA:0.9, bend:STRAIGHT,
    wave:{ amp:[0,0.04], cycles:[6,10], phase:[0,TAU], vr:1 }, dir:k % 2 ? 1 : -1, hazeColor:[[0,WH],[1,LIL]], stop:0.05 })),                       // crack light core
  ...range(NB).map(k => ({ name:`L0${String(k).padStart(2,"0")} Arcs`, mat:"bolt", mode:"stretch", ls:1.1 / 0.3, vs:0.02, fudge:-3, life:0.1, size:[0.24,0.36],
    rate:9, bursts:[[0,1]], flip:{ x:1, y:4, cycles:2 }, path:{ progress:seg(k, inward(k)), bend:{ x:[-0.12,0.12], y:[-0.15,0.05] }, wave:{amp:[0.02,0.1], cycles:[3,6], phase:[0,TAU], vr:1} },
    color:[[0,WH],[1,VIO]], alpha:[[0,0.4],[0.2,1],[1,0]], onStop:0.03 })),
  { name:"L02 Current", mat:"streak", mode:"stretch", ls:0.5, vs:0.012, fudge:-2, life:[0.22,0.3], size:[0.08,0.12], rate:30, bursts:[[0,6]],
    path:{ progress:lin(0.06, 0.94), bend:STRAIGHT, wave:{amp:[0.05,0.1], cycles:[4,8], phase:[0,TAU], vr:1} }, color:[[0,WH],[0.4,LIL],[1,VIO]],
    alpha:[[0,0],[0.15,1],[0.85,1],[1,0]], onStop:0.05 },
  ...[0.22, 0.5, 0.78].map((s, j) => ({ name:`L1${j} Scratch`, mat:"scratch", fudge:-6, life:0.3, size:[0.45,0.6], rot:[-0.4,0.4], rate:1.4, bursts:[[0.05 * j,1]],
    path:pth(cst(s), STRAIGHT), color:[[0,WH],[1,VIO]], alpha:[[0,0],[0.12,1],[1,0]], sizeLife:[[0,0.8],[0.2,1],[1,1.1]], onStop:0.05 })),
  ...[0.15, 0.35, 0.6, 0.85].map((s, j) => ({ name:`L2${j} Spit`, mat:"dot", mode:"stretch", ls:1, vs:0.02, fudge:-4, life:[0.18,0.3], size:[0.03,0.05], rate:12,
    path:{ progress:[[0,s,0,0],[1,s + (j % 2 ? 0.03 : -0.03),0,0]], bend:{x:[-0.7,0.7], y:[-0.3,0.8]}, wave:NOWAVE }, color:[[0,WH],[0.4,LIL],[1,VIO]],
    alpha:[[0,1],[1,0]], onStop:0.05 })),
  // strain at both players: shackle flicker + glow + sparks
  ...[["M", {}], ["P", AT_B]].flatMap(([tag, at]) => [
    { name:`${tag}0 Strain`, mat:"shackle", mode:"mesh", mesh:"ringXY", fudge:-2, life:0.12, size:[0.6,0.68], rot3:[0,0,[0,TAU]], rate:12, bursts:[[0,1]], ...at,
      color:[[0,WH],[1,LIL]], alpha:[[0,0],[0.3,0.8],[1,0]], onStop:0.03 },
    { name:`${tag}1 StrainGlow`, mat:"glow", fudge:-4, life:0.16, size:[0.8,0.95], rate:10, ...at, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.4,0.5],[1,0]], onStop:0.03 },
    { name:`${tag}2 Star`, mat:"star", fudge:-6, life:0.1, size:[0.4,0.6], rot:[0,0.8], rate:6, ...at, color:[[0,WH],[1,LIL]], alpha:[[0,1],[1,0]], onStop:0.02 },
  ]),
  { name:"M3 Sparks", mat:"dot", mode:"stretch", ls:1, vs:0.03, world:true, fudge:-4, life:[0.2,0.35], size:[0.025,0.045], speed:[2.5,4.5], shape:{type:"sphere", r:0.12},
    rate:30, drag:3, gravity:0.5, color:[[0,WH],[0.4,LIL],[1,VIO]], alpha:[[0,1],[1,0]] },
];

// ---------------------------------------------------------------- BREAK (one shot)
const BREAK = [
  ...range(13).flatMap(j => {
    const s = (j + 0.5) / 13, b = { progress:[[0,s,0,0.25],[1,s + 0.04 * (j % 2 ? 1 : -1),0,0]], bend:{ x:[-0.6,0.6], y:[-0.6,0.5] }, wave:{amp:[0,0.25], cycles:[1,2], phase:[0,TAU], vr:1} };
    const t0 = 0.012 * Math.abs(j - 6);                                            // snaps in the middle first
    return [
      { name:`X${String(j).padStart(2,"0")} LinkShards`, mat:"linkshard", mode:"stretch", ls:1.6, vs:0.1, fudge:1, life:[0.4,0.6], size:[0.08,0.13], bursts:[[t0, 2]],
        path:b, alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1.1],[1,0.5]] },
      { name:`X${String(j).padStart(2,"0")} InkShards`, mat:"inkshard", mode:"stretch", ls:1.8, vs:0.1, fudge:2, life:[0.45,0.7], size:[0.09,0.15], bursts:[[t0, 2]],
        path:{ ...b, bend:{ x:[-0.9,0.9], y:[-0.7,0.9] } }, alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1.2],[1,0.4]] },
    ];
  }),
  ...[0.12, 0.32, 0.5, 0.68, 0.88].map((s, j) => ({ name:`X3${j} InkPuff`, mat:"mist", fudge:8, life:[0.6,0.9], size:[0.5,0.8], rot:[0,TAU], flip:{x:4,y:4},
    bursts:[[0.015 * Math.abs(j - 2), 2]], path:{ progress:[[0,s],[1,s + 0.02]], bend:{x:[-0.3,0.3], y:[-0.3,0.2]}, wave:NOWAVE }, alpha:[[0,0.75],[1,0]], sizeLife:[[0,0.6],[1,1.2]] })),
  { name:"X40 MidFlash", mat:"glow", fudge:-7, life:0.3, size:1.3, bursts:[[0,1]], path:pth(cst(0.5)), color:[[0,LIL],[1,VIO]], alpha:[[0,0.75],[1,0]], sizeLife:[[0,0.4],[0.25,1],[1,0.8]] },
  { name:"X41 MidScratch", mat:"scratch", fudge:-6, life:0.5, size:1.0, rot:[-0.2,0.2], bursts:[[0,1]], path:pth(cst(0.5)), color:[[0,WH],[1,VIO]],
    alpha:[[0,0],[0.08,1],[0.5,0.7],[1,0]], sizeLife:[[0,0.8],[0.2,1],[1,1.15]] },
  { name:"X42 Sparks", mat:"star", fudge:-5, life:[0.12,0.2], size:[0.2,0.32], rot:[0,0.8], bursts:[[0,6],[0.05,5]],
    path:{ progress:[[0,0.1],[1,0.9,0.8,0.8]], bend:{x:[-0.4,0.4], y:[-0.2,0.3]}, wave:{amp:[0,0.2], cycles:1, phase:[0,TAU], vr:1} }, color:[[0,WH],[1,LIL]], alpha:[[0,1],[1,0]] },
  { name:"X43 Motes", mat:"dot", fudge:-3, life:[0.7,1.1], size:[0.04,0.08], bursts:[[0.03,30]],
    path:{ progress:[[0,0.05],[1,0.95,0.9,0.9]], bend:{x:[-0.7,0.7], y:[0.2,1.1]}, wave:{amp:[0,0.2], cycles:1, phase:[0,TAU], vr:1} }, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.15,1],[1,0]] },
  ...[["Y", {}], ["Z", AT_B]].flatMap(([tag, at]) => [
    { name:`${tag}0 ShackleBurst`, mat:"shackle", mode:"mesh", mesh:"ringXY", fudge:-2, life:0.35, size:0.55, rot3:[0,0,0], rotLife:[0,0,4], bursts:[[0,1]], ...at,
      color:[[0,WH],[1,VIO]], alpha:[[0,1],[1,0]], sizeLife:[[0,1,0,2],[1,2.0]] },
    { name:`${tag}1 Flash`, mat:"star", fudge:-6, life:0.2, size:0.9, rot:[0,0.8], bursts:[[0,1]], ...at, color:[[0,WH],[1,VIO]], alpha:[[0,1],[1,0]] },
    { name:`${tag}2 Glow`, mat:"glow", fudge:-4, life:0.3, size:0.9, bursts:[[0,1]], ...at, color:[[0,LIL],[1,VIO]], alpha:[[0,0.6],[1,0]] },
  ]),
];

// ---------------------------------------------------------------- meshes
var EXTRA_MESHES = {
  linkV: { verts:[[0,-0.25,-0.5],[0,-0.25,0.5],[0,0.25,0.5],[0,0.25,-0.5]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3, 0,2,1,0,3,2] },
  linkH: { verts:[[-0.25,0,-0.5],[-0.25,0,0.5],[0.25,0,0.5],[0.25,0,-0.5]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3, 0,2,1,0,3,2] },
  ringXY: { verts:[[-0.5,-0.5,0],[0.5,-0.5,0],[0.5,0.5,0],[-0.5,0.5,0]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3] },
  // ink curl around the chain end (XY plane, 1.4 turns spiralling in), U along the stroke
  curl: ribbon(90, s => {
    const phi = s * TAU * 1.4, r = 1 - 0.55 * s, hw = 0.02 + 0.09 * Math.pow(Math.sin(Math.PI * s), 0.6), d = [Math.cos(phi), Math.sin(phi), 0];
    return [mul(d, r - hw), mul(d, r + hw)];
  }),
  coil: ribbon(160, s => {
    const phi = -s * TAU * 2.6, y = -0.95 + 1.9 * s, hw = 0.03 + 0.09 * Math.pow(Math.sin(Math.PI * s), 0.4), d = [Math.cos(phi), 0, Math.sin(phi)];
    return [add(d, [0, y - hw, 0]), add(d, [0, y + hw, 0])];
  }),
};
const MATS = {
  link:{tex:"T_ChainLink", tint:[1.7,1.55,1.45], add:false},
  bar:{tex:"T_ChainBar", tint:[1.7,1.55,1.45], add:false},
  bar2:{tex:"T_ChainBar", tint:[1.3,1.2,1.15], add:false},
  band:{tex:"T_ShadowBand", tint:[2.2,2.0,2.4], add:false},
  dark:{tex:"T_ShadowBand", tint:[1.4,1.3,1.6], add:false},
  ink:{tex:"T_InkRibbon", tint:[2.4,2.2,2.6], add:false},
  inkshard:{tex:"T_InkShard", tint:[2.4,2.2,2.6], add:false},
  linkshard:{tex:"T_LinkShard", tint:[1.9,1.7,1.5], add:false},
  streak:{tex:"T_VioletStreak", tint:[2.2,1.6,2.8], add:true},
  crack:{tex:"T_VioletStreak", tint:[2.6,2.1,3.0], add:true},
  bolt:{tex:"T_Lightning", tint:[2.0,1.4,2.8], add:true},
  haze:{tex:"T_Glow", tint:[0.5,0.22,0.95], add:true},
  glow:{tex:"T_Glow", tint:[1.2,0.65,2.1], add:true},
  star:{tex:"T_Star", tint:[2.1,1.7,2.6], add:true},
  dot:{tex:"T_Dot", tint:[1.6,1.0,2.4], add:true},
  ring:{tex:"T_ThinRing", tint:[1.7,1.15,2.5], add:true},
  shackle:{tex:"T_ShackleRing", tint:[1.5,0.95,2.3], add:true},
  scratch:{tex:"T_Scratch3", tint:[2.0,1.4,2.6], add:true},
  mist:{tex:"T_Mist", tint:[1,1,1], add:false},
};

// ---------------------------------------------------------------- demo (non-normative): two players, distance 11 → 6 (slack) → 11 → 16 (taut) → break
const FROM = [0, 1.0, 0], TOFF = [0, 1.0, 0];
const PA = [[0,[-5.5,0,0],90],[2.4,[-5.5,0,0],90],[3.4,[-3,0,0.3],90],[5.6,[-3,0,0.3],90],[6.4,[-5.5,0,0],90],[7.6,[-5.5,0,0],90],[8.6,[-8,0,-0.3],90],[12,[-8,0,-0.3],90]];
const PB = [[0,[5.5,0,0],-90],[2.4,[5.5,0,0],-90],[3.4,[3,0,-0.3],-90],[5.6,[3,0,-0.3],-90],[6.4,[5.5,0,0],-90],[7.6,[5.5,0,0],-90],[8.6,[8,0,0.3],-90],[12,[8,0,0.3],-90]];
const evs = [
  { t:0, system:"bind_hold", actor:0, to:1, from:FROM, follow:true },
  { t:2.9, system:"bind_slack", actor:0, to:1, from:FROM, follow:true }, { t:6.0, stop:["bind_slack"] },
  { t:8.1, system:"bind_taut", actor:0, to:1, from:FROM, follow:true }, { t:10.6, stop:["bind_taut", "bind_hold"] },
  { t:10.6, system:"bind_break", actor:0, to:1, from:FROM, follow:true },
];
const SCENE = {
  name: "MordianChainBind",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.7, tint:[0.95,0.88,1.0] }, tonemapping:"neutral",
  systems:[
    { id:"bind_hold", role:"link", loop:true, duration:LIFE_HOLD, layers:HOLD },
    { id:"bind_slack", role:"link", loop:true, duration:2, layers:SLACK },
    { id:"bind_taut", role:"link", loop:true, duration:2, layers:TAUT },
    { id:"bind_break", role:"link", loop:false, duration:0.1, layers:BREAK },
  ],
  demo:{
    interval:12.2, bg:[0.2,0.19,0.24], floor:[0.24,0.23,0.27], grid:[0.2,0.19,0.23], dummy:{height:1.8},
    scenarios:{
      cycle:{ label:"11 m → 6 m(느슨) → 11 m → 16 m(팽팽) → 끊어짐", targetOffset:TOFF, actors:[{ path:PA }, { path:PB }], events:evs,
              camera:{ target:[0,1.3,0], dist:15, yaw:-90, pitch:14 } },
      near:{ label:"플레이어 A 근접", targetOffset:TOFF, actors:[{ path:PA }, { path:PB }], events:evs,
              camera:{ target:[-3.6,1.2,0], dist:4.6, yaw:-55, pitch:10 } },
      behind:{ label:"A 뒤 시점", targetOffset:TOFF, actors:[{ path:PA }, { path:PB }], events:evs,
              camera:{ target:[-2.5,1.6,0], dist:7, yaw:175, pitch:14 } },
    },
  },
};
