from pathlib import Path
import sys,zipfile,json,subprocess
sys.path.insert(0,str(Path(__file__).parent))
from bounded_package import class_delta,resource_delta,pack
r=Path('C:/Chacademi/staging/opening-prep-safe-integration-20261025-task3');baseline=Path('C:/Chacademi/staging/opening-baseline-7d8ec6e1-20261025-task3');o=Path(__file__).parent
jar=lambda folder:next(p for p in folder.glob('*.jar') if not any(x in p.name for x in ['-sources','-dev','-javadoc']))
def inputs():return Path('C:/Chacademi/artifacts/offline-integrated-20261024-task9/magic-codex-ui-integrated-offline-20261024+mc1.21.4.jar'),jar(r/'magic-codex-fabric/build/libs'),jar(baseline/'magic-codex-fabric/build/libs')
def run(emit=False):
 live,new,old=inputs();prefs=set()
 # Include constants inlined in unchanged sources, not only source-diff prefixes.
 with zipfile.ZipFile(new) as nz,zipfile.ZipFile(old) as oz:
  for name in nz.namelist():
   if name.startswith('school/magiccodex/') and name.endswith('.class') and (name not in oz.namelist() or nz.read(name)!=oz.read(name)):prefs.add(name[:-6].split('$',1)[0])
 prefs.add('school/magiccodex/client/TitleClient')
 report,changes=class_delta(live,new,old,sorted(prefs),'ui-final');report['constantInliningScan']='all school/magiccodex class bytes against pristine7d raw; TitleClient forced'
 names=subprocess.check_output(['git','diff','--name-only','7d8ec6e1','--','magic-codex-fabric/src/main/resources'],cwd=r).decode().splitlines();names=[s.split('/src/main/resources/',1)[1] for s in names]
 names.extend(['magiccodex.mixins.json','magic-codex-ui-refmap.json']);names=[x for x in names if x!='fabric.mod.json']
 resource_delta(live,new,old,sorted(set(names)),changes,report);(o/'ui-final-probe.json').write_text(json.dumps(report,indent=2));print(json.dumps({'prefixes':len(prefs),'classChanges':len(changes),'conflicts':report['conflicts']}),flush=True)
 if emit:return pack(live,o/'magic-codex-ui-opening-safe-20261025+mc1.21.4.jar',changes,report,'0.41.0-opening-safe-20261025+mc1.21.4','fabric')
 return report,changes
if __name__=='__main__':run('--emit' in sys.argv)
