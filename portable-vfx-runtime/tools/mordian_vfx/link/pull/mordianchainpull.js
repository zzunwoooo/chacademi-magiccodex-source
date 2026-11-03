// MordianChainPull (모르디안의 원령 — 사슬 끌어당기기)
// Boss tether motion: 0.5 wind-up, 0.75 throw  → the game starts this cast at tether t = 0.75.
// A black chain (faded-gold rimmed ink links + a wound shadow band + violet afterglow) shoots from the boss hand to the lowest-HP player (0.20 s),
// wraps the player (small violet flash + 3-scratch mark + coiling shadow band), holds while the game pulls the player in (hold ≈ 0.85 s, runtime holdDuration),
// then snaps and scatters into ink / gold shards.
// Phased (SCHEMA §5.1):  throw (begin 0–0.20) → hold (sustain, holdDuration) → break (end).
// Link frame (§5): source = boss hand point, target = player chest point (feet + 1.1 m), z runs hand → player, every frame.
// Wrap systems are role "attached" to the PULLED PLAYER (anchor = feet, facing = player → boss, horizontal).
const TAU = Math.PI * 2;
const WH = [0.97,0.92,1], LIL = [0.84,0.6,1], VIO = [0.54,0.24,1], DVIO = [0.3,0.12,0.6], GOLD = [1,0.86,0.55];
const SUS = 0.20, LIFE_HOLD = 60;
const tailK = tail => 1 - tail / LIFE_HOLD;
const lin = (a, b) => [[0, a, 0, b - a], [1, b, b - a, 0]];
const cst = s => [[0, s, 0, 0], [1, s, 0, 0]];
const range = n => Array.from({ length: n }, (_, k) => k);
const every = (t0, t1, n = 1, hz = 60) => [[t0, n, Math.max(1, Math.round((t1 - t0) * hz)), 1 / hz]];
const CURVE = { x:0, y:-0.12 };                                  // the chain: one centre line, slight sag
const pth = (progress, bend = CURVE, wave = { amp:0, cycles:1, phase:0, vr:1 }) => ({ progress, bend, wave });

// head progress (throw): Hermite ease-out 0 → 1 over SUS; link k appears when the head passes it
const HEAD = [[0,0,0,1.6],[1,1,0.45,0]];
const hermU = (u, p0, p1, m0, m1) => (2*u*u*u - 3*u*u + 1)*p0 + (u*u*u - 2*u*u + u)*m0 + (-2*u*u*u + 3*u*u)*p1 + (u*u*u - u*u)*m1;
const headS = u => hermU(u, 0, 1, 1.6, 0.45);
const tAt = s => { let a = 0, b = 1; for (let i = 0; i < 30; i++) { const m = (a + b) / 2; if (headS(m) < s) a = m; else b = m; } return a * SUS; };

// ---------------------------------------------------------------- chain links (alternating face-on / edge-on mesh quads along local z)
const N = 20, LINK = 0.42;
const sK = k => (k + 0.6) / (N + 0.2);
const linkMesh = k => (k % 2 ? "linkH" : "linkV");
const linkMat = k => (k % 2 ? "bar" : "link");
const THROW_LINKS = range(N).map(k => {
  const t0 = Math.min(tAt(sK(k)), SUS - 0.012), life = SUS - t0, kp = Math.min(0.9, 0.05 / life);
  return { name:`A${String(k).padStart(2,"0")} LinkIn`, mat:"link", mode:"mesh", mesh:linkMesh(k), fudge:2, life, size:LINK, rot3:[0,0,0],
    bursts:[[t0,1]], path:pth(cst(sK(k))), alpha:[[0,0],[kp * 0.4,1],[1,1]], sizeLife:[[0,1.5],[kp,1],[1,1]] };
});
const HOLD_LINKS = range(N).map(k => ({ name:`H${String(k).padStart(2,"0")} Link`, mat:"link", mode:"mesh", mesh:linkMesh(k), fudge:2,
  life:LIFE_HOLD, size:LINK, rot3:[0,0,0], bursts:[[0,1]], path:pth(cst(sK(k))), alpha:[[0,1],[tailK(0.1),1],[1,0]], onStop:0.1 }));

