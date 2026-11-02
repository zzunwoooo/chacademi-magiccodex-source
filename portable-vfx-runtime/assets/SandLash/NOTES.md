# SandLash (모래 채찍) — 변환 메모

- 시스템 `lash` (attached): 시전자 발 위치 + 수평 방향(+Z 정면, +X 오른쪽)을 매 프레임 따라간다. 길이 1.1 s, loop 없음.
- 시간표: 0.00 손 빛 + 발밑 모래 소용돌이 → 0.06 1타 (오른쪽→왼쪽 수평, 가슴 높이) → **0.13 s 1타 피해** (채찍 끝이 정면 2.2 m 통과, 섬광·모래 폭발·조약돌·바닥 파동) → 0.40 2타 (왼쪽→오른쪽 사선) → **0.47 s 2타 피해** (+ 바닥 균열 데칼) → 1.1 s 소멸.
- 피해 권장 시점: 0.13 s, 0.47 s (정면 반경 약 2.4 m 부채꼴).
- 런타임 입력: attached 표준(발 위치 + 수평 방향)만. scale 없음.
- 메쉬: `lashR/lashL` (호 모양 리본, 반경 1.6→2.3 m), `coreR/coreL` (얇은 심선), 툴킷 기본 `ground`, `quad`. 플립북 2×8 와이프.
- 어두운 강조: `strokeInk`(어두운 모래 채찍 잔상), `dust`(어두운 모래 먼지, 알파), `rock`(어두운 돌 + 밝은 테두리), `crack`(갈라진 땅).
- client_compat check: OK (FIX/WARN 없음). 고유 텍스처 RGBA 1.47 MiB (10장).
- 층 28개 (시스템 1개).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
