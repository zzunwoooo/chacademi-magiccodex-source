# 3인 공성전 레이드 "100년 전, 성문 방어전" — 미몹 + 애드온 + 모델 + VFX (Claude 2026-11-13, **빌드 안 함**)

해방된 악마 레이드의 재활용. 100년 전으로 돌아가 성문을 지킨다. 봉인 전(전성기)의 3천왕과 해방된 악마가 차례로 오고,
그 사이사이 지옥문 포탈에서 네발 마수가 쏟아져 나온다. **기믹 없음** — 보스는 짤패턴만, 주 목적은 성문 방어.
대사는 짧게만, 규칙 설명 없음. 빨간 예고 = 모드 예고, VFX 에는 장판 예고가 없음.

## 0. 바뀐 파일

| 파일 | 내용 |
|---|---|
| `magic-mythic-paper/src/main/java/.../SiegeRaid.java` | 새 메커닉 `chasiege` (성문 체력 · 포탈 · 마수 물결 · 보스 4 · 짤패턴 35 · 시험 명령) |
| `MagicMythicPlugin.java` | `case "chasiege"`, `SiegeRaid.Hooks` 등록, `onDisable` 에 `closeAll()` |
| `MythicCommands.java` | `/테스트 보스 공성전 …` |
| `mythicmobs/Mobs/chacademi_raid_siege.yml` | 성문 · 표식 · 보스 4 · 마수 8 (+이로치 8) = 22개 |
| `mythicmobs/Skills/chacademi_raid_siege.yml` | 보스 모션 `CHA_SG_<SO\|RL\|BZ\|FD>_a_*` · 마수 스킬 `CHA_SGB_<마수>_<패턴>` · `CHA_SGB_portal_out` |
| `magic-mythic-paper/models/siege/` | 보스 4 (+shiny) · `beasts/` 마수 8 (+shiny) · `source/` 생성기 |
| `portable-vfx-runtime/assets/Siege_*` | VFX 팩 6개: Somnia · Roulette · Belzar · Demon · Beasts · Portal (`claude:siege_*`) |

## 1. 동작 요약

`/mm mobs spawn CA_Siege_gate` — 소환한 자리 = 성문, 바라본 방향 = 적이 오는 쪽. 포탈 3개가 그 방향 `lane`(40)칸 앞, 좌우 12칸 간격.
전장 = 성문과 포탈 사이. 플레이어가 전장에 들어오면 5초 뒤 시작.

| 단계 | 마수 물결 (약 40초, 3번 쏟아짐) | 보스 (큰 포탈) | 체력 |
|---|---|---|---|
| 1 | 지옥견 · 가시 도마뱀 · 불씨 두꺼비 | 몽마 솜니아 | 9000 |
| 2 | 지옥견 · 뿔 돌격수 · 날개 추적자 · 불씨 두꺼비 | 도박의 악마 룰레타 | 10000 |
| 3 | 쌍두 마견 · 독침 마수 · 뿔 돌격수 · 가시 도마뱀 + 공성 거수 1 | 천사 벨자르 | 11000 |
| 4 | 전부 + 공성 거수 1 | 해방된 악마 | 16000 |

- 보스전 동안에도 15초마다 마수 2~3마리. 보스가 죽으면 남은 마수가 쓰러지고 8초 뒤 다음 단계. 악마를 잡으면 승리.
- **성문 체력** `gatehp`(4000), 화면 위 막대. 깎는 것: 성문 5칸 안에 붙은 마수(2초마다 지옥견 6 · 돌격수 16 · 도마뱀 8 · 쌍두 12 · 추적자 6 · 독침 8 · 거수 60,
  두꺼비는 자폭 45 한 번), 보스의 **성문 강타**(등장 35초 뒤부터 45초마다, 5% / 악마 8%). 0 이 되면 함락 → 5초 뒤 처음 상태로.
- 마수 역할: 돌격수 · 거수 · 두꺼비 · 도마뱀 = 성문으로 직행 (애드온이 길 안내) / 나머지 = 플레이어 추적 (미몹 AI).
- 보스 체력 30% 아래 = 광폭화: 피해 1.25배, 패턴 간격 짧아짐, 광폭화 전용 패턴이 섞임.
- 전장에 60초 동안 아무도 없으면 처음 상태로.

### 보스 짤패턴 (애드온 안, `/테스트 보스 공성전 패턴 <id>`)
- **솜니아** (느리고, 따라오고, 오래 남는다): orb 꿈방울 · gaze 돌아가는 시선 · lullaby 자장가 물결 · echo 악몽의 잔상 · yawn 하품 · fog 잠의 안개 · eyelid 눈꺼풀 · *fullmoon 만월 낙하*
- **룰레타** (운과 속임수): cards 카드 부메랑 · dice 튕기는 주사위 · domino 칩 도미노 · coin 동전 던지기 · slot 슬롯머신 · sectors 룰렛 난사 · hands 여섯 손 · allin 올인 · *jackpot 잭팟*
- **벨자르** (흑과 백): debt 청구서 · invert 흑백 반전 · chain 사슬 포박 · mines 깃털 지뢰 · halo 좁혀드는 후광 · mirror 거울 일격 · spears 행진하는 창 · interest 복리 · *final 최후 청구*
- **악마** (크고 빠르고 몸으로): combo 대검 3연격 · leap 도약 강타 · dash 지옥 돌진 · throw 마검 투척 · fissure 지면 가르기 · breath 숨결 · pillars 추적 불기둥 · wings 강풍 · roar 포효 · *doom 종말의 일격*
- 공통: gate 성문 강타. (기울임 = 광폭화 뒤에만.) 수치·모션·VFX 대응표는 Skills yml 머리말과 `SiegeRaid.java` 각 메서드 주석.

