# 3인 파티 기믹 보스 "모르디안의 원령" — 미몹 + 애드온 + 모델 + VFX (Claude 2026-11-03, **빌드 안 함**)

브랜치 `claude/mordian-boss-20261103` (기준 `claude/time-wraith-boss-20261103` @ d1a5014). 이 환경에는 Paper·MythicMobs 의존성이 없어
**컴파일을 못 했다** (javac 구문 검사만 — 파싱 오류 없음) — 호스팅에서 `Build-Server.ps1 -Verify` 로 먼저 빌드·테스트할 것.

스토리: 공식 기록의 '배신자' 모르디안(페이크 악역)의 원념이 뭉친 형체 없는 검은 그림자. 기록 속 긁혀 지워진 얼굴 = 긁힌 가면.
진실은 드러내지 않고 마지막에 "…나는, 부르지 않았다." 한 줄만 남김.

## 0. 바뀐 파일

| 파일 | 내용 |
|---|---|
| `magic-mythic-paper/src/main/java/.../MordianBoss.java` (새) | 미몹 메커닉 `chamord` (보스 진행자), 이벤트 `MordianBoss.Hooks` (기준선·무적·소품 피해 규칙·그림자 대상 고정·처치) |
| `ReaperBoss.java` | `Fx.held(id, at, yaw, ticks)` 추가 (멈출 수 있는 static 시스템 — 룬 hold, 제물 마법진) |
| `MagicMythicPlugin.java` | `chamord` 등록, Hooks 등록, 끌 때 `MordianBoss.closeAll()` |
| `MythicCommands.java` | `/테스트 보스 모르디안 <패턴 id \| 기믹 1~6 \| 재봉인 \| 혼자 켜기·끄기 \| 초기화>` |
| `magic-mythic-paper/mythicmobs/Mobs/chacademi_boss_mordian.yml` (새) | 보스 `CA_Boss_mordian_wraith` + 소품 `CA_Mordian_stake / shadow / seal_pillar` |
| `magic-mythic-paper/mythicmobs/Skills/chacademi_boss_mordian.yml` (새) | 보스 모션 `CHA_MD_a_*`, 소품 모션 `CHA_MD_p_*`, 루프 지우기 `CHA_MD_clear` |
| `magic-mythic-paper/models/mordian/*.bbmodel` (새) | 보스 `mordian_wraith` (애니 18) + `_shiny` (지금 미몹에서 안 씀). ModelEngine blueprints (파일 이름 = 모델 id) |
| `magic-mythic-paper/models/mordian/props/*.bbmodel` (새) | `mordian_stake`, `mordian_shadow`, `mordian_seal_pillar` |
| `magic-mythic-paper/models/mordian/source/` | 모델 생성 원본 (gen.py·models.py·props.py·render.py) + 미리보기 틀 |
| `portable-vfx-runtime/assets/Mordian*/` (새, 16팩) | **VFX 완성 팩** (vfx.json + textures + NOTES.md). 예고 없음. 팩당 고유 RGBA 1.4~4.0 MiB, client_compat check 전부 OK |
| `portable-vfx-runtime/tools/mordian_vfx/` (새) | VFX 원본 (팩별 스펙·텍스처 생성기) + `CONTRACT.md` (팩·시스템 id·붙이는 시점 = 서버 연동 표) |

## 1. 동작 요약

- 소환 위치 = 경기장 중심(반경 16), 소환 때 바라본 방향 = 정면 (룬·봉인석 배치 기준). 입장 대사 "기록은... 나를 그렇게 기억하더군."
- **그로기 없음.** 기믹 중 보스 무적, 성공·실패 모두 바로 패턴 복귀 (⑥ 제외). 실패 즉사는 기믹마다 아래.
- 짤패턴 (빨간 예고 = 모드 예고, 판정 = 예고 모양 그대로, 원소 dark):

