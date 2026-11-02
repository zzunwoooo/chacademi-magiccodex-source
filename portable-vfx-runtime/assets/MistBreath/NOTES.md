# MistBreath (안개 숨) — 변환 메모

물(냉기) 초급 근거리 범위 마법. 차가운 안개를 원뿔로 내뿜어 피해 + 둔화.

## 시스템 / 역할
| 시스템 | 역할 | 시작 | 배치 |
|---|---|---|---|
| breath | attached | 시전 t=0 (1회) | 시전자 발 위치 + 수평 방향 (매 프레임, 로컬 +Z = 정면). 입 위치 = 로컬 (0, 1.55, 0.3) |

## 시간표
- 0.00 s: 입 앞 서리 결정 + 냉기 빛.
- 0.05–1.10 s: 원뿔 분사 — 냉기 안개(가산) + 먹빛 깊은 안개(반투명), 빠른 서리 줄기, 눈꽃·얼음 조각·반짝임, 앞으로 퍼지는 냉기 고리와 나선 획(0.14–0.16 s 간격).
- 0.25–1.0 s: 원뿔 아래 바닥에 서리 문양이 하나씩 피어나고(약 1.2 s 유지), 바닥을 기는 냉기, 바닥 서리 줄.
- 분사 입자는 world 공간: 시전자가 돌아도 이미 뿜은 안개는 그 자리에서 흘러간다. 마지막 입자까지 약 2.2 s.

## 피해 / 둔화 권장
- 판정 범위: 입에서 정면 약 4 m, 반각 약 25° 원뿔 (바닥 서리는 정면 1.3–3.9 m).
- 피해: 0.15 s 부터 0.25 s 간격 4회 (0.15 / 0.40 / 0.65 / 0.90 s) 권장. 둔화는 마지막 피해 뒤 모드 설정 시간.

## 런타임 입력
- attached: anchor(발) + facing. scale 없음.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## 클라이언트 검사 / 텍스처
- tools/client_compat.py check: OK (fix 0건, 금지 필드 없음). validate.py OK.
- 고유 텍스처 RGBA: 0.68 MiB (9장: T_Dot.png 32x32, T_Frost.png 128x128, T_FrostStroke_Sheet.png 384x192, T_Glow.png 64x64, T_IceShard.png 64x64, T_Mist_Sheet.png 192x192, T_Ripple.png 128x128, T_WaterStar.png 128x128, T_WaterStreak.png 256x32).
- 층 수: 15 (breath 15).
- features.required: additiveBlend, alphaBlend, attachedFollow, billboard, bloom, drag, flipbook, gravity, hdrTint, linearVelocity, meshParticle, noise, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral, worldSpace
