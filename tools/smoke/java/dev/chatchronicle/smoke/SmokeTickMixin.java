package dev.chatchronicle.smoke.mixin;

import dev.chatchronicle.smoke.ClientSmoke;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SmokeTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void smoke$tick(CallbackInfo ci) { ClientSmoke.tick((Minecraft) (Object) this); }
}
