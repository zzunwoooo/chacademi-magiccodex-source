# RimeCrystal (서리 결정) — 변환 메모

물(얼음) 초급 투사체. 회전하는 서리 결정탄. 같은 적에게 3번 맞으면 그 적이 1초 굳는다.

## 시스템 / 역할
| 시스템 | 역할 | 시작 | 배치 |
|---|---|---|---|
| projectile | projectile | 발사마다 새 인스턴스 | 위치 + 진행 방향 매 틱 (손 위치에서 출발 권장: 시전자 로컬 (0.3, 1.35, 0.55)) |
| impact | impact | 명중마다 새 인스턴스 | hitPoint, surfaceNormal, incomingDirection (공중/엔티티 = −incomingDirection) |
| freeze | attached (**대상** 엔티티) | 3번째 명중 순간 | anchor = 대상 발 위치, facing = 대상 수평 방향, 매 프레임 |

freeze 는 SCHEMA §5 attached 계약을 시전자가 아니라 **맞은 대상**에 적용한다 (FlameLash 의 wrap 과 같은 방식). 모양은 원형이라 facing 은 아무 값이어도 된다.

## 시간표
- 탄: 비행 중 결정 회전, 서리 꼬리·얼음 조각·반짝임. 명중 시 로컬 층 제거, 월드 꼬리는 자연 소멸.
- impact: 0 s 섬광 + 서리 문양(바닥/벽 면에 붙음, 0.9 s) + 고리 2겹 + 얼음 파편, 약 0.9 s.
- freeze (3타 시점 = 0 s): 0–0.14 s 얼음 껍질(고드름 13개, 2겹)이 발밑에서 솟아 몸을 감싼다 → 1.0 s 유지 (내부 빛, 서리 바닥, 냉기, 반짝임) → **1.0 s 파쇄** (섬광, 얼음 조각 26개, 고리 2겹, 가루). 약 1.8 s 뒤 소멸.

## 피해 / 상태 권장 시점
- 피해: 각 탄 명중(impact 시작).
- 빙결(이동·행동 불가) 1.0 s: freeze 시작 ~ 1.0 s 의 파쇄 섬광까지. 파쇄 시 추가 피해를 준다면 1.0 s.
- 스택 카운트(1/3, 2/3)는 모드가 센다. 스택 표시용 별도 이펙트는 이 팩에 없다 (필요하면 요청).

## 런타임 입력
- projectile: 위치 + 진행 방향. impact: hitPoint/normal/incoming. freeze: 대상 anchor + facing. scale 없음 (껍질은 1.8 m 키 기준; 큰 몹에는 작게 보인다).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## 클라이언트 검사 / 텍스처
- tools/client_compat.py check: OK (fix 0건, 금지 필드 없음). validate.py OK.
- 고유 텍스처 RGBA: 0.74 MiB (10장: T_Dot.png 32x32, T_Frost.png 128x128, T_FrostStroke_Sheet.png 384x192, T_Glow.png 64x64, T_IceShard.png 64x64, T_Icicle.png 256x64, T_Mist_Sheet.png 192x192, T_Ripple.png 128x128, T_WaterStar.png 128x128, T_WaterStreak.png 256x32).
- 층 수: 28 (projectile 8, impact 8, freeze 12).
- features.required: additiveBlend, alphaBlend, attachedFollow, billboard, bloom, drag, flipbook, gravity, hdrTint, linearVelocity, meshParticle, noise, radialVelocity, rateOverDistance, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral, worldSpace
