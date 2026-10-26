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

## 16. 길잡이 별 — 움직이는 NPC 따라가기 (2026-10-26, 메모 #93)
- 길잡이 별(132/201 guiding_star → ChacademyStory `QuestEngine.guide`)의 목표가 NPC 면 그 NPC 의 **지금 위치**를 따라간다. 일과표로 돌아다니는 ChacaNPC(Citizens) 등.
- 서버(ChacademyStory): 목표가 NPC 인 사람만 `follows` 에 넣고 **2초마다**(`FOLLOW_TICKS` 40틱, 따라가는 사람 없으면 바로 끝) NPC 위치 확인 → **1.5블록 넘게 움직였거나** 상태(이동 중 / 언로드 / 다른 세계)가 바뀐 때만 새 채널 `chacademy:quest_guide_target` 으로 보냄. 이동 중 = Citizens `getNavigator().isNavigating()` 또는 2초 사이 0.3블록 넘게 움직임. 언로드면 저장 위치(마지막 위치), 다른 세계면 마지막 위치 + 상태를 보내고 따라가기 끝(클라가 화살표를 끔). 켤 때 지금 위치를 바로 한 번 보냄(단계 패킷의 NPC 좌표가 낡았을 수 있어서).
- 클라(스토리 모드): NPC 가 화면에 불러와져 있으면 엔티티 그대로, 멀면 서버 위치로 — 둘 다 부드럽게 옮겨감(순간이동처럼 48블록 넘게 바뀌면 바로). 걷는 중이면 화살표 거리 글씨 아래 **"이동 중"** 을 같은 글씨로 작게. 새 장식 없음.
- 고정 목표(포탈·목표 블록·스토리 고정 NPC)는 동작 그대로 — 스토리 NPC 는 위치 확인만 2초마다 하고, 안 움직이면 아무것도 안 보냄. 옛 클라(새 채널 없음)는 예전대로.
- **의뢰 NPC 목표 연결**: 스토리 단계에 가리킬 목표가 없으면(단계 없음·다 끝냄·목표 없는 단계·공개 전인 장) 진행 중인 MagicCodex 의뢰의 아직 못 채운 `NPC` 목표(Citizens 번호, 이 서버 것만) 중 같은 세계의 가장 가까운 NPC 를 가리키고 따라간다. 의뢰를 끝내거나 포기하면 화살표를 끔. MagicCodexBridge `NpcSocialFacade.questNpcTargets(Player)`(추가 메서드, API_VERSION 그대로) ← `QuestBridge.npcTargets`. 스토리 목표가 있으면 스토리가 먼저.
- 바꾼 파일: ChacademyStory `QuestEngine.java`·`QuestCodec.java`·`MagicCodexLink.java`, 스토리 모드 `QuestPackets.java`·`QuestClient.java`·`QuestWorld.java`, MagicCodexBridge `QuestBridge.java`·`NpcSocialFacade.java`, 테스트 `QuestGuideTest`·`QuestWireCompatibilityTest`.
- 배포: ChacademyStory 플러그인 + 스토리 클라 모드 + MagicCodexBridge 같이 (하나만 올려도 깨지진 않음 — 의뢰 NPC 는 셋 다 새 판일 때만).
- 못 한 것: 호감도 30·60·90 퀘스트는 아직 내용이 없어 연결할 대상 없음 (나중에 MagicCodex 의뢰 + `NPC` 목표로 만들면 자동으로 가리킴). 의뢰 목표 중 처치·제출·바이옴은 NPC 가 아니라 대상 아님. 다른 서버의 NPC 목표는 안 가리킴(예전처럼).
## 17. 한글 닉네임 표시 일관성 (2026-10-26, 메모 #94)
훑은 범위: 하우징·방명록(이미 한글, HousingNames)·랭킹·친구·우편·퀘스트·큐디치/결투 결과·관전·파티·귓속말/통합채팅·알림·정보창·거래·칭호 알림 — 서버(MagicCodexBridge·Kyudichi·ChacaNPC·ChacaPortrait·스토리 등)·Velocity(party·housing-presence)·클라. 대부분은 이미 `plugin.names().name(...)` / `ChacaLink.name` / `hooks().nick` / 클라 `NicknameClient.display` 를 씀. 계정 이름이 새던 곳만 고침 (명령 자동완성·입력·서버 로그는 그대로):
- `DisplayNames.name(offline, 저장된이름)` (MagicCodexBridge, 모든 표시 이름의 공통 길): 닉네임 자리 표시자가 계정 이름을 돌려주면 호출한 쪽이 저장해 둔 이름(친구 목록의 닉 등)을 먼저 씀 → SocialBridge 83(친구·신청 목록)·309(관리 친구 보기) 해결. 접속하지 않은(다른 서버) 사람은 캐시에 없으면 공유 DB 에서 한 번 읽어 둠(`requestStored`, 표시 전용·5초 제한).
- `CmdKit.name` 145: 명령 허브가 준비되기 전에도 계정 이름 대신 `nicknameOf`.
- 모든 플러그인의 `ChacaLink.name` 115 (12개 같은 파일: kyudichi·housing·gimmick·wildlife·tornado·mythic·discovery·story·motion·portrait·npc·vfx): 허브 호출 실패 때 브리지 `nicknameOf` → 그래도 없으면 계정 이름 (`nicknameFallback`).
- 파티(Velocity) `PartyPlugin.name`: 오프라인 파티원이 `/파티 목록` 등에 UUID 로 보이던 것, 닉 응답 전 계정 이름 → 기억해 둔 닉네임(PartyHud) 먼저, 없으면 "파티원".
- ChacaNPC 선물 기억 문장(`GiftService`) 계정 이름 → `link().playerName`. 잡담 필터(`SocialService`)가 접속자 한글 닉이 들어간 줄도 거름.
- ChacaPortrait 일러스트 완성·실패 보고("OO 님 일러스트 완성") 계정 이름 → 닉네임 (`PortraitService.jobFor`).
- 스토리 관리 알림 5곳(이미 재생 중·대기열 가득 등) `p.getName()` → `nick(p)`.
- 클라 `NicknameClient.display`: 접속·서버 이동 직후 닉 응답(약 1.5초)이 오기 전 계정 이름이 잠깐 보이던 것 → 같은 계정이면 마지막으로 받은 닉네임, 처음 접속이면 이름표 자료(`NameplateClient.nickname`)의 내 등록 닉네임. 정보창·큐디치 대기실·일러스트·스토리 대화 `{player}` 등이 다 이 함수를 씀.
- 그대로: kyudichi `Hooks.nick` 142 (브리지가 없을 때만 계정 이름 — 정상 폴백, 브리지 쪽 개선이 그대로 적용), `QuestBridge` 206 (서버 로그), 각종 `{player}` 명령 치환(콘솔 명령 인자라 계정 이름이어야 함).
- 못 고친 것: 바닐라 입장·퇴장·사망 메시지와 탭 목록은 이 저장소 코드가 아니라 서버 쪽 플러그인 설정(채팅/TAB 등) 몫 — 운영 설정에서 `%magiccodex_nickname%` 를 쓰는지 확인 필요.
## 18. 운영 기록 (감사 로그, 2026-10-26, 메모 #92)
오너: "누가 어떤 마법을 얻었다·무엇을 팔았다·어떤 퀘스트를 깼다… 가끔 복구할 때 보게. 서버에 무리 안 가게, 사소한 건 빼고."

**렉 방지 구조**: 게임 스레드는 `AuditLog.record/log` 에서 메모리 대기열(AuditQueue)에 한 줄 넣기만 한다 (DB·파일 IO 없음). 기록 스레드 `MagicCodex-Audit` 하나가 `batch-seconds`(기본 5초)마다 500줄씩 한 트랜잭션으로 넣는다. 대기열 `queue-limit`(기본 2만 줄) 넘으면 사소한 종류(접속·낚시)부터 버리고 콘솔 경고 1번. DB 가 안 되면 `plugins/MagicCodexBridge/audit/yyyy-MM-dd.log` 에 탭 구분 한 줄씩(시각·서버·uuid·닉·종류·설명·상세JSON), 1분 뒤 DB 재시도. 보관 `retention-days`(기본 90일) — 하루 한 번 기록 스레드가 5000줄씩 나눠 지움(파일도). 서버 끌 때 남은 줄을 최대 5초 동안 마저 씀.

**표 `codex_audit_log`** (대시보드가 읽음). 저장소 = `database.properties`(일반 도메인과 같은 파일): MariaDB 면 모든 서버 공유, 없으면 이 서버 `audit.db`(SQLite). 칭호 장부(codex_title_awards)와 같은 방식(`AuditStore`).

| 칸 | 형 | 내용 |
|---|---|---|
| id | BIGINT 자동증가 PK | 들어온 순서 |
| at_ms | BIGINT | 시각 (유닉스 ms) |
| server | VARCHAR(48) | 기록한 서버 (`audit.yml server-name`, 비우면 `quests.yml server-id`) |
| player | VARCHAR(36) | 플레이어 UUID (시스템/콘솔 기록은 "") — 관리 명령은 **대상 유저** UUID |
| player_name | VARCHAR(64) | 그때의 한글 닉 (없으면 계정명) |
| kind | VARCHAR(32) | 종류 (아래 한글 이름 그대로) |
| summary | VARCHAR(255) | 한 줄 한국어 설명 |
| detail | TEXT | 상세 JSON 객체 `{"키":"값"}` — 값은 전부 문자열, 4000자 넘으면 `"_잘림":"예"` |

인덱스: `idx_audit_player(player,at_ms)` · `idx_audit_kind(kind,at_ms)` · `idx_audit_time(at_ms)`. 웹 검색은 `WHERE player=? AND at_ms>=? ORDER BY at_ms DESC` / `WHERE kind=? AND at_ms>=?` 로 인덱스를 타게, 글자 검색은 기간을 꼭 걸 것.

