# ClepsydraDrop (물시계 방울) — 변환 메모

물 초급 지정 마법. 대상 머리 위에 물시계가 생기고 2초 뒤 물덩이가 떨어진다.

## 시스템 / 역할
| 시스템 | 역할 | 시작 | 배치 |
|---|---|---|---|
| clock | static | 시전 t=0 (1회) | P = 대상 발 위치(바닥), 기준 방향 = 서버가 넘기는 시전자 수평 방향 (방향 무관한 원형 연출) |

## 시간표
- 0.0–0.3 s: 바닥 표식(선 고리 2겹 + 룬 띠 + 육망성)이 그려지고, 대상 위 2.75 m 에 물시계 문양이 펼쳐진다.
- 0.3–1.98 s: 물시계 위 저수 구슬이 차오른다 (물방울 모임, 소용돌이), 시계 밑에서 물이 똑똑 떨어지고 0.5 / 1.0 / 1.5 s 에 째깍 섬광. 바닥의 카운트다운 고리가 0.4 → 2.0 s 동안 좁혀진다.
- 1.98 s: 물덩이 낙하 (0.18 s).
- **2.16 s: 착탄** — 섬광, 물보라 왕관 2겹, 파동 3겹, 소용돌이, 물방울·먹빛 파편·안개. 약 0.8 s 뒤 소멸 (총 약 3.0 s).
- 초급 표준 길이(0.6–1.8 s)보다 길지만, 마법 설명의 "2초 뒤" 를 지키기 위한 것.

## 피해 권장 시점
- 2.16 s (착탄) 1회, 반경 약 1.5 m.
- 대상이 2초 사이 움직여도 이 이펙트는 시전 시 P 에 고정이다 (static). 대상 추적이 필요하면 모드에서 대상 발 위치로 이 static 을 attached 처럼 매 프레임 옮겨도 된다 (local 층만 있으므로 같은 모양).

## 런타임 입력
- static: P(대상 발) + 수평 방향. scale 없음.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## 클라이언트 검사 / 텍스처
- tools/client_compat.py check: OK (fix 0건, 금지 필드 없음). validate.py OK.
- 고유 텍스처 RGBA: 1.45 MiB (14장: T_ClockSigil.png 256x256, T_DeepShard.png 128x32, T_Dot.png 32x32, T_Glow.png 64x64, T_Line.png 64x16, T_Mist_Sheet.png 192x192, T_Ripple.png 128x128, T_RuneStrip.png 256x64, T_Splash_Sheet.png 320x320, T_WaterDrop.png 128x128, T_WaterOrb.png 128x128, T_WaterStar.png 128x128, T_WaterStreak.png 256x32, T_WaterStroke_Sheet.png 384x192).
- 층 수: 28 (clock 28).
- features.required: additiveBlend, alphaBlend, billboard, bloom, drag, flipbook, gravity, hdrTint, linearVelocity, meshParticle, noise, radialVelocity, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral, worldSpace
