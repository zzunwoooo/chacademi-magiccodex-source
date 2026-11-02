# GemSplinter (보석 파편) — 변환 메모

- 시스템 5개:
  - `cast` (attached, 0.5 s): 시전 순간 1회. 손에서 금빛 섬광 + 세 색(황옥·석류석·에메랄드) 부채꼴 불꽃.
  - `gemTopaz` / `gemGarnet` / `gemEmerald` (projectile, 반복): 보석 1발 = 인스턴스 1개, **세 발을 동시에** 손에서 부채꼴(−15° / 0° / +15°, 어느 색이 어느 방향인지는 자유)로 쏜다. 진행 방향 +Z 로 누운 십자 메쉬 `gem`(면 음영 회색 텍스처 1장을 재질 tint 로 색만 바꿈) + 같은 색 반짝임·꼬리.
  - `impact` (impact, 1.0 s, 세 보석 공용): hitPoint, up = surfaceNormal (적/공중 = −진행방향). 섬광 + 세 색 보석 조각 + 무지개 반짝임 + 충격파.
- 피해 권장 시점: 각 보석의 impact 시작(t=0).
- 런타임 입력: projectile 위치·방향, impact hitPoint/normal/incoming. scale 없음.
- 어두운 강조: 보석 몸체·조각은 알파 블렌드(어두운 면 + 밝은 모서리, HDR tint 로 색).
- client_compat check: OK (FIX/WARN 없음). 고유 텍스처 RGBA 0.46 MiB (7장, 전용 T_Gem 160×56 / T_Splinter 48).
- 층 33개 (cast 6 + 보석 3종 × 6 + impact 9) — 같은 구성의 보석 3색이라 실제 종류는 21층 수준.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
