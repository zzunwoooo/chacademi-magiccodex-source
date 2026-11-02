# PinwheelToss (바람개비 던지기) — 바람 초급, 변환 메모

## 시스템 / 역할
| id | role | duration | 시작 |
|---|---|---|---|
| `projectile` | projectile (돌아가는 바람개비) | 6.0 s loop | 시전 시 |
| `impact` | impact (박혀서 3초간 베기) | 3.05 s | 적/바닥/벽 적중 시 hitPoint |

- 바람개비 본체는 카메라를 보는 빌보드(회전 rotZ 22~24 rad/s) + 흐린 잔상 1겹. 박힌 뒤에도 같은 위치에서 3초간 돈다 (`I04/I05`, 수명 3.0 s).
- 적중 지점에서 표면 쪽 로컬 +Y 0.3 m 앞(공중 적중 = 던진 사람 쪽)에 본체가 떠 있다. 적이 움직이면 모드가 impact 인스턴스를 적에 붙여 옮겨도 된다 (위치만 이동, 방향 유지) — 지금 스키마는 impact 를 고정점으로 정의하므로, 붙이려면 모드 쪽 처리 필요 (보고 사항).

## 시간표 (impact 기준)
- 0.00 박힘: 섬광, 충격 고리, 줄기 18개, 표면 원 문양(고리·갈고리 4개·눈금) 그려짐
- 0.0~3.0 계속 베기: 바람 베기 호(초당 7), 회전 불꽃 줄기(초당 30), 먹빛 바람 조각, 바람 말림
- 0.5 / 1.0 / 1.5 / 2.0 / 2.5 / 3.0 베기 틱: 밝은 베기 호 2개 + 작은 섬광
- 3.00 부서짐: 충격 고리, 줄기 24개, 먹빛 조각 8개, 빛 입자 30개 (끝)

## 피해 권장 시점
- 적중 순간 1회 + 0.5 s 간격 6틱 (0.5~3.0 s, 틱 섬광과 일치).

## 런타임 입력
- projectile: 위치 + 진행 방향. impact: hitPoint, surfaceNormal, incomingDirection (공중 = −incomingDirection). scale 없음.

## 클라이언트 호환
- `client_compat.py check`: OK. 비지원 필드 미사용.
- 고유 텍스처 RGBA: 0.99 MiB (10장, T_Pinwheel 256 px).
- 필요 기능: meshParticle (표면 원 문양·베기 호·리본), bloom, orbitalVelocity / radialVelocity (회전 베기 줄기), flipbook, stretchedBillboard.
- 길이: 초급 기준(0.6~1.8 s)보다 길지만 "3초간 벤다" 기획을 따름.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
