package dev.chatchronicle.client.mixin;

import com.mojang.authlib.GameProfile;
import dev.chatchronicle.client.ChatChronicle;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void chronicle$sender(PlayerChatMessage message, GameProfile sender, ChatType.Bound type, CallbackInfo ci) {
        if (ChatChronicle.get() != null && sender != null)
            ChatChronicle.get().sender(message.signature(), sender.name(), sender.id().toString());
    }
}
