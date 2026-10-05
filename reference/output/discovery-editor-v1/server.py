"""Loopback-only editing drafts. Never writes the Minecraft installation or source YAML."""
from pathlib import Path
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
import argparse,copy,datetime,json,os,re,secrets,threading,time,urllib.parse

ROOT=Path(__file__).resolve().parent
CATALOG=json.loads((ROOT/'catalog.json').read_text(encoding='utf-8'))
IDS={s['id'] for s in CATALOG['spells']}
ICON_FILES={'/icons/'+s['id']+'.png':ROOT/'icons'/(s['id']+'.png') for s in CATALOG['spells']}
SCHEMA='chacademia.discovery-edits.v1'
SETTING_KEYS={'name','category','mana','cooldown','order','permission','command'}
NUMERIC={'mana':(0,1000000),'cooldown':(0,86400),'order':(-1000000,1000000)}

def setting_valid(key,value):
    if key in NUMERIC:
        lo,hi=NUMERIC[key];return type(value) is int and lo<=value<=hi
    if not isinstance(value,str):return False
    if key=='category':return value in {'wind','fire','water','earth','light','dark'}
    limit={'name':64,'permission':100,'command':256}[key]
    if len(value.encode('utf-16-le'))//2>limit or any(ord(c)<32 for c in value):return False
    value=value.strip()
    if key=='name' and not value:return False
    if key=='permission':return bool(re.fullmatch(r'[a-z0-9_.-]{1,100}',value))
    if key=='command' and value.startswith('/'):return False
    return True

def valid_condition(value):
    return bool(value.strip()) and len(value.encode('utf-16-le'))//2<=512 and not any(ord(c)<32 and c not in '\n\r' for c in value)

