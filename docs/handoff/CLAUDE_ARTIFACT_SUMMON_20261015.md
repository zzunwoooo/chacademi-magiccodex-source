# 유물함 소환 (아티팩트 뽑기) (Claude, 2026-10-15)

브랜치: `claude/artifact-summon-20261015`. 기준은 `claude/artifact-catalog-20261015` @ `c1c2c01`이고, 아티팩트 전체 목록·사용 키·원소 반응을 모두 포함합니다.

## 0. 먼저 읽을 것

- **빌드 안 됨.** 대신 아래만 확인했습니다.
  - `ArtifactProtocol`: javac 컴파일. `ArtifactProtocolTest` 4개 통과(새 테스트: 소환 프레임 왕복).
  - `ArtifactBridge`·`ItemsAdderItems`·`ArtifactGrants`: Bukkit·LuckPerms 스텁으로 javac 통과.
  - `SummonScene`(연출 타임라인, Minecraft 의존 없음): javac 통과. 같은 코드를 소프트웨어 렌더러로 돌려 등급별 영상을 만들어 확인했습니다(오너에게 `artifact_summon_preview.mp4` 전달).
  - Fabric 화면 `ArtifactSummonScreen`과 `HudTextureCache` 가산 합성은 **컴파일 확인을 못 했습니다.**
- **아이템애더 아이템 2개는 오너가 만듭니다:** `item:random_artifact`(소환 열쇠), `item:artifact_shard`(중복 보상 조각). 아이디는 `artifacts.yml`의 `summon:`에서 바꿀 수 있습니다.
- 프로토콜은 v2 그대로이고, 소환 프레임(타입 3)만 추가했습니다. 서버와 모드는 같이 배포해 주세요.

## 1. 오너 요청 / 결정

- 레퍼런스 6컷 흐름: 유물함 5개 → 하나 선택 → 그 상자만 개봉 → 결과 → 남은 상자는 등급만 잠깐 → 결과 카드.
- **[소환하기] 버튼을 눌러 아이템을 소모**하고, 소모 아이템이 무엇을 하는지 안내 문구를 화면에 표시합니다.
- 소모 아이템은 `item:random_artifact`(아이템애더).
- 확률은 등급 비율 **5:3:1:0.1** (일반 55% / 고급 33% / 유일 11% / 전설 1.1%)이고, 같은 등급 안에서는 균등하게 고릅니다.
- **중복이 나오면 `item:artifact_shard`를 우편으로 지급**합니다(나중에 조각 모아 선택 교환 예정).
- **창은 명령어로 엽니다:** `/아티팩트소환`(본인), `/아티팩트소환 <플레이어>`(관리자·콘솔·NPC 명령용).
- 팝업 UI는 기존 아티팩트 도감 UI(버튼·툴팁 판)를 재사용합니다. 사운드는 바닐라 사운드를 씁니다. 상자에 마우스를 올리면 빛납니다.

## 2. 구조

| 위치 | 파일 | 내용 |
|---|---|---|
| 공용 | `ArtifactProtocol` | 요청 `SUMMON_INFO`(4)·`SUMMON`(5). 응답 `Summon` 프레임: kind(OPEN/STATE/RESULT/REFUSED), 남은 열쇠 수, 아티팩트 id, 등급, 처음 얻은 것인지 여부, 조각 수, 나머지 4상자 표시용 등급, 안내문 |
| 서버 | `ArtifactBridge` | `summon:` 설정 읽기, 열쇠 개수 세기·소모(아이템애더 id 비교), 등급·아티팩트 추첨, 새것은 권한 지급, 중복은 조각 우편, 결과 직후 보유 목록 갱신, `/아티팩트소환`, 콘솔 로그 `ARTIFACT SUMMON player=… artifact=… grade=… new/duplicate summon=<uuid>` |
| 서버 | `ItemsAdderItems` (새 파일) | 아이템애더 리플렉션(`CustomStack.byItemStack/getNamespacedID/getInstance/getItemStack`) — 컴파일 의존 없음 |
| 서버 | `ArtifactGrants` (새 파일) | LuckPerms API로 `magiccodex.artifact.own.<id>` 권한 추가 |
| 서버 | `plugin.yml`, `artifacts.yml` | 명령 `아티팩트소환`(권한 `magiccodex.artifact.summon`, 기본 true. 다른 사람 창 열기는 `magiccodex.artifact.admin`), `summon:` 설정(item, item-name, cost, weights, decoy-weights, duplicate.item·amount·mail-title·mail-body) |
| 클라 | `SummonScene` (새 파일) | 연출 타임라인·그리기 순수 로직(아래 3장) |
| 클라 | `ArtifactSummonScreen` (새 파일) | 배경(창 가득), 1672×941 기준 좌표, 안내 패널·버튼·닫기, 입력, 사운드 |
| 클라 | `ArtifactClient` | 소환 프레임 수신(OPEN이면 창 열기), `summonInfo()`, `summon()` |
| 클라 | `HudTextureCache` + 셰이더 `core/hud_additive` | 가산 합성 그리기 `drawTextureAdditive`: 같은 텍스처에서 알파를 0으로 출력해, 기존 블렌드(ONE, ONE_MINUS_SRC_ALPHA)에서 빛이 더해짐 |
| 클라 | `textures/gui/summon/` | GPT 소환 PNG 가공본: 배경 1672×941, 상자 닫힘·열림(40% 축소, 바닥 기준 정렬), 등급 프레임 4장(384), 이펙트 조각 14장(시트를 실제 조각 경계로 잘라 따로 저장, 최대 256px). 총 약 3.4MB |

