package dev.chatchronicle.client.mixin;

import dev.chatchronicle.client.*;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"))
    private void chronicle$capture(Component text, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (ChatChronicle.get() != null) ChatChronicle.get().capture(text, signature, signature == null ? "SYSTEM" : "PLAYER");
    }
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"), cancellable = true)
    private void chronicle$render(GuiGraphics graphics, Font font, int tick, int mouseX, int mouseY, boolean focused, boolean interactive, CallbackInfo ci) {
        if (ChatChronicle.get() != null) { ChronicleOverlay.renderHud(graphics, mouseX, mouseY); ci.cancel(); }
    }
    @Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
    private void chronicle$clickable(ActiveTextCollector collector, int x, int y, boolean interactive, CallbackInfo ci) {
        if (ChatChronicle.get() != null) ci.cancel();
    }
}
