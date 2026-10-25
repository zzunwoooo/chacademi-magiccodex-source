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
