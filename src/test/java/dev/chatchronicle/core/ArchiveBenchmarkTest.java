package dev.chatchronicle.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveBenchmarkTest {
    @TempDir Path root;
    @Test void indexedSearchAndContextOnTwentyFiveThousandMessages() throws Exception {
        try (var store = new ArchiveStore(root, 2, System.err::println)) {
            long start = System.nanoTime();
            for (int i = 0; i < 25000; i++) {
                String text = "Сообщение номер " + i + " обсуждение строительства и ресурсов";
                store.append(new ChatRecord(UUID.randomUUID().toString(), 1793610000000L + i, "2026-11-02", text,
                        "{\"text\":\"" + text + "\"}", null, null, "SYSTEM_SERVER"));
            }
            var latest = store.page(null, Long.MAX_VALUE, 100).get(90, TimeUnit.SECONDS);
            long indexed = System.nanoTime();
            var result = store.search(new SearchQuery("номер 23681", null, null, false, false, false), Long.MAX_VALUE, 100, new AtomicBoolean()).get(30, TimeUnit.SECONDS);
            long searched = System.nanoTime();
            assertEquals(1, result.rows().size()); assertEquals(100, latest.rows().size());
            assertEquals(41, store.around(result.rows().getFirst().sequence(), 20).get(30, TimeUnit.SECONDS).size());
            System.out.printf("ARCHIVE_BENCHMARK records=25000 append+index=%.1fms indexed-search=%.1fms context=%.1fms%n",
                    (indexed - start) / 1e6, (searched - indexed) / 1e6, (System.nanoTime() - searched) / 1e6);
        }
    }
}
