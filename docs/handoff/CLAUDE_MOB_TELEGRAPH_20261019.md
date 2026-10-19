# Claude 2026-10-19 — 몹 예고 장판(원형·직선·부채꼴) + 첫 몹 스킬 "블레이즈 화염 기둥" (빌드 안 함)

브랜치 `claude/mob-telegraph-20261019` (기반 `claude/status-hud-polish-20261019` = c5a2ccf + HUD v4). 노트북에서 Gradle 미실행 — 순수 Java 테스트만 로컬 확인.

## 무엇
- 적 범위 공격 전 **빨간 예고 장판**: 반투명 빨강 채움 + 밝은 빨강 외곽선(+옅은 글로우). 모양 3종 = 원형 / 직선 / 부채꼴 (사장님 레퍼런스 시트).
- 서버가 보낸 숫자 그대로 그리고, 예고가 끝나면 **같은 숫자로 판정** (`TelegraphProtocol.inside`) → 보이는 범위 = 맞는 범위.
- 첫 스킬: 블레이즈 `CHA_MOB_blaze_pillar` — 대상 발밑 반경 3 원형, 예고 1초 → 불기둥, 피해 6 + 화상 3초. VFX 팩 `BlazePillar` (claude:blazepillar/erupt).

## 파일
- protocol `TelegraphProtocol` — 채널 `magiccodex:telegraph`, v1 SHOW/CLEAR, `inside()` 판정(여유 = 대상 몸 절반 폭).
- magic-mythic-paper
  - `TelegraphMech` = `chatelegraph{shape;at;r;length;width;angle;ticks;flat|mult;element;ignite;status;hits;height;vfx;vfxlead;vfxref;tint;cancel}` (MagicMythicPlugin 스위치 등록).
  - `Telegraphs` = 송신(64블록, `telegraph.range`)·타이머·시전자 사망 시 취소(CLEAR)·지면 높이 스냅. config `telegraph:` 섹션 추가.
  - `ReactionVfx.effect(id, at, scale, yaw, ticks, tint)` 추가 (기존 반응 VFX 재생도 이걸 거침, 동작 동일). `Reactions.vfx()` 접근자.
  - `TelegraphCommand`: `/마법미몹 예고 <원형|직선|부채꼴|전부|화염기둥> [초]` — 피해 없음, 보이기만. 화염기둥 = 예고 + BlazePillar VFX.
  - `mythicmobs/Skills/chacademi_mob_skills.yml` (새 파일, 손 관리) — 블레이즈 스킬. 몹에 붙이는 줄은 파일 맨 위 주석.
- magic-codex-fabric: `TelegraphClient`(수신·렌더, AFTER_ENTITIES, aim 텍스처 band/glow 재사용), `TelegraphGeometry`(순수 수학), MagicCodexClient 초기화 1줄.
- 테스트: `TelegraphProtocolTest`, `TelegraphGeometryTest` (fabric), `TelegraphMechTest` (mythic).

## GPT 할 일
1. 빌드: protocol → magic-mythic-paper → magic-codex-fabric. 테스트 통과 확인.
2. VFX 팩: 사장님 PC `C:\Users\matil\Desktop\차카데미 vfx_Claude\BlazePillar\BlazePillar.zip` → PortableVFX 팩 폴더 `blazepillar` (효과 id `claude:blazepillar/erupt`, static 1시스템, areaRadius reference 3). client_compat check OK, 고유 텍스처 RGBA 3.7 MiB.
3. MythicMobs: `chacademi_mob_skills.yml` 를 Skills 폴더에, `ChAcademiaWildBlaze` 몹에 `- skill{s=CHA_MOB_blaze_pillar} @target ~onTimer:80 0.6` 추가 → `/mm reload`.
4. 확인: `/마법미몹 예고 전부` (세 모양), `/마법미몹 예고 화염기둥`, 서바이벌로 블레이즈 앞에서 장판 안/밖 판정.

## 주의
- 몹 스킬은 기본 `hits=players` (플레이어만 맞음). 펫/몹도 맞게 하려면 `hits=enemies`.
- WorldGuard `magic` 금지 구역에선 시전자·대상 모두 막힘(기존 규칙 그대로).
- 클라이언트 렌더: 레이어 버퍼는 레이어마다 한 번만 가져와 끝까지 씀 (중간에 다른 레이어를 가져오면 앞 버퍼가 닫힘).
- 평평한 판 하나로 그림(시작 높이). 계단·언덕에선 일부가 땅에 묻히거나 뜰 수 있음 — 필요하면 다음에 지형 따라 나누기.
- 몹 마나는 아직 없음(사장님 "마나는 일단 놔둬") — 쿨다운만.
