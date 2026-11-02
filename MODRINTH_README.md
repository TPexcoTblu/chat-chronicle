# Chat Chronicle

> **Your chat, kept in order.** Daily history, search with context, and clear markers for every visit.

**Minecraft Java 26.3 · Fabric · Client-side · English / Russian**

Current build: **0.1.0-alpha.7**

---

## Keep the conversation

Chat Chronicle saves your Minecraft chat locally and lets you return to conversations after leaving a server or restarting the game. Worlds and servers share one chronological archive, organised by calendar day.

History has **no message limit or expiration by default**. Only the portion needed for display is loaded into memory.

### Find a message — then read what happened around it

- Search the entire archive, one day, or a date range.
- Toggle **regular expressions**, **case sensitivity**, and **whole-word matching** separately.
- Browse matching messages first, then click a result to open its position in the conversation.
- Scroll through surrounding messages without loading the whole archive.
- Jump to a day using the day list or calendar.

### Clear visit markers

Entering a world or joining a server adds a timestamped separator showing your name and the destination. Reconnecting adds a new marker, while the day's history stays together. Dimension changes do not create extra visit markers.

You can disable new entry markers in the mod settings.

### Less spam, all the originals

Consecutive duplicates collapse into a line with a **×2, ×3, …** counter. Another message starts a new group.

Changing server-generated hover or click data does not prevent otherwise identical messages from stacking. Every original message retains its own formatting and interaction data in the archive. Open message context to see individual repeats.

### Useful actions, one right-click away

Copy plain or formatted text, text without timestamps/counters, JSON, links, timestamps, sender details, and duplicate counts. Fill a reply template without sending it automatically, jump to history, hide a message, or delete it from the archive.

**Shift-select multiple rows** to copy, hide, or delete them together. Deletion asks for confirmation. A selected collapsed group includes the repeats present when it was selected; later arrivals are not silently added to the selection.

### Make the chat fit

- Set the **HUD width**, **open chat height**, and **closed chat height** independently.
- Use `0` for a dimension to follow its vanilla setting.
- Keep Minecraft's text scale, opacity, and line spacing.
- Adjust timestamp formatting and the reply template.
- Configure smooth message appearance, idle delay, and fading.
- Reading older history, opening a menu, or selecting messages keeps the open chat visible.
- Choose **English** or **Russian** independently of Minecraft's language. **English is the default.**

## Controls

| Action | Control |
| --- | --- |
| Open history and search | Open chat, then choose **History / Search**, or press **Ctrl+F** |
| Open message actions | **Right-click** a message |
| Add/remove a row from selection | **Shift+Left-click** |
| Open group actions | Right-click a selected row |
| Copy selected rows | **Ctrl+C** |
| Delete selected rows | **Delete**, then confirm |
| Clear selection | **Esc** |
| Quickly copy original text | **Shift+Right-click**, with no active selection |
| Open message context | **Ctrl+Right-click**, with no active selection |

The two right-click shortcuts can be disabled in settings. Copying a selection uses one entry per selected visible row, in chronological order; the “As displayed” option includes timestamps and duplicate counters.

## Installation

1. Install **Minecraft Java 26.3** with **Java 25** and **Fabric Loader 0.20.1 or newer**.
2. Install **Fabric API** for Minecraft 26.3. The tested API version is **0.165.0+26.3**.
3. Put the Chat Chronicle `.jar` in the profile's `mods` folder.
4. Open chat and choose **Settings** to customise the mod or switch to Russian.

Install only one version of Chat Chronicle at a time. **No server installation is required.**

## Storage and recovery

The archive is stored in the game profile's `chat-chronicle` folder. Settings are in `config/chat-chronicle.json`. Chat Chronicle does **not upload your chat archive**.

Saving is configurable, with a default interval of **2 seconds**. Damaged or incomplete journal records are skipped during recovery, and the search index can be rebuilt from the journals. The most recent unflushed messages can still be lost after a crash.

Optional day/message limits are disabled by default. Enabling or changing them requires confirmation. Removing those limits later does not restore records already excluded. Deleted records remain in journal files until you use **Reclaim space from deleted records…** in settings.

## Alpha status

**This is an early alpha.** Automated archive and regression tests are in place, but visual verification across different GUI scales, live servers, and modpacks is still ongoing.

- Compatibility with other mods that replace the chat UI has not been established.
- Regex uses **RE2**: backreferences and lookaround are unsupported.
- Large regex searches, index rebuilding, and manual compaction currently share the archive worker with writes; heavy operations can delay queued archive work.
- Server-requested removal of signed messages is not yet synchronised with the permanent archive.
- The mod does not yet add a separate persistent history of typed commands.

## What's new in alpha.7

- Command and input suggestions now draw **over the chat panel**.
- Clicking or scrolling suggestions takes priority over the history underneath. An explicitly opened message menu remains on top.

### Fixed in alpha.6

- Fixed the Minecraft 26.3 mouse mapping: **RMB opens message actions**, **Shift+LMB selects**, and **LMB activates menu items**.
- Mouse handling now uses Minecraft's button constants instead of legacy numeric codes.
- Corrected Ctrl+F, Enter, Delete and Page Up/Down to use the current Minecraft key constants as well.

### Added in alpha.5

- Timestamped entry markers for worlds and servers, with an option to disable new markers.
- Complete English and Russian translations for the mod interface, including menus, calendar, confirmations, hints, and archive status. English is selected by default.
- Language switching preserves edited settings fields.
- Chat Chronicle is now the final project name.
