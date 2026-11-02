package dev.chatchronicle.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;

public final class ChronicleConfig {
    public String language = "en_us";
    public boolean showSessionMarkers = true;
    public int saveIntervalSeconds = 2;
    public boolean compactDuplicates = true;
    public boolean showTimestamps = true;
    public String timestampPattern = "HH:mm:ss";
    public String replyTemplate = "/msg $ ";
    public int pageSize = 100;
    public int cacheMessages = 500;
    public boolean shiftRightCopy = true;
    public boolean ctrlRightContext = true;
    // Zero means no automatic retention limit. Never enabled implicitly.
    public int retentionDays = 0;
    public long maxArchiveMessages = 0;
    // GUI pixels after text scaling. Zero follows the corresponding vanilla setting.
    public int chatHudWidth = 0;
    public int chatHudHeightFocused = 0;
    public int chatHudHeightUnfocused = 0;
    public boolean chatAnimations = true;
    public boolean fadeFocusedChat = true;
    public int chatAppearMillis = 180;
    public int chatIdleSeconds = 8;
    public int chatFadeMillis = 1200;
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public static ChronicleConfig load(Path path) {
        try {
            ChronicleConfig config = JSON.fromJson(Files.readString(path), ChronicleConfig.class);
            if (config == null) return new ChronicleConfig();
            config.language = "ru_ru".equals(config.language) ? "ru_ru" : "en_us";
            config.saveIntervalSeconds = Math.clamp(config.saveIntervalSeconds, 1, 60);
            config.pageSize = Math.clamp(config.pageSize, 20, 500);
            config.cacheMessages = Math.clamp(config.cacheMessages, 100, 5000);
            config.cacheMessages = Math.max(config.cacheMessages, config.pageSize * 2);
            config.retentionDays = Math.clamp(config.retentionDays, 0, 365000);
            config.maxArchiveMessages = Math.max(0, config.maxArchiveMessages);
            config.chatHudWidth = hudDimension(config.chatHudWidth);
            config.chatHudHeightFocused = hudDimension(config.chatHudHeightFocused);
            config.chatHudHeightUnfocused = hudDimension(config.chatHudHeightUnfocused);
            config.chatAppearMillis = Math.clamp(config.chatAppearMillis, 0, 2000);
            config.chatIdleSeconds = Math.clamp(config.chatIdleSeconds, 1, 600);
            config.chatFadeMillis = Math.clamp(config.chatFadeMillis, 0, 10000);
            if (config.replyTemplate == null) config.replyTemplate = "/msg $ ";
            try { java.time.format.DateTimeFormatter.ofPattern(config.timestampPattern); }
            catch (RuntimeException ex) { config.timestampPattern = "HH:mm:ss"; }
            return config;
        } catch (Exception ex) { return new ChronicleConfig(); }
    }
    private static int hudDimension(int value) { return value <= 0 ? 0 : Math.clamp(value, 20, 8192); }
    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, JSON.toJson(this));
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
