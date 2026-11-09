# 엘리시아 레이드 VFX — 서버 연동 표 (2026-11-09)

코드 = `claude:<팩 소문자>/<시스템>`. 예고(빨간 장판) 없음 — 모드 예고(Telegraphs)가 그림.

| 팩 | 시스템 | 역할 | 붙이는 곳 (ElysiaBoss.java) |
|---|---|---|---|
| Elysia_LightSpear | projectile / impact | 투사체 / 착탄 | 빛의 창 (Fx.fall → 판정 때 impact) |
| Elysia_Judgment | judgment | static | 낙하 패턴(판정 7틱 전 시작, 0.35초 낙하) · 가호의 빛 · 보스 중앙 이동 |
| Belzar_Hellfire | hellfire | static | 지옥불 패턴 · 그림자 몰수 · 계약 파기 무너지는 바깥 |
| Elysia_Sanctuary | begin / sustain / end | static (sustain = Fx.held, 멈추면 end) | 치유의 성역 · 가호 금빛 원 · 계시 답 원 |
| Elysia_Blessing | begin / sustain / end | attached (플레이어) | 축복 수락 ~ 정체 폭로 |
| Elysia_LightBarrier | begin / sustain / end | static | 축복의 제안 빛의 성벽 |
| Belzar_DebtShield | begin / sustain / end | attached (벨자르) | 빌린 힘의 반환 보호막 |
| Elysia_LightCore | hit / break | static | 빛의 핵 타격·파괴, 광휘 패턴, 보호막 피격 |
| Elysia_Hymn | ring / wrong | static (석주 꼭대기 +2.8) | 계시 찬송·정답 / 오답, 아기천사 찬송(미몹 vfx=) |
| Elysia_Salvation | revive / seize / drain | static / attached / attached 반복 | 구원 부활 · 목숨 회수 · 회복 불가 12초 |
| Elysia_DebtMark | gain / pay / chain | attached / attached / link | 빚 +1 · 빚 -1 · 빚의 사슬(플레이어 가슴 ↔ 벨자르 가슴, 9초마다 다시 이음) |
| Elysia_Reveal | reveal | static | 50% 정체 폭로 (2.8초 폭발에 벨자르 교체) |
| Belzar_Collection | collect | static | 수금 판정 순간 · 망령 사슬(미몹 vfx=) |
| Belzar_Strikes | claw / tail | static (정면 = 보스 방향) | 할퀴기(판정 2틱 전) · 꼬리 휩쓸기 |
| Belzar_ContractBurn | seal / burn | static | 봉인진 하나 켜질 때 · 장부 불태움(2.2초 빛) |

주의: phased 팩(begin/sustain/end)은 서버가 세 시스템을 따로 시작한다 (sustain 은 멈출 때까지 반복). PortableVFX 가 sustain 단독 재생을 받는지 확인할 것.
원본: specs/*.js (lib.js 를 앞에 붙여 export, `//@base` = 같은 본문 공유), gen_elysia.py (텍스처), build.py (번들 + client_compat fix/check).
