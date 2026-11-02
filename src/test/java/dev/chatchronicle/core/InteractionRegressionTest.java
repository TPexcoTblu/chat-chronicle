package dev.chatchronicle.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class InteractionRegressionTest {
    @TempDir Path root;
    private ChatRecord record(String text, String json) {
        return new ChatRecord(UUID.randomUUID().toString(), System.currentTimeMillis(), "2026-11-02", text,
                json, "Alex", "00000000-0000-0000-0000-000000000001", "PLAYER");
    }
    private ChatRecord delivery(int nonce) {
        return record("Alex: Hello", "{\"text\":\"\",\"extra\":[{\"text\":\"Alex: \",\"color\":\"gold\",\"click_event\":{\"action\":\"run_command\",\"command\":\"/reply " + nonce + "\"}},{\"text\":\"Hello\",\"hover_event\":{\"action\":\"show_text\",\"value\":\"delivery " + nonce + "\"}}]}");
    }
    private <T> T get(CompletableFuture<T> task) throws Exception { return task.get(60, TimeUnit.SECONDS); }
    private ArchiveStore open() { return new ArchiveStore(root, 2, System.err::println); }
    @Test void changingServerClickAndHoverPayloadsDoNotPreventStackingOrLoseOriginalJson() throws Exception {
        ChatRecord first = delivery(1), second = delivery(2);
        assertNotEquals(first.componentJson(), second.componentJson()); assertTrue(first.sameContent(second));
        try (var store = open()) {
            get(store.append(first)); get(store.append(second));
            var groups = get(store.groupedPage(null, Long.MAX_VALUE, 10));
            assertEquals(1, groups.rows().size()); assertEquals(2, groups.rows().getFirst().repeatCount());
            assertEquals(List.of(second.componentJson(), first.componentJson()), get(store.page(null, Long.MAX_VALUE, 10)).rows().stream().map(r -> r.message().componentJson()).toList());
        }
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("index.sqlite")); var st = db.createStatement()) { st.execute("PRAGMA user_version=2"); }
        try (var store = open()) { assertEquals(2, get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().getFirst().repeatCount()); }
    }
    @Test void differentVisibleStylesTextOrSendersRemainSeparate() {
        ChatRecord red = record("Hello", "{\"text\":\"Hello\",\"color\":\"red\"}");
        ChatRecord blue = record("Hello", "{\"text\":\"Hello\",\"color\":\"blue\"}");
        assertFalse(red.sameContent(blue));
        assertFalse(red.sameContent(record("Other", red.componentJson())));
        ChatRecord otherSender = new ChatRecord(UUID.randomUUID().toString(), red.time(), red.day(), red.text(), red.componentJson(), "Other", UUID.randomUUID().toString(), red.kind());
        assertFalse(red.sameContent(otherSender));
    }
    @Test void selectionSnapshotsAreChronologicalAndDoNotExpandWithNewDuplicates() {
        ChatRecord a = delivery(1), b = record("B", "{\"text\":\"B\"}");
        MessageSelection selected = new MessageSelection();
        var earlier = new ArchiveStore.Row(4, a, 1, 4, 4); var later = new ArchiveStore.Row(12, b, 10, 12, 3);
        selected.toggle(later); selected.toggle(earlier);
        assertEquals(List.of(earlier, later), selected.snapshot()); assertEquals(7, selected.originals());
        var expanded = new ArchiveStore.Row(5, delivery(2), 1, 5, 5);
        assertTrue(selected.contains(expanded)); assertEquals(7, selected.originals());
        selected.toggle(expanded); assertEquals(List.of(later), selected.snapshot());
        selected.clear(); assertTrue(selected.isEmpty());
    }
    @Test void bulkDeleteHandlesOverlappingSelectionsBatchesAndNewMessagesWithoutOverDeletion() throws Exception {
        ChatRecord arrival = delivery(9999), keep = record("KEEP", "{\"text\":\"KEEP\"}");
        try (var store = open()) {
            for (int i = 0; i < 600; i++) store.append(delivery(i));
            var selectedGroup = get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().getFirst();
            var overlappingOriginal = get(store.page(null, Long.MAX_VALUE, 10)).rows().getLast();
            get(store.append(arrival)); get(store.append(keep));
            assertEquals(600, get(store.deleteSelected(List.of(selectedGroup, overlappingOriginal))));
            assertEquals(List.of(keep, arrival), get(store.page(null, Long.MAX_VALUE, 10)).rows().stream().map(ArchiveStore.Row::message).toList());
            var snapshot = get(store.recentSnapshot(100));
            assertEquals(List.of(keep, arrival), snapshot.originals().stream().map(ArchiveStore.Row::message).toList());
            assertEquals(List.of(1L, 1L), snapshot.groups().stream().map(ArchiveStore.Row::repeatCount).toList());
            assertEquals(0, get(store.deleteSelected(List.of())));
        }
        for (String file : List.of("index.sqlite", "index.sqlite-shm", "index.sqlite-wal")) Files.deleteIfExists(root.resolve(file));
        try (var store = open()) { assertEquals(List.of(keep, arrival), get(store.page(null, Long.MAX_VALUE, 10)).rows().stream().map(ArchiveStore.Row::message).toList()); }
    }
    @Test void animationAppearsFadesAndResumesWithoutAnOpacityJump() {
        assertEquals(0, ChatMotion.appearance(0, 200)); assertEquals(1, ChatMotion.appearance(200, 200));
        assertEquals(1, ChatMotion.fade(7999, 8000, 1000)); assertEquals(.5, ChatMotion.fade(8500, 8000, 1000), .0001);
        assertEquals(0, ChatMotion.fade(9000, 8000, 1000));
        var tween = new ChatMotion.Tween();
        assertEquals(1, tween.sample(false, 0, 1000, true));
        assertEquals(.5, tween.sample(false, 500_000_000L, 1000, true), .0001);
        assertEquals(.5, tween.sample(true, 500_000_000L, 200, true), .0001);
        assertTrue(tween.sample(true, 600_000_000L, 200, true) > .5);
        assertEquals(1, tween.sample(true, 700_000_000L, 200, true));
        assertEquals(0, tween.sample(false, 800_000_000L, 1000, false));
    }
}
