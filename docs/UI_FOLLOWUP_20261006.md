# MagicCodex UI 후속 통합 — 2026-10-06 (KST)

## 기준과 변경

기준 소스는 `ae8644fe777b06c4fbf80a014394d9b433776032`이다. 이전 닉네임·펫·이로치·장비·VFX·ChacaNPC 통합을 유지하고, NPC 배회와 운영 설정은 변경하지 않았다. 자동완성 패치는 `6abddb9e6d0c43c6f83c4f7c02d5d63b1c8d6e87`의 준비된 메일 패치로 적용해 작성자 정보를 보존했다.

- 상점: 공통 `SocialScreen`의 고화질 이미지 캐시·폰트·버튼을 사용한다. 화면 fit을 기존의 70%로 조정하고 배경에 약한 투명도와 차분한 색상 tint를 적용했다. 원본 PNG는 수정하지 않았다. 탭·검색 여백, 확대 아이콘, 행 폭, 하단 이름/가능 수량/큰 합계/보유 금액, 수량 입력과 거래 버튼 높이를 조정했다. NPC 일러스트는 상대적으로 확대하고 아래로 내려 클리핑한다.
- 아이템 이름은 기존 `ItemTooltipRenderer.localName`을 사용해 커스텀 표시명을 우선하고, 기본 이름은 `minecraft:lang/ko_kr.json`의 한국어 이름을 사용한다. 목록 hover는 기존 `drawItemTooltip` → MagicCodex rich tooltip을 사용한다. 긴 정보는 기존 Shift+휠 스크롤을 지원한다.
- 기존 1KB 초과 아이템을 일반 아이템으로 바꾸던 미리보기 fallback을 제거했다. 최대 32KB의 원본 serialized stack을 전송하며 전체 패킷은 60KB 이하로 유지한다. 페이지에 들어가지 않는 상품은 검색으로 좁혀야 한다. 아이템 설명/컴포넌트를 잘라 목록 개수를 늘리지 않는다. 클라이언트 NBT 해제 한도는 2MiB다.
- `우편함 지급` 목록 문구는 제거했다. 구매품은 기존 `MailService`/멱등키를 통해 계속 우편으로 지급된다. 기존 DB 저널과 저장 영수증 절차는 유지했다.
- 버튼 클릭음과 거래 완료음을 분리했다. 구매/판매가 실제로 DB `done`에 도달한 뒤에만 서버가 `completedOperation`을 보낸다. 클라이언트는 요청 순번·세션·상점·거래 종류·operation ID가 일치할 때만 한 번 재생한다. 실패/OPEN 응답/중복 응답은 완료음을 내지 않는다.
- `/우편함`, `/메일함`, `/codexmail`, 상단 우편 버튼은 같은 client 경로를 사용한다. `/닉네임` 등 기존 별칭도 다음 client tick에 화면을 연다. 메인 스레드 `client.execute`에 의존하지 않는다. JOIN/DISCONNECT 또는 연결/월드 객체 변경 시 예약을 취소한다. 기존 요청·세션 검증은 유지한다.
- NPC: 왼쪽 위 중복 이름/사각형을 제거하고 아래 이름표를 유지한다. 오른쪽 선택지/입력 뒤에 어두운 배경을 넣고 일러스트와 분리했다. 호감도는 폰트에 의존하지 않는 벡터 하트 5개로 표시한다. 직접 말하기는 4번 선택지로 제공하며 입력창은 기본 숨김이다.
- 직접 입력의 Enter와 전송 클릭은 같은 submit 경로를 사용한다. 키보드 4번을 누르면서 생기는 문자 이벤트가 입력 내용에 들어가지 않도록 했다. 전송 불가/서버 INFO/타임아웃은 상태를 해제하고 안내한다. 입력 텍스트는 로컬 전송 실패 때 유지한다.
- NPC 기록은 여러 메시지의 줄 단위 스크롤 목록이다. 선택지로 보낸 말·직접 말한 문장·NPC 응답을 포함하고 역할/순번으로 중복을 막는다. 기록이 열리면 선택지와 입력창은 렌더링/클릭되지 않는다. 기록 닫기와 Esc는 기록만 닫으며 대화 세션을 닫지 않는다.
- HUD: 기존 `HudRenderCallback` 등록을 제거하고 Fabric 1.21.4의 `HudLayerRegistrationCallback`으로 `SUBTITLES` 뒤에 UI를 한 번 그린다. 마우스 tooltip은 마지막 별도 pass와 상대 깊이 300을 사용한다. 기존 `PlayerHudLayout.scale()`·메뉴 좌표/hitbox는 유지한다.
- `/상점`·`/상점관리` 자동완성은 권한 검사와 유효 인자를 확인하고, 이미 로드된 catalog/NPC snapshot만 사용한다. 자동완성에서 DB/파일 IO는 하지 않으며 잘못된 인자에는 빈 목록을 반환한다.

## 입력 실패 진단과 설정

