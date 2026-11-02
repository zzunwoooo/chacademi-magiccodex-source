# FreezePulse (결빙 파동) — 물(얼음) / 중급

## 시스템
- `pulse` — role **attached**, 1개. 시전자 발 위치 + 수평 방향을 매 프레임 따라감(마법진·문장·모이는 냉기). duration 2.8 s.
- 파동 조각(서리 고리·얼음 가시·파편·안개·균열)은 `space: world` — 생성된 자리에 남는다(시전자가 움직여도 파동은 퍼진 자리에서 진행).
- 런타임 입력: 시전자 발 위치 + 수평 방향. scale 없음(반경 고정 2.6 / 4.0 / 5.6 m).

## 시간표
| t (s) | 연출 |
|---|---|
| 0.00–0.35 | 발밑 얼음 마법진(링·육망성·12각 별·정점 원·룬 띠·눈꽃 문장) + 냉기 모임 |
| **0.40** | 파동 1: 섬광·눈보라·안개, 서리 고리가 0.5 s 동안 반경 **2.6 m** 까지 → 0.85 s 고리 자리에 얼음 가시 원 |
| **1.00** | 파동 2: 반경 **4.0 m** (1.45 s 가시 원) |
| **1.65** | 파동 3: 반경 **5.6 m** (2.10 s 가시 원) + 바닥 서리 균열 8 m |
| 2.1–2.8 | 반짝임·남은 냉기 안개, 마법진 소멸 |

## 피해 권장 시점
- 파동마다 고리가 0.5 s 동안 0 → R 로 퍼짐. 단순 판정이면 **0.65 / 1.25 / 1.90 s** 에 반경 2.6 / 4.0 / 5.6 m (고리가 끝에 닿고 가시가 솟는 순간). 거리별 판정이면 거리 d 에서 t0 + 0.5·d/R.

## 클라이언트
- client_compat check: OK (fix: 0 change, no visual change)
- 고유 텍스처 RGBA: 약 1.97 MiB
- 사용 기능: attachedFollow(시전자 추적), worldSpace, meshParticle(마법진·서리 고리·얼음 가시 원), flipbook, stretchedBillboard, radial/orbital velocity, noise. 지원 안 되는 기능 없음.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
