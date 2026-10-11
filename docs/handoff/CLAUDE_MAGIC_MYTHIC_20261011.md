# Claude 인계: 마법 미몹 게임 효과 (2026-10-11)

- 브랜치: `claude/magic-mythic-20261011`
- 기준: `codex/hires-item-icons-20261006` @ `f44666f`
- **빌드·서버 설치·게임 실행은 하지 않았다.** Gradle 의존성을 받을 수 없어 Paper API 소스로 타입 검사만 했다(7장). MythicMobs API는 4장 "빌드 때 확인할 API"를 먼저 본다.

사용자 지시: "VFX가 만들어진 마법 약 100개에 실제 MythicMobs 기능(단순 기능·피해 등)을 넣는다. 이펙트는 이미 있으니 필요 없다."

## 0. 주의사항 (먼저 읽기)

1. **MagicCodex(Bridge) 코드는 이 브랜치에서 바꾸지 않았다.** 사용자 규칙(MagicCodex 변경은 GPT 승인 후)에 따라 수정안만 `docs/handoff/proposals/BRIDGE_CATALOG_GAMEPLAY_20261011.patch` 로 넣었다. **이 수정안을 적용하지 않으면 새 미몹 스킬은 하나도 실행되지 않는다** (1장 참고).
2. 새 플러그인 `magic-mythic-paper` (ChAcademiaMagicMythic) 를 `settings.gradle` 과 `Build-Server.ps1` 목록에 추가했다. 다른 모듈 코드는 건드리지 않았다.
3. 서버에 이미 같은 이름의 `CHA_*` 스킬 파일이 있으면 MythicMobs가 중복 이름 경고를 낸다. 기존 `CHA_*` 마법 스킬 파일을 빼고 `magic-mythic-paper/mythicmobs/Skills/chacademi_spells.yml` 로 교체한다.
4. 운영 중인 `plugins/MagicCodexBridge/spell-runtime.yml` 은 jar 가 덮어쓰지 않는다. `magic-mythic-paper/server-config/spell-runtime.yml` 로 손으로 교체한다 (기존 31개 라우트의 스킬 이름·피해 수치는 그대로 유지, 새 라우트 추가).
5. 피해 수치는 **임시값**이다 (소유자 결정: 마법 밸런스 최종 검토 전). `tools/spells.py` 표에서 고치고 다시 생성한다.
6. PvP 기준이 아직 미정이라 플레이어끼리는 마법 피해가 없다 (`config.yml pvp-worlds: []`).

## 1. 왜 Bridge 수정이 필요한가

`MagicCodexBridge.runtimeCast()` 는 PortableVFX 카탈로그에 있는 마법이면 시각효과 결과만 반환하고 `SpellRuntime.cast()`(MythicMobs)를 부르지 않는다. 카탈로그 95종이 전부 여기에 걸리므로 지금은 `spell-runtime.yml` 에 라우트가 있어도 게임 효과가 실행되지 않는다 (카탈로그 = 시각효과 전용, 의도된 상태였음).

수정안 요약 (`proposals/BRIDGE_CATALOG_GAMEPLAY_20261011.patch`, 2개 파일):

| 파일 | 변경 |
| --- | --- |
| `SpellRuntime.java` | `cast()` 를 `prepare()`(대상 검증·선택, 월드 변경 없음) + `execute()`(유틸 적용 + MythicMobs 스킬 실행)로 분리. 기존 `cast()` 는 두 개를 이어 부르는 형태로 유지 |
| `MagicCodexBridge.java` | `runtimeCast()`: ① 라우트가 있으면 먼저 `prepare` (대상 없음 등은 VFX 없이 실패·환불) ② 카탈로그 VFX ③ `execute`. VFX가 시작된 뒤 스킬이 실패하면 환불하지 않는다(소유자 결정: "실패 시 그냥 소모, 로그만") |

라우트가 없는 카탈로그 마법은 지금과 똑같이 시각효과만 낸다.

## 2. 변경 범위

