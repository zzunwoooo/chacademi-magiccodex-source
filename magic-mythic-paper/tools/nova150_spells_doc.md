# 신규 마법 150종 (301~450) 구현표 (Claude, 2026-10-31)

기획: 프로젝트 문서 `차카데미 신규 마법 150종 기획안` · VFX: PC `차카데미 vfx_Claude\<번들>` (150팩, 목록 `_신규마법150_목록.html`) · 오너: "실제 기능 개발도 하고, 모험 일지도 다 적어봐" → "연구일지 쓰려면 발견조건도 해야겠네 발견조건도 ㄱㄱ".
마나·쿨·피해·반경은 **임시값** (밸런스 미확정). 모험일지 = 도감 `research` (연구 기록), 각 마법의 발견 조건 장면으로 씀.

자동 생성: `python3 magic-mythic-paper/tools/nova150_spells.py` (이 문서 / default-spells / mana-spells / spell-catalog / spell-bindings / **discoveries.yml**) →
`python3 magic-mythic-paper/tools/spells.py` (미몹 스킬 yml / spell-runtime.yml / SPELLS.md / spell-targeting.json).
게임 기능: `chanova{type=<id>;pack=<번들 소문자>}` = `NovaMech` → `Nova150Wind / Fire / Water / Earth / Light / Dark`, 도우미 `Nova`.

## 발견 조건 (tools/nova150/discovery.py → magic-discovery-paper discoveries.yml 끝 구간)

새 리스너 없이 서버가 이미 세는 카운터만 씀: `cast.<마법>` · `mana.<마법>` · `kill.spell.<마법>` (그 마법 피해 후 3초 안 처치) · `kill.mob.<CA_Wild_ 계열>` · `state.circle` · 기존 생활 카운터 + `prerequisites`.

| 등급 | 조건 흐름 |
|---|---|
| 초급 | 생활 행동(꽃 심기·대장간·낚시·눈덩이…) 또는 기존 같은 원소 마법으로 처치/시전 |
| 중급 | 같은 원소 **신규 초급** 마법으로 처치 (+ 생활 행동/커스텀 몹) |
| 고급 | 5서클 + 신규 중급 마법으로 처치 + 커스텀 몹 |
| 비전 | 7서클 + 신규 고급 마법에 마나 사용 + 강한 커스텀 몹 |
| 궁극 | 9서클 + 같은 원소 비전 2종 습득 + 보스급 몹 / 두 번째 궁극은 첫 궁극 + 나머지 비전 습득 |

같이 고친 것: `Nova.later / loop / Shot` 이 시전 때의 SpellTrace(어느 마법인지)를 이어받게 함. 전에는 지연 피해·투사체 처치가 `kill.spell.*` 에 안 잡혔음 (기존 nova 325~387 의 처치 조건도 같이 고쳐짐).
아이콘: discoveries.yml 은 `magiccodex:textures/spells/nova150/<id>.png` 를 가리킴 — 아직 그림 없음 (클라이언트는 없으면 원소 문장으로 대체). /cicon 으로 150장 만들면 됨.

## 구현표

{{TABLE}}
