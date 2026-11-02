# GlacierSpire (빙하 결정탑) — 물 · 비전

## 시스템 / 역할
- `spire` — **static**. P = 설치 지점(바닥), +Z = 시전자 수평 방향(대칭이라 무관). 저작 반경 4.5 m, `scale = {input:"areaRadius", reference:4.5, min:0.6, max:1.5}`. 길이 8.9 s.
  탑 = depthWrite 결정 메쉬(높이 ≈ 7 m) + 가산 모서리 선. **발사 기준점(크라운) = P + (0, 7.6, 0)·s**.
- `shard` — **projectile**. 발사 1회 = 인스턴스 1개, 크라운에서 가까운 적으로. local +Z = 진행 방향(얼음 결정 메쉬가 앞을 향함). t=0 에 크라운 섬광(world). 꼬리 = world + rateOverDistance.
- `shardHit` — **impact**. 적 몸통 명중 = 공중 규칙(surfaceNormal = −incomingDirection), 바닥/벽 명중 = 해당 면.

## 시간표 (spire, s)
| 시각 | 연출 |
|---|---|
| 0.0 | 육각 서리 마법진 + 서리 무늬·균열 |
| 0.2–0.9 | 결정탑이 솟음(살짝 넘침 후 안착), 충격 고리, 얼음 파편, 안개 |
| 0.9–8.0 | 유지: 크라운 눈꽃 결정 + 별빛 + 맥동 광채, 회전 룬 고리 2개(2.4 m / 5.0 m, 반대 방향), 빛 파편 8개 + 먹빛 파편 6개 공전, 바닥 냉기·먹빛 안개, 눈, 반짝임, 빛기둥, 상승 냉기 |
| 8.0 | 탑 붕괴: 섬광, 파동, 바닥 충격, 큰 파편(먹빛·빛)·잔파편, 크라운 폭발, 광선, 안개, 눈꽃 |
| 8.9 | 종료 |

## 피해 / 발사 권장
- 0.9–8.0 s 동안 **약 1.0 s 간격**으로 반경 안 가장 가까운 적에게 `shard` 발사(미리보기는 1.4, 2.4 … 7.4 s, 비행 0.32 s). 명중 시 `shardHit` + 피해.
- 8.0 s 붕괴 시 주변 작은 피해(선택).

## 런타임 입력
- spire: P + `areaRadius`. shard: 매 틱 위치 + 진행 방향. shardHit: hitPoint / surfaceNormal / incomingDirection.

## 클라이언트 호환
- `client_compat.py fix` 0 change(s), `check` OK, `validate.py` OK. 비지원 필드 없음, depthWrite 사용(지원).
- 고유 텍스처 RGBA 2.80 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
