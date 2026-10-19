# Latest HUD v4 and duel lobby integration - 2026-10-19

Supersedes the earlier intermediate lobby-only report. Final build includes the complete HUD chain 37f8643 -> bd6db65 -> 14c2606 -> 7163262 and duel lobby 289c6f9. Both feature tips are ancestors of this integration branch.

The native 2048x84 gold HUD frame, clipped gauges, 16 new 256px status icons and timer/text rendering are included. Name/divider/damage coordinates remain the exact latest 7163262 values (-70/-12/-24) in their intended 2048px layout. No 1237px adaptation remains. HUD Java matches 7163262 and lobby Java matches 289c6f9.

Client version: 0.40.1-integrated-20261018.1-statushud.3-lobbypolish.1+mc1.21.4

SHA-256: 56d65a4b8ee9c800d4819229d29e8398095892b73479bb1f1bc57c73721761a0

384 client tests passed, including wide-screen lobby hit targets and actual PNG/font geometry at six GUI widths and four device scales. Actual gauge pixels remain clear; decorative frame/stroke contact is recorded in the local geometry audit. Runtime remapJar/build passed; optional source archives were excluded under host disk constraints. All 25 approved asset changes match upstream and 1528 unrelated assets are preserved.

Temporary launcher v0.1.14: candidate 994472982e24f58f12bf339465cb4553e8d6707f, eight files prepared; launcher lint, 10 tests, ASAR and updater/blockmap checks passed. Only the UI module changed; 84 other modules retained. No public release or main feed update attempted. Publishing v0.1.14 to zzunwoooo/chacademi-launcher and updating its main feed remains a separate approval scope.

No server source changes or deployment were needed. Local personal-installation evidence is reported separately and is not included in this repository summary. No private installation paths, settings or service process data are included.

Manual checks: reopen Minecraft normally; use /대상체력바미리보기 일반, 이로치, 보스, 플레이어 and /큐디치미리보기 로비, 로비1대1. Verify new HUD appearance, special-key rebinding and party overlay/return. Live gameplay was not captured.