package dev.chatchronicle.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiTextRenderState.class)
public interface GuiTextRenderStateAccess {
    @Accessor("preparedText") void chronicle$prepared(Font.PreparedText text);
    @Accessor("bounds") void chronicle$bounds(ScreenRectangle bounds);
}
