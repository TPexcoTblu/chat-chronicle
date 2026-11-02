package dev.chatchronicle.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Run explicitly with releaseCheck; never writes to a player's archive. */
@Tag("release")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class ReleaseAcceptanceTest {
    @TempDir Path root;
    private static <T> T get(CompletableFuture<T> task) throws Exception { return task.get(180, TimeUnit.SECONDS); }
    static ChatRecord message(int i) {
        String text = "Archive entry " + i + " release-marker-" + i;
        return new ChatRecord(new UUID(0, i + 1).toString(), 1793610000000L + i,
                LocalDate.of(2026, 11, 1).plusDays(i / 333334).toString(), text,
                "{\"text\":\"" + text + "\",\"color\":\"gold\"}", "Test", null, "SYSTEM_SERVER");
    }
    @Test void millionMessagesPagingDatesSearchCancellationAndQueuedWrites() throws Exception {
        List<String> errors = new CopyOnWriteArrayList<>();
        long start = System.nanoTime();
        try (var store = new ArchiveStore(root, 2, errors::add)) {
            CompletableFuture<Void> last = null;
            for (int i = 0; i < 1_000_000; i++) {
                last = store.append(message(i));
                if ((i + 1) % 2000 == 0) get(last);
                if ((i + 1) % 100_000 == 0) System.out.printf("RELEASE ingest=%d elapsed_ms=%.0f%n", i + 1, (System.nanoTime()-start)/1e6);
            }
            var days = get(store.days());
            assertEquals(1_000_000, days.stream().mapToLong(ArchiveStore.Day::count).sum());
            long indexed = System.nanoTime();
            Set<String> ids = new HashSet<>(); long before = Long.MAX_VALUE;
            for (int page = 0; page < 8; page++) {
                var result = get(store.page(null, before, 100));
                assertEquals(100, result.rows().size());
                for (var row : result.rows()) assertTrue(ids.add(row.message().id()));
                before = result.cursor();
            }
            var exact = get(store.search(new SearchQuery("release-marker-712345", "2026-11-03", "2026-11-03", false, false, false), Long.MAX_VALUE, 100, new AtomicBoolean()));
            assertEquals(List.of(message(712345)), exact.rows().stream().map(ArchiveStore.Row::message).toList());
            assertEquals(41, get(store.around(exact.rows().getFirst().sequence(), 20)).size());
            long searched = System.nanoTime();
            long scanStart = System.nanoTime();
            var scan = store.search(new SearchQuery("^missing-release-[0-9]+$", null, null, true, false, false), Long.MAX_VALUE, 100, new AtomicBoolean());
            var queued = store.append(message(1_000_000));
            boolean scanFinishedBeforeWrite = get(queued.thenApply(v -> scan.isDone()));
            assertFalse(scanFinishedBeforeWrite, "A full scan must yield to the queued write before it finishes");
            long written = System.nanoTime();
            assertTrue(get(scan).rows().isEmpty());
            var cancelled = new AtomicBoolean(true);
            assertTrue(get(store.search(new SearchQuery(".", null, null, true, false, false), Long.MAX_VALUE, 100, cancelled)).rows().isEmpty());
            assertEquals(1_000_001, get(store.days()).stream().mapToLong(ArchiveStore.Day::count).sum());
            System.out.printf(Locale.ROOT, "RELEASE million index_ms=%.1f paging+search+context_ms=%.1f queued_write_ms=%.1f full_scan_ms=%.1f%n", (indexed-start)/1e6, (searched-indexed)/1e6, (written-scanStart)/1e6, (System.nanoTime()-scanStart)/1e6);
        }
        try (var store = new ArchiveStore(root, 2, errors::add)) {
            assertEquals(1_000_001, get(store.days()).stream().mapToLong(ArchiveStore.Day::count).sum());
        }
        assertTrue(errors.isEmpty(), errors.toString());
    }
    @Test void forcedProcessTerminationPreservesFlushedRecordsAndAcceptsMoreWrites() throws Exception {
        Path archive = root.resolve("crash-archive"), ready = root.resolve("ready"), output = root.resolve("child.log");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        var process = new ProcessBuilder(java, "-cp", System.getProperty("chronicle.test.classpath"),
                CrashWriter.class.getName(), archive.toString(), ready.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
            assertTrue(Files.exists(ready), () -> "Child did not become ready: " + output);
            process.destroyForcibly(); assertTrue(process.waitFor(15, TimeUnit.SECONDS));
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(15, TimeUnit.SECONDS); } }
        List<String> errors = new CopyOnWriteArrayList<>();
        try (var store = new ArchiveStore(archive, 2, errors::add)) {
            var recovered = get(store.page(null, Long.MAX_VALUE, 500)).rows();
            assertTrue(store.previousInterrupted);
            var ids = new HashSet<String>(); recovered.forEach(row -> assertTrue(ids.add(row.message().id())));
            for (int i = 0; i < 100; i++) assertTrue(ids.contains(message(i).id()));
            get(store.append(message(1000)));
            assertEquals(message(1000), get(store.page(null, Long.MAX_VALUE, 1)).rows().getFirst().message());
        }
        try (var store = new ArchiveStore(archive, 2, errors::add)) {
            assertEquals(message(1000), get(store.page(null, Long.MAX_VALUE, 1)).rows().getFirst().message());
            assertFalse(store.previousInterrupted);
        }
        assertTrue(errors.isEmpty(), errors.toString());
    }
    public static final class CrashWriter {
        public static void main(String[] args) throws Exception {
            var store = new ArchiveStore(Path.of(args[0]), 60, System.err::println);
            for (int i = 0; i < 100; i++) get(store.append(message(i)));
            get(store.page(null, Long.MAX_VALUE, 100)); // Forced durable boundary.
            for (int i = 100; i < 120; i++) get(store.append(message(i)));
            Files.writeString(Path.of(args[1]), "flushed=100 pending=20");
            Thread.sleep(TimeUnit.MINUTES.toMillis(5));
            store.close();
        }
    }
}
