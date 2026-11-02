# AM_StagLantern (재의 그림자 던전 잡몹, 2026-11-03)

등불 사슴 원거리. charge / charge_shiny: static 0.8초, 원점 = 사슴 발밑·정면(chavfx at=caster), 레이어는 위 2.1 · 앞 1.0(등불). bolt / bolt_shiny: projectile(chamobshot 이 이동). hit / hit_shiny: impact 착탄 원 r 2.5, 1.0초. scale.input areaRadius 기준 2.5 (charge·bolt 는 기준 크기로 재생).

- 빨간 예고 없음 (모드 chatelegraph 가 그림).
- `_shiny` 시스템 = 같은 레이어, 색만 (흰 재 + 하늘색 불 + 금빛 선).
- 클라 호환 범위 안에서만 작성 (client_compat check OK). 빠진 기능 없음.
- 원본: portable-vfx-runtime/tools/ash_shadow_vfx (lib.js + lib_m.js + packs/AM_StagLantern.js, 텍스처 gen_ash_m.py, build_m.sh).
