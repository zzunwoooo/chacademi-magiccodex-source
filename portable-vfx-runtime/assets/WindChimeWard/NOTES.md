# WindChimeWard (풍경 결계) — 바람 비전, 변환 메모

- 시스템 3개
  - `ward` (static, 8.0 s): P = 결계 중심 바닥. 바닥 바람 마법진(반경 3.5 m) + 풍경 4개((±2, ±2) m, 몸통 중심 높이 2.2 m, 위 2.95 m 에 작은 바람 인장) + 풍경을 잇는 바람 실(반경 2.83 m). 0.6 s 에 풍경이 걸림, 7.5 s 부터 사라짐.
  - `ring` (static, 0.5 s + 잔상 ~0.8 s): **P = 울린 풍경의 몸통 중심 = 풍경 위치 + (0, 2.2, 0)**. 풍경 섬광·소리 고리(빌보드 4겹 + 바닥 고리 + 기울어진 고리)·음표 반짝이·작은 칼바람.
  - `chain` (link, 0.3 s): source = 울린 풍경 중심, target = 다음 풍경 중심. 소리 펄스가 0.25 s 동안 다음 풍경으로 달려감 (pathFollow).
- 연쇄 규칙(모드): 적이 풍경 반경 약 1.5 m 안을 지나가면 그 풍경의 `ring` 을 시작하고 동시에 다음 풍경(시계 반대 순서 0→1→2→3)으로 `chain` 을 시작, **0.25 s 뒤 다음 풍경의 `ring`** … 4개를 한 바퀴(마지막 풍경에서 chain 없음). 같은 연쇄 중 다시 밟히면 무시 권장.
- 피해 권장: 각 `ring` 시작 시 그 풍경 반경 2.5 m 안의 적에게 1회 (연쇄 1번에 최대 4회, 0.25 s 간격).
- 런타임 입력: ward = 중심 1회, ring = 풍경 중심 1회, chain = 두 풍경 중심(고정). scale 없음.
- 미리보기: 적 두 명이 결계를 가로질러 1.5 s, 4.1 s 에 각각 연쇄를 일으킨다 (demo events).
- client_compat check OK (fix: 0 change). 고유 텍스처 RGBA 약 2.5 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