| id | 패턴 | 모양 | 예고 | 피해 | 대상 |
|---|---|---|---|---|---|
| claw | 할퀴기 연속 (오른→왼, 30% 이하 3연타) | 부채꼴 r 7 · 150° | 0.45초 (광폭 0.35) | 26 | 가까운 적, 매 타 재조준 |
| slam | 내려찍기 | 직선 9 × 6 → 충격파 원 r 8 | 0.95 + 0.5초 | 30 + 16 | 가까운 적 |
| sink | 그림자 잠행 | 원 r 4.5 (사라졌다 그 자리에서 솟음) | 0.9초 | 30 | 먼 적 |
| pull | 사슬 끌어당기기 → 할퀴기 | 직선 (폭 2.5) → 부채꼴 | 0.75 + 0.4초 | 14 + 24 | 체력 낮은 적 |
| grasp | 검은 손아귀 ×3 | 원 r 2 (그때 위치) + 둔화 1초 | 0.8초씩 | 16 | 전원 |
| bolt | 저주 화살 5갈래 | 직선 20 × 1.6 (±40°) | 0.6초 | 20 | 먼 적 |
| brand | 개인 낙인 → 폭발 | 낙인 3초, 마지막 1초 원 r 4 | 1초 | 34 | 무작위 1명 |
| ring | 금지된 원 | 도넛 r 5~13 (직선 8개) → 안쪽 원 r 5 | 1.0 + 0.8초 | 28 / 32 | 보스 중심 |
| rain | 어둠의 비 ×8 | 원 r 3 (플레이어 위치 + 무작위) | 1초, 0.2초 간격 | 22 | 전원 + 전역 |
| spin | 사슬 회전 ×2 | 원 r 6 | 0.6 + 0.7초 | 20 | 보스 중심 |
| dash | 그림자 돌진 ×3 | 직선 ≤12 × 3.5 → 끝까지 미끄러짐 | 0.42초 | 26 | 먼 적, 매번 재조준 |

- **① 85% 결박의 사슬**: 무작위 2명 사슬(`bind_hold`), 둘 거리 8 미만 = 6%/0.5초 + 둔화(`bind_slack`), 14 초과 = 5%/0.5초(`bind_taut`).
  남은 1명만 그림자 말뚝 3개(`CA_Mordian_stake`, 체력 600, 경기장 가장자리) 를 칠 수 있음. 20초 안에 다 부수면 성공, 못 부수면 묶인 둘 사망.
  이 동안 보스는 무적으로 가벼운 패턴(bolt·rain·grasp)만 느리게.
- **② 70% 금지된 의식**: 보스 중앙(사라졌다 솟음) → 반경 9 오각형 위치에 룬 5개를 무작위 순서로 그림(1.2초 간격, 문양 = 위치 번호). 그 **역순**으로
  룬 위에 1.5초 서서 지움(내려오면 다시 처음부터). 다른 룬에 0.5초 서면 틀림 = 남은 시간 -5초, 두 번 틀리면 전멸. 30초 초과도 전멸.
- **③ 55% 각자의 그림자**: 보스가 바닥으로 사라짐. 플레이어마다 그림자(`CA_Mordian_shadow`, 체력 400, 이름표 "○○ 의 그림자")가 주인만 쫓음.
  주인에게 닿으면 최대 체력 25% + 둔화(1초 쿨). **자기 그림자는 못 때림**(안내 메시지). 25초 뒤 남은 그림자의 주인 사망. 다 잡으면 보스가 솟아오름.
- **④ 40% 제물의 마법진**: 5라운드. 마법진 2~3개(반경 2.5) + 숫자(TextDisplay, 1~3). 숫자 합 = 살아 있는 인원.
  4초(4·5라운드 3초) 뒤 인원 판정: 맞음 = 조용히 소멸, 틀림 = 안에 있던 사람 사망, 빈 채 터지면 전원 최대 체력 50%. 라운드 사이 저주 화살 1발(가장 먼 적).
- **⑤ 25% 긁힌 기록 낭독**: 명령 8번(간격 4초 → 2초). 모여라(서로 4칸 안)/흩어져라(서로 8칸 이상), 나를 보라(시선 60° 안)/등을 돌려라(120° 밖),
  멈춰라(판정 1초 전부터 0.5칸 미만)/움직여라(1칸 이상), 떠올라라(공중)/엎드려라(웅크리기). **취소선(§m) 명령은 반대로**.
  마지막 두 번은 다른 종류 두 명령 중 하나만 긁힘. 첫 실수 = 최대 체력 40% + 망각 표식, 표식 상태에서 또 틀리면 사망. 끝나면 바로 패턴.
