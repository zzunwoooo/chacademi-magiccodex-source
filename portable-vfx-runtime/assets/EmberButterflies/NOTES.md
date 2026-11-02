# EmberButterflies (불나비 떼) — 변환 메모

- 시스템 3개:
  - `cast` (role **attached**, 시전자 발 위치 + 수평 방향, 1.6 s): 발밑 화염 마법진, 등 뒤 거대 나비 날개 실루엣, 불꽃 고치, 0.45 s 섬광과 함께 나비가 흩어짐.
  - `fly` (role **projectile**): **나비 1마리 = 인스턴스 1개, 총 12개.** 권장 발사 시각 = 시전 후 0.5 + 0.05·k s (k = 0..11), 시작점 = 시전자 가슴 높이 주변(시전자 기준 반경 0.7 m, 높이 1.2~1.7 m).
    모드가 각 나비를 적에게 유도(호밍)해서 움직인다 (권장: 처음 0.3 s 는 옆·위로 흩어졌다가 적을 향해 곡선 비행, 비행 약 1 s). 파티클 수명 3 s → 3 s 안에 명중/소멸.
    날개는 2x2 날갯짓 플립북 빌보드(cycles 33 ≈ 초당 11번), 꼬리는 per-particle `trail`.
  - `pop` (role **impact**): 나비가 적에 닿은 지점에서 생성. 적 명중은 공중 명중 규약(surfaceNormal = −incomingDirection). 대상이 없으면 3 s 뒤 그 자리에서 pop 을 재생하거나 그냥 정지.
- 적 배정 권장: 범위 내 적들에게 순서대로 나눠 배정(적 3명이면 4마리씩), 적이 없으면 시전자 앞 무작위 지점.
- 피해 권장: 나비 1마리 명중 = 1회 피해 (pop 0 s).
- 미리보기는 events 모드(나비 12마리 = projectile 이벤트 12개, via 로 흩어지는 곡선)로 모드 동작을 흉내낸 것이다.
- 고유 텍스처 RGBA: 2.66 MiB. client_compat check: OK (fix 0 change).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
