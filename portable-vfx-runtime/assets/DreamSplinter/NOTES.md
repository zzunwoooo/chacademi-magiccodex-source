# DreamSplinter (꿈 조각) — 어둠 초급, 변환 메모

- 시스템 3개:
  - `projectile` (projectile, loop, 8층): 회전하는 꿈 수정 조각 (분홍-라벤더 결정 + 먹빛 결정 겹침, 작은 소용돌이 무늬, 꿈빛 리본 회전), 궤적에 반짝이 별·알갱이·꿈 안개(world).
  - `impact` (impact, 1.0 s, 8층): 명중/바닥/벽 공통. **0 s 피해 권장** — 섬광, 꿈 소용돌이가 커지며 회전, 고리 2개, 결정·먹빛 파편, 불꽃, 반짝이, 안개.
  - `daze` (attached, 1.5 s, 11층): **맞은 몹**의 발 위치에 붙음 (+Z = 몹이 바라보는 방향). 몹을 맞혔을 때만 모드가 시작 (바닥·벽 명중 시에는 시작하지 않음). 몹이 엉뚱한 방향으로 걷는 이동은 모드가 처리하고, 이 연출은 몹을 따라다님.
    - 머리 위(2.15 m) 어지러움 고리 + 작은 오망성 원이 반대로 회전, 별 3개·작은 초승달 2개가 머리 주위를 돎, 머리 뒤 소용돌이, 몸 주위 꿈 안개·알갱이, 발밑 소용돌이 문양.
    - 1.35 s 에 '깨어남' 반짝 + 결정 파편 (혼란 종료 신호), 1.5 s 끝.
    - 몸집이 큰 몹은 머리 위 고리가 몸 안에 묻힐 수 있음 (targetHeight scale 미사용, 1.8 m 기준).
- 런타임 입력: 기본 projectile/impact/attached 입력만.
- 미리보기: 시전자(왼쪽)와 몹(오른쪽), 명중 후 몹이 엉뚱한 방향으로 걷는 데모 (`events`/`actors`, 미리보기 전용).
- client_compat check OK. 고유 텍스처 RGBA 1.33 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
