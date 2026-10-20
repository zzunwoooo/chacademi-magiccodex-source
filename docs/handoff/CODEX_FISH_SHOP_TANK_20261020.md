# Fish shop / tank integration — 2026-10-20

Pinned feature3a6fab365db2552212119a549bf67c0e3acf4754, operating21a88ec. Scoped integration preserves existing fishing,24v2+22 icons, Arcana and held spells, HUD, reactions, crops, Kyudichi and AIM.

Verified: Bridge391, Fabric426, Kyudichi18, Discovery35, Portable136 tests; actual isolated MariaDB two-connection tests included. UI2879 old resources byte-identical,70 new PNGs verified. FishTrap324 is basic/default permission,5mana/2seconds, no aim preview or VFX catalogue entry. Existing cast event policy retained.

Durable fish states and per-withdrawal tokens prevent old/copied items returning after sale/release or another withdrawal. SQL capacity/lock/state transitions are transactional. Inventory consumption follows committed ownership; failed writes do not consume items. A catch write-ahead journal retries storage failures. Ambiguous economy/delivery outcomes remain held for administrator reconciliation and are never automatically paid twice. Payment attempt/result receipts identify affected fish without printing credentials or item tokens.

NPC setup uses the existing server-scoped ShopStore binding table; no NPC is assigned by deployment. Commands:
- /상점관리 NPC 물고기판매소 citizens:<ID>
- /상점관리 NPC 물고기판매소 tag:<태그>
- /상점관리 연결해제 물고기판매소 <NPC키>
- /상점관리 연결목록
- /상점관리 물고기 열기 [플레이어]
- /상점관리 물고기 가격|크기보정|조건보정|초상 ...
Existing /물고기상점관리 NPC|NPC삭제 routes to the same store. Explicit rebinding changes only that NPC; detach checks target kind, preserving another kind. Normal shops/products/prices are not rewritten. No separate NPC-list file is seeded.

Source input UI design/coordinates retained; camera equations and release confirmation extracted for headless tests. Real-game appearance, GPU performance, NPC clicking and full network play remain manual QA. No backup, map copy, forced game closure or gameplay capture. Deployment outcome recorded separately in hosting artifacts/fish-shop-tank-20261020.