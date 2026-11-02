package dev.chatchronicle.client.mixin;

import dev.chatchronicle.client.ChronicleOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Shadow private CommandSuggestions commandSuggestions;
    @Inject(method = "init", at = @At("TAIL"))
    private void chronicle$init(CallbackInfo ci) { ChronicleOverlay.open((ChatScreen) (Object) this); }
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void chronicle$renderBehindInput(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        ChronicleOverlay.renderChat(graphics, x, y, commandSuggestions != null && commandSuggestions.isVisible());
        graphics.nextStratum();
    }
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void chronicle$renderMenu(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) { ChronicleOverlay.renderMenu(graphics, x, y); }
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void chronicle$scrollMenu(double x, double y, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (ChronicleOverlay.hasMenu() && ChronicleOverlay.scroll(vertical)) cir.setReturnValue(true);
    }
    @Inject(method = "mouseScrolled", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;hasShiftDown()Z"), cancellable = true)
    private void chronicle$scrollAfterSuggestions(double x, double y, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (ChronicleOverlay.scroll(vertical)) cir.setReturnValue(true);
    }
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chronicle$clickMenu(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ChronicleOverlay.hasMenu() && ChronicleOverlay.click(event)) cir.setReturnValue(true);
    }
    @Inject(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/MouseButtonEvent;button()I", ordinal = 0), cancellable = true)
    private void chronicle$clickAfterSuggestions(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ChronicleOverlay.click(event)) cir.setReturnValue(true);
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chronicle$key(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ChronicleOverlay.key(event)) cir.setReturnValue(true);
    }
}
