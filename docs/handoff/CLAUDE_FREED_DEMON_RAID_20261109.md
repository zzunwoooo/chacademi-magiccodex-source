# 1인 레이드 "해방된 악마" — 미몹 + 애드온 + 모델 + VFX (Claude 2026-11-09, **빌드 안 함**)

악마는 거짓말을 한다. 기믹 규칙은 아무 데서도 설명하지 않는다 (타이틀·안내 문구 없음).
보스는 짧은 대사만, **실패한 사람에게만** 짧은 단서 한 줄(회색 기울임). 빨간 예고 = 모드 예고, VFX 에는 장판 예고가 없음.

## 0. 바뀐 파일

| 파일 | 내용 |
|---|---|
| `magic-mythic-paper/src/main/java/.../FreedDemonBoss.java` | 새 메커닉 `chafreed` (패턴 18 · 기믹 4 · 잡몹 · 낙인 · 시험 명령) |
| `MagicMythicPlugin.java` | `case "chafreed"`, `FreedDemonBoss.Hooks` 등록, `onDisable` 에 `closeAll()` |
| `MythicCommands.java` | `/테스트 보스 해방된악마 …` |
| `mythicmobs/Mobs/chacademi_boss_freed_demon.yml` | 보스 · 분신 · 소품 7 · 잡몹 6 (15개) |
| `mythicmobs/Skills/chacademi_boss_freed_demon.yml` | `CHA_FD_a_*` 보스 모션 · `CHA_FD_lie_on/off` · `CHA_FD_p_*` 소품 · 잡몹 스킬 2 |
| `magic-mythic-paper/models/freed_demon/` | 보스(+shiny) · `minions/` 8 · `props/` 5 · `source/` 생성기 |
| `portable-vfx-runtime/assets/FreedDemon_*` | VFX 팩 10개 (`claude:freeddemon_*`) |

## 1. 동작 요약

체력 7000, 경기장 반경 14 (소환 위치 = 중심, 바라본 방향 = 정면). 기준선 70 / 40 / 10 / 1% — 기믹 동안 무적.
패턴 간격 배율 1.0 → 0.8 (70%↓) → 0.65 (40%↓) → 0.5 (10%↓). 40% 아래에서는 가끔 원·도넛 / 지옥의 손이 이어 붙음.

### 짤패턴 18
할퀴기 · 쌍갈래 2타 · 지연 내려찍기(멈칫) · 양손 위협(빛나는 손 쪽만 진짜) · 지옥불 십자(발동 직전 45° 회전) · 영혼 사슬(멀면 끌어옴) ·
처형(40%↓ 사람 등 뒤) · 유황 행진(불길 남김) · 거울 돌진(분신 둘, 본체 선만 아픔) · 원↔도넛 · 지옥 혜성 3연 · 계약의 낙인(붉은=벗어나기, 보라=멈추기, 보라 눈일 때 대사 반대) ·
날개 폭풍(밀침 + 외곽 불벽) · 지옥의 손 2파(2파는 1파 빈자리) · 도발(붉은 눈=반격, 보라 눈=1.5배 딜 기회) · 광란 5연격 · 가짜 안전지대(문양이 꺼지는 쪽이 진짜) · 어둠의 장막(실명 + 소리).

### 기믹
1. **70% 여섯 개의 고해** — 석상 6개(반경 10)에 번호·문장. 진실 석상 하나만 성립하도록 생성. 시작 귓속말 번호는 항상 거짓. 임프 2. 20초 뒤 진실 석상 발판(r 1.8) 밖 = 즉사.
2. **40% 지옥의 줄넘기** — 붉은 사슬(낮음)=점프, 검은 사슬(높음)=웅크리기. 15바퀴, 감속 뒤 역회전 3번. 외침("뛰어라/엎드려라")은 보라 눈일 때 거짓(40%). 맞으면 50% + 낙인, 낙인 2 = 즉사.
3. **10% 악마의 서명** — 8구역에 표식(6종 + 중복 2). 보스 위로 표식 6개(진짜 5, 그중 뒤집힘 1 = 불발, 흐린 가짜 1 = 순서에 없음). 그 순서대로 같은 표식 구역 폭발(예고 없음) = 즉사. 중앙 r 3 은 모두 걸림.
4. **1% 최후의 속임수** — 가짜 사망 + 가짜 "토벌 완료" 타이틀 + 보상 상자(미믹). 그림자를 10초 안에 잡으면 재봉인(진짜 사망). 상자에 닿으면 즉사 + 15% 부활, 그림자를 놓쳐도 15% 부활.

