# Final approved UI, shop admin and pet integration

Preserved commits: f3335bf (shop/mail/NPC follow-up and pet targeting), 56ed375 (remaining UI), c2ceaec (pet filtering). This final integration adds configured NPC default names, pixel-grounded title-close placement, and the two isolated operator shop editor patches.

Included:
- Shop 85 percent and mailbox 70 percent, requested layout/feedback/modal/tooltip refinements; NPC choices, speech modal, hearts and chat visibility.
- Seven-item menu order, My Info actions with existing assets, prefix/suffix/nickname composition, Class 1-9 labels and styled/clipped held-item name above hunger/armor.
- Quest Journal state paper colors and remaining subquest slots. Optional main/requires fields preserve legacy defaults/encoding; prerequisites rechecked in durable transactions. Existing quest contents/classification remain unchanged. No speculative random draw or Dialogue-story migration.
- NPC default name comes from the configured plain nickname. User-assigned per-NPC/ChacaNPC addresses remain higher priority. Session/prompt/new event records use the configured default, without title prefix/suffix. Optional v1 facade extension preserves older Bridge compatibility; integrated Bridge and ChacaNPC must be deployed together to enable the new default.
- Title close X normalized from parent-inspected relative screenshot shift: 13px left/25px down at reference width 1014, with native layout width 1080. Native rect is (985,51,42,42). Shared render/hitbox constants and five GUI-ratio checks pass; it does not overlap the nickname action. Screenshot absolute centers are not hardcoded.
- /상점관리화면 [상점ID] opens the isolated operator shop UI using existing magiccodex.shop.admin permission. Product search, authoritative held-stack registration, names/prices/allow flags, confirmation and revision-checked CRUD. Original ItemStack payload retained during edit; paid orders/mail retained during product deletion. No new PNG or DB table. Shared ShopStore/MagicCodexBridge/MagicCodexClient/plugin.yml changes were inspected and patches applied in order.
- Shared Shiny/Taming ModelEngine target resolution and 170-degree pet preview; only pet_model/ and textures/vanilla_shiny/ are exempt from forced blur. UI/font smoothing preserved.

Not included/remaining:
- Entire administrator command policy overhaul: still awaiting user approval.
- Optional JVM texture probe: reviewed but NOT integrated or enabled.
- No fix claimed for GUI depth blur or vanilla shiny color/UV defects. Actual in-game rendering, ModelEngine ray selection/performance, item-name readability, shop admin clicks/live permission revocation and real MariaDB concurrent editing remain unverified.
- No operational DB/config change, deployment, restart, launcher replacement or game launch. Existing school/wild sound interval remains 12 seconds; code fallback is 6.

Host full regression after all approved patches: magic-codex-paper 214, chaca-npc-paper 15, magic-codex-fabric 189; total 418, failures/errors/skips all zero. Java 21; offline Gradle test+jar for Paper/NPC and test+remapJar for Fabric. Existing compileOnly APIs added only to Paper test runtime via staging init script; repository build files unchanged.

Quest responses are v3; new client reads v2 too, requests remain v2. Deployment preparation must pair final Fabric/Bridge and use the rebuilt ChacaNPC for nickname integration. Do not deploy individual worker or earlier next-ready/followup-ready JARs.

Final artifacts, sizes, SHA256, source commit, public branch and complete XML suite counts: C:\Chacademi\staging\shop-ui-refine-20261005-task9\FINAL-INTEGRATION-VERIFICATION.json. Final folder: integrated-ready. Citizens remains the unchanged approved baseline JAR.
