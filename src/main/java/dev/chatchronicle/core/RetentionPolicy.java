package dev.chatchronicle.core;

/** Both zero values mean unlimited. Age counts calendar days, including today. */
public record RetentionPolicy(int days, long messages) {
    public static final RetentionPolicy UNLIMITED = new RetentionPolicy(0, 0);
    public RetentionPolicy {
        if (days < 0 || days > 365000 || messages < 0) throw new IllegalArgumentException(Messages.text("retention.invalid"));
    }
    public boolean enabled() { return days > 0 || messages > 0; }
}
