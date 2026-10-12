# ChacaGimmick — 던전 기믹 플러그인

던전 수수께끼용 기믹 모델(`dungeon-gimmicks/` 의 bbmodel)을 **MythicMobs 없이** 직접 소환하고, 퍼즐로 연결한다.
모델·애니메이션·크기는 **ModelEngine 4** 가 구현하고, ItemsAdder 는 ModelEngine 이 만든 리소스팩을 합쳐서 보내는 역할만 한다.
이 플러그인은 소환·저장·애니메이션 지시·클릭/밟기/마법 판정·퍼즐 상태를 맡는다.

## 설치

1. `chaca-gimmick-<버전>.jar` → `plugins/`
2. 모델 등록 (ModelEngine)
   - `dungeon-gimmicks/models/<id>/<id>.bbmodel` 을 `plugins/ModelEngine/blueprints/` 에 넣고 `/meg reload` (blueprint 이름 = 모델 id).
   - ModelEngine 이 만든 리소스팩(`plugins/ModelEngine/resource pack`)을 ItemsAdder 리소스팩에 합쳐서 보낸다
     (ItemsAdder 설정의 다른 플러그인 리소스팩 병합 목록에 ModelEngine 폴더 추가 → `/iazip`).
   - `config.yml` 기본값 `renderer: modelengine`, `model-prefix: ""` 그대로.
3. 마법 연동을 쓰려면 `ChAcademiaMagicMythic`(magic-mythic-paper) 도 설치. 없으면 클릭·밟기·아이템만 동작.
4. 서버 시작 → `plugins/ChacaGimmick/` 에 `config.yml`, `models.yml`, `puzzles.yml` 생성.

## 기본 사용

```
/기믹 모델                 모델 목록
/기믹 소환 gm_fire_brazier  바라보는 블록 위에 소환 (모델이 나를 바라봄)
/기믹 도구                 디버그 도구 받기
/기믹 노드 example_fire_gate brazier_left   선택한 기믹을 퍼즐 노드에 연결
/기믹 씌우기 gm_element_fragment_fire 0.6   바라보는 몹에 모델 씌우기 (벗기기: /기믹 벗기기)
/기믹 퍼즐 example_fire_gate                 노드 상태 보기
/기믹 신호 <퍼즐> <노드> 켜기 · /기믹 초기화 <퍼즐> · /기믹 리로드
```

### 디버그 도구 (블레이즈 막대)

모델을 클릭하거나, 모델을 바라보고 허공을 클릭한다. 마지막으로 고른 모델이 기억된다.

| 조작 | 동작 |
| --- | --- |
| 우클릭 / 좌클릭 | 현재 모드에서 + / − |
| 웅크리고 우클릭 / 좌클릭 | 다음 / 이전 모드 |

모드: **정보**(위치·방향·크기·퍼즐·상태) → **회전**(15°) → **크기**(0.1) → **이동**(바라보는 방향 축으로 0.25블록) → **높이**(0.125블록) → **애니메이션**(목록 순서대로 재생) → **삭제**(우클릭 두 번)

정확한 값은 `/기믹 설정 <방향|크기|높이|위치> <값>`.

## 퍼즐

`puzzles.yml` 첫 부분에 옵션 설명과 예시 5개(화로 두 개로 문 열기, 종 순서, 협동 공명석, 레버+압력판, 조각 모으기+텔레포트)가 있다.

- 트리거: `magic:<속성>`/`magic:any`(마법이 닿음), `interact`(우클릭), `attack`(좌클릭), `stand`(밟기)
- 출력 노드: `when: "all(a,b)" / any / not / count / seq(순서) / sync(동시)` — 식은 따옴표로
- `solid`: 문·다리·석주처럼 막혀야 하는 곳에 투명 방벽 블록을 세운다 (상태에 따라 생기고 사라짐)
- `teleport`: 켜진 게이트에 들어가거나 우클릭하면 연결된 게이트 앞으로 이동
- `collect`: 아이템을 들고 우클릭해서 넣기 (ItemsAdder id 또는 `minecraft:...`)
- **주의:** YAML 에서 `on:`/`off:` 는 참/거짓으로 읽혀서 키 이름은 `turn-on`/`turn-off` 다.

상태는 `puzzle-state.yml` 에 저장되어 재시작 후에도 열린 문은 열린 채로 나온다 (`persist: false` 로 끔).

## 저장 파일

| 파일 | 내용 |
| --- | --- |
| `gimmicks.yml` | 소환한 기믹 (모델·월드·좌표·방향·크기·퍼즐·노드). 엔티티는 저장하지 않고 청크 로드 때 다시 만든다 |
| `puzzle-state.yml` | 퍼즐 노드 상태, 넣은 아이템 |
| `solids.yml` | 세워 둔 방벽 블록 기록 (지워진 기믹의 방벽 정리용) |
| 몹 PDC | 씌운 모델 정보. 몹이 다시 로드되면 자동으로 다시 씌운다 |

## 알아둘 것

- 애니메이션: 상태 애니메이션(`idle`/`activate`→`active`/`deactivate`)은 하나씩 바꿔 재생하고, 조각 제단 `insert_*` 처럼 쌓이는 연출은 ModelEngine 레이어로 겹쳐 재생한다. 재시작 후에도 넣은 조각 연출이 다시 깔린다.
- 크기는 ModelEngine `setScale` 로 바뀐다.
- 클릭 판정은 모델 크기(`models.yml` 의 width/height × 크기)의 Interaction 엔티티로 한다. 방향을 돌려도 판정 상자는 회전하지 않는다.
- 마법 판정: 투사체(chabolt)는 이동 경로마다, 즉시 타격(chahit)은 맞은 지점에서, 지대(chazone)는 매 주기마다 주변 기믹에 신호를 준다. 투사체는 기믹에 닿으면 멈춘다 (`magic-blocks-bolts`).
- 대안: ItemsAdder 커스텀 엔티티로 그리는 렌더러(`renderer: itemsadder`)도 남겨 두었지만, 크기 조절·애니메이션 겹치기가 안 되고 bbmodel 변환 작업이 필요해 기본으로 쓰지 않는다.
- 아이템 넣기(`collect`)의 아이템 id 는 ItemsAdder 아이템이면 `네임스페이스:id` 로 인식한다.
