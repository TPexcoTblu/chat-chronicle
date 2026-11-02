package dev.chatchronicle.core;

/** Resolve persistent archive status at display time so language switches also update it. */
public record UiMessage(String key, Object... arguments) {
    public static final UiMessage EMPTY = new UiMessage("");
    public boolean isEmpty() { return key.isEmpty(); }
    public String text() { return key.isEmpty() ? "" : Messages.text(key, arguments); }
}
