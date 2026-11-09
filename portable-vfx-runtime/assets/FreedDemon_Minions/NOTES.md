# FreedDemon_Minions — 잡몹 3종 연출.

- imp_spawn     (static, P = 임프 발): 속삭이는 임프 등장 — 보라 연기에서 튀어나옴
- imp_shove     (static, P = 임프 발, +Z = 임프 정면): 양손 밀치기 충격파 (임프 attack 0.38 s 에 시작)
- imp_death     (static): 임프 소멸 — 보라 연기로 흩어짐
- enforcer_smash(static, P = 낙인 인두가 찍힌 지면): 계약의 집행자 내리찍기 — 지면에 계약 낙인이 타오름 (집행자 attack 1.0 s)
- grail_heal    (static, P = 진짜 성배 발밑): 진짜 성배 — 금빛 액체가 쏟아지며 회복 고리 (pour 와 함께, 약 2 s)
- grail_burst   (static, P = 거짓 성배 중심): 거짓 성배 — 부풀다(모션) 3 s 째 붉은 폭발 (attack 3.0 s 에 시작)

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
