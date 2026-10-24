import pathlib,zipfile,json,subprocess,struct,re
OUT=pathlib.Path(r'C:\Chacademi\artifacts\book-client-startupfix-20261023')
r=json.loads((OUT/'candidate-verification.json').read_text());target=pathlib.Path(r['file'])
def pool(data):
 n=struct.unpack_from('>H',data,8)[0];p=10;cp=[None]*n;i=1
 while i<n:
  t=data[p];p+=1
  if t==1:
   size=struct.unpack_from('>H',data,p)[0];p+=2;cp[i]=(t,data[p:p+size].decode('utf-8','replace'));p+=size
  elif t in (7,8,16,19,20):cp[i]=(t,struct.unpack_from('>H',data,p)[0]);p+=2
  elif t in (9,10,11,12,17,18):cp[i]=(t,*struct.unpack_from('>HH',data,p));p+=4
  elif t in (3,4):p+=4
  elif t in (5,6):p+=8;i+=1
  elif t==15:p+=3
  else:raise ValueError(t)
  i+=1
 return cp
missing=[]
with zipfile.ZipFile(target) as z:
 names=set(z.namelist())
 for name in names:
  if not name.endswith('.class'):continue
  cp=pool(z.read(name))
  for item in cp:
   if item and item[0]==7:
    ref=cp[item[1]][1]
    if ref.startswith('school/magiccodex/') and ref+'.class' not in names:missing.append([name,ref])
assert not missing,missing
r['missingOwnClassReferences']=missing
JAVA=r'C:\Program Files\Java\jdk-21.0.10\bin\javap.exe'
def calls(path):
 txt=subprocess.check_output([JAVA,'-classpath',str(path),'-c','-p','school.magiccodex.client.MagicCodexClient'],text=True,encoding='utf-8',errors='replace')
 (OUT/(path.stem+'.javap.txt')).write_text(txt,encoding='utf-8',errors='replace')
 method=txt.split('public void onInitializeClient();',1)[1].split('\n  private ',1)[0]
 return re.findall(r'// Method ([\w/$]+\.initialize:\(\)V)',method)
base=next(pathlib.Path(r'C:\Chacademi\artifacts\integrated-release-20261023-task7').glob('magic-codex-ui*.jar'))
source=next(pathlib.Path(r'C:\Chacademi\artifacts\launcher-0.1.25-final-20261023').glob('magic*.jar'))
b=calls(base);c=calls(target);s=calls(source)
assert [v for v in c if 'BookClient.' not in v]==b,(b,c)
assert [v for v in s if 'CastAimClient.' not in v]==c
assert sum('BookClient.' in v for v in c)==1
r['baselineInitializeCallsPreserved']=len(b);r['bookInitializeCalls']=1;r['removedInitializeCalls']=['CastAimClient.initialize:()V']
(OUT/'candidate-verification.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps(r))

classes=[v.split('.initialize:')[0].replace('/','.') for v in c]
classes=[v if '.' in v else 'school.magiccodex.client.'+v for v in classes]
linked=subprocess.check_output([JAVA,'-classpath',str(target),'-p',*classes],text=True,encoding='utf-8',errors='replace')
for owner in classes:
 section=linked.split(owner+' {',1)[1].split('}',1)[0]
 assert 'static void initialize();' in section,owner
r['actualJarEntrypointInitializeMethodsLinked']=len(classes)
(OUT/'entrypoint-initialize-linkage.txt').write_text(linked,encoding='utf-8')
(OUT/'candidate-verification.json').write_text(json.dumps(r,indent=2)+'\n')
print('ACTUAL_JAR_INITIALIZE_METHOD_LINKAGE_OK',len(classes))
