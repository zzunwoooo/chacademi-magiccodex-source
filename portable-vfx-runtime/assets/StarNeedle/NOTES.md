# StarNeedle (별똥 바늘) — 변환 메모

- 원소/등급: 빛 · 초급. 빛 바늘 3개를 오른쪽 어깨 옆에 만들고 하나씩 던진다 (salvo, SCHEMA §5.2).
- 시스템:
  - `summon` (attached, 0–0.5 s): 오른손 앞 세로 태양 마법진(메쉬 선 3겹 + 룬 띠) + 바늘 3개가 차례로 형성 (0.12 / 0.18 / 0.24 s).
  - `idle1`~`idle3` (attached, loop): 슬롯별 바늘 + 끝의 별빛 반짝임. 발사 k 에서 `idle{k}` 정지.
  - `projectile` (발사마다 새 인스턴스): 빛 바늘 메쉬(+Z 방향) + 끝 별/광채 + 월드 잔광(줄무늬, 별가루, 먼지, 먹빛 파편).
  - `impact` (적중마다 새 인스턴스): 섬광 → 충격 고리 2겹 → 작은 메쉬 마법진(고리+팔각별) → 꽂힌 바늘이 0.5 s 동안 사라짐, 불꽃·빛 파편·먹빛 파편·별 반짝임.
- 시간표(기본값): fireStart 0.45 s, interval 0.15 s → 발사 0.45 / 0.60 / 0.75 s. 비행은 모드가 결정(미리보기 0.26–0.30 s). 임팩트 길이 0.8 s. 전체 약 1.6 s.
- 피해 권장 시점: 각 바늘의 적중 순간(impact 시작 = 섬광 피크).
- 슬롯(시전자 기준, m): [0.55,1.95,-0.05], [0.8,1.62,0.05], [0.6,1.3,0.18]; 조준은 전방 10 m·높이 1.4 m 수렴(초기 방향만).
- 런타임 입력: attached = 발 위치+수평 방향, salvo = fireStart / fireInterval, projectile = 위치+진행 방향, impact = hitPoint / surfaceNormal / incomingDirection (공중: −incomingDirection).
- 주요 기능: meshParticle(바늘·마법진), attachedFollow, salvoSequence, bloom. scale 없음.
- 층 수 33 (summon 9 / idle 2×3 / projectile 8 / impact 11). 고유 텍스처 RGBA 0.705 MiB (11장).
- client_compat check: OK. `client_compat.py fix` 가 `salvo.aimMode` 를 지움(클라이언트 비지원) → 그 결과 툴킷 JSON Schema(1.21)의 validate 는 salvo 항목만 "aimMode 없음"으로 실패한다. 클라이언트 기준이 우선이므로 그대로 둔다.


## PortableVFX client compatibility (tools/client_compat.py fix)

- salvo.aimMode removed (shot count fixed at 3, flight speed/range come from the server binding)
