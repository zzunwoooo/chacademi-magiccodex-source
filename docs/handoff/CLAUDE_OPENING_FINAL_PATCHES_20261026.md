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
| `Drowsiness` + 신규 77팩 + 다시 만든 어둠 18팩 | 저장소 `portable-vfx-runtime/assets/<팩>` (2026-10-26 올림, 목록: [CLAUDE_VFX_PACKS_20261026](CLAUDE_VFX_PACKS_20261026.md)) | 마법 300개. 어둠 18팩은 런처판을 **교체** |

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

## 12. 꺼진 마법 12개 완전 삭제 + 수업 300개 기준 (Claude 2026-10-26, 오너 결정)

오너: "지금 새로 만들어서 300개 채웠으니 나머지는 기획에서 지워", "수업은 최신 300개 기준으로". 위 §9 의 2·3번은 이걸로 해결.

**지운 12개**: aerial_haul(부유) · descent_command(낙하 명령) · cinder_detonation(스타 퓨리) · flame_footprints(불꽃 걸음) · fireproof_film(내연성) · water_mirror(수면의 거울) · vine_ladder(덩굴 사다리) · automatic_supply(자동 보급) · light_mark(빛의 낙인) · luminous_archive(빛의 서고) · experience_exchange(경험의 환전) · white_night_sanctuary(백야의 성역).

지운 곳:
- Bridge: `mana-spells.yml`(312→300) · `donation-spells.yml` · `spell-elements.yml` · `spell-sounds.yml` · `titles.yml`(290→278) · `title-awards.yml`(마법 칭호 237→225) · 호환용 `spell-runtime.yml`(cinder_detonation) · `LifeSpells.NO_HINT`.
- MagicDiscovery: `discoveries.yml`(9개 + 메테오 선행 조건 3개), `manual-conditions.yml`(6개), 그 마법만 쓰던 조건 코드(자동 보급 "같은 블록 쌓기", 덩굴 사다리 "덩굴 타기", 불꽃 걸음 "불타며 30초 버티기").
- MagicMythic: `tools/spells.py`(S 4개·SKIP·PASSIVE), `tools/spell_order.py`(DROPPED·DROPPED_INFO 삭제) → 생성물 `chacademi_spells.yml`·`server-config/spell-runtime.yml`·`spell-targeting.json`·`SPELLS.md` 다시 만듦. 패시브 `Passives.java` 불꽃 걸음·자동 보급 기능 삭제 + `config.yml passives` 2개, `GameplayVisual` 빛의 낙인 투사체 연결.
- 클라: `SpellCastSounds`(200→188) · `sounds.json` · `sounds/cast/*.ogg` 12개 삭제 · 비주얼 테스트 예시 마법(낙하 명령→윈드 프레스).
- 운영 overlay: `operating-config-overrides.json` 의 운영 전용 keep 6개(vine_ladder·light_mark 의 mana-spells enabled·카탈로그·바인딩) 삭제 → `regenerate` → `check` "차이 없음".
- 테스트 기대값: ManaTest·AdminCommandRules·TamingCatalog 300, TitleTest 278(+NPC 40=318), TitleAwards 225, SpellRules 97, SpellCastSounds 188, ManualConditions 107, 메테오 선행 조건 29(−falling_star 본인=28), EventIndex stateOnly, ExpansionSpells.
- 손 안 댐: `reference/`, 옛 인계·기록 문서(`docs/**`, `portable-vfx-runtime/docs/pending/**`), VFX 자산 목록(`mob-asset-contract-20261019.yml`, `readiness-1245-ids.txt`, `external-vfx-assets.json` — 팩 존재 기록일 뿐 마법 연결 아님).

**수업 바뀐 마법** (28수업 47마법 중 지금 없는 마법은 빛의 서고 하나):

| 수업 | 전 | 후 | 이유 |
|---|---|---|---|
| 이솔데 `lesson_light_2` [중급] 지키는 빛 (지능 20) | 보호 결계 · 빛의 서고 | 보호 결계 · **기억의 등불**(memory_lantern, 213) | 빛의 서고 삭제. 같은 빛 원소, 습득 조건 없음, 가중치 2(마나 20) — 기초(202~207)와 고급(224~) 사이. 쓰러진 자리를 비추는 생활 마법이라 궁극 아님 |

