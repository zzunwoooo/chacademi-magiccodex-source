# 현행 검수97 발견 이벤트 코어 인계

## 현재 결과
현행 spell-catalog.yml reviewed=true 97개를 기준으로 한다. 이전 발견조건 검수90개와 교집합81, 현행만16, 옛 목록만9다. 정확한 한글 이름·ID·현재 조건·옛 승인조건·근거 파일·연결 경로는 current97-vs-old90.csv, 요약은 current97-summary.json에 있다. 번호1~100으로 가정하지 않았다.

현행97: 수동 조건 명령77 + 자연 이벤트18 = 소스 연결95개, 미완성2개. 실게임 완료 수가 아니다. 수동 설정 전체80개에는 기존 승인 목록의 현행 reviewed=false 3개(aerial_haul/fireproof_film/water_mirror)가 유지된다. 퇴역6개는 현행 대상 밖이며 옛 ID를 되살리지 않았다. 현행 feather_fall의 조건은 실제001 YAML에서 독립적으로 확인했다.

권위 있는 현재 정의는 magic-codex-fabric/src/main/resources/assets/magiccodex/default-spells의 번호별299 YAML이다. current-catalog-authority.json에 모두 기록했다. 그중 category=fire49개, 메테오 자신을 뺀48개를 prerequisites로 정확히 반영했다. 카탈로그 전체를 매 이벤트 읽지 않고 시작 시 인덱스만 만든다. 테스트가 현재 원본 fire 집합과 정확히 비교한다.

## 명령과 기존 저장 체계
```
/유저관리 <한글닉네임 또는 영문계정> 마법 조건지급 <한글마법명 또는 ID>
/유저관리 <유저> 마법 조건조회 <마법>
/유저관리 <유저> 마법 조건회수 <마법>
/유저관리 <유저> 마법 조건초기화 <마법>
/기증기록 상태|금지|해제 <한글마법명 또는 ID>
/기증기록 초기화 <마법 또는 all> 확인
```
관리자 권한은 각각 magicdiscovery.admin / magiccodex.school.admin. 기존 한국어 유저 관리·이름 해석·자동완성을 재사용한다. 조건 지급은 접속하여 세션이 로드된 대상에게만 가능하다. magic.condition.<id>, trace.condition.<id>와 기존 learned/DiscoveryStore/LuckPerms/알림·최초보상을 사용한다. 중복 지급·초기화 후 재지급으로 새 최초보상이나 중복 발견 알림을 만들지 않는다. 회수는 조건만, 초기화는 조건+습득을 취소하되 역사적 최초기록·보상기록을 보존한다.

기증은 학교 도감 최초 기증/기숙사 점수이며 개인 발견과 별개다. 초기화는 기증기록만 지우고 점수·금지정책·개인습득을 유지한다. 금지정책은 MariaDB codex_donation_policy / SQLite donation_policy. 승인된 격리 MariaDB에서 실제 교착1213을 재현해 정책행의 배타 upsert 잠금으로 고쳤으며 차단·해제·동시단일성공·재연결 지속을 검증했다. 운영에는 명령도 스키마도 실행하지 않았다.

## 생명의 분배
PartyHealing/PotionRecovery가 한 PotionSplashEvent의 대상 UUID·물약 강도·회복 전 체력/최대체력을 묶는다. 같은 틱의 INSTANT_HEALTH EntityEffectTickEvent 후 MAGIC EntityRegainHealthEvent가 취소되지 않고, 예측되는 바닐라 즉시 회복량과 맞으며 다음 틱 실제 체력 증가량까지 일치해야 한다. 만피/0증가/취소/중복 물약/다른 회복/중간 피해는 보수적으로 제외한다. 실제 호스팅의 공개 Purpur1.21.4 바이트코드에서 splash→effectTick→MAGIC regain 순서와 반올림 공식을 확인했다. 체력 직접 setHealth를 수행하거나 MONITOR에서 이벤트를 변경하는 외부 플러그인까지 원인을 증명하는 계약은 아니며 실게임 교차 검증은 아직 없다.

본인과 최소 다른2명이 실제 회복한 경우에만 Velocity에 1회 조회한다. chacademi:party_discovery 별도 서버 채널, 임의 UUID nonce, 요청/응답 크기 제한, 대상 연결 일치, trusted school/wild, 40틱 만료, pending128/context128 제한을 사용한다. 클라이언트 요청은 handled 처리 후 거부한다. DB 조회나 온라인 전체 순회는 없다. Velocity PartyBook에서 직접 답하며 영향 시점 이후 파티 변경이 있었으면 보수적으로 실패시킨다. 모든 프로세스가 현재 같은 호스팅 OS 시계를 사용한다는 전제 아래 발생 시각부터2초 이내 응답만 인정한다. 여러 호스트로 분리할 때는 이 시각 계약을 revision 방식으로 교체해야 한다. 파티 조회의 큐 지연/다른 파티 변경도 보수적으로 실패할 수 있다.