// shadow band wound along the chain: nb overlapping stretched segments of FIXED length SEGLEN (ls; vs tiny so a fast-changing linkLength
// — the pull itself — does not stretch them past the ends). Each head drifts 0.3 segment; the quad trails its head (dir −1 trails toward the player).
const BL = 0.16, SEGLEN = 0.66;
const bandSeg = (tag, k, nb, bursts, dir, opt = {}) => {
  const ds = 1 / nb, c = (k + 0.5) * ds, a = c - dir * 0.15 * ds, b = c + dir * 0.15 * ds;
  return [
    { name:`${tag}${String(k).padStart(2,"0")} Band`, mat:"band", mode:"stretch", ls:SEGLEN / 0.22, vs:0.02, fudge:4, life:BL, size:[0.2,0.24], ...bursts, path:pth(lin(a, b)),
      alpha:[[0,0],[0.25,0.85],[0.75,0.85],[1,0]], ...(opt.stop != null ? { onStop:opt.stop } : {}) },
    { name:`${tag}${String(k).padStart(2,"0")} Haze`, mat:"haze", mode:"stretch", ls:SEGLEN / 0.46, vs:0.02, fudge:6, life:BL, size:[0.42,0.5], ...bursts, path:pth(lin(a, b)),
      color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.3,opt.haze ?? 0.16],[0.7,opt.haze ?? 0.16],[1,0]], ...(opt.stop != null ? { onStop:opt.stop } : {}) },
  ];
};

// ---------------------------------------------------------------- BEGIN: throw (0 → 0.20)
const THROW = [
  ...THROW_LINKS,
  ...range(14).flatMap(k => bandSeg("B", k, 14, { bursts:every(tAt((k + 0.5) / 14) * 0.9, SUS, 1, 24) }, k ? 1 : -1, { haze:0.22 })),
  { name:"C00 HeadStar", mat:"star", fudge:-6, life:SUS, size:0.6, rot:[0,0.8], rotZ:[-6,6], bursts:[[0,1]], path:pth(HEAD),
    color:[[0,WH],[1,LIL]], alpha:[[0,1],[0.9,1],[1,0.5]] },
  { name:"C01 HeadShard", mat:"linkshard", mode:"stretch", ls:3.0, vs:0.01, fudge:1, life:SUS, size:0.16, bursts:[[0,1]], path:pth(HEAD), alpha:[[0,1],[1,1]] },
  { name:"C02 HeadTrail", mat:"streak", mode:"stretch", ls:1.2, vs:0.07, fudge:-2, life:SUS, size:0.16, bursts:[[0,1],[0.015,1],[0.03,1]], path:pth(HEAD),
    color:[[0,WH],[1,VIO]], alpha:[[0,1],[0.85,0.9],[1,0]] },
  { name:"C03 HeadGlow", mat:"glow", fudge:-3, life:SUS, size:0.7, bursts:[[0,1]], path:pth(HEAD), color:[[0,LIL],[1,VIO]], alpha:[[0,0.5],[1,0.35]] },
  { name:"C04 InkWhip", mat:"ink", mode:"stretch", ls:1.0, vs:0.025, fudge:3, life:[0.16,0.22], size:[0.07,0.12], bursts:every(0.02, SUS, 1),
    path:{ progress:[[0,0],[1,0.85,0.9,0.2]], bend:{x:[-0.2,0.2], y:[-0.1,0.3]}, wave:{amp:[0.15,0.35], cycles:[1.5,2.5], phase:[0,TAU], vr:1} }, alpha:[[0,0],[0.15,1],[1,0]] },
  // at the hand (non-path layers sit at the link source)
  { name:"C10 HandFlash", mat:"glow", fudge:-8, life:0.24, size:0.9, bursts:[[0,1]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.7],[1,0]], sizeLife:[[0,0.4],[0.25,1],[1,0.8]] },
  { name:"C11 HandStar", mat:"star", fudge:-9, life:0.16, size:0.8, rot:[0,0.8], bursts:[[0,1]], color:[[0,WH],[1,VIO]], alpha:[[0,1],[1,0]] },
  { name:"C12 HandRing", mat:"ring", mode:"mesh", mesh:"ringXY", fudge:-1, life:0.26, size:0.9, rot3:[0,0,[0,TAU]], bursts:[[0,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0.85],[1,0]], sizeLife:[[0,0.3,0,2.5],[1,1.2]] },
  { name:"C13 HandInk", mat:"inkshard", mode:"stretch", ls:1.8, vs:0.03, world:true, fudge:5, life:[0.3,0.5], size:[0.06,0.11], speed:[2.5,5],
    shape:{type:"sphere", r:0.12}, bursts:[[0,12]], drag:3, gravity:0.5, alpha:[[0,1],[0.7,0.9],[1,0]] },
  { name:"C14 HandMist", mat:"mist", world:true, fudge:9, life:[0.5,0.8], size:[0.5,0.8], speed:[0.3,0.8], shape:{type:"sphere", r:0.15}, bursts:[[0,4]],
    rot:[0,TAU], flip:{x:4,y:4}, drag:2, alpha:[[0,0.7],[1,0]] },
  { name:"C15 HandSparks", mat:"dot", mode:"stretch", ls:1, vs:0.03, world:true, fudge:-4, life:[0.25,0.45], size:[0.03,0.05], speed:[3,6],
    shape:{type:"sphere", r:0.08}, bursts:[[0,14]], drag:3, color:[[0,WH],[0.4,LIL],[1,VIO]], alpha:[[0,1],[1,0]] },
];

