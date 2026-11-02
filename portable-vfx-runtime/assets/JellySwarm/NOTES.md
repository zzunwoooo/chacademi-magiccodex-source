# JellySwarm (해파리 떼) — 물 / 중급

## 시스템 (2개)
- `swarm` — role **attached** (시전자 발 위치 + 수평 방향, 매 프레임 추적). duration 4.75 s.
  소환 마법진·물보라·파문(0–1.5 s) + 해파리 4마리가 시전자 주위를 반경 **2.4 m**, 각속도 **1.15 rad/s**(한 바퀴 5.5 s), 높이 1.25/1.75/1.45/1.9 m(초당 0.08 m 상승)로 돈다 (0.4 → 4.0 s). 4.0 s 에 각 해파리가 그 자리에서 물방울로 터진다.
  해파리 k 위치(시전자 기준, +Z 정면, +X 오른쪽): 각 a = π/2·k + 1.15·(t − 0.4), 위치 = (2.4·cos a, h_k + 0.08·(t − 0.4), −2.4·sin a). 서버 판정을 이 궤도에 맞추면 화면과 일치.
- `sting` — role **impact**. 해파리가 적에 닿을 때마다 모드가 새로 생성: hitPoint = 닿은 지점(해파리 위치 또는 적 몸 중심), 공중 규약(surfaceNormal = −incomingDirection). duration 1.2 s: 섬광·전기 같은 물줄기·파문 2겹·물방울·**둔화 표시**(대상 주위를 돌며 가라앉는 기포 + 회전 고리 1 s)·어두운 물 안개.
- 런타임 입력: swarm = 시전자 발 위치 + 수평 방향, sting = hitPoint / surfaceNormal / incomingDirection. scale 없음.

## 시간표
| t (s) | 연출 |
|---|---|
| 0.00–0.5 | 발밑 마법진, 물보라, 파문 2번, 기포 |
| 0.4–0.6 | 해파리 4마리 순서대로 나타남 (0.05 s 간격) |
| 0.4–4.0 | 궤도 비행, 0.6 s 마다 갓이 수축·확장(맥동), 촉수 회전, 빛 꼬리 |
| 4.0 | 해파리 4마리 터짐(물방울 + 파문) |

## 피해 권장 시점
- 0.5 s 이후 해파리와 적의 거리 < 0.7 m 일 때 sting 1회 (같은 적 재타격 쿨다운 0.5 s 권장) + 둔화. sting 연출의 0 s = 피해 시점.

## 클라이언트
- client_compat check: OK (fix: 0 change, no visual change)
- 고유 텍스처 RGBA: 약 1.64 MiB
- 사용 기능: attachedFollow, orbitalVelocity(해파리 궤도), trails(빛 꼬리), meshParticle(마법진·촉수), flipbook, stretchedBillboard, noise. 지원 안 되는 기능 없음.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
