# ThousandCranes (천 마리 종이학) — 바람 고급, 변환 메모

- 시스템 2개
  - `chant` (attached, 3.1 s + 잔상): 시전자 발밑 바람 마법진(반경 2.2 m) + 점점 늘어나는 종이학(0.25 / 1.0 / 1.8 / 2.3 s 단계) + 나선 띠. **3.0 s 종이학 떼 이륙**(정면·위로, world 공간).
  - `swarm` (static, 2.2 s + 잔상 ~1.5 s): **대상 발 위치에 3.0 s 에 시작**. 방향 = 시전자 수평 방향(+Z), 종이학 떼가 시전자 쪽(−Z, 위)에서 날아 들어온다.
    대상 둘레 소용돌이(종이학 3층, 서로 반대 회전) + 연타 8회 + 마무리 폭발.
- 시간표 (시전 기준 s): 0~3.0 영창 → 3.0 이륙 / swarm 시작 → 3.25~4.93 연타 (0.24 s 간격 8회) → 5.1 마무리 폭발 → ~6.5 종료.
- 피해 권장: swarm 기준 0.25 + k·0.24 s (k = 0..7) 각 1회, 2.1 s 마무리 1회 (= 시전 기준 3.25 … 4.93, 5.1).
  영창이 끊기면 chant 를 정지(stop) 하고 swarm 을 시작하지 않는다.
- 미리보기: static 은 world +Z 기준으로 그려지므로 시전자를 대상의 −Z 쪽에 세워 둠 (demo 전용).
- 런타임 입력: chant = 시전자 발 위치·수평 방향(매 프레임). swarm = 대상 발 위치 1회 + 시전자 수평 방향(정적 기준). 대상이 움직이면 swarm 은 시작 위치에 남는다 (attached 로 대상에 붙이고 싶으면 role 만 attached 로 바꿔도 같은 모양).
- 종이학은 stretched billboard(ls 1, 이동 방향으로 머리) + 2칸 날갯짓 플립북(cycles).
- client_compat check OK (fix: 0 change). 고유 텍스처 RGBA 약 2.4 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
