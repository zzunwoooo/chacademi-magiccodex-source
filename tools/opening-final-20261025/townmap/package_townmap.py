from pathlib import Path
import sys,json,zipfile,hashlib,os
A=Path('C:/Chacademi/artifacts/opening-final-20261025-task3');O=A/'candidate-tools';T=O/'townmap';C=A/'townmap/candidates';W=Path('C:/Chacademi/staging/opening-final-20261025-task3')
sys.path.insert(0,str(O));import bounded_package as b
b.o=C;b.cp=(T/'asm-classpath.txt').read_text();b.java='C:/Program Files/Java/jdk-21.0.10/bin/java.exe'
ledger=json.loads((O/'FINAL-17-CANDIDATE-LEDGER.json').read_text())['candidates'];oldinputs=json.loads((T/'base-raw-inputs.json').read_text())
for i in oldinputs:assert hashlib.sha256(Path(i['path']).read_bytes()).hexdigest()==i['sha256']
def raw(module):
 return next(p for p in (W/module/'build/libs').glob('*.jar') if not any(n in p.name for n in ['-sources','-dev','-javadoc']))
results=[]
for area,module,token in [('school','magic-codex-paper','school/plugins/magic-codex-bridge'),('wild','magic-codex-paper','wild/plugins/magic-codex-bridge'),('client','magic-codex-fabric','MagicCodex-client')]:
 r=next(x for x in ledger if x['label'].startswith(token));live=Path(r['sourcePath']);assert hashlib.sha256(live.read_bytes()).hexdigest()==r['sha256']
 new=raw(module);old=next(Path(i['path']) for i in oldinputs if ('ui-' in Path(i['path']).name)==(area=='client'));assert hashlib.sha256(new.read_bytes()).hexdigest()!=hashlib.sha256(old.read_bytes()).hexdigest(),'new raw not ready'
 os.environ['OPENING_SERVER_AREA']=area
 prefs=['school/magiccodex/client/TravelScreen'] if area=='client' else ['school/magiccodex/paper/TravelBridge','school/magiccodex/paper/TravelMapFit']
 report,changes=b.class_delta(live,new,old,prefs,'townmap-'+area)
 resources=['assets/magiccodex/textures/gui/travel/maps/school.png'] if area=='client' else ['travel.yml']
 b.resource_delta(live,new,old,resources,changes,report)
 candidate=C/('magic-codex-townmap-'+area+'-private.jar')
 if report['conflicts']:results.append({'area':area,'status':'CONFLICTS','conflicts':report['conflicts']})
 else:
  p=b.pack(live,candidate,changes,report,'opening-final-townmap-private-20261025','fabric' if area=='client' else 'bukkit')
  results.append({'area':area,'status':'PRIVATE_PACKAGED','path':str(candidate),'sha256':p['sha256']})
 (T/(area+'-full-report.json')).write_text(json.dumps(report,indent=2))
 (T/'packaging-results.json').write_text(json.dumps(results,indent=2));print(json.dumps(results[-1]),flush=True)
arena=next(r for r in ledger if r['label'].startswith('arena/plugins/magic-codex-bridge'));results.append({'area':'arena','status':'UNCHANGED_TRAVEL_NOT_INSTALLED','path':arena['sourcePath'],'sha256':arena['sha256']});(T/'packaging-results.json').write_text(json.dumps(results,indent=2))