// ---------------------------------------------------------------- SUSTAIN: hold while pulled (loop, holdDuration)
const HOLD = [
  ...HOLD_LINKS,
  ...range(16).flatMap(k => bandSeg("D", k, 16, { rate:13, bursts:[[0,2]] }, k >= 14 ? 1 : -1, { stop:0.06 })),          // band drifts toward the hand = reeling in
  { name:"E00 PullStreaks", mat:"streak", mode:"stretch", ls:0.6, vs:0.025, fudge:-2, life:[0.2,0.28], size:[0.05,0.09], rate:30,
    path:{ progress:lin(0.9, 0.05), bend:{x:[-0.12,0.12], y:[-0.25,0.05]}, wave:{amp:[0.04,0.12], cycles:[1,2], phase:[0,TAU], vr:1} },
    color:[[0,WH],[0.5,LIL],[1,VIO]], alpha:[[0,0],[0.2,0.9],[1,0]], onStop:0.05 },
  { name:"E01 InkCoils", mat:"ink", mode:"stretch", ls:0.8, vs:0.025, fudge:3, life:[0.35,0.45], size:[0.06,0.1], rate:24,
    path:{ progress:lin(0.93, 0), bend:CURVE, wave:{amp:[0.13,0.2], cycles:[5,7], phase:[0,TAU], vr:1} }, alpha:[[0,0],[0.15,1],[0.85,1],[1,0]], onStop:0.05 },
  { name:"E02 Motes", mat:"dot", fudge:-3, life:[0.5,0.8], size:[0.04,0.08], rate:20,
    path:{ progress:lin(0.95, 0.6), bend:{x:[-0.4,0.4], y:[0.1,0.6]}, wave:{amp:[0,0.1], cycles:1, phase:[0,TAU], vr:1} },
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.3,1],[1,0]] },
  { name:"E03 Glints", mat:"star", fudge:-5, life:[0.12,0.18], size:[0.16,0.26], rot:[0,0.8], rate:9,
    path:{ progress:cst(0.5), bend:{x:[-0.03,0.03], y:[-0.03,0.03]}, wave:{amp:0, cycles:1, phase:0, vr:1} }, color:[[0,WH],[1,GOLD]], alpha:[[0,0],[0.3,1],[1,0]] },
  { name:"E04 Glints2", mat:"star", fudge:-5, life:[0.12,0.18], size:[0.14,0.22], rot:[0,0.8], rate:9,
    path:{ progress:[[0,0.25],[1,0.25]], bend:CURVE, wave:{amp:0, cycles:1, phase:0, vr:1} }, color:[[0,WH],[1,GOLD]], alpha:[[0,0],[0.3,1],[1,0]] },
  // hand: dark grip + strain
  { name:"E10 HandGlow", mat:"glow", fudge:-8, life:0.2, size:[0.55,0.65], rate:20, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.5,0.4],[1,0]], onStop:0.05 },
  { name:"E11 HandShackle", mat:"shackle", mode:"mesh", mesh:"ringXY", fudge:-1, life:LIFE_HOLD, size:0.55, rot3:[0,0,0], rotLife:[0,0,3], bursts:[[0,1]],
    alpha:[[0,0.9],[tailK(0.1),0.9],[1,0]], onStop:0.1 },
  { name:"E12 HandMist", mat:"mist", world:true, fudge:9, life:[0.5,0.7], size:[0.4,0.6], speed:[0.2,0.5], shape:{type:"sphere", r:0.12}, rate:8,
    rot:[0,TAU], flip:{x:4,y:4}, vel:{y:0.4, space:"world"}, alpha:[[0,0.55],[1,0]] },
];

