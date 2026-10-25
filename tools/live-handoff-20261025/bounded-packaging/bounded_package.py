from pathlib import Path
import zipfile,json,hashlib,subprocess,copy,re,sys,os
sys.path.insert(0,'C:/Chacademi/tools/review-yaml');import yaml
from resource_merge import ABSENT,merge_value,metadata_union
o=Path(__file__).parent;cp=(o/'asm-classpath.txt').read_text();java='C:/Program Files/Java/jdk-21.0.10/bin/java.exe';sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
def entries(z,prefix):return [p for p in z.namelist() if p==prefix+'.class' or p.startswith(prefix+'$') and p.endswith('.class')]
def class_delta(live,new,old,prefixes,label):
 live,new,old=map(Path,[live,new,old]);folder=o/label;folder.mkdir(exist_ok=True);cache=o/'cache';cache.mkdir(exist_ok=True);changes={};actions=[];conflicts=[]
 with zipfile.ZipFile(live) as lz,zipfile.ZipFile(new) as nz,zipfile.ZipFile(old) as bz:
  for pref in sorted(set(prefixes)):
   for name in entries(nz,pref):
    if name not in lz.namelist():
     if name in bz.namelist():
      nb,ob=nz.read(name),bz.read(name)
      same=nb==ob
      if not same:
       ek=hashlib.sha256(nb+ob).hexdigest();en=cache/(ek+'.equiv.new');eo=cache/(ek+'.equiv.old');en.write_bytes(nb);eo.write_bytes(ob);ev=subprocess.run([java,'-Xmx128m','-cp',cp,'OpeningClassEquivalent',str(en),str(eo)],capture_output=True,text=True);same=ev.returncode==0
      if same:actions.append({'entry':name,'action':'unchanged-source-class-not-installed-preserved-absent'});continue
     changes[name]=nz.read(name);actions.append({'entry':name,'action':'new-class'});continue
    if name not in bz.namelist():conflicts.append({'entry':name,'error':'existing-live-class-without-source-baseline'});continue
    lb,nb,ob=lz.read(name),nz.read(name),bz.read(name)
    if nb==ob:actions.append({'entry':name,'action':'source-byte-identical-live-preserved'});continue
    key=hashlib.sha256(lb+nb+ob+(o/'OpeningDelta.class').read_bytes()+(o/'OpeningSpecial.class').read_bytes()+os.environ.get('OPENING_SERVER_AREA','').encode('ascii')).hexdigest();before=cache/(key+'.live');after=cache/(key+'.new');base=cache/(key+'.base');out=cache/(key+'.out');log=cache/(key+'.log')
    if not out.exists() or not log.exists() or not any(line.startswith('PASS ') for line in log.read_text(encoding='utf-8').splitlines()):
     before.write_bytes(lb);after.write_bytes(nb);base.write_bytes(ob);v=subprocess.run([java,'-Xmx128m','-cp',cp,'OpeningDelta',str(before),str(after),str(base),str(out)],capture_output=True,text=True,encoding='utf-8',errors='replace');log.write_text(v.stdout+v.stderr,encoding='utf-8')
     if v.returncode:conflicts.append({'entry':name,'error':v.stdout+v.stderr,'cacheKey':key});continue
    result=out.read_bytes()
    if result!=lb:changes[name]=result
    actions.append({'entry':name,'action':'bounded-method-field-delta','result':log.read_text(encoding='utf-8').strip(),'cacheKey':key})
 report={'label':label,'live':str(live),'liveSHA256':sha(live),'newRaw':str(new),'newRawSHA256':sha(new),'sourceBaselineRaw':str(old),'sourceBaselineRawSHA256':sha(old),'classActions':actions,'conflicts':conflicts,'bundleReady':False}
 (o/(label+'-probe.json')).write_text(json.dumps(report,indent=2));return report,changes

def resource_delta(live,new,old,names,changes,report):
 with zipfile.ZipFile(live) as lz,zipfile.ZipFile(new) as nz,zipfile.ZipFile(old) as bz:
  for name in names:
   if name not in nz.namelist():continue # preserve original resources, no deletion
   nb=nz.read(name);ob=bz.read(name) if name in bz.namelist() else None;lb=lz.read(name) if name in lz.namelist() else None
   if nb==ob:continue
   if lb is None or lb==ob or lb==nb:changes[name]=nb;continue
   try:
    if name.endswith(('.yml','.yaml')):
     value=merge_value(yaml.safe_load(lb),yaml.safe_load(ob) if ob else ABSENT,yaml.safe_load(nb),name);changes[name]=yaml.safe_dump(value,allow_unicode=True,sort_keys=False).encode('utf-8')
    elif name.endswith('.json'):
     if name.endswith(('refmap.json','mixins.json')):value=metadata_union(json.loads(lb),json.loads(nb),name)
     else:value=merge_value(json.loads(lb),json.loads(ob) if ob else ABSENT,json.loads(nb),name)
     changes[name]=json.dumps(value,ensure_ascii=False,indent=2).encode('utf-8')
    elif name.endswith('known-hashes.tsv'):
     ls=lb.decode('utf-8').splitlines();ls+= [x for x in nb.decode('utf-8').splitlines() if x not in ls];changes[name]=('\n'.join(ls)+'\n').encode()
    else:raise ValueError('Nonmergeable changed resource')
   except Exception as ex:report['conflicts'].append({'entry':name,'error':str(ex)})

def pack(live,out,changes,report,version,kind='bukkit'):
 if report['conflicts']:raise RuntimeError('Unresolved preservation conflicts: '+json.dumps(report['conflicts']))
 live,out=Path(live),Path(out);changes=dict(changes);allowed=set(changes);meta='fabric.mod.json' if kind=='fabric' else 'velocity-plugin.json' if kind=='velocity' else 'plugin.yml'
 with zipfile.ZipFile(live) as bz:
  content=changes.get(meta,bz.read(meta))
  if meta.endswith('.json'):obj=json.loads(content);obj['version']=version;changes[meta]=json.dumps(obj,ensure_ascii=False,indent=2).encode()
  else:changes[meta]=re.sub(rb'(?m)^version:.*$',('version: '+version).encode(),content)
  allowed.add(meta)
  with zipfile.ZipFile(out,'w') as z:
   for info in bz.infolist():z.writestr(copy.copy(info),changes.pop(info.filename,bz.read(info.filename)))
   for name,data in changes.items():z.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
  with zipfile.ZipFile(out) as z:
   assert z.testzip() is None;assert len(z.namelist())==len(set(z.namelist()));assert set(bz.namelist())<=set(z.namelist());changed=[name for name in z.namelist() if name not in bz.namelist() or z.read(name)!=bz.read(name)];assert set(changed)<=allowed;untouched=[name for name in bz.namelist() if name not in allowed];assert all(z.read(name)==bz.read(name) for name in untouched)
 v=subprocess.run([java,'-Xmx128m','-cp',cp,'AuditFinalLinkage',str(out),str(live)],capture_output=True,text=True,encoding='utf-8',errors='replace');Path(str(out)+'.linkage.log').write_text(v.stdout+v.stderr,encoding='utf-8')
 if v.returncode:raise RuntimeError('Linkage validation failed: '+v.stdout+v.stderr)
 result={'path':str(out),'sha256':sha(out),'bytes':out.stat().st_size,'baseline':str(live),'baselineSHA256':sha(live),'changedEntries':changed,'removedEntries':[],'untouchedByteIdentical':len(untouched),'linkage':'PASS','bundleReady':False,'deployment':'NOT_STARTED'};Path(str(out)+'.preservation.json').write_text(json.dumps(result,indent=2));return result
