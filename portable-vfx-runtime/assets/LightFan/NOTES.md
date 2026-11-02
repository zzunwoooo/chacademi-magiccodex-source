# LightFan (빛 부채) — 변환 메모

- 원소/등급: 빛 · 초급. 앞쪽 120°·반경 3 m 를 빛 부채꼴로 한 번 벤다(오른쪽 → 왼쪽 쓸기).
- 시스템: `fan` 하나 (attached: 발 위치 + 수평 방향, +Z 정면). 시전 동안만 따라오면 된다.
  - 0–0.12 s 예비 동작: 오른손 앞 작은 세로 마법진(고리+육각별) + 빛 모임 + 발밑 고리.
  - 0.12 s 베기: 부채꼴 메쉬 3장(주 부채 · 밝은 코어 · 낮은 보조 부채, 플립북 2×8 로 와이프) + 끝선 리본 호 + 바닥 메쉬 선(호 3개 + 살 7개) + 바닥 잔광 + 앞으로 뻗는 광선 · 끝선 별 · 별빛 · 먼지 · 먹빛 파편 · 손 섬광.
  - ~0.75 s 소멸.
- 피해 권장 시점: 0.18 s (와이프가 부채 중앙을 지날 때). 판정 범위 = 전방 120°, 반경 3 m.
- 런타임 입력: attached = 발 위치 + 수평 방향.
- 주요 기능: meshParticle(부채·선), flipbook, attachedFollow, bloom. scale 없음(반경이 바뀌면 메쉬가 그대로라 3 m 기준).
- 층 수 16 (예비 4 / 베기 12). 고유 텍스처 RGBA 0.518 MiB (8장).
- client_compat check: OK (fix 변경 0건), validate OK.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
