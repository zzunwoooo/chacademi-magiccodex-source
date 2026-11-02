# ShadowDoppel (그림자 도플갱어) — 어둠 비전, 변환 메모

- 시스템 2개:
  - `doppel` (static, 4.2 s, loop 없음, 29 layers): P = **적 발 위치 + 시전자 수평 방향 × 1.2 m** (적의 등 뒤 바닥), +Z = 시전자 수평 방향 (적은 local −Z 쪽). 시전 시 1번 시작.
    - 0.0 먹빛 웅덩이(반경 1.3 m 진홍 마법진) · 0.15~0.45 키 2.2 m 그림자(먹빛 몸, 보라 테두리, 진홍 눈)가 솟아오름 · **0.45 등장 섬광 = 따라하기 시작 권장**
    - 0.45~3.45 3 s 유지: 깜빡이는 잔상, 어둠·핏빛 안개, 적 쪽으로 뻗는 바닥 촉수, 별빛 · 3.45 먹으로 무너져 사라짐 (약 4.2 s + 수명)
  - `mimic` (impact, 16 layers): **시전자가 공격할 때마다 1회** 재생 (유지 3 s 동안). hitPoint = 적 등/가슴 높이, incomingDirection = 그림자 → 적 방향, surfaceNormal 없음(공중 판정 = −incoming). 먹빛+진홍+빛 베기 호 4겹, 섬광, 적 쪽으로 튀는 불꽃·먹 조각·핏빛 안개. 피해 = 재생 시점 (약 0.3 s 연출).
- 런타임 입력: 없음 (스케일 고정; 미리보기는 1.0/1.7/2.35/3.05 s 에 mimic 4번 재생).
- 모든 항목이 클라이언트 지원 기능 안 (client_compat check OK, fix 0 changes). 고유 텍스처 RGBA 2.24 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
