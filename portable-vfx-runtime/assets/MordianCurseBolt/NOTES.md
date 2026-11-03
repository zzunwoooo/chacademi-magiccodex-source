

## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## Attachment (모르디안의 원령 — 저주 화살)
- One `projectile` instance per bolt; the game fires 5 (fan) from the boss hand at the brand motion 0.4 s. Pack = one bolt.
- projectile: P = bolt position each tick, forward = travel direction. Local layers (arrowhead, core, coil arcs, ink ribbon) are cleared on hit; world layers (ink puffs, embers, sparks, launch flash) finish.
- impact: hitPoint + surfaceNormal (+Y) + incomingDirection (claw decal yaw is fixed 0.25 rad relative to the incoming direction so the decal and its glow line up). Air hit: surfaceNormal = -incomingDirection.
- No warning decal (the game has its own telegraph).
