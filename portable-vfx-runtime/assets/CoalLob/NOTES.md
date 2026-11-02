# CoalLob (숯불 투척, 화염 초급) — 변환 메모

- 시스템 3개
  - `coal` (projectile, loop, 6 layers): 달군 숯덩이(어두운 몸체 + 빛나는 균열, 회전) + 불꼬리·연기·불티 (world, rateOverDistance). 비행·튕김 궤적은 서버.
  - `bounce` (impact, 1.4 s, 6 layers): **튕길 때마다(1·2번째 바닥 접촉) 접촉점에 1회 재생**, 위 = surfaceNormal. 불티 튐·작은 섬광·고리·재 연기·그을음 자국·숯 부스러기. 숯은 계속 날아감(projectile 멈추지 않음).
  - `blast` (impact, 1.8 s, 11 layers): **3번째 접촉(또는 벽·대상 명중) 시 폭발**. 일반 착탄 규약 (projectile 멈춤 → hitPoint/surfaceNormal, 공중 = −진행방향). 폭발 중심 로컬 +Y 0.6 m.
- 피해 권장: `blast` 시작 시점 (반경 약 1.7 m). 튕김(bounce) 에 약한 피해를 줄지는 서버 선택.
- 데모: flightPath 로 세 번의 포물선을 그리고, 튕김 시점은 미리보기 비행 매개변수로 계산해 hits 에 넣음 (바닥 0.64 / 0.93 s, 폭발 1.15 s).
- 런타임 입력: 없음 (scale 사용 안 함).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