같이 맞춘 것: `lessons.yml` 수업 이름, `books.yml lesson_light_2` 본문·commands(`마법조건 주기 memory_lantern`), `manual-conditions.yml` memory_lantern 추가(조건 문구 "이솔데 교수의 빛 마법 중급 수업 듣기."), 도감 `213_memory_lantern.yml` discovery 문구 + `known-hashes.tsv` 새 해시. 나머지 신규 마법 조건은 안 건드림 (오너 "나중에 한 번에").

**서버 데이터 폴더에서 손으로 바꿀 파일** (jar 를 바꿔도 안 바뀜):

| 서버 파일 | 할 일 | 서버 |
|---|---|---|
| `MagicCodexBridge/mana-spells.yml`·`spell-runtime.yml`, `MythicMobs/Skills/chacademi_spells.yml`, `PortableVFX/spell-catalog.yml`·`spell-bindings.yml` | overlay 판으로 교체 (§3, 이번에 다시 만듦) | 학교·야생·아레나 |
| `MagicCodexBridge/titles.yml`·`title-awards.yml` | 저장소 판으로 교체 (마법 칭호 12개 빠짐) → `/서버관리 새로고침 칭호` | 학교·야생·아레나 |
| `MagicCodexBridge/donation-spells.yml` | 12개 항목 지우기 (자동 병합은 덧붙이기만 하고 안 지움) | 학교 |
| `MagicCodexBridge/spell-sounds.yml` | 저장소 판으로 교체 (또는 12개 항목 지우기) → `/서버관리 새로고침 마법소리` | 학교·야생·아레나 |
| `MagicCodexBridge/lessons.yml`·`books.yml` | §4 대로 (lesson_light_2 가 바뀐 판) | 학교 (책은 학교·야생) |
| `MagicDiscovery/discoveries.yml`·`manual-conditions.yml` | 저장소 판으로 교체 (§4 와 같은 파일, 이번 변경 포함) | 학교·야생·아레나 |
| `ChAcademiaMagicMythic/config.yml` | `passives.flame_footprints`·`automatic_supply` 지워도 되고 둬도 됨 (안 읽음) | — |

**클라 default-spells 에서 지워지는 파일**: 이미 2026-10-25 에 도감 파일은 빠졌고, `dropped-defaults.txt` 의 12개(008_aerial_haul … 162_white_night_sanctuary)를 클라가 지운다. 배포 그대로인 파일만 지우므로 `known-hashes.tsv` 의 그 12개 옛 해시 줄은 **일부러 남겨 둠** (지우면 클라가 옛 파일을 못 알아봄). 플레이어가 고친 파일은 그대로 남는다.

확인 필요:
- 운영에서 켜져 있던 덩굴 사다리·빛의 낙인을 이미 배운 플레이어: 권한 `magic.learned.vine_ladder`·`light_mark` 과 칭호(`suffix_magic_vine_ladder`·`suffix_magic_light_mark`)가 남아 있어도 서버가 모르는 마법·칭호라 안 보임. 정리하려면 LuckPerms·칭호 DB 에서 지우기 (안 해도 동작엔 문제 없음).
- 신규 마법 도감 습득 문구 중 지운 마법을 말하는 것 2개 (조건은 안 건드림, 나중에 한꺼번에 고칠 때): 047 sky_dive "낙하 명령으로 적 30번 내리꽂기", 205 prism_shot "빛의 낙인 100번 적중".
- 기억의 등불 아이콘이 아직 없어 MagicDiscovery 쪽 icon 을 `magiccodex:textures/spells/nova/memory_lantern.png` 로 적음 (발견 알림 그림, 신규 75종 아이콘 만들 때 같은 이름으로).

## 13. ChacaNPC 장소 관리 명령 (2026-10-26, 메모 #84)
- `/NPC관리 장소관리 목록` · `장소관리 삭제 <이름>` · `장소관리 전부삭제 확인` — 테스트 때 만든 장소 정리용 (PlaceRepository.removePlace). ChacaNPC jar 다시 빌드·학교에 배치.
- NPC 활동 장소 34곳 계획: [docs/plans/npc-places.md](../plans/npc-places.md). 길목(경로)은 안 찍음 — Citizens 가 길을 찾고, 못 찾으면 순간이동.

