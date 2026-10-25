# 오픈 최종 — 빌드·배포용 패치 모음 (Claude 2026-10-26)

GPT 가 이 문서 하나만 보고 빌드·배포할 수 있게 정리했다. **이 환경에는 Gradle·Paper·Fabric 이 없어 빌드·테스트를 못 했다** (Java 문법 검사, 생성기 실행, 운영 overlay 검사만 함). 호스팅에서 전체 빌드 + 전체 테스트 필수.

- 소스 브랜치: `claude/opening-final-20261025` 위에 다른 세션 패치를 합친 브랜치 (커밋은 이 문서가 들어간 커밋).
- 기준 3개
  - `c96c5ae3` = 운영 최종본 ([LIVE_SOURCE_HANDOFF_20261025](LIVE_SOURCE_HANDOFF_20261025.md)). 렉 1단계·야생 몹 추적 거리 복구(lag-fix-step1)는 이미 여기 있음.
  - `70044a79` = 2026-10-25 밤에 실제로 올라간 오픈 최종 1차 ([CODEX_OPENING_FINAL_DEPLOYED_20261025](CODEX_OPENING_FINAL_DEPLOYED_20261025.md)). 아래 표의 "운영" 칸 = 이미 올라감.
  - 그 뒤 패치는 전부 **아직 안 올라감**.
- 운영 jar 는 서버별 baseline 에 합성하는 방식이었다 (LIVE_SOURCE_HANDOFF "정규 Gradle 빌드만으로 운영 JAR 와 바이트가 같아진다고 보장하지 않음"). 이번에도 그 방식·검사 순서를 따른다.

## 1. 들어간 패치 (기능별)

