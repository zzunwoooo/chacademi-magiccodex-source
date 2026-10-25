from pathlib import Path
import zipfile,json,hashlib,subprocess,os,re,xml.etree.ElementTree as ET,shutil
r=Path('C:/Chacademi/staging/shops-notifications-20261024-task9');o=Path('C:/Chacademi/artifacts/shops-notifications-20261024-task9');t=r/'tools/task9-integration'
m=json.loads((o/'preservation-manifest.json').read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
java=Path('C:/Program Files/Java/jdk-21.0.10/bin');deps=[]
for lib in ['asm','asm-tree','asm-util','asm-analysis']:
 deps.append(next(Path('C:/Chacademi/tools/gradle-cache/caches/modules-2/files-2.1/org.ow2.asm',lib,'9.7.1').glob('*/*.jar')))
cp=os.pathsep.join([str(t),*map(str,deps)])
# Called only after all Gradle builds have returned: small verification JVMs run sequentially.
subprocess.run([str(java/'javac.exe'),'-J-Xmx256m','-cp',cp,str(t/'PreserveInitializers.java'),str(t/'AuditAffinityLinkage.java')],check=True)
def raw(module):
 files=[p for p in (r/module/'build/libs').glob('*.jar') if not any(k in p.name for k in ['-sources','-dev','-javadoc'])];assert len(files)==1,files;return files[0]
def patch(base,mode,label):
 name='school/magiccodex/client/MagicCodexClient.class' if mode=='client' else 'school/magiccodex/paper/MagicCodexBridge.class'
 with zipfile.ZipFile(base) as z:(o/(label+'-before.class')).write_bytes(z.read(name))
 p=subprocess.run([str(java/'java.exe'),'-Xmx256m','-cp',cp,'PreserveInitializers',mode,str(o/(label+'-before.class')),str(o/(label+'-after.class'))],capture_output=True)
 (o/(label+'-initializer-preservation.log')).write_bytes(p.stdout+p.stderr);assert p.returncode==0,p.stdout.decode('utf-8','replace')+p.stderr.decode('utf-8','replace')
 return name,(o/(label+'-after.class')).read_bytes()
def family(n,names):return n.endswith('.class') and n.rsplit('/',1)[-1].split('$')[0].removesuffix('.class') in names
def pack(base,module,label,classes,version,basehash):
 assert sha(base.read_bytes())==basehash,'Baseline hash mismatch'
 target=o/('magic-codex-ui-task9-integrated+mc1.21.4.jar' if label=='ui' else 'magic-codex-bridge-task9-integrated-'+label+'.jar')
 init,initbytes=patch(base,'client' if label=='ui' else 'server',label)
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(raw(module)) as n:
  if label!='ui':
   baseline=Path(m['serverSourceBaseline']['path']);assert sha(baseline.read_bytes())==m['serverSourceBaseline']['sha256']
   with zipfile.ZipFile(baseline) as source:
    for k in source.namelist():
     if family(k,classes) and k in b.namelist():assert source.read(k)==b.read(k),'Existing server source/compiled baseline differs '+k
  selected={k:n.read(k) for k in n.namelist() if family(k,classes)}
  for root in classes:assert any(k.rsplit('/',1)[-1]==root+'.class' for k in selected),'Missing family '+root
  if label=='ui':
   selected.update({k:n.read(k) for k in n.namelist() if k.startswith(('assets/magiccodex/textures/gui/mileage/','assets/magiccodex/textures/gui/notification/','assets/magiccodex/textures/gui/shop/v3/')) and not k.endswith('/')})
   meta=json.loads(b.read('fabric.mod.json'));meta['version']=version;selected['fabric.mod.json']=json.dumps(meta,ensure_ascii=False,indent=2).encode('utf-8')
  else:
   selected['shops/pipi.yml']=n.read('shops/pipi.yml');selected['plugin.yml']=re.sub(rb'(?m)^version:.*$',('version: '+version).encode(),b.read('plugin.yml'))
  selected[init]=initbytes;allowed=set(selected);oldnames=set(b.namelist())
  with zipfile.ZipFile(target,'w') as z:
   for info in b.infolist():z.writestr(info,selected.pop(info.filename,b.read(info.filename)))
   for k,v in selected.items():z.writestr(n.getinfo(k),v)
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(target) as z:
  assert z.testzip() is None
  assert oldnames<=set(z.namelist()),'Removed baseline entries'
  changed=sorted(k for k in z.namelist() if k not in oldnames or b.read(k)!=z.read(k));assert set(changed)<=allowed
  retained=sorted(oldnames-set(changed));assert all(b.read(k)==z.read(k) for k in retained)
  if label=='ui':
   for k,v in m['uiBase']['partialCompositionClasses'].items():
    if k!=init:assert sha(z.read(k))==v,'Partial composition lost '+k
   assert z.read('magic-codex-ui-refmap.json')==b.read('magic-codex-ui-refmap.json')
  frozen={k:sha(z.read(k)) for k in retained}
 report={'path':str(target),'sha256':sha(target.read_bytes()),'bytes':target.stat().st_size,'baseline':str(base),'baselineSHA256':basehash,'changedEntries':changed,'preservedEntries':len(retained),'preservedEntryHashes':frozen,'initializerPreservation':'PASS all original methods retained except exact mileage calls','removedEntries':[]}
 return report
ui=pack(Path(m['uiBase']['path']),'magic-codex-fabric','ui',m['approvedUiClassFamilies']+m['approvedProtocolClassFamilies'],'0.40.4-task9.1+mc1.21.4',m['uiBase']['sha256'])
bridges={}
for area,v in m['bridgeBases'].items():bridges[area]=pack(Path(v['path']),'magic-codex-paper',area,m['approvedBridgeClassFamilies']+m['approvedProtocolClassFamilies'],'0.31.5-task9.1',v['sha256'])
linkage={}
for label,v in [('ui',ui),*bridges.items()]:
 p=subprocess.run([str(java/'java.exe'),'-Xmx384m','-cp',cp,'AuditAffinityLinkage',v['path'],v['baseline']],capture_output=True)
 (o/('linkage-'+label+'.log')).write_bytes(p.stdout+p.stderr);linkage[label]={'exitCode':p.returncode,'result':p.stdout.decode('utf-8','replace').strip()};assert p.returncode==0,linkage[label]
# The separately built chat module must have exactly the same entry bytes as its completed handoff artifact.
chatraw=raw('client-mods/chacademi-chat-layout');chatbase=Path(m['chat']['jar'])
with zipfile.ZipFile(chatraw) as z,zipfile.ZipFile(chatbase) as b:
 assert set(z.namelist())==set(b.namelist());chatdiff=[k for k in z.namelist() if z.read(k)!=b.read(k)];assert not chatdiff,chatdiff
chat=o/chatbase.name;shutil.copyfile(chatbase,chat);assert sha(chat.read_bytes())==m['chat']['sha256']
# Keep ChacaNPC and the independently prepared NPC content in the release set unchanged; no operating writes.
aff=Path('C:/Chacademi/artifacts/affinity-gifts-integration-20261023-task7');npc=aff/'chaca-npc-0.3.1-npcmodel.2-buttons.1-affinity.1.jar'
content={p.name:sha(p.read_bytes()) for p in sorted((aff/'npc-content').glob('*.yml'))};assert len(content)==85
checks={}
for module in ['magic-codex-fabric','client-mods/chacademi-chat-layout',*json.loads(json.dumps(['magic-codex-paper','magic-discovery-paper','creature-spawns-paper','tornado-event-paper','chaca-npc-paper','chaca-portrait-paper','chacademi-party-velocity','magic-mythic-paper','gimmick-paper','kyudichi-paper','housing-paper','pet-interactions-paper','housing-presence-velocity']))]:
 rows=[ET.parse(p).getroot().attrib for p in (r/module/'build/test-results/test').glob('TEST-*.xml')];totals={k:sum(int(v.get(k,0)) for v in rows) for k in ['tests','failures','errors','skipped']};checks[module]=totals;assert not totals['failures'] and not totals['errors'],(module,totals)
assert checks['magic-codex-fabric']['tests']>=500 and checks['magic-codex-paper']['tests']>=474 and checks['client-mods/chacademi-chat-layout']['tests']>=160
report={'status':'BUILT_STATIC_VERIFIED_NOT_DEPLOYED_GAME_QA_PENDING','sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=r,text=True).strip(),'ui':ui,'bridges':bridges,'chat':{'path':str(chat),'sha256':sha(chat.read_bytes()),'entryBytesEqualCompletedHandoff':True},'npc':{'path':str(npc),'sha256':sha(npc.read_bytes()),'unchanged':True},'npcContent':{'commit':'695612139ff1f78b74ed1c375b74591a15969455','path':str(aff/'npc-content'),'files':content,'unchanged':True},'tests':checks,'linkage':linkage,'deployed':False,'pushed':False,'serverOrGameStarted':False,'realGameQA':'NOT_RUN','excludedRecoveryPlugin':True,'rawJarsAreNotDistributionArtifacts':True,'buildAttempts':[json.loads(p.read_text()) for p in sorted(o.glob('*-guard.json'))],'publicReleaseProvenance':m['publicReleaseProvenance'],'serverApprovedExistingFamiliesBaselineByteEquality':True}
(o/'final-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({'status':report['status'],'commit':report['sourceCommit'],'ui':{k:ui[k] for k in ['path','sha256','bytes','preservedEntries']},'bridges':{s:{k:v[k] for k in ['path','sha256','bytes','preservedEntries']} for s,v in bridges.items()},'chat':report['chat'],'tests':checks,'linkage':linkage},indent=2))
