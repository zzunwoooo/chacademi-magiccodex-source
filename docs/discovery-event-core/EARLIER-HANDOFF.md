# 발견 이벤트 코어·수동 조건·공유 기증 정책 인계

## 현재 상태
작업 경로 C:/Chacademi/staging/discovery-event-core-20261011-task3/source. 원본 f44666f689714d5292adfff6e36c8586d27760c0 및 호스팅 미커밋 채팅 패치를 독립 스냅샷으로 보존하고 변경했다. 원본 작업 폴더, 운영 서버·런처·DB를 수정하지 않았다.

발견조건 검수 JSON으로 확인되는 대상은 90개(62+28, 중복 없음)다. 이것은 VFX 검수 수가 아니다. 사용자 기억의 99/100개가 VFX/발견조건 중 어느 검수 집합인지, 별도 최종 목록은 미확인이다. review-evidence.json에 정확한 경로·revision·시간·SHA256이 있다.

최신 결정 후 상태: 수동 조건 명령 64개 / 자연 발생원 연결 14개 / 자연 조건의 근거 확인 대기 6개 / 현재 ID 승계 대기 6개. 숫자는 소스 경로 상태이며 실게임 완료 수가 아니다. condition-inventory.csv의 final_route를 사용한다. 이전 status_audited는 변경 전 비교용이다.

## 수동 조건 명령
사용자가 책·수업·행동 횟수는 직접 배치할 권한 명령으로 대체하도록 승인했다. manual-conditions.yml의 명시적 64개만 대체한다. 정확한 원문·ID·노드·명령은 manual-condition-replacements.csv에 있다. 기존 discoveries.yml을 지우지 않고 별도 overlay가 해당 마법의 자동 이벤트 인덱스와 기존 확률을 제거한다. 수동 지급 명령 자체가 성공 판정이며 수업 확률을 임의로 재추첨하지 않는다.

```
/유저관리 <한글닉네임 또는 영문계정> 마법 조건지급 바람바구니
/유저관리 <유저> 마법 조건조회 wind_basket
/유저관리 <유저> 마법 조건회수 wind_basket
/유저관리 <유저> 마법 조건초기화 wind_basket
```

기존 DisplayNames.resolve / AdminCommandRules.resolve / 자동완성을 재사용한다. 공백 있는 닉네임·마법명 및 ID를 지원하며 중복 이름은 거절한다. 접속 중이고 발견 데이터 로딩이 끝난 대상이 필요하다. 기존 magicdiscovery.admin 권한자 또는 콘솔만 사용한다.

조건지급은 특정 magic.condition.<spell_id>만 true로 동기화하고, 기존 발견 트랜잭션에서 진행 키 trace.condition.<spell_id>와 습득 기록을 저장한 뒤 기존 magic.learned.* 권한·발견 UI 알림·최초보상 경로를 사용한다. OP나 wildcard를 지급하지 않는다. 개별 /lp 명령만 실행해서는 발견 알림을 만들지 않으므로 위 명령을 배치해야 한다.

반복 지급은 신규 발견 토큰/최초보상/새 알림을 추가하지 않는다. 이미 관리자 습득 상태인 마법도 새 최초 발견으로 만들지 않는다. 조건회수는 조건 노드만 false로 하고 습득은 유지한다. 조건초기화는 조건과 습득을 false로 바꾸되 역사적 최초 발견·알림 확인·보상 기록을 유지한다. 초기화 후 재지급도 최초보상/알림을 재발행하지 않는다. 기존 습득/미습득 명령도 유지한다.

기존 DiscoveryStore/Session/LearnedSpells/LuckPerms/DiscoveryProtocol을 재사용한다. 수동 조건용 새 DB 테이블은 만들지 않았다. DB 성공 후 권한 동기화 실패는 명령 결과에 별도로 표시하고 재접속/동일 명령 재시도로 복구한다. 다른 서버에서 조건만 회수한 경우도 기존 1분 재확인에서 동기화된다.

## 자연 이벤트 코어
매 틱 전체 접속자·전체 마법 순회를 제거했다. 현재 불붙은 대상·잠수 대상·고온 공중 회전 대상만 매 틱 추적한다. 이동한 대상의 질주 통계만 주기 처리하고, 몬스터 조건은 미습득 후보를 버킷으로 분산한다. 광범위 월드/블록 스캔과 매 이벤트 DB 조회는 없다. 기존 dirty 저장과 서버 간 주기 재확인은 유지한다.

추가된 자연 상태: 물결 숨결은 눈 위치가 WATER인 상태에서 생존 600틱, 차가운 바다 방문은 Minecraft COLD_OCEAN/DEEP_COLD_OCEAN, 최대마나 조건은 기존 ManaService 실제 snapshot.maximum 2500 이상이다. 온도는 기존 ClimateService 섭씨 값을 그대로 전달하고 임의로 바닐라 온도를 환산하지 않는다. 기숙사 배정은 기존 성공한 관리자 배정 저장 뒤 신호를 전달한다. 마나 서비스와 발견 데이터 로드 순서 차이는 대상 1명의 메모리 스냅샷만 최대 3회 재확인한다.

