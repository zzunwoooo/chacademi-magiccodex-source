# FreedDemon_Contract — 계약·낙인·처형 계열.

- soulchain   (link, 반복): 영혼 사슬 — source = 보스 오른손(모드가 손 위치), target = 끌려오는 플레이어 가슴. 모드가 정지 신호로 끈다
- brand_red   (attached, 플레이어): 붉은 계약의 낙인 — 머리 위 낙인 + 발밑 고리. 3초 뒤 그 자리에서 폭발(=brand_burst). "벗어나라"
- brand_violet(attached, 플레이어): 보라 계약의 낙인 — "제자리에 버텨라". 색만 다름(악마가 채팅으로 반대로 말함)
- brand_stack (attached, 반복): 낙인 중첩 표시(줄넘기 피격·집행자 공격) — 가슴의 작은 불타는 낙인. 모드가 정지 신호로 끈다
- brand_burst (attached, 플레이어): 낙인 폭발 / 2중첩 즉사
- execution   (static, P = 대상 플레이어 발, +Z = 보스가 찌르는 방향): 처형 3연 찌르기 — 순간이동 연기 + 찌르기 섬광 3번

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