기존 코드에서 확인한 문제는 (1) 입력창이 보여도 Enter가 타자기/다음 페이지 처리를 먼저 실행해 전송되지 않을 수 있음, (2) 명시적인 클릭 전송 버튼 부재, (3) local send 실패에 안내가 없었음, (4) 1~3 문자 단축키가 빈 입력에서 해당 숫자를 삼켰음이다. 직접 입력 모드에서는 Enter를 즉시 submit으로 연결하고 숫자 입력을 허용한다. 화면의 실제 focus를 입력 편집에 사용하고 세션/순번은 서버가 계속 검증한다. 사용자가 겪은 특정 실게임 실패의 최종 원인은 runtime trace로 확인해야 하며, offline 테스트만으로 확인했다고 주장하지 않는다.

선택적인 클라이언트 JVM 진단 옵션:

```text
-Dmagiccodex.npcUiTrace=true
```

`MagicCodex/NpcUi` 로그에는 입력 열기, focus, 입력 길이, submit, op/sequence, waiting, 응답/타임아웃/채널 부재, handshake 준비와 연결/채널 유무, 응답 세션 일치 여부만 기록한다. 입력 문장·NPC 이름·세션 토큰·API 키·비밀번호는 기록하지 않는다. 기본값은 비활성이다.

상점 패킷 계약은 `SHP2`로 바뀌었다. **이번 Bridge/UI를 같은 통합본으로 준비해야 하며 구버전과 섞으면 안 된다.** NPC 대화 계약/서버 기능은 바꾸지 않았다. 전용 `shop-mailbox-database.properties` 분리는 유지하며 DB 계정·암호·연결 파일·이관은 이번 변경에서 다루지 않는다.

## 호스팅 빌드/테스트

