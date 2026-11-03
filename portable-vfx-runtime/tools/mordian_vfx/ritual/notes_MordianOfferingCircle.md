# MordianOfferingCircle — converter / game notes
- Role: all systems `static`, P = circle centre on the ground. Radius ~2.5 m (outer ring 2.5 m, hairline 2.62 m, ink stain 3.3 m).
- `circle` = 4 s round, `circle3` = 3 s round (same look, every timing × 0.75). Start at the round start; at the judgment send STOP to it
  (all held layers have onStop tail 0.35 s → they fade together) and start `success` or `fail` at the same P.
  If the judgment comes later than 4 s (3 s), the circle keeps its final fast spin/brightness (held up to 12 s).
- Spin speed-up = staged copies (stage starts circle: 0.42 / 1.44 / 2.4 / 3.2 s; circle3: 0.42 / 1.08 / 1.8 / 2.4 s), each with rot3 = accumulated angle so the hand-over is seamless.
- The number 1~3 is the game's text display; nothing in the pack draws it.
- `fail` spikes are mesh particles (3 crossed vertical quads, base at y 0) on an alpha-blended material (dark body, violet rim via HDR tint).
