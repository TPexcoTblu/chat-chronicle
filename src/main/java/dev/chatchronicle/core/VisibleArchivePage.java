package dev.chatchronicle.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/** Fill a visible page without charging hidden records against its size. */
public final class VisibleArchivePage {
    private VisibleArchivePage() {}

    public static CompletableFuture<ArchiveStore.Page> load(long boundary, int limit, boolean newer,
            Set<String> hidden, AtomicBoolean cancelled,
            BiFunction<Long, Integer, CompletableFuture<ArchiveStore.Page>> fetch) {
        return new Scan(limit, newer, Set.copyOf(hidden), cancelled, fetch).next(boundary);
    }

    private static final class Scan {
        final int limit;
        final boolean newer;
        final Set<String> hidden;
        final AtomicBoolean cancelled;
        final BiFunction<Long, Integer, CompletableFuture<ArchiveStore.Page>> fetch;
        final List<ArchiveStore.Row> rows = new ArrayList<>();

        Scan(int limit, boolean newer, Set<String> hidden, AtomicBoolean cancelled,
                BiFunction<Long, Integer, CompletableFuture<ArchiveStore.Page>> fetch) {
            if (limit < 1) throw new IllegalArgumentException("Page size must be positive");
            this.limit = limit; this.newer = newer; this.hidden = hidden;
            this.cancelled = cancelled; this.fetch = fetch;
        }

        CompletableFuture<ArchiveStore.Page> next(long boundary) {
            if (cancelled.get()) return CompletableFuture.failedFuture(new CancellationException());
            return fetch.apply(boundary, limit).thenComposeAsync(page -> {
                if (cancelled.get()) throw new CancellationException();
                long cursor = page.cursor();
                int consumed = 0;
                for (int i = 0; i < page.rows().size(); i++) {
                    var row = page.rows().get(newer ? page.rows().size() - 1 - i : i);
                    cursor = newer ? row.lastSequence() : row.firstSequence(); consumed++;
                    if (!hidden.contains(row.message().id())) {
                        if (newer) rows.add(0, row); else rows.add(row);
                        if (rows.size() == limit) break;
                    }
                }
                if (!page.more() || rows.size() >= limit)
                    return CompletableFuture.completedFuture(new ArchiveStore.Page(List.copyOf(rows),
                            page.more() || consumed < page.rows().size(), cursor));
                if (newer ? cursor <= boundary : cursor >= boundary)
                    return CompletableFuture.failedFuture(new IllegalStateException("Archive cursor did not advance"));
                // Continue off the archive worker, releasing it between bounded queries.
                return next(cursor);
            });
        }
    }
}
