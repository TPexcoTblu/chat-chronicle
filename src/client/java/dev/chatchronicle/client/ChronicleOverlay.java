package dev.chatchronicle.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.chatchronicle.core.Messages;

import dev.chatchronicle.core.ArchiveStore;
import dev.chatchronicle.core.ChatMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.*;
import java.util.*;

public final class ChronicleOverlay {
    private static MessageView live;
    private static MessageMenu menu;
    private static long revision = -1;
    private static final ChatMotion.Tween visibility = new ChatMotion.Tween();
    private static int lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;
    private static java.lang.ref.WeakReference<ChatScreen> owner = new java.lang.ref.WeakReference<>(null);
    public static void open() {
        open(Minecraft.getInstance().gui.screen() instanceof ChatScreen chat ? chat : null);
    }
    public static void open(ChatScreen screen) {
        ChatChronicle.get().touch();
        if (screen != null && owner.get() == screen && live != null) return;
        owner = new java.lang.ref.WeakReference<>(screen);
        live = new MessageView(); live.refresh(); menu = null;
        revision = ChatChronicle.get().revision;
    }
    public static void renderHud(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof HistoryScreen) return;
        if (mc.gui.hud.isHidden() || mc.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) return;
        MessageView hud = HudCache.VIEW;
        if (HudCache.revision != ChatChronicle.get().revision || HudCache.presentation != ChatChronicle.get().presentationRevision) {
            hud.rows = new ArrayList<>(); long seq = 0;
            if (ChatChronicle.get().config.compactDuplicates) hud.rows.addAll(ChatChronicle.get().recentGroups);
            else for (var record : ChatChronicle.get().recent) hud.rows.add(new ArchiveStore.Row(--seq, record));
            hud.invalidate(); HudCache.revision = ChatChronicle.get().revision; HudCache.presentation = ChatChronicle.get().presentationRevision;
        }
        var config = ChatChronicle.get().config;
        layout(hud, graphics, false); hud.fadeAfterMillis = config.chatIdleSeconds * 1000L + config.chatFadeMillis; hud.emptyHint = false; hud.animateArrivals = true;
        hud.render(graphics, -1, -1);
    }
    private static final class HudCache { static final MessageView VIEW = new MessageView(); static long revision = -1, presentation = -1; }
    public static void renderChat(GuiGraphicsExtractor graphics, int x, int y, boolean suggestionsVisible) {
        if (live == null) open();
        var mod = ChatChronicle.get();
        if (x != lastMouseX || y != lastMouseY) { mod.touch(); lastMouseX = x; lastMouseY = y; }
        boolean active = !mod.config.fadeFocusedChat || live.scroll > 0 || menu != null || !live.selection.isEmpty()
                || System.nanoTime() - mod.lastActivityNanos < mod.config.chatIdleSeconds * 1_000_000_000L;
        live.viewOpacity = visibility.sample(active, System.nanoTime(), active ? mod.config.chatAppearMillis : mod.config.chatFadeMillis, mod.config.chatAnimations);
        live.animateArrivals = true;
        if (revision != ChatChronicle.get().revision && live.scroll == 0 && !live.loading) { live.refresh(); revision = ChatChronicle.get().revision; }
        layout(live, graphics, true);
        if (Minecraft.getInstance().options.chatVisibility().get() != net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) live.render(graphics, suggestionsVisible ? -1 : x, suggestionsVisible ? -1 : y);
        int right = graphics.guiWidth();
        graphics.fill(right - 222, 4, right - 4, 24, 0xDD182531);
        graphics.text(Minecraft.getInstance().font, Messages.text("nav.historySearch"), right - 216, 10, 0xFFB9DFEE);
        graphics.text(Minecraft.getInstance().font, Messages.text("nav.settings"), right - 76, 10, 0xFFB9DFEE);
        if (!ChatChronicle.get().archive.statusText().isEmpty()) graphics.text(Minecraft.getInstance().font, ChatChronicle.get().archive.statusText(), 4, 27, 0xFFFFBB88);
    }
    public static boolean hasMenu() { return menu != null; }
    public static void renderMenu(GuiGraphicsExtractor graphics, int x, int y) { if (menu != null) menu.render(graphics, x, y); }
    public static boolean scroll(double amount) { ChatChronicle.get().touch(); return menu != null ? menu.scroll(amount) : live != null && live.scroll(amount); }
    public static boolean click(MouseButtonEvent event) {
        if (live == null) return false;
        ChatChronicle.get().touch();
        var mc = Minecraft.getInstance();
        if (menu != null) return menu.click(event);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.y() >= 4 && event.y() <= 24 && event.x() >= mc.getWindow().getGuiScaledWidth() - 222) {
            if (event.x() >= mc.getWindow().getGuiScaledWidth() - 82) mc.gui.setScreen(new SettingsScreen(mc.gui.screen()));
            else mc.gui.setScreen(new HistoryScreen(mc.gui.screen()));
            return true;
        }
        var hit = live.hit(event.x(), event.y()); if (hit == null) return false;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.hasShiftDown()) { live.selection.toggle(hit.line().row()); return true; }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (!live.selection.isEmpty() && !live.selection.contains(hit.line().row())) live.selection.clear();
            if (!live.selection.isEmpty()) menu = new MessageMenu(hit, live, (int) event.x(), (int) event.y(), text -> {}, () -> menu = null, () -> showContext(hit.line().row()));
            else if (event.hasShiftDown() && ChatChronicle.get().config.shiftRightCopy) mc.keyboardHandler.setClipboard(hit.line().row().message().text());
            else if (event.hasControlDown() && ChatChronicle.get().config.ctrlRightContext) showContext(hit.line().row());
            else menu = new MessageMenu(hit, live, (int) event.x(), (int) event.y(), text -> {
                if (mc.gui.screen() instanceof ChatScreen chat) chat.insertText(text, true);
            }, () -> menu = null, () -> showContext(hit.line().row()));
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            live.selection.clear();
            Style style = MessageView.styleAt(hit.line().text(), live.textPosition(event.x()));
            if (style != null && style.getClickEvent() != null) HistoryScreen.clickStyle(style);
            return true;
        }
        return false;
    }
    private static void layout(MessageView view, GuiGraphicsExtractor graphics, boolean focused) {
        var options = Minecraft.getInstance().options;
        view.scale = Math.clamp(options.chatScale().get(), .1, 1);
        view.lineSpacing = options.chatLineSpacing().get();
        view.textOpacity = .1 + .9 * options.chatOpacity().get();
        view.backgroundOpacity = options.textBackgroundOpacity().get();
        var config = ChatChronicle.get().config;
        view.x = 2;
        int desiredWidth = config.chatHudWidth > 0 ? config.chatHudWidth : (int) Math.ceil(ChatComponent.getWidth(options.chatWidth().get()) * view.scale);
        view.width = Math.max(20, Math.min(graphics.guiWidth() - 4, desiredWidth));
        view.bottom = graphics.guiHeight() - 40;
        int customHeight = focused ? config.chatHudHeightFocused : config.chatHudHeightUnfocused;
        int desired = customHeight > 0 ? customHeight : (int) Math.ceil(ChatComponent.getHeight((focused ? options.chatHeightFocused() : options.chatHeightUnfocused()).get()) * view.scale);
        view.top = Math.max(focused ? 40 : 2, view.bottom - desired);
    }
    private static void showContext(ArchiveStore.Row row) {
        HistoryScreen history = new HistoryScreen(Minecraft.getInstance().gui.screen());
        Minecraft.getInstance().gui.setScreen(history); history.showContext(row);
    }
    public static boolean key(KeyEvent event) {
        ChatChronicle.get().touch();
        if (menu != null && event.isEscape()) { menu = null; return true; }
        if (live != null && !live.selection.isEmpty()) {
            if (event.isEscape()) { live.selection.clear(); return true; }
            if (event.isCopy()) { Minecraft.getInstance().keyboardHandler.setClipboard(MessageMenu.selectedText(live)); return true; }
            if (event.key() == InputConstants.KEY_DELETE) { MessageMenu.deleteSelected(live); return true; }
        }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_F) {
            Minecraft.getInstance().gui.setScreen(new HistoryScreen(Minecraft.getInstance().gui.screen())); return true;
        }
        if (event.key() == InputConstants.KEY_PAGEUP) return scroll(10);
        if (event.key() == InputConstants.KEY_PAGEDOWN) return scroll(-10);
        return false;
    }
}
