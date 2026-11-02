package dev.chatchronicle.client;

import dev.chatchronicle.core.Messages;

import dev.chatchronicle.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded, wrapped line cache. Each line retains the identity of its source record. */
public final class MessageView {
    public List<ArchiveStore.Row> rows = new ArrayList<>();
    public int scroll;
    public boolean loading;
    public boolean more = true;
    public boolean searchMode;
    public String day;
    public String highlighted;
    public UiMessage notice = UiMessage.EMPTY;
    public SearchQuery query;
    public boolean expandedContext;
    public final MessageSelection selection = new MessageSelection();
    public AtomicBoolean cancelled = new AtomicBoolean();
    public int x, top, width, bottom;
    public double scale = 1, lineSpacing, textOpacity = 1, backgroundOpacity = .5;
    public long fadeAfterMillis;
    public boolean emptyHint = true;
    public boolean animateArrivals;
    public double viewOpacity = 1;
    private int cachedWidth = -1;
    private double cachedScale = -1;
    private boolean layoutDirty = true;
    private final List<Line> lines = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private int request;
    private boolean newerAvailable;
    private long presentationRevision = -1;
    public record Line(ArchiveStore.Row row, FormattedCharSequence text, long count, boolean timestamp) {}
    public record Hit(Line line, int y, double originX) {}

