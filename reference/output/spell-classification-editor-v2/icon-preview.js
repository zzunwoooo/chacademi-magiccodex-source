// Hosting migration: original full-size icon assets are not included.
const hostingArtFallback="data:image/svg+xml;charset=utf-8,"+encodeURIComponent("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"256\" height=\"256\" viewBox=\"0 0 256 256\"><rect width=\"256\" height=\"256\" rx=\"20\" fill=\"#152333\"/><path d=\"M128 42l15 60 60 26-60 26-15 60-15-60-60-26 60-26z\" fill=\"#ddbd7e\"/><text x=\"128\" y=\"239\" fill=\"#becbd3\" font-size=\"14\" text-anchor=\"middle\">원본 아이콘 미이전</text></svg>");
window.addEventListener("error",event=>{const img=event.target;if(img instanceof HTMLImageElement&&/\/icons\//.test(img.src)){img.src=hostingArtFallback;img.alt="원본 아이콘은 아직 호스팅에 이전하지 않았습니다.";}},true);
// Original spell art only. Editing a concept does not change its image binding.
window.createSpellArtPreview=function({getSpell,getName}){
 const cards=[];
 const dialog=document.createElement('dialog');dialog.className='art-dialog';dialog.setAttribute('aria-labelledby','art-dialog-title');
 const bar=document.createElement('div');bar.className='art-dialog-bar';
 const title=document.createElement('strong');title.id='art-dialog-title';
 const close=document.createElement('button');close.type='button';close.textContent='닫기';close.setAttribute('aria-label','아이콘 확대 닫기');close.onclick=()=>dialog.close();bar.append(title,close);
 const zoom=document.createElement('img');zoom.className='art-zoom-image';zoom.decoding='async';
 const note=document.createElement('p');note.className='hint';note.textContent='현재 마법에 연결된 원본 아이콘이야. 이름이나 효과를 수정해도 그림은 그대로 유지돼.';
 dialog.append(bar,zoom,note);document.body.append(dialog);
 dialog.addEventListener('click',event=>{if(event.target===dialog){const r=dialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)dialog.close();}});
 function enlarge(){const spell=getSpell();if(!spell)return;title.textContent=getName()+' · 마법 아이콘';zoom.src=spell.preview;zoom.alt=getName()+' 원본 아이콘';if(!dialog.open)dialog.showModal();}
 function card(className){
  const figure=document.createElement('figure');figure.className='spell-art '+className;
  const button=document.createElement('button');button.type='button';button.className='spell-art-button';button.setAttribute('aria-label','마법 아이콘 크게 보기');button.onclick=enlarge;
  const img=document.createElement('img');img.decoding='async';button.append(img);
  const caption=document.createElement('figcaption');
  const label=document.createElement('strong');label.textContent='현재 마법 아이콘';
  const hint=document.createElement('span');hint.textContent='눌러서 크게 보기';
  caption.append(label,hint);figure.append(button,caption);
  const error=document.createElement('p');error.className='art-error';error.hidden=true;error.textContent='아이콘을 불러오지 못했어.';figure.append(error);
  img.onload=()=>{error.hidden=true;button.disabled=false;};img.onerror=()=>{error.hidden=false;button.disabled=true;};
  cards.push({figure,button,img,label,error});return figure;
 }
 const inline=card('spell-art-inline');document.querySelector('#editor .spell-heading').after(inline);
 const side=card('spell-art-side');document.querySelector('.summary-panel').prepend(side);
 return {
  select(spell){for(const c of cards){c.figure.hidden=!spell;if(!spell)continue;c.error.hidden=true;c.button.disabled=false;if(c.img.getAttribute('src')!==spell.preview)c.img.src=spell.preview;}this.rename();},
  rename(){const spell=getSpell();if(!spell)return;for(const c of cards){c.img.alt=getName()+' 마법 아이콘';c.label.textContent=getName()+' 아이콘';}}
 };
};
