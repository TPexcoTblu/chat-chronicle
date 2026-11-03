package dev.chatchronicle.platform;

import dev.chatchronicle.client.ChatChronicle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Also used by Quilt's supported Fabric entrypoint compatibility, without Fabric API. */
public final class FabricEntrypoint implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        ChatChronicle.initialize(loader.getGameDir(), loader.getConfigDir());
    }
}
