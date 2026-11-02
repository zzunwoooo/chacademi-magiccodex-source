# FireLotus (불꽃 연꽃) — 변환 메모

- 시스템: `main` 1개, role **static**. P = 지정 지점(바닥), local +Y 위, +Z = 시전자 수평 방향(서버 기준).
- 런타임 입력: `areaRadius` (SCENE.scale reference 6 m, min 0.5, max 1.5 — 배율). 연꽃 지름 약 4 m, 꽃잎 불길 도달 반경 6 m.
- 시간표 (s): 0.0 바닥 화염 마법진 + 바닥 꽃잎 문양 · 0.2~1.2 불꽃 연꽃이 봉오리 → 반개 → 만개 (메쉬 3단계 교차 페이드) ·
  1.15~1.6 충전(빛 모임, 링 수축, 꽃잎 달아오름) · **1.6 중심 폭발 = 1차 피해(반경 약 2 m)** ·
  **1.65~2.6 꽃잎 불길이 8방향(+사이 8방향)으로 바깥까지 퍼짐 = 2차 피해(반경 6 m, 퍼지는 불 선 위)** · 2.85 연꽃이 재로 타 버림 · 4.4 종료.
- 피해 권장: 1.6 s 중심 폭발, 1.7~2.4 s 사이 퍼지는 불길(거리에 비례해 늦게: 약 r/7 s 지연).
- 불 선은 2x8 스트로크 와이프 플립북(바깥으로 번짐), 퍼지는 꽃잎 칼날은 메쉬 크기 곡선으로 확장.
- 고유 텍스처 RGBA: 2.53 MiB. client_compat check: OK (fix 0 change).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
