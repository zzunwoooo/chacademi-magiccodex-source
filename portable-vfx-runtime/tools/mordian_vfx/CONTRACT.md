# 모르디안의 원령 VFX — 서버 연동 표 (MordianBoss.java)

코드 = `claude:<팩 소문자>/<시스템>`. 팩 = `portable-vfx-runtime/assets/Mordian*` (16개, 빨간 예고 없음). 원본 스펙·텍스처 생성기는 이 폴더 (melee·ground·projectile·curse·link·ritual).

| 팩 | 시스템 | role | 서버에서 |
|---|---|---|---|
| MordianClaw | claw_right / claw_left | static | 할퀴기 판정 2틱 전, 보스 발밑 · 보스 방향 |
| MordianSlam | impact | impact | 내려찍기 판정 순간, 보스 앞 5 |
| MordianShadowGate | sink / rise | static | 잠행·기믹 시작(사라짐·솟아오름), 소품 등장. 그림자 분신은 areaRadius 1 |
| MordianChainPull | throw / hold / wrap / break / wrapBreak | link·attached | 사슬 끌어당기기 (Fx.link 손 → 가슴) |
| MordianGraspHand | grasp | impact | 손아귀 판정 8틱 전, 플레이어 발밑 |
| MordianCurseBolt | projectile / impact | projectile·impact | 저주 화살 (Fx.fall 손 → 끝점 4틱), 판정 순간 착탄 |
| MordianBrand | mark / burst | attached·impact | 낙인 3초 (follow) → 폭발 판정 순간 |
| MordianForbiddenRing | forbidden_ring | static | 포효 0.8초(16틱), 보스 발밑 |
| MordianDarkRain | projectile / impact | projectile·impact | 유성 Fx.fall 18 위 → 지면 17틱, 판정 순간 착탄 |
| MordianChainSpin | spin | attached | chain_spin 0.6초(12틱), 보스 follow |
| MordianShadowDash | dash | attached | 돌진마다 dash 시작, 보스 follow |
| MordianChainBind | bind_hold / bind_slack / bind_taut / bind_break | link | ① 결박 (A 가슴 ↔ B 가슴), 9초마다 다시 잇기 |
| MordianRitualRune | draw1~5 / hold1~5 / erase1~5 / wrong1~5 | static | ② 룬 (번호 = 문양, 위치 i 에 문양 i+1). hold 는 Fx.held 로 지움 |
| MordianOfferingCircle | circle / circle3 / success / fail | static | ④ 마법진 (4초·3초 라운드), 판정 결과 |
| MordianSealBeam | beam / beam_blocked_hit / pillar_crack / beam_switch | link·attached·impact | ⑥ 광선 (보스 가슴 높이 → 기둥 면 or 막는 사람 가슴) |
| MordianReseal | reseal | static | ⑥ 성공 → sealed 모션과 동시 (5초), 보스 중심 |
