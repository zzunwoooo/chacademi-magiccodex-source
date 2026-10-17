# 큐디치 (3:3 공중 PvP) — Claude 인계 2026-10-17

브랜치 `claude/kyudichi-20261017` (기반: `claude/party-ui-20261016` @ 8e9bbad → 파티 UI 브랜치를 먼저 또는 함께 통합).
커밋: d3a2242 경기장 플러그인 · 3a806bb 클라이언트 HUD · 595cd38 매칭 대기열 · 2085219 클라이언트 창/세팅/조준.
**빌드·배포 안 함.** Claude 환경에서는 Gradle/Minecraft jar를 받을 수 없어 Fabric 클라이언트는 컴파일하지 못했다 (Paper·Velocity 쪽은 javac 소스 대조로 오류 0, 순수 로직 테스트 통과). GPT가 호스팅에서 빌드하고 컴파일 오류가 나면 고쳐 달라.

기획 전체: 프로젝트 문서 `claude/quidditch-plan.md`. 이름은 **큐디치** (퀴디치 아님).

## 구성

| 모듈 | 무엇 | 비고 |
| --- | --- | --- |
| `kyudichi-paper` (새 플러그인 **ChacaKyudichi**, jar `chaca-kyudichi`) | 경기장·경기 엔진 (퀘이플/블러저 2/스니치, 성소 4종, 골·처치·스니치 점수, 골든골, 포기 투표, 재접속 60초, 관전, 연습, 레이팅·전적 DB), 관리자 명령 `/큐디치관리`, 플레이어 `/큐디치`, 프록시 링크(`Link`) | **경기장/PvP 전용 서버에만** 설치 |
| `magic-codex-protocol` | `KyudichiProtocol` (`chacademi:kyudichi`, 경기장↔클라), `KyudichiQueueProtocol` (`chacademi:kyudichi_queue` 프록시↔클라, `chacademi:kyudichi_link` 프록시↔경기장) | GOLDEN 테스트: magic-codex-fabric `KyudichiProtocolTest`, `KyudichiQueueProtocolTest` |
| `magic-codex-paper` (Bridge) | 새 이벤트 `MagicCastAttemptEvent`(시전 거부), `ArtifactUseAttemptEvent`, `MagicAimPointEvent`(공중 조준점) — `SpellRuntime.prepare`에서 AIM 경로가 아무것도 안 맞았을 때만 발생, 사거리 안 같은 월드 점만 받음 | 큐디치가 없으면 동작 변화 없음 |
| `magic-codex-fabric` (클라 모드) | 경기 HUD(점수판·피드·F키·공/성소/핑 표시·결과 카드), Alt 자석 핑+방향 메뉴, 대기열 카드(매칭 중/수락 확인), 대시보드(`/큐디치`), 준비 60초 마법 세팅 창, 경기용 6칸(비행 고정) 덮어쓰기, Alt/채팅창에서 우측 아래 마법 칸 끌어서 자리 바꾸기, 큐디치 한정 공중 조준(휠로 거리) | 아트는 모드에 내장 (`textures/gui/kyudichi/`) |
| `chacademi-party-velocity` | `/큐디치` 대기열·매칭(`KyudichiMatchmaker`, `KyudichiQueue`), 레이팅/전적 읽기(`KyudichiDb`, **MariaDB 읽기 전용**) | jar에 mariadb 드라이버와 protocol/database 소스 포함하도록 build.gradle 수정됨 |
| `chacademy-motion` (플러그인+모드) | `chacademy:broom_fx` 채널: 안정성 성소 = 안정성 가득, 가속 성소 = 기본 가속과 같은 효과 (쿨타임 안 씀/안 돎) | 모드·플러그인 같이 배포 |
| PortableVFX 팩 | ShrineHealth / ShrineMana / ShrineStability / ShrineHaste (idle 반복, pickup 1회) | 오너 PC `차카데미 vfx_Claude\Shrine*` zip |
| ModelEngine | `kyudichi_quaffle / _bludger / _snitch` (LittleRoom 팩 bbmodel, 블러저 재색) — 플러그인이 처음 켤 때 `plugins/ModelEngine/blueprints/kyudichi/`에 복사 | `/meg reload` + ItemsAdder 리소스팩 반영 필요. 없으면 아이템 모양으로 표시 |

## 배포 체크리스트 (GPT)

