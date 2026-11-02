# LodestoneShard (자철 조각) — 변환 메모

- 시스템 4개:
  - `projectile` (projectile, 반복): 손 → 목표로 나가는 자철 파편. 진행 방향 +Z 로 누운 십자 메쉬 `shard`(어두운 쇠 + 금 테두리) + 자기장 호 2겹(반대 회전).
  - `embed` (impact, 1.0 s): 명중 지점 hitPoint, up = surfaceNormal (적/공중 = −진행방향). **t=0 1타**(섬광·파편) → 파편이 표면에 박힌 채(local −Y 방향으로 꽂힘) 자력 고리가 3번 안쪽으로 조여듦 → **t=0.45 s 뽑혀 나감 = 2타**(위로 터지는 불꽃·충격파).
  - `returnShard` (projectile, 반복): **embed 시작 + 0.45 s** 에 hitPoint 에서 시전자 손으로 돌아오는 파편(다시 진행 방향 +Z). 돌아오는 길의 적도 맞을 수 있음(모드 판정). 끌려오는 자력선 추가.
  - `catch` (impact, 0.8 s): 파편이 손에 닿을 때 손 위치에서 1회(normal 없음 = 공중 규칙). 안쪽으로 모이는 빛.
- 피해 권장 시점: 1타 = embed t=0, 2타 = embed t=0.45 s (뽑힐 때) — 원하면 돌아오는 경로에서 지나치는 적에게도.
- 런타임 입력: projectile 위치·방향, impact hitPoint/normal/incoming. scale 없음.
- 어두운 강조: 쇠 파편 몸체(어두운 쇠, 알파 + HDR 테두리), 쇳조각·돌 파편(`iron`/`rock`).
- client_compat check: OK (FIX/WARN 없음). 고유 텍스처 RGBA 0.99 MiB (8장, 전용 T_IronShard 192×48).
- 층 32개 (projectile 8 + embed 11 + returnShard 9 + catch 4) — 시스템 4개로 나뉘어 한 번에 보이는 층은 20개 안팎.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
