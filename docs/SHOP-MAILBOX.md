# MagicCodex admin shops and mailbox

This implementation adds system mail and ordinary administrator shops. It does not add player auctions, stock rules, personal mail sending, fees, expiry, or automatic deletion.

## Player controls

- `/우편함` or `/codexmail`: paged list, detail, original item attachments, selected-mail claim, claim all that fit, and delete all fully claimed mail.
- Inbox search filters the current page. Overflow remains in mail; it is never dropped into the world.
- `/상점 <id>` or `/codexshop <id>`: NPC portrait on the left, products on the right, separate purchase/sale tabs, server search, direct numeric quantity entry and +/- controls, immediate total.
- Purchases create system mail. Sales remove only matching storage-inventory items, then pay through Vault. Armor and offhand are not implicitly sold.
- Up to nine attachment stacks per mail/transaction; the server validates the item's actual maximum stack size and the quantity limit. The catalogue response is bounded to 40 products; search narrows larger shops.

## In-game administration

Permission: `magiccodex.shop.admin` (default op). Player shop/mailbox access defaults true. Admin checks run on the server.

```
/상점관리 생성 general 일반상점
/상점관리 아이템추가 general 100 off
/상점관리 가격 general <product-UUID> 100 25
/상점관리 NPC general citizens:1
/상점관리 NPC general tag:general_shop
/상점관리 초상 general elena-neutral
/상점관리 목록
/상점관리 목록 general
/상점관리 기록 <player-UUID>
```

Hold the original item in the main hand to register it. A cloned one-item template preserves all serialized components, PDC and custom NBT; registration does not consume the held item. `off` disables purchase or sale independently. The product UUID is returned after registration. Prices are nonnegative, at most 1e12 with at most six decimal places; transactions additionally respect the Vault provider's fractional-digit contract. No economic balance is inferred from client/UI caches.

Citizens remains optional. Existing `elena-neutral.png` is reused; no replacement NPC illustration is invented. Entity-tag and Citizens bindings are scoped to the server's persistent mailbox origin ID so equal NPC numbers on different servers do not collide. ChacaNPC is not modified.

## Trusted integration API

`Bukkit.getServicesManager().load(MailService.class)` exposes:

- `sendSystem(sourceKey, recipient, title, body, attachments)` on the server thread. Capture original ItemStacks before submitting DB work. Completion is asynchronous; UI/Bukkit follow-ups must return to the server thread.
- `restoreDeletedMail(recipient, mailId)` on the server thread. Restores the owner-matched soft-deleted header and retains claimed attachment states.

`sourceKey` is the producer's durable operation key. Repeating it returns the original mail ID and does not duplicate attachments. Shop purchase keys are `shop:<order-UUID>`.

## Persistence and recovery boundaries

Existing `DatabaseSettings` / `ConnectionHolder` are reused. MariaDB uses `codex_mail*` and `codex_shop*` tables, InnoDB and utf8mb4. SQLite uses `mailbox.db` / `shops.db` with the existing WAL/FULL settings. SQLite files are local to one server; they do not provide cross-server sharing. Use the same existing MariaDB configuration for school/wild to share definitions, mail and ledgers. No credentials or live DB configuration are committed or changed by this task. Schema initialization occurs only when the new module is enabled; tests create temporary isolated SQLite databases.

`mailbox-origin.txt` is a stable random server identity created at first enable. Keep each server's own value through restarts. Do not copy the same identity to another server.

All mailbox owner writes serialize through a DB owner row. Attachments transition `ready -> pending -> claimed`; pending claims are neither deletable nor reservable again. Original item bytes are stored independently from bounded display previews. Catalogue changes use version polling every five seconds and revision validation again inside the transaction.

Inventory changes and unique receipts are saved together on the origin server. The receipt is read back from the default world's `playerdata/<UUID>.dat` before the DB marks a claim complete or begins sale payment. A pending claim can be settled after reconnect only when that origin server and its durable receipt agree. With no receipt, a different origin, a malformed save, or a DB failure, the claim stays held for review instead of being blindly released/replayed. Soft deletion requires owner match and no unclaimed/pending attachments; rows and payloads are retained for recovery.

Purchase ledger: `prepared -> paid -> done`. Vault is called once for a prepared attempt; durable paid orders resume only mail creation using the same source key. A crash between Vault debit and the paid DB commit is ambiguous and stays held. Sale ledger: `prepared -> sale_removed -> credit_pending -> done`. A crash around deposit likewise stays held; it is not deposited twice automatically. `/상점관리 기록` exposes incomplete order IDs and states for operator investigation. There is no automatic refund or assumed completion of an ambiguous external effect.

**The DB, Vault and inventory are not one distributed transaction.** Durable receipts reduce ambiguity; they do not replace provider-specific payment receipts or another plugin's cross-server inventory synchronization. This implementation does not alter an inventory-sync provider or enforce global exclusivity on unrelated economy plugins. A provider returning stale balances must be fixed at that provider, not hidden with a MagicCodex cache.

The inspected school/wild configuration currently has no MagicCodex `database.properties` (SQLite default). Both have Vault/XConomy JARs; XConomy points to matching MySQL settings but `SyncData.enable` is false. Shared-money correctness and live provider registration remain deployment prerequisites, not a result claimed by offline tests. Citizens was not installed in the inspected server plugin lists.

## Validation and visual status

Focused tests cover owner isolation, source-key replay, actual two-connection claim races, pending-state fencing, cancellation before inventory mutation, soft-delete restoration without regrant, exact stored bytes, malformed protocol/quantity input, stale catalogue revision, disabled buy/sell, transaction replay, total overflow, server-scoped NPC bindings, and saved/truncated receipt files.

Builds and tests run on hosting staging only. Latest nickname/pet/shiny/equipment assets and code are retained. No production JAR replacement, server restart, credential change or live database migration has been performed.

Approved production backgrounds are awaiting authorized file handoff:

- `shop-panel.png`: 1672x941 RGBA, full image; divider x737, header y118, transaction y709, list inner x770/y211/w821/h467. Runtime NPC/text/items/buttons stay separate.
- `mailbox_panel_background.png`: 1672x940 RGBA; no baked text/buttons. Intended partial opacity should be preserved without extra opacity filters.

The current screens use existing style assets as their fallback. Compile/tests are not in-game visual QA, an ItemsAdder/PDC round-trip gameplay test, live MariaDB contention testing, or an abrupt real-server crash test. Those checks remain required before production use. Mailbox production-background layout integration remains pending the approved PNG.
