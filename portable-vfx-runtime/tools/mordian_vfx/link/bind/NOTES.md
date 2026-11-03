# MordianChainBind (모르디안의 원령 — 기믹 ① 결박의 사슬) — converter notes

A black chain binds two players for up to 20 s; safe distance 8–14 m. Ink-bodied faded-gold links + a wound shadow band + faint violet afterglow,
a violet shackle ring (the chain passes through it) and a thin waist ring at each player. Warning states are separate overlay systems the game toggles.

## Systems (all `role: link`, the same two points every frame)
source = **player A chest** (feet + 1.0 m), target = **player B chest** (feet + 1.0 m). z runs A → B.
| system | loop | start | stop |
|---|---|---|---|
| bind_hold | yes (60 s cycle; held links are one ≤ 60 s particle each) | bind starts (0.35 s draw-in: both ends first, meeting flash in the middle) | bind ends (any reason) → links/rings fade in 0.25 s (`onStop` jumpToTail) |
| bind_slack | yes (2 s) | distance < 8 m (enter) | distance ≥ 8 m (leave) — sagging ghost chain, dark mist droop, ink coils winding the chain, shackles tightening pulse 1.6/s |
| bind_taut | yes (2 s) | distance > 14 m (enter) | distance ≤ 14 m (leave) — straight violet arcs, white-lilac crack light, 3-scratch flashes, sparks and strain at both shackles |
| bind_break | no | at the end (time out / resolved / punishment), same frame as stopping bind_hold (+ slack/taut) | — (~0.8 s of shards) |
- slack and taut run ON TOP of bind_hold; never both at once. Re-entering a state = start a new instance (old one stopped).
- Links: 28 stationary path particles (fixed progress), alternating face-on / edge-on mesh quads along z → spacing = distance / 28. The held chain has a fixed slight sag (bend y −0.28 m at the middle); the slack overlay adds a deeper sag (−0.9 m), the taut overlay a nearly straight line (−0.05 m). The base chain does not physically straighten (path bend is per particle at spawn).
- All stretched path segments are FIXED length (`lengthScale`, `velocityScale` 0.02) so players walking apart / together never stretch them past a player. Path render velocity is taken in the link frame (SCHEMA §4.8, as in the preview).
- No runtime input besides the two points.

## Features
required: linkTarget, pathFollow, meshParticle, stopTail, flipbook, flipbookCycles, bloom, stretchedBillboard, additiveBlend, alphaBlend, hdrTint, rotation3D, rotationOverLifetime, worldSpace, drag, gravity, linearVelocity, billboard, tonemapNeutral.
High impact: **pathFollow + linkTarget**, **meshParticle** (links, shackle rings, coils), **bloom**. Nothing outside the client's supported list is used.
