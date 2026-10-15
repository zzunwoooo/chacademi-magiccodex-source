# 아티팩트 도감 1차 (Claude, 2026-10-15)

브랜치: `claude/artifact-codex-20261015` (기준: `claude/element-reaction-20261014` @ `1a0fe6f`, 그 브랜치 포함)

## 0. 먼저 읽을 것 (상태)

- **빌드 안 됨.** Claude 작업 환경에서는 Paper/Fabric/Minecraft 의존성을 받을 수 없어 Gradle을 못 돌렸습니다.
  - `ArtifactProtocol`은 javac로 컴파일하고 200개 카탈로그 분할·왕복 시험을 통과시켰습니다.
  - UI는 같은 좌표·폰트·이미지로 파이썬 목업을 그려 레퍼런스와 맞춰 봤습니다. 실게임 화면은 확인하지 못했습니다.
- **아티팩트 능력은 구현하지 않았습니다(오너 지시).** 지금은 도감 표시, 보유 판정, 장착 기록만 합니다.
- **아이콘은 비워 두었습니다(오너 지시).** `artifacts.yml`의 `icon`이 비어 있으면 아무것도 그리지 않습니다.

## 1. 오너 요구사항

1. 승인된 UI PNG를 사용합니다. 원본은 오너 PC `Downloads/artifact-ui`이고, README의 "간소화 버전" 기준(하단 장착 목록·분류 칩·획득 경로 없음, 상세 효과는 마우스 올리면 툴팁)을 따릅니다.
2. 글자는 전부 MagicCodex 폰트로 그립니다(`title` / `hud_bold` / `label` / `body`, CodexTypography 사용). 위아래·좌우 여백을 맞췄습니다.
3. 예시 아티팩트는 등급별로 1개씩입니다(튼튼한 조끼 / 탭댄스 구두 / 거인의 심장 / 무지개 프리즘).
4. **오너가 yml로 언제든 수정할 수 있어야 합니다.** 서버 `plugins/MagicCodexBridge/artifacts.yml`을 고치고 `/아티팩트관리 새로고침`하면 접속자 도감까지 바로 바뀝니다. 모드는 다시 배포하지 않아도 됩니다.

## 2. 구조

| 위치 | 파일 | 역할 |
|---|---|---|
| 공용 | `magic-codex-protocol/.../ArtifactProtocol.java` | `magiccodex:artifact_request`(C2S: 조회/장착/해제 + 아이디), `magiccodex:artifact_response`(S2C: 카탈로그를 30000바이트 이하 조각으로 나눠 보내고, 상태 = 보유 비트셋·장착 목록·알림) |
| 서버 | `magic-codex-paper/.../ArtifactBridge.java` | `artifacts.yml` 읽기·검증, 카탈로그 리비전(CRC), 보유=권한 `magiccodex.artifact.own.<id>`(또는 `.*`), 장착=PDC `artifact_equipped`, `/아티팩트관리 <새로고침\|목록\|확인 <플레이어>>` |
| 서버 | `artifacts.yml`(기본 리소스), `plugin.yml`(명령 `아티팩트관리`, 권한 `magiccodex.artifact.admin`), `MagicCodexBridge`(생성 한 줄; 실패해도 플러그인은 계속 동작) | |
| 클라 | `ArtifactClient.java` | 수신·조각 합치기·상태 보관, `/아티팩트도감` 클라이언트 명령 |
| 클라 | `ArtifactCodexScreen.java`, `ArtifactCodexLayout.java` | 도감 화면. 좌표계는 1672×941(main_panel.png = CodexLayout과 같음) |
| 클라 | `EquipmentScreen.java` | 서버가 도감을 지원하면 **아티팩트 칸 좌클릭 → 도감**. 칸에 장착 아티팩트 이름 표시, 툴팁 변경 |
| 클라 | `textures/gui/artifact/*.png` | 승인 PNG(main_panel은 원본 크기, 나머지는 용량 때문에 50% 축소, lock은 실제 자물쇠만 잘라 256px), `compass.png`(codex_base 나침반을 투명 배경으로 추출) |

