from pathlib import Path
import sys,subprocess,zipfile,json,os
sys.path.insert(0,str(Path(__file__).parent))
from bounded_package import class_delta,resource_delta,pack
r=Path('C:/Chacademi/staging/opening-prep-safe-integration-20261025-task3');baseline=Path('C:/Chacademi/staging/release-bag-travel-off-20261024-task9');o=Path(__file__).parent
jar=lambda folder:next(p for p in folder.glob('*.jar') if not any(x in p.name for x in ['-sources','-dev','-javadoc']))
def run(area,emit=False):
 live=Path('C:/Chacademi/network')/area/'plugins'/('magic-codex-bridge-final-20261024-'+area+'.jar');new=jar(r/'magic-codex-paper/build/libs');old=jar(baseline/'magic-codex-paper/build/libs');paths=subprocess.check_output(['git','diff','--name-only','7d8ec6e1','--','magic-codex-paper/src/main/java','magic-codex-protocol/src/main/java','magic-codex-database/src/main/java'],cwd=r).decode().splitlines();prefs={s.split('/src/main/java/',1)[1][:-5] for s in paths if s.endswith('.java')}
 with zipfile.ZipFile(new) as nz,zipfile.ZipFile(old) as oz:
  for name in nz.namelist():
   if name.startswith('school/magiccodex/') and name.endswith('.class') and (name not in oz.namelist() or nz.read(name)!=oz.read(name)):prefs.add(name[:-6].split('$',1)[0])
 if area=='arena':prefs={s for s in prefs if not s.startswith(('school/magiccodex/paper/Travel','school/magiccodex/paper/Book','school/magiccodex/paper/ReturnTicketBridge','school/magiccodex/protocol/Travel','school/magiccodex/protocol/Book'))}
 os.environ['OPENING_SERVER_AREA']=area
 report,changes=class_delta(live,new,old,sorted(prefs),'bridge-final-'+area)
 changes['school/magiccodex/paper/OpeningHooks.class']=(o/'helper-classes/school/magiccodex/paper/OpeningHooks.class').read_bytes()
 names=subprocess.check_output(['git','diff','--name-only','7d8ec6e1','--','magic-codex-paper/src/main/resources'],cwd=r).decode().splitlines();names=[s.split('/src/main/resources/',1)[1] for s in names if not s.endswith('plugin.yml')];names=[n for n in names if area!='arena' or n not in ['books.yml','travel.yml','return-ticket.yml']];resource_delta(live,new,old,names,changes,report)
 report['operatingIsolation']={'lifecycle':'original shared bag/travel/mileage/core helpers retained; only ReturnTicket hook added','arena':'legacy Mana constructor callback retained; no new Travel registration; unchanged spatial-leap defaults remain operating values' if area=='arena' else 'existing server-specific bytecode retained'};(o/('bridge-final-'+area+'-probe.json')).write_text(json.dumps(report,indent=2));print(json.dumps({'server':area,'classEntries':len(changes),'conflicts':report['conflicts']}),flush=True)
 if emit:return pack(live,o/('magic-codex-bridge-opening-safe-20261025-'+area+'-v2.jar'),changes,report,'0.31.9-opening-safe-v2-20261025-'+area)
 return report,changes
if __name__=='__main__':
 areas=[a for a in sys.argv[1:] if not a.startswith('--')] or ['school','wild','arena']
 for area in areas:run(area,'--emit' in sys.argv)