| 기능 | 무엇이 바뀌었나 | 운영 | 자세한 문서 |
|---|---|---|---|
| 오픈 전 메모 1차 (#1~#57) | 자동시전·지능 명칭·칭호 290·호감도 AI 보상·파티 아군 피해·허수아비·하우징 좋아요/랭킹·장 잠금·튜토리얼 상점·귀환권·아티팩트 천장·운영 AI 도우미·마법 효과음 237 등 | 운영 | [CLAUDE_OPENING_PREP_20261025](CLAUDE_OPENING_PREP_20261025.md), 검수표 [REVIEW](CLAUDE_OPENING_PREP_REVIEW_20261025.md) |
| 스토리 일지 | 퀘스트창 메인 스토리 = 서버 단계 기록, 컷신 본 기록 서버 저장 | 운영 | [CLAUDE_STORY_JOURNAL_20261025](CLAUDE_STORY_JOURNAL_20261025.md) |
| 길잡이 별 = 1장 올레, 1장 대화 번호 ch1-15→ch1-17 | 발견 조건·대화 번호 | 운영 | [CLAUDE_OPENING_FINAL_20261025](CLAUDE_OPENING_FINAL_20261025.md) |
| 낚싯대 설명 | 찌·줄·미끼 줄 제거 | 운영 | 〃 |
| #53 타운맵 | 학교 그림 지도·지점 반짝임·맞추기 명령 | 운영 | CODEX_OPENING_FINAL_DEPLOYED |
| #58 호감도 선물 | 점수 고급 3·유일 7·전설 15, ChacaNPC `gift-likes.yml` (85명×10) | 안 됨 | REVIEW #58 |
| 툴팁 등급 연출 | 고급·유일·전설 툴팁을 구름·빛가루·반짝임으로 | 안 됨 | — |
| #61 사망 귀환 | 야생에서 죽으면 학교 스폰으로 (travel.yml `death-return`) | 안 됨 | REVIEW #61 |
| #67 구조물 다이아 | 다이아 블록 → 공기 (도구 기본값) | 안 됨 | REVIEW #67 |
| #68 수업 | 교수 버튼 → 수업 고르기 → 책 UI, 28수업 47마법, `lessons.yml` + `books.yml lesson_*` + manual-conditions 21종 + 도감 발견 문구 | 안 됨 | REVIEW #68 |
| #69 책 블록 표시 | 연결한 면 앞 홀로그램 책, 새 채널 `magiccodex:book_marks` | 안 됨 | REVIEW #69 |
| 아티팩트 29종 (150개 전부 켬) | 모션 9 · 생활·빗자루 10 · 마법 규칙 5 · 소환·분신·말풍선 5 | 안 됨 | [A](CLAUDE_ARTIFACTS29_A.md) [B](CLAUDE_ARTIFACTS29_B.md) [C](CLAUDE_ARTIFACTS29_C.md) [D](CLAUDE_ARTIFACTS29_D.md) |
| #79 남는 VFX | 조약돌 소환 14틱, 발사 실패 때 대기 화살·검 정리, 클라 끝난 뒤 입자 4초 상한 | 안 됨 | REVIEW #79 |
| #80 사거리 ×1.7 | 전투 마법 조준 거리·투사체 거리·이펙트 거리 1.7배 (`spells.py RANGE_SCALE`) | 안 됨 | REVIEW #80 |
| #81 동행 펫 | 커스텀 펫 276종 = 서버 몹 없이 클라가 그림, 도감 체력 누적, 2·3성 능력, MCPets 커스텀 펫 파일 삭제 | 안 됨 | [DEPLOY](CLAUDE_PET_COMPANIONS_DEPLOY.md) [SERVER](CLAUDE_PET_COMPANIONS_SERVER.md) [CLIENT](CLAUDE_PET_COMPANIONS_CLIENT.md) |
| **마법 300개** (다른 세션, 이번에 합침) | 신규 325~387 (전투 49·생활 14) + 생활·편의 12종 + 꺼진 12개 도감에서 빼고 300개를 원소별·가중치순으로 다시 번호, 175 졸음 켬, 1번 이름 "깃털 낙하" | 안 됨 | [CLAUDE_NOVA_325_387_20261025](CLAUDE_NOVA_325_387_20261025.md), 구현표 [NOVA_325_387](NOVA_325_387.md) |
| 신규 전투 마법 사거리 ×1.7 (이번에 맞춤) | #80 규칙을 신규 전투 마법에도: 조준 거리(10 이상)·투사체 비행 거리·대상 확인 거리 1.7배, 이펙트 range 30→51. 시전자 앞 고정 모양(갈라짐·행진·균열·거인 발걸음)·돌진 거리(천공 강하)는 그대로 | 안 됨 | 아래 §8 |
| 스토리 포탈 셰이더팩 (이번에 이식) | Iris 셰이더팩 켜면 포탈 빛 띠가 깨지던 것 (story-steps 8545f195, 통합본에 빠져 있었음) | 안 됨 | CLAUDE_STORY_STEPS_20261020 |
| 빗자루 궤적 팩 (이번에 넣음) | `BroomTrails`·`BroomTrailsWood`·`BroomTrailsUnique`·`BroomTrailsLegend` 원본이 저장소에 없었음 → broom-30 브랜치에서 가져옴 | 런처 확인 필요 | CLAUDE_BROOMS30_20261016 |
| 운영 배포 기록 문서 | 타운맵 배포 영수증 (문서만) | — | CODEX_OPENING_FINAL_DEPLOYED_20261025 |

## 2. 빌드할 것과 놓을 곳

70044a79 뒤에 바뀐 모듈만 적었다. "서버" 칸의 학교·야생·아레나 = `C:\Chacademi\network\<school|wild|arena>\plugins\`. 튜토리얼 서버(tutorial_1·2)는 아직 없음 — 만들면 **학교와 같은 묶음**.

| Gradle 모듈 | 빌드 | 플러그인/모드 이름 | 놓을 곳 |
|---|---|---|---|
| `magic-codex-paper` | 루트 `:magic-codex-paper:build` | MagicCodexBridge | 학교·야생·아레나 `plugins/` |
| `magic-mythic-paper` | 루트 `:magic-mythic-paper:build` | ChAcademiaMagicMythic | 학교·야생·아레나 `plugins/` (운영은 서버별 jar) |
| `magic-discovery-paper` | 루트 `:magic-discovery-paper:build` | MagicDiscovery | 학교·야생·아레나 `plugins/` |
| `chaca-npc-paper` | 루트 `:chaca-npc-paper:build` | ChacaNPC | 학교 `plugins/` |
| `creature-spawns-paper` | 루트 (`-PmythicJar` `-PmagicCodexBridgeJar` 필요) | ChAcademiaWildlife | 야생 `plugins/` |
| `tornado-event-paper` | 루트 `:tornado-event-paper:build` | ChAcademiaTornado | 깔린 서버 전부 (학교·야생) |
| `portable-vfx-runtime` `:paper` | `-p portable-vfx-runtime :common:protocolTest :client:test :client:remapJar :paper:test :paper:build` | PortableVFX | 학교·야생·아레나 `plugins/` |
| `portable-vfx-runtime` `:client` | 위와 같음 | PortableVFX 클라 | 클라 런처 `mods/` |
| `chacademy-motion/chacademy-motion-plugin` | `-p chacademy-motion/chacademy-motion-plugin test build` | ChacademyMotion | 학교·야생·아레나 `plugins/` |
| `chacademy-motion/chacademy-motion-mod` | `-p chacademy-motion/chacademy-motion-mod test remapJar -PplayerAnimatorJar=…` | chacademy-motion 모드 | 클라 `mods/` |
| `magic-codex-fabric` (+ `magic-codex-protocol` 포함) | `-p magic-codex-fabric test remapJar` — **전체 빌드** (새 클래스 SpellChargeClient·MapPingClient·PetCompanions 등, 일부만 바꾸면 크래시) | MagicCodex | 클라 `mods/` |
| `chacademy-story/mod` | `-p chacademy-story/mod test remapJar` | ChacademyStory 클라 | 클라 `mods/` (플러그인은 70044a79 그대로라 다시 안 올려도 됨) |

안 바뀐 것 (70044a79 그대로): chacademi-party-velocity, housing-paper, kyudichi-paper, chaca-portrait-paper, gimmick-paper, chacademy-story/plugin.

## 3. 운영 설정 (`deployment/operating-config/**`)

`python3 tools/operating_overlay.py check` → **"차이 없음"** 확인함 (manifest sha256 도 갱신됨). 서버 데이터 폴더 파일은 jar 를 바꿔도 자동으로 안 바뀌므로 아래 파일을 직접 교체한다.

| 저장소 파일 | 서버 위치 | 학교 | 야생 | 아레나 | 메모 |
|---|---|---|---|---|---|
| `MagicCodexBridge/mana-spells.yml` | `plugins/MagicCodexBridge/mana-spells.yml` | O | O | O | 세 곳 같은 파일. 300개 + 버린 12개(꺼짐) |
| `MagicCodexBridge/spell-runtime.yml` | `plugins/MagicCodexBridge/spell-runtime.yml` | O | O | O | 같은 파일. 사거리 1.7배 반영, `range-multiplier: 1.0` |
| `MagicCodexBridge/artifacts.yml` | `plugins/MagicCodexBridge/artifacts.yml` | O | O | O | 같은 파일. 150개 모두 켬 |
| `MagicCodexBridge/climate.yml` | `plugins/MagicCodexBridge/climate.yml` | O | O | (있으면) | 같은 파일 |
| `MagicCodexBridge/pet-companions.yml` | `plugins/MagicCodexBridge/pet-companions.yml` | O | O | O | 같은 파일. 바꾼 뒤 `/서버관리 새로고침 펫` |
| `PortableVFX/spell-catalog.yml` | `plugins/PortableVFX/spell-catalog.yml` | O | O | O | 같은 파일 |
| `PortableVFX/spell-bindings.yml` | `plugins/PortableVFX/spell-bindings.yml` | O | O | O | 같은 파일 |
| `MythicMobs/Skills/chacademi_spells.yml` | `plugins/MythicMobs/Skills/chacademi_spells.yml` | O | O | O | 같은 파일. 새 메커닉(chanova)이라 **서버 재시작** (`/mm reload` 로는 등록 안 됨) |
| `MythicMobs/Skills/chacademi_pet_skills.yml` | `plugins/MythicMobs/Skills/chacademi_pet_skills.yml` | O | O | O | 새 파일, 같은 파일 |
| `ChacademyStory/steps.yml` | `plugins/ChacademyStory/steps.yml` | O | — | — | 운영 원문 보존용 사본 (이번에 안 바뀜) |

## 4. 손으로 바꿔야 하는 설정 (overlay 에 없음, 서버 파일이 있으면 jar 가 안 덮어씀)

| 서버 파일 | 할 일 | 서버 |
|---|---|---|
| `MagicCodexBridge/lessons.yml` | 저장소 `magic-codex-paper/src/main/resources/lessons.yml` 로 (새 파일이면 jar 가 깔아 줌) | 학교 (튜토리얼) |
| `MagicCodexBridge/books.yml` | 저장소 books.yml 의 `lesson_*` 28권 추가 (운영에서 고친 책은 합치기) → `/서버관리 새로고침 책` → `새로고침 수업` | 학교·야생 (책 쓰는 곳) |
| `MagicCodexBridge/travel.yml` | `death-return:` 블록 복사 | 학교·야생 |
| `MagicCodexBridge/titles.yml`·`title-awards.yml` | 주석 한 줄(깃털 낙하)만 바뀜 → 안 바꿔도 됨 | — |
| `MagicCodexBridge/donation-spells.yml` | 새 75종은 서버가 켤 때 **자동으로 덧붙임** (기존 값 유지) | — |
| `MagicCodexBridge/spell-elements.yml` | jar 안 파일 (무지개 프리즘용), 새 75종 추가함 → 할 일 없음 | — |
| `MagicDiscovery/manual-conditions.yml` | 저장소 판으로 교체 (수업 21종 + 길잡이 별) | 학교·야생·아레나 |
| `MagicDiscovery/discoveries.yml` | 저장소 판으로 교체 (수업 발견 문구) | 학교·야생·아레나 |
| `ChacaNPC/gift-likes.yml` | 새 파일 (없으면 첫 시작에 생김, 있으면 교체) | 학교 |
| `ChacaNPC/config.yml` | `gifts.grades` 를 일반 1·고급 3·유일 7·전설 15 로 | 학교 |
| `ChacaNPC/npcs/<교수>.yml` | 교수 6명 buttons 에 `{ id: lesson, label: "수업 받으러 왔어요", action: "lesson:<키>" }` (키 harva·cedric·elena·berna·isolde·gregor). NPC당 버튼 3개까지라 인사 버튼 자리를 씀 | 학교 |
| `ChacademyMotion/config.yml` | `allowed-animations` 에 artifact_dance·wave·doze·bow·laugh·shell_hide·stretch·tap_dance 8줄 → `/서버관리 새로고침 모션` (`stop` 은 넣지 말 것) | 학교·야생·아레나 |
| `MCPets/Pets/ChAcademiaCustom/ca_*.yml` | **지우기** (아래 §6) | 학교·야생·아레나 |

자동 생성 DB 표: **c96c5ae3 뒤로 새 표 없음** (codex_title_awards·codex_artifact_pity·codex_shop_tutorial·codex_housing_like 는 이미 운영). 동행 펫은 `codex_player_state.artifact_runtime` 앞에 `cpet=` 토큰만 붙임 (컬럼 추가 없음).

## 5. VFX 팩 (클라 `config/portablevfx/effects/<팩>/`)

팩이 클라에 없으면 카탈로그가 그 마법을 막는다(신규 마법) / 그 효과만 안 보인다(아티팩트·펫·궤적).

| 팩 | 원본 | 쓰는 곳 |
|---|---|---|
| `PetPaw` | 저장소 `portable-vfx-runtime/assets/PetPaw` | 동행 펫 능력 발동 (`claude:petpaw/circle`, `circle_shiny`) |
| `JackInTheBox` | 저장소 `portable-vfx-runtime/assets/JackInTheBox` | 아티팩트 깜짝 상자 |
| `ArtifactCompanions` | 저장소 `portable-vfx-runtime/assets/ArtifactCompanions` | 아티팩트 화염 정령·분신 등 |
| `BroomTrails`·`BroomTrailsWood`·`BroomTrailsUnique`·`BroomTrailsLegend` | 저장소 `portable-vfx-runtime/assets/BroomTrails*` (이번에 넣음) | 빗자루 궤적. 런처 client-assets 에 이미 있으면 그대로 (해시 비교) |
| `Drowsiness` + 신규 75팩 + 다시 만든 어둠 18팩 | **저장소에 없음** — 오너 PC `차카데미 vfx_Claude\<팩>` | 마법 300개. 어둠 18팩은 같은 이름으로 교체 (옛 zip 은 각 폴더 `_이전_20261025\`) |

신규 75팩 이름: AbyssGate, AirCannon, AirChoke, AlchemyPreheat, AuroraVeil, BlessedRibbon, BottleFill, BoulderCharge, BroomAfterburner, CalmRipple, CampfireRest, CrimsonSpiral, CropSnare, CropSurvey, CrystalCoat, DreadDream, DuskOrb, EbbMark, EmberPuff, EnlightenHint, EruptionLine, FateLink, FaultRift, FlameEmperor, FlameJavelin, FlameWheel, FlashBlink, FreezeField, FrostPath, GaleCombo, HaloThrow, HarvestBell, HeartRead, HeatHaze, HeavenCyclone, HerdingBreeze, HexDoll, HydroBurst, IceBloom, IceSerpent, IceShard, InsightFlash, LeviathanRise, LightStep, MemoryLantern, MiniTwister, PackSort, PrismCage, PrismShot, ReplantTouch, SandFlick, ScaleAppraisal, ScorchLock, SeasonSense, ShadowFang, ShadowMire, ShadowStep, ShadowVault, SignalFlare, SkyDive, SparkFlick, StormEye, SunlanceDrop, SweetSleep, Tailwind, Thundercloud, TideSlash, TitanStride, TreasureScent, VacuumBurst, VolcanicBarrage, WardCandle, WaterGlimpse, WindNeedle, WraithMarch.
다시 만든 어둠 18팩: NightSight, EchoReading, SealingThread, ForbiddenContract, LifeSiphon, misfortune_whisper, HungryShadow, CurseTransfer, Discord, PainDeferral, InkFingertip, ShadowNail, LoosenGrasp, HealingSeal, WoundAccounting, InvertedOffering, GhostTool, BellOfFear.

`deployment/external-vfx-assets.json` = 런처 v0.1.10 client-assets 의 목록(163팩)일 뿐 배포 입력이 아니다. 지금까지 새 팩(PetPaw·JackInTheBox·ArtifactCompanions·GuidingStar 이후 팩)은 이 목록을 안 고쳤다. 런처 client-assets 를 새로 만들면 그 결과로 목록을 다시 쓰는 것을 권장 (path·bytes·sha256).

## 6. 지울 파일

- `deployment/mcpets-companion-removal.json` 의 `delete` 276개 = 각 서버 `plugins/MCPets/Pets/ChAcademiaCustom/ca_*.yml`. 서버를 정상 종료한 뒤, 백업하고 지운다.
- 그대로 둘 것: `ChAcademiaTaming`·`ChAcademiaShiny` 폴더, MythicMobs `CA_Pet_*` 몹, 권한 `chacademia.pet.*`. 자세히: [CLAUDE_PET_COMPANIONS_DEPLOY](CLAUDE_PET_COMPANIONS_DEPLOY.md).
- 클라 도감 기본 파일: 클라 모드가 알아서 처리 (`renamed-defaults.tsv` 로 옛 이름 → 새 이름, `dropped-defaults.txt` 12개는 배포 그대로인 파일만 지움, 플레이어가 고친 파일은 둠).

## 7. 서버·클라를 같이 올려야 하는 것 (프로토콜)

| 채널 / 버전 | 서버 쪽 | 클라 쪽 | 옛 버전과 섞이면 |
|---|---|---|---|
| `magiccodex:pet` PetProtocol **03** (Entry.info, Response.summary) | Bridge | MagicCodex 모드 | 펫 창이 안 열림 |
| `magiccodex:pet_companions` (새) | Bridge | MagicCodex 모드 | 펫이 안 보임 (효과는 동작) |
| `magiccodex:book_marks` (새) | Bridge | MagicCodex 모드 | 책 표시만 없음 |
| `magiccodex:artifact_fx` (새) | Bridge | MagicCodex 모드 | 정령·분신·앵무새·말풍선 안 보임 |
| `magiccodex:mana_heat` (새) | Bridge | MagicCodex 모드 | 거꾸로 된 마나병 표시만 없음 |
| `chacademy:broom_perk` (새, 아티팩트) · `chacademy:broom_buff` (새, 마법 375·380) | ChacademyMotion 플러그인 | chacademy-motion 모드 | 채널 등록을 보고 보냄 → 옛 모드엔 안 보냄. 375·380 은 거절·마나 환급 |
| `chacademia:spell_charge` (새, 338 충전) · `chacademia:map_ping` (새, 376 봉화) | MagicMythic | MagicCodex 모드 | 충전 없이 바로 시전 / 지도 표시 없음 |
| 도감 300개 (번호 바뀜) | Bridge mana-spells·MagicMythic·PortableVFX 카탈로그 | MagicCodex 모드 | 번호·목록이 서로 안 맞음 |
| 사거리 1.7배 | spell-runtime·미몹 스킬·bindings | MagicCodex 모드 (spell-targeting.json) | 조준 원과 서버 판정이 다름 |
| `chacademy:anim` 예약 이름 `stop` | MagicCodexBridge(아티팩트 모션) | chacademy-motion 모드 | 반복 모션이 안 멈춤 |

## 8. 이번 합치기에서 정한 것 (확인용)

- **빗자루**: 아티팩트 PERK 채널과 마법 BUFF 채널을 둘 다 등록·처리. 모드 속도 상한 = `maxSpeed × speedBonus(아티팩트) × 부스트 × speedMult(마법 버프)`, 서버 속도 검사 상한 = `maxSpeed × guardMul(아티팩트) × speedMult(버프) × 1.45 + 0.6`.
- **신규 전투 마법 사거리**: 조준 거리 1.7배 (예 바람 송곳 24→41, 화염 투창 30→48·투사체 최대 51, 그림자 송곳니 12→20). 투사체 비행 거리·대상 확인 거리(작열의 표적 25→42, 운명의 실 16→27, 숨 막힘 유지 20→34)도 같이. `NovaBasic.pick()` 의 고정 거리(12·16)를 `range` 값으로 바꿈 — 안 바꾸면 늘어난 거리에서 "대상이 없어요". 이펙트 binding range 30→51 (nova_spells.py 가 spells.py 결과를 보고 넣음).
- **도감 기본 파일**: 300개 새 이름에 우리 쪽 발견 문구 변경(수업·올레·교화)을 옮김. `known-hashes.tsv` = 양쪽 줄 합침 + 옛 이름의 해시를 새 이름에도 넣음 (예전 클라 파일이 새 이름으로 옮겨진 뒤 갱신되도록). 빛의 서고(luminous_archive)는 저쪽이 도감에서 뺐으므로 뺌.
- **기증 목록·원소표**: donation-spells.yml·spell-elements.yml 에 새 75종 (기증 1점, 오너 #24).
- 테스트 `CurrentReviewedMappingTest` 는 번호(101~167, order<300)로 묶음을 가르던 것을 id 목록으로 바꿈 (재정렬 뒤 번호로는 못 가름). ManaTest·GimmickCatalog·DefaultSpellMigration 등은 저쪽이 고친 값 그대로.

## 9. 확인 못 함 / 오너·GPT 확인 필요

1. 전체 빌드·테스트 (특히 magic-discovery 3개 테스트, AimRangeContractTest, BroomCodecTest, DefaultSpellMigrationTest, ManaTest 300/312).
2. **수업 빛 기초(lesson_light_2)가 빛의 서고(luminous_archive)를 가르치는데, 이 마법은 300개 재정렬에서 꺼진 채 도감에서 빠짐** — 조건만 주고 쓸 수는 없음. 수업에서 뺄지 오너 결정 필요 (지금은 그대로 둠).
3. 메테오 스트라이크(falling_star) 선행 조건에 도감에서 빠진 꺼진 마법 3개(cinder_detonation·flame_footprints·fireproof_film)가 그대로 있음 → 메테오를 배울 수 없을 수 있음 (예전부터 그랬는지 확인).
4. 175 졸음(drowsiness)이 켜졌지만 습득 조건이 없음. 신규 75종도 습득 조건 없음 (오너가 나중에 한꺼번에 — 저쪽 인계 그대로). 시험은 `/테스트 마법 시전 <id>`.
5. 신규 75종: 효과음(spell-sounds.yml)·마법 칭호(titles.yml)·도감 아이콘(`icon: ''`) 없음.
6. 저쪽 인계의 미확인 API: `Player.getCurrentInput()`(339), `Mob.getPathfinder().moveTo`(339), 368 재시전 `MagicCastAttemptEvent` 리플렉션. 생활 마법 효과는 서버 메모리라 재접속·서버 이동 때 사라짐.
7. 기존 문제(안 고침): `CastAimClient.initialize()` 가 빠져 있는데 `AimTargeting.confirm()` 이 `CastAimClient.send()` 를 부름 — 서버가 `magiccodex:cast_aim` 을 등록하면 예외 가능.
8. 빗자루 궤적 팩 4개가 지금 런처 client-assets 에 있는지 (저장소엔 이번에 처음 들어옴).
9. `tools/arcana_spells.py`·`mob_spells.py`·`gimmick_spells.py` 는 돌리지 말 것 (손으로 고친 값을 덮어씀). `nova_spells.py` → `spells.py` 순서만 안전.

## 10. 다시 만들 때

```
python3 magic-mythic-paper/tools/nova_spells.py && python3 magic-mythic-paper/tools/spells.py
python3 magic-mythic-paper/tools/pet_companions.py        # 펫 데이터 (필요할 때만)
python3 tools/operating_overlay.py regenerate && python3 tools/operating_overlay.py check   # "차이 없음" 이어야 함
```

## 11. 합친 브랜치 확인 결과

| 브랜치 | 결과 |
|---|---|
| `claude/magic-325-387-20261025` (18커밋) | 합침 (충돌 19개 양쪽 합치기, §8) |
| `claude/lag-fix-step1-20261025` | 이미 있음 (c96c5ae3 에 Codex 보강본) — INDEX 줄만 |
| `review/opening-final-deployment-receipt-20261025-task3` | 합침 (문서) |
| `claude/wildlife-spawner-20261023` (7) · `npc-buttons-20261023` (4) · `hires-item-world-20261023` (5) | 이미 있음 (커밋별 추가 줄 90% 이상, 나머지는 뒤 커밋이 고친 줄) |
| `claude/story-steps-20261020` (4) | 길잡이 별·HUD Ctrl·대사 서버 전송은 이미 있음, **포탈 셰이더팩 8545f195 만 빠져 있어 이식** |
| 10-11~10-20 1커밋 브랜치 (aim-camera, artifact-*, broom-*, alchemy*, housing*, kyudichi*, fishing, gimmick-plugin*, magic-mythic, magic-71-gameplay, mob-telegraph, reaction-*, shop-redesign, shoulder-crosshair, unit-target-ring, duel-spells-fix, dungeon-gimmick-models) | 이미 있음 (핵심 클래스 비교, 없어진 파일은 뒤에 일부러 지운 것). 빠진 것: **빗자루 궤적 팩 4개 + 원본(broom-30) → 넣음**. `dungeon-gimmicks/` 원본 bbmodel 폴더는 오너 보류 결정으로 안 넣음 (쓰는 모델은 gimmick-paper/models 에 있음) |
