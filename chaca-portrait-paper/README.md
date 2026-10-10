# ChacaPortrait 사용법과 검증 경계

- 자동 생성 기본값은 `auto.first-join: false`입니다. 관리자 수동 생성과 별개입니다.
- 자동 생성을 켜도 `server-id: school`에서만 생성합니다. wild는 자동 생성하지 않습니다.
- 전역 `enabled: false`는 모든 유료 생성(관리자 포함)을 막습니다.
- 키 우선순위: `CHACAPORTRAIT_OPENAI_KEY` → `CHACANPC_OPENAI_KEY` 환경변수 → 이 플러그인 `config.yml`의 `openai.api-key`입니다. 기본값은 빈 문자열이며 운영자가 직접 입력합니다. NPC 설정 파일은 읽지 않습니다. 실제 키는 공개 Git에 넣지 마세요. 저장 후 해당 서버에서 `/portrait reload`로 반영합니다(콘솔: `portrait reload`). 재시작은 필요하지 않습니다.
- 공유 DB에서 서버마다 고유 server-id가 필요합니다. 재시작 복구가 다른 서버 작업에 영향을 주면 안 됩니다.

## 명령
관리자 권한 `chacaportrait.admin`(기본 OP):
- `/portrait regen <온라인 플레이어> [모델]`: 유료 생성 후 저장하고 본인 클라이언트에 전달합니다. 다른 NPC와 대화하거나 /내일러스트로 볼 수 있습니다.
- `/portrait model <모델>`, `/portrait status [플레이어]`, `/portrait budget`, `/portrait reload`
- `/portrait reset <플레이어>`: 저장된 초상화 삭제. 자동 OFF면 다시 자동 생성하지 않습니다.
- 생성 명령은 키·레퍼런스·DB 준비 후 실행합니다. 이번 빌드 검증에서는 실행하지 않았습니다.

일반 사용자:
- `/내일러스트`: MagicCodex Fabric 클라이언트 명령. 현재 서버·현재 로그인 계정의 저장된 일러스트만 표시합니다. API/재생성/아이템 소모가 없습니다. ESC 또는 닫기로 나옵니다.
- ItemsAdder `item:reroll` 우클릭: 입력 후 재생성합니다. 아이템 정의는 별도 설치가 필요합니다.

## 중단·환급
DB PREPARED 의도를 먼저 기록합니다. 아이템 차감과 D 영수증은 같은 플레이어 데이터 저장에 남긴 뒤 PENDING으로 전환합니다.
성공 커밋은 DONE이며 환급하지 않습니다. 종료 시 진행 중 생성은 취소하고 재개하지 않습니다. PENDING은 다음 시작 때 REFUND_DUE로 전환합니다.
원래 차감한 서버에 다음 접속하면 D 영수증을 확인하여 원래 아이템 1개와 R 영수증을 함께 저장한 뒤 DB를 REFUNDED로 바꿉니다. R이 이미 있으면 재지급하지 않습니다.
빈 칸이 없으면 땅에 떨어뜨리지 않고 반환을 보류합니다. 영수증은 재처리 방지를 위해 보존합니다.
접속 교체·중복 콜백과 늦은 API 완료를 차단합니다. 저장 오류나 출처 불명인 옛 영수증 없는 건은 임의 지급하지 않고 확인 대상으로 남깁니다.

**한계:** DB와 인벤토리가 하나의 트랜잭션은 아닙니다. 이 설계는 Paper가 인벤토리와 PDC를 같은 플레이어 저장본으로 보존한다는 전제입니다.
디스크 손상·백업 불일치 복원·다른 플러그인이 인벤토리/PDC를 따로 동기화하거나 저장 실패를 숨기는 경우까지 exactly-once를 보장하지 않습니다.
school/wild 간 외부 인벤토리 동기화가 있다면 해당 플러그인의 PDC 동기화·접속 직렬화 보장을 먼저 검증해야 합니다.

## 스레드와 크기
클라이언트 파일 IO, SHA 검증, PNG 디코딩·알파 처리는 작업 스레드에서, GPU 업로드·렌더·텍스처 해제는 렌더 스레드에서 합니다.
이전 접속이나 이전 이미지의 작업 완료는 폐기합니다. HTTP 응답은 수신 중 12MiB를 넘으면 취소하며, 결과 PNG는 8MiB·2048px 제한을 따로 검사합니다.

