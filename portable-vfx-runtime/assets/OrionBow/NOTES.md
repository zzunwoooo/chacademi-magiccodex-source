# OrionBow (오리온의 활) — 빛 / 고급

## 시스템 (모드가 각각 시작)
| id | 역할 | 길이 | 층 | 시작 |
|---|---|---|---|---|
| bow | static | 2.4 s | 22 | 시전 즉시, P = 시전자 발, 기준 = 시전자 수평 방향(+Z). 활 중심 = 로컬 (0, 4.4, −0.6), 하늘 고리 = (0, 4.4, −2.2) (시전자 뒤 위) |
| arrow | projectile | 최대 2.5 s | 10 | 1.45 s, 시작점 = 로컬 (0, 4.4, 1.3) (활 앞, 화살 촉 위치), 목표 방향으로 직선 관통 |
| pierce | impact | 0.7 s | 4 | 화살이 적을 지날 때마다 (공중 규칙, 적 몸 중심) |
| impact | impact | 1.7 s | 15 | 최종 지형 충돌 (바닥/벽/공중): 박힌 화살·균열·빛기둥·파동 |

arrow 는 loop:false 한 발 (몸체 P01/P02/A06 는 t=0 버스트, 수명 2.5 s) → 비행 2.5 s 이하.

## 시간표
0.0 하늘 고리·룬·문장 · 0.3–0.8 별빛 활 그려짐 (별 7개 + 오리온 허리띠 3별) · 0.6–1.4 시위 당김 (정지→중간→당김 3단 교차), 굵은 화살 형성·빛 모임·충전 고리
· 1.45 발사 (섬광·고리 3겹·시위 튕김) · 1.5–2.4 활이 별가루로 흩어짐 · (데모) 1.61/1.70 관통 · 1.81 최종 충돌.

## 피해 권장 시점
- 관통: 화살이 지나는 틱 (pierce 재생과 같은 틱), 경로 위 모든 적.
- 최종 충돌: impact 시작 (+0.0 s), 반경 ~2 m.

## 런타임 입력
- projectile: 위치 + 진행 방향. impact/pierce: hitPoint, surfaceNormal, incomingDirection. scale 없음.

## 클라이언트
- `client_compat.py check`: OK (fix 0건, 시각 변화 없음). 고유 텍스처 RGBA 2.33 MiB. trail 사용(A06).
- 활은 시전자 피치(위아래 조준)를 따르지 않는다 (static 기준은 수평 방향만).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
