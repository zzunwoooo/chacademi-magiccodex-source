# MoonShard (달 조각) — 어둠 초급, 변환 메모

- 시스템 3개 + `salvo` (3발 고정):
  - `cast` (attached, 0.5 s, 8층): 시전자 손 앞(발 기준 0, 1.5, 0.62)에 작은 달(빛 초승달 + 먹빛 초승달 + 세로 육망성 원)이 맺힘 → **0.22 s 금이 가며 깨짐** (별 섬광·파편·고리).
  - `projectile` (projectile, loop 없음, 6 s, 8층): 달 조각 하나 = 투사체 하나. 빛 결정 + 먹빛 결정이 반대로 회전, 진행축 주위로 도는 초승달 고리, 궤적 빛줄기·알갱이·먹빛 가루(world). 조각·고리는 수명 6 s → 최대 비행 6 s.
  - `impact` (impact, 1.0 s, 9층): 조각마다 1개. **0 s 피해 권장** — 섬광, 달 고리(표면), 충격 고리, 광선, 파편, 빛/먹빛 초승달, 알갱이, 안개.
- salvo: `fireStart` 기본 0.25 s (0.05~2), `interval` 기본 0.03 s (0.001~0.3 — 0.001 이면 사실상 동시 발사). 슬롯(시전자 기준) 왼(-0.22, 1.48, 0.66) / 가운데(0, 1.58, 0.70) / 오른(0.22, 1.48, 0.66), 조준 yaw −0.3 / 0 / +0.3 rad (약 ±17° 부채꼴), pitch 약 −0.03~−0.05 rad (살짝 위). stopSystems 없음 (대기 시스템 없음).
- 런타임 입력: fireStart, fireInterval, 각 발의 projectile/impact 기본 입력. 속도·사거리는 서버 바인딩.
- client_compat: fix 가 export 기본값(null)인 salvo.aimMode 등을 지움 (aimMode 기본 'slot' 과 동일 → **보이는 변화 없음**). check OK.
  - 툴킷 `validate.py` 는 이 클라이언트용 salvo(aimMode 등이 없는 형태)를 1.21 스키마 위반으로 표시함 — 툴킷 스키마와 클라이언트 형식 차이일 뿐, 클라이언트는 이 형식을 요구함.
- 고유 텍스처 RGBA 1.33 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- salvo.aimMode removed (shot count fixed at 3, flight speed/range come from the server binding)
