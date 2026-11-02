# RimeGarden (서리 정원) — 물 · 고급

## 시스템 / 역할
- `main` — **static**. P = 지정 지점 바닥, local +Y = 위. 저작 반경 6 m, `scale = {input:"areaRadius", reference:6, min:0.5, max:1.5}`.
- `freeze` — **impact**. 서리 전선에 닿아 얼어붙은 적마다 1개, hitPoint = 적 발 위치, surfaceNormal = (0,1,0). 길이 1.6 s (얼음 결정이 발을 감싸고 1.25 s 에 부서짐). 크기 조정 없음.
- 결정 메쉬는 depthWrite(불투명 면 + 면별 명암을 T_IceShade U 좌표로 구움) + 가산 모서리 선.

## 시간표 (s)
| 시각 | 연출 |
|---|---|
| 0.0 | 육각 서리 마법진·눈꽃 문장 그려짐 |
| 0.4 | 서리 전선(링 + 안개 + 반짝임)이 0 → 6 m 로 2.0 s 동안 퍼짐, 서리 고사리 무늬·얼음 균열이 함께 자람 |
| 0.45 / 0.75 / 1.35 / 1.95 | 결정 무리 솟음: 중심 큰 무리 → 반경 1.6 m(6) → 3.4 m(10) → 5.3 m(14) |
| 0.6–4.0 | 정원 유지: 공중 눈꽃(3.1 m), 회전 눈꽃 6개, 빛기둥, 눈 내림, 냉기 안개, 반짝임 |
| 4.0 | 모든 결정 동시에 부서짐: 섬광, 충격 2겹, 먹빛/빛 얼음 파편, 눈꽃 폭발, 광선 |
| 5.0 | 종료 |

## 피해 권장 시점
- 서리 전선 통과 시 빙결: 반경 d 의 적은 t ≈ 0.4 + 2.0·(d/6) s 에 빙결 + `freeze` 재생 (예: d 2.7 m → 1.3 s).
- 파쇄 피해: **4.0 s** (구역 전체).

## 런타임 입력
- main: P + 수평 방향(무관) + `areaRadius`. freeze: hitPoint / surfaceNormal(위) / incomingDirection(무관).

## 클라이언트 호환
- `client_compat.py fix` 0 change(s), `check` OK, `validate.py` OK. 비지원 필드 없음. depthWrite 사용(클라이언트 지원 목록에 있음).
- 고유 텍스처 RGBA 2.80 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
