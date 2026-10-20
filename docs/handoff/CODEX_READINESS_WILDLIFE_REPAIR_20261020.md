# Readiness transport and wildlife compatibility repair - 2026-10-20

Operating baseline eede1bc (fish shop/tank v0.1.18). No mechanics, icons, VFX art, maps, existing shop/NPC bindings or spawn caps replaced.

## Readiness
Actual1245-ID old payload38190 bytes exceeded the operating Purpur1.21.4 C2S32767-byte decoder. Paper Messenger.MAX_MESSAGE_SIZE=1048576 is a different later API boundary. Legacy readiness remains bounded and unchanged; new peers use catalog_ready_v2 and server-issued catalog_epoch.

New protocol: max4096 effects,512KiB total,19 chunks,28072 bytes max packet; complete SHA256/count/sorted unique validated inventory only. Server nonce rotates on disconnect/catalog reload; transfer generations prevent old/replayed chunks. Fixed200tick timeout, identical duplicates accepted, conflicting duplicates rejected, one transfer per player, global64 transfers,32-packet burst and bounded refill. New partial generations withdraw readiness immediately. No readiness or permission bypass. Client sends one chunk per tick and invalidates on resource/connection changes. Timeout-before-tick slot-release race fixed and regression-tested. Actual fixture canonical38188 bytes =>28072+10260 packets. Future4096 boundary and malformed/ordering/nonce/timeout/fuzz tests pass.

Paper143 tests and client114 tests pass; common protocol harness passes including2125 new v2 assertions. Version3.2.2-integrated-20261020.1-readychunks.1. Managed preload system limits raised1237->4096 to include all1245 current effects; encoded512MiB and GPU1024MiB budgets retained.

## Wildlife
Actual earlier player location was vanilla plains; all138 configured habitats allowed Terralith-only biomes. New explicit54-biome ecological compatibility map preserves existing habitat/time/season/terrain/chance/24-80distance/chunk6/area24 policy. Vanilla caves recognized. Wildlife version0.4.1-integrated-20261020.2-biomes.1,30 tests pass. New read-only `/wildlife diagnose <player>` and bounded `Natural wildlife confirmed` logs support real spawn verification. This compatibility repair alone is NOT proof of complete natural spawn recovery: user reported other biomes too, shared blockers remain under investigation; online wild player and actual naturally generated valid entity evidence still required.

## Applied server files
Three PortableVFX JARs, wild Wildlife JAR and new biome-compatibility.yml applied. Protected114 file hashes match, all three servers ready, Velocity PID10260 unchanged. No config/habitats/RandomSpawns cap rewrite, backup, map copy, forced client closure or gameplay capture. Detailed artifacts C:/Chacademi/artifacts/readiness-wildlife-fix-20261020.

## F3+B correction
During active Kyudichi/duel matches, including tests and spectators, hitboxes cannot be enabled and an existing enabled state is turned off. No admin/test exemption. Outside a match they remain available.