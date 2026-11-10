# PepSweet — 빼빼로데이 던전 기믹·연출 VFX (schema 1.21.0)

PortableVFX id = `claude:pepsweet/<system>` · 13 systems · 모두 `role: static`, `chavfx{id=pepsweet/<system>}` 로 한 점(바닥 또는 엔티티 발밑)에서 재생, 방향 = 시전자 수평 facing.
실제 클라이언트 로더(ClaudeConfigLoader, portable-vfx-preserved-245d836c-rgba2048-bossfx2.jar) 검사: **defs=13 errors=0 warnings=0**.

## 크기 (scale)
- `scale = {input: areaRadius, reference: 1, min: 1, max: 1}` → **어떤 r 을 넘겨도(또는 안 넘겨도) 배율 1** = 아래 표의 만든 크기 그대로.
  한 팩은 scale reference 를 하나만 가질 수 있어서 시스템마다 다른 반경(1.2 / 3.5 / 6 …)을 "r = 그 반경일 때 배율 1"로 맞추려면 이 방법뿐이다.
  → MythicMobs 에서 `r=` 은 생략해도 되고, 표의 반경을 넣어도 결과가 같다.
- 바닥 원은 local XZ (y 0.02~0.04), 기준점 = 발밑.

## 시스템
| system | 길이 | 기준 반경 | 레이어 | 재생 방법 / 핵심 시각 |
|---|---|---|---|---|
| pair_mark | 1.0 s 조각 | 1.2 | 18 | **1초마다 다시 재생 (6초)**. 끝 0.12 s 가 다음 조각의 시작과 겹쳐 사라지고 나타남(크로스페이드), 하트 띠는 1초에 30°(12칸 대칭) 돌아 이어붙어도 끊기지 않음. 발밑 하트 선화 + 고리 2겹 + 하트 띠, 1초마다 하트 맥박 고리, 머리 위 2.4 m 빼빼로 아이콘 + 하트(1초 심장박동), 오르는 하트·반짝이 |
| pair_success | 1.5 s | 3.5 | 47 | 0~0.2 고리 수축·모임 → 0.18 섬광 → 하트 문양진(7.2 m) + 하트 선화 + 파동 2겹 + 분홍 광선·기둥 → 하트 불꽃 4발(0.30 / 0.48 / 0.62 / 0.80 s) + 리본 나선 + 스프링클·색종이·금별 |
| pair_fail | 1.2 s | 3 | 18 | 0~0.12 외톨이 하트가 작아짐 → 0.12 초코 폭발: 바닥 초코 스플랫(분홍 광택 테두리), 초코 방울·왕관 튀김, 깨진 하트 조각(어두운 몸통+분홍 테두리), 코코아 연기 |
| gift_spawn | 1.2 s | 1.5 | 20 | 리본 소용돌이(0~0.6 수렴) → 0.55 팡 섬광 + 금 파동 + 금 반짝이·별·색종이, 금 문양진 |
| gift_break | 1.4 s | 2 | 19 | 0.04 터짐: 금 섬광, 리본 조각(메시) 날림, 빼빼로 막대(초코·딸기) 사방으로, 색종이 4색, 금별, 금 문양진·파동 |
| shield_up | 0.8 s | 2.5 | 16 | 1회. 초코 웅덩이 퍼짐, 초코 방울이 솟으며 모임, 초코 리본 나선 → 0.15~0.6 하트 껍질이 커지며 생김(0.62 s 완성) → 0.6 분홍 섬광·하트. 껍질은 0.8~0.95 s 에 사라짐 → **shield 를 0.8 s(16틱) 뒤에 시작하면 이음매 없이 이어짐** |
| shield | 3.0 s 조각 | 2.5 | 16 | **방어막 동안 3초마다 다시 재생**. 껍질·광택·테두리·바닥 모두 수명 3.15 s, 끝 0.15 s 크로스페이드. 하트 띠 2개는 3초에 60°(띠 12칸 중 2칸) 반대로 돌아 이어붙어도 각도가 맞음. 반짝임·흘러내림·코코아 입자·하트는 rate 방출이라 자연히 이어짐 |
| shield_break | 1.0 s | 2.5 | 16 | 0 껍질 번쩍 + 부풀며 사라짐(0.15 s) → 0.1 섬광, 깨진 하트 조각 46개, 초코 방울, 하트 터짐, 파동 2겹, 코코아 연기 |
| stun | 2.0 s 조각 | 1.5 | 26 | **기절 동안 2초마다 다시 재생 (8초 = 4번)**. 머리 위 3.2 m 하트 3 + 별 3 이 2초에 한 바퀴(위치가 정확히 원위치), 별 자전 2초에 144°(5각 대칭), 어지러움 호 2개, 0.45 / 1.45 s 하트 팡, 발밑 분홍 고리·하트 띠(2초 30°)·반짝이 |
| enrage | 2.4 s | 5 | 35 | 0.1~0.8 크림슨 하트가 두 번 뛰고 초코 리본이 감아 오름, 어두운 기운·크림슨 불꽃, 0.48 고리 수축 → 0.8 폭발: 섬광, 붉은 기둥, 깨진 하트 고리, 깨진 하트 조각 30+16·발밑 초코 조각 14, 초코 리본 확산, 파동 3겹, 광선, 바닥 기둥 6 → 코코아 연기 상승 |
| phase | 2.0 s | 6 | 27 | 0~1.15 초코 리본 소용돌이 3 + 분홍 선 소용돌이 + 빠른 호, 반짝이·코코아가 빨려 들어감 → 1.15 섬광·빛기둥·바닥 파동 2겹(6.5 m)·리본 바깥으로 확산·광선·하트 |
| death | 3.0 s | 6 | 60 | 0 섬광·빛기둥·광선 → 하트 불꽃 6발(0.30 / 0.65 / 0.95 / 1.30 / 1.65 / 2.00 s), 리본 나선 4, 빼빼로 막대 비(0.4~2.4), 별 비, 색종이 2회, 스프링클, 하트 상승, 1.5 s 두 번째 파동·문양진 번쩍 |
| summon | 0.9 s | 1.2 | 15 | 코코아 가루 펑 + 분홍 먼지, 스프링클 4색, 작은 문양진·고리·파동, 섬광, 하트 |

