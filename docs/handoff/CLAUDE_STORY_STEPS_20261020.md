# 스토리 진행 단계 모듈 (조건 → 호출 → 다음 대화) + 퀘스트 HUD · 목표 포탈 · NPC 징표

작성: Claude, 2026-10-20 · **빌드 안 함** (이 환경에서 Paper·Fabric 저장소에 접근 불가. 서버 쪽 새 클래스는 API 스텁으로 javac 컴파일·단위 테스트만 확인, 클라 쪽은 미컴파일)
브랜치 `claude/story-steps-20261020` ← `codex/followups-integrated-20261020-task12` @ 0cd1454

## 바뀐 것

| 모듈 | 파일 | 내용 |
|---|---|---|
| ChacademyStory 플러그인 | `QuestDefs` `QuestProgress` `QuestCodec` `QuestEngine` (새 파일) | 단계 정의(steps.yml) · 진행(quest-progress.yml) · HUD 패킷 · 조건/호출/명령 |
| | `StoryPlugin` | QuestEngine 켜기/끄기, 완료 신호 두 경로를 한 곳(`quests.dialogueFinished`)으로, 컷신 완료, 중지/대기열비우기/세션 취소 → `dialogueAborted`, 스토리 단계 대화는 끝 명령이 없어도 빈 명령 세션을 연다 |
| | `resources/steps.yml` `config.yml`(`quest-server-id: school`) `plugin.yml`(softdepend Citizens) | |
| | 테스트 `QuestDefsTest` | 정의·진행 왕복 |
| 스토리 모드 (Fabric) | `story/quest/*` (새 파일) `ChacademyCutsceneClient`(install 한 줄) | HUD · 포탈 · 징표, 텍스처 `assets/chaca_story/textures/quest/*.png` 9개 |
| ChacaNPC | `NpcManager.onRightClick` | `@EventHandler(ignoreCancelled = true)` 한 줄 |

MagicCodex 본체(magic-codex-fabric / paper)는 건드리지 않았다. 버전: 플러그인 `0.1.4-quest.1`, 모드 `0.1.4-quest.1`.

## 빌드할 때 볼 것 (컴파일 위험 순서)

1. **클라 `QuestWorld` / `QuestHud` / `QuestPackets` 는 컴파일 확인을 못 했다.** 쓴 API: `StreamCodec.of(encoder, decoder)`, `RenderType.entityTranslucentEmissive`, `VertexConsumer.addVertex(Pose,…).setColor(int).setUv.setOverlay.setLight.setNormal(Pose,…)`, `Camera.rotation()`, `ScreenEvents.AFTER_INIT` + `ScreenMouseEvents.allowMouseClick`, `HudRenderCallback(GuiGraphics, DeltaTracker)`, `WorldRenderEvents.AFTER_ENTITIES`. `QuestArt` 은 chacademy-motion `HudArt` 를 그대로 복사(네임스페이스·경로만 바꿈) + `textLeft`(외곽선 글자) 추가.
2. 서버 `QuestEngine` 은 Citizens 를 **리플렉션**으로 연결한다 (`registerEvent(NPCRightClickEvent, LOWEST)`). 컴파일 의존성 추가 없음.
3. `ItemMeta.hasItemName()/itemName()` (Paper 1.20.5+) 사용.

## 동작 요약

- 흐름: 앞 단계 대화 완료 → 단계 시작 → 조건 다 채움 = **준비 완료(ready)** → 호출 → 대화(+컷신) → 완료 신호가 기다리던 것과 같을 때만 다음 단계.
- 조건: 아이템(보유, 버리면 다시 미충족) · 제출(호출 때 걷음, 걷은 뒤엔 완료로 남음 `submitted`) · 처치 · 블록 상호작용 · 대화완료 · 신호.
- 호출: 자동(지연 초) · NPC클릭(서버 ID + Citizens 번호) · 위치(반경 최소 1.0) · 컷신 시청 완료 · 관리자 강제 진행.
- **NPC 클릭 우선순위**: 그 유저의 현재 단계 호출 NPC이고 준비 완료면 LOWEST 에서 클릭을 취소하고 스토리를 띄운다 → ChacaNPC(선물·고정대화·AI)·Bridge 상점·의뢰 클릭 집계 모두 건너뜀 (의도: 퀘스트 우선, 오너 결정). 준비 전이면 취소하지 않고 할 일 알림만.
- 중복 방지: 호출 후 PLAYING(기다리는 대화/컷신 `waiting`) 동안 재호출 없음 (연속 클릭·포탈 재진입). 대화를 못 열면 3초 뒤 다시 호출 가능. 접속 시 대화 대기 중이었으면 ACTIVE 로 되돌림(제출 기록 유지), 컷신은 스토리 대기열이 이어 재생. 30분 넘게 안 끝나면 되돌림.
- 위치 판정: 블록 이동 + 텔레포트 직후 + 0.5초 주기(이미 서 있는 상태에서 조건을 채운 경우·같은 블록 안 경계).
- 완료 신호 경로: (1) 서버 흐름 대화 `Effect.DIALOGUE_END` (2) 로컬 세션 `DIALOGUE_SESSION_SIGNAL action 1` — **세션 토큰·ready·ending 이 맞을 때만** (finish 전에 검사) (3) 컷신 `Effect.CUTSCENE_END` (서버가 재생시킨 요청만).
- 진행 기준 서버 = 학교 서버 하나 (quest-progress.yml 은 로컬 파일, 다른 서버와 공유하지 않음).

