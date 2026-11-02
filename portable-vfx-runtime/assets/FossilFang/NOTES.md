# FossilFang (화석 송곳니) — 변환 메모

- 시스템 `main` (static): P = 지정한 바닥 지점(+Y 위), 서버가 넘겨주는 시전자 수평 방향 기준. 길이 1.5 s.
- 시간표: 0.00 예고(그림자·균열 데칼·땅 마법진 선/룬 띠, 모래 흘러내림) → 0.30 화석 송곳니 9개가 바깥으로 벌어진 채 솟음(모래 폭발·조약돌) → **0.50 s 물기**(송곳니가 안쪽으로 닫혀 끝이 모임 + 안쪽 작은 송곳니, 섬광·안쪽 집중선·충격파 2겹·뼛조각) → 0.50–0.95 다문 채 유지 → 0.95 가라앉으며 부스러짐 → 1.5 끝.
- 피해 권장 시점: 0.50 s (반경 약 1 m, areaRadius 기준).
- 런타임 입력: `areaRadius` (scale reference 1.2 m, 배율 min 0.6 / max 1.6).
- 메쉬: `rings`, `runes`(8칸 룬 띠), `jawOpen`/`jawShut`(9개 송곳니 링, 십자 쿼드, 벌림 −18° / 다묾 +42°), `jawInner`(7개 작은 송곳니). 물기 순간은 벌린 턱 → 다문 턱을 바로 바꿔 끼우는 방식(턱 모양 변형 기능 불필요).
- 어두운 강조: 바닥 그림자(`shade`), 균열 데칼(`crack`), 어두운 먼지(`dust`), 돌(`rock`). 송곳니 본체는 알파 블렌드 뼈(뿌리 쪽 흙물든 갈색 → 상아), 가장자리 호박색.
- client_compat check: OK (FIX/WARN 없음). 고유 텍스처 RGBA 1.08 MiB (12장, 전용 T_Fang 64×192 포함).
- 층 23개 (시스템 1개).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
