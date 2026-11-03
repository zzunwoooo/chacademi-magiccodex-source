# MordianSealBeam (모르디안의 원령 — 기믹 ⑥ 대마법사의 봉인) — converter notes

A ~2-block-wide black beam from the boss to a seal-stone pillar: black core + darker spine, violet glowing rims, violet helix streaks and ink ribbons
flowing to the target, rare violet crackles, a rotating violet seal ring at the boss end and a splash (star, ripple rings, ink + sparks) at the target end.

## Systems
| system | role | placement | start / stop |
|---|---|---|---|
| beam | link (loop, 60 s cycle) | source = **boss beam point** (casting hand / chest socket, every frame), target = **pillar surface point facing the boss** (beam hit point, ~1.4 m up) or, while blocked, **the blocking player's chest** (feet + 1.4 m) | start when the beam begins (0.16 s ignition head); while someone blocks just move the target point (no restart); stop on switch/end → fades in 0.08 s (`onStop`) |
| beam_blocked_hit | attached (loop 1 s) | the **blocking player** (feet, facing — effect is omni-directional) | start when that player starts blocking, stop when blocking ends |
| pillar_crack | impact (one shot, ~2.6 s glow) | hitPoint = beam end on the pillar surface, surfaceNormal = pillar surface normal there (toward the boss), incoming = beam direction | play once per crack stack |
| beam_switch | link (one shot, ~0.6 s) | the OLD source / target | on target change: stop old `beam`, play `beam_switch`, start a new `beam` on the new target in the same tick |

- Every 6–8 s the game switches target as above. No runtime input besides the points.
- Crack decal is a flat quad 1.75 m on the surface (local XZ); on a round pillar the edges may float a little (pillar radius ≥ 0.8 m reads fine). Several stacks overlap (one instance per stack).
- Beam body = 20 fixed-length stretched segments (`lengthScale`, `velocityScale` 0.02): any beam length works; boss dashes do not stretch them. Path render velocity is taken in the link frame (SCHEMA §4.8, as in the preview).

## Features
required: linkTarget, pathFollow, attachedFollow, meshParticle, stopTail, flipbook, flipbookCycles, bloom, stretchedBillboard, additiveBlend, alphaBlend, hdrTint, rotation3D, rotationOverLifetime, worldSpace, noise, drag, gravity, linearVelocity, billboard, tonemapNeutral.
High impact: **pathFollow + linkTarget** (the beam), **meshParticle** (seal rings, crack decal), **bloom**, **attachedFollow** (blocked hit on the player). Nothing outside the client's supported list is used.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
