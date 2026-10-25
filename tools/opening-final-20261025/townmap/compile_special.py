from pathlib import Path
import subprocess,shutil
O=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools');T=O/'townmap';C=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/townmap/candidates')
p=T/'OpeningSpecial.java';s=p.read_text(encoding='utf-8').replace('if(!(current instanceof VarInsnNode v)||v.var!=1)','if(((VarInsnNode)current).var!=1)').replace('if(i instanceof VarInsnNode v&&v.getOpcode()==ASTORE&&v.var==1)','if(i instanceof VarInsnNode store&&store.getOpcode()==ASTORE&&store.var==1)');p.write_text(s,encoding='utf-8')
cp=str(T)+';'+(O/'asm-classpath.txt').read_text()
q=subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/javac.exe','-cp',cp,'-d',str(T)]+[str(T/n) for n in ['OpeningDelta.java','OpeningSpecial.java','OpeningFinalAlign.java']],capture_output=True,text=True);(T/'special-compile.log').write_text(q.stdout+q.stderr);assert not q.returncode,q.stderr
for n in ['OpeningDelta.class','OpeningSpecial.class']:shutil.copyfile(T/n,C/n)
(T/'asm-classpath.txt').write_text(cp)
p=T/'package_townmap.py';s=p.read_text(encoding='utf-8').replace("b.cp=(O/'asm-classpath.txt').read_text()","b.cp=(T/'asm-classpath.txt').read_text()");p.write_text(s,encoding='utf-8')
print('compiled exact map-only special')
