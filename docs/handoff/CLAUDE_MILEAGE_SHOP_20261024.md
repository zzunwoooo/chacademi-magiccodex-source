# 마일리지 상점 + 관리 창 + 우편 지급 + 메뉴 연결 — Claude 인계 2026-10-24 (미빌드)

- 브랜치: `claude/mileage-shop-20261024`
- 기준: `review/integrated-release-20261023-task7` @ `af1ed3e`
- 범위: **MagicCodexBridge(학교·야생) + MagicCodex 클라 모드 + 프로토콜.** 셋을 같이 올려야 함(새 채널 `magiccodex:mileage_request` / `magiccodex:mileage_response`).
- 오너 요청: 레퍼런스(ZIP: shop_panel.png / purchase_button.png / mileage_coin.png + 시안) 그대로 마일리지 상점 UI, 마일리지는 `/유저관리`로 조절, 상품 관리 창, 구매하면 우편함으로, 메뉴 연결. 차카데미 글꼴 그대로, 글자가 테두리 밖으로 넘치거나 겹치지 않게.

## 0. 검증 상태

| 항목 | 상태 |
| --- | --- |
| 문법 검사(javac parse) | 새 파일·수정 파일 19개 오류 0 |
| 타입 검사 | `MileageProtocol`, `MileageView`(MC 의존 없음)만 실제 javac 컴파일 통과. `MileageBridge/MileageStore/MileageScreen/MileageClient`는 Paper·Fabric jar가 이 환경에 없어 **parse만** 함 |
| 단위 테스트 | `MileageProtocolTest` 4개, `MileageViewTest` 3개 — 같은 단언을 쓰는 작은 실행기로 통과. **Gradle test는 안 돌림** |
| 화면 확인 | `MileageView`(실제 배치·그리기 코드)를 Java2D 캔버스 + 실제 TTF(마루부리·Pretendard)로 그려 확인: `docs/handoff/img/mileage/*.jpg`. 아이콘 자리는 시안 그림을 잘라 넣은 미리보기 전용 |
| 실게임·DB | **안 함** (5장) |

## 1. 파일

| 파일 | 내용 |
| --- | --- |
| `magic-codex-protocol/.../MileageProtocol.java` (새) | C2S OPEN/BUY/CLOSE/ADMIN_*, S2C STATE/RESULT. 상품 최대 120, 미리보기 NBT 32KB, 패킷 1MB, 수량 1~99, 가격 ≤ 10억, 잔액 ≤ 1조 |
| `magic-codex-paper/.../MileageStore.java` (새) | DB: 잔액·상품·주문 원장 |
| `magic-codex-paper/.../MileageBridge.java` (새) | 창 열기, 구매 → 우편, 관리 창 요청, 명령어, 미배송 주문 재전송 |
| `magic-codex-paper/.../MagicCodexBridge.java` | `MileageBridge` 생성(실패해도 플러그인은 계속) / 종료 |
| `magic-codex-fabric/.../MileageView.java` (새) | MC 의존 없는 배치·그리기·히트 테스트 (shop_panel.png 픽셀 좌표) |
| `magic-codex-fabric/.../MileageScreen.java` (새) | 상점/관리 두 모드, 탭·페이지·선택·입력칸·요청 |
| `magic-codex-fabric/.../MileageClient.java` (새) | 패킷 등록·수신, `open()` |
| `MagicCodexClient.java` | `MileageClient.initialize()` |
| `TopMenuClient.java`, `TopMenuState.java`, `TopMenuTest.java` | 상단 메뉴 「마일리지샵」 → `MileageClient.open()` (예전 `/캐시샵` 명령 대신) |
| `SharedNotifications.java` | `MileageScreen`이 열려 있어도 공용 알림 카드가 보이게(상점과 같은 처리) |
| `textures/gui/mileage/*.png` (새 3장, 4.3MB) | 오너 ZIP 원본 **바이트 그대로** (sha256 앞 12자리 shop_panel 275a0935b372, purchase_button 605127d3f438, mileage_coin e11b3b0775bf) |
| `test/.../MileageProtocolTest.java`, `MileageViewTest.java` (새) | 왕복·잘못된 입력·합계 / 글자 폭·히트 영역 |

## 2. 데이터·구매 흐름 (서버)

