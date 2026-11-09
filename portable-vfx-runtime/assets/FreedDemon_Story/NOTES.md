# FreedDemon_Story — 등장·사망 연출.

- spawn      (static, P = 보스 발밑, 약 4 s): 봉인진 위에서 사슬에 묶여 웅크린 악마 → 2.0 s 족쇄가 끊어지며 폭발 → 포효 충격파 (보스 spawn 모션 4 s 와 같이 시작)
- fake_death (static, P = 보스 발밑, 약 3.2 s): 가짜 사망 — 쓰러지며 검은 연기가 터지고, 금빛 "클리어" 섬광이 속인다. 2.6 s 에 바닥으로 그림자가 새어 나감
- shadow     (attached, 도망치는 그림자, loop): 그림자 몸에서 피어오르는 검은 연기 + 붉은 눈빛 꼬리. 모드가 정지 신호로 끈다
- seal       (static, P = 보스 발밑, 약 4.2 s): 진짜 사망 — 다시 봉인됨. 바닥에서 사슬이 솟아 감고, 봉인진이 닫히며 바닥으로 가라앉음 (보스 death 모션과 같이)
- mimic_bite (static, P = 미믹 상자 앞): 가짜 보상 상자를 열었을 때 — 덥석 + 해골 웃음 + 즉사 폭발

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
