# MordianClaw — converter notes (모르디안의 원령 거대 할퀴기)

## Placement (role `static`, SCHEMA §5)
- Two independent systems: `claw_right` (motion `attack`, right hand: boss's upper-right → lower-left) and `claw_left` (motion `attack2`, mirror).
- Start one new instance at **motion time 0.45 s** (hit start). P = the boss's feet (ground point under the boss centre), M = the boss's horizontal facing at that moment (+Z forward, +X = boss's right).
- Timing inside the effect: the stroke heads sweep the whole arc in ~0.18 s (0.45 → 0.63 of the motion), ground gouges appear at 0.10 s, tip splash at 0.18 s; everything is gone by ~1.1 s.
- Rage 3-hit combo (HP ≤ 30%): just start a new instance per swing (right / left / right); instances overlap safely.
- Coverage: 3 parallel claw ribbons on an arc 3.5–5.6 m (outer line ~6.2 m) in front of the boss, ±58–62° to each side, height 4.2 m → 0.6 m. No warning decal (the game's own telegraph is used).

## Features
- High impact: `meshParticle` (claw ribbons, ground gouges, tip splat — the whole focal shape), `flipbook` (T_ClawSheet 1×16 wipe = the sweep; ink mist / splat 4×4), `bloom` (HDR tints on rims), `alphaBlend` (ink bodies) + `additiveBlend` (rims, sparks).
- `stretchedBillboard`, `linearVelocity` (space local), `noise`, `drag`, `gravity`, `rotation3D`.
- No orbital velocity, no attachedFollow, no trails, no client-unsupported fields.

## Notes
- The claw sheet is used by 4 materials (ink body α-blend, wake α-blend dim, rim glow additive, hot core additive) — same texture, load once.
- The sweep comes from the flipbook wipe on fixed meshes, so the claw path is baked in boss space (no runtime input besides P/M).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
