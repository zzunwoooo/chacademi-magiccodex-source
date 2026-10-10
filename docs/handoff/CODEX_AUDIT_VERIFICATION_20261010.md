# Audit fixes host verification — 2026-10-10

Status: BUILD VERIFIED; DEPLOYMENT BLOCKED. No production server stop/restart, installed JAR replacement, database modification, or paid API call occurred in this audit integration stage.

## Source
- Integration branch: codex/audit-fixes-20261010-task5
- Base: 310512b7dbf2f61f880f5f8a2c31266eeef873ce
- Incoming: claude/audit-fixes-20261010 @ 0ede1e2d0740240f1b7260979293edab96c85fa3
- No-conflict merge: 7ac9f3190d8082349ce7f079e7ce6df813a9de52
- Host staging: C:/Chacademi/staging/audit-fixes-20261010-task5/source
- Original dirty build-workspace preserved. Shared integration branch is not advanced by this verification checkpoint.

## Actual verification
All builds ran on hosting Java 21 with real dependencies, not handwritten API stubs.
- Build-Server.ps1 -Verify: successful; six server artifacts assembled. JUnit: NPC 21, Portrait 75, Bridge 269, Discovery 21, Tornado 2 = 388. Wildlife has no test task cases.
- magic-codex-fabric test remapJar: successful, 225 JUnit cases.
- chacademy-story/plugin test build: successful, 40 JUnit cases.
- chacademy-story/mod test remapJar with existing private storyFontDir: successful, 29 JUnit cases.
- portable-vfx-runtime test build -PskipClient: successful, Paper 102 JUnit cases plus protocol harness 31,753 assertions. Common test is intentionally disabled in favor of protocolTest. PortableVFX client excluded per patch scope.
- Total JUnit: 784, zero failures/errors/skips in generated XML.
- Existing private CNM l/m/b font hashes match previous personal deployment exactly. Private fonts and private JAR are excluded from Git.
- Build-Server copied compiled artifacts into the host artifact staging directory, not installed server plugins.
- Deprecation and SnakeYAML semver warnings remain; builds succeeded.

## Deployment blockers — do not bypass
1. ChacaNPC Storage.uniqueRumorIndex attempts DELETE after ANY index creation SQL error except duplicate-index-name/already-exists. SQL keeps MIN(id) for each (event_id, heard_by) and permanently deletes all other rows. It can therefore attempt deletion after errors unrelated to duplicate data. Separate approval or approved removal of this automatic deletion is required. No production query/count or deletion executed.
2. Isolated MariaDB migration validation is not performed. Existing inspected DB helpers are SQLite-only. Access to C:/Chacademi/services/mariadb is denied to current account. Need an existing authorized isolated test DB and host-local connection mechanism, without new grants/accounts or credentials in chat. No credentials were read/output.
3. Both server Story dialogues directories currently have no */dialogue.yml graph. Client originals exist for ch1-3, ch1-4, ch1-5, ch1_wakeup. ch1-2 is absent. New require-server-graph defaults true and refuses missing graphs. Preserve original client graphs; never generate a replacement sample or disable validation silently.
4. Existing finish-if-missing-mod=true on both servers; explicit compatibility decision needed before changing this existing setting. Portrait server-id already school/wild respectively. PortableVFX config-version absent; documented migration needs review before installation.

## Remaining validation and deployment
- MariaDB DDL/index syntax and migrations on isolated schema; duplicate nickname/rumor behavior; commerce/mail/quest recovery. No production fault injection, recovery commands, duplicate cleanup or paid AI.
- Verify and stage original client graphs, missing ch1-2 resolution; in-game UI/portrait/ESC/API/mapping behavior remains untested.
- Preserve Sunburst/Flare, Luna, transparency, personal UI sizes, pets/shiny features and private assets. HolyTaming remains outside this audit scope.
- Compatible UI+Bridge and Story mod+plugin must be installed together with changed server artifacts only after blockers resolved and manifests compared.
- Check local game state before launcher replacement. Stop school/wild only with Stop-Network.ps1 -Server target. Never Control.ps1 -Action Stop (stops all). Do not stop Velocity or DB.
- After deployment, check startup/API connection/schema warnings and selected safe settings; update CLAUDE_INDEX to received only after remaining validation/deployment status is accurately recorded.

## Safe-index and original-graph follow-up

The earlier automatic-delete blocker is now removed in source. Storage.uniqueRumorIndex performs no DELETE and never treats a thrown index-creation error as success. It classifies duplicate/constraint, permission, syntax and other SQL failures, queries aggregate duplicate counts only, and throws a sanitized SQLException. Driver row-value messages/causes are not retained. The existing plugin initialization catch marks budget loading failed and leaves AI disabled, rather than continuing as if initialization succeeded. Existing rows remain untouched.

Six new RumorIndexTest cases passed: real in-memory SQLite successful/repeated initialization and duplicate preservation; injected MariaDB permission, syntax, duplicate-index-name and duplicate-data failures with diagnostic access denied. The latter verify no cleanup or unexpected database operation and no raw row values in errors. MariaDB itself is still untested. Build-Server.ps1 -Verify passed again (4 executed tasks; 24 unchanged up-to-date). NPC now has 27 tests; aggregate available JUnit results total 790. Updated NPC SHA256: cd79b82a9922839d4199adf8322ba7f6235a4479d08d473070fc8f3b46c033a2.