1. 경기장 서버 새로 만들기 (Velocity 이름 기본값 `arena`). Paper 1.21.4, Bridge(MagicCodexBridge)·MagicMythic·ChacademyMotion·ModelEngine·PortableVFX·WorldGuard 등 학교 서버와 같은 마법 스택. 경기장 리전은 **WorldGuard pvp allow**.
2. `ChacaKyudichi` 설치 + `plugins/ChacaKyudichi/database.properties` (Bridge와 같은 형식, **공용 MariaDB** — 테이블 `codex_kyudichi_rating / _match / _match_player` 자동 생성).
3. Velocity: 새 party jar. `plugins/chacademi-party/kyudichi.properties` 첫 실행 시 생성 → `arena-server`, `return-fallback-server`(기본 school), `arena-slots`(기본 3), `tier-floors`(경기장 config `rating.tiers`와 같게). `kyudichi-database.properties`에 **같은 MariaDB** (없으면 모두 1000점으로 매칭, 랭킹/전적 빈 화면). `party.properties`의 `trusted-servers`에 경기장 서버도 추가 (파티 명단·닉네임·파티 HUD가 경기장에서도 동작하도록; 대기열 신청 자체는 경기장 서버에서도 따로 허용됨).
4. Bridge(magic-codex-paper) 새 jar를 **학교·야생·경기장 모두**에 (이벤트 클래스 추가, 기존 동작 동일).
5. 클라 모드(magic-codex-fabric) + chacademy-motion 모드/플러그인 같이 배포.
6. PortableVFX Shrine 4팩 경기장 서버에 설치. ModelEngine `/meg reload` 후 리소스팩 재생성.
7. 경기장 세팅은 오너가 직접: `/큐디치관리 막대` → pos1/pos2 → `/큐디치관리 경기장 ...` (탭 완성 참고). `/큐디치관리 경기장 <이름> 검사`로 빠진 항목 확인 후 켜기. 혼자 테스트: `/큐디치관리 테스트 <경기장> [준비:0] [스니치:0] [무적]`.

## 주의사항

- **`/큐디치` 명령 분담**: Velocity가 `신청·취소·수락·거절·대기열·관전·랭킹·정보·연습·도움말`과 인수 없는 `/큐디치`(모드 사용자는 대시보드 열림)를 처리하고, `hasPermission=false`로 나머지(`포기`, `준비`, `관전 나가기`, 경기장 서버 안의 `연습`)는 경기장 서버로 넘긴다. Velocity가 권한 없는 명령을 백엔드로 넘기는 동작을 **실제로 확인**해 달라. 안 넘어가면 해당 하위 명령을 프록시에서 링크로 전달하도록 바꿔야 함.
- 링크 채널 `chacademi:kyudichi_link`은 프록시가 모든 방향에서 handled 처리 (클라이언트 위조 차단). 경기장 서버 이름이 `arena-server`와 다르면 경기장 메시지를 무시한다.
- 경기장 서버 Paper가 프록시 플러그인 메시지를 받으려면 플레이어가 있어야 한다 → 배정은 도착한 플레이어의 HELLO(입장 1초 뒤)에 대한 응답으로 전달. 6명이 다 오면 즉시, 아니면 첫 배정 30초 뒤 온 사람끼리 시작(안 온 사람은 접속 끊김 → 60초 안 재접속 시 합류), 한 팀 전원 미도착 = 무효.
- 경기 끝 6초 뒤(결과 카드) 원래 서버로 돌려보냄. 위치는 그 서버가 접속 종료 때 저장한 자리.
- 진행 중 경기 추적은 LIVE 보고(2초마다)가 2분 끊기면 정리. 경기 중 끊긴 사람이 다시 접속하면 프록시가 첫 서버를 경기장으로 지정.
- 공중 조준: 클라가 시전 직전 C2S AIM 좌표를 보내고, 경기장 플러그인이 1초(20틱) 안의 다음 `MagicAimPointEvent`에서 한 번만 사용 (경기장 bounds 안만). **AIM 경로 마법만 해당.** 블록이 필요한 마법(소환 등)은 여전히 바닥이 필요하다.
- 경기용 6칸: `config/magiccodex/kyudichi-loadout.yml`. 키는 평소 6칸 순서의 키, 평소 칸에 키가 없으면 Q·E·R·T·G·V·C 중 빈 키를 빌린다. **F(손 바꾸기)에 지정된 마법은 경기 중 큐디치 키에 밀려 못 쓴다** (세팅 창에 빨간 키로 경고).
- 평소 마법 칸 끌기 = HUD 순서만 바꿈(마법+키 함께 이동, `key-settings.yml`의 registration-order 저장). 경기 중 끌기 = 경기용 마법만 바꾸고 키는 자리 그대로.
- 금지 마법/아티팩트는 경기장 `config.yml` `banned-spells` / `banned-artifacts` (기본 `rewind_hourglass`). 클라 세팅 창은 금지 목록을 모른다 (시전 시 서버가 거부).
- 레이팅 숫자(K=60, MVP 배율, 티어 범위)는 오너 최종 승인 대기. 바뀌면 경기장 config와 프록시 `tier-floors`를 같이 바꿀 것.
- **랜덤 팀 = 파티가 아님**: 파티 대상 마법(파티원 힐·버프)이 파티 명단으로 대상을 고르면 랜덤 팀원에게는 안 들어간다. 경기장 플러그인은 적 힐을 0으로, 팀원 힐을 통계에 넣을 뿐 대상 선택은 바꾸지 않는다 → 실제 테스트 후 필요하면 경기 중 팀 = 파티로 취급하는 처리 추가.
- 보안: DB 비밀번호·forwarding secret을 로그에 내지 말 것. 두 플러그인 모두 DB 설정은 연결 대상(host:port/db)만 로그에 남긴다.

