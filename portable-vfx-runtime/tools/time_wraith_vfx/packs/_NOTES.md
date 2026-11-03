# 시간의 망령 (3인 보스) VFX — 변환·서버 연동 메모

모든 팩은 PortableVFX 클라이언트 호환 범위 안 (client_compat check OK). 빠진 기능·대체한 기능 없음.
좌표: static 의 원점 = 서버가 지정한 바닥 위치, +Z = 서버가 넘긴 yaw 정면 (직선은 원점에서 +Z 로 뻗음, 부채꼴은 +Z 중심).
**빨간 예고는 VFX 에 없다** — 모드 예고(Telegraphs)가 그린다. 서버는 판정 순간(발동)에 static 을 재생한다 (TW_Sand/fall 만 발동 0.25초 전).
크기: `scale.input: areaRadius`, reference = 팩 기준. 서버는 기준과 같은 크기로 부름. 계약서: `portable-vfx-runtime/tools/time_wraith_vfx/CONTRACT.md`.
