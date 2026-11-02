package dev.chatchronicle.client.mixin;

import dev.chatchronicle.client.*;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @Inject(method = "addMessage", at = @At("HEAD"))
    private void chronicle$capture(Component text, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (ChatChronicle.get() != null) ChatChronicle.get().capture(text, signature, source.name());
    }
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"), cancellable = true)
    private void chronicle$render(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, ChatComponent.DisplayMode mode, boolean interactive, CallbackInfo ci) {
        if (ChatChronicle.get() != null) { ChronicleOverlay.renderHud(graphics, mouseX, mouseY); ci.cancel(); }
    }
    @Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
    private void chronicle$clickable(ActiveTextCollector collector, int x, int y, ChatComponent.DisplayMode mode, CallbackInfo ci) {
        if (ChatChronicle.get() != null) ci.cancel();
    }
}
