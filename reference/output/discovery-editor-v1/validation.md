# Validation — 2026-09-21

- 8 automated server tests passed: atomic save/reload/history, stale revision rejection, dataset and field validation, draft/review length limits, host/origin/token/path checks, corrupt-file preservation, all 200 entries, static application serving.
- JavaScript syntax and Python compilation checked.
- Browser verified: search, multiline condition edit, automatic file save, review completion, reload persistence, empty-condition validation and disabled completion.
- Export action displayed its completion notice. In-app browser download-event observation timed out, so downloaded file delivery is not verified in this browser. Primary workflow uses workspace automatic saving and does not depend on download.
- Production editor opened with 200 original entries and no draft edits. Test drafts are isolated in test-data and its helper was stopped.
- Game YAML was not changed. Future merge must use data/edits.json and compare against catalog.json, preserving other YAML fields.

## Description / research extension
- 10 server tests passed, including multiline effect/research round-trip, empty research, per-field limits, and legacy condition-only drafts.
- JavaScript syntax passed. Browser verified all three fields populated from the 200-spell baseline, with independent originals, counters and restore controls.
- Dataset identity is unchanged. Game YAML remains untouched.

## General settings extension
- 12 server tests passed, including all editable fields, circle-unset sentinel, numerical boundaries, invalid drafts, legacy compatibility and forbidden resource/ID edits.
- Browser test on isolated test-settings-data: renamed spell, changed element/circle/mana/cooldown/command/permission and all three descriptions, observed decimal validation, restored only mana, confirmed disk save and reload persistence. No console errors.

## Icon preview and checkpoint restore
- 13 server tests passed. All 200 icon HTTP responses match local original PNG bytes; unknown/traversal/private paths denied.
- All copied PNG hashes equal release resource pack originals; dataset ID retained for existing drafts.
- Imported the supplied 202609211818 JSON; exactly 12 entries, all reviewed. Compared every value after browser verification: unchanged. No game YAML modified.
- Browser verified renamed entries (페더 폴, 윈드 푸쉬), imported mana/command/conditions, wind_basket zoom, switch to wind_grooming art, image decoding and dialog close. Unreviewed filter shows 188; left ready to continue there.
