# DandelionVolley (민들레 홀씨탄) — 바람 초급, 변환 메모

## 시스템 / 역할
| id | role | duration | 시작 시점 |
|---|---|---|---|
| `cast` | attached (시전자 발 + 수평 방향) | 0.5 s | 시전 t=0 |
| `seed` | projectile (홀씨 1개) | 6.0 s loop | t≈0.1 s 부터 5발 (각 발마다 새 인스턴스) |
| `pop` | impact | 0.5 s | 각 홀씨가 적/바닥/벽에 닿는 순간 (발마다 새 인스턴스) |

- 홀씨 5개 = `seed` 인스턴스 5개. 서버가 정면 기준 yaw −28°, −14°, 0°, +14°, +28° 로 각각 발사 (미리보기는 0.1~0.26 s 에 0.04 s 간격, 가운데부터).
- 홀씨는 느리게 떠간다: 미리보기는 약 7 m 를 1.55~1.67 s (≈ 4.5 m/s), 살짝 위로 휘는 곡선. 실제 속도·경로는 서버 값. 비행이 6 s 보다 길면 `P02/P03`(홀씨 본체, 수명 6 s) 이 다시 터지므로 사거리 기준 6 s 이내로.
- 발사 위치: 시전자 오른손 근처 (발 기준 로컬 [0.22, 1.35, 0.55]).

## 시간표
- 0.00 바닥 바람 마법진(고리+꽃잎 5장) 그려짐, 손에 바람 소용돌이
- 0.08 부채꼴 안내선 5줄, 0.10 손 섬광 + 솜털·작은 홀씨 터짐 → 홀씨 5발 출발
- 비행 중: 홀씨 본체(회전 2겹) + 둘레 바람 리본 + 솜털·바람 줄기·떨어지는 작은 홀씨·먹빛 바람 조각 꼬리
- 적중: 작은 섬광, 솜털 실 22줄, 충격 고리 2겹, 바람 말림, 솜털·홀씨가 위로 흩날림

## 피해 권장 시점
- 각 홀씨가 닿는 순간 (`pop` 시작 t=0) 1회씩.

## 런타임 입력
- cast: 시전자 발 위치 + 수평 방향 (매 프레임). seed: 위치 + 진행 방향. pop: hitPoint, surfaceNormal, incomingDirection (공중 적중 = −incomingDirection).
- scale 없음.

## 클라이언트 호환
- `client_compat.py check`: OK (FIX 없음). 비지원 필드 미사용.
- 고유 텍스처 RGBA: 0.80 MiB (10장, 최대 256 px).
- 필요 기능: meshParticle (바닥 마법진·안내선·리본), bloom, orbitalVelocity (먹빛 조각 회전), attachedFollow, flipbook, stretchedBillboard, noise.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
