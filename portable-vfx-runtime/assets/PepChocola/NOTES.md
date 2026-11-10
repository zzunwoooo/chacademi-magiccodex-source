# PepChocola — 빼빼로데이 던전 「초코 막대 공방」 적 공격 VFX

PortableVFX id = `claude:pepchocola/<system>`. 15 systems, all `static`, one-shot, no loop.
Origin = ground point the server gives, +Z = caster's horizontal facing (lines run from the origin along +Z, cones are centred on +Z), +Y up.
The red warning area is NOT in this pack (chatelegraph draws it). Real client loader (portable-vfx-preserved-245d836c-rgba2048-bossfx2 ClaudeConfigLoader): **defs=15 errors=0 warnings=0**.

## Size / scale
`scale = {input: areaRadius, reference: 4, min 0.25, max 4}` (one reference per pack). Every system is authored at its own design size below,
so call it with `vfxref` = that design size: when the telegraph size equals vfxref the server plays it at scale 1 (unit path) and it matches exactly.
If a different size is used, the server sends the size in metres and the client divides by the pack reference 4 — only puddle / almond_boom are authored at 4,
so for the others keep size = vfxref (or the scale will be off).

| system | shape (design size = vfxref) | impact (s after VFX start) | length | layers |
|---|---|---|---|---|
| swing | cone r 6, 200° in front | 0.15 (sweep right→left 0–0.2) | 1.0 | 19 |
| stickrain | circle r 2.5 | 0.5 (stick falls from 8 m, stays till ~1.6) | 1.7 | 24 |
| puddle | circle r 4 | pour 0.1; bubble/ripple pulses 0.1 / 0.6 / 1.1 / 1.6; puddle holds to ~2.4, sinks by 2.6 | 2.65 | 20 |
| cross | line +Z 0→14, width 2.5 | 0.1 | 0.95 | 20 |
| fondue | circle r 9 | 0.15 (0–0.15 rumble + circle draw-on) | 2.1 | 38 |
| almond_mark | r 1 ring + almond 2.3 m above feet | — (1.0 s, loops cleanly when replayed every 20 ticks) | 1.0 | 8 |
| almond_boom | circle r 4 | 0.1 | 1.3 | 15 |
| soldier_thrust | line +Z 0→6, width 1.5 | 0.1 | 0.7 | 12 |
| almond_roll | line +Z 0→8, width 2 | 0.1 | 0.9 | 12 |
| fairy_sprinkle | circle r 3 | 0.4 (sprinkles fall from 3 m) | 1.25 | 16 |
| swing_w / stickrain_w / puddle_w / cross_w / fondue_w | same as the base system | same | same | same |

`_w` = white-chocolate shiny (화이트 쇼콜라): identical layers/timings, white-chocolate body + strawberry-pink accents (materials `*_w`, stick `T_StickW`).
The client loader reports durationTicks including particle tails (e.g. swing 41, fondue 84, puddle 105).

## Look
Chocolate = alpha-blended dark bodies (rgb 0.6 body grey × dark-brown tint) with lighter rims + separate additive gloss-highlight twins
(`*Gloss` textures = highlight only) → glossy liquid chocolate, not fire/mud. Biscuit beige stick cores, pink strawberry line work / sigil / god rays,
pink·mint·yellow sprinkles (white variant: pink·red-pink·pale pink). Ground: crisp mesh rings, dashed rings, {8/3} polygram + 8 heart outlines + heart sigil (fondue),
cracked decals, puddle decals, scuffs. Stick = 3 crossed planes with a baked pepero texture.

## features.required
additiveBlend, alphaBlend, billboard, bloom, drag, flipbook, gravity, hdrTint, instanceScale, linearVelocity, meshParticle, noise, orbitalVelocity,
radialVelocity, rotation3D, rotationOverLifetime, stretchedBillboard, tonemapNeutral.
High impact: meshParticle (all arcs/rings/sticks/crowns/waves), bloom, orbitalVelocity (swing speed lines only). Nothing unsupported, nothing substituted.

## Textures (24, unique RGBA 3.32 MiB, all ≤ 512 px)
T_Swipe_Sheet 512² (1×8 wipe) 1 MiB · T_SwipeGloss_Sheet 256² · T_Crack / T_Dust_Sheet (4×4) / T_Puddle / T_Sigil 256² · T_Crown / T_CrownGloss 256×128 ·
T_Stick / T_StickW 64×512 · T_Scuff / T_Streak 256×64 · T_PuddleGloss / T_Almond / T_Chunk / T_Glow / T_Star 128² · T_Drop / T_DropGloss 128×64 ·
T_Bubble / T_Dot / T_Heart / T_Sprinkle 64² · T_Line 64×32.

Generator sources (Java, no python/node): 바탕화면\클로드\빼빼로데이던전\vfx\PepChocola\source (Tex.java textures, Gen.java vfx.json, Preview.java preview, build.sh).
