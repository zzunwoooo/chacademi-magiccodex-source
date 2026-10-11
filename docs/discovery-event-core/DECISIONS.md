> 이전 결정 요청 당시의 기록입니다. 이후 사용자 승인으로 수동 64개 대체·기증 정책·컴파일 읽기가 확정됐습니다. 현재 상태는 HANDOFF.md와 condition-inventory.csv의 final_route를 우선하세요.

# 추가 검증 및 결정 목록

검수 90개는 신규 소스 연결 3 / 기존 발생원 연결 28 / 발생원 미연결 45 / 조건 또는 ID 결정 필요 14로 구분한다. 실게임 완료를 의미하지 않는다. 기존 정의 62 + 신규 정의 3 + 정의 없음 25라는 구조와 혼동하지 않는다. 미연결 45에는 정의는 있으나 외부 신호 공급자가 없는 항목이 포함된다. 결정 필요 14는 조건 차이 8개와 현행 ID에 없는 검수분 6개다. 미검수 209개 및 역사적 ID는 CSV에서 별도 확인한다.

기존 연결 28은 일반 행동 자동 판정 27개에서 현행 ID가 없는 feather_step/wind_vault를 뺀 25개와 friend.added, cast.vacuum_cleave, mana.trajectory_acceleration의 발생경로 3개다. 미라지 슈터 현재 cast 설정은 마나 10을 소비하며 ManaBridge 성공 응답 경로에서 DiscoveryLink.cast로 전달한다. 실제 패시브나 별도 스킬에서 쓰는 마나까지 모두 포함된다는 검증은 하지 않았다.

## 승인 원문과 후속 카탈로그 차이
|ID|검수 원문|후속 카탈로그|
|---|---|---|
|flight|학교 도서관에서 비행 연구 기록을 읽기.|바람 마법 비행 실습 수업을 완료하기.|
|far_touch|학교 도서관에서 '기계에 대한 이해' 책을 읽기|레버·버튼·압력판을 각각 10회 작동시키기.|
|aerial_haul|학교 도서관에서 '부유물의 무게와 균형' 책을 읽은 뒤 손길 너머 50회 사용하기.|손길 너머를 누적 50회 사용하기.|
|falling_star|모든 화염 마법 습득|메테오 스트라이크를 제외한 모든 화염 마법 습득하기.|
|flame_leap|학교 도서관에서 숨겨진 '체술 강화' 책 읽기.|달리며 몬스터에게 근접 공격을 30회 명중하기.|
|heat_recovery|학교 도서관에서 '에너지에 관하여' 책 읽기.|몸에 붙은 불을 누적 10회 끄기.|
|firebird_hunt|학교 도서관에서 '불사조, 주작, 가루다' 책 읽기.|불새 관측 실습을 완료하기.|

falling_star의 자기 자신 제외는 기존 문서에도 명시되어 있어 단순 정책 충돌로 단정하지 않는다. 최신 화염 전체 목록과 실제 31개 prerequisites의 일치 여부를 확인해야 한다.

## 실행 정의와 검수 원문 차이
- aerial_haul: 실행 정의 state.circle=6 / 검수 원문은 책을 읽고 far_touch 50회. 후속 카탈로그는 책 조건을 뺀 50회다. 세 버전을 구별해야 한다.
- descent_command: 실행 정의 cast.pressure_press.circle6=1 / 검수 원문과 카탈로그는 공중 몬스터에게 pressure_press 50회다.

## ID 차이
현행 목록에서 빠진 옛 ID: feather_step, wind_vault, dampen_powder, handful_of_snow, wet_footprints, pressure_weight, cold_storage, liquid_barrier, water_rebound, thaw_pulse.
현행 신규 ID: bouncing_droplet, bubble_aria, concrete_curing, feather_fall, healing_rain, pressure_hammer, pressure_lance, ripple_guard, waterspout.
이 중 검수 목록과 겹치는 퇴역 ID는 feather_step, wind_vault, handful_of_snow, wet_footprints, pressure_weight, cold_storage 6개다. 임의 승계는 하지 않았다.

## 기증
배운 마법을 학교에 최초 등록하고 기숙사 점수를 얻는 별도 기능이다. 개인 습득 상태를 지우거나 넘기는 기능이 아니다. SchoolStore.donate는 마법 ID를 PK로 하는 기증 기록과 점수를 같은 트랜잭션에서 처리한다.
기존 명령 /기증기록 초기화 <all|마법ID> 확인, 권한 magiccodex.school.admin. 기록만 삭제하고 기숙사 점수는 유지한다.
SQLite: MagicCodexBridge 데이터 폴더 school.db의 donations/house_points. MariaDB: codex_donations/codex_house_points. 설정은 데이터 폴더 database.properties에서 읽는다. 실설정이나 운영 DB는 이번에 조회하지 않았다.
현재 기증 테이블 필드는 spell, spell_name, donor, nickname, house, created이며 금지 필드가 없다. 미기증 마법도 포함한 서버 공용 영속 금지는 별도 정책 테이블이 적합하며 스키마 추가가 필요하다. 서버별 YAML은 DB 변경 없이 가능하지만 공용 상태를 보장하지 못한다. 금지/해제 코드는 아직 추가하지 않았고 기존 기증 기록도 변경하지 않았다.

## 빌드 차단의 정확한 범위
원본 의존성 폴더 C:/Chacademi/staging/audit-fixes-20261010-task5/source/plugins/:
- ModelEngine-4.0.8.jar (ModelEngine 4.0.8)
- MythicMobs-5.9.0.jar (MythicMobs 5.9.0)
- PlaceholderAPI-2.11.6.jar (무료 의존성도 같은 시도에 포함)
차단된 시도는 위 파일을 독립 staging의 plugins에 하드링크한 뒤, 호스팅 JDK21/기존 Gradle 캐시로 다음 명령을 실행하려던 것이다:
`gradlew.bat --no-daemon --console=plain :magic-codex-paper:test :magic-codex-paper:jar`
작업 디렉터리 C:/Chacademi/staging/discovery-event-core-20261011-task3/source.
심사 거절 사유 원문: "The build would read the explicitly prohibited paid plugin JARs (via hardlinks) for compilation, despite the stated ban on reading paid assets."
시도 전체가 실행 전에 거절되었고 재시도/우회하지 않았다. 필요한 좁은 승인은 기존 ModelEngine/MythicMobs JAR의 호스팅 컴파일 전용 읽기다. 내용 추출·출력·업로드·운영 교체는 포함하지 않는다. 이번 복구 후에는 빌드를 다시 실행하지 않았다.

## 검증·배포
validation.json은 이전 성공한 발견 모듈 23개 테스트와 JAR SHA256을 보존한다. Bridge는 빌드 미완료. 실제 Paper 행동 및 school/wild 이동 실험은 미실시. 운영 배포·재시작·DB/기증 기록 변경 없음.
ZIP은 공개 Java/Gradle/YAML 소스, 변경 패치, 이 문서와 조건 CSV/검증 JSON만 포함한다. JAR/개인 설정/DB/리소스팩/유료 자산은 포함하지 않는다.
