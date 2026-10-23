# Housing nickname display hotfix — prepared, not deployed

Branch: `fix/housing-nickname-20261023-task2`; operating housing source base: `7e6ac037`.

Only `HousingStore.java`, `Rooms.java`, new `HousingNames.java`, housing version, and targeted tests change. Display names are read asynchronously by stable UUID from the exact configured Bridge nickname domain. Historical room/guestbook names and UUID/body/time/ownership are retained. No nickname migration or identity writes were added. SQL failure returns an explicit error; missing registration displays `닉네임 미등록` and never silently falls back to the old English account.

School package preserves its Story event, wild package preserves the existing absence. Every existing JAR entry is byte-identical except HousingStore.class, Rooms.class and plugin.yml; HousingNames.class is the only new entry. Full rebuilt JAR is not a deployment target.

Tests: {'tests': 9, 'failures': 0, 'errors': 0, 'skipped': 0}. Real production lookup was read-only: school 5/5 Korean nicknames, wild 2/2 Korean nicknames. Current Bridge domains are separate SQLite stores; names absent from a server's store remain explicitly unregistered. Actual multi-client UI verification remains outstanding.

Prepared artifacts and hashes are in `ready.json`. Replace only the corresponding installed ChacaHousing JAR after approved clean stop, checking baseline SHA256 first; avoid duplicate plugin JARs. No Bridge, MCPets, launcher, arena, client or data/config changes are required by this hotfix. Nothing has been deployed or restarted. Whole-user search and chat verification remain pending after urgent priority change.
