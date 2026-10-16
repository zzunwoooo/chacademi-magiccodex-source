import subprocess, os, shutil, re, json, sys
PER = "/home/claude/work/per"; TK = "/home/claude/work/vfx_toolkit"
SPEED = lambda l: 10.9 + 22.1 * (l - 1) / 9
B = [  # id, name, bbmodel, trail, speed level, turn level
 ("r1", "차큐스 200", "broom_10.bbmodel", "rare_plus", 4, 4),
 ("r2", "차큐스 300", "broom_9.bbmodel", "rare_plus", 5, 4),
 ("r3", "고급스러운 빗자루", "broom_5.bbmodel", "rare_gold", 3, 5),
 ("r4", "차큐스 레이서 1", "broom_3.bbmodel", "wave1", 6, 6),
 ("r5", "차큐스 레이서 2", "broom_7.bbmodel", "wave2", 7, 5),
 ("r6", "차큐스 400 (참나무)", "hset/broom_h_oak.bbmodel", "leaf_oak", 5, 5),
 ("r7", "차큐스 400 (벚나무)", "hset/broom_h_cherry.bbmodel", "leaf_cherry", 5, 5),
 ("r8", "차큐스 400 (자작나무)", "hset/broom_h_birch.bbmodel", "leaf_birch", 5, 5),
 ("r9", "차큐스 400 (마호가니)", "hset/broom_h_mahogany.bbmodel", "leaf_mahogany", 5, 5),
 ("r10", "차큐스 400 (대나무)", "hset/broom_h_bamboo.bbmodel", "leaf_bamboo", 5, 5),
 ("r11", "초보 마녀의 빗자루", "hset/broom_h_starnight.bbmodel", "galaxy", 4, 5),
 ("n4", "차큐스 100 (일반, 비교용)", "broom_1.bbmodel", "basic", 3, 3),
 ("u1", "차큐스 500", "broom_racer.bbmodel", "u_dark", 7, 6),
 ("u2", "차큐스 500 (미드나잇)", "broom_racer_navy.bbmodel", "u_classic", 6, 7),
 ("u3", "차큐스 500 (에메랄드)", "uset/broom_u_emerald.bbmodel", "u_emerald", 8, 5),
 ("u4", "차큐스 500 (크림슨)", "uset/broom_u_crimson.bbmodel", "u_crimson", 7, 7),
 ("u5", "쏜살같은 빗자루", "uset/broom_u_arrow.bbmodel", "u_sonic", 8, 6),
 ("u6", "건메탈 레이서", "uset/broom_u_gunmetal.bbmodel", "u_holo", 6, 8),
 ("u7", "바이올렛 트윈테일", "uset/broom_u_violet.bbmodel", "u_twin", 10, 1),
 ("l1", "마법소녀 빗자루", "broom_heart.bbmodel", "l_heart", 9, 8),
 ("l2", "타락한 마법소녀 빗자루", "broom_dark_heart.bbmodel", "l_darkheart", 9, 8),
 ("l3", "드래고니아", "broom_dragon.bbmodel", "l_dragon", 9, 8),
 ("l4", "블러디 플라이", "broom_vampire2.bbmodel", "l_butterfly", 10, 7),
 ("l5", "아홉꼬리 빗자루", "broom_gumiho.bbmodel", "l_foxfire", 7, 10),
 ("l6", "차큐스 1000 (엔젤)", "broom_angel.bbmodel", "l_angel", 8, 8),
 ("l7", "차큐스 1000 (데빌)", "broom_demon.bbmodel", "l_devil", 9, 9),
]
only = sys.argv[1:]
LAY = open("rare_layers.js", encoding="utf-8").read() + "\n" + open("unique_layers.js", encoding="utf-8").read() + "\n" + open("legend_layers.js", encoding="utf-8").read()
os.makedirs("specs", exist_ok=True); os.makedirs("final", exist_ok=True)
for bid, name, bb, trail, sl, tl in B:
    if only and bid not in only: continue
    N = {"r":"RareTrail_","n":"RareTrail_","u":"UniqueTrail_","l":"LegendTrail_"}[bid[0]] + bid; td = os.path.abspath(f"texd/{N}"); shutil.rmtree(td, ignore_errors=True); shutil.copytree("tex", td); os.makedirs(td + "/models")
    out = subprocess.run(["python3", PER + "/gen_broom_glb.py", PER + "/" + bb, td + "/models/BroomRider.glb"], capture_output=True, text=True, cwd=PER).stdout
    tip = json.loads(out.strip().splitlines()[-1])["tip"]
    v = SPEED(sl); vb = v * 1.45; yr = (7 + tl - 1) * 20 * 0.6 * 0.5
    foot = f'''
const TIP = {json.dumps(tip)};
const LAYERS = TRAILS["{trail}"].map(l => Object.assign({{}}, l, {{ pos: [l.pos[0] + TIP[0], l.pos[1] + TIP[1], l.pos[2] + TIP[2]] }}));
const MATS = RARE_MATS;
function fly(segs, y0) {{
  const K = []; let t = 0, x = 0, z = 0, yaw = 0, dt = 0.05; K.push([0, [0, y0, 0], 0]);
  for (const [dur, v0, v1, yr] of segs) {{
    const n = Math.max(1, Math.round(dur / dt));
    for (let i = 1; i <= n; i++) {{ const u = (i - 0.5) / n, v = v0 + (v1 - v0) * u; yaw += yr * dt; x += Math.sin(yaw * D2R) * v * dt; z += Math.cos(yaw * D2R) * v * dt; t += dt;
      K.push([+t.toFixed(3), [+x.toFixed(3), y0, +z.toFixed(3)], +yaw.toFixed(2)]); }}
  }}
  return K;
}}
function scen(label, segs) {{
  const T = segs.reduce((a, s) => a + s[0], 0);
  return {{ label, path: fly(segs, 3), stops:{{ trail: +(T - 0.6).toFixed(2) }}, interval: T + 1.2, camera:{{ follow:true, targetY:1.0, dist:5.6, yaw:-125, pitch:14 }} }};
}}
const SCENE = {{
  name: "{N}",
  bloom:{{ threshold:0.95, intensity:1.2, scatter:0.65, tint:[1.0,0.97,1.0] }}, tonemapping:"neutral",
  systems:[ {{ id:"trail", role:"attached", loop:true, duration:1.0, layers:LAYERS }} ],
  demo:{{
    interval:7, bg:[0.52,0.62,0.74], floor:[0.33,0.42,0.30], grid:[0.38,0.48,0.34], dummy:{{height:1.8}},
    character:{{ file:"models/BroomRider.glb", clip:"fly" }},
    scenarios:{{
      top:  scen("최고속 {v:.1f} m/s → 부스트 {vb:.1f} → 급정거", [[2.5,{v:.2f},{v:.2f},0],[0.4,{v:.2f},{vb:.2f},0],[1.2,{vb:.2f},{vb:.2f},0],[1.2,{vb:.2f},0,0],[1.2,0,0,0]]),
      turn: scen("선회 {v:.1f} m/s", [[1.0,{v:.2f},{v:.2f},0],[2.0,{v:.2f},{v:.2f},{yr:.1f}],[1.0,{v:.2f},{v:.2f},0],[1.0,{v:.2f},0,0],[1.0,0,0,0]]),
      slow: scen("저속 5 m/s", [[3.5,5,5,0],[0.6,5,0,0],[1.2,0,0,0]]),
    }},
  }},
}};
'''
    open(f"{N}.js", "w", encoding="utf-8").write(LAY + foot)
    r = subprocess.run(["bash", "build.sh", N, td, "#5fa8ff"], capture_output=True, text=True)
    print(bid, name, tip, "OK" if r.returncode == 0 else "FAIL\n" + r.stdout[-1500:] + r.stderr[-1500:])
