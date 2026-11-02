package dev.chatchronicle.client.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse vanilla chat handling for commands, dialogs, custom actions and ordinary links. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccess {
    @Invoker("handleComponentClicked")
    boolean chronicle$handleComponentClicked(Style style, boolean insertion);
}
