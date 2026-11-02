# MoltenWave (쇳물 해일) — 변환 메모

- 시스템 2개: `wave` (role **projectile**) → `crash` (role **impact**).
  - wave: 시전자 앞 바닥 높이(y=0)에서 시작해 시전자 수평 방향으로 모드가 이동시킨다 (권장 약 6.5 m/s, 사거리 약 10 m). P = 파도 밑동 중앙(바닥), forward = 진행 방향, up = 월드 위 (SCHEMA §5 projectile 기준). 폭 약 4.8 m, 높이 2.2 m.
  - world 층(분무, 불타는 쇳물 웅덩이 자국, 그을음, 연기, 불씨)은 지나간 자리에 남는다. t=0 의 world 버스트(LaunchSplash/Ring/Flash/Column)는 출발점에 남는 '솟구침'.
  - 사거리 끝 또는 벽/블록에 막히면: 투사체 규약대로 정지(local 층 제거) 후 `crash` 를 그 지점에 생성. 바닥 끝 = normal (0,1,0), 벽 = 벽 법선. 공중 명중은 쓰지 않는다(지면 파도).
- 시간표: wave 0~0.25 s 파도가 솟아오름, 이후 이동 내내 유지(파티클 수명 4 s → 비행 4 s 이내) · crash 0.0 파도가 앞으로 무너지며 폭발·튀김, 0~1.9 쇳물 웅덩이 식음, 2.0 종료.
- 피해 권장: 파도 몸체(폭 4.8 m, 앞뒤 약 1.5 m)에 닿는 적에게 이동 중 접촉 피해 + 화상, 무너질 때(crash 0 s) 반경 2 m 추가 피해.
- 쇳물 흐름은 2x2 플립북 `flipbook.cycles`(flipbookCycles)로 위로 흐르게 함.
- 고유 텍스처 RGBA: 2.19 MiB. client_compat check: OK (fix 0 change).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
