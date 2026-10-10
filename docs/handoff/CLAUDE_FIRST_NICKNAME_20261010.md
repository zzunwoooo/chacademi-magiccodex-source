# 최초 닉네임 설정 + 스토리 연동 작업 기록 (2026-10-10)

- **기준**: 브랜치 `codex/hires-item-icons-20261006`, 커밋 `9a6263a`
- **브랜치**: `claude/first-nickname-20261010` (GPT 브랜치와 분리. 받는 순서는 `CLAUDE_INDEX.md`)
- **작성**: Claude (사용자 요청)
- **빌드·설치 상태**: 코드만 반영. **빌드, 서버 설치, 재시작 모두 하지 않음.** AGENTS.md대로 호스팅에서 빌드해 주세요.
  `NicknameProtocol` 변경은 `javac`로 단독 컴파일하고 FIRST/FIRST_DONE 왕복을 확인했습니다. 나머지 모듈은 컴파일하지 않았습니다.

## 1. 사용자 요구

1. 기존 `/닉네임설정`이 아니라 **`/최초닉네임설정`** 으로 들어온 경우에는 취소와 창 닫기가 안 된다.
2. 그 상태에서 서버가 꺼지거나 유저가 접속을 끊으면, 다음 접속 때 자동으로 다시 닉네임 창을 띄운다.
3. 닉네임 설정이 끝나면 `/storydialogue <플레이어> ch1-2` 를 실행한다 (차카데미 스토리 플러그인의 대화 명령).

## 2. 변경 범위

| 위치 | 변경 |
| --- | --- |
| `magic-codex-protocol` `NicknameProtocol` | 서버 → 클라 알림 종류 `FIRST=3`(최초 창 열기), `FIRST_DONE=4`(저장 확인, 창 닫기) 추가. `sequence=0`으로 보냄. 응답 kind 검사 범위를 4까지 |
| `magic-codex-paper` `NicknameBridge` | `/최초닉네임설정` 실행기, 대기 목록 `first-nickname.yml`, 접속 시 다시 보내기, 저장 성공 시 완료 처리 |
| `magic-codex-paper` `plugin.yml` | 명령어 `최초닉네임설정`, 권한 `magiccodex.nickname.admin`(기본 op) |
| `magic-codex-paper` `config.yml` | `first-nickname.on-complete` (기본 `storydialogue {player} ch1-2`) |
| `magic-codex-fabric` `NicknameClient` | FIRST / FIRST_DONE 처리. 최초 모드 동안 화면이 비면 1초마다 다시 엶. 접속·끊김 시 초기화 |
| `magic-codex-fabric` `NicknameScreen` | `first` 모드: X·취소 버튼 숨김, ESC 무시, `close()` 무시, 저장 버튼 가운데, 안내 문구 |
| `magic-codex-paper` 테스트 | `NicknameProtocolTest.firstNicknamePushRoundTrip` |

바꾸지 않은 것: 기존 `/닉네임`, `/닉네임설정` 흐름(닫기 가능), 닉네임 저장소·DB 스키마, OPEN/SAVE/CLOSE 요청 형식.

## 3. 동작

- `/최초닉네임설정 <플레이어>`: 콘솔·`magiccodex.nickname.admin`. 대상 UUID를 `plugins/MagicCodexBridge/first-nickname.yml` 의 `pending` 에 넣고 FIRST 를 보낸다.
- `/최초닉네임설정` (인자 없이): 플레이어 본인. 아직 저장된 닉네임이 없거나 이미 대기 중일 때만 (닉네임을 정한 사람이 스토리 대화를 다시 여는 것을 막음).
- 클라 채널이 아직 등록되지 않았으면 1초 간격으로 15번까지 다시 시도. 끝까지 안 되면 "MagicCodex UI 모드가 필요합니다" 메시지.
- 접속(`PlayerJoinEvent`) 2초 뒤, 대기 목록에 있으면 다시 FIRST 를 보낸다 → 서버 재시작·재접속 후에도 창이 다시 뜬다.
- 저장(SAVE)이 실제로 반영되면(revision +1, 닉네임 일치): 대기 목록에서 빼고 파일 저장 → FIRST_DONE 전송(클라가 창을 닫음) → 10틱 뒤 `first-nickname.on-complete` 명령을 콘솔로 실행.
  자리 표시: `{player}` 마인크래프트 닉네임, `{nickname}` 방금 정한 닉네임, `{uuid}`.
- 구버전 클라는 kind 3·4 를 검증 단계에서 버리므로 깨지지 않는다 (창이 안 뜰 뿐).

## 4. 주의사항

- **프로토콜 변경이 서버·클라 양쪽에 걸쳐 있다.** MagicCodexBridge 와 magic-codex-ui 를 같은 커밋으로 함께 배포해야 한다. 한쪽만 새것이면 최초 창이 안 뜰 뿐 기존 닉네임 기능은 그대로 동작한다.
- `NicknameScreen(Screen parent)` 공개 생성자는 그대로 두고, 최초 모드는 패키지 전용 `NicknameScreen(Screen,boolean)` 로 추가했다. 기존 호출부는 바꿀 필요 없다.
- `first-nickname.on-complete` 가 config 에 없으면 코드 기본값 `storydialogue {player} ch1-2` 를 쓴다. 기존 서버 config 에는 이 항목이 없으므로, 명령을 바꾸려면 config 에 직접 추가해야 한다.
- `storydialogue` 는 이 저장소가 아니라 차카데미 스토리 플러그인(ChacademyStory) 명령이다. 그 플러그인이 없으면 콘솔에 "Unknown command" 만 남고 다른 문제는 없다.
- 최초 모드 동안 클라는 화면이 비면 1초마다 창을 다시 연다. 다른 서버 화면(컷신 등)이 떠 있는 동안은 열지 않는다.
- `first-nickname.yml` 은 서버 하나의 파일이다. school·wild 처럼 서버가 여러 개면 대기 상태가 공유되지 않는다 (최초 설정은 한 서버에서만 여는 것을 권장).

## 5. 확인할 것

- [ ] 호스팅 빌드 (`Build-Server.ps1 -Verify`): MagicCodexBridge, magic-codex-ui 둘 다 새 jar 필요
- [ ] `/최초닉네임설정 <나>` → X·취소 없음, ESC 무시
- [ ] 창이 열린 채 접속 종료 → 재접속 시 다시 열림. 서버 재시작 후에도 동일
- [ ] 저장 → 창 닫힘 → `storydialogue <나> ch1-2` 실행 (스토리 플러그인 ChacademyStory 와 대화 `ch1-2` 가 있어야 함)

## 6. 같이 알아 둘 것 (차카데미 스토리 쪽, 이 저장소 밖)

차카데미 스토리 모드·플러그인(`chacademy-story`, 별도 프로젝트)이 이 저장소의 공개 경로를 **읽기만** 합니다. 이름·시그니처를 바꿀 때 알려 주세요.

- 클라: `school.magiccodex.client.NicknameClient.display(String)` (한글 닉네임), `PortraitClient.ready()` / `drawTurn(DrawContext)` (스토리 대화의 "나" 일러스트). 리플렉션, 없으면 조용히 건너뜀.
- 서버: `NpcSocialFacade.playerName / affinity / addAffinity` (출처 `dialogue`). 호감도의 주인은 계속 MagicCodexBridge.
