# Chat Chronicle changelog

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
