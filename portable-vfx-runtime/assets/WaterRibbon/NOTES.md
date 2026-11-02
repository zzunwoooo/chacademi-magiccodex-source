# WaterRibbon (물 리본) — 변환 메모

물 초급 근접(중거리) 마법. 물 리본 채찍 2연타, 마지막 타가 대상을 살짝 끌어당긴다.

## 시스템 / 역할
| 시스템 | 역할 | 시작 | 배치 |
|---|---|---|---|
| ribbon | attached | 시전 t=0 (1회) | 시전자 발 위치 + 수평 방향 (매 프레임, 로컬 +Z = 정면) |

리본은 시전자 기준 메쉬(손 (0.3, 1.35, 0.4) → 정면 3.4 m)로 저작되어 있다. 끝점(명중점) = 로컬 약 (0.3, 1.05, 3.8) / (0.3, 0.9, 3.8).
꼬인 리본(폭 방향이 길이를 따라 회전)이라 어느 각도에서도 보인다. 2x8 물 획 플립북이 손 → 끝으로 그려진다.

## 시간표
- 0.00 s 1타: 오른쪽으로 휜 가로 채찍, 0.15 s 에 끝이 튀며 섬광·물보라.
- 0.40 s 2타: 머리 위로 넘기는 채찍, 0.55 s 에 끝 섬광 + 감기는 고리 2겹 + 물보라 2겹.
- 0.60 s 끌어당김: 거의 팽팽한 리본이 끝 → 손 방향으로 쓸려 들어오고 물줄기가 시전자 쪽으로 흐른다, 0.85 s 손에서 받는 섬광.
- duration 0.9 s, 마지막 입자까지 약 1.3 s.

## 피해 권장 시점
- 0.15 s (1타), 0.55 s (2타). 끌어당김(넉백 반대 방향 약한 당김)은 0.60–0.85 s 에 모드가 처리 (시전자 쪽 0.5–1 m 권장).

## 런타임 입력
- attached: anchor(발) + facing. scale 없음. 사거리는 3.4 m 고정 연출 (대상이 더 가까워도 리본은 3.4 m 까지 그려진다).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## 클라이언트 검사 / 텍스처
- tools/client_compat.py check: OK (fix 0건, 금지 필드 없음). validate.py OK.
- 고유 텍스처 RGBA: 1.07 MiB (10장: T_DeepShard.png 128x32, T_Dot.png 32x32, T_Glow.png 64x64, T_Mist_Sheet.png 192x192, T_Ripple.png 128x128, T_Splash_Sheet.png 320x320, T_WaterDrop.png 128x128, T_WaterStar.png 128x128, T_WaterStreak.png 256x32, T_WaterStroke_Sheet.png 384x192).
- 층 수: 21 (ribbon 21).
- features.required: additiveBlend, alphaBlend, attachedFollow, billboard, bloom, drag, flipbook, gravity, hdrTint, linearVelocity, meshParticle, noise, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral, worldSpace
