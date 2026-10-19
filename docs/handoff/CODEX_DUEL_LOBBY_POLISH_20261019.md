# Duel lobby polish integration — 2026-10-19

Status: PERSONAL CLIENT DEPLOYED AND VERIFIED; TEMPORARY LAUNCHER v0.1.14 BUILT/VERIFIED, NOT PUBLISHED.

## Source and scope

Read AGENTS.md and the latest 2026-10-19 section of CLAUDE_KYUDICHI_20261017.md before integration. Feature 289c6f9 is exactly one commit on deployed status HUD baseline c5a2ccf. Merged on codex/operating-duel-lobby-polish-20261019. Built code: 75f7b8b13cbc6bf124d28f4c3c843e25cf4e8ea4.

Changed production Java: kui/LobbyView.java, KyudichiLobbyScreen.java, PartyScreen.java, KyudichiCanvas.java and the supporting kui/Canvas.java documentation. Samples.java and handoff updated upstream. No server, protocol or texture change. Existing status HUD and all operating patches retained. Separate new HUD design PNGs and status icon recolors were not included.

Behavior: use Minecraft skin face plus hat instead of portrait illustration; align left controls and right information to wide-screen edges while lists stay centered; smaller one-line 1:1 title and 70% spell-settings block; click special-key cap to rebind vanilla swap-hands (ESC cancels, UI/spell conflicts rejected); party window opens above lobby and returns to it when closed.

## Verification

- Client test/build/remapJar succeeded. 381 tests, zero failures/errors/skips. Five new regression tests cover wide-screen hit targets, 70% special-key hit bounds, duel exclusion, centered ranking rows/right search and balanced transforms across modes/tabs/aspect ratios.
- All 1550 assets entries in the existing client are byte-identical; no resources were replaced or added.
- Existing Java2D renderer generated 21 synthetic sample previews. Wide duel and Kyudichi images visually inspected for clipping/overlap. This was not a live Minecraft capture; skin/broom sample drawings are stand-ins.
- Initial packaging failed with host disk free=0. Reclaimed only this work's disposable build intermediates, auxiliary source JARs and redundant v0.1.13 packaging output. Canonical public release files, runtime JARs, source, tests and evidence retained. Successful retry verified.

## Personal deployment

Version: 0.40.1-integrated-20261018.1-statushud.1-lobbypolish.1+mc1.21.4

JAR: magic-codex-ui-0.40.1-integrated-20261018.1-statushud.1-lobbypolish.1+mc1.21.4.jar

SHA-256: 53115bcfae26edb072f70158e338227f2bc4b46a7db20dd7db3e71390d1cdaaf

Replaced one personal MagicCodex UI JAR, verified installed hash. Other 14 mods and options.txt preserved. No backup, game force-close, game launch, live-game capture or maps copied. If Minecraft was already running, the new mod takes effect on the user's next game launch.

## Servers

No server JARs copied and no restart issued. Six previously deployed Bridge/Mythic JAR hashes match and all four service PIDs/alive states are unchanged before/after this client task. Existing server build/tests were not rerun because no server code changed.

## Temporary launcher

v0.1.14 prepared from current public v0.1.13/main c940587. Candidate commit ae12305fd47284f1e5d3e52c6414dc763e5b11ae; eight release assets with SHA-256 checksums prepared. Only MagicCodex UI distribution module changed; 84 other modules and resource links preserved. Launcher lint, 10 tests, packaged ASAR byte checks, updater SHA512/size and NSIS/blockmap checks passed. Packaging used normal compression to reduce transient disk/CPU cost; runtime and manifests unchanged by that build option.

No release uploaded or published, no main feed changed. Public remains v0.1.13. Publishing the new v0.1.14 assets to zzunwoooo/chacademi-launcher and updating its main distribution/payload/package feed requires approval for that new release; earlier approval explicitly covered v0.1.13. No new publication attempt or automatic-review rejection occurred in this task.

## Manual acceptance

- `/큐디치미리보기 로비` and `/큐디치미리보기 로비1대1`: check one-line title, equal slot sizes, screen-edge alignment at wide aspect ratio.
- In actual lobby `/큐디치`: use invite or configured party key (default P); party overlays lobby; ESC/close returns to the same lobby.
- In Kyudichi mode click special-key cap: unused keyboard key binds swap-hands; ESC cancels; UI/magic-bound keys are refused. Verify after reopen/relaunch. This changes the user's chosen key only when they perform that action; deployment did not overwrite options.
- Check portrait-bearing player now uses skin face and hat in lobby. Check status HUD and existing keys still work.

Live game/multiplayer interaction was not performed. Permission/key/party server interactions are code-reviewed and compiled; synthetic geometry tests do not replace those manual checks.

## Evidence

A repository-safe verification summary is in CODEX_DUEL_LOBBY_POLISH_ARTIFACTS_20261019.json. Detailed host paths and service process metadata are retained privately on the deployment host and omitted from this repository.
