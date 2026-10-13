# Developed client features

| Existing mod | Feature |
| --- | --- |
| magic-codex-fabric | SurvivalInventoryUI: vanilla-backed survival inventory/crafting, 27+9 slots, real offhand, armor Shift guard, status effect rows, high-resolution icons/glint. No extra inventory mod. |
| magic-codex-fabric | Broom equipment: existing 4+4 equipment and stats, five-card pages, server-confirmed equip and gauge tooltips; shared unavailable notice and sneak-key reservation. |
| chacademy-motion 0.3.0-codex.1 | Client flight/animation/models and Object-based BroomApi; playerAnimator 2.0.5 and Fabric API 0.119.4+1.21.4. Companion Paper plugin owns permission checks, equipment/mastery and lifecycle. |

Existing Story, journal, pet/shiny, portrait, chat, mouse and quest features remain in their original modules. See [combined regression/release](handoff/CODEX_INVENTORY_BROOM_RELEASE_20261013.md).

## 2026-10-13 review

Magic Codex now includes the approved shoulder/aim camera, chapter journal, revised notification/inventory/equipment UI and server-owned combined monster HUD. Motion 0.3.0-codex.2 adds arc HUD and six-key recatch; Story 0.1.3-journal.2 supports optional chapter. [Full requirements, tests and pending QA](handoff/CODEX_UI_REVIEW_RELEASE_20261013.md). Monthly season behavior is not final: 72-hour real-time cycling is deferred to the next patch.

## 2026-10-13 final follow-up

SurvivalInventoryUI remains inside Magic Codex: 27 inventory slots and nine hotbar slots plus real offhand, revised approved artwork, shared notices, persistent key registration order, settings/camera/local VFX opacity, straight aim preview, broom equipment and four-key recatch. Motion is 0.3.0-codex.3. Real-time 72-hour season support awaits an explicit user-selected epoch before activation. [Combined implementation and regression checklist](handoff/CODEX_FINAL_FOLLOWUP_20261013.md). Public launcher feed is outside this follow-up.
