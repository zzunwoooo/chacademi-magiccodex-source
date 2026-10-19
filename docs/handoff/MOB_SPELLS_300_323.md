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

| # | id | 이름 | 원소 | 습득 (처치 시 5%) | 대상 | 마나 / 쿨 | 피해 (base + 마력×) | 효과 | 원본 몹 기술 VFX |
|---|---|---|---|---|---|---|---|---|---|
| 300 | `fox_orbit` | 여우불 공전 | fire | 키츠네 (`kill.mob.kitsune`) | AIM 20 | 90 / 14초 | 3 + 0.22 | 여우불 3개가 0.8초 몸 둘레를 돌다가 조준한 적에게 휘어 날아감 (불씨 2초) | `kitsunefox/hit_normal`, `kitsunefox/shot_normal` |
| 301 | `curving_plumes` | 곡선 불깃털 | fire | 피닉스 (`kill.mob.phoenix`) | AIM 20 | 90 / 14초 | 3 + 0.22 | 좌우로 퍼졌다가 휘어 들어오는 불깃털 3발 (불씨 2초) | `phoenixfeather/hit_flame`, `phoenixfeather/shot_flame` |
| 302 | `crystal_orbit` | 결정 공전탄 | water | 빙핵의 수호자 (`kill.mob.glacialcore`) | SELF 12 | 120 / 20초 | 3 + 0.22 | 얼음 결정 8개가 1초 몸 둘레를 돈 뒤 8방향으로 발사, 맞으면 1.5초 둔화 | `glacialshards/hit_glacial`, `glacialshards/shot_glacial` |
| 303 | `venom_spit` | 독 뱉기 | earth | 바실리스크 (`kill.mob.basilisk`) | AIM 18 | 70 / 14초 | 5 + 0.35 | 포물선 독 덩어리, 떨어진 자리 반경 2.5 피해 + 중독 3초 (웅덩이는 VFX) | `basiliskspit/hit_wild`, `basiliskspit/shot_wild` |
| 304 | `smoke_veil` | 연막 은신 | earth | 약초 도둑 (`kill.mob.herbthief`) | SELF 6 | 80 / 30초 | 0 + 0 | 주변 5블록 적 실명 2초 + 자신 2초 투명 | `smokebomb/smoke` |
| 305 | `domain_stomp` | 영역 선포 | light | 영록 (`kill.mob.stag`) | SELF 6 | 120 / 25초 | 5 + 0.4 | 주변 6블록 적을 위로 튕겨 올림 | `domainseal/domain_heavenly` |
| 306 | `seal_circle` | 봉인진 | light | 봉인의 술사 (`kill.mob.warden_mage`) | AIM 16 | 150 / 30초 | 5 + 0.35 | 조준 지점 반경 4, 0.8초 뒤 봉인 — 무장 해제 1.5초 | `sealcircle/seal` |
| 307 | `seal_talismans` | 봉인 부적 | light | 봉인의 술사 (`kill.mob.warden_mage`) | AIM 20 | 60 / 10초 | 2.5 + 0.2 | 곡선 유도 부적 3장 | `sealtalisman/hit_seal`, `sealtalisman/shot_seal` |
| 308 | `shield_slam` | 방패 내려찍기 | light | 봉인의 기사 (`kill.mob.warden_knight`) | SELF 5 | 80 / 15초 | 7 + 0.5 | 앞 부채꼴 반경 4.5 · 120° 강타 + 기절 0.5초 | `sealshield/shield` |
| 309 | `chain_arrow` | 사슬 화살 | light | 봉인의 궁수 (`kill.mob.warden_archer`) | AIM 22 | 30 / 7초 | 4 + 0.35 | 빠른 사슬 화살, 맞으면 1.5초 둔화 | `chainarrow/hit_chain`, `chainarrow/shot_chain` |
| 310 | `mana_drain` | 마나 흡수 | dark | 팬텀 (`kill.mob.phantom`) | AIM 14 | 120 / 25초 | 4 + 0.3 | 조준 지점 반경 4, 0.6초 뒤 피해 + 마나 연소 2초 | `phantomdrain/drain_ender` |
| 311 | `soul_scythe` | 영혼 낫 | dark | 팬텀 (`kill.mob.phantom`) | SELF 5 | 60 / 12초 | 6 + 0.45 | 앞 부채꼴 반경 5 · 150° 베기 | `phantomscythe/reap_ender` |
| 312 | `oblivion_orbs` | 망각의 탄환 | dark | 망각의 해골술사 (`kill.mob.skeleton_mage`) | AIM 20 | 70 / 12초 | 3 + 0.2 | 느리게 날아가는 탄 3발, 맞으면 1.5초 둔화 | `skelorb/hit`, `skelorb/shot` |
| 313 | `bone_grasp` | 뼈의 손아귀 | dark | 망각의 해골술사 (`kill.mob.skeleton_mage`) | AIM 15 | 150 / 30초 | 4 + 0.3 | 조준 지점 반경 4, 0.6초 뒤 묶음 1초 + 시전자 쪽으로 끌어옴 | `bonegrasp/grasp` |
| 314 | `shadow_leap` | 그림자 도약 | dark | 키츠네 (`kill.mob.kitsune`) | ENTITY 14 | 120 / 25초 | 6 + 0.45 | 대상 옆으로 순간이동해 반경 2.5 타격 (2칸 빈 공간 필요) | `specialkitsune/in_normal`, `specialkitsune/out_normal` |
| 315 | `binding_pull` | 끌어오기 | wind | 하늘뱀 (`kill.mob.sky_serpent`) | ENTITY 14 | 150 / 30초 | 3 + 0.2 | 대상을 내 앞 1.6블록으로 순간이동시키고 1초 둔화 | `specialserpent/in_jade`, `specialserpent/mark_jade` |
| 316 | `fairy_dust` | 요정 가루 | earth | 요정 (`kill.mob.fairy`) | SELF 6 | 200 / 45초 | 0 + 0 | 주변 5블록 적 수면 2초 (피해 받으면 깸) | `fairydust/dust_flower` |
| 317 | `moon_wind` | 반달 바람날 | wind | 페가수스 (`kill.mob.pegasus`) | SELF 10 | 60 / 12초 | 5 + 0.4 | 앞 직선 10 · 폭 3, 반달 바람날 2개가 밀며 지나감 | `pegasuscrescent/moon` |
| 318 | `gale_wings` | 돌풍 날갯짓 | wind | 페가수스 (`kill.mob.pegasus`) | SELF 6 | 110 / 25초 | 4 + 0.3 | 앞 반원 반경 6 밀치기 + 중력 1초 | `galewings/gale` |
| 319 | `feather_storm` | 깃털 폭풍 | wind | 히포그리프 (`kill.mob.hippogryph`) | SELF 5 | 90 / 16초 | 6 + 0.45 | 주변 5블록 깃털 칼날 폭풍 | `featherstorm/fstorm_common` |
| 320 | `hunting_pounce` | 사냥 도약 | earth | 검치호 (`kill.mob.sabertooth`) | ENTITY 12 | 110 / 22초 | 6 + 0.45 | 대상에게 뛰어들어 착지 반경 3 피해 + 묶음 1초 | `sabertoothpounce/pounce_arctic` |
| 321 | `roar_leap` | 포효 도약 | wind | 만티코어 (`kill.mob.manticore`) | ENTITY 14 | 130 / 25초 | 6 + 0.45 | 대상에게 뛰어들어 착지하며 포효, 반경 4 피해 + 공포 1초 | `manticoreroar/roar_aether` |
| 322 | `gravity_slam` | 중력 강타 | earth | 고대 골렘 (`kill.mob.golem`) | SELF 5 | 180 / 35초 | 7 + 0.5 | 주변 5블록 강타 + 중력 2초 | `gravityslam/gravity_arcane` |
| 323 | `tail_spikes` | 꼬리 가시 | earth | 와이번 (`kill.mob.wyvern`) | AIM 16 | 110 / 20초 | 3 + 0.22 | 조준 지점 반경 3에 0.4초 간격 가시 3번 | `wyvernspikes/spikes_green` |

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