- DB는 **상점·우편과 같은 전용 설정** `plugins/MagicCodexBridge/shop-mailbox-database.properties`. 학교·야생이 같은 MariaDB면 잔액·상품·주문이 공유된다. 파일이 없으면 이 서버 로컬 `mileage.db`(SQLite).
- 테이블(MariaDB): `codex_mileage_balance(owner, amount, updated)`, `codex_mileage_products(id, category, name, description, price, enabled, sort, payload)`, `codex_mileage_orders(id, owner, request_key UNIQUE, product, quantity, total, payload, title, state, created)`. 처음 켤 때 `CREATE TABLE IF NOT EXISTS`. 필요한 권한: SELECT/INSERT/UPDATE/DELETE/CREATE.
- 구매: 한 트랜잭션에서 잔액 잠금(MariaDB `FOR UPDATE`) → 판매 중·가격 일치(`expectedPrice`)·잔액 확인 → 차감 → 주문 `paid` 기록. 같은 `request_key`(= 유저UUID:operation)를 다시 보내면 첫 주문을 돌려주고 두 번 빼지 않는다.
- 그 다음 메인 스레드에서 `MailService.sendSystem("mileage:<주문ID>", …)` → 저장 확인되면 주문 `done`. 우편 source_key가 UNIQUE라 **`paid` 주문은 언제 다시 보내도 우편이 두 통 생기지 않는다.** 그래서 접속할 때, 켠 뒤 15초, 이후 5분마다 `paid` 주문을 다시 보낸다(서버 상관없음).
- 우편: 제목 「<상품 이름> 구매」, 본문 「마일리지 상점에서 N개를 구매했어요. (X M 사용)」, 첨부 = 상품 아이템 × 수량(최대 9묶음 → 한 번에 최대 `min(99, 최대 스택×9)`개). 주문에는 **구매 순간의 아이템 바이트**를 따로 저장하므로 나중에 상품을 고쳐도 이미 산 것은 그대로.
- 등록 아이템은 손에 든 것의 **1개짜리 복사본**(NBT·PDC·ItemsAdder 데이터 그대로, 손에서 빼지 않음).

## 3. 명령어

| 명령 | 권한 | 내용 |
| --- | --- | --- |
| `/마일리지상점` (별칭 `/마일리지샵`) | 누구나 | 상점 창 |
| `/상점관리 마일리지` | OP | 관리 창 |
| `/유저관리 <닉네임> 마일리지 보기` | OP | 잔액 |
| `/유저관리 <닉네임> 마일리지 설정|추가|빼기 <값>` | OP | 0 ~ 1조. 0 아래로는 거절. 열려 있는 상점 창은 바로 갱신 |
| `/유저관리 <닉네임> 마일리지 기록` | OP | 최근 구매 10건(우편 보냄 / 우편 대기) |

`/유저관리 <닉네임> 보기` 요약에 「마일리지」 줄(접속 중 + 최근 읽은 값만). 콘솔 로그: `MILEAGE BUY`, `MILEAGE ADMIN …`, `MILEAGE RECOVER`.

## 4. 화면 (클라)