**종류와 연결한 곳** (성공한 것만):
| 종류 | 남기는 것 | 연결 |
|---|---|---|
| 마법 | 발견·다시 습득(수업·책·신호 경로 포함), 관리자 배우기/잊기·사전 해금, 발견 조건 지급/회수/초기화, 마법 기증 | MagicDiscovery `acquire`·`adminSetLearned`·`adminCondition` (ChacaLink.audit), SchoolBridge 기증 |
| 상점 | 일반 상점(피피 잡화점·튜토리얼 상점 포함) 구매(우편 저장까지)·판매(대금 입금까지), 물고기 판매(종류·개체·합계) | ShopBridge, FishShopBridge |
| 마일리지 | 마일리지샵 구매, 관리자 마일리지 설정·추가·빼기(전→후) | MileageBridge |
| 우편 | 첨부 수령(영수증 확인 뒤, 아이템 이름·개수) | MailboxBridge |
| 퀘스트 | 의뢰 수락(게시판·대화·호감도 퀘스트), 완료·보상(돈·아이템·명령·기숙사 점수) | QuestBridge |
| 스토리 | 스토리 단계 완료(단계 → 다음) | ChacademyStory QuestEngine.advance (ChacaLink.audit) |
| 호감도 | 선물(아이템·증감·결과 점수), AI 대화 보상, 퀘스트·기타 출처 증가, 관리자 설정/추가/빼기·하트 단계 | NpcSocialService add·chatReward·commitGift·adminChange·adminHeart |
| 아티팩트 | 뽑기 결과(등급·새것/중복 조각·재료 = 열쇠 또는 확정권, 천장은 #98 로 폐기 §21) | ArtifactBridge |
| 펫 | 교화 성공(몹·펫·확률·위치), 관리자 펫 주기·회수 | TamingBridge, PetBridge |
| 강화 / 감정 | 장비 강화 성공·실패(전 능력치·상승·스타캐치·비용), 코어 감정(노드 수·파괴·비용) | EnhancementBridge, AppraisalBridge |
| 연금술 | 제작(레시피·결과·경험치·레시피 발견) — **레시피에 등급이 없어 전부** 남김 (자주 하지 않는 행동) | AlchemyBridge |
| 낚시 | 고급 이상 물고기(등급 ≥1, 크기·첫 발견·최고 기록), 고급 이상 보물상자(나온 아이템) | FishingBridge |
| 돈 | 관리자 돈 설정·추가·빼기(전→후), 행운의 동전 지급(money-threshold 이상) | WalletBridge, ArtifactLife |
| 관리자 | 관리 대단원(/유저관리·/컨텐츠관리·/서버관리·/상점관리 … 전부)에서 **바꾸는** 명령 실행: 누가·명령 전체·대상. 보기·목록·상태·기록·검색·도움말 같은 경로는 뺌 (AuditText.readOnlyCommand) | CommandHub.wrap (한 곳) |
| 기숙사 | 분류의 거울 배정(성향·인원 맞춤 여부), 관리자 지정 | SchoolBridge |
| 칭호 | 관리자 지급·회수, 획득 경로(발견·호감도·낚시·사전 설정) 지급 | TitleBridge |
| 접속 | 접속·퇴장 한 줄(서버 이름·위치), 지도/귀환권 서버 이동 요청(출발→도착) | AuditLog 리스너, TravelBridge |
| 사망 | 원인·처치자·위치·아이템 유지/떨군 묶음·경험치 | AuditLog 리스너 |

빼는 것: 일반 이동, 채팅, 블록, 마법 시전 하나하나, 일반 몹 처치, 줍기, 일반 등급 물고기 (→ 수는 §16 통계로만).

**명령**: `/유저관리 <닉> 기록 [종류|전체] [일수] [쪽]` (한 쪽 15줄, 최근 것부터) · `/서버관리 기록 검색 <글자>` (최근 `search-days`=7일, 설명·상세·닉 포함, 30줄) · `/서버관리 기록 상태` · `/서버관리 새로고침 운영기록`. 읽기도 기록 스레드에서 하고 결과만 채팅으로.

**설정 `audit.yml`** (jar 에 기본 판, 처음 켤 때 생김): `enabled`, `server-name`, `retention-days: 90`, `batch-seconds: 5`, `queue-limit: 20000`, `search-days: 7`, `money-threshold: 10000`, `minor-kinds: [접속, 낚시]`, `kinds:` 종류별 true/false, `stats:` (§16). queue-limit·batch-seconds 는 재시작 때 적용.

**다른 플러그인 연결**: `ChacaApi.audit(UUID, 종류, 설명, Map<String,String>)` (아무 스레드). MagicDiscovery·ChacademyStory 는 ChacaLink.audit 로 리플렉션 호출 (브리지 없으면 조용히 건너뜀).

**배치**: MagicCodexBridge·MagicDiscovery·ChacademyStory·Kyudichi jar 다시 빌드 → 학교·야생·아레나·튜토리얼 전부. 서버마다 `audit.yml server-name` 을 `school`/`wild1`/`wild2`/`arena1`/`tutorial_1` … 로 (또는 quests.yml server-id 가 이미 다르면 비워 둠). `database.properties` 가 같은 MariaDB 여야 한 표로 모임 (아니면 서버별 audit.db).

연결 못 한 것: 유저 간 거래·유저 우편 보내기는 기능이 없어 해당 없음 (우편은 시스템·관리자 발송뿐 — 관리자 발송은 '관리자' 종류로 남음). 퀘스트 보상 명령어·스토리 시작 명령 안에서 다른 플러그인이 주는 것(아이템 등)은 그 명령 문자열만 남음. ChacaNPC 쪽 독자 동작은 호감도가 MagicCodex 로 오므로 그쪽에서 남음. MCPets 자체 GUI 로 펫 꺼내기/넣기는 남기지 않음.

## 19. 서버 통계 수집 (2026-10-26, 메모 #96)
오너: "보여 줄 필요 없음, 기록만 쌓고 서버 끝날 때 정리". 운영 기록과 같은 저장소·같은 기록 스레드.

**표 `codex_player_stats`**: `player VARCHAR(36)`, `stat_key VARCHAR(96)`, `count BIGINT`(누적 합), `updated_at BIGINT`(ms), PK `(player, stat_key)`. `AuditStats.add(uuid, 키, n)` 은 메모리 맵에 더하기만 (아무 스레드). `stats.flush-minutes`(기본 5분)마다·그 유저가 나갈 때·서버 끌 때 기록 스레드가 upsert(`count = count + 증가분`, MariaDB `ON DUPLICATE KEY UPDATE`, SQLite `ON CONFLICT DO UPDATE`). DB 실패면 메모리에 되돌려 두고 다음에 다시 (메모리 상한 20만 키, 넘으면 새 키 버리고 경고). 다른 플러그인은 `ChacaApi.stat(UUID, 키, long)`.

| 키 | 뜻 (판단한 정의) | 센 곳 |
|---|---|---|
| `접속횟수` · `접속초` | 서버 접속 수 · 접속 시간(초). 접속 중이면 5분마다 지난 만큼 더함 (분 = /60) | AuditLog 리스너 |
| `서버이동` | 지도·귀환권으로 다른 서버 이동 요청 | TravelBridge |
| `사망` · `사망:몹` · `사망:플레이어` · `사망:기타` | 마지막 피해가 플레이어(처치자 있음) / 살아있는 몹·몹 투사체 / 그 밖(낙하·용암·마법 등) | AuditLog |
| `플레이어처치` | 다른 플레이어를 처치 | AuditLog |
| `몹처치` · `처치:<미몹ID>` | 플레이어가 막타 친 몹 전체 · 그중 MythicMobs 커스텀 몹 ID별 | AuditLog (EntityDeathEvent) |
| `시전` · `시전:<마법ID>` | 마나·쿨타임까지 통과한 성공 시전 | MagicCodexBridge.artifactCast |
| `마법발견` | 처음 발견(새 기록이 생긴 것만, 다시 습득 제외) | MagicDiscovery |
| `발견시도실패:<마법ID>` | **발견 조건은 다 채웠는데 확률(chance)에서 떨어진 판정** 한 번. 조건 미달 자체는 매 행동마다라 세지 않음 | MagicDiscovery.signal |
| `낚시` · `낚시:등급N` · `보물상자` | 수조로 간 물고기 (0 일반 1 고급 2 유일 3 전설) · 보물상자 | FishingBridge |
| `물고기판매` | 판 마리 수 | FishShopBridge |
| `상점구매` · `상점판매` | 끝까지 성공한 거래 건수 | ShopBridge |
| `퀘스트완료` · `스토리단계` | 의뢰 보상까지 끝남 · 스토리 단계 완료 | QuestBridge, ChacademyStory |
| `선물` | 확정된 NPC 선물 | NpcSocialService |
| `교화` · `연금술제작` | 교화 성공 · 연금술 제작 | TamingBridge, AlchemyBridge |
| `결투참가/승리/무승부/나감` · `큐디치참가/승리/무승부/나감` | 경기장에 실제 도착한 선수만, 무효 경기 제외 | Kyudichi Results.finish |

끝날 때 쓸 SQL 예시 (MariaDB):
```sql
-- 항목별 서버 전체 합계
SELECT stat_key, SUM(count) AS total, COUNT(*) AS players FROM codex_player_stats GROUP BY stat_key ORDER BY total DESC;
-- 접속 시간 순위 (시간 단위) + 한글 닉 (운영 기록의 가장 최근 닉)
SELECT s.player, (SELECT player_name FROM codex_audit_log a WHERE a.player=s.player ORDER BY at_ms DESC LIMIT 1) AS nick,
       ROUND(s.count/3600,1) AS hours FROM codex_player_stats s WHERE s.stat_key='접속초' ORDER BY s.count DESC LIMIT 30;
-- 많이 쓴 마법 TOP 20
SELECT SUBSTRING(stat_key,4) AS spell, SUM(count) AS casts FROM codex_player_stats WHERE stat_key LIKE '시전:%' GROUP BY stat_key ORDER BY casts DESC LIMIT 20;
-- 사람별 순위 (예: 큐디치 승리)
SELECT player, count FROM codex_player_stats WHERE stat_key='큐디치승리' ORDER BY count DESC LIMIT 20;
-- 가장 많이 잡힌 커스텀 몹
SELECT SUBSTRING(stat_key,4) AS mob, SUM(count) AS kills FROM codex_player_stats WHERE stat_key LIKE '처치:%' GROUP BY stat_key ORDER BY kills DESC;
-- 발견 확률에 가장 많이 떨어진 마법
SELECT SUBSTRING(stat_key,8) AS spell, SUM(count) AS fails FROM codex_player_stats WHERE stat_key LIKE '발견시도실패:%' GROUP BY stat_key ORDER BY fails DESC LIMIT 20;
```
설정: `audit.yml stats.enabled`(기본 true), `stats.flush-minutes`(1~60, 재시작 때 적용). `/서버관리 기록 상태` 에 대기 칸 수가 같이 보임.
## 20. 한글 편의 명령 (2026-10-26, 메모 #95)
Essentials 비슷한 한글 명령. 새 파일 `ConvenienceCommands.java`(등록·이벤트·서버 간 전달) + 순수 규칙 `ConvenienceRules.java`(+`ConvenienceRulesTest`), 설정 `convenience-commands.yml`(처음 켤 때 자동 생성, `/서버관리 새로고침 편의명령`).

| 명령 | 누가 | 하는 일 |
|---|---|---|
| `/겜모` · `/gm` · `/겜모 0` `1` · `/gm 0` `1` | 관리자(OP) | 서바이벌 ↔ 크리에이티브 토글 / 0 서바이벌 · 1 크리에이티브 (나 자신) |
| `/텔포 <닉네임>` | 관리자 | 그 유저에게 이동 = `/유저관리 <닉> 찾아가기` 와 같은 코드 (한글 닉 자동완성, 다른 서버면 `huskhomes:tp`) |
| `/소환 <닉네임>` | 관리자 | 그 유저를 내 위치로 = `불러오기` (다른 서버면 `huskhomes:tphere`) |
| `/공지 <할말>` | 관리자·콘솔 | 모든 서버 접속자 알림창(제목 "운영자 공지") + 소리 |
| `/뒤로` · `/back` | 관리자 | HuskHomes `huskhomes:back` 이 있으면 그것(서버 간·사망 위치는 HuskHomes 설정), 없으면 이 서버 안에서 마지막 순간이동 전 위치·죽은 곳 |
| `/스폰` · `/spawn` | 관리자 | HuskHomes `huskhomes:spawn` 이 있으면 그것, 없으면 첫 월드 스폰 |
| `/메모리` | 관리자·콘솔 | 메모리 사용/최대, TPS 1·5·15분, MSPT, 접속자, 로드된 청크·엔티티 수 (채팅) |
| `/도움기록` | 관리자 | 이 서버가 받은 최근 도움 요청 20개 |
| `/도움 <할말>` | 유저 (`magiccodex.convenience.help`, 기본 켜짐) | 운영자(OP)에게만 알림창+소리+채팅 한 줄(누르면 `/텔포 <닉>` 입력). 보낸 사람 "운영자에게 보냈어요". 쿨다운 60초(설정) |
| `/쓰레기통` | 유저 (`magiccodex.convenience.trash`, 기본 켜짐) | 빈 6줄 보관함 창, 닫으면 넣은 아이템 삭제 (콘솔에 개수 기록) |

- 오너 수정(2026-10-26): `/뒤로`·`/back`·`/스폰`·`/spawn` 도 **관리자 전용**. 유저가 쓰는 건 `/도움`·`/쓰레기통` 뿐. 그래서 전투 중 이동 막기(#91)는 해당 없음(관리자 예외). combat-lock.yml 목록은 그대로 둠 (유저가 HuskHomes 명령을 직접 치는 경우 대비).
- `/홈`·`/셋홈` 은 만들지 않음. **오너: 필요 없음 → HuskHomes 의 home 관련 권한(`huskhomes.command.home`·`sethome` 등)을 유저에게서 끄기 권장.** `/spawn`·`/back` 도 관리자 전용이 됐으니 유저의 HuskHomes spawn·back 권한도 끄기 권장 (이름 붙은 `/huskhomes:spawn` 으로는 여전히 칠 수 있음).
- 우리 `/spawn`·`/back` 은 Brigadier 로 등록돼 HuskHomes 의 이름 없는 `/spawn`·`/back` 을 가린다(OP 가 아니면 자동완성에도 안 보임). 위임은 이름 붙은 `huskhomes:spawn`·`huskhomes:back` 으로 하므로 서로 꼬이지 않음.
- 도움 요청 흐름: 이 서버에 OP 가 있으면 바로 "보냈어요". 없으면 다른 서버로 전하고 3초(`ack-wait-ticks`) 동안 다른 서버 OP 의 확인을 기다림 → 확인 오면 "보냈어요", 없으면 "지금 접속한 운영자가 없어요…" + 기록. 기록 = 메모리(최근 50, 이 서버에 OP 가 들어오면 채팅으로 보여 주고 비움) + 파일 `plugins/MagicCodexBridge/help-requests.log`(모든 요청, 비동기 추가). 서버를 껐다 켜면 메모리 기록은 비고 파일은 남음.
- 서버 간 전달: 프록시 플러그인 변경 없이 **Velocity 의 BungeeCord 호환 채널**(`BungeeCord` Forward ALL, 하위 채널 `ChacaConvenience`)로. 받는 서버에 접속자가 1명 이상 있어야 도착(없으면 받을 사람도 없음). 같은 메시지는 id 로 한 번만, 내가 보낸 것은 실행 토큰으로 거름. 접속자가 없는 서버에서 콘솔로 친 `/공지` 는 그 서버에만(결과 알림에 그렇게 나옴). `cross-server: false` 면 이 서버만.
- 부담: 매 틱 일 없음. 순간이동·사망 이벤트에서 OP 의 위치 하나만 적음. `/메모리` 의 청크·엔티티 수는 칠 때 한 번 셈.
- 바꾼 파일: `ConvenienceCommands.java`·`ConvenienceRules.java`·`ConvenienceRulesTest.java`·`convenience-commands.yml`(새), `AdminCommandBridge.java`(등록 3줄), `OperatorCommands.java`(`goTo`·`bring` 의 private 뗌), `plugin.yml`(권한 2개). operating-config 에는 안 넣음(combat-lock.yml 과 같은 방식, overlay check 차이 없음).
- 빌드 때 볼 것: Paper API `Server#getTPS()`·`getAverageTickTime()`, `Player#playSound(Location, String, SoundCategory, float, float)` 는 저장소에서 처음 씀 (Paper 1.21.4 에 있음).

**같이 배포할 것**: MagicCodexBridge jar → 학교·야생·아레나 등 모든 Paper 서버 (공지·도움을 전 서버에서 받으려면 전부). 서버 파일 손볼 것 없음(yml 자동 생성).
**배포 확인**: Velocity `velocity.toml` 의 `bungee-plugin-message-channel = true` (Velocity 기본값 true — 꺼져 있으면 공지·도움이 그 서버에만 감). HuskHomes 로 서버 간 `/back`·`/spawn` 을 하려면 HuskHomes 설정(cross-server, global spawn, back·사망 위치 저장)이 켜져 있어야 함.

**게임 확인 방법** (OP 1명 + 일반 유저 1명, 학교·야생 각각 접속):
1. OP: `/겜모` → 크리에이티브, 다시 `/겜모` → 서바이벌. `/gm 1`, `/gm 0`. 일반 유저는 `/겜모`·`/스폰`·`/뒤로` 가 자동완성에 안 보임.
2. OP(학교): `/텔포 <같은 서버 유저 한글 닉>` → 옆으로. `/소환 <닉>` → 데려옴. 야생에 있는 유저 닉으로 → HuskHomes 로 서버 이동.
3. OP: `/텔포` 한 뒤 `/뒤로` → 원래 자리 (HuskHomes 있으면 HuskHomes 메시지). 죽은 뒤 `/뒤로` → 죽은 자리. `/스폰` → 스폰.
4. OP(학교): `/공지 점검 10분 전이에요` → 학교·야생 접속자 모두 알림창 + 딩 소리.
5. 유저(야생): `/도움 길을 잃었어요` → 학교 OP 에게 알림창+벨 소리+채팅 줄(누르면 `/텔포 <닉>` 입력), 유저에게 "운영자에게 보냈어요". 바로 다시 → "조금 뒤에 다시 보낼 수 있어요 · N초". OP 가 아무도 없을 때 → "지금 접속한 운영자가 없어요…", 그 서버에 OP 가 들어오면 3초 뒤 채팅으로 보임. `/도움기록` 으로 목록.
6. 유저: `/쓰레기통` → 흙 넣고 닫기 → "아이템 N개를 버렸어요", 다시 열면 비어 있음.
7. OP: `/메모리` → 메모리·TPS·MSPT·접속자·청크·엔티티 줄.
## 21. 운영 대시보드 하트비트 (2026-10-26, 메모 #92)
- 새 모듈 `ops-dashboard` (게임과 따로 도는 웹 앱, `java -jar`) — 설치·설정·자동 시작은 [CLAUDE_OPS_DASHBOARD](CLAUDE_OPS_DASHBOARD.md). 루트 `settings.gradle` 에 `include 'ops-dashboard'` 한 줄.
- **MagicCodexBridge 변경**: `ServerHeartbeat`·`SparkProbe` 추가, `MagicCodexBridge` 에 필드·시작·종료 3줄. 10초마다(메인 스레드는 숫자만 읽음) 전용 스레드가 공유 DB 표 `codex_server_status` 에 서버별 한 줄을 덮어씀. spark API 를 리플렉션으로 (없으면 Bukkit TPS·평균 틱·JVM CPU). 끌 때 `state=stopped` 를 한 번 씀. `/서버관리 상태` 에 "하트비트" 줄. 실패해도 게임은 계속 (경고 한 줄).
- 새 설정 파일 `plugins/MagicCodexBridge/server-heartbeat.yml` (jar 가 처음에 만듦): `enabled: true`, `server-id: ""`(= 서버 폴더 이름), `interval-seconds: 10`(5~60), `world-counts: true`. `database.properties` 가 MariaDB 가 아니면 자동으로 꺼짐.

| `codex_server_status` 열 | 형식 | 뜻 |
|---|---|---|
| server_id (PK) | VARCHAR(48) | school·wild·arena·tutorial_1… |
| state | VARCHAR(12) | running / stopped |
| updated_at · started_at | BIGINT | epoch ms |
| source | VARCHAR(8) | spark / bukkit |
| tps_10s · tps_1m · tps_5m · tps_15m | DOUBLE | TPS |
| mspt_mean · mspt_max · mspt_p95 | DOUBLE | ms (-1 = 모름) |
| cpu_process · cpu_system | DOUBLE | % |
| heap_used_mb · heap_max_mb | INT | 힙 |
| online · max_players · worlds · chunks · entities | INT | 개수 (-1 = 안 셈) |
| version | VARCHAR(96) | 서버 이름·버전 |

- 배포: Bridge jar 를 학교·야생·아레나 `plugins/` (튜토리얼 서버도 만들면). 대시보드 `servers=` 이름 = 하트비트 server_id. 테스트 `ServerHeartbeatTest`(+대시보드 `OpsLogicTest`).

## 22. 같은 NPC 동시 이용·호감도 상한 퀘스트 (2026-10-26, 메모 #99·#100)

**#99 확인 결과** — 사람별 진행은 이미 분리돼 있음:
- 스토리 플러그인: 재생 대기열·결말 세션(`LocalEndingSessions`, 플레이어별 토큰)·대화 이벤트·컷신 완료가 모두 보낸 사람의 `PlayerStory` 에만 적용 (`StoryPlugin.onPluginMessageReceived`). "이미 재생 중"·"대기열 가득"(16개)도 그 사람 대기열 기준. 단계(`QuestEngine`) NPC 클릭·대기대화(`플레이어|단계|NPC` 키)도 플레이어별.
- 컷신은 클라 화면(`CutsceneScreen`)이라 서버 NPC 를 움직이거나 돌리지 않음.
- ChacaNPC: 대화 세션 `UUID → DialogueSession`, AI 대화 기록·호감도·힌트·퀘스트 제안도 세션 안. 퀘스트 수락 토큰은 플레이어·NPC·세션 토큰에 묶임 (`answerQuest`). HUD 패킷은 세션 토큰이 맞아야 받음 (`NpcTalkChannel`). 선물 처리 중 잠금도 플레이어별.
- MagicCodex: 고정 대화 세션(토큰+순번)·의뢰(수락·완료·보상 대상 = 패킷 보낸 사람)·NPC 대화 목표(클릭한 사람만)·수업 메뉴·상점 세션 모두 플레이어별.
- 의도된 공유: 누가 대화 중이면 그 NPC 는 일과표 걷기를 멈춤(끝나면 다시 출발), 3D 외형 대화 동작. 다른 사람 대화를 막거나 끊지 않음.

**#99 고친 것**: 둘 이상이 같은 NPC 와 동시에 대화하면 1초마다 NPC 가 서로를 번갈아 보며 돌아섰음 (NPC 몸은 모두에게 하나). → NPC 하나당 가장 최근에 말한 사람 한 명만 바라봄 (`DialogueService.newestPerNpc`, 1초 tick 에 세션 수만큼만 셈 — 부담 없음). 처음 말 걸 때는 그대로 그 사람을 봄.

**#100 확인 결과**: 상한(30·60·90)에서 선물 → `AffinityStore.beginGift` 가 하루 횟수 예약 전에 FULL → ChacaNPC 가 아이템 차감 없이 `gift_full` 대사. 선물 버튼도 먼저 상한을 보고 창을 안 엶. AI 대화 보상(`addCounted`)·일반 상승(`add`)도 상한이면 횟수를 안 씀. 상한 퀘스트 보상이 끝나면 `questCompleted` → 하트 단계 올림 → 같은 날 선물 재개, 새 상한까지만 오름.

**#100 고친 것**: 상한 퀘스트를 이미 깼는데 하트가 안 오른 경우(상한 퀘스트 설정이 없는 다른 서버에서 보상 받음, 하트 기록 DB 실패, 설정을 나중에 넣음) 일회성 의뢰라 다시 줄 수도 없어 계속 상한에 갇혔음. → 대화 열기(호감도 읽기)·선물·호감도 상승 때 상한 퀘스트 완료 기록이 있으면 다시 주지 않고 바로 다음 상한을 연다 (`NpcSocialService.gateAction`, `QuestBridge.completedEver` — 접속 때 읽은 캐시만 봄).

- 바꾼 파일: `chaca-npc-paper/.../dialogue/DialogueService.java`, `SharedNpcFacingTest.java`(새), `magic-codex-paper/.../NpcSocialService.java`, `QuestBridge.java`(메서드 1개), `AffinityStoreTest.java`(테스트 2개).

**같이 배포할 것**: ChacaNPC jar, MagicCodexBridge jar (NPC 있는 서버 전부).

**게임 확인 방법** (유저 2명):
1. 두 사람이 같은 ChacaNPC 에게 차례로 말 걸기 → 각자 다른 대화·버튼·하트, 한 사람이 닫아도 다른 사람 대화는 계속. NPC 는 마지막에 말한 사람 쪽을 보고 1초마다 돌아서지 않음.
2. 두 사람이 같은 스토리 NPC 를 누름 → 각자 자기 단계 대화만, 한 사람의 선택지가 다른 사람 화면을 넘기지 않음.
3. 관리자: `/유저관리 <닉> 호감도 <npc> 설정 30` → 그 NPC 에 선물 → "지금은 괜찮아" 류 대사, 아이템·하루 횟수 그대로. 상한 퀘스트 받아 완료 → 같은 날 선물이 다시 들어가고 60 에서 멈춤.
4. (복구) 상한 퀘스트를 완료한 유저를 `/유저관리 <닉> 호감도 <npc> 하트 설정 0` 으로 되돌린 뒤 그 NPC 에 말 걸기 → 퀘스트를 다시 주지 않고 하트가 1 로 돌아옴.
## 23. 야생 청크 점유 — 땅문서 (2026-10-26, 메모 #88)
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
## 24. 인벤(아공간 배낭) 열 때 버벅임 (2026-10-26, 메모 #101)
오너: "인벤 열 때 db 조회 때문에 약간 버벅이는 게 있어. 인벤 여는데 렉 걸리면 문제다."

**추적 결과 — E 를 누르는 순간 일어나는 일**
- 클라 `GalaxyBagSwapMixin` → `new GalaxyBagScreen` → `init()` 에서 `BagPagesClient.hello()` 를 보내고 **기다리지 않음**(마지막 상태로 바로 그림). 바닐라는 자기 인벤을 열 때 서버에 아무것도 안 보냄.
- 서버 `BagPagesBridge.onPluginMessageReceived` HELLO(`BagPagesBridge.java:172` 근처) = 메모리(`entries`, coordinator `holders`)만 읽고 1줄 패킷. **SQL·HuskSync 조회 없음.** 쪽·해금 수는 접속/동기화 때 io 스레드(`BagHuskCoordinator.verifyIncoming`·`complete`)와 쪽 넘김 뒤에만 읽음. → 서버 DB 가 열기 렉의 원인은 아님(주석으로 "HELLO 는 메모리만" 못 박음).
- 실제 원인 (클라):
  1. `UiResources.java:20,50-56` — 배낭 그림 8장(테두리 1817×866·제작창 1108×1420 등, 밉맵 포함 약 23MB)이 **공용 그림 캐시(192장/128MB)** 에 있었음. 도감(마법 아이콘 수백 장)·상점·장비창 등을 보고 나면 LRU 로 배낭 그림이 먼저 지워짐 → 다음 E 때 PNG 다시 디코드 + 프레임마다 1장씩 밉맵 업로드, 그동안 `UiResources.Entrance` 가 화면 전체를 가리고 **"화면을 준비하고 있어요"** 를 띄움 → 오너가 본 "DB 읽는 듯한" 멈칫.
  2. 배낭 글자(아공간 배낭·제작·조합법·쪽 표시·개수 숫자)가 **공용 글자 캐시(768개/16MB)** 에 있어 HUD·다른 창 글자에 밀리면 열 때마다 AWT 로 다시 구움(렌더 스레드에서 동기).
  3. `GalaxyBagScreen.java:119-123` — 제작창이 열려 있으면 **10틱마다** 레시피 전체(레시피북 × RecipeFinder 제작 가능 계산)를 다시 만듦 → 열려 있는 동안 0.5초마다 작은 멈칫.
  4. 열 때마다 HELLO 왕복 (가벼우나 불필요).

**고친 것** (클라 3파일 + 서버 주석)
- `UiResources`: 배낭 전용 `bagImages()`(HudTextureCache, 비동기)·`bagText()`(CodexTypography) 추가. 다른 창이 밀어내지 않음. 미리 올리기(warm)도 배낭 그림은 전용 캐시로. 리소스 리로드·접속 끊김 `reset()` 때 같이 닫음. `Entrance.draw(c,w,h,cache)` 오버로드(기존 호출은 그대로 공용 캐시).
- `GalaxyBagScreen`: 그림·글자를 전용 캐시로, 가림막도 전용 캐시 기준(툴팁 그림이 늦어도 가림막 안 뜸). 공용 캐시 beginFrame/endFrame 은 툴팁·스탯 아이콘용으로 그대로. 레시피는 **바뀐 게 있을 때만** 다시 만듦: 인벤 변경 수 + 2×2 칸 지문(`gridSignature`) + 서버 레시피북 갱신(`refreshRecipeBook`). 10틱 폴링 삭제.
- `BagPagesClient.helloIfNeeded()`: 상태가 없거나 준비 안 됐거나 30초 지났을 때만 HELLO. 서버가 준비/막힘/해금/쪽 넘김/관리자 변경을 모두 먼저 보내 주므로 안전. 화면은 절대 응답을 기다리지 않음.
- `BagPagesBridge`: HELLO 경로가 메모리 전용임을 주석으로 고정. **쪽 넘김·정리·확장권의 lease/CAS·HuskSync 세션 검증·WAL·복제 방지는 한 줄도 안 건드림.**

**남은 위험**
- 게임에서 측정 못 함(빌드·실행 불가). 원인 1·2 는 코드상 확실한 경로지만 체감 비중은 게임 확인 필요.
- 접속 직후 배낭 그림 미리 올리기가 끝나기 전에(1초 안팎) E 를 누르면 처음 한 번은 가림막이 뜰 수 있음(전과 같음).
- 배낭 그림 약 23MB 가 공용과 별도로 늘 GPU 에 남음(공용 쪽 배낭 사본은 안 쓰이므로 곧 빠짐 → 실질 증가 없음).
- 제작창이 열린 채 아이템 한 칸 안 데이터만 바뀌는 경우(개수·객체 동일) 레시피가 다음 인벤 변경까지 안 바뀔 수 있음 — 레시피 판정은 아이템 종류·개수만 보므로 실사용 영향 없음.
- 서버 쪽 넘김의 WAL `fsync`·`p.saveData()` 는 메인 스레드에서 돎(쪽 넘김 때만, 안전장치라 그대로 둠). 쪽 넘길 때 렉이 보이면 별도 검토.

**같이 배포할 것**: magic-codex-fabric 클라 모드 jar (런처 배포). 서버 Bridge 는 주석만 바뀌어 다시 올릴 필요 없음(다른 패치와 같이 올라가도 무방). 프로토콜 변경 없음 → 옛 클라·새 서버 섞여도 됨.

**게임 확인 방법**
1. 접속 후 E → 배낭이 바로 뜨고 가림막("화면을 준비하고 있어요") 없음.
2. 마법 도감을 열어 여러 쪽 넘기고, 상점·장비창도 연 뒤 닫고 바로 E → 가림막 없이 바로 뜸 (전에는 여기서 멈칫).
3. 제작창(우상단 아래 버튼)을 연 채 가만히 있기 → F3 프레임 그래프에 0.5초 주기 튀는 막대 없음. 나무를 넣으면 판자 카드가 바로 "제작 가능"으로 바뀜, 빼면 바로 돌아감. 새 레시피 해금 시 목록 갱신.
4. 확장권으로 2쪽 연 뒤 E → 쪽 표시 "1 / 3", 화살표로 넘기기·되돌리기 정상, 복제 없음(기존 쪽 넘김 확인 절차 그대로).
5. 서버 이동(학교↔야생) 후 E → 쪽 표시·잠금이 새 서버 상태로 바로 맞음.
## 25. 아티팩트 천장 폐기 · 상점 교환 · 등급 확정 뽑기권 (2026-10-26, 메모 #98)

오너 결정: **기존 천장(pity) 폐기.** 중복 아티팩트 → 아티팩트 조각(`item:artifact_shard`) 우편 지급은 그대로(`summon.duplicate.amount` 1). 조각은 상점 **"아티팩트 교환소"** 에서 **등급 확정 뽑기권**과 바꾼다.

**천장 걷어낸 것**
- `ArtifactBridge`: `summon.pity` 읽기, 미리 굴린 대체 결과, 소환 창 안내문 "유일 이상 확정까지 N회", `/컨텐츠관리 아티팩트 목록` 의 "천장:" 줄, `/유저관리 <닉> 아티팩트 보기` 의 "천장:" 줄, 감사 로그 "천장발동·천장카운터" 칸 없앰. 프로토콜은 그대로 (안내문 칸에 천장 문구만 안 붙음).
- `ArtifactOwnershipStore`: `Choice.pity`·`Pity` 레코드·`codex_artifact_pity` 만들기/읽기/쓰기·`Poll.pity` 없앰. `ArtifactOwnershipService`: `pityCount`·`ensurePityTable` 없앰. `ArtifactPurchaseJournal`: `pityGrade/pityCount` 안 씀. **배포 전 남은 옛 영수증**(`artifact-purchase-receipts/*.properties`, "/대체결과" 붙은 것)도 읽힌다 — 대체 결과는 버리고 원래 굴린 결과로 처리.
- `artifacts.yml` (소스 + `deployment/operating-config`): `pity:` 블록 → `tickets:` 블록. overlay `regenerate`·`check` "차이 없음".
- `ArtifactPityTest` 삭제.
- **DB**: `codex_artifact_pity` 는 더 만들지 않는다. **운영 DB 에 남은 `codex_artifact_pity` 표는 지워도 됨** (`DROP TABLE codex_artifact_pity;`, 코드가 안 읽음 — 안 지워도 무해).
- `tools/live-handoff-20261025/bounded-packaging` 의 천장 관련(`FinalArtifactPityHookCheck.java`, `OpeningSpecial.java` 의 `ArtifactOwnershipStore`·`ArtifactOwnershipService` 케이스, `helper-source/…/ArtifactOwnershipStore.java` 의 `opening$pity`) = **옛 확인용, 무시.** 2026-10-25 배포 패키징용이고, 오픈 최종(`tools/opening-final-20261025`)은 자체 `OpeningSpecial` 을 쓰며 천장 훅이 없음. README 에도 적어 둠. → 이번 합성 때 **`ArtifactBridge`·`ArtifactOwnershipStore`·`ArtifactOwnershipService`·`ArtifactPurchaseJournal` 은 새 빌드 클래스를 통째로** 쓴다 (운영 jar 의 천장 메서드를 살리지 말 것. 남기면 없는 표를 읽음).

**상점 교환 (돈 대신 아이템을 내고 사기)** — 중단됐던 `dd3a2d76` 을 다시 적용하고 완성
- 저장: `shop_products.cost_item`·`cost_count`, `shop_orders.cost` 열 (추가 전용 ALTER, 서버가 시작할 때 자동). 교환 상품은 판매 막힘.
- 거래 (`ShopBridge`): `prepared` → 비용 아이템 회수 + 영수증 PDC `shop_sale_<id>` 같은 틱 기록·`saveData` → 플레이어 파일에 저장된 것 확인 → `paid` → 우편 지급(출처 키 `shop:<id>` 멱등) → `done` → 영수증 지움. 판매와 같은 방식이라 **경제(Vault) 없어도 동작**. 서버가 시작할 때 `prepared` 교환은 자동 실패 처리하지 **않음**(비용 회수 뒤일 수 있음) — 유저가 그 서버에 접속하거나 상점을 열 때 영수증 유무로 판정(있으면 이어서 우편, 없으면 실패). `paid` 는 구매처럼 자동 재발송.
- 비용 아이템 판정: ItemsAdder 키(`namespace:id`)면 ItemsAdder 아이템만, `minecraft:id` 면 메타 없는 바닐라만 센다. ItemsAdder 가 꺼져 있으면 ItemsAdder 비용 교환은 거절(아무것도 안 뺌).
- 관리자 처리 `/상점관리 거래처리 <번호> 완료|환불|취소`: 교환은 완료 = 상품 우편, 환불 = 비용 아이템 우편 반환(`shop-refund:<id>` 멱등), 취소 = 보상 없이 닫기. `/유저관리 <닉> 거래기록` 에 "교환 · 비용 item:artifact_shard ×N".
- 명령: **`/상점관리 교환 <상점> <상품> <아이템키> <개수|없음>`** (없음 = 해제, 원래 구매가·판매가로). `/상점관리 목록 <상점>` 에 "교환 item:… ×N". 관리 창(`/상점관리 편집`)은 **안내 줄로만 보임**(교환 칸 없음, 편집해도 교환 값은 안 바뀜).
- 가져오기 파일: `교환: "item:artifact_shard 10"` 칸, 아이템 칸에 ItemsAdder 키 허용. 번들 `shops/artifact_exchange.yml` (상점 `artifact_exchange` "아티팩트 교환소", 분류 "확정 뽑기권").
- 프로토콜: 상점 **응답만** 머리 `SHP3`→`SHP4` (상품마다 비용 이름·개수·보유 수·견본). 요청은 SHP3 그대로, 새 클라는 SHP3 응답도 읽음. **옛 클라는 새 서버의 상점 응답을 못 읽음 → 서버·클라 같이 배포.** 교환 상품은 `buy` = 1개당 비용 개수, `sell` = "".
- 클라 (`ShopScreen`·`ShopView`): 가격 자리(카드·아래 구매 칸·합계)에 "아티팩트 조각 ×10", 동전 자리에 비용 아이템 아이콘. 살 수 있는 수량 = 가진 조각 ÷ 개수. 완료 알림 "… 교환 완료 · 아티팩트 조각 ×N 사용". 새 장식 없음.
- 테스트: `ShopExchangeTest` (새, 순수 로직). 빌드 때 실행.

**등급 확정 뽑기권**
- `artifacts.yml summon.tickets` (등급 키 common/rare/unique/legendary, 줄을 빼면 그 확정권 없음, 키가 열쇠·조각·서로 같으면 새로고침 실패):
  `rare: {item: "item:artifact_ticket_rare", name: "고급 확정 뽑기권"}` · `unique: … _unique` · `legendary: … _legendary`
- **사용 흐름**: 확정권을 들고 우클릭 → (모드·DB 준비·그 등급 아티팩트 있음 확인, 안 되면 알림만 · 확정권 안 줄어듦) → 열쇠 뽑기와 같은 영수증 흐름으로 확정권 1장 차감·그 등급 아티팩트 1개 균등 추첨 → 소환 창(`ArtifactSummonScreen`)이 열리고 결과가 오면 **같은 연출**(유물함 5개 고르기 → 열기 → 카드). 중복이면 조각 우편(열쇠와 같음). 결과 카드 아래 "고급 확정 뽑기권 사용 · 고급 확정". 창의 "다시 소환" 은 열쇠로 뽑음. 클라 변경 없음.
- `/컨텐츠관리 아티팩트 목록` 에 "확정권:" 줄. 감사 로그 "재료" 칸에 확정권 키.
- **교환소 기본 가격 (자리표시, 오너 결정 필요): 고급 10 · 유일 30 · 전설 80 조각.**

**ItemsAdder 정의 예시** (서버 `plugins/ItemsAdder/contents/<폴더>/configs/artifact_tickets.yml`, namespace 는 기존 `item:random_artifact` 와 같은 `item`. 그림은 오너가 따로 — 텍스처 `textures/item/artifact_ticket_*.png`)
```yaml
info:
  namespace: item
items:
  artifact_ticket_rare:
    display_name: "고급 확정 뽑기권"
    lore: ["우클릭: 고급 아티팩트 1회 뽑기", "이미 가진 아티팩트면 조각으로"]
    resource: {material: PAPER, generate: true, textures: [item/artifact_ticket_rare.png]}
  artifact_ticket_unique:
    display_name: "유일 확정 뽑기권"
    lore: ["우클릭: 유일 아티팩트 1회 뽑기", "이미 가진 아티팩트면 조각으로"]
    resource: {material: PAPER, generate: true, textures: [item/artifact_ticket_unique.png]}
  artifact_ticket_legendary:
    display_name: "전설 확정 뽑기권"
    lore: ["우클릭: 전설 아티팩트 1회 뽑기", "이미 가진 아티팩트면 조각으로"]
    resource: {material: PAPER, generate: true, textures: [item/artifact_ticket_legendary.png]}
```
(`item:artifact_shard` 도 ItemsAdder 에 있어야 함 — 기존.) 정의 뒤 `/iazip` → 리소스팩 다시 배포.

**바꾼 파일**: paper `ArtifactBridge`·`ArtifactOwnershipStore`·`ArtifactOwnershipService`·`ArtifactPurchaseJournal`·`ShopBridge`·`ShopStore`·`ShopImportFile`·`ShopAdminBridge`·`ShopExchange`(새), `resources/artifacts.yml`·`resources/shops/artifact_exchange.yml`(새), 테스트 `ArtifactPityTest`(삭제)·`ShopExchangeTest`(새); protocol `ShopProtocol`; fabric `ShopScreen`·`ShopView`; `deployment/operating-config/MagicCodexBridge/artifacts.yml`(+manifest); `tools/live-handoff-20261025/README.md`(무시 표기).

**같이 배포할 것**: MagicCodexBridge jar → 학교·야생·아레나 전부 + **MagicCodex 클라 모드 (상점 프로토콜 SHP4, 같이)**. 서버 `plugins/MagicCodexBridge/artifacts.yml` 은 jar 가 안 덮으므로 operating-config 것으로 교체(또는 `pity:` 블록 지우고 `tickets:` 추가) 뒤 `/서버관리 새로고침 아티팩트`. ItemsAdder 확정권 3종 정의 + 리소스팩. DB 는 자동(ALTER 3열), `codex_artifact_pity` 는 지워도 됨.

**게임 확인 방법**
1. 학교에서 `/상점관리 가져오기 artifact_exchange` → "아티팩트 교환소(artifact_exchange) 상점을 만들고 상품 3개 추가". `/상점관리 열기 artifact_exchange` → 카드 가격이 "아티팩트 조각 ×10/×30/×80" + 조각 아이콘, 판매 탭 없음.
2. 조각 9개만 들고 고급 확정권 → 구매 버튼 꺼짐(가능 수량 0). 조각 25개 → 수량 최대 2, 2개 교환 → 조각 20개 줄고 우편에 고급 확정권 2장, 알림 "교환 완료 · 아티팩트 조각 ×20 사용". Vault 꺼진 서버에서도 됨.
3. 교환 도중 서버를 죽였다 켜기(어려우면 생략): 다시 접속하면 "이전에 교환한 상품을 우편으로 보냈어요" 또는 조각 그대로.
4. 우편에서 확정권을 받아 들고 우클릭 → 소환 창이 열리고 유물함 고르기 → 고급 아티팩트 카드, 확정권 1장 줄어듦. 이미 가진 것이면 조각 우편. 전설 확정권도 같은 방법(전설 아티팩트가 켜져 있어야 함).
5. 소환 창 안내문·`/컨텐츠관리 아티팩트 목록` 에 "천장" 글자가 없음, 목록에 "확정권:" 줄.
6. `/상점관리 교환 artifact_exchange 고급확정뽑기권 item:artifact_shard 12` → 가격 ×12. `… 없음` → 해제(구매가 없음이면 안 보임).
## 26. 정보창 v4 (내 정보 · 상대 정보, 2026-10-26, 오너 납품 패키지)
- 기준: `docs/design/profile-ui-v4/` (approved-v4.png 확정 시안 · 개발_프롬프트 · layout-metrics.json · panel-layout.json). 좌표 1672×941, min(가로비, 세로비) 균일 배율·가운데 정렬. 패널은 nine-slice(slice 값 그대로), 구분선·진행 막대·닫기 X·꺾쇠·포커스·비활성(알파 0.4)은 runtime 값으로 코드에서 그림. 레퍼런스에 없는 장식 없음. 섹션 머리 아이콘(칼·빗자루·책·옷·막대)·모험 기록 행 아이콘·방 가기 버튼 집 아이콘·인기도 버튼 색 테두리는 레퍼런스에만 있고 PNG 가 없어 **안 그림** (실제 PNG 조립 미리보기와 같음 — 필요하면 PNG 요청).
- 같은 단축키(I)·상단 메뉴·하우징·파티에서 여는 내 정보, `/스탯창 <닉>`·웅크리고 우클릭으로 여는 상대 정보 = 모두 새 창. 제목은 둘 다 "정보창". Tab/Shift+Tab 포커스, Enter·Space 실행.
- 자산: `magic-codex-fabric/src/main/resources/assets/magiccodex/textures/gui/profile/{panels,stat-icons,action-icons}/` (납품 PNG 20개 그대로 복사). 경지 9개는 기존 `textures/gui/kyudichi/ui/tiers/*.png` 와 SHA256 이 같아 그 파일을 그대로 씀 (복사·수정 없음).
- 바뀐 파일 (클라): `StatsScreen`(새로 씀), `StatsLayout`(새 좌표, 순수 계산), `RemoteStatsClient`(v3 채널·내 정보 요청·인기도 결과), `StatsRanks`(새, 1:1 대전·큐디치 카드 = 기존 결투장 전적 요청 PROFILE 재사용), `OtherPortraits`(새, §28), `StatsSocialActions`(POPULARITY_DOWN), `KyudichiQueueClient`(정보창이 물은 전적 답만 가로채는 1줄), `PortraitClient`(상대 일러스트 패킷 넘기기 몇 줄), 테스트 `StatsTest`(layout-metrics.json 좌표와 비교·배율·파생 값).
- 바뀐 파일 (서버 MagicCodexBridge): `StatsBridge`(v3), `QuestBridge`(요약 `summary()` 추가), `EquipmentBridge`(`accessoryItem()` 1줄), `MagicCodexBridge`(접근자 2줄). ChacaHousing: `HousingPlugin.likesOf()`·`HousingStore.likes()` (좋아요 합계, 비동기, Bridge 가 리플렉션으로 부름 — 옛 ChacaHousing 이면 "—").
- **프로토콜 `StatsProtocol` v3** (`magiccodex:stats_request3` / `stats_response3`, 버전 0x53544103). v2 채널·바이트는 그대로라 옛 모드는 예전처럼 동작. 새 서버는 v3 응답 채널을 듣는 클라에만 v3, 새 클라는 서버가 v3 요청 채널을 열었을 때만 v3. v3 추가 값: 통합 칭호 한 줄(접두+접미), 하우징 좋아요 합계, 진행 중 퀘스트 수·추적 퀘스트(이름·목표 합계), 반지·귀걸이·목걸이 아이템, 장착 아티팩트·빗자루 ID, 인기도 상태(오늘 조절했는지·내 해제권 수), 요청 DOWN(5)·SELF(6), 응답 VOTED(5). 테스트 `StatsProtocolTest` 갱신(옛 테스트 그대로 + v3).
- 값 출처: 랭킹·레이팅·경지 = 프록시 랭킹 DB (기존 결투장 전적, rank ≤ 0 → "미집계", 기록 없음 → "기록 없음"), 발견한 마법 = 기존 습득/전체(전체는 마법 목록 수, 고정값 아님), 능력치 = 기존 값, 마법 가속 아랫줄 = 100·h/(100+h) %. 모르는 값은 "—" (0 으로 꾸미지 않음).
- 추적 퀘스트: 저장소에 "추적" 기능이 없어 **진행 중 메인 퀘스트, 없으면 처음 받은 진행 중 퀘스트**를 보여 줌 (목표 진행 = 목표 합계 current/required). 추적 기능이 생기면 `QuestBridge.summary()` 만 바꾸면 됨.
- 비활성(이유는 마우스를 올리면 보임): 인기도 버튼 — 내 정보("자신의 인기도는 조절할 수 없어요")·옛 서버, 칭호 자세히·장비 자세히 — 상대 정보(상대용 창 없음, 내 정보에선 기존 칭호 창·장비 창 연결), 상대 방 가기 — 친구가 아닐 때(하우징 규칙 = 친구만), 친구 추가 — 이미 친구, 하우징 서버 없음 → 방 가기 비활성.
- 배포: MagicCodexBridge + ChacaHousing + MagicCodex 모드(+ magic-codex-protocol) 같이.

## 27. 인기도 (2026-10-26, 메모 #87)
- 규칙: 다른 플레이어 인기도 올리기·내리기, 같은 상대에게 하루 1번(상대마다 따로), 자기 자신 안 됨. 오늘 이미 조절한 상대에게 한 번 더 누르면 **인기도 제한 해제권 1개를 자동으로 써서** 한 번 더, 없으면 거절 안내. 하루 기준 = `NpcSocialService.today()` (Asia/Seoul 자정, NPC 일일 제한과 같음).
- 새 파일: `PopularityRules`(순수 규칙 + `PopularityRulesTest`), `PopularityStore`(DB), `PopularityBridge`(버튼·관리 명령·일일 제한·알림), `popularity.yml`(처음 켤 때 자동 생성). StatsBridge 가 만든다 (MagicCodexBridge.onEnable 안 고침).
- **표** (MariaDB 공유 / SQLite 는 `plugins/MagicCodexBridge/popularity.db`, 자동 생성):
  - `codex_popularity` (player CHAR(36) PK, score INT, updated BIGINT) — 점수 (-1,000,000 ~ 1,000,000)
  - `codex_popularity_daily` (actor, target, day CHAR(10), seq, delta +1/-1, ticket 0/1, created BIGINT; PK actor·target·day·seq) — 오늘치 상·하락 기록 (누가·누구에게·올림/내림·해제권 사용·시각). `keep-days`(기본 30일) 지난 줄은 서버 켤 때 정리.
  - 한 거래 안에서 대상 점수 줄 잠금 → 오늘 (행위자→대상) 기록 수 확인(FOR UPDATE) → 기록 추가 → 점수 증감. DB 는 전용 IO 스레드, 결과는 메인 스레드.
- 해제권: 첫 시도에서 "오늘 이미 함"이면 메인 스레드에서 해제권 1개를 떼어 두고 다시 거래. DB 실패·필요 없었으면(그 사이 관리자가 오늘 기록 삭제) 돌려줌 (인벤토리 → 넘치면 발밑 → 접속 끊김이면 우편). 인벤토리에서 찾기·떼기는 ArtifactBridge 열쇠와 같은 방식(ItemsAdderItems).
- 대상 알림(접속 중일 때, 공통 알림창): 올리기 "{player}님이 인기도를 올렸어요", 내리기 "누군가 인기도를 내렸어요" (`popularity.yml notify`). 누른 사람 문구는 `messages`.
- 정보창 버튼 → 서버 → VOTED 응답(문구·새 점수·오늘 조절함·해제권 수)으로 창 바로 갱신. 옛 모드의 "인기도 상승"(POPULARITY) = 올리기 (문구만 받음). `StatsSocialRequestEvent` 는 이제 친구 추가에만 불림.
- 관리: `/유저관리 <닉> 인기도 보기 | 설정 <값> | 초기화` · `/유저관리 <닉> 일일제한 보기 | 초기화 인기도` (DailyLimits 종류 "인기도": 보기 = 오늘 그 사람이 조절한 상대 목록, 초기화 = 그 사람의 오늘 기록 삭제, 점수는 그대로). `/유저관리 <닉>` 요약 줄에 "인기도".
- 해제권 ItemsAdder 정의 예시 (아이템 그림은 오너가 따로, ID 는 `popularity.yml unlock-item` 에서 변경):
  ```yaml
  info:
    namespace: item
  items:
    popularity_unlock_ticket:
      display_name: "인기도 제한 해제권"
      lore:
        - "오늘 이미 인기도를 올리거나 내린 사람에게"
        - "한 번 더 할 수 있어요 (1개 사용)"
      resource:
        material: PAPER
        generate: false
        model_path: item/popularity_unlock_ticket
  ```

## 28. 상대 일러스트 (2026-10-26, 오너 결정)
- `PortraitProtocol` (버전 1 그대로, op 추가): C_OTHER(2) = 대상 UUID + 클라가 가진 SHA, S_OTHER_META(6) = 대상 UUID·SHA("" = 없음)·전체 바이트·'그대로'(flag), S_OTHER_CHUNK(7) = 대상 UUID·SHA·조각 번호, S_OTHER_BUSY(8). 어느 사람 그림인지 대상 UUID 로 구분. **원본 PNG 그대로** (줄이지 않음). 옛 서버는 C_OTHER 를 무시 → 클라는 15초 뒤 3D 스킨. `PortraitProtocolTest` 추가.
- 서버 `PortraitChannel`: DB 읽기 비동기, 한 사람 요청 간격 250ms·같은 대상 3초, 동시에 보내는 남의 그림 2장(나머지는 32개까지 줄, 넘치면 BUSY → 클라가 잠시 뒤 다시), 조각은 2틱마다 1개, 같은 사람의 새 요청은 옛 요청을 끊음. 공유 DB 면 다른 서버 사람 그림도. 그림 없는 사람은 "없음".
- 클라 `OtherPortraits`: 캐시 `config/magiccodex/portraits/others/<sha>.png` + `index.txt`(uuid→sha), 최근 64장만 유지, 같은 sha 면 다시 안 받음. **처음 받는 동안만 초상 칸에 "불러오는 중"**, 다음부턴 캐시를 먼저 보여 주고 바뀌었으면 새로 받아 바꿈. 디코드·해시·디스크는 IO 스레드, 텍스처 업로드는 클라 스레드 (메모리엔 최근 4장). 없음·실패 → 3D 스킨. 일러스트는 초상 칸 높이에 맞춰 비율 유지·가운데·칸 밖은 잘라냄 (내 일러스트도 같은 방식).
- **배포 주의: ChacaPortrait 플러그인 + chaca-portrait-protocol + MagicCodex 모드 같이.**

## 29. 기숙사 점수 홀로그램 (2026-10-26, 메모 #106)
- 오너: "게시판 없이 단순하게 홀로그램 느낌으로". 관리자가 정한 자리(여러 곳)에 **클라가 그리는** 떠 있는 글씨판 — 블록·엔티티·게시판 없음. 제목 "기숙사 점수" + 순위대로 4줄 (순위 · 기숙사 이름 · 점수, 같은 점수는 같은 순위 1·1·3·4). 1위 줄은 금빛 띠 + 금색 숫자. 기숙사 이름 색 = 학교 창(`SchoolScreen`) 막대 색.
- **방향 (오너 수정)**: 카메라를 따라 도는 빌보드가 아니라 **동·서·남·북 중 한 방향을 90도 단위로 고정**해서 바라본다. 앞면 쪽에서만 그리고, 옆 끝으로 갈수록 흐려지다가 뒤에서는 안 그림 (뒤집힌 글씨 없음).
- 스타일 재사용(새 PNG 없음): 웨이포인트·청크 점유 글씨 규칙(MagicCodex 굵은 글꼴 + 검은 외곽선 8장 + 채움), 책 블록·웨이포인트의 halo 텍스처(`textures/gui/travel/world/halo.png`)로 반투명 남색 판(3장 겹침)·푸른 테두리 빛·제목 아래 금빛 줄·1위 금빛 띠. 위아래로 ±0.04블록 떠다님, 빛이 천천히 숨쉼. 크기: 판 약 2.6×2.0블록, 글자 줄 높이 약 0.17블록.
- 점수가 바뀌면: 숫자는 0.7초 동안 위아래로 겹쳐 바뀌고, 순위가 바뀐 줄은 새 자리로 미끄러짐 (바뀐 숫자 글자만 한 번 새로 구움).
- 벽 뒤: 웨이포인트 글씨와 같은 규칙(깊이 검사 그대로 = 벽에 가려짐). 그리기 거리 `view-distance`(기본 48블록).

**흐름 (서버 렉 없음)**
- 서버 `HouseBoardBridge` (MagicCodexBridge 안 새 기능). `house-board.yml` 의 `servers`(travel.yml server-id, 기본 `school`)에서만 켜짐 — 다른 서버는 채널·이벤트·작업 없음.
- 점수는 `SchoolBridge.data().scores()` (기존 학교 기록 **메모리 캐시**, 공유 DB 면 SchoolBridge 가 원래 2초마다 갱신)만 읽음. **이 기능은 DB 를 치지 않음.**
- 보내기: 채널 등록(접속)·세계 이동·자리 추가/삭제/새로고침 때 + `refresh-seconds`(기본 20초)마다 훑어서 **지난번에 보낸 것과 다른 사람에게만** (세계별로 한 번만 만듦, 해시 비교). 자리 없는 세계에 처음 들어온 사람에겐 안 보냄.
- 새 채널 `magiccodex:house_board` (서버→클라 하나): `u8 VERSION · UTF world · u8 range · 4×i64 점수 · u8 n · n×(f64 x·y·z · u8 facing)`, facing 0 남 · 1 서 · 2 북 · 3 동 (= 마인크래프트 yaw/90). 한 세계 최대 32곳, 전체 64곳.

**명령 (OP)**
- `/컨텐츠관리 기숙사판 추가 <이름> [동|서|남|북]` — 서 있는 곳, 눈높이 + `height-above-eyes`(기본 0.9블록)가 판 가운데. 방향 = 판 앞면이 보는 쪽(그쪽에 선 사람이 읽음). 생략하면 내가 바라보는 방향을 가장 가까운 90도로. 같은 이름이면 옮김.
- `/컨텐츠관리 기숙사판 삭제 <이름>` · `/컨텐츠관리 기숙사판 목록`
- `/서버관리 새로고침 기숙사판` (house-board.yml 다시 읽기). `servers`·`enabled` 바꿔 켜고 끄는 것만 서버 재시작.

**설정** `plugins/MagicCodexBridge/house-board.yml` (처음 켤 때 jar 에서 자동 생성): `enabled`, `servers: [school]`, `view-distance: 48`(8~96), `refresh-seconds: 20`(5~300), `height-above-eyes: 0.9`, `boards:` (명령이 씀: 이름 → world·x·y·z·facing 동/서/남/북). operating-config 대상 아님.

**바꾼 파일**: protocol `HouseBoardProtocol`(새, 순위·방향 규칙 포함); paper `HouseBoardBridge`(새), `MagicCodexBridge`(생성·종료 2줄 + 필드), `resources/house-board.yml`(새), 테스트 `HouseBoardProtocolTest`(새); fabric `HouseBoardClient`(새), `MagicCodexClient`(등록 1줄).

**같이 배포할 것**: MagicCodexBridge jar (학교, 다른 서버에 올려도 꺼져 있음) + **MagicCodex 클라 모드 (+ magic-codex-protocol) 같이**. 옛 모드는 채널을 안 들어서 아무것도 안 받음.

**게임 확인 방법**
1. 학교 서버 시작 로그 "기숙사 점수 홀로그램: 켜짐 · 서버 school · 자리 0곳". 야생에선 "꺼짐".
2. 광장에서 남쪽을 보고 `/컨텐츠관리 기숙사판 추가 광장` → "광장 … 남쪽을 봐요". 남쪽으로 몇 칸 물러서서 돌아보면 머리 위에 남색 빛 판 + "기숙사 점수" + 4줄. 판이 카메라를 따라 돌지 않음.
3. 판 옆으로 돌아가면 흐려지고, 뒤(북쪽)에서는 안 보임. 벽 뒤로 숨으면 가려짐. 48블록 넘게 멀어지면 사라짐.
4. `/기숙사관리 점수 노크세르 추가 5000` → 20초 안에 노크세르 줄이 맨 위로 미끄러지고 금빛 띠·숫자가 바뀜. 두 기숙사 점수를 같게 하면 둘 다 "1위"·금빛.
5. `/컨텐츠관리 기숙사판 추가 정문 동` → 다른 자리에 동쪽을 보는 판. `목록` 에 둘 다, `삭제 정문` → 바로 사라짐.
6. 다른 세계로 갔다 오기, 재접속 → 다시 보임.
## 30. 마법 연습장 훈련 허수아비 개선 (2026-10-26, 메모 #107)
- **모델 (Blockbench, ModelEngine 용)**: `magic-mythic-paper/mythicmobs/blueprints/training_dummy.bbmodel` (모델 id `training_dummy`, 텍스처 128x64 내장, 큐브 36개). 나무 십자 받침+버팀대 / 기둥 / 짚 몸통(밧줄 두 줄) / 가슴 과녁(빨강·크림·금) / 가로대 팔(짚 소매) / 천 자루 머리(단추 눈·꿰맨 입·위 매듭). 히트박스 0.8 x 2.3 (숨김 `hitbox` 그룹, 기존 작물 몹 blueprint 와 같은 형식).
  - 애니: `idle`(3.2초 반복, 살짝 흔들림) · `hit`(0.5초, 뒤로 13° 휘청 → 스프링처럼 -6°·+3° 복귀) · `hit_heavy`(0.65초, 24°로 더 크게). 받침은 안 흔들림(본 `sway` 아래만 움직임).
  - 배치: 운영 서버(연습장이 있는 서버) `plugins/ModelEngine/blueprints/` 에 `training_dummy.bbmodel` 복사 → `/meg reload` → ModelEngine 리소스팩을 ItemsAdder 팩에 병합·배포(기존 blueprint 와 같은 절차).
- **MythicMobs** (운영 overlay 규칙대로, `operating_overlay.py check` 차이 없음):
  - `deployment/operating-config/MythicMobs/Mobs/chacademi_training_dummy.yml` — `CA_TrainingDummy` (HUSK, NoAI, `model{mid=training_dummy;n=false;drive=true} ~onSpawn`)
  - `deployment/operating-config/MythicMobs/Skills/chacademi_training_dummy.yml` — `CA_TrainingDummy_Hit`(`state{s=hit}`) · `CA_TrainingDummy_HitHeavy`(`state{s=hit_heavy}`)
  - 원본: `magic-mythic-paper/mythicmobs/Mobs|Skills/chacademi_training_dummy.yml`. 서버 `plugins/MythicMobs/Mobs`·`Skills` 에 넣고 `/mm reload`.
- **서버 ChAcademiaMagicMythic** `TrainingDummies`: 허수아비 몸을 미몹 `CA_TrainingDummy` 로 세움(미몹이 없으면 예전처럼 바닐라 허스크 + 이름표, 콘솔 경고 1번). 죽지 않음·체력 다시 채움·제자리 복귀·원소/디버프 그대로는 전과 같음. 맞을 때 큰 피해(`heavy-damage` 이상)면 `CA_TrainingDummy_HitHeavy`, 보통은 `CA_TrainingDummy_Hit` 시전(0.25초 안에 또 맞으면 다시 틀지 않음). 서버 텍스트 엔티티(머리 위 글씨)는 없앰.
  - 누적: 연속으로 맞은 피해 합. **마지막 타에서 10초 안 맞으면 0** (`reset-seconds`). 초당 피해 = 누적 / (첫 타~마지막 타, 최소 1초).
  - 숫자 종류: 원소 반응 피해(반응 적용 중 들어온 피해, `Reactions.applying()`) / 출처 없는 지속 피해(독·화상 등) / 큰 피해 / 보통. **서버에 치명타 개념이 없어 "큰 피해"를 치명타 자리에 씀.**
  - 때린 사람 알림 카드: 머리 위 표시로 충분해 **기본 끔** (`notify-card: true` 면 다시 켜짐).
  - config `training-dummy:` 바뀜 — `window-seconds` 없어짐, 새 키 `mythic-mob`(CA_TrainingDummy) · `model-height`(2.35) · `reset-seconds`(10) · `heavy-damage`(20) · `show-range`(32) · `notify-card`(false). 운영 config 에 키 병합.
- **머리 위 표시 = 클라(MagicCodex 모드)가 그림** — 이유: 서버 텍스트 엔티티로 떠오르는 숫자를 만들면 숫자마다 엔티티 소환·매 틱 위치/투명도 갱신 패킷이 필요해 렉 위험. 클라 방식은 맞을 때만 44바이트 패킷 하나.
  - 새 채널 `magiccodex:training_dummy` (`TrainingDummyProtocol` v1: 허수아비 몸 엔티티 id · 표시 기준점 xyz(모델 머리 꼭대기) · 이번 피해 · 종류 · 누적 · 초당 · 초기화 시간). 서버는 **맞을 때만, 32블록 안 + 채널 듣는 플레이어에게만** 보냄.
  - `TrainingDummyClient`: 머리 바로 위 "누적 1,234"(작게 "누적" 글자 + 큰 숫자), 그 아래 작은 "초당 56.7". 10초 안 맞으면 0.5초 흐려지며 사라짐. 그 위에서 맞을 때마다 숫자가 톡 커졌다(0.08초) 1.1초 동안 위로 0.55블록 올라가며 0.55초부터 투명해져 사라짐. 0.35초 안에 연달아 맞으면 위로 한 칸씩(4칸 반복) 쌓고 좌우로 번갈아 흩음. 보통 = 주황, 큰 피해 = 크고 빨강, 원소 반응 = 하늘색, 지속 피해 = 작고 보라. 숫자는 MagicCodex 폰트(잠실 엑스트라볼드)를 코드로 굵은 어두운 테두리 + 위아래 그라데이션 글자판 1장(0~9 , . 누적 초당)으로 한 번만 구워 이어 붙임(새 그림 파일 없음). 월드 공간 빌보드라 멀수록 작아짐, 48블록 밖은 안 그림.
- 바뀐 파일: 서버 `TrainingDummies`(새로 씀), `Reactions`(`applying()` 1줄), `config.yml`(training-dummy), `magic-mythic-paper/build.gradle`(프로토콜 include 1개), 테스트 `TrainingDummyTest`. 프로토콜 `magic-codex-protocol/.../TrainingDummyProtocol`(새). 클라 `TrainingDummyClient`(새), `MagicCodexClient`(등록 1줄), `CodexTypography`(`font()` 접근자 1줄), 테스트 `TrainingDummyClientTest`. overlay 등록 `operating-config-overrides.json`·manifest.
- **같이 배포**: ChAcademiaMagicMythic jar + MagicCodex 모드(+magic-codex-protocol) + MythicMobs yml 2개 + ModelEngine blueprint 1개(+리소스팩). 옛 모드는 채널을 안 들어서 표시만 안 보임(허수아비 기능은 그대로).
- 게임 확인:
  1. `/컨텐츠관리 연습장 허수아비 설치` → "(N개)" 뒤에 "모델 없음 → 허스크" 가 안 붙고 나무·짚 허수아비 모델이 서 있는지, 살짝 흔들리는지(idle). `/컨텐츠관리 연습장 허수아비 목록` 에 "(모델)".
  2. 칼·마법으로 때리면 뒤로 휘청였다 돌아오는지, 20 이상 한 방이면 더 크게(hit_heavy). 히트박스가 몸통 크기에 맞는지(F3+B).
  3. 머리 위 "누적" 숫자가 늘고, 10초 가만두면 사라졌다가 다시 때리면 처음부터 세는지. 숫자가 떠올라 사라지는지, 빠르게 연타(광역·연사 마법)하면 겹치지 않고 쌓이는지, 원소 반응 피해가 하늘색으로 뜨는지.
  4. 33블록 이상 떨어진 사람에겐 안 보이는지, 때린 사람에게 알림 카드가 안 뜨는지.
## 31. 확성기 (2026-10-26, 메모 #102, UI v2 납품 패키지)
- **흐름**: 확성기(ItemsAdder `item:megaphone`, `megaphone.yml item` 에서 변경)를 손에 들고 우클릭 → 서버가 입력 창 열기(OPEN, 토큰) → 클라 입력 창(제목 "확성기"·닫기·입력칸·보내기만) → 보내기(Enter·버튼) = SEND / 닫기(Esc·X) = CANCEL(소모 없음). 서버 검사: 입력 시간(기본 120초)·쿨다운(기본 30초, 서버마다)·글자 수(기본 80자, code point)·금칙어(`megaphone.yml banned-words` + ChacaNPC `filter.yml banned-words`, 띄어쓰기·기호 무시)·확성기가 아직 그 손(주 손이면 같은 칸)에 있는지 → 공지 모양 확인 → **1개 소모** → 이 서버 접속자에게 SHOW + 다른 서버로 전달. 거절이면 알림창 안내, 확성기 그대로.
- **같이 보여 줄 아이템** = 확성기 반대 손(주 손 확성기 → 왼손, 왼손 확성기 → 주 손). 서버가 보낼 때 손에서 직접 읽어 `serializeAsBytes` (1개, 숨은 PDC 제거, 16KB 넘으면 설명 줄 빼고, 그래도 크면 재료만). 클라는 아이템을 보내지 않음. 없으면 공지만.
- **화면 위 공지** (모드 클라): 받은 순서대로 하나씩(대기열 30, 넘치면 오래된 것부터 버림), 표시 6초(`show-seconds`, 보낸 서버 값). 화면이 보일 때(다른 창이 덮지 않을 때) 꺼내 띄운 시각 t0 부터 `remaining = clamp(1-(now-t0)/6초,0,1)` 로 금색 타이머 폭(늘이지 않고 잘라 그림). 끝나면 카드·아이템·힌트·툴팁 함께 숨김. 아이템 있음: 카드 위(16px 틈) 검정 반투명 배경 + 실제 아이템 80px(큰 아이템 그림은 그 크기로 새로 그림 `ExactItemIcons`, 아니면 GUI 아이템 렌더를 그 크기로) + "Ctrl 아이템 보기"(키 이름 = 키 설정 "마우스 포인터"). Ctrl 로 커서를 꺼내 아이템 배경에 올리면 게임 아이템 툴팁(`ItemTooltipRenderer`, 바닐라 대체)이 카드 아래 (960, 282) 에서 아래로, 화면 가장자리 16px 안으로 보정. 모드 없는 접속자: 채팅 한 줄 `[확성기] 닉: 문장`.
- **UI**: `docs/design/megaphone-ui-v2/docs/개발_프롬프트.txt` 그대로. 좌표 1920×1080, min(가로/1920, 세로/1080) 한 배율·가운데(코너·글자 같은 배율), 9-slice 입력 패널·공지 카드 16px / 입력 필드·버튼·아이템 배경 12px, 닫기 24×24, 보내기 normal/hover PNG, 전체 암막·블러 없음(PNG 반투명 그대로), 글자 Pretendard(`body`·`label`·`hud_bold`) #F2F8FF. 닉네임 26 bold·메시지 24 가운데 한 줄 — 넘치면 닉 20·메시지 18 까지 줄이고 그래도 넘치면 말줄임(원문 보존). 글자 y = rect 위쪽 + 0.48×크기(잉크 가운데, 납품 미리보기에서 잰 값). 입력: 한 줄, 80 code point(줄바꿈 제거·넘치면 뒤 자름), 빈 값·공백만은 안 보냄, 보내기 한 번만. 배치는 전부 `MegaphoneLayout` 한 곳 (`MegaphoneLayoutTest` 가 layout.json 값과 비교).
- **자산**: `magic-codex-fabric/src/main/resources/assets/magiccodex/textures/gui/megaphone/megaphone_*.png` (납품 PNG 9개 그대로 복사, 장식 추가 없음). 합성 미리보기(실제 PNG·좌표·Pretendard, 하늘·땅 단색 배경) 입력 / 아이템 공지 / 일반 공지 3장은 Claude 작업 폴더에서 패키지 previews 와 1~2px 안으로 맞춤 확인.
- **프로토콜** `MegaphoneProtocol` (magic-codex-protocol, 채널 `magiccodex:megaphone_request` / `magiccodex:megaphone_response`, 버전 1): S2C OPEN(token, maxChars) · SHOW(id, 보낸 사람, 문장, 표시 ms, 아이템 바이트 0~16384) · CLOSE(token), C2S SEND(token, 문장) · CANCEL(token). 프레임 최대 18KB, 요청 1KB, 남는 바이트·모르는 종류 거부. 테스트 `MegaphoneProtocolTest`.
- **서버 간**: Velocity BungeeCord 호환 채널 `Forward ALL`(#95 `/공지` 와 같은 방식, 하위 채널 `ChacaMegaphone`, 최대 30000바이트 — 받는 서버의 플러그인 메시지 한계 32767 안). 보낸 서버 실행 토큰(origin)으로 자기 메시지 무시, 공지 id 최근 256개로 중복 무시. 프록시 플러그인 변경 없음. **조건**: `velocity.toml` `bungee-plugin-message-channel = true`(기본값), 받는 서버에 접속자가 1명 이상(Velocity 는 접속자 없는 서버에 플러그인 메시지를 못 보냄 — 그 서버는 그 공지를 못 받음, 정상). `cross-server: false` 면 그 서버만.
- **설정** `megaphone.yml`(처음 켤 때 자동 생성, 모든 서버에 같은 파일): item·cooldown-seconds(0~3600)·max-length(10~100)·show-seconds(2~30)·cross-server·prompt-timeout-seconds(10~600)·use-chacanpc-filter·banned-words. 바꾼 뒤 `/서버관리 새로고침 확성기`. 미리보기: `/테스트 확성기 공지`(나에게만, 왼손 아이템) · `/테스트 확성기 창`(입력 창, 소모·쿨다운·기록 없음, 나에게만).
- **운영 기록**: `ChacaApi.audit` 종류 "확성기"(audit.yml `kinds.확성기`) — 보낸 사람·문장·같이 보인 아이템 이름·서버. 서버 로그 `MEGAPHONE 계정 (닉) [아이템] : 문장`.
- **부담**: 매 틱 하는 일 없음. 우클릭·보내기 때만 일함, 아이템 직렬화는 공지 한 번에 한 번. 클라는 공지가 있을 때만 그림.
- **바뀐 파일**: 서버 `MegaphoneBridge`·`MegaphoneRules`(+`MegaphoneRulesTest`)·`megaphone.yml`, `MagicCodexBridge`(등록 1줄)·`AuditLog`·`audit.yml`(종류 1개). 프로토콜 `MegaphoneProtocol`. 클라 `MegaphoneClient`·`MegaphoneScreen`·`MegaphoneLayout`(+`MegaphoneLayoutTest`·`MegaphoneProtocolTest`), `MagicCodexClient`(등록 1줄), `ItemTooltipRenderer`(`renderBelow` — 마우스 대신 위치에 붙여 그리기, 기존 동작 그대로).
- **ItemsAdder 정의 예시** (아이템 그림은 오너가 따로, ID 는 `megaphone.yml item` 에서 변경):
  ```yaml
  info:
    namespace: item
  items:
    megaphone:
      display_name: "확성기"
      lore:
        - "손에 들고 우클릭하면 모든 서버에 한마디를 외쳐요"
        - "반대 손에 든 아이템도 같이 보여 줘요 (1개 사용)"
      resource:
        material: PAPER
        generate: false
        model_path: item/megaphone
  ```
- **같이 배포**: MagicCodexBridge(모든 서버) + magic-codex-protocol + MagicCodex 클라 모드. 모든 서버에 ItemsAdder `item:megaphone`. 모드 없는 클라는 우클릭하면 "차카데미 런처로 접속했을 때 쓸 수 있어요".
- **게임 확인**: ① `/테스트 확성기 창` → 창 모양(암막 없음)·한글 입력·붙여넣기·80자 제한·Esc 취소. ② 확성기 우클릭 → 보내기 → 1개 줄고 위쪽 카드·6초 타이머, 다른 서버 접속자에게도 뜨는지. ③ 왼손에 아이템 들고 보내기 → 카드 위 아이콘·"Ctrl 아이템 보기", Ctrl 누른 채 아이콘에 커서 → 카드 아래 툴팁, 화면 작게(가장자리) 해도 화면 안. ④ 연속 전송(쿨다운 안내)·두 사람이 연달아(대기열 하나씩)·금칙어(거절, 소모 없음)·긴 닉네임·80자 메시지(줄이고 말줄임)·UI 배율 바꿔서 같은 비율.
## 32. 하우징 특수 가구 클릭 (2026-10-26, 메모 #108)
하우징 방은 실제 월드가 아니라 클라 창(`HousingScreen`, 쿼터뷰 직접 렌더). 원래 기숙사 점수판·내 정보 액자만 연결돼 있었고, 나머지를 오너 승인 표대로 **기존 창을 그대로** 열게 했다. 남의 방(구경)은 한 번 클릭, 내 방 꾸미기 중엔 **두 번 클릭**(첫 클릭은 평소처럼 집힘 → 제자리로 돌리고 엶). 이름표에 "클릭해서 보기 / 두 번 눌러 열기".

| 가구 | 방문자 | 주인 |
|---|---|---|
| 빗자루 거치대 `broom_rack` | 주인 장착 빗자루 3D 모델이 가구 위에 걸려 보임, 이름표·클릭(방 알림)에 빗자루 이름 | 같음 (내 장착 빗자루) |
| 아티팩트 진열장 `artifact_cabinet` | 장착 아티팩트 아이콘 최대 8개(4×2) 전시, 클릭 → 아티팩트 도감 **구경 모드**(주인 장착분만·장착/해제 버튼 없음), 닫으면 방으로 돌아옴 | 같음 |
| 큐디치 트로피장 `trophy_case` | 주인 큐디치 티어 아이콘 전시(이름표에 티어/전적 없음), 클릭 → 결투장 로비 전적 탭(주인 닉네임 검색) | 같음 (내 전적) |
| 기숙사 점수판 `house_scoreboard` | 기숙사 점수 창 (기존) | 같음 |
| 내 정보 액자 `profile_frame` | `/스탯창 <주인>` (주인이 같은 서버에 접속 중일 때만 서버가 엶 — 기존) | 내 정보창 |
| 연금 작업대 · 거울 옷장 · 펫 쿠션 · 미끼 상자(3종) | "주인만 쓸 수 있어요" 알림 | `/연금술` · 칭호 창 · 펫 창 · **"미끼 상자는 준비 중이에요"** (미끼 생성·받기 기능/수치 없음 → 오너 결정 대기) |
| 수조 | **안 함**: furniture.yml 에 수조 가구가 없고, 아공간 수조 창은 본인만(마법으로) 여는 창이라 남의 수조를 볼 서버 경로가 없음 | — |

- 데이터 (렉 없음): 방에 들어갈 때 서버가 ROOM 다음에 **SHOWCASE 한 번**(새 종류 7: 주인 UUID·빗자루 id·빗자루 이름·아티팩트 id ≤8). 값은 공용 DB 새 표 `codex_housing_showcase`(자동 생성) — 접속 30초 뒤·주인이 자기 방 열 때·이 서버에 있는 주인 방을 누가 열 때, **바뀌었을 때만** 1줄 upsert (매 틱·주기 작업 없음, 나갈 때는 안 씀: Bridge 가 아티팩트 보유 정보를 먼저 지울 수 있어서). 다른 서버에 있는 주인 = 마지막에 적힌 값. 내 방은 내 클라 값(BroomUiLink·ArtifactClient). 티어는 트로피장을 처음 그릴 때 주인당 한 번 프록시 PROFILE(정보창 StatsRanks 와 같은 요청)을 묻고 `HousingShowcase` 가 답을 가로챔.
- 값 읽기: `Showcases`(housing-paper)가 리플렉션 — ChacademyMotion `equippedBroomId` + `brooms().brooms().get(id).name()`, MagicCodexBridge 새 public `equippedArtifactIds(Player)` (한 줄). 플러그인이 없으면 빈 값.
- 옛 모드는 SHOWCASE 를 모르는 종류로 버림 (호환). 진열장 구경 중엔 방 세션 유지(`HousingClient.suspend`), 다른 창으로 가 버리면 그때 방 닫힘 처리.
- 꾸미기 중 저장 안 한 배치가 있으면 다른 창으로 넘어가는 가구(연금·칭호·펫·정보·점수판·트로피)는 "먼저 저장해 주세요" 로 막음.
- **디자인 납품 대기 (오너 2026-10-26, GPT 담당) — 교체 지점:** 빗자루 거치대 상세 카드 · 아티팩트 진열장 구경 창 · 트로피장 전적 카드 · 수조 구경 창. 좌표·그림 경로는 전부 `HousingShowcaseLayout`(가구 위 전시 크기·위치, 티어 PNG 경로, 납품 PNG 자리 `*_TEXTURE`, 구경 모드 문구)에 모았고, 화면 전환은 `HousingScreen.useFurniture` 의 `교체 지점 1~3` 주석 자리. 가구 위 전시 위치는 가구 그림마다 달라 대략값 → 게임에서 보고 `HousingShowcaseLayout` 숫자만 조정.
- 바뀐 파일: `magic-codex-protocol` HousingProtocol(SHOWCASE) · `housing-paper` Showcases(새)·HousingStore(표·읽기/쓰기)·Rooms(입장 때 전송)·HousingPlugin(등록) · `magic-codex-paper` MagicCodexBridge(`equippedArtifactIds` 한 줄) · `magic-codex-fabric` HousingScreen·HousingClient·HousingShowcase(새)·HousingShowcaseLayout(새)·ArtifactCodexScreen(구경 모드)·KyudichiLobbyScreen(`record(mode,nick)`)·KyudichiQueueClient(답 가로채기 한 줄). 테스트: HousingProtocolTest.showcase, ShowcasesTest.
- **같이 배포:** ChacaHousing 플러그인 + MagicCodexBridge + MagicCodex 모드 + magic-codex-protocol (학교·야생 모두). 프록시·ChacademyMotion 은 그대로.
- 게임 확인: ① 친구 방에서 빗자루 거치대 위 빗자루 모델·이름표 이름, 클릭 알림 ② 진열장 아이콘 → 클릭 → "OO의 아티팩트" 도감(버튼 없음) → 닫으면 방으로 ③ 트로피장 티어 아이콘 → 클릭 → 결투장 전적 탭에 주인 기록 ④ 남의 연금 작업대 클릭 → "주인만 쓸 수 있어요" ⑤ 내 방 꾸미기에서 연금 작업대 두 번 클릭 → 연금술 창, 거울 옷장 → 칭호 창, 펫 쿠션 → 펫 창, 미끼 상자 → "준비 중" ⑥ 주인이 다른 서버일 때도 ①② (마지막 값) ⑦ 서버 로그에 "전시 값" DB 경고 없는지.

## 33. 아공간 배낭: 툴팁 안 뜸 · 잠긴 쪽 배치 (2026-10-26, 오너 게임 제보)
- **툴팁 원인:** `GalaxyBagScreen.render` 가 `drawMouseoverTooltip` 을 부르지 않았다. 바닐라 `HandledScreen#render` 는 툴팁을 안 그리고 각 화면(InventoryScreen 등)이 직접 부르는데, 배낭 화면은 처음(f088c59b)부터 빠져 있었음 (#101 캐시 변경과는 무관). → `super.render` 뒤에 호출 추가. 마우스 칸은 배낭 배치 기준 `isPointWithinBounds` 로 잡히므로 배낭 27칸·핫바·제작 2x2·결과 칸 모두 `HandledItemTooltipMixin` → `ItemTooltipRenderer`(등급 연출 툴팁)로 뜬다. 커서에 아이템을 들고 있으면 바닐라대로 안 뜸. 조합법 카드 툴팁은 칸 툴팁이 없을 때만.
- **잠긴 쪽:** ① 제목 "아공간 배낭"은 한 번만, 테두리 위 선과 첫 줄 링 사이 가운데(y 103 → 84.6, 첫 줄 링과 안 겹침) ② 핫바 9칸·구분선·번호가 잠긴 쪽에서도 그대로 보이고 클릭·숫자키 다 됨 ③ 자물쇠 + "잠긴 공간" + "가방 확장권으로 개방" 묶음을 27칸 영역(핫바 제외, 링 바깥 끝 기준)의 가로·세로 정중앙으로 — 자물쇠는 PNG 알파 그림 범위, 글자는 실제 글꼴 잉크 높이로 계산(`GalaxyBagLayout.lockStack`·`lockTitleY`·`mainArea`). 새 장식 없음, 간격·크기·흐림 판은 예전 그대로.
- 참고: 잠긴 쪽에서 핫바 칸을 Shift+클릭하면 바닐라대로 지금 열린 쪽(보이지 않는 27칸)으로 옮겨진다.
- 바뀐 파일: `magic-codex-fabric` GalaxyBagScreen · GalaxyBagLayout · GalaxyBagLayoutTest(잠긴 쪽 배치 테스트 추가).
- **배포:** MagicCodex 클라 모드만. 서버·플러그인 그대로.
- 게임 확인: ① 배낭 칸·핫바·제작 칸·결과 칸에 마우스 → 등급 툴팁(고급·유일·전설 연출) ② 아이템을 집은 채로는 툴팁 없음 ③ 잠긴 쪽으로 넘기면 제목이 링과 안 겹치고 한 번만, 자물쇠·문구가 27칸 한가운데, 아래 핫바 9칸 그대로 ④ 잠긴 쪽에서 핫바 아이템 클릭·옮기기·숫자키 됨 ⑤ 열린 쪽으로 돌아오면 예전과 같음.

## 부록 A. ItemsAdder 아이템 id 맞추기 (오너 2026-10-26: 아이템은 이미 있음)
오너: 확성기 · 인기도 제한 해제권 · 아티팩트 등급 확정 뽑기권 3종 아이템은 **운영 서버 ItemsAdder 에 이미 있다**. 코드 기본 id 는 추정값이라, 배포 때 GPT 가 운영 ItemsAdder 의 실제 id(`네임스페이스:id`)를 확인해 아래 설정에 적는다 (새 아이템 정의는 만들지 않음).

| 아이템 | 설정 파일 · 키 | 코드 기본값 |
|---|---|---|
| 확성기 | `plugins/MagicCodexBridge/megaphone.yml` 의 아이템 id 키 | `item:megaphone` |
| 인기도 제한 해제권 | `plugins/MagicCodexBridge/popularity.yml` 의 해제권 id 키 | `item:popularity_unlock_ticket` |
| 고급·유일·전설 확정 뽑기권 | `artifacts.yml` `summon.tickets.<rare|unique|legendary>.item` | `item:artifact_ticket_rare` · `_unique` · `_legendary` |
| (참고) 땅문서 | `chunk-claims.yml` item | `item:chunk_deed` |
| (참고) 가방 확장권 | `bag-pages.yml` unlock-item | `item:bag_expansion` |

확인: 각 아이템을 손에 들고 `/iaget` 류 또는 `/ia` 편집기에서 id 를 보고, 설정 바꾼 뒤 `/서버관리 새로고침 …`(각 섹션 참고).
- **추가 (오너 2026-10-26 10:07)**: 잠긴 쪽에서 **"아공간 배낭" 제목을 그리지 않음**(자물쇠·문구만). 잠긴 쪽에서는 **Shift+클릭(빠른 이동)·두 번 클릭 모으기 막음**(보이지 않는 27칸으로 옮겨져 사라진 것처럼 보이던 문제) — 제작 패널 닫을 때 2x2 되돌리기만 예외. `GalaxyBagScreen.onMouseClick` 오버라이드.

## 35. 도서관·교실 책 67권 (2026-10-26, 메모 #105, 오너 최종 검수 대기)
- 바뀐 파일: `deployment/operating-config/MagicCodexBridge/books.yml`(overlay 등록) + 소스 기본값 `magic-codex-paper/src/main/resources/books.yml`. 코드 변경 없음.
- 마법 13 · 마법·대기 17(commands 주석 — MagicDiscovery manual-conditions 에 그 마법이 없어서, 오너 일괄 재기획 때 켬) · 헛걸음 37. 기획표·장소별 블록 연결 명령: [docs/plans/library-books.md](../plans/library-books.md).
- 적용: 서버 `plugins/MagicCodexBridge/books.yml` 교체 → `/서버관리 새로고침 책` → 오너가 책장마다 `/컨텐츠관리 책 연결 <책ID>` (서버별 book-blocks.yml).
## 34. 전투 스펙 + 장신구 강화 (2026-10-26, 오너 승인 제안 적용)
근거·계산: `docs/plans/combat-spec.md`(맨 위 "오너 결정 2026-10-26" 표) · `docs/plans/combat-spec/model.py`. 몹 체력·몹 마법 피해·플레이어→몹 피해는 **그대로**, 바뀌는 건 플레이어 스펙과 **플레이어→플레이어 마법**뿐.

**바뀐 수치**

| 항목 | 전 | 후 | 위치 |
|---|---|---|---|
| 기본 최대 마나 / 초당 회복 | 100 / 5 | **300 / 8** | `MagicCodexBridge/config.yml` `mana` |
| 마법봉 기본 지능 (추가 마나) | 수습 24 (0), 나머지는 운영자 직접 | 수습 20 (50) · 일반 30 (50) · 고급 45 (120) · 유일 50 (135) · 전설 55 (150) | `enhancement.yml` `wand-grades` |
| 마법봉 강화 1회 상승 | 코어 stat-gain 1.5~12 | 성공 1~7번째 +5 · 8~10번째 +2 (지능 중심 코어, 누적 +41). 코어 품질은 성공률·스타캐치만 | `enhancement.yml` `stage-gains` |
| 장신구 등급 (지능 / 체력 / 최대 마나 / 초당 회복) | 정의 없음 | 일반 3/1/30/0.5 · 고급 6/3/60/1.3 · 유일 7/3/80/1.7 · 전설 9/3/100/2.3 | `enhancement.yml` `accessory-grades` |
| 장신구 강화 | 없음 | 시도 5 · 1000원 · 단계 2,2,2,1,1 × (지능 0.5 · 마나 2.5 · 회복 0.15) × 코어 성향 | `enhancement.yml` `accessory` |
| 궁극 마나 비용 | 600~2500 | 쉬움 450 · 연계 600 (오로라·별빛 재현 450) | `mana-spells.yml` · 도감 · `spell-catalog.yml` |
| PvP 아티팩트 피해 보정 | 제한 없음 (여유 시계 +50% 등) | 합 +20% 상한 | `CombatSpec.PVP_ARTIFACT_CAP` |
| 펫 도감 체력 · 몹 수치 | — | **유지** (최대 +20, 몹 그대로) | — |

**마법 방어 (플레이어→플레이어 마법만)** — `MagicMythic/config.yml` `pvp:` (없으면 코드 기본값 같은 값)
- 감소율 = min(45%, (방어력 속성 + 갑옷 4칸 보호(Protection) 레벨 합, 최대 20) × 1.25%). 방어 강도 무시. 저항 효과 단계당 20%, 최대 20% (방어 뒤에 곱함). 예: 철 세트 18.75% · 다이아 + 보호 II~III 37.5% · 네더라이트 + 보호 IV 45%.
- 구현: `MagicProtection` 이 줄인 값을 `target.damage` 로 넣고, HIGHEST 리스너가 그 타격의 바닐라 ARMOR·MAGIC(보호 인첸트)·RESISTANCE 를 0으로 (흡수 하트는 다시 계산). **데이터팩·새 피해 종류 없음.**
- 처형(크로스 애로우 체력 20% 미만): 감소 0 (방어·저항·흡수 넘는 피해). 예전엔 방어구가 줄여 처형이 실패했음.
- 다단 히트: PvP 마법 타격 직전 대상 무적 시간 0 (`pvp.reset-invulnerability`).
- 이중 감소 정리: 화상 폭발(이미 줄어든 받은 피해 × 0.1)·피해 나눠 받기(`Guards` 맹세)는 다시 줄이지 않음 / 플레이어가 건 독은 마법 방어.
- 몹이 쓰는 마법·플레이어가 몹을 맞히는 마법은 예전 그대로(바닐라 방어).

**PvP 피해 공식** — 생성기 `magic-mythic-paper/tools/spells.py` `PVP_K`·`PVP_ULT` → `spell-runtime.yml` 마법마다 `pvp-base`/`pvp-ratio` (피해 있는 118개)
- PvP 피해 = k × (40 + 지능) × 마법별 상대 배율. k = 초급 0.027 · 중급 0.045 · 고급 0.091 · 쉬운 궁극 0.186 · 연계 궁극 0.537. 등급 = `spell_order.py` 가중치 1~3 / 4~6 / 7~8 / 9~10.
- 상대 배율(초급~고급) = 지금 몹 피해(지능 52 기준) ÷ 그 등급 평균 → 마법끼리 차이는 지금 그대로. 궁극 = 목표 %(제안서 §4) ÷ 분류 목표 % ÷ 타격 합.
- 전달: Bridge 가 시전 때 미몹 변수 `pvp_scale` = PvP 피해 ÷ 몹 상대 피해 를 넘기고, MagicMythic `Targets.hurt` 가 대상이 플레이어일 때만 곱함 (같은 시전의 모든 타격·투사체·장판·표식 폭발에 같은 배율). 변수를 못 받은 타격(패시브 추가타, 나중에 도는 일부 예약 작업)은 `pvp.fallback-scale` 0.13.

**궁극 분류**

| 분류 | 마법 (목표 %, 타격 합) |
|---|---|
| 연계 (k 0.537, 마나 600) | 메테오 (130, 1) · 레비아탄 (130, 1.35) · 태양의 심판 (150, 1.8) · 심연의 문 (120, 1) |
| 쉬움 (k 0.186, 마나 450) | 크로스 애로우 (25 + 처형) · 하늘의 눈 (40) · 드래곤 브레스 (45, 5타 × 0.4) · 염제 강림 (60, 4 × 0.5 + 1) · 타이탄의 발걸음 (45, 0.6+0.6+1) · 월식의 눈 (45, 접촉 약 3번 추정) · 별빛 재현 (분류 지시 없음 → 쉬움, 자체 피해 0) |
| 지원형 (마나 450) | 오로라 (피해 10%, 회복량은 그대로) |

**장신구 강화** (마법봉과 같은 흐름)
- 반지·귀걸이·목걸이를 **주 손에 들고** `/강화` → 같은 강화 창·마력코어·스타캐치·실패 시 횟수 소모. 아티팩트 칸·장착 중인 장신구는 안 됨, 강화 중엔 장비 바꾸기 막음.
- 오르는 값: 지능(`equipment_bonus_0`) · 최대 마나(`_2`) · 초당 마나 회복(`_3`). 체력·가속은 안 오름. 코어 성향: 지능 중심 → 지능 / 마나 중심 → 최대 마나 / 가속 중심 → 초당 회복. 5번 모두 성공(지능 중심) = 지능 +4 · 마나 +20 · 회복 +1.2.
- 창: 세 번째 줄이 "초당 마나 회복"(마나 아이콘), 코어 툴팁 "마나 회복 중심/보조". 프로토콜 응답 버전 3(kind 1)은 장신구일 때만 — 마법봉은 예전 버전 2 그대로라 옛 클라도 마법봉 강화는 됨.
- 원본 수치 `accessory_base_*` 를 등록 때 적음. 이미 강화한 장신구는 `장비 등급등록` 으로 다시 못 정함.
- 새 명령: `/아이템관리 마법봉 주기 <수습|일반|고급|유일|전설> [닉네임]` · `/아이템관리 장비 등급등록 <반지|귀걸이|목걸이> <일반|고급|유일|전설>` (`docs/ADMIN_COMMANDS_KO.md` 반영).
- **오너: 운영 서버 jar 에 저장소에 없는 장신구 강화가 있으면 GPT 가 오너에게 알려 주고 이것과 합칠 것.** (저장소에는 이번 것이 처음)

**바꾼 파일**
- `magic-codex-protocol`: EnhancementProtocol(kind·버전 3) · CoreAffinity(accessoryLabel)
- `magic-codex-paper`: EnhancementBridge · EnhancementRules · EquipmentBridge · AdminCommandBridge · MagicCodexBridge · SpellRules(pvp-base/ratio) · SpellRuntime(pvp_scale) · ArtifactEffects · CombatSpec(새) / 설정 config.yml · enhancement.yml · mana-spells.yml
- `magic-codex-fabric`: EnhancementScreen / 도감 default-spells 궁극 12개 + known-hashes.tsv
- `magic-mythic-paper`: MagicArmor(새) · MagicProtection · Targets · HitMech · StatusEffects · Guards · Marks · Mech · ChannelMech · SpellTrace · MagicMythicPlugin / config.yml `pvp` / 생성기 tools/spells.py · nova/ult.py · arcana_spells.py(월식의 눈 마나 값만, 생성기는 안 돌림) / server-config/spell-runtime.yml
- `portable-vfx-runtime`: spell-catalog.yml (궁극 마나 표시)
- `deployment/operating-config`: MagicCodexBridge/spell-runtime.yml · mana-spells.yml · PortableVFX/spell-catalog.yml · manifest (`operating_overlay.py check` 차이 없음)
- 문서: docs/plans/combat-spec.md · combat-spec/model.py · ADMIN_COMMANDS_KO.md

**같이 배포**
- 플러그인: **MagicCodexBridge + ChAcademiaMagicMythic** (학교·야생·튜토리얼 모든 서버, 같은 빌드끼리 — `pvp_scale` 변수를 주고받음). PortableVFX 는 jar 그대로, `spell-catalog.yml` 만.
- 클라 모드: **MagicCodex** (장신구 강화 창). 마법봉 강화만 쓸 거면 옛 클라도 됨.
- 운영 설정: `deployment/operating-config/MagicCodexBridge/{spell-runtime,mana-spells}.yml` → `plugins/MagicCodexBridge/`, `PortableVFX/spell-catalog.yml` → `plugins/PortableVFX/`.
- **서버에 이미 있는 설정 파일은 자동으로 안 바뀜** (saveResource 는 없을 때만): `plugins/MagicCodexBridge/config.yml` 의 `mana.default-maximum: 300`·`default-regeneration: 8`, `plugins/MagicCodexBridge/enhancement.yml` 은 저장소 파일로 교체(새 `stage-gains`·`wand-grades`·`accessory`·`accessory-grades` — 없으면 장신구 강화 꺼짐·예전 코어 상승량), `plugins/ChAcademiaMagicMythic/config.yml` 에 `pvp:` 블록(없어도 코드 기본값이 같음).
- 이미 접속했던 계정은 최대 마나·회복이 저장돼 있어 300/8 이 안 됨 → `/유저관리 <닉> 스탯 최대마나 설정 300` · `스탯 마나회복 설정 8`.
- MythicMobs 스킬 파일(`chacademi_spells.yml`)·데이터팩: **변경 없음**.
- 렉: 타격마다 속성·갑옷 4칸 인첸트 읽기와 곱셈 몇 번뿐, 새 주기 작업·DB 없음.

**게임 확인**
1. 허수아비: 몹 상대 피해가 예전과 같은지 (같은 마법·같은 지능으로 전후 숫자 비교).
2. 두 계정 결투: 같은 마법봉·장신구로 초급 몇 발에 쓰러지는지(적당 기준 약 15발), 방어구 바꿔 감소율(철 ~19% · 다이아+보호 37.5% · 네더+보호 IV 45%) 확인.
3. 크로스 애로우로 체력 20% 미만 상대 처형 (방어구 입어도 됨).
4. 드래곤 브레스 5타가 다 들어가는지 (무적 시간).
5. 화상 → 끝날 때 폭발, 독 틱 피해가 방어구 때문에 거의 0이 되지 않는지.
6. `/아이템관리 마법봉 주기 고급` → 지능 45 · 추가 마나 120, `/강화` 성공 1~7번째 지능 +5, 8번째부터 +2.
7. 반지에 `/아이템관리 장비 등급등록 반지 고급` → 손에 들고 `/강화` → 창 세 번째 줄 "초당 마나 회복", 성공 뒤 장착 → 정보창 지능·최대 마나·회복 반영. 장착한 채로는 강화 안 되는지.
8. 새 계정 최대 마나 300 · 회복 8, 궁극 마나 450/600.
9. 결투 중 여유 시계 등 아티팩트로 PvP 피해가 +20% 넘게 안 오르는지.

### §34 추가 — 강화 상승량 "섞기" (오너 2026-10-26 10:30)
- 성공 1회 상승량 = 단계값(1~7번째 5, 8~10번째 2; 장신구 2,2,2,1,1) × **코어 품질 배율(노드 수)** × gain × 코어 성향 배율. 코어 품질은 성공 확률·스타캐치 보너스에도 그대로 영향.
- 배율 `enhancement.yml` `core-quality-multiplier`: 1노드 0.9 · 2노드 0.95 · 3노드 1.0 → 10노드 1.15 (직선). 코드 기본값 같음 (`EnhancementRules.defaultQuality`).
- 재계산(model.py, 적당 = 4노드쯤 1.04, 한계 = 10노드 1.15): 적당 vs 한계 **1.90배** (10노드 1.3 이면 2.04배, 품질 무관이면 1.78배). 오너 검수 때 배율 조정.
- 서버 `enhancement.yml` 교체 필요 (jar 가 안 덮음).

### §19 추가 — NPC 통계 (오너 2026-10-26 10:56: 제일 많이·적게 대화한 NPC, 선물 1위 — NPC 쪽·유저 쪽 둘 다)
- 새 키 (플레이어별 누적, `codex_player_stats`): `대화시작:<npcId>` (ChacaNPC 대화창을 연 횟수) · `대화:<npcId>` (주고받은 말 수, AI 답 1번 = 1) · `선물:<npcId>` (선물 성공 수, 기존 `선물` 합계는 그대로). 관리자 테스트 대화는 안 셈.
- 연결: ChacaNPC `DialogueService.open`·`addTurns` → `MagicCodexLink.stat` → Bridge `NpcSocialFacade.stat` (API_VERSION 그대로, 옛 Bridge 면 조용히 무시) → `AuditStats.add`. 선물은 Bridge `NpcSocialService.auditAffinity`.
- 같이 배포: ChacaNPC jar + MagicCodexBridge jar.
- 끝날 때 정리 SQL (MariaDB, NPC id → 이름은 ChacaNPC npcs/*.yml):
```sql
-- NPC 쪽: 가장 많이/적게 대화한 NPC (말 수 합계 · 대화한 사람 수). 0번인 NPC 는 행이 없으니 NPC 목록과 비교.
SELECT SUBSTRING(stat_key, 4) AS npc, SUM(count) AS lines, COUNT(*) AS players
  FROM codex_player_stats WHERE stat_key LIKE '대화:%' GROUP BY stat_key ORDER BY lines DESC;   -- ASC 면 가장 적게
-- NPC 쪽: 선물을 가장 많이 받은 NPC
SELECT SUBSTRING(stat_key, 4) AS npc, SUM(count) AS gifts FROM codex_player_stats WHERE stat_key LIKE '선물:%' GROUP BY stat_key ORDER BY gifts DESC;
-- 유저 쪽: NPC 와 가장 많이 대화한 유저 / 선물을 가장 많이 건넨 유저
SELECT player, SUM(count) AS lines FROM codex_player_stats WHERE stat_key LIKE '대화:%' GROUP BY player ORDER BY lines DESC LIMIT 20;
SELECT player, count AS gifts FROM codex_player_stats WHERE stat_key = '선물' ORDER BY gifts DESC LIMIT 20;
-- 유저별 가장 많이 대화한 NPC (단짝 NPC)
SELECT s.player, SUBSTRING(s.stat_key, 4) AS npc, s.count FROM codex_player_stats s
  WHERE s.stat_key LIKE '대화:%' AND s.count = (SELECT MAX(t.count) FROM codex_player_stats t WHERE t.player = s.player AND t.stat_key LIKE '대화:%');
-- 특정 NPC 의 단골 1위 (예: ella)
SELECT player, count FROM codex_player_stats WHERE stat_key = '대화:ella' ORDER BY count DESC LIMIT 5;
```
## 36. 뽑기 상자 (오너 이름: API 상자) · 운영 GUI · 공통 지급 함수 (2026-10-26, 디자인 납품 `docs/design/loot-box-v1`)
오너: 모든 뽑기권 연출을 이 상자로. 구현은 `docs/design/loot-box-v1/docs/Claude_개발프롬프트.txt` 1~6 그대로.

**쓰는 법 (유저)**
- 상자의 **열쇠 아이템을 손에 들고 우클릭 = 1개 열기**, **Shift+우클릭 = 10개 열기**.
- 10개 열기인데 열쇠가 10개 미만이면 **거절**(열쇠 소모 없음, "10개 열기는 열쇠가 10개 필요해요 (지금 N개)") — Claude 판단: 승인 레퍼런스가 1개·10개(5×2) 두 화면뿐이라 가진 만큼(예: 7개) 여는 화면이 없음. 열쇠는 손에 든 칸부터, 모자라면 핫바·잠금 해제된 지금 쪽 27칸의 같은 열쇠에서 칸 번호 순으로 뺌.
- 보조 손의 열쇠는 쓰지 않음(설치·사용만 막음).

**명령 (`/상자관리`, OP 전용 대단원 새로 추가 — `ChacaApi.상자관리`)**
| 명령 | 하는 일 |
|---|---|
| `/상자관리 생성 <이름>` | **손에 든 아이템을 열쇠로** 새 상자. 이름은 띄어쓰기 없이 1~24자(명령에서 이 이름으로 부름). 열쇠 판정 ① ItemsAdder 아이템이면 **ItemsAdder id** ② 모델 데이터(CustomModelData)가 있으면 **재질+모델 데이터**(이름·설명이 달라도 같은 열쇠) ③ 둘 다 아니면 **이름·설명까지 정확히 같은 아이템**. 손의 아이템에 PDC 표시를 붙이지 않음 → 이미 나눠 준 같은 아이템도 그대로 열쇠. 이미 다른 상자 열쇠면 거절. |
| `/상자관리 관리 [이름]` | 운영자 관리 창 (모드 필요, 아래). |
| `/상자관리 목록` | 상자·보상 수·열쇠·꺼짐/설정 오류. |
| `/상자관리 삭제 <이름> 확인` | 상자 지우기 (나눠 준 열쇠는 더 이상 안 열림). |
| `/상자관리 확률 <이름>` | 채팅 확률 표 (등급별 %·보상별 %·가중치). |
| `/상자관리 테스트 <이름> [1\|10] [지급]` | 관리자 미리보기: **열쇠 소모·지급 없이 연출만**(결과 창 "관리자 미리보기 · 소모·지급 없음"). 끝에 `지급` = 열쇠 소모 없이 실제 지급(영수증·우편까지 일반 개봉과 같은 경로, 운영 기록 "관리자 테스트"). |
| `/상자관리 새로고침` | loot-boxes.yml 다시 읽기 (읽기는 IO 스레드). `/서버관리 새로고침 뽑기상자` 도 됨. 형식 오류면 이전 설정 유지. |
| `/상자관리 대기` | 우편·정리가 남은 개봉 목록 (우편 DB 장애 때만 생김). |

**운영자 관리 창** (`LootBoxAdminScreen` — 상점 물품 관리 창 ShopAdminScreen 의 판·버튼·인벤토리 고르기 창 재사용, 바닐라 상자 GUI 아님)
- 위: ‹ › 로 상자 고르기 · 새로고침 · 「인벤토리에서 보상 추가」. 상자 줄: 열쇠 아이콘·열쇠 설명, 가중치 합·등급별 확률(%).
- 왼쪽: 보상 목록(12개씩, 이전/다음) — 아이콘(마우스 = 기존 아이템 툴팁)·이름·등급(등급색)·×개수·가중치·**확률 % 자동**.
- 오른쪽: 선택 보상 수정(등급 4버튼·개수 1~64·가중치 1~1000000, 확률 미리 계산) → 「수정 확인」·「보상 삭제…」. 새 보상: 내 인벤토리 칸 고르기 → 등급·개수·가중치 → 「추가 확인」. 상자: 이름 변경 · 열쇠 다시 지정(인벤토리 칸 고르기) · 상자 삭제…. 모든 변경은 확인 창 → 서버가 yml 저장. 다른 곳에서 먼저 바뀌었으면(revision) 거절 → 새로고침.
- 보상 등록 규칙: ItemsAdder 아이템이 ItemsAdder 원본과 같으면 `itemsadder: id`, 아무 설정 없는 바닐라면 `vanilla: minecraft:id`, 그 밖(이름·인챈트·설명이 붙은 것)은 **원본 그대로 base64**(`item:`). 아이템 바이트는 서버→클라로만, 등록은 "인벤토리 몇 번 칸"만 보내고 서버가 실제 칸을 다시 읽음(상점 관리와 같음).
- `enabled`(꺼짐)·연출 속도는 창에 없음 → yml 에서.

**yml (`plugins/MagicCodexBridge/loot-boxes.yml`, 학교·야생 같은 파일)** — 사람이 읽고 손으로 고칠 수 있게. 창에서 저장하면 머리 주석 외 주석은 지워짐.
```yaml
boxes:
  api:                         # 상자 id (자동: 이름의 영문·숫자, 한글뿐이면 box, box_2 …)
    name: "API상자"
    enabled: true
    key:
      itemsadder: "item:api_box_key"     # 또는 material: PAPER + model-data: 10001, 또는 item: "<base64>"
    animation:
      speed: 100                         # 연출 속도 % (50~200)
    rewards:
      - itemsadder: "item:mana_potion"
        amount: 1
        grade: 고급                      # 일반 / 고급 / 유일 / 전설
        weight: 30                       # 확률 = weight ÷ 모든 weight 합
        label: "마나 물약"                # (선택) 읽기용 메모
      - vanilla: "minecraft:diamond"
        amount: 3
        grade: 일반
        weight: 70
```
위 보상·확률은 **형식 예시일 뿐**. 저장소·overlay 의 `loot-boxes.yml` 에는 **꺼진 `example` 상자 하나만**(열쇠·보상 비움) — 확률·보상·비용은 오너가 정함. 설정 오류가 있는 상자는 열리지 않고 `/상자관리 목록`·콘솔에 이유.

**연출 (클라 `LootBoxScreen`)** — 레퍼런스 `reference-corrected.png` 그대로
- 게임 화면 위에 바로(새 배경·장식·암막 없음). 중앙 닫힌 상자 등장(0.42초, 살짝 커지며) → 작게 들썩임 3번(위치 ±7·회전 ±2.6°·살짝 뜀, 0.96초) → **대표 등급의 열린 상자 PNG 로 전환**(0.11초 겹쳐 바꿈, 살짝 커졌다 돌아옴; 빛은 PNG 그대로) → 약 1초 뒤 결과. 클릭하면 바로 결과.
- 상자 PNG 5개(1254×1254) 원본 캔버스 그대로(자르기·좌우 반전 없음), **바닥 기준점 (698,1134)** 을 화면 고정점으로, 회전도 그 점이 축. 파괴·화염 없음.
- 결과: 1개 = 가운데 한 칸, 10개 = **5열×2행**. 패널 = 아티팩트 소환 창 패널(`artifact/tooltip.png` 9-slice), 칸 = 아티팩트 소환 결과 등급 칸(`summon/frame_<등급>.png` + `_fill`, **각 칸 실제 등급색**: 일반 은백·고급 청록·유일 보라·전설 금색), 확인 = 아티팩트 버튼(`artifact/button.png`). 칸 아이콘은 고해상도 아이콘을 그 크기로 새로 그림(`ExactItemIcons`, 가방 창과 같은 규칙).
- 칸에 마우스 = **기존 아이템 툴팁(`ItemTooltipRenderer`)**, 내용은 서버가 지급 확정한 ItemStack 그대로(우편·상점 미리보기와 같은 `coreAppearance` 표시 규칙만). 10칸 각각 hover.
- **확인 버튼 = 닫기만.** ESC 도 닫기. 연출 끝 알림(DONE)은 보내지만 서버는 아무것도 하지 않음(지급은 연출 전에 끝남).
- **10개 대표 연출 등급 = 결과 중 가장 높은 등급** — 기존 정책이 없어 **Claude 판단, 오너 확인 필요** (`LootBoxRules.representative` 한 곳).
- 모드 없는 클라: 연출 없이 같은 지급 + 채팅 결과(`── API상자 10개 결과 (전설) ──`, 줄마다 [등급] 이름 ×개수).

**지급 규칙 (개발프롬프트 3·5)** — 개봉 한 번 = 영수증 하나 (`LootBoxJournal`: 아티팩트 소환 영수증·낚시 우편 대기열과 같은 tmp→fsync→ATOMIC_MOVE, `plugins/MagicCodexBridge/lootbox-receipts/`)
1. 메인 스레드: 상자·상태 확인 → **서버가 추첨 확정**(가중치) → 열쇠 차감 계획 + 보상 넣을 칸 계산(공통 지급 함수, 아래) → 남는 것 계산.
2. IO 스레드: 영수증 저장(결과 ItemStack·등급·우편으로 갈 것).
3. 메인 같은 틱: 인벤토리가 계산 때와 그대로인지(36칸 바이트 비교)·칸 판정이 그대로인지 확인 → **열쇠 차감 + 보상 넣기 + 영수증 표시(PDC `lootbox_<서버8자리>_<번호>`) + saveData** 한 번에. 바뀌었으면 아무것도 바꾸지 않고 영수증만 지움("인벤토리가 바뀌어 열지 않았어요", 열쇠 그대로 — 결과는 아직 아무에게도 안 보냈으므로 다시 굴리는 이득 없음).
4. 그 다음에야 연출/채팅 결과. 남은 것만 우편(`MailService.sendSystem`, source `lootbox:<번호>:<쪽>` — 우편 source_key UNIQUE 라 다시 보내도 한 통, 우편 한 통 9첨부·첨부 하나 min(99, 최대 스택)). 일부만 들어갔으면 **남은 양만** 우편(전체 재발송 없음).
5. 우편 저장 확인 → 영수증 파일 삭제 → PDC 표시 삭제. 우편 실패: 1분 뒤 3번 재시도, 그래도 실패면 영수증 유지 → 재접속·재시작 때 다시.
- **복구**: 영수증 파일이 남아 있으면 플레이어 데이터의 영수증 표시로 판정 — 있음 = 지급 끝남 → 남은 우편만 다시(재추첨·재지급 없음) / 없음 = 3 전에 멈춤(열쇠·인벤토리 그대로) → 영수증 버림. 오프라인이면 시작 때 플레이어 파일(.dat)에서 표시 확인(우편·상점과 같은 `DeliveryReceipt`). 영수증 표시 이름에 서버 8자리(`mailbox-origin.txt`)를 넣어 학교↔야생 이동 때 다른 서버가 지우지 않음.
- **연타·중복**: 개봉 중(busy) 요청 무시 + 1.2초 쿨다운. 우클릭은 서버 이벤트라 클라 재전송 없음. 결과 창을 닫거나 다시 열어도 서버는 아무것도 안 함. 연출 중 게임 종료 = 이미 지급 끝남(재접속하면 인벤토리·우편에 있음).
- 실패를 숨기지 않음: 영수증 저장 실패 = 열지 않음(알림), saveData 예외 = 콘솔 경고(영수증 표시가 같이 메모리에 있어 다음 저장 때 함께 저장), 우편 실패 = 알림 "보상은 안전하게 보관 중".

**공통 지급 함수 `RewardDelivery` (개발프롬프트 4)** — "인벤토리 → 넘치면 우편" 칸 판정·배치 하나
- **넣는 칸**: 핫바 0~8 + **지금 쪽 27칸(9~35)은 서버 실제 상태로 그 쪽이 잠금 해제(active ≤ unlocked)이고 배낭 동기화 READY 일 때만**(`BagPagesBridge.mainSlotsUsable`, 메모리만 읽음). 쪽 넘기는 중(넘김이 끝나면 27칸을 덮어씀)·동기화 중·재조정 필요·관리자가 해금 수를 줄여 잠긴 쪽을 보고 있으면 27칸은 **한 칸도 안 씀**(합치기도 안 함). 아공간 배낭이 없는 서버(HuskSync 없음)는 바닐라 인벤토리 그대로.
- **지금 보이지 않는 잠금 해제된 다른 쪽(비활성 쪽)에는 넣지 않음 — 이유**: 비활성 쪽은 공유 DB 줄(lease/CAS·WAL)이라 바꾸려면 쪽 넘기기와 같은 전환(HuskSync 잠금·WAL·CAS·플레이어 표시·saveData)이 필요하고, CAS 성공 뒤 플레이어 저장 전에 서버가 멈추면 각 지급 경로의 영수증(PDC)과 묶을 방법이 없어 **자동 재지급(복제)** 이나 수동 재조정이 생김. 복제 방지를 약화시키지 않기 위해 비활성 쪽은 건드리지 않고 넘친 것은 우편 → 유저가 그 쪽으로 넘긴 뒤 우편함에서 받으면 그 쪽에 들어감. `BagHuskCoordinator` 는 바꾸지 않음.
- **배치 순서**: 기존 우편 수령(`MailboxBridge.insert`)과 같음 — 같은 아이템 칸 먼저 합치고(한 칸 min(64, 최대 스택)) 남으면 빈 칸, 칸 번호 순. 순수 계산 `RewardPlan` (JUnit `RewardPlanTest`).
- `give(...)`: 넣을 만큼 넣고 남은 것만 우편 — 우편 대기 파일(`reward-mail-outbox/`, 낚시 FishMailOutbox 형식) 먼저 → 같은 틱 인벤토리 + 영수증 + saveData → 우편 저장 확인 뒤 정리, 재시작·재접속 복구(낚시와 같은 규칙). 오프라인이면 전부 우편(`reward_offline_`, 영수증 없이 항상 보냄).

**기존 지급 경로 점검표** (저장소 전체 `addItem`·`setStorageContents`·`sendSystem`·`MailService` 검색)
| 경로 | 파일 · 함수 | 전 | 지금 |
|---|---|---|---|
| 우편 수령 | MailboxBridge.claim | 36칸 전부(넘기는 중·잠긴 쪽 27칸 포함) | **바꿈**: 공통 칸 판정(`RewardDelivery.insertWhole`), 첨부 통째로 |
| 낚시 보상·보물상자·수조 꺼내기 | FishingBridge.deliver (FishTankBridge 도 사용) | 바닐라 addItem → 남으면 우편 대기열 | **바꿈**: 공통 칸 판정·배치, 우편 대기열은 그대로 |
| 퀘스트(의뢰) 보상 | QuestBridge.plan / pay | 36칸 계산 후 setStorageContents | **바꿈**: 27칸을 못 쓰는 순간이면 지급하지 않고 "보상 공간 확인"으로 거절(잠시 뒤 다시) — 제출 차감도 같은 27칸이라 부분 판정 대신 거절 |
| 연금술 결과 | AlchemyBridge (fitsAfterTaking) | 36칸 | **바꿈**: 위와 같이 거절(재료·경험치 차감 없음). 우편 없음은 오너 결정 그대로 |
| 인기도 제한 해제권 반환 | PopularityBridge.giveTicketBack | 인벤 → 넘치면 발밑, 오프라인 우편 | **바꿈**: `RewardDelivery.give` (넘치면 우편) |
| 귀환권 반환 | ReturnTicketBridge.refund | 인벤 → 발밑, 오프라인 우편 | **바꿈**: `give` |
| 땅문서 반환·청크 판매(땅문서 돌려받기) | ChunkClaimBridge.give | 인벤 → 발밑, 오프라인 우편 | **바꿈**: `give` |
| 가방 확장권 반환 | BagPagesBridge.use → giveBack | 인벤 → 발밑 (오프라인 mailBack 은 그대로) | **바꿈**: `give` |
| 관리자 아이템 주기(마법봉 등) | AdminCommandBridge.deliver | 빈칸 개수만 보고 addItem | **바꿈**: 공통 칸 판정, 다 들어갈 때만(예전처럼 우편 없음) |
| 뽑기 상자 | LootBoxBridge (새) | — | 공통 칸 판정 + 영수증 + 남은 것만 우편 |
| 상점 구매·교환 상품 | ShopBridge.deliver | 항상 우편 | 그대로 (인벤토리 안 씀 — 인벤 가득 경로 아님) |
| 마일리지 상점 | MileageBridge.deliver | 항상 우편 | 그대로 |
| 아티팩트 조각·관리자 재발송 | ArtifactOwnershipService.deliver / redeliver | 항상 우편 | 그대로 |
| 상점 판매·교환 비용 회수 | ShopBridge.sell / exchange | 인벤토리에서 빼기(지급 아님) | 그대로 |
| NPC 선물 | NpcSocialService | 선물 1개 빼기(지급 아님), 보상은 호감도·퀘스트 | 그대로 (아이템 보상은 퀘스트 경로) |
| 책·수업 | BookBridge · LessonBridge | 아이템 지급 없음(마법·신호) | 해당 없음 |
| 마법 발견 칭호 보상 | magic-discovery-paper TitleRewards.claim | 빈칸 1개 확인 후 지급, 우편 없음 | 그대로 — 따로 빌드되는 플러그인이라 이번엔 미적용(ChacaLink 에 지급 함수 없음). 빈칸이 지금 쪽 27칸뿐이고 쪽 넘기는 순간과 겹치면 위험 → 다음 패치 후보 |
| 마법 산출물(물 떠오기·비료·물약 등) | LifeSpells · SpellUtility · ArtifactActions · ArtifactLife · magic-mythic-paper *Mech · creature-spawns CropAwakening | addItem → 넘치면 발밑 | 그대로 (우편 경로 아님) — 같은 넘김 순간 위험은 남음, 기록만 |
| 초상화 재생성권 | chaca-portrait RerollService | 사용권 빼기/되돌리기 | 그대로 (지급 아님) |
- 테스트: `RewardPlanTest`(인벤 여유 / 핫바 가득+지금 쪽 여유 / 잠긴·넘기는 중 27칸 한 칸도 안 씀 / 양쪽 가득 / 일부만 / 스택 일부 병합 후 남은 양만 / 수량 보존 / 우편 분할 / 열쇠 차감 계획), `LootBoxJournalTest`(재시작 뒤 같은 결과·같은 우편분, 지우면 끝, 깨진 파일 자동 처리 안 함), `LootBoxRulesTest`(가중치 추첨·대표 등급·% 표기·id), `LootBoxProtocolTest`. Gradle 은 못 돌림 — Claude 작업 폴더의 JUnit 대체 실행기로 이 4개(23개 검사) 통과 확인. 서버·클라 코드는 문법 검사만(Paper·Fabric 타입 검사 못 함) → **GPT 빌드 때 컴파일 확인 필요**. 기존 경로 회귀는 게임 확인 항목으로.

**프로토콜 `LootBoxProtocol`** (magic-codex-protocol)
- `magiccodex:lootbox_play` S2C: 개봉 번호·상자 id·이름·대표 등급·결과(등급, 실제 ItemStack 바이트 ≤32KB, 이름)×1~10·안내·속도%. 상한 384KB(결과 원본을 줄이지 않음).
- `magiccodex:lootbox_done` C2S: 개봉 번호 (연출 끝, 지급과 무관 — 서버는 형식만 보고 버림).
- `magiccodex:lootbox_admin_request` C2S / `magiccodex:lootbox_admin_response` S2C: 관리 창(LIST·ADD·EDIT·DELETE·RENAME·REKEY·DELETE_BOX·CLOSE, 세션 토큰·순번·revision, 보상 12개씩, 미리보기 ≤3KB, 60KB 상한).

**운영 기록**: `ChacaApi.audit` 종류 **"뽑기상자"**(띄어쓰기 없음 — 기록 검색 명령의 종류 칸이 한 단어, `audit.yml kinds.뽑기상자`) — 상자·이름·횟수·대표 등급·결과 전부·우편 첨부 수·개봉 번호·열쇠 소모 여부. 통계 `ChacaApi.stat`: `뽑기상자개봉`(횟수), `뽑기상자:<상자id>`, `뽑기상자유일`·`뽑기상자전설`. 관리 변경은 "관리자" 종류. 서버 로그 `LOOTBOX OPEN …` / `LOOTBOX MAIL …`.

**부담**: 매 틱 작업 없음. 우클릭 때 열쇠 재질 집합으로 먼저 거름(열쇠 재질이 아니면 바로 끝). 개봉 한 번 = 추첨·36칸 계산·영수증 파일 1개(IO 스레드)·saveData 1번(우편·상점과 같음). yml 저장·읽기는 IO 스레드.

**바뀐 파일**
- 서버(새): `LootBoxBridge` · `LootBoxStore` · `LootBoxJournal` · `LootBoxRules` · `RewardDelivery` · `RewardPlan` · `loot-boxes.yml` / 테스트 `RewardPlanTest` · `LootBoxRulesTest` · `LootBoxJournalTest` · `LootBoxProtocolTest`.
- 서버(고침): `MagicCodexBridge`(등록) · `ChacaApi`·`CommandHub`(대단원 `상자관리`) · `AuditLog`·`audit.yml`(종류) · `BagPagesBridge`(`mainSlotsUsable`, 확장권 반환) · `MailboxBridge` · `FishingBridge` · `QuestBridge` · `AlchemyBridge` · `PopularityBridge` · `ReturnTicketBridge` · `ChunkClaimBridge` · `AdminCommandBridge`.
- 프로토콜(새): `LootBoxProtocol`.
- 클라(새): `LootBoxClient` · `LootBoxScreen` · `LootBoxAdminScreen` · `textures/gui/lootbox/*.png`(납품 PNG 5개 그대로 복사). 클라(고침): `MagicCodexClient`(등록) · `SocialScreen`(관리 창 판 모양 목록) · `ArtifactSummonScreen`(`nine`·`Box` 를 패키지 공개, 동작 그대로).
- 운영 설정: `deployment/operating-config/MagicCodexBridge/loot-boxes.yml`(꺼진 예시 상자 1개) + overrides·manifest (`operating_overlay.py check` 차이 없음).

**같이 배포**
- **MagicCodexBridge + magic-codex-protocol + MagicCodex 클라 모드** 같은 빌드. 모든 서버(학교·야생·튜토리얼 등)에 Bridge. `plugins/MagicCodexBridge/loot-boxes.yml` 은 처음 켤 때 자동 생성(꺼진 예시) — 운영 overlay 파일을 학교·야생에 같게 두기.
- 서버에 이미 있는 `audit.yml` 은 자동으로 안 바뀜 → `kinds:` 에 `뽑기상자: true` 한 줄 추가 권장(빠져 있어도 코드 기본값으로 켜짐).
- 열쇠(뽑기권)는 운영 ItemsAdder 에 이미 있는 아이템을 손에 들고 `/상자관리 생성 <이름>`.

**게임 확인 (개발프롬프트 6 체크리스트)**
- [ ] 1개·10개 사용: 열쇠가 정확히 1개/10개 줄고, 결과 칸 수 = 1/10, 인벤토리+우편으로 받은 수량 = 결과 합계 (`/유저관리 <닉> 우편 목록`, 운영 기록 "뽑기상자" 결과와 대조).
- [ ] 일반 인벤 여유 → 전부 인벤토리.
- [ ] 핫바 가득 + 지금 쪽(잠금 해제) 여유 → 지금 쪽 27칸에 들어감.
- [ ] 아공간 일부 잠금(1쪽만 열림, 1쪽 보는 중) → 1쪽 27칸만 쓰고, 2·3쪽(잠김)은 `/유저관리 <닉> 배낭 보기` 칸 수 그대로.
- [ ] 전부 잠금 상태(관리자가 `배낭 해금 1` 로 줄여 2쪽(잠김)을 보고 있는 상태) → 27칸 한 칸도 안 쓰고 핫바만, 나머지 우편.
- [ ] 쪽 넘기는 순간 열기(넘김 화살표 직후 Shift+우클릭) → 27칸을 안 쓰거나 "인벤토리가 바뀌어 열지 않았어요"(열쇠 그대로), 사라짐·복제 없음.
- [ ] 양쪽 전부 가득 → 열쇠만 줄고 결과 전부 우편(9첨부씩), 알림 "남은 상자 보상을 우편으로 보냈어요".
- [ ] 일부만 수용 / 같은 아이템 스택 일부 병합 후 → 인벤토리에 들어간 양 + 우편 양 = 결과 합계(우편에 전체가 다시 오지 않음).
- [ ] 잠긴 페이지는 단 한 슬롯도 쓰지 않는지 (위 두 경우 `배낭 보기` 칸 수 전후 비교).
- [ ] 연타(빠른 우클릭·Shift+우클릭 연속) → 개봉 한 번씩만, 열쇠·결과 일치.
- [ ] 결과 창 확인/ESC 후 다시 우클릭 = 새 개봉(재지급 아님). 확인 버튼이 지급을 다시 하지 않는지.
- [ ] 연출 중 접속 종료 → 재접속하면 이미 받은 상태(인벤토리·우편), 다시 받지 않음.
- [ ] 우편 실패(우편 DB 끊긴 상태에서 인벤 가득 열기) → 열쇠·인벤토리 보상은 반영, "보상 우편이 늦어지고 있어요", DB 복구 뒤 1분 재시도·재접속·재시작 때 우편 한 통만, `/상자관리 대기` 에서 사라짐.
- [ ] 서버 강제 종료 직후 재시작 → 콘솔 `LOOTBOX 끝나지 않은 개봉` 처리(영수증 있으면 우편만, 없으면 버림), 중복 없음.
- [ ] 결과 툴팁: 10칸 각각 마우스 → 기존 아이템 툴팁, 이름·설명·등급·수량·옵션이 실제 받은 아이템(인벤토리의 그 아이템 툴팁)과 같음.
- [ ] 연출: 상자 방향(왼쪽 손잡이·오른쪽 별 잠금) 그대로, 들썩임 → 대표 등급 빛 PNG, 10개 = 5×2, 각 칸 등급색, 배경 추가 없음. UI 배율 바꿔도 비율 같음.
- [ ] 모드 없는 클라: 연출 없이 지급 + 채팅 결과.
- [ ] 관리 창: 상자 고르기·보상 추가(인벤토리 칸)·개수/등급/가중치 수정·삭제·확률 % 표시·이름 변경·열쇠 다시 지정·상자 삭제 → loot-boxes.yml 반영, 손으로 고친 뒤 `/상자관리 새로고침`.
- [ ] `/상자관리 생성` 으로 ItemsAdder 열쇠 등록 → 이미 나눠 준 같은 ItemsAdder 아이템도 열림. 모델 데이터 있는 바닐라 아이템 → 이름이 다른 같은 재질·모델 아이템도 열림.
- [ ] 회귀(기존 인벤 가득→우편 경로에 같은 아공간 판정): 우편 수령·낚시 보상(인벤 가득)·퀘스트 완료·연금술·해제권/귀환권/땅문서/확장권 반환·관리자 마법봉 주기 — 잠긴 쪽 보는 중이거나 쪽 넘기는 중엔 27칸을 안 쓰는지, 평소엔 예전과 같은지, 반환권은 넘치면 발밑 대신 우편으로 오는지.

**오너 확인 필요 / Claude 판단**
- 10개 대표 등급 = 결과 중 가장 높은 등급.
- 10개 열기 열쇠 부족 = 거절(가진 만큼 열지 않음).
- 지금 안 보이는 잠금 해제 쪽(비활성 쪽)에는 넣지 않고 우편(위 이유).
- 퀘스트·연금술은 27칸을 못 쓰는 순간이면 거절(잠시 뒤 다시).
- 반환권들(해제권·귀환권·땅문서·확장권)은 넘치면 발밑 대신 우편.
- 운영 기록 종류 이름 "뽑기상자"(띄어쓰기 없음).

## 부록 A-2. 실제 ItemsAdder id (오너 목록 2026-10-26 11:11) — 코드 기본값 맞춤
오너가 준 실제 id 로 코드·기본 설정을 바꿨다 (서버에 이미 생긴 yml 은 jar 가 안 덮으므로 같은 값으로 바꿀 것):
| 기능 | 실제 id | 바꾼 곳 |
|---|---|---|
| 귀환권 (학교 스폰 귀환, ReturnTicketBridge) | `item:home_ticket` | return-ticket.yml `item` (예전 `item:return_ticket`) |
| 인기도 제한 해제권 | `item:popularity_reset` | popularity.yml `unlock-item` (예전 `item:popularity_unlock_ticket`) |
| 확성기 · 땅문서 · 가방 확장권 · 아티팩트 열쇠·조각 · 고급/유일/전설 확정권 · 마력코어 3종 | 코드 기본값과 같음 | 변경 없음 |
| 연금술 예시 회복 물약 | `item:health_potion` | alchemy.yml 주석 예시 |
나머지(리턴권·텔포권·초대권·던전 입장권·인벤세이브·변색종 변경권·컷신 변경권·마법 힌트권·재화 주머니·체력 포션·주문서 I~III·일반 확정권·가구/낚시도구/몬스터 전리품 뽑기권·API 상자 2종)는 오너에게 동작 확인 중 — 아직 코드 연결 없음.

## 37. 뽑기권 상자 3개 미리 등록 · 가구 보상 종류 (2026-10-26, 확률 오너 검수 대기)
오너: 가구·낚시도구·몬스터 전리품 뽑기권을 §36 뽑기 상자로. 내용·확률표·근거·검수 항목은 [docs/plans/loot-box-contents.md](../plans/loot-box-contents.md). API 상자 2종은 오너가 직접 만듦(안 넣음).
- `loot-boxes.yml`(소스 기본값 + overlay 같은 내용, `operating_overlay.py check` 차이 없음)에 `furniture_ticket`(가구뽑기권, 열쇠 `item:furniture_ticket`, 가구 97종 같은 확률·전부 일반) · `fishing_ticket`(낚시도구뽑기권, `item:fishing_ticket`, 장비 27종, 일반 60·고급 28·유일 10·전설 2 %) · `monster_loot_ticket`(몬스터전리품뽑기권, `item:monster_loot_ticket`, 18종, 1성 70·2성 24·3성 6 %) — 모두 enabled.
- **새 보상 종류 `furniture: <가구 id>`** (+ `label` 이름 필수, `icon` 그림, amount 1): 아이템 대신 LuckPerms `chacademi.housing.furniture.<id>` 권한. 개봉 기록(lootbox-receipts)에 `furniture=` 줄 → ②(열쇠·영수증) 뒤 ③에서 권한 → 우편 → 정리. 영수증 있으면 복구 때 다시 줌(권한은 여러 번 줘도 같음). 실패 시 1분 뒤 3번 + 다음 시작·접속, `/상자관리 대기` 에 표시.
- **이미 가진 가구는 추첨에서 뺌**(이번 10개 열기에서 나온 것도) = 가지지 않은 것 중 다시 뽑기. 다 모으면 "모두 모았어요"로 안 열림(열쇠 그대로). OP 는 모든 가구를 가진 것으로 보여 거절됨 → 확인은 OP 아닌 계정, 화면만은 `/상자관리 테스트 가구뽑기권 10`.
- 고친 파일: `LootBoxStore`(furniture·icon 필드, 표시용 아이템 PAINTING + PDC `magiccodexbridge:lootbox_icon`), `LootBoxRules.drawUnique/uniqueLeft`, `LootBoxJournal`(furniture 줄, 예전 기록 = 없음), `LootBoxBridge`(settle·grantFurniture), 클라 `LootBoxScreen`·`LootBoxAdminScreen`(PDC 그림 경로가 있으면 그 PNG). 테스트 `LootBoxRulesTest`·`LootBoxJournalTest` 추가. 프로토콜 변경 없음 — 옛 클라는 가구가 그림 아이템 아이콘으로만 보임.
- 확인: ① 뽑기권 3종 ItemsAdder id 가 운영과 같은지(다르면 관리 창 「열쇠 다시 지정」) ② OP 아닌 계정으로 가구뽑기권 1개 → 결과 칸 가구 그림, 내 방 꾸미기 보관함에 생김, 콘솔 `LOOTBOX FURNITURE granted` ③ `/상자관리 테스트 낚시도구뽑기권 10 지급` (미끼 10개 묶음이 만들어지는지) ④ 몬스터 전리품은 **임시 바닐라 아이템**(야생 몹 전용 전리품·드롭 테이블이 아직 없음, 메모 #55) — 전용 아이템이 생기면 관리 창에서 교체.
