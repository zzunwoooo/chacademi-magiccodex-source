# ChacaNPC NPC별 추천 버튼 (2026-10-23)

- 브랜치 `claude/npc-buttons-20261023` (← `claude/npc-model-20261021` @ e00ab67, NPC 3D 외형 포함). `claude/story-steps-20261020` 의 ChacaNPC 한 줄(`NpcManager.onRightClick ignoreCancelled`)과 함께 통합할 것.
- 캐릭터 yml `buttons:` (3개까지, id `[a-z0-9_-]{1,32}`, 같은 NPC 안 중복 불가) → 그 NPC 만 이 버튼. 없으면 `config.yml dialogue.buttons` 공통.
- 바뀐 곳: `CharacterSheet`(+`buttons`, `buttonsOr`), `CharacterRepository.buttons`(읽기·검사·경고), `DialogueService` 4곳(창 열기 2 · 버튼 입력 · 미리 만들기 warmup) → `c.buttonsOr(st.buttons)`.
- 미리 만드는 대답(button-cache)은 원래 NPC id·버튼 id 별 키라 그대로 동작. NPC 마다 버튼 글이 달라도 비용 구조는 같음 (버튼 3개 × 변형 수).
- 콘텐츠: chacademi-server `claude/story-ch1-20261020` `npc/ChacaNPC/npcs/` 85명 (버튼 추가) → 학교 서버 `plugins/ChacaNPC/npcs/`, 초상화 `resourcepacks/chacademia_npc` (95장) → 런처.
- 확인 못 함: 빌드. 실서버에서 NPC 별 버튼 표시·눌렀을 때 대답·캐시 대답.
