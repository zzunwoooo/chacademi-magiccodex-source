from pathlib import Path
import zipfile,json,hashlib,subprocess,os,re,shutil,xml.etree.ElementTree as ET
r=Path('C:/Chacademi/staging/quest-guide-20261024-task9');o=Path('C:/Chacademi/artifacts/final-release-20261024-task9');t=r/'tools/final-release-task9';oldroot=Path('C:/Chacademi/staging/shops-notifications-20261024-task9');oldout=Path('C:/Chacademi/artifacts/shops-notifications-20261024-task9');sha=lambda b:hashlib.sha256(b).hexdigest();java=Path('C:/Program Files/Java/jdk-21.0.10/bin')
def jar(root,module):
 a=[p for p in (root/module/'build/libs').glob('*.jar') if not any(x in p.name for x in ['-sources','-dev','-javadoc'])];assert len(a)==1,a;return a[0]
deps=[next(Path('C:/Chacademi/tools/gradle-cache/caches/modules-2/files-2.1/org.ow2.asm',n,'9.7.1').glob('*/*.jar')) for n in ['asm','asm-tree','asm-util','asm-analysis']];cp=os.pathsep.join([str(t),*map(str,deps)])
checker=t/'AuditFinalLinkage.java';s=(r/'tools/task9-integration/AuditAffinityLinkage.java').read_text(encoding='utf-8').replace('public class AuditAffinityLinkage','public class AuditFinalLinkage').replace('return s.startsWith("school/magiccodex/")||s.startsWith("kr/chacademy/npc/");','return s.startsWith("school/magiccodex/")||s.startsWith("kr/chacademy/npc/")||s.startsWith("kr/chacademy/story")||s.startsWith("school/chacademia/magicmythic/");');checker.write_text(s,encoding='utf-8')
subprocess.run([str(java/'javac.exe'),'-J-Xmx256m','-cp',cp,str(t/'FinalClassPatch.java'),str(checker)],check=True)
def patch(mode,name,base,new,old=None):
 tag=mode+'-'+base.stem;before=o/(tag+'-before.class');after=o/(tag+'-after.class');raw=o/(tag+'-raw.class')
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(new) as n:before.write_bytes(b.read(name));raw.write_bytes(n.read(name))
 args=[str(java/'java.exe'),'-Xmx256m','-cp',cp,'FinalClassPatch',mode,str(before),str(raw),str(after)]
 if old:
  p=o/(tag+'-old-raw.class')
  with zipfile.ZipFile(old) as z:p.write_bytes(z.read(name))
  args.append(str(p))
 p=subprocess.run(args,capture_output=True);(o/(tag+'-method-audit.log')).write_bytes(p.stdout+p.stderr);assert p.returncode==0,p.stdout.decode('utf-8','replace')+p.stderr.decode('utf-8','replace');return after.read_bytes()
def pack(base,name,replace,metadata=None):
 out=o/name;allowed=set(replace)
 with zipfile.ZipFile(base) as b:
  if metadata:
   key,ver=metadata
   if key.endswith('.json'):v=json.loads(b.read(key));v['version']=ver;replace[key]=json.dumps(v,indent=2).encode()
   else:
    v=re.sub(rb'(?m)^version:.*$',('version: '+ver).encode(),b.read(key))
    if key=='plugin.yml' and 'bridge' in name and b'ItemsAdder' not in v:v=v.replace(b'softdepend: [',b'softdepend: [ItemsAdder, ',1)
    replace[key]=v
   allowed.add(key)
  with zipfile.ZipFile(out,'w') as z:
   for info in b.infolist():z.writestr(info,replace.pop(info.filename,b.read(info.filename)))
   for k,v in replace.items():z.writestr(k,v,compress_type=zipfile.ZIP_DEFLATED)
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(out) as z:
  assert z.testzip() is None;old=set(b.namelist());assert old<=set(z.namelist());changed=sorted(k for k in z.namelist() if k not in old or z.read(k)!=b.read(k));assert set(changed)<=allowed
 return {'path':str(out),'sha256':sha(out.read_bytes()),'bytes':out.stat().st_size,'baseline':str(base),'baselineSHA256':sha(base.read_bytes()),'changedEntries':changed,'preservedEntries':len(old-set(changed)),'removedEntries':[]}
def select(raw,families,prefix=''):
 with zipfile.ZipFile(raw) as z:return {k:z.read(k) for k in z.namelist() if k.endswith('.class') and (not prefix or k.startswith(prefix)) and k.rsplit('/',1)[-1].split('$')[0].removesuffix('.class') in families}
