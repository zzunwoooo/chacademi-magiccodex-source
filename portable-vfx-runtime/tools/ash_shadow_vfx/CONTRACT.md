# 재의 그림자 (1인 던전 보스) + 잡몹 3종 — VFX 계약서 (2026-11-03)

서버 코드(`magic-mythic-paper/.../AshShadowBoss.java`, `mythicmobs/Skills/chacademi_ash_shadow.yml`)가 이 id 그대로 부른다:
`claude:<팩 이름 소문자>/<시스템 id 소문자>` (예: `claude:as_handslam/slam`).
**VFX 에 빨간 예고 넣지 않는다** — 원형·직선·부채꼴 예고는 모드(chatelegraph / Telegraphs)가 그림. 하베스트(`pumpkin_reaper_vfx`)와 같은 규칙·도구(lib.js, build.sh).

공통
- 좌표: static 은 원점 = 지정 위치 바닥, +Z = 서버가 넘긴 yaw 정면. 직선(line)은 원점에서 +Z 로 length 만큼 뻗음. 부채꼴은 원점에서 +Z 중심.
- 크기: static 은 `scale.input: areaRadius`, reference = 아래 "기준" 값. 서버는 실제 크기를 넣어 부름 (보통 기준과 같음).
- 색 (기본 보스): 숯검정·잿빛(재 조각, 그을음 안개) + 잿불 주황 #ff7a2a → 진홍 #c8361a, 하얀 재 반점. 장치 빛만 창백한 금빛 #ffe6a0.
- 잡몹 `_shiny` = 같은 레이어, 색만: 흰 재 + 하늘색 불 #7ce8ff → #2f86e8, 금속은 금 #e2b448.
- 팩당 고유 RGBA ≤ 4 MiB. client_compat check OK 필수. 지속(duration)은 아래 길이 ±0.2초.
- 타이밍: "발동" = 판정 순간. 서버는 발동 순간에 static 을 재생한다(lead 0). 단 AS_AshRain/fall 만 발동 0.25초 전에 재생(낙하 포함).

## 보스 팩 (AS_*)