// ---------------------------------------------------------------- END: break (chain snaps into ink + gold shards)
const BREAKB = { x:[-0.5,0.5], y:[-0.3,0.6] };
const BREAK = [
  ...range(11).flatMap(j => {
    const s = (j + 0.5) / 11, b = { progress:[[0,s,0,0.25],[1,s + 0.06 * (j % 2 ? 1 : -1),0,0]], bend:BREAKB, wave:{amp:[0,0.25], cycles:[1,2], phase:[0,TAU], vr:1} };
    return [
      { name:`X${String(j).padStart(2,"0")} LinkShards`, mat:"linkshard", mode:"stretch", ls:1.6, vs:0.12, fudge:1, life:[0.35,0.55], size:[0.08,0.13],
        bursts:[[0.012 * j, 2]], path:b, alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1.1],[1,0.5]] },
      { name:`X${String(j).padStart(2,"0")} InkShards`, mat:"inkshard", mode:"stretch", ls:1.8, vs:0.12, fudge:2, life:[0.4,0.65], size:[0.09,0.15],
        bursts:[[0.012 * j, 2]], path:{ ...b, bend:{ x:[-0.8,0.8], y:[-0.5,0.8] } }, alpha:[[0,1],[0.7,0.9],[1,0]], sizeLife:[[0,1.2],[1,0.4]] },
    ];
  }),
  ...[0.15, 0.4, 0.65, 0.9].map((s, j) => ({ name:`X3${j} InkPuff`, mat:"mist", fudge:8, life:[0.5,0.8], size:[0.5,0.8], rot:[0,TAU], flip:{x:4,y:4}, bursts:[[0.02 * j, 2]],
    path:{ progress:[[0,s],[1,s + 0.02]], bend:{x:[-0.3,0.3], y:[-0.1,0.3]}, wave:{amp:0, cycles:1, phase:0, vr:1} }, alpha:[[0,0.75],[1,0]], sizeLife:[[0,0.6],[1,1.2]] })),
  { name:"X40 SnapSparks", mat:"star", fudge:-5, life:[0.12,0.2], size:[0.2,0.32], rot:[0,0.8], bursts:[[0,5],[0.05,4]],
    path:{ progress:[[0,0.1],[1,0.9,0.8,0.8]], bend:{x:[-0.3,0.3], y:[-0.15,0.3]}, wave:{amp:[0,0.2], cycles:1, phase:[0,TAU], vr:1} },
    color:[[0,WH],[1,LIL]], alpha:[[0,1],[1,0]] },
  { name:"X41 SnapMotes", mat:"dot", fudge:-3, life:[0.6,1.0], size:[0.04,0.08], bursts:[[0.03,24]],
    path:{ progress:[[0,0.05],[1,0.95,0.9,0.9]], bend:{x:[-0.6,0.6], y:[0.2,1.0]}, wave:{amp:[0,0.2], cycles:1, phase:[0,TAU], vr:1} },
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.15,1],[1,0]] },
  { name:"X42 BandShreds", mat:"ink", mode:"stretch", ls:1.0, vs:0.06, fudge:3, life:[0.3,0.45], size:[0.06,0.1], bursts:[[0,14]],
    path:{ progress:[[0,0.1],[1,0.9,0.8,0.8]], bend:{x:[-0.5,0.5], y:[-0.3,0.4]}, wave:{amp:[0.1,0.4], cycles:[1,3], phase:[0,TAU], vr:1} }, alpha:[[0,1],[1,0]] },
  { name:"X50 HandPuff", mat:"glow", fudge:-8, life:0.25, size:0.6, bursts:[[0,1]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.55],[1,0]] },
  { name:"X51 HandInk", mat:"inkshard", mode:"stretch", ls:1.6, vs:0.03, world:true, fudge:5, life:[0.3,0.5], size:[0.05,0.09], speed:[1.5,3.5],
    shape:{type:"sphere", r:0.1}, bursts:[[0,8]], drag:3, gravity:0.6, alpha:[[0,1],[1,0]] },
];