## 14. 일일 제한 (2026-10-26, 메모 #90)
- 마음 읽기(383) 하루 1번 기록을 서버 메모리 → **공유 DB** `codex_affinity_daily` (source `heart_read`, npc = 캐릭터 id) 로. 접속 때 오늘 기록을 읽어 캐시, 서버 이동·재시작에도 유지.
- 관리 명령 `/유저관리 <닉> 일일제한 보기` · `일일제한 초기화 <선물|AI대화|마음읽기|전체>` (DailyLimits). 오늘 횟수만 지우고 호감도 점수는 그대로. 다른 하루 제한 기능은 `DailyLimits.register(...)` 로 종류를 붙이면 같은 명령에 나온다 (인기도는 기능 만들 때 붙일 것).
- 배포 확인: 학교·야생 `MagicCodexBridge/database.properties` 가 같은 MariaDB, 하루 기준 timezone Asia/Seoul.

## 15. 전투 중 이동 막기 (2026-10-26, 메모 #91, 오너 2안)
- 새 공용 클래스 `CombatTag` (MagicCodexBridge `combatTag()` — 다른 기능도 `inCombat`·`remainingMillis`·`lockedMillis`·`refuse` 로 씀) + 순수 규칙 `CombatLockRules`(+JUnit). 설정 `combat-lock.yml` (처음 켤 때 자동 생성, `/서버관리 새로고침 전투이동`): enabled · seconds(기본 10) · servers(기본 school·wild·wild2, 비우면 전체) · blocked-commands · blocked-spells · notify-cooldown-ms.
- 전투 = 살아있는 상대(플레이어·몹; NPC·펫 태그·길들인 동물·허수아비·주민·갑옷 거치대 제외)를 때렸거나 맞은 뒤 N초. `EntityDamageByEntityEvent`(MONITOR, 취소된 피해 제외) 한 곳에서 시각만 적음 — 투사체는 쏜 사람, 길들인 동물은 주인, 마법 피해는 MagicProtection 이 `target.damage(양, 시전자)` 로 넣어 같이 잡힘, 동행 펫 스킬은 주인이 시전자. 죽으면 풀림. 매 틱 일 없음, 이동 시도 때만 비교.
- 막는 것: 귀환권(아이템 쓰기 전) · 지도 이동(공개 지점·개인 지점·야생 스폰 — `travel()` 맨 앞 + `go()` 에서 DB 확인 뒤 한 번 더, 개인 지점 마나 쓰기 전) · 명령(PlayerCommandPreprocessEvent LOW: home·phome·publichome·spawn·warp·tpa·tpahere·tpaccept·tpyes·back·rtp·randomtp·집·홈·스폰·워프·뒤로·귀환, `huskhomes:` 앞부분 무시) · 마법 145 동행 귀환(`MagicCastAttemptEvent` 취소 → 마나·쿨 안 씀).
- 허용: 공간 도약(개인 지점 새기기)·짧은 전투 이동 마법, 빗자루, 리스폰·사망 귀환, 큐디치·결투 경기 이동, OP·`magiccodex.combatlock.bypass`, 관리자 명령(/유저관리 찾아가기 등 — 목록에 없음).
- 알림: 차카데미 알림창(CmdKit.notify, key `combat-lock`, 1.5초 도배 방지) "전투 중이라 이동할 수 없어요 · N초 뒤 가능". 지도 창에도 같은 문구로 실패 표시.
- 바꾼 파일: `CombatTag.java`·`CombatLockRules.java`(새), `CombatLockRulesTest.java`(새), `combat-lock.yml`(새), `MagicCodexBridge.java`(등록 3줄), `TravelBridge.java`, `ReturnTicketBridge.java`, `plugin.yml`(권한 1개), `travel.yml`·`return-ticket.yml`(주석).
- 그대로 둔 것: travel.yml `rules.block-in-combat`(아티팩트 5초 판정, 기본 꺼짐) — 켜면 이것도 같이 막음. 동행 펫(PetCompanions)의 자체 전투 판정은 상대 엔티티·8초 설정(combat-seconds)이 따로 있어 바꾸면 동작이 달라지므로 안 바꿈.
- 확인 필요: HuskHomes 실제 명령·한글 별칭 이름(서버 HuskHomes config·commands.yml 별칭과 맞추기). 상대가 /tpa 를 나중에 수락해 내가 끌려가는 경우·HuskHomes 창 클릭 이동은 명령을 거치지 않아 못 막음. 옛 MCPets 소환 펫이 때린 건 주인 전투로 안 셈.

