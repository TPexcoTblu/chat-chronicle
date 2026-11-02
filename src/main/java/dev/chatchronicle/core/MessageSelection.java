package dev.chatchronicle.core;

import java.util.*;

/** Selection snapshots message ranges, so incoming duplicates are never silently selected. */
public final class MessageSelection {
    private final LinkedHashMap<String, ArchiveStore.Row> selected = new LinkedHashMap<>();
    public boolean isEmpty() { return selected.isEmpty(); }
    public int size() { return selected.size(); }
    public void clear() { selected.clear(); }
    public boolean contains(ArchiveStore.Row row) { return selected.values().stream().anyMatch(value -> overlaps(value, row)); }
    public void toggle(ArchiveStore.Row row) {
        if (contains(row)) selected.values().removeIf(value -> overlaps(value, row));
        else selected.put(row.message().id(), row);
    }
    public List<ArchiveStore.Row> snapshot() {
        return selected.values().stream().sorted(Comparator.comparingLong(ArchiveStore.Row::firstSequence)).toList();
    }
    public long originals() { return selected.values().stream().mapToLong(ArchiveStore.Row::repeatCount).sum(); }
    private static boolean overlaps(ArchiveStore.Row a, ArchiveStore.Row b) {
        return a.message().day().equals(b.message().day()) && a.firstSequence() <= b.lastSequence() && b.firstSequence() <= a.lastSequence();
    }
}
