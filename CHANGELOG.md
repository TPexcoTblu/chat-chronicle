# Chat Chronicle changelog

## 0.2.0 — multiloader development build

- Add separate Fabric, NeoForge and Quilt artifacts for 26.1, 26.1.1, 26.1.2, 26.2, 26.3 and 1.21.11.
- Share the archive, search, configuration and UI code; adapt the 1.21.11 chat/rendering API and the 26.1 screen API at build time.
- Use loader-independent tick, sender-capture and shutdown hooks. Fabric API, QSL and QFAPI are no longer dependencies.
- Add native client-only NeoForge initialization and loader-specific nested-library packaging. Quilt uses its supported Fabric entrypoint compatibility.
- Produce exact-version metadata, Java 21 bytecode for 1.21.11 and Java 25 bytecode for 26.x. Remap 1.21.11 Fabric/Quilt builds to intermediary; keep NeoForge in its official runtime namespace.
- Add a reproducible build matrix and targeted build options. Short automated in-client smoke checks passed on 26.3 with all three loaders, including archive reopen; other versions have not been played through. See `tools/smoke/REPORT.md` for the exact scope and test-helper corrections.

## 0.1.1

- Fill archive pages with visible messages, skipping hidden records across bounded background queries. Large hidden selections no longer leave sparse pages that require repeated wheel movements to refill.
- Prefetch near the visible edge and after hiding messages; preserve the reading position when hidden rows or cached pages change.
- Keep the current message in place when loading newer cached history. Show the oldest day's divider only after reaching the end of that history.
- Add regression checks for hidden-page traversal in both directions, cursor continuity, cancellation and an entirely hidden archive. Hiding does not delete archive records.

## 0.1.0

First public release for Minecraft Java 26.3 / Fabric, with English and Russian UI.

- Persistent shared daily history, indexed search with date/regex flags, and jumps to message context.
- Consecutive duplicate counters with configurable colors, message actions, multi-selection and configurable chat dimensions/animations.
- Checksummed journals, a rebuildable search index, optional retention and explicit compaction.
- Bounded component/layout/text caches and cooperative search batches that let queued writes and saves proceed.
- GPL-3.0-only, matching source archive, third-party notices and project icon included.

Validation: 31 regular tests plus two isolated acceptance checks (one million messages and forced child-process recovery). Known compatibility and maintenance limitations are documented in the README.

## 0.1.0-beta.1

- Full regex and short-query scans yield after batches of 256 candidates, allowing incoming writes and scheduled saves to proceed. Search restarts after archive maintenance to avoid retaining deleted results across batches.
- Added optional release acceptance checks with one million generated messages and a real forced child-process termination/recovery scenario. They use temporary archives and are excluded from normal builds.
- Added GPL-3.0-only licensing, copyright TPexcoTblu, third-party notices and a matching source ZIP with build scripts, Gradle wrapper and the bundled libraries' Maven source JARs.
- Updated the verification checklist to distinguish completed checks from remaining manual coverage. This is a beta, not a stable release.

## 0.1.0-alpha.10

- Cache decoded components and wrapped messages within the configured message-cache limit. Unchanged messages reuse their layout when new messages arrive.
- Reuse prepared glyphs for unchanged lines and colors. Resource/font changes invalidate the cache; obfuscated text and player glyphs retain vanilla preparation. Scrolling, animation transforms, alpha, clipping and click data remain independent of cached glyphs.
- Keep the journal handle open between flushes; close or replace it on day changes, compaction and shutdown. Preserve forced writes, deletion checkpoints and recovery.
- Add regression coverage for cache eviction and archive writes across flushes, day changes, bulk deletion, compaction and index rebuilding.

## 0.1.0-alpha.9

- Added a persistent duplicate-counter color setting: RGB hex input, nine clickable presets and a live preview in settings. Default remains gray (#AAAAAA).
- The chosen color applies to counters in both the chat HUD and archive without changing original message formatting.
- Consolidated documentation into English and Russian READMEs and kept the development plan local.

## 0.1.0-alpha.8

- Restored clickable server components by routing open-chat clicks through Minecraft's native chat handler, including run-command, suggested-command, dialog, custom, URL and clipboard actions.
- Use vanilla glyph hit detection with the rendered line's scale, position and appearance-animation offset.
- History context supports game click actions; suggested commands open the chat input instead of disappearing into the history screen.

## 0.1.0-alpha.7

- Made the primary GitHub README and Modrinth description English, with separate optional Russian reference documents.

- Render the chat panel below vanilla input and command suggestions, using a separate render stratum.
- Let suggestions handle mouse clicks and scrolling before the chat history underneath.
- Keep the explicitly opened message context menu above both layers, with matching input priority.

## 0.1.0-alpha.6

- Fixed mouse-button handling for Minecraft 26.3: context menus and their shortcuts use RMB, while selection, links and menu actions use LMB.
- Replaced legacy numeric mouse codes with Minecraft's InputConstants.
- Fixed the same legacy key-code mismatch for Ctrl+F, Enter, Delete and Page Up/Down.
- Restricted calendar/day navigation and toolbar activation to LMB. RMB closes an open context menu without executing its action.

## 0.1.0-alpha.5

- Added persisted world/server entry markers within the shared daily history. Reconnects create markers; repeated notifications for the same connection are deduplicated. New markers can be disabled.
- Added complete English/Russian catalogs and an independent language selector. English is the default.
- Localised menus, settings, calendar, hints, confirmations and their buttons, validation messages, archive status, and session-marker display.
- Preserved edited settings fields during language switching.
- Confirmed **Chat Chronicle** as the final project name.
- Added the maintained public `MODRINTH_README.md`, release workflow, and development progress report.

## 0.1.0-alpha.4

- Fixed stacking when equal visible messages have different server-generated click/hover data.
- Added configurable appearance and idle-fade animations.
- Added Shift selection, group copy/hide/delete, and bounded selection snapshots for collapsed groups.
- Preserved selection when cancelling confirmations and protected incoming HUD messages during maintenance refresh.

## 0.1.0-alpha.3

- Added independent HUD width and focused/unfocused height settings, with vanilla fallback.

## 0.1.0-alpha.2

- Added indexed duplicate runs across page boundaries.
- Added optional retention policies, explicit archive compaction, and vanilla HUD geometry/opacity settings.
- Added a standalone build script and archive regression tests.

## 0.1.0-alpha.1

- Initial daily archive, checksummed journals, recoverable search index, paged history, search/context, message actions, and basic settings.
