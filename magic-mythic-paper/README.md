# ChAcademiaMagicMythic

차카데미 마법의 **게임 효과**(피해, 밀치기, 지대, 보호막, 작은 월드 유틸)를 MythicMobs 스킬에서 쓸 수 있게 해 주는 보조 플러그인.
마나·쿨타임·권한·대상 선택은 MagicCodexBridge, 시각효과는 PortableVFX 가 맡는다.

## 설치

1. `chacademia-magic-mythic-<버전>.jar` → `plugins/`
2. `mythicmobs/Skills/chacademi_spells.yml` → `plugins/MythicMobs/Skills/` (기존 `CHA_*` 마법 스킬 파일은 제거)
3. `server-config/spell-runtime.yml` → `plugins/MagicCodexBridge/spell-runtime.yml` (교체)
4. MagicCodexBridge 에 `docs/handoff/proposals/BRIDGE_CATALOG_GAMEPLAY_20261011.patch` 가 적용된 빌드 필요
5. 서버 재시작 (또는 `/mm reload` + `/마법관리 새로고침`)

## 마법 수정 방법

`tools/spells.py` 의 표가 원본이다. 고친 뒤 `python3 tools/spells.py` 로 YAML 두 개와 `SPELLS.md` 를 다시 만든다.
한 줄 = `(id, 스킬이름, 대상, 사거리, 기본피해, 마력계수, 유틸여부, [미몹 줄], 설명)`.

- 대상: `SELF` / `AIM`(바라보는 곳, 맞은 적이 있으면 그 적) / `ENTITY`(적 필수) / `AIR_ENTITY` / `BURNING_ENTITY`
- 피해: `base-damage + power-ratio × 마력` 이 스킬 변수 `damage` 로 들어오고, 각 메커닉의 `mult` 를 곱한다.
- 대상 없이 쓰는 줄은 Bridge 가 고른 대상(적 또는 조준 위치)에 적용된다. 시전자 중심은 `@self`.

## 명령어

- `/마법미몹 새로고침` — config.yml 다시 읽기
- `/마법미몹 상태` — 진행 중인 투사체·지대·보호막·표식·임시 블록 개수

## 메커닉 옵션

각 클래스 상단 주석에 전체 옵션이 있다 (`HitMech`, `BoltMech`, `ZoneMech`, `GuardMech`, `MarkMech`, `AllyMech`, `WorldMech`).