| 경로 | 내용 |
| --- | --- |
| `magic-mythic-paper/` (새 모듈) | MythicMobs 커스텀 메커닉 7종 + 조건 1종. 8장 참고 |
| `magic-mythic-paper/tools/spells.py` | 마법별 설계표 (원본). 스킬 YAML·spell-runtime.yml·SPELLS.md 를 생성 |
| `magic-mythic-paper/mythicmobs/Skills/chacademi_spells.yml` | 생성된 `CHA_*` 스킬 88개 → 서버 `plugins/MythicMobs/Skills/` 로 복사 |
| `magic-mythic-paper/server-config/spell-runtime.yml` | 생성된 Bridge 라우트 88개 → 서버 `plugins/MagicCodexBridge/` 로 복사 |
| `magic-mythic-paper/SPELLS.md` | 마법별 처리 방식 표 |
| `settings.gradle`, `Build-Server.ps1` | 새 모듈 추가 |
| `docs/handoff/proposals/BRIDGE_CATALOG_GAMEPLAY_20261011.patch` | 위 1장 수정안 (적용 안 함) |

## 3. 마법 처리 현황 (카탈로그 VFX 95종 기준)

- 미몹 스킬로 구현: 72종 (MythicMobs 기본 메커닉만 쓰는 것 + `cha*` 메커닉)
- 기존 Bridge `SpellUtility` 가 처리 (스킬은 빈 실행): 15종
- 기존 라우트 유지: `cinder_detonation`(VFX 목록 밖), `palm_fireworks`(장식, 효과 없음)
- 보류 8종: `wind_message`(Bridge 직접 처리), 슬롯 장착형 상시 효과 5종(`trajectory_acceleration`, `resonant_arrow`, `glass_firing`, `flame_footprints`, `glaze_coating` — Bridge 장착 상태 연동 필요), `flame_forging`(단조 재료표 없음), `blue_bonfire`(마나 회복량 증가는 Bridge 마나 API 필요)

마법별 표는 `magic-mythic-paper/SPELLS.md`.

## 4. 빌드 때 확인할 API (컴파일 못 함)

MythicMobs 5.9.0 / Paper 1.21.4 기준으로 아래 이름이 맞는지 확인한다. 같은 저장소의 tornado·wildlife 플러그인이 이미 쓰는 것은 ✓.

| API | 위치 | 비고 |
| --- | --- | --- |
| `MythicMechanicLoadEvent#getConfig().getString(String,String)` | Params | ✓ tornado |
| `ITargetedEntitySkill` / `ITargetedLocationSkill` / `SkillResult` / `ThreadSafetyLevel` | Mech | ✓ tornado |
| `SkillCondition(String)` + `IEntityCondition` | EnemyCondition | ✓ wildlife(ILocationCondition) |
| `SkillMetadata#getVariables()` | Mech.variable | Bridge가 `putDouble` 사용. 읽기는 리플렉션 (`getDouble`→`get().get()`) |
| `SkillMetadata#getEntityTargets()` | Mech.hasEntityTargets | 리플렉션. 없으면 엔티티+위치 대상 스킬이 두 번 맞을 수 있음 → 실기 확인 |
| `getMobManager().getActiveMob(UUID)` → `getType().getInternalName()` | Targets.mythicType | ✓ 비슷한 호출 있음 |
| `Attribute.MAX_HEALTH`, `EntityType.OAK_BOAT` | Targets, WorldMech | 1.21.3+ 이름 |
| `Sign#getTargetSide(Player)`, `SignSide#line(int,Component)` | WorldMech.sign | Paper |
| `Mob#getPathfinder().moveTo(Location)`, `Mob#setAware` | WorldMech.decoy, Marks | Paper |
| `EntityBlockFormEvent`, `BlockGrowEvent` 생성자 | WorldMech | 보호구역 확인용 가짜 이벤트 |

## 5. 확인할 것 (실기)

