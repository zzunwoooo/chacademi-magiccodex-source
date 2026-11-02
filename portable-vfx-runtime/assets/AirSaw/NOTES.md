# AirSaw (바람 톱) — 바람 중급 · 변환 메모

## 시스템 / 역할
| id | role | 길이 | 내용 |
|---|---|---|---|
| projectile | projectile (loop, duration 30 s) | 비행 중 계속 | P = 원반 중심(가슴 높이 권장), local XZ 평면의 톱니 원반(지름 약 2.2 m, 앞이 12° 들림), Y축 회전 15 rad/s |
| impact | impact | 0.2 s (+ 잔상 ~1.3 s) | 벽 충돌 / 사거리 끝(공중 판정) 때 원반이 부서짐 |

- 투사체 1개 = 캐스트 1개. **천천히 전진**(데모 약 3.75 m/s, 실제 속도·사거리는 모드). 적을 관통하며 여러 번 벤다 → 적에 닿아도 impact 를 재생하지 않고 계속 진행, 벽/블록에 닿거나 사거리 끝일 때만 impact (air = −진행 방향).
- 원반 몸체·잔상·먹빛 그림자·룬 테두리는 수명 30 s 의 단일 입자(loop 한 주기 안에서 버스트 1회)라 비행이 30 s 를 넘지 않게 한다.
- 비행 중 레이어: 원반(밝은/잔상/먹빛), 반대 회전 룬 테두리, 중심 빛·별, 회전 바람 호(밝은/먹빛), 튀는 불꽃, 0.25 s 마다 앞 테두리 베기 섬광 + 먹빛 파편 + 줄기,
  world 잔상(원반 메아리, 줄기, 먹빛 파편, 입자, 먹빛 안개; rateOverDistance).

## 피해 권장
- 원반 반경 1.1 m 안의 적에게 0.25 s 간격 (발사 후 0.1 s, 0.35 s, 0.6 s … — 베기 섬광 간격과 같음). 같은 적 다단 히트 허용.
- impact 시 마지막 1회 (반경 1.5 m).

## 런타임 입력
- projectile: 위치·진행 방향 / impact: hitPoint, surfaceNormal, incomingDirection. scale 없음.

## 클라이언트 검사
- client_compat fix: 0 changes, check: OK, validate: OK
- 층 28 (projectile 17 + impact 11), 고유 텍스처 RGBA 2.06 MiB


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