## 패킷 (새 채널, 서버 → 클라만)

- `chacademy:quest_step` — 단계가 바뀔 때·접속·월드 이동 때. session, step, run, 장, 설명, ready, 조건[label,cur,req], 대상 종류(0 없음/1 위치/2 NPC), 월드 키, x y z, 반경, NPC 엔티티 UUID.
- `chacademy:quest_update` — 같은 단계 안에서 진행도·ready 가 바뀔 때만 (값 비교 후, 같은 틱 합침). session·step·run 이 맞을 때만 클라가 적용.
- 애니메이션은 클라 시계. 서버는 0.5초마다 값만 비교하고 바뀔 때만 보낸다.

## 명령어 (MagicCodexBridge 허브, OP)

`/대화관리` 대단원에 붙임 (`/스토리관리` 대단원은 허브 ROOTS 에 없어서).

```
/대화관리 단계 목록 | 시작설정 <단계> [대화완료 <대화>] | 지정끝 | 미리보기끝
/대화관리 단계 <ID> 만들기 | 삭제 확인 | 정보 | 이동 | 미리보기
/대화관리 단계 <ID> 장|설명 <문구> · 대화|컷신 <ID|없음> · 다음 <단계|없음>
/대화관리 단계 <ID> 조건 추가 <아이템|제출|처치|상호작용|대화완료|신호> <대상> <개수> [표시글]
/대화관리 단계 <ID> 조건 삭제|숨김|블록지정 <번호> · 조건 목록
/대화관리 단계 <ID> 호출 없음 | 자동 [지연초] | NPC클릭 (30초 안에 클릭) | NPC <번호> | 위치 [반경] (내가 선 블록) | 컷신 <ID>
/대화관리 신호 <닉네임> <키> [개수]          (콘솔 가능 — Skript·다른 플러그인 연결용)
/유저관리 <닉네임> 스토리진행 보기 | 다음 | 단계 <ID> | 초기화 확인
```

아이템 대상 `손` = 들고 있는 아이템(재료|이름). 위치·NPC 호출을 정하면 운영자 화면에 바로 미리보기(준비 완료 상태)로 보인다.

## 배포 (학교 서버 + 클라)

1. ChacademyStory 플러그인 jar + 스토리 모드 jar 같이 (모드 없는 유저는 HUD만 안 보이고 진행은 됨).
2. ChacaNPC jar (ignoreCancelled 한 줄).
3. 학교 서버 `plugins/ChacademyStory/steps.yml` — 처음 켜면 jar 안의 1장 기본값이 생긴다 (chacademi-server `story/ch1/서버/steps.yml` 과 같음).
4. chacademi-server 저장소 `claude/story-ch1-20261020` 브랜치의 `story/ch1/서버/` 새 버전:
   - `server_command_1장_추가분.yml`: `스토리완료`·`storydialogue`·`cutscene play` 줄 삭제(같이 두면 두 번 열림), 분류 결과 대화 끝에 `대화관리 신호 {player} 기숙사배정`.
   - `story_ch1.sk`: 장소 트리거·준비물 확인·다음 목표 안내 삭제, **기숙사 분류만 남김** (`storydialogue` → `대화관리 재생 … 대화 …`). `/스토리초기화` → `/분류초기화`.
   - `config.yml` on-finish 의 `스토리이어 {player} ch1-19 ch1-20` 삭제.
5. 운영 Skript 에 예전 story_ch1.sk(구역 트리거)가 올라가 있으면 새 것으로 교체 — 둘이 동시에 대화를 띄우면 안 된다.

## 남은 것 (이번 범위 밖)

- 교화 성공·낚시 성공·연금술 성공·내 방 열기 **성공 신호 연결**: 지금은 `신호` 조건 + `/대화관리 신호` 만 있다. 각 기능 소유 플러그인에서 저장 성공 뒤 이 명령(또는 공개 API) 호출을 넣어야 한다 (GPT 검토 의견 §1의 발행 시점 그대로).
- 길잡이 별 마법(방향 화살표, 10블록 안에서 사라짐) — 오너가 나중으로 미룸.
- NPC·포탈 위치는 아직 비어 있음 (NPC 배치 전). 오너가 배치 후 명령으로 지정.
