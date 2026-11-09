# FreedDemon_Slam — 지연 내려찍기 + 날개 폭풍 + 돌진.

- slam  (static, P = 내려찍는 지점 = 보스 앞 약 3.5 m 지면, 반경 R = 3.5 m): slam 모션 1.12 s(타격)에 시작. 멈칫(0.4 s 지연)은 모션이 담당.
- gust  (static, P = 보스 발밑, +Z 정면): 날개 폭풍 — 정면 부채꼴로 불바람 + 밀쳐내기 (wing_gust 0.72 s 에 시작)
- firewall (static phased 아님, loop): 투기장 외곽 불벽 — P = 투기장 중심, 반경 14 m. 날개 폭풍과 함께 켜고 모드가 정지 신호로 끈다
- dash  (attached, 보스): 돌진 — 몸 뒤로 끌리는 불꽃 잔상 + 지면 불티 (dash 0.2 s 에 켜고 0.75 s 에 정지 신호)

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
