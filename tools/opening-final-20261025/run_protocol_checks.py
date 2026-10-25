from pathlib import Path
import subprocess
o=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools')
s=(o/'ProtocolApiEquivalent.java').read_text()
s=s.replace('public final class ProtocolApiEquivalent','public final class ProtocolJarEquivalent').replace('public static void main(String[]a)throws Exception{','static void compare(byte[] left,byte[] right)throws Exception{').replace('Files.readAllBytes(Path.of(a[0]))','left').replace('Files.readAllBytes(Path.of(a[1]))','right')
pos=s.rfind('}')
s=s[:pos]+"""public static void main(String[]a)throws Exception{try(var l=new java.util.zip.ZipFile(a[0]);var r=new java.util.zip.ZipFile(a[1])){int count=0;for(String prefix:java.util.Arrays.copyOfRange(a,2,a.length)){var names=new java.util.TreeSet<String>();for(var en:java.util.Collections.list(l.entries()))if(en.getName().startsWith(prefix)&&en.getName().endsWith(".class"))names.add(en.getName());if(names.isEmpty())throw new IllegalStateException("missing family "+prefix);for(var name:names){var e=r.getEntry(name);if(e==null)throw new IllegalStateException("missing counterpart "+name);compare(l.getInputStream(l.getEntry(name)).readAllBytes(),r.getInputStream(e).readAllBytes());count++;}}System.out.println("PASS protocol families all class methods equivalent count="+count);}}"""+s[pos:]
(o/'ProtocolJarEquivalent.java').write_text(s)
cp=(o/'asm-classpath.txt').read_text();java='C:/Program Files/Java/jdk-21.0.10/bin/java.exe'
subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/javac.exe','-cp',cp,'-d',str(o),str(o/'ProtocolJarEquivalent.java'),str(o/'HousingRankRoundTrip.java')],check=True)
ui=o/'new-16-magic-codex-fabric.jar';school=o/'new-10-magic-codex-paper.jar'
results=[]
for index in [10,11,12]:
 jar=o/('new-'+str(index)+'-magic-codex-paper.jar')
 q=subprocess.run([java,'-Xverify:all','-cp',cp,'ProtocolJarEquivalent',str(jar),str(ui),'school/magiccodex/protocol/HousingRankProtocol','school/magiccodex/protocol/JournalProtocol','school/magiccodex/protocol/TitleProtocol'],capture_output=True,text=True,encoding='utf-8',errors='replace')
 (o/('protocol-'+str(index)+'.log')).write_text(q.stdout+q.stderr);results.append({'check':'server-ui-equivalence','server':index,'exit':q.returncode,'summary':q.stdout.splitlines()[-1:]})
for label,server in [('bridge',school),('housing',o/'housing/housing-school-private.jar')]:
 q=subprocess.run([java,'-Xverify:all','-cp',cp,'HousingRankRoundTrip',str(server),str(ui)],capture_output=True,text=True,encoding='utf-8',errors='replace')
 (o/('housing-rank-'+label+'-roundtrip.log')).write_text(q.stdout+q.stderr);results.append({'check':'actual-housing-rank-roundtrip','server':label,'exit':q.returncode,'summary':q.stdout.splitlines()[-1:]})
import json
(o/'protocol-validation.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))
