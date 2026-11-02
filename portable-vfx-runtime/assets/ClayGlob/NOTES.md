# ClayGlob (점토 덩이) — 변환 메모

- 시스템 3개:
  - `projectile` (projectile, 반복): 던진 점토 덩이 1개 = 인스턴스 1개. 손에서 포물선(던지기) 궤적은 모드가 움직인다. 반질한 점토 몸체 + 금빛 회전 호 + 흘러내리는 점토 방울·모래 꼬리.
  - `impact` (impact, 1.0 s): hitPoint, up = surfaceNormal (적/공중 = −진행방향). 점토가 철퍽 퍼져 **표면에 붙는 데칼**(약 0.9 s 유지) + 금빛 섬광·점토 조각·물보라 고리·충격파.
  - `slowed` (attached, loop 1.0 s): **맞은 적의 발 위치 + 방향**에 붙인다(FlameLash 의 wrap 과 같은 방식). 명중 순간 시작, 둔화가 끝날 때 stop 신호 → `onStop` 꼬리(0.2–0.25 s)로 사라짐. 발목 점토 웅덩이·느린 룬 고리·떨어지는 점토 방울·아래로 가라앉는 금빛 입자.
- 피해 권장 시점: impact 시작(t=0). 둔화는 같은 순간부터 모드 설정 시간 동안.
- 런타임 입력: projectile 위치·방향, impact hitPoint/normal/incoming, slowed = 대상 엔티티 발 위치 + 수평 방향. scale 없음.
- 어두운 강조: 점토 몸체·데칼(어두운 적갈 + 밝은 호박 테두리, 알파), 어두운 먼지(`dust`).
- client_compat check: OK (FIX/WARN 없음). 고유 텍스처 RGBA 1.46 MiB (12장, 전용 T_ClayOrb 128 / T_ClaySplat 256).
- 층 22개 (projectile 7 + impact 9 + slowed 6).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
