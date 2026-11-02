package dev.chatchronicle.core;

import java.time.*;
import java.util.UUID;

/** One original message. Decorations and duplicate counters never alter it. */
public record ChatRecord(String id, long time, String day, String text, String componentJson,
                         String sender, String senderUuid, String kind) {
    public static ChatRecord create(String text, String json, String sender, String uuid, String kind) {
        long now = System.currentTimeMillis();
        return new ChatRecord(UUID.randomUUID().toString(), now,
                Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                text, json, sender, uuid, kind);
    }
    public boolean valid() {
        try {
            UUID.fromString(id); LocalDate.parse(day);
            return time >= 0 && text != null && componentJson != null && kind != null;
        } catch (RuntimeException ex) { return false; }
    }
    public boolean sameContent(ChatRecord other) {
        if (SessionMarkers.KIND.equals(kind) || other != null && SessionMarkers.KIND.equals(other.kind)) return false;
        return other != null && day.equals(other.day) && text.equals(other.text)
                && java.util.Objects.equals(senderUuid, other.senderUuid) && kind.equals(other.kind)
                && MessageIdentity.sameVisualJson(componentJson, other.componentJson);
    }
}
