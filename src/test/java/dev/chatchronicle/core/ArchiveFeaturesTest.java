package dev.chatchronicle.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveFeaturesTest {
    @TempDir Path root;
    private ChatRecord record(String day, String text) {
        return new ChatRecord(UUID.randomUUID().toString(), System.currentTimeMillis(), day, text,
                "{\"text\":\"" + text + "\"}", "Alex", "00000000-0000-0000-0000-000000000001", "PLAYER");
    }
    private <T> T get(CompletableFuture<T> task) throws Exception { return task.get(60, TimeUnit.SECONDS); }
    private ArchiveStore open() { return new ArchiveStore(root, 2, System.err::println); }
    private void removeIndex() throws Exception {
        for (String name : List.of("index.sqlite", "index.sqlite-shm", "index.sqlite-wal")) Files.deleteIfExists(root.resolve(name));
    }
    @Test void repeatedFlushDayChangesBulkDeletionAndCompactionAllowFurtherWritesAndRecovery() throws Exception {
        var expected = new ArrayList<ChatRecord>();
        try (var store = open()) {
            for (String day : List.of("2026-11-01", "2026-11-02", "2026-11-01", "2026-11-03")) {
                for (int i = 0; i < 3; i++) {
                    ChatRecord message = record(day, "entry " + expected.size()); expected.add(message);
                    get(store.append(message));
                    assertEquals(expected.size(), get(store.page(null, Long.MAX_VALUE, 100)).rows().size());
                }
            }
            var rows = get(store.page(null, Long.MAX_VALUE, 100)).rows();
            var deleted = List.of(rows.get(0), rows.get(4), rows.get(8));
            assertEquals(3, get(store.deleteSelected(deleted)));
            expected.removeIf(message -> deleted.stream().anyMatch(row -> row.message().equals(message)));
            assertEquals(expected.size(), get(store.compactArchive()));
            for (String day : List.of("2026-11-03", "2026-11-01")) {
                var next = record(day, "after compact " + day); expected.add(next);
                get(store.append(next)); get(store.days());
            }
        }
        removeIndex();
        try (var store = open()) {
            var recovered = get(store.page(null, Long.MAX_VALUE, 100)).rows().stream().map(ArchiveStore.Row::message).toList();
            assertEquals(expected.size(), recovered.size());
            assertEquals(new HashSet<>(expected), new HashSet<>(recovered));
        }
    }
    @Test void thousandDuplicatesAreOneIndexedRowAndAnInterveningMessageStartsAnotherRun() throws Exception {
        String day = "2026-11-02";
        try (var store = open()) {
            for (int i = 0; i < 1000; i++) store.append(record(day, "A"));
            get(store.append(record(day, "B")));
            for (int i = 0; i < 300; i++) store.append(record(day, "A"));
            var page = get(store.groupedPage(null, Long.MAX_VALUE, 2));
            assertTrue(page.more()); assertEquals(2, page.rows().size());
            assertEquals(300, page.rows().getFirst().repeatCount());
            assertEquals("B", page.rows().getLast().message().text());
            var older = get(store.groupedPage(null, page.cursor(), 2));
            assertEquals(1, older.rows().size()); assertEquals(1000, older.rows().getFirst().repeatCount());
            var newer = get(store.groupedAfter(null, older.rows().getFirst().lastSequence(), 2));
            assertEquals(List.of("A", "B"), newer.rows().stream().map(r -> r.message().text()).toList());
            assertEquals(1301, get(store.days()).getFirst().count());
        }
        try (var store = open()) { assertEquals(300, get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().getFirst().repeatCount()); }
        removeIndex();
        try (var store = open()) { assertEquals(List.of(300L, 1L, 1000L), get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().stream().map(ArchiveStore.Row::repeatCount).toList()); }
    }
    @Test void deletingGroupRepresentativeUpdatesBoundariesAndDoesNotDeleteOtherDuplicates() throws Exception {
        try (var store = open()) {
            for (int i = 0; i < 12; i++) store.append(record("2026-11-02", "A"));
            var first = get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().getFirst();
            get(store.delete(first));
            var remaining = get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().getFirst();
            assertEquals(11, remaining.repeatCount()); assertTrue(remaining.sequence() < first.sequence());
            assertEquals(11, get(store.page(null, Long.MAX_VALUE, 50)).rows().size());
        }
        removeIndex();
        try (var store = open()) { assertEquals(11, get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().getFirst().repeatCount()); }
    }
    @Test void dayBoundarySeparatesOtherwiseEqualMessagesAndSchemaUpgradePreservesOriginals() throws Exception {
        try (var store = open()) {
            get(store.append(record("2026-11-01", "A"))); get(store.append(record("2026-11-02", "A")));
            assertEquals(2, get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().size());
        }
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("index.sqlite")); var st = db.createStatement()) { st.execute("PRAGMA user_version=0"); }
        try (var store = open()) { assertEquals(2, get(store.page(null, Long.MAX_VALUE, 10)).rows().size()); }
    }
    @Test void limitsAreDisabledByDefaultAndAgeAndCountWorkTogether() throws Exception {
        String today = LocalDate.now().toString(), yesterday = LocalDate.now().minusDays(1).toString();
        try (var store = open()) {
            for (int i = 0; i < 10; i++) store.append(record(yesterday, "Old"));
            for (int i = 0; i < 8; i++) store.append(record(today, "New"));
            assertEquals(18, get(store.configureRetention(RetentionPolicy.UNLIMITED)).remaining());
            var result = get(store.configureRetention(new RetentionPolicy(1, 5)));
            assertEquals(13, result.removed()); assertEquals(5, result.remaining());
            assertEquals(5, get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().getFirst().repeatCount());
            assertEquals(1, get(store.days()).size());
            assertEquals(5, get(store.configureRetention(RetentionPolicy.UNLIMITED)).remaining());
        }
        removeIndex();
        try (var store = open()) { assertEquals(5, get(store.page(null, Long.MAX_VALUE, 100)).rows().size()); }
    }
    @Test void compactionReclaimsJournalBytesAndDeletedContentCannotReturnOnRebuild() throws Exception {
        String day = "2026-11-02"; ChatRecord keep = record(day, "KEEP");
        long before;
        Path journal = root.resolve("2026/11/2026-11-02.journal");
        try (var store = open()) {
            for (int i = 0; i < 30; i++) store.append(record(day, "DELETE"));
            get(store.append(keep)); get(store.configureRetention(new RetentionPolicy(0, 1)));
            before = Files.size(journal);
            assertEquals(1, get(store.compactArchive()));
            assertTrue(Files.size(journal) < before);
            assertFalse(Files.readString(journal).contains("DELETE"));
            assertEquals(keep, get(store.page(null, Long.MAX_VALUE, 10)).rows().getFirst().message());
        }
        removeIndex();
        try (var store = open()) {
            assertEquals(List.of(keep), get(store.page(null, Long.MAX_VALUE, 10)).rows().stream().map(ArchiveStore.Row::message).toList());
            get(store.append(record(day, "NEXT")));
            assertEquals(2, get(store.page(null, Long.MAX_VALUE, 10)).rows().size());
        }
    }
    @Test void malformedCrashMarkerDoesNotPreventReadingHealthyHistory() throws Exception {
        try (var store = open()) { get(store.append(record("2026-11-02", "A"))); }
        Files.write(root.resolve(".running"), new byte[]{(byte) 0xFF, (byte) 0xFE});
        try (var store = open()) {
            assertEquals(1, get(store.page(null, Long.MAX_VALUE, 10)).rows().size());
            assertTrue(store.previousInterrupted);
        }
    }
}
