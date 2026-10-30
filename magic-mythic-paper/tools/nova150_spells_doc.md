# 신규 마법 150종 (301~450) 구현표 (Claude, 2026-10-31)

기획: 프로젝트 문서 `차카데미 신규 마법 150종 기획안` · VFX: PC `차카데미 vfx_Claude\<번들>` (150팩, 목록 `_신규마법150_목록.html`) · 오너: "실제 기능 개발도 하고, 모험 일지도 다 적어봐".
마나·쿨·피해·반경은 **임시값** (밸런스 미확정). 발견 조건은 비워 둠 (나중에 한꺼번에). 모험일지 = 도감 `research` (연구 기록).

자동 생성: `python3 magic-mythic-paper/tools/nova150_spells.py` (이 문서 / default-spells / mana-spells / spell-catalog / spell-bindings) →
`python3 magic-mythic-paper/tools/spells.py` (미몹 스킬 yml / spell-runtime.yml / SPELLS.md / spell-targeting.json).
게임 기능: `chanova{type=<id>;pack=<번들 소문자>}` = `NovaMech` → `Nova150Wind / Fire / Water / Earth / Light / Dark`, 도우미 `Nova`.

## 구현표

{{TABLE}}
