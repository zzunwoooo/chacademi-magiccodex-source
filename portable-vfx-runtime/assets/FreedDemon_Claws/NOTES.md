# FreedDemon_Claws — 해방된 악마 근접 짤패턴. static, P = 보스 발밑, 정면(+Z) = 보스가 보는 방향. 보스 키 6 m.

- claw     : 기본/1타 할퀴기 — 오른손 대각선(오른쪽 위 → 왼쪽 아래) 발톱 자국 4줄 (attack 0.55 s / claw_combo 0.42 s 에 시작)
- claw_rev : 쌍갈래 2타 — 반대 손·반대 각도 (claw_combo 0.9 s 에 시작). 1타와 각도가 달라서 같은 방향으로 피하면 맞는다
- frenzy   : 광란 5연격의 마지막 광역 — 보스 주위 반경 5 m 를 지옥불로 쓸어 버림 (마지막 타격 순간 시작)

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
