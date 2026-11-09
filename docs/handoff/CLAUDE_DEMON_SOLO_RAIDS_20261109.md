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

## 지금 바로 동작하는 것
- 잡몹 6종(+이로치): 공격 스킬이 yml 안에서 완결 (chatelegraph 예고 + VFX + 모션, 주사위 술사·악몽 인형은 chamobshot).
- 보스·소품 모션 스킬 (애드온이 부를 이름).

## 아직 없는 것 (다음 작업)
- 애드온 메커닉 `charoulette{a=start;arena=18;table=4}` / `chasomnia{a=start;arena=16}` — RouletteBoss.java / SomniaBoss.java.
  지금 yml 은 이 이름을 부르므로, 애드온 없이 넣으면 MythicMobs 가 "알 수 없는 메커닉" 경고를 내고 보스는 근접만 한다.
  패턴 → 모션 스킬 → VFX 대응표는 두 Skills 파일 머리말에 있다.
- 카드 병정 "정면 피해 50% 감소"는 방향 판정이 필요해 애드온 쪽.
- 솜니아 악몽 분신 몸(모델)·꿈 맵 좌표는 애드온에서 정한다.

## 배포 (GPT)
1. `Mobs/`, `Skills/` 의 chacademi_boss_roulette.yml, chacademi_boss_somnia.yml → `plugins/MythicMobs/Mobs·Skills/`, `/mm reload`.
2. `models/roulette/**`, `models/somnia/**` bbmodel → ModelEngine blueprints (모델 ID = 파일 이름), `/meg reload`.
3. `portable-vfx-runtime/assets/` 의 4팩 → 클라이언트 PortableVFX `claude` 이름공간 (`claude:roulette_boss/spin` 처럼 팩 소문자/시스템).
4. 잡몹 확인: `/mm mobs spawn CA_Roulette_card_soldier` 등으로 공격·예고·VFX 확인.
