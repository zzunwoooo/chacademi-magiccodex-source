# IcicleFling (고드름 투척) — 변환 메모

물(얼음) 초급 투사체. 손에 고드름 2개가 맺히고 차례로 포물선을 그리며 던져진다.

## 시스템 / 역할
| 시스템 | 역할 | 시작 | 배치 |
|---|---|---|---|
| cast | attached | 시전 t=0 (1회) | 시전자 발 위치 + 수평 방향. 손에 든 고드름 2개 = 로컬 (0.18, 1.52, 0.42) / (0.44, 1.3, 0.38), 위로 약 26° 기울어짐 |
| projectile | projectile | 0.25 s (1번), 0.42 s (2번) 에 각각 새 인스턴스 | 위치 + **진행 방향(포물선 접선)** 매 틱. 고드름 메쉬가 로컬 +Z 를 따라 놓이므로 곡선을 따라 고개를 숙인다 |
| impact | impact | 고드름마다 명중 시 새 인스턴스 | hitPoint, surfaceNormal, incomingDirection (공중 = −incomingDirection) |

## 시간표
- 0.00–0.25 s: 손 냉기, 서리 소용돌이, 얼음 조각이 모여 고드름 2개가 자란다 (cast 의 고드름은 각 발사 시점에 사라짐).
- 0.25 / 0.42 s: 투척 섬광 + 가루, projectile 시작. 포물선·속도·중력은 모드 쪽 (미리보기: 약 5 m 를 0.55 s, 정점 +1.3–1.6 m).
- 비행: 고드름이 축을 따라 천천히 회전, 서리 꼬리·얼음 조각·반짝임·냉기/먹빛 안개.
- 명중: 섬광, 서리 문양(1.0 s), 박힌 고드름(0.7 s, 면에서 솟음), 고리 2겹, 얼음 파편 16 + 파편 줄기 14, 냉기. 약 1.0 s.

## 피해 권장 시점
- 각 고드름 명중 시 1회씩 (총 2회).

## 런타임 입력
- attached: anchor + facing. projectile: 위치 + 진행 방향. impact: hitPoint/normal/incoming. scale 없음.
- 미리보기의 포물선은 데모 전용(events 의 via). projectile 시스템은 loop 없이 duration 10 s (명중 시 정지).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## 클라이언트 검사 / 텍스처
- tools/client_compat.py check: OK (fix 0건, 금지 필드 없음). validate.py OK.
- 고유 텍스처 RGBA: 0.74 MiB (10장: T_Dot.png 32x32, T_Frost.png 128x128, T_FrostStroke_Sheet.png 384x192, T_Glow.png 64x64, T_IceShard.png 64x64, T_Icicle.png 256x64, T_Mist_Sheet.png 192x192, T_Ripple.png 128x128, T_WaterStar.png 128x128, T_WaterStreak.png 256x32).
- 층 수: 25 (cast 7, projectile 8, impact 10).
- features.required: additiveBlend, alphaBlend, attachedFollow, billboard, bloom, drag, flipbook, gravity, hdrTint, linearVelocity, meshParticle, radialVelocity, rateOverDistance, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral, worldSpace
