# Operating source: alchemy and broom carrier integration (2026-10-16)

This branch records the complete previously uncommitted operating source, then integrates only dd34f25f1374c0faffc35a9265e9672e0ca4bcf9, 43513c2fcf4821b6e96aac34f1f8969eb45b716d, and b69ea36d1655ab6c207552477a85547b4aa725c6. It is a source snapshot on e3671911c4ddb34dd47b5e93c07a5f8acf68ea93, not a merge of the upstream branches.

The baseline is the operating vfx-b0a50cd-20261016-task10 integration. It retains ac545d0 multi summon, effects.2 recursion/heal/save and legacy equipped import fixes, 101-167, reaction VFX, unit ring, 103 VineLadder/134 LightMark, and the b0a50cd BC7 fix. The original staging tree is preserved. See deployment/source-preservation.json for the exact source comparison.

Alchemy refuses both known and unknown recipes when experience is insufficient, without spending ingredients/experience or granting discovery. Discovery follows successful production. Four fixture tests exercise the actual bridge craft method, including an unavailable result item. Production alchemy.yml deliberately contains zero recipes: no production recipes were invented. Recipe discovery uses LuckPerms; the current school/wild installations use separate H2 stores, so cross-server discovery sharing is NOT configured. No permission/database settings were changed.

Broom carrier integration retains the operating Bolt multi-shot, damage/proc/rehit, ally and visual behavior. CatalogVisualEngine retains target/point/height event anchors. The new initial lead sweep and positive forward inherited speed are added to those operating paths. Broom trails and unrelated wishlist changes are excluded.

## Build and verification

Use Java 21 and the wrappers. External licensed/local inputs are listed by path, size, SHA256 in deployment/external-build-inputs.json; obtain them from the authorized server operator, not this repository.

```powershell
./gradlew.bat --no-daemon --console=plain --max-workers=2 clean verifyServer assembleServer
cd magic-codex-fabric
./gradlew.bat --no-daemon --console=plain --max-workers=2 clean test build
cd ../portable-vfx-runtime
./gradlew.bat --no-daemon --console=plain --max-workers=2 :paper:clean :paper:test :paper:build
cd ../chacademy-motion/chacademy-motion-plugin
../../gradlew.bat --no-daemon --console=plain --max-workers=2 clean test build
```

All affected builds passed; machine-readable test counts are in deployment/test-results.json. Additional bridge fixture tests required Paper registry stubs, and two VFX server proxies were updated to represent an absent Motion plugin. No production behavior was changed merely to satisfy mocks. Existing BC7 client source and deployed BC7 binary are preserved; this change does not replace the VFX client or Motion client.

## Operating configuration contract

Apply the six nonsecret gameplay overlays in deployment/operating-config to the corresponding plugin data directories when reconstructing the installation. They were copied from matching school/wild operating files and preserve runtime-only bindings, including 103/134. Verify deployment/operating-config-manifest.json. Do not overwrite existing user data or private connection settings.

MagicCodexBridge reads player-state-database.properties first, falling back to generic database.properties only if the dedicated file is absent. Both current servers have the dedicated MariaDB configuration with matching connection targets; the generic file is absent. The dedicated template contains placeholders only. Do NOT add a generic database.properties as a supposed correction: it also enables unrelated database consumers. No live credentials, player data, logs, or private settings are included.

The real-time season deployment requires one shared application-time ISO8601 +09:00 epoch, season-clock: realtime, Asia/Seoul, initial-season: spring, and hours-per-season: 72. Reload with `/계절관리 새로고침`; never use the fixed-season command for this change. Before deployment both persisted climate states were automatic=true with autumn calendar state and no fixed override. Deployment status/actual epoch must be checked in the task receipt; no epoch is invented in this source handoff.

## External assets and limitations

The complete 163 VFX packs / 2017 files, including LightMark and VineLadder, are inventoried in deployment/external-vfx-assets.json with hashes and the existing v0.1.10 archive location. They are not copied into Git. This task does not modify the temporary launcher or its update feed.

The requested Library answer was read. Its appendix ZIP could not be materialized by the official helper on Windows (os.setxattr unavailable), so the appendix was not reviewed. All three requested commits and their repository handoff documents were available.

Real-game login, UI rendering, actual player crafting, broom flight/projectile alignment, and cross-server discovery were not executed. These require authorized player QA after deployment. Separate H2 storage and zero production recipes remain explicit dependencies, not silently corrected settings.
