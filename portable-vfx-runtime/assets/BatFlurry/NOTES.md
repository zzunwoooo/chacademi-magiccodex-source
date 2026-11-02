# BatFlurry (박쥐 떼) — 어둠 초급, 변환 메모

- 시스템 2개: `projectile` (projectile, loop 없음, duration 6 s, 9층) + `impact` (impact, 1.2 s, 12층).
- 투사체 하나 = 박쥐 떼 하나. **박쥐 4마리는 투사체 안의 로컬 입자** (t=0 에 정확히 4개 생성, 수명 6 s, 투사체 중심을 반지름 0.55 m 로 빙빙 돌며 noise 로 퍼덕임, 날갯짓 플립북 2×2 를 수명 동안 60회 반복). 모드는 투사체 1개를 적에게 유도(호밍)해서 움직이면 됨 — 4마리가 같이 따라감.
  - 따라서 비행이 6 s 를 넘으면 박쥐가 사라짐 → 최대 비행 시간 6 s 이하로 운용 (더 길게 필요하면 duration/수명만 늘리면 됨, 최대 60 s).
  - 박쥐 4마리를 각각 다른 대상에게 보내려면 투사체를 4개 쏘는 대신 이 팩을 그대로 쓰면 안 되고 별도 버전이 필요함 (이번 버전은 한 무리로 한 대상을 쫓음).
- 투사체 연출: 진홍/라벤더 소용돌이 고리(진행 방향 축 회전), 약한 초음파 고리, 진홍 눈빛 입자·먹빛 안개·깃털 조각 궤적(world).
- 충돌 (SCHEMA §5: hitPoint + surfaceNormal, 공중 = −진행방향): **0 s 피해 권장** — 진홍 송곳니 베기 3번(0 / 0.06 / 0.12 s), 섬광, 초음파 고리 2개, 피 방울·불꽃, 먹빛 안개·조각, 0.08 s 에 박쥐 4마리가 위로 흩어지며 날아감, 빛 알갱이.
- 런타임 입력: 기본 projectile/impact 입력만 (scale 미사용).
- 박쥐: 먹빛 몸체 + 진홍 테두리 (알파 블렌드, HDR tint 2.2 로 테두리가 블룸).
- client_compat check OK. 고유 텍스처 RGBA 1.34 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