## 반복 재생(seamless) 규칙 — 서버 쪽 타이밍
- `pair_mark`: 20틱마다, `shield`: 60틱마다, `stun`: 40틱마다 같은 위치에서 다시 재생. 조각 사이 간격이 정확히 맞으면 이음매가 안 보인다 (몇 틱 늦어도 크로스페이드 0.12~0.15 s 덕에 깜빡임이 작음).
- 첫 조각은 0.12~0.15 s 동안 페이드 인 된다 (shield 는 shield_up 0.8 s 뒤에 시작 권장).
- chavfx 는 멈출 수 없으므로 **방어막이 일찍 깨지면 마지막 shield 조각이 남은 시간(최대 3 s)만큼 계속 보인다**. shield_break 가 그 위에 크게 터지지만, 완전히 맞추려면 서버가 break 를 다음 3초 경계에 맞추거나(최대 3 s 늦음) shield 조각 주기를 더 짧게 해야 한다 — 필요하면 1.0 s 조각판을 따로 만들 수 있음.

## 재질 / 텍스처
- 26장, 모두 ≤ 512 px, **고유 RGBA 합계 2,813,952 B = 2.68 MiB**.
- 가산(additive): 선·파동·문양진(T_Sigil 384)·하트 띠(T_HeartBand 512×64)·하트·반짝이·별·광선·섬광·광택·분홍 먼지, HDR tint 1.7~2.6.
- 알파(alpha): 초코 스플랫·방울·하트 조각(tint 2.2 → 분홍 테두리만 번짐, 몸통은 어둡게)·리본(빨강/초코)·빼빼로 막대·색종이(4×4 회전 플립북)·스프링클·초코 껍질 몸통(T_Shell)·코코아/어두운 연기(4×4 플립북).
- 메시: 링·파동 링·하트 바닥 선화·하트 띠(바닥/세로)·나선 리본·리본 조각(curl)·호(arc)·빛기둥·**3D 하트 껍질**(implicit heart surface 를 광선으로 잘라 만든 48×32 격자) + 앞 실루엣 테두리 + 높이별 등고선 4개.

## features.required
additiveBlend, alphaBlend, billboard, bloom, drag, flipbook, flipbookCycles, gravity, hdrTint, instanceScale, linearVelocity, meshParticle, noise, orbitalVelocity, radialVelocity, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral
(높은 영향: meshParticle, bloom, orbitalVelocity — 모두 클라이언트 지원 목록 안. 금지 필드 height/depth/orientation/follow/lights/attachPoint/rollWithTravel 없음, lifetime 최대 3.15 s.)

## 이로치 (화이트 쇼콜라)
같은 시스템 + chavfx `tint=F4EDE4` 로 재생하면 됨 (기획 6장).

## 소스
`source/` — Tex.java (텍스처, java.awt 4× 슈퍼샘플), Gen.java (vfx.json), Preview.java (preview.html = engine/build_preview.py 와 같은 조립), MiniHttp.java (미리보기 확인용 로컬 서버), Rgba.java.
빌드: `javac -cp gson-2.10.1.jar Tex.java Gen.java Preview.java` → `java Tex textures` → `java -cp gson.jar;. Gen <아무 1.21.0 vfx.json(units·catalog 복사용)> vfx.json`.
