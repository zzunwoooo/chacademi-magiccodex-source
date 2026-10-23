# Global housing registered nickname hotfix — prepared, not deployed

This supersedes nickname.1, which read only the current server's nickname domain. New nickname.2 reads the actual configured nickname domains of the colocated school/wild/arena network on the Housing DB worker. It resolves stable UUIDs without changing room rows, guestbook bodies/times, nickname rows, ownership or friends. SQLite opens with mode=ro, open_mode=65 and query_only; MariaDB connections request read-only mode. No migration/config change is required.

Actual topology: all three Bridge domains select SQLite friends.db because database.properties is absent and player-state-database.properties does not opt generic domains into MariaDB. Character state and mailbox MariaDB settings do not authorize using that database as nickname authority. The proxy ranking-display-names.properties is a derived display cache, not an offline registration source.

Read-only audit: school has 5 registrations, wild has 2, arena 0. Their union contains 5 UUIDs, with no name conflicts. Production federation code resolved all 5 UUIDs identically from each origin, including players absent from that origin's own store. Each source follows the same GenericDatabaseFile precedence as Bridge. Known colocated paths only; other deployments retain their local source.

Conflict rule: accept one registered name or identical names across sources. Reject conflicting names with an explicit lookup error. Local DB revision values have no global causal ordering, so higher revision, school preference, stale room English name and proxy cache do not choose a winner. Source read failure is explicit and never creates a nickname or falls back to an English account.

Build and 11 tests passed, including bulk SQL, offline UUIDs, metadata preservation, config precedence, source allowlist and conflict behavior. School/wild deployment JARs preserve all original entries except HousingStore.class, Rooms.class and plugin.yml; only HousingNames.class and its Source helper are added. School Story event and wild absence of that event are retained. No Bridge/MCPets/launcher/client/arena replacement is involved. Actual multi-client game validation remains outstanding.

Artifact paths, baseline/output hashes and verification results are in ready.json in this directory. Clean-stop/replace/restart remains unperformed pending operational approval. Do not deploy the full Gradle-built JAR; use only the corresponding baseline-preserving package.
