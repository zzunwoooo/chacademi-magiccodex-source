# CastingMold (주물 거푸집) — 변환 메모

- 시스템: `main` 1개, role **static**. P = 대상(적)의 발 위치, local +Y 위, +Z = 시전자 수평 방향(서버 기준). 대상 위치에서 시전 즉시 시작.
- 런타임 입력: `targetHeight` (SCENE.scale reference 1.8, min 0.7, max 2.4 — 배율). 거푸집 높이 2.3 m·반경 0.82 m 가 대상 키에 맞춰 같이 커진다.
- 시간표 (s): 0.0 바닥 화염 마법진 그려짐 + 쇳물 웅덩이 · 0.15 철 거푸집(판 8장)이 2.6 m 위에서 낙하 · **0.6 거푸집 착지 = 고정(이동 불가) 시작** · 0.7~2.6 쇳물 주입, 수위 상승, 룬 띠 회전, 불꽃·연기 점증 · 2.15 예고(링 수축, 빛 모임, 마법진 플레어) · **2.6 폭발 = 피해** (판이 튕겨 나감, 쇳덩이·쇳물 파편, 섬광, 파동) · 3.9 종료.
- 피해 권장 시점: 고정 0.6~2.6 s (2 s), 폭발 피해 2.6 s.
- 거푸집 낙하는 gravity 로 계산(0.45 s 동안 2.6 m), 0.6 s 에 같은 메쉬의 착지 사본으로 교대.
- 고유 텍스처 RGBA: 2.15 MiB (17장, 최대 512 px).
- client_compat check: OK (fix 0 change).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
