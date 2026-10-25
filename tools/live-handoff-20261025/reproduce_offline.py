import argparse,sys,json,hashlib,os
from pathlib import Path
def main():
 p=argparse.ArgumentParser(description='Offline bounded packaging; no deploy or process operations')
 p.add_argument('--spec',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True);p.add_argument('--java',required=True);p.add_argument('--asm-classpath',required=True);p.add_argument('--check-only',action='store_true')
 a=p.parse_args();s=json.loads(a.spec.read_text(encoding='utf-8'));out=a.output_dir.resolve()
 if any(('network'==part.lower() or 'launcher' in part.lower()) for part in out.parts):raise ValueError('operating network/launcher output forbidden')
 if not a.check_only and (Path(s['outputName']).name!=s['outputName'] or s['outputName'] in ['.','..']):raise ValueError('outputName must be a plain filename')
 for role in ['live','newRaw','sourceBaselineRaw']:
  path=Path(s[role]).resolve()
  if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=s[role+'SHA256']:raise ValueError('input SHA gate '+role)
  if path==out or out in path.parents:raise ValueError('inputs must be outside output directory')
 if a.check_only:print('PASS input SHA gates; no writes');return
 out.mkdir(parents=True,exist_ok=True);sys.path.insert(0,str(Path(__file__).parent/'bounded-packaging'))
 import bounded_package as b
 b.o=out;b.java=a.java;b.cp=a.asm_classpath;os.environ['OPENING_SERVER_AREA']=s.get('area','')
 report,changes=b.class_delta(s['live'],s['newRaw'],s['sourceBaselineRaw'],s['prefixes'],s['label'])
 for entry,path in s.get('extraClasses',{}).items():changes[entry]=Path(path).read_bytes()
 b.resource_delta(s['live'],s['newRaw'],s['sourceBaselineRaw'],s.get('resources',[]),changes,report)
 for entry,path in s.get('canonicalClasses',{}).items():changes[entry]=Path(path).read_bytes()
 b.pack(s['live'],out/s['outputName'],changes,report,s['version'],s.get('kind','bukkit'))
 print('PASS offline candidate')
if __name__=='__main__':main()
