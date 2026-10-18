# 대상 체력바 HUD + 상태이상 16종 (Claude, 2026-10-19)

브랜치: `claude/status-hud-20261019` (기준: `codex/operating-latest-20261018` @ `6fe06f0`)

## 0. 상태

- **빌드하지 않았습니다.** Gradle·실게임 모두 확인하지 못했습니다.
  - 새 프로토콜 2개와 HUD 계산(`TargetHudLayout`)만 javac로 컴파일하고 테스트 5개를 돌렸습니다(통과).
  - 나머지 Paper·Fabric 코드는 API 시그니처를 손으로 검토했습니다. 컴파일 오류가 나면 아래 "4. 확인 부탁"부터 봐 주세요.
- 오너 결정(2026-10-19): 레퍼런스(CHACADEMI_BOSS_HUD)대로 대상 체력바를 교체합니다.
  - 대상: 최근 10초 안에 전투한 몹, 없으면 바라보는 몹. 플레이어는 표시, 내 펫은 표시하지 않습니다.
  - 바닐라 보스바는 이 HUD로 대체합니다.
  - 머리 위 교화 카드는 없애고, 머리 위에는 원소 표식과 디버프만 띄웁니다.
  - 디버프 15종에 공포를 더해 16종입니다. 효과만 구현했고 마법에는 연결하지 않았습니다(기존 부가효과만 매핑).
  - 마법에서 바닐라 불을 제거하고 화상으로 바꿨습니다.
  - 바닐라 효과도 아이콘으로 보여 주고, 바닐라 효과 HUD는 숨깁니다.
  - 원소 반응 재기획은 이번 범위가 아닙니다.

## 1. 함께 배포해야 하는 것

| jar | 바뀐 점 | 버전 |
|---|---|---|
| ChAcademiaMagicMythic (magic-mythic-paper) | 상태이상 엔진 `StatusEffects`, 새 채널 `magiccodex:status`, `chastatus` 메커닉, `/마법미몹 상태이상` | `...-carrier.1-status.1` |
| MagicCodexBridge (magic-codex-paper) | 대상 HUD 서버 `TargetHudBridge`, 새 채널 `magiccodex:target_hud`, 불타는 대상 판정을 화상으로 변경 | `0.31.1-integrated-20261018.1-statushud.1` |
| MagicCodex 클라 모드 (magic-codex-fabric) | 대상 HUD·머리 위 아이콘·내 디버프·새 원소 아이콘 | `0.40.1-integrated-20261018.1-statushud.1` |
| MythicMobs 스킬 | `chacademi_spells.yml`: 수압해머 `effect=SLOWNESS:20:4` → `status=stun:20` | - |

- 학교·야생 서버 모두 해당합니다.
- `deployment/operating-config/MythicMobs/Skills/chacademi_spells.yml`(옛 형식)은 고치지 않았습니다. 운영에 쓰는 파일이면 수압해머 줄도 같이 바꿔 주세요.
- 클라 없이 서버만 올려도 동작합니다(아이콘만 안 보임). 서버 없이 클라만 올리면 대상 HUD가 안 뜹니다. 이전에 쓰던 교화 QUERY를 클라가 더 이상 보내지 않기 때문입니다.

## 2. 서버: 상태이상 (`magic-mythic-paper/.../StatusEffects.java`)

| 상태 | 효과 |
|---|---|
| 기절 | 이동·점프·근접·활·마법·아티팩트 불가, 몹은 AI 정지(기존 PDC 복구 규칙). 보스는 둔화로 바뀜 |
| 수면 | 기절과 같고, 피해를 받으면 깸. 보스는 둔화로 바뀜 |
| 속박 | 이동·점프만 불가. 보스 면역 |
| 무장해제 | 근접·활·마법 불가 |
| 둔화 | 이동·비행 속도 감소(power, 최대 0.9) |
| 화상 | 몸에 불이 붙지 않음. 지속 중 받은 피해 합의 10%를 끝날 때 한 번 더 줌 |
| 회복봉인 | `EntityRegainHealthEvent`·마법 치유(`Targets.heal`) 차단, 마나 자연 회복 배수 0(`status_recovery_seal`), `Links.add`(마나 물약·마나석) 거부 |
| 약화 | 내가 주는 마법 피해 ×0.7(`Targets.hurt`/`hurtProc`) |
| 중력 | 빗자루 하차(`BroomLink.stop`)·바닐라 비행·겉날개 끔, 비행 마법 시전 불가, 나는 몹은 아래로 끌어내림 |
| 고정 | 이동 마법(`status.movement-spells`) 시전 불가, 걸린 뒤 새로 탄 빗자루도 내림 |
| 독 | 초당 1 피해, 기본은 체력 1 아래로 내리지 않음 |
| 나약함 | 마법이 아닌 피해 ×0.7 |
| 실명 | 플레이어: 바닐라 실명(아이콘 숨김), 몹: 3블록보다 먼 대상을 놓침 |
| 물약봉인 | 물약 마시기·던지기, 마나 물약 사용 불가 |
| 마나번 | 걸리는 순간 최대 마나의 10%를 빼앗음. 아이콘이 떠 있는 동안(내부 쿨 5초)은 다시 걸리지 않음. 몹 마나는 아직 없음 |
| 공포 | 몹은 건 플레이어(없으면 가장 가까운 플레이어)에게서 도망가고 공격하지 않음. 플레이어는 건 대상에게서 밀려남. 보스는 둔화로 바뀜 |

