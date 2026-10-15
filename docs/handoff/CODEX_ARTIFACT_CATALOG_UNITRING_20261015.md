# Artifact catalogue and unit target ring integration, 2026-10-15

Baseline: `3f06580728fbfe75f4c64c4904bfcf7e7fadbd69`, preserving expansion 101–167, default YAML migration, Solar/projectile visuals and elemental reaction VFX.

Included: artifact catalogue commit `c1c2c019f87cd72037b5b18dfb0f16aa76de83b1`, its small header/divider prerequisites `7ba91fc` and `10c7e42`, and client-only unit ring patch `09186da00853381012eb1a80584c0e5ef7839b4e`. The unrelated broom-projectile ancestor is excluded.

Versions: Bridge `0.31.0-expansion71.1-artifact.1`; UI `0.40.0-expansion71.1-artifact.1-unitring.1+mc1.21.4`.

Validation: hosting Java 21; Bridge build with 319 tests and Fabric build with 318 tests passed (637 total, zero failures). Regression checks cover 150 catalogue records / 148 visible entries, all 71 supplied icon paths, protocol split-packet round trip and v1 compatibility, and preserving existing spells while unbinding X or a remapped artifact key. Expansion, reaction, VFX, spell defaults and targeting data are unchanged from baseline.

Integration fixes: retained the expansion version when resolving the old branch version conflict; combined the handoff index without importing the broom-projectile feature. A fresh staging initially lacked the existing SQLite test-only JAR; restoring that dependency fixed 82 test-environment failures. No five-error Java compile failure reproduced. No production database changes were made.

Deployment status at this checkpoint: prepared, not installed. Personal Minecraft was running, so no process was stopped or personal file replaced. Bridge and client are to deploy together because the new server sends artifact protocol v2. Runtime artifacts.yml is the verified four-entry default and requires replacement with the new catalogue; settings remain unchanged. Temporary/distribution launchers and separate icon-generation folders are excluded.

Known feature boundaries: artifact abilities remain unimplemented; use reports that the effect is not ready and starts no cooldown. 79 icon fields remain empty. Equipped state remains per-server PDC; cross-server DB synchronization is not part of this change. Existing X spell bindings retain their spell and require rebinding on client load. Game visual testing has not been performed.

User checks after deployment: /아티팩트도감 shows 148 visible entries; equip a usable artifact and press X for the not-ready message; X is reserved in spell key settings. Aim a learned UNIT spell such as ash_brand at a creature and confirm its ring is on the ground under its feet.