## 16. 야생 청크 점유 — 땅문서 (2026-10-26, 메모 #88)
- **어디에 만들었나**: 새 플러그인이 아니라 MagicCodexBridge 안의 기능 하나(`ChunkClaimBridge`). 이유: 땅문서(ItemsAdder)·알림창(CmdKit.notify)·한글 닉네임·관리 명령 허브(ChacaApi)·DB 설정(database.properties)·클라 모드 채널이 전부 여기 있고, 야생 서버에도 이미 이 플러그인이 돈다. 켜는 곳은 `chunk-claims.yml servers`(travel.yml `server-id`) — 기본 `wild`·`wild2`. 학교·아레나 등 다른 서버는 이벤트·작업·DB 를 하나도 열지 않는다 (명령만 등록, "꺼져 있어요").
- **저장 방식 — 청크 NBT(PDC) 대신 DB 표 + 서버 메모리 표**: 청크 PDC 는 그 청크가 로드돼야 읽혀서 "내 땅 목록·1인 최대 수·관리자 조회"가 안 되고, 사람 기준 허용 명단을 넣을 곳이 없고, 월드 백업·재생성과 운명을 같이 한다. 그래서
  - 표 `codex_chunk_claim (world, cx, cz) PK · owner · claimed_at · server` (+INDEX owner), `codex_chunk_trust (owner, trusted) PK · added_at`.
  - DB 는 칭호 장부(TitleStore)와 같은 방식: `database.properties`(일반 도메인, GenericDatabaseFile)가 MariaDB 면 공유 표, 아니면 그 서버의 `chunk-claims.db`(SQLite). 연결 하나 + 전용 작업 스레드 하나, 서버 스레드에서는 DB 를 부르지 않음.
  - 서버 시작 때 이 서버에 있는 대상 세계의 점유 전부 + 허용 명단 전부를 메모리 표(`ChunkClaimRules.Index`, 세계 → 청크 키 → 주인)로 읽고, **블록 이벤트 판정은 전부 메모리에서** (DB 0번). 허용 명단은 1분마다 한 번 다시 읽음 (다른 서버에서 바꾼 것 반영).
  - 동시 점유 방지: PK + `INSERT IGNORE`(SQLite `OR IGNORE`)로 넣고 넣은 줄 수로 성공 판정 → 같은 청크는 먼저 넣은 한 사람만. 1인 최대 수는 같은 거래 안에서 셈 (모든 서버 합계). 판매·해제는 주인까지 맞아야 지움 (지운 줄 수로 판정 → 땅문서 두 장 방지).
  - 땅문서는 먼저 1장 가져가고 저장(`saveData`) → DB 가 거절하면 돌려줌 (접속 중이면 인벤토리·꽉 차면 발밑, 나갔으면 우편). 판매는 DB 에서 지운 뒤에만 1장 줌.
