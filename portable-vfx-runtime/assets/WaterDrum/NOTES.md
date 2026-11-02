# WaterDrum (물장구) — 물 / 중급

## 시스템
- `main` — role **static**, 1개. P = 물북을 놓은 바닥 지점 (local +Y = 위, +Z = 시전자 수평 정면). duration 3.3 s, loop 없음.
- 런타임 입력: P(지점) + 서버가 주는 수평 방향, `areaRadius` (scale: reference 4 m, 배율 min 0.5 / max 1.5 → s = clamp(R/4, 0.5, 1.5)).

## 시간표
| t (s) | 연출 |
|---|---|
| 0.00–0.55 | 물빛 마법진(링·육망성·12각 별·정점 원·룬 띠 2겹·물 문장)이 그려짐 |
| 0.15–0.50 | 바닥에서 물기둥 통(물북 몸통, 흐르는 물 메쉬 2겹)이 솟고 물방울·물보라 |
| 0.45 | 물막(북 가죽) 펼쳐짐 |
| **0.75 / 1.45 / 2.15** | **북 타격 3회**: 물막 펀치 + 섬광 + 물기둥 + 바닥 충격파 링(반경 ≈3.8 / 4.1 / 4.5 m) + 퍼지는 물보라 벽 + 짙은 심해 파편 + 안개 파동. 회차마다 입자 수 ×1 / ×1.25 / ×1.6 |
| 2.55 | 물북 붕괴(물보라·물방울·물웅덩이 파문·거품) |
| 3.0–3.3 | 마법진 소멸 |

## 피해 권장 시점
- 3회: **0.77 s, 1.47 s, 2.17 s** (충격파 링이 퍼지기 시작하는 순간). 범위는 각 링 반경(위 표, areaRadius 배율 적용).

## 클라이언트
- client_compat check: OK (fix: 0 change, no visual change)
- 고유 텍스처 RGBA: 2.38 MiB
- 사용 기능: meshParticle(마법진·물북·충격파), flipbook, stretchedBillboard, radial/orbital velocity, noise, bloom. 지원 안 되는 기능 없음.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
