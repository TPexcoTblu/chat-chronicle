package dev.chatchronicle.core;

public final class ChatMotion {
    private ChatMotion() {}
    public static double ease(double progress) { double t = Math.clamp(progress, 0, 1); return t * t * (3 - 2 * t); }
    public static double appearance(double ageMillis, int durationMillis) { return durationMillis <= 0 ? 1 : ease(ageMillis / durationMillis); }
    public static double fade(double ageMillis, int idleMillis, int durationMillis) {
        if (ageMillis <= idleMillis) return 1;
        return durationMillis <= 0 ? 0 : 1 - ease((ageMillis - idleMillis) / durationMillis);
    }
    public static final class Tween {
        private double from = 1, target = 1;
        private long started;
        private int duration;
        private double current(long now) {
            if (duration <= 0) return target;
            return from + (target - from) * ease((now - started) / (duration * 1_000_000.0));
        }
        public double sample(boolean visible, long now, int millis, boolean animate) {
            double next = visible ? 1 : 0;
            if (!animate) { from = target = next; duration = 0; return next; }
            if (next != target) { from = current(now); target = next; started = now; duration = millis; }
            return current(now);
        }
    }
}
