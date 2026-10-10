# ChacaPortrait 사용법과 검증 경계

- 자동 생성 기본값은 `auto.first-join: false`입니다. 관리자 수동 생성과 별개입니다.
- 자동 생성을 켜도 `server-id: school`에서만 생성합니다. wild는 자동 생성하지 않습니다.
- 전역 `enabled: false`는 모든 유료 생성(관리자 포함)을 막습니다.
- 키는 `CHACAPORTRAIT_OPENAI_KEY`, 없으면 `CHACANPC_OPENAI_KEY` 환경변수만 사용합니다.
- 공유 DB에서 서버마다 고유 server-id가 필요합니다. 재시작 복구가 다른 서버 작업에 영향을 주면 안 됩니다.

## 명령
관리자 권한 `chacaportrait.admin`(기본 OP):
- `/portrait regen <온라인 플레이어> [gpt-image-2|gpt-image-1.5]`: 유료 생성 후 저장하고 본인 클라이언트에 전달합니다. 다른 NPC와 대화하거나 /내일러스트로 볼 수 있습니다.
- `/portrait test <온라인 플레이어> both`: 유료 모델 비교. tests/ 폴더에 파일을 쓰며 플레이어의 저장 초상화는 교체하지 않습니다. 예산 예약·정산·사용 내역은 DB에 기록됩니다.
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
