# 신규 마법 150종 (301~450) 구현표 (Claude, 2026-10-31)

기획: 프로젝트 문서 `차카데미 신규 마법 150종 기획안` · VFX: PC `차카데미 vfx_Claude\<번들>` (150팩, 목록 `_신규마법150_목록.html`) · 오너: "실제 기능 개발도 하고, 모험 일지도 다 적어봐" → "연구일지 쓰려면 발견조건도 해야겠네 발견조건도 ㄱㄱ".
마나·쿨·피해·반경은 **임시값** (밸런스 미확정). 모험일지 = 도감 `research` (연구 기록), 각 마법의 발견 조건 장면으로 씀.

VFX 팩 150개: `portable-vfx-runtime/assets/<번들>/` (vfx.json · textures · NOTES.md, 2026-11-03 추가) — 클라이언트 PortableVFX 팩으로 배포. 미리보기 HTML 은 PC `차카데미 vfx_Claude\<번들>\preview.html`.

자동 생성: `python3 magic-mythic-paper/tools/nova150_spells.py` (이 문서 / default-spells / mana-spells / spell-catalog / spell-bindings / **discoveries.yml**) →
`python3 magic-mythic-paper/tools/spells.py` (미몹 스킬 yml / spell-runtime.yml / SPELLS.md / spell-targeting.json).
게임 기능: `chanova{type=<id>;pack=<번들 소문자>}` = `NovaMech` → `Nova150Wind / Fire / Water / Earth / Light / Dark`, 도우미 `Nova`.

## 발견 조건 v2 (tools/nova150/discovery.py → magic-discovery-paper discoveries.yml 끝 구간, 오너 2026-11-03)

바닐라·서버 활동 조건 (기존 discoveries.yml 조건과 겹치지 않게, 침대·친구 수 제외 — 멀티서버). 등급이 올라갈수록 어려움.
원소별 궁극 1종씩 6종(창공 절단·용광로의 문·영겁의 빙하·지핵 자기장·초신성·월하 가면무도회) = 「<마법>」 마법 스크롤 사용 (`scroll.<id>`) — 할로윈 12종과 같은 스크롤 시스템 (item:magic_scroll, `/마법스크롤 <마법> [닉네임] [수량]`, 우클릭하면 배움 + MagicScroll 이 신호를 보냄). 서버에 새로 만들 것 없음, 던전 보상 등으로 스크롤만 나눠 주면 됨. 나머지 144종 카운터는 **아직 서버가 안 셈** (그중 30종은 차카데미 시스템 연동 — 큐디치·연금술·마법봉 강화·의뢰·기숙사·펫·NPC 호감도·토네이도·작물 각성·고유 몬스터·칭호·파티·스토리 플러그인에서 신호를 받아야 함) — 서버 세션이 구현: 명세 [NOVA150_DISCOVERY_COUNTERS.md](NOVA150_DISCOVERY_COUNTERS.md).

2026-11-03 정리 (오너 "발견 조건 겹치는거 빼고 / 스크롤로 된건 우리 스크롤 시스템 할로윈에 했던거"): 운영 중 discoveries.yml (311종) 과 같은 행동이던 19종 조건·모험일지 교체 — 성냥 긋기(모닥불→네더 차원문 열기)·달 조각(엔더맨→후렴과)·연줄(끈 산책→끈 만들기)·진공 회랑(진주→광산 수레)·달의 인력(팬텀→슬라임)·상현·하현 칼(낚시 보물→숫돌)·풀무질(용광로→화염구)·불티 집게(부싯돌→벌집 연기 수확)·쇳물 방울(철 제련→점적석 용암 가마솥)·초점 렌즈(유리 굽기→망원경 제작)·별똥 바늘(망원경 보기→불꽃 별)·점토 덩이(테라코타→점토 덩이)·칼날비(뇌우 처치→높은 곳 화살 처치)·달빛 화살(보름달 처치→쇠뇌 처치)·서리 정원(차가운 걸음→눈 골렘)·등롱 불씨(불화살 처치→랜턴 설치)·흙먼지 발구르기(허스크→파괴수)·고드름 투척(스트레이→얼음 가시 바이옴)·칼바람 장막(브리즈 막대→거미줄 끊기, 풍차 날개 브리즈 처치와 겹침). 허스크·스트레이는 기존 "좀비와 스켈레톤 50마리" 에 같이 세어져서 뺌. 비교 도구: 운영 discoveries.yml 의 requirements 키·문구 대조.
모험일지(research)는 조건을 직접 말하지 않고 후보 4~5개가 떠오를 정도로 돌려 말함 (말투: 일기·소문·편지·동요·비문·대화 등 섞음).

