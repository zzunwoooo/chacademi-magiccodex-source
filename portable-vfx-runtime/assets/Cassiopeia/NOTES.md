# Cassiopeia (카시오페이아) — 빛 / 고급

## 시스템 (모드가 각각 시작)
| id | 역할 | 길이 | 층 | 시작 |
|---|---|---|---|---|
| sign | static | 2.4 s | 17 | 시전 즉시, P = 시전자 발, 기준 = 시전자 수평 방향(+Z). 시전자 뒤 위(로컬 (0, 4.7, −1.4))에 W 별자리 + 고리, 발밑 작은 마법진 |
| bolt | projectile | 최대 1.0 s | 7 | 튕길 때마다 새 인스턴스: 손 → 적1, 적1 → 적2 … 적4 → 적5 (구간 비행 ~0.1 s 권장) |
| hit | impact | 0.9 s | 8 | 적 1~4 명중 (공중 규칙, 적 가슴) |
| finale | impact | 1.2 s | 10 | 5번째 적 명중 (더 큰 마무리) |

## 시간표 (데모 기준, 연쇄 간격 0.15 s)
0.0 별자리·마법진 · 0.5 첫 빛 발사 · 명중 0.6 / 0.75 / 0.9 / 1.05 / 1.2 s · 별자리의 k번째 별이 k번째 명중 시각에 번쩍 (sign 안에 고정 시각으로 들어 있음 — 모드 연쇄 간격이 다르면 별 번쩍임만 어긋남) · 1.2 마무리 · 2.4 끝.

## 피해 권장 시점
- 각 bolt 가 도착하는 틱 (hit/finale 재생과 같은 틱). 5번째(finale)가 가장 큰 피해.
- W 모양은 대상 배치(좌우로 번갈아 가까움/멂)로 생긴다; 모드가 대상 순서를 좌→우로 정렬하면 W 로 읽힘.

## 런타임 입력
- projectile: 위치 + 진행 방향. hit/finale: hitPoint, surfaceNormal(=−incoming), incomingDirection. scale 없음.

## 클라이언트
- `client_compat.py check`: OK (fix 0건, 시각 변화 없음). 고유 텍스처 RGBA 1.21 MiB. trail 사용(B04).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
