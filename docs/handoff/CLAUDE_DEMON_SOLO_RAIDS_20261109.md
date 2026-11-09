# 악마 1인 레이드 — 룰레타 · 솜니아 (Claude 2026-11-09)

브랜치 `claude/demon-solo-raids-20261109` (claude/elysia-boss-20261109 위). 빌드·실행 확인 없음 (여기서 서버 실행 불가).
기획서: Claude Docs "차카데미 악마 1인 레이드 5종 기획서" 3번(룰레타)·4번(솜니아).

## 이번에 들어간 것

| 위치 | 내용 |
|---|---|
| `magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_roulette.yml` | 보스 `CA_Boss_roulette`(+`_reset`), 소품 주사위·칩 탑·동전, 잡몹 칩 임프·카드 병정·주사위 술사 (+이로치) |
| `magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_somnia.yml` | 보스 `CA_Boss_somnia`(현실, 반투명 모델) + `CA_Boss_somnia_dream`(꿈, 실체) + `_reset`, 소품 종·베개, 잡몹 졸음 나방·몽유 거인·악몽 인형 (+이로치) |
| `magic-mythic-paper/mythicmobs/Skills/chacademi_boss_roulette.yml` | 보스 모션 `CHA_RL_a_*`, 바늘 `CHA_RL_needle_*`, 소품 `CHA_RL_p_*`, 잡몹 `CHA_MOB_rl_*` |
| `magic-mythic-paper/mythicmobs/Skills/chacademi_boss_somnia.yml` | 보스 모션 `CHA_SN_a_*`, 모델 교체 `CHA_SN_veil`/`CHA_SN_solid`, 소품 `CHA_SN_p_*`, 잡몹 `CHA_MOB_sn_*` |
| `magic-mythic-paper/models/roulette/`, `models/somnia/` | ModelEngine bbmodel 23개 (보스·소품·잡몹, 이로치, somnia_veil) |
| `portable-vfx-runtime/assets/Roulette_Boss`, `Roulette_Mobs`, `Somnia_Boss`, `Somnia_Mobs` | 클라이언트 PortableVFX 팩 4개 (client_compat check OK, 장판 예고 없음). 시스템별 위치·타이밍은 각 팩 NOTES.md |
| `tools/demon_raid_models/`, `tools/demon_raid_vfx/` | 모델·VFX 생성 스크립트 (다시 만들 때) |

## 애드온 (magic-mythic-paper, 빌드 확인 안 됨 — 의존성 없는 javac 문법 검사만 통과)
| 파일 | 내용 |
|---|---|
| `DemonFight.java` | 두 보스 공용 바탕: 예고→같은 모양 판정(strike), 모션·소품 소환, 채팅+타이틀, 좌표 도우미 |
| `RouletteBoss.java` | 메커닉 `charoulette{a=start;arena=18;table=4}`. 짤패턴 7종(cards chips dice dealer coin allin recall), 35초(광폭 25초)마다 룰렛: 6구역 베팅 → 결과 구역 서면 보스 최대 체력 6% + 그로기 4초, 틀리면 최대 체력 45%(테이블 30%). 속임수 30%(광폭 50%). 50% 하우스 룰 3판, 30% 광폭화(속도·피해 ×1.25). 카드 병정 정면 피해 50% 감소 훅 |
| `SomniaBoss.java` | 메커닉 `chasomnia{a=start;arena=16;dx=0;dy=0;dz=120}`. 현실 몸 받는 피해 20%, 꿈 몸(CA_Boss_somnia_dream) 맞으면 현실 몸 체력 깎임. 잠의 웅덩이 2개(20초마다 이동) → 꿈, 꿈 안 졸음 15초에 가득 = 즉사, 빛나는 종(때리기·우클릭) → 현실·졸음 0. 현실 패턴 4 + 꿈 패턴 4. 50% 깨지 않는 꿈(25초 바닥 무너짐 → 가운데 종 → 그로기 10초), 30% 경계 붕괴(회전하는 경계선, 꿈 쪽 졸음 차고 보스는 꿈 쪽일 때만 피해 100%) |
| `MagicMythicPlugin.java` | 메커닉 2개 등록, Hooks 2개 등록, onDisable closeAll |
| `MythicCommands.java` | `/테스트 보스 룰레타 패턴 <id>·룰렛 [0~5]·속임수·하우스룰·광폭화·초기화`, `/테스트 보스 솜니아 패턴 <id>·꿈·현실·졸음 <0~100>·깨지않는꿈·광폭화·초기화` |

## 맵 조건
- 룰레타: 소환 위치 = 경기장 중심(반경 18 평지), 소환 때 바라보는 방향이 0번 구역. 가운데 반경 4 = 테이블(안전 아님, 실패 30%).
- 솜니아: 소환 위치 = 현실 경기장 중심(반경 16). 꿈 맵 = 소환 위치 + (0,0,120) 에 같은 크기 평지. 꿈 가장자리 4방향 종은 애드온이 놓음.

## 확인할 것 (빌드 후)
- 경계 붕괴 VFX `boundary` 회전 방향과 판정(boundaryYaw)이 반대로 보이면 SomniaBoss.boundaryYaw 부호만 바꾸면 됨.
- 룰렛 결과 구역 VFX `lose` 의 안전 구역 방향이 판정 구역과 맞는지 (yaw = 중심 yaw + 60 × 결과).
- MythicMobs `castSkill` 로 부르는 모션 스킬 이름은 Skills yml 과 대조 완료.

## 배포 (GPT)
1. `Mobs/`, `Skills/` 의 chacademi_boss_roulette.yml, chacademi_boss_somnia.yml → `plugins/MythicMobs/Mobs·Skills/`, `/mm reload`.
2. `models/roulette/**`, `models/somnia/**` bbmodel → ModelEngine blueprints (모델 ID = 파일 이름), `/meg reload`.
3. `portable-vfx-runtime/assets/` 의 4팩 → 클라이언트 PortableVFX `claude` 이름공간 (`claude:roulette_boss/spin` 처럼 팩 소문자/시스템).
4. MagicMythic 플러그인 jar 다시 빌드·교체 (RouletteBoss / SomniaBoss / DemonFight 추가).
5. 잡몹 확인: `/mm mobs spawn CA_Roulette_card_soldier` 등으로 공격·예고·VFX 확인.
6. 보스 확인: `/mm mobs spawn CA_Boss_roulette` / `CA_Boss_somnia` 후 위 `/테스트 보스 …` 명령으로 패턴·기믹 하나씩.
