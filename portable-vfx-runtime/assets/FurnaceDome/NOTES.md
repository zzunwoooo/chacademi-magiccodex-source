# FurnaceDome (화로 돔) — 변환 메모

- 시스템: `main` 1개, role **static**. P = 지정 지점(바닥 중심), local +Y 위, +Z = 시전자 수평 방향(서버 기준).
- 런타임 입력: `areaRadius` (SCENE.scale reference 5 m, min 0.5, max 1.6 — 배율). 돔 반경 5 m, 높이 4.2 m.
- 시간표 (s): 0.0 바닥 화염 마법진 · 0.3~0.9 육각 화로 돔이 마법진에서 부풀어 오름 · 0.9~4.8 화로 내부(소용돌이 불꽃, 열기둥, 그을음, 연기) ·
  **1.0 / 2.0 / 3.0 / 4.0 열 펄스(돔 번쩍 + 바닥 링 수축 + 불기둥) = 지속 피해 틱** · 4.8 돔이 중심으로 오그라들며 닫힘(빛 모임) · **5.2 폭발 = 마무리 피해** · 6.4 종료.
- 피해 권장: 돔 안 적에게 1.0~4.0 s 매 1 s, 5.2 s 폭발 피해(반경 5 m).
- 돔 표면은 6개 구역 메쉬(각 U 0..1)로 육각 텍스처 반복. 모두 mesh particle + additive.
- 고유 텍스처 RGBA: 2.72 MiB. client_compat check: OK (fix 0 change).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
