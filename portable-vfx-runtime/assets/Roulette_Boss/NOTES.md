# Roulette_Boss — 도박의 악마 룰레타 보스 패턴 VFX

- 빨간 예고 장판은 기존 예고 시스템(chatelegraph)이 그린다. 이 팩에는 예고가 없다.
- 크기는 기획 수치(1블록 = 1 m)로 고정. scale 입력 없음. 경기장 반경 18, 가운데 테이블 반경 4.
- static 의 +Z = 서버가 넘기는 수평 방향, attached = 대상 발밑 + 바라보는 방향.
- 보스 기준 위치: 등 뒤 휠 중심 = 발밑 (0, 6.4, -1.05), 반지름 2.1.
- 클라이언트 미지원 기능은 쓰지 않았다 (client_compat check OK). 근사·누락 없음.

| 시스템 | role | 놓는 곳 / +Z | 타이밍 | 쓰임 |
|---|---|---|---|---|
| spin | attached 반복 | 보스 | 회전 중 계속, 바늘 멈출 때 정지 신호 | 룰렛 돌리기 (spin_cast 모션과 같이) |
| trick | attached | 보스 | 1.4 s | 속임수 회차 경고(휠 테두리 금빛) |
| betlock | static | 경기장 중앙 | 0 s 마감 순간 | "베팅 마감" |
| win | attached | 맞힌 플레이어 | 0 s 적중 | 적중 |
| lose | static | 경기장 중앙, +Z = 바늘이 멈춘(안전) 구역 가운데 | 0 s 판정 (구역마다 0.025 s 차) | 나머지 5구역 동시 폭발 (한 번만 재생) |
| table | static | 경기장 중앙 | 0 s 판정 | 가운데 테이블 30% 피격 |
| card / card_hit | projectile / impact | 보스 손 → 직선 | 비행 중 / 착탄 | 카드 날리기 (갈래마다 1개) |
| chip_drop | static | 낙하 지점 | 0.12 s 착지 | 칩 탑 낙하 반경 3 |
| dice_trail | attached 반복 | 굴러가는 주사위 소품 | 구르는 동안, 멈추면 정지 | 주사위 굴리기 |
| pip_1 ~ pip_6 | static | 멈춘 주사위 발밑, +Z = 첫 줄 방향 | 0 s 판정 | 눈 수만큼 고르게 방사 (길이 16, 폭 2) |
| dealer_cross / dealer_x | static | 보스 발밑, +Z = 보스 정면 | 0 s 판정 | 딜러의 손 십자 → X자 (팔 16, 폭 3) |
| coin_heads / coin_tails | static | 보스 발밑 | 0 s 판정 | 동전 앞면(안쪽 r8) / 뒷면(바깥 8~18) |
| all_in | static | 보스 발밑, +Z = 보스 정면 | 0 s 판정 | 올인 앞 반원 r10 |
| recall | link | source = 바닥 칩, target = 보스 가슴 | 0.5 s 비행 | 칩 회수 (칩마다 1개) |
| groggy | attached 반복 | 보스 | 그로기 동안 | 그로기 |
| enrage | static | 보스 발밑 | 0.6 s 폭발 | 광폭화 전환 |
| enrage_aura | attached 반복 | 보스 | 광폭화 동안 | 광폭화 오라 |


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
