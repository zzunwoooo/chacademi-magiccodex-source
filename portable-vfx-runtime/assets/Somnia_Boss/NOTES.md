# Somnia_Boss — 몽마 솜니아 보스 패턴 VFX

- 빨간 예고 장판은 기존 예고 시스템(chatelegraph)이 그린다. 이 팩에는 예고가 없다.
- 크기 고정(1블록 = 1 m), 경기장 반경 16. static +Z = 서버가 넘기는 수평 방향.
- boundary 는 life 60 s 한 장으로 0.12 rad/s 로 돈다: 서버 판정도 같은 속도로 돌리고, 60 s 마다 현재 각도를 +Z 로 다시 재생해 이어 붙인다.
- 클라이언트 미지원 기능은 쓰지 않았다 (client_compat check OK).

| 시스템 | role | 놓는 곳 / +Z | 타이밍 | 쓰임 |
|---|---|---|---|---|
| lullaby | static | 보스 발밑 | 고리 3겹 0 / 0.5 / 1.0 s 출발, 1.6 s 만에 반경 16 | 자장가 |
| drowsy | attached | 맞은 플레이어 | 2.5 s | 졸음 30% |
| gust | static | 보스 발밑, +Z = 정면 | 0 s | 날개 바람 부채꼴 120° r12 |
| feather | static | 낙하 지점 | 0.32 s 착탄 | 깃털 비 한 발 r2 |
| pillow | static | 낙하 지점 | 0.6 s 착탄 | 베개 짓누르기 r5 |
| pool | static 반복 | 웅덩이 중심 | 유지, 옮길 때 정지 | 잠의 웅덩이 r1.8 |
| pool_open / pool_close | static | 웅덩이 중심 | 0.6 s | 웅덩이 생김 / 사라짐 |
| enter / exit | attached | 플레이어 | 0.8 s / 0.2 s | 꿈으로 들어감 / 현실로 튕겨 나옴 |
| bell_glow | static 반복 | 빛나는 종탑 발밑 | 유지 | 칠 종 표시 |
| bell_ring | static | 종탑 발밑 | 0 s | 종 치기 |
| stairs | static | 줄 시작점, +Z = 줄 방향 | 0.4 s 착지 | 떨어지는 계단 한 줄 (길이 18 폭 3) |
| eyelid | static | 경기장 중앙, +Z = 안전 띠 방향 | 0 s | 눈꺼풀 (가운데 폭 3 띠만 안전) |
| clone | attached 반복 | 악몽 분신 | 분신이 있는 동안 | 악몽 분신 기운 |
| collapse | static | 보스 발밑, +Z = 꺼지는 반원 | 0 s | 꿈 붕괴 반원 r16 |
| groggy | attached 반복 | 보스 | 그로기 동안 | 그로기 |
| eternal | attached | 플레이어 | 1.6 s | 졸음 100% 사망 |
| dreamfall | static | 경기장 중앙 | 2.6 s 끝에 섬광 | 50% 깨지 않는 꿈 진입 |
| wake | static | 보스 발밑 | 0.5 s 폭발 | "일어나" |
| boundary | static 반복 | 경기장 중앙, +Z = 경계선 시작 각도 | 유지 | 30% 경계 붕괴 회전선 |
| enrage | static | 보스 발밑 | 0.6 s 폭발 | 광폭화 전환 |


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
