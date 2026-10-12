# HUD fixes and wildlife review - 2026-10-12 (STAGED, NOT DEPLOYED)

Base: `91212d9ef9949abc29fa2ea3c9ef63b543b4aa53` on `codex/chat-discovery-magic-protection-20261011`.
Latest user instruction: replace server files only AFTER all requested patches are completed and verified. Hold personal launcher mods/resources, public launcher release and distribution/feed updates. No launcher repository changes were made in this patch.

## Implemented and built

1. Taming probability: client-side living-entity ray UUID could differ from the ModelEngine base selected by the server; client also suppressed labels on invisible bases. New clients query using the existing zero UUID sentinel; server resolves its existing obstruction/range/profile/permission-checked target. Nonzero mismatched UUIDs remain rejected. Invisible living bases can now render the chance label. Nameplate visibility was a user hypothesis, not the demonstrated cause. Live gameplay and an untracked entity base still need verification.
2. Pet codex: server emits only entries authorized by the existing MCPets permission policy; client filters owned entries and searches normalized names/IDs together with star filters. Public preview/model commands are removed. Summon authorization is unchanged. OP/wildcard permissions continue to follow MCPets policy.
3. Party HUD: new/default/reset layouts anchor to right-center. Existing saved x/y without the new flag retain manual positioning. First drag converts the anchor into saved fractions. Scale and coordinates remain bounded to the screen.
5. Chat: the existing aggregate tab receives all locally delivered normal chat, including party messages, with always-add enabled. Direct private payloads are mirrored into the aggregate only after this client receives them. A general tab is inserted next to aggregate only when no general tab already exists, preserving tab objects, selection and window state. Existing custom tabs remain. Aggregate/general send through the existing public `chacachat g` route; house retains `chacachat h`; party retains its existing ChatPlus prefix/dispatch; private tabs retain private routing; system remains read-only; slash commands retain prior dispatch. Server recipient authorization is unchanged: aggregate does not subscribe to other parties.

## Unresolved visual item 4

Reference Library file: `libfile_7ad200961c10819189670785a01cbfbc`, `image(20261012-011359).png`, 127x79. Actual pixels have NOT been viewed. Supported image materialization failed on the Windows environment; do not infer an alignment correction from the text caption. Current chat bitmap font glyphs E110-E115 use height 12/ascent 9; ChatImageSpacingMixin sets minimum line spacing 0.5. Neither is changed without the requested visual evidence. Parent should provide an actual supported image attachment/materialized image to finish the baseline comparison.

## Item 6: original wildlife/worldgen evidence

Only the user-specified local root was inspected: `C:/Users/matil/Desktop/` plus the Japanese-folder Chacademi project beneath it. Original outputs: `output/chacademia-worldgen-v1` and NEWER `output/wildlife-spawns-v2/*v3.zip`.

Use the newer v3 candidate, not the initially discovered v1. Author package SHA256: `cd64bb67c399d91b95d63816eb4815b198faec3762360b8fc7e6c0c63a16b975` (8,040,257 bytes). It contains `datapacks/Chacademia-Terralith-1.21.4-v3.zip` (7,163,968 bytes), Wildlife config/habitats, Mythic RandomSpawns and an old Wildlife 0.3.0 JAR. The old JAR was NOT extracted or executed; use the audited current source for a future install.

Actual embedded pack metadata: MC 1.21.4, pack_format 61, Terralith 2.5.8, 585 structure NBT assets. Actual v3 structure-set values supersede earlier v1 prose:

| Set | Spacing (chunks) | Separation | Frequency |
| --- | --- | --- | --- |
| natural_small | 8 | 4 | 0.75 |
| natural_medium | 16 | 8 | 0.60 |
| natural_large | 32 | 16 | 0.35 |
| landmarks | 24 | 12 | 0.65 |

Exclusion zones: small avoids medium within 4 chunks; medium avoids large within 8; large avoids villages within 8; landmarks avoids large within 6. Landmark weights: four ordinary landmarks 2 each, colossal guardian 1. Author rates are preserved exactly. The old 21 `.schem` files were found separately; their mapping to these 585 NBT assets is not established, so do not paste or overwrite existing structures.

138 habitat entries and 138 random-spawn rules; 276 normal/shiny mob IDs all exist in the current wild server Mythic definitions. Original config/habitat parsed content equals the current integrated source (only formatting differs). Mythic `Chance`/`Cooldown` distribution: 33 rules 0.045/20, 45 rules 0.018/60, 59 rules 0.004/240, 1 rule 0.0006/1800. These are per eligible spawn attempt, not guaranteed hourly rates. Initial shiny weights 4095:1 and group shiny denominator 4096 are unchanged. World `world`, player range 24-80, area cap 24, spawn exclusion radius 96 at x/z 0.

Current wild has only the Bukkit datapack and no ChAcademiaWildlife plugin or matching random-spawn rules. The plugin depends on MythicMobs, with Bridge/WorldGuard soft dependencies. Current source already includes season checks, population caching, lazy structure indexing and safe cleanup; do not overwrite it with the old local source. Existing Paper/MC target is 1.21.4 and hosting Java is 21.0.10.

Applying a worldgen pack to this existing world affects new chunks only; old chunks/structures remain. Generation boundaries and terrain/river collisions require live acceptance. No pack/structure installation, chunk generation/regeneration, world deletion, dependency install or DB operation was performed. Approval for the new Wildlife installation and worldgen change remains pending. Do not install duplicate Terralith packs. Review candidate defaults/world names before application.

## Staging, compatibility and remaining verification

Host staging: `C:/Chacademi/staging/magic-protection-20261011-task5/hudfix-validation-20261012/`. Built jars in `artifacts/`, original allowlisted worldgen/config data in `wildlife-candidate/`, build logs alongside. See `CODEX_HUD_FIXES_ARTIFACTS_20261012.json` for exact sizes, SHA256 and test counts.

Hosting-only tests/builds: Paper 292, Fabric 239, chat 136; zero failures/errors/skips, total 667. Current audited Wildlife source also builds successfully; its module has no test sources, so it adds no tests to the total. This is not live multi-player/visual acceptance. Existing packet versions and encodings remain unchanged. The server candidate accepts old requests, emits the same pet response schema with fewer authorized entries, and should remain compatible with the installed partyhud.1 client. Full taming/HUD/search/chat fixes require the staged client update, which is deliberately held. No server shutdown/restart, live JAR replacement, personal client write, launcher release/feed update or paid API call occurred.

After blockers are resolved, verify: invisible ModelEngine target probability/LOS/range and unrelated mobs; owned/unowned pet grants, revoke+refresh, Korean/name/ID search; existing/new/reset party layouts at several GUI scales; received party/house/public/private messages and cross-party nonreceipt; selected/custom tabs across restart. Recheck current players before any normal server stop. Preserve current Story/private fonts/portrait/Luna/transparency/pet/shiny features and all unrelated artifacts.

## Next dialogue cleanup plan (not removed here)

Legacy temporary admin entry: Fabric `DialogueAdminClient.initialize()` receives replies and opens `DialogueAdminScreen`; Paper `DialogueBridge` registers the dialogue admin channel and admin command. Scope the future removal to this temporary vanilla-style admin screen and its opening route. Do not remove dialogue data, quest handlers, NpcTalk or the newer Story system. A full dialogue module deletion is not authorized by this finding.
