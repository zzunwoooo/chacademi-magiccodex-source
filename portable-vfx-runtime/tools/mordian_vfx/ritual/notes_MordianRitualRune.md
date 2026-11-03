# MordianRitualRune — converter / game notes
- Role: all systems `static`, P = rune centre on the ground (surface point), basis = server horizontal facing. Radius ~1.5 m (outer ring 1.48 m, hairline 1.56 m).
- Glyph variant k = 1..5 is chosen by system id: `draw{k}` / `hold{k}` / `erase{k}` / `wrong{k}`. Use the same k for one rune through its life.
  1 = stem + V + bar, 2 = triangle + spine + eye, 3 = crossed diamond + ticks, 4 = three scratches + crescent, 5 = Z + ring + side bars.
- Timeline per rune: `draw{k}` at the boss draw moment (0.8 s; its keep layers fade out at 0.85–1.0 s) → start `hold{k}` at +0.8 s (fades in over 0.15 s; one ≤30 s particle per layer, duration 30 s).
  Erase: when the stepping starts, send stop to `hold{k}` (onStop tail 0.15 s) and start `erase{k}` (1.5 s, light dies from the rim inward, ink flakes; last spark at 1.42 s).
  If the player steps off before 1.5 s: remove `erase{k}` (stop + clear local layers) and start `hold{k}` again.
  Wrong order: start `wrong{k}` (0.9 s) on the stepped rune; keep `hold{k}` running (wrong is an overlay: black distortion, violet cracks, three-scratch flash). No red anywhere.
- Rune band rotation phase in `erase{k}` is not continuous with `hold{k}` (constant-rate rotation cannot be resumed); the band flares off in 0.7 s, hiding it.
- Demo: scenarios r1..r5 / wrong / all use hidden demo actors + events (demo-only).
