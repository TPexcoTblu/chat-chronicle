# Chat Chronicle 0.2.0 build matrix

Minecraft 26.3 completed a short automated in-client smoke scenario on Fabric, NeoForge and Quilt on 2026-11-03. Other game versions have compilation and packaging checks. See `TESTING.md` for the 0.2.1 checks and `tools/smoke/REPORT.md` for the 0.2.0 results.

Completed: 18 compiled/packaged artifacts, 35 shared-core tests, and 19 Mixin target/signature checks per variant without starting Minecraft. The bytecode checks include INVOKE injection sites, but do not execute the loader or establish runtime/modpack compatibility.

| Minecraft | Java at runtime | Fabric | NeoForge | Quilt |
| --- | --- | --- | --- | --- |
| 26.1 | 25 | 0.20.1 | 26.1.0.19-beta | 0.31.0-beta.5 |
| 26.1.1 | 25 | 0.20.1 | 26.1.1.15-beta | 0.31.0-beta.5 |
| 26.1.2 | 25 | 0.20.1 | 26.1.2.126 | 0.31.0-beta.5 |
| 26.2 | 25 | 0.20.1 | 26.2.0.93 | 0.31.0-beta.5 |
| 26.3 | 25 | 0.20.1 | 26.3.0.101 | 0.31.0-beta.5 |
| 1.21.11 | 21 | 0.20.1 | 21.11.48 | 0.30.2 |

Each cell represents a distinct installable JAR. A mod build's version is 0.2.0 across the matrix; its filename identifies the loader and exact Minecraft version. Beta labels in this table refer to the **loader itself**.

Only the listed stable Minecraft versions are targeted. Snapshots, pre-releases, future 26.x releases and unlisted patch versions are not covered by a wildcard. Add a target and compile it before declaring compatibility.

## Dependencies and packaging

- Fabric builds require Fabric Loader. Fabric API is not required.
- Quilt builds require Quilt Loader and use its Fabric mod/entrypoint compatibility. They contain a Fabric-format descriptor with an explicit `quilt_loader` dependency. They do not require QSL, QFAPI or Fabric API.
- NeoForge builds use a native client-only `@Mod` entrypoint, `neoforge.mods.toml`, access transformers and Jar-in-Jar metadata.
- All variants include SQLite JDBC, RE2/J, the original library licenses, English/Russian catalogs and the project icon.
- Minecraft 1.21.11 Fabric/Quilt artifacts are remapped to intermediary with a Mixin refmap. NeoForge uses official names and does not use that intermediary refmap.
- 26.x uses official unobfuscated names. Shared source adaptations are explicit in `gradle/loader.gradle`; the 1.21.11 message-capture mixin has its own source file.

## Build

JDK 25 is required to build **all** targets, including Java 21-compatible 1.21.11 artifacts.

```powershell
# Full matrix and shared core tests
.\gradlew.bat build

# One game, all three loaders
.\gradlew.bat build '-Pgames=26.3'

# One installable variant
.\gradlew.bat build '-Pgames=1.21.11' '-Ploaders=quilt'

# Start a development client, only when runtime testing is intended
.\gradlew.bat :neoforge-26.3:runClient '-Pgames=26.3' '-Ploaders=neoforge'
```

On Linux/macOS, use `./gradlew`. Installable artifacts are collected under `build/releases/GAME/LOADER/`. Module outputs live under `variants/GAME/LOADER/build/libs/`; do not install `-sources.jar` or development jars. The full buildable source ZIP is `build/distributions/chat-chronicle-0.2.0-source.zip`.

`build/releases` contains the variants selected by the most recent aggregate build. Build without filters to restore the complete matrix. Historical files under `build/libs` belong to earlier single-loader builds and are not current matrix outputs.

## Verification scope

The completed short in-client pass covers **26.3 on Fabric, NeoForge and Quilt only**: launch, open a world, receive/stack messages, search and jump to context, hide and scroll, and reopen the archive. Other targets receive compilation/packaging checks and follow-up fixes based on bug reports. This is a deliberately limited test plan, not a claim that every game/loader combination has been tested.

Please include the exact JAR filename, Minecraft/loader/Java versions, reproduction steps, expected/actual behavior and relevant log excerpt in an issue. Remove private chat and credentials before publishing logs.

Upstream references: [Fabric Loom](https://docs.fabricmc.net/develop/loom/), [ModDevGradle](https://github.com/neoforged/ModDevGradle), [Quilt's API retirement announcement](https://quiltmc.org/en/blog/2026-02-03-non-obfuscated-updates/). Loader and Minecraft availability was checked against their official metadata on 2026-11-03.