## 운영 승인
자동 OFF도 onEnable에서 DB 연결, cport_* 테이블/인덱스 생성, 예산 기본 행 삽입과 잠금·미완료 요청·예약 복구를 합니다.
운영 DB 변경 승인이 없으면 설치·활성화하지 않습니다. SQLite fallback도 DB 파일 생성이므로 승인 대상입니다.
필요한 승인 범위: cport_portrait, cport_state, cport_reroll, cport_budget, cport_reservation, cport_usage 및 관련 인덱스, 초기화/복구 DML.
실제 키 설정과 유료 API 테스트는 별도 승인입니다.
reference/style-reference.png 및 reference/style-reference-full.png는 공개 Git/JAR에 넣지 않습니다.

## 검증
허용된 호스팅 명령:
```powershell
.\gradlew.bat :chaca-portrait-paper:test :chaca-portrait-paper:jar
.\magic-codex-fabric\gradlew.bat -p magic-codex-fabric test remapJar
```
단위 테스트는 가짜 계정/저장본과 HTTP subscriber를 사용하며 운영 DB/API를 호출하지 않습니다.
실제 Paper 저장·강제 종료·인벤토리 플러그인 동기화, GPU 화면과 인게임 대화 및 유료 생성은 별도 검증이 필요합니다.

## 스킨 우선 준비와 로컬 투명 처리 (2026-10-10)

정상 생성은 GPT-6 Luna 준비 1회 → 이미지 생성 1회입니다. GPT Image 2 선택 시에만 로컬 배경 제거를 추가합니다. 기존 요청 형식 거부/전송 전 연결 실패 재시도 외에 유료 재생성을 자동 반복하지 않습니다.

- Luna: `gpt-6-luna`, `reasoning.effort=low`, 기본 `max_output_tokens=2048`(reasoning 포함), strict JSON schema. 도구 실행은 없습니다. 스킨 관찰 사실, 가상 캐릭터 유형/표현, 사용자 요청, 불확실성을 분리합니다. 원본 스킨 앞뒤 합성 이미지도 이미지 생성 요청에 직접 첨부합니다.
- 동물/로봇 등 비인간형은 인간화하지 않습니다. 실제 사용자의 성별/나이를 추정하지 않으며 머리만으로 표현을 단정하지 않습니다. 명시된 가상 캐릭터 표현 요청을 반영하고 불명확하면 중립 유지. 고정 외형·스타일 규칙은 코드가 붙이며 Luna 출력은 데이터만 됩니다.
- 준비 비활성/예산 부족/빈 응답/거절/잘림/잘못된 JSON/통신 실패는 이미지 호출 전에 중단합니다. 자동 fallback이나 유료 재시도는 없습니다. 전용 출력 토큰 상한이 너무 작으면 최소 1024로 제한합니다.
- Luna 추가 예약 비용 예시: 입력 3000 추정 토큰 × $0.10/1M + 출력 상한 2048 × $0.50/1M = **$0.001324**. 이는 예상 예약액이며 실제 이미지 비용은 별도입니다. 정상 응답은 반환 input/output usage(출력의 reasoning 포함)와 설정 단가로 정산합니다. 캐시 할인 등 청구서와 차이가 있을 수 있습니다. usage를 확정할 수 없는 실패는 예약액으로 보수적으로 정산합니다.
- GPT Image 2에는 `background=opaque`와 균일한 밝은 회색 배경을 요청합니다. GPT Image 1.5는 설정의 네이티브 배경 모드를 유지합니다. 모델 2 결과는 서버 작업 스레드에서 CPU GrabCut → 외부 배경 알파 마스크 → 경계 완화 → PNG 크기/투명 검사 후에만 저장합니다. 라이브러리 준비 실패는 API 요청 전에 차단합니다.
- 공식 Maven Central: `org.bytedeco:opencv:4.14.0-1.5.14`, `javacpp:1.5.14`, `openblas:0.3.34-1.5.14`. Windows x64 네이티브 포함. OpenCV/JavaCPP는 Apache 2.0 선택, OpenBLAS는 BSD 3-Clause, 번들 Microsoft 런타임은 해당 배포 라이선스를 따릅니다. 학습 모델이나 외부 제거 서비스가 없고 이미지가 다른 서비스로 전송되지 않습니다. OS 전역 설치 없이 공식 JavaCPP 번들 런타임을 로드합니다.
- 처리 제한: 서버 JVM별 1건, OpenCV 스레드 1개, 분리 마스크 긴 변 512px, 입력 최대 2048px, 결과 PNG 최대 8MiB. 두 서버가 수동 요청을 동시에 받으면 호스트 전체 최대 2건입니다. 네이티브 메모리는 Java 힙 한도 밖에도 존재하므로 서버별 수백 MiB의 여유가 필요합니다(정확한 운영 최대 사용량은 미측정).
- 한계: 학습된 의미 분할이 아닌 GrabCut입니다. 단순 색상 전체 삭제는 하지 않고 내부의 배경색 의상 구멍을 채워 보존하지만, 팔 사이처럼 완전히 둘러싸인 배경이 남을 수 있습니다. 가는 머리카락/배경과 비슷한 윤곽은 완전 보장하지 않습니다. 균일 배경/마스크 면적 검사를 통과하지 못하면 기존 그림을 유지합니다. 합성 테스트 성공이 실제 생성 이미지 품질 보장은 아닙니다.
- 현재 슬롯만 성공 트랜잭션에서 교체합니다. 기존 `running_since`를 단조 증가하는 세대 토큰으로 사용해 오래된 결과의 저장/잠금 해제를 차단합니다. 스키마 추가 없음. 실패/취소는 이전 정상 이미지 유지. 클라이언트는 성공 업로드 후 이전 텍스처를 닫고 단일 원자적 생성 캐시를 교체합니다. tests/ 비교 결과나 사용자 파일은 삭제하지 않습니다.

