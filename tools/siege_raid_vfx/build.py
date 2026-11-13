"""팩 빌드: lib.js + spec → make_bundle → client_compat fix/check → validate → preview → zip.
usage: python build.py <spec name> <texdir> [accent]"""
import os, sys, subprocess, shutil, zipfile, json
R = '/home/claude/vfx'; TK = R + '/vfx_toolkit'
name, tex = sys.argv[1], sys.argv[2]; accent = sys.argv[3] if len(sys.argv) > 3 else '#ffb050'
src = open(f'{R}/specs/lib.js').read() + '\n' + (open(f'{R}/specs/siege_lib.js').read() + '\n' if name.startswith('Siege') else '') + open(f'{R}/specs/{name}.js').read() + '\n' + open(f'{R}/specs/footer.js').read()
os.makedirs(f'{R}/build', exist_ok=True); sp = f'{R}/build/{name}.full.js'; open(sp, 'w').write(src)
out = f'{R}/build/out_{name}'; fin = f'{R}/build/fin_{name}'
shutil.rmtree(out, ignore_errors=True); shutil.rmtree(fin, ignore_errors=True)
notes = f'{R}/specs/{name}.NOTES.md'
cmd = [sys.executable, f'{TK}/tools/make_bundle.py', sp, tex, out, '--accent', accent] + (['--notes', notes] if os.path.exists(notes) else [])
r = subprocess.run(cmd, capture_output=True, text=True)
print(r.stdout[-1500:], r.stderr[-3000:])
if not any(os.path.isdir(f'{out}/{d}') and d != '_build' for d in os.listdir(out)):
    v = subprocess.run([sys.executable, f'{TK}/tools/validate.py', f'{out}/_build/' + os.listdir(f'{out}/_build')[0]], capture_output=True, text=True); print(v.stdout[-3000:]); sys.exit(1)
pack = [d for d in os.listdir(out) if os.path.isdir(f'{out}/{d}') and d != '_build'][0]
os.makedirs(fin)
r = subprocess.run([sys.executable, f'{TK}/tools/client_compat.py', 'fix', f'{out}/{pack}', f'{fin}/{pack}'], capture_output=True, text=True); print('FIX:', r.stdout[-800:], r.stderr[-800:])
r = subprocess.run([sys.executable, f'{TK}/tools/client_compat.py', 'check', f'{fin}/{pack}'], capture_output=True, text=True); print('CHECK:', r.stdout[-1500:], r.stderr[-500:])
r = subprocess.run([sys.executable, f'{TK}/tools/validate.py', f'{fin}/{pack}'], capture_output=True, text=True); print('VALIDATE:', r.stdout[-600:], r.stderr[-600:])
r = subprocess.run([sys.executable, f'{TK}/engine/build_preview.py', f'{fin}/{pack}', f'{fin}/{pack}/preview.html', '--accent', accent], capture_output=True, text=True); print('PREVIEW:', r.stdout[-300:], r.stderr[-600:])
zp = f'{fin}/{pack}.zip'
with zipfile.ZipFile(zp, 'w', zipfile.ZIP_DEFLATED) as z:
    for dp, _, fs in os.walk(f'{fin}/{pack}'):
        for f in sorted(fs): z.write(os.path.join(dp, f), os.path.relpath(os.path.join(dp, f), fin))
from PIL import Image
tot = sum(Image.open(f'{fin}/{pack}/textures/{f}').size[0] * Image.open(f'{fin}/{pack}/textures/{f}').size[1] * 4 for f in os.listdir(f'{fin}/{pack}/textures'))
d = json.load(open(f'{fin}/{pack}/vfx.json'))
print(f'ZIP {zp} {os.path.getsize(zp)//1024} KB, unique RGBA {tot/2**20:.2f} MiB, systems {len(d["systems"])}, layers {sum(len(s["layers"]) for s in d["systems"])}')
print('features.required:', d['features']['required'])