// ---------------------------------------------------------------- wrap on the pulled player (attached: feet = origin, +Z = toward the boss)
const CY = 1.1;
const WRAP = [
  { name:"W00 Flash", mat:"glow", fudge:-8, pos:[0,CY,0], life:0.22, size:1.0, bursts:[[0,1]], color:[[0,LIL],[1,VIO]], alpha:[[0,0.75],[1,0]], sizeLife:[[0,0.4],[0.25,1],[1,0.8]] },
  { name:"W01 FlashStar", mat:"star", fudge:-9, pos:[0,CY,0.15], life:0.18, size:1.1, rot:[0,0.8], bursts:[[0,1]], color:[[0,WH],[1,VIO]], alpha:[[0,1],[1,0]],
    sizeLife:[[0,0.5],[0.3,1],[1,0.6]] },
  { name:"W02 Scratch", mat:"scratch", fudge:-7, pos:[0,CY,0.25], life:0.4, size:0.75, rot:[-0.2,0.2], bursts:[[0.02,1]], color:[[0,WH],[1,VIO]],
    alpha:[[0,0],[0.1,1],[0.5,0.8],[1,0]], sizeLife:[[0,0.7],[0.2,1],[1,1.08]] },
  { name:"W03 SnapRing", mat:"ring", mode:"mesh", mesh:"quad", fudge:-1, pos:[0,CY,0], life:0.3, size:1.4, rot3:[0,[0,TAU],0], bursts:[[0,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0.85],[1,0]], sizeLife:[[0,0.3,0,2.5],[1,1.1]] },
  { name:"W04 Sparks", mat:"dot", mode:"stretch", ls:1, vs:0.03, world:true, fudge:-4, life:[0.25,0.45], size:[0.03,0.05], speed:[2.5,5],
    shape:{type:"sphere", r:0.3, off:[0,CY,0]}, bursts:[[0,16]], drag:3, color:[[0,WH],[0.4,LIL],[1,VIO]], alpha:[[0,1],[1,0]] },
  { name:"W05 InkSplash", mat:"inkshard", mode:"stretch", ls:1.8, vs:0.03, world:true, fudge:5, life:[0.3,0.5], size:[0.06,0.1], speed:[2,4],
    shape:{type:"circle", r:0.35, th:0, off:[0,CY,0]}, bursts:[[0,10]], drag:3, gravity:0.5, alpha:[[0,1],[1,0]] },
  // coil: dark band + gold-violet rim line, spinning in, tightening
  { name:"W10 CoilIn", mat:"coil", mode:"mesh", mesh:"coil", fudge:3, pos:[0,CY,0], life:LIFE_HOLD, size:0.48, rot3:[0,0,0], rotLife:[0,-2.5,0], bursts:[[0,1]],
    alpha:[[0,0],[0.0015,1],[tailK(0.12),1],[1,0]], sizeLife:[[0,1.5,0,0],[0.004,0.92],[tailK(0.12),0.92],[1,1.25]], onStop:0.12 },
  { name:"W11 CoilLine", mat:"coilline", mode:"mesh", mesh:"coil", fudge:-2, pos:[0,CY,0], life:LIFE_HOLD, size:0.5, rot3:[0,0,0], rotLife:[0,-2.5,0], bursts:[[0,1]],
    color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.0015,1],[tailK(0.12),0.9],[1,0]], sizeLife:[[0,1.5,0,0],[0.004,0.92],[tailK(0.12),0.92],[1,1.25]], onStop:0.12 },
  { name:"W12 Haze", mat:"haze", fudge:6, pos:[0,CY,0], life:0.3, size:[1.0,1.15], rate:12, color:[[0,LIL],[1,DVIO]], alpha:[[0,0],[0.5,0.35],[1,0]], onStop:0.05 },
  { name:"W13 Motes", mat:"dot", world:true, fudge:-3, life:[0.6,0.9], size:[0.03,0.06], shape:{type:"circle", r:0.5, th:0.2, off:[0,CY - 0.3,0]}, rate:16,
    vel:{y:[0.6,1.1], space:"world"}, noise:{s:0.3, f:1.2, sp:0.6}, color:[[0,LIL],[1,VIO]], alpha:[[0,0],[0.2,1],[1,0]], sizeLife:[[0,1],[1,0.3]] },
  { name:"W14 Mist", mat:"mist", world:true, fudge:9, life:[0.6,0.9], size:[0.6,0.9], shape:{type:"circle", r:0.4, th:0.5, off:[0,0.4,0]}, rate:6, rot:[0,TAU],
    flip:{x:4,y:4}, vel:{y:0.3, space:"world"}, alpha:[[0,0.5],[1,0]] },
];
const WRAPBREAK = [
  { name:"Y00 Burst", mat:"ring", mode:"mesh", mesh:"quad", fudge:-1, pos:[0,CY,0], life:0.3, size:1.2, rot3:[0,0,0], bursts:[[0,1]],
    color:[[0,WH],[1,VIO]], alpha:[[0,0.8],[1,0]], sizeLife:[[0,0.8,0,1.5],[1,1.8]] },
  { name:"Y01 CoilShreds", mat:"inkshard", mode:"stretch", ls:2.2, vs:0.03, world:true, fudge:5, life:[0.35,0.55], size:[0.07,0.12], speed:[2.5,4.5],
    shape:{type:"circle", r:0.45, th:0, off:[0,CY,0]}, bursts:[[0,16]], drag:3.5, gravity:0.4, alpha:[[0,1],[0.6,0.85],[1,0]], sizeLife:[[0,1],[1,0.4]] },
  { name:"Y02 GoldShreds", mat:"linkshard", mode:"stretch", ls:1.8, vs:0.03, world:true, fudge:4, life:[0.35,0.55], size:[0.05,0.09], speed:[2,4],
    shape:{type:"circle", r:0.45, th:0, off:[0,CY,0]}, bursts:[[0,8]], drag:3, gravity:0.8, alpha:[[0,1],[1,0]] },
  { name:"Y03 Motes", mat:"dot", world:true, fudge:-3, life:[0.7,1.1], size:[0.04,0.07], speed:[0.5,1.4], shape:{type:"circle", r:0.45, th:0.2, off:[0,CY,0]},
    bursts:[[0,18]], vel:{y:[0.5,1.0], space:"world"}, drag:2, noise:{s:0.4, f:1.2, sp:0.6}, color:[[0,LIL],[1,VIO]], alpha:[[0,1],[0.6,0.8],[1,0]], sizeLife:[[0,1],[1,0.3]] },
  { name:"Y04 Mist", mat:"mist", world:true, fudge:9, life:[0.6,0.9], size:[0.7,1.0], speed:[0.3,0.8], shape:{type:"circle", r:0.4, th:0, off:[0,CY,0]}, bursts:[[0,5]],
    rot:[0,TAU], flip:{x:4,y:4}, drag:2, alpha:[[0,0.65],[1,0]] },
];

