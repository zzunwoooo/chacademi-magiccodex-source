# FreedDemon_Hellfire — 지면 분출형 짤패턴. 빨간 예고 장판은 모드 예고 시스템(여기 없음), 모두 예고가 끝나는 순간(판정)에 시작.

- cross  (static, P = 보스 발밑, +Z 정면): 지옥불 십자 — 길이 24 m(±12) × 폭 2.4 m 두 줄. X자로 꺾는 건 모드가 시작 방향을 45° 돌려서 처리
- ring   (static, P = 보스 발밑): 원 — 반경 5 m 안쪽 전체 분출
- donut  (static, P = 보스 발밑): 도넛 — 반경 5~12 m 고리 분출 (안쪽 안전)
- cell   (static, P = 칸 중심): 가짜 안전지대 / 혜성 잔불 등에 쓰는 작은 분출 1칸 (반경 2 m, areaRadius 로 크기)
- hand   (static, P = 손이 솟는 지점): 지옥의 손 — 바닥이 갈라지며 악마 손이 솟아 움켜쥠

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
