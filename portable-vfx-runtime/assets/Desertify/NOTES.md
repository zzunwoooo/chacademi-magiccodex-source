# Desertify (사막화) — 대지 비전

## 시스템 / 역할 (둘 다 시전 순간 시작)
- `zone` (static, 8.6 s): P = 사막화 중심(바닥). 기준 방향 무관.
- `haste` (attached, 8.2 s): 시전자 발 + 수평 방향을 매 프레임 따라감. 발목 금빛 바람 고리 + 움직일 때 뒤로 튀는 모래·속도선(world, rateOverDistance).
- SCENE.scale `areaRadius` (reference 7 m, 배율 min 0.5, max 1.4). 주의: scale 은 팩 전체에 적용되므로 haste 도 같은 배율로 커진다(반경 9.8 m 일 때 발목 고리 ×1.4).

## 시간표 (zone)
| 시각 (s) | 연출 |
|---|---|
| 0.0–1.2 | 사막이 퍼짐: 모래 언덕 바닥(반경 ~7 m)이 중심에서 커짐, 갈라진 땅, 열풍 고리·충격파, 모래·돌 분출, 마법진(은은하게) |
| 1.2–7.2 | 사막 유지: 모래 물결 2겹 천천히 회전, 아지랑이(밝은/어두운 줄기), 햇살 줄기, 반짝임, 바람에 흐르는 모래, 가라앉는 모래 알갱이, 균열 맥동(1.2/2.7/4.2/5.7 s), 모래 회오리 3개 |
| 7.2–8.6 | 사막이 걷힘: 바람 돌풍, 안쪽으로 줄어드는 먼지 고리, 입자 |

## 효과 권장 시점
- 0.3–8.0 s: 범위 안 적 감속, 시전자 이속 증가(약 8 s). haste 는 시전자 버프 지속과 같게 8.2 s; 버프가 일찍 끝나면 모드가 stop.

## 런타임 입력
- zone: P + areaRadius. haste: 시전자 발 위치 + 수평 방향(매 프레임).

## 클라이언트 호환
- `client_compat.py check` OK. 고유 텍스처 RGBA 2.96 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
