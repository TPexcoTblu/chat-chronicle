package dev.chatchronicle.client;

import dev.chatchronicle.core.Messages;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** Confirmation buttons follow the mod language, not the Minecraft language. */
public final class ModConfirmScreen extends ConfirmScreen {
    public ModConfirmScreen(BooleanConsumer callback, Component title, Component message) {
        super(callback, title, message, Component.literal(Messages.text("nav.confirm")), Component.literal(Messages.text("nav.cancel")));
    }
}
