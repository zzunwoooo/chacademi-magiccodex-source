# Client startup repair (2026-10-23)

The book.1 overlay rebuilt MagicCodexClient from a source tree containing the CastAim initialization introduced by 8f28d6f5, but did not include CastAimClient.class in its selective overlay. The packaged initializer therefore references a missing class before the game can open.

The deployed pre-book baseline has no CastAim initialization. Remove this one accidental INVOKESTATIC call; retain all 55 baseline initializations and the new BookClient initialization. Do not bundle the unrelated aiming feature.

Reproduce on the host with its cached original JARs and Java 21:
- Run Python tools/incident-startupfix/build_candidate.py
- Run Python tools/incident-startupfix/verify_candidate.py

build_candidate.py asserts the original fc09 SHA, compiles the ASM visitor using the installed JDK, removes exactly one call, and changes only the version metadata plus MagicCodexClient.class. Original archives are never overwritten.
verify_candidate.py checks the completed JAR: all own CONSTANT_Class references resolve, baseline initialization ordering is preserved, Book initialization remains once, and every initializer method exists in its packaged target class.

Candidate artifacts and detailed javap evidence are written under C:/Chacademi/artifacts/book-client-startupfix-20261023.
This is a static artifact linkage check. It does not launch Minecraft or alter profiles, servers, launcher manifests, or public releases.