- **⑥ 10% 대마법사의 봉인**: 보스 중앙 `seal_struggle`. 봉인석 기둥 3개(반경 11, 120° 간격). 6~8초마다 기둥 2개(혼자 시험은 1개)를 고르고
  2초 빨간 직선 예고 → 검은 광선. 광선 위(폭 2)에 선 첫 사람이 막음: 0.25초마다 최대 체력 2% + 0.6%씩 증가(최대 12%), 내려오면 초기화 → 교대.
  막지 못한 광선 = 그 기둥 균열 +0.25초, 한 기둥 균열 3초 = 전원 사망(재도전 가능, 10% 기준선 복구). **30초 버티면 재봉인**:
  `sealed` 모션 + `MordianReseal` 5초 → 대사 → 체력 0 = 처치 (남은 10% 딜 없음). 보스 death 애니 = sealed 끝 자세 유지(솟아오르지 않음).
- 경기장(반경 16+6) 밖은 대상 아님. 60초 아무도 없으면 처음 상태.

## 2. 배포 (GPT)

1. MagicMythic jar 빌드.
2. `mythicmobs/Mobs/chacademi_boss_mordian.yml` → `plugins/MythicMobs/Mobs/`, `Skills/chacademi_boss_mordian.yml` → `plugins/MythicMobs/Skills/`, `/mm reload`.
3. ModelEngine blueprints: `models/mordian/mordian_wraith.bbmodel` + `models/mordian/props/` 3개 → `/meg reload` → ItemsAdder 병합 `/iazip`.
4. PortableVFX 팩 `portable-vfx-runtime/assets/Mordian*` (16개) → 클라이언트 PortableVFX `claude` 이름공간. 코드는 `claude:<팩 소문자>/<시스템>` (`tools/mordian_vfx/CONTRACT.md`).
5. 보스 소환: 경기장 한가운데에서 정면으로 쓸 방향을 바라보고 `/mm mobs spawn CA_Boss_mordian_wraith`.

## 3. 확인 못 한 것 / 주의

- **컴파일·실행 확인 없음.** API 는 TimeWraithBoss·ReaperBoss 와 같은 것 + Paper `Mob#setTarget`, `EntityTargetEvent`, `TextDisplay`, `Entity#customName`.
- `Fx.held` = `startCast(request, basis, seed, 0, 0, -1)` 로 static 시스템을 handle 과 함께 시작 (Fx.link 와 같은 API, 길이 -1). PortableVFX 가 static role 에 basis 를 받는지 확인.
- 링크 VFX (`Fx.link`) 요청 길이가 10초라 ① 결박은 9초마다 다시 잇는다. ⑥ 광선은 6~8초마다 바뀌어 문제 없음.
- 그림자 분신: Mythic `meleeattack` 으로 주인을 쫓게 하고 실제 피해는 애드온 거리 판정(1.5칸). 분신의 몸통 피해는 Hooks 에서 취소.
- ⑤ 판정: 시선 = `Location#getDirection`, 공중 = `isOnGround`, 웅크리기 = `isSneaking`. 서버 틱 지연으로 경계 판정이 갈릴 수 있음.
- ④ 숫자는 TextDisplay (크기 2.2배). 서버 버전에 따라 `getTransformation().getScale()` 이 불변이면 새 Transformation 으로 바꿀 것.
- 보스 손 위치(`hand()`)는 발밑 기준 근사(앞 1.5, 오른쪽 1.5, 높이 3). 필요하면 ModelEngine 본 위치로 바꿀 것.
- 혼자 시험(`/테스트 보스 모르디안 혼자 켜기`): 즉사 대신 알림, 말뚝·자기 그림자 공격 허용, 봉인 광선은 한 줄.

## 4. 시험 명령

```
/테스트 보스 모르디안 패턴 claw|slam|sink|pull|grasp|bolt|brand|ring|rain|spin|dash
/테스트 보스 모르디안 기믹 1~6
/테스트 보스 모르디안 재봉인 | 혼자 켜기|끄기 | 초기화
```
