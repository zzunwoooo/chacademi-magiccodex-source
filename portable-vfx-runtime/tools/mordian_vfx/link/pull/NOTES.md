# MordianChainPull (모르디안의 원령 — 사슬 끌어당기기) — converter notes

Boss tether motion: wind-up 0.5 s, throw **0.75 s** → start this cast at tether t = 0.75. A black chain (ink-bodied links with faded-gold rims, a wound
shadow band, violet afterglow) shoots from the boss hand to the lowest-HP player in 0.20 s, wraps the player (small violet flash + 3-scratch mark + coiling
shadow band), holds while the game pulls the player in, then snaps into ink / gold shards. No telegraph decal (the game has its own).

## Systems / placement
| system | role | phase | placement |
|---|---|---|---|
| throw | link | begin (0–0.20 s) | source = **boss hand point** (right-hand bone / socket, every frame), target = **player chest** = feet + 1.1 m (every frame) |
| hold | link | sustain (holdDuration) | same two points |
| wrap | attached | sustain | the **pulled player** (feet, facing = player → boss horizontal; the coil is symmetric so facing barely matters) |
| break | link | end | same two points |
| wrapBreak | attached | end | the pulled player |

- Runtime input `holdDuration` = pull time (default 0.85 s, 0.1–3 s). Pull finished / target dead / out of range → end plays (`break` + `wrapBreak`). Cancel = same end.
- Total visible time ≈ 0.20 + hold + ~0.7 s of shards.
- Chain links are stationary path particles (progress fixed at their slot s), alternating face-on (`linkV`) and edge-on (`linkH`) mesh quads oriented along the link z-axis, 20 links → spacing = linkLength / 20 (0.45 m at 9 m). They are held by one 60 s particle each and end through `onStop` (jumpToTail 0.1 s).
- Band / haze segments are FIXED-length stretched billboards (`lengthScale`, `velocityScale` 0.02) so the fast-shrinking linkLength during the pull does not stretch them past the player. The render velocity of path particles must be computed in the link frame (SCHEMA §4.8 step 10), as the preview does.

## Features
required: linkTarget, pathFollow, phasedSequence, attachedFollow, meshParticle, stopTail, bloom, stretchedBillboard, flipbook, additiveBlend, alphaBlend, hdrTint, rotation3D, rotationOverLifetime, worldSpace, noise, drag, gravity, linearVelocity, billboard, tonemapNeutral.
High impact: **pathFollow + linkTarget** (the whole chain), **meshParticle** (links, coil, rings), **bloom**, **attachedFollow** (coil on the pulled player). Nothing outside the client's supported list is used.
