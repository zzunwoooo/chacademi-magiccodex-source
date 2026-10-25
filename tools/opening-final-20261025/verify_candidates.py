from pathlib import Path
import json,shutil,subprocess,zipfile
o=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools')
old=Path('C:/Chacademi/staging/opening-final-20261025-task3/tools/live-handoff-20261025/bounded-packaging')
for n in ['FinalJvmVerify.java','ProtocolApiEquivalent.java']:shutil.copyfile(old/n,o/n)
cp=(o/'asm-classpath.txt').read_text();subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/javac.exe','-cp',cp,'-d',str(o),str(o/'FinalJvmVerify.java'),str(o/'ProtocolApiEquivalent.java')],check=True)
deps=o/'housing/dependencies.txt';results=[]
for i in [10,11,12,16,15]:
 jars=list(o.glob('new-'+str(i)+'-*.jar'))
 if not jars:continue
 jar=jars[0];pres=json.loads(Path(str(jar)+'.preservation.json').read_text())
 names=[n for n in pres['changedEntries'] if n.endswith('.class')];lst=o/(jar.stem+'-jvm-classes.txt');lst.write_text(chr(10).join(names))
 dep=deps
 if i in [15,16]:dep=Path('C:/Chacademi/artifacts/opening-prep-safe-integration-20261025-task3/bounded-packaging/ui-final-runtime-dependencies.txt')
 q=subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/java.exe','-Xverify:all','-cp',cp,'FinalJvmVerify',str(jar),str(lst),str(dep)],capture_output=True,text=True,encoding='utf-8',errors='replace');log=o/(jar.stem+'-jvm.log');log.write_text(q.stdout+q.stderr,encoding='utf-8');results.append({'jar':str(jar),'sha256':pres['sha256'],'exit':q.returncode,'summary':q.stdout.splitlines()[-1:]})
(o/'final-jvm-results.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))
