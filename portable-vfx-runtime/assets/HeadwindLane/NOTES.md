# HeadwindLane (역풍로) — 바람 중급 · 변환 메모

## 시스템 / 역할
| id | role | 배치 | 길이 | 시작 |
|---|---|---|---|---|
| lane | static | P = 시전자 발 위치, 기준 방향 = 시전자 수평 정면(+Z). 길: z 0.8 → 10.8 m, 반폭 1.25 m | 5.0 s (+ 잔상 ~0.6 s) | 시전 순간 |
| hit | impact | 피해 틱마다 적 1명당 1개: hitPoint = 적 발, surfaceNormal = (0,1,0), incomingDirection = 길 정면(+Z) → local +Z = 밀리는 방향 | 0.1 s (+ 잔상 ~0.7 s) | 피해 틱마다 |

- 바닥 요소(레일·룬·바닥 빛)는 0.3 s 동안 시전자 쪽에서 앞으로 뻗어 나간다(크기 0.05→1, 원점 = 시전자).
- 길 길이 10 m 기준. scale 없음 (길이를 바꾸려면 Z0/LEN 을 바꿔 재생성).

## 시간표 (lane)
- 0.00 입구 섬광 + 돌풍 앞면 2장이 길 끝까지 달림(28 m/s), 정면 줄기 버스트, 바닥 충격파
- 0.00–5.00 유지: 어두운 바닥 그림자 + 청록 바닥 빛, 이중 레일, 중앙 룬 8칸, 앞으로 흐르는 화살표(›)들,
  입구의 세로 바람 문양 + 룬 고리(반대 회전), 0.3 s 부터 0.5 s 간격 터널 고리(7.5 m/s 전진, 9회),
  흐르는 바람 리본(밝은/먹빛), 줄기, 레일 줄기, 작은 돌풍, 먹빛 파편, 낮은 먹빛 먼지, 부유 입자
- 4.60 마지막 돌풍 앞면이 길을 쓸고, 4.75 입구 문양이 회전하며 접힘

## 피해 권장
- 길 위(반폭 1.25 m, 높이 0–2 m) 적에게 0.5 s 간격 (0.3 s ~ 4.8 s, 터널 고리 출발과 같은 간격), 매 틱 `hit` 재생 + 뒤(+Z)로 밀기.

## 런타임 입력
- lane: P + 시전자 수평 방향 (static 기본) / hit: hitPoint, surfaceNormal, incomingDirection

## 클라이언트 검사
- client_compat fix: 0 changes, check: OK, validate: OK
- 층 31 (lane 25 + hit 6), 고유 텍스처 RGBA 2.13 MiB


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
