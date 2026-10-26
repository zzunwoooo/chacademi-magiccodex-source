# [BUILD-READY] 오픈 최종 2차 — GPT 빌드·배포 지시 (Claude 2026-10-26)


브랜치 `claude/opening-final-20261025` 의 ****가장 최근 `[BUILD-READY]` 커밋****을 빌드한다 (커밋 메시지에 `[BUILD-READY]`).
기준: `70044a79` (2026-10-25 밤 운영 배포) 이후 모든 패치. 자세한 내용·파일 위치는 [CLAUDE_OPENING_FINAL_PATCHES_20261026](CLAUDE_OPENING_FINAL_PATCHES_20261026.md) (§번호), 오너 결정은 [CLAUDE_OPENING_PREP_REVIEW_20261025](CLAUDE_OPENING_PREP_REVIEW_20261025.md).

이 환경에는 Gradle·Paper·Fabric 이 없어 **컴파일·테스트를 못 했다** (Java 문법 검사·생성기 실행·운영 overlay `check` = "차이 없음" 만 함). 첫 빌드에서 컴파일 오류가 나면 해당 § 의 "확인 필요" 줄부터 볼 것. 처음 쓰는 Paper/Fabric API 는 각 § 에 적어 둠.

## 0. 순서
1. `git fetch && git checkout <이 커밋>` → `python3 tools/operating_overlay.py check` 가 "차이 없음"인지.
2. 아래 §1 모듈 **전부** 빌드 (오늘 #94 한글 닉네임 패치가 거의 모든 플러그인의 `ChacaLink` 를 고쳐서, 70044a79 와 같은 모듈이 사실상 없다).
3. §2 서버·클라 짝을 **같이** 올린다 (프로토콜 바뀜 — 한쪽만 올리면 그 기능만 안 되거나 상점처럼 창이 안 열림).
4. §3 서버 설정·데이터 파일 교체, §4 손으로 할 것.
5. 서버 재시작은 오너 승인 규칙대로.

## 1. 빌드할 모듈 (전부)
| 모듈 | 결과물 | 놓을 곳 |
|---|---|---|
| `magic-codex-paper` | MagicCodexBridge | 학교·야생·아레나(·튜토리얼) `plugins/` |
| `magic-codex-fabric` (+`magic-codex-protocol`) | MagicCodex 클라 모드 — **전체 빌드** | 런처 `mods/` |
| `magic-mythic-paper` | ChAcademiaMagicMythic | 학교·야생·아레나 |
| `magic-discovery-paper` | MagicDiscovery | 학교·야생·아레나 |
| `chaca-npc-paper` | ChacaNPC | 학교 (NPC 있는 서버) |
| `housing-paper` | ChacaHousing | 학교·야생 |
| `chaca-portrait-paper` (+`chaca-portrait-protocol`) | ChacaPortrait | 깔린 서버 |
| `kyudichi-paper` | Kyudichi | 아레나(깔린 서버) |
| `chacademy-story/plugin` · `/mod` | ChacademyStory 플러그인 · 클라 모드 | 학교·야생 / 런처 `mods/` |
| `chacademy-motion/…-plugin` · `…-mod` | ChacademyMotion · 모션 모드 | 서버 / 런처 |
| `creature-spawns-paper` | ChAcademiaWildlife | 야생 |
| `tornado-event-paper` | ChAcademiaTornado | 깔린 서버 |
| `gimmick-paper` | 기믹 | 깔린 서버 |
| `chacademi-party-velocity` | 파티 (Velocity) | 프록시 `plugins/` |
| `portable-vfx-runtime` `:paper` · `:client` | PortableVFX | 서버 / 런처 |
| `ops-dashboard` (새 모듈) | 운영 대시보드 (`java -jar`) | 호스팅 — [CLAUDE_OPS_DASHBOARD](CLAUDE_OPS_DASHBOARD.md) |

## 2. 서버·클라 같이 올려야 하는 것
- 상점 응답 SHP4 (#98 교환) — Bridge + 모드. **한쪽만이면 상점이 안 열림.**
- 정보창 v3 (#87) · 인기도 · 상대 일러스트(ChacaPortrait + portrait-protocol + 모드) · 하우징 SHOWCASE (#108, ChacaHousing + Bridge + 모드).
- 청크 점유 #88 · 기숙사 점수 홀로그램 #106 · 확성기 #102 · 허수아비 표시 #107 (MagicMythic + 모드) · 동행 펫 #81 · 길잡이 별 #93 (스토리 플러그인+모드+Bridge).
- 전투 스펙·장신구 강화 (§34) — Bridge·MagicMythic·MythicMobs 스킬·클라 모드(EnhancementScreen·도감). 데이터팩 필요 없음.
- 뽑기 상자 API 상자 (§36) — Bridge + protocol + 클라 모드 (새 채널, 상자 PNG 5장 리소스 포함). 공통 지급 함수 RewardDelivery 로 우편 수령·낚시·퀘스트·연금술·환불 경로가 바뀜.
- NPC 통계 (§19 추가) — ChacaNPC + Bridge.

## 3. 서버 파일 (jar 가 안 덮어씀)
- `deployment/operating-config/**` 전부 overlay 대로 (MagicCodexBridge·MythicMobs·PortableVFX·ChacademyStory).
- 새 설정은 처음 켤 때 자동 생성: chunk-claims · popularity · megaphone · house-board · convenience-commands · audit · server-heartbeat · combat-lock · pet-companions(overlay) 등 — 각 § 의 기본값 확인.
- **ItemsAdder 실제 id 맞추기**: FINAL_PATCHES 부록 A (확성기·인기도 해제권·확정 뽑기권 3종·땅문서·가방 확장권 — 아이템은 운영에 이미 있음).
- NPC 일과표 85명: `docs/plans/npc-schedules/` → 학교 `plugins/ChacaNPC/npcs/` (먼저 places.yml 에 34곳 등록 — docs/plans/npc-places.md), `/서버관리 새로고침 NPC`.
- 뽑기 상자: `plugins/MagicCodexBridge/loot-boxes.yml` (overlay 는 꺼진 예시 상자만 — 실제 상자는 운영자가 `/상자관리 생성`·`관리` 로 만듦). 서버 `audit.yml` 에 새 종류 "뽑기상자" 반영(§36).
- 책 (#105): `MagicCodexBridge/books.yml` overlay 교체 → `/서버관리 새로고침 책` (§35, 블록 연결은 오너가 docs/plans/library-books.md 대로).
- 스토리 장 이름 (#104): ChacademyStory `steps.yml`.
- TAB 한글 닉 (#110): `deployment/tab/`.

## 4. 손으로 할 것
- MCPets 커스텀 펫 276개 파일 삭제 (#81, `deployment/mcpets-companion-removal.json`).
- ModelEngine: `training_dummy.bbmodel` → `plugins/ModelEngine/blueprints/` → `/meg reload` → 리소스팩 병합 (#107).
- PortableVFX 새 팩 (`portable-vfx-runtime/assets`) → 런처 `config/portablevfx/effects/` (FINAL_PATCHES §5, VFX 문서).
- Velocity `bungee-plugin-message-channel = true` 확인 (#95 공지·#102 확성기 전 서버 전달).
- HuskHomes `/home`·`/sethome` 권한 끄기 권장 (#95).
- DB: 학교·야생·아레나 `database.properties` 가 같은 MariaDB (공유 표 자동 생성: codex_audit_log · codex_player_stats · codex_popularity* · codex_chunk_claim/trust · codex_housing_showcase · codex_server_status …). 안 쓰는 `codex_artifact_pity` 는 지워도 됨.

## 5. 전투 스펙 배포 주의 (§34)
- 서버의 `MagicCodexBridge/config.yml`·`enhancement.yml` 은 jar 가 안 덮음 → 새 값으로 **교체** (enhancement.yml 이 옛 것이면 장신구 강화가 꺼져 있음). MagicMythic `config.yml` 도 새 키(마법 방어 상한 등) 반영.
- 이미 접속했던 계정은 최대 마나 100·회복 5 가 저장돼 있음 → 접속자 전체 `/유저관리 <닉> 스탯 최대마나 설정 300`, `스탯 마나회복 설정 8` (또는 GPT 가 일괄 처리 방법 판단, 오너에게 보고).
- 운영 jar 에 저장소에 없는 장신구 강화가 이미 있으면 오너에게 알리고 이 구현과 합칠 것 (오너: "이미 있다").

## 6. 오너 검수 대기 (빌드에는 포함, 수치는 바뀔 수 있음)
- 전투 스펙: 재계산 결과 적당 vs 한계 1.90배 (강화 상승량 = 단계값 × 코어 품질 배율 "섞기", §34 추가) (목표 1.6배 초과 — 펫 도감 20·마나 회복 8 유지 때문). 대충끼리 95초. 조정안은 docs/plans/combat-spec.md.
- 책 67권 (#105), 아공간 배낭 잠긴 쪽 (§33), 뽑기 상자 판단 6가지(§36: 10개 대표 등급 = 최고 등급, 열쇠 10개 미만이면 10연 거절, 안 보이는 잠금 해제 쪽엔 안 넣고 우편, 쪽 넘김 중 퀘스트·연금술 잠깐 거절, 환불 넘치면 바닥 대신 우편, 기록 종류 이름).

## 7. 컴파일 확인 상태
- 70044a79 이후 바뀐 Java 352개 전부 문법 검사 통과. 타입 검사는 못 함 → 첫 빌드 컴파일 오류는 각 § "확인 필요" 참고, 고친 뒤 오너에게 보고.
