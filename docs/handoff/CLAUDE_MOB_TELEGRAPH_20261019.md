# Claude 2026-10-19 — 몹 예고 장판(원형·직선·부채꼴) + 첫 몹 스킬 "봉인의 술사 봉인진" (빌드 안 함)

브랜치 `claude/mob-telegraph-20261019` (기반 `claude/status-hud-polish-20261019` = c5a2ccf + HUD v4). 노트북에서 Gradle 미실행 — 순수 Java 테스트만 로컬 확인.

## 무엇
- 적 범위 공격 전 **빨간 예고 장판**: 반투명 빨강 채움 + 밝은 빨강 외곽선(+옅은 글로우). 모양 3종 = 원형 / 직선 / 부채꼴 (사장님 레퍼런스 시트).
- 서버가 보낸 숫자 그대로 그리고, 예고가 끝나면 **같은 숫자로 판정** (`TelegraphProtocol.inside`) → 보이는 범위 = 맞는 범위.
- 첫 스킬: 봉인의 술사(CA_Wild_mf_warden_mage, 커스텀 미몹) `CHA_MOB_seal_circle` — 대상 발밑 반경 4 원형, 예고 1.2초 → 봉인 발동, 피해 4 + 무장해제 1.5초. 이로치(`_shiny`)는 `CHA_MOB_seal_circle_shiny` (VFX만 루비색).
  VFX 팩 `SealCircle`: `claude:sealcircle/seal` (청록·상아, 몹 외형색) / `claude:sealcircle/seal_ruby` (이로치 루비·금). 같은 텍스처, 고유 RGBA 2.8 MiB.
  (처음 만든 블레이즈 BlazePillar 팩은 사장님 지시로 미사용 — 바닐라 기반 몹이라서.)

## 파일
- protocol `TelegraphProtocol` — 채널 `magiccodex:telegraph`, v1 SHOW/CLEAR, `inside()` 판정(여유 = 대상 몸 절반 폭).
- magic-mythic-paper
  - `TelegraphMech` = `chatelegraph{shape;at;r;length;width;angle;ticks;flat|mult;element;ignite;status;hits;height;vfx;vfxlead;vfxref;tint;cancel}` (MagicMythicPlugin 스위치 등록).
  - `Telegraphs` = 송신(64블록, `telegraph.range`)·타이머·시전자 사망 시 취소(CLEAR)·지면 높이 스냅. config `telegraph:` 섹션 추가.
  - `ReactionVfx.effect(id, at, scale, yaw, ticks, tint)` 추가 (기존 반응 VFX 재생도 이걸 거침, 동작 동일). `Reactions.vfx()` 접근자.
  - `TelegraphCommand`: `/마법미몹 예고 <원형|직선|부채꼴|전부|화염기둥> [초]` — 피해 없음, 보이기만. 봉인진/봉인진이로치 = 예고 1.2초 + SealCircle VFX.
  - `mythicmobs/Skills/chacademi_mob_skills.yml` (새 파일, 손 관리) — 봉인의 술사 스킬 2개. 몹에 붙이는 줄은 파일 맨 위 주석.
- magic-codex-fabric: `TelegraphClient`(수신·렌더, AFTER_ENTITIES, aim 텍스처 band/glow 재사용), `TelegraphGeometry`(순수 수학), MagicCodexClient 초기화 1줄.
- 테스트: `TelegraphProtocolTest`, `TelegraphGeometryTest` (fabric), `TelegraphMechTest` (mythic).

