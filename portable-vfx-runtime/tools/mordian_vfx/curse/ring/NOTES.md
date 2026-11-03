# MordianForbiddenRing — converter notes (모르디안의 원령 "금지된 원")

One static system `forbidden_ring`: P = boss centre projected to the ground, basis = boss horizontal facing (+Z = front). Start it at boss motion `roar` 0.8 s (t = 0). One-shot, emission 1.2 s, everything gone by ≈2.5 s.

Timeline (t after start):
- 0.00–0.44 stage 1, DONUT (r 5–13 m): 16 eruption points at r ≈ 9 m (±0.8) chained from the boss FRONT around both sides to the BACK, one step every 0.055 s
  (k = 0..8, angle ±k·22.5°). Each point: black ink pillar (mesh, 4.8–6.4 m) + 2 smaller side pillars within 3 m + ink geyser flipbook + ink thorns + hit line ring + small star.
  Outer forbidden circle line work (r 13 / 12.55 rings, {8/3} octagram tangent to the inner circle, vertex circles, 28-segment rune band at r 11.75, inner edge ring r 5.45) flashes at 0.00 and 0.28 and fades out by ≈1.25.
- 0.55–1.00 anticipation: a line ring tightens onto r = 5, the inner rune band wakes dimly, motes are drawn toward the inner circle.
- 1.00 stage 2, INNER DISC (r < 5 m): 6 pillars at r 3.4 m (1.000–1.075 s ripple) + thorn crown r 2.4 + ink bursts / billows + scratch fragments + rays + arcs;
  inner forbidden circle (r 5 ring, hexagram, rune band r 4.2, centre sigil quad 9.9 m) flashes, holds and fades by ≈2.4.
- Damage timing suggestion for the server: donut hit at ≈0.0–0.45 (chain), inner hit at 1.0.

- The circle line work is mesh line strips (one tiny T_Line profile texture) + rune bands (T_RuneStrip mapped per arc segment) — keep the meshes as given (vertices in metres, size 1).
- 145 layers: per-point layers are generated (C00..C15 a–f, I0..I5 a–c), each with its own `position` and burst time. Their order/timing is the chain; don't merge them.
- No runtime scale input (radii fixed at 5 / 13 m as designed). If the server radii change, scale uniformly about P by r_outer/13.
