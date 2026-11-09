# FreedDemon_Signature — 기믹 3 "악마의 서명" (표식 기억 → 구역 폭발).

- mark1~mark6   (static, P = 보스 머리 위 표시 지점): 표식 하나를 보여줌 (약 1.2 s). 모드가 순서대로 5개를 고른다
- mark_reversed (static, 같은 위치): 뒤집힌 표식 = 건너뛰는 순서 — 표식을 거꾸로(180°) 보라색으로, 금이 가며 일그러짐. 모드가 어떤 표식인지 골라 rev1~6 중 하나를 켠다
- mark_fake     (static): 흐릿한 회색 가짜 표식 — 순서에 없음 (fake1~6)
- sector0~7     (static, P = 투기장 중심): 8등분 구역 k(45°, 반경 14 m)가 폭발. 구역 k = 정면(+Z)에서 시계 방향 k·45° (방향을 돌릴 필요 없음, 서버 facing 은 투기장 기준 0으로)
- fizzle0~7     (static, 같은 배치): 뒤집힌 표식 차례 — 구역이 달아올랐다가 폭발하지 않고 꺼짐(속임)

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
