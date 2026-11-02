# SandFall (모래 폭포) — 대지 고급

## 시스템 / 역할
- `main` (static, 3.4 s). P = 지정 지점(바닥). 기준 방향 무관.
- SCENE.scale `areaRadius` (reference 2 m, 배율 min 0.6, max 2 → 반경 1.2–4 m). 높이(하늘 봉인 6 m)도 같은 배율.

## 시간표
| 시각 (s) | 연출 |
|---|---|
| 0.0 | 바닥 표적 마법진(반경 2 m), 0.15 s 6 m 위 하늘 봉인 마법진 |
| 0.45 | 봉인 열림 섬광 |
| 0.5–2.5 | 모래 폭포 2 s: 모래 기둥 3겹(회전), 떨어지는 덩어리·줄기·알갱이, 바닥 물보라·어두운 먼지·먼지 고리(0.38 s 간격), 모래 더미가 쌓임 |
| 1.95 | 마지막 큰 덩어리 쏟아짐 |
| 2.5 | 마무리 충격: 섬광, 모래 폭발, 충격파, 먼지 고리, 돌, 균열 |
| 3.4 | 종료 |

## 피해 권장 시점
- 0.5–2.5 s 동안 지속 피해(예: 0.25 s 간격), 2.5 s 마무리 피해.

## 런타임 입력
- static: P + areaRadius.

## 클라이언트 호환
- `client_compat.py check` OK. 고유 텍스처 RGBA 3.17 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
