# MudBomb (진흙 폭탄, 대지 중급) — 변환 메모

- 시스템 2개:
  - `projectile` (projectile, loop, 8 layers): 마력 깃든 진흙 덩이(빛나는 리본 고리가 돌아감). 월드: 진흙 방울 낙하·모래 먼지·금빛 알갱이·결 (rateOverDistance).
  - `impact` (impact, 3.1 s, 19 layers): hitPoint, +Y = surfaceNormal (공중 = -incomingDirection). 연기·먼지는 world 위쪽으로.
- 시간표 (impact 기준 s):
  - **0.0 폭발 (피해 권장 시점)**: 섬광, 충격 고리, 진흙 덩이·방울·왕관 튐(중력), 금빛 모래 폭발·광선, 어두운 먼지, 흙 조각(메쉬)
  - 0.05~2.9 **둔화 장판 (반경 약 2 m)**: 진흙 웅덩이 데칼 + 작은 룬 고리·시길, 반짝임, 진흙 거품이 부풀고 터짐, 느린 금빛 입자 → 둔화 판정 구간 권장 0.05~2.6
  - 2.6~3.1 사라짐
- 흙 조각은 `depthWrite` 재질. 클라이언트 비지원 필드 사용 안 함 (client_compat check OK).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