## GPT 할 일
1. 빌드: protocol → magic-mythic-paper → magic-codex-fabric. 테스트 통과 확인.
2. VFX 팩: 사장님 PC `C:\Users\matil\Desktop\차카데미 vfx_Claude\SealCircle\SealCircle.zip` → PortableVFX 팩 폴더 `sealcircle` (static 2시스템 seal / seal_ruby, areaRadius reference 4). client_compat check OK.
3. MythicMobs: `chacademi_mob_skills.yml` 를 Skills 폴더에, `chacademia_custom.yml` 의 `CA_Wild_mf_warden_mage` / `_shiny` Skills 에 파일 맨 위 주석의 줄 추가 → `/mm reload`.
4. 확인: `/마법미몹 예고 전부` (세 모양), `/마법미몹 예고 봉인진`, `/마법미몹 예고 봉인진이로치`, 서바이벌로 봉인의 술사 앞에서 장판 안/밖 판정.
5. 시전 모션 `seal_cast` (1.6초, 1.12초 내려찍기·1.2초 발동): 사장님 PC `C:\Users\matil\Desktop\차카데미 vfx_Claude\몹스킬\봉인의 술사 - 봉인진\model\ca_mf_warden_mage(_s).bbmodel` 로 ModelEngine blueprints 의 기존 파일 교체 → `/meg reload` + 리소스팩 재생성. 기존 애니(idle/walk/attack/tentacle/death/ca_hurt)는 그대로.
6. 한 폴더 모음: `...\몹스킬\봉인의 술사 - 봉인진\` (model / mythicmobs / vfx / preview / source + README.md). 몹 파일은 Skills 에 한 줄만 추가(참고본 mobs_mf_warden_mage.yml).

## 주의
- 서버 부담: 예고 1개당 패킷 1번(~60바이트, 64블록 안 플레이어만) + 지연 작업 1개 + 판정 때 엔티티 검색 1번. 매 틱 도는 작업 없음(시전자 사망은 사망 이벤트로 취소). 그리는 건 전부 클라이언트.
- 몹 스킬은 기본 `hits=players` (플레이어만 맞음). 펫/몹도 맞게 하려면 `hits=enemies`.
- WorldGuard `magic` 금지 구역에선 시전자·대상 모두 막힘(기존 규칙 그대로).
- 클라이언트 렌더: 레이어 버퍼는 레이어마다 한 번만 가져와 끝까지 씀 (중간에 다른 레이어를 가져오면 앞 버퍼가 닫힘).
- 평평한 판 하나로 그림(시작 높이). 계단·언덕에선 일부가 땅에 묻히거나 뜰 수 있음 — 필요하면 다음에 지형 따라 나누기.
- 몹 마나는 아직 없음(사장님 "마나는 일단 놔둬") — 쿨다운만.

## 추가 (2026-10-19 오후) — 고위 몹 2호: 빙핵의 수호자 "빙핵 레이저" (직선 예고 첫 사용)
- 스킬 `CHA_MOB_frost_laser` / `_shiny` (chacademi_mob_skills.yml): 시전자 앞 직선 길이 16 · 폭 2, 예고 1.2초 → 관통 광선, 피해 8 + 둔화 2초(40%). 쿨 12초.
- VFX 팩 `FrostLaser` → 폴더 `frostlaser`: `laser` (흰·라벤더·보라, 모델 색) / `laser_obsidian` (이로치 흑요·은백·금). static, 시전자 yaw 기준 +Z. client_compat OK, 고유 RGBA 2.6 MiB.
- 모션 `frost_laser` (2.0초): `ca_glacialcore(_s).bbmodel` 에 추가. 기존 skill4 의 날개 자세·봉인 문양 뼈를 재사용, 모델 레이저 뼈는 숨김(광선은 VFX).
- 모음 폴더: 사장님 PC `차카데미 vfx_Claude\몹스킬\빙핵의 수호자 - 빙핵 레이저\` (README 표대로 설치).
- 테스트: `/마법미몹 예고 빙핵레이저`, `/마법미몹 예고 빙핵레이저이로치` (내 발에서 바라보는 방향, 피해 없음).
- 주의: VFX 발사점은 발 위 2.4 m · 앞 1.6 m (모델 문양 위치). ModelEngine 에서 모델 크기를 바꿨다면 어긋날 수 있음.

## 추가 — 몹 스킬 1차 묶음 (계열당 대표 1개, 색만 다른 몹은 같은 스킬·VFX 색만 다름)
| 계열 | 스킬 id (`CHA_MOB_<스킬>_<몹>`) | 예고 | 효과 | VFX 팩 / 시스템 | 모션 |
|---|---|---|---|---|---|
| 오우거 3종 | ground_quake | 원형 시전자 r6 · 1.2초 | 피해 7 + 기절 0.8초 | `ogrequake` / quake_{club,axe,hammer}[_s] | ground_quake (attack2 늘림) |
| 고대 골렘 4종 | gravity_slam | 원형 시전자 r7 · 1.2초 (vfxlead 3) | 피해 8 + 중력 2초 | `gravityslam` / gravity_{arcane,corrupted,magma,royal}[_s] | gravity_slam (새로) |
| 드래곤 4종 | dragon_breath | 부채꼴 r10 · 60° · 1.2초 | 피해 9 + 사막 독 / 빙하 둔화 / 화산 화상 / 스컬크 실명 | `dragonbreath` / breath_{desert,glacial,nether,sculk}[_s] | dragon_breath (새로) |
| 서리 기사 | frost_field | 원형 시전자 r5 · 1.0초 | 피해 5 + 속박 1초 | `frostfield` / frost_field[_s] | frost_field (새로) |
| 터스커 3종 | earth_stomp | 원형 시전자 r6 · 1.2초 | 피해 6 + 기절 0.8초 | `earthstomp` / stomp_{demonic,dreamlight,forest}[_s] | earth_stomp (새로) |
- 스킬 yml: `chacademi_mob_skills.yml` 의 "자동 생성" 구간 (사장님 PC 각 폴더 mythicmobs/ 에도 같은 파일). 몹마다 Skills 에 한 줄 (mobs_*.yml 참고본).
- 모델: 사장님 PC `차카데미 vfx_Claude\몹스킬\<계열> - <스킬>\model\` (기본·이로치 모두). blueprints 같은 이름 교체.
- 시험 명령 추가: `/마법미몹 예고 효과 <팩/시스템> <원형|원형내발|직선|부채꼴> <크기> [각도·폭] [틱] [리드틱]` (피해 없음).
- 주의: 팩 이름 `earthquake` 는 기존 마법 팩과 겹쳐서 오우거 팩은 `ogrequake` 로 함.
