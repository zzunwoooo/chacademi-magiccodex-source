# 차카데미 컷신 파일 형식 (format 1)

컷신 하나 = 폴더 하나.

```
.minecraft/config/chaca_cutscene/
  ch1_ashen_night/          ← 폴더 이름 = 컷신 id
    cutscene.yml
    s1_1.png
    s1_2.png
    s2_1.png
  _seen.txt                 ← 이미 본 컷신 목록 (모드가 자동으로 관리)
```

편집기에서 "zip으로 내보내기"를 누르면 이 폴더 구조 그대로 zip이 만들어진다.
zip을 `config/chaca_cutscene/` 안에 풀면 끝. 이미지는 편집기가 PNG로 바꿔서 넣어 준다.

## cutscene.yml

```yaml
format: 1
id: ch1_ashen_night        # 소문자, 숫자, _, - 만. 폴더 이름과 같아야 함
title: 잿빛 밤 동화         # 관리용 이름 (화면에 안 나옴)
font: medium               # 자막 폰트 굵기: light | medium | bold
font_size: 1.0             # 자막 크기 배율 (0.4 ~ 2.5). 1.0 = 아래 띠 높이의 약 1/3
text_color: "#EFE3C8"      # 자막 색
text_x: 0.5                # 자막 가운데의 가로 위치 (0 왼쪽 ~ 1 오른쪽)
text_y: auto               # 세로 위치. auto = 아래 띠 가운데, 숫자 = 화면 높이 비율 (0 위 ~ 1 아래)
text_shadow: false         # 그림 위에 글자를 올릴 때 켜면 잘 보임
italic: true               # 자막 기울임
prefix: "- "               # 모든 자막 앞에 자동으로 붙는 글자
type_speed: 0.06           # 글자 하나가 써지는 시간 (초)
letterbox: 0.14            # 위아래 검은 띠 높이 (화면 높이 대비 비율)
letterbox_time: 1.6        # 띠가 내려오는 시간 (초)
end_fade: 1.2              # 마지막 암전 시간 (초)
type_sound: default        # 글자 써질 때 소리. default = ChacaNPC 대화창과 같은 소리 | none | 마인크래프트 소리 id
type_sound_volume: 0.5     # 0.5 = 대화창과 같은 크기. 1.0 이면 두 배
bgm: bgm.ogg               # 컷신 폴더 안 배경음 (ogg만). 비우면 없음
bgm_volume: 0.6            # 배경음 크기 (0 ~ 1). 마인크래프트 [주 음량]만 곱해짐 ([음악]을 꺼도 들림)
bgm_start: 0               # 곡의 몇 초 지점부터 틀지 (초)
bgm_fade_in: 3.0           # 처음에 소리가 커지는 시간 (초)
bgm_fade_out: 4.0          # 끝나기 전 소리가 줄어드는 시간 (초)
scenes:
  - images: [s1_1.png, s1_2.png, s1_3.png]   # 2장 이상이면 fps 속도로 반복 (불꽃 흔들림 등)
    fps: 6
    duration: 8.0          # 이 장면의 길이 (초)
    zoom:
      x: 0.62              # 줌 중심 (이미지 가로 비율 0~1)
      y: 0.45              # 줌 중심 (이미지 세로 비율 0~1)
      from: 1.0            # 시작 배율
      to: 1.08             # 끝 배율
    transition: ink        # 앞 장면에서 넘어오는 방식: ink | fade | cut
    transition_time: 1.4
    lines:
      - text: 백 년 전, 잿빛 밤.
        start: 1.7         # 장면 시작 기준 (초)
        end: 4.2
```

## 시간 규칙

- 장면은 순서대로 이어진다. 전체 길이 = 모든 장면 duration 합 + end_fade.
- 전환(transition)은 장면이 시작될 때 transition_time 동안 앞 장면 위로 덮인다. 첫 장면은 검은 화면에서 전환된다.
- 줌은 장면 길이 전체에 걸쳐 부드럽게(ease-in-out) from → to로 움직인다.
- 자막은 start에 나타나 type_speed마다 한 글자씩 써지고, end 직전 0.3초 동안 흐려진다.
- 2배속이면 모든 시간이 절반으로 줄어든다.

## 소리

- 타자기 소리는 ChacaNPC 대화창과 같다 (버튼 클릭음, 높이 1.8). 공백에서는 소리가 안 나고, 45ms보다 촘촘하면 건너뛴다.
- 배경음은 bgm_start 지점부터 한 번만 재생된다 (반복 없음). 컷신이 끝나는 시점에 맞춰 bgm_fade_out 동안 천천히 줄어든다.
- 건너뛰거나 서버가 멈추면 1.5초 동안 줄어들며 꺼진다. 컷신 도중에는 바닐라 배경음악이 멈춘다.

## 건너뛰기

- 처음 보는 컷신은 건너뛸 수 없다.
- 끝까지 본 컷신은 `_seen.txt`에 기록되고, 다음부터는 스페이스바를 2초 꾹 눌러 건너뛸 수 있다 (원이 차오름).
- 서버 명령어에서 `skip`/`noskip`을 주면 이 규칙을 덮어쓴다.
- 2배속 버튼(>>)은 항상 있다.

