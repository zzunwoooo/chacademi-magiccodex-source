# WhistleBlade (휘파람 칼날) — 바람 초급, 변환 메모

## 시스템 / 역할
| id | role | duration | 시작 |
|---|---|---|---|
| `cast` | attached (시전자 발 + 수평 방향) | 0.25 s | 시전 t=0 |
| `bladeA` | projectile (첫 칼날, 오른쪽 위→왼쪽 아래로 기운 초승달) | 3.0 s loop | t≈0.03 s |
| `bladeB` | projectile (둘째 칼날, 반대로 기운 초승달) | 3.0 s loop | t≈0.21 s (0.18 s 뒤) |
| `impact` | impact (두 칼날 공용) | 0.1 s (잔여 ~1 s) | 칼날마다 적중 시 새 인스턴스 |

- 칼날 = 진행 방향(+Z)으로 볼록한 초승달 메쉬(`cres`, 140°), 로컬 Z축 기준 ±1.0 rad 기울임(A/B 가 X 자). 뒤에 먹빛 초승달, 바깥 은은한 빛 띠, 지나간 자리에 잔상 초승달(월드, 1.8 m 마다, 0.14 s).
- 짧은 사거리: 미리보기 약 5.5 m 를 0.32 s (≈17 m/s). 실제 속도·사거리는 서버. 칼날 수명 3 s 이내.
- 발사 위치: 시전자 정면 가슴 높이 (로컬 [0, 1.25, 0.7]).

## 시간표
- 0.00 바닥 고리, 첫 휘두름 호 + 휘파람 소용돌이 + 압력 고리 → 0.03 bladeA 발사
- 0.18 둘째 휘두름 (반대 기울기) → 0.21 bladeB 발사
- 비행: 초승달 + 휘파람 압력 고리(초당 9) + 바람 줄기·빛 입자·먹빛 조각 꼬리
- 적중: 작은 섬광, 베인 초승달 호, 충격 고리, 베기 2줄, 줄기 16개, 바람 말림, 먹빛 조각 5개

## 피해 권장 시점
- 칼날마다 적중 순간 1회 (총 2회).

## 런타임 입력
- cast: 발 위치 + 수평 방향. blade: 위치 + 진행 방향. impact: hitPoint, surfaceNormal, incomingDirection (공중 = −incomingDirection). scale 없음.

## 클라이언트 호환
- `client_compat.py check`: OK. 비지원 필드 미사용.
- 고유 텍스처 RGBA: 1.22 MiB (10장).
- 층 32개 (bladeA/B 가 같은 8층을 기울기만 바꿔 복제 — 고유 구성은 24층).
- 필요 기능: meshParticle (초승달·잔상·고리 — 핵심), flipbook (휘두름 호), bloom, stretchedBillboard, worldSpace 메쉬(잔상).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
