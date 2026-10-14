# 원소 반응 1차 (Claude, 2026-10-14)

브랜치: `claude/element-reaction-20261014` (기준: `claude/followup-fixes-20261013`)
상태: **코드만 작성, 빌드 안 됨.** 작업 환경에서 paper-api·Fabric·MythicMobs 의존성을 받을 수 없어
Gradle 빌드를 못 돌렸습니다. 순수 로직(`ReactionBook`, `ElementProtocol`)만 javac로 컴파일하고 시나리오 검사를 통과시켰습니다.
GPT 쪽에서 빌드·테스트를 부탁합니다.

## 무엇을 하나

- 마법이 적에게 피해를 넣는 데 성공하면(chahit / chabolt / chazone, 기존 `element=` 값 사용) 원소 표식이 3초 남는다.
- 표식이 살아 있을 때 **다른 원소**로 맞히면 반응 1회 → 표식 소멸 → 같은 대상은 3초 동안 다시 반응 안 함(그동안 맞으면 표식만 교체).
- 같은 원소는 시간만 갱신. 반응 효과가 다시 표식을 만들지 않음(`applying` 가드).
- 파티원끼리도 반응함 (표식은 대상 기준, 누가 남겼는지 무관).
- 반응은 **피해를 늘리지 않음** (오너: 전투 메인 서버 아님). 보스는 이동 방해(밀기·당기기·감속·경직·어그로 해제) 제외.

| 반응 | 효과 |
|---|---|
| 화염+물 증기 폭발 | 대상 주변 2블록 적 살짝 밀어냄 |
| 화염+바람 불꽃 회오리 | 시전자 3초 신속 I |
| 화염+대지 용암화 | 대상 3초 불붙음 (유일한 피해, 바닐라 화염) |
| 화염+빛 태양 플레어 | 대상 5초 발광 |
| 화염+어둠 잿불 낙인 | 8초 안에 처치되면 경험치 2배 |
| 물+바람 감전 | 1초 경직 (구속 X) |
| 물+대지 진흙 | 3초 구속 I |
| 물+빛 무지개 | 시전자·주변 4블록 아군 체력 +1 |
| 물+어둠 심연 | 주변 3블록 적을 대상 쪽으로 당김 |
| 바람+대지 모래바람 | 대상 3초 나약함 I |
| 바람+빛 섬광 | 주변 6블록 몬스터가 노리던 플레이어를 놓침 |
| 바람+어둠 공허 돌풍 | 대상 약한 넉백 |
| 대지+빛 수정 방패 | 시전자 5초 흡수 I |
| 대지+어둠 발 묶기 | 0.5초 이동 불가 |
| 빛+어둠 일식 | 8초 안에 처치되면 일반 드롭 한 번 더 (보스·플레이어 제외, config로 끌 수 있음) |

## 변경 파일

서버 `magic-mythic-paper`
- `ReactionBook.java` (신규, 순수 로직) / `Reactions.java` (신규, Bukkit 연결·효과·패킷)
- `HitMech`, `BoltMech`(strike·burst), `ZoneMech`(pulse·trap): `targets.hurt(...)` 성공 직후 `plugin.reactions().hit(...)` 한 줄
- `MagicMythicPlugin`: 생성·리스너 등록·`/마법미몹 새로고침`·`상태`에 연결, onDisable에서 close
- `config.yml`: `reactions:` 섹션 (enabled, mark-ticks, cooldown-ticks, eclipse-extra-drops, icon-range)
- `build.gradle`: `magic-codex-protocol`에서 **ElementProtocol.java 한 파일만** 소스로 포함, 버전 `...-reaction.1`
- 테스트 `ReactionBookTest.java`

공용 `magic-codex-protocol`
- `ElementProtocol.java` (신규): 채널 `magiccodex:element`, S2C 전용, 6~8바이트 프레임(Mark / Clear / Pop)

클라이언트 `magic-codex-fabric`
- `ElementMarkClient.java` (신규), `MagicCodexClient.initialize()`에 등록 한 줄
- `assets/magiccodex/textures/element/{fire,water,wind,earth,light,dark}.png` (256px, GPT 생성 아이콘 원본 그대로 잘라냄)

## 확인 부탁 / 주의

1. **빌드**: `Reactions`의 Paper API 사용 — `Bukkit.getCurrentTick()`, `Player#getListeningPluginChannels()`, `Mob#setTarget(null)`, sealed record 패턴 switch(Java 21).
2. **클래스 중복**: `ElementProtocol`이 MagicCodexBridge jar(프로토콜 전체 포함)와 MagicMythic jar에 둘 다 들어간다. 내용이 같고 각 플러그인이 자기 jar를 먼저 읽으므로 문제는 없어야 하지만, 로딩 경고가 뜨는지 확인 부탁.
3. **아이콘 그리기**: 월드 텍스처가 아니라 HUD 이미지로 그림. 몹 머리 위치를 `AimView.toScreen`으로 화면 좌표로 바꾸고 `HudTextureCache.drawCentered`로 PNG를 그대로 그림(다른 HUD 아이콘과 같은 경로). `AimView`는 `AimRenderer.render`가 매 프레임 캡처하므로 조준 중이 아니어도 유효해야 함 — 확인 부탁.
   - 32블록 이내, `player.canSee(entity)`일 때만, 최대 24개. 아이콘 둘레 링 = 남은 시간, 마지막 1초 깜빡임.
   - 반응 시 반응 이름이 1초간 떠오름 (CodexTypography hud_bold).
4. **부하**: 매 틱 작업 없음. 적중 시 맵 조회 1번 + 반응 시 반경 2~6블록 검색 1번. 30초마다 만료 정리. 패킷은 48블록 안, 채널 등록한 클라이언트에게만.
5. **아직 원소가 안 붙는 피해**: `chamark`(재의 낙인·폭발 쐐기·화염 검무 등)와 `chaworld companion`(운디네) 피해는 반응에 안 들어감. 2차에서 `tools/spells.py`가 이 줄에도 `element=`를 붙이고 `Marks`/`WorldMech`에서 hit 호출하면 됨.
6. **경제**: 일식 추가 드롭은 반응 쿨다운(3초)과 8초 제한이 있지만 농장형 반복이 가능하면 `eclipse-extra-drops: false`.
7. VFX(반응 이펙트)는 오너 결정으로 나중에 제작. 지금은 아이콘·이름 팝업만.
