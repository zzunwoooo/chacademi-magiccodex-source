# FreedDemon_Gimmick — 기믹 1(여섯 개의 고해)·2(지옥의 줄넘기) 연출.

- statue_truth (static, P = 진실 석상 발판 중심): 진실의 석상 — 석판에서 금홍색 빛기둥, 발판 마법진이 켜짐 (석상 reveal 모션과 함께, 약 2.5 s)
- statue_false (static, P = 거짓 석상 발판 중심): 거짓 석상이 무너질 때 — 검보라 파열 + 해골 + 잔해 (석상 crumble 모션과 함께)
- doom         (static, P = 플레이어 발): 기믹 실패 즉사 — 하늘에서 지옥불 기둥이 내리꽂힘
- whisper      (attached, 플레이어): 악마의 귓속말 — 머리 주위로 보라 연기와 눈이 맴돎 (거짓 정보를 줄 때, 약 1.8 s)
- lie          (static, P = 보스 머리 위, 모드가 보스 머리 높이로 놓음): 거짓말 신호 — 보라 눈 문양이 번쩍 (보스 eyes_lie 본을 켤 때 같이)
- rope_hit     (attached, 플레이어): 줄넘기 사슬에 맞음 — 사슬 불꽃 + 넘어지는 충격 (낙인 1중첩은 brand_stack 이 담당)
- rope_spark   (static, P = 사슬이 바닥을 긁는 지점, loop): 사슬 끝이 바닥을 긁는 불티 — 모드가 사슬 끝 위치로 매 틱 옮기거나 회전마다 새로 켬

## 공통 (해방된 악마 레이드)
- 보스 키 약 6 m. static 의 정면(+Z) = 서버가 넘기는 시전자(보스) 수평 방향, P = 각 시스템 설명의 지점.
- 빨간 예고 장판은 모드의 예고 시스템이 그린다 — 이 묶음에는 없음. 분출·폭발류는 예고가 끝나는 판정 순간에 시작한다.
- loop 시스템(firewall, dash, soulchain, brand_stack, rope_spark, shadow, taunt, sulfur)은 모드가 정지 신호로 끈다.
- 텍스처는 같은 레이드의 다른 묶음과 공유하는 이름(T_*)이 많다 — 묶음마다 필요한 것만 들어 있다.




## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
