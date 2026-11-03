# MordianShadowGate — converter notes
- 2 `static` systems, P = ground point under the boss (local +Y up), started separately by the mod:
  - `sink`: start with the **vanish** motion (t=0 = motion start; body melts 0.3–1.2 s). Tentacles grip 0.12–1.1 s, final gulp flash at 1.15 s, pool gone ~1.6 s.
  - `rise`: start with the **emerge** motion at the new position (rises 0–0.9 s). Ink column/tendrils grow with the body, peak burst at **0.9 s** (landing), gone ~1.45 s.
- Runtime `areaRadius` (scale §5.3, reference 3 m, clamp ×0.25–×1.6) scales the whole instance about P. Boss: 3 (or omit). Player shadow clone emerging: areaRadius ≈ 1 (×0.33).
- Rune circle layers use fixed angular velocities; R06 flare starts at rot3 = −0.5·0.9 so it lines up with R03.
- High-impact features: meshParticle (pool decal, rune circle, tentacle/geyser meshes, shock bands), bloom (HDR tints), orbitalVelocity (inward vortex motes/mist, rising base mist), instanceScale (areaRadius).


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
