> Content follow-up: [Six Story descriptions and Claude editor contract](STORY_DESCRIPTION_CONTRACT_20261012.md). Personal dialogue files updated with backups; only description metadata is published. No restart required.

> Deployed 2026-10-12: [Unified journal, shared notices, mouse keys and target/wildlife release](CODEX_JOURNAL_INPUT_RELEASE_20261012.md). Source938256b; hosting630 tests passed; school/wild and personal mods verified. Real game visual acceptance remains pending because hosting graphics initialization failed. This supersedes earlier preparation-only notices below.

> 2026-10-12 prepared, not deployed: [attack targets / grounded rare wildlife](CODEX_TARGETS_WILDLIFE_20261012.md). UI/input work is separate.

> Deployed 2026-10-12: [Catalog gameplay runtime + Story queue fix](CODEX_CATALOG_RUNTIME_FIX_20261012.md). School/wild Bridge runtime.1 and Story0.1.3 verified; client and Velocity unchanged. This supersedes candidate-only notices below.

> Latest combined candidate (tested, NOT deployed): [Catalog gameplay startup + Story queue](CODEX_CATALOG_RUNTIME_FIX_20261012.md). Bridge300 + Story109 tests passed; server-only replacements pending normal maintenance.

> Latest follow-up (tested, NOT deployed): [Story failed-cutscene queue fix](CODEX_STORY_QUEUE_FIX_20261012.md). Server-only0.1.3 candidate; live remains0.1.2.

> Latest deployed integration: [Taming, single-file Story and gimmicks](CODEX_TAMING_STORY_20261012.md). 673 hosting tests passed; school/wild and personal UI/Story applied. This latest entry supersedes historical pending statuses below. Dungeon models and launcher/feed remain excluded.

# Current deployment: wild reset and HUD server fixes, 2026-10-12

See [completed operation and rollback boundary](CODEX_WILD_RESET_20261012.md). Wild overworld reset with verified backup; Wildlife installed; Bridge updated on school/wild. Launcher remains explicitly held. This supersedes the earlier staging/approval-pending status below.

# Current staging: HUD fixes and wildlife review, 2026-10-12

See [staged fixes and blockers](CODEX_HUD_FIXES_20261012.md). Nothing in this staging patch is deployed. Launcher changes are explicitly held. Icon baseline is corrected using parent-verified pixel evidence; live visual acceptance and worldgen/new-plugin approval remain pending.

# Current update: party HUD, 2026-10-12

See [party HUD handoff](CODEX_PARTY_HUD_20261012.md) for the latest deployment and test scope. Prior HolyTaming/protection features are preserved.

# Current integration: 2026-10-12

HolyTaming, recovery fixes and discovery/protection deployment supersede the historical pending status below. See [current handoff](CODEX_HOLYTAMING_20261012.md). Gimmick module and dungeon models remain excluded; shared donation database remains pending.

# Claude 작업분 목록 (GPT/Codex 인계용)

## 2026-10-11 shared source integration

This checkpoint is based on public `codex/hires-item-icons-20261006` commit `f44666f689714d5292adfff6e36c8586d27760c0`, preserving the chat/discovery integration working tree and adding Claude magic source from `d4c0859fe11e96291c874dd0cef7ea71ac17e8d5` plus Codex protection fixes. Integration branch: `codex/chat-discovery-magic-protection-20261011`.

Included: Velocity network chat relay and client presentation; discovery event core, donation policy administration and Velocity party conditions; `magic-mythic-paper` source, generated public skill configuration, generator, tests and WorldGuard protection; Bridge prepare/VFX/execute integration. Existing portrait Sunburst/Flare, Luna analysis/transparency, personal UI sizing, pets/shiny and Story source remain preserved. The 97-entry discovery catalog has 95 connected entries and 2 pending (`crimson_heart`, `bouncing_droplet`); it is distinct from the 95-entry magic gameplay/VFX set (generated routes have their own count).

