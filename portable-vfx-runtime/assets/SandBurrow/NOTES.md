# SandBurrow (모래 두더지, 대지 중급) — 변환 메모

- 시스템 2개:
  - `burrow` (projectile, loop, 12 layers): 땅속 모래 줄기의 머리. 모드가 시전자 발밑 → 대상 발밑까지 바닥을 따라 이동시킴 (+Z = 진행 방향, 위치 y = 바닥 높이).
    로컬: 모래 둔덕(깊이 쓰기 메쉬)·머리 빛·모래 퍼프·소용돌이 리본·조약돌. 월드(지나간 자리에 남음, rateOverDistance): 금빛 균열·어두운 땅·모래 튐·먼지·능선 결.
  - `bind` (impact, 2.2 s, 18 layers): 도착 지점(hitPoint, +Y = surfaceNormal)에서 재생. 공중 도착이면 -incomingDirection 기준.
- 시간표 (bind 기준 s):
  - **0.0 분출 + 속박 시작 (피해 권장 시점)**: 섬광·충격 고리·모래 분출, 바닥 속박 마법진(반경 1.3 m, 나선 시길)
  - 0.05~1.6 속박 유지: 발목 모래 둔덕 + 다리를 감는 모래 나선 3개(서로 반대로 회전) + 감싸는 모래 퍼프 → **속박 시간 1.6 s 기준**
  - 1.55 부서짐: 모래 퍼프·알갱이·바위 조각(메쉬)이 흩어짐 · ~2.2 종료
- 속박 시간이 다르면 모드가 bind 를 그 시점까지 유지할 수 없으므로(고정 길이), 다른 길이가 필요하면 알려 주세요.
- 바위·둔덕은 `depthWrite` 재질. 클라이언트 비지원 필드 사용 안 함 (client_compat check OK).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
