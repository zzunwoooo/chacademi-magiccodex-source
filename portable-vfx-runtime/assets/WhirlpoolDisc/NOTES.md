# WhirlpoolDisc (소용돌이 원반) — 물 / 중급

## 시스템
- `main` — role **static**, 1개. P = 지정한 바닥 지점 (local +Y = 위). duration 3.8 s.
- 런타임 입력: P(+ 서버 수평 방향), `areaRadius` (scale: reference 2.5 m, 배율 min 0.5 / max 1.5).

## 시간표
| t (s) | 연출 |
|---|---|
| 0.00–0.35 | 물빛 마법진(링·8각 별·정점 원·룬 띠) |
| 0.15–0.45 | 소용돌이 열림: 나선 원반 2겹(역속도 회전), 깔때기형 나선 팔 메쉬 2겹, 테두리 물벽, 어두운 소용돌이 눈 |
| 0.25–3.2 | 안쪽으로 감겨 드는 물거품·물살·짙은 심해 파편·기포·테두리 안개 |
| **0.6 / 1.1 / 1.6 / 2.1 / 2.6 / 3.1** | **타격 6회**: 중심 물기둥 + 물방울 + 안쪽 물보라 왕관 + 안으로 조여드는 파문 + 테두리 섬광 + 회전 물살 호 |
| 3.25 | 붕괴: 소용돌이가 중심으로 줄어들며 마지막 물기둥·물보라·바깥 파문 |
| 3.6–3.8 | 마법진 소멸 |

## 피해 권장 시점
- **0.6, 1.1, 1.6, 2.1, 2.6, 3.1 s** (0.5 s 간격 6타), 반경 2.5 m × 배율. 필요하면 3.25 s 붕괴 때 마지막 1타.

## 클라이언트
- client_compat check: OK (fix: 0 change, no visual change)
- 고유 텍스처 RGBA: 약 2.18 MiB
- 사용 기능: meshParticle(마법진·나선 팔·원반·물벽·파문), flipbook, stretchedBillboard, orbital/radial velocity, noise. 지원 안 되는 기능 없음.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