1. Bridge 수정안 적용 후 `wind_push`(ENTITY), `ember_bolt`(AIM), `pressure_rupture`(SELF) 각 1회: 마나 차감 → VFX → 피해/밀림.
2. 대상 없이 ENTITY 마법 시전: VFX 없이 "시야 안의 몬스터를 지정해 주세요" + 마나 환불.
3. 펫(`chacademia_pet`), NPC, 길들인 늑대, 다른 플레이어가 맞지 않는지.
4. `air_step` 발판: 20초 뒤 사라지는지, 서버 재시작 후 `temp-blocks.yml` 로 원상 복구되는지.
5. `eternal_ice_prison`: 서버 정지 중 갇힌 몹이 있어도 재시작 후 움직이는지 (onDisable 에서 해제).
6. `/마법미몹 상태` 로 진행 중 개수가 줄어드는지 (누수 확인).

## 6. 남은 일

- 슬롯 장착형 상시 효과 5종, `blue_bonfire`, `flame_forging`, 빗자루 비행 연동.
- 던전 기믹(`gm_*` 모델) 연동: 마법이 기믹에 닿았을 때 신호를 보내는 경로는 이 애드온에 이어서 붙일 예정 (다음 작업).
- 밸런스 검토 후 `tools/spells.py` 수치 갱신.

## 7. 검증 수준

- `tools/spells.py`: 카탈로그 95종이 모두 라우트 또는 보류 목록에 있는지, 스킬 이름이 Bridge 정규식(`CHA_[a-z0-9_]+`)을 지키는지, 대상·사거리 범위를 검사한다. 생성된 YAML 두 개는 YAML 파서로 읽힘을 확인했다.
- Java: Gradle 빌드는 못 했다. 대신 Paper `ver/1.21.4` API 소스 + adventure 4.18 소스 + **MythicMobs 스텁(내가 가정한 API 모양)** 으로 javac 타입 검사를 돌려 이 모듈 파일의 오류 0건을 확인했다 (Bukkit/Paper 쪽 이름은 실제 소스로 검증됨, MythicMobs 쪽은 4장 표대로 빌드 때 확인 필요). `GeometryTest` 는 실행하지 못했다.
- Bridge 수정안 patch: 문법 검사만 통과.

## 8. 메커닉 요약

| 이름 | 하는 일 |
| --- | --- |
| `chahit` | 즉시 타격: 대상 1체 / 반경 / 부채꼴, 밀치기·끌기·내리꽂기·점화·물약효과·처형·체력 소모 폭발·방패 무력화 |
| `chabolt` | 보이지 않는 투사체: 관통·감쇠·유도·되돌아오기·튕기기·블록 파괴(사슬)·착탄 폭발·연속 적중 보상 |
| `chazone` | 지속 지대: 주기 피해·끌어당김·띄우기·아군 회복·화염벽·덫(최대 개수) |
| `chaguard` | 시전자 보호: 치명상 1회 무효 / 투사체 반사 / 정면 투사체 소멸 / 다음 근접 피해 감소 |
| `chamark` | 표식: 재의 낙인(폭발·전이) / 폭발 쐐기 / 빙옥(봉쇄) / 화염 검무(근접 추가타) |
| `chaally` | 아군: 불 끄기 / 정화 / 체력 분배 / 물약 분무 |
| `chaworld` | 유틸: 미끼, 발판, 비행, 정령, 서리 걸음, 화로 가속, 열 회복, 광석 표시, 물 설치, 블록 일렬 설치, 가마솥, 경작지, 작물 성장, 콘크리트, 표지판, 낚시, 보트, 물병, 염색 제거, 사금 |
| 조건 `chaenemy` | 마법 피해 대상인지 (기믹·몹 스킬 작성용) |

대상 규칙(모든 메커닉 공통, `Targets.enemy`): 바닐라 적대 몹 + MythicMobs 몹(단 `CHA_`/`GM_` 접두사 제외), 제외 태그·Citizens NPC·길들인 동물·갑옷 거치대 제외, 플레이어는 `pvp-worlds` 에서만. 블록 변경은 Bridge `SpellUtility` 와 같은 가짜 부수기/설치 이벤트로 보호구역을 확인한다.
