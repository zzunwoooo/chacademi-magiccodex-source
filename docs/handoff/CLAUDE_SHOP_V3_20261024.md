# Claude 2026-10-24 (not built): 일반 NPC 상점 v3 — 4×3 카드 · 페이지 · 정렬 · 하단 상세바

Branch `claude/shop-admin-inventory-20261024`, the commit after b01c784. The shop-admin work is in [CLAUDE_SHOP_ADMIN_20261024.md](CLAUDE_SHOP_ADMIN_20261024.md).
**Client mod only.** The player-shop protocol limit raised in the admin commit (40 → 256 products) still needs the Bridge on the same deploy.

## 1. What the owner asked for

Rebuild the general shop window to the reference in the owner's ZIP "일반 NPC 상점 12칸 UI v2". The ZIP holds `reference.png` (the approved mockup), `shop_frame.png`, `button_primary.png` and a README.

The README rules this follows:

- Drawing order: game screen → light blur → **navy backing in code** (alpha 0.8, `0xCC0B1828`) → `shop_frame.png` at alpha 1 → products, text and buttons at alpha 1.
  - No overall opacity is applied to the whole UI.
- Layout:
  - Catalogue of **4 columns × 3 rows**.
  - Search, the 전체 / 구매 / 판매 filter and **sort** across the top.
  - Description, quantity, total and buy/sell across the bottom.
- Card borders, the selection line, arrows, the search field and secondary buttons are **runtime geometry**.
  - Only the frame and the gold trade button are textures.
- The NPC illustration is separate from the item area.
  - It is drawn before the backing, and its width is capped so its right edge stops before the first card column.
- The old opaque panel is **not loaded** with the new pack.
  - `ShopView` no longer touches `v2/shop_frame`, rows, tabs, the footer panel, balance, scrollbar or navy/gold button PNGs.
  - From v2 only `coin.png` and `search.png` (icons) are still used.
  - The v2 files are left in place; delete them once the owner confirms.

## 2. Files

- `textures/gui/shop/v3/shop_frame.png` and `button_primary.png` are copied byte-for-byte from the ZIP.
  - SHA-256 `5828d718…f24b6` and `8977c4b3…7f8dec`.
- `ShopView` is rewritten for the same 1672×941 reference space.
  - The frame is drawn at one uniform scale `K = 0.9`; its outer line sits at x421..1650, y41..900.
  - Lines measured in the PNG: outer x37..1402 / y62..1017, ornament y140, header line y176, footer line y887.
  - Header: compass emblem, title, coin, 보유 골드 and amount.
  - Search row:
    - search field
    - three tabs, gold when selected
    - divider
    - sort dropdown: 기본순 / 가격 낮은순 / 가격 높은순 / 이름순
  - Cards: 4×3, each with icon, name and price.
    - Sell-only items show a cyan 「판매」 tag and 「보유 N」.
    - The selected card gets a gold 2.5px line and an inner glow.
  - Pager: ‹ n / m ›. The mouse wheel turns pages.
  - Footer:
    - item icon (hover shows the tooltip), name and first lore line
    - 구매 and 판매 prices
    - quantity stepper and 합계
    - the **판매 / 구매 switch** (navy; in the 전체 tab it switches direction when the product trades both ways)
    - `button_primary.png` as 구매하기 / 판매하기, with tint for hover, pressed and disabled
  - Bottom-left: role and NPC name over a soft dark band, with a small compass and a thin line, as in the reference.
    - The old `nameplate.png` is not used.
  - The close button is removed because the reference has none. ESC closes.
- `ShopScreen`:
  - `scroll` and drag are replaced by `page`.
  - Adds sort and the open state of the sort list. The open list takes the next click; ESC closes it.
  - The footer switch is added.
  - The card-row 구매/판매 buttons are gone. Trading only goes through the footer `trade()`, the same single path as before.
  - Protocol, price checks, quantity limits and sounds are unchanged.
- `HudMesh.diamond(rx, ry)`: a diamond with separate half-width and half-height, used for star points.
- `ShopViewTest` is rewritten for the new geometry:
  - footer boxes don't overlap and stay inside the footer band
  - header and grid stay inside the frame
  - the NPC never reaches the cards
  - card, icon, page and arrow hits
  - the open sort list takes all clicks
  - card price direction
  - The test ran green with a minimal JUnit stub.

## 3. Preview

`tools/shop-preview/Preview.java` draws `ShopView` with Java2D, using the real fonts and PNGs. Args: `<assets dir> <font dir> <icon dir> <portrait png> <out png> [open]`.

- Icons in the preview are crops of `reference.png`.
- `docs/handoff/img/shop/p_v3.jpg` and `p_v3_sort.jpg` are the previews.
- `compare_v3.jpg` puts the reference on top and the preview below.

## 4. Not done / check in game

- Not built with Gradle. Only `ShopView` and the test compiled with plain `javac`. `ShopScreen` and `HudMesh` need the real build.
- In game, check:
  - the backing alpha against the real blurred world (README: try 0.75–0.85)
  - frame sharpness at GUI scale 2, 3 and 4
  - item icons at 84px
  - the hover tooltip
  - a page with fewer than 12 items
  - 200+ products (피피 잡화점)
- Categories are still not shown to players. The sort list's 기본순 keeps the admin order, which is grouped by category.
  - Adding categories to the dropdown later needs `category` in `ShopProtocol.Product`.
