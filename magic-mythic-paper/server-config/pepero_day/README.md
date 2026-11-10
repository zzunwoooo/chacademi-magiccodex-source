# 빼빼로데이 3인 던전 「초코 막대 공방」 적용 안내 (2026-11-11 작성, 서버·실행기 아직 미적용)

기획: Desktop\클로드\빼빼로데이던전\기획.md · 게임 시험 안 함.

## 파일
| 무엇 | 레포 위치 | 넣을 곳 |
|---|---|---|
| 미몹 몹 6종 | magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_pepero.yml | 학교 plugins/MythicMobs/mobs/ |
| 미몹 스킬 48개 | magic-mythic-paper/mythicmobs/Skills/chacademi_boss_pepero.yml | 학교 plugins/MythicMobs/skills/ |
| 모델 6개 | magic-mythic-paper/models/pepero_day/*.bbmodel | 학교 plugins/ModelEngine/blueprints/ → /meg reload → /iazip → generated.zip 5대 + 실행기 통합팩 |
| VFX 2팩 (28종) | portable-vfx-runtime/assets/PepChocola, PepSweet | 실행기 개별 파일 (client-assets.zip 에 넣지 말 것) + 서버 PortableVFX 쪽 같은 방식 |
| 던전 등록 | server-config/pepero_day/dungeon-entry.yml | 학교 ChacaDungeon/dungeons.yml 의 dungeons: 아래 → /서버관리 새로고침 던전입장 |
| 의뢰 5개 | server-config/pepero_day/quests-pepero.yml | 학교 MagicCodexBridge/quests.yml → /의뢰관리 reload |
| NPC 코코 | server-config/pepero_day/npc/coco.yml + npc-quests-snippet.yml | 학교 ChacaNPC/npcs/ , ChacaNPC/quests.yml → 오너 배치 |
| 클라 이펙트 줄이기 예외 | server-config/pepero_day/client/BossOpacity.java | 실행기 PortableVFX 클라 jar → bossfx3 (Desktop\클로드\빼빼로데이던전\client) |

## 순서 (재시작 때)
1. 미몹 yml 2개 + blueprints 6개 넣고 재시작 (리로드 = 재시작 규칙). 시작 로그에서 PEP_ / CA_Pep_ 오류·skipped 확인.
2. /meg reload → /iazip → generated.zip 5대 복사 + 실행기 통합팩.
3. 실행기: VFX 2팩 개별 파일 + PortableVFX 클라 bossfx3 jar (실행기 배포 점검표대로: 소문자 id·manifest·client not ready).
4. 던전 등록 붙이고 새로고침. **같은 칸·입구를 쓰는 sweet_nightmare 는 이벤트 동안 닫기** (portal 치우기).
5. 의뢰 붙이고 /의뢰관리 reload, 코코 배치.
6. 시험: /테스트 등으로 보스 혼자 소환 → 70% 빼빼로 게임 · 60% 2페이즈 · 40% 상자 · 15% 광폭 · 사망 정리.

## 알아 둘 것
- 기믹·페이즈 모두 yml (애드온 코드 없음). 체력 % 조건이라 이지 체력 배율과 무관.
- VFX 크기는 만든 크기 고정 → chatelegraph vfxref 는 예고 크기와 같게 둘 것 (지금 다 같음).
- 껍질이 일찍 깨지면 이미 튼 껍질 VFX 가 최대 3초 남음 (위에 깨짐 연출이 덮음).
- 관전 중(쓰러진) 사람은 클리어 의뢰 진행 안 됨 (브리지가 관전자 진행 무시). 일일 의뢰는 하루 3개 상한 때문에 안 만듦.
- 입장창 배경(art pepero_day)·코코 초상화 그림 없음 → 그림_프롬프트.md. 그림 없으면 리치 연구실 배경.
