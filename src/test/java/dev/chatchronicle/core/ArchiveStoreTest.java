package dev.chatchronicle.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveStoreTest {
    @TempDir Path root;
    private ChatRecord record(String day, String text) {
        return new ChatRecord(UUID.randomUUID().toString(), 1793610000000L, day, text,
                "{\"text\":\"" + text + "\",\"color\":\"gold\"}", "Player", UUID.randomUUID().toString(), "PLAYER");
    }
    private ArchiveStore open() { return new ArchiveStore(root, 2, System.err::println); }
    private <T> T await(CompletableFuture<T> future) throws Exception { return future.get(30, TimeUnit.SECONDS); }
    private ArchiveStore.Page find(ArchiveStore store, SearchQuery query) throws Exception { return await(store.search(query, Long.MAX_VALUE, 100, new AtomicBoolean())); }
    private void removeDerivedIndex() throws Exception {
        for (String name : List.of("index.sqlite", "index.sqlite-wal", "index.sqlite-shm")) Files.deleteIfExists(root.resolve(name));
    }
    @Test void persistsUnicodeFormattingAndDailyOrderAcrossRestart() throws Exception {
        ChatRecord first = record("2026-11-01", "Привет мир"), second = record("2026-11-02", "Сегодня");
        try (var store = open()) { await(store.append(first)); await(store.append(second)); }
        try (var store = open()) {
            var page = await(store.page(null, Long.MAX_VALUE, 100));
            assertEquals(List.of(second, first), page.rows().stream().map(ArchiveStore.Row::message).toList());
            assertEquals(2, await(store.days()).size());
            assertEquals(first, await(store.page("2026-11-01", Long.MAX_VALUE, 100)).rows().getFirst().message());
        }
    }
    @Test void literalRegexFlagsDateRangeAndCancellation() throws Exception {
        try (var store = open()) {
            for (var r : List.of(record("2026-11-01", "Привет МИР"), record("2026-11-02", "мировой"), record("2026-11-02", "мир 42"), record("2026-11-02", "a.*b"))) await(store.append(r));
            assertEquals(3, find(store, new SearchQuery("мир", null, null, false, false, false)).rows().size());
            assertEquals(2, find(store, new SearchQuery("мир", null, null, false, false, true)).rows().size());
            assertEquals(2, find(store, new SearchQuery("мир", "2026-11-02", "2026-11-02", false, true, false)).rows().size());
            assertEquals(1, find(store, new SearchQuery("мир \\d+", null, null, true, false, false)).rows().size());
            assertEquals(1, find(store, new SearchQuery("a.*b", null, null, false, false, false)).rows().size());
            assertTrue(await(store.search(new SearchQuery("", null, null, true, false, false), Long.MAX_VALUE, 100, new AtomicBoolean(true))).rows().isEmpty());
            assertThrows(RuntimeException.class, () -> new SearchQuery("[", null, null, true, false, false).compile());
        }
    }
    @Test void regexChunkBoundariesKeepPagingDatesAndDeletionConsistent() throws Exception {
        try (var store = open()) {
            for (int i = 0; i < 1200; i++) await(store.append(record(i < 300 ? "2026-11-01" : "2026-11-02", "chunk item " + i)));
            var query = new SearchQuery("^chunk item [0-9]+$", "2026-11-02", "2026-11-02", true, false, false);
            var first = await(store.search(query, Long.MAX_VALUE, 300, new AtomicBoolean()));
            assertEquals(300, first.rows().size()); assertTrue(first.more());
            var selected = first.rows().get(10);
            var scanning = store.search(new SearchQuery("^no-match$", null, null, true, false, false), Long.MAX_VALUE, 300, new AtomicBoolean());
            await(store.delete(selected)); assertTrue(await(scanning).rows().isEmpty());
            var second = await(store.search(query, first.cursor(), 300, new AtomicBoolean()));
            assertEquals(300, second.rows().size());
            var ids = new HashSet<String>(); first.rows().forEach(row -> assertTrue(ids.add(row.message().id())));
            second.rows().forEach(row -> assertTrue(ids.add(row.message().id())));
            assertTrue(second.rows().stream().allMatch(row -> row.message().day().equals("2026-11-02")));
            var remaining = await(store.search(query, Long.MAX_VALUE, 1000, new AtomicBoolean()));
            assertEquals(899, remaining.rows().size());
            assertFalse(remaining.rows().stream().anyMatch(row -> row.message().id().equals(selected.message().id())));
        }
    }
    @Test void pagingAndContextUseRecordIdentityDespiteIdenticalText() throws Exception {
        try (var store = open()) {
            for (int i = 0; i < 250; i++) await(store.append(record("2026-11-02", "одинаково")));
            var first = await(store.page(null, Long.MAX_VALUE, 100)); assertTrue(first.more());
            var second = await(store.page(null, first.cursor(), 100));
            assertTrue(second.rows().getFirst().sequence() < first.rows().getLast().sequence());
            var chosen = second.rows().get(40);
            var around = await(store.around(chosen.sequence(), 5));
            assertEquals(11, around.size());
            assertEquals(chosen.message().id(), around.get(5).message().id());
            var after = await(store.after(null, chosen.sequence(), 5));
            assertEquals(chosen.sequence() + 1, after.rows().getLast().sequence());
        }
    }
    @Test void corruptMiddleAndTruncatedTailDoNotHideHealthyRecordsAndCanAppendAfterRecovery() throws Exception {
        ChatRecord healthy = record("2026-11-02", "сохранено");
        try (var store = open()) { await(store.append(healthy)); }
        Path journal = root.resolve("2026/11/2026-11-02.journal");
        Files.writeString(journal, "deadbeef\t{broken}\n123\t{truncated", StandardOpenOption.APPEND);
        removeDerivedIndex();
        ChatRecord next = record("2026-11-02", "после аварии");
        try (var store = open()) {
            assertEquals(1, await(store.page(null, Long.MAX_VALUE, 100)).rows().size());
            assertEquals(2, store.damagedRecords);
            await(store.append(next));
        }
        removeDerivedIndex();
        try (var store = open()) {
            assertEquals(List.of(next, healthy), await(store.page(null, Long.MAX_VALUE, 100)).rows().stream().map(ArchiveStore.Row::message).toList());
        }
    }
    @Test void deletionSurvivesIndexRebuildAndDoesNotDeleteEqualMessages() throws Exception {
        try (var store = open()) {
            await(store.append(record("2026-11-02", "одинаково"))); await(store.append(record("2026-11-02", "одинаково")));
            var rows = await(store.page(null, Long.MAX_VALUE, 100)).rows();
            await(store.delete(rows.getLast()));
            assertEquals(1, find(store, new SearchQuery("одинаково", null, null, false, false, false)).rows().size());
        }
        removeDerivedIndex();
        try (var store = open()) { assertEquals(1, await(store.page(null, Long.MAX_VALUE, 100)).rows().size()); }
    }
    @Test void physicallyBrokenIndexIsRebuiltFromJournal() throws Exception {
        ChatRecord record = record("2026-11-02", "Восстановление");
        try (var store = open()) { await(store.append(record)); }
        Files.writeString(root.resolve("index.sqlite"), "broken sqlite header");
        try (var store = open()) {
            assertEquals(record, await(store.page(null, Long.MAX_VALUE, 100)).rows().getFirst().message());
        }
    }
    @Test void interruptedRunIsReportedAndCleanShutdownClearsMarker() throws Exception {
        Files.writeString(root.resolve(".running"), "2026-11-02T23:59:59+02:00");
        try (var store = open()) {
            await(store.days()); assertTrue(store.previousInterrupted);
            assertTrue(Files.readString(root.resolve("recovery-notes.log")).contains("interrupted"));
        }
        assertFalse(Files.exists(root.resolve(".running")));
        try (var store = open()) { await(store.days()); assertFalse(store.previousInterrupted); }
    }
}
