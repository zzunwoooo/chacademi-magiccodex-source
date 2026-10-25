# VFX operating hotfix reference

Reference Java only; excluded from normal Gradle source sets. Do not copy these files wholesale over current sources.

Source worktree: `C:/Chacademi/staging/vfx-warmup-reuse-20261023`
Source HEAD: `d2f2f61dfd59468bafa72b354123bd5915ca5418`

| Original relative source | SHA256 |
|---|---|
| `render/ResourceFingerprint.java` | `48bed7a1a7e6ee87111515dd5a9c850b4c86fd88fd5b2017aa1456c193b4d72d` |
| `definition/EffectLibrary.java` | `68636727daccc189cf3f4efbceabfffa326baf1787483747c145c042beddc88f` |
| `render/EffectRenderer.java` | `654e41c99ba2fe71a7356091ed932602a2dede4362af94947e07b17d4f262284` |

`ResourceFingerprint.of` depends on `EffectLibrary.Snapshot.valid()`, which is absent from the current normal source. Its original renderer caller also depends on the complete operating warmup/reload reuse lifecycle (resident fingerprint, expanded preload batch, fresh preparation, reuse apply, cleanup tracking, GPU readiness/disposal). These dependencies are NOT integrated here.

Normal sources restore only the original DropRarity and DropRarityVfx classes plus their direct PortableVfxClient tick hook. No new Claude development features, runtime binaries, or credentials are included.

Validation: normal `:client:compileJava --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1g` PASS using JDK 21.0.10 and existing hosting Gradle cache. No full tests or operating JAR byte reproduction claimed. Log: `C:\Users\chacademi-codex\AppData\Local\Temp\live-source-handoff-vfx-compileJava-final.log`.
