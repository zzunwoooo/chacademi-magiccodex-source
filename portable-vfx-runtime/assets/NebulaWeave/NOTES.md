# NebulaWeave (성운 직조) — 빛 / 비전

시전자가 하늘에 점을 찍어 별자리를 그리면, 그 점마다 빛이 내린다.

## 시스템 (모드가 각각 시작)
| id | 역할 | 길이 | 층 | 시작 |
|---|---|---|---|---|
| canvas | static | 7.2 s | 31 | 시전 즉시, P = 범위 중심(바닥), 기준 = 시전자 수평 방향. 바닥 마법진(반경 6 m) + 하늘 캔버스(10 m: 고리·육각성·성운·어두운 장막·별) + 5.6 s 마무리 |
| node | static | 4.8 s | 7 | 플레이어가 점을 찍을 때마다 1개, P = 하늘의 그 점 |
| thread | projectile | 최대 1.5 s | 6 | 앞 점 → 새 점 (선을 그리는 빛 붓). 남는 선(T04/T05)은 world 입자로 ~3.3 s 유지 |
| knot | impact | 0.8 s | 4 | thread 가 새 점에 닿을 때 (공중 규칙) |
| rain | static | 1.4 s | 10 | 점마다 1개, P = 그 점 바로 아래 바닥. 빛기둥 높이 10 m (canvas 하늘 높이와 같음), 0.45 s 에 바닥 착탄 |

## 시간표 (데모: 점 6개)
0.0 canvas · 점 찍기 0.8 / 1.3 / 1.8 / 2.3 / 2.8 / 3.3 s (선 그리기 비행 0.48 s) · 빛 내림 3.8 + 0.25k (착탄 4.25 + 0.25k) · 5.1–5.6 수축 예고 · 5.6 마무리(섬광·파동·god ray·빛기둥 8·별비) · 7.2 끝.
- canvas 의 마무리는 5.6 s 고정 → 모드는 그리기를 ~3.5 s 안에, 빛 내림을 ~3.8–5.4 s 사이에 배치하면 맞는다.

## 피해 권장 시점
- 각 rain 착탄: rain 시작 + 0.45 s, 반경 ~1.3 m.
- 마무리: canvas 5.6 s, 반경 6 m.

## 런타임 입력
- `areaRadius` (scale: reference 6 m, 배율 min 0.6 / max 1.4) — 모든 시스템에 같은 배율.
- node/rain 위치는 모드가 넣는 점 좌표. projectile/impact 는 일반 규칙.

## 클라이언트
- `client_compat.py check`: OK (fix 0건, 시각 변화 없음). 고유 텍스처 RGBA 2.36 MiB. trail 사용(T03).
- rain 기둥은 높이 10 m 고정(런타임 높이 입력 없음, `height` 미지원) — 점이 더 높으면 기둥 위가 비어 보임.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
