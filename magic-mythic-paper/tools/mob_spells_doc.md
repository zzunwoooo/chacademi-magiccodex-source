# 몹 기술 습득 마법 300~323 (2026-10-19, Claude)

오너 요청: 오늘 만든 몹 스킬 중 기존 마법(1~299)과 겹치지 않는 것을 플레이어 마법으로. **해당 몹을 처치하면 5% 확률로 습득.**
브랜치 `claude/mob-telegraph-20261019`. 원본 표: `magic-mythic-paper/tools/mob_spells.py` (`python3 tools/mob_spells.py` → 아래 파일들, `python3 tools/spells.py` → 미몹 스킬·Bridge 라우트·조준 미리보기).

## 0. 먼저 읽을 것 (GPT)

1. **빌드·실게임 안 함.** 새 메커닉 `chamobshot`(MobShotMech), `chatelegraph warn=false / blink / tpme / hits=enemies` 를 플레이어 마법에도 그대로 씀. 호스팅에서 빌드·시험 필요.
2. **켜진 채로 들어감 (2026-10-19 밤, 오너: "실제 마법 기능 구현").** `mana-spells.yml` 300~323 `enabled: true`, `spell-catalog.yml` `reviewed/enabled: true`.
   - 모습은 **몹 기술 VFX 팩**(PC `차카데미 vfx_Claude\몹스킬\<몹>\기본기\*.zip`, 큰 기술 팩, `Special*` 팩)을 메커닉이 직접 재생(`vfx=pack/system` → ReactionVfx·GameplayVisual). 카탈로그는 시전만 받아 줌:
     `spell-bindings.yml` 에 `trajectory: server-events` + `sequence.sustainStart: 0` + `holdTicks: 60`, phase 1개(`trigger: external:mobskill` — 아무도 안 보내는 이벤트, effect = 그 팩의 맞은 자리 시스템). 그래서 카탈로그가 같은 이펙트를 두 번 그리지 않음.
   - **배포 순서 주의:** 이 phase 의 팩이 카탈로그 "준비 완료" 조건에 들어감 → **몹 기술 VFX 팩을 모든 클라이언트에 먼저(같은 릴리스로) 배포**해야 함. 팩 없이 이 카탈로그를 올리면 다른 카탈로그 마법까지 막힐 수 있음(예전 101~167 때와 같은 문제). 팩 배포가 늦으면 300~323 만 `enabled: false` 로 두고 올릴 것.
3. **습득:** `magic-mythic-paper` `DiscoveryHooks` 가 `CA_Wild_*` 몹을 플레이어가 처치하면(`getKiller()`, 마법이든 근접이든) `kill.mob.<가족>` 을 1 올린다(색·이로치 무관, 가족 표 = `DiscoveryHooks.MOB_FAMILIES`). `discoveries.yml` 은 `kill.mob.<가족>: 1` + `chance: 0.05` → **처치할 때마다 5% 판정**. 같은 몹에서 마법 두 개(봉인의 술사·팬텀·해골술사·페가수스)는 각각 따로 5%.
   - 운영 `plugins/MagicDiscovery/discoveries.yml` 은 JAR 로 안 바뀜 → 병합 필요. 아이콘 `magiccodex:textures/spells/mob_skill/<id>.png` 는 아직 없음(도감 `icon: ''`).