잡몹: 속삭이는 임프(70% 뒤 30초마다, 밀치기) · 계약의 집행자(55% · 25%, 맞으면 낙인) · 성배 한 쌍(40% 뒤 35초마다, 진짜 = 30% 회복 / 거짓 = 3초 뒤 폭발 40%).

### 실패 단서 (실패한 사람에게만)
| 상황 | 단서 |
|---|---|
| 고해 — 귓속말 석상에 섬 | (속삭임은 언제나 달콤했다. …그리고 언제나 틀렸다.) |
| 고해 — 다른 오답 | (여섯 중 다섯은 거짓이었다. 남은 하나는… 다른 다섯과 어긋나지 않는 말이었다.) |
| 줄넘기 낙인 2 | (붉은 것은 낮게, 검은 것은 높게 지나갔다… 들어야 할 것은 외침이 아니었다.) |
| 집행자 낙인 2 | (낙인은 겹쳐지면 타오른다… 먼저 찍는 자를 없앴어야 했다.) |
| 서명 | (보여 준 순서 그대로였다. …거꾸로 선 표식의 차례에는 아무 일도 없었다.) |
| 그림자 놓침 | (쓰러진 것은 껍데기뿐이었다. …무언가 바닥을 타고 빠져나갔다.) |
| 미믹 | (…상자는 숨을 쉬고 있었다.) |

## 2. 배포 (GPT)

1. MagicMythic jar 빌드.
2. `mythicmobs/Mobs|Skills/chacademi_boss_freed_demon.yml` → `plugins/MythicMobs/Mobs|Skills/`, `/mm reload`.
3. ModelEngine: `models/freed_demon/*.bbmodel`, `minions/*`, `props/*` → `/meg reload` → `/iazip`.
4. PortableVFX 팩 `portable-vfx-runtime/assets/FreedDemon_*` (10개) → 클라이언트 `claude` 이름공간.
5. 경기장 한가운데에서 정면을 보고 `/mm mobs spawn CA_Boss_freed_demon`.

## 3. 확인 못 한 것 / 주의

- **컴파일·실행 확인 없음** (의존성 없는 환경, 구문만 확인). API 는 ElysiaBoss·ReaperBoss 와 같은 것 + Paper `TextDisplay`, `setBodyYaw`.
- **보라 눈**: `partvisibility{m=freed_demon;p=eyes|eyes_lie;v=..}` — 서버 ModelEngine 버전의 메커닉 이름 확인 필요. Blockbench 에서 eyes_lie 가 숨김이어도 ME 는 무시하므로 `~onSpawn` 에서 `CHA_FD_lie_off`.
- **사슬 방향** `FreedDemonBoss.CHAIN_YAW_OFFSET = -90` (모델이 +X 로 뻗음). 보이는 사슬과 판정 위치가 어긋나면 이 값만 조정.
- **서명 구역 방향** `SECTOR_SIGN = -1`. VFX `sector0~7` 은 +Z 에서 시계 방향으로 구움 — 폭발이 거울처럼 반대 구역에 보이면 1 로.
- 줄넘기 점프 판정 = `isOnGround()==false` 이고 발밑에서 0.25 이상. 웅크리기 = `isSneaking()`.
- 집행자 낙인 = 집행자가 `damager` 인 피해 (근접 + `chatelegraph` 내려찍기). 보스 패턴 피해는 낙인 없음.
- 그림자는 AI 목표 없음 + 애드온이 매 틱 `setVelocity` 로 도망시킴 (속도 0.34). 너무 빠르거나 느리면 `flee()` 값.
- 도발 반격은 피해 이벤트에서 공격자를 못 찾으면 경기장에 한 명뿐일 때 그 사람 (1인 전제).
- 거울 분신은 보스 모델 그대로 (색 변형 없음), 돌진 뒤 `mirror` VFX 와 함께 사라짐.

## 4. 시험 명령 (`/테스트 보스 해방된악마 …`, 64칸 안)

`패턴 <id>` (claw combo slam dual cross chain execution sulfur mirror ringdonut comet brand gust hands taunt frenzy fakesafe darkness) ·
`기믹 1~4` · `낙인 0|1` · `시험 켜기|끄기` (즉사 대신 알림 + 단서) · `초기화`