같이 고친 것: `Nova.later / loop / Shot` 이 시전 때의 SpellTrace(어느 마법인지)를 이어받게 함. 전에는 지연 피해·투사체 처치가 `kill.spell.*` 에 안 잡혔음 (기존 nova 325~387 의 처치 조건도 같이 고쳐짐).
아이콘: discoveries.yml 은 `magiccodex:textures/spells/nova150/<id>.png` 를 가리킴 — 아직 그림 없음 (클라이언트는 없으면 원소 문장으로 대체). /cicon 으로 150장 만들면 됨.

## 운영 반영 때 (2026-11-03 준비만 — 아직 서버·실행기에 안 올림)

- 이 브랜치는 운영과 갈라져 있음 (300개 재번호·생활 12종 등 운영에 없는 변경 포함). 통째로 합치지 말고 150종 구간만 옮길 것: discoveries.yml 끝 구간, mana-spells.yml, spell-catalog.yml, spell-bindings.yml, chacademi_spells.yml 의 `CHA_` 150개, spell-runtime.yml, default-spells 301~450 + known-hashes 150줄 (생성기 MARK 구간).
- 서버 플러그인: ChAcademiaMagicMythic 에 `Nova150*` 6개 + `Nova` 도우미(군중제어 연동)·`NovaMech` 등록이 있어야 스킬이 움직임 → 운영 소스에 `[기능:신규150 시작/끝]` 표시로 넣고 빌드.
- 도감 순서: 운영 도감은 몹 기술 마법이 order 300~324 를 씀 → 301~450 과 섞여 보임. 반영 때 순서를 뒤로(463~) 미룰지 오너 확인.
- VFX 150팩: 실행기 `config/portablevfx/effects/<번들>` 로 배포. vfx.json 들여쓰기 공백만 빼서 147MB → 102MB (내용 같음, 호환 검사 150/150 OK). 운영 152팩(약 141MB)과 합치면 약 240MB 로 클라이언트 팩 한도 256MB 에 가까움 → 배포 뒤 클라 로그에 "pack not admitted" 없는지 확인.
- 발견 조건: 스크롤 6종은 discoveries.yml 만 넣으면 바로 작동 (`/마법스크롤 <마법>`). 나머지 144종 카운터는 MagicDiscovery 에 새로 구현해야 셈 (명세 NOVA150_DISCOVERY_COUNTERS.md) — 구현 전엔 그 마법들은 배울 수 없음.
- 알림 문구: 운영 말투로 (`~어요` → `~습니다 / ~해 주세요`).
- 아이콘 150장 아직 없음 (/cicon).

## 군중제어(CC) — 우리 상태이상 시스템과 연동 (오너 2026-10-31: "군중제어랑 비슷한 효과는 실제 우리 시스템의 군중제어와 연동해야한다")

- 상태이상은 전부 `n.status` → `StatusEffects.apply` (아이콘, 보스 규칙: 속박 면역·기절/수면/공포 → 둔화, 면역 가드, 아티팩트 CC 감소/무효 `MagicStatusEvent`, 제압의 머리띠, 정화). 마법 코드에 보스 예외를 손으로 두지 않음.
  석화·동결·감금 = 기절, 묻힘 = 속박, 혼란·헤맴 = 공포, 시야 가림·대상 놓침 = 실명, 땅으로 끌어내림 = 중력.
- 위치 이동은 `Nova` 도우미: `knock`(밀치기) · `pull`(끌기) · `lift`(띄우기) · `halt` · `carry`(붙잡아 나르기/공중 고정/끌고 다니기/강제 걷기).
  모두 닻 장식 태그(`chacademi_anchor`)·대지의 닻 가드·면역 가드면 안 움직이고, 보스는 `boss-knockback-multiplier` 로 줄어듦.
  `carry` 는 엔진에 **고정(movement_lock)** 을 걸고 그 상태가 남아 있는 동안만 끌고 다님 → 아티팩트 감소(닻 장식 고정 -50% 등)·정화로 짧아짐, 보스는 상태만 받고 안 끌림.
- 적에게 직접 `setVelocity`/`setTarget`/Pathfinder/포션 CC 쓰는 곳 0 (남은 setVelocity 는 시전자 자신 이동·투사체 반사/정지뿐).

## 구현표

{{TABLE}}
