# StarHop (별빛 뛰기) — 변환 메모

- 원소/등급: 빛 · 초급. 손끝에서 튄 작은 별빛이 가까운 적들 사이를 포물선으로 3번 튀며 때린다.
- 시스템:
  - `cast` (attached, 0–0.3 s): 손앞 작은 세로 오각별 마법진 + 빛 모임 + 불꽃.
  - `hop` (projectile, 튈 때마다 새 인스턴스: 시전자 → 적 A → 적 B → 적 C): 8갈래 별 + 청백 별(역회전) + 광채 + 회전 리본 호 + 월드 잔광(줄무늬, 별가루, 먼지, 먹빛 조각).
    한 번 튈 때 약 0.26 s, 두 대상 사이 위로 휘는 경로 권장(미리보기: 중간점 높이 2.5 m 의 2차 베지어). 경로/대상 선택은 모드.
  - `hit` (impact, 적중마다): 섬광 · 코로나 · 메쉬 오각별 문양 + 고리(역회전) · 충격 고리 2겹 · 광선 · 별 조각 · 먹빛 파편 · 먼지.
- 시간표(미리보기): 0 cast, 0.18 첫 발(0.28 s), 0.46 적중 A → 0.5 두 번째(0.26 s) → 0.76 적중 B → 0.8 세 번째 → 1.06 적중 C, 끝 ~1.7 s.
- 피해 권장 시점: 각 `hit` 시작(3회). 다음 튐은 적중 직후 0.04 s 이내 시작 권장.
- 런타임 입력: attached = 발 위치+수평 방향, projectile = 위치+진행 방향, impact = hitPoint/surfaceNormal/incomingDirection(적: 공중 규칙).
- 주요 기능: meshParticle, attachedFollow, rateOverDistance, worldSpace, bloom. scale 없음.
- 층 수 21 (cast 4 / hop 8 / hit 9). 고유 텍스처 RGBA 0.830 MiB (10장).
- client_compat check: OK (fix 변경 0건), validate OK.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
