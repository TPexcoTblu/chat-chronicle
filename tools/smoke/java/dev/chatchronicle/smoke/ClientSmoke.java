package dev.chatchronicle.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import dev.chatchronicle.client.*;
import dev.chatchronicle.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises our mod through its own in-process APIs in a disposable local world.
 * No operating-system mouse/keyboard injection; results are not visual inspection.
 * The companion must only be installed in profiles created by prepare_profiles.py.
 */
public final class ClientSmoke {
    private static final String PREFIX = "CCS26";
    private static int phase, ticks, waitUntil;
    private static long started = System.nanoTime();
    private static CompletableFuture<?> pending;
    private static HistoryScreen history;
    private static MessageView view;
    private static String anchor, anchorText;
    private static boolean finished, reopen;
    private static Path directory;
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static final List<String> passed = new ArrayList<>();

    public static void tick(Minecraft mc) {
        if (finished) return;
        try {
            if (directory == null) {
                directory = mc.gameDirectory.toPath();
                if (!Files.isRegularFile(directory.resolve("chatchronicle-smoke-authorized"))) { finished = true; return; }
                reopen = Files.exists(directory.resolve("smoke-first.json"));
                result.put("phase", reopen ? "reopen" : "first");
                result.put("started", Instant.now().toString());
                result.put("checks", passed);
            }
            ticks++;
            if ((System.nanoTime() - started) / 1e9 > 180) throw new AssertionError("Timed out in phase " + phase + ", screen=" + mc.gui.screen());
            if (ticks < waitUntil) return;
            switch (phase) {
                case 0 -> {
                    if (!mc.isGameLoadFinished() || !(mc.gui.screen() instanceof TitleScreen)) return;
                    require(ChatChronicle.get() != null, "Mod entrypoint did not initialize");
                    pending = ChatChronicle.get().archive.days(); phase = 1;
                }
                case 1 -> {
                    if (!ready()) return;
                    check("entrypoint_and_embedded_sqlite");
                    if (reopen) {
                        pending = search(PREFIX + "_DUP"); phase = 20;
                    } else {
                        mc.createWorldOpenFlows().openWorld("Chat Chronicle Smoke", () -> fail(mc, new AssertionError("World loading cancelled")));
                        phase = 2;
                    }
                }
                case 2 -> {
                    if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
                    mc.gui.setScreen(null);
                    mc.getConnection().sendChat(PREFIX + "_NET local smoke message");
                    waitUntil = ticks + 30; phase = 3;
                }
                case 3 -> {
                    var chat = mc.gui.hud.getChat();
                    for (int i = 0; i < 620; i++) chat.addClientSystemMessage(Component.literal(PREFIX + " line " + String.format("%04d", i)));
                    for (int i = 0; i < 3; i++) chat.addClientSystemMessage(Component.literal(PREFIX + "_DUP"));
                    pending = ChatChronicle.get().archive.groupedPage(null, Long.MAX_VALUE, 1000);
                    phase = 4;
                }
                case 4 -> {
                    if (!ready()) return;
                    var page = (ArchiveStore.Page) pending.join();
                    require(page.rows().stream().anyMatch(r -> r.message().text().equals(PREFIX + "_DUP") && r.repeatCount() == 3), "Duplicate group is not x3");
                    require(page.rows().stream().anyMatch(r -> r.message().text().contains(PREFIX + "_NET")), "Local server chat not captured");
                    require(page.rows().stream().anyMatch(r -> SessionMarkers.KIND.equals(r.message().kind())), "World entry marker missing");
                    check("world_join_network_capture_and_duplicate_x3");
                    history = new HistoryScreen(new ChatScreen("", false)); mc.gui.setScreen(history);
                    view = field(history, "view"); phase = 5; waitUntil = ticks + 10;
                }
                case 5 -> {
                    if (view.loading) return;
                    require(view.notice.isEmpty(), "History load failed: " + view.notice.text());
                    if (view.rows.size() < 400 && view.more) { view.load(false); return; }
                    require(view.rows.size() >= 400, "History failed to page to 400 rows");
                    view.scroll(40); phase = 6; waitUntil = ticks + 10;
                }
                case 6 -> {
                    var hits = hits(); require(hits.size() > 2, "History did not render clickable rows");
                    anchor = hits.getFirst().line().row().message().id();
                    anchorText = hits.getFirst().line().row().message().text();
                    // Bulk selection uses the public selection model, then the real context-menu click path.
                    for (int i = 0; i < view.rows.size(); i++) if (i < 100 || i >= 200) view.selection.toggle(view.rows.get(i));
                    var clicked = hits.get(1); view.selection.toggle(clicked.line().row());
                    result.put("hidden_selection_rows", view.selection.size());
                    history.mouseClicked(mouse(view.x + 8, clicked.y() + 1, InputConstants.MOUSE_BUTTON_RIGHT), false);
                    require(field(history, "menu") != null, "Right-click did not open context menu");
                    phase = 7; waitUntil = ticks + 5;
                }
                case 7 -> {
                    MessageMenu menu = field(history, "menu"); List<?> items = field(menu, "items");
                    int index = -1;
                    for (int i = 0; i < items.size(); i++) {
                        Method label = items.get(i).getClass().getDeclaredMethod("label"); label.setAccessible(true);
                        if (label.invoke(items.get(i)).equals(Messages.text("selection.hide"))) index = i;
                    }
                    require(index >= 0, "Bulk hide action missing");
                    int x = field(menu, "x"), y = field(menu, "y");
                    history.mouseClicked(mouse(x + 10, y + index * 18 + 6, InputConstants.MOUSE_BUTTON_LEFT), false);
                    require(view.selection.isEmpty(), "Hide did not clear selection");
                    phase = 8; waitUntil = ticks + 15;
                }
                case 8 -> {
                    if (view.loading) return;
                    var hits = hits(); require(!hits.isEmpty(), "Empty viewport after bulk hide");
                    require(anchor.equals(hits.getFirst().line().row().message().id()), "Reading anchor jumped after hiding");
                    require(hits.stream().noneMatch(h -> ChatChronicle.get().hidden.contains(h.line().row().message().id())), "A hidden row remained visible");
                    check("render_right_click_bulk_hide_and_reading_anchor");
                    history.mouseScrolled(view.x + 10, view.top + 10, 0, 5);
                    phase = 9; waitUntil = ticks + 10;
                }
                case 9 -> {
                    if (view.loading) return;
                    require(!hits().isEmpty(), "Scroll up left empty viewport");
                    require(hits().stream().noneMatch(h -> ChatChronicle.get().hidden.contains(h.line().row().message().id())), "Scrolling resurrected a hidden row");
                    history.mouseScrolled(view.x + 10, view.top + 10, 0, -5);
                    phase = 10; waitUntil = ticks + 10;
                }
                case 10 -> {
                    if (view.loading) return;
                    require(!hits().isEmpty(), "Scroll down left empty viewport");
                    check("scroll_up_and_down_after_hiding");
                    EditBox input = field(history, "search"); input.setValue(anchorText);
                    phase = 11; waitUntil = ticks + 20;
                }
                case 11 -> {
                    if (view.loading) return;
                    require(view.searchMode && view.rows.size() == 1, "Search did not return the visible anchor: " + anchorText + ", rows=" + view.rows.size());
                    var hit = hits().getFirst();
                    history.mouseClicked(mouse(view.x + 10, hit.y() + 1, InputConstants.MOUSE_BUTTON_LEFT), false);
                    phase = 12; waitUntil = ticks + 10;
                }
                case 12 -> {
                    if (view.loading) return;
                    require(view.expandedContext && view.highlighted != null && !hits().isEmpty(), "Search result did not open context");
                    check("search_and_click_to_context");
                    pending = search(PREFIX); phase = 13;
                }
                case 13 -> {
                    if (!ready()) return;
                    result.put("saved_fixture_records", ((ArchiveStore.Page) pending.join()).rows().size());
                    require(((ArchiveStore.Page) pending.join()).rows().size() == 624, "Unexpected fixture count");
                    check("archive_retains_hidden_messages");
                    finish(mc);
                }
                case 20 -> {
                    if (!ready()) return;
                    require(((ArchiveStore.Page) pending.join()).rows().size() == 3, "Duplicates not restored after shutdown");
                    pending = search(PREFIX); phase = 21;
                }
                case 21 -> {
                    if (!ready()) return;
                    require(((ArchiveStore.Page) pending.join()).rows().size() == 624, "Archive count changed after reopening");
                    require(ChatChronicle.get().hidden.isEmpty(), "Temporary hidden state persisted");
                    require(!ChatChronicle.get().archive.previousInterrupted, "Normal exit was marked interrupted");
                    check("normal_shutdown_reopen_and_hidden_reset"); finish(mc);
                }
                default -> throw new AssertionError("Unknown phase " + phase);
            }
        } catch (Throwable error) { fail(mc, error); }
    }
    private static CompletableFuture<ArchiveStore.Page> search(String text) {
        return ChatChronicle.get().archive.search(new SearchQuery(text, null, null, false, false, false), Long.MAX_VALUE, 1000, new AtomicBoolean());
    }
    private static boolean ready() { if (!pending.isDone()) return false; pending.join(); return true; }
    private static MouseButtonEvent mouse(double x, double y, int button) { return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)); }
    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(owner);
    }
    private static List<MessageView.Hit> hits() throws Exception {
        List<MessageView.Hit> all = field(view, "hits"); return all.stream().filter(h -> h.line().row() != null).toList();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void check(String name) { passed.add(name); System.out.println("CC_SMOKE_PASS " + name); }
    private static void finish(Minecraft mc) { result.put("status", "passed"); write(); finished = true; mc.stop(); }
    private static void fail(Minecraft mc, Throwable error) {
        error.printStackTrace(); result.put("status", "failed"); result.put("failed_phase", phase); result.put("error", error.toString());
        write(); finished = true; mc.stop();
    }
    private static void write() {
        try {
            result.put("finished", Instant.now().toString()); result.put("seconds", (System.nanoTime() - started) / 1e9);
            Files.writeString(directory.resolve(reopen ? "smoke-reopen.json" : "smoke-first.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}
