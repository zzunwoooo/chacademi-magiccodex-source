from pathlib import Path
import zipfile,json,hashlib,subprocess,copy,re
r=Path('C:/Chacademi/artifacts/preset-off-20261024-task9');w=Path('C:/Chacademi/staging/preset-off-20261024-task9');java='C:/Program Files/Java/jdk-21.0.10/bin/';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
deps=[next(Path('C:/Chacademi/tools/gradle-cache/caches/modules-2/files-2.1/org.ow2.asm',n,'9.7.1').glob('*/*.jar')) for n in ['asm','asm-tree','asm-util','asm-analysis']];cp=';'.join([str(r),*map(str,deps)])
v=subprocess.run([java+'javac.exe','-J-Xmx128m','-cp',cp,str(r/'PresetClassPatch.java'),'-d',str(r),'C:/Chacademi/staging/quest-guide-20261024-task9/tools/final-release-task9/AuditFinalLinkage.java'],capture_output=True,text=True);assert v.returncode==0,v.stderr
jars=lambda p:next(x for x in p.glob('*.jar') if '-sources' not in x.name and '-dev' not in x.name)
uiRaw=jars(w/'magic-codex-fabric/build/libs');oldRaw=jars(Path('C:/Chacademi/staging/hud-reset-defaults-20261024-task9/magic-codex-fabric/build/libs'));bridgeRaw=jars(w/'magic-codex-paper/build/libs')
def patch(base,raw,entry,label,baselineJar=None,baselineClass=None):
 before=r/(label+'-before.class');after=r/(label+'-after.class');new=r/(label+'-raw.class');old=r/(label+'-baseline.class')
 with zipfile.ZipFile(base) as z:before.write_bytes(z.read(entry))
 with zipfile.ZipFile(raw) as z:new.write_bytes(z.read(entry))
 if baselineJar:
  with zipfile.ZipFile(baselineJar) as z:old.write_bytes(z.read(entry))
 else:old.write_bytes(Path(baselineClass).read_bytes())
 v=subprocess.run([java+'java.exe','-Xmx128m','-cp',cp,'PresetClassPatch','delta',str(before),str(new),str(after),str(old)],capture_output=True,text=True);(r/(label+'-preservation.log')).write_text(v.stdout+v.stderr,encoding='utf-8');assert v.returncode==0,v.stdout+v.stderr;print(v.stdout.strip(),flush=True);return after.read_bytes()
def pack(base,name,changes,version):
 bz=zipfile.ZipFile(base);allowed=set(changes);meta='fabric.mod.json' if 'ui-' in name else 'plugin.yml'
 if meta.endswith('.json'):v=json.loads(bz.read(meta));v['version']=version;changes[meta]=json.dumps(v,indent=2).encode()
 else:changes[meta]=re.sub(rb'(?m)^version:.*$',('version: '+version).encode(),bz.read(meta))
 allowed.add(meta);out=r/name
 with zipfile.ZipFile(out,'w') as z:
  for i in bz.infolist():z.writestr(copy.copy(i),changes.pop(i.filename,bz.read(i.filename)))
  for n,b in changes.items():z.writestr(n,b,compress_type=zipfile.ZIP_DEFLATED)
 with zipfile.ZipFile(out) as z:
  assert z.testzip() is None;assert set(bz.namelist())<=set(z.namelist());changed=[n for n in z.namelist() if n not in bz.namelist() or z.read(n)!=bz.read(n)];assert set(changed)<=allowed
 v=subprocess.run([java+'java.exe','-Xmx128m','-cp',cp,'AuditFinalLinkage',str(out),str(base)],capture_output=True,text=True);(r/(name+'.linkage.log')).write_text(v.stdout+v.stderr,encoding='utf-8');assert v.returncode==0,v.stdout+v.stderr
 return {'path':str(out),'sha256':sha(out),'bytes':out.stat().st_size,'baseline':str(base),'baselineSHA256':sha(base),'changedEntries':changed,'removedEntries':[],'linkage':'PASS'}
base=Path('C:/Chacademi/artifacts/hud-reset-defaults-20261024-task9/magic-codex-ui-hudreset-20261024+mc1.21.4.jar');changes={}
for cl in ['CastingClient','CastingState','GeneralPresetClient','KeySettingsScreen','NormalSpellPresets','SkillHudRenderer']:
 key='school/magiccodex/client/'+cl+'.class';changes[key]=patch(base,uiRaw,key,cl,baselineJar=oldRaw)
with zipfile.ZipFile(uiRaw) as z:
 for n in z.namelist():
  if n.endswith('.class') and (n.startswith('school/magiccodex/protocol/GeneralPresetModeProtocol') or n=='school/magiccodex/client/NormalPresetMode.class' or n.startswith('school/magiccodex/client/GeneralPresetClient$Mode')):changes[n]=z.read(n)
result={'status':'CANDIDATES_ONLY_NOT_DEPLOYED','ui':pack(base,'magic-codex-ui-presetoff-candidate-20261024+mc1.21.4.jar',changes,'0.40.5-presetoff.20261024+mc1.21.4'),'bridges':{}}
for area in ['school','wild','arena']:
 base=Path('C:/Chacademi/artifacts/final-release-20261024-task9')/('magic-codex-bridge-final-20261024-'+area+'.jar');key='school/magiccodex/paper/GeneralPresetBridge.class';changes={key:patch(base,bridgeRaw,key,'Bridge-'+area,baselineClass=r/'baseline-classes'/key)}
 with zipfile.ZipFile(bridgeRaw) as z:
  for n in z.namelist():
   if n.endswith('.class') and (n.startswith('school/magiccodex/protocol/GeneralPresetModeProtocol') or n=='school/magiccodex/paper/GeneralPresetModePolicy.class'):changes[n]=z.read(n)
 result['bridges'][area]=pack(base,'magic-codex-bridge-presetoff-candidate-20261024-'+area+'.jar',changes,'0.31.6-presetoff.20261024')
result.update(serverChanged=False,personalChanged=False,published=False,requiresMatchingServerV2=True,legacyV1Unchanged=True,travelIncluded=False);(r/'candidate-set.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print('CANDIDATE_SET_READY',flush=True)
