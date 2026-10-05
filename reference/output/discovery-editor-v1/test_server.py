import importlib.util,json,tempfile,threading,unittest,urllib.request,urllib.error
from pathlib import Path
spec=importlib.util.spec_from_file_location('editor',Path(__file__).with_name('server.py'));app=importlib.util.module_from_spec(spec);spec.loader.exec_module(app)

class EditorTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.store=app.Store(Path(self.tmp.name));self.server,self.token=app.create_server(self.store)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start();self.url=f'http://127.0.0.1:{self.server.server_port}'
        self.ident=app.CATALOG['spells'][0]['id']
    def tearDown(self):self.server.shutdown();self.server.server_close();self.thread.join();self.tmp.cleanup()
    def call(self,path,body=None,token=True,extra=None):
        headers={'X-Editor-Token':self.token} if token else {}
        if extra:headers.update(extra)
        if body is not None:headers['Content-Type']='application/json';body=json.dumps(body,ensure_ascii=False).encode()
        try:
            with urllib.request.urlopen(urllib.request.Request(self.url+path,data=body,headers=headers)) as r:return r.status,r.read()
        except urllib.error.HTTPError as e:return e.code,e.read()
    def doc(self,text='비 오는 날 밀 30개를 수확하기.\n서로 다른 마을 2곳에서 수확하기.',rev=0,done=False):
        return dict(schema=app.SCHEMA,datasetId=app.CATALOG['datasetId'],revision=rev,edits={self.ident:dict(condition=text,reviewed=done)})
    def test_save_reload_multiline_and_history(self):
        code,_=self.call('/api/state',self.doc(done=True));self.assertEqual(200,code)
        code,body=self.call('/api/state');saved=json.loads(body);self.assertEqual(self.doc()['edits'][self.ident]['condition'],saved['edits'][self.ident]['condition'])
        self.assertEqual(1,saved['revision']);self.assertEqual(saved,app.Store(Path(self.tmp.name)).read())
        self.assertEqual(200,self.call('/api/state',self.doc('새 조건',1))[0]);self.assertEqual(1,len(list((Path(self.tmp.name)/'history').glob('*.json'))))
    def test_stale_revision_cannot_overwrite(self):
        self.assertEqual(200,self.call('/api/state',self.doc('첫 번째'))[0]);self.assertEqual(409,self.call('/api/state',self.doc('두 번째'))[0]);self.assertEqual('첫 번째',self.store.read()['edits'][self.ident]['condition'])
    def test_bad_dataset_unknown_ids_and_types_rejected(self):
        d=self.doc();d['datasetId']='other';self.assertEqual(400,self.call('/api/state',d)[0])
        d=self.doc();d['edits']['../../outside']={'condition':'text','reviewed':False};self.assertEqual(400,self.call('/api/state',d)[0])
        d=self.doc();d['edits'][self.ident]['reviewed']='yes';self.assertEqual(400,self.call('/api/state',d)[0]);self.assertEqual(0,self.store.read()['revision'])
    def test_drafts_survive_but_invalid_review_is_rejected(self):
        self.assertEqual(200,self.call('/api/state',self.doc(''))[0]);self.assertEqual(400,self.call('/api/state',self.doc('',1,True))[0])
        self.assertEqual(200,self.call('/api/state',self.doc('가'*513,1))[0]);self.assertEqual(400,self.call('/api/state',self.doc('가'*513,2,True))[0])
        self.assertEqual(400,self.call('/api/state',self.doc('탭\t문자',2,True))[0]);self.assertEqual(200,self.call('/api/state',self.doc('가'*512,2,True))[0])
        self.assertFalse(app.valid_condition('😀'*257));self.assertTrue(app.valid_condition('😀'*256))
    def test_cross_origin_missing_token_and_paths_denied(self):
        self.assertEqual(403,self.call('/api/state',self.doc(),token=False)[0]);self.assertEqual(403,self.call('/api/state',self.doc(),extra={'Origin':'https://example.com'})[0])
        self.assertEqual(403,self.call('/api/state',self.doc(),extra={'Host':'example.com'})[0]);self.assertEqual(403,self.call('/api/state',token=False)[0]);self.assertEqual(404,self.call('/source-yaml/')[0]);self.assertEqual(404,self.call('/../server.py')[0])
    def test_corrupt_file_is_preserved(self):
        self.store.path.write_text('corrupt',encoding='utf-8');self.assertEqual(500,self.call('/api/state')[0]);self.assertEqual(400,self.call('/api/state',self.doc())[0]);self.assertEqual('corrupt',self.store.path.read_text())
    def test_all_200_conditions_save_together(self):
        d=self.doc();d['edits']={s['id']:{'condition':s['condition']+'\n추가 조건 확인.','reviewed':False} for s in app.CATALOG['spells']}
        self.assertEqual(200,self.call('/api/state',d)[0]);self.assertEqual(200,len(self.store.read()['edits']))
    def test_static_app_and_token(self):
        code,html=self.call('/');self.assertEqual(200,code);self.assertIn(self.token.encode(),html);self.assertEqual(200,self.call('/app.js')[0]);self.assertEqual(200,self.call('/catalog.js')[0])

    def test_original_icons_and_private_paths(self):
        for s in app.CATALOG['spells']:
            path='/icons/'+s['id']+'.png'
            code,body=self.call(path)
            original=app.ROOT/path[1:]
            if not original.is_file():
                self.assertEqual(404,code)
                continue
            self.assertEqual(200,code)
            self.assertEqual(original.read_bytes(),body)
            self.assertTrue(body.startswith(b'\x89PNG\r\n\x1a\n'))
        for path in ['/icons/not-a-spell.png','/icons/../data/edits.json','/icons/%2e%2e/server.py','/icon-manifest.json']:
            self.assertEqual(404,self.call(path)[0])
        self.assertEqual(200,self.call('/icon-preview.js')[0]);self.assertEqual(200,self.call('/icon-preview.css')[0])

    def test_description_and_research_roundtrip(self):
        d=self.doc(done=True);d['edits'][self.ident].update(description='마법 효과\n둘째 줄',research='모험일지의 기록')
        self.assertEqual(200,self.call('/api/state',d)[0])
        self.assertEqual(d['edits'],app.Store(Path(self.tmp.name)).read()['edits'])
        d['revision']=1;d['edits'][self.ident]['research']=''
        self.assertEqual(200,self.call('/api/state',d)[0])
        self.assertEqual('',self.store.read()['edits'][self.ident]['research'])

    def test_extra_field_limits_and_legacy_preservation(self):
        d=self.doc(done=True);d['edits'][self.ident]['description']='가'*1025
        self.assertEqual(400,self.call('/api/state',d)[0])
        d['edits'][self.ident]['description']='가'*1024;d['edits'][self.ident]['research']='가'*513
        self.assertEqual(400,self.call('/api/state',d)[0])
        d['edits'][self.ident]['research']='가'*512
        self.assertEqual(200,self.call('/api/state',d)[0])
        legacy=self.doc('기존에 작성한 조건',1)
        self.assertEqual(200,self.call('/api/state',legacy)[0])
        self.assertEqual(legacy['edits'],self.store.read()['edits'])

    def test_settings_roundtrip_and_legacy_metadata_discarded(self):
        d=self.doc(done=True);d['edits'][self.ident].update(name='새 이름',category='earth',mana=999,cooldown=120,order=-10,permission='magic.custom',command='cast custom',description='새 효과',research='새 기록')
        legacy=json.loads(json.dumps(d));legacy['edits'][self.ident].update(circle=9,rank='고급')
        self.assertEqual(200,self.call('/api/state',legacy)[0]);self.assertEqual(d['edits'],self.store.read()['edits'])
        for spell in app.CATALOG['spells']:
            self.assertNotIn('rank',spell);self.assertNotIn('circle',spell)

    def test_setting_validation_and_draft_recovery(self):
        for key,v in [('name',''),('name','가'*65),('category','all'),('mana',-1),('mana',1000001),('cooldown',86401),('order',-1000001),('permission','Bad Permission'),('command','/cast test'),('command','cast\ntest')]:
            with self.subTest(key=key,value=v):
                d=self.doc(done=True);d['edits'][self.ident][key]=v;self.assertEqual(400,self.call('/api/state',d)[0])
        d=self.doc();d['edits'][self.ident].update(mana='',cooldown='1.5',name='')
        self.assertEqual(200,self.call('/api/state',d)[0]);self.assertEqual(d['edits'],self.store.read()['edits'])
        for key in ['id','icon','sound','vfx']:
            d=self.doc(rev=1);d['edits'][self.ident][key]='test';self.assertEqual(400,self.call('/api/state',d)[0])

if __name__=='__main__':unittest.main(verbosity=2)
