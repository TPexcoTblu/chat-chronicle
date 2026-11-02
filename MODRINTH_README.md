# Chat Chronicle

> **Your chat, kept in order.** Daily history, search with context, and clear markers for every visit.

**Minecraft Java 26.3 · Fabric · Client-side · English / Русский**

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
- Choose **English** or **Русский** independently of Minecraft's language. **English is the default.**

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

---

## Русский

**Chat Chronicle сохраняет историю чата по дням и помогает находить сообщения вместе с контекстом.** Миры и серверы находятся в общей хронологической истории. По умолчанию нет ограничения количества сообщений или срока хранения.

### Возможности

- Поиск по всему архиву, конкретному дню или периоду; отдельные флажки Regex, регистра и целого слова.
- Переход от результата поиска к нужному месту переписки с просмотром соседних сообщений.
- Список дней с количеством записей и календарь.
- Разделитель с временем, именем игрока и названием мира/сервера при каждом входе. Повторные входы видны, но не разбивают день на отдельные файлы сессий.
- Сворачивание последовательных повторов со счётчиком с сохранением оригиналов.
- Меню ПКМ: текст, форматирование, JSON, ссылки, время, отправитель, ответ, переход в историю, скрытие и удаление.
- Мультивыбор через **Shift+ЛКМ**, групповые действия через ПКМ, **Ctrl+C** для копирования и **Delete** для удаления с подтверждением.
- Отдельные настройки ширины HUD и высоты открытого/закрытого чата, времени сообщений, появления и затухания.
- Полный интерфейс на английском и русском. По умолчанию — **English**; язык меняется в начале настроек мода и не зависит от языка Minecraft.
- Локальное хранение, восстановление после повреждённых записей, необязательные лимиты и освобождение места после удаления.

### Установка и начало работы

Нужны **Minecraft Java 26.3**, **Java 25**, **Fabric Loader 0.20.1+** и **Fabric API для 26.3**. Проверенная версия API — **0.165.0+26.3**. Установите один JAR Chat Chronicle в папку `mods` профиля; серверу мод не требуется.

Откройте чат → **Settings** → **Language: English**, чтобы переключить язык на русский. История и поиск доступны из чата или через **Ctrl+F**. В мультивыборе свёрнутая группа включает её повторы на момент выделения, а новые сообщения не добавляются к удалению.

История находится в папке `chat-chronicle`, настройки — в `config/chat-chronicle.json`. Архив никуда не отправляется. Интервал записи по умолчанию — **2 секунды**. После аварии последние незафиксированные сообщения могут быть потеряны. Лимиты по умолчанию выключены, их включение подтверждается отдельно. Физическая очистка удалённых записей запускается отдельным действием в настройках.

**Версия остаётся alpha:** автоматические проверки выполнены, визуальная проверка на серверах и в разных сборках продолжается. Совместимость с другими заменами чата пока не установлена. Regex не поддерживает обратные ссылки и lookaround; тяжёлые операции пока могут задерживать очередь архива.
