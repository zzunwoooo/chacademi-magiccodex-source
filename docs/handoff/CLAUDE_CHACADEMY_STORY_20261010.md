# 차카데미 스토리 모드·플러그인 작업 기록 (2026-10-10)

- **브랜치**: `claude/chacademy-story-20261010` — `claude/first-nickname-20261010` 위에 쌓음 (이 브랜치를 받으면 최초 닉네임 작업도 같이 들어옴)
- **작성**: Claude (사용자 요청)
- **위치**: 저장소 루트의 `chacademy-story/` (MagicCodex Gradle 빌드와 **별개**. 루트 `settings.gradle` 에 넣지 않음)
- **빌드 상태**: 준우 PC 에서 `chacademy-story/build-auto.bat` 으로 빌드 성공 (모드 jar, 플러그인 jar). 모드는 준우 런처 `mods` 에 설치함.
  **서버 플러그인 설치, 게임 안 테스트는 아직.**

## 1. 무엇인가

메인 스토리용 Fabric 1.21.4 클라 모드(`chaca_story`) + Paper 1.21.4 플러그인(`ChacademyStory`) + HTML 편집기 2개.

| 부분 | 내용 |
| --- | --- |
| 컷신 | 레터박스, 줌, 반복 프레임, 잉크 전환, 자막(KoreanCNM), 타자기 소리, MASTER 음량 배경음, 2배속, 두 번째부터 SPACE 2초 건너뛰기. `/cutscene play <플레이어> <id>` |
| 스토리 대화 | AI 아님, 정해진 대사. 비주얼노벨 화면, 표정 일러스트, 선택지, 호감도 갈림길. `/storydialogue <플레이어> <id>` (콘솔 가능) |
| 편집기 | `editor/cutscene-editor.html`, `editor/dialogue-editor.html` (zip 으로 내보내서 클라 config 에 풀기) |
| 형식 | `FORMAT.md` |

대사·그림은 **클라** `config/chaca_dialogue/<id>/`, 실행할 명령어·호감도 값은 **서버** `plugins/ChacademyStory/dialogues/<id>.yml`.

## 2. 이번에 들어간 것 (사용자 요구)

1. **한글 닉네임**: 대사의 `{player}` = MagicCodex 한글 닉네임. `{account}` = 마인크래프트 닉네임. config `dialogue-placeholders` 의 PlaceholderAPI 값도 대사에 사용 가능. 서버 명령어에서는 `{player}` = 마인크래프트 닉네임, `{nickname}` = 한글 닉네임.
2. **"나"의 일러스트**: 화자 `me` 가 말하면 MagicCodex 모드가 받아 둔 ChacaPortrait 내 일러스트를 MagicCodex "내 차례"와 같은 배치로 그림.
3. **호감도 올리기·내리기**: 선택지(여러 명), 장면 들어올 때, 결말 장면에서 끝날 때, 대화가 끝나면 항상. **실제 점수는 서버 파일 `affinity:` 값만 적용** (클라 yml 을 고쳐도 안 바뀜).
4. **닫을 수 없음**: 스토리 대화는 끝날 때까지 ESC 무시, 다른 화면이 덮어도 다시 열림.
5. **이어서 보기**: 서버가 연 대화는 장면·대사 번호를 `plugins/ChacademyStory/progress.yml` 에 저장. 접속 끊김·서버 재시작 후 다시 접속하면 3초 뒤 그 대사부터. 이미 실행된 이벤트(명령·호감도)는 다시 안 함.

## 3. 주의사항 (MagicCodex 쪽과 맞물리는 곳)

이 프로젝트는 MagicCodex 코드를 **고치지 않고 읽기만** 한다. 아래 이름·시그니처를 바꾸면 스토리 쪽이 조용히 기능을 끈다 (오류로 멈추지는 않음). 바꿀 때는 인계 문서에 적어 주세요.

| 쪽 | 쓰는 것 | 방식 |
| --- | --- | --- |
| 클라 | `school.magiccodex.client.NicknameClient.display(String)` | 리플렉션 (public static) |
| 클라 | `PortraitClient.ready()`, `PortraitClient.drawTurn(DrawContext)` | 리플렉션 + setAccessible (패키지 전용 static). 1600x900 좌표계라고 가정 |
| 서버 | ServicesManager 의 `school.magiccodex.paper.NpcSocialFacade` — `apiVersion()==1`, `playerName(Player)`, `affinity(UUID,String)`, `addAffinity(UUID,String,String,int)` | 리플렉션 (ChacaNPC `MagicCodexLink` 와 같은 방식) |
| 서버 | 호감도 출처 이름 `dialogue` | MagicCodex `affinity.yml` 의 `daily-caps.dialogue` (기본 0 = 한도 없음) |
| 서버 | PlaceholderAPI `%user_nickname%` | MagicCodexBridge 가 없을 때 닉네임 대체 |

- 최초 닉네임 작업의 `first-nickname.on-complete` 기본값이 `storydialogue {player} ch1-2` 다. 이 명령은 이 플러그인 것이고, 대화 `ch1-2` 는 **아직 만들지 않았다** (클라 config 에 넣어야 함).
- 예전 ChacaNPC 의 `ChacaNpcApi.setAffinityProvider` 연결은 지웠다 (최신 ChacaNPC 에 없음, 호감도 주인은 MagicCodexBridge).
- 채널: `chacademy:cutscene_play|stop|done`, `chacademy:dialogue_open|stop|event|done|progress`. MagicCodex 채널과 겹치지 않음.
- 폰트(KoreanCNM)는 라이선스 확인 전이라 저장소에 없음 → `chacademy-story/FONTS.md`.
- 빌드는 각 폴더의 Gradle wrapper (`mod/`, `plugin/`). Windows 는 `build-auto.bat` 이 둘 다 빌드해서 `out/` 에 jar 를 모음.

## 4. 확인할 것

- [ ] 서버에 `chacademy-story-plugin-0.1.0.jar` 설치 (예전 ChacademyCutscene 플러그인은 제거)
- [ ] `/storydialogue <나> ch1_wakeup` → `{player}` 가 한글 닉네임, "나" 대사에 내 일러스트
- [ ] 선택지 호감도 → `/affinity <나> teacher` 로 값 확인 (MagicCodex 점수)
- [ ] 대화 도중 접속 종료 → 재접속 시 같은 대사부터, 이미 고른 선택지 명령이 다시 실행되지 않음
- [ ] 대화 도중 서버 재시작 → 같은 결과
