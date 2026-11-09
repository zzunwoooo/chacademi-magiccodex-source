# Roulette_Mobs — 룰레타 레이드 잡몹 공격 VFX

- 빨간 예고는 기존 예고 시스템. 크기 고정(1블록 = 1 m). P = 몹 발밑, +Z = 몹 정면.

| 시스템 | role | 타이밍 | 쓰임 |
|---|---|---|---|
| imp_hit | static | 0 s 타격 | 칩 임프 굴러 들이받기 (정면 0.9 m) |
| imp_pop | static | 0.1 s 폭발 | 칩 임프 사망 동전 폭발 r2 |
| slash_a / slash_b / slash_c | static | 0 s 타격 | 카드 병정 3연 베기 (부채꼴 120° r4: 오른→왼, 왼→오른, 내려베기) |
| dice / dice_hit | projectile / impact | 비행 / 착탄 0.08 s | 주사위 술사 투척, 착탄 r2.5 |


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
