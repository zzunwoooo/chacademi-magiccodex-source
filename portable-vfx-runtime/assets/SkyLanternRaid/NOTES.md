# SkyLanternRaid (풍등 폭격) — 변환 메모

- 시스템: `main` 1개, role **static**. P = 지정한 지점(바닥), local +Y 위, +Z = 시전자 수평 방향(서버 기준). 지정 즉시 시작.
- 런타임 입력: `areaRadius` (SCENE.scale reference 3.2 m, min 0.6, max 1.6 — 배율). 착탄 지점 6곳은 오각형 반경 2.2 m + 중심.
- 시간표 (s): 0.0 바닥 화염 마법진 · 0.3~0.9 풍등 6개가 마법진에서 떠올라 약 7 m 까지 상승(drag) · 1.5 착탄 지점 표식 깜빡임 ·
  1.9 + 0.3k (k=0..4) 풍등이 차례로 0.35 s 동안 급강하 → **착탄 2.25 / 2.55 / 2.85 / 3.15 / 3.45 s** (각 오각형 지점) ·
  3.6 마지막(큰) 풍등 중심으로 강하 → **3.95 s 중심 대폭발** · 5.2 종료.
- 피해 권장 시점: 위 6개 착탄 시각에 각 지점 반경 약 1.2 m(중심은 2 m).
- 상승 풍등의 끝 위치는 SCHEMA §4.2 고정 스텝(drag)으로 미리 계산해 강하 사본을 그 자리에서 이어 붙였다 (풍등 1개 = 상승 층 + 강하 층).
- 강하 풍등은 per-particle `trail`(trails 기능) 사용.
- 층 수 61 (고급 목표 35~55 보다 많음: 풍등 6개 × 상승·강하·착탄 5층이 각자 위치를 가져야 해서).
- 고유 텍스처 RGBA: 1.90 MiB. client_compat check: OK (fix 0 change).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
