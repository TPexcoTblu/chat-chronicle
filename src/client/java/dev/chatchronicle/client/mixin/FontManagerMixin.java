package dev.chatchronicle.client.mixin;

import dev.chatchronicle.client.CachedChatText;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
    @Inject(method = {"apply", "updateOptions"}, at = @At("TAIL"))
    private void chronicle$invalidateText(CallbackInfo ci) { CachedChatText.resourceRevision++; }
}
