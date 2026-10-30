# 할로윈 3인 보스 "하베스트" (호박 리퍼) — 미몹 + 애드온 진행자 (Claude 2026-10-31, **빌드 안 함**)

브랜치 `claude/pumpkin-reaper-boss-20261031` (기준 `claude/magic-150-20261031` @ 5c31d3c). 이 환경에는 MythicMobs·Paper 의존성이 없어
**컴파일을 못 했다** — 호스팅에서 `Build-Server.ps1 -Verify` 로 먼저 빌드·테스트할 것.

## 0. 바뀐 파일

| 파일 | 내용 |
|---|---|
| `magic-mythic-paper/src/main/java/.../ReaperBoss.java` (새) | 미몹 메커닉 `chaboss` (보스 진행자), `chavfx` (PortableVFX 한 번 재생 / 따라가기), 이벤트 `ReaperBoss.Hooks` |
| `MagicMythicPlugin.java` | `chaboss`·`chavfx` 등록, Hooks 등록, 끌 때 `ReaperBoss.closeAll()` |
| `MobShotMech.java` | `chamobshot` 에 `back=true` (부메랑: 사거리 끝에서 돌아옴, 관통, 구간마다 한 번씩 판정, 잡을 때 hitvfx) |
| `MythicCommands.java` | `/테스트 보스 하베스트 <기믹 1~3 \| 패턴 p01~p10 \| 초기화>` (64칸 안 가장 가까운 보스) |
| `magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_pumpkin_reaper.yml` (새) | 보스 `CA_Boss_pumpkin_reaper` + 기믹 소품 9종 |
| `magic-mythic-paper/mythicmobs/Skills/chacademi_boss_pumpkin_reaper.yml` (새) | `CHA_REAPER_*` 24개 (모션 14 + 패턴 10) |
| `magic-mythic-paper/models/pumpkin_reaper/*.bbmodel` (새) | 보스 모델 기본·이로치 (애니 26개). ModelEngine blueprints 로 |

## 1. 동작 요약

- 소환 위치 = 경기장 중심, 반경 20 (`chaboss arena=`). 플레이어가 60초 동안 경기장에 없으면 처음 상태(체력 가득, 기믹 다시).
- 페이즈: 100~70% 패턴 1·2·3·4 → 70~40% +5·6·8·9 → 40~15% +7·10, 수확 베기 2연타 → 15% 아래 전부, 간격 짧게.
- **기믹 기준선 70% / 40% / 15%**: 체력이 기준선 아래로 못 내려감(그 순간 멈춤, 건너뛰기 방지) → 소환 위치로 귀환(vanish/appear) → 기믹 동안 무적(맞으면 deflect 연출) → 끝나면 패턴 재개. 각 기믹은 한 번만.
- 대상 규칙: 가까운 적(1·2), 가장 먼 적(3·7), 체력 비율 가장 낮은 적(6·10, 9의 메테오 일부), 가장 먼 적+그다음 체력 낮은 적(5), 전원(4·8·9).
- 빨간 예고는 전부 모드 예고 `chatelegraph` / `Telegraphs` (VFX 팩에는 예고 없음 — 사장님 지시 2026-10-31).
- 기믹 실패 = 경기장 안 전원 즉사 (`PR_Harvested` 연출 후 체력 0).

| 패턴 | 스킬 / 진행 | 예고 | 피해(첫 값) |
|---|---|---|---|
| 1 수확 베기 | CHA_REAPER_p01 / p01x2 | 부채꼴 240° 반경 7, 0.45초 | 11 (2연타 10+10) |
| 2 반달 회전베기 | CHA_REAPER_p02 | 원 반경 8 (보스 중심), 0.55초 | 10 + 밀침 |
| 3 부메랑 낫 | CHA_REAPER_p03 (chamobshot back) | 직선 18×2, 0.55초 | 갈 때 9, 올 때 9 |
| 4 호박 폭탄 | 애드온이 전원에게 0.4초 간격 p04 | 대상 원 2.2, 1.5초 | 9 + 불 |
| 5 영혼 사슬 | 애드온 (link VFX + bind) | 없음 (5초 카운트다운 VFX) | 12블록 못 벌리면 둘 다 16 |
| 6 땅속의 손 | p06 + 대상 위치에 p06_hand ×5 | 원 1.4, 0.6초 ×5 | 6 + 띄움 + 둔화 |
| 7 유령 돌진 | 애드온 (순간이동·이동·저주 장판) + p07 | 직선 26×2.6, 0.6초 | 11, 장판 0.5초마다 2 + 둔화 (10초) |
| 8 장난칠까 사탕 줄까 | 애드온 (사탕 모델 3개) | 떨어질 자리 원 0.9, 0.5초 (피해 없음) | 주우면 반은 회복 25%, 반은 실명·둔화 3초 |
| 9 사탕 비 | 애드온 (메테오 2파 + 나눠 맞기) | 메테오 원 1.3, 0.8초 / 중앙 원 3, 2초 | 메테오 7 + 끈적 장판 3초. 나눠 맞기 30÷인원(3명 이상), 모자라면 경기장 전원 24 |
| 10 수확자의 도약 | CHA_REAPER_p10 (blink) | 원 반경 5, 0.75초 | 13 + 밀침 |

