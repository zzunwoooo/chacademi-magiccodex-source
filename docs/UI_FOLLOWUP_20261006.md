# Remaining UI follow-up after f3335bf

Preserves f3335bfca6406d01e2b3e1564d3519c5bc12797f and its pet integration. No deployment, restart, launcher replacement or operational DB/config change.

- Top menu: Cash Shop, Mailbox, Magic Codex, Friends, My Info, Pet Codex, House Points. Existing command names and permissions retained.
- My Info replaces Stats in screen/key labels. Nickname, equipment and titles actions share gold-outline styling using existing menu assets. Equipment/titles removed from the top menu.
- Title preview and full/full_colored placeholders use prefix, suffix, nickname order. Ownership, selected IDs/sides and persistence unchanged.
- Class 1-9 labels replace circle labels. Internal player_circle PDC, APIs, assets and ascension commands remain compatible. Numeric circle research text changes only at display time.
- Held item name retains native rich Text, rarity/custom-name formatting and fade; renders 20 percent larger above hunger/armor chips. Long names are elided while retaining styled segments. Texture filtering is unchanged.
- Quest Journal replaces School Request Board. Paper state colors differ; remaining subquest slots are separate from per-quest completion quota.

Optional quest fields: main defaults false; requires defaults empty, contains at most 16 completed QuestDefinition IDs. Self/duplicate/invalid IDs are rejected. Absent fields preserve legacy behavior and byte encoding. Existing quest contents and main assignments were NOT changed. The existing Dialogue story journal remains separate; no speculative migration.

Prerequisites are rechecked inside the existing per-player DB transaction using durable completion records, including prior cycles. Unconfirmed paying records do not unlock successors. Main quests sort first in eligible tabs and do not use the existing three active subquest slots. No random draw was added: the existing board has no random selection. Fields are available through the existing quest admin form/YAML. No SQL table/column or migration was added.

Response v3 carries main flags and remaining slots; the new client also reads v2. Requests remain v2. Future deployment must update Fabric and Bridge together because old clients cannot parse v3 responses.

Deferred:
- Title close X: actual Library pixels unavailable; geometry unchanged.
- NPC address policy: current per-NPC nickname > ChacaNPC nickname > account name conflicts with always using configured MagicCodex nickname. No override applied. Decision needed: preserve explicit addresses and use configured nickname as fallback, or force configured nickname?
- Actual visual checks for menu style, title slots, item name clarity and paper colors remain pending.
- CodexTextureFilterMixin belongs to the pet worker and is untouched. Pet diagnostic/filter patch and separate ShopAdmin work await final integration.
- Command policy remains pending approval; no overhaul.

Minimum tests: Paper QuestStoreTest (7), QuestRevisionTest (6), QuestPrerequisiteTest (5), TitleTest (5); Fabric TopMenuTest (4), PlayerHudTest (5), StatsTest (4), TitleLayoutTest (3). Final results/hashes are recorded in sibling FOLLOWUP-VERIFICATION.json and provisional JARs in followup-ready. Full regression is reserved for final integration with pending patches.
