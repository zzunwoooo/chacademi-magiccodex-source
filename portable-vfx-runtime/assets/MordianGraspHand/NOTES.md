# MordianGraspHand — converter notes
- 1 `impact` system, one instance per grab. hitPoint = ground under the target player's feet, surfaceNormal = up. ~0.95 s; radius ≈ 2 m.
- The mod spawns it 3 times per player at 0.8 s intervals (consecutive instances overlap ~0.15 s — intended; pool/rune ring re-open).
- Timeline: pool + rune ring open 0–0.08 s → hand (4 finger thorn-bundles + thumb) shoots up open 0–0.3 s → snaps shut 0.3–0.52 s (snap flash / ink splash / scratch at 0.42 s — the natural damage tick) → sinks 0.52–0.95 s.
- Each digit = 3 mesh particles handed off at 0.3 / 0.52 s (rise / snap / sink) with matching rotation3D (Euler Z tilt = open/close, Y = azimuth). Keep the per-layer timing exact or the fingers will jump.
- High-impact features: meshParticle (fingers, pool, rune ring, rings), bloom (HDR tints), orbitalVelocity (palm mist only).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
