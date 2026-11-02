# ShearCurtain (칼바람 장막) — 바람 고급, 변환 메모

- 시스템 3개
  - `cast` (attached, 0.9 s): 시전자 발밑 바람 마법진(반경 1.7 m) + 모이는 바람 + 정면 세로 틈(0.38~0.8 s). **0.72 s 방출 섬광**.
  - `wall` (projectile, loop, 1.0 s 주기): 바람 장막. **P = 장막 중심 = 시전자 발 + 1.6 m 위, 정면 0.9 m 에서 시작**, 수평 진행. 로컬 XY 평면에 폭 5 m(x ±2.5) × 높이 3.4 m(y −1.6~+1.8). 0.72 s 에 발사.
    권장 속도 약 5 m/s, 사거리 약 10~11 m (미리보기 2.2 s). 적을 관통하며 지나간다 (적에 맞아도 멈추지 않음 → 사거리 끝 또는 블록 충돌에서만 hit 처리).
  - `end` (impact, 0.5 s + 잔상 ~1.3 s): 사거리 끝(공중 판정, normal = −진행방향) 또는 블록 충돌 지점에서 장막이 칼날로 흩어짐. hitPoint = 장막 중심 높이.
- 시간표: 0~0.72 시전 → 0.72 발사 → (0.72~2.9) 장막 전진 → 사거리 끝/벽 → end 0~1.3 s.
- 피해 권장: 장막이 지나가는 적에게 1회(관통 판정, 장막 폭 5 m·높이 3.4 m), end 시점 반경 2 m 추가 1회(선택).
- 런타임 입력: cast = 시전자 발 위치·수평 방향(매 프레임), wall = 투사체 위치·진행 방향(매 틱), end = hitPoint/surfaceNormal/incomingDirection. scale 없음.
- end 의 층은 회전 대칭(고리·구형 파편)으로만 만들었음: 공중/벽 충돌 시 impact 의 forward 가 정해지지 않으므로 방향에 의존하지 않게 함.
- 장막의 바닥 긁힘·먼지·잎·티끌은 world 공간(rateOverDistance) 이라 충돌 후에도 자연스럽게 사라짐.
- client_compat check OK (fix: 0 change, 시각 변화 없음). 고유 텍스처 RGBA 3.4 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
