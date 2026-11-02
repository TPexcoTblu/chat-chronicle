package dev.chatchronicle.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Mod language is explicitly selected; English is independent of the game's language. */
public final class Messages {
    private static final Map<String, Map<String, String>> CATALOGS = Map.of("en_us", load("en_us"), "ru_ru", load("ru_ru"));
    private static volatile String language = "en_us";
    private Messages() {}
    private static Map<String, String> load(String code) {
        String resource = "/assets/chatchronicle/lang/" + code + ".json";
        try (InputStream stream = Messages.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing translations: " + resource);
            JsonObject object = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> result = new LinkedHashMap<>(); object.entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString()));
            return Collections.unmodifiableMap(result);
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    public static String language() { return language; }
    public static void setLanguage(String code) { language = "ru_ru".equals(code) ? "ru_ru" : "en_us"; }
    public static Locale locale() { return "ru_ru".equals(language) ? Locale.forLanguageTag("ru-RU") : Locale.US; }
    public static Map<String, String> catalog(String code) { return CATALOGS.getOrDefault(code, CATALOGS.get("en_us")); }
    public static String text(String key, Object... args) { return in(language, key, args); }
    public static String in(String code, String key, Object... args) {
        String template = catalog(code).getOrDefault(key, CATALOGS.get("en_us").getOrDefault(key, key));
        return args.length == 0 ? template : String.format(Locale.ROOT, template, args);
    }
}
