from pathlib import Path
import json,subprocess,sys,os,zipfile,hashlib
O=Path(__file__).parent;sys.path.insert(0,str(O))
import bounded_package as b
W=Path('C:/Chacademi/staging/opening-final-20261025-task3');B=Path('C:/Chacademi/staging/opening-prep-safe-integration-20261025-task3')
rows=json.loads((O/'raw-live-audit.json').read_text())
def git(*args):return subprocess.check_output(['git','-C',str(W),*args]).decode().splitlines()
changed=git('diff','--name-only','c96c5ae3','HEAD')
prior=git('diff','--name-only','40e2f694','c96c5ae3','--','*src/main*')
b.java='C:/Program Files/Java/jdk-21.0.10/bin/java.exe';b.cp=(O/'asm-classpath.txt').read_text()
result=[]
for i,s in enumerate(rows):
 if s['status']!='AUDIT_ONLY_NOT_CANDIDATE':continue
 module=s['module'];folders=[module]
 if module in ['magic-codex-paper','magic-codex-fabric']:folders+=['magic-codex-protocol','magic-codex-database']
 if module=='chaca-npc-paper':folders+=['chaca-npc-protocol','chaca-npc-model-protocol']
 relevant=[p for p in changed if any(p.startswith(m+'/src/main/') for m in folders)]
 baselineAmbiguous=[p for p in prior if p in relevant]
 if baselineAmbiguous:raise ValueError('source baseline differs '+str(baselineAmbiguous))
 prefs={p.split('/src/main/java/',1)[1][:-5] for p in relevant if '/src/main/java/' in p and p.endswith('.java')}
 resources=[p.split('/src/main/resources/',1)[1] for p in relevant if '/src/main/resources/' in p]
 olds=[p for p in (B/module/'build/libs').glob('*.jar') if not any(t in p.name for t in ['-sources','-dev','-javadoc'])]
 if not olds:result.append(dict(label=s['label'],status='BASELINE_RAW_MISSING',module=module));continue
 old=max(olds,key=lambda p:p.stat().st_mtime)
 area=s['label'].split('/')[0] if '/plugins/' in s['label'] else ''
 os.environ['OPENING_SERVER_AREA']=area
 label='new-'+str(i)+'-'+module.replace('/','-')
 report,delta=b.class_delta(s['live'],s['raw'],str(old),sorted(prefs),label)
 b.resource_delta(s['live'],s['raw'],str(old),resources,delta,report)
 report['sourceBaselineProvenance']={'rawSource':'40e2f6944bd15a2c23f0d08b42c8dba4dcdc0e31','scopedSourceEquivalentTo':'c96c5ae3','c96ChangesOutsideSelectedInputs':prior,'selectedSourcePaths':relevant}
 status='CONFLICTS_NOT_PACKAGED'
 if not report['conflicts']:
  dest=O/(label+'.jar');kind='fabric' if module in ['magic-codex-fabric','portable-vfx-runtime/client','chacademy-story/mod'] else 'bukkit'
  try:
   b.pack(s['live'],dest,delta,report,'opening-final-private-20261025',kind);status='PRIVATE_CANDIDATE_LINKED_NOT_DEPLOYABLE'
  except Exception as ex:report['packError']=str(ex);status='FINAL_AUDIT_FAILED_NOT_DEPLOYABLE'
 (O/(label+'-full-report.json')).write_text(json.dumps(report,indent=2))
 result.append(dict(label=s['label'],status=status,changes=len(delta),conflicts=report['conflicts']))
 (O/'candidate-preparation-results.json').write_text(json.dumps(result,indent=2))
 print(json.dumps(result[-1]),flush=True)
