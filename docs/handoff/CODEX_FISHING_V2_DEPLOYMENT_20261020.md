# Fishing protocol v2 operating integration - 2026-10-20

Merged claude/fish-shop-20261020 through 5916b28 (7b862ac, 73b8bec, 3153cae) onto operating 388cb52. The existing VFX chunked readiness and wild main-thread spawn gate remain intact.

Incoming UI, fishing minigame, codex/reward displays, caught-only shop list and gear DB are included. FishingProtocol is v2. All three shared Bridge instances and personal/public clients must update together.

Integration retains the existing codex_fish_tank identity ledger rather than replacing its rows with destructive moves to codex_fish_item. ITEM rows provide authoritative metadata to the new id-only fish UI. Transactional capacity, owner locks, withdrawal generation tokens, terminal identities, catch recovery journal, commit-before-consume and uncertain-payment quarantine remain in effect. Admin fish are registered before delivery. Old fitted rod PDC parts migrate as a complete loadout, including when durability is the first write. Common ShopStore NPC routing is retained.

User scope: build and deploy only. ItemsAdder registration, item numbers, original rod materials and resource-pack generation/deployment were not changed. Live fishing/mana/shop configuration remains unchanged. The 681 removed client item assets are intentional incoming changes; all 200 fish UI illustrations remain.

Validation: Bridge396, Fabric427, Kyudichi18 and Discovery35 tests passed, including hosting MariaDB two-connection regressions. Existing item replay, payment rollback and catch journal tests pass. Added item metadata/reentry and legacy fitted gear migration regressions.

Deployment: school/wild/arena Bridge 0.31.2-integrated-20261020.4-fishing-v2.1. All server starts and 169 protected hashes verified. PortableVFX readychunks.1 and wild spawngate.1 retained; Velocity unchanged. No backup or map copy, game force-close or gameplay capture.

Live player fishing/minigame, full VFX readiness and natural wild spawn QA remain pending a player reconnect. Zero online players during deployment is not evidence of actual gameplay success.

Bridge SHA256: 8a434e412deb64f6eeaeb486ff59c7f814b688141c373288fd234012ba5e768d

UI SHA256: 3e84fb354a94aa295998f218f455986bd48c8da544cee1117b4099c45e2034c9
