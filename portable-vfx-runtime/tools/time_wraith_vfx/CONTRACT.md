# 시간의 망령 (3인 보스) — VFX 계약서 (2026-11-03)

서버 코드(`magic-mythic-paper/.../TimeWraithBoss.java`)가 이 id 그대로 부른다: `claude:<팩 이름 소문자>/<시스템 id>` (예: `claude:tw_clock/tick`).
**VFX 에 빨간 예고 넣지 않는다** — 원형·직선·부채꼴 예고는 모드(Telegraphs)가 그림. 재의 그림자·하베스트와 같은 도구(lib.js + timelib.js, build_t.sh).

공통
- 좌표: static 원점 = 지정 위치 바닥, +Z = 서버가 넘긴 yaw 정면. 직선은 원점에서 +Z 로 length 만큼, 부채꼴은 +Z 중심.
- 크기: `scale.input: areaRadius`, reference = 팩 기준. 서버는 기준 크기로 부름(배율 1).
- 색: 청록 #7ff3ff (시간), 금빛 #ffc260 (모래·황동), 남색 그림자. 텍스처는 흰~회색 + 머티리얼 tint.
- 팩당 고유 RGBA ≤ 4 MiB (최대 TW_Clock 3.39 MiB). client_compat check OK. 판정 순간 재생 (TW_Sand/fall 만 0.25초 전).
- 만들기: `python3 gen_time.py tex_t` → `./build_t.sh TW_<이름>` (툴킷 ~/vfxA/vfx_toolkit) → `fin_t/TW_<이름>/` → `assets/` 에 vfx.json·textures·NOTES.md 복사.

| 팩 / 시스템 | 역할 | 기준 | 길이 | 서버가 부르는 곳 |
|---|---|---|---|---|
| TW_Slash / swipe | static 부채꼴 | r 6, 150° | 1.0 | 기본 공격 (보스 발밑·대상 방향) |
| TW_Slash / second_a · second_b | static 부채꼴 | r 8, 160° | 1.0 | 초침 2연타 1타(오른→왼)·2타(왼→오른) |
| TW_Thrust / windup · thrust | static · 직선 | r 2.5 · 길이 16 | 0.8 · 1.3 | 시침 찌르기 예고 시작 · 발동 |
| TW_Sand / cast · fall | static | r 3 · 원 r 4 | 1.8 · 1.1 | 모래 낙하 시전 · 각 낙하 0.25초 전 |
| TW_Pendulum / inner · outer | static 원 · 도넛 | r 6 · 6~12 (기준 12) | 1.1 · 1.2 | 진자 안쪽 · 바깥 고리 (둘 다 배율 1) |
| TW_Stasis / orb · foresight | static 원 | r 3 · r 4.5 | 1.3 · 1.1 | 정지 구슬 · 앞당긴 미래 |
| TW_Prison / cast | static 보스 | r 2 | 1.0 | 감옥 시전 |
| TW_Prison / trap | static 감옥 자리 | r 2.5 | 1.4 | 가둠 (모래시계가 내려옴) |
| TW_Prison / hold | attached 감옥 | — | 루프 2 | 감옥 유지 (모래·룬 고리), 21초 |
| TW_Prison / break · fail | static | r 3 · r 2 | 1.4 · 1.6 | 감옥 파괴 성공 · 모래 다 떨어짐(사망) |
| TW_Clock / summon | static 시계 중심 | r 7 | 2.0 | 바닥 시계 펼침 |
| TW_Clock / tick · deny | static 바늘 끝/꼬리 | r 1 | 0.5 | 맞아서 한 칸 · 틀린 원소 |
| TW_Clock / success · fail | static 시계 중심 | r 7 | 1.8 · 1.6 | 맞춤 · 시간 초과(전멸) |
| TW_WorldStop / start | static 보스 | r 4 | 1.6 | 라운드 시작 (손가락 튕김) |
| TW_WorldStop / freeze · release | static 중심 | r 18 | 1.6 · 1.0 | 시간 정지 · 풀림 |
| TW_WorldStop / origin | static 경기장 가장자리 | r 2 | 2.6 | 탄환 방향 표시 (예고 동안) |
| TW_WorldStop / shield | attached 방패 플레이어 | — | 루프 2 | 방패 표식 |
| TW_WorldStop / bullet | projectile (서버가 매 틱 이동) | 길이 약 1.6 | ≤ 60 | 시곗바늘 탄환 (정지 중엔 제자리) |
| TW_WorldStop / block · hit | static | r 1 | 0.5 · 0.6 | 방패가 막음 · 맞음 |
| TW_Fate / intro · death | static 보스 | r 6 | 3.0 | 등장 · 처치 |
| TW_Fate / vanish · appear | static | r 3 | 0.8 | 기믹 귀환 |
| TW_Fate / groggy · stun | static · attached | r 4 | 1.3 · 루프 2 | 그로기 추락 · 유지 |
| TW_Fate / fakeclear · rewind | static 보스 | r 6 · (r 8 그림) | 2.6 · 2.6 | 가짜 클리어 · 되감기 부활 |
| TW_Fate / phase2 | attached 보스 | — | 루프 2 | 2페이즈 오라 (서버가 55초마다 다시 붙임) |
| TW_Fate / echo_hit | static 잔상 위치 | r 1.2 | 0.7 | 2페이즈 잔상이 맞음 |
