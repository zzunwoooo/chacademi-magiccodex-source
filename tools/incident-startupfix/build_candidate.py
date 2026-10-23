import pathlib,zipfile,json,subprocess,hashlib
ROOT=pathlib.Path(__file__).resolve().parent
OUT=pathlib.Path(r'C:\Chacademi\artifacts\book-client-startupfix-20261023');OUT.mkdir(exist_ok=True)
SRC=next(pathlib.Path(r'C:\Chacademi\artifacts\launcher-0.1.25-final-20261023').glob('magic*.jar'))
TARGET=OUT/SRC.name.replace('book.1+','book.1-startupfix.1+')
JAVA=pathlib.Path(r'C:\Program Files\Java\jdk-21.0.10\bin')
ENTRY='school/magiccodex/client/MagicCodexClient.class'
assert hashlib.sha256(SRC.read_bytes()).hexdigest()=='fc09cc5ef816171cceca9f3d3225e82d5a07ace23abb628ac47e34980e16f8ce'
with zipfile.ZipFile(SRC) as z:(OUT/'before.class').write_bytes(z.read(ENTRY))
subprocess.run([str(JAVA/'javac.exe'),'--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',str(ROOT/'RemoveAccidentalInit.java')],check=True)
subprocess.run([str(JAVA/'java.exe'),'--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','-cp',str(ROOT),'RemoveAccidentalInit',str(OUT/'before.class'),str(OUT/'after.class')],check=True)
with zipfile.ZipFile(SRC) as z,zipfile.ZipFile(TARGET,'w') as dst:
 for info in z.infolist():
  data=z.read(info.filename)
  if info.filename==ENTRY:data=(OUT/'after.class').read_bytes()
  if info.filename=='fabric.mod.json':
   meta=json.loads(data);meta['version']=meta['version'].replace('book.1+','book.1-startupfix.1+');data=(json.dumps(meta,ensure_ascii=True,indent=2)+'\n').encode()
  dst.writestr(info,data)
report={'file':str(TARGET),'sha256':hashlib.sha256(TARGET.read_bytes()).hexdigest(),'size':TARGET.stat().st_size}
with zipfile.ZipFile(SRC) as a,zipfile.ZipFile(TARGET) as b:
 changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
 assert changed==['fabric.mod.json',ENTRY],changed
 report['changedEntries']=changed
 assert a.namelist()==b.namelist()
(OUT/'candidate-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
