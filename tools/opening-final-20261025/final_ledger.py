from pathlib import Path
import json,subprocess,hashlib,zipfile
O=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools');W=Path('C:/Chacademi/staging/opening-final-20261025-task3')
raw=json.loads((O/'raw-live-audit.json').read_text());mk=json.loads((O/'mythic-kyu/final-candidates.json').read_text());hs=json.loads((O/'housing/housing-results.json').read_text());ledger=[]
for i,s in enumerate(raw):
 if i in [4,5,6,9]:candidate=next(Path(r['path']) for r in mk if str(Path(r['baseline'])).lower()==str(Path(s['live'])).lower())
 elif i in [7,8]:candidate=Path(next(r['candidate'] for r in hs if r['server']==s['label'].split('/')[0]))
 else:candidate=O/('new-'+str(i)+'-'+s['module'].replace('/','-')+'.jar')
 p=json.loads(Path(str(candidate)+'.preservation.json').read_text());assert hashlib.sha256(candidate.read_bytes()).hexdigest()==p['sha256'];assert not p['removedEntries'];assert p['linkage']=='PASS'
 target=s['label']
 if '/plugins/' not in target:
  manifest=json.loads((W/'docs/handoff/live-20261025/operating-manifest.json').read_text());target=next(r['relativeTarget'] for r in manifest['temporaryLauncherClients'] if r['label']==s['label'])
 ledger.append(dict(label=s['label'],target=target,sourcePath=str(candidate),sha256=p['sha256'],baseline=s['live'],baselineSHA256=s['liveSHA256'],removedEntries=0,untouchedByteIdentical=p['untouchedByteIdentical'],internalLinkage='PASS',deployment='NOT_STARTED',sourceOnlyReproduction=False))
(O/'FINAL-17-CANDIDATE-LEDGER.json').write_text(json.dumps({'candidates':ledger,'sourceOnlyReproduction':False,'deployment':'NOT_STARTED'},indent=2))
cp=(O/'asm-classpath.txt').read_text();deps=O/'housing/dependencies.txt';results=[]
for i in [0,1,2,3,4,5,6,9,13,14]:
 r=ledger[i];jar=Path(r['sourcePath']);p=json.loads(Path(str(jar)+'.preservation.json').read_text());names=[n for n in p['changedEntries'] if n.endswith('.class')];lst=O/('remaining-'+str(i)+'-classes.txt');lst.write_text(chr(10).join(names));dep=deps
 if i==14:dep=Path('C:/Chacademi/artifacts/opening-prep-safe-integration-20261025-task3/bounded-packaging/ui-final-runtime-dependencies.txt')
 q=subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/java.exe','-Xverify:all','-cp',cp,'FinalJvmVerify',str(jar),str(lst),str(dep)],capture_output=True,text=True,encoding='utf-8',errors='replace')
 (O/('remaining-'+str(i)+'-jvm.log')).write_text(q.stdout+q.stderr);results.append(dict(label=r['label'],exit=q.returncode,summary=q.stdout.splitlines()[-1:]));print(json.dumps(results[-1]),flush=True)
(O/'remaining-jvm-results.json').write_text(json.dumps(results,indent=2))
