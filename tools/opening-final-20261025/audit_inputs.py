from pathlib import Path
import json,hashlib,subprocess,zipfile
O=Path(__file__).parent
W=Path('C:/Chacademi/staging/opening-final-20261025-task3')
A=Path('C:/Chacademi/artifacts/opening-prep-safe-integration-20261025-task3')
JAVA='C:/Program Files/Java/jdk-21.0.10/bin/java.exe'
CP=(O/'asm-classpath.txt').read_text()
def scan(p):
 q=subprocess.run([JAVA,'-Xmx256m','-cp',CP,'JarMembers',str(p)],capture_output=True,text=True,encoding='utf-8')
 if q.returncode:raise RuntimeError(q.stderr)
 rows=[r.split('	') for r in q.stdout.splitlines()]
 return {x[1] for x in rows if x[0]=='C'},{tuple(x[1:4]):x[4] if len(x)>4 else None for x in rows if x[0] in ['M','F']}
def module(target):
 for token,path in [('magic-codex-bridge','magic-codex-paper'),('magic-mythic','magic-mythic-paper'),('kyudichi','kyudichi-paper'),('chaca-npc','chaca-npc-paper'),('housing','housing-paper'),('chacademy-story','chacademy-story/plugin'),('magic-discovery','magic-discovery-paper')]:
  if token in target:return path
def run():
 m=json.loads((W/'docs/handoff/live-20261025/operating-manifest.json').read_text());spec=[]
 for f in m['serverFiles']:
  if f.get('kind')!='jar':continue
  mod=module(f['target'])
  if not mod:continue
  spec.append(dict(label=f['target'],live=str(Path('C:/Chacademi/network')/f['target']),liveSHA256=f['sha256'],module=mod))
 for f in m['temporaryLauncherClients']:
  mod={'magiccodex':'magic-codex-fabric','portablevfx':'portable-vfx-runtime/client','chaca_story':'chacademy-story/mod'}[f['modId']]
  name=Path(f['relativeTarget']).name
  candidates=list(A.glob('*/'+name))
  if not candidates:raise ValueError('missing retained client '+name)
  spec.append(dict(label=f['label'],live=str(candidates[0]),liveSHA256=f['sha256'],module=mod))
 (O/'input-spec.json').write_text(json.dumps(spec,indent=2))
 results=[]
 for s in spec:
  jars=[p for p in (W/s['module']/'build/libs').glob('*.jar') if not any(t in p.name for t in ['-sources','-dev','-javadoc'])]
  if not jars:results.append(dict(label=s['label'],status='RAW_NOT_READY',module=s['module']));continue
  raw=max(jars,key=lambda p:p.stat().st_mtime);live=Path(s['live'])
  if hashlib.sha256(live.read_bytes()).hexdigest()!=s['liveSHA256']:raise ValueError('live hash mismatch '+s['label'])
  lc,lm=scan(live);rc,rm=scan(raw)
  results.append(dict(s,status='AUDIT_ONLY_NOT_CANDIDATE',raw=str(raw),rawSHA256=hashlib.sha256(raw.read_bytes()).hexdigest(),missingRawClasses=sorted(lc-rc),missingRawMembers=[list(k) for k in sorted(lm.keys()-rm.keys())],differentCommonMethods=[list(k) for k in sorted(lm.keys()&rm.keys()) if lm[k]!=rm[k]],vfxCacheMembers=[list(k) for k in sorted(lm) if any(t in str(k) for t in ['residentFingerprint','PreloadBatch','prepareFresh','ResourceFingerprint','dispose','cleanup'])]))
 (O/'raw-live-audit.json').write_text(json.dumps(results,indent=2))
 print(json.dumps([dict(label=r['label'],status=r['status'],missingClasses=len(r.get('missingRawClasses',[])),missingMembers=len(r.get('missingRawMembers',[]))) for r in results]))
if __name__=='__main__':run()
