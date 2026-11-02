# LightStitch (빛 바늘땀) — 변환 메모

- 원소/등급: 빛 · 초급. 실을 단 빛 바늘 3개를 부채꼴로 던지고, 맞은 적마다 빛 바늘땀 고리가 묶이며 맞은 적끼리 빛실로 꿰매져 느려진다.
- 시스템 (모두 모드가 시작/정지):
  - `throw` (attached, 0–0.3 s): 손앞 작은 세로 마법진 + 바늘 3개 형성 + 섬광.
  - `needle` (projectile, 바늘마다): 빛 바늘 메쉬 + 끝 별 + 뒤로 남는 빛실(월드 줄무늬) + 별빛 + 먹빛 조각. 적중 시 `mark` (impact).
  - `mark` (impact): 섬광 · 회전하며 조여드는 육각별 문양 · 충격 고리 · 불티 · 빛 파편.
  - `bound` (attached to 맞은 적, loop): 몸통·허리 높이 바늘땀 고리 2개(역회전, 기울어짐) + 발밑 고리 + 천천히 떨어지는 빛 먼지(둔화 표시). 1.8 m 몹 기준(scale 없음).
  - `thread` (link, source = 적 A 가슴, target = 적 B 가슴, 매 프레임): pathFollow 빛실 2가닥(엇갈린 물결 = 바느질) + 처짐 + 실 위를 흐르는 매듭 별 + 양 끝 매듭 반짝임. 꿰맬 쌍마다 한 인스턴스(맞은 순서대로 1–2, 2–3).
  - `release` (attached to 적, 둔화 종료 시): 고리가 풀리며 퍼지고 파편이 흩어짐.
- 시간표(미리보기): 0 throw, 0.18/0.20/0.22 바늘 발사(비행 0.26 s), 0.44–0.48 bound, 0.5/0.55 thread, 3.6 thread·bound 정지 + release.
- 피해 권장 시점: 각 `mark` 시작(바늘 적중). 둔화는 `bound` 시작부터 `release` 까지.
- 런타임 입력: attached = 대상 발 위치+수평 방향, projectile = 위치+진행 방향, impact = hitPoint/surfaceNormal/incomingDirection(적: 공중 규칙),
  link = 두 적의 가슴 높이 월드 점(미리보기: 발 + [0,1.15,0]) 매 프레임.
- 주요 기능: meshParticle, attachedFollow, linkTarget, pathFollow, rateOverDistance, bloom. scale 없음.
- 층 수 27 (throw 4 / needle 6 / mark 5 / bound 4 / thread 5 / release 3). 고유 텍스처 RGBA 0.611 MiB (10장).
- client_compat check: OK (fix 변경 0건), validate OK.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
