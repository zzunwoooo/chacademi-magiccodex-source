# FlameLoom (불실 그물) — 변환 메모

- 시스템 2개: `projectile` (role projectile, 비행 중 불실 그물; local +Z = 진행 방향, 그물은 local XY 면에 펼쳐져 회전) → `impact` (role impact, 명중 시 hitPoint 에 생성, local +Y = surfaceNormal).
- 투사체: 시전 시 새 인스턴스, 명중 시 local 층 제거·world 층 방출 중지 후 impact 생성 (Fireball 과 동일 규약). duration 4 s(비행 4 s 이내 가정, 그물 1개 파티클 수명 4 s).
- impact 시간표 (s): 0.0 섬광 + 그물이 1.7배 크기에서 덮쳐 0.35 s 에 감김 · **0.35~3.0 속박(감속) + 지속 화상** — 실이 깜빡이며 타고, 불길이 그물을 타고 기어오름, 쇳물 방울·불씨 · 1.0 / 2.0 조임 펄스(지속 피해 틱 권장) · **3.0 그물이 타 버리며 파편** · 3.8 종료.
- 피해 권장: 명중 0 s 첫 피해, 0.35~3.0 감속 + 1.0/2.0 화상 틱, 3.0 마무리 피해(선택).
- 바닥(적 발밑) 명중이 가장 자연스럽다: 적을 맞혔을 때는 적 발 위치 + normal (0,1,0) 으로 impact 를 생성하길 권장. 공중 명중(normal = −incoming)이면 돔이 시전자 쪽을 향해 덮인다.
- 런타임 입력: projectile = 위치 + 진행 방향, impact = hitPoint / surfaceNormal / incomingDirection. scale 없음.
- 고유 텍스처 RGBA: 2.06 MiB. client_compat check: OK (fix 0 change).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