**바닐라 효과 표시(효과는 바닐라 그대로):** 구속은 둔화, 독·위더는 독, 실명·어둠은 실명, 나약함은 나약함, 몸에 붙은 불(용암 등)은 화상 아이콘으로 보여 줍니다.

### 시전·아티팩트 차단

- `MagicCastAttemptEvent`·`ArtifactUseAttemptEvent`를 이름으로 등록했습니다. 큐디치와 같은 리플렉션 방식이라 컴파일 의존이 없습니다.
- 막히면 액션바에 이유가 표시됩니다.

### 기존 부가효과를 바꾼 곳

| 위치 | 변경 |
|---|---|
| `ignite=` (HitMech·BoltMech·ZoneMech 2곳), Passives `ignite-ticks`, 반응 용암 | 화상 (바닐라 불 없음) |
| `effect=SLOWNESS:d:a` | 둔화 status (단계당 15%, 같은 시간). 서리 파열·심해의 압력·지진·그림자 매듭에 해당 |
| Marks prison (만년빙옥) | 일반 몹은 기절(피해 20%면 해제). 보스·플레이어는 둔화 45% |
| Marks stun (반격의 무게추) | 기절 |
| Marks root (자재 치환) | 속박 |
| 수압해머 | 1초 기절 |
| 정화의 물결 | 독·나약함·둔화 status도 지움 |
| 여명의 의식 | 모든 status를 지움 (IMMUNE 가드가 있는 동안 새 status도 거부) |
| 불길 씻기, 불사조의 서약 | 화상도 끔 |
| Bridge `SpellRuntime` BURNING_ENTITY (스타 퓨리) | `hasStatus(burn)`으로 판정 (바닐라 불도 인정) |

- 새 파라미터 `status=<id>:<틱>[:<세기>],...`를 모든 cha* 적중 메커닉에서 쓸 수 있습니다. 몹 전용 마법용으로 `chastatus{type=;ticks=;power=}` 메커닉도 추가했습니다.

### 테스트 명령

- `/마법미몹 상태이상 <종류|해제|목록> [초] [나|바라봄|플레이어] [세기]`
  - 예: `/마법미몹 상태이상 기절 3 바라봄`
- `/마법미몹 상태`에 상태이상 개수가 함께 나옵니다.

### 설정

- 수치는 모두 임시값입니다. `config.yml`의 `status:` 섹션을 보세요.
- 기존 서버의 config에 섹션이 없어도 코드 기본값으로 동작합니다.

## 3. 대상 HUD

### 서버 `TargetHudBridge`

- 5틱마다, 채널을 등록한 플레이어만 처리합니다.
- **대상 선택:** 최근 10초 안의 전투 상대(내가 때렸거나 나를 때린 대상, 48블록 안)를 먼저 고릅니다. 없으면 `TamingBridge.lookTarget`(교화 정보 레이, ModelEngine 서브 히트박스, 25블록)으로 바라보는 대상을 고릅니다.
- **제외 대상:** 내 MCPets 펫(`PetBridge.ownedBy`), 아머스탠드, NPC, 비전투 중인 투명 대상.
- **보내는 내용:**
  - 이름: 교화 프로필의 한글 이름, 없으면 커스텀 이름, 그것도 없으면 번역 키(클라가 한글로 표시). 플레이어는 MagicCodex 닉네임입니다.
  - 체력, 마나(플레이어만, 몹은 -1), 내가 준 누적 피해, 교화 확률(-1이면 숨김), 이로치·보스·전투 플래그.
- 값이 바뀔 때만 보내고, 그 외에는 1초마다 다시 보냅니다.
- 클라가 보내던 교화 QUERY(2회/초)는 없앴습니다. 서버 레이 횟수는 최대 4회/초로, 전투 대상이 있으면 레이를 돌리지 않습니다.

### 클라 `TargetHudClient`

- 에셋: `textures/gui/target_hud/`
  - 레퍼런스 PNG를 0.6배로 잘랐습니다. `frame_hp.png`는 마나 줄을 뺀 프레임입니다.
  - 칸 좌표는 PNG에서 측정해 `TargetHudLayout.SLOTS`에 넣었습니다.
- 체력바는 6칸 고정입니다. 끝이 걸린 칸은 밝게 그리고, 막대는 부드럽게 줄어듭니다.
  - 칸 채움은 새 `HudTextureCache.drawPart`로 그립니다(한 텍스처의 UV만 잘라 그림). 매 프레임 새 텍스처를 만들지 않습니다.
