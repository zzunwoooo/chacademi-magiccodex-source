# RainPatter (빗방울 연사) — 변환 메모

물 초급 공격 마법. 작은 물방울 3발을 연달아 쏜다.

## 시스템 / 역할
| 시스템 | 역할 | 시작 | 배치 |
|---|---|---|---|
| cast | attached | 시전 t=0 (1회) | 시전자 발 위치 + 수평 방향 (매 프레임). 손 위치 = 로컬 (0.3, 1.35, 0.55) |
| projectile | projectile | 각 발사 시점마다 새 인스턴스 | 손 위치에서 출발, 위치 + 진행 방향 매 틱 |
| impact | impact | 각 탄이 맞을 때 새 인스턴스 | hitPoint, surfaceNormal, incomingDirection (공중 = −incomingDirection) |

## 시간표
- t = 0.00 / 0.15 / 0.30 s: 발사 (cast 의 손 섬광·고리·물보라가 이 세 시점에 맞춰 터진다). 모드가 발사 간격을 바꾸면 cast 섬광 시점과 어긋나므로 0.15 s 간격 권장.
- 탄 속도·사거리는 모드 설정 (미리보기: 약 5 m 를 0.32 s).
- 명중: 탄 정지(로컬 층 제거, 월드 꼬리는 자연 소멸) → impact 재생, 약 0.8 s 뒤 소멸.

## 피해 권장 시점
- 각 탄이 맞는 순간 (impact 시작) 1회씩, 총 3회.

## 런타임 입력
- attached: anchor(발) + facing. projectile: 위치 + 진행 방향. impact: hitPoint + surfaceNormal + incomingDirection.
- scale 없음.

## 비고
- 미리보기의 events/actors 는 데모 전용 (발사 3회 연출).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## 클라이언트 검사 / 텍스처
- tools/client_compat.py check: OK (fix 0건, 금지 필드 없음). validate.py OK.
- 고유 텍스처 RGBA: 1.41 MiB (11장: T_DeepShard.png 128x32, T_Dot.png 32x32, T_Glow.png 64x64, T_Mist_Sheet.png 256x256, T_Ripple.png 128x128, T_Splash_Sheet.png 384x384, T_WaterDrop.png 128x128, T_WaterOrb.png 128x128, T_WaterStar.png 128x128, T_WaterStreak.png 256x32, T_WaterStroke_Sheet.png 384x192).
- 층 수: 25 (cast 7, projectile 8, impact 10).
- features.required: additiveBlend, alphaBlend, attachedFollow, billboard, bloom, drag, flipbook, gravity, hdrTint, linearVelocity, meshParticle, noise, radialVelocity, rateOverDistance, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral, worldSpace
