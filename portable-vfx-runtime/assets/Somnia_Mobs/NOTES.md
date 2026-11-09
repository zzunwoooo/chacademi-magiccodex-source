# Somnia_Mobs — 솜니아 레이드 잡몹 공격 VFX

- 빨간 예고는 기존 예고 시스템. 크기 고정(1블록 = 1 m). P = 몹 발밑, +Z = 몹 정면.

| 시스템 | role | 타이밍 | 쓰임 |
|---|---|---|---|
| moth_hit | static | 0 s 타격 | 졸음 나방 들이받기 (정면 0.8 m) |
| walker_swing | static | 0 s 타격 | 몽유 거인 큰 손 휘두르기 (앞 반원 r4.5) |
| needle / needle_hit | projectile / impact | 비행 / 착탄 | 악몽 인형 바늘 (3번 재생) |


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
