# StarLink (별 잇기) — 빛 중급, 변환 메모

- 시스템 3개
  - `star` (static, 3.8 s): 고른 두 지점 A·B 에 **각각 1개씩**, 둘 다 t=0 에 시작. P = 그 지점 바닥. 별은 1.2 m 높이, 바닥에 작은 마법진.
  - `line` (link, 2.9 s + 잔상): **t=0.35 에 시작**. source = A + (0, 1.2, 0), target = B + (0, 1.2, 0) (매 프레임 갱신 가능). 0.25 s 에 선이 그어지고 유지, 3.23 s(전체 기준)에 가운데서 끊겨 양쪽 별로 말려 들어감.
  - `hit` (impact, 0.3 s): 선에 걸린 적마다 1개, hitPoint = 선 위 접점, 공중 판정(surfaceNormal = −incomingDirection).
- 시간표 (전체 기준 s): 0.0 별 2개 떨어져 섬 · 0.35~0.6 선 그어짐 · **0.6~3.2 선 유지 (피해 판정 구간)** · 3.23 선 끊김 · 3.4 두 별이 터지며 사라짐 · 3.8 끝.
- 피해 판정 권장: 0.6~3.2 s 동안 선에 닿은 적 (적마다 0.5 s 간격 등), 맞을 때 `hit` 재생.
- 런타임 입력: static 기본(P), link 기본(source/target 매 프레임), impact 기본. scale 없음 (선 길이는 link 길이로 자동).
- 참고: 비루프 시스템의 rate 방출은 미리보기에서 duration 뒤에도 계속되는 문제가 있어, 모든 연속 방출을 burst 반복(cycles·interval)으로 적음 → 끝나는 시점이 파일에 명시됨.
- client_compat check OK (fix 0 change). 고유 텍스처 RGBA 1.71 MiB.
