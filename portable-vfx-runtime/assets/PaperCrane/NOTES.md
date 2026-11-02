# PaperCrane (종이학) — 바람 초급, 변환 메모

## 시스템 / 역할
| id | role | duration | 시작 |
|---|---|---|---|
| `projectile` | projectile (종이학 1마리, 추적) | 8.0 s loop | 시전 시 |
| `impact` | impact (종이학 터짐) | 0.2 s (잔여 입자 ~1.3 s) | 적중 시 hitPoint |

- 종이학 = 메쉬 1개 (`crane`, 삼각형 15개, 양면). 로컬 +Z = 부리(진행 방향), +Y = 위. 모드가 매 틱 위치 + 진행 방향(추적 곡선의 접선)을 넘기면 된다 — 추적 조향은 서버/모드 몫, 연출은 방향만 따른다.
- 종이 재질: `T_Paper` 2×2 음영 아틀라스(밝음→어두움)에 각 면을 매핑해 접힌 종이처럼 보이고, 면 경계(접힌 선)가 청록으로 빛난다. 알파 블렌드 + HDR tint 1.6.
- 비행이 8 s 를 넘으면 본체(`P01`, 수명 8 s)가 다시 생성되므로 추적 최대 시간은 8 s 이하로.

## 시간표
- 비행: 종이학 + 작은 기운 + 앞쪽 조준 고리(초당 3.5) + 날개 끝 비행운 2줄 + 바람 줄기·말림 + 떨어지는 종이 조각 + 먹빛 바람 조각
- 적중 0.00: 섬광, 종이접기 사각 문양 원(0.6 s), 충격 고리 2겹, 베기 호 3개, 줄기 20개, 종이 조각 16장 흩날림, 먹빛 조각 6개, 빛 입자 28개

## 피해 권장 시점
- 적중 순간 1회 (`impact` t=0).

## 런타임 입력
- projectile: 위치 + 진행 방향(매 틱). impact: hitPoint, surfaceNormal, incomingDirection (공중 = −incomingDirection). scale 없음.

## 클라이언트 호환
- `client_compat.py check`: OK. 비지원 필드 미사용 (follow 등 없음).
- 고유 텍스처 RGBA: 1.00 MiB (11장, 최대 256 px).
- 필요 기능: meshParticle (종이학 메쉬 — 핵심, 없으면 종이학이 안 보임), bloom, flipbook, stretchedBillboard, rotation3D.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