Verification: hosting Java 21.0.10; final targeted test run passed **293 tests** (magic-mythic-paper 11, magic-codex-paper 282), zero failures. These are unit tests, including real WorldGuard classes used in memory and mocked Bukkit entities/events. They are not whole-repository or live-server acceptance tests. Actual dependencies: MythicMobs 5.9.0 (`C:/Chacademi/network/school/plugins/MythicMobs-5.9.0.jar`, staging hardlink), WorldGuard 7.0.13 (`worldguard-bukkit-7.0.13-dist.jar`), FAWE 2.13.1-SNAPSHOT-1119 in the same hosting plugins directory; Paper API 1.21.4. Paid/external JARs are not published. See [protection handoff](CODEX_MAGIC_PROTECTION_20261011.md) and [discovery handoff](../discovery-event-core/HANDOFF.md).

Deployment: earlier chat changes were applied to personal client/server. New discovery and magic-protection changes are **NOT deployed**. This Git synchronization performs no deployment, restart, launcher replacement/release update or DB operation. Discovery donation-policy table creation on future activation requires separate operational/DB authorization. No live tests of initial Mythic mechanic registration, WorldGuard boundaries/OP behavior, projectile/tick scheduling or actual pet integration have been performed.

Remaining: temporary-block recovery journal can lose recovery data (write ordering/save failure/unloaded-world handling); prison release does not preserve original AI awareness. Dungeon 63-model assets/generators/viewer/registration are withheld by user request. Newly observed `claude/gimmick-plugin-20261012` is not reviewed or integrated here. HolyTaming remains separately pending; existing TamingBridge discovery hooks do not authorize that separate feature. Secrets, runtime configuration, real databases, login material, paid JARs, restricted fonts and private reference images are excluded.

Older sections below are historical checkpoints; use this section for the current integration scope and deployment status.

| Claude branch | Source commit | Integration status |
| --- | --- | --- |
| claude/magic-mythic-20261011 | d4c0859fe11e96291c874dd0cef7ea71ac17e8d5 | Public source selectively integrated with protection fixes; not deployed |
| claude/dungeon-gimmick-models-20261011 | 81de103de182b6551862069d85845fd523aae8d4 | User deferred; excluded |
| claude/gimmick-plugin-20261012 | a8b7b678575c904a43109f0033461b5758a9d17f | Observed remotely; not reviewed or integrated |


Claude 는 GPT 작업 브랜치에 직접 커밋하지 않고, **`claude/` 로 시작하는 별도 브랜치**에만 올립니다.
각 브랜치에는 `docs/handoff/CLAUDE_<주제>_<날짜>.md` 인계 문서가 한 개씩 있습니다.

## GPT/Codex 가 받는 순서

사용자가 "깃에서 클로드 작업분 확인하고 주의사항 보고 다음 패치 진행해" 라고 하면:

1. `git fetch origin` 후 `git branch -r --list 'origin/claude/*'` 로 Claude 브랜치를 찾는다.
2. 아래 표에서 **상태가 `받기 전`** 인 브랜치마다 인계 문서를 읽는다 (`git show origin/<브랜치>:docs/handoff/<문서>`).
   문서의 "주의사항"과 "확인할 것"을 먼저 본다.
3. 지금 작업 브랜치에 병합한다 (`git merge --no-ff origin/<브랜치>`). 충돌이 나면 인계 문서의 "변경 범위" 표를 기준으로 Claude 쪽 의도를 살려 해결한다.
4. AGENTS.md 대로 호스팅에서 빌드·테스트한다.
5. 이 표의 상태를 `받음 (<커밋>, <날짜>)` 으로 바꿔 커밋한다. 받은 뒤의 Claude 브랜치는 지우지 않는다 (기록용).
6. 그다음 사용자의 다음 패치를 진행한다.

## 브랜치

