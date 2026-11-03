package dev.chatchronicle.client.mixin;

import dev.chatchronicle.client.ChatChronicle;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientLifecycleMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void chronicle$tick(CallbackInfo ci) {
        if (ChatChronicle.get() != null) ChatChronicle.get().tick((Minecraft) (Object) this);
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void chronicle$stop(CallbackInfo ci) {
        if (ChatChronicle.get() != null) ChatChronicle.get().stop();
    }
}