## 테스트 상태

- 순수 테스트 통과: RatingMath, Box/기간/핑, KyudichiWire, KyudichiProtocol(3), KyudichiQueueProtocol(5), KyudichiMatchmaker(5).
- javac 소스 대조: kyudichi-paper, chacademi-party-velocity, Bridge 새 이벤트 — 자기 파일 오류 0. Bridge `SpellRuntime` 수정분과 Fabric 클라이언트 전체는 **미컴파일**.
- 실제 서버/게임 테스트 0회. 우선 확인할 것: 혼자 테스트 경기(F키·공·성소·결과), 2계정 매칭(수락→이동→복귀), 재접속, 관전, 대시보드, 세팅 창, 공중 조준.


## 2026-10-17 오후: UI 재제작 (커밋 0f4c798, 06afebc)

- 혼자 테스트·연습 경기는 한 팀이 비어도 끝나지 않음 (모두 나가야 종료).
- 오너 PNG `CHACADEMI_QUIDDITCH_FULL_UI_REBUILD` 를 `textures/gui/kyudichi/ui/<폴더>/` 에 넣음 (layouts 제외, keys 256px로 축소,
  recent_table 의 예시 승패 배지 제거, scrollbar 를 track/thumb 로 분리, 오너가 따로 준 frame_ranking·frame_record 추가). 약 23MB.
- 화면 코드: `client/kui/*View.java` (마인크래프트 의존 없음, 레퍼런스 1672×941 좌표 그대로) + 게임용 `KyudichiCanvas`.
  같은 코드를 `src/test/.../kui/PreviewMain` 이 Java2D 로 그려 레퍼런스와 비교 이미지를 만든다 (`AwtCanvas`, `Samples`).
  레이아웃을 고칠 때는 View 만 고치고 PreviewMain 으로 확인.
- 새 창: 신청(`/큐디치`, 프록시 APPLY) · 매칭 대기 카드(HUD 오른쪽 위) · 수락 팝업(자동) · 준비(자동, F 로 다시 열기) · 마법 선택 · 결과(자동) ·
  대시보드(`/큐디치 현황|랭킹|정보`). `/큐디치미리보기 신청|매칭|수락|준비|경기|결과|대시보드|끄기` 로 서버 없이 확인.
- 프로토콜 추가: 대기열 S2C 5 APPLY, C2S 11 APPLY, DASH 는 LiveCard(얼굴), Rank·PlayerRecord 에 skin 해시. 경기장 S2C 8 ROSTER(기숙사·빗자루·레이팅).
- 새 공개 메서드: Bridge `MagicCodexBridge.houseOf(Player)`, 모션 플러그인 `ChacademyMotionPlugin.equippedBroomId(Player)` (경기장 플러그인이 리플렉션으로 사용).
- F키·퀘이플 타이머는 레퍼런스 위치(화면 맨 아래)가 핫바와 겹쳐 핫바·액션바 바로 위로 올림.
- 오너 EXTRA_PNG (2026-10-17) 적용: 탭별 창 `dashboard/frame_live·frame_ranking·frame_record` (1672×941 캔버스 좌표, 탭 구워짐),
  `dashboard/waiting_party_pill` (알파 영역만 잘라 700px), `icons/*` 10개, `medals/rank_1~3`.
  아이콘 위치는 레퍼런스에서 잰 값이고 `Theme.icon()` 이 PNG 투명 여백을 빼고 그림 부분만 맞춘다.
  - frame_ranking 에 구워진 스크롤 손잡이를 잘라 `dashboard/rank_thumb` 로 분리 (프레임 그 자리는 트랙으로 채움).
  - match_card 의 "◇ ─ ◇" 가운데 줄이 "진행 중" 글자를 가로질러 그 줄만 지움 (양옆 줄·마름모는 그대로).
  - 랭킹: 맨 위에서는 1~3위 금색 행 + 메달, 스크롤하면 고른 높이의 일반 행 8줄. 내 순위 행은 목록 판 안쪽 아래.
  - 대기 파티 알약 그림이 레퍼런스보다 가로로 길어 297×44 로 그림 (레퍼런스 235×48).
  - 이제 안 쓰는 부품: dashboard/main, tab_idle·tab_selected, search_field, profile, stat_card, recent_table, scrollbar* (지우지 않음).
- 클라이언트는 여전히 이 환경에서 컴파일 못 함 → 호스팅 빌드에서 오류 나면 수정.
