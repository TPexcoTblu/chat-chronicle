package dev.chatchronicle.client;

import dev.chatchronicle.core.Messages;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import dev.chatchronicle.core.RetentionPolicy;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.time.format.DateTimeFormatter;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private EditBox interval, pattern, reply, cache, page, days, maximum, hudWidth, hudOpenHeight, hudClosedHeight;
    private EditBox appear, idle, fade, counterColor;
    private Button counterPreview;
    private static final int[] COUNTER_COLORS = {0xAAAAAA, 0xFFFFFF, 0xFFFF55, 0xFFAA00, 0xFF5555, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF};
    private final java.util.Map<EditBox, String> fieldLabels = new java.util.LinkedHashMap<>();
    private String error = "";
    private int scroll;
    private final java.util.List<net.minecraft.client.gui.components.AbstractWidget> content = new java.util.ArrayList<>();
    private final java.util.List<Integer> positions = new java.util.ArrayList<>();
    private Button saveButton, backButton;
    private java.util.List<String> draftValues = java.util.List.of();
    private int nextField;
    public SettingsScreen(Screen parent) { super(Component.literal(Messages.text("settings.title"))); this.parent = parent; }
    @Override protected void init() {
        draftValues = fieldLabels.keySet().stream().map(EditBox::getValue).toList(); nextField = 0;
        content.clear(); positions.clear(); fieldLabels.clear();
        var c = ChatChronicle.get().config;
        int x = Math.max(8, width / 2 - 150), y = 58;
        var language = addRenderableWidget(Button.builder(Component.literal(Messages.text("language.button", Messages.text("ru_ru".equals(c.language) ? "language.russian" : "language.english"))), b -> {
            c.language = "ru_ru".equals(c.language) ? "en_us" : "ru_ru";
            Messages.setLanguage(c.language); error = ""; ChatChronicle.get().saveConfig(); rebuildWidgets();
        }).bounds(x, 30, 300, 20).build());
        content.add(language); positions.add(30);
        check(Messages.text("settings.compact"), x, y, c.compactDuplicates, v -> c.compactDuplicates = v);
        check(Messages.text("settings.time"), x, y + 24, c.showTimestamps, v -> c.showTimestamps = v);
        check(Messages.text("settings.shiftCopy"), x, y + 48, c.shiftRightCopy, v -> c.shiftRightCopy = v);
        check(Messages.text("settings.ctrlContext"), x, y + 72, c.ctrlRightContext, v -> c.ctrlRightContext = v);
        interval = field(Messages.text("settings.saveInterval"), x, y + 104, Integer.toString(c.saveIntervalSeconds));
        pattern = field(Messages.text("settings.timeFormat"), x, y + 132, c.timestampPattern);
        reply = field(Messages.text("settings.reply"), x, y + 160, c.replyTemplate);
        cache = field(Messages.text("settings.cache"), x, y + 188, Integer.toString(c.cacheMessages));
        page = field(Messages.text("settings.page"), x, y + 216, Integer.toString(c.pageSize));
        days = field(Messages.text("settings.days"), x, y + 244, Integer.toString(c.retentionDays));
        maximum = field(Messages.text("settings.maximum"), x, y + 272, Long.toString(c.maxArchiveMessages));
        hudWidth = field(Messages.text("settings.hudWidth"), x, y + 300, Integer.toString(c.chatHudWidth));
        hudOpenHeight = field(Messages.text("settings.hudOpen"), x, y + 328, Integer.toString(c.chatHudHeightFocused));
        hudClosedHeight = field(Messages.text("settings.hudClosed"), x, y + 356, Integer.toString(c.chatHudHeightUnfocused));
        Tooltip hudHelp = Tooltip.create(Component.literal(Messages.text("settings.hudHelp")));
        hudWidth.setTooltip(hudHelp); hudOpenHeight.setTooltip(hudHelp); hudClosedHeight.setTooltip(hudHelp);
        check(Messages.text("settings.animations"), x, y + 384, c.chatAnimations, v -> c.chatAnimations = v);
        check(Messages.text("settings.fadeOpen"), x, y + 408, c.fadeFocusedChat, v -> c.fadeFocusedChat = v);
        appear = field(Messages.text("settings.appear"), x, y + 436, Integer.toString(c.chatAppearMillis));
        idle = field(Messages.text("settings.idle"), x, y + 464, Integer.toString(c.chatIdleSeconds));
        fade = field(Messages.text("settings.fade"), x, y + 492, Integer.toString(c.chatFadeMillis));
        check(Messages.text("settings.markers"), x, y + 520, c.showSessionMarkers, v -> c.showSessionMarkers = v);
        counterColor = field(Messages.text("settings.counterColor"), x, y + 548, String.format("#%06X", c.duplicateCounterColor));
        counterColor.setTooltip(Tooltip.create(Component.literal(Messages.text("settings.counterColorHelp"))));
        counterPreview = addRenderableWidget(Button.builder(Component.empty(), b -> {
            int current;
            try { current = parseCounterColor(); } catch (IllegalArgumentException ex) { current = c.duplicateCounterColor; }
            int next = 0;
            for (int i = 0; i < COUNTER_COLORS.length; i++) if (COUNTER_COLORS[i] == current) { next = (i + 1) % COUNTER_COLORS.length; break; }
            counterColor.setValue(String.format("#%06X", COUNTER_COLORS[next]));
        }).bounds(x, y + 576, 300, 20).build());
        content.add(counterPreview); positions.add(y + 576);
        counterColor.setResponder(value -> updateCounterPreview()); updateCounterPreview();
        var compact = addRenderableWidget(Button.builder(Component.literal(Messages.text("settings.compactFiles")), b -> compact()).bounds(x, y + 604, 300, 20).build());
        content.add(compact); positions.add(y + 604);
        saveButton = addRenderableWidget(Button.builder(Component.literal(Messages.text("nav.save")), b -> save()).bounds(x, height - 27, 145, 20).build());
        backButton = addRenderableWidget(Button.builder(Component.literal(Messages.text("nav.back")), b -> onClose()).bounds(x + 155, height - 27, 145, 20).build());
        positionContent();
    }
    private void check(String label, int x, int y, boolean selected, java.util.function.Consumer<Boolean> setter) {
        var box = addRenderableWidget(Checkbox.builder(Component.literal(label), font).pos(x, y).selected(selected).onValueChange((widget, v) -> setter.accept(v)).build());
        content.add(box); positions.add(y);
    }
    private EditBox field(String label, int x, int y, String value) {
        EditBox box = addRenderableWidget(new EditBox(font, x + 190, y, 110, 20, Component.literal(label)));
        box.setMaxLength(128); box.setValue(nextField < draftValues.size() ? draftValues.get(nextField) : value); nextField++;
        content.add(box); positions.add(y); fieldLabels.put(box, label); return box;
    }
    private void save() {
        try {
            int seconds = Integer.parseInt(interval.getValue()), messages = Integer.parseInt(cache.getValue()), size = Integer.parseInt(page.getValue());
            if (seconds < 1 || seconds > 60 || messages < 100 || messages > 5000 || size < 20 || size > 500 || messages < size * 2) throw new IllegalArgumentException(Messages.text("settings.validateCache"));
            try { DateTimeFormatter.ofPattern(pattern.getValue()); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException(Messages.text("settings.validateTimePattern")); }
            var mod = ChatChronicle.get();
            RetentionPolicy policy = new RetentionPolicy(Integer.parseInt(days.getValue()), Long.parseLong(maximum.getValue()));
            int width = dimension(hudWidth), openHeight = dimension(hudOpenHeight), closedHeight = dimension(hudClosedHeight);
            int appearMs = Integer.parseInt(appear.getValue()), idleSeconds = Integer.parseInt(idle.getValue()), fadeMs = Integer.parseInt(fade.getValue());
            if (appearMs < 0 || appearMs > 2000 || idleSeconds < 1 || idleSeconds > 600 || fadeMs < 0 || fadeMs > 10000) throw new IllegalArgumentException(Messages.text("settings.validateMotion"));
            String newPattern = pattern.getValue(), newReply = reply.getValue();
            int newCounterColor = parseCounterColor();
            Runnable apply = () -> {
                mod.config.saveIntervalSeconds = seconds; mod.config.cacheMessages = messages; mod.config.pageSize = size;
                mod.config.timestampPattern = newPattern; mod.config.replyTemplate = newReply;
                mod.config.duplicateCounterColor = newCounterColor;
                mod.config.retentionDays = policy.days(); mod.config.maxArchiveMessages = policy.messages();
                mod.config.chatHudWidth = width; mod.config.chatHudHeightFocused = openHeight; mod.config.chatHudHeightUnfocused = closedHeight;
                mod.config.chatAppearMillis = appearMs; mod.config.chatIdleSeconds = idleSeconds; mod.config.chatFadeMillis = fadeMs;
                mod.archive.setFlushInterval(seconds); mod.saveConfig();
                error = Messages.text("settings.applying");
                mod.archive.configureRetention(policy).whenComplete((result, ex) -> ChatChronicle.onClient(() -> {
                    if (ex == null) onClose(); else error = Messages.text("settings.retentionError") + ex.getMessage();
                }));
            };
            if (policy.enabled() && (policy.days() != mod.config.retentionDays || policy.messages() != mod.config.maxArchiveMessages)) {
                minecraft.gui.setScreen(new ModConfirmScreen(yes -> {
                    minecraft.gui.setScreen(this); if (yes) apply.run();
                }, Component.literal(Messages.text("retention.title")), Component.literal(Messages.text("retention.body", policy.days(), policy.messages()))));
            } else apply.run();
        } catch (Exception ex) { error = ex instanceof NumberFormatException ? Messages.text("error.number") : ex.getMessage(); }
    }
    private int dimension(EditBox field) {
        int value = Integer.parseInt(field.getValue().trim());
        if (value != 0 && (value < 20 || value > 8192)) throw new IllegalArgumentException(Messages.text("settings.validateHud"));
        return value;
    }
    private int parseCounterColor() {
        String value = counterColor.getValue().trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException(Messages.text("settings.validateCounterColor"));
        return Integer.parseInt(value, 16);
    }
    private void updateCounterPreview() {
        try {
            counterPreview.setMessage(Component.literal(Messages.text("settings.counterColorPreview") + " ×3")
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(parseCounterColor())));
        } catch (IllegalArgumentException ex) {
            counterPreview.setMessage(Component.literal(Messages.text("settings.validateCounterColor")));
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF00D1721);
        graphics.centeredText(font, getTitle(), width / 2, 9, 0xFFE1EDF4);
        graphics.enableScissor(0, 26, width, height - 66);
        int x = Math.max(8, width / 2 - 150);
        fieldLabels.forEach((field, label) -> graphics.text(font, font.plainSubstrByWidth(label, 184), x, field.getY() + 5, 0xFFB6CEDD));
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.disableScissor();
        graphics.text(font, Messages.text("settings.zeroHint"), x, height - 54, 0xFF90A9B8);
        if (!error.isEmpty()) graphics.text(font, font.plainSubstrByWidth(error, width - 16), 8, height - 40, 0xFFFF9999);
        saveButton.extractRenderState(graphics, mouseX, mouseY, delta);
        backButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    private void positionContent() {
        int end = 0;
        for (int i = 0; i < content.size(); i++) end = Math.max(end, positions.get(i) + content.get(i).getHeight());
        scroll = Math.clamp(scroll, 0, Math.max(0, end - (height - 66)));
        for (int i = 0; i < content.size(); i++) {
            var widget = content.get(i); widget.setY(positions.get(i) - scroll);
            widget.visible = widget.getY() + widget.getHeight() > 26 && widget.getY() < height - 66;
        }
    }
    private void compact() {
        minecraft.gui.setScreen(new ModConfirmScreen(yes -> {
            minecraft.gui.setScreen(this);
            if (yes) {
                error = Messages.text("compact.working");
                ChatChronicle.get().archive.compactArchive().whenComplete((count, ex) -> ChatChronicle.onClient(() ->
                        error = ex == null ? Messages.text("compact.done", count) : Messages.text("compact.error", ex.getMessage())));
            }
        }, Component.literal(Messages.text("compact.title")), Component.literal(Messages.text("compact.body"))));
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll -= (int) vertical * 22; positionContent(); return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.y() >= height - 30) return saveButton.mouseClicked(event, doubleClick) || backButton.mouseClicked(event, doubleClick);
        if (event.y() < 26 || event.y() >= height - 66) return false;
        return super.mouseClicked(event, doubleClick);
    }
    @Override public void onClose() { ChatChronicle.get().saveConfig(); minecraft.gui.setScreen(parent); }
    @Override public Component getTitle() { return Component.literal(Messages.text("settings.title")); }
    @Override public Component getNarrationMessage() { return getTitle(); }
    @Override public boolean isPauseScreen() { return false; }
}
