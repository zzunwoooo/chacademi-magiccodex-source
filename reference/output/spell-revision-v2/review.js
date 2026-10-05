(() => {
'use strict';
const data=window.SPELL_REVIEW,rows=data.rows,byId=new Map(rows.map(r=>[r.id,r])),labels={wind:'바람',fire:'화염',water:'물',earth:'대지',light:'빛',dark:'어둠'};
const initial=JSON.parse(JSON.stringify(data.draft.edits)),key='chacademia.style-review.20260930.v2';
const fields=['name','category','mana','cooldown','order','permission','command','description','research','condition'];
let edits=JSON.parse(JSON.stringify(initial)),scope='proposal',selected='earth_elemental';
const $=id=>document.getElementById(id),node=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
function validEdit(id,e){
 if(!byId.has(id)||!e||Array.isArray(e)||typeof e.condition!=='string'||typeof e.reviewed!=='boolean')return false;
 for(const [f,v] of Object.entries(e)){
  if(![...fields,'reviewed'].includes(f))return false;
  if(f==='reviewed')continue;
  if(['mana','cooldown','order'].includes(f)){if(!Number.isInteger(v)||v<({mana:0,cooldown:0,order:-1000000}[f])||v>({mana:1000000,cooldown:86400,order:1000000}[f]))return false;}
  else if(typeof v!=='string'||v.length>({name:64,permission:100,command:256,condition:512,research:512,description:1024,category:16}[f])||/[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(v))return false;
 }
 if(e.category&&!Object.hasOwn(labels,e.category))return false;
 if(e.permission!==undefined&&!/^[a-z0-9_.-]+$/.test(e.permission))return false;
 if(e.command!==undefined&&(/[\r\n]/.test(e.command)||e.command.startsWith('/')))return false;
 if(e.reviewed&&byId.get(id).hasYaml&&!e.condition.trim())return false;
 return true;
}
try{const saved=JSON.parse(localStorage.getItem(key)||'null');if(saved&&Object.entries(saved).every(([i,e])=>validEdit(i,e)))edits=saved;}catch(_){}
function value(r,f){return edits[r.id]?.[f]??r[f]??'';}
function persist(){try{localStorage.setItem(key,JSON.stringify(edits));$('save-state').textContent='브라우저 임시 저장됨 · 작업을 마치면 내보내기';}catch(_){$('save-state').textContent='임시 저장 불가 · 내보내기로 보관해 주세요';}}
function change(r,f,v){
 const next={condition:value(r,'condition'),...(edits[r.id]??{}),[f]:v,reviewed:false};
 if(!validEdit(r.id,next)){$('save-state').textContent='값의 길이·형식을 확인해 주세요';return false;}
 edits[r.id]=next;persist();$('reviewed').checked=false;renderList();return true;
}
function matches(r){const q=$('search').value.trim().toLowerCase();return(scope==='all'||r.origin===scope)&&(!$('category').value||value(r,'category')===$('category').value)&&(!q||['name','description','condition','research'].some(f=>String(value(r,f)).toLowerCase().includes(q)));}
function icon(r){const img=node('img');img.src=r.preview;img.alt=r.name+' 아이콘';return img;}
function renderList(){const visible=rows.filter(matches);$('count').textContent=visible.length+'개';const list=$('list');list.replaceChildren();if(!visible.length){list.append(node('p','no-results','검색 결과가 없습니다.'));return;}
 for(const r of visible){const b=node('button','card'+(selected===r.id?' selected':''));b.append(icon(r));const txt=node('div');txt.append(node('strong','',value(r,'name')),node('small','',labels[value(r,'category')]+' · '+(edits[r.id]?.reviewed?'검토 완료':r.origin==='proposal'?'제안':r.origin==='user'?'직접 수정':r.hasYaml?'기존':'미작성')));b.append(txt);b.onclick=()=>{selected=r.id;renderDetail();renderList();};list.append(b);}
}
function makeField(r,f,label,multi=false){
 const wrap=node('div','field');wrap.append(node('label','',label));const control=node(multi?'textarea':'input');control.value=value(r,f);control.setAttribute('aria-label',label);control.dataset.field=f;
 if(['mana','cooldown','order'].includes(f)){control.type='number';control.min=f==='order'?-1000000:0;control.max=f==='cooldown'?86400:1000000;control.step='1';}
 else if(f==='category'){control.remove();const select=node('select');for(const [v,t] of Object.entries(labels)){const o=node('option','',t);o.value=v;select.append(o);}select.value=value(r,f);select.onchange=()=>change(r,f,select.value);wrap.append(select);return wrap;}
 control.oninput=()=>change(r,f,['mana','cooldown','order'].includes(f)?Number(control.value):control.value);
 wrap.append(control);return wrap;
}
function renderDetail(){
 const r=byId.get(selected),detail=$('detail');detail.replaceChildren();if(!r)return;
 const head=node('div','detail-head');head.append(icon(r));const title=node('div');title.append(node('span','badge '+r.origin,r.originLabel),node('h2','',value(r,'name')),node('p','',labels[value(r,'category')]+' · ID '+r.id));head.append(title);detail.append(head);
 const columns=node('div','columns');columns.append(node('div','column-title','기존 내용'),node('div','column-title final','수정안 · 직접 편집 가능'));
 for(const [f,label] of [['name','이름'],['condition','획득 조건'],['description','마법 효과'],['research','연구 기록']]){
  const group=node('div','field');group.append(node('label','',label),node('div','before'+(r.baseline[f]===value(r,f)?' dim':''),r.baseline[f]||'아직 작성되지 않았습니다.'));columns.append(group,makeField(r,f,label,f!=='name'));
 }
 detail.append(columns);
 const settings=node('div','pair');settings.append(makeField(r,'mana','마나 소모량'),makeField(r,'cooldown','쿨타임 (초)'));detail.append(settings,makeField(r,'command','시전 명령어 (/ 없이)'));
 const previous=node('div','note',r.hasYaml?'기존 마나 '+r.baseline.mana+' · 쿨타임 '+r.baseline.cooldown+'초 · 명령 '+r.baseline.command:'신규 아이콘 기획 단계입니다. 마나·쿨타임 0은 미작성 초기값입니다.');detail.append(previous);
 const todo=data.backlog.find(b=>b.id===r.id);if(todo)detail.append(node('div','note','실제 구현에서 남은 부분: '+todo.task));
 if(r.origin==='proposal'&&!todo)detail.append(node('div','note','제안은 자동으로 검토 완료 처리하지 않았습니다. 획득 조건은 표시 문구이며, 행동·수업·독서 판정은 별도로 연결해야 합니다.'));
 const approve=node('label','approve'),checkbox=node('input');checkbox.type='checkbox';checkbox.id='reviewed';checkbox.checked=Boolean(edits[r.id]?.reviewed);checkbox.onchange=()=>{const next={condition:value(r,'condition'),...(edits[r.id]??{}),reviewed:checkbox.checked};if(!validEdit(r.id,next)){checkbox.checked=false;$('save-state').textContent='검토 완료 전에 입력값을 확인해 주세요';return;}edits[r.id]=next;persist();renderList();};approve.append(checkbox,node('span','','내용 검토 완료 (게임 기능 구현 완료와 별도)'));detail.append(approve);
}
document.querySelectorAll('#tabs button').forEach(b=>b.onclick=()=>{scope=b.dataset.scope;document.querySelectorAll('#tabs button').forEach(x=>x.classList.toggle('active',x===b));const next=rows.find(matches);if(next&&!matches(byId.get(selected)))selected=next.id;renderList();renderDetail();});
$('search').oninput=renderList;$('category').onchange=renderList;
$('export').onclick=()=>{
 if(!Object.entries(edits).every(([i,e])=>validEdit(i,e))){$('save-state').textContent='내보내기 전에 입력값을 확인해 주세요';return;}
 const draft=JSON.parse(JSON.stringify(data.draft));draft.edits=JSON.parse(JSON.stringify(edits));draft.exportedAt=new Date().toISOString();draft.revision=0;
 for(const id of Object.keys(edits))if(!draft.baseline[id]){const r=byId.get(id);draft.baseline[id]={...r.baseline,file:r.file,sha256:r.sha256};}
 const blob=new Blob([JSON.stringify(draft,null,2)+'\n'],{type:'application/json;charset=utf-8'}),url=URL.createObjectURL(blob),a=node('a');a.href=url;a.download='차카데미-마법수정-스타일반영.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),5000);$('save-state').textContent='수정본을 내보냈습니다.';
};
renderList();renderDetail();
})();