def validate_edits(edits):
    if not isinstance(edits,dict) or len(edits)>len(IDS):raise ValueError('수정 목록 형식이 올바르지 않습니다.')
    out={}
    for ident,item in edits.items():
        if ident not in IDS or not isinstance(item,dict) or not {'condition','reviewed'}<=set(item) or set(item)-({'condition','reviewed','description','research'}|SETTING_KEYS|{'circle','rank'}):raise ValueError('등록되지 않은 마법 또는 수정 항목입니다.')
        value=item['condition'];reviewed=item['reviewed']
        if not isinstance(value,str) or len(value.encode('utf-16-le'))//2>8192 or type(reviewed) is not bool:raise ValueError('조건 문구 또는 검토 상태를 확인하세요.')
        value=value.replace('\r\n','\n').replace('\r','\n')
        if reviewed and not valid_condition(value):raise ValueError('완료한 조건은 1~512자이며 탭 등 제어 문자를 포함할 수 없습니다.')
        out[ident]=dict(condition=value,reviewed=reviewed)
        for field,limit in [('description',1024),('research',512)]:
            if field not in item:continue
            text=item[field]
            if not isinstance(text,str) or len(text.encode('utf-16-le'))//2>8192:raise ValueError('설명 문구 형식을 확인하세요.')
            text=text.replace('\r\n','\n').replace('\r','\n')
            if reviewed and (len(text.encode('utf-16-le'))//2>limit or any(ord(c)<32 and c!='\n' for c in text)):raise ValueError('설명 문구 길이 또는 제어 문자를 확인하세요.')
            out[ident][field]=text
        for field in SETTING_KEYS & set(item):
            v=item[field]
            if not (isinstance(v,str) and len(v.encode('utf-16-le'))//2<=8192) and not (field in NUMERIC and type(v) is int and abs(v)<=9007199254740991):raise ValueError('설정 값의 형식이 올바르지 않습니다.')
            if reviewed and not setting_valid(field,v):raise ValueError(field+': 값 또는 허용 범위를 확인하세요.')
            out[ident][field]=v
    return out

class Store:
    def __init__(self,folder):
        self.folder=folder.resolve();self.folder.mkdir(parents=True,exist_ok=True)
        self.path=self.folder/'edits.json';self.lock=threading.Lock()
    def read(self):
        if not self.path.exists():return dict(schema=SCHEMA,datasetId=CATALOG['datasetId'],revision=0,updatedAt=None,edits={})
        doc=json.loads(self.path.read_text(encoding='utf-8'))
        if doc.get('schema')!=SCHEMA or doc.get('datasetId')!=CATALOG['datasetId']:raise ValueError('저장 파일의 기준 목록이 다릅니다. 기존 수정본을 보존한 상태로 확인이 필요합니다.')
        doc['edits']=validate_edits(doc['edits'])
        if type(doc.get('revision')) is not int or doc['revision']<0:raise ValueError('저장 파일의 버전이 올바르지 않습니다.')
        return doc
    def save(self,body):
        if body.get('schema')!=SCHEMA or body.get('datasetId')!=CATALOG['datasetId']:raise ValueError('다른 목록에서 만든 수정본입니다.')
        edits=validate_edits(body.get('edits'))
        with self.lock:
            previous=self.read()
            if type(body.get('revision')) is not int or body['revision']!=previous['revision']:return None
            doc=dict(schema=SCHEMA,datasetId=CATALOG['datasetId'],revision=previous['revision']+1,updatedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),edits=edits)
            encoded=json.dumps(doc,ensure_ascii=False,indent=2)+'\n'
            if self.path.exists():
                backup=self.folder/'history';backup.mkdir(exist_ok=True)
                (backup/f"revision-{previous['revision']:08}.json").write_bytes(self.path.read_bytes())
                for old in sorted(backup.glob('revision-*.json'))[:-20]:
                    assert old.resolve().parent==backup.resolve();old.unlink()
            pending=self.folder/'edits.pending'
            with pending.open('w',encoding='utf-8',newline='\n') as f:f.write(encoded);f.flush();os.fsync(f.fileno())
            os.replace(pending,self.path)
            return doc

def create_server(store,port=0):
    token=secrets.token_urlsafe(32)
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def send(self,status,body,content='application/json; charset=utf-8'):
            if isinstance(body,(dict,list)):body=json.dumps(body,ensure_ascii=False).encode()
            elif isinstance(body,str):body=body.encode()
            self.send_response(status);self.send_header('Content-Type',content);self.send_header('Content-Length',str(len(body)))
            self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.send_header('Referrer-Policy','no-referrer');self.send_header('X-Frame-Options','DENY');self.end_headers();self.wfile.write(body)
        def host_ok(self):return self.headers.get('Host') in {f'127.0.0.1:{self.server.server_port}',f'localhost:{self.server.server_port}'}
        def do_GET(self):
            if not self.host_ok():return self.send(403,{'error':'허용되지 않은 접속입니다.'})
            path=urllib.parse.urlsplit(self.path).path
            try:
                if path=='/api/health':return self.send(200,{'app':'chacademia-discovery-editor','datasetId':CATALOG['datasetId']})
                if path in ICON_FILES:
                    file=ICON_FILES[path]
                    if not file.is_file():return self.send(404,{'error':'아이콘 파일을 찾을 수 없습니다.'})
                    return self.send(200,file.read_bytes(),'image/png')
                if path=='/api/state':
                    if self.headers.get('X-Editor-Token')!=token:return self.send(403,{'error':'편집기를 새로 열어 주세요.'})
                    with store.lock:doc=store.read()
                    return self.send(200,doc)
                if path in {'/','/index.html'}:
                    text=(ROOT/'index.html').read_text(encoding='utf-8').replace('/* SERVER_CONFIG */','window.EDITOR_TOKEN='+json.dumps(token)+';')
                    return self.send(200,text,'text/html; charset=utf-8')
                static={'/icon-preview.css':'text/css; charset=utf-8','/icon-preview.js':'text/javascript; charset=utf-8','/app.js':'text/javascript; charset=utf-8','/catalog.js':'text/javascript; charset=utf-8','/style.css':'text/css; charset=utf-8'}
                if path in static:return self.send(200,(ROOT/path[1:]).read_bytes(),static[path])
                self.send(404,{'error':'찾을 수 없는 페이지입니다.'})
            except (OSError,ValueError) as e:self.send(500,{'error':str(e)})
        def do_POST(self):
            origin=self.headers.get('Origin')
            if not self.host_ok() or self.headers.get('X-Editor-Token')!=token or (origin is not None and origin not in {f'http://127.0.0.1:{self.server.server_port}',f'http://localhost:{self.server.server_port}'}):return self.send(403,{'error':'허용되지 않은 저장 요청입니다.'})
            if self.headers.get('Content-Type','').split(';')[0]!='application/json':return self.send(415,{'error':'JSON 요청만 허용됩니다.'})
            try:
                length=int(self.headers.get('Content-Length','0'))
                if not 0<length<=2_000_000:return self.send(413,{'error':'파일이 너무 큽니다.'})
                body=json.loads(self.rfile.read(length))
                if not isinstance(body,dict):raise ValueError('저장 요청 형식이 올바르지 않습니다.')
                if self.path=='/api/state':
                    doc=store.save(body)
                    if doc is None:return self.send(409,{'error':'다른 창에서 수정본이 바뀌었습니다. 현재 수정본을 내보낸 뒤 새로고침해 주세요.'})
                    return self.send(200,doc)
                if self.path=='/api/shutdown':
                    self.send(200,{'ok':True});threading.Thread(target=self.server.shutdown,daemon=True).start();return
                return self.send(404,{'error':'알 수 없는 요청입니다.'})
            except (ValueError,UnicodeError) as e:self.send(400,{'error':str(e)})
            except OSError:self.send(500,{'error':'파일을 저장하지 못했습니다. 수정본을 내보내고 저장 공간을 확인하세요.'})
    server=ThreadingHTTPServer(('127.0.0.1',port),Handler)
    return server,token

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=0);parser.add_argument('--data-dir',type=Path,default=ROOT/'data');args=parser.parse_args()
    store=Store(args.data_dir);store.read()
    server,token=create_server(store,args.port)
    runtime=dict(url=f'http://127.0.0.1:{server.server_port}/',pid=os.getpid(),datasetId=CATALOG['datasetId'])
    (store.folder/'runtime.json').write_text(json.dumps(runtime),encoding='utf-8')
    print(json.dumps(runtime),flush=True)
    try:server.serve_forever()
    finally:server.server_close()