// ---------------------------------------------------------------- meshes
var EXTRA_MESHES = {
  // chain link quads, length 1 along local z (U), width 0.5 across (V): V = face-on (YZ plane), H = edge-on (XZ plane)
  linkV: { verts:[[0,-0.25,-0.5],[0,-0.25,0.5],[0,0.25,0.5],[0,0.25,-0.5]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3, 0,2,1,0,3,2] },
  linkH: { verts:[[-0.25,0,-0.5],[-0.25,0,0.5],[0.25,0,0.5],[0.25,0,-0.5]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3, 0,2,1,0,3,2] },
  // ring quad facing along the link (XY plane): the chain passes through it
  ringXY: { verts:[[-0.5,-0.5,0],[0.5,-0.5,0],[0.5,0.5,0],[-0.5,0.5,0]], uvs:[[0,0],[1,0],[1,1],[0,1]], tris:[0,1,2,0,2,3] },
  // 2.2-turn coil around local Y, radius 1, height ±0.6, U along the band
  coil: ribbon(160, s => {
    const phi = -s * TAU * 2.6, y = -0.95 + 1.9 * s, hw = 0.03 + 0.09 * Math.pow(Math.sin(Math.PI * s), 0.4), d = [Math.cos(phi), 0, Math.sin(phi)];
    return [add(d, [0, y - hw, 0]), add(d, [0, y + hw, 0])];
  }),
};
const MATS = {
  link:{tex:"T_ChainLink", tint:[1.7,1.55,1.45], add:false},
  bar:{tex:"T_ChainBar", tint:[1.7,1.55,1.45], add:false},
  band:{tex:"T_ShadowBand", tint:[2.2,2.0,2.4], add:false},
  coil:{tex:"T_ShadowBand", tint:[2.2,2.0,2.4], add:false},
  coilline:{tex:"T_VioletStreak", tint:[2.0,1.4,2.6], add:true},
  ink:{tex:"T_InkRibbon", tint:[2.4,2.2,2.6], add:false},
  inkshard:{tex:"T_InkShard", tint:[2.4,2.2,2.6], add:false},
  linkshard:{tex:"T_LinkShard", tint:[1.9,1.7,1.5], add:false},
  streak:{tex:"T_VioletStreak", tint:[2.2,1.6,2.8], add:true},
  haze:{tex:"T_Glow", tint:[0.5,0.22,0.95], add:true},
  glow:{tex:"T_Glow", tint:[1.2,0.65,2.1], add:true},
  star:{tex:"T_Star", tint:[2.1,1.7,2.6], add:true},
  dot:{tex:"T_Dot", tint:[1.6,1.0,2.4], add:true},
  ring:{tex:"T_ThinRing", tint:[1.7,1.15,2.5], add:true},
  shackle:{tex:"T_ShackleRing", tint:[1.5,0.95,2.3], add:true},
  scratch:{tex:"T_Scratch3", tint:[2.0,1.4,2.6], add:true},
  mist:{tex:"T_Mist", tint:[1,1,1], add:false},
};

