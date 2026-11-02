# SandStomp (흙먼지 발구르기) — 변환 메모

- 시스템 `stomp` (attached): 시전자 발 위치 + 수평 방향. 길이 1.2 s.
- 시간표: 0.00 준비(발 빛, 모래알이 발로 모임) → **0.15 s 발구르기**: 섬광, 땅 마법진(고리·룬 띠·육각형 선)이 반경 2 m 로 펼쳐짐, 균열 데칼, 충격파 2겹(0.15 / 0.23), 모래 왕관(세로 띠 메쉬)이 바깥으로 퍼짐, 위로 솟는 상승선·조약돌·모래알 → 0.4–1.2 먼지 가라앉고 입자 상승.
- 피해·띄우기 권장 시점: 0.15 s (반경 2 m).
- 런타임 입력: attached 표준 + `areaRadius` (scale reference 2 m, 배율 min 0.5 / max 1.5) — 반경 설정이 바뀌면 연출 전체가 발 기준으로 같이 커진다.
- 메쉬: `rings`, `runes`(10칸), `hex`(육각별 선), `crownA/crownB`(세로 원통 띠 1.1π/0.9π, 플립북 와이프).
- 어두운 강조: 바닥 그림자, 균열 데칼, 어두운 모래 띠(`strokeInk`), 어두운 먼지, 돌 파편.
- client_compat check: OK (FIX/WARN 없음). 고유 텍스처 RGBA 1.43 MiB (12장).
- 층 19개 (시스템 1개).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
