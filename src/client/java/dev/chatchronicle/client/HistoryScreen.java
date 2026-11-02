package dev.chatchronicle.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chatchronicle.client.mixin.ChatScreenAccess;

import dev.chatchronicle.core.Messages;

import dev.chatchronicle.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.*;
import java.time.*;
import java.util.*;

public final class HistoryScreen extends Screen {
    private final Screen parent;
    private final MessageView view = new MessageView();
    private List<ArchiveStore.Day> days = List.of();
    private int dayScroll;
    private EditBox search, from, to;
    private Checkbox regex, caseSensitive, wholeWord;
    private MessageMenu menu;
    private boolean calendar;
    private YearMonth month = YearMonth.now();
    private long changedAt;
    private boolean pendingSearch;
    private String error = "";
    private boolean initialized;
    private long seenPresentation = -1, seenMaintenance = -1;

    public HistoryScreen(Screen parent) { super(Component.literal(Messages.text("history.title"))); this.parent = parent; }
    @Override protected void init() {
        int left = Math.min(152, width / 3);
        String oldText = search == null ? "" : search.getValue();
        String oldFrom = from == null ? "" : from.getValue();
        String oldTo = to == null ? "" : to.getValue();
        boolean oldRegex = regex != null && regex.selected(), oldCase = caseSensitive != null && caseSensitive.selected(), oldWord = wholeWord != null && wholeWord.selected();
        search = addRenderableWidget(new EditBox(font, left, 26, Math.max(40, width - left - 92), 20, Component.literal(Messages.text("search.label"))));
        search.setMaxLength(2048); search.setHint(Component.literal(Messages.text("search.hint"))); search.setValue(oldText);
        search.setResponder(text -> scheduleSearch());
        button(Messages.text("nav.find"), width - 86, 26, 80, this::runSearch);
        from = addRenderableWidget(new EditBox(font, left, 53, 92, 18, Component.literal(Messages.text("search.fromLabel"))));
        to = addRenderableWidget(new EditBox(font, left + 100, 53, 92, 18, Component.literal(Messages.text("search.toLabel"))));
        from.setMaxLength(10); to.setMaxLength(10); from.setHint(Component.literal(Messages.text("search.fromHint"))); to.setHint(Component.literal(Messages.text("search.toHint")));
        from.setValue(oldFrom); to.setValue(oldTo);
        from.setResponder(text -> scheduleSearch()); to.setResponder(text -> scheduleSearch());
        regex = addRenderableWidget(Checkbox.builder(Component.literal(Messages.text("search.regex")), font).pos(left, 78).selected(oldRegex).onValueChange((box, value) -> scheduleSearch()).build());
        caseSensitive = addRenderableWidget(Checkbox.builder(Component.literal(Messages.text("search.case")), font).pos(left + 80, 78).selected(oldCase).onValueChange((box, value) -> scheduleSearch()).build());
        wholeWord = addRenderableWidget(Checkbox.builder(Component.literal(Messages.text("search.word")), font).pos(left + 172, 78).selected(oldWord).onValueChange((box, value) -> scheduleSearch()).build());
        regex.setTooltip(Tooltip.create(Component.literal(Messages.text("search.regexHelp"))));
        button(Messages.text("nav.allDays"), 6, 26, left - 16, () -> { from.setValue(""); to.setValue(""); view.day = null; runSearch(); });
        button(Messages.text("nav.calendar"), 6, 52, left - 16, () -> calendar = !calendar);
        int footerWidth = Math.max(30, (width - left - 18) / 4);
        button(Messages.text("nav.latest"), left, height - 27, footerWidth, () -> { view.searchMode = false; view.day = null; view.highlighted = null; view.refresh(); });
        button(Messages.text("nav.results"), left + footerWidth + 4, height - 27, footerWidth, this::runSearch);
        button(Messages.text("nav.settings"), left + (footerWidth + 4) * 2, height - 27, footerWidth, () -> minecraft.gui.setScreen(new SettingsScreen(this)));
        button(Messages.text("nav.back"), left + (footerWidth + 4) * 3, height - 27, footerWidth, this::onClose);
        view.x = left; view.top = 112; view.width = Math.max(50, width - left - 6); view.bottom = height - 34;
        if (!initialized) {
            initialized = true;
            view.refresh();
            refreshDays();
        } else if (seenPresentation != ChatChronicle.get().presentationRevision) { error = ""; view.reload(); refreshDays(); }
        seenPresentation = ChatChronicle.get().presentationRevision;
        seenMaintenance = ChatChronicle.get().archive.maintenanceRevision;
        setInitialFocus(search);
    }
    private void button(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, Math.max(30, w), 20).build());
    }
    private void scheduleSearch() { changedAt = System.currentTimeMillis(); pendingSearch = true; view.cancel(); }
    @Override public void tick() {
        if (seenMaintenance != ChatChronicle.get().archive.maintenanceRevision) {
            seenMaintenance = ChatChronicle.get().archive.maintenanceRevision;
            if (!pendingSearch) view.reload(); refreshDays();
        }
        if (pendingSearch && System.currentTimeMillis() - changedAt >= 300) runSearch();
    }
    private void refreshDays() {
        ChatChronicle.get().archive.days().whenComplete((result, ex) -> ChatChronicle.onClient(() -> {
            if (ex == null) days = result; else error = Messages.text("error.days");
        }));
    }
    private String date(EditBox box) { String text = box.getValue().trim(); if (text.isEmpty()) return null; return LocalDate.parse(text).toString(); }
    private void runSearch() {
        pendingSearch = false;
        try {
            String start = date(from), end = date(to);
            if (start != null && end != null && start.compareTo(end) > 0) throw new IllegalArgumentException(Messages.text("error.dateOrder"));
            SearchQuery query = new SearchQuery(search.getValue(), start, end, regex.selected(), caseSensitive.selected(), wholeWord.selected());
            query.compile(); error = ""; view.highlighted = null;
            view.selection.clear();
            view.searchMode = !query.text().isEmpty() || start != null || end != null;
            view.query = query; view.day = null; view.refresh();
        } catch (Exception ex) {
            error = ex instanceof java.time.format.DateTimeParseException ? Messages.text("error.date")
                    : ex instanceof com.google.re2j.PatternSyntaxException ? Messages.text("error.regex")
                    : Messages.text("error.searchPrefix") + ex.getMessage();
        }
    }
    private void selectDay(String day) {
        view.selection.clear();
        search.setValue(""); from.setValue(day); to.setValue(day); pendingSearch = false;
        view.day = day; view.searchMode = false; view.highlighted = null; view.refresh(); calendar = false; error = "";
    }
    public void showContext(ArchiveStore.Row row) { pendingSearch = false; view.selection.clear(); view.context(row); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xEE0C131C);
        graphics.text(font, Messages.text("history.heading"), 8, 8, 0xFFBDE0EF);
        graphics.text(font, Messages.text("history.days"), 8, 82, 0xFF95A9BA);
        int left = view.x;
        graphics.enableScissor(0, 100, left - 4, height - 34);
        for (int i = dayScroll, y = 104; i < days.size() && y < height - 34; i++, y += 25) {
            var day = days.get(i);
            if (Objects.equals(day.date(), view.day)) graphics.fill(4, y - 2, left - 8, y + 22, 0xFF263E50);
            graphics.text(font, day.date(), 8, y, 0xFFD6E5EF);
            graphics.text(font, Messages.text("history.messageCount", day.count()), 8, y + 10, 0xFF829AAA);
        }
        graphics.disableScissor();
        view.render(graphics, mouseX, mouseY);
        if (!error.isEmpty()) graphics.text(font, font.plainSubstrByWidth(error, width - left - 8), left, 101, 0xFFFF9999);
        else if (!ChatChronicle.get().archive.statusText().isEmpty()) graphics.text(font, font.plainSubstrByWidth(ChatChronicle.get().archive.statusText(), width - left - 8), left, 101, 0xFFFFCC88);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (calendar) renderCalendar(graphics, mouseX, mouseY);
        if (menu != null) menu.render(graphics, mouseX, mouseY);
    }
    private void renderCalendar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = Math.max(4, (width - 252) / 2), y = 72;
        graphics.nextStratum(); graphics.fill(x - 4, y - 4, x + 256, y + 188, 0xFF172735);
        graphics.outline(x - 4, y - 4, 260, 192, 0xFF6A9CB4);
        graphics.text(font, "‹   " + month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", Messages.locale())) + "   ›", x + 10, y + 5, 0xFFFFFFFF);
        String[] weekdays = {Messages.text("weekday.mon"), Messages.text("weekday.tue"), Messages.text("weekday.wed"), Messages.text("weekday.thu"), Messages.text("weekday.fri"), Messages.text("weekday.sat"), Messages.text("weekday.sun")};
        for (int i = 0; i < 7; i++) graphics.text(font, weekdays[i], x + i * 36 + 5, y + 27, 0xFF89A6BC);
        int first = month.atDay(1).getDayOfWeek().getValue() - 1;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            int slot = first + day - 1, dx = x + (slot % 7) * 36, dy = y + 44 + (slot / 7) * 23;
            String date = month.atDay(day).toString(); boolean has = days.stream().anyMatch(d -> d.date().equals(date));
            if (has) graphics.fill(dx, dy, dx + 32, dy + 20, 0xFF2C4F65);
            graphics.text(font, Integer.toString(day), dx + 5, dy + 5, has ? 0xFFFFFFFF : 0xFF697E8E);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu != null) return menu.click(event);
        if (calendar) {
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) { calendar = false; return true; }
            int x = Math.max(4, (width - 252) / 2), y = 72;
            if (event.y() >= y && event.y() < y + 23) {
                if (event.x() < x + 65) month = month.minusMonths(1);
                else if (event.x() > x + 190) month = month.plusMonths(1);
            } else if (event.x() >= x && event.x() < x + 252 && event.y() >= y + 44 && event.y() < y + 182) {
                int slot = ((int) event.y() - y - 44) / 23 * 7 + ((int) event.x() - x) / 36;
                int day = slot - month.atDay(1).getDayOfWeek().getValue() + 2;
                if (day >= 1 && day <= month.lengthOfMonth()) selectDay(month.atDay(day).toString());
            } else calendar = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() < view.x && event.y() >= 100 && event.y() < height - 34) {
            int i = dayScroll + ((int) event.y() - 102) / 25;
            if (i >= 0 && i < days.size()) selectDay(days.get(i).date());
            return true;
        }
        var hit = view.hit(event.x(), event.y());
        if (hit != null) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.hasShiftDown()) { view.selection.toggle(hit.line().row()); return true; }
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                if (!view.selection.isEmpty() && !view.selection.contains(hit.line().row())) view.selection.clear();
                if (!view.selection.isEmpty()) menu = new MessageMenu(hit, view, (int) event.x(), (int) event.y(), text -> {}, () -> menu = null, () -> showContext(hit.line().row()));
                else if (event.hasShiftDown() && ChatChronicle.get().config.shiftRightCopy) minecraft.keyboardHandler.setClipboard(hit.line().row().message().text());
                else if (event.hasControlDown() && ChatChronicle.get().config.ctrlRightContext) showContext(hit.line().row());
                else menu = new MessageMenu(hit, view, (int) event.x(), (int) event.y(), text -> minecraft.gui.setScreen(new ChatScreen(text, false)), () -> menu = null, () -> showContext(hit.line().row()));
            } else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && view.searchMode) showContext(hit.line().row());
            else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                view.selection.clear();
                Style style = view.clickableStyle(hit, event.x(), event.y());
                if (style != null) clickStyle(style);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    public static boolean clickStyle(Style style) {
        if (style == null || style.getClickEvent() == null) return false;
        var client = Minecraft.getInstance(); var screen = client.gui.screen();
        if (screen instanceof ChatScreen chat) return ((ChatScreenAccess) chat).chronicle$handleComponentClicked(style, false);
        var click = style.getClickEvent();
        if (click instanceof ClickEvent.SuggestCommand suggestion) {
            client.gui.setScreen(new ChatScreen(suggestion.command(), false));
            return true;
        }
        if (client.player != null) defaultHandleGameClickEvent(click, client, screen);
        else if (click instanceof ClickEvent.OpenUrl || click instanceof ClickEvent.OpenFile || click instanceof ClickEvent.CopyToClipboard) defaultHandleClickEvent(click, client, screen);
        else return false;
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menu != null) return menu.scroll(vertical);
        if (x < view.x) dayScroll = Math.clamp(dayScroll - (int) vertical * 3, 0, Math.max(0, days.size() - 1));
        else view.scroll(vertical);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && menu != null) { menu = null; return true; }
        if (event.isEscape() && calendar) { calendar = false; return true; }
        if (!view.selection.isEmpty()) {
            if (event.isEscape()) { view.selection.clear(); return true; }
            if (event.isCopy()) { minecraft.keyboardHandler.setClipboard(MessageMenu.selectedText(view)); return true; }
            if (event.key() == InputConstants.KEY_DELETE) { MessageMenu.deleteSelected(view); return true; }
        }
        if (event.key() == InputConstants.KEY_RETURN) { runSearch(); return true; }
        if (event.key() == InputConstants.KEY_PAGEUP) return view.scroll(10);
        if (event.key() == InputConstants.KEY_PAGEDOWN) return view.scroll(-10);
        return super.keyPressed(event);
    }
    @Override public void onClose() { view.cancel(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public Component getTitle() { return Component.literal(Messages.text("history.title")); }
    @Override public Component getNarrationMessage() { return getTitle(); }
}
