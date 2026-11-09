# FreedDemon_Comet — 지옥 혜성(추적 메테오 3연). projectile → impact. 3발째는 모드가 impact_big 을 고른다(범위 2배).

- projectile : 불타는 검은 운석 — 흑요석 덩어리 + 불꽃 껍질 + 연기·불티 꼬리
- impact     : 착탄 (반경 2.5 m) — 바닥/벽/공중
- impact_big : 3발째 착탄 (반경 5 m)

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