---

# 대화 파일 형식 (format 1)

대화 하나 = 폴더 하나. 대화 편집기에서 "zip으로 내보내기"를 누르면 이 구조로 나온다.

```
.minecraft/config/chaca_dialogue/
  ch1_wakeup/                 ← 폴더 이름 = 대화 id
    dialogue.yml              ← 대사, 선택지, 인물, 표정 (클라가 읽음)
    teacher_1.png ...         ← 표정 그림 (PNG)
    server_commands.yml       ← 서버용. 클라에서는 무시

서버: plugins/ChacademyStory/dialogues/ch1_wakeup.yml   ← server_commands.yml 을 이 이름으로
```

## dialogue.yml

```yaml
format: 1
id: ch1_wakeup
title: "1장 · 깨어남"
start: wake                    # 시작 장면
dim: 0.35                      # 뒤 화면 어둡게 (0~0.9)
type_speed: 0.03               # 글자 하나 나오는 시간 (초)
type_sound: default            # default (대화창 소리) | none
speakers:
  me: {name: "{player}"}       # "나". 그림은 ChacaPortrait 로 만든 내 AI 일러스트가 자동으로 나옴
  teacher:
    name: "마중 선생님"
    npc: teacher               # NPC id (호감도용, 선택). ChacaNPC / MagicCodex 와 같은 id
    portraits:                 # 표정 이름: 그림. 첫 번째가 기본
      기본: teacher_1.png
      웃음: teacher_2.png
scenes:
  wake:
    redirect:                  # 들어올 때 호감도가 높으면 다른 장면으로 (위에서부터)
      - {npc: teacher, min: 60, to: wake_friend}
    lines:                     # 한 줄 = 클릭 한 번. face 비우면 앞 표정 유지
      - {who: me, text: "으으… 여기는…?"}
      - {who: teacher, face: 웃음, text: "{player} 씨 맞소?"}
    choices:                   # 최대 6개
      - text: "괜찮아요"
        to: fine               # 비우면 대화 끝
        affinity: [{npc: teacher, add: 2}, {npc: rin, add: -1}]   # 고르면 호감도 변화 (대화 안 계산용, 실제 적용은 서버 파일)
        need: {npc: teacher, min: 50}        # 이 호감도 이상일 때만 보임
        event: c_wake_1        # 서버 명령 연결용 이름
    next: ""                   # 선택지가 없을 때 다음 장면 (비우면 대화 끝)
```

### 호감도 바꾸기

편집기에서 네 곳에 "+ 호감도"로 넣을 수 있다. 인물마다 한 줄, 올리면 +, 내리면 -.

| 언제 | 편집기 위치 | 한 번에 |
|---|---|---|
| 선택지를 고르면 | 선택지 ⚙ → "고르면 호감도 바꾸기" (여러 명 가능) | ±20 |
| 장면에 들어오면 | 장면 고급 → "이 장면에 들어오면 호감도 바꾸기" (대화마다 한 번) | ±20 |
| 이 장면에서 대화가 끝나면 | 장면 고급 → "여기서 대화가 끝나면 호감도 바꾸기" (결말 보상) | ±50 |
| 대화가 끝나면 항상 | 맨 아래 "대화가 끝나면 항상 바꿀 호감도" | ±50 |

- 클라 `dialogue.yml` 에는 선택지·장면의 `affinity: [{npc, add}, ...]` 가 들어가고 (대화 안에서 조건·갈림길 계산용),
  **실제로 점수를 바꾸는 값은 서버 `dialogues/<id>.yml` 의 `affinity:`** 이다. 클라 파일을 고쳐도 점수는 안 바뀐다.

```yaml
affinity:
  end: {teacher: 1}              # 대화가 끝나면 항상
  endings:
    go_friend: {teacher: 5}      # 이 장면에서 끝났을 때만
  events:                        # 선택지(c_장면_번호) / 장면 들어옴(e_장면) — 대화마다 한 번
    c_wake_1: {teacher: 2}
    c_wake_2: {teacher: -1, rin: 1}
```

### 닫기와 이어서 보기

- 스토리 대화는 마지막 대사(또는 선택지로 이어진 마지막 장면)가 끝날 때까지 닫을 수 없다. ESC 는 무시되고, 다른 화면이 덮어도 그 화면이 닫히면 대화가 다시 열린다.
- 서버가 연 대화는 보고 있던 장면·대사 번호를 서버 `progress.yml` 에 저장한다. 접속이 끊기거나 서버가 재시작돼도 다시 접속하면 그 대사부터 이어서 열린다.
- 이미 실행된 이벤트(선택지 명령·호감도, 장면 들어옴 호감도)는 이어서 볼 때 다시 실행되지 않는다.
- 강제로 끝내기: `/storydialogue stop <플레이어>` (끝 명령은 실행되지 않음).

### 대사에 쓸 수 있는 글자

