# StoneTop (바위 팽이, 대지 중급) — 변환 메모

- 시스템 4개 (모드가 각각 시작):
  - `top` (projectile, loop, 11 layers): 돌 팽이 본체(깊이 쓰기 메쉬, 높이 1.15 m, 22 rad/s 회전, 금빛 룬 띠) + 회전 리본·궤도 결·끝 빛.
    모드가 4 s 동안 바닥을 따라 움직임 (+Z = 진행 방향, 위치 = 팽이 끝이 닿는 바닥). 월드: 홈 균열·어두운 자국·갈리는 불꽃·먼지·모래 튐 (rateOverDistance).
  - `form` (impact, 0.7 s): 시전 시 생성 지점에서 1번 (선택). 
  - `clash` (impact, 0.7 s): **적과 부딪힐 때마다 1개** (피해·튕기기 권장 시점 = 시작 0.0). hitPoint = 적 표면, normal = 적 → 팽이 방향(공중 규칙).
  - `crumble` (impact, 1.4 s): 4 s 끝(또는 벽 충돌)에 팽이 위치에서 — 바위 조각(메쉬)·모래 폭발·충격 고리로 부서짐. 바닥 normal [0,1,0].
- 미리보기의 충돌 시각은 경로 위 대략적인 시점이며 실제는 모드 판정 시점.
- 클라이언트 비지원 필드(rollWithTravel 등) 사용 안 함: 팽이는 제자리 회전(rotationOverLifetime)만 함. client_compat check OK.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