| 브랜치 | 기준 | 인계 문서 | 내용 | 상태 |
| --- | --- | --- | --- | --- |
| `claude/first-nickname-20261010` | `codex/hires-item-icons-20261006` @ `9a6263a` | `CLAUDE_FIRST_NICKNAME_20261010.md` | `/최초닉네임설정` (닫을 수 없는 창, 재접속 시 다시 열기, 저장 후 `storydialogue {player} ch1-2`) | 받음 (1a42349, 2026-10-10; 호스팅 검증 완료, 배포 전) |
| `claude/chacademy-story-20261010` | `claude/first-nickname-20261010` (위 브랜치 포함) | `CLAUDE_CHACADEMY_STORY_20261010.md` | `chacademy-story/` 스토리 컷신·대화 모드+플러그인+편집기. 한글 닉네임, 내 일러스트, 호감도, 닫기 금지, 이어서 보기 | 받음 (3779d57, 2026-10-10; 검증·기존 한국어 글꼴 개인 배포 완료) |
| `claude/audit-fixes-20261010` | `codex/hires-item-icons-20261006` @ `310512b` | `CLAUDE_AUDIT_FIXES_20261010.md` | 스토리 대화창을 MagicCodex 기존 대화 UI로 통합 + 전체 코드 점검 수정 (상태 저장 추방, VFX 한도, 닉네임 중복 금지, 친구 신청제, 리롤 소모 규칙, 스토리 서버 검증 등). **빌드·실행 안 함 — 문서 0장·4장 먼저** | 받음 (36b2b35 안전 수정, 호스팅 790 테스트·MariaDB 격리 검증·서버/개인 런처 배포 완료; Story 콘텐츠 별도) |
| `claude/party-hud-20261012` | `codex/chat-discovery-magic-protection-20261011` @ `e11af86` | `CLAUDE_PARTY_HUD_20261012.md` | 파티 위젯: 기숙사 아이콘·한글 닉네임·실시간 체력/마나 막대, Alt 이동·크기 조절, 마우스 올리면 수치 팝업 (Paper vitals → Velocity → 클라이언트). **MC 의존 코드 빌드 안 함 — 문서 3·5장 먼저** | 받음 (0dcd626, 2026-10-12; hosting tests and deployment verified) |
| `claude/survival-inventory-20261012` | `codex/chat-discovery-magic-protection-20261011` @ `c373610` | `CLAUDE_SURVIVAL_INVENTORY_20261012.md` | 서바이벌 인벤토리 새 화면(떠 있는 조합 구체·타원 받침, 바닐라 슬롯·조합·조합법 그대로) + 128px 아이콘 반짝임. **Gradle 빌드·실게임 전 — 문서 0·6장 먼저** (1.21.4 yarn jar로 javac 타입검사, JUnit 257개 통과) | 받음 (2026-10-13; hosting 633 tests passed; see CODEX_INVENTORY_BROOM_RELEASE_20261013.md) |
| `claude/broom-flight-20261012` | `codex/chat-discovery-magic-protection-20261011` @ `c373610` | `CLAUDE_BROOM_FLIGHT_20261012.md` | 비행 마법 → 빗자루 비행 (클라이언트 물리·균형/낙마·숙련도·빗자루 10종·`/빗자루`). `chacademy-motion/` 0.2.0 사본 추가(PC 빌드 성공) + magic-mythic 연결(빌드 안 함). MagicCodex 제안은 문서 5장(미적용). **0.3.0 추가: 빗자루 보유=권한, 장비창 빗자루 칸·선택창 제안 `CLAUDE_BROOM_EQUIP_UI_20261012.md` (GPT 검토 후 적용, 자료 `broom-ui/`)** | 받음 (2026-10-13; hosting 633 tests passed; see CODEX_INVENTORY_BROOM_RELEASE_20261013.md) |
| `claude/aim-camera-20261013` | `codex/chat-discovery-magic-protection-20261011` @ `59f10f8` | `CLAUDE_AIM_CAMERA_20261013.md` | 어깨 시점(SSR 대체, F5 3인칭 뒤만), 마법 모드 에임 커서(시선 고정·Alt 꾹=시점 회전), 롤식 조준 시전(키→지형 맞춤 범위/사거리/대상 표시→좌클릭 발동·우클릭 취소). **클라이언트만, 서버·프로토콜 변경 없음**(발동 직전 시선 패킷). javac 타입검사·JUnit만, **빌드·실게임 전 — 문서 0장 먼저, SSR 런처에서 제거** | 받기 전 |
| `claude/followup-fixes-20261013` | `126fcea` | `CLAUDE_FOLLOWUP_FIXES_20261013.md` | 수정 요청서 1~7: 채팅 중 알림·스킬HUD·좌상단 메뉴, 알림 소리, 커스텀 인벤토리 제거, 설정(50%·슬라이더·직접 입력·소리), 키세팅 원래 디자인+마우스(아래 6칸 제거, 창 높이 축소), 사거리 표시(단색·채움·블룸·직선 홀로그램·힌트 위치), 장비창, 비행 HUD(시안·기능), 교화 UI. **javac·JUnit만, 빌드·실게임 전 — 문서 0장 먼저, 비행 HUD 사용자 승인됨** | 받기 전 |
| `claude/magic-71-gameplay-20261014` | `claude/followup-fixes-20261013` @ `41ee355` | `CLAUDE_MAGIC_71_20261014.md` | 미구현 마법 71종(이전 7 + 101~167 신규 64) 게임플레이: 애드온 새 메커닉 6종·패시브(슬롯 장착)·획득 조건(수동 9·자연 47)·VFX 바인딩 초안(미사용). 64종은 VFX 클라이언트 지원 전까지 시전 비활성 유지, `/마법미몹 시험` 으로 기능 확인. MagicCodex 코드는 제안만(Bridge 쿨타임 API 패치·클라이언트 슬롯 채널). **javac 타입검사만, JUnit·빌드·실게임 전 — 문서 0장 먼저** | 받기 전 |
| `claude/magic-71-gameplay-20261014` | 같은 브랜치 이어서 | `CLAUDE_VFX_BC7_20261014.md` | PortableVFX 클라이언트 텍스처 BC7 압축(순수 Java 인코더, 디스크 캐시, 화질 38 dB 미만은 RGBA 유지). 신규 62팩 430→119 MiB, 예산 문제 해소. `-Dportablevfx.claude.textureCompression=off` 로 예전 경로. **스텁 javac + 일부 테스트만, Gradle·GL 스모크·실게임 전 — 문서 0장 먼저** | 받기 전 |
| `claude/magic-71-gameplay-20261014` | 같은 브랜치 `da851b7` | `CLAUDE_DEPLOY_101_167_20261015.md` | **101~167 배포 전달 사항**: 070ceae 와 통합, 서버·클라이언트·런처에 무엇을 배포할지, 기존 PC 도감 폴더 미갱신 문제, VFX 연결 미반영(시전 거부 원인), 켜지 않는 마법 목록 | 받기 전 |