| 글자 | 바뀌는 값 |
|---|---|
| `{player}` | MagicCodex 에서 정한 **한글 닉네임** (칭호 없이). 없으면 마인크래프트 닉네임 |
| `{account}` | 마인크래프트 닉네임 |
| `%플레이스홀더%` | 서버 config `dialogue-placeholders` 에 적어 둔 PlaceholderAPI 값. 예: `%chacademiatitle_full%` (칭호 붙은 이름) |

이름표, 대사, 선택지 글 어디서나 쓸 수 있다. 서버 없이 `/story dialogue` 로 혼자 열 때는 `{player}` 만 이 PC 의 MagicCodex 모드에서 가져온다.

### "나"의 그림

- 화자 id 가 `me` 이면 (편집기의 "나 (플레이어)") MagicCodex UI 모드가 받아 둔 **ChacaPortrait 내 일러스트**를 MagicCodex 대화창의 "내 차례"와 같은 자리·크기로 그린다.
- 일러스트를 서버에 따로 요청하거나 새로 만들지 않고, MagicCodex 가 이미 가진 것을 그리기만 한다 (ChacaPortrait 와 충돌 없음).
- 아직 일러스트가 없거나 MagicCodex 모드가 없으면, `me` 에 넣은 표정 그림 → 그것도 없으면 앞 사람 그림을 그대로 둔다.

## server_commands.yml (서버)

```yaml
npcs: [teacher]      # 이 대화에 나오는 NPC (편집기가 자동으로 적음). 대화를 열 때 이 NPC 들의 호감도를 보냄
end:                 # 어느 결말이든 대화가 끝나면
  - "storydialogue {player} ch1_school"
endings:             # 이 장면에서 끝났을 때만
  wake_friend:
    - "say {player} 님은 선생님과 친해졌습니다"
events:              # 이 이벤트가 있는 선택지를 고르면 (대화마다 한 번)
  c_wake_1:
    - "give {player} bread 1"
```

쓸 수 있는 글자: `{player}` 마인크래프트 닉네임 (명령어 대상용), `{nickname}` 한글 닉네임, `{uuid}`, `{id}` 대화 id, `{scene}` 마지막 장면 id, `{event}` 이벤트 이름.

## 호감도

- **MagicCodexBridge 가 켜져 있으면 MagicCodex 가 저장한다.** ChacaNPC AI 대화와 같은 점수이고, MagicCodex 의 하트 단계 상한(하트 이벤트 전에는 20×(단계+1)점에서 멈춤)이 그대로 적용된다. 출처 이름은 `dialogue`.
- MagicCodexBridge 가 없으면 이 플러그인이 `plugins/ChacademyStory/affinity.yml` 에 따로 저장한다 (기록이 없으면 25).
- 대화를 열 때 서버가 `npcs` 에 적힌 NPC 의 현재 호감도를 모드로 보내 준다. 모드에 값이 없으면 25 로 본다.
- 실제 호감도는 서버 파일에 선언한 이벤트·결말·끝 값만 적용하며 항목마다 ±100으로 제한한다. MagicCodex 연결 시 그 서비스의 하트 단계·출처별 일일 한도가 추가로 적용된다. 구형 클라 값 경로는 제거했으며 affinity-max-per-dialogue는 더 이상 사용하지 않는다.
- 명령어: `/affinity <플레이어> [npc] [set|add] [값]` (별칭 `/호감도`)


## 통합 후 안전 동작 (2026-10-10)

- 서버에 선언되지 않은 이벤트 및 클라 패킷의 npc/add 값은 무시한다.
- 이벤트의 fired 기록을 progress.yml에 원자적으로 저장한 뒤 효과를 실행한다. 저장 실패 시 효과를 실행하지 않고 재시도를 허용한다.
- 완료 및 관리자 중단도 진행 제거를 먼저 영속화한다. 시작 진행 저장이 실패하면 대화 창을 열지 않는다.
- 이어보기는 저장된 장면/대사로 직접 복원하며 장면 진입 리다이렉트·호감도·이벤트를 다시 실행하지 않는다.
- 클라 대화 파일이 없거나 손상되어도 완료 신호를 보내지 않는다. 서버의 대기는 남으며 파일 준비 후 재접속하거나 관리자가 중단할 수 있다.
- 이 프로토콜은 서버에 선언된 효과의 허용 목록과 중복 방지다. 대화 그래프/선택 조건 전체는 클라 파일에 있으므로 서버가 실제 읽기 순서나 올바른 선택 갈림길까지 검증하지는 않는다.
- 진행 YAML과 외부 명령/호감도 서비스는 하나의 트랜잭션이 아니다. 기록 직후 비정상 종료 또는 외부 효과 실패 시 효과가 누락될 수 있다. 중복 방지 기록이 있는 효과를 자동 재실행하지 않는다.
- 대사 위치는 주기적으로 저장되며 정상 접속 종료/서버 종료 시 저장한다. 강제 프로세스 종료에서는 마지막 저장 이후 위치가 일부 되돌아갈 수 있다.
