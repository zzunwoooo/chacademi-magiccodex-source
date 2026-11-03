# MordianBrand — converter notes (모르디안의 원령 "개인 낙인 폭발")

Two systems, both started by the mod (no damage logic here):

| system | role | attach | start | end |
|---|---|---|---|---|
| `mark` | attached | branded PLAYER: feet position + horizontal facing every frame | boss motion `brand` at 0.4 s (the point) | runs exactly 3.0 s on its own (no stop needed); if the target dies / leaves, stop it (local layers clear) |
| `burst` | impact | hitPoint = the branded player's feet at mark + 3.0 s, surfaceNormal = (0,1,0), incoming = (0,-1,0) | mark + 3.0 s (same tick as the damage check) | one-shot (~1.8 s incl. tails) |

- The mark is a TARGET indicator, not a range telegraph: it never draws the 4 m radius. Head seal at 2.55 m above the feet (player 1.8 m), foot circle r ≈ 0.76 m.
- Speed-up: the turning seal ring and the foot circle are each 3 copies (0–1.0 / 1.0–2.0 / 2.0–3.0 s) with increasing angular speed; each copy starts at the previous copy's angle (`rotation` / `rotation3D` = ω·tStart) so the hand-off is seamless. Keep start rotation, angular speed, lifetimes and burst times exactly.
- Last 0.45 s: line ring + rune ring contract into the player, motes are drawn in, the seal flares → burst at 3.0.
- Burst: violet shock line ring reaches r = 4.0 m at ≈0.28 s (wash quad size 8.9 = r 4 at its outer edge, rim wall flare at r 4 at 0.17 s). Black ink: InkSplash flipbook + ink mist billows, mesh thorn crowns, stretched ink thorns, a 3-plane ink geyser.
- Dark accents are alpha-blended with HDR tint (2.2): the ink bodies stay dark, the lilac rims bloom.
- No runtime scale input (fixed 4 m radius as designed). If the server radius changes, scale the burst uniformly by r/4.
