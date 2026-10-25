from pathlib import Path
import subprocess
o=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools')
cp=(o/'asm-classpath.txt').read_text()
q=subprocess.run(['C:/Program Files/Java/jdk-21.0.10/bin/javac.exe','-cp',cp,'-d',str(o),str(o/'OpeningSpecial.java'),str(o/'OpeningFinalAlign.java'),str(o/'OpeningDelta.java')],capture_output=True,text=True);(o/'final-special-compile.log').write_text(q.stdout+q.stderr);assert q.returncode==0,q.stderr
p=o/'bounded_package.py';s=p.read_text()
s=s.replace("'OpeningDelta',str(before),str(after),str(base),str(out)","'OpeningFinalAlign',str(before),str(after),str(base),str(base)+'.aligned',str(after)+'.aligned',str(out),cp")
p.write_text(s,encoding='utf-8')
s=(o/'prepare_candidates.py').read_text()
s=s.replace("if s['status']!='AUDIT_ONLY_NOT_CANDIDATE':continue","if s['status']!='AUDIT_ONLY_NOT_CANDIDATE' or s['module'] not in ['magic-codex-paper','portable-vfx-runtime/client']:continue")
s=s.replace("'candidate-preparation-results.json'","'bridge-vfx-preparation-results.json'")
(o/'prepare_bridge_vfx.py').write_text(s,encoding='utf-8')
print('compiled / wrapper ready')
