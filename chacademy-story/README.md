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
2. 플러그인: `out/chacademy-story-plugin-0.1.0.jar` → 서버 `plugins/` (예전 ChacademyCutscene 플러그인은 지울 것)
3. 컷신 zip → `.minecraft/config/chaca_cutscene/` 에 풀기
4. 대화 zip → `.minecraft/config/chaca_dialogue/` 에 풀기, 안의 `server_commands.yml` 은 서버 `plugins/ChacademyStory/dialogues/<대화id>.yml` 로

## 명령어

서버 (OP, 콘솔 가능):
- `/cutscene play <플레이어|@a> <id> [auto|skip|noskip]`, `/cutscene stop <플레이어>`, `/cutscene reload`
- `/storydialogue <플레이어|@a> <대화id>` (별칭 `/스토리대화`), `/storydialogue stop <플레이어>`
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
대화가 끝나면 퀘스트로: `plugins/ChacademyStory/dialogues/ch1_wakeup.yml`
```yaml
end:
  - "의뢰관리 event {player} ch1_wakeup_done"
```

## 다른 플러그인에서 듣기

`kr.chacademy.storyplugin.StoryEvents` 의 `CutsceneFinish`, `DialogueChoice`, `DialogueFinish` 이벤트.

## 빌드

`build-auto.bat` 더블클릭 → `build-log.txt` 끝에 `ALL_DONE` 이면 성공.
