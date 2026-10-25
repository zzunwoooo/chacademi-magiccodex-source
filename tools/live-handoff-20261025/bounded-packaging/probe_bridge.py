from pathlib import Path
import subprocess,zipfile,json,hashlib,copy,sys,re
sys.path.insert(0,'C:/Chacademi/tools/review-yaml');import yaml
r=Path('C:/Chacademi/staging/opening-prep-safe-integration-20261025-task3');o=Path(__file__).parent;java='C:/Program Files/Java/jdk-21.0.10/bin/java.exe';cp=(o/'asm-classpath.txt').read_text();baseline=Path('C:/Chacademi/staging/release-bag-travel-off-20261024-task9');sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def jar(root):return next(p for p in root.glob('*.jar') if not any(x in p.name for x in ['-sources','-javadoc','-dev']))
def paths(mod,folder):return subprocess.check_output(['git','diff','--name-only','7d8ec6e1','--',mod+'/'+folder],cwd=r).decode().splitlines()
def prefixes(mods):
 result=[]
 for mod in mods:
  for path in paths(mod,'src/main/java'):
   if path.endswith('.java'):result.append(path.split('/src/main/java/',1)[1][:-5])
 return sorted(set(result))
def entries(z,prefix):return [p for p in z.namelist() if p==prefix+'.class' or p.startswith(prefix+'$') and p.endswith('.class')]
def probe(area):
 live=Path('C:/Chacademi/network')/area/'plugins'/('magic-codex-bridge-final-20261024-'+area+'.jar');new=jar(r/'magic-codex-paper/build/libs');old=jar(baseline/'magic-codex-paper/build/libs');changes={};audit=[];conflicts=[];folder=o/('bridge-'+area);folder.mkdir(exist_ok=True)
 with zipfile.ZipFile(live) as lz,zipfile.ZipFile(new) as nz,zipfile.ZipFile(old) as bz:
  for pref in prefixes(['magic-codex-paper','magic-codex-protocol','magic-codex-database']):
   for name in entries(nz,pref):
    label=name.replace('/','_').replace('$','-');before=folder/(label+'.live');after=folder/(label+'.new');base=folder/(label+'.base');out=folder/(label+'.out')
    if name not in lz.namelist():changes[name]=nz.read(name);audit.append({'entry':name,'action':'new-class'});continue
    if name not in bz.namelist():conflicts.append({'entry':name,'error':'existing-live-class-without-source-baseline'});continue
    before.write_bytes(lz.read(name));after.write_bytes(nz.read(name));base.write_bytes(bz.read(name));v=subprocess.run([java,'-Xmx128m','-cp',cp,'OpeningDelta',str(before),str(after),str(base),str(out)],capture_output=True,text=True,encoding='utf-8',errors='replace');(folder/(label+'.log')).write_text(v.stdout+v.stderr,encoding='utf-8')
    if v.returncode:conflicts.append({'entry':name,'error':v.stdout+v.stderr});continue
    result=out.read_bytes()
    if result!=lz.read(name):changes[name]=result
    audit.append({'entry':name,'action':'bounded-method-field-delta','result':v.stdout.strip()})
  report={'server':area,'live':str(live),'liveSHA256':sha(live),'newRaw':str(new),'newRawSHA256':sha(new),'sourceBaselineRaw':str(old),'sourceBaselineRawSHA256':sha(old),'changedPrefixes':prefixes(['magic-codex-paper','magic-codex-protocol','magic-codex-database']),'classChanges':audit,'conflicts':conflicts,'ready':False}
 (o/('bridge-'+area+'-probe.json')).write_text(json.dumps(report,indent=2));print(json.dumps({'server':area,'classActions':len(audit),'conflicts':conflicts}),flush=True)
 return report,changes
if __name__=='__main__':
 for area in sys.argv[1:] or ['school','wild','arena']:probe(area)
