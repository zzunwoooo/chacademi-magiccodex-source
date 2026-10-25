from pathlib import Path
import subprocess,zipfile,json
o=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools')
p=o/'HousingRankRoundTrip.java';s=p.read_text().replace('if(c(to,"").getMethod("toServer",byte[].class).invoke(null,(Object)invalid)!=null)throw new AssertionError("invalid accepted");','boolean rejected=false;try{Object v=c(to,"").getMethod("toServer",byte[].class).invoke(null,(Object)invalid);rejected=v==null;}catch(InvocationTargetException ex){if(ex.getCause() instanceof IllegalArgumentException)rejected=true;else throw ex;}if(!rejected)throw new AssertionError("invalid accepted");');p.write_text(s)
jar=o/'new-15-portable-vfx-runtime-client.jar'
with zipfile.ZipFile(jar) as z:
 nested='META-INF/jars/common-3.2.0-catalog.alpha.1.jar'
 dest=o/'vfx-nested-common.jar';dest.write_bytes(z.read(nested))
deps=Path('C:/Chacademi/artifacts/opening-prep-safe-integration-20261025-task3/bounded-packaging/ui-final-runtime-dependencies.txt').read_text()
(o/'vfx-runtime-dependencies.txt').write_text(str(dest)+';'+deps)
cp=(o/'asm-classpath.txt').read_text()
q=subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/java.exe','-Xverify:all','-cp',cp,'FinalJvmVerify',str(jar),str(o/'new-15-portable-vfx-runtime-client-jvm-classes.txt'),str(o/'vfx-runtime-dependencies.txt')],capture_output=True,text=True,encoding='utf-8',errors='replace')
(o/'vfx-jvm-final.log').write_text(q.stdout+q.stderr);print('VFX actual nestedcommon JVM',q.returncode,q.stdout.splitlines()[-1:])
