# 빼빼로데이 3인 던전 「초코 막대 공방」 적용 안내 (2026-11-11, 11/11 아침 개편 — 서버·실행기 아직 미적용)

기획: Desktop\클로드\빼빼로데이던전\기획.md · 게임 시험 안 함.
11/11 개편: 보스 진행(짤패턴 6·기믹 7)을 yml 에서 **애드온 chapepero (PeperoBoss.java)** 로 옮김. 체력 12000, 그로기 없음.

## 파일
| 무엇 | 레포 위치 | 넣을 곳 |
|---|---|---|
| 애드온 (보스 진행) | magic-mythic-paper/src/.../PeperoBoss.java + MagicMythicPlugin·MythicCommands 의 `[기능:빼빼로데이보스]` 줄 | 학교 ChAcademiaMagicMythic jar (표시 주석 부분만 라이브 소스에 넣어 빌드). 참고 빌드 = Desktop\클로드\빼빼로데이던전\chacademia-magic-mythic-pepero-20261111.jar (이 브랜치 바탕이라 그대로 쓰지 말 것) |
| 미몹 몹 8종 | magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_pepero.yml | 학교 plugins/MythicMobs/mobs/ |
| 미몹 스킬 (모션 16 + 졸개 6) | magic-mythic-paper/mythicmobs/Skills/chacademi_boss_pepero.yml | 학교 plugins/MythicMobs/skills/ |
| 모델 6개 | magic-mythic-paper/models/pepero_day/*.bbmodel (giftbox 빠짐, heartlock 추가) | 학교 plugins/ModelEngine/blueprints/ → /meg reload → /iazip → generated.zip 5대 + 실행기 통합팩 |
| VFX 3팩 (44종) | portable-vfx-runtime/assets/PepChocola, PepSweet, PepGimmick | 실행기 개별 파일 (client-assets.zip 에 넣지 말 것) + 서버 PortableVFX 쪽 같은 방식 |
| 던전 등록 | server-config/pepero_day/dungeon-entry.yml | 학교 ChacaDungeon/dungeons.yml 의 dungeons: 아래 → /서버관리 새로고침 던전입장 |
| 의뢰 5개 | server-config/pepero_day/quests-pepero.yml | 학교 MagicCodexBridge/quests.yml → /의뢰관리 reload |
| NPC 코코 | server-config/pepero_day/npc/coco.yml + npc-quests-snippet.yml | 학교 ChacaNPC/npcs/ , ChacaNPC/quests.yml → 오너 배치 |
| 클라 이펙트 줄이기 예외 | server-config/pepero_day/client/BossOpacity.java | 실행기 PortableVFX 클라 jar → bossfx4 (Desktop\클로드\빼빼로데이던전\client) |

## 순서 (재시작 때)
1. 애드온 jar · 미몹 yml 2개 · blueprints 6개 넣고 재시작 (리로드 = 재시작 규칙). 시작 로그에서 PEP_ / CA_Pep_ / chapepero 오류·skipped 확인.
2. /meg reload → /iazip → generated.zip 5대 복사 + 실행기 통합팩.
3. 실행기: VFX 3팩 개별 파일 + PortableVFX 클라 bossfx4 jar (실행기 배포 점검표대로: 소문자 id·manifest·client not ready).
4. 던전 등록 붙이고 새로고침. **같은 칸·입구를 쓰는 sweet_nightmare 는 이벤트 동안 닫기** (portal 치우기).
5. 의뢰 붙이고 /의뢰관리 reload, 코코 배치.
6. 시험: 경기장 중심에서 `/mm mobs spawn CA_Boss_pep_chocola` → `/테스트 보스 쇼콜라 혼자 켜기` → `기믹 1~7` · `패턴 <이름>` · `초기화`.

## 알아 둘 것
- 경기장 = 보스 소환 위치 중심 반경 16 (yml `arena=16`). 초콜릿 판은 가운데 20×20, 마지막 한 입 초코는 16칸부터 좁혀짐.
- 기믹 피해 대부분이 최대 체력 비율 (즉사 기믹: 배부름 중 나눠 먹기 · 우리 20초 · 마지막 한 입 60초). 혼자 시험 모드면 즉사 대신 채팅 알림.
- 막대·리본·초콜릿 판 선·초코 경계는 입자(DUST)로 그림 (VFX 아님).
- 관전 중(쓰러진) 사람은 클리어 의뢰 진행 안 됨. 일일 의뢰는 하루 3개 상한 때문에 안 만듦.
- 입장창 배경(art pepero_day)·코코 초상화 그림 없음 → 그림_프롬프트.md. 그림 없으면 리치 연구실 배경.