초기 자연 연결 12개 (후속 2개는 아래 추가 인계 참고): wind_message, storm_command, dust_duster, wind_decoy, lightning_needle, storm_crown, rekindle, sponge_drying, flame_footprints, crimson_breath, water_breath, ice_bridge.

자연 조건 8개는 임의 완성하지 않았다:
- trajectory_acceleration: 실제 4계절 이상현상 이벤트 ID/완료 시점.
- air_stacking: 아카데미 모든 스팟의 승인 목록·영역.
- flame_forging: 대장간 위치/모루 대상.
- phoenix_oath: 실제 불사조 펫 ID와 획득 완료 출처.
- falling_star: 최신 화염 마법 전체 목록과 기존 31개 prerequisites 대조.
- crimson_heart: 화염 마법 처치 출처와 피해/처치 책임 정보.
- life_distribution: 동료 정의 및 실제 회복받은 자기+2명의 판정.
- current_sorting: 물속 금광석의 바닐라/커스텀 블록 정의와 침수 판정 범위.

물고기·눈덩이·보트는 초기에는 자동 이벤트로 연결했지만 최신 사용자 결정에 따라 수동 overlay에서 자동 판정을 비활성화했다. 실게임 테스트는 아직 하지 않았다. 정지 중 침수는 EntityAirChangeEvent로 추적을 시작한다. 외부 setFireTicks 연동은 실게임 확인 대상이다.

## 기증 정책
기증은 배운 마법의 학교 최초 등록과 기숙사 점수 지급이며 개인 발견/습득과 별개다. 기존 SchoolStore를 확장했다.

```
/기증기록 상태 <마법ID 또는 한글명>
/기증기록 금지 <마법ID 또는 한글명>
/기증기록 해제 <마법ID 또는 한글명>
/기증기록 초기화 <마법ID 또는 한글명 또는 all> 확인
```

권한 magiccodex.school.admin. 기록 초기화는 기숙사 점수·금지 정책·개인 습득을 유지한다. 금지/해제는 기록과 점수를 유지한다. 상태 조회는 비동기로 저장소를 다시 읽는다. 금지 요청은 운영에서 한 번도 실행하지 않았다.

새 정책 테이블은 MariaDB codex_donation_policy / SQLite donation_policy이며 spell PK, blocked INT를 가진다. SchoolStore 초기화 시 CREATE TABLE IF NOT EXISTS로 마이그레이션하는 코드다. school/wild는 기존 database.properties가 같은 MariaDB를 가리켜야 공유된다. 운영 설정을 조회하거나 수정하지 않았다.

기증 요청은 기존 비동기 트랜잭션 안에서 정책 행을 확보·잠그고 금지 여부를 확인한 뒤 기증 기록과 점수를 기록한다. 정책 변경도 같은 행 잠금을 사용한다. MariaDB는 SELECT FOR UPDATE, SQLite는 선행 INSERT OR IGNORE 쓰기 잠금을 사용한다. 오래된 클라이언트/서버 캐시로 금지를 우회할 수 없다. 2연결 SQLite 테스트로 확인했고 실제 MariaDB 테스트 스키마에서의 실행은 미실시다.

## 빌드·통합 경계
사용자가 ModelEngine 4.0.8/MythicMobs 5.9.0의 지정 JAR에 대한 컴파일 전용 읽기를 명시 승인해, 거절된 흐름을 재시도했고 성공했다. JAR 내용을 추출·출력·Library 업로드하지 않았다. 발견/Bridge 호스팅 테스트·빌드 결과와 SHA256은 validation.json에 있다.

MagicCodexBridge 본체/PartyIdentityBridge/공용 Protocol/클라이언트 채팅 파일은 수정하지 않았다. Bridge 변경은 SchoolBridge/SchoolStore/DiscoveryLink/AdminCommandBridge/ClimateService/ManaService 및 모듈 버전·테스트다. 채팅 최신본과 마지막 한 번의 통합 빌드 후 배포해야 한다. 이 패치의 전체 소스 스냅샷을 채팅 작업 폴더에 덮어쓰지 않는다.

운영 배포·재시작·운영 스키마 실행·실데이터 변경·NPC/퀘스트 명령 배치·공개 push는 하지 않았다. 사용자가 명령을 직접 배치한다. 검토 ZIP에는 공개 소스·패치·문서만 있으며 JAR, 설정 비밀, DB, 유료 자산은 없다.


## 후속 소스 확인·구현 결과

