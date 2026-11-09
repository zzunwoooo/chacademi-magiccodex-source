# FreedDemon_Tricks — 속임수 짤패턴.

- taunt    (attached, 보스, loop): 도발 자세 — 붉은 눈빛이 일렁이는 반격 대기 오라. 모드가 정지 신호로 끈다
- counter  (static, P = 보스 발밑, +Z 정면): 도발 중에 때리면 — 반격 폭발 (정면 반원 5 m)
- mirror   (static, P = 분신 위치): 거울 돌진 분신이 사라짐 — 보라 연기·유리 파편 (본체는 그림자 있음, 분신은 이걸로 소멸)
- sulfur   (attached, 보스, loop): 유황 행진 — 이동 경로에 남는 불바다(월드 공간). 모드가 정지 신호로 끈다
- darkness (static, P = 플레이어 발): 어둠의 장막 — 검은 장막이 덮치며 쿵 하는 충격 (실명 자체는 모드의 효과)

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