old=json.loads((oldout/'final-verification.json').read_text(encoding='utf-8'));result={}
ui=Path(old['ui']['path']);replace={}
for name in ['132_guiding_star.yml','known-hashes.tsv']:replace['assets/magiccodex/default-spells/'+name]=(r/'magic-codex-fabric/src/main/resources/assets/magiccodex/default-spells'/name).read_bytes()
result['ui']=pack(ui,'magic-codex-ui-final-20261024+mc1.21.4.jar',replace,('fabric.mod.json','0.40.4-final.20261024+mc1.21.4'))
# Core consumers: refuse to replace a class if its old raw source differs from the bounded integrated base.
raw=jar(r,'magic-codex-paper');oldraw=jar(oldroot,'magic-codex-paper');families=['AppraisalBridge','EnhancementBridge','MailboxBridge','QuestBridge','ReconfigurationBridge','ShopBridge','CoreVisualBridge'];result['bridges']={}
for area,info in old['bridges'].items():
 base=Path(info['path']);replace=select(raw,families,'school/magiccodex/paper/')
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(oldraw) as prev:
  for k in list(replace):
   if k in b.namelist() and k in prev.namelist():replace[k]=patch('delta',k,base,raw,oldraw)
 c='school/magiccodex/paper/MagicCodexBridge.class';replace[c]=patch('core',c,base,raw)
 result['bridges'][area]=pack(base,'magic-codex-bridge-final-20261024-'+area+'.jar',replace,('plugin.yml','0.31.5-final.20261024'))
# Story and mythic retain actual deployed methods outside the approved guide changes.
raw=jar(r,'magic-mythic-paper');oldraw=jar(oldroot,'magic-mythic-paper');result['mythic']={}
for area in ['school','wild','arena']:
 base=next(Path('C:/Chacademi/network',area,'plugins').glob('chacademia-magic-mythic-*.jar'));replace=select(raw,['StoryLink']);name='school/chacademia/magicmythic/UtilMech.class';replace[name]=patch('util',name,base,raw,oldraw);result['mythic'][area]=pack(base,'chacademia-magic-mythic-final-20261024-'+area+'.jar',replace,('plugin.yml','0.2.3-guide.20261024'))
base=Path('C:/Chacademi/artifacts/integrated-release-20261023-task7/chacademy-story-0.1.4-quest.5-ch1.1-serverdialogue-hud.1.jar');raw=jar(r,'chacademy-story/mod');oldraw=jar(Path('C:/Chacademi/staging/story-dialogue-paths-20261023-task7'),'chacademy-story/mod');replace=select(raw,['QuestGuidePolicy']);prefix='kr/chacademy/story/quest/'
for mode,cls in [('client','QuestClient'),('world','QuestWorld'),('packets','QuestPackets')]:replace[prefix+cls+'.class']=patch(mode,prefix+cls+'.class',base,raw,oldraw if mode in ['client','world'] else None)
with zipfile.ZipFile(raw) as z:
 for k in z.namelist():
  if k.startswith(prefix+'QuestPackets$GuideS2C') and k.endswith('.class'):replace[k]=z.read(k)
 key='assets/chaca_story/textures/quest/guide_arrow.png';replace[key]=z.read(key)
result['storyClient']=pack(base,'chacademy-story-final-guide-20261024.jar',replace,('fabric.mod.json','0.1.5-guide.20261024'))
base=next(Path('C:/Chacademi/network/school/plugins').glob('chacademy-story-plugin-*.jar'));raw=jar(r,'chacademy-story/plugin');oldraw=jar(Path('C:/Chacademi/staging/story-dialogue-paths-20261023-task7'),'chacademy-story/plugin');replace={};prefix='kr/chacademy/storyplugin/'
for mode,cls in [('engine','QuestEngine'),('codec','QuestCodec'),('plugin','StoryPlugin')]:replace[prefix+cls+'.class']=patch(mode,prefix+cls+'.class',base,raw,oldraw if mode=='engine' else None)
result['storyServer']=pack(base,'chacademy-story-plugin-final-guide-20261024.jar',replace,('plugin.yml','0.1.5-guide.20261024'))
# The NPC pair is an independently audited bounded artifact, never raw build/libs.
npcdir=Path('C:/Chacademi/artifacts/npc-v2-build-20261024-task13');result['npc']={}
for role,n,h in [('client','chacademi-npc-model-0.1.1-npcmodel.4.jar','d24c5fb68ad0f98579b7e07c0914a524e4b0d725ff6fbdbce901a02251a166c9'),('server','chaca-npc-0.3.0-npcmodel.3.jar','8b83629b167dfe5d4222367d362cb3837b4f2ca3e5baca079dcb74e3631445ee')]:
 p=npcdir/n;assert sha(p.read_bytes())==h;result['npc'][role]={'path':str(p),'sha256':h,'bytes':p.stat().st_size}
result['chat']=old['chat'];assert sha(Path(result['chat']['path']).read_bytes())==result['chat']['sha256']
checks={}
for label,info in [('ui',result['ui']),('storyClient',result['storyClient']),('storyServer',result['storyServer']),*[(k+'Bridge',v) for k,v in result['bridges'].items()],*[(k+'Mythic',v) for k,v in result['mythic'].items()]]:
 extra=[result['npc']['server']['path']] if label=='storyServer' else []
 p=subprocess.run([str(java/'java.exe'),'-Xmx384m','-cp',cp,'AuditFinalLinkage',info['path'],*extra,info['baseline']],capture_output=True);(o/('linkage-'+label+'.log')).write_bytes(p.stdout+p.stderr);checks[label]=p.returncode;assert p.returncode==0,p.stdout.decode('utf-8','replace')+p.stderr.decode('utf-8','replace')
result.update(status='ASSEMBLED_STATIC_VERIFIED_RESOURCES_PENDING',sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=r,text=True).strip(),linkage=checks,modelFilesChanged=False,recoveryPluginIncluded=False,deployed=False)
(o/'release-candidates.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in result.items() if k not in ['linkage']},indent=2))
