# MordianChainSpin — converter notes (모르디안의 원령 사슬 회전)

## Placement (role `attached`, SCHEMA §5)
- One system `spin`. Start at **motion `chain_spin` 0.6 s**. anchor = boss feet every frame; facing = boss horizontal facing **fixed at start** (the model itself spins; do not feed the animated body yaw).
- Two full turns from t = 0 to 1.3 s (= motion 0.6 → 1.9), constant ω = 9.67 rad/s, **clockwise seen from above** (+Y rotation). The two chains start on the boss's right (+X) and left (−X).
  If the model's spin goes the other way, the band layers (S*1–S*5) need rotLife negated and the meshes mirrored — tell the author, do not guess.
- t = 1.3 s: shock ring + ink ring + sparks spread outward from r ≈ 5.6 m to ~9 m. Visible until ~1.9 s.
- Radius: chain band r 5.0–5.5 m at height 0.85–2.05 m; inner chain r 3.7–4.1 m; violet afterglow sector r 1.4 → 5.9 m.

## Features
- High impact: `meshParticle` (chain bands, afterglow sectors, ground rings, scrape marks, shock ring), `orbitalVelocity` (ink dust / spray / chips / gold sparks swept around the ring), `attachedFollow`, `bloom`, `flipbook` (mist / splat 4×4), alpha + additive blends.
- `rotationOverLifetime` (the spin itself), `rotation3D`, `stretchedBillboard`, `radialVelocity`, `noise`.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
