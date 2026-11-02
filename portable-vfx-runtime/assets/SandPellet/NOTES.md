# SandPellet (모래 알갱이) — 변환 메모

- 시스템 3개:
  - `cast` (attached, 0.7 s): 시전 시작에 1회. 손(발 기준 +0.32 오른쪽, 1.38 위, 0.55 앞)에서 모래 모임 + 발사마다 총구 섬광(0.00 / 0.15 / 0.30 s).
  - `projectile` (projectile, 반복): **알갱이 1발 = 인스턴스 1개**. 손 위치에서 0.15 s 간격으로 3번 발사(shot k = 0.15·k s). 단단한 모래알 핵 + 회전 호 + 모래 꼬리(rateOverDistance, world).
  - `impact` (impact, 1.0 s): 맞은 지점 hitPoint, up = surfaceNormal (공중 = −진행방향). 섬광·모래 폭발·조약돌·바닥 파동·작은 균열.
- 피해 권장 시점: 각 알갱이가 닿는 순간(impact 시작 t=0).
- 런타임 입력: attached 표준, projectile 위치·진행방향, impact hitPoint/normal/incoming. scale 없음.
- 어두운 강조: `dust`(어두운 모래 먼지, 알파), `rock`(어두운 돌 몸체 + 밝은 테두리), `crack`.
- client_compat check: OK (FIX/WARN 없음). 고유 텍스처 RGBA 1.47 MiB (10장).
- 층 25개 (cast 7 + projectile 8 + impact 10).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
