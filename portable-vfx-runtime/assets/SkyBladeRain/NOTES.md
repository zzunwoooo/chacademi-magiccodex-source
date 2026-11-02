# SkyBladeRain (칼날비) — 바람 고급, 변환 메모

- 시스템 1개: `main` (static, 4.9 s + 잔상 ~1 s). P = 지정한 지점 바닥, 위 = +Y. 반경 4 m 기준.
- 런타임 입력: `areaRadius` (scale: reference 4, min 0.5, max 1.5 배 → 반경 2~6 m; 하늘 봉인 높이 9 m 도 같은 비율).
- 시간표 (s):
  - 0~0.9 바닥 표적 마법진(반경 4 m) + 하늘 봉인(높이 9 m, 반경 4.6 m)·먹빛 구름
  - **0.9~3.9 칼날비**: 바람 칼날(빛 조각·초승달 칼날·먹빛 칼날·빛줄기)이 26 m/s 로 떨어짐, 매초 밀도 증가 (0.1 s 마다 3 → 5 → 7 묶음).
    칼날이 바닥에 닿는 시각(낙하 0.33 s) 에 맞춰 섬광·바닥 베임 자국·튀는 불꽃·먼지
  - **3.9 거대 칼날이 중심에 내리꽂힘** (섬광·충격파 2겹·광선·먹빛 파편) → 4.9 종료.
- 피해 권장: 1.25 ~ 4.2 s 동안 구역 안 적에게 0.25 s 마다 틱 피해 (칼날 착지 리듬), 3.9 중심 강타 1회(중심 반경 2 m 추가).
- client_compat check OK (fix: 0 change). 고유 텍스처 RGBA 2.3 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
