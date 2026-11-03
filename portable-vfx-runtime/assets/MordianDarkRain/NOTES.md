

## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change

## Attachment (모르디안의 원령 — 어둠의 비)
- One `projectile` instance per meteor, spawned ~15–20 m above the target point, falling almost vertically (forward ≈ down; basis rule of SCHEMA §5 with the degenerate fallback right = (1,0,0) is fine — every layer is symmetric about the travel axis).
- impact: ground hitPoint + surfaceNormal; radius ~3 m (shock ring / crack decal 6.4–6.8 m wide). Air hit supported (normal = -incoming).
- Many meteors overlap: shared small textures (1.95 MiB unique RGBA), ~110 particles per impact. Crack decal + glow share a fixed yaw (0.4 rad).
- No warning decal (the game has its own telegraph).
