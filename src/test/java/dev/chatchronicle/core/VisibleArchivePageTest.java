package dev.chatchronicle.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class VisibleArchivePageTest {
    @TempDir Path root;

    private List<ChatRecord> seed(ArchiveStore store, int count) throws Exception {
        List<ChatRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ChatRecord record = new ChatRecord(UUID.randomUUID().toString(), 1000L + i, "2026-11-02",
                    "Message " + i, "{\"text\":\"Message " + i + "\"}", null, null, "SYSTEM");
            records.add(record); store.append(record);
        }
        store.days().get(30, TimeUnit.SECONDS);
        return records;
    }

    @Test void olderPagesSkipHundredsOfHiddenRecordsAndKeepCursorOrder() throws Exception {
        try (var store = new ArchiveStore(root, 2, fail -> { throw new AssertionError(fail); })) {
            var records = seed(store, 1200);
            Set<String> hidden = new HashSet<>();
            for (int i = 150; i < records.size(); i++) if (i % 9 != 0) hidden.add(records.get(i).id());
            var page = VisibleArchivePage.load(Long.MAX_VALUE, 100, false, hidden, new AtomicBoolean(),
                    (cursor, limit) -> store.groupedPage(null, cursor, limit)).get(30, TimeUnit.SECONDS);
            assertEquals(100, page.rows().size()); assertTrue(page.more());
            List<ChatRecord> expected = new ArrayList<>(records.stream().filter(r -> !hidden.contains(r.id())).toList());
            Collections.reverse(expected);
            assertEquals(expected.subList(0, 100), page.rows().stream().map(ArchiveStore.Row::message).toList());
            var next = VisibleArchivePage.load(page.cursor(), 100, false, hidden, new AtomicBoolean(),
                    (cursor, limit) -> store.groupedPage(null, cursor, limit)).get(30, TimeUnit.SECONDS);
            assertEquals(expected.subList(100, 200), next.rows().stream().map(ArchiveStore.Row::message).toList());
            assertTrue(next.rows().getFirst().sequence() < page.rows().getLast().firstSequence());
        }
    }

    @Test void newerPagesSkipHiddenRecordsWithoutReversingBatches() throws Exception {
        try (var store = new ArchiveStore(root, 2, fail -> { throw new AssertionError(fail); })) {
            var records = seed(store, 350);
            Set<String> hidden = new HashSet<>();
            for (int i = 0; i < records.size(); i++) if (i % 7 != 0) hidden.add(records.get(i).id());
            var page = VisibleArchivePage.load(0, 30, true, hidden, new AtomicBoolean(),
                    (cursor, limit) -> store.after(null, cursor, limit)).get(30, TimeUnit.SECONDS);
            List<ChatRecord> expected = new ArrayList<>(records.stream().filter(r -> !hidden.contains(r.id())).limit(30).toList());
            Collections.reverse(expected);
            assertEquals(expected, page.rows().stream().map(ArchiveStore.Row::message).toList());
            assertTrue(page.more());
            var next = VisibleArchivePage.load(page.cursor(), 30, true, hidden, new AtomicBoolean(),
                    (cursor, limit) -> store.after(null, cursor, limit)).get(30, TimeUnit.SECONDS);
            assertEquals(20, next.rows().size()); assertFalse(next.more());
            assertTrue(next.rows().getLast().sequence() > page.rows().getFirst().sequence());
        }
    }

    @Test void entirelyHiddenArchiveExhaustsWithoutEmptyPageLoop() throws Exception {
        try (var store = new ArchiveStore(root, 2, fail -> { throw new AssertionError(fail); })) {
            var records = seed(store, 550);
            Set<String> hidden = new HashSet<>(); records.forEach(r -> hidden.add(r.id()));
            var page = VisibleArchivePage.load(Long.MAX_VALUE, 100, false, hidden, new AtomicBoolean(),
                    (cursor, limit) -> store.page(null, cursor, limit)).get(30, TimeUnit.SECONDS);
            assertTrue(page.rows().isEmpty()); assertFalse(page.more());
            assertEquals(1, page.cursor());
            assertEquals(550, store.days().get(30, TimeUnit.SECONDS).getFirst().count());
        }
    }

    @Test void cancellationStopsContinuationAndBrokenCursorFailsInsteadOfLooping() {
        AtomicBoolean cancelled = new AtomicBoolean(); AtomicInteger calls = new AtomicInteger();
        var task = VisibleArchivePage.load(100, 10, false, Set.of(), cancelled, (cursor, limit) -> {
            calls.incrementAndGet(); cancelled.set(true);
            return CompletableFuture.completedFuture(new ArchiveStore.Page(List.of(), true, 50));
        });
        assertThrows(CompletionException.class, task::join); assertEquals(1, calls.get());
        var broken = VisibleArchivePage.load(100, 10, false, Set.of(), new AtomicBoolean(),
                (cursor, limit) -> CompletableFuture.completedFuture(new ArchiveStore.Page(List.of(), true, cursor)));
        assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class, broken::join).getCause());
    }
}
