import json, html
B = [("n4","차큐스 100","일반 · 기본 궤적 (비교용)","#9aa0ad"),
 ("r1","차큐스 200","희귀 · 기본+ (반짝이)","#5fa8ff"),("r2","차큐스 300","희귀 · 기본+ (반짝이)","#5fa8ff"),
 ("r3","고급스러운 빗자루","희귀 · 금빛","#e8b84a"),("r4","차큐스 레이서 1","희귀 · 물결","#4fd6ff"),("r5","차큐스 레이서 2","희귀 · 센 물결","#2f8bff"),
 ("r6","차큐스 400 (참나무)","희귀 · 참나무 잎","#8cbf4a"),("r7","차큐스 400 (벚나무)","희귀 · 벚꽃잎","#ff9cc0"),
 ("r8","차큐스 400 (자작나무)","희귀 · 자작나무 잎","#d8d060"),("r9","차큐스 400 (마호가니)","희귀 · 마호가니 잎","#c0603c"),
 ("r10","차큐스 400 (대나무)","희귀 · 대나무 잎","#7fc24a"),("r11","초보 마녀의 빗자루","희귀 · 은하수","#8f7bff"),
 ("u1","차큐스 500","유일 · 어두운 고급","#f06eaa"),("u2","차큐스 500 (미드나잇)","유일 · 클래식 고급","#f06eaa"),("u3","차큐스 500 (에메랄드)","유일 · 초록 + 에너지 고리","#f06eaa"),
 ("u4","차큐스 500 (크림슨)","유일 · 빨강 + 에너지 고리","#f06eaa"),("u5","쏜살같은 빗자루","유일 · 소닉붐 고리","#f06eaa"),("u6","건메탈 레이서","유일 · 홀로그램 물결","#f06eaa"),
 ("u7","바이올렛 트윈테일","유일 · 고리 두 줄 + 보라","#f06eaa"),
 ("l1","마법소녀 빗자루","전설 · 핑크 하트 + 컨페티","#ffbe3c"),("l2","타락한 마법소녀 빗자루","전설 · 검은 하트 + 컨페티","#ffbe3c"),("l3","드래고니아","전설 · 파란 드래곤 브레스","#ffbe3c"),
 ("l4","블러디 플라이","전설 · 빨간 홀로그램 나비","#ffbe3c"),("l5","아홉꼬리 빗자루","전설 · 하늘색 여우불","#ffbe3c"),("l6","차큐스 1000 (엔젤)","전설 · 천사 깃털","#ffbe3c"),
 ("l7","차큐스 1000 (데빌)","전설 · 악마 불꽃","#ffbe3c")]
PFX = {"n":"RareTrail_","r":"RareTrail_","u":"UniqueTrail_","l":"LegendTrail_"}
docs = {b: open(f"final/{PFX[b[0]]}{b}/preview.html", encoding="utf-8").read() for b, *_ in B}
tabs = "".join(f'<button class="tab" data-id="{b}" style="--g:{c}"><b>{html.escape(n)}</b><span>{html.escape(d)}</span></button>' for b, n, d, c in B)
page = f'''<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>빗자루 궤적 미리보기</title>
<style>
:root{{--bg:#14161c;--fg:#e8eaf0;--mut:#9aa0ad;--card:#1c1f27;--line:#2a2e38}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--bg);color:var(--fg);font:14px/1.5 system-ui,"Malgun Gothic",sans-serif}}
header{{padding:14px 16px 4px}}h1{{font-size:17px;margin:0 0 4px}}p{{margin:0;color:var(--mut);font-size:13px}}
.tabs{{display:flex;flex-wrap:wrap;gap:6px;padding:10px 16px}}
.tab{{background:var(--card);color:var(--fg);border:1px solid var(--line);border-left:3px solid var(--g);border-radius:8px;padding:6px 10px;cursor:pointer;text-align:left;font:inherit}}
.tab b{{display:block;font-size:13px}}.tab span{{color:var(--mut);font-size:11px}}
.tab.on{{background:#262a35;border-color:var(--g)}}
iframe{{display:block;width:calc(100% - 32px);margin:0 16px 16px;height:min(72vh,640px);border:1px solid var(--line);border-radius:10px;background:#000}}
</style></head><body>
<header><h1>희귀 · 유일 · 전설 빗자루 궤적 (1차)</h1>
<p>실제 빗자루 모델 + 새 궤적. 각 빗자루의 최고 속도(새 능력치)와 부스트 ×1.45로 날아요. 미리보기 안의 버튼으로 상황(최고속·선회·저속)과 카메라 전환, 왼쪽 메뉴는 접을 수 있어요.</p></header>
<div class="tabs">{tabs}</div>
<iframe id="view" title="궤적 미리보기"></iframe>
<script>
const DOCS={json.dumps(docs).replace("</", "<\\/")};
const f=document.getElementById('view');
function show(id){{document.querySelectorAll('.tab').forEach(t=>t.classList.toggle('on',t.dataset.id===id));f.srcdoc=DOCS[id];try{{localStorage.setItem('broomTrailTab2',id)}}catch(e){{}}}}
document.querySelectorAll('.tab').forEach(t=>t.onclick=()=>show(t.dataset.id));
let s='r1';try{{s=localStorage.getItem('broomTrailTab2')||'r1'}}catch(e){{}}show(DOCS[s]?s:'r1');
</script></body></html>'''
open("final/BroomTrails_Preview.html", "w", encoding="utf-8").write(page)
print(len(page)//1024, "KB")
