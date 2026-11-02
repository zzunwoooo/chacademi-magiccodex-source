# 1인 던전 보스 "재의 그림자" + 잡몹 3종 — 미몹 + 애드온 + 모델 + VFX (Claude 2026-11-03, **빌드 안 함**)

브랜치 `claude/ash-shadow-boss-20261103` (기준 `claude/pumpkin-reaper-boss-20261031` @ 263ede4). 이 환경에는 Paper·MythicMobs 의존성이 없어
**컴파일을 못 했다** (구문 검사만 함) — 호스팅에서 `Build-Server.ps1 -Verify` 로 먼저 빌드·테스트할 것.

## 0. 바뀐 파일

| 파일 | 내용 |
|---|---|
| `magic-mythic-paper/src/main/java/.../AshShadowBoss.java` (새) | 미몹 메커닉 `chaash` (보스 진행자), 이벤트 `AshShadowBoss.Hooks` (무적·50% 멈춤·장치 보호·근접 배율·처치) |
| `MagicMythicPlugin.java` | `chaash` 등록, Hooks 등록, 끌 때 `AshShadowBoss.closeAll()` |
| `MythicCommands.java` | `/테스트 보스 재의그림자 <패턴 id \| 장치 0~3 \| 탄막 \| 그로기 \| 변신 \| 초기화>` |
| `magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_ash_shadow.yml` (새) | 보스 `CA_Boss_ash_shadow`, 장치 `CA_Ash_light_device`, 잡몹 `CA_Ash_hound / wax_brute / lantern_stag` (+`_shiny`) |
| `magic-mythic-paper/mythicmobs/Skills/chacademi_boss_ash_shadow.yml` (새) | 보스 모션 `CHA_ASH_a_*`, 모델 교체 `CHA_ASH_swap`, 잡몹 스킬 `CHA_MOB_ash_*` (+`_shiny`) |
| `magic-mythic-paper/models/ash_shadow/*.bbmodel` (새) | 보스 `ash_shadow`(애니 21) · `ash_shadow_enraged`(애니 14) + 이로치. ModelEngine blueprints 로 (파일 이름 = 모델 id) |
| `magic-mythic-paper/models/ash_shadow/minions/*.bbmodel` (새) | `ash_hound`, `wax_brute`, `lantern_stag` + `_shiny` (애니 spawn·idle·walk·attack·death, VFX 기준점 빈 본 fx_*) |
| `magic-mythic-paper/models/ash_shadow/*_viewer.html`, `source/` | 모델 3D 뷰어(모션 전부 재생) + 생성 원본 (gen.py·models3.py·mobs.py·render.py) |
| `portable-vfx-runtime/assets/AS_*/`, `AM_*/` (새) | **VFX 완성 팩** (vfx.json + textures + NOTES.md). 예고 없음 |
| `portable-vfx-runtime/tools/ash_shadow_vfx/` (새) | VFX 원본 + `CONTRACT.md` (팩·시스템 id·크기·길이 = 서버 연동 표) |

## 1. 동작 요약 (보스)

- 소환 위치 = 경기장 중심, 반경 16. 정면 기준 90°·210°·330°, 반경 9 에 **빛 발산 장치** 3개 (`CA_Ash_light_device`, 던전 기믹 모델 `gm_light_receiver` 재사용). 장치→보스 회복 빛줄기 VFX.
- 입장 대사 (채팅+타이틀): "빛이 있는 한, 그림자는 사라지지 않는다".
- **1페이즈 100~50%**: 장치 수에 따라 보스 초당 회복 3개 4% / 2개 1.5% / 1개 0.5%, 공격력 ×2.0 / 1.7 / 1.4 / 1.0.
  장치는 직접 때려서 못 부숨 — **유도 패턴의 판정 안에 장치가 있으면 부서짐** (플레이어가 장치 옆에서 피하기). 부서진 장치는 40초마다 하나씩 다시 켜짐.
  장치가 0개가 된 직후 다음 패턴은 반드시 **발악**.