- **허용 명단 범위**: "내 땅" 전체 — 주인 기준 명단 하나가 그 주인의 모든 청크(모든 서버)에 적용.
- **땅문서를 든 동안**: 서버가 0.5초마다 접속자를 훑어 (손 아이템 종류가 땅문서 재료일 때만 ItemsAdder id 확인) 주변 청크 상태를 계산하고 **지난번 보낸 것과 다를 때만** 보냄 (들었을 때·놓았을 때·청크를 옮길 때·주변 주인이 바뀔 때). 클라: 서 있는 청크 + 바로 옆(`view-radius: 1`, 3×3) 경계에 반투명 홀로그램 벽 (큐디치 경기장 경계 그림 `textures/kyudichi/boundary.png` 재사용; 내 땅 초록·허용됨 파랑·남의 땅 금색·제한 분홍·소유 가능 하늘색), 청크 가운데 **내 눈높이**에 글씨 (주인 한글 닉 / "소유 가능" / "제한", 웨이포인트 글씨와 같은 글자 규칙). 늘어나는 건 클라 그리기뿐.
- **우클릭** = 땅문서 1장 쓰고 서 있는 청크가 내 땅. 주인 있음·제한·최대 수면 아이템을 쓰지 않고 알림창으로 거절.
- **제한**: `worlds` 밖(기본 `world` 만) · `excluded` 원(세계·중심·반경)과 겹치는 청크 · WorldGuard 지역과 겹치는 청크(`__global__` 제외, `worldguard.ignore-regions` 로 뺄 수 있음). WorldGuard 는 리플렉션으로 부르고 청크마다 결과를 기억 (새로고침 때·10분마다 비움).
- **보호** (점유 청크에서 주인·허용 명단 말고는): 블록 부수기·놓기 (마법이 보내는 가짜 부수기·놓기 이벤트도 같이 막힘) · 보관함·문·버튼·레버 등 우클릭 · 부싯돌·뼛가루·괭이/삽/도끼·스폰알·갑옷 거치대/액자/보트 놓기 · 양동이 붓기/뜨기 · 작물 밟기·압력판 · 동물·주민·갑옷 거치대·액자 때리기/우클릭. 폭발(크리퍼·TNT·침대)·불 번짐·화염구·바깥 피스톤·바깥에서 흘러 들어오는 물/용암도 막음. 막지 않음: PvP, 몬스터, NPC, 펫·길들인 동물, 먹기·활·방패. 거절 알림은 알림창 "OO님의 땅이에요" (1.5초 도배 방지). 우회 권한 `magiccodex.chunkclaim.bypass` (OP 기본).
- **내 땅 관리** (클라): 내 청크 안에 서 있으면 오른쪽 위에 작은 칸 "내 땅 · 청크 x, z · H 관리" (파티 UI 알림 카드 그림, 큐디치 매칭 카드가 있으면 그 아래, HUD 편집으로 옮길 수 있음). **H 키**(키 설정 UI 단축키 "내 땅 관리", 내 땅 안에서만 가로챔 — 밖에서는 H 를 막지 않음) 또는 Ctrl 커서·채팅창에서 칸을 누르면 그 자리에서 아래로 펼쳐지는 관리 창. 파티 창의 "파티 초대" 패널·줄·얼굴 틀·파랑 "허용"/분홍 "빼기" 버튼·파티 나가기 버튼·확인 창을 그대로 재사용 (새 장식 없음): 지금 이 땅에 들어온 사람(허용/빼기) · 허용 명단(빼기) · "이 땅 판매 · 땅문서 1장 돌려받기"(확인 한 번). H·ESC·창 밖 클릭으로 접힘, 내 땅 밖으로 나가면 자동으로 닫힘. 허용 넣기는 접속 중인 사람만, 명단 48명까지.
  - 인벤토리 창 위에서는 HUD 가 그려지지 않아 칸이 안 보임 → 클릭은 Ctrl 커서·채팅창에서 (기존 HUD 클릭과 같은 규칙).
  - 기존 `ui-keys.properties` 가 있는 사람: H 를 이미 다른 UI 키나 마법이 쓰고 있으면 "내 땅 관리"는 미지정으로 시작 (키 설정에서 지정). UI 단축키가 11개가 되어 키 설정 왼쪽 목록 간격을 50→48px 로 조금 좁힘.