// ---------------------------------------------------------------- demo (non-normative): boss stand-in boxes at the origin facing +Z, hand at HAND
const HAND = [1.1, 3.0, 1.7], TOFF = [0, 1.1, 0];
const BOSS = [ { c:[0,4.6,0], e:[1.0,2.4,0.8], shade:[0.13,0.09,0.2] }, { c:[0.9,3.4,1.0], e:[0.22,0.22,0.7], rx:-30, shade:[0.15,0.1,0.22] },
  { c:[0,6.6,0.75], e:[0.35,0.4,0.05], shade:[0.85,0.85,0.88] } ];
const SCENE = {
  name: "MordianChainPull",
  bloom:{ threshold:0.95, intensity:1.3, scatter:0.7, tint:[0.95,0.88,1.0] }, tonemapping:"neutral",
  sequence:{ sustainStart:SUS, hold:{ default:0.85, min:0.1, max:3 } },
  systems:[
    { id:"throw", role:"link", phase:"begin", loop:false, duration:SUS, layers:THROW },
    { id:"hold", role:"link", phase:"sustain", loop:true, duration:LIFE_HOLD, layers:HOLD },
    { id:"wrap", role:"attached", phase:"sustain", loop:true, duration:LIFE_HOLD, layers:WRAP },
    { id:"break", role:"link", phase:"end", loop:false, duration:0.15, layers:BREAK },
    { id:"wrapBreak", role:"attached", phase:"end", loop:false, duration:0.1, layers:WRAPBREAK },
  ],
  demo:{
    endTail:1.1, bg:[0.2,0.19,0.24], floor:[0.24,0.23,0.27], grid:[0.2,0.19,0.23], dummy:{height:1.8}, props:BOSS,
    scenarios:{
      side:{ label:"측면 (9 m → 끌려옴)", source:HAND, targetOffset:TOFF,
             path:[[0,[0.8,0,10],185],[SUS,[0.8,0,10],185],[SUS + 0.85,[1.0,0,4.5],185],[3,[1.0,0,4.5],185]], camera:{ target:[0.6,2.2,5.6], dist:11.5, yaw:12, pitch:10 } },
      behind:{ label:"플레이어 뒤 시점", source:HAND, targetOffset:TOFF,
             path:[[0,[0.8,0,10],185],[SUS,[0.8,0,10],185],[SUS + 0.85,[1.0,0,4.5],185],[3,[1.0,0,4.5],185]], camera:{ target:[0.9,2.0,7.0], dist:7.5, yaw:80, pitch:12 } },
      close:{ label:"감긴 지점 근접", source:HAND, targetOffset:TOFF,
             path:[[0,[0.8,0,8],185],[SUS,[0.8,0,8],185],[SUS + 0.85,[1.0,0,5.0],185],[3,[1.0,0,5.0],185]], camera:{ target:[1.0,1.5,6.0], dist:4.6, yaw:35, pitch:10 } },
    },
  },
};
