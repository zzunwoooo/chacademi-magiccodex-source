# SprayKick (물보라 발차기) — 변환 메모

물 초급 근접 마법. 물보라를 두른 발차기 3연타.

## 시스템 / 역할
| 시스템 | 역할 | 시작 | 배치 |
|---|---|---|---|
| kick | attached | 시전 t=0 (1회) | 시전자 발 위치 + 수평 방향 (매 프레임, 로컬 +Z = 정면) |

## 시간표
- 0.00 s 1타: 낮은 쓸어차기 (오른쪽 → 왼쪽, 높이 약 0.55 m)
- 0.30 s 2타: 중단 되돌려차기 (왼쪽 → 오른쪽, 약 1.0 m, 기울기 −28°)
- 0.60 s 3타(마무리): 높은 돌려차기 (약 1.2 m, 반경 1.25 m) + 발밑 물결 파동 2겹, 소용돌이
- 각 타격의 물 호(arc)는 메쉬 + 2x8 물 획 플립북: 꼬리에서 머리로 그려지며 0.3 s 안에 사라진다.
- 시스템 duration 0.72 s, 마지막 입자까지 약 1.3 s.

## 피해 권장 시점
- 0.06 / 0.36 / 0.66 s (각 접촉 섬광·물보라 왕관 시점). 접촉 지점 = 시전자 정면 약 1.0~1.35 m.

## 런타임 입력
- attached: anchor(발) + facing(수평). scale 없음.
- 물보라·물방울·먹빛 안개는 world 공간이라 시전자가 움직여도 그 자리에 남는다. 호와 섬광은 local (시전자를 따라감).

## 비고
- 플레이어 모션(발차기 애니메이션)은 모드 쪽. 이 이펙트는 0.30 s 간격 3타에 맞춰 있다.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## 클라이언트 검사 / 텍스처
- tools/client_compat.py check: OK (fix 0건, 금지 필드 없음). validate.py OK.
- 고유 텍스처 RGBA: 1.33 MiB (9장: T_DeepShard.png 128x32, T_Dot.png 32x32, T_Mist_Sheet.png 256x256, T_Ripple.png 128x128, T_Splash_Sheet.png 384x384, T_WaterDrop.png 128x128, T_WaterStar.png 128x128, T_WaterStreak.png 256x32, T_WaterStroke_Sheet.png 384x192).
- 층 수: 22 (kick 22).
- features.required: additiveBlend, alphaBlend, attachedFollow, billboard, bloom, drag, flipbook, gravity, hdrTint, linearVelocity, meshParticle, noise, orbitalVelocity, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral, worldSpace
