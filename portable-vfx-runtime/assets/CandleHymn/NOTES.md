# CandleHymn (촛불 성가) — 변환 메모

- 원소/등급: 빛 · 초급. 촛불 3개가 시전자 주위에 떠 있다가, 적이 가까이 오면 하나씩 그 적에게 섬광을 쏘고 꺼진다.
- 시스템 (모두 모드가 시작/정지):
  - `summon` (attached, 0–0.6 s): 발밑 메쉬 마법진(고리+육각별) + 솟는 빛줄기 + 촛불 점화 섬광 3개(0.25/0.35/0.45 s).
  - `hymn` (attached, loop): 옅은 발밑 마법진 + 룬 띠(역회전) + 시전자 주위를 도는 빛 먼지. 촛불이 하나라도 남아 있는 동안 유지, 마지막 촛불 사용/만료 때 정지.
  - `candle1`~`candle3` (attached, loop, 슬롯별): 촛대 몸체(알파) + 흔들리는 불꽃(플립북 cycles 2) + 후광 + 밑받침 작은 고리(회전). 사용 시 해당 `candle{k}` 정지.
  - `flash` (projectile, 사용마다 새 인스턴스): 슬롯의 불꽃 위치에서 적에게 날아가는 짧은 빛 화살(메쉬, 회전) + 잔광. 시작 순간 월드 공간 섬광·꺼진 연기·밀랍 방울이 슬롯에 남는다.
  - `burst` (impact, 적중마다): 섬광 → 코로나 → 충격 고리 2겹 → 작은 육각별 문양 → 광선·불티·먹빛 파편·별빛.
- 슬롯(시전자 기준, 발 원점, +X 오른쪽, +Z 정면, 촛대 중심): [-0.8,1.7,0.1], [0.8,1.85,0.0], [0.0,2.15,-0.65]. 섬광 발사점 = 슬롯 + [0,0.23,0] (불꽃).
- 시간표(미리보기): 0 summon, 0.05 hymn, 0.25/0.35/0.45 candle1..3, 이후 적 접근 시 1.45 / 2.16 / 2.86 s 에 하나씩 발사(비행 0.14 s), 2.95 hymn 정지.
- 피해 권장 시점: 각 `burst` 시작(섬광 피크). 비행 시간은 모드가 정함(0.1–0.2 s 권장).
- 런타임 입력: attached = 발 위치+수평 방향, projectile = 위치+진행 방향, impact = hitPoint/surfaceNormal/incomingDirection (적 = 공중 적중: −incomingDirection).
- 주요 기능: meshParticle, attachedFollow, flipbook(+cycles), orbitalVelocity(빛 먼지), bloom. scale 없음.
- 층 수 35 (summon 6 / hymn 3 / candle 4×3 / flash 6 / burst 8). 고유 텍스처 RGBA 1.205 MiB (13장).
- client_compat check: OK (fix 변경 0건), validate OK.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