## 추가 자연 조건
- 사금 추출: 취소되지 않은 바닐라 GOLD_ORE/DEEPSLATE_GOLD_ORE 채굴, 바로 위가 WATER source(level0). 옆면 접촉/흐르는 물만 있음/네더 금광석/금 블록 제외. 한 블록만 검사.
- 불사조: 자체 MCPets/taming manifest의 정확한 phoenix8종, 기존 교화 LP 저장 성공 이후 신호. 외부 LP 직접 지급이나 기존 보유 소급 판정은 미구현.
- 수압해머: 20 이상 fallDistance에서 철퇴로 몬스터/플레이어에 취소되지 않은 양의 근접 피해. 실제 서버 이벤트 순서 실게임 검증은 남는다.
- 용오름: 성공한 light_leap 시전 시 ClimateService의 실제 SUMMER + 활성월드 + Paper is_ocean biome tag. 해당 태그에 등록된 커스텀 바다도 포함하며 이름 추측은 없다.
- 메테오: 현재 정의의 화염49개 중 자신을 제외한48개 습득.

## 추가 승인된 수동 연결3개
trajectory_acceleration(미라지 슈터), air_stacking(허공 쌓기), flame_forging(불꽃 단조)는 부모가 사용자의 직접 배치·수동권한 대체 결정 범위로 승인했다. 현재 manual-conditions.yml에 활성화되어 있고 기존 조건지급/조회/회수/초기화 명령을 그대로 사용한다. 장소 좌표나 계절 현상 ID를 임의로 만들지 않는다. 이전 proposed-location-season-manual.yml은 승인된 내용의 참고 사본이며 별도 배치할 필요가 없다.

## 남은2개 — 사용자 답변 전 변경하지 않음
- crimson_heart(홍련의 심장): 하트1칸 이하 화염마법 몬스터 처치. 실제 화염 피해·시전 ID 귀속이 현행 시각 경로에 없으므로 자동 조건을 임의 대체하지 않았다.
- bouncing_droplet(도약하는 물방울): 실제095 YAML 원문이 '획득 조건은 추후 검토합니다.'이다.
부모가 두 조건의 임시 수동 연결 여부를 사용자에게 질문한 상태다. 답변 전 활성화하지 않는다.

## 통합과 검증
호스팅 격리 경로 C:/Chacademi/staging/discovery-event-core-20261011-task3/source, branch codex/discovery-event-core-20261011. 최신 채팅 작업 폴더는 수정하지 않았다. 공용 Protocol과 PartyIdentityBridge는 미변경이다. 새 파티 채널 때문에 PartyPlugin.java와 해당 build.gradle은 채팅 담당과 최종 한 번 통합해야 한다. Bridge 변경은 DiscoveryLink/관리·기증/온도·마나/교화 성공 콜백이며 MagicCodexBridge 본체를 덮어쓰지 않는다. velocity-party-discovery.patch를 별도로 제공한다.

validation.json은 최신 빌드·테스트 합계·산출물 SHA256, integration-check.json은 원본 내용 충돌 검사 결과다. 기존 approved chaca_audit_20261010_task5에 고유 가상 기록·0점으로만 검증했고 운영 DB는 건드리지 않았다. 운영 배포/재시작/NPC 배치/실데이터 초기화/공개 push 없음. ZIP에는 소스·문서·패치만 있고 JAR·유료자산·프로필·DB는 없다.

최종 패치는 --full-index로 생성한다. patch-base.json에 격리 snapshot commit과 공개 원본 commit을 기록했다. 부모의 최신 채팅 작업에는 3-way로 통합해야 하며 단순 파일 덮어쓰기는 금지한다. 이 단계에서 통합이나 배포를 실행하지 않았다.

패치 적용 선택: 전체 discovery-event-core.patch 하나, 또는 discovery-paper-bridge.patch + velocity-party-discovery.patch 쌍 중 하나만 선택한다. 전체 패치와 분리 패치를 중복 적용하면 안 된다.

배포 준비 주의: MagicDiscovery는 기존 discoveries.yml/manual-conditions.yml을 자동 덮어쓰지 않는다. 후속 승인된 통합·배포 단계에서 본 패치의 두 리소스 정의(메테오48/수동80 포함)를 기존 정의와 검토·병합해야 한다. JAR만 교체하면 기존 정의 파일 때문에95개 매핑이 모두 활성화되지 않을 수 있다. 현재는 운영 정의 파일을 읽거나 변경하지 않았다.