- **50%**: 체력이 50% 에서 멈춤 → 중앙 귀환 → **그림자 탄막 25초** (무적, 회복 없음). 높이 1칸 고정 탄, 기하학 6구간: 5갈래 회전 별 → 동심 고리 → 2중 나선 → 십자 휩쓸기 → 가장자리 육각형이 좁혀옴(한 변 비움) → 마무리 고리 3번. 맞으면 최대 체력 55% (두 번 = 죽음), 피격 후 0.5초 무적. 탄막 중 전멸 = 처음부터.
- 버티면 **그로기 10초** (맞음, 공격·이동 없음) → **변신** (무적): 남은 장치를 하나씩 흡수 → 모델 교체 → **광폭화**.
- **광폭화**: 장치 재소환 없음, 예고·간격 25% 짧음, 이동 1.5배, 공격력 흡수 0 ×1.5 / 1 ×1.7 / 2 ×1.9 / 3 ×2.2. 흡수 3개면 내려찍기·재의 비 1타 추가.
- 대상 규칙: 가까운 적(기본 공격·손 내려찍기), 가장 먼 적(돌진·잠행·손아귀), 체력 비율 가장 낮은 적(재의 비), 전원(장막).

| 패턴 | 종류 | 예고 (기본 / 광폭화는 ×0.75) | 피해 (× 배율) | 장치 파괴 |
|---|---|---|---|---|
| claw 앞팔 할퀴기 (기본 공격) | 부채꼴 r 6 · 150° | 0.4초 | 22 | × |
| dash 잿빛 돌진 ×2 | 직선 18 × 4 | 0.6초, 끝까지 미끄러짐 | 28 | ○ |
| slam 그림자 손 내려찍기 ×3(광폭 4~5) | 직선 14 × 4 | 0.45초씩 | 24 | ○ |
| rain 추적하는 재 ×4(5) | 원 r 4 (그때그때 대상 위치) | 0.5초씩 | 18 + 불 | ○ |
| sink 잠행 → 솟구침 | 원 r 5 → 원 r 8 | 1.1초 / 0.4초 | 26 / 18 | ○ |
| grasp 그림자 손아귀 | 원 r 6 | 0.5초 | 12 + 속박 1초 + 끌어당김 | × |
| afterimage 잔상 | 0.5초 뒤 직전 패턴 한 번 더 | — | — | (다시 쓴 패턴 따름) |
| veil 재의 장막 | 실명 1.5초 + 각자 발밑 원 r 7 ×2 | 0.7초 | 20 | × |
| ring 화장의 고리 | 원 r 5 → 팔각 고리 r 5~12 (직선 8개) → 원 r 5 | 0.6초씩 | 22 | × |
| ward 빛의 수호 | 장치 하나 2초 보호막 | — | — | 막음 |
| desperate 발악 | 8방향 직선 10 × 3, 22.5° 돌려 한 번 더 | 0.5초 | 28 | × |
| 근접 (미몹 meleeattack) | 바닐라 | — | Damage 22 | × |

체력 설계: 지능(마력) 140 기준 마법 한 방 = base + 비율 × 140 → 중앙값 약 60, 궁극기 약 300. 그로기 10초 극딜 ≈ 1200 = 최대 체력의 30% → **Health 4000** (50% → 20%, 20% 아래는 처형 마법).
실제 딜이 다르면 Mobs yml 의 Health 하나만 바꾸면 된다.

## 2. 잡몹