## 3. 화면 동작

- **상단:** 제목, 나침반, X / 검색창(이름 검색, 잠긴 항목은 이름이 숨겨져 있어서 검색에 안 걸림) / 등급 탭 [전체·일반·고급·유일·전설] / 수집 n / 전체
- **둘째 줄:** 정렬 드롭다운 [등급순·이름순·보유순] / [보유한 것만] 체크박스
- **카드 5×3, 페이지 화살표·휠로 넘김:**
  - 등급별 카드 프레임을 나인슬라이스로 그립니다(모서리 장식은 비율 유지, 보석은 카드 밖으로 나옴).
  - 미보유 카드는 자물쇠와 `???`로 표시합니다.
  - 장착 중이면 `장착 중` 칩, 선택하면 등급색 테두리가 빛납니다.
- **상세 패널:** 아이콘(빈칸) / 이름(제목 폰트) + 등급(등급색) / 구분선 / 소개 2줄 / 구분선 / "상세 효과" + 효과 2줄(넘치면 …) → **효과에 마우스를 올리면 전체 효과 툴팁** / [장착] [해제] / 서버 알림 한 줄
- **장착 규칙:** 서버 판정입니다.
  - `max-equipped`가 1이면 장착할 때 기존 것을 교체합니다.
  - 2 이상이면 칸이 다 찼을 때 거절합니다.
  - 응답이 5초 안에 없으면 "서버 응답이 늦습니다"를 띄우고 다시 조회합니다.

## 4. 확인 부탁 / 주의

1. **장비창 아티팩트 칸과의 관계:** 기존 `/장비설정 artifact ...`로 태그한 아이템 장착(EquipmentBridge 슬롯 4)이 있습니다. 서버가 도감을 지원하면 그 칸 **좌클릭은 도감으로 바뀝니다**(우클릭 해제는 그대로). 아이템형 아티팩트를 계속 쓸지 오너 결정이 필요합니다.
2. **장착 기록은 PDC(서버별)입니다.** 학교·야생 공유가 필요하면 `PlayerStateStore`에 컬럼을 추가해야 합니다. DB 재작업 중이라 손대지 않았습니다. 보유는 권한(LuckPerms)이라 이미 공유됩니다.
3. **권한 기본값:** `magiccodex.artifact.own.<id>`는 plugin.yml에 개별로 선언하지 않았기 때문에 **OP는 전부 보유**로 보입니다. 운영에서 문제가 되면 선언하거나 체크 방식을 바꿔야 합니다.
4. **빌드할 때 확인할 API:**
   - `TextFieldWidget`(포커스·`charTyped`)
   - `HudMesh.arc/line/star/capsule`
   - `HudTextureCache.drawTexture(..., sw, sh, tw, th, tint)`: 나인슬라이스가 조각 9개를 각각 업로드합니다.
   - `CodexTypography.draw(..., centered)`: y는 글자 잉크 중심입니다.
5. **실게임 확인:** GUI 배율 2·3·4, 창 크기 변경, 한글 입력(IME) 검색, 툴팁 위치(카드 위로 겹침), 잠긴 카드 어둡게 처리, 1개만 장착할 때 교체 동작을 봐 주세요.
6. **아이콘:** 나중에 `textures/artifact/<id>.png`를 모드에 넣거나 리소스팩으로 보내고, yml의 `icon`만 채우면 됩니다.

## 5. 다음 단계 (오너 결정 대기)

- 아티팩트 능력 구현. 원소 반응 연동 아티팩트는 `claude/element-reaction-20261014`의 `Reactions`에 연결합니다.
- 획득 경로(지급 방식)와 장착 칸 수 확정
- 전체 200종 yml 작성(기획 확정본 기준)
