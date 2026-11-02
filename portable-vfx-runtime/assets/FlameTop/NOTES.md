# FlameTop (불꽃 팽이) — 화염 중급

## 시스템 / 역할
| id | 역할 | 층 | 설명 |
|---|---|---|---|
| `top` | projectile, **loop 0.5 s** | 16 | 높이 1 m·반경 0.58 m 의 불꽃 나선 팽이. P = 팽이 아래 **지면 점**, forward = 이동 방향. 모드가 4초 동안 지면을 따라 움직이고 끝나면 정지(stop). 흔적(그을음·불꽃·연기·불티)은 world 공간 rateOverDistance. |
| `fade` | impact (첫 번째 impact = 종료 시) | 8 | 팽이가 터지며 사라짐. hitPoint = 마지막 지면 점, normal = (0,1,0). |
| `burn` | impact | 5 | 부딪힌 적마다 1회. hitPoint = 적 가슴(발 + 1.0 m), normal = 팽이→적 수평 방향(공중 규칙이면 −incoming 도 무방). |
| `spawn` | impact | 4 | 시전 시 팽이가 나타나는 지면 점, normal = (0,1,0). |

## 시간표
- 0.0: `spawn` + `top` 시작 → 0–4.0 s 돌아다님(회전 2 rps 나선 2겹 역회전 + 껍질 + 상단 링/문양 + 불꽃 왕관 + 튀는 불티) → 4.0: `top` 정지(로컬 층 즉시 제거) + `fade` 재생.
- 루프 0.5 s 동안 메쉬 회전각이 대칭 주기의 정수배라 루프 경계에서 끊김 없음 (보유 메쉬는 burst 1 + rate 1/0.5).

## 피해 권장 시점
- 0–4.0 s 동안 팽이 반경 ≈ 0.7 m 안에 들어온 적에게 화상 피해, 그 순간 `burn` 재생 (같은 적 재타격 쿨다운은 모드 설정).

## 메모
- 미리보기의 접촉 시각은 데모 경로에서 자동 계산(0.9 / 1.9 / 2.87 s).
- client_compat check: OK. 고유 텍스처 RGBA ≈ 1.75 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
