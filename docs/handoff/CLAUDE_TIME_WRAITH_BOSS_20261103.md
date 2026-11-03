# 3인 고난도 보스 "시간의 망령" — 미몹 + 애드온 + 모델 + VFX (Claude 2026-11-03, **빌드 안 함**)

브랜치 `claude/time-wraith-boss-20261103` (기준 `claude/ash-shadow-boss-20261103` @ 3dae1f7). 이 환경에는 Paper·MythicMobs 의존성이 없어
**컴파일을 못 했다** (javac 구문 검사만 — 파싱 오류 없음, 나머지는 의존성 없음 오류) — 호스팅에서 `Build-Server.ps1 -Verify` 로 먼저 빌드·테스트할 것.

## 0. 바뀐 파일

| 파일 | 내용 |
|---|---|
| `magic-mythic-paper/src/main/java/.../TimeWraithBoss.java` (새) | 미몹 메커닉 `chatime` (보스 진행자), 이벤트 `TimeWraithBoss.Hooks` (기준선·무적·누적 딜·감옥 피격·바늘 원소 판정·처치) |
| `MagicMythicPlugin.java` | `chatime` 등록, Hooks 등록, 끌 때 `TimeWraithBoss.closeAll()` |
| `MythicCommands.java` | `/테스트 보스 시간의망령 <패턴 id \| 기믹 1~3 \| 2페이즈 \| 혼자 켜기·끄기 \| 초기화>` |
| `magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_time_wraith.yml` (새) | 보스 `CA_Boss_time_wraith` + 소품 `CA_Time_prison / floor_clock / hand_hour·minute·second / hand_node / player_echo` |
| `magic-mythic-paper/mythicmobs/Skills/chacademi_boss_time_wraith.yml` (새) | 보스 모션 `CHA_TW_a_*`, 소품 모션 `CHA_TW_p_*` |
| `magic-mythic-paper/models/time_wraith/*.bbmodel` (새) | 보스 `time_wraith` (애니 21) + `_shiny` (지금 미몹에서 안 씀). ModelEngine blueprints 로 (파일 이름 = 모델 id) |
| `magic-mythic-paper/models/time_wraith/props/*.bbmodel` (새) | `time_prison`, `time_floor_clock`, `time_hand_hour/minute/second`, `time_player_echo`, (`time_bullet` = 예비, 지금은 VFX 탄을 씀) |
| `magic-mythic-paper/models/time_wraith/source/` | 모델 생성 원본 (gen.py·models.py·props.py·render.py) |
| `magic-mythic-paper/models/time_wraith/time_wraith_preview_all.html` | 미리보기 한 파일 (모델 3D + VFX 9팩, 브라우저로 열기) |
| `portable-vfx-runtime/assets/TW_*/` (새, 9팩) | **VFX 완성 팩** (vfx.json + textures + NOTES.md). 예고 없음. 팩당 고유 RGBA 1.9~3.4 MiB |
| `portable-vfx-runtime/tools/time_wraith_vfx/` (새) | VFX 원본 (gen_time.py · timelib.js · packs/*.js · build_t.sh) + `CONTRACT.md` (팩·시스템 id·크기·길이 = 서버 연동 표) |

## 1. 동작 요약

- 소환 위치 = 경기장 중심(반경 18), 소환 때 바라본 방향 = 정면 = 바닥 시계 12시. 입장 대사(채팅+타이틀) "너희의 시간은 이미 내 것이다".
- 1페이즈 패턴 (빨간 예고 = 모드 예고, 판정 = 예고 모양 그대로):

| id | 패턴 | 모양 | 예고 | 피해 | 대상 |
|---|---|---|---|---|---|
| attack | 초침 베기 (기본) | 부채꼴 r 6 · 150° | 0.4초 | 26 | 가까운 적 |
| second | 초침 2연타 | 부채꼴 r 8 · 160° ×2 (0.45 / 0.9초) | 0.45초씩 | 28 | 가까운 적 (매 타 재조준) |
| thrust | 시침 찌르기 | 직선 16 × 4 → 끝까지 돌진 | 0.6초 | 32 | 먼 적 |
| sand | 모래 낙하 ×3 | 원 r 4 (그때 위치) | 0.5초씩 | 22 | 체력 낮은 적 |
| pendulum | 진자 | 원 r 6 → 팔각 고리 r 6~12 (직선 8개) | 0.6초씩 | 26 | 보스 중심 |
| stasis | 정지 구슬 | 원 r 3 + 둔화 1.5초 | 0.7초 | 18 | 먼 적 |
| foresight | 앞당긴 미래 | 각자 0.8초 뒤 예상 위치 원 r 4.5 | 0.6초 | 26 | 전원 |

- **기믹1 (75%) 시간 감옥**: 보스 무적(패턴은 계속, 갇힌 사람은 대상 제외). 전투 시작부터 보스에게 준 딜(EntityDamageByEntityEvent, 취소 안 된 것) 1등을 가둠.
  감옥 = `CA_Time_prison`, 체력 = 그 사람 누적 딜 × `PRISON_RATIO`(1.0, 최소 300). 갇힌 사람은 감옥 밖으로 못 나가고(순간이동으로 되돌림), 감옥을 못 때림.
  20초(모래 drain 0.5배속) 안에 나머지가 깨면 성공 → 그로기 8초, 못 깨면 갇힌 사람 사망 → 1페이즈 재개.
- **기믹2 (50%) 바닥의 시계**: 보스는 12시 방향 가장자리(중심에서 arena-3)로 물러나 무적, 가벼운 패턴(sand·stasis·foresight)만 느리게.
  목표 시각 랜덤, 시작 = 바늘마다 목표 ±1~5 (최소 한 바늘은 뒤로 돌려야 함). 시계 중심에 `CA_Time_floor_clock` + 바늘 3개(엔티티 yaw = 정면 + 각도) +
  끝/꼬리 판정점 `CA_Time_hand_node` 6개(안 보이는 엔티티) + TextDisplay(목표·현재 시각).
  **원소 마법**(MagicDamageEvent.element)으로 바늘 끝 = 앞으로 1칸, 꼬리 = 뒤로 1칸: 시침 = fire·earth, 분침 = water·wind, 초침 = light·dark.
  틀린 원소 = 안 움직임(회색 VFX + 안내). 원소 없는 타격 = 안내만. 판정점마다 8틱 쿨다운(한 마법 다단히트 = 한 칸). 45초 안에 다 맞추면 성공 → 그로기, 시간 초과 = 전원 사망.
- **기믹3 (25%) 멈춘 세계**: 보스 중앙, 무적, 3라운드. 라운드마다 방패 1명(아직 안 한 사람 우선) + 문자판 방향 1개(2라운드는 60° 떨어진 2개).
  예고 2.5초(3라운드 1.5초, 방패 표식도 0.7초 늦게) → 시간 정지 2초(전원 MOVEMENT_LOCK, 탄 31발/방향이 ±55° 부채꼴로 나와 공중에 멈춤) → 풀리면 날아감.
  탄마다 처음 부딪힌 사람(반경 0.75)이 방패면 막힘(피해 없음), 아니면 최대 체력 45%(라운드당 최대 2발). 세 라운드 동안 아무도 안 맞으면 그로기.
- **0% 가짜 클리어 → 2페이즈**: 체력이 1에서 멈춤 → 쓰러짐 + "클리어!" 타이틀 → 4.5초 뒤 "...아니." → 되감기(revive = fake_death 역재생) → 2페이즈.
  2페이즈 = 체력이 10초 동안 50% 까지 차오름(그동안도 딜 들어감), 이동 1.2배, 피해 1.2배, **모든 패턴 판정이 2초 전 위치의 유저 잔상**(`CA_Time_player_echo`)에 걸림,
  예고 최소 2.5초(= 2초 일찍 빠져야 함). 진짜 0% = 처치.
- 경기장(반경 24) 밖은 대상 아님. 60초 아무도 없으면 처음 상태.

## 2. 배포 (GPT)

1. MagicMythic jar 빌드.
2. `mythicmobs/Mobs/chacademi_boss_time_wraith.yml` → `plugins/MythicMobs/Mobs/`, `Skills/chacademi_boss_time_wraith.yml` → `plugins/MythicMobs/Skills/`, `/mm reload`.
3. ModelEngine blueprints: `models/time_wraith/time_wraith.bbmodel` + `models/time_wraith/props/` 의 time_prison·time_floor_clock·time_hand_hour·time_hand_minute·time_hand_second·time_player_echo (time_bullet 은 선택) → `/meg reload` → ItemsAdder 병합 `/iazip`.
4. PortableVFX 팩 `portable-vfx-runtime/assets/TW_*` (9개) → 클라이언트 PortableVFX `claude` 이름공간. 코드는 `claude:<팩 소문자>/<시스템>` (`tools/time_wraith_vfx/CONTRACT.md`).
5. 보스 소환: 경기장 한가운데에서 12시로 쓸 방향을 바라보고 `/mm mobs spawn CA_Boss_time_wraith`.

## 3. 확인 못 한 것 / 주의

- **컴파일·실행 확인 없음.** API 는 AshShadowBoss·ReaperBoss 와 같은 것 + Paper `LivingEntity#setBodyYaw`, `TextDisplay`, `World#spawn(…, Consumer)`.
- 누적 딜: 마법 피해가 `EntityDamageByEntityEvent`(damager = 시전자)로 오는 것을 가정(MagicProtection.damage). 아니면 감옥 대상이 근접 위주로 잡힘 → 그땐 `MagicDamageEvent` 로 보스 대상 딜도 세도록 Hooks.dealt 보강.
- 바늘 판정점 = 안 보이는 HUSK(`Invisible: true`). 마법 대상 판정(Targets.enemy)이 이 소품을 적으로 보는지 확인. 꼬리 판정점 3개는 중심 가까이(1.1~1.8칸) 모여 있어 범위 마법이 여러 개를 같이 칠 수 있음(의도: 틀린 원소는 무반응이라 손해 없음).
- 바늘 모델 회전 = 엔티티 yaw. NoAI HUSK 의 몸 방향이 ModelEngine 에 반영되는지 확인 (setRotation + setBodyYaw 둘 다 함).
- 감옥 체력은 `MAX_HEALTH` 속성을 소환 직후 바꿈. 서버 속성 최대치(spigot attribute.maxHealth.max)가 그보다 작으면 잘림.
- 멈춘 세계 탄 = PortableVFX projectile 31~62개를 2초 띄우고 매 틱 이동(라운드 3번). 짧지만 클라 부담 확인.
- `state{…;speed=0.5}` (감옥 drain) 의 속도 옵션 이름이 ModelEngine 버전에 따라 다르면 맞출 것 (안 되면 모래가 10초에 다 떨어지는 것처럼 보일 뿐, 판정은 20초).
- 혼자 시험(`/테스트 보스 시간의망령 혼자 켜기`): 즉사 대신 알림, 감옥은 본인도 칠 수 있음, 방패는 매번 본인.

## 4. 시험 명령

```
/테스트 보스 시간의망령 패턴 attack|second|thrust|sand|pendulum|stasis|foresight
/테스트 보스 시간의망령 기믹 1|2|3
/테스트 보스 시간의망령 2페이즈 | 혼자 켜기|끄기 | 초기화
```