| 팩 / 시스템 | 역할 | 기준 | 길이 | 내용 |
|---|---|---|---|---|
| AS_ClawSwipe / swipe | static 부채꼴 | r 6, 150° | 0.8 | 기본 공격. 앞팔 할퀴기: 검은 손톱 자국 3줄(잿불 테두리) + 재 파편 + 불티 |
| AS_AshDash / windup | static | r 2.5 | 0.6 | 돌진 준비: 발밑으로 재가 빨려듦, 등손 쪽 잿불 |
| AS_AshDash / dash | static 직선 | 길이 18 · 폭 4 | 1.2 | 돌진 궤적: 검은 재 띠 + 잿불 줄무늬 + 그을린 바닥 + 끝에서 재 폭발 |
| AS_HandSlam / slam | static 직선 | 길이 14 · 폭 4 | 1.2 | 거대한 그림자 손 내려찍기: 앞쪽 절반에 손자국(손가락 4) 실루엣, 바닥 균열 줄, 충격파, 잿불 튐 |
| AS_AshRain / cast | static (보스 발밑) | r 3 | 2.2 | 손 들고 손가락 튕김: 머리 위 고리 쪽 불티 분수, 손끝에서 잿불 튐 4번 (0.6/0.9/1.2/1.5초) |
| AS_AshRain / fall | static 원 | r 4 | 1.0 | 0~0.25초 위에서 잿불 덩어리 낙하 → 0.25초 착탄: 재 폭발, 링, 그을린 원 |
| AS_ShadowSink / sink | static (보스) | r 3 | 1.0 | 보스가 녹아드는 검은 먹물 웅덩이 + 재 손들이 허우적 |
| AS_ShadowSink / emerge | static 원 | r 5 | 1.4 | 바닥에서 솟구침: 검은 기둥 + 재 폭발, 0.4초에 바깥 충격 링이 r 8 까지 |
| AS_Grasp / grasp | static 원 | r 6 | 1.2 | 바닥에서 검은 손이 솟아 0.35초에 움켜쥠, 그림자 촉수 줄기, 잿불 테두리 |
| AS_Afterimage / flicker | static (보스) | r 2 | 0.6 | 잔상: 검은 실루엣 줄무늬가 떨리며 흩어짐 |
| AS_AshVeil / wrap | static (보스) | r 3 | 1.2 | 등손이 감쌈: 재 소용돌이가 안으로 말려듦 |
| AS_AshVeil / burst | static 원 | r 7 | 1.0 | 재 장막 폭발: 검은 재 돔이 터지며 바깥으로, 잿불 링 |
| AS_CremationRing / inner | static 원 | r 5 | 1.0 | 안쪽 화장: 원 안 바닥에서 잿불 기둥들 + 링 |
| AS_CremationRing / outer | static 도넛 | 안 5 · 밖 12 (기준 12) | 1.0 | 바깥 고리: r 5~12 띠 영역에서 불길·재가 솟음, 안쪽은 비움 |
| AS_Desperate / scream | static 원 | r 10 | 1.2 | 발악: 8방향 균열 직선(길이 10 · 폭 3)에서 잿불 분출 + 중앙 비명 충격파. 서버가 2번째는 yaw +22.5° 로 다시 재생 |
| AS_Ward / shield | attached (장치 따라감) | 반경 약 1.6 고정 | 2.2 | 빛의 수호: 장치를 감싸는 창백한 금빛 육각 막 |
| AS_Barrage / start | static (보스) | r 4 | 1.8 | 탄막 시작: 떠오르며 촛불 고리 확 타오름, 바닥 마법진(재·잿불) |
| AS_Barrage / aura | attached (보스) | — | 27 | 탄막 유지 오라: 보스 주위 도는 재 조각, 등 뒤 고리 불꽃. duration 이 27초를 덮게 |
| AS_Barrage / shard | projectile (서버가 매 2틱 이동) | 길이 약 0.7 | ≤ 8 | 탄막 조각: 검은 재 조각 + 잿불 테두리 + 짧은 꼬리. 높이 1칸 고정으로 수평 이동 |
| AS_Barrage / pop | static | r 0.6 | 0.4 | 조각이 맞거나 사라질 때 작은 불티 |
| AS_Groggy / fall | static (보스) | r 4 | 1.2 | 그로기 추락: 바닥 충격, 고리가 떨어져 부서지는 불티 |
| AS_Groggy / stun | attached (보스) | — | 11 | 그로기 유지: 몸에서 재가 새어 나옴, 머리 위 깨진 고리 불티 |
| AS_Absorb / beam | link (장치→보스) | 링크 길이 | 1.0 | 장치 빛이 재로 변해 보스로 빨려드는 줄기 |
| AS_Absorb / burst | static (보스) | r 6 | 1.6 | 변신 폭발: 가슴 등불 터짐, 잿불 충격파 2겹, 재 기둥 |
| AS_Absorb / enrage | attached (보스) | — | 60 | 광폭화 오라: 몸에서 잿불 균열 빛, 위로 솟는 불티, 발밑 그을음 (서버가 55초마다 다시 붙임) |
| AS_Device / light | attached (장치) | — | 60 | 빛 발산 장치 켜짐: 창백한 금빛 맥동, 위로 오르는 빛 입자 (서버가 55초마다 다시 붙임) |
| AS_Device / tether | link (장치→보스) | 링크 길이 | 60 | 장치→보스 회복 빛줄기 (금빛, 보스 쪽 끝은 재로 검게) |
| AS_Device / break | static (장치) | r 2.5 | 1.2 | 장치 파괴: 빛 유리 파편, 빛이 꺼지며 재 |
| AS_Device / respawn | static (장치) | r 2 | 1.5 | 장치 재점화: 빛 기둥이 내려와 켜짐 |
| AS_Fate / intro | static (보스) | r 6 | 3.0 | 등장: 재가 솟아 형체, 촛불 고리 점화 |
| AS_Fate / death | static (보스) | r 6 | 3.0 | 처치: 재로 흩어짐, 촛불 꺼짐 연기 |
| AS_Fate / vanish | static | r 3 | 0.8 | 50% 귀환 사라짐 |
| AS_Fate / appear | static | r 3 | 0.8 | 경기장 중앙에 나타남 |

## 잡몹 팩 (AM_*) — 각 시스템마다 `<id>_shiny` 도 같이

| 팩 / 시스템 | 역할 | 기준 | 길이 | 내용 |
|---|---|---|---|---|
| AM_HoundBite / bite | static 부채꼴 | r 3.5, 100° | 0.6 | 재 사냥개 물어뜯기: 잿불 이빨 초승달 2개(위·아래 맞물림), 재 튐 |
| AM_BruteStomp / stomp | static 원 | r 4.5 | 1.2 | 촛농 짐승 내려찍기: 충격 링 2겹, 녹은 촛농 방울 튐, 꺼진 촛불 연기 |
| AM_StagLantern / charge | static | r 1 (머리 높이에서 재생) | 0.8 | 등불에 불 모으기: 잿불이 등불로 빨려듦 |
| AM_StagLantern / bolt | projectile | — | ≤ 3 | 등불 잿불탄: 촛불 불꽃 덩어리 + 꼬리 |
| AM_StagLantern / hit | impact / static 원 | r 2.5 | 1.0 | 착탄: 잿불 폭발, 그을린 원 |
