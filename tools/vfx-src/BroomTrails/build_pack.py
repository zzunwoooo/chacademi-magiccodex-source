# 궤적 팩 4개 빌드: 클라이언트는 팩당 시스템 16개 미만만 받으므로 키를 나눈다 (full + lite = 키당 2 시스템).
import subprocess, os, shutil, json
TK = "/home/claude/work/vfx_toolkit"
PACKS = {
  "BroomTrails":       ["basic","rare_plus","rare_gold","wave1","wave2"],
  "BroomTrailsWood":   ["leaf_oak","leaf_cherry","leaf_birch","leaf_mahogany","leaf_bamboo","galaxy"],
  "BroomTrailsUnique": ["u_dark","u_classic","u_emerald","u_crimson","u_sonic","u_holo","u_twin"],
  "BroomTrailsLegend": ["l_heart","l_darkheart","l_dragon","l_butterfly","l_foxfire","l_angel","l_devil"],
}
LAY = "".join(open(f"../{f}", encoding="utf-8").read() + "\n" for f in ("rare_layers.js","unique_layers.js","legend_layers.js"))
FOOT = open("pack_footer.js", encoding="utf-8").read()
shutil.rmtree("out", ignore_errors=True); shutil.rmtree("fixed", ignore_errors=True)
costs = {}; where = {}
for name, keys in PACKS.items():
    assert len(keys) * 2 < 16
    js = f"// {name} — 빗자루 비행 궤적 팩 (자동 생성: rare/unique/legend_layers.js + pack_footer.js, build_pack.py)\n" + LAY + \
         f"const PACK_NAME = {json.dumps(name)};\nconst PACK_KEYS = {json.dumps(keys)};\n" + FOOT
    open(f"{name}.js", "w", encoding="utf-8").write(js)
    subprocess.run(["python3", f"{TK}/tools/make_bundle.py", f"{name}.js", "../tex", os.path.abspath("out"), "--accent", "#5fa8ff"], capture_output=True)
    subprocess.run(["python3", f"{TK}/tools/client_compat.py", "fix", f"out/{name}", f"fixed/{name}"], capture_output=True, check=True)
    r = subprocess.run(["python3", f"{TK}/tools/client_compat.py", "check", f"fixed/{name}"], capture_output=True, text=True); print(name, r.stdout.strip().splitlines()[0])
    d = json.load(open(f"fixed/{name}/vfx.json"))
    assert len(d["systems"]) < 16
    for s in d["systems"]:
        c = k = 0
        for l in s["layers"]:
            lt = sum(l["start"]["lifetime"]) / 2; c += l["emission"]["rateOverDistance"] * lt; k += l["emission"]["rateOverTime"] * lt
        costs[s["id"]] = (round(c, 1), round(k, 1)); where[s["id"]] = name.lower()
json.dump({"costs": costs, "where": where}, open("costs.json", "w"))
print(len(costs))