    public void refresh() { expandedContext = false; load(true); }
    public void reload() {
        if (expandedContext && highlighted != null) {
            for (var row : rows) if (row.message().id().equals(highlighted)) { context(row); return; }
        }
        refresh();
    }
    public void invalidate() { layoutDirty = true; }
    public void cancel() { cancelled.set(true); request++; loading = false; }
    public void load(boolean reset) {
        if (loading && !reset) return;
        ChatChronicle mod = ChatChronicle.get();
        cancel(); cancelled = new AtomicBoolean(); int token = ++request;
        loading = true; notice = rows.isEmpty() ? new UiMessage("view.loading") : UiMessage.EMPTY;
        long before = reset || rows.isEmpty() ? Long.MAX_VALUE : rows.getLast().firstSequence();
        var future = searchMode ? mod.archive.search(query, before, mod.config.pageSize, cancelled)
                : compactArchive() ? mod.archive.groupedPage(day, before, mod.config.pageSize)
                : mod.archive.page(day, before, mod.config.pageSize);
        future.whenComplete((page, error) -> ChatChronicle.onClient(() -> {
            if (token != request) return;
            loading = false;
            if (error != null) { notice = new UiMessage("error.detail", rootCause(error).getMessage()); return; }
            notice = UiMessage.EMPTY; more = page.more();
            if (reset) { rows = new ArrayList<>(page.rows()); scroll = 0; newerAvailable = false; }
            else {
                rows.addAll(page.rows());
                if (rows.size() > mod.config.cacheMessages) {
                    int drop = rows.size() - mod.config.cacheMessages;
                    Set<String> ids = new HashSet<>();
                    rows.subList(0, drop).forEach(row -> ids.add(row.message().id()));
                    int removedLines = (int) lines.stream().filter(line -> line.row() != null && ids.contains(line.row().message().id())).count();
                    rows.subList(0, drop).clear(); scroll = Math.max(0, scroll - removedLines); newerAvailable = true;
                }
            }
            invalidate();
        }));
    }
    private static Throwable rootCause(Throwable t) { while (t.getCause() != null) t = t.getCause(); return t; }
    public void context(ArchiveStore.Row row) {
        cancel(); searchMode = false; expandedContext = true; day = null; loading = true; notice = new UiMessage("view.contextLoading");
        int token = ++request;
        ChatChronicle.get().archive.around(row.sequence(), 60).whenComplete((result, error) -> ChatChronicle.onClient(() -> {
            if (token != request) return;
            loading = false;
            if (error != null) { notice = new UiMessage("error.detail", rootCause(error).getMessage()); return; }
            rows = new ArrayList<>(result); more = true; newerAvailable = true;
            highlighted = row.message().id(); notice = new UiMessage("view.context", row.message().day()); invalidate();
            rebuild();
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).row() != null && lines.get(i).row().message().id().equals(highlighted)) {
                scroll = Math.max(0, i - Math.max(2, (bottom - top) / (lineHeight() * 2))); break;
            }
        }));
    }
    public boolean scroll(double amount) {
        rebuild();
        int visible = Math.max(1, (bottom - top) / lineHeight());
        scroll = Math.clamp(scroll + (int) Math.signum(amount) * Math.max(1, (int) Math.abs(amount) * 3), 0, Math.max(0, lines.size() - visible));
        if (amount > 0 && scroll + visible + 12 >= lines.size() && more && !loading) load(false);
        if (amount < 0 && scroll == 0 && newerAvailable && !searchMode) loadNewer();
        return true;
    }
    private void loadNewer() {
        if (loading || rows.isEmpty()) return;
        loading = true; int token = ++request;
        ChatChronicle mod = ChatChronicle.get();
        var future = compactArchive() ? mod.archive.groupedAfter(day, rows.getFirst().lastSequence(), mod.config.pageSize)
                : mod.archive.after(day, rows.getFirst().sequence(), mod.config.pageSize);
        future.whenComplete((result, error) -> ChatChronicle.onClient(() -> {
            if (token != request) return;
            loading = false;
            if (error != null) { notice = new UiMessage("error.detail", rootCause(error).getMessage()); return; }
            newerAvailable = result.more();
            if (!result.rows().isEmpty()) {
                List<ArchiveStore.Row> next = new ArrayList<>(result.rows()); next.addAll(rows); rows = next;
                if (rows.size() > mod.config.cacheMessages) { rows.subList(mod.config.cacheMessages, rows.size()).clear(); more = true; }
                invalidate(); scroll = 0;
            }
        }));
    }
    private boolean compactArchive() { return ChatChronicle.get().config.compactDuplicates && !expandedContext; }
    private void rebuild() {
        if (presentationRevision != ChatChronicle.get().presentationRevision) { layoutDirty = true; presentationRevision = ChatChronicle.get().presentationRevision; }
        if (!layoutDirty && cachedWidth == width && cachedScale == scale) return;
        lines.clear(); layoutDirty = false; cachedWidth = width; cachedScale = scale;
        var font = Minecraft.getInstance().font; var mod = ChatChronicle.get();
        DateTimeFormatter formatter;
        try { formatter = DateTimeFormatter.ofPattern(mod.config.timestampPattern, Messages.locale()).withZone(ZoneId.systemDefault()); }
        catch (RuntimeException ex) { formatter = DateTimeFormatter.ofPattern("HH:mm:ss", Messages.locale()).withZone(ZoneId.systemDefault()); }
        String previousDay = null;
        for (int i = 0; i < rows.size(); i++) {
            ArchiveStore.Row row = rows.get(i); ChatRecord record = row.message();
            if (mod.hidden.contains(record.id())) continue;
            long count = row.repeatCount();
            if (mod.config.compactDuplicates && !searchMode && !expandedContext && row.sequence() < 0) {
                while (i + 1 < rows.size()) {
                    var older = rows.get(i + 1).message();
                    if (!record.sameContent(older) || mod.hidden.contains(older.id()) ||
                            Objects.equals(older.id(), highlighted) || Objects.equals(record.id(), highlighted)) break;
                    count++; i++;
                }
            }
            if (previousDay != null && !previousDay.equals(record.day())) lines.add(new Line(null, Component.literal("── " + previousDay + " ──").getVisualOrderText(), 0, false));
            previousDay = record.day();
            var component = Component.empty();
            if (searchMode) component.append(Component.literal(record.day() + " ").withStyle(net.minecraft.ChatFormatting.GRAY));
            if (mod.config.showTimestamps) component.append(Component.literal("[" + formatter.format(Instant.ofEpochMilli(record.time())) + "] ").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            component.append(ChatChronicle.decode(record));
            if (count > 1) component.append(Component.literal(" ×" + count).withStyle(net.minecraft.ChatFormatting.GRAY));
            var wrapped = font.split(component, Math.max(20, (int) (width / scale) - 10));
            for (int j = wrapped.size() - 1; j >= 0; j--) lines.add(new Line(row, wrapped.get(j), count, j == 0));
        }
        if (previousDay != null) lines.add(new Line(null, Component.literal("── " + previousDay + " ──").getVisualOrderText(), 0, false));
    }
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        rebuild(); hits.clear();
        graphics.enableScissor(x, top, x + width, bottom);
        int step = lineHeight(), y = bottom - step;
        for (int i = scroll; i < lines.size() && y >= top; i++) {
            Line line = lines.get(i);
            if (line.row() == null && fadeAfterMillis > 0) continue;
            double fade = viewOpacity, entrance = 1;
            if (line.row() != null && fadeAfterMillis > 0) {
                long age = System.currentTimeMillis() - line.row().message().time();
                if (age >= fadeAfterMillis) continue;
                var config = ChatChronicle.get().config;
                fade *= config.chatAnimations ? ChatMotion.fade(age, config.chatIdleSeconds * 1000, config.chatFadeMillis) : 1;
            }
            if (animateArrivals && scroll == 0 && ChatChronicle.get().config.chatAnimations && line.row() != null) {
                Long arrived = ChatChronicle.get().arrivals.get(line.row().message().id());
                if (arrived != null) entrance = ChatMotion.appearance((System.nanoTime() - arrived) / 1e6, ChatChronicle.get().config.chatAppearMillis);
                fade *= entrance;
            }
            boolean selected = line.row() != null && (selection.contains(line.row()) || Objects.equals(line.row().message().id(), highlighted));
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + step;
            graphics.fill(x, y, x + width, y + step, alpha(selected ? 0x594821 : hover ? 0x26313D : 0, backgroundOpacity * fade));
            if (selected) graphics.outline(x, y, width, step, alpha(0xD9AE51, fade));
            graphics.pose().pushMatrix();
            double originX = x + (1 - entrance) * 6 * scale;
            graphics.pose().translate((float) originX, (float) y).scale((float) scale);
            graphics.text(Minecraft.getInstance().font, line.text(), 4, 0, alpha(line.row() == null ? 0x8BADC5 : 0xFFFFFF, textOpacity * fade));
            if (hover && fade > .99) {
                Style style = styleAt(line.text(), (int) ((mouseX - originX) / scale) - 4);
                if (style != null && style.getHoverEvent() != null) graphics.textRenderer().accept(4, 0, line.text());
            }
            graphics.pose().popMatrix();
            hits.add(new Hit(line, y, originX));
            if (hover && line.row() != null) {
                ChatRecord record = line.row().message();
                if (line.timestamp() && mouseX < x + 85 * scale) graphics.setTooltipForNextFrame(Component.literal(
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z", Messages.locale()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(record.time()))), mouseX, mouseY);
            }
            y -= step;
        }
        graphics.disableScissor();
        if (!notice.isEmpty()) graphics.text(Minecraft.getInstance().font, notice.text(), x + 4, top + 2, 0xFFFFCC88);
        if (emptyHint && rows.isEmpty() && !loading && notice.isEmpty()) graphics.text(Minecraft.getInstance().font, Messages.text("view.empty"), x + 4, top + 16, 0xFFAAAAAA);
        if (!selection.isEmpty()) graphics.text(Minecraft.getInstance().font, Messages.text("selection.badge", selection.size()), x + 4, top + (notice.isEmpty() ? 2 : 14), 0xFFFFDB8C);
    }
    public Hit hit(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < top || mouseY >= bottom) return null;
        for (Hit hit : hits) if (hit.line().row() != null && mouseY >= hit.y() && mouseY < hit.y() + lineHeight()) return hit;
        return null;
    }
    public int lineHeight() { return Math.max(1, (int) Math.ceil(9 * (1 + lineSpacing) * scale)); }
    public Style clickableStyle(Hit hit, double mouseX, double mouseY) {
        var finder = new ActiveTextCollector.ClickableStyleFinder(Minecraft.getInstance().font, (int) mouseX, (int) mouseY);
        var pose = new org.joml.Matrix3x2f().translate((float) hit.originX(), (float) hit.y()).scale((float) scale);
        finder.defaultParameters(finder.defaultParameters().withPose(pose));
        finder.accept(4, 0, hit.line().text());
        return finder.result();
    }
    public int textPosition(double mouseX) { return (int) ((mouseX - x) / scale) - 4; }
    private static int alpha(int rgb, double opacity) { return ((int) Math.round(Math.clamp(opacity, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF); }
    public static Style styleAt(FormattedCharSequence text, int position) {
        if (position < 0) return null;
        int[] offset = {0}; Style[] found = {null};
        text.accept((index, style, codepoint) -> {
            offset[0] += Minecraft.getInstance().font.width(FormattedCharSequence.codepoint(codepoint, style));
            if (offset[0] > position) { found[0] = style; return false; }
            return true;
        });
        return found[0];
    }
}
