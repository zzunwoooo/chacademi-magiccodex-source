# MoonbeamDart (달빛 화살) — 어둠 초급, 변환 메모

- 시스템 2개: `projectile` (projectile, loop 없음, duration 6 s, 10층) + `impact` (impact, 1.2 s, 12층).
- 투사체: 화살 몸체는 진행 방향(+Z)을 따라 교차된 메쉬 두 장(달빛 화살촉 텍스처) + 흰 코어, 화살 뒤쪽에 반대로 도는 초승달 고리 2개, 화살촉 반짝임. 화살·고리는 t=0 에 1개만 생기고 수명 6 s → **최대 비행 6 s** (더 길면 duration·수명만 늘리면 됨, 최대 60 s).
  - 궤적(world): 잔상 화살(라벤더, 0.16 s), 빛 알갱이, 작은 초승달, 먹빛 깃털 조각.
- 충돌 (hitPoint + surfaceNormal, 공중 = −진행방향): **0 s 피해 권장** — 섬광, 표면에 작은 팔각 달 마법진이 펼쳐졌다 0.9 s 에 사라짐(회전), 초승달 고리 회전, 충격 고리, 달빛 광선 16줄, 빛/먹빛 초승달 조각, 결정 파편, 알갱이, 안개.
- 런타임 입력: 기본 projectile/impact 입력만 (scale 미사용).
- client_compat check OK. 고유 텍스처 RGBA 1.39 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
