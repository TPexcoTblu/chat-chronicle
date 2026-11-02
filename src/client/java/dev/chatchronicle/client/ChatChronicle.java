package dev.chatchronicle.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.chatchronicle.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;

public final class ChatChronicle implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Chat Chronicle");
    public static ChatChronicle instance;
    public ChronicleConfig config;
    public ArchiveStore archive;
    public final Set<String> hidden = new HashSet<>();
    public final Deque<ChatRecord> recent = new ArrayDeque<>();
    public final Deque<ArchiveStore.Row> recentGroups = new ArrayDeque<>();
    private long liveSequence;
    public final Map<MessageSignature, String[]> senders = new LinkedHashMap<>();
    public long revision;
    public long presentationRevision;
    public Path configPath;
    public boolean stopping;
    private long seenMaintenance;
    private final SessionMarkers.Visits visits = new SessionMarkers.Visits();
    public final Map<String, Long> arrivals = new LinkedHashMap<>();
    public long lastActivityNanos = System.nanoTime();
    public void touch() { lastActivityNanos = System.nanoTime(); }

    @Override public void onInitializeClient() {
        instance = this;
        configPath = FabricLoader.getInstance().getConfigDir().resolve("chat-chronicle.json");
        config = ChronicleConfig.load(configPath);
        Messages.setLanguage(config.language);
        archive = new ArchiveStore(FabricLoader.getInstance().getGameDir().resolve("chat-chronicle"),
                config.saveIntervalSeconds, LOG::error);
        archive.configureRetention(new RetentionPolicy(config.retentionDays, config.maxArchiveMessages));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!stopping && seenMaintenance != archive.maintenanceRevision) {
                seenMaintenance = archive.maintenanceRevision;
                Set<String> beforeRequest = new HashSet<>(); recent.forEach(record -> beforeRequest.add(record.id()));
                archive.recentSnapshot(config.cacheMessages).thenAccept(snapshot -> onClient(() -> {
                    Set<String> indexed = new HashSet<>(); snapshot.originals().forEach(row -> indexed.add(row.message().id()));
                    List<ChatRecord> newer = recent.stream().filter(record -> !beforeRequest.contains(record.id()) && !indexed.contains(record.id())).toList();
                    recent.removeIf(record -> beforeRequest.contains(record.id()) && !indexed.contains(record.id()));
                    recentGroups.clear(); recentGroups.addAll(snapshot.groups());
                    for (int i = newer.size() - 1; i >= 0; i--) addLiveGroup(newer.get(i));
                    presentationRevision++; revision++;
                }));
            }
        });
        ClientReceiveMessageEvents.CHAT.register((text, signed, sender, type, time) -> {
            if (signed != null && signed.signature() != null && sender != null) {
                senders.put(signed.signature(), new String[]{sender.name(), sender.id().toString()});
                while (senders.size() > 500) senders.remove(senders.keySet().iterator().next());
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!visits.join(handler.getConnection()) || !config.showSessionMarkers) return;
            boolean world = client.hasSingleplayerServer();
            String location = world && client.getSingleplayerServer() != null
                    ? client.getSingleplayerServer().getWorldData().getLevelName()
                    : client.getCurrentServer() == null ? null : client.getCurrentServer().name;
            acceptRecord(SessionMarkers.create(world, client.getUser().getName(), location, System.currentTimeMillis()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> visits.leave(handler.getConnection()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { stopping = true; archive.close(); });
        LOG.info("Chat Chronicle initialized: Minecraft 26.3, daily archive enabled");
    }
    public void capture(Component component, MessageSignature signature, String source) {
        if (stopping) return;
        try {
            String[] sender = senders.remove(signature);
            String json = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow().toString();
            ChatRecord record = ChatRecord.create(component.getString(), json,
                    sender == null ? null : sender[0], sender == null ? null : sender[1], source);
            acceptRecord(record);
        } catch (Exception ex) { LOG.error("Cannot serialize chat message", ex); }
    }
    private void acceptRecord(ChatRecord record) {
        if (stopping) return;
        touch();
            recent.addFirst(record);
            while (recent.size() > config.cacheMessages) recent.removeLast();
            boolean continuing = !recentGroups.isEmpty() && record.sameContent(recentGroups.getFirst().message());
            if (!continuing || record.time() - recentGroups.getFirst().message().time() >= config.chatIdleSeconds * 1000L) arrivals.put(record.id(), System.nanoTime());
            while (arrivals.size() > Math.max(1000, config.cacheMessages * 2)) arrivals.remove(arrivals.keySet().iterator().next());
            addLiveGroup(record);
            revision++;
            archive.append(record).exceptionally(ex -> { LOG.error("Cannot archive message", ex); return null; });
    }
    private void addLiveGroup(ChatRecord record) {
        long sequence = liveSequence + 1;
        if (!recentGroups.isEmpty()) sequence = Math.max(sequence, recentGroups.getFirst().lastSequence() + 1);
        liveSequence = sequence;
        long count = 1, firstSequence = sequence;
        if (!recentGroups.isEmpty() && record.sameContent(recentGroups.getFirst().message())) {
            var previous = recentGroups.removeFirst(); count += previous.repeatCount(); firstSequence = previous.firstSequence();
        }
        recentGroups.addFirst(new ArchiveStore.Row(sequence, record, firstSequence, sequence, count));
        while (recentGroups.size() > config.cacheMessages) recentGroups.removeLast();
    }
    public static Component decode(ChatRecord record) {
        if (SessionMarkers.KIND.equals(record.kind())) return Component.literal(SessionMarkers.display(record)).withStyle(net.minecraft.ChatFormatting.DARK_AQUA);
        try { return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(record.componentJson())).getOrThrow(); }
        catch (Exception ex) { return Component.literal(record.text()); }
    }
    public void saveConfig() {
        presentationRevision++;
        try { config.save(configPath); } catch (Exception ex) { LOG.error("Cannot save settings", ex); }
    }
    public static ChatChronicle get() { return instance; }
    public static void onClient(Runnable runnable) { Minecraft.getInstance().execute(runnable); }
}
