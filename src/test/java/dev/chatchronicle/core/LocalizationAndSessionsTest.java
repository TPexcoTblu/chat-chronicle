package dev.chatchronicle.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationAndSessionsTest {
    @TempDir Path root;
    @AfterEach void resetLanguage() { Messages.setLanguage("en_us"); }
    private <T> T get(CompletableFuture<T> future) throws Exception { return future.get(30, TimeUnit.SECONDS); }

    @Test void englishAndRussianHaveCompleteMatchingKeysAndFormatArguments() {
        var en = Messages.catalog("en_us"); var ru = Messages.catalog("ru_ru");
        assertTrue(en.size() >= 140); assertEquals(en.keySet(), ru.keySet());
        for (String key : en.keySet()) {
            assertFalse(en.get(key).isBlank(), key); assertFalse(ru.get(key).isBlank(), key);
            long count = en.get(key).chars().filter(c -> c == '%').count();
            assertEquals(count, ru.get(key).chars().filter(c -> c == '%').count(), key);
            Object[] args = new Object[(int) count]; Arrays.fill(args, "42");
            assertDoesNotThrow(() -> Messages.in("en_us", key, args), key);
            assertDoesNotThrow(() -> Messages.in("ru_ru", key, args), key);
        }
    }
    @Test void productionTranslationReferencesExistAndNoRussianUiStringsRemainInCode() throws Exception {
        var referencedKey = Pattern.compile("(?:Messages\\.text|new UiMessage)\\(\"([^\"]+)\"\\s*[,)]");
        var cyrillic = Pattern.compile("[А-Яа-яЁё]");
        for (String source : List.of("src/main/java", "src/client/java")) try (var files = Files.walk(Path.of(source))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file); var matcher = referencedKey.matcher(text);
                while (matcher.find()) assertTrue(Messages.catalog("en_us").containsKey(matcher.group(1)), file + ": " + matcher.group(1));
                assertFalse(cyrillic.matcher(text).find(), file.toString());
            }
        }
    }
    @Test void englishIsDefaultAndLanguageChoicePersistsWithFallback() throws Exception {
        assertEquals("en_us", new ChronicleConfig().language);
        var config = new ChronicleConfig(); config.language = "ru_ru";
        Path path = root.resolve("config.json"); config.save(path);
        assertEquals("ru_ru", ChronicleConfig.load(path).language);
        Files.writeString(path, "{\"chatHudWidth\":480}");
        var legacy = ChronicleConfig.load(path); assertEquals("en_us", legacy.language); assertEquals(480, legacy.chatHudWidth);
        Files.writeString(path, "{\"language\":\"unknown\"}");
        assertEquals("en_us", ChronicleConfig.load(path).language);
        Messages.setLanguage("unknown"); assertEquals("Settings", Messages.text("nav.settings"));
        Messages.setLanguage("ru_ru"); assertEquals("Настройки", Messages.text("nav.settings"));
        assertSame(Messages.catalog("en_us"), Messages.catalog("invalid"));
    }
    @Test void archiveStatusAndStoredMarkersFollowTheCurrentLanguageWithoutChangingRecords() {
        UiMessage status = new UiMessage("archive.skipped", 3);
        ChatRecord marker = SessionMarkers.create(false, "Alex", "Example server", 1793610000000L);
        String original = marker.componentJson();
        Messages.setLanguage("en_us"); assertTrue(status.text().startsWith("Damaged")); assertTrue(SessionMarkers.display(marker).contains("joined server"));
        Messages.setLanguage("ru_ru"); assertTrue(status.text().contains("повреждённые")); assertTrue(SessionMarkers.display(marker).contains("зашёл на сервер"));
        assertEquals(original, marker.componentJson());
        assertTrue(SessionMarkers.display(SessionMarkers.create(true, null, null, 1793610000000L)).contains("Одиночный мир"));
    }
    @Test void connectionVisitsAreDeduplicatedAndReconnectsRemainVisible() {
        var visits = new SessionMarkers.Visits(); Object first = new Object(), second = new Object();
        assertTrue(visits.join(first)); assertFalse(visits.join(first));
        visits.leave(first); assertTrue(visits.join(first)); assertTrue(visits.join(second));
        visits.leave(first); assertFalse(visits.join(second)); assertFalse(visits.join(null));
    }
    @Test void entryMarkersSplitDuplicateRunsButRemainInOneDailyArchive() throws Exception {
        long time = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        ChatRecord join = SessionMarkers.create(false, "Alex", "Example server", time);
        ChatRecord a = new ChatRecord(UUID.randomUUID().toString(), time, join.day(), "Hello", "{\"text\":\"Hello\"}", null, null, "SYSTEM_SERVER");
        ChatRecord b = new ChatRecord(UUID.randomUUID().toString(), time + 1, join.day(), "Hello", "{\"text\":\"Hello\"}", null, null, "SYSTEM_SERVER");
        ChatRecord again = SessionMarkers.create(false, "Alex", "Example server", time + 2);
        assertFalse(join.sameContent(again));
        try (var store = new ArchiveStore(root, 2, System.err::println)) {
            get(store.append(a)); get(store.append(join)); get(store.append(b)); get(store.append(again));
            assertEquals(4, get(store.groupedPage(null, Long.MAX_VALUE, 10)).rows().size());
            assertEquals(1, get(store.days()).size()); assertEquals(4, get(store.days()).getFirst().count());
            assertEquals(2, get(store.search(new SearchQuery("joined server", null, null, false, false, false), Long.MAX_VALUE, 10, new java.util.concurrent.atomic.AtomicBoolean())).rows().size());
            assertEquals(2, get(store.search(new SearchQuery("зашёл на сервер", null, null, false, false, false), Long.MAX_VALUE, 10, new java.util.concurrent.atomic.AtomicBoolean())).rows().size());
        }
        try (var store = new ArchiveStore(root, 2, System.err::println)) {
            assertEquals(2, get(store.page(null, Long.MAX_VALUE, 10)).rows().stream().filter(r -> SessionMarkers.KIND.equals(r.message().kind())).count());
        }
    }
    @Test void releaseDescriptionsTrackTheCurrentBuildVersion() throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) { properties.load(reader); }
        String version = properties.getProperty("version");
        for (String document : List.of("README.md", "README.ru.md", "CHANGELOG.md", "PROGRESS.md")) {
            assertTrue(Files.readString(Path.of(document)).contains(version), document + " must mention " + version);
        }
    }
}