- 현행 portable-vfx-runtime spell-catalog.yml의 reviewed:true는 97개, false는 202개다. 발견조건 검수90개와 다른 목록이며 99/100으로 반올림하거나 번호1~100으로 대체하면 안 된다.
- 자연 조건 최종 연결은14개다. phoenix_oath는 자체 MCPets/taming 설정의 ca_phoenix_{flame,darkflame,light,thunder}{,_shiny} 정확한8종을 기존 교화 권한 저장 성공 이후에만 연결한다. 일반 LP 직접지급/기존보유 소급 검증은 미구현이다.
- current_sorting은 취소되지 않은 GOLD_ORE/DEEPSLATE_GOLD_ORE BlockBreakEvent에서 바로 위 블록이 WATER이고 Levelled.level=0인 경우만 인정한다. 원본 광석의 상면 위에 완전한 수원 블록이 있는 보수적 침수 기준이다. 옆면만 닿음, 흐르는 물/떨어지는 물만 존재, 네더 금광석, 금 블록은 제외한다. 한 블록 위만 검사한다. 사용자 확정: 바닐라 두 광석.
- 생명의 분배 동료는 사용자 확인으로 같은 파티원이다. 본인+다른 파티원2명이 한 투척용 치유 물약으로 실제 체력이 증가해야 한다. 현재 구현 완료 아님. Velocity PartyBook은 권위 있는 영속 파티 정보지만 Paper 질의 API가 없다. 새 전용 서버 메시지 채널과 서버 발행 nonce/응답 검증이 필요하다. 클라이언트 주장이나 매회 DB 조회로 대체하면 안 된다.
- 기본 Paper1.21.4 EntityRegainHealthEvent는 amount/reason만 제공하고 원인 물약 참조가 없다. PotionSplashEvent 영향 대상과 다음 틱 HP 차이만으로는 다른 회복을 오인할 수 있으므로 완성 판정하지 않았다. 원인 물약과 실제 회복을 연결하는 신뢰 가능한 서버 훅이 남는다.
- 기존 메테오 선행조건31개는 부족하다. 현행299 ID와 이전 명시적 category=fire 자료의 교집합49개에서 본인을 뺀48개 중17개가 빠져 있다. fire-category-audit.json에 모든ID를 보존했다. 신규9개에는 해당 자료의 category 근거가 없으며 이름만으로 분류하지 않았다.
- 확인되지 않은 자연6개는 Definitions.PENDING으로 이벤트·메타·상태 자동 인덱스에서 제외했다. 특히 불완전한31선행조건으로 메테오를 조기 지급하지 않는다. 명시적인 기존 관리자 습득 제어는 유지한다.
- 계절 소스에는 가을 토네이도와 여름/겨울 물속성 시각 효과가 있지만 4계절 이상현상 완주 이벤트 계약은 확인되지 않았다. 이들을 임의로 동일시하지 않았다.
- 전투 SpellRuntime은 Mythic에 damage 수치를 전달하나 spell/element가 귀속된 피해 이벤트는 없다. SeasonalSpellMechanics의 명시적 element 피해 경로는 존재하지만 모든 화염마법 피해를 포괄한다는 근거가 없어 홍련의 심장 조건을 가짜 킬 이벤트로 대체하지 않았다.
- feather_fall은 현행 icon/baseline-icon에서 feather_step 이미지를 재사용한다. 이는 에셋 승계 증거일 뿐 발견조건 승인 승계 증거는 아니다. 검수 퇴역6개는 자동 이전하지 않았다.

## MariaDB 후속 검증

기존 승인된 chaca_audit_20261010_task5 스키마를 재사용했다. 내부 연결 프로필은 출력하지 않고 URL의 스키마만 고정 교체했다. SELECT DATABASE() guard 통과 후에만 SchoolStore를 열었다. 운영 스키마/운영 기증 기록은 읽거나 쓰지 않았다. 테스트는 고유 audit_policy UUID의 가상 기록만, 점수0으로 실행했다. 다른 테스트자료 삭제는 없고 가상 기록은 유지했다.

첫 동시성 테스트에서 MariaDB1213/40001 교착을 실제 발견했다. INSERT IGNORE의 공유 잠금 후 FOR UPDATE로 승격하는 경합이었다. 정책 행을 INSERT ... ON DUPLICATE KEY UPDATE spell=VALUES(spell)로 처음부터 배타 잠금하도록 수정했다. 이후 서로 다른 연결에서 금지 우회 차단, 초기화 후 금지 유지, 해제, 동시 기증 단일 성공, 재연결 정책 유지가 통과했다. 테스트 결과는 mariadb-policy-result.txt다.

추가 충돌 파일: TamingBridge.java(성공 콜백 한 곳). PartyIdentityBridge/PartyPlugin/공용Protocol은 변경하지 않았다. 이후 생명의 분배를 연결할 때 Velocity 전용 서버 메시지 훅은 채팅 담당과 한 최종 통합 단계에서 합쳐야 한다. 소스 패치를 원본에 덮어쓰면 안 된다.

검수 퇴역6개도 PENDING에 포함해 옛 ID 자동 지급을 차단했다. 승계가 확정되면 현행 ID와 원래 승인조건을 함께 매핑해야 한다.
