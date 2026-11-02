# FocusLens (초점 렌즈) — 변환 메모

- 원소/등급: 빛 · 초급. 시전자 앞에 빛 렌즈를 만들고 햇빛을 모아 원뿔로 한 점에 집중시켜 태운다(유지 최대 2 s). 끝나면 렌즈가 부서지고 초점에 마지막 불씨가 터진다.
- 단계(SCHEMA §5.1 phased): sustainStart 0.35 s, hold 기본 1.6 s (min 0.2, max 2.0, 런타임 holdDuration = 실제 유지 시간).
  - begin `lensForm` (attached): 렌즈 마법진이 그려지며 빛이 모임 (0–0.45 s).
  - sustain `lens` (attached): 렌즈 유리 + 고리 3겹 + 육각별 + 룬 띠(역회전) + 유입 광선 + 렌즈 잔상 + 별빛.
  - sustain `beam` (link, source = 렌즈 위치, target = 초점): pathFollow 원뿔 광선(외곽 → 한 점으로 수렴) + 중심 광선 + 원뿔 베일 + 나선 먼지.
  - sustain `burn` (static, P = 초점, +Y = 위/표면): 그을음 데칼(커짐) + 열기 바닥광 + 작은 메쉬 마법진 2겹 + 뜨거운 별 + 맥동 고리 + 불꽃·불티·불씨·연기. 0.6 / 1.2 / 1.7 s 에 불꽃·불티가 단계적으로 늘어남(유지가 길수록 거세짐).
  - end `lensBreak` (attached) + `burnEnd` (static, 초점): 렌즈 파편·고리 확산·섬광 / 초점 섬광·충격 고리·불씨 폭발·연기.
- 모드 연결: link source = 시전자 기준 렌즈 위치 [0.3, 1.45, 0.75] (발 기준, +Z 정면) 의 월드 좌표, target = 조준 지점(블록 표면/적 발밑). static `burn`/`burnEnd` 는 같은 초점에 놓는다.
  렌즈 메쉬는 미리보기 기준으로 아래로 0.485 rad 기울어 있다(2.75 m 앞 바닥 조준). 초점이 크게 다르면 렌즈 기울기만 어긋나 보이고 원뿔은 항상 정확히 초점을 향한다.
- 피해 권장 시점: 유지 중 sustainStart + 0.15 s 부터 0.25 s 간격 틱 (초점 별이 밝게 깜빡이는 구간). 마지막 틱 = end 시작(불씨 폭발).
- 런타임 입력: attached = 발 위치+수평 방향, link = source/target 월드 점 매 프레임, static = 초점, holdDuration.
- 주요 기능: phasedSequence, linkTarget, pathFollow, meshParticle, attachedFollow, stopTail(onStop), flipbook, bloom. scale 없음.
- 층 수 34 (begin 4 / lens 8 / beam 4 / burn 10 / end 4+4) — 2 s 유지형이라 초급 목표보다 약간 많음. 고유 텍스처 RGBA 1.299 MiB (14장).
- client_compat check: OK (fix 변경 0건), validate OK.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
