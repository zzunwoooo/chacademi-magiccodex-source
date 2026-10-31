# 하베스트(호박 리퍼) 보스 모델 소스

- `../pumpkin_reaper.bbmodel` / `../pumpkin_reaper_shiny.bbmodel` — Modded Entity · Box UV · 큐브 80개 · 애니 26개 · 숨김 히트박스(1.6×3.3, 눈 2.75). ModelEngine 모델 id `pumpkin_reaper`.
- `../motion_preview.html` — 브라우저 3D 미리보기 (애니 목록 재생, 기본/이로치, 발광, 어둡게).
- `models.py` (모델·팔레트·이로치), `anims.py` (애니 26개), `gen.py`·`render.py` (mc-mob-bbmodel 스킬 원본 그대로), `run.py` (export), `build_html.py` + `viewer_template.html` (미리보기).

다시 만들기: `python3 run.py` → `/mnt/user-data/outputs/pumpkin_reaper(_shiny).bbmodel` (run.py 의 출력 경로 O 를 바꿔도 됨), 미리보기 `python3 build_html.py`.
발광 본: `face_glow`, `robe_glow`, `*_sleeve_glow`, `scythe_glow`. 애니 이름은 Skills yml 의 state{s=...} 와 같다.
소품 8종: `../props/*.bbmodel` — `props.py` 로 생성 (`python3 props.py <출력폴더>`). 호박 얼굴 면은 결무늬 없이 + 파낸 곳 둘레를 어둡게 해 멀리서도 표정이 읽히게 함. 히트박스 숨김.
