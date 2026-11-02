# NewMoonVeil (그믐 장막) — 어둠 비전, 변환 메모

- 시스템 3개 (phased, SCHEMA §5.1, 모두 attached: P = 시전자 발, +Z = 시전자 수평 방향):
  - `veil` (begin, 0.9 s, 15 layers): 시전자 등 뒤로 검은 그믐달(지름 2.2 m) 떠오름, 먹빛 초승달·어둠 안개·리본이 몸을 감싸고 룬 고리가 수축 → **0.72 s 사라짐 섬광 = 은신 시작 권장 시점**
  - `hidden` (sustain, loop, 7 layers): 은신 중 — 발밑 옅은 물결·먹 조각·드문 별빛·희미한 그믐달 잔상만 (거의 안 보이게)
  - `strike` (end, 0.4 s, 24 layers): **풀리는 순간 일격 = end 시작 시점 (피해 권장 end + 0.05 s)**. 그믐달이 초승달로 번쩍, 장막이 찢겨 흩어짐, 정면 1.6 m 높이 1.15 m 에 거대 초승달(먹빛+빛) 베기·세로 베기·가로 베기, 섬광, 파동 2겹, 정면 빛줄기, 파편, 바닥 충격파 (end 후 약 1.4 s 에 종료)
- 런타임 입력: `holdDuration` (은신 시간, 기본 2.5 s, 0.3~30 s; sustainStart 0.8 s 부터 잼). 서버가 은신을 공격 등으로 일찍 풀면 cancel(jumpToEnd) 로 strike 가 바로 재생됨.
- 시전자 모델 숨김/표시는 서버(모드)가 처리 (이 팩은 이펙트만).
- 모든 항목이 클라이언트 지원 기능 안 (client_compat check OK, fix 0 changes; phasedSequence 사용). 고유 텍스처 RGBA 2.05 MiB.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
