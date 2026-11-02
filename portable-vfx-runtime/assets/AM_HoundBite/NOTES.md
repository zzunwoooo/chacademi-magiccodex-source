# AM_HoundBite (재의 그림자 던전 잡몹, 2026-11-03)

재 사냥개 물어뜯기. bite / bite_shiny: static 부채꼴 r 3.5 · 100°, 0.6초, 원점 = 사냥개 발밑, +Z = 정면, 맞물림 0.1초. 몸통 공격이라 예고 없음(warn=false). scale.input areaRadius 기준 3.5.

- 빨간 예고 없음 (모드 chatelegraph 가 그림).
- `_shiny` 시스템 = 같은 레이어, 색만 (흰 재 + 하늘색 불 + 금빛 선).
- 클라 호환 범위 안에서만 작성 (client_compat check OK). 빠진 기능 없음.
- 원본: portable-vfx-runtime/tools/ash_shadow_vfx (lib.js + lib_m.js + packs/AM_HoundBite.js, 텍스처 gen_ash_m.py, build_m.sh).