- 좌표계 = shop_panel.png 픽셀(1706×922). PNG의 실제 선(머리 y137, 왼쪽 칸 x301, 오른쪽 상세 x1186~1660, 안쪽 테두리 x44~1665 / y27~884)을 재서 시안 요소를 그 위에 옮김. 화면 맞춤 `min(w*.98/1706, h*.98/922)`, 메뉴 배경 블러.
- 코드로 그린 것(README 지정): 카드 외곽선·선택 강조, 잔액 캡슐, 닫기·새로고침·화살표·수량 ± , 카테고리 선 아이콘(상자·별·티켓). 시안 제목 왼쪽 **나침반 문양은 PNG에 없어 선으로 그림**(두 원 + 4방향 별).
- 구매 버튼: purchase_button.png의 불투명 판(x92~2080, y178~526)만 3조각(양끝 장식 고정, 가운데 늘림). hover/pressed/disabled는 밝기 조절.
- 글꼴: 제목·가격·합계 숫자 = 마루부리(`title`), 버튼 = 마루부리 bold(`section`), 이름·라벨 = Pretendard medium(`label`), 설명 = Pretendard(`body`), 수량 = Pretendard bold(`hud_bold`).
- **넘침 방지**: 모든 글자는 `MileageView.fit`(82%까지 줄인 뒤 「…」), 설명은 2줄 줄바꿈 + 「…」, 금액은 자르지 않고 45%까지 줄임. 테스트가 폭을 검사함. 긴 이름·1조 잔액 미리보기 `p_long.jpg`.
- 상점: 탭 3개(편의/이벤트/이용권), 한 쪽 6칸(3×2), 쪽 넘기기 + 휠. 카드 선택 → 오른쪽 상세(큰 아이콘·이름·장식선·설명·수량·합계·구매하기). 수량 칸 직접 입력(숫자 2자리), 최대 = min(99, 스택×9, 잔액/가격). 아이콘에 마우스 → 기존 아이템 툴팁.
- 관리 창(같은 패널): 머리 「마일리지 상점 관리」, 캡슐 「등록 상품 N개」, 카드 끝에 「새 상품 추가」 카드(손에 든 아이템), 숨긴 상품은 어둡게 + 「숨김」. 오른쪽: 이름·설명·가격 입력칸, 분류 3칸, 판매 중/숨김, 순서 앞으로/뒤로(즉시), 손에 든 것으로 교체, 삭제(3초 안에 두 번), 저장하기/추가하기. Tab 키로 이름→설명→가격.
- 알림: 구매 성공 = 공용 알림 카드(아이템 아이콘) 「<이름> ×N 구매 완료 / 우편함으로 보냈어요 · X M 사용」. 실패·관리 결과는 같은 카드(경고/정보). 창 안에 토스트를 띄우지 않음(카드와 겹치지 않게).
- 메뉴: 상단 메뉴 「마일리지샵」 → 패킷으로 바로 엶. 서버에 이 기능이 없으면 `/마일리지상점` 명령으로 대신 보냄.

## 5. 실게임에서 볼 것

1. 메뉴 「마일리지샵」·`/마일리지상점` → 창, 시안(`img/mileage/compare_reference.jpg`)과 비교. 16:9 / 4:3 / 21:9, GUI 배율 2·3·4.
2. `/상점관리 마일리지` → 손에 아이템 들고 「새 상품 추가」 → 이름·가격·분류 → 추가하기. 숨김/순서/교체/삭제. 다른 유저 상점 창이 바로 갱신되는지.
3. `/유저관리 <닉> 마일리지 추가 12500` → 열린 상점 잔액 갱신. 구매 → 잔액 차감, 우편함에 첨부 도착, 알림 카드. 부족·수량 초과·숨긴 상품 구매 시 거절 문구.
4. 연타(같은 operation 한 번만), 우편함 꺼진 상태에서 구매 → `paid`로 남았다가 재전송되는지(`/유저관리 <닉> 마일리지 기록`).
5. 고해상도 아이템 아이콘이 176/240px에서 깨지지 않는지(`ItemIconTextures`), 한글 IME 입력.
6. 큰 PNG 3장(약 4.3MB, GPU는 더 큼) 첫 열림 끊김. 있으면 `UiResources` 예열에 `textures/gui/mileage/` 추가 검토.

## 6. 컴파일 위험 (빌드에서 먼저 볼 곳)

- `MileageBridge.push`: `work(() -> new Object[]{…})` 후 `(List<MileageStore.Product>) v[0]` 캐스트 + 지역 변수 `@SuppressWarnings` + `var`.
- `MileageStore.buy`: try-with-resources 안에서만 대입하는 `name/price/enabled/bytes` 확정 대입 판정.
- `LongArgumentType.longArg(0, 1e12)` (brigadier long 칸), `ChacaApi.playerCommand(..., List.of("마일리지샵"))`.
- `MileageScreen`: `TextFieldWidget#setCursorToStart(boolean)`, `Math.clamp(long,int,int)`, arrow-switch 블록 안 `break`.
- `MileageClient`: `record Request/Response implements CustomPayload` (FishShopClient와 같은 모양).

## 7. 배포

- Bridge(학교·야생) + 클라 모드 같이. 서버 재시작 필요(새 채널·명령). 런처에 새 텍스처 3장 포함되는지 확인.
- 학교·야생이 같은 잔액을 쓰려면 두 서버의 `shop-mailbox-database.properties`가 같은 MariaDB여야 함(상점·우편과 같은 조건).
- 예전 메뉴가 보내던 `/캐시샵` 명령은 이제 쓰지 않음(다른 플러그인이 쓰고 있었다면 그대로 남음).