4. **수치 (2026-10-19 밤 재조정, 1~167 기준에 맞춤).** 피해 = Bridge `base-damage + power-ratio × 마력` (spell-runtime.yml), **투사체·연속 타격은 한 발/한 번마다**.
   - 기준으로 쓴 기존 마법: 파이어 볼 2+0.25 / 5 / 4초 · 윈드 커터 4+0.35 / 30 / 7초 · 서리 파열 6+0.45 / 55 / 15초 · 수압해머 9+0.6 / 100 / 15초 · 퍼붓는 화살 (3+0.2)×5 / 200 / 20초 · 대지 성형 4+0.3 / 120 / 25초 · 지진 6+0.45 / 200 / 45초 · 심해의 압력(제어) 5+0.35 / 420 / 90초.
   - 규칙: 계수 ≈ 기본 피해 × 0.075 (기존과 같은 비율). 여러 발 = 한 발 피해를 낮추고 마나·쿨을 올림. 범위 제어(무장 해제·수면·묶음·끌어오기·중력)는 피해를 낮추고 마나·쿨을 크게(150~200 / 30~45초). 수면·연막은 피해 0 (피해를 주면 수면이 깸).
   - 처음 넣은 값(30~45 마나, 7~20초)은 같은 효과의 기존 마법보다 3~5배 쌌음 → 위 표대로 올림. 여전히 밸런스 검토 전 값.
5. 다른 서버 YAML(`spell-runtime.yml`, `chacademi_spells.yml`, `mana-spells.yml`)도 운영 폴더에 직접 병합.
6. `hunting_pounce`·`roar_leap` 은 MythicMobs 기본 `leap` 을 씀 — 빗자루 비행 중 막는 SelfMech 규칙이 안 걸림. 필요하면 확인.

- `chamobshot` 플레이어 시전: 조준한 대상이 없으면(AIM) 유도·공전 후 방향을 **조준 지점**으로 (1.5블록 안에 오면 유도 끝). 예전엔 대상이 없으면 유도가 안 돼 퍼진 탄이 그대로 빗나갔음.

## 1. 마법표

{{TABLE}}

## 2. 기존 마법과 겹침 확인 (뺀 것)

- 드래곤·하늘뱀 브레스(64 드래곤 브레스), 땅 울림·대지 짓밟기(105 지진), 서리 진(78 서리 파열), 뿔 광선·빙핵 레이저(147·62), 깃털 탄막(119 가시 씨앗), 꼬리 회전(31 토네이도 스핀), 덩굴 채찍(42 화염 채찍), 바위·뼈화살 연사(99·28), 소환(154 소환장·104 그놈).
- 168~299 대조 후 추가로 뺀 것: 현혹(195 공포의 종), 나선 휘감기(205 소용돌이 중심), 별 낙하(198 공허의 입), 독약병(303 독 뱉기와 중복).
- 비슷하지만 남긴 것: 봉인진(186 손아귀 풀기는 1체, 이건 범위), 요정 가루(175 졸음은 1체), 끌어오기(172 그림자 교환은 자리 바꾸기), 연막 은신(257 모래 눈속임은 실명만).

## 3. 바뀐 파일

| 파일 | 내용 |
|---|---|
| `magic-mythic-paper/tools/mob_spells.py` (새) | 24종 원본 표 + 생성기 |
| `magic-mythic-paper/tools/spells.py` | `S += mob_spells.routes()`, 조준 미리보기에 chatelegraph/chamobshot 모양 |
| `magic-mythic-paper/mythicmobs/Skills/chacademi_spells.yml`, `server-config/spell-runtime.yml`, `SPELLS.md` | 재생성 (CHA_<id> 24개, 라우트 24개) |
| `magic-mythic-paper/.../DiscoveryHooks.java` | `kill.mob.<가족>` 신호 + `MOB_FAMILIES` |
| `magic-codex-fabric/.../default-spells/300~323_*.yml`, `index.txt`, `known-hashes.tsv` | 도감 항목 (설명·획득 문구·연구 일지·마나·쿨·`마법 <id>`) |
| `magic-codex-fabric/.../spell-targeting.json` | 조준 미리보기 재생성 |
| `magic-codex-paper/src/main/resources/mana-spells.yml` | 300~323 (enabled true) |
| `portable-vfx-runtime/paper/src/main/resources/spell-catalog.yml`, `spell-bindings.yml` | 300~323 (reviewed/enabled true, server-events 자리 phase) |
| `magic-discovery-paper/src/main/resources/discoveries.yml` | 300~323 획득 조건 (`kill.mob.<가족>` ×1, 5%) |
