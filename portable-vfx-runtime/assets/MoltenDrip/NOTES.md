# MoltenDrip (쇳물 방울, 화염 초급) — 변환 메모

- 시스템 3개
  - `glob` (projectile, loop, 7 layers): 서버가 포물선으로 날리는 쇳물 덩어리. 꼬리에 떨어지는 쇳물 방울(중력)·불꼬리·연기·불티 (world, rateOverDistance).
  - `splash` (impact, 1.0 s, 8 layers): 착탄점 hitPoint, 위 = surfaceNormal (공중/대상 = −진행방향). 섬광·튀는 왕관·쇳물 방울·불티·고리·김·숯 조각.
  - `pool` (static, 2.6 s, 10 layers): **착탄과 같은 순간에, 착탄점 아래 바닥(벽·공중 착탄이면 아래로 내려 찾은 지면, 대상이면 대상 발밑)** 에 시작. P = 바닥, 위 = +Y, 회전 무관.
- 시간표 (pool 기준): 0~0.15 웅덩이가 퍼짐 · **0~2.0 웅덩이 유지 (피해 판정 구간, 반경 약 0.85 m)** · 1.3~2.6 식으며 검은 그을음(빛나는 균열)으로 굳고 사라짐.
- 피해 권장: 착탄 순간(splash t=0) 1회 + pool 0~2.0 s 동안 틱 피해.
- 런타임 입력: 없음 (scale 사용 안 함). 웅덩이 지름 1.7 m 고정.
- 미리보기의 포물선은 demo flightPath(14점) — 실제 비행 경로는 서버.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
