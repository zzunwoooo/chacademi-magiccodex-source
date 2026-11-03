# MordianSlam — converter notes
- 1 `impact` system. Spawn at slam motion **0.95 s** (both hands hit the ground). hitPoint = ground point 4–6 m in front of the boss, surfaceNormal = up, incomingDirection = down (any).
- Size authored for the boss: crack decal Ø9.5 m, shock rings to r ≈ 8 m (G08), spike crowns to r ≈ 4 m / 3.4 m tall. No scale input.
- No warning decal — the game's own telegraph precedes this; the effect starts at the hit.
- Dark bodies (crack, spikes, shards, ink mist) are alpha-blended with HDR tint 2.2–2.3: the ink stays black, the violet rims bloom. Keep that blend/tint split.
- Spike crowns (M01–M03) are single mesh particles holding many crossed-ribbon spikes; sizeLife grows them out of the impact point.
- High-impact features: meshParticle (decals, rune circle, shock bands, spike crowns, ground swirl), bloom (HDR tints). Total ~2.4 s.
