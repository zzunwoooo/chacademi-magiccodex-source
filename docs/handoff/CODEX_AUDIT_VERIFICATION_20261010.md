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
