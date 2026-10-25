# Opening-final bounded private packaging record

These Java/Python sources preserve the exact tools used for the 17 private candidates. They do not deploy or restart services. Do not treat intermediate conflict/probe outputs as final; the canonical manifests are docs/handoff/opening-final-20261025/private-stage-manifest.json and FINAL-VALIDATION.json.

This is not a clean source-only reproduction. Restore the SHA-identified operating binary baselines and private raw-build outputs first. input-spec.json records original host input paths; rebase paths in a private copy and recheck SHA gates before executing. Existing tools/live-handoff-20261025 and cached ASM dependencies are prerequisites. No baseline JAR, paid plugin, user DB, operating config body or credential is included here. The Python tools were executed on host Python 3.11; Java helpers were compiled on JDK21 against ASM. Some wrappers contain historical absolute artifact paths and are preserved for exact provenance, not advertised as portable one-command drivers.

Role order: audit_inputs.py inventories live/raw class members; prepare_tools.py and compile_final_special.py prepare bounded ASM helpers; prepare_candidates.py produces conflict-gated generic candidates; prepare_bridge_vfx.py preserves Bridge lifecycle and VFX cache; housing/ and mythic-kyu/ supply independently verified narrow overrides. protocol-check/ and candidate-runtime-tests.init.gradle validate actual candidate protocols/runtime (the latter has the original private candidate path). Class/JAR caches and private payloads are intentionally excluded.

Before a later owner-approved installation, recheck operating SHA against every baselineSHA256/beforeSHA256; preserve new operator edits. None of these tools authorizes deployment.
