# IceSlide (얼음 미끄럼) — 물(얼음) / 중급

## 시스템 (2개)
- `slide` — role **attached** (시전자 발 위치 + 수평 방향 매 프레임). **+Z = 미끄러지는(이동) 방향** (GaleDash 처럼 모드가 이동 방향을 facing 으로 넘겨줄 것). duration 0.85 s, 연출 길이 0.8 s.
  - 시작(월드에 남음): 눈꽃 문장·고리·서리 고리·얼음 파편·섬광.
  - 이동 중 시전자에 붙음(local): 발밑 얼음 보드, 앞쪽 얼음 쐐기(충각) 5조각, 앞 물보라, 속도선, 물살.
  - 지나간 자리(world, `rateOverDistance`): 서리 균열 자국(1.4–1.8 s 남음), 얼음 궤적 선, 튀는 얼음 조각, 눈가루, 냉기 안개, 반짝임 — 이동 거리에 비례해서 깔리므로 속도·거리와 무관하게 맞는다.
  - 끝(0.8 s): 서리 고리·파편·눈 폭발.
- `bump` — role **impact**. 미끄러지다 적과 부딪힐 때마다 모드가 새로 생성: hitPoint = 적 몸(가슴 높이 권장), **공중 규약**(surfaceNormal = −incomingDirection, incomingDirection = 미끄러지는 방향) → 파편·충격선이 local −Y = 밀려나는 방향으로 날아간다. duration 0.9 s.
- 런타임 입력: slide = 발 위치 + 이동 방향, bump = hitPoint / surfaceNormal / incomingDirection. scale 없음.

## 시간표
| t (s) | 연출 |
|---|---|
| 0.00 | 시작 문장·서리 고리·파편 폭발 |
| 0.00–0.80 | 얼음 보드 + 앞 쐐기 타고 이동 (데모: 7 m, 0.55 s 에 5.6 m 후 감속), 얼음 궤적이 뒤로 깔림 |
| 접촉 시 | `bump` 1회 (데모: 0.27 s, 0.55 s) |
| 0.80 | 도착 폭발 |
| 0.8–2.5 | 궤적의 균열·안개가 사라짐 |

## 피해 권장 시점
- 이동 중(0~0.8 s) 시전자 앞 0.9 m 이내에 닿은 적마다 1회 + 넉백(이동 방향), 그 순간 `bump` 생성(0 s = 피해).

## 클라이언트
- client_compat check: OK (fix: 0 change, no visual change)
- 고유 텍스처 RGBA: 약 2.10 MiB
- 사용 기능: attachedFollow, worldSpace, rateOverDistance(world 레이어만), meshParticle(보드·쐐기·고리·균열), flipbook, stretchedBillboard, noise. 지원 안 되는 기능 없음.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
