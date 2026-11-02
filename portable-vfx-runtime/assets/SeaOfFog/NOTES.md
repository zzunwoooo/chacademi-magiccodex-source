# SeaOfFog (안개 바다) — 물 · 고급

## 시스템 / 역할 (3개, 모두 시전 시 동시에 시작 가능)
- `fog` — **static**. P = 시전 지점(시전자 발 위치), local +Y = 위. 저작 반경 8 m, `scale = {input:"areaRadius", reference:8, min:0.5, max:1.5}`. 길이 8.8 s.
- `veil` — **attached** (시전자 발 + 수평 방향). 8.0 s. 몸을 감싸는 안개·먹빛 안개·물 고리 2개·반짝임 = "숨음 + 다음 공격 강화" 표시. **첫 강화 공격이 나가면 모드가 stop** 신호를 보내면 됨(입자는 수명대로 사라짐).
- `ambush` — **impact**. 첫 강화 공격이 적중한 지점(hitPoint = 적 몸통, 공중 규칙: surfaceNormal = −incomingDirection). 1.0 s. 물 초승달 2겹 + 섬광 + 물보라 + 안개 터짐.

## 시간표 (fog 기준, s)
| 시각 | 연출 |
|---|---|
| 0.0–0.8 | 시전 지점에서 안개가 사방으로 터져 나감(충격 고리 2겹), 옅은 물 마법진(희미하게) |
| 0.2–8.0 | 안개 바다 유지: 낮은 안개 + 안개 둑 + 높은 안개 + 경계 안개벽 + 먹빛 안개 덩어리, 빛기둥 6개, 떠 있는 물방울·반짝임, 바닥 파문, 흐르는 물살 호 |
| 8.0–8.8 | 안개가 중심으로 빨려 들며 걷힘, 수렴 고리, 물방울 상승 |

## 피해 권장 시점
- 안개 자체는 피해 없음(은신 구역 0.8–8.0 s). 강화 첫 공격 적중 순간 = `ambush` 재생 시각(t = 0).

## 런타임 입력
- fog: P + `areaRadius`. veil: 시전자 발 위치 + 수평 방향(매 프레임). ambush: hitPoint / surfaceNormal / incomingDirection.

## 클라이언트 호환
- `client_compat.py fix` 0 change(s), `check` OK, `validate.py` OK. 비지원 필드 없음 (attachedFollow 사용 — 지원 목록에 있음).
- 고유 텍스처 RGBA 2.39 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