공식 근거: https://developers.openai.com/api/docs/models/gpt-6-luna · https://developers.openai.com/api/docs/guides/reasoning · https://docs.opencv.org/4.13.0/dd/dfc/tutorial_js_grabcut.html · https://opencv.org/license/ · https://github.com/bytedeco/javacpp-presets

## GPT Image 2.5 운영

기본 모델은 `gpt-image-2.5-sunburst`, 기존 품질 `medium`, 크기 `1024x1536`입니다.
`/portrait model gpt-image-2.5-flare`도 지원하며 GPT Image 2 / 1.5 선택은 유지합니다.
`/portrait model <모델>` → `/portrait regen <온라인 플레이어>` → `내일러스트` 순서로 확인합니다.
비교 전용 관리자 명령과 작업 경로는 제거했습니다. 이전 tests/ 결과 파일은 삭제하지 않습니다.

2.5에는 `background=transparent`, `output_format=png`, `n=1`을 보내며 input_fidelity를 생략합니다.
네 귀퉁이 알파까지 검사하고 불투명 결과는 실패 처리합니다. 2.5에는 GrabCut이나 회색 배경 지시를 적용하지 않습니다.
2.5는 low/medium/high/auto/xhigh/max를 지원하지만 기본 품질을 자동으로 높이지 않습니다.
기존 모델로 전환할 때 지원하지 않는 품질은 호출 전에 거부합니다.

Standard USD/100만 토큰 단가는 두 2.5 모델 모두 텍스트 입력 5, 이미지 입력 8, 캐시 이미지 입력 2, 이미지 출력 30입니다.
단가가 같아도 장당 토큰 수와 비용이 같다는 뜻은 아닙니다. GPT Image 2 토큰 계산기를 2.5 비용 근거로 쓰지 않습니다.
`estimate.image-2-5`는 별도로 조정하는 보수적 예약 휴리스틱이며 실제 비용 상한이 아닙니다.
기본 medium 이미지 예약은 $0.4290이며 Luna 준비 비용은 별도입니다. 실제 응답 usage로 정산합니다.
현재 Images usage 스키마는 캐시 이미지 토큰을 분리하지 않으므로, 캐시 단가는 기록하되 할인 토큰을 추정하지 않습니다.
따라서 플러그인 장부는 캐시 할인된 실제 청구액보다 높을 수 있습니다. usage 누락·시간 초과도 예약액으로 보수 정산합니다.

공식 자료: [요청 옵션](https://developers.openai.com/api/reference/resources/images/methods/edit),
[모델별 품질·투명 배경](https://developers.openai.com/api/docs/guides/image-prompting),
[Sunburst 가격](https://developers.openai.com/api/docs/models/gpt-image-2.5-sunburst),
[Flare 가격](https://developers.openai.com/api/docs/models/gpt-image-2.5-flare).
실제 유료 생성, API 계정 접근성, 인게임 표시 품질은 별도 검증 대상입니다.
가격표 YAML 키는 Bukkit 경로 구분자 충돌을 피하려고 gpt-image-2_5-sunburst / gpt-image-2_5-flare로 저장합니다. 실제 API 모델 ID는 점이 들어간 2.5입니다.