- **통신**: 새 채널 `chacademi:chunk_claim`(서버→클라: VIEW 주변 칸 상태 · LAND 내 땅 안 사람·허용 명단 · RESULT 결과 문구) / `chacademi:chunk_claim_action`(클라→서버: 허용 넣기·빼기, 판매). `ChunkClaimProtocol`. 서버가 모든 동작을 다시 검사 (내 땅인지, 그 청크에 서 있는지, 상대가 접속 중인지, 0.25초 간격).
- **관리 명령**: `/컨텐츠관리 청크 보기` (서 있는 청크: 상태·주인·점유 시각·허용 명단) · `/컨텐츠관리 청크 해제 확인` (서 있는 청크 소유 해제, 땅문서는 안 돌려줌) · `/유저관리 <닉> 청크 목록` (모든 서버·세계, DB 에서) · `/서버관리 새로고침 청크` · `/서버관리 상태` "청크 점유" 줄 · `/유저관리 <닉> 보기` 요약 "청크".
- **설정** `chunk-claims.yml` (처음 켤 때 자동 생성): enabled · servers(기본 wild, wild2) · worlds(기본 world) · item(`item:chunk_deed`) · item-name · max-claims-per-player(0 = 제한 없음) · view-radius(0~2) · worldguard.restrict-overlap · worldguard.ignore-regions · excluded · notify-cooldown-ms · labels.free/restricted · messages. jar 리소스라 operating-config overlay 는 만들지 않음 (overlay 검사 영향 없음).
- **바꾼 파일**: 새 `ChunkClaimBridge.java`·`ChunkClaimRules.java`·`ChunkClaimStore.java`·`ChunkClaimWorldGuard.java`·`chunk-claims.yml`·`ChunkClaimTest.java`(규칙·메모리 표·SQLite 저장소·패킷), 새 `magic-codex-protocol/ChunkClaimProtocol.java`, 새 클라 `ChunkClaimClient.java`·`ChunkLandScreen.java`. 공유 파일은 등록 몇 줄: `MagicCodexBridge.java`(필드·생성·종료), `plugin.yml`(권한 1개), `GenericDatabaseFile.java`(로그 목록 글자), 클라 `MagicCodexClient.java`(1줄), `UiKeys.java`(LAND 키·처리 2줄·기존 파일의 겹치는 기본 키 비우기), `KeySettingsLayout.java`(목록 간격), `HudCursorScreen.java`·`HudChatClicks.java`(클릭 1개씩).
- **배치·같이 배포**: MagicCodexBridge jar → 야생(wild, wild2) 그리고 학교·아레나에도 같은 jar (거기선 꺼짐). 클라 모드(magic-codex-fabric) 같이 배포 — 모드가 없으면 점유·보호·알림은 되지만 경계선·글씨·관리 창은 없음. 야생 서버에 ItemsAdder `item:chunk_deed` 필요. 야생 `MagicCodexBridge/database.properties` 를 학교와 같은 MariaDB 로 (권장 — SQLite 로 시작했다가 나중에 옮기면 tools/db-migrate 가 이 표를 옮기지 않음). 야생 travel.yml `server-id: wild` 확인 (아니면 "꺼짐" 로그).
- **게임 확인 방법** (야생 서버, 모드 깐 클라 2개 A·B):
  1. 시작 로그 "청크 점유: 켜짐 · 서버 wild · 세계 [world] · 공유 DB · 점유 0칸".
  2. A 가 땅문서를 손에 들기 → 주변 3×3 청크 경계 홀로그램 + 가운데 "소유 가능". 스폰(WorldGuard 지역) 근처는 "제한". 놓으면 사라짐.
  3. 우클릭 → 땅문서 1개 줄고 "이 땅은 이제 내 땅이에요", 글씨가 A 닉네임·초록. 다시 우클릭 → "이미 내 땅이에요"(아이템 그대로). 스폰 근처 우클릭 → "이 땅은 소유할 수 없어요"(아이템 그대로).
  4. B 가 A 의 땅에서 블록 부수기·놓기·상자·문·양동이·밭 밟기·소 때리기 → 막히고 "A님의 땅이에요". 바깥에서 TNT 를 터뜨려도 A 땅 블록은 안 깨짐. B 가 A 를 때리는 PvP 는 그대로.
  5. A 가 내 땅 안에서 오른쪽 위 "내 땅" 칸 확인 → H → 펼친 창에 B "손님" → "허용" → B 가 블록을 부술 수 있음, 허용 명단에 B. "빼기" → 다시 막힘. Ctrl 누른 채 칸 클릭·채팅창 열고 클릭으로도 열림.
  6. "이 땅 판매" → 확인 → 땅문서 1장 돌려받음(꽉 차면 발밑), 칸 사라짐, 땅문서 들면 "소유 가능".
  7. 관리: `/컨텐츠관리 청크 보기`, `/유저관리 A 청크 목록`, `/컨텐츠관리 청크 해제 확인`. OP 는 남의 땅에서도 활동 가능.
- **추정한 것 (확인 필요)**: 야생 세계 이름 `world`(travel.yml 탭 기준), 야생 server-id `wild`/`wild2`, 야생 커스텀 몹(CA_Wild_*)은 몬스터 계열이라 보호 대상 아님 (동물 계열 몹이 있으면 남의 땅에서 못 때림), 야생 전체를 덮는 WorldGuard 지역이 있으면 `ignore-regions` 에 넣어야 전부 "제한"이 되지 않음.
