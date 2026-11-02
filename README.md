# Chat Chronicle

> Daily chat history, search with context, and clear markers for every visit.

**Minecraft Java 26.3 · Fabric · Client-side · English and Russian**

Current version: **0.1.0-beta.1**

[Installation](#installation) · [Controls](#controls) · [Build from source](#build-from-source) · [Changelog](CHANGELOG.md) · [Russian documentation](README.ru.md)

## Features

- **Persistent daily history.** Worlds and servers share one chronological archive. Each day starts at local midnight; reconnecting adds a visit separator within the same day.
- **No retention limit by default.** History is loaded in batches with a bounded memory cache. Optional age and message-count limits are available.
- **Search with context.** Search all history, a day, or a date range. Enable regex, case sensitivity, and whole-word matching separately. Click a result to open its position in the conversation.
- **Day navigation.** Browse a list of dates with message counts or jump through the calendar.
- **Duplicate stacking.** Consecutive repeats display a counter, even when server-generated hover or click data changes. Every original record retains its own formatting and interaction data.
- **Message actions.** Right-click to copy text, formatting, JSON, links, timestamps, sender details, and duplicate counts; prepare a reply, open context, hide a message, or delete it.
- **Multiple selection.** Shift-click rows to copy, hide, or delete them together. New arrivals never silently expand a selection.
- **Customisable HUD.** Set width and focused/unfocused heights independently, or follow vanilla settings. Configure timestamps, reply templates, appearance animations, and idle fading.
- **English and Russian localisation.** English is the default, independently of Minecraft's language. Change the language at the top of the mod's settings.
- **Local storage and recovery.** Checksummed journals, a rebuildable search index, interrupted-run detection, and explicit archive compaction.

Command suggestions draw above the chat panel and receive clicks and scrolling before the history underneath. An explicitly opened message context menu stays on top.

Clickable message components use Minecraft's native chat actions, including server command buttons, suggested commands, dialogs, custom actions, links and clipboard copying. Hit detection follows the actual rendered glyphs and scale. Clicking a suggested command in history context opens the chat input.

## Installation

1. Use **Minecraft Java 26.3**, **Java 25**, and **Fabric Loader 0.20.1 or newer**.
2. Install **Fabric API for Minecraft 26.3**. The tested version is **0.165.0+26.3**.
3. Place `chat-chronicle-0.1.0-beta.1.jar` in the game profile's `mods` folder.
4. Open chat and choose **History / Search** or **Settings**.

Keep only one version of Chat Chronicle installed. The mod is client-side; no server installation is required. SQLite and RE2 are bundled in the mod JAR.

## Controls

| Action | Control |
| --- | --- |
| Open history and search | **Ctrl+F** while chat is open, or **History / Search** |
| Open message actions | **Right-click** a message |
| Select or deselect a row | **Shift+Left-click** |
| Open group actions | Right-click a selected row |
| Copy selected rows | **Ctrl+C** |
| Delete selected rows | **Delete**, then confirm |
| Clear selection | **Esc** |
| Quickly copy original text | **Shift+Right-click**, with no active selection |
| Open message context | **Ctrl+Right-click**, with no active selection |
| Scroll history by a page | **Page Up / Page Down** |
| Run a search | **Enter** in the history screen |

The two right-click shortcuts can be disabled in settings.

A selected collapsed group includes the original repeats present when it was selected. Later arrivals are excluded from deletion. Copying uses one entry per selected visible row, ordered from oldest to newest; **As displayed** includes timestamps and counters. Open context to select individual repeats.

## Settings and storage

Decoded components, wrapped messages and prepared text are cached with bounded capacity. Font/resource changes invalidate text caches, and dynamic glyphs keep vanilla rendering. The active journal stays open between flushes while retaining periodic forced writes and recovery checks.

Choose the duplicate-counter color in settings: enter `#RRGGBB` or click the colored preview to cycle nine presets. The default is gray (`#AAAAAA`). Save to apply it to the HUD and history; original message colors stay intact.

HUD dimensions accept **20–8192 GUI pixels**; `0` follows the corresponding vanilla setting. Dimensions fit the available window area. Minecraft's text scale, opacity, and line spacing remain in use.

Default animation timings are **180 ms** for appearance, **8 seconds** before fading, and **1200 ms** for fading. Reading older history, opening a menu, or selecting messages keeps the open chat visible. The separate archive screen does not fade.

Files are stored in the current game profile:

```text
chat-chronicle/
  2026/11/2026-11-02.journal
  index.sqlite
  recovery-notes.log
config/chat-chronicle.json
```

Chat Chronicle does not upload the archive. Visit markers are persisted as separate entries, split duplicate runs, and remain available after restarting. Their labels display in the selected language and are searchable in English and Russian.

Journals use append-only `CRC32<TAB>JSON<NEWLINE>` records. Original component JSON is stored separately from HUD timestamps and counters. Damaged or incomplete records are skipped during recovery; a damaged SQLite/FTS index is renamed and rebuilt from the journals.

The default save interval is **2 seconds**. Writes are forced to disk on the configured interval, before history queries, and during normal shutdown. A crash may still lose the latest unflushed records. An unclean-shutdown marker indicates that shutdown did not finish; it does not prove that messages were lost.

Retention limits default to `0`, meaning unlimited. A day limit of `1` keeps today only. Message limits count original messages rather than collapsed groups. If both limits are enabled, both apply. Increasing or disabling a limit does not restore excluded records.

Deletion removes records from history and search and appends a tombstone to the journal. Use **Reclaim space from deleted records…** to rewrite journals without deleted records and compact the index. **Hide from view** lasts until restart.

## Build from source

Install **JDK 25**. Both the Gradle daemon and Java toolchain request Java 25; if automatic discovery fails, set `JAVA_HOME` to its installation directory.

```sh
git clone https://github.com/TPexcoTblu/chat-chronicle.git
cd chat-chronicle
```

On Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat runClient
```

You can also run `build-mod.bat`, including from another working directory.

On Linux or macOS:

```sh
./gradlew build
./gradlew test
./gradlew runClient
```

The installable JAR is written to `build/libs/chat-chronicle-0.1.0-beta.1.jar`. The `-sources.jar` file contains source code and is not the mod to install.

`gradlew releaseCheck` runs the optional large-archive and forced-process-recovery checks. They are excluded from normal builds and use temporary data. On Windows, use `gradlew.bat releaseCheck`; on Linux/macOS, use `./gradlew releaseCheck`.

Pinned versions: Minecraft **26.3**, Loader **0.20.1**, Fabric API **0.165.0+26.3**, Loom **1.19.3**, Gradle **9.8.2**, SQLite JDBC **3.51.3.0**, and RE2/J **1.8**.

## Beta status and known limitations

This is a **beta build**. Automated archive and regression checks pass, but visual verification across GUI scales, servers, and modpacks remains ongoing.

- Compatibility with other mods that replace the chat UI has not been established.
- Regex and short-query scans yield between batches so queued writes and scheduled saves can proceed. Initial index rebuilding and manual compaction still occupy the archive worker; avoid running compaction while actively chatting.
- Regex uses RE2; backreferences and lookaround are unsupported. Short queries and regex queries can scan a large part of the archive.
- Sender names and UUIDs are available only when provided by the server.
- A normal right-click on a collapsed group targets its latest original message. Use context to select another repeat, or multi-select the group to act on its selection snapshot.
- Arbitrary shortcut remapping and a separate persistent history of typed commands are not implemented yet.
- Server-requested deletion of signed messages is not synchronised with the permanent archive.
- Changing the time zone does not regroup previously stored days.

## License

Copyright © 2026 TPexcoTblu. Chat Chronicle is licensed under [GNU GPL version 3 only](LICENSE) (`GPL-3.0-only`). Copyright remains with the author. You may use and modify the code; when distributing a covered derivative, provide its corresponding source under GPL v3 and retain the required notices. Private changes that are not distributed do not have to be published.

Publish the matching `build/distributions/chat-chronicle-VERSION-source.zip` alongside the mod JAR, or provide equivalent access to the corresponding source. This archive includes the source, build scripts and Gradle wrapper. Bundled libraries keep their own licenses.

## Project documentation

- [Changelog](CHANGELOG.md)
- [Release workflow](RELEASING.md)
- [Russian user and local build guide](README.ru.md)
- [Progress report (Russian)](PROGRESS.md)
- [Verification checklist (Russian)](TESTING.md)

Use this English README for GitHub and the Modrinth description. The Russian version is maintained in README.ru.md. Release notes are in CHANGELOG.md; build instructions are included above.
