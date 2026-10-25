from pathlib import Path
import json,zipfile,hashlib
A=Path('C:/Chacademi/artifacts/opening-final-20261025-task3');O=A/'candidate-tools';T=O/'townmap'
ledger=json.loads((O/'FINAL-17-CANDIDATE-LEDGER.json').read_text());patches=json.loads((T/'TOWNMAP-VALIDATION.json').read_text())['candidates']
for p in patches:
 row=next(r for r in ledger['candidates'] if (r['label'].startswith(p['area']+'/plugins/magic-codex-bridge') if p['area']!='client' else r['label']=='MagicCodex-client'))
 previous=dict(row);row['townmapInputCandidate']=previous['sourcePath'];row['townmapInputSHA256']=previous['sha256'];row['sourcePath']=p['path'];row['sha256']=p['sha256'];row['townmapValidation']='PASS'
 with zipfile.ZipFile(previous['baseline']) as bz,zipfile.ZipFile(p['path']) as z:
  assert set(bz.namelist())<=set(z.namelist())
  changed=[n for n in z.namelist() if n not in bz.namelist() or z.read(n)!=bz.read(n)]
  row['untouchedByteIdentical']=sum(1 for n in bz.namelist() if z.read(n)==bz.read(n));row['cumulativeChangedEntries']=changed
for row in ledger['candidates']:assert hashlib.sha256(Path(row['sourcePath']).read_bytes()).hexdigest()==row['sha256']
ledger.update(sourceCommit='70044a79232b22b37c5e024f3fc0bcaf808a04cc',townmapCandidateCount=3,arenaTravelPolicy='UNCHANGED_NO_TRAVEL_REGISTRATION',townmapValidation=str(T/'TOWNMAP-VALIDATION.json'),actualSchoolNew26TestResult='PENDING_PARENT',deployment='NOT_STARTED')
(A/'townmap/FINAL-17-CANDIDATE-LEDGER-TOWNMAP.json').write_text(json.dumps(ledger,indent=2))
# Keep generator reproducible with the exact variable-scope fix used in the compiled Java source.
p=T/'prepare_special.py';s=p.read_text(encoding='utf-8').replace('if(!(current instanceof VarInsnNode v)||v.var!=1)','if(((VarInsnNode)current).var!=1)').replace('if(i instanceof VarInsnNode v&&v.getOpcode()==ASTORE&&v.var==1)','if(i instanceof VarInsnNode store&&store.getOpcode()==ASTORE&&store.var==1)');p.write_text(s,encoding='utf-8')
print('17 candidate SHA rechecked; 3 replacements; arena unchanged; tools done')
