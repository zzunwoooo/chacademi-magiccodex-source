# BlackMoth (검은 나방) — 어둠 초급, 변환 메모

- 시스템 2개: `projectile` (projectile, loop 없음, duration 6 s, 8층) + `impact` (impact, 1.3 s, 10층).
- 투사체 하나 = 나방 무리 하나. **나방 3마리는 투사체 안의 로컬 입자** (t=0 에 정확히 3개, 수명 6 s, 중심 주위를 반지름 0.38 m 로 천천히 돌며 noise 로 팔랑임, 날갯짓 플립북 2×2 를 수명 동안 36회). 모드는 투사체 1개를 적에게 유도(호밍)하면 됨 — 3마리가 같이 따라감. 최대 비행 6 s (늘리려면 duration·수명만 늘리면 됨, 최대 60 s).
- 투사체 연출: 먹빛 몸체 + 라벤더 테두리·눈알 무늬 나방(알파 블렌드, HDR tint), 떨어지는 인분 가루·반짝이, 먹빛 안개·날개 조각, 라벤더 바람 줄기 (world).
- 충돌 (hitPoint + surfaceNormal, 공중 = −진행방향): **0 s 피해 권장** — 섬광, 나방 3마리가 흩어지며 작아져 사라짐, 인분 가루 구름(50), 반짝이, 표면에 저주 문양(육망성 시길 1.0 s 회전 + 선 마법진), 고리, 먹빛 날개 조각·안개, 바람 줄기.
- 런타임 입력: 기본 projectile/impact 입력만 (scale 미사용).
- 박쥐 떼(BatFlurry)와 같은 구조 (한 무리 = 한 투사체). 각 나방을 다른 대상에게 보내려면 별도 버전 필요.
- client_compat check OK. 고유 텍스처 RGBA 1.24 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
