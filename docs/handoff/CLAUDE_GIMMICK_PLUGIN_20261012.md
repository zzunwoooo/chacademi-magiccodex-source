# Claude 인계: 던전 기믹 플러그인 ChacaGimmick (2026-10-12)

- 브랜치: `claude/gimmick-plugin-20261012`
- 기준: `claude/dungeon-gimmick-models-20261011` @ `81de103` (마법 미몹 + 기믹 모델 브랜치 포함)
- **빌드·서버 설치·게임 실행은 하지 않았다.** Paper 1.21.4 API 소스 + adventure 4.18 소스 + (ModelEngine·MythicMobs 스텁)으로 javac 타입 검사만 통과. 퍼즐 엔진 단위 테스트 7개는 실행해서 통과.

사용자 요청: 던전 기믹 연동 플러그인. bbmodel 을 MythicMobs 없이 직접 소환(독립 소환, 몹에 씌우기 포함), 클릭으로 크기·방향 조절하는 디버그 도구. 리소스는 ItemsAdder 가 보냄.

## 0. 주의사항

1. **MagicCodex(Bridge) 코드는 건드리지 않았다.** 마법 연동은 우리 애드온 `magic-mythic-paper` 에 이벤트를 추가해서 했다.
2. `magic-mythic-paper` 변경: `MagicImpactEvent`(공개 이벤트) 추가, `chahit`/`chabolt`/`chazone` 이 맞은 위치·속성으로 이벤트를 쏜다. `tools/spells.py` 가 각 줄에 `element=<카탈로그 속성>` 을 넣도록 바뀌었고 스킬 YAML 을 다시 생성했다. 듣는 플러그인이 없으면 이벤트를 만들지 않는다.
3. 마법 미몹 스킬이 실제로 돌려면 여전히 Bridge 수정안(`docs/handoff/proposals/BRIDGE_CATALOG_GAMEPLAY_20261011.patch`, GPT 승인 필요)이 필요하다. 그 전에는 기믹이 마법에 반응하지 않고, 클릭·밟기·아이템 퍼즐만 동작한다.
4. 새 모듈 `gimmick-paper` 를 `settings.gradle`, `Build-Server.ps1` 에 추가했다. `compileOnly project(':magic-mythic-paper')` 와 ModelEngine jar(`../plugins/ModelEngine-4.0.8.jar`) 를 쓴다. ItemsAdder 는 리플렉션 (chaca-portrait 와 같은 방식).
5. ItemsAdder 렌더러로 쓰려면 각 bbmodel 을 Blockbench ItemsAdder 확장으로 변환·내보내기 해야 한다 (사용자 작업). 자세한 건 `gimmick-paper/README.md`.

## 1. 구성

| 파일 | 역할 |
| --- | --- |
| `GimmickPlugin` | 시작/종료, 렌더러 선택(`renderer: itemsadder|modelengine`), 리로드 |
| `ItemsAdderRenderer` | `CustomEntity.spawn/convert/playAnimation/stopAnimation/teleport/destroy` (리플렉션, API 4.x 문서 기준) |
| `ModelEngineRenderer` | `ModelEngineAPI.createActiveModel/createModeledEntity` 등 (Bridge TamingModelFx 와 같은 호출) |
| `GimmickWorld` | `gimmicks.yml` 저장, 청크 로드/언로드 때 엔티티 생성/제거(비영구 엔티티), 몹에 씌우기(PDC), 클릭용 Interaction, 바라보는 기믹 찾기, 애니메이션 전환 |
| `Puzzles` + `puzzle/` | `puzzles.yml` 파싱, 퍼즐별 상태 기계, `puzzle-state.yml` 저장, 상태 변화 → 애니메이션·방벽·명령·문구 |
| `Solids` | 문·다리용 투명 방벽 블록 (공기에만 놓고 방벽일 때만 지움, `solids.yml` 기록) |
| `Inputs` | 우클릭(아이템 넣기/포탈/interact), 좌클릭(attack), 밟기·포탈 진입 검사 |
| `DebugTool` | 정보·회전·크기·이동·높이·애니메이션·삭제 모드 |
| `MagicHook` | `MagicImpactEvent` → `magic:<속성>` 트리거 (magic-mythic 이 켜져 있을 때만 등록) |
| `GimmickCommand` | `/기믹` (`/gimmick`) |

## 2. 빌드 때 확인할 API

| API | 비고 |
| --- | --- |
| ItemsAdder `dev.lone.itemsadder.api.CustomEntity` | 리플렉션. `spawn(String,Location,boolean,boolean,boolean)`, `convert(String,LivingEntity,boolean,boolean,boolean,boolean)` 시그니처가 서버 ItemsAdder 버전에 있는지 |
| ModelEngine `ModeledEntity#removeModel(String)`, `AnimationHandler#playAnimation(String,double,double,double,boolean)` | Bridge 코드와 같은 호출 |
| Paper `PrePlayerAttackEntityEvent`, `Interaction`, `Attribute.SCALE`, `EntitiesLoadEvent` | Paper 소스로 확인됨 |

## 3. 확인할 것 (실기)

1. ItemsAdder 로 변환한 모델 하나(`gm_fire_brazier`)를 `/기믹 소환` → 보이는지, `/기믹 도구` 애니메이션 모드로 `activate`·`active` 재생되는지.
2. 크기 모드: ItemsAdder 에서 `minecraft:scale` 로 뼈대가 같이 커지는지 (안 되면 README 의 대안).
3. 청크를 벗어났다 돌아왔을 때 모델이 하나만 있는지 (중복 생성 없음), 재시작 후 위치·방향·크기 유지.
4. `example_fire_gate`: 화로 두 개 연결 → `/기믹 신호` 로 켜서 문 방벽이 사라지는지 (마법 연동은 Bridge 수정안 적용 후).
5. `/기믹 씌우기` 한 몹이 재접속·청크 재로드 후에도 모델을 다시 쓰는지.

## 4. 테스트

- `gimmick-paper/src/test/.../PuzzleEngineTest` (식 파싱, 화로 2개, hold·쿨다운, 종 순서 실패 초기화, sync, 밟기+아이템 수집, 저장·불러오기) — 이 환경에서 실행해 7개 통과.
