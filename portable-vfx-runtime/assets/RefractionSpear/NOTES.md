# RefractionSpear (굴절 창) — 빛 / 고급

## 시스템 (모드가 각각 시작)
| id | 역할 | 길이 | 층 | 시작 |
|---|---|---|---|---|
| cast | static | 0.9 s | 9 | 시전 즉시, P = 시전자 발, 기준 = 시전자 수평 방향. 손 앞 (0.3, 1.45, 0.75) 에 수직 마법진 + 창 형성, 0.45 s 에 발사 섬광 |
| lens | static | 1.75 s | 10 | 렌즈 2개 각각 1 인스턴스, P = 렌즈 중심(공중 점). 0.0–0.35 생성, 1.45 깨짐. 시전 직후(0.05/0.15 s) 놓기 권장 |
| spear | projectile | (최대 2.5 s) | 10 | 직선 구간마다 새 인스턴스: ① 손 → 렌즈1 ② 렌즈1 → 렌즈2 ③ 렌즈2 → 최종 지점. 구간 끝(렌즈)에서는 hit 처리 = refract 를 공중 충돌 규칙으로 재생 |
| refract | impact | 0.9 s | 8 | 창이 렌즈를 지나 꺾일 때 (렌즈 중심, 공중: surfaceNormal = −진행방향) |
| pierce | impact | 0.7 s | 4 | 관통한 적마다 (공중 규칙, 적 몸 중심) |
| impact | impact | 1.6 s | 15 | 최종 지형 충돌 (바닥/벽/공중 모두): 박힌 창·균열·빛기둥·파동 |

spear 는 loop:false, duration 2.5 s 의 한 발짜리 몸체(P01/P02/P07 는 t=0 버스트 1개, 수명 2.5 s) → 한 구간 비행이 2.5 s 를 넘지 않게.

## 시간표 (데모)
0.0 시전 · 0.05/0.15 렌즈 생성 · 0.45 발사 · 0.65 렌즈1 굴절 · 0.85 렌즈2 굴절 · 0.97 관통 · 1.09 최종 충돌 · 2.7 끝.

## 피해 권장 시점
- 관통 피해: 창이 적을 지날 때 (pierce 재생과 같은 틱).
- 최종 충돌: impact 시작 시 (+0.0 s).

## 런타임 입력
- projectile: 위치 + 진행 방향(매 틱). impact/refract/pierce: hitPoint, surfaceNormal, incomingDirection. scale 없음.

## 클라이언트
- `client_compat.py check`: OK (fix 0건, 시각 변화 없음). 고유 텍스처 RGBA 2.30 MiB.
- trail(P07 TrailRibbon) 사용 (trails 지원 기능).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
