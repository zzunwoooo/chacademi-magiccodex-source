from pathlib import Path
import json,subprocess,zipfile,hashlib,struct
A=Path('C:/Chacademi/artifacts/opening-final-20261025-task3');O=A/'candidate-tools';T=O/'townmap';C=A/'townmap/candidates';W=Path('C:/Chacademi/staging/opening-final-20261025-task3')
(T/'TownmapSmoke.java').write_text("""import java.util.*;import java.util.zip.*;import org.objectweb.asm.tree.*;public class TownmapSmoke{
 public static void main(String[]a)throws Exception{try(var z=new ZipFile(a[0])){boolean client=a[1].equals("client");String name=client?"school/magiccodex/client/TravelScreen.class":"school/magiccodex/paper/TravelBridge.class";var c=OpeningDelta.read(z.getInputStream(z.getEntry(name)).readAllBytes(),0);
 var expected=client?List.of("marker","hologram","sparkles","label"):List.of("fitCommands","fitView","fitAnchor","fitSecond","fitScale","fitRotate","fitShift","fitSave");
 for(String n:expected)if(c.methods.stream().noneMatch(m->m.name.equals(n)))throw new AssertionError("missing method "+n);
 if(!client){var command=c.methods.stream().filter(m->m.name.equals("commands")).findFirst().orElseThrow();int calls=0;for(var i:command.instructions)if(i instanceof MethodInsnNode m&&m.owner.equals(c.name)&&m.name.equals("fitCommands"))calls++;if(calls!=1)throw new AssertionError("fit command registration "+calls);
 if(z.getEntry("school/magiccodex/paper/TravelMapFit.class")==null||z.getEntry("school/magiccodex/paper/TravelMapFit$Fit.class")==null)throw new AssertionError("math classes missing");
 }else{var marker=c.methods.stream().filter(m->m.name.equals("marker")).findFirst().orElseThrow();var calls=new HashSet<String>();for(var i:marker.instructions)if(i instanceof MethodInsnNode m&&m.owner.equals(c.name))calls.add(m.name);if(!calls.containsAll(List.of("sparkles","hologram")))throw new AssertionError("new marker signals absent");}
 System.out.println("PASS actual townmap command/marker registration methods "+a[1]);}}
}""",encoding='utf-8')
cp=(T/'asm-classpath.txt').read_text();java='C:/Program Files/Java/jdk-21.0.10/bin/java.exe';subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/javac.exe','-cp',cp,'-d',str(T),str(T/'TownmapSmoke.java')],check=True)
results=[];entries=json.loads((T/'packaging-results.json').read_text())
for r in entries:
 if r['status']!='PRIVATE_PACKAGED':continue
 jar=Path(r['path']);p=json.loads(Path(str(jar)+'.preservation.json').read_text());assert hashlib.sha256(jar.read_bytes()).hexdigest()==r['sha256']
 base=Path(p['baseline']);names=[n for n in p['changedEntries'] if n.endswith('.class')];lst=T/(r['area']+'-classes.txt');lst.write_text(chr(10).join(names))
 dep=O/'housing/dependencies.txt' if r['area']!='client' else Path('C:/Chacademi/artifacts/opening-prep-safe-integration-20261025-task3/bounded-packaging/ui-final-runtime-dependencies.txt')
 q=subprocess.run([java,'-Xverify:all','-cp',cp,'FinalJvmVerify',str(jar),str(lst),str(dep)],capture_output=True,text=True,encoding='utf-8',errors='replace');(T/(r['area']+'-jvm.log')).write_text(q.stdout+q.stderr);assert q.returncode==0,q.stdout+q.stderr
 smoke=subprocess.run([java,'-cp',cp,'TownmapSmoke',str(jar),r['area']],capture_output=True,text=True,encoding='utf-8',errors='replace');(T/(r['area']+'-smoke.log')).write_text(smoke.stdout+smoke.stderr);assert not smoke.returncode,smoke.stdout+smoke.stderr
 with zipfile.ZipFile(base) as bz,zipfile.ZipFile(jar) as z:
  proto=[n for n in bz.namelist() if n.startswith('school/magiccodex/protocol/') and n.endswith('.class')]
  assert all(z.read(n)==bz.read(n) for n in proto)
  assets=[n for n in bz.namelist() if n.startswith('assets/')];assert all(z.read(n)==bz.read(n) for n in assets)
  image=None
  if r['area']=='client':
   n='assets/magiccodex/textures/gui/travel/maps/school.png';data=z.read(n);assert data==(W/'magic-codex-fabric/src/main/resources'/n).read_bytes();size=struct.unpack('>II',data[16:24]);assert size==(1479,1064);image=dict(width=size[0],height=size[1],sha256=hashlib.sha256(data).hexdigest(),legacyAssetsPreserved=len(assets))
 results.append(dict(r,JVM='PASS',JVMSummary=q.stdout.splitlines()[-1:],controlSignalSmoke='PASS',unchangedProtocolClasses=len(proto),removedEntries=p['removedEntries'],untouchedByteIdentical=p['untouchedByteIdentical'],image=image))
(T/'TOWNMAP-VALIDATION.json').write_text(json.dumps({'candidates':results,'arena':'UNCHANGED_TRAVEL_NOT_INSTALLED','sourceOnlyReproduction':False,'deployment':'NOT_STARTED'},indent=2));print(json.dumps(results))
