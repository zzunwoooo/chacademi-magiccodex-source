# Final operating integration, 2026-10-19

Branch: `codex/operating-final-20261019`. Integrate onto the observed operating base rather than replace it with a feature branch. Preserve existing Kyudichi, housing, shop, broom, lobby and operator configuration patches. No backup, map copy, forced game shutdown or real-game capture is authorized or performed by this integration.

## Included source

- Status/target HUD and camera aim fixes; arena proxy chat; lobby fixes retained.
- Kyudichi permission refresh, combat/duel HUD changes and F3+B lock, including spectators and entry while hitboxes were enabled.
- ModelEngine entity-target magic fallback; invisible player exclusion retained.
- Wild spawning: six live CA_Wild mobs per chunk excluding pets; apply only to wild and preserve region/species caps. Preserve the separately applied Multiverse world spawn-limit reductions.
- Player HUD size in Settings > GUI ratio: 0 hides, 50 is the exact previous/default size, 100 doubles; changes save immediately without changing unrelated GUI settings.
- Mob telegraphs/basic/special attacks and the 24 creature-discovery spells below. Runtime IDs remain 300-323, corresponding to plan 215-238. Costs/cooldowns and 5% discovery chance retain the supplied provisional values. No donation prices are invented. Legacy meteor prerequisites remain unchanged.

## Mechanic-owned VFX admission

PortableVFX recognizes the explicit `mob-skill-vfx-direct` marker only for these exact 24 number/ID pairs. It requires reviewed/enabled entries, known costs and explicit `required-effects`. Empty regular choreography is allowed only in this mode. The existing mana transaction invokes Mythic gameplay, and those mechanics play their VFX. All required effects still join catalogue readiness, so missing assets cannot silently admit an invisible spell.

The shipped catalogue regression test parses actual YAML, verifies all 323 entries and the 24 enabled direct entries. Existing unknown-cost, collision, path-safety and authority checks remain.

## New resource status and reused icons

68 approved original VFX ZIP archives (55,962,363 bytes) were transferred and all SHA-256 values matched. Extracted only runtime vfx.json and referenced PNG/GLB dependencies: 776 files, 54,791,860 bytes, 450 systems. The actual client loader admitted these 450 systems with zero errors/warnings. All 33 effect references of the player 24 are present. Existing 167 VFX packs/588 systems are retained; combined 235-pack/1038-system validation is recorded separately in staging.

No new dedicated mob-spell icon artwork was supplied or generated. The following verified icons already exist in the retained public client asset bundle; they are reused by reference. No runtime renumbering or deletion of old spells occurs.

| Runtime | Plan | Spell | Existing icon reused from |
|---|---|---|---|
| 300 | 215 | `fox_orbit` | `firefly_mark` |
| 301 | 216 | `curving_plumes` | `firebird_hunt` |
| 302 | 217 | `crystal_orbit` | `frost_rupture` |
| 303 | 218 | `venom_spit` | `thorn_seeds` |
| 304 | 219 | `smoke_veil` | `faint_veil` |
| 305 | 220 | `domain_stomp` | `guardian_oath` |
| 306 | 221 | `seal_circle` | `sealing_thread` |
| 307 | 222 | `seal_talismans` | `exorcism_flash` |
| 308 | 223 | `shield_slam` | `protective_ward` |
| 309 | 224 | `chain_arrow` | `guiding_fletching` |
| 310 | 225 | `mana_drain` | `mana_conduit` |
| 311 | 226 | `soul_scythe` | `black_harvest` |
| 312 | 227 | `oblivion_orbs` | `abyssal_pressure` |
| 313 | 228 | `bone_grasp` | `shadow_bind` |
| 314 | 229 | `shadow_leap` | `light_leap` |
| 315 | 230 | `binding_pull` | `feather_fall` |
| 316 | 231 | `fairy_dust` | `night_lullaby` |
| 317 | 232 | `moon_wind` | `feather_fall` |
| 318 | 233 | `gale_wings` | `gale_dash` |
| 319 | 234 | `feather_storm` | `thorn_seeds` |
| 320 | 235 | `hunting_pounce` | `light_leap` |
| 321 | 236 | `roar_leap` | `bell_of_fear` |
| 322 | 237 | `gravity_slam` | `weightless_zone` |
| 323 | 238 | `tail_spikes` | `pressure_lance` |

## HUD comparison requested by the owner

`6504a31..aa785a5` does not change TargetHudBridge's 64-block boss-priority selection or the removal of the vanilla `BOSS_BAR` layer. All vanilla/plugin boss bars, including Kyudichi bars, remain hidden. A nearby boss stays the top bar; recent boss combat wins, otherwise nearest; a looked-at normal monster may appear below. That commit range changes status icon assets/tooltips and target-HUD presentation. Later integrated camera aim fixes change the ray origin/direction, not boss priority.

## Validation

All six affected server module builds, PortableVFX paper build and Fabric build passed. Module test counts at preparation:

- `magic-codex-paper`: 354 tests, 0 failures, 0 errors.
- `magic-mythic-paper`: 60 tests, 0 failures, 0 errors.
- `chacademi-party-velocity`: 49 tests, 0 failures, 0 errors.
- `kyudichi-paper`: 18 tests, 0 failures, 0 errors.
- `creature-spawns-paper`: 11 tests, 0 failures, 0 errors.
- `magic-discovery-paper`: 35 tests, 0 failures, 0 errors.
- `portable-vfx-runtime/paper`: 135 tests, 0 failures, 0 errors.
- `magic-codex-fabric`: 407 tests, 0 failures, 0 errors.

## Not deployed / pending

This source commit is not a deployment claim. Operating JARs, server services and personal launcher remain unchanged. No server/proxy restart has occurred. The personal game's existing UI JAR is locked; wait for the user to close it normally after the package is ready.

The required 156 bbmodel originals and 29 per-mob hook YAML files (44,318,138 bytes) have not been transferred: automatic approval review rejected that separate payload because the prior VFX ZIP approval did not explicitly include it. Obtain explicit approval for these files to the same host integration staging before retrying. Compare geometry/old animations before merging new motion data; do not replace blueprints or operator YAML wholesale.

Final client asset bundle/launcher regeneration, complete model/hook validation, targeted operator-config merges, installation, coordinated restart, public-download hashes and live service logs remain pending.

The reviewed plan's other 47 spells have no verified direct runtime route under their reviewed IDs; aliases, old commands and generic components were separately audited. They are deferred, not implemented or blindly enabled. Evidence remains `missing47-implementation-plan.json` in the host final-integration artifact directory. Do not substitute the obsolete 300-spell proposal or renumber current runtime entries.
