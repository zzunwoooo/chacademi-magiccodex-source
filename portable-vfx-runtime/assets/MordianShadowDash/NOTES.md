# MordianShadowDash — converter notes (모르디안의 원령 그림자 돌진)

## Placement (role `attached`, SCHEMA §5)
- One system `dash`. Start a **new instance at motion `dash` 0 s** for each of the 3 dashes. anchor = boss feet every frame (the mod moves the boss);
  facing = **dash direction**, fixed per instance.
- Inside the effect: 0.22–0.42 s ink pools/gathers at the feet (crouch), **0.42 s launch** splash at the start point, 0.42–0.75 s tail emitted along the path,
  0.75 s arrival skid. Visible ~1.1–1.3 s.
- The tail (mist, ink streaks, violet wisps, motes, ground scratch segments, dust) is world-space with `rateOverDistance`, so it follows any dash length/speed.
  Demo assumes 8 m in 0.33 s; a 4 m scenario is included.
- If the real dash starts at a different motion time, shift by starting the instance (motion launch time − 0.42 s) earlier/later.

## Features
- High impact: `attachedFollow` (required: the tail is laid along the real movement; a fixed-point renderer would pile everything at the start), `meshParticle` (ground splat, scratch segments, launch ring), `bloom`, `flipbook`, alpha + additive blends.
- `worldSpace`, `rateOverDistance` (world layers only), `stretchedBillboard`, `linearVelocity` (space local = dash basis), `radialVelocity` (crouch gather), `noise`, `drag`, `gravity`.
- Ground scratch segments (world mesh) take the system basis (dash facing) — keep facing constant during the dash.


## PortableVFX client compatibility (tools/client_compat.py fix)

- no visual change