## 3. 연출 (rev 2: 등급 먼저 → 고른 상자 개봉)

오너 피드백(10-15): "등급이 먼저 보이고 선택한 상자를 오픈하는 순서로", "더 부드럽게", "이펙트 제대로", "옆 상자는 실제 확률과 다르게(전설 10%까지), 아깝다는 느낌".

1. **선택 전:** 닫힌 상자 5개가 둥실 떠 있습니다. 마우스를 올리면 상자가 부드럽게 떠오르고(시간 기반 보간), 금빛 번짐·궤도 반짝이·자수정 소리가 납니다.
2. **선택 직후 (줄 위에서):** 카메라가 고른 상자 쪽으로 살짝 다가갑니다. 빛 입자가 나선으로 빨려 들어오고, 바닥 마법진 고리가 하나씩 켜집니다.
   - 고리 수 = 등급 + 1이고, 켜질 때마다 고리가 번쩍이며 빛기둥이 솟고, 차임 소리가 점점 높아집니다.
   - **유일:** 시안으로 켜지다가 보라로 바뀝니다(섬광·충격파).
   - **전설:** 암전 → 금빛 별똥별이 꼬리를 그리며 떨어져 꽂힘 → 화면 흔들림·섬광·충격파 → 별자리 고리.
3. **등급 표시:** 고른 상자 위에 별(등급 수만큼)과 빛이 튀어나옵니다(등급별 소리). 줄 단계에는 등급 글씨가 없습니다(오너: 이펙트로 알 수 있음).
4. **나머지 4상자:** 0.16초 간격으로 차례로 등급색 빛기둥이 터집니다(글씨 없음).
   - 이 등급은 연출용(보상 없음)이고, `summon.decoy-weights` 4:3:2:1(전설 10%)로 실제 확률보다 높게 나와 "아깝다"는 느낌을 줍니다.
5. **이동·충전:** 나머지 상자는 사라지고, 고른 상자가 가운데로 와서 커집니다.
   - 심장박동 3번(베이스드럼): 등급색 오라, 갓레이, 틈새 광선이 커집니다. 입자가 모여들고 상자가 점점 떨리다가, 직전에 빛을 빨아들입니다.
6. **개봉:** 흰 섬광, 화면 흔들림·줌 킥, 이중 빛기둥, 중력 받는 불꽃 파편, 남는 반짝이, 충격파가 터집니다.
   - 아이콘이 탄성 있게 떠오르고, 오른쪽에 등급·별·이름·신규/중복이 뜹니다.
7. **클릭:** 결과 카드가 뒤집히며 나옵니다(광택 스윕, 갓레이). 버튼은 [다시 소환] [확인]입니다.

- **클릭으로 넘기기:** 등급 표시 중이면 이동으로, 충전 중이면 개봉으로 넘어갑니다.
- **길이(개봉까지):** 일반 약 4.7초, 전설 약 6.7초. 클릭하면 줄일 수 있습니다.
- **움직임은 모두 시간 기반이라 게임 FPS만큼 부드럽습니다.** 첫 미리보기가 끊겨 보인 것은 영상이 20fps였기 때문이고, 이번 미리보기는 60fps입니다.
- 카메라는 `Canvas.background(zoom, dx, dy)`와 `Camera` 래퍼(좌표·크기 변환)로 처리하고, 전체 화면 채우기에는 적용하지 않습니다.
- 사운드 큐에는 번호가 붙습니다(`cueIndex`: 고리 번호, 미끼 등급, 박동 번호, 별 번호).

## 4. 확인 부탁

1. **아이템애더:** `item:random_artifact`, `item:artifact_shard` 생성. 표시 이름이 다르면 `summon.item-name`을 맞춰 주세요(창 안내문에 쓰임).
2. **빌드 때 볼 것:**
   - `RotationAxis` / `MatrixStack.multiply`
   - `SoundEvents`에서 `RegistryEntry`인 것(`BLOCK_NOTE_BLOCK_*`, `UI_BUTTON_CLICK`)의 `.value()`
   - 셰이더 `magiccodex:core/hud_additive`가 로드되는지
3. **실게임:**
   - 열쇠 1개 소모 → 결과 → 권한 지급 → 도감 반영
   - 중복일 때 우편 조각
   - 열쇠가 없을 때 버튼 비활성
   - 창을 닫았다 다시 열기
   - GUI 배율 2·3·4와 창 비율(배경은 창 가득, UI는 기준 좌표 안)
4. **실패 처리:**
   - 열쇠를 쓰기 전 확인: 캐릭터 로드, 아이템애더, LuckPerms, 카탈로그, 열쇠 수. 하나라도 아니면 거절하고 소모하지 않습니다.
   - LuckPerms 저장이 실패하면 열쇠를 우편으로 반환합니다.
   - 우편 기능이나 조각 아이템이 없으면 콘솔에 `ARTIFACT SHARD NOT SENT`를 남깁니다.
5. **천장(보장)·10연 소환·조각 교환은 없습니다(오너: 나중).**
