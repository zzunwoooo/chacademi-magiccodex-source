# 스토리 컷신 배치 (런처)

각 폴더를 **런처 `config/chaca_cutscene/<폴더 이름>/`** 에 그대로 넣는다 (cutscene.yml + PNG + bgm.ogg). 서버에는 필요 없음.
컷신 id = 폴더 이름 = steps.yml 의 `컷신:` 값. 목록·기획: [docs/plans/story-cutscenes-ch1-ch2.md](../../docs/plans/story-cutscenes-ch1-ch2.md)

| 폴더 | 단계 | 상태 |
|---|---|---|
| `ch1_harbor_arrival` | ch1-7 (브람 "출항이다!" 다음) → 끝나면 ch1-8 시작명령 `essentials:warp harbor` | 오너 납품 2026-10-26 (3장면·자막 없음·BGM, 약 5초 — 단순하게 감) |
| `ch0_forgotten_dream` | tutorial 단계 | 미제작 |
| `magic_tutorial` | ch1-4 | 미제작 |
| `ch1_ashen_night` | ch1-23 | 미제작 (`chacademy-story/example/ch1_ashen_night` 는 예제) |

컷신 파일이 런처에 없으면 그 단계에서 컷신 대기가 반복될 수 있음 (system-audit D-3) — 미제작 컷신은 steps.yml `컷신:` 을 비워 두거나 파일을 먼저 넣을 것.