기믹:
1. **거짓말하는 호박들** — 표정 5종 중 3개가 가장자리·가운데·가장자리에 솟음(`CA_Reaper_pumpkin_<face>`). 단서 3줄(모두 참)을 플레이어에게 나눠 채팅 1번 + 액션바 2초마다. 두 줄을 합쳐야 하나로 좁혀짐. 15초 경기장 예고. 진짜를 때리면 성공, 가짜를 때리거나 시간 끝 = 실패. 좌우는 보는 쪽마다 달라서 단서에 "가운데/가장자리/○○ 호박 옆"만 쓴다. (불꽃 밝기 단서는 호박 모델 밝기 3종이 없어 뺐음)
2. **세 번의 종소리** — 분신 3(`CA_Reaper_clone`, 촛불 보라·주황·청록 VFX). 플레이어 머리 위 표식(TextDisplay ◆, 서로 다른 색) = **그 색 분신은 때리면 안 됨** (설명은 안 함). 2초 간격 종 3번. 세 번째 종 뒤 1.5초 안에 세 분신을 서로 다른 사람이 1초 안에 때리면 성공. 종 전에 때림 / 자기 색 / 둘이 같은 분신 / 시간 끝 = 합체 → 경기장 예고 0.9초 → 전체 베기.
3. **마지막 등불** — 어둠(DARKNESS 32초). 등불(`CA_Reaper_lantern`)에 다가가면 머리 위로(승객), 웅크리면 내려놓음(같은 사람 1초 못 줍기). 든 사람은 초당 최대 체력 4%. 빛 반경 4 → 초당 0.15 줄어듦(최소 1.5), 허수아비(`CA_Reaper_scarecrow`, 최대 4) 처치 → 사탕 → 머리 위로 → 빛 안에 들어가면 반경 +1.2(최대 9). 30초 끝 경기장 예고 0.9초 → 휩쓸기, 빛 밖 즉사. 빛 VFX `PR_LanternLight` 는 runtime areaRadius = 현재 반경 (0.5 이상 바뀌면 다시 붙임).

## 2. 배포 (GPT)

1. MagicMythic jar 빌드 (school / 보스가 있는 서버). 테스트: 기존 MagicMythic 테스트 + 컴파일만이라도.
2. `mythicmobs/Mobs/chacademi_boss_pumpkin_reaper.yml` → `plugins/MythicMobs/Mobs/`, `Skills/chacademi_boss_pumpkin_reaper.yml` → `plugins/MythicMobs/Skills/`, `/mm reload`.
3. ModelEngine: `models/pumpkin_reaper/pumpkin_reaper.bbmodel` → blueprints (모델 id `pumpkin_reaper`). 이로치 bbmodel 은 지금 미몹에서 안 씀(필요하면 `CA_Boss_pumpkin_reaper_shiny` 추가).
   **아직 없는 모델 (사장님 Blockbench 제작 예정)**: `reaper_pumpkin_smile/angry/sad/blank/surprised`, `reaper_lantern`, `reaper_candy`, `reaper_scarecrow`. 없으면 소품은 기본 엔티티로 보임(허스크·갑옷 거치대) — 판정은 그대로.
4. PortableVFX 팩 16개 (사장님 PC `Desktop\차카데미 vfx_Claude\PR_*` 또는 채팅의 PumpkinReaper_VFX_all.zip) → 클라이언트 PortableVFX `claude` 이름공간. 이 코드는 `claude:pr_<팩 이름 소문자>/<시스템 소문자>` 로 부른다
   (예 `claude:pr_harvestslash/slashback`, `claude:pr_candyrain/sharemark`). 팩 id 규칙이 다르면 ReaperBoss.java·Skills yml 의 `pr_` id 만 바꾸면 된다.
5. 보스 소환: 경기장 한가운데에서 `/mm mobs spawn CA_Boss_pumpkin_reaper` (소환 위치 = 경기장 중심, 정면 = 소환할 때 바라본 방향 → 호박 3개가 그 앞에 섬).

## 3. 확인 못 한 것 / 주의

- **컴파일·실행 확인 없음.** API 이름은 저장소 안 기존 코드(GameplayVisual·NovaLife3·ReactionVfx·TelegraphMech)를 따라 썼다: PortableVfxService `follow`, `startFollowCast(req,uuid,seed,width,scaleInput)`, `startCast(req,basis,seed,width,scaleInput,linkLength)`, `updateCast(...,linkLength)`, `stop`, `finishCast(uuid,bool)`.
- ModelEngine 상태 끄기를 `state{s=...;r=true}` 로 썼다 (사탕 비 루프·분열 자세 해제). ME 버전에 따라 이름이 다르면 맞출 것.
- 등불·사탕·표식은 플레이어 **승객**으로 태운다 (ModelEngine 모델이 머리 위에 보이는지 실제로 확인 필요). 나갈 때 내려 줌.
- 장난(저주)의 "썩은 호박 시야"는 머리 장비를 바꾸지 않고 실명 3초로 대신했다 (아이템 분실 위험 없음). 진짜 조각 호박 화면이 필요하면 클라 쪽 오버레이로.
- 즉사는 `setHealth(0)` (보호·토템 무시). 경기장 밖(반경 26 바깥)은 대상이 아님.
- 서버 부담: 보스당 Ticker 1개(5틱마다 판단), 장판·소품 근접 검사도 5~10틱마다. 예고 패킷·지연 작업은 기존 Telegraphs 그대로.
- 수치(체력 12000, 피해들, 기믹 시간)는 첫 값. 3인 테스트 후 조정.

## 4. 시험 명령

```
/테스트 보스 하베스트 기믹 1|2|3     (체력을 기준선으로 맞추고 바로 기믹)
/테스트 보스 하베스트 패턴 p01~p10   (그 패턴 한 번, 다음 패턴은 5초 뒤)
/테스트 보스 하베스트 초기화
```
