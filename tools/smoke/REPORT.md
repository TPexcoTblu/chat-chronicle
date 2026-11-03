# Minecraft 26.3 short runtime smoke — 2026-11-03

Tested release artifacts: Chat Chronicle 0.2.0, JDK 25.0.2, Windows, Prism Launcher 11.0.3. Each loader used its own disposable profile, a copy of the local creative test world and only Chat Chronicle plus the optional test companion. No Fabric API, QSL or QFAPI was installed.

| Loader | Version | In-world checks | Archive reopen |
| --- | --- | --- | --- |
| Fabric | 0.20.1 | Passed | Passed |
| NeoForge | 26.3.0.101 | Passed | Passed |
| Quilt | 0.31.0-beta.5 | Passed | Passed |

The companion executed Minecraft/mod APIs in a real running client: embedded SQLite startup, world join, entry marker, a local-server chat message, 620 unique client messages, three consecutive duplicates grouped as ×3, paging to at least 400 rows, rendering hit targets, the right-click menu path, hiding 301 selected rows, preserving the reading anchor, scrolling both ways, searching a visible row and opening its context. The archive retained all 624 fixture records. Reopen verified the same count, three original duplicates, reset temporary hidden state and no interrupted-session flag.

## Test-helper corrections and limits

- The initial helper failed because its ordinary test class shared the reserved Mixin package; separated that package.
- Its initial search assertion queried duplicates that the test had just hidden. Corrected it to query the unhidden anchor row; archive-level checks still verify the three originals.
- The first in-world runs called `exitWorldAndClose()` from a tick callback. This closed graphics resources before the current frame finished, producing `Invalid atlas id: minecraft:gui` during shutdown (observed on NeoForge and Quilt). Replaced that helper-only call with `stop()`. Subsequent archive-reopen runs exited without that error. The entire in-world scenario was not repeated after this shutdown-only helper change.
- No release-mod source or release JAR changed during this test pass.
- This is automated in-client integration testing, not a complete manual visual assessment. Native window captures raced with the short scenarios and do not establish visual correctness. No performance benchmark, multiplayer-server visit, modpack compatibility, or other Minecraft-version runtime coverage is claimed.

Local evidence is in `.tools/smoke/results/`, including JSON results, latest reopen logs, first-pass crash reports where present, and hashes of the tested release JARs. Personal-profile chat was not used or modified.

## Reproduction

Build the optional companion with JDK 25:

```powershell
.\gradlew.bat :neoforge-26.3:smokeHarnessJar '-Pgames=26.3' '-Ploaders=neoforge'
python tools/smoke/prepare_profiles.py
```

The preparation script refuses to overwrite profiles. It expects Prism's existing `26.3/minecraft/saves/Chat Chronicle Test` world and creates `CC-Smoke-26.3-fabric`, `CC-Smoke-26.3-neoforge`, and `CC-Smoke-26.3-quilt`. Launch each profile twice, sequentially. The authorization marker confines test execution to these profiles. The companion JAR is not a release artifact and must not be distributed as the mod.
