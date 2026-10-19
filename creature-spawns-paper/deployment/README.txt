Wildlife 0.3.1-chunkcap.1 -- coordinated deployment only

Initial chunk-cap is 6, the existing maximum group size. A chunk is the full
16x16 vertical column. Only valid living CA_Wild_ Mythic mobs count; players,
items, vanilla mobs, other custom types and chacademia_pet entities do not.
Existing mobs are never removed by this new check. An overfull chunk rejects
new natural spawns until its population drops. Moving entities are not removed.
The candidate UUID is excluded during its spawn-event recheck.

The common cahabitat check covers leaders and additional group members.
The spawn event rechecks it synchronously even after a generator queued requests.
Existing area-cap 24, species caps, biomes, distance, seasons and rates remain.

DEPLOYMENT (owner must coordinate with Bridge/client/Velocity changes):
1. Stop wild in the single agreed deployment window. This package does not stop it.
2. Move its old Wildlife JAR out of plugins; install the bundled new JAR.
3. Run PowerShell 7: ./Apply-WildlifeConfig.ps1
   It refuses a running wild process, wrong JAR hash, duplicate Wildlife JAR,
   unexpected area cap or changed spawn-pack layout. -WhatIf validates only.
4. Start wild as part of the coordinated deployment.
5. Console: wildlifeadmin status (expect version 0.3.1-chunkcap.1, chunk-cap=6,
   area-cap=24). In game: /mm utility getspawnstate and /wildlifeadmin check <id>.
   Observe natural CA_Wild_ spawning while in survival/adventure in valid habitats.
   Do not use a forced spawn as evidence of natural spawning.

Paired configuration changes:
- Wildlife chunk-cap: 6 (area-cap stays 24).
- Mythic SpawningLimit: 48 -> 0. This old gate counts vanilla NMS monsters.
- LocalGroupMultiplier: 0.1 -> 1.33, the installed 5.9.0 packaged default.
- Only the 138 CA_Natural_* rules get MaxLocalMobs: 0. The old inherited local
  limit counted every chunk entity, including vanilla mobs/items. Wildlife's
  selective region 24/species/now-chunk limits replace that duplicate gate.
  The global LocalSpawningLimit 24 stays unchanged for other spawn rules.
- Retain the existing Multiverse-Core/worlds.yml reductions; this script never
  touches Multiverse, ownership databases, pets, Bridge, school or arena.

Rollback: only while wild is stopped, restore its previous JAR and all three
configuration backups together. Never keep the old unprotected JAR with the
global 0 configuration. No server restart or runtime deployment has been
performed by the author of this package.
