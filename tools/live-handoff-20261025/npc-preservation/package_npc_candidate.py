from pathlib import Path
import subprocess,zipfile,json,hashlib,copy,sys,difflib,re
O=Path(__file__).resolve().parent
R=Path('C:/Chacademi/staging/opening-prep-safe-integration-20261025-task3')
BASE=Path('C:/Chacademi/network/school/plugins/chaca-npc-0.3.0-npcmodel.3.jar')
OLD=Path('C:/Chacademi/staging/release-bag-travel-off-20261024-task9/chaca-npc-paper/build/libs/chaca-npc-0.3.0-npcmodel.3.jar')
RAW=R/'chaca-npc-paper/build/libs/chaca-npc-0.3.0-npcmodel.3.jar'
DEST=O/'chaca-npc-opening-prep-safe-candidate.jar'
LIVE_HASH='8b83629b167dfe5d4222367d362cb3837b4f2ca3e5baca079dcb74e3631445ee'
OLD_HASH='113973ee8ee6e04f8b61241bef4b22a70a6fe03107c7a37bc2b94541c638207e'
J=Path('C:/Program Files/Java/jdk-21.0.10/bin')
FAMILIES=['config/CharacterRepository','config/Settings','core/Defs','core/PromptBuilder','core/ReplyParser','dialogue/DialogueService','integration/MagicCodexLink']
sha=lambda b:hashlib.sha256(b).hexdigest()
def run(args,log):
 q=subprocess.run(args,capture_output=True,text=True,encoding='utf-8',errors='replace');(O/log).write_text(q.stdout+q.stderr,encoding='utf-8')
 if q.returncode:raise RuntimeError('See '+str(O/log))
 return q.stdout
assert sha(BASE.read_bytes())==LIVE_HASH,'Live NPC changed: review baseline'
assert sha(OLD.read_bytes())==OLD_HASH,'Old raw changed: review baseline'
deps=[next(Path('C:/Chacademi/tools/gradle-cache/caches/modules-2/files-2.1/org.ow2.asm',n,'9.7.1').glob('*/*.jar')) for n in ['asm','asm-tree','asm-util','asm-analysis']]
cp=';'.join([str(O),*map(str,deps)])
run([str(J/'javac.exe'),'-J-Xmx128m','-cp',cp,*[str(O/x) for x in ['BoundedReleasePatch.java','AuditAffinityLinkage.java','BudgetDescriptorAudit.java']]],'compile-tools.log')
changes={};actions=[]
with zipfile.ZipFile(BASE) as b,zipfile.ZipFile(OLD) as old,zipfile.ZipFile(RAW) as raw:
 for family in FAMILIES:
  prefix='kr/chacademy/npc/'+family
  names=[n for n in raw.namelist() if n.endswith('.class') and (n==prefix+'.class' or n.startswith(prefix+'$'))]
  assert names,'Raw family absent '+family
  for name in names:
   if name not in b.namelist():changes[name]=raw.read(name);actions.append([name,'add']);continue
   assert name in old.namelist(),'No old raw '+name
   work=O/'class-work';work.mkdir(exist_ok=True);tag=name.rsplit('/',1)[-1].replace('$','-')
   ps=[work/(tag+'-'+v+'.class') for v in ['live','new','old','out']]
   for p,data in zip(ps[:3],[b.read(name),raw.read(name),old.read(name)]):p.write_bytes(data)
   run([str(J/'java.exe'),'-Xmx128m','-cp',cp,'BoundedReleasePatch','delta',str(ps[0]),str(ps[1]),str(ps[3]),str(ps[2])],tag+'-preservation.log')
   changes[name]=ps[3].read_bytes();actions.append([name,'bounded-method-field-delta'])
 # Packaged config: keep current defaults; fill newly introduced keys only.
 sys.path.insert(0,'C:/Chacademi/tools/review-yaml');import yaml
 before=yaml.safe_load(b.read('config.yml'));requested=yaml.safe_load(raw.read('config.yml'))
 def fill(current,new):
  if not isinstance(current,dict) or not isinstance(new,dict):return current
  result=copy.deepcopy(current)
  for k,v in new.items():result[k]=fill(result[k],v) if k in result else copy.deepcopy(v)
  return result
 merged=fill(before,requested)
 if merged!=before:changes['config.yml']=yaml.safe_dump(merged,sort_keys=False,allow_unicode=True).encode('utf-8')
 # Prompt additions only; existing operational wording is retained.
 key='prompts/common.txt';previous=old.read(key).decode('utf-8-sig').splitlines();new=raw.read(key).decode('utf-8-sig').splitlines();prompt=b.read(key).decode('utf-8-sig')
 for tag,a,z,c,d in difflib.SequenceMatcher(a=previous,b=new).get_opcodes():
  if tag=='equal':continue
  assert tag=='insert','Prompt changed existing lines; manual review required'
  for line in new[c:d]:
   if line not in prompt.splitlines():prompt=prompt.rstrip()+chr(10)+line+chr(10)
 if prompt.encode('utf-8')!=b.read(key):changes[key]=prompt.encode('utf-8')
 changes['plugin.yml']=re.sub(r'(?m)^version:.*$',"version: '0.3.1-opening-prep-safe.20261025'",b.read('plugin.yml').decode('utf-8')).encode('utf-8')
 allowed=set(changes)
 with zipfile.ZipFile(DEST,'w') as target:
  for info in b.infolist():target.writestr(copy.copy(info),changes.pop(info.filename,b.read(info.filename)))
  for key,value in changes.items():target.writestr(key,value,compress_type=zipfile.ZIP_DEFLATED)
 with zipfile.ZipFile(DEST) as target:
  assert target.testzip() is None and len(target.namelist())==len(set(target.namelist()))
  assert set(b.namelist())<=set(target.namelist())
  changed=[key for key in target.namelist() if key not in b.namelist() or target.read(key)!=b.read(key)]
  assert set(changed)<=allowed
  frozen=[key for key in b.namelist() if key not in allowed]
  assert all(target.read(key)==b.read(key) for key in frozen)
  assert target.read('kr/chacademy/npc/budget/BudgetService.class')==b.read('kr/chacademy/npc/budget/BudgetService.class')
run([str(J/'java.exe'),'-Xmx128m','-cp',cp,'BudgetDescriptorAudit',str(BASE),str(DEST)],'budget-descriptor-check.log')
run([str(J/'java.exe'),'-Xmx128m','-cp',cp,'AuditAffinityLinkage',str(DEST),str(BASE)],'linkage.log')
report={'status':'CANDIDATE_ONLY_NOT_INSTALLED','base':str(BASE),'baseSHA256':LIVE_HASH,'rawBaseline':str(OLD),'rawBaselineSHA256':OLD_HASH,'rawCandidate':str(RAW),'rawCandidateSHA256':sha(RAW.read_bytes()),'candidate':str(DEST),'candidateSHA256':sha(DEST.read_bytes()),'changedEntries':changed,'removedEntries':[],'untouchedByteIdentical':len(frozen),'BudgetServiceByteIdentical':True,'budgetDescriptorAudit':'PASS','linkage':'PASS','classActions':actions,'operatingChanged':False}
(O/'npc-candidate-preservation.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
