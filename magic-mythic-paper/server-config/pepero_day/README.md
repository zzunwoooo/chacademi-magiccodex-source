# 빼빼로데이 3인 던전 「초코 막대 공방」 적용 안내 (2026-11-11, 11/11 아침 개편 — 서버·실행기 아직 미적용)

기획: Desktop\클로드\빼빼로데이던전\기획.md · 게임 시험 안 함.
11/11 개편: 보스 진행(짤패턴 6·기믹 7)을 yml 에서 **애드온 chapepero (PeperoBoss.java)** 로 옮김. 체력 12000, 그로기 없음.

## 파일
| 무엇 | 레포 위치 | 넣을 곳 |
|---|---|---|
| 애드온 (보스 진행) | magic-mythic-paper/src/.../PeperoBoss.java (+ 브랜치 MagicMythicPlugin·MythicCommands 의 `[기능:빼빼로데이보스]` 줄) | **라이브용 = 저쪽 hb2/n1111/m35/mythic35-{school,arena,wild}.jar** (= mythic34 + PeperoBoss + ReaperHook 연결, Fx.held → MordianFx.held, build.sh). 5대 plugins/update 에 넣고 재시작 |
| 미몹 몹 8종 | magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_pepero.yml | 학교 plugins/MythicMobs/mobs/ |
| 미몹 스킬 (모션 16 + 졸개 6) | magic-mythic-paper/mythicmobs/Skills/chacademi_boss_pepero.yml | 학교 plugins/MythicMobs/skills/ |
| 모델 6개 | magic-mythic-paper/models/pepero_day/*.bbmodel (giftbox 빠짐, heartlock 추가) | 학교 plugins/ModelEngine/blueprints/ → /meg reload → /iazip → generated.zip 5대 + 실행기 통합팩 |
| VFX 3팩 (44종) | portable-vfx-runtime/assets/PepChocola, PepSweet, PepGimmick | **실행기 배포본 준비됨 = 저쪽 hb2/n1111/launcher-pep** (make.ps1 → Merge → Verify bad=0, publish.sh 는 오너 "배포해" 뒤에만). 개별 File 항목 79개 + 클라 jar bossfx4. 서버 PortableVFX effects·spell-catalog·bindings 에는 넣지 않음 |
| 던전 등록 | server-config/pepero_day/dungeon-entry.yml | 학교 ChacaDungeon/dungeons.yml 의 dungeons: 아래 → /서버관리 새로고침 던전입장 |
| 의뢰 5개 | server-config/pepero_day/quests-pepero.yml | 학교 MagicCodexBridge/quests.yml → /퀘스트관리 파일반영 |
| NPC 코코 | server-config/pepero_day/npc/coco.yml + npc-quests-snippet.yml | 학교 ChacaNPC/npcs/ , ChacaNPC/quests.yml → 오너 배치 |
| 클라 이펙트 줄이기 예외 | server-config/pepero_day/client/BossOpacity.java | bossfx4 = 지금 배포된 bossfx2(md5 32d6cbd5) 에서 BossOpacity 클래스 하나만 다름. 설치 이름 mods/portable-vfx-preserved-245d836c.jar 그대로 (launcher-pep 에 포함) |

## 순서 (재시작 때)
1. 애드온 jar · 미몹 yml 2개 · blueprints 6개 넣고 재시작 (리로드 = 재시작 규칙). 시작 로그에서 PEP_ / CA_Pep_ / chapepero 오류·skipped 확인.
2. /meg reload → /iazip → generated.zip 5대 복사 + 실행기 통합팩.
3. 실행기: VFX 3팩 개별 파일 + PortableVFX 클라 bossfx4 jar (실행기 배포 점검표대로: 소문자 id·manifest·client not ready).
4. 던전 등록 붙이고 새로고침. **같은 칸·입구를 쓰는 sweet_nightmare 는 이벤트 동안 닫기** (portal 치우기).
5. 의뢰 붙이고 /퀘스트관리 파일반영 (DB catalog 에 5개 들어갔는지 확인), 코코 배치.
6. 시험: 경기장 중심에서 `/mm mobs spawn CA_Boss_pep_chocola` → `/테스트 보스 쇼콜라 혼자 켜기` → `기믹 1~7` · `패턴 <이름>` · `초기화`.

## 알아 둘 것
- 경기장 = 보스 소환 위치 중심 반경 20 (yml `arena=20`, 하베스트와 같은 맵). 초콜릿 판은 가운데 20×20, 마지막 한 입 초코는 20칸부터 10초마다 2칸씩 좁혀짐.
- 기믹 피해 대부분이 최대 체력 비율 (즉사 기믹: 배부름 중 나눠 먹기 · 우리 20초 · 마지막 한 입 60초). 혼자 시험 모드면 즉사 대신 채팅 알림.
- 막대·리본·초콜릿 판 선·초코 경계는 입자(DUST)로 그림 (VFX 아님).
- 관전 중(쓰러진) 사람은 클리어 의뢰 진행 안 됨. 일일 의뢰는 하루 3개 상한 때문에 안 만듦.
- 입장창 배경(art pepero_day)·코코 초상화 그림 없음 → 그림_프롬프트.md. 그림 없으면 리치 연구실 배경.

## 11/11 점검 (지난 사고 기록 대조)
- 교화 봉인(30초) 중 기믹·마지막 한 입·패턴 전부 멈춤 (10/31 하베스트 봉인 중 공격 사고).
- 패턴 쓰는 동안 보스 제자리 (11/11 아스칼론 예고 뒤 걸어 다님).
- 보스·졸개 공격은 방패로 못 막음 (11/04 3인 던전 방패 관통과 같게).
- 기믹 설명·성공/실패 타이틀 → 채팅 대사로 (11/03 오너 "기믹을 대놓고 알려주지 말 것", 보스 타이틀 정리). 기믹 이름·남은 시간 타이틀만 남김.
- 라이브 jar 에 ReaperBoss.Fx.held 가 없음 → 라이브판은 MordianFx.held (mythic33·34 와 같은 방식). 브랜치 jar 를 그대로 넣으면 기믹 3·5 에서 오류.
- 모델 hitbox 뼈대 상자 1개 + 눈높이 = 뼈대 기준점 (10/31 납작 히트박스·질식), model{hb=true}, chacademia_boss 태그, VFX 팩은 spell-catalog 에 넣지 않음 (client not ready 방지).

## 11/11 VFX·실행기 점검 (지난 실행기·VFX 사고 기록 대조)
- 클라 팩 상한: 팩당 시스템 ≤16 (15·13·16), 레이어 ≤64 (최대 19), 시스템 id 전부 소문자 → 실제 클라 로더(배포 중인 bossfx2 jar + load-limits 2048)로 0 오류.
  레포 전체 111팩 같이 읽어도 빼빼로 팩 오류 없음 (오류 7건은 레포 원본이 옛 판인 재의 그림자·모르디안 팩 — 배포본은 11/04·11/09 에 이미 나눠 고침).
- 배포된 팩과 이름 겹침 없음 (distribution 에 pep* 없음). 팩 수 684개 안팎 < max-packs 768. 텍스처 원본 RGBA 3.3+2.7+2.7 MiB (resident 2048 MiB 여유 충분).
- client-assets.zip 은 건드리지 않음 (manifest·4096개·1GiB 사고) → 개별 File 항목. 릴리스는 --latest=false (실행기 앱 업데이트 사고).
- 보스 팩은 서버 spell-catalog·bindings 에 넣지 않음 → 클라에 팩이 없어도 마법이 "client not ready" 로 막히지 않음 (11/03 교화 거절 사고).
- 쇼콜라 코드가 부르는 VFX id 40개 전부 3팩 정의 안에 있음. 예고 크기 = VFX 크기 (팩 배율 고정): 분신 웅덩이 r4·분신 폭발 r9 로 맞춤, 깨물기 VFX 는 조각 가운데서.
- 배포 직후: 서버 로그 `client not ready`·`received=` 확인, 재시작 뒤 5대 `skipped`·PortableVFX `0 skipped` 확인 (지금 학교 PortableVFX 는 보스 팩을 서버 쪽에 두지 않는 구조라 변화 없음).
- 유저 안내: 실행기를 다시 켜야 새 VFX·bossfx4 를 받음 (11/03 "실행기 안 켠 클라" 거절 사례).

## 11/11 다시 점검 (코드 독립 검토 15건 중 고친 것)
- 우리에 갇힌 사람이 다른 월드로 가면 싸움 전체가 멈추던 것 → 그때 기믹 실패로 끝냄. 점프로 끌려오던 것 → 수평 거리로.
- 분신 체력 표시(5000)로 진짜가 바로 보이던 것 → 진짜와 같은 최대 체력·현재 체력 계속 맞춤.
- 마지막 한 입에서 나눠 먹기 두 번 연속 = 배부름 안 풀려 무조건 사망 → 연속 안 나옴.
- 빼빼로 게임 시작 막대가 무조건 20칸이라 붙어 있거나 멀면 시작하자마자 실패 → 처음 거리 + 2칸 (8~20), 준비 2초. 끝 판정 2.5칸이 실제로는 2칸이던 것 고침.
- 초콜릿 판 밖(경기장 가장자리)에 서면 기믹이 무의미 → 판 밖 초당 8% + 둔화.
- 같은 틱 두 번째 판정이 무적 시간에 먹히던 것 (선물 2개·겹친 원) → 판정마다 무적 시간 0.
- 가벼운 패턴 뒤 보스 AI 가 기믹 끝까지 꺼져 있던 것 → 패턴 끝 틱 기준으로 다시 켬.
- 봉인 중에도 예약된 패턴·예고가 터지던 것 → 예약 실행·예고 판정 모두 봉인 중엔 안 함.
- 분신 기믹이 1초 안에 끝나도 분신이 뒤늦게 생기던 것, 혼자 시험 모드가 서버 전체에 걸리던 것(→ 그 보스만), 소품 목록 안 비우던 것, 구하러 갈 사람이 한 명뿐이면 자물쇠 2초 동시 파괴 불가(→ 10초).
- 아몬드 굴림이 박치기 모션 없던 것 추가. 경기장 반경 16 → 20 (맵 = 하베스트와 같은 20). 의뢰 반영 명령 = /퀘스트관리 파일반영.
