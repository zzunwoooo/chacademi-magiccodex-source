# 차카데미 스토리 (컷신 + 대화)

| 폴더 | 내용 |
|---|---|
| `editor/cutscene-editor.html` | 컷신 편집기 (더블클릭해서 열기) |
| `editor/dialogue-editor.html` | 대화 편집기 (더블클릭해서 열기) |
| `mod/` | Fabric 1.21.4 클라이언트 모드 `chaca_story` (예전 `chaca_cutscene` 을 대신함) |
| `plugin/` | Paper 1.21.4 서버 플러그인 `ChacademyStory` (예전 ChacademyCutscene 을 대신함) |
| `example/` | 예시 컷신, 예시 대화 |
| `out/` | 빌드된 jar |
| `FORMAT.md` | 컷신·대화 파일 형식 |

## 설치

1. 모드: `out/chacademy-story-0.1.0.jar` → 클라 `mods/` (예전 `chacademy-cutscene-*.jar` 는 지울 것. 같이 있으면 게임이 안 켜짐)
   - **MagicCodex UI 모드(`magiccodex`)가 같이 있어야 한다** (없으면 게임이 켜질 때 알려 줌). 스토리 대화는 자체 창이 아니라 MagicCodex NPC 대화창으로 나온다. 대화창 창구(`ExternalDialogue`, API 1 이상)가 들어 있는 MagicCodex 빌드여야 하며, 두 모드는 같이 배포한다. 자세한 것은 `FORMAT.md` 의 "대화창".
2. 플러그인: `out/chacademy-story-plugin-0.1.0.jar` → 서버 `plugins/` (예전 ChacademyCutscene 플러그인은 지울 것)
3. 컷신 zip → `.minecraft/config/chaca_cutscene/` 에 풀기
4. 대화 zip → 클라 `.minecraft/config/chaca_dialogue/` 에 풀기, **같은 zip 을 서버 `plugins/ChacademyStory/dialogues/` 에도 풀기** (`<대화id>/dialogue.yml` + `<대화id>/server_commands.yml`, 그림은 없어도 됨) → `/cutscene reload`
   - 서버는 `dialogue.yml` 로 선택지·조건·결말을 검증한다. 서버에 `dialogue.yml` 이 없는 대화는 열리지 않는다 (config `require-server-graph`, 기본 `true`). 클라와 서버의 `dialogue.yml` 은 항상 같은 파일로.
   - 예전 위치 `dialogues/<대화id>.yml` (명령어 파일) 도 계속 읽지만, 그것만으로는 대화가 열리지 않으니 `dialogue.yml` 을 넣어 줄 것.
5. 모드와 플러그인은 같이 올린다 (프로토콜 2). 예전 모드로 접속하면 스토리를 보내지 않고 "모드를 업데이트해 주세요" 라고 알린다.

## 명령어

서버 (OP, 콘솔 가능):
- `/cutscene play <플레이어|@a> <id> [auto|skip|noskip]`, `/cutscene stop <플레이어>`, `/cutscene clear <플레이어>` (재생 중·대기 중인 스토리 모두 취소), `/cutscene reload` (config + 대화 파일 다시 읽기)
- `/storydialogue <플레이어|@a> <대화id> [replay]` (별칭 `/스토리대화`), `/storydialogue stop <플레이어>`
- `/storydialogue ledger <플레이어> [대화id]` (진행·완료 기록 보기), `/storydialogue reset <플레이어> <대화id|all>` (완료 기록 지우기 — 다시 열면 보상도 다시 나감)
- 모두 권한 `chacademy.story.admin` (기본 OP)
- `/affinity <플레이어> [npc] [set|add] [값]` (별칭 `/호감도`)

클라 (혼자 테스트, 서버 명령은 실행 안 됨):
- `/story cutscene <id>`, `/story dialogue <id>`, `/story list`, `/story resetseen`
- 예전 `/ccs play <id>` 도 그대로 됨

## 이어 붙이기 예

컷신이 끝나면 대화로: 플러그인 `config.yml`
```yaml
on-finish:
  ch1_ashen_night:
    - "storydialogue {player} ch1_wakeup"
```
대화가 끝나면 퀘스트로: `plugins/ChacademyStory/dialogues/ch1_wakeup/server_commands.yml`
```yaml
end:
  - "의뢰관리 event {player} ch1_wakeup_done"
```

## 알아 둘 동작

- 한 사람에게는 한 번에 하나만 재생된다. 보는 중에 온 컷신·대화는 순서대로 기다린다. 접속 직후에 온 명령도 모드 채널이 잡힐 때까지 (최대 15초) 기다렸다가 재생한다.
- 컷신도 대화도 끝날 때까지 서버 `progress.yml` 에 남는다. 접속이 끊기거나 화면이 중간에 닫히면 다시 보여 주고, `on-finish` / `end` 명령은 끝까지 본 다음 한 번만 실행된다.
- 이미 끝낸 대화를 다시 열라는 명령은 아무 일도 하지 않는다 (보상 중복 방지). 다시 보여 주려면 `replay`, 보상까지 다시 주려면 `reset`.
- 모드가 없는 사람은 스토리가 넘어가지 않는다 (config `finish-if-missing-mod: false` 가 기본. 예전 config 에 `true` 로 남아 있으면 그대로 `true` 로 동작하니 확인할 것).
- 보는 동안 플레이어는 서버에서 보호된다 (피해·몹·배고픔 없음, 제자리 고정. config `protect-during-story`).
- `progress.yml`, `affinity.yml` 은 서버마다 따로다. 서버가 여러 대면 보상을 주는 스토리 명령은 한 서버에서만 실행되게 할 것.
- 자세한 규칙과 config 항목: `FORMAT.md` 의 "서버 검증 · 기록 · 보호", 플러그인 `config.yml` 의 주석.

## 다른 플러그인에서 듣기

`kr.chacademy.storyplugin.StoryEvents` 의 `CutsceneFinish`, `DialogueChoice`, `DialogueFinish` 이벤트.

## 빌드

`build-auto.bat` 더블클릭 → `build-log.txt` 끝에 `ALL_DONE` 이면 성공 (모드·플러그인 테스트까지 통과하고 jar 두 개가 `out/` 에 복사됐을 때만 `ALL_DONE`).