## Claude 가 지키는 규칙

- GPT 브랜치(`codex/*` 등)에 직접 push 하지 않는다.
- 이 저장소를 빌드·서버 설치하지 않는다 (AGENTS.md: 호스팅 우선). 빌드 여부는 인계 문서에 적는다.
- 새 브랜치를 만들 때마다 이 표에 한 줄 추가한다.

## Codex audit checkpoint

Audit integration verified and deployed; Story content intentionally deferred. See [CODEX_AUDIT_VERIFICATION_20261010.md](CODEX_AUDIT_VERIFICATION_20261010.md). See the final verification section for deployment evidence and remaining content/gameplay limitations.

## Codex Story client-only checkpoint (2026-10-11)

최신 기본 대화 흐름은 클라이언트 단일 실행·로컬 중복 기록으로 변경됐고, 두 고정 보상을 접속자 본인에게 적용하는 한정 API 연결을 포함한다. UI/닉네임 ACK/컷신 공개 완료 알림 제거와 사용법은 [CODEX_STORY_LOCAL_20261011.md](CODEX_STORY_LOCAL_20261011.md)를 먼저 읽는다. 앞선 인계의 서버 그래프 필수 설치·서버 ledger 설명은 새 기본 대화 경로에 적용하지 않는다. school/wild 및 개인 런처 배포는 완료됐으며 이번 공유 Git 동기화는 재배포가 아니다. 개인 폰트·콘텐츠·설정·JAR은 공개 소스에 넣지 않는다.

## Codex portrait clip follow-up (2026-10-11)

[CODEX_PORTRAIT_CLIP_20261011.md](CODEX_PORTRAIT_CLIP_20261011.md): 앞선 Story UI 변경에서 빠진 공통 PlayerTurn 및 AI NPC 초상화 scissor 하단을 CLIP_BOTTOM으로 통일. 얼굴 위치/크기 불변, Fabric 226 테스트 및 remapJar 검증, UI 자산 901개 보존. 서버 변경 없음.

## Codex 한국어 명령·리로드 통합 (배포 완료)

4014a51 채팅·파티 기준의 한국어 명령, 클라이언트 리로드, ID 메타데이터 자동완성 및 배포 제한은 [CODEX_STORY_COMMANDS_20261011.md](CODEX_STORY_COMMANDS_20261011.md)를 확인한다. 사용자 승인으로 공유 소스에 반영. 개인 런처 및 school/wild 교체·재시작, 추가 승인된 Velocity 파티 설치·재시작 완료. 실사용 다계정 검증은 별도.
