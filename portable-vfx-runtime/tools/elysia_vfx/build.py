"""python build.py <spec_name> [--notes NOTES.md]  → /home/claude/vfx/fin/<Name>/ + <Name>.zip"""
import sys, os, subprocess, shutil, zipfile, json, glob
from PIL import Image
T = '/home/claude/vfx/vfx_toolkit'; V = '/home/claude/vfx'
name = sys.argv[1]; notes = sys.argv[3] if len(sys.argv) > 3 else None
spec = f'{V}/specs/_{name}.js'
body = open(f'{V}/specs/{name}.js').read()
first = body.splitlines()[0]
if first.startswith('//@base '): body = body + '\n' + open(f'{V}/specs/' + first.split()[1]).read()
open(spec, 'w').write(open(f'{V}/specs/lib.js').read() + '\n' + body)
out = f'{V}/out'; os.makedirs(out, exist_ok=True)
args = [sys.executable, f'{T}/tools/make_bundle.py', spec, f'{V}/tex/out', out, '--accent', '#e8b84a']
if notes: args += ['--notes', notes]
r = subprocess.run(args, capture_output=True, text=True)
print(r.stdout[-1500:], r.stderr[-3000:])
d = json.load(open(glob.glob(f'{out}/*/vfx.json')[0])) if False else None
bname = [l for l in r.stdout.splitlines() if l.startswith('bundle:')][0].split('/')[-1].split('.zip')[0]
shutil.rmtree(f'{out}/{bname}x', ignore_errors=True)
fin = f'{V}/fin'; os.makedirs(fin, exist_ok=True); shutil.rmtree(f'{fin}/{bname}', ignore_errors=True)
r2 = subprocess.run([sys.executable, f'{T}/tools/client_compat.py', 'fix', f'{out}/{bname}', f'{fin}/{bname}'], capture_output=True, text=True); print('FIX', r2.stdout[-600:], r2.stderr[-600:])
r3 = subprocess.run([sys.executable, f'{T}/tools/client_compat.py', 'check', f'{fin}/{bname}'], capture_output=True, text=True); print('CHECK', r3.stdout[-800:], r3.stderr[-400:])
subprocess.run([sys.executable, f'{T}/tools/validate.py', f'{fin}/{bname}'], check=True)
subprocess.run([sys.executable, f'{T}/engine/build_preview.py', f'{fin}/{bname}', f'{fin}/{bname}/preview.html', '--accent', '#e8b84a'], check=True, capture_output=True)
zp = f'{fin}/{bname}.zip'
with zipfile.ZipFile(zp, 'w', zipfile.ZIP_DEFLATED) as z:
    for dp, _, fs in os.walk(f'{fin}/{bname}'):
        for f in sorted(fs): z.write(os.path.join(dp, f), os.path.relpath(os.path.join(dp, f), fin))
rgba = sum(Image.open(p).size[0] * Image.open(p).size[1] * 4 for p in glob.glob(f'{fin}/{bname}/textures/*.png'))
print(f'OK {bname} zip={os.path.getsize(zp)//1024}KB rgba={rgba/2**20:.2f}MiB')
