# 호박 리퍼(pumpkin_reaper) 보스 VFX 묶음 — 변환·서버 연동 메모

모든 팩은 PortableVFX 클라이언트 호환 범위 안에서만 만들었다 (client_compat check OK). 빠진 기능·대체한 기능 없음.
좌표: static 의 +Z = 서버가 넘겨주는 시전자(보스) 수평 정면, +X = 보스 오른쪽. 경기장 반경 기준 20블록, 보스 소환 위치 = 경기장 중심.
**빨간 예고(원형·직선·부채꼴)는 VFX 에 없다 — 기존 모드 예고(chatelegraph)를 쓴다.** 아래 '모드 예고' 칸의 모양·반경·시간으로 그리고, 판정 순간에 VFX 를 켠다.
VFX 에 남긴 빨간 표시는 모드 예고로 그리기 어려운 것뿐: 나눠 맞기 아이콘(PR_CandyRain shareMark), 사슬 5초 카운트다운(PR_SoulChain bind), 등불 빛 경계 위험선(PR_LanternLight). 모델 애니메이션은 `pumpkin_reaper.bbmodel` 이름 기준.

| 팩 | 모드 예고 | 시스템 (role) | 배치·타이밍 |
|---|---|---|---|
| PR_HarvestSlash (1 수확 베기) | 부채꼴 240° 반경 7, 0.45초 | slash(static, 오른→왼) · slashBack(static, 왼→오른) | 보스 발밑·정면. 예고 시작 0.15초 뒤 `attack`(또는 `p01_harvest_double`) → 0.45초에 slash. 2연타면 0.65초 뒤 slashBack(두 번째 예고도 모드) |
| PR_CrescentSpin (2 반달 회전베기) | 원 반경 8, 0.55초 | spin | 예고와 동시에 `p02_crescent_spin`, 0.55초에 spin |
| PR_BoomerangScythe (3 부메랑 낫) | 직선 18×2, 0.55초 | launch(static) · scythe(projectile, 서버가 왕복 이동) · catch(impact, 손에 돌아올 때) | 가장 먼 적 방향을 정면으로. `p03_boomerang_throw` 의 0.55초(낫 숨김)에 launch+scythe, 1.25초(낫 다시 보임)에 scythe 정지 + catch |
| PR_PumpkinBomb (4 호박 폭탄) | 대상 발밑 원 반경 2.2, 1.5초 | throw(static 보스) · bomb(projectile 포물선) · boom(impact) | 세 명 각각 예고 → `p04_pumpkin_bomb` 던지는 순간(0.27/0.67/1.07초) throw+bomb → 예고 끝(1.5초)에 착탄 boom |
| PR_SoulChain (5 영혼 사슬) | 없음 (bind 카운트다운이 VFX 표시) | chain(link: A 가슴 → B 가슴) · bind(attached, 두 사람 모두, 5초 빨간 카운트다운) · snap(impact, 끊긴 지점) · fail(impact, 두 사람 발밑) | 대상 = 가장 먼 적 + 가장 체력 낮은 적. 끊기면 chain/bind 정지 + snap, 5초 지나면 fail |
| PR_GroundHands (6 땅속의 손) | 원 반경 1.4, 0.6초 ×5 | slam(static 보스) · hand(static) | `p06_ground_hands` 0.55초에 slam. 가장 체력 낮은 적 위치에 예고를 0.35초 간격 5번, 각 예고 끝에 같은 자리 hand |
| PR_GhostDash (7 유령 돌진) | 직선 14×2.6, 0.6초 | vanish · appear · dash(attached 보스) · curse(static 장판 10초) | `p07_ghost_dash`: 0.45초 vanish(옛 위치) → 순간이동 → appear + 모드 예고 → 0.6초 뒤 돌진 시작에 dash, 지나간 길 2블록마다 curse |
| PR_TrickOrTreat (8 장난칠까 사탕 줄까) | 사탕 떨어질 자리 원 반경 0.9, 0.5초 | scatter(static 보스) · land · treat(attached 회복) · trick(attached 저주) | `p08_trick_or_treat` 1.1초에 scatter, 사탕 모델 떨어질 자리에 모드 예고 → land. 먹으면 treat 또는 trick(썩은 호박 시야 가림 동안) |
| PR_CandyRain (9 사탕 비) | 메테오 원 반경 1.3, 0.8초 · 막대사탕 원 반경 3, 2.0초 | aura(attached 보스 공중 루프) · meteor(projectile) · meteorHit(impact + 끈적 장판 3초) · shareMark(나눠 맞기 아이콘, 막대사탕 예고와 같은 자리·같은 2초) · lollipopTrail(projectile, 거대 막대사탕 모델에 붙임) · lollipopHit(impact) | `p09_candy_rain_start` 에 aura 시작, `_end` 에 정지. 나눠 맞기 판정은 서버(원 안 인원 수) |
| PR_ReaperLeap (10 수확자의 도약) | 착지점 원 반경 5, 1.15초 | launch(static 보스) · slam(static 착지점) | 예고와 동시에 `p10_reaper_leap`, 0.5초에 launch, 1.15초에 slam |
| PR_ReturnGuard (귀환·무적) | 없음 | vanish · appear(소환 위치) · guard(attached 루프 보호막) · deflect(impact, 무적 중 피격 지점) | `return_vanish` 시작에 vanish, 끝(0.7초)에 소환 위치로 이동 + appear + `return_appear`. 패턴 동안 guard, 끝나면 정지 |
| PR_Harvested (즉사 공통) | 없음 | harvest(impact) | 기믹 실패·빛 밖 등으로 죽은 플레이어 발밑 |
| PR_LyingPumpkins (기믹1) | 경기장 원 반경 20, 15초 | plant · rise(호박마다) · truth · lie(호박마다) · wipe | `g1_scythe_plant` 0.75초에 plant, 호박 모델 솟을 때 rise, 동시에 모드 예고. 정답 → 예고 취소 + plant·rise 정지 + truth. 오답/시간초과 → 호박마다 lie, 0.3초 뒤 wipe + 전원 PR_Harvested |
| PR_ThreeBells (기믹2) | 실패 시 경기장 원 반경 20, 0.9초 | split · toll(종 1번) · candleViolet/Orange/Teal(attached 분신) · hitViolet/Orange/Teal(impact) · merge · sweep | `g2_split` 에 split, 분신마다 촛불색 candle. 종소리마다 toll(3번). 분신 맞으면 해당 색 hit. 실패 → merge + 모드 예고 → 0.9초 뒤 sweep(`g2_merge_sweep`) |
| PR_LastLantern (기믹3) | 끝에 경기장 원 반경 20, 0.9초 | darkFall · lanternDrop · carry(attached 등불 든 사람) · deposit · lightOut · sweep | `g3_lantern_drop` 1.35초에 darkFall + lanternDrop. 든 사람에게 carry, 사탕 넣으면 deposit. 30초 끝 모드 예고 → 0.9초 뒤 sweep(`g3_arena_sweep` 1.3초) |
| PR_LanternLight (기믹3 안전지대) | 없음 (빛 경계 위험선이 VFX 표시) | light(attached 루프) | 등불 든 사람 발밑. **runtime areaRadius = 현재 빛 반경**(기준 4블록) — 사탕 넣으면 키우고 시간 지나면 줄임. 경계 바로 바깥 빨간 선 = 위험 |

사탕·등불·잭오랜턴·표식은 Blockbench 모델이고 VFX 는 그 주변 연출만 담당한다 (사탕 투사체 meteor 의 사탕 그림은 작은 빌보드).
