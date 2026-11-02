package dev.chatchronicle.core;

import com.google.gson.*;
import java.time.*;
import java.util.UUID;

public final class SessionMarkers {
    public static final String KIND = "SESSION_START";
    private SessionMarkers() {}
    public static ChatRecord create(boolean world, String actor, String location, long time) {
        String key = world ? "session.world" : "session.server";
        String who = actor == null ? "" : actor;
        String where = location == null ? "" : location;
        String text = Messages.in("en_us", key, who.isBlank() ? Messages.in("en_us", "session.you") : who,
                where.isBlank() ? Messages.in("en_us", world ? "session.unknownWorld" : "session.unknownServer") : where);
        JsonObject meta = new JsonObject(); meta.addProperty("world", world); meta.addProperty("actor", who); meta.addProperty("location", where);
        JsonObject component = new JsonObject(); component.addProperty("text", text); component.addProperty("color", "dark_aqua"); component.add("chronicle_session", meta);
        return new ChatRecord(UUID.randomUUID().toString(), time,
                Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().toString(), text, component.toString(), null, null, KIND);
    }
    public static String display(ChatRecord record) {
        return display(record, Messages.language());
    }
    private static String display(ChatRecord record, String language) {
        if (!KIND.equals(record.kind())) return record.text();
        try {
            JsonObject meta = JsonParser.parseString(record.componentJson()).getAsJsonObject().getAsJsonObject("chronicle_session");
            boolean world = meta.get("world").getAsBoolean();
            String actor = meta.get("actor").getAsString(), location = meta.get("location").getAsString();
            return Messages.in(language, world ? "session.world" : "session.server", actor.isBlank() ? Messages.in(language, "session.you") : actor,
                    location.isBlank() ? Messages.in(language, world ? "session.unknownWorld" : "session.unknownServer") : location);
        } catch (RuntimeException ex) { return record.text(); }
    }
    public static java.util.List<String> searchVariants(ChatRecord record) {
        if (!KIND.equals(record.kind())) return java.util.List.of(record.text());
        var variants = new java.util.LinkedHashSet<String>(); variants.add(record.text());
        variants.add(display(record, "en_us")); variants.add(display(record, "ru_ru"));
        return java.util.List.copyOf(variants);
    }
    /** One marker per physical play connection; dimension changes do not start a new visit. */
    public static final class Visits {
        private Object connection;
        public boolean join(Object next) {
            if (next == null || next == connection) return false;
            connection = next; return true;
        }
        public void leave(Object previous) { if (connection == previous) connection = null; }
    }
}
