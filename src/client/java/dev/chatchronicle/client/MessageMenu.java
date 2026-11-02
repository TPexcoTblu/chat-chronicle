package dev.chatchronicle.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.chatchronicle.core.Messages;

import dev.chatchronicle.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class MessageMenu {
    private record Item(String label, Runnable action, List<Item> children) {}
    private List<Item> items;
    private final Deque<List<Item>> parents = new ArrayDeque<>();
    private int x, y;
    private int offset;
    private String builtLanguage;
    private final int width = 224;
    private final ArchiveStore.Row row;
    private final long count;
    private final MessageView view;
    private final Consumer<String> reply;
    private final Runnable close;
    private final Runnable context;
    public MessageMenu(MessageView.Hit hit, MessageView view, int x, int y, Consumer<String> reply, Runnable close, Runnable context) {
        this.row = hit.line().row(); this.count = hit.line().count(); this.view = view;
        this.x = x; this.y = y; this.reply = reply; this.close = close; this.context = context;
        view.highlighted = row.message().id();
        build();
    }
    private Item action(String label, Runnable run) { return new Item(label, run, null); }
    private Item copy(String label, String value) { return action(label, () -> { Minecraft.getInstance().keyboardHandler.setClipboard(value); close.run(); }); }
    private Item submenu(String label, List<Item> children) { return new Item(label + "  ›", null, children); }
    private String time() {
        return DateTimeFormatter.ofPattern(ChatChronicle.get().config.timestampPattern, Messages.locale()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(row.message().time()));
    }
    private String fullTime() {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z", Messages.locale()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(row.message().time()));
    }
    private void build() {
        builtLanguage = Messages.language();
        if (!view.selection.isEmpty() && view.selection.contains(row)) { buildSelection(); return; }
        ChatRecord message = row.message();
        String visibleText = ChatChronicle.decode(message).getString();
        String counter = count > 1 ? " ×" + count : "";
        String prefix = ChatChronicle.get().config.showTimestamps ? "[" + time() + "] " : "";
        List<Item> text = List.of(
                copy(Messages.text("menu.plain"), prefix + visibleText + counter),
                copy(Messages.text("menu.formatted"), prefix + formatted(ChatChronicle.decode(message)) + counter),
                copy(Messages.text("menu.noTime"), visibleText + counter),
                copy(Messages.text("menu.noCounter"), prefix + visibleText),
                copy(Messages.text("menu.json"), message.componentJson()));
        List<Item> links = new ArrayList<>(); LinkedHashSet<String> urls = new LinkedHashSet<>();
        ChatChronicle.decode(message).visit((style, part) -> {
            if (style.getClickEvent() instanceof ClickEvent.OpenUrl url) urls.add(url.uri().toString());
            return Optional.empty();
        }, Style.EMPTY);
        var matcher = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(message.text());
        while (matcher.find()) urls.add(matcher.group());
        for (String url : urls) links.add(copy(url, url));
        if (links.isEmpty()) links.add(action(Messages.text("menu.noLinks"), () -> {}));
        items = new ArrayList<>(List.of(
                submenu(Messages.text("menu.copyText"), text),
                submenu(Messages.text("menu.copyLinks"), links),
                submenu(Messages.text("menu.copyTime"), List.of(copy(Messages.text("menu.time"), time()), copy(Messages.text("menu.fullDate"), fullTime()), copy(Messages.text("menu.unix"), Long.toString(message.time() / 1000)))),
                submenu(Messages.text("menu.counter"), List.of(copy(Messages.text("menu.counterText"), counter), copy(Messages.text("menu.counterValue"), Long.toString(count)))),
                submenu(Messages.text("menu.sender"), List.of(message.sender() == null ? action(Messages.text("menu.noName"), () -> {}) : copy(Messages.text("menu.name"), message.sender()),
                        message.senderUuid() == null ? action(Messages.text("menu.noUuid"), () -> {}) : copy(Messages.text("menu.uuid"), message.senderUuid()))),
                action(message.sender() == null ? Messages.text("menu.replyUnknown") : Messages.text("menu.reply"), () -> {
                    if (message.sender() != null) { reply.accept(ChatChronicle.get().config.replyTemplate.replace("$", message.sender())); close.run(); }
                }),
                action(Messages.text("menu.context"), () -> { close.run(); context.run(); }),
                action(Messages.text("menu.hide"), () -> { ChatChronicle.get().hidden.add(message.id()); ChatChronicle.get().presentationRevision++; view.invalidate(); close.run(); }),
                action(Messages.text("menu.delete"), this::confirmDelete),
                action(Messages.text("nav.close"), close)));
    }
    public static String selectedText(MessageView view) {
        var formatter = DateTimeFormatter.ofPattern(ChatChronicle.get().config.timestampPattern, Messages.locale()).withZone(ZoneId.systemDefault());
        return view.selection.snapshot().stream().map(r -> {
            String prefix = ChatChronicle.get().config.showTimestamps ? "[" + formatter.format(Instant.ofEpochMilli(r.message().time())) + "] " : "";
            return prefix + ChatChronicle.decode(r.message()).getString() + (r.repeatCount() > 1 ? " ×" + r.repeatCount() : "");
        }).collect(java.util.stream.Collectors.joining("\n"));
    }
    private void buildSelection() {
        List<ArchiveStore.Row> rows = view.selection.snapshot();
        String plain = rows.stream().map(r -> r.message().text()).collect(java.util.stream.Collectors.joining("\n"));
        String formatted = rows.stream().map(r -> formatted(ChatChronicle.decode(r.message()))).collect(java.util.stream.Collectors.joining("\n"));
        String json = rows.stream().map(r -> r.message().componentJson()).collect(java.util.stream.Collectors.joining("\n"));
        LinkedHashSet<String> links = new LinkedHashSet<>();
        for (var selected : rows) {
            ChatChronicle.decode(selected.message()).visit((style, text) -> {
                if (style.getClickEvent() instanceof ClickEvent.OpenUrl url) links.add(url.uri().toString());
                return Optional.empty();
            }, Style.EMPTY);
            var matcher = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(selected.message().text());
            while (matcher.find()) links.add(matcher.group());
        }
        items = new ArrayList<>(List.of(
                action(Messages.text("selection.count", rows.size()), () -> {}),
                submenu(Messages.text("selection.copy"), List.of(copy(Messages.text("selection.asShown"), selectedText(view)), copy(Messages.text("selection.originalText"), plain),
                        copy(Messages.text("menu.formatted"), formatted), copy(Messages.text("selection.json"), json), copy(Messages.text("selection.links"), String.join("\n", links)))),
                action(Messages.text("selection.hide"), () -> {
                    var selected = view.selection;
                    for (var visible : view.rows) if (selected.contains(visible)) ChatChronicle.get().hidden.add(visible.message().id());
                    rows.forEach(r -> ChatChronicle.get().hidden.add(r.message().id()));
                    view.selection.clear(); ChatChronicle.get().presentationRevision++; view.invalidate(); close.run();
                }),
                action(Messages.text("selection.delete"), () -> { close.run(); deleteSelected(view); }),
                action(Messages.text("selection.clear"), () -> { view.selection.clear(); close.run(); }),
                action(Messages.text("nav.close"), close)));
    }
    public static void deleteSelected(MessageView view) {
        List<ArchiveStore.Row> snapshot = view.selection.snapshot();
        if (snapshot.isEmpty()) return;
        long originals = snapshot.stream().mapToLong(ArchiveStore.Row::repeatCount).sum();
        var mc = Minecraft.getInstance(); Screen parent = mc.gui.screen();
        mc.gui.setScreen(new ModConfirmScreen(yes -> {
            mc.gui.setScreen(parent);
            if (yes) {
                view.notice = new UiMessage("selection.deleting");
                ChatChronicle.get().archive.deleteSelected(snapshot).whenComplete((removed, error) -> ChatChronicle.onClient(() -> {
                    if (error == null) {
                        MessageSelection selected = new MessageSelection(); snapshot.forEach(selected::toggle);
                        view.rows.removeIf(selected::contains); view.selection.clear(); view.invalidate();
                        ChatChronicle.get().presentationRevision++; ChatChronicle.get().revision++;
                        view.notice = new UiMessage("selection.deleted", removed);
                    } else view.notice = new UiMessage("selection.deleteError", error.getMessage());
                }));
            }
        }, Component.literal(Messages.text("selection.confirmTitle")), Component.literal(Messages.text("selection.confirmBody", snapshot.size(), originals))));
    }
    private static String formatted(Component component) {
        StringBuilder out = new StringBuilder();
        component.visit((style, text) -> {
            out.append("§r");
            if (style.getColor() != null) {
                String hex = String.format("%06x", style.getColor().getValue()); out.append("§x");
                for (char c : hex.toCharArray()) out.append('§').append(c);
            }
            if (style.isBold()) out.append("§l"); if (style.isItalic()) out.append("§o");
            if (style.isUnderlined()) out.append("§n"); if (style.isStrikethrough()) out.append("§m");
            if (style.isObfuscated()) out.append("§k"); out.append(text); return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }
    private void confirmDelete() {
        var mc = Minecraft.getInstance(); Screen parent = mc.gui.screen(); close.run();
        mc.gui.setScreen(new ModConfirmScreen(yes -> {
            mc.gui.setScreen(parent);
            if (yes) ChatChronicle.get().archive.delete(row).whenComplete((unused, error) -> ChatChronicle.onClient(() -> {
                if (error == null) {
                    ChatChronicle.get().hidden.add(row.message().id()); ChatChronicle.get().presentationRevision++; ChatChronicle.get().revision++;
                    view.rows.removeIf(r -> r.message().id().equals(row.message().id()));
                    ChatChronicle.get().recent.removeIf(r -> r.id().equals(row.message().id()));
                    ChatChronicle.get().recentGroups.removeIf(r -> r.message().id().equals(row.message().id()));
                    view.invalidate();
                } else view.notice = new UiMessage("delete.error");
            }));
        }, Component.literal(Messages.text("delete.confirmTitle")), Component.literal(Messages.text("delete.confirmBody"))));
    }
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!Messages.language().equals(builtLanguage)) { parents.clear(); offset = 0; build(); }
        int visible = Math.min(items.size(), Math.max(1, (graphics.guiHeight() - 8) / 18));
        x = Math.clamp(x, 4, Math.max(4, graphics.guiWidth() - width - 4));
        y = Math.clamp(y, 4, Math.max(4, graphics.guiHeight() - visible * 18 - 4));
        graphics.nextStratum(); graphics.fill(x - 2, y - 2, x + width + 2, y + visible * 18 + 2, 0xFF101923);
        graphics.outline(x - 2, y - 2, width + 4, visible * 18 + 4, 0xFF557C9B);
        offset = Math.clamp(offset, 0, Math.max(0, items.size() - visible));
        for (int i = 0; i < visible; i++) {
            int top = y + i * 18;
            if (mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + 18) graphics.fill(x, top, x + width, top + 18, 0xFF304C63);
            graphics.text(Minecraft.getInstance().font, Minecraft.getInstance().font.plainSubstrByWidth(items.get(i + offset).label(), width - 8), x + 4, top + 5, 0xFFFFFFFF);
        }
    }
    public boolean click(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) close.run();
            return true;
        }
        int i = offset + (int) (event.y() - y) / 18;
        if (event.x() < x || event.x() >= x + width || event.y() < y || i >= items.size()) { close.run(); return true; }
        Item item = items.get(i);
        if (item.children() != null) {
            offset = 0;
            parents.push(items); items = new ArrayList<>();
            items.add(action(Messages.text("menu.back"), () -> items = parents.pop())); items.addAll(item.children());
        } else item.action().run();
        return true;
    }
    public boolean scroll(double amount) { offset = Math.clamp(offset - (int) Math.signum(amount), 0, Math.max(0, items.size() - 1)); return true; }
}
