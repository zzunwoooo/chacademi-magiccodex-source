# SeedMines (홀씨 지뢰밭) — 바람 중급 · 변환 메모

## 구조: 홀씨 1개 = 캐스트 1개 (EmberTrap 과 같은 phased trap 패턴)
모드는 홀씨마다 이 팩을 **독립 캐스트로 6번** 재생한다 (각자 P = 그 홀씨의 바닥 지점, local +Y = 위). 각 캐스트가 따로 발동/만료된다.
기본 배치(데모): 시전자 기준 정면 3 m 지점을 중심으로 반경 1.8 m 육각형, 0.07 s 간격으로 차례로 심김
(caster frame: [0,0,4.8] [1.56,0,3.9] [1.56,0,2.1] [0,0,1.2] [-1.56,0,2.1] [-1.56,0,3.9]).

| id | role | phase | 길이 | 내용 |
|---|---|---|---|---|
| sow | static | begin | 0.6 s | 홀씨가 1.7 m 에서 떨어져 0.42 s 에 착지: 섬광, 링, 작은 마법진이 그려짐, 돌풍, 입자 |
| armed | static | sustain (loop) | hold (기본 20 s, 0.5~60) | 심긴 홀씨, 6각 마법진 + 꽃잎 선, 어두운 바닥, 숨쉬는 빛·문양, 빙 도는 솜털, 오르는 포자, 소용돌이, 먹빛 티 |
| pop | static | end, endBranch "trigger" | 0.3 s (+ 잔상 ~1.7 s) | 밟힘 → 상승 기류: 섬광, 바닥 충격파 2개, 마법진 폭발, 토네이도 띠(밝은/먹빛) 상승, 기둥 줄기, 솜털 22개 흩날림, 세로 베기 호, 돌풍, 먹빛 파편, 올라가는 고리 2개 |
| fizzle | static | end, endBranch "expire" | 0.1 s (+ ~1.4 s) | 시간 만료/취소: 홀씨가 떠올라 날아가고 마법진이 사라짐 |

sequence: sustainStart 0.55 s, hold {default 20, min 0.5, max 60}, trigger: true.

## 피해 / 띄우기 권장 시점
- trigger 신호 = 적이 홀씨 반경 0.55 m 안에 들어온 순간. 피해 + 띄우기는 trigger 후 0.05 s (기둥 줄기·토네이도가 솟는 순간).
- trigger 전에는 아무 피해 없음.

## 런타임 입력
- 캐스트마다 P (+ 시전자 수평 방향), holdDuration, trigger 신호 / cancel

## 클라이언트 검사
- client_compat fix: 0 changes, check: OK, validate: OK
- 층 35 (sow 7 + armed 11 + pop 13 + fizzle 4), 고유 텍스처 RGBA 1.81 MiB


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