- 큐디치 경기 중에는 점수판 아래로 내려갑니다.
- 아이콘 줄:
  - 체력바 아래 오른쪽: 디버프를 오래된 것부터 오른쪽 끝에 채우고, 원소 표식은 그 왼쪽에 둡니다. HUD에서는 전부 보입니다.
  - 머리 위(`ElementMarkClient`): 원소 표식 1개를 왼쪽에, 디버프 최대 3개를 오른쪽에 두고, 넘치면 "…"을 표시합니다. 내 머리 위에는 그리지 않습니다.
  - 내 디버프: 화면 오른쪽 위(바닐라 효과 아이콘이 있던 자리), 10개마다 줄바꿈합니다.
- 바닐라 효과 HUD는 `IdentifiedLayer.STATUS_EFFECTS`를 지워서 없앴습니다(버프 포함, 오너 결정). 인벤토리 화면 옆 효과 목록은 그대로입니다.
- 보스바는 `BossBarFilterMixin`(`BossBarHud.render`의 `Map.values()` Redirect)으로 걸러냅니다.
  - 숨기는 것: 바닐라 엔티티 보스바(번역 키 `entity.*`), 이름이 근처 커스텀 이름 몹이나 현재 HUD 대상과 같은 보스바(MythicMobs).
  - 큐디치 등 다른 보스바는 남습니다.
- `TamingClient`: 머리 위 교화 카드(`renderMonster`)와 주기 QUERY를 없앴습니다. 교화 시전·채널링 창은 그대로입니다. `hidesVanillaName`은 항상 false입니다.
- 원소 아이콘 6종을 새 PNG로 교체했습니다(`textures/element/*.png`, 256px).
- 미리보기: `/대상체력바미리보기 [일반|이로치|플레이어|끄기]`
  - 가짜 데이터로 15초 동안 보여 주고, 서버로는 아무것도 보내지 않습니다.
  - 내 디버프 아이콘도 함께 미리 보여 줍니다.

## 4. 확인 부탁 (빌드 때)

1. Fabric 쪽 이름 확인
   - `IdentifiedLayer.STATUS_EFFECTS`, `drawer.removeLayer`(fabric-api 0.119.4).
   - `BossBarHud.render`에 `Map.values()` 호출이 실제로 있는지. `defaultRequire 1`이라 없으면 시작할 때 크래시가 납니다.
2. Paper 쪽 이름 확인: `EntityType#translationKey()`, `TNTPrimed#getSource()`, `AttributeModifier#getKey()`, `PotionEffect#isInfinite()`, `Mob#getPathfinder().moveTo(Location,double)`.
3. `MagicProtection.magicDamaging()`으로 나약함(마법 아님)과 약화(마법)를 구분합니다. 패시브 화살(`procDamage`)은 마법으로 취급됩니다.
4. 반응 수정화(`Reactions.frozen`)는 여전히 자기 방식으로 AI를 멈춥니다. 같은 몹에 기절이 겹치면 해제 순서가 꼬일 수 있습니다. 원소 반응 재기획 때 status 기절로 옮길 예정입니다.
5. 화염의 정복자(화염 지속 피해 면역)는 바닐라 FIRE_TICK만 막습니다. 화상 마지막 추가 피해를 막을지는 오너에게 확인이 필요합니다.
6. 숲의 왕좌(자기 고정, BuffMech root)는 디버프가 아니라서 아이콘이 없습니다.

## 5. 파일

- 프로토콜: `magic-codex-protocol/.../StatusProtocol.java`, `TargetHudProtocol.java`
- 서버(MagicMythic): `StatusEffects`, `StatusMech`, `StatusCommand`
  - 변경: `MagicMythicPlugin`, `Marks`, `Targets`, `HitMech`, `BoltMech`, `ZoneMech`, `Passives`, `Reactions`, `Guards`, `AllyMech`, `UtilMech`, `Links`, `config.yml`, `build.gradle`, `tools/spells.py`, `SPELLS.md`, `mythicmobs/Skills/chacademi_spells.yml`
- 서버(Bridge): `TargetHudBridge`
  - 변경: `MagicCodexBridge`, `TamingBridge`, `PetBridge`, `MagicProtectionLink`, `SpellRuntime`, `build.gradle`
- 클라: `TargetHudClient`, `TargetHudLayout`, `StatusClient`, `StatusIcons`, `mixin/BossBarFilterMixin`
  - 변경: `ElementMarkClient`, `TamingClient`, `HudTextureCache`, `MagicCodexClient`, `magiccodex.mixins.json`, `gradle.properties`
  - 에셋: `textures/gui/target_hud/*`, `textures/status/*`(16), `textures/element/*`(교체)
- 테스트: `magic-codex-fabric/src/test/.../StatusHudProtocolTest.java`
