from pathlib import Path
import subprocess
o=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools');t=o/'townmap'
p=t/'DumpCommands.java';p.write_text("""import java.nio.file.*;public class DumpCommands{public static void main(String[]a)throws Exception{var c=OpeningDelta.read(Files.readAllBytes(Path.of(a[0])),0);for(var m:c.methods)if(m.name.equals("commands"))System.out.print(OpeningDelta.norm(m));}}""")
cp=(o/'asm-classpath.txt').read_text();subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/javac.exe','-cp',cp,'-d',str(t),str(p)],check=True)
key='0e5dc3a98aca8b3d782b3ad538a82aeb9fa2645d84f3eb187a7db3d2a07face2'
q=subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/java.exe','-cp',str(t)+';'+cp,'DumpCommands',str(Path('C:/Chacademi/artifacts/opening-final-20261025-task3/townmap/candidates/cache')/(key+'.live'))],capture_output=True,text=True,encoding='utf-8',errors='replace');(t/'live-commands.txt').write_text(q.stdout,encoding='utf-8')
lines=q.stdout.splitlines()
for i,line in enumerate(lines):
 if 'ASTORE' in line or 'ChacaApi.branch' in line:print(chr(10).join(lines[max(0,i-4):i+6]))
