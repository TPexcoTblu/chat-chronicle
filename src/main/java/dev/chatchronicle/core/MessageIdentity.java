package dev.chatchronicle.core;

import com.google.gson.*;
import java.util.Set;

/** Compare visible content/styles; per-delivery hover/click payloads are not visual changes. */
public final class MessageIdentity {
    private static final Set<String> INTERACTION = Set.of("click_event", "hover_event", "clickEvent", "hoverEvent", "insertion");
    private MessageIdentity() {}
    public static boolean sameVisualJson(String left, String right) {
        if (left.equals(right)) return true;
        try { return visual(JsonParser.parseString(left)).equals(visual(JsonParser.parseString(right))); }
        catch (RuntimeException ex) { return false; }
    }
    private static JsonElement visual(JsonElement node) {
        if (node.isJsonObject()) {
            JsonObject result = new JsonObject();
            node.getAsJsonObject().entrySet().forEach(e -> { if (!INTERACTION.contains(e.getKey())) result.add(e.getKey(), visual(e.getValue())); });
            return result;
        }
        if (node.isJsonArray()) { JsonArray result = new JsonArray(); node.getAsJsonArray().forEach(child -> result.add(visual(child))); return result; }
        return node;
    }
}