작업 소스: `C:\Chacademi\staging\shop-ui-refine-20261005-task9\source`. Java 21.0.10 / Gradle 8.12.1. 기존 호스팅 컴파일 의존성(ModelEngine, MythicMobs, PlaceholderAPI, MariaDB driver, SQLite JDBC)을 ignored 경로에 준비했다. 새 clone의 이 파일들이 없어서 첫 서버 빌드는 중단됐으며, 기존 호스팅 파일만 복사한 뒤 정상 통과했다. 외부 유료/배포 제한 JAR은 Git에 포함하지 않는다.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
$env:GRADLE_USER_HOME='C:\Chacademi\tools\gradle-cache'
$env:Path=$env:JAVA_HOME+'\bin;'+$env:Path
Set-Location 'C:\Chacademi\staging\shop-ui-refine-20261005-task9\source'
.\gradlew.bat --offline --no-daemon --console=plain :magic-codex-paper:test :magic-codex-paper:jar :chaca-npc-paper:test :chaca-npc-paper:jar
.\magic-codex-fabric\gradlew.bat -p magic-codex-fabric --offline --no-daemon --console=plain test remapJar
```

| 모듈 | 테스트 | 실패/오류/건너뜀 |
| --- | ---: | --- |
| MagicCodex Bridge | 192 | 0 / 0 / 0 |
| ChacaNPC + protocol | 12 | 0 / 0 / 0 |
| Fabric UI | 185 | 0 / 0 / 0 |
| 합계 | 389 | 0 / 0 / 0 |

Bridge에는 자동완성 12개와 full preview/완료 영수증 프로토콜 4개 테스트가 포함된다. Fabric에는 다음 tick 예약/취소/서버 이동, 늦은 응답·중복 응답·타임아웃, 선택지/직접 입력 기록, 완료음 판정 9개 회귀 테스트가 추가됐다. 메뉴 이름을 `우편함`으로 통일하면서 기존 메뉴 테스트의 기대 문자열을 갱신했으며 배율/hitbox assertions는 유지했다.

실제 최종 JAR에서 기존 client 자산 **895개(기존 893개 + 승인 PNG 2개)** 및 보호 Bridge 클래스 **47개** (승인된 `CustomShinyModels` 변경 제외)가 이전 통합 산출물과 byte-identical함을 확인했다. 9개 자산 메타데이터는 Git clone에서 줄바꿈만 달랐으므로 내용 동일성을 먼저 확인하고 원래 JAR 바이트로 복원했다. 펫/장비/닉네임 서버/대화 facade/VFX/ChacaNPC source 보존도 확인했다. 이로치는 아래 승인된 후속 수정만 반영했다.

## 최종 산출물

호스팅 경로: `C:\Chacademi\staging\shop-ui-refine-20261005-task9\final-ready`

| 파일 | SHA256 |
| --- | --- |
| magic-codex-bridge-0.30.0-catalog.alpha.1.jar | DA81A97FF5687DA506F3B2D42683BD32922A7083497DDF23B7E1A478171B84CF |
| magic-codex-ui-0.39.0-catalog.alpha.2+mc1.21.4.jar | 68B1093EE8A3C266EE06CEAE2AA5143E72398A600EEA19AF0DD3BA42B73E84A6 |
| chaca-npc-0.2.0.jar | 1C83007BE562F8591C26D65004B85F22A5253FAD992A6F5FE91416FE718CE66A |
| Citizens-2.0.37-b3725.jar | 90E2C3C0948F9B54D3196B52C36116651E9512D3656EB4B1B9F110848C3FECA2 |

ChacaNPC/Citizens는 기존 최종 exact-build 파일을 그대로 보존했다. 이번 ChacaNPC 소스 빌드/테스트도 통과했으나 제공받은 최종 파일을 다시 교체하지 않았다. 상세 manifest는 staging의 `FINAL-VERIFICATION.json`이다.

## MythicMobs 등록 몹 이로치 지정·해제 후속 수정

`c76fbcee4bdcb4de5e7fa0ec322aad938762a65c`의 UI 통합본에서 이어서 수정했다. 등록된 MythicMobs ID가 교화 대상이어도 `chacademia_custom` scoreboard 태그가 없으면 두 군데의 태그 검사 때문에 모델 교체를 건너뛰고 성공 메시지만 출력하던 경로를 제거했다. 기존 `TamingBridge.profileId`의 등록 ID/펫 제외 판정과 `configuredMythic` 확인을 함께 사용한다. 태그를 강제로 추가하거나 모든 Mythic 몹을 등록하지 않는다.

기존 전용 모델 이름(doxy/fenrir_mother/fenrir_pup/goblin의 `_shiny`, `ca_` 모델의 `_s`)을 유지한다. 등록된 vanilla 외형 Mythic 몹은 ModelEngine 모델이 없어도 기존 희귀 상태 처리를 유지한다. custom 태그가 있는데 모델이 아직 연결되지 않았거나 ModelEngine이 없으면 성공 처리하지 않는다. 연결된 모델에 지원되는 모델 쌍이 없거나 대체 모델이 누락돼도 오류로 처리한다.

모든 교체 모델을 먼저 생성·크기 복사한 뒤 기존 모델을 분리한다. 연결 중 실패하면 이미 삽입된 대체 모델까지 제거하고 원본을 다시 연결하며 원본을 파괴하지 않는다. 기존 `mark`의 희귀 PDC/태그/추적목록/클라이언트 전송은 외형 변경이 성공한 다음에만 진행된다. 모델 변경은 기존처럼 서버 메인 스레드에서 실행한다. NPC 배회/AI 및 기존 리소스는 수정하지 않았다.

`ShinyModelSwapTest` 8개가 추가됐다: 무태그 등록 판정, 5개 기존 모델 계열의 지정·해제 매핑, 반복 명령/크기/교화 연출 모델 보존, 누락 모델 원본 보존, 삽입 후 실패 원복, 다중 모델 사전 준비, 해제 실패 시 이로치 원복, 미지원 모델 실패 처리. 위 호스팅 명령으로 Bridge 192/NPC 12/UI 185, 총 389개 테스트가 실패·오류·건너뜀 없이 통과했다. 서버 JAR 빌드와 Fabric remapJar도 성공했다. UI와 NPC/Citizens 최종 JAR 해시는 이전 통합본과 동일하다.

실제 MythicMobs/ModelEngine 런타임에서 직접 소환하고 명령을 실행하는 게임 검증은 미실시다. 회귀 테스트는 모델 교체 어댑터를 사용하므로 실제 플러그인의 애니메이션/시각 효과/라이프사이클까지 검증했다고 주장하지 않는다. 운영 배포와 서버 재시작 없이 확인할 수 있는 범위만 수행했다. 추후 게임 검증에서는 등록 몹 직접 소환 → 지정 → 재지정 → 해제 → 재해제, 무태그 개체, 모델 누락 실패 후 기존 상태, 교화 잠금/미등록/펫 거부를 확인한다.

## 남은 검증과 배포 상태

실게임 화면 크기/색/알파/가독성/일러스트 crop, HUD-chat 깊이, 실제 키보드/클릭과 화면 resize, 여러 메시지 스크롤, NPC 세션 재연결, 실제 AI 응답, custom ItemStack/PDC/ItemsAdder tooltip 및 수령 round-trip, Vault 성공/실패 응답/소리, live MariaDB 경쟁/급격한 종료 복구는 미검증이다. 유료 AI 요청과 운영 DB 조작을 하지 않았다. UI root-cause 확인에 필요한 opt-in trace와 재현 체크는 통합본 게임 검증에서 수행한다.

운영 배포·서버 재시작·DB 권한/계정 변경·기존 데이터 이관을 하지 않았다. 임시 `chacademi-launcher` 저장소/배포 파일도 변경하지 않았다. 런처 최종 반영은 위 소스/해시의 통합본을 확인한 뒤 담당 작업과 조율한다. 이전 `mailbox-20261005-task9/final-ready`를 최신 UI 수정본으로 착각하지 않는다.