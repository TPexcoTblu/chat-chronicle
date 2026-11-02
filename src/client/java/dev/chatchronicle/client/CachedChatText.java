package dev.chatchronicle.client;

import dev.chatchronicle.client.mixin.GuiTextRenderStateAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

/** One prepared color variant per wrapped line; dynamic glyphs retain vanilla preparation. */
public final class CachedChatText {
    public static volatile long resourceRevision;
    public final FormattedCharSequence sequence;
    private final boolean dynamic;
    private Font.PreparedText prepared;
    private int preparedColor;
    private long preparedRevision = -1;

    public CachedChatText(FormattedCharSequence sequence) {
        this.sequence = sequence;
        boolean[] animated = {false};
        sequence.accept((index, style, codepoint) -> {
            animated[0] = style.isObfuscated() || style.getFont() instanceof FontDescription.PlayerSprite;
            return !animated[0];
        });
        dynamic = animated[0];
    }
    public void draw(GuiGraphicsExtractor graphics, int color) {
        if ((color >>> 24) == 0) return;
        Font font = Minecraft.getInstance().font;
        if (dynamic) { graphics.text(font, sequence, 4, 0, color); return; }
        if (prepared == null || preparedColor != color || preparedRevision != resourceRevision) {
            prepared = font.prepareText(sequence, 4, 0, color, true, false, 0);
            preparedColor = color; preparedRevision = resourceRevision;
        }
        var pose = new Matrix3x2f(graphics.pose());
        var scissor = graphics.scissorStack.peek();
        var state = new GuiTextRenderState(font, sequence, pose, 4, 0, color, 0, true, false, scissor);
        var bounds = prepared.bounds();
        if (bounds != null) {
            bounds = bounds.transformMaxBounds(pose);
            if (scissor != null) bounds = scissor.intersection(bounds);
        }
        var access = (GuiTextRenderStateAccess) (Object) state;
        access.chronicle$prepared(prepared); access.chronicle$bounds(bounds);
        graphics.guiRenderState.addText(state);
    }
}
