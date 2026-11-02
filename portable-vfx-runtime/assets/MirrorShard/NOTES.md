# MirrorShard (거울 조각) — 변환 메모

- 원소/등급: 빛 · 초급. 회전하는 거울 조각탄. 처음 벽에 맞으면 벽에 거울 문양이 번쩍이며 한 번 반사되어 계속 날아가고, 다음 적중에서 산산이 부서진다.
- 시스템:
  - `cast` (attached, 0–0.3 s): 손앞 작은 세로 마법진 + 거울 조각 형성 + 반짝임.
  - `shard` (projectile): 거울 조각(알파, HDR 테두리) 회전 + 후광 + 청백 반짝임 + 월드 잔광(줄무늬, 작은 거울 조각, 먼지, 먹빛 조각).
  - `reflect` (impact, 첫 벽 적중): 청백 섬광 · 육각 플레어 · 벽면 메쉬 거울 문양(고리+육각별, 역회전) · 물결 고리 2겹 · 반짝임 · 거울 부스러기 · 불티.
  - `impact` (impact, 마지막 적중: 적/두 번째 벽/바닥): 섬광 · 코로나 · 충격 고리 · 거울 파편 폭발 · 빛 파편 · 먹빛 파편 · 불티 · 별빛.
- 모드 처리: 첫 벽(블록) 적중 시 → 현재 `shard` 정지(로컬 층 제거) + `reflect` 를 hitPoint/법선에 재생 + 새 `shard` 를 hitPoint 에서 반사 방향 r = d − 2(d·n)n 으로 발사(반사 1회 제한).
  적 적중, 또는 반사 후의 적중/사거리 끝 → `impact`.
- 시간표(미리보기, 벽 반사): 0 cast, 0.2 발사(0.34 s 비행), 0.54 반사 + 재발사(0.3 s), 0.84 최종 적중.
- 피해 권장 시점: 최종 `impact` 시작. (반사 순간은 피해 없음 권장)
- 런타임 입력: attached = 발 위치+수평 방향, projectile = 위치+진행 방향, impact = hitPoint/surfaceNormal/incomingDirection(공중: −incomingDirection).
- 주요 기능: meshParticle(마법진), attachedFollow, rateOverDistance, worldSpace, bloom. scale 없음.
- 층 수 27 (cast 4 / shard 7 / reflect 8 / impact 8). 고유 텍스처 RGBA 0.986 MiB (12장).
- client_compat check: OK (fix 변경 0건), validate OK.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
