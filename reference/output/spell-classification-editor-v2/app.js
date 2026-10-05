(() => {
'use strict';
const data=window.DISCOVERY_CATALOG, spells=data.spells, byId=new Map(spells.map(s=>[s.id,s]));
const schema='chacademia.classification-edits.v2', storageKey='chacademia.classification.'+data.datasetId;
const labels={wind:'바람',fire:'화염',water:'물',earth:'대지',light:'빛',dark:'어둠'};
const colors={wind:'#a6d6c5',fire:'#e6ae8d',water:'#9bcfe5',earth:'#b4ce98',light:'#e4d08e',dark:'#c6b1e5'};
const $=id=>document.getElementById(id), online=Boolean(window.EDITOR_TOKEN);
let edits={},revision=0,selected=spells[0].id,visible=spells,ready=false,saving=false,blocked=false;
let generation=0,acked=0,timer,toastTimer,localAvailable=true;
const reconnect=document.createElement('button');reconnect.textContent='저장 파일 다시 불러오기';reconnect.hidden=true;reconnect.style.margin='10px 30px';$('notice').after(reconnect);
reconnect.onclick=()=>{if(confirm('현재 입력을 보관하려면 먼저 수정본을 내보내줘. 저장 파일의 내용으로 다시 열까?')){try{localStorage.removeItem(storageKey);}catch(_){}acked=generation;ready=false;location.reload();}};
function value(id,field='condition'){return edits[id]?.[field]??byId.get(id)[field]??'';}
const settings=[
 {key:'category',label:'원소',options:Object.entries(labels)},
 {key:'name',label:'마법 이름',max:64,required:true},
 {key:'mana',label:'마나 소모량',numeric:true,min:0,max:1000000},
 {key:'cooldown',label:'쿨타임 (초)',numeric:true,min:0,max:86400},
 {key:'order',label:'정렬 순서',numeric:true,min:-1000000,max:1000000},
 {key:'permission',label:'획득 권한',max:100,required:true,hint:'예: magic.learned.wind_basket'},
 {key:'command',label:'시전 명령어',max:256,hint:'맨 앞의 / 없이 입력. 비워두면 시전 명령을 보내지 않아.'}
];
const extraFields=['description','research',...settings.map(s=>s.key)];
const fields=['condition',...extraFields];
function settingError(key,v){
 const s=settings.find(s=>s.key===key);if(!s)return '';
 if(key==='category')return Object.hasOwn(labels,v)?'':'원소를 선택해줘.';
 if(s.numeric)return Number.isInteger(v)&&v>=s.min&&v<=s.max?'':s.min+'~'+s.max+' 범위의 정수를 입력해줘.';
 if(typeof v!=='string')return '문자열을 입력해줘.';
 if(s.required&&!v.trim())return '내용을 입력해줘.';
 if(v.length>s.max||/[\x00-\x1f]/.test(v))return '한 줄, 최대 '+s.max+'자로 입력해줘.';
 if(key==='permission'&&!/^[a-z0-9_.-]{1,100}$/.test(v.trim()))return '권한은 영문 소문자·숫자·점·밑줄·하이픈으로 입력해줘.';
 if(key==='command'&&v.trim().startsWith('/'))return '맨 앞의 /를 빼줘.';
 return '';
}
function mountSettings(){
 const section=document.createElement('section');section.className='settings-section';
 const title=document.createElement('h3');title.textContent='기본 설정';section.append(title);
 const grid=document.createElement('div');grid.className='settings-grid';section.append(grid);
 for(const s of settings){
  const box=document.createElement('div');box.className='setting-field';
  const label=document.createElement('label');label.htmlFor='setting-'+s.key;label.textContent=s.label;
  const input=document.createElement(s.options?'select':'input');input.id='setting-'+s.key;
  if(s.options)for(const [v,t] of s.options){const o=document.createElement('option');o.value=v;o.textContent=t;input.append(o);}
  else {input.type='text';if(s.numeric)input.inputMode='numeric';}
  input.disabled=true;input.setAttribute('aria-describedby','setting-'+s.key+'-error');
  const error=document.createElement('span');error.id='setting-'+s.key+'-error';error.className='setting-error';error.setAttribute('role','status');
  const original=document.createElement('span');original.id='setting-'+s.key+'-original';original.className='setting-original';
  const reset=document.createElement('button');reset.className='text-button';reset.textContent='되돌리기';reset.id='setting-'+s.key+'-reset';reset.setAttribute('aria-label',s.label+' 되돌리기');
  input.addEventListener(s.options?'change':'input',()=>{if(!ready||!selected)return;let v=input.value;if(s.numeric&&v!==''&&/^-?\d+$/.test(v))v=Number(v);setEntry(selected,value(selected),false,{[s.key]:v});commit();});
  reset.onclick=()=>{if(selected){setEntry(selected,value(selected),false,{[s.key]:byId.get(selected)[s.key]??''});renderEditor();commit();}};
  box.append(label,input,error,original,reset);if(s.hint){const hint=document.createElement('p');hint.className='hint';hint.textContent=s.hint;box.append(hint);}grid.append(box);
 }
 const note=document.createElement('p');note.className='hint';note.textContent='마법 ID는 기존 마법을 연결하는 식별자로 고정돼. 리소스·사운드·VFX는 편집하지 않아.';section.append(note);
 $('editor').querySelector('.spell-heading').after(section);
}
mountSettings();
const artPreview=window.createSpellArtPreview({getSpell:()=>byId.get(selected),getName:()=>value(selected,'name')});
function updateHeading(){if(!selected)return;artPreview.rename();const id=selected,c=value(id,'category');$('spell-name').textContent=value(id,'name');$('element-badge').textContent=labels[c]??c;$('element-badge').style.setProperty('--element',colors[c]??'');$('mana').textContent='마나 '+value(id,'mana');$('cooldown').textContent='쿨타임 '+value(id,'cooldown')+'초';}

function extraError(text,limit){return text.length>limit?'최대 '+limit+'자까지 사용할 수 있어.':/[\x00-\x09\x0b\x0c\x0e-\x1f]/.test(text)?'탭 등 제어 문자를 지워줘.':'';}
function entryError(id){return errorFor(value(id),id)||extraError(value(id,'description'),1024)||extraError(value(id,'research'),512)||settings.map(s=>settingError(s.key,value(id,s.key))).find(Boolean)||'';}
function reviewed(id){return edits[id]?.reviewed===true;}
function changed(id){return fields.some(f=>value(id,f)!==(byId.get(id)[f]??''));}
function errorFor(text,id=selected){if(!text.trim()&&byId.get(id)?.sourceGroup!=='new')return '조건을 입력해줘. 빈 내용도 임시 저장되지만 검토 완료할 수 없어.';if(text.length>512)return '조건은 512자까지 사용할 수 있어. 내용을 줄여줘.';if(/[\x00-\x09\x0b\x0c\x0e-\x1f]/.test(text))return '탭 등 제어 문자를 지우고 공백이나 줄바꿈으로 바꿔줘.';return '';}
function notice(message){$('notice').textContent=message;$('notice').hidden=!message;reconnect.hidden=!blocked;}
function toast(message){clearTimeout(toastTimer);$('toast').textContent=message;$('toast').hidden=false;toastTimer=setTimeout(()=>$('toast').hidden=true,2800);}
function documentState(){return {schema,datasetId:data.datasetId,revision,edits};}
function persistLocal(){
 try{localStorage.setItem(storageKey,JSON.stringify({...documentState(),pending:generation!==acked,savedAt:new Date().toISOString(),selected}));localAvailable=true;}
 catch(_){localAvailable=false;notice('브라우저 임시 저장을 사용할 수 없어. “수정본 내보내기”로 별도 사본을 저장해줘.');}
}
function validationMessage(err){$('save-status').textContent=err;}
function commit(){
 generation++;persistLocal();updateCounts();updateField();renderList();
 if(online && !blocked){validationMessage('저장 중…');clearTimeout(timer);timer=setTimeout(save,500);}
 else validationMessage(localAvailable?'브라우저에 임시 저장됨':'수정본 내보내기 필요');
}
async function save(){
 clearTimeout(timer);if(!online || !ready || saving || blocked || generation===acked)return;
 saving=true;const snapshot=generation,body=JSON.stringify(documentState());
 try{
  const r=await fetch('/api/state',{method:'POST',headers:{'Content-Type':'application/json','X-Editor-Token':window.EDITOR_TOKEN},body,keepalive:true});
  const result=await r.json();
  if(!r.ok){if(r.status===409)blocked=true;throw new Error(result.error||'저장 실패');}
  revision=result.revision;acked=snapshot;persistLocal();
  validationMessage('파일에 저장됨 · '+new Date(result.updatedAt).toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'}));
  if(!blocked && localAvailable)notice('');
 }catch(e){notice(e.message+' 현재 입력은 이 브라우저에 보관 중이야. 수정본을 내보낸 뒤 저장 파일 다시 불러오기를 사용할 수 있어.');validationMessage('파일 저장 안 됨 · 임시 보관');}
 finally{saving=false;if(generation!==acked && !blocked)timer=setTimeout(save,2500);}
}
function setEntry(id,condition,isReviewed,extras={}){
 edits[id]={...edits[id],...extras,condition,reviewed:isReviewed};
 for(const f of extraFields)if(edits[id][f]===(byId.get(id)[f]??''))delete edits[id][f];
 if(!changed(id)&&!isReviewed)delete edits[id];
}
function matches(s){
 const category=$('category').value,status=$('status-filter').value,source=$('source-filter').value;
 const q=$('search').value.replace(/\s+/g,'').toLowerCase();
 if(category&&value(s.id,'category')!==category)return false;
 if(source&&s.sourceGroup!==source)return false;
 if(status==='todo'&&reviewed(s.id))return false;
 if(status==='changed'&&!changed(s.id))return false;
 if(status==='reviewed'&&!reviewed(s.id))return false;
 if(status==='invalid'&&!entryError(s.id))return false;
 return !q||(s.name+s.id+fields.map(f=>value(s.id,f)).join(' ')).replace(/\s+/g,'').toLowerCase().includes(q);
}
function renderList(){
 visible=spells.filter(matches).sort((a,b)=>(Number(value(a.id,'order'))||0)-(Number(value(b.id,'order'))||0)||a.id.localeCompare(b.id));$('result-count').textContent=visible.length+'종';
 const scroll=$('spell-list').scrollTop,fragment=document.createDocumentFragment();
 for(const s of visible){
  const row=document.createElement('button');row.className='spell-row'+(s.id===selected?' active':'');row.dataset.id=s.id;
  row.setAttribute('aria-current',s.id===selected?'true':'false');
  const text=document.createElement('span'),name=document.createElement('span'),sub=document.createElement('span'),status=document.createElement('span');
  name.className='row-name';name.textContent=value(s.id,'name');sub.className='row-sub';sub.textContent=labels[value(s.id,'category')]+' · '+(s.sourceGroup==='new'?'신규':'기존')+' · 순서 '+value(s.id,'order');
  status.className='row-status';
  if(entryError(s.id)){status.textContent='확인 필요';status.classList.add('invalid');}
  else if(reviewed(s.id))status.textContent='✓ 완료';
  else if(changed(s.id)){status.textContent='수정';status.classList.add('edited');}
  text.append(name,sub);row.append(text,status);row.onclick=()=>select(s.id);fragment.append(row);
 }
 $('spell-list').replaceChildren(fragment);$('spell-list').scrollTop=scroll;updateNavigation();
}
function filter(){renderList();if(!visible.some(s=>s.id===selected)){selected=visible[0]?.id??null;renderEditor();renderList();}}
function select(id){selected=id;renderEditor();renderList();persistLocal();}
function updateNavigation(){
 const i=visible.findIndex(s=>s.id===selected);$('position').textContent=i<0?'':(i+1)+' / '+visible.length+' · 선택한 목록';
 $('previous').disabled=i<=0;$('next').disabled=i<0||i>=visible.length-1;
}
function move(delta){const i=visible.findIndex(s=>s.id===selected);if(i>=0&&visible[i+delta])select(visible[i+delta].id);}
function renderEditor(){
 const s=byId.get(selected);$('editor').hidden=!s;$('empty-state').hidden=Boolean(s);artPreview.select(s);if(!s)return;
 for(const f of settings){$('setting-'+f.key).value=value(s.id,f.key);$('setting-'+f.key).disabled=!ready;}
 $('spell-name').textContent=s.name;$('spell-id').textContent=s.id+(s.hasYaml?' · 기존 YAML':' · 신규 기획 · YAML 미제작');$('element-badge').textContent=labels[s.category];$('element-badge').style.setProperty('--element',colors[s.category]);
 $('source-note').hidden=s.hasYaml;
 $('condition-hint').textContent=s.hasYaml?'조건을 편하게 적어줘. 여러 조건은 줄을 나눠 작성해도 돼.':'이 100종은 아직 획득 조건과 실제 YAML이 없어. 분류 검토는 조건 없이 완료할 수 있고, 조건은 나중에 적어도 돼.';
 $('mana').textContent='마나 '+s.mana;$('cooldown').textContent='쿨타임 '+s.cooldown+'초';
 for(const f of ['description','research']){$(f).value=value(s.id,f);$(f).disabled=!ready;$('original-'+f).textContent=s[f]??'';}$('original-condition').textContent=s.condition;$('condition').value=value(s.id);$('condition').disabled=!ready;updateField();
}
function updateField(){
 if(!selected)return;const text=value(selected),err=entryError(selected);updateHeading();
 for(const s of settings){const v=value(selected,s.key),original=byId.get(selected)[s.key]??'',e=settingError(s.key,v);$('setting-'+s.key+'-error').textContent=e;$('setting-'+s.key).setAttribute('aria-invalid',String(Boolean(e)));$('setting-'+s.key+'-reset').disabled=v===original||!ready;const shown=s.options?s.options.find(([k])=>k===String(original))?.[1]??original:original;$('setting-'+s.key+'-original').textContent='기존: '+(shown===''?'비어 있음':shown);}
 for(const [f,limit] of [['description',1024],['research',512]]){const v=value(selected,f),e=extraError(v,limit);$(f+'-count').textContent=v.length+' / '+limit+'자';$(f+'-validation').textContent=e;$(f).classList.toggle('invalid',Boolean(e));$(f).setAttribute('aria-invalid',String(Boolean(e)));$(f).setAttribute('aria-describedby',f+'-validation '+f+'-count');$(f+'-changed').hidden=v===(byId.get(selected)[f]??'');$(f+'-restore').disabled=v===(byId.get(selected)[f]??'')||!ready;}
 $('char-count').textContent=text.length+' / 512자';$('validation').textContent=errorFor(text,selected);$('condition').classList.toggle('invalid',Boolean(errorFor(text,selected)));
 $('condition').setAttribute('aria-invalid',String(Boolean(errorFor(text,selected))));$('condition').setAttribute('aria-describedby','validation char-count');
 $('changed-badge').hidden=value(selected)===byId.get(selected).condition;$('review-badge').hidden=!reviewed(selected);$('reviewed').checked=reviewed(selected);$('reviewed').disabled=Boolean(err)||!ready;
 $('review-next').disabled=Boolean(err)||!ready;$('restore').disabled=value(selected)===byId.get(selected).condition||!ready;
}
function updateCounts(){const changedCount=spells.filter(s=>changed(s.id)).length,done=spells.filter(s=>reviewed(s.id)).length;
 $('changed-count').textContent=changedCount;$('reviewed-count').textContent=done;$('todo-count').textContent=spells.length-done;$('progress').value=done;$('progress-label').textContent=done+' / '+spells.length+' 검토';}
function completeAndNext(){
 if(!ready||!selected||entryError(selected))return;
 const oldVisible=visible.slice(),index=oldVisible.findIndex(s=>s.id===selected),id=selected;
 setEntry(id,value(id),true);commit();
 const next=[...oldVisible.slice(index+1),...oldVisible.slice(0,index)].find(s=>!reviewed(s.id));
 if(next)select(next.id);else if(!visible.some(s=>s.id===selected))filter();
 toast(next?'검토 완료 · 다음 마법으로 이동했어.':'현재 목록의 검토를 마쳤어.');
}
function exportDraft(){
 if(!ready)return;
 const baseline={};for(const id of Object.keys(edits)){const s=byId.get(id);baseline[id]={...Object.fromEntries(fields.map(f=>[f,s[f]??''])),sha256:s.sha256,file:s.file};}
 const invalid=Object.keys(edits).filter(id=>entryError(id));
 const bundle={...documentState(),exportedAt:new Date().toISOString(),baseline,validation:{invalidIds:invalid},note:'마법 설정 및 설명 수정본입니다. 항목에 없는 필드는 유지합니다. 실제 YAML의 최신 내용과 baseline을 비교한 뒤 반영하세요.'};
 const blob=new Blob([JSON.stringify(bundle,null,2)+'\n'],{type:'application/json;charset=utf-8'}),url=URL.createObjectURL(blob),a=document.createElement('a');
 const date=new Date(),stamp=[date.getFullYear(),date.getMonth()+1,date.getDate(),date.getHours(),date.getMinutes()].map((v,i)=>i?String(v).padStart(2,'0'):v).join('');
 a.href=url;a.download='차카데미아-마법분류-수정본-'+stamp+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),5000);
 toast(invalid.length?'수정본을 내보냈어. 입력 확인이 필요한 조건 '+invalid.length+'개도 포함돼.':'수정본을 내보냈어. 이 파일로도 이어서 작업할 수 있어.');
}
function checkedEdits(doc){
 if(!doc||doc.schema!==schema||doc.datasetId!==data.datasetId||!doc.edits||Array.isArray(doc.edits)||typeof doc.edits!=='object')throw new Error('이 마법 목록에서 내보낸 수정본이 아니야. 다른 기준 목록은 먼저 비교가 필요해.');
 const result={};for(const [id,item] of Object.entries(doc.edits)){
  if(!byId.has(id)||!item||typeof item.condition!=='string'||item.condition.length>8192||typeof item.reviewed!=='boolean')throw new Error('수정본의 마법이나 입력 형식을 확인해줘.');
  const condition=item.condition.replace(/\r\n?/g,'\n');if(item.reviewed&&errorFor(condition,id))throw new Error('검토 완료된 항목에 잘못된 조건이 있어.');
  const extras={};for(const [f,limit] of [['description',1024],['research',512]]){if(!(f in item))continue;if(typeof item[f]!=='string'||item[f].length>8192)throw new Error('설명 문구 형식을 확인해줘.');extras[f]=item[f].replace(/\r\n?/g,'\n');if(item.reviewed&&extraError(extras[f],limit))throw new Error('검토 완료된 설명을 확인해줘.');}
  for(const s of settings){if(!(s.key in item))continue;const v=item[s.key];if(!(typeof v==='string'&&v.length<=8192)&&!(s.numeric&&typeof v==='number'&&Number.isFinite(v)))throw new Error('설정 값의 형식을 확인해줘.');if(item.reviewed&&settingError(s.key,v))throw new Error(s.label+': '+settingError(s.key,v));extras[s.key]=v;}
  result[id]={condition,reviewed:item.reviewed,...extras};
 }return result;
}
async function importDraft(file){
 if(!file||!ready)return;
 try{
  if(file.size>2_000_000)throw new Error('수정본 파일이 너무 커.');
  const incoming=checkedEdits(JSON.parse(await file.text())),ids=Object.keys(incoming);
  if(!ids.length){toast('가져올 수정 내용이 없어.');return;}
  if(!confirm(ids.length+'개 마법의 수정 내용을 가져올까? 해당 마법에 지금 입력한 내용은 가져온 내용으로 바뀌고, 다른 마법은 유지돼.'))return;
  for(const id of ids)setEntry(id,incoming[id].condition,incoming[id].reviewed,Object.fromEntries(extraFields.filter(f=>f in incoming[id]).map(f=>[f,incoming[id][f]])));
  renderEditor();commit();toast(ids.length+'개 마법의 수정본을 가져왔어.');
 }catch(e){notice(e.message);}finally{$('import-file').value='';}
}
async function init(){
 let cached=null;try{const raw=localStorage.getItem(storageKey);if(raw){cached=JSON.parse(raw);checkedEdits(cached);}}catch(_){notice('브라우저에 있던 임시 저장본을 읽지 못했어. 별도 사본이 있다면 가져오기를 사용해줘.');}
 if(online){
  try{
   const response=await fetch('/api/state',{headers:{'X-Editor-Token':window.EDITOR_TOKEN}}),doc=await response.json();if(!response.ok)throw new Error(doc.error||'저장 파일을 읽지 못했어.');
   edits=checkedEdits(doc);revision=doc.revision;
   if(cached?.pending){
    if(cached.revision===revision){edits=checkedEdits(cached);generation=1;}
    else {blocked=true;notice('파일에 반영되지 않은 브라우저 수정본이 있고, 다른 창의 저장 내용도 바뀌었어. 현재 임시 수정본을 내보내서 보관한 뒤 저장 파일 다시 불러오기를 눌러줘.');edits=checkedEdits(cached);revision=cached.revision;generation=1;}
   }
   validationMessage(blocked?'저장 충돌 · 내보내기 필요':generation?'이전 임시 수정본 복구 중…':'로컬 파일 자동 저장 준비됨');
  }catch(e){blocked=true;if(cached)edits=checkedEdits(cached);notice(e.message+' 원본 파일은 그대로 보존돼. 현재 수정본은 내보내기로 보관해줘.');validationMessage('파일 연결 안 됨');}
 }else{
  if(cached){edits=checkedEdits(cached);revision=cached.revision||0;}
  notice('HTML만 연 상태야. 브라우저에 임시 저장되며, 다 수정한 뒤 “수정본 내보내기” 파일을 보내줘. 실행 파일로 열면 작업 폴더에 자동 저장돼.');
  $('workflow').textContent='이 화면은 브라우저에만 임시 저장돼. 다 고쳤으면 수정본을 내보내서 보내줘.';validationMessage('브라우저 임시 저장 모드');
 }
 selected=spells.find(s=>!reviewed(s.id))?.id??spells[0].id;
 if(cached?.selected&&byId.has(cached.selected))selected=cached.selected;
 ready=true;updateCounts();renderEditor();renderList();if(generation&&!blocked)save();
}
for(const f of ['description','research']){
 $(f).addEventListener('input',()=>{if(!ready||!selected)return;setEntry(selected,value(selected),false,{[f]:$(f).value.replace(/\r\n?/g,'\n')});commit();});
 $(f+'-restore').onclick=()=>{if(selected&&confirm('이 항목을 기존 문구로 되돌릴까?')){setEntry(selected,value(selected),false,{[f]:byId.get(selected)[f]??''});renderEditor();commit();}};
}
$('condition').addEventListener('input',()=>{if(!ready||!selected)return;setEntry(selected,$('condition').value.replace(/\r\n?/g,'\n'),false);commit();});
$('reviewed').onchange=()=>{if(!selected)return;setEntry(selected,value(selected),$('reviewed').checked);commit();};
$('review-next').onclick=completeAndNext;$('previous').onclick=()=>move(-1);$('next').onclick=()=>move(1);
$('restore').onclick=()=>{if(selected&&confirm('이 마법의 조건을 기존 문구로 되돌릴까?')){setEntry(selected,byId.get(selected).condition,false);renderEditor();commit();}};
$('search').oninput=filter;for(const id of ['category','status-filter','source-filter'])$(id).onchange=filter;
$('clear-filter').onclick=()=>{$('search').value='';$('category').value='';$('source-filter').value='';$('status-filter').value='all';filter();};
$('show-changes').onclick=()=>{$('search').value='';$('category').value='';$('source-filter').value='';$('status-filter').value='changed';filter();};
$('export-button').onclick=exportDraft;$('import-button').onclick=()=>$('import-file').click();$('import-file').onchange=e=>importDraft(e.target.files[0]);
document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key==='Enter'){e.preventDefault();completeAndNext();}if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='s'){e.preventDefault();if(online)save();else exportDraft();}});
window.addEventListener('beforeunload',e=>{if(online&&generation!==acked){e.preventDefault();e.returnValue='';}});
window.addEventListener('pagehide',()=>{persistLocal();if(online&&!blocked)save();});
window.addEventListener('online',()=>{if(online&&!blocked)save();});
// Cross-tab edits are guarded by server revisions; polling never replaces an active draft.
init();
})();