| 몹 | 역할 | 체력 / 근접 | 스킬 |
|---|---|---|---|
| `CA_Ash_hound` 재 사냥개 | 빠른 근접 (이동 0.36) | 300 / 12 | 물어뜯기: 몸 앞 부채꼴 r 3.5 · 100°, 체술(예고 없음), 0.38초 판정, 16 |
| `CA_Ash_wax_brute` 촛농 짐승 | 탱커 근접 (0.22) | 750 / 16 | 내려찍기: 자기 중심 원 r 4.5, 예고 0.75초, 26 + 밀침 + 둔화 2초 |
| `CA_Ash_lantern_stag` 등불 사슴 | 원거리 (근접 AI 없음) | 350 / 0 | 등불 잿불탄: 대상 발밑 원 r 2.5 예고 1.5초, 20 + 화상. 0.75초에 투사체(보여주기) |

이로치(`_shiny`) = 같은 스킬, 모델·VFX 시스템만 `_shiny`.

## 3. 배포 (GPT)

1. MagicMythic jar 빌드 (보스 던전이 있는 서버).
2. `mythicmobs/Mobs/chacademi_boss_ash_shadow.yml` → `plugins/MythicMobs/Mobs/`, `Skills/chacademi_boss_ash_shadow.yml` → `plugins/MythicMobs/Skills/`, `/mm reload`.
3. ModelEngine blueprints: `models/ash_shadow/ash_shadow.bbmodel`, `ash_shadow_enraged.bbmodel` (+ `_shiny` 둘은 지금 미몹에서 안 씀), `models/ash_shadow/minions/*.bbmodel` 6개. 장치는 기존 `gm_light_receiver` (기믹 플러그인 모델) 이 이미 ModelEngine 에 있어야 함.
4. PortableVFX 팩 = `portable-vfx-runtime/assets/AS_*`, `AM_*` → 클라이언트 PortableVFX `claude` 이름공간. 코드는 `claude:<팩 소문자>/<시스템 소문자>` 로 부름 (`tools/ash_shadow_vfx/CONTRACT.md`).
5. 보스 소환: 경기장 한가운데에서 바라볼 정면을 정하고 `/mm mobs spawn CA_Boss_ash_shadow`.

## 4. 확인 못 한 것 / 주의

- **컴파일·실행 확인 없음.** API 는 ReaperBoss.java(하베스트) 와 같은 것만 썼다 (Fx.effect/follow/link/stop, GameplayVisual.cast/move/close, Telegraphs.start, StatusEffects.apply, ReactionVfx.areaEffect).
- 모델 교체 `CHA_ASH_swap` = `model{mid=ash_shadow;r=true}` + `model{mid=ash_shadow_enraged;...}`. ModelEngine 버전에 따라 지우기 옵션 이름이 다르면 맞출 것. 실패하면 기본형 모델 그대로 광폭화가 진행됨(동작은 됨).
- 상태 끄기 `state{s=...;r=true}` 는 하베스트와 같은 가정.
- 탄막 탄은 PortableVFX projectile 을 탄마다 하나 띄우고 2틱마다 위치 갱신 (동시에 약 20~40개). 1인 던전이라 괜찮다고 봤지만 서버·클라 부담을 실제로 볼 것. 판정은 서버 계산(반경 0.8, 높이 ±1.3).
- 탄막 피해는 `Targets.hurt` 로 최대 체력 55%. 방어·보호 효과가 피해를 줄이면 "두 번 = 죽음" 이 안 될 수 있음 → 그땐 비율만 올리기.
- 잠행 중 보스는 setInvisible + 모션(땅속 hold). ModelEngine 모델이 실제로 숨는지 확인 (모션이 아래로 내려가서 보이지 않게 만들어 둠).
- 경기장 밖(반경 22 바깥)은 대상 아님. 60초 아무도 없으면 처음 상태 (광폭화 뒤면 보스를 새로 소환).

## 5. 시험 명령

```
/테스트 보스 재의그림자 패턴 claw|dash|slam|rain|sink|grasp|afterimage|veil|ring|ward|desperate
/테스트 보스 재의그림자 장치 0~3
/테스트 보스 재의그림자 탄막 | 그로기 | 변신 | 초기화
```
