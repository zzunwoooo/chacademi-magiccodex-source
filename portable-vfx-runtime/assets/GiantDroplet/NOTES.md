# GiantDroplet (거대 물방울) — 물 · 비전

## 시스템 / 역할 (3개)
- `summon` — **static**. P = 물방울이 생기는 바닥 지점(시전자 앞 1.8 m 권장). 0–0.75 s: 물 마법진 + 나선 물줄기 + 물방울 모임 → 지름 3.2 m 물방울(중심 1.6 m 높이)로 자람. 1.3 s 종료.
- `droplet` — **projectile**. **0.75 s 에 P + (0, 1.6, 0) 에서 발사**, 바닥을 따라 굴러감(중심 높이 1.6 m 유지, 모드가 위치·진행 방향을 매 틱 넘김). 지름 3.2 m.
  local +Z = 진행, +Y = 위. 구르는 물 고리 3개(로컬 X축 회전 3.2 rad/s ≈ 속도 5 m/s 기준), 내부 물빛·기포·반짝임, 앞쪽 물거품.
  world 꼬리(rateOverDistance): 바닥 파문, 젖은 자국, 튀는 물방울, 물줄기, 물보라, 먹빛 물안개.
  layers 수명 20 s(비행 최대 20 s), 명중/사거리 끝에서 표준 규칙대로 local 층 지우고 `burst` 생성.
- `burst` — **impact**. 사거리 끝 = 공중 규칙(surfaceNormal = −진행 방향), 벽 = 벽 법선. 1.4 s.

## 시간표 (미리보기 기준)
| 시각 | 연출 |
|---|---|
| 0.0–0.75 | summon: 물방울이 모임 (0.7 섬광 + 충격) |
| 0.75–3.75 | droplet: 14 m 를 3 s 에 굴러감 (속도·사거리는 모드 설정) |
| 끝 | burst: 섬광, 껍질 팽창, 파동 2겹, 물보라 왕관 + 링, 물방울 90, 물줄기, 물 리본, 물안개, 먹빛 안개 |

## 피해 / 끌기 권장
- 굴러가는 동안 물방울에 닿은 적을 **삼켜서(물방울 중심 근처로 붙잡아) 함께 이동**, 0.5 s 간격 피해.
- 끝(사거리 끝·벽)에서 `burst` 와 함께 큰 피해 + 붙잡은 적 해제.

## 런타임 입력
- summon: P. droplet: 매 틱 위치 + 진행 방향. burst: hitPoint / surfaceNormal / incomingDirection.
- 크기 조정 입력 없음(고정 지름 3.2 m).

## 클라이언트 호환
- `client_compat.py fix` 0 change(s), `check` OK, `validate.py` OK. 비지원 필드 없음 (rollWithTravel 대신 고정 속도 회전으로 구름 표현).
- 고유 텍스처 RGBA 2.64 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
