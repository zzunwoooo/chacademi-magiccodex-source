# 재의 그림자 보스 VFX (그룹 A) — 변환·서버 연동 메모

모든 팩은 PortableVFX 클라이언트 호환 범위 안 (client_compat check OK). 빠진 기능·대체한 기능 없음.
좌표: static 의 원점 = 서버가 지정한 바닥 위치, +Z = 서버가 넘긴 yaw 정면 (직선은 원점에서 +Z 로 뻗음, 부채꼴은 +Z 중심).
**빨간 예고는 VFX 에 없다** — 모드 예고(chatelegraph / Telegraphs)가 그린다. 서버는 판정 순간(발동)에 static 을 재생한다 (AS_AshRain/fall 만 발동 0.25초 전).
크기: 팩마다 `scale.input: areaRadius`, reference = 아래 '기준'. 서버가 기준과 같은 크기로 부르면 배율 1. 한 팩에 크기가 다른 시스템이 둘 있으면 둘 다 배율 1(기본 재생)로 부를 것.
계약서: `portable-vfx-runtime/tools/ash_shadow_vfx/CONTRACT.md`.

| 팩 | 시스템 (static) | 기준 | 재생 위치·시점 |
|---|---|---|---|
| AS_ClawSwipe | swipe (1.0초) | 부채꼴 150° r 6 | 보스 발밑·정면, 발동 순간 |
| AS_AshDash | windup (0.8) · dash (1.3) | 직선 길이 18 | windup = 예고 시작 때 보스 발밑 · dash = 발동 순간 출발점, +Z 로 18 |
| AS_HandSlam | slam (1.4) | 직선 길이 14 | 보스 발밑·내려찍는 방향, 매 타 발동 순간 (손 낙하 0~0.1초 포함, 손자국 중심 z 9.5) |
| AS_AshRain | cast (2.3) · fall (1.1) | 원 r 4 | cast = 보스 발밑 시전 시작 (튕김 0.6/0.9/1.2/1.5초) · fall = 발동 0.25초 전 대상 위치 |
| AS_ShadowSink | sink (1.1) · emerge (1.5) | 원 r 5 | sink = 보스 발밑 · emerge = 발동 순간 (0.4초 바깥 링 r 8) |
| AS_Grasp | grasp (1.3) | 원 r 6 | 발동 순간 대상 위치, 0.35초에 움켜쥠 |
| AS_Afterimage | flicker (0.7) | r 2 | 보스 발밑 |
| AS_AshVeil | wrap (1.3) · burst (1.1) | 원 r 7 | wrap = 보스 발밑 · burst = 발동 순간 (두 번) |
| AS_CremationRing | inner (1.1) · outer (1.2) | 도넛 밖 12 (inner 는 r 5 로 제작) | 둘 다 보스 발밑, 배율 1 로 재생 (areaRadius 를 넘기려면 12) |
