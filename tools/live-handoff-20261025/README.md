# Live source handoff: bounded packaging (2026-10-25)

Source-only final v2 tools; no JAR/class/dependency/DB/credential/launcher included.
SOURCE-INVENTORY.json pins original scripts. INPUT-ROLES.json gives input paths/SHA roles;
obsolete reports remain historical evidence, not deployable candidates.

## Reproduction limit / dependencies

Normal source40e2f6944bd15a2c23f0d08b42c8dba4dcdc0e31 build is NOT operating release.
Exact actual live JAR + raw7d8ec6e1 baseline + final raw build are required because prior
operating binaries contain bounded patches beyond this checkout. Do not raw-swap.
Historical scripts retain original host paths; review/parameterize before reuse.
Other-module orchestration was interactive; no complete one-command driver is claimed.
Use JDK21, Python3/PyYAML, ASM9.7.1 asm/asm-tree/asm-util/asm-analysis.
Compile bounded-packaging/*.java together with javac -cp ASM-jars -d scratch/classes.
Runtime CP is those classes plus four ASM jars. Compile OpeningHooks against final raw
Bridge and Paper dependencies. Other helper-source files are compile stubs, NOT product
replacements. OpeningHooks is also copied unchanged into normal paper source.
Fish/range live there; pity is synthesized by OpeningSpecial.

## Invocation order / final v2 fixes

1. Hash/freeze exact live original, raw7d baseline, final raw40e builds.
2. Compile OpeningDelta/Special/Dump/ClassEquivalent, auditors/verifiers, OpeningHooks.
3. stage_bridge.run(area,emit=True) for school/wild/arena selects all changed class bytes,
   including inlined constants. Arena excludes Book/Travel/ReturnTicket and their resources,
   preserving operating lifecycle and Mana callback.
4. probe_ui.run(emit=True) preserves actual UI initializer order through special strip proof.
   This intermediate UI is NOT final v2.
5. ProtocolApiEquivalent proves EVERY class shape/field/method body in pure TitleProtocol,
   BagProtocol, GeneralPresetModeProtocol. Copy canonical school family bytes into UI;
   verify family SHA equality. This normalization produces final UI-v2.
6. other-preservation patches plus OpeningDelta preserve operating module lifecycle/API/
   lambdas/Housing. MetadataOnlyVerify proves Kyu/Velocity metadata unions leave methods/
   fields unchanged. NPC package_npc_candidate.py asserts input hashes and uses
   BoundedReleasePatch + affinity/budget audits. KyudichiPatch is a separate follow-up.
7. MeteorRegistrationPatch follows final Mythic packaging on extracted live/new registry
   classes. RegistrationProbe checks chameteor; strip proof retains old registrations.
8. AuditFinalLinkage checks direct AND invokedynamic Handle descriptors; ASM BasicVerifier,
   zero original class deletion, untouched-entry hashes must pass.
9. FinalJvmVerify -Xverify:all false initialization; isolated FinalProtocolRoundTrip:
   375 titles bidirectional, oldv1 rejection, Bag/OFF3/Custom sealed record.
   FinalArtifactPityHookCheck:401 players/batches, default/missing schema.
   Actual school final passed64 fixtures separately. Source test PASS != final ASM PASS.

OpeningDelta required-method closure follows direct calls AND invokedynamic Handles, adds
only missing helpers, never replaces existing live helper. This fixed missing unchanged
ArtifactOwnershipStore.lambda$poll$0. It unions PermittedSubclasses/NestMembers/InnerClasses.
OpeningSpecial preserves owned/pending+pity batching, UI init, lifecycle/core/bag, range/fish,
arena policy with bounded strip proofs. Cache keys include tool SHA and area; no pre-v2 cache.

## Offline primitive

reproduce_offline.py --spec spec.json --output-dir scratch --java JAVA --asm-classpath CP
--check-only verifies SHA gates with no writes. No deployment/process/download actions.
Spec: live/liveSHA256,newRaw/newRawSHA256,sourceBaselineRaw/sourceBaselineRawSHA256,
prefixes,label,outputName,version,kind(bukkit/fabric/velocity),optional area/resources/
extraClasses/canonicalClasses. Last two map entry names to precompiled class paths.
Bridge extraClasses must include OpeningHooks. Use historical prefix/arena exclusions.
UI canonicalClasses must include all three proven pure protocol families. This driver is
a primitive, not module selection or replacement for ordered validation. PyYAML importable
required; historical bounded_package also tries its old host PyYAML directory.

## Final v2 SHA256

school 4b90ebb14e331f108d01469f4c6b9a97385936e7bb58ac2669a80a603341e93f
wild 40ff5a0e716c5f482d0e76c9921bcf4faa4922df0368cf0674dade6f373edc29
arena 6b2633cbc792e92992306cce212bbe411830be7ec5ace941f6c875a7583e49c7
UI 11754cca8f1d8cdd34aa1e4958f9aa732e3a18b3384eda8af733315fd78ffc96

No production/process/launcher/commit/push action is performed.

## Source preservation / shim clarification

helper-source/MagicCodexBridge.java and ArtifactOwnershipStore.java are isolated compile
stubs for resolving the package-private helper compilation only. They are NOT additional
normal source inputs and MUST NOT be copied over or compiled alongside the real product
classes. The real normal source is the sole business implementation. OpeningHooks.java is
the only helper copied to normal src, byte-identical to the final packaging helper.
SOURCE-INVENTORY hashes refer to original collected scripts, not generated build classes.
Runtime baseline preservation still requires the exact original binary. Original historical
network paths may now point at later deployed versions: never silently accept them when
their pinned SHA fails. Restore a separately retained original baseline into an offline
input directory. The driver input-gate smoke used unchanged raw build files, not a claim
of full final release reproduction; fullOfflineRebuildPerformed is explicitly false.
Output under any network/launcher path component is rejected, as is outputName traversal.
