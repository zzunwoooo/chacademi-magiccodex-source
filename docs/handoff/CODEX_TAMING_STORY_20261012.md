# Taming hidden-base label and repeatable Story ending commands - 2026-10-12

Status: staged and tested, NOT deployed. Based on c4545ea23ef4b85f8f6192718e6c9aa19f6ce660.

## Taming

Reuses the existing TamingClient world probability label and channel panel. No new UI design or texture replacement. All 927 UI asset entries are byte-identical to partyhud.4. Historical source 4c7d6f9 and hosting build-workspace both contain this label; unused target_panel/hint_panel assets are not evidence of a different approved layout. Personal-context lookup was unavailable.

Installed ModelEngine 4.0.8 BukkitEntity.setVisible(false) calls forceDespawn; its 1.21.4 handler sends ClientboundRemoveEntitiesPacket for the living base. The old server response supplied only that base entity ID, and TamingClient.renderChance returned when ClientWorld.getEntityById was absent. Removing isInvisibleTo and accepting zero-UUID queries did not cover a despawned base. This is a demonstrated code-path defect, not a claim of live visual reproduction.

New clients advertise magiccodex:taming_anchored. The bridge supplies the authoritative state and base UUID/position/height atomically on that optional channel. Existing v1 clients receive the unchanged message. The same world label uses a matching tracked base for smooth interpolation or the bounded server anchor when absent; reused/non-living/dead IDs, stale/empty/error state are rejected. Existing world reset, 16-block rendering cap and terrain depth testing remain. Server query ray, ModelEngine sub-hitbox ownership resolution, zero-UUID query sentinel, obstruction/range/profile/permission checks remain. NPC metadata now joins existing player/armor-stand/tamed/pet exclusions before vanilla profile fallback.

CA_Wild_alpha_boar remains the Mythic HUSK base with ca_alpha_boar ModelEngine model, base chance .08. No target catalog, RNG, mana 150, cooldown 60, ownership, boss or VFX changes.

## Story ending commands

The prior local completion ledger incorrectly gated ordinary ending commands together with one-time rewards. Ordinary ending commands now run once per server-authorized normal playback, independently of done.<id>. Existing local affinity/event claims and special chaca_reward first-completion handling stay unchanged; no records are removed or reset.

Reuse the existing server commands store and console dispatcher. Only the administrator's server-side command list is executed. Client packets carry action, dialogue ID, opaque session token and ending ID: no command text, arbitrary player target or permission grant. Sessions are bound to the authenticated connection player, require ready acknowledgement, whitelist the ending ID and expire after one hour. Duplicate ready/completion, previous-session completion, wrong player/token/ID/ending, abort, disconnect, admin stop/clear and server definition reload cannot replay a consumed authorization. Consumption occurs before console dispatch. No durable command retry on process crash is promised. The server does not possess the dialogue graph and cannot prove the client actually read every line; it authorizes only the pre-registered ending choices of a server-issued play.

Register only this small file on EACH backend where the dialogue is played:

`plugins/ChacademyStory/dialogues/ch1-3/server_commands.yml`

```yaml
endings:
  s20:
    - "컷신 재생 {player} magic_tutorial"
  s21:
    - "컷신 재생 {player} magic_tutorial"
```

The copy-ready example is `chacademy-story/examples/ch1-3/server_commands.yml`. Neither backend had this file during inspection; the example is staged only. No full dialogue YAML, portraits or personal fonts belong on the server. A common `end:` list may also be used, but every permitted final scene must still be explicitly listed under `endings:` (an empty list is allowed).

Administrator workflow after coordinated deployment: `/스토리 서버리로드`, then `/스토리 재생 <account> ch1-3`. The requesting administrator needs chacademy.story.admin; the receiving player needs no OP/admin permission. `{player}` and `{uuid}` are expanded from the authenticated server Player, `{nickname}` uses the existing sanitized server nickname. Commands run as server console. No leading slash is needed.

`/스토리 리로드` still validates client files without executing commands, resetting rewards, or replacing the current dialogue. `/스토리도구 재생 ch1-3` is local preview and does not create server console authorization. Client YAML ordinary end/endings strings are no longer sent as player commands; approved server lists are authoritative. Existing special chaca_reward entries remain client first-reward markers. Local event-command behavior is outside this ending-only change and remains unchanged.

The user's ch1-3/dialogue.yml and _progress records were read only. Its actual terminal scenes are s20 and s21; done.ch1-3=true remains untouched. Registering the server example allows the cutscene ending command on another normal server-authorized play even with that existing record. It does not regrant jump.sprinting or affinity.

## Verification and deployment boundary

Hosting Java21 only: Bridge 297, UI 245, Story plugin 54, Story mod 40 = 636 tests, zero failures/errors/skips. Tests cover hidden ModelEngine base vs tracked vanilla, stale/reused IDs, invalid anchor packets, pet/NPC exclusions; two normal dialogue plays -> two ending command lists but one affinity gain/first reward; cancellation, unregistered ID, forged user/token/ending, duplicate ACK/completion, reload and timeout. Wire codecs are tested across Fabric/Paper. Non-admin recipient behavior is policy-level unit coverage, not a live non-OP game test. Existing chance/cost/cooldown/catalog tests remain passing.

Public Story 0.1.1 has no private TTF. Private Story preserves all 8 asset entries including the three fonts, matching the prior personal JAR b0a4c3166591293ea6962d6c522c06f049a7d602f2206a46530648ab1c8cf479. All 927 UI assets unchanged. Chat Layout 1.6.0, ChatPlus, portrait/pet/shiny data and launcher files unchanged.

Prepared artifacts and hashes: CODEX_TAMING_STORY_ARTIFACTS_20261012.json. Hosting directory: C:/Chacademi/staging/magic-protection-20261011-task5/taming-story-validation-20261012/artifacts (private Story under private/; no-font build under public/).

Required coordinated replacement: school and wild Bridge + Story plugin; personal UI + private Story; add only the approved server_commands.yml above to applicable backends. No running server JAR/config or personal mods were replaced in this task. No restart, game launch, rewards, spawn command, record reset or paid API call. Personal check found only two existing server.jar Java processes and no Minecraft client, but recheck immediately before any future copy. Server player count must likewise be checked again before any approved stop.

Remaining acceptance: actual CA_Wild_alpha_boar label with hidden base, configured vanilla label, NPC/pet refusal, normal taming/channel visuals; live non-OP Story recipient completes twice and sees the cutscene twice while first rewards remain unchanged. Public launcher/feed/Release work is excluded. The installed personal zzunwoo 0.0.4 launcher still uses mchdistro/zzunwoo, so a manual mod copy can still be reverted by its old FullRepair feed; this task does not change that feed.