### 마수 스킬 (미몹 yml, 모션 + VFX + 예고)
지옥견 물기 · 도약 물어뜯기 · 무리 울음 / 뿔 돌격수 뿔 치켜올리기 · 직선 돌진 / 가시 도마뱀 꼬리 쓸기 · 등 가시 발사 / 불씨 두꺼비 불씨 뱉기 · 혀로 끌어당기기 · 자폭 /
쌍두 마견 두 번 물기 · 쌍두 화염 숨결 / 공성 거수 지진 발구르기 · 공성추 들이받기 / 날개 추적자 할퀴기 · 도약 덮치기 · 급강하 / 독침 마수 꼬리 찌르기 · 독침 발사 · 땅속 이동.

## 2. 배포 (GPT)

1. MagicMythic jar 빌드.
2. `mythicmobs/Mobs|Skills/chacademi_raid_siege.yml` → `plugins/MythicMobs/Mobs|Skills/`, `/mm reload`.
3. ModelEngine: `models/siege/*.bbmodel`, `models/siege/beasts/*.bbmodel` (모델 ID = 파일 이름) → `/meg reload`.
4. PortableVFX 팩 `portable-vfx-runtime/assets/Siege_*` (6개) → 클라이언트 `claude` 이름공간 (`claude:siege_demon/cleave` 처럼 팩 소문자/시스템).
5. 성문 자리에서 적이 올 방향을 보고 `/mm mobs spawn CA_Siege_gate`. 전장은 성문 앞 40칸 + 좌우 20칸쯤 평지가 필요.

## 3. 확인 못 한 것 / 주의

- **컴파일·실행 확인 없음** (의존성 없는 환경, javac 구문 검사만). API 는 RouletteBoss·DemonFight 와 같은 것 + Paper `Mob#getPathfinder`, Bukkit `BossBar`.
- **솜니아 "달의 궤도"(moon_orbit) 는 애드온에 넣지 않았다.** VFX 의 달 시작 위치가 무작위라 서버 판정과 맞출 수 없다. 모션(`CHA_SG_SO_a_moon_orbit`)과 VFX 는 있다.
- **회전 방향**: 솜니아 gaze(광선 회전) = `SiegeRaid.GAZE_SIGN`, 악마 breath(훑는 방향) = `BREATH_SIGN`. 보이는 것과 판정이 거울처럼 반대면 부호만 바꾼다.
- **도넛 범위**(동전 뒷면 · 흑백 반전 바깥): 예고 모양에 도넛이 없어 바깥 원 전체가 빨갛게 보이고, 판정만 안쪽 반경을 뺀다.
- **움직이는 범위**(자장가 · 후광 · 포효 · 충격파 고리, 도미노 · 균열)는 예고가 시작 모양만 보이고 판정은 애드온 틱이 한다. 고리는 뛰어서(`isOnGround()==false`) 넘는다.
- **사각 VFX**(eyelid_band · final_* · doom_half)는 가운데 기준으로 재생한다고 가정했다. 반 칸 밀려 보이면 `rect()` 의 재생 위치만 조정.
- **불씨 두꺼비 불씨 뱉기**: VFX 착탄이 정면 10칸 고정이라 대상이 7~12칸일 때만 쓰고, 예고는 대상 자리 원이다. 어긋나 보이면 `chamobshot` 방식으로 바꾸는 게 맞다.
- **지옥견 도약 물어뜯기**: VFX 를 대상 자리에서 재생한다 (이빨 자국이 대상보다 2~3칸 뒤에 보일 수 있다). 뿔 돌격수 돌진은 미몹 `lunge` 로 민다.
- **벨자르 모델 크기**: belzar_past 는 다른 보스보다 작다 (높이 약 4.6블록). ModelEngine 에서 1.8배 권장. yml 에는 배율을 넣지 않았다.
- **악마 거짓 눈**: `partvisibility{m=freed_demon_past;p=eyes_lie;v=false}` — 서버 ModelEngine 버전의 메커닉 이름 확인 필요.
- 성문 직행 마수는 1초마다 `setTarget(null)` + 길찾기를 다시 건다. 플레이어에게 맞아도 성문으로 간다 (미몹 타이머 스킬은 대상이 없으면 안 나간다).
- 성문 몸(CA_Siege_gate)은 보이지 않는 무적 몸이다. 실제 성문은 맵에 짓는다.
- 승리 보상·칭호는 없다. 서버 로그에 `[siege] 승리 - 남은 성문 N% / [참가자]` 만 남긴다.

## 4. 시험 명령 (`/테스트 보스 공성전 …`, 96칸 안)

`단계 1~4` (그 단계 마수 물결부터) · `보스 1~4` (그 단계 보스 바로) · `패턴 <id>` · `마수 <이름>` (hound ram lizard toad twin behemoth stalker stinger) ·
`성문 <퍼센트>` · `광폭화` · `초기화`
