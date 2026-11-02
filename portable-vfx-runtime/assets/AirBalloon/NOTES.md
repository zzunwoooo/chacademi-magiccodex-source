# AirBalloon (공기 풍선) — 바람 초급, 변환 메모

## 시스템 / 역할
| id | role | duration | 시작 |
|---|---|---|---|
| `projectile` | projectile (출렁이는 공기 풍선) | 6.0 s loop | 시전 시 |
| `impact` | impact (터짐 + 상승 기류) | 0.7 s (잔여 ~1.1 s) | 적/바닥/벽 적중 시 hitPoint |

- 풍선 = 빌보드 2겹(투명 막 + 뒤 은은한 막) + 안쪽 바람 소용돌이(초당 7) + 빛 반짝임. 꼬리: 실 같은 바람 줄기, 작은 공기 방울, 빛 입자, 먹빛 바람 조각.
- 던지기 궤적(포물선 등)은 서버 이동 그대로 — 연출은 위치·방향만 따른다. 미리보기는 약 5.3 m 를 0.9 s.
- 상승 기류 층(`I12~I14`)은 속도가 월드 위쪽(space world)이라 벽·공중 적중에서도 위로 솟는다.

## 시간표 (impact 기준)
- 0.00 터짐: 섬광, 막이 부풀며 사라짐(0.08 s), 공기 고리 2개(0.00/0.06), 막 조각 16개, 줄기 18개, 바람 말림 3개, 먹빛 조각 5개
- 0.00~0.7 상승 기류: 바닥(표면) 원 문양(고리·방울 표시 8개·소용돌이), 위로 솟는 바람 줄기(초당 65), 올라가는 말림·공기 방울
- ~1.1 모두 사라짐

## 피해 권장 시점
- 적중 순간 1회 (`impact` t=0) + 동시에 적을 살짝 띄움 (상승 기류 0~0.7 s 와 맞춤, 예: 위쪽 속도 0.4~0.5).

## 런타임 입력
- projectile: 위치 + 진행 방향. impact: hitPoint, surfaceNormal, incomingDirection (공중 = −incomingDirection). scale 없음.

## 클라이언트 호환
- `client_compat.py check`: OK. 비지원 필드 미사용.
- 고유 텍스처 RGBA: 0.96 MiB (11장).
- 필요 기능: meshParticle (표면 원 문양), bloom, flipbook, orbitalVelocity, stretchedBillboard, worldSpace.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