Original client graphs were copied unchanged to C:/Chacademi/staging/audit-fixes-20261010-task5/story-graphs, outside Git and outside runtime directories. Actual built StoryGraph.parse with SnakeYAML validated them:
- ch1-3: 9 scenes, 0 events, no errors/warnings. SHA256 c40b72c31a278491d70afdc6af9389eb9cedecb325882adb4bc1924bf165ea5f.
- ch1-4: 3 scenes, 0 events, no errors/warnings. SHA256 3f56db42eb1f666973996d7d258898fe8403b743096e9c127aa7b92e7b7f0921.
- ch1-5: 8 scenes, 0 events, no errors/warnings. SHA256 5cd0f37e7c9cf82f52fc20f2fea0f14f30bcb905b67f25bbdb522e61a5470140.
- ch1_wakeup: 6 scenes, 1 event, no parse errors; warning: scene wake choices 1 and 3 have different affinity effects and lack distinct event identifiers. Server may not distinguish the chosen branch, causing later need checks to diverge. Per audit handoff, treat this ambiguity as a deployment concern. Original remains unchanged; do not silently invent events or rewards.
- ch1-2 remains absent as the acknowledged limitation; no sample generated.
- Both school/wild dialogues directories contain no files, so neither server_commands.yml nor legacy <id>.yml command definitions exist there. No commands/rewards were added or overwritten.

Exact MariaDB access issue: host SSH account running C:/Chacademi/tools/python/python.exe attempted pathlib.Path('C:/Chacademi/services/mariadb').iterdir(); Windows returned PermissionError [WinError 5] Access is denied. This is an OS filesystem denial, not evidence of a rejected SQL login and not an automatic tool-approval rejection. The exact ACL cause was not inspected. No retry, alternative route into that directory, administrator escalation, credential reading/transmission or grant/account change occurred.

Permitted independent locations checked by filename: C:/Chacademi/tools/db-readonly-inspection contains only ReadonlyMergeAudit.java and ReadonlySqlCounts.java (both SQLite-only); C:/Chacademi/tools/plugin-builds contains HuskSync-3.8.7 and mcpets-api-check-20261005. No existing MariaDB test helper was found in these locations. This does not assert that none exists elsewhere.

Minimum remaining DB question: can the owner provide the path of an already-authorized host-local test helper/profile that connects to an isolated MariaDB schema? If none exists, the owner must prepare an isolated schema and approved access mechanism before validation can resume; do not request passwords in chat or broaden current credentials/permissions automatically.

Runtime deployment remains blocked pending MariaDB migration verification and the graph ambiguity decision. No runtime modifications in this follow-up. Shared codex/hires-item-icons-20261006 remains unchanged.

## Scope update and schema-preparation approval block

User subsequently approved isolated MariaDB validation and explicitly deferred Story content for later bulk installation. Therefore do not edit or deploy Story graphs/identifiers in this binary deployment, and do not treat missing ch1-2 or the existing ch1_wakeup ambiguity as binary-deployment prerequisites. They remain untested content limitations.

Attempted preparation design: use DatabaseSettings.load/connect through the existing school MagicCodexBridge profile (without logging/transmitting its credentials), then create ONLY chaca_audit_20261010_task5 and switch the connection catalog with a SELECT DATABASE() guard before any test tables. No production rows, accounts, grants, ACLs or service controls were targeted.

Automatic approval review rejected the command BEFORE execution: CREATE DATABASE through the production plugin profile could mutate shared production infrastructure, and the endpoint was not verified as a separate test server. No helper was written by that command and no database connection/statement ran. Do not retry indirectly or bypass this denial. The pending clarification asks explicit approval for creating the isolated schema on the same existing MariaDB instance, or an existing separately authorized test connection path. No password should be sent in chat.

Minimal preparation sequence after approval is resolved:
1. Use the existing normal connection API, with credentials confined to host process memory. No privileged service-directory access.
2. Create only chaca_audit_20261010_task5, without IF NOT EXISTS; a collision must stop instead of reusing unknown data. Do not create users or change grants. If CREATE is denied, stop and request owner preparation of that exact isolated schema and the required schema-scoped privileges.
3. On every validation connection, require SELECT DATABASE() to equal the dedicated schema before any DDL/DML. Use synthetic rows only. Do not qualify any operation with production schema names.
4. Validate actual pre/post migration DDL, nickname uniqueness, rumors preservation, commerce/mail/quest and Portrait states. Keep test schema for evidence; no destructive cleanup without authorization.
5. Only after validation succeeds, perform the previously approved binary deployment. Runtime migration may add required columns/indexes; never permanently delete existing production rows.

Read-only deployment preparation confirms:
- Code HEAD 36b2b3579ae0f234883a1e79e9a57814024e291b was clean; binary hashes saved privately on host in audit-fixes-20261010-task5/binary-manifest.json.
- Personal CNM l/m/b hashes still match originals exactly.
- Portrait server-id school/wild correct. Existing Story finish-if-missing-mod remains true. PortableVFX config-version absent. No configuration modified.
- Local Java/game processes: none at check time. Recheck immediately before replacing launcher JARs.
- ChacaNPC is installed on school only. Wildlife matching plugin JAR is absent on both servers; do not silently add previously absent plugins as a replacement. Bridge, Portrait, Story, PortableVFX installed on both; Discovery matching JAR exists on both.
- All installed runtime JARs remain unchanged; no server/Velocity/DB process control occurred. Shared integration branch remains unchanged.
